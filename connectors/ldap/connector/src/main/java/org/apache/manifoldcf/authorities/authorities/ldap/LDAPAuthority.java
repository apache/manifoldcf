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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

import java.io.*;
import java.util.*;
import javax.naming.*;
import javax.naming.ldap.*;
import javax.naming.directory.*;
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
  
  /**
  * This is the active directory global deny token. This should be ingested
  * with all documents.
  */
  private static final String globalDenyToken = "DEAD_AUTHORITY";
  private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_UNREACHABLE);
  private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_USERNOTFOUND);

  private ConfigParams parameters;
  
  private String serverName;
  private String serverPort;
  private String serverBase;
  private String userBase;
  private String userSearch;
  private String groupBase;
  private String groupSearch;
  private String groupNameAttr;
    
  /**
   * Constructor.
   */
  public LDAPAuthority() {
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
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Session setup.  Anything that might need to throw an exception should go
  * here.
  */
  protected LdapContext getSession()
    throws ManifoldCFException
  {
    // We get the parameters here, so we can check them in case they are missing
    serverName = parameters.getParameter( "ldapServerName" );
    if (serverName == null || serverName.length() == 0)
      throw new ManifoldCFException("Server name parameter missing but required");
    serverPort = parameters.getParameter( "ldapServerPort" );
    if (serverPort == null || serverPort.length() == 0)
      throw new ManifoldCFException("Server port parameter missing but required");
    serverBase = parameters.getParameter( "ldapServerBase" );
    if (serverBase == null)
      throw new ManifoldCFException("Server base parameter missing but required");

    userBase = parameters.getParameter( "ldapUserBase" );
    if (userBase == null)
      throw new ManifoldCFException("User base parameter missing but required");
    userSearch = parameters.getParameter( "ldapUserSearch" );
    if (userSearch == null || userSearch.length() == 0)
      throw new ManifoldCFException("User search expression missing but required");
    groupBase = parameters.getParameter( "ldapGroupBase" );
    if (groupBase == null)
      throw new ManifoldCFException("Group base parameter missing but required");
    groupSearch = parameters.getParameter( "ldapGroupSearch" );
    if (groupSearch == null || groupSearch.length() == 0)
      throw new ManifoldCFException("Group search expression missing but required");
    groupNameAttr = parameters.getParameter( "ldapGroupNameAttr" );
    if (groupNameAttr == null || groupNameAttr.length() == 0)
      throw new ManifoldCFException("Group name attribute missing but required");

    Hashtable env = new Hashtable();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, "ldap://"+serverName+":"+serverPort+"/"+serverBase);

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
      throw new ManifoldCFException("Authentication error: "+e.getMessage(),e);
    } catch (CommunicationException e) {
      session = null;
      sessionExpirationTime = -1L;
      throw new ManifoldCFException("Communication error: "+e.getMessage(),e);
    } catch (NamingException e) {
      session = null;
      sessionExpirationTime = -1L;
      throw new ManifoldCFException("Naming error: "+e.getMessage(),e);
    }

  }
    
  /**
  * Check connection for sanity.
  */
  @Override
  public String check()
    throws ManifoldCFException {
    disconnectSession();
    LdapContext session = getSession();
    // MHL for a real check of all the search etc.
    return super.check();
  }

  /**
   * Poll. The connection should be closed if it has been idle for too long.
   */
  @Override
  public void poll()
    throws ManifoldCFException {
    if (session != null && System.currentTimeMillis() > sessionExpirationTime)
      disconnectSession();
    super.poll();
  }

  /** Disconnect a session.
  */
  protected void disconnectSession()
  {
    if (session != null) {
      try {
        session.close();
      } catch (NamingException e) {
        // Eat this error
      }
      session = null;
      sessionExpirationTime = -1L;
    }
    
    serverName = null;
    serverPort = null;
    serverBase = null;
    userBase = null;
    userSearch = null;
    groupBase = null;
    groupSearch = null;
    groupNameAttr = null;
  }
    
  /**
  * Close the connection. Call this before discarding the repository
  * connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException {
    disconnectSession();
    super.disconnect();
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
    LdapContext session = getSession();
    try {
      //Get DistinguishedName (for this method we are using DomainPart as a searchBase ie: DC=qa-ad-76,DC=metacarta,DC=com")
      String usrDN = getDistinguishedName(session, userName);
      if (usrDN == null) {
        return userNotFoundResponse;
      }

      //specify the LDAP search filter
      String searchFilter = groupSearch.replaceAll( "\\{0\\}", ldapEscape(usrDN));
      SearchControls searchCtls = new SearchControls();
      searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String returnedAtts[] = {groupNameAttr};
      searchCtls.setReturningAttributes(returnedAtts);

      //Search for tokens.  Since every user *must* have a SID, the "no user" detection should be safe.
      NamingEnumeration answer = session.search(groupBase, searchFilter, searchCtls);

      ArrayList theGroups = new ArrayList();
        
      while (answer.hasMoreElements()) {
        SearchResult sr = (SearchResult) answer.next();
        Attributes attrs = sr.getAttributes();
        if (attrs != null) {
          theGroups.add(attrs.get(groupNameAttr).get().toString());
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
      return userNotFoundResponse;
    } catch (NamingException e) {
      // Unreachable
      return unreachableResponse;
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
    return unreachableResponse;
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected.  That is why they receive a thread context argument.
  
  /**
  * Output the configuration header section. This method is called in the
  * head section of the connector's configuration page. Its purpose is to add
  * the required tabs to the list, and to output any javascript methods that
  * might be needed by the configuration editing HTML.
  *
  * @param threadContext is the local thread context.
  * @param out is the output to which any HTML should be sent.
  * @param parameters are the configuration parameters, as they currently
  * exist, for this connection being configured.
  * @param tabsArray is an array of tab names. Add to this array any tab
  * names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale,"LDAP.LDAP"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig() {\n"+
"  if (editconnection.ldapServerName.value.indexOf(\"/\") != -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerNameCannotIncludeSlash")+"\");\n"+
"    editconnection.ldapServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerPort.value != \"\" && !isInteger(editconnection.ldapServerPort.value)) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerPortMustBeAnInteger")+"\");\n"+
"    editconnection.ldapServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerBase.value.indexOf(\"/\") != -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerBaseCannotIncludeSlash")+"\");\n"+
"    editconnection.ldapServerBase.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapUserSearch.value != \"\" && editconnection.ldapUserSearch.value.indexOf(\"{0}\") == -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.UserSearchMustIncludeSubstitution")+"\");\n"+
"    editconnection.ldapUserSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapGroupSearch.value != \"\" && editconnection.ldapGroupSearch.value.indexOf(\"{0}\") == -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.GroupSearchMustIncludeSubstitution")+"\");\n"+
"    editconnection.ldapGroupSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+ 
"}\n"+ 
"\n"+
"function checkConfigForSave() {\n"+ 
"  if (editconnection.ldapServerName.value == \"\") {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerNameCannotBeBlank")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerPort.value == \"\") {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerPortCannotBeBlank")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapUserSearch.value == \"\") {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.UserSearchCannotBeBlank")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapUserSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapGroupSearch.value == \"\") {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.GroupSearchCannotBeBlank")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapGroupSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapGroupNameAttr.value == \"\") {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.GroupNameAttrCannotBeBlank")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapGroupNameAttr.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapUserSearch.value != \"\" && editconnection.ldapUserSearch.value.indexOf(\"{0}\") == -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.UserSearchMustIncludeSubstitution")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapUserSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapGroupSearch.value != \"\" && editconnection.ldapGroupSearch.value.indexOf(\"{0}\") == -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.GroupSearchMustIncludeSubstitution")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapGroupSearch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerPort.value != \"\" && !isInteger(editconnection.ldapServerPort.value)) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerPortMustBeAnInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerName.value.indexOf(\"/\") != -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerNameCannotIncludeSlash")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"LDAP.LDAP")+"\");\n"+
"    editconnection.ldapServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ldapServerBase.value.indexOf(\"/\") != -1) {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"LDAP.ServerBaseCannotIncludeSlash")+"\");\n"+
"    editconnection.ldapServerBase.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+ 
"}\n"+ 
"//-->\n"+
"</script>\n"
    );
  }

  /**
  * Output the configuration body section. This method is called in the body
  * section of the authority connector's configuration page. Its purpose is
  * to present the required form elements for editing. The coder can presume
  * that the HTML that is output from this configuration will be within
  * appropriate <html>, <body>, and <form> tags. The name of the form is
  * "editconnection".
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
    String serverName = getParam( parameters, "ldapServerName", "");
    String serverPort = getParam( parameters, "ldapServerPort", "389");
    String serverBase = getParam( parameters, "ldapServerBase", "");
    String userBase = getParam( parameters, "ldapUserBase", "ou=People" );
    String userSearch = getParam( parameters, "ldapUserSearch", "(&(objectClass=inetOrgPerson)(uid={0}))" );
    String groupBase = getParam( parameters, "ldapGroupBase", "ou=Groups" );
    String groupSearch = getParam( parameters, "ldapGroupSearch", "(&(objectClass=groupOfNames)(member={0}))" );
    String groupNameAttr = getParam( parameters, "ldapGroupNameAttr", "cn" );
        
    if (tabName.equals(Messages.getString(locale,"LDAP.LDAP"))) {
      out.print(
"<table class=\"displaytable\">\n"+
" <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
                    
" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerNameColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"ldapServerName\" value=\""+Encoder.attributeEscape(serverName)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerPortColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"5\" name=\"ldapServerPort\" value=\""+Encoder.attributeEscape(serverPort)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapServerBase\" value=\""+Encoder.attributeEscape(serverBase)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.UserSearchBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapUserBase\" value=\""+Encoder.attributeEscape(userBase)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.UserSearchFilterColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapUserSearch\" value=\""+Encoder.attributeEscape(userSearch)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupSearchBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupBase\" value=\""+Encoder.attributeEscape(groupBase)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupSearchFilterColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupSearch\" value=\""+Encoder.attributeEscape(groupSearch)+"\"/></td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupNameAttributeColon")+"</nobr></td>\n"+
"  <td class=\"value\"><input type=\"text\" size=\"64\" name=\"ldapGroupNameAttr\" value=\""+Encoder.attributeEscape(groupNameAttr)+"\"/></td>\n"+
" </tr>\n"+

"</table>\n"
      );
    } else {
      out.print( "<input type=\"hidden\" name=\"ldapServerName\" value=\""+Encoder.attributeEscape(serverName)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapServerPort\" value=\""+Encoder.attributeEscape(serverPort)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapServerBase\" value=\""+Encoder.attributeEscape(serverBase)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapUserBase\" value=\""+Encoder.attributeEscape(userBase)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapUserSearch\" value=\""+Encoder.attributeEscape(userSearch)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapGroupBase\" value=\""+Encoder.attributeEscape(groupBase)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapGroupSearch\" value=\""+Encoder.attributeEscape(groupSearch)+"\"/>\n");
      out.print( "<input type=\"hidden\" name=\"ldapGroupNameAttr\" value=\""+Encoder.attributeEscape(groupNameAttr)+"\"/>\n");
    }
  }

  private String getParam( ConfigParams parameters, String name, String def) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : def;
  }

  private String getViewParam( ConfigParams parameters, String name) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : "";
  }

  private boolean copyParam( IPostParameters variableContext, ConfigParams parameters, String name) {
    String val = variableContext.getParameter( name );
    if( val == null ){
      return false;
    }
    parameters.setParameter( name, val );
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
  * @return null if all is well, or a string error message if there is an
  * error that should prevent saving of the connection (and cause a
  * redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters)
    throws ManifoldCFException {
    copyParam(variableContext, parameters, "ldapServerName" );
    copyParam(variableContext, parameters, "ldapServerPort" );
    copyParam(variableContext, parameters, "ldapServerBase" );
    copyParam(variableContext, parameters, "ldapUserBase" );
    copyParam(variableContext, parameters, "ldapUserSearch" );
    copyParam(variableContext, parameters, "ldapGroupBase" );
    copyParam(variableContext, parameters, "ldapGroupSearch" );
    copyParam(variableContext, parameters, "ldapGroupNameAttr" );
    return null;
  }

  /**
  * View configuration. This method is called in the body section of the
  * authority connector's view configuration page. Its purpose is to present
  * the connection information to the user. The coder can presume that the
  * HTML that is output from this configuration will be within appropriate
  * <html> and <body> tags.
  *
  * @param threadContext is the local thread context.
  * @param out is the output to which any HTML should be sent.
  * @param parameters are the configuration parameters, as they currently
  * exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException {
    String serverName = getViewParam( parameters, "ldapServerName" );
    String serverPort = getViewParam( parameters, "ldapServerPort" );
    String serverBase = getViewParam( parameters, "ldapServerBase" );

    String userBase = getViewParam( parameters, "ldapUserBase" );
    String userSearch = getViewParam( parameters, "ldapUserSearch" );
    String groupBase = getViewParam( parameters, "ldapGroupBase" );
    String groupSearch = getViewParam( parameters, "ldapGroupSearch" );
    String groupNameAttr = getViewParam( parameters, "ldapGroupNameAttr" );
    out.print(
"<table class=\"displaytable\">\n"+
" <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
                    
" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerNameColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(serverName)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerPortColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(serverPort)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.LDAPServerBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(serverBase)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.UserSearchBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(userBase)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.UserSearchFilterColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(userSearch)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupSearchBaseColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(groupBase)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupSearchFilterColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(groupSearch)+"</td>\n"+
" </tr>\n"+

" <tr>\n"+
"  <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"LDAP.GroupNameAttributeColon")+"</nobr></td>\n"+
"  <td class=\"value\">"+Encoder.bodyEscape(groupNameAttr)+"</td>\n"+
" </tr>\n"+

"</table>\n"
    );
  }

  // Protected methods
  /**
  * Obtain the DistinguishedName for a given user logon name.
  *
  * @param ctx is the ldap context to use.
  * @param userName (Domain Logon Name) is the user name or identifier.
  * @param searchBase (Full Domain Name for the search ie:
  * DC=qa-ad-76,DC=metacarta,DC=com)
  * @return DistinguishedName for given domain user logon name. (Should
  * throws an exception if user is not found.)
  */
  protected String getDistinguishedName(LdapContext ctx, String userName)
    throws ManifoldCFException {
    String returnedAtts[] = {"dn"};
    String searchFilter = userSearch.replaceAll( "\\{0\\}", ldapEscape(userName));
    SearchControls searchCtls = new SearchControls();
    searchCtls.setReturningAttributes(returnedAtts);
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

    try {
      NamingEnumeration answer = ctx.search(userBase, searchFilter, searchCtls);
      while (answer.hasMoreElements()) {
        SearchResult sr = (SearchResult) answer.next();
        return sr.getNameInNamespace();
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
}
