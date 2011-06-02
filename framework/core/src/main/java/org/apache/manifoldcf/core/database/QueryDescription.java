/* $Id: QueryDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.database;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This object is immutable, and is used as a description for a cached query.
*/
public class QueryDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
{
  public static final String _rcsid = "@(#)$Id: QueryDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  // Store an id for the transaction, not the actual transaction handle.
  // We don't want to hold onto the actual open database handle!
  protected String databaseName;
  protected String query;
  protected List parameters;
  protected String criticalSectionName = null;
  protected String cacheClassName;
  protected StringSet keys;
  protected int maxReturn;
  protected ResultSpecification spec;
  protected ILimitChecker returnLimit;

  public QueryDescription(String databaseName, String query, List parameters,
    String cacheClassName, StringSet cacheKeys, int maxReturn, ResultSpecification spec,
    ILimitChecker returnLimit)
  {
    super(cacheClassName);
    this.databaseName = databaseName;
    this.query = query;
    this.parameters = null;
    if (parameters != null)
    {
      this.parameters = listClone(parameters);
    }
    keys = cacheKeys;
    this.maxReturn = maxReturn;
    this.spec = spec;
    if (returnLimit != null)
    {
      if (returnLimit.doesCompareWork())
        this.returnLimit = returnLimit.duplicate();
      else
      {
        this.returnLimit = returnLimit;
        // Also turn off caching
        keys = null;
      }
    }
    else
      this.returnLimit = null;
  }

  public String getQuery()
  {
    return query;
  }

  public List getParameters()
  {
    if (parameters == null)
      return null;
    return listClone(parameters);
  }

  public int getMaxReturn()
  {
    return maxReturn;
  }

  public ResultSpecification getResultSpecification()
  {
    return spec;
  }

  public ILimitChecker getReturnLimit()
  {
    return returnLimit;
  }

  public int hashCode()
  {
    int rval = databaseName.hashCode() + query.hashCode();
    if (parameters != null)
      rval += parameters.hashCode();
    if (cacheClassName != null)
      rval += cacheClassName.hashCode();
    rval += maxReturn;
    if (spec != null)
      rval += spec.hashCode();
    if (returnLimit != null && returnLimit.doesCompareWork())
      rval += returnLimit.hashCode();
    return rval;
  }

  public boolean equals(Object o)
  {
    if (!(o instanceof QueryDescription))
      return false;
    QueryDescription other = (QueryDescription)o;
    // System.out.println("Matching query descriptions: "+this.getCriticalSectionName()+" against: "+other.getCriticalSectionName());
    if (parameters == null || other.parameters == null)
    {
      if (parameters != null || other.parameters != null)
        return false;
    }
    else
    {
      if (!parameters.equals(other.parameters))
        return false;
    }
    if (cacheClassName == null || other.cacheClassName == null)
    {
      if (cacheClassName != null || other.cacheClassName != null)
        return false;
    }
    else
    {
      if (!cacheClassName.equals(other.cacheClassName))
        return false;
    }
    if (spec == null || other.spec == null)
    {
      if (spec != null || other.spec != null)
        return false;
    }
    else
    {
      if (!spec.equals(other.spec))
        return false;
    }
    if (returnLimit == null || other.returnLimit == null)
    {
      if (returnLimit != null || other.returnLimit != null)
        return false;
    }
    else
    {
      if (returnLimit.doesCompareWork() == false || other.returnLimit.doesCompareWork() == false)
        return false;
      if (!returnLimit.equals(other.returnLimit))
        return false;
    }
    if (databaseName == null || other.databaseName == null)
    {
      if (databaseName != null || other.databaseName != null)
        return false;
    }
    else
    {
      if (!databaseName.equals(other.databaseName))
        return false;
    }
    return query.equals(other.query) && maxReturn == other.maxReturn;
  }

  /** Get the cache keys for an object (which may or may not exist yet in
  * the cache).  This method is called in order for cache manager to throw the correct locks.
  * @return the object's cache keys, or null if the object should not
  * be cached.
  */
  public StringSet getObjectKeys()
  {
    return keys;
  }

  public String getCriticalSectionName()
  {
    if (criticalSectionName == null)
    {
      StringBuilder sb = new StringBuilder();
      if (databaseName != null)
        sb.append(databaseName);
      sb.append("-").append(getClass().getName()).append("-");
      if (cacheClassName != null)
        sb.append(cacheClassName).append("-");
      sb.append(query).append("-");
      sb.append(Integer.toString(maxReturn)).append("-");
      if (parameters != null)
      {
        int i = 0;
        while (i < parameters.size())
        {
          sb.append(parameters.get(i++)).append("-");
        }
      }
      // For the return specification, we can be specific.
      if (spec != null)
        sb.append(":").append(spec.toString());

      // For return limit, we really can't be very specific.  The critical section will therefore interact all
      // limited queries with out distinction about the kind of limit.
      if (returnLimit != null && returnLimit.doesCompareWork())
        sb.append(":limited");

      criticalSectionName = sb.toString();
    }
    return criticalSectionName;
  }

  protected static List listClone(List list)
  {
    List rval = new ArrayList(list.size());
    for (Object o : list)
    {
      rval.add(o);
    }
    return rval;
  }
  
}



