/* $Id: IAgentManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes the management of the agent registry.
*/
public interface IAgentManager
{
  public static final String _rcsid = "@(#)$Id: IAgentManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Install.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall.  Also uninstalls all remaining agents.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Register an agent.
  *@param className is the class.
  */
  public void registerAgent(String className)
    throws ManifoldCFException;

  /** Unregister an agent.
  *@param className is the class to unregister.
  */
  public void unregisterAgent(String className)
    throws ManifoldCFException;

  /** Remove an agent.
  * Use this when the agent cannot be invoked.  The agent becomes unavailable,
  * but its schema is not cleaned up.
  *@param className is the class to remove.
  */
  public void removeAgent(String className)
    throws ManifoldCFException;

  /** Get a list of all registered agent class names.
  *@return the classnames in an array.
  */
  public String[] getAllAgents()
    throws ManifoldCFException;

}
