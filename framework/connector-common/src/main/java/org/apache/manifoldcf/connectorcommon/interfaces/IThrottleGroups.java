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
package org.apache.manifoldcf.connectorcommon.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

import java.util.*;

/** An IThrottleGroups object is thread-local and creates a virtual pool
* of connections to resources whose access needs to be throttled in number, 
* rate of use, and byte rate.
*/
public interface IThrottleGroups
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get all existing throttle groups for a throttle group type.
  * The throttle group type typically describes a connector class, while the throttle group represents
  * a namespace of bin names specific to that connector class.
  *@param throttleGroupType is the throttle group type.
  *@return the set of throttle groups for that group type.
  */
  public Set<String> getThrottleGroups(String throttleGroupType)
    throws ManifoldCFException;
  
  /** Remove a throttle group.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  */
  public void removeThrottleGroup(String throttleGroupType, String throttleGroup)
    throws ManifoldCFException;
  
  /** Create or update a throttle group.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the desired throttle specification object.
  */
  public void createOrUpdateThrottleGroup(String throttleGroupType, String throttleGroup, IThrottleSpec throttleSpec)
    throws ManifoldCFException;

  /** Construct connection throttler for connections with specific bin names.  This object is meant to be embedded with a connection
  * pool of similar objects, and used to gate the creation of new connections in that pool.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames are the connection type bin names.
  *@return the connection throttling object, or null if the pool is being shut down.
  */
  public IConnectionThrottler obtainConnectionThrottler(String throttleGroupType, String throttleGroup, String[] binNames)
    throws ManifoldCFException;
  
  /** Poll periodically, to update cluster-wide statistics and allocation.
  *@param throttleGroupType is the throttle group type to update.
  */
  public void poll(String throttleGroupType)
    throws ManifoldCFException;

  /** Poll periodically, to update ALL cluster-wide statistics and allocation.
  */
  public void poll()
    throws ManifoldCFException;

  /** Free all unused resources.
  */
  public void freeUnusedResources()
    throws ManifoldCFException;
  
  /** Shut down throttler permanently.
  */
  public void destroy()
    throws ManifoldCFException;

}
