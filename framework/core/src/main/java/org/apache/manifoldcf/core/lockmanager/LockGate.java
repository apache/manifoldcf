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
* Seeing lockups.  These lockups are characterized by a thread waiting on a lock object
* while another thread waits on permission to do something else with the lock object.
* It is by no means clear at this point how this situation causes a hang-up; the 
* lock object is waiting to be awakened, but there is no obvious entity holding the lock elsewhere.
* But one thread (A) seems always to be in a multi-lock situation, waiting to obtain a lock, e.g.:
	at java.lang.Object.wait(Native Method)
	at java.lang.Object.wait(Object.java:503)
	at org.apache.manifoldcf.core.lockmanager.LockObject.enterWriteLock(LockObject.java:80)
	- locked <0x00000000fe205720> (a org.apache.manifoldcf.core.lockmanager.LockObject)
	at org.apache.manifoldcf.core.lockmanager.LockGate.enterWriteLock(LockGate.java:132)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enter(BaseLockManager.java:1483)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enterCriticalSections(BaseLockManager.java:920)
	at org.apache.manifoldcf.core.lockmanager.LockManager.enterCriticalSections(LockManager.java:455)
* Here's the second thread (B), which is waitingForPermission:
	at java.lang.Object.wait(Native Method)
	- waiting on <0x00000000f8b71c78> (a org.apache.manifoldcf.core.lockmanager.LockGate)
	at java.lang.Object.wait(Object.java:503)
	at org.apache.manifoldcf.core.lockmanager.LockGate.waitForPermission(LockGate.java:91)
	- locked <0x00000000f8b71c78> (a org.apache.manifoldcf.core.lockmanager.LockGate)
	at org.apache.manifoldcf.core.lockmanager.LockGate.enterWriteLock(LockGate.java:129)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enterWrite(BaseLockManager.java:1130)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enterWriteCriticalSection(BaseLockManager.java:896)
	at org.apache.manifoldcf.core.lockmanager.LockManager.enterWriteCriticalSection(LockManager.java:431)
	at org.apache.manifoldcf.core.interfaces.IDFactory.make(IDFactory.java:55)
* The problem is that (A) has already obtained permission, but cannot obtain the lock.  (B) is somehow blocking
* (A) from obtaining the lock even though it has not yet taken its own lock!  Or, maybe it has, and we don't see it in
* the stack trace.
* Another example: (C)
	at java.lang.Object.wait(Native Method)
	- waiting on <0x00000000ffbdc038> (a org.apache.manifoldcf.core.lockmanager.LockGate)
	at java.lang.Object.wait(Object.java:503)
	at org.apache.manifoldcf.core.lockmanager.LockGate.waitForPermission(LockGate.java:91)
	- locked <0x00000000ffbdc038> (a org.apache.manifoldcf.core.lockmanager.LockGate)
	at org.apache.manifoldcf.core.lockmanager.LockGate.enterReadLock(LockGate.java:211)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enter(BaseLockManager.java:1532)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enterLocks(BaseLockManager.java:813)
	at org.apache.manifoldcf.core.lockmanager.LockManager.enterLocks(LockManager.java:355)
* and (D):
	at java.lang.Object.wait(Native Method)
	- waiting on <0x00000000ffbdd2f8> (a org.apache.manifoldcf.core.lockmanager.LockObject)
	at java.lang.Object.wait(Object.java:503)
	at org.apache.manifoldcf.core.lockmanager.LockObject.enterWriteLock(LockObject.java:83)
	- locked <0x00000000ffbdd2f8> (a org.apache.manifoldcf.core.lockmanager.LockObject)
	at org.apache.manifoldcf.core.lockmanager.LockGate.enterWriteLock(LockGate.java:132)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enter(BaseLockManager.java:1483)
	at org.apache.manifoldcf.core.lockmanager.BaseLockManager.enterLocks(BaseLockManager.java:813)
	at org.apache.manifoldcf.core.lockmanager.LockManager.enterLocks(LockManager.java:355)
* Problem here: The LockGate 0x00000000ffbdc038 has no other instance anywhere, which should not be able to happen.
* Debugging must entail dumping ALL outstanding locks periodically -- and who holds each.
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
  
  public void makeInvalid()
  {
    synchronized (this)
    {
      this.lockPool = null;
      lockObject.makeInvalid();
    }
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
      freePermission(threadID);
      throw e;
    }
    catch (ExpiredObjectException e)
    {
      freePermission(threadID);
      throw e;
    }
    catch (Error e)
    {
      freePermission(threadID);
      throw e;
    }
    catch (RuntimeException e)
    {
      freePermission(threadID);
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
    synchronized (this)
    {
      // Leave, and if we succeed, flush from pool.
      if (lockObject.leaveWriteLock())
      {
        if (threadRequests.size() == 0 && lockPool != null)
          lockPool.releaseObject(lockKey, this);
      }
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
    synchronized (this)
    {
      // Leave, and if we succeed, flush from pool.
      if (lockObject.leaveNonExWriteLock())
      {
        if (threadRequests.size() == 0 && lockPool != null)
          lockPool.releaseObject(lockKey, this);
      }
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
    // Leave, and if we succeed (and the thread queue is empty), flush from pool.
    synchronized (this)
    {
      if (lockObject.leaveReadLock())
      {
        if (threadRequests.size() == 0 && lockPool != null)
          lockPool.releaseObject(lockKey, this);
      }
    }
  }

}
