/* $Id: RunResultHistory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class runs a result history report.
*/
public class RunResultHistory
{
  public static final String _rcsid = "@(#)$Id: RunResultHistory.java 988245 2010-08-23 18:39:35Z kwright $";

  private RunResultHistory()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 11)
    {
      System.err.println("Usage: RunResultHistory <connection_name> <activity_list> <start_time> <end_time> <entity_regexp> <resultcode_regexp> <sortorder_list> <id_bucket_regexp> <result_bucket_regexp> <start_row> <row_count>");
      System.err.println("<connection_name> is the name of the repository connection");
      System.err.println("<activity_list> is the comma-separated list of activity names to include");
      System.err.println("<start_time> is the earliest time to include, in ms. since epoch [blank if no limit]");
      System.err.println("<end_time> is the latest time to include, in ms. since epoch [blank if no limit]");
      System.err.println("<entity_regexp> describes which document identifiers to include");
      System.err.println("<resultcode_regexp> describes which result codes to include");
      System.err.println("<sortorder_list> a comma-separated list of fields describing the sort order, preceded by + or -");
      System.err.println("    for ascending or descending; the legal field names are: 'idbucket', 'resultcodebucket', 'eventcount'");
      System.err.println("<id_bucket_regexp> is a regular expression which extracts the bucket part from a document identifier");
      System.err.println("<result_bucket_regexp> is a regular expression which extracts the bucket part from a result code");
      System.err.println("<start_row> describes which row to start at, beginning at 0");
      System.err.println("<row_count> indicates the maximum number of rows to include");
      System.err.println("");
      System.err.println("The result will be printed to standard out, will be UTF-8 encoded, and will contain the following columns:");
      System.err.println("    identifier_bucket, resultcode_bucket, event_count");


      System.exit(1);
    }

    String connectionName = args[0];
    String activityList = args[1];
    String startTime = args[2];
    String endTime = args[3];
    String entityRegexp = args[4];
    String resultCodeRegexp = args[5];
    String sortOrder = args[6];
    String entityBucketRegexp = args[7];
    String resultBucketRegexp = args[8];
    String startRow = args[9];
    String rowCount = args[10];

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);

      FilterCriteria filter = parseFilterCriteria(activityList,startTime,endTime,entityRegexp,resultCodeRegexp);
      SortOrder sortOrderValue = parseSortorder(sortOrder);
      BucketDescription entityBucket = parseBucketDescription(entityBucketRegexp);
      BucketDescription resultBucket = parseBucketDescription(resultBucketRegexp);
      int startRowValue = Integer.parseInt(startRow);
      int rowCountValue = Integer.parseInt(rowCount);

      IResultSet result = connManager.genHistoryResultCodes(connectionName,filter,sortOrderValue,resultBucket,entityBucket,startRowValue,rowCountValue);
      int i = 0;
      while (i < result.getRowCount())
      {
        IResultRow row = result.getRow(i++);

        UTF8Stdout.println(commaEscape((String)row.getValue("idbucket"))+","+
          commaEscape((String)row.getValue("resultcodebucket"))+","+
          row.getValue("eventcount").toString());
      }
      System.err.println("History query done");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
    }
  }

  protected static String commaEscape(String input)
  {
    StringBuffer output = new StringBuffer();
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

  protected static FilterCriteria parseFilterCriteria(String activityList, String startTime, String endTime, String entityRegexp, String resultCodeRegexp)
    throws Exception
  {
    String[] activityTypes = parseActivityList(activityList);
    Long startTimeValue;
    if (startTime != null && startTime.length() > 0)
      startTimeValue = new Long(startTime);
    else
      startTimeValue = null;
    Long endTimeValue;
    if (endTime != null && endTime.length() > 0)
      endTimeValue = new Long(endTime);
    else
      endTimeValue = null;

    RegExpCriteria entityRegexpValue = parseRegexp(entityRegexp);
    RegExpCriteria resultCodeRegexpValue = parseRegexp(resultCodeRegexp);

    return new FilterCriteria(activityTypes,startTimeValue,endTimeValue,entityRegexpValue,resultCodeRegexpValue);
  }

  protected static String[] parseActivityList(String activityList)
    throws Exception
  {
    String[] activities = activityList.split(",");
    String[] rval = new String[activities.length];
    int i = 0;
    while (i < rval.length)
    {
      String activity = activities[i].trim();
      rval[i] = activity;
      i++;
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
