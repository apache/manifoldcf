/* $Id: JobQueue.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;

/** This is the job queue manager class.  It is responsible for managing the jobqueue database table.
 * 
 * <br><br>
 * <b>jobqueue</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>id</td><td>BIGINT</td><td>Primary Key</td></tr>
 * <tr><td>jobid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
 * <tr><td>dochash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>docid</td><td>LONGTEXT</td><td></td></tr>
 * <tr><td>checktime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>failtime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>failcount</td><td>BIGINT</td><td></td></tr>
 * <tr><td>status</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>isseed</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>docpriority</td><td>FLOAT</td><td></td></tr>
 * <tr><td>checkaction</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>processid</td><td>VARCHAR(16)</td><td></td></tr>
 * <tr><td>seedingprocessid</td><td>VARCHAR(16)</td><td></td></tr>
 * <tr><td>needpriority</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>needpriorityprocessid</td><td>VARCHAR(16)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class JobQueue extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: JobQueue.java 988245 2010-08-23 18:39:35Z kwright $";

  // Seeding status values
  public final static int SEEDSTATUS_NOTSEED = 0;
  public final static int SEEDSTATUS_SEED = 1;
  public final static int SEEDSTATUS_NEWSEED = 2;

  // Status values
  public final static int STATUS_PENDING = 0;
  public final static int STATUS_ACTIVE = 1;
  public final static int STATUS_COMPLETE = 2;
  public final static int STATUS_UNCHANGED = 3;
  public final static int STATUS_PENDINGPURGATORY = 4;
  public final static int STATUS_ACTIVEPURGATORY = 5;
  public final static int STATUS_PURGATORY = 6;
  public final static int STATUS_BEINGDELETED = 7;
  public final static int STATUS_ACTIVENEEDRESCAN = 8;
  public final static int STATUS_ACTIVENEEDRESCANPURGATORY = 9;
  public final static int STATUS_BEINGCLEANED = 10;
  public final static int STATUS_ELIGIBLEFORDELETE = 11;
  public final static int STATUS_HOPCOUNTREMOVED = 12;
  
  // Action values
  public final static int ACTION_RESCAN = 0;
  public final static int ACTION_REMOVE = 1;

  // Need priority status
  public final static int NEEDPRIORITY_FALSE = 0;
  public final static int NEEDPRIORITY_INPROGRESS = 1;
  public final static int NEEDPRIORITY_TRUE = 2;
  
  // State descriptions are as follows:
  // PENDING means a newly-added reference that has not been scanned before.
  // ACTIVE means a newly-added reference that is being scanned for the first time.
  // COMPLETE means a reference that has been already scanned (and does not need to be
  //   scanned again for this job session)
  // UNCHANGED represents a document that was previously COMPLETE, and is eligible
  //   to be discovered or seeded, but hasn't been yet.  This is different from PURGATORY because
  //   UNCHANGED documents are not cleaned up at the end, but PURGATORY documents are.
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
  // BEINGDELETED means that the document is queued because the owning job is being
  //   deleted.  It exists so that jobs that are active can avoid processing a document until the cleanup
  //   activity is done.
  //
  // BEINGCLEANED means that the document is queued because the owning job is in the SHUTTINGDOWN
  //   state, and the document was never encountered during the crawl.

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
  public static final String checkActionField = "checkaction";
  public static final String processIDField = "processid";
  public static final String seedingProcessIDField = "seedingprocessid";
  public static final String needPriorityField = "needpriority";
  public static final String needPriorityProcessIDField = "needpriorityprocessid";
  
  public static final double noDocPriorityValue = 1e9;
  public static final Double nullDocPriority = new Double(noDocPriorityValue + 1.0);
  
  protected static final Map<String,Integer> statusMap = new HashMap<String,Integer>();

  static
  {
    statusMap.put("P",new Integer(STATUS_PENDING));
    statusMap.put("A",new Integer(STATUS_ACTIVE));
    statusMap.put("C",new Integer(STATUS_COMPLETE));
    statusMap.put("U",new Integer(STATUS_UNCHANGED));
    statusMap.put("G",new Integer(STATUS_PENDINGPURGATORY));
    statusMap.put("F",new Integer(STATUS_ACTIVEPURGATORY));
    statusMap.put("Z",new Integer(STATUS_PURGATORY));
    statusMap.put("E",new Integer(STATUS_ELIGIBLEFORDELETE));
    statusMap.put("D",new Integer(STATUS_BEINGDELETED));
    statusMap.put("a",new Integer(STATUS_ACTIVENEEDRESCAN));
    statusMap.put("f",new Integer(STATUS_ACTIVENEEDRESCANPURGATORY));
    statusMap.put("d",new Integer(STATUS_BEINGCLEANED));
    statusMap.put("H",new Integer(STATUS_HOPCOUNTREMOVED));
  }

  protected static final Map<String,Integer> seedstatusMap = new HashMap<String,Integer>();

  static
  {
    seedstatusMap.put("F",new Integer(SEEDSTATUS_NOTSEED));
    seedstatusMap.put("S",new Integer(SEEDSTATUS_SEED));
    seedstatusMap.put("N",new Integer(SEEDSTATUS_NEWSEED));
  }

  protected static final Map<String,Integer> actionMap = new HashMap<String,Integer>();

  static
  {
    actionMap.put("R",new Integer(ACTION_RESCAN));
    actionMap.put("D",new Integer(ACTION_REMOVE));
  }

  protected static final Map<String,Integer> needPriorityMap = new HashMap<String,Integer>();
  
  static
  {
    needPriorityMap.put("T",new Integer(NEEDPRIORITY_TRUE));
    needPriorityMap.put("I",new Integer(NEEDPRIORITY_INPROGRESS));
    needPriorityMap.put("F",new Integer(NEEDPRIORITY_FALSE));
  }
  
  /** Prerequisite event manager */
  protected PrereqEventManager prereqEventManager;

  /** Thread context */
  protected IThreadContext threadContext;
  
  /** Cached getNextDocuments order-by index hint */
  protected String getNextDocumentsIndexHint = null;
  
  /** Constructor.
  *@param database is the database handle.
  */
  public JobQueue(IThreadContext tc, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobqueue");
    this.threadContext = tc;
    prereqEventManager = new PrereqEventManager(database);
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ManifoldCFException
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
        map.put(checkActionField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(processIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        map.put(seedingProcessIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        map.put(needPriorityField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(needPriorityProcessIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade; null docpriority fields bashed to 'infinity', so they don't slow down MySQL
      }

      // Secondary table installation
      prereqEventManager.install(getTableName(),idField);

      // Handle indexes
      IndexDescription uniqueIndex = new IndexDescription(true,new String[]{docHashField,jobIDField});
      IndexDescription jobStatusIndex = new IndexDescription(false,new String[]{jobIDField,statusField});
      IndexDescription jobSeedIndex = new IndexDescription(false,new String[]{isSeedField,jobIDField});
      IndexDescription failTimeIndex = new IndexDescription(false,new String[]{failTimeField,jobIDField});
      IndexDescription actionTimeStatusIndex = new IndexDescription(false,new String[]{statusField,checkActionField,checkTimeField});
      IndexDescription needPriorityIndex = new IndexDescription(false,new String[]{needPriorityField});
      // No evidence that the extra fields help at all, for any database...
      IndexDescription docpriorityIndex = new IndexDescription(false,new String[]{docPriorityField,statusField,checkActionField,checkTimeField});

      // Get rid of unused indexes
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (uniqueIndex != null && id.equals(uniqueIndex))
          uniqueIndex = null;
        else if (needPriorityIndex != null && id.equals(needPriorityIndex))
          needPriorityIndex = null;
        else if (jobStatusIndex != null && id.equals(jobStatusIndex))
          jobStatusIndex = null;
        else if (jobSeedIndex != null && id.equals(jobSeedIndex))
          jobSeedIndex = null;
        else if (failTimeIndex != null && id.equals(failTimeIndex))
          failTimeIndex = null;
        else if (actionTimeStatusIndex != null && id.equals(actionTimeStatusIndex))
          actionTimeStatusIndex = null;
        else if (docpriorityIndex != null && id.equals(docpriorityIndex))
          docpriorityIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Build missing indexes

      if (jobStatusIndex != null)
        performAddIndex(null,jobStatusIndex);

      if (needPriorityIndex != null)
        performAddIndex(null,needPriorityIndex);
      
      if (jobSeedIndex != null)
        performAddIndex(null,jobSeedIndex);

      if (failTimeIndex != null)
        performAddIndex(null,failTimeIndex);
      
      if (actionTimeStatusIndex != null)
        performAddIndex(null,actionTimeStatusIndex);

      if (docpriorityIndex != null)
        performAddIndex(null,docpriorityIndex);

      if (uniqueIndex != null)
        performAddIndex(null,uniqueIndex);


      break;
    }
  }

  /** Get the 'getNextDocuments' index hint.
  */
  public String getGetNextDocumentsIndexHint()
    throws ManifoldCFException
  {
    if (getNextDocumentsIndexHint == null)
    {
      // Figure out what index it is
      getNextDocumentsIndexHint = getDBInterface().constructIndexHintClause(getTableName(),
        new IndexDescription(false,new String[]{docPriorityField,statusField,checkActionField,checkTimeField}));
    }
    return getNextDocumentsIndexHint;
  }
  
  /** Analyze job tables due to major event */
  public void unconditionallyAnalyzeTables()
    throws ManifoldCFException
  {
    long startTime = System.currentTimeMillis();
    Logging.perf.debug("Beginning to analyze jobqueue table");
    analyzeTable();
    Logging.perf.debug("Done analyzing jobqueue table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      prereqEventManager.deinstall();
      performDrop(null);
    }
    catch (ManifoldCFException e)
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
  *@param processID is the processID to clean up after.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    // Map NEEDPRIORITY_INPROCESS back to NEEDPRIORITY_TRUE
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(needPriorityField,needPriorityToString(NEEDPRIORITY_INPROGRESS)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map ACTIVE back to PENDING.
    map.clear();
    list.clear();
    map.put(statusField,statusToString(STATUS_PENDING));
    map.put(processIDField,null);
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVENEEDRESCAN)}),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map ACTIVEPURGATORY to PENDINGPURGATORY
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(processIDField,null);
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVEPURGATORY),
        statusToString(STATUS_ACTIVENEEDRESCANPURGATORY)}),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map BEINGDELETED to ELIGIBLEFORDELETE
    map.put(statusField,statusToString(STATUS_ELIGIBLEFORDELETE));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGDELETED)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map BEINGCLEANED to PURGATORY
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGCLEANED)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map newseed fields to seed
    map.clear();
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_SEED));
    map.put(seedingProcessIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED)),
      new UnitaryClause(seedingProcessIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Not accurate, but best we can do without overhead.  This number is chosen so that default
    // values of the reindexing parameters will cause reindexing to occur at this point, but users
    // can configure them higher and shut this down.
    noteModifications(0,50000,0);
    // Always analyze.
    unconditionallyAnalyzeTables();

    TrackerClass.noteGlobalChange("Restart");
  }

  /** Cleanup after all processIDs.
  */
  public void restart()
    throws ManifoldCFException
  {
    // Map NEEDPRIORITY_INPROGRESS to NEEDPRIORITY_TRUE
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(needPriorityField,needPriorityToString(NEEDPRIORITY_INPROGRESS))});
    performUpdate(map,"WHERE "+query,list,null);

    // Map ACTIVE back to PENDING.
    map.clear();
    list.clear();
    map.put(statusField,statusToString(STATUS_PENDING));
    map.put(processIDField,null);
    // This restart is the system one, so make sure that priorities are generated for records going back to PENDING
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVENEEDRESCAN)})});
    performUpdate(map,"WHERE "+query,list,null);

    // Map ACTIVEPURGATORY to PENDINGPURGATORY
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(processIDField,null);
    // This restart is the system one, so make sure that priorities are generated for records going back to PENDING
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVEPURGATORY),
        statusToString(STATUS_ACTIVENEEDRESCANPURGATORY)})});
    performUpdate(map,"WHERE "+query,list,null);

    // Map BEINGDELETED to ELIGIBLEFORDELETE
    map.put(statusField,statusToString(STATUS_ELIGIBLEFORDELETE));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGDELETED))});
    performUpdate(map,"WHERE "+query,list,null);

    // Map BEINGCLEANED to PURGATORY
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGCLEANED))});
    performUpdate(map,"WHERE "+query,list,null);

    // Map newseed fields to seed
    map.clear();
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_SEED));
    map.put(seedingProcessIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED))});
    performUpdate(map,"WHERE "+query,list,null);

    // Not accurate, but best we can do without overhead.  This number is chosen so that default
    // values of the reindexing parameters will cause reindexing to occur at this point, but users
    // can configure them higher and shut this down.
    noteModifications(0,50000,0);
    unconditionallyAnalyzeTables();

    TrackerClass.noteGlobalChange("Restart cluster");
  }
  
  /** Restart for entire cluster.
  */
  public void restartCluster()
    throws ManifoldCFException
  {
    // Clear out all failtime fields (since we obviously haven't been retrying whilst we were not
    // running)
    HashMap map = new HashMap();
    map.put(failTimeField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new NullCheckClause(failTimeField,false)});
    performUpdate(map,"WHERE "+query,list,null);
  }
  
  /** Flip all records for a job that have status HOPCOUNTREMOVED back to PENDING.
  * NOTE: We need to actually schedule these!!!  so the following can't really work. 
  */
  public void reactivateHopcountRemovedRecords(Long jobID)
    throws ManifoldCFException
  {
    Map map = new HashMap();
    // Map HOPCOUNTREMOVED to PENDING
    map.put(statusField,statusToString(STATUS_PENDING));
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    map.put(checkTimeField,new Long(0L));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(statusField,statusToString(STATUS_HOPCOUNTREMOVED))});
    performUpdate(map,"WHERE "+query,list,null);
    unconditionallyAnalyzeTables();
    
    TrackerClass.noteJobChange(jobID,"Map HOPCOUNTREMOVED to PENDING");
  }


  /** Clear the failtimes for all documents associated with a job.
  * This method is called when the system detects that a significant delaying event has occurred,
  * and therefore the "failure clock" needs to be reset.
  *@param jobID is the job identifier.
  */
  public void clearFailTimes(Long jobID)
    throws ManifoldCFException
  {
    Map map = new HashMap();
    map.put(failTimeField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new NullCheckClause(failTimeField,false),
      new UnitaryClause(jobIDField,jobID)});
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Reset as part of restoring document worker threads.
  * This will get called if something went wrong that could have screwed up the
  * status of a worker thread.  The threads all die/end, and this method
  * resets any active documents back to the right state (waiting for stuffing).
  *@param processID is the current processID.
  */
  public void resetDocumentWorkerStatus(String processID)
    throws ManifoldCFException
  {
    // Map ACTIVE back to PENDING.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDING));
    map.put(processIDField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVENEEDRESCAN)}),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);

    // Map ACTIVEPURGATORY to PENDINGPURGATORY
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(processIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVEPURGATORY),
        statusToString(STATUS_ACTIVENEEDRESCANPURGATORY)}),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);
    unconditionallyAnalyzeTables();
        
    TrackerClass.noteGlobalChange("Reset document worker status");
  }

  /** Reset doc delete worker status.
  */
  public void resetDocDeleteWorkerStatus(String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    // Map BEINGDELETED to ELIGIBLEFORDELETE
    map.put(statusField,statusToString(STATUS_ELIGIBLEFORDELETE));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGDELETED)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);
    unconditionallyAnalyzeTables();

    TrackerClass.noteGlobalChange("Reset document delete worker status");
  }

  /** Reset doc cleaning worker status.
  */
  public void resetDocCleanupWorkerStatus(String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    // Map BEINGCLEANED to PURGATORY
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(0L));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_BEINGCLEANED)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);
    unconditionallyAnalyzeTables();

    TrackerClass.noteGlobalChange("Reset document cleanup worker status");
  }

  /** Prepare for a job delete pass.  This will not be called
  * unless the job is in an INACTIVE state.
  * Does the following:
  * (1) Delete PENDING entries
  * (2) Maps PENDINGPURGATORY, PURGATORY, and COMPLETED entries to ELIGIBLEFORDELETE
  *@param jobID is the job identifier.
  */
  public void prepareDeleteScan(Long jobID)
    throws ManifoldCFException
  {
    // Delete PENDING entries
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause("t0."+jobIDField,jobID),
      new MultiClause("t0."+statusField,new Object[]{
        statusToString(STATUS_PENDING),
        statusToString(STATUS_HOPCOUNTREMOVED)})});
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(getTableName()+" t0","t0."+idField,query,list);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_PENDING),
        statusToString(STATUS_HOPCOUNTREMOVED)})});
    performDelete("WHERE "+query,list,null);

    // Turn PENDINGPURGATORY, PURGATORY, COMPLETED into ELIGIBLEFORDELETE.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_ELIGIBLEFORDELETE));
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_PENDINGPURGATORY),
        statusToString(STATUS_COMPLETE),
        statusToString(STATUS_UNCHANGED),
        statusToString(STATUS_PURGATORY)})});
    performUpdate(map,"WHERE "+query,list,null);

    // Not accurate, but best we can do without overhead
    noteModifications(0,2,0);
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();

    TrackerClass.noteJobChange(jobID,"Prepare delete scan");
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
    throws ManifoldCFException
  {
    // Delete PENDING and HOPCOUNTREMOVED entries (they are treated the same)
    ArrayList list = new ArrayList();
    // Clean out prereqevents table first
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause("t0."+jobIDField,jobID),
      new MultiClause("t0."+statusField,new Object[]{
        statusToString(STATUS_PENDING),
        statusToString(STATUS_HOPCOUNTREMOVED)})});
    prereqEventManager.deleteRows(getTableName()+" t0","t0."+idField,query,list);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_PENDING),
        statusToString(STATUS_HOPCOUNTREMOVED)})});
    performDelete("WHERE "+query,list,null);

    // Turn PENDINGPURGATORY and COMPLETED into PURGATORY.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    // Do not reset priorities.  This means, of course, that they may be out of date - but they are probably more accurate in their current form
    // than being set back to some arbitrary value.
    // The alternative, which would be to reprioritize all the documents at this point, is somewhat attractive, but let's see if we can get away
    // without for now.
      
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{  
        statusToString(STATUS_PENDINGPURGATORY),
        statusToString(STATUS_UNCHANGED),
        statusToString(STATUS_COMPLETE)})});
    performUpdate(map,"WHERE "+query,list,null);

    // Not accurate, but best we can do without overhead
    noteModifications(0,2,0);
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();
        
    TrackerClass.noteJobChange(jobID,"Prepare full scan");
  }

  /** Reset schedule for all PENDINGPURGATORY entries.
  *@param jobID is the job identifier.
  */
  public void resetPendingDocumentSchedules(Long jobID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    // Do not reset priorities here!  They should all be blank at this point.
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_PENDINGPURGATORY),
        statusToString(STATUS_PENDING)})});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }
  
  /** For ADD_CHANGE_DELETE jobs where the specifications have been changed,
  * we must reconsider every existing document.  So reconsider them all.
  *@param jobID is the job identifier.
  */
  public void queueAllExisting(Long jobID)
    throws ManifoldCFException
  {
    // Map COMPLETE to PENDINGPURGATORY
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    // Do not reset priorities here!  They should all be blank at this point.
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(statusField,statusToString(STATUS_COMPLETE))});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();
  }
    
  /** Prepare for a "partial" job.  This is called ONLY when the job is inactive.
  *
  * This method maps all COMPLETE entries to UNCHANGED.  The purpose is to
  * allow discovery to find the documents that need to be processed.  If they were
  * marked as COMPLETE that would stop them from being queued.
  *@param jobID is the job identifier.
  */
  public void preparePartialScan(Long jobID)
    throws ManifoldCFException
  {
    // Map COMPLETE to UNCHANGED.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_UNCHANGED));
    // Do not reset priorities here!  They should all be blank at this point.
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(statusField,statusToString(STATUS_COMPLETE))});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
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
    throws ManifoldCFException
  {
    // Map COMPLETE to PENDINGPURGATORY.
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    // Do not reset priorities here!  They should all be blank at this point.
    map.put(checkTimeField,new Long(0L));
    map.put(checkActionField,actionToString(ACTION_RESCAN));
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_COMPLETE),
        statusToString(STATUS_UNCHANGED)})});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    // Do an analyze, otherwise our plans are going to be crap right off the bat
    unconditionallyAnalyzeTables();
      
    TrackerClass.noteJobChange(jobID,"Prepare incremental scan");
  }

  /** Delete ingested document identifiers (as part of deleting the owning job).
  * The number of identifiers specified is guaranteed to be less than the maxInClauseCount
  * for the database.
  *@param identifiers is the set of document identifiers.
  */
  public void deleteIngestedDocumentIdentifiers(DocumentDescription[] identifiers)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    int i = 0;
    while (i < identifiers.length)
    {
      list.add(identifiers[i].getID());
      i++;
    }
    if (list.size() > 0)
      doDeletes(list);
    noteModifications(0,0,identifiers.length);
  }

  /** Check if there are any outstanding active documents for a job */
  public boolean checkJobBusy(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVEPURGATORY),
        statusToString(STATUS_ACTIVENEEDRESCAN),
        statusToString(STATUS_ACTIVENEEDRESCANPURGATORY)})});
    IResultSet set = performQuery("SELECT "+docHashField+" FROM "+getTableName()+
      " WHERE "+query+" "+constructOffsetLimitClause(0,1),list,null,null,1);
    return set.getRowCount() > 0;
  }

  /** For a job deletion: Delete all records for a job.
  *@param jobID is the job identifier.
  */
  public void deleteAllJobRecords(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(getTableName()+" t0","t0."+idField,"t0."+jobIDField+"=?",list);
    list.clear();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
    performDelete("WHERE "+query,list,null);
    noteModifications(0,0,1);
  }

  /** Prepare to calculate a document priority for a given row. */
  public void markNeedPriorityInProgress(Long rowID, String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_INPROGRESS));
    map.put(needPriorityProcessIDField,processID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,rowID)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }
  
  /** Write out a document priority */
  public void writeDocPriority(Long rowID, IPriorityCalculator priority)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
    map.put(needPriorityProcessIDField,null);
    map.put(docPriorityField,new Double(priority.getDocumentPriority()));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,rowID)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }

  /** Mark queued documents for prioritization */
  public void prioritizeQueuedDocuments(Long jobID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_PENDING),
        statusToString(STATUS_PENDINGPURGATORY)})});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }
  
  /** Clear all document priorities for a job that is going to sleep */
  public void noDocPriorities(Long jobID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
    map.put(needPriorityProcessIDField,null);
    map.put(docPriorityField,nullDocPriority);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }
  
  /** Clear all document priorities globally for all documents that
  * have priorities set, and signal that we need new priorities for all.
  */
  public void clearAllDocPriorities()
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    map.put(docPriorityField,nullDocPriority);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(docPriorityField,"<",nullDocPriority)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
  }
  
  /** Set the "completed" status for a record.
  */
  public void updateCompletedRecord(Long recID, int currentStatus)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    
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
      // Remove document priority; we don't want to pollute the queue.  See CONNECTORS-290.
      map.put(docPriorityField,nullDocPriority);
      map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
      break;
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      newStatus = STATUS_PENDINGPURGATORY;
      actionFieldValue = actionToString(ACTION_RESCAN);
      checkTimeValue = new Long(0L);
      // Leave doc priority unchanged.
      break;
    default:
      TrackerClass.printForensics(recID, currentStatus);
      throw new ManifoldCFException("Unexpected jobqueue status - record id "+recID.toString()+", expecting active status, saw "+Integer.toString(currentStatus));
    }

    map.put(statusField,statusToString(newStatus));
    map.put(processIDField,null);
    map.put(checkTimeField,checkTimeValue);
    map.put(checkActionField,actionFieldValue);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,recID)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(recID, newStatus, "Note completion");
  }

  /** Either mark a record as hopcountremoved, or set status to "rescan", depending on the
  * record's state.
  */
  public boolean updateOrHopcountRemoveRecord(Long recID, int currentStatus)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    
    int newStatus;
    String actionFieldValue;
    Long checkTimeValue;
    
    boolean rval;
    
    switch (currentStatus)
    {
    case STATUS_ACTIVE:
    case STATUS_ACTIVEPURGATORY:
      // Mark as hopcountremove (and remove its priority too)
      newStatus = STATUS_HOPCOUNTREMOVED;
      map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
      map.put(needPriorityProcessIDField,null);
      map.put(docPriorityField,nullDocPriority);
      actionFieldValue = actionToString(ACTION_RESCAN);
      checkTimeValue = new Long(0L);
      rval = true;
      break;
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      newStatus = STATUS_PENDINGPURGATORY;
      actionFieldValue = actionToString(ACTION_RESCAN);
      checkTimeValue = new Long(0L);
      rval = false;
      // Leave doc priority unchanged.
      break;
    default:
      TrackerClass.printForensics(recID, currentStatus);
      throw new ManifoldCFException("Unexpected jobqueue status - record id "+recID.toString()+", expecting active status, saw "+Integer.toString(currentStatus));
    }

    map.put(statusField,statusToString(newStatus));
    map.put(processIDField,null);
    map.put(checkTimeField,checkTimeValue);
    map.put(checkActionField,actionFieldValue);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,recID)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(recID, newStatus, "Update or hopcount remove");
    return rval;
  }

  /** Set the status to active on a record, leaving alone priority or check time.
  *@param id is the job queue id.
  *@param currentStatus is the current status
  */
  public void updateActiveRecord(Long id, int currentStatus, String processID)
    throws ManifoldCFException
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
      TrackerClass.printForensics(id, currentStatus);
      throw new ManifoldCFException("Unexpected status value for jobqueue record "+id.toString()+"; got "+Integer.toString(currentStatus));
    }

    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    map.put(processIDField,processID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, newStatus, "Make active");
  }

  /** Set the status on a record, including check time and priority.
  *@param id is the job queue id.
  *@param checkTime is the check time.
  */
  public void setRequeuedStatus(Long id,
    Long checkTime, int action, long failTime, int failCount)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
    map.put(processIDField,null);
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
    // We don't know whether the document was processed or not, but we do know it was
    // put into the Active state.  Therefore, the document priority might have been cleared out.
    // To cover that case, we need to make sure that the document priority gets reset at some point.
    // NOTE WELL: We could be giving the document a new priority right here, but doing that
    // would complicate a number of threads that use this method enormously, and this is a relatively
    // rare situation.  So we just hand such documents to the reprioritizer thread and let it fill in the document priority.
    
    // First update: for those who have an intact doc priority.
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id),
      new UnitaryClause(docPriorityField,"<",nullDocPriority)});
    performUpdate(map,"WHERE "+query,list,null);
    
    // Second update: for rows whose doc priority has been nulled out
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_TRUE));
    map.put(needPriorityProcessIDField,null);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id),
      new UnitaryClause(docPriorityField,nullDocPriority)});
    performUpdate(map,"WHERE "+query,list,null);

    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, STATUS_PENDINGPURGATORY, "Set requeued status");
  }

  /** Set the status of a document to "being deleted".
  */
  public void setDeletingStatus(Long id, String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_BEINGDELETED));
    map.put(processIDField,processID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, STATUS_BEINGDELETED, "Set deleting status");
  }

  /** Set the status of a document to be "no longer deleting" */
  public void setUndeletingStatus(Long id, long checkTime)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_ELIGIBLEFORDELETE));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(checkTime));
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, STATUS_ELIGIBLEFORDELETE, "Set undeleting status");
  }

  /** Set the status of a document to "being cleaned".
  */
  public void setCleaningStatus(Long id, String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_BEINGCLEANED));
    map.put(processIDField,processID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, STATUS_BEINGCLEANED, "Set cleaning status");
  }

  /** Set the status of a document to be "no longer cleaning" */
  public void setUncleaningStatus(Long id, long checkTime)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_PURGATORY));
    map.put(processIDField,null);
    map.put(checkTimeField,new Long(checkTime));
    map.put(checkActionField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,id)});
    performUpdate(map,"WHERE "+query,list,null);
    noteModifications(0,1,0);
    TrackerClass.noteRecordChange(id, STATUS_PURGATORY, "Set uncleaning status");
  }

  /** Remove multiple records entirely.
  *@param ids is the set of job queue id's
  */
  public void deleteRecordMultiple(Long[] ids)
    throws ManifoldCFException
  {
    // Delete in chunks
    int maxClause = maxClauseDoDeletes();
    ArrayList list = new ArrayList();
    int j = 0;
    int i = 0;
    while (i < ids.length)
    {
      if (j == maxClause)
      {
        doDeletes(list);
        list.clear();
        j = 0;
      }
      list.add(ids[i++]);
      j++;
    }
    if (j > 0)
      doDeletes(list);
    noteModifications(0,0,ids.length);
  }

  /** Calculate the number of deletes we can do at once.
  */
  protected int maxClauseDoDeletes()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Do a batch of deletes.
  */
  protected void doDeletes(ArrayList list)
    throws ManifoldCFException
  {
    // Clean out prereqevents table first
    prereqEventManager.deleteRows(list);
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(idField,list)});
    performDelete("WHERE "+query,newList,null);
  }

  /** Remove a record entirely.
  *@param id is the job queue id.
  */
  public void deleteRecord(Long id)
    throws ManifoldCFException
  {
    deleteRecordMultiple(new Long[]{id});
  }

  /** Update an existing record (as the result of an initial add).
  * The record is presumed to exist and have been locked, via "FOR UPDATE".
  */
  public void updateExistingRecordInitial(Long recordID, int currentStatus, Long checkTimeValue,
    long desiredExecuteTime, IPriorityCalculator desiredPriority, String[] prereqEvents,
    String processID)
    throws ManifoldCFException
  {
    // The general rule here is:
    // If doesn't exist, make a PENDING entry.
    // If PENDING, keep it as PENDING.  
    // If COMPLETE, make a PENDING entry.
    // If PURGATORY, make a PENDINGPURGATORY entry.
    // Leave everything else alone and do nothing.

    HashMap map = new HashMap();
    switch (currentStatus)
    {
    case STATUS_ACTIVE:
    case STATUS_ACTIVEPURGATORY:
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
    case STATUS_BEINGCLEANED:
      // These are all the active states.  Being in this state implies that a thread may be working on the document.  We
      // must not interrupt it.
      // Initial adds never bring along any carrydown info, so we should be satisfied as long as the record exists.
      break;

    case STATUS_COMPLETE:
    case STATUS_UNCHANGED:
    case STATUS_PURGATORY:
      // Set the status and time both
      map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
      TrackerClass.noteRecordChange(recordID, STATUS_PENDINGPURGATORY, "Update existing record initial");
      if (desiredExecuteTime == -1L)
        map.put(checkTimeField,new Long(0L));
      else
        map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // Update the doc priority.
      map.put(docPriorityField,new Double(desiredPriority.getDocumentPriority()));
      map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
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
    map.put(seedingProcessIDField,processID);
    // Delete any existing prereqevent entries first
    prereqEventManager.deleteRows(recordID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,recordID)});
    performUpdate(map,"WHERE "+query,list,null);
    // Insert prereqevent entries, if any
    prereqEventManager.addRows(recordID,prereqEvents);
    noteModifications(0,1,0);
  }

  /** Insert a new record into the jobqueue table (as part of adding an initial reference).
  *
  *@param jobID is the job identifier.
  *@param docHash is the hash of the local document identifier.
  *@param docID is the local document identifier.
  */
  public void insertNewRecordInitial(Long jobID, String docHash, String docID, IPriorityCalculator desiredDocPriority,
    long desiredExecuteTime, String[] prereqEvents, String processID)
    throws ManifoldCFException
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
    map.put(seedingProcessIDField,processID);
    // Set the document priority
    map.put(docPriorityField,new Double(desiredDocPriority.getDocumentPriority()));
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
    performInsert(map,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    noteModifications(1,0,0);
    TrackerClass.noteRecordChange(recordID, STATUS_PENDING, "Create initial");
  }

  /** Note the remaining documents that do NOT need to be queued.  These are noted so that the
  * doneDocumentsInitial() method does not clean up seeds from previous runs wrongly.
  */
  public void addRemainingDocumentsInitial(Long jobID, String[] docIDHashes, String processID)
    throws ManifoldCFException
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

    // To avoid deadlock, use 1 instead of something larger.  The docIDs are presumed to come in in sorted order.
    int maxClause = 1;

    ArrayList list = new ArrayList();
    j = 0;
    while (j < docIDHashes.length)
    {
      String docIDHash = docIDHashes[j++];

      if (k == maxClause)
      {
        processRemainingDocuments(idMap,jobID,list,inSet);
        k = 0;
        list.clear();
      }

      list.add(docIDHash);
      k++;
    }
    if (k > 0)
      processRemainingDocuments(idMap,jobID,list,inSet);

    // We have a set of id's.  Process those in bulk.
    k = 0;
    list.clear();
    maxClause = maxClauseUpdateRemainingDocuments();
    Iterator idValues = idMap.keySet().iterator();
    while (idValues.hasNext())
    {
      if (k == maxClause)
      {
        updateRemainingDocuments(list,processID);
        k = 0;
        list.clear();
      }
      list.add(idValues.next());
      k++;
    }
    if (k > 0)
      updateRemainingDocuments(list,processID);
    noteModifications(0,docIDHashes.length,0);
  }

  /** Calculate max */
  protected int maxClauseProcessRemainingDocuments(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
  
  /** Process the specified set of documents. */
  protected void processRemainingDocuments(Map idMap, Long jobID, ArrayList list, Map inSet)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(docHashField,list),
      new UnitaryClause(jobIDField,jobID)});
    newList.add(seedstatusToString(SEEDSTATUS_SEED));
    IResultSet set = performQuery("SELECT "+idField+","+docHashField+" FROM "+getTableName()+
      " WHERE "+query+" AND "+isSeedField+"=? FOR UPDATE",newList,null,null);
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

  /** Get the maximum count */
  protected int maxClauseUpdateRemainingDocuments()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
  
  /** Update the specified set of documents to be "NEWSEED" */
  protected void updateRemainingDocuments(ArrayList list, String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_NEWSEED));
    map.put(seedingProcessIDField,processID);
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(idField,list)});
    performUpdate(map,"WHERE "+query,newList,null);
  }

  /** Complete the initial set of documents.  This method converts the seeding statuses for the
  * job to their steady-state values.
  * SEEDSTATUS_SEED becomes SEEDSTATUS_NOTSEED, and SEEDSTATUS_NEWSEED becomes
  * SEEDSTATUS_SEED.  If the seeding was partial, then all previous seeds are preserved as such.
  *@param jobID is the job identifier.
  *@param isPartial is true of the passed list of seeds is not complete.
  */
  public void doneDocumentsInitial(Long jobID, boolean isPartial)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query;
    HashMap map = new HashMap();
    if (!isPartial)
    {
      query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(isSeedField,seedstatusToString(SEEDSTATUS_SEED)),
        new UnitaryClause(jobIDField,jobID)});
      map.put(isSeedField,seedstatusToString(SEEDSTATUS_NOTSEED));
      performUpdate(map,"WHERE "+query,list,null);
      list.clear();
    }
    query =  buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(isSeedField,seedstatusToString(SEEDSTATUS_SEED)),
      new UnitaryClause(jobIDField,jobID)});
    map.put(isSeedField,seedstatusToString(SEEDSTATUS_SEED));
    map.put(seedingProcessIDField,null);
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Get all the current seeds.
  * Returns the seed document identifiers for a job.
  *@param jobID is the job identifier.
  *@return the document identifier hashes that are currently considered to be seeds.
  */
  public String[] getAllSeeds(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(isSeedField,seedstatusToString(SEEDSTATUS_SEED)),
      new UnitaryClause(jobIDField,jobID)});
    IResultSet set = performQuery("SELECT "+docHashField+" FROM "+getTableName()+" WHERE "+query,
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
  public void updateExistingRecord(Long recordID, int currentStatus, Long checkTimeValue,
    long desiredExecuteTime, boolean otherChangesSeen,
    IPriorityCalculator desiredPriority, String[] prereqEvents)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    switch (currentStatus)
    {
    case STATUS_PURGATORY:
    case STATUS_UNCHANGED:
      // Set the status and time both
      map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
      TrackerClass.noteRecordChange(recordID, STATUS_PENDINGPURGATORY, "Update existing");
      map.put(checkTimeField,new Long(desiredExecuteTime));
      map.put(checkActionField,actionToString(ACTION_RESCAN));
      map.put(failTimeField,null);
      map.put(failCountField,null);
      // Going into pending: set the docpriority.
      map.put(docPriorityField,new Double(desiredPriority.getDocumentPriority()));
      map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
      break;
    case STATUS_COMPLETE:
    case STATUS_BEINGCLEANED:
      // Requeue the document for processing, if there have been other changes.
      if (otherChangesSeen)
      {
        // The document has been processed before, so it has to go into PENDINGPURGATORY.
        // Set the status and time both
        map.put(statusField,statusToString(STATUS_PENDINGPURGATORY));
        TrackerClass.noteRecordChange(recordID, STATUS_PENDINGPURGATORY, "Update existing");
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority.getDocumentPriority()));
        map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
        break;
      }
      return;
    case STATUS_ACTIVENEEDRESCAN:
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      // Document is in the queue, but already needs a rescan for prior reasons.
      // We're done.
      return;
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
        TrackerClass.noteRecordChange(recordID, STATUS_ACTIVENEEDRESCAN, "Update existing");
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority.getDocumentPriority()));
        map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
        break;
      }
      return;
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
        TrackerClass.noteRecordChange(recordID, STATUS_ACTIVENEEDRESCANPURGATORY, "Update existing");
        map.put(checkTimeField,new Long(desiredExecuteTime));
        map.put(checkActionField,actionToString(ACTION_RESCAN));
        map.put(failTimeField,null);
        map.put(failCountField,null);
        // Going into pending: set the docpriority.
        map.put(docPriorityField,new Double(desiredPriority.getDocumentPriority()));
        map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
        break;
      }
      return;
    case STATUS_PENDING:
      // Document is already waiting to be processed.
      // Bump up the schedule, if called for.  Otherwise, just leave it alone.
      Long cv = checkTimeValue;
      if (cv != null)
      {
        long currentExecuteTime = cv.longValue();
        if (currentExecuteTime <= desiredExecuteTime)
          return;
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
      return;
    }
    prereqEventManager.deleteRows(recordID);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,recordID)});
    performUpdate(map,"WHERE "+query,list,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    noteModifications(0,1,0);
    return;
  }

  /** Insert a new record into the jobqueue table (as part of adding a child reference).
  *
  */
  public void insertNewRecord(Long jobID, String docIDHash, String docID, IPriorityCalculator desiredDocPriority, long desiredExecuteTime,
    String[] prereqEvents)
    throws ManifoldCFException
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
    map.put(docPriorityField,new Double(desiredDocPriority.getDocumentPriority()));
    map.put(needPriorityField,needPriorityToString(NEEDPRIORITY_FALSE));
    performInsert(map,null);
    prereqEventManager.addRows(recordID,prereqEvents);
    noteModifications(1,0,0);
    TrackerClass.noteRecordChange(recordID, STATUS_PENDING, "Create new");

  }

  // Methods to convert status strings to integers and back

  /** Convert seedstatus value to a string.
  */
  public static String seedstatusToString(int status)
    throws ManifoldCFException
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
      throw new ManifoldCFException("Invalid seed status: "+Integer.toString(status));
    }
  }

  /** Convert seedstatus field value to a boolean.
  */
  public static int stringToSeedstatus(String x)
    throws ManifoldCFException
  {
    if (x == null || x.length() == 0)
      return SEEDSTATUS_NOTSEED;
    Integer y = seedstatusMap.get(x);
    if (y == null)
      throw new ManifoldCFException("Unknown seed status code: "+x);
    return y.intValue();
  }

  /** Convert action field value to integer.
  */
  public static int stringToAction(String value)
    throws ManifoldCFException
  {
    Integer x = actionMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Unknown action string: '"+value+"'");
    return x.intValue();
  }

  /** Convert integer to action string */
  public static String actionToString(int action)
    throws ManifoldCFException
  {
    switch (action)
    {
    case ACTION_RESCAN:
      return "R";
    case ACTION_REMOVE:
      return "D";
    default:
      throw new ManifoldCFException("Bad action value: "+Integer.toString(action));
    }
  }

  /** Convert need priority value to boolean.
  */
  public static int stringToNeedPriority(String value)
    throws ManifoldCFException
  {
    if (value == null || value.length() == 0)
      return NEEDPRIORITY_FALSE;
    Integer x = needPriorityMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Unknown needpriority string: '"+value+"'");
    return x.intValue();
  }
  
  /** Convert boolean to need priority value.
  */
  public static String needPriorityToString(int value)
    throws ManifoldCFException
  {
    switch (value)
    {
    case NEEDPRIORITY_TRUE:
      return "T";
    case NEEDPRIORITY_FALSE:
      return "F";
    case NEEDPRIORITY_INPROGRESS:
      return "I";
    default:
      throw new ManifoldCFException("Bad needpriority value: "+Integer.toString(value));
    }
  }
  
  /** Convert status field value to integer.
  *@param value is the string.
  *@return the integer.
  */
  public static int stringToStatus(String value)
    throws ManifoldCFException
  {
    Integer x = statusMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Unknown status string: '"+value+"'");
    return x.intValue();
  }

  /** Convert status to string.
  *@param status is the status value.
  *@return the database string.
  */
  public static String statusToString(int status)
    throws ManifoldCFException
  {
    switch (status)
    {
    case STATUS_PENDING:
      return "P";
    case STATUS_ACTIVE:
      return "A";
    case STATUS_COMPLETE:
      return "C";
    case STATUS_UNCHANGED:
      return "U";
    case STATUS_PENDINGPURGATORY:
      return "G";
    case STATUS_ACTIVEPURGATORY:
      return "F";
    case STATUS_PURGATORY:
      return "Z";
    case STATUS_ELIGIBLEFORDELETE:
      return "E";
    case STATUS_BEINGDELETED:
      return "D";
    case STATUS_ACTIVENEEDRESCAN:
      return "a";
    case STATUS_ACTIVENEEDRESCANPURGATORY:
      return "f";
    case STATUS_BEINGCLEANED:
      return "d";
    case STATUS_HOPCOUNTREMOVED:
      return "H";
    default:
      throw new ManifoldCFException("Bad status value: "+Integer.toString(status));
    }

  }

  /** Get a hash value from a document id string.  This will convert the string into something that can fit in 20 characters.
  * (Someday this will be an MD5 hash, but for now just use java hashing.)
  *@param documentIdentifier is the input document id string.
  *@return the hash code.
  */
  public static String getHashCode(String documentIdentifier)
    throws ManifoldCFException
  {
    return ManifoldCF.hash(documentIdentifier);
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
      throws ManifoldCFException
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
      throws ManifoldCFException
    {
      return true;
    }
  }

}
