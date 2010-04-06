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
package org.apache.lcf.authorities.activedirectory;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.authorities.interfaces.*;
import org.apache.lcf.authorities.system.Logging;
import org.apache.lcf.authorities.system.LCF;

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
public class ActiveDirectoryAuthority extends org.apache.lcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Data from the parameters
  private String domainControllerName = null;
  private String userName = null;
  private String password = null;

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

  /** Return the path for the UI interface JSP elements.
  * These JSP's must be provided to allow the connector to be configured, and to
  * permit it to present document filtering specification information in the UI.
  * This method should return the name of the folder, under the <webapp>/connectors/
  * area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
  *@return the folder part
  */
  public String getJSPFolder()
  {
    return "activedirectory";
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // First, create server object (llServer)
    domainControllerName = configParams.getParameter(ActiveDirectoryConfig.PARAM_DOMAINCONTROLLER);
    userName = configParams.getParameter(ActiveDirectoryConfig.PARAM_USERNAME);
    password = configParams.getObfuscatedParameter(ActiveDirectoryConfig.PARAM_PASSWORD);
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Check connection for sanity.
  */
  public String check()
    throws LCFException
  {
    getSession();
    return super.check();
  }

  /** Poll.  The connection should be closed if it has been idle for too long.
  */
  public void poll()
    throws LCFException
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
  public void disconnect()
    throws LCFException
  {
    closeConnection();
    domainControllerName = null;
    userName = null;
    password = null;
    super.disconnect();
  }

  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws LCFException
  {
    getSession();

    //Create the search controls 		
    SearchControls searchCtls = new SearchControls();

    //Specify the search scope, must be base level search for tokenGroups
    searchCtls.setSearchScope(SearchControls.OBJECT_SCOPE);
 
    //specify the LDAP search filter
    String searchFilter = "(objectClass=user)";
		
    //Specify the Base for the search
    String searchBase = parseUser(userName);
 
    //Specify the attributes to return
    String returnedAtts[] = {"tokenGroups","objectSid"};
    searchCtls.setReturningAttributes(returnedAtts);

    try
    {
      //Search for objects using the filter
      NamingEnumeration answer = ctx.search(searchBase, searchFilter, searchCtls);

      ArrayList theGroups = new ArrayList();
      // All users get certain well-known groups
      theGroups.add("S-1-1-0");

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
            throw new LCFException(e.getMessage(),e);
          }
				
        }
      }
      
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
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    // The default response if the getConnection method fails
    return unreachableResponse;
  }

  // Protected methods
  
  protected void getSession()
    throws LCFException
  {
    if (ctx == null)
    {
      // Calculate the ldap url first
      String ldapURL = "ldap://" + domainControllerName + ":389";
      
      Hashtable env = new Hashtable();
      env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.SECURITY_AUTHENTICATION,"DIGEST-MD5 GSSAPI");
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
        throw new LCFException("Authentication problem authenticating admin user '"+userName+"': "+e.getMessage(),e);
      }
      catch (CommunicationException e)
      {
        // This means we couldn't connect, most likely
	throw new LCFException("Couldn't communicate with domain controller '"+domainControllerName+"': "+e.getMessage(),e);
      }
      catch (NamingException e)
      {
	throw new LCFException(e.getMessage(),e);
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
        throw new LCFException("Authentication problem authenticating admin user '"+userName+"': "+e.getMessage(),e);
      }
      catch (CommunicationException e)
      {
        // This means we couldn't connect, most likely
	throw new LCFException("Couldn't communicate with domain controller '"+domainControllerName+"': "+e.getMessage(),e);
      }
      catch (NamingException e)
      {
	throw new LCFException(e.getMessage(),e);
      }
    }
    
    expiration = System.currentTimeMillis() + expirationInterval;
  }
  
  /** Parse a user name into an ldap search base. */
  protected static String parseUser(String userName)
    throws LCFException
  {
    //String searchBase = "CN=Administrator,CN=Users,DC=qa-ad-76,DC=metacarta,DC=com";
    int index = userName.indexOf("@");
    if (index == -1)
      throw new LCFException("Username is in unexpected form (no @): '"+userName+"'");
    String userPart = userName.substring(0,index);
    String domainPart = userName.substring(index+1);
    // Start the search base assembly
    StringBuffer sb = new StringBuffer();
    sb.append("CN=").append(userPart).append(",CN=Users");
    int j = 0;
    while (true)
    {
      int k = domainPart.indexOf(".",j);
      if (k == -1)
      {
        sb.append(",DC=").append(domainPart.substring(j));
        break;
      }
      sb.append(",DC=").append(domainPart.substring(j,k));
      j = k+1;
    }
    return sb.toString();
  }

  /** Convert a binary SID to a string */
  protected static String sid2String(byte[] SID)
  {
    StringBuffer strSID = new StringBuffer("S");
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

}


