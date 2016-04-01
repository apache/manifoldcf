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
package org.apache.manifoldcf.crawler.connectors.wiki;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.connectorcommon.common.*;
import org.apache.manifoldcf.core.util.URLEncoder;


import org.xml.sax.Attributes;

import org.apache.manifoldcf.agents.common.XMLStream;
import org.apache.manifoldcf.agents.common.XMLContext;
import org.apache.manifoldcf.agents.common.XMLStringContext;
import org.apache.manifoldcf.agents.common.XMLFileContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.NTCredentials;
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
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpHost;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;
import org.apache.http.ParseException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;


import java.util.concurrent.TimeUnit;


/** This is the repository connector for a wiki.
*/
public class WikiConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

  /**
   * Deny access token for default authority
   */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  // Activities that we know about
  
  /** Fetch activity */
  protected final static String ACTIVITY_FETCH = "fetch document";

  /** Activities list */
  protected static final String[] activitiesList = new String[]{ACTIVITY_FETCH};

  /** Has setup been called? */
  protected boolean hasBeenSetup = false;
  
  /** Server name */
  protected String server = null;
  
  /** Base URL */
  protected String baseURL = null;
  
  /** The user-agent for this connector instance */
  protected String userAgent = null;

  // Server login parameters
  protected String serverLogin = null;
  protected String serverPass = null;
  protected String serverDomain = null;
  
  // Basic auth parameters
  protected String accessRealm = null;
  protected String accessUser = null;
  protected String accessPassword = null;
  
  // Proxy parameters
  protected String proxyHost = null;
  protected String proxyPort = null;
  protected String proxyDomain = null;
  protected String proxyUsername = null;
  protected String proxyPassword = null;
  
  /** Connection management */
  protected HttpClientConnectionManager connectionManager = null;

  protected HttpClient httpClient = null;
  
  // Current host name
  private static String currentHost = null;
  static
  {
    // Find the current host name
    try
    {
      java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

      // Get hostname
      currentHost = addr.getHostName();
    }
    catch (java.net.UnknownHostException e)
    {
    }
  }

  /** Constructor.
  */
  public WikiConnector()
  {
  }

  /** List the activities we might report on.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** For any given document, list the bins that it is a member of.
  */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    // Return the host name
    return new String[]{server};
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);

    server = params.getParameter(WikiConfig.PARAM_SERVER);
    serverLogin = params.getParameter(WikiConfig.PARAM_LOGIN);
    serverPass = params.getObfuscatedParameter(WikiConfig.PARAM_PASSWORD);
    serverDomain = params.getParameter(WikiConfig.PARAM_DOMAIN);
    accessRealm = params.getParameter(WikiConfig.PARAM_ACCESSREALM);
    accessUser = params.getParameter(WikiConfig.PARAM_ACCESSUSER);
    accessPassword = params.getObfuscatedParameter(WikiConfig.PARAM_ACCESSPASSWORD);

    proxyHost = params.getParameter(WikiConfig.PARAM_PROXYHOST);
    proxyPort = params.getParameter(WikiConfig.PARAM_PROXYPORT);
    proxyDomain = params.getParameter(WikiConfig.PARAM_PROXYDOMAIN);
    proxyUsername = params.getParameter(WikiConfig.PARAM_PROXYUSERNAME);
    proxyPassword = params.getObfuscatedParameter(WikiConfig.PARAM_PROXYPASSWORD);
  }

  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
  {
    if (hasBeenSetup == false)
    {
      String emailAddress = params.getParameter(WikiConfig.PARAM_EMAIL);
      if (emailAddress != null)
        userAgent = "Mozilla/5.0 (ApacheManifoldCFWikiReader; "+((emailAddress==null)?"":emailAddress)+")";
      else
        userAgent = null;

      String protocol = params.getParameter(WikiConfig.PARAM_PROTOCOL);
      if (protocol == null || protocol.length() == 0)
        protocol = "http";
      String portString = params.getParameter(WikiConfig.PARAM_PORT);
      if (portString == null || portString.length() == 0)
        portString = null;
      String path = params.getParameter(WikiConfig.PARAM_PATH);
      if (path == null)
        path = "/w";
      
      baseURL = protocol + "://" + server + ((portString!=null)?":" + portString:"") + path + "/api.php?format=xml&";

      int socketTimeout = 900000;
      int connectionTimeout = 300000;

      javax.net.ssl.SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
      SSLConnectionSocketFactory myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeout),
        NoopHostnameVerifier.INSTANCE);

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

      if (accessUser != null && accessUser.length() > 0 && accessPassword != null)
      {
        Credentials credentials = new UsernamePasswordCredentials(accessUser, accessPassword);
        if (accessRealm != null && accessRealm.length() > 0)
          credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, accessRealm), credentials);
        else
          credentialsProvider.setCredentials(AuthScope.ANY, credentials);
      }

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(socketTimeout)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);
          
      // If there's a proxy, set that too.
      if (proxyHost != null && proxyHost.length() > 0)
      {

        int proxyPortInt;
        if (proxyPort != null && proxyPort.length() > 0)
        {
          try
          {
            proxyPortInt = Integer.parseInt(proxyPort);
          }
          catch (NumberFormatException e)
          {
            throw new ManifoldCFException("Bad number: "+e.getMessage(),e);
          }
        }
        else
          proxyPortInt = 8080;

        // Configure proxy authentication
        if (proxyUsername != null && proxyUsername.length() > 0)
        {
          if (proxyPassword == null)
            proxyPassword = "";
          if (proxyDomain == null)
            proxyDomain = "";

          credentialsProvider.setCredentials(
            new AuthScope(proxyHost, proxyPortInt),
            new NTCredentials(proxyUsername, proxyPassword, currentHost, proxyDomain));
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPortInt);
        requestBuilder.setProxy(proxy);
      }

      httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setSSLSocketFactory(myFactory)
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .build();

      /*
      BasicHttpParams params = new BasicHttpParams();
      params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,true);
      params.setIntParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE,socketTimeout);
      params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
      params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,true);
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,socketTimeout);
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeout);
      params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS,true);
      DefaultHttpClient localHttpClient = new DefaultHttpClient(connectionManager,params);
      // No retries
      localHttpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler()
        {
          public boolean retryRequest(
            IOException exception,
            int executionCount,
            HttpContext context)
          {
            return false;
          }
       
        });
      */
      
      loginToAPI();
      
      hasBeenSetup = true;
    }
  }

  /** Log in via the Wiki API.
  * Call this method whenever login is apparently needed.
  *@return true if the login was successful, false otherwise.
  */
  protected boolean loginToAPI()
    throws ManifoldCFException, ServiceInterruption
  {
    if (serverLogin == null || serverLogin.length() == 0)
      return false;

    // Grab the httpclient, and use the same one throughout.
    HttpClient client = httpClient;
    
    // First step in login process: get the token
    Map<String, String> loginParams = new HashMap<String, String>();

    String token = null;
    
    String loginURL = baseURL + "action=login";
    loginParams.put("action", "login");
    loginParams.put("lgname", serverLogin);
    loginParams.put("lgpassword", serverPass);
    if (serverDomain != null && !"".equals(serverDomain)) {
      loginParams.put("lgdomain", serverDomain);
    }

    APILoginResult result = new APILoginResult();
        
    try {
      HttpRequestBase method = getInitializedPostMethod(loginURL,loginParams);
      ExecuteAPILoginThread t = new ExecuteAPILoginThread(client, method, result);
      try {
        t.start();
        token = t.finishUp();
      } catch (ManifoldCFException e) {
        t.interrupt();
        throw e;
      } catch (ServiceInterruption e) {
        t.interrupt();
        throw e;
      } catch (IOException e) {
        t.interrupt();
        throw e;
      } catch (HttpException e) {
	t.interrupt();
	throw e;
      } catch (InterruptedException e) {
        t.interrupt();
        // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
        throw e;
      }
      
      if (result.result)
        return true;
      
      // Grab the token from the first call
      if (token == null)
      {
        // We don't need a token, we just couldn't log in
        Logging.connectors.debug("WIKI API login error: '" + result.reason + "'");
        throw new ManifoldCFException("WIKI API login error: " + result.reason, null, ManifoldCFException.REPOSITORY_CONNECTION_ERROR);
      }
      
    } catch (InterruptedException e) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (ManifoldCFException e) {
      throw e;
    } catch (java.net.SocketTimeoutException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login timed out reading from the Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (java.net.SocketException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login received a socket error reading from Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (ConnectTimeoutException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login connection timed out reading from Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (InterruptedIOException e) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      throw new ManifoldCFException("Login had an IO failure: " + e.getMessage(), e);
    } catch (HttpException e) {
      throw new ManifoldCFException("Login had an Http exception: "+e.getMessage(), e);
    }

    // First request is finished.  Fire off the second one.
    
    loginParams.put("lgtoken", token);
    
    try {
      HttpRequestBase method = getInitializedPostMethod(loginURL,loginParams);
      ExecuteTokenAPILoginThread t = new ExecuteTokenAPILoginThread(httpClient, method, result);
      try {
        t.start();
        t.finishUp();
      } catch (ManifoldCFException e) {
        t.interrupt();
        throw e;
      } catch (ServiceInterruption e) {
        t.interrupt();
        throw e;
      } catch (IOException e) {
        t.interrupt();
        throw e;
      } catch (HttpException e) {
	t.interrupt();
	throw e;
      } catch (InterruptedException e) {
        t.interrupt();
        // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
        throw e;
      }

      // Fall through
    } catch (InterruptedException e) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (ManifoldCFException e) {
      throw e;
    } catch (java.net.SocketTimeoutException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login timed out reading from the Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (java.net.SocketException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login received a socket error reading from Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (ConnectTimeoutException e) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Login connection timed out reading from Wiki server: " + e.getMessage(), e, currentTime + 300000L, currentTime + 12L * 60000L, -1, false);
    } catch (InterruptedIOException e) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      throw new ManifoldCFException("Login had an IO failure: " + e.getMessage(), e);
    } catch (HttpException e) {
      throw new ManifoldCFException("Login had an Http exception: "+e.getMessage(), e);
    }
    
    // Check result
    if (!result.result)
    {
      Logging.connectors.debug("WIKI API login error: '" + result.reason + "'");
      throw new ManifoldCFException("WIKI API login error: " + result.reason, null, ManifoldCFException.REPOSITORY_CONNECTION_ERROR);
    }
    return true;
  }

  /**
   * Thread to execute a "login" operation. This thread both executes
   * the operation and parses the result.
   */
  protected class ExecuteAPILoginThread extends Thread {

    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected APILoginResult result;
    protected Throwable exception = null;
    protected String token = null;

    public ExecuteAPILoginThread(HttpClient client, HttpRequestBase executeMethod, APILoginResult result) {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.result = result;
    }

    public void run() {
      try {
        // Call the execute method appropriately
	HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200) {
          throw new ManifoldCFException("Unexpected HTTP response code " + rval.getStatusLine().getStatusCode() + ": " + readResponseAsString(rval));
        }

        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try {
          // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
          //<api>
          //  <login
          //    result="NeedToken"
          //    token="b5780b6e2f27e20b450921d9461010b4"
          //    cookieprefix="enwiki"
          //    sessionid="17ab96bd8ffbe8ca58a78657a918558e"
          //  />
          //</api>
          XMLStream x = new XMLStream(false);
          WikiLoginAPIContext c = new WikiLoginAPIContext(x,result);
          x.setContext(c);
          try {
            try {
              x.parse(is);
              token = c.getToken();
            }
	    catch (InterruptedIOException e)
	    {
	      throw e;
	    }
            catch (IOException e)
            {
              long time = System.currentTimeMillis();
              throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
            }
          } finally {
            x.cleanup();
          }
        } finally {
          try {
            is.close();
          } catch (IllegalStateException e) {
            // Ignore this error
          }
        }
      } catch (Throwable e) {
        this.exception = e;
      } finally {
	executeMethod.abort();
      }
    }

    public String finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException {
      join();
      handleException(exception);
      return token;
    }
    
  }

  /**
   * Class representing the "api" context of a "login" response
   */
  protected class WikiLoginAPIContext extends SingleLevelContext {

    protected APILoginResult result;
    protected String token = null;
    
    public WikiLoginAPIContext(XMLStream theStream, APILoginResult result) {
      super(theStream, "api");
      this.result = result;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts) {
      return new WikiLoginAPIResultAPIContext(theStream, namespaceURI, localName, qName, atts, result);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException {
      token = ((WikiLoginAPIResultAPIContext)child).getToken();
    }
    
    public String getToken()
    {
      return token;
    }
  }

  /**
   * Class representing the "api/result" context of a "login"
   * response
   */
  protected class WikiLoginAPIResultAPIContext extends BaseProcessingContext {

    protected APILoginResult result;
    protected String token = null;
    
    public WikiLoginAPIResultAPIContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, APILoginResult result) {
      super(theStream, namespaceURI, localName, qName, atts);
      this.result = result;
    }

    @Override
    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption {
      if (qName.equals("login")) {
        String loginResult = atts.getValue("result");
        if ("NeedToken".equals(loginResult)) {
          token = atts.getValue("token");
        } else if ("Success".equals(loginResult)) {
          result.result = true;
        } else {
          result.reason = loginResult;
        }
      }
      return super.beginTag(namespaceURI, localName, qName, atts);
    }
  
    public String getToken()
    {
      return token;
    }
  }

  protected static class APILoginResult {

    public boolean result = false;
    public String reason = "";
  }

  /**
   * Thread to finish a "login" operation. This thread both executes
   * the operation and parses the result.
   */
  protected class ExecuteTokenAPILoginThread extends Thread {

    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected APILoginResult result;

    public ExecuteTokenAPILoginThread(HttpClient client, HttpRequestBase executeMethod, APILoginResult result) {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.result = result;
    }

    public void run() {
      try {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200) {
          throw new ManifoldCFException("Unexpected HTTP response code " + rval.getStatusLine().getStatusCode() + ": " + readResponseAsString(rval));
        }

        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try {
          // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
          //<api>
          //  <login
          //    result="NeedToken"
          //    token="b5780b6e2f27e20b450921d9461010b4"
          //    cookieprefix="enwiki"
          //    sessionid="17ab96bd8ffbe8ca58a78657a918558e"
          //  />
          //</api>
          XMLStream x = new XMLStream(false);
          WikiTokenLoginAPIContext c = new WikiTokenLoginAPIContext(x,result);
          x.setContext(c);
          try {
            try {
              x.parse(is);
            }
            catch (IOException e)
            {
              long time = System.currentTimeMillis();
              throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
            }
          } finally {
            x.cleanup();
          }
        } finally {
          try {
            is.close();
          } catch (IllegalStateException e) {
            // Ignore this error
          }
        }
      } catch (Throwable e) {
        this.exception = e;
      } finally {
	executeMethod.abort();
      }
    }

    public void finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException {
      join();
      handleException(exception);
    }
  }

  /**
   * Class representing the "api" context of a "login" response
   */
  protected class WikiTokenLoginAPIContext extends SingleLevelContext {

    protected APILoginResult result;
    
    public WikiTokenLoginAPIContext(XMLStream theStream, APILoginResult result) {
      super(theStream, "api");
      this.result = result;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts) {
      return new WikiTokenLoginAPIResultAPIContext(theStream, namespaceURI, localName, qName, atts, result);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException {
    }
    
  }

  /**
   * Class representing the "api/result" context of a "login"
   * response
   */
  protected class WikiTokenLoginAPIResultAPIContext extends BaseProcessingContext {

    protected APILoginResult result;
    
    public WikiTokenLoginAPIResultAPIContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, APILoginResult result) {
      super(theStream, namespaceURI, localName, qName, atts);
      this.result = result;
    }

    @Override
    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption {
      if (qName.equals("login")) {
        String loginResult = atts.getValue("result");
        if ("Success".equals(loginResult)) {
          result.result = true;
        } else {
          result.reason = loginResult;
        }
      }
      return super.beginTag(namespaceURI, localName, qName, atts);
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
      hasBeenSetup = false;
      performCheck();
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
    if (connectionManager != null)
      connectionManager.closeIdleConnections(60000L,TimeUnit.MILLISECONDS);
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    hasBeenSetup = false;
    server = null;
    serverLogin = null;
    serverPass = null;
    serverDomain = null;
    accessUser = null;
    accessPassword = null;
    accessRealm = null;
    proxyHost = null;
    proxyPort = null;
    proxyDomain = null;
    proxyUsername = null;
    proxyPassword = null;
    baseURL = null;
    userAgent = null;

    if (httpClient != null) {
      httpClient = null;
    }

    if (connectionManager != null)
    {
      connectionManager.shutdown();
      connectionManager = null;
    }

    super.disconnect();
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    return 20;
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
    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else
    {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }

    // Scan specification nodes and extract prefixes and namespaces
    boolean seenAny = false;
    for (int i = 0 ; i < spec.getChildCount() ; i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX))
      {
        String namespace = sn.getAttributeValue(WikiConfig.ATTR_NAMESPACE);
        String titleprefix = sn.getAttributeValue(WikiConfig.ATTR_TITLEPREFIX);
        listAllPages(activities,namespace,titleprefix,startTime,seedTime);
        seenAny = true;
      }
    }
    if (!seenAny)
      listAllPages(activities,null,null,startTime,seedTime);
    
    return new Long(seedTime).toString();
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
    // Forced acls
    String[] acls = getAcls(spec);

    Map<String,String> versions = new HashMap<String,String>();
    getTimestamps(documentIdentifiers,versions,activities);
    
    List<String> fetchDocuments = new ArrayList<String>();
    for (String documentIdentifier : documentIdentifiers)
    {
      String versionString = versions.get(documentIdentifier);
      if (versionString == null)
      {
        activities.deleteDocument(documentIdentifier);
        continue;
      }
      
      if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
        continue;
      
      fetchDocuments.add(documentIdentifier);
    }
    
    if (fetchDocuments.size() == 0)
      return;
    
    String[] fetchDocumentsArray = fetchDocuments.toArray(new String[0]);
    Map<String,String> urls = new HashMap<String,String>();
    getDocURLs(documentIdentifiers,urls);
    for (String documentIdentifier : fetchDocumentsArray)
    {
      String url = urls.get(documentIdentifier);
      String versionString = versions.get(documentIdentifier);
      if (url != null)
        getDocInfo(documentIdentifier, versionString, url, activities, acls);
      else
        activities.noDocument(documentIdentifier,versionString);
    }

  }
  
  /**
   * Grab forced acl out of document specification.
   *
   * @param spec is the document specification.
   * @return the acls.
   */
  protected static String[] getAcls(Specification spec) {
    Set<String> aclMap = new HashSet<String>();
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("access")) {
        String token = sn.getAttributeValue("token");
        aclMap.add(token);
      }
    }

    String[] rval = new String[aclMap.size()];
    int j = 0;
    for (String acl : aclMap)
    {
      rval[j++] = acl;
    }
    return rval;
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
    tabsArray.add(Messages.getString(locale,"WikiConnector.Server"));
    tabsArray.add(Messages.getString(locale,"WikiConnector.Email"));
    tabsArray.add(Messages.getString(locale,"WikiConnector.Proxy"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.email.value != \"\" && editconnection.email.value.indexOf(\"@\") == -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.NeedAValidEmailAddress")+"\");\n"+
"    editconnection.email.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.WikiServerPortMustBeAValidInteger")+"\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverpath.value != \"\" && editconnection.serverpath.value.indexOf(\"/\") != 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.PathMustStartWithACharacter")+"\");\n"+
"    editconnection.serverpath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.proxyport.value != \"\" && !isInteger(editconnection.proxyport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.ProxyPortMustBeAValidInteger")+"\");\n"+
"    editconnection.proxyport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.email.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.EmailAddressRequiredToBeIncludedInAllRequestHeaders")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.Email")+"\");\n"+
"    editconnection.email.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.PleaseSupplyAValidWikiServerName")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.Server")+"\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.WikiServerPortMustBeAValidInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.Server")+"\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverpath.value != \"\" && editconnection.serverpath.value.indexOf(\"/\") != 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.PathMustStartWithACharacter")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.Server")+"\");\n"+
"    editconnection.serverpath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.proxyport.value != \"\" && !isInteger(editconnection.proxyport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.ProxyPortMustBeAValidInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"WikiConnector.Proxy")+"\");\n"+
"    editconnection.proxyport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String email = parameters.getParameter(WikiConfig.PARAM_EMAIL);
    if (email == null)
      email = "";

    String protocol = parameters.getParameter(WikiConfig.PARAM_PROTOCOL);
    if (protocol == null)
      protocol = "http";
		
    String server = parameters.getParameter(WikiConfig.PARAM_SERVER);
    if (server == null)
      server = "";

    String port = parameters.getParameter(WikiConfig.PARAM_PORT);
    if (port == null)
      port = "";

    String path = parameters.getParameter(WikiConfig.PARAM_PATH);
    if (path == null)
      path = "/w";

    // Server login parameters

    String login = parameters.getParameter(WikiConfig.PARAM_LOGIN);
    if (login == null) {
      login = "";
    }
    String pass = parameters.getObfuscatedParameter(WikiConfig.PARAM_PASSWORD);
    if (pass == null) {
      pass = "";
    } else {
      pass = out.mapPasswordToKey(pass);
    }
    String domain = parameters.getParameter(WikiConfig.PARAM_DOMAIN);
    if (domain == null) {
      domain = "";
    }

    // Basic auth parameters
    
    String accessRealm = parameters.getParameter(WikiConfig.PARAM_ACCESSREALM);
    if (accessRealm == null)
      accessRealm = "";
    
    String accessUser = parameters.getParameter(WikiConfig.PARAM_ACCESSUSER);
    if (accessUser == null)
      accessUser = "";
    
    String accessPassword = parameters.getObfuscatedParameter(WikiConfig.PARAM_ACCESSPASSWORD);
    if (accessPassword == null)
      accessPassword = "";
    else
      accessPassword = out.mapPasswordToKey(accessPassword);

    // Proxy parameters
    
    String proxyHost = parameters.getParameter(WikiConfig.PARAM_PROXYHOST);
    if (proxyHost == null)
      proxyHost = "";
    
    String proxyPort = parameters.getParameter(WikiConfig.PARAM_PROXYPORT);
    if (proxyPort == null)
      proxyPort = "";
    
    String proxyDomain = parameters.getParameter(WikiConfig.PARAM_PROXYDOMAIN);
    if (proxyDomain == null)
      proxyDomain = "";
    
    String proxyUsername = parameters.getParameter(WikiConfig.PARAM_PROXYUSERNAME);
    if (proxyUsername == null)
      proxyUsername = "";
    
    String proxyPassword = parameters.getObfuscatedParameter(WikiConfig.PARAM_PROXYPASSWORD);
    if (proxyPassword == null)
      proxyPassword = "";
    else
      proxyPassword = out.mapPasswordToKey(proxyPassword);

    // Proxy tab
    if (tabName.equals(Messages.getString(locale,"WikiConnector.Proxy")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ProxyHostColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"proxyhost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyHost)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ProxyPortColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"proxyport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyPort)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ProxyDomainColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"proxydomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyDomain)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ProxyUsernameColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"16\" name=\"proxyusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyUsername)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ProxyPasswordColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"16\" name=\"proxypassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyPassword)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"proxyhost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyHost)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyPort)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxydomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyDomain)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyUsername)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxypassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(proxyPassword)+"\"/>\n"
      );
    }
    
    // Email tab
    if (tabName.equals(Messages.getString(locale,"WikiConnector.Email")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.EmailAddressToContactColon") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"email\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(email)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"email\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(email)+"\"/>\n"
      );
    }

    if (tabName.equals(Messages.getString(locale,"WikiConnector.Server")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.Protocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverprotocol\">\n"+
"        <option value=\"http\""+(protocol.equals("http")?" selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\""+(protocol.equals("https")?" selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.ServerName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"servername\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.Port") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverport\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.PathName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverpath\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.ServerLogin") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverlogin\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(login) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.ServerPassword") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverpass\" type=\"password\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pass) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.ServerDomain") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverdomain\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(domain) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.AccessUser") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"accessuser\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessUser) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.AccessPassword") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"accesspassword\" type=\"password\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessPassword) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.AccessRealm") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"accessrealm\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessRealm) + "\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"serverprotocol\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(protocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"servername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverpath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverlogin\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(login) + "\"/>\n"+
"<input type=\"hidden\" name=\"serverpass\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pass) + "\"/>\n"+
"<input type=\"hidden\" name=\"serverdomain\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(domain) + "\"/>\n"+
"<input type=\"hidden\" name=\"accessuser\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessUser) + "\"/>\n"+
"<input type=\"hidden\" name=\"accesspassword\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessPassword) + "\"/>\n"+
"<input type=\"hidden\" name=\"accessrealm\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(accessRealm) + "\"/>\n"
      );
    }

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
    String email = variableContext.getParameter("email");
    if (email != null)
      parameters.setParameter(WikiConfig.PARAM_EMAIL,email);

    String protocol = variableContext.getParameter("serverprotocol");
    if (protocol != null)
      parameters.setParameter(WikiConfig.PARAM_PROTOCOL,protocol);
		
    String server = variableContext.getParameter("servername");
    if (server != null)
      parameters.setParameter(WikiConfig.PARAM_SERVER,server);

    String port = variableContext.getParameter("serverport");
    if (port != null)
      parameters.setParameter(WikiConfig.PARAM_PORT,port);

    String path = variableContext.getParameter("serverpath");
    if (path != null)
      parameters.setParameter(WikiConfig.PARAM_PATH,path);

    String login = variableContext.getParameter("serverlogin");
    if (login != null) {
      parameters.setParameter(WikiConfig.PARAM_LOGIN, login);
    }

    String pass = variableContext.getParameter("serverpass");
    if (pass != null) {
      parameters.setObfuscatedParameter(WikiConfig.PARAM_PASSWORD, variableContext.mapKeyToPassword(pass));
    }

    String domain = variableContext.getParameter("serverdomain");
    if (domain != null) {
      parameters.setParameter(WikiConfig.PARAM_DOMAIN, domain);
    }

    String accessUser = variableContext.getParameter("accessuser");
    if (accessUser != null) {
      parameters.setParameter(WikiConfig.PARAM_ACCESSUSER, accessUser);
    }

    String accessPassword = variableContext.getParameter("accesspassword");
    if (accessPassword != null) {
      parameters.setObfuscatedParameter(WikiConfig.PARAM_ACCESSPASSWORD, variableContext.mapKeyToPassword(accessPassword));
    }

    String accessRealm = variableContext.getParameter("accessrealm");
    if (accessRealm != null) {
      parameters.setParameter(WikiConfig.PARAM_ACCESSREALM, accessRealm);
    }

    String proxyHost = variableContext.getParameter("proxyhost");
    if (proxyHost != null) {
      parameters.setParameter(WikiConfig.PARAM_PROXYHOST, proxyHost);
    }
    
    String proxyPort = variableContext.getParameter("proxyport");
    if (proxyPort != null) {
      parameters.setParameter(WikiConfig.PARAM_PROXYPORT, proxyPort);
    }

    String proxyDomain = variableContext.getParameter("proxydomain");
    if (proxyDomain != null) {
      parameters.setParameter(WikiConfig.PARAM_PROXYDOMAIN, proxyDomain);
    }
    
    String proxyUsername = variableContext.getParameter("proxyusername");
    if (proxyUsername != null) {
      parameters.setParameter(WikiConfig.PARAM_PROXYUSERNAME, proxyUsername);
    }

    String proxyPassword = variableContext.getParameter("proxypassword");
    if (proxyPassword != null) {
      parameters.setObfuscatedParameter(WikiConfig.PARAM_PROXYPASSWORD, variableContext.mapKeyToPassword(proxyPassword));
    }

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
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>"+Messages.getBodyString(locale,"WikiConnector.Parameters")+"</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+Messages.getBodyString(locale,"WikiConnector.certificates")+"&gt;</nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );

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
    tabsArray.add(Messages.getString(locale,"WikiConnector.NamespaceAndTitles"));
    tabsArray.add(Messages.getString(locale, "WikiConnector.Security"));
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function "+seqPrefix+"NsDelete(k)\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"nsop_\"+k, \"Delete\", \""+seqPrefix+"ns_\"+k);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"NsAdd(k)\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"nsop\", \"Add\", \""+seqPrefix+"ns_\"+k);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale, "WikiConnector.TypeInAnAccessToken") + "\");\n"+
"    editjob."+seqPrefix+"spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    if (tabName.equals(Messages.getString(locale,"WikiConnector.NamespaceAndTitles")) && connectionSequenceNumber == actualSequenceNumber)
    {
      boolean seenAny = false;
      // Output table column headers
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.NamespaceAndTitles2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.Namespace") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.TitlePrefix") + "</nobr></td>\n"+
"        </tr>\n"
      );

      int k = 0;
      for (int i = 0 ; i < ds.getChildCount() ; i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX))
        {
          String namespace = sn.getAttributeValue(WikiConfig.ATTR_NAMESPACE);
          String titlePrefix = sn.getAttributeValue(WikiConfig.ATTR_TITLEPREFIX);
          
          String nsOpName = seqPrefix+"nsop_"+k;
          String nsNsName = seqPrefix+"nsnsname_"+k;
          String nsTitlePrefix = seqPrefix+"nstitleprefix_"+k;
          out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+seqPrefix+"ns_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+nsOpName+"\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\""+nsNsName+"\" value=\""+((namespace==null)?"":org.apache.manifoldcf.ui.util.Encoder.attributeEscape(namespace))+"\"/>\n"+
"              <input type=\"hidden\" name=\""+nsTitlePrefix+"\" value=\""+((titlePrefix==null)?"":org.apache.manifoldcf.ui.util.Encoder.attributeEscape(titlePrefix))+"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WikiConnector.Delete") + "\" onClick='Javascript:"+seqPrefix+"NsDelete("+Integer.toString(k)+")' alt=\""+Messages.getAttributeString(locale,"WikiConnector.DeleteNamespaceTitle")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+((namespace==null)?"(default)":org.apache.manifoldcf.ui.util.Encoder.bodyEscape(namespace))+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+((titlePrefix==null)?"(all titles)":org.apache.manifoldcf.ui.util.Encoder.bodyEscape(titlePrefix))+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          k++;
        }
      }

      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td colspan=\"3\" class=\"formmessage\">" + Messages.getBodyString(locale,"WikiConnector.NoSpecification") + "</td></tr>\n"
        );
      }

      // Add area
      out.print(
"        <tr class=\"formrow\"><td colspan=\"4\" class=\"formseparator\"><hr/></td></tr>\n"
      );

      // Obtain the list of namespaces
      Map<String,String> namespaces = new HashMap<String,String>();
      try
      {
        getNamespaces(namespaces);
        // Extract and sort the names we're going to present
        String[] nameSpaceNames = new String[namespaces.size()];
        Iterator<String> keyIter = namespaces.keySet().iterator();
        int j = 0;
        while (keyIter.hasNext())
        {
          nameSpaceNames[j++] = keyIter.next();
        }
        java.util.Arrays.sort(nameSpaceNames);
      
        out.print(
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+seqPrefix+"ns_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+seqPrefix+"nsop\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\""+seqPrefix+"nscount\" value=\""+Integer.toString(k)+"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WikiConnector.Add") + "\" onClick='Javascript:"+seqPrefix+"NsAdd("+Integer.toString(k)+")' alt=\"" + Messages.getAttributeString(locale,"WikiConnector.AddNamespacePrefix") + "\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <select name=\""+seqPrefix+"nsnsname\">\n"+
"                <option value=\"\" selected=\"true\">-- " + Messages.getBodyString(locale,"WikiConnector.UseDefault") + " --</option>\n"
        );
        for (int l = 0 ; l < nameSpaceNames.length ; l++)
        {
          String prettyName = nameSpaceNames[l];
          String canonicalName = namespaces.get(prettyName);
          out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(canonicalName)+"\">"+
  org.apache.manifoldcf.ui.util.Encoder.bodyEscape(prettyName)+"</option>\n"
          );
        }
        out.print(
"              </select>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"text\" name=\""+seqPrefix+"nstitleprefix\" size=\"16\" value=\"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
      }
      catch (ServiceInterruption e)
      {
        out.print(
"        <tr class=\"formrow\"><td colspan=\"3\" class=\"formmessage\">" + Messages.getBodyString(locale,"WikiConnector.TransientError") + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td></tr>\n"
        );
      }

      out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );

    }
    else
    {
      // Generate hiddens
      int k = 0;
      for (int i = 0 ; i < ds.getChildCount() ; i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX))
        {
          String namespace = sn.getAttributeValue(WikiConfig.ATTR_NAMESPACE);
          String titlePrefix = sn.getAttributeValue(WikiConfig.ATTR_TITLEPREFIX);
          
          String nsNsName = seqPrefix+"nsnsname_"+k;
          String nsTitlePrefix = seqPrefix+"nstitleprefix_"+k;

          out.print(
"<input type=\"hidden\" name=\""+nsNsName+"\" value=\""+((namespace == null)?"":org.apache.manifoldcf.ui.util.Encoder.attributeEscape(namespace))+"\"/>\n"+
"<input type=\"hidden\" name=\""+nsTitlePrefix+"\" value=\""+((titlePrefix == null)?"":org.apache.manifoldcf.ui.util.Encoder.attributeEscape(titlePrefix))+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"nscount\" value=\""+new Integer(k)+"\"/>\n"
      );
    }
    
    if (tabName.equals(Messages.getString(locale, "WikiConnector.Security")) && connectionSequenceNumber == actualSequenceNumber) 
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Go through forced ACL
      int i = 0;
      int k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String accessOpName = seqPrefix + "accessop" + accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"" + accessOpName + "\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\"" + seqPrefix + "spectoken" + accessDescription + "\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token) + "\"/>\n"+
"      <a name=\"" + seqPrefix + "token_" + Integer.toString(k) + "\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "WikiConnector.Delete") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\"" + accessOpName + "\",\"Delete\",\""+seqPrefix+"token_" + Integer.toString(k) + "\")' alt=\"" + Messages.getAttributeString(locale, "WikiConnector.Delete") + Integer.toString(k) + "\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token) + "\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0) {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale, "WikiConnector.NoAccessTokensPresent") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"accessop\" value=\"\"/>\n"+
"      <a name=\"" + seqPrefix + "token_" + Integer.toString(k) + "\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "WikiConnector.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecAddToken(\""+seqPrefix+"token_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "WikiConnector.Add") + "\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Finally, go through forced ACL
      int i = 0;
      int k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\"" + seqPrefix + "spectoken" + accessDescription + "\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token) + "\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n"
      );
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

    String countString = variableContext.getParameter(seqPrefix+"nscount");
    if (countString != null)
    {
      for (int i = 0 ; i < ds.getChildCount() ; i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX))
          ds.removeChild(i);
        else
          i++;
      }
      
      int nsCount = Integer.parseInt(countString);
      for (int i = 0 ; i < nsCount ; i++)
      {
        String nsOpName = seqPrefix+"nsop_"+i;
        String nsNsName = seqPrefix+"nsnsname_"+i;
        String nsTitlePrefix = seqPrefix+"nstitleprefix_"+i;
        
        String nsOp = variableContext.getParameter(nsOpName);
        if (nsOp == null || !nsOp.equals("Delete"))
        {
          String namespaceName = variableContext.getParameter(nsNsName);
          if (namespaceName != null && namespaceName.length() == 0)
            namespaceName = null;
          String titlePrefix = variableContext.getParameter(nsTitlePrefix);
          if (titlePrefix != null && titlePrefix.length() == 0)
            titlePrefix = null;
          SpecificationNode sn = new SpecificationNode(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX);
          if (namespaceName != null)
            sn.setAttribute(WikiConfig.ATTR_NAMESPACE,namespaceName);
          if (titlePrefix != null)
            sn.setAttribute(WikiConfig.ATTR_TITLEPREFIX,titlePrefix);
          ds.addChild(ds.getChildCount(),sn);
        }
      }
      
      String newOp = variableContext.getParameter(seqPrefix+"nsop");
      if (newOp != null && newOp.equals("Add"))
      {
        String namespaceName = variableContext.getParameter(seqPrefix+"nsnsname");
        if (namespaceName != null && namespaceName.length() == 0)
          namespaceName = null;
        String titlePrefix = variableContext.getParameter(seqPrefix+"nstitleprefix");
        if (titlePrefix != null && titlePrefix.length() == 0)
          titlePrefix = null;
        SpecificationNode sn = new SpecificationNode(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX);
        if (namespaceName != null)
          sn.setAttribute(WikiConfig.ATTR_NAMESPACE,namespaceName);
        if (titlePrefix != null)
          sn.setAttribute(WikiConfig.ATTR_TITLEPREFIX,titlePrefix);
        ds.addChild(ds.getChildCount(),sn);
      }
    }

    String xc = variableContext.getParameter(seqPrefix+"tokencount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access")) {
          ds.removeChild(i);
        } else {
          i++;
        }
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String accessDescription = "_" + Integer.toString(i);
        String accessOpName = seqPrefix +"accessop" + accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix + "spectoken" + accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessSpec);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add")) {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessspec);
        ds.addChild(ds.getChildCount(), node);
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
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.NamespaceAndTitles2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.Namespace") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WikiConnector.TitlePrefix") + "</nobr></td>\n"+
"        </tr>\n"
    );

    int k = 0;
    for (int i = 0 ; i < ds.getChildCount() ; i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(WikiConfig.NODE_NAMESPACE_TITLE_PREFIX))
      {
        String namespace = sn.getAttributeValue(WikiConfig.ATTR_NAMESPACE);
        String titlePrefix = sn.getAttributeValue(WikiConfig.ATTR_TITLEPREFIX);
        out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+((namespace==null)?"(default)":org.apache.manifoldcf.ui.util.Encoder.bodyEscape(namespace))+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+((titlePrefix==null)?"(all documents)":org.apache.manifoldcf.ui.util.Encoder.bodyEscape(titlePrefix))+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        k++;
      }
    }
    
    if (k == 0)
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"2\">" + Messages.getBodyString(locale,"WikiConnector.AllDefaultNamespaceDocumentsIncluded") + "</td></tr>\n"
      );
    
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
    );

    // Go through looking for access tokens
    boolean seenAny = false;
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access")) {
        if (seenAny == false) {
          out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.AccessTokens") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token) + "<br/>\n"
        );
      }
    }

    if (seenAny) {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    } else {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale, "WikiConnector.NoAccessTokensSpecified") + "</nobr></td></tr>\n"
      );
    }

    out.print(
"</table>\n"
    );
  }

  // Protected static classes and methods

  /** Create and initialize an HttpRequestBase */
  protected HttpRequestBase getInitializedGetMethod(String URL)
    throws IOException
  {
    HttpGet method = new HttpGet(URL);
    if (userAgent != null)
      method.setHeader(new BasicHeader("User-Agent",userAgent));
    method.setHeader(new BasicHeader("Accept","*/*"));
    return method;
  }

  /** Create an initialize a post method */
  protected HttpRequestBase getInitializedPostMethod(String URL, Map<String,String> params)
    throws IOException
  {
    HttpPost method = new HttpPost(URL);
    if (userAgent != null)
      method.setHeader(new BasicHeader("User-Agent",userAgent));

    List<NameValuePair> pairs = new ArrayList<NameValuePair>();
    
    for (String key : params.keySet()) {
      pairs.add(new BasicNameValuePair(key, params.get(key)));
    }
    
    method.setEntity(new UrlEncodedFormEntity(pairs, StandardCharsets.UTF_8));
    
    return method;
  }
  
  // -- Methods and classes to perform a "check" operation. --

  /** Do the check operation.  This throws an exception if anything is wrong.
  */
  protected void performCheck()
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    boolean loginAttempted = false;
    while (true)
    {
      HttpClient client = httpClient;
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getCheckURL());
        ExecuteCheckThread t = new ExecuteCheckThread(client,executeMethod);
        try
        {
          t.start();
          if (!t.finishUp() || loginAttempted)
            return;
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Fetch test timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Fetch test received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Fetch test connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Fetch test had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("Fetch test had Http exception: "+e.getMessage(),e);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
      // Back around...
    }
  }

  /** Get a URL for a check operation.
  */
  protected String getCheckURL()
    throws ManifoldCFException
  {
    return baseURL + "action=query&list=allpages&aplimit=1";
  }
  
  /** Thread to execute a "check" operation.  This thread both executes the operation and parses the result. */
  protected static class ExecuteCheckThread extends Thread
  {
    protected final HttpClient client;
    protected final HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected boolean loginNeeded = false;

    public ExecuteCheckThread(HttpClient client, HttpRequestBase executeMethod)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
          throw new ManifoldCFException("Unexpected HTTP response code: "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          loginNeeded = parseCheckResponse(is);
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }

  }

  /** Parse check response, e.g.:
  * <api xmlns="http://www.mediawiki.org/xml/api/">
  *   <query>
  *     <allpages>
  *       <p pageid="19839654" ns="0" title="Kre&#039;fey" />
  *     </allpages>
  *   </query>
  *   <query-continue>
  *     <allpages apfrom="Krea" />
  *   </query-continue>
  * </api>
  */
  protected static boolean parseCheckResponse(InputStream is)
    throws ManifoldCFException, ServiceInterruption
  {
    // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
    XMLStream x = new XMLStream(false);
    WikiCheckAPIContext c = new WikiCheckAPIContext(x);
    x.setContext(c);
    try
    {
      try
      {
        x.parse(is);
        if (c.isLoginRequired())
          return true;
        if (!c.hasResponse())
          throw new ManifoldCFException("Valid API response not detected");
        return false;
      }
      catch (IOException e)
      {
        long time = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
      }
    }
    finally
    {
      x.cleanup();
    }
  }

  /** Class representing the "api" context of a "check" response */
  protected static class WikiCheckAPIContext extends SingleLevelContext
  {
    boolean responseSeen = false;
    boolean needLogin = false;
    
    public WikiCheckAPIContext(XMLStream theStream)
    {
      super(theStream,"api");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiCheckQueryContext(theStream,namespaceURI,localName,qName,atts);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      responseSeen |= ((WikiCheckQueryContext)child).hasResponse();
      needLogin |= ((WikiCheckQueryContext)child).isLoginRequired();
    }
    
    public boolean hasResponse()
    {
      return responseSeen;
    }

    public boolean isLoginRequired()
    {
      return needLogin;
    }
    
  }

  /** Class representing the "api/query" context of a "check" response */
  protected static class WikiCheckQueryContext extends SingleLevelErrorContext
  {
    protected boolean responseSeen = false;
    
    public WikiCheckQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiCheckAllPagesContext(theStream,namespaceURI,localName,qName,atts);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      responseSeen |= ((WikiCheckAllPagesContext)child).hasResponse();
    }

    public boolean hasResponse()
    {
      return responseSeen;
    }
    
  }

  /** Class recognizing the "api/query/allpages" context of a "check" response */
  protected static class WikiCheckAllPagesContext extends SingleLevelContext
  {
    protected boolean responseSeen = false;
    
    public WikiCheckAllPagesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"allpages");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiCheckPContext(theStream,namespaceURI,localName,qName,atts);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      responseSeen |= true;
    }

    public boolean hasResponse()
    {
      return responseSeen;
    }
    
  }
  
  /** Class representing the "api/query/allpages/p" context of a "check" response */
  protected static class WikiCheckPContext extends BaseProcessingContext
  {
    public WikiCheckPContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }
  }

  // -- Methods and classes to perform a "list pages" operation. --

  /** Perform a series of listPages() operations, so that we fully obtain the documents we're looking for even though
  * we're limited to 500 of them per request.
  */
  protected void listAllPages(ISeedingActivity activities, String namespace, String prefix, long startTime, long endTime)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    String lastTitle = null;
    while (true)
    {
      activities.checkJobStillActive();
      
      // Start with the last title seen in the previous round. 
      String newLastTitle = executeListPagesViaThread(lastTitle,namespace,prefix,activities);
      if (newLastTitle == null)
        break;
      lastTitle = newLastTitle;
    }
  }
  
  /** Execute a listPages() operation via a thread.  Returns the last page title. */
  protected String executeListPagesViaThread(String startPageTitle, String namespace, String prefix, ISeedingActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    boolean loginAttempted = false;
    while (true)
    {
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getListPagesURL(startPageTitle,namespace,prefix));
        XThreadStringBuffer pageBuffer = new XThreadStringBuffer();
        ExecuteListPagesThread t = new ExecuteListPagesThread(httpClient,executeMethod,pageBuffer,startPageTitle);
        try
        {
          t.start();

          // Pick up the pages, and add them to the activities, before we join with the child thread.
          while (true)
          {
            // The only kind of exceptions this can throw are going to shut the process down.
            String pageID = pageBuffer.fetch();
            if (pageID ==  null)
              break;
            // Add the pageID to the queue
            activities.addSeedDocument(pageID);
          }
          
          if (!t.finishUp() || loginAttempted)
            return t.getLastPageTitle();
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
        finally
        {
          // Make SURE buffer is dead, otherwise child thread may well hang waiting on it
          pageBuffer.abandon();
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("ListPages timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("ListPages received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("ListPages connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("ListPages had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("ListPages had an HTTP exception: "+e.getMessage(),e);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
      // Back around...

    }
    return null;
  }

  /** Create a URL to obtain the next 500 pages.
  */
  protected String getListPagesURL(String startingTitle, String namespace, String prefix)
    throws ManifoldCFException
  {
      return baseURL + "action=query&list=allpages" +
        ((prefix != null)?"&apprefix="+URLEncoder.encode(prefix):"") +
        ((namespace != null)?"&apnamespace="+URLEncoder.encode(namespace):"") +
        ((startingTitle!=null)?"&apfrom="+URLEncoder.encode(startingTitle):"") +
        "&aplimit=500";
  }

  protected static class ReturnString
  {
    public String returnValue = null;
  }
  
  /** Thread to execute a list pages operation */
  protected static class ExecuteListPagesThread extends Thread
  {
    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected XThreadStringBuffer pageBuffer;
    protected String lastPageTitle = null;
    protected String startPageTitle;
    protected boolean loginNeeded = false;

    public ExecuteListPagesThread(HttpClient client, HttpRequestBase executeMethod, XThreadStringBuffer pageBuffer, String startPageTitle)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.pageBuffer = pageBuffer;
      this.startPageTitle = startPageTitle;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
          throw new ManifoldCFException("Unexpected HTTP response code: "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          ReturnString returnString = new ReturnString();
          loginNeeded = parseListPagesResponse(is,pageBuffer,startPageTitle,returnString);
          lastPageTitle = returnString.returnValue;
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
        pageBuffer.signalDone();
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }
    
    public String getLastPageTitle()
    {
      return lastPageTitle;
    }
    
  }

  /** Parse list output, e.g.:
  * <api xmlns="http://www.mediawiki.org/xml/api/">
  *   <query>
  *     <allpages>
  *       <p pageid="19839654" ns="0" title="Kre&#039;fey" />
  *       <p pageid="30955295" ns="0" title="Kre-O" />
  *       <p pageid="14773725" ns="0" title="Kre8tiveworkz" />
  *       <p pageid="19219017" ns="0" title="Kre M&#039;Baye" />
  *       <p pageid="19319577" ns="0" title="Kre Mbaye" />
  *     </allpages>
  *   </query>
  *   <query-continue>
  *     <allpages apfrom="Krea" />
  *   </query-continue>
  * </api>
  */
  protected static boolean parseListPagesResponse(InputStream is, XThreadStringBuffer buffer, String startPageTitle, ReturnString lastTitle)
    throws ManifoldCFException, ServiceInterruption
  {
    // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
    XMLStream x = new XMLStream(false);
    WikiListPagesAPIContext c = new WikiListPagesAPIContext(x,buffer,startPageTitle);
    x.setContext(c);
    try
    {
      try
      {
        x.parse(is);
        String lastTitleString = c.getLastTitle();
        lastTitle.returnValue = lastTitleString;
        return c.isLoginRequired();
      }
      catch (IOException e)
      {
        long time = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
      }
    }
    finally
    {
      x.cleanup();
    }
  }

  /** Class representing the "api" context of a "list all pages" response */
  protected static class WikiListPagesAPIContext extends SingleLevelContext
  {
    protected String lastTitle = null;
    protected XThreadStringBuffer buffer;
    protected String startPageTitle;
    protected boolean loginNeeded = false;
    
    public WikiListPagesAPIContext(XMLStream theStream, XThreadStringBuffer buffer, String startPageTitle)
    {
      super(theStream,"api");
      this.buffer = buffer;
      this.startPageTitle = startPageTitle;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiListPagesQueryContext(theStream,namespaceURI,localName,qName,atts,buffer,startPageTitle);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      lastTitle = ((WikiListPagesQueryContext)child).getLastTitle();
      loginNeeded |= ((WikiListPagesQueryContext)child).isLoginRequired();
    }
    
    public String getLastTitle()
    {
      return lastTitle;
    }

    public boolean isLoginRequired()
    {
      return loginNeeded;
    }

  }

  /** Class representing the "api/query" context of a "list all pages" response */
  protected static class WikiListPagesQueryContext extends SingleLevelErrorContext
  {
    protected String lastTitle = null;
    protected XThreadStringBuffer buffer;
    protected String startPageTitle;
    
    public WikiListPagesQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      XThreadStringBuffer buffer, String startPageTitle)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
      this.buffer = buffer;
      this.startPageTitle = startPageTitle;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiListPagesAllPagesContext(theStream,namespaceURI,localName,qName,atts,buffer,startPageTitle);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      lastTitle = ((WikiListPagesAllPagesContext)child).getLastTitle();
    }
    
    public String getLastTitle()
    {
      return lastTitle;
    }
    
  }

  /** Class recognizing the "api/query/allpages" context of a "list all pages" response */
  protected static class WikiListPagesAllPagesContext extends SingleLevelContext
  {
    protected String lastTitle = null;
    protected XThreadStringBuffer buffer;
    protected String startPageTitle;
    
    public WikiListPagesAllPagesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      XThreadStringBuffer buffer, String startPageTitle)
    {
      super(theStream,namespaceURI,localName,qName,atts,"allpages");
      this.buffer = buffer;
      this.startPageTitle = startPageTitle;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      // When we recognize allpages, we need to look for <p> records.
      return new WikiListPagesPContext(theStream,namespaceURI,localName,qName,atts,buffer,startPageTitle);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      // Update the last title from all the <p> records we saw.
      lastTitle = ((WikiListPagesPContext)child).getLastTitle();
    }
    
    public String getLastTitle()
    {
      return lastTitle;
    }
    
  }
  
  /** Class representing the "api/query/allpages/p" context of a "list all pages" response */
  protected static class WikiListPagesPContext extends BaseProcessingContext
  {
    protected String lastTitle = null;
    protected XThreadStringBuffer buffer;
    protected String startPageTitle;
    
    public WikiListPagesPContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      XThreadStringBuffer buffer, String startPageTitle)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.buffer = buffer;
      this.startPageTitle = startPageTitle;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("p"))
      {
        String currentTitle = atts.getValue("title");
        // Skip the record that matches the start page title (just pretend it isn't there)
        if (startPageTitle == null || !currentTitle.equals(startPageTitle))
        {
          lastTitle = currentTitle;
          String pageID = atts.getValue("pageid");
          // Add the discovered page id to the page buffer
          try
          {
            buffer.add(pageID);
          }
          catch (InterruptedException e)
          {
            throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
        }
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
    public String getLastTitle()
    {
      return lastTitle;
    }
  }

  // -- Methods and classes to perform a "get doc urls" operation. --
  
  protected void getDocURLs(String[] documentIdentifiers, Map<String,String> urls)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    boolean loginAttempted = false;
    while (true)
    {
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getGetDocURLsURL(documentIdentifiers));
        ExecuteGetDocURLsThread t = new ExecuteGetDocURLsThread(httpClient,executeMethod,urls);
        try
        {
          t.start();
          if (!t.finishUp() || loginAttempted)
            return;
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("URL fetch timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("URL fetch received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("URL fetch connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("URL fetch had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("URL fetch had an HTTP exception: "+e.getMessage(),e);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
    }
  }
  
  /** Create a URL to obtain multiple page's urls, given the page IDs.
  */
  protected String getGetDocURLsURL(String[] documentIdentifiers)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < documentIdentifiers.length ; i++)
    {
      if (i > 0)
        sb.append("|");
      sb.append(documentIdentifiers[i]);
    }
      return baseURL + "action=query&prop=info&pageids="+URLEncoder.encode(sb.toString())+"&inprop=url";
  }

  /** Thread to execute a "get timestamp" operation.  This thread both executes the operation and parses the result. */
  protected static class ExecuteGetDocURLsThread extends Thread
  {
    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected Map<String,String> urls;
    protected boolean loginNeeded = false;

    public ExecuteGetDocURLsThread(HttpClient client, HttpRequestBase executeMethod, Map<String,String> urls)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.urls = urls;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
          throw new ManifoldCFException("Unexpected HTTP response code: "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          loginNeeded = parseGetDocURLsResponse(is,urls);
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }
    
  }

  /** This method parses a response like the following:
  * <api>
  *   <query>
  *     <pages>
  *       <page pageid="27697087" ns="0" title="API" fullurl="..."/>
  *     </pages>
  *   </query>
  * </api>
  */
  protected static boolean parseGetDocURLsResponse(InputStream is, Map<String,String> urls)
    throws ManifoldCFException, ServiceInterruption
  {
    // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
    XMLStream x = new XMLStream(false);
    WikiGetDocURLsAPIContext c = new WikiGetDocURLsAPIContext(x,urls);
    x.setContext(c);
    try
    {
      try
      {
        x.parse(is);
        return c.isLoginRequired();
      }
      catch (IOException e)
      {
        long time = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
      }
    }
    finally
    {
      x.cleanup();
    }
  }

  /** Class representing the "api" context of a "get timestamp" response */
  protected static class WikiGetDocURLsAPIContext extends SingleLevelContext
  {
    protected Map<String,String> urls;
    protected boolean loginNeeded = false;
    
    public WikiGetDocURLsAPIContext(XMLStream theStream, Map<String,String> urls)
    {
      super(theStream,"api");
      this.urls = urls;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocURLsQueryContext(theStream,namespaceURI,localName,qName,atts,urls);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      loginNeeded |= ((WikiGetDocURLsQueryContext)child).isLoginRequired();
    }

    public boolean isLoginRequired()
    {
      return loginNeeded;
    }

  }

  /** Class representing the "api/query" context of a "get timestamp" response */
  protected static class WikiGetDocURLsQueryContext extends SingleLevelErrorContext
  {
    protected Map<String,String> urls;
    
    public WikiGetDocURLsQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> urls)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
      this.urls = urls;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocURLsPagesContext(theStream,namespaceURI,localName,qName,atts,urls);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
    
  }

  /** Class looking for the "api/query/pages" context of a "get timestamp" response */
  protected static class WikiGetDocURLsPagesContext extends SingleLevelContext
  {
    protected Map<String,String> urls;
    
    public WikiGetDocURLsPagesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> urls)
    {
      super(theStream,namespaceURI,localName,qName,atts,"pages");
      this.urls = urls;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocURLsPageContext(theStream,namespaceURI,localName,qName,atts,urls);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
  }

  /** Class looking for the "api/query/pages/page" context of a "get timestamp" response */
  protected static class WikiGetDocURLsPageContext extends BaseProcessingContext
  {
    protected Map<String,String> urls;
    
    public WikiGetDocURLsPageContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> urls)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.urls = urls;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("page"))
      {
        String pageID = atts.getValue("pageid");
        String fullURL = atts.getValue("fullurl");
        if (pageID != null && fullURL != null)
          urls.put(pageID,fullURL);
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
  }

  // -- Methods and classes to perform a "get Timestamp" operation. --

  /** Obtain document versions for a set of documents.
  */
  protected void getTimestamps(String[] documentIdentifiers, Map<String,String> versions, IProcessActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    boolean loginAttempted = false;
    while (true)
    {
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getGetTimestampURL(documentIdentifiers));
        ExecuteGetTimestampThread t = new ExecuteGetTimestampThread(httpClient,executeMethod,versions);
        try
        {
          t.start();
          if (!t.finishUp() || loginAttempted)
            return;
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Version fetch timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Version fetch received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Version fetch connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Version fetch had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("Version fetch had an HTTP exception: "+e.getMessage(),e);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
    }
  }

  /** Create a URL to obtain multiple page's timestamps, given the page IDs.
  */
  protected String getGetTimestampURL(String[] documentIdentifiers)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < documentIdentifiers.length ; i++)
    {
      if (i > 0)
        sb.append("|");
      sb.append(documentIdentifiers[i]);
    }
      return baseURL + "action=query&prop=revisions&pageids="+URLEncoder.encode(sb.toString())+"&rvprop=timestamp";
  }

  /** Thread to execute a "get timestamp" operation.  This thread both executes the operation and parses the result. */
  protected static class ExecuteGetTimestampThread extends Thread
  {
    protected final HttpClient client;
    protected final HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected final Map<String,String> versions;
    protected boolean loginNeeded = false;

    public ExecuteGetTimestampThread(HttpClient client, HttpRequestBase executeMethod, Map<String,String> versions)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.versions = versions;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
          throw new ManifoldCFException("Unexpected HTTP response code: "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          loginNeeded = parseGetTimestampResponse(is,versions);
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }
  }

  /** This method parses a response like the following:
  * <api>
  *   <query>
  *     <pages>
  *       <page pageid="27697087" ns="0" title="API">
  *         <revisions>
  *           <rev user="Graham87" timestamp="2010-06-13T08:41:17Z" />
  *         </revisions>
  *       </page>
  *     </pages>
  *   </query>
  * </api>
  */
  protected static boolean parseGetTimestampResponse(InputStream is, Map<String,String> versions)
    throws ManifoldCFException, ServiceInterruption
  {
    // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
    XMLStream x = new XMLStream(false);
    WikiGetTimestampAPIContext c = new WikiGetTimestampAPIContext(x,versions);
    x.setContext(c);
    try
    {
      try
      {
        x.parse(is);
        return c.isLoginRequired();
      }
      catch (IOException e)
      {
        long time = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
      }
    }
    finally
    {
      x.cleanup();
    }
  }

  /** Class representing the "api" context of a "get timestamp" response */
  protected static class WikiGetTimestampAPIContext extends SingleLevelContext
  {
    protected Map<String,String> versions;
    protected boolean loginNeeded = false;
    
    public WikiGetTimestampAPIContext(XMLStream theStream, Map<String,String> versions)
    {
      super(theStream,"api");
      this.versions = versions;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetTimestampQueryContext(theStream,namespaceURI,localName,qName,atts,versions);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      loginNeeded |= ((WikiGetTimestampQueryContext)child).isLoginRequired();
    }

    public boolean isLoginRequired()
    {
      return loginNeeded;
    }
  }

  /** Class representing the "api/query" context of a "get timestamp" response */
  protected static class WikiGetTimestampQueryContext extends SingleLevelErrorContext
  {
    protected Map<String,String> versions;
    
    public WikiGetTimestampQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> versions)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
      this.versions = versions;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetTimestampPagesContext(theStream,namespaceURI,localName,qName,atts,versions);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
    
  }

  /** Class looking for the "api/query/pages" context of a "get timestamp" response */
  protected static class WikiGetTimestampPagesContext extends SingleLevelContext
  {
    protected Map<String,String> versions;
    
    public WikiGetTimestampPagesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> versions)
    {
      super(theStream,namespaceURI,localName,qName,atts,"pages");
      this.versions = versions;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetTimestampPageContext(theStream,namespaceURI,localName,qName,atts,versions);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
  }

  /** Class looking for the "api/query/pages/page" context of a "get timestamp" response */
  protected static class WikiGetTimestampPageContext extends BaseProcessingContext
  {
    protected String pageID = null;
    protected Map<String,String> versions;
    
    public WikiGetTimestampPageContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> versions)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.versions = versions;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("page"))
      {
        pageID = atts.getValue("pageid");
        return new WikiGetTimestampRevisionsContext(theStream,namespaceURI,localName,qName,atts);
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
    protected void endTag()
      throws ManifoldCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();

      if (theTag.equals("page"))
      {
        String lastRevEdit = ((WikiGetTimestampRevisionsContext)theContext).getTimestamp();
        versions.put(pageID,lastRevEdit);
      }
      else
        super.endTag();
    }
    
  }

  /** Class looking for the "api/query/pages/page/revisions" context of a "get timestamp" response */
  protected static class WikiGetTimestampRevisionsContext extends SingleLevelContext
  {
    protected String timestamp = null;
    
    public WikiGetTimestampRevisionsContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"revisions");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetTimestampRevContext(theStream,namespaceURI,localName,qName,atts);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      WikiGetTimestampRevContext rc = (WikiGetTimestampRevContext)child;
      if (timestamp == null)
        timestamp = rc.getTimestamp();
    }
    
    public String getTimestamp()
    {
      return timestamp;
    }
  }

  /** Class looking for the "api/query/pages/page/revisions/rev" context of a "get timestamp" response */
  protected static class WikiGetTimestampRevContext extends BaseProcessingContext
  {
    protected String timestamp = null;
    
    public WikiGetTimestampRevContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("rev"))
        timestamp = atts.getValue("timestamp");
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
    public String getTimestamp()
    {
      return timestamp;
    }
  }
  
  // -- Methods and classes to perform a "get namespaces" operation. --
  
  /** Obtain the set of namespaces, as a map keyed by the canonical namespace name
  * where the value is the descriptive name.
  */
  protected void getNamespaces(Map<String,String> namespaces)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    boolean loginAttempted = false;
    while (true)
    {
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getGetNamespacesURL());
        ExecuteGetNamespacesThread t = new ExecuteGetNamespacesThread(httpClient,executeMethod,namespaces);
        try
        {
          t.start();
          if (!t.finishUp() || loginAttempted)
            return;
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get namespaces timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get namespaces received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get namespaces connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Get namespaces had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("Get namespaces had an HTTP exception: "+e.getMessage(),e);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
    }
  }
  
  /** Thread to execute a "get namespaces" operation.  This thread both executes the operation and parses the result. */
  protected static class ExecuteGetNamespacesThread extends Thread
  {
    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected Map<String,String> namespaces;
    protected boolean loginNeeded = false;

    public ExecuteGetNamespacesThread(HttpClient client, HttpRequestBase executeMethod, Map<String,String> namespaces)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.namespaces = namespaces;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
        {
          throw new ManifoldCFException("Unexpected HTTP response code "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        }

        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
          //<api>
          //  <query>
          //    <namespaces>
          //      <ns id="-2" case="first-letter" canonical="Media" xml:space="preserve">Media</ns>
          //      <ns id="-1" case="first-letter" canonical="Special" xml:space="preserve">Special</ns>
          //      <ns id="0" case="first-letter" subpages="" content="" xml:space="preserve" />
          //      <ns id="1" case="first-letter" subpages="" canonical="Talk" xml:space="preserve">Talk</ns>
          //      <ns id="2" case="first-letter" subpages="" canonical="User" xml:space="preserve">User</ns>
          //      <ns id="90" case="first-letter" canonical="Thread" xml:space="preserve">Thread</ns>
          //      <ns id="91" case="first-letter" canonical="Thread talk" xml:space="preserve">Thread talk</ns>
          //    </namespaces>
          //  </query>
          //</api>
          XMLStream x = new XMLStream(false);
          WikiGetNamespacesAPIContext c = new WikiGetNamespacesAPIContext(x,namespaces);
          x.setContext(c);
          try
          {
            try
            {
              x.parse(is);
              loginNeeded = c.isLoginRequired();
            }
            catch (IOException e)
            {
              long time = System.currentTimeMillis();
              throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
            }
          }
          finally
          {
            x.cleanup();
          }
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }
  }

  /** Create a URL to obtain the namespaces.
  */
  protected String getGetNamespacesURL()
    throws ManifoldCFException
  {
    return baseURL + "action=query&meta=siteinfo&siprop=namespaces";
  }

  /** Class representing the "api" context of a "get namespaces" response */
  protected static class WikiGetNamespacesAPIContext extends SingleLevelContext
  {
    protected Map<String,String> namespaces;
    protected boolean loginNeeded = false;
    
    public WikiGetNamespacesAPIContext(XMLStream theStream, Map<String,String> namespaces)
    {
      super(theStream,"api");
      this.namespaces = namespaces;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetNamespacesQueryContext(theStream,namespaceURI,localName,qName,atts,namespaces);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      loginNeeded |= ((WikiGetNamespacesQueryContext)child).isLoginRequired();
    }

    public boolean isLoginRequired()
    {
      return loginNeeded;
    }

  }

  /** Class representing the "api/query" context of a "get namespaces" response */
  protected static class WikiGetNamespacesQueryContext extends SingleLevelErrorContext
  {
    protected Map<String,String> namespaces;
    
    public WikiGetNamespacesQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> namespaces)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
      this.namespaces = namespaces;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetNamespacesNamespacesContext(theStream,namespaceURI,localName,qName,atts,namespaces);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
    
  }

  /** Class representing the "api/query/namespaces" context of a "get namespaces" response */
  protected static class WikiGetNamespacesNamespacesContext extends SingleLevelContext
  {
    protected Map<String,String> namespaces;
    
    public WikiGetNamespacesNamespacesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> namespaces)
    {
      super(theStream,namespaceURI,localName,qName,atts,"namespaces");
      this.namespaces = namespaces;
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetNamespacesNsContext(theStream,namespaceURI,localName,qName,atts,namespaces);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
    }
    
  }

  /** Class representing the "api/query/pages/page" context of a "get doc info" response */
  protected static class WikiGetNamespacesNsContext extends BaseProcessingContext
  {
    protected Map<String,String> namespaces;
    protected String canonical = null;
	protected String nsid = null;
    
    public WikiGetNamespacesNsContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts,
      Map<String,String> namespaces)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.namespaces = namespaces;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("ns"))
      {
        nsid = atts.getValue("id");
        canonical = atts.getValue("canonical");
        if (canonical != null && nsid != null)
          return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
    protected void endTag()
      throws ManifoldCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("ns"))
      {
        if (canonical != null && nsid != null)
        {
          // Pull down the data
          XMLStringContext sc = (XMLStringContext)theContext;
          namespaces.put(sc.getValue(),nsid);
        }
        else
          super.endTag();
      }
      else
        super.endTag();
    }

    protected void tagCleanup()
      throws ManifoldCFException
    {
    }
   
  }
  
  // -- Methods and classes to perform a "get Docinfo" operation. --

  /** Get document info and index the document.
  */
  protected void getDocInfo(String documentIdentifier, String documentVersion, String fullURL, IProcessActivity activities, String[] allowACL)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    boolean loginAttempted = false;
    while (true)
    {
      String statusCode = "UNKNOWN";
      String errorMessage = null;
      long startTime = System.currentTimeMillis();
      long dataSize = 0L;
      
      try
      {
	HttpRequestBase executeMethod = getInitializedGetMethod(getGetDocInfoURL(documentIdentifier));
        ExecuteGetDocInfoThread t = new ExecuteGetDocInfoThread(httpClient,executeMethod,documentIdentifier);
        try
        {
          t.start();
          boolean needsLogin = t.finishUp();
          // Fetch all the data we need from the thread, and do the indexing.
          File contentFile = t.getContentFile();
          if (contentFile != null)
          {
            statusCode = "OK";
            try
            {
              String author = t.getAuthor();
              String comment = t.getComment();
              String title = t.getTitle();
              String lastModified = t.getLastModified();
              Date modifiedDate = (lastModified==null)?null:DateParser.parseISO8601Date(lastModified);
              String contentType = "text/plain";
              dataSize = contentFile.length();

              if (!activities.checkURLIndexable(fullURL))
              {
                activities.noDocument(documentIdentifier,documentVersion);
                statusCode = activities.EXCLUDED_URL;
                errorMessage = "Downstream pipeline excluded document URL ('"+fullURL+"')";
                return;
              }
              
              if (!activities.checkLengthIndexable(dataSize))
              {
                activities.noDocument(documentIdentifier,documentVersion);
                statusCode = activities.EXCLUDED_LENGTH;
                errorMessage = "Downstream pipeline excluded document length ("+dataSize+")";
                return;
              }
              
              if (!activities.checkMimeTypeIndexable(contentType))
              {
                activities.noDocument(documentIdentifier,documentVersion);
                statusCode = activities.EXCLUDED_MIMETYPE;
                errorMessage = "Downstream pipeline excluded document mime type ('"+contentType+"')";
                return;
              }
              
              if (!activities.checkDateIndexable(modifiedDate))
              {
                activities.noDocument(documentIdentifier,documentVersion);
                statusCode = activities.EXCLUDED_DATE;
                errorMessage = "Downstream pipeline excluded document date ("+modifiedDate+")";
                return;
              }
              
              RepositoryDocument rd = new RepositoryDocument();
              
              // For wiki, type is always text/plain
              rd.setMimeType(contentType);
              
              InputStream is = new FileInputStream(contentFile);
              try
              {
                rd.setBinary(is,dataSize);
                if (comment != null)
                  rd.addField("comment",comment);
                if (author != null)
                  rd.addField("author",author);
                if (title != null)
                  rd.addField("title",title);
                if (lastModified != null)
                {
                  rd.addField("last-modified",lastModified);
                  rd.setModifiedDate(modifiedDate);
                }

                if (allowACL != null && allowACL.length > 0) {
                  String[] denyACL = new String[]{
                    defaultAuthorityDenyToken
                  };
                  rd.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,allowACL,denyACL);
                }

                activities.ingestDocumentWithException(documentIdentifier,documentVersion,fullURL,rd);
              }
              finally
              {
                is.close();
              }
            }
            finally
            {
              contentFile.delete();
            }
          }
          else
          {
            statusCode = t.getStatusCode();
            errorMessage = t.getErrorMessage();
          }
          
          if (loginAttempted || !needsLogin)
            return;
        }
        catch (ManifoldCFException e)
        {
          t.interrupt();
          statusCode = t.getStatusCode();
          errorMessage = t.getErrorMessage();
          throw e;
        }
        catch (ServiceInterruption e)
        {
          t.interrupt();
          statusCode = t.getStatusCode();
          errorMessage = t.getErrorMessage();
          throw e;
        }
        catch (IOException e)
        {
          t.interrupt();
          statusCode = t.getStatusCode();
          errorMessage = t.getErrorMessage();
          throw e;
        }
	catch (HttpException e)
	{
	  t.interrupt();
          statusCode = t.getStatusCode();
          errorMessage = t.getErrorMessage();
	  throw e;
	}
        catch (InterruptedException e)
        {
          t.interrupt();
          // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
          throw e;
        }
        finally
        {
          t.cleanup();
        }
      }
      catch (InterruptedException e)
      {
        // Drop the connection on the floor
        statusCode = null;
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        {
          statusCode = null;
        }
        throw e;
      }
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get doc info timed out reading from the Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (java.net.SocketException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get doc info received a socket error reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Get doc info connection timed out reading from Wiki server: "+e.getMessage(),e,currentTime+300000L,currentTime+12L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        statusCode = null;
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Get doc info had an IO failure: "+e.getMessage(),e);
      }
      catch (HttpException e)
      {
	throw new ManifoldCFException("Get doc info had an HTTP exception: "+e.getMessage(),e);
      }
      finally
      {
        if (statusCode != null)
          activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,new Long(dataSize),documentIdentifier,statusCode,errorMessage,null);
      }
      
      if (!loginToAPI())
        break;
      loginAttempted = true;
    }
  }
  
  /** Thread to execute a "get doc info" operation.  This thread both executes the operation and parses the result. */
  protected static class ExecuteGetDocInfoThread extends Thread
  {
    protected HttpClient client;
    protected HttpRequestBase executeMethod;
    protected Throwable exception = null;
    protected String documentIdentifier;
    protected File contentFile = null;
    protected String author = null;
    protected String title = null;
    protected String comment = null;
    protected String lastModified = null;
    
    protected String statusCode = null;
    protected String errorMessage = null;
    protected boolean loginNeeded = false;

    public ExecuteGetDocInfoThread(HttpClient client, HttpRequestBase executeMethod, String documentIdentifier)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.executeMethod = executeMethod;
      this.documentIdentifier = documentIdentifier;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        HttpResponse rval = client.execute(executeMethod);
        if (rval.getStatusLine().getStatusCode() != 200)
        {
          throw new ManifoldCFException("Unexpected HTTP response code "+rval.getStatusLine().getStatusCode()+": "+readResponseAsString(rval));
        }
        // Read response and make sure it's valid
        InputStream is = rval.getEntity().getContent();
        try
        {
          // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
          // <api>
          //  <query>
          //    <pages>
          //      <page pageid="27697087" ns="0" title="API" touched="2011-09-27T07:00:55Z" lastrevid="367741756" counter="" length="70" redirect="" fullurl="http://en.wikipedia.org/wiki/API" editurl="http://en.wikipedia.org/w/index.php?title=API&amp;action=edit">
          //        <revisions>
          //          <rev user="Graham87" timestamp="2010-06-13T08:41:17Z" comment="Protected API: restore protection ([edit=sysop] (indefinite) [move=sysop] (indefinite))" xml:space="preserve">#REDIRECT [[Application programming interface]]{{R from abbreviation}}</rev>
          //        </revisions>
          //      </page>
          //    </pages>
          //  </query>
          //</api>

          XMLStream x = new XMLStream(false);
          WikiGetDocInfoAPIContext c = new WikiGetDocInfoAPIContext(x);
          x.setContext(c);
          try
          {
            try
            {
              x.parse(is);
              contentFile = c.getContentFile();
              title = c.getTitle();
              author = c.getAuthor();
              comment = c.getComment();
              lastModified = c.getLastModified();
              statusCode = "OK";
              loginNeeded = c.isLoginRequired();
            }
	    catch (InterruptedIOException e)
	    {
	      throw e;
	    }
            catch (IOException e)
            {
              long time = System.currentTimeMillis();
              throw new ServiceInterruption(e.getMessage(),e,time + 300000L,time + 12L * 60000L,-1,false);
            }
          }
          finally
          {
            x.cleanup();
          }
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IllegalStateException e)
          {
            // Ignore this error
          }
        }
      }
      catch (Throwable e)
      {
        statusCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        errorMessage = e.getMessage();
        this.exception = e;
      }
      finally
      {
	executeMethod.abort();
      }
    }

    public boolean finishUp()
      throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
    {
      join();
      handleException(exception);
      return loginNeeded;
    }
    
    public String getStatusCode()
    {
      return statusCode;
    }
    
    public String getErrorMessage()
    {
      return errorMessage;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }
    
    public String getComment()
    {
      return comment;
    }
    
    public String getTitle()
    {
      return title;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
    public void cleanup()
    {
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }
    
  }

  /** Create a URL to obtain a page's metadata and content, given the page ID.
  * QUESTION: Can we do multiple document identifiers at a time??
  */
  protected String getGetDocInfoURL(String documentIdentifier)
    throws ManifoldCFException
  {
    return baseURL + "action=query&prop=revisions&pageids="+documentIdentifier+"&rvprop=user%7ccomment%7ccontent%7ctimestamp";
  }

  /** Class representing the "api" context of a "get doc info" response */
  protected static class WikiGetDocInfoAPIContext extends SingleLevelContext
  {
    /** Title */
    protected String title = null;
    /** Content file */
    protected File contentFile = null;
    /** Author */
    protected String author = null;
    /** Comment */
    protected String comment = null;
    /** Last modified */
    protected String lastModified = null;
    protected boolean loginNeeded = false;
    
    public WikiGetDocInfoAPIContext(XMLStream theStream)
    {
      super(theStream,"api");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocInfoQueryContext(theStream,namespaceURI,localName,qName,atts);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      WikiGetDocInfoQueryContext pc = (WikiGetDocInfoQueryContext)child;
      tagCleanup();
      title = pc.getTitle();
      contentFile = pc.getContentFile();
      author = pc.getAuthor();
      comment = pc.getComment();
      lastModified = pc.getLastModified();
      loginNeeded |= pc.isLoginRequired();
    }
    
    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }

    public String getTitle()
    {
      return title;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
    public String getComment()
    {
      return comment;
    }

    public boolean isLoginRequired()
    {
      return loginNeeded;
    }

  }

  /** Class representing the "api/query" context of a "get doc info" response */
  protected static class WikiGetDocInfoQueryContext extends SingleLevelErrorContext
  {
    /** Title */
    protected String title = null;
    /** Content file */
    protected File contentFile = null;
    /** Author */
    protected String author = null;
    /** Comment */
    protected String comment = null;
    /** Last modified */
    protected String lastModified = null;
    
    public WikiGetDocInfoQueryContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"query");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocInfoPagesContext(theStream,namespaceURI,localName,qName,atts);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      WikiGetDocInfoPagesContext pc = (WikiGetDocInfoPagesContext)child;
      tagCleanup();
      title = pc.getTitle();
      contentFile = pc.getContentFile();
      author = pc.getAuthor();
      comment = pc.getComment();
      lastModified = pc.getLastModified();
    }
    
    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }

    public String getTitle()
    {
      return title;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
    public String getComment()
    {
      return comment;
    }
    
  }

  /** Class representing the "api/query/pages" context of a "get doc info" response */
  protected static class WikiGetDocInfoPagesContext extends SingleLevelContext
  {
    /** Title */
    protected String title = null;
    /** Content file */
    protected File contentFile = null;
    /** Author */
    protected String author = null;
    /** Comment */
    protected String comment = null;
    /** Last modified */
    protected String lastModified = null;
    
    public WikiGetDocInfoPagesContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"pages");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocInfoPageContext(theStream,namespaceURI,localName,qName,atts);
    }
    
    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      WikiGetDocInfoPageContext pc = (WikiGetDocInfoPageContext)child;
      tagCleanup();
      title = pc.getTitle();
      contentFile = pc.getContentFile();
      author = pc.getAuthor();
      lastModified = pc.getLastModified();
      comment = pc.getComment();
    }
    
    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }

    public String getTitle()
    {
      return title;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
    public String getComment()
    {
      return comment;
    }

  }

  /** Class representing the "api/query/pages/page" context of a "get doc info" response */
  protected static class WikiGetDocInfoPageContext extends BaseProcessingContext
  {
    /** Title */
    protected String title = null;
    /** Content file */
    protected File contentFile = null;
    /** Author */
    protected String author = null;
    /** Comment */
    protected String comment = null;
    /** Last modified */
    protected String lastModified = null;
    
    public WikiGetDocInfoPageContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("page"))
      {
        title = atts.getValue("title");
        return new WikiGetDocInfoRevisionsContext(theStream,namespaceURI,localName,qName,atts);
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }
    
    protected void endTag()
      throws ManifoldCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("page"))
      {
        // Pull down the data
        WikiGetDocInfoRevisionsContext rc = (WikiGetDocInfoRevisionsContext)theContext;
        tagCleanup();
        contentFile = rc.getContentFile();
        author = rc.getAuthor();
        comment = rc.getComment();
        lastModified = rc.getLastModified();
      }
      super.endTag();
    }

    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }

    public String getTitle()
    {
      return title;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }
    
    public String getComment()
    {
      return comment;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
  }

  /** Class representing the "api/query/pages/page/revisions" context of a "get doc info" response */
  protected static class WikiGetDocInfoRevisionsContext extends SingleLevelContext
  {
    protected File contentFile = null;
    protected String author = null;
    protected String comment = null;
    protected String lastModified = null;
    
    public WikiGetDocInfoRevisionsContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts,"revisions");
    }

    @Override
    protected BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts)
    {
      return new WikiGetDocInfoRevContext(theStream,namespaceURI,localName,qName,atts);
    }

    @Override
    protected void finishChild(BaseProcessingContext child)
      throws ManifoldCFException
    {
      WikiGetDocInfoRevContext rc = (WikiGetDocInfoRevContext)child;
      tagCleanup();
      contentFile = rc.getContentFile();
      author = rc.getAuthor();
      comment = rc.getComment();
      lastModified = rc.getLastModified();
    }
    
    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }

    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
    public String getAuthor()
    {
      return author;
    }
    
    public String getComment()
    {
      return comment;
    }
    
    public String getLastModified()
    {
      return lastModified;
    }
  }

  /** Class looking for the "api/query/pages/page/revisions/rev" context of a "get doc info" response */
  protected static class WikiGetDocInfoRevContext extends BaseProcessingContext
  {
    protected String author = null;
    protected String comment = null;
    protected File contentFile = null;
    protected String lastModified = null;
    
    public WikiGetDocInfoRevContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws ManifoldCFException, ServiceInterruption
    {
      if (qName.equals("rev"))
      {
        author = atts.getValue("user");
        comment = atts.getValue("comment");
        lastModified = atts.getValue("timestamp");
        try
        {
          File tempFile = File.createTempFile("_wikidata_","tmp");
          return new XMLFileContext(theStream,namespaceURI,localName,qName,atts,tempFile);
        }
        catch (java.net.SocketTimeoutException e)
        {
          throw new ManifoldCFException("IO exception creating temp file: "+e.getMessage(),e);
        }
        catch (InterruptedIOException e)
        {
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (IOException e)
        {
          throw new ManifoldCFException("IO exception creating temp file: "+e.getMessage(),e);
        }
      }
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    protected void endTag()
      throws ManifoldCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("rev"))
      {
        // Pull down the data
        XMLFileContext rc = (XMLFileContext)theContext;
        tagCleanup();
        contentFile = rc.getCompletedFile();
      }
      else
        super.endTag();
    }

    protected void tagCleanup()
      throws ManifoldCFException
    {
      // Delete the contents file if it is there.
      if (contentFile != null)
      {
        contentFile.delete();
        contentFile = null;
      }
    }
    
    public String getAuthor()
    {
      return author;
    }

    public String getLastModified()
    {
      return lastModified;
    }
    
    public String getComment()
    {
      return comment;
    }
    
    public File getContentFile()
    {
      File rval = contentFile;
      contentFile = null;
      return rval;
    }
    
  }
  
  protected static String readResponseAsString(HttpResponse httpResponse)
    throws IOException
  {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null)
    {
      InputStream is = entity.getContent();
      try
      {
        Charset charSet;
        try
        {
          ContentType ct = ContentType.get(entity);
          if (ct == null)
            charSet = StandardCharsets.UTF_8;
          else
            charSet = ct.getCharset();
        }
        catch (ParseException e)
        {
          charSet = StandardCharsets.UTF_8;
        }
        char[] buffer = new char[65536];
        Reader r = new InputStreamReader(is,charSet);
        Writer w = new StringWriter();
        try
        {
          while (true)
          {
            int amt = r.read(buffer);
            if (amt == -1)
              break;
            w.write(buffer,0,amt);
          }
        }
        finally
        {
          w.flush();
        }
        return w.toString();
      }
      finally
      {
        is.close();
      }
    }
    return "";
  }
  
  protected static void handleException(Throwable thr)
    throws InterruptedException, ManifoldCFException, ServiceInterruption, IOException, HttpException
  {
    if (thr != null) {
      if (thr instanceof ManifoldCFException) {
	if (((ManifoldCFException) thr).getErrorCode() == ManifoldCFException.INTERRUPTED) {
	  throw new InterruptedException(thr.getMessage());
	}
	throw (ManifoldCFException) thr;
      } else if (thr instanceof ServiceInterruption) {
	throw (ServiceInterruption) thr;
      } else if (thr instanceof IOException) {
	throw (IOException) thr;
      } else if (thr instanceof HttpException) {
	throw (HttpException) thr;
      } else if (thr instanceof RuntimeException) {
	throw (RuntimeException) thr;
      } else if (thr instanceof Error) {
	throw (Error) thr;
      } else {
        throw new RuntimeException("Unexpected exception class: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }
  }

}
