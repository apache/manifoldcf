/* $Id: ExpireThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents an expire thread.  These threads expire documents, by deleting them from the system.
*/
public class ExpireThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: ExpireThread.java 988245 2010-08-23 18:39:35Z kwright $";


  // Local data
  /** Thread id */
  protected final String id;
  /** This is a reference to the static main document queue */
  protected final DocumentCleanupQueue documentQueue;
  /** Worker thread pool reset manager */
  protected final WorkerResetManager resetManager;
  /** Process ID */
  protected final String processID;
  
  /** Constructor.
  *@param id is the expire thread id.
  */
  public ExpireThread(String id, DocumentCleanupQueue documentQueue, WorkerResetManager resetManager, String processID)
    throws ManifoldCFException
  {
    super();
    this.id = id;
    this.documentQueue = documentQueue;
    this.resetManager = resetManager;
    this.processID = processID;
    setName("Expiration thread '"+id+"'");
    setDaemon(true);

  }

  public void run()
  {
    // Register this thread in the worker reset manager
    resetManager.registerMe();

    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IIncrementalIngester ingester = IncrementalIngesterFactory.make(threadContext);
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
      IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
      
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          // Before we begin, conditionally reset
          resetManager.waitForReset(threadContext);

          // Once we pull something off the queue, we MUST make sure that
          // we update its status, even if there is an exception!!!

          // See if there is anything on the queue for me
          DocumentCleanupSet dds = documentQueue.getDocuments();
          if (dds == null)
            // It's a reset, so recycle
            continue;

          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          IJobDescription job = dds.getJobDescription();
          String connectionName = job.getConnectionName();
          String outputConnectionName = job.getOutputConnectionName();
          
          try
          {
            long currentTime = System.currentTimeMillis();

            // Documents will be naturally segregated by connection, since each set comes from a single job.

            // Produce a map of connection name->connection object.  We will use this to perform a request for multiple connector objects
            IRepositoryConnection connection = connMgr.load(connectionName);
            
            // This is where we store the hopcount cleanup data
            ArrayList arrayDocHashes = new ArrayList();
            ArrayList arrayDocsToDelete = new ArrayList();
            ArrayList arrayRelationshipTypes = new ArrayList();
            ArrayList hopcountMethods = new ArrayList();
            
            int j = 0;
            while (j < dds.getCount())
            {
              CleanupQueuedDocument dqd = dds.getDocument(j);
              DocumentDescription ddd = dqd.getDocumentDescription();
              if (job != null && connection != null)
              {
                // We'll need the legal link types; grab those before we proceed
                String[] legalLinkTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
                if (legalLinkTypes != null)
                {
                  arrayDocHashes.add(ddd.getDocumentIdentifierHash());
                  arrayDocsToDelete.add(dqd);
                  arrayRelationshipTypes.add(legalLinkTypes);
                  hopcountMethods.add(new Integer(job.getHopcountMode()));
                }
              }
              j++;
            }

            // Grab one connection for the connectionName.  If we fail, nothing is lost and retries are possible.
            IRepositoryConnector connector = repositoryConnectorPool.grab(connection);
            try
            {

              // Iterate over the outputs
              boolean[] deleteFromQueue = new boolean[arrayDocHashes.size()];
                    
              // Count the number of docs to actually delete.  This will be a subset of the documents in the list.
              int k = 0;
              int removeCount = 0;
              while (k < arrayDocHashes.size())
              {
                if (((CleanupQueuedDocument)arrayDocsToDelete.get(k)).shouldBeRemovedFromIndex())
                {
                  deleteFromQueue[k] = false;
                  removeCount++;
                }
                else
                  deleteFromQueue[k] = true;
                k++;
              }
                    
              // Allocate removal arrays
              String[] docClassesToRemove = new String[removeCount];
              String[] hashedDocsToRemove = new String[removeCount];

              // Now, iterate over the list
              k = 0;
              removeCount = 0;
              while (k < arrayDocHashes.size())
              {
                if (((CleanupQueuedDocument)arrayDocsToDelete.get(k)).shouldBeRemovedFromIndex())
                {
                  docClassesToRemove[removeCount] = connectionName;
                  hashedDocsToRemove[removeCount] = (String)arrayDocHashes.get(k);
                  removeCount++;
                }
                k++;
              }

              OutputRemoveActivity activities = new OutputRemoveActivity(connectionName,connMgr,outputConnectionName);

              // Finally, go ahead and delete the documents from the ingestion system.
              // If we fail, we need to put the documents back on the queue.
              try
              {
                ingester.documentDeleteMultiple(outputConnectionName,docClassesToRemove,hashedDocsToRemove,activities);
                // Success!  Label all these as needing deletion from queue.
                k = 0;
                while (k < arrayDocHashes.size())
                {
                  if (((CleanupQueuedDocument)arrayDocsToDelete.get(k)).shouldBeRemovedFromIndex())
                    deleteFromQueue[k] = true;
                  k++;
                }
              }
              catch (ServiceInterruption e)
              {
                // We don't know which failed, or maybe they all did.
                // Go through the list of documents we just tried, and reset them on the queue based on the
                // ServiceInterruption parameters.  Then we must proceed to delete ONLY the documents that
                // were not part of the index deletion attempt.
                k = 0;
                while (k < arrayDocHashes.size())
                {
                  CleanupQueuedDocument cqd = (CleanupQueuedDocument)arrayDocsToDelete.get(k);
                  if (cqd.shouldBeRemovedFromIndex())
                  {
                    DocumentDescription dd = cqd.getDocumentDescription();
                    if (dd.getFailTime() != -1L && dd.getFailTime() < e.getRetryTime() ||
                      dd.getFailRetryCount() == 0)
                    {
                      // Treat this as a hard failure.
                      if (e.isAbortOnFail())
                        throw new ManifoldCFException("Repeated service interruptions - failure expiring document"+((e.getCause()!=null)?": "+e.getCause().getMessage():""),e.getCause());
                    }
                    else
                    {
                      // To recover from an expiration failure, requeue the document to PENDING etc.
                      jobManager.resetDocument(dd,e.getRetryTime(),
                        IJobManager.ACTION_REMOVE,e.getFailTime(),e.getFailRetryCount());
                      cqd.setProcessed();
                    }
                  }
                  k++;
                }
              }

              // Successfully deleted some documents from ingestion system.  Now, remove them from job queue.  This
              // must currently happen one document at a time, because the jobs and connectors for each document
              // potentially differ.
              k = 0;
              while (k < arrayDocHashes.size())
              {
                if (deleteFromQueue[k])
                {
                  CleanupQueuedDocument dqd = (CleanupQueuedDocument)arrayDocsToDelete.get(k);
                  DocumentDescription ddd = dqd.getDocumentDescription();
                  Long jobID = ddd.getJobID();
                  int hopcountMethod = ((Integer)hopcountMethods.get(k)).intValue();
                  String[] legalLinkTypes = (String[])arrayRelationshipTypes.get(k);
                  DocumentDescription[] requeueCandidates = jobManager.markDocumentExpired(jobID,legalLinkTypes,ddd,hopcountMethod);
                  // Use the common method for doing the requeuing
                  ManifoldCF.requeueDocumentsDueToCarrydown(jobManager,requeueCandidates,
                    connector,connection,rt,currentTime);
                  // Finally, completed expiration of the document.
                  dqd.setProcessed();
                }
                k++;
              }
            }
            finally
            {
              // Free up the reserved connector instance
              repositoryConnectorPool.release(connection,connector);
            }
          }
          catch (ManifoldCFException e)
          {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              break;

            if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
              throw e;

            // It's ok to abort a job because we can't talk to the search engine.
            if (jobManager.errorAbort(dds.getJobDescription().getID(),e.getMessage()))
            {
              // We eat the exception if there was already one recorded.

              // An exception occurred in the processing of a set of documents.
              // Shut the corresponding job down, with an appropriate error
              Logging.threads.error("Exception tossed: "+e.getMessage(),e);
            }
          }
          finally
          {
            // Insure that the documents that were not deleted get restored to the proper state.
            int j = 0;
            while (j < dds.getCount())
            {
              CleanupQueuedDocument dqd = dds.getDocument(j);
              if (dqd.wasProcessed() == false)
              {
                DocumentDescription ddd = dqd.getDocumentDescription();
                // Requeue this document!
                jobManager.resetDocument(ddd,0L,IJobManager.ACTION_REMOVE,-1L,-1);
                dqd.setProcessed();
              }
              j++;
            }
          }
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            // Note the failure, which will cause a reset to occur
            resetManager.noteEvent();

            Logging.threads.error("Expiration thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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

          // An exception occurred in the cleanup from another error.
          // Log the error (but that's all we can do)
          Logging.threads.error("Exception tossed: "+e.getMessage(),e);

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
      Logging.threads.fatal("ExpirationThread "+id+" initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }


  /** The ingest logger class */
  protected static class OutputRemoveActivity implements IOutputRemoveActivity
  {

    // Connection name
    protected String connectionName;
    // Connection manager
    protected IRepositoryConnectionManager connMgr;
    // Output connection name
    protected String outputConnectionName;

    /** Constructor */
    public OutputRemoveActivity(String connectionName, IRepositoryConnectionManager connMgr, String outputConnectionName)
    {
      this.connectionName = connectionName;
      this.connMgr = connMgr;
      this.outputConnectionName = outputConnectionName;
    }

    /** Record time-stamped information about the activity of the output connector.
    *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
    *       activity has an associated time; the startTime field records when the activity began.  A null value
    *       indicates that the start time and the finishing time are the same.
    *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
    *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
    *       "fetch document" activity.  Cannot be null.
    *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
    *@param entityURI is a (possibly long) string which identifies the object involved in the history record.
    *       The interpretation of this field will differ from connector to connector.  May be null.
    *@param resultCode contains a terse description of the result of the activity.  The description is limited in
    *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
    *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
    *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
    */
    public void recordActivity(Long startTime, String activityType, Long dataSize,
      String entityURI, String resultCode, String resultDescription)
      throws ManifoldCFException
    {
      connMgr.recordHistory(connectionName,startTime,ManifoldCF.qualifyOutputActivityName(activityType,outputConnectionName),dataSize,entityURI,resultCode,
        resultDescription,null);
    }
  }

}
