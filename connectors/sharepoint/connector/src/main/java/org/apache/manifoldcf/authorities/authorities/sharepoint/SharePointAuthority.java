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
package org.apache.manifoldcf.authorities.authorities.sharepoint;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.authorities.system.ManifoldCF;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.core.util.URLDecoder;
import org.apache.manifoldcf.connectorcommon.interfaces.*;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.TimeUnit;
import javax.naming.*;
import javax.naming.ldap.*;
import javax.naming.directory.*;

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
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.HttpHost;


/** This is the native SharePoint implementation of the IAuthorityConnector interface.
*/
public class SharePointAuthority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Data from the parameters
  
  /** Cache manager. */
  private ICacheManager cacheManager = null;
  
  private boolean hasSessionParameters = false;
  
  /** Length of time that a SharePoint session can remain idle */
  private static final long SharePointExpirationInterval = 300000L;
  
  // SharePoint server parameters
  // These are needed for caching, so they are set at connect() time
  private boolean isClaimSpace = false;
  private String serverProtocol = null;
  private String serverUrl = null;
  private String fileBaseUrl = null;
  private String serverUserName = null;
  private String password = null;
  private String ntlmDomain = null;
  private String serverName = null;
  private String serverPortString = null;
  private String serverLocation = null;
  private String strippedUserName = null;
  private String encodedServerLocation = null;
  private String keystoreData = null;
  
  private String proxyHost = null;
  private String proxyPortString = null;
  private String proxyUsername = null;
  private String proxyPassword = null;
  private String proxyDomain = null;
  
  private String cacheLRUsize = null;
  private String cacheLifetime = null;
  
  // These are calculated when the session is set up
  private int serverPort = -1;
  private SPSProxyHelper proxy = null;
  private long sharepointSessionTimeout;
  
  private long responseLifetime = -1L;
  private int LRUsize = -1;
  
  private IKeystoreManager keystoreManager = null;
  
  private HttpClientConnectionManager connectionManager = null;
  private HttpClient httpClient = null;


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
    catch (UnknownHostException e)
    {
    }
  }

  /** Constructor.
  */
  public SharePointAuthority()
  {
  }

  /** Set thread context.
  */
  @Override
  public void setThreadContext(IThreadContext tc)
    throws ManifoldCFException
  {
    super.setThreadContext(tc);
    cacheManager = CacheManagerFactory.make(tc);
  }
  
  /** Clear thread context.
  */
  @Override
  public void clearThreadContext()
  {
    super.clearThreadContext();
    cacheManager = null;
  }
  
  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // Pick up all the parameters that go into the cache key here
    cacheLifetime = configParams.getParameter(SharePointConfig.PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    cacheLRUsize = configParams.getParameter(SharePointConfig.PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";
    
    String serverVersion = configParams.getParameter( SharePointConfig.PARAM_SERVERVERSION );
    if (serverVersion == null)
      serverVersion = "4.0";
    // Authority needs to do nothing with SharePoint version right now.

    String serverClaimSpace = configParams.getParameter( SharePointConfig.PARAM_SERVERCLAIMSPACE);
    if (serverClaimSpace == null)
      serverClaimSpace = "false";
    isClaimSpace = serverClaimSpace.equals("true");
    
    serverProtocol = configParams.getParameter( SharePointConfig.PARAM_SERVERPROTOCOL );
    if (serverProtocol == null)
      serverProtocol = "http";
      
    serverName = configParams.getParameter( SharePointConfig.PARAM_SERVERNAME );
    serverPortString = configParams.getParameter( SharePointConfig.PARAM_SERVERPORT );
    serverLocation = configParams.getParameter(SharePointConfig.PARAM_SERVERLOCATION);
    if (serverLocation == null)
      serverLocation = "";
    if (serverLocation.endsWith("/"))
      serverLocation = serverLocation.substring(0,serverLocation.length()-1);
    if (serverLocation.length() > 0 && !serverLocation.startsWith("/"))
      serverLocation = "/" + serverLocation;
    encodedServerLocation = serverLocation;
    serverLocation = decodePath(serverLocation);

    serverUserName = configParams.getParameter(SharePointConfig.PARAM_SERVERUSERNAME);
    password = configParams.getObfuscatedParameter(SharePointConfig.PARAM_SERVERPASSWORD);
    int index = serverUserName.indexOf("\\");
    if (index != -1)
    {
      strippedUserName = serverUserName.substring(index+1);
      ntlmDomain = serverUserName.substring(0,index);
    }
    else
    {
      strippedUserName = null;
      ntlmDomain = null;
    }
    
    proxyHost = params.getParameter(SharePointConfig.PARAM_PROXYHOST);
    proxyPortString = params.getParameter(SharePointConfig.PARAM_PROXYPORT);
    proxyUsername = params.getParameter(SharePointConfig.PARAM_PROXYUSER);
    proxyPassword = params.getObfuscatedParameter(SharePointConfig.PARAM_PROXYPASSWORD);
    proxyDomain = params.getParameter(SharePointConfig.PARAM_PROXYDOMAIN);

    keystoreData = params.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);

  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Check connection for sanity.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    getSharePointSession();
    try
    {
      URL urlServer = new URL( serverUrl );
    }
    catch ( MalformedURLException e )
    {
      return "Illegal SharePoint url: "+e.getMessage();
    }

    try
    {
      proxy.checkConnection( "/" );
    }
    catch (ManifoldCFException e)
    {
      return e.getMessage();
    }

    return super.check();
  }

  /** Poll.  The connection should be closed if it has been idle for too long.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    long currentTime = System.currentTimeMillis();
    if (proxy != null && System.currentTimeMillis() >= sharepointSessionTimeout)
      expireSharePointSession();
    if (connectionManager != null)
      connectionManager.closeIdleConnections(60000L,TimeUnit.MILLISECONDS);
    super.poll();
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

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    // Clean up caching parameters
    
    cacheLifetime = null;
    cacheLRUsize = null;
    
    // Clean up SharePoint parameters
    
    isClaimSpace = false;
    serverUrl = null;
    fileBaseUrl = null;
    serverUserName = null;
    strippedUserName = null;
    password = null;
    ntlmDomain = null;
    serverName = null;
    serverLocation = null;
    encodedServerLocation = null;
    serverPort = -1;

    proxyHost = null;
    proxyPortString = null;
    proxyUsername = null;
    proxyPassword = null;
    proxyDomain = null;
    
    keystoreData = null;
    keystoreManager = null;

    proxy = null;
    httpClient = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;

    hasSessionParameters = false;
    
    super.disconnect();
  }

  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  @Override
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws ManifoldCFException
  {
    getSessionParameters();
    // Construct a cache description object
    ICacheDescription objectDescription = new AuthorizationResponseDescription(userName,
      serverName,serverPortString,serverLocation,serverProtocol,serverUserName,password,
      this.responseLifetime,this.LRUsize);
    
    // Enter the cache
    ICacheHandle ch = cacheManager.enterCache(new ICacheDescription[]{objectDescription},null,null);
    try
    {
      ICacheCreateHandle createHandle = cacheManager.enterCreateSection(ch);
      try
      {
        // Lookup the object
        AuthorizationResponse response = (AuthorizationResponse)cacheManager.lookupObject(createHandle,objectDescription);
        if (response != null)
          return response;
        // Create the object.
        response = getAuthorizationResponseUncached(userName);
        // Save it in the cache
        cacheManager.saveObject(createHandle,objectDescription,response);
        // And return it...
        return response;
      }
      finally
      {
        cacheManager.leaveCreateSection(createHandle);
      }
    }
    finally
    {
      cacheManager.leaveCache(ch);
    }
  }
  
  /** Obtain the access tokens for a given user name, uncached.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  protected AuthorizationResponse getAuthorizationResponseUncached(String userName)
    throws ManifoldCFException
  {
    //String searchBase = "CN=Administrator,CN=Users,DC=qa-ad-76,DC=metacarta,DC=com";
    int index = userName.indexOf("@");
    if (index == -1)
      throw new ManifoldCFException("Username is in unexpected form (no @): '"+userName+"'");

    String userPart = userName.substring(0,index);
    String domainPart = userName.substring(index+1);

    // First, look up user in SharePoint.
    getSharePointSession();
    List<String> sharePointTokens = proxy.getAccessTokens("/", domainPart + "\\" + userPart);
    if (sharePointTokens == null)
      return RESPONSE_USERNOTFOUND_ADDITIVE;
    
    return new AuthorizationResponse(sharePointTokens.toArray(new String[0]),AuthorizationResponse.RESPONSE_OK);
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    // The default response if the getConnection method fails
    return RESPONSE_UNREACHABLE_ADDITIVE;
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected.  That is why they receive a thread context argument.
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"SharePointAuthority.Server"));
    tabsArray.add(Messages.getString(locale,"SharePointAuthority.Cache"));
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration.js",null);
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the authority connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    velocityContext.put("TabName",tabName);
    fillInCacheTab(velocityContext,out,parameters);
    fillInServerTab(velocityContext,out,parameters);
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration_Cache.html",velocityContext);
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration_Server.html",velocityContext);
  }

  protected static void fillInServerTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException
  {
    String serverVersion = parameters.getParameter(SharePointConfig.PARAM_SERVERVERSION);
    if (serverVersion == null)
      serverVersion = "2.0";
    
    String serverClaimSpace = parameters.getParameter(SharePointConfig.PARAM_SERVERCLAIMSPACE);
    if (serverClaimSpace == null)
      serverClaimSpace = "false";

    String serverProtocol = parameters.getParameter(SharePointConfig.PARAM_SERVERPROTOCOL);
    if (serverProtocol == null)
      serverProtocol = "http";

    String serverName = parameters.getParameter(SharePointConfig.PARAM_SERVERNAME);
    if (serverName == null)
      serverName = "localhost";

    String serverPort = parameters.getParameter(SharePointConfig.PARAM_SERVERPORT);
    if (serverPort == null)
      serverPort = "";

    String serverLocation = parameters.getParameter(SharePointConfig.PARAM_SERVERLOCATION);
    if (serverLocation == null)
      serverLocation = "";
      
    String userName = parameters.getParameter(SharePointConfig.PARAM_SERVERUSERNAME);
    if (userName == null)
      userName = "";

    String password = parameters.getObfuscatedParameter(SharePointConfig.PARAM_SERVERPASSWORD);
    if (password == null)
      password = "";
    else
      password = out.mapPasswordToKey(password);

    String keystore = parameters.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);
    IKeystoreManager localKeystore;
    if (keystore == null)
      localKeystore = KeystoreManagerFactory.make("");
    else
      localKeystore = KeystoreManagerFactory.make("",keystore);

    List<Map<String,String>> certificates = new ArrayList<Map<String,String>>();
    
    String[] contents = localKeystore.getContents();
    for (String alias : contents)
    {
      String description = localKeystore.getDescription(alias);
      if (description.length() > 128)
        description = description.substring(0,125) + "...";
      Map<String,String> certificate = new HashMap<String,String>();
      certificate.put("ALIAS", alias);
      certificate.put("DESCRIPTION", description);
      certificates.add(certificate);
    }
    
    String proxyHost = parameters.getParameter(SharePointConfig.PARAM_PROXYHOST);
    if (proxyHost == null)
      proxyHost = "";
    
    String proxyPort = parameters.getParameter(SharePointConfig.PARAM_PROXYPORT);
    if (proxyPort == null)
      proxyPort = "";
    
    String proxyUser = parameters.getParameter(SharePointConfig.PARAM_PROXYUSER);
    if (proxyUser == null)
      proxyUser = "";
    
    String proxyPassword = parameters.getObfuscatedParameter(SharePointConfig.PARAM_PROXYPASSWORD);
    if (proxyPassword == null)
      proxyPassword = "";
    else
      proxyPassword = out.mapPasswordToKey(proxyPassword);

    String proxyDomain = parameters.getParameter(SharePointConfig.PARAM_PROXYDOMAIN);
    if (proxyDomain == null)
      proxyDomain = "";

    // Fill in context
    velocityContext.put("SERVERVERSION", serverVersion);
    velocityContext.put("SERVERCLAIMSPACE", serverClaimSpace);
    velocityContext.put("SERVERPROTOCOL", serverProtocol);
    velocityContext.put("SERVERNAME", serverName);
    velocityContext.put("SERVERPORT", serverPort);
    velocityContext.put("SERVERLOCATION", serverLocation);
    velocityContext.put("SERVERUSERNAME", userName);
    velocityContext.put("SERVERPASSWORD", password);
    if (keystore != null)
      velocityContext.put("KEYSTORE", keystore);
    velocityContext.put("CERTIFICATELIST", certificates);

    velocityContext.put("PROXYHOST", proxyHost);
    velocityContext.put("PROXYPORT", proxyPort);
    velocityContext.put("PROXYUSER", proxyUser);
    velocityContext.put("PROXYPASSWORD", proxyPassword);
    velocityContext.put("PROXYDOMAIN", proxyDomain);

  }

  protected static void fillInCacheTab(Map<String,Object> velocityContext, IPasswordMapperActivity mapper, ConfigParams parameters)
  {
    String cacheLifetime = parameters.getParameter(SharePointConfig.PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    velocityContext.put("CACHELIFETIME",cacheLifetime);
    String cacheLRUsize = parameters.getParameter(SharePointConfig.PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";
    velocityContext.put("CACHELRUSIZE",cacheLRUsize);
  }
  
  /** Process a configuration post.
  * This method is called at the start of the authority connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    // Cache parameters
    
    String cacheLifetime = variableContext.getParameter("cachelifetime");
    if (cacheLifetime != null)
      parameters.setParameter(SharePointConfig.PARAM_CACHELIFETIME,cacheLifetime);
    String cacheLRUsize = variableContext.getParameter("cachelrusize");
    if (cacheLRUsize != null)
      parameters.setParameter(SharePointConfig.PARAM_CACHELRUSIZE,cacheLRUsize);
    
    // SharePoint server parameters
    
    String serverVersion = variableContext.getParameter("serverVersion");
    if (serverVersion != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERVERSION,serverVersion);

    String serverClaimSpace = variableContext.getParameter("serverClaimSpace");
    if (serverClaimSpace != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERCLAIMSPACE,serverClaimSpace);
    
    String serverProtocol = variableContext.getParameter("serverProtocol");
    if (serverProtocol != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERPROTOCOL,serverProtocol);

    String serverName = variableContext.getParameter("serverName");

    if (serverName != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERNAME,serverName);

    String serverPort = variableContext.getParameter("serverPort");
    if (serverPort != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERPORT,serverPort);

    String serverLocation = variableContext.getParameter("serverLocation");
    if (serverLocation != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERLOCATION,serverLocation);

    String userName = variableContext.getParameter("serverUserName");
    if (userName != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERUSERNAME,userName);

    String password = variableContext.getParameter("serverPassword");
    if (password != null)
      parameters.setObfuscatedParameter(SharePointConfig.PARAM_SERVERPASSWORD,variableContext.mapKeyToPassword(password));

    String proxyHost = variableContext.getParameter("proxyhost");
    if (proxyHost != null)
      parameters.setParameter(SharePointConfig.PARAM_PROXYHOST,proxyHost);
    
    String proxyPort = variableContext.getParameter("proxyport");
    if (proxyPort != null)
      parameters.setParameter(SharePointConfig.PARAM_PROXYPORT,proxyPort);
    
    String proxyUser = variableContext.getParameter("proxyuser");
    if (proxyUser != null)
      parameters.setParameter(SharePointConfig.PARAM_PROXYUSER,proxyUser);
    
    String proxyPassword = variableContext.getParameter("proxypassword");
    if (proxyPassword != null)
      parameters.setObfuscatedParameter(SharePointConfig.PARAM_PROXYPASSWORD,variableContext.mapKeyToPassword(proxyPassword));
    
    String proxyDomain = variableContext.getParameter("proxydomain");
    if (proxyDomain != null)
      parameters.setParameter(SharePointConfig.PARAM_PROXYDOMAIN,proxyDomain);

    String keystoreValue = variableContext.getParameter("keystoredata");
    if (keystoreValue != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERKEYSTORE,keystoreValue);

    String configOp = variableContext.getParameter("configop");
    if (configOp != null)
    {
      if (configOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("shpkeystorealias");
        keystoreValue = parameters.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        parameters.setParameter(SharePointConfig.PARAM_SERVERKEYSTORE,mgr.getString());
      }
      else if (configOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("shpcertificate");
        keystoreValue = parameters.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
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
            // Don't report anything
          }
        }

        if (certError != null)
        {
          // Redirect to error page
          return "Illegal certificate: "+certError;
        }
        parameters.setParameter(SharePointConfig.PARAM_SERVERKEYSTORE,mgr.getString());
      }
    }
    
    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the authority connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    fillInCacheTab(velocityContext,out,parameters);
    fillInServerTab(velocityContext,out,parameters);
    Messages.outputResourceWithVelocity(out,locale,"viewConfiguration.html",velocityContext);
  }

  // Protected methods

  /** Get parameters needed for caching.
  */
  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (!hasSessionParameters)
    {
      try
      {
        responseLifetime = Long.parseLong(this.cacheLifetime) * 60L * 1000L;
        LRUsize = Integer.parseInt(this.cacheLRUsize);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Cache lifetime or Cache LRU size must be an integer: "+e.getMessage(),e);
      }
      hasSessionParameters = true;
    }
  }
  
  protected void getSharePointSession()
    throws ManifoldCFException
  {
    if (proxy == null)
    {
      // Set up server URL
      try
      {
        if (serverPortString == null || serverPortString.length() == 0)
        {
          if (serverProtocol.equals("https"))
            this.serverPort = 443;
          else
            this.serverPort = 80;
        }
        else
          this.serverPort = Integer.parseInt(serverPortString);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
      
      int proxyPort = 8080;
      if (proxyPortString != null && proxyPortString.length() > 0)
      {
        try
        {
          proxyPort = Integer.parseInt(proxyPortString);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage(),e);
        }
      }

      serverUrl = serverProtocol + "://" + serverName;
      if (serverProtocol.equals("https"))
      {
        if (serverPort != 443)
          serverUrl += ":" + Integer.toString(serverPort);
      }
      else
      {
        if (serverPort != 80)
          serverUrl += ":" + Integer.toString(serverPort);
      }

      fileBaseUrl = serverUrl + encodedServerLocation;

      int connectionTimeout = 60000;
      int socketTimeout = 900000;
      
      // Set up ssl if indicated

      SSLConnectionSocketFactory myFactory = null;
      if (keystoreData != null)
      {
        keystoreManager = KeystoreManagerFactory.make("",keystoreData);
        myFactory = new SSLConnectionSocketFactory(keystoreManager.getSecureSocketFactory(), new DefaultHostnameVerifier());
      }
      else
      {
        myFactory = SSLConnectionSocketFactory.getSocketFactory();
      }

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

      if (strippedUserName != null)
      {
        credentialsProvider.setCredentials(
          new AuthScope(serverName,serverPort),
          new NTCredentials(strippedUserName, password, currentHost, ntlmDomain));
      }

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(socketTimeout)
          .setExpectContinueEnabled(false)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);

      // If there's a proxy, set that too.
      if (proxyHost != null && proxyHost.length() > 0)
      {

        // Configure proxy authentication
        if (proxyUsername != null && proxyUsername.length() > 0)
        {
          if (proxyPassword == null)
            proxyPassword = "";
          if (proxyDomain == null)
            proxyDomain = "";

          credentialsProvider.setCredentials(
            new AuthScope(proxyHost, proxyPort),
            new NTCredentials(proxyUsername, proxyPassword, currentHost, proxyDomain));
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        requestBuilder.setProxy(proxy);
      }

      HttpClientBuilder builder = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setDefaultCredentialsProvider(credentialsProvider);
      builder.setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .setRedirectStrategy(new LaxRedirectStrategy());
      httpClient = builder.build();
      
      proxy = new SPSProxyHelper( serverUrl, encodedServerLocation, serverLocation, serverUserName, password,
        org.apache.manifoldcf.connectorcommon.common.CommonsHTTPSender.class, "client-config.wsdd",
        httpClient, isClaimSpace );
      
    }
    sharepointSessionTimeout = System.currentTimeMillis() + SharePointExpirationInterval;
  }
  
  protected void expireSharePointSession()
    throws ManifoldCFException
  {
    serverPort = -1;
    serverUrl = null;
    fileBaseUrl = null;
    keystoreManager = null;
    proxy = null;
    httpClient = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;
  }

  /** Decode a path item.
  */
  public static String pathItemDecode(String pathItem)
  {
      return URLDecoder.decode(pathItem.replaceAll("\\%20","+"));
  }

  /** Encode a path item.
  */
  public static String pathItemEncode(String pathItem)
  {
      String output = URLEncoder.encode(pathItem);
      return output.replaceAll("\\+","%20");

  }

  /** Given a path that is /-separated, and otherwise encoded, decode properly to convert to
  * unencoded form.
  */
  public static String decodePath(String relPath)
  {
    StringBuilder sb = new StringBuilder();
    String[] pathEntries = relPath.split("/");
    int k = 0;

    boolean isFirst = true;
    while (k < pathEntries.length)
    {
      if (isFirst)
        isFirst = false;
      else
        sb.append("/");
      sb.append(pathItemDecode(pathEntries[k++]));
    }
    return sb.toString();
  }

  /** Given a path that is /-separated, and otherwise unencoded, encode properly for an actual
  * URI
  */
  public static String encodePath(String relPath)
  {
    StringBuilder sb = new StringBuilder();
    String[] pathEntries = relPath.split("/");
    int k = 0;

    boolean isFirst = true;
    while (k < pathEntries.length)
    {
      if (isFirst)
        isFirst = false;
      else
        sb.append("/");
      sb.append(pathItemEncode(pathEntries[k++]));
    }
    return sb.toString();
  }

  protected static StringSet emptyStringSet = new StringSet();
  
  /** This is the cache object descriptor for cached access tokens from
  * this connector.
  */
  protected static class AuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    /** The user name */
    protected final String userName;
    /** The response lifetime */
    protected final long responseLifetime;
    /** The expiration time */
    protected long expirationTime = -1;
    // Parameters designed to guarantee cache key uniqueness
    protected final String serverName;
    protected final String serverPortString;
    protected final String serverLocation;
    protected final String serverProtocol;
    protected final String serverUserName;
    protected final String password;
    
    /** Constructor. */
    public AuthorizationResponseDescription(String userName,
      String serverName, String serverPortString, String serverLocation, String serverProtocol, String serverUserName, String password,
      long responseLifetime, int LRUsize)
    {
      super("SharePointAuthority",LRUsize);
      this.userName = userName;
      this.responseLifetime = responseLifetime;
      this.serverName = serverName;
      this.serverPortString = serverPortString;
      this.serverLocation = serverLocation;
      this.serverProtocol = serverProtocol;
      this.serverUserName = serverUserName;
      this.password = password;
    }

    /** Return the invalidation keys for this object. */
    public StringSet getObjectKeys()
    {
      return emptyStringSet;
    }

    /** Get the critical section name, used for synchronizing the creation of the object */
    public String getCriticalSectionName()
    {
      StringBuilder sb = new StringBuilder(getClass().getName());
      sb.append("-").append(userName);
      sb.append("-").append(serverName);
      sb.append("-").append(serverPortString);
      sb.append("-").append(serverLocation);
      sb.append("-").append(serverProtocol);
      sb.append("-").append(serverUserName);
      sb.append("-").append(password);
      return sb.toString();
    }

    /** Return the object expiration interval */
    public long getObjectExpirationTime(long currentTime)
    {
      if (expirationTime == -1)
        expirationTime = currentTime + responseLifetime;
      return expirationTime;
    }

    public int hashCode()
    {
      int rval = userName.hashCode();
      rval += serverName.hashCode();
      rval += serverPortString.hashCode();
      rval += serverLocation.hashCode();
      rval += serverProtocol.hashCode();
      rval += serverUserName.hashCode();
      rval += password.hashCode();
      return rval;
    }
    
    public boolean equals(Object o)
    {
      if (!(o instanceof AuthorizationResponseDescription))
        return false;
      AuthorizationResponseDescription ard = (AuthorizationResponseDescription)o;
      if (!ard.userName.equals(userName))
        return false;
      if (!ard.serverName.equals(serverName))
        return false;
      if (!ard.serverPortString.equals(serverPortString))
        return false;
      if (!ard.serverLocation.equals(serverLocation))
        return false;
      if (!ard.serverProtocol.equals(serverProtocol))
        return false;
      if (!ard.serverUserName.equals(serverUserName))
        return false;
      if (!ard.password.equals(password))
        return false;
      return true;
    }
    
  }
  
}


