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
package org.apache.manifoldcf.core.interfaces;

import java.util.*;

/** An IConnectionThrottler object is thread-local and creates a virtual pool
* of connections to resources whose access needs to be throttled in number, 
* rate of use, and byte rate.
*/
public interface IConnectionThrottler
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
  
  /** Set or update throttle specification for a throttle group.  This creates the
  * throttle group if it does not yet exist.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the desired throttle specification object.
  */
  public void updateThrottleSpecification(String throttleGroupType, String throttleGroup, IThrottleSpec throttleSpec)
    throws ManifoldCFException;

  /** Get permission to use a connection, which is described by the passed array of bin names.
  * This method may block until a connection slot is available.
  * The connection can be used multiple times until the releaseConnectionPermission() method is called.
  * This persistence feature is meant to allow connections to be pooled locally by the caller.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  *@return the fetch throttler to use when performing fetches from the corresponding connection.
  */
  public IFetchThrottler obtainConnectionPermission(String throttleGroupType , String throttleGroup,
    String[] binNames)
    throws ManifoldCFException;
  
  /** Determine whether to release a pooled connection.  This method returns the number of bins
  * where the outstanding connection exceeds current quotas, indicating whether at least one with the specified
  * characteristics should be released.
  * NOTE WELL: This method cannot judge which is the best connection to be released to meet
  * quotas.  The caller needs to do that based on the highest number of bins matched.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  *@return the number of bins that are over quota, or zero if none of them are.
  */
  public int overConnectionQuotaCount(String throttleGroupType, String throttleGroup, String[] binNames)
    throws ManifoldCFException;
  
  /** Release permission to use one connection. This presumes that obtainConnectionPermission()
  * was called earlier by someone and was successful.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  */
  public void releaseConnectionPermission(String throttleGroupType, String throttleGroup, String[] binNames)
    throws ManifoldCFException;
  
  /** Poll periodically, to update cluster-wide statistics and allocation.
  *@param throttleGroupType is the throttle group type to update.
  */
  public void poll(String throttleGroupType)
    throws ManifoldCFException;
  
  /** Free unused resources.
  */
  public void freeUnusedResources()
    throws ManifoldCFException;
  
  /** Shut down throttler permanently.
  */
  public void destroy()
    throws ManifoldCFException;

}
