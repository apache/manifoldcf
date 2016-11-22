package org.apache.manifoldcf.crawler.connectors.nuxeo;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import org.apache.manifoldcf.crawler.connectors.nuxeo.client.NuxeoClient;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Ace;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Acl;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Attachment;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Document;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoResponse;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.IRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
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

	protected final static String ACTIVITY_READ = "read document";

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

	protected NuxeoClient nuxeoClient = null;

	// Constructor
	public NuxeoRepositoryConnector() {
		super();
	}

	public void setNuxeoClient(NuxeoClient nuxeoClient) {
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
		String nuxeoPassword = parameters.getParameter(NuxeoConfiguration.Server.PASSWORD);

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
			nuxeoPassword = mapper.mapKeyToPassword(nuxeoPassword);

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

		try {
			initNuxeoClient();
		} catch (ManifoldCFException manifoldCFException) {
			logger.debug("Not possible to initialize Nuxeo client. Reason: {}", manifoldCFException.getMessage());
			manifoldCFException.printStackTrace();
		}
	}

	// Check the connection
	@Override
	public String check() throws ManifoldCFException {
		try {
			if (!isConnected()) {
				initNuxeoClient();
			}

			Boolean result = nuxeoClient.check();

			if (result)
				return super.check();
			else
				throw new ManifoldCFException("Nuxeo instance could not be reached");

		} catch (ServiceInterruption serviceInterruption) {
			return "Connection temporarily failed: " + serviceInterruption.getMessage();
		} catch (ManifoldCFException manifoldCFException) {
			return "Connection failed: " + manifoldCFException.getMessage();
		} catch (Exception e) {
			return "Connection failed: " + e.getMessage();
		}
	}

	/**
	 * Initialize Nuxeo client using the configured parameters.
	 * 
	 * @throws ManifoldCFException
	 */
	private void initNuxeoClient() throws ManifoldCFException {
		int portInt;

		if (nuxeoClient == null) {

			if (StringUtils.isEmpty(protocol)) {
				throw new ManifoldCFException(
						"Parameter " + NuxeoConfiguration.Server.PROTOCOL + " required but not set");
			}

			if (StringUtils.isEmpty(host)) {
				throw new ManifoldCFException("Parameter " + NuxeoConfiguration.Server.HOST + " required but not set");
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

			nuxeoClient = new NuxeoClient(protocol, host, portInt, path, username, password);

			lastSessionFetch = System.currentTimeMillis();

		}

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
			nuxeoClient.close();
			nuxeoClient = null;
			lastSessionFetch = -1;
		}
	}

	/** SEEDING **/
	@Override
	public String addSeedDocuments(ISeedingActivity activities, Specification spec, String lastSeedVersion,
			long seedTime, int jobMode) throws ManifoldCFException, ServiceInterruption {

		if (!isConnected())
			initNuxeoClient();

		try {

			int lastStart = 0;
			int defaultSize = 50;
			Boolean isLast = true;
			NuxeoSpecification ns = NuxeoSpecification.from(spec);
			List<String> domains = ns.getDomains();
			List<String> documentsType = ns.getDocumentsType();

			do {
				final NuxeoResponse<Document> response = nuxeoClient.getDocuments(domains, documentsType,
						lastSeedVersion, lastStart, defaultSize, isLast);

				for (Document doc : response.getResults()) {
					activities.addSeedDocument(doc.getUid());
				}

				lastStart++;
				isLast = response.isLast();

			} while (!isLast);

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

			lastSeedVersion = sdf.format(new Date());

			return lastSeedVersion;
		} catch (Exception exception) {
			long interruptionRetryTime = 5L * 60L * 1000L;
			String message = "Server appears down during seeding: " + exception.getMessage();
			throw new ServiceInterruption(message, exception, System.currentTimeMillis() + interruptionRetryTime, -1L,
					3, true);
		}
	}

	/** PROCESS DOCUMENTS **/
	@Override
	public void processDocuments(String[] documentsIdentifieres, IExistingVersions statuses, Specification spec,
			IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
			throws ManifoldCFException, ServiceInterruption {

		for (int i = 0; i < documentsIdentifieres.length; i++) {

			String documentId = documentsIdentifieres[i];
			String version = statuses.getIndexedVersionString(documentId);

			long startTime = System.currentTimeMillis();
			ProcessResult pResult = null;
			boolean doLog = true;

			try {

				if (!isConnected()) {
					initNuxeoClient();
				}

				pResult = processDocument(documentId, spec, version, activities, doLog,
						Maps.<String, String> newHashMap());
			} catch (Exception exception) {
				long interruptionRetryTime = 5L * 60L * 1000L;
				String message = "Server appears down during seeding: " + exception.getMessage();
				throw new ServiceInterruption(message, exception, System.currentTimeMillis() + interruptionRetryTime,
						-1L, 3, true);
			} finally {
				if (doLog)
					if (pResult != null && pResult.errorCode != null && !pResult.errorCode.isEmpty())
						activities.recordActivity(new Long(startTime), ACTIVITY_READ, pResult.fileSize, documentId,
								pResult.errorCode, pResult.errorDecription, null);
			}

		}
	}

	/**
	 * @param documentId
	 * @param version
	 * @param activities
	 * @param doLog
	 * @param newHashMap
	 * @return
	 */
	private ProcessResult processDocument(String documentId, Specification spec, String version,
			IProcessActivity activities, boolean doLog, HashMap<String, String> extraProperties)
			throws ManifoldCFException, ServiceInterruption, IOException {

		Document doc = nuxeoClient.getDocument(documentId);

		return processDocumentInternal(doc, documentId, spec, version, activities, doLog, extraProperties);
	}

	/**
	 * @param doc
	 * @param documentId
	 * @param version
	 * @param activities
	 * @param doLog
	 * @param extraProperties
	 * @return
	 */
	private ProcessResult processDocumentInternal(Document doc, String manifoldDocumentIdentifier, Specification spec,
			String version, IProcessActivity activities, boolean doLog, HashMap<String, String> extraProperties)
			throws ManifoldCFException, ServiceInterruption, IOException {

		RepositoryDocument rd = new RepositoryDocument();
		NuxeoSpecification ns = NuxeoSpecification.from(spec);

		Date lastModified = doc.getLastModified();

		DateFormat df = DateFormat.getDateTimeInstance();

		String lastVersion = null;

		if (lastModified != null)
			lastVersion = df.format(lastModified);

		if (doc.getState() != null && doc.getState().equalsIgnoreCase(Document.DELETED)) {
			activities.deleteDocument(manifoldDocumentIdentifier);
			return new ProcessResult(doc.getLenght(), "DELETED", "");
		}

		if (!activities.checkDocumentNeedsReindexing(manifoldDocumentIdentifier, lastVersion)) {
			return new ProcessResult(doc.getLenght(), "RETAINED", "");
		}

		if (doc.getUid() == null) {
			activities.deleteDocument(manifoldDocumentIdentifier);
			return new ProcessResult(doc.getLenght(), "DELETED", "");
		}

		// Add respository document information
		rd.setMimeType(doc.getMediatype());
		if (lastModified != null)
			rd.setModifiedDate(lastModified);
		rd.setIndexingDate(new Date());

		// Adding Document Metadata
		Map<String, Object> docMetadata = doc.getMetadataAsMap();

		for (Entry<String, Object> entry : docMetadata.entrySet()) {
			if (entry.getValue() instanceof List) {
				List<?> list = (List<?>) entry.getValue();
				rd.addField(entry.getKey(), list.toArray(new String[list.size()]));
			} else {
				rd.addField(entry.getKey(), entry.getValue().toString());
			}

		}

		if (ns.isProcessTags())
			rd.addField("Tags", getTagsFromDocument(doc));

		String documentUri = nuxeoClient.getPathDocument(doc.getUid());

		// Set repository ACLs
		String[] permissions = getPermissionDocument(doc);
		rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, permissions);
		rd.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, new String[] { GLOBAL_DENY_TOKEN });
		rd.setBinary(doc.getContentStream(), doc.getLenght());

		// Action
		activities.ingestDocumentWithException(manifoldDocumentIdentifier, lastVersion, documentUri, rd);

		if (ns.isProcessAttachments())
			for (Attachment att : doc.getAttachments()) {
				RepositoryDocument att_rd = new RepositoryDocument();
				String attDocumentUri = nuxeoClient.getPathDocument(doc.getUid()) + "_" + att.getName();

				att_rd.setMimeType(att.getMime_type());
				att_rd.setBinary(doc.getContentStream(), att.getLength());

				if (lastModified != null)
					att_rd.setModifiedDate(lastModified);
				att_rd.setIndexingDate(new Date());

				att_rd.addField(Attachment.ATT_KEY_NAME, att.getName());
				att_rd.addField(Attachment.ATT_KEY_LENGTH, String.valueOf(att.getLength()));
				att_rd.addField(Attachment.ATT_KEY_URL, att.getUrl());

				// Set repository ACLs
				att_rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, permissions);
				att_rd.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,
						new String[] { GLOBAL_DENY_TOKEN });

				activities.ingestDocumentWithException(manifoldDocumentIdentifier, attDocumentUri, lastVersion,
						attDocumentUri, att_rd);
			}

		return new ProcessResult(doc.getLenght(), "OK", StringUtils.EMPTY);
	}

	public String[] getPermissionDocument(Document doc) {

		List<String> permissions = new ArrayList<String>();
		try {
			Acl acl = nuxeoClient.getAcl(doc.getUid());

			for (Ace ace : acl.getAces()) {
				if (ace.getStatus().equalsIgnoreCase("effective") && ace.isGranted()) {
					permissions.add(ace.getName());
				}
			}

			return permissions.toArray(new String[0]);

		} catch (Exception e) {
			return new String[] {};
		}
	}

	public String[] getTagsFromDocument(Document doc) {
		try {
			return nuxeoClient.getTags(doc.getUid());
		} catch (Exception e) {
			return new String[] {};
		}
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

		paramMap.put(NuxeoConfiguration.Specification.DOMAINS.toUpperCase(), ns.getDomains());
		paramMap.put(NuxeoConfiguration.Specification.DOCUMENTS_TYPE.toUpperCase(), ns.documentsType);
		paramMap.put(NuxeoConfiguration.Specification.PROCESS_TAGS.toUpperCase(), ns.isProcessTags().toString());
		paramMap.put(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS.toUpperCase(),
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

		paramMap.put(NuxeoConfiguration.Specification.DOMAINS.toUpperCase(), ns.getDomains());
		paramMap.put(NuxeoConfiguration.Specification.DOCUMENTS_TYPE.toUpperCase(), ns.getDocumentsType());
		paramMap.put(NuxeoConfiguration.Specification.PROCESS_TAGS.toUpperCase(), ns.isProcessTags());
		paramMap.put(NuxeoConfiguration.Specification.PROCESS_ATTACHMENTS.toUpperCase(), ns.isProcessAttachments());

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

	public static class NuxeoSpecification {

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
