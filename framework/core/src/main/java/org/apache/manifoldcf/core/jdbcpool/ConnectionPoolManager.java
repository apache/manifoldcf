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
package org.apache.manifoldcf.core.jdbcpool;

import javax.naming.*;
import javax.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.LockManagerFactory;
import org.apache.manifoldcf.core.system.ManifoldCF;

/** An instance of this class manages a number of (independent) connection pools.
*/
public class ConnectionPoolManager
{
  public static final String _rcsid = "@(#)$Id$";

  protected final Map<String,ConnectionPool> poolMap;
  protected final ConnectionCloserThread connectionCloserThread;
  protected volatile AtomicBoolean shuttingDown = new AtomicBoolean(false);
  protected final boolean debug;
  
  public ConnectionPoolManager(int count, boolean debug)
    throws ManifoldCFException
  {
    this.debug = debug;
    poolMap = new HashMap<String,ConnectionPool>(count);
    connectionCloserThread = new ConnectionCloserThread();
    connectionCloserThread.start();
  }
  
  /** Look for a pool with a given key.
  */
  public synchronized ConnectionPool getPool(String poolKey)
  {
    return poolMap.get(poolKey);
  }
  
  /** Set up a pool with a given key.
  */
  public synchronized ConnectionPool addAlias(String poolKey, String driverClassName, String dbURL,
    String userName, String password, int maxSize, long expiration)
    throws ClassNotFoundException, InstantiationException, IllegalAccessException
  {
    Class.forName(driverClassName).newInstance();
    ConnectionPool cp = new ConnectionPool(dbURL,userName,password,maxSize,expiration,debug);
    poolMap.put(poolKey,cp);
    return cp;
  }
  
  public void flush()
  {
    synchronized (this)
    {
      Iterator<String> iter = poolMap.keySet().iterator();
      while (iter.hasNext())
      {
        String poolKey = iter.next();
        ConnectionPool cp = poolMap.get(poolKey);
        cp.flushPool();
      }
    }
  }
  
  public void shutdown()
  {
    //System.out.println("JDBC POOL SHUTDOWN CALLED");
    shuttingDown.set(true);
    while (connectionCloserThread.isAlive())
    {
      try
      {
        Thread.sleep(1000L);
      }
      catch (InterruptedException e)
      {
        // Ignore this until the thread is down
        connectionCloserThread.interrupt();
      }
    }
    synchronized (this)
    {
      Iterator<String> iter = poolMap.keySet().iterator();
      while (iter.hasNext())
      {
        String poolKey = iter.next();
        ConnectionPool cp = poolMap.get(poolKey);
        cp.closePool();
      }
    }
  }
  
  protected void cleanupExpiredConnections(long cleanupTime)
  {
    ConnectionPool[] connectionPools;
    synchronized (this)
    {
      connectionPools = new ConnectionPool[poolMap.size()];
      int i = 0;
      Iterator<String> iter = poolMap.keySet().iterator();
      while (iter.hasNext())
      {
        String poolKey = iter.next();
        connectionPools[i++] = poolMap.get(poolKey);
      }
    }
    for (int i = 0 ; i < connectionPools.length ; i++)
    {
      connectionPools[i].cleanupExpiredConnections(cleanupTime);
    }
  }
  
  protected class ConnectionCloserThread extends Thread
  {
    
    public ConnectionCloserThread()
    {
      super();
      setName("Connection pool reaper");
      setDaemon(true);
    }
    
    public void run()
    {
      while (true)
      {
        if (shuttingDown.get())
          break;
        cleanupExpiredConnections(System.currentTimeMillis());
        if (shuttingDown.get())
          break;
        try
        {
          Thread.sleep(5000L);
        }
        catch (InterruptedException e)
        {
          break;
        }
      }
      
    }
    
  }
  
  
}


