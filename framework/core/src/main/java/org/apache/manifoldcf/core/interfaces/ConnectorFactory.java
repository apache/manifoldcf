/* $Id: OutputConnectorFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import org.apache.manifoldcf.core.system.ManifoldCF;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** This is the base factory class for all IConnector objects.
*/
public abstract class ConnectorFactory<T extends IConnector>
{
  public static final String _rcsid = "@(#)$Id: OutputConnectorFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  // Pool hash table.
  // Keyed by PoolKey; value is Pool
  protected final Map<PoolKey,Pool> poolHash = new HashMap<PoolKey,Pool>();

  protected ConnectorFactory()
  {
  }

  /** Install connector.
  *@param className is the class name.
  */
  protected void installThis(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    T connector = getThisConnectorNoCheck(className);
    connector.install(threadContext);
  }

  /** Uninstall connector.
  *@param className is the class name.
  */
  protected void deinstallThis(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    T connector = getThisConnectorNoCheck(className);
    connector.deinstall(threadContext);
  }

  /** Output the configuration header section.
  */
  protected void outputThisConfigurationHeader(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams parameters, ArrayList tabsArray)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationHeader(threadContext,out,locale,parameters,tabsArray);
  }

  /** Output the configuration body section.
  */
  protected void outputThisConfigurationBody(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationBody(threadContext,out,locale,parameters,tabName);
  }

  /** Process configuration post data for a connector.
  */
  protected String processThisConfigurationPost(IThreadContext threadContext, String className,
    IPostParameters variableContext, Locale locale, ConfigParams configParams)
    throws ManifoldCFException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return null;
    return connector.processConfigurationPost(threadContext,variableContext,locale,configParams);
  }
  
  /** View connector configuration.
  */
  protected void viewThisConfiguration(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams configParams)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    // We want to be able to view connections even if they have unregistered connectors.
    if (connector == null)
      return;
    connector.viewConfiguration(threadContext,out,locale,configParams);
  }

  /** Get a connector instance, without checking for installed connector.
  *@param className is the class name.
  *@return the instance.
  */
  protected T getThisConnectorNoCheck(String className)
    throws ManifoldCFException
  {
    try
    {
      Class theClass = ManifoldCF.findClass(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      try
      {
        return (T)o;
      }
      catch (ClassCastException e)
      {
        throw new ManifoldCFException("Class '"+className+"' does not implement IConnector.");
      }
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else if (z instanceof RuntimeException)
        throw (RuntimeException)z;
      else if (z instanceof ManifoldCFException)
        throw (ManifoldCFException)z;
      else
        throw new RuntimeException("Unknown exception type: "+z.getClass().getName()+": "+z.getMessage(),z);
    }
    catch (ClassNotFoundException e)
    {
      throw new ManifoldCFException("No connector class '"+className+"' was found.",
        e);
    }
    catch (NoSuchMethodException e)
    {
      throw new ManifoldCFException("No appropriate constructor for IConnector implementation '"+
        className+"'.  Need xxx().",
        e);
    }
    catch (SecurityException e)
    {
      throw new ManifoldCFException("Protected constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new ManifoldCFException("Unavailable constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new ManifoldCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new ManifoldCFException("InstantiationException for IConnector implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new ManifoldCFException("ExceptionInInitializerError for IConnector implementation '"+className+"'",
        e);
    }

  }

  // Protected methods
  
  /** Override this method to hook into a connector manager.
  */
  protected abstract boolean isInstalled(IThreadContext tc, String className)
    throws ManifoldCFException;
  
  /** Get a connector instance.
  *@param className is the class name.
  *@return the instance.
  */
  protected T getThisConnector(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    if (!isInstalled(threadContext,className))
      return null;

    try
    {
      Class theClass = ManifoldCF.findClass(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      try
      {
        return (T)o;
      }
      catch (ClassCastException e)
      {
        throw new ManifoldCFException("Class '"+className+"' does not implement IConnector.");
      }
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else if (z instanceof RuntimeException)
        throw (RuntimeException)z;
      else if (z instanceof ManifoldCFException)
        throw (ManifoldCFException)z;
      else
        throw new RuntimeException("Unknown exception type: "+z.getClass().getName()+": "+z.getMessage(),z);
    }
    catch (ClassNotFoundException e)
    {
      // This MAY mean that an existing connector has been uninstalled; check out this possibility!
      // We return null because that is the signal that we cannot get a connector instance for that reason.
      if (!isInstalled(threadContext,className))
        return null;

      throw new ManifoldCFException("No connector class '"+className+"' was found.",
        e);
    }
    catch (NoSuchMethodException e)
    {
      throw new ManifoldCFException("No appropriate constructor for IConnector implementation '"+
        className+"'.  Need xxx(ConfigParams).",
        e);
    }
    catch (SecurityException e)
    {
      throw new ManifoldCFException("Protected constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new ManifoldCFException("Unavailable constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new ManifoldCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new ManifoldCFException("InstantiationException for IConnector implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new ManifoldCFException("ExceptionInInitializerError for IConnector implementation '"+className+"'",
        e);
    }

  }

  /** Get multiple connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  */
  protected T[] grabThisMultiple(IThreadContext threadContext, Class<T> clazz,
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
        T connector = grabThis(threadContext,className,cp,maxPoolSize);
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
            releaseThis(rval[index]);
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
  protected T grabThis(IThreadContext threadContext,
    String className, ConfigParams configInfo, int maxPoolSize)
    throws ManifoldCFException
  {
    // We want to get handles off the pool and use them.  But the
    // handles we fetch have to have the right config information.

    // Use the classname and config info to build a pool key.  This
    // key will be discarded if we actually have to save a key persistently,
    // since we avoid copying the configInfo unnecessarily.
    PoolKey pk = new PoolKey(className,configInfo);
    Pool<T> p;
    synchronized (poolHash)
    {
      p = poolHash.get(pk);
      if (p == null)
      {
        pk = new PoolKey(className,configInfo.duplicate());
        p = new Pool<T>(pk,maxPoolSize);
        poolHash.put(pk,p);
      }
    }

    T rval = p.getConnector(threadContext);

    return rval;

  }

  /** Release multiple output connectors.
  */
  protected void releaseThisMultiple(T[] connectors)
    throws ManifoldCFException
  {
    ManifoldCFException currentException = null;
    for (int i = 0; i < connectors.length; i++)
    {
      T c = connectors[i];
      try
      {
        releaseThis(c);
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
  protected void releaseThis(T connector)
    throws ManifoldCFException
  {
    // If the connector is null, skip the release, because we never really got the connector in the first place.
    if (connector == null)
      return;

    // Figure out which pool this goes on, and put it there
    PoolKey pk = new PoolKey(connector.getClass().getName(),connector.getConfiguration());
    Pool<T> p;
    synchronized (poolHash)
    {
      p = poolHash.get(pk);
    }

    p.releaseConnector(connector);
  }

  /** Idle notification for inactive output connector handles.
  * This method polls all inactive handles.
  */
  protected void pollThisAllConnectors(IThreadContext threadContext)
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

    // System.out.println("About to check if any output connector instances have been abandoned...");
    // checkConnectors(System.currentTimeMillis());
  }

  /** Flush only those connector handles that are currently unused.
  */
  protected void flushThisUnusedConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    closeThisAllConnectors(threadContext);
  }

  /** Clean up all open output connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  *@param threadContext is the local thread context.
  */
  protected void closeThisAllConnectors(IThreadContext threadContext)
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
  public class Pool<T extends IConnector>
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

        if (!isInstalled(threadContext,className))
          return null;

        try
        {
          Class theClass = ManifoldCF.findClass(className);
          Class[] argumentClasses = new Class[0];
          // Look for a constructor
          Constructor c = theClass.getConstructor(argumentClasses);
          Object[] arguments = new Object[0];
          Object o = c.newInstance(arguments);
          T newrc;
          try
          {
            newrc = (T)o;
          }
          catch (ClassCastException e)
          {
            throw new ManifoldCFException("Class '"+className+"' does not implement IConnector.");
          }
          newrc.connect(configParams);
          stack.add(newrc);
        }
        catch (InvocationTargetException e)
        {
          Throwable z = e.getTargetException();
          if (z instanceof Error)
            throw (Error)z;
          else if (z instanceof RuntimeException)
            throw (RuntimeException)z;
          else if (z instanceof ManifoldCFException)
            throw (ManifoldCFException)z;
          else
            throw new RuntimeException("Unknown exception type: "+z.getClass().getName()+": "+z.getMessage(),z);
        }
        catch (ClassNotFoundException e)
        {
          // If we see this exception, it COULD mean that the connector was uninstalled, and we happened to get here
          // after that occurred.
          // We return null because that is the signal that we cannot get a connector instance for that reason.
          if (!isInstalled(threadContext,className))
            return null;

          throw new ManifoldCFException("No connector class '"+className+"' was found.",
            e);
        }
        catch (NoSuchMethodException e)
        {
          throw new ManifoldCFException("No appropriate constructor for IConnector implementation '"+
            className+"'.  Need xxx(ConfigParams).",
            e);
        }
        catch (SecurityException e)
        {
          throw new ManifoldCFException("Protected constructor for IConnector implementation '"+className+"'",
            e);
        }
        catch (IllegalAccessException e)
        {
          throw new ManifoldCFException("Unavailable constructor for IConnector implementation '"+className+"'",
            e);
        }
        catch (IllegalArgumentException e)
        {
          throw new ManifoldCFException("Shouldn't happen!!!",e);
        }
        catch (InstantiationException e)
        {
          throw new ManifoldCFException("InstantiationException for IConnector implementation '"+className+"'",
            e);
        }
        catch (ExceptionInInitializerError e)
        {
          throw new ManifoldCFException("ExceptionInInitializerError for IConnector implementation '"+className+"'",
            e);
        }
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
