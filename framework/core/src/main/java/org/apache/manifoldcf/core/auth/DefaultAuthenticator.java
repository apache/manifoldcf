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
package org.apache.manifoldcf.core.auth;

import org.apache.manifoldcf.core.interfaces.*;

/** Default authenticator for MCF system.
*/
public class DefaultAuthenticator implements IAuth
{

  /** UI login user name */
  public static final String loginUserNameProperty = "org.apache.manifoldcf.login.name";
  /** UI login password */
  public static final String loginPasswordProperty = "org.apache.manifoldcf.login.password";

  /** API login user name */
  public static final String apiLoginUserNameProperty = "org.apache.manifoldcf.apilogin.name";
  /** API login password */
  public static final String apiLoginPasswordProperty = "org.apache.manifoldcf.apilogin.password";

  protected final String loginUserName;
  protected final String loginPassword;
  protected final String apiLoginUserName;
  protected final String apiLoginPassword;
  
  /** Constructor */
  public DefaultAuthenticator(final IThreadContext threadContext)
    throws ManifoldCFException {
    loginUserName = LockManagerFactory.getStringProperty(threadContext,loginUserNameProperty,"admin");
    loginPassword = LockManagerFactory.getPossiblyObfuscatedStringProperty(threadContext,loginPasswordProperty,"admin");

    apiLoginUserName = LockManagerFactory.getStringProperty(threadContext,apiLoginUserNameProperty,"");
    apiLoginPassword = LockManagerFactory.getPossiblyObfuscatedStringProperty(threadContext,apiLoginPasswordProperty,"");
  }
    
  /** Verify UI login */
  @Override
  public boolean verifyUILogin(final String userId, final String password)
    throws ManifoldCFException {
    if (userId != null && password != null)
    {
      return userId.equals(loginUserName) &&  password.equals(loginPassword);
    }
    return false;
  }

  /** Verify API login */
  @Override
  public boolean verifyAPILogin(final String userId, final String password)
    throws ManifoldCFException {
    if (userId != null && password != null)
    {
      return userId.equals(apiLoginUserName) &&  password.equals(apiLoginPassword);
    }
    return false;
  }
	
  /** Check user capability */
  public boolean checkCapability(final String userId, final int capability)
    throws ManifoldCFException {
    // MHL when we add capability support throught MCF
    return true;
  }
  
}