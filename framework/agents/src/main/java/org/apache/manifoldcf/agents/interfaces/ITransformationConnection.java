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

/** An instance of this interface represents a paper object that describes a transformation connection.
* This is the paper object meant for editing and manipulation.
*/
public interface ITransformationConnection
{
  public static final String _rcsid = "@(#)$Id$";

  /** Set 'isnew' condition.
  *@param isnew true if this is a new instance.
  */
  public void setIsNew(boolean isnew);
  
  /** Get 'isnew' condition.
  *@return true if this is a new connection, false otherwise.
  */
  public boolean getIsNew();
  
  /** Set name.
  *@param name is the name.
  */
  public void setName(String name);

  /** Get name.
  *@return the name
  */
  public String getName();

  /** Set description.
  *@param description is the description.
  */
  public void setDescription(String description);

  /** Get description.
  *@return the description
  */
  public String getDescription();

  /** Set the class name.
  *@param className is the class name.
  */
  public void setClassName(String className);

  /** Get the class name.
  *@return the class name
  */
  public String getClassName();

  /** Get the configuration parameters.
  *@return the map.  Can be modified.
  */
  public ConfigParams getConfigParams();

  /** Set the maximum size of the connection pool.
  *@param maxCount is the maximum connection count per JVM.
  */
  public void setMaxConnections(int maxCount);

  /** Get the maximum size of the connection pool.
  *@return the maximum size.
  */
  public int getMaxConnections();

}
