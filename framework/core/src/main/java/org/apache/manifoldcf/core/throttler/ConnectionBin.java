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
import java.util.concurrent.atomic.*;

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
  /** This is the maximum number of active connections allowed for this bin */
  protected int maxActiveConnections = 0;
  /** This is the number of connections in this bin that have been reserved - that is, they
  * are promised to various callers, but those callers have not yet committed to obtaining them. */
  protected int reservedConnections = 0;
  /** This is the number of connections in this bin that are connected; immaterial whether they are
  * in use or in a pool somewhere. */
  protected int inUseConnections = 0;

  /** Constructor. */
  public ConnectionBin(IThreadContext threadContext, String binName)
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
  public synchronized void updateMaxActiveConnections(int maxActiveConnections)
  {
    // Update the number and wake up any waiting threads; they will take care of everything.
    this.maxActiveConnections = maxActiveConnections;
    notifyAll();
  }

  /** Wait for a connection to become available, in the context of an existing connection pool.
  *@param poolCount is the number of connections in the pool times the number of bins per connection.
  * This parameter is only ever changed in this class!!
  *@return a recommendation as to how to proceed, using the IConnectionThrottler values.  If the
  * recommendation is to create a connection, a slot will be reserved for that purpose.  A
  * subsequent call to noteConnectionCreation() will be needed to confirm the reservation, or clearReservation() to
  * release the reservation.
  */
  public synchronized int waitConnectionAvailable(AtomicInteger poolCount)
    throws InterruptedException
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
        return IConnectionThrottler.CONNECTION_FROM_POOL;
      }
      if (inUseConnections + reservedConnections < maxActiveConnections)
      {
        reservedConnections++;
        return IConnectionThrottler.CONNECTION_FROM_CREATION;
      }
      // Wait for a connection to free up.  Note that it is up to the caller to free stuff up.
      wait();
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
      poolCount.set(poolCount.get() + 1);
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
    return inUseConnections > maxActiveConnections;
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
      // Consider it removed from the pool for the purposes of consideration.  If we change our minds, we'll
      // return it, and no harm done.
      poolCount.set(currentPoolCount-1);
      // We don't count reserved connections here because those are not yet committed.
      if (inUseConnections > maxActiveConnections)
      {
        return CONNECTION_DESTROY;
      }
      return CONNECTION_WITHINBOUNDS;
    }
    return CONNECTION_POOLEMPTY;
  }

  /** Undo the decision to destroy a pooled connection.
  */
  public synchronized void undoPooledConnectionDecision(AtomicInteger poolCount)
  {
    poolCount.set(poolCount.get() + 1);
  }
  
  /** Note a connection returned to the pool.
  */
  public synchronized void noteConnectionReturnedToPool(AtomicInteger poolCount)
  {
    poolCount.set(poolCount.get() + 1);
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

  /** Shut down the bin, and release everything that is waiting on it.
  */
  public synchronized void shutDown(IThreadContext threadContext)
    throws ManifoldCFException
  {
    isAlive = false;
    notifyAll();
  }
}

