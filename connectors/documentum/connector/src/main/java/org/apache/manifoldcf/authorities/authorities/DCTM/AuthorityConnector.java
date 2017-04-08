/* $Id: AuthorityConnector.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.authorities.DCTM;

import org.apache.log4j.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;
import java.util.*;
import java.io.*;
import org.apache.manifoldcf.crawler.common.DCTM.*;
import java.rmi.*;


/** Autheticator.
*/
public class AuthorityConnector extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector
{

  public static final String CONFIG_PARAM_DOCBASE = "docbasename";
  public static final String CONFIG_PARAM_USERNAME = "docbaseusername";
  public static final String CONFIG_PARAM_PASSWORD = "docbasepassword";
  public static final String CONFIG_PARAM_DOMAIN = "domain";
  public static final String CONFIG_PARAM_CASEINSENSITIVE = "usernamecaseinsensitive";
  public static final String CONFIG_PARAM_USESYSTEMACLS = "usesystemacls";
  public static final String CONFIG_PARAM_CACHELIFETIME = "cachelifetimemins";
  public static final String CONFIG_PARAM_CACHELRUSIZE = "cachelrusize";
  
  protected String docbaseName = null;
  protected String userName = null;
  protected String password = null;
  protected String domain = null;
  protected boolean caseInsensitive = false;
  protected boolean useSystemAcls = true;

  // Documentum has no "deny" tokens, and its document acls cannot be empty, so no local authority deny token is required.
  // However, it is felt that we need to be suspenders-and-belt, so we use the deny token.
  // The documentum tokens are of the form xxx:yyy, so they cannot collide with the standard deny token.

    /** Cache manager. */
  protected ICacheManager cacheManager = null;

  // Set if we have set up session parameters necessary for caching
  protected boolean hasSessionParameters = false;
  // This is the DFC session; it may be null, or it may be set.
  protected IDocumentum session = null;
  protected long lastSessionFetch = -1L;

  protected static final long timeToRelease = 300000L;

  private String cacheLifetime = null;
  private String cacheLRUsize = null;
  private long responseLifetime = 60000L;
  private int LRUsize = 1000;

  public AuthorityConnector()
  {
    super();
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

  protected class GetSessionThread extends Thread
  {
    protected Throwable exception = null;

    public GetSessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Create a session
        IDocumentumFactory df = (IDocumentumFactory)Naming.lookup("rmi://127.0.0.1:8300/documentum_factory");
        IDocumentum newSession = df.make();
        newSession.createSession(docbaseName,userName,password,domain);
        session = newSession;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }
  }

  /** Get session parameters.
  */
  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (!hasSessionParameters)
    {
      try
      {
        responseLifetime = Long.parseLong(this.cacheLifetime) * 60L * 1000L;
        LRUsize = Integer.parseInt(this.cacheLRUsize);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Cache lifetime or Cache LRU size must be an integer: "+e.getMessage(),e);
      }

      // This is the stuff that used to be in connect()
      if (docbaseName == null || docbaseName.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_DOCBASE+" required but not set");

      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("DCTM: Docbase = '" + docbaseName + "'");

      if (userName == null || userName.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_USERNAME+" required but not set");

      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("DCTM: Username = '" + userName + "'");

      if (password == null || password.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

      Logging.authorityConnectors.debug("DCTM: Password exists");

      if (domain == null)
      {
        // Empty domain is allowed
        Logging.authorityConnectors.debug("DCTM: No domain");
      }
      else
        Logging.authorityConnectors.debug("DCTM: Domain = '" + domain + "'");

      if (caseInsensitive)
      {
        Logging.authorityConnectors.debug("DCTM: Case insensitivity enabled");
      }

      if (useSystemAcls)
      {
        Logging.authorityConnectors.debug("DCTM: Use system acls enabled");
      }

      hasSessionParameters = true;
    }
  }
  
  /** Get a DFC session.  This will be done every time it is needed.
  */
  protected void getSession()
    throws ManifoldCFException
  {
    getSessionParameters();
    if (session == null)
    {
      // This actually sets up the connection
      GetSessionThread t = new GetSessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof java.net.MalformedURLException)
            throw (java.net.MalformedURLException)thr;
          else if (thr instanceof NotBoundException)
            throw (NotBoundException)thr;
          else if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
      catch (NotBoundException e)
      {
        // Transient problem: Server not available at the moment.
        throw new ManifoldCFException("Server not up at the moment: "+e.getMessage(),e);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        throw new ManifoldCFException("Transient remote exception creating session: "+e.getMessage(),e);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          throw new ManifoldCFException("Remote service interruption creating session: "+e.getMessage(),e);
        }
        throw new ManifoldCFException(e.getMessage(),e);
      }
    }

    // Update the expire time for this session, except if an error was thrown.
    lastSessionFetch = System.currentTimeMillis();

  }

  /** Perform a DQL query, with appropriate reset on a remote exception */
  protected IDocumentumResult performDQLQuery(String query)
    throws DocumentumException, ManifoldCFException
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      try
      {
        return session.performDQLQuery(query);
      }
      catch (RemoteException e)
      {
        if (noSession)
          throw new ManifoldCFException("Transient error connecting to documentum service",e);
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class CheckConnectionThread extends Thread
  {
    protected Throwable exception = null;

    public CheckConnectionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.checkConnection();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  /** Check connection, with appropriate retries */
  protected void checkConnection()
    throws DocumentumException, ManifoldCFException
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      CheckConnectionThread t = new CheckConnectionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
          throw new ManifoldCFException("Transient error connecting to documentum service",e);
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  /** Perform getObjectByQualification, with appropriate reset */
  protected IDocumentumObject getObjectByQualification(String qualification)
    throws DocumentumException, ManifoldCFException
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      try
      {
        return session.getObjectByQualification(qualification);
      }
      catch (RemoteException e)
      {
        if (noSession)
          throw new ManifoldCFException("Transient error connecting to documentum service",e);
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }

  }

  /** Get server version, with appropriate retries */
  protected String getServerVersion()
    throws DocumentumException, ManifoldCFException
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      try
      {
        return session.getServerVersion();
      }
      catch (RemoteException e)
      {
        if (noSession)
          throw new ManifoldCFException("Transient error connecting to documentum service",e);
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }

  }


  protected class DestroySessionThread extends Thread
  {
    protected Throwable exception = null;

    public DestroySessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.destroySession();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

  }


  /** Release the session, if it's time.
  */
  protected void releaseCheck()
    throws ManifoldCFException
  {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.authorityConnectors.warn("Transient remote exception closing session",e);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.authorityConnectors.warn("Remote service interruption closing session",e);
        }
        else
          Logging.authorityConnectors.warn("Error closing session",e);
      }
    }
  }

  protected class GetUserAccessIDThread extends Thread
  {
    protected String strUserName;
    protected Throwable exception = null;
    protected String rval = null;
    protected AuthorizationResponse response = null;

    public GetUserAccessIDThread(String strUserName)
    {
      super();
      setDaemon(true);
      this.strUserName = strUserName;
    }

    public void run()
    {
      try
      {
        // Need the server version so we can figure out whether to try the user_login_name query
        String serverVersion = session.getServerVersion();
        boolean hasLoginNameColumn = (serverVersion.compareTo("5.3.") >= 0);

        IDocumentumObject object = null;
        try
        {
          if (hasLoginNameColumn)
            object = getObjectByQualification("dm_user where "+insensitiveMatch(caseInsensitive,"user_login_name",strUserName));
          if (!object.exists())
            object = getObjectByQualification("dm_user where "+insensitiveMatch(caseInsensitive,"user_os_name",strUserName));
          if (!object.exists())
          {
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("DCTM: No user found for username '" + strUserName + "'");
            response = RESPONSE_USERNOTFOUND;
            return;
          }

          if (object.getUserState() != 0)
          {
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("DCTM: User found for username '" + strUserName + "' but the account is not active.");
            response = RESPONSE_USERUNAUTHORIZED;
            return;
          }

          rval = object.getUserName();

        }
        finally
        {
          if (object != null)
            object.release();
        }


      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public String getUserID()
    {
      return rval;
    }

    public AuthorizationResponse getResponse()
    {
      return response;
    }
  }

  protected class GetAccessTokensThread extends Thread
  {
    protected String strDQL;
    protected ArrayList list;
    protected Throwable exception = null;

    public GetAccessTokensThread(String strDQL, ArrayList list)
    {
      super();
      setDaemon(true);
      this.strDQL = strDQL;
      this.list = list;
    }

    public void run()
    {
      try
      {
        IDocumentumResult result = session.performDQLQuery(strDQL);
        try
        {
          if (Logging.authorityConnectors.isDebugEnabled())
            Logging.authorityConnectors.debug("DCTM: Collection returned.");
          while (result.isValidRow())
          {
            String strObjectName = result.getStringValue("object_name");
            String strOwnerName = result.getStringValue("owner_name");
            String strFullTokenName = docbaseName + ":" + strOwnerName + "." + strObjectName;
            list.add(strFullTokenName);

            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("DCTM: ACL being added: " + strFullTokenName);

            result.nextRow();

          }
        }
        finally
        {
          result.close();
        }


      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }
  }

  /** Obtain the access tokens for a given user name.
  *@param strUserNamePassedIn is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  @Override
  public AuthorizationResponse getAuthorizationResponse(String strUserNamePassedIn)
    throws ManifoldCFException
  {

    if (Logging.authorityConnectors.isDebugEnabled())
      Logging.authorityConnectors.debug("DCTM: Inside AuthorityConnector.getAuthorizationResponse for user '"+strUserNamePassedIn+"'");

    // We need this in order to be able to properly construct an AuthorizationResponseDescription.
    getSessionParameters();
    
    // Construct a cache description object
    ICacheDescription objectDescription = new AuthorizationResponseDescription(strUserNamePassedIn,docbaseName,userName,password,
      domain,caseInsensitive,useSystemAcls,responseLifetime,LRUsize);
    
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
        response = getAuthorizationResponseUncached(strUserNamePassedIn);
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
  
  /** Uncached get response method. */
  protected AuthorizationResponse getAuthorizationResponseUncached(String strUserNamePassedIn)
    throws ManifoldCFException
  {
    if (Logging.authorityConnectors.isDebugEnabled())
      Logging.authorityConnectors.debug("DCTM: Inside AuthorityConnector.getAuthorizationResponseUncached for user '"+strUserNamePassedIn+"'");

    try
    {
      String strUserName = strUserNamePassedIn;

      String strAccessToken;
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        GetUserAccessIDThread t = new GetUserAccessIDThread(strUserName);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          if (t.getResponse() != null)
            return t.getResponse();
          strAccessToken = t.getUserID();
          // Exit out of retry loop
          break;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            Logging.authorityConnectors.warn("DCTM: Transient error checking authorization: "+e.getMessage(),e);
            return RESPONSE_UNREACHABLE;
          }
          session = null;
          lastSessionFetch = -1L;
          // Go back around again
        }
      }


      // String strDQL = "SELECT DISTINCT A.object_name FROM dm_acl A
      // WHERE (any (A.r_accessor_name = '" + strAccessToken + "' AND
      // A.r_accessor_permit > 1) OR ANY (A.r_accessor_name in (SELECT
      // G.group_name FROM dm_group G WHERE ANY G.i_all_users_names = '" +
      // strAccessToken + "') AND A.r_accessor_permit > 1)) AND '" +
      // strAccessToken + "' In (SELECT U.user_name FROM dm_user U WHERE
      // U.user_state=0)";
      String strDQL = "SELECT DISTINCT A.owner_name, A.object_name FROM dm_acl A " + " WHERE ";
      if (!useSystemAcls)
      {
        strDQL += "A.object_name NOT LIKE 'dm_%' AND (";
      }
      
      // Include ACLs with positive groups and users
      strDQL += "(any (A.r_accessor_name IN (" + quoteDQLString(strAccessToken) + ", 'dm_world') AND r_accessor_permit>2) OR (any (A.r_accessor_name='dm_owner' AND A.r_accessor_permit>2) AND A.owner_name=" + quoteDQLString(strAccessToken) + ") OR (ANY (A.r_accessor_name in (SELECT G.group_name FROM dm_group G WHERE ANY G.i_all_users_names = " + quoteDQLString(strAccessToken) + ") AND r_accessor_permit>2))) ";
      // Exclude ACLs with negative groups and users
      strDQL += "AND NOT (any (A.r_accessor_name IN (" + quoteDQLString(strAccessToken) + ", 'dm_world') AND r_accessor_permit<=2) OR (any (A.r_accessor_name='dm_owner' AND A.r_accessor_permit<=2) AND A.owner_name=" + quoteDQLString(strAccessToken) + ") OR (ANY (A.r_accessor_name in (SELECT G.group_name FROM dm_group G WHERE ANY G.i_all_users_names = " + quoteDQLString(strAccessToken) + ") AND r_accessor_permit<=2)))";
      
      if (!useSystemAcls) {
        strDQL += ")";
      }

      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("DCTM: About to execute query= (" + strDQL + ")");

      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        ArrayList list =  new ArrayList();
        GetAccessTokensThread t = new GetAccessTokensThread(strDQL,list);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          Logging.authorityConnectors.debug("DCTM: Done processing authorization query");

          String[] strArrayRetVal = new String[list.size()];

          int intObjectIdIndex = 0;

          while (intObjectIdIndex < strArrayRetVal.length)
          {
            strArrayRetVal[intObjectIdIndex] = (String)list.get(intObjectIdIndex);
            intObjectIdIndex++;
          }
          // Break out of retry loop and return
          return new AuthorizationResponse(strArrayRetVal,AuthorizationResponse.RESPONSE_OK);
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            Logging.authorityConnectors.warn("DCTM: Transient error checking authorization: "+e.getMessage(),e);
            return RESPONSE_UNREACHABLE;
          }
          session = null;
          lastSessionFetch = -1L;
          // Go back around again
        }
      }
    }
    catch (DocumentumException e)
    {

      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.authorityConnectors.warn("DCTM: Transient error checking authorization: "+e.getMessage(),e);
        // Transient: Treat as if user does not exist, not like credentials invalid.
        return RESPONSE_UNREACHABLE;
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    return RESPONSE_UNREACHABLE;
  }

  protected static String insensitiveMatch(boolean insensitive, String field, String value)
  {
    StringBuilder sb = new StringBuilder();
    if (insensitive)
      sb.append("upper(").append(field).append(")");
    else
      sb.append(field);
    sb.append("=");
    if (insensitive)
      sb.append(quoteDQLString(value.toUpperCase(Locale.ROOT)));
    else
      sb.append(quoteDQLString(value));
    return sb.toString();
  }

  protected static String quoteDQLString(String value)
  {
    StringBuilder sb = new StringBuilder("'");
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\'')
        sb.append(x);
      sb.append(x);
    }
    sb.append("'");
    return sb.toString();
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      try
      {
        checkConnection();
        return super.check();
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
          return "Connection temporarily failed: "+e.getMessage();
        else
          return "Connection failed: "+e.getMessage();
      }
    }
    catch (ManifoldCFException e)
    {
      return "Connection failed: "+e.getMessage();
    }

  }


  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    docbaseName = configParams.getParameter(CONFIG_PARAM_DOCBASE);
    userName = configParams.getParameter(CONFIG_PARAM_USERNAME);
    password = configParams.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    domain = configParams.getParameter(CONFIG_PARAM_DOMAIN);
    if (domain == null || domain.length() < 1)
    {
      // Empty domain is allowed
      domain = null;
    }

    String strCaseInsensitive = configParams.getParameter(CONFIG_PARAM_CASEINSENSITIVE);
    if (strCaseInsensitive != null && strCaseInsensitive.equals("true"))
    {
      caseInsensitive = true;
    }
    else
      caseInsensitive = false;

    String strUseSystemAcls = configParams.getParameter(CONFIG_PARAM_USESYSTEMACLS);
    if (strUseSystemAcls == null || strUseSystemAcls.equals("true"))
    {
      useSystemAcls = true;
    }
    else
      useSystemAcls = false;

    cacheLifetime = configParams.getParameter(CONFIG_PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    cacheLRUsize = configParams.getParameter(CONFIG_PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";    

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

  /** Disconnect from Documentum.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    hasSessionParameters = false;
    if (session != null)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.authorityConnectors.warn("DCTM: Transient remote exception closing session: "+e.getMessage(),e);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.authorityConnectors.warn("DCTM: Remote service interruption closing session: "+e.getMessage(),e);
        }
        else
          Logging.authorityConnectors.warn("DCTM: Error closing session: "+e.getMessage(),e);
      }

    }

    docbaseName = null;
    userName = null;
    password = null;
    domain = null;
    
    cacheLifetime = null;
    cacheLRUsize = null;

  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    releaseCheck();
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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"DCTM.Docbase"));
    tabsArray.add(Messages.getString(locale,"DCTM.UserMapping"));
    tabsArray.add(Messages.getString(locale,"DCTM.SystemACLs"));
    tabsArray.add(Messages.getString(locale,"DCTM.Cache"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.docbasename.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.PleaseSupplyTheNameOfADocbase") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasename.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbaseusername.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.TheConnectionRequiresAValidDocumentumUserName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbaseusername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbasepassword.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.TheConnectionRequiresTheDocumentumUsersPassword") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasepassword.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelifetime.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.CacheLifetimeCannotBeNull") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Cache") + "\");\n"+
"    editconnection.cachelifetime.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelifetime.value != \"\" && !isInteger(editconnection.cachelifetime.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.CacheLifetimeMustBeAnInteger") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Cache") + "\");\n"+
"    editconnection.cachelifetime.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelrusize.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.CacheLRUSizeCannotBeNull") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Cache") + "\");\n"+
"    editconnection.cachelrusize.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.cachelrusize.value != \"\" && !isInteger(editconnection.cachelrusize.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.CacheLRUSizeMustBeAnInteger") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Cache") + "\");\n"+
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String docbaseName = parameters.getParameter(CONFIG_PARAM_DOCBASE);
    if (docbaseName == null)
      docbaseName = "";

    String docbaseUserName = parameters.getParameter(CONFIG_PARAM_USERNAME);
    if (docbaseUserName == null)
      docbaseUserName = "";

    String docbasePassword = parameters.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    if (docbasePassword == null)
      docbasePassword = "";
    else
      docbasePassword = out.mapPasswordToKey(docbasePassword);

    String docbaseDomain = parameters.getParameter(CONFIG_PARAM_DOMAIN);
    if (docbaseDomain == null)
      docbaseDomain = "";

    String caseInsensitiveUser = parameters.getParameter(CONFIG_PARAM_CASEINSENSITIVE);
    if (caseInsensitiveUser == null)
      caseInsensitiveUser = "false";

    String useSystemAcls = parameters.getParameter(CONFIG_PARAM_USESYSTEMACLS);
    if (useSystemAcls == null)
      useSystemAcls = "true";

    String cacheLifetime = parameters.getParameter(CONFIG_PARAM_CACHELIFETIME);
    if (cacheLifetime == null)
      cacheLifetime = "1";
    
    String cacheLRUsize = parameters.getParameter(CONFIG_PARAM_CACHELRUSIZE);
    if (cacheLRUsize == null)
      cacheLRUsize = "1000";    

    // "Docbase" tab
    if (tabName.equals(Messages.getString(locale,"DCTM.Docbase")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbasename\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseUserName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbaseusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseUserName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbasePassword") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"32\" name=\"docbasepassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbasePassword)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseDomain") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbasedomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseDomain)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for "Docbase" tab
      out.print(
"<input type=\"hidden\" name=\"docbasename\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseName)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbaseusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseUserName)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbasepassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbasePassword)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbasedomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseDomain)+"\"/>\n"
      );
    }

    // "User Mapping" tab
    if (tabName.equals(Messages.getString(locale,"DCTM.UserMapping")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.AuthenticationUsernameMatching") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"description\"><input name=\"usernamecaseinsensitive\" type=\"radio\" value=\"true\" "+((caseInsensitiveUser.equals("true"))?"checked=\"true\"":"")+" /></td>\n"+
"          <td class=\"value\"><nobr>" + Messages.getBodyString(locale,"DCTM.CaseInsensitive") + "</nobr></td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\"><input name=\"usernamecaseinsensitive\" type=\"radio\" value=\"false\" "+((!caseInsensitiveUser.equals("true"))?"checked=\"true\"":"")+" /></td>\n"+
"          <td class=\"value\"><nobr>" + Messages.getBodyString(locale,"DCTM.CaseSensitive") + "</nobr></td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for "User Mapping" tab
      out.print(
"<input type=\"hidden\" name=\"usernamecaseinsensitive\" value=\""+caseInsensitiveUser+"\"/>\n"
      );
    }

    // "System ACLs" tab
    if (tabName.equals(Messages.getString(locale,"DCTM.SystemACLs")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.UseSystemAcls") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"description\"><input name=\"usesystemacls\" type=\"radio\" value=\"true\" "+((useSystemAcls.equals("true"))?"checked=\"true\"":"")+" /></td>\n"+
"          <td class=\"value\"><nobr>" + Messages.getBodyString(locale,"DCTM.UseSystemAcls") + "</nobr></td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\"><input name=\"usesystemacls\" type=\"radio\" value=\"false\" "+((!useSystemAcls.equals("true"))?"checked=\"true\"":"")+" /></td>\n"+
"          <td class=\"value\"><nobr>" + Messages.getBodyString(locale,"DCTM.DontUseSystemAcls") + "</nobr></td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for "System ACLs" tab
      out.print(
"<input type=\"hidden\" name=\"usesystemacls\" value=\""+useSystemAcls+"\"/>\n"
      );
    }
    
    // "Cache" tab
    if(tabName.equals(Messages.getString(locale,"DCTM.Cache")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.CacheLifetime") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"cachelifetime\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLifetime) + "\"/> " + Messages.getBodyString(locale,"DCTM.minutes") + "</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.CacheLRUSize") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"cachelrusize\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLRUsize) + "\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for "Cache" tab
      out.print(
"<input type=\"hidden\" name=\"cachelifetime\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLifetime) + "\"/>\n"+
"<input type=\"hidden\" name=\"cachelrusize\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(cacheLRUsize) + "\"/>\n"
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    String docbaseName = variableContext.getParameter("docbasename");
    if (docbaseName != null)
      parameters.setParameter(CONFIG_PARAM_DOCBASE,docbaseName);
	
    String docbaseUserName = variableContext.getParameter("docbaseusername");
    if (docbaseUserName != null)
      parameters.setParameter(CONFIG_PARAM_USERNAME,docbaseUserName);
	
    String docbasePassword = variableContext.getParameter("docbasepassword");
    if (docbasePassword != null)
      parameters.setObfuscatedParameter(CONFIG_PARAM_PASSWORD,variableContext.mapKeyToPassword(docbasePassword));
	
    String docbaseDomain = variableContext.getParameter("docbasedomain");
    if (docbaseDomain != null)
      parameters.setParameter(CONFIG_PARAM_DOMAIN,docbaseDomain);

    String caseInsensitiveUser = variableContext.getParameter("usernamecaseinsensitive");
    if (caseInsensitiveUser != null)
      parameters.setParameter(CONFIG_PARAM_CASEINSENSITIVE,caseInsensitiveUser);

    String useSystemAcls = variableContext.getParameter("usesystemacls");
    if (useSystemAcls != null)
      parameters.setParameter(CONFIG_PARAM_USESYSTEMACLS,useSystemAcls);
    
    String cacheLifetime = variableContext.getParameter("cachelifetime");
    if (cacheLifetime != null)
      parameters.setParameter(CONFIG_PARAM_CACHELIFETIME,cacheLifetime);

    String cacheLRUsize = variableContext.getParameter("cachelrusize");
    if (cacheLRUsize != null)
      parameters.setParameter(CONFIG_PARAM_CACHELRUSIZE,cacheLRUsize);

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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"DCTM.Parameters") + "</nobr></td>\n"+
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
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" " + Messages.getBodyString(locale,"DCTM.certificate") + "&gt;</nobr><br/>\n"
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

  protected static StringSet emptyStringSet = new StringSet();

  /** This is the cache object descriptor for cached access tokens from
  * this connector.
  */
  protected static class AuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    // The parameters upon which the cached results are based.
    protected String userName;
    protected String docbaseName;
    protected String adminUserName;
    protected String adminPassword;
    protected String domain;
    protected boolean caseInsensitive;
    protected boolean useSystemACLs;
    /** The expiration time */
    protected long expirationTime = -1;
    /** The response lifetime */
    protected long responseLifetime;
    
    /** Constructor. */
    public AuthorizationResponseDescription(String userName, String docbaseName,
      String adminUserName, String adminPassword, String domain,
      boolean caseInsensitive, boolean useSystemACLs,
      long responseLifetime, int LRUsize)
    {
      super("DocumentumDirectoryAuthority",LRUsize);
      this.userName = userName;
      this.docbaseName = docbaseName;
      this.adminUserName = adminUserName;
      this.adminPassword = adminPassword;
      this.domain = domain;
      this.caseInsensitive = caseInsensitive;
      this.useSystemACLs = useSystemACLs;
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
      return getClass().getName() + "-" + userName + "-" + docbaseName +
        "-" + adminUserName + "-" + adminPassword + "-" + ((domain==null)?"NULL":domain) + "-" +
        (caseInsensitive?"true":"false") + "-" + (useSystemACLs?"true":"false");
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
      return userName.hashCode() + docbaseName.hashCode() + adminUserName.hashCode() +
        adminPassword.hashCode() + ((domain==null)?0:domain.hashCode()) +
        (caseInsensitive?1:0) + (useSystemACLs?1:0);
    }
    
    public boolean equals(Object o)
    {
      if (!(o instanceof AuthorizationResponseDescription))
        return false;
      AuthorizationResponseDescription ard = (AuthorizationResponseDescription)o;
      return ard.userName.equals(userName) && ard.docbaseName.equals(docbaseName) &&
        ard.adminUserName.equals(adminUserName) && ard.adminPassword.equals(adminPassword) &&
        ((ard.domain==null||domain==null)?(ard.domain == domain):(ard.domain.equals(domain))) &&
        ard.caseInsensitive == caseInsensitive && ard.useSystemACLs == useSystemACLs;
    }
    
  }

}
