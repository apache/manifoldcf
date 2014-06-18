/* $Id: DCTM.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.registry.DCTM;

import java.rmi.*;
import org.apache.manifoldcf.crawler.common.DCTM.RMILocalSocketFactory;
import org.apache.manifoldcf.crawler.common.DCTM.RMILocalClientSocketFactory;

/** This class is the main registry class, which gets run to start the registry service that handles Documentum server communication.
* I needed to create my own since the rmiregistry utility did not let me override the java security policy.
*/
public class DCTM
{
  public static final String _rcsid = "@(#)$Id: DCTM.java 988245 2010-08-23 18:39:35Z kwright $";

  private DCTM()
  {
  }


  public static void main(String[] args)
  {
    try
    {
      java.rmi.registry.Registry r = java.rmi.registry.LocateRegistry.createRegistry(8300,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
      // Registry started OK
      System.out.println("Documentum Registry started and awaiting connections.");
      // Sleep forever, until process is externally terminated
      while (true)
      {
        Thread.sleep(10000L);
      }
    }
    catch (InterruptedException e)
    {
    }
    catch (RemoteException er)
    {
      System.err.println("Remote exception in DCTM.main: " + er);
      er.printStackTrace(System.err);
    }
  }
}
