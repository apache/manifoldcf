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
package org.apache.manifoldcf.agents.output.mongodboutput;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.DBTCPConnector;
import com.mongodb.DBPort;
import com.mongodb.DBPortPool;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.MongoException;
import org.bson.types.Binary;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.Logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Iterator;

/**
 * This is the "output connector" for MongoDB.
 *
 * @author Irindu Nugawela
 */

public class MongodbOutputConnector extends BaseOutputConnector {

    // Tab name properties

    private static final String MONGODB_TAB_PARAMETERS = "MongodbConnector.Parameters";

    // Template names

    /**
     * Forward to the javascript to check the configuration parameters
     */
    private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";
    /**
     * Server tab template
     */
    private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Parameters.html";
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
     * Document accepted
     */
    private final static int DOCUMENT_STATUS_ACCEPTED = 0;

    private static final String DOCUMENT_STATUS_ACCEPTED_DESC = "Injection OK - ";

    private static final String DOCUMENT_STATUS_REJECTED_DESC = "Injection KO - ";

    /**
     * Document permanently rejected
     */
    private final static int DOCUMENT_STATUS_REJECTED = 1;

    /**
     * Document remove accepted
     */
    private final static String DOCUMENT_DELETION_STATUS_ACCEPTED = "Remove request accepted";

    /**
     * Document remove permanently rejected
     */
    private final static String DOCUMENT_DELETION_STATUS_REJECTED = "Remove request rejected";

    /** Location of host where MongoDB server is hosted*/
    protected String host = null;

    /** port number associated with MongoDB server process */
    protected String port = null;

    /** Name of the target database */
    protected String database = null;

    /** Name of the target collection that belongs to the database specified above */
    protected String collection = null;

    /** username and password associated with the target database */
    protected String username = null;
    protected String password = null;

    /**
     * Session expiration time in milliseconds.
     */
    protected static final long timeToRelease = 300000L;

    /**
     * Last session fetch time.
     */
    protected long lastSessionFetch = -1L;

    /**
     * MongoDB client instance used to make the connection to the MongoDB server
     */
    private MongoClient client = null;

    /**
     * MongoDB database handle instance
     */
    private DB mongoDatabase = null;


    /**
     * Constructor
     */
    public MongodbOutputConnector() {
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
            DestroySessionThread t = new DestroySessionThread();
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
                client = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                client = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("MongoDB: Transient remote exception closing session: " + e.getMessage(), e);
            }

        }
    }

    /**
     * This method is called to assess whether to count this connector instance
     * should actually be counted as being connected.
     *
     * @return true if the connector instance is actually connected.
     */
    @Override
    public boolean isConnected() {
        if (client == null) {
            return false;
        }
        DBTCPConnector currentTCPConnection = client.getConnector();
        return currentTCPConnection.isOpen();
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
        Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
    }

    /**
     * Fill in a Server tab configuration parameter map for calling a Velocity
     * template.
     *
     * @param newMap     is the map to fill in
     * @param parameters is the current set of configuration parameters
     */
    private static void fillInServerConfigurationMap(Map<String, String> newMap, IPasswordMapperActivity mapper,
                                                     ConfigParams parameters) {
        String username = parameters.getParameter(MongodbOutputConfig.USERNAME_PARAM);
        String password = parameters.getParameter(MongodbOutputConfig.PASSWORD_PARAM);
        String host = parameters.getParameter(MongodbOutputConfig.HOST_PARAM);
        String port = parameters.getParameter(MongodbOutputConfig.PORT_PARAM);
        String database = parameters.getParameter(MongodbOutputConfig.DATABASE_PARAM);
        String collection = parameters.getParameter(MongodbOutputConfig.COLLECTION_PARAM);

        if (username == null)
            username = StringUtils.EMPTY;
        if (password == null)
            password = StringUtils.EMPTY;
        else
            password = mapper.mapPasswordToKey(password);
        if (host == null)
            host = MongodbOutputConfig.HOST_DEFAULT_VALUE;
        if (port == null)
            port = MongodbOutputConfig.PORT_DEFAULT_VALUE;
        if (database == null)
            database = MongodbOutputConfig.DATABASE_DEFAULT_VALUE;
        if (collection == null)
            collection = MongodbOutputConfig.COLLECTION_DEFAULT_VALUE;

        newMap.put(MongodbOutputConfig.USERNAME_PARAM, username);
        newMap.put(MongodbOutputConfig.PASSWORD_PARAM, password);
        newMap.put(MongodbOutputConfig.HOST_PARAM, host);
        newMap.put(MongodbOutputConfig.PORT_PARAM, port);
        newMap.put(MongodbOutputConfig.DATABASE_PARAM, database);
        newMap.put(MongodbOutputConfig.COLLECTION_PARAM, collection);
    }

    /**
     * View configuration. This method is called in the body section of the
     * connector's view configuration page. Its purpose is to present the
     * connection information to the user. The coder can presume that the HTML
     * that is output from this configuration will be within appropriate
     * <html> and <body> tags.
     *
     * @param threadContext is the local thread context.
     * @param out           is the output to which any HTML should be sent.
     * @param parameters    are the configuration parameters, as they currently exist, for
     *                      this connection being configured.
     */
    @Override
    public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
            throws ManifoldCFException, IOException {
        Map<String, String> paramMap = new HashMap<String, String>();

        // Fill in map from each tab
        fillInServerConfigurationMap(paramMap, out, parameters);

        outputResource(VIEW_CONFIG_FORWARD, out, locale, paramMap);
    }

    /**
     * Output the configuration header section. This method is called in the head
     * section of the connector's configuration page. Its purpose is to add the
     * required tabs to the list, and to output any javascript methods that might
     * be needed by the configuration editing HTML.
     *
     * @param threadContext is the local thread context.
     * @param out           is the output to which any HTML should be sent.
     * @param parameters    are the configuration parameters, as they currently exist, for
     *                      this connection being configured.
     * @param tabsArray     is an array of tab names. Add to this array any tab names that are
     *                      specific to the connector.
     */
    @Override
    public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale,
                                          ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
        // Add the Server tab
        tabsArray.add(Messages.getString(locale, MONGODB_TAB_PARAMETERS));
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
     * @param threadContext   is the local thread context.
     * @param variableContext is the set of variables available from the post, including binary
     *                        file post information.
     * @param parameters      are the configuration parameters, as they currently exist, for
     *                        this connection being configured.
     * @return null if all is well, or a string error message if there is an error
     * that should prevent saving of the connection (and cause a
     * redirection to an error page).
     */
    @Override
    public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
                                           ConfigParams parameters) throws ManifoldCFException {


        String username = variableContext.getParameter(MongodbOutputConfig.USERNAME_PARAM);
        if (username != null)
            parameters.setParameter(MongodbOutputConfig.USERNAME_PARAM, username);

        String password = variableContext.getParameter(MongodbOutputConfig.PASSWORD_PARAM);
        if (password != null)
            parameters.setParameter(MongodbOutputConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));

        String host = variableContext.getParameter(MongodbOutputConfig.HOST_PARAM);
        if (host != null)
            parameters.setParameter(MongodbOutputConfig.HOST_PARAM, host);

        String port = variableContext.getParameter(MongodbOutputConfig.PORT_PARAM);
        if (port != null && !StringUtils.isEmpty(port)) {
            try {
                Integer.parseInt(port);
                parameters.setParameter(MongodbOutputConfig.PORT_PARAM, port);
            } catch (NumberFormatException e) {
                return "Invalid Port Number";
            }
        }

        String database = variableContext.getParameter(MongodbOutputConfig.DATABASE_PARAM);
        if (database != null)
            parameters.setParameter(MongodbOutputConfig.DATABASE_PARAM, database);

        String collection = variableContext.getParameter(MongodbOutputConfig.COLLECTION_PARAM);
        if (collection != null)
            parameters.setParameter(MongodbOutputConfig.COLLECTION_PARAM, collection);


        return null;
    }

    /**
     * Close the connection. Call this before discarding the connection.
     */
    @Override
    public void disconnect() throws ManifoldCFException {
        if (client != null) {
            DestroySessionThread t = new DestroySessionThread();
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
                client = null;
                mongoDatabase = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                client = null;
                mongoDatabase = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("MongoDB: Transient remote exception closing session: " + e.getMessage(), e);
            }

        }

        username = null;
        password = null;
        host = null;
        port = null;
        database = null;
        collection = null;

    }

    /**
     * This method creates a new MongoDB Session for a MongoDB repository,
     *
     * @param configParams is the set of configuration parameters, which in this case
     *                     describe the target, basic auth configuration, etc.
     */
    @Override
    public void connect(ConfigParams configParams) {
        super.connect(configParams);
        username = params.getParameter(MongodbOutputConfig.USERNAME_PARAM);
        password = params.getParameter(MongodbOutputConfig.PASSWORD_PARAM);
        host = params.getParameter(MongodbOutputConfig.HOST_PARAM);
        port = params.getParameter(MongodbOutputConfig.PORT_PARAM);
        database = params.getParameter(MongodbOutputConfig.DATABASE_PARAM);
        collection = params.getParameter(MongodbOutputConfig.COLLECTION_PARAM);
    }

    /**
     * Test the connection. Returns a string describing the connection integrity.
     *
     * @return the connection's status as a displayable string.
     */
    @Override
    public String check() throws ManifoldCFException {
        try {
            checkConnection();
            return super.check();
        } catch (ServiceInterruption e) {
            return "Connection temporarily failed: " + e.getMessage();
        } catch (ManifoldCFException e) {
            return "Connection failed: " + e.getMessage();
        }
    }

    /**
     * Set up a session
     */
    protected void getSession() throws ManifoldCFException, ServiceInterruption {

        // Check for parameter validity

        if (StringUtils.isEmpty(database))
            throw new ManifoldCFException("Parameter " + MongodbOutputConfig.DATABASE_PARAM + " required but not set");

        if (StringUtils.isEmpty(collection))
            throw new ManifoldCFException("Parameter " + MongodbOutputConfig.COLLECTION_PARAM + " required but not set");


        long currentTime;
        GetSessionThread t = new GetSessionThread();
        try {
            t.start();
            t.join();
            Throwable thr = t.getException();
            if (thr != null) {
                if (thr instanceof ManifoldCFException)
                    throw new ManifoldCFException("MongoDB: Error during getting a new session: " + thr.getMessage(), thr);
                else if (thr instanceof RemoteException)
                    throw (RemoteException) thr;
                else if (thr instanceof MongoException)
                    throw new ManifoldCFException("MongoDB: Error during getting a new session: " + thr.getMessage(), thr);
                else if (thr instanceof java.net.ConnectException)
                    throw new ManifoldCFException("MongoDB: Error Connecting to MongoDB is mongod running? : " + thr.getMessage(), thr);
                else
                    throw (Error) thr;
            }
        } catch (InterruptedException e) {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
        } catch (RemoteException e) {
            Throwable e2 = e.getCause();
            if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
            // Treat this as a transient problem
            Logging.connectors.warn("MongoDB: Transient remote exception creating session: " + e.getMessage(), e);
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
        }

        lastSessionFetch = System.currentTimeMillis();
    }

    /**
     * Release the session, if it's time.
     */
    protected void releaseCheck() throws ManifoldCFException {
        if (lastSessionFetch == -1L)
            return;

        long currentTime = System.currentTimeMillis();
        if (currentTime >= lastSessionFetch + timeToRelease) {
            DestroySessionThread t = new DestroySessionThread();
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
                client = null;
                lastSessionFetch = -1L;
            } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (RemoteException e) {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                    throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
                client = null;
                lastSessionFetch = -1L;
                // Treat this as a transient problem
                Logging.connectors.warn("MongoDB: Transient remote exception closing session: " + e.getMessage(), e);
            }

        }
    }

    protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
        while (true) {
            boolean noSession = (client == null);
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
                    else if (thr instanceof ConnectException)
                        throw new ManifoldCFException("MongoDB: Error during checking connection: is Mongod running?" + thr.getMessage(), thr);
                    else if (thr instanceof MongoException)
                        throw new ManifoldCFException("MongoDB: Error during checking connection: " + thr.getMessage(), thr);
                    else if (thr instanceof ManifoldCFException)
                        throw new ManifoldCFException(thr.getMessage(), thr);
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
                if (noSession) {
                    currentTime = System.currentTimeMillis();
                    throw new ServiceInterruption("Transient error connecting to filenet service: " + e.getMessage(),
                            currentTime + 60000L);
                }
                client = null;
                lastSessionFetch = -1L;
                continue;
            }
        }
    }

    protected class GetSessionThread extends Thread {
        protected Throwable exception = null;

        public GetSessionThread() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                // Create a session
                if (client == null) {
                    if (StringUtils.isEmpty(host) && StringUtils.isEmpty(port)) {
                        try {
                            client = new MongoClient();
                            mongoDatabase = client.getDB(database);
                        } catch (UnknownHostException ex) {
                            throw new ManifoldCFException("MongoDB: Default host is not found. Is mongod process running?" + ex.getMessage(), ex);
                        }
                    } else if (!StringUtils.isEmpty(host) && StringUtils.isEmpty(port)) {
                        try {
                            client = new MongoClient(host);
                            mongoDatabase = client.getDB(database);
                        } catch (UnknownHostException ex) {
                            throw new ManifoldCFException("MongoDB: Given host information is not valid or mongod process doesn't run" + ex.getMessage(), ex);
                        }
                    } else if (!StringUtils.isEmpty(host) && !StringUtils.isEmpty(port)) {
                        try {
                            int integerPort = Integer.parseInt(port);
                            client = new MongoClient(host, integerPort);
                            mongoDatabase = client.getDB(database);
                        } catch (UnknownHostException ex) {
                            throw new ManifoldCFException("MongoDB: Given information is not valid or mongod process doesn't run" + ex.getMessage(), ex);
                        } catch (NumberFormatException ex) {
                            throw new ManifoldCFException("MongoDB: Given port is not a valid number. " + ex.getMessage(), ex);
                        }
                    } else if (StringUtils.isEmpty(host) && !StringUtils.isEmpty(port)) {
                        try {
                            int integerPort = Integer.parseInt(port);
                            client = new MongoClient("localhost", integerPort);
                            mongoDatabase = client.getDB(database);
                        } catch (UnknownHostException e) {
                            Logging.connectors.warn("MongoDB: Given information is not valid or mongod process doesn't run" + e.getMessage(), e);
                            throw new ManifoldCFException("MongoDB: Given information is not valid or mongod process doesn't run" + e.getMessage(), e);
                        } catch (NumberFormatException e) {
                            Logging.connectors.warn("MongoDB: Given port is not valid number. " + e.getMessage(), e);
                            throw new ManifoldCFException("MongoDB: Given port is not valid number. " + e.getMessage(), e);
                        }
                    }
                    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
                        boolean auth = mongoDatabase.authenticate(username, password.toCharArray());
                        if (!auth) {
                            Logging.connectors.warn("MongoDB:Authentication Error! Given database username and password doesn't match for the database given.");
                            throw new ManifoldCFException("MongoDB: Given database username and password doesn't match for the database given.");
                        } else {
                            if (Logging.connectors.isDebugEnabled()) {
                                Logging.connectors.debug("MongoDB: Username = '" + username + "'");
                                Logging.connectors.debug("MongoDB: Password exists");
                            }
                        }
                    }
                }

            } catch (Throwable e) {
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }
    }

    protected class CheckConnectionThread extends Thread {
        protected Throwable exception = null;

        public CheckConnectionThread() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                if (client != null) {
                    DBTCPConnector dbtcpConnector = client.getConnector();
                    DBPortPool dbPortPool = dbtcpConnector.getDBPortPool(client.getAddress());
                    DBPort dbPort = dbPortPool.get();
                    dbPort.ensureOpen();
                    client = null;
                }

            } catch (Throwable e) {
                Logging.connectors.warn("MongoDB: Error checking repository: " + e.getMessage(), e);
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }

    }

    protected class DestroySessionThread extends Thread {
        protected Throwable exception = null;

        public DestroySessionThread() {
            super();
            setDaemon(true);
        }

        public void run() {
            try {
                client = null;
                mongoDatabase = null;
            } catch (Throwable e) {
                this.exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }

    }

    @Override
    public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
                                                 RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
            throws ManifoldCFException, ServiceInterruption, IOException {

        getSession();

        //to get an exception if write fails
        client.setWriteConcern(WriteConcern.SAFE);

        long startTime = System.currentTimeMillis();
        String resultDescription = StringUtils.EMPTY;

        // Standard document fields
        String fileName = document.getFileName();
        Long binaryLength = document.getBinaryLength();
        String mimeType = document.getMimeType();
        Date creationDate = document.getCreatedDate();
        Date lastModificationDate = document.getModifiedDate();
        Date indexingDate = document.getIndexingDate();

        //   check if the repository connector includes the content path
        String primaryPath = StringUtils.EMPTY;
        List<String> sourcePath = document.getSourcePath();
        if (sourcePath != null && !sourcePath.isEmpty()) {
            primaryPath = sourcePath.get(0);
        }

        // Content Stream
        InputStream inputStream = document.getBinaryStream();

        try {

            byte[] bytes = IOUtils.toByteArray(inputStream);
            Binary content = new Binary(bytes);

            DBCollection mongoCollection = mongoDatabase.getCollection(collection);

            BasicDBObject newDocument = new BasicDBObject();
            newDocument.append("fileName", fileName)
                    .append("creationDate", creationDate)
                    .append("lastModificationDate", lastModificationDate)
                    .append("indexingDate", indexingDate)
                    .append("binaryLength", binaryLength)
                    .append("documentURI", documentURI)
                    .append("mimeType", mimeType)
                    .append("content", content)
                    .append("primaryPath", primaryPath);

            // Rest of the fields
            Iterator<String> i = document.getFields();
            while (i.hasNext()) {
                String fieldName = i.next();
                Date[] dateFieldValues = document.getFieldAsDates(fieldName);
                if (dateFieldValues != null) {
                    newDocument.append(fieldName, dateFieldValues);
                } else {
                    String[] fieldValues = document.getFieldAsStrings(fieldName);
                    newDocument.append(fieldName, fieldValues);
                }
            }

            BasicDBObject searchQuery = new BasicDBObject().append("documentURI", documentURI);
            DBCursor cursor = mongoCollection.find(searchQuery);
            Long numberOfDocumentsBeforeInsert = mongoCollection.count();
            WriteResult result;
            if (cursor.count() > 0) {
                result = mongoCollection.update(searchQuery, newDocument);
            } else {
                result = mongoCollection.insert(newDocument);
            }
            //result.getLastError().get("err") == null
            //To get the number of documents indexed i.e. tne number of documents inserted or updated
            Long numberOfDocumentsAfterInsert = mongoCollection.count();
            Long numberOfDocumentsInserted = numberOfDocumentsAfterInsert - numberOfDocumentsBeforeInsert;
            Long numberOfDocumentsIndexed = (numberOfDocumentsInserted != 0) ? numberOfDocumentsInserted : result.getN();
            Logging.connectors.info("Number of documents indexed : " + numberOfDocumentsIndexed);

            //check if a document is inserted or updated (numberOfDocumentsInserted > 0) || (result.getN() > 0)
            if (numberOfDocumentsIndexed > 0) {
                resultDescription = DOCUMENT_STATUS_ACCEPTED_DESC;
                return DOCUMENT_STATUS_ACCEPTED;
            } else {
                resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
                return DOCUMENT_STATUS_REJECTED;
            }

        } catch (MongoException e) {
            resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
            Logging.connectors.info("MongoDB: Error inserting or updating : " + e.getMessage());
            throw new ManifoldCFException(e.getMessage(), e);
        } catch (IOException e) {
            resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
            Logging.connectors.info("Error converting the input stream to byte array : " + e.getMessage());
            throw new ManifoldCFException(e.getMessage(), e);
        } catch (Exception e) {
            resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
            Logging.connectors.info("Error encoding content : " + e.getMessage());
            throw new ManifoldCFException(e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            activities.recordActivity(startTime, ACTIVITY_INJECTION, document.getBinaryLength(), documentURI, resultDescription,
                    resultDescription);
        }
    }

    @Override
    public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        getSession();
        long startTime = System.currentTimeMillis();
        String resultDescription = StringUtils.EMPTY;

        try {

            DBCollection mongoCollection = mongoDatabase.getCollection(collection);

            BasicDBObject query = new BasicDBObject();
            query.append("documentURI", documentURI);

            WriteResult result = mongoCollection.remove(query);
            Logging.connectors.info("Number of documents deleted : " + result.getN());

            if (result.getN() > 0) {
                resultDescription = DOCUMENT_DELETION_STATUS_ACCEPTED;
            } else {
                resultDescription = DOCUMENT_DELETION_STATUS_REJECTED;
            }

        } catch (Exception e) {
            resultDescription = DOCUMENT_DELETION_STATUS_REJECTED;
            throw new ManifoldCFException(e.getMessage(), e);
        } finally {
            activities.recordActivity(startTime, ACTIVITY_DELETE, null, documentURI, null, resultDescription);
        }
    }
}
