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
    throws ManifoldCFException
  {
    try
    {
      zookeeperWatcher = new ZooKeeperWatcher();
      zookeeper = new ZooKeeper(connectString, sessionTimeout, zookeeperWatcher);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Zookeeper initialization error: "+e.getMessage(),e);
    }
  }

  // MHL
  
  /** Close this connection. */
  public void close()
    throws ManifoldCFException
  {
    try
    {
      zookeeper.close();
      zookeeper = null;
      zookeeperWatcher = null;
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
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
