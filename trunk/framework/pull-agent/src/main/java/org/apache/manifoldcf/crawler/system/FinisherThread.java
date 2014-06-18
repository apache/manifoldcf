/* $Id: FinisherThread.java 991295 2010-08-31 19:12:14Z kwright $ */

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

/** This class represents the finisher thread.  This thread's job is to detect when a job is completed, and mark it as such.
*/
public class FinisherThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: FinisherThread.java 991295 2010-08-31 19:12:14Z kwright $";

  // Local data
  /** Process ID */
  protected final String processID;

  /** Constructor.
  */
  public FinisherThread(String processID)
    throws ManifoldCFException
  {
    super();
    this.processID = processID;
    setName("Finisher thread");
    setDaemon(true);
  }

  public void run()
  {
    Logging.threads.debug("Start up finisher thread");
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          Logging.threads.debug("Cleaning up completed jobs...");
          // See if there are any completed jobs
          jobManager.finishJobs();
          Logging.threads.debug("Done cleaning up completed jobs");
          ManifoldCF.sleep(10000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Finisher thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("FinisherThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
