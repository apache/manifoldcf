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
* NOTE WELL: This is entirely local in operation
*/
public class ConnectionBin
{
  /** This is the bin name which this connection pool belongs to */
  protected final String binName;
  /** This is the number of connections in this bin that are signed out and presumably in use */
  protected int inUseConnections = 0;
  /** This is the last time a fetch was done on this bin */
  protected long lastFetchTime = 0L;
  /** This object is what we synchronize on when we are waiting on a connection to free up for this
  * bin.  This is a separate object, because we also want to protect the integrity of the
  * ConnectionBin object itself, for which we'll use the ConnectionBin's synchronizer. */
  protected final Integer connectionWait = new Integer(0);

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

  /** Note the creation of an active connection that belongs to this bin.  The slots all must
  * have been reserved prior to the connection being created.
  */
  public synchronized void noteConnectionCreation()
  {
    inUseConnections++;
  }

  /** Note the destruction of an active connection that belongs to this bin.
  */
  public synchronized void noteConnectionDestruction()
  {
    inUseConnections--;
  }

  /** Note a new time for connection fetch for this pool.
  *@param currentTime is the time the fetch was started.
  */
  public synchronized void setLastFetchTime(long currentTime)
  {
    if (currentTime > lastFetchTime)
      lastFetchTime = currentTime;
  }

  /** Get the last fetch time.
  *@return the time.
  */
  public synchronized long getLastFetchTime()
  {
    return lastFetchTime;
  }

  /** Count connections that are in use.
  *@return connections that are in use.
  */
  public synchronized int countConnections()
  {
    return inUseConnections;
  }
}

