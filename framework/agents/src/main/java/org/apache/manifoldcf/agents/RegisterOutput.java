/* $Id: RegisterOutput.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * Register an output connector class
 */
public class RegisterOutput extends TransactionalAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: RegisterOutput.java 988245 2010-08-23 18:39:35Z kwright $";

  private final String className;
  private final String description;

  public RegisterOutput(String className, String description)
  {

    this.className = className;
    this.description = description;
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
    IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
    // First, register connector
    mgr.registerConnector(description,className);
    // Then, signal to all jobs that might depend on this connector that they can switch state
    // Find the connection names that come with this class
    String[] connectionNames = connManager.findConnectionsForConnector(className);
    // For all connection names, notify all agents of the registration
    AgentManagerFactory.noteOutputConnectorRegistration(tc,connectionNames);
    Logging.root.info("Successfully registered output connector '"+className+"'");
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
      RegisterOutput registerOutput = new RegisterOutput(className, description);
      registerOutput.execute();
      System.err.println("Successfully registered output connector '"+className+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
