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

  private final static String LOCK_PATH_PREFIX = "org.apache.manifoldcf.locks/";

  private final ZooKeeperConnectionPool pool;
  private final String lockPath;
  
  public ZooKeeperLockObject(LockPool lockPool, Object lockKey, ZooKeeperConnectionPool pool)
  {
    super(lockPool,lockKey);
    this.pool = pool;
    this.lockPath = LOCK_PATH_PREFIX + lockKey.toString();
  }

  @Override
  protected void obtainGlobalWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.obtainGlobalWriteLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }

  @Override
  protected void clearGlobalWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.clearGlobalWriteLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }

  @Override
  protected void obtainGlobalNonExWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.obtainGlobalNonExWriteLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }

  @Override
  protected void clearGlobalNonExWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.clearGlobalNonExWriteLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }

  @Override
  protected void obtainGlobalReadLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.obtainGlobalReadLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }

  @Override
  protected void clearGlobalReadLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
    ZooKeeperConnection connection = pool.grab();
    try
    {
      connection.clearGlobalReadLock(lockPath);
    }
    finally
    {
      pool.release(connection);
    }
  }


}

