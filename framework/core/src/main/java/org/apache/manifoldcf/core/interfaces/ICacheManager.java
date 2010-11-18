/* $Id: ICacheManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes the functionality in the cache manager.  It is not meant to have multiple implementations, but
* merely to provide general abstract cross-cluster cache management services.
*/
public interface ICacheManager
{
  public static final String _rcsid = "@(#)$Id: ICacheManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Locate or create a set of objects in the cached object pool, and/or destroy and invalidate
  * the same or other objects.
  *
  * For each "locate" object
  * described below, one of two things will happen: the execObject's exists() method
  * will be called (if the object exists in the cache already), or the execObject's create*()
  * family of methods will be called.  When all objects have been declared to the execObject,
  * finally the execute() method will be called.  In both cases, all objects are considered
  * to be locked, and cannot be invalidated until the lock is cleared (after the end of the called method).
  *
  * For the invalidation descriptions, the execObject's destroy() method will be called instead.
  * The object will be invalidated from the cache at the end of the findObjectsAndExecute() operation.
  *
  * It is perfectly legal to include an object that will be invalidated in the locate set!
  *
  * If an error occurs during object creation, the execute() method will NOT be called.  If the
  * execute() method has an error, the objects that were created will still be recorded in the cache.
  *
  * @param locateObjectDescriptions is a set of description objects that uniquely describe the objects needed.
  * May be null if no objects are desired.
  * @param invalidateKeys are the keys to invalidate after successful execution.  May be null.
  * @param execObject is the cache execution object whose create() or execute() methods will
  * be called.  May be null if no in-section logic is desired, and no objects are specified.
  * @param transactionID is the current transaction identifier, or null.  Objects created within this call
  * will be associated with this transaction; they will be purged from the cache should the transaction
  * be rolled back.
  */
  public void findObjectsAndExecute(ICacheDescription[] locateObjectDescriptions, StringSet invalidateKeys,
    ICacheExecutor execObject, String transactionID)
    throws ManifoldCFException;

  /** Second way of doing cache management.
  * Basically, this approach breaks the findObjectsAndExecute() method down into bite-sized chunks.
  * The goal is additional flexibility, and an executor object does not need to be defined.
  * Pretty much everything else is identical.  The objects returned by the manager methods
  * provide context control and enforce the proper ordering and nesting.
  * This method enters the cacher and builds a cache handle.  Once a cache handle is
  * returned, the calling code MUST, under all circumstances, leave the cache using the
  * same handle.
  * @param locateObjectDescriptions is a set of description objects that uniquely describe the objects needed.
  * May be null if no objects are desired.
  * @param invalidateKeys are the keys to invalidate after successful execution.  May be null.
  * @param transactionID is the current transaction identifier, or null.  Objects created within this block
  * will be associated with this transaction; they will be purged from the cache should the transaction
  * be rolled back.
  * @return a cache handle.
  */
  public ICacheHandle enterCache(ICacheDescription[] locateObjectDescriptions, StringSet invalidateKeys,
    String transactionID)
    throws ManifoldCFException;

  /** Enter a creation critical section.  This insures that only one thread is
  * creating the specified objects at a time.  This MUST be paired with
  * a leaveCreateSection() method call, whatever happens.
  *@param handle is the cache handle.
  */
  public ICacheCreateHandle enterCreateSection(ICacheHandle handle)
    throws ManifoldCFException;

  /** Lookup an object.  Returns null if object not found.  If it is found,
  * object's LRU and expiration info are updated.  The objectDescription passed
  * MUST be one of the ones specified in the enclosing enterCache() method.
  *@param handle is the handle to use for the create.
  *@param objectDescription is the description of the object to look up.
  */
  public Object lookupObject(ICacheCreateHandle handle, ICacheDescription objectDescription)
    throws ManifoldCFException;

  /** Save a newly created object.  The object MUST be one of those identified in the
  * enterCache() method.
  *@param handle is the create handle.
  *@param objectDescription is the object description.
  *@param object is the object.
  */
  public void saveObject(ICacheCreateHandle handle, ICacheDescription objectDescription,
    Object object)
    throws ManifoldCFException;

  /** Leave the create section.
  *@param handle is the handle created by the corresponding enterCreateSection() method.
  */
  public void leaveCreateSection(ICacheCreateHandle handle)
    throws ManifoldCFException;

  /** Invalidate keys.  The keys invalidated are what got passed to the enterCache() method.
  *@param handle is the cache handle.  Does nothing if a null set of keys was passed in.
  */
  public void invalidateKeys(ICacheHandle handle)
    throws ManifoldCFException;

  /** Leave the cache.  Must be paired with enterCache, above.
  *@param handle is the handle of the cache we are leaving.
  */
  public void leaveCache(ICacheHandle handle)
    throws ManifoldCFException;

  // The following methods are used to communicate transaction information to the cache.

  /** Begin a cache transaction.
  * This keeps track of the relationship between objects cached within transactions.
  *@param startingTransactionID is the id of the transaction that is starting.
  *@param enclosingTransactionID is the id of the transaction that is in effect, or null.
  */
  public void startTransaction(String startingTransactionID, String enclosingTransactionID)
    throws ManifoldCFException;

  /** Commit a cache transaction.
  * This method MUST be called when a transaction successfully ends, or open locks will not be closed!!!
  * All cache activity that has taken place inside the transaction will be resolved, and the cache locks
  * held open will be released.
  *@param transactionID is the id of the transaction that is ending.
  */
  public void commitTransaction(String transactionID)
    throws ManifoldCFException;

  /** Roll back a cache transaction.
  * This method releases all objects cached against the ending transaction ID, and releases all locks
  * held for the transaction.
  *@param transactionID is the id of the transaction that is ending.
  */
  public void rollbackTransaction(String transactionID)
    throws ManifoldCFException;

  // This is a maintenance method; call it when convenient.

  /** Timed invalidation.  Call this periodically to get rid of all objects that have expired.
  *@param currentTimestamp is the current time in milliseconds since epoch.
  */
  public void expireObjects(long currentTimestamp)
    throws ManifoldCFException;

}
