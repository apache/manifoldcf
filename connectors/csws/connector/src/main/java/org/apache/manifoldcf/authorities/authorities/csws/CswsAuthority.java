/* $Id: CswsAuthority.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.authorities.csws;

import com.opentext.livelink.service.memberservice.MemberRight;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;

import org.apache.manifoldcf.connectorcommon.interfaces.*;

import com.opentext.livelink.service.memberservice.User;
import com.opentext.livelink.service.memberservice.Member;
import com.opentext.livelink.service.memberservice.MemberPrivileges;
import org.apache.manifoldcf.csws.*;

import java.io.*;
import java.util.*;

/** This is the Csws implementation of the IAuthorityConnector interface.
*
* Access tokens for livelink are simply user and usergroup node identifiers.  Therefore,
* this class retrieves those using the standard livelink call, being sure to map anything
* that looks like an active directory user name to something that looks like a Csws
* domain/username form.
*
*/
public class CswsAuthority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id: CswsAuthority.java 988245 2010-08-23 18:39:35Z kwright $";

  //Forward to the javascript to check the configuration parameters.
  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

  //Forward to the HTML template to edit the configuration parameters.
  private static final String EDIT_CONFIGURATION_SERVER_HTML = "editConfiguration_Server.html";
  private static final String EDIT_CONFIGURATION_CACHE_HTML = "editConfiguration_Cache.html";

  //Forward to the HTML template to view the configuration parameters.
  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";

  // Signal that we have set up connection parameters properly
  private boolean hasSessionParameters = false;
  // Session expiration time
  private long expirationTime = -1L;
  // Idle session expiration interval
  private final static long expirationInterval = 300000L;

  // Data from the parameters
  private String serverProtocol = null;
  private String serverName = null;
  private int serverPort = -1;
  private String serverUsername = null;
  private String serverPassword = null;
  private String authenticationServicePath = null;
  //private String documentManagementServicePath = null;
  //private String contentServiceServicePath = null;
  private String memberServiceServicePath = null;
  //private String searchServiceServicePath = null;
  private String serverHTTPNTLMDomain = null;
  private String serverHTTPNTLMUsername = null;
  private String serverHTTPNTLMPassword = null;
  private IKeystoreManager serverHTTPSKeystore = null;

  // Data required for maintaining Csws connection
  private CswsSession cswsSession = null;

  // Cache variables
  private String cacheLifetime = null;
  private String cacheLRUsize = null;
  private long responseLifetime = 60000L;
  private int LRUsize = 1000;

  /** Cache manager. */
  protected ICacheManager cacheManager = null;

  // Csws does not have "deny" permissions, and there is no such thing as a document with no tokens, so it is safe to not have a local "deny" token.
  // However, people feel that a suspenders-and-belt approach is called for, so this restriction has been added.
  // Csws tokens are numbers, "SYSTEM", or "GUEST", so they can't collide with the standard form.

  /** Constructor.
  */
  public CswsAuthority()
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
  }

  /** Initialize the parameters, including the ones needed for caching.
  */
  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (!hasSessionParameters)
    {
      // Server parameters
      serverProtocol = params.getParameter(CswsParameters.serverProtocol);
      serverName = params.getParameter(CswsParameters.serverName);
      String serverPortString = params.getParameter(CswsParameters.serverPort);
      serverUsername = params.getParameter(CswsParameters.serverUsername);
      serverPassword = params.getObfuscatedParameter(CswsParameters.serverPassword);
      authenticationServicePath = params.getParameter(CswsParameters.authenticationPath);
      //documentManagementServicePath = params.getParameter(CswsParameters.documentManagementPath);
      //contentServiceServicePath = params.getParameter(CswsParameters.contentServicePath);
      memberServiceServicePath = params.getParameter(CswsParameters.memberServicePath);
      //searchServiceServicePath = params.getParameter(CswsParameters.searchServicePath);
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

      cacheLifetime = params.getParameter(CswsParameters.cacheLifetime);
      if (cacheLifetime == null)
        cacheLifetime = "1";
      cacheLRUsize = params.getParameter(CswsParameters.cacheLRUSize);
      if (cacheLRUsize == null)
        cacheLRUsize = "1000";

      try
      {
        responseLifetime = Long.parseLong(this.cacheLifetime) * 60L * 1000L;
        LRUsize = Integer.parseInt(this.cacheLRUsize);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Cache lifetime or Cache LRU size must be an integer: "+e.getMessage(),e);
      }

      if (Logging.authorityConnectors.isDebugEnabled())
      {
        String passwordExists = (serverPassword!=null && serverPassword.length() > 0)?"password exists":"";
        Logging.authorityConnectors.debug("Csws: Csws connection parameters: Server='"+serverName+"'; port='"+serverPort+"'; user name='"+serverUsername+"'; "+passwordExists);
      }
      hasSessionParameters = true;
    }
  }

  /** Set up a session.
  */
  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
  {
    getSessionParameters();
    if (cswsSession == null)
    {
      // Construct the various URLs we need
      final String baseURL = serverProtocol + "://" + serverName + ":" + serverPort;
      final String authenticationServiceURL = baseURL + authenticationServicePath;
      //final String documentManagementServiceURL = baseURL + documentManagementServicePath;
      //final String contentServiceServiceURL = baseURL + contentServiceServicePath;
      final String memberServiceServiceURL = baseURL + memberServiceServicePath;
      //final String searchServiceServiceURL = baseURL + searchServiceServicePath;

      if (Logging.authorityConnectors.isDebugEnabled())
      {
        Logging.authorityConnectors.debug("Csws: Csws session created.");
      }

      // Construct a new csws session object for setting up this session
      cswsSession = new CswsSession(serverUsername, serverPassword, serverHTTPSKeystore, 1000L * 60L * 15L,
        authenticationServiceURL, null, null, memberServiceServiceURL, null);

    }
    expirationTime = System.currentTimeMillis() + expirationInterval;
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Check connection for sanity.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      // Reestablish the session
      cswsSession = null;
      getSession();

      final User user = cswsSession.getUserByLoginName(this.serverUsername);
      if (user != null) {
        return super.check();
      }
      return "Connection failed: User authentication failed";
    }
    catch (ServiceInterruption e)
    {
      return "Temporary service interruption: "+e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      return "Connection failed: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    if (cswsSession == null)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= expirationTime)
    {
      expirationTime = -1L;
      cswsSession = null;
    }
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return cswsSession != null;
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    hasSessionParameters = false;
    cswsSession = null;
    expirationTime = -1L;

    serverProtocol = null;
    serverName = null;
    serverPort = -1;
    serverUsername = null;
    serverPassword = null;
    authenticationServicePath = null;
    //documentManagementServicePath = null;
    //contentServiceServicePath = null;
    memberServiceServicePath = null;
    //searchServiceServicePath = null;
    serverHTTPNTLMDomain = null;
    serverHTTPNTLMUsername = null;
    serverHTTPNTLMPassword = null;
    serverHTTPSKeystore = null;

    cacheLifetime = null;
    cacheLRUsize = null;

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
    // We need the session parameters here
    getSessionParameters();

    // Construct a cache description object
    ICacheDescription objectDescription = new AuthorizationResponseDescription(userName,
      serverProtocol,serverName,serverPort,
      serverUsername,serverPassword,
      authenticationServicePath, memberServiceServicePath,
      serverHTTPSKeystore,
      responseLifetime,LRUsize);

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

  /** Uncached method to get access tokens for a user name. */
  protected AuthorizationResponse getAuthorizationResponseUncached(String userName)
    throws ManifoldCFException
  {
    try
    {
      getSession();
      // First, do what's necessary to map the user name that comes in to a reasonable
      // Csws domain\\user combination.

      if (Logging.authorityConnectors.isDebugEnabled())
      {
        Logging.authorityConnectors.debug("Authentication user name = '"+userName+"'");
      }

      String domainAndUser = userName;

      if (Logging.authorityConnectors.isDebugEnabled())
      {
        Logging.authorityConnectors.debug("Csws: Csws user name = '"+domainAndUser+"'");
      }

      ArrayList list = new ArrayList();

      // Find out if the specified user is a member of the Guest group, or is a member
      // of the System group.
      // Get information about the current user.  This is how we will determine if the
      // user exists, and also what permissions s/he has.
      final User user = cswsSession.getUserByLoginName(domainAndUser);
      if (user == null) {
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Csws: Csws user '"+domainAndUser+"' does not exist");
        return RESPONSE_USERNOTFOUND;
      }

      if (user.isDeleted())
      {
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Csws: Csws user '"+domainAndUser+"' has been deleted");
        // Since the user cannot become undeleted, then this should be treated as 'user does not exist'.
        return RESPONSE_USERNOTFOUND;
      }

      final MemberPrivileges memberPrivileges = user.getPrivileges();
      if (memberPrivileges.isPublicAccessEnabled()) {
        // if ((privs & LAPI_USERS.PRIV_PERM_WORLD) == LAPI_USERS.PRIV_PERM_WORLD) ??
        list.add("GUEST");
      }

      if (memberPrivileges. isCanAdministerSystem()) {
        // if ((privs & LAPI_USERS.PRIV_PERM_BYPASS) == LAPI_USERS.PRIV_PERM_BYPASS)
        list.add("SYSTEM");
      }

      final Member member = cswsSession.getMemberByLoginName(domainAndUser);
      if (member == null) {
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Csws: Csws member '"+domainAndUser+"' does not exist");
        return RESPONSE_USERNOTFOUND;
      }

      final List<? extends MemberRight> memberRights = cswsSession.listRightsByMemberId(member.getID());
      if (memberRights == null)
      {
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Csws: Csws error looking up user rights for '"+domainAndUser+"' - user does not exist");
        return RESPONSE_USERNOTFOUND;
      }

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
      // These objects are returned by the corresponding GetObjectRights() call made during
      // the ingestion process.  We have to figure out how to map these to things that are
      // the equivalent of acls.

      // Idea:
      // 1) RIGHT_WORLD is based on some property of the user.
      // 2) RIGHT_SYSTEM is based on some property of the user.
      // 3) RIGHT_OWNER and RIGHT_GROUP are managed solely in the ingestion side of the world.

      // NOTE:  It turns out that -1 and -2 are in fact returned as part of the list of
      // rights requested above.  They get mapped to special keywords already in the above
      // code, so it *may* be reasonable to filter them from here.  It's not a real problem because
      // it's effectively just a duplicate of what we are doing.

      final String[] rval = new String[memberRights.size()];
      int j = 0;
      for (final MemberRight memberRight : memberRights) {
        rval[j++] = Long.toString(memberRight.getID());
      }

      return new AuthorizationResponse(rval,AuthorizationResponse.RESPONSE_OK);
    }
    catch (ServiceInterruption e)
    {
      Logging.authorityConnectors.warn("Csws: Server seems to be down: "+e.getMessage(),e);
      return RESPONSE_UNREACHABLE;
    }
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    // The default response if the getConnection method fails
    return RESPONSE_UNREACHABLE;
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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"CswsConnector.Server"));
    tabsArray.add(Messages.getString(locale,"CswsConnector.Cache"));

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_JS, null, true);
  }

  /** Output the configuration body section.
  * This method is called in the body section of the authority connector's configuration page.  Its purpose is to present the required form elements for editing.
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
    velocityContext.put("TabName",tabName);

    fillInServerTab(velocityContext, out, parameters);
    fillInCacheTab(velocityContext, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_SERVER_HTML, velocityContext);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_CACHE_HTML, velocityContext);
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
    String memberServiceServicePath = parameters.getParameter(CswsParameters.memberServicePath);
    if (memberServiceServicePath == null)
      memberServiceServicePath = CswsParameters.memberServicePathDefault;

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
      Logging.authorityConnectors.warn(e);
    }

    velocityContext.put("SERVERPROTOCOL",serverProtocol);
    velocityContext.put("SERVERNAME",serverName);
    velocityContext.put("SERVERPORT",serverPort);
    velocityContext.put("SERVERUSERNAME",serverUserName);
    velocityContext.put("SERVERPASSWORD",serverPassword);

    velocityContext.put("AUTHENTICATIONSERVICEPATH", authenticationServicePath);
    velocityContext.put("MEMBERSERVICESERVICEPATH", memberServiceServicePath);

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

  /** Fill in Cache tab */
  private void fillInCacheTab(Map<String, Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    String cacheLifetime = parameters.getParameter(CswsParameters.cacheLifetime);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    String cacheLRUsize = parameters.getParameter(CswsParameters.cacheLRUSize);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";

    velocityContext.put("CACHELIFETIME",cacheLifetime);
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
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
    String memberServiceServicePath = variableContext.getParameter("memberserviceservicepath");
    if (memberServiceServicePath != null)
      parameters.setParameter(CswsParameters.memberServicePath, memberServiceServicePath);

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

    // Cache parameters
    String cacheLifetime = variableContext.getParameter("cachelifetime");
    if (cacheLifetime != null)
      parameters.setParameter(CswsParameters.cacheLifetime,cacheLifetime);
    String cacheLRUsize = variableContext.getParameter("cachelrusize");
    if (cacheLRUsize != null)
      parameters.setParameter(CswsParameters.cacheLRUSize,cacheLRUsize);

    return null;
  }

  /** View configuration.
  * This method is called in the body section of the authority connector's view configuration page.  Its purpose is to present the connection information to the user.
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

  protected static StringSet emptyStringSet = new StringSet();

  /** This is the cache object descriptor for cached access tokens from
  * this connector.
  */
  protected static class AuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    /** The user name associated with the access tokens */
    protected final String userName;

    // The server connection parameters
    protected final String serverProtocol;
    protected final String serverName;
    protected final int serverPort;
    protected final String serverUsername;
    protected final String serverPassword;
    protected final String authenticationServicePath;
    protected final String memberServicePath;
    protected final String serverHTTPSKeystore;

    protected long responseLifetime;

    /** The expiration time */
    protected long expirationTime = -1;

    /** Constructor. */
    public AuthorizationResponseDescription(String userName,
      String serverProtocol,
      String serverName, int serverPort,
      String serverUsername, String serverPassword,
      String authenticationServicePath, String memberServicePath,
      IKeystoreManager serverHTTPSKeystore,
      long responseLifetime, int LRUsize)
      throws ManifoldCFException
    {
      super("CswsAuthority",LRUsize);
      this.userName = userName;

      this.serverProtocol = serverProtocol;
      this.serverName = serverName;
      this.serverPort = serverPort;
      this.serverUsername = serverUsername;
      this.serverPassword = serverPassword;
      this.authenticationServicePath = authenticationServicePath;
      this.memberServicePath = memberServicePath;
      if (serverHTTPSKeystore != null)
        this.serverHTTPSKeystore = serverHTTPSKeystore.getString();
      else
        this.serverHTTPSKeystore = null;
      this.responseLifetime = responseLifetime;
    }

    /** Return the invalidation keys for this object. */
    public StringSet getObjectKeys()
    {
      return emptyStringSet;
    }

    /** Get the critical section name, used for synchronizing the creation of the object */
    public String getCriticalSectionName()
    {
      return getClass().getName() + "-" + userName + "-" + serverProtocol + "-" + serverName +
        "-" + Integer.toString(serverPort) + "-" + serverUsername + "-" + serverPassword +
        "-" + authenticationServicePath + "-" + memberServicePath +
        "-" + ((serverHTTPSKeystore==null)?"":serverHTTPSKeystore);
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
      return userName.hashCode() +
        serverProtocol.hashCode() + serverName.hashCode() + new Integer(serverPort).hashCode() +
        serverUsername.hashCode() + serverPassword.hashCode() +
        authenticationServicePath.hashCode() + memberServicePath.hashCode() +
        ((serverHTTPSKeystore==null)?0:serverHTTPSKeystore.hashCode());
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof AuthorizationResponseDescription))
        return false;
      AuthorizationResponseDescription ard = (AuthorizationResponseDescription)o;
      return ard.userName.equals(userName) &&
        ard.serverProtocol.equals(serverProtocol) && ard.serverName.equals(serverName) && ard.serverPort == serverPort &&
        ard.serverUsername.equals(serverUsername) && ard.serverPassword.equals(serverPassword) &&
        ard.authenticationServicePath.equals(authenticationServicePath) &&
        ard.memberServicePath.equals(memberServicePath) &&
        ((ard.serverHTTPSKeystore != null && serverHTTPSKeystore != null && ard.serverHTTPSKeystore.equals(serverHTTPSKeystore)) ||
          ((ard.serverHTTPSKeystore == null || serverHTTPSKeystore == null) && ard.serverHTTPSKeystore == serverHTTPSKeystore));
    }

  }

}


