/* $Id$ */

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
package org.apache.lcf.agents;

import java.io.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.*;

public class RegisterOutput
{
  public static final String _rcsid = "@(#)$Id$";

  private RegisterOutput()
  {
  }


  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      System.err.println("Usage: RegisterOutput <classname> <description>");
      System.exit(1);
    }

    String className = args[0];
    String description = args[1];

    try
    {
      LCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      IDBInterface database = DBInterfaceFactory.make(tc,
        LCF.getMasterDatabaseName(),
        LCF.getMasterDatabaseUsername(),
        LCF.getMasterDatabasePassword());
      IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
      IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
      // Deregistration should be done in a transaction
      database.beginTransaction();
      try
      {
        // First, register connector
        mgr.registerConnector(description,className);
        // Then, signal to all jobs that might depend on this connector that they can switch state
        // Find the connection names that come with this class
        String[] connectionNames = connManager.findConnectionsForConnector(className);
        // For all connection names, notify all agents of the registration
        AgentManagerFactory.noteOutputConnectorRegistration(tc,connectionNames);
      }
      catch (LCFException e)
      {
        database.signalRollback();
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
      }

      System.err.println("Successfully registered output connector '"+className+"'");
    }
    catch (LCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }




}
