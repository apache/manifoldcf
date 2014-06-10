/* $Id: IAgent.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes the external functionality of an agent class.  Agents are all poked at
* start-up time; they run independently until the JVM is shut down.
* All agent classes are expected to support the following constructor:
*
* xxx() throws ManifoldCFException
*
* Agent classes are furthermore expected to be cross-thread, but not necessarily thread-safe
* in that a given IAgent instance is meant to be used by only one thread at a time.  It is
* furthermore safe to keep stateful data in the IAgent instance object pertaining to the
* running state of the system.  That is, an instance of IAgent used to start the agent will be
* the same one stopAgent() is called with.
*/
public interface IAgent
{
  public static final String _rcsid = "@(#)$Id: IAgent.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Initialize agent environment.
  * This is called before any of the other operations are called, and is meant to insure that
  * the environment is properly initialized.
  */
  public void initialize(IThreadContext threadContext)
    throws ManifoldCFException;
  
  /** Tear down agent environment.
  * This is called after all the other operations are completed, and is meant to allow
  * environment resources to be freed.
  */
  public void cleanUp(IThreadContext threadContext)
    throws ManifoldCFException;
  
  /** Install agent.  This usually installs the agent's database tables etc.
  */
  public void install(IThreadContext threadContext)
    throws ManifoldCFException;

  /** Uninstall agent.  This must clean up everything the agent is responsible for.
  */
  public void deinstall(IThreadContext threadContext)
    throws ManifoldCFException;

  /** Called ONLY when no other active services of this kind are running.  Meant to be
  * used after the cluster has been down for an indeterminate period of time.
  */
  public void clusterInit(IThreadContext threadContext)
    throws ManifoldCFException;
    
  /** Cleanup after ALL agents processes.
  * Call this method to clean up dangling persistent state when a cluster is just starting
  * to come up.  This method CANNOT be called when there are any active agents
  * processes at all.
  *@param processID is the current process ID.
  */
  public void cleanUpAllAgentData(IThreadContext threadContext, String currentProcessID)
    throws ManifoldCFException;
  
  /** Cleanup after agents process.
  * Call this method to clean up dangling persistent state after agent has been stopped.
  * This method CANNOT be called when the agent is active, but it can
  * be called at any time and by any process in order to guarantee that a terminated
  * agent does not block other agents from completing their tasks.
  *@param currentProcessID is the current process ID.
  *@param cleanupProcessID is the process ID of the agent to clean up after.
  */
  public void cleanUpAgentData(IThreadContext threadContext, String currentProcessID, String cleanupProcessID)
    throws ManifoldCFException;

  /** Start the agent.  This method should spin up the agent threads, and
  * then return.
  *@param processID is the process ID to start up an agent for.
  */
  public void startAgent(IThreadContext threadContext, String processID)
    throws ManifoldCFException;

  /** Stop the agent.  This should shut down the agent threads.
  */
  public void stopAgent(IThreadContext threadContext)
    throws ManifoldCFException;

  /** Request permission from agent to delete an output connection.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  public boolean isOutputConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException;

  /** Note the deregistration of a set of output connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  public void noteOutputConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of a set of output connections.
  *@param connectionNames are the names of the connections being registered.
  */
  public void noteOutputConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException;

  /** Note a change in configuration for an output connection.
  *@param connectionName is the name of the connection being changed.
  */
  public void noteOutputConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException;

  /** Request permission from agent to delete a transformation connection.
  *@param connName is the name of the transformation connection.
  *@return true if the connection is in use, false otherwise.
  */
  public boolean isTransformationConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException;

  /** Note the deregistration of a set of transformation connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  public void noteTransformationConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of a set of transformation connections.
  *@param connectionNames are the names of the connections being registered.
  */
  public void noteTransformationConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException;

  /** Note a change in configuration for a transformation connection.
  *@param connectionName is the name of the connection being changed.
  */
  public void noteTransformationConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException;

}
