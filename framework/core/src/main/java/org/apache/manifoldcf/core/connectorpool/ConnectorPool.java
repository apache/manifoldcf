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
package org.apache.manifoldcf.core.connectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** This is the base factory class for all ConnectorPool objects.
*/
public abstract class ConnectorPool<T extends IConnector>
{
  public static final String _rcsid = "@(#)$Id$";

  // Pool hash table.
  // Keyed by PoolKey; value is Pool
  protected final Map<PoolKey,Pool> poolHash = new HashMap<PoolKey,Pool>();

  protected ConnectorPool()
  {
  }

  // Protected methods
  
  /** Override this method to instantiate a connector.
  */
  protected abstract T createConnectorInstance(IThreadContext tc, String className)
    throws ManifoldCFException;
  
  /** Get multiple connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  */
  public T[] grabMultiple(IThreadContext threadContext, Class<T> clazz,
    String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
    throws ManifoldCFException
  {
    T[] rval = (T[])Array.newInstance(clazz,classNames.length);
    Map<String,Integer> orderMap = new HashMap<String,Integer>();
    for (int i = 0; i < orderingKeys.length; i++)
    {
      if (orderMap.get(orderingKeys[i]) != null)
        throw new ManifoldCFException("Found duplicate order key");
      orderMap.put(orderingKeys[i],new Integer(i));
    }
    java.util.Arrays.sort(orderingKeys);
    for (int i = 0; i < orderingKeys.length; i++)
    {
      String orderingKey = orderingKeys[i];
      int index = orderMap.get(orderingKey).intValue();
      String className = classNames[index];
      ConfigParams cp = configInfos[index];
      int maxPoolSize = maxPoolSizes[index];
      try
      {
        T connector = grab(threadContext,className,cp,maxPoolSize);
        rval[index] = connector;
      }
      catch (Throwable e)
      {
        while (i > 0)
        {
          i--;
          orderingKey = orderingKeys[i];
          index = orderMap.get(orderingKey).intValue();
          try
          {
            release(rval[index]);
          }
          catch (ManifoldCFException e2)
          {
          }
        }
        if (e instanceof ManifoldCFException)
          throw (ManifoldCFException)e;
        else if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof Error)
          throw (Error)e;
        else
          throw new RuntimeException("Unexpected exception type: "+e.getClass().getName()+": "+e.getMessage(),e);
      }
    }
    return rval;
  }

  /** Get a connector.
  * The connector is specified by its class and its parameters.
  *@param threadContext is the current thread context.
  *@param className is the name of the class to get a connector for.
  *@param configInfo are the name/value pairs constituting configuration info
  * for this class.
  */
  public T grab(IThreadContext threadContext,
    String className, ConfigParams configInfo, int maxPoolSize)
    throws ManifoldCFException
  {
    // We want to get handles off the pool and use them.  But the
    // handles we fetch have to have the right config information.

    // Use the classname and config info to build a pool key.  This
    // key will be discarded if we actually have to save a key persistently,
    // since we avoid copying the configInfo unnecessarily.
    PoolKey pk = new PoolKey(className,configInfo);
    Pool p;
    synchronized (poolHash)
    {
      p = poolHash.get(pk);
      if (p == null)
      {
        pk = new PoolKey(className,configInfo.duplicate());
        p = new Pool(pk,maxPoolSize);
        poolHash.put(pk,p);
      }
    }

    T rval = p.getConnector(threadContext);

    return rval;

  }

  /** Release multiple output connectors.
  */
  public void releaseMultiple(T[] connectors)
    throws ManifoldCFException
  {
    ManifoldCFException currentException = null;
    for (int i = 0; i < connectors.length; i++)
    {
      T c = connectors[i];
      try
      {
        release(c);
      }
      catch (ManifoldCFException e)
      {
        if (currentException == null)
          currentException = e;
      }
    }
    if (currentException != null)
      throw currentException;
  }

  /** Release an output connector.
  *@param connector is the connector to release.
  */
  public void release(T connector)
    throws ManifoldCFException
  {
    // If the connector is null, skip the release, because we never really got the connector in the first place.
    if (connector == null)
      return;

    // Figure out which pool this goes on, and put it there
    PoolKey pk = new PoolKey(connector.getClass().getName(),connector.getConfiguration());
    Pool p;
    synchronized (poolHash)
    {
      p = poolHash.get(pk);
    }

    p.releaseConnector(connector);
  }

  /** Idle notification for inactive output connector handles.
  * This method polls all inactive handles.
  */
  protected void pollAllConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // System.out.println("Pool stats:");

    // Go through the whole pool and notify everyone
    synchronized (poolHash)
    {
      Iterator<Pool> iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = iter.next();
        p.pollAll(threadContext);
      }
    }

  }

  /** Flush only those connector handles that are currently unused.
  */
  protected void flushUnusedConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    closeAllConnectors(threadContext);
  }

  /** Clean up all open output connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  *@param threadContext is the local thread context.
  */
  protected void closeAllConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (poolHash)
    {
      Iterator<Pool> iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = iter.next();
        p.releaseAll(threadContext);
      }
    }
  }

  /** This is an immutable pool key class, which describes a pool in terms of two independent keys.
  */
  public static class PoolKey
  {
    protected final String className;
    protected final ConfigParams configInfo;

    /** Constructor.
    */
    public PoolKey(String className, Map configInfo)
    {
      this.className = className;
      this.configInfo = new ConfigParams(configInfo);
    }

    public PoolKey(String className, ConfigParams configInfo)
    {
      this.className = className;
      this.configInfo = configInfo;
    }

    /** Get the class name.
    *@return the class name.
    */
    public String getClassName()
    {
      return className;
    }

    /** Get the config info.
    *@return the params
    */
    public ConfigParams getParams()
    {
      return configInfo;
    }

    /** Hash code.
    */
    public int hashCode()
    {
      return className.hashCode() + configInfo.hashCode();
    }

    /** Equals operator.
    */
    public boolean equals(Object o)
    {
      if (!(o instanceof PoolKey))
        return false;

      PoolKey pk = (PoolKey)o;
      return pk.className.equals(className) && pk.configInfo.equals(configInfo);
    }

  }

  /** This class represents a value in the pool hash, which corresponds to a given key.
  */
  public class Pool
  {
    protected final List<T> stack = new ArrayList<T>();
    protected final PoolKey key;
    protected int numFree;

    /** Constructor
    */
    public Pool(PoolKey pk, int maxCount)
    {
      key = pk;
      numFree = maxCount;
    }

    /** Grab a connector.
    * If none exists, construct it using the information in the pool key.
    *@return the connector, or null if no connector could be connected.
    */
    public synchronized T getConnector(IThreadContext threadContext)
      throws ManifoldCFException
    {
      while (numFree == 0)
      {
        try
        {
          wait();
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }

      if (stack.size() == 0)
      {
        String className = key.getClassName();
        ConfigParams configParams = key.getParams();

        T newrc = createConnectorInstance(threadContext,className);
        newrc.connect(configParams);
        stack.add(newrc);
      }
      
      // Since thread context set can fail, do that before we remove it from the pool.
      T rc = stack.get(stack.size()-1);
      rc.setThreadContext(threadContext);
      stack.remove(stack.size()-1);
      numFree--;
      
      return rc;
    }

    /** Release a connector to the pool.
    *@param connector is the connector.
    */
    public synchronized void releaseConnector(T connector)
      throws ManifoldCFException
    {
      if (connector == null)
        return;

      // Make sure connector knows it's released
      connector.clearThreadContext();
      // Append
      stack.add(connector);
      numFree++;
      notifyAll();
    }

    /** Notify all free connectors.
    */
    public synchronized void pollAll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      int i = 0;
      while (i < stack.size())
      {
        T rc = stack.get(i++);
        // Notify
        rc.setThreadContext(threadContext);
        try
        {
          rc.poll();
        }
        finally
        {
          rc.clearThreadContext();
        }
      }
    }

    /** Release all free connectors.
    */
    public synchronized void releaseAll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      while (stack.size() > 0)
      {
        // Disconnect
        T rc = stack.get(stack.size()-1);
        rc.setThreadContext(threadContext);
        try
        {
          rc.disconnect();
          stack.remove(stack.size()-1);
        }
        finally
        {
          rc.clearThreadContext();
        }
      }
    }

  }

}
