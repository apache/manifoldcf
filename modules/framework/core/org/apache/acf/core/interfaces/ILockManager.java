/* $Id: ILockManager.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.core.interfaces;


/** The lock manager manages locks across all threads and JVMs and cluster members.  It also
* manages shared data, which is not necessarily atomic and should be protected by locks.
*/
public interface ILockManager
{
  public static final String _rcsid = "@(#)$Id: ILockManager.java 921329 2010-03-10 12:44:20Z kwright $";

  /** Raise a flag.  Use this method to assert a condition, or send a global signal.  The flag will be reset when the
  * entire system is restarted.
  *@param flagName is the name of the flag to set.
  */
  public void setGlobalFlag(String flagName)
    throws ACFException;

  /** Clear a flag.  Use this method to clear a condition, or retract a global signal.
  *@param flagName is the name of the flag to clear.
  */
  public void clearGlobalFlag(String flagName)
    throws ACFException;
  
  /** Check the condition of a specified flag.
  *@param flagName is the name of the flag to check.
  *@return true if the flag is set, false otherwise.
  */
  public boolean checkGlobalFlag(String flagName)
    throws ACFException;

  /** Read data from a shared data resource.  Use this method to read any existing data, or get a null back if there is no such resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@return a byte array containing the data, or null.
  */
  public byte[] readData(String resourceName)
    throws ACFException;
  
  /** Write data to a shared data resource.  Use this method to write a body of data into a shared resource.
  * Note well that this is not necessarily an atomic operation, and it must thus be protected by a lock.
  *@param resourceName is the global name of the resource.
  *@param data is the byte array containing the data.  Pass null if you want to delete the resource completely.
  */
  public void writeData(String resourceName, byte[] data)
    throws ACFException;

  /** Wait for a time before retrying a lock.  Use this method to wait
  * after a LockException has been thrown.  )If this is not done, the application
  * will wind up busy waiting.)
  *@param time is the amount of time to wait, in milliseconds.  Zero is a legal
  * value, and will wait no time, but will give up the current timeslice to another
  * thread.
  */
  public void timedWait(int time)
    throws ACFException;

  /** Enter a write locked code area (i.e., block out both readers and other writers).
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, the current thread may wait until all other
  * threads have left the section.
  *@param lockKey is the name of the lock.
  */
  public void enterWriteLock(String lockKey)
    throws ACFException;

  /** Enter a write locked code area (i.e., block out both readers and other writers),
  * but do not wait if the lock cannot be obtained.
  * Write locks permit only ONE thread to be in the named section, across JVM's
  * as well.  In order to guarantee this, an exception (LockException) will be
  * thrown if the lock condition cannot be met immediately.
  *@param lockKey is the name of the lock.
  */
  public void enterWriteLockNoWait(String lockKey)
    throws ACFException, LockException;

  /** Leave a write locked code area.  Use this method to exit a write-locked section. The lockKey
  * parameter must correspond to the key used for the enter method.
  * @param lockKey is the name of the lock.
  */
  public void leaveWriteLock(String lockKey)
    throws ACFException;

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  public void enterNonExWriteLock(String lockKey)
    throws ACFException;

  /** Enter a non-exclusive write-locked area (blocking out all readers, but letting in other "writers").
  * This kind of lock is designed to be used in conjunction with read locks.  It is used typically in
  * a situation where the read lock represents a query and the non-exclusive write lock represents a modification
  * to an individual item that might affect the query, but where multiple modifications do not individually
  * interfere with one another (use of another, standard, write lock per item can guarantee this).
  * This method works across JVMs, and will throw LockException if the lock condition cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  public void enterNonExWriteLockNoWait(String lockKey)
    throws ACFException, LockException;

  /** Leave a non-exclusive write locked code area.  Use this method to exit a non-ex-write-locked section.
  * The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  public void leaveNonExWriteLock(String lockKey)
    throws ACFException;

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and may wait if the required lock cannot be immediately obtained.
  *@param lockKey is the name of the lock.
  */
  public void enterReadLock(String lockKey)
    throws ACFException;

  /** Enter a read-only locked area (i.e., block ONLY if there's a writer).  This kind of lock
  * permits multiple threads inside the same code area, but only if there is no "writer" in the
  * same section at the same time.
  * This method works across JVMs, and will throw LockException if the required lock cannot be immediately met.
  *@param lockKey is the name of the lock.
  */
  public void enterReadLockNoWait(String lockKey)
    throws ACFException, LockException;

  /** Leave a read-locked code area.  Use this method to exit a read-locked section.  The lockKey
  * parameter must correspond to the key used for the enter method.
  *@param lockKey is the name of the lock.
  */
  public void leaveReadLock(String lockKey)
    throws ACFException;

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will wait if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void enterLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ACFException;

  /** Enter multiple locks simultaneously.  Use this method if a series or set of locks needs to be
  * thrown for an operation to take place.  This operation will avoid deadlock if all the locks are
  * thrown at the start of the area using this method.
  * This method works cross-JVM, and will throw LockException if the required locks are not available.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void enterLocksNoWait(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ACFException, LockException;

  /** Leave multiple locks. Use this method to leave a section started with enterLocks() or
  * enterLocksNoWait().  The parameters must correspond to those passed to the enter method.
  *@param readLocks is an array of read lock names, or null if there are no read locks desired.
  *@param nonExWriteLocks is an array of non-ex write lock names, or null if none desired.
  *@param writeLocks is an array of write lock names, or null if there are none desired.
  */
  public void leaveLocks(String[] readLocks, String[] nonExWriteLocks, String[] writeLocks)
    throws ACFException;

  /** Clear all outstanding locks in the system.
  * This is a very dangerous method to use (obviously)...
  */
  public void clearLocks()
    throws ACFException;

  /** Enter a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterReadCriticalSection(String sectionKey)
    throws ACFException;

  /** Leave a named, read critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveReadCriticalSection(String sectionKey)
    throws ACFException;

  /** Enter a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterNonExWriteCriticalSection(String sectionKey)
    throws ACFException;

  /** Leave a named, non-exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveNonExWriteCriticalSection(String sectionKey)
    throws ACFException;


  /** Enter a named, exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to enter.  Only one thread can be in any given named
  * section at a time.
  */
  public void enterWriteCriticalSection(String sectionKey)
    throws ACFException;

  /** Leave a named, exclusive write critical section (NOT a lock).  Critical sections never cross JVM boundaries.
  * Critical section names do not collide with lock names; they have a distinct namespace.
  *@param sectionKey is the name of the section to leave.  Only one thread can be in any given named
  * section at a time.
  */
  public void leaveWriteCriticalSection(String sectionKey)
    throws ACFException;

  /** Enter multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void enterCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ACFException;

  /** Leave multiple critical sections simultaneously.
  *@param readSectionKeys is an array of read section descriptors, or null if there are no read sections desired.
  *@param nonExSectionKeys is an array of non-ex write section descriptors, or null if none desired.
  *@param writeSectionKeys is an array of write section descriptors, or null if there are none desired.
  */
  public void leaveCriticalSections(String[] readSectionKeys, String[] nonExSectionKeys, String[] writeSectionKeys)
    throws ACFException;

}
