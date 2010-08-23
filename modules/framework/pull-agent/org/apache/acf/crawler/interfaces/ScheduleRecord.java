/* $Id: ScheduleRecord.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.interfaces;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import java.util.*;

/** This class describes a single scheduling record, which describes a matching date and time for
* a job to be started or unblocked.  It also describes the throttle rate that should be in effect
* for the interval.  This class is immutable.
*/
public class ScheduleRecord
{
  public static final String _rcsid = "@(#)$Id: ScheduleRecord.java 921329 2010-03-10 12:44:20Z kwright $";

  // Absolute job-triggering times
  protected EnumeratedValues dayOfWeek = null;
  protected EnumeratedValues monthOfYear = null;
  protected EnumeratedValues dayOfMonth = null;
  protected EnumeratedValues year = null;
  protected EnumeratedValues hourOfDay = null;
  protected EnumeratedValues minutesOfHour = null;
  protected String timezone = null;
  protected Long duration = null;

  /** Constructor.
  *@param dayOfWeek is the day-of-week enumeration.
  *@param monthOfYear is the month-of-year enumeration.
  *@param dayOfMonth is the day-of-month enumeration.
  *@param year is the year enumeration.
  *@param hourOfDay is the time of day enumeration.
  *@param minutesOfHour is the minutes enumeration.
  *@param timezone is the timezone.
  *@param duration is the window duration, or null if infinite.
  */
  public ScheduleRecord(EnumeratedValues dayOfWeek,
    EnumeratedValues monthOfYear,
    EnumeratedValues dayOfMonth,
    EnumeratedValues year,
    EnumeratedValues hourOfDay,
    EnumeratedValues minutesOfHour,
    String timezone,
    Long duration)
  {
    this.dayOfWeek = dayOfWeek;
    this.monthOfYear = monthOfYear;
    this.dayOfMonth = dayOfMonth;
    this.year = year;
    this.hourOfDay = hourOfDay;
    this.minutesOfHour = minutesOfHour;
    this.timezone = timezone;
    this.duration = duration;
  }

  /** Get the day of week.
  *@return the enumeration or null.
  */
  public EnumeratedValues getDayOfWeek()
  {
    return dayOfWeek;
  }

  /** Get the month of year.
  *@return the enumeration or null.
  */
  public EnumeratedValues getMonthOfYear()
  {
    return monthOfYear;
  }

  /** Get the day of month.
  *@return the enumeration or null.
  */
  public EnumeratedValues getDayOfMonth()
  {
    return dayOfMonth;
  }

  /** Get the year.
  *@return the enumeration or null.
  */
  public EnumeratedValues getYear()
  {
    return year;
  }

  /** Get the hour of the day.
  *@return the enumeration or null.
  */
  public EnumeratedValues getHourOfDay()
  {
    return hourOfDay;
  }

  /** Get the minutes of the hour.
  *@return the enumeration or null.
  */
  public EnumeratedValues getMinutesOfHour()
  {
    return minutesOfHour;
  }

  /** Get the timezone.
  *@return the timezone or null.
  */
  public String getTimezone()
  {
    return timezone;
  }

  /** Get the window duration.
  *@return the duration or null.
  */
  public Long getDuration()
  {
    return duration;
  }



}
