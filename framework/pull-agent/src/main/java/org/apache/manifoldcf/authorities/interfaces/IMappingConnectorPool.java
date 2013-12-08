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
import java.io.*;

/** An object implementing this interface functions as a pool of mapping connectors.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public interface IMappingConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get multiple mapping connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param mappingConnections are the connections to use the build the connector instances.
  */
  public IMappingConnector[] grabMultiple(String[] orderingKeys, IMappingConnection[] mappingConnections)
    throws ManifoldCFException;

  /** Get a mapping connector.
  * The connector is specified by a mapping connection object.
  *@param mappingConnection is the mapping connection to base the connector instance on.
  */
  public IMappingConnector grab(IMappingConnection mappingConnection)
    throws ManifoldCFException;

  /** Release multiple mapping connectors.
  *@param connections are the connections describing the instances to release.
  *@param connectors are the connector instances to release.
  */
  public void releaseMultiple(IMappingConnection[] connections, IMappingConnector[] connectors)
    throws ManifoldCFException;

  /** Release a mapping connector.
  *@param connection is the connection describing the instance to release.
  *@param connector is the connector to release.
  */
  public void release(IMappingConnection connection, IMappingConnector connector)
    throws ManifoldCFException;

  /** Idle notification for inactive mapping connector handles.
  * This method polls all inactive handles.
  */
  public void pollAllConnectors()
    throws ManifoldCFException;

  /** Flush only those connector handles that are currently unused.
  */
  public void flushUnusedConnectors()
    throws ManifoldCFException;

  /** Clean up all open mapping connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  */
  public void closeAllConnectors()
    throws ManifoldCFException;

}
