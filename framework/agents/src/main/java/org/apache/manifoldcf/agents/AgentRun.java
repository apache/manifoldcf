/* $Id: AgentRun.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * Main agents process class
 */
public class AgentRun extends BaseAgentsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: AgentRun.java 988245 2010-08-23 18:39:35Z kwright $";

  public static final String agentInUseSignal = "_AGENTINUSE_";
  public static final String agentShutdownSignal = "_AGENTRUN_";
  
  public AgentRun()
  {
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(tc);
    // Agent already in use?
    if (lockManager.checkGlobalFlag(agentInUseSignal))
    {
      System.err.println("Agent already in use");
      System.exit(1);
    }
    // Set the agents in use signal.
    lockManager.setGlobalFlag(agentInUseSignal);    
    try
    {
      // Clear the agents shutdown signal.
      lockManager.clearGlobalFlag(agentShutdownSignal);
      Logging.root.info("Running...");
      while (true)
      {
        // Any shutdown signal yet?
        if (lockManager.checkGlobalFlag(agentShutdownSignal))
          break;

        // Start whatever agents need to be started
        ManifoldCF.startAgents(tc);

        try
        {
          ManifoldCF.sleep(5000);
        }
        catch (InterruptedException e)
        {
          break;
        }
      }
      Logging.root.info("Shutting down...");
    }
    catch (ManifoldCFException e)
    {
      Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
    }
    finally
    {
      lockManager.clearGlobalFlag(agentInUseSignal);
    }
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: AgentRun");
      System.exit(1);
    }

    try
    {
      System.err.println("Running...");
      AgentRun agentRun = new AgentRun();
      agentRun.execute();
      System.err.println("Shutting down...");
    }
    catch (ManifoldCFException e)
    {
      Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
}
