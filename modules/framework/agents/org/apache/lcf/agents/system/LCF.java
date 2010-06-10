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
package org.apache.lcf.agents.system;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import java.io.*;
import java.util.*;

public class LCF extends org.apache.lcf.core.system.LCF
{
  public static final String _rcsid = "@(#)$Id$";

  // Agents initialized flag
  protected static boolean agentsInitialized = false;
  
  /** This is the place we keep track of the agents we've started. */
  protected static HashMap runningHash = new HashMap();
  /** This flag prevents startAgents() from starting anything once stopAgents() has been called. */
  protected static boolean stopAgentsRun = false;
  
  /** Initialize environment.
  */
  public static void initializeEnvironment()
    throws LCFException
  {
    synchronized (initializeFlagLock)
    {
      if (agentsInitialized)
        return;

      // Do core initialization
      org.apache.lcf.core.system.LCF.initializeEnvironment();
      
      // Create the shutdown hook for agents.  All activity will be keyed off of runningHash, so it is safe to do this under all conditions.
      org.apache.lcf.core.system.LCF.addShutdownHook(new AgentsShutdownHook());
      
      // Initialize the local loggers
      Logging.initializeLoggers();
      Logging.setLogLevels();
      agentsInitialized = true;
    }
  }


  /** Install the agent tables.  This is also responsible for upgrading the existing
  * tables!!!
  *@param threadcontext is the thread context.
  */
  public static void installTables(IThreadContext threadcontext)
    throws LCFException
  {
    IAgentManager mgr = AgentManagerFactory.make(threadcontext);
    IIncrementalIngester igstmgr = IncrementalIngesterFactory.make(threadcontext);
    IOutputConnectorManager outputConnMgr = OutputConnectorManagerFactory.make(threadcontext);
    IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadcontext);
    mgr.install();
    outputConnMgr.install();
    outputConnectionManager.install();
    igstmgr.install();
  }

  /** Uninstall all the crawler system tables.
  *@param threadcontext is the thread context.
  */
  public static void deinstallTables(IThreadContext threadcontext)
    throws LCFException
  {
    IAgentManager mgr = AgentManagerFactory.make(threadcontext);
    IIncrementalIngester igstmgr = IncrementalIngesterFactory.make(threadcontext);
    IOutputConnectorManager outputConnMgr = OutputConnectorManagerFactory.make(threadcontext);
    IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadcontext);
    igstmgr.deinstall();
    outputConnectionManager.deinstall();
    outputConnMgr.deinstall();
    mgr.deinstall();
  }

  /** Start all not-running agents.
  *@param threadContext is the thread context.
  */
  public static void startAgents(IThreadContext threadContext)
    throws LCFException
  {
    // Get agent manager
    IAgentManager manager = AgentManagerFactory.make(threadContext);
    String[] classes = manager.getAllAgents();
    LCFException problem = null;
    synchronized (runningHash)
    {
      // DO NOT permit this method to do anything if stopAgents() has ever been called for this JVM! 
      // (If it has, it means that the JVM is trying to shut down.)
      if (stopAgentsRun)
        return;
      int i = 0;
      while (i < classes.length)
      {
        String className = classes[i++];
        if (runningHash.get(className) == null)
        {
          // Start this agent
          IAgent agent = AgentFactory.make(threadContext,className);
          try
          {
            // There is a potential race condition where the agent has been started but hasn't yet appeared in runningHash.
            // But having runningHash be the synchronizer for this activity will prevent any problems.
            // There is ANOTHER potential race condition, however, that can occur if the process is shut down just before startAgents() is called.
            // We avoid that problem by means of a flag, which prevents startAgents() from doing anything once stopAgents() has been called.
            agent.startAgent();
            // Successful!
            runningHash.put(className,agent);
          }
          catch (LCFException e)
          {
            problem = e;
          }
        }
      }
    }
    if (problem != null)
      throw problem;
    // Done.
  }

  /** Stop all started agents.
  */
  public static void stopAgents(IThreadContext threadContext)
    throws LCFException
  {
    synchronized (runningHash)
    {
      HashMap iterHash = (HashMap)runningHash.clone();
      Iterator iter = iterHash.keySet().iterator();
      while (iter.hasNext())
      {
        String className = (String)iter.next();
        IAgent agent = (IAgent)runningHash.get(className);
        // Stop it
        agent.stopAgent();
        runningHash.remove(className);
      }
    }
    // Done.
  }
  
  /** Signal output connection needs redoing.
  * This is called when something external changed on an output connection, and
  * therefore all associated documents must be reindexed.
  *@param threadContext is the thread context.
  *@param connectionName is the connection name.
  */
  public static void signalOutputConnectionRedo(IThreadContext threadContext, String connectionName)
    throws LCFException
  {
    // Blow away the incremental ingestion table first
    IIncrementalIngester ingester = IncrementalIngesterFactory.make(threadContext);
    ingester.resetOutputConnection(connectionName);
    // Now, signal to all agents that the output connection configuration has changed.  Do this second, so that there cannot be documents
    // resulting from this signal that find themselves "unchanged".
    AgentManagerFactory.noteOutputConnectionChange(threadContext,connectionName);
  }
  
  /** Agents shutdown hook class */
  protected static class AgentsShutdownHook implements IShutdownHook
  {
    
    public AgentsShutdownHook()
    {
    }
    
    public void doCleanup()
      throws LCFException
    {
      // Shutting down in this way must prevent startup from taking place.
      synchronized (runningHash)
      {
        stopAgentsRun = true;
      }
      IThreadContext tc = ThreadContextFactory.make();
      stopAgents(tc);
    }
    
  }
  
}

