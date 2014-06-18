/* $Id: StringSet.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.util.*;

/** Immutable helper class, representing an unordered set of strings
*/
public class StringSet
{
  public static final String _rcsid = "@(#)$Id: StringSet.java 988245 2010-08-23 18:39:35Z kwright $";

  protected HashMap hashtable = new HashMap();
  protected String descriptiveString = null;

  /** Create the empty stringset
  */
  public StringSet()
  {
  }

  /** Create the stringset from a stringsetbuffer
  */
  public StringSet(StringSetBuffer buffer)
  {
    Iterator x = buffer.getKeys();
    while (x.hasNext())
    {
      String key = (String)x.next();
      hashtable.put(key,key);
    }
  }

  /** Create a stringset from a single string.
  */
  public StringSet(String x)
  {
    hashtable.put(x,x);
  }

  /** Create the stringset from a string array
  */
  public StringSet(String[] array)
  {
    int i = 0;
    while (i < array.length)
    {
      String x = array[i++];
      hashtable.put(x,x);
    }
  }

  /** Create the stringset from a vector of strings
  */
  public StringSet(Vector stringVector)
  {
    int i = 0;
    while (i < stringVector.size())
    {
      String x = (String)stringVector.elementAt(i++);
      hashtable.put(x,x);
    }
  }

  /** Create the stringset from a Map of strings
  */
  public StringSet(Map table)
  {
    Iterator x = table.values().iterator();
    while (x.hasNext())
    {
      String key = (String)x.next();
      hashtable.put(key,key);
    }
  }

  /** Check if a key is in the set
  */
  public boolean contains(String key)
  {
    return hashtable.get(key) != null;
  }

  public boolean contains(StringSet x)
  {
    Iterator iter = x.getKeys();
    while (iter.hasNext())
    {
      String key = (String)iter.next();
      if (contains(key))
        return true;
    }
    return false;
  }


  /** Enumerate through the keys
  */
  public Iterator getKeys()
  {
    return hashtable.keySet().iterator();
  }

  /** Get number of keys
  */
  public int size()
  {
    return hashtable.size();
  }

  /** Get array of strings
  */
  public String[] getArray(String prefix)
  {
    String[] rval = new String[hashtable.size()];
    int i = 0;
    Iterator iter = hashtable.keySet().iterator();
    while (iter.hasNext())
    {
      String x = (String)iter.next();
      if (prefix != null)
        x = prefix + x;
      rval[i++] = x;
    }
    return rval;
  }

  /** Get a new stringset based on an old one plus a prefix.
  */
  public StringSet(StringSet oldOne, String prefix)
  {
    Iterator iter = oldOne.hashtable.keySet().iterator();
    while (iter.hasNext())
    {
      String x = (String)iter.next();
      if (prefix != null)
        x = prefix + x;
      hashtable.put(x,x);
    }

  }

  /** Calculate the hashcode
  */
  public int hashCode()
  {
    return hashtable.hashCode();
  }

  /** Perform equals operation
  */
  public boolean equals(Object o)
  {
    if (!(o instanceof StringSet))
      return false;
    StringSet set = (StringSet)o;
    return hashtable.equals(set.hashtable);
  }

  /** Convert to a descriptive string.
  *@return the descriptive string.
  */
  public String getDescriptiveString()
  {
    if (descriptiveString == null)
    {
      // Get as array first
      String[] array = getArray(null);
      java.util.Arrays.sort(array);
      StringBuilder sb = new StringBuilder();
      int i = 0;
      while (i < array.length)
      {
        if (i > 0)
          sb.append(":");
        sb.append(array[i++]);
      }
      descriptiveString = sb.toString();
    }
    return descriptiveString;
  }

}
