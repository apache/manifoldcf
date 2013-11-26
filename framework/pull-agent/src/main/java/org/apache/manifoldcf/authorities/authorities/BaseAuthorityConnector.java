/* $Id: BaseAuthorityConnector.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.authorities;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;

import java.util.*;
import java.io.*;

/** An authority connector supplies an ACL of some kind for a given user.  This is necessary so that the search UI
* can find the documents that can be legally seen.
*
* An instance of this interface provides this functionality.  Authority connector instances are pooled, so that session
* setup does not need to be done repeatedly.  The pool is segregated by specific sets of configuration parameters.
*/
public abstract class BaseAuthorityConnector extends org.apache.manifoldcf.core.connector.BaseConnector implements IAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id: BaseAuthorityConnector.java 988245 2010-08-23 18:39:35Z kwright $";

  // For repositories that have the ability to deny access based on a user's access tokens
  protected static final AuthorizationResponse RESPONSE_UNREACHABLE = new AuthorizationResponse(new String[]{GLOBAL_DENY_TOKEN},
    AuthorizationResponse.RESPONSE_UNREACHABLE);
  protected static final AuthorizationResponse RESPONSE_USERNOTFOUND = new AuthorizationResponse(new String[]{GLOBAL_DENY_TOKEN},
    AuthorizationResponse.RESPONSE_USERNOTFOUND);
  protected static final AuthorizationResponse RESPONSE_USERUNAUTHORIZED = new AuthorizationResponse(new String[]{GLOBAL_DENY_TOKEN},
    AuthorizationResponse.RESPONSE_USERUNAUTHORIZED);

  // For repositories that DO NOT have the ability to deny access based on a user's access tokens
  protected static final AuthorizationResponse RESPONSE_UNREACHABLE_ADDITIVE = new AuthorizationResponse(new String[0],
    AuthorizationResponse.RESPONSE_UNREACHABLE);
  protected static final AuthorizationResponse RESPONSE_USERNOTFOUND_ADDITIVE = new AuthorizationResponse(new String[0],
    AuthorizationResponse.RESPONSE_USERNOTFOUND);
  protected static final AuthorizationResponse RESPONSE_USERUNAUTHORIZED_ADDITIVE = new AuthorizationResponse(new String[0],
    AuthorizationResponse.RESPONSE_USERUNAUTHORIZED);

  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  @Override
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws ManifoldCFException
  {
    // Implementation for old-style behavior.  Override this method for new-style behavior.
    try
    {
      String[] accessTokens = getAccessTokens(userName);
      if (accessTokens == null)
        return new AuthorizationResponse(new String[0],AuthorizationResponse.RESPONSE_USERNOTFOUND);
      return new AuthorizationResponse(accessTokens,AuthorizationResponse.RESPONSE_OK);
    }
    catch (ManifoldCFException e)
    {
      // There's an authorization failure of some kind.
      String[] defaultAccessTokens = getDefaultAccessTokens(userName);
      if (defaultAccessTokens == null)
      {
        // Treat it as an authorization failure
        return new AuthorizationResponse(new String[0],AuthorizationResponse.RESPONSE_USERUNAUTHORIZED);
      }
      return new AuthorizationResponse(defaultAccessTokens,AuthorizationResponse.RESPONSE_UNREACHABLE);
    }
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    String[] acls = getDefaultAccessTokens(userName);
    if (acls == null)
      return new AuthorizationResponse(new String[0],AuthorizationResponse.RESPONSE_USERUNAUTHORIZED);
    else
      return new AuthorizationResponse(acls,AuthorizationResponse.RESPONSE_UNREACHABLE);
  }

  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the tokens (according to the current authority), or null if the user does not exist.
  * (Throw an exception if access is denied, usually because the authority is down).
  */
  public String[] getAccessTokens(String userName)
    throws ManifoldCFException
  {
    return null;
  }

  /** Return the default access tokens in the case where the getAccessTokens() method could not
  * connect with the server.
  *@param userName is the username that the access tokens are for.  Typically this is not used.
  *@return the default tokens, or null if there are no default takens, and the error should be
  * treated as a hard one.
  */
  public String[] getDefaultAccessTokens(String userName)
  {
    return null;
  }

}
