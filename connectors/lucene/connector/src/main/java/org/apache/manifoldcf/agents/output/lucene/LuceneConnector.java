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
package org.apache.manifoldcf.agents.output.lucene;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.manifoldcf.crawler.system.Logging;


/** This is the output connector for Lucene.
*/
public class LuceneConnector extends org.apache.manifoldcf.agents.output.BaseOutputConnector
{
  private final static String LUCENE_TAB_PARAMETERS = "LuceneConnector.Parameters";

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD_PARAMETERS = "editConfiguration_Parameters.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Connection */
  private LuceneClient client = null;

  /** Expiration */
  protected long expirationTime = -1L;

  /** Idle connection expiration interval */
  protected final static long EXPIRATION_INTERVAL = 300000L;

  public LuceneConnector()
  {
  }

  /**
   * Return the list of activities that this connector supports (i.e. writes
   * into the log).
   *
   * @return the list.
   */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Connect.
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /**
   * This method is called to assess whether to count this connector instance
   * should actually be counted as being connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected()
  {
    return client != null;
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect() throws ManifoldCFException
  {
    if (client != null)
    {
      try
      {
        client.close();
      } catch (IOException e) {
        Logging.connectors.error("Failed to disconnect:", e);
      }
      client = null;
      expirationTime = -1L;
    }
    super.disconnect();
  }

  protected void getSession() throws ManifoldCFException
  {
    if (client == null || !client.isOpen())
    {
      final String path = params.getParameter(LuceneConfig.PARAM_PATH);
      if (path == null)
        throw new ManifoldCFException("path not configured");

      final String charfilters = params.getParameter(LuceneConfig.PARAM_CHARFILTERS);
      if (charfilters == null)
        throw new ManifoldCFException("charfilters not configured");

      final String tokenizers = params.getParameter(LuceneConfig.PARAM_TOKENIZERS);
      if (tokenizers == null)
        throw new ManifoldCFException("tokenizers not configured");

      final String filters = params.getParameter(LuceneConfig.PARAM_FILTERS);
      if (filters == null)
        throw new ManifoldCFException("filters not configured");

      final String analyzers = params.getParameter(LuceneConfig.PARAM_ANALYZERS);
      if (analyzers == null)
        throw new ManifoldCFException("analyzers not configured");

      final String fields = params.getParameter(LuceneConfig.PARAM_FIELDS);
      if (fields == null)
        throw new ManifoldCFException("fields not configured");

      final String idField = params.getParameter(LuceneConfig.PARAM_IDFIELD);
      if (idField == null)
        throw new ManifoldCFException("id field not configured");

      final String contentField = params.getParameter(LuceneConfig.PARAM_CONTENTFIELD);
      if (contentField == null)
        throw new ManifoldCFException("content field not configured");

      final String maxDocLength = params.getParameter(LuceneConfig.PARAM_MAXDOCUMENTLENGTH);
      if (maxDocLength == null)
        throw new ManifoldCFException("max document length not configured");
      Long maxDocumentLength = new Long(maxDocLength);

      try
      {
        client = LuceneClientManager.getClient(path,
                   charfilters, tokenizers, filters, analyzers, fields,
                   idField, contentField, maxDocumentLength);
      }
      catch (Exception e)
      {
        throw new ManifoldCFException(e);
      }
    }
    expirationTime = System.currentTimeMillis() + EXPIRATION_INTERVAL;
  }

  @Override
  public String check() throws ManifoldCFException
  {
    try
    {
      getSession();
    } catch (ManifoldCFException e) {
      Logging.connectors.error("Connection Not Working", e);
      return "Connection Not Working! " + e.getMessage();
    }
    return super.check();
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll() throws ManifoldCFException
  {
    if (client != null)
    {
      if (expirationTime <= System.currentTimeMillis())
      {
        try
        {
          client.close();
        } catch (IOException e) {
          Logging.connectors.error("Failed to poll:", e);
        }
        client = null;
        expirationTime = -1L;
      }
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
    getSession();
    return new VersionContext(client.versionString(), params, spec);
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
    getSession();
    if (length > client.maxDocumentLength())
      return false;
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
      throws ManifoldCFException, ServiceInterruption, IOException
  {
    getSession();

    if (document.getBinaryLength() > client.maxDocumentLength().longValue()){
      activities.recordActivity(null, INGEST_ACTIVITY, null, documentURI, activities.EXCLUDED_LENGTH, "Lucene connector rejected document due to its big size: ('"+document.getBinaryLength()+"')");
      return DOCUMENTSTATUS_REJECTED;
    }

    long startTime = System.currentTimeMillis();
    try
    {
      LuceneDocument inputDoc = buildDocument(documentURI, document);
      client.addOrReplace(documentURI, inputDoc);
      activities.recordActivity(startTime, INGEST_ACTIVITY, null, documentURI, "OK", "Document Indexed");
    } catch (Exception e) {
      Logging.connectors.error("Failed to addOrReplaceDocumentWithException:" + documentURI, e);
      String activityCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      String activityDetails = e.getMessage() + ((e.getCause() != null) ? ": "+ e.getCause().getMessage() : "");
      activities.recordActivity(startTime, INGEST_ACTIVITY, null, documentURI, activityCode, activityDetails);
      return DOCUMENTSTATUS_REJECTED;
    }
    return DOCUMENTSTATUS_ACCEPTED;
  }

  private LuceneDocument buildDocument(String documentURI, RepositoryDocument document) throws Exception {
    LuceneDocument doc = new LuceneDocument();

    doc = LuceneDocument.addField(doc, client.idField(), documentURI, client.fieldsInfo());

    try
    {
      doc = LuceneDocument.addField(doc, client.contentField(), document.getBinaryStream(), client.fieldsInfo());
    } catch (Exception e) {
      if (e instanceof IOException) {
        Logging.connectors.error("[Parsing Content]Content is not text plain, verify you are properly using Apache Tika Transformer " + documentURI, e);
      } else {
        throw e;
      }
    }

    Iterator<String> it = document.getFields();
    while (it.hasNext()) {
      String rdField = it.next();
      if (client.fieldsInfo().containsKey(rdField)) {
        try
        {
          String[] values = document.getFieldAsStrings(rdField);
          for (String value : values) {
            doc = LuceneDocument.addField(doc, rdField, value, client.fieldsInfo());
          }
        } catch (IOException e) {
          Logging.connectors.error("[Getting Field Values]Impossible to read value for metadata " + rdField + " " + documentURI, e);
        }
      }
    }
    return doc;
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
      IOutputRemoveActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    getSession();

    long startTime = System.currentTimeMillis();
    try
    {
      client.remove(documentURI);
      activities.recordActivity(startTime, REMOVE_ACTIVITY, null, documentURI, "OK", "Document Deleted");
    } catch (IOException e) {
      String activityCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      String activityDetails = e.getMessage() + ((e.getCause() != null) ? ": "+ e.getCause().getMessage() : "");
      activities.recordActivity(startTime, REMOVE_ACTIVITY, null, documentURI, activityCode, activityDetails);
    }
  }

  /** Notify the connector of a completed job.
  * This is meant to allow the connector to flush any internal data structures it has been keeping around, or to tell the output repository that this
  * is a good time to synchronize things.  It is called whenever a job is either completed or aborted.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    try
    {
      client.optimize();
    } catch (IOException e) {
      Logging.connectors.error("Failed to noteJobComplete:", e);
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

    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    super.outputConfigurationHeader(threadContext, out, locale, parameters,
        tabsArray);
    tabsArray.add(Messages.getString(locale, LUCENE_TAB_PARAMETERS));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null, null, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      String tabName) throws ManifoldCFException, IOException {
    super.outputConfigurationBody(threadContext, out, locale, parameters, tabName);
    Map<String, String> config = getConfigParameters(parameters);
    outputResource(EDIT_CONFIG_FORWARD_PARAMETERS, out, locale, config, tabName, null, null);
  }

  private final Map<String, String> getConfigParameters(ConfigParams configParams) {
    Map<String, String> map = new HashMap<String, String>();

    String path = configParams.getParameter(LuceneConfig.PARAM_PATH);
    if (path == null)
      path = LuceneClient.defaultPath();
    map.put(LuceneConfig.PARAM_PATH, path);

    String charfilters = configParams.getParameter(LuceneConfig.PARAM_CHARFILTERS);
    if (charfilters == null)
      charfilters = LuceneClient.defaultCharfilters();
    map.put(LuceneConfig.PARAM_CHARFILTERS, charfilters);

    String tokenizers = configParams.getParameter(LuceneConfig.PARAM_TOKENIZERS);
    if (tokenizers == null)
      tokenizers = LuceneClient.defaultTokenizers();
    map.put(LuceneConfig.PARAM_TOKENIZERS, tokenizers);

    String filters = configParams.getParameter(LuceneConfig.PARAM_FILTERS);
    if (filters == null)
      filters = LuceneClient.defaultFilters();
    map.put(LuceneConfig.PARAM_FILTERS, filters);

    String analyzers = configParams.getParameter(LuceneConfig.PARAM_ANALYZERS);
    if (analyzers == null)
      analyzers = LuceneClient.defaultAnalyzers();
    map.put(LuceneConfig.PARAM_ANALYZERS, analyzers);

    String fields = configParams.getParameter(LuceneConfig.PARAM_FIELDS);
    if (fields == null)
      fields = LuceneClient.defaultFields();
    map.put(LuceneConfig.PARAM_FIELDS, fields);

    String idField = configParams.getParameter(LuceneConfig.PARAM_IDFIELD);
    if (idField == null)
      idField = LuceneClient.defaultIdField();
    map.put(LuceneConfig.PARAM_IDFIELD, idField);

    String contentField = configParams.getParameter(LuceneConfig.PARAM_CONTENTFIELD);
    if (contentField == null)
      contentField = LuceneClient.defaultContentField();
    map.put(LuceneConfig.PARAM_CONTENTFIELD, contentField);

    String maxDocumentLength = configParams.getParameter(LuceneConfig.PARAM_MAXDOCUMENTLENGTH);
    if (maxDocumentLength == null)
      maxDocumentLength = LuceneClient.defaultMaxDocumentLength().toString();
    map.put(LuceneConfig.PARAM_MAXDOCUMENTLENGTH, maxDocumentLength);

    return map;
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    outputResource(VIEW_CONFIG_FORWARD, out, locale, getConfigParameters(parameters), null, null, null);
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
    String path = variableContext.getParameter(LuceneConfig.PARAM_PATH);
    if (path != null)
      parameters.setParameter(LuceneConfig.PARAM_PATH, path);
    String charfilters = variableContext.getParameter(LuceneConfig.PARAM_CHARFILTERS);
    if (charfilters != null)
      parameters.setParameter(LuceneConfig.PARAM_CHARFILTERS, charfilters);
    String tokenizers = variableContext.getParameter(LuceneConfig.PARAM_TOKENIZERS);
    if (tokenizers != null)
      parameters.setParameter(LuceneConfig.PARAM_TOKENIZERS, tokenizers);
    String filters = variableContext.getParameter(LuceneConfig.PARAM_FILTERS);
    if (filters != null)
      parameters.setParameter(LuceneConfig.PARAM_FILTERS, filters);
    String analyzers = variableContext.getParameter(LuceneConfig.PARAM_ANALYZERS);
    if (analyzers != null)
      parameters.setParameter(LuceneConfig.PARAM_ANALYZERS, analyzers);
    String fields = variableContext.getParameter(LuceneConfig.PARAM_FIELDS);
    if (fields != null)
      parameters.setParameter(LuceneConfig.PARAM_FIELDS, fields);
    String idFields = variableContext.getParameter(LuceneConfig.PARAM_IDFIELD);
    if (idFields != null)
      parameters.setParameter(LuceneConfig.PARAM_IDFIELD, idFields);
    String contentFields = variableContext.getParameter(LuceneConfig.PARAM_CONTENTFIELD);
    if (contentFields != null)
      parameters.setParameter(LuceneConfig.PARAM_CONTENTFIELD, contentFields);
    String maxDocumentLength = variableContext.getParameter(LuceneConfig.PARAM_MAXDOCUMENTLENGTH);
    if (maxDocumentLength != null)
      parameters.setParameter(LuceneConfig.PARAM_MAXDOCUMENTLENGTH, maxDocumentLength);
    return null;
  }

}
