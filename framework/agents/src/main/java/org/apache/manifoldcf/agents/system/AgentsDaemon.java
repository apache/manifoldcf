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
package org.apache.manifoldcf.agents.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import java.io.*;
import java.util.*;

public class AgentsDaemon
{
  public static final String _rcsid = "@(#)$Id$";

  /** Agent shutdown signal name */
  public static final String agentShutdownSignal = "_AGENTRUN_";
  /** Agent service name prefix (followed by agent class name) */
  public static final String agentServicePrefix = "AGENT_";

  /** The agents thread, which starts and stops agents daemons to keep them consistent with the database, and
  * also takes on process cleanup where necessary. */
  protected AgentsThread agentsThread = null;

  /** The idle cleanup thread. */
  protected IdleCleanupThread idleCleanupThread = null;
  
  /** Process ID for this agents daemon. */
  protected final String processID;
  
  /** This is the place we keep track of the agents we've started. */
  protected final Map<String,IAgent> runningHash = new HashMap<String,IAgent>();
  
  // There are a number of different ways of running the agents framework.
  // (1) Repeatedly call checkAgents(), and when all done make sure to call stopAgents().
  // (2) Call registerAgentsShutdownHook(), then repeatedly run checkAgents(),  Agent shutdown happens on JVM exit.
  // (3) Call runAgents(), which will wait for someone else to call assertAgentsShutdownSignal().  Before exit, stopAgents() must be called.
  // (4) Call registerAgentsShutdownHook(), then call runAgents(), which will wait for someone else to call assertAgentsShutdownSignal().  Shutdown happens on JVM exit.
  
  /** Create an agents daemon object.
  *@param processID is the process ID of this agents daemon.  Process ID's must be unique
  * for all agents daemons.
  */
  public AgentsDaemon(String processID)
  {
    this.processID = processID;
  }
  
  /** Assert shutdown signal for the current agents daemon.
  */
  public static void assertAgentsShutdownSignal(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.setGlobalFlag(agentShutdownSignal);
  }
  
  /** Clear shutdown signal for the current agents daemon.
  */
  public static void clearAgentsShutdownSignal(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.clearGlobalFlag(agentShutdownSignal);
  }


  /** Register agents shutdown hook.
  * Call this ONCE before calling startAgents or checkAgents the first time, if you want automatic cleanup of agents on JVM stop.
  */
  public void registerAgentsShutdownHook(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Create the shutdown hook for agents.  All activity will be keyed off of runningHash, so it is safe to do this under all conditions.
    org.apache.manifoldcf.core.system.ManifoldCF.addShutdownHook(new AgentsShutdownHook());
  }
  
  /** Run agents process.
  * This method will not return until a shutdown signal is sent.
  */
  public void runAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);

    // Don't come up at all if shutdown signal in force
    if (lockManager.checkGlobalFlag(agentShutdownSignal))
      return;

    // Create and start agents thread.
    startAgents(threadContext);
    
    while (true)
    {
      // Any shutdown signal yet?
      if (lockManager.checkGlobalFlag(agentShutdownSignal))
        break;
          
      try
      {
        ManifoldCF.sleep(5000L);
      }
      catch (InterruptedException e)
      {
        break;
      }
    }
    
  }

  /** Start agents thread for this agents daemon object.
  */
  public void startAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Create idle cleanup thread.
    idleCleanupThread = new IdleCleanupThread(processID);
    agentsThread = new AgentsThread();
    // Create and start agents thread.
    idleCleanupThread.start();
    agentsThread.start();
  }
  
  /** Stop all started agents running under this agents daemon.
  */
  public void stopAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Shut down agents background thread.
    while (agentsThread != null || idleCleanupThread != null)
    {
      if (agentsThread != null)
        agentsThread.interrupt();
      if (idleCleanupThread != null)
        idleCleanupThread.interrupt();
      
      if (agentsThread != null && !agentsThread.isAlive())
        agentsThread = null;
      if (idleCleanupThread != null && !idleCleanupThread.isAlive())
        idleCleanupThread = null;
    }
    
    // Shut down running agents services directly.
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    synchronized (runningHash)
    {
      // This is supposedly safe; iterator remove is used
      Iterator<String> iter = runningHash.keySet().iterator();
      while (iter.hasNext())
      {
        String className = iter.next();
        IAgent agent = runningHash.get(className);
        // Stop it
        agent.stopAgent(threadContext);
        lockManager.endServiceActivity(getAgentsClassServiceType(className), processID);
        iter.remove();
        agent.cleanUp(threadContext);
      }
    }
    // Done.
    OutputConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
    TransformationConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
  }

  protected static String getAgentsClassServiceType(String agentClassName)
  {
    return agentServicePrefix + agentClassName;
  }
  
  /** Agents thread.  This runs in background until interrupted, at which point
  * it shuts down.  Its responsibilities include cleaning up after dead processes,
  * as well as starting newly-registered agent processes, and terminating ones that disappear.
  */
  protected class AgentsThread extends Thread
  {
    public AgentsThread()
    {
      super();
      setName("Agents thread");
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        IThreadContext threadContext = ThreadContextFactory.make();
        while (true)
        {
          try
          {
            if (Thread.currentThread().isInterrupted())
              throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

            checkAgents(threadContext);
            ManifoldCF.sleep(5000L);
          }
          catch (InterruptedException e)
          {
            break;
          }
          catch (ManifoldCFException e)
          {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              break;
            if (e.getErrorCode() == ManifoldCFException.SETUP_ERROR)
            {
              System.err.println("Misconfigured ManifoldCF agents - shutting down");
              Logging.agents.fatal("AgentThread configuration exception tossed: "+e.getMessage(),e);
              System.exit(-200);
            }
            Logging.agents.error("Exception tossed: "+e.getMessage(),e);
          }
          catch (OutOfMemoryError e)
          {
            System.err.println("Agents process ran out of memory - shutting down");
            e.printStackTrace(System.err);
            System.exit(-200);
          }
          catch (Throwable e)
          {
            Logging.agents.fatal("Error tossed: "+e.getMessage(),e);
          }
        }
      }
      catch (Throwable e)
      {
        // Severe error on initialization
        System.err.println("Agents process could not start - shutting down");
        Logging.agents.fatal("AgentThread initialization error tossed: "+e.getMessage(),e);
        System.exit(-300);
      }
    }
  }

  /** Start all not-running agents.
  *@param threadContext is the thread context.
  */
  protected void checkAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    // Get agent manager
    IAgentManager manager = AgentManagerFactory.make(threadContext);
    synchronized (runningHash)
    {
      String[] classes = manager.getAllAgents();
      Set<String> currentAgentClasses = new HashSet<String>();

      int i = 0;
      while (i < classes.length)
      {
        String className = classes[i++];
        if (runningHash.get(className) == null)
        {
          // Start this agent
          IAgent agent = AgentFactory.make(className);
          String serviceType = getAgentsClassServiceType(className);
          agent.initialize(threadContext);
          try
          {
            // Throw a lock, so that cleanup processes and startup processes don't collide.
            lockManager.registerServiceBeginServiceActivity(serviceType, processID, new CleanupAgent(threadContext, agent, processID));
            // There is a potential race condition where the agent has been started but hasn't yet appeared in runningHash.
            // But having runningHash be the synchronizer for this activity will prevent any problems.
            agent.startAgent(threadContext, processID);
            // Successful!
            runningHash.put(className,agent);
          }
          catch (ManifoldCFException e)
          {
            if (e.getErrorCode() != ManifoldCFException.INTERRUPTED)
            {
              agent.cleanUp(threadContext);
              lockManager.endServiceActivity(serviceType, processID);
            }
            throw e;
          }
        }
        currentAgentClasses.add(className);
      }

      // Go through running hash and look for agents processes that have left
      Iterator<String> runningAgentsIterator = runningHash.keySet().iterator();
      while (runningAgentsIterator.hasNext())
      {
        String runningAgentClass = runningAgentsIterator.next();
        if (!currentAgentClasses.contains(runningAgentClass))
        {
          // Shut down this one agent.
          IAgent agent = runningHash.get(runningAgentClass);
          // Stop it
          agent.stopAgent(threadContext);
          lockManager.endServiceActivity(getAgentsClassServiceType(runningAgentClass), processID);
          runningAgentsIterator.remove();
          agent.cleanUp(threadContext);
        }
      }
    }

    synchronized (runningHash)
    {
      // For every class we're supposed to be running, find registered but no-longer-active instances and clean
      // up after them.
      for (String agentsClass : runningHash.keySet())
      {
        IAgent agent = runningHash.get(agentsClass);
        IServiceCleanup cleanup = new CleanupAgent(threadContext, agent, processID);
        String agentsClassServiceType = getAgentsClassServiceType(agentsClass);
        while (!lockManager.cleanupInactiveService(agentsClassServiceType, cleanup))
        {
          // Loop until no more inactive services
        }
      }
    }
    
  }

  /** Agent cleanup class.  This provides functionality to clean up after agents processes
  * that have gone away, or initialize an entire cluster.
  */
  protected static class CleanupAgent implements IServiceCleanup
  {
    protected final IAgent agent;
    protected final IThreadContext threadContext;
    protected final String processID;

    public CleanupAgent(IThreadContext threadContext, IAgent agent, String processID)
    {
      this.agent = agent;
      this.threadContext = threadContext;
      this.processID = processID;
    }
    
    /** Clean up after the specified service.  This method will block any startup of the specified
    * service for as long as it runs.
    *@param serviceName is the name of the service.
    */
    @Override
    public void cleanUpService(String serviceName)
      throws ManifoldCFException
    {
      agent.cleanUpAgentData(threadContext, processID, serviceName);
    }

    /** Clean up after ALL services of the type on the cluster.
    */
    @Override
    public void cleanUpAllServices()
      throws ManifoldCFException
    {
      agent.cleanUpAllAgentData(threadContext, processID);
    }
    
    /** Perform cluster initialization - that is, whatever is needed presuming that the
    * cluster has been down for an indeterminate period of time, but is otherwise in a clean
    * state.
    */
    @Override
    public void clusterInit()
      throws ManifoldCFException
    {
      agent.clusterInit(threadContext);
    }

  }
  
  /** Agents shutdown hook class */
  protected class AgentsShutdownHook implements IShutdownHook
  {

    public AgentsShutdownHook()
    {
    }
    
    @Override
    public void doCleanup(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // Shutting down in this way must prevent startup from taking place.
      stopAgents(threadContext);
    }
    
  }
  
}

