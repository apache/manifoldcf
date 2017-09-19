/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.bfsioutput;


import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.runtime.ObjectIdImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.manifoldcf.crawler.system.Logging;


/**
 * This is the "output connector" for a Alfresco Bulk File System Import format repository.
 *
 * @author Luis Cabaceira
 */
public class AlfrescoBfsiOutputConnector extends BaseOutputConnector {

    protected final static String ACTIVITY_READ = "read document";
    protected static final String RELATIONSHIP_CHILD = "child";
    /*
    * CMIS SPECIFIC PROPERITES
    * */
    private static final String CMIS_PROPERTY_PREFIX = "cmis:";
    private static final String CMIS_DOCUMENT_TYPE = "cmis:document";
    /*
    * END CMIS PROPERITES
    * */
    /** Document accepted */
    private final static int DOCUMENT_STATUS_ACCEPTED = 0;
    private static final String DOCUMENT_STATUS_ACCEPTED_DESC = "Injection OK - ";
    private static final String DOCUMENT_STATUS_REJECTED_DESC = "Injection KO - ";
    /** Document permanently rejected */
    private final static int DOCUMENT_STATUS_REJECTED = 1;
    /** Document remove accepted */
    private final static String DOCUMENT_DELETION_STATUS_ACCEPTED = "Remove request accepted";
    /** Document remove permanently rejected */
    private final static String DOCUMENT_DELETION_STATUS_REJECTED = "Remove request rejected";
    /** The standard Path property for ManifoldCF used for migrate contents **/
    private static final String CONTENT_MIGRATION_PATH_PROPERTY = "manifoldcf:path";

    // Tab name properties
    private static final String TARGET_DROP_FOLDER_TAB_PROPERTY = "AlfrescoBfsiOutputConnector.BfsiParametersTab";
    // Template names
    /**
     * Forward to the javascript to check the configuration parameters
     */
    private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";
    /**
     * Server tab template
     */
    private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";
    /**
     * Forward to the HTML template to view the configuration parameters
     */
    private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";
    /**
     * Save activity
     */
    protected final static String ACTIVITY_INJECTION = "Injection";
    /**
     * Delete activity
     */
    protected final static String ACTIVITY_DELETE = "Delete";
    /**
     * Target path handle
     */
    Path DropFolderPath = null;

    /**
     * Constructor
     */
    public AlfrescoBfsiOutputConnector() {
        super();
    }

    /**
     * Return the list of activities that this connector supports (i.e. writes
     * into the log).
     *
     * @return the list.
     */
    @Override
    public String[] getActivitiesList() {
        return new String[]{ACTIVITY_INJECTION, ACTIVITY_DELETE};
    }


    protected class CheckConnectionThread extends Thread {
        protected Throwable exception = null;

        public CheckConnectionThread() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                if (DropFolderPath.getFileSystem().equals(null))     Logging.connectors.error(" Error checking path Object: ");
            } catch (Throwable e) {
                Logging.connectors.warn(" Error checking path Object: " + e.getMessage(), e);
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }

    }



    /**
     * Target filesystem folder for the drop zone
     */
    protected String dropFolder = null;

    /**
     * Credentials (for remote mode)
     * */
    protected String username = null;
    protected String password = null;
    /** Endpoint protocol */
    protected String protocol = null;
    /** Endpoint server name */
    protected String server = null;
    /** Endpoint port */
    protected String port = null;
    /** Endpoint context path of the Alfresco webapp */
    protected String path = null;
    /**
     * Flag for creating the new tree structure using timestamp
     **/
    protected String createTimestampTree = Boolean.FALSE.toString();
    protected Map<String, String> parameters = new HashMap<String, String>();
    protected static final long timeToRelease = 300000L;
    protected long lastSessionFetch = -1L;

    /*
    * DropFolderPath is a java.nio Path object that represents the target for the output package
    * The thread model enables parallel output executions
    * */
    protected class GetDropFolderPath extends Thread {
        protected Throwable exception = null;

        public GetDropFolderPath() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                // Create the path object
                DropFolderPath = Paths.get(path); // Solaris/Linux syntax
            } catch (Throwable e) {
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }
    }

    protected class DestroyDropFolderPath extends Thread {
        protected Throwable exception = null;

        public DestroyDropFolderPath() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                DropFolderPath = null;
            } catch (Throwable e) {
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }

    }


    /**
     * Close the connection. Call this before discarding the connection.
     */
    @Override
    public void disconnect() throws ManifoldCFException {
        if (DropFolderPath != null) {
            DestroyDropFolderPath t = new DestroyDropFolderPath();
            try {
                t.start();
                t.join();
                Throwable thr = t.getException();
                if (thr != null) {
                    if (thr instanceof RemoteException)
                        throw (RemoteException) thr;
                    else
                        throw (Error) thr;
                }
                DropFolderPath = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                DropFolderPath = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("Remote exception closing path object: " + e.getMessage(), e);
            }

        }

        username = null;
        password = null;
        protocol = null;
        server = null;
        port = null;
        path = null;
        createTimestampTree = Boolean.FALSE.toString();
    }


    /**
     * This method create a new "session" for a DropFolder path object
     * This can represent  S3, local filesystem or ftp
     * @param  configParameters
     *          is the set of configuration parameters, which in this case
     *          describe the target appliance, basic auth configuration, etc.
     *          (This formerly came out of the ini file.)
     */
    @Override
    public void connect(ConfigParams configParams) {
        super.connect(configParams);
        username = params.getParameter(AlfrescoBfsiOutputConfig.USERNAME_PARAM);
        password = params.getParameter(AlfrescoBfsiOutputConfig.PASSWORD_PARAM);
        protocol = params.getParameter(AlfrescoBfsiOutputConfig.PROTOCOL_PARAM);
        server = params.getParameter(AlfrescoBfsiOutputConfig.SERVER_PARAM);
        port = params.getParameter(AlfrescoBfsiOutputConfig.PORT_PARAM);
        path = params.getParameter(AlfrescoBfsiOutputConfig.PATH_PARAM);
        createTimestampTree = params.getParameter(AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);
        //Logging.connectors.info("Connected");
    }

    /**
     * Test the connection. Returns a string describing the connection integrity.
     *
     * @return the connection's status as a displayable string.
     */
    @Override
    public String check() throws ManifoldCFException {
        try {
            //Logging.connectors.info("Checking Connection");
            return super.check();
        } catch (ManifoldCFException e) {
            return "Connection failed: " + e.getMessage();
        }
    }




    /** Set up a session */
    protected void getSession() throws ManifoldCFException, ServiceInterruption {
        //Logging.connectors.info("Getting Session ");
        if (DropFolderPath == null) {
            // Check for parameter validity
            if (StringUtils.isEmpty(username))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.USERNAME_PARAM + " required but not set");
            if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Username = '" + username + "'");
            if (StringUtils.isEmpty(password))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.PASSWORD_PARAM + " required but not set");
            Logging.connectors.debug(" Password exists");
            if (StringUtils.isEmpty(protocol))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.PROTOCOL_PARAM + " required but not set");
            if (StringUtils.isEmpty(server))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.SERVER_PARAM + " required but not set");
            if (StringUtils.isEmpty(port))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.PORT_PARAM + " required but not set");
            if (StringUtils.isEmpty(path))
                throw new ManifoldCFException("Parameter " + AlfrescoBfsiOutputConfig.PATH_PARAM + " required but not set");
            // Create path object with the output connector path location (dropZone)
            DropFolderPath= Paths.get(path);
        }
        long currentTime;
        GetDropFolderPath t = new GetDropFolderPath();
        try {
            t.start();
            t.join();
            Throwable thr = t.getException();
            if (thr != null) {
                if (thr instanceof java.net.MalformedURLException)
                    throw (java.net.MalformedURLException) thr;
                else if (thr instanceof NotBoundException)
                    throw (NotBoundException) thr;
                else if (thr instanceof RemoteException)
                    throw (RemoteException) thr;
                else
                    throw (Error) thr;
            }
        } catch (InterruptedException e) {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
        } catch (java.net.MalformedURLException e) {
            throw new ManifoldCFException(e.getMessage(), e);
        } catch (NotBoundException e) {
            // Transient problem: Server not available at the moment.
            Logging.connectors.warn("Target DropFolder Path not up at the moment: " + e.getMessage(), e);
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
        } catch (RemoteException e) {
            Throwable e2 = e.getCause();
            if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
            // Treat this as a transient problem
            Logging.connectors.warn("Transient remote exception creating session: " + e.getMessage(), e);
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
        }


        lastSessionFetch = System.currentTimeMillis();
    }


    protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
        while (true) {
            boolean noPathObject = (DropFolderPath == null);
            getSession();
            long currentTime;
            CheckConnectionThread t = new CheckConnectionThread();
            try {
                t.start();
                t.join();
                Throwable thr = t.getException();
                if (thr != null) {
                    if (thr instanceof RemoteException)
                        throw (RemoteException) thr;
                    else
                        throw (Error) thr;
                }
                return;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                if (noPathObject) {
                    currentTime = System.currentTimeMillis();
                    throw new ServiceInterruption("Transient error connecting to file system service: " + e.getMessage(),
                            currentTime + 60000L);
                }
                DropFolderPath = null;
                lastSessionFetch = -1L;
                continue;
            }
        }
    }



    /**
     * Release the session, if it's time.
     */
    protected void releaseCheck() throws ManifoldCFException {
        Logging.connectors.error("Starting releaseCheck ");
        if (lastSessionFetch == -1L)
            return;

        long currentTime = System.currentTimeMillis();
        if (currentTime >= lastSessionFetch + timeToRelease) {
            DestroyDropFolderPath t = new DestroyDropFolderPath();
            try {
                t.start();
                t.join();
                Throwable thr = t.getException();
                if (thr != null) {
                    if (thr instanceof RemoteException)
                        throw (RemoteException) thr;
                    else
                        throw (Error) thr;
                }
                DropFolderPath = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                DropFolderPath = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("Transient remote exception closing session: " + e.getMessage(), e);
            }

        }
    }



    /**
     * This method is periodically called for all connectors that are connected
     * but not in active use.
     */
    @Override
    public void poll() throws ManifoldCFException {
        if (lastSessionFetch == -1L)
            return;

        long currentTime = System.currentTimeMillis();
        if (currentTime >= lastSessionFetch + timeToRelease) {
            DestroyDropFolderPath t = new DestroyDropFolderPath();
            try {
                t.start();
                t.join();
                Throwable thr = t.getException();
                if (thr != null) {
                    if (thr instanceof RemoteException)
                        throw (RemoteException) thr;
                    else
                        throw (Error) thr;
                }
                DropFolderPath = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                DropFolderPath = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("Remote exception closing path object: " + e.getMessage(), e);
            }

        }
    }





    /**
     * This method is called to assess whether to count this connector instance
     * should actually be counted as being connected (having a path object to dropFolder)
     *
     * @return true if the connector instance is actually connected (could get a path object of dropFolder)
     */
    @Override
    public boolean isConnected() {
        return DropFolderPath != null;
    }

    /**
     * Read the content of a resource, replace the variable ${PARAMNAME} with the
     * value and copy it to the out.
     *
     * @param resName
     * @param out
     * @throws ManifoldCFException
     */
    private static void outputResource(String resName, IHTTPOutput out, Locale locale, Map<String, String> paramMap)
            throws ManifoldCFException {
        //Logging.connectors.info(" In outputResource");
        Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
    }

    /**
     * Fill in a Server tab configuration parameter map for calling a Velocity
     * template.
     *
     * @param newMap
     *          is the map to fill in
     * @param parameters
     *          is the current set of configuration parameters
     */
    private static void fillInServerConfigurationMap(Map<String, String> newMap, IPasswordMapperActivity mapper,
                                                     ConfigParams parameters) {


        //Logging.connectors.info("In fillInServerConfigurationMap ");
        String username = parameters.getParameter(AlfrescoBfsiOutputConfig.USERNAME_PARAM);
        String password = parameters.getParameter(AlfrescoBfsiOutputConfig.PASSWORD_PARAM);
        String protocol = parameters.getParameter(AlfrescoBfsiOutputConfig.PROTOCOL_PARAM);
        String server = parameters.getParameter(AlfrescoBfsiOutputConfig.SERVER_PARAM);
        String port = parameters.getParameter(AlfrescoBfsiOutputConfig.PORT_PARAM);
        String path = parameters.getParameter(AlfrescoBfsiOutputConfig.PATH_PARAM);

        String dropfolder = parameters.getParameter(AlfrescoBfsiOutputConfig.DROPFOLDER_PARAM);
        String createTimestampTree = parameters.getParameter(AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);

        if (dropfolder == null)
            dropfolder = StringUtils.EMPTY;

        if(createTimestampTree == null)
            createTimestampTree = AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_DEFAULT_VALUE;


        if (username == null)
            username = StringUtils.EMPTY;
        if (password == null)
            password = StringUtils.EMPTY;
        else
            password = mapper.mapPasswordToKey(password);
        if (protocol == null)
            protocol = AlfrescoBfsiOutputConfig.PROTOCOL_DEFAULT_VALUE;
        if (server == null)
            server = AlfrescoBfsiOutputConfig.SERVER_DEFAULT_VALUE;
        if (port == null)
            port = AlfrescoBfsiOutputConfig.PORT_DEFAULT_VALUE;
        if (path == null)
            path = AlfrescoBfsiOutputConfig.PATH_DEFAULT_VALUE;


        newMap.put(AlfrescoBfsiOutputConfig.USERNAME_PARAM, username);
        newMap.put(AlfrescoBfsiOutputConfig.PASSWORD_PARAM, password);
        newMap.put(AlfrescoBfsiOutputConfig.PROTOCOL_PARAM, protocol);
        newMap.put(AlfrescoBfsiOutputConfig.SERVER_PARAM, server);
        newMap.put(AlfrescoBfsiOutputConfig.PORT_PARAM, port);
        newMap.put(AlfrescoBfsiOutputConfig.PATH_PARAM, path);
        newMap.put(AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_PARAM, createTimestampTree);
        newMap.put(AlfrescoBfsiOutputConfig.DROPFOLDER_PARAM, dropfolder);
        //Logging.connectors.info("Cabaceira Ending fillInServerConfigurationMap" + username + "-- pass - " + password + "-- protocol -- " + protocol + "--- server ---" + server + "-- port --" + port
        //+ "---path---" + path + "--- createTimestamp ---" + createTimestampTree + " --- dropfolder ---" + dropfolder);
    }


    /**
     * View configuration. This method is called in the body section of the
     * connector's view configuration page. Its purpose is to present the
     * connection information to the user. The coder can presume that the HTML
     * that is output from this configuration will be within appropriate
     * <html> and <body> tags.
     *
     * @param threadContext
     *          is the local thread context.
     * @param out
     *          is the output to which any HTML should be sent.
     * @param parameters
     *          are the configuration parameters, as they currently exist, for
     *          this connection being configured.
     */
    @Override
    public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
            throws ManifoldCFException, IOException {

        //Logging.connectors.info(" In viewConfiguration ");
        Map<String, String> paramMap = new HashMap<String, String>();

        // Fill in map from each tab
        fillInServerConfigurationMap(paramMap, out, parameters);
        outputResource(VIEW_CONFIG_FORWARD, out, locale, paramMap);
    }

    /**
     *
     * Output the configuration header section. This method is called in the head
     * section of the connector's configuration page. Its purpose is to add the
     * required tabs to the list, and to output any javascript methods that might
     * be needed by the configuration editing HTML.
     *
     * @param threadContext
     *          is the local thread context.
     * @param out
     *          is the output to which any HTML should be sent.
     * @param parameters
     *          are the configuration parameters, as they currently exist, for
     *          this connection being configured.
     * @param tabsArray
     *          is an array of tab names. Add to this array any tab names that are
     *          specific to the connector.
     */
    @Override
    public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale,
                                          ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
        // Add the Server tab
        tabsArray.add(Messages.getString(locale, TARGET_DROP_FOLDER_TAB_PROPERTY));
        Logging.connectors.info(" In viewConfiguration ");
        // Map the parameters
        Map<String, String> paramMap = new HashMap<String, String>();
        // Fill in the parameters from each tab
        fillInServerConfigurationMap(paramMap, out, parameters);
        // Output the Javascript - only one Velocity template for all tabs
        outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, paramMap);
    }



    @Override
    public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale,
                                        ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {

        // Call the Velocity templates for each tab
       // Logging.connectors.error("In viewConfiguration ");
        // Server tab
        Map<String, String> paramMap = new HashMap<String, String>();
        // Set the tab name
        paramMap.put("TabName", tabName);
        // Fill in the parameters
        fillInServerConfigurationMap(paramMap, out, parameters);
        outputResource(EDIT_CONFIG_FORWARD_SERVER, out, locale, paramMap);

    }







    /**
     * Process a configuration post. This method is called at the start of the
     * connector's configuration page, whenever there is a possibility that form
     * data for a connection has been posted. Its purpose is to gather form
     * information and modify the configuration parameters accordingly. The name
     * of the posted form is "editconnection".
     *
     * @param threadContext
     *          is the local thread context.
     * @param variableContext
     *          is the set of variables available from the post, including binary
     *          file post information.
     * @param parameters
     *          are the configuration parameters, as they currently exist, for
     *          this connection being configured.
     * @return null if all is well, or a string error message if there is an error
     *         that should prevent saving of the connection (and cause a
     *         redirection to an error page).
     */
    @Override
    public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
                                           ConfigParams parameters) throws ManifoldCFException {


        String dropfolder = variableContext.getParameter(AlfrescoBfsiOutputConfig.DROPFOLDER_PARAM);
        if (dropfolder != null)
            parameters.setParameter(AlfrescoBfsiOutputConfig.DROPFOLDER_PARAM, dropfolder);


        String createTimestampTree = variableContext.getParameter(AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);
        if (createTimestampTree != null) {
            parameters.setParameter(AlfrescoBfsiOutputConfig.CREATE_TIMESTAMP_TREE_PARAM, createTimestampTree);
        }


        String username = variableContext.getParameter(AlfrescoBfsiOutputConfig.USERNAME_PARAM);
        if (username != null)
            parameters.setParameter(AlfrescoBfsiOutputConfig.USERNAME_PARAM, username);

        String password = variableContext.getParameter(AlfrescoBfsiOutputConfig.PASSWORD_PARAM);
        if (password != null)
            parameters.setParameter(AlfrescoBfsiOutputConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));

        String protocol = variableContext.getParameter(AlfrescoBfsiOutputConfig.PROTOCOL_PARAM);
        if (protocol != null) {
            parameters.setParameter(AlfrescoBfsiOutputConfig.PROTOCOL_PARAM, protocol);
        }

        String server = variableContext.getParameter(AlfrescoBfsiOutputConfig.SERVER_PARAM);
        if (server != null && !StringUtils.contains(server, '/')) {
            parameters.setParameter(AlfrescoBfsiOutputConfig.SERVER_PARAM, server);
        }

        String port = variableContext.getParameter(AlfrescoBfsiOutputConfig.PORT_PARAM);
        if (port != null) {
            try {
                Integer.parseInt(port);
                parameters.setParameter(AlfrescoBfsiOutputConfig.PORT_PARAM, port);
            } catch (NumberFormatException e) {

            }
        }

        String path = variableContext.getParameter(AlfrescoBfsiOutputConfig.PATH_PARAM);
        if (path != null) {
            parameters.setParameter(AlfrescoBfsiOutputConfig.PATH_PARAM, path);
        }




        return null;
    }


    private boolean isSourceRepoCmisCompliant(RepositoryDocument document) {
        Iterator<String> fields = document.getFields();
        while (fields.hasNext()) {
            String fieldName = (String) fields.next();
            if (StringUtils.startsWith(fieldName, CMIS_PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }


    private boolean isSourceRepoDropBox(RepositoryDocument document) {
        Iterator<String> fields = document.getFields();
        while (fields.hasNext()) {
            String fieldName = (String) fields.next();
            if (StringUtils.startsWith(fieldName, "dropbox")) { //TODO LOGIC
                return true;
            }
        }
        return false;
    }


    @Override // called for each content item.
    public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
                                                 RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
            throws ManifoldCFException, ServiceInterruption, IOException {

        getSession();

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        String date = dateFormat.format(cal.getTime());
        String objectId = StringUtils.EMPTY;
        Properties metadataProperties = new Properties();
        /*
        * HERE WE CAN CALL TRANSFORMERS OR CUSTOM METADATA AGENTS THAT ARE RESPONSIBLE TO EXTRACT THE METADATA FROM THE SOURCE
        *
        * USING SOME DEFAULT DUMMY VALUES FOR NOW
        * */
        metadataProperties.setProperty("type", "cm:content");
        metadataProperties.setProperty("aspects", "cm:versionable,cm:dublincore");
        metadataProperties.setProperty("cm:title", "Crawled document from ManifoldCF Input Connectors : " + date);
        metadataProperties.setProperty("cm:description", "");
        metadataProperties.setProperty("cm:author", "Apache ManifoldCF BulkFilesystem Output Connector");
        metadataProperties.setProperty("cm:publisher", "BulkFilesystem Output Connector");
        metadataProperties.setProperty("cm:contributor", "BulkFilesystem Output Connector");
        metadataProperties.setProperty("cm:type", "default_plus_dubincore_aspect");
        metadataProperties.setProperty("cm:identifier", document.getFileName());
        metadataProperties.setProperty("cm:source", "BulkFilesystem Output Connector");
        metadataProperties.setProperty("cm:coverage", "General");
        metadataProperties.setProperty("cm:rights", "");
        metadataProperties.setProperty("cm:subject", "Metadata file created with Apache ManifoldCF BulkFilesystem Output Connector");

        /* *************************************************************************************************************************
           ************************************ MAPPING LOGIC (REPOSITORY SOURCES) *************************************************
           *************************************************************************************************************************/

        /*
            CMIS Repository Connector Source  - Override the objectId for synchronizing with removeDocument method
        */
        if(isSourceRepoCmisCompliant(document)) {
            //Logging.connectors.info("CmisCompliant source repo - mapping cmis metada into node's shadow metadata file");
            String[] cmisObjectIdArray = (String[]) document.getField(PropertyIds.OBJECT_ID);
            if(cmisObjectIdArray!=null && cmisObjectIdArray.length>0) {
                objectId = cmisObjectIdArray[0];
            }

            //Mapping all the CMIS properties ...
            /*
            Iterator<String> fields = document.getFields();
            while (fields.hasNext()) {
                String field = (String) fields.next();
                if(!StringUtils.equals(field, "cm:lastThumbnailModification")
                        || !StringUtils.equals(field, "cmis:secondaryObjectTypeIds")) {
                    String[] valuesArray = (String[]) document.getField(field);
                    properties.put(field,valuesArray);
                }
            }
            */
            //Agnostic metadata from cmis repo
            metadataProperties.setProperty(PropertyIds.OBJECT_TYPE_ID, CMIS_DOCUMENT_TYPE);
            metadataProperties.setProperty(PropertyIds.CREATION_DATE, document.getCreatedDate().toString());
            metadataProperties.setProperty(PropertyIds.LAST_MODIFICATION_DATE, document.getModifiedDate().toString());
            //check objectId
            if(StringUtils.isNotEmpty(objectId)){
                ObjectId objId = new ObjectIdImpl(objectId);
                String uuid = objId.getId().substring(0,objId.getId().indexOf(';'));
                metadataProperties.setProperty("sys:node-uuid", uuid);
            }
        }

        if(isSourceRepoDropBox(document)) {
            //Logging.connectors.info("DropBox source repo - mapping metada into node's shadow metadata file");
        }

        // Add more input connectors specific logic here


        /* *************************************************************************************************************************
           ************************************ END MAPPING LOGIC (REPOSITORY SOURCES) *********************************************
           *************************************************************************************************************************/

        // Creating target node in BFSI format (document + shadow metadata xml file)
        AlfrescoBfsiOutputAgent.createBulkDocument(document.getBinaryStream(),document.getFileName(),metadataProperties,DropFolderPath);


       // Logging.connectors.error("In addOrReplaceDocumentWithException -- " + document.getFileName());
       // Logging.connectors.error("DropFolder filesystem is " + DropFolderPath.getFileSystem());
       // Logging.connectors.error("DropFolder path is " + DropFolderPath.toString());
       // Logging.connectors.error("Parent is " + DropFolderPath.getParent() + " Root is " + DropFolderPath.getRoot());


        return DOCUMENT_STATUS_ACCEPTED;
    }




    @Override
    public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        getSession();
        Logging.connectors.error("In removeDocument -- " + documentURI);
        long startTime = System.currentTimeMillis();
        String result = StringUtils.EMPTY;

        //append the prefix for the relative path in the target repo
        String parentDropZonePath = DropFolderPath.toString();
        String fullDocumentURIinTargetRepo = parentDropZonePath + documentURI;
        Path documentPathInTarget = Paths.get(fullDocumentURIinTargetRepo);
        Path documentShadowMetaPathInTarget = Paths.get(fullDocumentURIinTargetRepo + ".metadata.properties.xml");
        try {
            if(Files.exists(documentPathInTarget)) {
                // delete document and shadow metadata file
                Files.delete(documentPathInTarget);
                Files.delete(documentShadowMetaPathInTarget);
                result = DOCUMENT_DELETION_STATUS_ACCEPTED;
            } else {
                result = DOCUMENT_DELETION_STATUS_REJECTED;
            }
        } catch (Exception e) {
            result = DOCUMENT_DELETION_STATUS_REJECTED;
            throw new ManifoldCFException(e.getMessage(), e);
        } finally {
            activities.recordActivity(startTime, ACTIVITY_DELETE, null, documentURI, null, result);
        }
    }

    protected static void handleIOException(IOException e, String context)
            throws ManifoldCFException, ServiceInterruption {
        if (e instanceof InterruptedIOException) {
            throw new ManifoldCFException(e.getMessage(), e, ManifoldCFException.INTERRUPTED);
        } else {
            Logging.connectors.warn("IOException " + context + ": " + e.getMessage(), e);
            throw new ManifoldCFException(e.getMessage(), e);
        }
    }







}