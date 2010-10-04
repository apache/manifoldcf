/* $Id: DocumentumResultImpl.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface represents a streamed resultset from a documentum DQL
* query. */
public class DocumentumResultImpl extends UnicastRemoteObject implements IDocumentumResult
{
  public static final String _rcsid = "@(#)$Id: DocumentumResultImpl.java 988245 2010-08-23 18:39:35Z kwright $";

  // The documentum collection, or null if empty
  protected IDfCollection objIDfCollection;
  // Whether the current row is valid
  protected boolean isValid;

  /** Constructor */
  public DocumentumResultImpl(IDfCollection objIDfCollection)
    throws DocumentumException, RemoteException
  {
    super(0,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
    this.objIDfCollection = objIDfCollection;
    isValid = (objIDfCollection != null);
    if (isValid)
    {
      try
      {
        isValid = objIDfCollection.next();
      }
      catch (DfException e)
      {
        throw new DocumentumException("Documentum error: "+e.getMessage(),e);
      }
    }
  }

  /** Check if we are done with the resultset.
  *@return true if there is still a valid row to read out of, or false if the list is done. */
  public boolean isValidRow()
    throws DocumentumException, RemoteException
  {
    return isValid;
  }

  /** Get a string result value */
  public String getStringValue(String valueName)
    throws DocumentumException, RemoteException
  {
    try
    {
      return objIDfCollection.getString(valueName);
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Advance to the next row. */
  public void nextRow()
    throws DocumentumException, RemoteException
  {
    try
    {
      isValid = objIDfCollection.next();
    }
    catch (DfException e)
    {
      throw new DocumentumException("Documentum error: "+e.getMessage());
    }
  }

  /** Close and release the resources for this resultset.
  */
  public void close()
    throws DocumentumException, RemoteException
  {
    if (objIDfCollection != null)
    {
      try
      {
        objIDfCollection.close();
        objIDfCollection = null;
        isValid = false;
      }
      catch (DfException e)
      {
        throw new DocumentumException("Documentum error: "+e.getMessage());
      }
    }
  }

}
