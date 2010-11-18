/* $Id: IThreadContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes the functionality in a thread context.
* Thread contexts exist as a place to park objects that should exist
* at most once per thread.  While there is no guarantee that this would
* be enforced, the semantics don't generally fail with multiple instances,
* but they do become inefficient.
* But, in any case, an IThreadContext object should NEVER be shared among threads!!!
*/
public interface IThreadContext
{
  public static final String _rcsid = "@(#)$Id: IThreadContext.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Set a named object into the context.
  * @param key is the name of the object (usually a string)
  * @param object is the object to save, or null if the object is to be
  * destroyed instead.
  */
  public void save(Object key, Object object);

  /** Retrieve a named object from the context.
  * Use an equivalent key to retrieve what was previously saved.
  * If no such object exists, null will be returned.
  * @param key is the object's key (usually a string)
  * @return the object, or null.
  */
  public Object get(Object key);

}
