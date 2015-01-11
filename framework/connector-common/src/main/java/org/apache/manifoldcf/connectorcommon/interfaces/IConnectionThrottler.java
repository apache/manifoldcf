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
package org.apache.manifoldcf.connectorcommon.interfaces;

import java.util.*;

/** An IConnectionThrottler object is not thread-local.  It gates connection
* creation and pool management.
* The underlying model is a pool of connections.  A connection gets pulled off the pool and
* used to perform a fetch.  If there are insufficient connections in the pool, and there is
* sufficient capacity to create a new connection, a connection will be created instead.
* When the fetch is done, the connection is returned, and then there is a decision whether
* or not to put the connection back into the pool, or to destroy it.  Finally, the pool is
* periodically evaluated, and connections may be destroyed if either they have expired,
* or the allocated connections are still over capacity.
*
* This object does not in itself contain a connection pool - but it is intended to assist
* in the management of that pool.  Specifically, it tracks connections that are in the
* pool, and connections that are handed out for use, and performs ALL the waiting needed
* due to the pool being empty and/or the number of active connections being at or over
* the quota.
*/
public interface IConnectionThrottler
{
  public static final String _rcsid = "@(#)$Id$";

  // For grabbing a connection for use
  
  /** Get the connection from the pool */
  public final static int CONNECTION_FROM_POOL = 0;
  /** Create a connection */
  public final static int CONNECTION_FROM_CREATION = 1;
  /** Pool shutting down */
  public final static int CONNECTION_FROM_NOWHERE = -1;
  
  /** Get permission to grab a connection for use.  If this object believes there is a connection
  * available in the pool, it will update its pool size variable and return   If not, this method
  * evaluates whether a new connection should be created.  If neither condition is true, it
  * waits until a connection is available.
  *@return whether to take the connection from the pool, or create one, or whether the
  * throttler is being shut down.
  */
  public int waitConnectionAvailable()
    throws InterruptedException;

  /** Get permission to grab a connection for use.  If this object believes there is a connection
  * available in the pool, it will update its pool size variable and return   If not, this method
  * evaluates whether a new connection should be created.  If neither condition is true, it
  * waits until a connection is available.
  *@return whether to take the connection from the pool, or create one, or whether the
  * throttler is being shut down.
  */
  public int waitConnectionAvailable(IBreakCheck breakCheck)
    throws InterruptedException, BreakException;

  /** For a new connection, obtain the fetch throttler to use for the connection.
  * If the result from waitConnectionAvailable() is CONNECTION_FROM_CREATION,
  * the calling code is expected to create a connection using the result of this method.
  *@return the fetch throttler for a new connection.
  */
  public IFetchThrottler getNewConnectionFetchThrottler();
  
  /** This method indicates whether a formerly in-use connection should be placed back
  * in the pool or destroyed.
  *@return true if the connection should not be put into the pool but should instead
  *  simply be destroyed.  If true is returned, the caller MUST call noteConnectionDestroyed()
  *  after the connection is destroyed in order for the bookkeeping to work.  If false
  *  is returned, the caller MUST call noteConnectionReturnedToPool() after the connection
  *  is returned to the pool.
  */
  public boolean noteReturnedConnection();
  
  /** This method calculates whether a connection should be taken from the pool and destroyed
  /* in order to meet quota requirements.  If this method returns
  /* true, you MUST remove a connection from the pool, and you MUST call
  /* noteConnectionDestroyed() afterwards.
  *@return true if a pooled connection should be destroyed.  If true is returned, the
  * caller MUST call noteConnectionDestroyed() (below) in order for the bookkeeping to work.
  */
  public boolean checkDestroyPooledConnection();
  
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
  public boolean checkExpireConnection();
  
  /** Note that a connection has been returned to the pool.  Call this method after a connection has been
  * placed back into the pool and is available for use.
  */
  public void noteConnectionReturnedToPool();
  
  /** Note that a connection has been destroyed.  Call this method ONLY after noteReturnedConnection()
  * or checkDestroyPooledConnection() returns true, AND the connection has been already
  * destroyed.
  */
  public void noteConnectionDestroyed();
  
}
