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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This interface describes the functionality in the authority group manager.
* The authority group manager manages the definitions of individual groups,
* and allows them to be defined, edited, and removed.
*/
public interface IAuthorityGroupManager
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

  /** Obtain a list of the authority groups, ordered by name.
  *@return an array of group objects.
  */
  public IAuthorityGroup[] getAllGroups()
    throws ManifoldCFException;

  /** Load a authority group by name.
  *@param name is the name of the authority group.
  *@return the loaded group object, or null if not found.
  */
  public IAuthorityGroup load(String name)
    throws ManifoldCFException;

  /** Load multiple authority groups by name.
  *@param names are the names to load.
  *@return the loaded group objects.
  */
  public IAuthorityGroup[] loadMultiple(String[] names)
    throws ManifoldCFException;

  /** Create a new authority group object.
  *@return the new object.
  */
  public IAuthorityGroup create()
    throws ManifoldCFException;

  /** Save an authority group object.
  *@param object is the object to save.
  *@return true if the object was created, false otherwise.
  */
  public boolean save(IAuthorityGroup object)
    throws ManifoldCFException;

  /** Delete an authority group.
  *@param name is the name of the group to delete.  If the
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
  public String getGroupNameColumn();

  /** Get the authority connection description column.
  *@return the description column.
  */
  public String getGroupDescriptionColumn();

}
