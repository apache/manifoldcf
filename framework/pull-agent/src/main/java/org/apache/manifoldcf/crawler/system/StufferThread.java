/* $Id: StufferThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents the stuffer thread.  This thread's job is to request documents from the database and add them to the
* document queue.  The thread then sleeps until the document queue is empty again.
*/
public class StufferThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: StufferThread.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Write lock which allows us to keep track of the last time ANY stuffer thread stuffed data */
  protected final static String stufferThreadLockName = "_STUFFERTHREAD_LOCK";
  /** Datum which contains the last time, in milliseconds since epoch, that any stuffer thread in the cluster
      successfully fired. */
  protected final static String stufferThreadLastTimeDatumName = "_STUFFERTHREAD_LASTTIME";
  
  // Local data
  
  /** This is a reference to the static main document queue */
  protected final DocumentQueue documentQueue;
  /** Worker thread pool reset manager */
  protected final WorkerResetManager resetManager;
  /** This is the lowest number of entries we want ot stuff at any one time */
  protected final int lowestStuffAmt;
  /** This is the number of entries we want to stuff at any one time. */
  protected int stuffAmt;
  /** This is the low water mark for attempting to restuff */
  protected final int lowWaterMark;
  /** This is the queue tracker object. */
  protected final QueueTracker queueTracker;
  /** Blocking documents object. */
  protected final BlockingDocuments blockingDocuments;
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  *@param documentQueue is the document queue we'll be stuffing.
  *@param n represents the number of threads that will be processing queued stuff, NOT the
  * number of documents to be done at once!
  */
  public StufferThread(DocumentQueue documentQueue, int n, WorkerResetManager resetManager, QueueTracker qt,
    BlockingDocuments blockingDocuments, float lowWaterFactor, float stuffSizeFactor, String processID)
    throws ManifoldCFException
  {
    super();
    this.documentQueue = documentQueue;
    this.lowWaterMark = (int)(lowWaterFactor * (float)n);
    this.lowestStuffAmt = (int)(stuffSizeFactor * (float)n);
    this.stuffAmt = lowestStuffAmt;
    this.resetManager = resetManager;
    this.queueTracker = qt;
    this.blockingDocuments = blockingDocuments;
    this.processID = processID;
    setName("Stuffer thread");
    setDaemon(true);
    // The priority of this thread is higher than most others.  We want stuffing to proceed even if the machine
    // is pretty busy already.
    setPriority(getPriority()+1);
  }

  public void run()
  {
    resetManager.registerMe();

    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(threadContext);
      IIncrementalIngester ingester = IncrementalIngesterFactory.make(threadContext);
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
      
      Logging.threads.debug("Stuffer thread: Low water mark is "+Integer.toString(lowWaterMark)+"; amount per stuffing is "+Integer.toString(stuffAmt));

      // Hashmap keyed by jobid and containing ArrayLists.
      // This way we can guarantee priority will do the right thing, because the
      // priority is per-job.  We CANNOT guarantee anything about scheduling order, however,
      // other than that it falls in the time window.
      HashMap documentSets = new HashMap();
      ArrayList versionMap = new ArrayList();

      // Job description map (local) - designed to improve performance.
      // Cleared and reloaded on every batch of documents.
      HashMap jobDescriptionMap = new HashMap();

      // Repository connection map (local) - designed to improve performance.
      // Cleared and reloaded on every batch of documents.
      HashMap connectionMap = new HashMap();

      // Parameters we need in order to adjust the number of documents we fetch.  We base the number on how long it took to queue documents vs.
      // how long it took to need to queue again.
      long lastQueueStart = -1L;
      long lastQueueEnd = -1L;
      boolean lastQueueFullResults = false;

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          // Check if we're okay
          resetManager.waitForReset(threadContext);

          // System.out.println("Waiting...");
          // Wait until queue is below low water mark.
          boolean isEmpty = documentQueue.checkIfEmpty(lowWaterMark);
          if (isEmpty == false)
          {
            ManifoldCF.sleep(1000L);
            continue;
          }
          long queueNeededTime = System.currentTimeMillis();

          Logging.threads.debug("Document stuffer thread woke up");

          // Adjust stuffAmt based on how well we did in the last queuing attempt keeping up with the worker threads.
          if (lastQueueFullResults)
          {
            if (lastQueueEnd - lastQueueStart >= queueNeededTime - lastQueueEnd)
              stuffAmt *= 2;
            else if (lastQueueEnd - lastQueueStart < 4 * (queueNeededTime - lastQueueEnd))
            {
              stuffAmt /= 2;
              if (stuffAmt < lowestStuffAmt)
                stuffAmt = lowestStuffAmt;
            }
          }

          // What we want to do is load enough documents to completely fill n queued document sets.
          // The number n passed in here thus cannot be used in a query to limit the number of returned
          // results.  Instead, it must be factored into the limit portion of the query.
          
          // Note well: the stuffer code stuffs based on intervals, so it is perfectly OK to 
          // compute the interval for this request AND update the global "last time" even
          // before actually firing off the query.  The worst that can happen is if the query
          // fails, the interval will be "lost", and thus fewer documents will be stuffed than could
          // be.
          long stuffingStartTime;
          long stuffingEndTime;
          lockManager.enterWriteLock(stufferThreadLockName);
          try
          {
            stuffingStartTime = readLastTime(lockManager);
            stuffingEndTime = System.currentTimeMillis();
            // Set the last time to be the current time
            writeLastTime(lockManager,stuffingEndTime);
          }
          finally
          {
            lockManager.leaveWriteLock(stufferThreadLockName);
          }

          lastQueueStart = System.currentTimeMillis();
          DepthStatistics depthStatistics = new DepthStatistics();
          DocumentDescription[] descs = jobManager.getNextDocuments(processID,stuffAmt,stuffingEndTime,stuffingEndTime-stuffingStartTime,
            blockingDocuments,queueTracker.getCurrentStatistics(),depthStatistics);
          lastQueueEnd = System.currentTimeMillis();
          lastQueueFullResults = (descs.length == stuffAmt);
          
          // Assess what we've done.
          rt.assessMinimumDepth(depthStatistics.getBins());

          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          if (Logging.threads.isDebugEnabled())
          {
            Logging.threads.debug("Stuffer thread: Found "+Integer.toString(descs.length)+" documents to queue");
          }

          // If there are no queuable documents at all, then we can sleep for a while.
          // The theory is that we need to allow stuff to accumulate.
          if (descs.length == 0)
          {
            ManifoldCF.sleep(2000L);       // 2 seconds
            continue;
          }

          // if (Logging.threads.isDebugEnabled())
          //      Logging.threads.debug("Found "+Integer.toString(descs.length)+" documents to stuff");

          // Clear the job description map and connection map
          jobDescriptionMap.clear();
          connectionMap.clear();

          // We need to get the last ingested version string for all of these documents, in bulk!

          IJobDescription[] jobs = new IJobDescription[descs.length];
          IRepositoryConnection[] connections = new IRepositoryConnection[descs.length];
          DocumentIngestStatus[] versions = new DocumentIngestStatus[descs.length];
          String[] outputConnectionNames = new String[descs.length];
          String[] documentClasses = new String[descs.length];
          String[] documentIDHashes = new String[descs.length];

          // Go through the documents and set up jobs, prefixed id's
          int i = 0;
          while (i < descs.length)
          {
            DocumentDescription dd = descs[i];
            IJobDescription job = (IJobDescription)jobDescriptionMap.get(dd.getJobID());
            if (job == null)
            {
              job = jobManager.load(dd.getJobID(),true);
              jobDescriptionMap.put(dd.getJobID(),job);
            }
            jobs[i] = job;
            String connectionName = job.getConnectionName();
            documentClasses[i] = connectionName;
            outputConnectionNames[i] = job.getOutputConnectionName();
            IRepositoryConnection connection = (IRepositoryConnection)connectionMap.get(connectionName);
            if (connection == null)
            {
              connection = mgr.load(connectionName);
              connectionMap.put(connectionName,connection);
            }
            connections[i] = connection;
            documentIDHashes[i] = dd.getDocumentIdentifierHash();

            i++;
          }

          versions = ingester.getDocumentIngestDataMultiple(outputConnectionNames,documentClasses,documentIDHashes);

          // Now, do the incremental ingestion version request.

          // We need to go through the list, and segregate them by job, so the individual
          // connectors can work in batch.
          documentSets.clear();
          i = 0;
          while (i < descs.length)
          {
            Long jobID = jobs[i].getID();

            // We have to see how we are doing with respect to the limit for this connector.
            // We also need to log the queuing activity to the queue tracker, so that
            // the priority setter thread can do its thing properly.

            // Get a repository connection appropriate for this document.
            IRepositoryConnection connection = connections[i];
            int maxDocuments;
            String[] binNames;
            try
            {
              // Grab a connector handle
              IRepositoryConnector connector = repositoryConnectorPool.grab(connection);
              if (connector == null)
              {
                maxDocuments = 1;
                binNames = new String[]{""};
              }
              else
              {
                try
                {
                  // Convert the document identifier to a URI
                  maxDocuments = connector.getMaxDocumentRequest();
                  // Get the bins for the document identifier
                  binNames = connector.getBinNames(descs[i].getDocumentIdentifier());
                }
                finally
                {
                  repositoryConnectorPool.release(connection,connector);
                }
              }
            }
            catch (ManifoldCFException e)
            {
              // If we were interrupted, then we are allowed to leave, because the process is terminating, but that's the only exception to the rule
              if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                throw e;

              // Note: We really should never leave this block by throwing an exception, since that could easily leave dangling
              // active jobqueue entries around.  Instead, log the error and continue IN ALL CASES.
              Logging.threads.error("Stuffer thread ManifoldCF Exception thrown: "+e.getMessage()+" - continuing",
                e);
              maxDocuments = 1;
              binNames = new String[]{""};
            }
            catch (OutOfMemoryError e)
            {
              System.err.println("agents process ran out of memory - shutting down");
              e.printStackTrace(System.err);
              System.exit(-200);
              // These are needed because the compiler doesn't know System.exit ends the thread.
              maxDocuments = 1;
              binNames = new String[]{""};
            }
            catch (Throwable e)
            {
              // Note: We really should never leave this block by throwing an exception, since that could easily leave dangling
              // active jobqueue entries around.  Instead, log the error and continue IN ALL CASES.
              Logging.threads.fatal("Stuffer thread Throwable thrown: "+e.getMessage()+" - continuing",
                e);
              maxDocuments = 1;
              binNames = new String[]{""};
            }

            QueuedDocument qd = new QueuedDocument(descs[i],versions[i],binNames);

            // Grab the arraylist that's there, or create it.
            ArrayList set = (ArrayList)documentSets.get(jobID);
            if (set == null)
            {
              set = new ArrayList();
              documentSets.put(jobID,set);
            }
            set.add(qd);

            // Note the queuing activity
            queueTracker.addRecord(binNames);

            if (set.size() >= maxDocuments)
            {
              // Create and queue this as a document set
              // if (Logging.threads.isDebugEnabled())
              //      Logging.threads.debug("Queuing "+Integer.toString(set.size())+" documents in one request");
              QueuedDocumentSet qds = new QueuedDocumentSet(set,jobs[i],connections[i]);
              documentQueue.addDocument(qds);
              set.clear();
            }
            i++;
          }

          // Stuff everything left into the queue.
          i = 0;
          while (i < descs.length)
          {
            Long jobID = jobs[i].getID();
            ArrayList x = (ArrayList)documentSets.get(jobID);
            if (x != null && x.size() > 0)
            {
              QueuedDocumentSet set = new QueuedDocumentSet(x,jobs[i],connections[i]);
              documentQueue.addDocument(set);
              documentSets.remove(jobID);
            }
            i++;
          }

          // If we don't wait here, the other threads don't seem to have a chance to queue anything else up.
          //Thread.yield();
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            resetManager.noteEvent();

            Logging.threads.error("Stuffer thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("StufferThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

  protected static long readLastTime(ILockManager lockManager)
    throws ManifoldCFException
  {
    byte[] data = lockManager.readData(stufferThreadLastTimeDatumName);
    if (data == null || data.length != 8)
      return System.currentTimeMillis();
    long value = (((long)data[0]) & 0xffL) +
      ((((long)data[1]) << 8) & 0xff00L) +
      ((((long)data[2]) << 16) & 0xff0000L) +
      ((((long)data[3]) << 24) & 0xff000000L) +
      ((((long)data[4]) << 32) & 0xff00000000L) +
      ((((long)data[5]) << 40) & 0xff0000000000L) +
      ((((long)data[6]) << 48) & 0xff000000000000L) +
      ((((long)data[7]) << 56) & 0xff00000000000000L);
    return value;
  }

  protected static void writeLastTime(ILockManager lockManager, long lastTime)
    throws ManifoldCFException
  {
    byte[] data = new byte[8];
    data[0] = (byte)(lastTime & 0xffL);
    data[1] = (byte)((lastTime >> 8) & 0xffL);
    data[2] = (byte)((lastTime >> 16) & 0xffL);
    data[3] = (byte)((lastTime >> 24) & 0xffL);
    data[4] = (byte)((lastTime >> 32) & 0xffL);
    data[5] = (byte)((lastTime >> 40) & 0xffL);
    data[6] = (byte)((lastTime >> 48) & 0xffL);
    data[7] = (byte)((lastTime >> 56) & 0xffL);
    lockManager.writeData(stufferThreadLastTimeDatumName,data);
  }
  
}
