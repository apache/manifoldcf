/* $Id: UnRegisterOutput.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * Un-register an output connector class
 */
public class UnRegisterOutput extends TransactionalAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: UnRegisterOutput.java 988245 2010-08-23 18:39:35Z kwright $";

  private final String className;

  public UnRegisterOutput(String className)
  {
    this.className = className;
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
    IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
    // Find the connection names that come with this class
    String[] connectionNames = connManager.findConnectionsForConnector(className);
    // For all connection names, notify all agents of the deregistration
    AgentManagerFactory.noteOutputConnectorDeregistration(tc,connectionNames);
    // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
    mgr.unregisterConnector(className);
    Logging.root.info("Successfully unregistered output connector '"+className+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: UnRegisterOutput <classname>");
      System.exit(1);
    }

    String className = args[0];

    try
    {
      UnRegisterOutput unRegisterOutput = new UnRegisterOutput(className);
      unRegisterOutput.execute();
      System.err.println("Successfully unregistered output connector '"+className+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
