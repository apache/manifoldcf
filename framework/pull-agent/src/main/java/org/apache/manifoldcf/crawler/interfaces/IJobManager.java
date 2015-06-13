/* $Id: IJobManager.java 991295 2010-08-31 19:12:14Z kwright $ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.f
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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.List;

/** This manager deals with jobs.  Each job is associated with a repository connection, and has a number
* of scheduling options: starting every n hours/days/weeks/months, on specific dates, or "continuous" (which basically
* establishes a priority queue based on modification frequency).
* The job itself also specifies "seeds" (or starting points), which are the places that scanning begins.
* NOTE WELL: Every job is incremental.  This means that the job will check for deletions among all the documents
* that it has scanned in the past, as part of the process of ingesting.
*/
public interface IJobManager
{
  public static final String _rcsid = "@(#)$Id: IJobManager.java 991295 2010-08-31 19:12:14Z kwright $";

  // Actions, for continuous crawling
  public static final int ACTION_RESCAN = 0;
  public static final int ACTION_REMOVE = 1;

  // Document states, for status reports.
  public static final int DOCSTATE_NEVERPROCESSED = 0;
  public static final int DOCSTATE_PREVIOUSLYPROCESSED = 1;
  public static final int DOCSTATE_OUTOFSCOPE = 2;

  // Document statuses, for status reports.
  public static final int DOCSTATUS_INACTIVE = 0;
  public static final int DOCSTATUS_PROCESSING = 1;
  public static final int DOCSTATUS_EXPIRING = 2;
  public static final int DOCSTATUS_DELETING = 3;
  public static final int DOCSTATUS_READYFORPROCESSING = 4;
  public static final int DOCSTATUS_READYFOREXPIRATION = 5;
  public static final int DOCSTATUS_WAITINGFORPROCESSING = 6;
  public static final int DOCSTATUS_WAITINGFOREXPIRATION = 7;
  public static final int DOCSTATUS_WAITINGFOREVER = 8;
  public static final int DOCSTATUS_HOPCOUNTEXCEEDED = 9;

  // Job stop reasons
  public static final int STOP_ERRORABORT = 0;
  public static final int STOP_MANUALABORT = 1;
  public static final int STOP_MANUALPAUSE = 2;
  public static final int STOP_SCHEDULEPAUSE = 3;
  public static final int STOP_RESTART = 4;
  
  /** Install the job manager's tables.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall the job manager's tables.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException;

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException;

  /** Load a sorted list of job descriptions.
  *@return the list, sorted by description.
  */
  public IJobDescription[] getAllJobs()
    throws ManifoldCFException;

  /** Create a new job.
  *@return the new job.
  */
  public IJobDescription createJob()
    throws ManifoldCFException;

  /** Delete a job.
  *@param id is the job's identifier.  This method will purge all the records belonging to the job from the database, as
  * well as remove all documents indexed by the job from the index.
  */
  public void deleteJob(Long id)
    throws ManifoldCFException;

  /** Load a job for editing.
  *@param id is the job's identifier.
  *@return null if the job doesn't exist.
  */
  public IJobDescription load(Long id)
    throws ManifoldCFException;

  /** Load a job.
  *@param id is the job's identifier.
  *@param readOnly is true if a read-only object is desired.
  *@return null if the job doesn't exist.
  */
  public IJobDescription load(Long id, boolean readOnly)
    throws ManifoldCFException;

  /** Save a job.
  *@param jobDescription is the job description.
  */
  public void save(IJobDescription jobDescription)
    throws ManifoldCFException;

  /** See if there's a reference to a connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfReference(String connectionName)
    throws ManifoldCFException;

  /** See if there's a reference to a notification connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfNotificationReference(String connectionName)
    throws ManifoldCFException;

  /** See if there's a reference to an output connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfOutputReference(String connectionName)
    throws ManifoldCFException;

  /** See if there's a reference to a transformation connection name.
  *@param connectionName is the name of the connection.
  *@return true if there is a reference, false otherwise.
  */
  public boolean checkIfTransformationReference(String connectionName)
    throws ManifoldCFException;

  /** Get the job IDs associated with a given connection name.
  *@param connectionName is the name of the connection.
  *@return the set of job id's associated with that connection.
  */
  public IJobDescription[] findJobsForConnection(String connectionName)
    throws ManifoldCFException;

  /** Clear job seeding state.
  *@param jobID is the job ID.
  */
  public void clearJobSeedingState(Long jobID)
    throws ManifoldCFException;

  // These methods cover activities that require interaction with the job queue.
  // The job queue is maintained underneath this interface, and all threads that perform
  // job activities need to go through this layer.

  /** Reset the job queue for an individual process ID.
  * If a node was shut down in the middle of doing something, sufficient information should
  * be around in the database to allow the node's activities to be cleaned up.
  *@param processID is the process ID of the node we want to clean up after.
  */
  public void cleanupProcessData(String processID)
    throws ManifoldCFException;

  /** Reset the job queue for all process IDs.
  * If a node was shut down in the middle of doing something, sufficient information should
  * be around in the database to allow the node's activities to be cleaned up.
  */
  public void cleanupProcessData()
    throws ManifoldCFException;

  /** Prepare to start the entire cluster.
  * If there are no other nodes alive, then at the time the first node comes up, we need to
  * reset the job queue for ALL processes that had been running before.  This method must
  * be called in addition to cleanupProcessData().
  */
  public void prepareForClusterStart()
    throws ManifoldCFException;

  /** Reset as part of restoring document worker threads.
  *@param processID is the current process ID.
  */
  public void resetDocumentWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring seeding threads.
  */
  public void resetSeedingWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring doc delete threads.
  *@param processID is the current process ID.
  */
  public void resetDocDeleteWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring doc cleanup threads.
  *@param processID is the current process ID.
  */
  public void resetDocCleanupWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring delete startup threads.
  *@param processID is the current process ID.
  */
  public void resetDeleteStartupWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring notification threads.
  *@param processID is the current process ID.
  */
  public void resetNotificationWorkerStatus(String processID)
    throws ManifoldCFException;

  /** Reset as part of restoring startup threads.
  *@param processID is the current process ID.
  */
  public void resetStartupWorkerStatus(String processID)
    throws ManifoldCFException;

  // These methods support the "set doc priority" thread

  /** Clear all document priorities, in preparation for reprioritization of all previously-prioritized documents.
  * This method is called to start the dynamic reprioritization cycle, which follows this
  * method with explicit prioritization of all documents, piece-meal, using getNextNotYetProcessedReprioritizationDocuments(),
  * and writeDocumentPriorities().
  */
  public void clearAllDocumentPriorities()
    throws ManifoldCFException;

  /** Get a list of not-yet-processed documents to reprioritize.  Documents in all jobs will be
  * returned by this method.  Up to n document descriptions will be returned.
  *@param processID is the process that requests the reprioritization documents.
  *@param n is the maximum number of document descriptions desired.
  *@return the document descriptions.
  */
  public DocumentDescription[] getNextNotYetProcessedReprioritizationDocuments(String processID, int n)
    throws ManifoldCFException;

  /** Save a set of document priorities.  In the case where a document was eligible to have its
  * priority set, but it no longer is eligible, then the provided priority will not be written.
  *@param descriptions are the document descriptions.
  *@param priorities are the desired priorities.
  */
  public void writeDocumentPriorities(DocumentDescription[] descriptions, IPriorityCalculator[] priorities)
    throws ManifoldCFException;

  // This method supports the "expiration" thread

  /** Get up to the next n documents to be expired.
  * This method marks the documents whose descriptions have been returned as "being processed", or active.
  * The same marking is used as is used for documents that have been queued for worker threads.  The model
  * is thus identical.
  *
  *@param processID is the current process ID.
  *@param n is the maximum number of records desired.
  *@param currentTime is the current time.
  *@return the array of document descriptions to expire.
  */
  public DocumentSetAndFlags getExpiredDocuments(String processID, int n, long currentTime)
    throws ManifoldCFException;

  // This method supports the "queue stuffer" thread

  /** Get up to the next n document(s) to be fetched and processed.
  * This fetch returns records that contain the document identifier, plus all instructions
  * pertaining to the document's handling (e.g. whether it should be refetched if the version
  * has not changed).
  * This method also marks the documents whose descriptions have be returned as "being processed".
  *@param processID is the current process ID.
  *@param n is the number of documents desired.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@param interval is the number of milliseconds that this set of documents should represent (for throttling).
  *@param blockingDocuments is the place to record documents that were encountered, are eligible for reprioritization,
  *  but could not be queued due to throttling considerations.
  *@param statistics are the current performance statistics per connection, which are used to balance the queue stuffing
  *  so that individual connections are not overwhelmed.
  *@param scanRecord retains the bins from all documents encountered from the query, even those that were skipped due
  * to being overcommitted.
  *@return the array of document descriptions to fetch and process.
  */
  public DocumentDescription[] getNextDocuments(String processID,
    int n, long currentTime, long interval,
    BlockingDocuments blockingDocuments, PerformanceStatistics statistics,
    DepthStatistics scanRecord)
    throws ManifoldCFException;

  // These methods support the individual fetch/process threads.

  /** Verify that a specific job is indeed still active.  This is used to permit abort or pause to be relatively speedy.
  * The query done within MUST be cached in order to not cause undue performance degradation.
  *@param jobID is the job identifier.
  *@return true if the job is in one of the "active" states.
  */
  public boolean checkJobActive(Long jobID)
    throws ManifoldCFException;

  /** Verify if a job is still processing documents, or no longer has any outstanding active documents */
  public boolean checkJobBusy(Long jobID)
    throws ManifoldCFException;

  /** Note completion of document processing by a job thread of a document.
  * This method causes the state of the document to be marked as "completed".
  *@param documentDescriptions are the description objects for the documents that were processed.
  */
  public void markDocumentCompletedMultiple(DocumentDescription[] documentDescriptions)
    throws ManifoldCFException;

  /** Note completion of document processing by a job thread of a document.
  * This method causes the state of the document to be marked as "completed".
  *@param documentDescription is the description object for the document that was processed.
  */
  public void markDocumentCompleted(DocumentDescription documentDescription)
    throws ManifoldCFException;

  /** Delete from queue as a result of processing of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  The RESCAN variants are interpreted
  * as meaning that the document should not be deleted, but should instead be popped back on the queue for
  * a repeat processing attempt.
  *@param documentDescriptions are the set of description objects for the documents that were processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentDeletedMultiple(Long jobID, String[] legalLinkTypes, DocumentDescription[] documentDescriptions,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Delete from queue as a result of processing of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  The RESCAN variants are interpreted
  * as meaning that the document should not be deleted, but should instead be popped back on the queue for
  * a repeat processing attempt.
  *@param documentDescription is the description object for the document that was processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentDeleted(Long jobID, String[] legalLinkTypes, DocumentDescription documentDescription,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Mark hopcount removal from queue as a result of processing of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  The RESCAN variants are interpreted
  * as meaning that the document should not be marked as removed, but should instead be popped back on the queue for
  * a repeat processing attempt.
  *@param documentDescriptions are the set of description objects for the documents that were processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentHopcountRemovalMultiple(Long jobID, String[] legalLinkTypes, DocumentDescription[] documentDescriptions,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Mark hopcount removal from queue as a result of processing of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  The RESCAN variants are interpreted
  * as meaning that the document should not be marked as removed, but should instead be popped back on the queue for
  * a repeat processing attempt.
  *@param documentDescription is the description object for the document that was processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentHopcountRemoval(Long jobID, String[] legalLinkTypes, DocumentDescription documentDescription,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Delete from queue as a result of expiration of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  Since the document expired,
  * no special activity takes place as a result of the document being in a RESCAN state.
  *@param documentDescriptions are the set of description objects for the documents that were processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentExpiredMultiple(Long jobID, String[] legalLinkTypes, DocumentDescription[] documentDescriptions,
    int hopcountMethod)
    throws ManifoldCFException;
  
  /** Delete from queue as a result of expiration of an active document.
  * The document is expected to be in one of the active states: ACTIVE, ACTIVESEEDING,
  * ACTIVENEEDSRESCAN, ACTIVESEEDINGNEEDSRESCAN.  Since the document expired,
  * no special activity takes place as a result of the document being in a RESCAN state.
  *@param documentDescription is the description object for the document that was processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentExpired(Long jobID, String[] legalLinkTypes, DocumentDescription documentDescription,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Delete from queue as a result of cleaning up an unreachable document.
  * The document is expected to be in the PURGATORY state.  There is never any need to reprocess the
  * document.
  *@param documentDescriptions are the set of description objects for the documents that were processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentCleanedUpMultiple(Long jobID, String[] legalLinkTypes, DocumentDescription[] documentDescriptions,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Delete from queue as a result of cleaning up an unreachable document.
  * The document is expected to be in the PURGATORY state.  There is never any need to reprocess the
  * document.
  *@param documentDescription is the description object for the document that was processed.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] markDocumentCleanedUp(Long jobID, String[] legalLinkTypes, DocumentDescription documentDescription,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Requeue a document set because of carrydown changes.
  * This method is called when carrydown data is modified for a set of documents.  The documents must be requeued for immediate reprocessing, even to the
  * extent that if one is *already* being processed, it will need to be done over again.
  *@param documentDescriptions is the set of description objects for the documents that have had their parent carrydown information changed.
  *@param docPriorities are the document priorities to assign to the documents, if needed.
  */
  public void carrydownChangeDocumentMultiple(DocumentDescription[] documentDescriptions, IPriorityCalculator[] docPriorities)
    throws ManifoldCFException;

  /** Requeue a document because of carrydown changes.
  * This method is called when carrydown data is modified for a document.  The document must be requeued for immediate reprocessing, even to the
  * extent that if it is *already* being processed, it will need to be done over again.
  *@param documentDescription is the description object for the document that has had its parent carrydown information changed.
  *@param docPriority is the document priority to assign to the document, if needed.
  */
  public void carrydownChangeDocument(DocumentDescription documentDescription, IPriorityCalculator docPriority)
    throws ManifoldCFException;

  /** Requeue a document for further processing in the future.
  * This method is called after a document is processed, when the job is a "continuous" one.
  * It is essentially equivalent to noting that the document processing is complete, except the
  * document remains on the queue.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param executeTimes are the times that the documents should be rescanned.  Null indicates "never".
  *@param actions are what should be done when the time arrives.  Choices are ACTION_RESCAN or ACTION_REMOVE.
  */
  public void requeueDocumentMultiple(DocumentDescription[] documentDescriptions, Long[] executeTimes,
    int[] actions)
    throws ManifoldCFException;


  /** Requeue a document for further processing in the future.
  * This method is called after a document is processed, when the job is a "continuous" one.
  * It is essentially equivalent to noting that the document processing is complete, except the
  * document remains on the queue.
  *@param documentDescription is the description object for the document that was processed.
  *@param executeTime is the time that the document should be rescanned.  Null indicates "never".
  *@param action is what should be done when the time arrives.  Choices include ACTION_RESCAN or ACTION_REMOVE.
  */
  public void requeueDocument(DocumentDescription documentDescription, Long executeTime,
    int action)
    throws ManifoldCFException;

  /** Reset documents for further processing in the future.
  * This method is called after a service interruption is thrown.
  * It is essentially equivalent to resetting the time for documents to be reprocessed.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param executeTime is the time that the documents should be rescanned.
  *@param failTime is the time beyond which hard failure should occur.
  *@param failCount is the number of permitted failures before a hard error is signalled.
  */
  public void resetDocumentMultiple(DocumentDescription[] documentDescriptions, long executeTime,
    int action, long failTime, int failCount)
    throws ManifoldCFException;

  /** Reset an active document back to its former state.
  * This gets done when there's a service interruption and the document cannot be processed yet.
  *@param documentDescription is the description object for the document that was processed.
  *@param executeTime is the time that the document should be rescanned.
  *@param failTime is the time that the document should be considered to have failed, if it has not been
  * successfully read until then.
  *@param failCount is the number of permitted failures before a hard error is signalled.
  */
  public void resetDocument(DocumentDescription documentDescription, long executeTime, int action, long failTime,
    int failCount)
    throws ManifoldCFException;

  /** Reset a set of deleting documents for further processing in the future.
  * This method is called after some unknown number of the documents were deleted, but then an ingestion service interruption occurred.
  * Note well: The logic here basically presumes that we cannot know whether the documents were indeed processed or not.
  * If we knew for a fact that none of the documents had been handled, it would be possible to look at the document's
  * current status and decide what the new status ought to be, based on a true rollback scenario.  Such cases, however, are rare enough so that
  * special logic is probably not worth it.
  *@param documentDescriptions is the set of description objects for the document that was processed.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetDeletingDocumentMultiple(DocumentDescription[] documentDescriptions, long checkTime)
    throws ManifoldCFException;

  /** Reset a deleting document back to its former state.
  * This gets done when a deleting thread sees a service interruption, etc., from the ingestion system.
  *@param documentDescription is the description object for the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetDeletingDocument(DocumentDescription documentDescription, long checkTime)
    throws ManifoldCFException;

  /** Reset a cleaning document back to its former state.
  * This gets done when a cleaning thread sees a service interruption, etc., from the ingestion system.
  *@param documentDescription is the description object for the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetCleaningDocument(DocumentDescription documentDescription, long checkTime)
    throws ManifoldCFException;

  /** Reset a set of cleaning documents for further processing in the future.
  * This method is called after some unknown number of the documents were cleaned, but then an ingestion service interruption occurred.
  * Note well: The logic here basically presumes that we cannot know whether the documents were indeed cleaned or not.
  * If we knew for a fact that none of the documents had been handled, it would be possible to look at the document's
  * current status and decide what the new status ought to be, based on a true rollback scenario.  Such cases, however, are rare enough so that
  * special logic is probably not worth it.
  *@param documentDescriptions is the set of description objects for the document that was cleaned.
  *@param checkTime is the minimum time for the next cleaning attempt.
  */
  public void resetCleaningDocumentMultiple(DocumentDescription[] documentDescriptions, long checkTime)
    throws ManifoldCFException;

  /** Retry startup.
  *@param jobStartRecord is the current job startup record.
  *@param failTime is the new fail time (-1L if none).
  *@param failRetryCount is the new fail retry count (-1 if none).
  */
  public void retryStartup(JobStartRecord jobStartRecord, long failTime, int failRetryCount)
    throws ManifoldCFException;

  /** Retry seeding.
  *@param jobSeedingRecord is the current job seeding record.
  *@param failTime is the new fail time (-1L if none).
  *@param failRetryCount is the new fail retry count (-1 if none).
  */
  public void retrySeeding(JobSeedingRecord jobSeedingRecord, long failTime, int failRetryCount)
    throws ManifoldCFException;

  /** Retry notification.
  *@param jobNotifyRecord is the current job notification record.
  *@param failTime is the new fail time (-1L if none).
  *@param failRetryCount is the new fail retry count (-1 if none).
  */
  public void retryNotification(JobNotifyRecord jobNotifyRecord, long failTime, int failRetryCount)
    throws ManifoldCFException;

  /** Retry delete notification.
  *@param jnr is the current job notification record.
  *@param failTime is the new fail time (-1L if none).
  *@param failCount is the new fail retry count (-1 if none).
  */
  public void retryDeleteNotification(JobNotifyRecord jnr, long failTime, int failCount)
    throws ManifoldCFException;

  /** Add an initial set of documents to the queue.
  * This method is called during job startup, when the queue is being loaded.
  * A set of document references is passed to this method, which updates the status of the document
  * in the specified job's queue, according to specific state rules.
  *@param processID is the current process ID.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the hashes of the local document identifiers (primary key).
  *@param docIDs are the local document identifiers.
  *@param overrideSchedule is true if any existing document schedule should be overridden.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  *@param documentPriorities are the document priorities corresponding to the document identifiers.
  *@param prereqEventNames are the events that must be completed before each document can be processed.
  */
  public void addDocumentsInitial(String processID,
    Long jobID, String[] legalLinkTypes,
    String[] docIDHashes, String[] docIDs, boolean overrideSchedule,
    int hopcountMethod, IPriorityCalculator[] documentPriorities,
    String[][] prereqEventNames)
    throws ManifoldCFException;

  /** Add an initial set of remaining documents to the queue.
  * This method is called during job startup, when the queue is being loaded, to list documents that
  * were NOT included by calling addDocumentsInitial().  Documents listed here are simply designed to
  * enable the framework to get rid of old, invalid seeds.  They are not queued for processing.
  *@param processID is the current process ID.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the hash values of the local document identifiers.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  */
  public void addRemainingDocumentsInitial(String processID,
    Long jobID, String[] legalLinkTypes,
    String[] docIDHashes,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Signal that a seeding pass has been done.
  * Call this method at the end of a seeding pass.  It is used to perform the bookkeeping necessary to
  * maintain the hopcount table.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param isPartial is set if the seeds provided are only a partial list.  Some connectors cannot
  *       supply a full list of seeds on every seeding iteration; this acknowledges that limitation.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  */
  public void doneDocumentsInitial(Long jobID, String[] legalLinkTypes, boolean isPartial,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Begin an event sequence.
  *@param processID is the current process ID.
  *@param eventName is the name of the event.
  *@return true if the event could be created, or false if it's already there.
  */
  public boolean beginEventSequence(String processID, String eventName)
    throws ManifoldCFException;

  /** Complete an event sequence.
  *@param eventName is the name of the event.
  */
  public void completeEventSequence(String eventName)
    throws ManifoldCFException;

  /** Get the specified hop counts, with the limit as described.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes is the set of document hashes to find the hopcount for.
  *@param linkType is the kind of link to find the hopcount for.
  *@param limit is the limit, beyond which a negative distance may be returned.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return a vector of booleans corresponding to the documents requested.  A true value is returned
  * if the document is within the specified limit, false otherwise.
  */
  public boolean[] findHopCounts(Long jobID, String[] legalLinkTypes, String[] docIDHashes, String linkType, int limit,
    int hopcountMethod)
    throws ManifoldCFException;

  /** Get all the current seeds.
  * Returns the seed document identifiers for a job.
  *@param jobID is the job identifier.
  *@return the document identifier hashes that are currently considered to be seeds.
  */
  public String[] getAllSeeds(Long jobID)
    throws ManifoldCFException;

  /** Add a document to the queue.
  * This method is called during document processing, when a document reference is discovered.
  * The document reference is passed to this method, which updates the status of the document
  * in the specified job's queue, according to specific state rules.
  *@param processID is the current process ID.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHash is the local document identifier hash value.
  *@param parentIdentifierHash is the optional parent identifier hash value for this document.  Pass null if none.
  *       MUST be present in the case of carrydown information.
  *@param relationshipType is the optional link type between this document and its parent.  Pass null if there
  *       is no relationship with a parent.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  *@param dataNames are the names of the data to carry down to the child from this parent.
  *@param dataValues are the values to carry down to the child from this parent, corresponding to dataNames above.  If CharacterInput objects are passed in here,
  *       it is the caller's responsibility to clean these up.
  *@param priority is the desired document priority for the document.
  *@param prereqEventNames are the events that must be completed before the document can be processed.
  */
  public void addDocument(String processID,
    Long jobID, String[] legalLinkTypes,
    String docIDHash, String docID,
    String parentIdentifierHash,
    String relationshipType,
    int hopcountMethod, String[] dataNames, Object[][] dataValues,
    IPriorityCalculator priority, String[] prereqEventNames)
    throws ManifoldCFException;

  /** Add documents to the queue in bulk.
  * This method is called during document processing, when a set of document references are discovered.
  * The document references are passed to this method, which updates the status of the document(s)
  * in the specified job's queue, according to specific state rules.
  *@param processID is the current process ID.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param docIDHashes are the hashes of the local document identifiers.
  *@param docIDs are the local document identifiers.
  *@param parentIdentifierHash is the optional parent identifier hash of these documents.  Pass null if none.
  *       MUST be present in the case of carrydown information.
  *@param relationshipType is the optional link type between this document and its parent.  Pass null if there
  *       is no relationship with a parent.
  *@param hopcountMethod is either accurate, nodelete, or neverdelete.
  *@param dataNames are the names of the data to carry down to the child from this parent.
  *@param dataValues are the values to carry down to the child from this parent, corresponding to dataNames above.  If CharacterInput objects are passed in here,
  *       it is the caller's responsibility to clean these up.
  *@param priorities are the desired document priorities for the documents.
  *@param prereqEventNames are the events that must be completed before each document can be processed.
  */
  public void addDocuments(String processID,
    Long jobID, String[] legalLinkTypes,
    String[] docIDHashes, String[] docIDs,
    String parentIdentifierHash,
    String relationshipType,
    int hopcountMethod, String[][] dataNames, Object[][][] dataValues,
    IPriorityCalculator[] priorities,
    String[][] prereqEventNames)
    throws ManifoldCFException;

  /** Complete adding child documents to the queue, for a set of documents.
  * This method is called at the end of document processing, to help the hopcount tracking engine do its bookkeeping.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param parentIdentifierHashes are the hashes of the document identifiers for whom child link extraction just took place.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@return the set of documents for which carrydown data was changed by this operation.  These documents are likely
  *  to be requeued as a result of the change.
  */
  public DocumentDescription[] finishDocuments(Long jobID, String[] legalLinkTypes,
    String[] parentIdentifierHashes, int hopcountMethod)
    throws ManifoldCFException;

  /** Undo the addition of child documents to the queue, for a set of documents.
  * This method is called at the end of document processing, to back out any incomplete additions to the queue, and restore
  * the status quo ante prior to the incomplete additions.  Call this method instead of finishDocuments() if the
  * addition of documents was not completed.
  *@param jobID is the job identifier.
  *@param legalLinkTypes is the set of legal link types that this connector generates.
  *@param parentIdentifierHashes are the hashes of the document identifiers for whom child link extraction just took place.
  */
  public void revertDocuments(Long jobID, String[] legalLinkTypes,
    String[] parentIdentifierHashes)
    throws ManifoldCFException;

  /** Retrieve specific parent data for a given document.
  *@param jobID is the job identifier.
  *@param docIDHash is the hash of the document identifier.
  *@param dataName is the kind of data to retrieve.
  *@return the unique data values.
  */
  public String[] retrieveParentData(Long jobID, String docIDHash, String dataName)
    throws ManifoldCFException;

  /** Retrieve specific parent data for a given document.
  *@param jobID is the job identifier.
  *@param docIDHash is the document identifier hash value.
  *@param dataName is the kind of data to retrieve.
  *@return the unique data values.
  */
  public CharacterInput[] retrieveParentDataAsFiles(Long jobID, String docIDHash, String dataName)
    throws ManifoldCFException;

  // These methods support the job threads (which start jobs and end jobs)
  // There is one thread that starts jobs.  It simply looks for jobs which are ready to
  // start, and changes their state accordingly.
  // There is also a pool of threads that end jobs.  These threads wait for a job that
  // looks like it is done, and do completion processing if it is.

  /** Manually start a job.  The specified job will be run REGARDLESS of the timed windows, and
  * will not cease until complete.  If the job is already running, this operation will assure that
  * the job does not pause when its window ends.  The job can be manually paused, or manually aborted.
  *@param jobID is the ID of the job to start.
  *@param requestMinimum is true if a minimal job run is requested.
  */
  public void manualStart(Long jobID, boolean requestMinimum)
    throws ManifoldCFException;

  /** Manually start a job.  The specified job will be run REGARDLESS of the timed windows, and
  * will not cease until complete.  If the job is already running, this operation will assure that
  * the job does not pause when its window ends.  The job can be manually paused, or manually aborted.
  *@param jobID is the ID of the job to start.
  */
  public void manualStart(Long jobID)
    throws ManifoldCFException;

  /** Manually abort a running job.  The job will be permanently stopped, and will not run again until
  * automatically started based on schedule, or manually started.
  *@param jobID is the job to abort.
  */
  public void manualAbort(Long jobID)
    throws ManifoldCFException;

  /** Manually restart a running job.  The job will be stopped and restarted.  Any schedule affinity will be lost,
  * until the job finishes on its own.
  *@param jobID is the job to abort.
  *@param requestMinimum is true if a minimal job run is requested.
  */
  public void manualAbortRestart(Long jobID, boolean requestMinimum)
    throws ManifoldCFException;

  /** Manually restart a running job.  The job will be stopped and restarted.  Any schedule affinity will be lost,
  * until the job finishes on its own.
  *@param jobID is the job to abort.
  */
  public void manualAbortRestart(Long jobID)
    throws ManifoldCFException;

  /** Pause a job.
  *@param jobID is the job identifier to pause.
  */
  public void pauseJob(Long jobID)
    throws ManifoldCFException;

  /** Restart a paused job.
  *@param jobID is the job identifier to restart.
  */
  public void restartJob(Long jobID)
    throws ManifoldCFException;

  /** Reset job schedule.  This re-evaluates whether the job should be started now.  This method would typically
  * be called after a job's scheduling window has been changed.
  *@param jobID is the job identifier.
  */
  public void resetJobSchedule(Long jobID)
    throws ManifoldCFException;

  // These methods are called by automatic processes

  /** Start jobs based on schedule.
  * This method marks all the appropriate jobs as "in progress", which is all that should be
  * needed to start them.
  *@param currentTime is the current time in milliseconds since epoch.
  *@param unwaitList is filled in with the set of job id's that were resumed (Long's).
  */
  public void startJobs(long currentTime, List<Long> unwaitList)
    throws ManifoldCFException;


  /** Put active or paused jobs in wait state, if they've exceeded their window.
  *@param currentTime is the current time in milliseconds since epoch.
  *@param waitList is filled in with the set of job id's that were put into a wait state (Long's).
  */
  public void waitJobs(long currentTime, List<Long> waitList)
    throws ManifoldCFException;

  /** Get the list of jobs that are ready for seeding.
  *@param processID is the current process ID.
  *@param currentTime is the current time in milliseconds since epoch.
  *@return jobs that are active and are running in adaptive mode.  These will be seeded
  * based on what the connector says should be added to the queue.
  */
  public JobSeedingRecord[] getJobsReadyForSeeding(String processID, long currentTime)
    throws ManifoldCFException;

  /** Reset a seeding job back to "active" state.
  *@param jobID is the job id.
  */
  public void resetSeedJob(Long jobID)
    throws ManifoldCFException;

  /** Get the list of jobs that are ready for delete cleanup.
  *@param processID is the current process ID.
  *@return jobs that were in the "readyfordelete" state.
  */
  public JobDeleteRecord[] getJobsReadyForDeleteCleanup(String processID)
    throws ManifoldCFException;
    
  /** Get the list of jobs that are ready for startup.
  *@param processID is the current process ID.
  *@return jobs that were in the "readyforstartup" state.  These will be marked as being in the "starting up" state.
  */
  public JobStartRecord[] getJobsReadyForStartup(String processID)
    throws ManifoldCFException;

  /** Find the list of jobs that need to have their connectors notified of job completion.
  *@param processID is the current process ID.
  *@return the ID's of jobs that need their output connectors notified in order to become inactive.
  */
  public JobNotifyRecord[] getJobsReadyForInactivity(String processID)
    throws ManifoldCFException;

  /** Find the list of jobs that need to have their connectors notified of job deletion.
  *@param processID is the process ID.
  *@return the ID's of jobs that need their output connectors notified in order to be removed.
  */
  public JobNotifyRecord[] getJobsReadyForDelete(String processID)
    throws ManifoldCFException;

  /** Inactivate a job, from the notification state.
  *@param jobID is the ID of the job to inactivate.
  */
  public void inactivateJob(Long jobID)
    throws ManifoldCFException;

  /** Remove a job, from the notification state.
  *@param jobID is the ID of the job to remove.
  */
  public void removeJob(Long jobID)
    throws ManifoldCFException;

  /** Reset a job starting for delete back to "ready for delete"
  * state.
  *@param jobID is the job id.
  */
  public void resetStartDeleteJob(Long jobID)
    throws ManifoldCFException;

  /** Reset a job that is notifying back to "ready for notify"
  * state.
  *@param jobID is the job id.
  */
  public void resetNotifyJob(Long jobID)
    throws ManifoldCFException;

  /** Reset a job that is delete notifying back to "ready for delete notify"
  * state.
  *@param jobID is the job id.
  */
  public void resetDeleteNotifyJob(Long jobID)
    throws ManifoldCFException;

  /** Reset a starting job back to "ready for startup" state.
  *@param jobID is the job id.
  */
  public void resetStartupJob(Long jobID)
    throws ManifoldCFException;

  /** Prepare for a delete scan.
  *@param jobID is the job id.
  */
  public void prepareDeleteScan(Long jobID)
    throws ManifoldCFException;

  /** Prepare a job to be run.
  * This method is called regardless of the details of the job; what differs is only the flags that are passed in.
  * The code inside will determine the appropriate procedures.
  * (This method replaces prepareFullScan() and prepareIncrementalScan(). )
  *@param jobID is the job id.
  *@param legalLinkTypes are the link types allowed for the job.
  *@param hopcountMethod describes how to handle deletions for hopcount purposes.
  *@param connectorModel is the model used by the connector for the job.
  *@param continuousJob is true if the job is a continuous one.
  *@param fromBeginningOfTime is true if the job is running starting from time 0.
  *@param requestMinimum is true if the minimal amount of work is requested for the job run.
  */
  public void prepareJobScan(Long jobID, String[] legalLinkTypes, int hopcountMethod,
    int connectorModel, boolean continuousJob, boolean fromBeginningOfTime,
    boolean requestMinimum)
    throws ManifoldCFException;
  
  /** Note job delete started.
  *@param jobID is the job id.
  *@param startTime is the job start time.
  */
  public void noteJobDeleteStarted(Long jobID, long startTime)
    throws ManifoldCFException;

  /** Note job started.
  *@param jobID is the job id.
  *@param startTime is the job start time.
  *@param seedingVersion is the seeding version to record with the job start.
  */
  public void noteJobStarted(Long jobID, long startTime, String seedingVersion)
    throws ManifoldCFException;

  /** Note job seeded.
  *@param jobID is the job id.
  *@param seedingVersion is the seeding version string to record.
  */
  public void noteJobSeeded(Long jobID, String seedingVersion)
    throws ManifoldCFException;

  /**  Note the deregistration of a connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of a connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException;

  /**  Note the deregistration of a notification connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteNotificationConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of a notification connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteNotificationConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note a change in connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external repository change
  * is signalled.
  */
  public void noteConnectionChange(String connectionName)
    throws ManifoldCFException;

  /** Note a change in notification connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external repository change
  * is signalled.
  */
  public void noteNotificationConnectionChange(String connectionName)
    throws ManifoldCFException;
    
  /**  Note the deregistration of an output connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteOutputConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of an output connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteOutputConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note a change in output connection configuration.
  * This method will be called whenever a connection's configuration is modified, or when an external output target change
  * is signalled.
  */
  public void noteOutputConnectionChange(String connectionName)
    throws ManifoldCFException;

  /**  Note the deregistration of a transformation connector used by the specified connections.
  * This method will be called when the connector is deregistered.  Jobs that use these connections
  *  must therefore enter appropriate states.
  *@param connectionNames is the set of connection names.
  */
  public void noteTransformationConnectorDeregistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note the registration of a transformation connector used by the specified connections.
  * This method will be called when a connector is registered, on which the specified
  * connections depend.
  *@param connectionNames is the set of connection names.
  */
  public void noteTransformationConnectorRegistration(String[] connectionNames)
    throws ManifoldCFException;

  /** Note a change in transformation connection configuration.
  * This method will be called whenever a connection's configuration is modified.
  */
  public void noteTransformationConnectionChange(String connectionName)
    throws ManifoldCFException;

  /** Assess jobs marked to be in need of assessment for connector status changes.
  */
  public void assessMarkedJobs()
    throws ManifoldCFException;

  /** Delete jobs in need of being deleted (which are marked "ready for delete").
  * This method is meant to be called periodically to perform delete processing on jobs.
  */
  public void deleteJobsReadyForDelete()
    throws ManifoldCFException;

  /** Get list of deletable document descriptions.  This list will take into account
  * multiple jobs that may own the same document.
  *@param processID is the current process ID.
  *@param n is the maximum number of documents to return.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@return the document descriptions for these documents.
  */
  public DocumentDescription[] getNextDeletableDocuments(String processID,
    int n, long currentTime)
    throws ManifoldCFException;

  /** Get list of cleanable document descriptions.  This list will take into account
  * multiple jobs that may own the same document.
  *@param processID is the current process ID.
  *@param n is the maximum number of documents to return.
  *@param currentTime is the current time; some fetches do not occur until a specific time.
  *@return the document descriptions for these documents.
  */
  public DocumentSetAndFlags getNextCleanableDocuments(String processID,
    int n, long currentTime)
    throws ManifoldCFException;

  /** Delete ingested document identifiers (as part of deleting the owning job).
  * The number of identifiers specified is guaranteed to be less than the maxInClauseCount
  * for the database.
  *@param identifiers is the set of document identifiers.
  */
  public void deleteIngestedDocumentIdentifiers(DocumentDescription[] identifiers)
    throws ManifoldCFException;

  /** Abort a running job due to a fatal error condition.
  *@param jobID is the job to abort.
  *@param errorText is the error text.
  *@return true if this is the first abort for the job.
  */
  public boolean errorAbort(Long jobID, String errorText)
    throws ManifoldCFException;

  /** Complete the sequence that stops jobs, either for abort, pause, or because of a scheduling
  * window.  The logic will move the job to its next state (INACTIVE, PAUSED, ACTIVEWAIT),
  * and will record the jobs that have been so modified.
  *@param timestamp is the current time in milliseconds since epoch.
  *@param modifiedJobs is filled in with the set of IJobDescription objects that were stopped.
  *@param stopNotificationTypes is filled in with the type of stop notification.
  */
  public void finishJobStops(long timestamp, List<IJobDescription> modifiedJobs, List<Integer> stopNotificationTypes)
    throws ManifoldCFException;

  /** Complete the sequence that resumes jobs, either from a pause or from a scheduling window
  * wait.  The logic will restore the job to an active state (many possibilities depending on
  * connector status), and will record the jobs that have been so modified.
  *@param timestamp is the current time in milliseconds since epoch.
  *@param modifiedJobs is filled in with the set of IJobDescription objects that were resumed.
  */
  public void finishJobResumes(long timestamp, List<IJobDescription> modifiedJobs)
    throws ManifoldCFException;
    
  /** Put all eligible jobs in the "shutting down" state.
  */
  public void finishJobs()
    throws ManifoldCFException;

  /** Reset eligible jobs either back to the "inactive" state, or make them active again.  The
  * latter will occur if the cleanup phase of the job generated more pending documents.
  *
  *  This method is used to pick up all jobs in the shutting down state
  * whose purgatory or being-cleaned records have been all processed.
  *
  *@param currentTime is the current time in milliseconds since epoch.
  *@param resetJobs is filled in with the set of IJobDescription objects that were reset.
  */
  public void resetJobs(long currentTime, List<IJobDescription> resetJobs)
    throws ManifoldCFException;


  // Status reports

  /** Get the status of a job.
  *@param jobID is the job ID.
  *@return the status object for the specified job.
  */
  public JobStatus getStatus(Long jobID)
    throws ManifoldCFException;

  /** Get a list of all jobs, and their status information.
  *@return an ordered array of job status objects.
  */
  public JobStatus[] getAllStatus()
    throws ManifoldCFException;

  /** Get a list of running jobs.  This is for status reporting.
  *@return an array of the job status objects.
  */
  public JobStatus[] getRunningJobs()
    throws ManifoldCFException;

  /** Get a list of completed jobs, and their statistics.
  *@return an array of the job status objects.
  */
  public JobStatus[] getFinishedJobs()
    throws ManifoldCFException;

  /** Get the status of a job.
  *@param jobID is the job ID.
  *@param includeCounts is true if document counts should be included.
  *@return the status object for the specified job.
  */
  public JobStatus getStatus(Long jobID, boolean includeCounts)
    throws ManifoldCFException;

  /** Get a list of all jobs, and their status information.
  *@param includeCounts is true if document counts should be included.
  *@return an ordered array of job status objects.
  */
  public JobStatus[] getAllStatus(boolean includeCounts)
    throws ManifoldCFException;

  /** Get a list of running jobs.  This is for status reporting.
  *@param includeCounts is true if document counts should be included.
  *@return an array of the job status objects.
  */
  public JobStatus[] getRunningJobs(boolean includeCounts)
    throws ManifoldCFException;

  /** Get a list of completed jobs, and their statistics.
  *@param includeCounts is true if document counts should be included.
  *@return an array of the job status objects.
  */
  public JobStatus[] getFinishedJobs(boolean includeCounts)
    throws ManifoldCFException;

  /** Get the status of a job.
  *@param jobID is the job ID.
  *@param includeCounts is true if document counts should be included.
  *@param maxCount is the maximum number of documents we want to count for each status.
  *@return the status object for the specified job.
  */
  public JobStatus getStatus(Long jobID, boolean includeCounts, int maxCount)
    throws ManifoldCFException;

  /** Get a list of all jobs, and their status information.
  *@param includeCounts is true if document counts should be included.
  *@param maxCount is the maximum number of documents we want to count for each status.
  *@return an ordered array of job status objects.
  */
  public JobStatus[] getAllStatus(boolean includeCounts, int maxCount)
    throws ManifoldCFException;

  /** Get a list of running jobs.  This is for status reporting.
  *@param includeCounts is true if document counts should be included.
  *@param maxCount is the maximum number of documents we want to count for each status.
  *@return an array of the job status objects.
  */
  public JobStatus[] getRunningJobs(boolean includeCounts, int maxCount)
    throws ManifoldCFException;

  /** Get a list of completed jobs, and their statistics.
  *@param includeCounts is true if document counts should be included.
  *@param maxCount is the maximum number of documents we want to count for each status.
  *@return an array of the job status objects.
  */
  public JobStatus[] getFinishedJobs(boolean includeCounts, int maxCount)
    throws ManifoldCFException;

  // The following commands generate reports based on the queue.

  /** Run a 'document status' report.
  *@param connectionName is the name of the connection.
  *@param filterCriteria are the criteria used to limit the records considered for the report.
  *@param sortOrder is the specified sort order of the final report.
  *@param startRow is the first row to include.
  *@param rowCount is the number of rows to include.
  *@return the results, with the following columns: identifier, job, state, status, scheduled, action, retrycount, retrylimit.  The "scheduled" column and the
  * "retrylimit" column are long values representing a time; all other values will be user-friendly strings.
  */
  public IResultSet genDocumentStatus(String connectionName, StatusFilterCriteria filterCriteria, SortOrder sortOrder,
    int startRow, int rowCount)
    throws ManifoldCFException;

  /** Run a 'queue status' report.
  *@param connectionName is the name of the connection.
  *@param filterCriteria are the criteria used to limit the records considered for the report.
  *@param sortOrder is the specified sort order of the final report.
  *@param idBucketDescription is the bucket description for generating the identifier class.
  *@param startRow is the first row to include.
  *@param rowCount is the number of rows to include.
  *@return the results, with the following columns: idbucket, inactive, processing, expiring, deleting,
  processready, expireready, processwaiting, expirewaiting
  */
  public IResultSet genQueueStatus(String connectionName, StatusFilterCriteria filterCriteria, SortOrder sortOrder,
    BucketDescription idBucketDescription, int startRow, int rowCount)
    throws ManifoldCFException;
}
