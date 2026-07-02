/* $Id: LockObject.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import org.apache.manifoldcf.core.interfaces.LockException;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.system.ManifoldCF;

/** Base class.  One instance of this object exists for each lock on each JVM!
*/
public class LockObject
{
  public static final String _rcsid = "@(#)$Id: LockObject.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final Object lockKey;

  protected final ReentrantLock lock = new ReentrantLock();
  protected final Condition condition = lock.newCondition();

  private LockPool lockPool;
  private long writeLockedBy = -1L;
  private int writeLockCount = 0;
  private volatile int obtainedRead = 0;           // Set to a count if this object already owns the permission to read
  private volatile int obtainedNonExWrite = 0;     // Set to a count if this object already owns the permission to non-exclusively write

  protected static final String LOCKEDANOTHERTHREAD = "Locked by another thread in this JVM";
  protected static final String LOCKEDANOTHERJVM = "Locked by another JVM";


  public LockObject(LockPool lockPool, Object lockKey)
  {
    this.lockPool = lockPool;
    this.lockKey = lockKey;
  }

  public void makeInvalid()
  {
    lock.lock();
    try
    {
      this.lockPool = null;
    }
    finally
    {
      lock.unlock();
    }
  }

  /** This method WILL NOT BE CALLED UNLESS we are actually committing a write lock for the
  * first time for a given thread.
  */
  public void enterWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      while (true)
      {
        if (lockPool == null)
          throw new ExpiredObjectException("Invalid");

        try
        {
          long threadID = Thread.currentThread().getId();
          if (writeLockedBy != -1L)
          {
            if (writeLockedBy != threadID)
              throw new LocalLockException(LOCKEDANOTHERTHREAD);
            writeLockCount++;
            return;
          }
          // Does another thread in this JVM have the writelock?
          if (obtainedRead > 0 || obtainedNonExWrite > 0)
            throw new LocalLockException(LOCKEDANOTHERTHREAD);
          // Attempt to obtain a global write lock
          obtainGlobalWriteLock();
          writeLockedBy = threadID;
          writeLockCount = 1;
          return;
        }
        catch (LocalLockException le)
        {
          condition.await();
        }
      }
    }
    finally
    {
      lock.unlock();
    }
  }

  /** Note well: Upgrading a read lock to a non-ex write lock is tricky.  The code inside the
  * lock should execute only when there are NO threads that are executing in a read-locked area that
  * aren't waiting to enter the non-ex write lock area!  This is therefore essentially an illegal codepath,
  * because it will lead inevitably to deadlock, as is going from a read-locked area into a write-locked area,
  * or from a non-ex write area into an
  * exclusive write area.
  */
  public void enterWriteLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      long threadID = Thread.currentThread().getId();
      if (writeLockedBy != -1L)
      {
        if (writeLockedBy != threadID)
          throw new LocalLockException(LOCKEDANOTHERTHREAD);
        writeLockCount++;
        return;
      }
      // Got the write token!
      if (obtainedRead > 0 || obtainedNonExWrite > 0)
        throw new LocalLockException(LOCKEDANOTHERTHREAD);
      // Attempt to obtain a global write lock
      obtainGlobalWriteLockNoWait();
      writeLockedBy = threadID;
      writeLockCount = 1;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void obtainGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void obtainGlobalWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        obtainGlobalWriteLockNoWait();
        return;
      }
      catch (LockException e)
      {
        // Cross JVM lock; sleep!
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }

  public boolean leaveWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      if (writeLockedBy != Thread.currentThread().getId())
        throw new RuntimeException("JVM failure: Don't hold lock for object "+this.toString());
      
      if (writeLockCount > 1)
      {
        writeLockCount--;
        return false;
      }

      clearGlobalWriteLock();

      writeLockedBy = -1L;
      writeLockCount = 0;
      condition.signalAll();
      return true;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void clearGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void clearGlobalWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        clearGlobalWriteLockNoWait();
        return;
      }
      catch (LockException e)
      {
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }

  public void enterNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      // System.out.println("Entering write lock for resource "+lockFileName);
      while (true)
      {
        if (lockPool == null)
          throw new ExpiredObjectException("Invalid");

        try
        {          
          // Does another thread in this JVM have the lock?
          if (writeLockedBy != -1L || obtainedRead > 0)
            throw new LocalLockException(LOCKEDANOTHERTHREAD);
          // We've got the local non-ex write token
          if (obtainedNonExWrite > 0)
          {
            obtainedNonExWrite++;
            return;
          }
          obtainGlobalNonExWriteLock();
          obtainedNonExWrite++;
          return;
        }
        catch (LocalLockException le)
        {
          condition.await();
        }
      }
    }
    finally
    {
      lock.unlock();
    }
  }

  /** Note well: Upgrading a read lock to a non-ex write lock is tricky.  The code inside the
  * lock should execute only when there are NO threads that are executing in a read-locked area that
  * aren't waiting to enter the non-ex write lock area!  This is therefore essentially an illegal codepath,
  * because it will lead inevitably to deadlock, as is going from a read-locked area into a write-locked area,
  * or from a non-ex write area into an
  * exclusive write area.
  */
  public void enterNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      // Does another thread in this JVM have the lock?
      if (writeLockedBy != -1L || obtainedRead > 0)
        throw new LocalLockException(LOCKEDANOTHERTHREAD);
      // We've got the local non-ex write token
      if (obtainedNonExWrite > 0)
      {
        obtainedNonExWrite++;
        return;
      }
      obtainGlobalNonExWriteLockNoWait();
      obtainedNonExWrite++;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void obtainGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void obtainGlobalNonExWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        obtainGlobalNonExWriteLockNoWait();
        return;
      }
      catch (LockException e)
      {
        // Cross JVM lock; sleep!
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }

  public boolean leaveNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      if (obtainedNonExWrite == 0)
        throw new RuntimeException("JVM error: Don't hold lock for object "+this.toString());
      if (obtainedNonExWrite > 1)
      {
        obtainedNonExWrite--;
        return false;
      }

      clearGlobalNonExWriteLock();

      obtainedNonExWrite--;
      condition.signalAll();
      return true;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void clearGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void clearGlobalNonExWriteLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        clearGlobalNonExWriteLockNoWait();
        return;
      }
      catch (LockException e)
      {
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }

  public void enterReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      while (true)
      {
        if (lockPool == null)
          throw new ExpiredObjectException("Invalid");
        try
        {
          if (writeLockedBy != -1L || obtainedNonExWrite > 0)
            throw new LocalLockException(LOCKEDANOTHERTHREAD);
          if (obtainedRead > 0)
          {
            obtainedRead++;
            return;
          }
          // Got the read token locally!
          obtainGlobalReadLock();
          obtainedRead = 1;
          return;
        }
        catch (LocalLockException le)
        {
          condition.await();
        }
      }
    }
    finally
    {
      lock.unlock();
    }
  }

  public void enterReadLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      if (writeLockedBy != -1L || obtainedNonExWrite > 0)
        throw new LocalLockException(LOCKEDANOTHERTHREAD);
      if (obtainedRead > 0)
      {
        obtainedRead++;
        return;
      }
      // Got the read token locally!
      obtainGlobalReadLockNoWait();
      obtainedRead = 1;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void obtainGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void obtainGlobalReadLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        obtainGlobalReadLockNoWait();
        return;
      }
      catch (LockException e)
      {
        // Cross JVM lock; sleep!
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }
  
  public boolean leaveReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    lock.lock();
    try
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      if (obtainedRead == 0)
        throw new RuntimeException("JVM error: Don't hold lock for object "+this.toString());
      if (obtainedRead > 1)
      {
        obtainedRead--;
        return false;
      }
      
      clearGlobalReadLock();

      obtainedRead--;
      condition.signalAll();
      return true;
    }
    finally
    {
      lock.unlock();
    }
  }

  protected void clearGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  protected void clearGlobalReadLock()
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        clearGlobalReadLockNoWait();
        return;
      }
      catch (LockException e)
      {
        lock.unlock();
        try
        {
          ManifoldCF.sleep(10L);
        }
        finally
        {
          lock.lock();
        }
      }
    }
  }
  
}

