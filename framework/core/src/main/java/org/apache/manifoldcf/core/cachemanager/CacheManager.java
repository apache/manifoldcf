/* $Id: CacheManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.cachemanager;

import org.apache.manifoldcf.core.interfaces.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.io.*;

/** This class implements the cache manager interface, and provides generic cache management
* services.  See the interface for a description of how the services work.  However, since this
* service requires the lock manager, and there is one lock manager per thread, there will
* be one of these service instances per thread as well.
*/
public class CacheManager implements ICacheManager
{
  public static final String _rcsid = "@(#)$Id: CacheManager.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String cacheLockPrefix = "_Cache_";

  protected ILockManager lockManager;
  protected static GeneralCache cache = new GeneralCache();

  // This is the hash mapping transaction id's to CacheTransactionHandle objects.
  // It is thread specific because transactions are thread local.
  protected HashMap transactionHash = new HashMap();

  public CacheManager(IThreadContext context)
    throws ManifoldCFException
  {
    lockManager = LockManagerFactory.make(context);
  }

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
  * It is perfectly legal to include the same object in both the locate set and the invalidate set!
  *
  * If an error occurs during object creation, the execute() method will NOT be called.  If the
  * execute() method has an error, the objects that were created will still be recorded in the cache.
  *
  * @param locateObjectDescriptions is a set of description objects that uniquely describe the objects needed.
  * @param invalidateKeys is the set of keys to invalidate at the end of the execution.
  * @param execObject is the cache execution object whose create() or execute() methods will
  * be called.
  * @param transactionID is the current transaction identifier, or null.  Objects created within this call
  * will be associated with this transaction; they will be purged from the cache should the transaction
  * be rolled back.
  */
  public void findObjectsAndExecute(ICacheDescription[] locateObjectDescriptions, StringSet invalidateKeys,
    ICacheExecutor execObject, String transactionID)
    throws ManifoldCFException
  {
    // There is some clever engineering involved here.  The lock manager is used to control synchronization
    // around usage and invalidation of objects.  However, there is ANOTHER lock condition that needs to be
    // managed as well in this code.  Specifically, in a single JVM, only one thread should be allowed to
    // create an object at a time.
    //
    // This is not a true "lock", because it doesn't cross JVM boundaries.  However, it is not brute-force
    // like a synchronizer either (a synchronizer would single-thread all object creation and usage).
    // Instead, we need to create a specific object for every object being located.  Then, a synchronizer
    // on that object can insure that only one thread per JVM is allowed to create a given object at a time,
    // and the others see an "existing" object instead.
    //
    // The basic logic is that the sync object is looked up from a global pool in a synchronized way, and if
    // found indicates that a creation is in progress on the corresponding object.  The thread must then wait
    // until the synchronizer object is released before continuing.  Since true locks have already been thrown,
    // it is guaranteed that the object cannot be destroyed, and the released threads can refind the object
    // and use it as "existing".  Basically, it's a runt version of the lock manager.

    ICacheHandle handle = enterCache(locateObjectDescriptions,invalidateKeys,transactionID);
    try
    {
      // Ok, we locked everything we need.
      // Now we need to create what is not yet there.
      // First, call create() or exists() on the "locate" objects (and also update the
      // LRU times)
      if (locateObjectDescriptions != null)
      {
        // We are going to go through the locate object list twice; once to see
        // what we need to create, and a second time after we do the creation.

        // Accumulate non-cached objects here.
        HashMap allObjects = new HashMap();
        int i;

        ICacheCreateHandle createHandle = enterCreateSection(handle);
        try
        {
          // Now, go through the objects again, and see which ones we need
          // to create.  The ones with null cache sets will always need creation.
          ArrayList createList = new ArrayList();
          i = 0;
          while (i < locateObjectDescriptions.length)
          {
            ICacheDescription objectDescription = locateObjectDescriptions[i++];
            StringSet set = objectDescription.getObjectKeys();
            if (set == null)
              createList.add(objectDescription);
            else
            {
              Object o = lookupObject(createHandle,objectDescription);
              if (o == null)
                createList.add(objectDescription);
              else
                allObjects.put(objectDescription,o);
            }
          }
          // Perform the create operation
          ICacheDescription[] createDescriptions = new ICacheDescription[createList.size()];
          i = 0;
          while (i < createList.size())
          {
            createDescriptions[i] = (ICacheDescription)createList.get(i);
            i++;
          }
          Object[] createdObjects = execObject.create(createDescriptions);
          if (createdObjects == null)
            return;
          // Loop through the returned objects, and enter them into the cache
          i = 0;
          while (i < createdObjects.length)
          {
            saveObject(createHandle,createDescriptions[i],createdObjects[i]);
            allObjects.put(createDescriptions[i],createdObjects[i]);
            i++;
          }
        }
        finally
        {
          leaveCreateSection(createHandle);
        }

        i = 0;
        while (i < locateObjectDescriptions.length)
        {
          ICacheDescription objectDescription = locateObjectDescriptions[i++];
          Object o = allObjects.get(objectDescription);
          // It already exists - send it to the executor
          execObject.exists(objectDescription,o);
        }
      }

      // Now, call the execute method
      if (execObject != null)
        execObject.execute();

      invalidateKeys(handle);
    }
    finally
    {
      leaveCache(handle);
    }
  }

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
    throws ManifoldCFException
  {
    if (Logging.cache.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      if (locateObjectDescriptions != null)
      {
        sb.append("{");
        int i = 0;
        while (i < locateObjectDescriptions.length)
        {
          if (i > 0)
            sb.append(",");
          sb.append(locateObjectDescriptions[i].getCriticalSectionName());
          i++;
        }
        sb.append("}");
      }
      else
        sb.append("NULL");

      StringBuilder inv = new StringBuilder();
      if (invalidateKeys != null)
      {
        inv.append("{");
        boolean isFirst = true;
        Iterator iter = invalidateKeys.getKeys();
        while (iter.hasNext())
        {
          if (isFirst)
            isFirst = false;
          else
            inv.append(",");
          inv.append((String)iter.next());
        }
        inv.append("}");
      }
      else
        inv.append("NULL");

      Logging.cache.debug("Entering cacher; objects = "+sb.toString()+" invalidate keys = "+inv.toString());
    }

    // First, throw read locks for the locate objects and write locks for invalidate objects.
    // To do the read locks, we need to go through the locate objects and get their cache keys.
    // This must be done prior to the object even being created.
    StringSetBuffer readLockTable = new StringSetBuffer();
    if (locateObjectDescriptions != null)
    {
      int i = 0;
      while (i < locateObjectDescriptions.length)
      {
        ICacheDescription objectDescription = locateObjectDescriptions[i++];
        // Get the keys
        StringSet keys = objectDescription.getObjectKeys();
        if (keys != null)
        {
          readLockTable.add(keys);
        }
      }
    }
    // Convert readLockTable to a StringSet
    StringSet readKeys = new StringSet(readLockTable);

    // The locks we will actually throw depend on whether or not we are in a transaction.  The
    // transaction filters the locks that are needed; locks are preserved within the transaction.
    // The cache handle itself also does not need the same data - in a transaction, readLocks and writeLocks
    // passed to the handle will be null (because we don't want them to be freed until the transaction exits).
    CacheHandle ch;
    if (transactionID == null)
    {
      String[] writeLocks = null;
      if (invalidateKeys != null)
        writeLocks = invalidateKeys.getArray(cacheLockPrefix);
      String[] readLocks = readKeys.getArray(cacheLockPrefix);
      ch = new CacheHandle(readLocks,writeLocks,locateObjectDescriptions,invalidateKeys,transactionID);
      Logging.lock.debug("Starting cache outside transaction");
      lockManager.enterLocks(readLocks,null,writeLocks);
      Logging.lock.debug(" Done starting cache");
    }
    else
    {
      CacheTransactionHandle handle = (CacheTransactionHandle)transactionHash.get(transactionID);
      if (handle == null)
      {
        ManifoldCFException ex = new ManifoldCFException("Illegal transaction ID: '"+transactionID+"'",ManifoldCFException.GENERAL_ERROR);
        Logging.cache.error(Thread.currentThread().toString()+": enterCache: "+transactionID+": "+this.toString()+": Transaction hash = "+transactionHash.toString(),ex);
        throw ex;
      }

      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug("Starting cache in transaction "+transactionID);

      ch = new CacheHandle(null,null,locateObjectDescriptions,invalidateKeys,transactionID);
      StringSet newReadLocks = handle.getRemainingReadLocks(readKeys,invalidateKeys);
      StringSet newWriteLocks = handle.getRemainingWriteLocks(readKeys,invalidateKeys);
      lockManager.enterLocks(newReadLocks.getArray(cacheLockPrefix),null,newWriteLocks.getArray(cacheLockPrefix));
      handle.addLocks(newReadLocks,newWriteLocks);
      // Logging.lock.debug(" Done starting cache");
    }

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Successfully entered cacher; handle = "+ch.toString());
    }

    return ch;
  }

  /** Enter a creation critical section.  This insures that only one thread is
  * creating the specified objects at a time.  This MUST be paired with
  * a leaveCreateSection() method call, whatever happens.
  *@param handle is the cache handle.
  */
  public ICacheCreateHandle enterCreateSection(ICacheHandle handle)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Entering cache create section; cache handle = "+handle.toString());
    }

    // Since we are inside the locks, any existing object cannot be invalidated;
    // however, it is possible that more than one thread can attempt to perform
    // object creation for the same object.  The only way to prevent this is to
    // throw a multiple critical section writelock on all the objects.  This will
    // stop multiple creation.
    ICacheDescription[] locateObjectDescriptions = handle.getObjectDescriptions();
    if (locateObjectDescriptions == null)
      throw new ManifoldCFException("Can't enter create section without objects to create",
      ManifoldCFException.GENERAL_ERROR);
    int i = 0;
    ArrayList writeCriticalSectionArray = new ArrayList();
    while (i < locateObjectDescriptions.length)
    {
      ICacheDescription objectDescription = locateObjectDescriptions[i++];
      StringSet set = objectDescription.getObjectKeys();

      // If this object is uncached, we do NOT try to synchronize creation, since it is
      // a waste of time to do that.  Multiple objects will need to be created anyhow.
      if (set != null)
        writeCriticalSectionArray.add(objectDescription.getCriticalSectionName());
    }
    // Make it into an array.
    String[] writeCriticalSections = new String[writeCriticalSectionArray.size()];
    i = 0;
    while (i < writeCriticalSectionArray.size())
    {
      writeCriticalSections[i] = (String)writeCriticalSectionArray.get(i);
      i++;
    }

    CacheCreateHandle ch = new CacheCreateHandle(writeCriticalSections,handle.getTransactionID());
    // Enter critical section (but only based on the objects we intend to cache).
    lockManager.enterCriticalSections(null,null,writeCriticalSections);

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Successfully entered cache create section for handle = "+
        handle.toString()+"; section handle = "+ch.toString());
    }

    return ch;
  }

  /** Lookup an object.  Returns null if object not found.  If it is found,
  * object's LRU and expiration info are updated.  The objectDescription passed
  * MUST be one of the ones specified in the enclosing enterCache() method.
  *@param handle is the handle to use for the create.
  *@param objectDescription is the description of the object to look up.
  *@return the object, or null if not found.
  */
  public Object lookupObject(ICacheCreateHandle handle, ICacheDescription objectDescription)
    throws ManifoldCFException
  {
    if (handle == null)
      throw new ManifoldCFException("Can't do lookup outside of create section",
      ManifoldCFException.GENERAL_ERROR);

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Looking up object in section handle = "+handle.toString()+
        "; object name = '"+objectDescription.getCriticalSectionName()+"'");
    }

    StringSet set = objectDescription.getObjectKeys();
    // If no cache keys, it can't be cached, so don't check!
    if (set == null)
      return null;

    // If this is in a transaction, we must look at the local cached copy first.
    // In fact, we walk back through the chain of parent transactions until we find it,
    // or until the cache keys are invalid against the transaction's invalidation keys.
    String transactionID = handle.getTransactionID();
    if (transactionID != null)
    {
      CacheTransactionHandle transactionHandle = (CacheTransactionHandle)transactionHash.get(transactionID);
      if (transactionHandle == null)
      {
        ManifoldCFException ex = new ManifoldCFException("Illegal transaction id",ManifoldCFException.GENERAL_ERROR);
        Logging.cache.error(Thread.currentThread().toString()+": lookupObject: "+transactionID+": "+this.toString()+": Transaction hash = "+transactionHash.toString(),ex);
        throw ex;
      }
      while (transactionHandle != null)
      {
        // Look for the object
        Object q = transactionHandle.lookupObject(objectDescription);
        if (q != null)
        {
          if (Logging.cache.isDebugEnabled())
          {
            Logging.cache.debug(" Object '"+objectDescription.getCriticalSectionName()+"' found in transaction cache");
          }
          return q;
        }
        // See if we can look at the parent
        if (transactionHandle.checkCacheKeys(set))
          return null;
        transactionHandle = transactionHandle.getParentTransaction();
      }
      // If nothing stops us, look in the global cache too
    }

    Object o = cache.lookup(objectDescription);
    if (o == null)
      return null;

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(" Object '"+objectDescription.getCriticalSectionName()+"' exists locally; checking if local copy is valid");
    }

    // See if the object's attached expiration is before the current time.
    long expireTime = cache.getObjectExpirationTime(objectDescription);
    if (expireTime != -1L && expireTime <= handle.getLookupTime())
    {
      // Blow away the entry in cache, since it has expired
      cache.deleteObject(objectDescription);
      return null;
    }
    
    // Before we conclude that the object is found, if we are on a multi-JVM environment we MUST check
    // the object's timestamp!!!  We check it against the invalidation key file timestamps for the object.
    long createTime = cache.getObjectCreationTime(objectDescription);
    StringSet keys = cache.getObjectInvalidationKeys(objectDescription);

    Iterator iter = keys.getKeys();
    while (iter.hasNext())
    {
      String key = (String)iter.next();
      if (hasExpired(key,createTime))
      {
        // Blow away the entry in cache, since it has expired
        cache.deleteObject(objectDescription);
        return null;
      }
    }

    // System.out.println("Found object: "+objectDescription.getCriticalSectionName());

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(" Object '"+objectDescription.getCriticalSectionName()+"' is valid; resetting local expiration");
    }

    // Update the expiration time for this object.
    resetObjectExpiration(objectDescription,handle.getLookupTime());

    return o;
  }

  /** Check if object has expired (by looking at file system).
  *@param key is the invalidation key.
  *@param createTime is the creation time.
  *@return true if expired, false otherwise.
  */
  protected boolean hasExpired(String key, long createTime)
    throws ManifoldCFException
  {
    long createdDate = readSharedData(key);
    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(" Checking whether our cached copy of object with key = "+key+" has been invalidated.  It has create time "+new Long(createTime).toString()+", and the last change is "+new Long(createdDate).toString());
    }
    if (createdDate == 0L)
      return false;
    return createdDate >= createTime;
  }

  /** Set object's expiration and LRU.
  *@param objectDescription is the description object.
  *@param currentTime is the current time in milliseconds since epoch.
  */
  protected void resetObjectExpiration(ICacheDescription objectDescription, long currentTime)
  {

    // Update the expiration time for this object.
    long expireInterval = objectDescription.getObjectExpirationTime(currentTime);
    cache.setObjectExpiration(objectDescription,expireInterval);

    // Update LRU and max counts.  This also flushes the cache to meet the criteria.
    ICacheClass objectClass = objectDescription.getObjectClass();
    if (objectClass != null)
      cache.setObjectClass(objectDescription,objectClass.getClassName(),objectClass.getMaxLRUCount());
    else
      cache.setObjectClass(objectDescription,null,Integer.MAX_VALUE);

  }

  /** Save a newly created object.  The object MUST be one of those identified in the
  * enterCache() method.
  *@param handle is the create handle.
  *@param objectDescription is the object description.
  *@param object is the object.
  */
  public void saveObject(ICacheCreateHandle handle, ICacheDescription objectDescription,
    Object object)
    throws ManifoldCFException
  {
    if (handle == null)
      throw new ManifoldCFException("Can't do save outside of create section",
      ManifoldCFException.GENERAL_ERROR);

    if (Logging.cache.isDebugEnabled())
    {
      StringSet ks = objectDescription.getObjectKeys();
      StringBuilder sb = new StringBuilder();
      if (ks != null)
      {
        sb.append("{");
        Iterator iter = ks.getKeys();
        boolean isFirst = true;
        while (iter.hasNext())
        {
          if (isFirst)
            isFirst = false;
          else
            sb.append(",");
          sb.append((String)iter.next());
        }
        sb.append("}");
      }
      else
        sb.append("NULL");

      Logging.cache.debug("Saving new object in section handle = "+handle.toString()+
        "; object description = '"+objectDescription.getCriticalSectionName()+
        "; cache keys = "+sb.toString());
    }


    StringSet keys = objectDescription.getObjectKeys();
    if (keys != null)
    {
      String transactionID = handle.getTransactionID();
      if (transactionID == null)
      {
        cache.setObject(objectDescription,object,keys,handle.getLookupTime());
        // Update the expiration time for this object.
        resetObjectExpiration(objectDescription,handle.getLookupTime());
      }
      else
      {
        // Put it into the transaction object
        // Expiration and LRU don't count here; they get applied when the object goes into
        // the global cache.
        CacheTransactionHandle transactionHandle = (CacheTransactionHandle)transactionHash.get(transactionID);
        if (transactionHandle == null)
        {
          ManifoldCFException ex = new ManifoldCFException("Bad transaction handle",ManifoldCFException.GENERAL_ERROR);
          Logging.cache.error(Thread.currentThread().toString()+": saveObject: "+transactionID+": "+this.toString()+": Transaction hash = "+transactionHash.toString(),ex);
          throw ex;
        }
        transactionHandle.saveObject(objectDescription,object,keys);
      }
    }
  }

  /** Leave the create section.
  *@param handle is the handle created by the corresponding enterCreateSection() method.
  */
  public void leaveCreateSection(ICacheCreateHandle handle)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Leaving cache create section; section handle = "+handle.toString());
    }

    lockManager.leaveCriticalSections(null,null,handle.getCriticalSectionNames());
  }

  /** Invalidate keys.  The keys invalidated are what got passed to the enterCache() method.
  *@param handle is the cache handle.  Does nothing if a null set of keys was passed in.
  */
  public void invalidateKeys(ICacheHandle handle)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Invalidating keys; handle = "+handle.toString());
    }


    StringSet invalidateKeys = handle.getInvalidationKeys();
    // Get the transaction handle in effect
    String transactionID = handle.getTransactionID();
    if (transactionID == null)
    {
      performInvalidation(invalidateKeys);
    }
    else
    {
      CacheTransactionHandle transactionHandle = (CacheTransactionHandle)transactionHash.get(transactionID);
      if (transactionHandle == null)
      {
        ManifoldCFException ex = new ManifoldCFException("Bad transaction ID!",ManifoldCFException.GENERAL_ERROR);
        Logging.cache.error(Thread.currentThread().toString()+": invalidateKeys: "+transactionID+": "+this.toString()+": Transaction hash = "+transactionHash.toString(),ex);
        throw ex;
      }
      // Do the invalidation right in the transaction handle.
      transactionHandle.invalidateKeys(invalidateKeys);
    }
  }

  /** Perform an invalidation.  Assume all appropriate locks are in place.
  *@param keys is the set of keys to invalidate.
  */
  protected void performInvalidation(StringSet keys)
    throws ManifoldCFException
  {

    // Finally, perform the invalidation.

    if (keys != null)
    {
      long invalidationTime = System.currentTimeMillis();
      // Loop through all keys
      Iterator iter = keys.getKeys();
      while (iter.hasNext())
      {
        String keyName = (String)iter.next();
        if (Logging.cache.isDebugEnabled())
          Logging.cache.debug(" Invalidating key = "+keyName+" as of time = "+new Long(invalidationTime).toString());
        writeSharedData(keyName,invalidationTime);
      }

      cache.invalidateKeys(keys);
    }
  }

  /** Leave the cache.  Must be paired with enterCache, above.
  *@param handle is the handle of the cache we are leaving.
  */
  public void leaveCache(ICacheHandle handle)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Leaving cacher; handle = "+handle.toString());
    }

    lockManager.leaveLocks(handle.getReadLockStrings(),null,handle.getWriteLockStrings());
  }

  // The following methods are used to communicate transaction information to the cache.

  /** Begin a cache transaction.
  * This keeps track of the relationship between objects cached within transactions.
  *@param startingTransactionID is the id of the transaction that is starting.
  *@param enclosingTransactionID is the id of the transaction that is in effect, or null.
  */
  public void startTransaction(String startingTransactionID, String enclosingTransactionID)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(Thread.currentThread().toString()+": Starting transaction: "+startingTransactionID+": "+this.toString()+": "+transactionHash.toString());
    }

    CacheTransactionHandle parent;
    if (enclosingTransactionID == null)
      parent = null;
    else
    {
      parent = (CacheTransactionHandle)transactionHash.get(enclosingTransactionID);
      if (parent == null)
      {
        ManifoldCFException ex = new ManifoldCFException("Illegal parent transaction ID: "+enclosingTransactionID,
          ManifoldCFException.GENERAL_ERROR);
        Logging.cache.error(Thread.currentThread().toString()+": startTransaction: "+this.toString()+": Transaction hash = "+transactionHash.toString(),ex);
        throw ex;
      }
    }

    transactionHash.put(startingTransactionID,new CacheTransactionHandle(parent));

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug("Successfully created transaction: "+startingTransactionID+": "+this.toString()+": "+transactionHash.toString());
    }

  }

  /** Commit a cache transaction.
  * This method MUST be called when a transaction successfully ends, or open locks will not be closed!!!
  * All cache activity that has taken place inside the transaction will be resolved, and the cache locks
  * held open will be released.
  */
  public void commitTransaction(String transactionID)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(Thread.currentThread().toString()+": Committing transaction: "+transactionID+": "+this.toString()+": "+transactionHash.toString());
    }

    CacheTransactionHandle handle = (CacheTransactionHandle)transactionHash.get(transactionID);
    if (handle == null)
      throw new ManifoldCFException("Cache manager: commit transaction without start!",ManifoldCFException.GENERAL_ERROR);

    // First, move all the locally cached entries into the global cache.
    // This is safe to do because we know that the transaction belongs to a single thread.
    CacheTransactionHandle parentTransaction = handle.getParentTransaction();
    Iterator iter = handle.getCurrentObjects();
    StringSet invalidationKeys = handle.getInvalidationKeys();
    if (parentTransaction == null)
    {
      // It will be folded into the main cache!
      // First, trigger invalidation.
      performInvalidation(invalidationKeys);
      long currentTime = System.currentTimeMillis();
      // Now, copy the objects into the main cache
      while (iter.hasNext())
      {
        ICacheDescription desc = (ICacheDescription)iter.next();
        Object o = handle.lookupObject(desc);
        // System.out.println("Moving object to main cache: "+desc.getCriticalSectionName());
        cache.setObject(desc,o,desc.getObjectKeys(),currentTime);
        // Now, set expiration and LRU
        // Update the expiration time for this object.
        resetObjectExpiration(desc,currentTime);
      }
      // End all of the locks.
      // We always end the write locks before the read locks.

      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug("Ending transaction write locks for transaction "+transactionID);

      lockManager.leaveLocks(null,null,handle.getWriteLocks().getArray(cacheLockPrefix));

      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug("Ending transaction read locks for transaction "+transactionID);

      lockManager.leaveLocks(handle.getReadLocks().getArray(cacheLockPrefix),null,null);

      if (Logging.lock.isDebugEnabled())
        Logging.lock.debug("Done ending "+transactionID);

    }
    else
    {
      // Copy the invalidation etc. into the wrapping transaction.
      // Invalidate the parent cache entries
      parentTransaction.invalidateKeys(invalidationKeys);
      // Copy the objects from child to parent
      while (iter.hasNext())
      {
        ICacheDescription desc = (ICacheDescription)iter.next();
        Object o = handle.lookupObject(desc);
        parentTransaction.saveObject(desc,o,desc.getObjectKeys());
      }
      // Transfer all locks to parent from child
      parentTransaction.addLocks(handle.getReadLocks(),handle.getWriteLocks());
    }

    // Finally, remove the transaction from the hash.
    transactionHash.remove(transactionID);
  }

  /** Roll back a cache transaction.
  * This method releases all objects cached against the ending transaction ID, and releases all locks
  * held for the transaction.
  */
  public void rollbackTransaction(String transactionID)
    throws ManifoldCFException
  {

    if (Logging.cache.isDebugEnabled())
    {
      Logging.cache.debug(Thread.currentThread().toString()+": Rolling back transaction: "+transactionID+": "+this.toString()+": "+transactionHash.toString());
    }

    CacheTransactionHandle handle = (CacheTransactionHandle)transactionHash.get(transactionID);
    if (handle == null)
      throw new ManifoldCFException("Cache manager: rollback transaction without start!",ManifoldCFException.GENERAL_ERROR);

    // End all of the locks
    // We always end the write locks before the read locks.

    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Ending rollback write locks for transaction "+transactionID);

    lockManager.leaveLocks(null,null,handle.getWriteLocks().getArray(cacheLockPrefix));

    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Ending rollback read locks for transaction "+transactionID);

    lockManager.leaveLocks(handle.getReadLocks().getArray(cacheLockPrefix),null,null);

    if (Logging.lock.isDebugEnabled())
      Logging.lock.debug("Done rolling back "+transactionID);


    // Now, get rid of the transaction entry, because we just want to chuck what we filed there.
    transactionHash.remove(transactionID);
  }

  /** Timed invalidation.  Call this periodically to get rid of all objects that have expired.
  *@param currentTimestamp is the current time in milliseconds since epoch.
  */
  public void expireObjects(long currentTimestamp)
    throws ManifoldCFException
  {
    // This is a local JVM operation; we will not need to do any locks.  We just
    // need to blow expired objects from the cache.
    cache.expireRecords(currentTimestamp);
  }


  // Protected methods and classes

  /** Read an invalidation file contents.
  *@param key is the cache key name.
  *@return the invalidation time, or 0 if none.
  */
  protected long readSharedData(String key)
    throws ManifoldCFException
  {
    // Read cache resource
    byte[] cacheResourceData = lockManager.readData("cache-"+key);
    if (cacheResourceData == null)
      return 0L;

    String expiration = new String(cacheResourceData, StandardCharsets.UTF_8);
    return new Long(expiration).longValue();


  }

  /** Write the invalidation file contents.
  *@param key is the cache key name.
  *@param value is the invalidation timestamp.
  */
  protected void writeSharedData(String key, long value)
    throws ManifoldCFException
  {
    if (value == 0L)
      lockManager.writeData(key,null);
    else
    {
      lockManager.writeData("cache-"+key,Long.toString(value).getBytes(StandardCharsets.UTF_8));
    }
  }


  /** This is the local implementation of ICacheHandle.
  */
  protected class CacheHandle implements ICacheHandle
  {
    // Member variables
    protected String[] readLocks;
    protected String[] writeLocks;
    protected ICacheDescription[] objectDescriptions;
    protected StringSet invalidationKeys;
    protected String transactionID;

    public CacheHandle(String[] readLocks, String[] writeLocks, ICacheDescription[] descriptions,
      StringSet invalidationKeys, String transactionID)
    {
      this.readLocks = readLocks;
      this.writeLocks = writeLocks;
      this.objectDescriptions = descriptions;
      this.invalidationKeys = invalidationKeys;
      this.transactionID = transactionID;
    }

    /** Get the read lock strings.
    *@return read lock string array.
    */
    public String[] getReadLockStrings()
    {
      return readLocks;
    }

    /** Get the write lock strings.
    *@return write lock string array.
    */
    public String[] getWriteLockStrings()
    {
      return writeLocks;
    }

    /** Get the set of object descriptions.
    *@return the object descriptions.
    */
    public ICacheDescription[] getObjectDescriptions()
    {
      return objectDescriptions;
    }

    /** Get the invalidation keys.
    *@return the invalidation key set.
    */
    public StringSet getInvalidationKeys()
    {
      return invalidationKeys;
    }

    /** Get the transaction ID.
    *@return the transaction ID.
    */
    public String getTransactionID()
    {
      return transactionID;
    }
  }

  /** This is the local implementation of ICacheCreateHandle
  */
  protected class CacheCreateHandle implements ICacheCreateHandle
  {
    // Member variables
    protected String[] criticalSectionNames;
    protected long theTime;
    protected String transactionID;

    public CacheCreateHandle(String[] criticalSectionNames, String transactionID)
    {
      this.criticalSectionNames = criticalSectionNames;
      theTime = System.currentTimeMillis();
      this.transactionID = transactionID;
    }

    /** Get the critical section names.
    *@return the critical section names.
    */
    public String[] getCriticalSectionNames()
    {
      return criticalSectionNames;
    }

    /** Get create start time.
    *@return the time in milliseconds since epoch.
    */
    public long getLookupTime()
    {
      return theTime;
    }

    /** Get the transaction ID.
    *@return the transaction ID.
    */
    public String getTransactionID()
    {
      return transactionID;
    }

  }

  /** This is the class the cache manager uses to keep track of transaction
  * relationships and data.  Since a transaction is local to a thread, this
  * class is local to a specific thread as well; it is thus referenced by a thread-local
  * hash table.
  */
  protected class CacheTransactionHandle
  {
    // Member variables
    protected CacheTransactionHandle parentTransaction;

    // The cache in here must also be flushable via cache keys; the
    // objects are therefore in a cache key section as well as in a "lookup" section.

    /** This is the object hash (key is the description object, value is the stored object).
    */
    protected HashMap objectHash = new HashMap();

    /** This is the cache key map (key is the cache key, value is a hashmap containing the
    * object descriptions to be invalidated).
    */
    protected HashMap cacheKeyMap = new HashMap();

    /** This is the set of cache key read locks that are currently held by this transaction.
    */
    protected HashMap cacheKeyReadLocks = new HashMap();

    /** This is the set of cache key write locks that are currently held by this transaction.
    */
    protected HashMap cacheKeyWriteLocks = new HashMap();

    /** This is the current set of keys to invalidate if the transaction should be committed.
    */
    protected StringSetBuffer invalidationKeys = new StringSetBuffer();

    /** Constructor.
    *@param parentTransaction is the parent transaction identifier, or null if none.
    */
    public CacheTransactionHandle(CacheTransactionHandle parentTransaction)
    {
      this.parentTransaction = parentTransaction;
    }

    /** Get parent transaction.
    *@return the parent transaction.
    */
    public CacheTransactionHandle getParentTransaction()
    {
      return parentTransaction;
    }

    /** Get the set of write locks to close on exit from this transaction.
    *@return the write lock names as an array.
    */
    public StringSet getWriteLocks()
    {
      return new StringSet(cacheKeyWriteLocks);
    }

    /** Get the set of read locks to close on exit from this transaction.
    *@return the read lock names as an array.
    */
    public StringSet getReadLocks()
    {
      return new StringSet(cacheKeyReadLocks);
    }

    /** Get the current invalidation keys.
    *@return the keys as a stringset
    */
    public StringSet getInvalidationKeys()
    {
      return new StringSet(invalidationKeys);
    }

    /** Look for an object in cache.
    *@param descriptionObject is the cache description object.
    *@return the object, if found, or null if not found.
    */
    public Object lookupObject(ICacheDescription descriptionObject)
    {
      return objectHash.get(descriptionObject);
    }

    /** Save an object in cache.
    *@param descriptionObject is the description.
    *@param object is the object to save.
    *@param cacheKeys are the cache keys.
    */
    public void saveObject(ICacheDescription descriptionObject, Object object,
      StringSet cacheKeys)
    {
      // Put into main hash
      objectHash.put(descriptionObject,object);
      // Put into the cache key hashes
      Iterator iter = cacheKeys.getKeys();
      while (iter.hasNext())
      {
        String key = (String)iter.next();
        HashMap thisHash = (HashMap)cacheKeyMap.get(key);
        if (thisHash == null)
        {
          thisHash = new HashMap();
          cacheKeyMap.put(key,thisHash);
        }
        thisHash.put(descriptionObject,descriptionObject);
      }
    }

    /** Invalidate objects.
    *@param keys is the set of keys to invalidate.
    */
    public void invalidateKeys(StringSet keys)
    {
      if (keys == null)
        return;

      // Merge these keys into the invalidation set
      invalidationKeys.add(keys);
      // Now, bump stuff from the cache
      Iterator iter = keys.getKeys();
      while (iter.hasNext())
      {
        String keyName = (String)iter.next();
        HashMap x = (HashMap)cacheKeyMap.get(keyName);
        if (x != null)
        {
          // Need to flush the stated items
          Iterator iter2 = x.keySet().iterator();
          while (iter2.hasNext())
          {
            ICacheDescription desc = (ICacheDescription)iter2.next();
            objectHash.remove(desc);
          }
          cacheKeyMap.remove(keyName);
        }
      }
    }

    /** See if cache keys intersect with invalidation keys.
    *@param cacheKeys is the set of cache keys that describe an object.
    *@return true if these keys have been invalidated in this transaction
    */
    public boolean checkCacheKeys(StringSet cacheKeys)
    {
      return invalidationKeys.contains(cacheKeys);
    }

    /** Come up with the set of read locks we still need to throw.
    *@param cacheKeys is the set of cache keys we need to have read locks for.
    *@param keys is the set of invalidation keys we need to have write locks for.
    *@return the array of read locks we still need to throw.
    */
    public StringSet getRemainingReadLocks(StringSet cacheKeys, StringSet keys)
    {
      StringSetBuffer accumulator = new StringSetBuffer();
      if (cacheKeys != null)
      {
        Iterator iter = cacheKeys.getKeys();
        while (iter.hasNext())
        {
          String cacheKey = (String)iter.next();
          // If this already has a lock, of any kind, continue
          if (keys != null && keys.contains(cacheKey))
            continue;
          // Look back through the whole parent chain to see whether we need to throw
          // this lock.
          CacheTransactionHandle ptr = this;
          boolean found = false;
          while (ptr != null)
          {
            if (ptr.cacheKeyReadLocks.get(cacheKey) != null ||
              ptr.cacheKeyWriteLocks.get(cacheKey) != null)
            {
              found = true;
              break;
            }
            ptr = ptr.parentTransaction;
          }
          if (found)
            continue;
          accumulator.add(cacheKey);
        }
      }
      return new StringSet(accumulator);
    }

    /** Come up with the set of write locks we still need to throw.
    *@param cacheKeys is the set of cache keys we need to have read locks for.
    *@param keys is the set of invalidation keys we need to have write locks for.
    *@return the array of write locks we still need to throw.
    */
    public StringSet getRemainingWriteLocks(StringSet cacheKeys, StringSet keys)
      throws ManifoldCFException
    {
      // If any of these keys are read locks but not yet write locks, we throw an exception!
      // (There is currently no ability to promote a read lock to a write lock.)
      StringSetBuffer accumulator = new StringSetBuffer();
      if (keys != null)
      {
        Iterator iter = keys.getKeys();
        while (iter.hasNext())
        {
          String invKey = (String)iter.next();
          // Look back through the whole parent chain to see whether we need to throw
          // this lock.
          CacheTransactionHandle ptr = this;
          boolean found = false;
          while (ptr != null)
          {
            // Write check first!  Then, read check.
            if (ptr.cacheKeyWriteLocks.get(invKey) != null)
            {
              found = true;
              break;
            }
            // Upgrade lock attempts are now permitted.
            if (ptr.cacheKeyReadLocks.get(invKey) != null)
              break;
            ptr = ptr.parentTransaction;
          }
          if (found)
            continue;
          accumulator.add(invKey);
        }
      }
      return new StringSet(accumulator);
    }

    /** Add to the set of locks that are open.
    *@param thrownReadLocks is the set of read locks.
    *@param thrownWriteLocks is the set of write locks.
    */
    public void addLocks(StringSet thrownReadLocks, StringSet thrownWriteLocks)
    {
      // Some of the write locks will be upgrades.  In this case, the same lock
      // might appear BOTH as read and as write.  I hope the lock manager does the
      // right thing in this case!
      // In any case, at worst we will have one write lock and one read lock for the
      // same lock name, and never a write lock then a read lock, so we should be able to disambiguate if
      // needed.

      Iterator iter = thrownReadLocks.getKeys();
      while (iter.hasNext())
      {
        String x = (String)iter.next();
        cacheKeyReadLocks.put(x,x);
      }
      iter = thrownWriteLocks.getKeys();
      while (iter.hasNext())
      {
        String x = (String)iter.next();
        cacheKeyWriteLocks.put(x,x);
      }
    }

    /** Get all existing object descriptions.
    *@return an iterator of ICacheDescription objects.
    */
    public Iterator getCurrentObjects()
    {
      return objectHash.keySet().iterator();
    }

  }


}
