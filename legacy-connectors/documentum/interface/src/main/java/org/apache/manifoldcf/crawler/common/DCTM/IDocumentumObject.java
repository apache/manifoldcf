/* $Id: IDocumentumObject.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This represents the limited equivalent of an IDfSysObject, containing only the properties that the crawler code
* needs to do its job.
*/
public interface IDocumentumObject extends Remote
{
  public static final String _rcsid = "@(#)$Id: IDocumentumObject.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Release the object */
  public void release()
    throws RemoteException;

  /** Does the object exist? */
  public boolean exists()
    throws DocumentumException, RemoteException;

  /** Get the object identifier */
  public String getObjectId()
    throws DocumentumException, RemoteException;

  /** Get the object name */
  public String getObjectName()
    throws DocumentumException, RemoteException;

  /** Get the object's content type */
  public String getContentType()
    throws DocumentumException, RemoteException;

  /** Get the ACL domain */
  public String getACLDomain()
    throws DocumentumException, RemoteException;

  /** Get the ACL name */
  public String getACLName()
    throws DocumentumException, RemoteException;

  /** Check if object is deleted */
  public boolean isDeleted()
    throws DocumentumException, RemoteException;

  /** Check if object is hidden */
  public boolean isHidden()
    throws DocumentumException, RemoteException;

  /** Get object's permit level */
  public int getPermit()
    throws DocumentumException, RemoteException;

  /** Get object's content size */
  public long getContentSize()
    throws DocumentumException, RemoteException;

  /** Get object's page count */
  public int getPageCount()
    throws DocumentumException, RemoteException;

  /** Get the object's version label */
  public String getVersionLabel()
    throws DocumentumException, RemoteException;

  /** Get object type name */
  public String getTypeName()
    throws DocumentumException, RemoteException;

  /** Get the vstamp field for the object */
  public String getVStamp()
    throws DocumentumException, RemoteException;

  /** Get the path set for the object.  This is the complete set of folder paths that lead to the object. */
  public String[] getFolderPaths(Map pathMapCache)
    throws DocumentumException, RemoteException;

  /** Write the content of the object to a file path.
  *@param path is where the content should be written.
  *@return the file path where the content was written. */
  public String getFile(String path)
    throws DocumentumException, RemoteException;

  /** Get all the values that an attribute has, including multiple ones if present */
  public String[] getAttributeValues(String attribute)
    throws DocumentumException, RemoteException;

  /** Get a user state */
  public int getUserState()
    throws DocumentumException, RemoteException;

  /** Get a user's name */
  public String getUserName()
    throws DocumentumException, RemoteException;

}
