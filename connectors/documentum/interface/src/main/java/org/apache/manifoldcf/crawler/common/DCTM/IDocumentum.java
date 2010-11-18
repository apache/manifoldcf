/* $Id: IDocumentum.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class abstracts away from the documentum methods necessary to "do things" that
* the crawler or authority needs to be done with the Documentum repository.  The purpose for breaking this
* out is to permit the Documentum invocation to be RMI based, because it keeps segfaulting on us.
*
* One of the tricks needed is to preserve session.  This is handled at this level by explicitly passing a
* session string around.  The session string is created on the remote JVM, and is subsequently used to describe
* the individual session we care about from the client side.
*/
public interface IDocumentum extends Remote
{
  public static final String _rcsid = "@(#)$Id: IDocumentum.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Create a session.
  *@param docbaseName is the name of the docbase we want to connect to.
  *@param userName is the username to use to establish the session.
  *@param password is the password to use to establish the session.
  *@param domain is the domain to use to establish the session.
  */
  public void createSession(String docbaseName, String userName, String password, String domain)
    throws DocumentumException, RemoteException;

  /** Delete the session.
  */
  public void destroySession()
    throws DocumentumException, RemoteException;

  /** Check if there is a working connection.
  */
  public void checkConnection()
    throws DocumentumException, RemoteException;

  /** Read the docbase name based on the session.
  */
  public String getDocbaseName()
    throws DocumentumException, RemoteException;

  /** Get the server version.
  */
  public String getServerVersion()
    throws DocumentumException, RemoteException;

  /** Get the current session id.
  */
  public String getSessionId()
    throws DocumentumException, RemoteException;

  /** Perform a DQL query.  What comes back from this is the equivalent of a DFC collection,
  * which I've represented as an interface that reads a resultset-like object in a streamed manner.
  *@param dql is the query that is to be fired off.
  *@return a resultset.  This differs somewhat from the documentum convention in that it is
  * ALWAYS returned, even if it is empty.
  */
  public IDocumentumResult performDQLQuery(String dql)
    throws DocumentumException, RemoteException;

  /** Get a documentum object, by qualification.  The qualification is a DQL query part.  The
  * returned object has properties as described by the methods of IDocumentumObject.
  */
  public IDocumentumObject getObjectByQualification(String dql)
    throws DocumentumException, RemoteException;

  /** Get folder contents */
  public IDocumentumResult getFolderContents(String folderPath)
    throws DocumentumException, RemoteException;

  /** Check if an object type is equal to or is a subtype of any one of a set of other object types.
  */
  public boolean isOneOf(String theType, String[] matchTypeSet)
    throws DocumentumException, RemoteException;


  // Helper methods for building DQL queries

  /** Build a DQL date string from a long timestamp */
  public String buildDateString(long timestamp)
    throws RemoteException;

}
