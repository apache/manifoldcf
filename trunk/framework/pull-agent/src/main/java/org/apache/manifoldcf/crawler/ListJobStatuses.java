/* $Id: ListJobStatuses.java 991295 2010-08-31 19:12:14Z kwright $ */

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

/** This class lists the status of all jobs presently being handled by the connector framework.
*/
public class ListJobStatuses
{
  public static final String _rcsid = "@(#)$Id: ListJobStatuses.java 991295 2010-08-31 19:12:14Z kwright $";

  private ListJobStatuses()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 0)
    {
      System.err.println("Usage: ListJobStatuses");
      System.err.println("");
      System.err.println("The result will be printed to standard out, UTF-8 encoded, and will contain the following columns:");
      System.err.println("    identifier,description,status,inqueue,outstanding,processed,starttime,endtime,errortext");
      System.exit(1);
    }

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      JobStatus[] jobStatuses = jobManager.getAllStatus();

      int i = 0;
      while (i < jobStatuses.length)
      {
        JobStatus jobStatus = jobStatuses[i++];

        // identifier,description,status,inqueue,outstanding,processed,starttime,endtime,errortext
        UTF8Stdout.println(jobStatus.getJobID().toString()+","+
          ((jobStatus.getDescription()==null)?"":commaEscape(jobStatus.getDescription()))+","+
          statusMap(jobStatus.getStatus())+","+
          new Long(jobStatus.getDocumentsInQueue()).toString()+","+
          new Long(jobStatus.getDocumentsOutstanding()).toString()+","+
          new Long(jobStatus.getDocumentsProcessed()).toString()+","+
          new Long(jobStatus.getStartTime()).toString()+","+
          new Long(jobStatus.getEndTime()).toString()+","+
          ((jobStatus.getErrorText()==null)?"":commaEscape(jobStatus.getErrorText())));

      }
      System.err.println("Job status list done");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
    }
  }

  protected static String statusMap(int status)
  {
    switch (status)
    {
    case JobStatus.JOBSTATUS_NOTYETRUN:
      return "not yet run";
    case JobStatus.JOBSTATUS_RUNNING:
      return "running";
    case JobStatus.JOBSTATUS_PAUSED:
      return "paused";
    case JobStatus.JOBSTATUS_COMPLETED:
      return "done";
    case JobStatus.JOBSTATUS_WINDOWWAIT:
      return "waiting";
    case JobStatus.JOBSTATUS_STARTING:
      return "starting up";
    case JobStatus.JOBSTATUS_DESTRUCTING:
      return "cleaning up";
    case JobStatus.JOBSTATUS_ERROR:
      return "error";
    case JobStatus.JOBSTATUS_ABORTING:
      return "aborting";
    case JobStatus.JOBSTATUS_RESTARTING:
      return "restarting";
    case JobStatus.JOBSTATUS_RUNNING_UNINSTALLED:
      return "running no connector";
    case JobStatus.JOBSTATUS_JOBENDCLEANUP:
      return "terminating";
    case JobStatus.JOBSTATUS_JOBENDNOTIFICATION:
      return "notifying";
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
      if (x < ' ')
        x = ' ';
      if (x == '\\' || x == ',')
        output.append("\\");
      output.append(x);
    }
    return output.toString();
  }

}
