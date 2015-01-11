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
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.util.concurrent.atomic.*;
import java.util.*;

/** Connection tracking for a bin.
*
* This class keeps track of information needed to figure out throttling for connections,
* on a bin-by-bin basis.  It is *not*, however, a connection pool.  Actually establishing
* connections, and pooling established connections, is functionality that must reside in the
* caller.
*
* The 'connections' each connection bin tracks are connections outstanding that share this bin name.
* Not all such connections are identical; some may in fact have entirely different sets of
* bins associated with them, but they all have the specific bin in common.  Since each bin has its
* own unique limit, this effectively means that in order to get a connection, you need to find an
* available slot in ALL of its constituent connection bins.  If the connections are pooled, it makes
* the most sense to divide the pool up by characteristics such that identical connections are all
* handled together - and it is reasonable to presume that an identical connection has identical
* connection bins.
*
* NOTE WELL: This is entirely local in operation
*/
public class ConnectionBin
{
  /** True if this bin is alive still */
  protected boolean isAlive = true;
  /** This is the bin name which this connection pool belongs to */
  protected final String binName;
  /** Service type name */
  protected final String serviceTypeName;
  /** The (anonymous) service name */
  protected final String serviceName;
  /** The target calculation lock name */
  protected final String targetCalcLockName;
  
  /** This is the maximum number of active connections allowed for this bin */
  protected int maxActiveConnections = 0;
  
  /** This is the local maximum number of active connections allowed for this bin */
  protected int localMax = 0;
  /** This is the number of connections in this bin that have been reserved - that is, they
  * are promised to various callers, but those callers have not yet committed to obtaining them. */
  protected int reservedConnections = 0;
  /** This is the number of connections in this bin that are connected; immaterial whether they are
  * in use or in a pool somewhere. */
  protected int inUseConnections = 0;
  /** This is the number of active referring connection pools.  We increment this number
  * whenever a poolCount goes from zero to 1, and we decrement it whenever a poolCount
  * goes from one to zero. */
  protected int referencingPools = 0;
  
  /** The service type prefix for connection bins */
  protected final static String serviceTypePrefix = "_CONNECTIONBIN_";

  /** The target calculation lock prefix */
  protected final static String targetCalcLockPrefix = "_CONNECTIONBINTARGET_";
  
  /** Random number */
  protected final static Random randomNumberGenerator = new Random();

  /** Constructor. */
  public ConnectionBin(IThreadContext threadContext, String throttlingGroupName, String binName)
    throws ManifoldCFException
  {
    this.binName = binName;
    this.serviceTypeName = buildServiceTypeName(throttlingGroupName, binName);
    this.targetCalcLockName = buildTargetCalcLockName(throttlingGroupName, binName);
    // Now, register and activate service anonymously, and record the service name we get.
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    this.serviceName = lockManager.registerServiceBeginServiceActivity(serviceTypeName, null, null);
  }

  protected static String buildServiceTypeName(String throttlingGroupName, String binName)
  {
    return serviceTypePrefix + throttlingGroupName + "_" + binName;
  }

  protected static String buildTargetCalcLockName(String throttlingGroupName, String binName)
  {
    return targetCalcLockPrefix + throttlingGroupName + "_" + binName;
  }
  
  /** Get the bin name. */
  public String getBinName()
  {
    return binName;
  }

  /** Update the maximum number of active connections.
  */
  public synchronized void updateMaxActiveConnections(int maxActiveConnections)
  {
    // Update the number; the poller will wake up any waiting threads.
    this.maxActiveConnections = maxActiveConnections;
  }

  /** Wait for a connection to become available, in the context of an existing connection pool.
  *@param poolCount is the number of connections in the pool times the number of bins per connection.
  * This parameter is only ever changed in this class!!
  *@return a recommendation as to how to proceed, using the IConnectionThrottler values.  If the
  * recommendation is to create a connection, a slot will be reserved for that purpose.  A
  * subsequent call to noteConnectionCreation() will be needed to confirm the reservation, or clearReservation() to
  * release the reservation.
  */
  public synchronized int waitConnectionAvailable(AtomicInteger poolCount, IBreakCheck breakCheck)
    throws InterruptedException, BreakException
  {
    // Reserved connections keep a slot available which can't be used by anyone else.
    // Connection bins are always sorted so that deadlocks can't occur.
    // Once all slots are reserved, the caller will go ahead and create the necessary connection
    // and convert the reservation to a new connection.
    
    while (true)
    {
      if (!isAlive)
        return IConnectionThrottler.CONNECTION_FROM_NOWHERE;
      int currentPoolCount = poolCount.get();
      if (currentPoolCount > 0)
      {
        // Recommendation is to pull the connection from the pool.
        poolCount.set(currentPoolCount - 1);
        if (currentPoolCount == 1)
          referencingPools--;
        return IConnectionThrottler.CONNECTION_FROM_POOL;
      }
      if (inUseConnections + reservedConnections < localMax)
      {
        reservedConnections++;
        return IConnectionThrottler.CONNECTION_FROM_CREATION;
      }
      // Wait for a connection to free up.  Note that it is up to the caller to free stuff up.
      if (breakCheck == null)
      {
        wait();
      }
      else
      {
        long amt = breakCheck.abortCheck();
        wait(amt);
      }
      // Back around
    }
  }
  
  /** Undo what we had decided to do before.
  *@param recommendation is the decision returned by waitForConnection() above.
  */
  public synchronized void undoReservation(int recommendation, AtomicInteger poolCount)
  {
    if (recommendation == IConnectionThrottler.CONNECTION_FROM_CREATION)
    {
      if (reservedConnections == 0)
        throw new IllegalStateException("Can't clear a reservation we don't have");
      reservedConnections--;
      notifyAll();
    }
    else if (recommendation == IConnectionThrottler.CONNECTION_FROM_POOL)
    {
      int currentCount = poolCount.get();
      poolCount.set(currentCount + 1);
      if (currentCount == 0)
        referencingPools++;
      notifyAll();
    }
  }
  
  /** Note the creation of an active connection that belongs to this bin.  The connection MUST
  * have been reserved prior to the connection being created.
  */
  public synchronized void noteConnectionCreation()
  {
    if (reservedConnections == 0)
      throw new IllegalStateException("Creating a connection when no connection slot reserved!");
    reservedConnections--;
    inUseConnections++;
    // No notification needed because the total number of reserved+active connections did not change.
  }

  /** Figure out whether we are currently over target or not for this bin.
  */
  public synchronized boolean shouldReturnedConnectionBeDestroyed()
  {
    // We don't count reserved connections here because those are not yet committed
    return inUseConnections > localMax;
  }
  
  public static final int CONNECTION_DESTROY = 0;
  public static final int CONNECTION_POOLEMPTY = 1;
  public static final int CONNECTION_WITHINBOUNDS = 2;
  
  /** Figure out whether we are currently over target or not for this bin, and whether a
  * connection should be pulled from the pool and destroyed.
  * Note that this is tricky in conjunction with other bins, because those other bins
  * may conclude that we can't destroy a connection.  If so, we just return the stolen
  * connection back to the pool.
  *@return CONNECTION_DESTROY, CONNECTION_POOLEMPTY, or CONNECTION_WITHINBOUNDS.
  */
  public synchronized int shouldPooledConnectionBeDestroyed(AtomicInteger poolCount)
  {
    int currentPoolCount = poolCount.get();
    if (currentPoolCount > 0)
    {
      int individualPoolAllocation = localMax / referencingPools;
      // Consider it removed from the pool for the purposes of consideration.  If we change our minds, we'll
      // return it, and no harm done.
      poolCount.set(currentPoolCount-1);
      if (currentPoolCount == 1)
        referencingPools--;
      // We don't count reserved connections here because those are not yet committed.
      if (inUseConnections > individualPoolAllocation)
      {
        return CONNECTION_DESTROY;
      }
      return CONNECTION_WITHINBOUNDS;
    }
    return CONNECTION_POOLEMPTY;
  }

  /** Check only if there's a pooled connection, and make moves to take it from the pool.
  */
  public synchronized boolean hasPooledConnection(AtomicInteger poolCount)
  {
    int currentPoolCount = poolCount.get();
    if (currentPoolCount > 0)
    {
      poolCount.set(currentPoolCount-1);
      if (currentPoolCount == 1)
        referencingPools--;
      return true;
    }
    return false;
  }
  
  /** Undo the decision to destroy a pooled connection.
  */
  public synchronized void undoPooledConnectionDecision(AtomicInteger poolCount)
  {
    int currentPoolCount = poolCount.get();
    poolCount.set(currentPoolCount + 1);
    if (currentPoolCount == 0)
      referencingPools++;
    notifyAll();
  }
  
  /** Note a connection returned to the pool.
  */
  public synchronized void noteConnectionReturnedToPool(AtomicInteger poolCount)
  {
    int currentPoolCount = poolCount.get();
    poolCount.set(currentPoolCount + 1);
    if (currentPoolCount == 0)
      referencingPools++;
    // Wake up threads possibly waiting on a pool return.
    notifyAll();
  }
  
  /** Note the destruction of an active connection that belongs to this bin.
  */
  public synchronized void noteConnectionDestroyed()
  {
    inUseConnections--;
    notifyAll();
  }

  /** Poll this bin */
  public synchronized void poll(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // The meat of the cross-cluster apportionment algorithm goes here!
    // Two global numbers each service posts: "in-use" and "target".  At no time does a service *ever* post either a "target"
    // that, together with all other active service targets, is in excess of the max.  Also, at no time a service post
    // a target that, when added to the other "in-use" values, exceeds the max.  If the "in-use" values everywhere else
    // already equal or exceed the max, then the target will be zero.
    // The target quota is calculated as follows:
    // (1) Target is summed, excluding ours.  This is GlobalTarget.
    // (2) In-use is summed, excluding ours.  This is GlobalInUse.
    // (3) Our MaximumTarget is computed, which is Maximum - GlobalTarget or Maximum - GlobalInUse, whichever is
    //     smaller, but never less than zero.
    // (4) Our FairTarget is computed.  The FairTarget divides the Maximum by the number of services, and adds
    //     1 randomly based on the remainder.
    // (5) We compute OptimalTarget as follows: We start with current local target.  If current local target
    //    exceeds current local in-use count, we adjust OptimalTarget downward by one.  Otherwise we increase it
    //    by one.
    // (6) Finally, we compute Target by taking the minimum of MaximumTarget, FairTarget, and OptimalTarget.

    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.enterWriteLock(targetCalcLockName);
    try
    {
      // Compute MaximumTarget
      SumClass sumClass = new SumClass(serviceName);
      lockManager.scanServiceData(serviceTypeName, sumClass);
      //System.out.println("numServices = "+sumClass.getNumServices()+"; globalTarget = "+sumClass.getGlobalTarget()+"; globalInUse = "+sumClass.getGlobalInUse());
        
      int numServices = sumClass.getNumServices();
      if (numServices == 0)
        return;
      int globalTarget = sumClass.getGlobalTarget();
      int globalInUse = sumClass.getGlobalInUse();
      int maximumTarget = maxActiveConnections - globalTarget;
      if (maximumTarget > maxActiveConnections - globalInUse)
        maximumTarget = maxActiveConnections - globalInUse;
      if (maximumTarget < 0)
        maximumTarget = 0;
        
      // Compute FairTarget
      int fairTarget = maxActiveConnections / numServices;
      int remainder = maxActiveConnections % numServices;
      // Randomly choose whether we get an addition to the FairTarget
      if (randomNumberGenerator.nextInt(numServices) < remainder)
        fairTarget++;
        
      // Compute OptimalTarget
      int localInUse = inUseConnections;
      int optimalTarget = localMax;
      if (localMax > localInUse)
        optimalTarget--;
      else
      {
        // We want a fast ramp up, so make this proportional to maxActiveConnections
        int increment = maxActiveConnections >> 2;
        if (increment == 0)
          increment = 1;
        optimalTarget += increment;
      }
        
      //System.out.println("maxTarget = "+maximumTarget+"; fairTarget = "+fairTarget+"; optimalTarget = "+optimalTarget);

      // Now compute actual target
      int target = maximumTarget;
      if (target > fairTarget)
        target = fairTarget;
      if (target > optimalTarget)
        target = optimalTarget;
        
      // Write these values to the service data variables.
      // NOTE that there is a race condition here; the target value depends on all the calculations above being accurate, and not changing out from under us.
      // So, that's why we have a write lock around the pool calculations.
        
      lockManager.updateServiceData(serviceTypeName, serviceName, pack(target, localInUse));
        
      // Now, update our localMax, if it needs it.
      if (target == localMax)
        return;
      localMax = target;
      notifyAll();
    }
    finally
    {
      lockManager.leaveWriteLock(targetCalcLockName);
    }
  }

  /** Shut down the bin, and release everything that is waiting on it.
  */
  public synchronized void shutDown(IThreadContext threadContext)
    throws ManifoldCFException
  {
    isAlive = false;
    notifyAll();
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.endServiceActivity(serviceTypeName, serviceName);
  }
  
  // Protected classes and methods
  
  protected static class SumClass implements IServiceDataAcceptor
  {
    protected final String serviceName;
    protected int numServices = 0;
    protected int globalTargetTally = 0;
    protected int globalInUseTally = 0;
    
    public SumClass(String serviceName)
    {
      this.serviceName = serviceName;
    }
    
    @Override
    public boolean acceptServiceData(String serviceName, byte[] serviceData)
      throws ManifoldCFException
    {
      numServices++;

      if (!serviceName.equals(this.serviceName))
      {
        globalTargetTally += unpackTarget(serviceData);
        globalInUseTally += unpackInUse(serviceData);
      }
      return false;
    }

    public int getNumServices()
    {
      return numServices;
    }
    
    public int getGlobalTarget()
    {
      return globalTargetTally;
    }
    
    public int getGlobalInUse()
    {
      return globalInUseTally;
    }
    
  }
  
  protected static int unpackTarget(byte[] data)
  {
    if (data == null || data.length != 8)
      return 0;
    return (((int)data[0]) & 0xff) +
      ((((int)data[1]) << 8) & 0xff00) +
      ((((int)data[2]) << 16) & 0xff0000) +
      ((((int)data[3]) << 24) & 0xff000000);
  }

  protected static int unpackInUse(byte[] data)
  {
    if (data == null || data.length != 8)
      return 0;
    return (((int)data[4]) & 0xff) +
      ((((int)data[5]) << 8) & 0xff00) +
      ((((int)data[6]) << 16) & 0xff0000) +
      ((((int)data[7]) << 24) & 0xff000000);
  }

  protected static byte[] pack(int target, int inUse)
  {
    byte[] rval = new byte[8];
    rval[0] = (byte)(target & 0xff);
    rval[1] = (byte)((target >> 8) & 0xff);
    rval[2] = (byte)((target >> 16) & 0xff);
    rval[3] = (byte)((target >> 24) & 0xff);
    rval[4] = (byte)(inUse & 0xff);
    rval[5] = (byte)((inUse >> 8) & 0xff);
    rval[6] = (byte)((inUse >> 16) & 0xff);
    rval[7] = (byte)((inUse >> 24) & 0xff);
    return rval;
  }

}

