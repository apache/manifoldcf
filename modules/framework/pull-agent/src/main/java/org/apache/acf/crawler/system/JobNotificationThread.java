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
package org.apache.acf.crawler.system;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This class represents the thread that notices jobs that have completed their "notify connector" phase, and resets them back to
* inactive.
*/
public class JobNotificationThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  /** Constructor.
  */
  public JobNotificationThread()
    throws ACFException
  {
    super();
    setName("Job notification thread");
    setDaemon(true);
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(threadContext);

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          Long[] jobsNeedingNotification = jobManager.getJobsReadyForInactivity();
          
          HashMap connectionNames = new HashMap();
          
          int k = 0;
          while (k < jobsNeedingNotification.length)
          {
            Long jobID = jobsNeedingNotification[k++];
            IJobDescription job = jobManager.load(jobID,true);
            if (job != null)
            {
              // Get the connection name
              String connectionName = job.getOutputConnectionName();
              connectionNames.put(connectionName,connectionName);
            }
          }
          
          // Attempt to notify the specified connections
          HashMap notifiedConnections = new HashMap();
          
          Iterator iter = connectionNames.keySet().iterator();
          while (iter.hasNext())
          {
            String connectionName = (String)iter.next();
            
            IOutputConnection connection = connectionManager.load(connectionName);
            if (connection != null)
            {
              // Grab an appropriate connection instance
              IOutputConnector connector = OutputConnectorFactory.grab(threadContext,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
              if (connector != null)
              {
                try
                {
                  // Do the notification itself
                  try
                  {
                    connector.noteJobComplete();
                    notifiedConnections.put(connectionName,connectionName);
                  }
                  catch (ServiceInterruption e)
                  {
                    Logging.threads.warn("Service interruption notifying connection - retrying: "+e.getMessage(),e);
                    continue;
                  }
                  catch (ACFException e)
                  {
                    if (e.getErrorCode() == ACFException.INTERRUPTED)
                      throw e;
                    if (e.getErrorCode() == ACFException.DATABASE_CONNECTION_ERROR)
                      throw e;
                    if (e.getErrorCode() == ACFException.SETUP_ERROR)
                      throw e;
                    // Nothing special; report the error and keep going.
                    Logging.threads.error(e.getMessage(),e);
                    continue;
                  }
                }
                finally
                {
                  OutputConnectorFactory.release(connector);
                }
              }
            }
          }
          
          // Go through jobs again, and put the notified ones into the inactive state.
          k = 0;
          while (k < jobsNeedingNotification.length)
          {
            Long jobID = jobsNeedingNotification[k++];
            IJobDescription job = jobManager.load(jobID,true);
            if (job != null)
            {
              // Get the connection name
              String connectionName = job.getOutputConnectionName();
              if (notifiedConnections.get(connectionName) != null)
              {
                // When done, put the job into the Inactive state.  Otherwise, the notification will be retried until it succeeds.
                jobManager.inactivateJob(jobID);
              }
            }
          }

          ACF.sleep(10000L);
        }
        catch (ACFException e)
        {
          if (e.getErrorCode() == ACFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ACFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Job notification thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
            try
            {
              // Give the database a chance to catch up/wake up
              ACF.sleep(10000L);
            }
            catch (InterruptedException se)
            {
              break;
            }
            continue;
          }

          // Log it, but keep the thread alive
          Logging.threads.error("Exception tossed: "+e.getMessage(),e);

          if (e.getErrorCode() == ACFException.SETUP_ERROR)
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

}
