/* $Id: StringSetBuffer.java 988245 2010-08-23 18:39:35Z kwright $ */

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

public class StringSetBuffer
{
  public static final String _rcsid = "@(#)$Id: StringSetBuffer.java 988245 2010-08-23 18:39:35Z kwright $";

  protected HashMap hashtable = new HashMap();

  public StringSetBuffer()
  {
  }

  public void clear()
  {
    hashtable.clear();
  }

  public boolean contains(String x)
  {
    return (hashtable.get(x) != null);
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

  public void add(String s)
  {
    add(s,null);
  }

  public void add(String s, String prefix)
  {
    if (prefix != null)
      s = prefix + s;
    hashtable.put(s,s);
  }

  public void add(StringSet ss)
  {
    add(ss,null);
  }

  public void add(StringSet ss, String prefix)
  {
    Iterator iterator = ss.getKeys();
    while (iterator.hasNext())
    {
      String key = (String)iterator.next();
      add(key,prefix);
    }
  }

  public void add(String[] stringArray)
  {
    add(stringArray,null);
  }

  public void add(String[] stringArray, String prefix)
  {
    int i = 0;
    while (i < stringArray.length)
    {
      add(stringArray[i++],prefix);
    }
  }

  public void add(Map table)
  {
    add(table,null);
  }

  public void add(Map table, String prefix)
  {
    Iterator x = table.values().iterator();
    while (x.hasNext())
    {
      String key = (String)x.next();
      add(key,prefix);
    }
  }

  public void remove(String value)
  {
    hashtable.remove(value);
  }

  public int size()
  {
    return hashtable.size();
  }

  public Iterator getKeys()
  {
    return hashtable.keySet().iterator();
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


}
