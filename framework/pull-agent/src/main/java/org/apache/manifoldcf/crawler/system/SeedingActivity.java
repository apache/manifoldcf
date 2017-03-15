/* $Id: SeedingActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents the things you can do with the framework while
* seeding.
*/
public class SeedingActivity implements ISeedingActivity
{
  public static final String _rcsid = "@(#)$Id: SeedingActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  // This is the maximum number of documents passed to the queue at one time.
  protected static final int MAX_COUNT = 100;

  // Variables
  protected final String processID;
  protected final String connectionName;
  protected final IRepositoryConnectionManager connManager;
  protected final IJobManager jobManager;
  protected final IReprioritizationTracker rt;
  protected final IRepositoryConnection connection;
  protected final IRepositoryConnector connector;
  protected final Long jobID;
  protected final String[] legalLinkTypes;
  protected final boolean overrideSchedule;
  protected final int hopcountMethod;
  
  protected final String[] documentHashList = new String[MAX_COUNT];
  protected final String[] documentList = new String[MAX_COUNT];
  protected final String[][] documentPrereqList = new String[MAX_COUNT][];
  protected int documentCount = 0;
  protected final String[] remainingDocumentHashList = new String[MAX_COUNT];
  protected int remainingDocumentCount = 0;

  /** Constructor.
  */
  public SeedingActivity(String connectionName, IRepositoryConnectionManager connManager,
    IJobManager jobManager,
    IReprioritizationTracker rt, IRepositoryConnection connection, IRepositoryConnector connector,
    Long jobID, String[] legalLinkTypes, boolean overrideSchedule, int hopcountMethod, String processID)
  {
    this.processID = processID;
    this.connectionName = connectionName;
    this.connManager = connManager;
    this.jobManager = jobManager;
    this.rt = rt;
    this.connection = connection;
    this.connector = connector;
    this.jobID = jobID;
    this.legalLinkTypes = legalLinkTypes;
    this.overrideSchedule = overrideSchedule;
    this.hopcountMethod = hopcountMethod;
  }

  /** Record a "seed" document identifier.
  * Seeds passed to this method will be loaded into the job's queue at the beginning of the
  * job's execution, and for continuous crawling jobs, periodically throughout the crawl.
  *
  * All documents passed to this method are placed on the "pending documents" list, and are marked as being seed
  * documents.  All pending documents will be processed to determine if they have changed or have been deleted.
  * It is not a big problem if the connector chooses to put more documents onto the pending list than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *
  *@param documentIdentifier is the identifier of the document to add to the "pending" queue.
  *@param prereqEventNames is the list of prerequisite events required for this document, or null if none.
  */
  public void addSeedDocument(String documentIdentifier, String[] prereqEventNames)
    throws ManifoldCFException
  {
    if (documentCount == MAX_COUNT)
    {
      // Prioritize and write the seed documents.
      writeSeedDocuments(documentHashList,documentList,documentPrereqList);
      documentCount = 0;
    }
    documentHashList[documentCount] = ManifoldCF.hash(documentIdentifier);
    documentList[documentCount] = documentIdentifier;
    if (prereqEventNames != null)
      documentPrereqList[documentCount] = prereqEventNames;
    else
      documentPrereqList[documentCount] = null;
    documentCount++;
  }

  /** Record a "seed" document identifier.
  * Seeds passed to this method will be loaded into the job's queue at the beginning of the
  * job's execution, and for continuous crawling jobs, periodically throughout the crawl.
  *
  * All documents passed to this method are placed on the "pending documents" list, and are marked as being seed
  * documents.  All pending documents will be processed to determine if they have changed or have been deleted.
  * It is not a big problem if the connector chooses to put more documents onto the pending list than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *
  *@param documentIdentifier is the identifier of the document to add to the "pending" queue.
  */
  public void addSeedDocument(String documentIdentifier)
    throws ManifoldCFException
  {
    addSeedDocument(documentIdentifier,null);
  }

  /** This method receives document identifiers that should be considered part of the seeds, but do not need to be
  * queued for processing at this time.  (This method is used to keep the hopcount tables up to date.)  It is
  * allowed to receive more identifiers than it strictly needs to, specifically identifiers that may have also been
  * sent to the addSeedDocuments() method above.  However, the connector must constrain the identifiers
  * it sends by the document specification.
  * This method is only required to be called at all if the connector supports hopcount determination (which it
  * should signal by having more than zero legal relationship types returned by the getRelationshipTypes() method).
  *
  *@param documentIdentifier is the identifier of the document to consider as a seed, but not to put in the
  * "pending" queue.
  */
  public void addUnqueuedSeedDocument(String documentIdentifier)
    throws ManifoldCFException
  {
    if (remainingDocumentCount == MAX_COUNT)
    {
      // Flush the remaining documents
      jobManager.addRemainingDocumentsInitial(processID,jobID,legalLinkTypes,remainingDocumentHashList,hopcountMethod);
      remainingDocumentCount = 0;
    }
    remainingDocumentHashList[remainingDocumentCount++] = ManifoldCF.hash(documentIdentifier);
  }

  /** Finish a seeding pass */
  public void doneSeeding(boolean isPartial)
    throws ManifoldCFException
  {
    if (documentCount > 0)
    {
      String[] documentHashes = new String[documentCount];
      String[] documents = new String[documentCount];
      String[][] documentPrereqs = new String[documentCount][];
      int i = 0;
      while (i < documentHashes.length)
      {
        documentHashes[i] = documentHashList[i];
        documents[i] = documentList[i];
        documentPrereqs[i] = documentPrereqList[i];
        i++;
      }
      writeSeedDocuments(documentHashes,documents,documentPrereqs);
      documentCount = 0;
    }
    if (remainingDocumentCount > 0)
    {
      String[] documents = new String[remainingDocumentCount];
      int i = 0;
      while (i < documents.length)
      {
        documents[i] = remainingDocumentHashList[i];
        i++;
      }
      jobManager.addRemainingDocumentsInitial(processID,jobID,legalLinkTypes,documents,hopcountMethod);
      remainingDocumentCount = 0;
    }

    // Need to signal JobManager that seeding is done.
    jobManager.doneDocumentsInitial(jobID,legalLinkTypes,isPartial,hopcountMethod);
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
    connManager.recordHistory(connectionName,startTime,activityType,dataSize,entityIdentifier,resultCode,
      resultDescription,childIdentifiers);
  }

  /** Write specified documents after calculating their priorities */
  protected void writeSeedDocuments(String[] docIDHashes, String[] docIDs, String[][] prereqEventNames)
    throws ManifoldCFException
  {
    // First, prioritize the documents using the queue tracker
    IPriorityCalculator[] docPriorities = new IPriorityCalculator[docIDHashes.length];

    rt.clearPreloadRequests();

    for (int i = 0 ; i < docIDHashes.length ; i++)
    {
      // Calculate desired document priority based on current queuetracker status.
      String[] bins = connector.getBinNames(docIDs[i]);
      PriorityCalculator p = new PriorityCalculator(rt,connection,bins,docIDs[i]);
      docPriorities[i] = p;
      p.makePreloadRequest();
    }

    rt.preloadBinValues();

    jobManager.addDocumentsInitial(processID,
      jobID,legalLinkTypes,docIDHashes,docIDs,overrideSchedule,hopcountMethod,
      docPriorities,prereqEventNames);

  }

  /** Check whether current job is still active.
  * This method is provided to allow an individual connector that needs to wait on some long-term condition to give up waiting due to the job
  * itself being aborted.  If the connector should abort, this method will raise a properly-formed ServiceInterruption, which if thrown to the
  * caller, will signal that the current seeding activity remains incomplete and must be retried when the job is resumed.
  */
  public void checkJobStillActive()
    throws ManifoldCFException, ServiceInterruption
  {
    if (jobManager.checkJobActive(jobID) == false)
      throw new ServiceInterruption("Job no longer active",System.currentTimeMillis());
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
    return ManifoldCF.createConnectionSpecificString(connection.getName(),simpleString);
  }

  /** Create a job-based string from a simple string.
  *@param simpleString is the simple string.
  *@return a job-specific string.
  */
  public String createJobSpecificString(String simpleString)
  {
    return ManifoldCF.createJobSpecificString(jobID,simpleString);
  }

}
