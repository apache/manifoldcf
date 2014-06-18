/* $Id: IAuthorityConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This interface describes the functionality in the authority connection manager.
* The authority connection manager manages the definitions of individual connections,
* and allows them to be defined, edited, and removed.
*/
public interface IAuthorityConnectionManager
{
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

  /** Return true if the specified authority group name is referenced.
  *@param authorityGroup is the authority group name.
  *@return true if referenced, false otherwise.
  */
  public boolean isGroupReferenced(String authorityGroup)
    throws ManifoldCFException;

  /** Obtain a list of the authority connections, ordered by name.
  *@return an array of connection objects.
  */
  public IAuthorityConnection[] getAllConnections()
    throws ManifoldCFException;

  /** Obtain a list of the authority connections which correspond to an auth domain.
  *@param authDomain is the domain to get connections for.
  *@return an array of connection objects.
  */
  public IAuthorityConnection[] getDomainConnections(String authDomain)
    throws ManifoldCFException;

  /** Load a authority connection by name.
  *@param name is the name of the authority connection.
  *@return the loaded connection object, or null if not found.
  */
  public IAuthorityConnection load(String name)
    throws ManifoldCFException;

  /** Load multiple repository connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  public IAuthorityConnection[] loadMultiple(String[] names)
    throws ManifoldCFException;

  /** Create a new authority connection object.
  *@return the new object.
  */
  public IAuthorityConnection create()
    throws ManifoldCFException;

  /** Save an authority connection object.
  *@param object is the object to save.
  *@return true if the object was created, false otherwise.
  */
  public boolean save(IAuthorityConnection object)
    throws ManifoldCFException;

  /** Delete an authority connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws ManifoldCFException;

  // Schema related

  /** Get the authority connection table name.
  *@return the table name.
  */
  public String getTableName();

  /** Get the authority connection name column.
  *@return the name column.
  */
  public String getAuthorityNameColumn();

  /** Return true if the specified mapping name is referenced.
  *@param mappingName is the mapping name.
  *@return true if referenced, false otherwise.
  */
  public boolean isMappingReferenced(String mappingName)
    throws ManifoldCFException;

}
