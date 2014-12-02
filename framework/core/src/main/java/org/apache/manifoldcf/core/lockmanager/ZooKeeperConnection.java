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
  
  protected final String connectString;
  protected final int sessionTimeout;
  
  // Our zookeeper client
  protected ZooKeeper zookeeper = null;
  protected ZooKeeperWatcher zookeeperWatcher = null;

  // Transient state
  protected String lockNode = null;
  protected String nodePath = null;
  protected byte[] nodeData = null;

  /** Constructor. */
  public ZooKeeperConnection(String connectString, int sessionTimeout)
    throws ManifoldCFException, InterruptedException
  {
    this.connectString = connectString;
    this.sessionTimeout = sessionTimeout;
    zookeeperWatcher = new ZooKeeperWatcher();
    createSession();
  }
  
  protected void createSession()
    throws ManifoldCFException, InterruptedException
  {
    try
    {
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
    if (this.nodePath != null)
      throw new IllegalStateException("Ephemeral node '"+this.nodePath+"' already open; can't open '"+nodePath+"'.");

    while (true)
    {
      try
      {
        if (this.nodePath == null)
        {
          zookeeper.create(nodePath, nodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
          // Keep a record of the ephemeral node
          this.nodePath = nodePath;
          this.nodeData = nodeData;
        }
        break;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,false);
      }
    }
  }
  
  /** Check whether a node exists.
  *@param nodePath is the path of the node.
  *@return the data, if the node if exists, otherwise null.
  */
  public boolean checkNodeExists(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        return (zookeeper.exists(nodePath,false) != null);
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }

  /** Get node data.
  *@param nodePath is the path of the node.
  *@return the data, if the node if exists, otherwise null.
  */
  public byte[] getNodeData(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    return readData(nodePath);
  }
  
  /** Set node data.
  */
  public void setNodeData(byte[] data)
    throws ManifoldCFException, InterruptedException
  {
    if (nodePath == null)
      throw new IllegalStateException("Can't set data for a node path we did not create: '"+nodePath+"'");
    writeData(nodePath, data);
    this.nodeData = data;
  }
  
  /** Delete a node.
  */
  public void deleteNode()
    throws ManifoldCFException, InterruptedException
  {
    if (nodePath == null)
      throw new IllegalStateException("Can't delete ephemeral node that isn't registered: '"+nodePath+"'");
    while (true)
    {
      try
      {
        if (nodePath != null)
        {
          zookeeper.delete(nodePath,-1);
          nodePath = null;
          nodeData = null;
        }
        return;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,false);
      }
    }
  }
  
  /** Delete all a node's children.
  */
  public void deleteNodeChildren(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        List<String> children = zookeeper.getChildren(nodePath,false);
        for (String child : children)
        {
          zookeeper.delete(nodePath + "/" + child,-1);
        }
        break;
      }
      catch (KeeperException.NoNodeException e)
      {
        break;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }
  
  /** Get the relative paths of all node's children.  If the node does not exist,
  * return an empty list.
  */
  public List<String> getChildren(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
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
        handleKeeperException(e,true);
      }
    }
  }
  
  /** Create a persistent child of a node.
  */
  public void createChild(String nodePath, String childName)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        //System.out.println("Creating child '"+childName+"' of nodepath '"+nodePath+"'");
        createPersistentPath(nodePath + "/" + CHILD_PREFIX + childName, null);
        break;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }

  protected void createPersistentPath(String path, byte[] data)
    throws KeeperException, InterruptedException
  {
    // Loop until we've created the entire path, but initially try the whole thing for performance
    while (true)
    {
      try
      {
        zookeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Break on success
        break;
      }
      catch (KeeperException.NoNodeException e)
      {
        // Strip off last part of path, and try recursively
        int lastIndex = path.lastIndexOf("/");
        // No last path: rethrow because we don't have a clue what is going on
        if (lastIndex == -1 || lastIndex == 0)
          throw e;
        createPersistentPath(path.substring(0,lastIndex),null);
        // Retry
      }
      catch (KeeperException.NodeExistsException e)
      {
        // Path is there: someone else beat us to it
        // But we do have to set the data.
        // NOTE: This code relies on the fact that only leaf persistent nodes have data.
        if (data != null)
        {
          try
          {
            zookeeper.setData(path, data, -1);
          }
          catch (KeeperException.NoNodeException e2)
          {
            // Repeat, since we've lost our node
            continue;
          }
        }
        break;
      }
    }
  }
  
  /** Delete the child of a node.
  */
  public void deleteChild(String nodePath, String childName)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        //System.out.println("Deleting child '"+childName+"' of nodePath '"+nodePath+"'");
        zookeeper.delete(nodePath + "/" + CHILD_PREFIX + childName, -1);
        //System.out.println("...done");
        break;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
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

    while (true)
    {
      try
      {
        // Assert that we want a write lock
        if (lockNode == null)
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
            releaseLock();
            return false;
          }
        }
        // We got it!
        return true;
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
    }
  }
  
  /** Obtain a write lock, with wait.
  *@param lockPath is the lock node path.
  */
  public void obtainWriteLock(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also write lock '"+lockPath+"'");

    while (true)
    {
      try
      {
        // Assert that we want a write lock
        if (lockNode == null)
          lockNode = createSequentialChild(lockPath,WRITE_PREFIX);
        long lockSequenceNumber = new Long(lockNode.substring(lockPath.length() + 1 + WRITE_PREFIX.length())).longValue();
        //System.out.println("Trying to get write lock for '"+lockSequenceNumber+"'");
        while (true)
        {
          //System.out.println("Assessing whether we got lock for '"+lockNode+"'...");
          // See if we got it
          List<String> children = zookeeper.getChildren(lockPath,false);
          String previousLock = null;
          boolean gotLock = true;
          long highestPreviousLockIndex = -1L;
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
            long otherLockSequenceNumber = new Long(otherLock).longValue();
            //System.out.println("Saw other child sequence number "+otherLockSequenceNumber);
            if (otherLockSequenceNumber < lockSequenceNumber)
            {
              // We didn't get the lock.  But keep going because we're looking for the node right before the
              // one we just asserted.
              gotLock = false;
              if (otherLockSequenceNumber > highestPreviousLockIndex)
              {
                previousLock = x;
                highestPreviousLockIndex = otherLockSequenceNumber;
              }
            }
          }

          if (gotLock)
          {
            // We got it!
            //System.out.println("Got write lock for '"+lockSequenceNumber+"'");
            return;
          }

          // There SHOULD be a previous node immediately prior to the one we asserted.  If we didn't find one, go back around;
          // the previous lock was probably created and destroyed before we managed to get the children.
          if (previousLock != null)
          {
            //System.out.println(" Waiting on '"+previousLock+"' for write lock '"+lockSequenceNumber+"'");
            // Create an exists() watch on the previous node, and wait until we are awakened by that watch firing.
            ExistsWatcher w = new ExistsWatcher();
            Stat s = zookeeper.exists(lockPath+"/"+previousLock, w);
            if (s != null)
              w.waitForEvent();
          }
          //else
          //  System.out.println(" Retrying for write lock '"+lockSequenceNumber+"'");
        }
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
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

    while (true)
    {
      try
      {
        // Assert that we want a read lock
        if (lockNode == null)
          lockNode = createSequentialChild(lockPath,NONEXWRITE_PREFIX);
        String lockSequenceNumber = lockNode.substring(lockPath.length() + 1 + NONEXWRITE_PREFIX.length());
        // See if we got it
        List<String> children = null;
        while (true)
        {
          try
          {
            children = zookeeper.getChildren(lockPath,false);
            break;
          }
          catch (KeeperException.NoNodeException e)
          {
            // New session; back around again.
            break;
          }
          catch (KeeperException e)
          {
            handleKeeperException(e,true);
          }
        }
        if (children == null)
        {
          // Reassert ephemeral node b/c we had a session restart
          continue;
        }
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
            releaseLock();
            return false;
          }
        }
        // We got it!
        return true;
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
    }
  }

  /** Obtain a non-ex-write lock, with wait.
  *@param lockPath is the lock node path.
  */
  public void obtainNonExWriteLock(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also non-ex write lock '"+lockPath+"'");

    while (true)
    {
      try
      {
        // Assert that we want a read lock
        if (lockNode == null)
          lockNode = createSequentialChild(lockPath,NONEXWRITE_PREFIX);
        long lockSequenceNumber = new Long(lockNode.substring(lockPath.length() + 1 + NONEXWRITE_PREFIX.length())).longValue();
        while (true)
        {
          // See if we got it
          List<String> children = null;
          while (true)
          {
            try
            {
              children = zookeeper.getChildren(lockPath,false);
              break;
            }
            catch (KeeperException.NoNodeException e)
            {
              break;
            }
            catch (KeeperException e)
            {
              handleKeeperException(e,true);
            }
          }

          if (children == null)
            break;

          String previousLock = null;
          boolean gotLock = true;
          long highestPreviousLockIndex = -1L;
          for (String x : children)
          {
            String otherLock;
            if (x.startsWith(WRITE_PREFIX))
              otherLock = x.substring(WRITE_PREFIX.length());
            else if (x.startsWith(READ_PREFIX))
              otherLock = x.substring(READ_PREFIX.length());
            else
              continue;
            long otherLockSequenceNumber = new Long(otherLock).longValue();
            //System.out.println("Saw other child sequence number "+otherLockSequenceNumber);
            if (otherLockSequenceNumber < lockSequenceNumber)
            {
              // We didn't get the lock.  But keep going because we're looking for the node right before the
              // one we just asserted.
              gotLock = false;
              if (otherLockSequenceNumber > highestPreviousLockIndex)
              {
                previousLock = x;
                highestPreviousLockIndex = otherLockSequenceNumber;
              }
            }
          }
            
          if (gotLock)
          {
            // We got it!
            return;
          }

          // There SHOULD be a previous node immediately prior to the one we asserted.  If we didn't find one, go back around;
          // the previous lock was probably created and destroyed before we managed to get the children.
          if (previousLock != null)
          {
            // Create an exists() watch on the previous node, and wait until we are awakened by that watch firing.
            ExistsWatcher w = new ExistsWatcher();
            Stat s = zookeeper.exists(lockPath+"/"+previousLock, w);
            if (s != null)
              w.waitForEvent();
          }
        }
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
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

    while (true)
    {
      try
      {
        // Assert that we want a read lock
        if (lockNode == null)
          lockNode = createSequentialChild(lockPath,READ_PREFIX);
        String lockSequenceNumber = lockNode.substring(lockPath.length() + 1 + READ_PREFIX.length());
        // See if we got it
        List<String> children = null;
        while (true)
        {
          try
          {
            children = zookeeper.getChildren(lockPath,false);
            break;
          }
          catch (KeeperException.NoNodeException e)
          {
            break;
          }
          catch (KeeperException e)
          {
            handleKeeperException(e,true);
          }
        }
        if (children == null)
          continue;
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
            releaseLock();
            return false;
          }
        }
        // We got it!
        return true;
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
    }
  }
  
  /** Obtain a read lock, with wait.
  *@param lockPath is the lock node path.
  */
  public void obtainReadLock(String lockPath)
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode != null)
      throw new IllegalStateException("Already have a lock in place: '"+lockNode+"'; can't also read lock '"+lockPath+"'");

    while (true)
    {
      try
      {
        // Assert that we want a read lock
        if (lockNode == null)
          lockNode = createSequentialChild(lockPath,READ_PREFIX);
        long lockSequenceNumber = new Long(lockNode.substring(lockPath.length() + 1 + READ_PREFIX.length())).longValue();
        //System.out.println("Trying to get read lock for '"+lockSequenceNumber+"'");
        while (true)
        {
          // See if we got it
          List<String> children = null;
          while (true)
          {
            try
            {
              children = zookeeper.getChildren(lockPath,false);
              break;
            }
            catch (KeeperException.NoNodeException e)
            {
              break;
            }
            catch (KeeperException e)
            {
              handleKeeperException(e,true);
            }
          }

          // Handle new session
          if (children == null)
            break;
            
          String previousLock = null;
          boolean gotLock = true;
          long highestPreviousLockIndex = -1L;
          for (String x : children)
          {
            String otherLock;
            if (x.startsWith(WRITE_PREFIX))
              otherLock = x.substring(WRITE_PREFIX.length());
            else if (x.startsWith(NONEXWRITE_PREFIX))
              otherLock = x.substring(NONEXWRITE_PREFIX.length());
            else
              continue;
            long otherLockSequenceNumber = new Long(otherLock).longValue();
            //System.out.println("Saw other child sequence number "+otherLockSequenceNumber);
            if (otherLockSequenceNumber < lockSequenceNumber)
            {
              // We didn't get the lock.  But keep going because we're looking for the node right before the
              // one we just asserted.
              gotLock = false;
              if (otherLockSequenceNumber > highestPreviousLockIndex)
              {
                previousLock = x;
                highestPreviousLockIndex = otherLockSequenceNumber;
              }
            }
          }
            
          if (gotLock)
          {
            // We got it!
            //System.out.println("Got read lock for '"+lockSequenceNumber+"'");
            return;
          }

          // There SHOULD be a previous node immediately prior to the one we asserted.  If we didn't find one, go back around;
          // the previous lock was probably created and destroyed before we managed to get the children.
          if (previousLock != null)
          {
            //System.out.println(" Waiting on '"+previousLock+"' for read lock '"+lockSequenceNumber+"'");
            // Create an exists() watch on the previous node, and wait until we are awakened by that watch firing.
            ExistsWatcher w = new ExistsWatcher();
            Stat s = zookeeper.exists(lockPath+"/"+previousLock, w);
            if (s != null)
              w.waitForEvent();
          }
          //else
          //  System.out.println(" Retrying for read lock '"+lockSequenceNumber+"'");

        }
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
    }
  }
  
  /** Release the (saved) lock.
  */
  public void releaseLock()
    throws ManifoldCFException, InterruptedException
  {
    if (lockNode == null)
      throw new IllegalStateException("Can't release lock we don't hold");
    //System.out.println("Releasing lock '"+lockNode+"'");
    
    while (true)
    {
      if (lockNode == null)
        break;
      try
      {
        zookeeper.delete(lockNode,-1);
        lockNode = null;
        break;
      }
      catch (InterruptedException e)
      {
        lockNode = null;
        throw e;
      }
      catch (KeeperException e)
      {
        handleEphemeralNodeKeeperException(e,true);
      }
    }
  }

  public byte[] readData(String resourcePath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        return zookeeper.getData(resourcePath,false,null);
      }
      catch (KeeperException.NoNodeException e)
      {
        return null;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }
  
  public void writeData(String resourcePath, byte[] data)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
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
          break;
        }
        else
        {
          try
          {
            zookeeper.setData(resourcePath, data, -1);
          }
          catch (KeeperException.NoNodeException e)
          {
            createPersistentPath(resourcePath, data);
          }
          break;
        }
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }

  public void setGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        createPersistentPath(flagPath, null);
        break;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }

  public void clearGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
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
        break;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
    }
  }
  
  public boolean checkGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    while (true)
    {
      try
      {
        Stat s = zookeeper.exists(flagPath,false);
        return s != null;
      }
      catch (KeeperException e)
      {
        handleKeeperException(e,true);
      }
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
  
  /** Handle keeper exceptions that may involve ephemeral node creation.
  */
  protected void handleEphemeralNodeKeeperException(KeeperException e, boolean recreate)
    throws ManifoldCFException, InterruptedException
  {
    if (e instanceof KeeperException.ConnectionLossException || e instanceof KeeperException.SessionExpiredException)
    {
      while (true)
      {
        try
        {
          // Close the handle, open a new one
          lockNode = null;
          if (!recreate)
          {
            nodePath = null;
            nodeData = null;
          }
          zookeeper.close();
          createSession();
          // Lock is lost, but we can (and should) recreate the ephemeral nodes
          if (nodePath != null)
          {
            zookeeper.create(nodePath, nodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
          }
          break;
        }
        catch (KeeperException e2)
        {
          if (!(e2 instanceof KeeperException.ConnectionLossException) && !(e2 instanceof KeeperException.SessionExpiredException))
            throw new ManifoldCFException(e2.getMessage(),e2);
        }
      }
    }
    else
    {
      // If nothing we know how to deal with, throw.
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Handle keeper exceptions that don't involve ephemeral node creation.
  */
  protected void handleKeeperException(KeeperException e, boolean recreate)
    throws ManifoldCFException, InterruptedException
  {
    if (e instanceof KeeperException.ConnectionLossException)
    {
      // Retry if connection loss
      ManifoldCF.sleep(100L);
    }
    else if (e instanceof KeeperException.SessionExpiredException)
    {
      while (true)
      {
        try
        {
          // Close the handle, open a new one
          lockNode = null;
          if (!recreate)
          {
            nodePath = null;
            nodeData = null;
          }
          zookeeper.close();
          createSession();
          // Lock is lost, but we can (and should) recreate the ephemeral nodes
          if (nodePath != null)
          {
            zookeeper.create(nodePath, nodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
          }
          break;
        }
        catch (KeeperException e2)
        {
          if (!(e2 instanceof KeeperException.ConnectionLossException) && !(e2 instanceof KeeperException.SessionExpiredException))
            throw new ManifoldCFException(e2.getMessage(),e2);
        }
      }
    }
    else
    {
      // If nothing we know how to deal with, throw.
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  public static String zooKeeperSafeName(String input)
  {
    // Escape "/" characters
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++)
    {
      char x = input.charAt(i);
      if (x == '/')
        sb.append('\\').append('0');
      else if (x == '\u007f')
        sb.append('\\').append('1');
      else if (x == '\\')
        sb.append('\\').append('\\');
      else if (x >= '\u0000' && x < '\u0020')
        sb.append('\\').append(x + '\u0040');
      else if (x >= '\u0080' && x < '\u00a0')
        sb.append('\\').append(x + '\u0060' - '\u0080');
      else
        sb.append(x);
    }
    return sb.toString();
  }

  public static String zooKeeperDecodeSafeName(String input)
  {
    // Escape "/" characters
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i);
      if (x == '\\')
      {
        i++;
        if (i == input.length())
          throw new RuntimeException("Supposedly safe zookeeper name is not properly encoded!!");
        x = input.charAt(i);
        if (x == '0')
          sb.append('/');
        else if (x == '1')
          sb.append('\u007f');
        else if (x == '\\')
          sb.append('\\');
        else if (x >= '\u0040' && x < '\u0060')
          sb.append(x - '\u0040');
        else if (x >= '\u0060' && x < '\u0080')
          sb.append(x - '\u0060' + '\u0080');
        else
          throw new RuntimeException("Supposedly safe zookeeper name is not properly encoded!!");
      }
      else
        sb.append(x);
      i++;
    }
    return sb.toString();
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
    
    @Override
    public void process(WatchedEvent event)
    {
    }

  }

  /** Watcher class for exists state changes, so we get notified about deletions of lock request nodes. */
  protected static class ExistsWatcher implements Watcher
  {
    protected boolean eventTriggered = false;

    public ExistsWatcher()
    {
    }
    
    @Override
    public void process(WatchedEvent event)
    {
      synchronized (this)
      {
        eventTriggered = true;
        notifyAll();
      }
    }

    public void waitForEvent()
      throws InterruptedException
    {
      synchronized (this)
      {
        if (eventTriggered)
          return;
        wait();
      }
    }
    
  }

}
