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
	
  // One zookeeper client per thread
  protected ZooKeeper zookeeper = null;
  protected ZooKeeperWatcher zookeeperWatcher = null;

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

  public void obtainGlobalWriteLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
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
  
  public void clearGlobalWriteLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
    /*
      Delete the node we created in step 1 above.
    */
    // MHL
  }

  public void obtainGlobalNonExWriteLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
    // MHL
  }

  public void clearGlobalNonExWriteLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
    // MHL
  }
  
  public void obtainGlobalReadLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
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
  
 public void clearGlobalReadLock(String lockPath)
    throws ManifoldCFException, LockException, InterruptedException
  {
    /*
      Delete the node we created in step 1 above.
    */
    // MHL
  }

  public byte[] readData(String resourcePath)
    throws ManifoldCFException, InterruptedException
  {
    // MHL
    return null;
  }
  
  public void writeData(String resourcePath, byte[] data)
    throws ManifoldCFException, InterruptedException
  {
    // MHL
  }

  public void setGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    // MHL
  }

  public void clearGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    // MHL
  }
  
  public boolean checkGlobalFlag(String flagPath)
    throws ManifoldCFException, InterruptedException
  {
    // MHL
    return false;
  }
  
  /** Close this connection. */
  public void close()
    throws InterruptedException
  {
    zookeeper.close();
    zookeeper = null;
    zookeeperWatcher = null;
  }
  
  /** Watcher class for zookeeper, so we get notified about zookeeper events. */
  protected static class ZooKeeperWatcher implements Watcher
  {
    public ZooKeeperWatcher()
    {
      // MHL
    }
    
    public void process(WatchedEvent event)
    {
      // MHL
    }

  }

}
