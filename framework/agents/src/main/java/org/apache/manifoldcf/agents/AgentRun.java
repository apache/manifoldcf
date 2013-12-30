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

  public static final String agentServiceType = "AGENT";
  
  public AgentRun()
  {
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    // Note well:
    // As part of CONNECTORS-781, multiple agents processes are now permitted, provided
    // that a truly global lock manager implementation is used.  This implementation thus does the
    // following:
    // (1) Register the agent, and begin its execution
    // (2) Periodically check for any new registered IAgent implementations
    // (3) Await a shutdown signal
    // (4) If exit signal seen, exit active block
    // (5) Trap JVM exit to be sure we exit active block no matter what
    //   (This latter option requires the ability to exit active blocks from different ILockManager instances)
    //
    // Note well that the agents shutdown signal is NEVER modified by this code; it will be set/cleared by
    // AgentStop only, and AgentStop will wait until all services become inactive before exiting.
    String processID = ManifoldCF.getProcessID();
    ILockManager lockManager = LockManagerFactory.make(tc);
    lockManager.registerServiceBeginServiceActivity(agentServiceType, processID, null);
    try
    {
      // Register a shutdown hook to make sure we signal that the main agents process is going inactive.
      ManifoldCF.addShutdownHook(new AgentRunShutdownRunner(processID));
      
      Logging.root.info("Running...");
      // Register hook first so stopAgents() not required
      AgentsDaemon ad = new AgentsDaemon(processID);
      ad.registerAgentsShutdownHook(tc);
      ad.runAgents(tc);
      Logging.root.info("Shutting down...");
    }
    catch (ManifoldCFException e)
    {
      Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
    }
    finally
    {
      // Exit service
      // This is a courtesy; some lock managers (i.e. ZooKeeper) manage to do this anyway
      lockManager.endServiceActivity(agentServiceType, processID);
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
  
  protected static class AgentRunShutdownRunner implements IShutdownHook
  {
    protected final String processID;
    
    public AgentRunShutdownRunner(String processID)
    {
      this.processID = processID;
    }
    
    @Override
    public void doCleanup(IThreadContext tc)
      throws ManifoldCFException
    {
      ILockManager lockManager = LockManagerFactory.make(tc);
      // We can blast the active flag off here; we may have already exited though and an exception will
      // therefore be thrown.
      if (lockManager.checkServiceActive(agentServiceType, processID))
      {
        lockManager.endServiceActivity(agentServiceType, processID);
      }
    }
    
  }
  
}
