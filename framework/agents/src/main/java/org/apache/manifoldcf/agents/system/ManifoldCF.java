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
    // Close all pools
    try
    {
      OutputConnectorPoolFactory.make(threadContext).closeAllConnectors();
    }
    catch (ManifoldCFException e)
    {
      if (Logging.agents != null)
        Logging.agents.warn("Exception shutting down output connector pool: "+e.getMessage(),e);
    }
  }
  
  /** Reset the environment.
  */
  public static void resetEnvironment(IThreadContext threadContext)
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.core.system.ManifoldCF.resetEnvironment(threadContext);
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
    ITransformationConnectorManager transConnMgr = TransformationConnectorManagerFactory.make(threadcontext);
    ITransformationConnectionManager transConnectionManager = TransformationConnectionManagerFactory.make(threadcontext);
    mgr.install();
    outputConnMgr.install();
    outputConnectionManager.install();
    transConnMgr.install();
    transConnectionManager.install();
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
    ITransformationConnectorManager transConnMgr = TransformationConnectorManagerFactory.make(threadcontext);
    ITransformationConnectionManager transConnectionManager = TransformationConnectionManagerFactory.make(threadcontext);
    igstmgr.deinstall();
    transConnectionManager.deinstall();
    transConnMgr.deinstall();
    outputConnectionManager.deinstall();
    outputConnMgr.deinstall();
    mgr.deinstall();
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
    IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadContext);
    ingester.resetOutputConnection(outputConnectionManager.load(connectionName));
    // Now, signal to all agents that the output connection configuration has changed.  Do this second, so that there cannot be documents
    // resulting from this signal that find themselves "unchanged".
    AgentManagerFactory.noteOutputConnectionChange(threadContext,connectionName);
  }

  /** Signal output connection has been deleted.
  * This is called when the target of an output connection has been removed,
  * therefore all associated documents were also already removed.
  *@param threadContext is the thread context.
  *@param connectionName is the connection name.
  */
  public static void signalOutputConnectionRemoved(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Blow away the incremental ingestion table first
    IIncrementalIngester ingester = IncrementalIngesterFactory.make(threadContext);
    IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadContext);
    ingester.removeOutputConnection(outputConnectionManager.load(connectionName));
    // Now, signal to all agents that the output connection configuration has changed.  Do this second, so that there cannot be documents
    // resulting from this signal that find themselves "unchanged".
    AgentManagerFactory.noteOutputConnectionChange(threadContext,connectionName);
  }
  
  /** Qualify output activity name.
  *@param outputActivityName is the name of the output activity.
  *@param outputConnectionName is the corresponding name of the output connection.
  *@return the qualified (global) activity name.
  */
  public static String qualifyOutputActivityName(String outputActivityName, String outputConnectionName)
  {
    return outputActivityName+" ("+outputConnectionName+")";
  }

  /** Qualify transformation activity name.
  *@param transformationActivityName is the name of the output activity.
  *@param transformationConnectionName is the corresponding name of the transformation connection.
  *@return the qualified (global) activity name.
  */
  public static String qualifyTransformationActivityName(String transformationActivityName, String transformationConnectionName)
  {
    return transformationActivityName+" ["+transformationConnectionName+"]";
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

