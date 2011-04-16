/* $Id: GeneralCache.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** General cache class.  This class will be statically instantiated.  It contains all the structures
* needed to maintain a cache of objects, with both LRU flushing behavior, and timed expiration of
* objects.
* This cache is entirely local to a JVM and does NOT have any locking and synchronization semantics
* cross-JVM.  That is handled at a higher level.
*/
public class GeneralCache
{
  public static final String _rcsid = "@(#)$Id: GeneralCache.java 988245 2010-08-23 18:39:35Z kwright $";

  // This table is for looking stuff up by object description
  protected ObjectRecordTable hashtable = new ObjectRecordTable();
  // This table is for looking stuff up by cache key - hash table of hash tables
  protected InvalidationTable invalidationTable = new InvalidationTable();
  // This table keeps the running count of each object class
  protected ObjectClassTable objectClassTable = new ObjectClassTable();
  // This structure is the general expiration tree
  public ExpirationTree expirationTree = new ExpirationTree();

  public GeneralCache()
  {
  }

  /** Locate an object in the cache, and return it if found.
  *@param objectDescription is the object's unique identifier.
  *@return the object if found, or null if not present in the cache.
  */
  public synchronized Object lookup(Object objectDescription)
  {
    ObjectRecord o = hashtable.lookup(objectDescription);
    if (o == null)
      return null;
    return o.getObject();
  }

  /** Get the creation time of an object in the cache.
  *@param objectDescription is the object's unique identifier.
  *@return the creation time, or -1 if object not found.
  */
  public synchronized long getObjectCreationTime(Object objectDescription)
  {
    ObjectRecord o = hashtable.lookup(objectDescription);
    if (o == null)
      return -1L;
    return o.getCreationTime();
  }

  /** Get the invalidation keys for an object in the cache.
  *@param objectDescription is the object's unique identifier.
  *@return the keys, or null if not found.
  */
  public synchronized StringSet getObjectInvalidationKeys(Object objectDescription)
  {
    ObjectRecord o = hashtable.lookup(objectDescription);
    if (o == null)
      return null;
    return o.getKeys();
  }

  /** Get the expiration time for an object in the cache.
  *@param objectDescription is the object's unique identifier.
  *@return the expiration time (-1L means none).
  */
  public synchronized long getObjectExpirationTime(Object objectDescription)
  {
    ObjectRecord o = hashtable.lookup(objectDescription);
    if (o == null)
      return -1L;
    return o.getObjectExpiration();
  }

  /** Delete a record from the cache.
  *@param objectDescription is the unique description.
  */
  public synchronized void deleteObject(Object objectDescription)
  {
    ObjectRecord o = hashtable.lookup(objectDescription);
    if (o != null)
      deleteEntry(o);
  }

  /** Add a newly created object to the cache.  Use ONLY for newly created objects!
  *@param objectDescription is the newly created object's unique description.
  *@param object is the newly created object itself.
  *@param keys are the invalidation keys for the newly created object.
  *@param timestamp is the creation timestamp for this object (used for cross-JVM invalidation)
  */
  public synchronized void setObject(Object objectDescription, Object object, StringSet keys, long timestamp)
  {
    ObjectRecord record = new ObjectRecord(objectDescription,object,keys,timestamp);
    hashtable.add(record);
    // Make an entry in the invalidation hash
    invalidationTable.addKeys(keys,record);
    // Object has no expiration or class yet, so don't add it to the expiration tree, or to the object
    // class trees
  }

  /** Set an object's expiration time.
  *@param objectDescription is the object's unique description.
  *@param expirationTime is the object's new expiration time, in milliseconds since epoch.
  */
  public synchronized void setObjectExpiration(Object objectDescription, long expirationTime)
  {
    // Find existing object
    ObjectRecord existing = hashtable.lookup(objectDescription);
    if (existing == null)
      return;
    if (existing.getObjectExpiration() != -1)
    {
      // Pull the object from the expiration tree
      expirationTree.removeEntry(existing);
    }
    // Set the new expiration
    existing.setObjectExpiration(expirationTime);
    if (expirationTime != -1)
    {
      //Put the object back into the expiration tree
      expirationTree.addEntry(existing);
    }
  }

  /** Set an object's class and maximum count.  This will clean up extra objects
  * in a Least Recently Used fashion until the count is met.
  *@param objectDescription is the object's unique description.
  *@param objectClass is the object's "class", or grouping for the purposes of LRU.
  *@param maxCount is the maximum number of objects of the class to permit to
  * remain in the cache.
  */
  public synchronized void setObjectClass(Object objectDescription, String objectClass,
    int maxCount)
  {
    // Lookup the existing object class
    ObjectRecord existing = hashtable.lookup(objectDescription);
    if (existing == null)
      return;
    if (existing.getObjectClass() != null)
    {
      // Pull the object from the object class expiration tree
      objectClassTable.removeEntry(existing);
    }
    // Set the new object class & LRU value
    existing.setObjectClass(objectClass);
    if (objectClass != null)
    {
      // Put the object into the object class expiration tree
      objectClassTable.addEntry(existing);

      if (maxCount >= 0)
      {
        // Now, clean up objects to meet the count
        while (objectClassTable.getCurrentMemberCount(objectClass) > maxCount)
        {
          ObjectRecord oldestRecord = objectClassTable.getOldestEntry(objectClass);
          // Delete this entry from all places it lives
          deleteEntry(oldestRecord);
        }

      }
    }

  }

  /** Invalidate a set of keys.  This causes all objects that have any of the specified
  * keys as invalidation keys to be removed from the cache.
  *@param keys is the StringSet describing the keys to invalidate.
  */
  public synchronized void invalidateKeys(StringSet keys)
  {
    Iterator enum2 = keys.getKeys();
    while (enum2.hasNext())
    {
      String invalidateKey = (String)enum2.next();
      Iterator enum1 = invalidationTable.getObjectRecordsForKey(invalidateKey);
      while (enum1.hasNext())
      {
        ObjectRecord record = (ObjectRecord)enum1.next();
        hashtable.remove(record);
        // Remove from object class table
        if (record.getObjectClass() != null)
        {
          objectClassTable.removeEntry(record);
        }
        // Remove from expiration table
        if (record.getExpirationTime() >= 0)
        {
          expirationTree.removeEntry(record);
        }

      }
      // We do this last, because we are enumerating over something in here!
      invalidationTable.removeKey(invalidateKey);
    }
  }

  /** Expire all records that have older expiration times than that passed in.
  * @param expireTime is the time to compare against, in milliseconds since epoch.
  */
  public void expireRecords(long expireTime)
  {
    while (true)
    {
      // Do the synchronizer inside the loop.  Cleanup is slower,
      // but the cache does not get locked for long periods.
      synchronized (this)
      {
        // Get the oldest record, if any
        ObjectRecord x = expirationTree.getOldestEntry();
        if (x == null)
          break;
        if (x.getExpirationTime() > expireTime)
          break;
        // Remove the entry
        deleteEntry(x);
      }
    }
  }

  /** Delete a record from the cache. NOTE WELL: This method cannot be used
  * if the data associated with the record is currently being processed with
  * an enumeration (for example), since it modifies the structures that the
  * enumeration is based on!
  *@param record is the object record.
  */
  protected void deleteEntry(ObjectRecord record)
  {
    // Delete from the main cache
    hashtable.remove(record);
    // Delete from key hash
    invalidationTable.removeObjectRecord(record);
    // Remove from object class table
    if (record.getObjectClass() != null)
    {
      objectClassTable.removeEntry(record);
    }
    // Remove from expiration table
    if (record.getExpirationTime() >= 0)
    {
      expirationTree.removeEntry(record);
    }

  }

  /** This class represents a cached object.  It has enough hooks to allow it
  * to live in all the various data structures the general cache maintains.
  */
  protected class ObjectRecord
  {
    protected Object objectDescription;
    protected Object theObject;
    protected StringSet invalidationKeys;
    protected long creationTime;
    protected long expirationTime = -1;
    protected String objectClass = null;
    protected ObjectRecord prevLRU = null;
    protected ObjectRecord nextLRU = null;
    protected ObjectRecord sameExpirationPrev = null;
    protected ObjectRecord sameExpirationNext = null;

    public ObjectRecord(Object objectDescription, Object theObject, StringSet invalidationKeys, long creationTime)
    {
      this.creationTime = creationTime;
      this.objectDescription = objectDescription;
      this.theObject = theObject;
      this.invalidationKeys = invalidationKeys;
    }

    public long getCreationTime()
    {
      return creationTime;
    }

    public void setSameExpirationPrev(ObjectRecord x)
    {
      sameExpirationPrev = x;
    }

    public ObjectRecord getSameExpirationPrev()
    {
      return sameExpirationPrev;
    }

    public void setSameExpirationNext(ObjectRecord x)
    {
      sameExpirationNext = x;
    }

    public ObjectRecord getSameExpirationNext()
    {
      return sameExpirationNext;
    }

    public void setObjectExpiration(long expTime)
    {
      expirationTime = expTime;
    }

    public long getObjectExpiration()
    {
      return expirationTime;
    }

    public Object getObjectDescription()
    {
      return objectDescription;
    }

    public void setObjectClass(String className)
    {
      objectClass = className;
    }

    public String getObjectClass()
    {
      return objectClass;
    }

    public ObjectRecord getPrevLRU()
    {
      return prevLRU;
    }

    public ObjectRecord getNextLRU()
    {
      return nextLRU;
    }

    public void setPrevLRU(ObjectRecord prev)
    {
      prevLRU = prev;
    }

    public void setNextLRU(ObjectRecord next)
    {
      nextLRU = next;
    }

    public Object getObject()
    {
      return theObject;
    }

    public StringSet getKeys()
    {
      return invalidationKeys;
    }

    public long getExpirationTime()
    {
      return expirationTime;
    }

    public int hashCode()
    {
      return objectDescription.hashCode();
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof ObjectRecord))
        return false;
      ObjectRecord record = (ObjectRecord)o;
      return objectDescription.equals(record.objectDescription);
    }
  }

  /** This class describes a table of object records, looked up
  * by the unique object description.
  */
  protected class ObjectRecordTable
  {
    protected HashMap hashtable = new HashMap();

    public ObjectRecordTable()
    {
    }

    public void add(ObjectRecord record)
    {
      hashtable.put(record.getObjectDescription(),record);
    }

    public void remove(Object objectDescription)
    {
      hashtable.remove(objectDescription);
    }

    public void remove(ObjectRecord record)
    {
      hashtable.remove(record.getObjectDescription());
    }

    public ObjectRecord lookup(Object objectDescription)
    {
      return (ObjectRecord)hashtable.get(objectDescription);
    }
  }

  /** This class describes a table of invalidation keys, each of which points
  * to a set of object records.
  */
  protected class InvalidationTable
  {
    protected HashMap hashtable = new HashMap();

    public InvalidationTable()
    {
    }

    public void addKeys(StringSet keyset, ObjectRecord objectRecord)
    {
      Iterator enum1 = keyset.getKeys();
      while (enum1.hasNext())
      {
        String key = (String)enum1.next();
        HashMap ht = (HashMap)hashtable.get(key);
        if (ht == null)
        {
          ht = new HashMap();
          hashtable.put(key,ht);
        }
        ht.put(objectRecord,objectRecord);
      }
    }

    public Iterator getObjectRecordsForKey(String key)
    {
      HashMap ht = (HashMap)hashtable.get(key);
      if (ht == null)
      {
        ht = new HashMap();
        hashtable.put(key,ht);
      }
      return ht.keySet().iterator();
    }

    public void removeKey(String key)
    {
      hashtable.remove(key);
    }

    public void removeObjectRecord(ObjectRecord record)
    {
      // Get the keys
      StringSet keys = record.getKeys();
      Iterator enum1 = keys.getKeys();
      while (enum1.hasNext())
      {
        String key = (String)enum1.next();
        removeObjectRecordFromKey(key,record);
      }
    }

    public void removeObjectRecordFromKey(String key, ObjectRecord objectRecord)
    {
      HashMap ht = (HashMap)hashtable.get(key);
      if (ht == null)
        return;
      ht.remove(objectRecord);
    }

  }

  /** This class describes a set of object classes, each with its own LRU behavior.
  */
  protected class ObjectClassTable
  {
    protected HashMap hashtable = new HashMap();

    public ObjectClassTable()
    {
    }

    /** Call ONLY if there is no existing record in the object class table for this record
    */
    public void addEntry(ObjectRecord record)
    {
      ObjectClassRecord x = (ObjectClassRecord)hashtable.get(record.getObjectClass());
      if (x == null)
      {
        x = new ObjectClassRecord();
        hashtable.put(record.getObjectClass(),x);
      }
      x.addEntry(record);
    }

    /** Call ONLY if there is known to be an existing record in the object class table
    */
    public void removeEntry(ObjectRecord record)
    {
      ObjectClassRecord x = (ObjectClassRecord)hashtable.get(record.getObjectClass());
      if (x == null)
        return;
      x.removeEntry(record);
    }

    public int getCurrentMemberCount(String objectClassName)
    {
      ObjectClassRecord x = (ObjectClassRecord)hashtable.get(objectClassName);
      if (x == null)
        return 0;
      return x.getCurrentMemberCount();
    }

    public ObjectRecord getOldestEntry(String objectClassName)
    {
      ObjectClassRecord x = (ObjectClassRecord)hashtable.get(objectClassName);
      if (x == null)
        return null;
      return x.getOldestEntry();
    }
  }

  /** This is a helper class for the ObjectClassTable.  It maintains the data
  * for an individual object class.
  */
  protected class ObjectClassRecord
  {
    protected int currentMemberCount = 0;
    protected ObjectRecord firstLRU = null;
    protected ObjectRecord lastLRU = null;

    public ObjectClassRecord()
    {
    }

    public int getCurrentMemberCount()
    {
      return currentMemberCount;
    }

    /** Call this ONLY if it is known that the entry exists in
    * the object class record!!!
    */
    public void removeEntry(ObjectRecord x)
    {
      currentMemberCount--;
      // Patch up everything
      ObjectRecord prev = x.getPrevLRU();
      ObjectRecord next = x.getNextLRU();
      if (prev == null)
        firstLRU = next;
      else
        prev.setNextLRU(next);
      if (next == null)
        lastLRU = prev;
      else
        next.setPrevLRU(prev);
      x.setPrevLRU(null);
      x.setNextLRU(null);
    }

    /** Add a record to the end of the LRU list.
    * Call this ONLY if it is known that the entry does NOT
    * exist in the object class record!!!
    */
    public void addEntry(ObjectRecord x)
    {
      currentMemberCount++;
      x.setNextLRU(null);
      x.setPrevLRU(lastLRU);
      if (lastLRU == null)
        firstLRU = x;
      else
        lastLRU.setNextLRU(x);
      lastLRU = x;
    }

    /** Find the first (oldest) entry, or null
    * if there is none.
    */
    public ObjectRecord getOldestEntry()
    {
      return firstLRU;
    }

  }

  /** This class represents a timed expiration tree.  Expiration
  * is used to order the nodes.
  */
  protected class ExpirationTree
  {
    protected ExpirationTreeNode root = null;

    public ExpirationTree()
    {
    }

    /** This method MUST have the entry in the tree before
    * being called!
    */
    public void removeEntry(ObjectRecord x)
    {
      // We may delete entries from a node, but we could very well delete the node
      // as well.  Therefore, this method assumes that this might happen.
      //
      ExpirationTreeNode parent = null;
      boolean parentLesser = false;

      long expTime = x.getExpirationTime();

      ExpirationTreeNode current = root;
      while (current != null)
      {
        long nodeExpTime = current.getExpirationTime();
        if (expTime == nodeExpTime)
        {
          // Found the right node!
          // Delete the embedded record
          if (current.removeObjectRecord(x))
          {
            // The node itself also needs to be removed!

            ExpirationTreeNode lesserSide = current.getLesser();
            ExpirationTreeNode greaterSide = current.getGreater();

            if (lesserSide == null && greaterSide == null)
            {
              // Just remove the node; no children
              setPointer(parent,parentLesser,null);
            }
            else if (lesserSide == null && greaterSide != null)
            {
              // Simple rearrangement
              setPointer(parent,parentLesser,greaterSide);
            }
            else if (lesserSide != null && greaterSide == null)
            {
              // Reverse simple rearrangement
              setPointer(parent,parentLesser,lesserSide);
            }
            else
            {
              // Full complexity
              // Here, we may have a choice: Move up the lesser child, or
              // move up the greater child.

              // In theory, this should depend on the depth difference of the two
              // sides, but since we don't keep this info, we'll just be arbitrary
              setPointer(parent,parentLesser,greaterSide);
              // Add the lesser side into the new node in this position
              addTreeToBranch(greaterSide,true,lesserSide);
            }

          }
          return;
        }
        if (expTime < nodeExpTime)
        {
          // go the lesser route
          parent = current;
          parentLesser = true;
          current = current.getLesser();
        }
        else
        {
          // go the greater route
          parent = current;
          parentLesser = false;
          current = current.getGreater();
        }
      }
      // Should never get here, because it means we did not find the record.

    }

    /** This method files a subtree (represented by toAdd) beneath a branch, which is represented by
    * the parent parameters.  If parent is null, then the overall root of the tree is the start point.
    * If the parent is NOT null, then parentLesser describes whether the lesser branch is the one being modified.
    * The logic determines the shallowest legal placement of the toAdd node(s), and inserts them there.
    */
    protected void addTreeToBranch(ExpirationTreeNode parent, boolean parentLesser, ExpirationTreeNode toAdd)
    {
      if (toAdd == null)
        return;

      long expTime = toAdd.getExpirationTime();

      // Find the first node to consider
      ExpirationTreeNode current;
      if (parent == null)
        current = root;
      else
      {
        if (parentLesser)
          current = parent.getLesser();
        else
          current = parent.getGreater();
      }

      // Now, loop until we hit the end
      while (current != null)
      {
        long nodeExpTime = current.getExpirationTime();
        if (expTime < nodeExpTime)
        {
          // Take the lesser route
          parent = current;
          parentLesser = true;
          current = current.getLesser();
        }
        else
        {
          // Take the greater route
          parent = current;
          parentLesser = false;
          current = current.getGreater();
        }
      }

      // Insert the subtree here
      setPointer(parent,parentLesser,toAdd);
    }

    /** This method MUST NOT have the entry in the tree already
    * before being called!
    */
    public void addEntry(ObjectRecord x)
    {
      // Get the record expiration time, for convenience
      long expTime = x.getExpirationTime();

      // These two variables keep track of the last link we examined, so we can know where to put
      // the new node, if required.
      ExpirationTreeNode previousNode = null;
      boolean lesser = false;

      // This is our current variable
      ExpirationTreeNode current = root;
      while (current != null)
      {
        long nodeExpTime = current.getExpirationTime();
        if (nodeExpTime == expTime)
        {
          // Add to the current node
          current.addObjectRecord(x);
          return;
        }
        if (nodeExpTime > expTime)
        {
          // Go down the lesser branch
          previousNode = current;
          lesser = true;
          current = current.getLesser();
        }
        else
        {
          // Go down the greater branch
          previousNode = current;
          lesser = false;
          current = current.getGreater();
        }
      }

      // New node needs to be created.
      ExpirationTreeNode newNode = new ExpirationTreeNode(x);
      setPointer(previousNode,lesser,newNode);
    }

    protected void setPointer(ExpirationTreeNode parent, boolean isLesser, ExpirationTreeNode toAdd)
    {
      if (parent == null)
        root = toAdd;
      else
      {
        if (isLesser)
          parent.setLesser(toAdd);
        else
          parent.setGreater(toAdd);
      }
    }


    public ObjectRecord getOldestEntry()
    {
      // Look for the least node, and grab a record from it
      ExpirationTreeNode current = root;
      ExpirationTreeNode last = null;
      while (current != null)
      {
        last = current;
        current = current.getLesser();
      }
      if (last == null)
        return null;

      return last.getOldest();
    }
  }

  /** This class represents a node in the expiration tree.
  * The node has a pool of size at least one containing object records
  * with the same expiration date.
  */
  protected class ExpirationTreeNode
  {
    protected ExpirationTreeNode lesser = null;
    protected ExpirationTreeNode greater = null;

    protected ObjectRecord firstSame = null;
    protected ObjectRecord lastSame = null;

    public ExpirationTreeNode(ObjectRecord record)
    {
      firstSame = record;
      lastSame = record;
    }

    public long getExpirationTime()
    {
      return firstSame.getExpirationTime();
    }

    public ExpirationTreeNode getLesser()
    {
      return lesser;
    }

    public void setLesser(ExpirationTreeNode lesser)
    {
      this.lesser = lesser;
    }

    public ExpirationTreeNode getGreater()
    {
      return greater;
    }

    public void setGreater(ExpirationTreeNode greater)
    {
      this.greater = greater;
    }

    public void addObjectRecord(ObjectRecord x)
    {
      x.setSameExpirationNext(firstSame);
      firstSame.setSameExpirationPrev(x);
      firstSame = x;
    }

    /** Returns true if this removal was the last one (in which case the tree node is now
    * invalid, and should be removed from the tree)
    */
    public boolean removeObjectRecord(ObjectRecord x)
    {
      // Patch up everything
      ObjectRecord prev = x.getSameExpirationPrev();
      ObjectRecord next = x.getSameExpirationNext();
      if (prev == null)
        firstSame = next;
      else
        prev.setSameExpirationNext(next);
      if (next == null)
        lastSame = prev;
      else
        next.setSameExpirationPrev(prev);
      x.setSameExpirationPrev(null);
      x.setSameExpirationNext(null);
      return (firstSame == null);
    }

    public ObjectRecord getOldest()
    {
      return lastSame;
    }

  }
}
