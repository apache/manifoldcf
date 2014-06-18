/* $Id: DocumentDeleteStufferThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class looks for documents that need to be deleted (as part of a job deletion), and
* queues them up for the various document delete threads to take care of.
* To do this, this thread performs a query which returns a chunk of results, then queues those
* results.  The individual document delete threads will be waiting on the queue.
* Once the queue is full enough, the thread then sleeps until the delete queue is empty again.
*/
public class DocumentDeleteStufferThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: DocumentDeleteStufferThread.java 988245 2010-08-23 18:39:35Z kwright $";

  // Local data
  /** This is a reference to the static main document queue */
  protected final DocumentDeleteQueue documentDeleteQueue;
  /** This is the reset manager */
  protected final DocDeleteResetManager resetManager;
  /** This is the number of entries we want to stuff at any one time. */
  protected final int n;
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  *@param documentDeleteQueue is the document queue we'll be stuffing.
  *@param n is the maximum number of threads that will be doing delete processing.
  */
  public DocumentDeleteStufferThread(DocumentDeleteQueue documentDeleteQueue, int n, DocDeleteResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    this.documentDeleteQueue = documentDeleteQueue;
    this.n = n;
    this.resetManager = resetManager;
    this.processID = processID;
    setName("Document delete stuffer thread");
    setDaemon(true);
  }

  public void run()
  {
    resetManager.registerMe();

    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(threadContext);
      IJobManager jobManager = JobManagerFactory.make(threadContext);

      ArrayList docList = new ArrayList();

      IDBInterface database = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      int deleteChunkSize = database.getMaxInClause();

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          resetManager.waitForReset(threadContext);

          // Wait until the delete queue is "empty" (meaning that some delete threads
          // can run out of work if we don't act).
          if (documentDeleteQueue.checkIfEmpty(n * 3) == false)
          {
            ManifoldCF.sleep(100L);
            continue;
          }

          Logging.threads.debug("Document delete stuffer thread woke up");

          long currentTime = System.currentTimeMillis();
          
          // Get a single chunk at a time (but keep going until everything is stuffed)
          // This method will set the status of the documents in question
          // to "beingdeleted".

          DocumentDescription[] descs = jobManager.getNextDeletableDocuments(processID,deleteChunkSize,currentTime);

          // If there are no chunks at all, then we can sleep for a while.
          // The theory is that we need to allow stuff to accumulate.
          if (descs.length == 0)
          {
            Logging.threads.debug("Document delete stuffer thread found nothing to do");
            ManifoldCF.sleep(1000L);       // 1 second
            continue;
          }

          if (Logging.threads.isDebugEnabled())
            Logging.threads.debug("Document delete stuffer thread found "+Integer.toString(descs.length)+" documents");

          // Do the stuffing.  Each set must be segregated by job, since we need the job ID in the doc set.
          Map jobMap = new HashMap();
          int k = 0;
          while (k < descs.length)
          {
            DeleteQueuedDocument x = new DeleteQueuedDocument(descs[k]);
            Long jobID = descs[k].getJobID();
            List y = (List)jobMap.get(jobID);
            if (y == null)
            {
              y = new ArrayList();
              jobMap.put(jobID,y);
            }
            y.add(x);
            k++;
          }
          
          Iterator iter = jobMap.keySet().iterator();
          while (iter.hasNext())
          {
            Long jobID = (Long)iter.next();
            IJobDescription jobDescription = jobManager.load(jobID,true);
            List y = (List)jobMap.get(jobID);
            DeleteQueuedDocument[] docDescs = new DeleteQueuedDocument[y.size()];
            k = 0;
            while (k < docDescs.length)
            {
              docDescs[k] = (DeleteQueuedDocument)y.get(k);
              k++;
            }
            DocumentDeleteSet set = new DocumentDeleteSet(docDescs,jobDescription);
            documentDeleteQueue.addDocuments(set);
          }

          // If we don't wait here, the other threads don't have a chance to queue anything else up.
          yield();
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            resetManager.noteEvent();

            Logging.threads.error("Delete stuffer thread aborting and restarting due to database connection reset",e);
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
      Logging.threads.fatal("DocumentDeleteStufferThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }

  }

}
