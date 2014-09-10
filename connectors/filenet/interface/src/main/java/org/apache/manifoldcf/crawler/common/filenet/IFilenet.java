/* $Id: IFilenet.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.filenet;

import java.rmi.*;
import java.util.*;

/** This class abstracts away from the filenet methods necessary to "do things" that
* the crawler or authority needs to be done with the Filenet repository.  The purpose for breaking this
* out is to permit the Filenet invocation to be RMI based, because it relies
* on too many specific versions of jars.
*
* One of the tricks needed is to preserve session.  This is handled at this level by explicitly passing a
* session string around.  The session string is created on the remote JVM, and is subsequently used to describe
* the individual session we care about from the client side.
*/
public interface IFilenet extends Remote
{
  public static final String _rcsid = "@(#)$Id: IFilenet.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Create a session.
  *@param userID is the userID to use to establish the session.
  *@param password is the password to use to establish the session.
  *@param domain is the filenet domain of the user and password.
  *@param objectStore is the object store to use to establish the session.
  *@param serverWSIURI is the URI to use to get to the server's web services.
  */
  public void createSession(String userID, String password, String domain, String objectStore, String serverWSIURI)
    throws FilenetException, RemoteException;

  /** Delete a session.
  */
  public void destroySession()
    throws FilenetException, RemoteException;

  /** Check if there is a working connection.
  */
  public void checkConnection()
    throws FilenetException, RemoteException;

  /** Get the set of folder names that are children of the specified folder path. */
  public String[] getChildFolders(String[] parentFolderPath)
    throws FilenetException, RemoteException;

  /** Get the set of available document classes
  */
  public DocumentClassDefinition[] getDocumentClassesDetails()
    throws FilenetException, RemoteException;

  /** Get the set of available metadata fields per document class
  */
  public MetadataFieldDefinition[] getDocumentClassMetadataFieldsDetails(String documentClassName)
    throws FilenetException, RemoteException;

  /** Execute a sql statement against FileNet and return the matching object id's */
  public String[] getMatchingObjectIds(String sql)
    throws RemoteException, FilenetException;

  /** Get the document content information given an object id.  Will return null if the version id is not a current document version id. */
  public Integer getDocumentContentCount(String docId)
    throws RemoteException, FilenetException;

  /** Get document information for a given filenet document.  Will return null if the version id is not a current document version id.
  * The metadataFields hashmap is keyed by document class, and contains as a value either null (meaning "all"), or a String[] that has the
  * list of fields desired. */
  public FileInfo getDocumentInformation(String docId, Map<String,Object> metadataFields)
    throws FilenetException, RemoteException;

  /** Get document contents */
  public void getDocumentContents(String docId, int elementNumber, String tempFileName)
    throws FilenetException, RemoteException;


}
