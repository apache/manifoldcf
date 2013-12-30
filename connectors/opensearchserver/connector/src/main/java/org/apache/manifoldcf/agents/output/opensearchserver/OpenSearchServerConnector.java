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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.opensearchserver.OpenSearchServerAction.CommandEnum;
import org.apache.manifoldcf.agents.output.opensearchserver.OpenSearchServerConnection.Result;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.system.Logging;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenSearchServerConnector extends BaseOutputConnector {

  private final static String OPENSEARCHSERVER_INDEXATION_ACTIVITY = "Optimize";
  private final static String OPENSEARCHSERVER_DELETION_ACTIVITY = "Deletion";
  private final static String OPENSEARCHSERVER_OPTIMIZE_ACTIVITY = "Indexation";

  private final static String[] OPENSEARCHSERVER_ACTIVITIES = {
      OPENSEARCHSERVER_INDEXATION_ACTIVITY, OPENSEARCHSERVER_DELETION_ACTIVITY,
      OPENSEARCHSERVER_OPTIMIZE_ACTIVITY };

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

  private ClientConnectionManager connectionManager = null;
  private HttpClient client = null;
  private long expirationTime = -1L;

  // Private data

  private String specsCacheOutpuDescription;
  private OpenSearchServerSpecs specsCache;

  public OpenSearchServerConnector() {
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
      PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
      localConnectionManager.setMaxTotal(1);
      connectionManager = localConnectionManager;

      int socketTimeout = 900000;
      int connectionTimeout = 60000;
      
      BasicHttpParams params = new BasicHttpParams();
      // This one is essential to prevent us from reading from the content stream before necessary during auth, but
      // is incompatible with some proxies.
      params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,true);
      // Enabled for Solr, but probably not necessary for better-behaved ES
      //params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
      params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,true);
      params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS,true);
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,socketTimeout);
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeout);
      params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS,true);
      DefaultHttpClient localClient = new DefaultHttpClient(connectionManager,params);
      // No retries
      localClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler()
        {
          public boolean retryRequest(
            IOException exception,
            int executionCount,
            HttpContext context)
          {
            return false;
          }
       
        });
      client = localClient;
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

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return connectionManager != null;
  }

  @Override
  public String[] getActivitiesList() {
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
    Locale locale, OpenSearchServerParam params, String tabName) throws ManifoldCFException {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
    }
    Messages.outputResourceWithVelocity(out,locale,resName,paramMap,false);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    super.outputConfigurationHeader(threadContext, out, locale, parameters, tabsArray);
    tabsArray.add(Messages.getString(locale,PARAMETERS_TAB_MESSAGE));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    super.outputConfigurationBody(threadContext, out, locale, parameters, tabName);
    OpenSearchServerConfig config = this.getConfigParameters(parameters);
    outputResource(EDIT_CONFIG_FORWARD, out, locale, config, tabName);
  }

  @Override
  public void outputSpecificationHeader(IHTTPOutput out,
      Locale locale, OutputSpecification os, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    super.outputSpecificationHeader(out, locale, os, tabsArray);
    tabsArray.add(Messages.getString(locale,OPENSEARCHSERVER_TAB_MESSAGE));
    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, null, null);
  }

  final private SpecificationNode getSpecNode(OutputSpecification os) {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++) {
      SpecificationNode node = os.getChild(i);
      if (OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE.equals(node
          .getType())) {
        return node;
      }
    }
    return null;
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, OutputSpecification os,
      String tabName) throws ManifoldCFException, IOException {
    super.outputSpecificationBody(out, locale, os, tabName);
    OpenSearchServerSpecs specs = getSpecParameters(os);
    outputResource(EDIT_SPEC_FORWARD, out, locale, specs, tabName);
  }

  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, OutputSpecification os) throws ManifoldCFException {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd) {
      specNode = new SpecificationNode(
          OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE);
    }
    OpenSearchServerSpecs.contextToSpecNode(variableContext, specNode);
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
      ConfigParams configParams) {
    if (configParams == null)
      configParams = getConfiguration();
    return new OpenSearchServerConfig(configParams);
  }

  final private OpenSearchServerSpecs getSpecParameters(OutputSpecification os)
      throws ManifoldCFException {
    return new OpenSearchServerSpecs(getSpecNode(os));
  }

  final private OpenSearchServerSpecs getSpecsCache(String outputDescription)
      throws ManifoldCFException {
    try {
      synchronized (this) {
        if (!outputDescription.equals(specsCacheOutpuDescription))
          specsCache = null;
        if (specsCache == null)
          specsCache = new OpenSearchServerSpecs(new JSONObject(
              outputDescription));
        return specsCache;
      }
    } catch (JSONException e) {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public String getOutputDescription(OutputSpecification os)
      throws ManifoldCFException {
    OpenSearchServerSpecs specs = new OpenSearchServerSpecs(getSpecNode(os));
    return specs.toJson().toString();
  }

  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
      throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    long maxFileSize = specs.getMaxFileSize();
    if (length > maxFileSize)
      return false;
    return super.checkLengthIndexable(outputDescription, length);
  }

  @Override
  public boolean checkDocumentIndexable(String outputDescription, File localFile)
      throws ManifoldCFException, ServiceInterruption {
    return true;
  }

  @Override
  public boolean checkMimeTypeIndexable(String outputDescription,
      String mimeType) throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    return specs.checkMimeType(mimeType);
  }

  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are not worth indexing.
  *@param outputDescription is the document's output version.
  *@param url is the URL of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkURLIndexable(String outputDescription, String url)
    throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    return specs.checkExtension(FilenameUtils.getExtension(url));
  }
    
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    outputResource(VIEW_CONFIG_FORWARD, out, locale, getConfigParameters(parameters), null);
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, OutputSpecification os)
      throws ManifoldCFException, IOException {
    outputResource(VIEW_SPEC_FORWARD, out, locale, getSpecParameters(os), null);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException {
    OpenSearchServerConfig.contextToConfig(variableContext, parameters);
    return null;
  }

  private static Map<String, Integer> ossInstances = null;

  private synchronized final Integer addInstance(OpenSearchServerConfig config) {
    if (ossInstances == null)
      ossInstances = new TreeMap<String, Integer>();
    synchronized (ossInstances) {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null) {
        count = new Integer(1);
        ossInstances.put(uii, count);
      } else
        count++;
      return count;
    }
  }

  private synchronized final void removeInstance(OpenSearchServerConfig config) {
    if (ossInstances == null)
      return;
    synchronized (ossInstances) {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null)
        return;
      if (--count == 0)
        ossInstances.remove(uii);
    }
  }

  @Override
  public int addOrReplaceDocument(String documentURI, String outputDescription,
      RepositoryDocument document, String authorityNameString,
      IOutputAddActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    HttpClient client = getSession();
    OpenSearchServerConfig config = getConfigParameters(null);
    Integer count = addInstance(config);
    synchronized (count) {
      InputStream inputStream = document.getBinaryStream();
      try {
        long startTime = System.currentTimeMillis();
        OpenSearchServerIndex oi = new OpenSearchServerIndex(
            client,
            documentURI,
            inputStream, config);
        activities.recordActivity(startTime,
            OPENSEARCHSERVER_INDEXATION_ACTIVITY, document.getBinaryLength(),
            documentURI, oi.getResult().name(), oi.getResultDescription());
        if (oi.getResult() != Result.OK)
          return DOCUMENTSTATUS_REJECTED;
      } finally {
        removeInstance(config);
      }
      return DOCUMENTSTATUS_ACCEPTED;
    }
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    OpenSearchServerDelete od = new OpenSearchServerDelete(
        client,
        documentURI,
        getConfigParameters(null));
    activities.recordActivity(startTime, OPENSEARCHSERVER_DELETION_ACTIVITY,
        null, documentURI, od.getResult().name(), od.getResultDescription());
  }

  @Override
  public String check() throws ManifoldCFException {
    HttpClient client = getSession();
    OpenSearchServerSchema oss = new OpenSearchServerSchema(
        client,
        getConfigParameters(null));
    return oss.getResult().name() + " " + oss.getResultDescription();
  }

  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    OpenSearchServerAction oo = new OpenSearchServerAction(
        client,
        CommandEnum.optimize, getConfigParameters(null));
    activities.recordActivity(startTime, OPENSEARCHSERVER_OPTIMIZE_ACTIVITY,
        null, oo.getCallUrlSnippet(), oo.getResult().name(),
        oo.getResultDescription());
  }

}
