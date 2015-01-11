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
import java.util.concurrent.atomic.*;

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

  /** Throttle group hash table.  Keyed by throttle group type, value is throttling groups */
  protected final Map<String,ThrottlingGroups> throttleGroupsHash = new HashMap<String,ThrottlingGroups>();

  /** Create a throttler instance.  Usually there will be one of these per connector
  * type that needs throttling.
  */
  public Throttler()
  {
  }
  
  // There are a lot of synchronizers to coordinate here.  They are indeed hierarchical.  It is not possible to simply
  // throw a synchronizer at every level, and require that we hold all of them, because when we wait somewhere in the
  // inner level, we will continue to hold locks and block access to all the outer levels.
  //
  // Instead, I've opted for a model whereby individual resources are protected.  This is tricky to coordinate, though,
  // because (for instance) after a resource has been removed from the hash table, it had better be cleaned up
  // thoroughly before the outer lock is removed, or two versions of the resource might wind up coming into existence.
  // The general rule is therefore:
  // (1) Creation or deletion of resources involves locking the parent where the resource is being added or removed
  // (2) Anything that waits CANNOT also add or remove.
  
  /** Get all existing throttle groups for a throttle group type.
  * The throttle group type typically describes a connector class, while the throttle group represents
  * a namespace of bin names specific to that connector class.
  *@param throttleGroupType is the throttle group type.
  *@return the set of throttle groups for that group type.
  */
  public Set<String> getThrottleGroups(IThreadContext threadContext, String throttleGroupType)
    throws ManifoldCFException
  {
    synchronized (throttleGroupsHash)
    {
      return throttleGroupsHash.keySet();
    }
  }
  
  /** Remove a throttle group.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  */
  public void removeThrottleGroup(IThreadContext threadContext, String throttleGroupType, String throttleGroup)
    throws ManifoldCFException
  {
    // Removal.  Lock the whole hierarchy.
    synchronized (throttleGroupsHash)
    {
      ThrottlingGroups tg = throttleGroupsHash.get(throttleGroupType);
      if (tg != null)
      {
        tg.removeThrottleGroup(threadContext, throttleGroup);
      }
    }
  }
  
  /** Set or update throttle specification for a throttle group.  This creates the
  * throttle group if it does not yet exist.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the desired throttle specification object.
  */
  public void createOrUpdateThrottleGroup(IThreadContext threadContext, String throttleGroupType, String throttleGroup, IThrottleSpec throttleSpec)
    throws ManifoldCFException
  {
    // Potential addition.  Lock the whole hierarchy.
    synchronized (throttleGroupsHash)
    {
      ThrottlingGroups tg = throttleGroupsHash.get(throttleGroupType);
      if (tg == null)
      {
        tg = new ThrottlingGroups(throttleGroupType);
        throttleGroupsHash.put(throttleGroupType, tg);
      }
      tg.createOrUpdateThrottleGroup(threadContext, throttleGroup, throttleSpec);
    }
  }

  /** Construct connection throttler for connections with specific bin names.  This object is meant to be embedded with a connection
  * pool of similar objects, and used to gate the creation of new connections in that pool.
  *@param throttleGroupType is the throttle group type.
  *@param throttleGroup is the throttle group.
  *@param binNames are the connection type bin names.
  *@return the connection throttling object, or null if the pool is being shut down.
  */
  public IConnectionThrottler obtainConnectionThrottler(IThreadContext threadContext, String throttleGroupType, String throttleGroup, String[] binNames)
    throws ManifoldCFException
  {
    // No waiting, so lock the entire tree.
    synchronized (throttleGroupsHash)
    {
      ThrottlingGroups tg = throttleGroupsHash.get(throttleGroupType);
      if (tg != null)
        return tg.obtainConnectionThrottler(threadContext, throttleGroup, binNames);
      return null;
    }
  }
  
  /** Poll periodically.
  */
  public void poll(IThreadContext threadContext, String throttleGroupType)
    throws ManifoldCFException
  {
    // No waiting, so lock the entire tree.
    synchronized (throttleGroupsHash)
    {
      ThrottlingGroups tg = throttleGroupsHash.get(throttleGroupType);
      if (tg != null)
        tg.poll(threadContext);
    }
      
  }

  /** Poll ALL bins periodically.
  */
  public void poll(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // No waiting, so lock the entire tree.
    synchronized (throttleGroupsHash)
    {
      for (ThrottlingGroups tg : throttleGroupsHash.values())
      {
        tg.poll(threadContext);
      }
    }
      
  }
  
  /** Free unused resources.
  */
  public void freeUnusedResources(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // This potentially affects the entire hierarchy.
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
    // This affects the entire hierarchy, so lock the whole thing.
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
  
  protected static String buildThrottlingGroupName(String throttlingGroupType, String throttlingGroupName)
  {
    return throttlingGroupType + "_" + throttlingGroupName;
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
    
    /** Update throttle specification */
    public void createOrUpdateThrottleGroup(IThreadContext threadContext, String throttleGroup, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      synchronized (groups)
      {
        ThrottlingGroup g = groups.get(throttleGroup);
        if (g == null)
        {
          g = new ThrottlingGroup(threadContext, throttlingGroupTypeName, throttleGroup, throttleSpec);
          groups.put(throttleGroup, g);
        }
        else
        {
          g.updateThrottleSpecification(throttleSpec);
        }
      }
    }
    
    /** Obtain connection throttler.
    *@return the throttler, or null of the hierarchy has changed.
    */
    public IConnectionThrottler obtainConnectionThrottler(IThreadContext threadContext, String throttleGroup, String[] binNames)
      throws ManifoldCFException
    {
      synchronized (groups)
      {
        ThrottlingGroup g = groups.get(throttleGroup);
        if (g == null)
          return null;
        return g.obtainConnectionThrottler(threadContext, binNames);
      }
    }
    
    /** Remove specified throttle group */
    public void removeThrottleGroup(IThreadContext threadContext, String throttleGroup)
      throws ManifoldCFException
    {
      // Must synch the whole thing, because otherwise there would be a risk of someone recreating the
      // group right after we removed it from the map, and before we destroyed it.
      synchronized (groups)
      {
        ThrottlingGroup g = groups.remove(throttleGroup);
        if (g != null)
        {
          g.destroy(threadContext);
        }
      }
    }
    
    /** Poll this set of throttle groups.
    */
    public void poll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      synchronized (groups)
      {
        Iterator<String> iter = groups.keySet().iterator();
        while (iter.hasNext())
        {
          String throttleGroup = iter.next();
          ThrottlingGroup p = groups.get(throttleGroup);
          p.poll(threadContext);
        }
      }
    }

    /** Free unused resources */
    public void freeUnusedResources(IThreadContext threadContext)
      throws ManifoldCFException
    {
      synchronized (groups)
      {
        Iterator<ThrottlingGroup> iter = groups.values().iterator();
        while (iter.hasNext())
        {
          ThrottlingGroup g = iter.next();
          g.freeUnusedResources(threadContext);
        }
      }
    }
    
    /** Destroy and shutdown all */
    public void destroy(IThreadContext threadContext)
      throws ManifoldCFException
    {
      synchronized (groups)
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
  }
  
  /** This class represents a throttling group, of a specific throttling group type.  It basically
  * describes an entire self-consistent throttling environment.
  */
  protected class ThrottlingGroup
  {
    /** The throttling group name */
    protected final String throttlingGroupName;
    /** The current throttle spec */
    protected IThrottleSpec throttleSpec;
    
    /** The connection bins */
    protected final Map<String,ConnectionBin> connectionBins = new HashMap<String,ConnectionBin>();
    /** The fetch bins */
    protected final Map<String,FetchBin> fetchBins = new HashMap<String,FetchBin>();
    /** The throttle bins */
    protected final Map<String,ThrottleBin> throttleBins = new HashMap<String,ThrottleBin>();

    // For synchronization, we use several in this class.
    // Modification to the connectionBins, fetchBins, or throttleBins hashes uses the appropriate local synchronizer.
    // Changes to other local variables use the main synchronizer.
    
    /** Constructor
    */
    public ThrottlingGroup(IThreadContext threadContext, String throttlingGroupType, String throttleGroup, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.throttlingGroupName = buildThrottlingGroupName(throttlingGroupType, throttleGroup);
      this.throttleSpec = throttleSpec;
      // Once all that is done, perform the initial setting of all the bin cutoffs
      poll(threadContext);
    }

    /** Create a bunch of bins, corresponding to the bin names specified.
    * Note that this also registers them as services etc.
    *@param binNames describes the set of bins to create.
    */
    public synchronized IConnectionThrottler obtainConnectionThrottler(IThreadContext threadContext, String[] binNames)
      throws ManifoldCFException
    {
      synchronized (connectionBins)
      {
        for (String binName : binNames)
        {
          ConnectionBin bin = connectionBins.get(binName);
          if (bin == null)
          {
            bin = new ConnectionBin(threadContext, throttlingGroupName, binName);
            connectionBins.put(binName, bin);
          }
        }
      }
      
      synchronized (fetchBins)
      {
        for (String binName : binNames)
        {
          FetchBin bin = fetchBins.get(binName);
          if (bin == null)
          {
            bin = new FetchBin(threadContext, throttlingGroupName, binName);
            fetchBins.put(binName, bin);
          }
        }
      }
      
      synchronized (throttleBins)
      {
        for (String binName : binNames)
        {
          ThrottleBin bin = throttleBins.get(binName);
          if (bin == null)
          {
            bin = new ThrottleBin(threadContext, throttlingGroupName, binName);
            throttleBins.put(binName, bin);
          }
        }
      }
      
      return new ConnectionThrottler(this, binNames);
    }
    
    /** Update the throttle spec.
    *@param throttleSpec is the new throttle spec for this throttle group.
    */
    public synchronized void updateThrottleSpecification(IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.throttleSpec = throttleSpec;
    }
    
    
    // IConnectionThrottler support methods
    
    /** Wait for a connection to become available.
    *@param poolCount is a description of how many connections
    * are available in the current pool, across all bins.
    *@return the IConnectionThrottler codes for results.
    */
    public int waitConnectionAvailable(String[] binNames, AtomicInteger[] poolCounts, IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      // Each bin can signal something different.  Bins that signal
      // CONNECTION_FROM_NOWHERE are shutting down, but there's also
      // apparently the conflicting possibilities of distinct answers of
      // CONNECTION_FROM_POOL and CONNECTION_FROM_CREATION.
      // However: the pool count we track is in fact N * the actual pool count,
      // where N is the number of bins in each connection.  This means that a conflict 
      // is ALWAYS due to two entities simultaneously calling waitConnectionAvailable(),
      // and deadlocking each other.  The solution is therefore to back off and retry.

      // This is the retry loop
      while (true)
      {
        int currentRecommendation = IConnectionThrottler.CONNECTION_FROM_NOWHERE;
        
        boolean retry = false;

        // First, make sure all the bins exist, and reserve a slot in each
        int i = 0;
        while (i < binNames.length)
        {
          String binName = binNames[i];
          ConnectionBin bin;
          synchronized (connectionBins)
          {
            bin = connectionBins.get(binName);
          }
          if (bin != null)
          {
            // Reserve a slot
            int result;
            try
            {
              result = bin.waitConnectionAvailable(poolCounts[i],breakCheck);
            }
            catch (Throwable e)
            {
              while (i > 0)
              {
                i--;
                binName = binNames[i];
                synchronized (connectionBins)
                {
                  bin = connectionBins.get(binName);
                }
                if (bin != null)
                  bin.undoReservation(currentRecommendation, poolCounts[i]);
              }
              if (e instanceof BreakException)
                throw (BreakException)e;
              if (e instanceof InterruptedException)
                throw (InterruptedException)e;
              if (e instanceof Error)
                throw (Error)e;
              if (e instanceof RuntimeException)
                throw (RuntimeException)e;
              throw new RuntimeException("Unexpected exception of type '"+e.getClass().getName()+"': "+e.getMessage(),e);
            }
            if (result == IConnectionThrottler.CONNECTION_FROM_NOWHERE)
            {
              // Release previous reservations, and either return, or retry
              while (i > 0)
              {
                i--;
                binName = binNames[i];
                synchronized (connectionBins)
                {
                  bin = connectionBins.get(binName);
                }
                if (bin != null)
                  bin.undoReservation(currentRecommendation, poolCounts[i]);
              }
              return result;
            }

            if (currentRecommendation != IConnectionThrottler.CONNECTION_FROM_NOWHERE && currentRecommendation != result)
            {
              // Release all previous reservations, including this one, and either return, or retry
              bin.undoReservation(result, poolCounts[i]);
              while (i > 0)
              {
                i--;
                binName = binNames[i];
                synchronized (connectionBins)
                {
                  bin = connectionBins.get(binName);
                }
                if (bin != null)
                  bin.undoReservation(currentRecommendation, poolCounts[i]);
              }

              // Break out of the outer loop so we can retry
              retry = true;
              break;
            }

            if (currentRecommendation == IConnectionThrottler.CONNECTION_FROM_NOWHERE)
              currentRecommendation = result;
          }
          i++;
        }
        
        if (retry)
          continue;
        
        // Complete the reservation process (if that is what we decided)
        if (currentRecommendation == IConnectionThrottler.CONNECTION_FROM_CREATION)
        {
          // All reservations have been made!  Convert them.
          for (String binName : binNames)
          {
            ConnectionBin bin;
            synchronized (connectionBins)
            {
              bin = connectionBins.get(binName);
            }
            if (bin != null)
              bin.noteConnectionCreation();
          }
        }

        return currentRecommendation;
      }
      
    }
    
    public IFetchThrottler getNewConnectionFetchThrottler(String[] binNames)
    {
      return new FetchThrottler(this, binNames);
    }
    
    public boolean noteReturnedConnection(String[] binNames)
    {
      // If ANY of the bins think the connection should be destroyed, then that will be
      // the recommendation.
      synchronized (connectionBins)
      {
        boolean destroyConnection = false;

        for (String binName : binNames)
        {
          ConnectionBin bin = connectionBins.get(binName);
          if (bin != null)
          {
            destroyConnection |= bin.shouldReturnedConnectionBeDestroyed();
          }
        }
        
        return destroyConnection;
      }
    }
    
    public boolean checkDestroyPooledConnection(String[] binNames, AtomicInteger[] poolCounts)
    {
      // Only if all believe we can destroy a pool connection, will we do it.
      // This is because some pools may be empty, etc.
      synchronized (connectionBins)
      {
        boolean destroyConnection = false;

        int i = 0;
        while (i < binNames.length)
        {
          String binName = binNames[i];
          ConnectionBin bin = connectionBins.get(binName);
          if (bin != null)
          {
            int result = bin.shouldPooledConnectionBeDestroyed(poolCounts[i]);
            if (result == ConnectionBin.CONNECTION_POOLEMPTY)
            {
              // Give up now, and undo all the other bins
              while (i > 0)
              {
                i--;
                binName = binNames[i];
                bin = connectionBins.get(binName);
                bin.undoPooledConnectionDecision(poolCounts[i]);
              }
              return false;
            }
            else if (result == ConnectionBin.CONNECTION_DESTROY)
            {
              destroyConnection = true;
            }
          }
          i++;
        }
        
        if (destroyConnection)
          return true;
        
        // Undo pool reservation, since everything is apparently within bounds.
        for (int j = 0; j < binNames.length; j++)
        {
          ConnectionBin bin = connectionBins.get(binNames[j]);
          if (bin != null)
            bin.undoPooledConnectionDecision(poolCounts[j]);
        }
        
        return false;
      }

    }
    
    /** Connection expiration is tricky, because even though a connection may be identified as
    * being expired, at the very same moment it could be handed out in another thread.  So there
    * is a natural race condition present.
    * The way the connection throttler deals with that is to allow the caller to reserve a connection
    * for expiration.  This must be called BEFORE the actual identified connection is removed from the
    * connection pool.  If the value returned by this method is "true", then a connection MUST be removed
    * from the pool and destroyed, whether or not the identified connection is actually still available for
    * destruction or not.
    *@return true if a connection from the pool can be expired.  If true is returned, noteConnectionDestruction()
    *  MUST be called once the connection has actually been destroyed.
    */
    public boolean checkExpireConnection(String[] binNames, AtomicInteger[] poolCounts)
    {
      synchronized (connectionBins)
      {
        int i = 0;
        while (i < binNames.length)
        {
          String binName = binNames[i];
          ConnectionBin bin = connectionBins.get(binName);
          if (bin != null)
          {
            if (!bin.hasPooledConnection(poolCounts[i]))
            {
              // Give up now, and undo all the other bins
              while (i > 0)
              {
                i--;
                binName = binNames[i];
                bin = connectionBins.get(binName);
                bin.undoPooledConnectionDecision(poolCounts[i]);
              }
              return false;
            }
          }
          i++;
        }
        return true;
      }
    }

    public void noteConnectionReturnedToPool(String[] binNames, AtomicInteger[] poolCounts)
    {
      synchronized (connectionBins)
      {
        for (int j = 0; j < binNames.length; j++)
        {
          ConnectionBin bin = connectionBins.get(binNames[j]);
          if (bin != null)
            bin.noteConnectionReturnedToPool(poolCounts[j]);
        }
      }
    }

    public void noteConnectionDestroyed(String[] binNames)
    {
      synchronized (connectionBins)
      {
        for (String binName : binNames)
        {
          ConnectionBin bin = connectionBins.get(binName);
          if (bin != null)
            bin.noteConnectionDestroyed();
        }
      }
    }
    
    // IFetchThrottler support methods
    
    /** Get permission to fetch a document.  This grants permission to start
    * fetching a single document, within the connection that has already been
    * granted permission that created this object.
    *@param binNames are the names of the bins.
    *@return false if being shut down
    */
    public boolean obtainFetchDocumentPermission(String[] binNames, IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      // First, make sure all the bins exist, and reserve a slot in each
      int i = 0;
      while (i < binNames.length)
      {
        String binName = binNames[i];
        FetchBin bin;
        synchronized (fetchBins)
        {
          bin = fetchBins.get(binName);
        }
        // Reserve a slot
        try
        {
          if (bin == null || !bin.reserveFetchRequest(breakCheck))
          {
            // Release previous reservations, and return null
            while (i > 0)
            {
              i--;
              binName = binNames[i];
              synchronized (fetchBins)
              {
                bin = fetchBins.get(binName);
              }
              if (bin != null)
                bin.clearReservation();
            }
            return false;
          }
        }
        catch (BreakException e)
        {
          // Release previous reservations, and rethrow
          while (i > 0)
          {
            i--;
            binName = binNames[i];
            synchronized (fetchBins)
            {
              bin = fetchBins.get(binName);
            }
            if (bin != null)
              bin.clearReservation();
          }
          throw e;
        }
        i++;
      }
      
      // All reservations have been made!  Convert them.
      // (These are guaranteed to succeed - but they may wait)
      i = 0;
      while (i < binNames.length)
      {
        String binName = binNames[i];
        FetchBin bin;
        synchronized (fetchBins)
        {
          bin = fetchBins.get(binName);
        }
        if (bin != null)
        {
          try
          {
            if (!bin.waitNextFetch(breakCheck))
            {
              // Undo the reservations we haven't processed yet
              while (i < binNames.length)
              {
                binName = binNames[i];
                synchronized (fetchBins)
                {
                  bin = fetchBins.get(binName);
                }
                if (bin != null)
                  bin.clearReservation();
                i++;
              }
              return false;
            }
          }
          catch (BreakException e)
          {
            // Undo the reservations we haven't processed yet
            while (i < binNames.length)
            {
              binName = binNames[i];
              synchronized (fetchBins)
              {
                bin = fetchBins.get(binName);
              }
              if (bin != null)
                bin.clearReservation();
              i++;
            }
            throw e;
          }
        }
        i++;
      }
      return true;
    }
    
    public IStreamThrottler createFetchStream(String[] binNames)
    {
      // Do a "begin fetch" for all throttle bins
      synchronized (throttleBins)
      {
        for (String binName : binNames)
        {
          ThrottleBin bin = throttleBins.get(binName);
          if (bin != null)
            bin.beginFetch();
        }
      }
      
      return new StreamThrottler(this, binNames);
    }
    
    // IStreamThrottler support methods
    
    /** Obtain permission to read a block of bytes.  This method may wait until it is OK to proceed.
    * The throttle group, bin names, etc are already known
    * to this specific interface object, so it is unnecessary to include them here.
    *@param byteCount is the number of bytes to get permissions to read.
    *@return true if the wait took place as planned, or false if the system is being shut down.
    */
    public boolean obtainReadPermission(String[] binNames, int byteCount, IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      int i = 0;
      while (i < binNames.length)
      {
        String binName = binNames[i];
        ThrottleBin bin;
        synchronized (throttleBins)
        {
          bin = throttleBins.get(binName);
        }
        try
        {
          if (bin == null || !bin.beginRead(byteCount, breakCheck))
          {
            // End bins we've already done, and exit
            while (i > 0)
            {
              i--;
              binName = binNames[i];
              synchronized (throttleBins)
              {
                bin = throttleBins.get(binName);
              }
              if (bin != null)
                bin.endRead(byteCount,0);
            }
            return false;
          }
        }
        catch (BreakException e)
        {
          // End bins we've already done, and exit
          while (i > 0)
          {
            i--;
            binName = binNames[i];
            synchronized (throttleBins)
            {
              bin = throttleBins.get(binName);
            }
            if (bin != null)
              bin.endRead(byteCount,0);
          }
          throw e;
        }
        i++;
      }
      return true;
    }
      
    /** Note the completion of the read of a block of bytes.  Call this after
    * obtainReadPermission() was successfully called, and bytes were successfully read.
    *@param origByteCount is the originally requested number of bytes to get permissions to read.
    *@param actualByteCount is the number of bytes actually read.
    */
    public void releaseReadPermission(String[] binNames, int origByteCount, int actualByteCount)
    {
      synchronized (throttleBins)
      {
        for (String binName : binNames)
        {
          ThrottleBin bin = throttleBins.get(binName);
          if (bin != null)
            bin.endRead(origByteCount, actualByteCount);
        }
      }
    }

    /** Note the stream being closed.
    */
    public void closeStream(String[] binNames)
    {
      synchronized (throttleBins)
      {
        for (String binName : binNames)
        {
          ThrottleBin bin = throttleBins.get(binName);
          if (bin != null)
            bin.endFetch();
        }
      }
    }

    // Bookkeeping methods
    
    /** Call this periodically.
    */
    public synchronized void poll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // Go through all existing bins and update each one.
      synchronized (connectionBins)
      {
        for (ConnectionBin bin : connectionBins.values())
        {
          bin.updateMaxActiveConnections(throttleSpec.getMaxOpenConnections(bin.getBinName()));
          bin.poll(threadContext);
        }
      }
  
      synchronized (fetchBins)
      {
        for (FetchBin bin : fetchBins.values())
        {
          bin.updateMinTimeBetweenFetches(throttleSpec.getMinimumMillisecondsPerFetch(bin.getBinName()));
          bin.poll(threadContext);
        }
      }
      
      synchronized (throttleBins)
      {
        for (ThrottleBin bin : throttleBins.values())
        {
          bin.updateMinimumMillisecondsPerByte(throttleSpec.getMinimumMillisecondsPerByte(bin.getBinName()));
          bin.poll(threadContext);
        }
      }
      
    }
    
    /** Free unused resources.
    */
    public synchronized void freeUnusedResources(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // Does nothing; there are not really resources to free
    }
    
    /** Destroy this pool.
    */
    public synchronized void destroy(IThreadContext threadContext)
      throws ManifoldCFException
    {
      synchronized (connectionBins)
      {
        Iterator<ConnectionBin> binIter = connectionBins.values().iterator();
        while (binIter.hasNext())
        {
          ConnectionBin bin = binIter.next();
          bin.shutDown(threadContext);
          binIter.remove();
        }
      }
      
      synchronized (fetchBins)
      {
        Iterator<FetchBin> binIter = fetchBins.values().iterator();
        while (binIter.hasNext())
        {
          FetchBin bin = binIter.next();
          bin.shutDown(threadContext);
          binIter.remove();
        }
      }
      
      synchronized (throttleBins)
      {
        Iterator<ThrottleBin> binIter = throttleBins.values().iterator();
        while (binIter.hasNext())
        {
          ThrottleBin bin = binIter.next();
          bin.shutDown(threadContext);
          binIter.remove();
        }
      }

    }
  }
  
  /** Connection throttler implementation class.
  * This class instance stores some parameters and links back to ThrottlingGroup.  But each class instance
  * models a connection pool with the specified bins.  But the description of each pool consists of more than just
  * the bin names that describe the throttling - it also may include connection parameters which we have
  * no insight into at this level.
  *
  * Thus, in order to do pool tracking properly, we cannot simply rely on the individual connection bin instances
  * to do all the work, since they cannot distinguish between different pools properly.  So that leaves us with
  * two choices.  (1) We can somehow push the separate pool instance parameters down to the connection bin
  * level, or (2) the connection bins cannot actually do any waiting or blocking.
  *
  * The benefit of having blocking take place in connection bins is that they are in fact designed to be precisely
  * the thing you would want to synchronize on.   If we presume that the waits happen in those classes,
  * then we need the ability to send in our local pool count to them, and we need to be able to "wake up"
  * those underlying classes when the local pool count changes.
  */
  protected static class ConnectionThrottler implements IConnectionThrottler
  {
    protected final ThrottlingGroup parent;
    protected final String[] binNames;
    protected final AtomicInteger[] poolCounts;
    
    // Keep track of local pool parameters.

    public ConnectionThrottler(ThrottlingGroup parent, String[] binNames)
    {
      this.parent = parent;
      this.binNames = binNames;
      this.poolCounts = new AtomicInteger[binNames.length];
      for (int i = 0; i < poolCounts.length; i++)
        poolCounts[i] = new AtomicInteger(0);
    }
    
    /** Get permission to grab a connection for use.  If this object believes there is a connection
    * available in the pool, it will update its pool size variable and return   If not, this method
    * evaluates whether a new connection should be created.  If neither condition is true, it
    * waits until a connection is available.
    *@return whether to take the connection from the pool, or create one, or whether the
    * throttler is being shut down.
    */
    @Override
    public int waitConnectionAvailable()
      throws InterruptedException
    {
      try
      {
        return waitConnectionAvailable(null);
      }
      catch (BreakException e)
      {
        throw new RuntimeException("Unexpected break exception: "+e.getMessage(),e);
      }
    }

    /** Get permission to grab a connection for use.  If this object believes there is a connection
    * available in the pool, it will update its pool size variable and return   If not, this method
    * evaluates whether a new connection should be created.  If neither condition is true, it
    * waits until a connection is available.
    *@return whether to take the connection from the pool, or create one, or whether the
    * throttler is being shut down.
    */
    @Override
    public int waitConnectionAvailable(IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      return parent.waitConnectionAvailable(binNames, poolCounts, breakCheck);
    }
    
    /** For a new connection, obtain the fetch throttler to use for the connection.
    * If the result from waitConnectionAvailable() is CONNECTION_FROM_CREATION,
    * the calling code is expected to create a connection using the result of this method.
    *@return the fetch throttler for a new connection.
    */
    @Override
    public IFetchThrottler getNewConnectionFetchThrottler()
    {
      return parent.getNewConnectionFetchThrottler(binNames);
    }
    
    /** For returning a connection from use, there is only one method.  This method signals
    /* whether a formerly in-use connection should be placed back in the pool or destroyed.
    *@return true if the connection should NOT be put into the pool but should instead
    *  simply be destroyed.  If true is returned, the caller MUST call noteConnectionDestroyed()
    *  (below) in order for the bookkeeping to work.
    */
    @Override
    public boolean noteReturnedConnection()
    {
      return parent.noteReturnedConnection(binNames);
    }
    
    /** This method calculates whether a connection should be taken from the pool and destroyed
    /* in order to meet quota requirements.  If this method returns
    /* true, you MUST remove a connection from the pool, and you MUST call
    /* noteConnectionDestroyed() afterwards.
    *@return true if a pooled connection should be destroyed.  If true is returned, the
    * caller MUST call noteConnectionDestroyed() (below) in order for the bookkeeping to work.
    */
    @Override
    public boolean checkDestroyPooledConnection()
    {
      return parent.checkDestroyPooledConnection(binNames, poolCounts);
    }
    
    /** Connection expiration is tricky, because even though a connection may be identified as
    * being expired, at the very same moment it could be handed out in another thread.  So there
    * is a natural race condition present.
    * The way the connection throttler deals with that is to allow the caller to reserve a connection
    * for expiration.  This must be called BEFORE the actual identified connection is removed from the
    * connection pool.  If the value returned by this method is "true", then a connection MUST be removed
    * from the pool and destroyed, whether or not the identified connection is actually still available for
    * destruction or not.
    *@return true if a connection from the pool can be expired.  If true is returned, noteConnectionDestruction()
    *  MUST be called once the connection has actually been destroyed.
    */
    @Override
    public boolean checkExpireConnection()
    {
      return parent.checkExpireConnection(binNames, poolCounts);
    }
    
    /** Note that a connection has been returned to the pool.  Call this method after a connection has been
    * placed back into the pool and is available for use.
    */
    @Override
    public void noteConnectionReturnedToPool()
    {
      parent.noteConnectionReturnedToPool(binNames, poolCounts);
    }

    /** Note that a connection has been destroyed.  Call this method ONLY after noteReturnedConnection()
    * or checkDestroyPooledConnection() returns true, AND the connection has been already
    * destroyed.
    */
    @Override
    public void noteConnectionDestroyed()
    {
      parent.noteConnectionDestroyed(binNames);
    }
  }
  
  /** Fetch throttler implementation class.
  * This basically stores some parameters and links back to ThrottlingGroup.
  */
  protected static class FetchThrottler implements IFetchThrottler
  {
    protected final ThrottlingGroup parent;
    protected final String[] binNames;
    
    public FetchThrottler(ThrottlingGroup parent, String[] binNames)
    {
      this.parent = parent;
      this.binNames = binNames;
    }
    
    /** Get permission to fetch a document.  This grants permission to start
    * fetching a single document, within the connection that has already been
    * granted permission that created this object.
    *@return false if the throttler is being shut down.
    */
    @Override
    public boolean obtainFetchDocumentPermission()
      throws InterruptedException
    {
      try
      {
        return obtainFetchDocumentPermission(null);
      }
      catch (BreakException e)
      {
        throw new RuntimeException("Unexpected break exception: "+e.getMessage(),e);
      }
    }

    /** Get permission to fetch a document.  This grants permission to start
    * fetching a single document, within the connection that has already been
    * granted permission that created this object.
    *@return false if the throttler is being shut down.
    */
    @Override
    public boolean obtainFetchDocumentPermission(IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      return parent.obtainFetchDocumentPermission(binNames,breakCheck);
    }
    
    /** Open a fetch stream.  When done (or aborting), call
    * IStreamThrottler.closeStream() to note the completion of the document
    * fetch activity.
    *@return the stream throttler to use to throttle the actual data access.
    */
    @Override
    public IStreamThrottler createFetchStream()
    {
      return parent.createFetchStream(binNames);
    }

  }
  
  /** Stream throttler implementation class.
  * This basically stores some parameters and links back to ThrottlingGroup.
  */
  protected static class StreamThrottler implements IStreamThrottler
  {
    protected final ThrottlingGroup parent;
    protected final String[] binNames;
    
    public StreamThrottler(ThrottlingGroup parent, String[] binNames)
    {
      this.parent = parent;
      this.binNames = binNames;
    }

    /** Obtain permission to read a block of bytes.  This method may wait until it is OK to proceed.
    * The throttle group, bin names, etc are already known
    * to this specific interface object, so it is unnecessary to include them here.
    *@param byteCount is the number of bytes to get permissions to read.
    *@return true if the wait took place as planned, or false if the system is being shut down.
    */
    @Override
    public boolean obtainReadPermission(int byteCount)
      throws InterruptedException
    {
      try
      {
        return obtainReadPermission(byteCount, null);
      }
      catch (BreakException e)
      {
        throw new RuntimeException("Unexpected break exception: "+e.getMessage(),e);
      }
    }
      
    /** Obtain permission to read a block of bytes.  This method may wait until it is OK to proceed.
    * The throttle group, bin names, etc are already known
    * to this specific interface object, so it is unnecessary to include them here.
    *@param byteCount is the number of bytes to get permissions to read.
    *@param breakCheck is the break check object.
    *@return true if the wait took place as planned, or false if the system is being shut down.
    */
    @Override
    public boolean obtainReadPermission(int byteCount, IBreakCheck breakCheck)
      throws InterruptedException, BreakException
    {
      return parent.obtainReadPermission(binNames, byteCount, breakCheck);
    }

    /** Note the completion of the read of a block of bytes.  Call this after
    * obtainReadPermission() was successfully called, and bytes were successfully read.
    *@param origByteCount is the originally requested number of bytes to get permissions to read.
    *@param actualByteCount is the number of bytes actually read.
    */
    @Override
    public void releaseReadPermission(int origByteCount, int actualByteCount)
    {
      parent.releaseReadPermission(binNames, origByteCount, actualByteCount);
    }

    /** Note the stream being closed.
    */
    @Override
    public void closeStream()
    {
      parent.closeStream(binNames);
    }

  }
  
}
