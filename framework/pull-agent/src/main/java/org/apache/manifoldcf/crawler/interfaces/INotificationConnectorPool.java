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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

import java.util.*;
import java.io.*;

/** An object implementing this interface functions as a pool of notification connectors.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public interface INotificationConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get multiple notification connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param notificationConnections are the connections to use the build the connector instances.
  */
  public INotificationConnector[] grabMultiple(String[] orderingKeys, INotificationConnection[] authorityConnections)
    throws ManifoldCFException;

  /** Get a notification connector.
  * The connector is specified by a notification connection object.
  *@param notificationConnection is the authority connection to base the connector instance on.
  */
  public INotificationConnector grab(INotificationConnection notificationConnection)
    throws ManifoldCFException;

  /** Release multiple notification connectors.
  *@param connections are the connections describing the instances to release.
  *@param connectors are the connector instances to release.
  */
  public void releaseMultiple(INotificationConnection[] connections, INotificationConnector[] connectors)
    throws ManifoldCFException;

  /** Release a notification connector.
  *@param connection is the connection describing the instance to release.
  *@param connector is the connector to release.
  */
  public void release(INotificationConnection connection, INotificationConnector connector)
    throws ManifoldCFException;

  /** Idle notification for inactive notification connector handles.
  * This method polls all inactive handles.
  */
  public void pollAllConnectors()
    throws ManifoldCFException;

  /** Flush only those connector handles that are currently unused.
  */
  public void flushUnusedConnectors()
    throws ManifoldCFException;

  /** Clean up all open notification connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  */
  public void closeAllConnectors()
    throws ManifoldCFException;

}
