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

/** An IConnectionThrottler object is thread-local and creates a virtual pool
* of connections to resources whose access needs to be throttled in number, 
* rate of use, and byte rate.
*/
public interface IConnectionThrottler
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get permission to use a connection, which is described by the passed array of bin names.
  * The connection can be used multiple
  * times until the releaseConnectionPermission() method is called.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  *@param currentTime is the current time, in ms. since epoch.
  *@return the fetch throttler to use when performing fetches from this connection.
  */
  public IFetchThrottler obtainConnectionPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException;
  
  /** Release permission to use a connection. This presumes that obtainConnectionPermission()
  * was called earlier in the same thread and was successful.
  *@param currentTime is the current time, in ms. since epoch.
  */
  public void releaseConnectionPermission(long currentTime)
    throws ManifoldCFException;
  
  /** Poll periodically.
  */
  public void poll()
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
