/* $Id: ActiveDirectoryAuthority.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.authorities.activedirectory;

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


/** This is the Active Directory implementation of the IAuthorityConnector interface.
* Access tokens for this connector are simple SIDs, except for the "global deny" token, which
* is designed to allow the authority to shut off access to all authorized documents when the
* user is unrecognized or the domain controller does not respond.
*/
public class ActiveDirectoryAuthority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id: ActiveDirectoryAuthority.java 988245 2010-08-23 18:39:35Z kwright $";

  // Data from the parameters
  private String domainControllerName = null;
  private String userName = null;
  private String password = null;
  private String authentication = null;
  private String userACLsUsername = null;
  private String cacheLifetime = null;
  private String cacheLRUsize = null;
  private long responseLifetime = 60000L;
  private int LRUsize = 1000;


  /** Cache manager. */
  private ICacheManager cacheManager = null;
  
  /** The initialized LDAP context (which functions as a session) */
  private LdapContext ctx = null;
  /** The time of last access to this ctx object */
  private long expiration = -1L;
  
  /** The length of time in milliseconds that the connection remains idle before expiring.  Currently 5 minutes. */
  private static final long expirationInterval = 300000L;
  
  /** This is the active directory global deny token.  This should be ingested with all documents. */
  private static final String globalDenyToken = "DEAD_AUTHORITY";
  
  private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_UNREACHABLE);
  private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_USERNOTFOUND);
  
  /** Constructor.
  */
  public ActiveDirectoryAuthority()
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

    // First, create server object (llServer)
    domainControllerName = configParams.getParameter(ActiveDirectoryConfig.PARAM_DOMAINCONTROLLER);
    userName = configParams.getParameter(ActiveDirectoryConfig.PARAM_USERNAME);
    password = configParams.getObfuscatedParameter(ActiveDirectoryConfig.PARAM_PASSWORD);
    authentication = configParams.getParameter(ActiveDirectoryConfig.PARAM_AUTHENTICATION);
    userACLsUsername = configParams.getParameter(ActiveDirectoryConfig.PARAM_USERACLsUSERNAME);
    if (userACLsUsername == null)
      userACLsUsername = "sAMAccountName";
    cacheLifetime = configParams.getParameter(ActiveDirectoryConfig.PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    cacheLRUsize = configParams.getParameter(ActiveDirectoryConfig.PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";    
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Check connection for sanity.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    getSession();
    return super.check();
  }

  /** Poll.  The connection should be closed if it has been idle for too long.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    if (expiration != -1L && System.currentTimeMillis() > expiration)
      closeConnection();
    super.poll();
  }
  
  /** Close the connection handle, but leave the info around if we open it again. */
  protected void closeConnection()
  {
    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (NamingException e)
      {
        // Eat this error
      }
      ctx = null;
      expiration = -1L;
    }
  }
  
  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    closeConnection();
    domainControllerName = null;
    userName = null;
    password = null;
    authentication = null;
    userACLsUsername = null;
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
    // Construct a cache description object
    ICacheDescription objectDescription = new AuthorizationResponseDescription(userName,domainControllerName,
      this.userName,this.password,this.responseLifetime,this.LRUsize);
    
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
    //Specify the Base for the search
    String searchBase = parseUser(userName);
    if (searchBase == null)
      return userNotFoundResponse;

    //specify the LDAP search filter
    String searchFilter = "(objectClass=user)";

    //Create the search controls for finding the access tokens	
    SearchControls searchCtls = new SearchControls();

    //Specify the search scope, must be base level search for tokenGroups
    searchCtls.setSearchScope(SearchControls.OBJECT_SCOPE);
 
    //Specify the attributes to return
    String returnedAtts[]={"tokenGroups","objectSid"};
    searchCtls.setReturningAttributes(returnedAtts);

    try
    {
      getSession();  
      //Search for tokens.  Since every user *must* have a SID, the no user detection should be safe.
      NamingEnumeration answer = ctx.search(searchBase, searchFilter, searchCtls);

      ArrayList theGroups = new ArrayList();

      //Loop through the search results
      while (answer.hasMoreElements())
      {
        SearchResult sr = (SearchResult)answer.next();
 
        //the sr.GetName should be null, as it is relative to the base object
        
        Attributes attrs = sr.getAttributes();
        if (attrs != null)
        {
          try
          {
            for (NamingEnumeration ae = attrs.getAll();ae.hasMore();) 
            {
              Attribute attr = (Attribute)ae.next();
              for (NamingEnumeration e = attr.getAll();e.hasMore();)
              {
                theGroups.add(sid2String((byte[])e.next()));
              }
            }
 
          }	 
          catch (NamingException e)
          {
            throw new ManifoldCFException(e.getMessage(),e);
          }
				
        }
      }

      if (theGroups.size() == 0)
        return userNotFoundResponse;
      
      // All users get certain well-known groups
      theGroups.add("S-1-1-0");

      String[] tokens = new String[theGroups.size()];
      int k = 0;
      while (k < tokens.length)
      {
        tokens[k] = (String)theGroups.get(k);
        k++;
      }
      
      return new AuthorizationResponse(tokens,AuthorizationResponse.RESPONSE_OK);

    }
    catch (NameNotFoundException e)
    {
      // This means that the user doesn't exist
      return userNotFoundResponse;
    }
    catch (NamingException e)
    {
      // Unreachable
      return unreachableResponse;
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
    return unreachableResponse;
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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add("Domain Controller");
    tabsArray.add("Cache");
    
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.domaincontrollername.value == \"\")\n"+
"  {\n"+
"    alert(\"Enter a domain controller server name\");\n"+
"    SelectTab(\"Domain Controller\");\n"+
"    editconnection.domaincontrollername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.username.value == \"\")\n"+
"  {\n"+
"    alert(\"Administrative user name cannot be null\");\n"+
"    SelectTab(\"Domain Controller\");\n"+
"    editconnection.username.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.authentication.value == \"\")\n"+
"  {\n"+
"    alert(\"Authentication cannot be null\");\n"+
"    SelectTab(\"Domain Controller\");\n"+
"    editconnection.authentication.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelifetime.value == \"\")\n"+
"  {\n"+
"    alert(\"Cache lifetime cannot be null\");\n"+
"    SelectTab(\"Cache\");\n"+
"    editconnection.cachelifetime.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelifetime.value != \"\" && !isInteger(editconnection.cachelifetime.value))\n"+
"  {\n"+
"    alert(\"Cache lifetime must be an integer\");\n"+
"    SelectTab(\"Cache\");\n"+
"    editconnection.cachelifetime.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelrusize.value == \"\")\n"+
"  {\n"+
"    alert(\"Cache LRU size cannot be null\");\n"+
"    SelectTab(\"Cache\");\n"+
"    editconnection.cachelrusize.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelrusize.value != \"\" && !isInteger(editconnection.cachelrusize.value))\n"+
"  {\n"+
"    alert(\"Cache LRU size must be an integer\");\n"+
"    SelectTab(\"Cache\");\n"+
"    editconnection.cachelrusize.focus();\n"+
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
  * This method is called in the body section of the authority connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String domainControllerName = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_DOMAINCONTROLLER);
    if (domainControllerName == null)
      domainControllerName = "";
    String userName = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_USERNAME);
    if (userName == null)
      userName = "";
    String password = parameters.getObfuscatedParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_PASSWORD);
    if (password == null)
      password = "";
    String authentication = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_AUTHENTICATION);
    if (authentication == null)
    	authentication = "DIGEST-MD5 GSSAPI";
    String userACLsUsername = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_USERACLsUSERNAME);
    if (userACLsUsername == null)
    	userACLsUsername = "sAMAccountName";
    String cacheLifetime = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    String cacheLRUsize = parameters.getParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";    
    
    // The "Domain Controller" tab
    if (tabName.equals("Domain Controller"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Domain controller name:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"domaincontrollername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(domainControllerName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Administrative user name:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"username\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Administrative password:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Authentication:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"authentication\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(authentication)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Login name AD attribute:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"userACLsUsername\">\n"+
"        <option value=\"sAMAccountName\""+(userACLsUsername.equals("sAMAccountName")?" selected=\"true\"":"")+">sAMAccountName</option>\n"+
"        <option value=\"userPrincipalName\""+(userACLsUsername.equals("userPrincipalName")?" selected=\"true\"":"")+">userPrincipalName</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Domain Controller tab
      out.print(
"<input type=\"hidden\" name=\"domaincontrollername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(domainControllerName)+"\"/>\n"+
"<input type=\"hidden\" name=\"username\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"+
"<input type=\"hidden\" name=\"authentication\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(authentication)+"\"/>\n"
      );
    }
    // The "Cache" tab
    if (tabName.equals("Cache"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Cache lifetime:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"cachelifetime\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLifetime)+"\"/> minutes</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Cache LRU size:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"cachelrusize\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLRUsize)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Domain Controller tab
      out.print(
"<input type=\"hidden\" name=\"cachelifetime\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLifetime)+"\"/>\n"+
"<input type=\"hidden\" name=\"cachelrusize\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLRUsize)+"\"/>\n"
      );
    }    
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ManifoldCFException
  {
    String domainControllerName = variableContext.getParameter("domaincontrollername");
    if (domainControllerName != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_DOMAINCONTROLLER,domainControllerName);
    String userName = variableContext.getParameter("username");
    if (userName != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_USERNAME,userName);
    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_PASSWORD,password);
    String authentication = variableContext.getParameter("authentication");
    if (authentication != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_AUTHENTICATION,authentication);
    String userACLsUsername = variableContext.getParameter("userACLsUsername");
    if (userACLsUsername != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_USERACLsUSERNAME,userACLsUsername);
    String cacheLifetime = variableContext.getParameter("cachelifetime");
    if (cacheLifetime != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_CACHELIFETIME,cacheLifetime);
    String cacheLRUsize = variableContext.getParameter("cachelrusize");
    if (cacheLRUsize != null)
      parameters.setParameter(org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_CACHELRUSIZE,cacheLRUsize);
    
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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Parameters:</nobr></td>\n"+
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
      else if (param.length() >="cache lifetime".length() && param.substring(param.length()-"cache lifetime".length()).equalsIgnoreCase("cache lifetime"))
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+" minutes</nobr><br/>\n"
        );
      }      
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=<"+Integer.toString(kmanager.getContents().length)+" certificate(s)></nobr><br/>\n"
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

  // Protected methods

  protected void getSession()
    throws ManifoldCFException
  {
    if (ctx == null)
    {
      // Calculate the ldap url first
      String ldapURL = "ldap://" + domainControllerName + ":389";
      
      Hashtable env = new Hashtable();
      env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.SECURITY_AUTHENTICATION,authentication);      
      env.put(Context.SECURITY_PRINCIPAL,userName);
      env.put(Context.SECURITY_CREDENTIALS,password);
				
      //connect to my domain controller
      env.put(Context.PROVIDER_URL,ldapURL);
		
      //specify attributes to be returned in binary format
      env.put("java.naming.ldap.attributes.binary","tokenGroups objectSid");
 
      // Now, try the connection...
      try
      {
        ctx = new InitialLdapContext(env,null);
      }
      catch (AuthenticationException e)
      {
        // This means we couldn't authenticate!
        throw new ManifoldCFException("Authentication problem authenticating admin user '"+userName+"': "+e.getMessage(),e);
      }
      catch (CommunicationException e)
      {
        // This means we couldn't connect, most likely
	throw new ManifoldCFException("Couldn't communicate with domain controller '"+domainControllerName+"': "+e.getMessage(),e);
      }
      catch (NamingException e)
      {
	throw new ManifoldCFException(e.getMessage(),e);
      }
    }
    else
    {
      // Attempt to reconnect.  I *hope* this is efficient and doesn't do unnecessary work.
      try
      {
        ctx.reconnect(null);
      }
      catch (AuthenticationException e)
      {
        // This means we couldn't authenticate!
        throw new ManifoldCFException("Authentication problem authenticating admin user '"+userName+"': "+e.getMessage(),e);
      }
      catch (CommunicationException e)
      {
        // This means we couldn't connect, most likely
	throw new ManifoldCFException("Couldn't communicate with domain controller '"+domainControllerName+"': "+e.getMessage(),e);
      }
      catch (NamingException e)
      {
	throw new ManifoldCFException(e.getMessage(),e);
      }
    }
    
    expiration = System.currentTimeMillis() + expirationInterval;
    
    try
    {
      responseLifetime = Long.parseLong(this.cacheLifetime) * 60L * 1000L;
      LRUsize = Integer.parseInt(this.cacheLRUsize);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Cache lifetime or Cache LRU size must be an integer: "+e.getMessage(),e);
    }
    
  }
  
  /** Parse a user name into an ldap search base. */
  protected String parseUser(String userName)
    throws ManifoldCFException
  {
    //String searchBase = "CN=Administrator,CN=Users,DC=qa-ad-76,DC=metacarta,DC=com";
    int index = userName.indexOf("@");
    if (index == -1)
      throw new ManifoldCFException("Username is in unexpected form (no @): '"+userName+"'");
    String userPart = userName.substring(0,index);
    String domainPart = userName.substring(index+1);
    if (userACLsUsername.equals("userPrincipalName")){
    	userPart = userName;
    }
    
    //Build the DN searchBase from domain part
    StringBuilder domainsb = new StringBuilder();
    int j = 0;
    while (true)
    {
      if (j > 0)
        domainsb.append(",");

      int k = domainPart.indexOf(".",j);
      if (k == -1)
      {
        domainsb.append("DC=").append(ldapEscape(domainPart.substring(j)));
        break;
      }
      domainsb.append("DC=").append(ldapEscape(domainPart.substring(j,k)));
      j = k+1;
    }

    //Get DistinguishedName (for this method we are using DomainPart as a searchBase ie: DC=qa-ad-76,DC=metacarta,DC=com")
    String userDN = getDistinguishedName(userPart, domainsb.toString());

    return userDN;
  }
  
  /** Obtain the DistinguishedNamefor a given user logon name.
  *@param userName (Domain Logon Name) is the user name or identifier.
  *@param searchBase (Full Domain Name for the search ie: DC=qa-ad-76,DC=metacarta,DC=com)
  *@return DistinguishedName for given domain user logon name. 
  * (Should throws an exception if user is not found.)
  */
  protected String getDistinguishedName(String userName, String searchBase)
    throws ManifoldCFException
  {
    getSession();  
    String returnedAtts[] = {"distinguishedName"};
    String searchFilter = "(&(objectClass=user)(" + userACLsUsername + "=" + userName + "))";
    SearchControls searchCtls = new SearchControls();
    searchCtls.setReturningAttributes(returnedAtts);
    //Specify the search scope  
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    searchCtls.setReturningAttributes(returnedAtts);

    try
    {
      NamingEnumeration answer = ctx.search(searchBase, searchFilter, searchCtls);
      while (answer.hasMoreElements())
      {
        SearchResult sr = (SearchResult)answer.next();
        Attributes attrs = sr.getAttributes();
        if (attrs != null)
        {
          String dn = attrs.get("distinguishedName").get().toString();
          return dn;
        }
      }
      return null;
    }
    catch (NamingException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
   
  /** LDAP escape a string.
  */
  protected static String ldapEscape(String input)
  {
    //Add escape sequence to all commas
    StringBuilder sb = new StringBuilder();
    int index = 0;
    while (true)
    {
      int oldIndex = index;
      index = input.indexOf(",",oldIndex);
      if (index == -1)
      {
        sb.append(input.substring(oldIndex));
        break;
      }
      sb.append(input.substring(oldIndex,index)).append("\\,");
      index++;
    }
    return sb.toString();
  }
    	
  /** Convert a binary SID to a string */
  protected static String sid2String(byte[] SID)
  {
    StringBuilder strSID = new StringBuilder("S");
    long version = SID[0];
    strSID.append("-").append(Long.toString(version));
    long authority = SID[4];
    for (int i = 0;i<4;i++)
    {
      authority <<= 8;
      authority += SID[4+i] & 0xFF;
    }
    strSID.append("-").append(Long.toString(authority));
    long count = SID[2];
    count <<= 8;
    count += SID[1] & 0xFF;
    for (int j=0;j<count;j++)
    {
      long rid = SID[11 + (j*4)] & 0xFF;
      for (int k=1;k<4;k++)
      {
        rid <<= 8;
        rid += SID[11-k + (j*4)] & 0xFF;
      }
      strSID.append("-").append(Long.toString(rid));
    }
    return strSID.toString();
  }

  protected static StringSet emptyStringSet = new StringSet();
  
  /** This is the cache object descriptor for cached access tokens from
  * this connector.
  */
  protected static class AuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    /** The user name associated with the access tokens */
    protected String userName;
    /** The domain controller associated with the access tokens */
    protected String domainControllerName;
    /** The admin user name */
    protected String adminUserName;
    /** The admin password */
    protected String adminPassword;
    /** The response lifetime */
    protected long responseLifetime;
    /** The expiration time */
    protected long expirationTime = -1;
    
    /** Constructor. */
    public AuthorizationResponseDescription(String userName, String domainControllerName,
      String adminUserName, String adminPassword, long responseLifetime, int LRUsize)
    {
      super("ActiveDirectoryAuthority",LRUsize);
      this.userName = userName;
      this.domainControllerName = domainControllerName;
      this.adminUserName = adminUserName;
      this.adminPassword = adminPassword;
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
      return getClass().getName() + "-" + userName + "-" + domainControllerName +
        "-" + adminUserName + "-" + adminPassword;
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
      return userName.hashCode() + domainControllerName.hashCode() + adminUserName.hashCode() +
        adminPassword.hashCode();
    }
    
    public boolean equals(Object o)
    {
      if (!(o instanceof AuthorizationResponseDescription))
        return false;
      AuthorizationResponseDescription ard = (AuthorizationResponseDescription)o;
      return ard.userName.equals(userName) && ard.domainControllerName.equals(domainControllerName) &&
        ard.adminUserName.equals(adminUserName) && ard.adminPassword.equals(adminPassword);
    }
    
  }
  
}


