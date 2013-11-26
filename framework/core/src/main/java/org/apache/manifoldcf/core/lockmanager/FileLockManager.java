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
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.util.*;
import java.io.*;

/** This is the file-based lock manager.
*/
public class FileLockManager extends BaseLockManager
{
  public static final String _rcsid = "@(#)$Id$";

  /** Synchronization directory property - local to this implementation of ILockManager */
  public static final String synchDirectoryProperty = "org.apache.manifoldcf.synchdirectory";

  // These are for file-based locks (which cross JVM boundaries)
  protected final static Integer lockPoolInitialization = new Integer(0);
  protected static LockPool myFileLocks = null;

  // This is the directory used for cross-JVM synchronization, or null if off
  protected File synchDirectory = null;

  public FileLockManager(File synchDirectory)
    throws ManifoldCFException
  {
    this.synchDirectory = synchDirectory;
    if (synchDirectory == null)
      throw new ManifoldCFException("Synch directory cannot be null");
    if (!synchDirectory.isDirectory())
      throw new ManifoldCFException("Synch directory must point to an existing, writeable directory!",ManifoldCFException.SETUP_ERROR);
    synchronized(lockPoolInitialization)
    {
      if (myFileLocks == null)
      {
        myFileLocks = new LockPool(new FileLockObjectFactory(synchDirectory));
      }
    }
  }
  
  public FileLockManager()
    throws ManifoldCFException
  {
    this(getSynchDirectoryProperty());
  }

  /** Get the synch directory property. */
  public static File getSynchDirectoryProperty()
    throws ManifoldCFException
  {
    return ManifoldCF.getFileProperty(synchDirectoryProperty);
  }
  
  /** Calculate the name of a flag resource.
  *@param flagName is the name of the flag.
  *@return the name for the flag resource.
  */
  protected static String getFlagResourceName(String flagName)
  {
    return "flag-"+flagName;
  }
    
  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    String resourceName = getFlagResourceName(flagName);
    String path = makeFilePath(resourceName);
    (new File(path)).mkdirs();
    File f = new File(path,ManifoldCF.safeFileName(resourceName));
    try
    {
      f.createNewFile();
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    String resourceName = getFlagResourceName(flagName);
    File f = new File(makeFilePath(resourceName),ManifoldCF.safeFileName(resourceName));
    f.delete();
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  @Override
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    String resourceName = getFlagResourceName(flagName);
    File f = new File(makeFilePath(resourceName),ManifoldCF.safeFileName(resourceName));
    return f.exists();
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
    File f = new File(makeFilePath(resourceName),ManifoldCF.safeFileName(resourceName));
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
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
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
    try
    {
      String path = makeFilePath(resourceName);
      // Make sure the directory exists
      (new File(path)).mkdirs();
      File f = new File(path,ManifoldCF.safeFileName(resourceName));
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
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Override this method to change the nature of global locks.
  */
  @Override
  protected LockPool getGlobalLockPool()
  {
    return myFileLocks;
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
