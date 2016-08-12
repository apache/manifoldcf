/* $Id: RunQueueStatus.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class runs a queue status report.  It prints the report data in a comma-separated form to stdout.
*/
public class RunQueueStatus
{
  public static final String _rcsid = "@(#)$Id: RunQueueStatus.java 988245 2010-08-23 18:39:35Z kwright $";

  private RunQueueStatus()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 10)
    {
      System.err.println("Usage: RunQueueStatus <connection_name> <job_id_list> <time_ms> <match_state_list> <match_status_list> <match_regexp> <sortorder_list> <bucket_regexp> <start_row> <row_count>");
      System.err.println("<connection_name> is the name of the connection");
      System.err.println("<job_id_list> is a comma-separated list of job identifiers");
      System.err.println("<time_ms> is the time for which each document state and status will be calculated");
      System.err.println("<match_state_list> is a comma-separated list of states, one of:");
      System.err.println("    'neverprocessed', 'previouslyprocessed'");
      System.err.println("<match_status_list> is a comma-separated list of statuses, one of:");
      System.err.println("    'inactive', 'processing', 'expiring', 'deleting',");
      System.err.println("    'readyforprocessing', 'readyforexpiration', 'waitingforprocessing', 'waitingforexpiration', 'waitingforever'");
      System.err.println("<match_regexp> is the regular expression that describes which document identifiers to include");
      System.err.println("<sortorder_list> is a comma-separated list of fields describing the sort order, preceded by + or -");
      System.err.println("    for ascending or descending; the legal field names are: 'idbucket', 'inactive',");
      System.err.println("    'processing', 'expiring', 'deleting', 'processready', 'expireready',");
      System.err.println("    'processwaiting', 'expirewaiting', 'waitingforever'");
      System.err.println("<bucket_regexp> is the regular expression which identifies a document's bucket from its identifier");
      System.err.println("<start_row> is the number of the first row to include, starting with 0");
      System.err.println("<row_count> is the maximum number of rows to include");
      System.err.println("");
      System.err.println("The printed result is UTF-8 encoded and has the following columns, in order:");
      System.err.println("    id_bucket, inactive_count, processing_count, expiring_count, deleting_count,");
      System.err.println("    process_ready_count, expire_ready_count, process_waiting_count, expire_waiting_count, waiting_forever_count");

      System.exit(1);
    }

    String connectionName = args[0];
    String jobList = args[1];
    String currentTime = args[2];
    String matchStateList = args[3];
    String matchStatusList = args[4];
    String matchRegexp = args[5];
    String sortorderList = args[6];
    String bucketRegexp = args[7];
    String startRow = args[8];
    String rowCount = args[9];


    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);

      StatusFilterCriteria filter = parseFilterCriteria(jobList,currentTime,matchRegexp,matchStateList,matchStatusList);
      SortOrder sortOrderValue = parseSortorder(sortorderList);
      int startRowValue = Integer.parseInt(startRow);
      int rowCountValue = Integer.parseInt(rowCount);
      BucketDescription bucketDescription = parseBucketDescription(bucketRegexp);

      IResultSet result = jobManager.genQueueStatus(connectionName,filter,sortOrderValue,bucketDescription,startRowValue,rowCountValue);
      int i = 0;
      while (i < result.getRowCount())
      {
        IResultRow row = result.getRow(i++);
        UTF8Stdout.println(commaEscape((String)row.getValue("idbucket"))+","+
          row.getValue("inactive").toString()+","+
          row.getValue("processing").toString()+","+
          row.getValue("expiring").toString()+","+
          row.getValue("deleting").toString()+","+
          row.getValue("processready").toString()+","+
          row.getValue("expireready").toString()+","+
          row.getValue("processwaiting").toString()+","+
          row.getValue("expirewaiting").toString()+","+
          row.getValue("waitingforever").toString());

      }
      System.err.println("Status query done");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
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

  protected static StatusFilterCriteria parseFilterCriteria(String jobList, String currentTime, String matchRegexp, String matchStateList, String matchStatusList)
    throws Exception
  {
    Long[] jobIds = parseJobList(jobList);
    RegExpCriteria matchRegexpValue = parseRegexp(matchRegexp);
    long currentTimeValue = new Long(currentTime).longValue();
    int[] matchStateValue = parseStateList(matchStateList);
    int[] matchStatusValue = parseStatusList(matchStatusList);
    return new StatusFilterCriteria(jobIds,currentTimeValue,matchRegexpValue, matchStateValue,matchStatusValue);
  }

  protected static Long[] parseJobList(String jobList)
    throws Exception
  {
    String[] jobs = jobList.split(",");
    Long[] rval = new Long[jobs.length];
    int i = 0;
    while (i < rval.length)
    {
      String job = jobs[i].trim();
      rval[i] = new Long(job);
      i++;
    }
    return rval;
  }

  private static HashMap stateMap;
  static
  {
    stateMap = new HashMap();
    stateMap.put("neverprocessed",new Integer(IJobManager.DOCSTATE_NEVERPROCESSED));
    stateMap.put("previouslyprocessed",new Integer(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED));
  }

  protected static int[] parseStateList(String stateList)
    throws Exception
  {
    String[] states = stateList.split(",");
    int[] rval = new int[states.length];
    int i = 0;
    while (i < rval.length)
    {
      String state = states[i].trim();
      Integer value = (Integer)stateMap.get(state.toLowerCase(Locale.ROOT));
      if (value == null)
        throw new ManifoldCFException("State value of '"+state+"' is illegal");
      rval[i++] = value.intValue();
    }
    return rval;
  }

  private static HashMap statusMap;
  static
  {
    statusMap = new HashMap();
    statusMap.put("inactive",new Integer(IJobManager.DOCSTATUS_INACTIVE));
    statusMap.put("processing",new Integer(IJobManager.DOCSTATUS_PROCESSING));
    statusMap.put("expiring",new Integer(IJobManager.DOCSTATUS_EXPIRING));
    statusMap.put("deleting",new Integer(IJobManager.DOCSTATUS_DELETING));
    statusMap.put("readyforprocessing",new Integer(IJobManager.DOCSTATUS_READYFORPROCESSING));
    statusMap.put("readyforexpiration",new Integer(IJobManager.DOCSTATUS_READYFOREXPIRATION));
    statusMap.put("waitingforprocessing",new Integer(IJobManager.DOCSTATUS_WAITINGFORPROCESSING));
    statusMap.put("waitingforexpiration",new Integer(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION));
    statusMap.put("waitingforever",new Integer(IJobManager.DOCSTATUS_WAITINGFOREVER));
  }

  protected static int[] parseStatusList(String statusList)
    throws Exception
  {
    String[] statuses = statusList.split(",");
    int[] rval = new int[statuses.length];
    int i = 0;
    while (i < rval.length)
    {
      String status = statuses[i].trim();
      Integer value = (Integer)statusMap.get(status.toLowerCase(Locale.ROOT));
      if (value == null)
        throw new ManifoldCFException("Status value of '"+status+"' is illegal");
      rval[i++] = value.intValue();
    }
    return rval;

  }

  protected static RegExpCriteria parseRegexp(String regexp)
    throws Exception
  {
    if (regexp == null || regexp.length() == 0)
      return null;
    return new RegExpCriteria(regexp,true);
  }

  protected static BucketDescription parseBucketDescription(String bucketDesc)
    throws Exception
  {
    return new BucketDescription(bucketDesc,false);
  }

  protected static SortOrder parseSortorder(String sortorder)
    throws Exception
  {
    SortOrder so = new SortOrder();
    if (sortorder == null || sortorder.length() == 0)
      return so;
    String[] columns = sortorder.split(",");
    int i = 0;
    while (i < columns.length)
    {
      String column = columns[i++].trim();
      int clickCount = 1;
      if (column.startsWith("+"))
        column = column.substring(1);
      else if (column.startsWith("-"))
      {
        clickCount++;
        column = column.substring(1);
      }
      while (clickCount > 0)
      {
        clickCount--;
        so.clickColumn(column);
      }
    }
    return so;
  }

}
