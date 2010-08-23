/* $Id: SynchronizeAll.java 988101 2010-08-23 12:18:13Z kwright $ */

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
package org.apache.acf.agents;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.agents.system.*;

/**
 * Un-register all registered agent classes that can't be found
 */
public class SynchronizeAll extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: SynchronizeAll.java 988101 2010-08-23 12:18:13Z kwright $";

  public SynchronizeAll()
  {
  }

  protected void doExecute(IThreadContext tc) throws LCFException
  {
    IAgentManager mgr = AgentManagerFactory.make(tc);
    String[] classnames = mgr.getAllAgents();
    int i = 0;
    while (i < classnames.length)
    {
      String classname = classnames[i++];
      try
      {
        AgentFactory.make(tc,classname);
      }
      catch (LCFException e)
      {
        // Couldn't instantiate the agent: Remove from database table
        mgr.removeAgent(classname);
      }
    }
    Logging.root.info("Successfully synchronized all agents");
  }

  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: SynchronizeAll");
      System.exit(1);
    }

    try
    {
      SynchronizeAll synchronizeAll = new SynchronizeAll();
      synchronizeAll.execute();
      System.err.println("Successfully synchronized all agents");
    }
    catch (LCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
