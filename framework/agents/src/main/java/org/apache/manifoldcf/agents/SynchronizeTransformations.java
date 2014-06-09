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
package org.apache.manifoldcf.agents;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

/**
 * Un-register all registered transformation connector classes that can't be found
 */
public class SynchronizeTransformations extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id$";

  public SynchronizeTransformations()
  {
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IDBInterface database = DBInterfaceFactory.make(tc,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
    ITransformationConnectorManager mgr = TransformationConnectorManagerFactory.make(tc);
    ITransformationConnectionManager connManager = TransformationConnectionManagerFactory.make(tc);
    IResultSet classNames = mgr.getConnectors();
    int i = 0;
    while (i < classNames.getRowCount())
    {
      IResultRow row = classNames.getRow(i++);
      String className = (String)row.getValue("classname");
      try
      {
        TransformationConnectorFactory.getConnectorNoCheck(className);
      }
      catch (ManifoldCFException e)
      {
        // Deregistration should be done in a transaction
        database.beginTransaction();
        try
        {
          // Find the connection names that come with this class
          String[] connectionNames = connManager.findConnectionsForConnector(className);
          // For all connection names, notify all agents of the deregistration
          AgentManagerFactory.noteTransformationConnectorDeregistration(tc,connectionNames);
          // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
          mgr.removeConnector(className);
        }
        catch (ManifoldCFException e2)
        {
          database.signalRollback();
          throw e2;
        }
        catch (Error e2)
        {
          database.signalRollback();
          throw e2;
        }
        finally
        {
          database.endTransaction();
        }
      }
    }
    Logging.root.info("Successfully synchronized all transformations");
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: SynchronizeTransformations");
      System.exit(1);
    }

    try
    {
      SynchronizeTransformations synchronizeTransformations = new SynchronizeTransformations();
      synchronizeTransformations.execute();
      System.err.println("Successfully synchronized all transformations");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
