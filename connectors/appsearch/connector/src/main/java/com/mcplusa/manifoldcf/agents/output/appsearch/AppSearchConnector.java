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
package com.mcplusa.manifoldcf.agents.output.appsearch;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;

import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import com.mcplusa.manifoldcf.agents.output.appsearch.AppSearchAction.CommandEnum;
import com.mcplusa.manifoldcf.agents.output.appsearch.AppSearchConnection.Result;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.manifoldcf.connectorcommon.interfaces.IKeystoreManager;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 * This is the "output connector" for appsearch.
 */
public class AppSearchConnector extends BaseOutputConnector {

  private final static String APPSEARCH_INDEXATION_ACTIVITY = "Indexation";
  private final static String APPSEARCH_DELETION_ACTIVITY = "Deletion";

  private final static String[] APPSEARCH_ACTIVITIES = {APPSEARCH_INDEXATION_ACTIVITY, APPSEARCH_DELETION_ACTIVITY};

  private final static String APPSEARCH_TAB_SERVER = "AppSearchConnector.Server";
  private final static String APPSEARCH_TAB_PARAMETERS = "AppSearchConnector.Parameters";

  /**
   * Forward to the javascript to check the configuration parameters
   */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /**
   * Forward to the HTML template to edit the configuration parameters
   */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";
  private static final String EDIT_CONFIG_FORWARD_PARAMETERS = "editConfiguration_Parameters.html";

  /**
   * Forward to the HTML template to view the configuration parameters
   */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /**
   * Connection expiration interval
   */
  private static final long EXPIRATION_INTERVAL = 60000L;

  private HttpClientConnectionManager connectionManager = null;
  private HttpClient client = null;
  private long expirationTime = -1L;

  public AppSearchConnector() {
  }

  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
  }

  protected HttpClient getSession()
	  throws ManifoldCFException {
    if (client == null) {
      int socketTimeout = 900000; // TODO: Make this configurable
      int connectionTimeout = 60000; // TODO: Make this configurable

      // Load configuration from parameters
      final AppSearchConfig config = new AppSearchConfig(params);
      final IKeystoreManager keystoreManager = config.getSSLKeystore();


      // Set up ingest ssl if indicated
      SSLConnectionSocketFactory myFactory = SSLConnectionSocketFactory.getSocketFactory();

      // Set up connection manager
      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
	      .register("http", PlainConnectionSocketFactory.getSocketFactory())
	      .register("https", myFactory)
	      .build());
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(2000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
	      .setTcpNoDelay(true)
	      .setSoTimeout(socketTimeout)
	      .build());
      connectionManager = poolingConnectionManager;

      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
	      .setCircularRedirectsAllowed(true)
	      .setSocketTimeout(socketTimeout)
	      .setExpectContinueEnabled(true)
	      .setConnectTimeout(connectionTimeout)
	      .setConnectionRequestTimeout(socketTimeout);

      client = HttpClients.custom()
	      .setConnectionManager(connectionManager)
	      .setMaxConnTotal(1)
	      .disableAutomaticRetries()
	      .setDefaultRequestConfig(requestBuilder.build())
	      .setDefaultCredentialsProvider(credentialsProvider)
	      .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
	      .build();

    }
    expirationTime = System.currentTimeMillis() + EXPIRATION_INTERVAL;
    return client;
  }

  protected void closeSession() {
    if (connectionManager != null) {
      connectionManager.shutdown();
      connectionManager = null;
    }
    client = null;
    expirationTime = -1L;
  }

  /**
   * This method is called to assess whether to count this connector instance should actually be counted as being
   * connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected() {
    return connectionManager != null;
  }

  @Override
  public void disconnect()
	  throws ManifoldCFException {
    super.disconnect();
    closeSession();
  }

  @Override
  public void poll()
	  throws ManifoldCFException {
    super.poll();
    if (connectionManager != null) {
      if (System.currentTimeMillis() > expirationTime) {
	closeSession();
      }
    }
  }

  @Override
  public String[] getActivitiesList() {
    return APPSEARCH_ACTIVITIES;
  }

  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with the value and copy it to the out.
   *
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out,
	  Locale locale, AppSearchParam params,
	  String tabName, Integer sequenceNumber, Integer currentSequenceNumber) throws ManifoldCFException {
    Map<String, Object> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap(out);
      if (tabName != null) {
	paramMap.put("TabName", tabName);
      }
      if (currentSequenceNumber != null) {
	paramMap.put("SelectedNum", currentSequenceNumber.toString());
      }
    } else {
      paramMap = new HashMap<>();
    }
    if (sequenceNumber != null) {
      paramMap.put("SeqNum", sequenceNumber.toString());
    }

    Messages.outputResourceWithVelocity(out, locale, resName, paramMap);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
	  IHTTPOutput out, Locale locale, ConfigParams parameters,
	  List<String> tabsArray) throws ManifoldCFException, IOException {
    super.outputConfigurationHeader(threadContext, out, locale, parameters,
	    tabsArray);
    tabsArray.add(Messages.getString(locale, APPSEARCH_TAB_SERVER));
    tabsArray.add(Messages.getString(locale, APPSEARCH_TAB_PARAMETERS));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null, null, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
	  IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
	  throws ManifoldCFException, IOException {
    try {
      super.outputConfigurationBody(threadContext, out, locale, parameters,
	      tabName);
      AppSearchConfig config = this.getConfigParameters(parameters);
      outputResource(EDIT_CONFIG_FORWARD_SERVER, out, locale, config, tabName, null, null);
      outputResource(EDIT_CONFIG_FORWARD_PARAMETERS, out, locale, config, tabName, null, null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Build a Set of AppSearch parameters. If configParams is null, getConfiguration() is used.
   *
   * @param configParams
   */
  final private AppSearchConfig getConfigParameters(
	  ConfigParams configParams) {
    if (configParams == null) {
      configParams = getConfiguration();
    }
    return new AppSearchConfig(configParams);
  }

  @Override
  public VersionContext getPipelineDescription(Specification os)
	  throws ManifoldCFException {
    return new VersionContext("", params, os);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
	  Locale locale, ConfigParams parameters) throws ManifoldCFException,
	  IOException {
    outputResource(VIEW_CONFIG_FORWARD, out, locale,
	    getConfigParameters(parameters), null, null, null);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
	  IPostParameters variableContext, ConfigParams parameters)
	  throws ManifoldCFException {
    return AppSearchConfig.contextToConfig(threadContext, variableContext, parameters);
  }

  /**
   * Convert an unqualified ACL to qualified form.
   *
   * @param acl is the initial, unqualified ACL.
   * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
   * @param activities is the activities object, so we can report what's happening.
   * @return the modified ACL.
   * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
   */
  protected static String[] convertACL(String[] acl, String authorityNameString, IOutputAddActivity activities)
	  throws ManifoldCFException {
    if (acl != null) {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length) {
	rval[i] = activities.qualifyAccessToken(authorityNameString, acl[i]);
	i++;
      }
      return rval;
    }
    return new String[0];
  }

  /**
   * Add (or replace) a document in the output data store using the connector. This method presumes that the connector
   * object has been configured, and it is thus able to communicate with the output data store should that be necessary.
   *
   * @param documentURI is the URI of the document. The URI is presumed to be the unique identifier which the output
   * data store will use to process and serve the document. This URI is constructed by the repository connector which
   * fetches the document, and is thus universal across all output connectors.
   * @param pipelineDescription includes the description string that was constructed for this document by the
   * getOutputDescription() method.
   * @param document is the document data to be processed (handed to the output data store).
   * @param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in
   * with the repository document. May be null.
   * @param activities is the handle to an object that the implementer of a pipeline connector may use to perform
   * operations, such as logging processing activity, or sending a modified document to the next stage in the pipeline.
   * @return the document status (accepted or permanently rejected).
   * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
   * @throws IOException only if there's a stream error reading the document data.
   * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption
   */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
	  RepositoryDocument document, String authorityNameString,
	  IOutputAddActivity activities) throws ManifoldCFException,
	  ServiceInterruption, IOException {
    HttpClient addDocClient = getSession();
    AppSearchConfig config = getConfigParameters(null);

    InputStream inputStream = document.getBinaryStream();

    String[] acls = null;
    String[] denyAcls = null;
    String[] shareAcls = null;
    String[] shareDenyAcls = null;
    String[] parentAcls = null;
    String[] parentDenyAcls = null;
    Iterator<String> a = document.securityTypesIterator();
    while (a.hasNext()) {
      String securityType = a.next();
      String[] convertedAcls = convertACL(document.getSecurityACL(securityType), authorityNameString, activities);
      String[] convertedDenyAcls = convertACL(document.getSecurityDenyACL(securityType), authorityNameString, activities);
      if (securityType.equals(RepositoryDocument.SECURITY_TYPE_DOCUMENT)) {
	acls = convertedAcls;
	denyAcls = convertedDenyAcls;
      } else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_SHARE)) {
	shareAcls = convertedAcls;
	shareDenyAcls = convertedDenyAcls;
      } else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_PARENT)) {
	parentAcls = convertedAcls;
	parentDenyAcls = convertedDenyAcls;
      } else {
	activities.recordActivity(null, APPSEARCH_INDEXATION_ACTIVITY, document.getBinaryLength(), documentURI, activities.UNKNOWN_SECURITY, "Rejected document that has security info which AppSearch does not recognize: '" + securityType + "'");
	return DOCUMENTSTATUS_REJECTED;
      }
    }

    long startTime = System.currentTimeMillis();
    AppSearchIndex appsearch = new AppSearchIndex(addDocClient, config);
    try {
      appsearch.execute(documentURI, document, inputStream, acls, denyAcls, shareAcls, shareDenyAcls, parentAcls, parentDenyAcls);
      if (appsearch.getResult() != Result.OK) {
	return DOCUMENTSTATUS_REJECTED;
      }
      return DOCUMENTSTATUS_ACCEPTED;
    } finally {
      activities.recordActivity(startTime, APPSEARCH_INDEXATION_ACTIVITY, document.getBinaryLength(), documentURI, appsearch.getResultCode(), appsearch.getResultDescription());
    }
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
	  IOutputRemoveActivity activities) throws ManifoldCFException,
	  ServiceInterruption {
    HttpClient removeDocClient = getSession();
    long startTime = System.currentTimeMillis();
    AppSearchDelete od = new AppSearchDelete(removeDocClient, getConfigParameters(null));
    try {
      od.execute(documentURI);
    } finally {
      activities.recordActivity(startTime, APPSEARCH_DELETION_ACTIVITY, null, documentURI, od.getResultCode(), od.getResultDescription());
    }
  }

  @Override
  public String check() throws ManifoldCFException {
    HttpClient checkStatusClient = getSession();
    AppSearchAction oss = new AppSearchAction(checkStatusClient, getConfigParameters(null));
    try {
      oss.executeGET(CommandEnum._none, true);
      String resultName = oss.getResult().name();
      Logging.connectors.warn("Check Result: " + oss.getResult().toString());
      if (resultName.equals("OK")) {
	return super.check();
      }
      return resultName + " " + oss.getResultDescription();
    } catch (ServiceInterruption e) {
      return "Transient exception: " + e.getMessage();
    }
  }

}
