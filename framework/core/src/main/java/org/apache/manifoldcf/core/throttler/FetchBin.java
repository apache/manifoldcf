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
  /** This is the last time a fetch was done on this bin */
  protected long lastFetchTime = 0L;
  /** This is the minimum time between fetches for this bin, in ms. */
  protected long minTimeBetweenFetches = Long.MAX_VALUE;
  /** Is the next fetch reserved? */
  protected boolean reserveNextFetch = false;

  /** Constructor. */
  public FetchBin(String binName)
  {
    this.binName = binName;
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
    // Wake up everything that's waiting.
    notifyAll();
  }

  /** Reserve a request to fetch a document from this bin.  The actual fetch is not yet committed
  * with this call, but if it succeeds for all bins associated with the document, then the caller
  * has permission to do the fetch, and can update the last fetch time.
  */
  public synchronized void reserveFetchRequest()
    throws InterruptedException
  {
    // First wait for the ability to even get the next fetch from this bin
    while (true)
    {
      if (!reserveNextFetch)
        break;
      wait();
    }
    reserveNextFetch = true;
  }
  
  /** Clear reserved request.
  */
  public synchronized void clearReservation()
  {
    if (!reserveNextFetch)
      throw new IllegalStateException("Can't clear a fetch reservation we don't have");
    reserveNextFetch = false;
  }
  
  /** Wait the necessary time to do the fetch.  Presumes we've reserved the next fetch
  * rights already, via reserveFetchRequest().
  *@return false if the wait did not complete because the bin was shut down.
  */
  public synchronized boolean waitNextFetch()
    throws InterruptedException
  {
    if (!reserveNextFetch)
      throw new IllegalStateException("No fetch request reserved!");
    
    while (true)
    {
      if (!isAlive)
        return false;
      if (minTimeBetweenFetches == Long.MAX_VALUE)
      {
        // wait forever - but eventually someone will set a smaller interval and wake us up.
        wait();
      }
      else
      {
        long currentTime = System.currentTimeMillis();
        // Compute how long we have to wait, based on the current time and the time of the last fetch.
        long waitAmt = lastFetchTime + minTimeBetweenFetches - currentTime;
        if (waitAmt <= 0L)
          return true;
        wait(waitAmt);
      }
    }
  }
  
  /** Note the beginning of fetch of a document from this bin.
  *@param currentTime is the actual time the fetch was started.
  */
  public synchronized void beginFetch(long currentTime)
  {
    if (currentTime > lastFetchTime)
      lastFetchTime = currentTime;
    reserveNextFetch = false;
  }

  /** Shut the bin down, and wake up all threads waiting on it.
  */
  public synchronized void shutDown()
  {
    isAlive = false;
    notifyAll();
  }
}

