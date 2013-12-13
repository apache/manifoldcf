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
package org.apache.manifoldcf.core.throttler;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** A Throttler object creates a virtual pool of connections to resources
* whose access needs to be throttled in number, rate of use, and byte rate.
* This code is modeled on the code for distributed connection pools, and is intended
* to work in a similar manner.  Basically, a periodic assessment is done about what the
* local throttling parameters should be (on a per-pool basis), and the local throttling
* activities then adjust what they are doing based on the new parameters.  A service
* model is used to keep track of which pools have what clients working with them.
* This implementation has the advantage that:
* (1) Only local throttling ever takes place on a method-by-method basis, which makes
*   it possible to use throttling even in streams and background threads;
* (2) Throttling resources are apportioned fairly, on average, between all the various
*   cluster members, so it is unlikely that any persistent starvation conditions can
*   arise.
*/
public class Throttler
{
  public static final String _rcsid = "@(#)$Id$";

  /** The service type prefix for throttle pools */
  protected final static String serviceTypePrefix = "_THROTTLEPOOL_";
  
  /** Throttle group hash table.  Keyed by throttle group type, value is throttling groups */
  protected final Map<String,ThrottlingGroups> throttleGroupsHash = new HashMap<String,ThrottlingGroups>();

  /** Create a throttler instance.  Usually there will be one of these per connector
  * type that needs throttling.
  */
  public Throttler()
  {
  }
  
  /** Get all existing throttle groups for a throttle group type.
  * The throttle group type typically describes a connector class, while the throttle group represents
  * a namespace of bin names specific to that connector class.
  *@param throttleGroupType is the throttle group type.
  *@return the set of throttle groups for that group type.
  */
  public Set<String> getThrottleGroups(String throttleGroupType)
  {
    // MHL
    return null;
  }
  
  /** Remove a throttle group.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  */
  public void removeThrottleGroup(String throttleGroupType, String throttleGroup)
  {
    // MHL
  }
  
  /** Set or update throttle specification for a throttle group.  This creates the
  * throttle group if it does not yet exist.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the desired throttle specification object.
  */
  public void updateThrottleSpecification(String throttleGroupType, String throttleGroup, IThrottleSpec throttleSpec)
  {
    // MHL
  }

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
    throws InterruptedException
  {
    // MHL
    return null;
  }
  
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
  {
    // MHL
    return 0;
  }
  
  /** Release permission to use one connection. This presumes that obtainConnectionPermission()
  * was called earlier by someone and was successful.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  */
  public void releaseConnectionPermission(String throttleGroupType, String throttleGroup, String[] binNames)
  {
    // MHL
  }
  
  /** Poll periodically.
  */
  public void poll(IThreadContext threadContext, String throttleGroupType)
    throws ManifoldCFException
  {
    // Find the right pool, and poll that
    ThrottlingGroups tg;
    synchronized (throttleGroupsHash)
    {
      tg = throttleGroupsHash.get(throttleGroupType);
    }
    
    if (tg != null)
      tg.poll(threadContext);
      
  }
  
  /** Free unused resources.
  */
  public void freeUnusedResources(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (throttleGroupsHash)
    {
      Iterator<ThrottlingGroups> iter = throttleGroupsHash.values().iterator();
      while (iter.hasNext())
      {
        ThrottlingGroups p = iter.next();
        p.freeUnusedResources(threadContext);
      }
    }
  }
  
  /** Shut down all throttlers and deregister them.
  */
  public void destroy(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (throttleGroupsHash)
    {
      Iterator<ThrottlingGroups> iter = throttleGroupsHash.values().iterator();
      while (iter.hasNext())
      {
        ThrottlingGroups p = iter.next();
        p.destroy(threadContext);
        iter.remove();
      }
    }
  }

  // Protected methods and classes
  
  protected String buildServiceTypeName(String throttlingGroupType, String throttleGroupName)
  {
    return serviceTypePrefix + throttlingGroupType + "_" + throttleGroupName;
  }
  

  /** This class represents a throttling group pool */
  protected class ThrottlingGroups
  {
    /** The throttling group type for this throttling group pool */
    protected final String throttlingGroupTypeName;
    /** The pool of individual throttle group services for this pool, keyed by throttle group name */
    protected final Map<String,ThrottlingGroup> groups = new HashMap<String,ThrottlingGroup>();
    
    public ThrottlingGroups(String throttlingGroupTypeName)
    {
      this.throttlingGroupTypeName = throttlingGroupTypeName;
    }
    
    // MHL
    
    /** Poll this set of throttle groups */
    public synchronized void poll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      Iterator<String> iter = groups.keySet().iterator();
      while (iter.hasNext())
      {
        String throttleGroup = iter.next();
        ThrottlingGroup p = groups.get(throttleGroup);
        p.poll(threadContext);
      }
    }

    /** Free unused resources */
    public synchronized void freeUnusedResources(IThreadContext threadContext)
      throws ManifoldCFException
    {
      Iterator<ThrottlingGroup> iter = groups.values().iterator();
      while (iter.hasNext())
      {
        ThrottlingGroup g = iter.next();
        g.freeUnusedResources(threadContext);
      }
    }
    
    /** Destroy and shutdown all */
    public synchronized void destroy(IThreadContext threadContext)
      throws ManifoldCFException
    {
      Iterator<ThrottlingGroup> iter = groups.values().iterator();
      while (iter.hasNext())
      {
        ThrottlingGroup p = iter.next();
        p.destroy(threadContext);
        iter.remove();
      }
    }
  }
  
  /** This class represents a throttling group, of a specific throttling group type.
  */
  protected class ThrottlingGroup
  {
    /** Whether this pool is alive */
    protected boolean isAlive = true;
    /** Service type name */
    protected final String serviceTypeName;
    /** The (anonymous) service name */
    protected final String serviceName;
    /** The current throttle spec */
    protected IThrottleSpec throttleSpec;
    
    /** The connection bins */
    protected final Map<String,ConnectionBin> connectionBins = new HashMap<String,ConnectionBin>();
    /** The fetch bins */
    protected final Map<String,FetchBin> fetchBins = new HashMap<String,FetchBin>();
    /** The throttle bins */
    protected final Map<String,ThrottleBin> throttleBins = new HashMap<String,ThrottleBin>();

    /** Constructor
    */
    public ThrottlingGroup(IThreadContext threadContext, String throttlingGroupType, String throttleGroup, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.serviceTypeName = buildServiceTypeName(throttlingGroupType, throttleGroup);
      this.throttleSpec = throttleSpec;
      // Now, register and activate service anonymously, and record the service name we get.
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      this.serviceName = lockManager.registerServiceBeginServiceActivity(serviceTypeName, null, null);
    }

    /** Update the throttle spec.
    *@param throttleSpec is the new throttle spec for this throttle group.
    */
    public synchronized void updateThrottleSpec(IThreadContext threadContext, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.throttleSpec = throttleSpec;
    }
    
    // MHL
    
    /** Call this periodically.
    */
    public synchronized void poll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // MHL
    }
    
    /** Free unused resources.
    */
    public synchronized void freeUnusedResources(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // MHL
    }
    
    /** Destroy this pool.
    */
    public synchronized void destroy(IThreadContext threadContext)
      throws ManifoldCFException
    {
      freeUnusedResources(threadContext);
      // End service activity
      isAlive = false;
      notifyAll();
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      lockManager.endServiceActivity(serviceTypeName, serviceName);
    }
  }
  
}
