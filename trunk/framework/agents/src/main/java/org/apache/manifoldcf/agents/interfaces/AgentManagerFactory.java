/* $Id: AgentManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

/** Agent manager factory class.
*/
public class AgentManagerFactory
{
  public static final String _rcsid = "@(#)$Id: AgentManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String agentManager = "_AgentManager_";

  private AgentManagerFactory()
  {
  }

  /** Make an agent manager instance.
  *@param threadContext is the thread context.
  *@return the manager.
  */
  public static IAgentManager make(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Object o = threadContext.get(agentManager);
    if (o == null || !(o instanceof IAgentManager))
    {
      IDBInterface database = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.agents.agentmanager.AgentManager(threadContext,database);
      threadContext.save(agentManager,o);
    }
    return (IAgentManager)o;
  }

  /** Request permission from all registered agents to delete an output connection.
  *@param threadContext is the thread context.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  public static boolean isOutputConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      if (theAgents[i++].isOutputConnectionInUse(threadContext, connName))
        return true;
    }
    return false;
  }

  /**  Note to all registered agents the deregistration of an output connector used by the specified connections.
  * This method will be called when the connector is deregistered.
  *@param threadContext is the thread context.
  *@param connectionNames is the set of connection names.
  */
  public static void noteOutputConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteOutputConnectorDeregistration(threadContext, connectionNames);
    }
  }

  /** Note to all registered agents the registration of an output connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param threadContext is the thread context.
  *@param connectionNames is the set of connection names.
  */
  public static void noteOutputConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteOutputConnectorRegistration(threadContext, connectionNames);
    }
  }

  /** Note to all registered agents the change of configuration of an output connection.
  * This method will be called when the connection's xml is modified.
  *@param threadContext is the thread context.
  *@param connectionName is the connection name.
  */
  public static void noteOutputConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteOutputConnectionChange(threadContext, connectionName);
    }
  }

    /** Request permission from all registered agents to delete a transformation connection.
  *@param threadContext is the thread context.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  public static boolean isTransformationConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      if (theAgents[i++].isTransformationConnectionInUse(threadContext, connName))
        return true;
    }
    return false;
  }

  /**  Note to all registered agents the deregistration of a transformation connector used by the specified connections.
  * This method will be called when the connector is deregistered.
  *@param threadContext is the thread context.
  *@param connectionNames is the set of connection names.
  */
  public static void noteTransformationConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteTransformationConnectorDeregistration(threadContext, connectionNames);
    }
  }

  /** Note to all registered agents the registration of a transformation connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param threadContext is the thread context.
  *@param connectionNames is the set of connection names.
  */
  public static void noteTransformationConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteTransformationConnectorRegistration(threadContext, connectionNames);
    }
  }

  /** Note to all registered agents the change of configuration of a transformation connection.
  * This method will be called when the connection's xml is modified.
  *@param threadContext is the thread context.
  *@param connectionName is the connection name.
  */
  public static void noteTransformationConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Instantiate the list of IAgent objects
    IAgent[] theAgents = instantiateAllAgents(threadContext);
    int i = 0;
    while (i < theAgents.length)
    {
      theAgents[i++].noteTransformationConnectionChange(threadContext, connectionName);
    }
  }

  /** Instantiate the complete set of IAgent objects.
  *@param threadContext is the thread context.
  *@return the array of such objects.
  */
  public static IAgent[] instantiateAllAgents(IThreadContext threadContext)
    throws ManifoldCFException
  {
    IAgentManager manager = make(threadContext);
    String[] allAgents = manager.getAllAgents();
    IAgent[] rval = new IAgent[allAgents.length];
    int i = 0;
    while (i < rval.length)
    {
      rval[i] = AgentFactory.make(allAgents[i]);
      i++;
    }
    return rval;
  }

}
