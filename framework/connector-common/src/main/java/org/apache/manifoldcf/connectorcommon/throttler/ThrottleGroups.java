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
package org.apache.manifoldcf.connectorcommon.throttler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import java.util.*;

/** An implementation of IThrottleGroups, which establishes a JVM-wide
* pool of throttlers that can be used as a resource by any connector that needs
* it.
*/
public class ThrottleGroups implements IThrottleGroups
{
  public static final String _rcsid = "@(#)$Id$";

  /** The thread context */
  protected final IThreadContext threadContext;
    
  /** The actual static pool */
  protected final static Throttler throttler = new Throttler();
  
  /** Constructor */
  public ThrottleGroups(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get all existing throttle groups for a throttle group type.
  * The throttle group type typically describes a connector class, while the throttle group represents
  * a namespace of bin names specific to that connector class.
  *@param throttleGroupType is the throttle group type.
  *@return the set of throttle groups for that group type.
  */
  @Override
  public Set<String> getThrottleGroups(String throttleGroupType)
    throws ManifoldCFException
  {
    return throttler.getThrottleGroups(threadContext, throttleGroupType);
  }
  
  /** Remove a throttle group.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  */
  @Override
  public void removeThrottleGroup(String throttleGroupType, String throttleGroup)
    throws ManifoldCFException
  {
    throttler.removeThrottleGroup(threadContext, throttleGroupType, throttleGroup);
  }
  
  /** Set or update throttle specification for a throttle group.  This creates the
  * throttle group if it does not yet exist.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the desired throttle specification object.
  */
  @Override
  public void createOrUpdateThrottleGroup(String throttleGroupType, String throttleGroup, IThrottleSpec throttleSpec)
    throws ManifoldCFException
  {
    throttler.createOrUpdateThrottleGroup(threadContext, throttleGroupType, throttleGroup, throttleSpec);
  }

  /** Construct connection throttler for connections with specific bin names.  This object is meant to be embedded with a connection
  * pool of similar objects, and used to gate the creation of new connections in that pool.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames are the connection type bin names.
  *@return the connection throttling object, or null if the pool is being shut down.
  */
  @Override
  public IConnectionThrottler obtainConnectionThrottler(String throttleGroupType, String throttleGroup, String[] binNames)
    throws ManifoldCFException
  {
    java.util.Arrays.sort(binNames);
    return throttler.obtainConnectionThrottler(threadContext, throttleGroupType, throttleGroup, binNames);
  }

  /** Poll periodically, to update cluster-wide statistics and allocation.
  *@param throttleGroupType is the throttle group type to update.
  */
  @Override
  public void poll(String throttleGroupType)
    throws ManifoldCFException
  {
    throttler.poll(threadContext, throttleGroupType);
  }
  
  /** Poll periodically, to update ALL cluster-wide statistics and allocation.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    throttler.poll(threadContext);
  }

  /** Free unused resources.
  */
  @Override
  public void freeUnusedResources()
    throws ManifoldCFException
  {
    throttler.freeUnusedResources(threadContext);
  }
  
  /** Shut down throttler permanently.
  */
  @Override
  public void destroy()
    throws ManifoldCFException
  {
    throttler.destroy(threadContext);
  }

}
