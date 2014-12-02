/* $Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** The lock manager manages locks across all threads and JVMs and cluster members, using Zookeeper.
* There should be no more than ONE instance of this class per thread!!!  The factory should enforce this.
*/
public class ZooKeeperLockManager extends BaseLockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String zookeeperConnectStringParameter = "org.apache.manifoldcf.zookeeper.connectstring";
  protected final static String zookeeperSessionTimeoutParameter = "org.apache.manifoldcf.zookeeper.sessiontimeout";

  private final static String CONFIGURATION_PATH = "/org.apache.manifoldcf/configuration";
  private final static String RESOURCE_PATH_PREFIX = "/org.apache.manifoldcf/resources-";
  private final static String FLAG_PATH_PREFIX = "/org.apache.manifoldcf/flags-";
  private final static String SERVICETYPE_LOCK_PATH_PREFIX = "/org.apache.manifoldcf/servicelock-";
  private final static String SERVICETYPE_ACTIVE_PATH_PREFIX = "/org.apache.manifoldcf/serviceactive-";
  private final static String SERVICETYPE_REGISTER_PATH_PREFIX = "/org.apache.manifoldcf/service-";
  /** Anonymous global variable name prefix, to be followed by the service type */
  private final static String SERVICETYPE_ANONYMOUS_COUNTER_PREFIX = "/org.apache.manifoldcf/serviceanon-";
  
  /** Anonymous service name prefix, to be followed by an integer */
  protected final static String anonymousServiceNamePrefix = "_ANON_";

  // ZooKeeper connection pool
  protected static Integer connectionPoolLock = new Integer(0);
  protected static ZooKeeperConnectionPool pool = null;
  protected static Integer zookeeperPoolLocker = new Integer(0);
  protected static LockPool myZooKeeperLocks = null;
  protected static Integer ephemeralPoolLocker = new Integer(0);
  protected static ZooKeeperEphemeralNodePool myEphemeralNodes = null;

  // Cached local values
  protected ManifoldCFConfiguration cachedConfiguration = null;
  
  /** Constructor */
  public ZooKeeperLockManager()
    throws ManifoldCFException
  {
    synchronized (connectionPoolLock)
    {
      if (pool == null)
      {
        // Initialize the ZooKeeper connection pool
        String connectString = ManifoldCF.getStringProperty(zookeeperConnectStringParameter,null);
        if (connectString == null)
          throw new ManifoldCFException("Zookeeper lock manager requires a valid "+zookeeperConnectStringParameter+" property");
        int sessionTimeout = ManifoldCF.getIntProperty(zookeeperSessionTimeoutParameter,300000);
        ManifoldCF.addShutdownHook(new ZooKeeperShutdown());
        pool = new ZooKeeperConnectionPool(connectString, sessionTimeout);
      }
    }
    synchronized (zookeeperPoolLocker)
    {
      if (myZooKeeperLocks == null)
      {
        myZooKeeperLocks = new LockPool(new ZooKeeperLockObjectFactory(pool));
      }
    }
    synchronized (ephemeralPoolLocker)
    {
      if (myEphemeralNodes == null)
      {
        myEphemeralNodes = new ZooKeeperEphemeralNodePool(pool);
      }
    }
  }
  
  // The node synchronization model involves keeping track of active agents entities, so that other entities
  // can perform any necessary cleanup if one of the agents processes goes away unexpectedly.  There is a
  // registration primitive (which can fail if the same guid is used as is already registered and active), a
  // shutdown primitive (which makes a process id go inactive), and various inspection primitives.
  
  // For the zookeeper implementation, we'll need the following:
  // - a service-type-specific global write lock transient node
  // - a service-type-specific permanent root node that has registered services as children
  // - a service-type-specific transient root node that has active services as children
  //
  // This is not necessarily the best implementation that meets the constraints, but it is straightforward
  // and will serve until we come up with a better one.
  
  /** Register a service and begin service activity.
  * This atomic operation creates a permanent registration entry for a service.
  * If the permanent registration entry already exists, this method will not create it or
  * treat it as an error.  This operation also enters the "active" zone for the service.  The "active" zone will remain in force until it is
  * canceled, or until the process is interrupted.  Ideally, the corresponding endServiceActivity method will be
  * called when the service shuts down.  Some ILockManager implementations require that this take place for
  * proper management.
  * If the transient registration already exists, it is treated as an error and an exception will be thrown.
  * If registration will succeed, then this method may call an appropriate IServiceCleanup method to clean up either the
  * current service, or all services on the cluster.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to register.  If null is passed, a transient unique service name will be
  *    created, and will be returned to the caller.
  *@param cleanup is called to clean up either the current service, or all services of this type, if no other active service exists.
  *    May be null.  Local service cleanup is never called if the serviceName argument is null.
  *@return the actual service name.
  */
  @Override
  public String registerServiceBeginServiceActivity(String serviceType, String serviceName,
    IServiceCleanup cleanup)
    throws ManifoldCFException
  {
    return registerServiceBeginServiceActivity(serviceType, serviceName, null, cleanup);
  }
    
  /** Register a service and begin service activity.
  * This atomic operation creates a permanent registration entry for a service.
  * If the permanent registration entry already exists, this method will not create it or
  * treat it as an error.  This operation also enters the "active" zone for the service.  The "active" zone will remain in force until it is
  * canceled, or until the process is interrupted.  Ideally, the corresponding endServiceActivity method will be
  * called when the service shuts down.  Some ILockManager implementations require that this take place for
  * proper management.
  * If the transient registration already exists, it is treated as an error and an exception will be thrown.
  * If registration will succeed, then this method may call an appropriate IServiceCleanup method to clean up either the
  * current service, or all services on the cluster.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to register.  If null is passed, a transient unique service name will be
  *    created, and will be returned to the caller.
  *@param initialData is the initial service data for this service.
  *@param cleanup is called to clean up either the current service, or all services of this type, if no other active service exists.
  *    May be null.  Local service cleanup is never called if the serviceName argument is null.
  *@return the actual service name.
  */
  public String registerServiceBeginServiceActivity(String serviceType, String serviceName,
    byte[] initialData, IServiceCleanup cleanup)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryWriteLock(connection, serviceType);
        try
        {
          if (serviceName == null)
            serviceName = constructUniqueServiceName(connection, serviceType);

          String encodedServiceName = ZooKeeperConnection.zooKeeperSafeName(serviceName);
          
          String activePath = buildServiceTypeActivePath(serviceType, encodedServiceName);
          if (connection.checkNodeExists(activePath))
            throw new ManifoldCFException("Service '"+serviceName+"' of type '"+serviceType+"' is already active");
          // First, see where we stand.
          // We need to find out whether (a) our service is already registered; (b) how many registered services there are;
          // (c) whether there are other active services.  But no changes will be made at this time.
          String registrationNodePath = buildServiceTypeRegistrationPath(serviceType);
          List<String> children = connection.getChildren(registrationNodePath);
          boolean foundService = false;
          boolean foundActiveService = false;
          for (String encodedRegisteredServiceName : children)
          {
            if (encodedRegisteredServiceName.equals(encodedServiceName))
              foundService = true;
            if (connection.checkNodeExists(buildServiceTypeActivePath(serviceType, encodedRegisteredServiceName)))
              foundActiveService = true;
          }
          
          // Call the appropriate cleanup.  This will depend on what's actually registered, and what's active.
          // If there were no services registered at all when we started, then no cleanup is needed, just cluster init.
          // If this fails, we must revert to having our service not be registered and not be active.
          boolean unregisterAll = false;
          if (cleanup != null)
          {
            if (children.size() == 0)
            {
              // If we could count on locks never being cleaned up, clusterInit()
              // would be sufficient here.  But then there's no way to recover from
              // a lock clean.
              cleanup.cleanUpAllServices();
              cleanup.clusterInit();
            }
            else if (foundService && foundActiveService)
              cleanup.cleanUpService(serviceName);
            else if (!foundActiveService)
            {
              cleanup.cleanUpAllServices();
              cleanup.clusterInit();
              unregisterAll = true;
            }
          }

          if (unregisterAll)
          {
            // Unregister all (since we did a global cleanup)
            for (String encodedRegisteredServiceName : children)
            {
              if (!encodedRegisteredServiceName.equals(encodedServiceName))
                connection.deleteChild(registrationNodePath, encodedRegisteredServiceName);
            }
          }

          // Now, register (if needed)
          if (!foundService)
          {
            connection.createChild(registrationNodePath, encodedServiceName);
          }
          
          // Last, set the appropriate active flag
          myEphemeralNodes.createNode(activePath, initialData);
          return serviceName;
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Set service data for a service.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service.
  *@param serviceData is the data to update to (may be null).
  * This updates the service's transient data (or deletes it).  If the service is not active, an exception is thrown.
  */
  @Override
  public void updateServiceData(String serviceType, String serviceName, byte[] serviceData)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryWriteLock(connection, serviceType);
        try
        {
          String activePath = buildServiceTypeActivePath(serviceType, ZooKeeperConnection.zooKeeperSafeName(serviceName));
          myEphemeralNodes.setNodeData(activePath, (serviceData==null)?new byte[0]:serviceData);
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Retrieve service data for a service.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service.
  *@return the service's transient data.
  */
  @Override
  public byte[] retrieveServiceData(String serviceType, String serviceName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryReadLock(connection, serviceType);
        try
        {
          String activePath = buildServiceTypeActivePath(serviceType, ZooKeeperConnection.zooKeeperSafeName(serviceName));
          return connection.getNodeData(activePath);
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Scan service data for a service type.  Only active service data will be considered.
  *@param serviceType is the type of service.
  *@param dataAcceptor is the object that will be notified of each item of data for each service name found.
  */
  @Override
  public void scanServiceData(String serviceType, IServiceDataAcceptor dataAcceptor)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryReadLock(connection, serviceType);
        try
        {
          String registrationNodePath = buildServiceTypeRegistrationPath(serviceType);
          List<String> children = connection.getChildren(registrationNodePath);
          for (String encodedRegisteredServiceName : children)
          {
            String activeNodePath = buildServiceTypeActivePath(serviceType, encodedRegisteredServiceName);
            if (connection.checkNodeExists(activeNodePath))
            {
              byte[] serviceData = connection.getNodeData(activeNodePath);
              if (dataAcceptor.acceptServiceData(ZooKeeperConnection.zooKeeperDecodeSafeName(encodedRegisteredServiceName), serviceData))
                break;
            }
          }
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }

  }

  /** Count all active services of a given type.
  *@param serviceType is the service type.
  *@return the count.
  */
  @Override
  public int countActiveServices(String serviceType)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryReadLock(connection, serviceType);
        try
        {
          String registrationNodePath = buildServiceTypeRegistrationPath(serviceType);
          List<String> children = connection.getChildren(registrationNodePath);
          int activeServiceCount = 0;
          for (String encodedRegisteredServiceName : children)
          {
            if (connection.checkNodeExists(buildServiceTypeActivePath(serviceType, encodedRegisteredServiceName)))
              activeServiceCount++;
          }
          return activeServiceCount;
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Clean up any inactive services found.
  * Calling this method will invoke cleanup of one inactive service at a time.
  * If there are no inactive services around, then false will be returned.
  * Note that this method will block whatever service it finds from starting up
  * for the time the cleanup is proceeding.  At the end of the cleanup, if
  * successful, the service will be atomically unregistered.
  *@param serviceType is the service type.
  *@param cleanup is the object to call to clean up an inactive service.
  *@return true if there were no cleanup operations necessary.
  */
  @Override
  public boolean cleanupInactiveService(String serviceType, IServiceCleanup cleanup)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryWriteLock(connection, serviceType);
        try
        {
          // We find ONE service that is registered but inactive, and clean up after that one.
          // Presumably the caller will lather, rinse, and repeat.
          String registrationNodePath = buildServiceTypeRegistrationPath(serviceType);
          List<String> children = connection.getChildren(registrationNodePath);
          String encodedServiceName = null;
          for (String encodedRegisteredServiceName : children)
          {
            if (!connection.checkNodeExists(buildServiceTypeActivePath(serviceType, encodedRegisteredServiceName)))
            {
              encodedServiceName = encodedRegisteredServiceName;
              break;
            }
          }
          if (encodedServiceName == null)
            return true;
          
          // Found one, in serviceName, at position i
          // Ideally, we should signal at this point that we're cleaning up after it, and then leave
          // the exclusive lock, so that other activity can take place.  MHL
          cleanup.cleanUpService(ZooKeeperConnection.zooKeeperDecodeSafeName(encodedServiceName));

          // Unregister the service.
          connection.deleteChild(registrationNodePath, encodedServiceName);
          return false;
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }

      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** End service activity.
  * This operation exits the "active" zone for the service.  This must take place using the same ILockManager
  * object that was used to registerServiceBeginServiceActivity() - which implies that it is the same thread.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to exit.
  */
  @Override
  public void endServiceActivity(String serviceType, String serviceName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryWriteLock(connection, serviceType);
        try
        {
          myEphemeralNodes.deleteNode(buildServiceTypeActivePath(serviceType, ZooKeeperConnection.zooKeeperSafeName(serviceName)));
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
    
  /** Check whether a service is active or not.
  * This operation returns true if the specified service is considered active at the moment.  Once a service
  * is not active anymore, it can only return to activity by calling beginServiceActivity() once more.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to check on.
  *@return true if the service is considered active.
  */
  @Override
  public boolean checkServiceActive(String serviceType, String serviceName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        enterServiceRegistryReadLock(connection, serviceType);
        try
        {
          return connection.checkNodeExists(buildServiceTypeActivePath(serviceType, ZooKeeperConnection.zooKeeperSafeName(serviceName)));
        }
        finally
        {
          leaveServiceRegistryLock(connection);
        }
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Enter service registry read lock */
  protected void enterServiceRegistryReadLock(ZooKeeperConnection connection, String serviceType)
    throws ManifoldCFException, InterruptedException
  {
    String serviceTypeLock = buildServiceTypeLockPath(serviceType);
    while (true)
    {
      if (connection.obtainReadLockNoWait(serviceTypeLock))
        return;
      ManifoldCF.sleep(100L);
    }
  }
  
  /** Enter service registry write lock */
  protected void enterServiceRegistryWriteLock(ZooKeeperConnection connection, String serviceType)
    throws ManifoldCFException, InterruptedException
  {
    String serviceTypeLock = buildServiceTypeLockPath(serviceType);
    while (true)
    {
      if (connection.obtainWriteLockNoWait(serviceTypeLock))
        return;
      ManifoldCF.sleep(100L);
    }
  }
  
  /** Leave service registry lock */
  protected void leaveServiceRegistryLock(ZooKeeperConnection connection)
    throws ManifoldCFException, InterruptedException
  {
    connection.releaseLock();
  }
  
  /** Construct a unique service name given the service type.
  */
  protected String constructUniqueServiceName(ZooKeeperConnection connection, String serviceType)
    throws ManifoldCFException, InterruptedException
  {
    String serviceCounterName = makeServiceCounterName(serviceType);
    int serviceUID = readServiceCounter(connection, serviceCounterName);
    writeServiceCounter(connection, serviceCounterName,serviceUID+1);
    return anonymousServiceNamePrefix + serviceUID;
  }
  
  /** Make the service counter name for a service type.
  */
  protected static String makeServiceCounterName(String serviceType)
  {
    return SERVICETYPE_ANONYMOUS_COUNTER_PREFIX + ZooKeeperConnection.zooKeeperSafeName(serviceType);
  }
  
  /** Read service counter.
  */
  protected int readServiceCounter(ZooKeeperConnection connection, String serviceCounterName)
    throws ManifoldCFException, InterruptedException
  {
    int rval;
    byte[] serviceCounterData = connection.readData(serviceCounterName);
    if (serviceCounterData == null || serviceCounterData.length != 4)
    {
      rval = 0;
      //System.out.println(" Null or bad data length for service counter '"+serviceCounterName+"'");
    }
    else
    {
      rval = (((int)serviceCounterData[0]) & 0xff) +
        ((((int)serviceCounterData[1]) << 8) & 0xff00) +
        ((((int)serviceCounterData[2]) << 16) & 0xff0000) +
        ((((int)serviceCounterData[3]) << 24) & 0xff000000);
      //System.out.println(" Read actual data from service counter '"+serviceCounterName+"': "+java.util.Arrays.toString(serviceCounterData));
    }
    //System.out.println("Read service counter '"+serviceCounterName+"'; value = "+rval);
    return rval;
  }
  
  /** Write service counter.
  */
  protected void writeServiceCounter(ZooKeeperConnection connection, String serviceCounterName, int counter)
    throws ManifoldCFException, InterruptedException
  {
    byte[] serviceCounterData = new byte[4];
    serviceCounterData[0] = (byte)(counter & 0xff);
    serviceCounterData[1] = (byte)((counter >> 8) & 0xff);
    serviceCounterData[2] = (byte)((counter >> 16) & 0xff);
    serviceCounterData[3] = (byte)((counter >> 24) & 0xff);
    connection.writeData(serviceCounterName,serviceCounterData);
    //System.out.println("Wrote service counter '"+serviceCounterName+"'; value = "+counter+": "+java.util.Arrays.toString(serviceCounterData));
  }

  /** Build a zk path for the lock for a specific service type.
  */
  protected static String buildServiceTypeLockPath(String serviceType)
  {
    return SERVICETYPE_LOCK_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(serviceType);
  }
  
  /** Build a zk path for the active node for a specific service of a specific type.
  */
  protected static String buildServiceTypeActivePath(String serviceType, String encodedServiceName)
  {
    return SERVICETYPE_ACTIVE_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(serviceType) + "-" + encodedServiceName;
  }
  
  /** Build a zk path for the registration node for a specific service type.
  */
  protected static String buildServiceTypeRegistrationPath(String serviceType)
  {
    return SERVICETYPE_REGISTER_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(serviceType);
  }
  
  // Shared configuration

  /** Get the current shared configuration.  This configuration is available in common among all nodes,
  * and thus must not be accessed through here for the purpose of finding configuration data that is specific to any one
  * specific node.
  *@param configurationData is the globally-shared configuration information.
  */
  @Override
  public ManifoldCFConfiguration getSharedConfiguration()
    throws ManifoldCFException
  {
    if (cachedConfiguration == null)
    {
      try
      {
        ZooKeeperConnection connection = pool.grab();
        try
        {
          // Read as a byte array, then parse
          byte[] configurationData = connection.readData(CONFIGURATION_PATH);
          if (configurationData != null)
            cachedConfiguration = new ManifoldCFConfiguration(new ByteArrayInputStream(configurationData));
          else
            cachedConfiguration = new ManifoldCFConfiguration();
        }
        finally
        {
          pool.release(connection);
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
    }
    return cachedConfiguration;
  }

  /** Write shared configuration.  Caller closes the input stream.
  */
  public void setSharedConfiguration(InputStream configurationInputStream)
    throws ManifoldCFException
  {
    try
    {
      // Read to a byte array
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      byte[] data = new byte[65536];

      while (true)
      {
        int nRead = configurationInputStream.read(data, 0, data.length);
        if (nRead == -1)
          break;
        buffer.write(data, 0, nRead);
      }
      buffer.flush();

      byte[] toWrite = buffer.toByteArray();
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.writeData(CONFIGURATION_PATH, toWrite);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.setGlobalFlag(FLAG_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(flagName));
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.clearGlobalFlag(FLAG_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(flagName));
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  @Override
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        return connection.checkGlobalFlag(FLAG_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(flagName));
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Read data from a shared data resource.  Use this method to read any existing data, or get a null back if there is no such resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@return a byte array containing the data, or null.
  */
  @Override
  public byte[] readData(String resourceName)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        return connection.readData(RESOURCE_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(resourceName));
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }
  
  /** Write data to a shared data resource.  Use this method to write a body of data into a shared resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@param data is the byte array containing the data.  Pass null if you want to delete the resource completely.
  */
  @Override
  public void writeData(String resourceName, byte[] data)
    throws ManifoldCFException
  {
    try
    {
      ZooKeeperConnection connection = pool.grab();
      try
      {
        connection.writeData(RESOURCE_PATH_PREFIX + ZooKeeperConnection.zooKeeperSafeName(resourceName), data);
      }
      finally
      {
        pool.release(connection);
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  // Main method - for loading Zookeeper data
  
  public static void main(String[] argv)
  {
    if (argv.length != 1)
    {
      System.err.println("Usage: ZooKeeperLockManager <shared_configuration_file>");
      System.exit(1);
    }
    
    File file = new File(argv[0]);
    
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);

      try
      {
        FileInputStream fis = new FileInputStream(file);
        try
        {
          new ZooKeeperLockManager().setSharedConfiguration(fis);
        }
        finally
        {
          fis.close();
        }
      }
      finally
      {
        ManifoldCF.cleanUpEnvironment(tc);
      }
    }
    catch (Throwable e)
    {
      e.printStackTrace(System.err);
      System.exit(-1);
    }
  }
  
  // Protected methods and classes
  
  /** Override this method to change the nature of global locks.
  */
  @Override
  protected LockPool getGlobalLockPool()
  {
    return myZooKeeperLocks;
  }

  /** Shutdown the connection pool.
  */
  protected static void shutdownPool()
    throws ManifoldCFException
  {
    synchronized (ephemeralPoolLocker)
    {
      if (myEphemeralNodes != null)
      {
        try
        {
          myEphemeralNodes.deleteAll();
          myEphemeralNodes = null;
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
    }
    
    synchronized (connectionPoolLock)
    {
      if (pool != null)
      {
        try
        {
          pool.closeAll();
          pool = null;
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
    }
  }
  
  protected static class ZooKeeperShutdown implements IShutdownHook
  {
    public ZooKeeperShutdown()
    {
    }
    
    /** Do the requisite cleanup.
    */
    @Override
    public void doCleanup(IThreadContext threadContext)
      throws ManifoldCFException
    {
      shutdownPool();
    }

  }
  
}
