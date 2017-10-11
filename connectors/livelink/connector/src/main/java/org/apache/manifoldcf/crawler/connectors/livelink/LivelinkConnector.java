/* $Id: LivelinkConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.livelink;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;
import org.apache.manifoldcf.connectorcommon.common.XThreadOutputStream;
import org.apache.manifoldcf.connectorcommon.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.core.common.DateParser;

import org.apache.manifoldcf.livelink.*;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

import com.opentext.api.*;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.NameValuePair;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpStatus;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;


/** This is the Livelink implementation of the IRepositoryConnectr interface.
* The original Volant code forced there to be one livelink session per JVM, with
* lots of buggy synchronization present to try to enforce this.  This implementation
* is multi-session.  However, since it is possible that the Volant restriction was
* indeed needed, I have attempted to structure things to allow me to turn on
* single-session if needed.
*
* For livelink, the document identifiers are the object identifiers.
*
*/
public class LivelinkConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: LivelinkConnector.java 996524 2010-09-13 13:38:01Z kwright $";

  //Forward to the javascript to check the configuration parameters.
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

  //Forward to the HTML template to edit the configuration parameters.
  private static final String EDIT_SPECIFICATION_PATHS_HTML = "editSpecification_Paths.html";
  private static final String EDIT_SPECIFICATION_FILTERS_HTML = "editSpecification_Filters.html";
  private static final String EDIT_SPECIFICATION_SECURITY_HTML = "editSpecification_Security.html";
  private static final String EDIT_SPECIFICATION_METADATA_HTML = "editSpecification_Metadata.html";

  private static final String EDIT_CONFIGURATION_SERVER_HTML = "editConfiguration_Server.html";
  private static final String EDIT_CONFIGURATION_ACCESS_HTML = "editConfiguration_Access.html";
  private static final String EDIT_CONFIGURATION_VIEW_HTML = "editConfiguration_View.html";

  //Forward to the HTML template to view the configuration parameters.
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";
  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";

  //Tab name parameter for managing the view of the Web UI.
  private static final String TAB_NAME_PARAM = "TabName";

  // Activities we will report on
  private final static String ACTIVITY_SEED = "find documents";
  private final static String ACTIVITY_FETCH = "fetch document";

  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = GLOBAL_DENY_TOKEN;

  // A couple of very important points.
  // First, the canonical document identifier has the following form:
  // <D|F>[<volume_id>:]<object_id>
  // Second, the only LEGAL objects for a document identifier to describe
  // are folders, documents, and volume objects.  Project objects are NOT
  // allowed; they must be mapped to the appropriate volume object before
  // being returned to the crawler.

  // Metadata names for general metadata fields
  protected final static String GENERAL_NAME_FIELD = "general_name";
  protected final static String GENERAL_DESCRIPTION_FIELD = "general_description";
  protected final static String GENERAL_CREATIONDATE_FIELD = "general_creationdate";
  protected final static String GENERAL_MODIFYDATE_FIELD = "general_modifydate";
  protected final static String GENERAL_OWNER = "general_owner";
  protected final static String GENERAL_CREATOR = "general_creator";
  protected final static String GENERAL_MODIFIER = "general_modifier";
  protected final static String GENERAL_PARENTID = "general_parentid";
  
  // Signal that we have set up connection parameters properly
  private boolean hasSessionParameters = false;
  // Signal that we have set up a connection properly
  private boolean hasConnected = false;
  // Session expiration time
  private long expirationTime = -1L;
  // Idle session expiration interval
  private final static long expirationInterval = 300000L;

  // Data required for maintaining livelink connection
  private LAPI_DOCUMENTS LLDocs = null;
  private LAPI_ATTRIBUTES LLAttributes = null;
  private LAPI_USERS LLUsers = null;
  
  private LLSERVER llServer = null;
  private int LLENTWK_VOL;
  private int LLENTWK_ID;
  private int LLCATWK_VOL;
  private int LLCATWK_ID;

  // Parameter values we need
  private String serverProtocol = null;
  private String serverName = null;
  private int serverPort = -1;
  private String serverUsername = null;
  private String serverPassword = null;
  private String serverHTTPCgi = null;
  private String serverHTTPNTLMDomain = null;
  private String serverHTTPNTLMUsername = null;
  private String serverHTTPNTLMPassword = null;
  private IKeystoreManager serverHTTPSKeystore = null;

  private String ingestProtocol = null;
  private String ingestPort = null;
  private String ingestCgiPath = null;

  private String viewProtocol = null;
  private String viewServerName = null;
  private String viewPort = null;
  private String viewCgiPath = null;
  private String viewAction = null;

  private String ingestNtlmDomain = null;
  private String ingestNtlmUsername = null;
  private String ingestNtlmPassword = null;

  // SSL support for ingestion
  private IKeystoreManager ingestKeystoreManager = null;

  // Connection management
  private HttpClientConnectionManager connectionManager = null;
  private HttpClient httpClient = null;
  
  // Base path for viewing
  private String viewBasePath = null;

  // Ingestion port number
  private int ingestPortNumber = -1;

  // Activities list
  private static final String[] activitiesList = new String[]{ACTIVITY_SEED,ACTIVITY_FETCH};

  // Retry count.  This is so we can try to install some measure of sanity into situations where LAPI gets confused communicating to the server.
  // So, for some kinds of errors, we just retry for a while hoping it will go away.
  private static final int FAILURE_RETRY_COUNT = 10;

  // Current host name
  private static String currentHost = null;
  private static java.net.InetAddress currentAddr = null;
  static
  {
    // Find the current host name
    try
    {
      currentAddr = java.net.InetAddress.getLocalHost();

      // Get hostname
      currentHost = currentAddr.getHostName();
    }
    catch (UnknownHostException e)
    {
    }
  }


  /** Constructor.
  */
  public LivelinkConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    // Livelink is a chained hierarchy model
    return MODEL_CHAINED_ADD_CHANGE;
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // This is required by getBins()
    serverName = params.getParameter(LiveLinkParameters.serverName);
  }

  protected class GetSessionThread extends Thread
  {
    protected Throwable exception = null;

    public GetSessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Create the session
        llServer = new LLSERVER(!serverProtocol.equals("internal"),serverProtocol.equals("https"),
          serverName,serverPort,serverUsername,serverPassword,
          serverHTTPCgi,serverHTTPNTLMDomain,serverHTTPNTLMUsername,serverHTTPNTLMPassword,
          serverHTTPSKeystore);

        LLDocs = new LAPI_DOCUMENTS(llServer.getLLSession());
        LLAttributes = new LAPI_ATTRIBUTES(llServer.getLLSession());
        LLUsers = new LAPI_USERS(llServer.getLLSession());
        
        if (Logging.connectors.isDebugEnabled())
        {
          String passwordExists = (serverPassword!=null&&serverPassword.length()>0)?"password exists":"";
          Logging.connectors.debug("Livelink: Livelink Session: Server='"+serverName+"'; port='"+serverPort+"'; user name='"+serverUsername+"'; "+passwordExists);
        }
        LLValue entinfo = new LLValue().setAssoc();

        int status;
        status = LLDocs.AccessEnterpriseWS(entinfo);
        if (status == 0)
        {
          LLENTWK_ID = entinfo.toInteger("ID");
          LLENTWK_VOL = entinfo.toInteger("VolumeID");
        }
        else
          throw new ManifoldCFException("Error accessing enterprise workspace: "+status);

        entinfo = new LLValue().setAssoc();
        status = LLDocs.AccessCategoryWS(entinfo);
        if (status == 0)
        {
          LLCATWK_ID = entinfo.toInteger("ID");
          LLCATWK_VOL = entinfo.toInteger("VolumeID");
        }
        else
          throw new ManifoldCFException("Error accessing category workspace: "+status);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public void finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }
    
  }

  /** Get the bin name string for a document identifier.  The bin name describes the queue to which the
  * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  *@param documentIdentifier is the document identifier.
  *@return the bin name.
  */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    // This should return server name
    return new String[]{serverName};
  }

  protected HttpHost getHost()
  {
    return new HttpHost(llServer.getHost(),ingestPortNumber,ingestProtocol);
  }

  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (hasSessionParameters == false)
    {
      // Do the initial setup part (what used to be part of connect() itself)

      // Get the parameters
      ingestProtocol = params.getParameter(LiveLinkParameters.ingestProtocol);
      ingestPort = params.getParameter(LiveLinkParameters.ingestPort);
      ingestCgiPath = params.getParameter(LiveLinkParameters.ingestCgiPath);

      viewProtocol = params.getParameter(LiveLinkParameters.viewProtocol);
      viewServerName = params.getParameter(LiveLinkParameters.viewServerName);
      viewPort = params.getParameter(LiveLinkParameters.viewPort);
      viewCgiPath = params.getParameter(LiveLinkParameters.viewCgiPath);
      viewAction = params.getParameter(LiveLinkParameters.viewAction);

      ingestNtlmDomain = params.getParameter(LiveLinkParameters.ingestNtlmDomain);
      ingestNtlmUsername = params.getParameter(LiveLinkParameters.ingestNtlmUsername);
      ingestNtlmPassword = params.getObfuscatedParameter(LiveLinkParameters.ingestNtlmPassword);

      serverProtocol = params.getParameter(LiveLinkParameters.serverProtocol);
      String serverPortString = params.getParameter(LiveLinkParameters.serverPort);
      serverUsername = params.getParameter(LiveLinkParameters.serverUsername);
      serverPassword = params.getObfuscatedParameter(LiveLinkParameters.serverPassword);
      serverHTTPCgi = params.getParameter(LiveLinkParameters.serverHTTPCgiPath);
      serverHTTPNTLMDomain = params.getParameter(LiveLinkParameters.serverHTTPNTLMDomain);
      serverHTTPNTLMUsername = params.getParameter(LiveLinkParameters.serverHTTPNTLMUsername);
      serverHTTPNTLMPassword = params.getObfuscatedParameter(LiveLinkParameters.serverHTTPNTLMPassword);

      if (ingestProtocol == null || ingestProtocol.length() == 0)
        ingestProtocol = null;
      if (viewProtocol == null || viewProtocol.length() == 0)
      {
        if (ingestProtocol == null)
          viewProtocol = "http";
        else
          viewProtocol = ingestProtocol;
      }

      if (ingestPort == null || ingestPort.length() == 0)
      {
        if (ingestProtocol != null)
        {
          if (!ingestProtocol.equals("https"))
            ingestPort = "80";
          else
            ingestPort = "443";
        }
        else
          ingestPort = null;
      }

      if (viewPort == null || viewPort.length() == 0)
      {
        if (ingestProtocol == null || !viewProtocol.equals(ingestProtocol))
        {
          if (!viewProtocol.equals("https"))
            viewPort = "80";
          else
            viewPort = "443";
        }
        else
          viewPort = ingestPort;
      }

      if (ingestPort != null)
      {
        try
        {
          ingestPortNumber = Integer.parseInt(ingestPort);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Bad ingest port: "+e.getMessage(),e);
        }
      }

      String viewPortString;
      try
      {
        int portNumber = Integer.parseInt(viewPort);
        viewPortString = ":" + Integer.toString(portNumber);
        if (!viewProtocol.equals("https"))
        {
          if (portNumber == 80)
            viewPortString = "";
        }
        else
        {
          if (portNumber == 443)
            viewPortString = "";
        }
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Bad view port: "+e.getMessage(),e);
      }

      if (viewCgiPath == null || viewCgiPath.length() == 0)
        viewCgiPath = ingestCgiPath;

      if (ingestNtlmDomain != null && ingestNtlmDomain.length() == 0)
        ingestNtlmDomain = null;
      if (ingestNtlmDomain == null)
      {
        ingestNtlmUsername = null;
        ingestNtlmPassword = null;
      }
      else
      {
        if (ingestNtlmUsername == null || ingestNtlmUsername.length() == 0)
        {
          ingestNtlmUsername = serverUsername;
          if (ingestNtlmPassword == null || ingestNtlmPassword.length() == 0)
            ingestNtlmPassword = serverPassword;
        }
        else
        {
          if (ingestNtlmPassword == null)
            ingestNtlmPassword = "";
        }
      }

      // Set up ingest ssl if indicated
      String ingestKeystoreData = params.getParameter(LiveLinkParameters.ingestKeystore);
      if (ingestKeystoreData != null)
        ingestKeystoreManager = KeystoreManagerFactory.make("",ingestKeystoreData);


      // Server parameter processing

      if (serverProtocol == null || serverProtocol.length() == 0)
        serverProtocol = "internal";
      
      if (serverPortString == null)
        serverPort = 2099;
      else
        serverPort = new Integer(serverPortString).intValue();
      
      if (serverHTTPNTLMDomain != null && serverHTTPNTLMDomain.length() == 0)
        serverHTTPNTLMDomain = null;
      if (serverHTTPNTLMUsername == null || serverHTTPNTLMUsername.length() == 0)
      {
        serverHTTPNTLMUsername = null;
        serverHTTPNTLMPassword = null;
      }
      
      // Set up server ssl if indicated
      String serverHTTPSKeystoreData = params.getParameter(LiveLinkParameters.serverHTTPSKeystore);
      if (serverHTTPSKeystoreData != null)
        serverHTTPSKeystore = KeystoreManagerFactory.make("",serverHTTPSKeystoreData);

      // View parameters
      if (viewServerName == null || viewServerName.length() == 0)
        viewServerName = serverName;

      viewBasePath = viewProtocol+"://"+viewServerName+viewPortString+viewCgiPath;

      hasSessionParameters = true;
    }
  }
  
  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
  {
    getSessionParameters();
    if (hasConnected == false)
    {
      int socketTimeout = 900000;
      int connectionTimeout = 300000;

      // Set up ingest ssl if indicated
      SSLConnectionSocketFactory myFactory = null;
      if (ingestKeystoreManager != null)
      {
        myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(ingestKeystoreManager.getSecureSocketFactory(), connectionTimeout),
          NoopHostnameVerifier.INSTANCE);
      }
      else
      {
        myFactory = SSLConnectionSocketFactory.getSocketFactory();
      }

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

      // Set up authentication to use
      if (ingestNtlmDomain != null)
      {
        credentialsProvider.setCredentials(AuthScope.ANY,
          new NTCredentials(ingestNtlmUsername,ingestNtlmPassword,currentHost,ingestNtlmDomain));
      }

      HttpClientBuilder builder = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(socketTimeout)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout)
          .build())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .setRedirectStrategy(new LaxRedirectStrategy());

      httpClient = builder.build();

      // System.out.println("Connection server object = "+llServer.toString());

      // Establish the actual connection
      int sanityRetryCount = FAILURE_RETRY_COUNT;
      while (true)
      {
        GetSessionThread t = new GetSessionThread();
        try
        {
          t.start();
	  t.finishUp();
          hasConnected = true;
          break;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RuntimeException e2)
        {
          sanityRetryCount = handleLivelinkRuntimeException(e2,sanityRetryCount,true);
        }
      }
    }
    expirationTime = System.currentTimeMillis() + expirationInterval;
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!


  protected static int executeMethodViaThread(HttpClient client, HttpRequestBase executeMethod)
    throws InterruptedException, HttpException, IOException
  {
    ExecuteMethodThread t = new ExecuteMethodThread(client,executeMethod);
    t.start();
    try
    {
      return t.getResponseCode();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw e;
    }
    finally
    {
      t.abort();
      t.finishUp();
    }
  }

  /** Check status of connection.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      // Destroy saved session setup and repeat it
      hasConnected = false;
      getSession();

      // Now, set up trial of ingestion connection
      if (ingestProtocol != null)
      {
        String contextMsg = "for document access";
        String ingestHttpAddress = ingestCgiPath;

        HttpClient client = getInitializedClient(contextMsg);
        HttpGet method = new HttpGet(getHost().toURI() + ingestHttpAddress);
        method.setHeader(new BasicHeader("Accept","*/*"));
        try
        {
          int statusCode = executeMethodViaThread(client,method);
          switch (statusCode)
          {
          case 502:
            return "Fetch test had transient 502 error response";

          case HttpStatus.SC_UNAUTHORIZED:
            return "Fetch test returned UNAUTHORIZED (401) response; check the security credentials and configuration";

          case HttpStatus.SC_OK:
            return super.check();

          default:
            return "Fetch test returned an unexpected response code of "+Integer.toString(statusCode);
          }
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (java.net.SocketTimeoutException e)
        {
          return "Fetch test timed out reading from the Livelink HTTP Server: "+e.getMessage();
        }
        catch (java.net.SocketException e)
        {
          return "Fetch test received a socket error reading from Livelink HTTP Server: "+e.getMessage();
        }
        catch (javax.net.ssl.SSLHandshakeException e)
        {
          return "Fetch test was unable to set up a SSL connection to Livelink HTTP Server: "+e.getMessage();
        }
        catch (ConnectTimeoutException e)
        {
          return "Fetch test connection timed out reading from Livelink HTTP Server: "+e.getMessage();
        }
        catch (InterruptedIOException e)
        {
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (HttpException e)
        {
          return "Fetch test had an HTTP exception: "+e.getMessage();
        }
        catch (IOException e)
        {
          return "Fetch test had an IO failure: "+e.getMessage();
        }
      }
      else
        return super.check();
    }
    catch (ServiceInterruption e)
    {
      return "Transient error: "+e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        throw e;
      return "Error: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    if (!hasConnected)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= expirationTime)
    {
      hasConnected = false;
      expirationTime = -1L;

      // Shutdown livelink connection
      if (llServer != null)
      {
        llServer.disconnect();
        llServer = null;
      }
      
      // Shutdown pool
      if (connectionManager != null)
      {
        connectionManager.shutdown();
        connectionManager = null;
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
    return hasConnected;
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    hasSessionParameters = false;
    hasConnected = false;
    expirationTime = -1L;
    if (llServer != null)
    {
      llServer.disconnect();
      llServer = null;
    }
    LLDocs = null;
    LLAttributes = null;
    ingestKeystoreManager = null;
    ingestPortNumber = -1;

    serverProtocol = null;
    serverName = null;
    serverPort = -1;
    serverUsername = null;
    serverPassword = null;
    serverHTTPCgi = null;
    serverHTTPNTLMDomain = null;
    serverHTTPNTLMUsername = null;
    serverHTTPNTLMPassword = null;
    serverHTTPSKeystore = null;

    ingestPort = null;
    ingestProtocol = null;
    ingestCgiPath = null;

    viewPort = null;
    viewServerName = null;
    viewProtocol = null;
    viewCgiPath = null;

    viewBasePath = null;

    ingestNtlmDomain = null;
    ingestNtlmUsername = null;
    ingestNtlmPassword = null;

    if (connectionManager != null)
    {
      connectionManager.shutdown();
      connectionManager = null;
    }

    super.disconnect();
  }

  /** List the activities we might report on.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Convert a document identifier to a relative URI to read data from.  This is not the search URI; that's constructed
  * by a different method.
  *@param documentIdentifier is the document identifier.
  *@return the relative document uri.
  */
  protected String convertToIngestURI(String documentIdentifier)
    throws ManifoldCFException
  {
    // The document identifier is the string form of the object ID for this connector.
    if (!documentIdentifier.startsWith("D"))
      return null;
    int colonPosition = documentIdentifier.indexOf(":",1);
    if (colonPosition == -1)
      return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(1)+"&objAction=download";
    else
      return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(colonPosition+1)+"&objAction=download";
  }

  /** Convert a document identifier to a URI to view.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.  It must therefore
  * be a unique way of describing the document.
  *@param documentIdentifier is the document identifier.
  *@return the document uri.
  */
  protected String convertToViewURI(String documentIdentifier)
    throws ManifoldCFException
  {
    // The document identifier is the string form of the object ID for this connector.
    if (!documentIdentifier.startsWith("D"))
      return null;
    String objectID = null;
    int colonPosition = documentIdentifier.indexOf(":",1);
    if (colonPosition == -1)
      objectID = documentIdentifier.substring(1);
    else
      objectID = documentIdentifier.substring(colonPosition+1);
    String viewURL = null;
    switch(viewAction)
    {
      case "download":
        viewURL =  viewBasePath+"?func=ll&objAction=download&objID=" + objectID;
      break;
      case "open":
        viewURL = viewBasePath+"/open/" + objectID;
      break;
      case "overview":
        viewURL = viewBasePath+"?func=ll&objAction=overview&objID=" + objectID;
      break;
      default:
        viewURL = viewBasePath+"?func=ll&objAction=download&objID=" + objectID;
    }
    return viewURL;
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  @Override
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException
  {
    if (command.equals("workspaces"))
    {
      try
      {
        String[] workspaces = getWorkspaceNames();
        int i = 0;
        while (i < workspaces.length)
        {
          String workspace = workspaces[i++];
          ConfigurationNode node = new ConfigurationNode("workspace");
          node.setValue(workspace);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("folders/"))
    {
      String path = command.substring("folders/".length());
      
      try
      {
        String[] folders = getChildFolderNames(path);
        int i = 0;
        while (i < folders.length)
        {
          String folder = folders[i++];
          ConfigurationNode node = new ConfigurationNode("folder");
          node.setValue(folder);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("categories/"))
    {
      String path = command.substring("categories/".length());

      try
      {
        String[] categories = getChildCategoryNames(path);
        int i = 0;
        while (i < categories.length)
        {
          String category = categories[i++];
          ConfigurationNode node = new ConfigurationNode("category");
          node.setValue(category);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }

    }
    else if (command.startsWith("categoryattributes/"))
    {
      String path = command.substring("categoryattributes/".length());

      try
      {
        String[] attributes = getCategoryAttributes(path);
        int i = 0;
        while (i < attributes.length)
        {
          String attribute = attributes[i++];
          ConfigurationNode node = new ConfigurationNode("attribute");
          node.setValue(attribute);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else
      return super.requestInfo(output,command);
    return true;
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
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    LivelinkContext llc = new LivelinkContext();
    
    // First, grab the root LLValue
    ObjectInformation rootValue = llc.getObjectInformation(LLENTWK_VOL,LLENTWK_ID);
    if (!rootValue.exists())
    {
      // If we get here, it HAS to be a bad network/transient problem.
      Logging.connectors.warn("Livelink: Could not look up root workspace object during seeding!  Retrying -");
      throw new ServiceInterruption("Service interruption during seeding",new ManifoldCFException("Could not looking root workspace object during seeding"),System.currentTimeMillis()+60000L,
        System.currentTimeMillis()+600000L,-1,true);
    }

    // Walk the specification for the "startpoint" types.  Amalgamate these into a list of strings.
    // Presume that all roots are startpoint nodes
    boolean doUserWorkspaces = false;
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode n = spec.getChild(i);
      if (n.getType().equals("startpoint"))
      {
        // The id returned is simply the node path, which can't be messed up
        long beginTime = System.currentTimeMillis();
        String path = n.getAttributeValue("path");
        VolumeAndId vaf = rootValue.getPathId(path);
        if (vaf != null)
        {
          activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
            path,"OK",null,null);

          String newID = "F" + new Integer(vaf.getVolumeID()).toString()+":"+ new Integer(vaf.getPathId()).toString();
          activities.addSeedDocument(newID);
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Seed = '"+newID+"'");
        }
        else
        {
          activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
            path,"NOT FOUND",null,null);
        }
      }
      else if (n.getType().equals("userworkspace"))
      {
        String value = n.getAttributeValue("value");
        if (value != null && value.equals("true"))
          doUserWorkspaces = true;
        else if (value != null && value.equals("false"))
          doUserWorkspaces = false;
      }
      
      if (doUserWorkspaces)
      {
        // Do ListUsers and enumerate the values.
        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          ListUsersThread t = new ListUsersThread();
          try
          {
            t.start();
	    LLValue childrenDocs;
	    try
	    {
	      childrenDocs = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
	    }

            int size = 0;

            if (childrenDocs.isRecord())
              size = 1;
            if (childrenDocs.isTable())
              size = childrenDocs.size();

            // Do the scan
            for (int j = 0; j < size; j++)
            {
              int childID = childrenDocs.toInteger(j, "ID");
              
              // Skip admin user
              if (childID == 1000 || childID == 1001)
                continue;
              
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Livelink: Found a user: ID="+Integer.toString(childID));

              activities.addSeedDocument("F0:"+Integer.toString(childID));
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
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
    throws ManifoldCFException, ServiceInterruption
  {
    
    // Initialize a "livelink context", to minimize the number of objects we have to fetch
    LivelinkContext llc = new LivelinkContext();
    // Initialize the table of catid's.
    // Keeping this around will allow us to benefit from batching of documents.
    MetadataDescription desc = new MetadataDescription(llc);
    
    // First, process the spec to get the string we tack on
    SystemMetadataDescription sDesc = new SystemMetadataDescription(llc,spec);


    // Read the forced acls.  A null return indicates that security is disabled!!!
    // A zero-length return indicates that the native acls should be used.
    // All of this is germane to how we ingest the document, so we need to note it in
    // the version string completely.
    String[] acls = sDesc.getAcls();
    // Sort it, in case it is needed.
    if (acls != null)
      java.util.Arrays.sort(acls);

    // Prepare the specified metadata
    String metadataString = null;
    String[] specifiedMetadataAttributes = null;
    CategoryPathAccumulator catAccum = null;
    if (!sDesc.includeAllMetadata())
    {
      StringBuilder sb = new StringBuilder();
      specifiedMetadataAttributes = sDesc.getMetadataAttributes();
      // Sort!
      java.util.Arrays.sort(specifiedMetadataAttributes);
      // Build the metadata string piece now
      packList(sb,specifiedMetadataAttributes,'+');
      metadataString = sb.toString();
    }
    else
      catAccum = new CategoryPathAccumulator(llc);

    // Calculate the part of the version string that comes from path name and mapping.
    // This starts with = since ; is used by another optional component (the forced acls)
    String pathNameAttributeVersion;
    StringBuilder sb2 = new StringBuilder();
    if (sDesc.getPathAttributeName() != null)
      sb2.append("=").append(sDesc.getPathAttributeName()).append(":").append(sDesc.getPathSeparator()).append(":").append(sDesc.getMatchMapString());
    pathNameAttributeVersion = sb2.toString();

    // Since the identifier indicates it is a directory, then queue up all the current children which pass the filter.
    String filterString = sDesc.getFilterString();

    for (String documentIdentifier : documentIdentifiers)
    {
      // Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
      activities.checkJobStillActive();

      // Read the document or folder metadata, which includes the ModifyDate
      String docID = documentIdentifier;
      
      boolean isFolder = docID.startsWith("F");

      int colonPos = docID.indexOf(":",1);

      int objID;
      int vol;

      if (colonPos == -1)
      {
        objID = new Integer(docID.substring(1)).intValue();
        vol = LLENTWK_VOL;
      }
      else
      {
        objID = new Integer(docID.substring(colonPos+1)).intValue();
        vol = new Integer(docID.substring(1,colonPos)).intValue();
      }

      getSession();
      ObjectInformation value = llc.getObjectInformation(vol,objID);
      if (!value.exists())
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(objID)+" has no information - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }
        
      // Make sure we have permission to see the object's contents
      int permissions = value.getPermissions().intValue();
      if ((permissions & LAPI_DOCUMENTS.PERM_SEECONTENTS) == 0)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Crawl user cannot see contents of object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }
        
        
      Date dt = value.getModifyDate();
      // The rights don't change when the object changes, so we have to include those too.
      int[] rights = getObjectRights(vol,objID);
      if (rights == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Could not get rights for object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }
        
      // We were able to get rights, so object still exists.
          
      // Changed folder versioning for MCF 2.0
      if (isFolder)
      {
        // === Livelink folder ===
        // I'm still not sure if Livelink folder modified dates are one-level or hierarchical.
        // The code below assumes one-level only, so we always scan folders and there's no versioning
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));

        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          ListObjectsThread t = new ListObjectsThread(vol,objID,filterString);
          try
          {
            t.start();
            LLValue childrenDocs;
            try
            {
              childrenDocs = t.finishUp();
            }
            catch (ManifoldCFException e)
            {
              sanityRetryCount = assessRetry(sanityRetryCount,e);
              continue;
            }

            int size = 0;
            
            if (childrenDocs.isRecord())
              size = 1;
            if (childrenDocs.isTable())
              size = childrenDocs.size();

            // System.out.println("Total child count = "+Integer.toString(size));
            // Do the scan
            for (int j = 0; j < size; j++)
            {
              int childID = childrenDocs.toInteger(j, "ID");

              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Livelink: Found a child of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : ID="+Integer.toString(childID));

              int subtype = childrenDocs.toInteger(j, "SubType");
              boolean childIsFolder = (subtype == LAPI_DOCUMENTS.FOLDERSUBTYPE || subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE ||
                subtype == LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE);

              // If it's a folder, we just let it through for now
              if (!childIsFolder && checkInclude(childrenDocs.toString(j,"Name") + "." + childrenDocs.toString(j,"FileType"), spec) == false)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" was excluded by inclusion criteria");
                continue;
              }

              if (childIsFolder)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a folder, project, or compound document; adding a reference");
                if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
                {
                  // If we pick up a project object, we need to describe the volume object (which
                  // will be the root of all documents beneath)
                  activities.addDocumentReference("F"+new Integer(childID).toString()+":"+new Integer(-childID).toString());
                }
                else
                  activities.addDocumentReference("F"+new Integer(vol).toString()+":"+new Integer(childID).toString());
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a simple document; adding a reference");

                activities.addDocumentReference("D"+new Integer(vol).toString()+":"+new Integer(childID).toString());
              }

            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Done processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));
      }
      else
      {
        // === Livelink document ===
              
        // The version string includes the following:
        // 1) The modify date for the document
        // 2) The rights for the document, ordered (which can change without changing the ModifyDate field)
        // 3) The requested metadata fields (category and attribute, ordered) for the document
        //
        // The document identifiers are object id's.
  
        StringBuilder sb = new StringBuilder();

        String[] categoryPaths;
        if (sDesc.includeAllMetadata())
        {
          // Find all the metadata associated with this object, and then
          // find the set of category pathnames that correspond to it.
          int[] catIDs = getObjectCategoryIDs(vol,objID);
          categoryPaths = catAccum.getCategoryPathsAttributeNames(catIDs);
          // Sort!
          java.util.Arrays.sort(categoryPaths);
          // Build the metadata string piece now
          packList(sb,categoryPaths,'+');
        }
        else
        {
          categoryPaths = specifiedMetadataAttributes;
          sb.append(metadataString);
        }
              
        String[] actualAcls;
        String[] denyAcls;
              
        String denyAcl;
        if (acls != null && acls.length == 0)
        {
          // No forced acls.  Read the actual acls from livelink, as a set of rights.
          // We need also to add in support for the special rights objects.  These are:
          // -1: RIGHT_WORLD
          // -2: RIGHT_SYSTEM
          // -3: RIGHT_OWNER
          // -4: RIGHT_GROUP
          //
          // RIGHT_WORLD means guest access.
          // RIGHT_SYSTEM is "Public Access".
          // RIGHT_OWNER is access by the owner of the object.
          // RIGHT_GROUP is access by a member of the base group containing the owner
          //
          // These objects are returned by the GetObjectRights() call made above, and NOT
          // returned by LLUser.ListObjects().  We have to figure out how to map these to
          // things that are
          // the equivalent of acls.

          actualAcls = lookupTokens(rights, value);
          java.util.Arrays.sort(actualAcls);
          // If security is on, no deny acl is needed for the local authority, since the repository does not support "deny".  But this was added
          // to be really really really sure.
          denyAcl = defaultAuthorityDenyToken;
              
        }
        else if (acls != null && acls.length > 0)
        {
          // Forced acls
          actualAcls = acls;
          denyAcl = defaultAuthorityDenyToken;
        }
        else
        {
          // Security is OFF
          actualAcls = acls;
          denyAcl = null;
        }

        // Now encode the acls.  If null, we write a special value.
        if (actualAcls == null)
        {
          sb.append('-');
          denyAcls = null;
        }
        else
        {
          sb.append('+');
          packList(sb,actualAcls,'+');
          // This was added on 4/21/2008 to support forced acls working with the global default authority.
          pack(sb,denyAcl,'+');
          denyAcls = new String[]{denyAcl};
        }

        // The date does not need to be parseable
        sb.append(new Long(dt.getTime()).toString());

        // PathNameAttributeVersion comes completely from the spec, so we don't
        // have to worry about it changing.  No need, therefore, to parse it during
        // processDocuments.
        sb.append("=").append(pathNameAttributeVersion);
          
        // Tack on ingestCgiPath, to insulate us against changes to the repository connection setup.  Added 9/7/07.
        sb.append("_").append(viewBasePath);

        String versionString = sb.toString();
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Successfully calculated version string for document "+Integer.toString(vol)+":"+Integer.toString(objID)+" : '"+versionString+"'");
              
        if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
          continue;
        
        // Index the document
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
        if (!checkIngest(llc,objID,spec))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Decided not to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID)+" - Did not match ingestion criteria");
          activities.noDocument(documentIdentifier,versionString);
          continue;
        }

        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Decided to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID));

        // Grab the access tokens for this file from the version string, inside ingest method.
        ingestFromLiveLink(llc,documentIdentifier,versionString,actualAcls,denyAcls,categoryPaths,activities,desc,sDesc);
          
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Done processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
      }
    }
  }

  protected class ListObjectsThread extends Thread
  {
    protected final int vol;
    protected final int objID;
    protected final String filterString;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public ListObjectsThread(int vol, int objID, String filterString)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.objID = objID;
      this.filterString = filterString;
    }

    public void run()
    {
      try
      {
        LLValue childrenDocs = new LLValue();
        int status = LLDocs.ListObjects(vol, objID, null, filterString, LAPI_DOCUMENTS.PERM_SEECONTENTS, childrenDocs);
        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving contents of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : Status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = childrenDocs;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    // Intrinsically, Livelink doesn't batch well.  Multiple chunks have no advantage over one-at-a-time requests,
    // since apparently the Livelink API does not support multiples.  HOWEVER - when metadata is considered,
    // it becomes worthwhile, because we will be able to do what is needed to look up the correct CATID node
    // only once per n requests!  So it's a tradeoff between the advantage gained by threading, and the
    // savings gained by CATID lookup.
    // Note that at Shell, the fact that the network hiccups a lot makes it better to choose a smaller value.
    return 6;
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.Server"));
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.DocumentAccess"));
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.DocumentView"));
    
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_JS, null, true);
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {    
    Map<String, Object> velocityContext = new HashMap<>();
    velocityContext.put(TAB_NAME_PARAM,tabName);

    fillInServerTab(velocityContext, out, parameters);
    fillInDocumentAccessTab(velocityContext, out, parameters);
    fillInDocumentViewTab(velocityContext, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_SERVER_HTML, velocityContext);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_ACCESS_HTML, velocityContext);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_VIEW_HTML, velocityContext);
  }

  /** Fill in Server tab */
  protected static void fillInServerTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    // LAPI parameters
    String serverProtocol = parameters.getParameter(LiveLinkParameters.serverProtocol);
    if (serverProtocol == null)
      serverProtocol = "internal";
    String serverName = parameters.getParameter(LiveLinkParameters.serverName);
    if (serverName == null)
      serverName = "localhost";
    String serverPort = parameters.getParameter(LiveLinkParameters.serverPort);
    if (serverPort == null)
      serverPort = "2099";
    String serverUserName = parameters.getParameter(LiveLinkParameters.serverUsername);
    if(serverUserName == null)
      serverUserName = "";
    String serverPassword = parameters.getObfuscatedParameter(LiveLinkParameters.serverPassword);
    if (serverPassword == null)
      serverPassword = "";
    else
      serverPassword = out.mapPasswordToKey(serverPassword);
    String serverHTTPCgiPath = parameters.getParameter(LiveLinkParameters.serverHTTPCgiPath);
    if (serverHTTPCgiPath == null)
      serverHTTPCgiPath = "/livelink/livelink.exe";
    String serverHTTPNTLMDomain = parameters.getParameter(LiveLinkParameters.serverHTTPNTLMDomain);
    if(serverHTTPNTLMDomain == null)
      serverHTTPNTLMDomain = "";
    String serverHTTPNTLMUserName = parameters.getParameter(LiveLinkParameters.serverHTTPNTLMUsername);
    if(serverHTTPNTLMUserName == null)
      serverHTTPNTLMUserName = "";
    String serverHTTPNTLMPassword = parameters.getObfuscatedParameter(LiveLinkParameters.serverHTTPNTLMPassword);
    if (serverHTTPNTLMPassword == null)
      serverHTTPNTLMPassword = "";
    else
      serverHTTPNTLMPassword = out.mapPasswordToKey(serverHTTPNTLMPassword);
    String serverHTTPSKeystore = parameters.getParameter(LiveLinkParameters.serverHTTPSKeystore);

    IKeystoreManager localServerHTTPSKeystore;
    Map<String,String> serverCertificatesMap = null;
    String message = null;

    try {
      if (serverHTTPSKeystore == null)
        localServerHTTPSKeystore = KeystoreManagerFactory.make("");
      else
        localServerHTTPSKeystore = KeystoreManagerFactory.make("",serverHTTPSKeystore);

      // List the individual certificates in the store, with a delete button for each
      String[] contents = localServerHTTPSKeystore.getContents();
      if (contents.length > 0)
      {
        serverCertificatesMap = new HashMap<>();
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localServerHTTPSKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          serverCertificatesMap.put(alias, description);
          i++;
        }
      }
    } catch (ManifoldCFException e) {
      message = e.getMessage();
      Logging.connectors.warn(e);
    }

    velocityContext.put("SERVERPROTOCOL",serverProtocol);
    velocityContext.put("SERVERNAME",serverName);
    velocityContext.put("SERVERPORT",serverPort);
    velocityContext.put("SERVERUSERNAME",serverUserName);
    velocityContext.put("SERVERPASSWORD",serverPassword);
    velocityContext.put("SERVERHTTPCGIPATH",serverHTTPCgiPath);
    velocityContext.put("SERVERHTTPNTLMDOMAIN",serverHTTPNTLMDomain);
    velocityContext.put("SERVERHTTPNTLMUSERNAME",serverHTTPNTLMUserName);
    velocityContext.put("SERVERHTTPNTLMPASSWORD",serverHTTPNTLMPassword);
    if(serverHTTPSKeystore != null)
      velocityContext.put("SERVERHTTPSKEYSTORE",serverHTTPSKeystore);
    if(serverCertificatesMap != null)
    velocityContext.put("SERVERCERTIFICATESMAP", serverCertificatesMap);
    if(message != null)
      velocityContext.put("MESSAGE", message);
  }

  /** Fill in Document Access tab */
  protected static void fillInDocumentAccessTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    // Document access parameters
    String ingestProtocol = parameters.getParameter(LiveLinkParameters.ingestProtocol);
    if(ingestProtocol == null)
      ingestProtocol = "";
    String ingestPort = parameters.getParameter(LiveLinkParameters.ingestPort);
    if(ingestPort == null)
      ingestPort = "";
    String ingestCgiPath = parameters.getParameter(LiveLinkParameters.ingestCgiPath);
    if(ingestCgiPath == null)
      ingestCgiPath = "";
    String ingestNtlmUsername = parameters.getParameter(LiveLinkParameters.ingestNtlmUsername);
    if(ingestNtlmUsername == null)
      ingestNtlmUsername = "";
    String ingestNtlmPassword = parameters.getObfuscatedParameter(LiveLinkParameters.ingestNtlmPassword);
    if (ingestNtlmPassword == null)
      ingestNtlmPassword = "";
    else
      ingestNtlmPassword = out.mapPasswordToKey(ingestNtlmPassword);
    String ingestNtlmDomain = parameters.getParameter(LiveLinkParameters.ingestNtlmDomain);
    if(ingestNtlmDomain == null)
      ingestNtlmDomain = "";
    String ingestKeystore = parameters.getParameter(LiveLinkParameters.ingestKeystore);

    IKeystoreManager localIngestKeystore;
    Map<String,String> ingestCertificatesMap = null;
    String message = null;

    try{
      if (ingestKeystore == null)
        localIngestKeystore = KeystoreManagerFactory.make("");
      else
        localIngestKeystore = KeystoreManagerFactory.make("",ingestKeystore);

      String[] contents = localIngestKeystore.getContents();
      if (contents.length > 0)
      {
        ingestCertificatesMap = new HashMap<>();
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localIngestKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          ingestCertificatesMap.put(alias,description);
          i++;
        }
      }

    } catch (ManifoldCFException e) {
      message = e.getMessage();
      Logging.connectors.warn(e);
    }

    velocityContext.put("INGESTPROTOCOL",ingestProtocol);
    velocityContext.put("INGESTPORT",ingestPort);
    velocityContext.put("INGESTCGIPATH",ingestCgiPath);
    velocityContext.put("INGESTNTLMUSERNAME",ingestNtlmUsername);
    velocityContext.put("INGESTNTLMPASSWORD",ingestNtlmPassword);
    velocityContext.put("INGESTNTLMDOMAIN",ingestNtlmDomain);
    velocityContext.put("INGESTKEYSTORE",ingestKeystore);
    if(ingestCertificatesMap != null)
      velocityContext.put("INGESTCERTIFICATESMAP", ingestCertificatesMap);
    if(message != null)
      velocityContext.put("MESSAGE", message);
  }

  /** Fill in Document View tab */
  protected static void fillInDocumentViewTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    // Document view parameters
    String viewProtocol = parameters.getParameter(LiveLinkParameters.viewProtocol);
    if (viewProtocol == null)
      viewProtocol = "http";

    String viewServerName = parameters.getParameter(LiveLinkParameters.viewServerName);
    if(viewServerName == null)
      viewServerName = "";
    String viewPort = parameters.getParameter(LiveLinkParameters.viewPort);
    if(viewPort == null)
      viewPort = "";
    String viewCgiPath = parameters.getParameter(LiveLinkParameters.viewCgiPath);
    if (viewCgiPath == null)
      viewCgiPath = "/livelink/livelink.exe";
    String viewAction = parameters.getParameter(LiveLinkParameters.viewAction);
    if (viewAction == null)
      viewAction = "download";

    velocityContext.put("VIEWPROTOCOL",viewProtocol);
    velocityContext.put("VIEWSERVERNAME",viewServerName);
    velocityContext.put("VIEWPORT",viewPort);
    velocityContext.put("VIEWCGIPATH",viewCgiPath);
    velocityContext.put("VIEWACTION",viewAction);
  }  
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    // View parameters
    String viewProtocol = variableContext.getParameter("viewprotocol");
    if (viewProtocol != null)
      parameters.setParameter(LiveLinkParameters.viewProtocol,viewProtocol);
    String viewServerName = variableContext.getParameter("viewservername");
    if (viewServerName != null)
      parameters.setParameter(LiveLinkParameters.viewServerName,viewServerName);
    String viewPort = variableContext.getParameter("viewport");
    if (viewPort != null)
      parameters.setParameter(LiveLinkParameters.viewPort,viewPort);
    String viewCgiPath = variableContext.getParameter("viewcgipath");
    if (viewCgiPath != null)
      parameters.setParameter(LiveLinkParameters.viewCgiPath,viewCgiPath);
    String viewAction = variableContext.getParameter("viewaction");
    if (viewAction != null)
      parameters.setParameter(LiveLinkParameters.viewAction,viewAction);
    
    // Server parameters
    String serverProtocol = variableContext.getParameter("serverprotocol");
    if (serverProtocol != null)
      parameters.setParameter(LiveLinkParameters.serverProtocol,serverProtocol);
    String serverName = variableContext.getParameter("servername");
    if (serverName != null)
      parameters.setParameter(LiveLinkParameters.serverName,serverName);
    String serverPort = variableContext.getParameter("serverport");
    if (serverPort != null)
      parameters.setParameter(LiveLinkParameters.serverPort,serverPort);
    String serverUserName = variableContext.getParameter("serverusername");
    if (serverUserName != null)
      parameters.setParameter(LiveLinkParameters.serverUsername,serverUserName);
    String serverPassword = variableContext.getParameter("serverpassword");
    if (serverPassword != null)
      parameters.setObfuscatedParameter(LiveLinkParameters.serverPassword,variableContext.mapKeyToPassword(serverPassword));
    String serverHTTPCgiPath = variableContext.getParameter("serverhttpcgipath");
    if (serverHTTPCgiPath != null)
      parameters.setParameter(LiveLinkParameters.serverHTTPCgiPath,serverHTTPCgiPath);
    String serverHTTPNTLMDomain = variableContext.getParameter("serverhttpntlmdomain");
    if (serverHTTPNTLMDomain != null)
      parameters.setParameter(LiveLinkParameters.serverHTTPNTLMDomain,serverHTTPNTLMDomain);
    String serverHTTPNTLMUserName = variableContext.getParameter("serverhttpntlmusername");
    if (serverHTTPNTLMUserName != null)
      parameters.setParameter(LiveLinkParameters.serverHTTPNTLMUsername,serverHTTPNTLMUserName);
    String serverHTTPNTLMPassword = variableContext.getParameter("serverhttpntlmpassword");
    if (serverHTTPNTLMPassword != null)
      parameters.setObfuscatedParameter(LiveLinkParameters.serverHTTPNTLMPassword,variableContext.mapKeyToPassword(serverHTTPNTLMPassword));
    
    String serverHTTPSKeystoreValue = variableContext.getParameter("serverhttpskeystoredata");
    final String serverConfigOp = variableContext.getParameter("serverconfigop");
    if (serverConfigOp != null)
    {
      if (serverConfigOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("serverkeystorealias");
        final IKeystoreManager mgr;
        if (serverHTTPSKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",serverHTTPSKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        serverHTTPSKeystoreValue = mgr.getString();
      }
      else if (serverConfigOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("servercertificate");
        final IKeystoreManager mgr;
        if (serverHTTPSKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",serverHTTPSKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try
        {
          mgr.importCertificate(alias,is);
        }
        catch (Throwable e)
        {
          certError = e.getMessage();
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Eat this exception
          }
        }

        if (certError != null)
        {
          return "Illegal certificate: "+certError;
        }
        serverHTTPSKeystoreValue = mgr.getString();
      }
    }
    parameters.setParameter(LiveLinkParameters.serverHTTPSKeystore,serverHTTPSKeystoreValue);
    
    // Ingest parameters
    String ingestProtocol = variableContext.getParameter("ingestprotocol");
    if (ingestProtocol != null)
      parameters.setParameter(LiveLinkParameters.ingestProtocol,ingestProtocol);
    String ingestPort = variableContext.getParameter("ingestport");
    if (ingestPort != null)
      parameters.setParameter(LiveLinkParameters.ingestPort,ingestPort);
    String ingestCgiPath = variableContext.getParameter("ingestcgipath");
    if (ingestCgiPath != null)
      parameters.setParameter(LiveLinkParameters.ingestCgiPath,ingestCgiPath);
    String ingestNtlmDomain = variableContext.getParameter("ingestntlmdomain");
    if (ingestNtlmDomain != null)
      parameters.setParameter(LiveLinkParameters.ingestNtlmDomain,ingestNtlmDomain);
    String ingestNtlmUsername = variableContext.getParameter("ingestntlmusername");
    if (ingestNtlmUsername != null)
      parameters.setParameter(LiveLinkParameters.ingestNtlmUsername,ingestNtlmUsername);
    String ingestNtlmPassword = variableContext.getParameter("ingestntlmpassword");
    if (ingestNtlmPassword != null)
      parameters.setObfuscatedParameter(LiveLinkParameters.ingestNtlmPassword,variableContext.mapKeyToPassword(ingestNtlmPassword));
    
    String ingestKeystoreValue = variableContext.getParameter("ingestkeystoredata");
    final String ingestConfigOp = variableContext.getParameter("ingestconfigop");
    if (ingestConfigOp != null)
    {
      if (ingestConfigOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("ingestkeystorealias");
        final IKeystoreManager mgr;
        if (ingestKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",ingestKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        ingestKeystoreValue = mgr.getString();
      }
      else if (ingestConfigOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("ingestcertificate");
        final IKeystoreManager mgr;
        if (ingestKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",ingestKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try
        {
          mgr.importCertificate(alias,is);
        }
        catch (Throwable e)
        {
          certError = e.getMessage();
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Eat this exception
          }
        }

        if (certError != null)
        {
          return "Illegal certificate: "+certError;
        }
        ingestKeystoreValue = mgr.getString();
      }
    }
    parameters.setParameter(LiveLinkParameters.ingestKeystore,ingestKeystoreValue);

    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<>();
    Map<String,String> configMap = new HashMap<>();

    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        configMap.put(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param),"********");
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore") ||
              param.length() > "truststore".length() && param.substring(param.length()-"truststore".length()).equalsIgnoreCase("truststore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        configMap.put(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param),"=&lt;"+Integer.toString(kmanager.getContents().length)+Messages.getBodyString(locale,"LivelinkConnector.certificates")+"&gt;");
      }
      else
      {
        configMap.put(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param), org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value));
      }
    }

    paramMap.put("CONFIGMAP",configMap);
    Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIGURATION_HTML, paramMap);
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.Paths"));
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.Filters"));
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.Security"));
    tabsArray.add(Messages.getString(locale,"LivelinkConnector.Metadata"));
    
    String seqPrefixParam = "s" + connectionSequenceNumber + "_";

    Map<String, String> paramMap = new HashMap<String, String>();  
    paramMap.put("seqPrefix", seqPrefixParam);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap, true);
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.  (actualSequenceNumber, tabName) form a unique tuple within
  *  the job.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    Map<String,Object> velocityContext = new HashMap<>();
    velocityContext.put("TabName",tabName);
    velocityContext.put("SeqNum", Integer.toString(connectionSequenceNumber));
    velocityContext.put("SelectedNum", Integer.toString(actualSequenceNumber));

    fillInPathsTab(velocityContext,out,ds);
    fillInFiltersTab(velocityContext, out, ds);
    fillInSecurityTab(velocityContext,out,ds);
    fillInMetadataTab(velocityContext,out,ds);

    // Now, do the part of the tabs that requires context logic
    if (tabName.equals(Messages.getString(locale,"LivelinkConnector.Paths")))
      fillInTransientPathsInfo(velocityContext,connectionSequenceNumber);
    else if (tabName.equals(Messages.getString(locale,"LivelinkConnector.Metadata")))
      fillInTransientMetadataInfo(velocityContext,connectionSequenceNumber);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_PATHS_HTML,velocityContext);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_FILTERS_HTML,velocityContext);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_SECURITY_HTML,velocityContext);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_METADATA_HTML,velocityContext);
  }

  /** Fill in paths tab */
  protected static void fillInPathsTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    boolean userWorkspaces = false;
    List<String> paths = new ArrayList<>();

    int i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("userworkspace"))
      {
        String value = sn.getAttributeValue("value");
        if (value != null && value.equals("true"))
          userWorkspaces = true;
      }
      else if (sn.getType().equals("startpoint"))
      {
        paths.add(sn.getAttributeValue("path"));
      }
    }

    velocityContext.put("USERWORKSPACES",userWorkspaces);
    velocityContext.put("PATHS",paths);
  }

  /** Fill in the transient portion of the Paths tab */
  protected void fillInTransientPathsInfo(Map<String,Object> velocityContext, int connectionSequenceNumber)
  {
    String message = null;
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String pathSoFar = (String)currentContext.get(seqPrefix+"specpath");
    if (pathSoFar == null)
      pathSoFar = "";

    String[] childList = null;
    // Grab next folder/project list
    try
    {
      childList = getChildFolderNames(pathSoFar);
      if (childList == null)
      {
        // Illegal path - set it back
        pathSoFar = "";
        childList = getChildFolderNames("");
        if (childList == null)
          throw new ManifoldCFException("Can't find any children for root folder");
      }
    }
    catch (ServiceInterruption e)
    {
      //e.printStackTrace();
      message = e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      //e.printStackTrace();
      message = e.getMessage();
    }

    velocityContext.put("PATHSOFAR",pathSoFar);
    if (message != null)
      velocityContext.put("MESSAGE",message);
    if (childList != null)
      velocityContext.put("CHILDLIST",childList);
  }

  /** Fill in filters tab */
  protected static void fillInFiltersTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    List<Pair<String,String>> fileSpecs = new ArrayList<>();

    int i = 0;
    // Next, go through include/exclude filespecs
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("include") || sn.getType().equals("exclude"))
      {
        fileSpecs.add(new Pair<>(sn.getType(),sn.getAttributeValue("filespec")));
      }
    }

    velocityContext.put("FILESPECS",fileSpecs);
  }

  /** Fill in security tab */
  protected static void fillInSecurityTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    // Security tab
    String security = "on";
    List<String> accessTokens = new ArrayList<String>();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("security"))
      {
        security = sn.getAttributeValue("value");
      }
      else if (sn.getType().equals("access"))
      {
        String token = sn.getAttributeValue("token");
        accessTokens.add(token);
      }
    }

    velocityContext.put("SECURITY",security);
    velocityContext.put("ACCESSTOKENS",accessTokens);
  }

  /** Fill in Metadata tab */
  protected static void fillInMetadataTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    boolean ingestAllMetadata = false;
    String pathNameAttribute = "";
    String pathNameSeparator = "/";
    Map<String,String> matchMap = new HashMap<>();
    //We are actually trying to create a Triple<L,M,R> by using Pair<L,R> where R is Pair<L,R>
    List<Pair<String,Pair<String,String>>> metadataList = new ArrayList<>();

    int i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("allmetadata"))
      {
        String value = sn.getAttributeValue("all");
        if (value != null && value.equals("true"))
          ingestAllMetadata = true;
      }
      // Find the path-value metadata attribute name
      else if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
        if (sn.getAttributeValue("separator") != null)
          pathNameSeparator = sn.getAttributeValue("separator");
      }
      // Find the path-value mapping data
      else if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.put(pathMatch, pathReplace);
      }
      // Go through the selected metadata attributes
      else if (sn.getType().equals("metadata"))
      {
        String categoryPath = sn.getAttributeValue("category");
        String isAll = sn.getAttributeValue("all");
        if (isAll == null)
          isAll = "false";
        String attributeName = sn.getAttributeValue("attribute");
        if (attributeName == null)
          attributeName = "";
        metadataList.add(new Pair<>(categoryPath,new Pair<>(isAll,attributeName)));
      }
    }


    velocityContext.put("INGESTALLMETADATA",ingestAllMetadata);
    velocityContext.put("PATHNAMEATTRIBUTE",pathNameAttribute);
    velocityContext.put("PATHNAMESEPARATOR",pathNameSeparator);
    velocityContext.put("MATCHMAP",matchMap);
    velocityContext.put("METADATA",metadataList);
  }

  /** Fill in the transient portion of the Metadata tab */
  protected void fillInTransientMetadataInfo(Map<String,Object> velocityContext, int connectionSequenceNumber)
  {
    String message = null;
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String categorySoFar = (String)currentContext.get(seqPrefix+"speccategory");
    if (categorySoFar == null)
      categorySoFar = "";

    String[] childList = null;
    String[] workspaceList = null;
    String[] categoryList = null;
    String[] attributeList = null;

    // Grab next folder/project list, and the appropriate category list
    try
    {
      if (categorySoFar.length() == 0)
      {
        workspaceList = getWorkspaceNames();
      }
      else
      {
        attributeList = getCategoryAttributes(categorySoFar);
        if (attributeList == null)
        {
          childList = getChildFolderNames(categorySoFar);
          if (childList == null)
          {
            // Illegal path - set it back
            categorySoFar = "";
            childList = getChildFolderNames("");
            if (childList == null)
              throw new ManifoldCFException("Can't find any children for root folder");
          }
          categoryList = getChildCategoryNames(categorySoFar);
          if (categoryList == null)
            throw new ManifoldCFException("Can't find any categories for root folder folder");
        }
      }

    }
    catch (ServiceInterruption e)
    {
      //e.printStackTrace();
      message = e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      //e.printStackTrace();
      message = e.getMessage();
    }

    velocityContext.put("CATEGORYSOFAR",categorySoFar);
    if (message != null)
      velocityContext.put("MESSAGE",message);
    if (childList != null)
      velocityContext.put("CHILDLIST",childList);
    if (workspaceList != null)
      velocityContext.put("WORKSPACELIST",workspaceList);
    if (categoryList != null)
      velocityContext.put("CATEGORYLIST",categoryList);
    if (attributeList != null)
      velocityContext.put("ATTRIBUTELIST",attributeList);
  }

  /**
   * A class to store a pair structure, where none of the properties can behave as a key.
   * @param <L> value to store in left.
   * @param <R> value to store in right.
   */
  public static final class Pair<L,R> {
    private final L left;
    private final R right;
    public Pair(L left, R right){
      this.left = left;
      this.right = right;
    }
    public L getLeft(){ return left; }
    public R getRight(){ return right; }

    @Override
    public String toString() {
      return left + "=" + right;
    }
  }  
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String userWorkspacesPresent = variableContext.getParameter(seqPrefix+"userworkspace_present");
    if (userWorkspacesPresent != null)
    {
      String value = variableContext.getParameter(seqPrefix+"userworkspace");
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("userworkspace"))
          ds.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode("userworkspace");
      sn.setAttribute("value",value);
      ds.addChild(ds.getChildCount(),sn);
    }
    
    String xc = variableContext.getParameter(seqPrefix+"pathcount");
    if (xc != null)
    {
      // Delete all path specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("startpoint"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(xc);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"pathop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter(seqPrefix+"specpath"+pathDescription);
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter(seqPrefix+"pathop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter(seqPrefix+"specpath");
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        ds.addChild(ds.getChildCount(),node);
      }
      else if (op != null && op.equals("Up"))
      {
        // Strip off end
        String path = variableContext.getParameter(seqPrefix+"specpath");
        int lastSlash = -1;
        int k = 0;
        while (k < path.length())
        {
          char x = path.charAt(k++);
          if (x == '/')
          {
            lastSlash = k-1;
            continue;
          }
          if (x == '\\')
            k++;
        }
        if (lastSlash == -1)
          path = "";
        else
          path = path.substring(0,lastSlash);
        currentContext.save(seqPrefix+"specpath",path);
      }
      else if (op != null && op.equals("AddToPath"))
      {
        String path = variableContext.getParameter(seqPrefix+"specpath");
        String addon = variableContext.getParameter(seqPrefix+"pathaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuilder sb = new StringBuilder();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (path.length() == 0)
            path = sb.toString();
          else
            path += "/" + sb.toString();
        }
        currentContext.save(seqPrefix+"specpath",path);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"filecount");
    if (xc != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("include") || sn.getType().equals("exclude"))
          ds.removeChild(i);
        else
          i++;
      }

      int fileCount = Integer.parseInt(xc);
      i = 0;
      while (i < fileCount)
      {
        String fileSpecDescription = "_"+Integer.toString(i);
        String fileOpName = seqPrefix+"fileop"+fileSpecDescription;
        xc = variableContext.getParameter(fileOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String filespecType = variableContext.getParameter(seqPrefix+"specfiletype"+fileSpecDescription);
        String filespec = variableContext.getParameter(seqPrefix+"specfile"+fileSpecDescription);
        SpecificationNode node = new SpecificationNode(filespecType);
        node.setAttribute("filespec",filespec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"fileop");
      if (op != null && op.equals("Add"))
      {
        String filespec = variableContext.getParameter(seqPrefix+"specfile");
        String filespectype = variableContext.getParameter(seqPrefix+"specfiletype");
        SpecificationNode node = new SpecificationNode(filespectype);
        node.setAttribute("filespec",filespec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"specsecurity");
    if (xc != null)
    {
      // Delete all security entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("security"))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode node = new SpecificationNode("security");
      node.setAttribute("value",xc);
      ds.addChild(ds.getChildCount(),node);

    }

    xc = variableContext.getParameter(seqPrefix+"tokencount");
    if (xc != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount)
      {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = seqPrefix+"accessop"+accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix+"spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"specallmetadata");
    if (xc != null)
    {
      // Look for the 'all metadata' checkbox
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("allmetadata"))
          ds.removeChild(i);
        else
          i++;
      }

      if (xc.equals("true"))
      {
        SpecificationNode newNode = new SpecificationNode("allmetadata");
        newNode.setAttribute("all",xc);
        ds.addChild(ds.getChildCount(),newNode);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"metadatacount");
    if (xc != null)
    {
      // Delete all metadata specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("metadata"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int metadataCount = Integer.parseInt(xc);
      // Gather up these
      i = 0;
      while (i < metadataCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"metadataop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Metadata inserts won't happen until the very end
        String category = variableContext.getParameter(seqPrefix+"speccategory"+pathDescription);
        String attributeName = variableContext.getParameter(seqPrefix+"specattribute"+pathDescription);
        String isAll = variableContext.getParameter(seqPrefix+"specattributeall"+pathDescription);
        SpecificationNode node = new SpecificationNode("metadata");
        node.setAttribute("category",category);
        if (isAll != null && isAll.equals("true"))
          node.setAttribute("all","true");
        else
          node.setAttribute("attribute",attributeName);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter(seqPrefix+"metadataop");
      if (op != null && op.equals("Add"))
      {
        String category = variableContext.getParameter(seqPrefix+"speccategory");
        String isAll = variableContext.getParameter(seqPrefix+"attributeall");
        if (isAll != null && isAll.equals("true"))
        {
          SpecificationNode node = new SpecificationNode("metadata");
          node.setAttribute("category",category);
          node.setAttribute("all","true");
          ds.addChild(ds.getChildCount(),node);
        }
        else
        {
          String[] attributes = variableContext.getParameterValues(seqPrefix+"attributeselect");
          if (attributes != null && attributes.length > 0)
          {
            int k = 0;
            while (k < attributes.length)
            {
              String attribute = attributes[k++];
              SpecificationNode node = new SpecificationNode("metadata");
              node.setAttribute("category",category);
              node.setAttribute("attribute",attribute);
              ds.addChild(ds.getChildCount(),node);
            }
          }
        }
      }
      else if (op != null && op.equals("Up"))
      {
        // Strip off end
        String category = variableContext.getParameter(seqPrefix+"speccategory");
        int lastSlash = -1;
        int firstColon = -1;      
        int k = 0;
        while (k < category.length())
        {
          char x = category.charAt(k++);
          if (x == '/')
          {
            lastSlash = k-1;
            continue;
          }
          if (x == ':')
          {
            firstColon = k;
            continue;
          }
          if (x == '\\')
            k++;
        }

        if (lastSlash == -1)
        {
          if (firstColon == -1 || firstColon == category.length())
            category = "";
          else
            category = category.substring(0,firstColon);
        }
        else
          category = category.substring(0,lastSlash);
        currentContext.save(seqPrefix+"speccategory",category);
      }
      else if (op != null && op.equals("AddToPath"))
      {
        String category = variableContext.getParameter(seqPrefix+"speccategory");
        String addon = variableContext.getParameter(seqPrefix+"metadataaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuilder sb = new StringBuilder();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (category.length() == 0 || category.endsWith(":"))
            category += sb.toString();
          else
            category += "/" + sb.toString();
        }
        currentContext.save(seqPrefix+"speccategory",category);
      }
      else if (op != null && op.equals("SetWorkspace"))
      {
        String addon = variableContext.getParameter(seqPrefix+"metadataaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuilder sb = new StringBuilder();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }

          String category = sb.toString() + ":";
          currentContext.save(seqPrefix+"speccategory",category);
        }
      }
      else if (op != null && op.equals("AddCategory"))
      {
        String category = variableContext.getParameter(seqPrefix+"speccategory");
        String addon = variableContext.getParameter(seqPrefix+"categoryaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuilder sb = new StringBuilder();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (category.length() == 0 || category.endsWith(":"))
            category += sb.toString();
          else
            category += "/" + sb.toString();
        }
        currentContext.save(seqPrefix+"speccategory",category);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"specpathnameattribute");
    if (xc != null)
    {
      String separator = variableContext.getParameter(seqPrefix+"specpathnameseparator");
      if (separator == null)
        separator = "/";
      // Delete old one
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathnameattribute"))
          ds.removeChild(i);
        else
          i++;
      }
      if (xc.length() > 0)
      {
        SpecificationNode node = new SpecificationNode("pathnameattribute");
        node.setAttribute("value",xc);
        node.setAttribute("separator",separator);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter(seqPrefix+"specmappingcount");
    if (xc != null)
    {
      // Delete old spec
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathmap"))
          ds.removeChild(i);
        else
          i++;
      }

      // Now, go through the data and assemble a new list.
      int mappingCount = Integer.parseInt(xc);

      // Gather up these
      i = 0;
      while (i < mappingCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"specmappingop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Inserts won't happen until the very end
        String match = variableContext.getParameter(seqPrefix+"specmatch"+pathDescription);
        String replace = variableContext.getParameter(seqPrefix+"specreplace"+pathDescription);
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // Check for add
      xc = variableContext.getParameter(seqPrefix+"specmappingop");
      if (xc != null && xc.equals("Add"))
      {
        String match = variableContext.getParameter(seqPrefix+"specmatch");
        String replace = variableContext.getParameter(seqPrefix+"specreplace");
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    Map<String,Object> velocityContext = new HashMap<>();

    fillInPathsTab(velocityContext,out,ds);
    fillInFiltersTab(velocityContext, out, ds);
    fillInSecurityTab(velocityContext,out,ds);
    fillInMetadataTab(velocityContext,out,ds);

    Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, velocityContext);
  }

  // The following public methods are NOT part of the interface.  They are here so that the UI can present information
  // that will allow users to select what they need.

  protected final static String CATEGORY_NAME = "CATEGORY";
  protected final static String ENTWKSPACE_NAME = "ENTERPRISE";

  /** Get the allowed workspace names.
  *@return a list of workspace names.
  */
  public String[] getWorkspaceNames()
    throws ManifoldCFException, ServiceInterruption
  {
    return new String[]{CATEGORY_NAME,ENTWKSPACE_NAME};
  }

  /** Given a path string, get a list of folders and projects under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of folder and project names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildFolderNames(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getChildFolders(new LivelinkContext(),pathString);
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildCategoryNames(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getChildCategories(new LivelinkContext(),pathString);
  }

  /** Given a category path, get a list of legal attribute names.
  *@param pathString is the current path of a category (with path components separated by dots).
  *@return a list of attribute names, in sorted order, or null of the path was invalid.
  */
  public String[] getCategoryAttributes(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getCategoryAttributes(new LivelinkContext(), pathString);
  }
  
  protected String[] getCategoryAttributes(LivelinkContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    // Start at root
    RootValue rv = new RootValue(llc,pathString);

    // Get the object id of the category the path describes
    int catObjectID = getCategoryId(rv);
    if (catObjectID == -1)
      return null;

    String[] rval = getCategoryAttributes(catObjectID);
    if (rval == null)
      return new String[0];
    return rval;
  }

  // Protected methods and classes


  /** Create the login URI.  This must be a relative URI.
  */
  protected String createLivelinkLoginURI()
    throws ManifoldCFException
  {
      StringBuilder llURI = new StringBuilder();

      llURI.append(ingestCgiPath);
      llURI.append("?func=ll.login&CurrentClientTime=D%2F2005%2F3%2F9%3A13%3A16%3A30&NextURL=");
      llURI.append(org.apache.manifoldcf.core.util.URLEncoder.encode(ingestCgiPath));
      llURI.append("%3FRedirect%3D1&Username=");
      llURI.append(org.apache.manifoldcf.core.util.URLEncoder.encode(llServer.getLLUser()));
      llURI.append("&Password=");
      llURI.append(org.apache.manifoldcf.core.util.URLEncoder.encode(llServer.getLLPwd()));

      return llURI.toString();
  }

  /**
  * Connects to the specified Livelink document using HTTP protocol
  * @param documentIdentifier is the document identifier (as far as the crawler knows).
  * @param activities is the process activity structure, so we can ingest
  */
  protected void ingestFromLiveLink(LivelinkContext llc,
    String documentIdentifier, String version,
    String[] actualAcls, String[] denyAcls,
    String[] categoryPaths,
    IProcessActivity activities,
    MetadataDescription desc, SystemMetadataDescription sDesc)
    throws ManifoldCFException, ServiceInterruption
  {

    String contextMsg = "for '"+documentIdentifier+"'";


    // Fetch logging
    long startTime = System.currentTimeMillis();
    String resultCode = null;
    String resultDescription = null;
    Long readSize = null;
    int objID;
    int vol;

    int colonPos = documentIdentifier.indexOf(":",1);
        
    if (colonPos == -1)
    {
      objID = new Integer(documentIdentifier.substring(1)).intValue();
      vol = LLENTWK_VOL;
    }
    else
    {
      objID = new Integer(documentIdentifier.substring(colonPos+1)).intValue();
      vol = new Integer(documentIdentifier.substring(1,colonPos)).intValue();
    }
    
    // Try/finally for fetch logging
    try
    {
      String viewHttpAddress = convertToViewURI(documentIdentifier);
      if (viewHttpAddress == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: No view URI "+contextMsg+" - not ingesting");
        resultCode = "NOVIEWURI";
        resultDescription = "Document had no view URI";
        activities.noDocument(documentIdentifier,version);
        return;
      }
      
      // Check URL first
      if (!activities.checkURLIndexable(viewHttpAddress))
      {
        // Document not ingestable due to URL
        resultCode = activities.EXCLUDED_URL;
        resultDescription = "URL ("+viewHttpAddress+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Excluding document "+documentIdentifier+" because its URL ("+viewHttpAddress+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }
      
      // Add general metadata
      ObjectInformation objInfo = llc.getObjectInformation(vol,objID);
      VersionInformation versInfo = llc.getVersionInformation(vol,objID,0);
      if (!objInfo.exists())
      {
        resultCode = "OBJECTNOTFOUND";
        resultDescription = "Object was not found in Livelink";
        Logging.connectors.debug("Livelink: No object "+contextMsg+": not ingesting");
        activities.noDocument(documentIdentifier,version);
        return;
      }
      if (!versInfo.exists())
      {
        resultCode = "VERSIONNOTFOUND";
        resultDescription = "Version was not found in Livelink";
        Logging.connectors.debug("Livelink: No version data "+contextMsg+": not ingesting");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      String mimeType = versInfo.getMimeType();
      if (!activities.checkMimeTypeIndexable(mimeType))
      {
        // Document not indexable because of its mime type
        resultCode = activities.EXCLUDED_MIMETYPE;
        resultDescription = "Mime type ("+mimeType+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Excluding document "+documentIdentifier+" because its mime type ("+mimeType+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }
        
      Long dataSize = versInfo.getDataSize();
      if (dataSize == null)
      {
        // Document had no length
        resultCode = "DOCUMENTNOLENGTH";
        resultDescription = "Document had no length in Livelink";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Excluding document "+documentIdentifier+" because it had no length");
        activities.noDocument(documentIdentifier,version);
        return;
      }
      
      if (!activities.checkLengthIndexable(dataSize.longValue()))
      {
        // Document not indexable because of its length
        resultCode = activities.EXCLUDED_LENGTH;
        resultDescription = "Document length ("+dataSize+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Excluding document "+documentIdentifier+" because its length ("+dataSize+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      Date modifyDate = versInfo.getModifyDate();
      if (!activities.checkDateIndexable(modifyDate))
      {
        // Document not indexable because of its date
        resultCode = activities.EXCLUDED_DATE;
        resultDescription = "Document date ("+modifyDate+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Excluding document "+documentIdentifier+" because its date ("+modifyDate+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }
      
      String fileName = versInfo.getFileName();
      Date creationDate = objInfo.getCreationDate();
      Integer parentID = objInfo.getParentId();
      
      
      RepositoryDocument rd = new RepositoryDocument();

      // Add general data we need for the output connector
      if (mimeType != null)
        rd.setMimeType(mimeType);
      if (fileName != null)
        rd.setFileName(fileName);
      if (creationDate != null)
        rd.setCreatedDate(creationDate);
      if (modifyDate != null)
        rd.setModifiedDate(modifyDate);
            
      rd.addField(GENERAL_NAME_FIELD,objInfo.getName());
      rd.addField(GENERAL_DESCRIPTION_FIELD,objInfo.getComments());
      if (creationDate != null)
        rd.addField(GENERAL_CREATIONDATE_FIELD,DateParser.formatISO8601Date(creationDate));
      if (modifyDate != null)
        rd.addField(GENERAL_MODIFYDATE_FIELD,DateParser.formatISO8601Date(modifyDate));
      if (parentID != null)
        rd.addField(GENERAL_PARENTID,parentID.toString());
      UserInformation owner = llc.getUserInformation(objInfo.getOwnerId().intValue());
      UserInformation creator = llc.getUserInformation(objInfo.getCreatorId().intValue());
      UserInformation modifier = llc.getUserInformation(versInfo.getOwnerId().intValue());
      if (owner != null)
        rd.addField(GENERAL_OWNER,owner.getName());
      if (creator != null)
        rd.addField(GENERAL_CREATOR,creator.getName());
      if (modifier != null)
        rd.addField(GENERAL_MODIFIER,modifier.getName());

      // Iterate over the metadata items.  These are organized by category
      // for speed of lookup.

      Iterator<MetadataItem> catIter = desc.getItems(categoryPaths);
      while (catIter.hasNext())
      {
        MetadataItem item = catIter.next();
        MetadataPathItem pathItem = item.getPathItem();
        if (pathItem != null)
        {
          int catID = pathItem.getCatID();
          // grab the associated catversion
          LLValue catVersion = getCatVersion(objID,catID);
          if (catVersion != null)
          {
            // Go through attributes now
            Iterator<String> attrIter = item.getAttributeNames();
            while (attrIter.hasNext())
            {
              String attrName = attrIter.next();
              // Create a unique metadata name
              String metadataName = pathItem.getCatName()+":"+attrName;
              // Fetch the metadata and stuff it into the RepositoryData structure
              String[] metadataValue = getAttributeValue(catVersion,attrName);
              if (metadataValue != null)
                rd.addField(metadataName,metadataValue);
              else
                Logging.connectors.warn("Livelink: Metadata attribute '"+metadataName+"' does not seem to exist; please correct the job");
            }
          }
          
        }
      }

      if (actualAcls != null && denyAcls != null)
        rd.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,actualAcls,denyAcls);

      // Add the path metadata item into the mix, if enabled
      String pathAttributeName = sDesc.getPathAttributeName();
      if (pathAttributeName != null && pathAttributeName.length() > 0)
      {
        String pathString = sDesc.getPathAttributeValue(documentIdentifier);
        if (pathString != null)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Path attribute name is '"+pathAttributeName+"'"+contextMsg+", value is '"+pathString+"'");
          rd.addField(pathAttributeName,pathString);
        }
      }

      if (ingestProtocol != null)
      {
        // Use HTTP to fetch document!
        String ingestHttpAddress = convertToIngestURI(documentIdentifier);
        if (ingestHttpAddress == null)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: No fetch URI "+contextMsg+" - not ingesting");
          resultCode = "NOURI";
          resultDescription = "Document had no fetch URI";
          activities.noDocument(documentIdentifier,version);
          return;
        }

        // Set up connection
        HttpClient client = getInitializedClient(contextMsg);

        long currentTime;

        if (Logging.connectors.isInfoEnabled())
          Logging.connectors.info("Livelink: " + ingestHttpAddress);


        HttpGet method = new HttpGet(getHost().toURI() + ingestHttpAddress);
        method.setHeader(new BasicHeader("Accept","*/*"));

        boolean wasInterrupted = false;
        ExecuteMethodThread methodThread = new ExecuteMethodThread(client,method);
        methodThread.start();
        try
        {
          int statusCode = methodThread.getResponseCode();
          switch (statusCode)
          {
          case 500:
          case 502:
            Logging.connectors.warn("Livelink: Service interruption during fetch "+contextMsg+" with Livelink HTTP Server, retrying...");
            resultCode = "FETCHFAILED";
            resultDescription = "HTTP error code "+statusCode+" fetching document";
            throw new ServiceInterruption("Service interruption during fetch",new ManifoldCFException(Integer.toString(statusCode)+" error while fetching"),System.currentTimeMillis()+60000L,
              System.currentTimeMillis()+600000L,-1,true);

          case HttpStatus.SC_UNAUTHORIZED:
            Logging.connectors.warn("Livelink: Document fetch unauthorized for "+ingestHttpAddress+" ("+contextMsg+")");
            // Since we logged in, we should fail here if the ingestion user doesn't have access to the
            // the document, but if we do, don't fail hard.
            resultCode = "UNAUTHORIZED";
            resultDescription = "Document fetch was unauthorized by IIS";
            activities.noDocument(documentIdentifier,version);
            return;

          case HttpStatus.SC_OK:
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Created http document connection to Livelink "+contextMsg);
            // A non-existent content length will cause a value of -1 to be returned.  This seems to indicate that the session login did not work right.
            if (methodThread.getResponseContentLength() < 0)
            {
              resultCode = "SESSIONLOGINFAILED";
              resultDescription = "Response content length was -1, which usually means session login did not succeed";
              activities.noDocument(documentIdentifier,version);
              return;
            }
              
            try
            {
              InputStream is = methodThread.getSafeInputStream();
              try
              {
                rd.setBinary(is,dataSize);
                            
                activities.ingestDocumentWithException(documentIdentifier,version,viewHttpAddress,rd);
                resultCode = "OK";
                readSize = dataSize;
                    
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Ingesting done "+contextMsg);

              }
              finally
              {
                // Close stream via thread, since otherwise this can hang
                is.close();
              }
            }
            catch (InterruptedException e)
            {
              wasInterrupted = true;
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            catch (HttpException e)
            {
              resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              resultDescription = e.getMessage();
              handleHttpException(contextMsg,e);
            }
            catch (IOException e)
            {
              resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              resultDescription = e.getMessage();
              handleIOException(contextMsg,e);
            }
            break;
          case HttpStatus.SC_BAD_REQUEST:
          case HttpStatus.SC_USE_PROXY:
          case HttpStatus.SC_GONE:
            resultCode = "HTTPERROR";
            resultDescription = "Http request returned status "+Integer.toString(statusCode);
            throw new ManifoldCFException("Unrecoverable request failure; error = "+Integer.toString(statusCode));
          default:
            resultCode = "UNKNOWNHTTPCODE";
            resultDescription = "Http request returned status "+Integer.toString(statusCode);
            Logging.connectors.warn("Livelink: Attempt to retrieve document from '"+ingestHttpAddress+"' received a response of "+Integer.toString(statusCode)+"; retrying in one minute");
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Fetch failed; retrying in 1 minute",new ManifoldCFException("Fetch failed with unknown code "+Integer.toString(statusCode)),
              currentTime+60000L,currentTime+600000L,-1,true);
          }
        }
        catch (InterruptedException e)
        {
          // Drop the connection on the floor
          methodThread.interrupt();
          methodThread = null;
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (HttpException e)
        {
          resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          resultDescription = e.getMessage();
          handleHttpException(contextMsg,e);
        }
        catch (IOException e)
        {
          resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          resultDescription = e.getMessage();
          handleIOException(contextMsg,e);
        }
        finally
        {
          if (methodThread != null)
          {
            methodThread.abort();
            try
            {
              if (!wasInterrupted)
                methodThread.finishUp();
            }
            catch (InterruptedException e)
            {
              throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
          }
        }
      }
      else
      {
        // Use FetchVersion instead
        long currentTime;
              
        // Fire up the document reading thread
        DocumentReadingThread t = new DocumentReadingThread(vol,objID,0);
        boolean wasInterrupted = false;
        t.start();
        try 
        {
          try
          {
            InputStream is = t.getSafeInputStream();
            try 
            {
              // Can only index while background thread is running!
              rd.setBinary(is, dataSize);
              activities.ingestDocumentWithException(documentIdentifier, version, viewHttpAddress, rd);
              resultCode = "OK";
              readSize = dataSize;
            }
            finally
            {
              is.close();
            }
          }
          catch (java.net.SocketTimeoutException e)
          {
            throw e;
          }
          catch (InterruptedIOException e)
          {
            wasInterrupted = true;
            throw e;
          }
          finally
          {
            if (!wasInterrupted)
              t.finishUp();
          }

          // No errors.  Record the fact that we made it.
        }
        catch (InterruptedException e) 
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
        }
        catch (IOException e)
        {
          resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          resultDescription = e.getMessage();
          handleIOException(contextMsg,e);
        }
        catch (RuntimeException e)
        {
          resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          resultDescription = e.getMessage();
          handleLivelinkRuntimeException(e,0,true);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        resultCode = null;
      throw e;
    }
    finally
    {
      if (resultCode != null)
        activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,readSize,vol+":"+objID,resultCode,resultDescription,null);
    }
  }

  protected static void handleHttpException(String contextMsg, HttpException e)
    throws ManifoldCFException, ServiceInterruption
  {
    long currentTime = System.currentTimeMillis();
    // Treat unknown error ingesting data as a transient condition
    Logging.connectors.warn("Livelink: HTTP exception ingesting "+contextMsg+": "+e.getMessage(),e);
    throw new ServiceInterruption("HTTP exception ingesting "+contextMsg+": "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
  }
  
  protected static void handleIOException(String contextMsg, IOException e)
    throws ManifoldCFException, ServiceInterruption
  {
    long currentTime = System.currentTimeMillis();
    if (e instanceof java.net.SocketTimeoutException)
    {
      Logging.connectors.warn("Livelink: Livelink socket timed out ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof java.net.SocketException)
    {
      Logging.connectors.warn("Livelink: Livelink socket error ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof javax.net.ssl.SSLHandshakeException)
    {
      Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
      throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
    }
    if (e instanceof ConnectTimeoutException)
    {
      Logging.connectors.warn("Livelink: Livelink socket timed out connecting to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    // Treat unknown error ingesting data as a transient condition
    Logging.connectors.warn("Livelink: IO exception ingesting "+contextMsg+": "+e.getMessage(),e);
    throw new ServiceInterruption("IO exception ingesting "+contextMsg+": "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
  }
  
  /** Initialize a livelink client connection */
  protected HttpClient getInitializedClient(String contextMsg)
    throws ServiceInterruption, ManifoldCFException
  {
    long currentTime;
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("Livelink: Session authenticating via http "+contextMsg+"...");
    HttpGet authget = new HttpGet(getHost().toURI() + createLivelinkLoginURI());
    authget.setHeader(new BasicHeader("Accept","*/*"));
    try
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Created new HttpGet "+contextMsg+"; executing authentication method");
      int statusCode = executeMethodViaThread(httpClient,authget);

      if (statusCode == 502 || statusCode == 500)
      {
        Logging.connectors.warn("Livelink: Service interruption during authentication "+contextMsg+" with Livelink HTTP Server, retrying...");
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("502 error during authentication",new ManifoldCFException("502 error while authenticating"),
          currentTime+60000L,currentTime+600000L,-1,true);
      }
      if (statusCode != HttpStatus.SC_OK)
      {
        Logging.connectors.error("Livelink: Failed to authenticate "+contextMsg+" against Livelink HTTP Server; Status code: " + statusCode);
        // Ok, so we didn't get in - simply do not ingest
        if (statusCode == HttpStatus.SC_UNAUTHORIZED)
          throw new ManifoldCFException("Session authorization failed with a 401 code; are credentials correct?");
        else
          throw new ManifoldCFException("Session authorization failed with code "+Integer.toString(statusCode));
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (java.net.SocketTimeoutException e)
    {
      currentTime = System.currentTimeMillis();
      Logging.connectors.warn("Livelink: Socket timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
    }
    catch (java.net.SocketException e)
    {
      currentTime = System.currentTimeMillis();
      Logging.connectors.warn("Livelink: Socket error authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
    }
    catch (javax.net.ssl.SSLHandshakeException e)
    {
      currentTime = System.currentTimeMillis();
      Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
      throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
    }
    catch (ConnectTimeoutException e)
    {
      currentTime = System.currentTimeMillis();
      Logging.connectors.warn("Livelink: Connect timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (HttpException e)
    {
      Logging.connectors.error("Livelink: HTTP exception when authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ManifoldCFException("Unable to communicate with the Livelink HTTP Server: "+e.getMessage(), e);
    }
    catch (IOException e)
    {
      Logging.connectors.error("Livelink: IO exception when authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ManifoldCFException("Unable to communicate with the Livelink HTTP Server: "+e.getMessage(), e);
    }

    return httpClient;
  }

  /** Pack category and attribute */
  protected static String packCategoryAttribute(String category, String attribute)
  {
    StringBuilder sb = new StringBuilder();
    pack(sb,category,':');
    pack(sb,attribute,':');
    return sb.toString();
  }

  /** Unpack category and attribute */
  protected static void unpackCategoryAttribute(StringBuilder category, StringBuilder attribute, String value)
  {
    int startPos = 0;
    startPos = unpack(category,value,startPos,':');
    startPos = unpack(attribute,value,startPos,':');
  }

  /** Given a path string, get a list of folders and projects under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of folder and project names, in sorted order, or null if the path was invalid.
  */
  protected String[] getChildFolders(LivelinkContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    RootValue rv = new RootValue(llc,pathString);

    // Get the volume, object id of the folder/project the path describes
    VolumeAndId vid = getPathId(rv);
    if (vid == null)
      return null;

    String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
      " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";

    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
      try
      {
        t.start();
	LLValue children;
	try
	{
	  children = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
	}

        String[] rval = new String[children.size()];
        int j = 0;
        while (j < children.size())
        {
          rval[j] = children.toString(j,"Name");
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  protected String[] getChildCategories(LivelinkContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    // Start at root
    RootValue rv = new RootValue(llc,pathString);

    // Get the volume, object id of the folder/project the path describes
    VolumeAndId vid = getPathId(rv);
    if (vid == null)
      return null;

    // We want only folders that are children of the current object and which match the specified subfolder
    String filterString = "SubType="+ LAPI_DOCUMENTS.CATEGORYSUBTYPE;

    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
      try
      {
        t.start();
	LLValue children;
	try
	{
	  children = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }

        String[] rval = new String[children.size()];
        int j = 0;
        while (j < children.size())
        {
          rval[j] = children.toString(j,"Name");
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetCategoryAttributesThread extends Thread
  {
    protected final int catObjectID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetCategoryAttributesThread(int catObjectID)
    {
      super();
      setDaemon(true);
      this.catObjectID = catObjectID;
    }

    public void run()
    {
      try
      {
        LLValue catID = new LLValue();
        catID.setAssoc();
        catID.add("ID", catObjectID);
        catID.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

        LLValue catVersion = new LLValue();
        int status = LLDocs.FetchCategoryVersion(catID,catVersion);
        if (status == 107105 || status == 107106)
          return;
        if (status != 0)
        {
          throw new ManifoldCFException("Error getting category version: "+Integer.toString(status));
        }

        LLValue children = new LLValue();
        status = LLAttributes.AttrListNames(catVersion,null,children);
        if (status != 0)
        {
          throw new ManifoldCFException("Error getting attribute names: "+Integer.toString(status));
        }
        rval = children;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }


  /** Given a category path, get a list of legal attribute names.
  *@param catObjectID is the object id of the category.
  *@return a list of attribute names, in sorted order, or null of the path was invalid.
  */
  protected String[] getCategoryAttributes(int catObjectID)
    throws ManifoldCFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetCategoryAttributesThread t = new GetCategoryAttributesThread(catObjectID);
      try
      {
        t.start();
	LLValue children;
	try
	{
	  children = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }

        if (children == null)
          return null;

        String[] rval = new String[children.size()];
        LLValueEnumeration en = children.enumerateValues();

        int j = 0;
        while (en.hasMoreElements())
        {
          LLValue v = (LLValue)en.nextElement();
          rval[j] = v.toString();
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetCategoryVersionThread extends Thread
  {
    protected final int objID;
    protected final int catID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetCategoryVersionThread(int objID, int catID)
    {
      super();
      setDaemon(true);
      this.objID = objID;
      this.catID = catID;
    }

    public void run()
    {
      try
      {
        // Set up the right llvalues

        // Object ID
        LLValue objIDValue = new LLValue().setAssoc();
        objIDValue.add("ID", objID);
        // Current version, so don't set the "Version" field

        // CatID
        LLValue catIDValue = new LLValue().setAssoc();
        catIDValue.add("ID", catID);
        catIDValue.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

        LLValue rvalue = new LLValue();

        int status = LLDocs.GetObjectAttributesEx(objIDValue,catIDValue,rvalue);
        // If either the object is wrong, or the object does not have the specified category, return null.
        if (status == 103101 || status == 107205)
          return;

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving category version: "+Integer.toString(status)+": "+llServer.getErrors());
        }

        rval = rvalue;

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Get a category version for document.
  */
  protected LLValue getCatVersion(int objID, int catID)
    throws ManifoldCFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetCategoryVersionThread t = new GetCategoryVersionThread(objID,catID);
      try
      {
        t.start();
	try
	{
	  return t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (NullPointerException npe)
      {
        // LAPI throws a null pointer exception under very rare conditions when the GetObjectAttributesEx is
        // called.  The conditions are not clear at this time - it could even be due to Livelink corruption.
        // However, I'm going to have to treat this as
        // indicating that this category version does not exist for this document.
        Logging.connectors.warn("Livelink: Null pointer exception thrown trying to get cat version for category "+
          Integer.toString(catID)+" for object "+Integer.toString(objID));
        return null;
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetAttributeValueThread extends Thread
  {
    protected final LLValue categoryVersion;
    protected final String attributeName;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetAttributeValueThread(LLValue categoryVersion, String attributeName)
    {
      super();
      setDaemon(true);
      this.categoryVersion = categoryVersion;
      this.attributeName = attributeName;
    }

    public void run()
    {
      try
      {
        // Set up the right llvalues
        LLValue children = new LLValue();
        int status = LLAttributes.AttrGetValues(categoryVersion,attributeName,
          0,null,children);
        // "Not found" status - I don't know if it possible to get this here, but if so, behave civilly
        if (status == 103101)
          return;
        // This seems to be the real error LAPI returns if you don't have an attribute of this name
        if (status == 8000604)
          return;

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving attribute value: "+Integer.toString(status)+": "+llServer.getErrors());
        }
        rval = children;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Get an attribute value from a category version.
  */
  protected String[] getAttributeValue(LLValue categoryVersion, String attributeName)
    throws ManifoldCFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetAttributeValueThread t = new GetAttributeValueThread(categoryVersion, attributeName);
      try
      {
        t.start();
	LLValue children;
	try
	{
	  children = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }
	
        if (children == null)
          return null;
        String[] rval = new String[children.size()];
        LLValueEnumeration en = children.enumerateValues();

        int j = 0;
        while (en.hasMoreElements())
        {
          LLValue v = (LLValue)en.nextElement();
          rval[j] = v.toString();
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetObjectRightsThread extends Thread
  {
    protected final int vol;
    protected final int objID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectRightsThread(int vol, int objID)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.objID = objID;
    }

    public void run()
    {
      try
      {
        LLValue childrenObjects = new LLValue();
        int status = LLDocs.GetObjectRights(vol, objID, childrenObjects);
        // If the rights object doesn't exist, behave civilly
        if (status == 103101)
          return;

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving document rights: "+Integer.toString(status)+": "+llServer.getErrors());
        }

        rval = childrenObjects;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Get an object's rights.  This will be an array of right id's, including the special
  * ones defined by Livelink, or null will be returned (if the object is not found).
  *@param vol is the volume id
  *@param objID is the object id
  *@return the array.
  */
  protected int[] getObjectRights(int vol, int objID)
    throws ManifoldCFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetObjectRightsThread t = new GetObjectRightsThread(vol,objID);
      try
      {
        t.start();
	LLValue childrenObjects;
	try
	{
	  childrenObjects = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }

        if (childrenObjects == null)
          return null;

        int size;
        if (childrenObjects.isRecord())
          size = 1;
        else if (childrenObjects.isTable())
          size = childrenObjects.size();
        else
          size = 0;

        int minPermission = LAPI_DOCUMENTS.PERM_SEE +
          LAPI_DOCUMENTS.PERM_SEECONTENTS;


        int j = 0;
        int count = 0;
        while (j < size)
        {
          int permission = childrenObjects.toInteger(j, "Permissions");
          // Only if the permission is "see contents" can we consider this
          // access token!
          if ((permission & minPermission) == minPermission)
            count++;
          j++;
        }

        int[] rval = new int[count];
        j = 0;
        count = 0;
        while (j < size)
        {
          int token = childrenObjects.toInteger(j, "RightID");
          int permission = childrenObjects.toInteger(j, "Permissions");
          // Only if the permission is "see contents" can we consider this
          // access token!
          if ((permission & minPermission) == minPermission)
            rval[count++] = token;
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  /** Local cache for various kinds of objects that may be useful more than once.
  */
  protected class LivelinkContext
  {
    /** Cache of ObjectInformation objects. */
    protected final Map<ObjectInformation,ObjectInformation> objectInfoMap = new HashMap<ObjectInformation,ObjectInformation>();
    /** Cache of VersionInformation objects. */
    protected final Map<VersionInformation,VersionInformation> versionInfoMap = new HashMap<VersionInformation,VersionInformation>();
    /** Cache of UserInformation objects */
    protected final Map<UserInformation,UserInformation> userInfoMap = new HashMap<UserInformation,UserInformation>();
    
    public LivelinkContext()
    {
    }
    
    public ObjectInformation getObjectInformation(int volumeID, int objectID)
    {
      ObjectInformation oi = new ObjectInformation(volumeID,objectID);
      ObjectInformation lookupValue = objectInfoMap.get(oi);
      if (lookupValue == null)
      {
        objectInfoMap.put(oi,oi);
        return oi;
      }
      return lookupValue;
    }
    
    public VersionInformation getVersionInformation(int volumeID, int objectID, int revisionNumber)
    {
      VersionInformation vi = new VersionInformation(volumeID,objectID,revisionNumber);
      VersionInformation lookupValue = versionInfoMap.get(vi);
      if (lookupValue == null)
      {
        versionInfoMap.put(vi,vi);
        return vi;
      }
      return lookupValue;
    }
    
    public UserInformation getUserInformation(int userID)
    {
      UserInformation ui = new UserInformation(userID);
      UserInformation lookupValue = userInfoMap.get(ui);
      if (lookupValue == null)
      {
        userInfoMap.put(ui,ui);
        return ui;
      }
      return lookupValue;
    }
  }

  /** This object represents a cache of user information.
  * Initialize it with the user ID.  Then, request desired fields from it.
  */
  protected class UserInformation
  {
    protected final int userID;
    
    protected LLValue userValue = null;
    
    public UserInformation(int userID)
    {
      this.userID = userID;
    }
    
    public boolean exists()
      throws ServiceInterruption, ManifoldCFException
    {
      return getUserValue() != null;
    }
    
    public String getName()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue userValue = getUserValue();
      if (userValue == null)
        return null;
      return userValue.toString("NAME");
    }
      
    protected LLValue getUserValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (userValue == null)
      {
        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          GetUserInfoThread t = new GetUserInfoThread(userID);
          try
          {
            t.start();
	    try
	    {
	      userValue = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
	    }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
      }
      return userValue;
    }

    @Override
    public String toString()
    {
      return "("+userID+")";
    }
    
    @Override
    public int hashCode()
    {
      return (userID << 5) ^ (userID >> 3);
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof UserInformation))
        return false;
      UserInformation other = (UserInformation)o;
      return userID == other.userID;
    }

  }
  
  /** This object represents a cache of version information.
  * Initialize it with the volume ID and object ID and revision number (usually zero).
  * Then, request the desired fields from it.
  */
  protected class VersionInformation
  {
    protected final int volumeID;
    protected final int objectID;
    protected final int revisionNumber;
    
    protected LLValue versionValue = null;
    
    public VersionInformation(int volumeID, int objectID, int revisionNumber)
    {
      this.volumeID = volumeID;
      this.objectID = objectID;
      this.revisionNumber = revisionNumber;
    }
    
    public boolean exists()
      throws ServiceInterruption, ManifoldCFException
    {
      return getVersionValue() != null;
    }

    /** Get data size.
    */
    public Long getDataSize()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getVersionValue();
      if (elem == null)
        return null;
      return new Long(elem.toLong("FILEDATASIZE"));
    }

    /** Get file name.
    */
    public String getFileName()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.toString("FILENAME");
    }

    /** Get mime type.
    */
    public String getMimeType()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.toString("MIMETYPE");
    }

    /** Get modify date.
    */
    public Date getModifyDate()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.toDate("MODIFYDATE"); 
    }

    /** Get modifier.
    */
    public Integer getOwnerId()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getVersionValue();
      if (elem == null)
        return null;
      return new Integer(elem.toInteger("OWNER")); 
    }

    /** Get version LLValue */
    protected LLValue getVersionValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (versionValue == null)
      {
        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          GetVersionInfoThread t = new GetVersionInfoThread(volumeID,objectID,revisionNumber);
          try
          {
            t.start();
	    try
	    {
	      versionValue = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
      }
      return versionValue;
    }
    
    @Override
    public int hashCode()
    {
      return (volumeID << 5) ^ (volumeID >> 3) ^ (objectID << 5) ^ (objectID >> 3) ^ (revisionNumber << 5) ^ (revisionNumber >> 3);
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof VersionInformation))
        return false;
      VersionInformation other = (VersionInformation)o;
      return volumeID == other.volumeID && objectID == other.objectID && revisionNumber == other.revisionNumber;
    }

  }
  
  /** This object represents an object information cache.
  * Initialize it with the volume ID and object ID, and then request
  * the appropriate fields from it.  Keep it around as long as needed; it functions as a cache
  * of sorts...
  */
  protected class ObjectInformation
  {
    protected final int volumeID;
    protected final int objectID;
    
    protected LLValue objectValue = null;
    
    public ObjectInformation(int volumeID, int objectID)
    {
      this.volumeID = volumeID;
      this.objectID = objectID;
    }

    /**
    * Check whether object seems to exist or not.
    */
    public boolean exists()
      throws ServiceInterruption, ManifoldCFException
    {
      return getObjectValue() != null;
    }

    /** Check if this object is the category workspace.
    */
    public boolean isCategoryWorkspace()
    {
      return objectID == LLCATWK_ID;
    }
    
    /** Check if this object is the entity workspace.
    */
    public boolean isEntityWorkspace()
    {
      return objectID == LLENTWK_ID;
    }
    
    /** toString override */
    @Override
    public String toString()
    {
      return "(Volume: "+volumeID+", Object: "+objectID+")";
    }
    
    /**
    * Returns the object ID specified by the path name.
    * @param startPath is the folder name (a string with dots as separators)
    */
    public VolumeAndId getPathId(String startPath)
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue objInfo = getObjectValue();
      if (objInfo == null)
        return null;

      // Grab the volume ID and starting object
      int obj = objInfo.toInteger("ID");
      int vol = objInfo.toInteger("VolumeID");

      // Pick apart the start path.  This is a string separated by slashes.
      int charindex = 0;
      while (charindex < startPath.length())
      {
        StringBuilder currentTokenBuffer = new StringBuilder();
        // Find the current token
        while (charindex < startPath.length())
        {
          char x = startPath.charAt(charindex++);
          if (x == '/')
            break;
          if (x == '\\')
          {
            // Attempt to escape what follows
            x = startPath.charAt(charindex);
            charindex++;
          }
          currentTokenBuffer.append(x);
        }

        String subFolder = currentTokenBuffer.toString();
        // We want only folders that are children of the current object and which match the specified subfolder
        String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
          " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ") and Name='" + subFolder + "'";

        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
          try
          {
            t.start();
	    LLValue children;
	    try
	    {
	      children = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
            }

            if (children == null)
              return null;

            // If there is one child, then we are okay.
            if (children.size() == 1)
            {
              // New starting point is the one we found.
              obj = children.toInteger(0, "ID");
              int subtype = children.toInteger(0, "SubType");
              if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
              {
                vol = obj;
                obj = -obj;
              }
            }
            else
            {
              // Couldn't find the path.  Instead of throwing up, return null to indicate
              // illegal node.
              return null;
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;

          }
        }

      }
      return new VolumeAndId(vol,obj);
    }

    /**
    * Returns the category ID specified by the path name.
    * @param startPath is the folder name, ending in a category name (a string with slashes as separators)
    */
    public int getCategoryId(String startPath)
      throws ManifoldCFException, ServiceInterruption
    {
      LLValue objInfo = getObjectValue();
      if (objInfo == null)
        return -1;

      // Grab the volume ID and starting object
      int obj = objInfo.toInteger("ID");
      int vol = objInfo.toInteger("VolumeID");

      // Pick apart the start path.  This is a string separated by slashes.
      if (startPath.length() == 0)
        return -1;

      int charindex = 0;
      while (charindex < startPath.length())
      {
        StringBuilder currentTokenBuffer = new StringBuilder();
        // Find the current token
        while (charindex < startPath.length())
        {
          char x = startPath.charAt(charindex++);
          if (x == '/')
            break;
          if (x == '\\')
          {
            // Attempt to escape what follows
            x = startPath.charAt(charindex);
            charindex++;
          }
          currentTokenBuffer.append(x);
        }
        String subFolder = currentTokenBuffer.toString();
        String filterString;

        // We want only folders that are children of the current object and which match the specified subfolder
        if (charindex < startPath.length())
          filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
          " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";
        else
          filterString = "SubType="+LAPI_DOCUMENTS.CATEGORYSUBTYPE;

        filterString += " and Name='" + subFolder + "'";

        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
          try
          {
            t.start();
	    LLValue children;
	    try
	    {
	      children = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
            }

            if (children == null)
              return -1;

            // If there is one child, then we are okay.
            if (children.size() == 1)
            {
              // New starting point is the one we found.
              obj = children.toInteger(0, "ID");
              int subtype = children.toInteger(0, "SubType");
              if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
              {
                vol = obj;
                obj = -obj;
              }
            }
            else
            {
              // Couldn't find the path.  Instead of throwing up, return null to indicate
              // illegal node.
              return -1;
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
      }
      return obj;
    }

    /** Get permissions.
    */
    public Integer getPermissions()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return new Integer(objectValue.toInteger("Permissions"));
    }
    
    /** Get OpenText document name.
    */
    public String getName()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.toString("NAME"); 
    }

    /** Get OpenText comments/description.
    */
    public String getComments()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.toString("COMMENT"); 
    }

    /** Get parent ID.
    */
    public Integer getParentId()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return new Integer(elem.toInteger("ParentId")); 
    }

    /** Get owner ID.
    */
    public Integer getOwnerId()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return new Integer(elem.toInteger("UserId")); 
    }

    /** Get group ID.
    */
    public Integer getGroupId()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return new Integer(elem.toInteger("GroupId")); 
    }
    
    /** Get creation date.
    */
    public Date getCreationDate()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.toDate("CREATEDATE"); 
    }
    
    /** Get creator ID.
    */
    public Integer getCreatorId()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return new Integer(elem.toInteger("CREATEDBY")); 
    }

    /* Get modify date.
    */
    public Date getModifyDate()
      throws ServiceInterruption, ManifoldCFException
    {
      LLValue elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.toDate("ModifyDate"); 
    }

    /** Get the objInfo object.
    */
    protected LLValue getObjectValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (objectValue == null)
      {
        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          GetObjectInfoThread t = new GetObjectInfoThread(volumeID,objectID);
          try
          {
            t.start();
	    try
	    {
	      objectValue = t.finishUp();
	    }
	    catch (ManifoldCFException e)
	    {
	      sanityRetryCount = assessRetry(sanityRetryCount,e);
	      continue;
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
      }
      return objectValue;
    }
    
    @Override
    public int hashCode()
    {
      return (volumeID << 5) ^ (volumeID >> 3) ^ (objectID << 5) ^ (objectID >> 3);
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof ObjectInformation))
        return false;
      ObjectInformation other = (ObjectInformation)o;
      return volumeID == other.volumeID && objectID == other.objectID;
    }
  }

  /** Thread we can abandon that lists all users (except admin).
  */
  protected class ListUsersThread extends Thread
  {
    protected LLValue rval = null;
    protected Throwable exception = null;

    public ListUsersThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        LLValue userList = new LLValue();
        int status = LLUsers.ListUsers(userList);

        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: User list retrieved: status="+Integer.toString(status));
        }

        if (status < 0)
        {
          Logging.connectors.debug("Livelink: User list inaccessable ("+llServer.getErrors()+")");
          return;
        }

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving user list: status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        
        rval = userList;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Thread we can abandon that gets user information for a userID.
  */
  protected class GetUserInfoThread extends Thread
  {
    protected final int user;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetUserInfoThread(int user)
    {
      super();
      setDaemon(true);
      this.user = user;
    }

    public void run()
    {
      try
      {
        LLValue userinfo = new LLValue().setAssoc();
        int status = LLUsers.GetUserByID(user,userinfo);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: User status retrieved for "+Integer.toString(user)+": status="+Integer.toString(status));
        }

        // Treat both 103101 and 103102 as 'object not found'. 401101 is 'user not found'.
        if (status == 103101 || status == 103102 || status == 401101)
          return;

        // This error means we don't have permission to get the object's status, apparently
        if (status < 0)
        {
          Logging.connectors.debug("Livelink: User info inaccessable for user "+Integer.toString(user)+
            " ("+llServer.getErrors()+")");
          return;
        }

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving user "+Integer.toString(user)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = userinfo;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Thread we can abandon that gets version information for a volume and an id and a revision.
  */
  protected class GetVersionInfoThread extends Thread
  {
    protected final int vol;
    protected final int id;
    protected final int revNumber;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetVersionInfoThread(int vol, int id, int revNumber)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.id = id;
      this.revNumber = revNumber;
    }

    public void run()
    {
      try
      {
        LLValue versioninfo = new LLValue().setAssocNotSet();
        int status = LLDocs.GetVersionInfo(vol,id,revNumber,versioninfo);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Version status retrieved for "+Integer.toString(vol)+":"+Integer.toString(id)+", rev "+revNumber+": status="+Integer.toString(status));
        }

        // Treat both 103101 and 103102 as 'object not found'.
        if (status == 103101 || status == 103102)
          return;

        // This error means we don't have permission to get the object's status, apparently
        if (status < 0)
        {
          Logging.connectors.debug("Livelink: Version info inaccessable for object "+Integer.toString(vol)+":"+Integer.toString(id)+", rev "+revNumber+
            " ("+llServer.getErrors()+")");
          return;
        }

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving document version "+Integer.toString(vol)+":"+Integer.toString(id)+", rev "+revNumber+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = versioninfo;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Thread we can abandon that gets object information for a volume and an id.
  */
  protected class GetObjectInfoThread extends Thread
  {
    protected int vol;
    protected int id;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectInfoThread(int vol, int id)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.id = id;
    }

    public void run()
    {
      try
      {
        LLValue objinfo = new LLValue().setAssocNotSet();
        int status = LLDocs.GetObjectInfo(vol,id,objinfo);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Status retrieved for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status));
        }

        // Treat both 103101 and 103102 as 'object not found'.
        if (status == 103101 || status == 103102)
          return;

        // This error means we don't have permission to get the object's status, apparently
        if (status < 0)
        {
          Logging.connectors.debug("Livelink: Object info inaccessable for object "+Integer.toString(vol)+":"+Integer.toString(id)+
            " ("+llServer.getErrors()+")");
          return;
        }

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving document object "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = objinfo;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Build a set of actual acls given a set of rights */
  protected String[] lookupTokens(int[] rights, ObjectInformation objInfo)
    throws ManifoldCFException, ServiceInterruption
  {
    if (!objInfo.exists())
      return null;
    
    String[] convertedAcls = new String[rights.length];

    LLValue infoObject = null;
    int j = 0;
    int k = 0;
    while (j < rights.length)
    {
      int token = rights[j++];
      String tokenValue;
      // Consider this token
      switch (token)
      {
      case LAPI_DOCUMENTS.RIGHT_OWNER:
        // Look up user for current document (UserID attribute)
        tokenValue = objInfo.getOwnerId().toString();
        break;
      case LAPI_DOCUMENTS.RIGHT_GROUP:
        tokenValue = objInfo.getGroupId().toString();
        break;
      case LAPI_DOCUMENTS.RIGHT_WORLD:
        // Add "Guest" token
        tokenValue = "GUEST";
        break;
      case LAPI_DOCUMENTS.RIGHT_SYSTEM:
        // Add "System" token
        tokenValue = "SYSTEM";
        break;
      default:
        tokenValue = Integer.toString(token);
        break;
      }

      // This might return a null if we could not look up the object corresponding to the right.  If so, it is safe to skip it because
      // that always RESTRICTS view of the object (maybe erroneously), but does not expand visibility.
      if (tokenValue != null)
        convertedAcls[k++] = tokenValue;
    }
    String[] actualAcls = new String[k];
    j = 0;
    while (j < k)
    {
      actualAcls[j] = convertedAcls[j];
      j++;
    }
    return actualAcls;
  }

  protected class GetObjectCategoryIDsThread extends Thread
  {
    protected final int vol;
    protected final int id;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectCategoryIDsThread(int vol, int id)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.id = id;
    }

    public void run()
    {
      try
      {
        // Object ID
        LLValue objIDValue = new LLValue().setAssocNotSet();
        objIDValue.add("ID", id);

        // Category ID List
        LLValue catIDList = new LLValue().setAssocNotSet();

        int status = LLDocs.ListObjectCategoryIDs(objIDValue,catIDList);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Status value for getting object categories for "+Integer.toString(vol)+":"+Integer.toString(id)+" is: "+Integer.toString(status));
        }

        if (status == 103101)
          return;

        if (status != 0)
        {
          throw new ManifoldCFException("Error retrieving document categories for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = catIDList;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public LLValue finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Get category IDs associated with an object.
  * @param vol is the volume ID
  * @param id the object ID
  * @return an array of integers containing category identifiers, or null if the object is not found.
  */
  protected int[] getObjectCategoryIDs(int vol, int id)
    throws ManifoldCFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetObjectCategoryIDsThread t = new GetObjectCategoryIDsThread(vol,id);
      try
      {
        t.start();
	LLValue catIDList;
	try
	{
	  catIDList = t.finishUp();
	}
	catch (ManifoldCFException e)
	{
	  sanityRetryCount = assessRetry(sanityRetryCount,e);
	  continue;
        }

        if (catIDList == null)
          return null;

        int size = catIDList.size();

        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(size)+" attached categories");
        }

        // Count the category ids
        int count = 0;
        int j = 0;
        while (j < size)
        {
          int type = catIDList.toValue(j).toInteger("Type");
          if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
            count++;
          j++;
        }

        int[] rval = new int[count];

        // Do the scan
        j = 0;
        count = 0;
        while (j < size)
        {
          int type = catIDList.toValue(j).toInteger("Type");
          if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
          {
            int childID = catIDList.toValue(j).toInteger("ID");
            rval[count++] = childID;
          }
          j++;
        }

        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(rval.length)+" attached library categories");
        }

        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  /** RootValue version of getPathId.
  */
  protected VolumeAndId getPathId(RootValue rv)
    throws ManifoldCFException, ServiceInterruption
  {
    return rv.getRootValue().getPathId(rv.getRemainderPath());
  }

  /** Rootvalue version of getCategoryId.
  */
  protected int getCategoryId(RootValue rv)
    throws ManifoldCFException, ServiceInterruption
  {
    return rv.getRootValue().getCategoryId(rv.getRemainderPath());
  }

  // Protected static methods

  /** Check if a file or directory should be included, given a document specification.
  *@param filename is the name of the "file".
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected static boolean checkInclude(String filename, Specification documentSpecification)
    throws ManifoldCFException
  {
    // Scan includes to insure we match
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i);
      if (sn.getType().equals("include"))
      {
        String filespec = sn.getAttributeValue("filespec");
        // If it matches, we can exit this loop.
        if (checkMatch(filename,0,filespec))
          break;
      }
      i++;
    }
    if (i == documentSpecification.getChildCount())
      return false;

    // We matched an include.  Now, scan excludes to ditch it if needed.
    i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i);
      if (sn.getType().equals("exclude"))
      {
        String filespec = sn.getAttributeValue("filespec");
        // If it matches, we return false.
        if (checkMatch(filename,0,filespec))
          return false;
      }
      i++;
    }

    // System.out.println("Match!");
    return true;
  }

  /** Check if a file should be ingested, given a document specification.  It is presumed that
  * documents that pass checkInclude() will be checked with this method.
  *@param objID is the file ID.
  *@param documentSpecification is the specification.
  */
  protected boolean checkIngest(LivelinkContext llc, int objID, Specification documentSpecification)
    throws ManifoldCFException
  {
    // Since the only exclusions at this point are not based on file contents, this is a no-op.
    return true;
  }

  /** Check a match between two strings with wildcards.
  *@param sourceMatch is the expanded string (no wildcards)
  *@param sourceIndex is the starting point in the expanded string.
  *@param match is the wildcard-based string.
  *@return true if there is a match.
  */
  protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
  {
    // Note: The java regex stuff looks pretty heavyweight for this purpose.
    // I've opted to try and do a simple recursive version myself, which is not compiled.
    // Basically, the match proceeds by recursive descent through the string, so that all *'s cause
    // recursion.
    boolean caseSensitive = false;

    return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
  }

  /** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
  * strings in their entirety in a matched way.
  *@param caseSensitive is true if file names are case sensitive.
  *@param sourceMatch is the source string (w/o wildcards)
  *@param sourceIndex is the current point in the source string.
  *@param match is the match string (w/wildcards)
  *@param matchIndex is the current point in the match string.
  *@return true if there is a match.
  */
  protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
    String match, int matchIndex)
  {
    // Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
    //      " against '"+match+"' position "+Integer.toString(matchIndex));

    // Match up through the next * we encounter
    while (true)
    {
      // If we've reached the end, it's a match.
      if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
        return true;
      // If one has reached the end but the other hasn't, no match
      if (match.length() == matchIndex)
        return false;
      if (sourceMatch.length() == sourceIndex)
      {
        if (match.charAt(matchIndex) != '*')
          return false;
        matchIndex++;
        continue;
      }
      char x = sourceMatch.charAt(sourceIndex);
      char y = match.charAt(matchIndex);
      if (!caseSensitive)
      {
        if (x >= 'A' && x <= 'Z')
          x -= 'A'-'a';
        if (y >= 'A' && y <= 'Z')
          y -= 'A'-'a';
      }
      if (y == '*')
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
          processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
      }
      if (y == '?' || x == y)
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /** Class for returning volume id/folder id combination on path lookup.
  */
  protected static class VolumeAndId
  {
    protected final int volumeID;
    protected final int folderID;

    public VolumeAndId(int volumeID, int folderID)
    {
      this.volumeID = volumeID;
      this.folderID = folderID;
    }

    public int getVolumeID()
    {
      return volumeID;
    }

    public int getPathId()
    {
      return folderID;
    }
  }

  /** Class that describes a metadata catid and path.
  */
  protected static class MetadataPathItem
  {
    protected final int catID;
    protected final String catName;

    /** Constructor.
    */
    public MetadataPathItem(int catID, String catName)
    {
      this.catID = catID;
      this.catName = catName;
    }

    /** Get the cat ID.
    *@return the id.
    */
    public int getCatID()
    {
      return catID;
    }

    /** Get the cat name.
    *@return the category name path.
    */
    public String getCatName()
    {
      return catName;
    }

  }

  /** Class that describes a metadata catid and attribute set.
  */
  protected static class MetadataItem
  {
    protected final MetadataPathItem pathItem;
    protected final Set<String> attributeNames = new HashSet<String>();

    /** Constructor.
    */
    public MetadataItem(MetadataPathItem pathItem)
    {
      this.pathItem = pathItem;
    }

    /** Add an attribute name.
    */
    public void addAttribute(String attributeName)
    {
      attributeNames.add(attributeName);
    }

    /** Get the path object.
    *@return the object.
    */
    public MetadataPathItem getPathItem()
    {
      return pathItem;
    }

    /** Get an iterator over the attribute names.
    *@return the iterator.
    */
    public Iterator<String> getAttributeNames()
    {
      return attributeNames.iterator();
    }

  }

  /** Class that tracks paths associated with nodes, and also keeps track of the name
  * of the metadata attribute to use for the path.
  */
  protected class SystemMetadataDescription
  {
    // The livelink context
    protected final LivelinkContext llc;
    
    // The path attribute name
    protected final String pathAttributeName;

    // The path separator
    protected final String pathSeparator;
    
    // The node ID to path name mapping (which acts like a cache)
    protected final Map<String,String> pathMap = new HashMap<String,String>();

    // The path name map
    protected final MatchMap matchMap = new MatchMap();

    // Acls
    protected final Set<String> aclMap = new HashSet<String>();
    protected final boolean securityOn;
    
    // Filter string
    protected final String filterString;
    
    protected final Set<String> holder = new HashSet<String>();
    protected final boolean includeAllMetadata;

    /** Constructor */
    public SystemMetadataDescription(LivelinkContext llc, Specification spec)
      throws ManifoldCFException, ServiceInterruption
    {
      this.llc = llc;
      String pathAttributeName = null;
      String pathSeparator = null;
      boolean securityOn = true;
      StringBuilder fsb = new StringBuilder();
      boolean first = true;
      boolean includeAllMetadata = false;
      
      for (int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode n = spec.getChild(i);
        if (n.getType().equals("pathnameattribute"))
        {
          pathAttributeName = n.getAttributeValue("value");
          pathSeparator = n.getAttributeValue("separator");
          if (pathSeparator == null)
            pathSeparator = "/";
        }
        else if (n.getType().equals("pathmap"))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
        else if (n.getType().equals("access"))
        {
          String token = n.getAttributeValue("token");
          aclMap.add(token);
        }
        else if (n.getType().equals("security"))
        {
          String value = n.getAttributeValue("value");
          if (value.equals("on"))
            securityOn = true;
          else if (value.equals("off"))
            securityOn = false;
        }
        else if (n.getType().equals("include"))
        {
          String includeMatch = n.getAttributeValue("filespec");
          if (includeMatch != null)
          {
            // Peel off the extension
            int index = includeMatch.lastIndexOf(".");
            if (index != -1)
            {
              String type = includeMatch.substring(index+1).toLowerCase(Locale.ROOT).replace('*','%');
              if (first)
                first = false;
              else
                fsb.append(" or ");
              fsb.append("lower(FileType) like '").append(type).append("'");
            }
          }
        }
        else if (n.getType().equals("allmetadata"))
        {
          String isAll = n.getAttributeValue("all");
          if (isAll != null && isAll.equals("true"))
            includeAllMetadata = true;
        }
        else if (n.getType().equals("metadata"))
        {
          String category = n.getAttributeValue("category");
          String attributeName = n.getAttributeValue("attribute");
          String isAll = n.getAttributeValue("all");
          if (isAll != null && isAll.equals("true"))
          {
            // Locate all metadata items for the specified category path,
            // and enter them into the array
            getSession();
            String[] attrs = getCategoryAttributes(llc,category);
            if (attrs != null)
            {
              int j = 0;
              while (j < attrs.length)
              {
                attributeName = attrs[j++];
                String metadataName = packCategoryAttribute(category,attributeName);
                holder.add(metadataName);
              }
            }
          }
          else
          {
            String metadataName = packCategoryAttribute(category,attributeName);
            holder.add(metadataName);
          }

        }
      }
      
      this.includeAllMetadata = includeAllMetadata;
      this.pathAttributeName = pathAttributeName;
      this.pathSeparator = pathSeparator;
      this.securityOn = securityOn;
      String filterStringPiece = fsb.toString();
      if (filterStringPiece.length() == 0)
        this.filterString = "0>1";
      else
      {
        StringBuilder sb = new StringBuilder();
        sb.append("SubType=").append(new Integer(LAPI_DOCUMENTS.FOLDERSUBTYPE).toString());
        sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE).toString());
        sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.PROJECTSUBTYPE).toString());
        sb.append(" or (SubType=").append(new Integer(LAPI_DOCUMENTS.DOCUMENTSUBTYPE).toString());
        sb.append(" and (");
        // Walk through the document spec to find the documents that match under the specified root
        // include lower(column)=spec
        sb.append(filterStringPiece);
        sb.append("))");
        this.filterString = sb.toString();
      }
    }

    public boolean includeAllMetadata()
    {
      return includeAllMetadata;
    }
    
    public String[] getMetadataAttributes()
    {
      // Put into an array
      String[] specifiedMetadataAttributes = new String[holder.size()];
      int i = 0;
      for (String attrName : holder)
      {
        specifiedMetadataAttributes[i++] = attrName;
      }
      return specifiedMetadataAttributes;
    }
    
    public String getFilterString()
    {
      return filterString;
    }
    
    public String[] getAcls()
    {
      if (!securityOn)
        return null;

      String[] rval = new String[aclMap.size()];
      int i = 0;
      for (String token : aclMap)
      {
        rval[i++] = token;
      }
      return rval;
    }
  
    /** Get the path attribute name.
    *@return the path attribute name, or null if none specified.
    */
    public String getPathAttributeName()
    {
      return pathAttributeName;
    }

    /** Get the path separator.
    */
    public String getPathSeparator()
    {
      return pathSeparator;
    }
    
    /** Given an identifier, get the translated string that goes into the metadata.
    */
    public String getPathAttributeValue(String documentIdentifier)
      throws ManifoldCFException, ServiceInterruption
    {
      String path = getNodePathString(documentIdentifier);
      if (path == null)
        return null;
      return matchMap.translate(path);
    }

    /** Get the matchmap string.
    */
    public String getMatchMapString()
    {
      return matchMap.toString();
    }
    
    /** For a given node, get its path.
    */
    public String getNodePathString(String documentIdentifier)
      throws ManifoldCFException, ServiceInterruption
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Looking up path for '"+documentIdentifier+"'");
      String path = pathMap.get(documentIdentifier);
      if (path == null)
      {
        // Not yet present.  Look it up, recursively
        String identifierPart = documentIdentifier;
        // Get the current node's name first
        // D = Document; anything else = Folder
        if (identifierPart.startsWith("D") || identifierPart.startsWith("F"))
        {
          // Strip off the letter
          identifierPart = identifierPart.substring(1);
        }
        // See if there's a volume label; if not, use the default.
        int colonPosition = identifierPart.indexOf(":");
        int volumeID;
        int objectID;
        try
        {
          if (colonPosition == -1)
          {
            // Default volume ID
            volumeID = LLENTWK_VOL;
            objectID = Integer.parseInt(identifierPart);
          }
          else
          {
            volumeID = Integer.parseInt(identifierPart.substring(0,colonPosition));
            objectID = Integer.parseInt(identifierPart.substring(colonPosition+1));
          }
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Bad document identifier: "+e.getMessage(),e);
        }

        ObjectInformation objInfo = llc.getObjectInformation(volumeID,objectID);
        if (!objInfo.exists())
        {
          // The document identifier describes a path that does not exist.
          // This is unexpected, but don't die: just log a warning and allow the higher level to deal with it.
          Logging.connectors.warn("Livelink: Bad document identifier: '"+documentIdentifier+"' apparently does not exist, but need to find its path");
          return null;
        }

        // Get the name attribute
        String name = objInfo.getName();
        // Get the parentID attribute
        int parentID = objInfo.getParentId().intValue();
        if (parentID == -1)
          path = name;
        else
        {
          String parentIdentifier = "F"+Integer.toString(volumeID)+":"+Integer.toString(parentID);
          String parentPath = getNodePathString(parentIdentifier);
          if (parentPath == null)
            return null;
          path = parentPath + pathSeparator + name;
        }

        pathMap.put(documentIdentifier,path);
      }

      return path;
    }
  }


  /** Class that manages to find catid's and attribute names that have been specified.
  * This accepts a part of the version string which contains the string-ified metadata
  * spec, rather than pulling it out of the document specification.  That guarantees that
  * the version string actually corresponds to the document that was ingested.
  */
  protected class MetadataDescription
  {
    protected final LivelinkContext llc;
    
    // This is a map of category name to category ID and attributes
    protected final Map<String,MetadataPathItem> categoryMap = new HashMap<String,MetadataPathItem>();

    /** Constructor.
    */
    public MetadataDescription(LivelinkContext llc)
    {
      this.llc = llc;
    }

    /** Iterate over the metadata items represented by the specified chunk of version string.
    *@return an iterator over MetadataItem objects.
    */
    public Iterator<MetadataItem> getItems(String[] metadataItems)
      throws ManifoldCFException, ServiceInterruption
    {
      // This is the map that will be iterated over for a return value.
      // It gets built out of (hopefully cached) data from categoryMap.
      Map<String,MetadataItem> newMap = new HashMap<String,MetadataItem>();

      // Start at root
      ObjectInformation rootValue = null;

      // Walk through string and process each metadata element in turn.
      for (String metadataSpec : metadataItems)
      {
        StringBuilder categoryBuffer = new StringBuilder();
        StringBuilder attributeBuffer = new StringBuilder();
        unpackCategoryAttribute(categoryBuffer,attributeBuffer,metadataSpec);
        String category = categoryBuffer.toString();
        String attributeName = attributeBuffer.toString();

        // If there's already an entry for this category in the return map, use it
        MetadataItem mi = newMap.get(category);
        if (mi == null)
        {
          // Now, look up the node information
          // Convert category to cat id.
          MetadataPathItem item = categoryMap.get(category);
          if (item == null)
          {
            RootValue rv = new RootValue(llc,category);
            if (rootValue == null)
            {
              rootValue = rv.getRootValue();
            }

            // Get the object id of the category the path describes.
            // NOTE: We don't use the RootValue version of getCategoryId because
            // we want to use the cached value of rootValue, if it was around.
            int catObjectID = rootValue.getCategoryId(rv.getRemainderPath());
            if (catObjectID != -1)
            {
              item = new MetadataPathItem(catObjectID,rv.getRemainderPath());
              categoryMap.put(category,item);
            }
          }
          mi = new MetadataItem(item);
          newMap.put(category,mi);
        }
        // Add attribute name to category
        mi.addAttribute(attributeName);
      }

      return newMap.values().iterator();
    }

  }


  /** This class caches the category path strings associated with a given category object identifier.
  * The goal is to allow reasonably speedy lookup of the path name, so we can put it into the metadata part of the
  * version string.
  */
  protected class CategoryPathAccumulator
  {
    // Livelink context
    protected final LivelinkContext llc;
    
    // This is the map from category ID to category path name.
    // It's keyed by an Integer formed from the id, and has String values.
    protected final Map<Integer,String> categoryPathMap = new HashMap<Integer,String>();

    // This is the map from category ID to attribute names.  Keyed
    // by an Integer formed from the id, and has a String[] value.
    protected final Map<Integer,String[]> attributeMap = new HashMap<Integer,String[]>();

    /** Constructor */
    public CategoryPathAccumulator(LivelinkContext llc)
    {
      this.llc = llc;
    }

    /** Get a specified set of packed category paths with attribute names, given the category identifiers */
    public String[] getCategoryPathsAttributeNames(int[] catIDs)
      throws ManifoldCFException, ServiceInterruption
    {
      Set<String> set = new HashSet<String>();
      for (int x : catIDs)
      {
        Integer key = new Integer(x);
        String pathValue = categoryPathMap.get(key);
        if (pathValue == null)
        {
          // Chase the path back up the chain
          pathValue = findPath(key.intValue());
          if (pathValue == null)
            continue;
          categoryPathMap.put(key,pathValue);
        }
        String[] attributeNames = attributeMap.get(key);
        if (attributeNames == null)
        {
          // Get the attributes for this category
          attributeNames = findAttributes(key.intValue());
          if (attributeNames == null)
            continue;
          attributeMap.put(key,attributeNames);
        }
        // Now, put the path and the attributes into the hash.
        for (String attributeName : attributeNames)
        {
          String metadataName = packCategoryAttribute(pathValue,attributeName);
          set.add(metadataName);
        }
      }

      String[] rval = new String[set.size()];
      int i = 0;
      for (String value : set)
      {
        rval[i++] = value;
      }

      return rval;
    }

    /** Find a category path given a category ID */
    protected String findPath(int catID)
      throws ManifoldCFException, ServiceInterruption
    {
      return getObjectPath(llc.getObjectInformation(0,catID));
    }

    /** Get the complete path for an object.
    */
    protected String getObjectPath(ObjectInformation currentObject)
      throws ManifoldCFException, ServiceInterruption
    {
      String path = null;
      while (true)
      {
        if (currentObject.isCategoryWorkspace())
          return CATEGORY_NAME + ((path==null)?"":":" + path);
        else if (currentObject.isEntityWorkspace())
          return ENTWKSPACE_NAME + ((path==null)?"":":" + path);

        if (!currentObject.exists())
        {
          // The document identifier describes a path that does not exist.
          // This is unexpected, but an exception would terminate the job, and we don't want that.
          Logging.connectors.warn("Livelink: Bad identifier found? "+currentObject.toString()+" apparently does not exist, but need to look up its path");
          return null;
        }

        // Get the name attribute
        String name = currentObject.getName();
        if (path == null)
          path = name;
        else
          path = name + "/" + path;

        // Get the parentID attribute
        int parentID = currentObject.getParentId().intValue();
        if (parentID == -1)
        {
          // Oops, hit the top of the path without finding the workspace we're in.
          // No idea where it lives; note this condition and exit.
          Logging.connectors.warn("Livelink: Object ID "+currentObject.toString()+" doesn't seem to live in enterprise or category workspace!  Path I got was '"+path+"'");
          return null;
        }
        currentObject = llc.getObjectInformation(0,parentID);
      }
    }

    /** Find a set of attributes given a category ID */
    protected String[] findAttributes(int catID)
      throws ManifoldCFException, ServiceInterruption
    {
      return getCategoryAttributes(catID);
    }

  }

  /** Class representing a root value object, plus remainder string.
  * This class peels off the workspace name prefix from a path string or
  * attribute string, and finds the right workspace root node and remainder
  * path.
  */
  protected class RootValue
  {
    protected final LivelinkContext llc;
    protected final String workspaceName;
    protected ObjectInformation rootValue = null;
    protected final String remainderPath;

    /** Constructor.
    *@param pathString is the path string.
    */
    public RootValue(LivelinkContext llc, String pathString)
    {
      this.llc = llc;
      int colonPos = pathString.indexOf(":");
      if (colonPos == -1)
      {
        remainderPath = pathString;
        workspaceName = ENTWKSPACE_NAME;
      }
      else
      {
        workspaceName = pathString.substring(0,colonPos);
        remainderPath = pathString.substring(colonPos+1);
      }
    }

    /** Get the path string.
    *@return the path string (without the workspace name prefix).
    */
    public String getRemainderPath()
    {
      return remainderPath;
    }

    /** Get the root node.
    *@return the root node.
    */
    public ObjectInformation getRootValue()
      throws ManifoldCFException, ServiceInterruption
    {
      if (rootValue == null)
      {
        if (workspaceName.equals(CATEGORY_NAME))
          rootValue = llc.getObjectInformation(LLCATWK_VOL,LLCATWK_ID);
        else if (workspaceName.equals(ENTWKSPACE_NAME))
          rootValue = llc.getObjectInformation(LLENTWK_VOL,LLENTWK_ID);
        else
          throw new ManifoldCFException("Bad workspace name: "+workspaceName);
      }
      
      if (!rootValue.exists())
      {
        Logging.connectors.warn("Livelink: Could not get workspace/volume ID!  Retrying...");
        // This cannot mean a real failure; it MUST mean that we have had an intermittent communication hiccup.  So, pass it off as a service interruption.
        throw new ServiceInterruption("Service interruption getting root value",new ManifoldCFException("Could not get workspace/volume id"),System.currentTimeMillis()+60000L,
          System.currentTimeMillis()+600000L,-1,true);
      }

      return rootValue;
    }
  }

  // Here's an interesting note.  All of the LAPI exceptions are subclassed off of RuntimeException.  This makes life
  // hell because there is no superclass exception to capture, and even tweaky server communication issues wind up throwing
  // uncaught RuntimeException's up the stack.
  //
  // To fix this rather bad design, all places that invoke LAPI need to catch RuntimeException and run it through the following
  // method for interpretation and logging.
  //

  /** Interpret runtimeexception to search for livelink API errors.  Throws an appropriately reinterpreted exception, or
  * just returns if the exception indicates that a short-cycle retry attempt should be made.  (In that case, the appropriate
  * wait has been already performed).
  *@param e is the RuntimeException caught
  *@param failIfTimeout is true if, for transient conditions, we want to signal failure if the timeout condition is acheived.
  */
  protected int handleLivelinkRuntimeException(RuntimeException e, int sanityRetryCount, boolean failIfTimeout)
    throws ManifoldCFException, ServiceInterruption
  {
    if (
      e instanceof com.opentext.api.LLHTTPAccessDeniedException ||
      e instanceof com.opentext.api.LLHTTPClientException ||
      e instanceof com.opentext.api.LLHTTPServerException ||
      e instanceof com.opentext.api.LLIndexOutOfBoundsException ||
      e instanceof com.opentext.api.LLNoFieldSpecifiedException ||
      e instanceof com.opentext.api.LLNoValueSpecifiedException ||
      e instanceof com.opentext.api.LLSecurityProviderException ||
      e instanceof com.opentext.api.LLUnknownFieldException ||
      e instanceof NumberFormatException ||
      e instanceof ArrayIndexOutOfBoundsException
    )
    {
      String details = llServer.getErrors();
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e,currentTime + 5*60000L,currentTime+12*60*60000L,-1,failIfTimeout);
    }
    else if (
      e instanceof com.opentext.api.LLBadServerCertificateException ||
      e instanceof com.opentext.api.LLHTTPCGINotFoundException ||
      e instanceof com.opentext.api.LLCouldNotConnectHTTPException ||
      e instanceof com.opentext.api.LLHTTPForbiddenException ||
      e instanceof com.opentext.api.LLHTTPProxyAuthRequiredException ||
      e instanceof com.opentext.api.LLHTTPRedirectionException ||
      e instanceof com.opentext.api.LLUnsupportedAuthMethodException ||
      e instanceof com.opentext.api.LLWebAuthInitException
    )
    {
      String details = llServer.getErrors();
      throw new ManifoldCFException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e);
    }
    else if (e instanceof com.opentext.api.LLSSLNotAvailableException)
    {
      String details = llServer.getErrors();
      throw new ManifoldCFException("Missing llssl.jar error: "+e.getMessage()+((details==null)?"":"; "+details),e);
    }
    else if (e instanceof com.opentext.api.LLIllegalOperationException)
    {
      // This usually means that LAPI has had a minor communication difficulty but hasn't reported it accurately.
      // We *could* throw a ServiceInterruption, but OpenText recommends to just retry almost immediately.
      String details = llServer.getErrors();
      return assessRetry(sanityRetryCount,new ManifoldCFException("Livelink API illegal operation error: "+e.getMessage()+((details==null)?"":"; "+details),e));
    }
    else if (e instanceof com.opentext.api.LLIOException || (e instanceof RuntimeException && e.getClass().getName().startsWith("com.opentext.api.")))
    {
      // Catching obfuscated and unspecified opentext runtime exceptions now too - these come from llssl.jar.  We
      // have to presume these are SSL connection errors; nothing else to go by unfortunately.  UGH.
      
      // Treat this as a transient error; try again in 5 minutes, and only fail after 12 hours of trying

      // LAPI is returning errors that are not terribly explicit, and I don't have control over their wording, so check that server can be resolved by DNS,
      // so that a better error message can be returned.
      try
      {
        InetAddress.getByName(serverName);
      }
      catch (UnknownHostException e2)
      {
        throw new ManifoldCFException("Server name '"+serverName+"' cannot be resolved",e2);
      }

      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption(e.getMessage(),e,currentTime + 5*60000L,currentTime+12*60*60000L,-1,failIfTimeout);
    }
    else
      throw e;
  }

  /** Do a retry, or throw an exception if the retry count has been exhausted
  */
  protected static int assessRetry(int sanityRetryCount, ManifoldCFException e)
    throws ManifoldCFException
  {
    if (sanityRetryCount == 0)
    {
      throw e;
    }

    sanityRetryCount--;

    try
    {
      ManifoldCF.sleep(1000L);
    }
    catch (InterruptedException e2)
    {
      throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
    }
    // Exit the method
    return sanityRetryCount;

  }

  /** This thread performs a LAPI FetchVersion command, streaming the resulting
  * document back through a XThreadInputStream to the invoking thread.
  */
  protected class DocumentReadingThread extends Thread 
  {

    protected Throwable exception = null;
    protected final int volumeID;
    protected final int docID;
    protected final int versionNumber;
    protected final XThreadInputStream stream;
    
    public DocumentReadingThread(int volumeID, int docID, int versionNumber)
    {
      super();
      this.volumeID = volumeID;
      this.docID = docID;
      this.versionNumber = versionNumber;
      this.stream = new XThreadInputStream();
      setDaemon(true);
    }

    @Override
    public void run()
    {
      try
      {
        XThreadOutputStream outputStream = new XThreadOutputStream(stream);
        try 
        {
          int status = LLDocs.FetchVersion(volumeID, docID, versionNumber, outputStream);
          if (status != 0)
          {
            throw new ManifoldCFException("Error retrieving contents of document "+Integer.toString(volumeID)+":"+Integer.toString(docID)+" revision "+versionNumber+" : Status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
          }
        }
        finally
        {
          outputStream.close();
        }
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public InputStream getSafeInputStream() {
      return stream;
    }
    
    public void finishUp()
      throws InterruptedException, ManifoldCFException
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      stream.abort();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof ManifoldCFException)
          throw (ManifoldCFException) thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException) thr;
        else if (thr instanceof Error)
          throw (Error) thr;
        else
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
      }
    }

  }

  /** This thread does the actual socket communication with the server.
  * It's set up so that it can be abandoned at shutdown time.
  *
  * The way it works is as follows:
  * - it starts the transaction
  * - it receives the response, and saves that for the calling class to inspect
  * - it transfers the data part to an input stream provided to the calling class
  * - it shuts the connection down
  *
  * If there is an error, the sequence is aborted, and an exception is recorded
  * for the calling class to examine.
  *
  * The calling class basically accepts the sequence above.  It starts the
  * thread, and tries to get a response code.  If instead an exception is seen,
  * the exception is thrown up the stack.
  */
  protected static class ExecuteMethodThread extends Thread
  {
    /** Client and method, all preconfigured */
    protected final HttpClient httpClient;
    protected final HttpRequestBase executeMethod;
    
    protected HttpResponse response = null;
    protected Throwable responseException = null;
    protected XThreadInputStream threadStream = null;
    protected InputStream bodyStream = null;
    protected boolean streamCreated = false;
    protected Throwable streamException = null;
    protected boolean abortThread = false;

    protected Throwable shutdownException = null;

    protected Throwable generalException = null;
    
    public ExecuteMethodThread(HttpClient httpClient, HttpRequestBase executeMethod)
    {
      super();
      setDaemon(true);
      this.httpClient = httpClient;
      this.executeMethod = executeMethod;
    }

    public void run()
    {
      try
      {
        try
        {
          // Call the execute method appropriately
          synchronized (this)
          {
            if (!abortThread)
            {
              try
              {
                response = httpClient.execute(executeMethod);
              }
              catch (java.net.SocketTimeoutException e)
              {
                responseException = e;
              }
              catch (ConnectTimeoutException e)
              {
                responseException = e;
              }
              catch (InterruptedIOException e)
              {
                throw e;
              }
              catch (Throwable e)
              {
                responseException = e;
              }
              this.notifyAll();
            }
          }
          
          // Start the transfer of the content
          if (responseException == null)
          {
            synchronized (this)
            {
              if (!abortThread)
              {
                try
                {
                  bodyStream = response.getEntity().getContent();
                  if (bodyStream != null)
                  {
                    threadStream = new XThreadInputStream(bodyStream);
                  }
                  streamCreated = true;
                }
                catch (java.net.SocketTimeoutException e)
                {
                  streamException = e;
                }
                catch (ConnectTimeoutException e)
                {
                  streamException = e;
                }
                catch (InterruptedIOException e)
                {
                  throw e;
                }
                catch (Throwable e)
                {
                  streamException = e;
                }
                this.notifyAll();
              }
            }
          }
          
          if (responseException == null && streamException == null)
          {
            if (threadStream != null)
            {
              // Stuff the content until we are done
              threadStream.stuffQueue();
            }
          }
          
        }
        finally
        {
          if (bodyStream != null)
          {
            try
            {
              bodyStream.close();
            }
            catch (IOException e)
            {
            }
            bodyStream = null;
          }
          synchronized (this)
          {
            try
            {
              executeMethod.abort();
            }
            catch (Throwable e)
            {
              shutdownException = e;
            }
            this.notifyAll();
          }
        }
      }
      catch (Throwable e)
      {
        // We catch exceptions here that should ONLY be InterruptedExceptions, as a result of the thread being aborted.
        this.generalException = e;
      }
    }

    public int getResponseCode()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until the response object is there
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
            return response.getStatusLine().getStatusCode();
          wait();
        }
      }
    }

    public long getResponseContentLength()
      throws InterruptedException, IOException, HttpException
    {
      String contentLength = getFirstHeader("Content-Length");
      if (contentLength == null || contentLength.length() == 0)
        return -1L;
      return new Long(contentLength.trim()).longValue();
    }
    
    public String getFirstHeader(String headerName)
      throws InterruptedException, IOException, HttpException
    {
      // Must wait for the response object to appear
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
          {
            Header h = response.getFirstHeader(headerName);
            if (h == null)
              return null;
            return h.getValue();
          }
          wait();
        }
      }
    }

    public InputStream getSafeInputStream()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until stream is created, or until we note an exception was thrown.
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before getting stream");
          checkException(streamException);
          if (streamCreated)
            return threadStream;
          wait();
        }
      }
    }
    
    public void abort()
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      synchronized (this)
      {
        if (streamCreated)
        {
          if (threadStream != null)
            threadStream.abort();
        }
        abortThread = true;
      }
    }
    
    public void finishUp()
      throws InterruptedException
    {
      join();
    }
    
    protected synchronized void checkException(Throwable exception)
      throws IOException, HttpException
    {
      if (exception != null)
      {
        // Throw the current exception, but clear it, so no further throwing is possible on the same problem.
        Throwable e = exception;
        if (e instanceof IOException)
          throw (IOException)e;
        else if (e instanceof HttpException)
          throw (HttpException)e;
        else if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof Error)
          throw (Error)e;
        else
          throw new RuntimeException("Unhandled exception of type: "+e.getClass().getName(),e);
      }
    }

  }
  
}