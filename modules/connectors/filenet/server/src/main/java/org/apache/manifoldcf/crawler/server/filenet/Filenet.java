/* $Id: Filenet.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.server.filenet;

import java.rmi.*;
import org.apache.manifoldcf.crawler.common.filenet.*;
import java.net.*;

/** This class is the main server class, which gets run to start the rmi service that talks to Filenet.
*/
public class Filenet
{
  public static final String _rcsid = "@(#)$Id: Filenet.java 988245 2010-08-23 18:39:35Z kwright $";

  private Filenet()
  {
  }

  public static void main(String[] args)
  {
    try
    {
      FilenetFactoryImpl factory = new FilenetFactoryImpl();
      Naming.rebind("//127.0.0.1:8305/filenet_factory", factory);
      // sets the handle
      System.out.println("FileNet Server started and awaiting connections.");
      while (true)
      {
        Thread.sleep(600000L);
      }
    }
    catch (InterruptedException e)
    {
    }
    catch (RemoteException er)
    {
      System.err.println("Remote exception in FileNet.main: " + er);
      er.printStackTrace(System.err);
    }
    catch (MalformedURLException er)
    {
      System.err.println("Exception in FileNet.main: " + er);
      er.printStackTrace(System.err);
    }

  }
}
