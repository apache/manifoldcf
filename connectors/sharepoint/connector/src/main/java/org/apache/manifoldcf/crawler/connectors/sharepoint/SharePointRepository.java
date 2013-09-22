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
import org.apache.manifoldcf.core.extmimemap.ExtensionMimeMap;

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

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

/** This is the "repository connector" for Microsoft SharePoint.
* Document identifiers for this connector come in three forms:
* (1) An "S" followed by the encoded subsite/library path, which represents the encoded relative path from the root site to a library. [deprecated and no longer supported];
* (2) A "D" followed by a subsite/library/folder/file path, which represents the relative path from the root site to a file. [deprecated and no longer supported]
* (3) Five different kinds of unencoded path, each of which starts with a "/" at the beginning, where the "/" represents the root site of the connection, as follows:
*   /sitepath/ - the relative path to a site.  The path MUST both begin and end with a single "/".
*   /sitepath/libraryname// - the relative path to a library.  The path MUST begin with a single "/" and end with "//".
*   /sitepath/libraryname//folderfilepath - the relative path to a file.  The path MUST begin with a single "/" and MUST include a "//" after the library, and must NOT end with a "/".
*   /sitepath/listname/// - the relative path to a list.  The path MUST begin with a single "/" and end with "///".
*   /sitepath/listname///rowid - the relative path to a list item.  The path MUST begin with a single "/" and MUST include a "///" after the list name, and must NOT end in a "/".
*   /sitepath/listname///rowid/attachment_filename - the relative path to a list attachment.  The path MUST begin with a single "/", MUST include a "///" after the list name, and
*      MUST include a "/" separating the rowid from the filename.
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
  
  private ClientConnectionManager connectionManager = null;
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
      String serverVersion = params.getParameter( "serverVersion" );
      if (serverVersion == null)
        serverVersion = "2.0";
      supportsItemSecurity = !serverVersion.equals("2.0");
      dspStsWorks = !serverVersion.equals("4.0");
      attachmentsSupported = !serverVersion.equals("2.0");

      serverProtocol = params.getParameter( "serverProtocol" );
      if (serverProtocol == null)
        serverProtocol = "http";
      try
      {
        String serverPort = params.getParameter( "serverPort" );
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
      serverLocation = params.getParameter("serverLocation");
      if (serverLocation == null)
        serverLocation = "";
      if (serverLocation.endsWith("/"))
        serverLocation = serverLocation.substring(0,serverLocation.length()-1);
      if (serverLocation.length() > 0 && !serverLocation.startsWith("/"))
        serverLocation = "/" + serverLocation;
      encodedServerLocation = serverLocation;
      serverLocation = decodePath(serverLocation);

      userName = params.getParameter( "userName" );
      password = params.getObfuscatedParameter( "password" );
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

      // Set up ssl if indicated
      keystoreData = params.getParameter( "keystore" );

      PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
      localConnectionManager.setMaxTotal(1);
      connectionManager = localConnectionManager;

      if (keystoreData != null)
      {
        keystoreManager = KeystoreManagerFactory.make("",keystoreData);
        SSLSocketFactory myFactory = new SSLSocketFactory(keystoreManager.getSecureSocketFactory(), new BrowserCompatHostnameVerifier());
        Scheme myHttpsProtocol = new Scheme("https", 443, myFactory);
        connectionManager.getSchemeRegistry().register(myHttpsProtocol);
      }

      fileBaseUrl = serverUrl + encodedServerLocation;

      BasicHttpParams params = new BasicHttpParams();
      params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
      params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,false);
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,60000);
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,900000);
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
      localHttpClient.setRedirectStrategy(new DefaultRedirectStrategy());
      if (strippedUserName != null)
      {
        localHttpClient.getCredentialsProvider().setCredentials(
          new AuthScope(serverName,serverPort),
          new NTCredentials(strippedUserName, password, currentHost, ntlmDomain));
      }

      httpClient = localHttpClient;
      
      proxy = new SPSProxyHelper( serverUrl, encodedServerLocation, serverLocation, userName, password,
        getClass(), "sharepoint-client-config.wsdd",
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
    serverName = configParameters.getParameter( "serverName" );
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
    // Since we pick up acls on a per-lib basis, it helps to have this bigger than 1.
    return 10;
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
        
        Map fieldSet = getLibFieldList(sitePath,library);
        Iterator iter = fieldSet.keySet().iterator();
        while (iter.hasNext())
        {
          String fieldName = (String)iter.next();
          String displayName = (String)fieldSet.get(fieldName);
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
        
        Map fieldSet = getListFieldList(sitePath,listName);
        Iterator iter = fieldSet.keySet().iterator();
        while (iter.hasNext())
        {
          String fieldName = (String)iter.next();
          String displayName = (String)fieldSet.get(fieldName);
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
  * The times passed to this method may be interpreted for greatest efficiency.  The time ranges
  * any given job uses with this connector will not overlap, but will proceed starting at 0 and going
  * to the "current time", each time the job is run.  For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding start time to 0.  The
  * seeding start time may also be set to 0 on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  @Override
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    // Check the session
    getSession();
    // Add just the root.
    activities.addSeedDocument("/");
  }


  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();

    // Get the forced acls.  (We need this only for the case where documents have their own acls)
    String[] forcedAcls = getAcls(spec);

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

    String[] rval = new String[documentIdentifiers.length];
    
    i = 0;
    while (i < rval.length)
    {
      // Check if we should abort
      activities.checkJobStillActive();

      String documentIdentifier = documentIdentifiers[i];
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug( "SharePoint: Getting version of '" + documentIdentifier + "'");
      if ( documentIdentifier.startsWith("D") || documentIdentifier.startsWith("S") )
      {
        // Old-style document identifier.  We don't recognize these anymore, so signal deletion.
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Removing old-style document identifier '"+documentIdentifier+"'");
        rval[i] = null;
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
            if (checkIncludeList(documentIdentifier.substring(0,documentIdentifier.length()-3),spec))
              // This is the path for the list: No versioning
              rval[i] = "";
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: List specification no longer includes list '"+documentIdentifier+"' - removing");
              rval[i] = null;
            }
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

            int attachmentSeparatorIndex = itemAndAttachment.indexOf("/");
            if (attachmentSeparatorIndex == -1)
            {
              // == List item path! ==
              if (checkIncludeListItem(decodedItemPath,spec))
              {
                // This file is included, so calculate a version string.  This will include metadata info, so get that first.
                MetadataInformation metadataInfo = getMetadataSpecification(decodedItemPath,spec);

                String[] accessTokens = activities.retrieveParentData(documentIdentifier, "accessTokens");
                String[] denyTokens = activities.retrieveParentData(documentIdentifier, "denyTokens");
                String[] listIDs = activities.retrieveParentData(documentIdentifier, "guids");
                String[] listFields = activities.retrieveParentData(documentIdentifier, "fields");

                String listID;
                if (listIDs.length >= 1)
                  listID = listIDs[0];
                else
                  listID = null;

                if (listID != null)
                {
                  String[] sortedMetadataFields = getInterestingFieldSetSorted(metadataInfo,listFields);
                  
                  // Sort access tokens so they are comparable in the version string
                  java.util.Arrays.sort(accessTokens);
                  java.util.Arrays.sort(denyTokens);

                  // Next, get the actual timestamp field for the file.
                  ArrayList metadataDescription = new ArrayList();
                  metadataDescription.add("Modified");
                  metadataDescription.add("Created");
                  // The document path includes the library, with no leading slash, and is decoded.
                  String decodedItemPathWithoutSite = decodedItemPath.substring(cutoff+1);
                  Map<String,String> values = proxy.getFieldValues( metadataDescription, encodedSitePath, listID, "/Lists/" + decodedItemPathWithoutSite, dspStsWorks );
                  String modifiedDate = values.get("Modified");
                  String createdDate = values.get("Created");
                  if (modifiedDate != null)
                  {
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
                    // The rest of this is unparseable
                    sb.append(versionToken);
                    sb.append(pathNameAttributeVersion);
                    // Added 9/7/07
                    sb.append("_").append(fileBaseUrl);
                    //
                    rval[i] = sb.toString();
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + rval[i]);
                  }
                  else
                  {
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has no modify date");
                    rval[i] = null;
                  }
                }
                else
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because list '"+decodedListPath+"' does not exist - removing");
                  rval[i] = null;
                }
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: List item '"+documentIdentifier+"' is no longer included - removing");
                rval[i] = null;
              }
            }
            else
            {
              // == List item attachment path! ==
              if (checkIncludeListItemAttachment(decodedItemPath,spec))
              {

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
                if (modifiedDate != null && createdDate != null && url != null)
                {
                  // Item has a modified date so we presume it exists.
                      
                  Date modifiedDateValue = new Date(new Long(modifiedDate).longValue());
                  Date createdDateValue = new Date(new Long(createdDate).longValue());
                      
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
                  rval[i] = sb.toString();
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + rval[i]);
                }
                else
                {
                  // Can't look up list ID, which means the list is gone, so delete
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because list no longer apparently exists");
                  rval[i] = null;
                }
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: List item attachment '"+documentIdentifier+"' is no longer included - removing");
                rval[i] = null;
              }
            }
          }
        }
        else if (dLibSeparatorIndex != -1)
        {
          // === Library-style identifier ===
          if (dLibSeparatorIndex == documentIdentifier.length() - 2)
          {
            // Library path!
            if (checkIncludeLibrary(documentIdentifier.substring(0,documentIdentifier.length()-2),spec))
              // This is the path for the library: No versioning
              rval[i] = "";
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Library specification no longer includes library '"+documentIdentifier+"' - removing");
              rval[i] = null;
            }
          }
          else
          {
            // Document path!
            // Convert the modified document path to an unmodified one, plus a library path.
            String decodedLibPath = documentIdentifier.substring(0,dLibSeparatorIndex);
            String decodedDocumentPath = decodedLibPath + documentIdentifier.substring(dLibSeparatorIndex+1);
            if (checkIncludeFile(decodedDocumentPath,spec))
            {
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
              
              if (libID != null)
              {
                String encodedSitePath = encodePath(sitePath);
                String[] sortedMetadataFields = getInterestingFieldSetSorted(metadataInfo,libFields);
                
                // Sort access tokens
                java.util.Arrays.sort(accessTokens);
                java.util.Arrays.sort(denyTokens);

                // Next, get the actual timestamp field for the file.
                ArrayList metadataDescription = new ArrayList();
                metadataDescription.add("Last_x0020_Modified");
                metadataDescription.add("Modified");
                metadataDescription.add("Created");
                // The document path includes the library, with no leading slash, and is decoded.
                int cutoff = decodedLibPath.lastIndexOf("/");
                String decodedDocumentPathWithoutSite = decodedDocumentPath.substring(cutoff+1);
                Map<String,String> values = proxy.getFieldValues( metadataDescription, encodedSitePath, libID, decodedDocumentPathWithoutSite, dspStsWorks );

                String modifiedDate = values.get("Modified");
                String createdDate = values.get("Created");
                  
                String modifyDate = values.get("Last_x0020_Modified");
                if (modifyDate != null)
                {
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
                      accessTokens = proxy.getDocumentACLs( encodedSitePath, encodePath(decodedDocumentPath) );
                      denyTokens = new String[]{defaultAuthorityDenyToken};
                    }
                  }
                  
                  if (accessTokens != null)
                  {
                    // Revamped version string on 9/21/2013 to make parseability better

                    StringBuilder sb = new StringBuilder();

                    packList(sb,sortedMetadataFields,'+');
                    packList(sb,accessTokens,'+');
                    packList(sb,denyTokens,'+');
                    packDate(sb,modifiedDateValue);
                    packDate(sb,createdDateValue);
                    // The rest of this is unparseable
                    sb.append(versionToken);
                    sb.append(pathNameAttributeVersion);
                    // Added 9/7/07
                    sb.append("_").append(fileBaseUrl);
                    //
                    rval[i] = sb.toString();
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + rval[i]);
                  }
                  else
                  {
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: Couldn't get access tokens for item '"+decodedDocumentPath+"'; removing document '"+documentIdentifier+"'");
                    rval[i] = null;
                  }
                }
                else
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has no modify date");
                  rval[i] = null;
                }
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because library '"+decodedLibPath+"' does not exist - removing");
                rval[i] = null;
              }
            }
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Document '"+documentIdentifier+"' is no longer included - removing");
              rval[i] = null;
            }
          }
        }
        else
        {
          // === Site-style identifier ===
          String sitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);
          if (sitePath.length() == 0)
            sitePath = "/";
          if (checkIncludeSite(sitePath,spec))
            // This is the path for the site: No versioning
            rval[i] = "";
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Site specification no longer includes site '"+documentIdentifier+"' - removing");
            rval[i] = null;
          }
        }
      }
      else
        throw new ManifoldCFException("Invalid document identifier discovered: '"+documentIdentifier+"'");
      i++;
    }
    return rval;
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

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();

    // Read the forced acls.  A null return indicates that security is disabled!!!
    // A zero-length return indicates that the native acls should be used.
    // All of this is germane to how we ingest the document, so we need to note it in
    // the version string completely.
    String[] forcedAcls = getAcls(spec);

    // Decode the system metadata part of the specification
    SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

    Map<String,String> docLibIDMap = new HashMap<String,String>();
    Map<String,String> listIDMap = new HashMap<String,String>();

    int i = 0;
    while (i < documentIdentifiers.length)
    {
      // Make sure the job is still active
      activities.checkJobStillActive();

      String documentIdentifier = documentIdentifiers[i];
      String version = versions[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug( "SharePoint: Processing: '" + documentIdentifier + "'");
      if ( documentIdentifier.startsWith("/") )
      {
        // New document identifier format.
        int dListSeparatorIndex = documentIdentifier.indexOf("///");
        int dLibSeparatorIndex = documentIdentifier.indexOf("//");
        if (dListSeparatorIndex != -1)
        {
          // === List style identifier ===
          if (dListSeparatorIndex == documentIdentifier.length() - 3)
          {
            String siteListPath = documentIdentifier.substring(0,documentIdentifier.length()-3);
            int listCutoff = siteListPath.lastIndexOf( "/" );
            String site = siteListPath.substring(0,listCutoff);
            String listName = siteListPath.substring( listCutoff + 1 );

            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Document identifier is a list: '" + siteListPath + "'" );

            String listID = proxy.getListID( encodePath(site), site, listName );
            if (listID != null)
            {
              String encodedSitePath = encodePath(site);
              
              // Get the list's fields
              Map<String,String> fieldNames = proxy.getFieldList( encodedSitePath, listID );
              if (fieldNames != null)
              {
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
                  accessTokens = proxy.getACLs( encodedSitePath, listID );
                  denyTokens = new String[]{defaultAuthorityDenyToken};
                }

                if (accessTokens != null)
                {
                  ListItemStream fs = new ListItemStream( activities, encodedServerLocation, site, siteListPath, spec,
                    documentIdentifier, accessTokens, denyTokens, listID, fields );
                  boolean success = proxy.getChildren( fs, encodedSitePath , listID, dspStsWorks );
                  if (!success)
                  {
                    // Site/list no longer exists, so delete entry
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: No list found for list '"+siteListPath+"' - deleting");
                    activities.deleteDocument(documentIdentifier,version);
                  }
                }
                else
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Access token lookup failed for list '"+siteListPath+"' - deleting");
                  activities.deleteDocument(documentIdentifier,version);
                }
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Field list lookup failed for list '"+siteListPath+"' - deleting");
                activities.deleteDocument(documentIdentifier,version);
              }
            }
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: GUID lookup failed for list '"+siteListPath+"' - deleting");
              activities.deleteDocument(documentIdentifier,version);
            }
          }
          else
          {
            // == List item or attachment identifier ==
            
            // Get the item part of the path
            String decodedListPath = documentIdentifier.substring(0,dListSeparatorIndex);
            String itemAndAttachment = documentIdentifier.substring(dListSeparatorIndex+2);
            String decodedItemPath = decodedListPath + itemAndAttachment;
            
            // If the item part has a slash, we're looking at an attachment
            int attachmentSeparatorIndex = itemAndAttachment.indexOf("/");
            if (attachmentSeparatorIndex == -1)
            {
              // == List item identifier ==
              
              // Before we index, we queue up any attachments
              int cutoff = decodedListPath.lastIndexOf( "/" );
              String site = decodedListPath.substring(0,cutoff);
              String listName = decodedListPath.substring( cutoff + 1 );
              
              // Before we do anything further, unpack the info from the version string.
              // We need to do this always because attachment queuing will require it, if enabled.

              // Placeholder for metadata specification
              ArrayList metadataDescription = new ArrayList();
              int startPosition = unpackList(metadataDescription,version,0,'+');

              // Acls
              ArrayList acls = new ArrayList();
              ArrayList denyAcls = new ArrayList();
              startPosition = unpackList(acls,version,startPosition,'+');
              startPosition = unpackList(denyAcls,version,startPosition,'+');

              Date modifiedDate = new Date(0L);
              startPosition = unpackDate(version,startPosition,modifiedDate);
              if (modifiedDate.getTime() == 0L)
                modifiedDate = null;
              Date createdDate = new Date(0L);
              startPosition = unpackDate(version,startPosition,createdDate);
              if (createdDate.getTime() == 0L)
                createdDate = null;

              // Now, do any queuing that is needed.
              if (attachmentsSupported)
              {
                String itemID = itemAndAttachment.substring(attachmentSeparatorIndex+1);
                String listID = listIDMap.get(decodedListPath);
                if (listID == null)
                {
                  listID = proxy.getListID( encodePath(site), site, listName );
                  if (listID == null)
                    listID = "";
                  listIDMap.put(decodedListPath,listID);
                }
  
                if (listID.length() == 0)
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: List '"+decodedListPath+"' no longer exists - deleting item '"+documentIdentifier+"'");
                  activities.deleteDocument(documentIdentifier,version);
                  i++;
                  continue;
                }

                // Get the attachment names
                // MHL: need to get the item number from somewhere
                String itemNumber = itemID;
                
                List<NameValue> attachmentNames = proxy.getAttachmentNames( site, listID, itemNumber );
                // Now, queue up each attachment as a separate entry
                for (NameValue attachmentName : attachmentNames)
                {
                  // For attachments, we use the carry-down feature to get the data where we need it.  That's why
                  // we unpacked the version information early above.
                  
                  // No check for inclusion; if the list item is included, so is this
                  String[] dataNames = new String[]{"createdDate","modifiedDate","accessTokens","denyTokens","url"};
                  String[][] dataValues = new String[3][];
                  if (createdDate == null)
                    dataValues[0] = new String[0];
                  else
                    dataValues[0] = new String[]{new Long(createdDate.getTime()).toString()};
                  if (modifiedDate == null)
                    dataValues[1] = new String[0];
                  else
                    dataValues[1] = new String[]{new Long(modifiedDate.getTime()).toString()};
                  if (acls == null)
                    dataValues[2] = new String[0];
                  else
                    dataValues[2] = (String[])acls.toArray(new String[0]);
                  if (denyAcls == null)
                    dataValues[3] = new String[0];
                  else
                    dataValues[3] = (String[])denyAcls.toArray(new String[0]);
                  dataValues[4] = new String[]{attachmentName.getPrettyName()};

                  activities.addDocumentReference(documentIdentifier + "/" + attachmentName.getValue(),
                    documentIdentifier, null, dataNames, dataValues);
                  
                }
              }
              
              if ( !scanOnly[ i ] )
              {
                // Convert the modified document path to an unmodified one, plus a library path.
                String encodedItemPath = encodePath(decodedListPath.substring(0,cutoff) + "/Lists/" + decodedItemPath.substring(cutoff+1));
                
                
                // Generate the URL we are going to use
                String itemUrl = fileBaseUrl + encodedItemPath;
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug( "SharePoint: Processing list item '"+documentIdentifier+"'; url: '" + itemUrl + "'" );

                // Fetch the metadata we will be indexing
                Map<String,String> metadataValues = null;
                if (metadataDescription.size() > 0)
                {
                  String listID = listIDMap.get(decodedListPath);
                  if (listID == null)
                  {
                    listID = proxy.getListID( encodePath(site), site, listName );
                    if (listID == null)
                      listID = "";
                    listIDMap.put(decodedListPath,listID);
                  }

                  if (listID.length() == 0)
                  {
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: List '"+decodedListPath+"' no longer exists - deleting item '"+documentIdentifier+"'");
                    activities.deleteDocument(documentIdentifier,version);
                    i++;
                    continue;
                  }

                  metadataValues = proxy.getFieldValues( metadataDescription, encodePath(site), listID, "/Lists/" + decodedItemPath.substring(cutoff+1), dspStsWorks );
                  if (metadataValues == null)
                  {
                    // Item has vanished
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: Item metadata fetch failure indicated that item is gone: '"+documentIdentifier+"' - removing");
                    activities.deleteDocument(documentIdentifier,version);
                    i++;
                    continue;
                  }
                }
                
                if (activities.checkLengthIndexable(0L))
                {
                  InputStream is = new ByteArrayInputStream(new byte[0]);
                  try
                  {
                    RepositoryDocument data = new RepositoryDocument();
                    data.setBinary( is, 0L );

                    if (modifiedDate != null)
                      data.setModifiedDate(modifiedDate);
                    if (createdDate != null)
                      data.setCreatedDate(createdDate);
                    
                    setDataACLs(data,acls,denyAcls);
                    
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

                    activities.ingestDocument( documentIdentifier, version, itemUrl , data );
                  }
                  finally
                  {
                    try
                    {
                      is.close();
                    }
                    catch (InterruptedIOException e)
                    {
                      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
                    }
                    catch (IOException e)
                    {
                      // This should never happen; we're closing a bytearrayinputstream
                    }
                  }
                }
                else
                  // Document too long (should never happen; length is 0)
                  activities.deleteDocument( documentIdentifier, version );
              }
            }
            else
            {
              // == List item attachment identifier ==
              if (!scanOnly[i])
              {
                // Unpack the version info.
                int startPosition = 0;
                StringBuilder urlBuffer = new StringBuilder();
                ArrayList accessTokens = new ArrayList();
                ArrayList denyTokens = new ArrayList();
                Date modifiedDate = new Date(0L);
                Date createdDate = new Date(0L);
                
                startPosition = unpack(urlBuffer,version,startPosition,'+');
                startPosition = unpackList(accessTokens,version,startPosition,'+');
                startPosition = unpackList(denyTokens,version,startPosition,'+');
                startPosition = unpackDate(version,startPosition,modifiedDate);
                startPosition = unpackDate(version,startPosition,createdDate);
                
                if (modifiedDate.getTime() == 0L)
                  modifiedDate = null;
                if (createdDate.getTime() == 0L)
                  createdDate = null;

                // Fetch and index.  This also filters documents based on output connector restrictions.
                String fileUrl = serverUrl + urlBuffer.toString();

                if (!fetchAndIndexFile(activities, documentIdentifier, version, fileUrl, fileUrl,
                  accessTokens, denyTokens, createdDate, modifiedDate, null, sDesc))
                {
                  // Document not indexed for whatever reason
                  activities.deleteDocument(documentIdentifier,version);
                  i++;
                  continue;
                }

              }
            }
          }
        }
        else if (dLibSeparatorIndex != -1)
        {
          // === Library style identifier ===
          if (dLibSeparatorIndex == documentIdentifier.length() - 2)
          {
            // It's a library.
            String siteLibPath = documentIdentifier.substring(0,documentIdentifier.length()-2);
            int libCutoff = siteLibPath.lastIndexOf( "/" );
            String site = siteLibPath.substring(0,libCutoff);
            String libName = siteLibPath.substring( libCutoff + 1 );

            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug( "SharePoint: Document identifier is a library: '" + siteLibPath + "'" );

            String libID = proxy.getDocLibID( encodePath(site), site, libName );
            if (libID != null)
            {
              String encodedSitePath = encodePath(site);
              
              // Get the lib's fields
              Map<String,String> fieldNames = proxy.getFieldList( encodedSitePath, libID );
              if (fieldNames != null)
              {
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
                  accessTokens = proxy.getACLs( encodedSitePath, libID );
                  denyTokens = new String[]{defaultAuthorityDenyToken};
                }

                if (accessTokens != null)
                {
                  FileStream fs = new FileStream( activities, encodedServerLocation, site, siteLibPath, spec,
                    documentIdentifier, accessTokens, denyTokens, libID, fields );
                  boolean success = proxy.getChildren( fs, encodedSitePath , libID, dspStsWorks );
                  if (!success)
                  {
                    // Site/library no longer exists, so delete entry
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("SharePoint: No list found for library '"+siteLibPath+"' - deleting");
                    activities.deleteDocument(documentIdentifier,version);
                  }
                }
                else
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Access token lookup failed for library '"+siteLibPath+"' - deleting");
                  activities.deleteDocument(documentIdentifier,version);
                }
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("SharePoint: Field list lookup failed for library '"+siteLibPath+"' - deleting");
                activities.deleteDocument(documentIdentifier,version);
              }
            }
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: GUID lookup failed for library '"+siteLibPath+"' - deleting");
              activities.deleteDocument(documentIdentifier,version);
            }
          }
          else
          {
            // File/folder identifier
            if ( !scanOnly[ i ] )
            {
              // Convert the modified document path to an unmodified one, plus a library path.
              String decodedLibPath = documentIdentifier.substring(0,dLibSeparatorIndex);
              String decodedDocumentPath = decodedLibPath + documentIdentifier.substring(dLibSeparatorIndex+1);
              String encodedDocumentPath = encodePath(decodedDocumentPath);

              int libCutoff = decodedLibPath.lastIndexOf( "/" );
              String site = decodedLibPath.substring(0,libCutoff);
              String libName = decodedLibPath.substring( libCutoff + 1 );

              // Parse what we need out of version string.

              // Placeholder for metadata specification
              ArrayList metadataDescription = new ArrayList();
              int startPosition = unpackList(metadataDescription,version,0,'+');

              // Acls
              ArrayList acls = new ArrayList();
              ArrayList denyAcls = new ArrayList();
              startPosition = unpackList(acls,version,startPosition,'+');
              startPosition = unpackList(denyAcls,version,startPosition,'+');

              // Dates
              Date modifiedDate = new Date(0L);
              startPosition = unpackDate(version,startPosition,modifiedDate);
              if (modifiedDate.getTime() == 0L)
                modifiedDate = null;
              Date createdDate = new Date(0L);
              startPosition = unpackDate(version,startPosition,createdDate);
              if (createdDate.getTime() == 0L)
                createdDate = null;
              
              // Generate the URL we are going to use
              String fileUrl = fileBaseUrl + encodedDocumentPath;
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug( "SharePoint: Processing file '"+documentIdentifier+"'; url: '" + fileUrl + "'" );

              // First, fetch the metadata we plan to index.
              Map<String,String> metadataValues = null;
              if (metadataDescription.size() > 0)
              {
                String documentLibID = docLibIDMap.get(decodedLibPath);
                if (documentLibID == null)
                {
                  documentLibID = proxy.getDocLibID( encodePath(site), site, libName );
                  if (documentLibID == null)
                    documentLibID = "";
                  docLibIDMap.put(decodedLibPath,documentLibID);
                }

                if (documentLibID.length() == 0)
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Library '"+decodedLibPath+"' no longer exists - deleting document '"+documentIdentifier+"'");
                  activities.deleteDocument(documentIdentifier,version);
                  i++;
                  continue;
                }

                int cutoff = decodedLibPath.lastIndexOf("/");
                metadataValues = proxy.getFieldValues( metadataDescription, encodePath(site), documentLibID, decodedDocumentPath.substring(cutoff+1), dspStsWorks );
                if (metadataValues == null)
                {
                  // Document has vanished
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Document metadata fetch failure indicated that document is gone: '"+documentIdentifier+"' - removing");
                  activities.deleteDocument(documentIdentifier,version);
                  i++;
                  continue;
                }
              }

              // Fetch and index.  This also filters documents based on output connector restrictions.
              if (!fetchAndIndexFile(activities, documentIdentifier, version, fileUrl, serverUrl + encodedServerLocation + encodedDocumentPath,
                acls, denyAcls, createdDate, modifiedDate, metadataValues, sDesc))
              {
                // Document not indexed for whatever reason
                activities.deleteDocument(documentIdentifier,version);
                i++;
                continue;
              }
            }
          }
        }
        else
        {
          // === Site-style identifier ===
          // Strip off the trailing "/" to get the site name.
          String decodedSitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);

          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug( "SharePoint: Document identifier is a site: '" + decodedSitePath + "'" );

          // Look at subsites
          List<NameValue> subsites = proxy.getSites( encodePath(decodedSitePath) );
          if (subsites != null)
          {
            int j = 0;
            while (j < subsites.size())
            {
              NameValue subSiteName = subsites.get(j++);
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
            int j = 0;
            while (j < libraries.size())
            {
              NameValue library = libraries.get(j++);
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
            int j = 0;
            while (j < lists.size())
            {
              NameValue list = lists.get(j++);
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
        throw new ManifoldCFException("Found illegal document identifier in processDocuments: '"+documentIdentifier+"'");

      i++;
    }
  }

  /** Method that fetches and indexes a file fetched from a SharePoint URL, with appropriate error handling
  * etc.
  */
  protected boolean fetchAndIndexFile(IProcessActivity activities, String documentIdentifier, String version,
    String fileUrl, String fetchUrl, ArrayList acls, ArrayList denyAcls, Date createdDate, Date modifiedDate,
    Map<String,String> metadataValues, SystemMetadataDescription sDesc)
    throws ManifoldCFException, ServiceInterruption
  {
    // Before we fetch, confirm that the output connector will accept the document
    if (activities.checkURLIndexable(fileUrl))
    {
      // Also check mime type
      String contentType = mapExtensionToMimeType(documentIdentifier);
      if (activities.checkMimeTypeIndexable(contentType))
      {
        // Set stuff up for fetch activity logging
        long startFetchTime = System.currentTimeMillis();
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
                  
                if (returnCode == 404 || returnCode == 401 || returnCode == 400)
                {
                  // Well, sharepoint thought the document was there, but it really isn't, so delete it.
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("SharePoint: Document at '"+fileUrl+"' failed to fetch with code "+Integer.toString(returnCode)+", deleting");
                  activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Not found",Integer.toString(returnCode),null);
                  return false;
                }
                else if (returnCode != 200)
                {
                  activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Error","Http status "+Integer.toString(returnCode),null);
                  throw new ManifoldCFException("Error fetching document '"+fileUrl+"': "+Integer.toString(returnCode));
                }

                // Log the normal fetch activity
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Success",null,null);
                
              }
              catch (InterruptedException e)
              {
                throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
              }
              catch (java.net.SocketTimeoutException e)
              {
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
                Logging.connectors.warn("SharePoint: SocketTimeoutException thrown: "+e.getMessage(),e);
                long currentTime = System.currentTimeMillis();
                throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                  currentTime + 12 * 60 * 60000L,-1,true);
              }
              catch (org.apache.http.conn.ConnectTimeoutException e)
              {
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
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
                Logging.connectors.error("SharePoint: Illegal argument", e);
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
                throw new ManifoldCFException("SharePoint: Illegal argument: "+e.getMessage(),e);
              }
              catch (org.apache.http.HttpException e)
              {
                Logging.connectors.warn("SharePoint: HttpException thrown",e);
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
                long currentTime = System.currentTimeMillis();
                throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
                  currentTime + 12 * 60 * 60000L,-1,true);
              }
              catch (IOException e)
              {
                activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
                  new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
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
            if (activities.checkLengthIndexable(documentLength))
            {
              InputStream is = new FileInputStream(tempFile);
              try
              {
                RepositoryDocument data = new RepositoryDocument();
                data.setBinary( is, documentLength );
                
                data.setFileName(mapToFileName(documentIdentifier));
                          
                if (contentType != null)
                  data.setMimeType(contentType);
                
                setDataACLs(data,acls,denyAcls);

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
                activities.ingestDocument( documentIdentifier, version, fileUrl , data );
                return true;
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
            else
            {
              // Document too long
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("SharePoint: Document '"+documentIdentifier+"' was too long, according to output connector");
              return false;
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
      else
      {
        // Mime type failed
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Skipping document '"+documentIdentifier+"' because output connector says mime type is not indexable");
        return false;
      }
    }
    else
    {
      // URL failed
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Skipping document '"+documentIdentifier+"' because output connector says URL is not indexable");
      return false;
    }
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
  
  protected static void setDataACLs(RepositoryDocument data, ArrayList acls, ArrayList denyAcls)
  {
    if (acls != null)
    {
      String[] actualAcls = new String[acls.size()];
      for (int j = 0; j < actualAcls.length; j++)
      {
        actualAcls[j] = (String)acls.get(j);
      }

      if (Logging.connectors.isDebugEnabled())
      {
        StringBuilder sb = new StringBuilder("SharePoint: Acls: [ ");
        for (int j = 0; j < actualAcls.length; j++)
        {
          sb.append(actualAcls[j]).append(" ");
        }
        sb.append("]");
        Logging.connectors.debug( sb.toString() );
      }

      data.setACL( actualAcls );
    }

    if (denyAcls != null)
    {
      String[] actualDenyAcls = new String[denyAcls.size()];
      for (int j = 0; j < actualDenyAcls.length; j++)
      {
        actualDenyAcls[j] = (String)denyAcls.get(j);
      }

      if (Logging.connectors.isDebugEnabled())
      {
        StringBuilder sb = new StringBuilder("SharePoint: DenyAcls: [ ");
        for (int j = 0; j < actualDenyAcls.length; j++)
        {
          sb.append(actualDenyAcls[j]).append(" ");
        }
        sb.append("]");
        Logging.connectors.debug( sb.toString() );
      }

      data.setDenyACL(actualDenyAcls);
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
    protected final DocumentSpecification spec;
    protected final String rootPath;
    protected final String sitePath;
    protected final String siteLibPath;
    
    // For carry-down
    protected final String documentIdentifier;
    protected final String[][] dataValues;
    
    public FileStream(IProcessActivity activities, String rootPath, String sitePath, String siteLibPath, DocumentSpecification spec,
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
    
    public void addFile(String relPath)
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
  
  protected final static String[] listItemStreamDataNames = new String[]{"accessTokens", "denyTokens", "guids", "fields"};

  protected class ListItemStream implements IFileStream
  {
    protected final IProcessActivity activities;
    protected final DocumentSpecification spec;
    protected final String rootPath;
    protected final String sitePath;
    protected final String siteListPath;

    // For carry-down
    protected final String documentIdentifier;
    protected final String[][] dataValues;

    public ListItemStream(IProcessActivity activities, String rootPath, String sitePath, String siteListPath, DocumentSpecification spec,
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
    
    public void addFile(String relPath)
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

        if (relPath.startsWith(sitePath))
        {
          relPath = relPath.substring(sitePath.length());
          
          // Now, strip "Lists" from relPath
          if (!relPath.startsWith("/Lists/"))
            throw new ManifoldCFException("Expected path to start with /Lists/");
          relPath = sitePath + relPath.substring("/Lists".length());
          if ( checkIncludeListItem( relPath, spec ) )
          {
            if (relPath.startsWith(siteListPath))
            {
              // Since the processing for a item needs to know the list path, we need a way to signal the cutoff between list and item levels.
              // The way I've chosen to do this is to use a triple slash at that point, as a separator.
              String modifiedPath = relPath.substring(0,siteListPath.length()) + "//" + relPath.substring(siteListPath.length());

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
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function ShpDeleteCertificate(aliasName)\n"+
"{\n"+
"  editconnection.shpkeystorealias.value = aliasName;\n"+
"  editconnection.configop.value = \"Delete\";\n"+
"  postForm();\n"+
"}\n"+
"\n"+
"function ShpAddCertificate()\n"+
"{\n"+
"  if (editconnection.shpcertificate.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.ChooseACertificateFile")+"\");\n"+
"    editconnection.shpcertificate.focus();\n"+
"  }\n"+
"  else\n"+
"  {\n"+
"    editconnection.configop.value = \"Add\";\n"+
"    postForm();\n"+
"  }\n"+
"}\n"+
"\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.serverPort.value != \"\" && !isInteger(editconnection.serverPort.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSupplyAValidNumber")+"\");\n"+
"    editconnection.serverPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverName.value.indexOf(\"/\") >= 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSpecifyAnyServerPathInformation")+"\");\n"+
"    editconnection.serverName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  var svrloc = editconnection.serverLocation.value;\n"+
"  if (svrloc != \"\" && svrloc.charAt(0) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.SitePathMustBeginWithWCharacter")+"\");\n"+
"    editconnection.serverLocation.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (svrloc != \"\" && svrloc.charAt(svrloc.length - 1) == \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.SitePathCannotEndWithACharacter")+"\");\n"+
"    editconnection.serverLocation.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userName.value != \"\" && editconnection.userName.value.indexOf(\"\\\\\") <= 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.AValidSharePointUserNameHasTheForm")+"\");\n"+
"    editconnection.userName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave() \n"+
"{\n"+
"  if (editconnection.serverName.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseFillInASharePointServerName")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.serverName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverName.value.indexOf(\"/\") >= 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSpecifyAnyServerPathInformationInTheSitePathField")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.serverName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  var svrloc = editconnection.serverLocation.value;\n"+
"  if (svrloc != \"\" && svrloc.charAt(0) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.SitePathMustBeginWithWCharacter")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.serverLocation.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (svrloc != \"\" && svrloc.charAt(svrloc.length - 1) == \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.SitePathCannotEndWithACharacter")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.serverLocation.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverPort.value != \"\" && !isInteger(editconnection.serverPort.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSupplyASharePointPortNumber")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.serverPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userName.value != \"\" && editconnection.userName.value.indexOf(\"\\\\\") <= 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.TheConnectionRequiresAValidSharePointUserName")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"SharePointRepository.Server") + "\");\n"+
"    editconnection.userName.focus();\n"+
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
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String serverVersion = parameters.getParameter("serverVersion");
    if (serverVersion == null)
      serverVersion = "2.0";

    String serverProtocol = parameters.getParameter("serverProtocol");
    if (serverProtocol == null)
      serverProtocol = "http";

    String serverName = parameters.getParameter("serverName");
    if (serverName == null)
      serverName = "localhost";

    String serverPort = parameters.getParameter("serverPort");
    if (serverPort == null)
      serverPort = "";

    String serverLocation = parameters.getParameter("serverLocation");
    if (serverLocation == null)
      serverLocation = "";
      
    String userName = parameters.getParameter("userName");
    if (userName == null)
      userName = "";

    String password = parameters.getObfuscatedParameter("password");
    if (password == null)
      password = "";
    else
      password = out.mapPasswordToKey(password);

    String keystore = parameters.getParameter("keystore");
    IKeystoreManager localKeystore;
    if (keystore == null)
      localKeystore = KeystoreManagerFactory.make("");
    else
      localKeystore = KeystoreManagerFactory.make("",keystore);

    // "Server" tab
    // Always send along the keystore.
    if (keystore != null)
    {
      out.print(
"<input type=\"hidden\" name=\"keystoredata\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(keystore)+"\"/>\n"
      );
    }

    if (tabName.equals(Messages.getString(locale,"SharePointRepository.Server")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.ServerSharePointVersion") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverVersion\">\n"+
"        <option value=\"2.0\" "+((serverVersion.equals("2.0"))?"selected=\"true\"":"")+">SharePoint Services 2.0 (2003)</option>\n"+
"        <option value=\"3.0\" "+(serverVersion.equals("3.0")?"selected=\"true\"":"")+">SharePoint Services 3.0 (2007)</option>\n"+
"        <option value=\"4.0\" "+(serverVersion.equals("4.0")?"selected=\"true\"":"")+">SharePoint Services 4.0 (2010)</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.ServerProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverProtocol\">\n"+
"        <option value=\"http\" "+((serverProtocol.equals("http"))?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(serverProtocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.ServerName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"serverName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.ServerPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"serverPort\" value=\""+serverPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.SitePath") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"serverLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverLocation)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.UserName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"userName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Password") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.SSLCertificateList") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\"configop\" value=\"Continue\"/>\n"+
"      <input type=\"hidden\" name=\"shpkeystorealias\" value=\"\"/>\n"+
"      <table class=\"displaytable\">\n"
      );
      // List the individual certificates in the store, with a delete button for each
      String[] contents = localKeystore.getContents();
      if (contents.length == 0)
      {
        out.print(
"        <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"SharePointRepository.NoCertificatesPresent") + "</td></tr>\n"
        );
      }
      else
      {
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          out.print(
"        <tr>\n"+
"          <td class=\"value\">\n"+
"            <input type=\"button\" onclick='Javascript:ShpDeleteCertificate(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteCert")+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(alias)+"\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\"/>\n"+
"          </td>\n"+
"          <td>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)+"</td>\n"+
"        </tr>\n"
          );

          i++;
        }
      }
      out.print(
"      </table>\n"+
"      <input type=\"button\" onclick='Javascript:ShpAddCertificate()' alt=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddCert") + "\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Add") + "\"/>&nbsp;\n"+
"      "+Messages.getBodyString(locale,"SharePointRepository.Certificate")+"&nbsp;<input name=\"shpcertificate\" size=\"50\" type=\"file\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"serverProtocol\" value=\""+serverProtocol+"\"/>\n"+
"<input type=\"hidden\" name=\"serverName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverName)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverPort\" value=\""+serverPort+"\"/>\n"+
"<input type=\"hidden\" name=\"serverLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverLocation)+"\"/>\n"+
"<input type=\"hidden\" name=\"userName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
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
    String serverVersion = variableContext.getParameter("serverVersion");
    if (serverVersion != null)
      parameters.setParameter("serverVersion",serverVersion);

    String serverProtocol = variableContext.getParameter("serverProtocol");
    if (serverProtocol != null)
      parameters.setParameter("serverProtocol",serverProtocol);

    String serverName = variableContext.getParameter("serverName");

    if (serverName != null)
      parameters.setParameter("serverName",serverName);

    String serverPort = variableContext.getParameter("serverPort");
    if (serverPort != null)
      parameters.setParameter("serverPort",serverPort);

    String serverLocation = variableContext.getParameter("serverLocation");
    if (serverLocation != null)
      parameters.setParameter("serverLocation",serverLocation);

    String userName = variableContext.getParameter("userName");
    if (userName != null)
      parameters.setParameter("userName",userName);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter("password",variableContext.mapKeyToPassword(password));

    String keystoreValue = variableContext.getParameter("keystoredata");
    if (keystoreValue != null)
      parameters.setParameter("keystore",keystoreValue);

    String configOp = variableContext.getParameter("configop");
    if (configOp != null)
    {
      if (configOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("shpkeystorealias");
        keystoreValue = parameters.getParameter("keystore");
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        parameters.setParameter("keystore",mgr.getString());
      }
      else if (configOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("shpcertificate");
        keystoreValue = parameters.getParameter("keystore");
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
        parameters.setParameter("keystore",mgr.getString());
      }
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
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Parameters") + "</nobr></td>\n"+
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
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" certificate(s)&gt;</nobr><br/>\n"
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
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Paths"));
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Security"));
    tabsArray.add(Messages.getString(locale,"SharePointRepository.Metadata"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkSpecification()\n"+
"{\n"+
"  // Does nothing right now.\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function SpecRuleAddPath(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectype.value==\"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectATypeFirst")+"\");\n"+
"    editjob.spectype.focus();\n"+
"  }\n"+
"  else if (editjob.specflavor.value==\"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectAnActionFirst")+"\");\n"+
"    editjob.specflavor.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"specop\",\"Add\",anchorvalue);\n"+
"}\n"+
"  \n"+
"function SpecPathReset(anchorvalue)\n"+
"{\n"+
"  SpecOp(\"specpathop\",\"Reset\",anchorvalue);\n"+
"}\n"+
"  \n"+
"function SpecPathAppendSite(anchorvalue)\n"+
"{\n"+
"  if (editjob.specsite.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectASiteFirst")+"\");\n"+
"    editjob.specsite.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"specpathop\",\"AppendSite\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecPathAppendLibrary(anchorvalue)\n"+
"{\n"+
"  if (editjob.speclibrary.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectALibraryFirst")+"\");\n"+
"    editjob.speclibrary.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"specpathop\",\"AppendLibrary\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecPathAppendList(anchorvalue)\n"+
"{\n"+
"  if (editjob.speclist.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectAListFirst")+"\");\n"+
"    editjob.speclist.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"specpathop\",\"AppendList\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecPathAppendText(anchorvalue)\n"+
"{\n"+
"  if (editjob.specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseProvideMatchTextFirst")+"\");\n"+
"    editjob.specmatch.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"specpathop\",\"AppendText\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecPathRemove(anchorvalue)\n"+
"{\n"+
"  SpecOp(\"specpathop\",\"Remove\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaRuleAddPath(anchorvalue)\n"+
"{\n"+
"  if (editjob.metaflavor.value==\"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectAnActionFirst")+"\");\n"+
"    editjob.metaflavor.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"metaop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaPathReset(anchorvalue)\n"+
"{\n"+
"  SpecOp(\"metapathop\",\"Reset\",anchorvalue);\n"+
"}\n"+
"  \n"+
"function MetaPathAppendSite(anchorvalue)\n"+
"{\n"+
"  if (editjob.metasite.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectASiteFirst")+"\");\n"+
"    editjob.metasite.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"metapathop\",\"AppendSite\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaPathAppendLibrary(anchorvalue)\n"+
"{\n"+
"  if (editjob.metalibrary.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectALibraryFirst")+"\");\n"+
"    editjob.metalibrary.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"metapathop\",\"AppendLibrary\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaPathAppendList(anchorvalue)\n"+
"{\n"+
"  if (editjob.metalist.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseSelectAListFirst")+"\");\n"+
"    editjob.metalist.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"metapathop\",\"AppendList\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaPathAppendText(anchorvalue)\n"+
"{\n"+
"  if (editjob.metamatch.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.PleaseProvideMatchTextFirst")+"\");\n"+
"    editjob.metamatch.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"metapathop\",\"AppendText\",anchorvalue);\n"+
"}\n"+
"\n"+
"function MetaPathRemove(anchorvalue)\n"+
"{\n"+
"  SpecOp(\"metapathop\",\"Remove\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddAccessToken(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.AccessTokenCannotBeNull")+"\");\n"+
"    editjob.spectoken.focus();\n"+
"  }\n"+
"  else\n"+
"    SpecOp(\"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddMapping(anchorvalue)\n"+
"{\n"+
"  if (editjob.specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.MatchStringCannotBeEmpty")+"\");\n"+
"    editjob.specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editjob.specmatch.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SharePointRepository.MatchStringMustBeValidRegularExpression")+"\");\n"+
"    editjob.specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"specmappingop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
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
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    int i;
    int k;
    int l;

    // Paths tab


    if (tabName.equals(Messages.getString(locale,"SharePointRepository.Paths")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathRules") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Type") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      l = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String site = sn.getAttributeValue("site");
          String lib = sn.getAttributeValue("lib");
          String siteLib = site + "/" + lib + "/";

          // Go through all the file/folder rules for the startpoint, and generate new "rules" corresponding to each.
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode node = sn.getChild(j++);
            if (node.getType().equals("include") || node.getType().equals("exclude"))
            {
              String matchPart = node.getAttributeValue("match");
              String ruleType = node.getAttributeValue("type");
              
              String theFlavor = node.getType();

              String pathDescription = "_"+Integer.toString(k);
              String pathOpName = "specop"+pathDescription;
              String thePath = siteLib + matchPart;
              out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.InsertNewRule") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")' alt=\""+"Insert new rule before rule #"+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"3\"></td>\n"+
"        </tr>\n"
              );
              l++;
              out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thePath)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thePath)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\"file\"/>\n"+
"              file\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+theFlavor+"\"/>\n"+
"              "+theFlavor+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
              );
              l++;
              k++;
              if (ruleType.equals("file") && !matchPart.startsWith("*"))
              {
                // Generate another rule corresponding to all matching paths.
                pathDescription = "_"+Integer.toString(k);
                pathOpName = "specop"+pathDescription;

                thePath = siteLib + "*/" + matchPart;
                out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.InsertNewRule") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.InsertNewRuleBeforeRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"3\"></td>\n"+
"        </tr>\n"
                );
                l++;
                out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thePath)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thePath)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\"file\"/>\n"+
"              file\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+theFlavor+"\"/>\n"+
"              "+theFlavor+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
                );
                l++;
                  
                k++;
              }
            }
          }
        }
        else if (sn.getType().equals("pathrule"))
        {
          String match = sn.getAttributeValue("match");
          String type = sn.getAttributeValue("type");
          String action = sn.getAttributeValue("action");
          
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "specop"+pathDescription;

          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.InsertNewRule") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.InsertNewRuleBeforeRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"3\"></td>\n"+
"        </tr>\n"
          );
          l++;
          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(match)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(match)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\""+type+"\"/>\n"+
"              "+type+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+action+"\"/>\n"+
"              "+action+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          l++;
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td colspan=\"4\" class=\"formmessage\">" + Messages.getBodyString(locale,"SharePointRepository.NoDocumentsCurrentlyIncluded") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\"specop\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\"specpathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddNewRule") + "\" onClick='Javascript:SpecRuleAddPath(\"path_"+Integer.toString(k)+"\")' alt=\"Add rule\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"3\"></td>\n"+
"        </tr>\n"+
"        <tr class=\"formrow\"><td colspan=\"4\" class=\"formseparator\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Messages.getBodyString(locale,"SharePointRepository.NewRule")+"</nobr>\n"
      );

      // The following variables may be in the thread context because postspec.jsp put them there:
      // (1) "specpath", which contains the rule path as it currently stands;
      // (2) "specpathstate", which describes what the current path represents.  Values are "unknown", "site", "library", "list".
      // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
      // specsitepath may be in the thread context, put there by postspec.jsp 
      String pathSoFar = (String)currentContext.get("specpath");
      String pathState = (String)currentContext.get("specpathstate");
      String pathLibrary = (String)currentContext.get("specpathlibrary");
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
            pathState = "unknown";
            pathLibrary = null;
          }
          childLibList = getDocLibsBySite(queryPath);
          if (childLibList == null)
          {
            // Illegal path - state becomes "unknown"
            pathState = "unknown";
            pathLibrary = null;
          }
          childListList = getListsBySite(queryPath);
          if (childListList == null)
          {
            // Illegal path - state becomes "unknown"
            pathState = "unknown";
            pathLibrary = null;
          }
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
      }
      out.print(
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\"specpathop\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\"specpath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"              <input type=\"hidden\" name=\"specpathstate\" value=\""+pathState+"\"/>\n"
      );
      if (pathLibrary != null)
      {
        out.print(
"              <input type=\"hidden\" name=\"specpathlibrary\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathLibrary)+"\"/>\n"
        );
      }
      out.print(
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathSoFar)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"
      );
      if (pathState.equals("unknown"))
      {
        if (pathLibrary == null)
        {
          out.print(
"              <select name=\"spectype\" size=\"4\">\n"+
"                <option value=\"file\" selected=\"true\">" + Messages.getBodyString(locale,"SharePointRepository.File") + "</option>\n"+
"                <option value=\"library\">" + Messages.getBodyString(locale,"SharePointRepository.Library") + "</option>\n"+
"                <option value=\"list\">" + Messages.getBodyString(locale,"SharePointRepository.List") + "</option>\n"+
"                <option value=\"site\">" + Messages.getBodyString(locale,"SharePointRepository.Site") + "</option>\n"+
"              </select>\n"
          );
        }
        else
        {
          out.print(
"              <input type=\"hidden\" name=\"spectype\" value=\"file\"/>\n"+
"              file\n"
          );
        }
      }
      else
      {
        out.print(
"              <input type=\"hidden\" name=\"spectype\" value=\""+pathState+"\"/>\n"+
"              "+pathState+"\n"
        );
      }
      out.print(
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <select name=\"specflavor\" size=\"2\">\n"+
"                <option value=\"include\" selected=\"true\">" + Messages.getBodyString(locale,"SharePointRepository.Include") + "</option>\n"+
"                <option value=\"exclude\">" + Messages.getBodyString(locale,"SharePointRepository.Exclude") + "</option>\n"+

"              </select>\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr class=\"formrow\"><td colspan=\"4\" class=\"formseparator\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"
      );
      if (message != null)
      {
        // Display the error message, with no widgets
        out.print(
"          <td class=\"formmessage\" colspan=\"4\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(message)+"</td>\n"
        );
      }
      else
      {
        // What we display depends on the determined state of the path.  If the path is a library or is unknown, all we can do is allow a type-in to append
        // to it, or allow a reset.  If the path is a site, then we can optionally display libraries, sites, lists, OR allow a type-in.
        // The path buttons are on the left; they consist of "Reset" (to reset the path), "+" (to add to the path), and "-" (to remove from the path).
        out.print(
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\"pathwidget\"/>\n"+
"              <input type=\"button\" value=\"Reset Path\" onClick='Javascript:SpecPathReset(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.ResetRulePath")+"\"/>\n"
        );
        if (pathSoFar.length() > 1 && (pathState.equals("site") || pathState.equals("library") || pathState.equals("list")))
        {
          out.print(
"              <input type=\"button\" value=\"-\" onClick='Javascript:SpecPathRemove(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.RemoveFromRulePath")+"\"/>\n"
          );
        }
        out.print(
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"3\">\n"+
"            <nobr>\n"
        );
        if (pathState.equals("site") && childSiteList != null && childSiteList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddSite") + "\" onClick='Javascript:SpecPathAppendSite(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddSiteToRulePath")+"\"/>\n"+
"              <select name=\"specsite\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">-- " + Messages.getBodyString(locale,"SharePointRepository.SelectSite") + " --</option>\n"
          );
          int q = 0;
          while (q < childSiteList.size())
          {
            NameValue childSite = childSiteList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childSite.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childSite.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }
        
        if (pathState.equals("site") && childLibList != null && childLibList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddLibrary") + "\" onClick='Javascript:SpecPathAppendLibrary(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddLibraryToRulePath")+"\"/>\n"+
"              <select name=\"speclibrary\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">-- " + Messages.getBodyString(locale,"SharePointRepository.SelectLibrary") + " --</option>\n"
          );
          int q = 0;
          while (q < childLibList.size())
          {
            NameValue childLib = childLibList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childLib.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childLib.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }

        if (pathState.equals("site") && childListList != null && childListList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddList") + "\" onClick='Javascript:SpecPathAppendList(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddListToRulePath")+"\"/>\n"+
"              <select name=\"speclist\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">-- " + Messages.getBodyString(locale,"SharePointRepository.SelectList") + " --</option>\n"
          );
          int q = 0;
          while (q < childListList.size())
          {
            NameValue childList = childListList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childList.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childList.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }
        
        // If it's a list name, we're done; no text allowed
        if (!pathState.equals("list"))
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddText") + "\" onClick='Javascript:SpecPathAppendText(\"pathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddTextToRulePath")+"\"/>\n"+
"              <input type=\"text\" name=\"specmatch\" size=\"32\" value=\"\"/>\n"
          );
        }
        
        out.print(
"            </nobr>\n"+
"          </td>\n"
        );
      }
      out.print(
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for path rules
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String site = sn.getAttributeValue("site");
          String lib = sn.getAttributeValue("lib");
          String siteLib = site + "/" + lib + "/";

          // Go through all the file/folder rules for the startpoint, and generate new "rules" corresponding to each.
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode node = sn.getChild(j++);
            if (node.getType().equals("include") || node.getType().equals("exclude"))
            {
              String matchPart = node.getAttributeValue("match");
              String ruleType = node.getAttributeValue("type");
              
              String theFlavor = node.getType();

              String pathDescription = "_"+Integer.toString(k);
              
              String thePath = siteLib + matchPart;
              out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thePath)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\"file\"/>\n"+
"<input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+theFlavor+"\"/>\n"
              );
              k++;

              if (ruleType.equals("file") && !matchPart.startsWith("*"))
              {
                // Generate another rule corresponding to all matching paths.
                pathDescription = "_"+Integer.toString(k);

                thePath = siteLib + "*/" + matchPart;
                out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thePath)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\"file\"/>\n"+
"<input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+theFlavor+"\"/>\n"
                );
                k++;
              }
            }
          }
        }
        else if (sn.getType().equals("pathrule"))
        {
          String match = sn.getAttributeValue("match");
          String type = sn.getAttributeValue("type");
          String action = sn.getAttributeValue("action");
          
          String pathDescription = "_"+Integer.toString(k);
          out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(match)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"spectype"+pathDescription+"\" value=\""+type+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specflav"+pathDescription+"\" value=\""+action+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"specpathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Security tab

    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }

    if (tabName.equals(Messages.getString(locale,"SharePointRepository.Security")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Security2") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <nobr>\n"+
"        <input type=\"radio\" name=\"specsecurity\" value=\"on\" "+(securityOn?"checked=\"true\"":"")+" />"+Messages.getBodyString(locale,"SharePointRepository.Enabled")+"&nbsp;\n"+
"        <input type=\"radio\" name=\"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />"+Messages.getBodyString(locale,"SharePointRepository.Disabled")+"\n"+
"      </nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = "accessop"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteToken")+Integer.toString(k)+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"</nobr>\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"SharePointRepository.NoAccessTokensPresent") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\"accessop\" value=\"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Add") + "\" onClick='Javascript:SpecAddAccessToken(\"token_"+Integer.toString(k+1)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddAccessToken")+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specsecurity\" value=\""+(securityOn?"on":"off")+"\"/>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Metadata tab

    // Find the path-value metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.sharepoint.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.sharepoint.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }

    if (tabName.equals(Messages.getString(locale,"SharePointRepository.Metadata")))
    {
      out.print(
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\"specmappingop\" value=\"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"<tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"<tr>\n"+
"  <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.MetadataRules") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.AllMetadata") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Fields") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      l = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String site = sn.getAttributeValue("site");
          String lib = sn.getAttributeValue("lib");
          String path = site + "/" + lib + "/*";
          String allmetadata = sn.getAttributeValue("allmetadata");
          StringBuilder metadataFieldList = new StringBuilder();
          ArrayList metadataFieldArray = new ArrayList();
          if (allmetadata == null || !allmetadata.equals("true"))
          {
            int j = 0;
            while (j < sn.getChildCount())
            {
              SpecificationNode node = sn.getChild(j++);
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
            String pathDescription = "_"+Integer.toString(k);
            String pathOpName = "metaop"+pathDescription;
            out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"meta_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.InsertNewRule") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Insert Here\",\"meta_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.InsertNewMetadataRuleBeforeRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"4\"></td>\n"+
"        </tr>\n"
            );
            l++;
            out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"meta_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteMetadataRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metapath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metaflav"+pathDescription+"\" value=\"include\"/>\n"+
"              "+Messages.getBodyString(locale,"SharePointRepository.include")+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metaall"+pathDescription+"\" value=\""+allmetadata+"\"/>\n"+
"              "+allmetadata+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"
            );
            int q = 0;
            while (q < metadataFieldArray.size())
            {
              String field = (String)metadataFieldArray.get(q++);
              out.print(
"              <input type=\"hidden\" name=\""+"metafields"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\"/>\n"
              );
            }
            out.print(
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(metadataFieldList.toString())+"\n"+
"          </td>\n"+
"        </tr>\n"
            );
            l++;
            k++;
          }
        }
        else if (sn.getType().equals("metadatarule"))
        {
          String path = sn.getAttributeValue("match");
          String action = sn.getAttributeValue("action");
          String allmetadata = sn.getAttributeValue("allmetadata");
          StringBuilder metadataFieldList = new StringBuilder();
          ArrayList metadataFieldArray = new ArrayList();
          if (action.equals("include"))
          {
            if (allmetadata == null || !allmetadata.equals("true"))
            {
              int j = 0;
              while (j < sn.getChildCount())
              {
                SpecificationNode node = sn.getChild(j++);
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
          
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "metaop"+pathDescription;
          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"meta_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.InsertNewRule") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Insert Here\",\"meta_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.InsertNewMetadataRuleBeforeRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"4\"></td>\n"+
"        </tr>\n"
          );
          l++;
          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"meta_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteMetadataRule")+Integer.toString(k)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metapath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metaflav"+pathDescription+"\" value=\""+action+"\"/>\n"+
"              "+action+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"metaall"+pathDescription+"\" value=\""+allmetadata+"\"/>\n"+
"              "+allmetadata+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"
          );
          int q = 0;
          while (q < metadataFieldArray.size())
          {
            String field = (String)metadataFieldArray.get(q++);
            out.print(
"              <input type=\"hidden\" name=\""+"metafields"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\"/>\n"
            );
          }
          out.print(
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(metadataFieldList.toString())+"\n"+
"          </td>\n"+
"        </tr>\n"
          );
          l++;
          k++;

        }
      }

      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">"+Messages.getBodyString(locale,"SharePointRepository.NoMetadataIncluded")+"</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"meta_"+Integer.toString(k)+"\"/>\n"+
"              <input type=\"hidden\" name=\"metaop\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\"metapathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddNewRule") + "\" onClick='Javascript:MetaRuleAddPath(\"meta_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddRule")+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"4\"></td>\n"+
"        </tr>\n"+
"        <tr class=\"formrow\"><td colspan=\"5\" class=\"formseparator\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Messages.getBodyString(locale,"SharePointRepository.NewRule")+"</nobr>\n"
      );
      // The following variables may be in the thread context because postspec.jsp put them there:
      // (1) "metapath", which contains the rule path as it currently stands;
      // (2) "metapathstate", which describes what the current path represents.  Values are "unknown", "site", "library".
      // (3) "metapathlibrary" is the library or list path (if this is known yet).
      // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
      String metaPathSoFar = (String)currentContext.get("metapath");
      String metaPathState = (String)currentContext.get("metapathstate");
      String metaPathLibrary = (String)currentContext.get("metapathlibrary");
      if (metaPathState == null)
        metaPathState = "unknown";
      if (metaPathSoFar == null)
      {
        metaPathSoFar = "/";
        metaPathState = "site";
      }

      String message = null;
      String[] fields = null;
      if (metaPathLibrary != null)
      {
        // Look up metadata fields
        int index = metaPathLibrary.lastIndexOf("/");
        String site = metaPathLibrary.substring(0,index);
        String libOrList = metaPathLibrary.substring(index+1);
        Map metaFieldList = null;
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
          fields = new String[metaFieldList.size()];
          int j = 0;
          Iterator iter = metaFieldList.keySet().iterator();
          while (iter.hasNext())
          {
            fields[j++] = (String)iter.next();
          }
          java.util.Arrays.sort(fields);
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
            // Illegal path - state becomes "unknown".
            metaPathState = "unknown";
            metaPathLibrary = null;
          }
          childLibList = getDocLibsBySite(queryPath);
          if (childLibList == null)
          {
            // Illegal path - state becomes "unknown"
            metaPathState = "unknown";
            metaPathLibrary = null;
          }
          childListList = getListsBySite(queryPath);
          if (childListList == null)
          {
            // Illegal path - state becomes "unknown"
            metaPathState = "unknown";
            metaPathLibrary = null;
          }
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
      }
      out.print(
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\"metapathop\" value=\"\"/>\n"+
"              <input type=\"hidden\" name=\"metapath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(metaPathSoFar)+"\"/>\n"+
"              <input type=\"hidden\" name=\"metapathstate\" value=\""+metaPathState+"\"/>\n"
      );
      if (metaPathLibrary != null)
      {
        out.print(
"              <input type=\"hidden\" name=\"metapathlibrary\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(metaPathLibrary)+"\"/>\n"
        );
      }
      out.print(
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(metaPathSoFar)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <select name=\"metaflavor\" size=\"2\">\n"+
"                <option value=\"include\" selected=\"true\">" + Messages.getBodyString(locale,"SharePointRepository.Include") + "</option>\n"+
"                <option value=\"exclude\">" + Messages.getBodyString(locale,"SharePointRepository.Exclude") + "</option>\n"+
"              </select>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"checkbox\" name=\"metaall\" value=\"true\"/>"+Messages.getBodyString(locale,"SharePointRepository.IncludeAllMetadata")+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"
      );
      if (fields != null && fields.length > 0)
      {
        out.print(
"              <select name=\"metafields\" multiple=\"true\" size=\"5\">\n"
        );
        int q = 0;
        while (q < fields.length)
        {
          String field = fields[q++];
          out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(field)+"</option>\n"
          );
        }
        out.print(
"              </select>\n"
        );
      }
      out.print(
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr class=\"formrow\"><td colspan=\"5\" class=\"formseparator\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"
      );
      if (message != null)
      {
        out.print(
"          <td class=\"formmessage\" colspan=\"5\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(message)+"</td></tr>\n"
        );
      }
      else
      {
        // What we display depends on the determined state of the path.  If the path is a library or is unknown, all we can do is allow a type-in to append
        // to it, or allow a reset.  If the path is a site, then we can optionally display libraries, sites, OR allow a type-in.
        // The path buttons are on the left; they consist of "Reset" (to reset the path), "+" (to add to the path), and "-" (to remove from the path).
        out.print(
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\"metapathwidget\"/>\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.ResetPath") + "\" onClick='Javascript:MetaPathReset(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.ResetMetadataRulePath")+"\"/>\n"
        );
        if (metaPathSoFar.length() > 1 && (metaPathState.equals("site") || metaPathState.equals("library")))
        {
          out.print(
"              <input type=\"button\" value=\"-\" onClick='Javascript:MetaPathRemove(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.RemoveFromMetadataRulePath")+"\"/>\n"
          );
        }
        out.print(
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\" colspan=\"4\">\n"+
"            <nobr>\n"
        );
        if (metaPathState.equals("site") && childSiteList != null && childSiteList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddSite") + "\" onClick='Javascript:MetaPathAppendSite(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddSiteToMetadataRulePath")+"\"/>\n"+
"              <select name=\"metasite\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">"+Messages.getBodyString(locale,"SharePointRepository.SelectSite")+"</option>\n"
          );
          int q = 0;
          while (q < childSiteList.size())
          {
            NameValue childSite = childSiteList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childSite.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childSite.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }
        
        if (metaPathState.equals("site") && childLibList != null && childLibList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddLibrary") + "\" onClick='Javascript:MetaPathAppendLibrary(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddLibraryToMetadataRulePath")+"\"/>\n"+
"              <select name=\"metalibrary\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">"+Messages.getBodyString(locale,"SharePointRepository.SelectLibrary")+"</option>\n"
          );
          int q = 0;
          while (q < childLibList.size())
          {
            NameValue childLib = childLibList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childLib.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childLib.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }
        
        if (metaPathState.equals("site") && childListList != null && childListList.size() > 0)
        {
          out.print(
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddList") + "\" onClick='Javascript:MetaPathAppendList(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddListToMetadataRulePath")+"\"/>\n"+
"              <select name=\"metalist\" size=\"5\">\n"+
"                <option value=\"\" selected=\"true\">"+Messages.getBodyString(locale,"SharePointRepository.SelectList")+"</option>\n"
          );
          int q = 0;
          while (q < childListList.size())
          {
            NameValue childList = childListList.get(q++);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childList.getValue())+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childList.getPrettyName())+"</option>\n"
            );
          }
          out.print(
"              </select>\n"
          );
        }

        if (!metaPathState.equals("list"))
        {
          out.print(
  "              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SharePointRepository.AddText") + "\" onClick='Javascript:MetaPathAppendText(\"metapathwidget\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.AddTextToMetadataRulePath")+"\"/>\n"+
  "              <input type=\"text\" name=\"metamatch\" size=\"32\" value=\"\"/>\n"
          );
        }
        
        out.print(
"            </nobr>\n"+
"          </td>\n"
        );
      }
      out.print(
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMetadata") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.AttributeName") + "</nobr></td>\n"+
"          <td class=\"value\" colspan=\"3\">\n"+
"            <nobr>\n"+
"              <input type=\"text\" name=\"specpathnameattribute\" size=\"20\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <input type=\"hidden\" name=\""+"specmappingop_"+Integer.toString(i)+"\" value=\"\"/>\n"+
"            <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"              <input type=\"button\" onClick='Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"Delete\",\"mapping_"+Integer.toString(i)+"\")' alt=\""+Messages.getAttributeString(locale,"SharePointRepository.DeleteMapping")+Integer.toString(i)+"\" value=\""+Messages.getAttributeString(locale,"SharePointRepository.DeletePathMapping")+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">==></td>\n"+
"          <td class=\"value\">\n"+
"            <nobr>\n"+
"              <input type=\"hidden\" name=\""+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"\n"+
"            </nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        i++;
      }
      if (i == 0)
      {
        out.print(
"        <tr><td colspan=\"4\" class=\"message\">" + Messages.getBodyString(locale,"SharePointRepository.NoMappingsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"              <input type=\"button\" onClick='Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")' alt=\"Add to mappings\" value=\"Add Path Mapping\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"value\"><nobr>"+Messages.getBodyString(locale,"SharePointRepository.MatchRegexp") + "&nbsp;<input type=\"text\" name=\"specmatch\" size=\"32\" value=\"\"/></nobr></td>\n"+
"          <td class=\"value\">==></td>\n"+
"          <td class=\"value\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.ReplaceString") + "&nbsp;<input type=\"text\" name=\"specreplace\" size=\"32\" value=\"\"/></nobr></td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for metadata rules
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String site = sn.getAttributeValue("site");
          String lib = sn.getAttributeValue("lib");
          String path = site + "/" + lib + "/*";
          
          String allmetadata = sn.getAttributeValue("allmetadata");
          ArrayList metadataFieldArray = new ArrayList();
          if (allmetadata == null || !allmetadata.equals("true"))
          {
            int j = 0;
            while (j < sn.getChildCount())
            {
              SpecificationNode node = sn.getChild(j++);
              if (node.getType().equals("metafield"))
              {
                String val = node.getAttributeValue("value");
                metadataFieldArray.add(val);
              }
            }
            allmetadata = "false";
          }
          
          if (allmetadata.equals("true") || metadataFieldArray.size() > 0)
          {
            String pathDescription = "_"+Integer.toString(k);
            out.print(
"<input type=\"hidden\" name=\""+"metapath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"metaflav"+pathDescription+"\" value=\"include\"/>\n"+
"<input type=\"hidden\" name=\""+"metaall"+pathDescription+"\" value=\""+allmetadata+"\"/>\n"
            );
            int q = 0;
            while (q < metadataFieldArray.size())
            {
              String field = (String)metadataFieldArray.get(q++);
              out.print(
"<input type=\"hidden\" name=\""+"metafields"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\"/>\n"
              );
            }
            k++;
          }
        }
        else if (sn.getType().equals("metadatarule"))
        {
          String match = sn.getAttributeValue("match");
          String action = sn.getAttributeValue("action");
          String allmetadata = sn.getAttributeValue("allmetadata");
          
          String pathDescription = "_"+Integer.toString(k);
          out.print(
"<input type=\"hidden\" name=\""+"metapath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(match)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"metaflav"+pathDescription+"\" value=\""+action+"\"/>\n"
          );
          if (action.equals("include"))
          {
            if (allmetadata == null || allmetadata.length() == 0)
              allmetadata = "false";
            out.print(
"<input type=\"hidden\" name=\""+"metaall"+pathDescription+"\" value=\""+allmetadata+"\"/>\n"
            );
            int j = 0;
            while (j < sn.getChildCount())
            {
              SpecificationNode node = sn.getChild(j++);
              if (node.getType().equals("metafield"))
              {
                String value = node.getAttributeValue("value");
                out.print(
"<input type=\"hidden\" name=\""+"metafields"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value)+"\"/>\n"
                );
              }
            }
          }
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"metapathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"\n"+
"<input type=\"hidden\" name=\"specpathnameattribute\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"<input type=\"hidden\" name=\""+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"
        );
        i++;
      }
    }
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    // Remove old-style rules, but only if the information would not be lost
    if (variableContext.getParameter("specpathcount") != null && variableContext.getParameter("metapathcount") != null)
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
    
    String x = variableContext.getParameter("specpathcount");
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
        String pathOpName = "specop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        
        // Get the stored information for this rule.
        String path = variableContext.getParameter("specpath"+pathDescription);
        String type = variableContext.getParameter("spectype"+pathDescription);
        String action = variableContext.getParameter("specflav"+pathDescription);
        
        SpecificationNode node = new SpecificationNode("pathrule");
        node.setAttribute("match",path);
        node.setAttribute("action",action);
        node.setAttribute("type",type);
        
        // If there was an insert operation, do it now
        if (x != null && x.equals("Insert Here"))
        {
          // The global parameters are what are used to create the rule
          path = variableContext.getParameter("specpath");
          type = variableContext.getParameter("spectype");
          action = variableContext.getParameter("specflavor");
          
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
      String op = variableContext.getParameter("specop");
      if (op != null)
      {
        if (op.equals("Add"))
        {
          String match = variableContext.getParameter("specpath");
          String action = variableContext.getParameter("specflavor");
          String type = variableContext.getParameter("spectype");
          SpecificationNode node = new SpecificationNode("pathrule");
          node.setAttribute("match",match);
          node.setAttribute("action",action);
          node.setAttribute("type",type);
          ds.addChild(ds.getChildCount(),node);
        }
      }

      // See if there's a global pathbuilder operation
      String pathop = variableContext.getParameter("specpathop");
      if (pathop != null)
      {
        if (pathop.equals("Reset"))
        {
          currentContext.save("specpath","/");
          currentContext.save("specpathstate","site");
          currentContext.save("specpathlibrary",null);
        }
        else if (pathop.equals("AppendSite"))
        {
          String path = variableContext.getParameter("specpath");
          String addon = variableContext.getParameter("specsite");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
          }
          currentContext.save("specpath",path);
          currentContext.save("specpathstate","site");
          currentContext.save("specpathlibrary",null);
        }
        else if (pathop.equals("AppendLibrary"))
        {
          String path = variableContext.getParameter("specpath");
          String addon = variableContext.getParameter("speclibrary");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save("specpathstate","library");
            currentContext.save("specpathlibrary",path);
          }
          currentContext.save("specpath",path);
        }
        else if (pathop.equals("AppendList"))
        {
          String path = variableContext.getParameter("specpath");
          String addon = variableContext.getParameter("speclist");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save("specpathstate","list");
            currentContext.save("specpathlibrary",path);
          }
          currentContext.save("specpath",path);
        }
        else if (pathop.equals("AppendText"))
        {
          String path = variableContext.getParameter("specpath");
          String library = variableContext.getParameter("specpathlibrary");
          String addon = variableContext.getParameter("specmatch");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save("specpathstate","unknown");
          }
          currentContext.save("specpath",path);
          currentContext.save("specpathlibrary",library);
        }
        else if (pathop.equals("Remove"))
        {
          // Strip off end
          String path = variableContext.getParameter("specpath");
          int index = path.lastIndexOf("/");
          path = path.substring(0,index);
          if (path.length() == 0)
            path = "/";
          currentContext.save("specpath",path);
          // Now, adjust state.
          String pathState = variableContext.getParameter("specpathstate");
          if (pathState.equals("library") || pathState.equals("list"))
            pathState = "site";
          currentContext.save("specpathstate",pathState);
        }
      }

    }
    
    x = variableContext.getParameter("metapathcount");
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
        String pathOpName = "metaop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }

        // Get the stored information for this rule.
        String path = variableContext.getParameter("metapath"+pathDescription);
        String action = variableContext.getParameter("metaflav"+pathDescription);
        String allmetadata =  variableContext.getParameter("metaall"+pathDescription);
        String[] metadataFields = variableContext.getParameterValues("metafields"+pathDescription);
        
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
          path = variableContext.getParameter("metapath");
          action = variableContext.getParameter("metaflavor");
          allmetadata =  variableContext.getParameter("metaall");
          metadataFields = variableContext.getParameterValues("metafields");
        
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
      String op = variableContext.getParameter("metaop");
      if (op != null)
      {
        if (op.equals("Add"))
        {
          String match = variableContext.getParameter("metapath");
          String action = variableContext.getParameter("metaflavor");
          SpecificationNode node = new SpecificationNode("metadatarule");
          node.setAttribute("match",match);
          node.setAttribute("action",action);
          if (action.equals("include"))
          {
            String allmetadata = variableContext.getParameter("metaall");
            String[] metadataFields = variableContext.getParameterValues("metafields");
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
      String pathop = variableContext.getParameter("metapathop");
      if (pathop != null)
      {
        if (pathop.equals("Reset"))
        {
          currentContext.save("metapath","/");
          currentContext.save("metapathstate","site");
          currentContext.save("metapathlibrary",null);
        }
        else if (pathop.equals("AppendSite"))
        {
          String path = variableContext.getParameter("metapath");
          String addon = variableContext.getParameter("metasite");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
          }
          currentContext.save("metapath",path);
          currentContext.save("metapathstate","site");
          currentContext.save("metapathlibrary",null);
        }
        else if (pathop.equals("AppendLibrary"))
        {
          String path = variableContext.getParameter("metapath");
          String addon = variableContext.getParameter("metalibrary");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save("metapathstate","library");
            currentContext.save("metapathlibrary",path);
          }
          currentContext.save("metapath",path);
        }
        else if (pathop.equals("AppendList"))
        {
          String path = variableContext.getParameter("metapath");
          String addon = variableContext.getParameter("metalist");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            currentContext.save("metapathstate","list");
            currentContext.save("metapathlibrary",path);
            // Automatically add on wildcard for list item part of the match
            path += "/*";
          }
          currentContext.save("metapath",path);
        }
        else if (pathop.equals("AppendText"))
        {
          String path = variableContext.getParameter("metapath");
          String library = variableContext.getParameter("metapathlibrary");
          String addon = variableContext.getParameter("metamatch");
          if (addon != null && addon.length() > 0)
          {
            if (path.equals("/"))
              path = path + addon;
            else
              path = path + "/" + addon;
            if (library != null)
              currentContext.save("metapathstate","file");
            else
              currentContext.save("metapathstate","unknown");
          }
          currentContext.save("metapath",path);
          currentContext.save("metapathlibrary",library);
        }
        else if (pathop.equals("Remove"))
        {
          String pathState = variableContext.getParameter("metapathstate");
          String path;
          if (pathState.equals("file"))
          {
            pathState = "library";
            path = variableContext.getParameter("metapathlibrary");
          }
          else if (pathState.equals("list") || pathState.equals("library"))
          {
            pathState = "site";
            path = variableContext.getParameter("metapathlibrary");
            int index = path.lastIndexOf("/");
            path = path.substring(0,index);
            if (path.length() == 0)
              path = "/";
            currentContext.save("metapathlibrary",null);
          }
          else
          {
            path = variableContext.getParameter("metapath");
            int index = path.lastIndexOf("/");
            path = path.substring(0,index);
            if (path.length() == 0)
              path = "/";
          }

          currentContext.save("metapathstate",pathState);
          currentContext.save("metapath",path);
        }
      }

      
    }

    String xc = variableContext.getParameter("specsecurity");
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
   
    xc = variableContext.getParameter("tokencount");
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
        String accessOpName = "accessop"+accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter("spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter("spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter("specpathnameattribute");
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

    xc = variableContext.getParameter("specmappingcount");
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
        String pathOpName = "specmappingop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Inserts won't happen until the very end
        String match = variableContext.getParameter("specmatch"+pathDescription);
        String replace = variableContext.getParameter("specreplace"+pathDescription);
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);

        i++;
      }

      // Check for add
      xc = variableContext.getParameter("specmappingop");
      if (xc != null && xc.equals("Add"))
      {
        String match = variableContext.getParameter("specmatch");
        String replace = variableContext.getParameter("specreplace");
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    // Display path rules
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    int i = 0;
    int l = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("startpoint"))
      {
        String site = sn.getAttributeValue("site");
        String lib = sn.getAttributeValue("lib");
        String siteLib = site + "/" + lib + "/";

        // Old-style path.
        // There will be an inclusion or exclusion rule for every entry in the path rules for this startpoint, so loop through them.
        int j = 0;
        while (j < sn.getChildCount())
        {
          SpecificationNode node = sn.getChild(j++);
          if (node.getType().equals("include") || node.getType().equals("exclude"))
          {
            String matchPart = node.getAttributeValue("match");
            String ruleType = node.getAttributeValue("type");
            // Whatever happens, we're gonna display a rule here, so go ahead and set that up.
            if (seenAny == false)
            {
              seenAny = true;
              out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathRules") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.RuleType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"        </tr>\n"
              );
            }
            String action = node.getType();
            // Display the path rule corresponding to this match rule
            // The first part comes from the site/library
            String completePath;
            // The match applies to only the file portion.  Therefore, there are TWO rules needed to emulate: sitelib/<match>, and sitelib/*/<match>
            completePath = siteLib + matchPart;
            out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(completePath)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.file") + "</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+action+"</nobr></td>\n"+
"        </tr>\n"
            );
            l++;
            if (ruleType.equals("file") && !matchPart.startsWith("*"))
            {
              completePath = siteLib + "*/" + matchPart;
              out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(completePath)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.file") + "</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+action+"</nobr></td>\n"+
"        </tr>\n"
              );
              l++;
            }
          }
        }
      }
      else if (sn.getType().equals("pathrule"))
      {
        String path = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String ruleType = sn.getAttributeValue("type");
        if (seenAny == false)
        {
          seenAny = true;
          out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathRules") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.RuleType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"        </tr>\n"
          );
        }
        out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+ruleType+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+action+"</nobr></td>\n"+
"        </tr>\n"
        );
        l++;
      }
    }
    if (seenAny)
    {
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td colspan=\"2\" class=\"message\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.NoDocumentsWillBeIncluded") + "</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"
    );
  
    // Finally, display metadata rules
    out.print(
"  <tr>\n"
    );

    i = 0;
    l = 0;
    seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("startpoint"))
      {
        // Old-style
        String site = sn.getAttributeValue("site");
        String lib = sn.getAttributeValue("lib");
        String path = site + "/" + lib + "/*";
        
        String allmetadata = sn.getAttributeValue("allmetadata");
        StringBuilder metadataFieldList = new StringBuilder();
        if (allmetadata == null || !allmetadata.equals("true"))
        {
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode node = sn.getChild(j++);
            if (node.getType().equals("metafield"))
            {
              String value = node.getAttributeValue("value");
              if (metadataFieldList.length() > 0)
                metadataFieldList.append(", ");
              metadataFieldList.append(value);
            }
          }
          allmetadata = "false";
        }
        if (allmetadata.equals("true") || metadataFieldList.length() > 0)
        {
          if (seenAny == false)
          {
            seenAny = true;
            out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Metadata2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.AllMetadata") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Fields") + "</nobr></td>\n"+
"        </tr>\n"
            );
          }
          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.include2") + "</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allmetadata+"</nobr></td>\n"+
"          <td class=\"formcolumncell\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(metadataFieldList.toString())+"</td>\n"+
"        </tr>\n"
          );
          l++;
        }
      }
      else if (sn.getType().equals("metadatarule"))
      {
        String path = sn.getAttributeValue("match");
        String action = sn.getAttributeValue("action");
        String allmetadata = sn.getAttributeValue("allmetadata");
        StringBuilder metadataFieldList = new StringBuilder();
        if (action.equals("include"))
        {
          if (allmetadata == null || !allmetadata.equals("true"))
          {
            int j = 0;
            while (j < sn.getChildCount())
            {
              SpecificationNode node = sn.getChild(j++);
              if (node.getType().equals("metafield"))
              {
                String fieldName = node.getAttributeValue("value");
                if (metadataFieldList.length() > 0)
                  metadataFieldList.append(", ");
                metadataFieldList.append(fieldName);
              }
            }
            allmetadata = "false";
          }
        }
        else
          allmetadata = "";
        if (seenAny == false)
        {
          seenAny = true;
          out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Metadata2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMatch") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Action") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.AllMetadata") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Fields") + "</nobr></td>\n"+
"        </tr>\n"
          );
        }
        out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+action+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allmetadata+"</nobr></td>\n"+
"          <td class=\"formcolumncell\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(metadataFieldList.toString())+"</td>\n"+
"        </tr>\n"
        );
        l++;
      }
    }
    if (seenAny)
    {
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td colspan=\"2\" class=\"message\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.NoMetadataWillBeIncluded") + "</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }
    out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.Security2") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+(securityOn?Messages.getBodyString(locale,"SharePointRepository.Enabled2"):Messages.getBodyString(locale,"SharePointRepository.Disabled"))+"</nobr></td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr><td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.AccessToken") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"</nobr><br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"SharePointRepository.NoAccessTokensSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find the path-name metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }
    out.print(
"  <tr>\n"
    );
    if (pathNameAttribute.length() > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathMetadataAttributeName") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathNameAttribute)+"</nobr></td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"SharePointRepository.NoPathNameMetadataAttributeSpecified") + "</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"+
"  <tr>\n"+
"\n"
    );
    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.sharepoint.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.sharepoint.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }
    if (matchMap.getMatchCount() > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SharePointRepository.PathValueMapping") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"        <tr>\n"+
"          <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</nobr></td>\n"+
"          <td class=\"value\">==></td>\n"+
"          <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</nobr></td>\n"+
"        </tr>\n"
        );
        i++;
      }
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"SharePointRepository.NoMappingsSpecified") + "</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"</table>\n"
    );
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
  public Map getLibFieldList( String parentSite, String docLibrary )
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
  public Map getListFieldList( String parentSite, String listName )
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
  protected boolean checkIncludeLibrary( String libraryPath, DocumentSpecification documentSpecification )
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
  protected boolean checkIncludeList( String listPath, DocumentSpecification documentSpecification )
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
  protected boolean checkIncludeSite( String sitePath, DocumentSpecification documentSpecification )
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
  protected MetadataInformation getMetadataSpecification( String filePath, DocumentSpecification documentSpecification )
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
  protected boolean checkIncludeFile( String filePath, DocumentSpecification documentSpecification )
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
  protected boolean checkIncludeListItemAttachment( String attachmentPath, DocumentSpecification documentSpecification )
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
  protected boolean checkIncludeListItem( String itemPath, DocumentSpecification documentSpecification )
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
  protected static String[] getAcls(DocumentSpecification spec)
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
    try
    {
      return java.net.URLDecoder.decode(pathItem.replaceAll("\\%20","+"),"utf-8");
    }
    catch (UnsupportedEncodingException e)
    {
      // Bad news, utf-8 not available!
      throw new RuntimeException("No utf-8 encoding available");
    }
  }

  /** Encode a path item.
  */
  public static String pathItemEncode(String pathItem)
  {
    try
    {
      String output = java.net.URLEncoder.encode(pathItem,"utf-8");
      return output.replaceAll("\\+","%20");
    }
    catch (UnsupportedEncodingException e)
    {
      // Bad news, utf-8 not available!
      throw new RuntimeException("No utf-8 encoding available");
    }
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
    protected HashMap metadataFields = new HashMap();

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
      metadataFields.put(fieldName,fieldName);
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
      Iterator iter = metadataFields.keySet().iterator();
      int i = 0;
      while (iter.hasNext())
      {
        rval[i++] = (String)iter.next();
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
    protected String pathAttributeName;

    // The path name map
    protected MatchMap matchMap = new MatchMap();

    /** Constructor */
    public SystemMetadataDescription(DocumentSpecification spec)
      throws ManifoldCFException
    {
      pathAttributeName = null;
      int i = 0;
      while (i < spec.getChildCount())
      {
        SpecificationNode n = spec.getChild(i++);
        if (n.getType().equals("pathnameattribute"))
          pathAttributeName = n.getAttributeValue("value");
        else if (n.getType().equals("pathmap"))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
      }
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
