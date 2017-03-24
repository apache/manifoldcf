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

  /** Connection expiration interval */
  private static final long EXPIRATION_INTERVAL = 60000L;

  private HttpClientConnectionManager connectionManager = null;
  private HttpClient client = null;
  private long expirationTime = -1L;

  public OpenSearchServerConnector()
  {
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
      final int executorTimeout = 300000;
      final int socketTimeout = 60000;
      final int connectionTimeout = 60000;

      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(2000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
        .setTcpNoDelay(true)
        .setSoTimeout(socketTimeout)
        .build());
      connectionManager = poolingConnectionManager;

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true).setSocketTimeout(socketTimeout)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);

      HttpClientBuilder clientBuilder = HttpClients
          .custom()
          .setConnectionManager(connectionManager)
          .disableAutomaticRetries()
          .setDefaultRequestConfig(requestBuilder.build())
          .setRequestExecutor(new HttpRequestExecutor(executorTimeout));

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

  @Override
  public VersionContext getPipelineDescription(Specification os)
      throws ManifoldCFException
  {
    return new VersionContext("", params, os);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException
  {
    outputResource(VIEW_CONFIG_FORWARD, out, locale,
        getConfigParameters(parameters), null, null, null);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException
  {
    OpenSearchServerConfig.contextToConfig(variableContext, parameters);
    return null;
  }

  // Apparently, only one connection to any given Open Search Server instance is allowed at a time.
  
  private static Map<String, Integer> ossInstances = new TreeMap<String, Integer>();

  private final Integer addInstance(OpenSearchServerConfig config)
  {
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

  private final void removeInstance(OpenSearchServerConfig config)
  {
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
            documentURI, oi.getResultCode(), oi.getResultDescription());
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
        null, documentURI, od.getResultCode(), od.getResultDescription());
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
          null, oo.getCallUrlSnippet(), oo.getResultCode(),
          oo.getResultDescription());
    }
  }

}
