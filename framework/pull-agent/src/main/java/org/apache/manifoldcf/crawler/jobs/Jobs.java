/* $Id: Jobs.java 991295 2010-08-31 19:12:14Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import java.util.*;

/** This class manages the jobs table.
 * 
 * <br><br>
 * <b>jobs</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>id</td><td>BIGINT</td><td>Primary Key</td></tr>
 * <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>status</td><td>CHAR(1)</td><td>operational field</td></tr>
 * <tr><td>lasttime</td><td>BIGINT</td><td>operational field</td></tr>
 * <tr><td>starttime</td><td>BIGINT</td><td>operational field</td></tr>
 * <tr><td>lastchecktime</td><td>BIGINT</td><td>operational field</td></tr>
 * <tr><td>seedingversion</td><td>LONGTEXT</td><td>operational field</td></tr>
 * <tr><td>endtime</td><td>BIGINT</td><td>operational field</td></tr>
 * <tr><td>docspec</td><td>LONGTEXT</td><td></td></tr>
 * <tr><td>connectionname</td><td>VARCHAR(32)</td><td>Reference:repoconnections.connectionname</td></tr>
 * <tr><td>type</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>intervaltime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>maxintervaltime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>expirationtime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>windowend</td><td>BIGINT</td><td></td></tr>
 * <tr><td>priority</td><td>BIGINT</td><td></td></tr>
 * <tr><td>startmethod</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>errortext</td><td>LONGTEXT</td><td></td></tr>
 * <tr><td>reseedinterval</td><td>BIGINT</td><td></td></tr>
 * <tr><td>reseedtime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>hopcountmode</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>processid</td><td>VARCHAR(16)</td><td></td></tr>
 * <tr><td>failtime</td><td>BIGINT</td><td></td></tr>
 * <tr><td>failcount</td><td>BIGINT</td><td></td></tr>
 * <tr><td>assessmentstate</td><td>CHAR(1)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class Jobs extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: Jobs.java 991295 2010-08-31 19:12:14Z kwright $";

  // Status field values
  //
  // There are some themes in this state diagram.  For instance, for all active states there's a corresponding "active seeding" state, which
  // indicates that automatic seeding is taking place at that time.  Also, the "pause a job" sequence goes from "pausing" to "paused",  while
  // "resuming a job" goes from "resuming" to "active".  This is complicated by the fact that entering the "activewait" state is very similar
  // to entering the "paused" state, in that the job must go first to the "active waiting" state and then to the "active wait" state, but it
  // is completely orthogonal to the "paused" state, because one is triggered automatically and the other by human control.  Similarly,
  // resuming from an "active wait" state must transition through another state to return to the "active" state.
  // 
  // However, it is only certain transitions that require special states.  For example, leaving a waited/paused state and returning to "active"
  // does something, as does entering either active wait or paused.  Also transitions between these states do NOT require intermediates.
  // The state diagram reflects that.  But it also has to reflect that during the "pausing" stage, "activewait" may occur, and visa versa.
  public static final int STATUS_INACTIVE = 0;                            // Not running
  public static final int STATUS_ACTIVE = 1;                              // Active, within a valid window
  public static final int STATUS_ACTIVESEEDING = 2;               // Same as active, but seeding process is currently active also.
  public static final int STATUS_ACTIVEWAITING = 3;                 // In the process of waiting due to window expiration; will enter STATUS_ACTIVEWAIT when done.
  public static final int STATUS_ACTIVEWAITINGSEEDING = 4;    // In the process of waiting due to window exp, also seeding; will enter STATUS_ACTIVEWAITSEEDING when done.
  public static final int STATUS_ACTIVEWAIT = 5;                     // Active, but paused due to window expiration
  public static final int STATUS_ACTIVEWAITSEEDING = 6;        // Same as active wait, but seeding process is currently active also.
  public static final int STATUS_PAUSING = 7;                            // In the process of pausing a job; will enter STATUS_PAUSED when done.
  public static final int STATUS_PAUSINGSEEDING = 8;                 // In the process of pausing a job, but seeding also; will enter STATUS_PAUSEDSEEDING when done.
  public static final int STATUS_PAUSINGWAITING = 9;                // In the process of pausing a job; will enter STATUS_PAUSEDWAIT when done.
  public static final int STATUS_PAUSINGWAITINGSEEDING = 10;        // In the process of pausing a job, but seeding also; will enter STATUS_PAUSEDWAITSEEDING when done.
  public static final int STATUS_PAUSED = 11;                             // Paused, but within a valid window
  public static final int STATUS_PAUSEDSEEDING = 12;              // Same as paused, but seeding process is currently active also.
  public static final int STATUS_PAUSEDWAIT = 13;                        // Paused, and outside of window expiration
  public static final int STATUS_PAUSEDWAITSEEDING = 14;        // Same as paused wait, but seeding process is currently active also.
  public static final int STATUS_SHUTTINGDOWN = 15;                  // Done, except for process cleanup
  public static final int STATUS_RESUMING = 16;                          // In the process of resuming a paused or waited job; will enter STATUS_ACTIVE when done
  public static final int STATUS_RESUMINGSEEDING = 17;               // In the process of resuming a paused or waited job, seeding process active too; will enter STATUS_ACTIVESEEDING when done.
  public static final int STATUS_ABORTING = 18;                          // Aborting (not yet aborted because documents still being processed)
  public static final int STATUS_STARTINGUP = 19;                        // Loading the queue (will go into ACTIVE if successful, or INACTIVE if not)
  public static final int STATUS_STARTINGUPMINIMAL = 20;           // Loading the queue for minimal job run (will go into ACTIVE if successful, or INACTIVE if not)
  public static final int STATUS_READYFORSTARTUP = 23;             // Job is marked for minimal startup; startup thread has not taken it yet.
  public static final int STATUS_READYFORSTARTUPMINIMAL = 24;   // Job is marked for startup; startup thread has not taken it yet.
  public static final int STATUS_READYFORDELETE = 25;             // Job is marked for delete; delete thread has not taken it yet.
  public static final int STATUS_ABORTINGFORRESTART = 27;       // Same as aborting, except after abort is complete startup will happen.
  public static final int STATUS_ABORTINGFORRESTARTMINIMAL = 28;  // Same as aborting, except after abort is complete startup will happen.
  public static final int STATUS_ABORTINGFORRESTARTSEEDING = 29;  // Seeding version of aborting for restart
  public static final int STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL = 30;  // Seeding version of aborting for restart
  public static final int STATUS_ABORTINGSTARTINGUPFORRESTART = 31; // Starting up version of aborting for restart
  public static final int STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL = 32; // Starting up version of aborting for restart
  public static final int STATUS_READYFORNOTIFY = 33;                   // Job is ready to be notified of completion
  public static final int STATUS_NOTIFYINGOFCOMPLETION = 34;    // Notifying connector of terminating job (either aborted, or finished)
  public static final int STATUS_DELETING = 35;                         // The job is deleting.
  public static final int STATUS_DELETESTARTINGUP = 36;         // The delete is starting up.
  public static final int STATUS_ABORTINGSHUTTINGDOWN = 37;     // Aborting the cleanup phase.
  public static final int STATUS_READYFORDELETENOTIFY = 38;     // Job is ready for delete notification
  public static final int STATUS_NOTIFYINGOFDELETION = 39;      // Notifying connector of job deletion
  
  // These statuses have to do with whether a job has an installed underlying connector or not.
  // There are two reasons to have a special state here: (1) if the behavior of the crawler differs, or (2) if the
  // UI would present something different.
  //
  // For the base states, such as for an inactive job, I've chosen not to have an "uninstalled" version of the state, for now.
  // This is because I believe that feedback will be adequate without such states.
  // But, since there is no indication in the jobs table of an uninstalled connector for such jobs, the code which starts
  // jobs up (or otherwise would enter any state that has a corresponding special state) must check to see if the underlying
  // connector exists before deciding what state to put the job into.
  public static final int STATUS_ACTIVE_UNINSTALLED = 40;               // Active, but repository connector not installed
  public static final int STATUS_ACTIVESEEDING_UNINSTALLED = 41;   // Active and seeding, but repository connector not installed
  public static final int STATUS_DELETING_NOOUTPUT = 42;                // Job is being deleted but there's no output connector installed

  // Deprecated states.  These states should never be used; they're defined only for upgrade purposes
  public static final int STATUS_ACTIVE_NOOUTPUT = 100;                  // Active, but output connector not installed
  public static final int STATUS_ACTIVESEEDING_NOOUTPUT = 101;       // Active and seeding, but output connector not installed
  public static final int STATUS_ACTIVE_NEITHER = 102;                     // Active, but neither repository connector nor output connector installed
  public static final int STATUS_ACTIVESEEDING_NEITHER = 103;          // Active and seeding, but neither repository connector nor output connector installed

  // Need Connector Assessment states
  public static final int ASSESSMENT_KNOWN = 0;                         // State is known.
  public static final int ASSESSMENT_UNKNOWN = 1;                       // State is unknown, and job needs assessment
  
  // Type field values
  public static final int TYPE_CONTINUOUS = IJobDescription.TYPE_CONTINUOUS;
  public static final int TYPE_SPECIFIED = IJobDescription.TYPE_SPECIFIED;

  // Start method field values
  public static final int START_WINDOWBEGIN = IJobDescription.START_WINDOWBEGIN;
  public static final int START_WINDOWINSIDE = IJobDescription.START_WINDOWINSIDE;
  public static final int START_DISABLE = IJobDescription.START_DISABLE;

  // Hopcount mode values
  public static final int HOPCOUNT_ACCURATE = IJobDescription.HOPCOUNT_ACCURATE;
  public static final int HOPCOUNT_NODELETE = IJobDescription.HOPCOUNT_NODELETE;
  public static final int HOPCOUNT_NEVERDELETE = IJobDescription.HOPCOUNT_NEVERDELETE;

  // Field names
  public final static String idField = "id";
  public final static String descriptionField = "description";
  public final static String documentSpecField = "docspec";
  public final static String connectionNameField = "connectionname";
  public final static String typeField = "type";
  /** This is the minimum reschedule interval for a document being crawled adaptively (in ms.) */
  public final static String intervalField = "intervaltime";
  /** This is the maximum reschedule interval for a document being crawled adaptively (in ms.) */
  public final static String maxIntervalField = "maxintervaltime";
  /** This is the expiration time of documents for a given job (in ms.) */
  public final static String expirationField = "expirationtime";
  /** This is the job's priority vs. other jobs. */
  public final static String priorityField = "priority";
  /** How/when to start the job */
  public final static String startMethodField = "startmethod";
  /** If this is an adaptive job, what should the reseed interval be (in milliseconds) */
  public final static String reseedIntervalField = "reseedinterval";

  // These fields are NOT part of the definition, but are operational
  /** Status of this job. */
  public final static String statusField = "status";
  /** The last time this job was assessed, in ms. since epoch. */
  public final static String lastTimeField = "lasttime";
  /** If active, paused, activewait, or pausedwait, the start time of the current session, else null. */
  public final static String startTimeField = "starttime";
  /** This text data represents the seeding version string, which for many connectors is simply the last time seeding was done */
  public final static String seedingVersionField = "seedingversion";
  /** If inactive, the end time of the LAST session, if any. */
  public final static String endTimeField = "endtime";
  /** If non-null, this is the time that the current execution window closes, in ms since epoch. */
  public final static String windowEndField = "windowend";
  /** If non-null, this is the last error that occurred (which aborted the last task, either running the job or doing the
  * delete cleanup). */
  public final static String errorField = "errortext";
  /** For an adaptive job, this is the next time to reseed the job. */
  public final static String reseedTimeField = "reseedtime";
  /** For a job whose connector supports hopcounts, this describes how those hopcounts are handled. */
  public final static String hopcountModeField = "hopcountmode";
  /** Process id field, for keeping track of which process owns transient state */
  public final static String processIDField = "processid";
  /** When non-null, indicates the time that, when a ServiceInterruption occurs, the attempt will be considered to have actually failed */
  public static final String failTimeField = "failtime";
  /** When non-null, indicates the number of retries remaining, after which the attempt will be considered to have actually failed */
  public static final String failCountField = "failcount";
  /** Set to N when the job needs connector-installed assessment */
  public static final String assessmentStateField = "assessmentstate";
  
  protected static Map<String,Integer> statusMap;
  protected static Map<String,Integer> typeMap;
  protected static Map<String,Integer> startMap;
  protected static Map<String,Integer> hopmodeMap;
  protected static Map<String,Integer> assessmentMap;
  
  static
  {
    statusMap = new HashMap<String,Integer>();
    statusMap.put("N",new Integer(STATUS_INACTIVE));
    statusMap.put("A",new Integer(STATUS_ACTIVE));
    statusMap.put("P",new Integer(STATUS_PAUSED));
    statusMap.put("S",new Integer(STATUS_SHUTTINGDOWN));
    statusMap.put("s",new Integer(STATUS_READYFORNOTIFY));
    statusMap.put("n",new Integer(STATUS_NOTIFYINGOFCOMPLETION));
    statusMap.put("d",new Integer(STATUS_READYFORDELETENOTIFY));
    statusMap.put("j",new Integer(STATUS_NOTIFYINGOFDELETION));
    statusMap.put("W",new Integer(STATUS_ACTIVEWAIT));
    statusMap.put("Z",new Integer(STATUS_PAUSEDWAIT));
    statusMap.put("X",new Integer(STATUS_ABORTING));
    statusMap.put("B",new Integer(STATUS_STARTINGUP));
    statusMap.put("b",new Integer(STATUS_STARTINGUPMINIMAL));
    statusMap.put("C",new Integer(STATUS_READYFORSTARTUP));
    statusMap.put("c",new Integer(STATUS_READYFORSTARTUPMINIMAL));
    statusMap.put("E",new Integer(STATUS_READYFORDELETE));
    statusMap.put("V",new Integer(STATUS_DELETESTARTINGUP));
    statusMap.put("e",new Integer(STATUS_DELETING));
    statusMap.put("Y",new Integer(STATUS_ABORTINGFORRESTART));
    statusMap.put("M",new Integer(STATUS_ABORTINGFORRESTARTMINIMAL));
    statusMap.put("T",new Integer(STATUS_ABORTINGSTARTINGUPFORRESTART));
    statusMap.put("t",new Integer(STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL));

    statusMap.put("a",new Integer(STATUS_ACTIVESEEDING));
    statusMap.put("p",new Integer(STATUS_PAUSEDSEEDING));
    statusMap.put("w",new Integer(STATUS_ACTIVEWAITSEEDING));
    statusMap.put("z",new Integer(STATUS_PAUSEDWAITSEEDING));
    statusMap.put("y",new Integer(STATUS_ABORTINGFORRESTARTSEEDING));
    statusMap.put("m",new Integer(STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL));
    statusMap.put("v",new Integer(STATUS_ABORTINGSHUTTINGDOWN));
    
    statusMap.put("H",new Integer(STATUS_ACTIVEWAITING));
    statusMap.put("h",new Integer(STATUS_ACTIVEWAITINGSEEDING));
    statusMap.put("F",new Integer(STATUS_PAUSING));
    statusMap.put("f",new Integer(STATUS_PAUSINGSEEDING));
    statusMap.put("G",new Integer(STATUS_PAUSINGWAITING));
    statusMap.put("g",new Integer(STATUS_PAUSINGWAITINGSEEDING));
    statusMap.put("I",new Integer(STATUS_RESUMING));
    statusMap.put("i",new Integer(STATUS_RESUMINGSEEDING));


    // These are the uninstalled states.  The values, I'm afraid, are pretty random.
    statusMap.put("R",new Integer(STATUS_ACTIVE_UNINSTALLED));
    statusMap.put("r",new Integer(STATUS_ACTIVESEEDING_UNINSTALLED));
    statusMap.put("D",new Integer(STATUS_DELETING_NOOUTPUT));

    // These are deprecated states; we may be able to reclaim them
    statusMap.put("O",new Integer(STATUS_ACTIVE_NOOUTPUT));
    statusMap.put("o",new Integer(STATUS_ACTIVESEEDING_NOOUTPUT));
    statusMap.put("U",new Integer(STATUS_ACTIVE_NEITHER));
    statusMap.put("u",new Integer(STATUS_ACTIVESEEDING_NEITHER));

    typeMap = new HashMap<String,Integer>();
    typeMap.put("C",new Integer(TYPE_CONTINUOUS));
    typeMap.put("S",new Integer(TYPE_SPECIFIED));

    startMap = new HashMap<String,Integer>();
    startMap.put("B",new Integer(START_WINDOWBEGIN));
    startMap.put("I",new Integer(START_WINDOWINSIDE));
    startMap.put("D",new Integer(START_DISABLE));

    hopmodeMap = new HashMap<String,Integer>();
    hopmodeMap.put("A",new Integer(HOPCOUNT_ACCURATE));
    hopmodeMap.put("N",new Integer(HOPCOUNT_NODELETE));
    hopmodeMap.put("V",new Integer(HOPCOUNT_NEVERDELETE));
    
    assessmentMap = new HashMap<String,Integer>();
    assessmentMap.put("Y",new Integer(ASSESSMENT_KNOWN));
    assessmentMap.put("N",new Integer(ASSESSMENT_UNKNOWN));
  }

  /* Transient vs. non-transient states
  *==================================
  * There are two kinds of states.  The first kind of state is that which is considered permanent; if the
  * whole world restarts, the state would not change.  Such states reflect the underlying status of the job.
  * The second kind of state is that which is clearly transient but is attached to a given process.  So if the
  * process is killed, the job should leave the transient state and return to a permanent state.  For example,
  * when a given thread decided to undertake seeding, the job is annotated with the process ID of the thread
  * that is performing the seeding operation.  These states are called process-transient.
  *
  * The permanent states are:
  * STATUS_INACTIVE
  * STATUS_ACTIVE
  * STATUS_ACTIVEWAIT
  * STATUS_PAUSEDWAIT
  * STATUS_PAUSED
  * STATUS_READYFORNOTIFY
  * STATUS_READYFORDELETENOTIFY
  * STATUS_READYFORDELETE
  * STATUS_DELETING
  * STATUS_READYFORSTARTUP
  * STATUS_READYFORSTARTUPMINIMAL
  * STATUS_SHUTTINGDOWN
  * STATUS_ABORTINGFORRESTART
  * STATUS_ABORTINGFORRESTARTMINIMAL
  * STATUS_ACTIVE_UNINSTALLED
  * STATUS_ACTIVE_NOOUTPUT
  * STATUS_ACTIVE_NEITHER
  * STATUS_DELETING_NOOUTPUT
  * STATUS_ABORTING
  * STATUS_ACTIVEWAITING
  * STATUS_PAUSING
  * STATUS_PAUSINGWAITING
  * STATUS_RESUMING
  * STATUS_ABORTINGSHUTTINGDOWN
  *  
  * These are the process-transient states:
  * STATUS_DELETESTARTINGUP
  * STATUS_NOTIFYINGOFCOMPLETION
  * STATUS_NOTIFYINGOFDELETION
  * STATUS_STARTINGUP
  * STATUS_STARTINGUPMINIMAL
  * STATUS_ABORTINGSTARTINGUPFORRESTART
  * STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL
  * STATUS_ACTIVEWAITINGSEEDING
  * STATUS_ACTIVEWAITSEEDING
  * STATUS_ACTIVESEEDING
  * STATUS_PAUSINGSEEDING
  * STATUS_PAUSINGWAITINGSEEDING
  * STATUS_PAUSEDSEEDING
  * STATUS_PAUSEDWAITSEEDING
  * STATUS_RESUMINGSEEDING
  * STATUS_ABORTINGFORRESTARTSEEDING
  * STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL
  * STATUS_ACTIVESEEDING_UNINSTALLED
  * STATUS_ACTIVESEEDING_NOOUTPUT
  * STATUS_ACTIVESEEDING_NEITHER
  *
  *
  */
  
  // Local variables
  protected final ICacheManager cacheManager;
  protected final ScheduleManager scheduleManager;
  protected final HopFilterManager hopFilterManager;
  protected final PipelineManager pipelineManager;
  protected final NotificationManager notificationManager;
  
  protected final IOutputConnectionManager outputMgr;
  protected final IRepositoryConnectionManager connectionMgr;
  protected final ITransformationConnectionManager transMgr;
  
  protected final ILockManager lockManager;
  
  protected final IThreadContext threadContext;
  
  protected final static String jobsLock = "JOBS_LOCK";
  /** Constructor.
  *@param database is the database handle.
  */
  public Jobs(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobs");
    this.threadContext = threadContext;
    scheduleManager = new ScheduleManager(threadContext,database);
    hopFilterManager = new HopFilterManager(threadContext,database);
    pipelineManager = new PipelineManager(threadContext,database);
    notificationManager = new NotificationManager(threadContext,database);
    
    cacheManager = CacheManagerFactory.make(threadContext);
    lockManager = LockManagerFactory.make(threadContext);
    
    outputMgr = OutputConnectionManagerFactory.make(threadContext);
    connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);
    transMgr = TransformationConnectionManagerFactory.make(threadContext);
  }

  /** Install or upgrade this table.
  */
  public void install(String transTableName, String transNameField,
    String outputTableName, String outputNameField,
    String connectionTableName, String connectionNameField,
    String notificationConnectionTableName, String notificationConnectionNameField)
    throws ManifoldCFException
  {
    // Standard practice: Have a loop around everything, in case upgrade needs it.
    while (true)
    {
      // These are fields we want to get rid of.
      String oldOutputSpecField = "outputspec";
      String oldOutputNameField = "outputname";
      String oldLastCheckTimeField = "lastchecktime";

      // A place to keep the outputs we find, so we can add them into the pipeline at the end.
      IResultSet outputSet = null;
      
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(idField,new ColumnDescription("BIGINT",true,false,null,null,false));
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(statusField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
        map.put(lastTimeField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(startTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(seedingVersionField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(endTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(documentSpecField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(this.connectionNameField,new ColumnDescription("VARCHAR(32)",false,false,connectionTableName,connectionNameField,false));
        map.put(typeField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
        map.put(intervalField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(maxIntervalField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(expirationField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(windowEndField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(priorityField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(startMethodField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
        map.put(errorField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(reseedIntervalField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(reseedTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(hopcountModeField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(processIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        map.put(failTimeField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(failCountField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(assessmentStateField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Do any needed upgrades
      }

      // Handle related tables
      pipelineManager.install(getTableName(),idField,outputTableName,outputNameField,transTableName,transNameField);
      if (outputSet != null)
      {
        // Go through set and add pipeline stages corresponding to outputs
        for (int k = 0; k < outputSet.getRowCount(); k++)
        {
          IResultRow row = outputSet.getRow(k);
          Long id = (Long)row.getValue(idField);
          String outputConnectionName = (String)row.getValue(oldOutputNameField);
          String outputConnectionSpec = (String)row.getValue(oldOutputSpecField);
          pipelineManager.writeOutputStage(id,outputConnectionName,outputConnectionSpec);
        }
      }
      notificationManager.install(getTableName(),idField,notificationConnectionTableName,notificationConnectionNameField);
      scheduleManager.install(getTableName(),idField);
      hopFilterManager.install(getTableName(),idField);

      // Index management
      IndexDescription statusIndex = new IndexDescription(false,new String[]{statusField,idField,priorityField});
      IndexDescription statusProcessIndex = new IndexDescription(false,new String[]{statusField,processIDField});
      IndexDescription connectionIndex = new IndexDescription(false,new String[]{connectionNameField});
      IndexDescription failTimeIndex = new IndexDescription(false,new String[]{failTimeField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (statusIndex != null && id.equals(statusIndex))
          statusIndex = null;
        else if (statusProcessIndex != null && id.equals(statusProcessIndex))
          statusProcessIndex = null;
        else if (connectionIndex != null && id.equals(connectionIndex))
          connectionIndex = null;
        else if (failTimeIndex != null && id.equals(failTimeIndex))
          failTimeIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (statusIndex != null)
        performAddIndex(null,statusIndex);
      if (statusProcessIndex != null)
        performAddIndex(null,statusProcessIndex);
      if (connectionIndex != null)
        performAddIndex(null,connectionIndex);
      if (failTimeIndex != null)
        performAddIndex(null,failTimeIndex);

      break;

    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      hopFilterManager.deinstall();
      scheduleManager.deinstall();
      notificationManager.deinstall();
      pipelineManager.deinstall();
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

  /** Come up with a maximum time (in minutes) for re-analyzing tables.
  *@return the time, in minutes.
  */
  public int getAnalyzeTime()
    throws ManifoldCFException
  {
    // Since we never expect jobs to grow rapidly, every 24hrs should always be fine
    return 24 * 60;
  }

  /** Analyze job tables that need analysis.
  */
  public void analyzeTables()
    throws ManifoldCFException
  {
    analyzeTable();
  }

  /** Find a list of jobs matching specified notification names.
  */
  public Long[] findJobsMatchingNotifications(List<String> notificationConnectionNames)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    ArrayList params = new ArrayList();
    query.append("SELECT ").append(idField)
      .append(" FROM ").append(getTableName()).append(" t1 WHERE EXISTS(");
    notificationManager.buildNotificationQueryClause(query,params,"t1."+idField,notificationConnectionNames);
    query.append(")");
    IResultSet set = performQuery(query.toString(),params,null,null);
    Long[] rval = new Long[set.getRowCount()];
    for (int i = 0; i < rval.length; i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (Long)row.getValue(idField);
    }
    return rval;
  }

  /** Find a list of jobs matching specified transformation names.
  */
  public Long[] findJobsMatchingTransformations(List<String> transformationConnectionNames)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    ArrayList params = new ArrayList();
    query.append("SELECT ").append(idField)
      .append(" FROM ").append(getTableName()).append(" t1 WHERE EXISTS(");
    pipelineManager.buildTransformationQueryClause(query,params,"t1."+idField,transformationConnectionNames);
    query.append(")");
    IResultSet set = performQuery(query.toString(),params,null,null);
    Long[] rval = new Long[set.getRowCount()];
    for (int i = 0; i < rval.length; i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (Long)row.getValue(idField);
    }
    return rval;
  }

  /** Find a list of jobs matching specified output names.
  */
  public Long[] findJobsMatchingOutputs(List<String> outputConnectionNames)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    ArrayList params = new ArrayList();
    query.append("SELECT ").append(idField)
      .append(" FROM ").append(getTableName()).append(" t1 WHERE EXISTS(");
    pipelineManager.buildOutputQueryClause(query,params,"t1."+idField,outputConnectionNames);
    query.append(")");
    IResultSet set = performQuery(query.toString(),params,null,null);
    Long[] rval = new Long[set.getRowCount()];
    for (int i = 0; i < rval.length; i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (Long)row.getValue(idField);
    }
    return rval;
  }

  /** Read schedule records for a specified set of jobs.  Cannot use caching!
  */
  public ScheduleRecord[][] readScheduleRecords(Long[] jobIDs)
    throws ManifoldCFException
  {
    Map uniqueIDs = new HashMap();
    int i = 0;
    while (i < jobIDs.length)
    {
      Long jobID = jobIDs[i++];
      uniqueIDs.put(jobID,jobID);
    }

    HashMap returnValues = new HashMap();
    beginTransaction();
    try
    {
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = scheduleManager.maxClauseGetRowsAlternate();
      Iterator iter = uniqueIDs.keySet().iterator();
      while (iter.hasNext())
      {
        if (j == maxIn)
        {
          scheduleManager.getRowsAlternate(returnValues,params);
          params.clear();
          j = 0;
        }
        params.add(iter.next());
        j++;
      }
      if (j > 0)
        scheduleManager.getRowsAlternate(returnValues,params);
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }

    // Fill the return array
    ScheduleRecord[][] rval = new ScheduleRecord[jobIDs.length][];
    i = 0;
    while (i < jobIDs.length)
    {
      ArrayList al = (ArrayList)returnValues.get(jobIDs[i]);
      ScheduleRecord[] srList;
      if (al == null)
        srList = new ScheduleRecord[0];
      else
      {
        srList = new ScheduleRecord[al.size()];
        int k = 0;
        while (k < srList.length)
        {
          srList[k] = (ScheduleRecord)al.get(k);
          k++;
        }
      }
      rval[i++] = srList;
    }

    return rval;
  }

  // Only fetch 200 jobs at a time, for resource reasons
  protected final int FETCH_MAX = 200;

  /** Get a list of all jobs which are not in the process of being deleted already.
  *@return the array of all jobs.
  */
  public IJobDescription[] getAll()
    throws ManifoldCFException
  {
    // Begin transaction
    lockManager.enterReadLock(jobsLock);
    try
    {
      // Put together cache key
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getJobsKey());
      ssb.add(getJobStatusKey());
      StringSet cacheKeys = new StringSet(ssb);

      ArrayList list = new ArrayList();
      list.add(statusToString(STATUS_READYFORDELETE));
      list.add(statusToString(STATUS_DELETESTARTINGUP));
      list.add(statusToString(STATUS_DELETING));
      list.add(statusToString(STATUS_DELETING_NOOUTPUT));
      IResultSet set = performQuery("SELECT "+idField+","+descriptionField+" FROM "+
        getTableName()+" WHERE "+statusField+"!=? AND "+statusField+"!=? AND "+statusField+"!=? AND "+statusField+"!=?"+
        " ORDER BY "+descriptionField+" ASC",list,cacheKeys,null);
      
      Long[] ids = new Long[set.getRowCount()];
      boolean[] readOnlies = new boolean[set.getRowCount()];
      
      for (int i = 0; i < set.getRowCount(); i++)
      {
        IResultRow row = set.getRow(i);
        ids[i] = (Long)row.getValue(idField);
        readOnlies[i] = true;
      }
      return loadMultiple(ids,readOnlies);
    }
    finally
    {
      lockManager.leaveReadLock(jobsLock);
    }
  }

  /** Get a list of active job identifiers and their associated connection names.
  *@return a resultset with "jobid" and "connectionname" fields.
  */
  public IResultSet getActiveJobConnections()
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVESEEDING)})});
    return performQuery("SELECT "+idField+" AS jobid,"+connectionNameField+" AS connectionname FROM "+getTableName()+" WHERE "+
      query,list,null,null);
  }

  /** Get unique connection names for all active jobs.
  *@return the array of connection names corresponding to active jobs.
  */
  public String[] getActiveConnectionNames()
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVESEEDING)})});
    IResultSet set = performQuery("SELECT DISTINCT "+connectionNameField+" FROM "+getTableName()+" WHERE "+
      query,list,null,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(connectionNameField);
      i++;
    }
    return rval;
  }

  /** Are there any jobs that have a specified priority level */
  public boolean hasPriorityJobs(int priority)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVESEEDING)})});
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+query+" AND "+priorityField+"="+Integer.toString(priority)+
      " "+constructOffsetLimitClause(0,1),list,null,null,1);
    return set.getRowCount() > 0;
  }

  /** Create a job.
  */
  public IJobDescription create()
    throws ManifoldCFException
  {
    JobDescription rval = new JobDescription();
    rval.setIsNew(true);
    rval.setID(new Long(IDFactory.make(threadContext)));
    return rval;
  }

  /** Delete a job.  This is not enough by itself; the queue entries also need to be deleted.
  *@param id is the job id.
  */
  public void delete(Long id)
    throws ManifoldCFException
  {
    lockManager.enterNonExWriteLock(jobsLock);
    try
    {
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getJobsKey());
      ssb.add(getJobStatusKey());
      ssb.add(getJobIDKey(id));
      StringSet cacheKeys = new StringSet(ssb);
      beginTransaction();
      try
      {
        scheduleManager.deleteRows(id);
        hopFilterManager.deleteRows(id);
        pipelineManager.deleteRows(id);
        notificationManager.deleteRows(id);
        ArrayList params = new ArrayList();
        String query = buildConjunctionClause(params,new ClauseDescription[]{
          new UnitaryClause(idField,id)});
        performDelete("WHERE "+query,params,cacheKeys);
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
    finally
    {
      lockManager.leaveNonExWriteLock(jobsLock);
    }
  }

  /** Load a job for editing.
  *@param id is the job id.
  *@return the editable job description.
  */
  public IJobDescription load(Long id, boolean readOnly)
    throws ManifoldCFException
  {
    return loadMultiple(new Long[]{id}, new boolean[]{readOnly})[0];
  }

  /** Load multiple jobs for editing.
  *@param ids is the set of id's to load
  *@param readOnlies are boolean values, set to true if the job description to be read is to be designated "read only".
  *@return the array of objects, in order.
  */
  public IJobDescription[] loadMultiple(Long[] ids, boolean[] readOnlies)
    throws ManifoldCFException
  {
    IJobDescription[] rval = new IJobDescription[ids.length];
    if (ids.length == 0)
      return rval;

    int inputIndex = 0;
    int outputIndex = 0;
    while (ids.length - inputIndex > FETCH_MAX)
    {
      outputIndex = loadMultipleInternal(rval,outputIndex,ids,readOnlies,inputIndex,FETCH_MAX);
      inputIndex += FETCH_MAX;
    }
    loadMultipleInternal(rval,outputIndex,ids,readOnlies,inputIndex,ids.length-inputIndex);
    return rval;
  }

  protected int loadMultipleInternal(IJobDescription[] rval, int outputIndex, Long[] ids, boolean[] readOnlies, int inputIndex, int length)
    throws ManifoldCFException
  {
    // Build description objects
    JobObjectDescription[] objectDescriptions = new JobObjectDescription[length];
    StringSetBuffer ssb = new StringSetBuffer();
    for (int i = 0; i < length; i++)
    {
      Long id = ids[inputIndex + i];
      ssb.clear();
      ssb.add(getJobIDKey(id));
      objectDescriptions[i] = new JobObjectDescription(id,new StringSet(ssb));
    }

    JobObjectExecutor exec = new JobObjectExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    IJobDescription[] results = exec.getResults(readOnlies,inputIndex);
    for (IJobDescription result : results)
    {
      rval[outputIndex++] = result;
    }
    return outputIndex;
  }

  /** Save a job description.
  *@param jobDescription is the job description.
  */
  public void save(IJobDescription jobDescription)
    throws ManifoldCFException
  {
    // The invalidation keys for this are both the general and the specific.
    Long id = jobDescription.getID();
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getJobsKey());
    ssb.add(getJobStatusKey());
    ssb.add(getJobIDKey(id));
    StringSet invKeys = new StringSet(ssb);

    while (true)
    {
      long sleepAmt = 0L;
      try
      {
        lockManager.enterNonExWriteLock(jobsLock);
        try
        {
          ICacheHandle ch = cacheManager.enterCache(null,invKeys,getTransactionID());
          try
          {
            beginTransaction();
            try
            {
              //performLock();
              // See whether the instance exists
              ArrayList params = new ArrayList();
              String query = buildConjunctionClause(params,new ClauseDescription[]{
                new UnitaryClause(idField,id)});
              IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
                query+" FOR UPDATE",params,null,null);
              HashMap values = new HashMap();
              values.put(descriptionField,jobDescription.getDescription());
              values.put(connectionNameField,jobDescription.getConnectionName());
              String newXML = jobDescription.getSpecification().toXML();
              values.put(documentSpecField,newXML);
              values.put(typeField,typeToString(jobDescription.getType()));
              values.put(startMethodField,startMethodToString(jobDescription.getStartMethod()));
              values.put(intervalField,jobDescription.getInterval());
              values.put(maxIntervalField,jobDescription.getMaxInterval());
              values.put(reseedIntervalField,jobDescription.getReseedInterval());
              values.put(expirationField,jobDescription.getExpiration());
              values.put(priorityField,new Integer(jobDescription.getPriority()));
              values.put(hopcountModeField,hopcountModeToString(jobDescription.getHopcountMode()));

              if (set.getRowCount() > 0)
              {
                // Update
                // We need to reset the seedingVersionField if there are any changes that
                // could affect what set of documents we allow!!!

                IResultRow row = set.getRow(0);

                // Determine whether we need to reset the scan time for documents.
                // Basically, any change to job parameters that could affect ingestion should clear isSame so that we
                // relook at all the documents, not just the recent ones.

                boolean isSame = pipelineManager.compareRows(id,jobDescription);
                if (!isSame)
                {
                  int currentStatus = stringToStatus((String)row.getValue(statusField));
                  if (currentStatus == STATUS_ACTIVE || currentStatus == STATUS_ACTIVESEEDING ||
                    currentStatus == STATUS_ACTIVE_UNINSTALLED || currentStatus == STATUS_ACTIVESEEDING_UNINSTALLED)
                    values.put(assessmentStateField,assessmentStateToString(ASSESSMENT_UNKNOWN));
                }

                // Changing notifications should never reset seeding.
                /*
                if (isSame)
                {
                  isSame = notificationManager.compareRows(id,jobDescription);
                }
                */
                
                if (isSame)
                {
                  String oldDocSpecXML = (String)row.getValue(documentSpecField);
                  if (!oldDocSpecXML.equals(newXML))
                    isSame = false;
                }

                if (isSame)
                  isSame = hopFilterManager.compareRows(id,jobDescription);

                if (!isSame) {
                  //System.out.println("Setting version field to null");
                  values.put(seedingVersionField,null);
                } else {
                  //System.out.println("NOT setting version field to null");
                }

                params.clear();
                query = buildConjunctionClause(params,new ClauseDescription[]{
                  new UnitaryClause(idField,id)});
                performUpdate(values," WHERE "+query,params,null);
                pipelineManager.deleteRows(id);
                notificationManager.deleteRows(id);
                scheduleManager.deleteRows(id);
                hopFilterManager.deleteRows(id);
              }
              else
              {
                // Insert
                values.put(startTimeField,null);
                values.put(seedingVersionField,null);
                values.put(endTimeField,null);
                values.put(statusField,statusToString(STATUS_INACTIVE));
                values.put(lastTimeField,new Long(System.currentTimeMillis()));
                values.put(idField,id);
                performInsert(values,null);
              }

              // Write pipeline rows
              pipelineManager.writeRows(id,jobDescription);
              // Write notification rows
              notificationManager.writeRows(id,jobDescription);
              // Write schedule records
              scheduleManager.writeRows(id,jobDescription);
              // Write hop filter rows
              hopFilterManager.writeRows(id,jobDescription);
              
              cacheManager.invalidateKeys(ch);
              break;
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
          finally
          {
            cacheManager.leaveCache(ch);
          }
        }
        finally
        {
          lockManager.leaveNonExWriteLock(jobsLock);
        }
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() != ManifoldCFException.DATABASE_TRANSACTION_ABORT)
          throw e;
        sleepAmt = getSleepAmt();
        continue;
      }
      finally
      {
        sleepFor(sleepAmt);
      }
    }
  }

  /** Clear seeding state for a job.
  *@param jobID is the job whose state should be cleared.
  */
  public void clearSeedingState(Long jobID)
    throws ManifoldCFException
  {
    Map values = new HashMap();
    values.put(seedingVersionField,null);
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    performUpdate(values,"WHERE "+query,params,null);
  }

  /** This method is called on a restart.
  *@param processID is the process to be restarting.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    StringSet invKey = new StringSet(getJobStatusKey());
    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query;
      
    // Starting up delete goes back to just being ready for delete
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_DELETESTARTINGUP)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORDELETE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Notifying of completion goes back to just being ready for notify
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFCOMPLETION)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Notifying of deletion goes back to just being ready for delete notify
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFDELETION)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORDELETENOTIFY));
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Starting up or aborting starting up goes back to just being ready
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_STARTINGUP)}),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUP));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Starting up or aborting starting up goes back to just being ready
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_STARTINGUPMINIMAL)}),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUPMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Aborting starting up for restart state goes to ABORTINGFORRESTART
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTART)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Aborting starting up for restart state goes to ABORTINGFORRESTART
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // All seeding values return to pre-seeding values
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVEWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGWAITINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSINGWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_RESUMINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_RESUMING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVEWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDWAITSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSEDWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING_UNINSTALLED)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVE_UNINSTALLED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();

  }

  /** Clean up after all process IDs.
  */
  public void restart()
    throws ManifoldCFException
  {
    StringSet invKey = new StringSet(getJobStatusKey());
    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query;
    
    // All jobs with non-null failTime and failCount get set to null failTime and failCount
    
    // Starting up delete goes back to just being ready for delete
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_DELETESTARTINGUP))});
    map.put(statusField,statusToString(STATUS_READYFORDELETE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Notifying of completion goes back to just being ready for notify
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFCOMPLETION))});
    map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Notifying of deletion goes back to just being ready for delete notify
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFDELETION))});
    map.put(statusField,statusToString(STATUS_READYFORDELETENOTIFY));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Starting up or aborting starting up goes back to just being ready
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_STARTINGUP)})});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUP));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Starting up or aborting starting up goes back to just being ready
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_STARTINGUPMINIMAL)})});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUPMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Aborting starting up for restart state goes to ABORTINGFORRESTART
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTART))});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // Aborting starting up for restart state goes to ABORTINGFORRESTART
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL))});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

    // All seeding values return to pre-seeding values
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING))});
    map.put(statusField,statusToString(STATUS_ACTIVE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGSEEDING))});
    map.put(statusField,statusToString(STATUS_PAUSING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITINGSEEDING))});
    map.put(statusField,statusToString(STATUS_ACTIVEWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGWAITINGSEEDING))});
    map.put(statusField,statusToString(STATUS_PAUSINGWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_RESUMINGSEEDING))});
    map.put(statusField,statusToString(STATUS_RESUMING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDING))});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL))});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDSEEDING))});
    map.put(statusField,statusToString(STATUS_PAUSED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITSEEDING))});
    map.put(statusField,statusToString(STATUS_ACTIVEWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDWAITSEEDING))});
    map.put(statusField,statusToString(STATUS_PAUSEDWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING_UNINSTALLED))});
    map.put(statusField,statusToString(STATUS_ACTIVE_UNINSTALLED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

  }

  public void restartCluster()
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(failTimeField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new NullCheckClause(failTimeField,false)});
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Invalidate current state with respect to installed connectors, as a result of registration.
  */
  public void invalidateCurrentUnregisteredState(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    // If we are in a state that cares about the connector state, then we have to signal we need an assessment.
    if (oldStatusValue == STATUS_ACTIVE_UNINSTALLED || oldStatusValue == STATUS_ACTIVESEEDING_UNINSTALLED || oldStatusValue == STATUS_DELETING_NOOUTPUT)
    {
      // Assessment state is not cached, so no cache invalidation needed
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      HashMap map = new HashMap();
      map.put(assessmentStateField,assessmentStateToString(ASSESSMENT_UNKNOWN));
      performUpdate(map,"WHERE "+query,list,null);
    }
  }
  
  /** Signal to a job that an underlying transformation connector has gone away.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteTransformationConnectorDeregistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    int newStatusValue;
    // The following states are special, in that when the underlying connector goes away, the jobs
    // in such states are switched away to something else.  There are TWO reasons that a state may be in
    // this category: EITHER we don't want the job in this state to be treated in the same way if its
    // connector is uninstalled, OR we need feedback for the user interface.  If it's the latter situation,
    // then all usages of the corresponding states will be identical - and that's in fact precisely where we
    // start with in all the code.
    switch (oldStatusValue)
    {
    case STATUS_ACTIVE:
      newStatusValue = STATUS_ACTIVE_UNINSTALLED;
      break;
    case STATUS_ACTIVESEEDING:
      newStatusValue = STATUS_ACTIVESEEDING_UNINSTALLED;
      break;
    default:
      newStatusValue = oldStatusValue;
      break;
    }
    if (newStatusValue == oldStatusValue)
      return;

    StringSet invKey = new StringSet(getJobStatusKey());

    HashMap newValues = new HashMap();
    newValues.put(statusField,statusToString(newStatusValue));
    newValues.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    performUpdate(newValues,"WHERE "+query,list,invKey);
  }

  /** Signal to a job that an underlying transformation connector has been registered.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteTransformationConnectorRegistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    invalidateCurrentUnregisteredState(jobID,oldStatusValue);
  }

  /** Signal to a job that its underlying output connector has gone away.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteOutputConnectorDeregistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    int newStatusValue;
    // The following states are special, in that when the underlying connector goes away, the jobs
    // in such states are switched away to something else.  There are TWO reasons that a state may be in
    // this category: EITHER we don't want the job in this state to be treated in the same way if its
    // connector is uninstalled, OR we need feedback for the user interface.  If it's the latter situation,
    // then all usages of the corresponding states will be identical - and that's in fact precisely where we
    // start with in all the code.
    switch (oldStatusValue)
    {
    case STATUS_ACTIVE:
      newStatusValue = STATUS_ACTIVE_UNINSTALLED;
      break;
    case STATUS_ACTIVESEEDING:
      newStatusValue = STATUS_ACTIVESEEDING_UNINSTALLED;
      break;
    case STATUS_DELETING:
      newStatusValue = STATUS_DELETING_NOOUTPUT;
      break;
    default:
      newStatusValue = oldStatusValue;
      break;
    }
    if (newStatusValue == oldStatusValue)
      return;

    StringSet invKey = new StringSet(getJobStatusKey());

    HashMap newValues = new HashMap();
    newValues.put(statusField,statusToString(newStatusValue));
    newValues.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    performUpdate(newValues,"WHERE "+query,list,invKey);
  }
  
  /** Signal to a job that its underlying output connector has returned.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteOutputConnectorRegistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    if (oldStatusValue == STATUS_DELETING_NOOUTPUT)
    {
      // We can do the state transition now.
      StringSet invKey = new StringSet(getJobStatusKey());

      HashMap newValues = new HashMap();
      newValues.put(statusField,statusToString(STATUS_DELETING));
      newValues.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      performUpdate(newValues,"WHERE "+query,list,invKey);
      return;
    }
    // Otherwise, we don't know the state, and can't do the work now because we'd deadlock
    invalidateCurrentUnregisteredState(jobID,oldStatusValue);
  }
  
  /** Signal to a job that its underlying notification connector has gone away.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteNotificationConnectorDeregistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    // MHL
  }
  
  /** Signal to a job that its underlying connector has gone away.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteConnectorDeregistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    int newStatusValue;
    // The following states are special, in that when the underlying connector goes away, the jobs
    // in such states are switched away to something else.  There are TWO reasons that a state may be in
    // this category: EITHER we don't want the job in this state to be treated in the same way if its
    // connector is uninstalled, OR we need feedback for the user interface.  If it's the latter situation,
    // then all usages of the corresponding states will be identical - and that's in fact precisely where we
    // start with in all the code.
    switch (oldStatusValue)
    {
    case STATUS_ACTIVE:
      newStatusValue = STATUS_ACTIVE_UNINSTALLED;
      break;
    case STATUS_ACTIVESEEDING:
      newStatusValue = STATUS_ACTIVESEEDING_UNINSTALLED;
      break;
    default:
      newStatusValue = oldStatusValue;
      break;
    }
    if (newStatusValue == oldStatusValue)
      return;

    StringSet invKey = new StringSet(getJobStatusKey());

    HashMap newValues = new HashMap();
    newValues.put(statusField,statusToString(newStatusValue));
    newValues.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    performUpdate(newValues,"WHERE "+query,list,invKey);
  }
  
  /** Signal to a job that its underlying notification connector has returned.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteNotificationConnectorRegistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Signal to a job that its underlying connector has returned.
  *@param jobID is the identifier of the job.
  *@param oldStatusValue is the current status value for the job.
  */
  public void noteConnectorRegistration(Long jobID, int oldStatusValue)
    throws ManifoldCFException
  {
    invalidateCurrentUnregisteredState(jobID,oldStatusValue);
  }
  
  /** Note a change in connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external repository change
  * is signalled.
  */
  public void noteConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    // No cache keys need invalidation, since we're changing the start time, not the status.
    HashMap newValues = new HashMap();
    newValues.put(seedingVersionField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(connectionNameField,connectionName)});
    performUpdate(newValues,"WHERE "+query,list,null);
  }

  /** Note a change in notification connection configuration.
  * This method will be called whenever a notification connection's configuration is modified, or when an external repository change
  * is signalled.
  */
  public void noteNotificationConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    // Notification configuration change does not change anything in a crawl
  }

  /** Note a change in output connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external target config change
  * is signalled.
  */
  public void noteOutputConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    // No cache keys need invalidation, since we're changing the start time, not the status.
    HashMap newValues = new HashMap();
    newValues.put(seedingVersionField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new JoinClause(getTableName()+"."+idField,pipelineManager.ownerIDField),
      new UnitaryClause(pipelineManager.outputNameField,connectionName)});
    performUpdate(newValues,"WHERE EXISTS(SELECT 'x' FROM "+pipelineManager.getTableName()+" WHERE "+query+")",list,null);
  }

  /** Note a change in transformation connection configuration.
  * This method will be called whenever a connection's configuration is modified.
  */
  public void noteTransformationConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    // No cache keys need invalidation, since we're changing the start time, not the status.
    HashMap newValues = new HashMap();
    newValues.put(seedingVersionField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new JoinClause(getTableName()+"."+idField,pipelineManager.ownerIDField),
      new UnitaryClause(pipelineManager.transformationNameField,connectionName)});
    performUpdate(newValues,"WHERE EXISTS(SELECT 'x' FROM "+pipelineManager.getTableName()+" WHERE "+query+")",list,null);
  }
  
  /** Check whether a job's status indicates that it is in ACTIVE or ACTIVESEEDING state.
  */
  public boolean checkJobActive(Long jobID)
    throws ManifoldCFException
  {
    StringSet cacheKeys = new StringSet(getJobStatusKey());
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    IResultSet set = performQuery("SELECT "+statusField+" FROM "+
      getTableName()+" WHERE "+query,list,cacheKeys,null);
    if (set.getRowCount() == 0)
      return false;
    IResultRow row = set.getRow(0);
    String statusValue = (String)row.getValue(statusField);
    int status = stringToStatus(statusValue);
    // Any active state in the lifecycle will do: seeding, active, active_seeding
    return (status == STATUS_ACTIVE || status == STATUS_ACTIVESEEDING ||
      status == STATUS_STARTINGUP || status == STATUS_STARTINGUPMINIMAL);
  }

  /** Reset delete startup worker thread status.
  */
  public void resetDeleteStartupWorkerStatus(String processID)
    throws ManifoldCFException
  {
    // This handles everything that the delete startup thread would resolve.

    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_DELETESTARTINGUP)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORDELETE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

  }
  
  /** Reset notification worker thread status.
  */
  public void resetNotificationWorkerStatus(String processID)
    throws ManifoldCFException
  {
    // This resets everything that the job notification thread would resolve.

    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFCOMPLETION)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

    list.clear();
    map.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_NOTIFYINGOFDELETION)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORDELETENOTIFY));
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

  }
  
  /** Reset startup worker thread status.
  */
  public void resetStartupWorkerStatus(String processID)
    throws ManifoldCFException
  {
    // We have to handle all states that the startup thread would resolve, and change them to something appropriate.

    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query;

    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_STARTINGUP)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUP));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_STARTINGUPMINIMAL)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_READYFORSTARTUPMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTART)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));

  }

  /** Reset as part of restoring seeding worker threads.
  */
  public void resetSeedingWorkerStatus(String processID)
    throws ManifoldCFException
  {
    StringSet invKey = new StringSet(getJobStatusKey());
    ArrayList list = new ArrayList();
    HashMap map = new HashMap();
    String query;
    // All seeding values return to pre-seeding values
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVE));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVEWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSINGWAITINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSINGWAITING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_RESUMINGSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_RESUMING));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTART));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ABORTINGFORRESTARTMINIMAL));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVEWAITSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVEWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_PAUSEDWAITSEEDING)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_PAUSEDWAIT));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_ACTIVESEEDING_UNINSTALLED)),
      new UnitaryClause(processIDField,processID)});
    map.put(statusField,statusToString(STATUS_ACTIVE_UNINSTALLED));
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,invKey);

  }

  /** Retry startup.
  *@param jobID is the job identifier.
  *@param requestMinimum is true for a minimal crawl.
  *@param failTime is the fail time, -1 == none
  *@param failCount is the fail count to use, -1 == none.
  */
  public void retryStartup(Long jobID, boolean requestMinimum, long failTime, int failCount)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    if (requestMinimum)
      map.put(statusField,statusToString(STATUS_READYFORSTARTUPMINIMAL));
    else
      map.put(statusField,statusToString(STATUS_READYFORSTARTUP));
    if (failTime == -1L)
      map.put(failTimeField,null);
    else
      map.put(failTimeField,new Long(failTime));
    if (failCount == -1)
      map.put(failCountField,null);
    else
      map.put(failCountField,failCount);
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Retry seeding.
  *@param jobID is the job identifier.
  *@param failTime is the fail time, -1 == none
  *@param failCount is the fail count to use, -1 == none.
  */
  public void retrySeeding(Long jobID, long failTime, int failCount)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      // Map the field values we see to those we want.
      // This is exactly equivalent to finishing seeding, except for fail time etc.
      IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+" WHERE "+
        query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Can't find job "+jobID.toString());
      IResultRow row = set.getRow(0);
      int status = stringToStatus((String)row.getValue(statusField));
      int newStatus;
      switch (status)
      {
      case STATUS_ACTIVESEEDING:
        newStatus = STATUS_ACTIVE;
        break;
      case STATUS_PAUSINGSEEDING:
        newStatus = STATUS_PAUSING;
        break;
      case STATUS_ACTIVEWAITINGSEEDING:
        newStatus = STATUS_ACTIVEWAITING;
        break;
      case STATUS_PAUSINGWAITINGSEEDING:
        newStatus = STATUS_PAUSINGWAITING;
        break;
      case STATUS_ACTIVESEEDING_UNINSTALLED:
        newStatus = STATUS_ACTIVE_UNINSTALLED;
        break;
      case STATUS_ACTIVEWAITSEEDING:
        newStatus = STATUS_ACTIVEWAIT;
        break;
      case STATUS_PAUSEDSEEDING:
        newStatus = STATUS_PAUSED;
        break;
      case STATUS_PAUSEDWAITSEEDING:
        newStatus = STATUS_PAUSEDWAIT;
        break;
      case STATUS_ABORTINGFORRESTARTSEEDING:
        newStatus = STATUS_ABORTINGFORRESTART;
        break;
      case STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL:
        newStatus = STATUS_ABORTINGFORRESTARTMINIMAL;
        break;
      default:
        throw new ManifoldCFException("Unexpected job status encountered: "+Integer.toString(status));
      }
      HashMap map = new HashMap();
      map.put(statusField,statusToString(newStatus));
      if (failTime == -1L)
        map.put(failTimeField,null);
      else
        map.put(failTimeField,new Long(failTime));
      if (failCount == -1)
        map.put(failCountField,null);
      else
        map.put(failCountField,failCount);
      map.put(processIDField,null);
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (RuntimeException e)
    {
      signalRollback();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Retry notification.
  *@param jobID is the job identifier.
  *@param failTime is the fail time, -1 == none
  *@param failCount is the fail count to use, -1 == none.
  */
  public void retryNotification(Long jobID, long failTime, int failCount)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
    if (failTime == -1L)
      map.put(failTimeField,null);
    else
      map.put(failTimeField,new Long(failTime));
    if (failCount == -1)
      map.put(failCountField,null);
    else
      map.put(failCountField,failCount);
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Retry delete notification.
  *@param jobID is the job identifier.
  *@param failTime is the fail time, -1 == none
  *@param failCount is the fail count to use, -1 == none.
  */
  public void retryDeleteNotification(Long jobID, long failTime, int failCount)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_READYFORDELETENOTIFY));
    if (failTime == -1L)
      map.put(failTimeField,null);
    else
      map.put(failTimeField,new Long(failTime));
    if (failCount == -1)
      map.put(failCountField,null);
    else
      map.put(failCountField,failCount);
    map.put(processIDField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Write job status and window end, and clear the endtime field.  (The start time will be written
  * when the job enters the "active" state.)
  *@param jobID is the job identifier.
  *@param windowEnd is the window end time, if any
  *@param requestMinimum is true if a minimal job run is requested
  */
  public void startJob(Long jobID, Long windowEnd, boolean requestMinimum)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(requestMinimum?STATUS_READYFORSTARTUPMINIMAL:STATUS_READYFORSTARTUP));
    map.put(endTimeField,null);
    // Make sure error is removed (from last time)
    map.put(errorField,null);
    map.put(windowEndField,windowEnd);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Assess all marked jobs to determine if they can be reactivated.
  */
  public void assessMarkedJobs()
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(assessmentStateField,assessmentStateToString(ASSESSMENT_UNKNOWN))});
    // Query for the matching jobs, and then for each job potentially adjust the state based on the connector status
    IResultSet set = performQuery("SELECT "+idField+","+statusField+","+connectionNameField+" FROM "+
      getTableName()+" WHERE "+query+" FOR UPDATE",
      newList,null,null);
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      Long jobID = (Long)row.getValue(idField);
      String connectionName = (String)row.getValue(connectionNameField);
      String[] transformationNames = pipelineManager.getTransformationConnectionNames(jobID);
      String[] outputNames = pipelineManager.getOutputConnectionNames(jobID);
      int statusValue = stringToStatus((String)row.getValue(statusField));
      int newValue;
      
      // Based on status value, see what we need to know to determine if the state can be switched
      switch (statusValue)
      {
      case STATUS_DELETING_NOOUTPUT:
        if (checkOutputsInstalled(outputNames))
          newValue = STATUS_DELETING;
        else
          return;
        break;
      case STATUS_ACTIVE_UNINSTALLED:
        if (connectionMgr.checkConnectorExists(connectionName) &&
          checkTransformationsInstalled(transformationNames) &&
          checkOutputsInstalled(outputNames))
          newValue = STATUS_ACTIVE;
        else
          return;
        break;
      case STATUS_ACTIVESEEDING_UNINSTALLED:
        if (connectionMgr.checkConnectorExists(connectionName) &&
          checkTransformationsInstalled(transformationNames) &&
          checkOutputsInstalled(outputNames))
          newValue = STATUS_ACTIVESEEDING;
        else
          return;
        break;
      default:
        return;
      }
      
      ArrayList list = new ArrayList();
      query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      HashMap map = new HashMap();
      map.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }

  }
  
  /** Put job back into active state, from the shutting-down state.
  *@param jobID is the job identifier.
  */
  public void returnJobToActive(Long jobID)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+","+connectionNameField+" FROM "+getTableName()+" WHERE "+
        query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Can't find job "+jobID.toString());
      
      IResultRow row = set.getRow(0);
      
      int status = stringToStatus((String)row.getValue(statusField));
      int newStatus;
      switch (status)
      {
      case STATUS_SHUTTINGDOWN:
        String[] transformationConnectionNames = pipelineManager.getTransformationConnectionNames(jobID);
        String[] outputConnectionNames = pipelineManager.getOutputConnectionNames(jobID);
        String connectionName = (String)row.getValue(connectionNameField);
        // Want either STATUS_ACTIVE, or STATUS_ACTIVE_UNINSTALLED
        if (!checkTransformationsInstalled(transformationConnectionNames) ||
          !checkOutputsInstalled(outputConnectionNames) ||
          !connectionMgr.checkConnectorExists(connectionName))
          newStatus = STATUS_ACTIVE_UNINSTALLED;
        else
          newStatus = STATUS_ACTIVE;
        break;
      default:
        // Complain!
        throw new ManifoldCFException("Unexpected job status encountered: "+Integer.toString(status));
      }

      HashMap map = new HashMap();
      map.put(statusField,statusToString(newStatus));
      map.put(processIDField,null);
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (RuntimeException e)
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

  protected boolean checkTransformationsInstalled(String[] transformationNames)
    throws ManifoldCFException
  {
    for (String transformationName : transformationNames)
    {
      if (!transMgr.checkConnectorExists(transformationName))
        return false;
    }
    return true;
  }

  protected boolean checkOutputsInstalled(String[] outputNames)
    throws ManifoldCFException
  {
    for (String outputName : outputNames)
    {
      if (!outputMgr.checkConnectorExists(outputName))
        return false;
    }
    return true;
  }
  
  /** Put job into "deleting" state, and set the start time field.
  *@param jobID is the job identifier.
  *@param startTime is the current time in milliseconds from start of epoch.
  */
  public void noteJobDeleteStarted(Long jobID, long startTime)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+" WHERE "+
        query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Can't find job "+jobID.toString());
      IResultRow row = set.getRow(0);
      int status = stringToStatus((String)row.getValue(statusField));
      String[] outputNames = pipelineManager.getOutputConnectionNames(jobID);
      int newStatus;
      switch (status)
      {
      case STATUS_DELETESTARTINGUP:
        if (checkOutputsInstalled(outputNames))
          newStatus = STATUS_DELETING;
        else
          newStatus = STATUS_DELETING_NOOUTPUT;
        break;
      default:
        // Complain!
        throw new ManifoldCFException("Unexpected job status encountered: "+Integer.toString(status));
      }

      HashMap map = new HashMap();
      map.put(statusField,statusToString(newStatus));
      if (newStatus == STATUS_DELETING || newStatus == STATUS_DELETING_NOOUTPUT)
      {
        map.put(startTimeField,new Long(startTime));
      }
      // Clear out seeding version, in case we wind up keeping the job and rerunning it
      map.put(seedingVersionField,null);
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (RuntimeException e)
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

  /** Make job active, and set the start time field.
  *@param jobID is the job identifier.
  *@param startTime is the current time in milliseconds from start of epoch.
  *@param seedVersionString is the version string to record for the seeding.
  */
  public void noteJobStarted(Long jobID, long startTime, String seedVersionString)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+","+connectionNameField+" FROM "+getTableName()+" WHERE "+
        query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Can't find job "+jobID.toString());
      IResultRow row = set.getRow(0);
      
      int status = stringToStatus((String)row.getValue(statusField));
      int newStatus;
      switch (status)
      {
      case STATUS_STARTINGUP:
      case STATUS_STARTINGUPMINIMAL:
        String[] transformationConnectionNames = pipelineManager.getTransformationConnectionNames(jobID);
        String[] outputConnectionNames = pipelineManager.getOutputConnectionNames(jobID);
        String connectionName = (String)row.getValue(connectionNameField);

        // Need to set either STATUS_ACTIVE, or STATUS_ACTIVE_UNINSTALLED
        if (!checkTransformationsInstalled(transformationConnectionNames) ||
          !checkOutputsInstalled(outputConnectionNames) ||
          !connectionMgr.checkConnectorExists(connectionName))
          newStatus = STATUS_ACTIVE_UNINSTALLED;
        else
          newStatus = STATUS_ACTIVE;
        break;
      case STATUS_ABORTINGSTARTINGUPFORRESTART:
        newStatus = STATUS_ABORTINGFORRESTART;
        break;
      case STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL:
        newStatus = STATUS_ABORTINGFORRESTARTMINIMAL;
        break;
      default:
        // Complain!
        throw new ManifoldCFException("Unexpected job status encountered: "+Integer.toString(status));
      }

      HashMap map = new HashMap();
      map.put(statusField,statusToString(newStatus));
      if (newStatus == STATUS_ACTIVE || newStatus == STATUS_ACTIVE_UNINSTALLED)
      {
        map.put(startTimeField,new Long(startTime));
      }
      // The seeding was complete or we wouldn't have gotten called, so at least note that.
      map.put(seedingVersionField,seedVersionString);
      // Clear out the retry fields we might have set
      map.put(failTimeField,null);
      map.put(failCountField,null);
      map.put(processIDField,null);
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (RuntimeException e)
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

  /** Note job seeded.
  *@param jobID is the job id.
  *@param seedVersionString is the job seed version string.
  */
  public void noteJobSeeded(Long jobID, String seedVersionString)
    throws ManifoldCFException
  {
    // We have to convert the current status to the non-seeding equivalent
    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+" WHERE "+
        query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Can't find job "+jobID.toString());
      IResultRow row = set.getRow(0);
      int status = stringToStatus((String)row.getValue(statusField));
      int newStatus;
      switch (status)
      {
      case STATUS_ACTIVESEEDING:
        newStatus = STATUS_ACTIVE;
        break;
      case STATUS_PAUSINGSEEDING:
        newStatus = STATUS_PAUSING;
        break;
      case STATUS_ACTIVEWAITINGSEEDING:
        newStatus = STATUS_ACTIVEWAITING;
        break;
      case STATUS_PAUSINGWAITINGSEEDING:
        newStatus = STATUS_PAUSINGWAITING;
        break;
      case STATUS_ACTIVESEEDING_UNINSTALLED:
        newStatus = STATUS_ACTIVE_UNINSTALLED;
        break;
      case STATUS_ACTIVEWAITSEEDING:
        newStatus = STATUS_ACTIVEWAIT;
        break;
      case STATUS_PAUSEDSEEDING:
        newStatus = STATUS_PAUSED;
        break;
      case STATUS_PAUSEDWAITSEEDING:
        newStatus = STATUS_PAUSEDWAIT;
        break;
      case STATUS_ABORTINGFORRESTARTSEEDING:
        newStatus = STATUS_ABORTINGFORRESTART;
        break;
      case STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL:
        newStatus = STATUS_ABORTINGFORRESTARTMINIMAL;
        break;
      default:
        throw new ManifoldCFException("Unexpected job status encountered: "+Integer.toString(status));
      }
      HashMap map = new HashMap();
      map.put(statusField,statusToString(newStatus));
      map.put(processIDField,null);
      map.put(seedingVersionField,seedVersionString);
      map.put(failTimeField,null);
      map.put(failCountField,null);
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (RuntimeException e)
    {
      signalRollback();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Cause job that is in "wait" state to become un-waited.
  *@param jobID is the job identifier.
  *@param newStatus is the new status (either STATUS_ACTIVE or STATUS_PAUSED or
  *  STATUS_ACTIVESEEDING or STATUS_PAUSEDSEEDING)
  *@param windowEnd is the window end time, if any.
  */
  public void unwaitJob(Long jobID, int newStatus, Long windowEnd)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    map.put(windowEndField,windowEnd);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Cause job that is in active or paused state to become waited.
  *@param jobID is the job identifier.
  *@param newStatus is the new status (either STATUS_ACTIVEWAIT or STATUS_PAUSEDWAIT or
  *  STATUS_ACTIVEWAITSEEDING or STATUS_PAUSEDWAITSEEDING)
  */
  public void waitJob(Long jobID, int newStatus)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    map.put(assessmentStateField,assessmentStateToString(ASSESSMENT_KNOWN));
    map.put(windowEndField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Abort a job.
  *@param jobID is the job id.
  *@param errorText is the error, or null if none.
  *@return true if there wasn't an abort already logged for this job.
  */
  public boolean abortJob(Long jobID, String errorText)
    throws ManifoldCFException
  {
    // Get the current job status
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+
      " WHERE "+query+" FOR UPDATE",list,null,null);
    if (set.getRowCount() == 0)
      throw new ManifoldCFException("Job does not exist: "+jobID);
    IResultRow row = set.getRow(0);
    int status = stringToStatus(row.getValue(statusField).toString());
    if (status == STATUS_ABORTING ||
      status == STATUS_ABORTINGSHUTTINGDOWN)
      return false;
    int newStatus;
    switch (status)
    {
    case STATUS_NOTIFYINGOFCOMPLETION:
      newStatus = STATUS_INACTIVE;
      break;
    case STATUS_STARTINGUP:
    case STATUS_ABORTINGSTARTINGUPFORRESTART:
      newStatus = STATUS_ABORTING;
      break;
    case STATUS_STARTINGUPMINIMAL:
    case STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL:
      newStatus = STATUS_ABORTING;
      break;
    case STATUS_SHUTTINGDOWN:
      newStatus = STATUS_ABORTINGSHUTTINGDOWN;
      break;
    case STATUS_READYFORSTARTUP:
    case STATUS_READYFORSTARTUPMINIMAL:
    case STATUS_ACTIVE:
    case STATUS_ACTIVE_UNINSTALLED:
    case STATUS_ACTIVEWAIT:
    case STATUS_PAUSING:
    case STATUS_ACTIVEWAITING:
    case STATUS_PAUSINGWAITING:
    case STATUS_PAUSED:
    case STATUS_PAUSEDWAIT:
    case STATUS_ABORTINGFORRESTART:
    case STATUS_ABORTINGFORRESTARTMINIMAL:
      newStatus = STATUS_ABORTING;
      break;
    case STATUS_ACTIVESEEDING:
    case STATUS_ACTIVESEEDING_UNINSTALLED:
    case STATUS_ACTIVEWAITSEEDING:
    case STATUS_PAUSINGSEEDING:
    case STATUS_ACTIVEWAITINGSEEDING:
    case STATUS_PAUSINGWAITINGSEEDING:
    case STATUS_PAUSEDSEEDING:
    case STATUS_PAUSEDWAITSEEDING:
    case STATUS_ABORTINGFORRESTARTSEEDING:
    case STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL:
      newStatus = STATUS_ABORTING;
      break;
    default:
      throw new ManifoldCFException("Job "+jobID+" is not active");
    }
    // Pause the job
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    map.put(errorField,errorText);
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
    return true;
  }

  /** Restart a job.  Finish off what's currently happening, and then start the job up again.
  *@param jobID is the job id.
  *@param requestMinimum is true if the minimal job run is requested.
  */
  public void abortRestartJob(Long jobID, boolean requestMinimum)
    throws ManifoldCFException
  {
    // Get the current job status
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+
      " WHERE "+query+" FOR UPDATE",list,null,null);
    if (set.getRowCount() == 0)
      throw new ManifoldCFException("Job does not exist: "+jobID);
    IResultRow row = set.getRow(0);
    int status = stringToStatus(row.getValue(statusField).toString());
    if (status == STATUS_ABORTINGFORRESTART || status == STATUS_ABORTINGFORRESTARTSEEDING ||
      status == STATUS_ABORTINGSTARTINGUPFORRESTART ||
      status == STATUS_ABORTINGFORRESTARTMINIMAL || status == STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL ||
      status == STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL)
      return;
    int newStatus;
    switch (status)
    {
    case STATUS_STARTINGUP:
    case STATUS_STARTINGUPMINIMAL:
      newStatus = requestMinimum?STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL:STATUS_ABORTINGSTARTINGUPFORRESTART;
      break;
    case STATUS_READYFORSTARTUP:
    case STATUS_READYFORSTARTUPMINIMAL:
    case STATUS_ACTIVE:
    case STATUS_ACTIVE_UNINSTALLED:
    case STATUS_ACTIVEWAIT:
    case STATUS_PAUSING:
    case STATUS_ACTIVEWAITING:
    case STATUS_PAUSINGWAITING:
    case STATUS_PAUSED:
    case STATUS_PAUSEDWAIT:
      newStatus = requestMinimum?STATUS_ABORTINGFORRESTARTMINIMAL:STATUS_ABORTINGFORRESTART;
      break;
    case STATUS_ACTIVESEEDING:
    case STATUS_ACTIVESEEDING_UNINSTALLED:
    case STATUS_ACTIVEWAITSEEDING:
    case STATUS_PAUSINGSEEDING:
    case STATUS_ACTIVEWAITINGSEEDING:
    case STATUS_PAUSINGWAITINGSEEDING:
    case STATUS_PAUSEDSEEDING:
    case STATUS_PAUSEDWAITSEEDING:
      newStatus = requestMinimum?STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL:STATUS_ABORTINGFORRESTARTSEEDING;
      break;
    default:
      throw new ManifoldCFException("Job "+jobID+" is not restartable");
    }
    // reset the job
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Pause a job.
  *@param jobID is the job id.
  */
  public void pauseJob(Long jobID)
    throws ManifoldCFException
  {
    // Get the current job status
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+
      " WHERE "+query+" FOR UPDATE",list,null,null);
    if (set.getRowCount() == 0)
      throw new ManifoldCFException("Job does not exist: "+jobID);
    IResultRow row = set.getRow(0);
    int status = stringToStatus(row.getValue(statusField).toString());
    int newStatus;
    switch (status)
    {
    case STATUS_ACTIVE:
    case STATUS_ACTIVE_UNINSTALLED:
      newStatus = STATUS_PAUSING;
      break;
    case STATUS_ACTIVEWAITING:
      newStatus = STATUS_PAUSINGWAITING;
      break;
    case STATUS_ACTIVEWAIT:
      newStatus = STATUS_PAUSEDWAIT;
      break;
    case STATUS_ACTIVESEEDING:
    case STATUS_ACTIVESEEDING_UNINSTALLED:
      newStatus = STATUS_PAUSINGSEEDING;
      break;
    case STATUS_ACTIVEWAITINGSEEDING:
      newStatus = STATUS_PAUSINGWAITINGSEEDING;
      break;
    case STATUS_ACTIVEWAITSEEDING:
      newStatus = STATUS_PAUSEDWAITSEEDING;
      break;
    default:
      throw new ManifoldCFException("Job "+jobID+" is not active");
    }
    // Pause the job
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Restart a job.
  *@param jobID is the job id.
  */
  public void restartJob(Long jobID)
    throws ManifoldCFException
  {
    // Get the current job status
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+
      " WHERE "+query+" FOR UPDATE",list,null,null);
    if (set.getRowCount() == 0)
      throw new ManifoldCFException("Job does not exist: "+jobID);
    IResultRow row = set.getRow(0);
    int status = stringToStatus(row.getValue(statusField).toString());
    int newStatus;
    switch (status)
    {
    case STATUS_PAUSED:
      newStatus = STATUS_RESUMING;
      break;
    case STATUS_PAUSEDWAIT:
      newStatus = STATUS_ACTIVEWAIT;
      break;
    case STATUS_PAUSEDSEEDING:
      newStatus = STATUS_RESUMINGSEEDING;
      break;
    case STATUS_PAUSEDWAITSEEDING:
      newStatus = STATUS_ACTIVEWAITSEEDING;
      break;
    default:
      throw new ManifoldCFException("Job "+jobID+" is not paused");
    }
    // Pause the job
    HashMap map = new HashMap();
    map.put(statusField,statusToString(newStatus));
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Update a job's status, and its reseed time.
  *@param jobID is the job id.
  *@param status is the desired status.
  *@param reseedTime is the reseed time.
  */
  public void writeTransientStatus(Long jobID, int status, Long reseedTime, String processID)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, reseedTime, processID, false);
  }

  /** Update a job's status, and its reseed time.
  *@param jobID is the job id.
  *@param status is the desired status.
  *@param reseedTime is the reseed time.
  */
  public void writePermanentStatus(Long jobID, int status, Long reseedTime)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, reseedTime, null, false);
  }

  /** Update a job's status, and its reseed time.
  *@param jobID is the job id.
  *@param status is the desired status.
  *@param reseedTime is the reseed time.
  */
  public void writePermanentStatus(Long jobID, int status, Long reseedTime, boolean clearFailTime)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, reseedTime, null, clearFailTime);
  }

  /** Update a job's status, and its reseed time.
  *@param jobID is the job id.
  *@param status is the desired status.
  *@param reseedTime is the reseed time.
  */
  protected void writeStatus(Long jobID, int status, Long reseedTime, String processID, boolean clearFailTime)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(status));
    map.put(processIDField,processID);
    map.put(reseedTimeField,reseedTime);
    if (clearFailTime)
    {
      map.put(failTimeField,null);
      map.put(failCountField,null);
    }
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Update a job's status.
  *@param jobID is the job id.
  *@param status is the desired status.
  */
  public void writeTransientStatus(Long jobID, int status, String processID)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, processID, false);
  }
  
  /** Update a job's status.
  *@param jobID is the job id.
  *@param status is the desired status.
  */
  public void writePermanentStatus(Long jobID, int status)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, null, false);
  }

  /** Update a job's status.
  *@param jobID is the job id.
  *@param status is the desired status.
  */
  public void writePermanentStatus(Long jobID, int status, boolean clearFailTime)
    throws ManifoldCFException
  {
    writeStatus(jobID, status, null, clearFailTime);
  }

  /** Update a job's status.
  *@param jobID is the job id.
  *@param status is the desired status.
  */
  protected void writeStatus(Long jobID, int status, String processID, boolean clearFailTime)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(status));
    map.put(processIDField,processID);
    if (clearFailTime)
    {
      map.put(failTimeField,null);
      map.put(failCountField,null);
    }
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Update a job's last-time field.
  *@param jobID is the job id.
  *@param currentTime is the current time.
  */
  public void updateLastTime(Long jobID, long currentTime)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(lastTimeField,new Long(currentTime));
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Finish a job.
  * Write completion and the current time.
  *@param jobID is the job id.
  *@param finishTime is the finish time.
  */
  public void finishJob(Long jobID, long finishTime)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
    map.put(errorField,null);
    map.put(endTimeField,new Long(finishTime));
    map.put(lastTimeField,new Long(finishTime));
    map.put(windowEndField,null);
    map.put(reseedTimeField,null);
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Finish job cleanup.
  * Write completion and the current time.
  *@param jobID is the job id.
  */
  public void finishJobCleanup(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_READYFORDELETENOTIFY));
    map.put(errorField,null);
    // Anything else?
    // MHL
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }

  /** Resume a stopped job (from a pause or activewait).
  * Updates the job record in a manner consistent with the job's state.
  */
  public void finishResumeJob(Long jobID, long currentTime)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      // Get the current job status
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+","+connectionNameField+" FROM "+getTableName()+
        " WHERE "+query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Job does not exist: "+jobID);
      IResultRow row = set.getRow(0);
      int status = stringToStatus(row.getValue(statusField).toString());
      String[] transformationConnectionNames = pipelineManager.getTransformationConnectionNames(jobID);
      String[] outputConnectionNames = pipelineManager.getOutputConnectionNames(jobID);
      String connectionName = (String)row.getValue(connectionNameField);
      int newStatus;
      HashMap map = new HashMap();
      switch (status)
      {
      case STATUS_RESUMING:
        // Want either STATUS_ACTIVE or STATUS_ACTIVE_UNINSTALLED
        if (!checkTransformationsInstalled(transformationConnectionNames) ||
          !checkOutputsInstalled(outputConnectionNames) ||
          !connectionMgr.checkConnectorExists(connectionName))
          newStatus = STATUS_ACTIVE_UNINSTALLED;
        else
          newStatus = STATUS_ACTIVE;
        map.put(statusField,statusToString(newStatus));
        break;
      case STATUS_RESUMINGSEEDING:
        // Want either STATUS_ACTIVESEEDING or STATUS_ACTIVESEEDING_UNINSTALLED
        if (!checkTransformationsInstalled(transformationConnectionNames) ||
          !checkOutputsInstalled(outputConnectionNames) ||
          !connectionMgr.checkConnectorExists(connectionName))
          newStatus = STATUS_ACTIVESEEDING_UNINSTALLED;
        else
          newStatus = STATUS_ACTIVESEEDING;
        map.put(statusField,statusToString(newStatus));
        break;
      default:
        throw new ManifoldCFException("Unexpected value for job status: "+Integer.toString(status));
      }
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
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
  
  /** Stop a job suddenly (abort, pause, activewait).
  * Updates the job record in a manner consistent with the job's state.
  */
  public void finishStopJob(Long jobID, long currentTime)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      // Get the current job status
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(idField,jobID)});
      IResultSet set = performQuery("SELECT "+statusField+" FROM "+getTableName()+
        " WHERE "+query+" FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Job does not exist: "+jobID);
      IResultRow row = set.getRow(0);
      int status = stringToStatus(row.getValue(statusField).toString());
      HashMap map = new HashMap();
      switch (status)
      {
      case STATUS_ABORTINGSHUTTINGDOWN:
      case STATUS_ABORTING:
        // Mark status of job as "inactive"
        map.put(statusField,statusToString(STATUS_READYFORNOTIFY));
        map.put(endTimeField,null);
        map.put(lastTimeField,new Long(currentTime));
        map.put(windowEndField,null);
        map.put(reseedTimeField,null);
        break;
      case STATUS_ABORTINGFORRESTART:
        map.put(statusField,statusToString(STATUS_READYFORSTARTUP));
        map.put(endTimeField,null);
        // Make sure error is removed (from last time)
        map.put(errorField,null);
        map.put(windowEndField,null);
        break;
      case STATUS_ABORTINGFORRESTARTMINIMAL:
        map.put(statusField,statusToString(STATUS_READYFORSTARTUPMINIMAL));
        map.put(endTimeField,null);
        // Make sure error is removed (from last time)
        map.put(errorField,null);
        map.put(windowEndField,null);
        break;
      case STATUS_PAUSING:
        map.put(statusField,statusToString(STATUS_PAUSED));
        break;
      case STATUS_PAUSINGSEEDING:
        map.put(statusField,statusToString(STATUS_PAUSEDSEEDING));
        break;
      case STATUS_ACTIVEWAITING:
        map.put(statusField,statusToString(STATUS_ACTIVEWAIT));
        break;
      case STATUS_ACTIVEWAITINGSEEDING:
        map.put(statusField,statusToString(STATUS_ACTIVEWAITSEEDING));
        break;
      case STATUS_PAUSINGWAITING:
        map.put(statusField,statusToString(STATUS_PAUSEDWAIT));
        break;
      case STATUS_PAUSINGWAITINGSEEDING:
        map.put(statusField,statusToString(STATUS_PAUSEDWAITSEEDING));
        break;
      default:
        throw new ManifoldCFException("Unexpected value for job status: "+Integer.toString(status));
      }
      performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
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
  
  /** Mark job as having properly notified the output connector of completion.
  *@param jobID is the job id.
  */
  public void notificationComplete(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(idField,jobID)});
    HashMap map = new HashMap();
    map.put(statusField,statusToString(STATUS_INACTIVE));
    map.put(processIDField,null);
    map.put(failTimeField,null);
    map.put(failCountField,null);
    // Leave everything else around from the abort/finish.
    performUpdate(map,"WHERE "+query,list,new StringSet(getJobStatusKey()));
  }
  
  /** See if there's a reference to a connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfReference(String connectionName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(connectionNameField,connectionName)});
    IResultSet set = performQuery("SELECT "+idField+" FROM "+getTableName()+
      " WHERE "+query,list,new StringSet(getJobsKey()),null);
    return set.getRowCount() > 0;
  }

  /** See if there's a reference to a notification connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfNotificationReference(String connectionName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(notificationManager.notificationNameField,connectionName)});
    IResultSet set = performQuery("SELECT "+notificationManager.ownerIDField+" FROM "+notificationManager.getTableName()+
      " WHERE "+query,list,new StringSet(getJobsKey()),null);
    return set.getRowCount() > 0;
  }

  /** See if there's a reference to an output connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfOutputReference(String connectionName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(pipelineManager.outputNameField,connectionName)});
    IResultSet set = performQuery("SELECT "+pipelineManager.ownerIDField+" FROM "+pipelineManager.getTableName()+
      " WHERE "+query,list,new StringSet(getJobsKey()),null);
    return set.getRowCount() > 0;
  }

  /** See if there's a reference to a transformation connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfTransformationReference(String connectionName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(pipelineManager.transformationNameField,connectionName)});
    IResultSet set = performQuery("SELECT "+pipelineManager.ownerIDField+" FROM "+pipelineManager.getTableName()+
      " WHERE "+query,list,new StringSet(getJobsKey()),null);
    return set.getRowCount() > 0;
  }

  /** Get the job IDs associated with a given connection name.
  *@param connectionName is the name of the connection.
  *@return the set of job id's associated with that connection.
  */
  public IJobDescription[] findJobsForConnection(String connectionName)
    throws ManifoldCFException
  {
    lockManager.enterReadLock(jobsLock);
    try
    {
      // Put together cache key
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getJobsKey());
      StringSet cacheKeys = new StringSet(ssb);
      ArrayList list = new ArrayList();
      String query = buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(connectionNameField,connectionName)});

      IResultSet set = performQuery("SELECT "+idField+","+descriptionField+" FROM "+
        getTableName()+" WHERE "+query+
        " ORDER BY "+descriptionField+" ASC",list,cacheKeys,null);

      // Convert to an array of id's, and then load them
      Long[] ids = new Long[set.getRowCount()];
      boolean[] readOnlies = new boolean[set.getRowCount()];
      for (int i = 0; i < ids.length; i++)
      {
        IResultRow row = set.getRow(i);
        ids[i] = (Long)row.getValue(idField);
        readOnlies[i] = true;
      }
      return loadMultiple(ids,readOnlies);
    }
    finally
    {
      lockManager.leaveReadLock(jobsLock);
    }

  }

  /** Return true if there is a job in the DELETING state.  (This matches the
  * conditions for values to be returned from
  * getNextDeletableDocuments).
  *@return true if such jobs exist.
  */
  public boolean deletingJobsPresent()
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_DELETING))});
    IResultSet set = performQuery("SELECT "+idField+" FROM "+getTableName()+" WHERE "+
      query+" "+constructOffsetLimitClause(0,1),
      list,new StringSet(getJobStatusKey()),null,1);
    return set.getRowCount() > 0;
  }

  /** Return true if there is a job in the
  * SHUTTINGDOWN state.  (This matches the conditions for values to be returned from
  * getNextCleanableDocuments).
  *@return true if such jobs exist.
  */
  public boolean cleaningJobsPresent()
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(statusField,statusToString(STATUS_SHUTTINGDOWN))});
    IResultSet set = performQuery("SELECT "+idField+" FROM "+getTableName()+" WHERE "+
      query+" "+constructOffsetLimitClause(0,1),
      list,new StringSet(getJobStatusKey()),null,1);
    return set.getRowCount() > 0;
  }


  /** Return true if there is a job in either the ACTIVE or the ACTIVESEEDING state.
  * (This matches the conditions for values to be returned from getNextDocuments).
  *@return true if such jobs exist.
  */
  public boolean activeJobsPresent()
    throws ManifoldCFException
  {
    // To improve the postgres CPU usage of the system at rest, we do a *fast* check to be
    // sure there are ANY jobs in an active state.
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(statusField,new Object[]{
        statusToString(STATUS_ACTIVE),
        statusToString(STATUS_ACTIVESEEDING)})});
    IResultSet set = performQuery("SELECT "+idField+" FROM "+getTableName()+" WHERE "+
      query+" "+constructOffsetLimitClause(0,1),list,new StringSet(getJobStatusKey()),null,1);
    return set.getRowCount() > 0;
  }

  // These functions map from status to a string and back

  /** Go from string to status.
  *@param value is the string.
  *@return the status value.
  */
  public static int stringToStatus(String value)
    throws ManifoldCFException
  {
    Integer x = (Integer)statusMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad status value: '"+value+"'");
    return x.intValue();
  }

  /** Go from status to string.
  *@param status is the status.
  *@return the string.
  */
  public static String statusToString(int status)
    throws ManifoldCFException
  {
    switch (status)
    {
    case STATUS_INACTIVE:
      return "N";
    case STATUS_ACTIVE:
      return "A";
    case STATUS_PAUSED:
      return "P";
    case STATUS_SHUTTINGDOWN:
      return "S";
    case STATUS_NOTIFYINGOFCOMPLETION:
      return "n";
    case STATUS_READYFORNOTIFY:
      return "s";
    case STATUS_NOTIFYINGOFDELETION:
      return "j";
    case STATUS_READYFORDELETENOTIFY:
      return "d";
    case STATUS_ACTIVEWAIT:
      return "W";
    case STATUS_PAUSEDWAIT:
      return "Z";
    case STATUS_ABORTING:
      return "X";
    case STATUS_ABORTINGFORRESTART:
      return "Y";
    case STATUS_ABORTINGFORRESTARTMINIMAL:
      return "M";
    case STATUS_STARTINGUP:
      return "B";
    case STATUS_STARTINGUPMINIMAL:
      return "b";
    case STATUS_ABORTINGSTARTINGUPFORRESTART:
      return "T";
    case STATUS_ABORTINGSTARTINGUPFORRESTARTMINIMAL:
      return "t";
    case STATUS_READYFORSTARTUP:
      return "C";
    case STATUS_READYFORSTARTUPMINIMAL:
      return "c";
    case STATUS_READYFORDELETE:
      return "E";
    case STATUS_DELETESTARTINGUP:
      return "V";
    case STATUS_DELETING:
      return "e";
    case STATUS_ACTIVESEEDING:
      return "a";
    case STATUS_PAUSEDSEEDING:
      return "p";
    case STATUS_ACTIVEWAITSEEDING:
      return "w";
    case STATUS_PAUSEDWAITSEEDING:
      return "z";
    case STATUS_ABORTINGFORRESTARTSEEDING:
      return "y";
    case STATUS_ABORTINGFORRESTARTSEEDINGMINIMAL:
      return "m";
    case STATUS_ACTIVE_UNINSTALLED:
      return "R";
    case STATUS_ACTIVESEEDING_UNINSTALLED:
      return "r";
    case STATUS_DELETING_NOOUTPUT:
      return "D";
    
    case STATUS_ACTIVEWAITING:
      return "H";
    case STATUS_ACTIVEWAITINGSEEDING:
      return "h";
    case STATUS_PAUSING:
      return "F";
    case STATUS_PAUSINGSEEDING:
      return "f";
    case STATUS_PAUSINGWAITING:
      return "G";
    case STATUS_PAUSINGWAITINGSEEDING:
      return "g";
    case STATUS_RESUMING:
      return "I";
    case STATUS_RESUMINGSEEDING:
      return "i";

    case STATUS_ABORTINGSHUTTINGDOWN:
      return "v";

    // These are deprecated
    case STATUS_ACTIVE_NOOUTPUT:
      return "O";
    case STATUS_ACTIVESEEDING_NOOUTPUT:
      return "o";
    case STATUS_ACTIVE_NEITHER:
      return "U";
    case STATUS_ACTIVESEEDING_NEITHER:
      return "u";

    default:
      throw new ManifoldCFException("Bad status value: "+Integer.toString(status));
    }
  }

  /** Go from string to type.
  *@param value is the string.
  *@return the type value.
  */
  public static int stringToType(String value)
    throws ManifoldCFException
  {
    Integer x = (Integer)typeMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad type value: '"+value+"'");
    return x.intValue();
  }

  /** Go from type to string.
  *@param type is the type.
  *@return the string.
  */
  public static String typeToString(int type)
    throws ManifoldCFException
  {
    switch (type)
    {
    case TYPE_CONTINUOUS:
      return "C";
    case TYPE_SPECIFIED:
      return "S";
    default:
      throw new ManifoldCFException("Bad type: "+Integer.toString(type));
    }
  }

  /** Go from string to assessment state.
  */
  public static int stringToAssessmentState(String value)
    throws ManifoldCFException
  {
    if (value == null || value.length() == 0)
      return ASSESSMENT_KNOWN;
    Integer x = assessmentMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad assessment value: '"+value+"'");
    return x.intValue();
  }
  
  /** Go from assessment state to string.
  */
  public static String assessmentStateToString(int value)
    throws ManifoldCFException
  {
    switch(value)
    {
    case ASSESSMENT_KNOWN:
      return "Y";
    case ASSESSMENT_UNKNOWN:
      return "N";
    default:
      throw new ManifoldCFException("Unknown assessment state value "+Integer.toString(value));
    }
  }

  /** Go from string to hopcount mode.
  */
  public static int stringToHopcountMode(String value)
    throws ManifoldCFException
  {
    if (value == null || value.length() == 0)
      return HOPCOUNT_ACCURATE;
    Integer x = hopmodeMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad hopcount mode value: '"+value+"'");
    return x.intValue();
  }

  /** Go from hopcount mode to string.
  */
  public static String hopcountModeToString(int value)
    throws ManifoldCFException
  {
    switch(value)
    {
    case HOPCOUNT_ACCURATE:
      return "A";
    case HOPCOUNT_NODELETE:
      return "N";
    case HOPCOUNT_NEVERDELETE:
      return "V";
    default:
      throw new ManifoldCFException("Unknown hopcount mode value "+Integer.toString(value));
    }
  }

  /** Go from string to start method.
  *@param value is the string.
  *@return the start method value.
  */
  public static int stringToStartMethod(String value)
    throws ManifoldCFException
  {
    Integer x = (Integer)startMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad start method value: '"+value+"'");
    return x.intValue();
  }

  /** Get from start method to string.
  *@param startMethod is the start method.
  *@return a string.
  */
  public static String startMethodToString(int startMethod)
    throws ManifoldCFException
  {
    switch(startMethod)
    {
    case START_WINDOWBEGIN:
      return "B";
    case START_WINDOWINSIDE:
      return "I";
    case START_DISABLE:
      return "D";
    default:
      throw new ManifoldCFException("Bad start method: "+Integer.toString(startMethod));
    }
  }


  /** Go from string to enumerated value.
  *@param value is the input.
  *@return the enumerated value.
  */
  public static EnumeratedValues stringToEnumeratedValue(String value)
    throws ManifoldCFException
  {
    if (value == null)
      return null;
    try
    {
      ArrayList valStore = new ArrayList();
      if (!value.equals("*"))
      {
        int curpos = 0;
        while (true)
        {
          int newpos = value.indexOf(",",curpos);
          if (newpos == -1)
          {
            valStore.add(new Integer(value.substring(curpos)));
            break;
          }
          valStore.add(new Integer(value.substring(curpos,newpos)));
          curpos = newpos+1;
        }
      }
      return new EnumeratedValues(valStore);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Bad number: '"+value+"'",e);
    }

  }

  /** Go from enumerated value to string.
  *@param values is the enumerated value.
  *@return the string value.
  */
  public static String enumeratedValueToString(EnumeratedValues values)
  {
    if (values == null)
      return null;
    if (values.size() == 0)
      return "*";
    StringBuilder rval = new StringBuilder();
    Iterator iter = values.getValues();
    boolean first = true;
    while (iter.hasNext())
    {
      if (first)
        first = false;
      else
        rval.append(',');
      rval.append(((Integer)iter.next()).toString());
    }
    return rval.toString();
  }

  // Cache key picture for jobs.  There is one global job cache key, and a cache key for each individual job (which is based on
  // id).

  protected static String getJobsKey()
  {
    return CacheKeyFactory.makeJobsKey();
  }

  protected static String getJobIDKey(Long jobID)
  {
    return CacheKeyFactory.makeJobIDKey(jobID.toString());
  }

  protected static String getJobStatusKey()
  {
    return CacheKeyFactory.makeJobStatusKey();
  }

  /** Get multiple jobs (without caching)
  *@param ids is the set of ids to get jobs for.
  *@return the corresponding job descriptions.
  */
  protected JobDescription[] getJobsMultiple(Long[] ids)
    throws ManifoldCFException
  {
    // Fetch all the jobs, but only once for each ID.  Then, assign each one by id into the final array.
    Set<Long> uniqueIDs = new HashSet<Long>();
    for (Long id : ids)
    {
      uniqueIDs.add(id);
    }
    Map<Long,JobDescription> returnValues = new HashMap<Long,JobDescription>();
    beginTransaction();
    try
    {
      StringBuilder sb = new StringBuilder();
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = getMaxInClause();
      for (Long uniqueID : uniqueIDs)
      {
        if (j == maxIn)
        {
          getJobsChunk(returnValues,sb.toString(),params);
          sb.setLength(0);
          params.clear();
          j = 0;
        }
        if (j > 0)
          sb.append(',');
        sb.append('?');
        params.add(uniqueID);
        j++;
      }
      if (j > 0)
        getJobsChunk(returnValues,sb.toString(),params);
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }

    // Build the return array
    JobDescription[] rval = new JobDescription[ids.length];
    for (int i = 0; i < rval.length; i++)
    {
      Long id = ids[i];
      JobDescription jd = returnValues.get(id);
      if (jd != null)
        jd.makeReadOnly();
      rval[i] = jd;
    }
    return rval;
  }

  /** Read a chunk of repository connections.
  *@param returnValues is keyed by id and contains a JobDescription value.
  *@param idList is the list of id's.
  *@param params is the set of parameters.
  */
  protected void getJobsChunk(Map<Long,JobDescription> returnValues, String idList, ArrayList params)
    throws ManifoldCFException
  {
    try
    {
      IResultSet set;
      set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
        idField+" IN ("+idList+")",params,null,null);
      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i++);
        Long id = (Long)row.getValue(idField);
        JobDescription rc = new JobDescription();
        rc.setID(id);
        rc.setIsNew(false);
        rc.setDescription(row.getValue(descriptionField).toString());
        rc.setConnectionName(row.getValue(connectionNameField).toString());
        rc.setType(stringToType(row.getValue(typeField).toString()));
        rc.setStartMethod(stringToStartMethod(row.getValue(startMethodField).toString()));
        rc.setHopcountMode(stringToHopcountMode((String)row.getValue(hopcountModeField)));
        rc.getSpecification().fromXML(row.getValue(documentSpecField).toString());

        Object x;

        rc.setInterval((Long)row.getValue(intervalField));
        rc.setMaxInterval((Long)row.getValue(maxIntervalField));
        rc.setReseedInterval((Long)row.getValue(reseedIntervalField));
        rc.setExpiration((Long)row.getValue(expirationField));

        rc.setPriority(Integer.parseInt(row.getValue(priorityField).toString()));
        returnValues.put(id,rc);
      }

      // Fill in schedules for jobs
      pipelineManager.getRows(returnValues,idList,params);
      notificationManager.getRows(returnValues,idList,params);
      scheduleManager.getRows(returnValues,idList,params);
      hopFilterManager.getRows(returnValues,idList,params);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Bad number: "+e.getMessage(),e);
    }
  }

  /** Job object description class.  This class describes an object in the cache.
  */
  protected static class JobObjectDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected Long jobID;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public JobObjectDescription(Long jobID, StringSet invKeys)
    {
      super("jobdescriptioncache");
      this.jobID = jobID;
      criticalSectionName = getClass().getName()+"-"+jobID.toString();
      cacheKeys = invKeys;
    }

    public Long getJobID()
    {
      return jobID;
    }

    public int hashCode()
    {
      return jobID.hashCode() ;
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof JobObjectDescription))
        return false;
      JobObjectDescription d = (JobObjectDescription)o;
      return d.jobID.equals(jobID);
    }

    public String getCriticalSectionName()
    {
      return criticalSectionName;
    }

    /** Get the cache keys for an object (which may or may not exist yet in
    * the cache).  This method is called in order for cache manager to throw the correct locks.
    * @return the object's cache keys, or null if the object should not
    * be cached.
    */
    public StringSet getObjectKeys()
    {
      return cacheKeys;
    }

  }
  /** This is the executor object for locating job objects.
  */
  protected static class JobObjectExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected Jobs thisManager;
    protected JobDescription[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the ToolManager.
    *@param objectDescriptions are the object descriptions.
    */
    public JobObjectExecutor(Jobs manager, JobObjectDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new JobDescription[objectDescriptions.length];
      int i = 0;
      while (i < objectDescriptions.length)
      {
        returnMap.put(objectDescriptions[i].getJobID(),new Integer(i));
        i++;
      }
    }

    /** Get the result.
    *@return the looked-up or read cached instances.
    */
    public JobDescription[] getResults(boolean[] readOnlies, int inputIndex)
    {
      JobDescription[] rval = new JobDescription[returnValues.length];
      for (int i = 0; i < rval.length; i++)
      {
        JobDescription jd = returnValues[i];
        if (jd != null)
          rval[i] = jd.duplicate(readOnlies[inputIndex + i]);
        else
          rval[i] = null;
      }
      return rval;
    }

    /** Create a set of new objects to operate on and cache.  This method is called only
    * if the specified object(s) are NOT available in the cache.  The specified objects
    * should be created and returned; if they are not created, it means that the
    * execution cannot proceed, and the execute() method will not be called.
    * @param objectDescriptions is the set of unique identifier of the object.
    * @return the newly created objects to cache, or null, if any object cannot be created.
    *  The order of the returned objects must correspond to the order of the object descriptinos.
    */
    public Object[] create(ICacheDescription[] objectDescriptions) throws ManifoldCFException
    {
      // Turn the object descriptions into the parameters for the ToolInstance requests
      Long[] ids = new Long[objectDescriptions.length];
      int i = 0;
      while (i < ids.length)
      {
        JobObjectDescription desc = (JobObjectDescription)objectDescriptions[i];
        ids[i] = desc.getJobID();
        i++;
      }

      return thisManager.getJobsMultiple(ids);
    }


    /** Notify the implementing class of the existence of a cached version of the
    * object.  The object is passed to this method so that the execute() method below
    * will have it available to operate on.  This method is also called for all objects
    * that are freshly created as well.
    * @param objectDescription is the unique identifier of the object.
    * @param cachedObject is the cached object.
    */
    public void exists(ICacheDescription objectDescription, Object cachedObject) throws ManifoldCFException
    {
      // Cast what came in as what it really is
      JobObjectDescription objectDesc = (JobObjectDescription)objectDescription;
      JobDescription ci = (JobDescription)cachedObject;

      // All objects stored in this cache are read-only; no need to duplicate them at this level.

      // In order to make the indexes line up, we need to use the hashtable built by
      // the constructor.
      returnValues[((Integer)returnMap.get(objectDesc.getJobID())).intValue()] = ci;
    }

    /** Perform the desired operation.  This method is called after either createGetObject()
    * or exists() is called for every requested object.
    */
    public void execute() throws ManifoldCFException
    {
      // Does nothing; we only want to fetch objects in this cacher.
    }


  }

}
