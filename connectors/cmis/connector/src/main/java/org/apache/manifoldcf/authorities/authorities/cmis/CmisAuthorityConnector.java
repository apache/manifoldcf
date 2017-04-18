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
package org.apache.manifoldcf.authorities.authorities.cmis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.core.interfaces.CacheManagerFactory;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ICacheCreateHandle;
import org.apache.manifoldcf.core.interfaces.ICacheDescription;
import org.apache.manifoldcf.core.interfaces.ICacheHandle;
import org.apache.manifoldcf.core.interfaces.ICacheManager;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.StringSet;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.ui.util.Encoder;

/**
 * 
 * The CMIS Authority Connector is based only on a regular expression checker, because the CMIS specification 
 * (and the Apache Chemistry implementation) doesn't have any exposed method to get info about users.
 * 
 * For the configuration we can assume that we could have different users for any CMIS repository that 
 * could have the same endpoint. That's why for this connector is required the Repository ID.
 * 
 * @author Piergiorgio Lucidi
 * 
 */
public class CmisAuthorityConnector extends BaseAuthorityConnector {

  public static final String CONFIG_PARAM_USERNAME = "username";
  public static final String CONFIG_PARAM_PASSWORD = "password";
  public static final String CONFIG_PARAM_ENDPOINT = "endpoint";
  public static final String CONFIG_PARAM_REPOSITORY_ID = "repositoryId";
  
  protected static final String CONFIG_PARAM_USERNAME_REGEXP = "usernameregexp";
  protected static final String CONFIG_PARAM_USER_TRANSLATION = "usertranslation";
  
  private static final String DEFAULT_VALUE_ENDPOINT = "http://localhost:8080/cmis/";
  private static final String DEFAULT_VALUE_REPOSITORY_ID = "uuid";

  protected String endpoint = null;
  protected String repositoryId = null;

  protected Map<String, String> parameters = new HashMap<String, String>();
  
  /** The cache manager. */
  protected ICacheManager cacheManager = null;
  
  protected static long responseLifetime = 60000L;
  protected static int LRUsize = 1000;
  protected static StringSet emptyStringSet = new StringSet();
  
  /** This is the active directory global deny token.  This should be ingested with all documents. */
  public static final String GLOBAL_DENY_TOKEN = "DEAD_AUTHORITY";
  
  /** Unreachable CMIS */
  private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(
    new String[]{GLOBAL_DENY_TOKEN},AuthorizationResponse.RESPONSE_UNREACHABLE);
  
  /** User not found */
  private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(
    new String[]{GLOBAL_DENY_TOKEN},AuthorizationResponse.RESPONSE_USERNOTFOUND);

  /** Set thread context.
   */
   @Override
   public void setThreadContext(IThreadContext tc)
     throws ManifoldCFException {
     super.setThreadContext(tc);
     cacheManager = CacheManagerFactory.make(tc);
   }
   
   /** Clear thread context.
   */
   @Override
   public void clearThreadContext() {
     super.clearThreadContext();
     cacheManager = null;
   }
  
  public CmisAuthorityConnector() {
    super();
  }
  
  
  /**
   * Output the configuration body section. This method is called in the body
   * section of the authority connector's configuration page. Its purpose is to
   * present the required form elements for editing. The coder can presume that
   * the HTML that is output from this configuration will be within appropriate
   * <html>, <body>, and <form> tags. The name of the form is "editconnection".
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabName
   *          is the current tab name.
   */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {

    String endpoint = parameters.getParameter(CONFIG_PARAM_ENDPOINT);
    String repositoryId = parameters.getParameter(CONFIG_PARAM_REPOSITORY_ID);
    
    if(StringUtils.isEmpty(endpoint))
      endpoint = DEFAULT_VALUE_ENDPOINT;
    if(StringUtils.isEmpty(repositoryId))
      repositoryId = DEFAULT_VALUE_REPOSITORY_ID;
    
    if (tabName.equals(Messages.getString(locale,"CmisAuthorityConnector.Repository")))
    {
    out.print("<table class=\"displaytable\">\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");
    out.print("<tr><td class=\"description\"><nobr>" + Messages.getBodyString(locale,"CmisAuthorityConnector.Endpoint") + "</nobr></td>" +
        "<td class=\"value\"><input type=\"text\" name=\""
        + CONFIG_PARAM_ENDPOINT + "\" value=\""+Encoder.attributeEscape(endpoint)+"\" size=\"50\"/></td></tr>\n");
    out.print("<tr><td class=\"description\"><nobr>" + Messages.getBodyString(locale,"CmisAuthorityConnector.RepositoryID") + "</nobr></td>" +
        "<td class=\"value\"><input type=\"text\" name=\""
        + CONFIG_PARAM_REPOSITORY_ID + "\" value=\""+Encoder.attributeEscape(repositoryId)+"\"/></td></tr>\n");
    out.print("</table>\n");
    }
    else
    {
      out.print("<input type=\"hidden\" name=\""+CONFIG_PARAM_ENDPOINT+"\" value=\""+Encoder.attributeEscape(endpoint)+"\"/>\n");
      out.print("<input type=\"hidden\" name=\""+CONFIG_PARAM_REPOSITORY_ID+"\" value=\""+Encoder.attributeEscape(repositoryId)+"\"/>\n");
    }
    
  }

  /**
   * Output the configuration header section. This method is called in the head
   * section of the connector's configuration page. Its purpose is to add the
   * required tabs to the list, and to output any javascript methods that might
   * be needed by the configuration editing HTML.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    
    tabsArray.add(Messages.getString(locale,"CmisAuthorityConnector.Repository"));
    
    out.print("<script type=\"text/javascript\">\n" + "<!--\n"
        + "function checkConfig()\n" + "{\n"
        + "  if (editconnection.endpoint.value == \"\")\n" + "  {\n"
        + "    alert(\"" + Messages.getBodyJavascriptString(locale,"CmisAuthorityConnector.TheEndpointMustBeNotNull") + "\");\n"
        + "    editconnection.endpoint.focus();\n" + "    return false;\n"
        + "  }\n" + "\n" + "  return true;\n" + "}\n" + " \n"
        + "function checkConfigForSave()\n" + "{\n"
        + "  if (editconnection.endpoint.value == \"\")\n" + "  {\n"
        + "    alert(\"" + Messages.getBodyJavascriptString(locale,"CmisAuthorityConnector.TheEndpointMustBeNotNull") + "\");\n"
        + "    editconnection.endpoint.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.repositoryId.value == \"\")\n" + "  {\n"
        + "    alert(\"" + Messages.getBodyJavascriptString(locale,"CmisAuthorityConnector.TheRepositoryIDMustBeNotNull") + "\");\n"
        + "    editconnection.repositoryId.focus();\n" + "    return false;\n"
        + "  }\n" + "  return true;\n" + "}\n" + "\n" + "//-->\n"
        + "</script>\n");
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   * 
   * @param threadContext
   *          is the local thread context.
   * @param variableContext
   *          is the set of variables available from the post, including binary
   *          file post information.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the connection (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale, ConfigParams parameters)
      throws ManifoldCFException {

    //Repository
    String endpoint = variableContext.getParameter(CONFIG_PARAM_ENDPOINT);
    if (StringUtils.isNotEmpty(endpoint) && endpoint.length() > 0)
      parameters.setParameter(CONFIG_PARAM_ENDPOINT, endpoint);

    String repositoryId = variableContext
        .getParameter(CONFIG_PARAM_REPOSITORY_ID);
    if (StringUtils.isNotEmpty(repositoryId))
      parameters.setParameter(CONFIG_PARAM_REPOSITORY_ID, repositoryId);
    
    return null;

  }
  
  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML that
   * is output from this configuration will be within appropriate <html> and
   * <body> tags.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    out.print("<table class=\"displaytable\">\n"
        + "  <tr>\n"
        + "    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"CmisAuthorityConnector.Parameters") + "</nobr></td>\n"
        + "    <td class=\"value\" colspan=\"3\">\n");
    Iterator iter = parameters.listParameters();
    while (iter.hasNext()) {
      String param = (String) iter.next();
      String value = parameters.getParameter(param);
        out.print("      <nobr>"
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param) + "="
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)
            + "</nobr><br/>\n");
    }
    out.print("</td>\n" + "  </tr>\n" + "</table>\n");
  }
  
  /** Obtain the default access tokens for a given user name.
   *@param userName is the user name or identifier.
   *@return the default response tokens, presuming that the connect method fails.
   */
   @Override
   public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
   {
     return unreachableResponse;
   }
   
   /** Uncached version of the getAuthorizationResponse method.
    *@param userName is the user name or identifier.
    *@return the response tokens (according to the current authority).
    * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
    */
    protected AuthorizationResponse getAuthorizationResponseUncached(String userName)
      throws ManifoldCFException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Calculating response access tokens for user '"+userName+"'");

      // Map the user to the final value
      String verifiedUserName = userName;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Mapped user name is '"+verifiedUserName+"'");
      
      String[] tokens = new String[1];
      tokens[0] = verifiedUserName;
      return new AuthorizationResponse(tokens,AuthorizationResponse.RESPONSE_OK);

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
     if (Logging.connectors.isDebugEnabled())
       Logging.connectors.debug("CMIS: Received request for user '"+userName+"'");
     
     ICacheDescription objectDescription = new AuthorizationResponseDescription(userName,endpoint,repositoryId);
     
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
   
   /** This is the cache object descriptor for cached access tokens from
    * this connector.
    */
    protected static class AuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
    {
      /** The user name associated with the access tokens */
      protected final String userName;
      /** The repository endpoint */
      protected final String endpoint;
      /** The repository id */
      protected final String repositoryId;
      /** The expiration time */
      protected long expirationTime = -1;
      
      /** Constructor. */
      public AuthorizationResponseDescription(String userName, String endpoint, String repositoryId)
      {
        super("CMISAuthority",LRUsize);
        this.userName = userName;
        this.endpoint = endpoint;
        this.repositoryId = repositoryId;
      }

      /** Return the invalidation keys for this object. */
      public StringSet getObjectKeys()
      {
        return emptyStringSet;
      }

      /** Get the critical section name, used for synchronizing the creation of the object */
      public String getCriticalSectionName()
      {
        return getClass().getName() + "-" + userName + "-" + endpoint +
          "-" + repositoryId;
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
        return userName.hashCode() + endpoint.hashCode() + repositoryId.hashCode();
      }
      
      public boolean equals(Object o)
      {
        if (!(o instanceof AuthorizationResponseDescription))
          return false;
        AuthorizationResponseDescription ard = (AuthorizationResponseDescription)o;
        return ard.userName.equals(userName) && ard.endpoint.equals(endpoint) &&
          ard.repositoryId.equals(repositoryId);
      }
      
    }
    
}
