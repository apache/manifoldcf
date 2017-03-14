/**
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.crawler.connectors.gridfs;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.DBTCPConnector;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.system.Logging;
import org.bson.types.ObjectId;

/**
 *
 * @author molgun
 */
public class GridFSRepositoryConnector extends BaseRepositoryConnector {

    /**
     * Activity name for the activity record.
     */
    protected static final String ACTIVITY_FETCH = "fetch";
    /**
     * Server name for declaring bin name.
     */
    protected static final String SERVER = "MongoDB - GridFS";
    /**
     * Session expiration milliseconds.
     */
    protected static final long SESSION_EXPIRATION_MILLISECONDS = 30000L;
    /**
     * Endpoint username.
     */
    protected String username = null;
    /**
     * Endpoint password.
     */
    protected String password = null;
    /**
     * Endpoint host.
     */
    protected String host = null;
    /**
     * Endpoint port.
     */
    protected String port = null;
    /**
     * Endpoint db.
     */
    protected String db = null;
    /**
     * Endpoint bucket.
     */
    protected String bucket = null;
    /**
     * Endpoint url.
     */
    protected String url = null;
    /**
     * Endpoint acl.
     */
    protected String acl = null;
    /**
     * Endpoint denyAcl.
     */
    protected String denyAcl = null;
    /**
     * MongoDB session.
     */
    protected DB session = null;
    /**
     * Last session fetch time.
     */
    protected long lastSessionFetch = -1L;

    /**
     * Forward to the javascript to check the configuration parameters.
     */
    private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

    /**
     * Forward to the HTML template to view the configuration parameters.
     */
    private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

    /**
     * Forward to the HTML template to edit the configuration parameters.
     */
    private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";

    /**
     * GridFS server tab name.
     */
    private static final String GRIDFS_SERVER_TAB_RESOURCE = "GridFSConnector.Server";

    /**
     * Tab name parameter for managing the view of the Web UI.
     */
    private static final String TAB_NAME_PARAM = "TabName";

    /**
     * Constructer.
     */
    public GridFSRepositoryConnector() {
        super();
    }

    /**
     * Tell the world what model this connector uses for addSeedDocuments().
     * This must return a model value as specified above. The connector does not
     * have to be connected for this method to be called.
     *
     * @return the model type value.
     */
    @Override
    public String[] getBinNames(String documentIdentifier) {
        return new String[]{host};
    }

    /**
     * Tell the world what model this connector uses for addSeedDocuments().
     * This must return a model value as specified above. The connector does not
     * have to be connected for this method to be called.
     *
     * @return the model type value.
     */
    @Override
    public int getConnectorModel() {
        return super.getConnectorModel();
    }

    /**
     * Return the list of activities that this connector supports (i.e. writes
     * into the log). The connector does not have to be connected for this
     * method to be called.
     *
     * @return the list.
     */
    @Override
    public String[] getActivitiesList() {
        return new String[]{ACTIVITY_FETCH};
    }

    /**
     * Connect.
     *
     * @param configParams is the set of configuration parameters, which in this
     * case describe the root directory.
     */
    @Override
    public void connect(ConfigParams configParams) {
        super.connect(configParams);
        username = params.getParameter(GridFSConstants.USERNAME_PARAM);
        password = params.getObfuscatedParameter(GridFSConstants.PASSWORD_PARAM);
        host = params.getParameter(GridFSConstants.HOST_PARAM);
        port = params.getParameter(GridFSConstants.PORT_PARAM);
        db = params.getParameter(GridFSConstants.DB_PARAM);
        bucket = params.getParameter(GridFSConstants.BUCKET_PARAM);
        url = params.getParameter(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM);
        acl = params.getParameter(GridFSConstants.ACL_RETURN_FIELD_NAME_PARAM);
        denyAcl = params.getParameter(GridFSConstants.DENY_ACL_RETURN_FIELD_NAME_PARAM);
    }

    /**
     * Test the connection. Returns a string describing the connection
     * integrity.
     *
     * @return the connection's status as a displayable string.
     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
     */
    @Override
    public String check() throws ManifoldCFException {
        try {
            getSession();
            if (session != null) {
                Mongo currentMongoSession = session.getMongo();
                currentMongoSession.getConnector()
                        .getDBPortPool(currentMongoSession.getAddress())
                        .get()
                        .ensureOpen();
                session.getMongo().close();
                session = null;
                return super.check();
            }
            return "Not connected.";
        } catch (ManifoldCFException e) {
            return e.getMessage();
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    /**
     * Close the connection. Call this before discarding this instance of the
     * repository connector.
     *
     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
     */
    @Override
    public void disconnect() throws ManifoldCFException {
        if (session != null) {
            try {
                session.getMongo().close();
            } catch (Exception e) {
                Logging.connectors.error("GridFS: Error when trying to disconnect: " + e.getMessage());
                throw new ManifoldCFException("GridFS: Error when trying to disconnect: " + e.getMessage(), e);
            }
            session = null;
            lastSessionFetch = -1L;
            username = null;
            password = null;
            host = null;
            port = null;
            db = null;
            bucket = null;
            url = null;
            acl = null;
            denyAcl = null;
        }
    }

    /**
     * This method is periodically called for all connectors that are connected
     * but not in active use.
     *
     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
     */
    @Override
    public void poll() throws ManifoldCFException {
        if (lastSessionFetch == -1L) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= lastSessionFetch + SESSION_EXPIRATION_MILLISECONDS) {
            if (session != null) {
                session.getMongo().close();
                session = null;
            }
            lastSessionFetch = -1L;
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
        if (session == null) {
            return false;
        }
        Mongo currentMongoSession = session.getMongo();
        DBTCPConnector currentTCPConnection = currentMongoSession.getConnector();
        return currentTCPConnection.isOpen();
    }

    /**
     * Get the maximum number of documents to amalgamate together into one
     * batch, for this connector.
     *
     * @return the maximum number. 0 indicates "unlimited".
     */
    @Override
    public int getMaxDocumentRequest() {
        return super.getMaxDocumentRequest();
    }

    /**
     * Return the list of relationship types that this connector recognizes.
     *
     * @return the list.
     */
    @Override
    public String[] getRelationshipTypes() {
        return super.getRelationshipTypes();
    }

    /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
    * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
     *
    * This method can choose to find repository changes that happen only during the specified time interval.
    * The seeds recorded by this method will be viewed by the framework based on what the
    * getConnectorModel() method returns.
     *
    * It is not a big problem if the connector chooses to create more seeds than are
    * strictly necessary; it is merely a question of overall work required.
     *
    * The end time and seeding version string passed to this method may be interpreted for greatest efficiency.
    * For continuous crawling jobs, this method will
    * be called once, when the job starts, and at various periodic intervals as the job executes.
     *
    * When a job's specification is changed, the framework automatically resets the seeding version string to null.  The
    * seeding version string may also be set to null on each job run, depending on the connector model returned by
    * getConnectorModel().
     *
    * Note that it is always ok to send MORE documents rather than less to this method.
    * The connector will be connected before this method can be called.
    *@param activities is the interface this method should use to perform whatever framework actions are desired.
    *@param spec is a document specification (that comes from the job).
    *@param seedTime is the end of the time range of documents to consider, exclusive.
    *@param lastSeedVersionString is the last seeding version string for this job, or null if the job has no previous seeding version string.
    *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
    *@return an updated seeding version string, to be stored with the job.
     */
    @Override
    public String addSeedDocuments(ISeedingActivity activities, Specification spec,
            String lastSeedVersion, long seedTime, int jobMode)
            throws ManifoldCFException, ServiceInterruption {
        getSession();
        DBCollection fsFiles = session.getCollection(
                bucket + GridFSConstants.COLLECTION_SEPERATOR + GridFSConstants.FILES_COLLECTION_NAME
        );
        DBCursor dnc = fsFiles.find();
        while (dnc.hasNext()) {
            DBObject dbo = dnc.next();
            String _id = dbo.get("_id").toString();
            activities.addSeedDocument(_id);
            if (Logging.connectors.isDebugEnabled()) {
                Logging.connectors.debug("GridFS: Document _id = " + _id + " added to queue");
            }
        }
        return "";
    }

    /** Process a set of documents.
    * This is the method that should cause each document to be fetched, processed, and the results either added
    * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
    * The document specification allows this class to filter what is done based on the job.
    * The connector will be connected before this method can be called.
    *@param documentIdentifiers is the set of document identifiers to process.
    *@param statuses are the currently-stored document versions for each document in the set of document identifiers
    * passed in above.
    *@param activities is the interface this method should use to queue up new document references
    * and ingest documents.
    *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
    *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
     */
    @Override
    public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
            IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
            throws ManifoldCFException, ServiceInterruption {

        for (String documentIdentifier : documentIdentifiers) {

            String versionString;
            GridFS gfs;
            GridFSDBFile document;

            getSession();
            String _id = documentIdentifier;
            gfs = new GridFS(session, bucket);
            document = gfs.findOne(new ObjectId(_id));
            if (document == null) {
                activities.deleteDocument(documentIdentifier);
                continue;
            } else {
                DBObject metadata = document.getMetaData();
                versionString = document.getMD5() + "+" + metadata != null
                        ? Integer.toString(metadata.hashCode())
                        : StringUtils.EMPTY;
            }

            if (versionString.length() == 0 || activities.checkDocumentNeedsReindexing(documentIdentifier, versionString)) {
                long startTime = System.currentTimeMillis();
                String errorCode = null;
                String errorDesc = null;
                String version = versionString;
                try {

                    if (Logging.connectors.isDebugEnabled()) {
                        Logging.connectors.debug("GridFS: Processing document _id = " + _id);
                    }

                    DBObject metadata = document.getMetaData();
                    if (metadata == null) {
                        errorCode = "NULLMETADATA";
                        errorDesc = "Excluded because document had a null Metadata";
                        Logging.connectors.warn("GridFS: Document " + _id + " has a null metadata - skipping.");
                        activities.noDocument(_id, version);
                        continue;
                    }

                    String urlValue = document.getMetaData().get(this.url) == null
                            ? StringUtils.EMPTY
                            : document.getMetaData().get(this.url).toString();
                    if (!StringUtils.isEmpty(urlValue)) {
                        boolean validURL;
                        try {
                            new java.net.URI(urlValue);
                            validURL = true;
                        } catch (java.net.URISyntaxException e) {
                            validURL = false;
                        }
                        if (validURL) {
                            long fileLenght = document.getLength();
                            Date createdDate = document.getUploadDate();
                            String fileName = document.getFilename();
                            String mimeType = document.getContentType();

                            if (!activities.checkURLIndexable(urlValue)) {
                                Logging.connectors.warn("GridFS: Document " + _id + " has a URL excluded by the output connector ('" + urlValue + "') - skipping.");
                                errorCode = activities.EXCLUDED_URL;
                                errorDesc = "Excluded because of URL (" + urlValue + ")";
                                activities.noDocument(_id, version);
                                continue;
                            }

                            if (!activities.checkLengthIndexable(fileLenght)) {
                                Logging.connectors.warn("GridFS: Document " + _id + " has a length excluded by the output connector (" + fileLenght + ") - skipping.");
                                errorCode = activities.EXCLUDED_LENGTH;
                                errorDesc = "Excluded because of length (" + fileLenght + ")";
                                activities.noDocument(_id, version);
                                continue;
                            }

                            if (!activities.checkMimeTypeIndexable(mimeType)) {
                                Logging.connectors.warn("GridFS: Document " + _id + " has a mime type excluded by the output connector ('" + mimeType + "') - skipping.");
                                errorCode = activities.EXCLUDED_MIMETYPE;
                                errorDesc = "Excluded because of mime type (" + mimeType + ")";
                                activities.noDocument(_id, version);
                                continue;
                            }

                            if (!activities.checkDateIndexable(createdDate)) {
                                Logging.connectors.warn("GridFS: Document " + _id + " has a date excluded by the output connector (" + createdDate + ") - skipping.");
                                errorCode = activities.EXCLUDED_DATE;
                                errorDesc = "Excluded because of date (" + createdDate + ")";
                                activities.noDocument(_id, version);
                                continue;
                            }

                            RepositoryDocument rd = new RepositoryDocument();
                            rd.setCreatedDate(createdDate);
                            rd.setModifiedDate(createdDate);
                            rd.setFileName(fileName);
                            rd.setMimeType(mimeType);
                            String[] aclsArray = null;
                            String[] denyAclsArray = null;
                            if (acl != null) {
                                try {
                                    Object aclObject = document.getMetaData().get(acl);
                                    if (aclObject != null) {
                                        List<String> acls = (List<String>) aclObject;
                                        aclsArray = (String[]) acls.toArray();
                                    }
                                } catch (ClassCastException e) {
                                    // This is bad because security will fail
                                    Logging.connectors.warn("GridFS: Document " + _id + " metadata ACL field doesn't contain List<String> type.");
                                    errorCode = "ACLTYPE";
                                    errorDesc = "Allow ACL field doesn't contain List<String> type.";
                                    throw new ManifoldCFException("Security decoding error: " + e.getMessage(), e);
                                }
                            }
                            if (denyAcl != null) {
                                try {
                                    Object denyAclObject = document.getMetaData().get(denyAcl);
                                    if (denyAclObject != null) {
                                        List<String> denyAcls = (List<String>) denyAclObject;
                                        denyAcls.add(GLOBAL_DENY_TOKEN);
                                        denyAclsArray = (String[]) denyAcls.toArray();
                                    }
                                } catch (ClassCastException e) {
                                    // This is bad because security will fail
                                    Logging.connectors.warn("GridFS: Document " + _id + " metadata DenyACL field doesn't contain List<String> type.");
                                    errorCode = "ACLTYPE";
                                    errorDesc = "Deny ACL field doesn't contain List<String> type.";
                                    throw new ManifoldCFException("Security decoding error: " + e.getMessage(), e);
                                }
                            }
                            rd.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT, aclsArray, denyAclsArray);

                            InputStream is = document.getInputStream();
                            try {
                                rd.setBinary(is, fileLenght);
                                try {
                                    activities.ingestDocumentWithException(_id, version, urlValue, rd);
                                } catch (IOException e) {
                                    handleIOException(e);
                                }
                            } finally {
                                try {
                                    is.close();
                                } catch (IOException e) {
                                    handleIOException(e);
                                }
                            }
                            gfs.getDB().getMongo().close();
                            session = null;
                            errorCode = "OK";
                        } else {
                            Logging.connectors.warn("GridFS: Document " + _id + " has a invalid URL: " + urlValue + " - skipping.");
                            errorCode = activities.BAD_URL;
                            errorDesc = "Excluded because document had illegal URL ('" + urlValue + "')";
                            activities.noDocument(_id, version);
                        }
                    } else {
                        Logging.connectors.warn("GridFS: Document " + _id + " has a null URL - skipping.");
                        errorCode = activities.NULL_URL;
                        errorDesc = "Excluded because document had a null URL.";
                        activities.noDocument(_id, version);
                    }
                } finally {
                    if (errorCode != null) {
                        activities.recordActivity(startTime, ACTIVITY_FETCH, document.getLength(), _id, errorCode, errorDesc, null);
                    }
                }
            }
        }
    }

    protected static void handleIOException(IOException e) throws ManifoldCFException, ServiceInterruption {
        if (e instanceof InterruptedIOException) {
            throw new ManifoldCFException(e.getMessage(), e, ManifoldCFException.INTERRUPTED);
        } else {
            throw new ManifoldCFException(e.getMessage(), e);
        }
    }

    /**
     * Output the configuration header section. This method is called in the
     * head section of the connector's configuration page. Its purpose is to add
     * the required tabs to the list, and to output any javascript methods that
     * might be needed by the configuration editing HTML. The connector does not
     * need to be connected for this method to be called.
     *
     * @param threadContext is the local thread context.
     * @param out is the output to which any HTML should be sent.
     * @param parameters are the configuration parameters, as they currently
     * exist, for this connection being configured.
     * @param tabsArray is an array of tab names. Add to this array any tab
     * names that are specific to the connector.
     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
     * @throws java.io.IOException
     */
    @Override
    public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
        tabsArray.add(Messages.getString(locale, GRIDFS_SERVER_TAB_RESOURCE));
        Map<String, String> paramMap = new HashMap<String, String>();

        fillInServerParameters(paramMap, out, parameters);

        Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_HEADER_FORWARD, paramMap, true);
    }

    /**
     * Output the configuration body section. This method is called in the body
     * section of the connector's configuration page. Its purpose is to present
     * the required form elements for editing. The coder can presume that the
     * HTML that is output from this configuration will be within appropriate
     * <html>, <body>, and <form> tags. The name of the form is always
     * "editconnection". The connector does not need to be connected for this
     * method to be called.
     *
     * @param threadContext is the local thread context.
     * @param out is the output to which any HTML should be sent.
     * @param parameters are the configuration parameters, as they currently
     * exist, for this connection being configured.
     * @param tabName is the current tab name.
     */
    @Override
    public void outputConfigurationBody(IThreadContext threadContext,
            IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {

        Map<String, String> paramMap = new HashMap<String, String>();
        paramMap.put(TAB_NAME_PARAM, tabName);

        fillInServerParameters(paramMap, out, parameters);

        Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_FORWARD_SERVER, paramMap, true);
    }

    /**
     * Process a configuration post. This method is called at the start of the
     * connector's configuration page, whenever there is a possibility that form
     * data for a connection has been posted. Its purpose is to gather form
     * information and modify the configuration parameters accordingly. The name
     * of the posted form is always "editconnection". The connector does not
     * need to be connected for this method to be called.
     *
     * @param threadContext is the local thread context.
     * @param variableContext is the set of variables available from the post,
     * including binary file post information.
     * @param parameters are the configuration parameters, as they currently
     * exist, for this connection being configured.
     * @return null if all is well, or a string error message if there is an
     * error that should prevent saving of the connection (and cause a
     * redirection to an error page).
     */
    @Override
    public String processConfigurationPost(IThreadContext threadContext,
            IPostParameters variableContext, Locale locale, ConfigParams parameters)
            throws ManifoldCFException {

        String username = variableContext.getParameter(GridFSConstants.USERNAME_PARAM);
        if (username != null) {
            parameters.setParameter(GridFSConstants.USERNAME_PARAM, username);
        }

        String password = variableContext.getParameter(GridFSConstants.PASSWORD_PARAM);
        if (password != null) {
            parameters.setObfuscatedParameter(GridFSConstants.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));
        }

        String db = variableContext.getParameter(GridFSConstants.DB_PARAM);
        if (db != null) {
            parameters.setParameter(GridFSConstants.DB_PARAM, db);
        }

        String bucket = variableContext.getParameter(GridFSConstants.BUCKET_PARAM);
        if (bucket != null) {
            parameters.setParameter(GridFSConstants.BUCKET_PARAM, bucket);
        }

        String port = variableContext.getParameter(GridFSConstants.PORT_PARAM);
        if (port != null) {
            parameters.setParameter(GridFSConstants.PORT_PARAM, port);
        }

        String host = variableContext.getParameter(GridFSConstants.HOST_PARAM);
        if (host != null) {
            parameters.setParameter(GridFSConstants.HOST_PARAM, host);
        }

        String url = variableContext.getParameter(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM);
        if (url != null) {
            parameters.setParameter(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM, url);
        }

        String acl = variableContext.getParameter(GridFSConstants.ACL_RETURN_FIELD_NAME_PARAM);
        if (acl != null) {
            parameters.setParameter(GridFSConstants.ACL_RETURN_FIELD_NAME_PARAM, acl);
        }

        String denyAcl = variableContext.getParameter(GridFSConstants.DENY_ACL_RETURN_FIELD_NAME_PARAM);
        if (denyAcl != null) {
            parameters.setParameter(GridFSConstants.DENY_ACL_RETURN_FIELD_NAME_PARAM, denyAcl);
        }

        return null;
    }

    /**
     * View configuration. This method is called in the body section of the
     * connector's view configuration page. Its purpose is to present the
     * connection information to the user. The coder can presume that the HTML
     * that is output from this configuration will be within appropriate <html>
     * and <body> tags. The connector does not need to be connected for this
     * method to be called.
     *
     * @param threadContext is the local thread context.
     * @param out is the output to which any HTML should be sent.
     * @param parameters are the configuration parameters, as they currently
     * exist, for this connection being configured.
     */
    @Override
    public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
        Map<String, String> paramMap = new HashMap<String, String>();

        fillInServerParameters(paramMap, out, parameters);

        Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIG_FORWARD, paramMap, true);
    }

    /**
     * Setup a session.
     *
     * @throws ManifoldCFException
     */
    protected void getSession() throws ManifoldCFException {
        if (session == null) {

            if (StringUtils.isEmpty(db) || StringUtils.isEmpty(bucket)) {
                throw new ManifoldCFException("GridFS: Database or bucket name cannot be empty.");
            }

            if (StringUtils.isEmpty(url)) {
                throw new ManifoldCFException("GridFS: Metadata URL field cannot be empty.");
            }

            if (StringUtils.isEmpty(host) && StringUtils.isEmpty(port)) {
                try {
                    session = new MongoClient().getDB(db);
                } catch (UnknownHostException ex) {
                    throw new ManifoldCFException("GridFS: Default host is not found. Does mongod process run?" + ex.getMessage(), ex);
                }
            } else if (!StringUtils.isEmpty(host) && StringUtils.isEmpty(port)) {
                try {
                    session = new MongoClient(host).getDB(db);
                } catch (UnknownHostException ex) {
                    throw new ManifoldCFException("GridFS: Given host information is not valid or mongod process doesn't run" + ex.getMessage(), ex);
                }
            } else if (!StringUtils.isEmpty(host) && !StringUtils.isEmpty(port)) {
                try {
                    int integerPort = Integer.parseInt(port);
                    session = new MongoClient(host, integerPort).getDB(db);
                } catch (UnknownHostException ex) {
                    throw new ManifoldCFException("GridFS: Given information is not valid or mongod process doesn't run" + ex.getMessage(), ex);
                } catch (NumberFormatException ex) {
                    throw new ManifoldCFException("GridFS: Given port is not valid number. " + ex.getMessage(), ex);
                }
            } else if (StringUtils.isEmpty(host) && !StringUtils.isEmpty(port)) {
                try {
                    int integerPort = Integer.parseInt(port);
                    session = new MongoClient(host, integerPort).getDB(db);
                } catch (UnknownHostException ex) {
                    throw new ManifoldCFException("GridFS: Given information is not valid or mongod process doesn't run" + ex.getMessage(), ex);
                } catch (NumberFormatException ex) {
                    throw new ManifoldCFException("GridFS: Given port is not valid number. " + ex.getMessage(), ex);
                }
            }

            if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
                boolean auth = session.authenticate(username, password.toCharArray());
                if (!auth) {
                    throw new ManifoldCFException("GridFS: Given database username and password doesn't match.");
                }
            }
            lastSessionFetch = System.currentTimeMillis();
        }
    }

    /**
     * Fill in a Server tab configuration parameter map for calling a Velocity
     * template.
     *
     * @param paramMap is the map to fill in
     * @param parameters is the current set of configuration parameters
     */
    public void fillInServerParameters(Map<String, String> paramMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
        String usernameParam = parameters.getParameter(GridFSConstants.USERNAME_PARAM);
        paramMap.put(GridFSConstants.USERNAME_PARAM, usernameParam);

        String passwordParam = parameters.getObfuscatedParameter(GridFSConstants.PASSWORD_PARAM);
        if (passwordParam == null) {
          passwordParam = StringUtils.EMPTY;
        } else {
          passwordParam = mapper.mapPasswordToKey(passwordParam);
        }
        paramMap.put(GridFSConstants.PASSWORD_PARAM, passwordParam);

        String dbParam = parameters.getParameter(GridFSConstants.DB_PARAM);
        if (StringUtils.isEmpty(dbParam)) {
            dbParam = GridFSConstants.DEFAULT_DB_NAME;
        }
        paramMap.put(GridFSConstants.DB_PARAM, dbParam);
        String bucketParam = parameters.getParameter(GridFSConstants.BUCKET_PARAM);
        if (StringUtils.isEmpty(bucketParam)) {
            bucketParam = GridFSConstants.DEFAULT_BUCKET_NAME;
        }
        paramMap.put(GridFSConstants.BUCKET_PARAM, bucketParam);
        String hostParam = parameters.getParameter(GridFSConstants.HOST_PARAM);
        paramMap.put(GridFSConstants.HOST_PARAM, hostParam);
        String portParam = parameters.getParameter(GridFSConstants.PORT_PARAM);
        paramMap.put(GridFSConstants.PORT_PARAM, portParam);
        String urlParam = parameters.getParameter(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM);
        paramMap.put(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM, urlParam);
        String aclParam = parameters.getParameter(GridFSConstants.ACL_RETURN_FIELD_NAME_PARAM);
        paramMap.put(GridFSConstants.ACL_RETURN_FIELD_NAME_PARAM, aclParam);
        String denyAclParam = parameters.getParameter(GridFSConstants.DENY_ACL_RETURN_FIELD_NAME_PARAM);
        paramMap.put(GridFSConstants.DENY_ACL_RETURN_FIELD_NAME_PARAM, denyAclParam);
    }

    /**
     * Special column names, as far as document queries are concerned
     */
    protected static HashMap documentKnownColumns;

    static {
        documentKnownColumns = new HashMap();
        documentKnownColumns.put(GridFSConstants.DEFAULT_ID_FIELD_NAME, "");
        documentKnownColumns.put(GridFSConstants.URL_RETURN_FIELD_NAME_PARAM, "");
    }

    /**
     * Apply metadata to a repository document.
     *
     * @param rd is the repository document to apply the metadata to.
     * @param metadataMap is the resultset row to use to get the metadata. All
     * non-special columns from this row will be considered to be metadata.
     */
    protected void applyMetadata(RepositoryDocument rd, DBObject metadataMap)
            throws ManifoldCFException {
        // Cycle through the document's fields
        Iterator iter = metadataMap.keySet().iterator();
        while (iter.hasNext()) {
            String fieldName = (String) iter.next();
            if (documentKnownColumns.get(fieldName) == null) {
                // Consider this field to contain metadata.
                // We can only accept non-binary metadata at this time.
                Object metadata = metadataMap.get(fieldName);
                if (!(metadata instanceof String)) {
                    throw new ManifoldCFException("Metadata field '" + fieldName + "' must be convertible to a string.");
                }
                rd.addField(fieldName, metadata.toString());
            }
        }
    }
}
