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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This class represents the start delete thread.  This thread's job is to detect when a job is
* ready for deletion, initialize it, and put it into the DELETING state.
*/
public class StartDeleteThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  /** Delete startup reset manager */
  protected final DeleteStartupResetManager resetManager;
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  */
  public StartDeleteThread(DeleteStartupResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    this.resetManager = resetManager;
    this.processID = processID;
    setName("Delete startup thread");
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
      IRepositoryConnectionManager connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);

      IDBInterface database = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

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
            Logging.threads.debug("Checking for deleting jobs");

          // See if there are any starting jobs.
          // Note: Since this following call changes the job state, we must be careful to reset it on any kind of failure.
          JobDeleteRecord[] deleteJobs = jobManager.getJobsReadyForDeleteCleanup(processID);
          try
          {

            if (deleteJobs.length == 0)
            {
              ManifoldCF.sleep(waitTime);
              continue;
            }

            if (Logging.threads.isDebugEnabled())
              Logging.threads.debug("Found "+Integer.toString(deleteJobs.length)+" jobs ready to be deleted");

            long currentTime = System.currentTimeMillis();


            // Loop through jobs
            int i = 0;
            while (i < deleteJobs.length)
            {
              JobDeleteRecord jsr = deleteJobs[i++];
              Long jobID = jsr.getJobID();
	      try
	      {
		jobManager.prepareDeleteScan(jobID);
                // Start deleting this job!
                jobManager.noteJobDeleteStarted(jobID,currentTime);
                jsr.noteStarted();
              }
              catch (ManifoldCFException e)
              {
                if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                  throw new InterruptedException();
                if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
                  throw e;
		// We cannot abort the delete startup, but if we fall through, we'll put the job back into
		// the state whence it came.  So, fall through.
		Logging.threads.error("Exception tossed: "+e.getMessage(),e);
              }
            }
          }
          finally
          {
            // Clean up all jobs that did not start
            ManifoldCFException exception = null;
            int i = 0;
            while (i < deleteJobs.length)
            {
              JobDeleteRecord jsr = deleteJobs[i++];
              if (!jsr.wasStarted())
              {
                // Clean up from failed start.
                try
                {
                  jobManager.resetStartDeleteJob(jsr.getJobID());
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

            Logging.threads.error("Start delete thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("StartDeleteThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
