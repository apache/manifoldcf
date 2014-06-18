/* $Id: BaseDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This is the base class for cache object descriptions.
* The base class sets up LRU behavior based on parameters
* in a configuration file, and also bases expiration on the
* same class picture.
*/
public abstract class BaseDescription implements ICacheDescription
{
  public static final String _rcsid = "@(#)$Id: BaseDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  protected ICacheClass cacheClass = null;

  protected final static Integer max_value = new Integer(Integer.MAX_VALUE);

  public BaseDescription(String objectClassName)
  {
    if (objectClassName != null)
      cacheClass = new LocalCacheClass(objectClassName);
  }

  public BaseDescription(String objectClassName, int maxLRUCount)
  {
    if (objectClassName != null)
      cacheClass = new LocalCacheClass(objectClassName,maxLRUCount);
  }

  /** Get the object class for an object.  The object class is used to determine
  * the group of objects treated in the same LRU manner.
  * @return the newly created object's object class, or null if there is no
  * such class, and LRU behavior is not desired.
  */
  public ICacheClass getObjectClass()
  {
    return cacheClass;
  }

  /** Obtain an expiration time for an object, in milliseconds since epoch.
  * The cache manager will call this method for all objects that are being operated on,
  * so that their expiration timestamps get properly updated to a new time.
  * @return a time in milliseconds for the object to expire, or -1 if there is no expiration
  * desired.
  */
  public long getObjectExpirationTime(long currentTime)
  {
    return -1;
  }

  /** This is a cache class implementation that gets expiration and LRU info
  * from .ini variables.
  */
  protected class LocalCacheClass implements ICacheClass
  {
    protected String objectClassName;
    protected Integer maxLRUCount = null;

    public LocalCacheClass(String objectClassName)
    {
      this(objectClassName,-1);
    }
    
    public LocalCacheClass(String objectClassName, int maxLRUCount)
    {
      this.objectClassName = objectClassName;
      if (maxLRUCount != -1)
        this.maxLRUCount = new Integer(maxLRUCount);
    }

    /** Get the name of the object class.
    * This determines the set of objects that are treated in the same
    * LRU pool.
    *@return the class name.
    */
    public String getClassName()
    {
      return objectClassName;
    }

    /** Get the maximum LRU count of the object class.
    *@return the maximum number of the objects of the particular class
    * allowed.
    */
    public int getMaxLRUCount()
    {
      if (maxLRUCount == null)
      {
        try
        {
          String x = null; // JSKW.getProperty("cache."+objectClassName+".lrusize");
          if (x == null)
            maxLRUCount = max_value;
          else
            maxLRUCount = new Integer(x);
        }
        catch (Exception e)
        {
          maxLRUCount = max_value;
        }
      }
      return maxLRUCount.intValue();
    }
  }
}
