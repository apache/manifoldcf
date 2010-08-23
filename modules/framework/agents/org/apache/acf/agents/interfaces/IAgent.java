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
package org.apache.acf.agents.interfaces;

import org.apache.acf.core.interfaces.*;

/** This interface describes the external functionality of an agent class.  Agents are all poked at
* start-up time; they run independently until the JVM is shut down.
* All agent classes are expected to support the following constructor:
*
* xxx(IThreadContext tc) throws ACFException
*
*/
public interface IAgent
{
  public static final String _rcsid = "@(#)$Id$";

  /** Install agent.  This usually installs the agent's database tables etc.
  */
  public void install()
    throws ACFException;

  /** Uninstall agent.  This must clean up everything the agent is responsible for.
  */
  public void deinstall()
    throws ACFException;

  /** Start the agent.  This method should spin up the agent threads, and
  * then return.
  */
  public void startAgent()
    throws ACFException;

  /** Stop the agent.  This should shut down the agent threads.
  */
  public void stopAgent()
    throws ACFException;

  /** Request permission from agent to delete an output connection.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  public boolean isOutputConnectionInUse(String connName)
    throws ACFException;

  /** Note the deregistration of a set of output connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  public void noteOutputConnectorDeregistration(String[] connectionNames)
    throws ACFException;

  /** Note the registration of a set of output connections.
  *@param connectionNames are the names of the connections being registered.
  */
  public void noteOutputConnectorRegistration(String[] connectionNames)
    throws ACFException;

  /** Note a change in configuration for an output connection.
  *@param connectionName is the name of the connection being changed.
  */
  public void noteOutputConnectionChange(String connectionName)
    throws ACFException;
  
}
