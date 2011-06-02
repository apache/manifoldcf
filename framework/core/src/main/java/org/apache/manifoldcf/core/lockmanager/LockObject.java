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

/** One instance of this object exists for each lock on each JVM!
*/
public class LockObject
{
  public static final String _rcsid = "@(#)$Id: LockObject.java 988245 2010-08-23 18:39:35Z kwright $";

  private final static int STATUS_WRITELOCKED = -1;

  private LockPool lockPool;
  private Object lockKey;
  private File lockDirectoryName = null;
  private File lockFileName = null;
  private boolean obtainedWrite = false;  // Set to true if this object already owns the permission to exclusively write
  private int obtainedRead = 0;           // Set to a count if this object already owns the permission to read
  private int obtainedNonExWrite = 0;     // Set to a count if this object already owns the permission to non-exclusively write
  private boolean isSync;                 // True if we need to be synchronizing across JVM's

  private final static String DOTLOCK = ".lock";
  private final static String DOTFILE = ".file";
  private final static String SLASH = "/";
  private static final String LOCKEDANOTHERTHREAD = "Locked by another thread in this JVM";
  private static final String LOCKEDANOTHERJVM = "Locked by another JVM";


  public LockObject(LockPool lockPool, Object lockKey, File synchDir)
  {
    this.lockPool = lockPool;
    this.lockKey = lockKey;
    this.isSync = (synchDir != null);
    if (isSync)
    {
      // Hash the filename
      int hashcode = lockKey.hashCode();
      int outerDirNumber = (hashcode & (1023));
      int innerDirNumber = ((hashcode >> 10) & (1023));
      String fullDir = synchDir.toString();
      if (fullDir.length() == 0 || !fullDir.endsWith(SLASH))
        fullDir = fullDir + SLASH;
      fullDir = fullDir + Integer.toString(outerDirNumber)+SLASH+Integer.toString(innerDirNumber);
      (new File(fullDir)).mkdirs();
      String filename = createFileName(lockKey);

      lockDirectoryName = new File(fullDir,filename+DOTLOCK);
      lockFileName = new File(fullDir,filename+DOTFILE);
    }
  }

  public synchronized void makeInvalid()
  {
    this.lockPool = null;
  }

  private static String createFileName(Object lockKey)
  {
    return "lock-"+ManifoldCF.safeFileName(lockKey.toString());
  }

  /** This method WILL NOT BE CALLED UNLESS we are actually committing a write lock for the
  * first time for a given thread.
  */
  public void enterWriteLock()
    throws InterruptedException, ExpiredObjectException
  {
    // if (lockFileName != null)
    //  System.out.println("Entering write lock for resource "+lockFileName.toString());
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
              // if (lockFileName != null)
              //      System.out.println("Leaving write lock for resource "+lockFileName.toString());
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
    throws LockException, LocalLockException, InterruptedException, ExpiredObjectException
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
    if (isSync)
    {
      grabFileLock();
      try
      {
        int status = readFile();
        if (status != 0)
        {
          throw new LockException(LOCKEDANOTHERJVM);
        }
        writeFile(STATUS_WRITELOCKED);
      }
      finally
      {
        releaseFileLock();
      }
    }
    obtainedWrite = true;
  }

  public void leaveWriteLock()
    throws InterruptedException, ExpiredObjectException
  {
    // if (lockFileName != null)
    //      System.out.println("Releasing write lock for resource "+lockFileName.toString());
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
          if (isSync)
          {
            try
            {
              grabFileLock();
              try
              {
                writeFile(0);
              }
              finally
              {
                releaseFileLock();
              }
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
          }

          // Lock is free, so release this object from the pool
          lockPool.releaseObject(lockKey,this);

          notifyAll();
          // if (lockFileName != null)
          //      System.out.println("Write lock released for resource "+lockFileName.toString());
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

  public void enterNonExWriteLock()
    throws InterruptedException, ExpiredObjectException
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
    throws LockException, LocalLockException, InterruptedException, ExpiredObjectException
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

    // Attempt to obtain a global write lock
    if (isSync)
    {
      grabFileLock();
      try
      {
        int status = readFile();
        if (status >= STATUS_WRITELOCKED)
        {
          throw new LockException(LOCKEDANOTHERJVM);
        }
        if (status == 0)
          status = STATUS_WRITELOCKED;
        writeFile(status-1);
      }
      finally
      {
        releaseFileLock();
      }
    }
    obtainedNonExWrite++;
  }

  public void leaveNonExWriteLock()
    throws InterruptedException, ExpiredObjectException
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

          if (isSync)
          {
            try
            {
              grabFileLock();
              try
              {
                int status = readFile();
                if (status >= STATUS_WRITELOCKED)
                  throw new RuntimeException("JVM error: File lock is not in expected state for object "+this.toString());
                status++;
                if (status == STATUS_WRITELOCKED)
                  status = 0;
                writeFile(status);
              }
              finally
              {
                releaseFileLock();
              }
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

  public void enterReadLock()
    throws InterruptedException, ExpiredObjectException
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
    throws LockException, LocalLockException, InterruptedException, ExpiredObjectException
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

    // Attempt to obtain a global read lock
    if (isSync)
    {
      grabFileLock();
      try
      {
        int status = readFile();
        // System.out.println(" Read "+Integer.toString(status));
        if (status <= STATUS_WRITELOCKED)
        {
          throw new LockException(LOCKEDANOTHERJVM);
        }
        status++;
        writeFile(status);
        // System.out.println(" Wrote "+Integer.toString(status));
      }
      finally
      {
        releaseFileLock();
      }
      // System.out.println(" Exiting");
    }

    obtainedRead = 1;
  }

  public void leaveReadLock()
    throws InterruptedException, ExpiredObjectException
  {
    // if (lockFileName != null)
    //      System.out.println("Leaving read lock for resource "+lockFileName.toString()+" "+toString());
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
            // if (lockFileName != null)
            //      System.out.println("Freed read lock for resource "+lockFileName.toString()+" (obtainedRead > 0)");
            return;
          }
          if (isSync)
          {
            try
            {
              grabFileLock();
              try
              {
                int status = readFile();
                // System.out.println(" Read status = "+Integer.toString(status));
                if (status == 0)
                  throw new RuntimeException("JVM error: File lock is not in expected state for object "+this.toString());
                status--;
                writeFile(status);
                // System.out.println(" Wrote status = "+Integer.toString(status));
              }
              finally
              {
                releaseFileLock();
              }
              // System.out.println(" Done");
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
          }

          // Lock is free, so release this object from the pool
          lockPool.releaseObject(lockKey,this);

          notifyAll();
          // if (lockFileName != null)
          //      System.out.println("Freed read lock for resource "+lockFileName.toString());
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

  private final static String FILELOCKED = "File locked";

  private synchronized void grabFileLock()
    throws LockException, InterruptedException
  {
    while (true)
    {
      // Try to create the lock file
      try
      {
        if (lockDirectoryName.createNewFile() == false)
          throw new LockException(FILELOCKED);
        break;
      }
      catch (InterruptedIOException e)
      {
        throw new InterruptedException("Interrupted IO: "+e.getMessage());
      }
      catch (IOException e)
      {
        // Log this if possible
        try
        {
          Logging.lock.warn("Attempt to set file lock '"+lockDirectoryName.toString()+"' failed: "+e.getMessage(),e);
        }
        catch (Throwable e2)
        {
          e.printStackTrace();
        }
        // Winnt sometimes throws an exception when you can't do the lock
        ManifoldCF.sleep(100);
        continue;
      }
    }
  }

  private synchronized void releaseFileLock()
    throws InterruptedException
  {
    Throwable ie = null;
    while (true)
    {
      try
      {
        if (lockDirectoryName.delete())
          break;
        try
        {
          Logging.lock.fatal("Failure deleting file lock '"+lockDirectoryName.toString()+"'");
        }
        catch (Throwable e2)
        {
          System.out.println("Failure deleting file lock '"+lockDirectoryName.toString()+"'");
        }
        // Fail hard
        System.exit(-100);
      }
      catch (Error e)
      {
        // An error - must try again to delete
        // Attempting to log this to the log may not work due to disk being full, but try anyway.
        String message = "Error deleting file lock '"+lockDirectoryName.toString()+"': "+e.getMessage();
        try
        {
          Logging.lock.error(message,e);
        }
        catch (Throwable e2)
        {
          // Ok, we failed, send it to standard out
          System.out.println(message);
          e.printStackTrace();
        }
        ie = e;
        ManifoldCF.sleep(100);
        continue;
      }
      catch (RuntimeException e)
      {
        // A runtime exception - try again to delete
        // Attempting to log this to the log may not work due to disk being full, but try anyway.
        String message = "Error deleting file lock '"+lockDirectoryName.toString()+"': "+e.getMessage();
        try
        {
          Logging.lock.error(message,e);
        }
        catch (Throwable e2)
        {
          // Ok, we failed, send it to standard out
          System.out.println(message);
          e.printStackTrace();
        }
        ie = e;
        ManifoldCF.sleep(100);
        continue;
      }
    }

    // Succeeded finally - but we need to rethrow any exceptions we got
    if (ie != null)
    {
      if (ie instanceof InterruptedException)
        throw (InterruptedException)ie;
      if (ie instanceof Error)
        throw (Error)ie;
      if (ie instanceof RuntimeException)
        throw (RuntimeException)ie;
    }

  }

  private synchronized int readFile()
    throws InterruptedException
  {
    try
    {
      FileReader fr = new FileReader(lockFileName);
      try
      {
        BufferedReader x = new BufferedReader(fr);
        try
        {
          StringBuilder sb = new StringBuilder();
          while (true)
          {
            int rval = x.read();
            if (rval == -1)
              break;
            sb.append((char)rval);
          }
          try
          {
            return Integer.parseInt(sb.toString());
          }
          catch (NumberFormatException e)
          {
            // We should never be in a situation where we can't parse a number we have supposedly written.
            // But, print a stack trace and throw IOException, so we recover.
            throw new IOException("Lock number read was not valid: "+e.getMessage());
          }
        }
        finally
        {
          x.close();
        }
      }
      catch (InterruptedIOException e)
      {
        throw new InterruptedException("Interrupted IO: "+e.getMessage());
      }
      catch (IOException e)
      {
        String message = "Could not read from lock file: '"+lockFileName.toString()+"'";
        try
        {
          Logging.lock.error(message,e);
        }
        catch (Throwable e2)
        {
          System.out.println(message);
          e.printStackTrace();
        }
        // Don't fail hard or there is no way to recover
        throw e;
      }
      finally
      {
        fr.close();
      }
    }
    catch (InterruptedIOException e)
    {
      throw new InterruptedException("Interrupted IO: "+e.getMessage());
    }
    catch (IOException e)
    {
      return 0;
    }

  }

  private synchronized void writeFile(int value)
    throws InterruptedException
  {
    try
    {
      if (value == 0)
      {
        if (lockFileName.delete() == false)
          throw new IOException("Could not delete file '"+lockFileName.toString()+"'");
      }
      else
      {
        FileWriter fw = new FileWriter(lockFileName);
        try
        {
          BufferedWriter x = new BufferedWriter(fw);
          try
          {
            x.write(Integer.toString(value));
          }
          finally
          {
            x.close();
          }
        }
        finally
        {
          fw.close();
        }
      }
    }
    catch (Error e)
    {
      // Couldn't write for some reason!  Write to BOTH stdout and the log, since we
      // can't be sure we will succeed at the latter.
      String message = "Couldn't write to lock file; hard error occurred.  Shutting down process; locks may be left dangling.  You must cleanup before restarting.";
      try
      {
        Logging.lock.error(message,e);
      }
      catch (Throwable e2)
      {
        System.out.println(message);
        e.printStackTrace();
      }
      System.exit(-100);
    }
    catch (RuntimeException e)
    {
      // Couldn't write for some reason!  Write to BOTH stdout and the log, since we
      // can't be sure we will succeed at the latter.
      String message = "Couldn't write to lock file; JVM error.  Shutting down process; locks may be left dangling.  You must cleanup before restarting.";
      try
      {
        Logging.lock.error(message,e);
      }
      catch (Throwable e2)
      {
        System.out.println(message);
        e.printStackTrace();
      }
      System.exit(-100);
    }
    catch (InterruptedIOException e)
    {
      throw new InterruptedException("Interrupted IO: "+e.getMessage());
    }
    catch (IOException e)
    {
      // Couldn't write for some reason!  Write to BOTH stdout and the log, since we
      // can't be sure we will succeed at the latter.
      String message = "Couldn't write to lock file; disk may be full.  Shutting down process; locks may be left dangling.  You must cleanup before restarting.";
      try
      {
        Logging.lock.error(message,e);
      }
      catch (Throwable e2)
      {
        System.out.println(message);
        e.printStackTrace();
      }
      System.exit(-100);
      // Hard failure is called for
      // throw new Error("Lock management system failure",e);
    }
  }


}

