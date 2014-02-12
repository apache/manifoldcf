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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;
import java.io.*;

/** Base class.  One instance of this object exists for each lock on each JVM!
*/
public class LockObject
{
  public static final String _rcsid = "@(#)$Id: LockObject.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final Object lockKey;

  private LockPool lockPool;
  private volatile boolean obtainedWrite = false;  // Set to true if this object already owns the permission to exclusively write
  private volatile int obtainedRead = 0;           // Set to a count if this object already owns the permission to read
  private volatile int obtainedNonExWrite = 0;     // Set to a count if this object already owns the permission to non-exclusively write

  protected static final String LOCKEDANOTHERTHREAD = "Locked by another thread in this JVM";
  protected static final String LOCKEDANOTHERJVM = "Locked by another JVM";


  public LockObject(LockPool lockPool, Object lockKey)
  {
    this.lockPool = lockPool;
    this.lockKey = lockKey;
  }

  public synchronized void makeInvalid()
  {
    this.lockPool = null;
  }

  /** This method WILL NOT BE CALLED UNLESS we are actually committing a write lock for the
  * first time for a given thread.
  */
  public synchronized void enterWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      try
      {
        // Does another thread in this JVM have the writelock?
        if (obtainedWrite)
          throw new LocalLockException(LOCKEDANOTHERTHREAD);
        // Got the write token!
        if (obtainedRead > 0 || obtainedNonExWrite > 0)
          throw new LocalLockException(LOCKEDANOTHERTHREAD);
        // Attempt to obtain a global write lock
        obtainGlobalWriteLock();
        obtainedWrite = true;
        return;
      }
      catch (LocalLockException le)
      {
        wait();
      }
    }
  }

  /** Note well: Upgrading a read lock to a non-ex write lock is tricky.  The code inside the
  * lock should execute only when there are NO threads that are executing in a read-locked area that
  * aren't waiting to enter the non-ex write lock area!  This is therefore essentially an illegal codepath,
  * because it will lead inevitably to deadlock, as is going from a read-locked area into a write-locked area,
  * or from a non-ex write area into an
  * exclusive write area.
  */
  public synchronized void enterWriteLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    if (lockPool == null)
      throw new ExpiredObjectException("Invalid");

    // Does another thread in this JVM have the writelock?
    if (obtainedWrite)
      throw new LocalLockException(LOCKEDANOTHERTHREAD);
    // Got the write token!
    if (obtainedRead > 0 || obtainedNonExWrite > 0)
      throw new LocalLockException(LOCKEDANOTHERTHREAD);
    // Attempt to obtain a global write lock
    obtainGlobalWriteLockNoWait();
    obtainedWrite = true;
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
        ManifoldCF.sleep(10L);
      }
    }
  }

  public synchronized boolean leaveWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    if (lockPool == null)
      throw new ExpiredObjectException("Invalid");

    if (obtainedWrite == false)
      throw new RuntimeException("JVM failure: Don't hold lock for object "+this.toString());
    
    clearGlobalWriteLock();

    obtainedWrite = false;
    notifyAll();
    return true;
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
        ManifoldCF.sleep(10L);
      }
    }
  }

  public synchronized void enterNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // System.out.println("Entering write lock for resource "+lockFileName);
    while (true)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");

      try
      {          
        // Does another thread in this JVM have the lock?
        if (obtainedWrite || obtainedRead > 0)
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
        wait();
      }
    }
  }

  /** Note well: Upgrading a read lock to a non-ex write lock is tricky.  The code inside the
  * lock should execute only when there are NO threads that are executing in a read-locked area that
  * aren't waiting to enter the non-ex write lock area!  This is therefore essentially an illegal codepath,
  * because it will lead inevitably to deadlock, as is going from a read-locked area into a write-locked area,
  * or from a non-ex write area into an
  * exclusive write area.
  */
  public synchronized void enterNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    if (lockPool == null)
      throw new ExpiredObjectException("Invalid");

    // Does another thread in this JVM have the lock?
    if (obtainedWrite || obtainedRead > 0)
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
        ManifoldCF.sleep(10L);
      }
    }
  }

  public synchronized boolean leaveNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
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
    notifyAll();
    return true;
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
        ManifoldCF.sleep(10L);
      }
    }
  }

  public synchronized void enterReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      if (lockPool == null)
        throw new ExpiredObjectException("Invalid");
      try
      {
        if (obtainedWrite || obtainedNonExWrite > 0)
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
        wait();
      }
    }
  }

  public synchronized void enterReadLockNoWait()
    throws ManifoldCFException, LockException, LocalLockException, InterruptedException, ExpiredObjectException
  {
    if (lockPool == null)
      throw new ExpiredObjectException("Invalid");

    if (obtainedWrite || obtainedNonExWrite > 0)
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
        ManifoldCF.sleep(10L);
      }
    }
  }
  
  public synchronized boolean leaveReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
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
    notifyAll();
    return true;
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
        ManifoldCF.sleep(10L);
      }
    }
  }
  
}

