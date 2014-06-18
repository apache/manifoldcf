/* $Id: RepositoryConnection.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.repository;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** An instance of this class represents a persistently-stored repository connection description.
* This is the paper object meant for editing and manipulation.
*/
public class RepositoryConnection implements IRepositoryConnection
{
  public static final String _rcsid = "@(#)$Id: RepositoryConnection.java 988245 2010-08-23 18:39:35Z kwright $";

  // data
  protected boolean isNew = true;
  protected String name = null;
  protected String description = null;
  protected String className = null;
  protected String authorityName = null;
  protected ConfigParams configParams = new ConfigParams();
  protected int maxCount = 100;

  // Throttles.  Keyed by regexp, and has ThrottleItem values.
  protected HashMap throttles = new HashMap();

  /** Constructor.
  */
  public RepositoryConnection()
  {
  }

  /** Clone this object.
  *@return the cloned object.
  */
  public RepositoryConnection duplicate()
  {
    RepositoryConnection rval = new RepositoryConnection();
    rval.isNew = isNew;
    rval.name = name;
    rval.description = description;
    rval.className = className;
    rval.authorityName = authorityName;
    rval.maxCount = maxCount;
    rval.configParams = configParams.duplicate();
    Iterator iter = throttles.keySet().iterator();
    while (iter.hasNext())
    {
      String key = (String)iter.next();
      ThrottleItem ti = (ThrottleItem)throttles.get(key);
      rval.throttles.put(key,ti);
    }
    return rval;
  }

  /** Set 'isnew' condition.
  *@param isnew true if this is a new instance.
  */
  public void setIsNew(boolean isnew)
  {
    this.isNew = isnew;
  }
  
  /** Get 'isnew' condition.
  *@return true if this is a new connection, false otherwise.
  */
  public boolean getIsNew()
  {
    return isNew;
  }

  /** Set name.
  *@param name is the name.
  */
  public void setName(String name)
  {
    this.name = name;
  }

  /** Get name.
  *@return the name
  */
  public String getName()
  {
    return name;
  }

  /** Set description.
  *@param description is the description.
  */
  public void setDescription(String description)
  {
    this.description = description;
  }

  /** Get description.
  *@return the description
  */
  public String getDescription()
  {
    return description;
  }

  /** Set the class name.
  *@param className is the class name.
  */
  public void setClassName(String className)
  {
    this.className = className;
  }

  /** Get the class name.
  *@return the class name
  */
  public String getClassName()
  {
    return className;
  }

  /** Set the ACL authority name.
  *@param authorityName is the ACL authority name.
  */
  public void setACLAuthority(String authorityName)
  {
    this.authorityName = authorityName;
  }

  /** Get the ACL authority name.
  *@return the ACL authority name.
  */
  public String getACLAuthority()
  {
    return authorityName;
  }

  /** Get the configuration parameters.
  *@return the map.  Can be modified.
  */
  public ConfigParams getConfigParams()
  {
    return configParams;
  }

  /** Set the maximum size of the connection pool.
  *@param maxCount is the maximum connection count per JVM.
  */
  public void setMaxConnections(int maxCount)
  {
    this.maxCount = maxCount;
  }

  /** Get the maximum size of the connection pool.
  *@return the maximum size.
  */
  public int getMaxConnections()
  {
    return maxCount;
  }

  /** Clear all throttle values. */
  public void clearThrottleValues()
  {
    throttles.clear();
  }

  /** Add a throttle value.
  *@param description is the throttle description.
  *@param match is the regexp to be applied to the bin names.
  *@param throttle is the fetch rate to use, in fetches per millisecond.
  */
  public void addThrottleValue(String match, String description, float throttle)
  {
    throttles.put(match,new ThrottleItem(match,description,throttle));
  }

  /** Delete a throttle.
  *@param match is the regexp describing the throttle to be removed.
  */
  public void deleteThrottleValue(String match)
  {
    throttles.remove(match);
  }

  /** Get throttles.  This will return a list of match strings, ordered by description and then
  * match string.
  *@return the ordered list of throttles.
  */
  public String[] getThrottles()
  {
    String[] rval = new String[throttles.size()];
    Iterator iter = throttles.keySet().iterator();
    int i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(rval);
    return rval;
  }

  /** Get the description for a throttle.
  *@param match describes the throttle.
  *@return the description.
  */
  public String getThrottleDescription(String match)
  {
    ThrottleItem i = (ThrottleItem)throttles.get(match);
    if (i == null)
      return null;
    return i.getDescription();
  }

  /** Get the throttle value for a throttle.
  *@param match describes the throttle.
  *@return the throttle value, in fetches per millisecond.
  */
  public float getThrottleValue(String match)
  {
    ThrottleItem i = (ThrottleItem)throttles.get(match);
    if (i == null)
      return 0.0f;
    return i.getValue();
  }

  /** Set the maximum number of document fetches per millisecond.
  *@param rate is the rate, in fetches/millisecond.
  */
  public void setThrottle(Float rate)
  {
    if (rate == null)
      deleteThrottleValue(".*");
    else
      addThrottleValue(".*","All bins",rate.floatValue());
  }

  /** Get the maximum number of document fetches per millisecond.
  *@return fetches/ms, or null if there is no throttle.
  */
  public Float getThrottle()
  {
    float rate = getThrottleValue(".*");
    if (rate == 0.0f)
      return null;
    return new Float(rate);
  }

  /** Throttle item class.  Each instance describes a particular throttle.
  */
  protected static class ThrottleItem
  {
    /** The regexp key. */
    protected String match;
    /** The description. */
    protected String description;
    /** The throttle value */
    protected float value;

    /** Constructor. */
    public ThrottleItem(String match, String description, float value)
    {
      this.match = match;
      this.description = description;
      this.value = value;
    }

    /** Get the match. */
    public String getMatch()
    {
      return match;
    }

    /** Get the description. */
    public String getDescription()
    {
      return description;
    }

    /** Get the throttle value. */
    public float getValue()
    {
      return value;
    }

    /** Get the hash code. */
    public int hashCode()
    {
      return match.hashCode();
    }

    /** Compare. */
    public boolean equals(Object o)
    {
      if (!(o instanceof ThrottleItem))
        return false;
      ThrottleItem other = (ThrottleItem)o;
      return match.equals(other.match);
    }
  }

}
