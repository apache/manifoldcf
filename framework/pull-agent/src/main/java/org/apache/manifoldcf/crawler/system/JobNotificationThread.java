/* $Id: JobNotificationThread.java 998081 2010-09-17 11:33:15Z kwright $ */

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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This class represents the thread that notices jobs that have completed their "notify connector" phase, and resets them back to
* inactive.
*/
public class JobNotificationThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: JobNotificationThread.java 998081 2010-09-17 11:33:15Z kwright $";

  /** Notification reset manager */
  protected final NotificationResetManager resetManager;
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  */
  public JobNotificationThread(NotificationResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    this.resetManager = resetManager;
    this.processID = processID;
    setName("Job notification thread");
    setDaemon(true);
  }

  public void run()
  {
    resetManager.registerMe();

    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(threadContext);
      IRepositoryConnectionManager repositoryConnectionManager = RepositoryConnectionManagerFactory.make(threadContext);

      IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
      
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // Before we begin, conditionally reset
          resetManager.waitForReset(threadContext);

          // Find the jobs ready for inactivity and notify them
          JobNotifyRecord[] jobsNeedingNotification = jobManager.getJobsReadyForInactivity(processID);
          try
          {
            Set<OutputAndRepositoryConnection> connectionNames = new HashSet<OutputAndRepositoryConnection>();
            
            for (JobNotifyRecord jsr : jobsNeedingNotification)
            {
              Long jobID = jsr.getJobID();
              IJobDescription job = jobManager.load(jobID,true);
              if (job != null)
              {
                // Get the connection name
                String repositoryConnectionName = job.getConnectionName();
                IPipelineSpecificationBasic basicSpec = new PipelineSpecificationBasic(job);
                for (int i = 0; i < basicSpec.getOutputCount(); i++)
                {
                  String outputConnectionName = basicSpec.getStageConnectionName(basicSpec.getOutputStage(i));
                  OutputAndRepositoryConnection c = new OutputAndRepositoryConnection(outputConnectionName, repositoryConnectionName);
                  connectionNames.add(c);
                }
              }
            }
            
            // Attempt to notify the specified connections
            Map<OutputAndRepositoryConnection,Disposition> notifiedConnections = new HashMap<OutputAndRepositoryConnection,Disposition>();
            
            for (OutputAndRepositoryConnection connections : connectionNames)
            {
              String outputConnectionName = connections.getOutputConnectionName();
              String repositoryConnectionName = connections.getRepositoryConnectionName();
              
              OutputNotifyActivity activity = new OutputNotifyActivity(repositoryConnectionName,repositoryConnectionManager,outputConnectionName);
              
              IOutputConnection connection = connectionManager.load(outputConnectionName);
              if (connection != null)
              {
                // Grab an appropriate connection instance
                IOutputConnector connector = outputConnectorPool.grab(connection);
                if (connector != null)
                {
                  try
                  {
                    // Do the notification itself
                    try
                    {
                      connector.noteJobComplete(activity);
                      notifiedConnections.put(connections,new Disposition());
                    }
                    catch (ServiceInterruption e)
                    {
                      notifiedConnections.put(connections,new Disposition(e));
                    }
                    catch (ManifoldCFException e)
                    {
                      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                        throw e;
                      if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
                        throw e;
                      if (e.getErrorCode() == ManifoldCFException.SETUP_ERROR)
                        throw e;
                      // Nothing special; report the error and keep going.
                      Logging.threads.error(e.getMessage(),e);
                    }
                  }
                  finally
                  {
                    outputConnectorPool.release(connection,connector);
                  }
                }
              }
            }
            
            // Go through jobs again, and put the notified ones into the inactive state.
            for (JobNotifyRecord jsr : jobsNeedingNotification)
            {
              Long jobID = jsr.getJobID();
              IJobDescription job = jobManager.load(jobID,true);
              if (job != null)
              {
                // Get the connection name
                String repositoryConnectionName = job.getConnectionName();
                IPipelineSpecificationBasic basicSpec = new PipelineSpecificationBasic(job);
                boolean allOK = true;
                for (int i = 0; i < basicSpec.getOutputCount(); i++)
                {
                  String outputConnectionName = basicSpec.getStageConnectionName(basicSpec.getOutputStage(i));

                  OutputAndRepositoryConnection c = new OutputAndRepositoryConnection(outputConnectionName, repositoryConnectionName);
                  
                  Disposition d = notifiedConnections.get(c);
                  if (d != null)
                  {
                    ServiceInterruption e = d.getServiceInterruption();
                    if (e == null)
                    {
                      break;
                    }
                    else
                    {
                      if (!e.jobInactiveAbort())
                      {
                        Logging.jobs.warn("Notification service interruption reported for job "+
                          jobID+" output connection '"+outputConnectionName+"': "+
                          e.getMessage(),e);
                      }

                      // If either we are going to be requeuing beyond the fail time, OR
                      // the number of retries available has hit 0, THEN we treat this
                      // as either an "ignore" or a hard error.
                      if (!e.jobInactiveAbort() && (jsr.getFailTime() != -1L && jsr.getFailTime() < e.getRetryTime() ||
                        jsr.getFailRetryCount() == 0))
                      {
                        // Treat this as a hard failure.
                        if (e.isAbortOnFail())
                        {
                          // Note the error in the job, and transition to inactive state
                          String message = e.jobInactiveAbort()?"":"Repeated service interruptions during notification"+((e.getCause()!=null)?": "+e.getCause().getMessage():"");
                          if (jobManager.errorAbort(jobID,message) && message.length() > 0)
                            Logging.jobs.error(message,e.getCause());
                          jsr.noteStarted();
                        }
                        else
                        {
                          // Not sure this can happen -- but just transition silently to inactive state
                          jobManager.inactivateJob(jobID);
                          jsr.noteStarted();
                        }
                      }
                      else
                      {
                        // Reset the job to the READYFORNOTIFY state, updating the failtime and failcount fields
                        jobManager.retryNotification(jsr,e.getFailTime(),e.getFailRetryCount());
                        jsr.noteStarted();
                      }
                      allOK = false;
                      break;
                    }
                  }
                }
                if (allOK)
                {
                  jobManager.inactivateJob(jobID);
                  jsr.noteStarted();
                }

              }
            }
          }
          finally
          {
            // Clean up all jobs that did not start
            ManifoldCFException exception = null;
            int i = 0;
            while (i < jobsNeedingNotification.length)
            {
              JobNotifyRecord jsr = jobsNeedingNotification[i++];
              if (!jsr.wasStarted())
              {
                // Clean up from failed start.
                try
                {
                  jobManager.resetNotifyJob(jsr.getJobID());
                }
                catch (ManifoldCFException e)
                {
                  if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                    throw e;
                  exception = e;
                }
              }
            }
            if (exception != null)
              throw exception;
          }

          // We also need to do a notify for jobs that are about to be deleted
          JobNotifyRecord[] jobsNeedingDeleteNotification = jobManager.getJobsReadyForDelete(processID);
          try
          {
            Set<OutputAndRepositoryConnection> connectionNames = new HashSet<OutputAndRepositoryConnection>();
            
            for (JobNotifyRecord jsr : jobsNeedingDeleteNotification)
            {
              Long jobID = jsr.getJobID();
              IJobDescription job = jobManager.load(jobID,true);
              if (job != null)
              {
                // Get the connection name
                String repositoryConnectionName = job.getConnectionName();
                IPipelineSpecificationBasic basicSpec = new PipelineSpecificationBasic(job);
                for (int i = 0; i < basicSpec.getOutputCount(); i++)
                {
                  String outputConnectionName = basicSpec.getStageConnectionName(basicSpec.getOutputStage(i));
                  OutputAndRepositoryConnection c = new OutputAndRepositoryConnection(outputConnectionName, repositoryConnectionName);
                  connectionNames.add(c);
                }
              }
            }
            
            // Attempt to notify the specified connections
            Map<OutputAndRepositoryConnection,Disposition> notifiedConnections = new HashMap<OutputAndRepositoryConnection,Disposition>();
            
            for (OutputAndRepositoryConnection connections : connectionNames)
            {
              String outputConnectionName = connections.getOutputConnectionName();
              String repositoryConnectionName = connections.getRepositoryConnectionName();
              
              OutputNotifyActivity activity = new OutputNotifyActivity(repositoryConnectionName,repositoryConnectionManager,outputConnectionName);
              
              IOutputConnection connection = connectionManager.load(outputConnectionName);
              if (connection != null)
              {
                // Grab an appropriate connection instance
                IOutputConnector connector = outputConnectorPool.grab(connection);
                if (connector != null)
                {
                  try
                  {
                    // Do the notification itself
                    try
                    {
                      connector.noteJobComplete(activity);
                      notifiedConnections.put(connections,new Disposition());
                    }
                    catch (ServiceInterruption e)
                    {
                      notifiedConnections.put(connections,new Disposition(e));
                    }
                    catch (ManifoldCFException e)
                    {
                      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                        throw e;
                      if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
                        throw e;
                      if (e.getErrorCode() == ManifoldCFException.SETUP_ERROR)
                        throw e;
                      // Nothing special; report the error and keep going.
                      Logging.threads.error(e.getMessage(),e);
                    }
                  }
                  finally
                  {
                    outputConnectorPool.release(connection,connector);
                  }
                }
              }
            }
            
            // Go through jobs again, and put the notified ones into the inactive state.
            for (JobNotifyRecord jsr : jobsNeedingDeleteNotification)
            {
              Long jobID = jsr.getJobID();
              IJobDescription job = jobManager.load(jobID,true);
              if (job != null)
              {
                // Get the connection name
                String repositoryConnectionName = job.getConnectionName();
                IPipelineSpecificationBasic basicSpec = new PipelineSpecificationBasic(job);
                boolean allOK = true;
                for (int i = 0; i < basicSpec.getOutputCount(); i++)
                {
                  String outputConnectionName = basicSpec.getStageConnectionName(basicSpec.getOutputStage(i));

                  OutputAndRepositoryConnection c = new OutputAndRepositoryConnection(outputConnectionName, repositoryConnectionName);
                  
                  Disposition d = notifiedConnections.get(c);
                  if (d != null)
                  {
                    ServiceInterruption e = d.getServiceInterruption();
                    if (e == null)
                    {
                      break;
                    }
                    else
                    {
                      if (!e.jobInactiveAbort())
                      {
                        Logging.jobs.warn("Delete notification service interruption reported for job "+
                          jobID+" output connection '"+outputConnectionName+"': "+
                          e.getMessage(),e);
                      }

                      // If either we are going to be requeuing beyond the fail time, OR
                      // the number of retries available has hit 0, THEN we treat this
                      // as either an "ignore" or a hard error.
                      ///System.out.println("jsr.getFailTime()="+jsr.getFailTime()+"; e.getRetryTime()="+e.getRetryTime()+"; jsr.getFailRetryCount()="+jsr.getFailRetryCount());
                      if (!e.jobInactiveAbort() && (jsr.getFailTime() != -1L && jsr.getFailTime() < e.getRetryTime() ||
                        jsr.getFailRetryCount() == 0))
                      {
                        // Treat this as a hard failure.
                        if (e.isAbortOnFail())
                        {
                          // Note the error in the job, and transition to inactive state
                          String message = e.jobInactiveAbort()?"":"Repeated service interruptions during delete notification"+((e.getCause()!=null)?": "+e.getCause().getMessage():"");
                          if (message.length() > 0)
                            Logging.jobs.error(message,e.getCause());
                          // Can't abort a delete!!
                          jobManager.removeJob(jobID);
                          jsr.noteStarted();
                        }
                        else
                        {
                          // Not sure this can happen -- but just transition silently to inactive state
                          jobManager.removeJob(jobID);
                          jsr.noteStarted();
                        }
                      }
                      else
                      {
                        // Reset the job to the READYFORDELETENOTIFY state, updating the failtime and failcount fields
                        //System.out.println("Retrying... e.getFailTime()="+e.getFailTime()+"; e.getFailRetryCount()="+e.getFailRetryCount());
                        jobManager.retryDeleteNotification(jsr,e.getFailTime(),e.getFailRetryCount());
                        jsr.noteStarted();
                      }
                      allOK = false;
                      break;
                    }
                  }
                }
                if (allOK)
                {
                  jobManager.removeJob(jobID);
                  jsr.noteStarted();
                }

              }
            }
          }
          finally
          {
            // Clean up all jobs that did not start
            ManifoldCFException exception = null;
            int i = 0;
            while (i < jobsNeedingDeleteNotification.length)
            {
              JobNotifyRecord jsr = jobsNeedingDeleteNotification[i++];
              if (!jsr.wasStarted())
              {
                // Clean up from failed start.
                try
                {
                  jobManager.resetDeleteNotifyJob(jsr.getJobID());
                }
                catch (ManifoldCFException e)
                {
                  if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                    throw e;
                  exception = e;
                }
              }
            }
            if (exception != null)
              throw exception;
          }

          ManifoldCF.sleep(10000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            resetManager.noteEvent();
            
            Logging.threads.error("Job notification thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
            try
            {
              // Give the database a chance to catch up/wake up
              ManifoldCF.sleep(10000L);
            }
            catch (InterruptedException se)
            {
              break;
            }
            continue;
          }

          // Log it, but keep the thread alive
          Logging.threads.error("Exception tossed: "+e.getMessage(),e);

          if (e.getErrorCode() == ManifoldCFException.SETUP_ERROR)
          {
            // Shut the whole system down!
            System.exit(1);
          }

        }
        catch (InterruptedException e)
        {
          // We're supposed to quit
          break;
        }
        catch (OutOfMemoryError e)
        {
          System.err.println("agents process ran out of memory - shutting down");
          e.printStackTrace(System.err);
          System.exit(-200);
        }
        catch (Throwable e)
        {
          // A more severe error - but stay alive
          Logging.threads.fatal("Error tossed: "+e.getMessage(),e);
        }
      }
    }
    catch (Throwable e)
    {
      // Severe error on initialization
      System.err.println("agents process could not start - shutting down");
      Logging.threads.fatal("JobNotificationThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

  /** Disposition of an output/repository connection combination */
  protected static class Disposition
  {
    protected final ServiceInterruption serviceInterruption;
    
    public Disposition(ServiceInterruption serviceInterruption)
    {
      this.serviceInterruption = serviceInterruption;
    }
    
    public Disposition()
    {
      this.serviceInterruption = null;
    }
    
    public ServiceInterruption getServiceInterruption()
    {
      return serviceInterruption;
    }
  }
  
  /** Output connection/repository connection pair object */
  protected static class OutputAndRepositoryConnection
  {
    protected final String outputConnectionName;
    protected final String repositoryConnectionName;
    
    public OutputAndRepositoryConnection(String outputConnectionName, String repositoryConnectionName)
    {
      this.outputConnectionName = outputConnectionName;
      this.repositoryConnectionName = repositoryConnectionName;
    }
    
    public String getOutputConnectionName()
    {
      return outputConnectionName;
    }
    
    public String getRepositoryConnectionName()
    {
      return repositoryConnectionName;
    }
    
    public boolean equals(Object o)
    {
      if (!(o instanceof OutputAndRepositoryConnection))
        return false;
      OutputAndRepositoryConnection x = (OutputAndRepositoryConnection)o;
      return this.outputConnectionName.equals(x.outputConnectionName) && this.repositoryConnectionName.equals(x.repositoryConnectionName);
    }
    
    public int hashCode()
    {
      return outputConnectionName.hashCode() + repositoryConnectionName.hashCode();
    }
  }
  
  /** The ingest logger class */
  protected static class OutputNotifyActivity implements IOutputNotifyActivity
  {

    // Connection name
    protected String connectionName;
    // Connection manager
    protected IRepositoryConnectionManager connMgr;
    // Output connection name
    protected String outputConnectionName;

    /** Constructor */
    public OutputNotifyActivity(String connectionName, IRepositoryConnectionManager connMgr, String outputConnectionName)
    {
      this.connectionName = connectionName;
      this.connMgr = connMgr;
      this.outputConnectionName = outputConnectionName;
    }

    /** Record time-stamped information about the activity of the output connector.
    *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
    *       activity has an associated time; the startTime field records when the activity began.  A null value
    *       indicates that the start time and the finishing time are the same.
    *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
    *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
    *       "fetch document" activity.  Cannot be null.
    *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
    *@param entityURI is a (possibly long) string which identifies the object involved in the history record.
    *       The interpretation of this field will differ from connector to connector.  May be null.
    *@param resultCode contains a terse description of the result of the activity.  The description is limited in
    *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
    *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
    *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
    */
    public void recordActivity(Long startTime, String activityType, Long dataSize,
      String entityURI, String resultCode, String resultDescription)
      throws ManifoldCFException
    {
      connMgr.recordHistory(connectionName,startTime,ManifoldCF.qualifyOutputActivityName(activityType,outputConnectionName),dataSize,entityURI,resultCode,
        resultDescription,null);
    }

  }
  
}
