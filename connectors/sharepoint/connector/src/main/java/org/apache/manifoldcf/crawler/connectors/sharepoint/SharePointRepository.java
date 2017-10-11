/* $Id: SharePointRepository.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.sharepoint;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.connectorcommon.extmimemap.ExtensionMimeMap;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.core.util.URLDecoder;

import java.io.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.net.*;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;

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

/** This is the "repository connector" for Microsoft SharePoint.
* Document identifiers for this connector come in three forms:
* (1) An "S" followed by the encoded subsite/library path, which represents the encoded relative path from the root site to a library. [deprecated and no longer supported];
* (2) A "D" followed by a subsite/library/folder/file path, which represents the relative path from the root site to a file. [deprecated and no longer supported]
* (3) Six different kinds of unencoded path, each of which starts with a "/" at the beginning, where the "/" represents the root site of the connection, as follows:
*   /sitepath/ - the relative path to a site.  The path MUST both begin and end with a single "/".
*   /sitepath/libraryname// - the relative path to a library.  The path MUST begin with a single "/" and end with "//".
*   /sitepath/libraryname//folderfilepath - the relative path to a file.  The path MUST begin with a single "/" and MUST include a "//" after the library, and must NOT end with a "/".
*   /sitepath/listname/// - the relative path to a list.  The path MUST begin with a single "/" and end with "///".
*   /sitepath/listname///rowid - the relative path to a list item.  The path MUST begin with a single "/" and MUST include a "///" after the list name, and must NOT end in a "/".
*   /sitepath/listname///rowid//attachment_filename - the relative path to a list attachment.  The path MUST begin with a single "/", MUST include a "///" after the list name, and
*      MUST include a "//" separating the rowid from the filename.
*/
public class SharePointRepository extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: SharePointRepository.java 996524 2010-09-13 13:38:01Z kwright $";

  // Properties we need
  public final static String wsddPathProperty = "org.apache.manifoldcf.sharepoint.wsddpath";

  // Activities we log
  public final static String ACTIVITY_FETCH = "fetch";

  protected final static long sessionExpirationInterval = 300000L;
  
  private boolean supportsItemSecurity = false;
  private boolean dspStsWorks = true;
  private boolean attachmentsSupported = false;
  private boolean activeDirectoryAuthority = true;
  
  private String serverProtocol = null;
  private String serverUrl = null;
  private String fileBaseUrl = null;
  private String userName = null;
  private String strippedUserName = null;
  private String password = null;
  private String ntlmDomain = null;
  private String serverName = null;
  private String serverLocation = null;
  private String encodedServerLocation = null;
  private int serverPort = -1;

  private SPSProxyHelper proxy = null;

  private long sessionTimeout;
  
  // SSL support
  private String keystoreData = null;
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

  // Turn off AXIS debug output that we don't want
  static
  {
    Logger logger = Logger.getLogger("org.apache.axis.ConfigurationException");
    logger.setLevel(Level.INFO);
  }
  
  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = GLOBAL_DENY_TOKEN;

  /** Constructor.
  */
  public SharePointRepository()
  {
  }

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    if (proxy == null)
    {
      String serverVersion = params.getParameter( SharePointConfig.PARAM_SERVERVERSION );
      if (serverVersion == null)
        serverVersion = "4.0";
      supportsItemSecurity = !serverVersion.equals("2.0");
      dspStsWorks = serverVersion.equals("2.0") || serverVersion.equals("3.0");
      attachmentsSupported = !serverVersion.equals("2.0");
      
      String authorityType = params.getParameter( SharePointConfig.PARAM_AUTHORITYTYPE );
      if (authorityType == null)
        authorityType = "ActiveDirectory";
      
      activeDirectoryAuthority = authorityType.equals("ActiveDirectory");

      serverProtocol = params.getParameter( SharePointConfig.PARAM_SERVERPROTOCOL );
      if (serverProtocol == null)
        serverProtocol = "http";
      try
      {
        String serverPort = params.getParameter( SharePointConfig.PARAM_SERVERPORT );
        if (serverPort == null || serverPort.length() == 0)
        {
          if (serverProtocol.equals("https"))
            this.serverPort = 443;
          else
            this.serverPort = 80;
        }
        else
          this.serverPort = Integer.parseInt(serverPort);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
      serverLocation = params.getParameter(SharePointConfig.PARAM_SERVERLOCATION);
      if (serverLocation == null)
        serverLocation = "";
      if (serverLocation.endsWith("/"))
        serverLocation = serverLocation.substring(0,serverLocation.length()-1);
      if (serverLocation.length() > 0 && !serverLocation.startsWith("/"))
        serverLocation = "/" + serverLocation;
      encodedServerLocation = serverLocation;
      serverLocation = decodePath(serverLocation);

      userName = params.getParameter(SharePointConfig.PARAM_SERVERUSERNAME);
      password = params.getObfuscatedParameter(SharePointConfig.PARAM_SERVERPASSWORD);
      int index = userName.indexOf("\\");
      if (index != -1)
      {
        strippedUserName = userName.substring(index+1);
        ntlmDomain = userName.substring(0,index);
      }
      else
      {
        strippedUserName = null;
        ntlmDomain = null;
      }

      String proxyHost = params.getParameter(SharePointConfig.PARAM_PROXYHOST);
      String proxyPortString = params.getParameter(SharePointConfig.PARAM_PROXYPORT);
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
      String proxyUsername = params.getParameter(SharePointConfig.PARAM_PROXYUSER);
      String proxyPassword = params.getObfuscatedParameter(SharePointConfig.PARAM_PROXYPASSWORD);
      String proxyDomain = params.getParameter(SharePointConfig.PARAM_PROXYDOMAIN);
      
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

      // Set up ssl if indicated
      keystoreData = params.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);

      int connectionTimeout = 60000;
      int socketTimeout = 900000;

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

      proxy = new SPSProxyHelper( serverUrl, encodedServerLocation, serverLocation, userName, password,
        org.apache.manifoldcf.connectorcommon.common.CommonsHTTPSender.class, "client-config.wsdd",
        httpClient );
      
    }
    sessionTimeout = System.currentTimeMillis() + sessionExpirationInterval;
  }

  protected void expireSession()
    throws ManifoldCFException
  {
    serverUrl = null;
    fileBaseUrl = null;
    userName = null;
    strippedUserName = null;
    password = null;
    ntlmDomain = null;
    serverLocation = null;
    encodedServerLocation = null;
    serverPort = -1;

    keystoreData = null;
    keystoreManager = null;

    proxy = null;
    httpClient = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;

  }
  
  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH};
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the root directory.
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
    // This is needed by getBins()
    serverName = configParameters.getParameter( SharePointConfig.PARAM_SERVERNAME );
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    serverUrl = null;
    fileBaseUrl = null;
    userName = null;
    strippedUserName = null;
    password = null;
    ntlmDomain = null;
    serverName = null;
    serverLocation = null;
    encodedServerLocation = null;
    serverPort = -1;

    keystoreData = null;
    keystoreManager = null;

    proxy = null;
    httpClient = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;

    super.disconnect();
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
    return new String[]{serverName};
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    // Since we went to a carrydown-based implementation, having this greater than 1 does not help.
    return 1;
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    getSession();
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
      proxy.checkConnection( "/", supportsItemSecurity );
    }
    catch ( ServiceInterruption e )
    {
      return "SharePoint temporarily unavailable: "+e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      return e.getMessage();
    }

    return super.check();
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    if (proxy != null && System.currentTimeMillis() >= sessionTimeout)
      expireSession();
    if (connectionManager != null)
      connectionManager.closeIdleConnections(60000L,TimeUnit.MILLISECONDS);
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
    if (command.startsWith("fields/"))
    {
      String library;
      String sitePath;
      
      String remainder = command.substring("fields/".length());
      
      try
      {
        int index = remainder.indexOf("/");
        if (index == -1)
        {
          library = remainder;
          sitePath = "";
        }
        else
        {
          library = remainder.substring(0,index);
          sitePath = remainder.substring(index+1);
        }
        
        Map<String,String> fieldSet = getLibFieldList(sitePath,library);
        Iterator<String> iter = fieldSet.keySet().iterator();
        while (iter.hasNext())
        {
          String fieldName = iter.next();
          String displayName = fieldSet.get(fieldName);
          ConfigurationNode node = new ConfigurationNode("field");
          ConfigurationNode child;
          child = new ConfigurationNode("name");
          child.setValue(fieldName);
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("display_name");
          child.setValue(displayName);
          node.addChild(node.getChildCount(),child);
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
    else if (command.startsWith("listfields/"))
    {
      String listName;
      String sitePath;
      
      String remainder = command.substring("listfields/".length());
      
      try
      {
        int index = remainder.indexOf("/");
        if (index == -1)
        {
          listName = remainder;
          sitePath = "";
        }
        else
        {
          listName = remainder.substring(0,index);
          sitePath = remainder.substring(index+1);
        }
        
        Map<String,String> fieldSet = getListFieldList(sitePath,listName);
        Iterator<String> iter = fieldSet.keySet().iterator();
        while (iter.hasNext())
        {
          String fieldName = iter.next();
          String displayName = fieldSet.get(fieldName);
          ConfigurationNode node = new ConfigurationNode("field");
          ConfigurationNode child;
          child = new ConfigurationNode("name");
          child.setValue(fieldName);
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("display_name");
          child.setValue(displayName);
          node.addChild(node.getChildCount(),child);
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
    else if (command.startsWith("sites/"))
    {
      try
      {
        String sitePath = command.substring("sites/".length());
        List<NameValue> sites = getSites(sitePath);
        int i = 0;
        while (i < sites.size())
        {
          NameValue site = sites.get(i++);
          ConfigurationNode node = new ConfigurationNode("site");
          ConfigurationNode child;
          child = new ConfigurationNode("name");
          child.setValue(site.getValue());
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("display_name");
          child.setValue(site.getPrettyName());
          node.addChild(node.getChildCount(),child);
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
    else if (command.startsWith("libraries/"))
    {
      try
      {
        String sitePath = command.substring("libraries/".length());
        List<NameValue> libs = getDocLibsBySite(sitePath);
        int i = 0;
        while (i < libs.size())
        {
          NameValue lib = libs.get(i++);
          ConfigurationNode node = new ConfigurationNode("library");
          ConfigurationNode child;
          child = new ConfigurationNode("name");
          child.setValue(lib.getValue());
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("display_name");
          child.setValue(lib.getPrettyName());
          node.addChild(node.getChildCount(),child);
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
    else if (command.startsWith("lists/"))
    {
      try
      {
        String sitePath = command.substring("lists/".length());
        List<NameValue> libs = getListsBySite(sitePath);
        int i = 0;
        while (i < libs.size())
        {
          NameValue lib = libs.get(i++);
          ConfigurationNode node = new ConfigurationNode("list");
          ConfigurationNode child;
          child = new ConfigurationNode("name");
          child.setValue(lib.getValue());
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("display_name");
          child.setValue(lib.getPrettyName());
          node.addChild(node.getChildCount(),child);
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
    // Check the session
    getSession();
    // Add just the root.
    activities.addSeedDocument("/");
    return "";
  }

  protected static final String[] attachmentDataNames = new String[]{"createdDate","modifiedDate","accessTokens","denyTokens","url","guids"};

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
    // Get the forced acls.  (We need this only for the case where documents have their own acls)
    String[] forcedAcls = getAcls(spec);
    
    SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

    // Look at the metadata attributes.
    // So that the version strings are comparable, we will put them in an array first, and sort them.
    String pathAttributeName = null;
    MatchMap matchMap = new MatchMap();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals("pathnameattribute"))
        pathAttributeName = n.getAttributeValue("value");
      else if (n.getType().equals("pathmap"))
      {
        // Path mapping info also needs to be looked at, because it affects what is
        // ingested.
        String pathMatch = n.getAttributeValue("match");
        String pathReplace = n.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }

    }

    // Calculate the part of the version string that comes from path name and mapping.
    // This starts with = since ; is used by another optional component (the forced acls)
    StringBuilder pathNameAttributeVersion = new StringBuilder();
    if (pathAttributeName != null)
      pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

    for (String documentIdentifier : documentIdentifiers)
    {
      // Check if we should abort
      activities.checkJobStillActive();

      getSession();

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug( "SharePoint: Getting version of '" + documentIdentifier + "'");
      if ( documentIdentifier.startsWith("D") || documentIdentifier.startsWith("S") )
      {
        // Old-style document identifier.  We don't recognize these anymore, so signal deletion.
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Removing old-style document identifier '"+documentIdentifier+"'");
        activities.deleteDocument(documentIdentifier);
        continue;
      }
      else if (documentIdentifier.startsWith("/"))
      {
        // New-style document identifier.  A double-slash marks the separation between the library and folder/file levels.
        // A triple-slash marks the separation between a list name and list row ID.
        int dListSeparatorIndex = documentIdentifier.indexOf("///");
        int dLibSeparatorIndex = documentIdentifier.indexOf("//");
        if (dListSeparatorIndex != -1)
        {
          // === List-style identifier ===
          if (dListSeparatorIndex == documentIdentifier.length() - 3)
          {
            // == List path! ==
            if (!checkIncludeList(documentIdentifier.substring(0,documentIdentifier.length()-3),spec))
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: List specification no longer includes list '"+documentIdentifier+"' - removing");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // Version string for a list
            String versionString = "";

            // Chained connectors always scan parent nodes, so they don't bother setting a version
            String siteListPath = documentIdentifier.substring(0,documentIdentifier.length()-3);
            int listCutoff = siteListPath.lastIndexOf( "/" );
            String site = siteListPath.substring(0,listCutoff);
            String listName = siteListPath.substring( listCutoff + 1 );

            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Document identifier is a list: '" + siteListPath + "'" );

            String listID = proxy.getListID( encodePath(site), site, listName );
            if (listID == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: GUID lookup failed for list '"+siteListPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            String encodedSitePath = encodePath(site);
                
            // Get the list's fields
            Map<String,String> fieldNames = proxy.getFieldList( encodedSitePath, listID );
            if (fieldNames == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Field list lookup failed for list '"+siteListPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // Note well: There's been a lot of back-and forth about this code.
            // See CONNECTORS-1324.
            // The fieldNames map returned by proxy.getFieldList() has the internal name as a key, and the display name as a value.
            // Since we want the complete list of fields here, by *internal* name, we iterate over the keySet(), not the values.
            String[] fields = new String[fieldNames.size()];
            int j = 0;
            for (String field : fieldNames.keySet())
            {
              fields[j++] = field;
            }
                  
            String[] accessTokens;
            String[] denyTokens;
                  
            if (forcedAcls == null)
            {
              // Security is off
              accessTokens = new String[0];
              denyTokens = new String[0];
            }
            else if (forcedAcls.length != 0)
            {
              // Forced security
              accessTokens = forcedAcls;
              denyTokens = new String[0];
            }
            else
            {
              // Security enabled, native security
              accessTokens = proxy.getACLs( encodedSitePath, listID, activeDirectoryAuthority );
              denyTokens = new String[]{defaultAuthorityDenyToken};
            }

            if (accessTokens == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Access token lookup failed for list '"+siteListPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            ListItemStream fs = new ListItemStream( activities, encodedServerLocation, site, siteListPath, spec,
              documentIdentifier, accessTokens, denyTokens, listID, fields );
            boolean success = proxy.getChildren( fs, encodedSitePath , listID, dspStsWorks );
            if (!success)
            {
              // Site/list no longer exists, so delete entry
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: No list found for list '"+siteListPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            activities.noDocument(documentIdentifier,versionString);
          }
          else
          {
            // == List item or attachment path! ==
            // Convert the modified document path to an unmodified one, plus a library path.
            String decodedListPath = documentIdentifier.substring(0,dListSeparatorIndex);
            String itemAndAttachment = documentIdentifier.substring(dListSeparatorIndex+2);
            String decodedItemPath = decodedListPath + itemAndAttachment;
            
            int cutoff = decodedListPath.lastIndexOf("/");
            String sitePath = decodedListPath.substring(0,cutoff);
            String list = decodedListPath.substring(cutoff+1);

            String encodedSitePath = encodePath(sitePath);

            int attachmentSeparatorIndex = itemAndAttachment.indexOf("//",1);
            if (attachmentSeparatorIndex == -1)
            {
              // == List item path! ==
              if (!checkIncludeListItem(decodedItemPath,spec))
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: List item '"+documentIdentifier+"' is no longer included - removing");
                activities.deleteDocument(documentIdentifier);
                continue;
              }

              // This file is included, so calculate a version string.  This will include metadata info, so get that first.
              MetadataInformation metadataInfo = getMetadataSpecification(decodedItemPath,spec);

              String[] accessTokens = activities.retrieveParentData(documentIdentifier, "accessTokens");
              String[] denyTokens = activities.retrieveParentData(documentIdentifier, "denyTokens");
              String[] listIDs = activities.retrieveParentData(documentIdentifier, "guids");
              String[] listFields = activities.retrieveParentData(documentIdentifier, "fields");
              String[] displayURLs = activities.retrieveParentData(documentIdentifier, "displayURLs");
                
              String listID;
              if (listIDs.length >= 1)
                listID = listIDs[0];
              else
                listID = null;

              String displayURL;
              if (displayURLs.length >= 1)
                displayURL = displayURLs[0];
              else
                displayURL = null;

              if (listID == null)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because list '"+decodedListPath+"' does not exist - removing");
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              
              // Get the fields we want (internal field names only at this point), given the list of all fields (internal field names again).
              String[] sortedMetadataFields = getInterestingFieldSetSorted(metadataInfo,listFields);
                  
              // Sort access tokens so they are comparable in the version string
              java.util.Arrays.sort(accessTokens);
              java.util.Arrays.sort(denyTokens);

              // Next, get the actual timestamp field for the file.
              List<String> metadataDescription = new ArrayList<String>();
              metadataDescription.add("Modified");
              metadataDescription.add("Created");
              metadataDescription.add("ID");
              metadataDescription.add("GUID");
              // The document path includes the library, with no leading slash, and is decoded.
              String decodedItemPathWithoutSite = decodedItemPath.substring(cutoff+1);
              Map<String,String> values = proxy.getFieldValues( metadataDescription.toArray(new String[0]), encodedSitePath, listID, "/Lists/" + decodedItemPathWithoutSite, dspStsWorks );
              if (values == null) {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because of bad XML characters(?)");
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              String modifiedDate = values.get("Modified");
              String createdDate = values.get("Created");
              String id = values.get("ID");
              String guid = values.get("GUID");
              if (modifiedDate == null)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has no modify date");
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              
              // Item has a modified date so we presume it exists.
                  
              Date modifiedDateValue = DateParser.parseISO8601Date(modifiedDate);
              Date createdDateValue = DateParser.parseISO8601Date(createdDate);
                    
              // Build version string
              String versionToken = modifiedDate;
                      
              // Revamped version string on 9/21/2013 to make parseability better
              
              StringBuilder sb = new StringBuilder();

              packList(sb,sortedMetadataFields,'+');
              packList(sb,accessTokens,'+');
              packList(sb,denyTokens,'+');
              packDate(sb,modifiedDateValue);
              packDate(sb,createdDateValue);
              pack(sb,id,'+');
              pack(sb,guid,'+');
              pack(sb,displayURL,'+');
              // The rest of this is unparseable
              sb.append(versionToken);
              sb.append(pathNameAttributeVersion);
              // Added 9/7/07
              sb.append("_").append(fileBaseUrl);
              //
              String versionString = sb.toString();
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + versionString);
              
              // Before we index, we queue up any attachments

              // Now, do any queuing that is needed.
              if (attachmentsSupported)
              {
                String itemNumber = id;


                List<NameValue> attachmentNames = proxy.getAttachmentNames( sitePath, listID, itemNumber );
                // Now, queue up each attachment as a separate entry
                for (NameValue attachmentName : attachmentNames)
                {
                  // For attachments, we use the carry-down feature to get the data where we need it.  That's why
                  // we unpacked the version information early above.
                  
                  // No check for inclusion; if the list item is included, so is this
                  String[][] dataValues = new String[attachmentDataNames.length][];
                  if (createdDateValue == null)
                    dataValues[0] = new String[0];
                  else
                    dataValues[0] = new String[]{new Long(createdDateValue.getTime()).toString()};
                  if (modifiedDateValue == null)
                    dataValues[1] = new String[0];
                  else
                    dataValues[1] = new String[]{new Long(modifiedDateValue.getTime()).toString()};
                  if (accessTokens == null)
                    dataValues[2] = new String[0];
                  else
                    dataValues[2] = accessTokens;
                  if (denyTokens == null)
                    dataValues[3] = new String[0];
                  else
                    dataValues[3] = denyTokens;
                  dataValues[4] = new String[]{attachmentName.getPrettyName()};
                  dataValues[5] = new String[]{guid};

                  activities.addDocumentReference(documentIdentifier + "//" + attachmentName.getValue(),
                    documentIdentifier, null, attachmentDataNames, dataValues);
                  
                }
              }

              if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
                continue;
                            
              // Convert the modified document path to an unmodified one, plus a library path.
              String encodedItemPath = encodePath(decodedListPath.substring(0,cutoff) + "/Lists/" + decodedItemPath.substring(cutoff+1));
                
              // Generate the URL we are going to use
              String itemUrl = serverUrl + displayURL;  //fileBaseUrl + encodedItemPath;
                
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug( "SharePoint: Processing list item '"+documentIdentifier+"'; url: '" + itemUrl + "'" );

              // Fetch the metadata we will be indexing
              Map<String,String> metadataValues = null;
              if (sortedMetadataFields.length > 0)
              {
                metadataValues = proxy.getFieldValues( sortedMetadataFields, encodePath(sitePath), listID, "/Lists/" + decodedItemPath.substring(cutoff+1), dspStsWorks );
                if (metadataValues == null)
                {
                  // Item has vanished
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Item metadata fetch failure indicated that item is gone: '"+documentIdentifier+"' - removing");
                  activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,"NOMETADATA","List item metadata is missing",null);
                  activities.noDocument(documentIdentifier,versionString);
                  continue;
                }
              }
                
              if (!activities.checkLengthIndexable(0L))
              {
                // Document too long (should never happen; length is 0)
                activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,activities.EXCLUDED_LENGTH,"List item excluded due to content length (0)",null);
                activities.noDocument( documentIdentifier, versionString );
                continue;
              }
                
              InputStream is = new ByteArrayInputStream(new byte[0]);
              try
              {
                RepositoryDocument data = new RepositoryDocument();
                data.setBinary( is, 0L );

                if (modifiedDateValue != null)
                  data.setModifiedDate(modifiedDateValue);
                if (createdDateValue != null)
                  data.setCreatedDate(createdDateValue);
                    
                setDataACLs(data,accessTokens,denyTokens);
                
                setPathAttribute(data,sDesc,documentIdentifier);

                if (metadataValues != null)
                {
                  Iterator<String> iter = metadataValues.keySet().iterator();
                  while (iter.hasNext())
                  {
                    String fieldName = iter.next();
                    String fieldData = metadataValues.get(fieldName);
                    data.addField(fieldName,fieldData);
                  }
                }
                data.addField("GUID",guid);
                try
                {
                  activities.ingestDocumentWithException( documentIdentifier, versionString, itemUrl , data );
                }
                catch (IOException e)
                {
                  handleIOException(e,"reading document");
                }
              }
              finally
              {
                try
                {
                  is.close();
                }
                catch (IOException e)
                {
                  handleIOException(e,"closing stream");
                }
              }

            }
            else
            {
              // == List item attachment path! ==
              if (!checkIncludeListItemAttachment(decodedItemPath,spec))
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: List item attachment '"+documentIdentifier+"' is no longer included - removing");
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              
              // To save work, we retrieve most of what we need in version info from the parent.

              // Retrieve modified and created dates
              String[] modifiedDateSet = activities.retrieveParentData(documentIdentifier, "modifiedDate");
              String[] createdDateSet = activities.retrieveParentData(documentIdentifier, "createdDate");
              String[] accessTokens = activities.retrieveParentData(documentIdentifier, "accessTokens");
              String[] denyTokens = activities.retrieveParentData(documentIdentifier, "denyTokens");
              String[] urlSet = activities.retrieveParentData(documentIdentifier, "url");

              // Only one modifiedDate and createdDate can be used.  If there's more than one, just pick one - the item will be reindexed
              // anyhow.
              String modifiedDate;
              if (modifiedDateSet.length >= 1)
                modifiedDate = modifiedDateSet[0];
              else
                modifiedDate = null;
              String createdDate;
              if (createdDateSet.length >= 1)
                createdDate = createdDateSet[0];
              else
                createdDate = null;
              String url;
              if (urlSet.length >=1)
                url = urlSet[0];
              else
                url = null;

              // If we have no modified or created date, it means that the parent has gone away, so we go away too.
              if (modifiedDate == null || url == null)
              {
                // Can't look up list ID, which means the list is gone, so delete
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because modified date or attachment url not found");
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              
              // Item has a modified date so we presume it exists.
                      
              Date modifiedDateValue;
              if (modifiedDate != null)
                modifiedDateValue = new Date(new Long(modifiedDate).longValue());
              else
                modifiedDateValue = null;
              Date createdDateValue;
              if (createdDate != null)
                createdDateValue = new Date(new Long(createdDate).longValue());
              else
                createdDateValue = null;
                      
              // Build version string
              String versionToken = modifiedDate;
                      
              StringBuilder sb = new StringBuilder();

              // Pack the URL to get the data from
              pack(sb,url,'+');
                  
              // Do the acls.  If we get this far, we are guaranteed to have them, but we need to sort.
              java.util.Arrays.sort(accessTokens);
              java.util.Arrays.sort(denyTokens);
                  
              packList(sb,accessTokens,'+');
              packList(sb,denyTokens,'+');
              packDate(sb,modifiedDateValue);
              packDate(sb,createdDateValue);

              // The rest of this is unparseable
              sb.append(versionToken);
              sb.append(pathNameAttributeVersion);
              sb.append("_").append(fileBaseUrl);
              //
              String versionString = sb.toString();
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + versionString);

              if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
                continue;
              
              // We need the list ID, which we've already fetched, so grab that from the parent data.
              String[] guids = activities.retrieveParentData(documentIdentifier, "guids");
              String guid;
              if (guids.length >= 1)
                guid = guids[0];
              else
                guid = null;
                
              if (guid == null)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Skipping attachment '"+documentIdentifier+"' because no parent guid found");
                activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,"NOGUID","List item attachment GUID is missing",null);
                activities.noDocument(documentIdentifier,versionString);
                continue;
              }
              
              int lastIndex = url.lastIndexOf("/");
              guid = guid + ":" + url.substring(lastIndex+1);
                  
              // Fetch and index.  This also filters documents based on output connector restrictions.
              String fileUrl = serverUrl + encodePath(url);
              String fetchUrl = fileUrl;
              fetchAndIndexFile(activities, documentIdentifier, versionString, fileUrl, fetchUrl,
                accessTokens, denyTokens, createdDateValue, modifiedDateValue, null, guid, sDesc);
            }
          }
        }
        else if (dLibSeparatorIndex != -1)
        {
          // === Library-style identifier ===
          if (dLibSeparatorIndex == documentIdentifier.length() - 2)
          {
            // Library path!
            if (!checkIncludeLibrary(documentIdentifier.substring(0,documentIdentifier.length()-2),spec))
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Library specification no longer includes library '"+documentIdentifier+"' - removing");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
              
            // This is the path for the library: No versioning
            String versionString = "";
            // Chained document parents are always rescanned
            String siteLibPath = documentIdentifier.substring(0,documentIdentifier.length()-2);
            int libCutoff = siteLibPath.lastIndexOf( "/" );
            String site = siteLibPath.substring(0,libCutoff);
            String libName = siteLibPath.substring( libCutoff + 1 );

            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Document identifier is a library: '" + siteLibPath + "'" );

            String libID = proxy.getDocLibID( encodePath(site), site, libName );
            if (libID == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: GUID lookup failed for library '"+siteLibPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            String encodedSitePath = encodePath(site);
              
            // Get the lib's fields
            Map<String,String> fieldNames = proxy.getFieldList( encodedSitePath, libID );
            if (fieldNames == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Field list lookup failed for library '"+siteLibPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // See CONNECTORS-1324.  We want internal field names only,
            // which are the keys of the map.
            String[] fields = new String[fieldNames.size()];
            int j = 0;
            for (String field : fieldNames.keySet())
            {
              fields[j++] = field;
            }
                  
            String[] accessTokens;
            String[] denyTokens;
                  
            if (forcedAcls == null)
            {
              // Security is off
              accessTokens = new String[0];
              denyTokens = new String[0];
            }
            else if (forcedAcls.length != 0)
            {
              // Forced security
              accessTokens = forcedAcls;
              denyTokens = new String[0];
            }
            else
            {
              // Security enabled, native security
              accessTokens = proxy.getACLs( encodedSitePath, libID, activeDirectoryAuthority );
              denyTokens = new String[]{defaultAuthorityDenyToken};
            }

            if (accessTokens == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Access token lookup failed for library '"+siteLibPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            FileStream fs = new FileStream( activities, encodedServerLocation, site, siteLibPath, spec,
              documentIdentifier, accessTokens, denyTokens, libID, fields );
            
            boolean success = proxy.getChildren( fs, encodedSitePath , libID, dspStsWorks );
            
            if (!success)
            {
              // Site/library no longer exists, so delete entry
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: No list found for library '"+siteLibPath+"' - deleting");
              activities.deleteDocument(documentIdentifier);
              continue;
            }

            activities.noDocument(documentIdentifier,versionString);
          }
          else
          {
            // == Document path ==
            // Convert the modified document path to an unmodified one, plus a library path.
            String decodedLibPath = documentIdentifier.substring(0,dLibSeparatorIndex);
            String decodedDocumentPath = decodedLibPath + documentIdentifier.substring(dLibSeparatorIndex+1);
            if (!checkIncludeFile(decodedDocumentPath,spec))
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Document '"+documentIdentifier+"' is no longer included - removing");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // This file is included, so calculate a version string.  This will include metadata info, so get that first.
            MetadataInformation metadataInfo = getMetadataSpecification(decodedDocumentPath,spec);
            
            int lastIndex = decodedLibPath.lastIndexOf("/");
            String sitePath = decodedLibPath.substring(0,lastIndex);
            String lib = decodedLibPath.substring(lastIndex+1);

            // Retrieve the carry-down data we will be using.
            // Note well: for sharepoint versions that include document/folder acls, these access tokens will be ignored,
            // but they will still be carried down nonetheless, in case someone switches versions on us.
            String[] accessTokens = activities.retrieveParentData(documentIdentifier, "accessTokens");
            String[] denyTokens = activities.retrieveParentData(documentIdentifier, "denyTokens");
            String[] libIDs = activities.retrieveParentData(documentIdentifier, "guids");
            String[] libFields = activities.retrieveParentData(documentIdentifier, "fields");

            String libID;
            if (libIDs.length >= 1)
              libID = libIDs[0];
            else
              libID = null;

            if (libID == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because library '"+decodedLibPath+"' does not exist - removing");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            String encodedSitePath = encodePath(sitePath);
            // Get the fields we want (internal field names only at this point), given the list of all fields (internal field names again).
            String[] sortedMetadataFields = getInterestingFieldSetSorted(metadataInfo,libFields);
                
            // Sort access tokens
            java.util.Arrays.sort(accessTokens);
            java.util.Arrays.sort(denyTokens);

            // Next, get the actual timestamp field for the file.
            List<String> metadataDescription = new ArrayList<String>();
            metadataDescription.add("Last_x0020_Modified");
            metadataDescription.add("Modified");
            metadataDescription.add("Created");
            metadataDescription.add("GUID");
            // The document path includes the library, with no leading slash, and is decoded.
            int cutoff = decodedLibPath.lastIndexOf("/");
            String decodedDocumentPathWithoutSite = decodedDocumentPath.substring(cutoff);
            Map<String,String> values = proxy.getFieldValues( metadataDescription.toArray(new String[0]), encodedSitePath, libID, decodedDocumentPathWithoutSite, dspStsWorks );
            if (values == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has bad characters(?)");
              activities.deleteDocument(documentIdentifier);
              continue;
            }

            String modifiedDate = values.get("Modified");
            String createdDate = values.get("Created");
            String guid = values.get("GUID");
            String modifyDate = values.get("Last_x0020_Modified");

            if (modifyDate == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has no modify date");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // Item has a modified date, so we presume it exists
            Date modifiedDateValue = DateParser.parseISO8601Date(modifiedDate);
            Date createdDateValue = DateParser.parseISO8601Date(createdDate);

            // Build version string
            String versionToken = modifyDate;

            if (supportsItemSecurity)
            {
              // Do the acls.
              if (forcedAcls == null)
              {
                // Security is off
                accessTokens = new String[0];
                denyTokens = new String[0];
              }
              else if (forcedAcls.length > 0)
              {
                // Security on, forced acls
                accessTokens = forcedAcls;
                denyTokens = new String[0];
              }
              else
              {
                // Security on, is native
                accessTokens = proxy.getDocumentACLs( encodedSitePath, encodePath(decodedDocumentPath), activeDirectoryAuthority );
                denyTokens = new String[]{defaultAuthorityDenyToken};
              }
            }
                  
            if (accessTokens == null)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Couldn't get access tokens for item '"+decodedDocumentPath+"'; removing document '"+documentIdentifier+"'");
              activities.deleteDocument(documentIdentifier);
              continue;
            }
            
            // Revamped version string on 9/21/2013 to make parseability better

            StringBuilder sb = new StringBuilder();
            packList(sb,sortedMetadataFields,'+');
            packList(sb,accessTokens,'+');
            packList(sb,denyTokens,'+');
            packDate(sb,modifiedDateValue);
            packDate(sb,createdDateValue);
            pack(sb,guid,'+');
            // The rest of this is unparseable
            sb.append(versionToken);
            sb.append(pathNameAttributeVersion);
            // Added 9/7/07
            sb.append("_").append(fileBaseUrl);
            //
            String versionString = sb.toString();
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + versionString);
            
            if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
              continue;
            
            // Convert the modified document path to an unmodified one, plus a library path.
            String encodedDocumentPath = encodePath(decodedDocumentPath);

            // Parse what we need out of version string.

            // Generate the URL we are going to use
            String fileUrl = fileBaseUrl + encodedDocumentPath;
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Processing file '"+documentIdentifier+"'; url: '" + fileUrl + "'" );

            // First, fetch the metadata we plan to index.
            Map<String,String> metadataValues = null;
            if (sortedMetadataFields.length > 0)
            {
              metadataValues = proxy.getFieldValues( sortedMetadataFields, encodePath(sitePath), libID, decodedDocumentPath.substring(cutoff), dspStsWorks );
              if (metadataValues == null)
              {
                // Document has vanished
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Document metadata fetch failure indicated that document is gone: '"+documentIdentifier+"' - removing");
                activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,"NOMETADATA","Document metadata is missing",null);
                activities.noDocument(documentIdentifier,versionString);
                continue;
              }
            }

            // Fetch and index.  This also filters documents based on output connector restrictions.
            fetchAndIndexFile(activities, documentIdentifier, versionString, fileUrl, serverUrl + encodedServerLocation + encodedDocumentPath,
              accessTokens, denyTokens, createdDateValue, modifiedDateValue, metadataValues, guid, sDesc);
          }
        }
        else
        {
          // === Site-style identifier ===
          String sitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);
          if (sitePath.length() == 0)
            sitePath = "/";
          if (!checkIncludeSite(sitePath,spec))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Site specification no longer includes site '"+documentIdentifier+"' - removing");
            activities.deleteDocument(documentIdentifier);
            continue;
          }
          
          String versionString = "";
          activities.noDocument(documentIdentifier,versionString);

          // Strip off the trailing "/" to get the site name.
          String decodedSitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);

          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug( "SharePoint: Document identifier is a site: '" + decodedSitePath + "'" );

          // Look at subsites
          List<NameValue> subsites = proxy.getSites( encodePath(decodedSitePath) );
          if (subsites != null)
          {
            for (NameValue subSiteName : subsites)
            {
              String newPath = decodedSitePath + "/" + subSiteName.getValue();

              String encodedNewPath = encodePath(newPath);
              if ( checkIncludeSite(newPath,spec) )
                activities.addDocumentReference(newPath + "/");
            }
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: No permissions to access subsites of '"+decodedSitePath+"' - skipping");
          }

          // Look at libraries
          List<NameValue> libraries = proxy.getDocumentLibraries( encodePath(decodedSitePath), decodedSitePath );
          if (libraries != null)
          {
            for (NameValue library : libraries)
            {
              String newPath = decodedSitePath + "/" + library.getValue();

              if (checkIncludeLibrary(newPath,spec))
                activities.addDocumentReference(newPath + "//");

            }
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: No permissions to access libraries of '"+decodedSitePath+"' - skipping");
          }

          // Look at lists
          List<NameValue> lists = proxy.getLists( encodePath(decodedSitePath), decodedSitePath );
          if (lists != null)
          {
            for (NameValue list : lists)
            {
              String newPath = decodedSitePath + "/" + list.getValue();

              if (checkIncludeList(newPath,spec))
                activities.addDocumentReference(newPath + "///");

            }
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: No permissions to access lists of '"+decodedSitePath+"' - skipping");
          }

        }
      }
      else
        throw new ManifoldCFException("Invalid document identifier discovered: '"+documentIdentifier+"'");
    }
  }

  protected static void packDate(StringBuilder sb, Date dateValue)
  {
    if (dateValue != null)
    {
      sb.append("+");
      pack(sb,new Long(dateValue.getTime()).toString(),'+');
    }
    else
      sb.append("-");
  }

  protected static int unpackDate(String value, int index, Date theDate)
  {
    if (value.length() > index)
    {
      if (value.charAt(index++) == '+')
      {
        StringBuilder sb = new StringBuilder();
        index = unpack(sb,value,index,'+');
        if (sb.length() > 0)
        {
          theDate.setTime(new Long(sb.toString()).longValue());
        }
      }
    }
    return index;
  }
  
  protected String[] getInterestingFieldSetSorted(MetadataInformation metadataInfo, String[] allFields)
  {
    Set<String> metadataFields = new HashSet<String>();

    // Figure out the actual metadata fields we will request
    if (metadataInfo.getAllMetadata())
    {
      for (String field : allFields)
      {
        metadataFields.add(field);
      }
    }
    else
    {
      String[] fields = metadataInfo.getMetadataFields();
      for (String field : fields)
      {
        metadataFields.add(field);
      }
    }
    
    // Convert the hashtable to an array and sort it.
    String[] sortedMetadataFields = new String[metadataFields.size()];
    int z = 0;
    for (String field : metadataFields)
    {
      sortedMetadataFields[z++] = field;
    }
    java.util.Arrays.sort(sortedMetadataFields);

    return sortedMetadataFields;
  }

  /** Method that fetches and indexes a file fetched from a SharePoint URL, with appropriate error handling
  * etc.
  */
  protected void fetchAndIndexFile(IProcessActivity activities, String documentIdentifier, String version,
    String fileUrl, String fetchUrl, String[] accessTokens, String[] denyTokens, Date createdDate, Date modifiedDate,
    Map<String,String> metadataValues, String guid, SystemMetadataDescription sDesc)
    throws ManifoldCFException, ServiceInterruption
  {
    String errorCode = null;
    String errorDesc = null;
    long startTime = System.currentTimeMillis();
    Long fileLengthLong = null;
    
    try
    {
      // Before we fetch, confirm that the output connector will accept the document
      if (!activities.checkURLIndexable(fileUrl))
      {
        // URL failed
        errorCode = activities.EXCLUDED_URL;
        errorDesc = "Document rejected because of URL ("+fileUrl+")";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Skipping document '"+documentIdentifier+"' because output connector says URL '"+fileUrl+"' is not indexable");
        activities.noDocument(documentIdentifier, version);
        return;
      }
      
      // Also check mime type
      String contentType = mapExtensionToMimeType(documentIdentifier);
      if (!activities.checkMimeTypeIndexable(contentType))
      {
        // Mime type failed
        errorCode = activities.EXCLUDED_MIMETYPE;
        errorDesc = "Document rejected because of mime type ("+contentType+")";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Skipping document '"+documentIdentifier+"' because output connector says mime type '"+((contentType==null)?"null":contentType)+"' is not indexable");
        activities.noDocument(documentIdentifier, version);
        return;
      }
      
      // Now check date stamp
      if (!activities.checkDateIndexable(modifiedDate))
      {
        // Date failed
        errorCode = activities.EXCLUDED_DATE;
        errorDesc = "Document rejected because of date ("+modifiedDate+")";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Skipping document '"+documentIdentifier+"' because output connector says date '"+((modifiedDate==null)?"null":modifiedDate)+"' is not indexable");
        activities.noDocument(documentIdentifier, version);
        return;
      }
      
      // Set stuff up for fetch activity logging
      try
      {
        // Read the document into a local temporary file, so I get a reliable length.
        File tempFile = File.createTempFile("__shp__",".tmp");
        try
        {
          // Open the output stream
          OutputStream os = new FileOutputStream(tempFile);
          try
          {
            // Catch all exceptions having to do with reading the document
            try
            {
              ExecuteMethodThread emt = new ExecuteMethodThread(httpClient, fetchUrl, os);
              emt.start();
              int returnCode = emt.finishUp();
                    
              if (returnCode == 404 || returnCode == 401 || returnCode == 400 || returnCode == 415)
              {
                // Well, sharepoint thought the document was there, but it really isn't, so delete it.
                errorCode = "DOCUMENTNOTFOUND";
                errorDesc = "Document not found; HTTP code "+returnCode;
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Document at '"+fileUrl+"' failed to fetch with code "+Integer.toString(returnCode)+", deleting");
                activities.noDocument(documentIdentifier, version);
                return;
              }
              else if (returnCode != 200)
              {
                errorCode = "UNKNOWNHTTPCODE";
                errorDesc = "Unknown HTTP return code "+returnCode;
                throw new ManifoldCFException("Error fetching document '"+fileUrl+"': "+Integer.toString(returnCode));
              }

            }
            catch (InterruptedException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            catch (java.net.SocketTimeoutException e)
            {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.warn("SharePoint: SocketTimeoutException thrown: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,true);
            }
            catch (org.apache.http.conn.ConnectTimeoutException e)
            {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.warn("SharePoint: ConnectTimeoutException thrown: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,true);
            }
            catch (InterruptedIOException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            catch (IllegalArgumentException e)
            {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.error("SharePoint: Illegal argument: "+e.getMessage(), e);
              throw new ManifoldCFException("SharePoint: Illegal argument: "+e.getMessage(),e);
            }
            catch (org.apache.http.HttpException e)
            {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.warn("SharePoint: HttpException thrown: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,true);
            }
            catch (IOException e)
            {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.warn("SharePoint: IOException thrown: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,true);
            }
          }
          finally
          {
            os.close();
          }
                        
          // Ingest the document
          long documentLength = tempFile.length();
          if (!activities.checkLengthIndexable(documentLength))
          {
            // Document too long
            errorCode = activities.EXCLUDED_LENGTH;
            errorDesc = "Document excluded due to length ("+documentLength+")";
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Document '"+documentIdentifier+"' was too long, according to output connector");
            activities.noDocument(documentIdentifier, version);
            return;
          }
          
          InputStream is = new FileInputStream(tempFile);
          try
          {
            RepositoryDocument data = new RepositoryDocument();
            data.setBinary( is, documentLength );
                  
            data.setFileName(mapToFileName(documentIdentifier));
                            
            if (contentType != null)
              data.setMimeType(contentType);
                  
            setDataACLs(data,accessTokens,denyTokens);

            setPathAttribute(data,sDesc,documentIdentifier);
            
            if (modifiedDate != null)
              data.setModifiedDate(modifiedDate);
            if (createdDate != null)
              data.setCreatedDate(createdDate);

            if (metadataValues != null)
            {
              Iterator<String> iter = metadataValues.keySet().iterator();
              while (iter.hasNext())
              {
                String fieldName = iter.next();
                String fieldData = metadataValues.get(fieldName);
                data.addField(fieldName,fieldData);
              }
            }
            data.addField("GUID",guid);
                  
            try
            {
              activities.ingestDocumentWithException( documentIdentifier, version, fileUrl , data );
              errorCode = "OK";
              fileLengthLong = new Long(documentLength);
            }
            catch (IOException e)
            {
              handleIOException(e,"reading document");
            }
            return;
          }
          finally
          {
            try
            {
              is.close();
            }
            catch (java.net.SocketTimeoutException e)
            {
              // This is not fatal
              Logging.connectors.debug("SharePoint: Timeout before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
            }
            catch (org.apache.http.conn.ConnectTimeoutException e)
            {
              // This is not fatal
              Logging.connectors.debug("SharePoint: Connect timeout before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
            }
            catch (InterruptedIOException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            catch (IOException e)
            {
              // This is not fatal
              Logging.connectors.debug("SharePoint: Server closed connection before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
            }
          }
        }
        finally
        {
          tempFile.delete();
        }
      }
      catch (java.net.SocketTimeoutException e)
      {
        throw new ManifoldCFException("Socket timeout error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
      }
      catch (org.apache.http.conn.ConnectTimeoutException e)
      {
        throw new ManifoldCFException("Connect timeout error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("IO error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
      }
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        errorCode = null;
      throw e;
    }
    finally
    {
      if (errorCode != null)
        activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
          fileLengthLong,documentIdentifier,errorCode,errorDesc,null);
    }
  }

  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if (e instanceof java.net.SocketTimeoutException)
    {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("SharePoint is down attempting to "+context+", retrying: "+e.getMessage(),e,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    else if (e instanceof org.apache.http.conn.ConnectTimeoutException)
    {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("SharePoint is down attempting to "+context+", retrying: "+e.getMessage(),e,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    else if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    else
      throw new ManifoldCFException(e.getMessage(),e);
  }
  
  /** Map an extension to a mime type */
  protected static String mapExtensionToMimeType(String fileName)
  {
    int slashIndex = fileName.lastIndexOf("/");
    if (slashIndex != -1)
      fileName = fileName.substring(slashIndex+1);
    int dotIndex = fileName.lastIndexOf(".");
    if (dotIndex == -1)
      return null;
    return ExtensionMimeMap.mapToMimeType(fileName.substring(dotIndex+1));
  }

  /** Map document identifier to file name */
  protected static String mapToFileName(String fileName)
  {
    int slashIndex = fileName.lastIndexOf("/");
    if (slashIndex != -1)
      fileName = fileName.substring(slashIndex+1);
    return fileName;
  }
  
  protected static void setDataACLs(RepositoryDocument data, String[] acls, String[] denyAcls)
  {
    if (acls != null)
    {
      if (Logging.connectors.isDebugEnabled())
      {
        StringBuilder sb = new StringBuilder("SharePoint: Acls: [ ");
        for (String acl : acls)
        {
          sb.append(acl).append(" ");
        }
        sb.append("]");
        Logging.connectors.debug( sb.toString() );
      }

      data.setSecurityACL( RepositoryDocument.SECURITY_TYPE_DOCUMENT, acls );
    }

    if (denyAcls != null)
    {
      if (Logging.connectors.isDebugEnabled())
      {
        StringBuilder sb = new StringBuilder("SharePoint: DenyAcls: [ ");
        for (String denyAcl : denyAcls)
        {
          sb.append(denyAcl).append(" ");
        }
        sb.append("]");
        Logging.connectors.debug( sb.toString() );
      }

      data.setSecurityDenyACL( RepositoryDocument.SECURITY_TYPE_DOCUMENT, denyAcls);
    }
  }

  protected static void setPathAttribute(RepositoryDocument data, SystemMetadataDescription sDesc, String documentIdentifier)
    throws ManifoldCFException
  {
    // Add the path metadata item into the mix, if enabled
    String pathAttributeName = sDesc.getPathAttributeName();
    if (pathAttributeName != null && pathAttributeName.length() > 0)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Path attribute name is '"+pathAttributeName+"'");
      String pathString = sDesc.getPathAttributeValue(documentIdentifier);
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Path attribute value is '"+pathString+"'");
      data.addField(pathAttributeName,pathString);
    }
    else
      Logging.connectors.debug("SharePoint: Path attribute name is null");
  }

  protected final static String[] fileStreamDataNames = new String[]{"accessTokens", "denyTokens", "guids", "fields"};

  protected class FileStream implements IFileStream
  {
    protected final IProcessActivity activities;
    protected final Specification spec;
    protected final String rootPath;
    protected final String sitePath;
    protected final String siteLibPath;
    
    // For carry-down
    protected final String documentIdentifier;
    protected final String[][] dataValues;
    
    public FileStream(IProcessActivity activities, String rootPath, String sitePath, String siteLibPath, Specification spec,
      String documentIdentifier, String[] accessTokens, String denyTokens[], String libID, String[] fields)
    {
      this.activities = activities;
      this.spec = spec;
      this.rootPath = rootPath;
      this.sitePath = sitePath;
      this.siteLibPath = siteLibPath;
      this.documentIdentifier = documentIdentifier;
      this.dataValues = new String[fileStreamDataNames.length][];
      this.dataValues[0] = accessTokens;
      this.dataValues[1] = denyTokens;
      this.dataValues[2] = new String[]{libID};
      this.dataValues[3] = fields;
    }
    
    @Override
    public void addFile(String relPath, String displayURL)
      throws ManifoldCFException
    {

      // First, convert the relative path to a full path
      if ( !relPath.startsWith("/") )
      {
        relPath = rootPath + sitePath + "/" + relPath;
      }
      
      // Now, strip away what we don't want - namely, the root path.  This makes the path relative to the root.
      if ( relPath.startsWith(rootPath) )
      {
        relPath = relPath.substring(rootPath.length());
      
        if ( checkIncludeFile( relPath, spec ) )
        {
          // Since the processing for a file needs to know the library path, we need a way to signal the cutoff between library and folder levels.
          // The way I've chosen to do this is to use a double slash at that point, as a separator.
          if (relPath.startsWith(siteLibPath))
          {
            // Split at the libpath/file boundary
            String modifiedPath = siteLibPath + "/" + relPath.substring(siteLibPath.length());
            activities.addDocumentReference( modifiedPath, documentIdentifier, null, fileStreamDataNames, dataValues );
          }
          else
          {
            Logging.connectors.warn("SharePoint: Unexpected relPath structure; path is '"+relPath+"', but expected to see something beginning with '"+siteLibPath+"'");
          }
        }
      }
      else
      {
        Logging.connectors.warn("SharePoint: Unexpected relPath structure; path is '"+relPath+"', but expected to see something beginning with '"+rootPath+"'");
      }
    }
  }
  
  protected final static String[] listItemStreamDataNames = new String[]{"accessTokens", "denyTokens", "guids", "fields", "displayURLs"};

  protected class ListItemStream implements IFileStream
  {
    protected final IProcessActivity activities;
    protected final Specification spec;
    protected final String rootPath;
    protected final String sitePath;
    protected final String siteListPath;

    // For carry-down
    protected final String documentIdentifier;
    protected final String[][] dataValues;

    public ListItemStream(IProcessActivity activities, String rootPath, String sitePath, String siteListPath, Specification spec,
      String documentIdentifier, String[] accessTokens, String denyTokens[], String listID, String[] fields)
    {
      this.activities = activities;
      this.spec = spec;
      this.rootPath = rootPath;
      this.sitePath = sitePath;
      this.siteListPath = siteListPath;
      this.documentIdentifier = documentIdentifier;
      this.dataValues = new String[listItemStreamDataNames.length][];
      this.dataValues[0] = accessTokens;
      this.dataValues[1] = denyTokens;
      this.dataValues[2] = new String[]{listID};
      this.dataValues[3] = fields;
    }
    
    @Override
    public void addFile(String relPath, String displayURL)
      throws ManifoldCFException
    {
      // First, convert the relative path to a full path
      if ( !relPath.startsWith("/") )
      {
        relPath = rootPath + sitePath + "/" + relPath;
      }

      String fullPath = relPath;

      // Now, strip away what we don't want - namely, the root path.  This makes the path relative to the root.
      if ( relPath.startsWith(rootPath) )
      {
        relPath = relPath.substring(rootPath.length());

        if (relPath.startsWith(sitePath))
        {
          relPath = relPath.substring(sitePath.length());
          
          // Now, strip "Lists" from relPath.  If it doesn't start with /Lists/, ignore it.
          if (relPath.startsWith("/Lists/"))
          {
            relPath = sitePath + relPath.substring("/Lists".length());
            if ( checkIncludeListItem( relPath, spec ) )
            {
              if (relPath.startsWith(siteListPath))
              {
                // Since the processing for a item needs to know the list path, we need a way to signal the cutoff between list and item levels.
                // The way I've chosen to do this is to use a triple slash at that point, as a separator.
                String modifiedPath = relPath.substring(0,siteListPath.length()) + "//" + relPath.substring(siteListPath.length());
                
                if (displayURL != null)
                  dataValues[4] = new String[]{displayURL};
                else
                  dataValues[4] = new String[]{fullPath};

                activities.addDocumentReference( modifiedPath, documentIdentifier, null, listItemStreamDataNames, dataValues );
              }
              else
              {
                Logging.connectors.warn("SharePoint: Unexpected relPath structure; site path is '"+relPath+"', but expected to see something beginning with '"+siteListPath+"'");
              }
            }
          }
          else
          {
            Logging.connectors.warn("SharePoint: Unexpected relPath structure; rel path is '"+relPath+"', but expected to see something beginning with '/Lists/'");
          }
        }
        else
        {
          Logging.connectors.warn("SharePoint: Unexpected relPath structure; site path is '"+relPath+"', but expected to see something beginning with '"+sitePath+"'");
        }
      }
      else
      {
        Logging.connectors.warn("SharePoint: Unexpected relPath structure; path is '"+relPath+"', but expected to see something beginning with '"+rootPath+"'");
      }
    }

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
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Server"));
    tabsArray.add(Messages.getString(locale,"SharePointRepository.AuthorityType"));
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration.js",null);
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
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    velocityContext.put("TabName",tabName);
    fillInServerTab(velocityContext,out,parameters);
    fillInAuthorityTypeTab(velocityContext,out,parameters);
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration_Server.html",velocityContext);
    Messages.outputResourceWithVelocity(out,locale,"editConfiguration_AuthorityType.html",velocityContext);
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
    String serverVersion = variableContext.getParameter("serverVersion");
    if (serverVersion != null)
      parameters.setParameter(SharePointConfig.PARAM_SERVERVERSION,serverVersion);

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
    
    String authorityType = variableContext.getParameter("authorityType");
    if (authorityType != null)
      parameters.setParameter(SharePointConfig.PARAM_AUTHORITYTYPE,authorityType);

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
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    fillInServerTab(velocityContext,out,parameters);
    fillInAuthorityTypeTab(velocityContext,out,parameters);
    Messages.outputResourceWithVelocity(out,locale,"viewConfiguration.html",velocityContext);
  }

  protected static void fillInAuthorityTypeTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException
  {
    // Default to Active Directory, for backwards compatibility
    String authorityType = parameters.getParameter(SharePointConfig.PARAM_AUTHORITYTYPE);
    if (authorityType == null)
      authorityType = "ActiveDirectory";
    velocityContext.put("AUTHORITYTYPE", authorityType);
  }
  
  protected static void fillInServerTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException
  {
    String serverVersion = parameters.getParameter(SharePointConfig.PARAM_SERVERVERSION);
    if (serverVersion == null)
      serverVersion = "4.0";

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
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Paths"));
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Security"));
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Metadata"));
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    velocityContext.put("SeqNum", Integer.toString(connectionSequenceNumber));

    Messages.outputResourceWithVelocity(out,locale,"editSpecification.js",velocityContext);
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
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    velocityContext.put("TabName",tabName);
    velocityContext.put("SeqNum", Integer.toString(connectionSequenceNumber));
    velocityContext.put("SelectedNum", Integer.toString(actualSequenceNumber));

    fillInSecurityTab(velocityContext,out,ds);
    fillInPathsTab(velocityContext,out,ds);
    fillInMetadataTab(velocityContext,out,ds);
    
    // Now, do the part of the tabs that requires context logic
    if (tabName.equals(Messages.getString(locale,"SharePointRepository.Paths")))
      fillInTransientPathsInfo(velocityContext,connectionSequenceNumber);
    else if (tabName.equals(Messages.getString(locale,"SharePointRepository.Metadata")))
      fillInTransientMetadataInfo(velocityContext,connectionSequenceNumber);
    
    Messages.outputResourceWithVelocity(out,locale,"editSpecification_Security.html",velocityContext);
    Messages.outputResourceWithVelocity(out,locale,"editSpecification_Paths.html",velocityContext);
    Messages.outputResourceWithVelocity(out,locale,"editSpecification_Metadata.html",velocityContext);
  }
  
  /** Fill in metadata tab */
  protected static void fillInMetadataTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    // Find the path-value metadata attribute name
    String pathNameAttribute = "";
    MatchMap matchMap = new MatchMap();
    List<Map<String,Object>> metadataRules = new ArrayList<Map<String,Object>>();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
      else if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
      else if (sn.getType().equals("startpoint"))
      {
        String site = sn.getAttributeValue("site");
        String lib = sn.getAttributeValue("lib");
        String path = site + "/" + lib + "/*";
        String allmetadata = sn.getAttributeValue("allmetadata");
        StringBuilder metadataFieldList = new StringBuilder();
        List<String> metadataFieldArray = new ArrayList<String>();
        if (allmetadata == null || !allmetadata.equals("true"))
        {
          for (int j = 0; j < sn.getChildCount(); j++)
          {
            SpecificationNode node = sn.getChild(j);
            if (node.getType().equals("metafield"))
            {
              if (metadataFieldList.length() > 0)
                metadataFieldList.append(", ");
              String val = node.getAttributeValue("value");
              metadataFieldList.append(val);
              metadataFieldArray.add(val);
            }
          }
          allmetadata = "false";
        }
          
        if (allmetadata.equals("true") || metadataFieldList.length() > 0)
        {
          Map<String,Object> item = new HashMap<String,Object>();
          item.put("THEPATH",path);
          item.put("THEACTION","include");
          item.put("ALLFLAG",allmetadata);
          item.put("FIELDLIST",metadataFieldArray);
          item.put("FIELDS",metadataFieldList.toString());
          metadataRules.add(item);
        }
      }
      else if (sn.getType().equals("metadatarule"))
      {
        String path = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String allmetadata = sn.getAttributeValue("allmetadata");
        StringBuilder metadataFieldList = new StringBuilder();
        List<String> metadataFieldArray = new ArrayList<String>();
        if (action.equals("include"))
        {
          if (allmetadata == null || !allmetadata.equals("true"))
          {
            for (int j = 0; j < sn.getChildCount(); j++)
            {
              SpecificationNode node = sn.getChild(j);
              if (node.getType().equals("metafield"))
              {
                String val = node.getAttributeValue("value");
                if (metadataFieldList.length() > 0)
                  metadataFieldList.append(", ");
                metadataFieldList.append(val);
                metadataFieldArray.add(val);
              }
            }
            allmetadata="false";
          }
        }
        else
          allmetadata = "";
        
        Map<String,Object> item = new HashMap<String,Object>();
        item.put("THEPATH",path);
        item.put("THEACTION",action);
        item.put("ALLFLAG",allmetadata);
        item.put("FIELDLIST",metadataFieldArray);
        item.put("FIELDS",metadataFieldList.toString());
        metadataRules.add(item);
      }
    }
    
    List<Map<String,String>> mapList = new ArrayList<Map<String,String>>();
    for (int i = 0; i < matchMap.getMatchCount(); i++)
    {
      String matchString = matchMap.getMatchString(i);
      String replaceString = matchMap.getReplaceString(i);

      Map<String,String> item = new HashMap<String,String>();
      item.put("MATCH",matchString);
      item.put("REPLACE",replaceString);
      mapList.add(item);
    }
    
    velocityContext.put("PATHNAMEATTRIBUTE",pathNameAttribute);
    velocityContext.put("MAPLIST",mapList);
    velocityContext.put("METADATARULES",metadataRules);
  }
  
  /** Fill in transient metadata info */
  protected void fillInTransientMetadataInfo(Map<String,Object> velocityContext, int connectionSequenceNumber)
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // The following variables may be in the thread context because postspec.jsp put them there:
    // (1) "metapath", which contains the rule path as it currently stands;
    // (2) "metapathstate", which describes what the current path represents.  Values are "unknown", "site", "library".
    // (3) "metapathlibrary" is the library or list path (if this is known yet).
    // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
    String metaPathSoFar = (String)currentContext.get(seqPrefix+"metapath");
    String metaPathState = (String)currentContext.get(seqPrefix+"metapathstate");
    String metaPathLibrary = (String)currentContext.get(seqPrefix+"metapathlibrary");
    if (metaPathState == null)
      metaPathState = "unknown";
    if (metaPathSoFar == null)
    {
      metaPathSoFar = "/";
      metaPathState = "site";
    }

    String message = null;
    List<NameValue> fieldList = null;
    if (metaPathLibrary != null)
    {
      // Look up metadata fields
      int index = metaPathLibrary.lastIndexOf("/");
      String site = metaPathLibrary.substring(0,index);
      String libOrList = metaPathLibrary.substring(index+1);
      Map<String,String> metaFieldList = null;
      try
      {
        if (metaPathState.equals("library") || metaPathState.equals("file"))
          metaFieldList = getLibFieldList(site,libOrList);
        else if (metaPathState.equals("list"))
          metaFieldList = getListFieldList(site,libOrList);
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        message = e.getMessage();
      }
      catch (ServiceInterruption e)
      {
        message = "SharePoint unavailable: "+e.getMessage();
      }
      if (metaFieldList != null)
      {
        String[] fields = new String[metaFieldList.size()];
        int j = 0;
        Iterator<String> iter = metaFieldList.keySet().iterator();
        while (iter.hasNext())
        {
          fields[j++] = iter.next();
        }
        java.util.Arrays.sort(fields);
        fieldList = new ArrayList<NameValue>();
        for (String field : fields)
        {
          fieldList.add(new NameValue(field,metaFieldList.get(field)));
        }
      }
    }
      
    // Grab next site list and lib list
    List<NameValue> childSiteList = null;
    List<NameValue> childLibList = null;
    List<NameValue> childListList = null;

    if (message == null && metaPathState.equals("site"))
    {
      try
      {
        String queryPath = metaPathSoFar;
        if (queryPath.equals("/"))
          queryPath = "";
        childSiteList = getSites(queryPath);
        if (childSiteList == null)
        {
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          // Illegal path - state becomes "unknown".
          metaPathState = "unknown";
          metaPathLibrary = null;
        }
        childLibList = getDocLibsBySite(queryPath);
        if (childLibList == null)
        {
          // Illegal path - state becomes "unknown"
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          metaPathState = "unknown";
          metaPathLibrary = null;
        }
        childListList = getListsBySite(queryPath);
        if (childListList == null)
        {
          // Illegal path - state becomes "unknown"
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          metaPathState = "unknown";
          metaPathLibrary = null;
        }
      }
      catch (ManifoldCFException e)
      {
        Logging.connectors.warn(e.getMessage(),e);
        message = e.getMessage();
      }
      catch (ServiceInterruption e)
      {
        message = "SharePoint unavailable: "+e.getMessage();
      }
    }
    
    if (metaPathSoFar != null)
      velocityContext.put("METAPATHSOFAR",metaPathSoFar);
    if (metaPathState != null)
      velocityContext.put("METAPATHSTATE",metaPathState);
    if (metaPathLibrary != null)
      velocityContext.put("METAPATHLIBRARY",metaPathLibrary);
    if (message != null)
      velocityContext.put("METAMESSAGE",message);
    if (fieldList != null)
      velocityContext.put("METAFIELDLIST",fieldList);
    if (childSiteList != null)
      velocityContext.put("METACHILDSITELIST",childSiteList);
    if (childLibList != null)
      velocityContext.put("METACHILDLIBLIST",childLibList);
    if (childListList != null)
      velocityContext.put("METACHILDLISTLIST",childListList);
  }
  
  /** Fill in paths tab */
  protected static void fillInPathsTab(Map<String,Object> velocityContext, IHTTPOutput out, Specification ds)
  {
    List<Map<String,String>> rules = new ArrayList<Map<String,String>>();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("startpoint"))
      {
        String site = sn.getAttributeValue("site");
        String lib = sn.getAttributeValue("lib");
        String siteLib = site + "/" + lib + "/";

        // Go through all the file/folder rules for the startpoint, and generate new "rules" corresponding to each.
        for (int j = 0; j < sn.getChildCount(); j++)
        {
          SpecificationNode node = sn.getChild(j);
          if (node.getType().equals("include") || node.getType().equals("exclude"))
          {
            String matchPart = node.getAttributeValue("match");
            String ruleType = node.getAttributeValue("type");
            String theFlavor = node.getType();
            String thePath = siteLib + matchPart;
            
            Map<String,String> item = new HashMap<String,String>();
            item.put("THEPATH",thePath);
            item.put("THETYPE","file");
            item.put("THEACTION",theFlavor);
            rules.add(item);
            
            if (ruleType.equals("file") && !matchPart.startsWith("*"))
            {
              thePath = siteLib + "*/" + matchPart;
              item = new HashMap<String,String>();
              item.put("THEPATH",thePath);
              item.put("THETYPE","file");
              item.put("THEACTION",theFlavor);
              rules.add(item);
            }
          }
        }
      }
      else if (sn.getType().equals("pathrule"))
      {
        String match = sn.getAttributeValue("match");
        String type = sn.getAttributeValue("type");
        String action = sn.getAttributeValue("action");
        
        Map<String,String> item = new HashMap<String,String>();
        item.put("THEPATH",match);
        item.put("THETYPE",type);
        item.put("THEACTION",action);
        rules.add(item);
        
      }
    }
    
    velocityContext.put("RULES",rules);
  }
  
  /** Fill in the transient portion of the Paths tab */
  protected void fillInTransientPathsInfo(Map<String,Object> velocityContext, int connectionSequenceNumber)
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // The following variables may be in the thread context because postspec.jsp put them there:
    // (1) "specpath", which contains the rule path as it currently stands;
    // (2) "specpathstate", which describes what the current path represents.  Values are "unknown", "site", "library", "list".
    // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
    // specsitepath may be in the thread context, put there by postspec.jsp 
    String pathSoFar = (String)currentContext.get(seqPrefix+"specpath");
    String pathState = (String)currentContext.get(seqPrefix+"specpathstate");
    String pathLibrary = (String)currentContext.get(seqPrefix+"specpathlibrary");
    if (pathState == null)
    {
      pathState = "unknown";
      pathLibrary = null;
    }
    if (pathSoFar == null)
    {
      pathSoFar = "/";
      pathState = "site";
      pathLibrary = null;
    }

    // Grab next site list and lib list
    List<NameValue> childSiteList = null;
    List<NameValue> childLibList = null;
    List<NameValue> childListList = null;
    String message = null;
    if (pathState.equals("site"))
    {
      try
      {
        String queryPath = pathSoFar;
        if (queryPath.equals("/"))
          queryPath = "";
        childSiteList = getSites(queryPath);
        if (childSiteList == null)
        {
          // Illegal path - state becomes "unknown".
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          pathState = "unknown";
          pathLibrary = null;
        }
        childLibList = getDocLibsBySite(queryPath);
        if (childLibList == null)
        {
          // Illegal path - state becomes "unknown"
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          pathState = "unknown";
          pathLibrary = null;
        }
        childListList = getListsBySite(queryPath);
        if (childListList == null)
        {
          // Illegal path - state becomes "unknown"
          if (queryPath.length() == 0)
            throw new ManifoldCFException("Root site is unreachable, or user has no permissions");
          pathState = "unknown";
          pathLibrary = null;
        }
      }
      catch (ManifoldCFException e)
      {
        Logging.connectors.warn(e.getMessage(),e);
        message = e.getMessage();
      }
      catch (ServiceInterruption e)
      {
        message = "SharePoint unavailable: "+e.getMessage();
      }
    }
      
    if (pathSoFar != null)
      velocityContext.put("PATHSOFAR",pathSoFar);
    if (pathState != null)
      velocityContext.put("PATHSTATE",pathState);
    if (pathLibrary != null)
      velocityContext.put("PATHLIBRARY",pathLibrary);
    if (message != null)
      velocityContext.put("MESSAGE",message);
    if (childSiteList != null)
      velocityContext.put("CHILDSITELIST",childSiteList);
    if (childLibList != null)
      velocityContext.put("CHILDLIBLIST",childLibList);
    if (childListList != null)
      velocityContext.put("CHILDLISTLIST",childListList);
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

    // Remove old-style rules, but only if the information would not be lost
    if (variableContext.getParameter(seqPrefix+"specpathcount") != null && variableContext.getParameter(seqPrefix+"metapathcount") != null)
    {
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("startpoint"))
          ds.removeChild(i);
        else
          i++;
      }
    }
    
    String x = variableContext.getParameter(seqPrefix+"specpathcount");
    if (x != null)
    {
      // Delete all path rule entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathrule"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(x);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"specop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        
        // Get the stored information for this rule.
        String path = variableContext.getParameter(seqPrefix+"specpath"+pathDescription);
        String type = variableContext.getParameter(seqPrefix+"spectype"+pathDescription);
        String action = variableContext.getParameter(seqPrefix+"specflav"+pathDescription);
        
        SpecificationNode node = new SpecificationNode("pathrule");
        node.setAttribute("match",path);
        node.setAttribute("action",action);
        node.setAttribute("type",type);
        
        // If there was an insert operation, do it now
        if (x != null && x.equals("Insert Here"))
        {
          // The global parameters are what are used to create the rule
          path = variableContext.getParameter(seqPrefix+"specpath");
          type = variableContext.getParameter(seqPrefix+"spectype");
          action = variableContext.getParameter(seqPrefix+"specflavor");
          
          SpecificationNode sn = new SpecificationNode("pathrule");
          sn.setAttribute("match",path);
          sn.setAttribute("action",action);
          sn.setAttribute("type",type);
          ds.addChild(ds.getChildCount(),sn);
        }
        
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global path rule operation
      String op = variableContext.getParameter(seqPrefix+"specop");
      if (op != null)
      {
        if (op.equals("Add"))
        {
          String match = variableContext.getParameter(seqPrefix+"specpath");
          String action = variableContext.getParameter(seqPrefix+"specflavor");
          String type = variableContext.getParameter(seqPrefix+"spectype");
          SpecificationNode node = new SpecificationNode("pathrule");
          node.setAttribute("match",match);
          node.setAttribute("action",action);
          node.setAttribute("type",type);
          ds.addChild(ds.getChildCount(),node);
        }
      }

      // See if there's a global pathbuilder operation
      String pathop = variableContext.getParameter(seqPrefix+"specpathop");
      if (pathop != null)
      {
        if (pathop.equals("Reset"))
        {
          currentContext.save(seqPrefix+"specpath","/");
          currentContext.save(seqPrefix+"specpathstate","site");
          currentContext.save(seqPrefix+"specpathlibrary",null);
        }
        else if (pathop.equals("AppendSite"))
        {
          String path = variableContext.getParameter(seqPrefix+"specpath");
          String addon = variableContext.getParameter(seqPrefix+"specsite");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
          }
          currentContext.save(seqPrefix+"specpath",path);
          currentContext.save(seqPrefix+"specpathstate","site");
          currentContext.save(seqPrefix+"specpathlibrary",null);
        }
        else if (pathop.equals("AppendLibrary"))
        {
          String path = variableContext.getParameter(seqPrefix+"specpath");
          String addon = variableContext.getParameter(seqPrefix+"speclibrary");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save(seqPrefix+"specpathstate","library");
            currentContext.save(seqPrefix+"specpathlibrary",path);
          }
          currentContext.save(seqPrefix+"specpath",path);
        }
        else if (pathop.equals("AppendList"))
        {
          String path = variableContext.getParameter(seqPrefix+"specpath");
          String addon = variableContext.getParameter(seqPrefix+"speclist");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save(seqPrefix+"specpathstate","list");
            currentContext.save(seqPrefix+"specpathlibrary",path);
          }
          currentContext.save(seqPrefix+"specpath",path);
        }
        else if (pathop.equals("AppendText"))
        {
          String path = variableContext.getParameter(seqPrefix+"specpath");
          String library = variableContext.getParameter(seqPrefix+"specpathlibrary");
          String addon = variableContext.getParameter(seqPrefix+"specmatch");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save(seqPrefix+"specpathstate","unknown");
          }
          currentContext.save(seqPrefix+"specpath",path);
          currentContext.save(seqPrefix+"specpathlibrary",library);
        }
        else if (pathop.equals("Remove"))
        {
          // Strip off end
          String path = variableContext.getParameter(seqPrefix+"specpath");
          int index = path.lastIndexOf("/");
          path = path.substring(0,index);
          if (path.length() == 0)
            path = "/";
          currentContext.save(seqPrefix+"specpath",path);
          // Now, adjust state.
          String pathState = variableContext.getParameter(seqPrefix+"specpathstate");
          if (pathState.equals("library") || pathState.equals("list"))
            pathState = "site";
          currentContext.save(seqPrefix+"specpathstate",pathState);
        }
      }

    }
    
    x = variableContext.getParameter(seqPrefix+"metapathcount");
    if (x != null)
    {
      // Delete all metadata rule entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("metadatarule"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(x);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"metaop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }

        // Get the stored information for this rule.
        String path = variableContext.getParameter(seqPrefix+"metapath"+pathDescription);
        String action = variableContext.getParameter(seqPrefix+"metaflav"+pathDescription);
        String allmetadata =  variableContext.getParameter(seqPrefix+"metaall"+pathDescription);
        String[] metadataFields = variableContext.getParameterValues(seqPrefix+"metafields"+pathDescription);
        
        SpecificationNode node = new SpecificationNode("metadatarule");
        node.setAttribute("match",path);
        node.setAttribute("action",action);
        if (action.equals("include"))
        {
          if (allmetadata != null)
            node.setAttribute("allmetadata",allmetadata);
          if (metadataFields != null)
          {
            int j = 0;
            while (j < metadataFields.length)
            {
              SpecificationNode sn = new SpecificationNode("metafield");
              sn.setAttribute("value",metadataFields[j]);
              node.addChild(j++,sn);
            }
          }
        }
        
        if (x != null && x.equals("Insert Here"))
        {
          // Insert the new global rule information now
          path = variableContext.getParameter(seqPrefix+"metapath");
          action = variableContext.getParameter(seqPrefix+"metaflavor");
          allmetadata =  variableContext.getParameter(seqPrefix+"metaall");
          metadataFields = variableContext.getParameterValues(seqPrefix+"metafields");
        
          SpecificationNode sn = new SpecificationNode("metadatarule");
          sn.setAttribute("match",path);
          sn.setAttribute("action",action);
          if (action.equals("include"))
          {
            if (allmetadata != null)
              node.setAttribute("allmetadata",allmetadata);
            if (metadataFields != null)
            {
              int j = 0;
              while (j < metadataFields.length)
              {
                SpecificationNode node2 = new SpecificationNode("metafield");
                node2.setAttribute("value",metadataFields[j]);
                sn.addChild(j++,node2);
              }
            }
          }

          ds.addChild(ds.getChildCount(),sn);
        }
        
        ds.addChild(ds.getChildCount(),node);
        i++;
      }
      
      // See if there's a global path rule operation
      String op = variableContext.getParameter(seqPrefix+"metaop");
      if (op != null)
      {
        if (op.equals("Add"))
        {
          String match = variableContext.getParameter(seqPrefix+"metapath");
          String action = variableContext.getParameter(seqPrefix+"metaflavor");
          SpecificationNode node = new SpecificationNode("metadatarule");
          node.setAttribute("match",match);
          node.setAttribute("action",action);
          if (action.equals("include"))
          {
            String allmetadata = variableContext.getParameter(seqPrefix+"metaall");
            String[] metadataFields = variableContext.getParameterValues(seqPrefix+"metafields");
            if (allmetadata != null)
              node.setAttribute("allmetadata",allmetadata);
            if (metadataFields != null)
            {
              int j = 0;
              while (j < metadataFields.length)
              {
                SpecificationNode sn = new SpecificationNode("metafield");
                sn.setAttribute("value",metadataFields[j]);
                node.addChild(j++,sn);
              }
            }

          }
          ds.addChild(ds.getChildCount(),node);
        }
      }

      // See if there's a global pathbuilder operation
      String pathop = variableContext.getParameter(seqPrefix+"metapathop");
      if (pathop != null)
      {
        if (pathop.equals("Reset"))
        {
          currentContext.save(seqPrefix+"metapath","/");
          currentContext.save(seqPrefix+"metapathstate","site");
          currentContext.save(seqPrefix+"metapathlibrary",null);
        }
        else if (pathop.equals("AppendSite"))
        {
          String path = variableContext.getParameter(seqPrefix+"metapath");
          String addon = variableContext.getParameter(seqPrefix+"metasite");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
          }
          currentContext.save(seqPrefix+"metapath",path);
          currentContext.save(seqPrefix+"metapathstate","site");
          currentContext.save(seqPrefix+"metapathlibrary",null);
        }
        else if (pathop.equals("AppendLibrary"))
        {
          String path = variableContext.getParameter(seqPrefix+"metapath");
          String addon = variableContext.getParameter(seqPrefix+"metalibrary");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save(seqPrefix+"metapathstate","library");
            currentContext.save(seqPrefix+"metapathlibrary",path);
          }
          currentContext.save(seqPrefix+"metapath",path);
        }
        else if (pathop.equals("AppendList"))
        {
          String path = variableContext.getParameter(seqPrefix+"metapath");
          String addon = variableContext.getParameter(seqPrefix+"metalist");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save(seqPrefix+"metapathstate","list");
            currentContext.save(seqPrefix+"metapathlibrary",path);
            // Automatically add on wildcard for list item part of the match
            path += "/*";
          }
          currentContext.save(seqPrefix+"metapath",path);
        }
        else if (pathop.equals("AppendText"))
        {
          String path = variableContext.getParameter(seqPrefix+"metapath");
          String library = variableContext.getParameter(seqPrefix+"metapathlibrary");
          String addon = variableContext.getParameter(seqPrefix+"metamatch");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            if (library != null)
              currentContext.save(seqPrefix+"metapathstate","file");
            else
              currentContext.save(seqPrefix+"metapathstate","unknown");
          }
          currentContext.save(seqPrefix+"metapath",path);
          currentContext.save(seqPrefix+"metapathlibrary",library);
        }
        else if (pathop.equals("Remove"))
        {
          String pathState = variableContext.getParameter(seqPrefix+"metapathstate");
          String path;
          if (pathState.equals("file"))
          {
            pathState = "library";
            path = variableContext.getParameter(seqPrefix+"metapathlibrary");
          }
          else if (pathState.equals("list") || pathState.equals("library"))
          {
            pathState = "site";
            path = variableContext.getParameter(seqPrefix+"metapathlibrary");
            int index = path.lastIndexOf("/");
            path = path.substring(0,index);
            if (path.length() == 0)
              path = "/";
            currentContext.save(seqPrefix+"metapathlibrary",null);
          }
          else
          {
            path = variableContext.getParameter(seqPrefix+"metapath");
            int index = path.lastIndexOf("/");
            path = path.substring(0,index);
            if (path.length() == 0)
              path = "/";
          }

          currentContext.save(seqPrefix+"metapathstate",pathState);
          currentContext.save(seqPrefix+"metapath",path);
        }
      }

      
    }

    String xc = variableContext.getParameter(seqPrefix+"specsecurity");
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

    xc = variableContext.getParameter(seqPrefix+"specpathnameattribute");
    if (xc != null)
    {
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
    Map<String,Object> velocityContext = new HashMap<String,Object>();
    velocityContext.put("SeqNum", Integer.toString(connectionSequenceNumber));

    fillInSecurityTab(velocityContext,out,ds);
    fillInPathsTab(velocityContext,out,ds);
    fillInMetadataTab(velocityContext,out,ds);
    
    Messages.outputResourceWithVelocity(out,locale,"viewSpecification.html",velocityContext);
  }

  protected static class ExecuteMethodThread extends Thread
  {
    protected final HttpClient httpClient;
    protected final String url;
    protected final OutputStream os;

    protected Throwable exception = null;
    protected int returnCode = 0;

    public ExecuteMethodThread( HttpClient httpClient, String url, OutputStream os )
    {
      super();
      setDaemon(true);
      this.httpClient = httpClient;
      this.url = url;
      this.os = os;
    }

    public void run()
    {
      try
      {
        HttpGet method = new HttpGet( url );
        // Try block to insure that the connection gets cleaned up
        try
        {
          // Begin the fetch
          HttpResponse response = httpClient.execute(method);
          returnCode = response.getStatusLine().getStatusCode();
          
          if (returnCode == 200)
          {
            // Process the data
            HttpEntity entity = response.getEntity();
            if (entity != null)
            {
              InputStream is = entity.getContent();
              // Figure out what to do with the data. 
              byte[] transferBuffer = new byte[65536];
              while (true)
              {
                int amt = is.read(transferBuffer);
                if (amt == -1)
                  break;
                os.write(transferBuffer,0,amt);
              }
            }
          }
        }
        finally
        {
          // Consumes and closes the stream, releasing the connection
          method.abort();
        }

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public int finishUp()
      throws InterruptedException, IOException, org.apache.http.HttpException
    {
      join();
      if (exception != null)
      {
        if (exception instanceof IOException)
          throw (IOException)exception;
        else if (exception instanceof Error)
          throw (Error)exception;
        else if (exception instanceof org.apache.http.HttpException)
          throw (org.apache.http.HttpException)exception;
        else if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        else
          throw new RuntimeException("Unexpected exception type thrown: "+exception.getClass().getName());
      }
      return returnCode;
    }
  }



  /**
  * Gets a list of field names of the given document library or list.
  * @param parentSite - parent site path
  * @param docLibrary name
  * @return list of the fields
  */
  public Map<String,String> getLibFieldList( String parentSite, String docLibrary )
    throws ServiceInterruption, ManifoldCFException
  {
    getSession();
    return proxy.getFieldList( encodePath(parentSite), proxy.getDocLibID( encodePath(parentSite), parentSite, docLibrary ) );
  }

  /**
  * Gets a list of field names of the given document library or list.
  * @param parentSite - parent site path
  * @param docLibrary name
  * @return list of the fields
  */
  public Map<String,String> getListFieldList( String parentSite, String listName )
    throws ServiceInterruption, ManifoldCFException
  {
    getSession();
    return proxy.getFieldList( encodePath(parentSite), proxy.getListID( encodePath(parentSite), parentSite, listName ) );
  }

  /**
  * Gets a list of sites/subsites of the given parent site
  * @param parentSite the unencoded parent site path to search for subsites, empty for root.
  * @return list of the sites
  */
  public List<NameValue> getSites( String parentSite )
    throws ServiceInterruption, ManifoldCFException
  {
    getSession();
    return proxy.getSites( encodePath(parentSite) );
  }

  /**
  * Gets a list of document libraries of the given parent site
  * @param parentSite the unencoded parent site to search for libraries, empty for root.
  * @return list of the libraries
  */
  public List<NameValue> getDocLibsBySite( String parentSite )
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return proxy.getDocumentLibraries( encodePath(parentSite), parentSite );
  }

  /**
  * Gets a list of lists of the given parent site
  * @param parentSite the unencoded parent site to search for lists, empty for root.
  * @return list of the lists
  */
  public List<NameValue> getListsBySite( String parentSite )
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return proxy.getLists( encodePath(parentSite), parentSite );
  }

  // Protected static methods

  /** Check if a library should be included, given a document specification.
  *@param libraryPath is the unencoded canonical library name (including site path from root site), without any starting slash.
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected boolean checkIncludeLibrary( String libraryPath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include library '" + libraryPath + "'" );

    // Scan the specification, looking for the old-style "startpoint" matches and the new-style "libraryrule" matches.
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if ( sn.getType().equals("startpoint") )
      {
        // Old style rule!

        String site = sn.getAttributeValue( "site" );
        String lib = sn.getAttributeValue( "lib" );
        // Both site and lib are unencoded.  See if they match the library path
        String pathStart = site + "/" + lib;

        // Old-style matches have a preceding "/" when there's no subsite...
        if (libraryPath.equals(pathStart))
        {
          // Hey, the startpoint rule matches!  It's an implicit inclusion, so we don't need to do anything else except return.
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Library path '"+libraryPath+"' matched old-style startpoint with site '"+site+"' and library '"+lib+"' - including");
          return true;
        }
      }
      else if (sn.getType().equals("pathrule"))
      {
        // New-style rule.
        // Here's the trick: We do what the first matching rule tells us to do.
        String pathMatch = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String ruleType = sn.getAttributeValue("type");

        // First, find out if we match EXACTLY.
        if (checkMatch(libraryPath,0,pathMatch))
        {
          // If this is true, the type also has to match if the rule is to apply.
          if (ruleType.equals("library"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' exactly matched rule path '"+pathMatch+"'");
            if (action.equals("include"))
            {
              // For include rules, partial match is good enough to proceed.
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Including library '"+libraryPath+"'");
              return true;
            }
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Excluding library '"+libraryPath+"'");
            return false;
          }
        }
        else if (ruleType.equals("file") && checkPartialPathMatch(libraryPath,0,pathMatch,1) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' partially matched file rule path '"+pathMatch+"' - including");
          return true;
        }
        else if (ruleType.equals("folder") && checkPartialPathMatch(libraryPath,0,pathMatch,1) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' partially matched folder rule path '"+pathMatch+"' - including");
          return true;
        }
      }
    }
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("SharePoint: Not including library '"+libraryPath+"' because no matching rule");
    return false;
  }

  /** Check if a list should be included, given a document specification.
  *@param listPath is the unencoded canonical list name (including site path from root site), without any starting slash.
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected boolean checkIncludeList( String listPath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include list '" + listPath + "'" );

    // Scan the specification, looking for new-style "pathrule" matches.
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if (sn.getType().equals("pathrule"))
      {
        // New-style rule.
        // Here's the trick: We do what the first matching rule tells us to do.
        String pathMatch = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String ruleType = sn.getAttributeValue("type");

        // First, find out if we match EXACTLY.
        if (checkMatch(listPath,0,pathMatch))
        {
          // If this is true, the type also has to match if the rule is to apply.
          if (ruleType.equals("list"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: List '"+listPath+"' exactly matched rule path '"+pathMatch+"'");
            if (action.equals("include"))
            {
              // For include rules, partial match is good enough to proceed.
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Including list '"+listPath+"'");
              return true;
            }
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Excluding list '"+listPath+"'");
            return false;
          }
        }
      }
    }
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("SharePoint: Not including list '"+listPath+"' because no matching rule");
    return false;
  }

  /** Check if a site should be included, given a document specification.
  *@param sitePath is the unencoded canonical site path name from the root site level, without any starting slash.
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected boolean checkIncludeSite( String sitePath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include site '" + sitePath + "'" );

    // Scan the specification, looking for the old-style "startpoint" matches and the new-style "libraryrule" matches.
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if ( sn.getType().equals("startpoint") )
      {
        // Old style rule!

        String site = sn.getAttributeValue( "site" );
        // Both site and lib are unencoded.  See if they match part of the site path.
        // Note well: We want a complete subsection match!  That is, what's left in the path after the match must
        // either start with "/" or be empty.
        if (!site.startsWith("/"))
          site = "/" + site;

        // Old-style matches have a preceding "/" when there's no subsite...
        if (site.startsWith(sitePath))
        {
          if (sitePath.length() == 1 || site.length() == sitePath.length() || site.charAt(sitePath.length()) == '/')
          {
            // Hey, the startpoint rule matches!  It's an implicit inclusion, so we don't need to do anything else except return.
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Site path '"+sitePath+"' matched old-style startpoint with site '"+site+"' - including");
            return true;
          }
        }
      }
      else if (sn.getType().equals("pathrule"))
      {
        // New-style rule.
        String pathMatch = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String ruleType = sn.getAttributeValue("type");

        // First, find out if we match EXACTLY.
        if (checkMatch(sitePath,0,pathMatch))
        {
          // If this is true, the type also has to match if the rule is to apply.
          if (ruleType.equals("site"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Site '"+sitePath+"' exactly matched rule path '"+pathMatch+"'");
            if (action.equals("include"))
            {
              // For include rules, partial match is good enough to proceed.
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Including site '"+sitePath+"'");
              return true;
            }
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Excluding site '"+sitePath+"'");
            return false;
          }
        }
        else if (ruleType.equals("library") && checkPartialPathMatch(sitePath,0,pathMatch,1) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched library rule path '"+pathMatch+"' - including");
          return true;
        }
        else if (ruleType.equals("list") && checkPartialPathMatch(sitePath,0,pathMatch,1) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched list rule path '"+pathMatch+"' - including");
          return true;
        }
        else if (ruleType.equals("site") && checkPartialPathMatch(sitePath,0,pathMatch,0) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched site rule path '"+pathMatch+"' - including");
          return true;
        }
        else if (ruleType.equals("file") && checkPartialPathMatch(sitePath,0,pathMatch,2) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched file rule path '"+pathMatch+"' - including");
          return true;
        }
        else if (ruleType.equals("folder") && checkPartialPathMatch(sitePath,0,pathMatch,2) && action.equals("include"))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched folder rule path '"+pathMatch+"' - including");
          return true;
        }

      }
    }
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("SharePoint: Not including site '"+sitePath+"' because no matching rule");
    return false;
  }

  /** Get a file or item's metadata specification, given a path and a document specification.
  *@param filePath is the unencoded path to a file or item, including sites and library/list, beneath the root site.
  *@param documentSpecification is the document specification.
  *@return the metadata description appropriate to the file.
  */
  protected MetadataInformation getMetadataSpecification( String filePath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Finding metadata to include for document/item '" + filePath + "'." );

    MetadataInformation rval = new MetadataInformation();

    // Scan the specification, looking for the old-style "startpoint" matches and the new-style "metadatarule" matches.
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if ( sn.getType().equals("startpoint") )
      {
        // Old style rule!

        String site = sn.getAttributeValue( "site" );
        String lib = sn.getAttributeValue( "lib" );
        // Both site and lib are unencoded.  See if they match the first part of the filepath
        String pathStart = site + "/" + lib + "/";
        // Old-style matches have a preceding "/" when there's no subsite...
        if (filePath.startsWith(pathStart))
        {
          // Hey, the startpoint rule matches!  It's an implicit inclusion, so this is where we get the metadata from (and then return)
          String allmetadata = sn.getAttributeValue("allmetadata");
          if (allmetadata != null && allmetadata.equals("true"))
            rval.setAllMetadata();
          else
          {
            // Scan children looking for metadata nodes
            int j = 0;
            while (j < sn.getChildCount())
            {
              SpecificationNode node = sn.getChild(j++);
              if (node.getType().equals("metafield"))
                rval.addMetadataField(node.getAttributeValue("value"));
            }
          }
          return rval;
        }
      }
      else if (sn.getType().equals("metadatarule"))
      {
        // New-style rule.
        // Here's the trick: We do what the first matching rule tells us to do.
        String pathMatch = sn.getAttributeValue("match");
        // First, find out if we match...
        if (checkMatch(filePath,0,pathMatch))
        {
          // The rule "fired".  Now, do what it tells us to.
          String action = sn.getAttributeValue("action");
          if (action.equals("include"))
          {
            // Include: Process the metadata specification, then return
            String allMetadata = sn.getAttributeValue("allmetadata");
            if (allMetadata != null && allMetadata.equals("true"))
              rval.setAllMetadata();
            else
            {
              // Scan children looking for metadata nodes
              int j = 0;
              while (j < sn.getChildCount())
              {
                SpecificationNode node = sn.getChild(j++);
                if (node.getType().equals("metafield"))
                  rval.addMetadataField(node.getAttributeValue("value"));
              }
            }
          }
          return rval;
        }
      }
    }

    return rval;

  }

  /** Check if a file should be included.
  *@param filePath is the path to the file, including sites and library, beneath the root site.
  *@param documentSpecification is the document specification.
  *@return true if file should be included.
  */
  protected boolean checkIncludeFile( String filePath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include document '" + filePath + "'" );

    // Break up the file/folder part of the path
    int lastSlash = filePath.lastIndexOf("/");
    String pathPart = filePath.substring(0,lastSlash);
    String filePart = filePath.substring(lastSlash+1);

    // Scan the spec rules looking for a library match, and extract the information if found.
    // We need to understand both the old-style rules (startpoints), and the new style (matchrules)
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if ( sn.getType().equals("startpoint") )
      {
        // Old style rule!

        String site = sn.getAttributeValue( "site" );
        String lib = sn.getAttributeValue( "lib" );
        // Both site and lib are unencoded.  The string we are matching starts with "/" if the site is empty.
        String pathMatch = site + "/" + lib + "/";
        if (filePath.startsWith(pathMatch))
        {
          // Hey, it matched!
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style startpoint with site '"+site+"' and library '"+lib+"'");

          int restOfPathIndex = pathMatch.length();

          // We need to walk through the subrules and see whether it's in or out.
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode node = sn.getChild(j++);
            String flavor = node.getType();
            if (flavor.equals("include") || flavor.equals("exclude"))
            {
              String match = node.getAttributeValue("match");
              String type = node.getAttributeValue("type");
              String sourceMatch;
              int sourceIndex;
              if ( type.equals("file") )
              {
                sourceMatch = filePart;
                sourceIndex = 0;
              }
              else
              {
                sourceMatch = pathPart;
                sourceIndex = restOfPathIndex;
              }
              if ( checkMatch(sourceMatch,sourceIndex,match) )
              {
                // Our file path matched the rule.
                if (flavor.equals("include"))
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style inclusion rule '"+match+"' - including");
                  return true;
                }
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style exclusion rule '"+match+"' - excluding");
                return false;
              }
            }
          }

          // Didn't match any of the file rules; therefore exclude.
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: File path '"+filePath+"' did not match any old-style inclusion/exclusion rules - excluding");
          return false;
        }
      }
      else if (sn.getType().equals("pathrule"))
      {
        // New style rule!
        String pathMatch = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String ruleType = sn.getAttributeValue("type");

        // Find out if we match EXACTLY.  There are no "partial matches" for files.
        if (checkMatch(filePath,0,pathMatch))
        {
          // If this is true, the type also has to match if the rule is to apply.
          if (ruleType.equals("file"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: File '"+filePath+"' exactly matched rule path '"+pathMatch+"'");
            if (action.equals("include"))
            {
              // For include rules, partial match is good enough to proceed.
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Including file '"+filePath+"'");
              return true;
            }
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Excluding file '"+filePath+"'");
            return false;
          }
        }
      }
    }

    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("SharePoint: File path '"+filePath+"' does not match any rules - excluding");

    return false;
  }

  /** Check if a list item attachment should be included.
  *@param attachmentPath is the path to the attachment, including sites and list name, beneath the root site.
  *@param documentSpecification is the document specification.
  *@return true if file should be included.
  */
  protected boolean checkIncludeListItemAttachment( String attachmentPath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include list item attachment '" + attachmentPath + "'" );

    // There are no attachment rules, so they are always included
    return true;
  }

  /** Check if a list item should be included.
  *@param itemPath is the path to the item, including sites and list name, beneath the root site.
  *@param documentSpecification is the document specification.
  *@return true if file should be included.
  */
  protected boolean checkIncludeListItem( String itemPath, Specification documentSpecification )
  {
    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug( "SharePoint: Checking whether to include list item '" + itemPath + "'" );

    // There are no item rules, so they are always included
    return true;
  }

  /** Match a sub-path.  The sub-path must match the complete starting part of the full path, in a path
  * sense.  The returned value should point into the file name beyond the end of the matched path, or
  * be -1 if there is no match.
  *@param subPath is the sub path.
  *@param fullPath is the full path.
  *@return the index of the start of the remaining part of the full path, or -1.
  */
  protected static int matchSubPath( String subPath, String fullPath )
  {
    if ( subPath.length() > fullPath.length() )
      return -1;
    if ( fullPath.startsWith( subPath ) == false )
      return -1;
    int rval = subPath.length();
    if ( fullPath.length() == rval )
      return rval;
    char x = fullPath.charAt( rval );
    if ( x == '/' )
      rval++;
    return rval;
  }

  /** Check for a partial path match between two strings with wildcards.
  * Match allowance also must be made for the minimum path components in the rest of the path.
  */
  protected static boolean checkPartialPathMatch( String sourceMatch, int sourceIndex, String match, int requiredExtraPathSections )
  {
    // The partial match must be of a complete path, with at least a specified number of trailing path components possible in what remains.
    // Path components can include everything but the "/" character itself.
    //
    // The match string is the one containing the wildcards.  Both the "*" wildcard and the "?" wildcard will match a "/", which is intended but is why this
    // matcher is a little tricky to write.
    //
    // Note also that it is OK to return "true" more than strictly necessary, but it is never OK to return "false" incorrectly.

    // This is a partial path match.  That means that we don't have to completely use up the match string, but what's left on the match string after the source
    // string is used up MUST either be capable of being null, or be capable of starting with a "/"integral path sections, and MUST include at least n of these sections.
    //

    boolean caseSensitive = true;
    if (!sourceMatch.endsWith("/"))
      sourceMatch = sourceMatch + "/";

    return processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex, match, 0, requiredExtraPathSections );
  }

  /** Recursive worker method for checkPartialPathMatch.  Returns 'true' if there is a path that consumes the source string entirely,
  * and leaves the remainder of the match string able to match the required followup.
  *@param caseSensitive is true if file names are case sensitive.
  *@param sourceMatch is the source string (w/o wildcards)
  *@param sourceIndex is the current point in the source string.
  *@param match is the match string (w/wildcards)
  *@param matchIndex is the current point in the match string.
  *@return true if there is a match.
  */
  protected static boolean processPartialPathCheck(boolean caseSensitive, String sourceMatch, int sourceIndex, String match, int matchIndex,
    int requiredExtraPathSections)
  {
    // Match up through the next * we encounter
    while ( true )
    {
      // If we've reached the end of the source, verify that it's a match.
      if ( sourceMatch.length() == sourceIndex)
      {
        // The "correct" way to code this is to recursively attempt to generate all different paths that correspond to the required extra sections.  However,
        // that's computationally very nasty.  In practice, we'll simply distinguish between "some" and "none".
        // If we've reached the end of the match string too, then it passes (or fails, if we need extra sections)
        if (match.length() == matchIndex)
          return (requiredExtraPathSections == 0);
        // We can match a path separator, so we win
        return true;
      }
      // If we have reached the end of the match (but not the source), match fails
      if ( match.length() == matchIndex )
        return false;
      char x = sourceMatch.charAt( sourceIndex );
      char y = match.charAt( matchIndex );
      if ( !caseSensitive )
      {
        if ( x >= 'A' && x <= 'Z' )
          x -= 'A'-'a';
        if ( y >= 'A' && y <= 'Z' )
          y -= 'A'-'a';
      }
      if ( y == '*' )
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex + 1, match, matchIndex, requiredExtraPathSections ) ||
          processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex, match, matchIndex + 1, requiredExtraPathSections );
      }
      if ( y == '?' || x == y )
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /** Check a match between two strings with wildcards.
  *@param sourceMatch is the expanded string (no wildcards)
  *@param sourceIndex is the starting point in the expanded string.
  *@param match is the wildcard-based string.
  *@return true if there is a match.
  */
  protected static boolean checkMatch( String sourceMatch, int sourceIndex, String match )
  {
    // Note: The java regex stuff looks pretty heavyweight for this purpose.
    // I've opted to try and do a simple recursive version myself, which is not compiled.
    // Basically, the match proceeds by recursive descent through the string, so that all *'s cause
    // recursion.
    boolean caseSensitive = true;

    return processCheck( caseSensitive, sourceMatch, sourceIndex, match, 0 );
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
  protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex, String match, int matchIndex )
  {
    // Match up through the next * we encounter
    while ( true )
    {
      // If we've reached the end, it's a match.
      if ( sourceMatch.length() == sourceIndex && match.length() == matchIndex )
        return true;
      // If one has reached the end but the other hasn't, no match
      if ( match.length() == matchIndex )
        return false;
      if ( sourceMatch.length() == sourceIndex )
      {
        if ( match.charAt(matchIndex) != '*' )
          return false;
        matchIndex++;
        continue;
      }
      char x = sourceMatch.charAt( sourceIndex );
      char y = match.charAt( matchIndex );
      if ( !caseSensitive )
      {
        if ( x >= 'A' && x <= 'Z' )
          x -= 'A'-'a';
        if ( y >= 'A' && y <= 'Z' )
          y -= 'A'-'a';
      }
      if ( y == '*' )
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processCheck( caseSensitive, sourceMatch, sourceIndex + 1, match, matchIndex ) ||
          processCheck( caseSensitive, sourceMatch, sourceIndex, match, matchIndex + 1 );
      }
      if ( y == '?' || x == y )
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(Specification spec)
  {
    HashMap map = new HashMap();
    int i = 0;
    boolean securityOn = true;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("access"))
      {
        String token = sn.getAttributeValue("token");
        map.put(token,token);
      }
      else if (sn.getType().equals("security"))
      {
        String value = sn.getAttributeValue("value");
        if (value.equals("on"))
          securityOn = true;
        else if (value.equals("off"))
          securityOn = false;
      }
    }
    if (!securityOn)
      return null;

    String[] rval = new String[map.size()];
    Iterator iter = map.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    return rval;
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

  /** Metadata information gleaned from document paths and specification.
  */
  protected static class MetadataInformation
  {
    protected boolean allMetadata = false;
    protected Set<String> metadataFields = new HashSet<String>();

    /** Constructor */
    public MetadataInformation()
    {
    }

    /** Set "all metadata" */
    public void setAllMetadata()
    {
      allMetadata = true;
    }

    /** Add a metadata field */
    public void addMetadataField(String fieldName)
    {
      metadataFields.add(fieldName);
    }

    /** Get whether "all metadata" is to be used */
    public boolean getAllMetadata()
    {
      return allMetadata;
    }

    /** Get the set of metadata fields to use */
    public String[] getMetadataFields()
    {
      String[] rval = new String[metadataFields.size()];
      int i = 0;
      for (String field : metadataFields)
      {
        rval[i++] = field;
      }
      return rval;
    }
  }

  /** Class that tracks paths associated with id's, and the name
  * of the metadata attribute to use for the path.
  */
  protected class SystemMetadataDescription
  {
    // The path attribute name
    protected final String pathAttributeName;

    // The path name map
    protected final MatchMap matchMap = new MatchMap();

    /** Constructor */
    public SystemMetadataDescription(Specification spec)
      throws ManifoldCFException
    {
      String pathAttributeName = null;
      for (int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode n = spec.getChild(i);
        if (n.getType().equals("pathnameattribute"))
          pathAttributeName = n.getAttributeValue("value");
        else if (n.getType().equals("pathmap"))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
      }
      this.pathAttributeName = pathAttributeName;
    }

    /** Get the path attribute name.
    *@return the path attribute name, or null if none specified.
    */
    public String getPathAttributeName()
    {
      return pathAttributeName;
    }

    /** Given an identifier, get the translated string that goes into the metadata.
    */
    public String getPathAttributeValue(String documentIdentifier)
      throws ManifoldCFException
    {
      String path = getPathString(documentIdentifier);
      return matchMap.translate(path);
    }

    /** For a given id, get the portion of its path which the mapping and ingestion
    * should go against.  Effectively this should include the whole identifer, so this
    * is easy to calculate.
    */
    public String getPathString(String documentIdentifier)
      throws ManifoldCFException
    {
      // There will be a "//" somewhere in the string.  Remove it!
      int dslashIndex = documentIdentifier.indexOf("//");
      if (dslashIndex == -1)
        return documentIdentifier;
      return documentIdentifier.substring(0,dslashIndex) + documentIdentifier.substring(dslashIndex+1);
    }
  }


}
