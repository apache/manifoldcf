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

/** This class keeps track of a zookeeper ephemeral node that is owned by the
* current process. 
*/
public class ZooKeeperEphemeralNodeObject
{
  private final ZooKeeperConnectionPool pool;
  private final String nodePath;
  
  private ZooKeeperConnection currentConnection = null;

  public ZooKeeperEphemeralNodeObject(String nodePath, ZooKeeperConnectionPool pool)
  {
    this.nodePath = nodePath;
    this.pool = pool;
  }
  
  /** Create the specified node.
  */
  public synchronized void createNode(byte[] nodeData)
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection != null)
      throw new IllegalStateException("Already have a created node for '"+nodePath+"'");
    currentConnection = pool.grab();
    try
    {
      currentConnection.createNode(nodePath,nodeData);
    }
    catch (Throwable t)
    {
      pool.release(currentConnection);
      currentConnection = null;
      if (t instanceof ManifoldCFException)
        throw (ManifoldCFException)t;
      if (t instanceof InterruptedException)
        throw (InterruptedException)t;
      if (t instanceof Error)
        throw (Error)t;
      if (t instanceof RuntimeException)
        throw (RuntimeException)t;
      throw new RuntimeException("Unexpected exception type: "+t.getClass().getName()+": "+t.getMessage(),t);
    }
  }
  
  /** Set the node's data.
  */
  public synchronized void setNodeData(byte[] nodeData)
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection == null)
      throw new IllegalStateException("Node not yet created for node path '"+nodePath+"'");
    
    currentConnection.setNodeData(nodeData);
  }
  
  /** Delete the node.
  */
  public synchronized void deleteNode()
    throws ManifoldCFException, InterruptedException
  {
    if (currentConnection == null)
      throw new IllegalStateException("Can't delete node '"+nodePath+"' that we don't own");
      // It's allowed to delete the same node multiple times
      //return;
    
    currentConnection.deleteNode();
    pool.release(currentConnection);
    currentConnection = null;
  }
  
}
