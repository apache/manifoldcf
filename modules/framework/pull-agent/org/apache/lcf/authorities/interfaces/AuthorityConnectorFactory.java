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
package org.apache.lcf.authorities.interfaces;

import org.apache.lcf.core.interfaces.*;
import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** This class manages a pool of authority connectors.
*/
public class AuthorityConnectorFactory
{
  // Pool hash table.
  // Keyed by PoolKey; value is Pool
  protected static Map poolHash = new HashMap();

  private AuthorityConnectorFactory()
  {
  }

  /** Install connector.
  *@param className is the class name.
  */
  public static void install(IThreadContext threadContext, String className)
    throws LCFException
  {
    IAuthorityConnector connector = getConnectorNoCheck(className);
    connector.install(threadContext);
  }

  /** Uninstall connector.
  *@param className is the class name.
  */
  public static void deinstall(IThreadContext threadContext, String className)
    throws LCFException
  {
    IAuthorityConnector connector = getConnectorNoCheck(className);
    connector.deinstall(threadContext);
  }

  /** Get the default response from a connector.  Called if the connection attempt fails.
  */
  public static AuthorizationResponse getDefaultAuthorizationResponse(IThreadContext threadContext, String className, String userName)
    throws LCFException
  {
    IAuthorityConnector connector = getConnector(threadContext,className);
    if (connector == null)
      return null;
    return connector.getDefaultAuthorizationResponse(userName);
  }

  /** Output the configuration header section.
  */
  public static void outputConfigurationHeader(IThreadContext threadContext, String className, IHTTPOutput out, ConfigParams parameters, ArrayList tabsArray)
    throws LCFException, IOException
  {
    IAuthorityConnector connector = getConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationHeader(threadContext,out,parameters,tabsArray);
  }

  /** Output the configuration body section.
  */
  public static void outputConfigurationBody(IThreadContext threadContext, String className, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws LCFException, IOException
  {
    IAuthorityConnector connector = getConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationBody(threadContext,out,parameters,tabName);
  }

  /** Process configuration post data for a connector.
  */
  public static String processConfigurationPost(IThreadContext threadContext, String className, IPostParameters variableContext, ConfigParams configParams)
    throws LCFException
  {
    IAuthorityConnector connector = getConnector(threadContext, className);
    if (connector == null)
      return null;
    return connector.processConfigurationPost(threadContext,variableContext,configParams);
  }
  
  /** View connector configuration.
  */
  public static void viewConfiguration(IThreadContext threadContext, String className, IHTTPOutput out, ConfigParams configParams)
    throws LCFException, IOException
  {
    IAuthorityConnector connector = getConnector(threadContext, className);
    // We want to be able to view connections even if they have unregistered connectors.
    if (connector == null)
      return;
    connector.viewConfiguration(threadContext,out,configParams);
  }

  /** Get a repository connector instance, but do NOT check if class is installed first!
  *@param className is the class name.
  *@return the instance.
  */
  public static IAuthorityConnector getConnectorNoCheck(String className)
    throws LCFException
  {
    try
    {
      Class theClass = Class.forName(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      if (!(o instanceof IAuthorityConnector))
        throw new LCFException("Class '"+className+"' does not implement IAuthorityConnector.");
      return (IAuthorityConnector)o;
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else
        throw (LCFException)z;
    }
    catch (ClassNotFoundException e)
    {
      throw new LCFException("No class implementing IAuthorityConnector called '"+
        className+"'.",
        e);
    }
    catch (NoSuchMethodException e)
    {
      throw new LCFException("No appropriate constructor for IAuthorityConnector implementation '"+
        className+"'.  Need xxx().",
        e);
    }
    catch (SecurityException e)
    {
      throw new LCFException("Protected constructor for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new LCFException("Unavailable constructor for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new LCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new LCFException("InstantiationException for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new LCFException("ExceptionInInitializerError for IAuthorityConnector implementation '"+className+"'",
        e);
    }

  }

  /** Get a repository connector instance.
  *@param className is the class name.
  *@return the instance.
  */
  protected static IAuthorityConnector getConnector(IThreadContext threadContext, String className)
    throws LCFException
  {
    IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadContext);
    if (connMgr.isInstalled(className) == false)
      return null;

    try
    {
      Class theClass = Class.forName(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      if (!(o instanceof IAuthorityConnector))
        throw new LCFException("Class '"+className+"' does not implement IAuthorityConnector.");
      return (IAuthorityConnector)o;
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else
        throw (LCFException)z;
    }
    catch (ClassNotFoundException e)
    {
      // If we get this exception, it may mean that the authority is not registered.
      if (connMgr.isInstalled(className) == false)
        return null;

      throw new LCFException("No class implementing IAuthorityConnector called '"+
        className+"'.",
        e);
    }
    catch (NoSuchMethodException e)
    {
      throw new LCFException("No appropriate constructor for IAuthorityConnector implementation '"+
        className+"'.  Need xxx().",
        e);
    }
    catch (SecurityException e)
    {
      throw new LCFException("Protected constructor for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new LCFException("Unavailable constructor for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new LCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new LCFException("InstantiationException for IAuthorityConnector implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new LCFException("ExceptionInInitializerError for IAuthorityConnector implementation '"+className+"'",
        e);
    }

  }

  /** Get a repository connector.
  * The connector is specified by its class and its parameters.
  *@param threadContext is the current thread context.
  *@param className is the name of the class to get a connector for.
  *@param configInfo are the name/value pairs constituting configuration info
  * for this class.
  */
  public static IAuthorityConnector grab(IThreadContext threadContext,
    String className, ConfigParams configInfo, int maxPoolSize)
    throws LCFException
  {
    // System.out.println("In AuthorityConnectorManager.grab()");

    // We want to get handles off the pool and use them.  But the
    // handles we fetch have to have the right config information.

    // Use the classname and config info to build a pool key
    PoolKey pk = new PoolKey(className,configInfo);
    Pool p;
    synchronized (poolHash)
    {
      p = (Pool)poolHash.get(pk);
      if (p == null)
      {
        // Build it again, this time making a copy
        pk = new PoolKey(className,configInfo.duplicate());
        p = new Pool(pk,maxPoolSize);
        poolHash.put(pk,p);
      }
    }

    IAuthorityConnector rval = p.getConnector(threadContext);
    // System.out.println("Leaving AuthorityConnectorManager.grab()");
    return rval;
  }

  /** Release a repository connector.
  *@param connector is the connector to release.
  */
  public static void release(IAuthorityConnector connector)
    throws LCFException
  {
    if (connector == null)
      return;

    // System.out.println("Releasing an authority connector");
    // Figure out which pool this goes on, and put it there
    PoolKey pk = new PoolKey(connector.getClass().getName(),connector.getConfiguration());
    Pool p;
    synchronized (poolHash)
    {
      p = (Pool)poolHash.get(pk);
    }

    p.releaseConnector(connector);
    // System.out.println("Done releasing");
  }

  /** Idle notification for inactive authority connector handles.
  * This method polls all inactive handles.
  */
  public static void pollAllConnectors(IThreadContext threadContext)
    throws LCFException
  {
    // Go through the whole pool and notify everyone
    synchronized (poolHash)
    {
      Iterator iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = (Pool)iter.next();
        p.pollAll(threadContext);
      }
    }

  }

  /** Clean up all open authority connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  *@param threadContext is the local thread context.
  */
  public static void closeAllConnectors(IThreadContext threadContext)
    throws LCFException
  {
    // Go through the whole pool and clean it out
    synchronized (poolHash)
    {
      Iterator iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = (Pool)iter.next();
        p.releaseAll(threadContext);
      }
    }
  }

  /** This is an immutable pool key class, which describes a pool in terms of two independent keys.
  */
  public static class PoolKey
  {
    protected String className;
    protected ConfigParams configInfo;

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
  public static class Pool
  {
    protected ArrayList stack = new ArrayList();
    protected PoolKey key;
    protected int numFree;

    /** Constructor
    */
    public Pool(PoolKey pk, int maxCount)
    {
      key = pk;
      numFree = maxCount;
    }

    /** Grab a repository connector.
    * If none exists, construct it using the information in the pool key.
    *@return the connector.
    */
    public synchronized IAuthorityConnector getConnector(IThreadContext threadContext)
      throws LCFException
    {
      while (numFree == 0)
      {
        try
        {
          wait();
        }
        catch (InterruptedException e)
        {
          throw new LCFException("Interrupted",e,LCFException.INTERRUPTED);
        }
      }

      IAuthorityConnector rc;
      if (stack.size() == 0)
      {
        String className = key.getClassName();
        ConfigParams configParams = key.getParams();

        IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadContext);
        if (connMgr.isInstalled(className) == false)
          return null;

        try
        {
          Class theClass = Class.forName(className);
          Class[] argumentClasses = new Class[0];
          // Look for a constructor
          Constructor c = theClass.getConstructor(argumentClasses);
          Object[] arguments = new Object[0];
          Object o = c.newInstance(arguments);
          if (!(o instanceof IAuthorityConnector))
            throw new LCFException("Class '"+className+"' does not implement IAuthorityConnector.");
          // System.out.println("Authority connector instantiated");
          rc = (IAuthorityConnector)o;
          rc.connect(configParams);
          // System.out.println("Connect has been called for authority connector");
        }
        catch (InvocationTargetException e)
        {
          Throwable z = e.getTargetException();
          if (z instanceof Error)
            throw (Error)z;
          else
            throw (LCFException)z;
        }
        catch (ClassNotFoundException e)
        {
          // If we get this exception, it may mean that the authority is not registered.
          if (connMgr.isInstalled(className) == false)
            return null;

          throw new LCFException("No class implementing IAuthorityConnector called '"+
            className+"'.",
            e);
        }
        catch (NoSuchMethodException e)
        {
          throw new LCFException("No appropriate constructor for IAuthorityConnector implementation '"+
            className+"'.  Need xxx(ConfigParams).",
            e);
        }
        catch (SecurityException e)
        {
          throw new LCFException("Protected constructor for IAuthorityConnector implementation '"+className+"'",
            e);
        }
        catch (IllegalAccessException e)
        {
          throw new LCFException("Unavailable constructor for IAuthorityConnector implementation '"+className+"'",
            e);
        }
        catch (IllegalArgumentException e)
        {
          throw new LCFException("Shouldn't happen!!!",e);
        }
        catch (InstantiationException e)
        {
          throw new LCFException("InstantiationException for IAuthorityConnector implementation '"+className+"'",
            e);
        }
        catch (ExceptionInInitializerError e)
        {
          throw new LCFException("ExceptionInInitializerError for IAuthorityConnector implementation '"+className+"'",
            e);
        }
      }
      else
      {
        // System.out.println("Getting existing authority connector off the stack");
        rc = (IAuthorityConnector)stack.remove(stack.size()-1);
      }

      numFree--;

      rc.setThreadContext(threadContext);
      return rc;
    }

    /** Release a repository connector to the pool.
    *@param connector is the connector.
    */
    public synchronized void releaseConnector(IAuthorityConnector connector)
      throws LCFException
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
      throws LCFException
    {
      int i = 0;
      while (i < stack.size())
      {
        IAuthorityConnector rc = (IAuthorityConnector)stack.get(i++);
        // Notify
        rc.setThreadContext(threadContext);
        rc.poll();
        rc.clearThreadContext();
      }
    }

    /** Release all free connectors.
    */
    public synchronized void releaseAll(IThreadContext threadContext)
      throws LCFException
    {
      while (stack.size() > 0)
      {
        IAuthorityConnector rc = (IAuthorityConnector)stack.remove(stack.size()-1);
        // Disconnect
        rc.setThreadContext(threadContext);
        rc.disconnect();
        rc.clearThreadContext();
      }
    }

  }



}

