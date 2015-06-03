/* $Id: JobStartThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;
import java.lang.reflect.*;

/** This class describes the thread that starts up jobs.  Basically, it periodically evaluates whether any jobs are ready to begin,
* if it finds any, starts them.
*/
public class JobStartThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: JobStartThread.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  */
  public JobStartThread(String processID)
    throws ManifoldCFException
  {
    super();
    this.processID = processID;
    setName("Job start thread");
    setDaemon(true);
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);

      // Loop
      while (true)
      {
        if (Thread.currentThread().isInterrupted())
          break;

        // Do another try/catch around everything in the loop
        try
        {
          // Get current time
          long currentTime = System.currentTimeMillis();
          // Log it
          if (Logging.threads.isDebugEnabled())
            Logging.threads.debug("Job start thread - checking for jobs to start at "+new Long(currentTime).toString());
          // Start any waiting jobs
          List<Long> unwaitJobs = new ArrayList<Long>();
          jobManager.startJobs(currentTime,unwaitJobs);
          // Log these events in the event log
          int k = 0;
          while (k < unwaitJobs.size())
          {
            Long jobID = unwaitJobs.get(k++);
            IJobDescription desc = jobManager.load(jobID);
            connectionManager.recordHistory(desc.getConnectionName(),
              null,connectionManager.ACTIVITY_JOBUNWAIT,null,
              desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
          }
          // Cause jobs out of window to stop.
          List<Long> waitJobs = new ArrayList<Long>();
          jobManager.waitJobs(currentTime,waitJobs);
          k = 0;
          while (k < waitJobs.size())
          {
            Long jobID = waitJobs.get(k++);
            IJobDescription desc = jobManager.load(jobID);
            connectionManager.recordHistory(desc.getConnectionName(),
              null,connectionManager.ACTIVITY_JOBWAIT,null,
              desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
          }
          // Loop around again, after resting a while
          ManifoldCF.sleep(10000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Job start thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("JobStartThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
