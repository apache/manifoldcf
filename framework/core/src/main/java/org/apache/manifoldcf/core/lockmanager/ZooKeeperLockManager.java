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

import org.apache.zookeeper.*;

import java.util.*;
import java.io.*;

/** The lock manager manages locks across all threads and JVMs and cluster members, using Zookeeper.
* There should be no more than ONE instance of this class per thread!!!  The factory should enforce this.
*/
public class ZooKeeperLockManager extends BaseLockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String zookeeperConnectStringParameter = "org.apache.manifoldcf.zookeeper.connectstring";
  protected final static String zookeeperSessionTimeoutParameter = "org.apache.manifoldcf.zookeeper.sessiontimeout";

  private final static String CONFIGURATION_PATH = "/org.apache.manifoldcf.configuration";
  private final static String RESOURCE_PATH_PREFIX = "/org.apache.manifoldcf.resources-";
  private final static String FLAG_PATH_PREFIX = "/org.apache.manifoldcf.flags-";
    
  // ZooKeeper connection pool
  protected static Integer connectionPoolLock = new Integer(0);
  protected static ZooKeeperConnectionPool pool = null;
  protected static Integer zookeeperPoolLocker = new Integer(0);
  protected static LockPool myZooKeeperLocks = null;

  /** Constructor */
  public ZooKeeperLockManager()
    throws ManifoldCFException
  {
    synchronized (connectionPoolLock)
    {
      if (pool == null)
      {
        // Initialize the ZooKeeper connection pool
        String connectString = ManifoldCF.getStringProperty(zookeeperConnectStringParameter,null);
        if (connectString == null)
          throw new ManifoldCFException("Zookeeper lock manager requires a valid "+zookeeperConnectStringParameter+" property");
        int sessionTimeout = ManifoldCF.getIntProperty(zookeeperSessionTimeoutParameter,300000);
        ManifoldCF.addShutdownHook(new ZooKeeperShutdown());
        pool = new ZooKeeperConnectionPool(connectString, sessionTimeout);
      }
    }
    synchronized (zookeeperPoolLocker)
    {
      if (myZooKeeperLocks == null)
      {
        myZooKeeperLocks = new LockPool(new ZooKeeperLockObjectFactory(pool));
      }
    }
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
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        // Read as a byte array, then parse
        byte[] configurationData = connection.readData(CONFIGURATION_PATH);
        if (configurationData != null)
          return new ManifoldCFConfiguration(new ByteArrayInputStream(configurationData));
        else
          return new ManifoldCFConfiguration();
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Write shared configuration.  Caller closes the input stream.
  */
  public void setSharedConfiguration(InputStream configurationInputStream)
    throws ManifoldCFException
  {
    try
    {
      // Read to a byte array
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      byte[] data = new byte[65536];

      while (true)
      {
        int nRead = configurationInputStream.read(data, 0, data.length);
        if (nRead == -1)
          break;
        buffer.write(data, 0, nRead);
      }
      buffer.flush();

      byte[] toWrite = buffer.toByteArray();
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.writeData(CONFIGURATION_PATH, toWrite);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.setGlobalFlag(FLAG_PATH_PREFIX + flagName);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.clearGlobalFlag(FLAG_PATH_PREFIX + flagName);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        return connection.checkGlobalFlag(FLAG_PATH_PREFIX + flagName);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        return connection.readData(RESOURCE_PATH_PREFIX + resourceName);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.writeData(RESOURCE_PATH_PREFIX + resourceName, data);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  // Main method - for loading Zookeeper data
  
  public static void main(String[] argv)
  {
    if (argv.length != 1)
    {
      System.err.println("Usage: ZooKeeperLockManager <shared_configuration_file>");
      System.exit(1);
    }
    
    File file = new File(argv[0]);
    
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);

      try
      {
        FileInputStream fis = new FileInputStream(file);
        try
        {
          new ZooKeeperLockManager().setSharedConfiguration(fis);
        }
        finally
        {
          fis.close();
        }
      }
      finally
      {
        ManifoldCF.cleanUpEnvironment(tc);
      }
    }
    catch (Throwable e)
    {
      e.printStackTrace(System.err);
      System.exit(-1);
    }
  }
  
  // Protected methods and classes
  
  /** Override this method to change the nature of global locks.
  */
  @Override
  protected LockPool getGlobalLockPool()
  {
    return myZooKeeperLocks;
  }

  /** Shutdown the connection pool.
  */
  protected static void shutdownPool()
    throws ManifoldCFException
  {
    synchronized (connectionPoolLock)
    {
      if (pool != null)
      {
        try
        {
          pool.closeAll();
          pool = null;
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
    }
  }
  
  protected static class ZooKeeperShutdown implements IShutdownHook
  {
    public ZooKeeperShutdown()
    {
    }
    
    /** Do the requisite cleanup.
    */
    @Override
    public void doCleanup()
      throws ManifoldCFException
    {
      shutdownPool();
    }

  }
  
}
