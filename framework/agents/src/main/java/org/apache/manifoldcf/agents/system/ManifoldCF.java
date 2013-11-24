/* $Id: ManifoldCF.java 988245 2010-08-23 18:39:35Z kwright $ */

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

public class ManifoldCF extends org.apache.manifoldcf.core.system.ManifoldCF
{
  public static final String _rcsid = "@(#)$Id: ManifoldCF.java 988245 2010-08-23 18:39:35Z kwright $";

  public static final String agentShutdownSignal = "_AGENTRUN_";

  // Agents initialized flag
  protected static boolean agentsInitialized = false;
  
  /** This is the place we keep track of the agents we've started. */
  protected static Map<String,IAgent> runningHash = new HashMap<String,IAgent>();
  /** This flag prevents startAgents() from starting anything once stopAgents() has been called. */
  protected static boolean stopAgentsRun = false;
  
  /** Initialize environment.
  */
  public static void initializeEnvironment(IThreadContext threadContext)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      // Do core initialization
      org.apache.manifoldcf.core.system.ManifoldCF.initializeEnvironment(threadContext);
      // Local initialization
      org.apache.manifoldcf.agents.system.ManifoldCF.localInitialize(threadContext);
    }
  }

  /** Clean up environment.
  */
  public static void cleanUpEnvironment(IThreadContext threadContext)
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.agents.system.ManifoldCF.localCleanup(threadContext);
      org.apache.manifoldcf.core.system.ManifoldCF.cleanUpEnvironment(threadContext);
    }
  }
  
  public static void localInitialize(IThreadContext threadContext)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      if (agentsInitialized)
        return;

      // Initialize the local loggers
      Logging.initializeLoggers();
      Logging.setLogLevels(threadContext);
      agentsInitialized = true;
    }
  }

  public static void localCleanup(IThreadContext threadContext)
  {
  }
  
  /** Reset the environment.
  */
  public static void resetEnvironment(IThreadContext threadContext)
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.core.system.ManifoldCF.resetEnvironment(threadContext);
      synchronized (runningHash)
      {
        stopAgentsRun = false;
      }
    }
  }

  /** Install the agent tables.  This is also responsible for upgrading the existing
  * tables!!!
  *@param threadcontext is the thread context.
  */
  public static void installTables(IThreadContext threadcontext)
    throws ManifoldCFException
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
    throws ManifoldCFException
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

  // There are a number of different ways of running the agents framework.
  // (1) Repeatedly call checkAgents(), and when all done make sure to call stopAgents().
  // (2) Call registerAgentsShutdownHook(), then repeatedly run checkAgents(),  Agent shutdown happens on JVM exit.
  // (3) Call runAgents(), which will wait for someone else to call assertAgentsShutdownSignal().  Before exit, stopAgents() must be called.
  // (4) Call registerAgentsShutdownHook(), then call runAgents(), which will wait for someone else to call assertAgentsShutdownSignal().  Shutdown happens on JVM exit.
  
  /** Assert shutdown signal.
  */
  public static void assertAgentsShutdownSignal(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.setGlobalFlag(agentShutdownSignal);
  }
  
  /** Clear shutdown signal.
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
  public static void registerAgentsShutdownHook(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
    // Create the shutdown hook for agents.  All activity will be keyed off of runningHash, so it is safe to do this under all conditions.
    org.apache.manifoldcf.core.system.ManifoldCF.addShutdownHook(new AgentsShutdownHook(processID));
  }
  
  /** Agent service name prefix (followed by agent class name) */
  public static final String agentServicePrefix = "AGENT_";
  
  /** Run agents process.
  * This method will not return until a shutdown signal is sent.
  */
  public static void runAgents(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);

    // Don't come up at all if shutdown signal in force
    if (lockManager.checkGlobalFlag(agentShutdownSignal))
      return;

    while (true)
    {
      // Any shutdown signal yet?
      if (lockManager.checkGlobalFlag(agentShutdownSignal))
        break;
          
      // Start whatever agents need to be started
      checkAgents(threadContext, processID);

      try
      {
        ManifoldCF.sleep(5000);
      }
      catch (InterruptedException e)
      {
        break;
      }
    }
  }

  protected final static String agentClassLockPrefix = "_AGENTCLASSLOCK_";
  
  protected static String getAgentsClassLockName(String agentClassName)
  {
    return agentClassLockPrefix + agentClassName;
  }
  
  protected static String getAgentsClassServiceType(String agentClassName)
  {
    return agentServicePrefix + agentClassName;
  }
  
  /** Start all not-running agents.
  *@param threadContext is the thread context.
  */
  public static void checkAgents(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    // Get agent manager
    IAgentManager manager = AgentManagerFactory.make(threadContext);
    ManifoldCFException problem = null;
    synchronized (runningHash)
    {
      // DO NOT permit this method to do anything if stopAgents() has ever been called for this JVM! 
      // (If it has, it means that the JVM is trying to shut down.)
      if (stopAgentsRun)
        return;
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
          try
          {
            // Throw a lock, so that cleanup processes and startup processes don't collide.
            String lockName = getAgentsClassLockName(className);
            boolean firstTime;
            lockManager.enterWriteLock(lockName);
            try
            {
              firstTime = lockManager.registerServiceBeginServiceActivity(getAgentsClassServiceType(className), processID);
            }
            finally
            {
              lockManager.leaveWriteLock(lockName);
            }
            // Now initialize agent, being sure to clean up data from previous incarnations
            agent.initialize(threadContext);
            if (firstTime)
              agent.cleanUpAgentData(threadContext);
            else
              agent.cleanUpAgentData(threadContext, processID);
            // There is a potential race condition where the agent has been started but hasn't yet appeared in runningHash.
            // But having runningHash be the synchronizer for this activity will prevent any problems.
            agent.startAgent(threadContext, processID);
            // Successful!
            runningHash.put(className,agent);
          }
          catch (ManifoldCFException e)
          {
            problem = e;
            agent.cleanUp(threadContext);
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
          try
          {
            // Stop it
            agent.stopAgent(threadContext);
            lockManager.endServiceActivity(getAgentsClassServiceType(runningAgentClass), processID);
            runningAgentsIterator.remove();
            agent.cleanUp(threadContext);
          }
          catch (ManifoldCFException e)
          {
            problem = e;
          }
        }
      }
      
      // For every class we're supposed to be running, find registered but no-longer-active instances and clean
      // up after them.
      for (String agentsClass : runningHash.keySet())
      {
        IAgent agent = runningHash.get(agentsClass);
        // Look for dead service instances for this class.
        // This cannot happen at the same time as other processes doing this check, or at the same
        // time as a service of that class starting up, so we need a lock to prevent those situations.
        String lockName = getAgentsClassLockName(agentsClass);
        lockManager.enterWriteLock(lockName);
        try
        {
          // Find the derelict agents of this class, clean them up, and deregister them.
          String agentsClassServiceType = getAgentsClassServiceType(agentsClass);
          String[] inactiveAgents = lockManager.getInactiveServices(agentsClassServiceType);
          for (String inactiveAgentProcessID : inactiveAgents)
          {
            agent.cleanUpAgentData(threadContext, inactiveAgentProcessID);
            // Deregister
            lockManager.unregisterService(agentsClassServiceType, inactiveAgentProcessID);
          }
        }
        catch (ManifoldCFException e)
        {
          problem = e;
        }
        finally
        {
          lockManager.leaveWriteLock(lockName);
        }
      }

    }
    if (problem != null)
      throw problem;
    // Done.
  }

  /** Stop all started agents.
  */
  public static void stopAgents(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
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
  }
  
  /** Signal output connection needs redoing.
  * This is called when something external changed on an output connection, and
  * therefore all associated documents must be reindexed.
  *@param threadContext is the thread context.
  *@param connectionName is the connection name.
  */
  public static void signalOutputConnectionRedo(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
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
    protected final String processID;

    public AgentsShutdownHook(String processID)
    {
      this.processID = processID;
    }
    
    public void doCleanup()
      throws ManifoldCFException
    {
      // Shutting down in this way must prevent startup from taking place.
      synchronized (runningHash)
      {
        stopAgentsRun = true;
      }
      IThreadContext tc = ThreadContextFactory.make();
      stopAgents(tc,processID);
    }
    
  }
  
  // Helper methods for API support.  These are made public so connectors can use them to implement the executeCommand method.
  
  // These are the universal node types.
  
  protected static final String API_ERRORNODE = "error";
  protected static final String API_SERVICEINTERRUPTIONNODE = "service_interruption";
  
  /** Find a configuration node given a name */
  public static ConfigurationNode findConfigurationNode(Configuration input, String argumentName)
  {
    // Look for argument among the children
    int i = 0;
    while (i < input.getChildCount())
    {
      ConfigurationNode cn = input.findChild(i++);
      if (cn.getType().equals(argumentName))
        return cn;
    }
    return null;

  }
  
  /** Find a configuration value given a name */
  public static String getRootArgument(Configuration input, String argumentName)
  {
    ConfigurationNode node = findConfigurationNode(input,argumentName);
    if (node == null)
      return null;
    return node.getValue();
  }

  /** Create an error node with a general error message. */
  public static void createErrorNode(Configuration output, String errorMessage)
    throws ManifoldCFException
  {
    ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
    error.setValue(errorMessage);
    output.addChild(output.getChildCount(),error);
  }


  /** Handle an exception, by converting it to an error node. */
  public static void createErrorNode(Configuration output, ManifoldCFException e)
    throws ManifoldCFException
  {
    if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
      throw e;
    Logging.api.error(e.getMessage(),e);
    createErrorNode(output,e.getMessage());
  }

  /** Handle a service interruption, by converting it to a serviceinterruption node. */
  public static void createServiceInterruptionNode(Configuration output, ServiceInterruption e)
  {
    Logging.api.warn(e.getMessage(),e);
    ConfigurationNode error = new ConfigurationNode(API_SERVICEINTERRUPTIONNODE);
    error.setValue(e.getMessage());
    output.addChild(output.getChildCount(),error);
  }


}

