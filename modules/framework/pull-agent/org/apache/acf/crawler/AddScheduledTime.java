/* $Id: AddScheduledTime.java 939667 2010-04-30 13:34:54Z kwright $ */

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
package org.apache.acf.crawler;

import java.io.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class AddScheduledTime
{
        public static final String _rcsid = "@(#)$Id: AddScheduledTime.java 939667 2010-04-30 13:34:54Z kwright $";

        private AddScheduledTime()
        {
        }

        public static void main(String[] args)
        {
                if (args.length != 8)
                {
                        System.err.println("Usage: AddScheduledTime <jobid> <interval_minutes> <day_of_week_list>");
                        System.err.println("            <day_of_month_list> <month_list> <year_list> <hour_list> <minute_list>");
                        System.err.println("");
                        System.err.println("If <interval_minutes> is an empty string, no interval limit");
                        System.err.println("All lists are comma-separated");
                        System.err.println("All list values can be empty to indicate no constraint");
                        System.err.println("Days of week are lower case, full day names, e.g. tuesday");
                        System.err.println("Months are full month names in lower case, e.g. january");
                        System.err.println("Years are full year values, e.g. 1999");
                        System.err.println("Hours include am or pm in lower case, e.g. 12am or 2pm");

                        System.exit(1);
                }

                String jobID = args[0];
                String interval = args[1];
                String dayOfWeekList = args[2];
                String dayOfMonthList = args[3];
                String monthList = args[4];
                String yearList = args[5];
                String hourList = args[6];
                String minuteList = args[7];

                try
                {
                        LCF.initializeEnvironment();
                        IThreadContext tc = ThreadContextFactory.make();
                        IJobManager jobManager = JobManagerFactory.make(tc);

                        IJobDescription desc = jobManager.load(new Long(jobID));
                        if (desc == null)
                                throw new LCFException("No such job: '"+jobID+"'");

                        ScheduleRecord sr = new ScheduleRecord(
                                parseDayOfWeek(dayOfWeekList),
                                parseMonthOfYear(monthList),
                                parseDayOfMonth(dayOfMonthList),
                                parseYear(yearList),
                                parseHourOfDay(hourList),
                                parseMinutes(minuteList),
                                null,
                                (interval.length()>0)?new Long(interval):null);

                        desc.addScheduleRecord(sr);
                        jobManager.save(desc);

                        System.out.println("Job updated");
                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }

        protected static EnumeratedValues parseDayOfWeek(String list)
                throws Exception
        {
                if (list.length() == 0)
                        return null;

                StringBuffer sb = new StringBuffer();
                int index = 0;
                while (true)
                {
                        if (index != 0)
                                sb.append(",");
                        int newIndex = list.indexOf(",",index);
                        if (newIndex == -1)
                        {
                                sb.append(new Integer(mapToWeekday(list.substring(index))).toString());
                                break;
                        }
                        sb.append(new Integer(mapToWeekday(list.substring(index,newIndex))).toString());
                        index = newIndex+1;
                }
                return new EnumeratedValues(sb.toString());
        }

        protected static int mapToWeekday(String day)
                throws Exception
        {
                if (day.equals("sunday"))
                        return 0;
                if (day.equals("monday"))
                        return 1;
                if (day.equals("tuesday"))
                        return 2;
                if (day.equals("wednesday"))
                        return 3;
                if (day.equals("thursday"))
                        return 4;
                if (day.equals("friday"))
                        return 5;
                if (day.equals("saturday"))
                        return 6;
                throw new LCFException("Bad day of week: '"+day+"'");
        }

        protected static EnumeratedValues parseMonthOfYear(String list)
                throws Exception
        {
                if (list.length() == 0)
                        return null;

                StringBuffer sb = new StringBuffer();
                int index = 0;
                while (true)
                {
                        if (index != 0)
                                sb.append(",");
                        int newIndex = list.indexOf(",",index);
                        if (newIndex == -1)
                        {
                                sb.append(new Integer(mapToMonth(list.substring(index))).toString());
                                break;
                        }
                        sb.append(new Integer(mapToMonth(list.substring(index,newIndex))).toString());
                        index = newIndex+1;
                }
                return new EnumeratedValues(sb.toString());

        }

        protected static int mapToMonth(String day)
                throws Exception
        {
                if (day.equals("january"))
                        return 0;
                if (day.equals("february"))
                        return 1;
                if (day.equals("march"))
                        return 2;
                if (day.equals("april"))
                        return 3;
                if (day.equals("may"))
                        return 4;
                if (day.equals("june"))
                        return 5;
                if (day.equals("july"))
                        return 6;
                if (day.equals("august"))
                        return 7;
                if (day.equals("september"))
                        return 8;
                if (day.equals("october"))
                        return 9;
                if (day.equals("november"))
                        return 10;
                if (day.equals("december"))
                        return 11;

                throw new LCFException("Bad month: '"+day+"'");
        }

        protected static EnumeratedValues parseDayOfMonth(String list)
                throws Exception
        {
                // Zero based internally
                if (list.length() == 0)
                        return null;

                StringBuffer sb = new StringBuffer();
                int index = 0;
                while (true)
                {
                        if (index != 0)
                                sb.append(",");
                        int newIndex = list.indexOf(",",index);
                        if (newIndex == -1)
                        {
                                sb.append(new Integer(new Integer(list.substring(index)).intValue() - 1).toString());
                                break;
                        }
                        sb.append(new Integer(new Integer(list.substring(index,newIndex)).intValue() - 1).toString());
                        index = newIndex+1;
                }
                return new EnumeratedValues(sb.toString());
        }

        protected static EnumeratedValues parseYear(String list)
                throws Exception
        {
                if (list.length() == 0)
                        return null;
                return new EnumeratedValues(list);
        }

        protected static EnumeratedValues parseHourOfDay(String list)
                throws Exception
        {
                if (list.length() == 0)
                        return null;

                StringBuffer sb = new StringBuffer();
                int index = 0;
                while (true)
                {
                        if (index != 0)
                                sb.append(",");
                        int newIndex = list.indexOf(",",index);
                        if (newIndex == -1)
                        {
                                sb.append(new Integer(mapToHour(list.substring(index))).toString());
                                break;
                        }
                        sb.append(new Integer(mapToHour(list.substring(index,newIndex))).toString());
                        index = newIndex+1;
                }
                return new EnumeratedValues(sb.toString());

        }

        protected static int mapToHour(String hour)
                throws Exception
        {
                boolean isPM;
                String value;
                if (hour.endsWith("am"))
                {
                        isPM = false;
                        value = hour.substring(0,hour.length()-2);
                }
                else if (hour.endsWith("pm"))
                {
                        isPM = true;
                        value = hour.substring(0,hour.length()-2);
                }
                else
                {
                        isPM = false;
                        value = hour;
                }

                int rval = new Integer(value).intValue();
                if (rval == 12)
                        rval = 0;
                if (isPM)
                        rval += 12;
                return rval;
        }

        protected static EnumeratedValues parseMinutes(String list)
                throws Exception
        {
                if (list.length() == 0)
                        return null;

                return new EnumeratedValues(list);
        }

}
