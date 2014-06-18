/* $Id: ListJobs.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class lists all jobs presently being handled by the connector framework.
*/
public class ListJobs
{
  public static final String _rcsid = "@(#)$Id: ListJobs.java 988245 2010-08-23 18:39:35Z kwright $";

  private ListJobs()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 0)
    {
      System.err.println("Usage: ListJobs");
      System.err.println("");
      System.err.println("The result will be printed to standard out, will be UTF-8 encoded, and will contain the following columns:");
      System.err.println("    identifier,description,connection,startmode,runmode,hopcountmode,priority,rescaninterval,expirationinterval,reseedinterval");
      System.exit(1);
    }

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription[] jobs = jobManager.getAllJobs();

      int i = 0;
      while (i < jobs.length)
      {
        IJobDescription job = jobs[i++];

        //identifier,description,connection,startmode,runmode,hopcountmode,priority,rescaninterval,expirationinterval,reseedinterval

        UTF8Stdout.println(job.getID().toString()+","+
          ((job.getDescription()==null)?"":commaEscape(job.getDescription()))+","+
          commaEscape(job.getConnectionName())+","+
          startModeMap(job.getStartMethod())+","+
          runModeMap(job.getType())+","+
          hopcountModeMap(job.getHopcountMode())+","+
          Integer.toString(job.getPriority())+","+
          presentInterval(job.getInterval())+","+
          presentInterval(job.getExpiration())+","+
          presentInterval(job.getReseedInterval()));
      }
      System.err.println("Job list done");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
    }
  }

  protected static String presentInterval(Long interval)
  {
    if (interval == null)
      return "infinite";
    return interval.toString();
  }

  protected static String startModeMap(int startMethod)
  {
    switch (startMethod)
    {
    case IJobDescription.START_WINDOWBEGIN:
      return "schedule window start";
    case IJobDescription.START_WINDOWINSIDE:
      return "schedule window anytime";
    case IJobDescription.START_DISABLE:
      return "manual";
    default:
      return "unknown";
    }
  }

  protected static String runModeMap(int type)
  {
    switch (type)
    {
    case IJobDescription.TYPE_CONTINUOUS:
      return "continuous";
    case IJobDescription.TYPE_SPECIFIED:
      return "scan once";
    default:
      return "unknown";
    }
  }

  protected static String hopcountModeMap(int mode)
  {
    switch (mode)
    {
    case IJobDescription.HOPCOUNT_ACCURATE:
      return "accurate";
    case IJobDescription.HOPCOUNT_NODELETE:
      return "no delete";
    case IJobDescription.HOPCOUNT_NEVERDELETE:
      return "never delete";
    default:
      return "unknown";
    }
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
