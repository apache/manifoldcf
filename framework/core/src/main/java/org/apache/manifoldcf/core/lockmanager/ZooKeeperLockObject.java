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
package org.apache.manifoldcf.core.lockmanager;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;
import java.io.*;

/** One instance of this object exists for each lock on each JVM!
* This is the ZooKeeper version of the lock.
*/
public class ZooKeeperLockObject extends LockObject
{
  public static final String _rcsid = "@(#)$Id$";

  private final static String LOCK_PATH_PREFIX = "/org.apache.manifoldcf.locks-";

  private final ZooKeeperConnectionPool pool;
  private final String lockPath;
  
  private ZooKeeperConnection currentConnection = null;

  public ZooKeeperLockObject(LockPool lockPool, Object lockKey, ZooKeeperConnectionPool pool)
  {
    super(lockPool,lockKey);
    this.pool = pool;
    this.lockPath = LOCK_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(lockKey.toString());
  }

  @Override
  protected void obtainGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before write locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      succeeded = currentConnection.obtainWriteLockNoWait(lockPath);
      if (!succeeded)
        throw new LockException(LOCKEDANOTHERJVM);
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void obtainGlobalWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before write locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      currentConnection.obtainWriteLock(lockPath);
      succeeded = true;
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void clearGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection == null)
      throw new IllegalStateException("Cannot clear write lock we don't have: "+lockPath);
    clearLock();
  }
  
  @Override
  protected void obtainGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before non-ex-write locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      succeeded = currentConnection.obtainNonExWriteLockNoWait(lockPath);
      if (!succeeded)
        throw new LockException(LOCKEDANOTHERJVM);
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void obtainGlobalNonExWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before non-ex-write locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      currentConnection.obtainNonExWriteLock(lockPath);
      succeeded = true;
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void clearGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection == null)
      throw new IllegalStateException("Cannot clear non-ex-write lock we don't have: "+lockPath);
    clearLock();
  }

  @Override
  protected void obtainGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before read locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      succeeded = currentConnection.obtainReadLockNoWait(lockPath);
      if (!succeeded)
        throw new LockException(LOCKEDANOTHERJVM);
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void obtainGlobalReadLock()
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a connection before read locking: "+lockPath);
    boolean succeeded = false;
    currentConnection = pool.grab();
    try
    {
      currentConnection.obtainReadLock(lockPath);
      succeeded = true;
    }
    finally
    {
      if (!succeeded)
      {
        pool.release(currentConnection);
        currentConnection = null;
      }
    }
  }

  @Override
  protected void clearGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (currentConnection == null)
      throw new IllegalStateException("Cannot clear read lock we don't have: "+lockPath);
    clearLock();
  }

  protected void clearLock()
    throws ManifoldCFException, InterruptedException
  {
    currentConnection.releaseLock();
    pool.release(currentConnection);
    currentConnection = null;
  }

}

