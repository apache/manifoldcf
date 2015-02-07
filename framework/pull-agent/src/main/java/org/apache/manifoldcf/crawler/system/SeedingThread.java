/* $Id: SeedingThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents the background seeding thread.  Its job is to add
* seeded documents from the connector periodically, during adaptive crawls
* (which continue until stopped).  The actual use case is for creating a
* connector that handles RSS feeds, including keeping them current and
* handling deletions.
*/
public class SeedingThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: SeedingThread.java 988245 2010-08-23 18:39:35Z kwright $";

  // Local data
  /** Seeding reset manager */
  protected final SeedingResetManager resetManager;
  /** Process ID */
  protected final String processID;

  /** The number of documents that are added to the queue per transaction */
  protected final static int MAX_COUNT = 100;

  /** Constructor.
  */
  public SeedingThread(SeedingResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    setName("Seeding thread");
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
      
      String[] identifiers = new String[MAX_COUNT];
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // Before we begin, conditionally reset
          resetManager.waitForReset(threadContext);

          long currentTime = System.currentTimeMillis();

          // Accumulate the wait before doing the next check.
          // We start with 10 seconds, which is the maximum.  If there's a service request
          // that's faster than that, we'll adjust the time downward.
          long waitTime = 60000L;

          Logging.threads.debug("Seeding thread woke up");

          // Grab active, adaptive jobs (and set their state to xxxSEEDING as a side effect)
          JobSeedingRecord[] seedJobs = jobManager.getJobsReadyForSeeding(processID,currentTime);

          // Process these jobs, and do the seeding.  The seeding is based on what came back
          // in the job start record for sync time.  If there's an interruption, we just go on
          // to the next job, since the whole thing will retry anyhow.
          try
          {

            if (seedJobs.length == 0)
            {
              Logging.threads.debug("Seeding thread found nothing to do");
              ManifoldCF.sleep(waitTime);
              continue;
            }

            if (Logging.threads.isDebugEnabled())
              Logging.threads.debug("Seeding thread: Found "+Integer.toString(seedJobs.length)+" jobs to seed");

            // Loop through jobs
            int i = 0;
            while (i < seedJobs.length)
            {
              JobSeedingRecord jsr = seedJobs[i++];
              Long jobID = jsr.getJobID();
              try
              {
                String lastSeedingVersion = jsr.getSeedingVersionString();
                IJobDescription jobDescription = jobManager.load(jobID,true);
                int jobType = jobDescription.getType();

                int hopcountMethod = jobDescription.getHopcountMode();

                IRepositoryConnection connection = connectionMgr.load(jobDescription.getConnectionName());
                IRepositoryConnector connector = repositoryConnectorPool.grab(connection);
                // Null will come back if the connector instance could not be obtained, so just skip in that case.
                if (connector == null)
                  continue;

                String newSeedingVersion = null;
                try
                {
                  
                  // Get the number of link types.
                  String[] legalLinkTypes = connector.getRelationshipTypes();

                  int model = connector.getConnectorModel();

                  try
                  {

                    SeedingActivity activity = new SeedingActivity(connection.getName(),connectionMgr,
                      jobManager,rt,
                      connection,connector,jobID,legalLinkTypes,false,hopcountMethod,processID);

                    if (Logging.threads.isDebugEnabled())
                      Logging.threads.debug("Seeding thread: Getting seeds for job "+jobID.toString());

                    newSeedingVersion = connector.addSeedDocuments(activity,jobDescription.getSpecification(),lastSeedingVersion,currentTime,jobType);

                    activity.doneSeeding(model==connector.MODEL_PARTIAL);

                    if (Logging.threads.isDebugEnabled())
                      Logging.threads.debug("Seeding thread: Done processing seeds from job "+jobID.toString());


                  }
                  catch (ServiceInterruption e)
                  {
                    if (!e.jobInactiveAbort())
                    {
                      Logging.jobs.warn("Seeding service interruption reported for job "+
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
                        String message = e.jobInactiveAbort()?"":"Repeated service interruptions during seeding"+((e.getCause()!=null)?": "+e.getCause().getMessage():"");
                        if (jobManager.errorAbort(jobID,message) && message.length() > 0)
                          Logging.jobs.error(message,e.getCause());
                        jsr.noteStarted();
                      }
                      else
                      {
                        // Not sure this can happen -- but just transition silently to active state
                        jobManager.noteJobSeeded(jobID,newSeedingVersion);
                        jsr.noteStarted();
                      }
                    }
                    else
                    {
                      // Reset the job to the READYFORSTARTUP state, updating the failtime and failcount fields
                      jobManager.retrySeeding(jsr,e.getFailTime(),e.getFailRetryCount());
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


                if (Logging.threads.isDebugEnabled())
                  Logging.threads.debug("Seeding thread: Successfully reseeded job "+jobID.toString());

                // Note that this job has been seeded!
                jobManager.noteJobSeeded(jobID,newSeedingVersion);
                jsr.noteStarted();

              }
              catch (ManifoldCFException e)
              {
                if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                  throw new InterruptedException();
                if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
                  throw e;
                if (jobManager.errorAbort(jobID,e.getMessage()))
                  Logging.threads.error("Exception tossed: "+e.getMessage(),e);
                jsr.noteStarted();
              }
            }
          }
          finally
          {
            // Clean up all jobs that did not seed
            ManifoldCFException exception = null;
            int i = 0;
            while (i < seedJobs.length)
            {
              JobSeedingRecord jsr = seedJobs[i++];
              if (!jsr.wasStarted())
              {
                if (Logging.threads.isDebugEnabled())
                  Logging.threads.debug("Seeding thread: aborting reseed for "+jsr.getJobID().toString());

                // Clean up from failed seed.
                try
                {
                  jobManager.resetSeedJob(jsr.getJobID());
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

            Logging.threads.error("Seeding thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("SeedingThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
