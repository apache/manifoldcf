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
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.ui.util.Encoder;

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

  private long sessionExpirationTime = -1L;

  private ConfigParams parameters;

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
    parameters = configParams;

    // We get the parameters here, so we can check them in case they are missing
    serverName = configParams.getParameter("ldapServerName");
    serverPort = configParams.getParameter("ldapServerPort");
    serverBase = configParams.getParameter("ldapServerBase");

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
    while (i < parameters.getChildCount()) {
      ConfigNode sn = parameters.getChild(i++);
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
    if (serverName == null || serverName.length() == 0) {
      throw new ManifoldCFException("Server name parameter missing but required");
    }
    if (serverPort == null || serverPort.length() == 0) {
      throw new ManifoldCFException("Server port parameter missing but required");
    }
    if (serverBase == null) {
      throw new ManifoldCFException("Server base parameter missing but required");
    }
    if (userBase == null) {
      throw new ManifoldCFException("User base parameter missing but required");
    }
    if (userSearch == null || userSearch.length() == 0) {
      throw new ManifoldCFException("User search expression missing but required");
    }
    if (groupBase == null) {
      throw new ManifoldCFException("Group base parameter missing but required");
    }
    if (groupSearch == null || groupSearch.length() == 0) {
      throw new ManifoldCFException("Group search expression missing but required");
    }
    if (groupNameAttr == null || groupNameAttr.length() == 0) {
      throw new ManifoldCFException("Group name attribute missing but required");
    }
    if (userNameAttr == null || userNameAttr.length() == 0) {
      throw new ManifoldCFException("User name attribute missing but required");
    }

    Hashtable env = new Hashtable();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, "ldap://" + serverName + ":" + serverPort + "/" + serverBase);

    //get bind credentials
    String bindUser = getParam(parameters, "ldapBindUser", "");
    String bindPass = "";
    try {
      bindPass = ManifoldCF.deobfuscate(getParam(parameters, "ldapBindPass", ""));
    } catch (ManifoldCFException ex) {
      if (!bindUser.isEmpty()) {
        Logger.getLogger(LDAPAuthority.class.getName()).log(Level.SEVERE, "Deobfuscation error", ex);
      }
    }
    if (!bindUser.isEmpty()) {
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, bindUser);
      env.put(Context.SECURITY_CREDENTIALS, bindPass);
    }

    try {
      if (session == null) {
        session = new InitialLdapContext(env, null);
      } else {
        session.reconnect(null);
      }
      sessionExpirationTime = System.currentTimeMillis() + 300000L;
      return session;
    } catch (AuthenticationException e) {
      session = null;
      sessionExpirationTime = -1L;
      throw new ManifoldCFException("Authentication error: " + e.getMessage() + ", explanation: " + e.getExplanation(), e);
    } catch (CommunicationException e) {
      session = null;
      sessionExpirationTime = -1L;
      throw new ManifoldCFException("Communication error: " + e.getMessage(), e);
    } catch (NamingException e) {
      session = null;
      sessionExpirationTime = -1L;
      throw new ManifoldCFException("Naming error: " + e.getMessage(), e);
    }
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
        session.close();
      } catch (NamingException e) {
        // Eat this error
      }
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
      return RESPONSE_USERNOTFOUND;
    } catch (NamingException e) {
      // Unreachable
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
    tabsArray.add(Messages.getString(locale, "LDAP.ForcedTokens"));
    tabsArray.add(Messages.getString(locale, "LDAP.LDAP"));
    out.print(
      "<script type=\"text/javascript\">\n"
      + "<!--\n"
      + "function checkConfig() {\n"
      + "  if (editconnection.ldapServerName.value.indexOf(\"/\") != -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerNameCannotIncludeSlash") + "\");\n"
      + "    editconnection.ldapServerName.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerPort.value != \"\" && !isInteger(editconnection.ldapServerPort.value)) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerPortMustBeAnInteger") + "\");\n"
      + "    editconnection.ldapServerPort.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerBase.value.indexOf(\"/\") != -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerBaseCannotIncludeSlash") + "\");\n"
      + "    editconnection.ldapServerBase.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapUserSearch.value != \"\" && editconnection.ldapUserSearch.value.indexOf(\"{0}\") == -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.UserSearchMustIncludeSubstitution") + "\");\n"
      + "    editconnection.ldapUserSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapGroupSearch.value != \"\" && editconnection.ldapGroupSearch.value.indexOf(\"{0}\") == -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.GroupSearchMustIncludeSubstitution") + "\");\n"
      + "    editconnection.ldapGroupSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  return true;\n"
      + "}\n"
      + "\n"
      + "function checkConfigForSave() {\n"
      + "  if (editconnection.ldapServerName.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerNameCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapServerName.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerPort.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerPortCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapServerPort.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapUserSearch.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.UserSearchCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapUserSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapGroupSearch.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.GroupSearchCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapGroupSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapGroupNameAttr.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.GroupNameAttrCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapGroupNameAttr.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapUserSearch.value != \"\" && editconnection.ldapUserSearch.value.indexOf(\"{0}\") == -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.UserSearchMustIncludeSubstitution") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapUserSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapGroupSearch.value != \"\" && editconnection.ldapGroupSearch.value.indexOf(\"{0}\") == -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.GroupSearchMustIncludeSubstitution") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapGroupSearch.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerPort.value != \"\" && !isInteger(editconnection.ldapServerPort.value)) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerPortMustBeAnInteger") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapServerPort.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerName.value.indexOf(\"/\") != -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerNameCannotIncludeSlash") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "LDAP.LDAP") + "\");\n"
      + "    editconnection.ldapServerName.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  if (editconnection.ldapServerBase.value.indexOf(\"/\") != -1) {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.ServerBaseCannotIncludeSlash") + "\");\n"
      + "    editconnection.ldapServerBase.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  return true;\n"
      + "}\n"
      + "function SpecOp(n, opValue, anchorvalue) {\n"
      + "  eval(\"editconnection.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"
      + "  postFormSetAnchor(anchorvalue);\n"
      + "}\n"
      + "function SpecAddToken(anchorvalue) {\n"
      + "  if (editconnection.spectoken.value == \"\")\n"
      + "  {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "LDAP.TypeInToken") + "\");\n"
      + "    editconnection.spectoken.focus();\n"
      + "    return;\n"
      + "  }\n"
      + "  SpecOp(\"accessop\",\"Add\",anchorvalue);\n"
      + "}\n"
      + "//-->\n"
      + "</script>\n");
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
    String fServerName = getParam(parameters, "ldapServerName", "");
    String fServerPort = getParam(parameters, "ldapServerPort", "389");
    String fServerBase = getParam(parameters, "ldapServerBase", "");

    String fUserBase = getParam(parameters, "ldapUserBase", "ou=People");
    String fUserSearch = getParam(parameters, "ldapUserSearch", "(&(objectClass=inetOrgPerson)(uid={0}))");
    String fUserNameAttr = getParam(parameters, "ldapUserNameAttr", "uid");
    boolean fAddUserRecord = "1".equals(getParam(parameters, "ldapAddUserRecord", ""));

    String fGroupBase = getParam(parameters, "ldapGroupBase", "ou=Groups");
    String fGroupSearch = getParam(parameters, "ldapGroupSearch", "(&(objectClass=groupOfNames)(member={0}))");
    String fGroupNameAttr = getParam(parameters, "ldapGroupNameAttr", "cn");
    boolean fGroupMemberDN = "1".equals(getParam(parameters, "ldapGroupMemberDn", ""));

    String fBindUser = getParam(parameters, "ldapBindUser", "");
    String fBindPass = "";
    try {
      fBindPass = ManifoldCF.deobfuscate(getParam(parameters, "ldapBindPass", ""));
    } catch (ManifoldCFException ex) {
      //ignore
    }
    fBindPass = out.mapPasswordToKey(fBindPass);

    if (tabName.equals(Messages.getString(locale, "LDAP.LDAP"))) {
      out.print(
        "<table class=\"displaytable\">\n"
        + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerNameColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"ldapServerName\" value=\"" + Encoder.attributeEscape(fServerName) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerPortColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"5\" name=\"ldapServerPort\" value=\"" + Encoder.attributeEscape(fServerPort) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerBaseColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapServerBase\" value=\"" + Encoder.attributeEscape(fServerBase) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPBindUserColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapBindUser\" value=\"" + Encoder.attributeEscape(fBindUser) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPBindPasswordColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"password\" size=\"64\" name=\"ldapBindPass\" value=\"" + Encoder.attributeEscape(fBindPass) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserSearchBaseColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapUserBase\" value=\"" + Encoder.attributeEscape(fUserBase) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserSearchFilterColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapUserSearch\" value=\"" + Encoder.attributeEscape(fUserSearch) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.AddUserAuthColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"checkbox\" value=\"1\" name=\"ldapAddUserRecord\" " + (fAddUserRecord ? "checked=\"true\"" : "") + "/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserNameAttrColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapUserNameAttr\" value=\"" + Encoder.attributeEscape(fUserNameAttr) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupSearchBaseColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupBase\" value=\"" + Encoder.attributeEscape(fGroupBase) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupSearchFilterColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupSearch\" value=\"" + Encoder.attributeEscape(fGroupSearch) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupNameAttributeColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupNameAttr\" value=\"" + Encoder.attributeEscape(fGroupNameAttr) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupMemberDnColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"checkbox\" value=\"1\" name=\"ldapGroupMemberDn\" " + (fGroupMemberDN ? "checked=\"true\"" : "") + "/></td>\n"
        + " </tr>\n"
        + "</table>\n");
    } else {
      out.print("<input type=\"hidden\" name=\"ldapServerName\" value=\"" + Encoder.attributeEscape(fServerName) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapServerPort\" value=\"" + Encoder.attributeEscape(fServerPort) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapServerBase\" value=\"" + Encoder.attributeEscape(fServerBase) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapBindUser\" value=\"" + Encoder.attributeEscape(fBindUser) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapBindPass\" value=\"" + Encoder.attributeEscape(fBindPass) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapUserBase\" value=\"" + Encoder.attributeEscape(fUserBase) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapUserSearch\" value=\"" + Encoder.attributeEscape(fUserSearch) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapGroupBase\" value=\"" + Encoder.attributeEscape(fGroupBase) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapGroupSearch\" value=\"" + Encoder.attributeEscape(fGroupSearch) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapGroupNameAttr\" value=\"" + Encoder.attributeEscape(fGroupNameAttr) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapUserNameAttr\" value=\"" + Encoder.attributeEscape(fUserNameAttr) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapAddUserRecord\" value=\"" + (fAddUserRecord ? "1" : "0") + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"ldapGroupMemberDn\" value=\"" + (fGroupMemberDN ? "1" : "0") + "\"/>\n");
    }

    if (tabName.equals(Messages.getString(locale, "LDAP.ForcedTokens"))) {
      out.print(
        "<table class=\"displaytable\">\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + "  <tr><td class=\"value\" colspan=\"2\">" + Messages.getBodyString(locale, "LDAP.ForcedTokensDisclaimer") + "</td></tr>\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");

      out.print("  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");
      // Go through forced ACL
      int i = 0;
      int k = 0;
      while (i < parameters.getChildCount()) {
        ConfigNode sn = parameters.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String accessOpName = "accessop" + accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\">\n"
            + "      <input type=\"hidden\" name=\"" + accessOpName + "\" value=\"\"/>\n"
            + "      <input type=\"hidden\" name=\"" + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n"
            + "      <a name=\"" + "token_" + Integer.toString(k) + "\">\n"
            + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "LDAP.Delete") + "\" onClick='Javascript:SpecOp(\"" + accessOpName + "\",\"Delete\",\"token_" + Integer.toString(k) + "\")' alt=\"" + Messages.getAttributeString(locale, "LDAP.DeleteToken") + Integer.toString(k) + "\"/>\n"
            + "      </a>&nbsp;\n"
            + "    </td>\n"
            + "    <td class=\"value\">\n"
            + "      " + Encoder.bodyEscape(token) + "\n"
            + "    </td>\n"
            + "  </tr>\n");
          k++;
        }
      }
      if (k == 0) {
        out.print(
          "  <tr>\n"
          + "    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale, "LDAP.NoTokensPresent") + "</td>\n"
          + "  </tr>\n");
      }
      out.print(
        "  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"
        + "  <tr>\n"
        + "    <td class=\"description\">\n"
        + "      <input type=\"hidden\" name=\"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n"
        + "      <input type=\"hidden\" name=\"accessop\" value=\"\"/>\n"
        + "      <a name=\"" + "token_" + Integer.toString(k) + "\">\n"
        + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "LDAP.Add") + "\" onClick='Javascript:SpecAddToken(\"token_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "LDAP.AddToken") + "\"/>\n"
        + "      </a>&nbsp;\n"
        + "    </td>\n"
        + "    <td class=\"value\">\n"
        + "      <input type=\"text\" size=\"30\" name=\"spectoken\" value=\"\"/>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>\n");
    } else {
      // Finally, go through forced ACL
      int i = 0;
      int k = 0;
      while (i < parameters.getChildCount()) {
        ConfigNode sn = parameters.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String token = "" + sn.getAttributeValue("token");
          out.print(
            "<input type=\"hidden\" name=\"" + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n");
          k++;
        }
      }
      out.print("<input type=\"hidden\" name=\"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n");
    }
  }

  private String getParam(ConfigParams parameters, String name, String def) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : def;
  }

  private String getViewParam(ConfigParams parameters, String name) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : "";
  }

  private boolean copyParam(IPostParameters variableContext, ConfigParams parameters, String name) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      return false;
    }
    parameters.setParameter(name, val);
    return true;
  }

  private boolean copyParam(IPostParameters variableContext, ConfigParams parameters, String name, String def) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      val = def;
    }
    parameters.setParameter(name, val);
    return true;
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
    String bindPass = variableContext.getParameter("ldapBindPass");
    if (bindPass != null) {
      parameters.setObfuscatedParameter("ldapBindPass", variableContext.mapKeyToPassword(bindPass));
    }

    String xc = variableContext.getParameter("tokencount");
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

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String accessDescription = "_" + Integer.toString(i);
        String accessOpName = "accessop" + accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
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
    String f_serverName = getViewParam(parameters, "ldapServerName");
    String f_serverPort = getViewParam(parameters, "ldapServerPort");
    String f_serverBase = getViewParam(parameters, "ldapServerBase");
    String f_bindUser = getViewParam(parameters, "ldapBindUser");

    String f_userBase = getViewParam(parameters, "ldapUserBase");
    String f_userSearch = getViewParam(parameters, "ldapUserSearch");
    String f_groupBase = getViewParam(parameters, "ldapGroupBase");
    String f_groupSearch = getViewParam(parameters, "ldapGroupSearch");
    String f_groupNameAttr = getViewParam(parameters, "ldapGroupNameAttr");

    String f_userNameAttr = getViewParam(parameters, "ldapUserNameAttr");
    boolean f_groupMemberDN = "1".equals(getViewParam(parameters, "ldapGroupMemberDn"));
    boolean f_addUserRecord = "1".equals(getViewParam(parameters, "ldapAddUserRecord"));

    out.print(
      "<table class=\"displaytable\">\n"
      + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerNameColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_serverName) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerPortColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_serverPort) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPServerBaseColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_serverBase) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPBindUserColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_bindUser) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.LDAPBindPasswordColon") + "</nobr></td>\n"
      + "  <td class=\"value\">*******</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserSearchBaseColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_userBase) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserSearchFilterColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_userSearch) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.AddUserAuthColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + (f_addUserRecord ? "Y" : "N") + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.UserNameAttrColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_userNameAttr) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupSearchBaseColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_groupBase) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupSearchFilterColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_groupSearch) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupNameAttributeColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(f_groupNameAttr) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.GroupMemberDnColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + (f_groupMemberDN ? "Y" : "N") + "</td>\n"
      + " </tr>\n");

    out.print("  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n");
    boolean seenAny = false;
    int i;

    // Go through looking for access tokens
    i = 0;
    while (i < parameters.getChildCount()) {
      ConfigNode sn = parameters.getChild(i++);
      if (sn.getType().equals("access")) {
        if (seenAny == false) {
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "LDAP.ForcedTokensColon") + "</nobr></td>\n"
            + "    <td class=\"value\">\n");
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(Encoder.bodyEscape(token) + "<br/>\n");
      }
    }

    if (seenAny) {
      out.print(
        "    </td>\n"
        + "  </tr>\n");
    } else {
      out.print(
        "  <tr><td class=\"message\" colspan=\"4\"><nobr>" + Messages.getBodyString(locale, "LDAP.NoTokensSpecified") + "</nobr></td></tr>\n");
    }
    out.print("</table>\n");
  }

  // Protected methods
  /**
   * Obtain the user LDAP record for a given user logon name.
   *
   * @param ctx is the ldap context to use.
   * @param userName (Domain Logon Name) is the user name or identifier.
   * @param searchBase (Full Domain Name for the search ie:
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
