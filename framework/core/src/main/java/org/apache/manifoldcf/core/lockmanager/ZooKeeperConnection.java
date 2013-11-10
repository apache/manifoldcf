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

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.util.*;
import java.io.*;

/** An instance of this class is the Zookeeper analog to a database connection.
* Basically, it bundles up the Zookeeper functionality we need in a nice package,
* which we can share between users as needed.  These connections will be pooled,
* and will be closed when the process they live in is shut down.
*/
public class ZooKeeperConnection
{
  public static final String _rcsid = "@(#)$Id$";

  private static final String READ_PREFIX = "read-";
  private static final String NONEXWRITE_PREFIX = "nonexwrite-";
  private static final String WRITE_PREFIX = "write-";

  // Our zookeeper client
  protected ZooKeeper zookeeper = null;
  protected ZooKeeperWatcher zookeeperWatcher = null;

  // Transient state
  protected String lockNode = null;

  /** Constructor. */
  public ZooKeeperConnection(String connectString, int sessionTimeout)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      zookeeperWatcher = new ZooKeeperWatcher();
      zookeeper = new ZooKeeper(connectString, sessionTimeout, zookeeperWatcher);
    }
    catch (InterruptedIOException e)
    {
      throw new InterruptedException(e.getMessage());
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Zookeeper initialization error: "+e.getMessage(),e);
    }
  }

  /** Obtain a write lock, with no wait.
  *@param lockPath is the lock node path.
  *@return true if the lock was obtained, false otherwise.
  */
  public boolean obtainWriteLockNoWait(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also write lock '"+lockPath+"'");

    try
    {
      // Assert that we want a read lock
      lockNode = createSequentialChild(lockPath,WRITE_PREFIX);
      String lockSequenceNumber = lockNode.substring(WRITE_PREFIX.length());
      // See if we got it
      List<String> children = zookeeper.getChildren(lockPath,false);
      for (String x : children)
      {
        String otherLock;
        if (x.startsWith(WRITE_PREFIX))
          otherLock = x.substring(WRITE_PREFIX.length());
        else if (x.startsWith(NONEXWRITE_PREFIX))
          otherLock = x.substring(NONEXWRITE_PREFIX.length());
        else if (x.startsWith(READ_PREFIX))
          otherLock = x.substring(READ_PREFIX.length());
        else
          continue;
        if (otherLock.compareTo(lockSequenceNumber) < 0)
        {
          // We didn't get the lock.  Clean up and exit
          zookeeper.delete(lockNode,-1);
          lockNode = null;
          return false;
        }
      }
      // We got it!
      return true;
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Obtain a non-ex-write lock, with no wait.
  *@param lockPath is the lock node path.
  *@return true if the lock was obtained, false otherwise.
  */
  public boolean obtainNonExWriteLockNoWait(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also non-ex write lock '"+lockPath+"'");

    try
    {
      // Assert that we want a read lock
      lockNode = createSequentialChild(lockPath,NONEXWRITE_PREFIX);
      String lockSequenceNumber = lockNode.substring(NONEXWRITE_PREFIX.length());
      // See if we got it
      List<String> children = zookeeper.getChildren(lockPath,false);
      for (String x : children)
      {
        String otherLock;
        if (x.startsWith(WRITE_PREFIX))
          otherLock = x.substring(WRITE_PREFIX.length());
        else if (x.startsWith(READ_PREFIX))
          otherLock = x.substring(READ_PREFIX.length());
        else
          continue;
        if (otherLock.compareTo(lockSequenceNumber) < 0)
        {
          // We didn't get the lock.  Clean up and exit
          zookeeper.delete(lockNode,-1);
          lockNode = null;
          return false;
        }
      }
      // We got it!
      return true;
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Obtain a read lock, with no wait.
  *@param lockPath is the lock node path.
  *@return true if the lock was obtained, false otherwise.
  */
  public boolean obtainReadLockNoWait(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also read lock '"+lockPath+"'");

    try
    {
      // Assert that we want a read lock
      lockNode = createSequentialChild(lockPath,READ_PREFIX);
      String lockSequenceNumber = lockNode.substring(READ_PREFIX.length());
      // See if we got it
      List<String> children = zookeeper.getChildren(lockPath,false);
      for (String x : children)
      {
        String otherLock;
        if (x.startsWith(WRITE_PREFIX))
          otherLock = x.substring(WRITE_PREFIX.length());
        else if (x.startsWith(NONEXWRITE_PREFIX))
          otherLock = x.substring(NONEXWRITE_PREFIX.length());
        else
          continue;
        if (otherLock.compareTo(lockSequenceNumber) < 0)
        {
          // We didn't get the lock.  Clean up and exit
          zookeeper.delete(lockNode,-1);
          lockNode = null;
          return false;
        }
      }
      // We got it!
      return true;
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Release the (saved) lock.
  */
  public void releaseLock()
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode == null)
      throw new IllegalStateException("Can't release lock we don't hold");
    try
    {
      zookeeper.delete(lockNode,-1);
      lockNode = null;
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  public byte[] readData(String resourcePath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      Stat s = zookeeper.exists(resourcePath,false);
      if (s == null)
        return null;
      return zookeeper.getData(resourcePath,null,s);
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  public void writeData(String resourcePath, byte[] data)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      try
      {
        List<ACL> aclList = new ArrayList<ACL>();
        // MHL
        zookeeper.create(resourcePath, data, aclList, CreateMode.PERSISTENT);
      }
      catch (KeeperException e)
      {
        if (!(e instanceof KeeperException.NodeExistsException))
          throw e;
        zookeeper.setData(resourcePath, data, -1);
      }
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  public void setGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      List<ACL> acls = new ArrayList<ACL>();
      // MHL
      zookeeper.create(flagPath, new byte[0], acls, CreateMode.PERSISTENT);
    }
    catch (KeeperException e)
    {
      if (!(e instanceof KeeperException.NodeExistsException))
        throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  public void clearGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      zookeeper.delete(flagPath,-1);
    }
    catch (KeeperException e)
    {
      if (!(e instanceof KeeperException.NoNodeException))
        return;
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  public boolean checkGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      Stat s = zookeeper.exists(flagPath,false);
      return s != null;
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Close this connection. */
  public void close()
    throws InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Should not be closing handles that have open locks!  Locknode: '"+lockNode+"'");
    zookeeper.close();
    zookeeper = null;
    zookeeperWatcher = null;
  }
  
  // Protected methods
  
  /** Create a node and a sequential child node.  Neither node has any data.
  */
  protected String createSequentialChild(String mainNode, String childPrefix)
    throws KeeperException, InterruptedException
  {
    List<ACL> aclList = new ArrayList<ACL>();
    // MHL for the right ACL.
    try
    {
      zookeeper.create(mainNode, new byte[0], aclList, CreateMode.PERSISTENT);
    }
    catch (KeeperException e)
    {
      if (!(e instanceof KeeperException.NodeExistsException))
        throw e;
    }
    
    return zookeeper.create(mainNode + "/" + childPrefix, new byte[0], aclList, CreateMode.EPHEMERAL_SEQUENTIAL);
  }

  /** Watcher class for zookeeper, so we get notified about zookeeper events. */
  protected static class ZooKeeperWatcher implements Watcher
  {
    public ZooKeeperWatcher()
    {
    }
    
    public void process(WatchedEvent event)
    {
    }

  }

}
