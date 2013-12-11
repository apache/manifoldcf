/* $Id: WorkerThread.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.io.*;
import java.lang.reflect.*;

/** This class represents a worker thread.  Hundreds of these threads are instantiated in order to
* perform crawling and extraction.
*/
public class WorkerThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: WorkerThread.java 988245 2010-08-23 18:39:35Z kwright $";


  // Local data
  /** Thread id */
  protected final String id;
  /** This is a reference to the static main document queue */
  protected final DocumentQueue documentQueue;
  /** Worker thread pool reset manager */
  protected final WorkerResetManager resetManager;
  /** Queue tracker */
  protected final QueueTracker queueTracker;
  /** Process ID */
  protected final String processID;

  /** Constructor.
  *@param id is the worker thread id.
  */
  public WorkerThread(String id, DocumentQueue documentQueue, WorkerResetManager resetManager, QueueTracker queueTracker, String processID)
    throws ManifoldCFException
  {
    super();
    this.id = id;
    this.documentQueue = documentQueue;
    this.resetManager = resetManager;
    this.queueTracker = queueTracker;
    this.processID = processID;
    setName("Worker thread '"+id+"'");
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
      IBinManager binManager = BinManagerFactory.make(threadContext);
      IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
      IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(threadContext);
      IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
      
      List<DocumentToProcess> fetchList = new ArrayList<DocumentToProcess>();
      Map<String,String> versionMap = new HashMap<String,String>();
      List<QueuedDocument> finishList = new ArrayList<QueuedDocument>();
      Map<String,Integer> idHashIndexMap = new HashMap<String,Integer>();

      // This is where we accumulate the document QueuedDocuments to be deleted from the job queue.
      List<QueuedDocument> deleteList = new ArrayList<QueuedDocument>();

      // This is where we accumulate documents that need to be placed in the HOPCOUNTREMOVED
      // state
      List<QueuedDocument> hopcountremoveList = new ArrayList<QueuedDocument>();
      
      // This is where we accumulate documents that need to be rescanned
      List<QueuedDocument> rescanList = new ArrayList<QueuedDocument>();
      
      // This is where we store document ID strings of documents that need to be noted as having
      // been checked.
      List<String> ingesterCheckList = new ArrayList<String>();

      // Service interruption thrown with "abort on fail".
      ManifoldCFException abortOnFail = null;
      
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
          QueuedDocumentSet qds = documentQueue.getDocument(queueTracker);
          if (qds == null)
            // It's a reset, so recycle
            continue;

          try
          {
            // System.out.println("Got a document set");

            if (Thread.currentThread().isInterrupted())
              throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

            // First of all: find out if the job for these documents has been aborted, paused, etc.
            // If so, we requeue the documents immediately.
            IJobDescription job = qds.getJobDescription();
            Long jobID = job.getID();
            if (jobManager.checkJobActive(jobID) == false)
              // Recycle; let these documents be requeued and go get the next set.
              continue;

            if (Logging.threads.isDebugEnabled())
              Logging.threads.debug("Worker thread received "+Integer.toString(qds.getCount())+" documents");

            // Universal data, from the job
            String connectionName = job.getConnectionName();
            String outputName = job.getOutputConnectionName();
            String newParameterVersion = packParameters(job.getForcedMetadata());
            DocumentSpecification spec = job.getSpecification();
            OutputSpecification outputSpec = job.getOutputSpecification();
            int jobType = job.getType();

            IRepositoryConnection connection = qds.getConnection();
            
            OutputActivity ingestLogger = new OutputActivity(connectionName,connMgr,outputName);

            // The flow through this section of the code is as follows.
            // (1) We start with a list of documents
            // (2) We attempt to do various things to these documents
            // (3) Based on what happens, and what errors we get, we progressively move documents out of the main list
            //     and into secondary lists that will be all treated in the same way
            
            // First, initialize the active document set to contain everything.
            List<QueuedDocument> activeDocuments = new ArrayList<QueuedDocument>(qds.getCount());
            
            for (int i = 0; i < qds.getCount(); i++)
            {
              QueuedDocument qd = qds.getDocument(i);
              activeDocuments.add(qd);
            }

            // Clear out all of our disposition lists
            fetchList.clear();
            finishList.clear();
            versionMap.clear();
            deleteList.clear();
            ingesterCheckList.clear();
            hopcountremoveList.clear();
            rescanList.clear(); //                  jobManager.resetDocument(dd,0L,IJobManager.ACTION_RESCAN,-1L,-1);
            abortOnFail = null;

            // Keep track of the starting processing time, for statistics calculation
            long processingStartTime = System.currentTimeMillis();
            // Log these documents in the overlap calculator
            qds.beginProcessing(queueTracker);
            try
            {
              long currentTime = System.currentTimeMillis();

              if (Logging.threads.isDebugEnabled())
                Logging.threads.debug("Worker thread starting document count is "+Integer.toString(activeDocuments.size()));

              // Get the legal link types.  This is needed for later hopcount checking.
              String[] legalLinkTypes = null;
              if (activeDocuments.size() > 0)
              {
                legalLinkTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
                // If this came back null, it means that there is no underlying implementation available, so treat this like a kind of service interruption.
                if (legalLinkTypes == null)
                {
                  // Failure here puts all remaining documents into rescan list
                  moveList(activeDocuments,rescanList);
                }
              }

              if (Logging.threads.isDebugEnabled())
                Logging.threads.debug(" Post-linktype document count is "+Integer.toString(activeDocuments.size()));
              
              // Do the hopcount checks, if any.  This will iteratively reduce the viable list of
              // document identifiers in need of having their versions fetched.
              if (legalLinkTypes != null && activeDocuments.size() > 0)
              {
                // Set up the current ID array
                String[] currentDocIDHashArray = new String[activeDocuments.size()];
                for (int i = 0; i < currentDocIDHashArray.length; i++)
                {
                  currentDocIDHashArray[i] = activeDocuments.get(i).getDocumentDescription().getDocumentIdentifierHash();
                }
                Map filterMap = job.getHopCountFilters();
                Iterator filterIter = filterMap.keySet().iterator();
                // Array to accumulate hopcount results for all link types
                boolean[] overallResults = new boolean[currentDocIDHashArray.length];
                for (int i = 0; i < overallResults.length; i++)
                {
                  overallResults[i] = true;
                }
                // Calculate the hopcount result for each link type, and fold it in.
                while (filterIter.hasNext())
                {
                  String linkType = (String)filterIter.next();
                  int maxHop = (int)((Long)filterMap.get(linkType)).longValue();
                  boolean[] results = jobManager.findHopCounts(job.getID(),legalLinkTypes,currentDocIDHashArray,linkType,
                    maxHop,job.getHopcountMode());
                  for (int i = 0; i < results.length; i++)
                  {
                    overallResults[i] = overallResults[i] && results[i];
                  }
                }
                // Move all documents to the appropriate list
                List<QueuedDocument> newActiveSet = new ArrayList<QueuedDocument>(activeDocuments.size());
                for (int i = 0; i < overallResults.length; i++)
                {
                  if (overallResults[i] == false)
                  {
                    hopcountremoveList.add(activeDocuments.get(i));
                  }
                  else
                  {
                    newActiveSet.add(activeDocuments.get(i));
                  }
                }
                activeDocuments = newActiveSet;
              }

              if (Logging.threads.isDebugEnabled())
                Logging.threads.debug(" Post-hopcount pruned document count is "+Integer.toString(activeDocuments.size()));
              
              // From here on down we need a connector instance, so get one.
              IRepositoryConnector connector = null;
              if (activeDocuments.size() > 0 || hopcountremoveList.size() > 0)
              {
                connector = repositoryConnectorPool.grab(connection);

                // If we wind up with a null here, it means that a document got queued for a connector which is now gone.
                // Basically, what we want to do in that case is to treat this kind of like a service interruption - the document
                // must be requeued for immediate reprocessing.  When the rest of the world figures out that the job that owns this
                // document is in fact unable to function, we'll stop getting such documents handed to us, because the state of the
                // job will be changed.

                if (connector == null)
                {
                  // Failure here puts all remaining documents into rescan list
                  moveList(activeDocuments,rescanList);
                  moveList(hopcountremoveList,rescanList);
                }
              }
              
              if (connector != null)
              {
                // Open try/finally block to free the connector instance no matter what
                try
                {
                  // Check for interruption before we start fetching
                  if (Thread.currentThread().isInterrupted())
                    throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

                  if (activeDocuments.size() > 0)
                  {
                    // === Fetch document versions ===
                    String[] currentDocIDHashArray = new String[activeDocuments.size()];
                    String[] currentDocIDArray = new String[activeDocuments.size()];
                    String[] oldVersionStringArray = new String[activeDocuments.size()];

                    for (int i = 0; i < activeDocuments.size(); i++)
                    {
                      QueuedDocument qd = activeDocuments.get(i);
                      currentDocIDHashArray[i] = qd.getDocumentDescription().getDocumentIdentifierHash();
                      currentDocIDArray[i] = qd.getDocumentDescription().getDocumentIdentifier();
                      DocumentIngestStatus dis = qd.getLastIngestedStatus();
                      if (dis == null)
                        oldVersionStringArray[i] = null;
                      else
                      {
                        oldVersionStringArray[i] = dis.getDocumentVersion();
                        if (oldVersionStringArray[i] == null)
                          oldVersionStringArray[i] = "";
                      }
                    }

                    // Get the output version string.
                    String outputVersion = ingester.getOutputDescription(outputName,outputSpec);
                      
                    HashMap abortSet = new HashMap();
                    VersionActivity versionActivity = new VersionActivity(processID,connectionName,connMgr,jobManager,job,ingester,abortSet,outputVersion);

                    String aclAuthority = connection.getACLAuthority();
                    boolean isDefaultAuthority = (aclAuthority == null || aclAuthority.length() == 0);

                    if (Logging.threads.isDebugEnabled())
                      Logging.threads.debug("Worker thread getting versions for "+Integer.toString(currentDocIDArray.length)+" documents");

                    // === Fetch documents ===
                    // We start by getting the document version string.
                    String[] newVersionStringArray = null;
                    try
                    {
                      newVersionStringArray = connector.getDocumentVersions(currentDocIDArray,oldVersionStringArray,
                        versionActivity,spec,jobType,isDefaultAuthority);

                      if (Logging.threads.isDebugEnabled())
                        Logging.threads.debug("Worker thread done getting versions for "+Integer.toString(currentDocIDArray.length)+" documents");

                    }
                    catch (ServiceInterruption e)
                    {
                      // This service interruption comes from a point where we
                      // know that no documents were ingested.
                      // Therefore, active -> pending and activepurgatory -> pendingpurgatory

                      if (!e.jobInactiveAbort())
                      {
                        Logging.jobs.warn("Pre-ingest service interruption reported for job "+
                          job.getID()+" connection '"+job.getConnectionName()+"': "+
                          e.getMessage());
                      }

                      if (!e.jobInactiveAbort() && e.isAbortOnFail())
                        abortOnFail = new ManifoldCFException("Repeated service interruptions - failure getting document version"+((e.getCause()!=null)?": "+e.getCause().getMessage():""),e.getCause());
                        
                      // Mark the current documents to be recrawled at the
                      // time specified, with the proper error handling.
                      List<QueuedDocument> newActiveList = new ArrayList<QueuedDocument>(activeDocuments.size());
                      for (int i = 0; i < activeDocuments.size(); i++)
                      {
                        QueuedDocument qd = activeDocuments.get(i);
                        DocumentDescription dd = qd.getDocumentDescription();
                        // If either we are going to be requeuing beyond the fail time, OR
                        // the number of retries available has hit 0, THEN we treat this
                        // as either an "ignore" or a hard error.
                        if (!e.jobInactiveAbort() && (dd.getFailTime() != -1L && dd.getFailTime() < e.getRetryTime() ||
                          dd.getFailRetryCount() == 0))
                        {
                          // Treat this as a hard failure.
                          if (e.isAbortOnFail())
                          {
                            rescanList.add(qd);
                          }
                          // We want this particular document to be not included in the
                          // reprocessing.  Therefore, we do the same thing as we would
                          // if we got back a null version.
                          deleteList.add(qd);
                        }
                        else
                        {
                          // Retry this document according to the parameters provided.
                          jobManager.resetDocument(dd,e.getRetryTime(),
                            IJobManager.ACTION_RESCAN,e.getFailTime(),e.getFailRetryCount());
                          qd.setProcessed();
                        }
                      }
                      
                      // All active documents have been removed from the list
                      activeDocuments.clear();
                      
                    }

                    // If version fetch was successful, the go on to processing phase
                    if (newVersionStringArray != null)
                    {
                      // This try{ } is for releasing document versions at the connector level.
                      try
                      {
                        // Organize what we need for document status comparison, and get it into a canonical form.
                        String newOutputVersion = outputVersion;
                        if (newOutputVersion == null)
                          newOutputVersion = "";

                        // Loop through documents now, and amass what we need to fetch.
                        // We also need to tally: (1) what needs to be marked as deleted via
                        //   jobManager.markDocumentDeleted();
                        // (2) what needs to be noted as a deletion to ingester
                        // (3) what needs to be noted as a check for the ingester
                        for (int i = 0; i < activeDocuments.size(); i++)
                        {
                          QueuedDocument qd = activeDocuments.get(i);
                          DocumentDescription dd = qd.getDocumentDescription();
                          // If this document was aborted, then treat it specially; we never go on to fetch it, for one thing.
                          if (abortSet.get(dd.getDocumentIdentifier()) != null)
                          {
                            // Special treatment for aborted documents.
                            // We ignore the returned version string completely, since it's presumed that processing was not completed for this doc.
                            // We want to give up immediately on this one, and just requeue it for immediate reprocessing (pending its prereqs being all met).
                            // Add to the finish list, so it gets requeued.  Because the document is already marked as aborted, this should be enough to cause an
                            // unconditional requeue.
                            finishList.add(qd);
                          }
                          else
                          {
                            DocumentIngestStatus oldDocStatus = qd.getLastIngestedStatus();
                            String documentIDHash = dd.getDocumentIdentifierHash();
                            String newDocVersion = newVersionStringArray[i];
                            String newAuthorityName = aclAuthority;
                            if (newAuthorityName == null)
                              newAuthorityName = "";

                            versionMap.put(dd.getDocumentIdentifierHash(),newDocVersion);

                            if (newDocVersion == null)
                            {
                              deleteList.add(qd);
                            }
                            else
                            {
                              // Not getting deleted, so we must do the finish processing (i.e. conditionally requeue), so note that.
                              finishList.add(qd);

                              // See if we need to add, or update.
                              boolean allowIngest = false;
                              if (oldDocStatus == null)
                              {
                                // Add
                                allowIngest = true;
                                // Fall through to allow the processing
                              }
                              else
                              {
                                // Update.  There are two possibilities here.  (1) the same version
                                // that was there before is there now (which may mean a rescan),
                                // or (2) there are different versions (which ALWAYS means a rescan).
                                String oldDocVersion = oldDocStatus.getDocumentVersion();
                                if (oldDocVersion == null)
                                  oldDocVersion = "";
                                String oldAuthorityName = oldDocStatus.getDocumentAuthorityNameString();
                                if (oldAuthorityName == null)
                                  oldAuthorityName = "";
                                String oldOutputVersion = oldDocStatus.getOutputVersion();
                                if (oldOutputVersion == null)
                                  oldOutputVersion = "";
                                String oldParameterVersion = oldDocStatus.getParameterVersion();
                                if (oldParameterVersion == null)
                                  oldParameterVersion = "";

                                // Start the comparison processing
                                if (newDocVersion.length() == 0)
                                {
                                  // Always reingest
                                  allowIngest = true;
                                }
                                else if (oldDocVersion.equals(newDocVersion) &&
                                  oldAuthorityName.equals(newAuthorityName) &&
                                  oldOutputVersion.equals(newOutputVersion) &&
                                  oldParameterVersion.equals(newParameterVersion))
                                {
                                  // The old logic was as follows:
                                  //
                                  // If the crawl is an incremental crawl, then we do NOT add this
                                  // document to the fetch list, even for scanning and no ingestion.
                                  // But we *do* add it, scan only, if this was a "full crawl".
                                  //
                                  // Apparently this was designed to prevent a document that had
                                  // already been processed and had queued stuff from causing deletions
                                  // under 'full scan' conditions, because those child documents would
                                  // not be requeued then.  This contrasts with the incremental case,
                                  // where we really don't want to refetch the document simply to find
                                  // children - or do we?  The connector has to make that decision, it
                                  // seems to me.  If it's the kind of document that might have children,
                                  // then rescanning is warranted under ANY conditions; if it's not,
                                  // then the connector can decide to just do nothing.
                                  //
                                  // For the kinds of connectors where all documents have children,
                                  // preventing the fetch is not likely to help much.  These kinds of
                                  // connectors (rss and web) depend on the document checksum to
                                  // determine version anyway, so the document is fetched regardless.
                                  // At least we prevent the ingestion.

                                  // Fall through to allow the scanning, but not the ingest
                                }
                                else
                                  allowIngest = true;
                              }

                              fetchList.add(new DocumentToProcess(qd,!allowIngest));
                              if (!allowIngest)
                                ingesterCheckList.add(documentIDHash);
                            }
                          }

                        }
                        activeDocuments.clear();

                        // We are done transfering activeDocuments documents to the other lists for processing.
                        // Those lists will all need to be processed, but the processList is special because it
                        // must be processed in the same context as the version fetch.

                        // Note the documents that have been checked but not reingested.  This should happen BEFORE we need
                        // the statistics (which are calculated during the finishlist step below)
                        if (ingesterCheckList.size() > 0)
                        {
                          String[] checkClasses = new String[ingesterCheckList.size()];
                          String[] checkIDs = new String[ingesterCheckList.size()];
                          for (int i = 0; i < checkIDs.length; i++)
                          {
                            checkClasses[i] = connectionName;
                            checkIDs[i] = ingesterCheckList.get(i);
                          }
                          ingester.documentCheckMultiple(outputName,checkClasses,checkIDs,currentTime);
                        }

                        // First, make the things we will need for all subsequent steps.
                        ProcessActivity activity = new ProcessActivity(processID,
                          threadContext,rt,jobManager,ingester,
                          currentTime,job,connection,connector,connMgr,legalLinkTypes,ingestLogger,abortSet,outputVersion,newParameterVersion);
                        try
                        {

                          // Finishlist and Fetchlist are parallel.  Fetchlist contains what we need to process.
                          if (fetchList.size() > 0)
                          {
                            // Build a list of id's and flags
                            String[] processIDs = new String[fetchList.size()];
                            String[] processIDHashes = new String[fetchList.size()];
                            String[] versions = new String[fetchList.size()];
                            boolean[] scanOnly = new boolean[fetchList.size()];

                            for (int i = 0; i < fetchList.size(); i++)
                            {
                              DocumentToProcess dToP = fetchList.get(i);
                              DocumentDescription dd = dToP.getDocument().getDocumentDescription();
                              processIDs[i] = dd.getDocumentIdentifier();
                              processIDHashes[i] = dd.getDocumentIdentifierHash();
                              versions[i] = versionMap.get(dd.getDocumentIdentifierHash());
                              scanOnly[i] = dToP.getScanOnly();
                            }

                            if (Thread.currentThread().isInterrupted())
                              throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

                            if (Logging.threads.isDebugEnabled())
                              Logging.threads.debug("Worker thread about to process "+Integer.toString(processIDs.length)+" documents");

                            // Now, process in bulk
                            try
                            {

                              connector.processDocuments(processIDs,versions,activity,job.getSpecification(),scanOnly,jobType);

                              // Flush remaining references into the database!
                              activity.flush();

                              // "Finish" the documents (removing unneeded carrydown info, etc.)
                              DocumentDescription[] requeueCandidates = jobManager.finishDocuments(job.getID(),legalLinkTypes,processIDHashes,job.getHopcountMode());

                              ManifoldCF.requeueDocumentsDueToCarrydown(jobManager,
                                requeueCandidates,connector,connection,rt,currentTime);

                              if (Logging.threads.isDebugEnabled())
                                Logging.threads.debug("Worker thread done processing "+Integer.toString(processIDs.length)+" documents");

                            }
                            catch (ServiceInterruption e)
                            {
                              // This service interruption could have resulted
                              // after some or all of the documents ingested.
                              // They will therefore need to go into the PENDINGPURGATORY
                              // state.

                              if (!e.jobInactiveAbort())
                                Logging.jobs.warn("Service interruption reported for job "+
                                  job.getID()+" connection '"+job.getConnectionName()+"': "+
                                  e.getMessage());

                              if (!e.jobInactiveAbort() && e.isAbortOnFail())
                                abortOnFail = new ManifoldCFException("Repeated service interruptions - failure processing document"+((e.getCause()!=null)?": "+e.getCause().getMessage():""),e.getCause());

                              // Mark the current documents to be recrawled in the
                              // time specified, except for the ones beyond their limits.
                              // Those will either be deleted, or an exception will be thrown that
                              // will abort the current job.

                              deleteList.clear();
                              ArrayList requeueList = new ArrayList();

                              Set<String> fetchDocuments = new HashSet<String>();
                              for (int i = 0; i < fetchList.size(); i++)
                              {
                                fetchDocuments.add(fetchList.get(i).getDocument().getDocumentDescription().getDocumentIdentifierHash());
                              }
                              List<QueuedDocument> newFinishList = new ArrayList<QueuedDocument>();
                              for (int i = 0; i < finishList.size(); i++)
                              {
                                QueuedDocument qd = finishList.get(i);
                                if (fetchDocuments.contains(qd.getDocumentDescription().getDocumentIdentifierHash()))
                                {
                                  DocumentDescription dd = qd.getDocumentDescription();
                                  // Check for hard failure.  But no hard failure possible of it's a job inactive abort.
                                  if (!e.jobInactiveAbort() && (dd.getFailTime() != -1L && dd.getFailTime() < e.getRetryTime() ||
                                    dd.getFailRetryCount() == 0))
                                  {
                                    // Treat this as a hard failure.
                                    if (e.isAbortOnFail())
                                    {
                                      rescanList.add(qd);
                                    }
                                    else
                                    {
                                      // We want this particular document to be not included in the
                                      // reprocessing.  Therefore, we do the same thing as we would
                                      // if we got back a null version.
                                      deleteList.add(qd);
                                    }
                                  }
                                  else
                                  {
                                    requeueList.add(qd);
                                  }
                                }
                                else
                                  newFinishList.add(qd);
                              }

                              // Requeue the documents we've identified
                              requeueDocuments(jobManager,requeueList,e.getRetryTime(),e.getFailTime(),
                                e.getFailRetryCount());

                              // We've disposed of all the documents, so finishlist is now clear
                              finishList = newFinishList;
                            }
                          } // End of fetching

                          if (finishList.size() > 0)
                          {
                            // In both job types, we have to go through the finishList to figure out what to do with the documents.
                            // In the case of a document that was aborted, we must requeue it for immediate reprocessing in BOTH job types.
                            switch (job.getType())
                            {
                            case IJobDescription.TYPE_CONTINUOUS:
                              {
                                // We need to populate timeArray
                                String[] timeIDClasses = new String[finishList.size()];
                                String[] timeIDHashes = new String[finishList.size()];
                                for (int i = 0; i < timeIDHashes.length; i++)
                                {
                                  QueuedDocument qd = (QueuedDocument)finishList.get(i);
                                  DocumentDescription dd = qd.getDocumentDescription();
                                  String documentIDHash = dd.getDocumentIdentifierHash();
                                  timeIDClasses[i] = connectionName;
                                  timeIDHashes[i] = documentIDHash;
                                }
                                long[] timeArray = ingester.getDocumentUpdateIntervalMultiple(outputName,timeIDClasses,timeIDHashes);
                                Long[] recheckTimeArray = new Long[timeArray.length];
                                int[] actionArray = new int[timeArray.length];
                                DocumentDescription[] recrawlDocs = new DocumentDescription[finishList.size()];
                                for (int i = 0; i < finishList.size(); i++)
                                {
                                  QueuedDocument qd = finishList.get(i);
                                  recrawlDocs[i] = qd.getDocumentDescription();
                                  String documentID = recrawlDocs[i].getDocumentIdentifier();

                                  // If aborted due to sequencing issue, then requeue for reprocessing immediately, ignoring everything else.
                                  boolean wasAborted = abortSet.get(documentID) != null;
                                  if (wasAborted)
                                  {
                                    // Requeue for immediate reprocessing
                                    if (Logging.scheduling.isDebugEnabled())
                                      Logging.scheduling.debug("Document '"+documentID+"' will be RESCANNED as soon as prerequisites are met");

                                    actionArray[i] = IJobManager.ACTION_RESCAN;
                                    recheckTimeArray[i] = new Long(0L);     // Must not use null; that means 'never'.
                                  }
                                  else
                                  {
                                    // Calculate the next time to run, or time to expire.

                                    // For run time, the formula is to calculate the running avg interval between changes,
                                    // add an additional interval (which comes from the job description),
                                    // and add that to the current time.
                                    // One caveat: we really want to calculate the interval from the last
                                    // time change was detected, but this is not implemented yet.
                                    long timeAmt = timeArray[i];
                                    // null value indicates never to schedule

                                    Long recrawlTime = activity.calculateDocumentRescheduleTime(currentTime,timeAmt,documentID);
                                    Long expireTime = activity.calculateDocumentExpireTime(currentTime,documentID);


                                    // Merge the two times together.  We decide on the action based on the action with the lowest time.
                                    if (expireTime == null || (recrawlTime != null && recrawlTime.longValue() < expireTime.longValue()))
                                    {
                                      if (Logging.scheduling.isDebugEnabled())
                                        Logging.scheduling.debug("Document '"+documentID+"' will be RESCANNED at "+recrawlTime.toString());
                                      recheckTimeArray[i] = recrawlTime;
                                      actionArray[i] = IJobManager.ACTION_RESCAN;
                                    }
                                    else if (recrawlTime == null || (expireTime != null && recrawlTime.longValue() > expireTime.longValue()))
                                    {
                                      if (Logging.scheduling.isDebugEnabled())
                                        Logging.scheduling.debug("Document '"+documentID+"' will be REMOVED at "+expireTime.toString());
                                      recheckTimeArray[i] = expireTime;
                                      actionArray[i] = IJobManager.ACTION_REMOVE;
                                    }
                                    else
                                    {
                                      // Default activity if conflict will be rescan
                                      if (Logging.scheduling.isDebugEnabled() && recrawlTime != null)
                                        Logging.scheduling.debug("Document '"+documentID+"' will be RESCANNED at "+recrawlTime.toString());
                                      recheckTimeArray[i] = recrawlTime;
                                      actionArray[i] = IJobManager.ACTION_RESCAN;
                                    }
                                  }
                                }

                                jobManager.requeueDocumentMultiple(recrawlDocs,recheckTimeArray,actionArray);

                              }
                              break;
                            case IJobDescription.TYPE_SPECIFIED:
                              {
                                // Separate the ones we actually finished from the ones we need to requeue because they were aborted
                                List<DocumentDescription> completedList = new ArrayList<DocumentDescription>();
                                List<DocumentDescription> abortedList = new ArrayList<DocumentDescription>();
                                for (int i = 0; i < finishList.size(); i++)
                                {
                                  QueuedDocument qd = finishList.get(i);
                                  DocumentDescription dd = qd.getDocumentDescription();
                                  if (abortSet.get(dd.getDocumentIdentifier()) != null)
                                  {
                                    // The document was aborted, so put it into the abortedList
                                    abortedList.add(dd);
                                  }
                                  else
                                  {
                                    // The document was completed.
                                    completedList.add(dd);
                                  }
                                }

                                // Requeue the ones that must be repeated
                                if (abortedList.size() > 0)
                                {
                                  DocumentDescription[] docDescriptions = new DocumentDescription[abortedList.size()];
                                  Long[] recheckTimeArray = new Long[docDescriptions.length];
                                  int[] actionArray = new int[docDescriptions.length];
                                  for (int i = 0; i < docDescriptions.length; i++)
                                  {
                                    docDescriptions[i] = abortedList.get(i);
                                    recheckTimeArray[i] = new Long(0L);
                                    actionArray[i] = IJobManager.ACTION_RESCAN;
                                  }

                                  jobManager.requeueDocumentMultiple(docDescriptions,recheckTimeArray,actionArray);
                                }

                                // Mark the ones completed that were actually completed.
                                if (completedList.size() > 0)
                                {
                                  DocumentDescription[] docDescriptions = new DocumentDescription[completedList.size()];
                                  for (int i = 0; i < docDescriptions.length; i++)
                                  {
                                    docDescriptions[i] = (DocumentDescription)completedList.get(i);
                                  }

                                  jobManager.markDocumentCompletedMultiple(docDescriptions);
                                }
                              }
                              break;
                            default:
                              throw new ManifoldCFException("Unexpected value for job type: '"+Integer.toString(job.getType())+"'");
                            }

                            // Finally, if we're still alive, mark everything as "processed".
                            for (int i = 0; i < finishList.size(); i++)
                            {
                              QueuedDocument qd = finishList.get(i);
                              qd.setProcessed();
                            }

                          }
                        
                        }
                        finally
                        {
                          // Make sure we don't leave any dangling carrydown files
                          activity.discard();
                        }
                          
                        // Successful processing of the set
                        // We count 'get version' time in the average, so even if we decide not to process a doc
                        // it still counts.
                        queueTracker.noteConnectionPerformance(qds.getCount(),connectionName,System.currentTimeMillis() - processingStartTime);

                      }
                      finally
                      {
                        // Release any document temporary storage held by the connector
                        connector.releaseDocumentVersions(currentDocIDArray,newVersionStringArray);
                      }
                    
                    }
                  }
                  
                  // Now, handle the delete list
                  processDeleteLists(outputName,connector,connection,jobManager,
                    deleteList,ingester,
                    job.getID(),legalLinkTypes,ingestLogger,job.getHopcountMode(),rt,currentTime);

                  // Handle hopcount removal
                  processHopcountRemovalLists(outputName,connector,connection,jobManager,
                    hopcountremoveList,ingester,
                    job.getID(),legalLinkTypes,ingestLogger,job.getHopcountMode(),rt,currentTime);

                }
                finally
                {
                  repositoryConnectorPool.release(connection,connector);
                }
              
              }
              
              // Handle rescanning
              for (int i = 0; i < rescanList.size(); i++)
              {
                QueuedDocument qd = rescanList.get(i);
                jobManager.resetDocument(qd.getDocumentDescription(),0L,IJobManager.ACTION_RESCAN,-1L,-1);
                qd.setProcessed();
              }
                
            }
            finally
            {
              // Note termination of processing of these documents in the overlap calculator
              qds.endProcessing(queueTracker);
            }
            
            if (abortOnFail != null)
              throw abortOnFail;
            
          }
          catch (ManifoldCFException e)
          {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              break;

            if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
              throw e;

            if (jobManager.errorAbort(qds.getJobDescription().getID(),e.getMessage()))
            {
              // We eat the exception if there was already one recorded.

              // An exception occurred in the processing of a set of documents.
              // Shut the corresponding job down, with an appropriate error
              Logging.threads.error("Exception tossed: "+e.getMessage(),e);
            }
          }
          finally
          {
            // Go through qds and requeue any that aren't closed out in one way or another.  This allows the job
            // to be aborted; no dangling entries are left around.
            int i = 0;
            while (i < qds.getCount())
            {
              QueuedDocument qd = qds.getDocument(i++);
              if (!qd.wasProcessed())
              {
                jobManager.resetDocument(qd.getDocumentDescription(),0L,IJobManager.ACTION_RESCAN,-1L,-1);
              }
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

            Logging.threads.error("Worker thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
      Logging.threads.fatal("WorkerThread "+id+" initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }

  }

  /** Compare two sorted collection names lists.
  */
  protected static boolean compareArrays(String[] array1, String[] array2)
  {
    if (array1.length != array2.length)
      return false;
    int i = 0;
    while (i < array1.length)
    {
      if (!array1[i].equals(array2[i]))
        return false;
      i++;
    }
    return true;
  }

  protected static void moveList(List<QueuedDocument> sourceList, List<QueuedDocument> targetList)
  {
    for (int i = 0; i < sourceList.size(); i++)
    {
      targetList.add(sourceList.get(i));
    }
    sourceList.clear();
  }

  /** Mark specified documents as 'hopcount removed', and remove them from the
  * index.  Documents in this state are presumed to have:
  * (a) nothing in the index
  * (b) no intrinsic links for which they are the origin
  * In order to guarantee this situation, this method must be capable of doing much
  * of what the deletion method must do.  Specifically, it should be capable of deleting
  * documents from the index should they be already present.
  */
  protected static void processHopcountRemovalLists(String outputName, IRepositoryConnector connector,
    IRepositoryConnection connection, IJobManager jobManager,
    List<QueuedDocument> hopcountremoveList,
    IIncrementalIngester ingester,
    Long jobID, String[] legalLinkTypes, OutputActivity ingestLogger,
    int hopcountMethod, IReprioritizationTracker rt, long currentTime)
    throws ManifoldCFException
  {
    // Remove from index
    hopcountremoveList = removeFromIndex(outputName,connection.getName(),jobManager,hopcountremoveList,ingester,ingestLogger);
    // Mark as 'hopcountremoved' in the job queue
    processJobQueueHopcountRemovals(hopcountremoveList,connector,connection,
      jobManager,jobID,legalLinkTypes,hopcountMethod,rt,currentTime);
  }

  /** Clear specified documents out of the job queue and from the appliance.
  *@param outputName is the output connection name.
  *@param jobManager is the job manager.
  *@param deleteList is a list of QueuedDocument objects to clean out.
  *@param ingester is the handle to the incremental ingestion API control object.
  *@param ingesterDeleteList is a list of document id's to delete.
  */
  protected static void processDeleteLists(String outputName, IRepositoryConnector connector,
    IRepositoryConnection connection, IJobManager jobManager,
    List<QueuedDocument> deleteList,
    IIncrementalIngester ingester,
    Long jobID, String[] legalLinkTypes, OutputActivity ingestLogger,
    int hopcountMethod, IReprioritizationTracker rt, long currentTime)
    throws ManifoldCFException
  {
    // Remove from index
    deleteList = removeFromIndex(outputName,connection.getName(),jobManager,deleteList,ingester,ingestLogger);
    // Delete from the job queue
    processJobQueueDeletions(deleteList,connector,connection,
      jobManager,jobID,legalLinkTypes,hopcountMethod,rt,currentTime);
  }

  /** Remove a specified set of documents from the index.
  *@return the list of documents whose state needs to be updated in jobqueue.
  */
  protected static List<QueuedDocument> removeFromIndex(String outputName,
    String connectionName, IJobManager jobManager, List<QueuedDocument> deleteList, 
    IIncrementalIngester ingester, OutputActivity ingestLogger)
    throws ManifoldCFException
  {
    List<String> ingesterDeleteList = new ArrayList<String>(deleteList.size());
    for (int i = 0; i < deleteList.size(); i++)
    {
      QueuedDocument qd = deleteList.get(i);
      DocumentIngestStatus oldDocStatus = qd.getLastIngestedStatus();
      // See if we need to delete from index
      if (oldDocStatus != null)
      {
        // Queue up to issue deletion
        ingesterDeleteList.add(qd.getDocumentDescription().getDocumentIdentifierHash());
      }
    }
    
    // First, do the ingester delete list.  This guarantees that if the ingestion system is down, this operation will be handled atomically.
    if (ingesterDeleteList.size() > 0)
    {
      String[] deleteClasses = new String[ingesterDeleteList.size()];
      String[] deleteIDs = new String[ingesterDeleteList.size()];

      for (int i = 0; i < ingesterDeleteList.size(); i++)
      {
        deleteClasses[i] = connectionName;
        deleteIDs[i] = ingesterDeleteList.get(i);
      }
      
      // Try to delete the documents via the output connection.
      try
      {
        ingester.documentDeleteMultiple(outputName,deleteClasses,deleteIDs,ingestLogger);
      }
      catch (ServiceInterruption e)
      {
        // It looks like the output connection is not currently functioning, so we need to requeue instead of deleting
        // those documents that could not be removed.
        List<QueuedDocument> newDeleteList = new ArrayList<QueuedDocument>();
        List<QueuedDocument> newRequeueList = new ArrayList<QueuedDocument>();
        
        Set<String> ingesterSet = new HashSet<String>();
        for (int j = 0 ; j < ingesterDeleteList.size() ; j++)
        {
          String id = ingesterDeleteList.get(j);
          ingesterSet.add(id);
        }
        for (int j = 0 ; j < deleteList.size() ; j++)
        {
          QueuedDocument qd = deleteList.get(j);
          DocumentDescription dd = qd.getDocumentDescription();
          String documentIdentifierHash = dd.getDocumentIdentifierHash();
          if (ingesterSet.contains(documentIdentifierHash))
            newRequeueList.add(qd);
          else
            newDeleteList.add(qd);
        }

        // Requeue those that are supposed to be requeued
        requeueDocuments(jobManager,newRequeueList,e.getRetryTime(),e.getFailTime(),
          e.getFailRetryCount());
        
        // Process the ones that are just new job queue changes
        deleteList = newDeleteList;
      }
    }
    return deleteList;
  }
  
  /** Process job queue deletions.  Either the indexer has already been updated, or it is not necessary to update it.
  */
  protected static void processJobQueueDeletions(List<QueuedDocument> jobmanagerDeleteList,
    IRepositoryConnector connector, IRepositoryConnection connection, IJobManager jobManager,
    Long jobID, String[] legalLinkTypes, int hopcountMethod, IReprioritizationTracker rt, long currentTime)
    throws ManifoldCFException
  {
    // Now, do the document queue cleanup for deletions.
    if (jobmanagerDeleteList.size() > 0)
    {
      DocumentDescription[] deleteDescriptions = new DocumentDescription[jobmanagerDeleteList.size()];
      for (int i = 0; i < deleteDescriptions.length; i++)
      {
        QueuedDocument qd = jobmanagerDeleteList.get(i);
        deleteDescriptions[i] = qd.getDocumentDescription();
      }

      // Do the actual work.
      DocumentDescription[] requeueCandidates = jobManager.markDocumentDeletedMultiple(jobID,legalLinkTypes,deleteDescriptions,hopcountMethod);

      // Requeue those documents that had carrydown data modifications
      ManifoldCF.requeueDocumentsDueToCarrydown(jobManager,
        requeueCandidates,connector,connection,rt,currentTime);

      // Mark all these as done
      for (int i = 0; i < jobmanagerDeleteList.size(); i++)
      {
        QueuedDocument qd = jobmanagerDeleteList.get(i);
        qd.setProcessed();
      }
    }
  }

  /** Process job queue hopcount removals.  All indexer updates have already taken place.
  */
  protected static void processJobQueueHopcountRemovals(List<QueuedDocument> jobmanagerRemovalList,
    IRepositoryConnector connector, IRepositoryConnection connection, IJobManager jobManager,
    Long jobID, String[] legalLinkTypes, int hopcountMethod, IReprioritizationTracker rt, long currentTime)
    throws ManifoldCFException
  {
    // Now, do the document queue cleanup for deletions.
    if (jobmanagerRemovalList.size() > 0)
    {
      DocumentDescription[] removalDescriptions = new DocumentDescription[jobmanagerRemovalList.size()];
      for (int i = 0; i < removalDescriptions.length; i++)
      {
        QueuedDocument qd = jobmanagerRemovalList.get(i);
        removalDescriptions[i] = qd.getDocumentDescription();
      }

      // Do the actual work.
      DocumentDescription[] requeueCandidates = jobManager.markDocumentHopcountRemovalMultiple(jobID,legalLinkTypes,removalDescriptions,hopcountMethod);

      // Requeue those documents that had carrydown data modifications
      ManifoldCF.requeueDocumentsDueToCarrydown(jobManager,
        requeueCandidates,connector,connection,rt,currentTime);

      // Mark all these as done
      for (int i = 0; i < jobmanagerRemovalList.size(); i++)
      {
        QueuedDocument qd = jobmanagerRemovalList.get(i);
        qd.setProcessed();
      }
    }
  }

  /** Requeue documents after a service interruption was detected.
  *@param jobManager is the job manager object.
  *@param requeueList is a list of QueuedDocument objects describing what needs to be requeued.
  *@param retryTime is the time that the first retry ought to be scheduled for.
  *@param failTime is the time beyond which retries lead to hard failure.
  *@param failCount is the number of retries allowed until hard failure.
  */
  protected static void requeueDocuments(IJobManager jobManager, List<QueuedDocument> requeueList, long retryTime, long failTime, int failCount)
    throws ManifoldCFException
  {
    if (requeueList.size() > 0)
    {
      DocumentDescription[] requeueDocs = new DocumentDescription[requeueList.size()];

      int i = 0;
      while (i < requeueDocs.length)
      {
        QueuedDocument qd = requeueList.get(i);
        DocumentDescription dd = qd.getDocumentDescription();
        requeueDocs[i] = dd;
        i++;
      }

      jobManager.resetDocumentMultiple(requeueDocs,retryTime,IJobManager.ACTION_RESCAN,failTime,failCount);

      i = 0;
      while (i < requeueList.size())
      {
        QueuedDocument qd = requeueList.get(i++);
        qd.setProcessed();
      }
    }
  }

  protected static String packParameters(Map<String,Set<String>> forcedParameters)
  {
    StringBuilder sb = new StringBuilder();
    String[] paramNames = new String[forcedParameters.size()];
    int i = 0;
    for (String paramName : forcedParameters.keySet())
    {
      paramNames[i++] = paramName;
    }
    java.util.Arrays.sort(paramNames);
    for (String paramName : paramNames)
    {
      Set<String> values = forcedParameters.get(paramName);
      String[] paramValues = new String[values.size()];
      i = 0;
      for (String paramValue : values)
      {
        paramValues[i++] = paramValue;
      }
      java.util.Arrays.sort(paramValues);
      for (String paramValue : paramValues)
      {
        pack(sb,paramName,'+');
        pack(sb,paramValue,'+');
      }
    }
    return sb.toString();
  }
  
  protected static void pack(StringBuilder sb, String value, char delim)
  {
    for (int i = 0; i < value.length(); i++)
    {
      char x = value.charAt(i);
      if (x == delim || x == '\\')
      {
        sb.append('\\');
      }
      sb.append(x);
    }
    sb.append(delim);
  }

  /** The maximum number of adds that happen in a single transaction */
  protected static final int MAX_ADDS_IN_TRANSACTION = 20;

  // Nested classes

  /** Version activity class wraps access to activity history.
  */
  protected static class VersionActivity implements IVersionActivity
  {
    protected final String processID;
    protected final String connectionName;
    protected final IRepositoryConnectionManager connMgr;
    protected final IJobManager jobManager;
    protected final IJobDescription job;
    protected final IIncrementalIngester ingester;
    protected final HashMap abortSet;
    protected final String outputVersion;

    /** Constructor.
    */
    public VersionActivity(String processID, String connectionName, IRepositoryConnectionManager connMgr,
      IJobManager jobManager, IJobDescription job, IIncrementalIngester ingester, HashMap abortSet,
      String outputVersion)
    {
      this.processID = processID;
      this.connectionName = connectionName;
      this.connMgr = connMgr;
      this.jobManager = jobManager;
      this.job = job;
      this.ingester = ingester;
      this.abortSet = abortSet;
      this.outputVersion = outputVersion;
    }

    /** Check whether a mime type is indexable by the currently specified output connector.
    *@param mimeType is the mime type to check, not including any character set specification.
    *@return true if the mime type is indexable.
    */
    public boolean checkMimeTypeIndexable(String mimeType)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkMimeTypeIndexable(job.getOutputConnectionName(),outputVersion,mimeType);
    }

    /** Check whether a document is indexable by the currently specified output connector.
    *@param localFile is the local copy of the file to check.
    *@return true if the document is indexable.
    */
    public boolean checkDocumentIndexable(File localFile)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkDocumentIndexable(job.getOutputConnectionName(),outputVersion,localFile);
    }

    /** Check whether a document of a specified length is indexable by the currently specified output connector.
    *@param length is the length to check.
    *@return true if the document is indexable.
    */
    public boolean checkLengthIndexable(long length)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkLengthIndexable(job.getOutputConnectionName(),outputVersion,length);
    }

    /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
    * to help filter out documents that are not worth indexing.
    *@param url is the URL of the document.
    *@return true if the file is indexable.
    */
    public boolean checkURLIndexable(String url)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkURLIndexable(job.getOutputConnectionName(),outputVersion,url);
    }

    /** Record time-stamped information about the activity of the connector.
    *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
    *       activity has an associated time; the startTime field records when the activity began.  A null value
    *       indicates that the start time and the finishing time are the same.
    *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
    *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
    *       "fetch document" activity.  Cannot be null.
    *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
    *@param entityIdentifier is a (possibly long) string which identifies the object involved in the history record.
    *       The interpretation of this field will differ from connector to connector.  May be null.
    *@param resultCode contains a terse description of the result of the activity.  The description is limited in
    *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
    *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
    *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
    *@param childIdentifiers is a set of child entity identifiers associated with this activity.  May be null.
    */
    public void recordActivity(Long startTime, String activityType, Long dataSize,
      String entityIdentifier, String resultCode, String resultDescription, String[] childIdentifiers)
      throws ManifoldCFException
    {
      connMgr.recordHistory(connectionName,startTime,activityType,dataSize,entityIdentifier,resultCode,
        resultDescription,childIdentifiers);
    }

    /** Retrieve data passed from parents to a specified child document.
    *@param localIdentifier is the document identifier of the document we want the recorded data for.
    *@param dataName is the name of the data items to retrieve.
    *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
    */
    public String[] retrieveParentData(String localIdentifier, String dataName)
      throws ManifoldCFException
    {
      return jobManager.retrieveParentData(job.getID(),ManifoldCF.hash(localIdentifier),dataName);
    }

    /** Retrieve data passed from parents to a specified child document.
    *@param localIdentifier is the document identifier of the document we want the recorded data for.
    *@param dataName is the name of the data items to retrieve.
    *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
    */
    public CharacterInput[] retrieveParentDataAsFiles(String localIdentifier, String dataName)
      throws ManifoldCFException
    {
      return jobManager.retrieveParentDataAsFiles(job.getID(),ManifoldCF.hash(localIdentifier),dataName);
    }

    /** Check whether current job is still active.
    * This method is provided to allow an individual connector that needs to wait on some long-term condition to give up waiting due to the job
    * itself being aborted.  If the connector should abort, this method will raise a properly-formed ServiceInterruption, which if thrown to the
    * caller, will signal that the current versioning activity remains incomplete and must be retried when the job is resumed.
    */
    public void checkJobStillActive()
      throws ManifoldCFException, ServiceInterruption
    {
      if (jobManager.checkJobActive(job.getID()) == false)
        throw new ServiceInterruption("Job no longer active",System.currentTimeMillis(),true);
    }

    /** Begin an event sequence.
    * This method should be called by a connector when a sequencing event should enter the "pending" state.  If the event is already in that state,
    * this method will return false, otherwise true.  The connector has the responsibility of appropriately managing sequencing given the response
    * status.
    *@param eventName is the event name.
    *@return false if the event is already in the "pending" state.
    */
    public boolean beginEventSequence(String eventName)
      throws ManifoldCFException
    {
      return jobManager.beginEventSequence(processID,eventName);
    }

    /** Complete an event sequence.
    * This method should be called to signal that an event is no longer in the "pending" state.  This can mean that the prerequisite processing is
    * completed, but it can also mean that prerequisite processing was aborted or cannot be completed.
    * Note well: This method should not be called unless the connector is CERTAIN that an event is in progress, and that the current thread has
    * the sole right to complete it.  Otherwise, race conditions can develop which would be difficult to diagnose.
    *@param eventName is the event name.
    */
    public void completeEventSequence(String eventName)
      throws ManifoldCFException
    {
      jobManager.completeEventSequence(eventName);
    }

    /** Abort processing a document (for sequencing reasons).
    * This method should be called in order to cause the specified document to be requeued for later processing.  While this is similar in some respects
    * to the semantics of a ServiceInterruption, it is applicable to only one document at a time, and also does not specify any delay period, since it is
    * presumed that the reason for the requeue is because of sequencing issues synchronized around an underlying event.
    *@param localIdentifier is the document identifier to requeue
    */
    public void retryDocumentProcessing(String localIdentifier)
      throws ManifoldCFException
    {
      // Accumulate aborts
      abortSet.put(localIdentifier,localIdentifier);
    }

    /** Create a global string from a simple string.
    *@param simpleString is the simple string.
    *@return a global string.
    */
    public String createGlobalString(String simpleString)
    {
      return ManifoldCF.createGlobalString(simpleString);
    }

    /** Create a connection-specific string from a simple string.
    *@param simpleString is the simple string.
    *@return a connection-specific string.
    */
    public String createConnectionSpecificString(String simpleString)
    {
      return ManifoldCF.createConnectionSpecificString(connectionName,simpleString);
    }

    /** Create a job-based string from a simple string.
    *@param simpleString is the simple string.
    *@return a job-specific string.
    */
    public String createJobSpecificString(String simpleString)
    {
      return ManifoldCF.createJobSpecificString(job.getID(),simpleString);
    }

  }

  /** Process activity class wraps access to the ingester and job queue.
  */
  protected static class ProcessActivity implements IProcessActivity
  {
    // Member variables
    protected final String processID;
    protected final IThreadContext threadContext;
    protected final IJobManager jobManager;
    protected final IIncrementalIngester ingester;
    protected final long currentTime;
    protected final IJobDescription job;
    protected final IRepositoryConnection connection;
    protected final IRepositoryConnector connector;
    protected final IRepositoryConnectionManager connMgr;
    protected final String[] legalLinkTypes;
    protected final OutputActivity ingestLogger;
    protected final IReprioritizationTracker rt;
    protected final HashMap abortSet;
    protected final String outputVersion;
    protected final String parameterVersion;
    
    // We submit references in bulk, because that's way more efficient.
    protected HashMap referenceList = new HashMap();

    // Keep track of lower and upper reschedule bounds separately.  Contains a Long and is keyed by a document identifier.
    protected HashMap lowerRescheduleBounds = new HashMap();
    protected HashMap upperRescheduleBounds = new HashMap();
    protected HashMap lowerExpireBounds = new HashMap();
    protected HashMap upperExpireBounds = new HashMap();

    // Origination times
    protected HashMap originationTimes = new HashMap();

    /** Constructor.
    *@param jobManager is the job manager
    *@param ingester is the ingester
    */
    public ProcessActivity(String processID, IThreadContext threadContext,
      IReprioritizationTracker rt, IJobManager jobManager,
      IIncrementalIngester ingester, long currentTime,
      IJobDescription job, IRepositoryConnection connection, IRepositoryConnector connector,
      IRepositoryConnectionManager connMgr, String[] legalLinkTypes, OutputActivity ingestLogger,
      HashMap abortSet, String outputVersion, String parameterVersion)
    {
      this.processID = processID;
      this.threadContext = threadContext;
      this.rt = rt;
      this.jobManager = jobManager;
      this.ingester = ingester;
      this.currentTime = currentTime;
      this.job = job;
      this.connection = connection;
      this.connector = connector;
      this.connMgr = connMgr;
      this.legalLinkTypes = legalLinkTypes;
      this.ingestLogger = ingestLogger;
      this.abortSet = abortSet;
      this.outputVersion = outputVersion;
      this.parameterVersion = parameterVersion;
    }

    /** Clean up any dangling information, before abandoning this process activity object */
    public void discard()
      throws ManifoldCFException
    {
      Iterator iter = referenceList.keySet().iterator();
      while (iter.hasNext())
      {
        DocumentReference dr = (DocumentReference)iter.next();
        dr.discard();
      }
      referenceList.clear();
    }

    /** Add a document description to the current job's queue.
    *@param localIdentifier is the local document identifier to add (for the connector that
    * fetched the document).
    *@param parentIdentifier is the document identifier that is considered to be the "parent"
    * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
    *@param relationshipType is the string describing the kind of relationship described by this
    * reference.  This must be one of the strings returned by the IRepositoryConnector method
    * "getRelationshipTypes()".  May be null.
    *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
    *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
    *          The type of each object must either be a String, or a CharacterInput.
    *@param originationTime is the time, in ms since epoch, that the document originated.  Pass null if none or unknown.
    *@param prereqEventNames are the names of the prerequisite events which this document requires prior to processing.  Pass null if none.
    */
    @Override
    public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
      String[] dataNames, Object[][] dataValues, Long originationTime, String[] prereqEventNames)
      throws ManifoldCFException
    {
      String localIdentifierHash = ManifoldCF.hash(localIdentifier);
      String parentIdentifierHash = null;
      if (parentIdentifier != null && parentIdentifier.length() > 0)
        parentIdentifierHash = ManifoldCF.hash(parentIdentifier);

      if (Logging.threads.isDebugEnabled())
        Logging.threads.debug("Adding document reference, from "+((parentIdentifier==null)?"no parent":"'"+parentIdentifier+"'")
      +" to '"+localIdentifier+"', relationship type "+((relationshipType==null)?"null":"'"+relationshipType+"'")
      +", with "+((dataNames==null)?"no":Integer.toString(dataNames.length))+" data types, origination time="+((originationTime==null)?"unknown":originationTime.toString()));

      Long expireInterval = job.getExpiration();
      if (expireInterval != null)
      {
        // We should not queue documents that have already expired; it wastes time
        // So, calculate when this document will expire
        long currentTime = System.currentTimeMillis();
        long expireTime;
        if (originationTime == null)
          expireTime = currentTime + expireInterval.longValue();
        else
          expireTime = originationTime.longValue() + expireInterval.longValue();
        if (expireTime <= currentTime)
        {
          if (Logging.threads.isDebugEnabled())
            Logging.threads.debug("Not adding document reference for '"+localIdentifier+"', since it has already expired");
          return;
        }
      }

      if (referenceList.size() == MAX_ADDS_IN_TRANSACTION)
      {
        // Output what we've got, and reset
        processDocumentReferences();
      }
      DocumentReference dr = new DocumentReference(localIdentifierHash,localIdentifier,new DocumentBin(parentIdentifierHash,relationshipType));
      DocumentReference existingDr = (DocumentReference)referenceList.get(dr);
      if (existingDr == null)
      {
        referenceList.put(dr,dr);
        existingDr = dr;
      }
      // We can't just keep a reference to the passed-in data values, because if these are files the caller will delete them upon the return of this method.  So, for all data values we keep,
      // make a local copy, and remove the file pointer from the caller's copy.  It then becomes the responsibility of the ProcessActivity object to clean up these items when it is discarded.
      Object[][] savedDataValues;
      if (dataValues != null)
      {
        savedDataValues = new Object[dataValues.length][];
        int q = 0;
        while (q < savedDataValues.length)
        {
          Object[] innerArray = dataValues[q];
          if (innerArray != null)
          {
            savedDataValues[q] = new Object[innerArray.length];
            int z = 0;
            while (z < innerArray.length)
            {
              Object innerValue = innerArray[z];
              if (innerValue != null)
              {
                if (innerValue instanceof CharacterInput)
                  (savedDataValues[q])[z] = ((CharacterInput)innerValue).transfer();
                else
                  (savedDataValues[q])[z] = innerValue;
              }
              else
                (savedDataValues[q])[z] = null;
              z++;
            }
          }
          else
            savedDataValues[q] = null;
          q++;
        }
      }
      else
        savedDataValues = null;

      existingDr.addData(dataNames,savedDataValues);
      existingDr.addPrerequisiteEvents(prereqEventNames);
    }

    /** Add a document description to the current job's queue.
    *@param localIdentifier is the local document identifier to add (for the connector that
    * fetched the document).
    *@param parentIdentifier is the document identifier that is considered to be the "parent"
    * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
    *@param relationshipType is the string describing the kind of relationship described by this
    * reference.  This must be one of the strings returned by the IRepositoryConnector method
    * "getRelationshipTypes()".  May be null.
    *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
    *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
    *@param originationTime is the time, in ms since epoch, that the document originated.  Pass null if none or unknown.
    */
    @Override
    public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
      String[] dataNames, Object[][] dataValues, Long originationTime)
      throws ManifoldCFException
    {
      addDocumentReference(localIdentifier,parentIdentifier,relationshipType,dataNames,dataValues,originationTime,null);
    }

    /** Add a document description to the current job's queue.
    *@param localIdentifier is the local document identifier to add (for the connector that
    * fetched the document).
    *@param parentIdentifier is the document identifier that is considered to be the "parent"
    * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
    *@param relationshipType is the string describing the kind of relationship described by this
    * reference.  This must be one of the strings returned by the IRepositoryConnector method
    * "getRelationshipTypes()".  May be null.
    *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
    *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
    */
    @Override
    public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
      String[] dataNames, Object[][] dataValues)
      throws ManifoldCFException
    {
      addDocumentReference(localIdentifier,parentIdentifier,relationshipType,dataNames,dataValues,null);
    }

    /** Add a document description to the current job's queue.
    *@param localIdentifier is the local document identifier to add (for the connector that
    * fetched the document).
    *@param parentIdentifier is the document identifier that is considered to be the "parent"
    * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
    *@param relationshipType is the string describing the kind of relationship described by this
    * reference.  This must be one of the strings returned by the IRepositoryConnector method
    * "getRelationshipTypes()".  May be null.
    */
    @Override
    public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType)
      throws ManifoldCFException
    {
      addDocumentReference(localIdentifier,parentIdentifier,relationshipType,null,null);
    }

    /** Add a document description to the current job's queue.  This method is equivalent to
    * addDocumentReference(localIdentifier,null,null).
    *@param localIdentifier is the local document identifier to add (for the connector that
    * fetched the document).
    */
    @Override
    public void addDocumentReference(String localIdentifier)
      throws ManifoldCFException
    {
      addDocumentReference(localIdentifier,null,null,null,null);
    }

    /** Retrieve data passed from parents to a specified child document.
    *@param localIdentifier is the document identifier of the document we want the recorded data for.
    *@param dataName is the name of the data items to retrieve.
    *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
    */
    @Override
    public String[] retrieveParentData(String localIdentifier, String dataName)
      throws ManifoldCFException
    {
      return jobManager.retrieveParentData(job.getID(),ManifoldCF.hash(localIdentifier),dataName);
    }

    /** Retrieve data passed from parents to a specified child document.
    *@param localIdentifier is the document identifier of the document we want the recorded data for.
    *@param dataName is the name of the data items to retrieve.
    *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
    */
    @Override
    public CharacterInput[] retrieveParentDataAsFiles(String localIdentifier, String dataName)
      throws ManifoldCFException
    {
      return jobManager.retrieveParentDataAsFiles(job.getID(),ManifoldCF.hash(localIdentifier),dataName);
    }

    /** Record a document version, but don't ingest it.
    * ServiceInterruption is thrown if this action must be rescheduled.
    *@param documentIdentifier is the document identifier.
    *@param version is the document version.
    */
    @Override
    public void recordDocument(String documentIdentifier, String version)
      throws ManifoldCFException, ServiceInterruption
    {
      String documentIdentifierHash = ManifoldCF.hash(documentIdentifier);
      ingester.documentRecord(job.getOutputConnectionName(),job.getConnectionName(),documentIdentifierHash,version,currentTime,ingestLogger);
    }

    /** Ingest the current document.
    *@param documentIdentifier is the document's local identifier.
    *@param version is the version of the document, as reported by the getDocumentVersions() method of the
    *       corresponding repository connector.
    *@param documentURI is the URI to use to retrieve this document from the search interface (and is
    *       also the unique key in the index).
    *@param data is the document data.  The data is closed after ingestion is complete.
    */
    @Override
    public void ingestDocument(String documentIdentifier, String version, String documentURI, RepositoryDocument data)
      throws ManifoldCFException, ServiceInterruption
    {
      // We should not get called here if versions agree, unless the repository
      // connector cannot distinguish between versions - in which case it must
      // always ingest (essentially)

      String documentIdentifierHash = ManifoldCF.hash(documentIdentifier);

      if (data != null)
      {
        Map<String,Set<String>> forcedMetadata = job.getForcedMetadata();
        
        // Modify the repository document with forced parameters.
        for (String paramName : forcedMetadata.keySet())
        {
          Set<String> values = forcedMetadata.get(paramName);
          String[] paramValues = new String[values.size()];
          int j = 0;
          for (String value : values)
          {
            paramValues[j++] = value;
          }
          data.addField(paramName,paramValues);
        }
      }
        
      // First, we need to add into the metadata the stuff from the job description.
      ingester.documentIngest(job.getOutputConnectionName(),
        job.getConnectionName(),documentIdentifierHash,
        version,outputVersion,parameterVersion,
        connection.getACLAuthority(),
        data,currentTime,
        documentURI,
        ingestLogger);
    }

    /** Delete the current document from the search engine index, while keeping track of the version information
    * for it (to reduce churn).
    *@param documentIdentifier is the document's local identifier.
    *@param version is the version of the document, as reported by the getDocumentVersions() method of the
    *       corresponding repository connector.
    */
    @Override
    public void deleteDocument(String documentIdentifier, String version)
      throws ManifoldCFException, ServiceInterruption
    {
      if (version.length() == 0)
        deleteDocument(documentIdentifier);
      else
        ingestDocument(documentIdentifier,version,null,null);
    }

    /** Delete the current document from the search engine index.  This method does NOT keep track of version
    * information for the document and thus can lead to "churn", whereby the same document is queued, versioned,
    * and removed on subsequent crawls.  It therefore should be considered to be deprecated, in favor of
    * deleteDocument(String localIdentifier, String version).
    *@param documentIdentifier is the document's local identifier.
    */
    @Override
    public void deleteDocument(String documentIdentifier)
      throws ManifoldCFException, ServiceInterruption
    {
      String documentIdentifierHash = ManifoldCF.hash(documentIdentifier);
      ingester.documentDelete(job.getOutputConnectionName(),
        job.getConnectionName(),documentIdentifierHash,
        ingestLogger);
    }

    /** Override the schedule for the next time a document is crawled.
    * Calling this method allows you to set an upper recrawl bound, lower recrawl bound, upper expire bound, lower expire bound,
    * or a combination of these, on a specific document.  This method is only effective if the job is a continuous one, and if the
    * identifier you pass in is being processed.
    *@param localIdentifier is the document's local identifier.
    *@param lowerRecrawlBoundTime is the time in ms since epoch that the reschedule time should not fall BELOW, or null if none.
    *@param upperRecrawlBoundTime is the time in ms since epoch that the reschedule time should not rise ABOVE, or null if none.
    *@param lowerExpireBoundTime is the time in ms since epoch that the expire time should not fall BELOW, or null if none.
    *@param upperExpireBoundTime is the time in ms since epoch that the expire time should not rise ABOVE, or null if none.
    */
    @Override
    public void setDocumentScheduleBounds(String localIdentifier,
      Long lowerRecrawlBoundTime, Long upperRecrawlBoundTime,
      Long lowerExpireBoundTime, Long upperExpireBoundTime)
      throws ManifoldCFException
    {
      if (lowerRecrawlBoundTime != null)
        lowerRescheduleBounds.put(localIdentifier,lowerRecrawlBoundTime);
      else
        lowerRescheduleBounds.remove(localIdentifier);
      if (upperRecrawlBoundTime != null)
        upperRescheduleBounds.put(localIdentifier,upperRecrawlBoundTime);
      else
        upperRescheduleBounds.remove(localIdentifier);
      if (lowerExpireBoundTime != null)
        lowerExpireBounds.put(localIdentifier,lowerExpireBoundTime);
      else
        lowerExpireBounds.remove(localIdentifier);
      if (upperExpireBoundTime != null)
        upperExpireBounds.put(localIdentifier,upperExpireBoundTime);
      else
        upperExpireBounds.remove(localIdentifier);
    }

    /** Override a document's origination time.
    * Use this method to signal the framework that a document's origination time is something other than the first time it was crawled.
    *@param localIdentifier is the document's local identifier.
    *@param originationTime is the document's origination time, or null if unknown.
    */
    @Override
    public void setDocumentOriginationTime(String localIdentifier,
      Long originationTime)
      throws ManifoldCFException
    {
      if (originationTime == null)
        originationTimes.remove(localIdentifier);
      else
        originationTimes.put(localIdentifier,originationTime);
    }

    /** Find a document's lower rescheduling time bound, if any */
    public Long getDocumentRescheduleLowerBoundTime(String localIdentifier)
    {
      return (Long)lowerRescheduleBounds.get(localIdentifier);
    }

    /** Find a document's upper rescheduling time bound, if any */
    public Long getDocumentRescheduleUpperBoundTime(String localIdentifier)
    {
      return (Long)upperRescheduleBounds.get(localIdentifier);
    }

    /** Find a document's lower expiration time bound, if any */
    public Long getDocumentExpirationLowerBoundTime(String localIdentifier)
    {
      return (Long)lowerExpireBounds.get(localIdentifier);
    }

    /** Find a document's upper expiration time bound, if any */
    public Long getDocumentExpirationUpperBoundTime(String localIdentifier)
    {
      return (Long)upperExpireBounds.get(localIdentifier);
    }

    /** Get a document's origination time */
    public Long getDocumentOriginationTime(String localIdentifier)
    {
      return (Long)originationTimes.get(localIdentifier);
    }

    public Long calculateDocumentRescheduleTime(long currentTime, long timeAmt, String localIdentifier)
    {
      Long recrawlTime = null;
      Long recrawlInterval = job.getInterval();
      if (recrawlInterval != null)
        recrawlTime = new Long(currentTime + timeAmt + recrawlInterval.longValue());
      if (Logging.scheduling.isDebugEnabled())
        Logging.scheduling.debug("Default rescan time for document '"+localIdentifier+"' is "+((recrawlTime==null)?"NEVER":recrawlTime.toString()));
      Long lowerBound = getDocumentRescheduleLowerBoundTime(localIdentifier);
      if (lowerBound != null)
      {
        if (recrawlTime == null || recrawlTime.longValue() < lowerBound.longValue())
        {
          recrawlTime = lowerBound;
          if (Logging.scheduling.isDebugEnabled())
            Logging.scheduling.debug(" Rescan time overridden for document '"+localIdentifier+"' due to lower bound; new value is "+recrawlTime.toString());
        }
      }
      Long upperBound = getDocumentRescheduleUpperBoundTime(localIdentifier);
      if (upperBound != null)
      {
        if (recrawlTime == null || recrawlTime.longValue() > upperBound.longValue())
        {
          recrawlTime = upperBound;
          if (Logging.scheduling.isDebugEnabled())
            Logging.scheduling.debug(" Rescan time overridden for document '"+localIdentifier+"' due to upper bound; new value is "+recrawlTime.toString());
        }
      }
      return recrawlTime;
    }

    public Long calculateDocumentExpireTime(long currentTime, String localIdentifier)
    {
      // For expire time, we take the document's origination time, plus the expiration interval (which comes from the job).
      Long originationTime = getDocumentOriginationTime(localIdentifier);
      if (originationTime == null)
        originationTime = new Long(currentTime);
      Long expireInterval = job.getExpiration();
      Long expireTime = null;
      if (expireInterval != null)
        expireTime = new Long(originationTime.longValue() + expireInterval.longValue());
      Long lowerBound = getDocumentExpirationLowerBoundTime(localIdentifier);
      if (lowerBound != null)
      {
        if (expireTime == null || expireTime.longValue() < lowerBound.longValue())
          expireTime = lowerBound;
      }
      Long upperBound = getDocumentExpirationUpperBoundTime(localIdentifier);
      if (upperBound != null)
      {
        if (expireTime == null || expireTime.longValue() > upperBound.longValue())
          expireTime = upperBound;
      }
      return expireTime;
    }

    /** Reset the recorded times */
    public void resetTimes()
    {
      lowerRescheduleBounds.clear();
      upperRescheduleBounds.clear();
      lowerExpireBounds.clear();
      upperExpireBounds.clear();
    }

    /** Record time-stamped information about the activity of the connector.
    *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
    *       activity has an associated time; the startTime field records when the activity began.  A null value
    *       indicates that the start time and the finishing time are the same.
    *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
    *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
    *       "fetch document" activity.  Cannot be null.
    *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
    *@param entityIdentifier is a (possibly long) string which identifies the object involved in the history record.
    *       The interpretation of this field will differ from connector to connector.  May be null.
    *@param resultCode contains a terse description of the result of the activity.  The description is limited in
    *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
    *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
    *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
    *@param childIdentifiers is a set of child entity identifiers associated with this activity.  May be null.
    */
    @Override
    public void recordActivity(Long startTime, String activityType, Long dataSize,
      String entityIdentifier, String resultCode, String resultDescription, String[] childIdentifiers)
      throws ManifoldCFException
    {
      connMgr.recordHistory(connection.getName(),startTime,activityType,dataSize,entityIdentifier,resultCode,
        resultDescription,childIdentifiers);
    }

    /** Flush the outstanding references into the database.
    */
    public void flush()
      throws ManifoldCFException
    {
      processDocumentReferences();
    }

    /** Process outstanding document references, in batch.
    */
    protected void processDocumentReferences()
      throws ManifoldCFException
    {
      if (referenceList.size() == 0)
        return;

      // We have to segregate the references by link type and parent.
      HashMap linkBins = new HashMap();
      Iterator iter = referenceList.keySet().iterator();
      while (iter.hasNext())
      {
        DocumentReference dr = (DocumentReference)iter.next();
        DocumentBin key = dr.getKey();
        ArrayList set = (ArrayList)linkBins.get(key);
        if (set == null)
        {
          set = new ArrayList();
          linkBins.put(key,set);
        }
        set.add(dr);
      }

      // Now, go through link types.
      iter = linkBins.keySet().iterator();
      while (iter.hasNext())
      {
        DocumentBin db = (DocumentBin)iter.next();
        ArrayList set = (ArrayList)linkBins.get(db);

        String[] docidHashes = new String[set.size()];
        String[] docids = new String[set.size()];
        IPriorityCalculator[] priorities = new IPriorityCalculator[set.size()];
        String[][] dataNames = new String[docids.length][];
        Object[][][] dataValues = new Object[docids.length][][];
        String[][] eventNames = new String[docids.length][];

        long currentTime = System.currentTimeMillis();

        rt.clearPreloadRequests();
        for (int j = 0; j < docidHashes.length; j++)
        {
          DocumentReference dr = (DocumentReference)set.get(j);
          docidHashes[j] = dr.getLocalIdentifierHash();
          docids[j] = dr.getLocalIdentifier();
          dataNames[j] = dr.getDataNames();
          dataValues[j] = dr.getDataValues();
          eventNames[j] = dr.getPrerequisiteEventNames();

          // Calculate desired document priority based on current queuetracker status.
          String[] bins = ManifoldCF.calculateBins(connector,dr.getLocalIdentifier());
          PriorityCalculator p = new PriorityCalculator(rt,connection,bins);
          priorities[j] = p;
          p.makePreloadRequest();
        }
        rt.preloadBinValues();

        jobManager.addDocuments(processID,
          job.getID(),legalLinkTypes,docidHashes,docids,db.getParentIdentifierHash(),db.getLinkType(),job.getHopcountMode(),
          dataNames,dataValues,currentTime,priorities,eventNames);
        
        rt.clearPreloadedValues();
      }

      discard();
    }

    /** Check whether current job is still active.
    * This method is provided to allow an individual connector that needs to wait on some long-term condition to give up waiting due to the job
    * itself being aborted.  If the connector should abort, this method will raise a properly-formed ServiceInterruption, which if thrown to the
    * caller, will signal that the current processing activity remains incomplete and must be retried when the job is resumed.
    */
    @Override
    public void checkJobStillActive()
      throws ManifoldCFException, ServiceInterruption
    {
      if (jobManager.checkJobActive(job.getID()) == false)
        throw new ServiceInterruption("Job no longer active",System.currentTimeMillis());
    }

    /** Begin an event sequence.
    * This method should be called by a connector when a sequencing event should enter the "pending" state.  If the event is already in that state,
    * this method will return false, otherwise true.  The connector has the responsibility of appropriately managing sequencing given the response
    * status.
    *@param eventName is the event name.
    *@return false if the event is already in the "pending" state.
    */
    @Override
    public boolean beginEventSequence(String eventName)
      throws ManifoldCFException
    {
      return jobManager.beginEventSequence(processID,eventName);
    }

    /** Complete an event sequence.
    * This method should be called to signal that an event is no longer in the "pending" state.  This can mean that the prerequisite processing is
    * completed, but it can also mean that prerequisite processing was aborted or cannot be completed.
    * Note well: This method should not be called unless the connector is CERTAIN that an event is in progress, and that the current thread has
    * the sole right to complete it.  Otherwise, race conditions can develop which would be difficult to diagnose.
    *@param eventName is the event name.
    */
    @Override
    public void completeEventSequence(String eventName)
      throws ManifoldCFException
    {
      jobManager.completeEventSequence(eventName);
    }

    /** Abort processing a document (for sequencing reasons).
    * This method should be called in order to cause the specified document to be requeued for later processing.  While this is similar in some respects
    * to the semantics of a ServiceInterruption, it is applicable to only one document at a time, and also does not specify any delay period, since it is
    * presumed that the reason for the requeue is because of sequencing issues synchronized around an underlying event.
    *@param localIdentifier is the document identifier to requeue
    */
    @Override
    public void retryDocumentProcessing(String localIdentifier)
      throws ManifoldCFException
    {
      // Accumulate aborts
      abortSet.put(localIdentifier,localIdentifier);
    }

    /** Check whether a mime type is indexable by the currently specified output connector.
    *@param mimeType is the mime type to check, not including any character set specification.
    *@return true if the mime type is indexable.
    */
    @Override
    public boolean checkMimeTypeIndexable(String mimeType)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkMimeTypeIndexable(job.getOutputConnectionName(),outputVersion,mimeType);
    }

    /** Check whether a document is indexable by the currently specified output connector.
    *@param localFile is the local copy of the file to check.
    *@return true if the document is indexable.
    */
    @Override
    public boolean checkDocumentIndexable(File localFile)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkDocumentIndexable(job.getOutputConnectionName(),outputVersion,localFile);
    }

    /** Check whether a document of a specified length is indexable by the currently specified output connector.
    *@param length is the length to check.
    *@return true if the document is indexable.
    */
    @Override
    public boolean checkLengthIndexable(long length)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkLengthIndexable(job.getOutputConnectionName(),outputVersion,length);
    }

    /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
    * to help filter out documents that are not worth indexing.
    *@param url is the URL of the document.
    *@return true if the file is indexable.
    */
    @Override
    public boolean checkURLIndexable(String url)
      throws ManifoldCFException, ServiceInterruption
    {
      return ingester.checkURLIndexable(job.getOutputConnectionName(),outputVersion,url);
    }

    /** Create a global string from a simple string.
    *@param simpleString is the simple string.
    *@return a global string.
    */
    @Override
    public String createGlobalString(String simpleString)
    {
      return ManifoldCF.createGlobalString(simpleString);
    }

    /** Create a connection-specific string from a simple string.
    *@param simpleString is the simple string.
    *@return a connection-specific string.
    */
    @Override
    public String createConnectionSpecificString(String simpleString)
    {
      return ManifoldCF.createConnectionSpecificString(connection.getName(),simpleString);
    }

    /** Create a job-based string from a simple string.
    *@param simpleString is the simple string.
    *@return a job-specific string.
    */
    @Override
    public String createJobSpecificString(String simpleString)
    {
      return ManifoldCF.createJobSpecificString(job.getID(),simpleString);
    }

  }

  /** DocumentBin class */
  protected static class DocumentBin
  {
    protected String linkType;
    protected String parentIdentifierHash;

    public DocumentBin(String parentIdentifierHash, String linkType)
    {
      this.parentIdentifierHash = parentIdentifierHash;
      this.linkType = linkType;
    }

    public String getParentIdentifierHash()
    {
      return parentIdentifierHash;
    }

    public String getLinkType()
    {
      return linkType;
    }

    public int hashCode()
    {
      return ((linkType==null)?0:linkType.hashCode()) + ((parentIdentifierHash==null)?0:parentIdentifierHash.hashCode());
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof DocumentBin))
        return false;
      DocumentBin db = (DocumentBin)o;
      if (linkType == null || db.linkType == null)
      {
        if (linkType != db.linkType)
          return false;
      }
      else
      {
        if (!linkType.equals(db.linkType))
          return false;
      }
      if (parentIdentifierHash == null || db.parentIdentifierHash == null)
      {
        if (parentIdentifierHash != db.parentIdentifierHash)
          return false;
      }
      else
      {
        if (!parentIdentifierHash.equals(db.parentIdentifierHash))
          return false;
      }
      return true;
    }
  }

  /** Class describing document reference.
  * Note: If the same document reference occurs multiple times, the data names and values should AGGREGATE, rather than the newer one replacing the older.
  * Similar treatment will occur for prerequisites, although that's unlikely to be used.
  */
  protected static class DocumentReference
  {
    protected String localIdentifierHash;
    protected String localIdentifier;
    protected DocumentBin db;
    /** This hashmap is keyed by data name and has a hashmap as a value (which contains the data values) */
    protected HashMap data = new HashMap();
    /** This hashmap contains the prerequisite event names */
    protected HashMap prereqEvents = new HashMap();

    public DocumentReference(String localIdentifierHash, String localIdentifier, DocumentBin db)
    {
      this.localIdentifierHash = localIdentifierHash;
      this.localIdentifier = localIdentifier;
      this.db = db;
    }

    /** Close all object data references.  This should be called whenever a DocumentReference object is abandoned. */
    public void discard()
      throws ManifoldCFException
    {
      Iterator iter = data.keySet().iterator();
      while (iter.hasNext())
      {
        String dataName = (String)iter.next();
        ArrayList list = (ArrayList)data.get(dataName);
        int i = 0;
        while (i < list.size())
        {
          Object o = (Object)list.get(i++);
          if (o instanceof CharacterInput)
            ((CharacterInput)o).discard();
        }
      }
    }

    public void addData(String[] dataNames, Object[][] dataValues)
    {
      if (dataNames == null || dataValues == null)
        return;
      int i = 0;
      while (i < dataNames.length)
      {
        addData(dataNames[i],dataValues[i]);
        i++;
      }
    }

    public void addData(String dataName, Object[] dataValues)
    {
      if (dataName == null || dataValues == null)
        return;
      int i = 0;
      while (i < dataValues.length)
      {
        addData(dataName,dataValues[i++]);
      }
    }

    public void addData(String dataName, Object dataValue)
    {
      if (dataName == null)
        return;
      ArrayList valueMap = (ArrayList)data.get(dataName);
      if (valueMap == null)
      {
        valueMap = new ArrayList();
        data.put(dataName,valueMap);
      }
      // Without the hash value, it's impossible to keep track of value uniqueness in this layer.  So, I've removed any attempts to do so; jobManager.addDocuments()
      // will have to do that job instead.
      valueMap.add(dataValue);
    }

    public void addPrerequisiteEvents(String[] eventNames)
    {
      if (eventNames == null)
        return;
      int i = 0;
      while (i < eventNames.length)
      {
        addPrerequisiteEvent(eventNames[i++]);
      }
    }

    public void addPrerequisiteEvent(String eventName)
    {
      prereqEvents.put(eventName,eventName);
    }

    public DocumentBin getKey()
    {
      return db;
    }

    public String getLocalIdentifierHash()
    {
      return localIdentifierHash;
    }

    public String getLocalIdentifier()
    {
      return localIdentifier;
    }

    public String[] getPrerequisiteEventNames()
    {
      String[] rval = new String[prereqEvents.size()];
      int i = 0;
      Iterator iter = prereqEvents.keySet().iterator();
      while (iter.hasNext())
      {
        rval[i++] = (String)iter.next();
      }
      return rval;
    }

    public String[] getDataNames()
    {
      String[] rval = new String[data.size()];
      int i = 0;
      Iterator iter = data.keySet().iterator();
      while (iter.hasNext())
      {
        String dataName = (String)iter.next();
        rval[i++] = dataName;
      }
      return rval;
    }

    public Object[][] getDataValues()
    {
      // Presumably the values will correspond with the names ONLY if no changes have occurred to the hash table.
      Object[][] rval = new Object[data.size()][];
      int i = 0;
      Iterator iter = data.keySet().iterator();
      while (iter.hasNext())
      {
        String dataName = (String)iter.next();
        ArrayList values = (ArrayList)data.get(dataName);
        Object[] valueArray = new Object[values.size()];
        rval[i] = valueArray;
        int j = 0;
        while (j < valueArray.length)
        {
          valueArray[j] = values.get(j);
          j++;
        }
        i++;
      }
      return rval;
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof DocumentReference))
        return false;
      DocumentReference other = (DocumentReference)o;
      if (!other.localIdentifierHash.equals(localIdentifierHash))
        return false;
      return other.db.equals(db);
    }

    public int hashCode()
    {
      return localIdentifierHash.hashCode() + db.hashCode();
    }
  }

  /** Class that represents a decision to process a document.
  */
  protected static class DocumentToProcess
  {
    protected QueuedDocument document;
    protected boolean scanOnly;

    /** Construct.
    *@param document is the document to process.
    *@param scanOnly is true if the document should be scanned, but not ingested.
    */
    public DocumentToProcess(QueuedDocument document, boolean scanOnly)
    {
      this.document = document;
      this.scanOnly = scanOnly;
    }

    /** Get the document.
    *@return the document.
    */
    public QueuedDocument getDocument()
    {
      return document;
    }

    /** Get the 'scan only' flag.
    *@return true if only scan should be attempted.
    */
    public boolean getScanOnly()
    {
      return scanOnly;
    }
  }

  /** The ingest logger class */
  protected static class OutputActivity implements IOutputActivity
  {

    // Connection name
    protected String connectionName;
    // Connection manager
    protected IRepositoryConnectionManager connMgr;
    // Output connection name
    protected String outputConnectionName;

    /** Constructor */
    public OutputActivity(String connectionName, IRepositoryConnectionManager connMgr, String outputConnectionName)
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

    /** Qualify an access token appropriately, to match access tokens as returned by mod_aa.  This method
    * includes the authority name with the access token, if any, so that each authority may establish its own token space.
    *@param authorityNameString is the name of the authority to use to qualify the access token.
    *@param accessToken is the raw, repository access token.
    *@return the properly qualified access token.
    */
    public String qualifyAccessToken(String authorityNameString, String accessToken)
      throws ManifoldCFException
    {
      try
      {
        if (authorityNameString == null)
          return java.net.URLEncoder.encode(accessToken,"UTF-8");
        return java.net.URLEncoder.encode(authorityNameString,"UTF-8") + ":" + java.net.URLEncoder.encode(accessToken,"UTF-8");
      }
      catch (java.io.UnsupportedEncodingException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
    }


  }

}
