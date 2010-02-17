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
package org.apache.lcf.authorities.interfaces;

import org.apache.lcf.core.interfaces.*;
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
		throws MetacartaException;

	/** Uninstall the manager.
	*/
	public void deinstall()
		throws MetacartaException;

	/** Export configuration */
	public void exportConfiguration(java.io.OutputStream os)
		throws java.io.IOException, MetacartaException;
	
	/** Import configuration */
	public void importConfiguration(java.io.InputStream is)
		throws java.io.IOException, MetacartaException;

	/** Obtain a list of the authority connections, ordered by name.
	*@return an array of connection objects.
	*/
	public IAuthorityConnection[] getAllConnections()
		throws MetacartaException;

	/** Load a authority connection by name.
	*@param name is the name of the authority connection.
	*@return the loaded connection object, or null if not found.
	*/
	public IAuthorityConnection load(String name)
		throws MetacartaException;

	/** Create a new authority connection object.
	*@return the new object.
	*/
	public IAuthorityConnection create()
		throws MetacartaException;

	/** Save an authority connection object.
	*@param object is the object to save.
	*/
	public void save(IAuthorityConnection object)
		throws MetacartaException;

	/** Delete an authority connection.
	*@param name is the name of the connection to delete.  If the
	* name does not exist, no error is returned.
	*/
	public void delete(String name)
		throws MetacartaException;

	// Schema related

	/** Get the authority connection table name.
	*@return the table name.
	*/
	public String getTableName();

	/** Get the authority connection name column.
	*@return the name column.
	*/
	public String getAuthorityNameColumn();

}
