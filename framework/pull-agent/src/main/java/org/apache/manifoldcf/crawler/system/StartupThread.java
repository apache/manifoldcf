/* $Id: StartupThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents the startup thread.  This thread's job is to detect when a job is starting, and stuff its queue
* appropriately.  Finally, the job is marked as "started".
*/
public class StartupThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: StartupThread.java 988245 2010-08-23 18:39:35Z kwright $";

  // Local data
  /** Process ID */
  protected final String processID;
  /** Reset manager */
  protected final StartupResetManager resetManager;
  
  /** Constructor.
  */
  public StartupThread(StartupResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    setName("Startup thread");
    setDaemon(true);
    this.resetManager = resetManager;
    this.processID = processID;
  }

  public void run()
  {
    resetManager.registerMe();

    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);
      IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
      
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // Before we begin, conditionally reset
          resetManager.waitForReset(threadContext);

          // Accumulate the wait before doing the next check.
          // We start with 10 seconds, which is the maximum.  If there's a service request
          // that's faster than that, we'll adjust the time downward.
          long waitTime = 10000L;

          if (Logging.threads.isDebugEnabled())
            Logging.threads.debug("Checking for starting jobs");

          // See if there are any starting jobs.
          // Note: Since this following call changes the job state, we must be careful to reset it on any kind of failure.
          JobStartRecord[] startupJobs = jobManager.getJobsReadyForStartup(processID);
          try
          {

            if (startupJobs.length == 0)
            {
              ManifoldCF.sleep(waitTime);
              continue;
            }

            if (Logging.threads.isDebugEnabled())
              Logging.threads.debug("Found "+Integer.toString(startupJobs.length)+" jobs ready to be started");

            long currentTime = System.currentTimeMillis();


            // Loop through jobs
            for (int i = 0; i < startupJobs.length; i++)
            {
              JobStartRecord jsr = startupJobs[i];
              Long jobID = jsr.getJobID();
              try
              {
                String lastSeedingVersion = jsr.getSeedingVersionString();
                IJobDescription jobDescription = jobManager.load(jobID,true);

                int jobType = jobDescription.getType();
                int hopcountMethod = jobDescription.getHopcountMode();

                IRepositoryConnection connection = connectionMgr.load(jobDescription.getConnectionName());
                IRepositoryConnector connector = repositoryConnectorPool.grab(connection);

                // If the attempt to grab a connector instance failed, don't start the job, of course.
                if (connector == null)
                  continue;

                String newSeedingVersion = null;
                try
                {
                  // Only now record the fact that we are trying to start the job.
                  connectionMgr.recordHistory(jobDescription.getConnectionName(),
                    null,connectionMgr.ACTIVITY_JOBSTART,null,
                    jobID.toString()+"("+jobDescription.getDescription()+")",null,null,null);

                  int model = connector.getConnectorModel();
                  // Get the number of link types.
                  String[] legalLinkTypes = connector.getRelationshipTypes();

                  boolean requestMinimum = jsr.getRequestMinimum();
                  
                  if (Logging.threads.isDebugEnabled())
                    Logging.threads.debug("Preparing job "+jobID.toString()+" for execution...");
                  jobManager.prepareJobScan(jobID,legalLinkTypes,hopcountMethod,
                    model,jobType == IJobDescription.TYPE_CONTINUOUS,lastSeedingVersion == null,
                    requestMinimum);
                  
                  if (Logging.threads.isDebugEnabled())
                    Logging.threads.debug("Prepared job "+jobID.toString()+" for execution.");

                  try
                  {
                    SeedingActivity activity = new SeedingActivity(connection.getName(),connectionMgr,
                      jobManager,rt,
                      connection,connector,jobID,legalLinkTypes,true,hopcountMethod,processID);

                    if (Logging.threads.isDebugEnabled())
                      Logging.threads.debug("Adding initial seed documents for job "+jobID.toString()+"...");
                    // Get the initial seed documents, and make sure those are added
                    newSeedingVersion = connector.addSeedDocuments(activity,jobDescription.getSpecification(),lastSeedingVersion,currentTime,jobType);
                    // Flush anything left
                    activity.doneSeeding(model==connector.MODEL_PARTIAL);
                    if (Logging.threads.isDebugEnabled())
                      Logging.threads.debug("Done adding initial seed documents for job "+jobID.toString()+".");
                  }
                  catch (ServiceInterruption e)
                  {
                    if (!e.jobInactiveAbort())
                    {
                      Logging.jobs.warn("Startup service interruption reported for job "+
                        jobID+" connection '"+connection.getName()+"': "+
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
                        String message = e.jobInactiveAbort()?"":"Repeated service interruptions during startup"+((e.getCause()!=null)?": "+e.getCause().getMessage():"");
                        if (jobManager.errorAbort(jobID,message) && message.length() > 0)
                          Logging.jobs.error(message,e.getCause());
                        jsr.noteStarted();
                      }
                      else
                      {
                        // Not sure this can happen -- but just transition silently to active state
                        jobManager.noteJobStarted(jobID,currentTime,newSeedingVersion);
                        jsr.noteStarted();
                      }
                    }
                    else
                    {
                      // Reset the job to the READYFORSTARTUP state, updating the failtime and failcount fields
                      jobManager.retryStartup(jsr,e.getFailTime(),e.getFailRetryCount());
                      jsr.noteStarted();
                    }
                    // Go on to the next job
                    continue;
                  }
                }
                finally
                {
                  repositoryConnectorPool.release(connection,connector);
                }

                // Start this job!
                jobManager.noteJobStarted(jobID,currentTime,newSeedingVersion);
                jsr.noteStarted();
              }
              catch (ManifoldCFException e)
              {
                if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                  throw new InterruptedException();
                if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
                  throw e;
                // Note: The error abort below will put the job in the "ABORTING" state. 
                if (jobManager.errorAbort(jobID,e.getMessage()))
                  Logging.threads.error("Exception tossed: "+e.getMessage(),e);
                jsr.noteStarted();
              }
            }
          }
          finally
          {
            // Clean up all jobs that did not start
            ManifoldCFException exception = null;
            int i = 0;
            while (i < startupJobs.length)
            {
              JobStartRecord jsr = startupJobs[i++];
              if (!jsr.wasStarted())
              {
                // Clean up from failed start.
                try
                {
                  jobManager.resetStartupJob(jsr.getJobID());
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

          // Sleep for the retry interval.
          ManifoldCF.sleep(waitTime);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            resetManager.noteEvent();

            Logging.threads.error("Startup thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("StartupThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
