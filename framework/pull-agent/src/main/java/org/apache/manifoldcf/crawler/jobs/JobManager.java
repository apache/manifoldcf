/* $Id: JobManager.java 998576 2010-09-19 01:11:02Z kwright $ */

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
import java.util.*;
import java.util.regex.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

/** This is the main job manager.  It provides methods that support both job definition, and the threads that execute the jobs.
*/
public class JobManager implements IJobManager
{
  public static final String _rcsid = "@(#)$Id: JobManager.java 998576 2010-09-19 01:11:02Z kwright $";

  protected static final String hopLock = "_HOPLOCK_";

  // Member variables
  protected IDBInterface database;
  protected IOutputConnectionManager outputMgr;
  protected IRepositoryConnectionManager connectionMgr;
  protected ILockManager lockManager;
  protected IThreadContext threadContext;
  protected JobQueue jobQueue;
  protected Jobs jobs;
  protected HopCount hopCount;
  protected Carrydown carryDown;
  protected EventManager eventManager;


  protected static Random random = new Random();

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database.
  */
  public JobManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    this.database = database;
    this.threadContext = threadContext;
    jobs = new Jobs(threadContext,database);
    jobQueue = new JobQueue(threadContext,database);
    hopCount = new HopCount(threadContext,database);
    carryDown = new Carrydown(database);
    eventManager = new EventManager(database);
    outputMgr = OutputConnectionManagerFactory.make(threadContext);
    connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);
    lockManager = LockManagerFactory.make(threadContext);

  }

  /** Install.
  */
  public void install()
    throws ManifoldCFException
  {
    jobs.install(outputMgr.getTableName(),outputMgr.getConnectionNameColumn(),connectionMgr.getTableName(),connectionMgr.getConnectionNameColumn());
    jobQueue.install(jobs.getTableName(),jobs.idField);
    hopCount.install(jobs.getTableName(),jobs.idField);
    carryDown.install(jobs.getTableName(),jobs.idField);
    eventManager.install();
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    eventManager.deinstall();
    carryDown.deinstall();
    hopCount.deinstall();
    jobQueue.deinstall();
    jobs.deinstall();
  }

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException
  {
    // Write a version indicator
    ManifoldCF.writeDword(os,2);
    // Get the job list
    IJobDescription[] list = getAllJobs();
    // Write the number of authorities
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual repository connection info
    int i = 0;
    while (i < list.length)
    {
      IJobDescription job = list[i++];
      ManifoldCF.writeString(os,job.getConnectionName());
      ManifoldCF.writeString(os,job.getOutputConnectionName());
      ManifoldCF.writeString(os,job.getDescription());
      ManifoldCF.writeDword(os,job.getType());
      ManifoldCF.writeDword(os,job.getStartMethod());
      ManifoldCF.writeLong(os,job.getInterval());
      ManifoldCF.writeLong(os,job.getExpiration());
      ManifoldCF.writeLong(os,job.getReseedInterval());
      ManifoldCF.writeDword(os,job.getPriority());
      ManifoldCF.writeDword(os,job.getHopcountMode());
      ManifoldCF.writeString(os,job.getSpecification().toXML());
      ManifoldCF.writeString(os,job.getOutputSpecification().toXML());

      // Write schedule
      int recCount = job.getScheduleRecordCount();
      ManifoldCF.writeDword(os,recCount);
      int j = 0;
      while (j < recCount)
      {
        ScheduleRecord sr = job.getScheduleRecord(j++);
        writeEnumeratedValues(os,sr.getDayOfWeek());
        writeEnumeratedValues(os,sr.getMonthOfYear());
        writeEnumeratedValues(os,sr.getDayOfMonth());
        writeEnumeratedValues(os,sr.getYear());
        writeEnumeratedValues(os,sr.getHourOfDay());
        writeEnumeratedValues(os,sr.getMinutesOfHour());
        ManifoldCF.writeString(os,sr.getTimezone());
        ManifoldCF.writeLong(os,sr.getDuration());
      }

      // Write hop count filters
      Map filters = job.getHopCountFilters();
      ManifoldCF.writeDword(os,filters.size());
      Iterator iter = filters.keySet().iterator();
      while (iter.hasNext())
      {
        String linkType = (String)iter.next();
        Long hopcount = (Long)filters.get(linkType);
        ManifoldCF.writeString(os,linkType);
        ManifoldCF.writeLong(os,hopcount);
      }
    }
  }

  protected static void writeEnumeratedValues(java.io.OutputStream os, EnumeratedValues ev)
    throws java.io.IOException
  {
    if (ev == null)
    {
      ManifoldCF.writeSdword(os,-1);
      return;
    }
    int size = ev.size();
    ManifoldCF.writeSdword(os,size);
    Iterator iter = ev.getValues();
    while (iter.hasNext())
    {
      ManifoldCF.writeDword(os,((Integer)iter.next()).intValue());
    }
  }

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    int version = ManifoldCF.readDword(is);
    if (version != 2)
      throw new java.io.IOException("Unknown job configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      IJobDescription job = createJob();

      job.setConnectionName(ManifoldCF.readString(is));
      job.setOutputConnectionName(ManifoldCF.readString(is));
      job.setDescription(ManifoldCF.readString(is));
      job.setType(ManifoldCF.readDword(is));
      job.setStartMethod(ManifoldCF.readDword(is));
      job.setInterval(ManifoldCF.readLong(is));
      job.setExpiration(ManifoldCF.readLong(is));
      job.setReseedInterval(ManifoldCF.readLong(is));
      job.setPriority(ManifoldCF.readDword(is));
      job.setHopcountMode(ManifoldCF.readDword(is));
      job.getSpecification().fromXML(ManifoldCF.readString(is));
      job.getOutputSpecification().fromXML(ManifoldCF.readString(is));

      // Read schedule
      int recCount = ManifoldCF.readDword(is);
      int j = 0;
      while (j < recCount)
      {
        EnumeratedValues dayOfWeek = readEnumeratedValues(is);
        EnumeratedValues monthOfYear = readEnumeratedValues(is);
        EnumeratedValues dayOfMonth = readEnumeratedValues(is);
        EnumeratedValues year = readEnumeratedValues(is);
        EnumeratedValues hourOfDay = readEnumeratedValues(is);
        EnumeratedValues minutesOfHour = readEnumeratedValues(is);
        String timezone = ManifoldCF.readString(is);
        Long duration = ManifoldCF.readLong(is);

        ScheduleRecord sr = new ScheduleRecord(dayOfWeek, monthOfYear, dayOfMonth, year,
          hourOfDay, minutesOfHour, timezone, duration);
        job.addScheduleRecord(sr);
        j++;
      }

      // Read hop count filters
      int hopFilterCount = ManifoldCF.readDword(is);
      j = 0;
      while (j < hopFilterCount)
      {
        String linkType = ManifoldCF.readString(is);
        Long hopcount = ManifoldCF.readLong(is);
        job.addHopCountFilter(linkType,hopcount);
        j++;
      }

      // Attempt to save this job
      save(job);
      i++;
    }
  }

  protected EnumeratedValues readEnumeratedValues(java.io.InputStream is)
    throws java.io.IOException
  {
    int size = ManifoldCF.readSdword(is);
    if (size == -1)
      return null;
    int[] values = new int[size];
    int i = 0;
    while (i < size)
    {
      values[i++] = ManifoldCF.readDword(is);
    }
    return new EnumeratedValues(values);
  }

  /**  Note the deregistration of a connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException
  {
    // For each connection, find the corresponding list of jobs.  From these jobs, we want the job id and the status.
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = database.getMaxInClause();
    int currentCount = 0;
    int i = 0;
    while (i < connectionNames.length)
    {
      if (currentCount == maxCount)
      {
        noteConnectionDeregistration(sb.toString(),list);
        sb.setLength(0);
        list.clear();
        currentCount = 0;
      }

      if (currentCount > 0)
        sb.append(",");
      sb.append("?");
      list.add(connectionNames[i++]);
      currentCount++;
    }
    if (currentCount > 0)
      noteConnectionDeregistration(sb.toString(),list);
  }

  /** Note deregistration for a batch of connection names.
  */
  protected void noteConnectionDeregistration(String query, ArrayList list)
    throws ManifoldCFException
  {
    //System.out.println("Query is "+query);
    // Query for the matching jobs, and then for each job potentially adjust the state
    IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.statusField+" FROM "+
      jobs.getTableName()+" WHERE "+jobs.connectionNameField+" IN ("+query+") FOR UPDATE",
      list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long jobID = (Long)row.getValue(jobs.idField);
      int statusValue = jobs.stringToStatus((String)row.getValue(jobs.statusField));
      jobs.noteConnectorDeregistration(jobID,statusValue);
    }
  }

  /** Note the registration of a connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException
  {
    // For each connection, find the corresponding list of jobs.  From these jobs, we want the job id and the status.
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = database.getMaxInClause();
    int currentCount = 0;
    int i = 0;
    while (i < connectionNames.length)
    {
      if (currentCount == maxCount)
      {
        noteConnectionRegistration(sb.toString(),list);
        sb.setLength(0);
        list.clear();
        currentCount = 0;
      }

      if (currentCount > 0)
        sb.append(",");
      sb.append("?");
      list.add(connectionNames[i++]);
      currentCount++;
    }
    if (currentCount > 0)
      noteConnectionRegistration(sb.toString(),list);
  }

  /** Note registration for a batch of connection names.
  */
  protected void noteConnectionRegistration(String query, ArrayList list)
    throws ManifoldCFException
  {
    // Query for the matching jobs, and then for each job potentially adjust the state
    IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.statusField+" FROM "+
      jobs.getTableName()+" WHERE "+jobs.connectionNameField+" IN ("+query+") FOR UPDATE",
      list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long jobID = (Long)row.getValue(jobs.idField);
      int statusValue = jobs.stringToStatus((String)row.getValue(jobs.statusField));
      jobs.noteConnectorRegistration(jobID,statusValue);
    }
  }

  /** Note a change in connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external repository change
  * is signalled.
  */
  public void noteConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    jobs.noteConnectionChange(connectionName);
  }

  /**  Note the deregistration of an output connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteOutputConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException
  {
    // For each connection, find the corresponding list of jobs.  From these jobs, we want the job id and the status.
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = database.getMaxInClause();
    int currentCount = 0;
    int i = 0;
    while (i < connectionNames.length)
    {
      if (currentCount == maxCount)
      {
        noteOutputConnectionDeregistration(sb.toString(),list);
        sb.setLength(0);
        list.clear();
        currentCount = 0;
      }

      if (currentCount > 0)
        sb.append(",");
      sb.append("?");
      list.add(connectionNames[i++]);
      currentCount++;
    }
    if (currentCount > 0)
      noteOutputConnectionDeregistration(sb.toString(),list);
  }

  /** Note deregistration for a batch of output connection names.
  */
  protected void noteOutputConnectionDeregistration(String query, ArrayList list)
    throws ManifoldCFException
  {
    //System.out.println("Query is "+query);
    // Query for the matching jobs, and then for each job potentially adjust the state
    IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.statusField+" FROM "+
      jobs.getTableName()+" WHERE "+jobs.outputNameField+" IN ("+query+") FOR UPDATE",
      list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long jobID = (Long)row.getValue(jobs.idField);
      int statusValue = jobs.stringToStatus((String)row.getValue(jobs.statusField));
      jobs.noteOutputConnectorDeregistration(jobID,statusValue);
    }
  }

  /** Note the registration of an output connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteOutputConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException
  {
    // For each connection, find the corresponding list of jobs.  From these jobs, we want the job id and the status.
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = database.getMaxInClause();
    int currentCount = 0;
    int i = 0;
    while (i < connectionNames.length)
    {
      if (currentCount == maxCount)
      {
        noteOutputConnectionRegistration(sb.toString(),list);
        sb.setLength(0);
        list.clear();
        currentCount = 0;
      }

      if (currentCount > 0)
        sb.append(",");
      sb.append("?");
      list.add(connectionNames[i++]);
      currentCount++;
    }
    if (currentCount > 0)
      noteOutputConnectionRegistration(sb.toString(),list);
  }

  /** Note registration for a batch of output connection names.
  */
  protected void noteOutputConnectionRegistration(String query, ArrayList list)
    throws ManifoldCFException
  {
    // Query for the matching jobs, and then for each job potentially adjust the state
    IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.statusField+" FROM "+
      jobs.getTableName()+" WHERE "+jobs.outputNameField+" IN ("+query+") FOR UPDATE",
      list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long jobID = (Long)row.getValue(jobs.idField);
      int statusValue = jobs.stringToStatus((String)row.getValue(jobs.statusField));
      jobs.noteOutputConnectorRegistration(jobID,statusValue);
    }
  }

  /** Note a change in output connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external target config change
  * is signalled.
  */
  public void noteOutputConnectionChange(String connectionName)
    throws ManifoldCFException
  {
    jobs.noteOutputConnectionChange(connectionName);
  }

  /** Load a sorted list of job descriptions.
  *@return the list, sorted by description.
  */
  public IJobDescription[] getAllJobs()
    throws ManifoldCFException
  {
    return jobs.getAll();
  }

  /** Create a new job.
  *@return the new job.
  */
  public IJobDescription createJob()
    throws ManifoldCFException
  {
    return jobs.create();
  }

  /** Get the hoplock for a given job ID */
  protected String getHopLockName(Long jobID)
  {
    return hopLock + jobID;
  }



  /** Delete a job.
  *@param id is the job's identifier.  This method will purge all the records belonging to the job from the database, as
  * well as remove all documents indexed by the job from the index.
  */
  public void deleteJob(Long id)
    throws ManifoldCFException
  {
    database.beginTransaction();
    try
    {
      // If the job is running, throw an error
      ArrayList list = new ArrayList();
      list.add(id);
      IResultSet set = database.performQuery("SELECT "+jobs.statusField+","+jobs.outputNameField+" FROM "+
        jobs.getTableName()+" WHERE "+jobs.idField+"=? FOR UPDATE",list,null,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("Attempting to delete a job that doesn't exist: "+id);
      IResultRow row = set.getRow(0);
      String outputName = (String)row.getValue(jobs.outputNameField);
      int status = jobs.stringToStatus(row.getValue(jobs.statusField).toString());
      if (status == jobs.STATUS_ACTIVE || status == jobs.STATUS_ACTIVESEEDING ||
        status == jobs.STATUS_ACTIVE_UNINSTALLED || status == jobs.STATUS_ACTIVESEEDING_UNINSTALLED ||
        status == jobs.STATUS_ACTIVE_NOOUTPUT || status == jobs.STATUS_ACTIVESEEDING_NOOUTPUT ||
        status == jobs.STATUS_ACTIVE_NEITHER || status == jobs.STATUS_ACTIVESEEDING_NEITHER)
      throw new ManifoldCFException("Job "+id+" is active; you must shut it down before deleting it");
      if (status != jobs.STATUS_INACTIVE)
        throw new ManifoldCFException("Job "+id+" is busy; you must wait and/or shut it down before deleting it");
      if (outputMgr.checkConnectorExists(outputName))
        jobs.writeStatus(id,jobs.STATUS_READYFORDELETE);
      else
        jobs.writeStatus(id,jobs.STATUS_READYFORDELETE_NOOUTPUT);
      if (Logging.jobs.isDebugEnabled())
        Logging.jobs.debug("Job "+id+" marked for deletion");
    }
    catch (ManifoldCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }

  }

  /** Load a job for editing.
  *@param id is the job's identifier.
  *@return null if the job doesn't exist.
  */
  public IJobDescription load(Long id)
    throws ManifoldCFException
  {
    return jobs.load(id,false);
  }

  /** Load a job.
  *@param id is the job's identifier.
  *@param readOnly is true if a read-only object is desired.
  *@return null if the job doesn't exist.
  */
  public IJobDescription load(Long id, boolean readOnly)
    throws ManifoldCFException
  {
    return jobs.load(id,readOnly);
  }

  /** Save a job.
  *@param jobDescription is the job description.
  */
  public void save(IJobDescription jobDescription)
    throws ManifoldCFException
  {
    ManifoldCF.noteConfigurationChange();
    jobs.save(jobDescription);
  }

  /** See if there's a reference to a connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfReference(String connectionName)
    throws ManifoldCFException
  {
    return jobs.checkIfReference(connectionName);
  }

  /** See if there's a reference to an output connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfOutputReference(String connectionName)
    throws ManifoldCFException
  {
    return jobs.checkIfOutputReference(connectionName);
  }

  /** Get the job IDs associated with a given connection name.
  *@param connectionName is the name of the connection.
  *@return the set of job id's associated with that connection.
  */
  public IJobDescription[] findJobsForConnection(String connectionName)
    throws ManifoldCFException
  {
    return jobs.findJobsForConnection(connectionName);
  }

  // These methods cover activities that require interaction with the job queue.
  // The job queue is maintained underneath this interface, and all threads that perform
  // job activities need to go through this layer.

  /** Reset the job queue immediately after starting up.
  * If the system was shut down in the middle of a job, sufficient information should
  * be around in the database to allow it to restart.  However, BEFORE all the job threads
  * are spun up, there needs to be a pass over the queue to bring things back to a "normal"
  * state.
  * Also, if a job's status is in a state that indicates it was being processed by a thread
  * (which is now dead), then we have to set that status back to previous value.
  */
  public void prepareForStart()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting due to restart");
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Clean up events
        eventManager.restart();
        // Clean up job queue
        jobQueue.restart();
        // Clean up jobs
        jobs.restart();
        // Clean up hopcount stuff
        hopCount.reset();
        // Clean up carrydown stuff
        carryDown.reset();
        Logging.jobs.debug("Reset complete");
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction resetting for restart: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset as part of restoring document worker threads.
  */
  public void resetDocumentWorkerStatus()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting document active status");
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        jobQueue.resetDocumentWorkerStatus();
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction resetting document active status: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
    Logging.jobs.debug("Reset complete");
  }

  /** Reset as part of restoring seeding threads.
  */
  public void resetSeedingWorkerStatus()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting seeding status");
    jobs.resetSeedingWorkerStatus();
    Logging.jobs.debug("Reset complete");
  }

  /** Reset as part of restoring doc delete threads.
  */
  public void resetDocDeleteWorkerStatus()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting doc deleting status");
    jobQueue.resetDocDeleteWorkerStatus();
    Logging.jobs.debug("Reset complete");
  }

  /** Reset as part of restoring doc cleanup threads.
  */
  public void resetDocCleanupWorkerStatus()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting doc cleaning status");
    jobQueue.resetDocCleanupWorkerStatus();
    Logging.jobs.debug("Reset complete");
  }

  /** Reset as part of restoring startup threads.
  */
  public void resetStartupWorkerStatus()
    throws ManifoldCFException
  {
    Logging.jobs.debug("Resetting job starting up status");
    jobs.resetStartupWorkerStatus();
    Logging.jobs.debug("Reset complete");
  }

  // These methods support job delete threads

  /** Delete ingested document identifiers (as part of deleting the owning job).
  * The number of identifiers specified is guaranteed to be less than the maxInClauseCount
  * for the database.
  *@param identifiers is the set of document identifiers.
  */
  public void deleteIngestedDocumentIdentifiers(DocumentDescription[] identifiers)
    throws ManifoldCFException
  {
    jobQueue.deleteIngestedDocumentIdentifiers(identifiers);
    // Hopcount rows get removed when the job itself is removed.
    // carrydown records get removed when the job itself is removed.
  }

  /** Get list of cleanable document descriptions.  This list will take into account
  * multiple jobs that may own the same document.  All documents for which a description
  * is returned will be transitioned to the "beingcleaned" state.  Documents which are
  * not in transition and are eligible, but are owned by other jobs, will have their
  * jobqueue entries deleted by this method.
  *@param maxCount is the maximum number of documents to return.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@return the document descriptions for these documents.
  */
  public DocumentSetAndFlags getNextCleanableDocuments(int maxCount, long currentTime)
    throws ManifoldCFException
  {
    // The query will be built here, because it joins the jobs table against the jobqueue
    // table.
    //
    // This query must only pick up documents that are not active in any job and
    // which belong to a job that's in a "shutting down" state and are in
    // a "purgatory" state.
    //
    // We are in fact more conservative in this query than we need to be; the documents
    // excluded will include some that simply match our criteria, which is designed to
    // be fast rather than perfect.  The match we make is: hashvalue against hashvalue, and
    // different job id's.
    //
    // SELECT id,jobid,docid FROM jobqueue t0 WHERE t0.status='P' AND EXISTS(SELECT 'x' FROM
    //              jobs t3 WHERE t0.jobid=t3.id AND t3.status='X')
    //      AND NOT EXISTS(SELECT 'x' FROM jobqueue t2 WHERE t0.hashval=t2.hashval AND t0.jobid!=t2.jobid
    //              AND t2.status IN ('A','F','B'))
    //

    // Do a simple preliminary query, since the big query is currently slow, so that we don't waste time during stasis or
    // ingestion.
    // Moved outside of transaction, so we have no chance of locking up job status cache key for an extended period of time.
    if (!jobs.cleaningJobsPresent())
      return new DocumentSetAndFlags(new DocumentDescription[0],new boolean[0]);

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to find documents to put on the cleaning queue");
    }

    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("After "+new Long(System.currentTimeMillis()-startTime).toString()+" ms, beginning query to look for documents to put on cleaning queue");

        // Note: This query does not do "FOR UPDATE", because it is running under the only thread that can possibly change the document's state to "being cleaned".
        ArrayList list = new ArrayList();
        list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
        
        list.add(new Long(currentTime));

        list.add(jobs.statusToString(jobs.STATUS_SHUTTINGDOWN));
        
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
        
        // The checktime is null field check is for backwards compatibility
        IResultSet set = database.performQuery("SELECT "+jobQueue.idField+","+jobQueue.jobIDField+","+jobQueue.docHashField+","+jobQueue.docIDField+","+
          jobQueue.failTimeField+","+jobQueue.failCountField+" FROM "+
          jobQueue.getTableName()+" t0 WHERE t0."+jobQueue.statusField+"=? "+
          " AND (t0."+jobQueue.checkTimeField+" IS NULL OR t0."+jobQueue.checkTimeField+"<=?) "+
          " AND EXISTS(SELECT 'x' FROM "+jobs.getTableName()+" t1 WHERE t0."+jobQueue.jobIDField+"=t1."+jobs.idField+
          " AND t1."+jobs.statusField+"=?"+
          ") AND NOT EXISTS(SELECT 'x' FROM "+jobQueue.getTableName()+" t2 WHERE t0."+jobQueue.docHashField+"=t2."+
          jobQueue.docHashField+" AND t0."+jobQueue.jobIDField+"!=t2."+jobQueue.jobIDField+
          " AND t2."+jobQueue.statusField+" IN (?,?,?,?,?,?)) "+database.constructOffsetLimitClause(0,maxCount),
          list,null,null,maxCount,null);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Done getting docs to cleaning queue after "+new Long(System.currentTimeMillis()-startTime).toString()+" ms.");

        // We need to organize the returned set by connection name and output connection name, so that we can efficiently
        // use  getUnindexableDocumentIdentifiers.
        // This is a table keyed by connection name and containing an ArrayList, which in turn contains DocumentDescription
        // objects.
        HashMap connectionNameMap = new HashMap();
        HashMap documentIDMap = new HashMap();
        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i);
          Long jobID = (Long)row.getValue(jobQueue.jobIDField);
          String documentIDHash = (String)row.getValue(jobQueue.docHashField);
          String documentID = (String)row.getValue(jobQueue.docIDField);
          Long failTimeValue = (Long)row.getValue(jobQueue.failTimeField);
          Long failCountValue = (Long)row.getValue(jobQueue.failCountField);
          // Failtime is probably not useful in this context, but we'll bring it along for completeness
          long failTime;
          if (failTimeValue == null)
            failTime = -1L;
          else
            failTime = failTimeValue.longValue();
          int failCount;
          if (failCountValue == null)
            failCount = 0;
          else
            failCount = (int)failCountValue.longValue();
          IJobDescription jobDesc = load(jobID);
          String connectionName = jobDesc.getConnectionName();
          String outputConnectionName = jobDesc.getOutputConnectionName();
          DocumentDescription dd = new DocumentDescription((Long)row.getValue(jobQueue.idField),
            jobID,documentIDHash,documentID,failTime,failCount);
          String compositeDocumentID = makeCompositeID(documentIDHash,connectionName);
          documentIDMap.put(compositeDocumentID,dd);
          Map y = (Map)connectionNameMap.get(connectionName);
          if (y == null)
          {
            y = new HashMap();
            connectionNameMap.put(connectionName,y);
          }
          ArrayList x = (ArrayList)y.get(outputConnectionName);
          if (x == null)
          {
            // New entry needed
            x = new ArrayList();
            y.put(outputConnectionName,x);
          }
          x.add(dd);
          i++;
        }

        // For each bin, obtain a filtered answer, and enter all answers into a hash table.
        // We'll then scan the result again to look up the right descriptions for return,
        // and delete the ones that are owned multiply.
        HashMap allowedDocIds = new HashMap();
        Iterator iter = connectionNameMap.keySet().iterator();
        while (iter.hasNext())
        {
          String connectionName = (String)iter.next();
          Map y = (Map)connectionNameMap.get(connectionName);
          Iterator outputIter = y.keySet().iterator();
          while (outputIter.hasNext())
          {
            String outputConnectionName = (String)outputIter.next();
            ArrayList x = (ArrayList)y.get(outputConnectionName);
            // Do the filter query
            DocumentDescription[] descriptions = new DocumentDescription[x.size()];
            int j = 0;
            while (j < descriptions.length)
            {
              descriptions[j] = (DocumentDescription)x.get(j);
              j++;
            }
            String[] docIDHashes = getUnindexableDocumentIdentifiers(descriptions,connectionName,outputConnectionName);
            j = 0;
            while (j < docIDHashes.length)
            {
              String docIDHash = docIDHashes[j++];
              String key = makeCompositeID(docIDHash,connectionName);
              allowedDocIds.put(key,docIDHash);
            }
          }
        }

        // Now, assemble a result, and change the state of the records accordingly
        // First thing to do is order by document hash, so we reduce the risk of deadlock.
        String[] compositeIDArray = new String[documentIDMap.size()];
        i = 0;
        iter = documentIDMap.keySet().iterator();
        while (iter.hasNext())
        {
          compositeIDArray[i++] = (String)iter.next();
        }
        
        java.util.Arrays.sort(compositeIDArray);
        
        DocumentDescription[] rval = new DocumentDescription[documentIDMap.size()];
        boolean[] rvalBoolean = new boolean[documentIDMap.size()];
        i = 0;
        while (i < compositeIDArray.length)
        {
          String compositeDocID = compositeIDArray[i];
          DocumentDescription dd = (DocumentDescription)documentIDMap.get(compositeDocID);
          // Determine whether we can delete it from the index or not
          rvalBoolean[i] = (allowedDocIds.get(compositeDocID) != null);
          // Set the record status to "being cleaned" and return it
          rval[i++] = dd;
          jobQueue.setCleaningStatus(dd.getID());
        }

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Done pruning unindexable docs after "+new Long(System.currentTimeMillis()-startTime).toString()+" ms.");

        return new DocumentSetAndFlags(rval,rvalBoolean);

      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction finding deleteable docs: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Create a composite document hash key.  This consists of the document id hash plus the
  * connection name.
  */
  protected static String makeCompositeID(String docIDHash, String connectionName)
  {
    return docIDHash + ":" + connectionName;
  }
  
  /** Get list of deletable document descriptions.  This list will take into account
  * multiple jobs that may own the same document.  All documents for which a description
  * is returned will be transitioned to the "beingdeleted" state.  Documents which are
  * not in transition and are eligible, but are owned by other jobs, will have their
  * jobqueue entries deleted by this method.
  *@param maxCount is the maximum number of documents to return.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@return the document descriptions for these documents.
  */
  public DocumentDescription[] getNextDeletableDocuments(int maxCount, long currentTime)
    throws ManifoldCFException
  {
    // The query will be built here, because it joins the jobs table against the jobqueue
    // table.
    //
    // This query must only pick up documents that are not active in any job and
    // which either belong to a job that's in a "delete pending" state and are in
    // a "complete", "purgatory", or "pendingpurgatory" state, OR belong to a job
    // that's in a "shutting down" state and are in the "purgatory" state.
    //
    // We are in fact more conservative in this query than we need to be; the documents
    // excluded will include some that simply match our criteria, which is designed to
    // be fast rather than perfect.  The match we make is: hashvalue against hashvalue, and
    // different job id's.
    //
    // SELECT id,jobid,docid FROM jobqueue t0 WHERE (t0.status IN ('C','P','G') AND EXISTS(SELECT 'x' FROM
    //      jobs t1 WHERE t0.jobid=t1.id AND t1.status='D')
    //      AND NOT EXISTS(SELECT 'x' FROM jobqueue t2 WHERE t0.hashval=t2.hashval AND t0.jobid!=t2.jobid
    //              AND t2.status IN ('A','F','B'))
    //

    // Do a simple preliminary query, since the big query is currently slow, so that we don't waste time during stasis or
    // ingestion.
    // Moved outside of transaction, so we have no chance of locking up job status cache key for an extended period of time.
    if (!jobs.deletingJobsPresent())
      return new DocumentDescription[0];

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to find documents to put on the delete queue");
    }

    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("After "+new Long(System.currentTimeMillis()-startTime).toString()+" ms, beginning query to look for documents to put on delete queue");

        // Note: This query does not do "FOR UPDATE", because it is running under the only thread that can possibly change the document's state to "being deleted".
        // If FOR UPDATE was included, deadlock happened a lot.
        ArrayList list = new ArrayList();
        list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        
        list.add(new Long(currentTime));
        
        list.add(jobs.statusToString(jobs.STATUS_READYFORDELETE));
        
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
        
        // The checktime is null field check is for backwards compatibility
        IResultSet set = database.performQuery("SELECT "+jobQueue.idField+","+jobQueue.jobIDField+","+jobQueue.docHashField+","+jobQueue.docIDField+","+
          jobQueue.failTimeField+","+jobQueue.failCountField+" FROM "+
          jobQueue.getTableName()+" t0 WHERE t0."+jobQueue.statusField+" IN (?,?,?) "+
          " AND (t0."+jobQueue.checkTimeField+" IS NULL OR t0."+jobQueue.checkTimeField+"<=?) "+
          " AND EXISTS(SELECT 'x' FROM "+jobs.getTableName()+" t1 WHERE t0."+jobQueue.jobIDField+"=t1."+jobs.idField+
          " AND t1."+jobs.statusField+"=?"+
          ") AND NOT EXISTS(SELECT 'x' FROM "+jobQueue.getTableName()+" t2 WHERE t0."+jobQueue.docHashField+"=t2."+
          jobQueue.docHashField+" AND t0."+jobQueue.jobIDField+"!=t2."+jobQueue.jobIDField+
          " AND t2."+jobQueue.statusField+" IN (?,?,?,?,?,?)) "+database.constructOffsetLimitClause(0,maxCount),
          list,null,null,maxCount,null);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Done getting docs to delete queue after "+new Long(System.currentTimeMillis()-startTime).toString()+" ms.");

        // We need to organize the returned set by connection name, so that we can efficiently
        // use  getUnindexableDocumentIdentifiers.
        // This is a table keyed by connection name and containing an ArrayList, which in turn contains DocumentDescription
        // objects.
        HashMap connectionNameMap = new HashMap();
        HashMap documentIDMap = new HashMap();
        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i);
          Long jobID = (Long)row.getValue(jobQueue.jobIDField);
          String documentIDHash = (String)row.getValue(jobQueue.docHashField);
          String documentID = (String)row.getValue(jobQueue.docIDField);
          Long failTimeValue = (Long)row.getValue(jobQueue.failTimeField);
          Long failCountValue = (Long)row.getValue(jobQueue.failCountField);
          // Failtime is probably not useful in this context, but we'll bring it along for completeness
          long failTime;
          if (failTimeValue == null)
            failTime = -1L;
          else
            failTime = failTimeValue.longValue();
          int failCount;
          if (failCountValue == null)
            failCount = 0;
          else
            failCount = (int)failCountValue.longValue();
          IJobDescription jobDesc = load(jobID);
          String connectionName = jobDesc.getConnectionName();
          String outputConnectionName = jobDesc.getOutputConnectionName();
          DocumentDescription dd = new DocumentDescription((Long)row.getValue(jobQueue.idField),
            jobID,documentIDHash,documentID,failTime,failCount);
          String compositeDocumentID = makeCompositeID(documentIDHash,connectionName);
          documentIDMap.put(compositeDocumentID,dd);
          Map y = (Map)connectionNameMap.get(connectionName);
          if (y == null)
          {
            y = new HashMap();
            connectionNameMap.put(connectionName,y);
          }
          ArrayList x = (ArrayList)y.get(outputConnectionName);
          if (x == null)
          {
            // New entry needed
            x = new ArrayList();
            y.put(outputConnectionName,x);
          }
          x.add(dd);
          i++;
        }

        // For each bin, obtain a filtered answer, and enter all answers into a hash table.
        // We'll then scan the result again to look up the right descriptions for return,
        // and delete the ones that are owned multiply.
        HashMap allowedDocIds = new HashMap();
        Iterator iter = connectionNameMap.keySet().iterator();
        while (iter.hasNext())
        {
          String connectionName = (String)iter.next();
          Map y = (Map)connectionNameMap.get(connectionName);
          Iterator outputIter = y.keySet().iterator();
          while (outputIter.hasNext())
          {
            String outputConnectionName = (String)outputIter.next();
            ArrayList x = (ArrayList)y.get(outputConnectionName);
            // Do the filter query
            DocumentDescription[] descriptions = new DocumentDescription[x.size()];
            int j = 0;
            while (j < descriptions.length)
            {
              descriptions[j] = (DocumentDescription)x.get(j);
              j++;
            }
            String[] docIDHashes = getUnindexableDocumentIdentifiers(descriptions,connectionName,outputConnectionName);
            j = 0;
            while (j < docIDHashes.length)
            {
              String docIDHash = docIDHashes[j++];
              String key = makeCompositeID(docIDHash,connectionName);
              allowedDocIds.put(key,docIDHash);
            }
          }
        }

        // Now, assemble a result, and change the state of the records accordingly
        // First thing to do is order by document hash to reduce chances of deadlock.
        String[] compositeIDArray = new String[documentIDMap.size()];
        i = 0;
        iter = documentIDMap.keySet().iterator();
        while (iter.hasNext())
        {
          compositeIDArray[i++] = (String)iter.next();
        }
        
        java.util.Arrays.sort(compositeIDArray);
        
        DocumentDescription[] rval = new DocumentDescription[allowedDocIds.size()];
        int j = 0;
        i = 0;
        while (i < compositeIDArray.length)
        {
          String compositeDocumentID = compositeIDArray[i];
          DocumentDescription dd = (DocumentDescription)documentIDMap.get(compositeDocumentID);
          if (allowedDocIds.get(compositeDocumentID) == null)
          {
            // Delete this record and do NOT return it.
            jobQueue.deleteRecord(dd.getID());
            // What should we do about hopcount here?
            // We are deleting a record which belongs to a job that is being
            // cleaned up.  The job itself will go away when this is done,
            // and so will all the hopcount stuff pertaining to it.  So, the
            // treatment I've chosen here is to leave the hopcount alone and
            // let the job cleanup get rid of it at the right time.
            // Note: carrydown records handled in the same manner...
            //carryDown.deleteRecords(dd.getJobID(),new String[]{dd.getDocumentIdentifier()});
          }
          else
          {
            // Set the record status to "being deleted" and return it
            rval[j++] = dd;
            jobQueue.setDeletingStatus(dd.getID());
          }
          i++;
        }

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Done pruning unindexable docs after "+new Long(System.currentTimeMillis()-startTime).toString()+" ms.");

        return rval;

      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction finding deleteable docs: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Get a list of document identifiers that should actually be deleted from the index, from a list that
  * might contain identifiers that are shared with other jobs, which are targeted to the same output connection.
  * The input list is guaranteed to be smaller in size than maxInClauseCount for the database.
  *@param documentIdentifiers is the set of document identifiers to consider.
  *@param connectionName is the connection name for ALL the document identifiers.
  *@param outputConnectionName is the output connection name for ALL the document identifiers.
  *@return the set of documents which should be removed from the index.
  */
  protected String[] getUnindexableDocumentIdentifiers(DocumentDescription[] documentIdentifiers, String connectionName, String outputConnectionName)
    throws ManifoldCFException
  {
    // This is where we will count the individual document id's
    HashMap countMap = new HashMap();

    // First thing: Compute the set of document identifier hash values to query against
    HashMap map = new HashMap();
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      String hash = documentIdentifiers[i++].getDocumentIdentifierHash();
      map.put(hash,hash);
      countMap.put(hash,new MutableInteger(0));
    }

    if (map.size() == 0)
      return new String[0];

    // Build a query
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    sb.append("SELECT t0.").append(jobQueue.docHashField).append(" FROM ").append(jobQueue.getTableName())
      .append(" t0 WHERE t0.").append(jobQueue.docHashField).append(" IN(");
    boolean firstTime = true;
    Iterator iter = map.keySet().iterator();
    while (iter.hasNext())
    {
      String hashValue = (String)iter.next();
      if (firstTime)
        firstTime = false;
      else
        sb.append(',');
      sb.append('?');
      list.add(hashValue);
    }

    // Note: There is a potential race condition here.  One job may be running while another is in process of
    // being deleted.  If they share a document, then the delete task could decide to delete the document and do so right
    // after the ingestion takes place in the running job, but right before the document's status is updated
    // in the job queue [which would have prevented the deletion].
    // Unless a transaction is thrown around the time ingestion is taking place (which is a very bad idea)
    // we are stuck with the possibility of this condition, which will essentially lead to a document being
    // missing from the index.
    // One way of dealing with this is to treat "active" documents as already ingested, for the purpose of
    // reference counting.  Then these documents will not be deleted.  The risk then becomes that the "active"
    // document entry will not be completed (say, because of a restart), and thus the corresponding document
    // will never be removed from the index.
    //
    // Instead, the only solution is to not queue a document for any activity that is inconsistent with activities
    // that may already be ongoing for that document.  For this reason, I have introduced a "BEING_DELETED"
    // and "BEING_CLEANED" state
    // for a document.  These states will allow the various queries that queue up activities to avoid documents that
    // are currently being processed elsewhere.

    list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
    
    list.add(connectionName);
    list.add(outputConnectionName);
    
    sb.append(") AND t0.").append(jobQueue.statusField).append(" IN (?,?,?) AND EXISTS(SELECT 'x' FROM ")
      .append(jobs.getTableName()).append(" t1 WHERE t0.")
      .append(jobQueue.jobIDField).append("=t1.").append(jobs.idField).append(" AND t1.")
      .append(jobs.connectionNameField).append("=? AND t1.")
      .append(jobs.outputNameField).append("=?)");

    // Do the query, and then count the number of times each document identifier occurs.
    IResultSet results = database.performQuery(sb.toString(),list,null,null);
    i = 0;
    while (i < results.getRowCount())
    {
      IResultRow row = results.getRow(i++);
      String docIDHash = (String)row.getValue(jobQueue.docHashField);
      MutableInteger mi = (MutableInteger)countMap.get(docIDHash);
      if (mi != null)
        mi.increment();
    }

    // Go through and count only those that have a count of 1.
    int count = 0;
    iter = countMap.keySet().iterator();
    while (iter.hasNext())
    {
      String docIDHash = (String)iter.next();
      MutableInteger mi = (MutableInteger)countMap.get(docIDHash);
      if (mi.intValue() == 1)
        count++;
    }

    String[] rval = new String[count];
    iter = countMap.keySet().iterator();
    count = 0;
    while (iter.hasNext())
    {
      String docIDHash = (String)iter.next();
      MutableInteger mi = (MutableInteger)countMap.get(docIDHash);
      if (mi.intValue() == 1)
        rval[count++] = docIDHash;
    }

    return rval;
  }

  // These methods support the reprioritization thread.

  /** Get a list of already-processed documents to reprioritize.  Documents in all jobs will be
  * returned by this method.  Up to n document descriptions will be returned.
  *@param currentTime is the current time stamp for this prioritization pass.  Avoid
  *  picking up any documents that are labeled with this timestamp or after.
  *@param n is the maximum number of document descriptions desired.
  *@return the document descriptions.
  */
  public DocumentDescription[] getNextAlreadyProcessedReprioritizationDocuments(long currentTime, int n)
    throws ManifoldCFException
  {
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();

    // The desired query is:
    // SELECT docid FROM jobqueue WHERE prioritysettime < (currentTime) LIMIT (n)

    list.add(new Long(currentTime));
    
    list.add(jobQueue.statusToString(JobQueue.STATUS_COMPLETE));
    list.add(jobQueue.statusToString(JobQueue.STATUS_PURGATORY));
    
    sb.append("SELECT ").append(jobQueue.idField).append(",").append(jobQueue.docHashField).append(",")
      .append(jobQueue.docIDField).append(",").append(jobQueue.jobIDField)
      .append(" FROM ").append(jobQueue.getTableName()).append(" WHERE ")
      .append(jobQueue.prioritySetField).append("<? AND ").append(jobQueue.statusField).append(" IN(?,?) ")
      .append(database.constructOffsetLimitClause(0,n));

    IResultSet set = database.performQuery(sb.toString(),list,null,null,n,null);

    DocumentDescription[] rval = new DocumentDescription[set.getRowCount()];

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      rval[i] =new DocumentDescription((Long)row.getValue(jobQueue.idField),
        (Long)row.getValue(jobQueue.jobIDField),
        (String)row.getValue(jobQueue.docHashField),
        (String)row.getValue(jobQueue.docIDField));
      i++;
    }

    return rval;
  }

  /** Get a list of not-yet-processed documents to reprioritize.  Documents in all jobs will be
  * returned by this method.  Up to n document descriptions will be returned.
  *@param currentTime is the current time stamp for this prioritization pass.  Avoid
  *  picking up any documents that are labeled with this timestamp or after.
  *@param n is the maximum number of document descriptions desired.
  *@return the document descriptions.
  */
  public DocumentDescription[] getNextNotYetProcessedReprioritizationDocuments(long currentTime, int n)
    throws ManifoldCFException
  {
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();

    // This query MUST return only documents that are in a pending state which belong to an active job!!!

    list.add(Jobs.statusToString(Jobs.STATUS_ACTIVE));
    list.add(Jobs.statusToString(Jobs.STATUS_PAUSED));
    list.add(Jobs.statusToString(Jobs.STATUS_ACTIVEWAIT));
    list.add(Jobs.statusToString(Jobs.STATUS_PAUSEDWAIT));
    list.add(Jobs.statusToString(Jobs.STATUS_ACTIVE));
    list.add(Jobs.statusToString(Jobs.STATUS_READYFORSTARTUP));
    list.add(Jobs.statusToString(Jobs.STATUS_STARTINGUP));
    list.add(Jobs.statusToString(Jobs.STATUS_ABORTINGSTARTINGUPFORRESTART));
    list.add(Jobs.statusToString(Jobs.STATUS_ABORTINGSTARTINGUP));
    list.add(Jobs.statusToString(Jobs.STATUS_ACTIVESEEDING));
    list.add(Jobs.statusToString(Jobs.STATUS_PAUSEDSEEDING));
    list.add(Jobs.statusToString(Jobs.STATUS_ACTIVEWAITSEEDING));
    list.add(Jobs.statusToString(Jobs.STATUS_PAUSEDWAITSEEDING));
    list.add(Jobs.statusToString(Jobs.STATUS_ABORTING));
    list.add(Jobs.statusToString(Jobs.STATUS_ABORTINGFORRESTART));
    list.add(Jobs.statusToString(Jobs.STATUS_ABORTINGFORRESTARTSEEDING));
    
    list.add(new Long(currentTime));

    list.add(JobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(JobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(JobQueue.ACTION_RESCAN));
    
    sb.append("SELECT ").append(jobQueue.idField).append(",").append(jobQueue.docHashField).append(",")
      .append(jobQueue.docIDField).append(",").append(jobQueue.jobIDField)
      .append(" FROM ").append(jobQueue.getTableName()).append(" t0 WHERE EXISTS(SELECT 'x' FROM ").append(jobs.getTableName())
      .append(" t1 WHERE t0.").append(jobQueue.jobIDField).append("=t1.").append(jobs.idField).append(" AND t1.")
      .append(jobs.statusField).append(" IN(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)) AND ")
      .append(jobQueue.prioritySetField).append("<? AND ").append(jobQueue.statusField).append(" IN(?,?) AND (")
      .append(jobQueue.checkActionField).append(" IS NULL OR ")
      .append(jobQueue.checkActionField).append("=?")
      .append(") ").append(database.constructOffsetLimitClause(0,n));

    // Analyze jobqueue tables unconditionally, since it's become much more sensitive in 8.3 than it used to be.
    jobQueue.unconditionallyAnalyzeTables();

    IResultSet set = database.performQuery(sb.toString(),list,null,null,n,null);

    DocumentDescription[] rval = new DocumentDescription[set.getRowCount()];

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      rval[i] =new DocumentDescription((Long)row.getValue(jobQueue.idField),
        (Long)row.getValue(jobQueue.jobIDField),
        (String)row.getValue(jobQueue.docHashField),
        (String)row.getValue(jobQueue.docIDField));
      i++;
    }

    return rval;
  }

  /** Save a set of document priorities.  In the case where a document was eligible to have its
  * priority set, but it no longer is eligible, then the provided priority will not be written.
  *@param currentTime is the time in milliseconds since epoch.
  *@param documentDescriptions are the document descriptions.
  *@param priorities are the desired priorities.
  */
  public void writeDocumentPriorities(long currentTime, DocumentDescription[] documentDescriptions, double[] priorities)
    throws ManifoldCFException
  {

    // Retry loop - in case we get a deadlock despite our best efforts
    while (true)
    {
      // This should be ordered by document identifier hash in order to prevent potential deadlock conditions
      HashMap indexMap = new HashMap();
      String[] docIDHashes = new String[documentDescriptions.length];

      int i = 0;
      while (i < documentDescriptions.length)
      {
        String documentIDHash = documentDescriptions[i].getDocumentIdentifierHash() + ":"+documentDescriptions[i].getJobID();
        docIDHashes[i] = documentIDHash;
        indexMap.put(documentIDHash,new Integer(i));
        i++;
      }

      java.util.Arrays.sort(docIDHashes);

      long sleepAmt = 0L;

      // Start the transaction now
      database.beginTransaction();
      try
      {

        // Need to order the writes by doc id.
        i = 0;
        while (i < docIDHashes.length)
        {
          String docIDHash = docIDHashes[i];
          Integer x = (Integer)indexMap.remove(docIDHash);
          if (x == null)
            throw new ManifoldCFException("Assertion failure: duplicate document identifier jobid/hash detected!");
          int index = x.intValue();
          DocumentDescription dd = documentDescriptions[index];
          double priority = priorities[index];
          jobQueue.writeDocPriority(currentTime,dd.getID(),priorities[index]);
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Setting document priority for '"+dd.getDocumentIdentifier()+"' to "+new Double(priority).toString()+", set time "+new Long(currentTime).toString());
          i++;
        }
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction writing doc priorities: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Get up to the next n documents to be expired.
  * This method marks the documents whose descriptions have been returned as "being processed", or active.
  * The same marking is used as is used for documents that have been queued for worker threads.  The model
  * is thus identical.
  *
  *@param n is the maximum number of records desired.
  *@param currentTime is the current time.
  *@return the array of document descriptions to expire.
  */
  public DocumentSetAndFlags getExpiredDocuments(int n, long currentTime)
    throws ManifoldCFException
  {
    // Screening query
    // Moved outside of transaction, so there's less chance of keeping jobstatus cache key tied up
    // for an extended period of time.
    if (!jobs.activeJobsPresent())
      return new DocumentSetAndFlags(new DocumentDescription[0], new boolean[0]);

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Beginning query to look for documents to expire");
    }

    // Put together a query with a limit of n
    // Note well: This query does not do "FOR UPDATE".  The reason is that only one thread can possibly change the document's state to active.
    // If FOR UPDATE was included, deadlock conditions would be common because of the complexity of this query.

    ArrayList list = new ArrayList();
    list.add(new Long(currentTime));
    list.add(jobQueue.actionToString(JobQueue.ACTION_REMOVE));

    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDINGPURGATORY));

    list.add(jobs.statusToString(jobs.STATUS_ACTIVE));
    list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
    
    StringBuffer sb = new StringBuffer("SELECT t0.");
    sb.append(jobQueue.idField).append(",t0.");
    sb.append(jobQueue.jobIDField).append(",t0.");
    sb.append(jobQueue.docHashField).append(",t0.");
    sb.append(jobQueue.docIDField).append(",t0.");
    sb.append(jobQueue.statusField).append(",t0.");
    sb.append(jobQueue.failTimeField).append(",t0.");
    sb.append(jobQueue.failCountField).append(" FROM ").append(jobQueue.getTableName()).append(" t0,")
      .append(jobs.getTableName()).append(" t1 WHERE t0.");
    sb.append(jobQueue.checkTimeField).append("<=? AND t0.");
    sb.append(jobQueue.checkActionField).append("=? AND ");
    sb.append("t0.").append(jobQueue.statusField).append(" IN(?,?) AND t0.").append(jobQueue.jobIDField).append("=t1.").append(jobs.idField).append(" AND t1.")
      .append(jobs.statusField).append(" IN (?,?) AND ");
    sb.append("NOT EXISTS(SELECT 'x' FROM ").append(jobQueue.getTableName()).append(" t2 WHERE t0.")
      .append(jobQueue.docHashField).append("=t2.").append(jobQueue.docHashField).append(" AND t0.")
      .append(jobQueue.jobIDField).append("!=t2.").append(jobQueue.jobIDField).append(" AND t2.")
      .append(jobQueue.statusField).append(" IN (?,?,?,?,?,?))");
    sb.append(" ").append(database.constructOffsetLimitClause(0,n));

    // Analyze jobqueue tables unconditionally, since it's become much more sensitive in 8.3 than it used to be.
    jobQueue.unconditionallyAnalyzeTables();

    ArrayList answers = new ArrayList();

    int repeatCount = 0;
    while (true)
    {
      long sleepAmt = 0L;

      if (Logging.perf.isDebugEnabled())
      {
        repeatCount++;
        Logging.perf.debug(" Attempt "+Integer.toString(repeatCount)+" to expire documents, after "+
          new Long(System.currentTimeMillis() - startTime)+" ms");
      }

      database.beginTransaction();
      try
      {
        IResultSet set = database.performQuery(sb.toString(),list,null,null,n,null);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug(" Expiring "+Integer.toString(set.getRowCount())+" documents");

        // To avoid deadlock, we want to update the document id hashes in order.  This means reading into a structure I can sort by docid hash,
        // before updating any rows in jobqueue.
        HashMap connectionNameMap = new HashMap();
        HashMap documentIDMap = new HashMap();
        Map statusMap = new HashMap();

        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i);
          Long jobID = (Long)row.getValue(jobQueue.jobIDField);
          String documentIDHash = (String)row.getValue(jobQueue.docHashField);
          String documentID = (String)row.getValue(jobQueue.docIDField);
          int status = jobQueue.stringToStatus(row.getValue(jobQueue.statusField).toString());
          Long failTimeValue = (Long)row.getValue(jobQueue.failTimeField);
          Long failCountValue = (Long)row.getValue(jobQueue.failCountField);
          // Failtime is probably not useful in this context, but we'll bring it along for completeness
          long failTime;
          if (failTimeValue == null)
            failTime = -1L;
          else
            failTime = failTimeValue.longValue();
          int failCount;
          if (failCountValue == null)
            failCount = 0;
          else
            failCount = (int)failCountValue.longValue();
          IJobDescription jobDesc = load(jobID);
          String connectionName = jobDesc.getConnectionName();
          String outputConnectionName = jobDesc.getOutputConnectionName();
          DocumentDescription dd = new DocumentDescription((Long)row.getValue(jobQueue.idField),
            jobID,documentIDHash,documentID,failTime,failCount);
          String compositeDocumentID = makeCompositeID(documentIDHash,connectionName);
          documentIDMap.put(compositeDocumentID,dd);
          statusMap.put(compositeDocumentID,new Integer(status));
          Map y = (Map)connectionNameMap.get(connectionName);
          if (y == null)
          {
            y = new HashMap();
            connectionNameMap.put(connectionName,y);
          }
          ArrayList x = (ArrayList)y.get(outputConnectionName);
          if (x == null)
          {
            // New entry needed
            x = new ArrayList();
            y.put(outputConnectionName,x);
          }
          x.add(dd);
          i++;
        }

        // For each bin, obtain a filtered answer, and enter all answers into a hash table.
        // We'll then scan the result again to look up the right descriptions for return,
        // and delete the ones that are owned multiply.
        HashMap allowedDocIds = new HashMap();
        Iterator iter = connectionNameMap.keySet().iterator();
        while (iter.hasNext())
        {
          String connectionName = (String)iter.next();
          Map y = (Map)connectionNameMap.get(connectionName);
          Iterator outputIter = y.keySet().iterator();
          while (outputIter.hasNext())
          {
            String outputConnectionName = (String)outputIter.next();
            ArrayList x = (ArrayList)y.get(outputConnectionName);
            // Do the filter query
            DocumentDescription[] descriptions = new DocumentDescription[x.size()];
            int j = 0;
            while (j < descriptions.length)
            {
              descriptions[j] = (DocumentDescription)x.get(j);
              j++;
            }
            String[] docIDHashes = getUnindexableDocumentIdentifiers(descriptions,connectionName,outputConnectionName);
            j = 0;
            while (j < docIDHashes.length)
            {
              String docIDHash = docIDHashes[j++];
              String key = makeCompositeID(docIDHash,connectionName);
              allowedDocIds.put(key,docIDHash);
            }
          }
        }

        // Now, assemble a result, and change the state of the records accordingly
        // First thing to do is order by document hash, so we reduce the risk of deadlock.
        String[] compositeIDArray = new String[documentIDMap.size()];
        i = 0;
        iter = documentIDMap.keySet().iterator();
        while (iter.hasNext())
        {
          compositeIDArray[i++] = (String)iter.next();
        }
        
        java.util.Arrays.sort(compositeIDArray);
        
        DocumentDescription[] rval = new DocumentDescription[documentIDMap.size()];
        boolean[] rvalBoolean = new boolean[documentIDMap.size()];
        i = 0;
        while (i < compositeIDArray.length)
        {
          String compositeDocID = compositeIDArray[i];
          DocumentDescription dd = (DocumentDescription)documentIDMap.get(compositeDocID);
          // Determine whether we can delete it from the index or not
          rvalBoolean[i] = (allowedDocIds.get(compositeDocID) != null);
          // Set the record status to "being cleaned" and return it
          rval[i++] = dd;
          jobQueue.updateActiveRecord(dd.getID(),((Integer)statusMap.get(compositeDocID)).intValue());
        }

        return new DocumentSetAndFlags(rval, rvalBoolean);

      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction finding docs to expire: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }

    }
  }

  // This method supports the "queue stuffer" thread

  /**
  /** Get up to the next n document(s) to be fetched and processed.
  * This fetch returns records that contain the document identifier, plus all instructions
  * pertaining to the document's handling (e.g. whether it should be refetched if the version
  * has not changed).
  * This method also marks the documents whose descriptions have be returned as "being processed".
  *@param n is the maximum number of records desired.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@param interval is the number of milliseconds that this set of documents should represent (for throttling).
  *@param blockingDocuments is the place to record documents that were encountered, are eligible for reprioritization,
  *  but could not be queued due to throttling considerations.
  *@param statistics are the current performance statistics per connection, which are used to balance the queue stuffing
  *  so that individual connections are not overwhelmed.
  *@param scanRecord retains the bins from all documents encountered from the query, even those that were skipped due
  * to being overcommitted.
  *@return the array of document descriptions to fetch and process.
  */
  public DocumentDescription[] getNextDocuments(int n, long currentTime, long interval,
    BlockingDocuments blockingDocuments, PerformanceStatistics statistics,
    DepthStatistics scanRecord)
    throws ManifoldCFException
  {
    // NOTE WELL: Jobs that are throttled must control the number of documents that are fetched in
    // a given interval.  Therefore, the returned result has the following constraints on it:
    // 1) There must be no more than n documents returned total;
    // 2) For any given job that is throttled, the total number of documents returned must be
    //    consistent with the time interval provided.
    // In general, this requires the database layer to perform fairly advanced filtering on the
    // the result, far in excess of a simple count.  An implementation of an interface is therefore
    // going to need to be passed into the performQuery() operation, which prunes the resultset
    // as it is being read into memory.  That's a new feature that will need to be added to the
    // database layer.

    // Screening query
    // Moved outside of transaction, so there's less chance of keeping jobstatus cache key tied up
    // for an extended period of time.
    if (!jobs.activeJobsPresent())
      return new DocumentDescription[0];

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to find documents to queue");
    }

    // Below there used to be one large transaction, with multiple read seconds and multiple write sections.
    // As part of reducing the chance of postgresql encountering deadlock conditions, I wanted to break this
    // transaction up.  However, the transaction depended for its correctness in throttling on making sure
    // that the throttles that were built were based on the same active jobs that the subsequent queries
    // that did the stuffing relied upon.  This made reorganization impossible until I realized that with
    // Postgresql's way of doing transaction isolation this was going to happen anyway, so I needed a more
    // robust solution.
    //
    // Specifically, I chose to change the way documents were queued so that only documents from properly
    // throttled jobs could be queued.  That meant I needed to add stuff to the ThrottleLimit class to track
    // the very knowledge of an active job.  This had the additional benefit of meaning there was no chance of
    // a query occurring from inside a resultset filter.
    //
    // But, after I did this, it was no longer necessary to have such a large transaction either.


    // Anything older than 10 minutes ago is considered eligible for reprioritization.
    long prioritizationTime = currentTime - 60000L * 10L;

    ThrottleLimit vList = new ThrottleLimit(n,prioritizationTime);

    IResultSet jobconnections = jobs.getActiveJobConnections();
    HashMap connectionSet = new HashMap();
    int i = 0;
    while (i < jobconnections.getRowCount())
    {
      IResultRow row = jobconnections.getRow(i++);
      Long jobid = (Long)row.getValue("jobid");
      String connectionName = (String)row.getValue("connectionname");
      vList.addJob(jobid,connectionName);
      connectionSet.put(connectionName,connectionName);
    }

    // Find the active connection names.  We'll load these, and then get throttling info
    // from each one.
    String[] activeConnectionNames = new String[connectionSet.size()];
    Iterator iter = connectionSet.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      activeConnectionNames[i++] = (String)iter.next();
    }
    IRepositoryConnection[] connections = connectionMgr.loadMultiple(activeConnectionNames);


    // Accumulate a sum of the max_connection_count * avg_connection_rate values, so we can calculate the appropriate adjustment
    // factor and set the connection limits.
    HashMap rawFetchCounts = new HashMap();
    double rawFetchCountTotal = 0.0;
    i = 0;
    while (i < connections.length)
    {
      IRepositoryConnection connection = connections[i++];
      String connectionName = connection.getName();
      int maxConnections = connection.getMaxConnections();
      double avgFetchRate = statistics.calculateConnectionFetchRate(connectionName);
      double weightedRawFetchCount = avgFetchRate * (double)maxConnections;
      // Keep the avg rate for later use, since it may get updated before next time we need it.
      rawFetchCounts.put(connectionName,new Double(weightedRawFetchCount));
      rawFetchCountTotal += weightedRawFetchCount;
    }

    // Calculate an adjustment factor
    double fetchCountAdjustmentFactor = ((double)n) / rawFetchCountTotal;

    // For each job, we must amortize the maximum number of fetches per ms to the actual interval,
    // and also randomly select an extra fetch based on the fractional probability.  (This latter is
    // necessary for the case where the maximum fetch rate is specified to be pretty low.)
    //
    i = 0;
    while (i < connections.length)
    {
      IRepositoryConnection connection = connections[i++];
      String connectionName = connection.getName();
      // Check if throttled...
      String[] throttles = connection.getThrottles();
      int k = 0;
      while (k < throttles.length)
      {
        // The key is the regexp value itself
        String throttle = throttles[k++];
        float throttleValue = connection.getThrottleValue(throttle);
        // For the given connection, set the fetch limit per bin.  This is calculated using the time interval
        // and the desired fetch rate.  The fractional remainder is used to conditionally provide an "extra fetch"
        // on a weighted random basis.
        //
        // In the future, the connection may specify tuples which pair a regexp describing a set of bins against
        // a fetch rate.  In that case, each fetch rate would need to be turned into a precise maximum
        // count.
        double fetchesPerTimeInterval = (double)throttleValue * (double)interval;
        // Actual amount will be the integer value of this, plus an additional 1 if the random number aligns
        int fetches = (int)fetchesPerTimeInterval;
        fetchesPerTimeInterval -= (double)fetches;
        if (random.nextDouble() <= fetchesPerTimeInterval)
          fetches++;
        // Save the limit in the ThrottleLimit structure
        vList.addLimit(connectionName,throttle,fetches);
      }
      // For the overall connection, we also have a limit which is based on the number of connections there are actually available.
      Double weightedRawFetchCount = (Double)rawFetchCounts.get(connectionName);
      double adjustedFetchCount = weightedRawFetchCount.doubleValue() * fetchCountAdjustmentFactor;

      // Note well: Queuing starvation that results from there being very few available documents for high-priority connections is dealt with here by simply allowing
      // the stuffer thread to keep queuing documents until there are enough.  This will be pretty inefficient if there's an active connection that is fast and has lots
      // of available connection handles, but the bulk of the activity is on slow speed/highly handle limited connections, but I honestly can't think of a better way at the moment.
      // One good way to correct a bit for this problem is to set a higher document count floor for each connection - say 5 documents - then we won't loop as much.
      //
      // Be off in the higher direction rather than the lower; this also prohibits zero values and sets a minimum.
      int fetchCount = ((int)adjustedFetchCount) + 5;

      vList.setConnectionLimit(connectionName,fetchCount);
    }


    if (Logging.perf.isDebugEnabled())
      Logging.perf.debug("After "+new Long(System.currentTimeMillis()-startTime).toString()+" ms, beginning query to look for documents to queue");

    // System.out.println("Done building throttle structure");

    // Locate records.
    // Note that we do NOT want to get everything there is to know about the job
    // using this query, since the file specification may be large and expensive
    // to parse.  We will load a (cached) copy of the job description for that purpose.
    //
    // NOTE: This query deliberately excludes documents which may be being processed by another job.
    // (It actually excludes a bit more than that, because the exact query is impossible to write given
    // the fact that document id's cannot be compared.)  These are documents where there is ANOTHER
    // document entry with the same hash value, a different job id, and a status which is either "active",
    // "activepurgatory", or "beingdeleted".  (It does not check whether the jobs have the same connection or
    // whether the document id's are in fact the same, and therefore may temporarily block legitimate document
    // activity under rare circumstances.)
    //
    // The query I want is:
    // SELECT jobid,docid,status FROM jobqueue t0 WHERE status IN ('P','G') AND checktime <=xxx
    //              AND EXISTS(SELECT 'x' FROM
    //                      jobs t1 WHERE t0.jobid=t1.id AND t1.status='A')
    //              AND NOT EXISTS(SELECT 'x' FROM jobqueue t2 WHERE t0.hashval=t2.hashval AND t0.jobid!=t2.jobid
    //                      AND t2.status IN ('A','F','D'))
    //                  ORDER BY docpriority ASC LIMIT xxx
    //

    // NOTE WELL: The above query did just fine until adaptive recrawling was seriously tried.  Then, because every
    // document in a job was still active, it failed miserably, actually causing Postgresql to stop responding at
    // one point.  Why?  Well, the key thing is the sort criteria - there just isn't any way to sort 1M documents
    // without working with a monster resultset.
    //
    // I introduced a new index as a result - based solely on docpriority - and postgresql now correctly uses that index
    // to pull its results in an ordered fashion
    //
    //
    // Another subtlety is that I *must* mark the documents active as I find them, so that they do not
    // have any chance of getting returned twice.

    // Accumulate the answers here
    ArrayList answers = new ArrayList();

    // The current time value
    Long currentTimeValue = new Long(currentTime);

    // Always analyze jobqueue before this query.  Otherwise stuffing may get a bad plan, interfering with performance.
    // This turned out to be needed in postgresql 8.3, even though 8.2 worked fine.
    jobQueue.unconditionallyAnalyzeTables();

    // Loop through priority values
    int currentPriority = 1;

    boolean isDone = false;

    while (!isDone && currentPriority <= 10)
    {
      if (jobs.hasPriorityJobs(currentPriority))
      {
        Long currentPriorityValue = new Long((long)currentPriority);
        fetchAndProcessDocuments(answers,currentTimeValue,currentPriorityValue,vList,connections);
        isDone = !vList.checkContinue();
      }
      currentPriority++;
    }

    // Assert the blocking documents we discovered
    vList.tallyBlockingDocuments(blockingDocuments);

    // Convert the saved answers to an array
    DocumentDescription[] rval = new DocumentDescription[answers.size()];
    i = 0;
    while (i < rval.length)
    {
      rval[i] = (DocumentDescription)answers.get(i);
      i++;
    }

    // After we're done pulling stuff from the queue, find the eligible row with the best priority on the queue, and save the bins for assessment.
    // This done to decide what the "floor" bincount should be - the idea being that it is wrong to assign priorities for new documents which are
    // higher than the current level that is currently being  dequeued.
    //
    // The complicating factor here is that there are indeed many potential *classes* of documents, each of which might have its own current
    // document priority level.  For example, documents could be classed by job, which might make sense because there is a possibility that two jobs'
    // job priorities may differ.  Also, because of document fetch scheduling, each time frame may represent a class in its own right as well.
    // These classes would have to be associated with independent bin counts, if we were to make any use of them.  Then, it would be also necessary
    // to know what classes a document belonged to in order to be able to calculate its priority.
    //
    // An alternative way to proceed is to just have ONE class, and document priorities then get assigned without regard to job, queuing time, etc.
    // That's the current reality.  The code below works in that model, knowing full well that it is an approximation to an ideal.

    // Find the one row from a live job that has the best document priority, which is available within the current time window.
    // Note that if there is NO such document, it means we were able to queue all eligible documents, and thus prioritization is probably not even
    // germane at the moment.

    StringBuffer sb = new StringBuffer("SELECT ");
    ArrayList list = new ArrayList();
    
    list.add(Jobs.statusToString(jobs.STATUS_ACTIVE));
    list.add(Jobs.statusToString(jobs.STATUS_ACTIVESEEDING));
    
    list.add(currentTimeValue);
    
    list.add(jobQueue.actionToString(JobQueue.ACTION_RESCAN));
    
    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDINGPURGATORY));
    
    sb.append(jobQueue.docPriorityField).append(",").append(jobQueue.jobIDField).append(",")
      .append(jobQueue.docHashField).append(",").append(jobQueue.docIDField)
      .append(" FROM ").append(jobQueue.getTableName())
      .append(" t0 WHERE EXISTS(SELECT 'x' FROM ").append(jobs.getTableName()).append(" t1 WHERE t0.").append(jobQueue.jobIDField)
      .append("=t1.").append(jobs.idField).append(" AND t1.").append(jobs.statusField).append(" IN(?,?)) AND ")
      .append(jobQueue.checkTimeField).append("<=? AND (")
      .append(jobQueue.checkActionField).append(" IS NULL OR ")
      .append(jobQueue.checkActionField).append("=?")
      .append(") AND (")
      .append(jobQueue.statusField).append("=? OR ").append(jobQueue.statusField).append("=?)")
      .append(" ORDER BY ").append(jobQueue.docPriorityField).append(" ASC ").append(database.constructOffsetLimitClause(0,1));


    IResultSet set = database.performQuery(sb.toString(),list,null,null,1,null);
    if (set.getRowCount() > 0)
    {
      IResultRow row = set.getRow(0);
      Double docPriority = (Double)row.getValue(jobQueue.docPriorityField);
      scanRecord.addBins(docPriority);
    }
    return rval;
  }

  protected void addDocumentCriteria(StringBuffer sb, ArrayList list, Long currentTimeValue, Long currentPriorityValue)
    throws ManifoldCFException
  {
    
    list.add(currentTimeValue);
    
    list.add(jobQueue.actionToString(JobQueue.ACTION_RESCAN));
    
    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(JobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobs.statusToString(jobs.STATUS_ACTIVE));
    list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING));
    
    list.add(currentPriorityValue);

    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
    
    sb.append("t0.").append(jobQueue.checkTimeField).append("<=? AND ");
    sb.append("(t0.").append(jobQueue.checkActionField).append(" IS NULL OR t0.").append(jobQueue.checkActionField)
      .append("=?) AND ");
    sb.append("(t0.").append(jobQueue.statusField).append("=? OR t0.").append(jobQueue.statusField).append("=?) AND t0.").append(jobQueue.jobIDField).append("=t1.").append(jobs.idField).append(" AND t1.")
      .append(jobs.statusField).append(" IN (?,?) AND t1.")
      .append(jobs.priorityField).append("=? AND ");
    sb.append("NOT EXISTS(SELECT 'x' FROM ").append(jobQueue.getTableName()).append(" t2 WHERE t0.")
      .append(jobQueue.docHashField).append("=t2.").append(jobQueue.docHashField).append(" AND t0.")
      .append(jobQueue.jobIDField).append("!=t2.").append(jobQueue.jobIDField).append(" AND t2.")
      .append(jobQueue.statusField).append(" IN (?,?,?,?,?,?)) AND ");

    // Prerequisite event clause: AND NOT EXISTS(SELECT 'x' FROM prereqevents t3,events t4 WHERE t3.ownerid=t0.id AND t3.name=t4.name)
    sb.append("NOT EXISTS(SELECT 'x' FROM ").append(jobQueue.prereqEventManager.getTableName()).append(" t3,").append(eventManager.getTableName()).append(" t4 WHERE t0.")
      .append(jobQueue.idField).append("=t3.").append(jobQueue.prereqEventManager.ownerField).append(" AND t3.")
      .append(jobQueue.prereqEventManager.eventNameField).append("=t4.").append(eventManager.eventNameField)
      .append(")");
  }

  /** Fetch and process documents matching the passed-in criteria */
  protected void fetchAndProcessDocuments(ArrayList answers, Long currentTimeValue, Long currentPriorityValue,
    ThrottleLimit vList, IRepositoryConnection[] connections)
    throws ManifoldCFException
  {

    // Note well: This query does not do "FOR UPDATE".  The reason is that only one thread can possibly change the document's state to active.
    // When FOR UPDATE was included, deadlock conditions were common because of the complexity of this query.

    ArrayList list = new ArrayList();

    StringBuffer sb = new StringBuffer("SELECT t0.");
    sb.append(jobQueue.idField).append(",t0.");
    if (Logging.scheduling.isDebugEnabled())
      sb.append(jobQueue.docPriorityField).append(",t0.");
    sb.append(jobQueue.jobIDField).append(",t0.");
    sb.append(jobQueue.docHashField).append(",t0.");
    sb.append(jobQueue.docIDField).append(",t0.");
    sb.append(jobQueue.statusField).append(",t0.");
    sb.append(jobQueue.failTimeField).append(",t0.");
    sb.append(jobQueue.failCountField).append(",t0.");
    sb.append(jobQueue.prioritySetField).append(" FROM ").append(jobQueue.getTableName()).append(" t0,")
      .append(jobs.getTableName()).append(" t1 WHERE ");

    addDocumentCriteria(sb,list,currentTimeValue,currentPriorityValue);

    sb.append(" ORDER BY t0.").append(jobQueue.docPriorityField).append(" ASC ");

    // Before entering the transaction, we must provide the throttlelimit object with all the connector
    // instances it could possibly need.  The purpose for doing this is to prevent a deadlock where
    // connector starvation causes database lockup.
    //
    // The preallocation of multiple connector instances is certainly a worry.  If any other part
    // of the code allocates multiple connector instances also, the potential exists for this to cause
    // deadlock all by itself.  I've therefore built a "grab multiple" and a "release multiple"
    // at the connector factory level to make sure these requests are properly ordered.

    String[] orderingKeys = new String[connections.length];
    String[] classNames = new String[connections.length];
    ConfigParams[] configParams = new ConfigParams[connections.length];
    int[] maxConnections = new int[connections.length];
    int k = 0;
    while (k < connections.length)
    {
      IRepositoryConnection connection = connections[k];
      orderingKeys[k] = connection.getName();
      classNames[k] = connection.getClassName();
      configParams[k] = connection.getConfigParams();
      maxConnections[k] = connection.getMaxConnections();
      k++;
    }
    IRepositoryConnector[] connectors = RepositoryConnectorFactory.grabMultiple(threadContext,orderingKeys,classNames,configParams,maxConnections);
    try
    {
      // Hand the connectors off to the ThrottleLimit instance
      k = 0;
      while (k < connections.length)
      {
        vList.addConnectionName(connections[k].getName(),connectors[k]);
        k++;
      }

      // Now we can tack the limit onto the query.  Before this point, remainingDocuments would be crap
      int limitValue = vList.getRemainingDocuments();
      sb.append(database.constructOffsetLimitClause(0,limitValue));

      if (Logging.perf.isDebugEnabled())
      {
        Logging.perf.debug("Queuing documents from time "+currentTimeValue.toString()+" job priority "+currentPriorityValue.toString()+
          " (up to "+Integer.toString(vList.getRemainingDocuments())+" documents)");
      }

      while (true)
      {
        long sleepAmt = 0L;
        database.beginTransaction();
        try
        {
          IResultSet set = database.performQuery(sb.toString(),list,null,null,-1,vList);

          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug(" Queuing "+Integer.toString(set.getRowCount())+" documents");

          // To avoid deadlock, we want to update the document id hashes in order.  This means reading into a structure I can sort by docid hash,
          // before updating any rows in jobqueue.
          String[] docIDHashes = new String[set.getRowCount()];
          Map storageMap = new HashMap();
          Map statusMap = new HashMap();

          int i = 0;
          while (i < set.getRowCount())
          {
            IResultRow row = set.getRow(i);
            Long id = (Long)row.getValue(jobQueue.idField);
            Long jobID = (Long)row.getValue(jobQueue.jobIDField);
            String docIDHash = (String)row.getValue(jobQueue.docHashField);
            String docID = (String)row.getValue(jobQueue.docIDField);
            int status = jobQueue.stringToStatus(row.getValue(jobQueue.statusField).toString());
            Long failTimeValue = (Long)row.getValue(jobQueue.failTimeField);
            Long failCountValue = (Long)row.getValue(jobQueue.failCountField);
            long failTime;
            if (failTimeValue == null)
              failTime = -1L;
            else
              failTime = failTimeValue.longValue();
            int failCount;
            if (failCountValue == null)
              failCount = -1;
            else
              failCount = (int)failCountValue.longValue();

            DocumentDescription dd = new DocumentDescription(id,jobID,docIDHash,docID,failTime,failCount);
            docIDHashes[i] = docIDHash + ":" + jobID;
            storageMap.put(docIDHashes[i],dd);
            statusMap.put(docIDHashes[i],new Integer(status));
            if (Logging.scheduling.isDebugEnabled())
            {
              Double docPriority = (Double)row.getValue(jobQueue.docPriorityField);
              Logging.scheduling.debug("Stuffing document '"+docID+"' that has priority "+docPriority.toString()+" onto active list");
            }
            i++;
          }

          // No duplicates are possible here
          java.util.Arrays.sort(docIDHashes);

          i = 0;
          while (i < docIDHashes.length)
          {
            String docIDHash = docIDHashes[i];
            DocumentDescription dd = (DocumentDescription)storageMap.get(docIDHash);
            Long id = dd.getID();
            int status = ((Integer)statusMap.get(docIDHash)).intValue();

            // Set status to "ACTIVE".
            jobQueue.updateActiveRecord(id,status);

            answers.add(dd);

            i++;
          }
          break;
        }
        catch (ManifoldCFException e)
        {
          database.signalRollback();
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            if (Logging.perf.isDebugEnabled())
              Logging.perf.debug("Aborted transaction finding docs to queue: "+e.getMessage());
            sleepAmt = getRandomAmount();
            continue;
          }
          throw e;
        }
        catch (Error e)
        {
          database.signalRollback();
          throw e;
        }
        finally
        {
          database.endTransaction();
          sleepFor(sleepAmt);
        }
      }
    }
    finally
    {
      RepositoryConnectorFactory.releaseMultiple(connectors);
    }
  }

  // These methods support the individual fetch/process threads.

  /** Verify that a specific job is indeed still active.  This is used to permit abort or pause to be relatively speedy.
  * The query done within MUST be cached in order to not cause undue performance degradation.
  *@param jobID is the job identifier.
  *@return true if the job is in one of the "active" states.
  */
  public boolean checkJobActive(Long jobID)
    throws ManifoldCFException
  {
    return jobs.checkJobActive(jobID);
  }

  /** Verify if a job is still processing documents, or no longer has any outstanding active documents */
  public boolean checkJobBusy(Long jobID)
    throws ManifoldCFException
  {
    return jobQueue.checkJobBusy(jobID);
  }

  /** Note completion of document processing by a job thread of a document.
  * This method causes the state of the document to be marked as "completed".
  *@param documentDescriptions are the description objects for the documents that were processed.
  */
  public void markDocumentCompletedMultiple(DocumentDescription[] documentDescriptions)
    throws ManifoldCFException
  {
    // Before we can change a document status, we need to know the *current* status.  Therefore, a SELECT xxx FOR UPDATE/UPDATE
    // transaction is needed in order to complete these documents correctly.
    //
    // Since we are therefore setting row locks on thejobqueue table, we need to work to avoid unnecessary deadlocking.  To do that, we have to
    // lock rows in document id hash order!!  Luckily, the DocumentDescription objects have a document identifier buried within, which we can use to
    // order the "select for update" operations appropriately.
    //

    HashMap indexMap = new HashMap();
    String[] docIDHashes = new String[documentDescriptions.length];

    int i = 0;
    while (i < documentDescriptions.length)
    {
      String documentIDHash = documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      docIDHashes[i] = documentIDHash;
      indexMap.put(documentIDHash,new Integer(i));
      i++;
    }

    java.util.Arrays.sort(docIDHashes);

    // Retry loop - in case we get a deadlock despite our best efforts
    while (true)
    {
      long sleepAmt = 0L;

      // Start the transaction now
      database.beginTransaction();
      try
      {
        // Do one row at a time, to avoid deadlocking things
        i = 0;
        while (i < docIDHashes.length)
        {
          String docIDHash = docIDHashes[i];

          // Get the DocumentDescription object
          DocumentDescription dd = documentDescriptions[((Integer)indexMap.get(docIDHash)).intValue()];

          // Query for the status
          ArrayList list = new ArrayList();
          list.add(dd.getID());
          IResultSet set = database.performQuery("SELECT "+jobQueue.statusField+" FROM "+jobQueue.getTableName()+" WHERE "+jobQueue.idField+"=? FOR UPDATE",
            list,null,null);
          if (set.getRowCount() > 0)
          {
            IResultRow row = set.getRow(0);
            // Grab the status
            int status = jobQueue.stringToStatus((String)row.getValue(jobQueue.statusField));
            // Update the jobqueue table
            jobQueue.updateCompletedRecord(dd.getID(),status);
          }
          i++;
        }
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction marking completed "+Integer.toString(docIDHashes.length)+
            " docs: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Note completion of document processing by a job thread of a document.
  * This method causes the state of the document to be marked as "completed".
  *@param documentDescription is the description object for the document that was processed.
  */
  public void markDocumentCompleted(DocumentDescription documentDescription)
    throws ManifoldCFException
  {
    markDocumentCompletedMultiple(new DocumentDescription[]{documentDescription});
  }

  /** Note deletion as result of document processing by a job thread of a document.
  *@param documentDescriptions are the set of description objects for the documents that were processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentDeletedMultiple(Long jobID, String[] legalLinkTypes, DocumentDescription[] documentDescriptions,
    int hopcountMethod)
    throws ManifoldCFException
  {
    if (documentDescriptions.length == 0)
      return new DocumentDescription[0];

    // Order of locking is not normally important here, because documents that wind up being deleted are never being worked on by anything else.
    // In all cases, the state of the document excludes other activity.
    // The only tricky situation is when a thread is processing a document which happens to be getting deleted, while another thread is trying to add
    // a reference for the very same document to the queue.  Then, order of locking matters, so the deletions should happen in a specific order to avoid
    // the possibility of deadlock.  Nevertheless, this is enough of a risk that I've chosen to order the deletions by document id hash order, just like everywhere
    // else.

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to delete "+Integer.toString(documentDescriptions.length)+" docs and clean up hopcount for job "+jobID.toString());
    }

    HashMap indexMap = new HashMap();
    String[] docIDHashes = new String[documentDescriptions.length];
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] = documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      indexMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort by doc hash, to establish non-blocking lock order
    java.util.Arrays.sort(docIDHashes);

    DocumentDescription[] rval;
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to start deleting "+Integer.toString(docIDHashes.length)+
          " docs and clean up hopcount for job "+jobID.toString());

        String[] docIDSimpleHashes = new String[docIDHashes.length];
        // Delete jobqueue rows FIRST.  Even though we do this before assessing the carrydown implications, it is OK because it's the CHILDREN of these
        // rows that might get affected by carrydown data deletion, not the rows themselves!
        i = 0;
        while (i < docIDHashes.length)
        {
          String docIDHash = docIDHashes[i];
          DocumentDescription dd = documentDescriptions[((Integer)indexMap.get(docIDHash)).intValue()];
          // Individual operations are necessary so order can be controlled.
          jobQueue.deleteRecord(dd.getID());
          docIDSimpleHashes[i] = dd.getDocumentIdentifierHash();
          i++;
        }

        // Next, find the documents that are affected by carrydown deletion.
        rval = calculateAffectedDeleteCarrydownChildren(jobID,docIDSimpleHashes);

        // Finally, delete the carrydown records in question.
        carryDown.deleteRecords(jobID,docIDSimpleHashes);
        if (legalLinkTypes.length > 0)
          hopCount.deleteDocumentIdentifiers(jobID,legalLinkTypes,docIDSimpleHashes,hopcountMethod);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to delete "+Integer.toString(docIDHashes.length)+
          " docs and clean up hopcount for job "+jobID.toString());
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction deleting "+Integer.toString(docIDHashes.length)+
            " docs and clean up hopcount for job "+jobID.toString()+": "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
    return rval;
  }

  /** Helper method: Find the document descriptions that will be affected due to carrydown row deletions.
  */
  protected DocumentDescription[] calculateAffectedDeleteCarrydownChildren(Long jobID, String[] docIDHashes)
    throws ManifoldCFException
  {
    // Break the request into pieces, as needed, and throw everything into a hash for uniqueness.
    // We are going to need to break up this query into a number of subqueries, each covering a subset of parent id hashes.
    // The goal is to throw all the children into a hash, to make them unique at the end.
    HashMap resultHash = new HashMap();
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = 25;
    int i = 0;
    int z = 0;
    while (i < docIDHashes.length)
    {
      if (z == maxCount)
      {
        processDeleteHashSet(jobID,resultHash,sb.toString(),list);
        list.clear();
        sb.setLength(0);
        z = 0;
      }
      if (z > 0)
        sb.append(",");
      sb.append("?");
      list.add(docIDHashes[i]);
      i++;
      z++;
    }

    if (z > 0)
      processDeleteHashSet(jobID,resultHash,sb.toString(),list);

    // Now, put together the result document list from the hash.
    DocumentDescription[] rval = new DocumentDescription[resultHash.size()];
    i = 0;
    Iterator iter = resultHash.keySet().iterator();
    while (iter.hasNext())
    {
      Long id = (Long)iter.next();
      DocumentDescription dd = (DocumentDescription)resultHash.get(id);
      rval[i++] = dd;
    }
    return rval;
  }

  /** Helper method: look up rows affected by a deleteRecords operation.
  */
  protected void processDeleteHashSet(Long jobID, HashMap resultHash, String queryPart, ArrayList list)
    throws ManifoldCFException
  {
    // The query here mirrors the carrydown.restoreRecords() delete query!  However, it also fetches enough information to build a DocumentDescription
    // object for return, and so a join is necessary against the jobqueue table.
    //???
    String query = "SELECT t0."+jobQueue.idField+",t0."+jobQueue.docHashField+",t0."+jobQueue.docIDField+" FROM "+
      jobQueue.getTableName()+" t0 WHERE EXISTS(SELECT 'x' FROM "+carryDown.getTableName()+
      " t1 WHERE t1."+carryDown.parentIDHashField+" IN ("+queryPart+") AND t1."+carryDown.childIDHashField+"=t0."+jobQueue.docHashField+
      " AND t0."+jobQueue.jobIDField+"=? AND t1."+carryDown.jobIDField+"=?)";
    list.add(jobID);
    list.add(jobID);
    IResultSet set = database.performQuery(query,list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long id = (Long)row.getValue(jobQueue.idField);
      String documentIdentifierHash = (String)row.getValue(jobQueue.docHashField);
      String documentIdentifier = (String)row.getValue(jobQueue.docIDField);
      resultHash.put(id,new DocumentDescription(id,jobID,documentIdentifierHash,documentIdentifier));
    }
  }

  /** Note deletion as result of document processing by a job thread of a document.
  *@param documentDescription is the description object for the document that was processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentDeleted(Long jobID, String[] legalLinkTypes, DocumentDescription documentDescription,
    int hopcountMethod)
    throws ManifoldCFException
  {
    return markDocumentDeletedMultiple(jobID,legalLinkTypes,new DocumentDescription[]{documentDescription},hopcountMethod);
  }


  /** Requeue a document for further processing in the future.
  * This method is called after a document is processed, when the job is a "continuous" one.
  * It is essentially equivalent to noting that the document processing is complete, except the
  * document remains on the queue.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param executeTimes are the times that the documents should be rescanned.  Null indicates "never".
  *@param actions are what should be done when the time arrives.  Choices are ACTION_RESCAN or ACTION_REMOVE.
  */
  public void requeueDocumentMultiple(DocumentDescription[] documentDescriptions, Long[] executeTimes,
    int[] actions)
    throws ManifoldCFException
  {
    String[] docIDHashes = new String[documentDescriptions.length];
    Long[] ids = new Long[documentDescriptions.length];
    Long[] executeTimesNew = new Long[documentDescriptions.length];
    int[] actionsNew = new int[documentDescriptions.length];

    // First loop maps document identifier back to an index.
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] =documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      indexMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort!
    java.util.Arrays.sort(docIDHashes);

    // Next loop populates the actual arrays we use to feed the operation so that the ordering is correct.
    i = 0;
    while (i < docIDHashes.length)
    {
      String docIDHash = docIDHashes[i];
      Integer x = (Integer)indexMap.remove(docIDHash);
      if (x == null)
        throw new ManifoldCFException("Assertion failure: duplicate document identifier jobid/hash detected!");
      int index = x.intValue();
      ids[i] = documentDescriptions[index].getID();
      executeTimesNew[i] = executeTimes[index];
      actionsNew[i] = actions[index];
      i++;
    }

    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Going through ids in order should greatly reduce or eliminate chances of deadlock occurring.  We thus need to pay attention to the sorted order.
        i = 0;
        while (i < ids.length)
        {
          jobQueue.setStatus(ids[i],jobQueue.STATUS_PENDINGPURGATORY,executeTimesNew[i],actionsNew[i],-1L,-1);
          i++;
        }

        break;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction requeuing documents: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Requeue a document for further processing in the future.
  * This method is called after a document is processed, when the job is a "continuous" one.
  * It is essentially equivalent to noting that the document processing is complete, except the
  * document remains on the queue.
  *@param documentDescription is the description object for the document that was processed.
  *@param executeTime is the time that the document should be rescanned.  Null indicates "never".
  *@param action is what should be done when the time arrives.  Choices include ACTION_RESCAN or ACTION_REMOVE.
  */
  public void requeueDocument(DocumentDescription documentDescription, Long executeTime, int action)
    throws ManifoldCFException
  {
    requeueDocumentMultiple(new DocumentDescription[]{documentDescription},new Long[]{executeTime},new int[]{action});
  }

  /** Reset a set of documents for further processing in the future.
  * This method is called after some unknown number of the documents were processed, but then a service interruption occurred.
  * Note well: The logic here basically presumes that we cannot know whether the documents were indeed processed or not.
  * If we knew for a fact that none of the documents had been handled, it would be possible to look at the document's
  * current status and decide what the new status ought to be, based on a true rollback scenario.  Such cases, however, are rare enough so that
  * special logic is probably not worth it.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param executeTime is the time that the documents should be rescanned.
  *@param failTime is the time beyond which a service interruption will be considered a hard failure.
  *@param failCount is the number of retries beyond which a service interruption will be considered a hard failure.
  */
  public void resetDocumentMultiple(DocumentDescription[] documentDescriptions, long executeTime,
    int action, long failTime, int failCount)
    throws ManifoldCFException
  {
    Long executeTimeLong = new Long(executeTime);
    Long[] ids = new Long[documentDescriptions.length];
    String[] docIDHashes = new String[documentDescriptions.length];
    Long[] executeTimes = new Long[documentDescriptions.length];
    int[] actions = new int[documentDescriptions.length];
    long[] failTimes = new long[documentDescriptions.length];
    int[] failCounts = new int[documentDescriptions.length];

    // First loop maps document identifier back to an index.
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] =documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      indexMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort!
    java.util.Arrays.sort(docIDHashes);

    // Next loop populates the actual arrays we use to feed the operation so that the ordering is correct.
    i = 0;
    while (i < docIDHashes.length)
    {
      String docIDHash = docIDHashes[i];
      Integer x = (Integer)indexMap.remove(docIDHash);
      if (x == null)
        throw new ManifoldCFException("Assertion failure: duplicate document identifier jobid/hash detected!");
      int index = x.intValue();
      ids[i] = documentDescriptions[index].getID();
      executeTimes[i] = executeTimeLong;
      actions[i] = action;
      long oldFailTime = documentDescriptions[index].getFailTime();
      if (oldFailTime == -1L)
        oldFailTime = failTime;
      failTimes[i] = oldFailTime;
      int oldFailCount = documentDescriptions[index].getFailRetryCount();
      if (oldFailCount == -1)
        oldFailCount = failCount;
      else
      {
        oldFailCount--;
        if (failCount != -1 && oldFailCount > failCount)
          oldFailCount = failCount;
      }
      failCounts[i] = oldFailCount;
      i++;
    }

    // Documents get marked PENDINGPURGATORY regardless of their current state; this is because we can't know at this point whether
    // an ingestion attempt occurred or not, so we have to treat the documents as having been processed at least once.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Going through ids in order should greatly reduce or eliminate chances of deadlock occurring.  We thus need to pay attention to the sorted order.
        i = 0;
        while (i < ids.length)
        {
          jobQueue.setStatus(ids[i],jobQueue.STATUS_PENDINGPURGATORY,executeTimes[i],actions[i],(failTimes==null)?-1L:failTimes[i],(failCounts==null)?-1:failCounts[i]);
          i++;
        }

        break;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction resetting documents: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset a set of cleaning documents for further processing in the future.
  * This method is called after some unknown number of the documents were cleaned, but then an ingestion service interruption occurred.
  * Note well: The logic here basically presumes that we cannot know whether the documents were indeed cleaned or not.
  * If we knew for a fact that none of the documents had been handled, it would be possible to look at the document's
  * current status and decide what the new status ought to be, based on a true rollback scenario.  Such cases, however, are rare enough so that
  * special logic is probably not worth it.
  *@param documentDescriptions is the set of description objects for the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetCleaningDocumentMultiple(DocumentDescription[] documentDescriptions, long checkTime)
    throws ManifoldCFException
  {
    Long[] ids = new Long[documentDescriptions.length];
    String[] docIDHashes = new String[documentDescriptions.length];

    // First loop maps document identifier back to an index.
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] =documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      indexMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort!
    java.util.Arrays.sort(docIDHashes);

    // Next loop populates the actual arrays we use to feed the operation so that the ordering is correct.
    i = 0;
    while (i < docIDHashes.length)
    {
      String docIDHash = docIDHashes[i];
      Integer x = (Integer)indexMap.remove(docIDHash);
      if (x == null)
        throw new ManifoldCFException("Assertion failure: duplicate document identifier jobid/hash detected!");
      int index = x.intValue();
      ids[i] = documentDescriptions[index].getID();
      i++;
    }

    // Documents get marked PURGATORY regardless of their current state; this is because we can't know at this point what the actual prior state was.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Going through ids in order should greatly reduce or eliminate chances of deadlock occurring.  We thus need to pay attention to the sorted order.
        i = 0;
        while (i < ids.length)
        {
          jobQueue.setUncleaningStatus(ids[i],checkTime);
          i++;
        }

        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction resetting cleaning documents: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset a cleaning document back to its former state.
  * This gets done when a deleting thread sees a service interruption, etc., from the ingestion system.
  *@param documentDescription is the description of the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetCleaningDocument(DocumentDescription documentDescription, long checkTime)
    throws ManifoldCFException
  {
    resetCleaningDocumentMultiple(new DocumentDescription[]{documentDescription},checkTime);
  }

  /** Reset a set of deleting documents for further processing in the future.
  * This method is called after some unknown number of the documents were deleted, but then an ingestion service interruption occurred.
  * Note well: The logic here basically presumes that we cannot know whether the documents were indeed processed or not.
  * If we knew for a fact that none of the documents had been handled, it would be possible to look at the document's
  * current status and decide what the new status ought to be, based on a true rollback scenario.  Such cases, however, are rare enough so that
  * special logic is probably not worth it.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetDeletingDocumentMultiple(DocumentDescription[] documentDescriptions, long checkTime)
    throws ManifoldCFException
  {
    Long[] ids = new Long[documentDescriptions.length];
    String[] docIDHashes = new String[documentDescriptions.length];

    // First loop maps document identifier back to an index.
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] =documentDescriptions[i].getDocumentIdentifierHash() + ":" + documentDescriptions[i].getJobID();
      indexMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort!
    java.util.Arrays.sort(docIDHashes);

    // Next loop populates the actual arrays we use to feed the operation so that the ordering is correct.
    i = 0;
    while (i < docIDHashes.length)
    {
      String docIDHash = docIDHashes[i];
      Integer x = (Integer)indexMap.remove(docIDHash);
      if (x == null)
        throw new ManifoldCFException("Assertion failure: duplicate document identifier jobid/hash detected!");
      int index = x.intValue();
      ids[i] = documentDescriptions[index].getID();
      i++;
    }

    // Documents get marked COMPLETED regardless of their current state; this is because we can't know at this point what the actual prior state was.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Going through ids in order should greatly reduce or eliminate chances of deadlock occurring.  We thus need to pay attention to the sorted order.
        i = 0;
        while (i < ids.length)
        {
          jobQueue.setUndeletingStatus(ids[i],checkTime);
          i++;
        }

        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction resetting documents: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset a deleting document back to its former state.
  * This gets done when a deleting thread sees a service interruption, etc., from the ingestion system.
  *@param documentDescription is the description object for the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetDeletingDocument(DocumentDescription documentDescription, long checkTime)
    throws ManifoldCFException
  {
    resetDeletingDocumentMultiple(new DocumentDescription[]{documentDescription},checkTime);
  }


  /** Reset an active document back to its former state.
  * This gets done when there's a service interruption and the document cannot be processed yet.
  * Note well: This method formerly presumed that a perfect rollback was possible, and that there was zero chance of any
  * processing activity occuring before it got called.  That assumption appears incorrect, however, so I've opted to now
  * presume that processing has perhaps occurred.  Perfect rollback is thus no longer possible.
  *@param documentDescription is the description object for the document that was processed.
  *@param executeTime is the time that the document should be rescanned.
  *@param failTime is the time that the document should be considered to have failed, if it has not been
  * successfully read until then.
  */
  public void resetDocument(DocumentDescription documentDescription, long executeTime, int action, long failTime,
    int failCount)
    throws ManifoldCFException
  {
    resetDocumentMultiple(new DocumentDescription[]{documentDescription},executeTime,action,failTime,failCount);
  }

  /** Eliminate duplicates, and sort */
  protected static String[] eliminateDuplicates(String[] docIDHashes)
  {
    HashMap map = new HashMap();
    int i = 0;
    while (i < docIDHashes.length)
    {
      String docIDHash = docIDHashes[i++];
      map.put(docIDHash,docIDHash);
    }
    String[] rval = new String[map.size()];
    i = 0;
    Iterator iter = map.keySet().iterator();
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(rval);
    return rval;
  }

  /** Build a reorder map, describing how to convert an original index into a reordered index. */
  protected static HashMap buildReorderMap(String[] originalIDHashes, String[] reorderedIDHashes)
  {
    HashMap reorderSet = new HashMap();
    int i = 0;
    while (i < reorderedIDHashes.length)
    {
      String reorderedIDHash = reorderedIDHashes[i];
      Integer position = new Integer(i);
      reorderSet.put(reorderedIDHash,position);
      i++;
    }

    HashMap map = new HashMap();
    int j = 0;
    while (j < originalIDHashes.length)
    {
      String originalIDHash = originalIDHashes[j];
      Integer position = (Integer)reorderSet.get(originalIDHash);
      if (position != null)
      {
        map.put(new Integer(j),position);
        // Remove, so that only one of each duplicate will have a place in the map
        reorderSet.remove(originalIDHash);
      }
      j++;
    }

    return map;
  }

  /** Add an initial set of documents to the queue.
  * This method is called during job startup, when the queue is being loaded.
  * A set of document references is passed to this method, which updates the status of the document
  * in the specified job's queue, according to specific state rules.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDs are the local document identifiers.
  *@param overrideSchedule is true if any existing document schedule should be overridden.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  *@param currentTime is the current time in milliseconds since epoch.
  *@param documentPriorities are the document priorities corresponding to the document identifiers.
  *@param prereqEventNames are the events that must be completed before each document can be processed.
  *@return true if the priority value(s) were used, false otherwise.
  */
  public boolean[] addDocumentsInitial(Long jobID, String[] legalLinkTypes,
    String[] docIDHashes, String[] docIDs, boolean overrideSchedule,
    int hopcountMethod, long currentTime, double[] documentPriorities,
    String[][] prereqEventNames)
    throws ManifoldCFException
  {
    if (docIDHashes.length == 0)
      return new boolean[0];

    // The document identifiers need to be sorted in a consistent fashion to reduce deadlock, and have duplicates removed, before going ahead.
    // But, the documentPriorities and the return booleans need to correspond to the initial array.  So, after we come up with
    // our internal order, we need to construct a map that takes an original index and maps it to the reduced, reordered index.
    String[] reorderedDocIDHashes = eliminateDuplicates(docIDHashes);
    HashMap reorderMap = buildReorderMap(docIDHashes,reorderedDocIDHashes);
    double[] reorderedDocumentPriorities = new double[reorderedDocIDHashes.length];
    String[][] reorderedDocumentPrerequisites = new String[reorderedDocIDHashes.length][];
    String[] reorderedDocumentIdentifiers = new String[reorderedDocIDHashes.length];
    boolean[] rval = new boolean[docIDHashes.length];
    int i = 0;
    while (i < docIDHashes.length)
    {
      Integer newPosition = (Integer)reorderMap.get(new Integer(i));
      if (newPosition != null)
      {
        reorderedDocumentPriorities[newPosition.intValue()] = documentPriorities[i];
        if (prereqEventNames != null)
          reorderedDocumentPrerequisites[newPosition.intValue()] = prereqEventNames[i];
        else
          reorderedDocumentPrerequisites[newPosition.intValue()] = null;
        reorderedDocumentIdentifiers[newPosition.intValue()] = docIDs[i];
      }
      rval[i] = false;
      i++;
    }

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to add "+Integer.toString(reorderedDocIDHashes.length)+" initial docs and hopcounts for job "+jobID.toString());
    }

    // Postgres gets all screwed up if we permit multiple threads into the hopcount code, unless serialized
    // transactions are used.  But serialized transactions may require a retry in order
    // to resolve transaction conflicts.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to start adding "+Integer.toString(reorderedDocIDHashes.length)+
          " initial docs and hopcounts for job "+jobID.toString());

        // Go through document id's one at a time, in order - mainly to prevent deadlock as much as possible.  Search for any existing row in jobqueue first (for update)
        boolean[] reorderedRval = new boolean[reorderedDocIDHashes.length];
        int z = 0;
        while (z < reorderedDocIDHashes.length)
        {
          String docIDHash = reorderedDocIDHashes[z];
          double docPriority = reorderedDocumentPriorities[z];
          String docID = reorderedDocumentIdentifiers[z];
          String[] docPrereqs = reorderedDocumentPrerequisites[z];

          ArrayList list = new ArrayList();
          list.add(jobID);
          list.add(docIDHash);

          IResultSet set = database.performQuery("SELECT "+jobQueue.idField+","+jobQueue.statusField+","+
            jobQueue.checkTimeField+" FROM "+jobQueue.getTableName()+
            " WHERE "+jobQueue.jobIDField+"=? AND "+jobQueue.docHashField+"=? FOR UPDATE",list,null,null);

          boolean priorityUsed;
          long executeTime = overrideSchedule?0L:-1L;

          if (set.getRowCount() > 0)
          {
            // Found a row, and it is now locked.
            IResultRow row = set.getRow(0);

            // Decode the row
            Long rowID = (Long)row.getValue(jobQueue.idField);
            int status = jobQueue.stringToStatus((String)row.getValue(jobQueue.statusField));
            Long checkTimeValue = (Long)row.getValue(jobQueue.checkTimeField);

            priorityUsed = jobQueue.updateExistingRecordInitial(rowID,status,checkTimeValue,executeTime,currentTime,docPriority,docPrereqs);
          }
          else
          {
            // Not found.  Attempt an insert instead.  This may fail due to constraints, but if this happens, the whole transaction will be retried.
            jobQueue.insertNewRecordInitial(jobID,docIDHash,docID,docPriority,executeTime,currentTime,docPrereqs);
            priorityUsed = true;
          }

          reorderedRval[z++] = priorityUsed;
        }

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to add "+Integer.toString(reorderedDocIDHashes.length)+
          " initial docs for job "+jobID.toString());

        if (legalLinkTypes.length > 0)
          hopCount.recordSeedReferences(jobID,legalLinkTypes,reorderedDocIDHashes,hopcountMethod);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to add "+Integer.toString(reorderedDocIDHashes.length)+
          " initial docs and hopcounts for job "+jobID.toString());

        // Rejigger to correspond with calling order
        i = 0;
        while (i < docIDs.length)
        {
          Integer finalPosition = (Integer)reorderMap.get(new Integer(i));
          if (finalPosition != null)
            rval[i] = reorderedRval[finalPosition.intValue()];
          i++;
        }

        return rval;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction adding "+Integer.toString(reorderedDocIDHashes.length)+
            " initial docs for job "+jobID.toString()+": "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Add an initial set of remaining documents to the queue.
  * This method is called during job startup, when the queue is being loaded, to list documents that
  * were NOT included by calling addDocumentsInitial().  Documents listed here are simply designed to
  * enable the framework to get rid of old, invalid seeds.  They are not queued for processing.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the local document identifier hashes.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  */
  public void addRemainingDocumentsInitial(Long jobID, String[] legalLinkTypes, String[] docIDHashes,
    int hopcountMethod)
    throws ManifoldCFException
  {
    if (docIDHashes.length == 0)
      return;

    String[] reorderedDocIDHashes = eliminateDuplicates(docIDHashes);

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to add "+Integer.toString(reorderedDocIDHashes.length)+" remaining docs and hopcounts for job "+jobID.toString());
    }

    // Postgres gets all screwed up if we permit multiple threads into the hopcount code, unless the transactions are serialized,
    // and allows one transaction to see the effects of another transaction before it's been committed.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to start adding "+Integer.toString(reorderedDocIDHashes.length)+
          " remaining docs and hopcounts for job "+jobID.toString());

        jobQueue.addRemainingDocumentsInitial(jobID,reorderedDocIDHashes);
        if (legalLinkTypes.length > 0)
          hopCount.recordSeedReferences(jobID,legalLinkTypes,reorderedDocIDHashes,hopcountMethod);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to add "+Integer.toString(reorderedDocIDHashes.length)+
          " remaining docs and hopcounts for job "+jobID.toString());

      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction adding "+Integer.toString(reorderedDocIDHashes.length)+
            " remaining docs and hopcounts for job "+jobID.toString()+": "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Signal that a seeding pass has been done.
  * Call this method at the end of a seeding pass.  It is used to perform the bookkeeping necessary to
  * maintain the hopcount table.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param isPartial is set if the seeds provided are only a partial list.  Some connectors cannot
  *       supply a full list of seeds on every seeding iteration; this acknowledges that limitation.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  */
  public void doneDocumentsInitial(Long jobID, String[] legalLinkTypes, boolean isPartial,
    int hopcountMethod)
    throws ManifoldCFException
  {
    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to finish initial docs and hopcounts for job "+jobID.toString());
    }

    // Postgres gets all screwed up if we permit multiple threads into the hopcount code, unless serialized transactions are used.
    // and allows one transaction to see the effects of another transaction before it's been committed.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+
          " ms to start finishing initial docs and hopcounts for job "+jobID.toString());

        jobQueue.doneDocumentsInitial(jobID,isPartial);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+
          " ms to finish initial docs for job "+jobID.toString());

        if (legalLinkTypes.length > 0)
          hopCount.finishSeedReferences(jobID,legalLinkTypes,hopcountMethod);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+
          " ms to finish initial docs and hopcounts for job "+jobID.toString());
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction finishing initial docs and hopcounts for job "+jobID.toString()+": "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Get the specified hop counts, with the limit as described.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the hashes for the set of documents to find the hopcount for.
  *@param linkType is the kind of link to find the hopcount for.
  *@param limit is the limit, beyond which a negative distance may be returned.
  *@param hopcountMethod is the method for managing hopcounts that is in effect.
  *@return a vector of booleans corresponding to the documents requested.  A true value is returned
  * if the document is within the specified limit, false otherwise.
  */
  public boolean[] findHopCounts(Long jobID, String[] legalLinkTypes, String[] docIDHashes, String linkType, int limit,
    int hopcountMethod)
    throws ManifoldCFException
  {
    if (docIDHashes.length == 0)
      return new boolean[0];

    if (legalLinkTypes.length == 0)
      throw new ManifoldCFException("Nonsensical request; asking for hopcounts where none are kept");

    // The idea is to delay queue processing as much as possible, because that avoids having to wait
    // on locks and having to repeat our evaluations.
    //
    // Luckily, we can glean a lot of information from what's hanging around.  Specifically, whatever value
    // we find in the table is an upper bound on the true hop distance value.  So, only if we have documents
    // that are outside the limit does the queue need to be processed.
    //
    // It is therefore really helpful to write in an estimated value for any newly created record, if possible.  Even if the
    // estimate is possibly greater than the true value, a great deal of locking and queue processing will be
    // avoided.

    // The flow here is to:
    // - grab the right hoplock
    // - process the queue
    // - if the queue is empty, get the hopcounts we wanted, otherwise release the lock and loop around

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Beginning work to get "+Integer.toString(docIDHashes.length)+" hopcounts for job "+jobID.toString());
    }

    // Make an answer array.
    boolean[] rval = new boolean[docIDHashes.length];

    // Make a hash of what we still need a definitive answer for.
    HashMap badAnswers = new HashMap();
    int i = 0;
    while (i < rval.length)
    {
      String docIDHash = docIDHashes[i];
      rval[i] = false;
      badAnswers.put(docIDHash,new Integer(i));
      i++;
    }

    int iterationCount = 0;
    while (true)
    {
      // Ask for only about documents we don't have a definitive answer for yet.
      String[] askDocIDHashes = new String[badAnswers.size()];
      i = 0;
      Iterator iter = badAnswers.keySet().iterator();
      while (iter.hasNext())
      {
        askDocIDHashes[i++] = (String)iter.next();
      }

      int[] distances = hopCount.findHopCounts(jobID,askDocIDHashes,linkType);
      i = 0;
      while (i < distances.length)
      {
        int distance = distances[i];
        String docIDHash = askDocIDHashes[i];
        if (distance != -1 && distance <= limit)
        {
          // Found a usable value
          rval[((Integer)badAnswers.remove(docIDHash)).intValue()] = true;
        }
        i++;
      }

      if (Logging.perf.isDebugEnabled())
        Logging.perf.debug("Iteration "+Integer.toString(iterationCount++)+": After initial check, "+Integer.toString(badAnswers.size())+
        " hopcounts remain to be found for job "+jobID.toString()+", out of "+Integer.toString(docIDHashes.length)+
        " ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

      if (badAnswers.size() == 0)
        return rval;

      // It appears we need to process the queue.  We need to enter the hoplock section
      // to make sure only one player is updating values at a time.  Then, before we exit, we get the
      // remaining values.

      askDocIDHashes = new String[badAnswers.size()];
      i = 0;
      iter = badAnswers.keySet().iterator();
      while (iter.hasNext())
      {
        askDocIDHashes[i++] = (String)iter.next();
      }

      // Currently, only one thread can possibly process any of the queue at a given time.  This is because the queue marks are not set to something
      // other than than the "in queue" value during processing.  My instinct is that queue processing is likely to interfere with other queue processing,
      // so I've taken the route of prohibiting more than one batch of queue processing at a time, for now.

      String hopLockName = getHopLockName(jobID);
      long sleepAmt = 0L;
      lockManager.enterWriteLock(hopLockName);
      try
      {
        database.beginTransaction(database.TRANSACTION_SERIALIZED);
        try
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Processing queue for job "+jobID.toString()+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

          // The internal queue processing only does 200 at a time.  This is a compromise between maximum efficiency (bigger number)
          // and the requirement that database writes are effectively blocked for a while (which argues for a smaller number).
          boolean definitive = hopCount.processQueue(jobID,legalLinkTypes,hopcountMethod);
          // If definitive answers were not found, we leave the lock and go back to check on the status of the questions we were
          // interested in.  If the answers are all OK then we are done; if not, we need to process more queue, and keep doing that
          // until we really ARE done.
          if (!definitive)
          {
            // Sleep a little bit so another thread can have a whack at things
            sleepAmt = 100L;
            continue;
          }

          // Definitive answers found; continue through.
          distances = hopCount.findHopCounts(jobID,askDocIDHashes,linkType);
        }
        catch (ManifoldCFException e)
        {
          database.signalRollback();
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            if (Logging.perf.isDebugEnabled())
              Logging.perf.debug("Aborted transaction processing queue for job "+jobID.toString()+": "+e.getMessage());
            sleepAmt = getRandomAmount();
            continue;
          }
          throw e;
        }
        catch (Error e)
        {
          database.signalRollback();
          throw e;
        }
        finally
        {
          database.endTransaction();
        }
      }
      finally
      {
        lockManager.leaveWriteLock(hopLockName);
        sleepFor(sleepAmt);
      }

      if (Logging.perf.isDebugEnabled())
        Logging.perf.debug("Definitive answers found for "+Integer.toString(docIDHashes.length)+
        " hopcounts for job "+jobID.toString()+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

      // All answers are guaranteed to be accurate now.
      i = 0;
      while (i < distances.length)
      {
        int distance = distances[i];
        String docIDHash = askDocIDHashes[i];
        if (distance != -1 && distance <= limit)
        {
          // Found a usable value
          rval[((Integer)badAnswers.remove(docIDHash)).intValue()] = true;
        }
        i++;
      }
      return rval;
    }
  }

  /** Get all the current seeds.
  * Returns the seed document identifiers for a job.
  *@param jobID is the job identifier.
  *@return the document identifiers that are currently considered to be seeds.
  */
  public String[] getAllSeeds(Long jobID)
    throws ManifoldCFException
  {
    return jobQueue.getAllSeeds(jobID);
  }

  /** Add documents to the queue in bulk.
  * This method is called during document processing, when a set of document references are discovered.
  * The document references are passed to this method, which updates the status of the document(s)
  * in the specified job's queue, according to specific state rules.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the local document identifier hashes.
  *@param parentIdentifierHash is the optional parent identifier hash of this document.  Pass null if none.
  *@param relationshipType is the optional link type between this document and its parent.  Pass null if there
  *       is no relationship with a parent.
  *@param hopcountMethod is the desired method for managing hopcounts.
  *@param dataNames are the names of the data to carry down to the child from this parent.
  *@param dataValues are the values to carry down to the child from this parent, corresponding to dataNames above.  If CharacterInput objects are passed in here,
  *       it is the caller's responsibility to clean these up.
  *@param currentTime is the time in milliseconds since epoch that will be recorded for this operation.
  *@param documentPriorities are the desired document priorities for the documents.
  *@param prereqEventNames are the events that must be completed before a document can be queued.
  *@return an array of boolean values indicating whether or not the passed-in priority value was used or not for each doc id (true if used).
  */
  public boolean[] addDocuments(Long jobID, String[] legalLinkTypes,
    String[] docIDHashes, String[] docIDs,
    String parentIdentifierHash, String relationshipType,
    int hopcountMethod, String[][] dataNames, Object[][][] dataValues,
    long currentTime, double[] documentPriorities,
    String[][] prereqEventNames)
    throws ManifoldCFException
  {
    if (docIDs.length == 0)
      return new boolean[0];

    // Sort the id hashes and eliminate duplicates.  This will help avoid deadlock conditions.
    // However, we also need to keep the carrydown data in synch, so track that around as well, and merge if there are
    // duplicate document identifiers.
    HashMap nameMap = new HashMap();
    int k = 0;
    while (k < docIDHashes.length)
    {
      String docIDHash = docIDHashes[k];
      // If there are duplicates, we need to merge them.
      HashMap names = (HashMap)nameMap.get(docIDHash);
      if (names == null)
      {
        names = new HashMap();
        nameMap.put(docIDHash,names);
      }

      String[] nameList = dataNames[k];
      Object[][] dataList = dataValues[k];

      int z = 0;
      while (z < nameList.length)
      {
        String name = nameList[z];
        Object[] values = dataList[z];
        HashMap valueMap = (HashMap)names.get(name);
        if (valueMap == null)
        {
          valueMap = new HashMap();
          names.put(name,valueMap);
        }
        int y = 0;
        while (y < values.length)
        {
          // Calculate the value hash; that's the true key, and the one that cannot be duplicated.
          String valueHash;
          if (values[y] instanceof CharacterInput)
          {
            // It's a CharacterInput object.
            valueHash = ((CharacterInput)values[y]).getHashValue();
          }
          else
          {
            // It better be a String.
            valueHash = ManifoldCF.hash((String)values[y]);
          }
          valueMap.put(valueHash,values[y]);
          y++;
        }
        z++;
      }
      k++;
    }

    String[] reorderedDocIDHashes = eliminateDuplicates(docIDHashes);
    HashMap reorderMap = buildReorderMap(docIDHashes,reorderedDocIDHashes);
    double[] reorderedDocumentPriorities = new double[reorderedDocIDHashes.length];
    String[][] reorderedDocumentPrerequisites = new String[reorderedDocIDHashes.length][];
    String[] reorderedDocumentIdentifiers = new String[reorderedDocIDHashes.length];
    boolean[] rval = new boolean[docIDHashes.length];
    int i = 0;
    while (i < docIDHashes.length)
    {
      Integer newPosition = (Integer)reorderMap.get(new Integer(i));
      if (newPosition != null)
      {
        reorderedDocumentPriorities[newPosition.intValue()] = documentPriorities[i];
        if (prereqEventNames != null)
          reorderedDocumentPrerequisites[newPosition.intValue()] = prereqEventNames[i];
        else
          reorderedDocumentPrerequisites[newPosition.intValue()] = null;
        reorderedDocumentIdentifiers[newPosition.intValue()] = docIDs[i];
      }
      rval[i] = false;
      i++;
    }

    dataNames = new String[reorderedDocIDHashes.length][];
    String[][][] dataHashValues = new String[reorderedDocIDHashes.length][][];
    dataValues = new Object[reorderedDocIDHashes.length][][];

    k = 0;
    while (k < reorderedDocIDHashes.length)
    {
      String docIDHash = reorderedDocIDHashes[k];
      HashMap names = (HashMap)nameMap.get(docIDHash);
      dataNames[k] = new String[names.size()];
      dataHashValues[k] = new String[names.size()][];
      dataValues[k] = new Object[names.size()][];
      Iterator iter = names.keySet().iterator();
      int z = 0;
      while (iter.hasNext())
      {
        String dataName = (String)iter.next();
        (dataNames[k])[z] = dataName;
        HashMap values = (HashMap)names.get(dataName);
        (dataHashValues[k])[z] = new String[values.size()];
        (dataValues[k])[z] = new Object[values.size()];
        Iterator iter2 = values.keySet().iterator();
        int y = 0;
        while (iter2.hasNext())
        {
          String dataValueHash = (String)iter2.next();
          Object dataValue = values.get(dataValueHash);
          ((dataHashValues[k])[z])[y] = dataValueHash;
          ((dataValues[k])[z])[y] = dataValue;
          y++;
        }
        z++;
      }
      k++;
    }

    long startTime = 0L;
    if (Logging.perf.isDebugEnabled())
    {
      startTime = System.currentTimeMillis();
      Logging.perf.debug("Waiting to add "+Integer.toString(reorderedDocIDHashes.length)+" docs and hopcounts for job "+jobID.toString()+" parent identifier "+parentIdentifierHash);
    }

    // Postgres gets all screwed up if we permit multiple threads into the hopcount code,
    // and allows one transaction to see the effects of another transaction before it's been committed.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to start adding "+Integer.toString(reorderedDocIDHashes.length)+
          " docs and hopcounts for job "+jobID.toString()+" parent identifier hash "+parentIdentifierHash);

        // Go through document id's one at a time, in order - mainly to prevent deadlock as much as possible.  Search for any existing row in jobqueue first (for update)
        HashMap existingRows = new HashMap();

        int z = 0;
        while (z < reorderedDocIDHashes.length)
        {
          String docIDHash = reorderedDocIDHashes[z];

          ArrayList list = new ArrayList();
          list.add(jobID);
          list.add(docIDHash);

          IResultSet set = database.performQuery("SELECT "+jobQueue.idField+","+jobQueue.statusField+","+
            jobQueue.checkTimeField+" FROM "+jobQueue.getTableName()+
            " WHERE "+jobQueue.jobIDField+"=? AND "+jobQueue.docHashField+"=? FOR UPDATE",list,null,null);

          boolean priorityUsed;

          if (set.getRowCount() > 0)
          {
            // Found a row, and it is now locked.
            IResultRow row = set.getRow(0);

            // Decode the row
            Long rowID = (Long)row.getValue(jobQueue.idField);
            int status = jobQueue.stringToStatus((String)row.getValue(jobQueue.statusField));
            Long checkTimeValue = (Long)row.getValue(jobQueue.checkTimeField);

            existingRows.put(docIDHash,new JobqueueRecord(rowID,status,checkTimeValue));
          }
          else
          {
            // Not found.  Attempt an insert instead.  This may fail due to constraints, but if this happens, the whole transaction will be retried.
            jobQueue.insertNewRecord(jobID,docIDHash,reorderedDocumentIdentifiers[z],reorderedDocumentPriorities[z],0L,currentTime,reorderedDocumentPrerequisites[z]);
          }

          z++;
        }

        // Update all the carrydown data at once, for greatest efficiency.
        boolean[] carrydownChangesSeen = carryDown.recordCarrydownDataMultiple(jobID,parentIdentifierHash,reorderedDocIDHashes,dataNames,dataHashValues,dataValues);

        // Loop through the document id's again, and perform updates where needed
        boolean[] reorderedRval = new boolean[reorderedDocIDHashes.length];

        z = 0;
        while (z < reorderedDocIDHashes.length)
        {
          String docIDHash = reorderedDocIDHashes[z];
          JobqueueRecord jr = (JobqueueRecord)existingRows.get(docIDHash);
          if (jr == null)
            // It was an insert
            reorderedRval[z] = true;
          else
            // It was an existing row; do the update logic
            reorderedRval[z] = jobQueue.updateExistingRecord(jr.getRecordID(),jr.getStatus(),jr.getCheckTimeValue(),
            0L,currentTime,carrydownChangesSeen[z],reorderedDocumentPriorities[z],reorderedDocumentPrerequisites[z]);
          z++;
        }

        if (parentIdentifierHash != null && relationshipType != null)
          hopCount.recordReferences(jobID,legalLinkTypes,parentIdentifierHash,reorderedDocIDHashes,relationshipType,hopcountMethod);

        if (Logging.perf.isDebugEnabled())
          Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to add "+Integer.toString(reorderedDocIDHashes.length)+
          " docs and hopcounts for job "+jobID.toString()+" parent identifier hash "+parentIdentifierHash);

        i = 0;
        while (i < docIDHashes.length)
        {
          Integer finalPosition = (Integer)reorderMap.get(new Integer(i));
          if (finalPosition != null)
            rval[i] = reorderedRval[finalPosition.intValue()];
          i++;
        }

        return rval;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          sleepAmt = getRandomAmount();
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction adding "+Integer.toString(reorderedDocIDHashes.length)+
            " docs and hopcounts for job "+jobID.toString()+" parent identifier hash "+parentIdentifierHash+": "+e.getMessage()+"; sleeping for "+new Long(sleepAmt).toString()+" ms",e);
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }


  /** Add a document to the queue.
  * This method is called during document processing, when a document reference is discovered.
  * The document reference is passed to this method, which updates the status of the document
  * in the specified job's queue, according to specific state rules.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHash is the local document identifier hash value.
  *@param parentIdentifierHash is the optional parent identifier hash of this document.  Pass null if none.
  *@param relationshipType is the optional link type between this document and its parent.  Pass null if there
  *       is no relationship with a parent.
  *@param hopcountMethod is the desired method for managing hopcounts.
  *@param dataNames are the names of the data to carry down to the child from this parent.
  *@param dataValues are the values to carry down to the child from this parent, corresponding to dataNames above.
  *@param currentTime is the time in milliseconds since epoch that will be recorded for this operation.
  *@param priority is the desired document priority for the document.
  *@param prereqEventNames are the events that must be completed before the document can be processed.
  *@return true if the priority value was used, false otherwise.
  */
  public boolean addDocument(Long jobID, String[] legalLinkTypes, String docIDHash, String docID,
    String parentIdentifierHash, String relationshipType,
    int hopcountMethod, String[] dataNames, Object[][] dataValues,
    long currentTime, double priority, String[] prereqEventNames)
    throws ManifoldCFException
  {
    return addDocuments(jobID,legalLinkTypes,
      new String[]{docIDHash},new String[]{docID},
      parentIdentifierHash,relationshipType,hopcountMethod,new String[][]{dataNames},
      new Object[][][]{dataValues},currentTime,new double[]{priority},new String[][]{prereqEventNames})[0];
  }

  /** Complete adding child documents to the queue, for a set of documents.
  * This method is called at the end of document processing, to help the hopcount tracking engine do its bookkeeping.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param parentIdentifierHashes are the document identifier hashes for whom child link extraction just took place.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] finishDocuments(Long jobID, String[] legalLinkTypes, String[] parentIdentifierHashes, int hopcountMethod)
    throws ManifoldCFException
  {
    if (parentIdentifierHashes.length == 0)
      return new DocumentDescription[0];

    DocumentDescription[] rval;

    if (legalLinkTypes.length == 0)
    {
      // Must at least end the carrydown transaction.  By itself, this does not need a serialized transaction; however, occasional
      // deadlock is possible when a document shares multiple parents, so do the whole retry drill
      while (true)
      {
        long sleepAmt = 0L;
        database.beginTransaction(database.TRANSACTION_SERIALIZED);
        try
        {
          // A certain set of carrydown records are going to be deleted by the ensuing restoreRecords command.  Calculate that set of records!
          rval = calculateAffectedRestoreCarrydownChildren(jobID,parentIdentifierHashes);
          carryDown.restoreRecords(jobID,parentIdentifierHashes);
          break;
        }
        catch (ManifoldCFException e)
        {
          database.signalRollback();
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            if (Logging.perf.isDebugEnabled())
              Logging.perf.debug("Aborted transaction finishing "+
              Integer.toString(parentIdentifierHashes.length)+" doc carrydown records for job "+jobID.toString()+": "+e.getMessage());
            sleepAmt = getRandomAmount();
            continue;
          }
          throw e;
        }
        catch (Error e)
        {
          database.signalRollback();
          throw e;
        }
        finally
        {
          database.endTransaction();
          sleepFor(sleepAmt);
        }
      }
    }
    else
    {
      long startTime = 0L;
      if (Logging.perf.isDebugEnabled())
      {
        startTime = System.currentTimeMillis();
        Logging.perf.debug("Waiting to finish "+Integer.toString(parentIdentifierHashes.length)+" doc hopcounts for job "+jobID.toString());
      }

      // Postgres gets all screwed up if we permit multiple threads into the hopcount code,
      // and allows one transaction to see the effects of another transaction before it's been committed.
      while (true)
      {
        long sleepAmt = 0L;
        database.beginTransaction(database.TRANSACTION_SERIALIZED);
        try
        {
          // A certain set of carrydown records are going to be deleted by the ensuing restoreRecords command.  Calculate that set of records!
          rval = calculateAffectedRestoreCarrydownChildren(jobID,parentIdentifierHashes);

          carryDown.restoreRecords(jobID,parentIdentifierHashes);

          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Waited "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to start finishing "+
            Integer.toString(parentIdentifierHashes.length)+" doc hopcounts for job "+jobID.toString());

          hopCount.finishParents(jobID,legalLinkTypes,parentIdentifierHashes,hopcountMethod);

          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Took "+new Long(System.currentTimeMillis()-startTime).toString()+" ms to finish "+
            Integer.toString(parentIdentifierHashes.length)+" doc hopcounts for job "+jobID.toString());
          break;
        }
        catch (ManifoldCFException e)
        {
          database.signalRollback();
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            if (Logging.perf.isDebugEnabled())
              Logging.perf.debug("Aborted transaction finishing "+
              Integer.toString(parentIdentifierHashes.length)+" doc hopcounts for job "+jobID.toString()+": "+e.getMessage());
            sleepAmt = getRandomAmount();
            continue;
          }
          throw e;
        }
        catch (Error e)
        {
          database.signalRollback();
          throw e;
        }
        finally
        {
          database.endTransaction();
          sleepFor(sleepAmt);
        }
      }
    }
    return rval;
  }

  /** Helper method: Calculate the unique set of affected carrydown children resulting from a "restoreRecords" operation.
  */
  protected DocumentDescription[] calculateAffectedRestoreCarrydownChildren(Long jobID, String[] parentIDHashes)
    throws ManifoldCFException
  {
    // We are going to need to break up this query into a number of subqueries, each covering a subset of parent id hashes.
    // The goal is to throw all the children into a hash, to make them unique at the end.
    HashMap resultHash = new HashMap();
    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    int maxCount = 25;
    int i = 0;
    int z = 0;
    while (i < parentIDHashes.length)
    {
      if (z == maxCount)
      {
        processParentHashSet(jobID,resultHash,sb.toString(),list);
        list.clear();
        sb.setLength(0);
        z = 0;
      }
      if (z > 0)
        sb.append(",");
      sb.append("?");
      list.add(parentIDHashes[i]);
      i++;
      z++;
    }

    if (z > 0)
      processParentHashSet(jobID,resultHash,sb.toString(),list);

    // Now, put together the result document list from the hash.
    DocumentDescription[] rval = new DocumentDescription[resultHash.size()];
    i = 0;
    Iterator iter = resultHash.keySet().iterator();
    while (iter.hasNext())
    {
      Long id = (Long)iter.next();
      DocumentDescription dd = (DocumentDescription)resultHash.get(id);
      rval[i++] = dd;
    }
    return rval;
  }

  /** Helper method: look up rows affected by a restoreRecords operation.
  */
  protected void processParentHashSet(Long jobID, HashMap resultHash, String queryPart, ArrayList list)
    throws ManifoldCFException
  {
    // The query here mirrors the carrydown.restoreRecords() delete query!  However, it also fetches enough information to build a DocumentDescription
    // object for return, and so a join is necessary against the jobqueue table.
    //???
    String query = "SELECT t0."+jobQueue.idField+",t0."+jobQueue.docHashField+",t0."+jobQueue.docIDField+" FROM "+
      jobQueue.getTableName()+" t0 WHERE EXISTS(SELECT 'x' FROM "+carryDown.getTableName()+
      " t1 WHERE "+carryDown.parentIDHashField+" IN ("+queryPart+") AND t1."+carryDown.childIDHashField+"=t0."+jobQueue.docHashField+
      " AND t0."+jobQueue.jobIDField+"=? AND t1."+jobQueue.jobIDField+"=? AND t1."+carryDown.newField+"=?)";
    list.add(jobID);
    list.add(jobID);
    list.add(carryDown.statusToString(carryDown.ISNEW_BASE));

    IResultSet set = database.performQuery(query,list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long id = (Long)row.getValue(jobQueue.idField);
      String documentIdentifierHash = (String)row.getValue(jobQueue.docHashField);
      String documentIdentifier = (String)row.getValue(jobQueue.docIDField);
      resultHash.put(id,new DocumentDescription(id,jobID,documentIdentifierHash,documentIdentifier));
    }
  }

  /** Begin an event sequence.
  *@param eventName is the name of the event.
  *@return true if the event could be created, or false if it's already there.
  */
  public boolean beginEventSequence(String eventName)
    throws ManifoldCFException
  {
    try
    {
      eventManager.createEvent(eventName);
      return true;
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        return false;
      throw e;
    }
  }

  /** Complete an event sequence.
  *@param eventName is the name of the event.
  */
  public void completeEventSequence(String eventName)
    throws ManifoldCFException
  {
    eventManager.destroyEvent(eventName);
  }


  /** Requeue a document set because of carrydown changes.
  * This method is called when carrydown data is modified for a set of documents.  The documents must be requeued for immediate reprocessing, even to the
  * extent that if one is *already* being processed, it will need to be done over again.
  *@param documentDescriptions is the set of description objects for the documents that have had their parent carrydown information changed.
  *@param docPriorities are the document priorities to assign to the documents, if needed.
  *@return a flag for each document priority, true if it was used, false otherwise.
  */
  public boolean[] carrydownChangeDocumentMultiple(DocumentDescription[] documentDescriptions, long currentTime, double[] docPriorities)
    throws ManifoldCFException
  {
    if (documentDescriptions.length == 0)
      return new boolean[0];

    // Order the updates by document hash, to prevent deadlock as much as possible.

    // This map contains the original index of the document id hash.
    HashMap docHashMap = new HashMap();

    String[] docIDHashes = new String[documentDescriptions.length];
    int i = 0;
    while (i < documentDescriptions.length)
    {
      docIDHashes[i] = documentDescriptions[i].getDocumentIdentifier() + ":" + documentDescriptions[i].getJobID();
      docHashMap.put(docIDHashes[i],new Integer(i));
      i++;
    }

    // Sort the hashes
    java.util.Arrays.sort(docIDHashes);

    boolean[] rval = new boolean[docIDHashes.length];

    // Enter transaction and prepare to look up document states in dochash order
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        // This is the map that will contain the rows we found, keyed by docIDHash.
        HashMap existingRows = new HashMap();

        // Loop through hashes in order
        int j = 0;
        while (j < docIDHashes.length)
        {
          String docIDHash = docIDHashes[j];
          // Get the index
          int originalIndex = ((Integer)docHashMap.get(docIDHash)).intValue();
          // Lookup document description
          DocumentDescription dd = documentDescriptions[originalIndex];
          // Do the query.  We can base this on the id column since we have that.
          ArrayList list = new ArrayList();
          list.add(dd.getID());
          IResultSet set = database.performQuery("SELECT "+jobQueue.idField+","+jobQueue.statusField+","+
            jobQueue.checkTimeField+" FROM "+jobQueue.getTableName()+
            " WHERE "+jobQueue.idField+"=? FOR UPDATE",list,null,null);
          // If the row is there, we use its current info to requeue it properly.
          if (set.getRowCount() > 0)
          {
            // Found a row, and it is now locked.
            IResultRow row = set.getRow(0);

            // Decode the row
            Long rowID = (Long)row.getValue(jobQueue.idField);
            int status = jobQueue.stringToStatus((String)row.getValue(jobQueue.statusField));
            Long checkTimeValue = (Long)row.getValue(jobQueue.checkTimeField);

            existingRows.put(docIDHash,new JobqueueRecord(rowID,status,checkTimeValue));
          }
          j++;
        }

        // Ok, existingRows contains all the rows we want to try to update.  Go through these and update.
        while (j < docIDHashes.length)
        {
          String docIDHash = docIDHashes[j];
          int originalIndex = ((Integer)docHashMap.get(docIDHash)).intValue();

          JobqueueRecord jr = (JobqueueRecord)existingRows.get(docIDHash);
          if (jr == null)
            // It wasn't found, so the doc priority wasn't used.
            rval[originalIndex] = false;
          else
            // It was an existing row; do the update logic; use the 'carrydown changes' flag = true all the time.
            rval[originalIndex] = jobQueue.updateExistingRecord(jr.getRecordID(),jr.getStatus(),jr.getCheckTimeValue(),
            0L,currentTime,true,docPriorities[originalIndex],null);
          j++;
        }
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction handling "+Integer.toString(docIDHashes.length)+" carrydown changes: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
    return rval;
  }

  /** Requeue a document because of carrydown changes.
  * This method is called when carrydown data is modified for a document.  The document must be requeued for immediate reprocessing, even to the
  * extent that if it is *already* being processed, it will need to be done over again.
  *@param documentDescription is the description object for the document that has had its parent carrydown information changed.
  *@param docPriority is the document priority to assign to the document, if needed.
  *@return a flag for the document priority, true if it was used, false otherwise.
  */
  public boolean carrydownChangeDocument(DocumentDescription documentDescription, long currentTime, double docPriority)
    throws ManifoldCFException
  {
    return carrydownChangeDocumentMultiple(new DocumentDescription[]{documentDescription},currentTime,new double[]{docPriority})[0];
  }

  /** Sleep a random amount of time after a transaction abort.
  */
  protected long getRandomAmount()
  {
    // Amount should be between .5 and 1 minute, approx, to give things time to unwind
    return (long)(random.nextDouble() * 60000.0 + 500.0);
  }

  protected void sleepFor(long amt)
    throws ManifoldCFException
  {
    if (amt == 0L)
      return;

    try
    {
      ManifoldCF.sleep(amt);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Retrieve specific parent data for a given document.
  *@param jobID is the job identifier.
  *@param docIDHash is the document identifier hash value.
  *@param dataName is the kind of data to retrieve.
  *@return the unique data values.
  */
  public String[] retrieveParentData(Long jobID, String docIDHash, String dataName)
    throws ManifoldCFException
  {
    return carryDown.getDataValues(jobID,docIDHash,dataName);
  }

  /** Retrieve specific parent data for a given document.
  *@param jobID is the job identifier.
  *@param docIDHash is the document identifier hash value.
  *@param dataName is the kind of data to retrieve.
  *@return the unique data values.
  */
  public CharacterInput[] retrieveParentDataAsFiles(Long jobID, String docIDHash, String dataName)
    throws ManifoldCFException
  {
    return carryDown.getDataValuesAsFiles(jobID,docIDHash,dataName);
  }

  // These methods support the job threads (which start jobs and end jobs)
  // There is one thread that starts jobs.  It simply looks for jobs which are ready to
  // start, and changes their state accordingly.
  // There is also a pool of threads that end jobs.  These threads wait for a job that
  // looks like it is done, and do completion processing if it is.

  /** Start all jobs in need of starting.
  * This method marks all the appropriate jobs as "in progress", which is all that should be
  * needed to start them.
  * It's also the case that the start event should be logged in the event log.  In order to make it possible for
  * the caller to do this logging, a set of job ID's will be returned containing the jobs that
  * were started.
  *@param currentTime is the current time in milliseconds since epoch.
  *@param unwaitList is filled in with the set of job ID objects that were resumed.
  */
  public void startJobs(long currentTime, ArrayList unwaitList)
    throws ManifoldCFException
  {
    // This method should compare the lasttime field against the current time, for all
    // "not active" jobs, and see if a job should be started.
    //
    // If a job is to be started, then the following occurs:
    // (1) If the job is "full scan", then all COMPLETED jobqueue entries are converted to
    //     PURGATORY.
    // (2) The job is labeled as "ACTIVE".
    // (3) The starttime field is set.
    // (4) The endtime field is nulled out.
    //
    // This method also assesses jobs that are ACTIVE or PAUSED to see if they should be
    // converted to ACTIVEWAIT or PAUSEDWAIT.  This would happen if the current time exceeded
    // the value in the "windowend" field for the job.
    //
    // Finally, jobs in ACTIVEWAIT or PAUSEDWAIT are assessed to see if they should become
    // ACTIVE or PAUSED.  This will occur if we have entered a new window for the job.

    // Note well: We can't combine locks across both our lock manager and the database unless we do it consistently.  The
    // consistent practice throughout CF is to do the external locks first, then the database locks.  This particular method
    // thus cannot use cached job description information, because it must throw database locks first against the jobs table.
    database.beginTransaction();
    try
    {
      // First, query the appropriate fields of all jobs.
      ArrayList list = new ArrayList();
      list.add(jobs.statusToString(jobs.STATUS_INACTIVE));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVEWAIT));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVEWAITSEEDING));
      list.add(jobs.statusToString(jobs.STATUS_PAUSEDWAIT));
      list.add(jobs.statusToString(jobs.STATUS_PAUSEDWAITSEEDING));
      
      list.add(jobs.startMethodToString(IJobDescription.START_DISABLE));
      
      IResultSet set = database.performQuery("SELECT "+
        jobs.idField+","+
        jobs.lastTimeField+","+
        jobs.statusField+","+
        jobs.startMethodField+","+
        jobs.outputNameField+","+
        jobs.connectionNameField+
        " FROM "+jobs.getTableName()+" WHERE "+
        jobs.statusField+" IN (?,?,?,?,?) AND "+
        jobs.startMethodField+"!=? FOR UPDATE",
        list,null,null);

      // Next, we query for the schedule information.  In order to do that, we amass a list of job identifiers that we want schedule info
      // for.
      Long[] jobIDSet = new Long[set.getRowCount()];
      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i);
        jobIDSet[i++] = (Long)row.getValue(jobs.idField);
      }

      ScheduleRecord[][] srSet = jobs.readScheduleRecords(jobIDSet);

      i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i);

        Long jobID = (Long)row.getValue(jobs.idField);
        int startMethod = jobs.stringToStartMethod((String)row.getValue(jobs.startMethodField));
        String outputName = (String)row.getValue(jobs.outputNameField);
        String connectionName = (String)row.getValue(jobs.connectionNameField);
        ScheduleRecord[] thisSchedule = srSet[i++];

        // Run at specific times

        // We need to start with the start time as given, plus one
        long startInterval = ((Long)row.getValue(jobs.lastTimeField)).longValue() + 1;
        if (Logging.jobs.isDebugEnabled())
          Logging.jobs.debug("Checking if job "+jobID.toString()+" needs to be started; it was last checked at "+
          new Long(startInterval).toString()+", and now it is "+new Long(currentTime).toString());

        // Proceed to the current time, and find a match if there is one to be found.
        // If not -> continue

        // We go through *all* the schedule records.  The one that matches that has the latest
        // end time is the one we take.
        int l = 0;
        Long matchTime = null;
        Long duration = null;
        while (l < thisSchedule.length)
        {
          long trialStartInterval = startInterval;
          ScheduleRecord sr = thisSchedule[l++];
          Long thisDuration = sr.getDuration();
          if (startMethod == IJobDescription.START_WINDOWINSIDE &&
            thisDuration != null)
          {
            // Bump the start interval back before the beginning of the current interval.
            // This will guarantee a start as long as there is time in the window.
            long trialStart = currentTime - thisDuration.longValue();
            if (trialStart < trialStartInterval)
              trialStartInterval = trialStart;
          }

          Long thisMatchTime = checkTimeMatch(trialStartInterval,currentTime,
            sr.getDayOfWeek(),
            sr.getDayOfMonth(),
            sr.getMonthOfYear(),
            sr.getYear(),
            sr.getHourOfDay(),
            sr.getMinutesOfHour(),
            sr.getTimezone(),
            thisDuration);

          if (thisMatchTime == null)
          {
            if (Logging.jobs.isDebugEnabled())
              Logging.jobs.debug(" No time match found within interval "+new Long(trialStartInterval).toString()+
              " to "+new Long(currentTime).toString());
            continue;
          }

          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug(" Time match FOUND within interval "+new Long(trialStartInterval).toString()+
            " to "+new Long(currentTime).toString());

          if (matchTime == null || thisDuration == null ||
            (duration != null && thisMatchTime.longValue() + thisDuration.longValue() >
          matchTime.longValue() + duration.longValue()))
          {
            matchTime = thisMatchTime;
            duration = thisDuration;
          }
        }

        if (matchTime == null)
        {
          jobs.updateLastTime(jobID,currentTime);
          continue;
        }

        int status = jobs.stringToStatus(row.getValue(jobs.statusField).toString());


        // Calculate the end of the window
        Long windowEnd = null;
        if (duration != null)
        {
          windowEnd = new Long(matchTime.longValue()+duration.longValue());
        }

        if (Logging.jobs.isDebugEnabled())
        {
          Logging.jobs.debug("Job '"+jobID+"' is within run window at "+new Long(currentTime).toString()+" ms. (which starts at "+
            matchTime.toString()+" ms."+((duration==null)?"":(" and goes for "+duration.toString()+" ms."))+")");
        }

        int newJobState;
        switch (status)
        {
        case Jobs.STATUS_INACTIVE:
          // If job was formerly "inactive", do the full startup.
          // Start this job!  but with no end time.
          // This does not get logged because the startup thread does the logging.
          jobs.startJob(jobID,windowEnd);
          jobQueue.clearFailTimes(jobID);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Signalled for job start for job "+jobID);
          }
          break;
        case Jobs.STATUS_ACTIVEWAIT:
          unwaitList.add(jobID);
          if (connectionMgr.checkConnectorExists(connectionName))
          {
            if (outputMgr.checkConnectorExists(outputName))
              newJobState = jobs.STATUS_ACTIVE;
            else
              newJobState = jobs.STATUS_ACTIVE_NOOUTPUT;
          }
          else
          {
            if (outputMgr.checkConnectorExists(outputName))
              newJobState = jobs.STATUS_ACTIVE_UNINSTALLED;
            else
              newJobState = jobs.STATUS_ACTIVE_NEITHER;
          }
          jobs.unwaitJob(jobID,newJobState,windowEnd);
          jobQueue.clearFailTimes(jobID);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Un-waited job "+jobID);
          }
          break;
        case Jobs.STATUS_ACTIVEWAITSEEDING:
          unwaitList.add(jobID);
          if (connectionMgr.checkConnectorExists(connectionName))
          {
            if (outputMgr.checkConnectorExists(outputName))
              newJobState = jobs.STATUS_ACTIVESEEDING;
            else
              newJobState = jobs.STATUS_ACTIVESEEDING_NOOUTPUT;
          }
          else
          {
            if (outputMgr.checkConnectorExists(outputName))
              newJobState = jobs.STATUS_ACTIVESEEDING_UNINSTALLED;
            else
              newJobState = jobs.STATUS_ACTIVESEEDING_NEITHER;
          }
          jobs.unwaitJob(jobID,newJobState,windowEnd);
          jobQueue.clearFailTimes(jobID);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Un-waited job "+jobID);
          }
          break;
        case Jobs.STATUS_PAUSEDWAIT:
          unwaitList.add(jobID);
          jobs.unwaitJob(jobID,jobs.STATUS_PAUSED,windowEnd);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Un-waited (but still paused) job "+jobID);
          }
          break;
        case Jobs.STATUS_PAUSEDWAITSEEDING:
          unwaitList.add(jobID);
          jobs.unwaitJob(jobID,jobs.STATUS_PAUSEDSEEDING,windowEnd);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Un-waited (but still paused) job "+jobID);
          }
          break;
        default:
          break;
        }

      }
    }
    catch (ManifoldCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }
  }

  /** Put active or paused jobs in wait state, if they've exceeded their window.
  *@param currentTime is the current time in milliseconds since epoch.
  *@param waitList is filled in with the set of job ID's that were put into a wait state.
  */
  public void waitJobs(long currentTime, ArrayList waitList)
    throws ManifoldCFException
  {
    // This method assesses jobs that are ACTIVE or PAUSED to see if they should be
    // converted to ACTIVEWAIT or PAUSEDWAIT.  This would happen if the current time exceeded
    // the value in the "windowend" field for the job.
    //
    database.beginTransaction();
    try
    {
      // First, query the appropriate fields of all jobs.
      ArrayList list = new ArrayList();
      list.add(jobs.statusToString(jobs.STATUS_ACTIVE));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVE_UNINSTALLED));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING_UNINSTALLED));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVE_NOOUTPUT));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING_NOOUTPUT));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVE_NEITHER));
      list.add(jobs.statusToString(jobs.STATUS_ACTIVESEEDING_NEITHER));
      list.add(jobs.statusToString(jobs.STATUS_PAUSED));
      list.add(jobs.statusToString(jobs.STATUS_PAUSEDSEEDING));
      
      IResultSet set = database.performQuery("SELECT "+
        jobs.idField+","+
        jobs.statusField+
        " FROM "+jobs.getTableName()+" WHERE "+
        jobs.statusField+" IN (?,?,?,?,?,?,?,?,?,?) AND "+
        jobs.windowEndField+"<"+(new Long(currentTime)).toString()+" FOR UPDATE",
        list,null,null);

      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i++);

        Long jobID = (Long)row.getValue(jobs.idField);
        waitList.add(jobID);

        int status = jobs.stringToStatus(row.getValue(jobs.statusField).toString());

        // Make the job wait.
        switch (status)
        {
        case Jobs.STATUS_ACTIVE:
        case Jobs.STATUS_ACTIVE_UNINSTALLED:
        case Jobs.STATUS_ACTIVE_NOOUTPUT:
        case Jobs.STATUS_ACTIVE_NEITHER:
          jobs.waitJob(jobID,Jobs.STATUS_ACTIVEWAIT);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Job "+jobID+" now in 'wait' state due to window end");
          }
          break;
        case Jobs.STATUS_ACTIVESEEDING:
        case Jobs.STATUS_ACTIVESEEDING_UNINSTALLED:
        case Jobs.STATUS_ACTIVESEEDING_NOOUTPUT:
        case Jobs.STATUS_ACTIVESEEDING_NEITHER:
          jobs.waitJob(jobID,Jobs.STATUS_ACTIVEWAITSEEDING);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Job "+jobID+" now in 'wait' state due to window end");
          }
          break;
        case Jobs.STATUS_PAUSED:
          jobs.waitJob(jobID,Jobs.STATUS_PAUSEDWAIT);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Job "+jobID+" now in 'wait paused' state due to window end");
          }
          break;
        case Jobs.STATUS_PAUSEDSEEDING:
          jobs.waitJob(jobID,Jobs.STATUS_PAUSEDWAITSEEDING);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Job "+jobID+" now in 'wait paused' state due to window end");
          }
          break;
        default:
          break;
        }

      }
    }
    catch (ManifoldCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }
  }

  /** Reset job schedule.  This re-evaluates whether the job should be started now.  This method would typically
  * be called after a job's scheduling window has been changed.
  *@param jobID is the job identifier.
  */
  public void resetJobSchedule(Long jobID)
    throws ManifoldCFException
  {
    // Note:  This is problematic; the expected behavior is for the job to start if "we are within the window",
    // but not to start if the transition to active status was long enough ago.
    // Since there's no "right" way to do this, do nothing for now.

    // This explicitly did NOT work - it caused the job to refire every time it was saved.
    // jobs.updateLastTime(jobID,0L);
  }

  /** Check if the specified job parameters have a 'hit' within the specified interval.
  *@param startTime is the start time.
  *@param currentTimestamp is the end time.
  *@param daysOfWeek is the enumerated days of the week, or null.
  *@param daysOfMonth is the enumerated days of the month, or null.
  *@param months is the enumerated months, or null.
  *@param years is the enumerated years, or null.
  *@param hours is the enumerated hours, or null.
  *@param minutes is the enumerated minutes, or null.
  *@return null if there is NO hit within the interval; otherwise the actual time of the hit in milliseconds
  * from epoch is returned.
  */
  protected static Long checkTimeMatch(long startTime, long currentTimestamp,
    EnumeratedValues daysOfWeek,
    EnumeratedValues daysOfMonth,
    EnumeratedValues months,
    EnumeratedValues years,
    EnumeratedValues hours,
    EnumeratedValues minutes,
    String timezone,
    Long duration)
  {
    // What we do here is start with the previous timestamp, and advance until we
    // either encounter a match, or we exceed the current timestamp.

    Calendar c;
    if (timezone == null)
    {
      c = Calendar.getInstance();
    }
    else
    {
      c = Calendar.getInstance(TimeZone.getTimeZone(timezone));
    }

    // Get the current starting time
    c.setTimeInMillis(startTime);

    // If there's a duration value, we can't match unless we're within the window.
    // That means we find a match, and then we verify that the end time is greater than the currenttimestamp.
    // If not, we move on (by incrementing)

    // The main loop works off of the calendar and these values.
    while (c.getTimeInMillis() < currentTimestamp)
    {
      // Round up to the nearest minute, unless at 0 already
      int x = c.get(Calendar.MILLISECOND);
      if (x != c.getMinimum(Calendar.MILLISECOND))
      {
        int amtToAdd = c.getLeastMaximum(Calendar.MILLISECOND)+1-x;
        if (amtToAdd < 1)
          amtToAdd = 1;
        c.add(Calendar.MILLISECOND,amtToAdd);
        continue;
      }
      x = c.get(Calendar.SECOND);
      if (x != c.getMinimum(Calendar.SECOND))
      {
        int amtToAdd = c.getLeastMaximum(Calendar.SECOND)+1-x;
        if (amtToAdd < 1)
          amtToAdd = 1;
        c.add(Calendar.SECOND,amtToAdd);
        continue;
      }
      boolean startedToCareYet = false;
      x = c.get(Calendar.MINUTE);
      // If we care about minutes, round up, otherwise go to the 0 value
      if (minutes == null)
      {
        if (x != c.getMinimum(Calendar.MINUTE))
        {
          int amtToAdd = c.getLeastMaximum(Calendar.MINUTE)+1-x;
          if (amtToAdd < 1)
            amtToAdd = 1;
          c.add(Calendar.MINUTE,amtToAdd);
          continue;
        }
      }
      else
      {
        // See if it is a legit value.
        if (!minutes.checkValue(x-c.getMinimum(Calendar.MINUTE)))
        {
          // Advance to next legit value
          // We could be clever, but we just advance one
          c.add(Calendar.MINUTE,1);
          continue;
        }
        startedToCareYet = true;
      }
      // Hours
      x = c.get(Calendar.HOUR_OF_DAY);
      if (hours == null)
      {
        if (!startedToCareYet && x != c.getMinimum(Calendar.HOUR_OF_DAY))
        {
          int amtToAdd = c.getLeastMaximum(Calendar.HOUR_OF_DAY)+1-x;
          if (amtToAdd < 1)
            amtToAdd = 1;
          c.add(Calendar.HOUR_OF_DAY,amtToAdd);
          continue;
        }
      }
      else
      {
        if (!hours.checkValue(x-c.getMinimum(Calendar.HOUR_OF_DAY)))
        {
          // next hour
          c.add(Calendar.HOUR_OF_DAY,1);
          continue;
        }
        startedToCareYet = true;
      }
      // Days of month and days of week are at the same level;
      // these advance concurrently.  However, if NEITHER is specified, and nothing
      // earlier was, then we do the 1st of the month.
      x = c.get(Calendar.DAY_OF_WEEK);
      if (daysOfWeek != null)
      {
        if (!daysOfWeek.checkValue(x-c.getMinimum(Calendar.DAY_OF_WEEK)))
        {
          // next day
          c.add(Calendar.DAY_OF_WEEK,1);
          continue;
        }
        startedToCareYet = true;
      }
      x = c.get(Calendar.DAY_OF_MONTH);
      if (daysOfMonth == null)
      {
        // If nothing is specified but the month or the year, do it on the 1st.
        if (!startedToCareYet && x != c.getMinimum(Calendar.DAY_OF_MONTH))
        {
          // Move as rapidly as possible towards the first of the month.  But in no case, increment
          // less than one day.
          int amtToAdd = c.getLeastMaximum(Calendar.DAY_OF_MONTH)+1-x;
          if (amtToAdd < 1)
            amtToAdd = 1;
          c.add(Calendar.DAY_OF_MONTH,amtToAdd);
          continue;
        }
      }
      else
      {
        if (!daysOfMonth.checkValue(x-c.getMinimum(Calendar.DAY_OF_MONTH)))
        {
          // next day
          c.add(Calendar.DAY_OF_MONTH,1);
          continue;
        }
        startedToCareYet = true;
      }
      x = c.get(Calendar.MONTH);
      if (months == null)
      {
        if (!startedToCareYet && x != c.getMinimum(Calendar.MONTH))
        {
          int amtToAdd = c.getLeastMaximum(Calendar.MONTH)+1-x;
          if (amtToAdd < 1)
            amtToAdd = 1;
          c.add(Calendar.MONTH,amtToAdd);
          continue;
        }
      }
      else
      {
        if (!months.checkValue(x-c.getMinimum(Calendar.MONTH)))
        {
          c.add(Calendar.MONTH,1);
          continue;
        }
        startedToCareYet = true;
      }
      x = c.get(Calendar.YEAR);
      if (years != null)
      {
        if (!years.checkValue(x))
        {
          c.add(Calendar.YEAR,1);
          continue;
        }
        startedToCareYet = true;
      }

      // Looks like a match.
      // Last check is to be sure we are in the window, if any.  If we are outside the window,
      // must skip forward.
      if (duration != null && c.getTimeInMillis() + duration.longValue() <= currentTimestamp)
      {
        c.add(Calendar.MILLISECOND,c.getLeastMaximum(Calendar.MILLISECOND));
        continue;
      }

      return new Long(c.getTimeInMillis());
    }
    return null;
  }

  /** Manually start a job.  The specified job will be run REGARDLESS of the timed windows, and
  * will not cease until complete.  If the job is already running, this operation will assure that
  * the job does not pause when its window ends.  The job can be manually paused, or manually aborted.
  *@param jobID is the ID of the job to start.
  */
  public void manualStart(Long jobID)
    throws ManifoldCFException
  {
    database.beginTransaction();
    try
    {
      // First, query the appropriate fields of all jobs.
      ArrayList list = new ArrayList();
      list.add(jobID);
      IResultSet set = database.performQuery("SELECT "+
        jobs.statusField+
        " FROM "+jobs.getTableName()+" WHERE "+
        jobs.idField+"=? FOR UPDATE",list,null,null);
      if (set.getRowCount() < 1)
        throw new ManifoldCFException("No such job: "+jobID);

      IResultRow row = set.getRow(0);
      int status = jobs.stringToStatus(row.getValue(jobs.statusField).toString());
      if (status != Jobs.STATUS_INACTIVE)
        throw new ManifoldCFException("Job "+jobID+" is already running");

      IJobDescription jobDescription = jobs.load(jobID,true);
      if (Logging.jobs.isDebugEnabled())
      {
        Logging.jobs.debug("Manually starting job "+jobID);
      }
      // Start this job!  but with no end time.
      jobs.startJob(jobID,null);
      jobQueue.clearFailTimes(jobID);
      if (Logging.jobs.isDebugEnabled())
      {
        Logging.jobs.debug("Manual job start signal for job "+jobID+" successfully sent");
      }

    }
    catch (ManifoldCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }
  }

  /** Note job started.
  *@param jobID is the job id.
  *@param startTime is the job start time.
  */
  public void noteJobStarted(Long jobID, long startTime)
    throws ManifoldCFException
  {
    jobs.noteJobStarted(jobID,startTime);
    if (Logging.jobs.isDebugEnabled())
      Logging.jobs.debug("Job "+jobID+" is now started");
  }

  /** Note job seeded.
  *@param jobID is the job id.
  *@param seedTime is the job seed time.
  */
  public void noteJobSeeded(Long jobID, long seedTime)
    throws ManifoldCFException
  {
    jobs.noteJobSeeded(jobID,seedTime);
    if (Logging.jobs.isDebugEnabled())
      Logging.jobs.debug("Job "+jobID+" has been successfully reseeded");
  }

  /** Prepare for a full scan.
  *@param jobID is the job id.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  */
  public void prepareFullScan(Long jobID, String[] legalLinkTypes, int hopcountMethod)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      // Since we delete documents here, we need to manage the hopcount part of the world too.
      database.beginTransaction(database.TRANSACTION_SERIALIZED);
      try
      {
        // Delete all documents that match a given criteria
        if (legalLinkTypes.length > 0)
        {
          ArrayList list = new ArrayList();
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
          hopCount.deleteMatchingDocuments(jobID,legalLinkTypes,jobQueue.getTableName()+" t99",
            "t99."+jobQueue.docHashField,"t99."+jobQueue.jobIDField,
            "t99."+jobQueue.statusField+"=?",list,
            hopcountMethod);
        }

        jobQueue.prepareFullScan(jobID);
        break;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction preparing full scan: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Prepare for an incremental scan.
  *@param jobID is the job id.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  */
  public void prepareIncrementalScan(Long jobID, String[] legalLinkTypes, int hopcountMethod)
    throws ManifoldCFException
  {
    jobQueue.prepareIncrementalScan(jobID);
  }

  /** Manually abort a running job.  The job will be permanently stopped, and will not run again until
  * automatically started based on schedule, or manually started.
  *@param jobID is the job to abort.
  */
  public void manualAbort(Long jobID)
    throws ManifoldCFException
  {
    // Just whack status back to "INACTIVE".  The active documents will continue to be processed until done,
    // but that's fine.  There will be no finishing stage, obviously.
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Manually aborting job "+jobID);
    }
    jobs.abortJob(jobID,null);
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Job "+jobID+" abort signal successfully sent");
    }
  }

  /** Manually restart a running job.  The job will be stopped and restarted.  Any schedule affinity will be lost,
  * until the job finishes on its own.
  *@param jobID is the job to abort.
  */
  public void manualAbortRestart(Long jobID)
    throws ManifoldCFException
  {
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Manually restarting job "+jobID);
    }
    jobs.abortRestartJob(jobID);
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Job "+jobID+" restart signal successfully sent");
    }
  }

  /** Abort a running job due to a fatal error condition.
  *@param jobID is the job to abort.
  *@param errorText is the error text.
  *@return true if this is the first logged abort request for this job.
  */
  public boolean errorAbort(Long jobID, String errorText)
    throws ManifoldCFException
  {
    // Just whack status back to "INACTIVE".  The active documents will continue to be processed until done,
    // but that's fine.  There will be no finishing stage, obviously.
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Aborting job "+jobID+" due to error '"+errorText+"'");
    }
    boolean rval = jobs.abortJob(jobID,errorText);
    if (rval && Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Job "+jobID+" abort signal successfully sent");
    }
    return rval;
  }

  /** Pause a job.
  *@param jobID is the job identifier to pause.
  */
  public void pauseJob(Long jobID)
    throws ManifoldCFException
  {
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Manually pausing job "+jobID);
    }
    jobs.pauseJob(jobID);
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Job "+jobID+" successfully paused");
    }

  }

  /** Restart a paused job.
  *@param jobID is the job identifier to restart.
  */
  public void restartJob(Long jobID)
    throws ManifoldCFException
  {
    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Manually restarting paused job "+jobID);
    }

    database.beginTransaction();
    try
    {
      jobs.restartJob(jobID);
      jobQueue.clearFailTimes(jobID);
    }
    catch (ManifoldCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }

    if (Logging.jobs.isDebugEnabled())
    {
      Logging.jobs.debug("Job "+jobID+" successfully restarted");
    }
  }

  /** Get the list of jobs that are ready for seeding.
  *@return jobs that are active and are running in adaptive mode.  These will be seeded
  * based on what the connector says should be added to the queue.
  */
  public JobStartRecord[] getJobsReadyForSeeding(long currentTime)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Do the query
        ArrayList list = new ArrayList();
        
        list.add(jobs.statusToString(jobs.STATUS_ACTIVE));
        list.add(jobs.typeToString(jobs.TYPE_CONTINUOUS));
        
        list.add(new Long(currentTime));
        
        IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.lastCheckTimeField+","+
          jobs.reseedIntervalField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+"=? AND "+
          jobs.typeField+"=? AND ("+
          jobs.reseedTimeField+" IS NULL OR "+jobs.reseedTimeField+"<=?) FOR UPDATE",list,null,null);
        // Update them all
        JobStartRecord[] rval = new JobStartRecord[set.getRowCount()];
        int i = 0;
        while (i < rval.length)
        {
          IResultRow row = set.getRow(i);
          Long jobID = (Long)row.getValue(jobs.idField);
          Long x = (Long)row.getValue(jobs.lastCheckTimeField);
          long synchTime = 0;
          if (x != null)
            synchTime = x.longValue();

          Long r = (Long)row.getValue(jobs.reseedIntervalField);
          Long reseedTime;
          if (r != null)
            reseedTime = new Long(currentTime + r.longValue());
          else
            reseedTime = null;

          // Mark status of job as "active/seeding".  Special status is needed so that abort
          // will not complete until seeding is completed.
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVESEEDING,reseedTime);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Marked job "+jobID+" for seeding");
          }

          rval[i] = new JobStartRecord(jobID,synchTime);
          i++;
        }
        return rval;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted getting jobs ready for seeding: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Get the list of jobs that are ready for startup.
  *@return jobs that were in the "readyforstartup" state.  These will be marked as being in the "starting up" state.
  */
  public JobStartRecord[] getJobsReadyForStartup()
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Do the query
        ArrayList list = new ArrayList();
        list.add(jobs.statusToString(jobs.STATUS_READYFORSTARTUP));
        IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.lastCheckTimeField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+"=? FOR UPDATE",list,null,null);
        // Update them all
        JobStartRecord[] rval = new JobStartRecord[set.getRowCount()];
        int i = 0;
        while (i < rval.length)
        {
          IResultRow row = set.getRow(i);
          Long jobID = (Long)row.getValue(jobs.idField);
          Long x = (Long)row.getValue(jobs.lastCheckTimeField);
          long synchTime = 0;
          if (x != null)
            synchTime = x.longValue();

          // Mark status of job as "starting"
          jobs.writeStatus(jobID,jobs.STATUS_STARTINGUP);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Marked job "+jobID+" for startup");
          }

          rval[i] = new JobStartRecord(jobID,synchTime);
          i++;
        }
        return rval;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted getting jobs ready for startup: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Inactivate a job, from the notification state.
  *@param jobID is the ID of the job to inactivate.
  */
  public void inactivateJob(Long jobID)
    throws ManifoldCFException
  {
    // While there is no flow that can cause a job to be in the wrong state when this gets called, as a precaution
    // it might be a good idea to put this in a transaction and have the state get checked first.
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Check job status
        ArrayList list = new ArrayList();
        list.add(jobID);
        IResultSet set = database.performQuery("SELECT "+jobs.statusField+" FROM "+jobs.getTableName()+
          " WHERE "+jobs.idField+"=? FOR UPDATE",list,null,null);
        if (set.getRowCount() == 0)
          throw new ManifoldCFException("No such job: "+jobID);
        IResultRow row = set.getRow(0);
        int status = jobs.stringToStatus((String)row.getValue(jobs.statusField));

        switch (status)
        {
        case Jobs.STATUS_NOTIFYINGOFCOMPLETION:
          jobs.notificationComplete(jobID);
          break;
        default:
          throw new ManifoldCFException("Unexpected job status: "+Integer.toString(status));
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted clearing notification state for job: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset a starting job back to "ready for startup" state.
  *@param jobID is the job id.
  */
  public void resetStartupJob(Long jobID)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Check job status
        ArrayList list = new ArrayList();
        list.add(jobID);
        IResultSet set = database.performQuery("SELECT "+jobs.statusField+" FROM "+jobs.getTableName()+
          " WHERE "+jobs.idField+"=? FOR UPDATE",list,null,null);
        if (set.getRowCount() == 0)
          throw new ManifoldCFException("No such job: "+jobID);
        IResultRow row = set.getRow(0);
        int status = jobs.stringToStatus((String)row.getValue(jobs.statusField));

        switch (status)
        {
        case Jobs.STATUS_STARTINGUP:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'ReadyForStartup' state");

          // Set the state of the job back to "ReadyForStartup"
          jobs.writeStatus(jobID,jobs.STATUS_READYFORSTARTUP);
          break;
        case Jobs.STATUS_ABORTINGSTARTINGUP:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" to 'Aborting' state");
          jobs.writeStatus(jobID,jobs.STATUS_ABORTING);
          break;
        case Jobs.STATUS_ABORTINGSTARTINGUPFORRESTART:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" to 'AbortingForRestart' state");
          jobs.writeStatus(jobID,jobs.STATUS_ABORTINGFORRESTART);
          break;

        case Jobs.STATUS_READYFORSTARTUP:
        case Jobs.STATUS_ABORTING:
        case Jobs.STATUS_ABORTINGFORRESTART:
          // ok
          break;
        default:
          throw new ManifoldCFException("Unexpected job status: "+Integer.toString(status));
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted resetting startup job: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset a seeding job back to "active" state.
  *@param jobID is the job id.
  */
  public void resetSeedJob(Long jobID)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Check job status
        ArrayList list = new ArrayList();
        list.add(jobID);
        IResultSet set = database.performQuery("SELECT "+jobs.statusField+" FROM "+jobs.getTableName()+
          " WHERE "+jobs.idField+"=? FOR UPDATE",list,null,null);
        if (set.getRowCount() == 0)
          throw new ManifoldCFException("No such job: "+jobID);
        IResultRow row = set.getRow(0);
        int status = jobs.stringToStatus((String)row.getValue(jobs.statusField));
        switch (status)
        {
        case Jobs.STATUS_ACTIVESEEDING_UNINSTALLED:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Active_Uninstalled' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVE_UNINSTALLED);
          break;
        case Jobs.STATUS_ACTIVESEEDING_NOOUTPUT:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Active_NoOutput' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVE_NOOUTPUT);
          break;
        case Jobs.STATUS_ACTIVESEEDING_NEITHER:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Active_Neither' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVE_NEITHER);
          break;
        case Jobs.STATUS_ACTIVESEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Active' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVE);
          break;
        case Jobs.STATUS_ACTIVEWAITSEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'ActiveWait' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ACTIVEWAIT);
          break;
        case Jobs.STATUS_PAUSEDSEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Paused' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_PAUSED);
          break;
        case Jobs.STATUS_PAUSEDWAITSEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'PausedWait' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_PAUSEDWAIT);
          break;
        case Jobs.STATUS_ABORTINGSEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'Aborting' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ABORTING);
          break;

        case Jobs.STATUS_ABORTINGFORRESTARTSEEDING:
          if (Logging.jobs.isDebugEnabled())
            Logging.jobs.debug("Setting job "+jobID+" back to 'AbortingForRestart' state");

          // Set the state of the job back to "Active"
          jobs.writeStatus(jobID,jobs.STATUS_ABORTINGFORRESTART);
          break;

        case Jobs.STATUS_ABORTING:
        case Jobs.STATUS_ABORTINGFORRESTART:
        case Jobs.STATUS_ACTIVE:
        case Jobs.STATUS_ACTIVE_UNINSTALLED:
        case Jobs.STATUS_ACTIVE_NOOUTPUT:
        case Jobs.STATUS_ACTIVE_NEITHER:
        case Jobs.STATUS_PAUSED:
        case Jobs.STATUS_ACTIVEWAIT:
        case Jobs.STATUS_PAUSEDWAIT:
          // ok
          break;
        default:
          throw new ManifoldCFException("Unexpected job status: "+Integer.toString(status));
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted resetting seeding job: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }


  /** Delete jobs in need of being deleted (which are marked "ready for delete").
  * This method is meant to be called periodically to perform delete processing on jobs.
  */
  public void deleteJobsReadyForDelete()
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      // This method must find only jobs that have nothing hanging around in their jobqueue that represents an ingested
      // document.  Any jobqueue entries which are in a state to interfere with the delete will be cleaned up by other
      // threads, so eventually a job will become eligible.  This happens when there are no records that have an ingested
      // status: complete, purgatory, being-cleaned, being-deleted, or pending purgatory.
      database.beginTransaction();
      try
      {
        // The original query was:
        //
        // SELECT id FROM jobs t0 WHERE status='D' AND NOT EXISTS(SELECT 'x' FROM jobqueue t1 WHERE t0.id=t1.jobid AND
        //      t1.status IN ('C', 'F', 'G'))
        //
        // However, this did not work well with Postgres when the tables got big.  So I revised things to do the following multi-stage process:
        // (1) The query should be broken up, such that n queries are done:
        //     (a) the first one should get all candidate jobs (those that have the right state)
        //     (b) there should be a query for each job of roughly this form: SELECT id FROM jobqueue WHERE jobid=xxx AND status IN (...) LIMIT 1
        // This will work way better than postgresql currently works, because neither the cost-based analysis nor the actual NOT clause seem to allow
        // early exit!!

        // Do the first query, getting the candidate jobs to be considered
        ArrayList list = new ArrayList();
        list.add(jobs.statusToString(jobs.STATUS_READYFORDELETE));
        list.add(jobs.statusToString(jobs.STATUS_READYFORDELETE_NOOUTPUT));
        IResultSet set = database.performQuery("SELECT "+jobs.idField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+" IN(?,?) FOR UPDATE",list,null,null);

        // Now, loop through this list.  For each one, verify that it's okay to delete it
        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          Long jobID = (Long)row.getValue(jobs.idField);

          list.clear();
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));

          IResultSet confirmSet = database.performQuery("SELECT "+jobQueue.idField+" FROM "+
            jobQueue.getTableName()+" WHERE "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) "+database.constructOffsetLimitClause(0,1),list,null,null,1,null);

          if (confirmSet.getRowCount() > 0)
            continue;

          ManifoldCF.noteConfigurationChange();
          // Remove documents from job queue
          jobQueue.deleteAllJobRecords(jobID);
          // Remove carrydowns for the job
          carryDown.deleteOwner(jobID);
          // Nothing is in a critical section - so this should be OK.
          hopCount.deleteOwner(jobID);
          jobs.delete(jobID);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Removed job "+jobID);
          }
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted deleting jobs ready for delete: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Put all eligible jobs in the "shutting down" state.
  */
  public void finishJobs()
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      // The jobs we should transition:
      // - are active
      // - have no ACTIVE, PENDING, ACTIVEPURGATORY, or PENDINGPURGATORY records
      database.beginTransaction();
      try
      {
        // The query I used to emit was:
        // SELECT jobid FROM jobs t0 WHERE t0.status='A' AND NOT EXISTS(SELECT 'x' FROM jobqueue t1 WHERE
        //              t0.id=t1.jobid AND t1.status IN ('A','P','F','G'))

        // This did not get along well with Postgresql, so instead this is what is now done:
        // (1) The query should be broken up, such that n queries are done:
        //     (a) the first one should get all candidate jobs (those that have the right state)
        //     (b) there should be a query for each job of roughly this form: SELECT id FROM jobqueue WHERE jobid=xxx AND status IN (...) LIMIT 1
        // This will work way better than postgresql currently works, because neither the cost-based analysis nor the actual NOT clause seem to allow
        // early exit!!

        // Do the first query, getting the candidate jobs to be considered
        ArrayList list = new ArrayList();
        list.add(jobs.statusToString(jobs.STATUS_ACTIVE));
        list.add(jobs.statusToString(jobs.STATUS_ACTIVEWAIT));
        list.add(jobs.statusToString(jobs.STATUS_ACTIVE_UNINSTALLED));
        list.add(jobs.statusToString(jobs.STATUS_ACTIVE_NOOUTPUT));
        list.add(jobs.statusToString(jobs.STATUS_ACTIVE_NEITHER));
        
        IResultSet set = database.performQuery("SELECT "+jobs.idField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+" IN (?,?,?,?,?) FOR UPDATE",list,null,null);

        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          Long jobID = (Long)row.getValue(jobs.idField);

          // Check to be sure the job is a candidate for shutdown
          list.clear();
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));

          IResultSet confirmSet = database.performQuery("SELECT "+jobQueue.idField+" FROM "+
            jobQueue.getTableName()+" WHERE "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) "+database.constructOffsetLimitClause(0,1),list,null,null,1,null);

          if (confirmSet.getRowCount() > 0)
            continue;

          // Mark status of job as "finishing"
          jobs.writeStatus(jobID,jobs.STATUS_SHUTTINGDOWN);
          if (Logging.jobs.isDebugEnabled())
          {
            Logging.jobs.debug("Marked job "+jobID+" for shutdown");
          }

        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted finishing jobs: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Find the list of jobs that need to have their connectors notified of job completion.
  *@return the ID's of jobs that need their output connectors notified in order to become inactive.
  */
  public Long[] getJobsReadyForInactivity()
    throws ManifoldCFException
  {
    // Do the query
    ArrayList list = new ArrayList();
    list.add(jobs.statusToString(jobs.STATUS_NOTIFYINGOFCOMPLETION));
    IResultSet set = database.performQuery("SELECT "+jobs.idField+" FROM "+
      jobs.getTableName()+" WHERE "+jobs.statusField+"=?",list,null,null);
    // Return them all
    Long[] rval = new Long[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      Long jobID = (Long)row.getValue(jobs.idField);
      rval[i++] = jobID;
      if (Logging.jobs.isDebugEnabled())
      {
        Logging.jobs.debug("Found job "+jobID+" in need of notification");
      }
    }
    return rval;
  }
  
  /** Complete the sequence that aborts jobs and makes them runnable again.
  *@param timestamp is the current time.
  *@param abortJobs is the set of IJobDescription objects that were aborted (and stopped).
  */
  public void finishJobAborts(long timestamp, ArrayList abortJobs)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // The query I used to emit was:
        // SELECT jobid FROM jobs t0 WHERE t0.status='X' AND NOT EXISTS(SELECT 'x' FROM jobqueue t1 WHERE
        //              t0.id=t1.jobid AND t1.status IN ('A','F'))
        // Now the query is broken up so that Postgresql behaves more efficiently.

        // Do the first query, getting the candidate jobs to be considered
        ArrayList list = new ArrayList();
        list.add(jobs.statusToString(jobs.STATUS_ABORTING));
        list.add(jobs.statusToString(jobs.STATUS_ABORTINGFORRESTART));
        
        IResultSet set = database.performQuery("SELECT "+jobs.idField+","+jobs.statusField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+" IN (?,?) FOR UPDATE",list,null,null);

        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          Long jobID = (Long)row.getValue(jobs.idField);

          list.clear();
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));

          IResultSet confirmSet = database.performQuery("SELECT "+jobQueue.idField+" FROM "+
            jobQueue.getTableName()+" WHERE "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) "+database.constructOffsetLimitClause(0,1),list,null,null,1,null);

          if (confirmSet.getRowCount() > 0)
            continue;

          int status = jobs.stringToStatus((String)row.getValue(jobs.statusField));
          IJobDescription jobDesc = jobs.load(jobID,true);
          abortJobs.add(jobDesc);

          switch (status)
          {
          case Jobs.STATUS_ABORTING:
            // Mark status of job as "inactive"
            jobs.finishAbortJob(jobID,timestamp);
            if (Logging.jobs.isDebugEnabled())
            {
              Logging.jobs.debug("Completed abort of job "+jobID);
            }
            break;
          case Jobs.STATUS_ABORTINGFORRESTART:
            // Do the restart sequence!  Log the abort here; the startup thread will log the start.
            jobs.startJob(jobID,null);
            {
              Logging.jobs.debug("Completed restart of job "+jobID);
            }
            break;
          default:
            throw new ManifoldCFException("Unexpected value for job status: "+Integer.toString(status));
          }
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted finishing job aborts: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

  /** Reset eligible jobs either back to the "inactive" state, or make them active again.  The
  * latter will occur if the cleanup phase of the job generated more pending documents.
  *
  *  This method is used to pick up all jobs in the shutting down state
  * whose purgatory or being-cleaned records have been all processed.
  *
  *@param currentTime is the current time in milliseconds since epoch.
  *@param resetJobs is filled in with the set of IJobDescription objects that were reset.
  */
  public void resetJobs(long currentTime, ArrayList resetJobs)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      database.beginTransaction();
      try
      {
        // Query for all jobs that fulfill the criteria
        // The query used to look like:
        //
        // SELECT id FROM jobs t0 WHERE status='D' AND NOT EXISTS(SELECT 'x' FROM jobqueue t1 WHERE
        //      t0.id=t1.jobid AND t1.status='P')
        //
        // Now, the query is broken up, for performance

        // Do the first query, getting the candidate jobs to be considered
        ArrayList list = new ArrayList();
        list.add(jobs.statusToString(jobs.STATUS_SHUTTINGDOWN));
        IResultSet set = database.performQuery("SELECT "+jobs.idField+" FROM "+
          jobs.getTableName()+" WHERE "+jobs.statusField+"=? FOR UPDATE",list,null,null);

        int i = 0;
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          Long jobID = (Long)row.getValue(jobs.idField);

          // Check to be sure the job is a candidate for shutdown
          list.clear();
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));

          IResultSet confirmSet = database.performQuery("SELECT "+jobQueue.idField+" FROM "+
            jobQueue.getTableName()+" WHERE "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) "+database.constructOffsetLimitClause(0,1),list,null,null,1,null);

          if (confirmSet.getRowCount() > 0)
            continue;

          // The shutting-down phase is complete.  However, we need to check if there are any outstanding
          // PENDING or PENDINGPURGATORY records before we can decide what to do.
          list.clear();
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
          list.add(jobID);
          list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));

          confirmSet = database.performQuery("SELECT "+jobQueue.idField+" FROM "+
            jobQueue.getTableName()+" WHERE "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) OR "+
            "("+jobQueue.jobIDField+"=? AND "+jobQueue.statusField+"=?) "+database.constructOffsetLimitClause(0,1),list,null,null,1,null);

          if (confirmSet.getRowCount() > 0)
          {
            // This job needs to re-enter the active state.  Make that happen.
            jobs.returnJobToActive(jobID);
            if (Logging.jobs.isDebugEnabled())
            {
              Logging.jobs.debug("Job "+jobID+" is re-entering active state");
            }
          }
          else
          {
            // This job should be marked as finished.
            IJobDescription jobDesc = jobs.load(jobID,true);
            resetJobs.add(jobDesc);
            
            // Label the job "finished"
            jobs.finishJob(jobID,currentTime);
            if (Logging.jobs.isDebugEnabled())
            {
              Logging.jobs.debug("Job "+jobID+" now completed");
            }
          }
        }
        return;
      }
      catch (ManifoldCFException e)
      {
        database.signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted resetting jobs: "+e.getMessage());
          sleepAmt = getRandomAmount();
          continue;
        }
        throw e;
      }
      catch (Error e)
      {
        database.signalRollback();
        throw e;
      }
      finally
      {
        database.endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }


  // Status reports

  /** Get the status of a job.
  *@return the status object for the specified job.
  */
  public JobStatus getStatus(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String whereClause = Jobs.idField+"=?";
    list.add(jobID.toString());
    JobStatus[] records = makeJobStatus(whereClause,list);
    if (records.length == 0)
      return null;
    return records[0];
  }


  /** Get a list of all jobs, and their status information.
  *@return an ordered array of job status objects.
  */
  public JobStatus[] getAllStatus()
    throws ManifoldCFException
  {
    return makeJobStatus(null,null);
  }

  /** Get a list of running jobs.  This is for status reporting.
  *@return an array of the job status objects.
  */
  public JobStatus[] getRunningJobs()
    throws ManifoldCFException
  {
    ArrayList whereParams = new ArrayList();
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVE));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVESEEDING));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVE_UNINSTALLED));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVESEEDING_UNINSTALLED));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVE_NOOUTPUT));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVESEEDING_NOOUTPUT));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVE_NEITHER));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVESEEDING_NEITHER));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_PAUSED));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_PAUSEDSEEDING));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVEWAIT));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_ACTIVEWAITSEEDING));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_PAUSEDWAIT));
    whereParams.add(Jobs.statusToString(Jobs.STATUS_PAUSEDWAITSEEDING));
    
    String whereClause =
      Jobs.statusField+" IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    return makeJobStatus(whereClause,whereParams);
  }

  /** Get a list of completed jobs, and their statistics.
  *@return an array of the job status objects.
  */
  public JobStatus[] getFinishedJobs()
    throws ManifoldCFException
  {
    ArrayList whereParams = new ArrayList();
    whereParams.add(Jobs.statusToString(Jobs.STATUS_INACTIVE));
    String whereClause =
      Jobs.statusField+"=? AND "+
      Jobs.endTimeField+"IS NOT NULL";

    return makeJobStatus(whereClause,whereParams);
  }

  // Protected methods and classes

  /** Make a job status array from a query result.
  *@param whereClause is the where clause for the jobs we are interested in.
  *@return the status array.
  */
  protected JobStatus[] makeJobStatus(String whereClause, ArrayList whereParams)
    throws ManifoldCFException
  {
    IResultSet set = database.performQuery("SELECT t0."+
      Jobs.idField+",t0."+
      Jobs.descriptionField+",t0."+
      Jobs.statusField+",t0."+
      Jobs.startTimeField+",t0."+
      Jobs.endTimeField+",t0."+
      Jobs.errorField+
      " FROM "+jobs.getTableName()+" t0 "+((whereClause==null)?"":(" WHERE "+whereClause))+" ORDER BY "+Jobs.descriptionField+" ASC",
      whereParams,null,null);

    IResultSet set2 = database.performQuery("SELECT "+
      JobQueue.jobIDField+",CAST(COUNT("+JobQueue.docHashField+") AS BIGINT) AS doccount FROM "+
      jobQueue.getTableName()+" t1"+
      ((whereClause==null)?"":(" WHERE EXISTS(SELECT 'x' FROM "+
      jobs.getTableName()+" t0 WHERE t0."+Jobs.idField+"=t1."+JobQueue.jobIDField+" AND "+whereClause+")"))+
      " GROUP BY "+JobQueue.jobIDField,whereParams,null,null);

    ArrayList list = new ArrayList();
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVE));
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(JobQueue.statusToString(JobQueue.STATUS_PENDING));
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVEPURGATORY));
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    list.add(JobQueue.statusToString(JobQueue.STATUS_PENDINGPURGATORY));
    if (whereParams != null)
      list.addAll(whereParams);
    IResultSet set3 = database.performQuery("SELECT "+
      JobQueue.jobIDField+",CAST(COUNT("+JobQueue.docHashField+") AS BIGINT) AS doccount FROM "+
      jobQueue.getTableName()+" t1 WHERE "+
      JobQueue.statusField+" IN (?,?,?,?,?,?)"+
      ((whereClause==null)?"":(" AND EXISTS(SELECT 'x' FROM "+
      jobs.getTableName()+" t0 WHERE t0."+Jobs.idField+"=t1."+JobQueue.jobIDField+" AND "+whereClause+")"))+
      " GROUP BY "+JobQueue.jobIDField,list,null,null);

    list.clear();
    list.add(JobQueue.statusToString(JobQueue.STATUS_COMPLETE));
    list.add(JobQueue.statusToString(JobQueue.STATUS_PURGATORY));
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVEPURGATORY));
    list.add(JobQueue.statusToString(JobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    list.add(JobQueue.statusToString(JobQueue.STATUS_PENDINGPURGATORY));
    if (whereParams != null)
      list.addAll(whereParams);
    IResultSet set4 = database.performQuery("SELECT "+
      JobQueue.jobIDField+",CAST(COUNT("+JobQueue.docHashField+") AS BIGINT) AS doccount FROM "+
      jobQueue.getTableName()+" t1 WHERE "+
      JobQueue.statusField+" IN (?,?,?,?,?)"+
      ((whereClause==null)?"":(" AND EXISTS(SELECT 'x' FROM "+
      jobs.getTableName()+" t0 WHERE t0."+Jobs.idField+"=t1."+JobQueue.jobIDField+" AND "+whereClause+")"))+
      " GROUP BY "+JobQueue.jobIDField,list,null,null);

    // Build hashes for set2 and set3
    HashMap set2Hash = new HashMap();
    int i = 0;
    while (i < set2.getRowCount())
    {
      IResultRow row = set2.getRow(i++);
      set2Hash.put(row.getValue(JobQueue.jobIDField),row.getValue("doccount"));
    }
    HashMap set3Hash = new HashMap();
    i = 0;
    while (i < set3.getRowCount())
    {
      IResultRow row = set3.getRow(i++);
      set3Hash.put(row.getValue(JobQueue.jobIDField),row.getValue("doccount"));
    }
    HashMap set4Hash = new HashMap();
    i = 0;
    while (i < set4.getRowCount())
    {
      IResultRow row = set4.getRow(i++);
      set4Hash.put(row.getValue(JobQueue.jobIDField),row.getValue("doccount"));
    }

    JobStatus[] rval = new JobStatus[set.getRowCount()];
    i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      Long jobID = (Long)row.getValue(Jobs.idField);
      String description = row.getValue(Jobs.descriptionField).toString();
      int status = Jobs.stringToStatus(row.getValue(Jobs.statusField).toString());
      Long startTimeValue = (Long)row.getValue(Jobs.startTimeField);
      long startTime = -1;
      if (startTimeValue != null)
        startTime = startTimeValue.longValue();
      Long endTimeValue = (Long)row.getValue(Jobs.endTimeField);
      long endTime = -1;
      if (endTimeValue != null)
        endTime = endTimeValue.longValue();
      String errorText = (String)row.getValue(Jobs.errorField);
      if (errorText != null && errorText.length() == 0)
        errorText = null;
      int rstatus = JobStatus.JOBSTATUS_NOTYETRUN;

      switch (status)
      {
      case Jobs.STATUS_INACTIVE:
        if (errorText != null)
          rstatus = JobStatus.JOBSTATUS_ERROR;
        else
        {
          if (startTime >= 0)
            rstatus = JobStatus.JOBSTATUS_COMPLETED;
          else
            rstatus = JobStatus.JOBSTATUS_NOTYETRUN;
        }
        break;
      case Jobs.STATUS_ACTIVE_UNINSTALLED:
      case Jobs.STATUS_ACTIVESEEDING_UNINSTALLED:
      case Jobs.STATUS_ACTIVE_NOOUTPUT:
      case Jobs.STATUS_ACTIVESEEDING_NOOUTPUT:
      case Jobs.STATUS_ACTIVE_NEITHER:
      case Jobs.STATUS_ACTIVESEEDING_NEITHER:
        rstatus = JobStatus.JOBSTATUS_RUNNING_UNINSTALLED;
        break;
      case Jobs.STATUS_ACTIVE:
      case Jobs.STATUS_ACTIVESEEDING:
        rstatus = JobStatus.JOBSTATUS_RUNNING;
        break;
      case Jobs.STATUS_SHUTTINGDOWN:
        rstatus = JobStatus.JOBSTATUS_JOBENDCLEANUP;
        break;
      case Jobs.STATUS_NOTIFYINGOFCOMPLETION:
        rstatus = JobStatus.JOBSTATUS_JOBENDNOTIFICATION;
        break;
      case Jobs.STATUS_ABORTING:
      case Jobs.STATUS_ABORTINGSEEDING:
      case Jobs.STATUS_ABORTINGSTARTINGUP:
        rstatus = JobStatus.JOBSTATUS_ABORTING;
        break;
      case Jobs.STATUS_ABORTINGFORRESTART:
      case Jobs.STATUS_ABORTINGFORRESTARTSEEDING:
      case Jobs.STATUS_ABORTINGSTARTINGUPFORRESTART:
        rstatus = JobStatus.JOBSTATUS_RESTARTING;
        break;
      case Jobs.STATUS_PAUSED:
      case Jobs.STATUS_PAUSEDSEEDING:
        rstatus = JobStatus.JOBSTATUS_PAUSED;
        break;
      case Jobs.STATUS_ACTIVEWAIT:
      case Jobs.STATUS_ACTIVEWAITSEEDING:
        rstatus = JobStatus.JOBSTATUS_WINDOWWAIT;
        break;
      case Jobs.STATUS_PAUSEDWAIT:
      case Jobs.STATUS_PAUSEDWAITSEEDING:
        rstatus = JobStatus.JOBSTATUS_PAUSED;
        break;
      case Jobs.STATUS_STARTINGUP:
      case Jobs.STATUS_READYFORSTARTUP:
        rstatus = JobStatus.JOBSTATUS_STARTING;
        break;
      case Jobs.STATUS_READYFORDELETE:
      case Jobs.STATUS_READYFORDELETE_NOOUTPUT:
        rstatus = JobStatus.JOBSTATUS_DESTRUCTING;
        break;
      default:
        break;
      }

      Long set2Value = (Long)set2Hash.get(jobID);
      Long set3Value = (Long)set3Hash.get(jobID);
      Long set4Value = (Long)set4Hash.get(jobID);

      rval[i++] = new JobStatus(jobID.toString(),description,rstatus,((set2Value==null)?0L:set2Value.longValue()),
        ((set3Value==null)?0L:set3Value.longValue()),
        ((set4Value==null)?0L:set4Value.longValue()),
        startTime,endTime,errorText);
    }
    return rval;
  }

  // These methods generate reports for direct display in the UI.

  /** Run a 'document status' report.
  *@param connectionName is the name of the connection.
  *@param filterCriteria are the criteria used to limit the records considered for the report.
  *@param sortOrder is the specified sort order of the final report.
  *@param startRow is the first row to include.
  *@param rowCount is the number of rows to include.
  *@return the results, with the following columns: identifier, job, state, status, scheduled, action, retrycount, retrylimit.  The "scheduled" column and the
  * "retrylimit" column are long values representing a time; all other values will be user-friendly strings.
  */
  public IResultSet genDocumentStatus(String connectionName, StatusFilterCriteria filterCriteria, SortOrder sortOrder,
    int startRow, int rowCount)
    throws ManifoldCFException
  {
    // Build the query.
    Long currentTime = new Long(System.currentTimeMillis());
    StringBuffer sb = new StringBuffer("SELECT ");
    ArrayList list = new ArrayList();
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    
    sb.append("t0.").append(jobQueue.idField).append(" AS id,")
      .append("t0.").append(jobQueue.docIDField).append(" AS identifier,")
      .append("t1.").append(jobs.descriptionField).append(" AS job,")
      .append("CASE")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Not yet processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Not yet processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Not yet processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Processed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Being removed'")
      .append(" WHEN ").append("t0.").append(jobQueue.statusField).append("=? THEN 'Being removed'")
      .append(" ELSE 'Unknown'")
      .append(" END AS state,")
      .append("CASE")
      .append(" WHEN ")
      .append("(").append("t0.").append(jobQueue.statusField).append("=? OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Inactive'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkTimeField).append("<=").append(currentTime.toString())
      .append(" AND (t0.").append(jobQueue.checkActionField).append(" IS NULL OR t0.").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Ready for processing'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkTimeField).append("<=").append(currentTime.toString())
      .append(" AND t0.").append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Ready for expiration'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkTimeField).append(">").append(currentTime.toString())
      .append(" AND (t0.").append(jobQueue.checkActionField).append(" IS NULL OR t0.").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Waiting for processing'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkTimeField).append(">").append(currentTime.toString())
      .append(" AND t0.").append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Waiting for expiration'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkTimeField).append(" IS NULL")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Waiting forever'")
      .append(" WHEN (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?)")
      .append(")")
      .append(" THEN 'Deleting'")
      .append(" WHEN ")
      .append("(t0.").append(jobQueue.checkActionField).append(" IS NULL OR t0.").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Processing'")
      .append(" WHEN ")
      .append("t0.").append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(" OR ").append("t0.").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 'Expiring'")
      .append(" ELSE 'Unknown'")
      .append(" END AS status,")
      .append("t0.").append(jobQueue.checkTimeField).append(" AS scheduled,")
      .append("CASE")
      .append(" WHEN ").append("(t0.").append(jobQueue.checkActionField).append(" IS NULL OR t0.").append(jobQueue.checkActionField).append("=?) THEN 'Process'")
      .append(" WHEN ").append("t0.").append(jobQueue.checkActionField).append("=? THEN 'Expire'")
      .append(" ELSE 'Unknown'")
      .append(" END AS action,")
      .append("t0.").append(jobQueue.failCountField).append(" AS retrycount,")
      .append("t0.").append(jobQueue.failTimeField).append(" AS retrylimit")
      .append(" FROM ").append(jobQueue.getTableName()).append(" t0,").append(jobs.getTableName()).append(" t1 WHERE ")
      .append("t0.").append(jobQueue.jobIDField).append("=t1.").append(jobs.idField);
    addCriteria(sb,list,"t0.",connectionName,filterCriteria,true);
    // The intrinsic ordering is provided by the "id" column, and nothing else.
    addOrdering(sb,new String[]{"id"},sortOrder);
    addLimits(sb,startRow,rowCount);
    return database.performQuery(sb.toString(),list,null,null,rowCount,null);
  }

  /** Run a 'queue status' report.
  *@param connectionName is the name of the connection.
  *@param filterCriteria are the criteria used to limit the records considered for the report.
  *@param sortOrder is the specified sort order of the final report.
  *@param idBucketDescription is the bucket description for generating the identifier class.
  *@param startRow is the first row to include.
  *@param rowCount is the number of rows to include.
  *@return the results, with the following columns: idbucket, inactive, processing, expiring, deleting,
  processready, expireready, processwaiting, expirewaiting
  */
  public IResultSet genQueueStatus(String connectionName, StatusFilterCriteria filterCriteria, SortOrder sortOrder,
    BucketDescription idBucketDescription, int startRow, int rowCount)
    throws ManifoldCFException
  {
    // SELECT substring(docid FROM '<id_regexp>') AS idbucket,
    //        substring(entityidentifier FROM '<id_regexp>') AS idbucket,
    //        SUM(CASE WHEN status='C' then 1 else 0 end)) AS inactive FROM jobqueue WHERE <criteria>
    //              GROUP BY idbucket

    Long currentTime = new Long(System.currentTimeMillis());

    StringBuffer sb = new StringBuffer();
    ArrayList list = new ArrayList();
    
    
    sb.append("SELECT t1.idbucket,SUM(t1.inactive) AS inactive,SUM(t1.processing) AS processing,SUM(t1.expiring) AS expiring,SUM(t1.deleting) AS deleting,")
      .append("SUM(t1.processready) AS processready,SUM(t1.expireready) AS expireready,SUM(t1.processwaiting) AS processwaiting,SUM(t1.expirewaiting) AS expirewaiting,")
      .append("SUM(t1.waitingforever) AS waitingforever FROM (SELECT ");
    
    addBucketExtract(sb,list,"",jobQueue.docIDField,idBucketDescription);
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
    list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
    list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
    list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
    sb.append(" AS idbucket,")
      .append("CASE")
      .append(" WHEN ")
      .append("(").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" AS inactive,")
      .append("CASE")
      .append(" WHEN ")
      .append("(").append(jobQueue.checkActionField).append(" IS NULL OR ").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as processing,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as expiring,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as deleting,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkTimeField).append("<=").append(currentTime.toString())
      .append(" AND (").append(jobQueue.checkActionField).append(" IS NULL OR ").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as processready,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkTimeField).append("<=").append(currentTime.toString())
      .append(" AND ").append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as expireready,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkTimeField).append(">").append(currentTime.toString())
      .append(" AND (").append(jobQueue.checkActionField).append(" IS NULL OR ").append(jobQueue.checkActionField).append("=?)")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as processwaiting,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkTimeField).append(">").append(currentTime.toString())
      .append(" AND ").append(jobQueue.checkActionField).append("=?")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as expirewaiting,")
      .append("CASE")
      .append(" WHEN ")
      .append(jobQueue.checkTimeField).append(" IS NULL")
      .append(" AND (").append(jobQueue.statusField).append("=?")
      .append(" OR ").append(jobQueue.statusField).append("=?")
      .append(")")
      .append(" THEN 1 ELSE 0")
      .append(" END")
      .append(" as waitingforever");
    sb.append(" FROM ").append(jobQueue.getTableName());
    addCriteria(sb,list,"",connectionName,filterCriteria,false);
    sb.append(") t1 GROUP BY idbucket");
    addOrdering(sb,new String[]{"idbucket","inactive","processing","expiring","deleting","processready","expireready","processwaiting","expirewaiting","waitingforever"},sortOrder);
    addLimits(sb,startRow,rowCount);
    return database.performQuery(sb.toString(),list,null,null,rowCount,null);
  }

  // Protected methods for report generation

  /** Turn a bucket description into a return column.
  * This is complicated by the fact that the extraction code is inherently case sensitive.  So if case insensitive is
  * desired, that means we whack the whole thing to lower case before doing the match.
  */
  protected void addBucketExtract(StringBuffer sb, ArrayList list, String columnPrefix, String columnName, BucketDescription bucketDesc)
  {
    boolean isSensitive = bucketDesc.isSensitive();
    list.add(bucketDesc.getRegexp());
    sb.append(database.constructSubstringClause(columnPrefix+columnName,"?",!isSensitive));
  }

  /** Add criteria clauses to query.
  */
  protected boolean addCriteria(StringBuffer sb, ArrayList list, String fieldPrefix, String connectionName, StatusFilterCriteria criteria, boolean whereEmitted)
    throws ManifoldCFException
  {
    Long[] matchingJobs = criteria.getJobs();

    if (matchingJobs != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      if (matchingJobs.length == 0)
      {
        sb.append("0>1");
      }
      else
      {
        sb.append(fieldPrefix).append(jobQueue.jobIDField).append(" IN(");
        int i = 0;
        while (i < matchingJobs.length)
        {
          if (i > 0)
            sb.append(",");
          Long jobID = matchingJobs[i++];
          sb.append(jobID.toString());
        }
        sb.append(")");
      }
    }

    RegExpCriteria identifierRegexp = criteria.getIdentifierMatch();
    if (identifierRegexp != null)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      list.add(identifierRegexp.getRegexpString());
      sb.append(database.constructRegexpClause(fieldPrefix+jobQueue.docIDField,"?",identifierRegexp.isInsensitive()));
    }

    Long nowTime = new Long(criteria.getNowTime());
    int[] states = criteria.getMatchingStates();
    int[] statuses = criteria.getMatchingStatuses();
    if (states.length == 0 || statuses.length == 0)
    {
      whereEmitted = emitClauseStart(sb,whereEmitted);
      sb.append("0>1");
      return whereEmitted;
    }

    // Iterate through the specified states, and emit a series of OR clauses, one for each state.  The contents of the clause will be complex.
    whereEmitted = emitClauseStart(sb,whereEmitted);
    sb.append("(");
    int k = 0;
    while (k < states.length)
    {
      int stateValue = states[k];
      if (k > 0)
        sb.append(" OR ");
      switch (stateValue)
      {
      case DOCSTATE_NEVERPROCESSED:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?,?)");
        break;
      case DOCSTATE_PREVIOUSLYPROCESSED:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
        list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?,?,?,?,?,?)");
        break;
      }
      k++;
    }
    sb.append(")");

    whereEmitted = emitClauseStart(sb,whereEmitted);
    sb.append("(");
    k = 0;
    while (k < statuses.length)
    {
      int stateValue = statuses[k];
      if (k > 0)
        sb.append(" OR ");
      switch (stateValue)
      {
      case DOCSTATUS_INACTIVE:
        list.add(jobQueue.statusToString(jobQueue.STATUS_COMPLETE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PURGATORY));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)");
        break;
      case DOCSTATUS_PROCESSING:
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?,?,?)")
          .append(" AND (").append(fieldPrefix).append(jobQueue.checkActionField).append(" IS NULL OR ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?)");
        break;
      case DOCSTATUS_EXPIRING:
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVE));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCAN));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVEPURGATORY));
        list.add(jobQueue.statusToString(jobQueue.STATUS_ACTIVENEEDRESCANPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?,?,?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?");
        break;
      case DOCSTATUS_DELETING:
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGDELETED));
        list.add(jobQueue.statusToString(jobQueue.STATUS_BEINGCLEANED));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)");
        break;
      case DOCSTATUS_READYFORPROCESSING:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)")
          .append(" AND (").append(fieldPrefix).append(jobQueue.checkActionField).append(" IS NULL OR ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkTimeField).append("<=").append(nowTime.toString());
        break;
      case DOCSTATUS_READYFOREXPIRATION:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkTimeField).append("<=").append(nowTime.toString());
        break;
      case DOCSTATUS_WAITINGFORPROCESSING:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_RESCAN));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)")
          .append(" AND (").append(fieldPrefix).append(jobQueue.checkActionField).append(" IS NULL OR ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkTimeField).append(">").append(nowTime.toString());
        break;
      case DOCSTATUS_WAITINGFOREXPIRATION:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        list.add(jobQueue.actionToString(jobQueue.ACTION_REMOVE));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkActionField).append("=?")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkTimeField).append(">").append(nowTime.toString());
        break;
      case DOCSTATUS_WAITINGFOREVER:
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDING));
        list.add(jobQueue.statusToString(jobQueue.STATUS_PENDINGPURGATORY));
        sb.append(fieldPrefix).append(jobQueue.statusField).append(" IN (?,?)")
          .append(" AND ").append(fieldPrefix).append(jobQueue.checkTimeField).append(" IS NULL");
        break;
      }
      k++;
    }
    sb.append(")");

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
    sb.append(" ").append(database.constructOffsetLimitClause(startRow,maxRowCount));
  }


  /** Class for tracking existing jobqueue row data */
  protected static class JobqueueRecord
  {
    protected Long recordID;
    protected int status;
    protected Long checkTimeValue;

    public JobqueueRecord(Long recordID, int status, Long checkTimeValue)
    {
      this.recordID = recordID;
      this.status = status;
      this.checkTimeValue = checkTimeValue;
    }

    public Long getRecordID()
    {
      return recordID;
    }

    public int getStatus()
    {
      return status;
    }

    public Long getCheckTimeValue()
    {
      return checkTimeValue;
    }
  }

  /** We go through 2x the number of documents we should need if we were perfect at setting document priorities.  */
  private static int EXTRA_FACTOR = 2;

  /** This class provides the throttling limits for the job queueing query.
  */
  protected static class ThrottleLimit implements ILimitChecker
  {
    // For each connection, there is (a) a number (which is the maximum per bin), and (b)
    // a current running count per bin.  These are stored as elements in a hash map.
    protected HashMap connectionMap = new HashMap();

    // The maximum number of jobs that have reached their chunk size limit that we
    // need
    protected int n;

    // This is the hash table that maps a job ID to the object that tracks the number
    // of documents already accumulated for this resultset.  The count of the number
    // of queue records we have is tallied by going through each job in this table
    // and adding the records outstanding for it.
    protected HashMap jobQueueHash = new HashMap();

    // This is the map from jobid to connection name
    protected HashMap jobConnection = new HashMap();

    // This is the set of allowed connection names.  We discard all documents that are
    // not from that set.
    protected HashMap activeConnections = new HashMap();

    // This is the number of documents per set per connection.
    protected HashMap setSizes = new HashMap();

    // These are the individual connection maximums, keyed by connection name.
    protected HashMap maxConnectionCounts = new HashMap();

    // This is the maximum number of documents per set over all the connections we are looking at.  This helps us establish a sanity limit.
    protected int maxSetSize = 0;

    // This is the number of documents processed so far
    protected int documentsProcessed = 0;

    // This is where we accumulate blocking documents.  This is an arraylist of DocumentDescription objects.
    protected ArrayList blockingDocumentArray = new ArrayList();

    // Cutoff time for documents eligible for prioritization
    protected long prioritizationTime;

    /** Constructor.
    * This class is built up piecemeal, so the constructor does nothing.
    *@param n is the maximum number of full job descriptions we want at this time.
    */
    public ThrottleLimit(int n, long prioritizationTime)
    {
      this.n = n;
      this.prioritizationTime = prioritizationTime;
      Logging.perf.debug("Limit instance created");
    }

    /** Transfer blocking documents discovered to BlockingDocuments object */
    public void tallyBlockingDocuments(BlockingDocuments blockingDocuments)
    {
      int i = 0;
      while (i < blockingDocumentArray.size())
      {
        DocumentDescription dd = (DocumentDescription)blockingDocumentArray.get(i++);
        blockingDocuments.addBlockingDocument(dd);
      }
      blockingDocumentArray.clear();
    }

    /** Add a job/connection name map entry.
    *@param jobID is the job id.
    *@param connectionName is the connection name.
    */
    public void addJob(Long jobID, String connectionName)
    {
      jobConnection.put(jobID,connectionName);
    }

    /** Add an active connection.  This is the pool of active connections that will be used for the lifetime of this operation.
    *@param connectionName is the connection name.
    */
    public void addConnectionName(String connectionName, IRepositoryConnector connectorInstance)
      throws ManifoldCFException
    {
      activeConnections.put(connectionName,connectorInstance);
      int setSize = connectorInstance.getMaxDocumentRequest();
      setSizes.put(connectionName,new Integer(setSize));
      if (setSize > maxSetSize)
        maxSetSize = setSize;
    }

    /** Add a document limit for a specified connection.  This is the limit across all matching bins; if any
    * individual matching bin exceeds that limit, then documents that belong to that bin will be excluded.
    *@param connectionName is the connection name.
    *@param regexp is the regular expression, which we will match against various bins.
    *@param upperLimit is the maximum count associated with the specified job.
    */
    public void addLimit(String connectionName, String regexp, int upperLimit)
    {
      if (Logging.perf.isDebugEnabled())
        Logging.perf.debug(" Adding fetch limit of "+Integer.toString(upperLimit)+" fetches for expression '"+regexp+"' for connection '"+connectionName+"'");

      ThrottleJobItem ji = (ThrottleJobItem)connectionMap.get(connectionName);
      if (ji == null)
      {
        ji = new ThrottleJobItem();
        connectionMap.put(connectionName,ji);
      }
      ji.addLimit(regexp,upperLimit);
    }

    /** Set a connection-based total document limit.
    */
    public void setConnectionLimit(String connectionName, int maxDocuments)
    {
      if (Logging.perf.isDebugEnabled())
        Logging.perf.debug(" Setting connection limit of "+Integer.toString(maxDocuments)+" for connection "+connectionName);
      maxConnectionCounts.put(connectionName,new MutableInteger(maxDocuments));
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
    * be cached, and therefore duplicate() is never called from the query executor.  But it can
    * be called from other places.
    *@return the duplicate.
    */
    public ILimitChecker duplicate()
    {
      return makeDeepCopy();
    }

    /** Make a deep copy */
    public ThrottleLimit makeDeepCopy()
    {
      ThrottleLimit rval = new ThrottleLimit(n,prioritizationTime);
      // Create a true copy of all the structures in which counts are kept.  The referential structures (e.g. connection hashes)
      // do not need a deep copy.
      rval.activeConnections = activeConnections;
      rval.setSizes = setSizes;
      rval.maxConnectionCounts = maxConnectionCounts;
      rval.maxSetSize = maxSetSize;
      rval.jobConnection = jobConnection;
      // The structures where counts are maintained DO need a deep copy.
      rval.documentsProcessed = documentsProcessed;
      Iterator iter;
      iter = connectionMap.keySet().iterator();
      while (iter.hasNext())
      {
        Object key = iter.next();
        rval.connectionMap.put(key,((ThrottleJobItem)connectionMap.get(key)).duplicate());
      }
      iter = jobQueueHash.keySet().iterator();
      while (iter.hasNext())
      {
        Object key = iter.next();
        rval.jobQueueHash.put(key,((QueueHashItem)jobQueueHash.get(key)).duplicate());
      }
      return rval;
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

    /** Get the remaining documents we should query for.
    *@return the maximal remaining count.
    */
    public int getRemainingDocuments()
    {
      return EXTRA_FACTOR * n * maxSetSize - documentsProcessed;
    }

    /** See if a result row should be included in the final result set.
    *@param row is the result row to check.
    *@return true if it should be included, false otherwise.
    */
    public boolean checkInclude(IResultRow row)
      throws ManifoldCFException
    {
      // Note: This method does two things: First, it insures that the number of documents per job per bin does
      // not exceed the calculated throttle number.  Second, it keeps track of how many document queue items
      // will be needed, so we can stop when we've got enough for the moment.
      Logging.perf.debug("Checking if row should be included");
      // This is the end that does the work.
      // The row passed in has the following jobqueue columns: idField, jobIDField, docIDField, and statusField
      Long jobIDValue = (Long)row.getValue(JobQueue.jobIDField);

      // Get the connection name for this row
      String connectionName = (String)jobConnection.get(jobIDValue);
      if (connectionName == null)
      {
        Logging.perf.debug(" Row does not have an eligible job - excluding");
        return false;
      }
      IRepositoryConnector connectorInstance = (IRepositoryConnector)activeConnections.get(connectionName);
      if (connectorInstance == null)
      {
        Logging.perf.debug(" Row does not have an eligible connector instance - excluding");
        return false;
      }

      // Find the connection limit for this document
      MutableInteger connectionLimit = (MutableInteger)maxConnectionCounts.get(connectionName);
      if (connectionLimit != null)
      {
        if (connectionLimit.intValue() == 0)
        {
          Logging.perf.debug(" Row exceeds its connection limit - excluding");
          return false;
        }
        connectionLimit.decrement();
      }

      // Tally this item in the job queue hash, so we can detect when to stop
      QueueHashItem queueItem = (QueueHashItem)jobQueueHash.get(jobIDValue);
      if (queueItem == null)
      {
        // Need to talk to the connector to get a max number of docs per chunk
        int maxCount = ((Integer)setSizes.get(connectionName)).intValue();
        queueItem = new QueueHashItem(maxCount);
        jobQueueHash.put(jobIDValue,queueItem);

      }

      String docIDHash = (String)row.getValue(JobQueue.docHashField);
      String docID = (String)row.getValue(JobQueue.docIDField);

      // Figure out what the right bins are, given the data we have.
      // This will involve a call to the connector.
      String[] binNames = ManifoldCF.calculateBins(connectorInstance,docID);
      // Keep the running count, so we can abort without going through the whole set.
      documentsProcessed++;
      //scanRecord.addBins(binNames);

      ThrottleJobItem item = (ThrottleJobItem)connectionMap.get(connectionName);

      // If there is no schedule-based throttling on this connection, we're done.
      if (item == null)
      {
        queueItem.addDocument();
        Logging.perf.debug(" Row has no throttling - including");
        return true;
      }


      int j = 0;
      while (j < binNames.length)
      {
        if (item.isEmpty(binNames[j]))
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug(" Bin "+binNames[j]+" has no more available fetches - excluding");

          Object o = row.getValue(JobQueue.prioritySetField);
          if (o == null || ((Long)o).longValue() <= prioritizationTime)
          {
            // Need to add a document descriptor based on this row to the blockingDocuments object!
            // This will cause it to be reprioritized preferentially, getting it out of the way if it shouldn't
            // be there.
            Long id = (Long)row.getValue(JobQueue.idField);
            Long jobID = (Long)row.getValue(JobQueue.jobIDField);
            DocumentDescription dd = new DocumentDescription(id,jobID,docIDHash,docID);
            blockingDocumentArray.add(dd);
          }

          return false;
        }
        j++;
      }
      j = 0;
      while (j < binNames.length)
      {
        item.decrement(binNames[j++]);
      }
      queueItem.addDocument();
      Logging.perf.debug(" Including!");
      return true;
    }

    /** See if we should examine another row.
    *@return true if we need to keep going, or false if we are done.
    */
    public boolean checkContinue()
      throws ManifoldCFException
    {
      if (documentsProcessed >= EXTRA_FACTOR * n * maxSetSize)
        return false;

      // If the number of chunks exceeds n, we are done
      Iterator iter = jobQueueHash.keySet().iterator();
      int count = 0;
      while (iter.hasNext())
      {
        Long jobID = (Long)iter.next();
        QueueHashItem item = (QueueHashItem)jobQueueHash.get(jobID);
        count += item.getChunkCount();
        if (count > n)
          return false;
      }
      return true;
    }



  }

  /** This class contains information per job on how many queue items have so far been accumulated.
  */
  protected static class QueueHashItem
  {
    // The number of items per chunk for this job
    int itemsPerChunk;
    // The number of chunks so far, INCLUDING incomplete chunks
    int chunkCount = 0;
    // The number of documents in the current incomplete chunk
    int currentDocumentCount = 0;

    /** Construct.
    *@param itemsPerChunk is the number of items per chunk for this job.
    */
    public QueueHashItem(int itemsPerChunk)
    {
      this.itemsPerChunk = itemsPerChunk;
    }

    /** Duplicate. */
    public QueueHashItem duplicate()
    {
      QueueHashItem rval = new QueueHashItem(itemsPerChunk);
      rval.chunkCount = chunkCount;
      rval.currentDocumentCount = currentDocumentCount;
      return rval;
    }

    /** Add a document to this job.
    */
    public void addDocument()
    {
      currentDocumentCount++;
      if (currentDocumentCount == 1)
        chunkCount++;
      if (currentDocumentCount == itemsPerChunk)
        currentDocumentCount = 0;
    }

    /** Get the number of chunks.
    *@return the number of chunks.
    */
    public int getChunkCount()
    {
      return chunkCount;
    }
  }

  /** This class represents the information stored PER JOB in the throttling structure.
  * In this structure, "remaining" counts are kept for each bin.  When the bin becomes empty,
  * then no more documents that would map to that bin will be returned, for this query.
  *
  * The way in which the maximum count per bin is determined is not part of this class.
  */
  protected static class ThrottleJobItem
  {
    /** These are the bin limits.  This is an array of ThrottleLimitSpec objects. */
    protected ArrayList throttleLimits = new ArrayList();
    /** This is a map of the bins and their current counts. If an entry doesn't exist, it's considered to be
    * the same as maxBinCount. */
    protected HashMap binCounts = new HashMap();

    /** Constructor. */
    public ThrottleJobItem()
    {
    }

    /** Add a bin limit.
    *@param regexp is the regular expression describing the bins to which the limit applies to.
    *@param maxCount is the maximum number of fetches allowed for that bin.
    */
    public void addLimit(String regexp, int maxCount)
    {
      try
      {
        throttleLimits.add(new ThrottleLimitSpec(regexp,maxCount));
      }
      catch (PatternSyntaxException e)
      {
        // Ignore the bad entry; it just won't contribute any throttling.
      }
    }

    /** Create a duplicate of this item.
    *@return the duplicate.
    */
    public ThrottleJobItem duplicate()
    {
      ThrottleJobItem rval = new ThrottleJobItem();
      rval.throttleLimits = throttleLimits;
      Iterator iter = binCounts.keySet().iterator();
      while (iter.hasNext())
      {
        String key = (String)iter.next();
        this.binCounts.put(key,((MutableInteger)binCounts.get(key)).duplicate());
      }
      return rval;
    }

    /** Check if the specified bin is empty.
    *@param binName is the bin name.
    *@return true if empty.
    */
    public boolean isEmpty(String binName)
    {
      MutableInteger value = (MutableInteger)binCounts.get(binName);
      int remaining;
      if (value == null)
      {
        int x = findMaxCount(binName);
        if (x == -1)
          return false;
        remaining = x;
      }
      else
        remaining = value.intValue();
      return (remaining == 0);
    }

    /** Decrement specified bin.
    *@param binName is the bin name.
    */
    public void decrement(String binName)
    {
      MutableInteger value = (MutableInteger)binCounts.get(binName);
      if (value == null)
      {
        int x = findMaxCount(binName);
        if (x == -1)
          return;
        value = new MutableInteger(x);
        binCounts.put(binName,value);
      }
      value.decrement();
    }

    /** Given a bin name, find the max value for it using the regexps that are in place.
    *@param binName is the bin name.
    *@return the max count for that bin, or -1 if infinite.
    */
    protected int findMaxCount(String binName)
    {
      // Each connector generates a set of bins per descriptor, e.g. "", ".com", ".metacarta.com", "foo.metacarta.com"
      //
      // We want to be able to do a couple of different kinds of things easily.  For example, we want to:
      // - be able to "turn off" or restrict fetching for a given domain, to a lower value than for other domains
      // - be able to control fetch rates of .com, .metacarta.com, and foo.metacarta.com such that we
      //   can establish a faster rate for .com than for foo.metacarta.com
      //
      // The standard case is to limit fetch rate for all terminal domains (e.g. foo.metacarta.com) to some number:
      //    ^[^\.] = 8
      //
      // To apply an additional limit restriction on a specific domain easily requires that the MINIMUM rate
      // value be chosen when more than one regexp match is found:
      //    ^[^\.] = 8
      //    ^foo\.metacarta\.com = 4
      //
      // To apply different rates for different levels:
      //    ^[^\.] = 8
      //    ^\.[^\.]*\.[^\.]*$ = 20
      //    ^\.[^\.]*$ = 40
      //

      // If the same bin is matched by more than one regexp, I now take the MINIMUM value, since this seems to be
      // more what the world wants to do (restrict, rather than increase, fetch rates).
      int maxCount = -1;
      int i = 0;
      while (i < throttleLimits.size())
      {
        ThrottleLimitSpec spec = (ThrottleLimitSpec)throttleLimits.get(i++);
        Pattern p = spec.getRegexp();
        Matcher m = p.matcher(binName);
        if (m.find())
        {
          int limit = spec.getMaxCount();
          if (maxCount == -1 || limit < maxCount)
            maxCount = limit;
        }
      }

      return maxCount;
    }
  }

  /** This is a class which describes an individual throttle limit, in fetches. */
  protected static class ThrottleLimitSpec
  {
    /** Regexp */
    protected Pattern regexp;
    /** The fetch limit for all bins matching that regexp */
    protected int maxCount;

    /** Constructor */
    public ThrottleLimitSpec(String regexp, int maxCount)
      throws PatternSyntaxException
    {
      this.regexp = Pattern.compile(regexp);
      this.maxCount = maxCount;
    }

    /** Get the regexp. */
    public Pattern getRegexp()
    {
      return regexp;
    }

    /** Get the max count */
    public int getMaxCount()
    {
      return maxCount;
    }
  }

  /** Mutable integer class.
  */
  protected static class MutableInteger
  {
    int value;

    /** Construct.
    */
    public MutableInteger(int value)
    {
      this.value = value;
    }

    /** Duplicate */
    public MutableInteger duplicate()
    {
      return new MutableInteger(value);
    }

    /** Decrement.
    */
    public void decrement()
    {
      value--;
    }

    /** Increment.
    */
    public void increment()
    {
      value++;
    }

    /** Get value.
    */
    public int intValue()
    {
      return value;
    }
  }


}

