/* $Id: IDocumentumResult.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface represents a streamed resultset from a documentum DQL
* query. */
public interface IDocumentumResult extends Remote
{
  public static final String _rcsid = "@(#)$Id: IDocumentumResult.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Check if we are done with the resultset.
  *@return true if there is still a valid row to read out of, or false if the list is done. */
  public boolean isValidRow()
    throws DocumentumException, RemoteException;

  /** Get a string result value */
  public String getStringValue(String valueName)
    throws DocumentumException, RemoteException;

  /** Advance to the next row. */
  public void nextRow()
    throws DocumentumException, RemoteException;

  /** Close and release the resources for this resultset.
  */
  public void close()
    throws DocumentumException, RemoteException;

}
