/* $Id: DocumentumObjectImpl.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This represents the limited equivalent of an IDfSysObject, containing only the properties that the crawler code
* needs to do its job.
*/
public class DocumentumObjectImpl extends UnicastRemoteObject implements IDocumentumObject
{
  public static final String _rcsid = "@(#)$Id: DocumentumObjectImpl.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Reference to the sysobject */
  protected IDfPersistentObject object;
  /** Reference to the session (for fetching more stuff when we need it) */
  protected IDfSession session;

  /** Constructor */
  public DocumentumObjectImpl(IDfSession session, IDfPersistentObject object)
    throws RemoteException
  {
    super(0,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
    this.session = session;
    this.object = object;
  }

  /** Release the object */
  public void release()
    throws RemoteException
  {
    object = null;
    session = null;
  }

  /** Does the object exist? */
  public boolean exists()
    throws DocumentumException, RemoteException
  {
    return (object!=null);
  }

  /** Get the object identifier */
  public String getObjectId()
    throws DocumentumException, RemoteException
  {
    try
    {
      return object.getObjectId().toString();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the object name */
  public String getObjectName()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getObjectName();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the object's content type */
  public String getContentType()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getContentType();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the ACL domain */
  public String getACLDomain()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getACLDomain();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the ACL name */
  public String getACLName()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getACLName();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Check if object is deleted */
  public boolean isDeleted()
    throws DocumentumException, RemoteException
  {
    try
    {
      return object.isDeleted();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Check if object is hidden */
  public boolean isHidden()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).isHidden();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get object's permit level */
  public int getPermit()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getPermit();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get object's content size */
  public long getContentSize()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getContentSize();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get object's page count */
  public int getPageCount()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getPageCount();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the object's version label */
  public String getVersionLabel()
    throws DocumentumException, RemoteException
  {
    try
    {
      String versionLabel;
      IDfVersionPolicy policy = ((IDfSysObject)object).getVersionPolicy();
      if (policy != null)
      {
        versionLabel = policy.getSameLabel();
        if (versionLabel == null)
          versionLabel = "";
      }
      else
        versionLabel = "";

      return versionLabel;
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }


  /** Get object type name */
  public String getTypeName()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getTypeName();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the vstamp field for the object */
  public String getVStamp()
    throws DocumentumException, RemoteException
  {
    try
    {
      return object.getString("i_vstamp");
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum exception: "+e.getMessage());
    }
  }

  /** Get the path set for the object.  This is the complete set of folder paths that lead to the object. */
  public String[] getFolderPaths(Map pathMapCache)
    throws DocumentumException, RemoteException
  {
    // Catch errors
    try
    {
      // Get the number of folder id's that own it
      int count = ((IDfSysObject)object).getFolderIdCount();
      // Accumulate translated strings
      ArrayList list = new ArrayList();
      int i = 0;
      while (i < count)
      {
        IDfId folderID = ((IDfSysObject)object).getFolderId(i++);
        String folderIDString = folderID.getId();
        // Look up folder's path
        String[] folderPath = (String[])pathMapCache.get(folderIDString);
        if (folderPath == null)
        {
          // Find the folder path (by loading the folder object)
          IDfFolder folder = (IDfFolder)session.getObject(folderID);
          if (folder == null)
            folderPath = new String[0];
          else
          {
            int folderPathCount = folder.getFolderPathCount();
            folderPath = new String[folderPathCount];
            int j = 0;
            while (j < folderPathCount)
            {
              folderPath[j] = folder.getFolderPath(j);
              j++;
            }
          }
          pathMapCache.put(folderIDString,folderPath);
        }
        int j = 0;
        while (j < folderPath.length)
        {
          list.add(folderPath[j++]);
        }
      }

      String[] rval = new String[list.size()];
      i = 0;
      while (i < rval.length)
      {
        rval[i] = (String)list.get(i);
        i++;
      }
      return rval;
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

  /** Write the content of the object to a file path.
  *@param path is where the content should be written.
  *@return the file path where the content was written. */
  public String getFile(String path)
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfSysObject)object).getFile(path);
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
        errorMessage.indexOf("[DM_PLATFORM_E_INTEGER_CONVERSION_ERROR]") != -1 ||
        errorMessage.indexOf("[DM_STORAGE_E_BAD_TICKET]") != -1)
      {
        throw new DocumentumException(dfe.getMessage(),DocumentumException.TYPE_CORRUPTEDDOCUMENT);
      }
      // Treat it as transient, and retry
      throw new DocumentumException(dfe.getMessage(),DocumentumException.TYPE_SERVICEINTERRUPTION);
    }
  }

  /** Get all the values that an attribute has, including multiple ones if present */
  public String[] getAttributeValues(String attribute)
    throws DocumentumException, RemoteException
  {
    try
    {
      int valueCount = object.getValueCount(attribute);
      String[] values = new String[valueCount];
      int y = 0;
      while (y < valueCount)
      {
        // Fetch the attribute.
        // It's supposed to work for all attribute types...
        String value = object.getRepeatingString(attribute,y);
        values[y++] = value;
      }
      return values;
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

  /** Get a user state */
  public int getUserState()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfUser)object).getUserState();
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

  /** Get a user's name */
  public String getUserName()
    throws DocumentumException, RemoteException
  {
    try
    {
      return ((IDfUser)object).getUserName();
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


}
