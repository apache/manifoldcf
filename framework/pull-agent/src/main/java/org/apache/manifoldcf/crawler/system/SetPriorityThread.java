/* $Id: SetPriorityThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class describes a thread whose job it is to continuously reset document priorities, based on recent
* queuing activity.  The goal is to evenly distribute queued documents across jobs and within classes of documents
* inside jobs.
* The way it works is for the thread to do some number of pending documents at a time.  The documents are fetched in such
* a way as to get the ones that have been least recently assessed preferentially.  The assessment process involves
* getting hold of a connector for the job that owns the document, and calculating the priority based on the recent
* history as maintained in the queueTracker object.
*/
public class SetPriorityThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: SetPriorityThread.java 988245 2010-08-23 18:39:35Z kwright $";

  // Local data
  /** This is the number of documents per cycle */
  protected final int cycleCount;
  /** The blocking documents object */
  protected final BlockingDocuments blockingDocuments;
  /** Process ID */
  protected final String processID;

  /** Constructor.
  */
  public SetPriorityThread(int workerThreadCount, BlockingDocuments blockingDocuments, String processID)
    throws ManifoldCFException
  {
    super();
    this.blockingDocuments = blockingDocuments;
    this.processID = processID;
    cycleCount = workerThreadCount * 10;
    setName("Set priority thread");
    setDaemon(true);
    // This thread's priority is highest so that stuffer thread does not go wanting
    setPriority(MAX_PRIORITY);
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(threadContext);
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      
      Logging.threads.debug("Set priority thread coming up");

      // Job description map (local) - designed to improve performance.
      // Cleared and reloaded on every batch of documents.
      HashMap jobDescriptionMap = new HashMap();

      // Repository connection map (local) - designed to improve performance.
      // Cleared and reloaded on every batch of documents.
      HashMap connectionMap = new HashMap();

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          Logging.threads.debug("Set priority thread woke up");

          // We're going to go through all eligible documents.  We will pick them up in
          // chunks however.
          long currentTime = System.currentTimeMillis();

          // Note well:
          // The priority that is given to a document is based on what priorities have been handed out in the past.  It is based
          // on the documents that have been queued for action, what job they belong to, the throttling in effect for the job,
          // and what the connection says that the document's throttling bins are.
          // Periodically, however, QueueTracker deliberately destroys the history.  My thinking here is that we need to "start over"
          // periodically to guarantee that we respond appropriately to changes in the environment - specifically, starting and
          // stopping jobs, changing throttling parameters, etc.

          // Clear the job description map and connection map
          jobDescriptionMap.clear();
          connectionMap.clear();

          // Do up to cycleCount documents in a "block".  Beyond this number we reset everything and loop back around.
          // This allows everything to restart, and higher priority documents can be found again.
          int processedCount = 0;
          while (true)
          {
            if (Thread.currentThread().isInterrupted())
              throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

            if (processedCount >= cycleCount)
            {
              Logging.threads.debug("Done reprioritizing because exceeded cycle count");
              break;
            }

            // Cycle through the current list of stuffer-identified documents until we come to the end.  Reprioritize these
            // first.  NOTE: These documents will already have document priorities.
            DocumentDescription desc = blockingDocuments.getBlockingDocument();
            if (desc != null)
            {
              ManifoldCF.writeDocumentPriorities(threadContext,
                new DocumentDescription[]{desc},connectionMap,jobDescriptionMap);
              processedCount++;
              continue;
            }
	    
            // Grab a list of document identifiers to set priority on.
            // We may well wind up calculating priority for documents that wind up having their
            // state changed before we can write back, but this is okay because update is only
            // going to be permitted for rows that still have the right state.
            DocumentDescription[] descs = jobManager.getNextNotYetProcessedReprioritizationDocuments(processID,1000);
            if (descs.length > 0)
            {
              ManifoldCF.writeDocumentPriorities(threadContext,
                descs,connectionMap,jobDescriptionMap);
              processedCount += descs.length;
              continue;
            }

            Logging.threads.debug("Done reprioritizing because no more documents to reprioritize");
            ManifoldCF.sleep(5000L);
            break;

          }

        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Set priority thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("SetPriorityThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
