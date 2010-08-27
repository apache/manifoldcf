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
package org.apache.acf.crawler.jobs;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import org.apache.acf.crawler.system.ACF;
import java.util.*;

/** This is the job queue manager class.  It is responsible for managing the jobqueue database table.
*/
public class JobQueue extends org.apache.acf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Seeding status values
  public final static int SEEDSTATUS_NOTSEED = 0;
  public final static int SEEDSTATUS_SEED = 1;
  public final static int SEEDSTATUS_NEWSEED = 2;

  // Status values
  public final static int STATUS_PENDING = 0;
  public final static int STATUS_ACTIVE = 1;
  public final static int STATUS_COMPLETE = 2;
  public final static int STATUS_PENDINGPURGATORY = 3;
  public final static int STATUS_ACTIVEPURGATORY = 4;
  public final static int STATUS_PURGATORY = 5;
  public final static int STATUS_BEINGDELETED = 6;
  public final static int STATUS_ACTIVENEEDRESCAN = 7;
  public final static int STATUS_ACTIVENEEDRESCANPURGATORY = 8;

  // Action values
  public final static int ACTION_RESCAN = 0;
  public final static int ACTION_REMOVE = 1;

  // State descriptions are as follows:
  // PENDING means a newly-added reference that has not been scanned before.
  // ACTIVE means a newly-added reference that is being scanned for the first time.
  // COMPLETE means a reference that has been already scanned (and does not need to be
  //   scanned again for this job session)
  // PURGATORY means a reference that was complete before, which means it will need to be deleted if
  //   it isn't included in this job session)
  // PENDINGPURGATORY means a reference that was complete before, but which has been rediscovered in
  //   this job session, but hasn't been scanned yet
  // ACTIVEPURGATORY means a reference that was PENDINGPURGATORY before, and has been picked up by a
  //   thread for processing
  //
  // PENDINGPURGATORY and ACTIVEPURGATORY exist in order to allow the system to properly recover from
  // an aborted job.  On recovery, PENDING and ACTIVE records are deleted (since they were never
  // completed), while PENDINGPURGATORY and ACTIVEPURGATORY records are retained but get marked as PURGATORY.
  //
  // BEINGDELETED means that the document is queued to be cleaned up because the owning job is being
  //   deleted.  It exists so that jobs that are active can avoid processing a document until the cleanup
  //   activity is done.

  // Field names
  public static final String idField = "id";
  public static final String jobIDField = "jobid";
  public static final String docHashField = "dochash";
  public static final String docIDField = "docid";
  public static final String checkTimeField = "checktime";
  public static final String statusField = "status";
  public static final String failTimeField = "failtime";
  public static final String failCountField = "failcount";
  public static final String isSeedField = "isseed";
  public static final String docPriorityField = "docpriority";
  public static final String prioritySetField = "priorityset";
  public static final String checkActionField = "checkaction";

  protected static Map statusMap;

  static
  {
    statusMap = new HashMap();
    statusMap.put("P",new Integer(STATUS_PENDING));
    statusMap.put("A",new Integer(STATUS_ACTIVE));
    statusMap.put("C",new Integer(STATUS_COMPLETE));
    statusMap.put("G",new Integer(STATUS_PENDINGPURGATORY));
    statusMap.put("F",new Integer(STATUS_ACTIVEPURGATORY));
    statusMap.put("Z",new Integer(STATUS_PURGATORY));
    statusMap.put("D",new Integer(STATUS_BEINGDELETED));
    statusMap.put("a",new Integer(STATUS_ACTIVENEEDRESCAN));
    statusMap.put("f",new Integer(STATUS_ACTIVENEEDRESCANPURGATORY));
  }

  protected static Map seedstatusMap;

  static
  {
    seedstatusMap = new HashMap();
    seedstatusMap.put("F",new Integer(SEEDSTATUS_NOTSEED));
    seedstatusMap.put("S",new Integer(SEEDSTATUS_SEED));
    seedstatusMap.put("N",new Integer(SEEDSTATUS_NEWSEED));
  }

  protected static Map actionMap;

  static
  {
    actionMap = new HashMap();
    actionMap.put("R",new Integer(ACTION_RESCAN));
    actionMap.put("D",new Integer(ACTION_REMOVE));
  }

  /** Counter for kicking off analyze */
  protected static AnalyzeTracker tracker = new AnalyzeTracker();
  /** Counter for kicking off reindex */
  protected static AnalyzeTracker reindexTracker = new AnalyzeTracker();

  /** The number of operations before we do a reindex.
  * Note well: 50,000 worked ok, but reindex was a bit frequent.  100,000 was infrequent enough
  * with 7.4 to cause occasional stuffing queries to take more than a minute.  Trying 250,000 now (on 8.2) */
  protected final static long REINDEX_COUNT = 250000L;

  /** Prerequisite event manager */
  protected PrereqEventManager prereqEventManager;

  /** Thread context */
  protected IThreadContext threadContext;
  
  /** Constructor.
  *@param database is the database handle.
  */
  public JobQueue(IThreadContext tc, IDBInterface database)
    throws ACFException
  {
    super(database,"jobqueue");
    this.threadContext = tc;
    prereqEventManager = new PrereqEventManager(database);
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ACFException
  {
    // Standard practice to use outer loop to allow retry in case of upgrade.
    while (true)
    {
      // Handle schema
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(idField,new ColumnDescription("BIGINT",true,false,null,null,false));
        map.put(jobIDField,new ColumnDescription("BIGINT",false,false,jobsTable,jobsColumn,false));
        // this is the local document identifier.
        map.put(docHashField,new ColumnDescription("VARCHAR(40)",false,false,null,null,false));
        map.put(docIDField,new ColumnDescription("LONGTEXT",false,false,null,null,false));
        map.put(checkTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(failTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(failCountField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(statusField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
        map.put(isSeedField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(docPriorityField,new ColumnDescription("FLOAT",false,true,null,null,false));
        map.put(prioritySetField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(checkActionField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, if needed
      }

      // Secondary table installation
      prereqEventManager.install(getTableName(),idField);

      // Handle indexes
      IndexDescription uniqueIndex = new IndexDescription(true,new String[]{docHashField,jobIDField});
      IndexDescription jobStatusIndex = new IndexDescription(false,new String[]{jobIDField,statusField});
      IndexDescription jobSeedIndex = new IndexDescription(false,new String[]{jobIDField,isSeedField});
      IndexDescription jobHashStatusIndex = new IndexDescription(false,new String[]{jobIDField,docHashField,statusField});
      IndexDescription statusIndex = new IndexDescription(false,new String[]{statusField});
      IndexDescription actionTimeStatusIndex = new IndexDescription(false,new String[]{checkActionField,checkTimeField,statusField});
      IndexDescription prioritysetStatusIndex = new IndexDescription(false,new String[]{prioritySetField,statusField});
      IndexDescription docpriorityIndex = new IndexDescription(false,new String[]{docPriorityField});

      // Get rid of unused indexes
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (uniqueIndex != null && id.equals(uniqueIndex))
          uniqueIndex = null;
        else if (jobStatusIndex != null && id.equals(jobStatusIndex))
          jobStatusIndex = null;
        else if (jobSeedIndex != null && id.equals(jobSeedIndex))
          jobSeedIndex = null;
        else if (jobHashStatusIndex != null && id.equals(jobHashStatusIndex))
          jobHashStatusIndex = null;
        else if (statusIndex != null && id.equals(statusIndex))
          statusIndex = null;
        else if (actionTimeStatusIndex != null && id.equals(actionTimeStatusIndex))
          actionTimeStatusIndex = null;
        else if (prioritysetStatusIndex != null && id.equals(prioritysetStatusIndex))
          prioritysetStatusIndex = null;
        else if (docpriorityIndex != null && id.equals(docpriorityIndex))
          docpriorityIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Build missing indexes

      if (jobStatusIndex != null)
        performAddIndex(null,jobStatusIndex);

      if (jobSeedIndex != null)
        performAddIndex(null,jobSeedIndex);

      if (jobHashStatusIndex != null)
        performAddIndex(null,jobHashStatusIndex);

      if (statusIndex != null)
        performAddIndex(null,statusIndex);

      if (actionTimeStatusIndex != null)
        performAddIndex(null,actionTimeStatusIndex);

      if (prioritysetStatusIndex != null)
        performAddIndex(null,prioritysetStatusIndex);

      if (docpriorityIndex != null)
        performAddIndex(null,docpriorityIndex);

      if (uniqueIndex != null)
        performAddIndex(null,uniqueIndex);


      break;
    }
  }

  /** Analyze job tables due to major event */
  public void unconditionallyAnalyzeTables()
    throws ACFException
  {
    try
    {
      long startTime = System.currentTimeMillis();
      Logging.perf.debug("Beginning to analyze jobqueue table");
      analyzeTable();
      Logging.perf.debug("Done analyzing jobqueue table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
    }
    finally
    {
      tracker.doAnalyze(15000L);
    }
  }

  /** Analyze job tables that need analysis.
  */
  public void conditionallyAnalyzeTables()
    throws ACFException
  {
    if (tracker.checkAnalyze())
    {
      unconditionallyAnalyzeTables();
    }
    if (reindexTracker.checkAnalyze())
    {
      try
      {
        long startTime = System.currentTimeMillis();
        Logging.perf.debug("Beginning to reindex jobqueue table");
        reindexTable();
        Logging.perf.debug("Done reindexing jobqueue table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
      }
      finally
      {
        // Simply reindex every n inserts
        reindexTracker.doAnalyze(REINDEX_COUNT);
      }
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ACFException
  {
    beginTransaction();
    try
    {
      prereqEventManager.deinstall();
      performDrop(null);
    }
    catch (ACFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Restart.
  * This method should be called at initial startup time.  It resets the status of all documents to something
  * reasonable, so the jobs can be restarted and work properly to completion.
  */
  public void restart()
    throws ACFException
  {
    // Map ACTIVE back to PENDING.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDING));
    ArrayList list = new ArrayList();
    list.add(statusToString(STATUS_ACTIVE));
    list.add(statusToString(STATUS_ACTIVENEEDRESCAN));
    performUpdate(map,"WHERE "+statusField+"=? OR "+statusField+"=?",list,null);

    // Map ACTIVEPURGATORY to PENDINGPURGATORY
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    list.clear();
    list.add(statusToString(STATUS_ACTIVEPURGATORY));
    list.add(statusToString(STATUS_ACTIVENEEDRESCANPURGATORY));
    performUpdate(map,"WHERE "+statusField+"=? OR "+statusField+"=?",list,null);

    // Map BEINGDELETED to COMPLETE
    map.put(statusField,statusToString(STATUS_COMPLETE));
    list.clear();
    list.add(statusToString(STATUS_BEINGDELETED));
    performUpdate(map,"WHERE "+statusField+"=?",list,null);

    // Map newseed fields to seed
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_SEED));
    list.clear();
    list.add(seedstatusToString(SEEDSTATUS_NEWSEED));
    performUpdate(map,"WHERE "+isSeedField+"=?",list,null);

    // Clear out all failtime fields (since we obviously haven't been retrying whilst we were not
    // running)
    map = new HashMap();
    map.put(failTimeField,null);
    performUpdate(map,"WHERE "+failTimeField+" IS NOT NULL",null,null);
    // Reindex the jobqueue table, since we've probably made lots of bad tuples doing the above operations.
    reindexTable();
    reindexTracker.doAnalyze(REINDEX_COUNT);
    unconditionallyAnalyzeTables();
  }

  /** Clear the failtimes for all documents associated with a job.
  * This method is called when the system detects that a significant delaying event has occurred,
  * and therefore the "failure clock" needs to be reset.
  *@param jobID is the job identifier.
  */
  public void clearFailTimes(Long jobID)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    Map map = new HashMap();
    map.put(failTimeField,null);
    performUpdate(map,"WHERE "+jobIDField+"=? AND "+failTimeField+" IS NOT NULL",list,null);
  }

  /** Reset as part of restoring document worker threads.
  * This will get called if something went wrong that could have screwed up the
  * status of a worker thread.  The threads all die/end, and this method
  * resets any active documents back to the right state (waiting for stuffing).
  */
  public void resetDocumentWorkerStatus()
    throws ACFException
  {
    // Map ACTIVE back to PENDING.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDING));
    ArrayList list = new ArrayList();
    list.add(statusToString(STATUS_ACTIVE));
    list.add(statusToString(STATUS_ACTIVENEEDRESCAN));
    performUpdate(map,"WHERE "+statusField+"=? OR "+statusField+"=?",list,null);

    // Map ACTIVEPURGATORY to PENDINGPURGATORY
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    list.clear();
    list.add(statusToString(STATUS_ACTIVEPURGATORY));
    list.add(statusToString(STATUS_ACTIVENEEDRESCANPURGATORY));
    performUpdate(map,"WHERE "+statusField+"=? OR "+statusField+"=?",list,null);
  }

  /** Reset doc delete worker status.
  */
  public void resetDocDeleteWorkerStatus()
    throws ACFException
  {
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    // Map BEINGDELETED to COMPLETE
    map.put(statusField,statusToString(STATUS_COMPLETE));
    list.clear();
    list.add(statusToString(STATUS_BEINGDELETED));
    performUpdate(map,"WHERE "+statusField+"=?",list,null);
  }

  /** Prepare for a "full scan" job.  This will not be called
  * unless the job is in the "INACTIVE" state.
  * This does the following:
  * (1) get rid of all PENDING entries.
  * (2) map PENDINGPURGATORY entries to PURGATORY.
  * (4) map COMPLETED entries to PURGATORY.
  *@param jobID is the job identifier.
  */
  public void prepareFullScan(Long jobID)
    throws ACFException
  {
    // Delete PENDING and ACTIVE entries
    ArrayList list = new ArrayList();
    list.add(jobID);
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(getTableName()+" t0","t0."+idField,"t0."+jobIDField+"=? AND t0."+statusField+"="+
      quoteSQLString(statusToString(STATUS_PENDING)),list);
    performDelete("WHERE "+jobIDField+"=? AND "+statusField+"="+
      quoteSQLString(statusToString(STATUS_PENDING)),list,null);

    // Turn PENDINGPURGATORY, COMPLETED into PURGATORY.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(checkTimeField,null);
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    // Do not reset priorities.  This means, of course, that they may be out of date - but they are probably more accurate in their current form
    // than being set back to some arbitrary value.
    // The alternative, which would be to reprioritize all the documents at this point, is somewhat attractive, but let's see if we can get away
    // without for now.

    performUpdate(map,"WHERE "+jobIDField+"=? AND "+statusField+" IN ("+
      quoteSQLString(statusToString(STATUS_PENDINGPURGATORY))+","+
      quoteSQLString(statusToString(STATUS_COMPLETE))+")",list,null);

    // Not accurate, but best we can do without overhead
    reindexTracker.noteInsert(2);
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();
  }

  /** Prepare for an "incremental" job.  This is called ONLY when the job is inactive;
  * that is, there should be no ACTIVE or ACTIVEPURGATORY entries at all.
  *
  * The preparation for starting an incremental job is to requeue all documents that are
  * currently in the system that are marked "COMPLETE".  These get marked as "PENDINGPURGATORY",
  * since the idea is to queue them in such a way that we know they were ingested before.
  *@param jobID is the job identifier.
  */
  public void prepareIncrementalScan(Long jobID)
    throws ACFException
  {
    // Delete PENDING and ACTIVE entries
    ArrayList list = new ArrayList();
    list.add(jobID);
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    // Do not reset priorities.  This means, of course, that they may be out of date - but they are probably more accurate in their current form
    // than being set back to some arbitrary value.
    // The alternative, which would be to reprioritize all the documents at this point, is somewhat attractive, but let's see if we can get away
    // without for now.
    //map.put(docPriorityField,new Double(1.0));
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+jobIDField+"=? AND "+statusField+"="+
      quoteSQLString(statusToString(STATUS_COMPLETE)),list,null);
    reindexTracker.noteInsert();
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();
  }

  /** Delete ingested document identifiers (as part of deleting the owning job).
  * The number of identifiers specified is guaranteed to be less than the maxInClauseCount
  * for the database.
  *@param identifiers is the set of document identifiers.
  */
  public void deleteIngestedDocumentIdentifiers(DocumentDescription[] identifiers)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < identifiers.length)
    {
      if (i > 0)
        sb.append(',');
      sb.append('?');
      list.add(identifiers[i].getID());
      i++;
    }
    doDeletes(list,sb.toString());
    reindexTracker.noteInsert(identifiers.length);
  }

  /** Check if there are any outstanding active documents for a job */
  public boolean checkJobBusy(Long jobID)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    IResultSet set = performQuery("SELECT "+docHashField+" FROM "+getTableName()+
      " WHERE "+jobIDField+"=? AND "+statusField+" IN ("+
      quoteSQLString(statusToString(STATUS_ACTIVE))+","+
      quoteSQLString(statusToString(STATUS_ACTIVEPURGATORY))+","+
      quoteSQLString(statusToString(STATUS_ACTIVENEEDRESCAN))+","+
      quoteSQLString(statusToString(STATUS_ACTIVENEEDRESCANPURGATORY))+") "+constructOffsetLimitClause(0,1),list,null,null,1);
    return set.getRowCount() > 0;
  }

  /** For a job deletion: Delete all records for a job.
  *@param jobID is the job identifier.
  */
  public void deleteAllJobRecords(Long jobID)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(getTableName()+" t0","t0."+idField,"t0."+jobIDField+"=?",list);
    performDelete("WHERE "+jobIDField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Write out a document priority */
  public void writeDocPriority(long currentTime, Long rowID, double priority)
    throws ACFException
  {
    HashMap map = new HashMap();
    map.put(prioritySetField,new Long(currentTime));
    map.put(docPriorityField,new Double(priority));
    ArrayList list = new ArrayList();
    list.add(rowID);
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Set the "completed" status for a record.
  */
  public void updateCompletedRecord(Long recID, int currentStatus)
    throws ACFException
  {
    int newStatus;
    String actionFieldValue;
    Long checkTimeValue;
    switch (currentStatus)
    {
    case STATUS_ACTIVE:
    case STATUS_ACTIVEPURGATORY:
      newStatus = STATUS_COMPLETE;
      actionFieldValue = null;
      checkTimeValue = null;
      break;
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      newStatus = STATUS_PENDINGPURGATORY;
      actionFieldValue = actionToString(ACTION_RESCAN);
      checkTimeValue = new Long(0L);
      break;
    default:
      throw new ACFException("Unexpected jobqueue status - record id "+recID.toString()+", expecting active status");
    }

    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    map.put(checkTimeField,checkTimeValue);
    map.put(checkActionField,actionFieldValue);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    // Don't rejigger document priority, because it is hard to calculate and because what's there is probably better
    // than any arbitrary value I'd use.
    //map.put(docPriorityField,new Double(1.0));
    //map.put(prioritySetField,new Long(0L));
    ArrayList list = new ArrayList();
    list.add(recID);
    performUpdate(map,"WHERE "+idField+"=?",list,null);
  }

  /** Set the status to active on a record, leaving alone priority or check time.
  *@param id is the job queue id.
  *@param currentStatus is the current status
  */
  public void updateActiveRecord(Long id, int currentStatus)
    throws ACFException
  {
    int newStatus;
    switch (currentStatus)
    {
    case STATUS_PENDING:
      newStatus = STATUS_ACTIVE;
      break;
    case STATUS_PENDINGPURGATORY:
      newStatus = STATUS_ACTIVEPURGATORY;
      break;
    default:
      throw new ACFException("Unexpected status value for jobqueue record "+id.toString()+"; got "+Integer.toString(currentStatus));
    }

    ArrayList list = new ArrayList();
    list.add(id);
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Set the status on a record, including check time and priority.
  *@param id is the job queue id.
  *@param status is the desired status
  *@param checkTime is the check time.
  */
  public void setStatus(Long id, int status,
    Long checkTime, int action, long failTime, int failCount)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(id);
    HashMap map = new HashMap();
    map.put(statusField,statusToString(status));
    map.put(checkTimeField,checkTime);
    map.put(checkActionField,actionToString(action));
    if (failTime == -1L)
      map.put(failTimeField,null);
    else
      map.put(failTimeField,new Long(failTime));
    if (failCount == -1)
      map.put(failCountField,null);
    else
      map.put(failCountField,new Long(failCount));
    // This does not need to set docPriorityField, because we want to preserve whatever
    // priority was in place from before.
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Set the status of a document to "being deleted".
  */
  public void setDeletingStatus(Long id)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(id);
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_BEINGDELETED));
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Set the status of a document to be "no longer deleting" */
  public void setUndeletingStatus(Long id)
    throws ACFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_COMPLETE));
    map.put(checkTimeField,null);
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    list.add(id);
    performUpdate(map,"WHERE "+idField+"=?",list,null);
  }

  /** Remove multiple records entirely.
  *@param ids is the set of job queue id's
  */
  public void deleteRecordMultiple(Long[] ids)
    throws ACFException
  {
    // Delete in chunks
    int maxInClause = getMaxInClause();
    ArrayList list = new ArrayList();
    StringBuffer sb = new StringBuffer();
    int j = 0;
    int i = 0;
    while (i < ids.length)
    {
      if (j == maxInClause)
      {
        doDeletes(list,sb.toString());
        list.clear();
        sb.setLength(0);
        j = 0;
      }
      if (j > 0)
        sb.append(',');
      sb.append('?');
      list.add(ids[i++]);
      j++;
    }
    if (j > 0)
      doDeletes(list,sb.toString());
    reindexTracker.noteInsert(ids.length);
  }

  /** Do a batch of deletes.
  */
  protected void doDeletes(ArrayList list, String queryPart)
    throws ACFException
  {
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(queryPart,list);
    performDelete("WHERE "+idField+" IN("+queryPart+")",list,null);
  }

  /** Remove a record entirely.
  *@param id is the job queue id.
  */
  public void deleteRecord(Long id)
    throws ACFException
  {
    deleteRecordMultiple(new Long[]{id});
  }

  /** Update an existing record (as the result of an initial add).
  * The record is presumed to exist and have been locked, via "FOR UPDATE".
  */
  public boolean updateExistingRecordInitial(Long recordID, int currentStatus, Long checkTimeValue,
    long desiredExecuteTime, long currentTime, double desiredPriority, String[] prereqEvents)
    throws ACFException
  {
    // The general rule here is:
    // If doesn't exist, make a PENDING entry.
    // If PENDING, keep it as PENDING.
    // If COMPLETE, make a PENDING entry.
    // If PURGATORY, make a PENDINGPURGATORY entry.
    // Leave everything else alone and do nothing.

    boolean rval = false;
    HashMap map = new HashMap();
    switch (currentStatus)
    {
    case STATUS_ACTIVE:
    case STATUS_ACTIVEPURGATORY:
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      // Initial adds never bring along any carrydown info, so we should be satisfied as long as the record exists.
      break;

    case STATUS_COMPLETE:
    case STATUS_PURGATORY:
    case STATUS_BEINGDELETED:
      // Set the status and time both
      map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
      if (desiredExecuteTime == -1L)
        map.put(checkTimeField,new Long(0L));
      else
        map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // Update the doc priority.
      map.put(docPriorityField,new Double(desiredPriority));
      map.put(prioritySetField,new Long(currentTime));
      rval = true;
      break;

    case STATUS_PENDING:
      // Bump up the schedule if called for
      Long cv = checkTimeValue;
      if (cv != null)
      {
        long currentExecuteTime = cv.longValue();
        if ((desiredExecuteTime == -1L ||currentExecuteTime <= desiredExecuteTime))
        {
          break;
        }
      }
      else
      {
        if (desiredExecuteTime == -1L)
        {
          break;
        }
      }
      map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // The existing doc priority field should be preserved.
      break;

    case STATUS_PENDINGPURGATORY:
      // In this case we presume that the reason we are in this state is due to adaptive crawling or retry, so DON'T bump up the schedule!
      // The existing doc priority field should also be preserved.
      break;

    default:
      break;

    }
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED));
    ArrayList list = new ArrayList();
    list.add(recordID);
    // Delete any existing prereqevent entries first
    prereqEventManager.deleteRows(recordID);
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    // Insert prereqevent entries, if any
    prereqEventManager.addRows(recordID,prereqEvents);
    reindexTracker.noteInsert();
    return rval;
  }

  /** Insert a new record into the jobqueue table (as part of adding an initial reference).
  *
  *@param jobID is the job identifier.
  *@param docHash is the hash of the local document identifier.
  *@param docID is the local document identifier.
  */
  public void insertNewRecordInitial(Long jobID, String docHash, String docID, double desiredDocPriority,
    long desiredExecuteTime, long currentTime, String[] prereqEvents)
    throws ACFException
  {
    // No prerequisites should be possible at this point.
    HashMap map = new HashMap();
    Long recordID = new Long(IDFactory.make(threadContext));
    map.put(idField,recordID);
    if (desiredExecuteTime == -1L)
      map.put(checkTimeField,new Long(0L));
    else
      map.put(checkTimeField,new Long(desiredExecuteTime));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(jobIDField,jobID);
    map.put(docHashField,docHash);
    map.put(docIDField,docID);
    map.put(statusField,statusToString(STATUS_PENDING));
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED));
    // Set the document priority
    map.put(docPriorityField,new Double(desiredDocPriority));
    map.put(prioritySetField,new Long(currentTime));
    performInsert(map,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    tracker.noteInsert();
  }

  /** Note the remaining documents that do NOT need to be queued.  These are noted so that the
  * doneDocumentsInitial() method does not clean up seeds from previous runs wrongly.
  */
  public void addRemainingDocumentsInitial(Long jobID, String[] docIDHashes)
    throws ACFException
  {
    if (docIDHashes.length == 0)
      return;

    // Basically, all we want to do is move the documents whose status is still SEED
    // to become NEWSEED.

    HashMap inSet = new HashMap();
    int j = 0;
    while (j < docIDHashes.length)
    {
      String docIDHash = docIDHashes[j++];
      inSet.put(docIDHash,docIDHash);
    }

    HashMap idMap = new HashMap();
    int k = 0;
    int maxInClause = getMaxInClause();

    // To avoid deadlock, use 1 instead of something larger.  The docIDs are presumed to come in in sorted order.
    int maxClause = 1;

    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    j = 0;
    while (j < docIDHashes.length)
    {
      String docIDHash = docIDHashes[j++];

      if (k == maxClause)
      {
        processRemainingDocuments(idMap,sb.toString(),list,inSet);
        sb.setLength(0);
        k = 0;
        list.clear();
      }

      if (k > 0)
        sb.append(" OR");
      sb.append("(").append(jobIDField).append("=? AND ").append(docHashField).append("=? AND ").append(isSeedField)
        .append("=?)");
      list.add(jobID);
      list.add(docIDHash);
      list.add(seedstatusToString(SEEDSTATUS_SEED));
      k++;
    }
    if (k > 0)
      processRemainingDocuments(idMap,sb.toString(),list,inSet);

    // We have a set of id's.  Process those in bulk.
    k = 0;
    sb.setLength(0);
    list.clear();
    Iterator idValues = idMap.keySet().iterator();
    while (idValues.hasNext())
    {
      if (k == maxInClause)
      {
        updateRemainingDocuments(sb.toString(),list);
        sb.setLength(0);
        k = 0;
        list.clear();
      }
      Long idValue = (Long)idValues.next();
      if (k > 0)
        sb.append(",");
      sb.append("?");
      list.add(idValue);
      k++;
    }
    if (k > 0)
      updateRemainingDocuments(sb.toString(),list);
    reindexTracker.noteInsert(docIDHashes.length);
  }

  /** Process the specified set of documents. */
  protected void processRemainingDocuments(Map idMap, String query, ArrayList list, Map inSet)
    throws ACFException
  {
    IResultSet set = performQuery("SELECT "+idField+","+docHashField+" FROM "+getTableName()+
      " WHERE "+query+" FOR UPDATE",list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docIDHash = (String)row.getValue(docHashField);
      if (inSet.get(docIDHash) != null)
      {
        Long idValue = (Long)row.getValue(idField);
        idMap.put(idValue,idValue);
      }
    }
  }

  /** Update the specified set of documents to be "NEWSEED" */
  protected void updateRemainingDocuments(String query, ArrayList list)
    throws ACFException
  {
    HashMap map = new HashMap();
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED));
    performUpdate(map,"WHERE "+idField+" IN("+query+")",list,null);
  }

  /** Complete the initial set of documents.  This method converts the seeding statuses for the
  * job to their steady-state values.
  * SEEDSTATUS_SEED becomes SEEDSTATUS_NOTSEED, and SEEDSTATUS_NEWSEED becomes
  * SEEDSTATUS_SEED.  If the seeding was partial, then all previous seeds are preserved as such.
  *@param jobID is the job identifier.
  *@param isPartial is true of the passed list of seeds is not complete.
  */
  public void doneDocumentsInitial(Long jobID, boolean isPartial)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    if (!isPartial)
    {
      list.add(jobID);
      list.add(seedstatusToString(SEEDSTATUS_SEED));
      map.put(isSeedField,seedstatusToString(SEEDSTATUS_NOTSEED));
      performUpdate(map,"WHERE "+jobIDField+"=? AND "+isSeedField+"=?",list,null);
      list.clear();
    }
    list.add(jobID);
    list.add(seedstatusToString(SEEDSTATUS_NEWSEED));
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_SEED));
    performUpdate(map,"WHERE "+jobIDField+"=? AND "+isSeedField+"=?",list,null);
  }

  /** Get all the current seeds.
  * Returns the seed document identifiers for a job.
  *@param jobID is the job identifier.
  *@return the document identifier hashes that are currently considered to be seeds.
  */
  public String[] getAllSeeds(Long jobID)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    list.add(seedstatusToString(SEEDSTATUS_SEED));
    IResultSet set = performQuery("SELECT "+docHashField+" FROM "+getTableName()+" WHERE "+jobIDField+"=? AND "+isSeedField+"=?",
      list,null,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i++] = (String)row.getValue(docHashField);
    }
    return rval;
  }

  /** Update an existing record (as the result of a reference add).
  * The record is presumed to exist and have been locked, via "FOR UPDATE".
  */
  public boolean updateExistingRecord(Long recordID, int currentStatus, Long checkTimeValue,
    long desiredExecuteTime, long currentTime, boolean otherChangesSeen, double desiredPriority,
    String[] prereqEvents)
    throws ACFException
  {
    boolean rval = false;
    HashMap map = new HashMap();
    switch (currentStatus)
    {
    case STATUS_PURGATORY:
      // Set the status and time both
      map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
      map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // Going into pending: set the docpriority.
      map.put(docPriorityField,new Double(desiredPriority));
      map.put(prioritySetField,new Long(currentTime));
      rval = true;
      break;
    case STATUS_COMPLETE:
      // Requeue the document for processing, if there have been other changes.
      if (otherChangesSeen)
      {
        // The document has been processed before, so it has to go into PENDINGPURGATORY.
        // Set the status and time both
        map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority));
        map.put(prioritySetField,new Long(currentTime));
        rval = true;
        break;
      }
      return rval;
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      // Document is in the queue, but already needs a rescan for prior reasons.
      // We're done.
      return rval;
    case STATUS_ACTIVE:
      // Document is in the queue.
      // The problem here is that we have no idea when the document is actually being worked on; we only find out when the document is actually *done*.
      // Any update to the carrydown information may therefore be too late for the current processing cycle.
      // Given that being the case, the "proper" thing to do is to requeue the document when the processing is completed, so that we can guarantee
      // reprocessing will take place.
      // Additional document states must therefore be added to represent the situation:
      // (ACTIVE or ACTIVEPURGATORY equivalent, but where document is requeued upon completion, rather than put into "COMPLETED".
      if (otherChangesSeen)
      {
        // Flip the state to the new one, and set the document priority at this time too - it will be preserved when the
        // processing is completed.
        map.put(statusField,statusToString(STATUS_ACTIVENEEDRESCAN));
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority));
        map.put(prioritySetField,new Long(currentTime));
        rval = true;
        break;
      }
      return rval;
    case STATUS_ACTIVEPURGATORY:
      // Document is in the queue.
      // The problem here is that we have no idea when the document is actually being worked on; we only find out when the document is actually *done*.
      // Any update to the carrydown information may therefore be too late for the current processing cycle.
      // Given that being the case, the "proper" thing to do is to requeue the document when the processing is completed, so that we can guarantee
      // reprocessing will take place.
      // Additional document states must therefore be added to represent the situation:
      // (ACTIVE or ACTIVEPURGATORY equivalent, but where document is requeued upon completion, rather than put into "COMPLETED".
      if (otherChangesSeen)
      {
        // Flip the state to the new one, and set the document priority at this time too - it will be preserved when the
        // processing is completed.
        map.put(statusField,statusToString(STATUS_ACTIVENEEDRESCANPURGATORY));
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority));
        map.put(prioritySetField,new Long(currentTime));
        rval = true;
        break;
      }
      return rval;
    case STATUS_PENDING:
      // Document is already waiting to be processed.
      // Bump up the schedule, if called for.  Otherwise, just leave it alone.
      Long cv = checkTimeValue;
      if (cv != null)
      {
        long currentExecuteTime = cv.longValue();
        if (currentExecuteTime <= desiredExecuteTime)
          return rval;
      }
      map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // Leave doc priority alone
      break;
    case STATUS_PENDINGPURGATORY:
      // This is just like PENDING except we know that the document was processed at least once before.
      // In this case we presume that the schedule was already set for adaptive or retry reasons, so DON'T change the schedule or activity
      // Also, leave doc priority alone
      // Fall through...
    default:
      return rval;
    }
    ArrayList list = new ArrayList();
    list.add(recordID);
    prereqEventManager.deleteRows(recordID);
    performUpdate(map,"WHERE "+idField+"=?",list,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    reindexTracker.noteInsert();
    return rval;
  }

  /** Insert a new record into the jobqueue table (as part of adding a child reference).
  *
  */
  public void insertNewRecord(Long jobID, String docIDHash, String docID, double desiredDocPriority, long desiredExecuteTime,
    long currentTime, String[] prereqEvents)
    throws ACFException
  {
    HashMap map = new HashMap();
    Long recordID = new Long(IDFactory.make(threadContext));
    map.put(idField,recordID);
    map.put(checkTimeField,new Long(desiredExecuteTime));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(jobIDField,jobID);
    map.put(docHashField,docIDHash);
    map.put(docIDField,docID);
    map.put(statusField,statusToString(STATUS_PENDING));
    // Be sure to set the priority also
    map.put(docPriorityField,new Double(desiredDocPriority));
    map.put(prioritySetField,new Long(currentTime));
    performInsert(map,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    tracker.noteInsert();
  }

  // Methods to convert status strings to integers and back

  /** Convert seedstatus value to a string.
  */
  public static String seedstatusToString(int status)
    throws ACFException
  {
    switch (status)
    {
    case SEEDSTATUS_NOTSEED:
      return "F";
    case SEEDSTATUS_SEED:
      return "S";
    case SEEDSTATUS_NEWSEED:
      return "N";
    default:
      throw new ACFException("Invalid seed status: "+Integer.toString(status));
    }
  }

  /** Convert seedstatus field value to a boolean.
  */
  public static int stringToSeedstatus(String x)
    throws ACFException
  {
    if (x == null || x.length() == 0)
      return SEEDSTATUS_NOTSEED;
    Integer y = (Integer)seedstatusMap.get(x);
    if (y == null)
      throw new ACFException("Unknown seed status code: "+x);
    return y.intValue();
  }

  /** Convert action field value to integer.
  */
  public static int stringToAction(String value)
    throws ACFException
  {
    Integer x = (Integer)actionMap.get(value);
    if (x == null)
      throw new ACFException("Unknown action string: '"+value+"'");
    return x.intValue();
  }

  /** Convert integer to action string */
  public static String actionToString(int action)
    throws ACFException
  {
    switch (action)
    {
    case ACTION_RESCAN:
      return "R";
    case ACTION_REMOVE:
      return "D";
    default:
      throw new ACFException("Bad action value: "+Integer.toString(action));
    }
  }

  /** Convert status field value to integer.
  *@param value is the string.
  *@return the integer.
  */
  public static int stringToStatus(String value)
    throws ACFException
  {
    Integer x = (Integer)statusMap.get(value);
    if (x == null)
      throw new ACFException("Unknown status string: '"+value+"'");
    return x.intValue();
  }

  /** Convert status to string.
  *@param status is the status value.
  *@return the database string.
  */
  public static String statusToString(int status)
    throws ACFException
  {
    switch (status)
    {
    case STATUS_PENDING:
      return "P";
    case STATUS_ACTIVE:
      return "A";
    case STATUS_COMPLETE:
      return "C";
    case STATUS_PENDINGPURGATORY:
      return "G";
    case STATUS_ACTIVEPURGATORY:
      return "F";
    case STATUS_PURGATORY:
      return "Z";
    case STATUS_BEINGDELETED:
      return "D";
    case STATUS_ACTIVENEEDRESCAN:
      return "a";
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      return "f";
    default:
      throw new ACFException("Bad status value: "+Integer.toString(status));
    }

  }

  /** Get a hash value from a document id string.  This will convert the string into something that can fit in 20 characters.
  * (Someday this will be an MD5 hash, but for now just use java hashing.)
  *@param documentIdentifier is the input document id string.
  *@return the hash code.
  */
  public static String getHashCode(String documentIdentifier)
    throws ACFException
  {
    return ACF.hash(documentIdentifier);
  }

  /** Analyze tracker class.
  */
  protected static class AnalyzeTracker
  {
    // Number of records to insert before we need to analyze again.
    // After start, we wait 1000 before analyzing the first time.
    protected long recordCount = 1000L;
    protected boolean busy = false;

    /** Constructor.
    */
    public AnalyzeTracker()
    {

    }

    /** Note an analyze.
    */
    public synchronized void doAnalyze(long repeatCount)
    {
      recordCount = repeatCount;
      busy = false;
    }

    public synchronized void noteInsert(int count)
    {
      if (recordCount >= (long)count)
        recordCount -= (long)count;
      else
        recordCount = 0L;
    }

    /** Note an insert */
    public synchronized void noteInsert()
    {
      if (recordCount > 0L)
        recordCount--;
    }

    /** Prepare to insert/delete a record, and see if analyze is required.
    */
    public synchronized boolean checkAnalyze()
    {
      if (busy)
        return false;
      busy = (recordCount == 0L);
      return busy;
    }


  }

  // This class filters an ordered resultset to return only the duplicates
  protected static class DuplicateFinder implements ILimitChecker
  {
    protected Long prevJobID = null;
    protected String prevDocIDHash = null;

    public DuplicateFinder()
    {
    }

    /** See if this class can be legitimately compared against another of
    * the same type.
    *@return true if comparisons will ever return "true".
    */
    public boolean doesCompareWork()
    {
      return false;
    }

    /** Create a duplicate of this class instance.  All current state should be preserved.
    * NOTE: Since doesCompareWork() returns false, queries using this limit checker cannot
    * be cached, and therefore duplicate() is never called from the query executor.
    *@return the duplicate.
    */
    public ILimitChecker duplicate()
    {
      DuplicateFinder df = new DuplicateFinder();
      df.prevJobID = prevJobID;
      df.prevDocIDHash = prevDocIDHash;
      return df;
    }

    /** Find the hashcode for this class.  This will only ever be used if
    * doesCompareWork() returns true.
    *@return the hashcode.
    */
    public int hashCode()
    {
      return 0;
    }

    /** Compare two objects and see if equal.  This will only ever be used
    * if doesCompareWork() returns true.
    *@param object is the object to compare against.
    *@return true if equal.
    */
    public boolean equals(Object object)
    {
      return false;
    }

    /** See if a result row should be included in the final result set.
    *@param row is the result row to check.
    *@return true if it should be included, false otherwise.
    */
    public boolean checkInclude(IResultRow row)
      throws ACFException
    {
      Long jobID = (Long)row.getValue(jobIDField);
      String docIDHash = (String)row.getValue(docHashField);
      // If this is a duplicate, we want to keep it!
      if (prevJobID != null && jobID.equals(prevJobID) && docIDHash.equals(prevDocIDHash))
        return true;
      prevJobID = jobID;
      prevDocIDHash = docIDHash;
      return false;
    }

    /** See if we should examine another row.
    *@return true if we need to keep going, or false if we are done.
    */
    public boolean checkContinue()
      throws ACFException
    {
      return true;
    }
  }

}
