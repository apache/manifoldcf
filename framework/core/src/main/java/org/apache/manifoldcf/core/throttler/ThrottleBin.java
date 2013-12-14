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
import org.apache.manifoldcf.core.system.ManifoldCF;

/** Throttles for a bin.
* An instance of this class keeps track of the information needed to bandwidth throttle access
* to a url belonging to a specific bin.
*
* In order to calculate
* the effective "burst" fetches per second and bytes per second, we need to have some idea what the window is.
* For example, a long hiatus from fetching could cause overuse of the server when fetching resumes, if the
* window length is too long.
*
* One solution to this problem would be to keep a list of the individual fetches as records.  Then, we could
* "expire" a fetch by discarding the old record.  However, this is quite memory consumptive for all but the
* smallest intervals.
*
* Another, better, solution is to hook into the start and end of individual fetches.  These will, presumably, occur
* at the fastest possible rate without long pauses spent doing something else.  The only complication is that
* fetches may well overlap, so we need to "reference count" the fetches to know when to reset the counters.
* For "fetches per second", we can simply make sure we "schedule" the next fetch at an appropriate time, rather
* than keep records around.  The overall rate may therefore be somewhat less than the specified rate, but that's perfectly
* acceptable.
*
* Some notes on the algorithms used to limit server bandwidth impact
* ==================================================================
*
* In a single connection case, the algorithm we'd want to use works like this.  On the first chunk of a series,
* the total length of time and the number of bytes are recorded.  Then, prior to each subsequent chunk, a calculation
* is done which attempts to hit the bandwidth target by the end of the chunk read, using the rate of the first chunk
* access as a way of estimating how long it will take to fetch those next n bytes.
*
* For a multi-connection case, which this is, it's harder to either come up with a good maximum bandwidth estimate,
* and harder still to "hit the target", because simultaneous fetches will intrude.  The strategy is therefore:
*
* 1) The first chunk of any series should proceed without interference from other connections to the same server.
*    The goal here is to get a decent quality estimate without any possibility of overwhelming the server.
*
* 2) The bandwidth of the first chunk is treated as the "maximum bandwidth per connection".  That is, if other
*    connections are going on, we can presume that each connection will use at most the bandwidth that the first fetch
*    took.  Thus, by generating end-time estimates based on this number, we are actually being conservative and
*    using less server bandwidth.
*
* 3) For chunks that have started but not finished, we keep track of their size and estimated elapsed time in order to schedule when
*    new chunks from other connections can start.
*
* NOTE WELL: This is entirely local in operation
*/
public class ThrottleBin
{
  /** This signals whether the bin is alive or not. */
  protected boolean isAlive = true;
  /** This is the bin name which this throttle belongs to. */
  protected final String binName;
  /** This is the reference count for this bin (which records active references) */
  protected volatile int refCount = 0;
  /** The inverse rate estimate of the first fetch, in ms/byte */
  protected double rateEstimate = 0.0;
  /** Flag indicating whether a rate estimate is needed */
  protected volatile boolean estimateValid = false;
  /** Flag indicating whether rate estimation is in progress yet */
  protected volatile boolean estimateInProgress = false;
  /** The start time of this series */
  protected long seriesStartTime = -1L;
  /** Total actual bytes read in this series; this includes fetches in progress */
  protected long totalBytesRead = -1L;
  /** The minimum milliseconds per byte */
  protected double minimumMillisecondsPerByte = Double.MAX_VALUE;
  
  /** Constructor. */
  public ThrottleBin(String binName)
  {
    this.binName = binName;
  }

  /** Get the bin name. */
  public String getBinName()
  {
    return binName;
  }

  /** Update minimumMillisecondsPerBytePerServer */
  public void updateMinimumMillisecondsPerByte(double min)
  {
    this.minimumMillisecondsPerByte = min;
  }
  
  /** Note the start of a fetch operation for a bin.  Call this method just before the actual stream access begins.
  * May wait until schedule allows.
  */
  public void beginFetch()
    throws InterruptedException
  {
    synchronized (this)
    {
      if (refCount == 0)
      {
        // Now, reset bandwidth throttling counters
        estimateValid = false;
        rateEstimate = 0.0;
        totalBytesRead = 0L;
        estimateInProgress = false;
        seriesStartTime = -1L;
      }
      refCount++;
    }

  }

  /** Abort the fetch.
  */
  public void abortFetch()
  {
    synchronized (this)
    {
      refCount--;
    }
  }
    
  /** Note the start of an individual byte read of a specified size.  Call this method just before the
  * read request takes place.  Performs the necessary delay prior to reading specified number of bytes from the server.
  *@return false if the wait was interrupted due to the bin being shut down.
  */
  public boolean beginRead(int byteCount)
    throws InterruptedException
  {
    long currentTime = System.currentTimeMillis();

    synchronized (this)
    {
      while (true)
      {
        if (!isAlive)
          return false;
        if (estimateInProgress)
        {
          wait();
          continue;
        }
        if (estimateValid == false)
        {
          seriesStartTime = currentTime;
          estimateInProgress = true;
          // Add these bytes to the estimated total
          totalBytesRead += (long)byteCount;
          // Exit early; this thread isn't going to do any waiting
          return true;
        }

        // Estimate the time this read will take, and wait accordingly
        long estimatedTime = (long)(rateEstimate * (double)byteCount);

        // Figure out how long the total byte count should take, to meet the constraint
        long desiredEndTime = seriesStartTime + (long)(((double)(totalBytesRead + (long)byteCount)) * minimumMillisecondsPerByte);

        // The wait time is the different between our desired end time, minus the estimated time to read the data, and the
        // current time.  But it can't be negative.
        long waitTime = (desiredEndTime - estimatedTime) - currentTime;

        // If no wait is needed, go ahead and update what needs to be updated and exit.  Otherwise, do the wait.
        if (waitTime <= 0L)
        {
          // Add these bytes to the estimated total
          totalBytesRead += (long)byteCount;
          return true;
        }
        
        this.wait(waitTime);
        // Back around again...
      }
    }
  }

  /** Abort a read in progress.
  */
  public void abortRead()
  {
    synchronized (this)
    {
      if (estimateInProgress)
      {
        estimateInProgress = false;
        notifyAll();
      }
    }
  }
    
  /** Note the end of an individual read from the server.  Call this just after an individual read completes.
  * Pass the actual number of bytes read to the method.
  */
  public void endRead(int originalCount, int actualCount)
  {
    long currentTime = System.currentTimeMillis();

    synchronized (this)
    {
      totalBytesRead = totalBytesRead + (long)actualCount - (long)originalCount;
      if (estimateInProgress)
      {
        if (actualCount == 0)
          // Didn't actually get any bytes, so use 0.0
          rateEstimate = 0.0;
        else
          rateEstimate = ((double)(currentTime - seriesStartTime))/(double)actualCount;
        estimateValid = true;
        estimateInProgress = false;
        notifyAll();
      }
    }
  }

  /** Note the end of a fetch operation.  Call this method just after the fetch completes.
  */
  public boolean endFetch()
  {
    synchronized (this)
    {
      refCount--;
      return (refCount == 0);
    }

  }

  /** Shut down this bin.
  */
  public synchronized void shutDown()
  {
    isAlive = false;
    notifyAll();
  }
}

