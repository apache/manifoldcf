/* $Id: IOutputConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $ */

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

/** Manager classes of this kind use the database to contain a human description of an output connection.
*/
public interface IOutputConnectionManager
{
  public static final String _rcsid = "@(#)$Id: IOutputConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $";

  /** Install the manager.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall the manager.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException;

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException;

  /** Obtain a list of the output connections, ordered by name.
  *@return an array of connection objects.
  */
  public IOutputConnection[] getAllConnections()
    throws ManifoldCFException;

  /** Load an output connection by name.
  *@param name is the name of the output connection.
  *@return the loaded connection object, or null if not found.
  */
  public IOutputConnection load(String name)
    throws ManifoldCFException;

  /** Load a set of output connections.
  *@param names are the names of the output connections.
  *@return the descriptors of the output connections, with null
  * values for those not found.
  */
  public IOutputConnection[] loadMultiple(String[] names)
    throws ManifoldCFException;

  /** Create a new output connection object.
  *@return the new object.
  */
  public IOutputConnection create()
    throws ManifoldCFException;

  /** Save an output connection object.
  *@param object is the object to save.
  *@return true if the object was created, false otherwise.
  */
  public boolean save(IOutputConnection object)
    throws ManifoldCFException;

  /** Delete an output connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws ManifoldCFException;

  /** Get a list of output connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the repository connections that use that connector.
  */
  public String[] findConnectionsForConnector(String className)
    throws ManifoldCFException;

  /** Check if underlying connector exists.
  *@param name is the name of the connection to check.
  *@return true if the underlying connector is registered.
  */
  public boolean checkConnectorExists(String name)
    throws ManifoldCFException;

  // Schema related

  /** Return the primary table name.
  *@return the table name.
  */
  public String getTableName();

  /** Return the name column.
  *@return the name column.
  */
  public String getConnectionNameColumn();

}
