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

/** Connection tracking for a bin.
*
* This class keeps track of information needed to figure out fetch rate throttling for connections,
* on a bin-by-bin basis. 
*
* NOTE WELL: This is entirely local in operation
*/
public class FetchBin
{
  /** This is set to true until the bin is shut down. */
  protected boolean isAlive = true;
  /** This is the bin name which this connection pool belongs to */
  protected final String binName;
  /** Service type name */
  protected final String serviceTypeName;
  /** The (anonymous) service name */
  protected final String serviceName;
  /** The target calculation lock name */
  protected final String targetCalcLockName;

  /** This is the minimum time between fetches for this bin, in ms. */
  protected long minTimeBetweenFetches = Long.MAX_VALUE;

  /** The local minimum time between fetches */
  protected long localMinimum = Long.MAX_VALUE;

  /** This is the last time a fetch was done on this bin */
  protected long lastFetchTime = 0L;
  /** Is the next fetch reserved? */
  protected boolean reserveNextFetch = false;

  /** The service type prefix for fetch bins */
  protected final static String serviceTypePrefix = "_FETCHBIN_";

  /** The target calculation lock prefix */
  protected final static String targetCalcLockPrefix = "_FETCHBINTARGET_";

  /** Constructor. */
  public FetchBin(IThreadContext threadContext, String throttlingGroupName, String binName)
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
  public synchronized void updateMinTimeBetweenFetches(long minTimeBetweenFetches)
  {
    // Update the number and wake up any waiting threads; they will take care of everything.
    this.minTimeBetweenFetches = minTimeBetweenFetches;
  }

  /** Reserve a request to fetch a document from this bin.  The actual fetch is not yet committed
  * with this call, but if it succeeds for all bins associated with the document, then the caller
  * has permission to do the fetch, and can update the last fetch time.
  *@return false if the fetch bin is being shut down.
  */
  public synchronized boolean reserveFetchRequest(IBreakCheck breakCheck)
    throws InterruptedException, BreakException
  {
    // First wait for the ability to even get the next fetch from this bin
    while (true)
    {
      if (!isAlive)
        return false;
      if (!reserveNextFetch)
      {
        reserveNextFetch = true;
        return true;
      }
      if (breakCheck == null)
      {
        wait();
      }
      else
      {
        long amt = breakCheck.abortCheck();
        wait(amt);
      }
    }
  }
  
  /** Clear reserved request.
  */
  public synchronized void clearReservation()
  {
    if (!reserveNextFetch)
      throw new IllegalStateException("Can't clear a fetch reservation we don't have");
    reserveNextFetch = false;
    notifyAll();
  }
  
  /** Wait the necessary time to do the fetch.  Presumes we've reserved the next fetch
  * rights already, via reserveFetchRequest().
  *@return false if the wait did not complete because the bin was shut down.
  */
  public synchronized boolean waitNextFetch(IBreakCheck breakCheck)
    throws InterruptedException, BreakException
  {
    // MHL
    if (!reserveNextFetch)
      throw new IllegalStateException("No fetch request reserved!");
    
    while (true)
    {
      if (!isAlive)
        // Leave it to the caller to undo reservations
        return false;
      if (localMinimum == Long.MAX_VALUE)
      {
        // wait forever - but eventually someone will set a smaller interval and wake us up.
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
      else
      {
        long currentTime = System.currentTimeMillis();
        // Compute how long we have to wait, based on the current time and the time of the last fetch.
        long waitAmt = lastFetchTime + localMinimum - currentTime;
        if (waitAmt <= 0L)
        {
          // Note actual time we start the fetch.
          if (currentTime > lastFetchTime)
            lastFetchTime = currentTime;
          reserveNextFetch = false;
          notifyAll();
          return true;
        }
        if (breakCheck == null)
        {
          wait(waitAmt);
        }
        else
        {
          long amt = breakCheck.abortCheck();
          if (waitAmt < amt)
            amt = waitAmt;
          wait(amt);
        }
        // Back around
      }
    }
  }

  /** Poll this bin */
  public synchronized void poll(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    lockManager.enterWriteLock(targetCalcLockName);
    try
    {
      // This is where the cross-cluster logic happens.
      // Each service records the following information:
      // -- the target rate, in fetches per millisecond
      // -- the earliest possible time for the service's next fetch, in ms from start of epoch
      // Target rates are apportioned in fetches-per-ms space, as follows:
      // (1) Target rate is summed cross-cluster, excluding our local service.  This is GlobalTarget.
      // (2) MaximumTarget is computed, which is Maximum-GlobalTarget.
      // (3) FairTarget is computed, which is Maximum/numServices + rand(Maximum%numServices).
      // (4) Finally, we compute Target rate by taking the minimum of MaximumTarget, FairTarget.
      // The earliest time for the next fetch is computed as follows:
      // (1) Find the LATEST most recent fetch time across the services, including an updated time for
      //   the local service.
      // (2) Compute the next possible fetch time, using the Target rate and that fetch time.
      // (3) The new targeted fetch time will be set to that value.

      SumClass sumClass = new SumClass(serviceName);
      lockManager.scanServiceData(serviceTypeName, sumClass);

      int numServices = sumClass.getNumServices();
      if (numServices == 0)
        return;
      double globalTarget = sumClass.getGlobalTarget();
      long earliestTargetTime = sumClass.getEarliestTime();
      long currentTime = System.currentTimeMillis();
      
      if (lastFetchTime == 0L)
        earliestTargetTime = currentTime;
      else if (earliestTargetTime > lastFetchTime)
        earliestTargetTime = lastFetchTime;
      
      // Now, compute the target rate
      double globalMaxFetchesPerMillisecond;
      double maximumTarget;
      double fairTarget;
      if (minTimeBetweenFetches == 0.0)
      {
        //System.out.println(binName+":Global minimum milliseconds per byte = 0.0");
        globalMaxFetchesPerMillisecond = Double.MAX_VALUE;
        maximumTarget = globalMaxFetchesPerMillisecond;
        fairTarget = globalMaxFetchesPerMillisecond;
      }
      else
      {
        globalMaxFetchesPerMillisecond = 1.0 / minTimeBetweenFetches;
        //System.out.println(binName+":Global max bytes per millisecond = "+globalMaxBytesPerMillisecond);
        maximumTarget = globalMaxFetchesPerMillisecond - globalTarget;
        if (maximumTarget < 0.0)
          maximumTarget = 0.0;

        // Compute FairTarget
        fairTarget = globalMaxFetchesPerMillisecond / numServices;
      }

      // Now compute actual target
      double inverseTarget = maximumTarget;
      if (inverseTarget > fairTarget)
        inverseTarget = fairTarget;

      long target;
      if (inverseTarget == 0.0)
        target = Long.MAX_VALUE;
      else
        target = (long)(1.0/inverseTarget +0.5);
      
      long nextFetchTime = earliestTargetTime + target;
      
      lockManager.updateServiceData(serviceTypeName, serviceName, pack(inverseTarget, nextFetchTime));

      // Update local parameters: the rate, and the next time.
      // But in order to update the next time, we have to update the last time.
      if (target == localMinimum && earliestTargetTime == lastFetchTime)
        return;
      //System.out.println(binName+":Setting localMinimum="+target+"; last fetch time="+earliestTargetTime);
      localMinimum = target;
      lastFetchTime = earliestTargetTime;
      notifyAll();
    }
    finally
    {
      lockManager.leaveWriteLock(targetCalcLockName);
    }

  }

  /** Shut the bin down, and wake up all threads waiting on it.
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
    protected double globalTargetTally = 0;
    protected long earliestTime = Long.MAX_VALUE;
    
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
        long checkTime = unpackEarliestTime(serviceData);
        if (checkTime < earliestTime)
          earliestTime = checkTime;
      }
      return false;
    }

    public int getNumServices()
    {
      return numServices;
    }
    
    public double getGlobalTarget()
    {
      return globalTargetTally;
    }
    
    public long getEarliestTime()
    {
      return earliestTime;
    }
  }

  protected static double unpackTarget(byte[] data)
  {
    if (data == null || data.length != 8)
      return 0.0;
    return Double.longBitsToDouble((((long)data[0]) & 0xffL) +
      ((((long)data[1]) << 8) & 0xff00L) +
      ((((long)data[2]) << 16) & 0xff0000L) +
      ((((long)data[3]) << 24) & 0xff000000L) +
      ((((long)data[4]) << 32) & 0xff00000000L) +
      ((((long)data[5]) << 40) & 0xff0000000000L) +
      ((((long)data[6]) << 48) & 0xff000000000000L) +
      ((((long)data[7]) << 56) & 0xff00000000000000L));
  }

  protected static long unpackEarliestTime(byte[] data)
  {
    if (data == null || data.length != 16)
      return Long.MAX_VALUE;
    return (((long)data[8]) & 0xffL) +
      ((((long)data[9]) << 8) & 0xff00L) +
      ((((long)data[10]) << 16) & 0xff0000L) +
      ((((long)data[11]) << 24) & 0xff000000L) +
      ((((long)data[12]) << 32) & 0xff00000000L) +
      ((((long)data[13]) << 40) & 0xff0000000000L) +
      ((((long)data[14]) << 48) & 0xff000000000000L) +
      ((((long)data[15]) << 56) & 0xff00000000000000L);
  }

  protected static byte[] pack(double targetDouble, long earliestTime)
  {
    long target = Double.doubleToLongBits(targetDouble);
    byte[] rval = new byte[16];
    rval[0] = (byte)(target & 0xffL);
    rval[1] = (byte)((target >> 8) & 0xffL);
    rval[2] = (byte)((target >> 16) & 0xffL);
    rval[3] = (byte)((target >> 24) & 0xffL);
    rval[4] = (byte)((target >> 32) & 0xffL);
    rval[5] = (byte)((target >> 40) & 0xffL);
    rval[6] = (byte)((target >> 48) & 0xffL);
    rval[7] = (byte)((target >> 56) & 0xffL);
    rval[8] = (byte)(earliestTime & 0xffL);
    rval[9] = (byte)((earliestTime >> 8) & 0xffL);
    rval[10] = (byte)((earliestTime >> 16) & 0xffL);
    rval[11] = (byte)((earliestTime >> 24) & 0xffL);
    rval[12] = (byte)((earliestTime >> 32) & 0xffL);
    rval[13] = (byte)((earliestTime >> 40) & 0xffL);
    rval[14] = (byte)((earliestTime >> 48) & 0xffL);
    rval[15] = (byte)((earliestTime >> 56) & 0xffL);
    return rval;
  }

}

