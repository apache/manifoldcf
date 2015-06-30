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
package org.apache.manifoldcf.ui.beans;

import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.*;

/** The profile object contains an API user's login information, and helps establish the
* session model for the application.  This particular bean maintains the user (against
* the IAdminUserManager service).
*/
public class APIProfile implements HttpSessionBindingListener, IAuthorizer
{
  public static final String _rcsid = "@(#)$Id$";

  /** Time of login */
  private long loginTime = -1L;
  /** Logged in user */
  private String userID = null;
  /** Set to "true" if user is logged in. */
  private boolean isLoggedIn = false;

  /** Session identifier */
  private String sessionIdentifier = null;

  /** Constructor.
  */
  public APIProfile()
  {
  }

  // The following methods constitute the "login process" for this bean.
  // Set the user and the company, then set LoggedOn.

  /** Get the current session identifier.
  *@return the identifier.
  */
  public String session()
  {
    return sessionIdentifier;
  }

  /** Get the admin user id.
  *@return the last login user id.
  */
  public String getUserID()
  {
    return userID;
  }

  /** Log out the current user.
  */
  public void logout()
  {
    sessionCleanup();
  }

  /** Log on the user, with the already-set user id and company
  * description.
  *@param userPassword is the login password for the user.
  */
  public void login(IThreadContext threadContext,
    String userID, String userPassword)
  {
    sessionCleanup();
    try
    {
      IAuth auth = AuthFactory.make(threadContext);
      // Check if everything is in place.
      if (auth.verifyAPILogin(userID,userPassword))
      {
        isLoggedIn = true;
        loginTime = System.currentTimeMillis();
        this.userID = userID;
      }
    }
    catch (ManifoldCFException e)
    {
      Logging.misc.fatal("Exception logging in: "+e.getMessage(),e);
    }
  }

  /** Get the logged-in status, which will be false if the log-in did not succeed, or
  * timed out.
  *@return the current login status: true if logged in.
  */
  public boolean getLoggedOn()
  {
    return isLoggedIn;
  }

  /** Get the current login time as a string.
  *@return the last login time.
  */
  public String getLoginTime()
  {
    return new java.util.Date(loginTime).toString();
  }

  /** Get the current login time as a long.
  *@return the last login time.
  */
  public long getLoginTimeLong()
  {
    return loginTime;
  }

  // Nuke stuff for security and the garbage
  // collector threads
  private void sessionCleanup()
  {
    // Un-log-in the user
    isLoggedIn = false;
    userID = null;
    loginTime = -1L;
  }

  /** Check user capability */
  @Override
  public boolean checkAllowed(final IThreadContext threadContext, final int capability)
    throws ManifoldCFException
  {
    if (!isLoggedIn)
      return false;
    IAuth auth = AuthFactory.make(threadContext);
    // Check if everything is in place.
    return auth.checkCapability(userID,capability);
  }

  //*****************************************************************
  // Bind listener api - support session invalidation
  // vis logout or timeout
  @Override
  public void valueBound(HttpSessionBindingEvent e)
  {
    HttpSession ss = e.getSession();

    if (sessionIdentifier==null)
    {
      sessionIdentifier = ss.getId();
    }
  }

  @Override
  public void valueUnbound(HttpSessionBindingEvent e)
  {
    sessionCleanup();
    sessionIdentifier = null;
  }

}
