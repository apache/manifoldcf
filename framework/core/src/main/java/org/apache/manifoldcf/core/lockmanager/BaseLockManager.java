/* $Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.util.*;
import java.io.*;

/** A lock manager manages locks and shared information across all threads and JVMs
* and cluster members.  There should be no more than ONE instance of this class per thread!!!
* The factory should enforce this.
* This is the base lock manager class.  Its implementation works solely within one JVM,
* which makes it ideal for single-process work.  Classes that handle multiple JVMs and thus
* need cross-JVM synchronization are thus expected to extend this class and override pertinent
* methods.
*/
public class BaseLockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // These are the lock/section types, in order of escalation
  protected final static int TYPE_READ = 1;
  protected final static int TYPE_WRITENONEX = 2;
  protected final static int TYPE_WRITE = 3;

  // These are for locks which putatitively cross JVM boundaries.
  // In this implementation, they ar strictly local, and are distinct from sections
  // just because of the namespace issues.
  protected final LocalLockPool localLocks = new LocalLockPool();
  protected final static LockPool myLocks = new LockPool(new LockObjectFactory());

  // These are for critical sections (which do not cross JVM boundaries)
  protected final LocalLockPool localSections = new LocalLockPool();
  protected final static LockPool mySections = new LockPool(new LockObjectFactory());

  /** Global flag information.  This is used only when all of ManifoldCF is run within one process. */
  protected final static Map<String,Boolean> globalFlags = new HashMap<String,Boolean>();

  /** Global resource data.  Used only when ManifoldCF is run entirely out of one process. */
  protected final static Map<String,byte[]> globalData = new HashMap<String,byte[]>();
    
  public BaseLockManager()
    throws ManifoldCFException
  {
  }

  /** Get the current shared configuration.  This configuration is available in common among all nodes,
  * and thus must not be accessed through here for the purpose of finding configuration data that is specific to any one
  * specific node.
  *@param configurationData is the globally-shared configuration information.
  */
  @Override
  public ManifoldCFConfiguration getSharedConfiguration()
    throws ManifoldCFException
  {
    // Local implementation vectors through to system property file, which is shared in this case
    return ManifoldCF.getConfiguration();
  }

  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // Keep local flag information in memory
    synchronized (globalFlags)
    {
      globalFlags.put(flagName,new Boolean(true));
    }
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // Keep flag information in memory
    synchronized (globalFlags)
    {
      globalFlags.remove(flagName);
    }
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  @Override
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // Keep flag information in memory
    synchronized (globalFlags)
    {
      return globalFlags.get(flagName) != null;
    }
  }

  /** Read data from a shared data resource.  Use this method to read any existing data, or get a null back if there is no such resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@return a byte array containing the data, or null.
  */
  @Override
  public byte[] readData(String resourceName)
    throws ManifoldCFException
  {
    // Keep resource data local
    synchronized (globalData)
    {
      return globalData.get(resourceName);
    }
  }
  
  /** Write data to a shared data resource.  Use this method to write a body of data into a shared resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@param data is the byte array containing the data.  Pass null if you want to delete the resource completely.
  */
  @Override
  public void writeData(String resourceName, byte[] data)
    throws ManifoldCFException
  {
    // Keep resource data local
    synchronized (globalData)
    {
      if (data == null)
        globalData.remove(resourceName);
      else
        globalData.put(resourceName,data);
    }
  }

  /** Wait for a time before retrying a lock.
  */
  @Override
  public final void timedWait(int time)
    throws ManifoldCFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Waiting for time "+Integer.toString(time));
    }

    try
    {
      ManifoldCF.sleep(time);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  */
  @Override
  public void enterNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    enterNonExWrite(lockKey, "lock", localLocks, myLocks);
  }
  
  @Override
  public final void enterNonExWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    enterNonExWriteNoWait(lockKey, "lock", localLocks, myLocks);
  }
  
  /** Leave a non-exclusive write lock.
  */
  @Override
  public void leaveNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    leaveNonExWrite(lockKey, "lock", localLocks, myLocks);
  }
  
  /** Enter a write locked area (i.e., block out both readers and other writers)
  * NOTE: Can't enter until all readers have left.
  */
  @Override
  public final void enterWriteLock(String lockKey)
    throws ManifoldCFException
  {
    enterWrite(lockKey, "lock", localLocks, myLocks);
  }
  
  @Override
  public void enterWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    enterWriteNoWait(lockKey, "lock", localLocks, myLocks);
  }
  
  @Override
  public void leaveWriteLock(String lockKey)
    throws ManifoldCFException
  {
    leaveWrite(lockKey, "lock", localLocks, myLocks);
  }
  
  /** Enter a read-only locked area (i.e., block ONLY if there's a writer)
  */
  @Override
  public final void enterReadLock(String lockKey)
    throws ManifoldCFException
  {
    enterRead(lockKey, "lock", localLocks, myLocks);
  }
  
  @Override
  public void enterReadLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    enterReadNoWait(lockKey, "lock", localLocks, myLocks);
  }
  
  @Override
  public final void leaveReadLock(String lockKey)
    throws ManifoldCFException
  {
    leaveRead(lockKey, "lock", localLocks, myLocks);
  }
  
  /** Enter multiple locks
  */
  @Override
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    enter(readLocks, nonExWriteLocks, writeLocks, "lock", localLocks, myLocks);
  }

  @Override
  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException, LockException
  {
    enterNoWait(readLocks, nonExWriteLocks, writeLocks, "lock", localLocks, myLocks);
  }

  /** Leave multiple locks
  */
  @Override
  public void leaveLocks(String[] readLocks, String[] writeNonExLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    leave(readLocks, writeNonExLocks, writeLocks, "lock", localLocks, myLocks);
  }
  
  @Override
  public void clearLocks()
    throws ManifoldCFException
  {
    clear("lock", localLocks, myLocks);
  }
  
  /** Enter a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void enterReadCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    enterRead(sectionKey, "critical section", localSections, mySections);
  }

  /** Leave a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void leaveReadCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    leaveRead(sectionKey, "critical section", localSections, mySections);
  }

  /** Enter a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void enterNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    enterNonExWrite(sectionKey, "critical section", localSections, mySections);
  }

  /** Leave a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void leaveNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    leaveNonExWrite(sectionKey, "critical section", localSections, mySections);
  }
  
  /** Enter a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void enterWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    enterWrite(sectionKey, "critical section", localSections, mySections);
  }
  
  /** Leave a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public final void leaveWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    leaveWrite(sectionKey, "critical section", localSections, mySections);
  }

  /** Enter multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  @Override
  public final void enterCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException
  {
    enter(readSectionKeys, nonExSectionKeys, writeSectionKeys, "critical section", localSections, mySections);
  }

  /** Leave multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  @Override
  public final void leaveCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException
  {
    leave(readSectionKeys, nonExSectionKeys, writeSectionKeys, "critical section", localSections, mySections);
  }


  // Protected methods

  protected static void enterNonExWrite(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering non-ex write "+description+" '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);

    // See if we already own a write lock for the object
    // If we do, there is no reason to change the status of the global lock we own.
    if (ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementNonExWriteLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock())
    {
      throw new ManifoldCFException("Illegal "+description+" sequence: NonExWrite "+description+" can't be within read "+description,ManifoldCFException.GENERAL_ERROR);
    }

    // We don't own a local non-ex write lock.  Get one.  The global lock will need
    // to know if we already have a a read lock.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        lo.enterNonExWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again to get a valid object
      }
    }
    ll.incrementNonExWriteLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void enterNonExWriteNoWait(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException, LockException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering non-ex write "+description+" no wait '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);


    // See if we already own a write lock for the object
    // If we do, there is no reason to change the status of the global lock we own.
    if (ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementNonExWriteLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock())
    {
      throw new ManifoldCFException("Illegal "+description+" sequence: NonExWrite "+description+" can't be within read "+description,ManifoldCFException.GENERAL_ERROR);
    }

    // We don't own a local non-ex write lock.  Get one.  The global lock will need
    // to know if we already have a a read lock.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        synchronized (lo)
        {
          lo.enterNonExWriteLockNoWait();
          break;
        }
      }
      catch (LocalLockException e)
      {

        if (Logging.lock.isDebugEnabled())
          Logging.lock.debug(" Could not non-ex write "+description+" '"+lockKey+"', lock exception");

        // Throw LockException instead
        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again to get a valid object
      }
    }
    ll.incrementNonExWriteLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void leaveNonExWrite(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Leaving non-ex write "+description+" '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);

    ll.decrementNonExWriteLocks();
    // See if we no longer have a write lock for the object.
    // If we retain the stronger exclusive lock, we still do not need to
    // change the status of the global lock.
    if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = crossLocks.getObject(lockKey);
        try
        {
          lo.leaveNonExWriteLock();
          break;
        }
        catch (InterruptedException e)
        {
          // try one more time
          try
          {
            lo.leaveNonExWriteLock();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ManifoldCFException("Interrupted",e2,ManifoldCFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again to get a valid object
        }
      }

      localLocks.releaseLocalLock(lockKey);
    }
  }

  protected static void enterWrite(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering write "+description+" '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);


    // See if we already own the write lock for the object
    if (ll.hasWriteLock())
    {
      ll.incrementWriteLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock() || ll.hasNonExWriteLock())
    {
      throw new ManifoldCFException("Illegal "+description+" sequence: Write "+description+" can't be within read "+description+" or non-ex write "+description,ManifoldCFException.GENERAL_ERROR);
    }

    // We don't own a local write lock.  Get one.  The global lock will need
    // to know if we already have a non-exclusive lock or a read lock, which we don't because
    // it's illegal.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        lo.enterWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementWriteLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void enterWriteNoWait(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException, LockException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering write "+description+" no wait '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);


    // See if we already own the write lock for the object
    if (ll.hasWriteLock())
    {
      ll.incrementWriteLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock() || ll.hasNonExWriteLock())
    {
      throw new ManifoldCFException("Illegal "+description+" sequence: Write "+description+" can't be within read "+description+" or non-ex write "+description,ManifoldCFException.GENERAL_ERROR);
    }

    // We don't own a local write lock.  Get one.  The global lock will need
    // to know if we already have a non-exclusive lock or a read lock, which we don't because
    // it's illegal.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        synchronized (lo)
        {
          lo.enterWriteLockNoWait();
          break;
        }
      }
      catch (LocalLockException e)
      {

        if (Logging.lock.isDebugEnabled())
        {
          Logging.lock.debug(" Could not write "+description+" '"+lockKey+"', lock exception");
        }

        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }

    ll.incrementWriteLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void leaveWrite(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Leaving write "+description+" '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);

    ll.decrementWriteLocks();
    if (!ll.hasWriteLock())
    {
      while (true)
      {
        LockObject lo = crossLocks.getObject(lockKey);
        try
        {
          lo.leaveWriteLock();
          break;
        }
        catch (InterruptedException e)
        {
          // try one more time
          try
          {
            lo.leaveWriteLock();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementWriteLocks();
            throw new ManifoldCFException("Interrupted",e2,ManifoldCFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementWriteLocks();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }

      localLocks.releaseLocalLock(lockKey);
    }
  }

  protected static void enterRead(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering read "+description+" '"+lockKey+"'");


    LocalLock ll = localLocks.getLocalLock(lockKey);

    // See if we already own the read lock for the object.
    // Write locks or non-ex writelocks count as well (they're stronger).
    if (ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementReadLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // We don't own a local read lock.  Get one.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        lo.enterReadLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementReadLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void enterReadNoWait(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException, LockException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Entering read "+description+" no wait '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);

    // See if we already own the read lock for the object.
    // Write locks or non-ex writelocks count as well (they're stronger).
    if (ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementReadLocks();
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained "+description+"!");
      return;
    }

    // We don't own a local read lock.  Get one.
    while (true)
    {
      LockObject lo = crossLocks.getObject(lockKey);
      try
      {
        synchronized (lo)
        {
          lo.enterReadLockNoWait();
          break;
        }
      }
      catch (LocalLockException e)
      {

        if (Logging.lock.isDebugEnabled())
          Logging.lock.debug(" Could not read "+description+" '"+lockKey+"', lock exception");

        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }

    ll.incrementReadLocks();
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug(" Successfully obtained "+description+"!");
  }

  protected static void leaveRead(String lockKey, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Leaving read "+description+" '"+lockKey+"'");

    LocalLock ll = localLocks.getLocalLock(lockKey);

    ll.decrementReadLocks();
    if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = crossLocks.getObject(lockKey);
        try
        {
          lo.leaveReadLock();
          break;
        }
        catch (InterruptedException e)
        {
          // Try one more time
          try
          {
            lo.leaveReadLock();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementReadLocks();
            throw new ManifoldCFException("Interrupted",e2,ManifoldCFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementReadLocks();
            throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }
      localLocks.releaseLocalLock(lockKey);
    }
  }

  protected static void clear(String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Clearing all "+description+"s");

    for (String keyValue : localLocks.keySet())
    {
      LocalLock ll = localLocks.getLocalLock(keyValue);
      while (ll.hasWriteLock())
        leaveWrite(keyValue, description, localLocks, crossLocks);
      while (ll.hasNonExWriteLock())
        leaveNonExWrite(keyValue, description, localLocks, crossLocks);
      while (ll.hasReadLock())
        leaveRead(keyValue, description, localLocks, crossLocks);
    }
  }

  protected static void enter(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering multiple "+description+"s:");
      int i;
      if (readLocks != null)
      {
        i = 0;
        while (i < readLocks.length)
        {
          Logging.lock.debug(" Read "+description+" '"+readLocks[i++]+"'");
        }
      }
      if (nonExWriteLocks != null)
      {
        i = 0;
        while (i < nonExWriteLocks.length)
        {
          Logging.lock.debug(" Non-ex write "+description+" '"+nonExWriteLocks[i++]+"'");
        }
      }
      if (writeLocks != null)
      {
        i = 0;
        while (i < writeLocks.length)
        {
          Logging.lock.debug(" Write "+description+" '"+writeLocks[i++]+"'");
        }
      }
    }


    // Sort the locks.  This improves the chances of making it through the locking process without
    // contention!
    LockDescription lds[] = getSortedUniqueLocks(readLocks,nonExWriteLocks,writeLocks);
    int locksProcessed = 0;
    try
    {
      while (locksProcessed < lds.length)
      {
        LockDescription ld = lds[locksProcessed];
        int lockType = ld.getType();
        String lockKey = ld.getKey();
        LocalLock ll;
        switch (lockType)
        {
        case TYPE_WRITE:
          ll = localLocks.getLocalLock(lockKey);
          // Check for illegalities
          if ((ll.hasReadLock() || ll.hasNonExWriteLock()) && !ll.hasWriteLock())
          {
            throw new ManifoldCFException("Illegal "+description+" sequence: Write "+description+" can't be within read "+description+" or non-ex write "+description,ManifoldCFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!ll.hasWriteLock())
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              try
              {
                lo.enterWriteLock();
                break;
              }
              catch (ExpiredObjectException e)
              {
                // Try again
              }
            }
          }
          ll.incrementWriteLocks();
          break;
        case TYPE_WRITENONEX:
          ll = localLocks.getLocalLock(lockKey);
          // Check for illegalities
          if (ll.hasReadLock() && !(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            throw new ManifoldCFException("Illegal "+description+" sequence: NonExWrite "+description+" can't be within read "+description,ManifoldCFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              try
              {
                lo.enterNonExWriteLock();
                break;
              }
              catch (ExpiredObjectException e)
              {
                // Try again
              }
            }
          }
          ll.incrementNonExWriteLocks();
          break;
        case TYPE_READ:
          ll = localLocks.getLocalLock(lockKey);
          if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local read lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              try
              {
                lo.enterReadLock();
                break;
              }
              catch (ExpiredObjectException e)
              {
                // Try again
              }
            }
          }
          ll.incrementReadLocks();
          break;
        }
        locksProcessed++;
      }
      // Got all; we are done!
      Logging.lock.debug(" Successfully obtained multiple "+description+"s!");
      return;
    }
    catch (Throwable ex)
    {
      // No matter what, undo the locks we've taken
      ManifoldCFException ae = null;
      int errno = 0;

      while (--locksProcessed >= 0)
      {
        LockDescription ld = lds[locksProcessed];
        int lockType = ld.getType();
        String lockKey = ld.getKey();
        try
        {
          switch (lockType)
          {
          case TYPE_READ:
            leaveRead(lockKey,description,localLocks,crossLocks);
            break;
          case TYPE_WRITENONEX:
            leaveNonExWrite(lockKey,description,localLocks,crossLocks);
            break;
          case TYPE_WRITE:
            leaveWrite(lockKey,description,localLocks,crossLocks);
            break;
          }
        }
        catch (ManifoldCFException e)
        {
          ae = e;
        }
      }

      if (ae != null)
      {
        throw ae;
      }
      if (ex instanceof ManifoldCFException)
      {
        throw (ManifoldCFException)ex;
      }
      if (ex instanceof InterruptedException)
      {
        // It's InterruptedException
        throw new ManifoldCFException("Interrupted",ex,ManifoldCFException.INTERRUPTED);
      }
      if (!(ex instanceof Error))
      {
        throw new Error("Unexpected exception",ex);
      }
      throw (Error)ex;
    }
  }

  protected static void enterNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException, LockException
  {
    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering multiple "+description+"s no wait:");
      int i;
      if (readLocks != null)
      {
        i = 0;
        while (i < readLocks.length)
        {
          Logging.lock.debug(" Read "+description+" '"+readLocks[i++]+"'");
        }
      }
      if (nonExWriteLocks != null)
      {
        i = 0;
        while (i < nonExWriteLocks.length)
        {
          Logging.lock.debug(" Non-ex write "+description+" '"+nonExWriteLocks[i++]+"'");
        }
      }
      if (writeLocks != null)
      {
        i = 0;
        while (i < writeLocks.length)
        {
          Logging.lock.debug(" Write "+description+" '"+writeLocks[i++]+"'");
        }
      }
    }


    // Sort the locks.  This improves the chances of making it through the locking process without
    // contention!
    LockDescription lds[] = getSortedUniqueLocks(readLocks,nonExWriteLocks,writeLocks);
    int locksProcessed = 0;
    try
    {
      while (locksProcessed < lds.length)
      {
        LockDescription ld = lds[locksProcessed];
        int lockType = ld.getType();
        String lockKey = ld.getKey();
        LocalLock ll;
        switch (lockType)
        {
        case TYPE_WRITE:
          ll = localLocks.getLocalLock(lockKey);
          // Check for illegalities
          if ((ll.hasReadLock() || ll.hasNonExWriteLock()) && !ll.hasWriteLock())
          {
            throw new ManifoldCFException("Illegal "+description+" sequence: Write "+description+" can't be within read "+description+" or non-ex write "+description,ManifoldCFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!ll.hasWriteLock())
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              synchronized (lo)
              {
                try
                {
                  lo.enterWriteLockNoWait();
                  break;
                }
                catch (ExpiredObjectException e)
                {
                  // Try again
                }
              }
            }
          }
          ll.incrementWriteLocks();
          break;
        case TYPE_WRITENONEX:
          ll = localLocks.getLocalLock(lockKey);
          // Check for illegalities
          if (ll.hasReadLock() && !(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            throw new ManifoldCFException("Illegal "+description+" sequence: NonExWrite "+description+" can't be within read "+description,ManifoldCFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              synchronized (lo)
              {
                try
                {
                  lo.enterNonExWriteLockNoWait();
                  break;
                }
                catch (ExpiredObjectException e)
                {
                  // Try again
                }
              }
            }
          }
          ll.incrementNonExWriteLocks();
          break;
        case TYPE_READ:
          ll = localLocks.getLocalLock(lockKey);
          if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local read lock.  Get one.
            while (true)
            {
              LockObject lo = crossLocks.getObject(lockKey);
              synchronized (lo)
              {
                try
                {
                  lo.enterReadLockNoWait();
                  break;
                }
                catch (ExpiredObjectException e)
                {
                  // Try again
                }
              }
            }
          }
          ll.incrementReadLocks();
          break;
        }
        locksProcessed++;
      }
      // Got all; we are done!
      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug(" Successfully obtained multiple "+description+"s!");
      return;
    }
    catch (Throwable ex)
    {
      // No matter what, undo the locks we've taken
      ManifoldCFException ae = null;
      int errno = 0;

      while (--locksProcessed >= 0)
      {
        LockDescription ld = lds[locksProcessed];
        int lockType = ld.getType();
        String lockKey = ld.getKey();
        try
        {
          switch (lockType)
          {
          case TYPE_READ:
            leaveRead(lockKey,description,localLocks,crossLocks);
            break;
          case TYPE_WRITENONEX:
            leaveNonExWrite(lockKey,description,localLocks,crossLocks);
            break;
          case TYPE_WRITE:
            leaveWrite(lockKey,description,localLocks,crossLocks);
            break;
          }
        }
        catch (ManifoldCFException e)
        {
          ae = e;
        }
      }

      if (ae != null)
      {
        throw ae;
      }
      if (ex instanceof ManifoldCFException)
      {
        throw (ManifoldCFException)ex;
      }
      if (ex instanceof LockException || ex instanceof LocalLockException)
      {
        Logging.lock.debug(" Couldn't get "+description+"; throwing LockException");
        // It's either LockException or LocalLockException
        throw new LockException(ex.getMessage());
      }
      if (ex instanceof InterruptedException)
      {
        throw new ManifoldCFException("Interrupted",ex,ManifoldCFException.INTERRUPTED);
      }
      if (!(ex instanceof Error))
      {
        throw new Error("Unexpected exception",ex);
      }
      throw (Error)ex;

    }

  }

  protected static void leave(String[] readLocks, String[] writeNonExLocks, String[] writeLocks, String description, LocalLockPool localLocks, LockPool crossLocks)
    throws ManifoldCFException
  {
    LockDescription[] lds = getSortedUniqueLocks(readLocks,writeNonExLocks,writeLocks);
    // Free them all... one at a time is fine
    ManifoldCFException ae = null;
    int i = lds.length;
    while (--i >= 0)
    {
      LockDescription ld = lds[i];
      String lockKey = ld.getKey();
      int lockType = ld.getType();
      try
      {
        switch (lockType)
        {
        case TYPE_READ:
          leaveRead(lockKey,description,localLocks,crossLocks);
          break;
        case TYPE_WRITENONEX:
          leaveNonExWrite(lockKey,description,localLocks,crossLocks);
          break;
        case TYPE_WRITE:
          leaveWrite(lockKey,description,localLocks,crossLocks);
          break;
        }
      }
      catch (ManifoldCFException e)
      {
        ae = e;
      }
    }

    if (ae != null)
    {
      throw ae;
    }
  }

  /** Process inbound locks into a sorted vector of most-restrictive unique locks
  */
  protected static LockDescription[] getSortedUniqueLocks(String[] readLocks, String[] writeNonExLocks,
    String[] writeLocks)
  {
    // First build a unique hash of lock descriptions
    Map<String,LockDescription> ht = new HashMap<String,LockDescription>();
    int i;
    if (readLocks != null)
    {
      i = 0;
      while (i < readLocks.length)
      {
        String key = readLocks[i++];
        LockDescription ld = ht.get(key);
        if (ld == null)
        {
          ld = new LockDescription(TYPE_READ,key);
          ht.put(key,ld);
        }
        else
          ld.set(TYPE_READ);
      }
    }
    if (writeNonExLocks != null)
    {
      i = 0;
      while (i < writeNonExLocks.length)
      {
        String key = writeNonExLocks[i++];
        LockDescription ld = ht.get(key);
        if (ld == null)
        {
          ld = new LockDescription(TYPE_WRITENONEX,key);
          ht.put(key,ld);
        }
        else
          ld.set(TYPE_WRITENONEX);
      }
    }
    if (writeLocks != null)
    {
      i = 0;
      while (i < writeLocks.length)
      {
        String key = writeLocks[i++];
        LockDescription ld = ht.get(key);
        if (ld == null)
        {
          ld = new LockDescription(TYPE_WRITE,key);
          ht.put(key,ld);
        }
        else
          ld.set(TYPE_WRITE);
      }
    }

    // Now, sort by key name
    LockDescription[] rval = new LockDescription[ht.size()];
    String[] sortarray = new String[ht.size()];
    i = 0;
    for (String key : ht.keySet())
    {
      sortarray[i++] = key;
    }
    java.util.Arrays.sort(sortarray);
    i = 0;
    for (String key : sortarray)
    {
      rval[i++] = ht.get(key);
    }
    return rval;
  }

  protected static class LockDescription
  {
    protected int lockType;
    protected String lockKey;

    public LockDescription(int lockType, String lockKey)
    {
      this.lockType = lockType;
      this.lockKey = lockKey;
    }

    public void set(int lockType)
    {
      if (lockType > this.lockType)
        this.lockType = lockType;
    }

    public int getType()
    {
      return lockType;
    }

    public String getKey()
    {
      return lockKey;
    }
  }


}
