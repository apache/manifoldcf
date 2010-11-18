/* $Id: UnRegisterAll.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * Un-register all current agent classes
 */
public class UnRegisterAll extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: UnRegisterAll.java 988245 2010-08-23 18:39:35Z kwright $";

  public UnRegisterAll()
  {
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IAgentManager mgr = AgentManagerFactory.make(tc);
    String[] classnames = mgr.getAllAgents();
    int i = 0;
    while (i < classnames.length)
    {
      mgr.unregisterAgent(classnames[i++]);
    }
    Logging.root.info("Successfully unregistered all agents");
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: UnRegisterAll");
      System.exit(1);
    }

    try
    {
      UnRegisterAll unRegisterAll = new UnRegisterAll();
      unRegisterAll.execute();
      System.err.println("Successfully unregistered all agents");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
