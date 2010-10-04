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
import org.apache.manifoldcf.core.system.ACF;
import java.util.*;
import java.io.*;

/** The lock manager manages locks across all threads and JVMs and cluster members.  There should be no more than ONE
* instance of this class per thread!!!  The factory should enforce this.
*/
public class LockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Synchronization directory property - local to this implementation of ILockManager */
  public static final String synchDirectoryProperty = "org.apache.manifoldcf.synchdirectory";

  // These are the lock/section types, in order of escalation
  protected final static int TYPE_READ = 1;
  protected final static int TYPE_WRITENONEX = 2;
  protected final static int TYPE_WRITE = 3;

  // These are for locks (which cross JVM boundaries)
  protected HashMap localLocks = new HashMap();
  protected static LockPool myLocks = new LockPool();

  // These are for critical sections (which do not cross JVM boundaries)
  protected HashMap localSections = new HashMap();
  protected static LockPool mySections = new LockPool();

  // This is the directory used for cross-JVM synchronization, or null if off
  protected File synchDirectory = null;

  public LockManager()
    throws ACFException
  {
    synchDirectory = ACF.getFileProperty(synchDirectoryProperty);
    if (synchDirectory != null)
    {
      if (!synchDirectory.isDirectory())
        throw new ACFException("Property "+synchDirectoryProperty+" must point to an existing, writeable directory!",ACFException.SETUP_ERROR);
    }
  }

  /** Calculate the name of a flag resource.
  *@param flagName is the name of the flag.
  *@return the name for the flag resource.
  */
  protected static String getFlagResourceName(String flagName)
  {
    return "flag-"+flagName;
  }
  
  /** Global flag information.  This is used only when all of ACF is run within one process. */
  protected static HashMap globalFlags = new HashMap();
  
  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  public void setGlobalFlag(String flagName)
    throws ACFException
  {
    if (synchDirectory == null)
    {
      // Keep local flag information in memory
      synchronized (globalFlags)
      {
        globalFlags.put(flagName,new Boolean(true));
      }
    }
    else
    {
      String resourceName = getFlagResourceName(flagName);
      String path = makeFilePath(resourceName);
      (new File(path)).mkdirs();
      File f = new File(path,ACF.safeFileName(resourceName));
      try
      {
        f.createNewFile();
      }
      catch (InterruptedIOException e)
      {
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ACFException(e.getMessage(),e);
      }
    }
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  public void clearGlobalFlag(String flagName)
    throws ACFException
  {
    if (synchDirectory == null)
    {
      // Keep flag information in memory
      synchronized (globalFlags)
      {
        globalFlags.remove(flagName);
      }
    }
    else
    {
      String resourceName = getFlagResourceName(flagName);
      File f = new File(makeFilePath(resourceName),ACF.safeFileName(resourceName));
      f.delete();
    }
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  public boolean checkGlobalFlag(String flagName)
    throws ACFException
  {
    if (synchDirectory == null)
    {
      // Keep flag information in memory
      synchronized (globalFlags)
      {
        return globalFlags.get(flagName) != null;
      }
    }
    else
    {
      String resourceName = getFlagResourceName(flagName);
      File f = new File(makeFilePath(resourceName),ACF.safeFileName(resourceName));
      return f.exists();
    }
  }

  /** Global resource data.  Used only when ACF is run entirely out of one process. */
  protected static HashMap globalData = new HashMap();
  
  /** Read data from a shared data resource.  Use this method to read any existing data, or get a null back if there is no such resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@return a byte array containing the data, or null.
  */
  public byte[] readData(String resourceName)
    throws ACFException
  {
    if (synchDirectory == null)
    {
      // Keep resource data local
      synchronized (globalData)
      {
        return (byte[])globalData.get(resourceName);
      }
    }
    else
    {
      File f = new File(makeFilePath(resourceName),ACF.safeFileName(resourceName));
      try
      {
        InputStream is = new FileInputStream(f);
        try
        {
          ByteArrayBuffer bab = new ByteArrayBuffer();
          while (true)
          {
            int x = is.read();
            if (x == -1)
              break;
            bab.add((byte)x);
          }
          return bab.toArray();
        }
        finally
        {
          is.close();
        }
      }
      catch (FileNotFoundException e)
      {
        return null;
      }
      catch (InterruptedIOException e)
      {
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ACFException("IO exception: "+e.getMessage(),e);
      }
    }
  }
  
  /** Write data to a shared data resource.  Use this method to write a body of data into a shared resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@param data is the byte array containing the data.  Pass null if you want to delete the resource completely.
  */
  public void writeData(String resourceName, byte[] data)
    throws ACFException
  {
    if (synchDirectory == null)
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
    else
    {
      try
      {
        String path = makeFilePath(resourceName);
        // Make sure the directory exists
        (new File(path)).mkdirs();
        File f = new File(path,ACF.safeFileName(resourceName));
        if (data == null)
        {
          f.delete();
          return;
        }
        FileOutputStream os = new FileOutputStream(f);
        try
        {
          os.write(data,0,data.length);
        }
        finally
        {
          os.close();
        }
      }
      catch (InterruptedIOException e)
      {
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ACFException("IO exception: "+e.getMessage(),e);
      }
    }
  }

  /** Wait for a time before retrying a lock.
  */
  public void timedWait(int time)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Waiting for time "+Integer.toString(time));
    }

    try
    {
      ACF.sleep(time);
    }
    catch (InterruptedException e)
    {
      throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
    }
  }

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  */
  public void enterNonExWriteLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering non-ex write lock '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);


    // See if we already own a write lock for the object
    // If we do, there is no reason to change the status of the global lock we own.
    if (ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementNonExWriteLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock())
    {
      throw new ACFException("Illegal lock sequence: NonExWrite lock can't be within read lock",ACFException.GENERAL_ERROR);
    }

    // We don't own a local non-ex write lock.  Get one.  The global lock will need
    // to know if we already have a a read lock.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
      try
      {
        lo.enterNonExWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again to get a valid object
      }
    }
    ll.incrementNonExWriteLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  public void enterNonExWriteLockNoWait(String lockKey)
    throws ACFException, LockException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering non-ex write lock no wait '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);


    // See if we already own a write lock for the object
    // If we do, there is no reason to change the status of the global lock we own.
    if (ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementNonExWriteLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock())
    {
      throw new ACFException("Illegal lock sequence: NonExWrite lock can't be within read lock",ACFException.GENERAL_ERROR);
    }

    // We don't own a local non-ex write lock.  Get one.  The global lock will need
    // to know if we already have a a read lock.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
        {
          Logging.lock.debug(" Could not non-ex write lock '"+lockKey+"', lock exception");
        }

        // Throw LockException instead
        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again to get a valid object
      }
    }
    ll.incrementNonExWriteLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  /** Leave a non-exclusive write lock.
  */
  public void leaveNonExWriteLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Leaving non-ex write lock '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);

    ll.decrementNonExWriteLocks();
    // See if we no longer have a write lock for the object.
    // If we retain the stronger exclusive lock, we still do not need to
    // change the status of the global lock.
    if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again to get a valid object
        }
      }

      releaseLocalLock(lockKey);
    }
  }

  /** Enter a write locked area (i.e., block out both readers and other writers)
  * NOTE: Can't enter until all readers have left.
  */
  public void enterWriteLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering write lock '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);


    // See if we already own the write lock for the object
    if (ll.hasWriteLock())
    {
      ll.incrementWriteLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock() || ll.hasNonExWriteLock())
    {
      throw new ACFException("Illegal lock sequence: Write lock can't be within read lock or non-ex write lock",ACFException.GENERAL_ERROR);
    }

    // We don't own a local write lock.  Get one.  The global lock will need
    // to know if we already have a non-exclusive lock or a read lock, which we don't because
    // it's illegal.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
      try
      {
        lo.enterWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementWriteLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  public void enterWriteLockNoWait(String lockKey)
    throws ACFException, LockException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering write lock no wait '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);


    // See if we already own the write lock for the object
    if (ll.hasWriteLock())
    {
      ll.incrementWriteLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock() || ll.hasNonExWriteLock())
    {
      throw new ACFException("Illegal lock sequence: Write lock can't be within read lock or non-ex write lock",ACFException.GENERAL_ERROR);
    }

    // We don't own a local write lock.  Get one.  The global lock will need
    // to know if we already have a non-exclusive lock or a read lock, which we don't because
    // it's illegal.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
          Logging.lock.debug(" Could not write lock '"+lockKey+"', lock exception");
        }

        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }

    ll.incrementWriteLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  public void leaveWriteLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Leaving write lock '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);

    ll.decrementWriteLocks();
    if (!ll.hasWriteLock())
    {
      while (true)
      {
        LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementWriteLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementWriteLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }

      releaseLocalLock(lockKey);
    }
  }

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer)
  */
  public void enterReadLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering read lock '"+lockKey+"'");
    }


    LocalLock ll = getLocalLock(lockKey);

    // See if we already own the read lock for the object.
    // Write locks or non-ex writelocks count as well (they're stronger).
    if (ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementReadLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // We don't own a local read lock.  Get one.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
      try
      {
        lo.enterReadLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementReadLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  public void enterReadLockNoWait(String lockKey)
    throws ACFException, LockException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering read lock no wait '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);

    // See if we already own the read lock for the object.
    // Write locks or non-ex writelocks count as well (they're stronger).
    if (ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementReadLocks();
      Logging.lock.debug(" Successfully obtained lock!");
      return;
    }

    // We don't own a local read lock.  Get one.
    while (true)
    {
      LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
        {
          Logging.lock.debug(" Could not read lock '"+lockKey+"', lock exception");
        }

        throw new LockException(e.getMessage());
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }

    ll.incrementReadLocks();
    Logging.lock.debug(" Successfully obtained lock!");
  }

  public void leaveReadLock(String lockKey)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Leaving read lock '"+lockKey+"'");
    }

    LocalLock ll = getLocalLock(lockKey);

    ll.decrementReadLocks();
    if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementReadLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementReadLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }
      releaseLocalLock(lockKey);
    }
  }

  public void clearLocks()
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Clearing all locks");
    }


    Iterator e = localLocks.keySet().iterator();
    while (e.hasNext())
    {
      String keyValue = (String)e.next();
      LocalLock ll = (LocalLock)localLocks.get(keyValue);
      while (ll.hasWriteLock())
        leaveWriteLock(keyValue);
      while (ll.hasNonExWriteLock())
        leaveNonExWriteLock(keyValue);
      while (ll.hasReadLock())
        leaveReadLock(keyValue);
    }
  }

  /** Enter multiple locks
  */
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ACFException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering multiple locks:");
      int i;
      if (readLocks != null)
      {
        i = 0;
        while (i < readLocks.length)
        {
          Logging.lock.debug(" Read lock '"+readLocks[i++]+"'");
        }
      }
      if (nonExWriteLocks != null)
      {
        i = 0;
        while (i < nonExWriteLocks.length)
        {
          Logging.lock.debug(" Non-ex write lock '"+nonExWriteLocks[i++]+"'");
        }
      }
      if (writeLocks != null)
      {
        i = 0;
        while (i < writeLocks.length)
        {
          Logging.lock.debug(" Write lock '"+writeLocks[i++]+"'");
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
          ll = getLocalLock(lockKey);
          // Check for illegalities
          if ((ll.hasReadLock() || ll.hasNonExWriteLock()) && !ll.hasWriteLock())
          {
            throw new ACFException("Illegal lock sequence: Write lock can't be within read lock or non-ex write lock",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!ll.hasWriteLock())
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
          ll = getLocalLock(lockKey);
          // Check for illegalities
          if (ll.hasReadLock() && !(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            throw new ACFException("Illegal lock sequence: NonExWrite lock can't be within read lock",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
          ll = getLocalLock(lockKey);
          if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local read lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
      Logging.lock.debug(" Successfully obtained multiple locks!");
      return;
    }
    catch (Throwable ex)
    {
      // No matter what, undo the locks we've taken
      ACFException ae = null;
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
            leaveReadLock(lockKey);
            break;
          case TYPE_WRITENONEX:
            leaveNonExWriteLock(lockKey);
            break;
          case TYPE_WRITE:
            leaveWriteLock(lockKey);
            break;
          }
        }
        catch (ACFException e)
        {
          ae = e;
        }
      }

      if (ae != null)
      {
        throw ae;
      }
      if (ex instanceof ACFException)
      {
        throw (ACFException)ex;
      }
      if (ex instanceof InterruptedException)
      {
        // It's InterruptedException
        throw new ACFException("Interrupted",ex,ACFException.INTERRUPTED);
      }
      if (!(ex instanceof Error))
      {
        throw new Error("Unexpected exception",ex);
      }
      throw (Error)ex;
    }
  }

  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ACFException, LockException
  {

    if (Logging.lock.isDebugEnabled())
    {
      Logging.lock.debug("Entering multiple locks no wait:");
      int i;
      if (readLocks != null)
      {
        i = 0;
        while (i < readLocks.length)
        {
          Logging.lock.debug(" Read lock '"+readLocks[i++]+"'");
        }
      }
      if (nonExWriteLocks != null)
      {
        i = 0;
        while (i < nonExWriteLocks.length)
        {
          Logging.lock.debug(" Non-ex write lock '"+nonExWriteLocks[i++]+"'");
        }
      }
      if (writeLocks != null)
      {
        i = 0;
        while (i < writeLocks.length)
        {
          Logging.lock.debug(" Write lock '"+writeLocks[i++]+"'");
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
          ll = getLocalLock(lockKey);
          // Check for illegalities
          if ((ll.hasReadLock() || ll.hasNonExWriteLock()) && !ll.hasWriteLock())
          {
            throw new ACFException("Illegal lock sequence: Write lock can't be within read lock or non-ex write lock",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!ll.hasWriteLock())
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
          ll = getLocalLock(lockKey);
          // Check for illegalities
          if (ll.hasReadLock() && !(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            throw new ACFException("Illegal lock sequence: NonExWrite lock can't be within read lock",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
          ll = getLocalLock(lockKey);
          if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local read lock.  Get one.
            while (true)
            {
              LockObject lo = myLocks.getObject(lockKey,synchDirectory);
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
      Logging.lock.debug(" Successfully obtained multiple locks!");
      return;
    }
    catch (Throwable ex)
    {
      // No matter what, undo the locks we've taken
      ACFException ae = null;
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
            leaveReadLock(lockKey);
            break;
          case TYPE_WRITENONEX:
            leaveNonExWriteLock(lockKey);
            break;
          case TYPE_WRITE:
            leaveWriteLock(lockKey);
            break;
          }
        }
        catch (ACFException e)
        {
          ae = e;
        }
      }

      if (ae != null)
      {
        throw ae;
      }
      if (ex instanceof ACFException)
      {
        throw (ACFException)ex;
      }
      if (ex instanceof LockException || ex instanceof LocalLockException)
      {
        Logging.lock.debug(" Couldn't get lock; throwing LockException");
        // It's either LockException or LocalLockException
        throw new LockException(ex.getMessage());
      }
      if (ex instanceof InterruptedException)
      {
        throw new ACFException("Interrupted",ex,ACFException.INTERRUPTED);
      }
      if (!(ex instanceof Error))
      {
        throw new Error("Unexpected exception",ex);
      }
      throw (Error)ex;

    }

  }

  /** Leave multiple locks
  */
  public void leaveLocks(String[] readLocks, String[] writeNonExLocks, String[] writeLocks)
    throws ACFException
  {
    LockDescription[] lds = getSortedUniqueLocks(readLocks,writeNonExLocks,writeLocks);
    // Free them all... one at a time is fine
    ACFException ae = null;
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
          leaveReadLock(lockKey);
          break;
        case TYPE_WRITENONEX:
          leaveNonExWriteLock(lockKey);
          break;
        case TYPE_WRITE:
          leaveWriteLock(lockKey);
          break;
        }
      }
      catch (ACFException e)
      {
        ae = e;
      }
    }

    if (ae != null)
    {
      throw ae;
    }
  }

  /** Enter a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterReadCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);

    // See if we already own the read lock for the object.
    // Write locks or non-ex writelocks count as well (they're stronger).
    if (ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementReadLocks();
      return;
    }

    // We don't own a local read lock.  Get one.
    while (true)
    {
      LockObject lo = mySections.getObject(sectionKey,null);
      try
      {
        lo.enterReadLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementReadLocks();
  }

  /** Leave a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveReadCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);

    ll.decrementReadLocks();
    if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = mySections.getObject(sectionKey,null);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementReadLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementReadLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }

      releaseLocalSection(sectionKey);
    }
  }

  /** Enter a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterNonExWriteCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);


    // See if we already own a write lock for the object
    // If we do, there is no reason to change the status of the global lock we own.
    if (ll.hasNonExWriteLock() || ll.hasWriteLock())
    {
      ll.incrementNonExWriteLocks();
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock())
    {
      throw new ACFException("Illegal lock sequence: NonExWrite critical section can't be within read critical section",ACFException.GENERAL_ERROR);
    }

    // We don't own a local non-ex write lock.  Get one.  The global lock will need
    // to know if we already have a a read lock.
    while (true)
    {
      LockObject lo = mySections.getObject(sectionKey,null);
      try
      {
        lo.enterNonExWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementNonExWriteLocks();
  }

  /** Leave a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveNonExWriteCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);

    ll.decrementNonExWriteLocks();
    // See if we no longer have a write lock for the object.
    // If we retain the stronger exclusive lock, we still do not need to
    // change the status of the global lock.
    if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
    {
      while (true)
      {
        LockObject lo = mySections.getObject(sectionKey,null);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementNonExWriteLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }

      releaseLocalSection(sectionKey);
    }
  }

  /** Enter a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterWriteCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);


    // See if we already own the write lock for the object
    if (ll.hasWriteLock())
    {
      ll.incrementWriteLocks();
      return;
    }

    // Check for illegalities
    if (ll.hasReadLock() || ll.hasNonExWriteLock())
    {
      throw new ACFException("Illegal lock sequence: Write lock can't be within read lock or non-ex write lock",ACFException.GENERAL_ERROR);
    }

    // We don't own a local write lock.  Get one.  The global lock will need
    // to know if we already have a non-exclusive lock or a read lock, which we don't because
    // it's illegal.
    while (true)
    {
      LockObject lo = mySections.getObject(sectionKey,null);
      try
      {
        lo.enterWriteLock();
        break;
      }
      catch (InterruptedException e)
      {
        throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
      }
      catch (ExpiredObjectException e)
      {
        // Try again
      }
    }
    ll.incrementWriteLocks();

  }

  /** Leave a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveWriteCriticalSection(String sectionKey)
    throws ACFException
  {
    LocalLock ll = getLocalSection(sectionKey);

    ll.decrementWriteLocks();
    if (!ll.hasWriteLock())
    {
      while (true)
      {
        LockObject lo = mySections.getObject(sectionKey,null);
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
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
          catch (InterruptedException e2)
          {
            ll.incrementWriteLocks();
            throw new ACFException("Interrupted",e2,ACFException.INTERRUPTED);
          }
          catch (ExpiredObjectException e2)
          {
            ll.incrementWriteLocks();
            throw new ACFException("Interrupted",e,ACFException.INTERRUPTED);
          }
        }
        catch (ExpiredObjectException e)
        {
          // Try again
        }
      }

      releaseLocalSection(sectionKey);
    }
  }

  /** Enter multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void enterCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ACFException
  {
    // Sort the locks.  This improves the chances of making it through the locking process without
    // contention!
    LockDescription lds[] = getSortedUniqueLocks(readSectionKeys,nonExSectionKeys,writeSectionKeys);
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
          ll = getLocalSection(lockKey);
          // Check for illegalities
          if ((ll.hasReadLock() || ll.hasNonExWriteLock()) && !ll.hasWriteLock())
          {
            throw new ACFException("Illegal lock sequence: Write critical section can't be within read critical section or non-ex write critical section",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!ll.hasWriteLock())
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = mySections.getObject(lockKey,null);
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
          ll = getLocalSection(lockKey);
          // Check for illegalities
          if (ll.hasReadLock() && !(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            throw new ACFException("Illegal lock sequence: NonExWrite critical section can't be within read critical section",ACFException.GENERAL_ERROR);
          }

          // See if we already own the write lock for the object
          if (!(ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local write lock.  Get one.
            while (true)
            {
              LockObject lo = mySections.getObject(lockKey,null);
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
          ll = getLocalSection(lockKey);
          if (!(ll.hasReadLock() || ll.hasNonExWriteLock() || ll.hasWriteLock()))
          {
            // We don't own a local read lock.  Get one.
            while (true)
            {
              LockObject lo = mySections.getObject(lockKey,null);
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
      return;
    }
    catch (Throwable ex)
    {
      // No matter what, undo the locks we've taken
      ACFException ae = null;
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
            leaveReadCriticalSection(lockKey);
            break;
          case TYPE_WRITENONEX:
            leaveNonExWriteCriticalSection(lockKey);
            break;
          case TYPE_WRITE:
            leaveWriteCriticalSection(lockKey);
            break;
          }
        }
        catch (ACFException e)
        {
          ae = e;
        }
      }

      if (ae != null)
      {
        throw ae;
      }
      if (ex instanceof ACFException)
      {
        throw (ACFException)ex;
      }
      if (ex instanceof InterruptedException)
      {
        // It's InterruptedException
        throw new ACFException("Interrupted",ex,ACFException.INTERRUPTED);
      }
      if (!(ex instanceof Error))
      {
        throw new Error("Unexpected exception",ex);
      }
      throw (Error)ex;
    }
  }

  /** Leave multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void leaveCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ACFException
  {
    LockDescription[] lds = getSortedUniqueLocks(readSectionKeys,nonExSectionKeys,writeSectionKeys);
    // Free them all... one at a time is fine
    ACFException ae = null;
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
          leaveReadCriticalSection(lockKey);
          break;
        case TYPE_WRITENONEX:
          leaveNonExWriteCriticalSection(lockKey);
          break;
        case TYPE_WRITE:
          leaveWriteCriticalSection(lockKey);
          break;
        }
      }
      catch (ACFException e)
      {
        ae = e;
      }
    }

    if (ae != null)
    {
      throw ae;
    }
  }

  protected LocalLock getLocalLock(String lockKey)
  {
    LocalLock ll = (LocalLock)localLocks.get(lockKey);
    if (ll == null)
    {
      ll = new LocalLock();
      localLocks.put(lockKey,ll);
    }
    return ll;
  }

  protected void releaseLocalLock(String lockKey)
  {
    localLocks.remove(lockKey);
  }

  protected LocalLock getLocalSection(String sectionKey)
  {
    LocalLock ll = (LocalLock)localSections.get(sectionKey);
    if (ll == null)
    {
      ll = new LocalLock();
      localSections.put(sectionKey,ll);
    }
    return ll;
  }

  protected void releaseLocalSection(String sectionKey)
  {
    localSections.remove(sectionKey);
  }

  /** Process inbound locks into a sorted vector of most-restrictive unique locks
  */
  protected LockDescription[] getSortedUniqueLocks(String[] readLocks, String[] writeNonExLocks,
    String[] writeLocks)
  {
    // First build a unique hash of lock descriptions
    HashMap ht = new HashMap();
    int i;
    if (readLocks != null)
    {
      i = 0;
      while (i < readLocks.length)
      {
        String key = readLocks[i++];
        LockDescription ld = (LockDescription)ht.get(key);
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
        LockDescription ld = (LockDescription)ht.get(key);
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
        LockDescription ld = (LockDescription)ht.get(key);
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
    Iterator iter = ht.keySet().iterator();
    while (iter.hasNext())
    {
      String key = (String)iter.next();
      sortarray[i++] = key;
    }
    java.util.Arrays.sort(sortarray);
    i = 0;
    while (i < sortarray.length)
    {
      rval[i] = (LockDescription)ht.get(sortarray[i]);
      i++;
    }
    return rval;
  }

  /** Create a file path given a key name.
  *@param key is the key name.
  *@return the file path.
  */
  protected String makeFilePath(String key)
  {
    int hashcode = key.hashCode();
    int outerDirNumber = (hashcode & (1023));
    int innerDirNumber = ((hashcode >> 10) & (1023));
    String fullDir = synchDirectory.toString();
    if (fullDir.length() == 0 || !fullDir.endsWith("/"))
      fullDir = fullDir + "/";
    fullDir = fullDir + Integer.toString(outerDirNumber)+"/"+Integer.toString(innerDirNumber);
    return fullDir;
  }

  protected class LockDescription
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

  protected class LocalLock
  {
    private int readCount = 0;
    private int writeCount = 0;
    private int nonExWriteCount = 0;

    public LocalLock()
    {
    }

    public boolean hasWriteLock()
    {
      return (writeCount > 0);
    }

    public boolean hasReadLock()
    {
      return (readCount > 0);
    }

    public boolean hasNonExWriteLock()
    {
      return (nonExWriteCount > 0);
    }

    public void incrementReadLocks()
    {
      readCount++;
    }

    public void incrementNonExWriteLocks()
    {
      nonExWriteCount++;
    }

    public void incrementWriteLocks()
    {
      writeCount++;
    }

    public void decrementReadLocks()
    {
      readCount--;
    }

    public void decrementNonExWriteLocks()
    {
      nonExWriteCount--;
    }

    public void decrementWriteLocks()
    {
      writeCount--;
    }
  }
  
  protected static final int BASE_SIZE = 128;
  
  protected static class ByteArrayBuffer
  {
    protected byte[] buffer;
    protected int length;
    
    public ByteArrayBuffer()
    {
      buffer = new byte[BASE_SIZE];
      length = 0;
    }
    
    public void add(byte b)
    {
      if (length == buffer.length)
      {
        byte[] oldbuffer = buffer;
        buffer = new byte[length * 2];
        System.arraycopy(oldbuffer,0,buffer,0,length);
      }
      buffer[length++] = b;
    }
    
    public byte[] toArray()
    {
      byte[] rval = new byte[length];
      System.arraycopy(buffer,0,rval,0,length);
      return rval;
    }
  }

}
