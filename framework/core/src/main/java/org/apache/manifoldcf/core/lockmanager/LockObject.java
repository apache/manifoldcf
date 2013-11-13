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

  protected Object lockKey;

  private LockPool lockPool;
  private boolean obtainedWrite = false;  // Set to true if this object already owns the permission to exclusively write
  private int obtainedRead = 0;           // Set to a count if this object already owns the permission to read
  private int obtainedNonExWrite = 0;     // Set to a count if this object already owns the permission to non-exclusively write

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
  public void enterWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          while (true)
          {
            try
            {
              enterWriteLockNoWait();
              return;
            }
            catch (LocalLockException le)
            {
              wait();
            }
          }
        }
      }
      catch (LockException le2)
      {
        // Cross JVM lock; sleep!
        ManifoldCF.sleep(10);
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
  

  public void leaveWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          if (obtainedWrite == false)
            throw new RuntimeException("JVM failure: Don't hold lock for object "+this.toString());
          obtainedWrite = false;
          try
          {
            clearGlobalWriteLock();
          }
          catch (LockException le)
          {
            obtainedWrite = true;
            throw le;
          }
          catch (Error e)
          {
            obtainedWrite = true;
            throw e;
          }
          catch (RuntimeException e)
          {
            obtainedWrite = true;
            throw e;
          }

          // Lock is free, so release this object from the pool
          lockPool.releaseObject(lockKey,this);

          notifyAll();
          return;
        }
      }
      catch (LockException le)
      {
        ManifoldCF.sleep(10);
        // Loop around
      }
    }

  }

  protected void clearGlobalWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
  public void enterNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          // System.out.println("Entering write lock for resource "+lockFileName);
          while (true)
          {
            try
            {
              enterNonExWriteLockNoWait();
              return;
            }
            catch (LocalLockException le)
            {
              wait();
            }
          }
        }
      }
      catch (LockException le2)
      {
        // Cross JVM lock; sleep!
        ManifoldCF.sleep(10);
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
  

  public void leaveNonExWriteLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // System.out.println("Releasing non-ex-write lock for resource "+lockFileName.toString());
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          if (obtainedNonExWrite == 0)
            throw new RuntimeException("JVM error: Don't hold lock for object "+this.toString());
          obtainedNonExWrite--;
          if (obtainedNonExWrite > 0)
            return;

          try
          {
            clearGlobalNonExWriteLock();
          }
          catch (LockException le)
          {
            obtainedNonExWrite++;
            throw le;
          }
          catch (Error e)
          {
            obtainedNonExWrite++;
            throw e;
          }
          catch (RuntimeException e)
          {
            obtainedNonExWrite++;
            throw e;
          }

          // Lock is free, so release this object from the pool
          lockPool.releaseObject(lockKey,this);

          notifyAll();
          break;
        }
      }
      catch (LockException le)
      {
        ManifoldCF.sleep(10);
        // Loop around
      }
    }
    // System.out.println("Non-ex Write lock released for resource "+lockFileName.toString());
  }

  protected void clearGlobalNonExWriteLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  

  public void enterReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    // if (lockFileName != null)
    //      System.out.println("Entering read lock for resource "+lockFileName.toString()+" "+toString());
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          while (true)
          {
            try
            {
              enterReadLockNoWait();
              // if (lockFileName != null)
              //      System.out.println("Obtained read permission for resource "+lockFileName.toString());
              return;
            }
            catch (LocalLockException le)
            {
              wait();
            }
          }
        }
      }
      catch (LockException le)
      {
        ManifoldCF.sleep(10);
        // Loop around
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
  

  public void leaveReadLock()
    throws ManifoldCFException, InterruptedException, ExpiredObjectException
  {
    while (true)
    {
      try
      {
        synchronized (this)
        {
          if (lockPool == null)
            throw new ExpiredObjectException("Invalid");

          if (obtainedRead == 0)
            throw new RuntimeException("JVM error: Don't hold lock for object "+this.toString());
          obtainedRead--;
          if (obtainedRead > 0)
          {
            return;
          }
          try
          {
            clearGlobalReadLock();
          }
          catch (LockException le)
          {
            obtainedRead++;
            throw le;
          }
          catch (Error e)
          {
            obtainedRead++;
            throw e;
          }
          catch (RuntimeException e)
          {
            obtainedRead++;
            throw e;
          }

          // Lock is free, so release this object from the pool
          lockPool.releaseObject(lockKey,this);

          notifyAll();
          return;
        }
      }
      catch (LockException le)
      {
        ManifoldCF.sleep(10);
        // Loop around
      }
    }
  }

  protected void clearGlobalReadLock()
    throws ManifoldCFException, LockException, InterruptedException
  {
  }
  
}

