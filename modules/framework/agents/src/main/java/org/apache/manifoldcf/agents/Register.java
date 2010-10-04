/* $Id: Register.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * Use to register an agent by providing its class
 */
public class Register extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: Register.java 988245 2010-08-23 18:39:35Z kwright $";

  private final String className;

  public Register(String className)
  {
    this.className = className;
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IAgentManager mgr = AgentManagerFactory.make(tc);
    mgr.registerAgent(className);
    Logging.root.info("Successfully registered agent '"+className+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: Register <classname>");
      System.exit(1);
    }

    String className = args[0];
    try
    {
      Register register = new Register(className);
      register.execute();
      System.err.println("Successfully registered agent '"+className+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
