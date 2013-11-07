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
* Note well: This class extends LockManager because zookeeper implementations of some features (e.g.
* critical sections) do not need to be overridden.
*/
public class ZookeeperLockManager extends LockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String zookeeperConnectStringParameter = "org.apache.manifoldcf.zookeeper.connectstring";
  protected final static String zookeeperSessionTimeoutParameter = "org.apache.manifoldcf.zookeeper.sessiontimeout";
  
  // Zookeeper construction values
  protected final String connectString;
  protected final int sessionTimeout;

  // One zookeeper client per thread
  protected ZooKeeper zookeeper = null;
  protected ZookeeperWatcher zookeeperWatcher = null;

  /** Constructor */
  public ZookeeperLockManager()
    throws ManifoldCFException
  {
    connectString = ManifoldCF.getStringProperty(zookeeperConnectStringParameter,null);
    if (connectString != null)
      throw new ManifoldCFException("Zookeeper lock manager requires a valid "+zookeeperConnectStringParameter+" property");
    sessionTimeout = ManifoldCF.getIntProperty(zookeeperSessionTimeoutParameter,300000);
  }
  
  public ZooKeeper getSession()
    throws ManifoldCFException
  {
    if (zookeeper == null)
    {
      try
      {
        ZookeeperWatcher zookeeperWatcher = new ZookeeperWatcher();
        zookeeper = new ZooKeeper(connectString, sessionTimeout, zookeeperWatcher);
        this.zookeeperWatcher = zookeeperWatcher;
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Zookeeper initialization error: "+e.getMessage(),e);
      }
    }
    return zookeeper;
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
    // MHL
    return null;
  }

  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // MHL
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  @Override
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    // MHL
    return false;
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
    // MHL
    return null;
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
    // MHL
  }

  /** Enter a write locked code area (i.e., block out both readers and other writers).
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, the current thread may wait until all other
  * threads have left the section.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterWriteLock(String lockKey)
    throws ManifoldCFException
  {
    // From Zookeeper recipes

    ZooKeeper zk = getSession();
    
    /*
    1. Call create( ) to create a node with pathname "guid-/write-". This is the lock node
       spoken of later in the protocol. Make sure to set both sequence and ephemeral flags.
       If a recoverable error occurs calling create() the client should call getChildren() and
       check for a node containing the guid used in the path name. This handles the case
       (noted above) of the create() succeeding on the server but the server crashing before
       returning the name of the new node.
    */

    /*
    2. Call getChildren( ) on the lock node without setting the watch flag - this is important,
       as it avoids the herd effect.
    */

    /*
    3. If there are no children with a lower sequence number than the node created in step
       1, the client has the lock and the client exits the protocol.
    */

    /*
    4. Call exists( ), with watch flag set, on the node with the pathname that has the next
       lowest sequence number.
    */

    /* 5. If exists( ) returns false, goto step 2. Otherwise, wait for a notification for the
       pathname from the previous step before going to step 2.
    */

    // MHL
  }

  /** Enter a write locked code area (i.e., block out both readers and other writers),
  * but do not wait if the lock cannot be obtained.
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, an exception (LockException) will be
  * thrown if the lock condition cannot be met immediately.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    // MHL
  }

  /** Leave a write locked code area.  Use this method to exit a write-locked section. The lockKey
  * parameter must correspond to the key used for the enter method.
  * @param lockKey is the name of the lock.
  */
  @Override
  public void leaveWriteLock(String lockKey)
    throws ManifoldCFException
  {
    // From Zookeeper recipes
    
    /*
    Delete the node we created in step 1 above.
    */

    // MHL
  }

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and will throw LockException if the lock condition cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterNonExWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    // MHL
  }

  /** Leave a non-exclusive write locked code area.  Use this method to exit a non-ex-write-locked section.
  * The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void leaveNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterReadLock(String lockKey)
    throws ManifoldCFException
  {

    // From Zookeeper recipes
    
    /*
    1. Call create( ) to create a node with pathname "guid-/read-". This is the lock node use later in the
       protocol. Make sure to set both the sequence and ephemeral flags.
       If a recoverable error occurs calling create() the client should call getChildren() and
       check for a node containing the guid used in the path name. This handles the case
       (noted above) of the create() succeeding on the server but the server crashing before
       returning the name of the new node.
    */

    /*
    2. Call getChildren( ) on the lock node without setting the watch flag - this is important, as it
       avoids the herd effect.
    */

    /*
    3. If there are no children with a pathname starting with "write-" and having a lower
       sequence number than the node created in step 1, the client has the lock and can exit the protocol.
    */

    /*
    4. Otherwise, call exists( ), with watch flag, set on the node in lock directory with pathname
       staring with "write-" having the next lowest sequence number.
    */

    /*
    5. If exists( ) returns false, goto step 2.
    */

    /*
    6. Otherwise, wait for a notification for the pathname from the previous step before going to step 2
    */

    // MHL
  }

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and will throw LockException if the required lock cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void enterReadLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    // MHL
  }

  /** Leave a read-locked code area.  Use this method to exit a read-locked section.  The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  @Override
  public void leaveReadLock(String lockKey)
    throws ManifoldCFException
  {
    // From Zookeeper recipes
    
    /*
    Delete the node we created in step 1 above.
    */

    // MHL
  }

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will wait if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  @Override
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will throw LockException if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  @Override
  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException, LockException
  {
    // MHL
  }

  /** Leave multiple locks. Use this method to leave a section started with enterLocks() or
  * enterLocksNoWait().  The parameters must correspond to those passed to the enter method.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  @Override
  public void leaveLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Clear all outstanding locks in the system.
  * This is a very dangerous method to use (obviously)...
  */
  @Override
  public void clearLocks()
    throws ManifoldCFException
  {
    // MHL
  }

  /** Watcher class for zookeeper, so we get notified about zookeeper events. */
  protected static class ZookeeperWatcher implements Watcher
  {
    public ZookeeperWatcher()
    {
      // MHL
    }
    
    public void process(WatchedEvent event)
    {
      // MHL
    }

  }
}
