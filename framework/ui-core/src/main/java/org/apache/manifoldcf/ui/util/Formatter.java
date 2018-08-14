/* $Id: Formatter.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.ui.util;

import java.util.*;

/** Various useful formatting methods for working with html
*/
public class Formatter
{
  public static final String _rcsid = "@(#)$Id: Formatter.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Format a long as an understandable date.
  *@param time is the long.
  *@return the date, as a human-readable string.
  */
  public static String formatTime(TimeZone tz, Locale locale, long time)
  {
    Calendar c = new GregorianCalendar(tz, locale);
    c.setTimeInMillis(time);
    // We want to format this string in a compact way:
    // mm-dd-yyyy hh:mm:ss.mmm
    StringBuilder returnString = new StringBuilder();
    writechars(returnString,c.get(Calendar.MONTH)+1,2);
    returnString.append("-");
    writechars(returnString,c.get(Calendar.DAY_OF_MONTH),2);
    returnString.append("-");
    writechars(returnString,c.get(Calendar.YEAR),4);
    returnString.append(" ");
    writechars(returnString,c.get(Calendar.HOUR_OF_DAY),2);
    returnString.append(":");
    writechars(returnString,c.get(Calendar.MINUTE),2);
    returnString.append(":");
    writechars(returnString,c.get(Calendar.SECOND),2);
    returnString.append(".");
    writechars(returnString,c.get(Calendar.MILLISECOND),3);
    return returnString.toString();
  }

  /** Format a string as a number of continuation fields, so that the total string is not too long.
  *@param value is the string to format.
  *@param maxWidth is the maximum width desired for each field.
  *@param multiple is true if multiple lines desired.
  *@param ellipsis is true if ellipses are desired for each non-terminal line.
  *@return an array of strings representing the split-up value.
  */
  public static String[] formatString(String value, int maxWidth, boolean multiple, boolean ellipsis)
  {
    ArrayList list = new ArrayList();
    while (true)
    {
      if (list.size() > 0 && multiple == false)
        break;
      if (value.length() == 0)
        break;
      if (value.length() <= maxWidth)
      {
        list.add(value);
        break;
      }
      if (ellipsis && maxWidth >= 4)
      {
        list.add(value.substring(0,maxWidth-3) + "...");
        value = value.substring(maxWidth-3);
      }
      else
      {
        list.add(value.substring(0,maxWidth));
        value = value.substring(maxWidth);
      }
    }
    String[] rval = new String[list.size()];
    int i = 0;
    while (i < rval.length)
    {
      rval[i] = (String)list.get(i);
      i++;
    }
    return rval;
  }

  // Helpful formatting methods

  protected static void writechars(StringBuilder sb, int value, int length)
  {
    String stuff = Integer.toString(value);
    if (length < stuff.length())
    {
      while (length > 0)
      {
        sb.append('*');
        length--;
      }
      return;
    }
    while (length > stuff.length())
    {
      sb.append('0');
      length--;
    }
    sb.append(stuff);
  }

}
