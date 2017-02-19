/* $Id$ */
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.authorities.authorities.ldap;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.ManifoldCF;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.ui.util.Encoder;
import org.apache.manifoldcf.core.common.LDAPSSLSocketFactory;

/**
 * This is the Active Directory implementation of the IAuthorityConnector
 * interface. Access tokens for this connector are simple SIDs, except for the
 * "global deny" token, which is designed to allow the authority to shut off
 * access to all authorized documents when the user is unrecognized or the
 * domain controller does not respond.
 */
public class LDAPAuthority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector {

  public static final String _rcsid = "@(#)$Id$";

  /**
   * Session information for all DC's we talk with.
   */
  private LdapContext session = null;
  private StartTlsResponse tls = null;
  
  private long sessionExpirationTime = -1L;

  //private ConfigParams parameters;

  private String bindUser;
  private String bindPass;
  private String serverProtocol;
  private String serverName;
  private String serverPort;
  private String serverBase;
  private String userBase;
  private String userSearch;
  private String groupBase;
  private String groupSearch;
  private String groupNameAttr;
  private boolean groupMemberDN;
  private boolean addUserRecord;
  private List<String> forcedTokens;
  private String userNameAttr;
  private String sslKeystoreData;
  
  private IKeystoreManager sslKeystore;
  
  private long responseLifetime = 60000L; //60sec

  private int LRUsize = 1000;

  /**
   * Cache manager.
   */
  private ICacheManager cacheManager = null;

  /**
   * Constructor.
   */
  public LDAPAuthority() {
  }

  /**
   * Set thread context.
   */
  @Override
  public void setThreadContext(IThreadContext tc)
    throws ManifoldCFException {
    super.setThreadContext(tc);
    cacheManager = CacheManagerFactory.make(tc);
  }

  /**
   * Connect. The configuration parameters are included.
   *
   * @param configParams are the configuration parameters for this connection.
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    //parameters = configParams;

    // Credentials
    bindUser = configParams.getParameter("ldapBindUser");
    bindPass = configParams.getObfuscatedParameter("ldapBindPass");

    // We get the parameters here, so we can check them in case they are missing
    serverProtocol = configParams.getParameter("ldapProtocol");
    serverName = configParams.getParameter("ldapServerName");
    serverPort = configParams.getParameter("ldapServerPort");
    serverBase = configParams.getParameter("ldapServerBase");

    sslKeystoreData = configParams.getParameter("sslKeystore");
    
    userBase = configParams.getParameter("ldapUserBase");
    userSearch = configParams.getParameter("ldapUserSearch");
    groupBase = configParams.getParameter("ldapGroupBase");
    groupSearch = configParams.getParameter("ldapGroupSearch");
    groupNameAttr = configParams.getParameter("ldapGroupNameAttr");
    userNameAttr = configParams.getParameter("ldapUserNameAttr");
    groupMemberDN = "1".equals(getParam(configParams, "ldapGroupMemberDn", ""));
    addUserRecord = "1".equals(getParam(configParams, "ldapAddUserRecord", ""));

    forcedTokens = new ArrayList<String>();
    int i = 0;
    while (i < configParams.getChildCount()) {
      ConfigNode sn = configParams.getChild(i++);
      if (sn.getType().equals("access")) {
        String token = "" + sn.getAttributeValue("token");
        forcedTokens.add(token);
      }
    }
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!
  /**
   * Session setup. Anything that might need to throw an exception should go
   * here.
   */
  protected LdapContext getSession()
    throws ManifoldCFException {

    try {
      LDAPProtocolEnum ldapProtocol = retrieveLDAPProtocol();
      if (session == null) {
        if (serverName == null || serverName.length() == 0) {
          Logging.authorityConnectors.error("Server name parameter missing but required");
          throw new ManifoldCFException("Server name parameter missing but required");
        }
        if (serverPort == null || serverPort.length() == 0) {
          Logging.authorityConnectors.error("Server port parameter missing but required");
          throw new ManifoldCFException("Server port parameter missing but required");
        }
        if (serverBase == null) {
          Logging.authorityConnectors.error("Server base parameter missing but required");
          throw new ManifoldCFException("Server base parameter missing but required");
        }
        if (userBase == null) {
          Logging.authorityConnectors.error("User base parameter missing but required");
          throw new ManifoldCFException("User base parameter missing but required");
        }
        if (userSearch == null || userSearch.length() == 0) {
          Logging.authorityConnectors.error("User search expression missing but required");
          throw new ManifoldCFException("User search expression missing but required");
        }
        if (groupBase == null) {
          Logging.authorityConnectors.error("Group base parameter missing but required");
          throw new ManifoldCFException("Group base parameter missing but required");
        }
        if (groupSearch == null || groupSearch.length() == 0) {
          Logging.authorityConnectors.error("Group search expression missing but required");
          throw new ManifoldCFException("Group search expression missing but required");
        }
        if (groupNameAttr == null || groupNameAttr.length() == 0) {
          Logging.authorityConnectors.error("Group name attribute missing but required");
          throw new ManifoldCFException("Group name attribute missing but required");
        }
        if (userNameAttr == null || userNameAttr.length() == 0) {
          Logging.authorityConnectors.error("User name attribute missing but required");
          throw new ManifoldCFException("User name attribute missing but required");
        }

        if (sslKeystoreData != null) {
          sslKeystore = KeystoreManagerFactory.make("", sslKeystoreData);
        } else {
          sslKeystore = KeystoreManagerFactory.make("");
        }

        final Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + serverName + ":" + serverPort + "/" + serverBase);
        if (LDAPProtocolEnum.LDAPS.equals(ldapProtocol)) {
          // Set thread local for keystore stuff
          LDAPSSLSocketFactory.setSocketFactoryProducer(sslKeystore);
          env.put(Context.SECURITY_PROTOCOL, "ssl");
          env.put("java.naming.ldap.factory.socket", "org.apache.manifoldcf.core.common.LDAPSSLSocketFactory");
        }
        
        if (bindUser != null && !bindUser.isEmpty()) {
          env.put(Context.SECURITY_AUTHENTICATION, "simple");
          env.put(Context.SECURITY_PRINCIPAL, bindUser);
          env.put(Context.SECURITY_CREDENTIALS, bindPass);
        }

        Logging.authorityConnectors.info("LDAP Context environment properties: " + printLdapContextEnvironment(env));
        session = new InitialLdapContext(env, null);
        
        if (isLDAPTLS(ldapProtocol)) {
          // Start TLS
          StartTlsResponse tls = (StartTlsResponse) session.extendedOperation(new StartTlsRequest());
          tls.negotiate(sslKeystore.getSecureSocketFactory());
        }
        
      } else {
        if (isLDAPS(ldapProtocol)) {
          // Set thread local for keystore stuff
          LDAPSSLSocketFactory.setSocketFactoryProducer(sslKeystore);
        }
        session.reconnect(null);
      }
      sessionExpirationTime = System.currentTimeMillis() + 300000L;
      return session;
    } catch (AuthenticationException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.authorityConnectors.error("Authentication error: " + e.getMessage() + ", explanation: " + e.getExplanation(), e);
      throw new ManifoldCFException("Authentication error: " + e.getMessage() + ", explanation: " + e.getExplanation(), e);
    } catch (CommunicationException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.authorityConnectors.error("Communication error: " + e.getMessage(), e);
      throw new ManifoldCFException("Communication error: " + e.getMessage(), e);
    } catch (NamingException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.authorityConnectors.error("Naming exception: " + e.getMessage(), e);
      throw new ManifoldCFException("Naming exception: " + e.getMessage(), e);
    } catch (InterruptedIOException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.authorityConnectors.error("Interrupted IO error: " + e.getMessage());
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.authorityConnectors.error("IO error: " + e.getMessage(), e);
      throw new ManifoldCFException("IO error: " + e.getMessage(), e);
    }
  }

  /**
   * Retrieves LDAPProtocol from serverProtocol String
   *
   * @return LDAPProtocolEnum
   */
  private LDAPProtocolEnum retrieveLDAPProtocol() {
    if (serverProtocol == null || serverProtocol.length() == 0) {
      return  LDAPProtocolEnum.LDAP;
    }

    final LDAPProtocolEnum ldapProtocol;
    switch (serverProtocol.toUpperCase(Locale.ENGLISH)){
      case "LDAP":
        ldapProtocol = LDAPProtocolEnum.LDAP;
        break;
      case "LDAPS":
        ldapProtocol = LDAPProtocolEnum.LDAPS;
        break;
      case "LDAP+TLS":
        ldapProtocol = LDAPProtocolEnum.LDAP_TLS;
        break;
      case "LDAPS+TLS":
        ldapProtocol = LDAPProtocolEnum.LDAPS_TLS;
        break;
      default:
        ldapProtocol = LDAPProtocolEnum.LDAP;
    }
    return ldapProtocol;
  }

  /**
   * Checks whether TLS is enabled for given LDAP Protocol
   *
   * @param ldapProtocol to check
   * @return whether TLS is enabled or not
   */
  private boolean isLDAPTLS (LDAPProtocolEnum ldapProtocol){
    return LDAPProtocolEnum.LDAP_TLS.equals(ldapProtocol) || LDAPProtocolEnum.LDAPS_TLS.equals(ldapProtocol);
  }

  /**
   * Checks whether LDAPS or LDAPS with TLS is enabled for given LDAP Protocol
   *
   * @param ldapProtocol to check
   * @return whether LDAPS or LDAPS with TLS is enabled or not
   */
  private boolean isLDAPS (LDAPProtocolEnum ldapProtocol){
    return LDAPProtocolEnum.LDAPS.equals(ldapProtocol) || LDAPProtocolEnum.LDAPS_TLS.equals(ldapProtocol);
  }

  /**
   * Check connection for sanity.
   */
  @Override
  public String check()
    throws ManifoldCFException {
    disconnectSession();
    getSession();
    // MHL for a real check of all the search etc.
    return super.check();
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return session != null;
  }

  /**
   * Poll. The connection should be closed if it has been idle for too long.
   */
  @Override
  public void poll()
    throws ManifoldCFException {
    if (session != null && System.currentTimeMillis() > sessionExpirationTime) {
      disconnectSession();
    }
    super.poll();
  }

  /**
   * Disconnect a session.
   */
  protected void disconnectSession() {
    if (session != null) {
      try {
        if (tls != null)
          tls.close();
        session.close();
      } catch (NamingException e) {
        // Eat this error
      } catch (IOException e) {
        // Eat this error
      }
      tls = null;
      session = null;
      sessionExpirationTime = -1L;
    }
  }

  /**
   * Close the connection. Call this before discarding the repository connector.
   */
  @Override
  public void disconnect()
    throws ManifoldCFException {
    disconnectSession();
    super.disconnect();
    // Zero out all the stuff that we want to be sure we don't use again
    serverName = null;
    serverPort = null;
    serverBase = null;
    userBase = null;
    userSearch = null;
    groupBase = null;
    groupSearch = null;
    groupNameAttr = null;
    userNameAttr = null;
    forcedTokens = null;
    sslKeystoreData = null;
    sslKeystore = null;
  }

  protected String createCacheConnectionString() {
    StringBuilder sb = new StringBuilder();
    sb.append(serverName).append(":").append(serverPort).append("/").append(serverBase);
    return sb.toString();
  }

  protected String createUserSearchString() {
    StringBuilder sb = new StringBuilder();
    sb.append(userBase).append("|").append(userSearch).append("|").append(userNameAttr).append("|").append(addUserRecord ? 'Y' : 'N');
    return sb.toString();
  }

  protected String createGroupSearchString() {
    StringBuilder sb = new StringBuilder();
    sb.append(groupBase).append("|").append(groupSearch).append("|").append(groupNameAttr).append("|").append(groupMemberDN ? 'Y' : 'N');
    return sb.toString();
  }

  /**
   * Obtain the access tokens for a given user name.
   *
   * @param userName is the user name or identifier.
   * @return the response tokens (according to the current authority). (Should
   * throws an exception only when a condition cannot be properly described
   * within the authorization response object.)
   */
  @Override
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws ManifoldCFException {

    getSession();
    // Construct a cache description object
    ICacheDescription objectDescription = new LdapAuthorizationResponseDescription(userName,
      createCacheConnectionString(), createUserSearchString(), createGroupSearchString(), this.responseLifetime, this.LRUsize);

    // Enter the cache
    ICacheHandle ch = cacheManager.enterCache(new ICacheDescription[]{objectDescription}, null, null);
    try {
      ICacheCreateHandle createHandle = cacheManager.enterCreateSection(ch);
      try {
        // Lookup the object
        AuthorizationResponse response = (AuthorizationResponse) cacheManager.lookupObject(createHandle, objectDescription);
        if (response != null) {
          return response;
        }
        // Create the object.
        response = getAuthorizationResponseUncached(userName);
        // Save it in the cache
        cacheManager.saveObject(createHandle, objectDescription, response);
        // And return it...
        return response;
      } finally {
        cacheManager.leaveCreateSection(createHandle);
      }
    } finally {
      cacheManager.leaveCache(ch);
    }
  }

  protected AuthorizationResponse getAuthorizationResponseUncached(String userName)
    throws ManifoldCFException {
    getSession();
    try {
      //find user in LDAP tree
      SearchResult usrRecord = getUserEntry(session, userName);
      if (usrRecord == null) {
        return RESPONSE_USERNOTFOUND;
      }

      ArrayList theGroups = new ArrayList();
      theGroups.addAll(forcedTokens);

      String usrName = userName.split("@")[0];
      if (userNameAttr != null && !"".equals(userNameAttr)) {
        if (usrRecord.getAttributes() != null) {
          Attribute attr = usrRecord.getAttributes().get(userNameAttr);
          if (attr != null) {
            usrName = attr.get().toString();
            if (addUserRecord) {
              NamingEnumeration values = attr.getAll();
              while (values.hasMore()) {
                theGroups.add(values.next().toString());
              }
            }
          }
        }
      }

      if (groupSearch != null && !groupSearch.isEmpty()) {
        //specify the LDAP search filter
        String searchFilter = groupSearch.replaceAll("\\{0\\}", escapeLDAPSearchFilter(groupMemberDN ? usrRecord.getNameInNamespace() : usrName));
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String returnedAtts[] = {groupNameAttr};
        searchCtls.setReturningAttributes(returnedAtts);

        NamingEnumeration answer = session.search(groupBase, searchFilter, searchCtls);

        while (answer.hasMoreElements()) {
          SearchResult sr = (SearchResult) answer.next();
          Attributes attrs = sr.getAttributes();
          if (attrs != null) {
            NamingEnumeration values = attrs.get(groupNameAttr).getAll();
            while (values.hasMore()) {
              theGroups.add(values.next().toString());
            }
          }
        }
      }

      String[] tokens = new String[theGroups.size()];
      int k = 0;
      while (k < tokens.length) {
        tokens[k] = (String) theGroups.get(k);
        k++;
      }

      return new AuthorizationResponse(tokens, AuthorizationResponse.RESPONSE_OK);

    } catch (NameNotFoundException e) {
      // This means that the user doesn't exist
      Logging.authorityConnectors.error("User does not exists: "+ e.getMessage(), e);
      return RESPONSE_USERNOTFOUND;
    } catch (NamingException e) {
      // Unreachable
      Logging.authorityConnectors.error("Response Unreachable: "+ e.getMessage(), e);
      return RESPONSE_UNREACHABLE;
    }
  }

  /**
   * Obtain the default access tokens for a given user name.
   *
   * @param userName is the user name or identifier.
   * @return the default response tokens, presuming that the connect method
   * fails.
   */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName) {
    // The default response if the getConnection method fails
    return RESPONSE_UNREACHABLE;
  }

  /**
   * Stringifies LDAP Context environment variable
   * @param env LDAP Context environment variable
   * @return Stringified LDAP Context environment. Password is masked if set.
   */
  private String printLdapContextEnvironment(Hashtable env) {
    Hashtable copyEnv = new Hashtable<>(env);
    if (copyEnv.containsKey(Context.SECURITY_CREDENTIALS)){
      copyEnv.put(Context.SECURITY_CREDENTIALS, "********");
    }
    return Arrays.toString(copyEnv.entrySet().toArray());
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected.  That is why they receive a thread context argument.
  /**
   * Output the configuration header section. This method is called in the head
   * section of the connector's configuration page. Its purpose is to add the
   * required tabs to the list, and to output any javascript methods that might
   * be needed by the configuration editing HTML.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   * @param tabsArray is an array of tab names. Add to this array any tab names
   * that are specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "LDAP.LDAP"));
    tabsArray.add(Messages.getString(locale, "LDAP.ForcedTokens"));
    final Map<String,Object> paramMap = new HashMap<String,Object>();
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration.js", paramMap);    
  }

  /**
   * Output the configuration body section. This method is called in the body
   * section of the authority connector's configuration page. Its purpose is to
   * present the required form elements for editing. The coder can presume that
   * the HTML that is output from this configuration will be within appropriate
   * <html>, <body>, and <form> tags. The name of the form is "editconnection".
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   * @param tabName is the current tab name.
   */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException {
    final Map<String,Object> paramMap = new HashMap<String,Object>();
    paramMap.put("TabName",tabName);
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration_LDAP.html", paramMap);    
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration_ForcedTokens.html", paramMap);    
  }

  private boolean copyParam(IPostParameters variableContext, ConfigParams parameters, String name) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      return false;
    }
    parameters.setParameter(name, val);
    return true;
  }

  private void copyParam(IPostParameters variableContext, ConfigParams parameters, String name, String def) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      val = def;
    }
    parameters.setParameter(name, val);
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * authority connector's configuration page, whenever there is a possibility
   * that form data for a connection has been posted. Its purpose is to gather
   * form information and modify the configuration parameters accordingly. The
   * name of the posted form is "editconnection".
   *
   * @param threadContext is the local thread context.
   * @param variableContext is the set of variables available from the post,
   * including binary file post information.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   * @return null if all is well, or a string error message if there is an error
   * that should prevent saving of the connection (and cause a redirection to an
   * error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters)
    throws ManifoldCFException {
    copyParam(variableContext, parameters, "ldapProtocol");
    copyParam(variableContext, parameters, "ldapServerName");
    copyParam(variableContext, parameters, "ldapServerPort");
    copyParam(variableContext, parameters, "ldapServerBase");
    copyParam(variableContext, parameters, "ldapUserBase");
    copyParam(variableContext, parameters, "ldapUserSearch");
    copyParam(variableContext, parameters, "ldapUserNameAttr");
    copyParam(variableContext, parameters, "ldapGroupBase");
    copyParam(variableContext, parameters, "ldapGroupSearch");
    copyParam(variableContext, parameters, "ldapGroupNameAttr");

    copyParam(variableContext, parameters, "ldapGroupMemberDn", "0"); //checkbox boolean value
    copyParam(variableContext, parameters, "ldapAddUserRecord", "0"); //checkbox boolean value

    copyParam(variableContext, parameters, "ldapBindUser");
    final String bindPass = variableContext.getParameter("ldapBindPass");
    if (bindPass != null) {
      parameters.setObfuscatedParameter("ldapBindPass", variableContext.mapKeyToPassword(bindPass));
    }

    final String xc = variableContext.getParameter("tokencount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < parameters.getChildCount()) {
        ConfigNode sn = parameters.getChild(i);
        if (sn.getType().equals("access")) {
          parameters.removeChild(i);
        } else {
          i++;
        }
      }

      final int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        final String accessDescription = "_" + Integer.toString(i);
        final String accessOpName = "accessop" + accessDescription;
        final String command = variableContext.getParameter(accessOpName);
        if (command != null && command.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter("spectoken" + accessDescription);
        ConfigNode node = new ConfigNode("access");
        node.setAttribute("token", accessSpec);
        parameters.addChild(parameters.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add")) {
        String accessspec = variableContext.getParameter("spectoken");
        ConfigNode node = new ConfigNode("access");
        node.setAttribute("token", accessspec);
        parameters.addChild(parameters.getChildCount(), node);
      }
    }

    String sslKeystoreValue = variableContext.getParameter("sslkeystoredata");
    final String sslConfigOp = variableContext.getParameter("sslconfigop");
    if (sslConfigOp != null)
    {
      if (sslConfigOp.equals("Delete"))
      {
        final String alias = variableContext.getParameter("sslkeystorealias");
        final IKeystoreManager mgr;
        if (sslKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",sslKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        sslKeystoreValue = mgr.getString();
      }
      else if (sslConfigOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("sslcertificate");
        final IKeystoreManager mgr;
        if (sslKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("",sslKeystoreValue);
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
        sslKeystoreValue = mgr.getString();
      }
    }
    if (sslKeystoreValue != null)
      parameters.setParameter("sslkeystore",sslKeystoreValue);
    
    return null;
  }

  /**
   * View configuration. This method is called in the body section of the
   * authority connector's view configuration page. Its purpose is to present
   * the connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>
   * and <body> tags.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException {
    final Map<String,Object> paramMap = new HashMap<String,Object>();
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "viewConfiguration.html", paramMap);    
  }

  // Protected methods
  
  private static String getParam(final ConfigParams parameters, final String name, final String def) {
    String rval = parameters.getParameter(name);
    return rval != null ? rval : def;
  }

  /** Fill in ForcedTokens tab */
  protected static void fillInForcedTokensTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    final List<String> forcedTokenList = new ArrayList<String>();
    for (int i = 0; i < parameters.getChildCount(); i++) {
      final ConfigNode sn = parameters.getChild(i);
      if (sn.getType().equals("access")) {
        forcedTokenList.add(sn.getAttributeValue("token"));
      }
    }
    velocityContext.put("FORCEDTOKENS", forcedTokenList);
  }
  
  /** Fill in LDAP tab */
  protected static void fillInLDAPTab(Map<String,Object> velocityContext, IHTTPOutput out, ConfigParams parameters)
  {
    velocityContext.put("FSERVERPROTOCOL", getParam(parameters, "ldapProtocol", "ldap"));
    velocityContext.put("FSERVERNAME", getParam(parameters, "ldapServerName", ""));
    velocityContext.put("FSERVERPORT", getParam(parameters, "ldapServerPort", "389"));
    velocityContext.put("FSERVERBASE", getParam(parameters, "ldapServerBase", ""));
    String sslKeystoreData = parameters.getParameter("sslkeystore");
    if (sslKeystoreData != null)
      velocityContext.put("SSLKEYSTOREDATA", sslKeystoreData);
    velocityContext.put("FUSERBASE", getParam(parameters, "ldapUserBase", "ou=People"));
    velocityContext.put("FUSERSEARCH", getParam(parameters, "ldapUserSearch", "(&(objectClass=inetOrgPerson)(uid={0}))"));
    velocityContext.put("FUSERNAMEATTR", getParam(parameters, "ldapUserNameAttr", "uid"));
    velocityContext.put("FADDUSERRECORD", getParam(parameters, "ldapAddUserRecord", ""));
    velocityContext.put("FGROUPBASE", getParam(parameters, "ldapGroupBase", "ou=Groups"));
    velocityContext.put("FGROUPSEARCH", getParam(parameters, "ldapGroupSearch", "(&(objectClass=groupOfNames)(member={0}))"));
    velocityContext.put("FGROUPNAMEATTR", getParam(parameters, "ldapGroupNameAttr", "cn"));
    velocityContext.put("FGROUPMEMBERDN", getParam(parameters, "ldapGroupMemberDn", ""));
    velocityContext.put("FBINDUSER", getParam(parameters, "ldapBindUser", ""));
    String fBindPass = parameters.getObfuscatedParameter("ldapBindPass");
    if (fBindPass == null)
      fBindPass = "";
    else
      fBindPass = out.mapPasswordToKey(fBindPass);
    velocityContext.put("FBINDPASS", fBindPass);
    
    Map<String,String> sslCertificatesMap = null;
    String message = null;

    try {
      final IKeystoreManager localSslKeystore;
      if (sslKeystoreData == null)
        localSslKeystore = KeystoreManagerFactory.make("");
      else
        localSslKeystore = KeystoreManagerFactory.make("",sslKeystoreData);

      // List the individual certificates in the store, with a delete button for each
      final String[] contents = localSslKeystore.getContents();
      if (contents.length > 0)
      {
        sslCertificatesMap = new HashMap<>();
        for (final String alias : contents)
        {
          String description = localSslKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          sslCertificatesMap.put(alias, description);
        }
      }
    } catch (ManifoldCFException e) {
      message = e.getMessage();
      Logging.authorityConnectors.warn(e);
    }

    if(sslCertificatesMap != null)
      velocityContext.put("SSLCERTIFICATESMAP", sslCertificatesMap);
    if(message != null)
      velocityContext.put("MESSAGE", message);
  }

  /**
   * Obtain the user LDAP record for a given user logon name.
   *
   * @param ctx is the ldap context to use.
   * @param userName (Domain Logon Name) is the user name or identifier.
   * DC=qa-ad-76,DC=metacarta,DC=com)
   * @return SearchResult for given domain user logon name. (Should throws an
   * exception if user is not found.)
   */
  protected SearchResult getUserEntry(LdapContext ctx, String userName)
    throws ManifoldCFException {
    String searchFilter = userSearch.replaceAll("\\{0\\}", escapeDN(userName.split("@")[0]));
    SearchControls searchCtls = new SearchControls();
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

    try {
      NamingEnumeration answer = ctx.search(userBase, searchFilter, searchCtls);
      if (answer.hasMoreElements()) {
        return (SearchResult) answer.next();
      }
      return null;
    } catch (Exception e) {
      throw new ManifoldCFException(e.getMessage(), e);
    }
  }

  /**
   * LDAP escape a string.
   */
  protected static String ldapEscape(String input) {
    //Add escape sequence to all commas
    StringBuilder sb = new StringBuilder();
    int index = 0;
    while (true) {
      int oldIndex = index;
      index = input.indexOf(",", oldIndex);
      if (index == -1) {
        sb.append(input.substring(oldIndex));
        break;
      }
      sb.append(input.substring(oldIndex, index)).append("\\,");
      index++;
    }
    return sb.toString();
  }

  public static String escapeDN(String name) {
    StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
    if ((name.length() > 0) && ((name.charAt(0) == ' ') || (name.charAt(0) == '#'))) {
      sb.append('\\'); // add the leading backslash if needed
    }
    for (int i = 0; i < name.length(); i++) {
      char curChar = name.charAt(i);
      switch (curChar) {
        case '\\':
          sb.append("\\\\");
          break;
        case ',':
          sb.append("\\,");
          break;
        case '+':
          sb.append("\\+");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '<':
          sb.append("\\<");
          break;
        case '>':
          sb.append("\\>");
          break;
        case ';':
          sb.append("\\;");
          break;
        default:
          sb.append(curChar);
      }
    }
    if ((name.length() > 1) && (name.charAt(name.length() - 1) == ' ')) {
      sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
    }
    return sb.toString();
  }

  public static String escapeLDAPSearchFilter(String filter) {
    StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
    for (int i = 0; i < filter.length(); i++) {
      char curChar = filter.charAt(i);
      switch (curChar) {
        case '\\':
          sb.append("\\5c");
          break;
        case '*':
          sb.append("\\2a");
          break;
        case '(':
          sb.append("\\28");
          break;
        case ')':
          sb.append("\\29");
          break;
        case '\u0000':
          sb.append("\\00");
          break;
        default:
          sb.append(curChar);
      }
    }
    return sb.toString();
  }

  protected static StringSet emptyStringSet = new StringSet();

  /**
   * This is the cache object descriptor for cached access tokens from this
   * connector.
   */
  protected class LdapAuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription {

    /**
     * The user name
     */
    protected String userName;

    /**
     * LDAP connection string with server name and base DN
     */
    protected String connectionString;

    /**
     * User search definition
     */
    protected String userSearch;

    /**
     * Group search definition
     */
    protected String groupSearch;

    /**
     * The response lifetime
     */
    protected long responseLifetime;

    /**
     * The expiration time
     */
    protected long expirationTime = -1;

    /**
     * Constructor.
     */
    public LdapAuthorizationResponseDescription(String userName, String connectionString, String userSearch, String groupSearch, long responseLifetime, int LRUsize) {
      super("LDAPAuthority", LRUsize);
      this.userName = userName;
      this.connectionString = connectionString;
      this.userSearch = userSearch;
      this.groupSearch = groupSearch;
      this.responseLifetime = responseLifetime;
    }

    /**
     * Return the invalidation keys for this object.
     */
    public StringSet getObjectKeys() {
      return emptyStringSet;
    }

    /**
     * Get the critical section name, used for synchronizing the creation of the
     * object
     */
    public String getCriticalSectionName() {
      StringBuilder sb = new StringBuilder(getClass().getName());
      sb.append("-").append(userName).append("-").append(connectionString);
      return sb.toString();
    }

    /**
     * Return the object expiration interval
     */
    @Override
    public long getObjectExpirationTime(long currentTime) {
      if (expirationTime == -1) {
        expirationTime = currentTime + responseLifetime;
      }
      return expirationTime;
    }

    @Override
    public int hashCode() {
      return userName.hashCode() + connectionString.hashCode() + userSearch.hashCode() + groupSearch.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof LdapAuthorizationResponseDescription)) {
        return false;
      }
      LdapAuthorizationResponseDescription ard = (LdapAuthorizationResponseDescription) o;
      if (!ard.userName.equals(userName)) {
        return false;
      }
      if (!ard.connectionString.equals(connectionString)) {
        return false;
      }
      if (!ard.userSearch.equals(userSearch)) {
        return false;
      }
      if (!ard.groupSearch.equals(groupSearch)) {
        return false;
      }
      return true;
    }
  }
}
