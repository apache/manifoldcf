/* $Id: AgentStop.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.system.*;

/**
 * Stops the running agents process
 */
public class AgentStop extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: AgentStop.java 988245 2010-08-23 18:39:35Z kwright $";

  public AgentStop()
  {
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    // As part of the work for CONNECTORS-781, this method is now synchronous.
    // We assert the shutdown signal, and then wait until all active services have shut down.
    ILockManager lockManager = LockManagerFactory.make(tc);
    AgentsDaemon.assertAgentsShutdownSignal(tc);
    try
    {
      Logging.root.info("Shutdown signal sent");
      while (true)
      {
        // Check to see if services are down yet
        int count = lockManager.countActiveServices(AgentRun.agentServiceType);
        if (count == 0)
          break;
        try
        {
          ManifoldCF.sleep(1000L);
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
      Logging.root.info("All agents shut down");
    }
    finally
    {
      // Clear shutdown signal
      AgentsDaemon.clearAgentsShutdownSignal(tc);
    }
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: AgentStop");
      System.exit(1);
    }

    try
    {
      AgentStop agentStop = new AgentStop();
      agentStop.execute();
      System.err.println("Shutdown signal sent");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
}
