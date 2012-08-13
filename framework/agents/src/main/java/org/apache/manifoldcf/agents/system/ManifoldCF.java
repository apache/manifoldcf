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

  // Agents initialized flag
  protected static boolean agentsInitialized = false;
  
  /** This is the place we keep track of the agents we've started. */
  protected static HashMap runningHash = new HashMap();
  /** This flag prevents startAgents() from starting anything once stopAgents() has been called. */
  protected static boolean stopAgentsRun = false;
  
  /** Initialize environment.
  */
  public static void initializeEnvironment()
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      if (agentsInitialized)
        return;

      // Do core initialization
      org.apache.manifoldcf.core.system.ManifoldCF.initializeEnvironment();
      
      // Create the shutdown hook for agents.  All activity will be keyed off of runningHash, so it is safe to do this under all conditions.
      org.apache.manifoldcf.core.system.ManifoldCF.addShutdownHook(new AgentsShutdownHook());
      
      // Initialize the local loggers
      Logging.initializeLoggers();
      Logging.setLogLevels();
      agentsInitialized = true;
    }
  }

  /** Reset the environment.
  */
  public static void resetEnvironment()
  {
    org.apache.manifoldcf.core.system.ManifoldCF.resetEnvironment();
    synchronized (runningHash)
    {
      stopAgentsRun = false;
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

  /** Start all not-running agents.
  *@param threadContext is the thread context.
  */
  public static void startAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
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
          catch (ManifoldCFException e)
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
    throws ManifoldCFException
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
    
    public AgentsShutdownHook()
    {
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
      stopAgents(tc);
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
  
  /** Handle an exception, by converting it to an error node. */
  public static void createErrorNode(Configuration output, ManifoldCFException e)
    throws ManifoldCFException
  {
    if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
      throw e;
    Logging.api.error(e.getMessage(),e);
    ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
    error.setValue(e.getMessage());
    output.addChild(output.getChildCount(),error);
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

