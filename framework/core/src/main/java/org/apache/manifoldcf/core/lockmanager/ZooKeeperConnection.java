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

  private static final String CHILD_PREFIX = "child-";
  
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

  /** Create a transient node.
  */
  public void createNode(String nodePath, byte[] nodeData)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      zookeeper.create(nodePath, nodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Check whether a node exists.
  *@param nodePath is the path of the node.
  *@return the data, if the node if exists, otherwise null.
  */
  public boolean checkNodeExists(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      return (zookeeper.exists(nodePath,false) != null);
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Get node data.
  *@param nodePath is the path of the node.
  *@return the data, if the node if exists, otherwise null.
  */
  public byte[] getNodeData(String nodePath, String childName)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      String combinedPath = nodePath + "/" + childName;
      Stat s = zookeeper.exists(combinedPath,false);
      if (s == null)
        return null;
      return zookeeper.getData(combinedPath,false,s);
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Set node data.
  */
  public void setNodeData(String nodePath, String childName, byte[] data)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      String combinedPath = nodePath + "/" + childName;
      while (true)
      {
        try
        {
          zookeeper.setData(combinedPath, data, -1);
          return;
        }
        catch (KeeperException.NoNodeException e1)
        {
          // Create parent unless it already exists
          try
          {
            zookeeper.create(combinedPath, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            return;
          }
          catch (KeeperException.NoNodeException e)
          {
            try
            {
              zookeeper.create(nodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            catch (KeeperException.NodeExistsException e2)
            {
            }
          }
        }
      }
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Delete a node.
  */
  public void deleteNode(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      zookeeper.delete(nodePath,-1);
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Delete all a node's children.
  */
  public void deleteNodeChildren(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      List<String> children = zookeeper.getChildren(nodePath,false);
      for (String child : children)
      {
        zookeeper.delete(nodePath + "/" + child,-1);
      }
    }
    catch (KeeperException.NoNodeException e)
    {
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Get the relative paths of all node's children.  If the node does not exist,
  * return an empty list.
  */
  public List<String> getChildren(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      //System.out.println("Children of '"+nodePath+"':");
      List<String> children = zookeeper.getChildren(nodePath,false);
      List<String> rval = new ArrayList<String>();
      for (String child : children)
      {
        //System.out.println(" '"+child+"'");
        if (child.startsWith(CHILD_PREFIX))
          rval.add(child.substring(CHILD_PREFIX.length()));
      }
      return rval;
    }
    catch (KeeperException.NoNodeException e)
    {
      return new ArrayList<String>();
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Create a persistent child of a node.
  */
  public void createChild(String nodePath, String childName)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      //System.out.println("Creating child '"+childName+"' of nodepath '"+nodePath+"'");
      while (true)
      {
        try
        {
          zookeeper.create(nodePath + "/" + CHILD_PREFIX + childName, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
          break;
        }
        catch (KeeperException.NoNodeException e)
        {
          try
          {
            zookeeper.create(nodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
          }
          catch (KeeperException.NodeExistsException e2)
          {
          }
        }
      }
      System.out.println("...done");
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Delete the child of a node.
  */
  public void deleteChild(String nodePath, String childName)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      //System.out.println("Deleting child '"+childName+"' of nodePath '"+nodePath+"'");
      zookeeper.delete(nodePath + "/" + CHILD_PREFIX + childName, -1);
      //System.out.println("...done");
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
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
      // Assert that we want a write lock
      lockNode = createSequentialChild(lockPath,WRITE_PREFIX);
      String lockSequenceNumber = lockNode.substring(lockPath.length() + 1 + WRITE_PREFIX.length());
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
      String lockSequenceNumber = lockNode.substring(lockPath.length() + 1 + NONEXWRITE_PREFIX.length());
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
      String lockSequenceNumber = lockNode.substring(lockPath.length() + 1 + READ_PREFIX.length());
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
      if (data == null)
      {
        try
        {
          zookeeper.delete(resourcePath, -1);
        }
        catch (KeeperException.NoNodeException e)
        {
        }
      }
      else
      {
        try
        {
          zookeeper.create(resourcePath, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        catch (KeeperException.NodeExistsException e)
        {
          zookeeper.setData(resourcePath, data, -1);
        }
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
      try
      {
        zookeeper.create(flagPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }
      catch (KeeperException.NodeExistsException e)
      {
      }
    }
    catch (KeeperException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  public void clearGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    try
    {
      try
      {
        zookeeper.delete(flagPath,-1);
      }
      catch (KeeperException.NoNodeException e)
      {
      }
    }
    catch (KeeperException e)
    {
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
    // Because zookeeper is so slow, AND reports all exceptions to the log, we do the minimum.
    while (true)
    {
      try
      {
        return zookeeper.create(mainNode + "/" + childPrefix, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
      }
      catch (KeeperException.NoNodeException e)
      {
        try
        {
          zookeeper.create(mainNode, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        catch (KeeperException.NodeExistsException e2)
        {
        }
      }
    }
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
