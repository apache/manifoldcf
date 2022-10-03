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
package org.apache.manifoldcf.authorities.mappers.ldap;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;

import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.connectorcommon.interfaces.IKeystoreManager;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.common.LDAPSSLSocketFactory;
import org.apache.manifoldcf.core.interfaces.ConfigNode;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IDFactory;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

/**
 * This is the ldap mapper implementation, which uses a ldap association to manipulate a user name.
 */
public class LDAPMapper extends org.apache.manifoldcf.authorities.mappers.BaseMappingConnector {
  public static final String _rcsid = "@(#)$Id$";

  // Match map for username
  private LdapContext session = null;
  private StartTlsResponse tls = null;

  private long sessionExpirationTime = -1L;

  // private ConfigParams parameters;

  private String bindUser;
  private String bindPass;
  private String serverProtocol;
  private String serverName;
  private String serverPort;
  private String serverBase;
  private String userBase;
  private String userSearch;
  private String ldapUserNameReplace;
  private String ldapAttributeCondition;
  private String ldapAttributeRegexpCondition;
  private Pattern ldapAttributeRegexpConditionPattern;
  private String ldapUserNameReplaceTrue;
  private List<String> forcedTokens;
  private String userNameAttr;
  private String sslKeystoreData;

  private IKeystoreManager sslKeystore;

  private final long responseLifetime = 60000L; // 60sec

  private final int LRUsize = 1000;

  /**
   * Constructor.
   */
  public LDAPMapper() {
  }

  @Override
  public String check() throws ManifoldCFException {
    disconnectSession();
    getSession();
    // MHL for a real check of all the search etc.
    return super.check();
  }

  /**
   * This method is called to assess whether to count this connector instance should actually be counted as being connected.
   *
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected() {
    return session != null;
  }

  /**
   * Poll. The connection should be closed if it has been idle for too long.
   */
  @Override
  public void poll() throws ManifoldCFException {
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
      } catch (final NamingException e) {
        // Eat this error
      } catch (final IOException e) {
        // Eat this error
      }
      tls = null;
      session = null;
      sessionExpirationTime = -1L;
    }
  }

  /**
   * Close the connection. Call this before discarding the mapping connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    disconnectSession();
    super.disconnect();
    // Zero out all the stuff that we want to be sure we don't use again
    serverName = null;
    serverPort = null;
    serverBase = null;
    userBase = null;
    userSearch = null;
    userNameAttr = null;
    ldapUserNameReplace = null;
    ldapAttributeCondition = null;
    ldapAttributeRegexpCondition = null;
    ldapAttributeRegexpConditionPattern = null;
    ldapUserNameReplaceTrue = null;
    forcedTokens = null;
    sslKeystoreData = null;
    sslKeystore = null;
  }

  /**
   * Retrieves LDAPProtocol from serverProtocol String
   *
   * @return LDAPProtocolEnum
   */
  private LDAPProtocolEnum retrieveLDAPProtocol() {
    if (serverProtocol == null || serverProtocol.length() == 0) {
      return LDAPProtocolEnum.LDAP;
    }

    final LDAPProtocolEnum ldapProtocol;
    switch (serverProtocol.toUpperCase(Locale.ENGLISH)) {
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
  private boolean isLDAPTLS(final LDAPProtocolEnum ldapProtocol) {
    return LDAPProtocolEnum.LDAP_TLS.equals(ldapProtocol) || LDAPProtocolEnum.LDAPS_TLS.equals(ldapProtocol);
  }

  /**
   * Checks whether LDAPS or LDAPS with TLS is enabled for given LDAP Protocol
   *
   * @param ldapProtocol to check
   * @return whether LDAPS or LDAPS with TLS is enabled or not
   */
  private boolean isLDAPS(final LDAPProtocolEnum ldapProtocol) {
    return LDAPProtocolEnum.LDAPS.equals(ldapProtocol) || LDAPProtocolEnum.LDAPS_TLS.equals(ldapProtocol);
  }

  /**
   * Stringifies LDAP Context environment variable
   *
   * @param env LDAP Context environment variable
   * @return Stringified LDAP Context environment. Password is masked if set.
   */
  private String printLdapContextEnvironment(final Hashtable env) {
    final Hashtable copyEnv = new Hashtable<>(env);
    if (copyEnv.containsKey(Context.SECURITY_CREDENTIALS)) {
      copyEnv.put(Context.SECURITY_CREDENTIALS, "********");
    }
    return Arrays.toString(copyEnv.entrySet().toArray());
  }

  /**
   * Connect. The configuration parameters are included.
   *
   * @param configParams are the configuration parameters for this connection.
   */
  @Override
  public void connect(final ConfigParams configParams) {
    super.connect(configParams);
    // parameters = configParams;

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

    userNameAttr = configParams.getParameter("ldapUserNameAttr");
    ldapAttributeCondition = configParams.getParameter("ldapAttributeCondition");
    ldapAttributeRegexpCondition = configParams.getParameter("ldapAttributeRegexpCondition");

    ldapUserNameReplaceTrue = configParams.getParameter("ldapUserNameReplaceTrue");
    ldapUserNameReplace = configParams.getParameter("ldapUserNameReplace");
    if (ldapAttributeCondition != null) {
      ldapAttributeRegexpConditionPattern = Pattern.compile(ldapAttributeRegexpCondition);
    }

    forcedTokens = new ArrayList<String>();
    int i = 0;
    while (i < configParams.getChildCount()) {
      final ConfigNode sn = configParams.getChild(i++);
      if (sn.getType().equals("access")) {
        final String token = "" + sn.getAttributeValue("token");
        forcedTokens.add(token);
      }
    }
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!
  /**
   * Session setup. Anything that might need to throw an exception should go here.
   */
  protected LdapContext getSession() throws ManifoldCFException {

    try {
      final LDAPProtocolEnum ldapProtocol = retrieveLDAPProtocol();
      if (session == null) {
        if (serverName == null || serverName.length() == 0) {
          Logging.mappingConnectors.error("Server name parameter missing but required");
          throw new ManifoldCFException("Server name parameter missing but required");
        }
        if (serverPort == null || serverPort.length() == 0) {
          Logging.mappingConnectors.error("Server port parameter missing but required");
          throw new ManifoldCFException("Server port parameter missing but required");
        }
        if (serverBase == null) {
          Logging.mappingConnectors.error("Server base parameter missing but required");
          throw new ManifoldCFException("Server base parameter missing but required");
        }
        if (userBase == null) {
          Logging.mappingConnectors.error("User base parameter missing but required");
          throw new ManifoldCFException("User base parameter missing but required");
        }
        if (userSearch == null || userSearch.length() == 0) {
          Logging.mappingConnectors.error("User search expression missing but required");
          throw new ManifoldCFException("User search expression missing but required");
        }
        /*
         * parameters not mandatory if (ldapAttributeCondition == null || ldapAttributeCondition.length() == 0) { Logging.mappingConnectors.error("User attribute condition missing but required");
         * throw new ManifoldCFException("User attribute condition missing but required"); } if (ldapAttributeRegexpCondition == null || ldapAttributeRegexpCondition.length() == 0) {
         * Logging.mappingConnectors.error("User attribute regexp condition missing but required"); throw new ManifoldCFException("User attribute regexp condition missing but required"); } if
         * (ldapUserNameReplaceTrue == null || ldapUserNameReplaceTrue.length() == 0) { Logging.mappingConnectors.error("ldap attribute replace true missing but required"); throw new
         * ManifoldCFException("ldap attribute replace if True missing but required"); }
         */
        if (ldapUserNameReplace == null || ldapUserNameReplace.length() == 0) {
          Logging.mappingConnectors.error("User name replace attribute missing but required");
          throw new ManifoldCFException("User name replace attribute missing but required");
        }
        if (userNameAttr == null || userNameAttr.length() == 0) {
          Logging.mappingConnectors.error("User name attribute missing but required");
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

        Logging.mappingConnectors.info("LDAP Context environment properties: " + printLdapContextEnvironment(env));
        session = new InitialLdapContext(env, null);

        if (isLDAPTLS(ldapProtocol)) {
          // Start TLS
          final StartTlsResponse tls = (StartTlsResponse) session.extendedOperation(new StartTlsRequest());
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
    } catch (final AuthenticationException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.mappingConnectors.error("Authentication error: " + e.getMessage() + ", explanation: " + e.getExplanation(), e);
      throw new ManifoldCFException("Authentication error: " + e.getMessage() + ", explanation: " + e.getExplanation(), e);
    } catch (final CommunicationException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.mappingConnectors.error("Communication error: " + e.getMessage(), e);
      throw new ManifoldCFException("Communication error: " + e.getMessage(), e);
    } catch (final NamingException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.mappingConnectors.error("Naming exception: " + e.getMessage(), e);
      throw new ManifoldCFException("Naming exception: " + e.getMessage(), e);
    } catch (final InterruptedIOException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.mappingConnectors.error("Interrupted IO error: " + e.getMessage());
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
    } catch (final IOException e) {
      session = null;
      sessionExpirationTime = -1L;
      Logging.mappingConnectors.error("IO error: " + e.getMessage(), e);
      throw new ManifoldCFException("IO error: " + e.getMessage(), e);
    }
  }

  /**
   * Obtain the user LDAP record for a given user logon name.
   *
   * @param ctx      is the ldap context to use.
   * @param userName (Domain Logon Name) is the user name or identifier. DC=qa-ad-76,DC=metacarta,DC=com)
   * @return SearchResult for given domain user logon name. (Should throws an exception if user is not found.)
   */
  protected SearchResult getUserEntry(final LdapContext ctx, final String userName) throws ManifoldCFException {
    final String searchFilter = userSearch.replaceAll("\\{0\\}", escapeDN(userName));
    final SearchControls searchCtls = new SearchControls();
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    final String[] attrIds = new String[4];
    attrIds[0] = userNameAttr;
    attrIds[1] = ldapUserNameReplace;
    if (ldapAttributeCondition != null && ldapAttributeCondition.length() > 0) {
      attrIds[2] = ldapAttributeCondition;
      attrIds[3] = ldapUserNameReplaceTrue;
    }
    searchCtls.setReturningAttributes(attrIds);

    try {
      final NamingEnumeration answer = ctx.search(userBase, searchFilter, searchCtls);
      if (answer.hasMoreElements()) {
        return (SearchResult) answer.next();
      }
      return null;
    } catch (final Exception e) {
      throw new ManifoldCFException(e.getMessage(), e);
    }
  }

  public static String escapeDN(final String name) {
    final StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
    if ((name.length() > 0) && ((name.charAt(0) == ' ') || (name.charAt(0) == '#'))) {
      sb.append('\\'); // add the leading backslash if needed
    }
    for (int i = 0; i < name.length(); i++) {
      final char curChar = name.charAt(i);
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

  /**
   * Map an input user name to an output name.
   *
   * @param userName is the name to map
   * @return the mapped user name
   */
  @Override
  public String mapUser(final String userName) throws ManifoldCFException {
    final LdapContext lcontext = getSession();

    try {
      final String searchFilter = userSearch.replaceAll("\\{0\\}", escapeDN(userName));
      if (Logging.mappingConnectors.isDebugEnabled())
        Logging.mappingConnectors.debug("SearchFilter=" + searchFilter);

      final SearchResult answer = getUserEntry(lcontext, userName);

      if (answer != null) {
        if (Logging.mappingConnectors.isDebugEnabled())
          Logging.mappingConnectors.debug("Found answer=" + answer.toString());
        final Attributes attrs = answer.getAttributes();
        String outputUserName = null;
        Attribute attrReplace = null;
        /* by default */
        attrReplace = attrs.get(ldapUserNameReplace);

        if (ldapAttributeCondition != null && ldapAttributeCondition.length() > 0) {
          final Attribute attrCondition = attrs.get(ldapAttributeCondition);
          if (attrCondition != null) {
            final String valueCondition = (String) attrCondition.get(0);
            final Matcher matcher = ldapAttributeRegexpConditionPattern.matcher(valueCondition);
            if (matcher.find()) {
              attrReplace = attrs.get(ldapUserNameReplaceTrue);
              if (Logging.mappingConnectors.isDebugEnabled())
                Logging.mappingConnectors.debug("LDAPMapper: condition matches");
            } else {
              if (Logging.mappingConnectors.isDebugEnabled())
                Logging.mappingConnectors.debug("LDAPMapper: condition not matches");
            }
          }
        }
        outputUserName = (String) attrReplace.get(0);

        if (Logging.mappingConnectors.isDebugEnabled())
          Logging.mappingConnectors.debug("LDAPMapper: Input user name '" + userName + "'; output user name '" + outputUserName + "'");

        return outputUserName;
      } else {
        if (Logging.mappingConnectors.isDebugEnabled())
          Logging.mappingConnectors.debug("No Found answer for userName =" + userName);
        return userName;
      }
    } catch (final Exception e) {
      Logging.mappingConnectors.error("replace error for user : '" + userName + "' error :" + e.getMessage());
      throw new ManifoldCFException("replace error: " + e.getMessage(), e);
    }
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected. That is why they receive a thread context argument.

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

    tabsArray.add(Messages.getString(locale, "LDAP.LDAPMapperTab"));
    Logging.mappingConnectors.info("LDAP.LDAPMapperTab=" + Messages.getString(locale, "LDAP.LDAPMapperTab"));
    tabsArray.add(Messages.getString(locale, "LDAP.ForcedTokens"));
    final Map<String, Object> paramMap = new HashMap<String, Object>();
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration.js", paramMap);
    Messages.outputResourceWithVelocity(out, locale, "configuration.css", paramMap);

  }

  private static String getParam(final ConfigParams parameters, final String name, final String def) {
    final String rval = parameters.getParameter(name);
    return rval != null ? rval : def;
  }

  /** Fill in ForcedTokens tab */
  protected static void fillInForcedTokensTab(final Map<String, Object> velocityContext, final IHTTPOutput out, final ConfigParams parameters) {
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
  protected static void fillInLDAPTab(final Map<String, Object> velocityContext, final IHTTPOutput out, final ConfigParams parameters) {
    velocityContext.put("FSERVERPROTOCOL", getParam(parameters, "ldapProtocol", "ldap"));
    velocityContext.put("FSERVERNAME", getParam(parameters, "ldapServerName", ""));
    velocityContext.put("FSERVERPORT", getParam(parameters, "ldapServerPort", "389"));
    velocityContext.put("FSERVERBASE", getParam(parameters, "ldapServerBase", ""));
    final String sslKeystoreData = parameters.getParameter("sslkeystore");
    if (sslKeystoreData != null)
      velocityContext.put("SSLKEYSTOREDATA", sslKeystoreData);
    velocityContext.put("FUSERBASE", getParam(parameters, "ldapUserBase", "ou=People"));
    velocityContext.put("FUSERSEARCH", getParam(parameters, "ldapUserSearch", "(&(objectClass=inetOrgPerson)(mail={0}))"));
    velocityContext.put("FUSERNAMEATTR", getParam(parameters, "ldapUserNameAttr", "mail"));
    velocityContext.put("FUSERNAMEREPLACE", getParam(parameters, "ldapUserNameReplace", "cn"));
    velocityContext.put("ATTRIBUTECONDITION", getParam(parameters, "ldapAttributeCondition", "displayName"));
    velocityContext.put("ATTRIBUTEREGEXPCONDITION", getParam(parameters, "ldapAttributeRegexpCondition", "^a.*$"));
    velocityContext.put("FUSERNAMETRUEREPLACE", getParam(parameters, "ldapUserNameReplaceTrue", "givenName"));

    velocityContext.put("FBINDUSER", getParam(parameters, "ldapBindUser", ""));
    String fBindPass = parameters.getObfuscatedParameter("ldapBindPass");
    if (fBindPass == null)
      fBindPass = "";
    else
      fBindPass = out.mapPasswordToKey(fBindPass);
    velocityContext.put("FBINDPASS", fBindPass);

    Map<String, String> sslCertificatesMap = null;
    String message = null;

    try {
      final IKeystoreManager localSslKeystore;
      if (sslKeystoreData == null)
        localSslKeystore = KeystoreManagerFactory.make("");
      else
        localSslKeystore = KeystoreManagerFactory.make("", sslKeystoreData);

      // List the individual certificates in the store, with a delete button for each
      final String[] contents = localSslKeystore.getContents();
      if (contents.length > 0) {
        sslCertificatesMap = new HashMap<>();
        for (final String alias : contents) {
          String description = localSslKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0, 125) + "...";
          sslCertificatesMap.put(alias, description);
        }
      }
    } catch (final ManifoldCFException e) {
      message = e.getMessage();
      Logging.mappingConnectors.warn(e);
    }

    if (sslCertificatesMap != null)
      velocityContext.put("SSLCERTIFICATESMAP", sslCertificatesMap);
    if (message != null)
      velocityContext.put("MESSAGE", message);
  }

  /**
   * Output the configuration body section. This method is called in the body section of the authority connector's configuration page. Its purpose is to present the required form elements for editing.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags. The name of the form is "editconnection".
   *
   * @param threadContext is the local thread context.
   * @param out           is the output to which any HTML should be sent.
   * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
   * @param tabName       is the current tab name.
   */
  @Override
  public void outputConfigurationBody(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale, final ConfigParams parameters, final String tabName)
      throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration_LDAP.html", paramMap);
    Messages.outputResourceWithVelocity(out, locale, "editConfiguration_ForcedTokens.html", paramMap);

  }

  private boolean copyParam(final IPostParameters variableContext, final ConfigParams parameters, final String name) {
    final String val = variableContext.getParameter(name);
    if (val == null) {
      return false;
    }
    parameters.setParameter(name, val);
    return true;
  }

  /**
   * Process a configuration post. This method is called at the start of the authority connector's configuration page, whenever there is a possibility that form data for a connection has been posted.
   * Its purpose is to gather form information and modify the configuration parameters accordingly. The name of the posted form is "editconnection".
   *
   * @param threadContext   is the local thread context.
   * @param variableContext is the set of variables available from the post, including binary file post information.
   * @param parameters      are the configuration parameters, as they currently exist, for this connection being configured.
   * @return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
   */
  @Override
  public String processConfigurationPost(final IThreadContext threadContext, final IPostParameters variableContext, final Locale locale, final ConfigParams parameters) throws ManifoldCFException {
    copyParam(variableContext, parameters, "ldapProtocol");
    copyParam(variableContext, parameters, "ldapServerName");
    copyParam(variableContext, parameters, "ldapServerPort");
    copyParam(variableContext, parameters, "ldapServerBase");
    copyParam(variableContext, parameters, "ldapUserBase");
    copyParam(variableContext, parameters, "ldapUserSearch");
    copyParam(variableContext, parameters, "ldapUserNameAttr");
    copyParam(variableContext, parameters, "ldapUserNameReplace");
    copyParam(variableContext, parameters, "ldapAttributeCondition");
    copyParam(variableContext, parameters, "ldapAttributeRegexpCondition");
    copyParam(variableContext, parameters, "ldapUserNameReplaceTrue");

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
        final ConfigNode sn = parameters.getChild(i);
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
        final String accessSpec = variableContext.getParameter("spectoken" + accessDescription);
        final ConfigNode node = new ConfigNode("access");
        node.setAttribute("token", accessSpec);
        parameters.addChild(parameters.getChildCount(), node);
        i++;
      }

      final String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add")) {
        final String accessspec = variableContext.getParameter("spectoken");
        final ConfigNode node = new ConfigNode("access");
        node.setAttribute("token", accessspec);
        parameters.addChild(parameters.getChildCount(), node);
      }
    }

    String sslKeystoreValue = variableContext.getParameter("sslkeystoredata");
    final String sslConfigOp = variableContext.getParameter("sslconfigop");
    if (sslConfigOp != null) {
      if (sslConfigOp.equals("Delete")) {
        final String alias = variableContext.getParameter("sslkeystorealias");
        final IKeystoreManager mgr;
        if (sslKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("", sslKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        sslKeystoreValue = mgr.getString();
      } else if (sslConfigOp.equals("Add")) {
        final String alias = IDFactory.make(threadContext);
        final byte[] certificateValue = variableContext.getBinaryBytes("sslcertificate");
        final IKeystoreManager mgr;
        if (sslKeystoreValue != null)
          mgr = KeystoreManagerFactory.make("", sslKeystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        final java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try {
          mgr.importCertificate(alias, is);
        } catch (final Throwable e) {
          certError = e.getMessage();
        } finally {
          try {
            is.close();
          } catch (final IOException e) {
            // Eat this exception
          }
        }

        if (certError != null) {
          return "Illegal certificate: " + certError;
        }
        sslKeystoreValue = mgr.getString();
      }
    }
    if (sslKeystoreValue != null)
      parameters.setParameter("sslkeystore", sslKeystoreValue);

    return null;
  }

  /**
   * View configuration. This method is called in the body section of the authority connector's view configuration page. Its purpose is to present the connection information to the user. The coder can
   * presume that the HTML that is output from this configuration will be within appropriate &lt;html&gt; and &lt;body&gt; tags.
   *
   * @param threadContext is the local thread context.
   * @param out           is the output to which any HTML should be sent.
   * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
   */
  @Override
  public void viewConfiguration(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale, final ConfigParams parameters) throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<String, Object>();
    fillInLDAPTab(paramMap, out, parameters);
    fillInForcedTokensTab(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "configuration.css", paramMap);
    Messages.outputResourceWithVelocity(out, locale, "viewConfiguration.html", paramMap);
  }

}
