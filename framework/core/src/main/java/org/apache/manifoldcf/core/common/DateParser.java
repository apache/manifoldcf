/* $Id$ */

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
package org.apache.manifoldcf.core.common;

import java.util.*;

/** Class to parse and format common dates.
*/
public class DateParser
{
  public static final String _rcsid = "@(#)$Id$";

  /** Parse ISO 8601 dates, and their common variants.
  */
  public static Date parseISO8601Date(String isoDateValue)
  {
    if (isoDateValue == null)
      return null;
    
    boolean isMicrosoft = (isoDateValue.indexOf("T") == -1);
    
    String formatString;
    if (isMicrosoft)
    {
      formatString = "yyyy-MM-dd' 'HH:mm:ss";
    }
    else
    {
      // There are a number of variations on the basic format.
      // We'll look for key characters to help is determine which is which.
      StringBuilder isoFormatString = new StringBuilder("yy");
      if (isoDateValue.length() > 2 && isoDateValue.charAt(2) != '-')
        isoFormatString.append("yy");
      isoFormatString.append("-MM-dd'T'HH:mm:ss");
      if (isoDateValue.indexOf(".") != -1)
        isoFormatString.append(".SSS");
      if (isoDateValue.endsWith("Z"))
      {
        isoDateValue = isoDateValue.substring(0,isoDateValue.length()-1) + "-0000";
        isoFormatString.append("Z");
      }
      else
      {
        // We need to be able to parse either "-08:00" or "-0800".  The 'Z' specifier only handles
        // -0800, unfortunately - see CONNECTORS-700.  So we have to do some hackery to remove the colon.
        int colonIndex = isoDateValue.lastIndexOf(":");
        int dashIndex = isoDateValue.lastIndexOf("-");
        int plusIndex = isoDateValue.lastIndexOf("+");
        if (colonIndex != -1 &&
          ((dashIndex != -1 && colonIndex == dashIndex+3 && isNumeral(isoDateValue,dashIndex-1)) || (plusIndex != -1 && colonIndex == plusIndex+3 && isNumeral(isoDateValue,plusIndex-1))))
          isoDateValue = isoDateValue.substring(0,colonIndex) + isoDateValue.substring(colonIndex+1);
        isoFormatString.append("Z");      // RFC 822 time, including general time zones
      }
      formatString = isoFormatString.toString();
    }
    java.text.DateFormat iso8601Format = new java.text.SimpleDateFormat(formatString, Locale.ROOT);
    try
    {
      return iso8601Format.parse(isoDateValue);
    }
    catch (java.text.ParseException e)
    {
      return null;
    }
  }
  
  protected static boolean isNumeral(String value, int index)
  {
    return index >= 0 && value.charAt(index) >= '0' && value.charAt(index) <= '9';
  }
  
  /** Format ISO8601 date.
  */
  public static String formatISO8601Date(Date dateValue)
  {
    java.text.DateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
    df.setTimeZone(TimeZone.getTimeZone("GMT"));
    return df.format(dateValue);
  }
  
  /** Timezone mapping from RFC822 timezones to ones understood by Java */
  
  // Month map
  protected static HashMap monthMap = new HashMap();
  static
  {
    monthMap.put("jan",new Integer(1));
    monthMap.put("feb",new Integer(2));
    monthMap.put("mar",new Integer(3));
    monthMap.put("apr",new Integer(4));
    monthMap.put("may",new Integer(5));
    monthMap.put("jun",new Integer(6));
    monthMap.put("jul",new Integer(7));
    monthMap.put("aug",new Integer(8));
    monthMap.put("sep",new Integer(9));
    monthMap.put("oct",new Integer(10));
    monthMap.put("nov",new Integer(11));
    monthMap.put("dec",new Integer(12));
  }

  protected static final HashMap milTzMap;
  static
  {
    milTzMap = new HashMap();
    milTzMap.put("Z","GMT");
    milTzMap.put("UT","GMT");
    milTzMap.put("A","GMT-01:00");
    milTzMap.put("M","GMT-12:00");
    milTzMap.put("N","GMT+01:00");
    milTzMap.put("Y","GMT+12:00");
  }

  /** Parse RFC822 date */
  public static Date parseRFC822Date(String dateValue)
  {
    if (dateValue == null)
      return null;
    dateValue = dateValue.trim();
    // See http://www.faqs.org/rfcs/rfc822.html for legal formats
    // Format: [day of week,] day mo year hh24:mm:ss tz
    int commaIndex = dateValue.indexOf(",");
    String usable;
    if (commaIndex == -1)
      usable = dateValue;
    else
      usable = dateValue.substring(commaIndex+1).trim();
    int index;

    index = usable.indexOf(" ");
    if (index == -1)
      return null;
    String day = usable.substring(0,index);
    usable = usable.substring(index+1).trim();

    index = usable.indexOf(" ");
    if (index == -1)
      return null;
    String month = usable.substring(0,index).toLowerCase(Locale.ROOT);
    usable = usable.substring(index+1).trim();

    String year;
    String hour = null;
    String minute = null;
    String second = null;
    String timezone = null;

    index = usable.indexOf(" ");
    if (index != -1)
    {
      year = usable.substring(0,index);
      usable = usable.substring(index+1).trim();

      index = usable.indexOf(":");
      if (index == -1)
        return null;
      hour = usable.substring(0,index);
      usable = usable.substring(index+1).trim();

      index = usable.indexOf(":");
      if (index != -1)
      {
        minute = usable.substring(0,index);
        usable = usable.substring(index+1).trim();

        index = usable.indexOf(" ");
        if (index == -1)
          second = usable;
        else
        {
          second = usable.substring(0,index);
          timezone = usable.substring(index+1).trim();
        }
      }
      else
      {
        index = usable.indexOf(" ");
        if (index == -1)
          minute = usable;
        else
        {
          minute = usable.substring(0,index);
          timezone = usable.substring(index+1).trim();
        }
      }
    }
    else
      year = usable;

    // Now construct a calendar object from this
    TimeZone tz;
    if (timezone != null && timezone.length() > 0)
    {
      if (timezone.startsWith("+") || timezone.startsWith("-"))
      {
        if (timezone.indexOf(":") == -1 && timezone.length() > 3)
          timezone = timezone.substring(0,timezone.length()-2) + ":" + timezone.substring(timezone.length()-2);
        timezone = "GMT"+timezone;
      }
      else
      {
        // Map special timezones to java timezones
        if (milTzMap.get(timezone) != null)
          timezone = (String)milTzMap.get(timezone);
      }

    }
    else
      timezone = "GMT";


    tz = TimeZone.getTimeZone(timezone);

    Calendar c = new GregorianCalendar(tz, Locale.ROOT);
    try
    {
      int value = Integer.parseInt(year);
      if (value < 1900)
        value += 1900;
      c.set(Calendar.YEAR,value);

      Integer x = (Integer)monthMap.get(month);
      if (x == null)
        return null;
      c.set(Calendar.MONTH,x.intValue()-1);

      value = Integer.parseInt(day);
      c.set(Calendar.DAY_OF_MONTH,value);

      if (hour != null)
        value = Integer.parseInt(hour);
      else
        value = 0;
      c.set(Calendar.HOUR_OF_DAY,value);

      if (minute != null)
        value = Integer.parseInt(minute);
      else
        value = 0;
      c.set(Calendar.MINUTE,value);

      if (second != null)
        value = Integer.parseInt(second);
      else
        value = 0;
      c.set(Calendar.SECOND,value);

      c.set(Calendar.MILLISECOND,0);
      return new Date(c.getTimeInMillis());
    }
    catch (NumberFormatException e)
    {
      return null;
    }

  }

  /** Parse a China Daily News date */
  public static Date parseChinaDate(String dateValue)
  {
    if (dateValue == null)
      return null;
    dateValue = dateValue.trim();
    // Format: 2007/12/30 11:01
    int index;
    index = dateValue.indexOf("/");
    if (index == -1)
      return null;
    String year = dateValue.substring(0,index);
    dateValue = dateValue.substring(index+1);
    index = dateValue.indexOf("/");
    if (index == -1)
      return null;
    String month = dateValue.substring(0,index);
    dateValue = dateValue.substring(index+1);
    index = dateValue.indexOf(" ");
    String day;
    String hour = null;
    String minute = null;
    String second = null;
    if (index == -1)
      day = dateValue;
    else
    {
      day = dateValue.substring(0,index);
      dateValue = dateValue.substring(index+1);
      index = dateValue.indexOf(":");
      if (index == -1)
        return null;
      hour = dateValue.substring(0,index);
      dateValue = dateValue.substring(index+1);
      index = dateValue.indexOf(":");
      if (index != -1)
      {
        minute = dateValue.substring(0,index);
        dateValue = dateValue.substring(index+1);
        second = dateValue;
      }
      else
        minute = dateValue;
    }
    TimeZone tz = TimeZone.getTimeZone("GMT");
    Calendar c = new GregorianCalendar(tz, Locale.ROOT);
    try
    {
      int value = Integer.parseInt(year);
      if (value < 1900)
        value += 1900;
      c.set(Calendar.YEAR,value);

      value = Integer.parseInt(month);
      c.set(Calendar.MONTH,value-1);

      value = Integer.parseInt(day);
      c.set(Calendar.DAY_OF_MONTH,value);

      if (hour != null)
        value = Integer.parseInt(hour);
      else
        value = 0;
      c.set(Calendar.HOUR_OF_DAY,value);

      if (minute != null)
        value = Integer.parseInt(minute);
      else
        value = 0;
      c.set(Calendar.MINUTE,value);

      if (second != null)
        value = Integer.parseInt(second);
      else
        value = 0;
      c.set(Calendar.SECOND,value);

      c.set(Calendar.MILLISECOND,0);
      return new Date(c.getTimeInMillis());
    }
    catch (NumberFormatException e)
    {
      return null;
    }

  }


}
