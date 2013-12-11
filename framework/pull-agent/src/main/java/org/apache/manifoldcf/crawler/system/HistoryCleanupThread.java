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
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;
import java.lang.reflect.*;

/** This class describes the thread that cleans up history records.
* It fires infrequently and removes history records older than a configuration-determined cutoff.
*/
public class HistoryCleanupThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  protected static final String historyCleanupIntervalProperty = "org.apache.manifoldcf.crawler.historycleanupinterval";
  
  // Local data
  /** Process ID */
  protected final String processID;

  /** Constructor.
  */
  public HistoryCleanupThread(String processID)
    throws ManifoldCFException
  {
    super();
    this.processID = processID;
    setName("History cleanup thread");
    setDaemon(true);
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);
      // Default zero value means we never clean up, which is the backwards-compatible behavior
      long historyCleanupInterval = LockManagerFactory.getLongProperty(threadContext, historyCleanupIntervalProperty, 0L);
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
            Logging.threads.debug("History cleanup thread - removing old history at "+new Long(currentTime).toString());
          if (historyCleanupInterval > 0L && historyCleanupInterval < currentTime)
            connectionManager.cleanUpHistoryData(currentTime - historyCleanupInterval);
          else
            Logging.threads.debug(" History cleanup thread did nothing because cleanup disabled");
          // Loop around again, after resting a while
          ManifoldCF.sleep(60L * 60L * 1000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("History thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("HistoryCleanupThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
