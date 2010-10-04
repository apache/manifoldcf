/* $Id: EnumeratedValues.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import java.util.*;

/** This class represents a set of enumerated integer values.
*/
public class EnumeratedValues
{
  public static final String _rcsid = "@(#)$Id: EnumeratedValues.java 988245 2010-08-23 18:39:35Z kwright $";

  // Member variables
  protected HashMap legalValues = new HashMap();

  /** Create enumerated values.
  *@param values is the set of legal values.
  */
  public EnumeratedValues(int[] values)
  {
    int i = 0;
    while (i < values.length)
    {
      Integer x = new Integer(values[i++]);
      legalValues.put(x,x);
    }
  }

  /** Create from arraylist.
  *@param values is the arraylist.
  */
  public EnumeratedValues(ArrayList values)
  {
    int i = 0;
    while (i < values.size())
    {
      Integer x = (Integer)values.get(i++);
      legalValues.put(x,x);
    }
  }

  /** Create from a list of semicolon-separated strings.
  *@param values are the values, as strings
  */
  public EnumeratedValues(String[] values)
  {
    int i = 0;
    while (i < values.length)
    {
      Integer val = new Integer(values[i++]);
      legalValues.put(val,val);
    }
  }

  /** Create for comma-separated list.
  */
  public EnumeratedValues(String commaList)
  {
    int startIndex = 0;
    while (true)
    {
      int pos = commaList.indexOf(",",startIndex);
      if (pos == -1)
      {
        String endString = commaList.substring(startIndex).trim();
        if (endString.length() > 0)
        {
          Integer x = new Integer(endString);
          legalValues.put(x,x);
        }
        break;
      }
      String value = commaList.substring(startIndex,pos).trim();
      startIndex = pos+1;
      if (value.length() > 0)
      {
        Integer x = new Integer(value);
        legalValues.put(x,x);
      }
    }
  }

  public boolean checkValue(int value)
  {
    return (legalValues.get(new Integer(value)) != null);
  }

  public Iterator getValues()
  {
    return legalValues.keySet().iterator();
  }

  public int size()
  {
    return legalValues.size();
  }


}
