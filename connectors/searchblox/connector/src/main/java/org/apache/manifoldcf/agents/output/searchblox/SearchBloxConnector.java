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
package org.apache.manifoldcf.agents.output.searchblox;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxClient.ResponseCode;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SearchBox Output Connector. SearchBox (http://www.searchblox.com/) is a Cloud
 * Based Search Engine. This connector index ManifoldCF crawled content and
 * metadata using the SearchBox REST API
 * (http://www.searchblox.com/developers-2/api-2)
 *
 * @author Rafa Haro <rharo@apache.org>
 * @author Antonio David Perez Morales <adperezmorales@apache.org>
 */
public class SearchBloxConnector extends BaseOutputConnector {

  private final static String SEARCHBLOX_TAB_PARAMETERS = "SearchBloxConnector.Parameters";

  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String EDIT_SPECIFICATION_CONFIGURATION_HTML = "editSpecification_Configuration.html";
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD_PARAMETERS = "editConfiguration_Parameters.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /**
   * Default collection name
   */
  private static final String DEFAULT_COLLECTION = "Custom";
  
  /**
   * Default apiKey
   */
  private static final String DEFAULT_APIKEY = "apiKey";
  
  /**
   * Ingestion activity
   */
  public final static String INGEST_ACTIVITY = "document ingest";

  /**
   * Document removal activity
   */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /**
   * Collection Creation activity
   */
  public final static String CREATION_ACTIVITY = "collection created";

  
  private static final String SEARCHBLOX_ENDPOINT = "endpoint";
  private static final String SEARCHBLOX_INDEXING_FORMAT = "indexformat";
  private static final String SEARCHBLOX_SOCKET_TIMEOUT = "sockettimeout";
  private static final String SEARCHBLOX_CONNECTION_TIMEOUT = "connectiontimeout";
  
  private static final String BUILDER_DEFAULT_SOCKET_TIMEOUT = "60";
  private static final String BUILDER_DEFAULT_CONNECTION_TIMEOUT = "60";

  private SearchBloxClient client = null;
  private String apiKey = null;

  public SearchBloxConnector() {

  }

  /** Connect.
  */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
  }
  
  /**
   * This method is called to assess whether to count this connector instance
   * should actually be counted as being connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected() {
    return client != null;
  }

  /**
   * Close the connection. Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (client != null) {
      apiKey = null;
      client = null;
    }
    super.disconnect();
  }

  /**
   * Return the list of activities that this connector supports (i.e. writes
   * into the log).
   *
   * @return the list.
   */
  @Override
  public String[] getActivitiesList() {
    return new String[] { INGEST_ACTIVITY, REMOVE_ACTIVITY,
        CREATION_ACTIVITY };
  }

  protected void getSession()
    throws ManifoldCFException {
    if (client == null) {
      String connectionTimeoutString = params.getParameter(SEARCHBLOX_CONNECTION_TIMEOUT);
      if (connectionTimeoutString == null)
        connectionTimeoutString = BUILDER_DEFAULT_CONNECTION_TIMEOUT;
      long connectionTimeout;
      try {
        connectionTimeout = Integer.parseInt(connectionTimeoutString);
      } catch (NumberFormatException e) {
        throw new ManifoldCFException("Bad connection timeout: "+e.getMessage(),e);
      }
      String socketTimeoutString = params.getParameter(SEARCHBLOX_SOCKET_TIMEOUT);
      if (socketTimeoutString == null)
        socketTimeoutString = BUILDER_DEFAULT_SOCKET_TIMEOUT;
      long socketTimeout;
      try {
        socketTimeout = Integer.parseInt(socketTimeoutString);
      } catch (NumberFormatException e) {
        throw new ManifoldCFException("Bad socket timeout: "+e.getMessage(),e);
      }
      final String endpoint = params.getParameter(SEARCHBLOX_ENDPOINT);
      this.apiKey = params.getParameter(SearchBloxDocument.API_KEY);
      ResteasyClientBuilder builder = new ResteasyClientBuilder();
      builder.connectionPoolSize(1);
      builder.establishConnectionTimeout(connectionTimeout, TimeUnit.SECONDS);
      builder.socketTimeout(socketTimeout, TimeUnit.SECONDS);
      client = new SearchBloxClient(apiKey, builder, endpoint);
    }
    
  }

  @Override
  public String check() throws ManifoldCFException {
    getSession();
    try {
      String format = getConfiguration().getParameter(SEARCHBLOX_INDEXING_FORMAT);
      if (client.ping(format)) {
        return super.check();
      } else {
        return "Connection Not Working!. Check SearchBlox Server is up and the configuration is correct.";
      }
    } catch (SearchBloxException e) {
      Logging.connectors.error("Connection Not Working", e);
      return "Connection Not Working!" + e.getMessage();
    }
  }

  /**
   * Get an output version string, given an output specification. The output
   * version string is used to uniquely describe the pertinent details of the
   * output specification and the configuration, to allow the Connector
   * Framework to determine whether a document will need to be output again.
   * Note that the contents of the document cannot be considered by this
   * method, and that a different version string (defined in
   * IRepositoryConnector) is used to describe the version of the actual
   * document.
   * <p/>
   * This method presumes that the connector object has been configured, and
   * it is thus able to communicate with the output data store should that be
   * necessary.
   *
   * @param spec
   *            is the current output specification for the job that is doing
   *            the crawling.
   * @return a string, of unlimited length, which uniquely describes output
   *         configuration and specification in such a way that if two such
   *         strings are equal, the document will not need to be sent again to
   *         the output data store.
   */
  @Override
  public VersionContext getPipelineDescription(Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(spec);
    return new VersionContext(sp.toPackedString(), params, spec);
  }

  /**
   * Detect if a mime type is indexable or not. This method is used by
   * participating repository connectors to pre-filter the number of unusable
   * documents that will be passed to this output connector.
   *
   * @param outputDescription
   *            is the document's output version.
   * @param mimeType
   *            is the mime type of the document.
   * @return true if the mime type is indexable by this connector.
   */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext outputDescription,
      String mimeType, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    // We work against the API, so we need to suppose the content reached by
    // the connector is a String convertible stream
    return true;
  }

  /**
   * Pre-determine whether a document's length is indexable by this connector.
   * This method is used by participating repository connectors to help filter
   * out documents that are too long to be indexable.
   *
   * @param outputDescription
   *            is the document's output version.
   * @param length
   *            is the length of the document.
   * @return true if the file is indexable.
   */
  @Override
  public boolean checkLengthIndexable(VersionContext outputDescription,
      long length, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    // No Size Limit for SearchBlox
    return true;
  }

  /**
   * Add (or replace) a document in the output data store using the connector.
   * This method presumes that the connector object has been configured, and
   * it is thus able to communicate with the output data store should that be
   * necessary.
   *
   * @param documentURI
   *            is the URI of the document. The URI is presumed to be the
   *            unique identifier which the output data store will use to
   *            process and serve the document. This URI is constructed by the
   *            repository connector which fetches the document, and is thus
   *            universal across all output connectors.
   * @param pipelineDescription
   *            includes the description string that was constructed for this
   *            document by the getOutputDescription() method.
   * @param document
   *            is the document data to be processed (handed to the output
   *            data store).
   * @param authorityNameString
   *            is the name of the authority responsible for authorizing any
   *            access tokens passed in with the repository document. May be
   *            null.
   * @param activities
   *            is the handle to an object that the implementer of a pipeline
   *            connector may use to perform operations, such as logging
   *            processing activity, or sending a modified document to the
   *            next stage in the pipeline.
   * @return the document status (accepted or permanently rejected).
   * @throws IOException
   *             only if there's a stream error reading the document data.
   */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI,
      VersionContext pipelineDescription, RepositoryDocument document,
      String authorityNameString, IOutputAddActivity activities)
      throws ManifoldCFException, ServiceInterruption, IOException {

    Logging.connectors.info("Indexing Document " + documentURI);
    long indexingTime = System.currentTimeMillis();
    SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());
    Map<String, List<String>> args = sp.getArgs();
    // Establish a session
    getSession();

    SearchBloxDocument sbDoc = new SearchBloxDocument(this.apiKey,
        documentURI, document, args);
    String format = this.getConfiguration().getParameter(SEARCHBLOX_INDEXING_FORMAT);
    long startTime = System.currentTimeMillis();
    try {
      ResponseCode code = client.addUpdateDocument(sbDoc, format);
      if (code == ResponseCode.DOCUMENT_INDEXED) {
                Logging.connectors.info("Document Indexed" + documentURI);
        activities.recordActivity(startTime, INGEST_ACTIVITY, null,
            documentURI, "OK", "Document Indexed");}
      else
        activities.recordActivity(startTime, INGEST_ACTIVITY, null,
            documentURI, "" + code.getCode(), code.name());
    } catch (SearchBloxException e) {
      Logging.connectors
          .error("[Indexing - Add] Exception indexing document :"
              + document, e);
      String activityCode = e.getClass().getSimpleName()
          .toUpperCase(Locale.ROOT);
      String activityDetails = e.getMessage()
          + ((e.getCause() != null) ? ": "
              + e.getCause().getMessage() : "");
      activities.recordActivity(startTime, INGEST_ACTIVITY, null,
          documentURI, activityCode, activityDetails);
      return DOCUMENTSTATUS_REJECTED;
    }
    indexingTime = System.currentTimeMillis() - indexingTime;
    Logging.connectors.info("Indexing Time for document " + documentURI + ": " + indexingTime);
    return DOCUMENTSTATUS_ACCEPTED;
  }

  /**
   * Remove a document using the connector. Note that the last
   * outputDescription is included, since it may be necessary for the
   * connector to use such information to know how to properly remove the
   * document.
   *
   * @param documentURI
   *            is the URI of the document. The URI is presumed to be the
   *            unique identifier which the output data store will use to
   *            process and serve the document. This URI is constructed by the
   *            repository connector which fetches the document, and is thus
   *            universal across all output connectors.
   * @param outputDescription
   *            is the last description string that was constructed for this
   *            document by the getOutputDescription() method above.
   * @param activities
   *            is the handle to an object that the implementer of an output
   *            connector may use to perform operations, such as logging
   *            processing activity.
   */
  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    Logging.ingest.debug("Deleting SearchBlox Document: '" + documentURI
        + "'");

    SpecPacker packer = new SpecPacker(outputDescription);
    Map<String, List<String>> args = packer.getArgs();
    // Establish a session
    getSession();
    
    SearchBloxDocument document = new SearchBloxDocument(this.apiKey);
    document.uid = documentURI;
    // document.apiKey = args.get(API_KEY).get(0);
    document.colName = args.get(SearchBloxDocument.SEARCHBLOX_COLLECTION).get(0);
    String format = this.getConfiguration().getParameter(SEARCHBLOX_INDEXING_FORMAT);
    long startTime = System.currentTimeMillis();
    try {
      ResponseCode code = client.deleteDocument(document, format);
      if (code == ResponseCode.DOCUMENT_DELETED)
        activities.recordActivity(startTime, REMOVE_ACTIVITY, null,
            documentURI, "OK", "Document Deleted");
      else
        activities.recordActivity(startTime, REMOVE_ACTIVITY, null,
            documentURI, "" + code.getCode(), code.name());
    } catch (SearchBloxException e) {
      Logging.connectors.error(
          "[Indexing - Remove] Exception indexing document :"
              + document, e);
      String activityCode = e.getClass().getSimpleName()
          .toUpperCase(Locale.ROOT);
      String activityDetails = e.getMessage()
          + ((e.getCause() != null) ? ": "
              + e.getCause().getMessage() : "");
      activities.recordActivity(startTime, REMOVE_ACTIVITY, null,
          documentURI, activityCode, activityDetails);
    }
  }

  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with
   * the value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out,
      Locale locale, Map<String, String> params, String tabName,
      Integer sequenceNumber, Integer currentSequenceNumber)
      throws ManifoldCFException {
    Map<String, String> paramMap = null;
    if (params != null) {
      paramMap = params;
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
      if (currentSequenceNumber != null)
        paramMap.put("SelectedNum", currentSequenceNumber.toString());
    } else {
      paramMap = new HashMap<String, String>();
    }
    if (sequenceNumber != null)
      paramMap.put("SeqNum", sequenceNumber.toString());

    Messages.outputResourceWithVelocity(out, locale, resName, paramMap,
        true);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    super.outputConfigurationHeader(threadContext, out, locale, parameters,
        tabsArray);
    tabsArray.add(Messages.getString(locale, SEARCHBLOX_TAB_PARAMETERS));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null,
        null, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      String tabName) throws ManifoldCFException, IOException {
    super.outputConfigurationBody(threadContext, out, locale, parameters,
        tabName);
    Map<String, String> config = getConfigParameters(parameters);
    outputResource(EDIT_CONFIG_FORWARD_PARAMETERS, out, locale, config,
        tabName, null, null);
  }

  /**
   * Build a Map of SearchBlox parameters. If configParams is null,
   * getConfiguration() is used.
   * 
   * @param configParams
   */
  final private Map<String, String> getConfigParameters(
      ConfigParams configParams) {
    Map<String, String> map = new HashMap<String, String>();

    String apiKey = configParams.getParameter(SearchBloxDocument.API_KEY);
    if(apiKey == null)
      apiKey = DEFAULT_APIKEY;
    map.put(SearchBloxDocument.API_KEY, apiKey);
    
    String endpoint = configParams.getParameter(SEARCHBLOX_ENDPOINT);
    if(endpoint == null) {
      endpoint = SearchBloxClient.DEFAULT_ENDPOINT;
    }
    map.put(SEARCHBLOX_ENDPOINT,
        endpoint);
    
    String indexFormat = configParams.getParameter(SEARCHBLOX_INDEXING_FORMAT);
    if (indexFormat == null) {
      indexFormat = IndexingFormat.JSON.name();
    }
    map.put(SEARCHBLOX_INDEXING_FORMAT, indexFormat);
    
    String connectionTimeout = configParams.getParameter(SEARCHBLOX_CONNECTION_TIMEOUT);
    if (connectionTimeout == null) {
      connectionTimeout = BUILDER_DEFAULT_CONNECTION_TIMEOUT;
    }
    map.put(SEARCHBLOX_CONNECTION_TIMEOUT, connectionTimeout);
    
    String socketTimeout = configParams.getParameter(SEARCHBLOX_SOCKET_TIMEOUT);
    if (socketTimeout == null) {
      socketTimeout = BUILDER_DEFAULT_SOCKET_TIMEOUT;
    }
    map.put(SEARCHBLOX_SOCKET_TIMEOUT, socketTimeout);
    
    return map;
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    outputResource(VIEW_CONFIG_FORWARD, out, locale,
        getConfigParameters(parameters), null, null, null);
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   *
   * @param threadContext
   *            is the local thread context.
   * @param variableContext
   *            is the set of variables available from the post, including
   *            binary file post information.
   * @param parameters
   *            are the configuration parameters, as they currently exist, for
   *            this connection being configured.
   * @return null if all is well, or a string error message if there is an
   *         error that should prevent saving of the connection (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale,
      ConfigParams parameters) throws ManifoldCFException {
    String apiKey = variableContext.getParameter(SearchBloxDocument.API_KEY);
    if (apiKey != null)
      parameters.setParameter(SearchBloxDocument.API_KEY, apiKey);

    String endpoint = variableContext.getParameter(SEARCHBLOX_ENDPOINT);
    if (endpoint != null)
      parameters.setParameter(SEARCHBLOX_ENDPOINT, endpoint);
    
    String indexformat = variableContext.getParameter(SEARCHBLOX_INDEXING_FORMAT);
    if (indexformat != null)
      parameters.setParameter(SEARCHBLOX_INDEXING_FORMAT, indexformat);

    String connectionTimeout = variableContext.getParameter(SEARCHBLOX_CONNECTION_TIMEOUT);
    if (connectionTimeout != null)
      parameters.setParameter(SEARCHBLOX_CONNECTION_TIMEOUT, connectionTimeout);
    
    String socketTimeout = variableContext.getParameter(SEARCHBLOX_SOCKET_TIMEOUT);
    if (socketTimeout != null)
      parameters.setParameter(SEARCHBLOX_SOCKET_TIMEOUT, socketTimeout);
    
    return null;
  }

  /**
   * Output the specification header section. This method is called in the
   * head section of a job page which has selected a pipeline connection of
   * the current type. Its purpose is to add the required tabs to the list,
   * and to output any javascript methods that might be needed by the job
   * editing HTML.
   *
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current pipeline specification for this connection.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param tabsArray
   *            is an array of tab names. Add to this array any tab names that
   *            are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale,
        "SearchBloxConnector.Configuration"));

    // Fill in the specification header map, using data from all tabs.
    fillInSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS,
        paramMap);
  }

  private void fillInSpecificationMap(
      Map<String, Object> paramMap, Specification os) {

    for (int i = 0, len = os.getChildCount(); i < len; i++) {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(SearchBloxConfig.NODE_CONFIGURATION)) {

        String titleBoost = sn
            .getAttributeValue(SearchBloxConfig.ATTRIBUTE_TITLEBOOST);
        if (titleBoost == null || titleBoost.isEmpty())
          titleBoost = "0";
        String contentBoost = sn
            .getAttributeValue(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST);
        if (contentBoost == null || contentBoost.isEmpty())
          contentBoost = "0";
        String keywordsBoost = sn
            .getAttributeValue(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST);
        if (keywordsBoost == null || keywordsBoost.isEmpty())
          keywordsBoost = "0";
        String descriptionBoost = sn
            .getAttributeValue(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST);
        if (descriptionBoost == null || descriptionBoost.isEmpty())
          descriptionBoost = "0";

        String collection = sn
            .getAttributeValue(SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME);
        if (collection == null)
          collection = DEFAULT_COLLECTION;
        
        paramMap.put(SearchBloxConfig.ATTRIBUTE_TITLEBOOST.toUpperCase(Locale.ROOT),
            titleBoost);
        paramMap.put(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST.toUpperCase(Locale.ROOT),
            contentBoost);
        paramMap.put(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST.toUpperCase(Locale.ROOT),
            keywordsBoost);
        paramMap.put(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST.toUpperCase(Locale.ROOT),
            descriptionBoost);
        paramMap.put(SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME
            .toUpperCase(Locale.ROOT), collection);

        return;
      }

    }

    paramMap.put(SearchBloxConfig.ATTRIBUTE_TITLEBOOST.toUpperCase(Locale.ROOT), 0);
    paramMap.put(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST.toUpperCase(Locale.ROOT), 0);
    paramMap.put(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST.toUpperCase(Locale.ROOT), 0);
    paramMap.put(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST.toUpperCase(Locale.ROOT), 0);
    paramMap.put(SearchBloxConfig.ATTRIBUTE_INDEX_FORMAT.toUpperCase(Locale.ROOT),
        IndexingFormat.XML.name());
    paramMap.put(SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME.toUpperCase(Locale.ROOT),
        "");

  }

  /**
   * View specification. This method is called in the body section of a job's
   * view page. Its purpose is to present the pipeline specification
   * information to the user. The coder can presume that the HTML that is
   * output from this configuration will be within appropriate <html> and
   * <body> tags.
   *
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param os
   *            is the current pipeline specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale,
        VIEW_SPECIFICATION_HTML, paramMap);

  }

  /**
   * Output the specification body section. This method is called in the body
   * section of a job page which has selected a pipeline connection of the
   * current type. Its purpose is to present the required form elements for
   * editing. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html>, <body>, and <form> tags.
   * The name of the form is "editjob".
   *
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current pipeline specification for this job.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param actualSequenceNumber
   *            is the connection within the job that has currently been
   *            selected.
   * @param tabName
   *            is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber,
      int actualSequenceNumber, String tabName)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale,
        EDIT_SPECIFICATION_CONFIGURATION_HTML, paramMap);

  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the transformation specification accordingly. The name of the
   * posted form is "editjob".
   *
   * @param variableContext
   *            contains the post data, including binary file-upload
   *            information.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current pipeline specification for this job.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @return null if all is well, or a string error message if there is an
   *         error that should prevent saving of the job (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, Specification os, int connectionSequenceNumber)
      throws ManifoldCFException {
    String seqPrefix = "s" + connectionSequenceNumber + "_";
    String titleBoost = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_TITLEBOOST);
    String contentBoost = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_CONTENTBOOST);
    String keywordsBoost = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST);
    String descriptionBoost = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST);
    String collection = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME);
    String indexFormat = variableContext.getParameter(seqPrefix
        + SearchBloxConfig.ATTRIBUTE_INDEX_FORMAT);

    // About to gather the configuration values, so get rid of the old one.
    int i = 0, len = os.getChildCount();
    while (i < len) {
      SpecificationNode node = os.getChild(i);
      if (node.getType().equals(SearchBloxConfig.NODE_CONFIGURATION))
        os.removeChild(i);
      else
        i++;
    }

    SpecificationNode node = new SpecificationNode(
        SearchBloxConfig.NODE_CONFIGURATION);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_TITLEBOOST, titleBoost);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST, contentBoost);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST, keywordsBoost);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST, descriptionBoost);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME,
        collection);
    node.setAttribute(SearchBloxConfig.ATTRIBUTE_INDEX_FORMAT, indexFormat);
    os.addChild(os.getChildCount(), node);

    return null;

  }
  

  protected static class SpecPacker {
    /** Arguments, from configuration */
    private final Multimap<String, String> args = HashMultimap.create();
      
    public SpecPacker(String outputDescription) {
      String[] parts = outputDescription.split(",");
      for(String part : parts) {
        String[] keyValue = part.split("=");
        if(keyValue.length != 2) {
          continue;
        }
          
        args.put(keyValue[0], keyValue[1]);
      }
    }
      
    public SpecPacker(Specification spec) {
      // Process arguments
      for (int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode node = spec.getChild(i);
        if (node.getType().equals(SearchBloxConfig.NODE_CONFIGURATION))
        {
          String titleBoost = node.getAttributeValue(SearchBloxConfig.ATTRIBUTE_TITLEBOOST);
          String contentBoost = node.getAttributeValue(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST);
          String keywordsBoost = node.getAttributeValue(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST);
          String descriptionBoost = node.getAttributeValue(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST);
          String collection = node.getAttributeValue(SearchBloxConfig.ATTRIBUTE_COLLECTION_NAME);
          args.put(SearchBloxConfig.ATTRIBUTE_TITLEBOOST, titleBoost);
          args.put(SearchBloxConfig.ATTRIBUTE_CONTENTBOOST, contentBoost);
          args.put(SearchBloxConfig.ATTRIBUTE_KEYWORDSBOOST, keywordsBoost);
          args.put(SearchBloxConfig.ATTRIBUTE_DESCRIPTIONBOOST, descriptionBoost);
          args.put(SearchBloxDocument.SEARCHBLOX_COLLECTION, collection);
            
        }
      }
      
    }
      
    public String toPackedString() {
      Map<String, List<String>> mapList = getArgs();
      StringBuilder sb = new StringBuilder();
      for(String s : mapList.keySet()) {
        sb.append(s).append("=").append(mapList.get(s).get(0));
        sb.append(",");
      }
      if(sb.toString().length()!=0)
        return sb.substring(0, sb.length()-1);
      else
        return "";
        
    }
      
    public Map<String,List<String>> getArgs() {
      Map<String,List<String>> result = Maps.newHashMap();
      for(String s : args.keySet()) {
        Collection<String> list = args.get(s);
        if(list instanceof List) {
          result.put(s,  (List<String>) list);
        }
        else {
          List<String> l = Lists.newArrayList(list);
          result.put(s,  l);
        }
      }
      return result;
    }
  }
}
