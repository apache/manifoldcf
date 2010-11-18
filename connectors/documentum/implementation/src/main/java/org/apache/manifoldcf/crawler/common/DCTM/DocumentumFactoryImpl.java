/* $Id: DocumentumFactoryImpl.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This class mints IDocumentum objects for clients, and does nothing else at all.
*/
public class DocumentumFactoryImpl extends UnicastRemoteObject implements IDocumentumFactory
{
  public static final String _rcsid = "@(#)$Id: DocumentumFactoryImpl.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Instantiate */
  public DocumentumFactoryImpl()
    throws RemoteException
  {
    super(0,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
  }

  /** Mint a new IDocumentum handle.
  */
  public IDocumentum make()
    throws RemoteException
  {
    return new DocumentumImpl();
  }
}
