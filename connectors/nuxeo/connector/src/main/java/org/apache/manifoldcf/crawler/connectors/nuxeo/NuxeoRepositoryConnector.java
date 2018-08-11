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

package org.apache.manifoldcf.crawler.connectors.nuxeo;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoAttachment;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoDocumentHelper;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.IRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;

import org.apache.manifoldcf.crawler.system.Logging;

import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.spi.NuxeoClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 *
 * Nuxeo Repository Connector class
 *
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoRepositoryConnector extends BaseRepositoryConnector {

    private static final String URI_DOCUMENT = "SELECT * FROM Document";

    private final static String ACTIVITY_READ = "read document";

    // Configuration tabs
    private static final String NUXEO_SERVER_TAB_PROPERTY = "NuxeoRepositoryConnector.Server";

    // Specification tabs
    private static final String CONF_DOMAINS_TAB_PROPERTY = "NuxeoRepositoryConnector.Domains";
    private static final String CONF_DOCUMENTS_TYPE_TAB_PROPERTY = "NuxeoRepositoryConnector.DocumentsType";
    private static final String CONF_DOCUMENT_PROPERTY = "NuxeoRepositoryConnector.Documents";

    // Prefix for nuxeo configuration and specification parameters
    private static final String PARAMETER_PREFIX = "nuxeo_";

    // Templates for Nuxeo configuration
    /**
     * Javascript to check the configuration parameters
     */
    private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration_conf.js";

    /**
     * Server edit tab template
     */
    private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_conf_server.html";

    /**
     * Server view tab template
     */
    private static final String VIEW_CONFIG_FORWARD = "viewConfiguration_conf.html";

    // Templates for Nuxeo specification
    /**
     * Forward to the javascript to check the specification parameters for the
     * job
     */
    private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification_conf.js";

    /**
     * Forward to the template to edit domains for the job
     */
    private static final String EDIT_SPEC_FORWARD_CONF_DOMAINS = "editSpecification_confDomains.html";

    /**
     * Forward to the template to edit documents type for the job
     */
    private static final String EDIT_SPEC_FORWARD_CONF_DOCUMENTS_TYPE = "editSpecification_confDocumentsType.html";

    /**
     * Forward to the template to edit document properties for the job
     */
    private static final String EDIT_SPEC_FORWARD_CONF_DOCUMENTS = "editSpecification_confDocuments.html";

    /**
     * Forward to the template to view the specification parameters for the job
     */
    private static final String VIEW_SPEC_FORWARD = "viewSpecification_conf.html";

    protected long lastSessionFetch = -1L;
    protected static final long timeToRelease = 300000L;

    private Logger logger = LoggerFactory.getLogger(NuxeoRepositoryConnector.class);

    /* Nuxeo instance parameters */
    protected String protocol = null;
    protected String host = null;
    protected String port = null;
    protected String path = null;
    protected String username = null;
    protected String password = null;

    private NuxeoClient nuxeoClient = null;

    // Constructor
    public NuxeoRepositoryConnector() {
        super();
    }

    void setNuxeoClient(NuxeoClient nuxeoClient) {
        this.nuxeoClient = nuxeoClient;
    }

    @Override
    public String[] getActivitiesList() {
        return new String[] { ACTIVITY_READ };
    }

    @Override
    public String[] getBinNames(String documentIdenfitier) {
        return new String[] { host };
    }

    /** CONFIGURATION CONNECTOR **/
    @Override
    public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale,
                                          ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {

        // Server tab
        tabsArray.add(Messages.getString(locale, NUXEO_SERVER_TAB_PROPERTY));

        Map<String, String> paramMap = new HashMap<String, String>();

        // Fill in the parameters form each tab
        fillInServerConfigurationMap(paramMap, out, parameters);

        Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_HEADER_FORWARD, paramMap, true);
    }

    @Override
    public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale,
                                        ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {

        // Call the Velocity tempaltes for each tab
        Map<String, String> paramMap = new HashMap<String, String>();

        // Set the tab name
        paramMap.put("TabName", tabName);

        // Fill in the parameters
        fillInServerConfigurationMap(paramMap, out, parameters);

        // Server tab
        Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_FORWARD_SERVER, paramMap, true);

    }

    private static void fillInServerConfigurationMap(Map<String, String> serverMap, IPasswordMapperActivity mapper,
                                                     ConfigParams parameters) {

        String nuxeoProtocol = parameters.getParameter(NuxeoConfiguration.Server.PROTOCOL);
        String nuxeoHost = parameters.getParameter(NuxeoConfiguration.Server.HOST);
        String nuxeoPort = parameters.getParameter(NuxeoConfiguration.Server.PORT);
        String nuxeoPath = parameters.getParameter(NuxeoConfiguration.Server.PATH);
        String nuxeoUsername = parameters.getParameter(NuxeoConfiguration.Server.USERNAME);
        String nuxeoPassword = parameters.getObfuscatedParameter(NuxeoConfiguration.Server.PASSWORD);

        if (nuxeoProtocol == null)
            nuxeoProtocol = NuxeoConfiguration.Server.PROTOCOL_DEFAULT_VALUE;
        if (nuxeoHost == null)
            nuxeoHost = NuxeoConfiguration.Server.HOST_DEFAULT_VALUE;
        if (nuxeoPort == null)
            nuxeoPort = NuxeoConfiguration.Server.PORT_DEFAULT_VALUE;
        if (nuxeoPath == null)
            nuxeoPath = NuxeoConfiguration.Server.PATH_DEFAULT_VALUE;
        if (nuxeoUsername == null)
            nuxeoUsername = NuxeoConfiguration.Server.USERNAME_DEFAULT_VALUE;
        if (nuxeoPassword == null)
            nuxeoPassword = NuxeoConfiguration.Server.PASSWORD_DEFAULT_VALUE;
        else
            nuxeoPassword = mapper.mapPasswordToKey(nuxeoPassword);

        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PROTOCOL, nuxeoProtocol);
        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.HOST, nuxeoHost);
        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PORT, nuxeoPort);
        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PATH, nuxeoPath);
        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.USERNAME, nuxeoUsername);
        serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PASSWORD, nuxeoPassword);

    }

    @Override
    public String processConfigurationPost(IThreadContext thredContext, IPostParameters variableContext,
                                           ConfigParams parameters) {

        String nuxeoProtocol = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PROTOCOL);
        if (nuxeoProtocol != null)
            parameters.setParameter(NuxeoConfiguration.Server.PROTOCOL, nuxeoProtocol);

        String nuxeoHost = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.HOST);
        if (nuxeoHost != null)
            parameters.setParameter(NuxeoConfiguration.Server.HOST, nuxeoHost);

        String nuxeoPort = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PORT);
        if (nuxeoPort != null)
            parameters.setParameter(NuxeoConfiguration.Server.PORT, nuxeoPort);

        String nuxeoPath = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PATH);
        if (nuxeoPath != null)
            parameters.setParameter(NuxeoConfiguration.Server.PATH, nuxeoPath);

        String nuxeoUsername = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.USERNAME);
        if (nuxeoUsername != null)
            parameters.setParameter(NuxeoConfiguration.Server.USERNAME, nuxeoUsername);

        String nuxeoPassword = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PASSWORD);
        if (nuxeoPassword != null)
            parameters.setObfuscatedParameter(NuxeoConfiguration.Server.PASSWORD,
                    variableContext.mapKeyToPassword(nuxeoPassword));

        return null;
    }

    @Override
    public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
            throws ManifoldCFException, IOException {

        Map<String, String> paramMap = new HashMap<String, String>();

        fillInServerConfigurationMap(paramMap, out, parameters);

        Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIG_FORWARD, paramMap, true);
    }

    /** CONNECTION **/
    @Override
    public void connect(ConfigParams configParams) {
        super.connect(configParams);

        protocol = params.getParameter(NuxeoConfiguration.Server.PROTOCOL);
        host = params.getParameter(NuxeoConfiguration.Server.HOST);
        port = params.getParameter(NuxeoConfiguration.Server.PORT);
        path = params.getParameter(NuxeoConfiguration.Server.PATH);
        username = params.getParameter(NuxeoConfiguration.Server.USERNAME);
        password = params.getObfuscatedParameter(NuxeoConfiguration.Server.PASSWORD);

    }

    @Override
    public void disconnect() throws ManifoldCFException {
        shutdownNuxeoClient();
        protocol = null;
        host = null;
        port = null;
        path = null;
        username = null;
        password = null;
        super.disconnect();
    }

    // Check the connection
    @Override
    public String check() throws ManifoldCFException {
        //shutdownNuxeoClient();
        try {
            initNuxeoClient();
        } catch (NuxeoClientException ex) {
            return "Connection failed: "+ex.getMessage();
        }

        return super.check();
    }

    /**
     * Initialize Nuxeo client using the configured parameters.
     *
     * @throws ManifoldCFException
     */
    private void initNuxeoClient() throws ManifoldCFException {
        if (nuxeoClient == null) {

            if (StringUtils.isEmpty(protocol)) {
                throw new ManifoldCFException(
                        "Parameter " + NuxeoConfiguration.Server.PROTOCOL + " required but not set");
            }

            if (StringUtils.isEmpty(host)) {
                throw new ManifoldCFException("Parameter " + NuxeoConfiguration.Server.HOST + " required but not set");
            }

            String url = getUrl();
            nuxeoClient = new NuxeoClient.Builder().
                    url(url).
                    authentication(username, password).connect();
            nuxeoClient.schemas("*"); // TODO Make This Configurable
            nuxeoClient.header("properties", "*");

            lastSessionFetch = System.currentTimeMillis();

        }
    }

    /**
     * Shut down Nuxeo client
     */
    private void shutdownNuxeoClient() {
        if (nuxeoClient != null) {
            nuxeoClient.disconnect();
            nuxeoClient = null;
            lastSessionFetch = -1L;
        }
    }

    /**
     * Formatter URL
     *
     * @throws ManifoldCFException
     */
    protected String getUrl() throws ManifoldCFException {
        int portInt;
        if (protocol == null || host == null || path == null){
            throw new ManifoldCFException("Nuxeo Endpoint Bad Configured");
        }
        if (port != null && port.length() > 0) {
            try {
                portInt = Integer.parseInt(port);
            } catch (NumberFormatException formatException) {
                throw new ManifoldCFException("Bad number: " + formatException.getMessage(), formatException);
            }
        } else {
            if (protocol.toLowerCase(Locale.ROOT).equals("http")) {
                portInt = 80;
            } else {
                portInt = 443;
            }
        }

        String url = protocol + "://" + host + ":" + portInt + "/" + path;

        return url;
    }

    @Override
    public boolean isConnected() {
        return nuxeoClient != null;
    }

    @Override
    public void poll() throws ManifoldCFException {
        if (lastSessionFetch == -1L) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime > lastSessionFetch + timeToRelease) {
            shutdownNuxeoClient();
        }
    }

    /** SEEDING **/
    @Override
    public String addSeedDocuments(ISeedingActivity activities, Specification spec, String lastSeedVersion,
                                   long seedTime, int jobMode) throws ManifoldCFException, ServiceInterruption {

        initNuxeoClient();
        try {

            int lastStart = 0;
            int defaultSize = 50;
            Boolean isLast = true;
            NuxeoSpecification ns = NuxeoSpecification.from(spec);
            List<String> domains = ns.getDomains();
            List<String> documentsType = ns.getDocumentsType();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date currentDate =  new Date();

            do {

                Documents docs = getDocsByDate(nuxeoClient, lastSeedVersion, domains, documentsType,
                        defaultSize, lastStart);

                for (Document doc : docs.getDocuments()) {
                    activities.addSeedDocument(doc.getUid());
                }

                lastStart++;
                isLast = docs.isNextPageAvailable();

            } while (isLast);

            return sdf.format(currentDate);

        } catch (NuxeoClientException exception) {
            Logging.connectors.warn("NUXEO: Error adding seed documents: " + exception.getMessage(), exception);
            throw new ManifoldCFException("Failure during seeding: "+exception.getMessage(), exception);
        }
    }

    Documents getDocsByDate(NuxeoClient nuxeoClient, String date, List<String> domains,
                            List<String> documentsType, int limit, int start) {

        String query = "";

        if (date == null || date.isEmpty()) {
            SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
            date = DATE_FORMAT.format(new Date(0));
        }
        query = "dc:modified > '" + date + "'";

        if (!domains.isEmpty()) {
            Iterator<String> itdom = domains.iterator();

            query = String.format(Locale.ROOT, " %s AND ( ecm:path STARTSWITH '/%s'", query, itdom.next());

            while (itdom.hasNext()) {
                query = String.format(Locale.ROOT, "%s OR ecm:path STARTSWITH '/%s'", query, itdom.next());
            }

            query = String.format(Locale.ROOT, "%s )", query);
        }

        if (!documentsType.isEmpty()) {
            Iterator<String> itDocTy = documentsType.iterator();

            query = String.format(Locale.ROOT, " %s AND ( ecm:primaryType = '%s'", query, itDocTy.next());

            while (itDocTy.hasNext()) {
                query = String.format(Locale.ROOT, "%s OR ecm:primaryType = '%s'", query, itDocTy.next());
            }

            query = String.format(Locale.ROOT, "%s )", query);
        }

        query = String.format(Locale.ROOT, URI_DOCUMENT + " where ecm:mixinType != 'HiddenInNavigation' AND ecm:isProxy = 0 " +
                "AND ecm:isCheckedInVersion = 0 AND %s ", query);

        Documents docs = nuxeoClient.repository().query(query, String.valueOf(limit), String.valueOf(start), null, null,
                null, null);

        return docs;
    }

    /** PROCESS DOCUMENTS **/

    @Override
    public void processDocuments(String[] documentsIdentifieres, IExistingVersions statuses, Specification spec,
                                 IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
            throws ManifoldCFException, ServiceInterruption {

        initNuxeoClient();

        for (int i = 0; i < documentsIdentifieres.length; i++) {

            String documentId = documentsIdentifieres[i];
            String indexed_version = statuses.getIndexedVersionString(documentId);

            long startTime = System.currentTimeMillis();
            ProcessResult pResult = null;

            try {
                NuxeoDocumentHelper document = new NuxeoDocumentHelper(nuxeoClient.repository().fetchDocumentById(documentId));

                String version = document.getVersion();

                // Filtering
                if (document.isFolder()){
                    activities.noDocument(documentId, version);
                    continue;
                }

                pResult = processDocument(document, documentId, spec, version, indexed_version,
                        activities, Maps.newHashMap());
            } catch (NuxeoClientException exception) {
                logger.info(String.format(Locale.ROOT, "Error Fetching Nuxeo Document %s. Marking for deletion", documentId));
                activities.deleteDocument(documentId);
            } catch (IOException exception) {
                long interruptionRetryTime = 5L * 60L * 1000L;
                String message = "Server appears down during seeding: " + exception.getMessage();
                throw new ServiceInterruption(message, exception, System.currentTimeMillis() + interruptionRetryTime,
                        -1L, 3, true);
            } finally {
                if (pResult != null && pResult.errorCode != null && !pResult.errorCode.isEmpty()) {
                    activities.recordActivity(new Long(startTime), ACTIVITY_READ, pResult.fileSize, documentId,
                            pResult.errorCode, pResult.errorDecription, null);
                }
            }

        }
    }

    private ProcessResult processDocument(NuxeoDocumentHelper doc, String manifoldDocumentIdentifier,
                                          Specification spec, String version, String indexed_version,
                                          IProcessActivity activities, HashMap<String, String> extraProperties)
            throws ManifoldCFException, ServiceInterruption, IOException {

        RepositoryDocument rd = new RepositoryDocument();
        NuxeoSpecification ns = NuxeoSpecification.from(spec);

        String lastModified = doc.getDocument().getLastModified();
        Date lastModifiedDate = null;

        if (lastModified != null) {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
            try {
                lastModifiedDate = formatter.parse(lastModified);
            } catch (Exception ex) {
                lastModifiedDate = new Date(0);
            }
        }

        int length = doc.getLength();

        if (doc.getDocument().getState() != null
                && doc.getDocument().getState().equalsIgnoreCase(NuxeoDocumentHelper.DELETED)) {
            activities.deleteDocument(manifoldDocumentIdentifier);
            return new ProcessResult(length, "DELETED", "");
        }

        if (!activities.checkDocumentNeedsReindexing(manifoldDocumentIdentifier, lastModified)) {
            return new ProcessResult(length, "RETAINED", "");
        }

        if (doc.getDocument().getUid() == null) {
            activities.deleteDocument(manifoldDocumentIdentifier);
            return new ProcessResult(length, "DELETED", "");
        }

        if (!activities.checkLengthIndexable(length))
        {
            activities.noDocument(manifoldDocumentIdentifier, version);
            return new ProcessResult(length, activities.EXCLUDED_LENGTH,
                    "Document excluded because of length ("+length+")");
        }

        // Document URI
        String url = getUrl();
        if (url.endsWith("/"))
            url = url.substring(0, url.length()-1);
        String documentUri = url + "/nxpath/" +
                doc.getDocument().getRepositoryName() + doc.getDocument().getPath() + "@view_documents";
        if (!activities.checkURLIndexable(documentUri))
        {
            activities.noDocument(manifoldDocumentIdentifier, version);
            return new ProcessResult(length, activities.EXCLUDED_URL,
                    "Document excluded because of URL ('"+documentUri+"')");
        }

        //Modified date
        if (!activities.checkDateIndexable(lastModifiedDate))
        {
            activities.noDocument(manifoldDocumentIdentifier, version);
            return new ProcessResult(length, activities.EXCLUDED_DATE,
                    "Document excluded because of date ("+lastModifiedDate+")");
        }

        // Mime type
        if (!activities.checkMimeTypeIndexable(doc.getMimeType()))
        {
            activities.noDocument(manifoldDocumentIdentifier, version);
            return new ProcessResult(length, activities.EXCLUDED_MIMETYPE,
                    "Document excluded because of mime type ('"+doc.getMimeType()+"')");
        }


        // Add respository document information
        rd.setMimeType(doc.getMimeType());
        rd.setModifiedDate(lastModifiedDate);
        rd.setFileName(doc.getFilename());
        rd.setIndexingDate(new Date());
        rd.setBinary(doc.getContent(), length);

        // Adding Document metadata
        Map<String, Object> docMetadata = doc.getMetadata();

        for (Entry<String, Object> entry : docMetadata.entrySet()) {
            if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                try {
                    rd.addField(entry.getKey(), list.toArray(new String[list.size()]));
                } catch (ArrayStoreException e){
                    continue;
                }
            } else {
                rd.addField(entry.getKey(), entry.getValue().toString());
            }
        }

        if (ns.isProcessTags())
            rd.addField("Tags", doc.getTags(nuxeoClient));

        // Set repository ACLs
        String[] permissions = doc.getPermissions();
        rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, permissions);
        rd.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, new String[] { GLOBAL_DENY_TOKEN });

        // Action
        activities.ingestDocumentWithException(manifoldDocumentIdentifier, version, documentUri, rd);

        if (ns.isProcessAttachments()) {
            for (NuxeoAttachment att : doc.getAttachments(nuxeoClient)) {
                RepositoryDocument att_rd = new RepositoryDocument();
                String attDocumentUri = att.getUrl();

                att_rd.setMimeType(att.getMime_type());
                att_rd.setBinary(att.getData(), att.getLength());

                if (lastModified != null)
                    att_rd.setModifiedDate(lastModifiedDate);
                att_rd.setIndexingDate(new Date());

                att_rd.addField(NuxeoAttachment.ATT_KEY_NAME, att.getName());
                att_rd.addField(NuxeoAttachment.ATT_KEY_LENGTH, String.valueOf(att.getLength()));
                att_rd.addField(NuxeoAttachment.ATT_KEY_DIGEST, att.getDigest());
                att_rd.addField(NuxeoAttachment.ATT_KEY_DIGEST_ALGORITHM, att.getDigestAlgorithm());
                att_rd.addField(NuxeoAttachment.ATT_KEY_ENCODING, att.getEncoding());
                // Set repository ACLs
                att_rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, permissions);
                att_rd.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,
                        new String[] { GLOBAL_DENY_TOKEN });

                activities.ingestDocumentWithException(manifoldDocumentIdentifier, attDocumentUri, lastModified,
                        attDocumentUri, att_rd);
            }
        }

        return new ProcessResult(length, "OK", StringUtils.EMPTY);
    }

    private class ProcessResult {
        private long fileSize;
        private String errorCode;
        private String errorDecription;

        private ProcessResult(long fileSize, String errorCode, String errorDescription) {
            this.fileSize = fileSize;
            this.errorCode = errorCode;
            this.errorDecription = errorDescription;
        }

    }

    @Override
    public int getConnectorModel() {
        return IRepositoryConnector.MODEL_ADD_CHANGE_DELETE;
    }

    /** Specifications **/

    @Override
    public void viewSpecification(IHTTPOutput out, Locale locale, Specification spec, int connectionSequenceNumber)
            throws ManifoldCFException, IOException {

        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));

        NuxeoSpecification ns = NuxeoSpecification.from(spec);

        paramMap.put(NuxeoConfiguration.Specification.DOMAINS.toUpperCase(Locale.ROOT), ns.getDomains());
        paramMap.put(NuxeoConfiguration.Specification.DOCUMENTS_TYPE.toUpperCase(Locale.ROOT), ns.documentsType);
        paramMap.put(NuxeoConfiguration.Specification.PROCESS_TAGS.toUpperCase(Locale.ROOT), ns.isProcessTags().toString());
        paramMap.put(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS.toUpperCase(Locale.ROOT),
                ns.isProcessAttachments().toString());

        Messages.outputResourceWithVelocity(out, locale, VIEW_SPEC_FORWARD, paramMap);
    }

    @Override
    public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
                                           int connectionSequenceNumber) throws ManifoldCFException {

        String seqPrefix = "s" + connectionSequenceNumber + "_";

        // DOMAINS
        String xc = variableContext.getParameter(seqPrefix + "domainscount");

        if (xc != null) {
            // Delete all preconfigured domains
            int i = 0;
            while (i < ds.getChildCount()) {
                SpecificationNode sn = ds.getChild(i);
                if (sn.getType().equals(NuxeoConfiguration.Specification.DOMAINS)) {
                    ds.removeChild(i);
                } else {
                    i++;
                }
            }

            SpecificationNode domains = new SpecificationNode(NuxeoConfiguration.Specification.DOMAINS);
            ds.addChild(ds.getChildCount(), domains);
            int domainsCount = Integer.parseInt(xc);
            i = 0;
            while (i < domainsCount) {
                String domainDescription = "_" + Integer.toString(i);
                String domainOpName = seqPrefix + "domainop" + domainDescription;
                xc = variableContext.getParameter(domainOpName);
                if (xc != null && xc.equals("Delete")) {
                    i++;
                    continue;
                }

                String domainKey = variableContext.getParameter(seqPrefix + "domain" + domainDescription);
                SpecificationNode node = new SpecificationNode(NuxeoConfiguration.Specification.DOMAIN);
                node.setAttribute(NuxeoConfiguration.Specification.DOMAIN_KEY, domainKey);
                domains.addChild(domains.getChildCount(), node);
                i++;
            }

            String op = variableContext.getParameter(seqPrefix + "domainop");
            if (op != null && op.equals("Add")) {
                String domainSpec = variableContext.getParameter(seqPrefix + "domain");
                SpecificationNode node = new SpecificationNode(NuxeoConfiguration.Specification.DOMAIN);
                node.setAttribute(NuxeoConfiguration.Specification.DOMAIN_KEY, domainSpec);
                domains.addChild(domains.getChildCount(), node);
            }
        }

        // TYPE OF DOCUMENTS
        String xt = variableContext.getParameter(seqPrefix + "documentsTypecount");

        if (xt != null) {
            // Delete all preconfigured type of documents
            int i = 0;
            while (i < ds.getChildCount()) {
                SpecificationNode sn = ds.getChild(i);
                if (sn.getType().equals(NuxeoConfiguration.Specification.DOCUMENTS_TYPE)) {
                    ds.removeChild(i);
                } else {
                    i++;
                }
            }

            SpecificationNode documentsType = new SpecificationNode(NuxeoConfiguration.Specification.DOCUMENTS_TYPE);
            ds.addChild(ds.getChildCount(), documentsType);
            int documentsTypeCount = Integer.parseInt(xt);
            i = 0;
            while (i < documentsTypeCount) {
                String documentTypeDescription = "_" + Integer.toString(i);
                String documentTypeOpName = seqPrefix + "documentTypeop" + documentTypeDescription;
                xt = variableContext.getParameter(documentTypeOpName);
                if (xt != null && xt.equals("Delete")) {
                    i++;
                    continue;
                }

                String documentTypeKey = variableContext
                        .getParameter(seqPrefix + "documentType" + documentTypeDescription);
                SpecificationNode node = new SpecificationNode(NuxeoConfiguration.Specification.DOCUMENT_TYPE);
                node.setAttribute(NuxeoConfiguration.Specification.DOCUMENT_TYPE_KEY, documentTypeKey);
                documentsType.addChild(documentsType.getChildCount(), node);
                i++;
            }

            String op = variableContext.getParameter(seqPrefix + "documentTypeop");
            if (op != null && op.equals("Add")) {
                String documentTypeSpec = variableContext.getParameter(seqPrefix + "documentType");
                SpecificationNode node = new SpecificationNode(NuxeoConfiguration.Specification.DOCUMENT_TYPE);
                node.setAttribute(NuxeoConfiguration.Specification.DOCUMENT_TYPE_KEY, documentTypeSpec);
                documentsType.addChild(documentsType.getChildCount(), node);
            }

        }

        // TAGS
        SpecificationNode documents = new SpecificationNode(NuxeoConfiguration.Specification.DOCUMENTS);
        ds.addChild(ds.getChildCount(), documents);

        String processTags = variableContext.getParameter(seqPrefix + NuxeoConfiguration.Specification.PROCESS_TAGS);
        String processAttachments = variableContext
                .getParameter(seqPrefix + NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS);

        if (processTags != null && !processTags.isEmpty()) {
            documents.setAttribute(NuxeoConfiguration.Specification.PROCESS_TAGS, String.valueOf(processTags));
        }
        if (processAttachments != null && !processAttachments.isEmpty()) {
            documents.setAttribute(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS,
                    String.valueOf(processAttachments));
        }

        return null;
    }

    @Override
    public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification spec,
                                        int connectionSequenceNumber, int actualSequenceNumber, String tabName)
            throws ManifoldCFException, IOException {

        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("TabName", tabName);
        paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
        paramMap.put("SelectedNum", Integer.toString(actualSequenceNumber));

        NuxeoSpecification ns = NuxeoSpecification.from(spec);

        paramMap.put(NuxeoConfiguration.Specification.DOMAINS.toUpperCase(Locale.ROOT), ns.getDomains());
        paramMap.put(NuxeoConfiguration.Specification.DOCUMENTS_TYPE.toUpperCase(Locale.ROOT), ns.getDocumentsType());
        paramMap.put(NuxeoConfiguration.Specification.PROCESS_TAGS.toUpperCase(Locale.ROOT), ns.isProcessTags());
        paramMap.put(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS.toUpperCase(Locale.ROOT), ns.isProcessAttachments());

        Messages.outputResourceWithVelocity(out, locale, EDIT_SPEC_FORWARD_CONF_DOMAINS, paramMap);
        Messages.outputResourceWithVelocity(out, locale, EDIT_SPEC_FORWARD_CONF_DOCUMENTS_TYPE, paramMap);
        Messages.outputResourceWithVelocity(out, locale, EDIT_SPEC_FORWARD_CONF_DOCUMENTS, paramMap);

    }

    @Override
    public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification spec,
                                          int connectionSequenceNumber, List<String> tabsArray) throws ManifoldCFException, IOException {

        tabsArray.add(Messages.getString(locale, CONF_DOMAINS_TAB_PROPERTY));
        tabsArray.add(Messages.getString(locale, CONF_DOCUMENTS_TYPE_TAB_PROPERTY));
        tabsArray.add(Messages.getString(locale, CONF_DOCUMENT_PROPERTY));

        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));

        Messages.outputResourceWithVelocity(out, locale, EDIT_SPEC_HEADER_FORWARD, paramMap);
    }

    static class NuxeoSpecification {

        private List<String> domains;
        private List<String> documentsType;
        private Boolean processTags = false;
        private Boolean processAttahcments = false;

        public List<String> getDomains() {
            return this.domains;
        }

        public List<String> getDocumentsType() {
            return this.documentsType;
        }

        public Boolean isProcessTags() {
            return this.processTags;
        }

        public Boolean isProcessAttachments() {
            return this.processAttahcments;
        }

        /**
         * @param spec
         * @return
         */
        public static NuxeoSpecification from(Specification spec) {
            NuxeoSpecification ns = new NuxeoSpecification();

            ns.domains = Lists.newArrayList();
            ns.documentsType = Lists.newArrayList();

            for (int i = 0, len = spec.getChildCount(); i < len; i++) {
                SpecificationNode sn = spec.getChild(i);

                if (sn.getType().equals(NuxeoConfiguration.Specification.DOMAINS)) {
                    for (int j = 0, sLen = sn.getChildCount(); j < sLen; j++) {
                        SpecificationNode spectNode = sn.getChild(j);
                        if (spectNode.getType().equals(NuxeoConfiguration.Specification.DOMAIN)) {
                            ns.domains.add(spectNode.getAttributeValue(NuxeoConfiguration.Specification.DOMAIN_KEY));
                        }
                    }
                } else if (sn.getType().equals(NuxeoConfiguration.Specification.DOCUMENTS_TYPE)) {
                    for (int j = 0, sLen = sn.getChildCount(); j < sLen; j++) {
                        SpecificationNode spectNode = sn.getChild(j);
                        if (spectNode.getType().equals(NuxeoConfiguration.Specification.DOCUMENT_TYPE)) {
                            ns.documentsType.add(
                                    spectNode.getAttributeValue(NuxeoConfiguration.Specification.DOCUMENT_TYPE_KEY));
                        }
                    }
                } else if (sn.getType().equals(NuxeoConfiguration.Specification.DOCUMENTS)) {
                    String procTags = sn.getAttributeValue(NuxeoConfiguration.Specification.PROCESS_TAGS);
                    ns.processTags = Boolean.valueOf(procTags);
                    String procAtt = sn.getAttributeValue(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS);
                    ns.processAttahcments = Boolean.valueOf(procAtt);
                }
            }

            return ns;
        }

    }
}
