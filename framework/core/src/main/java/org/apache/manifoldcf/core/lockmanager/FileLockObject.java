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
import java.nio.charset.StandardCharsets;

/** One instance of this object exists for each lock on each JVM!
* This is the file-system version of the lock.
*/
public class FileLockObject extends LockObject
{
  public static final String _rcsid = "@(#)$Id: LockObject.java 988245 2010-08-23 18:39:35Z kwright $";

  private final static int STATUS_WRITELOCKED = -1;

  private File lockDirectoryName = null;
  private File lockFileName = null;
  private boolean isSync;                 // True if we need to be synchronizing across JVM's

  private final static String DOTLOCK = ".lock";
  private final static String DOTFILE = ".file";
  private final static String SLASH = "/";


  public FileLockObject(LockPool lockPool, Object lockKey, File synchDir)
  {
    super(lockPool,lockKey);
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

  private static String createFileName(Object lockKey)
  {
    return "lock-"+ManifoldCF.safeFileName(lockKey.toString());
  }

  @Override
  protected void obtainGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
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
  }

  @Override
  protected void clearGlobalWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (isSync)
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
  }

  @Override
  protected void obtainGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    // Attempt to obtain a global write lock
    if (isSync)
    {
      grabFileLock();
      try
      {
        int status = readFile();
        if (status == STATUS_WRITELOCKED || status > 0)
        {
          throw new LockException(LOCKEDANOTHERJVM);
        }
        if (status == 0)
          status = STATUS_WRITELOCKED;
        status--;
        writeFile(status);
      }
      finally
      {
        releaseFileLock();
      }
    }
  }

  @Override
  protected void clearGlobalNonExWriteLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (isSync)
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
  }

  @Override
  protected void obtainGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    // Attempt to obtain a global read lock
    if (isSync)
    {
      grabFileLock();
      try
      {
        int status = readFile();
        if (status <= STATUS_WRITELOCKED)
        {
          throw new LockException(LOCKEDANOTHERJVM);
        }
        status++;
        writeFile(status);
      }
      finally
      {
        releaseFileLock();
      }
    }
  }

  @Override
  protected void clearGlobalReadLockNoWait()
    throws ManifoldCFException, LockException, InterruptedException
  {
    if (isSync)
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
      InputStreamReader isr = new InputStreamReader(new FileInputStream(lockFileName), StandardCharsets.UTF_8);
      try
      {
        BufferedReader x = new BufferedReader(isr);
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
        isr.close();
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
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(lockFileName), StandardCharsets.UTF_8);
        try
        {
          BufferedWriter x = new BufferedWriter(osw);
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
          osw.close();
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

