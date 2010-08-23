/* $Id: ExpireThread.java 921329 2010-03-10 12:44:20Z kwright $ */

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

/** This class represents an expire thread.  These threads expire documents, by deleting them from the system.
*/
public class ExpireThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: ExpireThread.java 921329 2010-03-10 12:44:20Z kwright $";


  // Local data
  protected String id;
  // This is a reference to the static main document queue
  protected DocumentDeleteQueue documentQueue;
  /** Worker thread pool reset manager */
  protected WorkerResetManager resetManager;
  /** Queue tracker */
  protected QueueTracker queueTracker;

  /** Constructor.
  *@param id is the expire thread id.
  */
  public ExpireThread(String id, DocumentDeleteQueue documentQueue, QueueTracker queueTracker, WorkerResetManager resetManager)
    throws LCFException
  {
    super();
    this.id = id;
    this.documentQueue = documentQueue;
    this.resetManager = resetManager;
    this.queueTracker = queueTracker;
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

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          if (Thread.currentThread().isInterrupted())
            throw new LCFException("Interrupted",LCFException.INTERRUPTED);

          // Before we begin, conditionally reset
          resetManager.waitForReset(threadContext);

          // Once we pull something off the queue, we MUST make sure that
          // we update its status, even if there is an exception!!!

          // See if there is anything on the queue for me
          DocumentDeleteSet dds = documentQueue.getDocuments();
          if (dds == null)
            // It's a reset, so recycle
            continue;

          if (Thread.currentThread().isInterrupted())
            throw new LCFException("Interrupted",LCFException.INTERRUPTED);

          try
          {
            long currentTime = System.currentTimeMillis();

            // We need to segregate all the documents by connection, in order to be able to form a decent activities object
            // to pass into the incremental ingester.  So, first pass through the document descriptions will build that.
            Map mappedDocs = new HashMap();
            int j = 0;
            while (j < dds.getCount())
            {
              DeleteQueuedDocument dqd = dds.getDocument(j++);
              DocumentDescription ddd = dqd.getDocumentDescription();
              Long jobID = ddd.getJobID();
              IJobDescription job = jobManager.load(jobID,true);
              String connectionName = job.getConnectionName();
              ArrayList list = (ArrayList)mappedDocs.get(connectionName);
              if (list == null)
              {
                list = new ArrayList();
                mappedDocs.put(connectionName,list);
              }
              list.add(dqd);
            }

            // Now, cycle through all represented connections.
            // For each connection, construct the necessary pieces to do the deletion.
            Iterator iter = mappedDocs.keySet().iterator();
            while (iter.hasNext())
            {
              String connectionName = (String)iter.next();
              ArrayList list = (ArrayList)mappedDocs.get(connectionName);

              // Produce a map of connection name->connection object.  We will use this to perform a request for multiple connector objects
              IRepositoryConnection connection = connMgr.load(connectionName);
              ArrayList arrayOutputConnectionNames = new ArrayList();
              ArrayList arrayDocHashes = new ArrayList();
              ArrayList arrayDocClasses = new ArrayList();
              ArrayList arrayDocsToDelete = new ArrayList();
              ArrayList arrayRelationshipTypes = new ArrayList();
              ArrayList hopcountMethods = new ArrayList();
              ArrayList connections = new ArrayList();
              j = 0;
              while (j < list.size())
              {
                DeleteQueuedDocument dqd = (DeleteQueuedDocument)list.get(j);
                DocumentDescription ddd = dqd.getDocumentDescription();
                Long jobID = ddd.getJobID();
                IJobDescription job = jobManager.load(jobID,true);
                if (job != null && connection != null)
                {
                  // We'll need the legal link types; grab those before we proceed
                  String[] legalLinkTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
                  if (legalLinkTypes != null)
                  {
                    arrayOutputConnectionNames.add(job.getOutputConnectionName());
                    arrayDocClasses.add(connectionName);
                    arrayDocHashes.add(ddd.getDocumentIdentifierHash());
                    arrayDocsToDelete.add(dqd);
                    arrayRelationshipTypes.add(legalLinkTypes);
                    hopcountMethods.add(new Integer(job.getHopcountMode()));
                  }
                }
                j++;
              }

              // Next, segregate the documents by output connection name.  This will permit logging to know what actual activity type to use.
              HashMap outputMap = new HashMap();
              j = 0;
              while (j < arrayDocHashes.size())
              {
                String outputConnectionName = (String)arrayOutputConnectionNames.get(j);
                ArrayList subList = (ArrayList)outputMap.get(outputConnectionName);
                if (subList == null)
                {
                  subList = new ArrayList();
                  outputMap.put(outputConnectionName,subList);
                }
                subList.add(new Integer(j));
                j++;
              }

              // Grab one connection for each connectionName.  If we fail, nothing is lost and retries are possible.
              try
              {
                IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
                try
                {

                  // Iterate over the outputs
                  Iterator outputIterator = outputMap.keySet().iterator();
                  while (outputIterator.hasNext())
                  {
                    String outputConnectionName = (String)outputIterator.next();
                    ArrayList indexList = (ArrayList)outputMap.get(outputConnectionName);
                    String[] docClassesToRemove = new String[indexList.size()];
                    String[] hashedDocsToRemove = new String[indexList.size()];

                    // Now, iterate over the index list
                    int k = 0;
                    while (k < indexList.size())
                    {
                      int index = ((Integer)indexList.get(k)).intValue();
                      docClassesToRemove[k] = (String)arrayDocClasses.get(index);
                      hashedDocsToRemove[k] = (String)arrayDocHashes.get(index);
                      k++;
                    }

                    OutputRemoveActivity activities = new OutputRemoveActivity(connectionName,connMgr,outputConnectionName);

                    // Finally, go ahead and delete the documents from the ingestion system.

                    while (true)
                    {
                      try
                      {
                        ingester.documentDeleteMultiple(outputConnectionName,docClassesToRemove,hashedDocsToRemove,activities);
                        break;
                      }
                      catch (ServiceInterruption e)
                      {
                        // If we get a service interruption here, it means that the ingestion API is down.
                        // There is no point, therefore, in freeing up this thread to go do something else;
                        // might as well just wait here for our retries.
                        // Wait for the prescribed time
                        long amt = e.getRetryTime();
                        long now = System.currentTimeMillis();
                        long waittime = amt-now;
                        if (waittime <= 0L)
                          waittime = 300000L;
                        LCF.sleep(waittime);
                      }
                    }

                    // Successfully deleted some documents from ingestion system.  Now, remove them from job queue.  This
                    // must currently happen one document at a time, because the jobs and connectors for each document
                    // potentially differ.
                    k = 0;
                    while (k < indexList.size())
                    {
                      int index = ((Integer)indexList.get(k)).intValue();

                      DeleteQueuedDocument dqd = (DeleteQueuedDocument)arrayDocsToDelete.get(index);
                      DocumentDescription ddd = dqd.getDocumentDescription();
                      Long jobID = ddd.getJobID();
                      int hopcountMethod = ((Integer)hopcountMethods.get(index)).intValue();
                      String[] legalLinkTypes = (String[])arrayRelationshipTypes.get(index);
                      DocumentDescription[] requeueCandidates = jobManager.markDocumentDeleted(jobID,legalLinkTypes,ddd,hopcountMethod);
                      // Use the common method for doing the requeuing
                      LCF.requeueDocumentsDueToCarrydown(jobManager,requeueCandidates,
                        connector,connection,queueTracker,currentTime);
                      // Finally, completed expiration of the document.
                      dqd.setProcessed();
                      k++;
                    }
                  }
                }
                finally
                {
                  // Free up the reserved connector instance
                  RepositoryConnectorFactory.release(connector);
                }
              }
              catch (LCFException e)
              {
                if (e.getErrorCode() == LCFException.REPOSITORY_CONNECTION_ERROR)
                {
                  // This error can only come from grabbing the connections.  So, if this occurs it means that
                  // all the documents we've been handed have to be stuffed back onto the queue for processing at a later time.
                  Logging.threads.warn("Expire thread couldn't establish necessary connections, retrying later: "+e.getMessage(),e);

                  // Let the unprocessed documents get requeued!  This is handled at the end of the loop...
                }
                else
                  throw e;
              }
            }
          }
          catch (LCFException e)
          {
            if (e.getErrorCode() == LCFException.INTERRUPTED)
              break;

            if (e.getErrorCode() == LCFException.DATABASE_CONNECTION_ERROR)
              throw e;

            Logging.threads.error("Exception tossed: "+e.getMessage(),e);
          }
          finally
          {
            // Insure that the documents that were not deleted get restored to the proper state.
            int j = 0;
            while (j < dds.getCount())
            {
              DeleteQueuedDocument dqd = dds.getDocument(j);
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
        catch (LCFException e)
        {
          if (e.getErrorCode() == LCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == LCFException.DATABASE_CONNECTION_ERROR)
          {
            // Note the failure, which will cause a reset to occur
            resetManager.noteEvent();
            // Wake up all sleeping worker threads
            documentQueue.reset();

            Logging.threads.error("Expiration thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
            try
            {
              // Give the database a chance to catch up/wake up
              LCF.sleep(10000L);
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
      throws LCFException
    {
      connMgr.recordHistory(connectionName,startTime,LCF.qualifyOutputActivityName(activityType,outputConnectionName),dataSize,entityURI,resultCode,
        resultDescription,null);
    }
  }

}
