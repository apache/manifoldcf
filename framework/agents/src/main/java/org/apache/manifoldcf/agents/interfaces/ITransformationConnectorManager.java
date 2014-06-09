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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This interface describes a manager for the registry of transformation connectors.
* Use this to register or remove a connector from the list of available choices.
*/
public interface ITransformationConnectorManager
{
  public static final String _rcsid = "@(#)$Id$";

  /** Install.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall.  This also unregisters all connectors.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Register a new connector.
  * The connector's install method will also be called.
  *@param description is the description to use in the UI.
  *@param className is the class name.
  */
  public void registerConnector(String description, String className)
    throws ManifoldCFException;

  /** Unregister a connector.
  * The connector's deinstall method will also be called.
  *@param className is the connector class to unregister.
  */
  public void unregisterConnector(String className)
    throws ManifoldCFException;

  /** Remove a connector.
  * Use this method when the connector doesn't seem to be in the
  * classpath, so deregistration cannot occur.
  *@param className is the connector class to remove.
  */
  public void removeConnector(String className)
    throws ManifoldCFException;

  /** Get ordered list of connectors.
  *@return a resultset with the columns "description" and "classname".
  * These will be ordered by description.
  */
  public IResultSet getConnectors()
    throws ManifoldCFException;

  /** Get a description given a class name.
  *@param className is the class name.
  *@return the description, or null if the class is not registered.
  */
  public String getDescription(String className)
    throws ManifoldCFException;

  /** Check if a particular connector is installed or not.
  *@param className is the class name of the connector.
  *@return true if installed, false otherwise.
  */
  public boolean isInstalled(String className)
    throws ManifoldCFException;

}
