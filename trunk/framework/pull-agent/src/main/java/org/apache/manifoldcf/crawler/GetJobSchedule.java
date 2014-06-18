/* $Id: GetJobSchedule.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;
import java.util.*;

/** This class gets the schedule records associated with a job.
*/
public class GetJobSchedule
{
  public static final String _rcsid = "@(#)$Id: GetJobSchedule.java 988245 2010-08-23 18:39:35Z kwright $";

  private GetJobSchedule()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: GetJobSchedule <job_id>");
      System.err.println("");
      System.err.println("The result will be UTF-8 encoded and will consist of the following columns:");
      System.err.println("    daysofweek,years,months,days,hours,minutes,timezone,duration,requestminimum");
      System.exit(1);
    }

    String jobID = args[0];

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);

      IJobDescription job = jobManager.load(new Long(jobID));
      if (job == null)
        throw new ManifoldCFException("No such job: "+jobID);

      int i = 0;
      while (i < job.getScheduleRecordCount())
      {
        ScheduleRecord sr = job.getScheduleRecord(i++);

        // daysofweek,years,months,days,hours,minutes,timezone,duration
        UTF8Stdout.println(enumerate(sr.getDayOfWeek())+","+
          enumerate(sr.getYear())+","+
          enumerate(sr.getMonthOfYear())+","+
          enumerate(sr.getDayOfMonth())+","+
          enumerate(sr.getHourOfDay())+","+
          enumerate(sr.getMinutesOfHour())+","+
          ((sr.getTimezone()==null)?"":sr.getTimezone())+","+
          presentInterval(sr.getDuration())+","+
          (sr.getRequestMinimum()?"true":"false"));
      }
      System.err.println("Schedule list done");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
    }
  }

  protected static String enumerate(EnumeratedValues ev)
  {
    if (ev == null)
      return "any";
    StringBuilder sb = new StringBuilder();
    Iterator iter = ev.getValues();
    boolean first = true;
    while (iter.hasNext())
    {
      Integer val = (Integer)iter.next();
      if (first)
        first = false;
      else
        sb.append(";");
      sb.append(val.toString());
    }
    return sb.toString();
  }

  protected static String presentInterval(Long interval)
  {
    if (interval == null)
      return "infinite";
    else
      return interval.toString();
  }

  protected static String commaEscape(String input)
  {
    StringBuilder output = new StringBuilder();
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
      if (x == '\\' || x == ',')
        output.append("\\");
      output.append(x);
    }
    return output.toString();
  }

}
