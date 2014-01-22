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

import java.util.*;
import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;

/** This class creates a first-come, first-serve local queue for locks.
* The usage model is as follows:
* (1) There is one lock gate per key for all threads.
* (2) The LockGate replaces LockObjects in the pool, but each LockGate refers to
*   a LockObject.  They are separate objects though because they require separate locking.
* (3) When a lock is desired, the appropriate LockGate method is called to obtain
*   the lock.  This method places the thread ID onto the queue, and waits until
*   the thread ID reaches the head of the queue before executing the lock request.
* (4) Only when the lock request is successful, or is aborted, is the thread ID
*   removed from the queue.  Write requests therefore serve to block read requests
*   until the write request is serviced.
* Preferred structure:
* <wait_for_permission>
* try {
* ... obtain the lock ...
* } finally {
*   <release_permission>
* }
* Problem: Delay in obtaining a lock causes LockGate to "back up".  This is because
* the thread ID is not removed before the thread blocks waiting to get the lock.
* One solution: Don't block while holding permission open.  But this defeats the whole
* point of the priority queue, which is to reserve locks in the order they are requested.
* A second solution is to work through threadRequests object notifications so that we
* can break out of those waits too.  So there may be a lockpool reference locked against threadRequests
* that also gets cleared etc.
*/
public class LockGate
{
  protected final List<Long> threadRequests = new ArrayList<Long>();
  protected final LockObject lockObject;
  protected final Object lockKey;
  protected LockPool lockPool;

  public LockGate(Object lockKey, LockObject lockObject, LockPool lockPool)
  {
    this.lockKey = lockKey;
    this.lockObject = lockObject;
    this.lockPool = lockPool;
  }
  
  public synchronized void makeInvalid()
  {
    this.lockPool = null;
    lockObject.makeInvalid();
  }

  protected void waitForPermission(Long threadID)
    throws InterruptedException, ExpiredObjectException
  {
    synchronized (this)
    {
      threadRequests.add(threadID);
    }
    try
    {
      // Now, wait until we are #1
      while (true)
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          if (threadRequests.get(0).equals(threadID))
            return;
          
          wait();
        }
      }
    }
    catch (InterruptedException e)
    {
      threadRequests.remove(threadID);
      throw e;
    }
    catch (ExpiredObjectException e)
    {
      threadRequests.remove(threadID);
      throw e;
    }
    catch (Error e)
    {
      threadRequests.remove(threadID);
      throw e;
    }
    catch (RuntimeException e)
    {
      threadRequests.remove(threadID);
      throw e;
    }
  }
  
  protected void freePermission(Long threadID)
  {
    synchronized (this)
    {
      threadRequests.remove(threadID);
      notifyAll();
    }
  }
  
  public void enterWriteLock(Long threadID)
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterWriteLock();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void enterWriteLockNoWait(Long threadID)
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterWriteLockNoWait();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void leaveWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // Leave, and if we succeed, flush from pool.
    lockObject.leaveWriteLock();
    synchronized (this)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");
      lockPool.releaseObject(lockKey, this);
    }
  }
  
  public void enterNonExWriteLock(Long threadID)
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterNonExWriteLock();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void enterNonExWriteLockNoWait(Long threadID)
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterNonExWriteLockNoWait();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void leaveNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // Leave, and if we succeed, flush from pool.
    lockObject.leaveNonExWriteLock();
    synchronized (this)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");
      lockPool.releaseObject(lockKey, this);
    }
  }

  public void enterReadLock(Long threadID)
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterReadLock();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void enterReadLockNoWait(Long threadID)
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    waitForPermission(threadID);
    try
    {
      lockObject.enterReadLockNoWait();
    }
    finally
    {
      freePermission(threadID);
    }
  }
  
  public void leaveReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // Leave, and if we succeed, flush from pool.
    lockObject.leaveReadLock();
    synchronized (this)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");
      lockPool.releaseObject(lockKey, this);
    }
  }

}
