/* $Id: IProcessActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import java.io.*;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that a connector's processDocuments() method can do.
* The processing flow for a document is expected to go something like this:
* (1) The connector's processDocuments() method is called with a set of documents to be processed.
* (2) The connector computes a version string for each document in the set as part of determining
*    whether the document indeed needs to be refetched.
* (3) For each document processed, there can be one of several dispositions:
*   (a) There is no such document (anymore): deleteDocument() called for the document.
*   (b) The document is (re)indexed: ingestDocumentWithException() is called for the document.
*   (c) The document is determined to be unchanged and no updates are needed: nothing needs to be called
*     for the document.
*   (d) The document is determined to be unchanged BUT the version string needs to be updated: recordDocument()
*     is called for the document.
*   (e) The document is determined to be unindexable BUT it still exists in the repository: noDocument()
*    is called for the document.
*   (f) There was a service interruption: ServiceInterruption is thrown.
* (4) In order to determine whether a document needs to be reindexed, the method checkDocumentNeedsReindexing()
*    is available to return an opinion on that matter.
*/
public interface IProcessActivity extends IHistoryActivity, IEventActivity, IAbortActivity, IFingerprintActivity, ICarrydownActivity
{
  public static final String _rcsid = "@(#)$Id: IProcessActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Check if a document needs to be reindexed, based on a computed version string.
  * Call this method to determine whether reindexing is necessary.  Pass in a newly-computed version
  * string.  This method will return "true" if the document needs to be re-indexed.
  *@param documentIdentifier is the document identifier.
  *@param newVersionString is the newly-computed version string.
  *@return true if the document needs to be reindexed.
  */
  public boolean checkDocumentNeedsReindexing(String documentIdentifier,
    String newVersionString)
    throws ManifoldCFException;

  /** Check if a document needs to be reindexed, based on a computed version string.
  * Call this method to determine whether reindexing is necessary.  Pass in a newly-computed version
  * string.  This method will return "true" if the document needs to be re-indexed.
  *@param documentIdentifier is the document identifier.
  *@param componentIdentifier is the component document identifier, if any.
  *@param newVersionString is the newly-computed version string.
  *@return true if the document needs to be reindexed.
  */
  public boolean checkDocumentNeedsReindexing(String documentIdentifier,
    String componentIdentifier,
    String newVersionString)
    throws ManifoldCFException;

  /** Add a document description to the current job's queue.
  *@param documentIdentifier is the local document identifier to add (for the connector that
  * fetched the document).
  *@param parentIdentifier is the document identifier that is considered to be the "parent"
  * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
  * MUST be present in the case of carrydown information.
  *@param relationshipType is the string describing the kind of relationship described by this
  * reference.  This must be one of the strings returned by the IRepositoryConnector method
  * "getRelationshipTypes()".  May be null.
  *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
  *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
  *          The type of each object must either be a String, or a CharacterInput.
  *@param originationTime is the time, in ms since epoch, that the document originated.  Pass null if none or unknown.
  *@param prereqEventNames are the names of the prerequisite events which this document requires prior to processing.  Pass null if none.
  */
  public void addDocumentReference(String documentIdentifier, String parentIdentifier, String relationshipType,
    String[] dataNames, Object[][] dataValues, Long originationTime, String[] prereqEventNames)
    throws ManifoldCFException;

  /** Add a document description to the current job's queue.
  *@param documentIdentifier is the document identifier to add (for the connector that
  * fetched the document).
  *@param parentIdentifier is the document identifier that is considered to be the "parent"
  * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
  * MUST be present in the case of carrydown information.
  *@param relationshipType is the string describing the kind of relationship described by this
  * reference.  This must be one of the strings returned by the IRepositoryConnector method
  * "getRelationshipTypes()".  May be null.
  *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
  *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
  *          The type of each object must either be a String, or a CharacterInput.
  *@param originationTime is the time, in ms since epoch, that the document originated.  Pass null if none or unknown.
  */
  public void addDocumentReference(String documentIdentifier, String parentIdentifier, String relationshipType,
    String[] dataNames, Object[][] dataValues, Long originationTime)
    throws ManifoldCFException;

  /** Add a document description to the current job's queue.
  *@param documentIdentifier is the document identifier to add (for the connector that
  * fetched the document).
  *@param parentIdentifier is the document identifier that is considered to be the "parent"
  * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
  * MUST be present in the case of carrydown information.
  *@param relationshipType is the string describing the kind of relationship described by this
  * reference.  This must be one of the strings returned by the IRepositoryConnector method
  * "getRelationshipTypes()".  May be null.
  *@param dataNames is the list of carry-down data from the parent to the child.  May be null.  Each name is limited to 255 characters!
  *@param dataValues are the values that correspond to the data names in the dataNames parameter.  May be null only if dataNames is null.
  *          The type of each object must either be a String, or a CharacterInput.
  */
  public void addDocumentReference(String documentIdentifier, String parentIdentifier, String relationshipType,
    String[] dataNames, Object[][] dataValues)
    throws ManifoldCFException;

  /** Add a document description to the current job's queue.
  *@param documentIdentifier is the document identifier to add (for the connector that
  * fetched the document).
  *@param parentIdentifier is the document identifier that is considered to be the "parent"
  * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
  *@param relationshipType is the string describing the kind of relationship described by this
  * reference.  This must be one of the strings returned by the IRepositoryConnector method
  * "getRelationshipTypes()".  May be null.
  */
  public void addDocumentReference(String documentIdentifier, String parentIdentifier, String relationshipType)
    throws ManifoldCFException;

  /** Add a document description to the current job's queue.  This method is equivalent to
  * addDocumentReference(localIdentifier,null,null).
  *@param documentIdentifier is the document identifier to add (for the connector that
  * fetched the document).
  */
  public void addDocumentReference(String documentIdentifier)
    throws ManifoldCFException;

  /** Ingest the current document.
  *@param documentIdentifier is the document's identifier.
  *@param version is the version of the document, as reported by the getDocumentVersions() method of the
  *       corresponding repository connector.  An empty version string signals that there is no calculable
  *       document version string, and that the document should always be indexed.
  *@param documentURI is the URI to use to retrieve this document from the search interface (and is
  *       also the unique key in the index).
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@throws IOException only when data stream reading fails.
  */
  public void ingestDocumentWithException(String documentIdentifier,
    String version, String documentURI, RepositoryDocument data)
    throws ManifoldCFException, ServiceInterruption, IOException;

  /** Ingest the current document.
  *@param documentIdentifier is the document's identifier.
  *@param componentIdentifier is the component document identifier, if any.
  *@param version is the version of the document, as reported by the getDocumentVersions() method of the
  *       corresponding repository connector.
  *@param documentURI is the URI to use to retrieve this document from the search interface (and is
  *       also the unique key in the index).
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@throws IOException only when data stream reading fails.
  */
  public void ingestDocumentWithException(String documentIdentifier,
    String componentIdentifier,
    String version, String documentURI, RepositoryDocument data)
    throws ManifoldCFException, ServiceInterruption, IOException;

  /** Remove the specified document from the search engine index, and update the
  * recorded version information for the document.
  *@param documentIdentifier is the document's local identifier.
  *@param version is the version string to be recorded for the document.
  */
  public void noDocument(String documentIdentifier,
    String version)
    throws ManifoldCFException, ServiceInterruption;

  /** Remove the specified document from the search engine index, and update the
  * recorded version information for the document.
  *@param documentIdentifier is the document's local identifier.
  *@param componentIdentifier is the component document identifier, if any.
  *@param version is the version string to be recorded for the document.
  */
  public void noDocument(String documentIdentifier,
    String componentIdentifier,
    String version)
    throws ManifoldCFException, ServiceInterruption;

  /** Remove the specified document primary component permanently from the search engine index,
  * and from the status table.  Use this method when your document has components and
  * now also has a primary document, but will not have a primary document again for the foreseeable
  * future.  This is a rare situation.
  *@param documentIdentifier is the document's identifier.
  */
  public void removeDocument(String documentIdentifier)
    throws ManifoldCFException, ServiceInterruption;

  /** Retain existing document component.  Use this method to signal that an already-existing
  * document component does not need to be reindexed.  The default behavior is to remove
  * components that are not mentioned during processing.
  *@param documentIdentifier is the document's identifier.
  *@param componentIdentifier is the component document identifier, which cannot be null.
  */
  public void retainDocument(String documentIdentifier,
    String componentIdentifier)
    throws ManifoldCFException;

  /** Retain all existing document components of a primary document.  Use this method to signal that
  * no document components need to be reindexed.  The default behavior is to remove
  * components that are not mentioned during processing.
  *@param documentIdentifier is the document's identifier.
  */
  public void retainAllComponentDocument(String documentIdentifier)
    throws ManifoldCFException;

  /** Record a document version, WITHOUT reindexing it, or removing it.  (Other
  * documents with the same URL, however, will still be removed.)  This is
  * useful if the version string changes but the document contents are known not
  * to have changed.
  *@param documentIdentifier is the document identifier.
  *@param version is the document version.
  */
  public void recordDocument(String documentIdentifier,
    String version)
    throws ManifoldCFException;

  /** Record a document version, WITHOUT reindexing it, or removing it.  (Other
  * documents with the same URL, however, will still be removed.)  This is
  * useful if the version string changes but the document contents are known not
  * to have changed.
  *@param documentIdentifier is the document identifier.
  *@param componentIdentifier is the component document identifier, if any.
  *@param version is the document version.
  */
  public void recordDocument(String documentIdentifier,
    String componentIdentifier,
    String version)
    throws ManifoldCFException;

  /** Delete the specified document permanently from the search engine index, and from the status table,
  * along with all its components.
  * This method does NOT keep track of any document version information for the document and thus can
  * lead to "churn", whereby the same document is queued, processed,
  * and removed on subsequent crawls.  It is therefore preferable to use noDocument() instead,
  * in any case where the same decision will need to be made over and over.
  *@param documentIdentifier is the document's identifier.
  */
  public void deleteDocument(String documentIdentifier)
    throws ManifoldCFException;

  /** Override the schedule for the next time a document is crawled.
  * Calling this method allows you to set an upper recrawl bound, lower recrawl bound, upper expire bound, lower expire bound,
  * or a combination of these, on a specific document.  This method is only effective if the job is a continuous one, and if the
  * identifier you pass in is being processed.
  *@param documentIdentifier is the document's identifier.
  *@param lowerRecrawlBoundTime is the time in ms since epoch that the reschedule time should not fall BELOW, or null if none.
  *@param upperRecrawlBoundTime is the time in ms since epoch that the reschedule time should not rise ABOVE, or null if none.
  *@param lowerExpireBoundTime is the time in ms since epoch that the expire time should not fall BELOW, or null if none.
  *@param upperExpireBoundTime is the time in ms since epoch that the expire time should not rise ABOVE, or null if none.
  */
  public void setDocumentScheduleBounds(String documentIdentifier,
    Long lowerRecrawlBoundTime, Long upperRecrawlBoundTime,
    Long lowerExpireBoundTime, Long upperExpireBoundTime)
    throws ManifoldCFException;

  /** Override a document's origination time.
  * Use this method to signal the framework that a document's origination time is something other than the first time it was crawled.
  *@param documentIdentifier is the document's identifier.
  *@param originationTime is the document's origination time, or null if unknown.
  */
  public void setDocumentOriginationTime(String documentIdentifier,
    Long originationTime)
    throws ManifoldCFException;

}
