/* $Id: IIncrementalIngester.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;

/** This interface describes the incremental ingestion API.
* SOME NOTES:
* The expected client flow for this API is to:
*
* 1) Use the API to fetch a document's version.
* 2) Base a decision whether to ingest based on that version.
* 3) If the decision to ingest occurs, then the ingest method in the API is
*    called.
*
* The module described by this interface is responsible for keeping track of what has been sent where, and also the corresponding version of
* each document so indexed.  The space over which this takes place is defined by the individual output connection - that is, the output connection
* seems to "remember" what documents were handed to it.
*
* A secondary purpose of this module is to provide a mapping between the key by which a document is described internally (by an
* identifier hash, plus the name of an identifier space), and the way the document is identified in the output space (by the name of an
* output connection, plus a URI which is considered local to that output connection space).
*
*/
public interface IIncrementalIngester
{
  public static final String _rcsid = "@(#)$Id: IIncrementalIngester.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Install the incremental ingestion manager.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall the incremental ingestion manager.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Flush all knowledge of what was ingested before.
  */
  public void clearAll()
    throws ManifoldCFException;


  /** Get an output version string for a document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param spec is the output specification.
  *@return the description string.
  */
  public String getOutputDescription(String outputConnectionName, OutputSpecification spec)
    throws ManifoldCFException, ServiceInterruption;

  /** Check if a mime type is indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param mimeType is the mime type to check.
  *@return true if the mimeType is indexable.
  */
  public boolean checkMimeTypeIndexable(String outputConnectionName, String outputDescription, String mimeType)
    throws ManifoldCFException, ServiceInterruption;

  /** Check if a file is indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param localFile is the local file to check.
  *@return true if the local file is indexable.
  */
  public boolean checkDocumentIndexable(String outputConnectionName, String outputDescription, File localFile)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are too long to be indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param length is the length of the document.
  *@return true if the file is indexable.
  */
  public boolean checkLengthIndexable(String outputConnectionName, String outputDescription, long length)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that not indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param url is the url of the document.
  *@return true if the file is indexable.
  */
  public boolean checkURLIndexable(String outputConnectionName, String outputDescription, String url)
    throws ManifoldCFException, ServiceInterruption;

  /** Record a document version, but don't ingest it.
  * The purpose of this method is to keep track of the frequency at which ingestion "attempts" take place.
  * ServiceInterruption is thrown if this action must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param recordTime is the time at which the recording took place, in milliseconds since epoch.
  *@param activities is the object used in case a document needs to be removed from the output index as the result of this operation.
  */
  public void documentRecord(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion, long recordTime,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Ingest a document.
  * This ingests the document, and notes it.  If this is a repeat ingestion of the document, this
  * method also REMOVES ALL OLD METADATA.  When complete, the index will contain only the metadata
  * described by the RepositoryDocument object passed to this method.
  * ServiceInterruption is thrown if the document ingestion must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param outputVersion is the output version string constructed from the output specification by the output connector.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the URI of the document, which will be used as the key of the document in the index.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  *@return true if the ingest was ok, false if the ingest is illegal (and should not be repeated).
  */
  public boolean documentIngest(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion,
    String outputVersion,
    String authorityName,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Ingest a document.
  * This ingests the document, and notes it.  If this is a repeat ingestion of the document, this
  * method also REMOVES ALL OLD METADATA.  When complete, the index will contain only the metadata
  * described by the RepositoryDocument object passed to this method.
  * ServiceInterruption is thrown if the document ingestion must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param parameterVersion is the forced parameter version.
  *@param outputVersion is the output version string constructed from the output specification by the output connector.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the URI of the document, which will be used as the key of the document in the index.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  *@return true if the ingest was ok, false if the ingest is illegal (and should not be repeated).
  */
  public boolean documentIngest(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion,
    String outputVersion,
    String parameterVersion,
    String authorityName,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes are the set of document identifier hashes.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  public void documentCheckMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes,
    long checkTime)
    throws ManifoldCFException;

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  public void documentCheck(String outputConnectionName,
    String identifierClass, String identifierHash,
    long checkTime)
    throws ManifoldCFException;

  /** Delete multiple documents from the search engine index.
  *@param outputConnectionNames are the names of the output connections associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDeleteMultiple(String[] outputConnectionNames,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Delete multiple documents from the search engine index.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDeleteMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Delete a document from the search engine index.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDelete(String outputConnectionName,
    String identifierClass, String identifierHash,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Look up ingestion data for a SET of documents.
  *@param outputConnectionNames are the names of the output connections associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  *@return the array of document data.  Null will come back for any identifier that doesn't
  * exist in the index.
  */
  public DocumentIngestStatus[] getDocumentIngestDataMultiple(String[] outputConnectionNames,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Look up ingestion data for a SET of documents.
  *@param outputConnectionName is the names of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  *@return the array of document data.  Null will come back for any identifier that doesn't
  * exist in the index.
  */
  public DocumentIngestStatus[] getDocumentIngestDataMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Look up ingestion data for a documents.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@return the current document's ingestion data, or null if the document is not currently ingested.
  */
  public DocumentIngestStatus getDocumentIngestData(String outputConnectionName,
    String identifierClass, String identifierHash)
    throws ManifoldCFException;

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the hashes of the ids of the documents.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  public long[] getDocumentUpdateIntervalMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  public long getDocumentUpdateInterval(String outputConnectionName,
    String identifierClass, String identifierHash)
    throws ManifoldCFException;

  /** Reset all documents belonging to a specific output connection, because we've got information that
  * that system has been reconfigured.  This will force all such documents to be reindexed the next time
  * they are checked.
  *@param outputConnectionName is the name of the output connection associated with this action.
  */
  public void resetOutputConnection(String outputConnectionName)
    throws ManifoldCFException;
    
  /** Remove all knowledge of an output index from the system.  This is appropriate
  * when the output index no longer exists and you wish to delete the associated job.
  *@param outputConnectionName is the name of the output connection associated with this action.
  */
  public void removeOutputConnection(String outputConnectionName)
    throws ManifoldCFException;

}
