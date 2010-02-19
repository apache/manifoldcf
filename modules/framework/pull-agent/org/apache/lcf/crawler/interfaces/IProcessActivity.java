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
package org.apache.lcf.crawler.interfaces;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import java.util.*;

/** This interface abstracts from the activities that a fetched document processor can do.
*/
public interface IProcessActivity extends IHistoryActivity, IEventActivity, IAbortActivity
{
        public static final String _rcsid = "@(#)$Id$";

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
        public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
                String[] dataNames, Object[][] dataValues, Long originationTime, String[] prereqEventNames)
                throws LCFException;

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
        */
        public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
                String[] dataNames, Object[][] dataValues, Long originationTime)
                throws LCFException;


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
        */
        public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType,
                String[] dataNames, Object[][] dataValues)
                throws LCFException;

        /** Add a document description to the current job's queue.
        *@param localIdentifier is the local document identifier to add (for the connector that
        * fetched the document).
        *@param parentIdentifier is the document identifier that is considered to be the "parent"
        * of this identifier.  May be null, if no hopcount filtering desired for this kind of relationship.
        *@param relationshipType is the string describing the kind of relationship described by this
        * reference.  This must be one of the strings returned by the IRepositoryConnector method
        * "getRelationshipTypes()".  May be null.
        */
        public void addDocumentReference(String localIdentifier, String parentIdentifier, String relationshipType)
                throws LCFException;

        /** Add a document description to the current job's queue.  This method is equivalent to
        * addDocumentReference(localIdentifier,null,null).
        *@param localIdentifier is the local document identifier to add (for the connector that
        * fetched the document).
        */
        public void addDocumentReference(String localIdentifier)
                throws LCFException;

        /** Retrieve data passed from parents to a specified child document.
        *@param localIdentifier is the document identifier of the document we want the recorded data for.
        *@param dataName is the name of the data items to retrieve.
        *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
        */
        public String[] retrieveParentData(String localIdentifier, String dataName)
                throws LCFException;

        /** Retrieve data passed from parents to a specified child document.
        *@param localIdentifier is the document identifier of the document we want the recorded data for.
        *@param dataName is the name of the data items to retrieve.
        *@return an array containing the unique data values passed from ALL parents.  Note that these are in no particular order, and there will not be any duplicates.
        */
        public CharacterInput[] retrieveParentDataAsFiles(String localIdentifier, String dataName)
                throws LCFException;

        /** Record a document version, but don't ingest it.
        *@param localIdentifier is the document identifier.
        *@param version is the document version.
        */
        public void recordDocument(String localIdentifier, String version)
                throws LCFException, ServiceInterruption;

        /** Ingest the current document.
        *@param localIdentifier is the document's local identifier.
        *@param version is the version of the document, as reported by the getDocumentVersions() method of the
        *       corresponding repository connector.
        *@param documentURI is the URI to use to retrieve this document from the search interface (and is
        *       also the unique key in the index).
        *@param data is the document data.  The data is closed after ingestion is complete.
        */
        public void ingestDocument(String localIdentifier, String version, String documentURI, RepositoryDocument data)
                throws LCFException, ServiceInterruption;

        /** Delete the current document from the search engine index.
        *@param localIdentifier is the document's local identifier.
        */
        public void deleteDocument(String localIdentifier)
                throws LCFException, ServiceInterruption;

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
        public void setDocumentScheduleBounds(String localIdentifier,
                Long lowerRecrawlBoundTime, Long upperRecrawlBoundTime,
                Long lowerExpireBoundTime, Long upperExpireBoundTime)
                throws LCFException;

        /** Override a document's origination time.
        * Use this method to signal the framework that a document's origination time is something other than the first time it was crawled.
        *@param localIdentifier is the document's local identifier.
        *@param originationTime is the document's origination time, or null if unknown.
        */
        public void setDocumentOriginationTime(String localIdentifier,
                Long originationTime)
                throws LCFException;

}
