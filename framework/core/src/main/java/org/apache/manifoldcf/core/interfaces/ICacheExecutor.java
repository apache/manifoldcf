/* $Id: ICacheExecutor.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes an object designed to be instantiated solely for the purpose of
* running inside the global cache manager's execution method.  The cache manager will
* perform all synchronization, and for objects being accessed will call either the create*()
* family of methods below, or
* the exists() method, if the object was found in the cache.  For all existing objects being
* invalidated,
* the destroy() method will be called.
* It IS legal to specify the same object for both read and invalidate!  In this case, both
* the create() or exists() sequence AND the destroy() method will be called.
* Finally, after this has been
* done for all of the requested objects, the execute() method will be called.
*
* Users of the cache manager will need to create objects implementing this interface when they want to
* operate on a set of cached objects.
* NOTE: Objects that are created by a cache executor must obey the following rule: A given objectDescription
* must always have the same invalidation keys, regardless of which cache executor object creates them,
* and regardless of WHEN the object is instantiated.  This is required in order to ensure that the locks
* thrown by the cache manager are correct.
*/
public interface ICacheExecutor
{
  public static final String _rcsid = "@(#)$Id: ICacheExecutor.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Create a set of new objects to operate on and cache.  This method is called only
  * if the specified object(s) are NOT available in the cache.  The specified objects
  * should be created and returned; if they are not created, it means that the
  * execution cannot proceed, and the execute() method will not be called.
  * @param objectDescriptions is the set of unique identifier of the object.
  * @return the newly created objects to cache, or null, if any object cannot be created.
  *  The order of the returned objects must correspond to the order of the object descriptinos.
  */
  public Object[] create(ICacheDescription[] objectDescriptions) throws ManifoldCFException;


  /** Notify the implementing class of the existence of a cached version of the
  * object.  The object is passed to this method so that the execute() method below
  * will have it available to operate on.  This method is also called for all objects
  * that are freshly created as well.
  * @param objectDescription is the unique identifier of the object.
  * @param cachedObject is the cached object.
  */
  public void exists(ICacheDescription objectDescription, Object cachedObject) throws ManifoldCFException;


  /** Perform the desired operation.  This method is called after either createGetObject()
  * or exists() is called for every requested object.
  */
  public void execute() throws ManifoldCFException;
}

