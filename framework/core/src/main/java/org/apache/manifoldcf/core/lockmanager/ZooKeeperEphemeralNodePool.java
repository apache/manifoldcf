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

/** This class represents a pool of ZooKeeperEphemeralNodeObject objects.
* The key for this pool is the node path.
*/
public class ZooKeeperEphemeralNodePool
{
  protected final ZooKeeperConnectionPool pool;
  protected final Map<String,ZooKeeperEphemeralNodeObject> nodes = new HashMap<String,ZooKeeperEphemeralNodeObject>();
  
  public ZooKeeperEphemeralNodePool(ZooKeeperConnectionPool pool)
  {
    this.pool = pool;
  }
  
  public void createNode(String nodePath, byte[] nodeData)
    throws ManifoldCFException, InterruptedException
  {
    getObject(nodePath).createNode(nodeData);
  }
  
  public void setNodeData(String nodePath, byte[] nodeData)
    throws ManifoldCFException, InterruptedException
  {
    getObject(nodePath).setNodeData(nodeData);
  }

  public void deleteNode(String nodePath)
    throws ManifoldCFException, InterruptedException
  {
    synchronized (this)
    {
      ZooKeeperEphemeralNodeObject rval = nodes.get(nodePath);
      if (rval != null)
      {
        rval.deleteNode();
        nodes.remove(nodePath);
      }
    }
  }
  
  public void deleteAll()
    throws ManifoldCFException, InterruptedException
  {
    synchronized (this)
    {
      while (nodes.size() > 0)
      {
        Iterator<String> nodePathIter = nodes.keySet().iterator();
        String nodePath = nodePathIter.next();
        deleteNode(nodePath);
      }
    }
  }

  protected synchronized ZooKeeperEphemeralNodeObject getObject(String nodePath)
  {
    ZooKeeperEphemeralNodeObject rval = nodes.get(nodePath);
    if (rval != null)
      return rval;
    rval = new ZooKeeperEphemeralNodeObject(nodePath,pool);
    nodes.put(nodePath,rval);
    return rval;
  }
  
}
