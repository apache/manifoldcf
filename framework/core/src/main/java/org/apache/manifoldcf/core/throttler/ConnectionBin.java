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
  public ConnectionBin(String binName)
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

  /** Reserve a connection from this bin.  If there is no connection yet available to reserve, wait
  * until there is.
  *@return false if the wait was aborted because the bin became inactivated.
  */
  public synchronized boolean reserveAConnection()
    throws InterruptedException
  {
    // Reserved connections keep a slot available which can't be used by anyone else.
    // Connection bins are always sorted so that deadlocks can't occur.
    // Once all slots are reserved, the caller will go ahead and create the necessary connection
    // and convert the reservation to a new connection.
    while (true)
    {
      if (!isAlive)
        return false;
      if (inUseConnections + reservedConnections < maxActiveConnections)
      {
        reservedConnections++;
        return true;
      }
      // Wait for a connection to free up.  Note that it is up to the caller to free stuff up.
      wait();
    }
  }
  
  /** Clear reservation.
  */
  public synchronized void clearReservation()
  {
    if (reservedConnections == 0)
      throw new IllegalStateException("Can't clear a reservation we don't have");
    reservedConnections--;
    notifyAll();
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

  /** Note the destruction of an active connection that belongs to this bin.
  */
  public synchronized void noteConnectionDestruction()
  {
    inUseConnections--;
    notifyAll();
  }

  /** Count connections that are active.
  *@return connections that are in use.
  */
  public synchronized int countConnections()
  {
    return inUseConnections;
  }
  
  /** Shut down the bin, and release everything that is waiting on it.
  */
  public synchronized void shutDown()
  {
    isAlive = false;
    notifyAll();
  }
}

