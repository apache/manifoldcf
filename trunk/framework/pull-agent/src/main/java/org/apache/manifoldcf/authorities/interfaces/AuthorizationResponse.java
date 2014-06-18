/* $Id: AuthorizationResponse.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** An authorization response must contain the following information:
* (a) A list of access tokens
* (b) An indication of how that result was obtained - specifically:
*     - Whether the authority was reachable or not
*     - Whether the user was found or not
*     - Whether the user was authorized or not
*/
public class AuthorizationResponse
{
  // Here are the kinds of conditions that apply to the response
  public final static int RESPONSE_OK = 0;
  public final static int RESPONSE_UNREACHABLE = 1;
  public final static int RESPONSE_USERNOTFOUND = 2;
  public final static int RESPONSE_USERUNAUTHORIZED = 3;

  /** The list of access tokens */
  protected String[] accessTokens;
  /** The status */
  protected int responseStatus;

  /** Constructor */
  public AuthorizationResponse(String[] accessTokens, int responseStatus)
  {
    this.accessTokens = accessTokens;
    this.responseStatus = responseStatus;
  }

  /** Get the status */
  public int getResponseStatus()
  {
    return responseStatus;
  }

  /** Get the tokens */
  public String[] getAccessTokens()
  {
    return accessTokens;
  }
}
