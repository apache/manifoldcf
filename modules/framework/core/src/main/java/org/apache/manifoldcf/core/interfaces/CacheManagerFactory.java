/* $Id: CacheManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Factory class for instantiating the cache manager.
*/
public class CacheManagerFactory
{
  public static final String _rcsid = "@(#)$Id: CacheManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  private final static String cacheManager = "_CacheManager_";

  private CacheManagerFactory()
  {
  }

  /** Return a cache manager instance that any client can use to obtain cache management services.
  * This service will use the lock manager, so it needs to have a thread context.
  * @return the proper cache manager instance.
  */
  public static ICacheManager make(IThreadContext context)
    throws ManifoldCFException
  {
    Object o = context.get(cacheManager);
    if (o == null || !(o instanceof ICacheManager))
    {
      o = new org.apache.manifoldcf.core.cachemanager.CacheManager(context);
      context.save(cacheManager,o);
    }
    return (ICacheManager)o;
  }

}
