/* $Id: JobDeleteThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents the job delete thread.  This thread's job is to find jobs that are marked for delete
* and which have no remaining ingested documents, and delete them.
* The query which locates these jobs is in fact where all the work is; this query finds only jobs where there are
* no remaining documents in the "completed", "purgatory", or "pendingpurgatory" states, which are in the "READYFORDELETE" state.
*/
public class JobDeleteThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: JobDeleteThread.java 988245 2010-08-23 18:39:35Z kwright $";

  // Local data
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  */
  public JobDeleteThread(String processID)
    throws ManifoldCFException
  {
    super();
    this.processID = processID;
    setName("Job delete thread");
    setDaemon(true);
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // Accumulate the wait before doing the next check.
          // We start with 10 seconds, which is the maximum.  If there's a service request
          // that's faster than that, we'll adjust the time downward.
          long waitTime = 10000L;

          // See if there are any starting jobs.
          // Note: Since this following call changes the job state, we must be careful to reset it on any kind of failure.
          Logging.threads.debug("Deleting jobs that need cleanup");
          jobManager.deleteJobsReadyForDelete();

          // Sleep for the retry interval.
          ManifoldCF.sleep(waitTime);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Job delete thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("JobDeleteThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
