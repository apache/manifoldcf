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
package org.apache.lcf.crawler.repository;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import java.util.*;

/** This class is the manager for the history records belonging to the repository connector.
*/
public class RepositoryHistoryManager extends org.apache.lcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Fields
  protected final static String idField = "id";
  protected final static String ownerNameField = "owner";
  protected final static String startTimeField = "starttime";
  protected final static String endTimeField = "endtime";
  protected final static String dataSizeField = "datasize";
  protected final static String activityTypeField = "activitytype";
  protected final static String entityIdentifierField = "entityid";
  protected final static String resultCodeField = "resultcode";
  protected final static String resultDescriptionField = "resultdesc";

  /** Counter for kicking off analyze */
  protected static AnalyzeTracker tracker = new AnalyzeTracker();

  /** Thread context */
  protected IThreadContext threadContext;

  /** Constructor.
  *@param database is the database instance.
  */
  public RepositoryHistoryManager(IThreadContext tc, IDBInterface database)
    throws LCFException
  {
    super(database,"repohistory");
    this.threadContext = tc;
  }

  /** Install or upgrade the table.
  *@param parentTable is the parent table.
  *@param parentField is the parent field.
  */
  public void install(String parentTable, String parentField)
    throws LCFException
  {
    // Always have an outer loop, in case of upgrade
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(ownerNameField,new ColumnDescription("VARCHAR(32)",false,false,parentTable,parentField,false));
        map.put(idField,new ColumnDescription("BIGINT",true,false,null,null,false));
        map.put(startTimeField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(endTimeField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(dataSizeField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(activityTypeField,new ColumnDescription("VARCHAR(64)",false,false,null,null,false));
        map.put(entityIdentifierField,new ColumnDescription("LONGTEXT",false,false,null,null,false));
        map.put(resultCodeField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(resultDescriptionField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code.
	ColumnDescription cd = (ColumnDescription)existing.get(activityTypeField);
	if (cd.getTypeString().toUpperCase().equals("VARCHAR(32)"))
	{
	  HashMap alterMap = new HashMap();
	  alterMap.put(activityTypeField,new ColumnDescription("VARCHAR(64)",false,false,null,null,false));
	  performAlter(null,alterMap,null,null);
	}
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerNameField});
      IndexDescription startTimeIndex = new IndexDescription(false,new String[]{startTimeField});
      IndexDescription endTimeIndex = new IndexDescription(false,new String[]{endTimeField});
      IndexDescription activityTypeIndex = new IndexDescription(false,new String[]{activityTypeField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        else if (startTimeIndex != null && id.equals(startTimeIndex))
          startTimeIndex = null;
        else if (endTimeIndex != null && id.equals(endTimeIndex))
          endTimeIndex = null;
        else if (activityTypeIndex == null && id.equals(activityTypeIndex))
          activityTypeIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);
      if (startTimeIndex != null)
        performAddIndex(null,startTimeIndex);
      if (endTimeIndex != null)
        performAddIndex(null,endTimeIndex);
      if (activityTypeIndex != null)
        performAddIndex(null,activityTypeIndex);

      break;

    }
  }

  /** Uninstall the table.
  */
  public void deinstall()
    throws LCFException
  {
    performDrop(null);
  }

  /** Delete all records associated with given owner.
  *@param owner is the name of the owner.
  *@param invKeys are the invalidation keys.
  */
  public void deleteOwner(String owner, StringSet invKeys)
    throws LCFException
  {
    ArrayList params = new ArrayList();
    params.add(owner);
    performDelete("WHERE "+ownerNameField+"=?",params,invKeys);
  }

  /** Add row to table, and reanalyze if necessary.
  */
  public Long addRow(String connectionName, long startTime, long endTime, long dataSize, String activityType,
    String entityIdentifier, String resultCode, String resultDescription)
    throws LCFException
  {
    Long id = new Long(IDFactory.make(threadContext));
    HashMap map = new HashMap();
    map.put(idField,id);
    map.put(ownerNameField,connectionName);
    map.put(startTimeField,new Long(startTime));
    map.put(endTimeField,new Long(endTime));
    map.put(dataSizeField,new Long(dataSize));
    map.put(activityTypeField,activityType);
    map.put(entityIdentifierField,entityIdentifier);
    if (resultCode != null)
      map.put(resultCodeField,resultCode);
    if (resultDescription != null)
      map.put(resultDescriptionField,resultDescription);
    performInsert(map,null);
    conditionallyAnalyzeInsert();
    return id;
  }

  // For result analysis, we make heavy use of Postgresql's more advanced posix regular expression
  // handling.  The queries in general are fairly messy.  There's a "front aligned" way of doing things,
  // which uses the start time of a row and finds everything that overlaps the interval from "start time"
  // to "start time + interval".  Then there's a "rear aligned"" way of doing things, which uses the
  // time range from "end time - interval" to "end time".  Both sets of data must be evaluated to have a
  // complete set of possible unique window positions.
  //
  // Some of the examples below only use one or the other alignment; they're meant to be illustrative rather
  // than complete.
  //
  // 1) How to come up with the "total count" or "total bytes" of the events in the time window:
  //
  // SELECT substring(entityid from '<expr>') AS entitybucket, COUNT('x') AS eventcount
  //      FROM table WHERE starttime > xxx AND endtime <= yyy AND <everything else> GROUP BY entitybucket
  // SELECT substring(entityid from '<expr>') AS entitybucket, SUM(bytecount) AS bytecount
  //      FROM table WHERE starttime > xxx AND endtime <= yyy AND <everything else> GROUP BY entitybucket
  //
  // Sample queries tried against test table:
  // SELECT substring(url from 'gov$') AS urlbucket, COUNT('x') AS eventcount, MIN(starttime) as minstarttime, MAX(endtime) AS maxendtime FROM testtable GROUP BY urlbucket;
  // SELECT substring(lower(url) from 'gov$') AS urlbucket, SUM(bytes) AS bytecount, MIN(starttime) as minstarttime, MAX(endtime) AS maxendtime FROM testtable GROUP BY urlbucket;
  // SELECT substring(upper(url) from 'gov$') AS urlbucket, COUNT('x') AS eventcount, MIN(starttime) as minstarttime, MAX(endtime) AS maxendtime FROM testtable GROUP BY urlbucket;
  //
  // 2) How to find a set of rows within the interval window for each row in the greater range (FRONT ALIGNED!!!):
  //
  // SELECT t0.url AS starturl,t0.starttime AS starttime,t1.url AS secondurl,t1.starttime AS secondstart,t1.endtime AS secondend FROM testtable t0,testtable t1
  //      WHERE t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime
  //
  // 3) Another way to do it (REAR ALIGNED!!!):
  //
  // SELECT t0.url AS starturl,t0.endtime AS endtime,t1.url AS secondurl,t1.starttime AS secondstart,t1.endtime AS secondend FROM testtable t0,testtable t1
  //      WHERE t1.starttime < t0.endtime AND t1.endtime > t0.endtime - 15
  //
  // 4) How to find the byte count for each of the intervals:
  //
  // SELECT t0.url AS starturl, t0.starttime AS windowstart, SUM(t1.bytes) AS bytecount FROM testtable t0, testtable t1
  //       WHERE t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime GROUP BY starturl,windowstart;
  //
  // 5) How to find the byte count per bucket for each of the intervals:
  //
  // SELECT substring(t0.url from '^.*(gov|com)$') AS bucket, t0.starttime AS windowstart, SUM(t1.bytes) AS bytecount FROM testtable t0, testtable t1
  //      WHERE substring(t0.url from '^.*(gov|com)$')=substring(t1.url from '^.*(gov|com)$') AND t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime GROUP BY bucket,windowstart;
  //
  // 6) How to find the max byte count for the highest interval for each bucket:
  //
  // SELECT t2.bucket AS bucketname, MAX(t2.bytecount) AS maxbytecount FROM (SELECT substring(t0.url from '^.*(gov|com)$') AS bucket, t0.starttime AS windowstart, SUM(t1.bytes) AS bytecount FROM testtable t0, testtable t1
  //      WHERE substring(t0.url from '^.*(gov|com)$')=substring(t1.url from '^.*(gov|com)$') AND t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime GROUP BY bucket,windowstart) t2 GROUP BY bucketname;
  //
  // 7) But, how do we include the right start time?  We want the start time from the row that yielded the max bytecount!
  //    So, use select distinct:
  //
  // SELECT DISTINCT ON (bucketname) t2.bucket AS bucketname, t2.bytecount AS maxbytecount, t2.windowstart AS windowstart FROM (SELECT substring(t0.url from '^.*(gov|com)$') AS bucket, t0.starttime AS windowstart, SUM(t1.bytes) AS bytecount FROM testtable t0, testtable t1
  //      WHERE substring(t0.url from '^.*(gov|com)$')=substring(t1.url from '^.*(gov|com)$') AND t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime GROUP BY bucket,windowstart) t2 ORDER BY bucketname ASC,maxbytecount DESC;
  //
  // 8) How do we account for boundary conditions?  E.g., fetches that start within the window but go over the window boundary?
  //    A: We can try to prorate based on window size.  This would involve a more complex query:
  //
  // ... least(t0.starttime + <interval>,t1.endtime) - greatest(t0.starttime,t1.starttime) AS overlaptime ...
  //
  // 9) Prorated byte count, FRONT ALIGNED form and BACK ALIGNED form:
  //
  // ... bytes * (least(t0.starttime + <interval>,t1.endtime) - greatest(t0.starttime,t1.starttime))/(t1.endtime-t1.starttime) AS bytecount ...
  // OR
  // ... bytes * (least(t0.endtime,t1.endtime) - greatest(t0.endtime - <interval>,t1.starttime))/(t1.endtime-t1.starttime) AS bytecount ...
  //
  // But, our version of postgresql doesn't know about greatest() and least(), so do this:
  //
  // SELECT t0.url AS starturl,t0.starttime AS starttime,t1.url AS secondurl,t1.starttime AS secondstart,t1.endtime AS secondend,
  //      t1.bytes AS fullbytes,
  //      t1.bytes * ((case when t0.starttime + 15<t1.endtime then t0.starttime + 15 else t1.endtime end) -
  //                  (case when t0.starttime>t1.starttime then t0.starttime else t1.starttime end))/(t1.endtime - t1.starttime) AS proratedbytes
  //      FROM testtable t0,testtable t1 WHERE t1.starttime < t0.starttime + 15 AND t1.endtime > t0.starttime

  /** Get a simple history, based on the passed-in filtering criteria and sort order.
  * The resultset returned should have the following columns: "activity","starttime","elapsedtime","resultcode","resultdesc","bytes","identifier".
  */
  public IResultSet simpleReport(String connectionName, FilterCriteria criteria, SortOrder sort, int startRow, int maxRowCount)
    throws LCFException
  {
    // Build the query.
    StringBuffer sb = new StringBuffer("SELECT ");
    sb.append(idField).append(" AS id,").append(activityTypeField).append(" AS activity,").append(startTimeField).append(" AS starttime,(")
      .append(endTimeField).append("-").append(startTimeField).append(")")
      .append(" AS elapsedtime,").append(resultCodeField).append(" AS resultcode,").append(resultDescriptionField)
      .append(" AS resultdesc,").append(dataSizeField).append(" AS bytes,").append(entityIdentifierField)
      .append(" AS identifier FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,criteria,false);
    // Note well: We can't order by "identifier" in all databases, so in order to guarantee order we use "id".  This will force a specific internal
    // order for the OFFSET/LIMIT clause.  We include "starttime" because that's the default ordering.
    addOrdering(sb,new String[]{"starttime","id"},sort);
    addLimits(sb,startRow,maxRowCount);
    return performQuery(sb.toString(),null,null,null,maxRowCount);
  }

  /** Count the number of rows specified by a given set of criteria.  This can be used to make decisions
  * as to whether a query based on those rows will complete in an acceptable amount of time.
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@return the number of rows included by the criteria.
  */
  public long countHistoryRows(String connectionName, FilterCriteria criteria)
    throws LCFException
  {
    StringBuffer sb = new StringBuffer("SELECT COUNT(*) AS countcol FROM ");
    sb.append(getTableName());
    addCriteria(sb,"",connectionName,criteria,false);
    IResultSet set = performQuery(sb.toString(),null,null,null);
    if (set.getRowCount() < 1)
      throw new LCFException("Expected at least one row");
    IResultRow row = set.getRow(0);
    Long value = (Long)row.getValue("countcol");
    return value.longValue();
  }

  /** Get a bucketed history, with sliding window, of maximum activity level.
  * The resultset returned should have the following columns: "starttime","endtime","activitycount","idbucket".
  * An activity is counted as being within the interval window on a prorated basis, which can lead to fractional
  * counts.
  */
  public IResultSet maxActivityCountReport(String connectionName, FilterCriteria filterCriteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws LCFException
  {
    // The query we will generate here looks like this:
    // SELECT *
    //   FROM
    //     (SELECT DISTINCT ON (idbucket) t3.bucket AS idbucket, t3.activitycount AS activitycount,
    //                               t3.windowstart AS starttime, t3.windowend AS endtime
    //        FROM (SELECT * FROM (SELECT t0.bucket AS bucket, t0.starttime AS windowstart, t0.starttime + <interval> AS windowend,
    //                   SUM(CAST(((case when t0.starttime + <interval> < t1.endtime then t0.starttime + <interval> else t1.endtime end) -
    //                     (case when t0.starttime>t1.starttime then t0.starttime else t1.starttime end)) AS double precision)
    //                      / CAST((t1.endtime - t1.starttime) AS double precision)) AS activitycount
    //                   FROM (SELECT DISTINCT substring(entityid from '<bucketregexp>') AS bucket, starttime FROM repohistory WHERE <criteria>) t0, repohistory t1
    //                   WHERE t0.bucket=substring(t1.entityid from '<bucket_regexp>')
    //                      AND t1.starttime < t0.starttime + <interval> AND t1.endtime > t0.starttime
    //                      AND <criteria on t1>
    //                          GROUP BY bucket,windowstart,windowend
    //              UNION SELECT t0a.bucket AS bucket, t0a.endtime - <interval> AS windowstart, t0a.endtime AS windowend,
    //                   SUM(CAST(((case when t0a.endtime < t1a.endtime then t0a.endtime else t1a.endtime end) -
    //                     (case when t0a.endtime - <interval> > t1a.starttime then t0a.endtime - <interval> else t1a.starttime end)) AS double precision)
    //                      / CAST((t1a.endtime - t1a.starttime) AS double precision)) AS activitycount
    //                   FROM (SELECT DISTINCT substring(entityid from '<bucketregexp>') AS bucket, endtime FROM repohistory WHERE <criteria>) t0a, repohistory t1a
    //                   WHERE t0a.bucket=substring(t1a.entityid from '<bucket_regexp>')
    //                      AND (t1a.starttime < t0a.endtime AND t1a.endtime > t0a.endtime - <interval>
    //                      AND <criteria on t1a>
    //                          GROUP BY bucket,windowstart,windowend) t2
    //                              ORDER BY bucket ASC,activitycount DESC) t3) t4 ORDER BY xxx LIMIT yyy OFFSET zzz;
    //
    // There are two different intervals being considered; each one may independently contribute possible
    // items to the list.  One is based on the start time of the current record; the other is based on the
    // end time of the current record.  That's why there are two inner clauses with a UNION.

    StringBuffer sb = new StringBuffer("SELECT * FROM (SELECT DISTINCT ON (idbucket) t3.bucket AS idbucket, t3.activitycount AS activitycount, t3.windowstart AS starttime, t3.windowend AS endtime FROM (SELECT * FROM (SELECT ");

    // Turn the interval into a string, since we'll need it a lot.
    String intervalString = new Long(interval).toString();

    sb.append("t0.bucket AS bucket, t0.").append(startTimeField).append(" AS windowstart, t0.")
      .append(startTimeField).append("+").append(intervalString).append(" AS windowend, SUM(CAST(((CASE WHEN t0.")
      .append(startTimeField).append("+").append(intervalString).append("<t1.").append(endTimeField)
      .append(" THEN t0.").append(startTimeField).append("+").append(intervalString).append(" ELSE t1.")
      .append(endTimeField).append(" END) - (CASE WHEN t0.").append(startTimeField).append(">t1.").append(startTimeField)
      .append(" THEN t0.").append(startTimeField).append(" ELSE t1.").append(startTimeField)
      .append(" END)) AS DOUBLE PRECISION) / CAST((t1.").append(endTimeField).append("-t1.").append(startTimeField)
      .append(") AS DOUBLE PRECISION)) AS activitycount FROM (SELECT DISTINCT ");
    addBucketExtract(sb,"",entityIdentifierField,idBucket);
    sb.append(" AS bucket,").append(startTimeField).append(" FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,filterCriteria,false);
    sb.append(") t0,")
      .append(getTableName()).append(" t1 WHERE ");
    sb.append("t0.bucket=");
    addBucketExtract(sb,"t1.",entityIdentifierField,idBucket);
    sb.append(" AND t1.").append(startTimeField).append("<t0.").append(startTimeField).append("+").append(intervalString)
      .append(" AND t1.").append(endTimeField).append(">t0.").append(startTimeField);
    addCriteria(sb,"t1.",connectionName,filterCriteria,true);
    sb.append(" GROUP BY bucket,windowstart,windowend UNION SELECT ");
    sb.append("t0a.bucket AS bucket, t0a.").append(endTimeField).append("-").append(intervalString).append(" AS windowstart, t0a.")
      .append(endTimeField).append(" AS windowend, SUM(CAST(((CASE WHEN t0a.")
      .append(endTimeField).append("<t1a.").append(endTimeField)
      .append(" THEN t0a.").append(endTimeField).append(" ELSE t1a.")
      .append(endTimeField).append(" END) - (CASE WHEN t0a.").append(endTimeField).append("-").append(intervalString)
      .append(">t1a.").append(startTimeField)
      .append(" THEN t0a.").append(endTimeField).append("-").append(intervalString).append(" ELSE t1a.")
      .append(startTimeField)
      .append(" END)) AS DOUBLE PRECISION) / CAST((t1a.").append(endTimeField).append("-t1a.").append(startTimeField)
      .append(") AS DOUBLE PRECISION)) AS activitycount FROM (SELECT DISTINCT ");
    addBucketExtract(sb,"",entityIdentifierField,idBucket);
    sb.append(" AS bucket,").append(endTimeField).append(" FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,filterCriteria,false);
    sb.append(") t0a,")
      .append(getTableName()).append(" t1a WHERE ");
    sb.append("t0a.bucket=");
    addBucketExtract(sb,"t1a.",entityIdentifierField,idBucket);
    sb.append(" AND t1a.").append(startTimeField).append("<t0a.").append(endTimeField)
      .append(" AND t1a.").append(endTimeField).append(">t0a.").append(endTimeField).append("-").append(intervalString);
    addCriteria(sb,"t1a.",connectionName,filterCriteria,true);
    sb.append(" GROUP BY bucket,windowstart,windowend) t2 ORDER BY bucket ASC, activitycount DESC) t3) t4");
    addOrdering(sb,new String[]{"activitycount","starttime","endtime","idbucket"},sort);
    addLimits(sb,startRow,maxRowCount);
    return performQuery(sb.toString(),null,null,null,maxRowCount);
  }


  /** Get a bucketed history, with sliding window, of maximum byte count.
  * The resultset returned should have the following columns: "starttime","endtime","bytecount","idbucket".
  */
  public IResultSet maxByteCountReport(String connectionName, FilterCriteria filterCriteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws LCFException
  {
    // The query we will generate here looks like this:
    // SELECT *
    //   FROM
    //     (SELECT DISTINCT ON (idbucket) t3.bucket AS idbucket, t3.bytecount AS bytecount,
    //                               t3.windowstart AS starttime, t3.windowend AS endtime
    //        FROM (SELECT * FROM (SELECT t0.bucket AS bucket, t0.starttime AS windowstart, t0.starttime + <interval> AS windowend,
    //                   SUM(t1.datasize * ((case when t0.starttime + <interval> < t1.endtime then t0.starttime + <interval> else t1.endtime end) -
    //                     (case when t0.starttime>t1.starttime then t0.starttime else t1.starttime end))
    //                      / (t1.endtime - t1.starttime)) AS bytecount
    //                   FROM (SELECT DISTINCT substring(entityid from '<bucketregexp>') AS bucket, starttime FROM repohistory WHERE <criteria>) t0, repohistory t1
    //                   WHERE t0.bucket=substring(t1.entityid from '<bucket_regexp>')
    //                      AND t1.starttime < t0.starttime + <interval> AND t1.endtime > t0.starttime
    //                      AND <criteria on t1>
    //                          GROUP BY bucket,windowstart,windowend
    //              UNION SELECT t0a.bucket AS bucket, t0a.endtime - <interval> AS windowstart, t0a.endtime AS windowend,
    //                   SUM(t1a.datasize * ((case when t0a.endtime < t1a.endtime then t0a.endtime else t1a.endtime end) -
    //                     (case when t0a.endtime - <interval> > t1a.starttime then t0a.endtime - <interval> else t1a.starttime end))
    //                      / (t1a.endtime - t1a.starttime)) AS bytecount
    //                   FROM (SELECT DISTINCT substring(entityid from '<bucketregexp>') AS bucket, endtime FROM repohistory WHERE <criteria>) t0a, repohistory t1a
    //                   WHERE t0a.bucket=substring(t1a.entityid from '<bucket_regexp>')
    //                      AND (t1a.starttime < t0a.endtime AND t1a.endtime > t0a.endtime - <interval>
    //                      AND <criteria on t1a>
    //                          GROUP BY bucket,windowstart,windowend) t2
    //                              ORDER BY bucket ASC,bytecount DESC) t3) t4 ORDER BY xxx LIMIT yyy OFFSET zzz;
    //
    // There are two different intervals being considered; each one may independently contribute possible
    // items to the list.  One is based on the start time of the current record; the other is based on the
    // end time of the current record.  That's why there are two inner clauses with a UNION.

    StringBuffer sb = new StringBuffer("SELECT * FROM (SELECT DISTINCT ON (idbucket) t3.bucket AS idbucket, t3.bytecount AS bytecount, t3.windowstart AS starttime, t3.windowend AS endtime FROM (SELECT * FROM (SELECT ");

    // Turn the interval into a string, since we'll need it a lot.
    String intervalString = new Long(interval).toString();

    sb.append("t0.bucket AS bucket, t0.").append(startTimeField).append(" AS windowstart, t0.")
      .append(startTimeField).append("+").append(intervalString).append(" AS windowend, SUM(t1.").append(dataSizeField)
      .append(" * ((CASE WHEN t0.")
      .append(startTimeField).append("+").append(intervalString).append("<t1.").append(endTimeField)
      .append(" THEN t0.").append(startTimeField).append("+").append(intervalString).append(" ELSE t1.")
      .append(endTimeField).append(" END) - (CASE WHEN t0.").append(startTimeField).append(">t1.").append(startTimeField)
      .append(" THEN t0.").append(startTimeField).append(" ELSE t1.").append(startTimeField)
      .append(" END)) / (t1.").append(endTimeField).append("-t1.").append(startTimeField)
      .append(")) AS bytecount FROM (SELECT DISTINCT ");
    addBucketExtract(sb,"",entityIdentifierField,idBucket);
    sb.append(" AS bucket,").append(startTimeField).append(" FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,filterCriteria,false);
    sb.append(") t0,")
      .append(getTableName()).append(" t1 WHERE ");
    sb.append("t0.bucket=");
    addBucketExtract(sb,"t1.",entityIdentifierField,idBucket);
    sb.append(" AND t1.").append(startTimeField).append("<t0.").append(startTimeField).append("+").append(intervalString)
      .append(" AND t1.").append(endTimeField).append(">t0.").append(startTimeField);
    addCriteria(sb,"t1.",connectionName,filterCriteria,true);
    sb.append(" GROUP BY bucket,windowstart,windowend UNION SELECT ")
      .append("t0a.bucket AS bucket, t0a.").append(endTimeField).append("-").append(intervalString).append(" AS windowstart, t0a.")
      .append(endTimeField).append(" AS windowend, SUM(t1a.").append(dataSizeField).append(" * ((CASE WHEN t0a.")
      .append(endTimeField).append("<t1a.").append(endTimeField)
      .append(" THEN t0a.").append(endTimeField).append(" ELSE t1a.")
      .append(endTimeField).append(" END) - (CASE WHEN t0a.").append(endTimeField).append("-").append(intervalString)
      .append(">t1a.").append(startTimeField)
      .append(" THEN t0a.").append(endTimeField).append("-").append(intervalString).append(" ELSE t1a.")
      .append(startTimeField)
      .append(" END)) / (t1a.").append(endTimeField).append("-t1a.").append(startTimeField)
      .append(")) AS bytecount FROM (SELECT DISTINCT ");
    addBucketExtract(sb,"",entityIdentifierField,idBucket);
    sb.append(" AS bucket,").append(endTimeField).append(" FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,filterCriteria,false);
    sb.append(") t0a,")
      .append(getTableName()).append(" t1a WHERE ");
    sb.append("t0a.bucket=");
    addBucketExtract(sb,"t1a.",entityIdentifierField,idBucket);
    sb.append(" AND t1a.").append(startTimeField).append("<t0a.").append(endTimeField)
      .append(" AND t1a.").append(endTimeField).append(">t0a.").append(endTimeField).append("-").append(intervalString);
    addCriteria(sb,"t1a.",connectionName,filterCriteria,true);
    sb.append(" GROUP BY bucket,windowstart,windowend) t2 ORDER BY bucket ASC, bytecount DESC) t3) t4");

    addOrdering(sb,new String[]{"bytecount","starttime","endtime","idbucket"},sort);
    addLimits(sb,startRow,maxRowCount);
    return performQuery(sb.toString(),null,null,null,maxRowCount);
  }

  /** Get a bucketed history of different result code/identifier combinations.
  * The resultset returned should have the following columns: "eventcount","resultcodebucket","idbucket".
  */
  public IResultSet resultCodesReport(String connectionName, FilterCriteria filterCriteria, SortOrder sort,
    BucketDescription resultCodeBucket, BucketDescription idBucket, int startRow, int maxRowCount)
    throws LCFException
  {
    // The query we'll use here will be:
    //
    // SELECT substring(resultcode FROM '<result_regexp>') AS resultcodebucket,
    //        substring(entityidentifier FROM '<id_regexp>') AS idbucket,
    //        COUNT('x') AS eventcount FROM repohistory WHERE <criteria>
    //              GROUP BY resultcodebucket,idbucket
    //                      ORDER BY xxx LIMIT yyy OFFSET zzz

    StringBuffer sb = new StringBuffer("SELECT ");
    addBucketExtract(sb,"",resultCodeField,resultCodeBucket);
    sb.append(" AS resultcodebucket, ");
    addBucketExtract(sb,"",entityIdentifierField,idBucket);
    sb.append(" AS idbucket, COUNT('x') AS eventcount FROM ").append(getTableName());
    addCriteria(sb,"",connectionName,filterCriteria,false);
    sb.append(" GROUP BY resultcodebucket,idbucket");
    addOrdering(sb,new String[]{"eventcount","resultcodebucket","idbucket"},sort);
    addLimits(sb,startRow,maxRowCount);
    return performQuery(sb.toString(),null,null,null,maxRowCount);
  }

  /** Turn a bucket description into a return column.
  * This is complicated by the fact that the extraction code is inherently case sensitive.  So if case insensitive is
  * desired, that means we whack the whole thing to lower case before doing the match.
  */
  protected void addBucketExtract(StringBuffer sb, String columnPrefix, String columnName, BucketDescription bucketDesc)
  {
    boolean isSensitive = bucketDesc.isSensitive();
    sb.append("SUBSTRING(");
    if (!isSensitive)
      sb.append("LOWER(").append(columnPrefix).append(columnName).append(")");
    else
      sb.append(columnPrefix).append(columnName);
    sb.append(" FROM ");
    if (!isSensitive)
      sb.append("LOWER(").append(quoteSQLString(bucketDesc.getRegexp())).append(")");
    else
      sb.append(quoteSQLString(bucketDesc.getRegexp()));
    sb.append(")");
  }

  /** Add criteria clauses to query.
  */
  protected boolean addCriteria(StringBuffer sb, String fieldPrefix, String connectionName, FilterCriteria criteria, boolean whereEmitted)
  {
    whereEmitted = emitClauseStart(sb,whereEmitted);
    sb.append(fieldPrefix).append(ownerNameField).append("=").append(quoteSQLString(connectionName));

    String[] activities = criteria.getActivities();
    if (activities != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      if (activities.length == 0)
      {
        sb.append("0>1");
      }
      else
      {
        sb.append(fieldPrefix).append(activityTypeField).append(" IN(");
        int i = 0;
        while (i < activities.length)
        {
          if (i > 0)
            sb.append(",");
          String activity = activities[i++];
          sb.append(quoteSQLString(activity));
        }
        sb.append(")");
      }
    }

    Long startTime = criteria.getStartTime();
    if (startTime != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      sb.append(fieldPrefix).append(startTimeField).append(">").append(startTime.toString());
    }

    Long endTime = criteria.getEndTime();
    if (endTime != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      sb.append(fieldPrefix).append(endTimeField).append("<=").append(endTime.toString());
    }

    RegExpCriteria entityMatch = criteria.getEntityMatch();
    if (entityMatch != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      sb.append(constructRegexpClause(fieldPrefix+entityIdentifierField,quoteSQLString(entityMatch.getRegexpString()),entityMatch.isInsensitive()));
    }

    RegExpCriteria resultCodeMatch = criteria.getResultCodeMatch();
    if (resultCodeMatch != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      sb.append(constructRegexpClause(fieldPrefix+resultCodeField,quoteSQLString(resultCodeMatch.getRegexpString()),resultCodeMatch.isInsensitive()));
    }

    return whereEmitted;
  }

  /** Emit a WHERE or an AND, depending...
  */
  protected boolean emitClauseStart(StringBuffer sb, boolean whereEmitted)
  {
    if (whereEmitted)
      sb.append(" AND ");
    else
      sb.append(" WHERE ");
    return true;
  }

  /** Add ordering.
  */
  protected void addOrdering(StringBuffer sb, String[] completeFieldList, SortOrder sort)
  {
    // Keep track of the fields we've seen
    Map hash = new HashMap();

    // Emit the "Order by"
    sb.append(" ORDER BY ");

    // Go through the specified list
    int i = 0;
    int count = sort.getCount();
    while (i < count)
    {
      if (i > 0)
        sb.append(",");
      String column = sort.getColumn(i);
      sb.append(column);
      if (sort.getDirection(i) == sort.SORT_ASCENDING)
        sb.append(" ASC");
      else
        sb.append(" DESC");
      hash.put(column,column);
      i++;
    }

    // Now, go through the complete field list, and emit sort criteria for everything
    // not actually specified.  This is so LIMIT and OFFSET give consistent results.

    int j = 0;
    while (j < completeFieldList.length)
    {
      String field = completeFieldList[j];
      if (hash.get(field) == null)
      {
        if (i > 0)
          sb.append(",");
        sb.append(field);
	// Always make it DESC order...
	sb.append(" DESC");
        //if (j == 0)
	//  sb.append(" DESC");
        //else
        //  sb.append(" ASC");
        i++;
      }
      j++;
    }
  }

  /** Add limit and offset.
  */
  protected void addLimits(StringBuffer sb, int startRow, int maxRowCount)
  {
    sb.append(" ").append(constructOffsetLimitClause(startRow,maxRowCount));
  }


  /** Conditionally do analyze operation.
  */
  protected void conditionallyAnalyzeInsert()
    throws LCFException
  {
    synchronized (tracker)
    {
      if (tracker.checkAnalyzeInsert())
      {
        // Do the analyze
        analyzeTable();
        // Simply reanalyze every 8000 inserts
        tracker.doAnalyze(8000);
      }
    }
  }

  /** Analyze tracker class.
  */
  protected static class AnalyzeTracker
  {
    // Number of records to insert before we need to analyze again
    protected long recordCount = 0;

    /** Constructor.
    */
    public AnalyzeTracker()
    {

    }

    /** Note an analyze.
    */
    public void doAnalyze(long repeatCount)
    {
      recordCount = repeatCount;
    }

    /** Prepare to insert/delete a record, and see if analyze is required.
    */
    public boolean checkAnalyzeInsert()
    {
      if (recordCount > 0L)
        recordCount--;
      return recordCount == 0L;
    }


  }

}
