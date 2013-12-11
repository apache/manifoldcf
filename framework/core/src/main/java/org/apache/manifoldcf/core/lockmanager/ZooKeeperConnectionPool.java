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
import java.util.*;
import java.io.*;

/** Pool of ZooKeeper connections.
* ZooKeeper connections are not trivial to set up and each one carries a cost.  Plus,
* if we want to shut them all down on exit we need them all in one place.
*/
public class ZooKeeperConnectionPool
{
  public static final String _rcsid = "@(#)$Id$";

  protected final String connectString;
  protected final int sessionTimeout;
  
  protected final List<ZooKeeperConnection> openConnectionList = new ArrayList<ZooKeeperConnection>();
  
  public ZooKeeperConnectionPool(String connectString, int sessionTimeout)
  {
    this.connectString = connectString;
    this.sessionTimeout = sessionTimeout;
  }
  
  public synchronized ZooKeeperConnection grab()
    throws ManifoldCFException, InterruptedException
  {
    if (openConnectionList.size() == 0)
      openConnectionList.add(new ZooKeeperConnection(connectString, sessionTimeout));
    return openConnectionList.remove(openConnectionList.size()-1);
  }

  public synchronized void release(ZooKeeperConnection connection)
  {
    openConnectionList.add(connection);
  }
  
  public synchronized void closeAll()
    throws InterruptedException
  {
    for (ZooKeeperConnection c : openConnectionList)
    {
      c.close();
    }
  }
}
