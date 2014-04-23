/* $Id: ILockManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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


/** The lock manager manages locks and shared data across all threads and JVMs and cluster members.  It also
* manages transient shared data, which is not necessarily atomic and should be protected by locks.
*/
public interface ILockManager
{
  public static final String _rcsid = "@(#)$Id: ILockManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Node synchronization
  
  // The node synchronization model involves keeping track of active agents entities, so that other entities
  // can perform any necessary cleanup if one of the agents processes goes away unexpectedly.  There is a
  // registration primitive (which can fail if the same guid is used as is already registered and active), a
  // shutdown primitive (which makes a process id go inactive), and various inspection primitives.
  
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
  public String registerServiceBeginServiceActivity(String serviceType, String serviceName,
    IServiceCleanup cleanup)
    throws ManifoldCFException;
  
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
    throws ManifoldCFException;

  /** Set service data for a service.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service.
  *@param serviceData is the data to update to (may be null).
  * This updates the service's transient data (or deletes it).  If the service is not active, an exception is thrown.
  */
  public void updateServiceData(String serviceType, String serviceName, byte[] serviceData)
    throws ManifoldCFException;

  /** Retrieve service data for a service.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service.
  *@return the service's transient data.
  */
  public byte[] retrieveServiceData(String serviceType, String serviceName)
    throws ManifoldCFException;
  
  /** Scan service data for a service type.  Only active service data will be considered.
  *@param serviceType is the type of service.
  *@param dataAcceptor is the object that will be notified of each item of data for each service name found.
  */
  public void scanServiceData(String serviceType, IServiceDataAcceptor dataAcceptor)
    throws ManifoldCFException;

  /** Count all active services of a given type.
  *@param serviceType is the service type.
  *@return the count.
  */
  public int countActiveServices(String serviceType)
    throws ManifoldCFException;
  
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
  public boolean cleanupInactiveService(String serviceType, IServiceCleanup cleanup)
    throws ManifoldCFException;

  /** End service activity.
  * This operation exits the "active" zone for the service.  This must take place using the same ILockManager
  * object that was used to registerServiceBeginServiceActivity() - which implies that it is the same thread.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to exit.
  */
  public void endServiceActivity(String serviceType, String serviceName)
    throws ManifoldCFException;
    
  /** Check whether a service is active or not.
  * This operation returns true if the specified service is considered active at the moment.  Once a service
  * is not active anymore, it can only return to activity by calling beginServiceActivity() once more.
  *@param serviceType is the type of service.
  *@param serviceName is the name of the service to check on.
  *@return true if the service is considered active.
  */
  public boolean checkServiceActive(String serviceType, String serviceName)
    throws ManifoldCFException;

  // Configuration
  
  /** Get the current shared configuration.  This configuration is available in common among all nodes,
  * and thus must not be accessed through here for the purpose of finding configuration data that is specific to any one
  * specific node.
  *@param configurationData is the globally-shared configuration information.
  */
  public ManifoldCFConfiguration getSharedConfiguration()
    throws ManifoldCFException;

  // Flags
  
  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  public void setGlobalFlag(String flagName)
    throws ManifoldCFException;

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  public void clearGlobalFlag(String flagName)
    throws ManifoldCFException;
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  public boolean checkGlobalFlag(String flagName)
    throws ManifoldCFException;

  // Shared data
  
  /** Read data from a shared data resource.  Use this method to read any existing data, or get a null back if there is no such resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@return a byte array containing the data, or null.
  */
  public byte[] readData(String resourceName)
    throws ManifoldCFException;
  
  /** Write data to a shared data resource.  Use this method to write a body of data into a shared resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@param data is the byte array containing the data.  Pass null if you want to delete the resource completely.
  */
  public void writeData(String resourceName, byte[] data)
    throws ManifoldCFException;

  // Locks
  
  /** Wait for a time before retrying a lock.  Use this method to wait
  * after a LockException has been thrown.  )If this is not done, the application
  * will wind up busy waiting.)
  *@param time is the amount of time to wait, in milliseconds.  Zero is a legal
  * value, and will wait no time, but will give up the current timeslice to another
  * thread.
  */
  public void timedWait(int time)
    throws ManifoldCFException;

  /** Enter a write locked code area (i.e., block out both readers and other writers).
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, the current thread may wait until all other
  * threads have left the section.
  *@param lockKey is the name of the lock.
  */
  public void enterWriteLock(String lockKey)
    throws ManifoldCFException;

  /** Enter a write locked code area (i.e., block out both readers and other writers),
  * but do not wait if the lock cannot be obtained.
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, an exception (LockException) will be
  * thrown if the lock condition cannot be met immediately.
  *@param lockKey is the name of the lock.
  */
  public void enterWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException;

  /** Leave a write locked code area.  Use this method to exit a write-locked section. The lockKey
  * parameter must correspond to the key used for the enter method.
  * @param lockKey is the name of the lock.
  */
  public void leaveWriteLock(String lockKey)
    throws ManifoldCFException;

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  public void enterNonExWriteLock(String lockKey)
    throws ManifoldCFException;

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and will throw LockException if the lock condition cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  public void enterNonExWriteLockNoWait(String lockKey)
    throws ManifoldCFException, LockException;

  /** Leave a non-exclusive write locked code area.  Use this method to exit a non-ex-write-locked section.
  * The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  public void leaveNonExWriteLock(String lockKey)
    throws ManifoldCFException;

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  public void enterReadLock(String lockKey)
    throws ManifoldCFException;

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and will throw LockException if the required lock cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  public void enterReadLockNoWait(String lockKey)
    throws ManifoldCFException, LockException;

  /** Leave a read-locked code area.  Use this method to exit a read-locked section.  The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  public void leaveReadLock(String lockKey)
    throws ManifoldCFException;

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will wait if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException;

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will throw LockException if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException, LockException;

  /** Leave multiple locks. Use this method to leave a section started with enterLocks() or
  * enterLocksNoWait().  The parameters must correspond to those passed to the enter method.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void leaveLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ManifoldCFException;

  /** Clear all outstanding locks in the system.
  * This is a very dangerous method to use (obviously)...
  */
  public void clearLocks()
    throws ManifoldCFException;

  // Critical sections
  
  /** Enter a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterReadCriticalSection(String sectionKey)
    throws ManifoldCFException;

  /** Leave a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveReadCriticalSection(String sectionKey)
    throws ManifoldCFException;

  /** Enter a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException;

  /** Leave a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveNonExWriteCriticalSection(String sectionKey)
    throws ManifoldCFException;


  /** Enter a named, exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterWriteCriticalSection(String sectionKey)
    throws ManifoldCFException;

  /** Leave a named, exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveWriteCriticalSection(String sectionKey)
    throws ManifoldCFException;

  /** Enter multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void enterCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException;

  /** Leave multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void leaveCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ManifoldCFException;

}
