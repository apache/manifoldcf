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
import java.util.*;
import java.io.*;

/** The lock manager manages locks across all threads and JVMs and cluster members.  There should be no more than ONE
* instance of this class per thread!!!  The factory should enforce this.
*/
public class LockManager implements ILockManager
{
  public static final String _rcsid = "@(#)$Id: LockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Backing lock manager */
  protected final ILockManager lockManager;

  public LockManager()
    throws ManifoldCFException
  {
    File synchDirectory = FileLockManager.getSynchDirectoryProperty();
    if (synchDirectory != null)
      lockManager = new FileLockManager(synchDirectory);
    else
      lockManager = new BaseLockManager();
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
  *@param cleanup is called to clean up either the current service, or all services of this type, if no other active service exists.
  *    May be null.  Local service cleanup is never called if the serviceName argument is null.
  *@return the actual service name.
  */
  @Override
  public String registerServiceBeginServiceActivity(String serviceType, String serviceName,
    IServiceCleanup cleanup)
    throws ManifoldCFException
  {
    return lockManager.registerServiceBeginServiceActivity(serviceType, serviceName, cleanup);
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
  @Override
  public String registerServiceBeginServiceActivity(String serviceType, String serviceName,
    byte[] initialData, IServiceCleanup cleanup)
    throws ManifoldCFException
  {
    return lockManager.registerServiceBeginServiceActivity(serviceType, serviceName, initialData, cleanup);
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
    lockManager.updateServiceData(serviceType, serviceName, serviceData);
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
    return lockManager.retrieveServiceData(serviceType, serviceName);
  }

  /** Scan service data for a service type.  Only active service data will be considered.
  *@param serviceType is the type of service.
  *@param dataAcceptor is the object that will be notified of each item of data for each service name found.
  */
  @Override
  public void scanServiceData(String serviceType, IServiceDataAcceptor dataAcceptor)
    throws ManifoldCFException
  {
    lockManager.scanServiceData(serviceType, dataAcceptor);
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
    return lockManager.cleanupInactiveService(serviceType, cleanup);
  }
  
  /** Count all active services of a given type.
  *@param serviceType is the service type.
  *@return the count.
  */
  @Override
  public int countActiveServices(String serviceType)
    throws ManifoldCFException
  {
    return lockManager.countActiveServices(serviceType);
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
    lockManager.endServiceActivity(serviceType, serviceName);
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
    return lockManager.checkServiceActive(serviceType, serviceName);
  }

  /** Get the current shared configuration.  This configuration is available in common among all nodes,
  * and thus must not be accessed through here for the purpose of finding configuration data that is specific to any one
  * specific node.
  *@param configurationData is the globally-shared configuration information.
  */
  @Override
  public ManifoldCFConfiguration getSharedConfiguration()
    throws ManifoldCFException
  {
    return lockManager.getSharedConfiguration();
  }

  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  @Override
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    lockManager.setGlobalFlag(flagName);
  }

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  @Override
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    lockManager.clearGlobalFlag(flagName);
  }
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  @Override
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException
  {
    return lockManager.checkGlobalFlag(flagName);
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
    return lockManager.readData(resourceName);
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
    lockManager.writeData(resourceName,data);
  }

  /** Wait for a time before retrying a lock.
  */
  @Override
  public void timedWait(int time)
    throws ManifoldCFException
  {
    lockManager.timedWait(time);
  }

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  */
  @Override
  public void enterNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.enterNonExWriteLock(lockKey);
  }

  @Override
  public void enterNonExWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    lockManager.enterNonExWriteLockNoWait(lockKey);
  }

  /** Leave a non-exclusive write lock.
  */
  @Override
  public void leaveNonExWriteLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.leaveNonExWriteLock(lockKey);
  }

  /** Enter a write locked area (i.e., block out both readers and other writers)
  * NOTE: Can't enter until all readers have left.
  */
  @Override
  public void enterWriteLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.enterWriteLock(lockKey);
  }

  @Override
  public void enterWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    lockManager.enterWriteLockNoWait(lockKey);
  }

  @Override
  public void leaveWriteLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.leaveWriteLock(lockKey);
  }

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer)
  */
  @Override
  public void enterReadLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.enterReadLock(lockKey);
  }

  @Override
  public void enterReadLockNoWait(String lockKey)
    throws ManifoldCFException, LockException
  {
    lockManager.enterReadLockNoWait(lockKey);
  }

  @Override
  public void leaveReadLock(String lockKey)
    throws ManifoldCFException
  {
    lockManager.leaveReadLock(lockKey);
  }

  @Override
  public void clearLocks()
    throws ManifoldCFException
  {
    lockManager.clearLocks();
  }

  /** Enter multiple locks
  */
  @Override
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    lockManager.enterLocks(readLocks, nonExWriteLocks, writeLocks);
  }

  @Override
  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException, LockException
  {
    lockManager.enterLocksNoWait(readLocks, nonExWriteLocks, writeLocks);
  }

  /** Leave multiple locks
  */
  @Override
  public void leaveLocks(String[] readLocks, String[] writeNonExLocks, String[] writeLocks)
    throws ManifoldCFException
  {
    lockManager.leaveLocks(readLocks, writeNonExLocks, writeLocks);
  }

  /** Enter a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void enterReadCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.enterReadCriticalSection(sectionKey);
  }

  /** Leave a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void leaveReadCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.leaveReadCriticalSection(sectionKey);
  }

  /** Enter a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void enterNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.enterNonExWriteCriticalSection(sectionKey);
  }

  /** Leave a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void leaveNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.leaveNonExWriteCriticalSection(sectionKey);
  }

  /** Enter a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void enterWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.enterWriteCriticalSection(sectionKey);
  }

  /** Leave a named, exclusive critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names should be distinct from all lock names.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  @Override
  public void leaveWriteCriticalSection(String sectionKey)
    throws ManifoldCFException
  {
    lockManager.leaveWriteCriticalSection(sectionKey);
  }

  /** Enter multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  @Override
  public void enterCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException
  {
    lockManager.enterCriticalSections(readSectionKeys, nonExSectionKeys, writeSectionKeys);
  }

  /** Leave multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  @Override
  public void leaveCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException
  {
    lockManager.leaveCriticalSections(readSectionKeys, nonExSectionKeys, writeSectionKeys);
  }

}
