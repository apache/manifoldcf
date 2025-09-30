/* $Id: DocumentumImpl.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.DCTM;

import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import com.documentum.fc.client.*;
import com.documentum.fc.common.*;
import com.documentum.com.*;
import java.util.*;

/** This class abstracts away from the documentum methods necessary to "do things" that
* the crawler or authority needs to be done with the Documentum repository.  The purpose for breaking this
* out is to permit the Documentum invocation to be RMI based, because it keeps segfaulting on us.
*
* One of the tricks needed is to preserve session.  This is handled at this level by explicitly passing a
* session string around.  The session string is created on the remote JVM, and is subsequently used to describe
* the individual session we care about from the client side.
*/
public class DocumentumImpl extends UnicastRemoteObject implements IDocumentum
{
  public static final String _rcsid = "@(#)$Id: DocumentumImpl.java 988245 2010-08-23 18:39:35Z kwright $";

  // All the parameters we need to set up a session
  protected String docBaseName = null;
  protected String userName = null;
  protected String password = null;
  protected String domain = null;

  // This is the session manager
  protected IDfSessionManager sessionManager = null;
  // This is the DFC session; it may be null, or it may be set.
  protected IDfSession session = null;

  /** Instantiate */
  public DocumentumImpl()
    throws RemoteException
  {
    super(0,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
  }

  /** Get a DFC session.  This will be done every time it is needed.
  */
  protected IDfSession getSession()
    throws DocumentumException
  {
    if (session == null)
    {
      // Retry 5 times, with a one-second wait between attempts
      int retryCount = 5;
      while (true)
      {
        performSessionCreate();
        if (session == null || !session.isConnected())
        {
          if (retryCount == 0)
            throw new DocumentumException("Connection attempt failed!");
          retryCount--;
          try
          {
            Integer x = new Integer(0);
            synchronized (x)
            {
              x.wait(1000L);
            }
          }
          catch (InterruptedException e)
          {
          }
          continue;
        }
        break;
      }
    }
    return session;
  }

  /** Create a session.
  *@param userName is the username to use to establish the session.
  *@param password is the password to use to establish the session.
  *@param domain is the domain to use to establish the session.
  */
  public void createSession(String docBaseName, String userName, String password, String domain)
    throws DocumentumException, RemoteException
  {
    this.docBaseName = docBaseName;
    this.userName = userName;
    this.password = password;
    this.domain = domain;

    performSessionCreate();
  }

  /** Do the work of creating a session instance from scratch.
  */
  protected void performSessionCreate()
    throws DocumentumException
  {
    try
    {
      IDfClientX clientx = new DfClientX();
      IDfClient client = clientx.getLocalClient();

      //create a Session Manager object
      IDfSessionManager localSessionManager = client.newSessionManager();

      //create an IDfLoginInfo object named loginInfoObj
      IDfLoginInfo loginInfoObj = clientx.getLoginInfo();
      loginInfoObj.setUser(userName);
      loginInfoObj.setPassword(password);
      if (domain != null)
        loginInfoObj.setDomain(domain);

      //bind the Session Manager to the login info
      localSessionManager.setIdentity(docBaseName, loginInfoObj);

      //create a session using getSession;
      // NOTE: this will reuse a released session or create a new one
      session = localSessionManager.getSession(docBaseName);
      sessionManager = localSessionManager;
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }


  /** Delete a session.
  */
  public void destroySession()
    throws DocumentumException, RemoteException
  {
    if (session != null)
    {
      sessionManager.release(session);
      if (!session.isConnected())
      {
        // Successfully disconnected...
      }
      // Regardless of what happened, clean up
      session = null;
      sessionManager = null;
    }
  }

  /** Check if there is a working connection.
  */
  public void checkConnection()
    throws DocumentumException, RemoteException
  {
    // Simple call that requires that a session actually be established (I hope)
    getDocbaseName();
  }


  /** Read the docbase name based on the session.
  */
  public String getDocbaseName()
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      return objIDfSession.getDocbaseName();
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Get the server version.
  */
  public String getServerVersion()
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      return objIDfSession.getServerVersion();
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Get the current session id.
  */
  public String getSessionId()
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      return objIDfSession.getSessionId();
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Perform a DQL query.  What comes back from this is the equivalent of a DFC collection,
  * which I've represented as an interface that reads a resultset-like object in a streamed manner.
  *@param dql is the query that is to be fired off.
  *@return a resultset.  This differs somewhat from the documentum convention in that it is
  * ALWAYS returned, even if it is empty.
  */
  public IDocumentumResult performDQLQuery(String dql)
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      IDfQuery objIDfQuery = new DfQuery();
      objIDfQuery.setDQL(dql);
      // Documentum seems to ignore this, but set it anyway in case they fix it.
      objIDfQuery.setBatchSize(2048);
      return new DocumentumResultImpl(objIDfQuery.execute(objIDfSession, IDfQuery.DF_EXECREAD_QUERY));
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Get a documentum object, by qualification.  The qualification is a DQL query part.  The
  * returned object has properties as described by the methods of IDocumentumObject.
  */
  public IDocumentumObject getObjectByQualification(String dql)
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      IDfPersistentObject o = objIDfSession.getObjectByQualification(dql);
      if (o == null) {
          return null;
      }
      return new DocumentumObjectImpl(objIDfSession,o);
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException dfe)
    {
      // Can't decide what to do without looking at the exception text.
      // This is crappy but it's the best we can manage, apparently.
      String errorMessage = dfe.getMessage();
      if (errorMessage.indexOf("[DM_CONTENT_E_CANT_START_PULL]") != -1)
      {
        // It's probably not a transient error.  Report it as an access violation, even though it
        // may well not be.  We don't have much info as to what's happening.
        throw new DocumentumException(dfe.getMessage(),DocumentumException.TYPE_NOTALLOWED);
      }
      else if (errorMessage.indexOf("[DM_OBJECT_E_LOAD_INVALID_STRING_LEN]") != -1 ||
        errorMessage.indexOf("[DM_PLATFORM_E_INTEGER_CONVERSION_ERROR]") != -1)
      {
        throw new DocumentumException(dfe.getMessage(),DocumentumException.TYPE_CORRUPTEDDOCUMENT);
      }
      // Treat it as transient, and retry
      throw new DocumentumException(dfe.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
      //throw new DocumentumException("Documentum error: "+e.getMessage());
    }

  }

  /** Get folder contents */
  public IDocumentumResult getFolderContents(String folderPath)
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      IDfFolder objTheParentFolderNode = (IDfFolder) objIDfSession.getObjectByPath(folderPath);
      if (objTheParentFolderNode == null)
      {
        return new DocumentumResultImpl(null);
      }

      return new DocumentumResultImpl(objTheParentFolderNode.getContents(null));
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Check if an object type is equal to or is a subtype of any one of a set of other object types.
  */
  public boolean isOneOf(String theType, String[] matchTypeSet)
    throws DocumentumException, RemoteException
  {
    IDfSession objIDfSession = getSession();
    try
    {
      IDfType typeDescription = objIDfSession.getType(theType);
      int i = 0;
      while (i < matchTypeSet.length)
      {
        String matchType = matchTypeSet[i++];
        if (matchType.equalsIgnoreCase(theType))
          return true;
        if (typeDescription.isSubTypeOf(matchType))
          return true;
      }
      return false;
    }
    catch (DfAuthenticationException ex)
    {
      throw new DocumentumException("Bad credentials: "+ex.getMessage(),DocumentumException.TYPE_BADCREDENTIALS);
    }
    catch (DfIdentityException ex)
    {
      throw new DocumentumException("Bad docbase name: "+ex.getMessage(),DocumentumException.TYPE_BADCONNECTIONPARAMS);
    }
    catch (DfDocbaseUnreachableException e)
    {
      throw new DocumentumException("Docbase unreachable: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfIOException e)
    {
      throw new DocumentumException("Docbase io exception: "+e.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Build a DQL date string from a long timestamp */
  public String buildDateString(long timestamp)
    throws RemoteException
  {
    return "date('"+new DfTime(new Date(timestamp)).asString(IDfTime.DF_TIME_PATTERN44)+"','"+IDfTime.DF_TIME_PATTERN44+"')";
  }


}
