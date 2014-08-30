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

package org.apache.manifoldcf.agents.output.opensearchserver;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.opensearchserver.OpenSearchServerConnection.Result;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenSearchServerConnector extends BaseOutputConnector
{

  private final static String OPENSEARCHSERVER_INDEXATION_ACTIVITY = "Indexation";
  private final static String OPENSEARCHSERVER_DELETION_ACTIVITY = "Deletion";
  private final static String OPENSEARCHSERVER_SCHEDULER_ACTIVITY = "Scheduler";

  private final static String[] OPENSEARCHSERVER_ACTIVITIES = {
      OPENSEARCHSERVER_INDEXATION_ACTIVITY, OPENSEARCHSERVER_DELETION_ACTIVITY,
      OPENSEARCHSERVER_SCHEDULER_ACTIVITY };

  // Tab resources

  private final static String OPENSEARCHSERVER_TAB_MESSAGE = "OpenSearchServerConnector.OpenSearchServer";
  private final static String PARAMETERS_TAB_MESSAGE = "OpenSearchServerConnector.Parameters";

  // Velocity templates
  // These are not broken down by tabs because the design of this connector makes it difficult to do it that way.

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD = "editConfiguration.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";

  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD = "editSpecification.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";

  /** Connection expiration interval */
  private static final long EXPIRATION_INTERVAL = 60000L;

  private HttpClientConnectionManager connectionManager = null;
  private HttpClient client = null;
  private long expirationTime = -1L;

  // Private data

  private String specsCacheOutpuDescription;
  private OpenSearchServerSpecs specsCache;

  public OpenSearchServerConnector()
  {
    specsCacheOutpuDescription = null;
    specsCache = null;
  }

  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);
  }

  protected HttpClient getSession()
      throws ManifoldCFException
  {
    if (client == null)
    {
      connectionManager = new PoolingHttpClientConnectionManager();

      final int executorTimeout = 300000;
      final int socketTimeout = 60000;
      final int connectionTimeout = 60000;

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true).setSocketTimeout(socketTimeout)
          .setStaleConnectionCheckEnabled(true).setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);

      HttpClientBuilder clientBuilder = HttpClients
          .custom()
          .setConnectionManager(connectionManager)
          .setMaxConnTotal(1)
          .disableAutomaticRetries()
          .setDefaultRequestConfig(requestBuilder.build())
          .setRequestExecutor(new HttpRequestExecutor(executorTimeout))
          .setDefaultSocketConfig(
              SocketConfig.custom().setTcpNoDelay(true)
                  .setSoTimeout(socketTimeout).build());

      client = clientBuilder.build();

    }
    expirationTime = System.currentTimeMillis() + EXPIRATION_INTERVAL;
    return client;
  }

  protected void closeSession()
  {
    if (connectionManager != null)
    {
      connectionManager.shutdown();
      connectionManager = null;
    }
    client = null;
    expirationTime = -1L;
  }

  @Override
  public void disconnect()
      throws ManifoldCFException
  {
    super.disconnect();
    closeSession();
  }

  @Override
  public void poll()
      throws ManifoldCFException
  {
    super.poll();
    if (connectionManager != null)
    {
      if (System.currentTimeMillis() > expirationTime)
      {
        closeSession();
      }
    }
  }

  /**
   * This method is called to assess whether to count this connector instance should
   * actually be counted as being connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected()
  {
    return connectionManager != null;
  }

  @Override
  public String[] getActivitiesList()
  {
    return OPENSEARCHSERVER_ACTIVITIES;
  }

  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out,
      Locale locale, OpenSearchServerParam params, String tabName,
      Integer sequenceNumber, Integer actualSequenceNumber)
      throws ManifoldCFException
  {
    Map<String, String> paramMap = null;
    if (params != null)
    {
      paramMap = params.buildMap();
      if (tabName != null)
      {
        paramMap.put("TabName", tabName);
      }
      if (actualSequenceNumber != null)
        paramMap.put("SelectedNum", actualSequenceNumber.toString());
    }
    else
    {
      paramMap = new HashMap<String, String>();
    }
    if (sequenceNumber != null)
      paramMap.put("SeqNum", sequenceNumber.toString());

    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, false);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray)
      throws ManifoldCFException, IOException
  {
    super.outputConfigurationHeader(threadContext, out, locale, parameters,
        tabsArray);
    tabsArray.add(Messages.getString(locale, PARAMETERS_TAB_MESSAGE));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null, null,
        null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException
  {
    super.outputConfigurationBody(threadContext, out, locale, parameters,
        tabName);
    OpenSearchServerConfig config = this.getConfigParameters(parameters);
    outputResource(EDIT_CONFIG_FORWARD, out, locale, config, tabName, null,
        null);
  }

  /**
  * Output the specification header section. This method is called in the head
  * section of a job page which has selected a pipeline connection of the
  * current type. Its purpose is to add the required tabs to the list, and to
  * output any javascript methods that might be needed by the job editing HTML.
  *
  * @param out is the output to which any HTML should be sent.
  * @param locale is the preferred local of the output.
  * @param os is the current pipeline specification for this connection.
  * @param connectionSequenceNumber is the unique number of this connection within the job.
  * @param tabsArray is an array of tab names. Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber, List<String> tabsArray)
      throws ManifoldCFException, IOException
  {
    super.outputSpecificationHeader(out, locale, os, connectionSequenceNumber,
        tabsArray);
    tabsArray.add(Messages.getString(locale, OPENSEARCHSERVER_TAB_MESSAGE));
    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, null, null,
        new Integer(connectionSequenceNumber), null);
  }

  final private SpecificationNode getSpecNode(Specification os)
  {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++)
    {
      SpecificationNode node = os.getChild(i);
      if (OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE.equals(node
          .getType()))
      {
        return node;
      }
    }
    return null;
  }

  /**
  * Output the specification body section. This method is called in the body
  * section of a job page which has selected a pipeline connection of the
  * current type. Its purpose is to present the required form elements for
  * editing. The coder can presume that the HTML that is output from this
  * configuration will be within appropriate <html>, <body>, and <form> tags.
  * The name of the form is "editjob".
  *
  * @param out is the output to which any HTML should be sent.
  * @param locale is the preferred local of the output.
  * @param os is the current pipeline specification for this job.
  * @param connectionSequenceNumber is the unique number of this connection within the job.
  * @param actualSequenceNumber is the connection within the job that has currently been selected.
  * @param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber, int actualSequenceNumber,
      String tabName)
      throws ManifoldCFException, IOException
  {
    OpenSearchServerSpecs specs = getSpecParameters(os);
    outputResource(EDIT_SPEC_FORWARD, out, locale, specs, tabName, new Integer(
        connectionSequenceNumber), new Integer(actualSequenceNumber));
  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the transformation specification accordingly. The name of the posted
   * form is "editjob".
   *
   * @param variableContext contains the post data, including binary file-upload information.
   * @param locale is the preferred local of the output.
   * @param os is the current pipeline specification for this job.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, Specification os, int connectionSequenceNumber)
      throws ManifoldCFException
  {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd)
    {
      specNode = new SpecificationNode(
          OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE);
    }
    OpenSearchServerSpecs.contextToSpecNode(variableContext, specNode,
        connectionSequenceNumber);
    if (bAdd)
      os.addChild(os.getChildCount(), specNode);
    return null;
  }

  /**
   * Build a Set of OpenSearchServer parameters. If configParams is null,
   * getConfiguration() is used.
   * 
   * @param configParams
   */
  final private OpenSearchServerConfig getConfigParameters(
      ConfigParams configParams)
  {
    if (configParams == null)
      configParams = getConfiguration();
    return new OpenSearchServerConfig(configParams);
  }

  final private OpenSearchServerSpecs getSpecParameters(Specification os)
      throws ManifoldCFException
  {
    return new OpenSearchServerSpecs(getSpecNode(os));
  }

  final private OpenSearchServerSpecs getSpecsCache(String outputDescription)
      throws ManifoldCFException
  {
    try
    {
      synchronized (this)
      {
        if (!outputDescription.equals(specsCacheOutpuDescription))
          specsCache = null;
        if (specsCache == null)
          specsCache = new OpenSearchServerSpecs(new JSONObject(
              outputDescription));
        return specsCache;
      }
    }
    catch (JSONException e)
    {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public VersionContext getPipelineDescription(Specification os)
      throws ManifoldCFException
  {
    OpenSearchServerSpecs specs = new OpenSearchServerSpecs(getSpecNode(os));
    return new VersionContext(specs.toJson().toString(), params, os);
  }

  @Override
  public boolean checkLengthIndexable(VersionContext outputDescription, long length, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription.getVersionString());
    long maxFileSize = specs.getMaxFileSize();
    if (length > maxFileSize)
      return false;
    return true;
  }

  @Override
  public boolean checkMimeTypeIndexable(VersionContext outputDescription, String mimeType, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription.getVersionString());
    return specs.checkMimeType(mimeType);
  }

  /**
   * Pre-determine whether a document's URL is indexable by this connector. This
   * method is used by participating repository connectors to help filter out
   * documents that are not worth indexing.
   *
   * @param outputDescription
   *          is the document's output version.
   * @param url
   *          is the URL of the document.
   * @return true if the file is indexable.
   */
  @Override
  public boolean checkURLIndexable(VersionContext outputDescription, String url, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription.getVersionString());
    return specs.checkExtension(FilenameUtils.getExtension(url));
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException
  {
    outputResource(VIEW_CONFIG_FORWARD, out, locale,
        getConfigParameters(parameters), null, null, null);
  }

  /**
   * View specification. This method is called in the body section of a job's
   * view page. Its purpose is to present the pipeline specification information
   * to the user. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html> and <body> tags.
   *
   * @param out is the output to which any HTML should be sent.
   * @param locale is the preferred local of the output.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param os is the current pipeline specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale,
      Specification os, int connectionSequenceNumber)
      throws ManifoldCFException, IOException
  {
    outputResource(VIEW_SPEC_FORWARD, out, locale, getSpecParameters(os), null,
        new Integer(connectionSequenceNumber), null);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException
  {
    OpenSearchServerConfig.contextToConfig(variableContext, parameters);
    return null;
  }

  private static Map<String, Integer> ossInstances = null;

  private synchronized final Integer addInstance(OpenSearchServerConfig config)
  {
    if (ossInstances == null)
      ossInstances = new TreeMap<String, Integer>();
    synchronized (ossInstances)
    {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null)
      {
        count = new Integer(1);
        ossInstances.put(uii, count);
      }
      else
        count++;
      return count;
    }
  }

  private synchronized final void removeInstance(OpenSearchServerConfig config)
  {
    if (ossInstances == null)
      return;
    synchronized (ossInstances)
    {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null)
        return;
      if (--count == 0)
        ossInstances.remove(uii);
    }
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param pipelineDescription includes the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity,
  * or sending a modified document to the next stage in the pipeline.
  *@return the document status (accepted or permanently rejected).
  *@throws IOException only if there's a stream error reading the document data.
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
      RepositoryDocument document, String authorityNameString,
      IOutputAddActivity activities)
      throws ManifoldCFException, ServiceInterruption, IOException
  {
    HttpClient client = getSession();
    OpenSearchServerConfig config = getConfigParameters(null);

    Integer count = addInstance(config);
    synchronized (count)
    {
      try
      {
        long startTime = System.currentTimeMillis();
        OpenSearchServerIndex oi = new OpenSearchServerIndex(client,
            documentURI, config, document, authorityNameString, activities);
        activities.recordActivity(startTime,
            OPENSEARCHSERVER_INDEXATION_ACTIVITY, document.getBinaryLength(),
            documentURI, oi.getResult().name(), oi.getResultDescription());
        if (oi.getResult() != Result.OK)
          return DOCUMENTSTATUS_REJECTED;
      }
      finally
      {
        removeInstance(config);
      }
      return DOCUMENTSTATUS_ACCEPTED;
    }
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    OpenSearchServerDelete od = new OpenSearchServerDelete(client, documentURI,
        getConfigParameters(null));
    activities.recordActivity(startTime, OPENSEARCHSERVER_DELETION_ACTIVITY,
        null, documentURI, od.getResult().name(), od.getResultDescription());
  }

  @Override
  public String check()
      throws ManifoldCFException
  {
    HttpClient client = getSession();
    OpenSearchServerSchema oss = new OpenSearchServerSchema(client,
        getConfigParameters(null));
    return oss.getResult().name() + " " + oss.getResultDescription();
  }

  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    OpenSearchServerConfig config = getConfigParameters(null);
    String schedulerJob = config.getSchedulerJob();
    if (schedulerJob != null && schedulerJob.trim().length() > 0)
    {
      OpenSearchServerScheduler oo = new OpenSearchServerScheduler(client,
          getConfigParameters(null), schedulerJob.trim());
      activities.recordActivity(startTime, OPENSEARCHSERVER_SCHEDULER_ACTIVITY,
          null, oo.getCallUrlSnippet(), oo.getResult().name(),
          oo.getResultDescription());
    }
  }

}
