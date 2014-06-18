/* $Id: ICacheDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface represents objects that describe cacheable objects.
* Implementors of this interface should ideally be immutable, if they
* are used to describe objects that are placed in cache.
* Note well: The getCriticalSectionName() method described by this interface is
* meant to return the name of a critical section.  This should
* be distinguishable from other critical sections in the system;
* it should therefore have the classname as a component.
*/
public interface ICacheDescription
{
  public static final String _rcsid = "@(#)$Id: ICacheDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Get the cache keys for an object (which may or may not exist yet in
  * the cache).  This method is called in order for cache manager to throw the correct locks.
  * @return the object's cache keys, or null if the object should not
  * be cached.
  */
  public StringSet getObjectKeys();

  /** Get the critical section name for this description object.
  * This is used to synchronize creation of the described object,
  * and thus is used only for objects that will be cached.  This
  * method does not need to return decent results for objects that
  * are never cached.
  *@return the critical section name.
  */
  public String getCriticalSectionName();

  /** Get the object class for an object.  The object class is used to determine
  * the group of objects treated in the same LRU manner.
  * @return the newly created object's object class, or null if there is no
  * such class, and LRU behavior is not desired.
  */
  public ICacheClass getObjectClass();

  /** Obtain an expiration time for an object, in milliseconds since epoch.
  * The cache manager will call this method whenever the object is being looked up,
  * so that its expiration timestamps can be properly updated to a new time.
  * @param currentTime is the time of the lookup, in milliseconds since epoch.
  * @return a time in milliseconds since epoch for the object to expire, or -1 if there is no expiration
  * desired.
  */
  public long getObjectExpirationTime(long currentTime);

}
