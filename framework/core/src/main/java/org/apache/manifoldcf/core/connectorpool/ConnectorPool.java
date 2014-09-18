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

  // How global connector allocation works:
  // (1) There is a lock-manager "service" associated with this connector pool.  This allows us to clean
  // up after local pools that have died without being released.  There's one anonymous service instance per local pool,
  // and thus one service instance per JVM.
  // (2) Each local pool knows how many connector instances of each type (keyed by connection name) there
  // are.
  // (3) Each local pool/connector instance type has a local authorization count.  This is the amount it's
  // allowed to actually keep.  If the pool has more connectors of a type than the local authorization count permits,
  // then every connector release operation will destroy the released connector until the local authorization count
  // is met.
  // (4) Each local pool/connector instance type needs a global variable describing how many CURRENT instances
  // the local pool has allocated.  This is a transient value which should automatically go to zero if the service becomes inactive.
  // The lock manager has primitives now that allow data to be set this way.  We will use the connection name as the
  // "data type" name - only in the local pool will we pay any attention to config info and class name, and flush those handles
  // that get returned that have the wrong info attached.

  /** Target calc lock prefix */
  protected final static String targetCalcLockPrefix = "_POOLTARGET_";
  
  /** Service type prefix */
  protected final String serviceTypePrefix;

  /** Pool hash table. Keyed by connection name; value is Pool */
  protected final Map<String,Pool> poolHash = new HashMap<String,Pool>();

  /** Random number */
  protected final static Random randomNumberGenerator = new Random();
  
  protected ConnectorPool(String serviceTypePrefix)
  {
    this.serviceTypePrefix = serviceTypePrefix;
  }

  // Protected methods
  
  /** Override this method to hook into a connector manager.
  */
  protected abstract boolean isInstalled(IThreadContext tc, String className)
    throws ManifoldCFException;
  
  /** Override this method to check if a connection name is still valid.
  */
  protected abstract boolean isConnectionNameValid(IThreadContext tc, String connectionName)
    throws ManifoldCFException;
  
  /** Get a connector instance.
  *@param className is the class name.
  *@return the instance.
  */
  protected T createConnectorInstance(IThreadContext threadContext, String className)
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
      // Equivalent to the connector not being installed
      return null;
      //throw new ManifoldCFException("No connector class '"+className+"' was found.",e);
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
  public T[] grabMultiple(IThreadContext threadContext, Class<T> clazz,
    String[] orderingKeys, String[] connectionNames,
    String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
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
      String connectionName = connectionNames[index];
      String className = classNames[index];
      ConfigParams cp = configInfos[index];
      int maxPoolSize = maxPoolSizes[index];
      try
      {
        T connector = grab(threadContext,connectionName,className,cp,maxPoolSize);
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
            release(threadContext,connectionName,rval[index]);
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
  * The connector is specified by its connection name, class, and parameters.  If the
  * class and parameters corresponding to a connection name change, then this code
  * will destroy any old connector instance that does not correspond, and create a new
  * one using the new class and parameters.
  *@param threadContext is the current thread context.
  *@param connectionName is the name of the connection.  This functions as a pool key.
  *@param className is the name of the class to get a connector for.
  *@param configInfo are the name/value pairs constituting configuration info
  * for this class.
  */
  public T grab(IThreadContext threadContext, String connectionName,
    String className, ConfigParams configInfo, int maxPoolSize)
    throws ManifoldCFException
  {
    // We want to get handles off the pool and use them.  But the
    // handles we fetch have to have the right config information.

    // Loop until we successfully get a connector.  This is necessary because the
    // pool may vanish because it has been closed.
    while (true)
    {
      Pool p;
      synchronized (poolHash)
      {
        p = poolHash.get(connectionName);
        if (p == null)
        {
          p = new Pool(threadContext, maxPoolSize, connectionName);
          poolHash.put(connectionName,p);
          // Do an initial poll right away, so we don't have to wait 5 seconds to 
          // get a connector instance unless they're already all in use.
          p.pollAll(threadContext);
        }
        else
        {
          p.updateMaximumPoolSize(threadContext, maxPoolSize);
        }
      }

      T rval = p.getConnector(threadContext,className,configInfo);
      if (rval != null)
        return rval;
    }

  }

  /** Release multiple output connectors.
  */
  public void releaseMultiple(IThreadContext threadContext, String[] connectionNames, T[] connectors)
    throws ManifoldCFException
  {
    ManifoldCFException currentException = null;
    for (int i = 0; i < connectors.length; i++)
    {
      String connectionName = connectionNames[i];
      T c = connectors[i];
      try
      {
        release(threadContext,connectionName,c);
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
  *@param connectionName is the connection name.
  *@param connector is the connector to release.
  */
  public void release(IThreadContext threadContext, String connectionName, T connector)
    throws ManifoldCFException
  {
    // If the connector is null, skip the release, because we never really got the connector in the first place.
    if (connector == null)
      return;

    // Figure out which pool this goes on, and put it there
    Pool p;
    synchronized (poolHash)
    {
      p = poolHash.get(connectionName);
    }

    if (p != null)
      p.releaseConnector(threadContext, connector);
    else
    {
      // Destroy the connector instance, since the pool is gone and that means we're shutting down
      connector.setThreadContext(threadContext);
      try
      {
        connector.disconnect();
      }
      finally
      {
        connector.clearThreadContext();
      }
    }
  }

  /** Idle notification for inactive output connector handles.
  * This method polls all inactive handles.
  */
  public void pollAllConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // System.out.println("Pool stats:");

    // Go through the whole pool and notify everyone
    synchronized (poolHash)
    {
      Iterator<String> iter = poolHash.keySet().iterator();
      while (iter.hasNext())
      {
        String connectionName = iter.next();
        Pool p = poolHash.get(connectionName);
        if (isConnectionNameValid(threadContext,connectionName))
          p.pollAll(threadContext);
        else
        {
          p.releaseAll(threadContext);
          iter.remove();
        }
      }
    }

  }

  /** Flush only those connector handles that are currently unused.
  */
  public void flushUnusedConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (poolHash)
    {
      Iterator<Pool> iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = iter.next();
        p.flushUnused(threadContext);
      }
    }
  }

  /** Clean up all open output connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  *@param threadContext is the local thread context.
  */
  public void closeAllConnectors(IThreadContext threadContext)
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
        iter.remove();
      }
    }
  }

  // Protected methods and classes
  
  protected String buildServiceTypeName(String connectionName)
  {
    return serviceTypePrefix + connectionName;
  }
  
  protected String buildTargetCalcLockName(String connectionName)
  {
    return targetCalcLockPrefix + serviceTypePrefix + connectionName;
  }
  
  /** This class represents a value in the pool hash, which corresponds to a given key.
  */
  protected class Pool
  {
    /** Whether this pool is alive */
    protected boolean isAlive = true;
    /** The global maximum for this pool */
    protected int globalMax;
    /** Service type name */
    protected final String serviceTypeName;
    /** The (anonymous) service name */
    protected final String serviceName;
    /** The target calculation lock name */
    protected final String targetCalcLockName;
    /** Place where we keep unused connector instances */
    protected final List<T> stack = new ArrayList<T>();
    /** The number of local instances we can currently pass out to requesting threads.  Initially zero until pool is apportioned */
    protected int numFree = 0;
    /** The number of instances we are allowed to hand out locally, at this time */
    protected int localMax = 0;
    /** The number of instances that are actually connected and in use, as of the last poll */
    protected int localInUse = 0;
    
    /** Constructor
    */
    public Pool(IThreadContext threadContext, int maxCount, String connectionName)
      throws ManifoldCFException
    {
      this.globalMax = maxCount;
      this.targetCalcLockName = buildTargetCalcLockName(connectionName);
      this.serviceTypeName = buildServiceTypeName(connectionName);
      // Now, register and activate service anonymously, and record the service name we get.
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      this.serviceName = lockManager.registerServiceBeginServiceActivity(serviceTypeName, null, null);
    }

    /** Update the maximum pool size.
    *@param maxPoolSize is the new global maximum pool size.
    */
    public synchronized void updateMaximumPoolSize(IThreadContext threadContext, int maxPoolSize)
      throws ManifoldCFException
    {
      // This updates the maximum global size that the pool uses.
      globalMax = maxPoolSize;
      // We do nothing else at this time; we rely on polling to reapportion the pool.
    }

    
    /** Grab a connector.
    * If none exists, construct it using the information in the pool key.
    *@return the connector, or null if no connector could be connected.
    */
    public synchronized T getConnector(IThreadContext threadContext, String className, ConfigParams configParams)
      throws ManifoldCFException
    {
      // numFree represents the number of available connector instances that have not been given out at this moment.
      // So it's the max minus the pool count minus the number in use.
      while (isAlive && numFree <= 0)
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
      if (!isAlive)
        return null;
      
      // We decrement numFree when we hand out a connector instance; we increment numFree when we
      // throw away a connector instance from the pool.
      while (true)
      {
        if (stack.size() == 0)
        {
          T newrc = createConnectorInstance(threadContext,className);
          if (newrc == null)
            return null;
          newrc.connect(configParams);
          stack.add(newrc);
        }
        
        // Since thread context set can fail, do that before we remove it from the pool.
        T rc = stack.remove(stack.size()-1);
        // Set the thread context.  This can throw an exception!!  We need to be sure our bookkeeping
        // is resilient against that possibility.  Losing a connector instance that was just sitting
        // in the pool does NOT affect numFree, so no change needed here; we just can't disconnect the
        // connector instance if this fails.
        rc.setThreadContext(threadContext);
        // Verify that the connector is in fact compatible
        if (!(rc.getClass().getName().equals(className) && rc.getConfiguration().equals(configParams)))
        {
          // Looks like parameters have changed, so discard old instance.
          try
          {
            rc.disconnect();
          }
          finally
          {
            rc.clearThreadContext();
          }
          continue;
        }
        // About to return a connector instance; decrement numFree accordingly.
        numFree--;
        return rc;
      }
    }

    /** Release a connector to the pool.
    *@param connector is the connector.
    */
    public synchronized void releaseConnector(IThreadContext threadContext, T connector)
      throws ManifoldCFException
    {
      if (connector == null)
        return;

      // Make sure connector knows it's released
      connector.clearThreadContext();
      // Return it to the pool, and note that it is no longer in use.
      stack.add(connector);
      numFree++;
      // Determine if we need to free some connectors.  If the number
      // of allocated connectors exceeds the target, we unload some
      // off the stack.
      // The question is whether the stack has too many connector instances
      // on it.  Obviously, if it stack.size() > max, it does - but remember
      // that the number of outstanding connectors is max - numFree.
      // So, we have an excess if stack.size() > max - (max-numFree).
      // Simplifying: excess is when stack.size() > numFree.
      while (stack.size() > 0 && stack.size() > numFree)
      {
        // Try to find a connector instance that is not actually connected.
        // These are likely to be at the front of the queue, since those are the
        // oldest.
        int j;
        for (j = 0; j < stack.size(); j++)
        {
          if (!stack.get(j).isConnected())
            break;
        }
        T rc;
        if (j == stack.size())
          rc = stack.remove(stack.size()-1);
        else
          rc = stack.remove(j);
        rc.setThreadContext(threadContext);
        try
        {
          rc.disconnect();
        }
        finally
        {
          rc.clearThreadContext();
        }
      }

      notifyAll();
    }

    /** Notify all free connectors.
    */
    public synchronized void pollAll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // The meat of the cross-cluster apportionment algorithm goes here!
      // Two global numbers each service posts: "in-use" and "target".  At no time does a service *ever* post either a "target"
      // that, together with all other active service targets, is in excess of the max.  Also, at no time a service post
      // a target that, when added to the other "in-use" values, exceeds the max.  If the "in-use" values everywhere else
      // already equal or exceed the max, then the target will be zero.
      // The target quota is calculated as follows:
      // (1) Target is summed, excluding ours.  This is GlobalTarget.
      // (2) In-use is summed, excluding ours.  This is GlobalInUse.
      // (3) Our MaximumTarget is computed, which is Maximum - GlobalTarget or Maximum - GlobalInUse, whichever is
      //     smaller, but never less than zero.
      // (4) Our FairTarget is computed.  The FairTarget divides the Maximum by the number of services, and adds
      //     1 randomly based on the remainder.
      // (5) We compute OptimalTarget as follows: We start with current local target.  If current local target
      //    exceeds current local in-use count, we adjust OptimalTarget downward by one.  Otherwise we increase it
      //    by one.
      // (6) Finally, we compute Target by taking the minimum of MaximumTarget, FairTarget, and OptimalTarget.

      ILockManager lockManager = LockManagerFactory.make(threadContext);
      lockManager.enterWriteLock(targetCalcLockName);
      try
      {
        // Compute MaximumTarget
        SumClass sumClass = new SumClass(serviceName);
        lockManager.scanServiceData(serviceTypeName, sumClass);
        //System.out.println("numServices = "+sumClass.getNumServices()+"; globalTarget = "+sumClass.getGlobalTarget()+"; globalInUse = "+sumClass.getGlobalInUse());
        
        int numServices = sumClass.getNumServices();
        if (numServices == 0)
          return;
        int globalTarget = sumClass.getGlobalTarget();
        int globalInUse = sumClass.getGlobalInUse();
        int maximumTarget = globalMax - globalTarget;
        if (maximumTarget > globalMax - globalInUse)
          maximumTarget = globalMax - globalInUse;
        if (maximumTarget < 0)
          maximumTarget = 0;
        
        // Compute FairTarget
        int fairTarget = globalMax / numServices;
        int remainder = globalMax % numServices;
        // Randomly choose whether we get an addition to the FairTarget
        if (randomNumberGenerator.nextInt(numServices) < remainder)
          fairTarget++;
        
        // Compute OptimalTarget (and poll connectors while we are at it)
        int localInUse = localMax - numFree;      // These are the connectors that have been handed out
        for (T rc : stack)
        {
          // Notify
          rc.setThreadContext(threadContext);
          try
          {
            rc.poll();
            if (rc.isConnected())
              localInUse++;       // Count every pooled connector that is still connected
          }
          finally
          {
            rc.clearThreadContext();
          }
        }
        int optimalTarget = localMax;
        if (localMax > localInUse)
          optimalTarget--;
        else
        {
          // We want a fast ramp up, so make this proportional to globalMax
          int increment = globalMax >> 2;
          if (increment == 0)
            increment = 1;
          optimalTarget += increment;
        }
        
        //System.out.println(serviceTypeName+":maxTarget = "+maximumTarget+"; fairTarget = "+fairTarget+"; optimalTarget = "+optimalTarget);

        // Now compute actual target
        int target = maximumTarget;
        if (target > fairTarget)
          target = fairTarget;
        if (target > optimalTarget)
          target = optimalTarget;
        
        //System.out.println(serviceTypeName+":Picking target="+target+"; localInUse="+localInUse);
        // Write these values to the service data variables.
        // NOTE that there is a race condition here; the target value depends on all the calculations above being accurate, and not changing out from under us.
        // So, that's why we have a write lock around the pool calculations.
        
        lockManager.updateServiceData(serviceTypeName, serviceName, pack(target, localInUse));
        
        // Now, update our localMax
        if (target == localMax)
          return;
        //System.out.println(serviceTypeName+":Updating target: "+target);
        // Compute the number of instances in use locally
        localInUse = localMax - numFree;
        localMax = target;
        // numFree may turn out to be negative here!!  That's okay; we'll just free released connectors
        // until we enter positive territory again.
        numFree = localMax - localInUse;
        notifyAll();
      }
      finally
      {
        lockManager.leaveWriteLock(targetCalcLockName);
      }
      
      // Finally, free pooled instances in excess of target
      while (stack.size() > 0 && stack.size() > numFree)
      {
        // Try to find a connector instance that is not actually connected.
        // These are likely to be at the front of the queue, since those are the
        // oldest.
        int j;
        for (j = 0; j < stack.size(); j++)
        {
          if (!stack.get(j).isConnected())
            break;
        }
        T rc;
        if (j == stack.size())
          rc = stack.remove(stack.size()-1);
        else
          rc = stack.remove(j);
        rc.setThreadContext(threadContext);
        try
        {
          rc.disconnect();
        }
        finally
        {
          rc.clearThreadContext();
        }
      }

    }

    /** Flush unused connectors.
    */
    public synchronized void flushUnused(IThreadContext threadContext)
      throws ManifoldCFException
    {
      while (stack.size() > 0)
      {
        // Disconnect
        T rc = stack.remove(stack.size()-1);
        rc.setThreadContext(threadContext);
        try
        {
          rc.disconnect();
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
      flushUnused(threadContext);
      
      // End service activity
      if (isAlive)
      {
        isAlive = false;
        notifyAll();
        ILockManager lockManager = LockManagerFactory.make(threadContext);
        lockManager.endServiceActivity(serviceTypeName, serviceName);
      }
    }

  }

  protected static class SumClass implements IServiceDataAcceptor
  {
    protected final String serviceName;
    protected int numServices = 0;
    protected int globalTargetTally = 0;
    protected int globalInUseTally = 0;
    
    public SumClass(String serviceName)
    {
      this.serviceName = serviceName;
    }
    
    @Override
    public boolean acceptServiceData(String serviceName, byte[] serviceData)
      throws ManifoldCFException
    {
      numServices++;

      if (!serviceName.equals(this.serviceName))
      {
        globalTargetTally += unpackTarget(serviceData);
        globalInUseTally += unpackInUse(serviceData);
      }
      return false;
    }

    public int getNumServices()
    {
      return numServices;
    }
    
    public int getGlobalTarget()
    {
      return globalTargetTally;
    }
    
    public int getGlobalInUse()
    {
      return globalInUseTally;
    }
    
  }
  
  protected static int unpackTarget(byte[] data)
  {
    if (data == null || data.length != 8)
      return 0;
    return (((int)data[0]) & 0xff) +
      ((((int)data[1]) << 8) & 0xff00) +
      ((((int)data[2]) << 16) & 0xff0000) +
      ((((int)data[3]) << 24) & 0xff000000);
  }

  protected static int unpackInUse(byte[] data)
  {
    if (data == null || data.length != 8)
      return 0;
    return (((int)data[4]) & 0xff) +
      ((((int)data[5]) << 8) & 0xff00) +
      ((((int)data[6]) << 16) & 0xff0000) +
      ((((int)data[7]) << 24) & 0xff000000);
  }

  protected static byte[] pack(int target, int inUse)
  {
    byte[] rval = new byte[8];
    rval[0] = (byte)(target & 0xff);
    rval[1] = (byte)((target >> 8) & 0xff);
    rval[2] = (byte)((target >> 16) & 0xff);
    rval[3] = (byte)((target >> 24) & 0xff);
    rval[4] = (byte)(inUse & 0xff);
    rval[5] = (byte)((inUse >> 8) & 0xff);
    rval[6] = (byte)((inUse >> 16) & 0xff);
    rval[7] = (byte)((inUse >> 24) & 0xff);
    return rval;
  }
  
}
