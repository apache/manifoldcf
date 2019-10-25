/* $Id: CswsConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.csws;

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

import javax.xml.datatype.XMLGregorianCalendar;
import com.opentext.livelink.service.core.PageHandle;
import com.opentext.livelink.service.core.DataValue;
import com.opentext.livelink.service.core.StringValue;
import com.opentext.livelink.service.core.RealValue;
import com.opentext.livelink.service.core.BooleanValue;
import com.opentext.livelink.service.core.DateValue;
import com.opentext.livelink.service.core.IntegerValue;
import com.opentext.livelink.service.docman.AttributeGroup;
import com.opentext.livelink.service.docman.AttributeGroupDefinition;
import com.opentext.livelink.service.docman.Attribute;
import com.opentext.livelink.service.docman.Metadata;
import com.opentext.livelink.service.docman.CategoryInheritance;
import com.opentext.livelink.service.docman.GetNodesInContainerOptions;
import com.opentext.livelink.service.docman.Node;
import com.opentext.livelink.service.docman.NodePermissions;
import com.opentext.livelink.service.docman.Version;
import com.opentext.livelink.service.docman.NodeRights;
import com.opentext.livelink.service.docman.NodeRight;
import com.opentext.livelink.service.memberservice.User;
import com.opentext.livelink.service.memberservice.Member;
import com.opentext.livelink.service.searchservices.SGraph;
import com.opentext.livelink.service.searchservices.SNode;

import org.apache.manifoldcf.csws.CswsParameters;
import org.apache.manifoldcf.csws.CswsSession;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

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


/** This is the Csws implementation of the IRepositoryConnector interface.
*/
public class CswsConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: CswsConnector.java 996524 2010-09-13 13:38:01Z kwright $";

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
  // <D|F><object_id>
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

  private static final String enterpriseWSName = "EnterpriseWS";
  private static final String categoryWSName = "CategoriesWS";

  // Signal that we have set up connection parameters properly
  private boolean hasSessionParameters = false;
  // Signal that we have set up a connection properly
  private boolean hasConnected = false;
  // Session expiration time
  private long expirationTime = -1L;
  // Idle session expiration interval
  private final static long expirationInterval = 300000L;

  // Data required for maintaining csws connection
  private CswsSession cswsSession = null;

  // Workspace Nodes (computed once and cached); should contain both enterprise and category workspaces
  private Map<String, Node> workspaceNodes = new HashMap<>();
  private Long enterpriseWSID = null;
  private Long categoryWSID = null;

  // Parameter values we need
  private String serverProtocol = null;
  private String serverName = null;
  private int serverPort = -1;
  private String serverUsername = null;
  private String serverPassword = null;
  private String authenticationServicePath = null;
  private String documentManagementServicePath = null;
  private String contentServiceServicePath = null;
  private String memberServiceServicePath = null;
  private String searchServiceServicePath = null;
  private String dataCollection = null;
  private String serverHTTPNTLMDomain = null;
  private String serverHTTPNTLMUsername = null;
  private String serverHTTPNTLMPassword = null;
  private IKeystoreManager serverHTTPSKeystore = null;

  private String viewProtocol = null;
  private String viewServerName = null;
  private String viewPort = null;
  private String viewCgiPath = null;
  private String viewAction = null;

  // Connection management
  private HttpClientConnectionManager connectionManager = null;
  private HttpClient httpClient = null;

  // Base path for viewing
  private String viewBasePath = null;

  // Activities list
  private static final String[] activitiesList = new String[]{ACTIVITY_SEED,ACTIVITY_FETCH};

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
  public CswsConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    // Csws is a chained hierarchy model
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
    serverName = params.getParameter(CswsParameters.serverName);
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
        // Get all the root workspaces
        final List<? extends String> workspaceNames = cswsSession.getRootNodeTypes();
        // Loop over these and get the nodes (which we'll save for later)
        workspaceNodes.clear();
        for (final String workspaceName : workspaceNames) {
          workspaceNodes.put(workspaceName, cswsSession.getRootNode(workspaceName));
        }

        if (enterpriseWSName == null || categoryWSName == null) {
          throw new ManifoldCFException("Could not locate either enterprise or category workspaces");
        }

        enterpriseWSID = workspaceNodes.get(enterpriseWSName).getID();
        categoryWSID = workspaceNodes.get(categoryWSName).getID();

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public void finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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

  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (hasSessionParameters == false)
    {
      // Do the initial setup part (what used to be part of connect() itself)

      viewProtocol = params.getParameter(CswsParameters.viewProtocol);
      viewServerName = params.getParameter(CswsParameters.viewServerName);
      viewPort = params.getParameter(CswsParameters.viewPort);
      viewCgiPath = params.getParameter(CswsParameters.viewCgiPath);
      viewAction = params.getParameter(CswsParameters.viewAction);

      serverProtocol = params.getParameter(CswsParameters.serverProtocol);
      String serverPortString = params.getParameter(CswsParameters.serverPort);
      serverUsername = params.getParameter(CswsParameters.serverUsername);
      serverPassword = params.getObfuscatedParameter(CswsParameters.serverPassword);
      authenticationServicePath = params.getParameter(CswsParameters.authenticationPath);
      documentManagementServicePath = params.getParameter(CswsParameters.documentManagementPath);
      contentServiceServicePath = params.getParameter(CswsParameters.contentServicePath);
      memberServiceServicePath = params.getParameter(CswsParameters.memberServicePath);
      searchServiceServicePath = params.getParameter(CswsParameters.searchServicePath);
      dataCollection = params.getParameter(CswsParameters.dataCollection);
      serverHTTPNTLMDomain = params.getParameter(CswsParameters.serverHTTPNTLMDomain);
      serverHTTPNTLMUsername = params.getParameter(CswsParameters.serverHTTPNTLMUsername);
      serverHTTPNTLMPassword = params.getObfuscatedParameter(CswsParameters.serverHTTPNTLMPassword);


      // Server parameter processing

      if (serverProtocol == null || serverProtocol.length() == 0)
        serverProtocol = "http";

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
      String serverHTTPSKeystoreData = params.getParameter(CswsParameters.serverHTTPSKeystore);
      if (serverHTTPSKeystoreData != null)
        serverHTTPSKeystore = KeystoreManagerFactory.make("",serverHTTPSKeystoreData);

      // View parameters
      // View parameter processing

      if (viewProtocol == null || viewProtocol.length() == 0)
      {
        if (serverProtocol == null)
          viewProtocol = "http";
        else
          viewProtocol = serverProtocol;
      }

      if (viewPort == null || viewPort.length() == 0)
      {
        if (serverProtocol == null || !viewProtocol.equals(serverProtocol))
        {
          if (!viewProtocol.equals("https"))
            viewPort = "80";
          else
            viewPort = "443";
        }
        else
          viewPort = new Integer(serverPort).toString();
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
        viewCgiPath = "";

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
      final SSLConnectionSocketFactory myFactory;
      final javax.net.ssl.SSLSocketFactory mySslFactory;
      if (serverHTTPSKeystore != null)
      {
        mySslFactory = new InterruptibleSocketFactory(serverHTTPSKeystore.getSecureSocketFactory(), connectionTimeout);
        myFactory = new SSLConnectionSocketFactory(mySslFactory, NoopHostnameVerifier.INSTANCE);
      }
      else
      {
        mySslFactory = null;
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
      if (serverHTTPNTLMDomain != null)
      {
        credentialsProvider.setCredentials(AuthScope.ANY,
          new NTCredentials(serverHTTPNTLMUsername,serverHTTPNTLMPassword,currentHost,serverHTTPNTLMDomain));
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

      // Construct the various URLs we need
      final String baseURL = serverProtocol + "://" + serverName + ":" + serverPort;
      final String authenticationServiceURL = baseURL + authenticationServicePath;
      final String documentManagementServiceURL = baseURL + documentManagementServicePath;
      final String contentServiceServiceURL = baseURL + contentServiceServicePath;
      final String memberServiceServiceURL = baseURL + memberServiceServicePath;
      final String searchServiceServiceURL = baseURL + searchServiceServicePath;

      // Build web services connection management object
      if (Logging.connectors.isDebugEnabled())
      {
        String passwordExists = (serverPassword!=null&&serverPassword.length()>0)?"password exists":"";
        Logging.connectors.debug("Csws: Csws Session: Server='"+serverName+"'; port='"+serverPort+"'; user name='"+serverUsername+"'; "+passwordExists);
      }

      // Construct a new csws session object for setting up this session
      cswsSession = new CswsSession(serverUsername, serverPassword, serverHTTPSKeystore, 1000L * 60L * 15L,
        authenticationServiceURL, documentManagementServiceURL, contentServiceServiceURL, memberServiceServiceURL, searchServiceServiceURL);

      final GetSessionThread t = new GetSessionThread();
      try
      {
        t.start();
        t.finishUp();
        hasConnected = true;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
      // Do a check of all web services
      // MHL
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

    serverProtocol = null;
    serverName = null;
    serverPort = -1;
    serverUsername = null;
    serverPassword = null;

    authenticationServicePath = null;
    documentManagementServicePath = null;
    contentServiceServicePath = null;
    memberServiceServicePath = null;
    searchServiceServicePath = null;

    serverHTTPNTLMDomain = null;
    serverHTTPNTLMUsername = null;
    serverHTTPNTLMPassword = null;
    serverHTTPSKeystore = null;

    viewPort = null;
    viewServerName = null;
    viewProtocol = null;
    viewCgiPath = null;

    viewBasePath = null;

    workspaceNodes.clear();
    enterpriseWSID = null;
    categoryWSID = null;

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
    final String objectID = documentIdentifier.substring(1);
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
  *@param lastSeedVersion is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("Csws: addSeedDocuments");
    getSession();
    CswsContext llc = new CswsContext();

    // First, grab the root object
    ObjectInformation rootValue = llc.getObjectInformation(enterpriseWSID);
    if (!rootValue.exists())
    {
      // If we get here, it HAS to be a bad network/transient problem.
      Logging.connectors.warn("Csws: Could not look up root workspace object during seeding!  Retrying -");
      throw new ServiceInterruption("Service interruption during seeding",new ManifoldCFException("Could not looking root workspace object during seeding"),System.currentTimeMillis()+60000L,
        System.currentTimeMillis()+600000L,-1,true);
    }

    Logging.connectors.debug("Csws: Picking up starting paths");

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
        Long vaf = rootValue.getPathId(path);
        if (vaf != null)
        {
          activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
            path,"OK",null,null);

          String newID = "F" + vaf;
          activities.addSeedDocument(newID);
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Csws: Seed = '"+newID+"'");
        }
        else
        {
          Logging.connectors.debug("Csws: Path '"+path+"' no longer present.");
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
        final ListUsersThread t = new ListUsersThread();
        try {
          Logging.connectors.debug("Csws: Going through user workspaces");
          t.start();
          final PageHandle resultPageHandle = t.finishUp();

          // Now walk through the results and add them
          while (true) {
            final GetUserResultsThread t2 = new GetUserResultsThread(resultPageHandle);
            try {
              t2.start();
              final List<? extends Member> childrenDocs = t2.finishUp();
              if (childrenDocs == null) {
                // We're done
                break;
              }

              for (final Member m : childrenDocs)
              {
                final long childID = m.getID();

                // Skip admin user
                if (childID == 1000L || childID == 1001L)
                  continue;

                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Csws: Found a user: ID="+childID);

                activities.addSeedDocument("F"+childID);
              }
            }
            catch (InterruptedException e)
            {
              t2.interrupt();
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
          }
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }

      }
      Logging.connectors.debug("Csws: Done user workspaces");
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
    CswsContext llc = new CswsContext();
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
      long objID = new Long(docID.substring(1)).longValue();

      getSession();

      final ObjectInformation value = llc.getObjectInformation(objID);
      if (!value.exists())
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Object "+objID+" has no information - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }

      // Make sure we have permission to see the object's contents
      final NodePermissions permissions = value.getPermissions();
      if (!permissions.isSeeContentsPermission())
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Crawl user cannot see contents of object "+objID+" - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }

      final Date dt = value.getModifyDate();

      // The rights don't change when the object changes, so we have to include those too.
      final NodeRights rights = getObjectRights(objID);
      if (rights == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Could not get rights for object "+objID+" - deleting");
        activities.deleteDocument(documentIdentifier);
        continue;
      }

      // We were able to get rights, so object still exists.

      if (isFolder)
      {
        // === Csws folder ===
        // I'm still not sure if Csws folder modified dates are one-level or hierarchical.
        // The code below assumes one-level only, so we always scan folders and there's no versioning
        if (Logging.connectors.isDebugEnabled()) {
          Logging.connectors.debug("Csws: Processing folder "+objID);
        }

        final ListObjectsThread t = new ListObjectsThread(objID, new String[]{"OTDataID", "OTSubTypeName", "OTName"}, dataCollection, filterString, "OTDataID");
        t.start();
        final List<? extends SGraph> childrenDocs;
        try {
          childrenDocs = t.finishUp();
        } catch (InterruptedException e) {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        for (final SGraph childDoc : childrenDocs)
        {
          // Decode results
          final long childID = getID(childDoc, 0);
          final String subType = getString(childDoc, 2);
          final String name = getString(childDoc, 1);

          if (Logging.connectors.isDebugEnabled())
          {
            Logging.connectors.debug("Csws: Found a child of folder "+objID+" : ID="+childID);
          }

          // All we need to know is whether the child is a container or not, and there is a way to do that directly from the node
          final boolean childIsFolder = subType.equals("Folder") || subType.equals("Project") || subType.equals("CompoundDocument");

          // If it's a folder, we just let it through for now
          if (!childIsFolder)
          {
            if (checkInclude(name, spec) == false)
            {
              if (Logging.connectors.isDebugEnabled()) {
                Logging.connectors.debug("Csws: Child identifier "+childID+" was excluded by inclusion criteria");
              }
              continue;
            }
          }

          if (childIsFolder)
          {
            if (Logging.connectors.isDebugEnabled()) {
              Logging.connectors.debug("Csws: Child identifier "+childID+" is a folder, project, or compound document; adding a reference");
            }
            if (subType.equals("Project"))
            {
              // If we pick up a project object, we need to describe the volume object (which
              // will be the root of all documents beneath)
              activities.addDocumentReference("F"+(-childID));
            }
            else
            {
              activities.addDocumentReference("F"+childID);
            }
          }
          else
          {
            if (Logging.connectors.isDebugEnabled()) {
              Logging.connectors.debug("Csws: Child identifier "+childID+" is a simple document; adding a reference");
            }
            activities.addDocumentReference("D"+childID);
          }

        }
        if (Logging.connectors.isDebugEnabled()) {
          Logging.connectors.debug("Csws: Done processing folder "+objID);
        }
      }
      else
      {
        // === Csws document ===

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
          final ObjectInformation objectInfo = llc.getObjectInformation(objID);
          long[] catIDs = objectInfo.getObjectCategoryIDs();
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

        // Tack on ingestCgiPath, to insulate us against changes to the repository connection setup.
        sb.append("_").append(viewBasePath);

        String versionString = sb.toString();
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Successfully calculated version string for document "+objID+" : '"+versionString+"'");

        if (!activities.checkDocumentNeedsReindexing(documentIdentifier, versionString))
          continue;

        // Index the document
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Processing document "+objID);
        if (!checkIngest(llc,objID,spec))
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Csws: Decided not to ingest document "+objID+" - Did not match ingestion criteria");
          activities.noDocument(documentIdentifier,versionString);
          continue;
        }

        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Decided to ingest document "+objID);

        // Index it
        ingestFromCsws(llc, documentIdentifier, versionString, actualAcls, denyAcls,
          (rights==null)?null:(rights.getOwnerRight()==null)?null:rights.getOwnerRight().getRightID(), categoryPaths, activities, desc, sDesc);

        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Done processing document "+objID);
      }
    }
  }

  private static long getID(final SGraph sg, final int nodeIndex) {
    final String value = getString(sg, nodeIndex);
    return new Long(value).longValue();
  }

  private static String getString(final SGraph sg, final int nodeIndex) {
    final List<? extends SNode> nodes = sg.getN();
    if (nodes == null || nodes.size() < 1) {
      throw new IllegalArgumentException("Expecting exactly one SNode");
    }
    final SNode node = nodes.get(0);
    final List<? extends String> stringValues = node.getS();
    if (stringValues == null || stringValues.size() <= nodeIndex) {
      return null;
    }
    return stringValues.get(nodeIndex);
  }

  /**
   * Thread that reads child objects that have a specified filter criteria, given an object ID.
   */
  protected class ListObjectsThread extends Thread
  {
    protected final long objID;
    protected final String[] outputColumns;
    protected final String filterString;
    protected final String orderingColumn;
    protected final String dataCollection;
    protected Throwable exception = null;
    protected List<? extends SGraph> rval = null;

    public ListObjectsThread(final long objID, final String[] outputColumns, final String dataCollection, final String filterString, final String orderingColumn)
    {
      super();
      setDaemon(true);
      this.objID = objID;
      this.outputColumns = outputColumns;
      this.dataCollection = dataCollection;
      this.filterString = filterString;
      this.orderingColumn = orderingColumn;
    }

    public void run()
    {
      try
      {
        // Worry about paging later.  Since these are all children of a specific node, we are unlikely to have a problem in any case.
        // TBD
        rval = cswsSession.searchFor(objID, outputColumns, dataCollection, filterString, orderingColumn, 0, 100000);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public List<? extends SGraph> finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
    // Intrinsically, Csws doesn't batch well.  Multiple chunks have no advantage over one-at-a-time requests,
    // since apparently the Csws API does not support multiples.  HOWEVER - when metadata is considered,
    // it becomes worthwhile, because we will be able to do what is needed to look up the correct CATID node
    // only once per n requests!  So it's a tradeoff between the advantage gained by threading, and the
    // savings gained by CATID lookup.
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
    tabsArray.add(Messages.getString(locale,"CswsConnector.Server"));
    tabsArray.add(Messages.getString(locale,"CswsConnector.DocumentView"));

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_JS, null, true);
  }

  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags.  The name of the
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
    fillInDocumentViewTab(velocityContext, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_SERVER_HTML, velocityContext);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_VIEW_HTML, velocityContext);
  }

  /** Fill in Server tab */
  protected static void fillInServerTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    // LAPI parameters
    String serverProtocol = parameters.getParameter(CswsParameters.serverProtocol);
    if (serverProtocol == null)
      serverProtocol = "http";
    String serverName = parameters.getParameter(CswsParameters.serverName);
    if (serverName == null)
      serverName = "localhost";
    String serverPort = parameters.getParameter(CswsParameters.serverPort);
    if (serverPort == null)
      serverPort = "2099";
    String serverUserName = parameters.getParameter(CswsParameters.serverUsername);
    if(serverUserName == null)
      serverUserName = "";
    String serverPassword = parameters.getObfuscatedParameter(CswsParameters.serverPassword);
    if (serverPassword == null)
      serverPassword = "";
    else
      serverPassword = out.mapPasswordToKey(serverPassword);

    String authenticationServicePath = parameters.getParameter(CswsParameters.authenticationPath);
    if (authenticationServicePath == null)
      authenticationServicePath = CswsParameters.authenticationPathDefault;
    String contentServiceServicePath = parameters.getParameter(CswsParameters.contentServicePath);
    if (contentServiceServicePath == null)
      contentServiceServicePath = CswsParameters.contentServicePathDefault;
    String documentManagementServicePath = parameters.getParameter(CswsParameters.documentManagementPath);
    if (documentManagementServicePath == null)
      documentManagementServicePath = CswsParameters.documentManagementPathDefault;
    String memberServiceServicePath = parameters.getParameter(CswsParameters.memberServicePath);
    if (memberServiceServicePath == null)
      memberServiceServicePath = CswsParameters.memberServicePathDefault;
    String searchServiceServicePath = parameters.getParameter(CswsParameters.searchServicePath);
    if (searchServiceServicePath == null)
      searchServiceServicePath = CswsParameters.searchServicePathDefault;
    String dataCollection = parameters.getParameter(CswsParameters.dataCollection);
    if (dataCollection == null)
      dataCollection = CswsParameters.dataCollectionDefault;

    String serverHTTPNTLMDomain = parameters.getParameter(CswsParameters.serverHTTPNTLMDomain);
    if(serverHTTPNTLMDomain == null)
      serverHTTPNTLMDomain = "";
    String serverHTTPNTLMUserName = parameters.getParameter(CswsParameters.serverHTTPNTLMUsername);
    if(serverHTTPNTLMUserName == null)
      serverHTTPNTLMUserName = "";
    String serverHTTPNTLMPassword = parameters.getObfuscatedParameter(CswsParameters.serverHTTPNTLMPassword);
    if (serverHTTPNTLMPassword == null)
      serverHTTPNTLMPassword = "";
    else
      serverHTTPNTLMPassword = out.mapPasswordToKey(serverHTTPNTLMPassword);
    String serverHTTPSKeystore = parameters.getParameter(CswsParameters.serverHTTPSKeystore);

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

    velocityContext.put("AUTHENTICATIONSERVICEPATH", authenticationServicePath);
    velocityContext.put("CONTENTSERVICESERVICEPATH", contentServiceServicePath);
    velocityContext.put("DOCUMENTMANAGEMENTSERVICEPATH", documentManagementServicePath);
    velocityContext.put("MEMBERSERVICESERVICEPATH", memberServiceServicePath);
    velocityContext.put("SEARCHSERVICESERVICEPATH", searchServiceServicePath);
    velocityContext.put("DATACOLLECTION", dataCollection);

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

  /** Fill in Document View tab */
  protected static void fillInDocumentViewTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    // Document view parameters
    String viewProtocol = parameters.getParameter(CswsParameters.viewProtocol);
    if (viewProtocol == null)
      viewProtocol = "http";

    String viewServerName = parameters.getParameter(CswsParameters.viewServerName);
    if(viewServerName == null)
      viewServerName = "";
    String viewPort = parameters.getParameter(CswsParameters.viewPort);
    if(viewPort == null)
      viewPort = "";
    String viewCgiPath = parameters.getParameter(CswsParameters.viewCgiPath);
    if (viewCgiPath == null)
      viewCgiPath = "/livelink/livelink.exe";
    String viewAction = parameters.getParameter(CswsParameters.viewAction);
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
      parameters.setParameter(CswsParameters.viewProtocol,viewProtocol);
    String viewServerName = variableContext.getParameter("viewservername");
    if (viewServerName != null)
      parameters.setParameter(CswsParameters.viewServerName,viewServerName);
    String viewPort = variableContext.getParameter("viewport");
    if (viewPort != null)
      parameters.setParameter(CswsParameters.viewPort,viewPort);
    String viewCgiPath = variableContext.getParameter("viewcgipath");
    if (viewCgiPath != null)
      parameters.setParameter(CswsParameters.viewCgiPath,viewCgiPath);
    String viewAction = variableContext.getParameter("viewaction");
    if (viewAction != null)
      parameters.setParameter(CswsParameters.viewAction,viewAction);

    // Server parameters
    String serverProtocol = variableContext.getParameter("serverprotocol");
    if (serverProtocol != null)
      parameters.setParameter(CswsParameters.serverProtocol,serverProtocol);
    String serverName = variableContext.getParameter("servername");
    if (serverName != null)
      parameters.setParameter(CswsParameters.serverName,serverName);
    String serverPort = variableContext.getParameter("serverport");
    if (serverPort != null)
      parameters.setParameter(CswsParameters.serverPort,serverPort);
    String serverUserName = variableContext.getParameter("serverusername");
    if (serverUserName != null)
      parameters.setParameter(CswsParameters.serverUsername,serverUserName);
    String serverPassword = variableContext.getParameter("serverpassword");
    if (serverPassword != null)
      parameters.setObfuscatedParameter(CswsParameters.serverPassword,variableContext.mapKeyToPassword(serverPassword));

    String authenticationServicePath = variableContext.getParameter("authenticationservicepath");
    if (authenticationServicePath != null)
      parameters.setParameter(CswsParameters.authenticationPath, authenticationServicePath);
    String contentServiceServicePath = variableContext.getParameter("contentserviceservicepath");
    if (contentServiceServicePath != null)
      parameters.setParameter(CswsParameters.contentServicePath, contentServiceServicePath);
    String documentManagementServicePath = variableContext.getParameter("documentmanagementservicepath");
    if (documentManagementServicePath != null)
      parameters.setParameter(CswsParameters.documentManagementPath, documentManagementServicePath);
    String memberServiceServicePath = variableContext.getParameter("memberserviceservicepath");
    if (memberServiceServicePath != null)
      parameters.setParameter(CswsParameters.memberServicePath, memberServiceServicePath);
    String searchServiceServicePath = variableContext.getParameter("searchserviceservicepath");
    if (searchServiceServicePath != null)
      parameters.setParameter(CswsParameters.searchServicePath, searchServiceServicePath);
    String dataCollection = variableContext.getParameter("datacollection");
    if (dataCollection != null)
      parameters.setParameter(CswsParameters.dataCollection, dataCollection);

    String serverHTTPNTLMDomain = variableContext.getParameter("serverhttpntlmdomain");
    if (serverHTTPNTLMDomain != null)
      parameters.setParameter(CswsParameters.serverHTTPNTLMDomain,serverHTTPNTLMDomain);
    String serverHTTPNTLMUserName = variableContext.getParameter("serverhttpntlmusername");
    if (serverHTTPNTLMUserName != null)
      parameters.setParameter(CswsParameters.serverHTTPNTLMUsername,serverHTTPNTLMUserName);
    String serverHTTPNTLMPassword = variableContext.getParameter("serverhttpntlmpassword");
    if (serverHTTPNTLMPassword != null)
      parameters.setObfuscatedParameter(CswsParameters.serverHTTPNTLMPassword,variableContext.mapKeyToPassword(serverHTTPNTLMPassword));

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
    parameters.setParameter(CswsParameters.serverHTTPSKeystore,serverHTTPSKeystoreValue);

    return null;
  }

  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt; and &lt;body&gt; tags.
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
        configMap.put(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param),"=&lt;"+Integer.toString(kmanager.getContents().length)+Messages.getBodyString(locale,"CswsConnector.certificates")+"&gt;");
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
    tabsArray.add(Messages.getString(locale,"CswsConnector.Paths"));
    tabsArray.add(Messages.getString(locale,"CswsConnector.Filters"));
    tabsArray.add(Messages.getString(locale,"CswsConnector.Security"));
    tabsArray.add(Messages.getString(locale,"CswsConnector.Metadata"));

    String seqPrefixParam = "s" + connectionSequenceNumber + "_";

    Map<String, String> paramMap = new HashMap<String, String>();
    paramMap.put("seqPrefix", seqPrefixParam);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap, true);
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags.  The name of the form is always "editjob".
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
    if (tabName.equals(Messages.getString(locale,"CswsConnector.Paths")))
      fillInTransientPathsInfo(velocityContext,connectionSequenceNumber);
    else if (tabName.equals(Messages.getString(locale,"CswsConnector.Metadata")))
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
  * this configuration will be within appropriate &lt;html&gt; and &lt;body&gt; tags.
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

  /** Get the allowed workspace names.
  *@return a list of workspace names.
  */
  public String[] getWorkspaceNames()
    throws ManifoldCFException, ServiceInterruption
  {
    return new String[]{categoryWSName, enterpriseWSName};
  }

  /** Given a path string, get a list of folders and projects under that node.
  *@return a list of folder and project names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildFolderNames(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getChildFolders(new CswsContext(),pathString);
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildCategoryNames(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getChildCategories(new CswsContext(),pathString);
  }

  /** Given a category path, get a list of legal attribute names.
  *@param pathString is the current path of a category (with path components separated by dots).
  *@return a list of attribute names, in sorted order, or null of the path was invalid.
  */
  public String[] getCategoryAttributes(String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    return getCategoryAttributes(new CswsContext(), pathString);
  }

  protected String[] getCategoryAttributes(CswsContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    // Start at root
    RootValue rv = new RootValue(llc,pathString);

    // Get the object id of the category the path describes
    long catObjectID = getCategoryId(rv);
    if (catObjectID == -1L)
      return null;

    String[] rval = getCategoryAttributes(catObjectID);
    if (rval == null)
      return new String[0];
    return rval;
  }

  // Protected methods and classes

  /**
  * Connects to the specified Csws document using HTTP protocol
  * @param documentIdentifier is the document identifier (as far as the crawler knows).
  * @param activities is the process activity structure, so we can ingest
  */
  protected void ingestFromCsws(CswsContext llc,
    String documentIdentifier, String version,
    String[] actualAcls, String[] denyAcls,
    Long ownerID,
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

    long objID = new Integer(documentIdentifier.substring(1)).intValue();

    // Try/finally for fetch logging
    try
    {
      final String viewHttpAddress = convertToViewURI(documentIdentifier);
      if (viewHttpAddress == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: No view URI "+contextMsg+" - not ingesting");
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
          Logging.connectors.debug("Csws: Excluding document "+documentIdentifier+" because its URL ("+viewHttpAddress+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      // Add general metadata
      final ObjectInformation objInfo = llc.getObjectInformation(objID);
      final VersionInformation versInfo = llc.getVersionInformation(objID, 0);
      if (!objInfo.exists())
      {
        resultCode = "OBJECTNOTFOUND";
        resultDescription = "Object was not found in Csws";
        Logging.connectors.debug("Csws: No object "+contextMsg+": not ingesting");
        activities.noDocument(documentIdentifier,version);
        return;
      }
      if (!versInfo.exists())
      {
        resultCode = "VERSIONNOTFOUND";
        resultDescription = "Version was not found in Csws";
        Logging.connectors.debug("Csws: No version data "+contextMsg+": not ingesting");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      final String mimeType = versInfo.getMimeType();
      if (!activities.checkMimeTypeIndexable(mimeType))
      {
        // Document not indexable because of its mime type
        resultCode = activities.EXCLUDED_MIMETYPE;
        resultDescription = "Mime type ("+mimeType+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Excluding document "+documentIdentifier+" because its mime type ("+mimeType+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      final Long dataSize = versInfo.getDataSize();
      if (dataSize == null)
      {
        // Document had no length
        resultCode = "DOCUMENTNOLENGTH";
        resultDescription = "Document had no length in Csws";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Excluding document "+documentIdentifier+" because it had no length");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      if (!activities.checkLengthIndexable(dataSize.longValue()))
      {
        // Document not indexable because of its length
        resultCode = activities.EXCLUDED_LENGTH;
        resultDescription = "Document length ("+dataSize+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Excluding document "+documentIdentifier+" because its length ("+dataSize+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      final Date modifyDate = versInfo.getModifyDate();
      if (!activities.checkDateIndexable(modifyDate))
      {
        // Document not indexable because of its date
        resultCode = activities.EXCLUDED_DATE;
        resultDescription = "Document date ("+modifyDate+") was rejected by output connector";
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Csws: Excluding document "+documentIdentifier+" because its date ("+modifyDate+") was rejected by output connector");
        activities.noDocument(documentIdentifier,version);
        return;
      }

      final String fileName = versInfo.getFileName();
      final Date creationDate = objInfo.getCreationDate();
      final Long parentID = objInfo.getParentId();


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

      UserInformation owner = ownerID == null?null:llc.getUserInformation(ownerID);  // from ObjectRights
      UserInformation creator = llc.getUserInformation(objInfo.getCreatorId());
      UserInformation modifier = llc.getUserInformation(versInfo.getOwnerId());
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
          final long catID = pathItem.getCatID();
          objInfo.getSpecifiedCategoryAttribute(rd, catID, pathItem.getCatName());
        }
      }

      if (actualAcls != null && denyAcls != null)
        rd.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,actualAcls,denyAcls);

      // Add the path metadata item into the mix, if enabled
      final String pathAttributeName = sDesc.getPathAttributeName();
      if (pathAttributeName != null && pathAttributeName.length() > 0)
      {
        String pathString = sDesc.getPathAttributeValue(documentIdentifier);
        if (pathString != null)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Csws: Path attribute name is '"+pathAttributeName+"'"+contextMsg+", value is '"+pathString+"'");
          rd.addField(pathAttributeName,pathString);
        }
      }

      // Use FetchVersion instead
      long currentTime;

      // Fire up the document reading thread
      final DocumentReadingThread t = new DocumentReadingThread(objID, 0);
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
        activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,readSize,new Long(objID).toString(),resultCode,resultDescription,null);
    }
  }

  protected static void handleIOException(String contextMsg, IOException e)
    throws ManifoldCFException, ServiceInterruption
  {
    long currentTime = System.currentTimeMillis();
    if (e instanceof java.net.SocketTimeoutException)
    {
      Logging.connectors.warn("Csws: Csws socket timed out ingesting from the Csws HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof java.net.SocketException)
    {
      Logging.connectors.warn("Csws: Csws socket error ingesting from the Csws HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof javax.net.ssl.SSLHandshakeException)
    {
      Logging.connectors.warn("Csws: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
      throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
    }
    if (e instanceof ConnectTimeoutException)
    {
      Logging.connectors.warn("Csws: Csws socket timed out connecting to the Csws HTTP Server "+contextMsg+": "+e.getMessage(), e);
      throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
    }
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    // Treat unknown error ingesting data as a transient condition
    Logging.connectors.warn("Csws: IO exception ingesting "+contextMsg+": "+e.getMessage(),e);
    throw new ServiceInterruption("IO exception ingesting "+contextMsg+": "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
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
  protected String[] getChildFolders(CswsContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    RootValue rv = new RootValue(llc,pathString);

    // Get the volume, object id of the folder/project the path describes
    Long vid = getPathId(rv);
    if (vid == null)
      return null;

    final String filterString = "\"OTSubType\":0 OR \"OTSubType\":202 OR \"OTSubType\":136";

    final ListObjectsThread t = new ListObjectsThread(vid, new String[]{"OTName"}, dataCollection, filterString, "OTName");
    try
    {
      t.start();
      final List<? extends SGraph> children = t.finishUp();

      final String[] rval = new String[children.size()];
      int j = 0;
      for (final SGraph node : children)
      {
        rval[j++] = getString(node, 0);
      }
      return rval;
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  protected String[] getChildCategories(CswsContext llc, String pathString)
    throws ManifoldCFException, ServiceInterruption
  {
    // Start at root
    RootValue rv = new RootValue(llc,pathString);

    // Get the volume, object id of the folder/project the path describes
    Long vid = getPathId(rv);
    if (vid == null)
      return null;

    // We want only folders that are children of the current object and which match the specified subfolder
    String filterString = "\"OTSubType\":131";

    final ListObjectsThread t = new ListObjectsThread(vid, new String[]{"OTName"}, dataCollection, filterString, "OTName");
    try
    {
      t.start();
      final List<? extends SGraph> children = t.finishUp();

      final String[] rval = new String[children.size()];
      int j = 0;
      for (final SGraph sg : children)
      {
        rval[j++] = getString(sg, 0);
      }
      return rval;
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  protected class GetCategoryAttributesThread extends Thread
  {
    protected final long catObjectID;
    protected Throwable exception = null;
    protected String[] rval = null;

    public GetCategoryAttributesThread(long catObjectID)
    {
      super();
      setDaemon(true);
      this.catObjectID = catObjectID;
    }

    public void run()
    {
      try
      {
        final AttributeGroupDefinition def = cswsSession.getCategoryDefinition(catObjectID);
        if (def == null) {
          return;
        }
        final List<? extends Attribute> atts = def.getAttributes();
        int attrCount = 0;
        for (final Attribute at : atts) {
          // Check for CATEGORY_TYPE_LIBRARY
          //if (at.getType().equals(CATEGORY_TYPE_LIBRARY))
          attrCount++;
        }
        rval = new String[attrCount];
        attrCount = 0;
        for (final Attribute at : atts) {
          rval[attrCount++] = at.getDisplayName();
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public String[] finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
  protected String[] getCategoryAttributes(long catObjectID)
    throws ManifoldCFException, ServiceInterruption
  {
    final GetCategoryAttributesThread t = new GetCategoryAttributesThread(catObjectID);
    try
    {
      t.start();
      return t.finishUp();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /* Unused -- done a different way
  protected class GetCategoryVersionThread extends Thread
  {
    protected final long objID;
    protected final long catID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetCategoryVersionThread(long objID, long catID)
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
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }
  */

  /** Get a category version for document.
  */
  /* Unused -- done a different way
  protected LLValue getCatVersion(long objID, long catID)
    throws ManifoldCFException, ServiceInterruption
  {
    final GetCategoryVersionThread t = new GetCategoryVersionThread(objID, catID);
    try
    {
      t.start();
      return t.finishUp();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  */

  /* Unused; done a different way
  protected class GetAttributeValueThread extends Thread
  {
    protected final LLValue categoryVersion;
    protected final String attributeName;
    protected Throwable exception = null;
    protected String[] rval = null;

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

        if (children != null)
        {
          rval = new String[children.size()];
          LLValueEnumeration en = children.enumerateValues();

          int j = 0;
          while (en.hasMoreElements())
          {
            LLValue v = (LLValue)en.nextElement();
            rval[j] = v.toString();
            j++;
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public String[] finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }
  */

  /** Get an attribute value from a category version.
  */
  /* Unused -- done a different way
  protected String[] getAttributeValue(LLValue categoryVersion, String attributeName)
    throws ManifoldCFException, ServiceInterruption
  {
    final GetAttributeValueThread t = new GetAttributeValueThread(categoryVersion, attributeName);
    try
    {
      t.start();
      return t.finishUp();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  */

  protected class GetObjectRightsThread extends Thread
  {
    protected final long objID;
    protected Throwable exception = null;
    protected NodeRights rval = null;

    public GetObjectRightsThread(long objID)
    {
      super();
      setDaemon(true);
      this.objID = objID;
    }

    public void run()
    {
      try
      {
        rval = cswsSession.getNodeRights(objID);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public NodeRights finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Get an object's rights.  This will be an array of right id's, including the special
  * ones defined by Csws, or null will be returned (if the object is not found).
  *@param objID is the object id
  *@return the NodeRights object
  */
  protected NodeRights getObjectRights(long objID)
    throws ManifoldCFException, ServiceInterruption
  {
    final GetObjectRightsThread t = new GetObjectRightsThread(objID);
    try
    {
      t.start();
      return t.finishUp();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Local cache for various kinds of objects that may be useful more than once.
  */
  protected class CswsContext
  {
    /** Cache of ObjectInformation objects. */
    protected final Map<ObjectInformation,ObjectInformation> objectInfoMap = new HashMap<>();
    /** Cache of VersionInformation objects. */
    protected final Map<VersionInformation,VersionInformation> versionInfoMap = new HashMap<>();
    /** Cache of UserInformation objects */
    protected final Map<UserInformation,UserInformation> userInfoMap = new HashMap<>();

    public CswsContext()
    {
    }

    public ObjectInformation getObjectInformation(final String workspaceName)
    {
      ObjectInformation oi = new ObjectInformation(workspaceName);
      ObjectInformation lookupValue = objectInfoMap.get(oi);
      if (lookupValue == null)
      {
        objectInfoMap.put(oi, oi);
        return oi;
      }
      return lookupValue;
    }

    public ObjectInformation getObjectInformation(long objectID)
    {
      ObjectInformation oi = new ObjectInformation(objectID);
      ObjectInformation lookupValue = objectInfoMap.get(oi);
      if (lookupValue == null)
      {
        objectInfoMap.put(oi, oi);
        return oi;
      }
      return lookupValue;
    }

    public VersionInformation getVersionInformation(long objectID, int revisionNumber)
    {
      VersionInformation vi = new VersionInformation(objectID, revisionNumber);
      VersionInformation lookupValue = versionInfoMap.get(vi);
      if (lookupValue == null)
      {
        versionInfoMap.put(vi, vi);
        return vi;
      }
      return lookupValue;
    }

    public UserInformation getUserInformation(long userID)
    {
      UserInformation ui = new UserInformation(userID);
      UserInformation lookupValue = userInfoMap.get(ui);
      if (lookupValue == null)
      {
        userInfoMap.put(ui, ui);
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
    protected final long userID;

    protected boolean fetched = false;
    protected Member userValue = null;

    public UserInformation(long userID)
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
      final Member userValue = getUserValue();
      if (userValue == null)
        return null;
      return userValue.getName();
    }

    protected Member getUserValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (!fetched)
      {
        final GetUserInfoThread t = new GetUserInfoThread(userID);
        try
        {
          t.start();
          userValue = t.finishUp();
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        fetched = true;
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
      return Long.hashCode(userID);
    }

    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof UserInformation))
        return false;
      final UserInformation other = (UserInformation)o;
      return userID == other.userID;
    }

  }

  /** This object represents a cache of version information.
  * Initialize it with the volume ID and object ID and revision number (usually zero).
  * Then, request the desired fields from it.
  */
  protected class VersionInformation
  {
    protected final long objectID;
    protected final long revisionNumber;

    protected boolean fetched = false;
    protected Version versionValue = null;

    public VersionInformation(long objectID, long revisionNumber)
    {
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
      final Version elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.getFileDataSize();
    }

    /** Get file name.
    */
    public String getFileName()
      throws ServiceInterruption, ManifoldCFException
    {
      final Version elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.getFilename();
    }

    /** Get mime type.
    */
    public String getMimeType()
      throws ServiceInterruption, ManifoldCFException
    {
      final Version elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.getMimeType();
    }

    /** Get modify date.
    */
    public Date getModifyDate()
      throws ServiceInterruption, ManifoldCFException
    {
      final Version elem = getVersionValue();
      if (elem == null)
        return null;
      return new Date(elem.getModifyDate().toGregorianCalendar().getTimeInMillis());
    }

    /** Get modifier.
    */
    public Long getOwnerId()
      throws ServiceInterruption, ManifoldCFException
    {
      final Version elem = getVersionValue();
      if (elem == null)
        return null;
      return elem.getOwner();
    }

    /** Get version LLValue */
    protected Version getVersionValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (!fetched)
      {
        final GetVersionInfoThread t = new GetVersionInfoThread(objectID, revisionNumber);
        try
        {
          t.start();
          versionValue = t.finishUp();
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        fetched = true;
      }
      return versionValue;
    }

    @Override
    public int hashCode()
    {
      return Long.hashCode(objectID) + Long.hashCode(revisionNumber);
    }

    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof VersionInformation))
        return false;
      final VersionInformation other = (VersionInformation)o;
      return objectID == other.objectID && revisionNumber == other.revisionNumber;
    }

  }

  /** This object represents an object information cache.
  * Initialize it with the volume ID and object ID, and then request
  * the appropriate fields from it.  Keep it around as long as needed; it functions as a cache
  * of sorts...
  */
  protected class ObjectInformation
  {
    protected final long objectID;
    protected final String workspaceName;

    protected Node objectValue = null;
    protected boolean fetched = false;

    public ObjectInformation(final long objectID)
    {
      this.objectID = objectID;
      this.workspaceName = null;
    }

    public ObjectInformation(final String workspaceName) {
      this.workspaceName = workspaceName;
      this.objectID = -1L;
    }

    /**
     * Get workspace name
     */
    public String getWorkspaceName() {
      return workspaceName;
    }

    /**
    * Check whether object seems to exist or not.
    */
    public boolean exists()
      throws ServiceInterruption, ManifoldCFException
    {
      return getObjectValue() != null;
    }

    /** toString override */
    @Override
    public String toString()
    {
      return "(Object: "+objectID+")";
    }

    /**
    * Fetch attribute value for a specified categoryID for a given object.
    */
    public boolean getSpecifiedCategoryAttribute(final RepositoryDocument rd, final long catID, final String catName)
      throws ServiceInterruption, ManifoldCFException {
      // grab the associated catversion
      // TBD
      /*
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
                // TBD
                String[] metadataValue = getAttributeValue(catVersion,attrName);
                if (metadataValue != null)
                  rd.addField(metadataName,metadataValue);
                else
                  Logging.connectors.warn("Csws: Metadata attribute '"+metadataName+"' does not seem to exist; please correct the job");
              }
            }
        */
      final Node objInfo = getObjectValue();
      if (objInfo == null) {
        return false;
      }
      
      final List<? extends AttributeGroup> attributeGroups = objInfo.getMetadata().getAttributeGroups();
      for (final AttributeGroup attribute : attributeGroups) {
        final int index = attribute.getKey().indexOf(".");
        final String categoryAttributeName = attribute.getDisplayName();
        //System.out.println("CategoryName **:  " + attribute.getDisplayName());
        final String categoryId = attribute.getKey().substring(0, index);
        //System.out.println("CategoryId **:  " + categoryId);
        final long attrCatID = new Long(categoryId).longValue();
        if (attrCatID == catID) {
          final List<? extends DataValue> dataValues = attribute.getValues();

          int i = 0;
          for (final DataValue dataValue : dataValues) {
              int j = 0;
              String[] valuesToIndex1 = null;
              if (dataValue instanceof StringValue) {
                  StringValue typedAttr = (StringValue)dataValue;
                  List<String> valArray = typedAttr.getValues();
                  if(valArray != null) {
                      valuesToIndex1 = new String[valArray.size()];
                      for (final String valueToIndex : valArray) {
                          valuesToIndex1[j++] = valueToIndex;
                      }
                  }
              } else if (dataValue instanceof DateValue) {
                  DateValue typedAttr = (DateValue)dataValue;
                  List<XMLGregorianCalendar> valArray = typedAttr.getValues();
                  if (valArray != null) {
                      valuesToIndex1 = new String[valArray.size()];
                      for (final XMLGregorianCalendar valueToIndex : valArray) {
                          valuesToIndex1[j++] = valueToIndex.toString();
                      }
                  }
              }
              if (valuesToIndex1 != null) {
                  rd.addField(catName + "." + dataValue.getDescription(), valuesToIndex1);
              }
          }
        }
      }
      return true;
    }

    /**
    * Returns the object ID specified by the path name.
    * @param startPath is the folder name (a string with dots as separators)
    */
    public Long getPathId(String startPath)
      throws ServiceInterruption, ManifoldCFException
    {
      final Node objInfo = getObjectValue();
      if (objInfo == null)
        return null;

      // Grab the volume ID and starting object
      long obj = objInfo.getID();

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

        final String subFolder = currentTokenBuffer.toString();
        // We want only folders that are children of the current object and which match the specified subfolder
        final String filterString = "(\"OTSubType\":0 OR \"OTSubType\":202 OR \"OTSubType\":136) AND \"OTName\":\""+subFolder+"\"";

        final ListObjectsThread t = new ListObjectsThread(obj, new String[]{"OTDataID", "OTSubTypeName"}, dataCollection, filterString, "OTDataID");
        try
        {
          t.start();
          final List<? extends SGraph> children = t.finishUp();

          if (children == null) {
            return null;
          }

          // If there is one child, then we are okay.
          if (children.size() == 1)
          {
            for (final SGraph child : children) {
              obj = getID(child, 0);
              final String subtype = getString(child, 1);
              if (subtype.equals("Project"))
              {
                obj = -obj;
              }
            }
          }
          else
          {
            // Couldn't find the path.  Instead of throwing up, return null to indicate
            // illegal node.
            return null;
          }
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }

      }
      return new Long(obj);
    }

    /**
    * Get AttributeGroups
    */
    public List<? extends AttributeGroup> getAttributeGroups()
      throws ManifoldCFException, ServiceInterruption
    {
      final Node elem = getObjectValue();
      if (elem == null) {
        return null;
      }
      final Metadata m = elem.getMetadata();
      if (m == null) {
        return new ArrayList<>(0);
      }
      return m.getAttributeGroups();
    }

    /**
    * Get category IDs available to the object with a specified object ID
    */
    public long[] getObjectCategoryIDs()
      throws ManifoldCFException, ServiceInterruption
    {
      return getSuperObjectCategoryIDs(objectID);
    }

    /**
    * Returns the category ID specified by the path name.
    * @param startPath is the folder name, ending in a category name (a string with slashes as separators)
    */
    public long getCategoryId(String startPath)
      throws ManifoldCFException, ServiceInterruption
    {
      final Node objInfo = getObjectValue();
      if (objInfo == null)
        return -1;

      // Grab the volume ID and starting object
      long obj = objInfo.getID();

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
        if (charindex < startPath.length()) {
          filterString = "(\"OTSubType\":0 OR \"OTSubType\":202 OR \"OTSubType\":136)";
        } else {
          filterString = "\"OTSubType\":131";
        }

        filterString += " AND \"OTName\":\"" + subFolder + "\"";

        final ListObjectsThread t = new ListObjectsThread(obj, new String[]{"OTDataID", "OTSubTypeName"}, dataCollection, filterString, "OTDataID");
        try
        {
          t.start();
          final List<? extends SGraph> children = t.finishUp();
          if (children == null) {
            return -1;
          }

          // If there is one child, then we are okay.
          if (children.size() == 1)
          {
            for (final SGraph child : children) {
              // New starting point is the one we found.
              obj = getID(child, 0);
              final String subtype = getString(child, 1);
              if (subtype.equals("Project"))
              {
                obj = -obj;
              }
            }
          }
          else
          {
            // Couldn't find the path.  Instead of throwing up, return null to indicate
            // illegal node.
            return -1L;
          }
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
      return obj;
    }

    /** Get permissions.
    */
    public NodePermissions getPermissions()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.getPermissions();
    }

    /** Get OpenText document name.
    */
    public String getName()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.getName();
    }

    /** Get OpenText comments/description.
    */
    public String getComments()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.getComment();
    }

    /** Get parent ID.
    */
    public Long getParentId()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.getParentID();
    }

    /** Get creation date.
    */
    public Date getCreationDate()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return new Date(elem.getCreateDate().toGregorianCalendar().getTimeInMillis());
    }

    /** Get creator ID.
    */
    public Long getCreatorId()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return elem.getCreatedBy();
    }

    /* Get modify date.
    */
    public Date getModifyDate()
      throws ServiceInterruption, ManifoldCFException
    {
      final Node elem = getObjectValue();
      if (elem == null)
        return null;
      return new Date(elem.getModifyDate().toGregorianCalendar().getTimeInMillis());
    }

    /** Get the objInfo object.
    */
    protected Node getObjectValue()
      throws ServiceInterruption, ManifoldCFException
    {
      if (!fetched)
      {
        if (workspaceName != null) {
          final GetWorkspaceInfoThread t = new GetWorkspaceInfoThread(workspaceName);
          try
          {
            t.start();
            objectValue = t.finishUp();
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          fetched = true;
        } else {
          final GetObjectInfoThread t = new GetObjectInfoThread(objectID);
          try
          {
            t.start();
            objectValue = t.finishUp();
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          fetched = true;
        }
      }
      return objectValue;
    }

    @Override
    public int hashCode()
    {
      if (workspaceName != null) {
        return workspaceName.hashCode();
      }
      return Long.hashCode(objectID);
    }

    @Override
    public boolean equals(Object o)
    {
      if (!(o instanceof ObjectInformation))
        return false;
      final ObjectInformation other = (ObjectInformation)o;
      if (workspaceName != null || other.workspaceName != null) {
        if (workspaceName == null || other.workspaceName == null) {
          return false;
        }
        return workspaceName.equals(other.workspaceName);
      }
      return objectID == other.objectID;
    }
  }

  /** Thread we can abandon that lists all users (except admin).
  */
  protected class ListUsersThread extends Thread
  {
    protected PageHandle rval = null;
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
        rval = cswsSession.getAllUsers();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public PageHandle finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }

  }

  /** Thread we can abandon that lists all users (except admin).
  */
  protected class GetUserResultsThread extends Thread
  {
    protected final PageHandle pageHandle;

    protected List<? extends Member> rval = null;
    protected Throwable exception = null;

    public GetUserResultsThread(final PageHandle pageHandle)
    {
      super();
      this.pageHandle = pageHandle;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        rval = cswsSession.getNextUserSearchResults(pageHandle);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public List<? extends Member> finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
    protected final long user;
    protected Throwable exception = null;
    protected Member rval = null;

    public GetUserInfoThread(long user)
    {
      super();
      setDaemon(true);
      this.user = user;
    }

    public void run()
    {
      try
      {
        rval = cswsSession.getMember(user);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Member finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
    protected final long id;
    protected final long revNumber;
    protected Throwable exception = null;
    protected Version rval = null;

    public GetVersionInfoThread(final long id, final long revNumber)
    {
      super();
      setDaemon(true);
      this.id = id;
      this.revNumber = revNumber;
    }

    public void run()
    {
      try
      {
        //int status = LLDocs.GetVersionInfo(vol,id,revNumber,versioninfo);
        rval = cswsSession.getVersion(id, revNumber);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Version finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
    protected final long id;
    protected Throwable exception = null;
    protected Node rval = null;

    public GetObjectInfoThread(long id)
    {
      super();
      setDaemon(true);
      this.id = id;
    }

    public void run()
    {
      try
      {
        // int status = LLDocs.GetObjectInfo(vol,id,objinfo);
        this.rval = cswsSession.getNode(id);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Node finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  protected class GetWorkspaceInfoThread extends Thread
  {
    protected final String workspaceName;
    protected Throwable exception = null;
    protected Node rval = null;

    public GetWorkspaceInfoThread(final String workspaceName)
    {
      super();
      setDaemon(true);
      this.workspaceName = workspaceName;
    }

    public void run()
    {
      try
      {
        this.rval = cswsSession.getRootNode(workspaceName);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Node finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Build a set of actual acls given a set of rights */
  protected static String[] lookupTokens(final NodeRights rights, final ObjectInformation objInfo)
    throws ManifoldCFException, ServiceInterruption
  {
    if (!objInfo.exists())
      return null;

    final List<String> tokenAccumulator = new ArrayList<>();

    if (evaluateRight(rights.getOwnerRight())) {
      tokenAccumulator.add(new Long(rights.getOwnerRight().getRightID()).toString());
    }
    if (evaluateRight(rights.getOwnerGroupRight())) {
      tokenAccumulator.add(new Long(rights.getOwnerGroupRight().getRightID()).toString());
    }
    if (evaluateRight(rights.getPublicRight())) {
      tokenAccumulator.add("SYSTEM");
    }
    // What happened to Guest/World right??
    // MHL -TBD - for "GUEST" token

    for (final NodeRight nr : rights.getACLRights()) {
      if (evaluateRight(nr)) {
        tokenAccumulator.add(new Long(nr.getRightID()).toString());
      }
    }

    return tokenAccumulator.toArray(new String[tokenAccumulator.size()]);
  }

  /** Check if NodeRight conveys the permissions we need */
  protected static boolean evaluateRight(final NodeRight nr) {
    if (nr == null) {
      return false;
    }
    final NodePermissions np = nr.getPermissions();
    return np.isSeePermission() && np.isSeeContentsPermission();
  }

  protected class GetObjectCategoryIDsThread extends Thread
  {
    protected final long id;
    protected Throwable exception = null;
    protected long[] rval;

    public GetObjectCategoryIDsThread(long id)
    {
      super();
      setDaemon(true);
      this.id = id;
    }

    public void run()
    {
      try
      {
        final List<? extends Node> categories = cswsSession.listNodes(id);

        if (categories == null) {
          return;
        }

        int catCount = 0;
        for (final Node category : categories) {
          if (category.getType().equals("Category")) {
            catCount++;
          }
        }

        rval = new long[catCount];
        for (final Node category : categories) {
          if (category.getType().equals("Category")) {
            rval[catCount++] = category.getID();
          }
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public long[] finishUp()
      throws ManifoldCFException, ServiceInterruption, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
	if (thr instanceof RuntimeException)
	  throw (RuntimeException)thr;
	else if (thr instanceof ManifoldCFException)
	  throw (ManifoldCFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
	else if (thr instanceof Error)
	  throw (Error)thr;
	else
	  throw new RuntimeException("Unrecognized exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return rval;
    }
  }

  /** Get category IDs associated with an object.
  * @param id the object ID
  * @return an array of longs containing category identifiers, or null if the object is not found.
  */
  protected long[] getSuperObjectCategoryIDs(long id)
    throws ManifoldCFException, ServiceInterruption
  {
    final GetObjectCategoryIDsThread t = new GetObjectCategoryIDsThread(id);
    try
    {
      t.start();
      return t.finishUp();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** RootValue version of getPathId.
  */
  protected Long getPathId(RootValue rv)
    throws ManifoldCFException, ServiceInterruption
  {
    return rv.getRootValue().getPathId(rv.getRemainderPath());
  }

  /** Rootvalue version of getCategoryId.
  */
  protected long getCategoryId(RootValue rv)
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
  protected boolean checkIngest(CswsContext llc, long objID, Specification documentSpecification)
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

  /** Class that describes a metadata catid and path.
  */
  protected static class MetadataPathItem
  {
    protected final long catID;
    protected final String catName;

    /** Constructor.
    */
    public MetadataPathItem(long catID, String catName)
    {
      this.catID = catID;
      this.catName = catName;
    }

    /** Get the cat ID.
    *@return the id.
    */
    public long getCatID()
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
    protected final CswsContext llc;

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
    public SystemMetadataDescription(CswsContext llc, Specification spec)
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
              String type = includeMatch.substring(index+1).toLowerCase(Locale.ROOT);
              if (first)
                first = false;
              else
                fsb.append(" OR ");
              fsb.append("(\"OTFileType\":").append(type).append(")");
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
        sb.append("\"OTSubType\":0 OR \"OTSubType\":136 OR \"OTSubType\":202 OR (\"OTSubType\":144 AND (");
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
        long objectID;
        try
        {
          objectID = Integer.parseInt(identifierPart);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Bad document identifier: "+e.getMessage(),e);
        }

        final ObjectInformation objInfo = llc.getObjectInformation(objectID);
        if (!objInfo.exists())
        {
          // The document identifier describes a path that does not exist.
          // This is unexpected, but don't die: just log a warning and allow the higher level to deal with it.
          Logging.connectors.warn("Csws: Bad document identifier: '"+documentIdentifier+"' apparently does not exist, but need to find its path");
          return null;
        }

        // Get the name attribute
        String name = objInfo.getName();
        // Get the parentID attribute
        if (objInfo.getParentId() == null) {
          path = name;
        } else {
          long parentID = objInfo.getParentId().longValue();
          String parentIdentifier = "F"+parentID;
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
    protected final CswsContext llc;

    // This is a map of category name to category ID and attributes
    protected final Map<String,MetadataPathItem> categoryMap = new HashMap<String,MetadataPathItem>();

    /** Constructor.
    */
    public MetadataDescription(CswsContext llc)
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
            long catObjectID = rootValue.getCategoryId(rv.getRemainderPath());
            if (catObjectID != -1L)
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
    // Csws context
    protected final CswsContext llc;

    // This is the map from category ID to category path name.
    // It's keyed by a Long formed from the id, and has String values.
    protected final Map<Long,String> categoryPathMap = new HashMap<>();

    // This is the map from category ID to attribute names.  Keyed
    // by a Long formed from the id, and has a String[] value.
    protected final Map<Long,String[]> attributeMap = new HashMap<>();

    /** Constructor */
    public CategoryPathAccumulator(CswsContext llc)
    {
      this.llc = llc;
    }

    /** Get a specified set of packed category paths with attribute names, given the category identifiers */
    public String[] getCategoryPathsAttributeNames(long[] catIDs)
      throws ManifoldCFException, ServiceInterruption
    {
      Set<String> set = new HashSet<String>();
      for (long x : catIDs)
      {
        Long key = new Long(x);
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
    protected String findPath(long catID)
      throws ManifoldCFException, ServiceInterruption
    {
      return getObjectPath(llc.getObjectInformation(catID));
    }

    /** Get the complete path for an object.
    */
    protected String getObjectPath(ObjectInformation currentObject)
      throws ManifoldCFException, ServiceInterruption
    {
      String path = null;
      while (true)
      {
        if (currentObject.getWorkspaceName() != null) {
          return currentObject.getWorkspaceName() + ((path==null)?"":":" + path);
        }

        if (!currentObject.exists())
        {
          // The document identifier describes a path that does not exist.
          // This is unexpected, but an exception would terminate the job, and we don't want that.
          Logging.connectors.warn("Csws: Bad identifier found? "+currentObject.toString()+" apparently does not exist, but need to look up its path");
          return null;
        }

        // Get the name attribute
        String name = currentObject.getName();
        if (path == null)
          path = name;
        else
          path = name + "/" + path;

        // Get the parentID attribute
        if (currentObject.getParentId() == null) {
          // Oops, hit the top of the path without finding the workspace we're in.
          // No idea where it lives; note this condition and exit.
          Logging.connectors.warn("Csws: Object ID "+currentObject.toString()+" doesn't seem to live in enterprise or category workspace!  Path I got was '"+path+"'");
          return null;
        }
        long parentID = currentObject.getParentId().longValue();
        currentObject = llc.getObjectInformation(parentID);
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
    protected final CswsContext llc;
    protected final String workspaceName;
    protected ObjectInformation rootValue = null;
    protected final String remainderPath;

    /** Constructor.
    *@param pathString is the path string.
    */
    public RootValue(CswsContext llc, String pathString)
    {
      this.llc = llc;
      int colonPos = pathString.indexOf(":");
      if (colonPos == -1)
      {
        remainderPath = pathString;
        workspaceName = enterpriseWSName;
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
        rootValue = llc.getObjectInformation(workspaceName);
      }

      if (!rootValue.exists())
      {
        Logging.connectors.warn("Csws: Could not get workspace/volume ID!  Retrying...");
        // This cannot mean a real failure; it MUST mean that we have had an intermittent communication hiccup.  So, pass it off as a service interruption.
        throw new ServiceInterruption("Service interruption getting root value",new ManifoldCFException("Could not get workspace/volume id"),System.currentTimeMillis()+60000L,
          System.currentTimeMillis()+600000L,-1,true);
      }

      return rootValue;
    }
  }

  /** This thread performs a LAPI FetchVersion command, streaming the resulting
  * document back through a XThreadInputStream to the invoking thread.
  */
  protected class DocumentReadingThread extends Thread
  {

    protected Throwable exception = null;
    protected final long docID;
    protected final long versionNumber;
    protected final XThreadInputStream stream;

    public DocumentReadingThread(final long docID, final long versionNumber)
    {
      super();
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
        final XThreadOutputStream outputStream = new XThreadOutputStream(stream);
        try
        {
          cswsSession.getVersionContents(docID, versionNumber, outputStream);
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
      throws InterruptedException, ManifoldCFException, ServiceInterruption
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
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption) thr;
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
