/* $Id$ */

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
package org.apache.manifoldcf.agents.transformation.tikaservice.rmeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.json.simple.parser.ParseException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * This connector works as a transformation connector, but does nothing other than logging.
 *
 */
public class TikaExtractor extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector {
  public static final String _rcsid = "@(#)$Id$";

  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";
  private static final String EDIT_CONFIGURATION_SERVER_HTML = "editConfiguration_Server.html";
  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String EDIT_SPECIFICATION_TIKASERVER_HTML = "editSpecification_TikaServer.html";
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

  protected static final String ACTIVITY_EXTRACT = "extract";

  protected static final String[] activitiesList = new String[] { ACTIVITY_EXTRACT };
  protected final static long sessionExpirationInterval = 300000L;

  /** We handle up to 64K in memory; after that we go to disk. */
  protected static final long inMemoryMaximumFile = 65536;

  // Metadata name exceeding 8k chars may trigger exceptions when the Solr output connector is used
  private static final int maxMetadataNameLength = 8000;

  // Raw parameters

  /** Tika host name */
  private String tikaHostname = null;

  /** Tika port */
  private String tikaPortString = null;

  /** Connection timeout */
  private String connectionTimeoutString = null;

  /** Socket timeout */
  private String socketTimeoutString = null;

  /** Retry interval */
  private String retryIntervalString = null;

  /** Retry interval when Tika seems down */
  private String retryIntervalTikaDownString = null;

  /** Retry number */
  private String retryNumberString = null;

  // Computed parameters

  /** Session timeout */
  private long sessionTimeout = -1L;

  /** Tika port */
  private int tikaPort = -1;

  /** Connection timeout */
  private int connectionTimeout = -1;

  /** Socket timeout */
  private int socketTimeout = -1;

  /** Retry interval */
  private long retryInterval = -1L;

  /** Retry interval */
  private long retryIntervalTikaDown = -1L;

  /** Retry number */
  private int retryNumber = -1;

  /** Connection manager */
  private HttpClientConnectionManager connectionManager = null;

  /** HttpClientBuilder */
  private HttpClientBuilder builder = null;

  /** Httpclient instance */
  private CloseableHttpClient httpClient = null;

  /** HttpHost */
  private HttpHost tikaHost = null;

  // Static data

  /** Metadata URI */
  protected final static URI rmetaURI;

  private final static Set<String> archiveMimes = new HashSet<>();
  static {
    archiveMimes.add("application/gzip");
    archiveMimes.add("application/zip");
    archiveMimes.add("application/x-gtar");
    archiveMimes.add("application/x-7z-compressed");
    archiveMimes.add("application/x-xz");
    archiveMimes.add("application/x-bzip2");
    archiveMimes.add("application/x-cpio");
    archiveMimes.add("application/x-java-pack200");
    archiveMimes.add("application/java-archive");
    archiveMimes.add("application/x-archive");
    archiveMimes.add("application/vnd.ms-outlook");
  }

  final static Set<String> archiveExtensions = new HashSet<>();
  static {
    archiveExtensions.add("zip");
    archiveExtensions.add("gz");
    archiveExtensions.add("tar");
    archiveExtensions.add("gtar");
    archiveExtensions.add("7z");
    archiveExtensions.add("xz");
    archiveExtensions.add("boz");
    archiveExtensions.add("bz2");
    archiveExtensions.add("cpio");
    archiveExtensions.add("jar");
    archiveExtensions.add("ar");
    archiveExtensions.add("a");
    archiveExtensions.add("pab");
    archiveExtensions.add("ost");
    archiveExtensions.add("pst");
  }

  static {
    try {
      rmetaURI = new URI("/rmeta/body");
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  /**
   * Connect.
   *
   * @param configParameters is the set of configuration parameters, which in this case describe the root directory.
   */
  @Override
  public void connect(final ConfigParams configParameters) {
    super.connect(configParameters);
    tikaHostname = configParameters.getParameter(TikaConfig.PARAM_TIKAHOSTNAME);
    tikaPortString = configParameters.getParameter(TikaConfig.PARAM_TIKAPORT);
    connectionTimeoutString = configParameters.getParameter(TikaConfig.PARAM_CONNECTIONTIMEOUT);
    socketTimeoutString = configParameters.getParameter(TikaConfig.PARAM_SOCKETTIMEOUT);
    retryIntervalString = configParameters.getParameter(TikaConfig.PARAM_RETRYINTERVAL);
    retryIntervalTikaDownString = configParameters.getParameter(TikaConfig.PARAM_RETRYINTERVALTIKADOWN);
    retryNumberString = configParameters.getParameter(TikaConfig.PARAM_RETRYNUMBER);
  }

  /**
   * Close the connection. Call this before discarding the repository connector.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    expireSession();
    tikaHostname = null;
    tikaPortString = null;
    connectionTimeoutString = null;
    socketTimeoutString = null;
    retryIntervalString = null;
    retryIntervalTikaDownString = null;
    retryNumberString = null;

    super.disconnect();
  }

  /**
   * This method is periodically called for all connectors that are connected but not in active use.
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (System.currentTimeMillis() >= sessionTimeout) {
      expireSession();
    }
    if (connectionManager != null) {
      connectionManager.closeIdleConnections(60000L, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * This method is called to assess whether to count this connector instance should actually be counted as being connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected() {
    return sessionTimeout != -1L;
  }

  /** Set up a session */
  protected void getSession() throws ManifoldCFException {
    if (sessionTimeout == -1L) {
      if (tikaHostname == null || tikaHostname.length() == 0) {
        throw new ManifoldCFException("Missing host name");
      }
      if (tikaPortString == null) {
        throw new ManifoldCFException("Missing port value");
      }
      try {
        this.tikaPort = Integer.parseInt(tikaPortString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad port number: " + tikaPortString);
      }
      try {
        this.connectionTimeout = Integer.parseInt(connectionTimeoutString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad connection timeout number: " + connectionTimeoutString);
      }
      try {
        this.socketTimeout = Integer.parseInt(socketTimeoutString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad socket timeout number: " + socketTimeoutString);
      }
      try {
        this.retryInterval = Long.parseLong(retryIntervalString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad retry interval number: " + retryIntervalString);
      }
      try {
        this.retryIntervalTikaDown = Long.parseLong(retryIntervalTikaDownString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad retry interval when tika is down number: " + retryIntervalTikaDownString);
      }
      try {
        this.retryNumber = Integer.parseInt(retryNumberString);
      } catch (final NumberFormatException e) {
        throw new ManifoldCFException("Bad retry number: " + retryNumberString);
      }

      final PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(
          RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory())
              // .register("https", myFactory)
              .build());
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(2000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setTcpNoDelay(true).setSoTimeout(socketTimeout).build());

      this.connectionManager = poolingConnectionManager;

      final RequestConfig.Builder requestBuilder = RequestConfig.custom().setCircularRedirectsAllowed(true).setSocketTimeout(socketTimeout).setExpectContinueEnabled(false)
          .setConnectTimeout(connectionTimeout).setConnectionRequestTimeout(socketTimeout);

      this.builder = HttpClients.custom().setConnectionManager(connectionManager).disableAutomaticRetries().setDefaultRequestConfig(requestBuilder.build());
      builder.setRequestExecutor(new HttpRequestExecutor(socketTimeout)).setRedirectStrategy(new DefaultRedirectStrategy());
      this.httpClient = builder.build();

      this.tikaHost = new HttpHost(tikaHostname, tikaPort);

    }
    sessionTimeout = System.currentTimeMillis() + sessionExpirationInterval;
  }

  /** Expire the current session */
  protected void expireSession() throws ManifoldCFException {
    tikaPort = -1;
    httpClient = null;
    tikaHost = null;
    if (connectionManager != null) {
      connectionManager.shutdown();
    }
    connectionManager = null;
    sessionTimeout = -1L;
  }

  /**
   * Test the connection. Returns a string describing the connection integrity.
   *
   * @return the connection's status as a displayable string.
   */
  @Override
  public String check() throws ManifoldCFException {
    getSession();
    final HttpPut httpPut = new HttpPut(rmetaURI);
    final HttpEntity entity = new InputStreamEntity(new ByteArrayInputStream("this is a test".getBytes(StandardCharsets.UTF_8)));
    httpPut.setEntity(entity);
    CloseableHttpResponse response = null;
    try {
      try {
        response = this.httpClient.execute(tikaHost, httpPut);
      } catch (final IOException e) {
        return "Connection error: " + e.getMessage();
      }
      final int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode != 200) {
        return "Bad response: " + response.getStatusLine();
      }
      return super.check();
    } finally {
      if (response != null) {
        try {
          response.close();
        } catch (final IOException e) {
          return "Connection error: " + e.getMessage();
        }
      }
    }
  }

  /**
   * Return a list of activities that this connector generates. The connector does NOT need to be connected before this method is called.
   *
   * @return the set of activities.
   */
  @Override
  public String[] getActivitiesList() {
    return activitiesList;
  }

  /**
   * Output the configuration header section. This method is called in the head section of the connector's configuration page. Its purpose is to add the required tabs to the list, and to output any
   * javascript methods that might be needed by the configuration editing HTML.
   *
   * @param threadContext is the local thread context.
   * @param out           is the output to which any HTML should be sent.
   * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
   * @param tabsArray     is an array of tab names. Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale, final ConfigParams parameters, final List<String> tabsArray)
      throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "TikaExtractor.TikaServerTabName"));
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_JS, null);
  }

  /**
   * Output the configuration body section. This method is called in the body section of the connector's configuration page. Its purpose is to present the required form elements for editing. The coder
   * can presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags. The name of the form is "editconnection".
   *
   * @param threadContext is the local thread context.
   * @param out           is the output to which any HTML should be sent.
   * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
   * @param tabName       is the current tab name.
   */
  @Override
  public void outputConfigurationBody(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale, final ConfigParams parameters, final String tabName)
      throws ManifoldCFException, IOException {
    final Map<String, Object> velocityContext = new HashMap<>();
    velocityContext.put("TabName", tabName);
    fillInServerTab(velocityContext, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_SERVER_HTML, velocityContext);
  }

  /**
   * Process a configuration post. This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been posted. Its
   * purpose is to gather form information and modify the configuration parameters accordingly. The name of the posted form is "editconnection".
   *
   * @param threadContext   is the local thread context.
   * @param variableContext is the set of variables available from the post, including binary file post information.
   * @param parameters      are the configuration parameters, as they currently exist, for this connection being configured.
   * @return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
   */
  @Override
  public String processConfigurationPost(final IThreadContext threadContext, final IPostParameters variableContext, final Locale locale, final ConfigParams parameters) throws ManifoldCFException {
    final String tikaHostname = variableContext.getParameter("tikaHostname");

    if (tikaHostname != null) {
      parameters.setParameter(TikaConfig.PARAM_TIKAHOSTNAME, tikaHostname);
    }

    final String tikaPort = variableContext.getParameter("tikaPort");
    if (tikaPort != null) {
      parameters.setParameter(TikaConfig.PARAM_TIKAPORT, tikaPort);
    }

    final String connectionTimeout = variableContext.getParameter(TikaConfig.PARAM_CONNECTIONTIMEOUT);
    if (connectionTimeout != null) {
      parameters.setParameter(TikaConfig.PARAM_CONNECTIONTIMEOUT, connectionTimeout);
    }

    final String socketTimeout = variableContext.getParameter(TikaConfig.PARAM_SOCKETTIMEOUT);
    if (socketTimeout != null) {
      parameters.setParameter(TikaConfig.PARAM_SOCKETTIMEOUT, socketTimeout);
    }

    final String retryInterval = variableContext.getParameter(TikaConfig.PARAM_RETRYINTERVAL);
    if (retryInterval != null) {
      parameters.setParameter(TikaConfig.PARAM_RETRYINTERVAL, retryInterval);
    }

    final String retryIntervalTikaDown = variableContext.getParameter(TikaConfig.PARAM_RETRYINTERVALTIKADOWN);
    if (retryIntervalTikaDown != null) {
      parameters.setParameter(TikaConfig.PARAM_RETRYINTERVALTIKADOWN, retryIntervalTikaDown);
    }

    final String retryNumber = variableContext.getParameter(TikaConfig.PARAM_RETRYNUMBER);
    if (retryNumber != null) {
      parameters.setParameter(TikaConfig.PARAM_RETRYNUMBER, retryNumber);
    }

    return null;
  }

  /**
   * View configuration. This method is called in the body section of the connector's view configuration page. Its purpose is to present the connection information to the user. The coder can presume
   * that the HTML that is output from this configuration will be within appropriate &lt;html&gt; and &lt;body&gt; tags.
   *
   * @param threadContext is the local thread context.
   * @param out           is the output to which any HTML should be sent.
   * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
   */
  @Override
  public void viewConfiguration(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale, final ConfigParams parameters) throws ManifoldCFException, IOException {
    final Map<String, Object> velocityContext = new HashMap<>();
    fillInServerTab(velocityContext, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIGURATION_HTML, velocityContext);
  }

  protected static void fillInServerTab(final Map<String, Object> velocityContext, final IHTTPOutput out, final ConfigParams parameters) throws ManifoldCFException {
    String tikaHostname = parameters.getParameter(TikaConfig.PARAM_TIKAHOSTNAME);
    if (tikaHostname == null) {
      tikaHostname = TikaConfig.TIKAHOSTNAME_DEFAULT;
    }

    String tikaPort = parameters.getParameter(TikaConfig.PARAM_TIKAPORT);
    if (tikaPort == null) {
      tikaPort = TikaConfig.TIKAPORT_DEFAULT;
    }

    String connectionTimeout = parameters.getParameter(TikaConfig.PARAM_CONNECTIONTIMEOUT);
    if (connectionTimeout == null) {
      connectionTimeout = TikaConfig.CONNECTIONTIMEOUT_DEFAULT;
    }

    String socketTimeout = parameters.getParameter(TikaConfig.PARAM_SOCKETTIMEOUT);
    if (socketTimeout == null) {
      socketTimeout = TikaConfig.SOCKETTIMEOUT_DEFAULT;
    }

    String retryInterval = parameters.getParameter(TikaConfig.PARAM_RETRYINTERVAL);
    if (retryInterval == null) {
      retryInterval = TikaConfig.RETRYINTERVAL_DEFAULT;
    }

    String retryIntervalTikaDown = parameters.getParameter(TikaConfig.PARAM_RETRYINTERVALTIKADOWN);
    if (retryIntervalTikaDown == null) {
      retryIntervalTikaDown = TikaConfig.RETRYINTERVALTIKADOWN_DEFAULT;
    }

    String retryNumber = parameters.getParameter(TikaConfig.PARAM_RETRYNUMBER);
    if (retryNumber == null) {
      retryNumber = TikaConfig.RETRYNUMBER_DEFAULT;
    }

    // Fill in context
    velocityContext.put("TIKAHOSTNAME", tikaHostname);
    velocityContext.put("TIKAPORT", tikaPort);
    velocityContext.put("CONNECTIONTIMEOUT", connectionTimeout);
    velocityContext.put("SOCKETTIMEOUT", socketTimeout);
    velocityContext.put("RETRYINTERVAL", retryInterval);
    velocityContext.put("RETRYINTERVALTIKADOWN", retryIntervalTikaDown);
    velocityContext.put("RETRYNUMBER", retryNumber);
  }

  /**
   * Get an output version string, given an output specification. The output version string is used to uniquely describe the pertinent details of the output specification and the configuration, to
   * allow the Connector Framework to determine whether a document will need to be output again. Note that the contents of the document cannot be considered by this method, and that a different
   * version string (defined in IRepositoryConnector) is used to describe the version of the actual document.
   *
   * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be necessary.
   *
   * @param os is the current output specification for the job that is doing the crawling.
   * @return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal, the document will not need to be sent
   *         again to the output data store.
   */
  @Override
  public VersionContext getPipelineDescription(final Specification os) throws ManifoldCFException, ServiceInterruption {
    final SpecPacker sp = new SpecPacker(os);
    return new VersionContext(sp.toPackedString(), params, os);
  }

  // We intercept checks pertaining to the document format and send modified
  // checks further down

  /**
   * Detect if a mime type is acceptable or not. This method is used to determine whether it makes sense to fetch a document in the first place.
   *
   * @param pipelineDescription is the document's pipeline version string, for this connection.
   * @param mimeType            is the mime type of the document.
   * @param checkActivity       is an object including the activities that can be performed by this method.
   * @return true if the mime type can be accepted by this connector.
   */
  @Override
  public boolean checkMimeTypeIndexable(final VersionContext pipelineDescription, final String mimeType, final IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption {
    // We should see what Tika will transform
    // MHL
    // Do a downstream check
    return checkActivity.checkMimeTypeIndexable("text/plain;charset=utf-8");
  }

  /**
   * Pre-determine whether a document (passed here as a File object) is acceptable or not. This method is used to determine whether a document needs to be actually transferred. This hook is provided
   * mainly to support search engines that only handle a small set of accepted file types.
   *
   * @param pipelineDescription is the document's pipeline version string, for this connection.
   * @param localFile           is the local file to check.
   * @param checkActivity       is an object including the activities that can be done by this method.
   * @return true if the file is acceptable, false if not.
   */
  @Override
  public boolean checkDocumentIndexable(final VersionContext pipelineDescription, final File localFile, final IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption {
    // Document contents are not germane anymore, unless it looks like Tika
    // won't accept them.
    // Not sure how to check that...
    return true;
  }

  /**
   * Pre-determine whether a document's length is acceptable. This method is used to determine whether to fetch a document in the first place.
   *
   * @param pipelineDescription is the document's pipeline version string, for this connection.
   * @param length              is the length of the document.
   * @param checkActivity       is an object including the activities that can be done by this method.
   * @return true if the file is acceptable, false if not.
   */
  @Override
  public boolean checkLengthIndexable(final VersionContext pipelineDescription, final long length, final IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption {
    // Always true
    return true;
  }

  private void triggerServiceInterruption(final String documentURI, final Exception e) throws ServiceInterruption {
    // Retry retryNumber times, retryInterval ms between retries, and abort if
    // doesn't
    // work
    Logging.ingest.warn("Tika Server unreachable while trying to process " + documentURI + ", retrying...", e);
    final long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("Tika Server connection down: " + e.getMessage(), e, currentTime + retryIntervalTikaDown, -1L, retryNumber, false);
  }

  private void retryWithoutAbort(final Exception e) throws ServiceInterruption {
    // Retry retryNumber times, retryInterval ms between retries, and skip document if
    // doesn't
    // work
    Logging.ingest.warn("Tika Server connection interrupted, retrying...", e);
    final long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("Tika Server connection interrupted: " + e.getMessage(), e, currentTime + retryInterval, -1L, retryNumber, false);
  }

  /**
   * Add (or replace) a document in the output data store using the connector. This method presumes that the connector object has been configured, and it is thus able to communicate with the output
   * data store should that be necessary. The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the output
   * description, since that was what was partly used to determine if output should be taking place. So it may be necessary for this method to decode an output description string in order to determine
   * what should be done.
   *
   * @param documentURI         is the URI of the document. The URI is presumed to be the unique identifier which the output data store will use to process and serve the document. This URI is
   *                            constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
   * @param pipelineDescription is the description string that was constructed for this document by the getOutputDescription() method.
   * @param document            is the document data to be processed (handed to the output data store).
   * @param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document. May be null.
   * @param activities          is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity, or sending a modified
   *                            document to the next stage in the pipeline.
   * @return the document status (accepted or permanently rejected).
   * @throws IOException only if there's a stream error reading the document data.
   */
  @Override
  public int addOrReplaceDocumentWithException(final String documentURI, final VersionContext pipelineDescription, final RepositoryDocument document, final String authorityNameString,
      final IOutputAddActivity activities) throws ManifoldCFException, ServiceInterruption, IOException {
    // First, make sure downstream pipeline will now accept
    // text/plain;charset=utf-8
    if (!activities.checkMimeTypeIndexable("text/plain;charset=utf-8")) {
      activities.noDocument();
      activities.recordActivity(null, ACTIVITY_EXTRACT, null, documentURI, activities.EXCLUDED_MIMETYPE, "Downstream pipeline rejected mime type 'text/plain;charset=utf-8'");
      return DOCUMENTSTATUS_REJECTED;
    }

    final SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());

    getSession();

    // Tika server variables
    CloseableHttpResponse response = null;

    // Tika's API reads from an input stream and writes to an output Writer.
    // Since a RepositoryDocument includes readers and inputstreams exclusively,
    // AND all downstream
    // processing needs to occur in a ManifoldCF thread, we have some
    // constraints on the architecture we need to get this done:
    // (1) The principle worker thread must call the downstream pipeline send()
    // method.
    // (2) The callee of the send() method must call a reader in the Repository
    // Document.
    // (3) The Reader, if its databuffer is empty, must pull more data from the
    // original input stream and hand it to Tika, which populates the Reader's
    // databuffer.
    // So all this can be done in one thread, with some work, and the creation
    // of a special InputStream or Reader implementation. Where it fails,
    // though, is the
    // requirement that tika-extracted metadata be included in the
    // RepositoryDocument right from the beginning. Effectively this means that
    // the entire document
    // must be parsed before it is handed downstream -- so basically a temporary
    // file (or in-memory buffer if small enough) must be created.
    // Instead of the elegant flow above, we have the following:
    // (1) Create a temporary file (or in-memory buffer if file is small enough)
    // (2) Run Tika to completion, streaming content output to temporary file
    // (3) Modify RepositoryDocument to read from temporary file, and include
    // Tika-extracted metadata
    // (4) Call downstream document processing

    // Prepare the destination storage
    DestinationStorage ds;

    if (document.getBinaryLength() <= inMemoryMaximumFile) {
      ds = new MemoryDestinationStorage((int) document.getBinaryLength());
    } else {
      ds = new FileDestinationStorage();
    }

    try {
      final Map<String, List<String>> metadata = new HashMap<>();
      final List<String> embeddedResourcesNames = new ArrayList<>();
      if (document.getFileName() != null) {
        metadata.put(TikaMetadataKeys.RESOURCE_NAME_KEY, new ArrayList<>());
        metadata.put("stream_name", new ArrayList<>());
        metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY).add(document.getFileName());
        metadata.get("stream_name").add(document.getFileName());
      }
      metadata.put("stream_size", new ArrayList<>());
      metadata.get("stream_size").add(String.valueOf(document.getBinaryLength()));

      // We only log the extraction
      final long startTime = System.currentTimeMillis();
      String resultCode = "OK";
      String description = "";
      Long length = 0L;
      boolean truncated = false;
      boolean resources_limit = false;

      int tikaServerResultCode = 0;

      try {
        try {

          // Process the document only if it is not an archive or if the extract archives
          // option is set to true
          if (!isArchive(document.getFileName(), document.getMimeType()) || isArchive(document.getFileName(), document.getMimeType()) && sp.extractArchives) {

            // Send document to the Tika Server
            final HttpPut httpPut = new HttpPut(rmetaURI);
            if (sp.writeLimit != -1) {
              httpPut.addHeader("writeLimit", String.valueOf(sp.writeLimit));
            }
            if (sp.maxEmbeddedResources != -1) {
              httpPut.addHeader("maxEmbeddedResources", String.valueOf(sp.maxEmbeddedResources));
            }
            final HttpEntity entity = new InputStreamEntity(document.getBinaryStream());
            httpPut.setEntity(entity);
            try {
              response = this.httpClient.execute(tikaHost, httpPut);
            } catch (final SocketTimeoutException e) { // The document is probably too big ! So we don't retry it
              resultCode = "TIKASERVERRESPONSETIMEOUT";
              description = "Socket timeout while processing document " + documentURI + " : " + e.getMessage();
              tikaServerResultCode = handleTikaServerError(description);
            } catch (final SocketException e) {
              // If the exception occurred after the connection, this probably means that the
              // tika server is not
              // down ! so retry {retryNumber} times without aborting the job in case of
              // failure
              if (!(e instanceof ConnectException) && !(e instanceof BindException) && !(e instanceof NoRouteToHostException) && !(e instanceof PortUnreachableException)) {
                resultCode = "TIKASERVERSOCKETEXCEPTION";
                description = "Socket exception while processing document " + documentURI + " : " + e.getMessage();
                tikaServerResultCode = handleTikaServerError(description);
                retryWithoutAbort(e);
              } else { // The tika server seams to be down : retry {retryNumber} times and abort the
                // job if it fails on
                // each retry
                resultCode = "TIKASERVEREXCEPTION";
                description = "Tika seemed to be down when requested to process document " + documentURI + " : " + e.getMessage();
                tikaServerResultCode = handleTikaServerError(description);
                triggerServiceInterruption(documentURI, e);
              }
            } catch (final NoHttpResponseException e) {
              // Tika probably does not manage to process document in time (task timeout)
              resultCode = "TIKASERVERNORESPONSEEXCEPTION";
              description = "Tika does not manage to treat " + documentURI + " (potential task timeout): " + e.getMessage();
              tikaServerResultCode = handleTikaServerError(description);
            } catch (final IOException e) { // Unknown problem with the Tika Server. Retry {retryNumber} times and abort
              // the job if it fails on
              // each retry
              resultCode = "TIKASERVEREXCEPTION";
              description = "Unknown Tika problem when processing document " + documentURI + " : " + e.getMessage();
              tikaServerResultCode = handleTikaServerError(description);
              triggerServiceInterruption(documentURI, e);
            }
            if (response != null) {
              final int responseCode = response.getStatusLine().getStatusCode();
              if (responseCode == 200 || responseCode == 204) {
                try (final OutputStream os = ds.getOutputStream(); Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8.name()); InputStream is = response.getEntity().getContent();) {

                  final JsonFactory jfactory = new JsonFactory();
                  final JsonParser jParser = jfactory.createParser(is);
                  JsonToken token = null;

                  // Go to beginning of metadata
                  boolean inMetadata = false;
                  while (!inMetadata && (token = jParser.nextToken()) != null) {
                    if (token == JsonToken.START_OBJECT) {
                      inMetadata = true;
                    }
                  }

                  int totalMetadataLength = 0;
                  boolean maxMetadataReached = false;
                  boolean metadataSkipped = false;
                  boolean metadataTruncated = false;

                  if (token != null) {
                    while ((token = jParser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                      final int fieldNameLength = jParser.getTextLength();
                      if (fieldNameLength <= maxMetadataNameLength) {
                        final String fieldName = jParser.getCurrentName();
                        if (fieldName != null) {
                          if (fieldName.startsWith("X-Parsed-By")) {
                            skipMetadata(jParser);
                          } else if (fieldName.contentEquals("X-TIKA:content")) {
                            // Consume content
                            jParser.nextToken();
                            length += jParser.getText(w);
                          } else if (!fieldName.startsWith("X-TIKA")) {
                            token = jParser.nextToken();
                            if (!metadata.containsKey(fieldName)) {
                              totalMetadataLength += fieldName.length();
                              metadata.put(fieldName, new ArrayList<>());
                            }
                            if (token == JsonToken.START_ARRAY) {
                              while (jParser.nextToken() != JsonToken.END_ARRAY) {
                                if (jParser.getTextLength() <= sp.maxMetadataValueLength) {
                                  final int totalMetadataLengthPreview = totalMetadataLength + jParser.getTextLength();
                                  if (totalMetadataLengthPreview <= sp.totalMetadataLimit) {
                                    metadata.get(fieldName).add(jParser.getText());
                                    totalMetadataLength = totalMetadataLengthPreview;
                                  } else {
                                    maxMetadataReached = true;
                                  }
                                } else {
                                  metadataSkipped = true;
                                  if (Logging.ingest.isDebugEnabled()) {
                                    Logging.ingest
                                        .debug("Skip value of metadata " + fieldName + " of document " + documentURI + " because it exceeds the max value limit of " + sp.maxMetadataValueLength);
                                  }
                                }
                              }
                            } else {
                              if (jParser.getTextLength() <= sp.maxMetadataValueLength) {
                                final int totalMetadataLengthPreview = totalMetadataLength + jParser.getTextLength();
                                if (totalMetadataLengthPreview <= sp.totalMetadataLimit) {
                                  metadata.get(fieldName).add(jParser.getText());
                                } else {
                                  maxMetadataReached = true;
                                }
                              } else {
                                metadataSkipped = true;
                                if (Logging.ingest.isDebugEnabled()) {
                                  Logging.ingest
                                      .debug("Skip value of metadata " + fieldName + " of document " + documentURI + " because it exceeds the max value limit of " + sp.maxMetadataValueLength);
                                }
                              }
                            }
                            // Remove metadata if no data has been gathered
                            if (metadata.get(fieldName).isEmpty()) {
                              totalMetadataLength -= fieldName.length();
                              metadata.remove(fieldName);
                            }
                          } else if (fieldName.startsWith("X-TIKA:EXCEPTION:")) { // deal with Tika exceptions
                            boolean unknownException = false;
                            if (fieldName.contentEquals("X-TIKA:EXCEPTION:write_limit_reached")) {
                              resultCode = "TRUNCATEDOK";
                              truncated = true;
                            } else if (fieldName.contentEquals("X-TIKA:EXCEPTION:embedded_resource_limit_reached")) {
                              resources_limit = true;
                            } else if (!fieldName.contentEquals("X-TIKA:EXCEPTION:warn")) { // If the exception is other than a warning message
                              unknownException = true;
                              resultCode = "TIKAEXCEPTION";
                              description += getTikaExceptionDesc(jParser) + System.lineSeparator();
                            }
                            if (!unknownException) {
                              skipMetadata(jParser);
                            }
                          } else if (fieldName.startsWith("X-TIKA:WARN:truncated_metadata")) {
                            metadataTruncated = true;
                            skipMetadata(jParser);
                          } else {
                            skipMetadata(jParser);
                          }
                        }
                      } else {
                        metadataSkipped = true;
                        if (Logging.ingest.isDebugEnabled()) {
                          Logging.ingest.debug("Skip a metadata of document " + documentURI + " because its name exceeds the max allowed length of " + maxMetadataNameLength);
                        }
                        skipMetadata(jParser);
                      }
                    }

                    // If token not null then there are embedded resources, process them if the extractArchives option is enabled
                    if (token != null && token == JsonToken.END_OBJECT && sp.extractArchives) {
                      // For embedded resource we only gather resourceNames and resources content, skip the rest
                      while ((token = jParser.nextToken()) != null) {
                        final String fieldName = jParser.getCurrentName();
                        if (fieldName != null && fieldName.contentEquals("resourceName")) {
                          token = jParser.nextToken();
                          if (jParser.getTextLength() <= sp.maxMetadataValueLength) {
                            embeddedResourcesNames.add(jParser.getText());
                          } else {
                            metadataSkipped = true;
                          }
                        } else if (fieldName != null && fieldName.contentEquals("X-TIKA:content")) {
                          // Add embedded resource content to main document content
                          jParser.nextToken();
                          length += jParser.getText(w);
                        }
                      }
                    }

                    jParser.close();
                  }

                  // If the are embedded resources, add their names, if possible, to the metadata
                  for (final String embeddedResourceName : embeddedResourcesNames) {
                    final int resourceNameBytesLength = embeddedResourceName.getBytes(StandardCharsets.UTF_8).length;

                    final int totalMetadataLengthPreview = totalMetadataLength + resourceNameBytesLength;
                    if (totalMetadataLengthPreview <= sp.totalMetadataLimit) {
                      if (!metadata.containsKey("embeddedResourcesNames")) {
                        totalMetadataLength += "embeddedResourcesNames".getBytes(StandardCharsets.UTF_8).length;
                        metadata.put("embeddedResourcesNames", new ArrayList<>());
                      }
                      metadata.get("embeddedResourcesNames").add(embeddedResourceName);
                      totalMetadataLength += resourceNameBytesLength;
                    } else {
                      maxMetadataReached = true;
                    }

                  }

                  if (maxMetadataReached) {
                    description += "Some metadata have been skipped because the total metadata limit of " + sp.totalMetadataLimit + " has been reached" + System.lineSeparator();
                  } else if (metadataSkipped) {
                    description += "Some metadata have been skipped because their names or values exceeded the limits" + System.lineSeparator();
                  }

                  if (metadataTruncated) {
                    description += "Some metadata have been truncated by Tika because they exceeded the limits specified in the Tika conf" + System.lineSeparator();
                  }
                }
              } else if (responseCode == 503) {
                // Service interruption; Tika trying to come up.
                // Retry unlimited times, retryInterval ms between retries
                resultCode = "TIKASERVERUNAVAILABLE";
                description = "Tika Server was unavailable: 503 " + response.getStatusLine().getReasonPhrase();
                tikaServerResultCode = handleTikaServerError(description);
                Logging.ingest.warn("Tika Server unavailable, retrying...");
                final long currentTime = System.currentTimeMillis();
                throw new ServiceInterruption("Tika Server unavailable, retrying...", new Exception(description), currentTime + retryInterval, -1L, -1, false);
              } else {
                if (responseCode == 500) {
                  resultCode = "TIKASERVERERROR";
                  description = "Tika Server failed to parse document with the following error: " + response.getStatusLine().getReasonPhrase();
                  tikaServerResultCode = handleTikaServerError(description);
                } else {
                  resultCode = "TIKASERVERREJECTS";
                  description = "Tika Server rejected document " + documentURI + " with the following reason: " + response.getStatusLine().getReasonPhrase();
                  tikaServerResultCode = handleTikaServerRejects(description);
                }
              }
            }
          } else {
            resultCode = "EXCLUDED";
            description = "Detected as an archive file and the extract archives option is set to false";
          }

        } catch (final IOException e) {
          resultCode = "TIKASERVERRESPONSEISSUE";
          if (e.getMessage() != null) {
            description = e.getMessage();
          }
          tikaServerResultCode = handleTikaServerException(e);
        } finally {
          if (response != null) {
            response.close();
          }
        }

        if (!activities.checkLengthIndexable(ds.getBinaryLength())) {
          activities.noDocument();
          resultCode = activities.EXCLUDED_LENGTH;
          description = "Downstream pipeline rejected document with length " + ds.getBinaryLength();
          return DOCUMENTSTATUS_REJECTED;
        }

      } finally {
        // Before injecting activity record, clean the description as it can contains non ascii chars that can cause errors during SQL insertion
        description = description.replaceAll("[^\\x20-\\x7e]", "");
        // Log the extraction processing
        activities.recordActivity(startTime, ACTIVITY_EXTRACT, length, documentURI, resultCode, description);
      }

      // Parsing complete!
      // Create a copy of Repository Document
      final RepositoryDocument docCopy = document.duplicate();

      // Open new input stream
      final InputStream is = ds.getInputStream();

      // Get new stream length
      final long newBinaryLength = ds.getBinaryLength();

      try {
        docCopy.setBinary(is, newBinaryLength);

        // Set up all metadata from Tika. We may want to run this through a
        // mapper eventually...
        for (String mName : metadata.keySet()) {
          String[] values = metadata.get(mName).toArray(new String[0]);

          // Only keep metadata if its name does not exceed 8k chars to avoid HTTP header error
          if (mName.length() < maxMetadataNameLength) {
            if (sp.lowerNames()) {
              final StringBuilder sb = new StringBuilder();
              for (int i = 0; i < mName.length(); i++) {
                char ch = mName.charAt(i);
                if (!Character.isLetterOrDigit(ch)) {
                  ch = '_';
                } else {
                  ch = Character.toLowerCase(ch);
                }
                sb.append(ch);
              }
              mName = sb.toString();
            }
            final String target = sp.getMapping(mName);
            if (target != null) {
              if (docCopy.getField(target) != null) {
                final String[] persistentValues = docCopy.getFieldAsStrings(target);
                values = ArrayUtils.addAll(persistentValues, values);
              }
              docCopy.addField(target, values);
            } else {
              if (sp.keepAllMetadata()) {
                if (docCopy.getField(mName) != null) {
                  final String[] persistentValues = docCopy.getFieldAsStrings(mName);
                  values = ArrayUtils.addAll(persistentValues, values);
                }
                docCopy.addField(mName, values);
              }
            }
          }
        }

        if (truncated) {
          removeField(docCopy, "truncated");
          docCopy.addField("truncated", "true");
        } else {
          removeField(docCopy, "truncated");
          docCopy.addField("truncated", "false");

        }

        if (resources_limit) {
          removeField(docCopy, "resources_limit");
          docCopy.addField("resources_limit", "true");
        } else {
          removeField(docCopy, "resources_limit");
          docCopy.addField("resources_limit", "false");

        }

        // Send new document downstream
        final int sendDocumentResultCode = activities.sendDocument(documentURI, docCopy);
        if (sendDocumentResultCode == 0) {
          return tikaServerResultCode;
        } else {
          return sendDocumentResultCode;
        }
      } finally {
        // This is really important to close the input stream in a finally statement as it will wait that the input stream is fully read (or closed) by down pipeline
        is.close();
      }
    } finally {
      if (ds != null) {
        ds.close();
      }
    }

  }

  private void skipMetadata(final JsonParser jParser) throws IOException {
    JsonToken token = jParser.nextToken();
    if (token == JsonToken.START_OBJECT) {
      while (token != JsonToken.END_OBJECT) {
        token = jParser.nextToken();
      }
    }
    if (token == JsonToken.START_ARRAY) {
      while (token != JsonToken.END_ARRAY) {
        token = jParser.nextToken();
      }
    }
  }

  private String getTikaExceptionDesc(final JsonParser jParser) throws IOException {
    final StringBuilder exceptionDescBuilder = new StringBuilder();
    JsonToken token = jParser.nextToken();
    if (token == JsonToken.START_ARRAY) {
      token = jParser.nextToken();
      while (token != JsonToken.END_ARRAY) {
        exceptionDescBuilder.append(jParser.getText());
        token = jParser.nextToken();
      }
    } else {
      exceptionDescBuilder.append(jParser.getText());
    }
    return exceptionDescBuilder.toString();
  }

  private void removeField(final RepositoryDocument document, final String fieldName) {
    final Iterator<String> fields = document.getFields();
    while (fields.hasNext()) {
      final String fieldname = fields.next();
      if (fieldname.equalsIgnoreCase(fieldName)) {
        document.removeField(fieldname);
        break;
      }
    }
  }

  /**
   * Extract extension from filename if possible
   *
   * @param filename
   * @return the filename's extension
   */
  private String getExtension(final String filename) {
    String extension = "";
    if (filename != null) {
      final int index = filename.lastIndexOf('.');
      if (index != -1) {
        extension = filename.substring(index + 1).toLowerCase(Locale.ROOT);
      }
    }
    return extension;
  }

  /**
   * Identify if the provided filename has an archive extension or not
   *
   * @param filename
   * @return true if the provided filename has an archive extension, false otherwise
   */
  private boolean isArchive(final String filename, final String mimeType) {
    final boolean filenameCheck = archiveExtensions.contains(getExtension(filename));
    final boolean mimeCheck = archiveMimes.contains(mimeType.toLowerCase(Locale.ROOT));
    if (filenameCheck || mimeCheck) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Obtain the name of the form check javascript method to call.
   *
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @return the name of the form check javascript method.
   */
  @Override
  public String getFormCheckJavascriptMethodName(final int connectionSequenceNumber) {
    return "s" + connectionSequenceNumber + "_checkSpecification";
  }

  /**
   * Obtain the name of the form presave check javascript method to call.
   *
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @return the name of the form presave check javascript method.
   */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(final int connectionSequenceNumber) {
    return "s" + connectionSequenceNumber + "_checkSpecificationForSave";
  }

  /**
   * Output the specification header section. This method is called in the head section of a job page which has selected a pipeline connection of the current type. Its purpose is to add the required
   * tabs to the list, and to output any javascript methods that might be needed by the job editing HTML.
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param os                       is the current pipeline specification for this connection.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param tabsArray                is an array of tab names. Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber, final List<String> tabsArray)
      throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "TikaExtractor.TikaServerTabName"));

    // Fill in the specification header map, using data from all tabs.
    fillInTikaSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
  }

  /**
   * Output the specification body section. This method is called in the body section of a job page which has selected a pipeline connection of the current type. Its purpose is to present the required
   * form elements for editing. The coder can presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags. The name of the
   * form is "editjob".
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param os                       is the current pipeline specification for this job.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param actualSequenceNumber     is the connection within the job that has currently been selected.
   * @param tabName                  is the current tab name.
   */
  @Override
  public void outputSpecificationBody(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber, final int actualSequenceNumber, final String tabName)
      throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInTikaSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_TIKASERVER_HTML, paramMap);
  }

  /**
   * Process a specification post. This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been posted. Its purpose is to
   * gather form information and modify the transformation specification accordingly. The name of the posted form is "editjob".
   *
   * @param variableContext          contains the post data, including binary file-upload information.
   * @param locale                   is the preferred local of the output.
   * @param os                       is the current pipeline specification for this job.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
   */
  @Override
  public String processSpecificationPost(final IPostParameters variableContext, final Locale locale, final Specification os, final int connectionSequenceNumber) throws ManifoldCFException {
    final String seqPrefix = "s" + connectionSequenceNumber + "_";

    String x;

    x = variableContext.getParameter(seqPrefix + "fieldmapping_count");
    if (x != null && x.length() > 0) {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount()) {
        final SpecificationNode node = os.getChild(i);
        if (node.getType().equals(TikaConfig.NODE_FIELDMAP) || node.getType().equals(TikaConfig.NODE_KEEPMETADATA) || node.getType().equals(TikaConfig.NODE_LOWERNAMES)
            || node.getType().equals(TikaConfig.NODE_WRITELIMIT)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      final int count = Integer.parseInt(x);
      i = 0;
      while (i < count) {
        final String prefix = seqPrefix + "fieldmapping_";
        final String suffix = "_" + Integer.toString(i);
        final String op = variableContext.getParameter(prefix + "op" + suffix);
        if (op == null || !op.equals("Delete")) {
          // Gather the fieldmap etc.
          final String source = variableContext.getParameter(prefix + "source" + suffix);
          String target = variableContext.getParameter(prefix + "target" + suffix);
          if (target == null) {
            target = "";
          }
          final SpecificationNode node = new SpecificationNode(TikaConfig.NODE_FIELDMAP);
          node.setAttribute(TikaConfig.ATTRIBUTE_SOURCE, source);
          node.setAttribute(TikaConfig.ATTRIBUTE_TARGET, target);
          os.addChild(os.getChildCount(), node);
        }
        i++;
      }

      final String addop = variableContext.getParameter(seqPrefix + "fieldmapping_op");
      if (addop != null && addop.equals("Add")) {
        final String source = variableContext.getParameter(seqPrefix + "fieldmapping_source");
        String target = variableContext.getParameter(seqPrefix + "fieldmapping_target");
        if (target == null) {
          target = "";
        }
        final SpecificationNode node = new SpecificationNode(TikaConfig.NODE_FIELDMAP);
        node.setAttribute(TikaConfig.ATTRIBUTE_SOURCE, source);
        node.setAttribute(TikaConfig.ATTRIBUTE_TARGET, target);
        os.addChild(os.getChildCount(), node);
      }

      // Gather the keep all metadata parameter to be the last one
      final SpecificationNode node = new SpecificationNode(TikaConfig.NODE_KEEPMETADATA);
      final String keepAll = variableContext.getParameter(seqPrefix + "keepallmetadata");
      if (keepAll != null) {
        node.setAttribute(TikaConfig.ATTRIBUTE_VALUE, keepAll);
      } else {
        node.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      }
      // Add the new keepallmetadata config parameter
      os.addChild(os.getChildCount(), node);

      final SpecificationNode node2 = new SpecificationNode(TikaConfig.NODE_LOWERNAMES);
      final String lower = variableContext.getParameter(seqPrefix + "lowernames");
      if (lower != null) {
        node2.setAttribute(TikaConfig.ATTRIBUTE_VALUE, lower);
      } else {
        node2.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      }
      os.addChild(os.getChildCount(), node2);

      final SpecificationNode node3 = new SpecificationNode(TikaConfig.NODE_WRITELIMIT);
      final String writeLimit = variableContext.getParameter(seqPrefix + "writelimit");
      if (writeLimit != null) {
        node3.setAttribute(TikaConfig.ATTRIBUTE_VALUE, writeLimit);
      } else {
        node3.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "");
      }
      os.addChild(os.getChildCount(), node3);

      final SpecificationNode maxMetadataValueNode = new SpecificationNode(TikaConfig.NODE_MAXMETADATAVALUELENGTH);
      final String maxMetadataValue = variableContext.getParameter(seqPrefix + "maxmetadatavaluelength");
      if (maxMetadataValue != null) {
        maxMetadataValueNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, maxMetadataValue);
      } else {
        maxMetadataValueNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, TikaConfig.MAXMETADATAVALUELENGTH_DEFAULT);
      }
      os.addChild(os.getChildCount(), maxMetadataValueNode);

      final SpecificationNode metadataLimitNode = new SpecificationNode(TikaConfig.NODE_TOTALMETADATALIMIT);
      final String metadataLimit = variableContext.getParameter(seqPrefix + "totalmetadatalimit");
      if (metadataLimit != null) {
        metadataLimitNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, metadataLimit);
      } else {
        metadataLimitNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, TikaConfig.TOTALMETADATALIMIT_DEFAULT);
      }
      os.addChild(os.getChildCount(), metadataLimitNode);

      final SpecificationNode node4 = new SpecificationNode(TikaConfig.NODE_EXTRACTARCHIVES);
      final String extractArch = variableContext.getParameter(seqPrefix + TikaConfig.NODE_EXTRACTARCHIVES);
      if (extractArch != null) {
        node4.setAttribute(TikaConfig.ATTRIBUTE_VALUE, extractArch);
      } else {
        node4.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      }
      os.addChild(os.getChildCount(), node4);

      final SpecificationNode node5 = new SpecificationNode(TikaConfig.NODE_MAXEMBEDDEDRESOURCES);
      final String maxEmbeddedResources = variableContext.getParameter(seqPrefix + "maxEmbeddedResources");
      if (maxEmbeddedResources != null) {
        node5.setAttribute(TikaConfig.ATTRIBUTE_VALUE, maxEmbeddedResources);
      } else {
        node5.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "0");
      }
      os.addChild(os.getChildCount(), node5);
    }

    return null;
  }

  /**
   * View specification. This method is called in the body section of a job's view page. Its purpose is to present the pipeline specification information to the user. The coder can presume that the
   * HTML that is output from this configuration will be within appropriate &lt;html&gt; and &lt;body&gt; tags.
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param os                       is the current pipeline specification for this job.
   */
  @Override
  public void viewSpecification(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber) throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInTikaSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, paramMap);

  }

  protected static void fillInTikaSpecificationMap(final Map<String, Object> paramMap, final Specification os) {
    // Prep for field mappings
    final List<Map<String, String>> fieldMappings = new ArrayList<>();
    String keepAllMetadataValue = "true";
    String lowernamesValue = "true";
    String writeLimitValue = "1000000"; // 1Mo by default
    String maxMetadataValueLength = "250000"; // 250ko by default
    String totalMetadataLimit = "500000"; // 500ko by default
    String extractArchives = "false";
    String maxEmbeddedResources = "";
    for (int i = 0; i < os.getChildCount(); i++) {
      final SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(TikaConfig.NODE_FIELDMAP)) {
        final String source = sn.getAttributeValue(TikaConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(TikaConfig.ATTRIBUTE_TARGET);
        String targetDisplay;
        if (target == null) {
          target = "";
          targetDisplay = "(remove)";
        } else {
          targetDisplay = target;
        }
        final Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("SOURCE", source);
        fieldMapping.put("TARGET", target);
        fieldMapping.put("TARGETDISPLAY", targetDisplay);
        fieldMappings.add(fieldMapping);
      } else if (sn.getType().equals(TikaConfig.NODE_KEEPMETADATA)) {
        keepAllMetadataValue = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_LOWERNAMES)) {
        lowernamesValue = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_WRITELIMIT)) {
        writeLimitValue = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_EXTRACTARCHIVES)) {
        extractArchives = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_MAXEMBEDDEDRESOURCES)) {
        maxEmbeddedResources = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_MAXMETADATAVALUELENGTH)) {
        maxMetadataValueLength = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      } else if (sn.getType().equals(TikaConfig.NODE_TOTALMETADATALIMIT)) {
        totalMetadataLimit = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
      }
    }
    paramMap.put("FIELDMAPPINGS", fieldMappings);
    paramMap.put("KEEPALLMETADATA", keepAllMetadataValue);
    paramMap.put("LOWERNAMES", lowernamesValue);
    paramMap.put("WRITELIMIT", writeLimitValue);
    paramMap.put("MAXMETADATAVALUELENGTH", maxMetadataValueLength);
    paramMap.put("TOTALMETADATALIMIT", totalMetadataLimit);
    paramMap.put("EXTRACTARCHIVES", extractArchives);
    paramMap.put("MAXEMBEDDEDRESOURCES", maxEmbeddedResources);
  }

  protected static int handleTikaServerRejects(final String reason) throws IOException, ManifoldCFException, ServiceInterruption {
    // MHL - what does Tika throw if it gets an IOException reading the stream??
    Logging.ingest.warn("Tika Server: Tika Server rejects: " + reason);
    return DOCUMENTSTATUS_REJECTED;
  }

  protected static int handleTikaServerError(final String description) throws IOException, ManifoldCFException, ServiceInterruption {
    // MHL - what does Tika throw if it gets an IOException reading the stream??
    Logging.ingest.warn("Tika Server: Tika Server error: " + description);
    return DOCUMENTSTATUS_REJECTED;
  }

  protected static int handleTikaServerException(final IOException e) throws IOException, ManifoldCFException, ServiceInterruption {
    // MHL - what does Tika throw if it gets an IOException reading the stream??
    Logging.ingest.warn("Tika Server: Tika exception extracting: " + e.getMessage(), e);
    return DOCUMENTSTATUS_REJECTED;
  }

  protected static int handleTikaServerException(final ParseException e) throws IOException, ManifoldCFException, ServiceInterruption {
    // MHL - what does Tika throw if it gets an IOException reading the stream??
    Logging.ingest.warn("Tika Server: Tika exception extracting: " + e.getMessage(), e);
    return DOCUMENTSTATUS_REJECTED;
  }

  protected static int handleIOException(final IOException e) throws ManifoldCFException {
    // IOException reading from our local storage...
    if (e instanceof InterruptedIOException) {
      throw new ManifoldCFException(e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    }
    throw new ManifoldCFException(e.getMessage(), e);
  }

  protected static interface DestinationStorage {
    /**
     * Get the output stream to write to. Caller should explicitly close this stream when done writing.
     */
    public OutputStream getOutputStream() throws ManifoldCFException;

    /**
     * Get new binary length.
     */
    public long getBinaryLength() throws ManifoldCFException;

    /**
     * Get the input stream to read from. Caller should explicitly close this stream when done reading.
     */
    public InputStream getInputStream() throws ManifoldCFException;

    /**
     * Close the object and clean up everything. This should be called when the data is no longer needed.
     */
    public void close() throws ManifoldCFException;
  }

  protected static class FileDestinationStorage implements DestinationStorage {
    protected final File outputFile;
    protected final OutputStream outputStream;

    public FileDestinationStorage() throws ManifoldCFException {
      File outputFile;
      OutputStream outputStream;
      try {
        outputFile = File.createTempFile("mcftika", "tmp");
        outputStream = new FileOutputStream(outputFile);
      } catch (final IOException e) {
        handleIOException(e);
        outputFile = null;
        outputStream = null;
      }
      this.outputFile = outputFile;
      this.outputStream = outputStream;
    }

    @Override
    public OutputStream getOutputStream() throws ManifoldCFException {
      return outputStream;
    }

    /**
     * Get new binary length.
     */
    @Override
    public long getBinaryLength() throws ManifoldCFException {
      return outputFile.length();
    }

    /**
     * Get the input stream to read from. Caller should explicitly close this stream when done reading.
     */
    @Override
    public InputStream getInputStream() throws ManifoldCFException {
      try {
        return new FileInputStream(outputFile);
      } catch (final IOException e) {
        handleIOException(e);
        return null;
      }
    }

    /**
     * Close the object and clean up everything. This should be called when the data is no longer needed.
     */
    @Override
    public void close() throws ManifoldCFException {
      outputFile.delete();
    }

  }

  protected static class MemoryDestinationStorage implements DestinationStorage {
    protected final ByteArrayOutputStream outputStream;

    public MemoryDestinationStorage(final int sizeHint) {
      outputStream = new ByteArrayOutputStream(sizeHint);
    }

    @Override
    public OutputStream getOutputStream() throws ManifoldCFException {
      return outputStream;
    }

    /**
     * Get new binary length.
     */
    @Override
    public long getBinaryLength() throws ManifoldCFException {
      return outputStream.size();
    }

    /**
     * Get the input stream to read from. Caller should explicitly close this stream when done reading.
     */
    @Override
    public InputStream getInputStream() throws ManifoldCFException {
      return new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * Close the object and clean up everything. This should be called when the data is no longer needed.
     */
    @Override
    public void close() throws ManifoldCFException {
    }

  }

  protected static class SpecPacker {

    private final Map<String, String> sourceTargets = new HashMap<>();
    private final boolean keepAllMetadata;
    private final boolean lowerNames;
    private final int writeLimit;
    private final boolean extractArchives;
    private final int maxEmbeddedResources;
    private final int maxMetadataValueLength;
    private final int totalMetadataLimit;

    public SpecPacker(final Specification os) {
      boolean keepAllMetadata = true;
      boolean lowerNames = false;
      boolean extractArchives = true;
      int writeLimit = TikaConfig.WRITELIMIT_DEFAULT;
      int maxEmbeddedResources = TikaConfig.MAXEMBEDDEDRESOURCES_DEFAULT;
      int maxMetadataValueLength = Integer.parseInt(TikaConfig.MAXMETADATAVALUELENGTH_DEFAULT);
      int totalMetadataLimit = Integer.parseInt(TikaConfig.TOTALMETADATALIMIT_DEFAULT);

      for (int i = 0; i < os.getChildCount(); i++) {
        final SpecificationNode sn = os.getChild(i);

        if (sn.getType().equals(TikaConfig.NODE_KEEPMETADATA)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          keepAllMetadata = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(TikaConfig.NODE_LOWERNAMES)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          lowerNames = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(TikaConfig.NODE_WRITELIMIT)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          if (value.length() == 0) {
            writeLimit = TikaConfig.WRITELIMIT_DEFAULT;
          } else {
            writeLimit = Integer.parseInt(value);
          }
        } else if (sn.getType().equals(TikaConfig.NODE_MAXMETADATAVALUELENGTH)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          if (value.length() == 0) {
            maxMetadataValueLength = Integer.parseInt(TikaConfig.MAXMETADATAVALUELENGTH_DEFAULT);
          } else {
            maxMetadataValueLength = Integer.parseInt(value);
          }
        } else if (sn.getType().equals(TikaConfig.NODE_TOTALMETADATALIMIT)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          if (value.length() == 0) {
            totalMetadataLimit = Integer.parseInt(TikaConfig.TOTALMETADATALIMIT_DEFAULT);
          } else {
            totalMetadataLimit = Integer.parseInt(value);
          }
        } else if (sn.getType().equals(TikaConfig.NODE_EXTRACTARCHIVES)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          extractArchives = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(TikaConfig.NODE_FIELDMAP)) {
          final String source = sn.getAttributeValue(TikaConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(TikaConfig.ATTRIBUTE_TARGET);

          if (target == null) {
            target = "";
          }
          sourceTargets.put(source, target);
        } else if (sn.getType().equals(TikaConfig.NODE_MAXEMBEDDEDRESOURCES)) {
          final String value = sn.getAttributeValue(TikaConfig.ATTRIBUTE_VALUE);
          if (value.length() == 0) {
            maxEmbeddedResources = TikaConfig.MAXEMBEDDEDRESOURCES_DEFAULT;
          } else {
            maxEmbeddedResources = Integer.parseInt(value);
          }
        }
      }
      this.keepAllMetadata = keepAllMetadata;
      this.lowerNames = lowerNames;
      this.writeLimit = writeLimit;
      if (maxMetadataValueLength > totalMetadataLimit) {
        maxMetadataValueLength = totalMetadataLimit;
      }
      this.maxMetadataValueLength = maxMetadataValueLength;
      this.totalMetadataLimit = totalMetadataLimit;
      this.extractArchives = extractArchives;
      this.maxEmbeddedResources = maxEmbeddedResources;
    }

    public String toPackedString() {
      final StringBuilder sb = new StringBuilder();
      int i;

      // Mappings
      final String[] sortArray = new String[sourceTargets.size()];
      i = 0;
      for (final String source : sourceTargets.keySet()) {
        sortArray[i++] = source;
      }
      java.util.Arrays.sort(sortArray);

      final List<String> packedMappings = new ArrayList<>();
      final String[] fixedList = new String[2];
      for (final String source : sortArray) {
        final String target = sourceTargets.get(source);
        final StringBuilder localBuffer = new StringBuilder();
        fixedList[0] = source;
        fixedList[1] = target;
        packFixedList(localBuffer, fixedList, ':');
        packedMappings.add(localBuffer.toString());
      }
      packList(sb, packedMappings, '+');

      // Keep all metadata
      if (keepAllMetadata) {
        sb.append('+');
      } else {
        sb.append('-');
      }
      if (lowerNames) {
        sb.append('+');
      } else {
        sb.append('-');
      }

      if (writeLimit != TikaConfig.WRITELIMIT_DEFAULT) {
        sb.append('+');
        sb.append(writeLimit);
      }

      if (maxMetadataValueLength != Integer.parseInt(TikaConfig.MAXMETADATAVALUELENGTH_DEFAULT)) {
        sb.append('+');
        sb.append(maxMetadataValueLength);
      }

      if (totalMetadataLimit != Integer.parseInt(TikaConfig.TOTALMETADATALIMIT_DEFAULT)) {
        sb.append('+');
        sb.append(totalMetadataLimit);
      }

      if (extractArchives) {
        sb.append('+');
      } else {
        sb.append('-');
      }

      if (maxEmbeddedResources != TikaConfig.MAXEMBEDDEDRESOURCES_DEFAULT) {
        sb.append('+');
        sb.append(maxEmbeddedResources);
      }

      return sb.toString();
    }

    public String getMapping(final String source) {
      return sourceTargets.get(source);
    }

    public boolean keepAllMetadata() {
      return keepAllMetadata;
    }

    public boolean lowerNames() {
      return lowerNames;
    }

    public int writeLimit() {
      return writeLimit;
    }

    public boolean extractArchives() {
      return extractArchives;
    }

    public int maxEmbeddedResources() {
      return maxEmbeddedResources;
    }

  }

}
