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
package org.apache.manifoldcf.crawler.reprioritizationtracker;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

/** This class tracks cluster-wide reprioritization operations.
* These operations are driven forward by whatever thread needs them,
* and are completed if those processes die by the threads that clean up
* after the original process.
*/
public class ReprioritizationTracker implements IReprioritizationTracker
{
  public static final String _rcsid = "@(#)$Id$";

  protected final static String trackerWriteLock = "_REPR_TRACKER_LOCK_";
  protected final static String trackerProcessIDResource = "_REPR_TRACKER_PID_";
  protected final static String trackerReproIDResource = "_REPR_TRACKER_RID_";
  protected final static String trackerMinimumDepthResource = "_REPR_MINDEPTH_";
  
  /** Lock manager */
  protected final ILockManager lockManager;
  protected final IBinManager binManager;

  /** Preload requests */
  protected final Map<PreloadKey,PreloadRequest> preloadRequests = new HashMap<PreloadKey,PreloadRequest>();
  /** Preload values */
  protected final Map<PreloadKey,PreloadedValues> preloadedValues = new HashMap<PreloadKey,PreloadedValues>();
    
  /** Constructor.
  */
  public ReprioritizationTracker(IThreadContext threadContext)
    throws ManifoldCFException
  {
    lockManager = LockManagerFactory.make(threadContext);
    binManager = BinManagerFactory.make(threadContext);
  }
  
  /** Start a reprioritization activity.
  *@param prioritizationTime is the timestamp of the prioritization.
  *@param processID is the process ID of the process performing/waiting for the prioritization
  * to complete.
  *@param reproID is the reprocessing thread ID
  */
  @Override
  public void startReprioritization(String processID, String reproID)
    throws ManifoldCFException
  {
    lockManager.enterWriteLock(trackerWriteLock);
    try
    {
      String currentProcessID = readProcessID();
      if (currentProcessID != null)
      {
        // Already a reprioritization in progress.
        return;
      }
      writeProcessID(processID);
      writeReproID(reproID);
      try
      {
        binManager.reset();
      }
      catch (Throwable e)
      {
        writeProcessID(null);
        writeReproID(null);
        if (e instanceof Error)
          throw (Error)e;
        else if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof ManifoldCFException)
          throw (ManifoldCFException)e;
        else
          throw new RuntimeException("Unknown exception: "+e.getClass().getName()+": "+e.getMessage(),e);
      }
      writeMinimumDepth(0.0);
    }
    finally
    {
      lockManager.leaveWriteLock(trackerWriteLock);
    }
  }
  
  
  /** Complete a reprioritization activity.  Prioritization will be marked as complete
  * only if the processID matches the one that started the current reprioritization.
  *@param processID is the process ID of the process completing the prioritization.
  */
  @Override
  public void doneReprioritization(String reproID)
    throws ManifoldCFException
  {
    lockManager.enterWriteLock(trackerWriteLock);
    try
    {
      String currentProcessID = readProcessID();
      String currentReproID = readReproID();
      if (currentProcessID != null && currentReproID != null && currentReproID.equals(reproID))
      {
        // Null out the fields
        writeProcessID(null);
        writeReproID(null);
      }
    }
    finally
    {
      lockManager.leaveWriteLock(trackerWriteLock);
    }
  }
  
  /** Check if the specified processID is the one performing reprioritization.
  *@param processID is the process ID to check.
  *@return the repro ID if the processID is confirmed to be the one.
  */
  @Override
  public String isSpecifiedProcessReprioritizing(String processID)
    throws ManifoldCFException
  {
    lockManager.enterWriteLock(trackerWriteLock);
    try
    {
      String currentProcessID = readProcessID();
      String currentReproID = readReproID();
      if (currentProcessID != null && currentReproID != null && currentProcessID.equals(processID))
        return currentReproID;
      return null;
    }
    finally
    {
      lockManager.leaveWriteLock(trackerWriteLock);
    }
  }
  
  /** Assess the current minimum depth.
  * This method is called to provide information about the priorities of the documents being currently
  * queued.  It is the case that it is unoptimal to assign document priorities that are fundamentally higher than this value,
  * because then the new documents will be preferentially queued, and the goal of distributing documents across bins will not be
  * adequately met.
  *@param binNamesSet is the current set of priorities we see on the queuing operation.
  */
  @Override
  public void assessMinimumDepth(Double[] binNamesSet)
    throws ManifoldCFException
  {
    double newMinPriority = Double.MAX_VALUE;
    for (Double binValue : binNamesSet)
    {
      if (binValue.doubleValue() < newMinPriority)
        newMinPriority = binValue.doubleValue();
    }

    if (newMinPriority != Double.MAX_VALUE)
    {

      lockManager.enterWriteLock(trackerWriteLock);
      try
      {
        String processID = readProcessID();
        if (processID == null)
        {
          double currentMinimumDepth = readMinimumDepth();

          // Convert minPriority to minDepth.
          // Note that this calculation does not take into account anything having to do with connection rates, throttling,
          // or other adjustment factors.  It allows us only to obtain the "raw" minimum depth: the depth without any
          // adjustments.
          double newMinDepth = Math.exp(newMinPriority)-1.0;

          if (newMinDepth > currentMinimumDepth)
          {
            writeMinimumDepth(newMinDepth);
            if (Logging.scheduling.isDebugEnabled())
              Logging.scheduling.debug("Setting new minimum depth value to "+new Double(currentMinimumDepth).toString());
          }
          else
          {
            if (newMinDepth < currentMinimumDepth && Logging.scheduling.isDebugEnabled())
              Logging.scheduling.debug("Minimum depth value seems to have been set too high too early! currentMin = "+new Double(currentMinimumDepth).toString()+"; queue value = "+new Double(newMinDepth).toString());
          }
        }
      }
      finally
      {
        lockManager.leaveWriteLock(trackerWriteLock);
      }
    }
  }

  /** Retrieve current minimum depth.
  *@return the current minimum depth to use.
  */
  @Override
  public double getMinimumDepth()
    throws ManifoldCFException
  {
    lockManager.enterReadLock(trackerWriteLock);
    try
    {
      return readMinimumDepth();
    }
    finally
    {
      lockManager.leaveReadLock(trackerWriteLock);
    }
  }
  
  /** Note preload amounts.
  */
  @Override
  public void addPreloadRequest(String connectorClass, String binName, double weightedMinimumDepth)
  {
    final PreloadKey pk = new PreloadKey(connectorClass, binName);
    PreloadRequest pr = preloadRequests.get(pk);
    if (pr == null)
    {
      pr = new PreloadRequest(weightedMinimumDepth);
      preloadRequests.put(pk,pr);
    }
    else
      pr.updateRequest(weightedMinimumDepth);
  }
  
  
  /** Preload bin values.  Call this OUTSIDE of a transaction.
  */
  @Override
  public void preloadBinValues()
    throws ManifoldCFException
  {
    for (PreloadKey pk : preloadRequests.keySet())
    {
      PreloadRequest pr = preloadRequests.get(pk);
      double[] newValues = binManager.getIncrementBinValuesInTransaction(pk.connectorClass, pk.binName, pr.getWeightedMinimumDepth(), pr.getRequestCount());
      PreloadedValues pv = new PreloadedValues(newValues);
      preloadedValues.put(pk,pv);
    }
    preloadRequests.clear();
  }
  
  /** Clear any preload requests.
  */
  @Override
  public void clearPreloadRequests()
  {
    preloadRequests.clear();
  }
  
  /** Clear remaining preloaded values.
  */
  @Override
  public void clearPreloadedValues()
  {
    preloadedValues.clear();
  }

  /** Get a bin value.
  *@param connectorClass is the connector class name.
  *@param binName is the bin name.
  *@param weightedMinimumDepth is the minimum depth to use.
  *@return the bin value.
  */
  @Override
  public double getIncrementBinValue(String connectorClass, String binName, double weightedMinimumDepth)
    throws ManifoldCFException
  {
    final PreloadKey key = new PreloadKey(connectorClass,binName);
    PreloadedValues pv = preloadedValues.get(key);
    if (pv != null)
    {
      Double rval = pv.getNextValue();
      if (rval != null)
        return rval.doubleValue();
    }
    return binManager.getIncrementBinValues(connectorClass, binName, weightedMinimumDepth,1)[0];
  }
  
  // Protected methods
  
  /** Read process ID.
  *@return processID, or null if none.
  */
  protected String readProcessID()
    throws ManifoldCFException
  {
    byte[] processIDData = lockManager.readData(trackerProcessIDResource);
    if (processIDData == null)
      return null;
    else
      return new String(processIDData, StandardCharsets.UTF_8);

  }
  
  /** Write process ID.
  *@param processID is the process ID to write.
  */
  protected void writeProcessID(String processID)
    throws ManifoldCFException
  {
    if (processID == null)
      lockManager.writeData(trackerProcessIDResource, null);
    else
    {
        byte[] processIDData = processID.getBytes(StandardCharsets.UTF_8);
        lockManager.writeData(trackerProcessIDResource, processIDData);
    }
  }

  /** Read repriotization ID.
  *@return reproID, or null if none.
  */
  protected String readReproID()
    throws ManifoldCFException
  {
    byte[] reproIDData = lockManager.readData(trackerReproIDResource);
    if (reproIDData == null)
      return null;
    else
      return new String(reproIDData, StandardCharsets.UTF_8);

  }
  
  /** Write repro ID.
  *@param reproID is the repro ID to write.
  */
  protected void writeReproID(String reproID)
    throws ManifoldCFException
  {
    if (reproID == null)
      lockManager.writeData(trackerReproIDResource, null);
    else
    {
        byte[] reproIDData = reproID.getBytes(StandardCharsets.UTF_8);
        lockManager.writeData(trackerReproIDResource, reproIDData);
    }
  }

  /** Read minimum depth.
  *@return the minimum depth.
  */
  protected double readMinimumDepth()
    throws ManifoldCFException
  {
    byte[] data = lockManager.readData(trackerMinimumDepthResource);
    if (data == null || data.length != 8)
      return 0.0;
    long dataLong = (((long)data[0]) & 0xffL) +
      ((((long)data[1]) << 8) & 0xff00L) +
      ((((long)data[2]) << 16) & 0xff0000L) +
      ((((long)data[3]) << 24) & 0xff000000L) +
      ((((long)data[4]) << 32) & 0xff00000000L) +
      ((((long)data[5]) << 40) & 0xff0000000000L) +
      ((((long)data[6]) << 48) & 0xff000000000000L) +
      ((((long)data[7]) << 56) & 0xff00000000000000L);

    return Double.longBitsToDouble(dataLong);
  }
  
  /** Write minimum depth.
  *@param the minimum depth.
  */
  protected void writeMinimumDepth(double depth)
    throws ManifoldCFException
  {
    long dataLong = Double.doubleToLongBits(depth);
    byte[] data = new byte[8];
    data[0] = (byte)(dataLong & 0xffL);
    data[1] = (byte)((dataLong >> 8) & 0xffL);
    data[2] = (byte)((dataLong >> 16) & 0xffL);
    data[3] = (byte)((dataLong >> 24) & 0xffL);
    data[4] = (byte)((dataLong >> 32) & 0xffL);
    data[5] = (byte)((dataLong >> 40) & 0xffL);
    data[6] = (byte)((dataLong >> 48) & 0xffL);
    data[7] = (byte)((dataLong >> 56) & 0xffL);
    lockManager.writeData(trackerMinimumDepthResource,data);
  }
  
  /** A preload request */
  protected static class PreloadRequest
  {
    protected double weightedMinimumDepth;
    protected int requestCount;
    
    public PreloadRequest(double weightedMinimumDepth)
    {
      this.weightedMinimumDepth = weightedMinimumDepth;
      this.requestCount = 1;
    }
    
    public void updateRequest(double weightedMinimumDepth)
    {
      if (this.weightedMinimumDepth < weightedMinimumDepth)
        this.weightedMinimumDepth = weightedMinimumDepth;
      requestCount++;
    }
    
    public double getWeightedMinimumDepth()
    {
      return weightedMinimumDepth;
    }
    
    public int getRequestCount()
    {
      return requestCount;
    }
  }
  
  /** A set of preloaded values */
  protected static class PreloadedValues
  {
    protected double[] values;
    protected int valueIndex;
    
    public PreloadedValues(double[] values)
    {
      this.values = values;
      this.valueIndex = valueIndex;
    }
    
    public Double getNextValue()
    {
      if (valueIndex == values.length)
        return null;
      return new Double(values[valueIndex++]);
    }
  }
  
  /** Connector class name, bin name pair */
  protected static class PreloadKey
  {
    public final String connectorClass;
    public final String binName;
    
    public PreloadKey(final String connectorClass, final String binName) {
      this.connectorClass = connectorClass;
      this.binName = binName;
    }
    
    public int hashCode() {
      return connectorClass.hashCode() + binName.hashCode();
    }
    
    public boolean equals(final Object o) {
      if (!(o instanceof PreloadKey))
        return false;
      final PreloadKey pk = (PreloadKey)o;
      return connectorClass.equals(pk.connectorClass) &&
        binName.equals(pk.binName);
    }
  }
  
}

