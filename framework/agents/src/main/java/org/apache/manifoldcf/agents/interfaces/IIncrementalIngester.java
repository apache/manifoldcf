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


  /** From a pipeline specification, get the name of the output connection that will be indexed last
  * in the pipeline.
  *@param pipelineSpecificationBasic is the basic pipeline specification.
  *@return the last indexed output connection name.
  */
  public String getLastIndexedOutputConnectionName(IPipelineSpecificationBasic pipelineSpecificationBasic);

  /** From a pipeline specification, get the name of the output connection that will be indexed first
  * in the pipeline.
  *@param pipelineSpecificationBasic is the basic pipeline specification.
  *@return the first indexed output connection name.
  */
  public String getFirstIndexedOutputConnectionName(IPipelineSpecificationBasic pipelineSpecificationBasic);

  /** Get an output version string for a document.
  *@param outputConnection is the output connection associated with this action.
  *@param spec is the output specification.
  *@return the description string.
  */
  public VersionContext getOutputDescription(IOutputConnection outputConnection, Specification spec)
    throws ManifoldCFException, ServiceInterruption;

  /** Get transformation version string for a document.
  *@param transformationConnection is the transformation connection associated with this action.
  *@param spec is the transformation specification.
  *@return the description string.
  */
  public VersionContext getTransformationDescription(ITransformationConnection transformationConnection, Specification spec)
    throws ManifoldCFException, ServiceInterruption;

  /** Check if a document date is indexable.
  *@param pipelineSpecification is the IPipelineSpecification object for this pipeline.
  *@param date is the date to check
  *@param activity are the activities available to this method.
  *@return true if the document with that date is indexable.
  */
  public boolean checkDateIndexable(
    IPipelineSpecification pipelineSpecification,
    Date date,
    IOutputCheckActivity activity)
    throws ManifoldCFException, ServiceInterruption;

  /** Check if a mime type is indexable.
  *@param pipelineSpecification is the IPipelineSpecification object for this pipeline.
  *@param mimeType is the mime type to check.
  *@param activity are the activities available to this method.
  *@return true if the mimeType is indexable.
  */
  public boolean checkMimeTypeIndexable(
    IPipelineSpecification pipelineSpecification,
    String mimeType,
    IOutputCheckActivity activity)
    throws ManifoldCFException, ServiceInterruption;

  /** Check if a file is indexable.
  *@param pipelineSpecification is the IPipelineSpecification object for this pipeline.
  *@param localFile is the local file to check.
  *@param activity are the activities available to this method.
  *@return true if the local file is indexable.
  */
  public boolean checkDocumentIndexable(
    IPipelineSpecification pipelineSpecification,
    File localFile,
    IOutputCheckActivity activity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are too long to be indexable.
  *@param pipelineSpecification is the IPipelineSpecification object for this pipeline.
  *@param length is the length of the document.
  *@param activity are the activities available to this method.
  *@return true if the file is indexable.
  */
  public boolean checkLengthIndexable(
    IPipelineSpecification pipelineSpecification,
    long length,
    IOutputCheckActivity activity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that not indexable.
  *@param pipelineSpecification is the IPipelineSpecification object for this pipeline.
  *@param url is the url of the document.
  *@param activity are the activities available to this method.
  *@return true if the file is indexable.
  */
  public boolean checkURLIndexable(
    IPipelineSpecification pipelineSpecification,
    String url,
    IOutputCheckActivity activity)
    throws ManifoldCFException, ServiceInterruption;

  /** Determine whether we need to fetch or refetch a document.
  * Pass in information including the pipeline specification with existing version info, plus new document and parameter version strings.
  * If no outputs need to be updated, then this method will return false.  If any outputs need updating, then true is returned.
  *@param pipelineSpecificationWithVersions is the pipeline specification including new version info for all transformation and output
  *  connections.
  *@param newDocumentVersion is the newly-determined document version.
  *@param newAuthorityNameString is the newly-determined authority name.
  *@return true if the document needs to be refetched.
  */
  public boolean checkFetchDocument(
    IPipelineSpecificationWithVersions pipelineSpecificationWithVersions,
    String newDocumentVersion,
    String newAuthorityNameString);

  /** Record a document version, but don't ingest it.
  * The purpose of this method is to update document version information without reindexing the document.
  *@param pipelineSpecificationBasic is the basic pipeline specification needed.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param componentHash is the hashed component identifier, if any.
  *@param documentVersion is the document version.
  *@param recordTime is the time at which the recording took place, in milliseconds since epoch.
  */
  public void documentRecord(
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String identifierClass, String identifierHash, String componentHash,
    String documentVersion, long recordTime)
    throws ManifoldCFException;

  /** Remove a document from specified indexes, just as if an empty document
  * was indexed, and record the necessary version information.
  * This method is conceptually similar to documentIngest(), but does not actually take
  * a document or allow it to be transformed.  If there is a document already
  * indexed, it is removed from the index.
  *@param pipelineSpecificationWithVersions is the pipeline specification with already-fetched output versioning information.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param componentHash is the hashed component identifier, if any.
  *@param documentVersion is the document version.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param recordTime is the time at which the recording took place, in milliseconds since epoch.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  */
  public void documentNoData(
    IPipelineSpecificationWithVersions pipelineSpecificationWithVersions,
    String identifierClass, String identifierHash, String componentHash,
    String documentVersion,
    String authorityName,
    long recordTime,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Ingest a document.
  * This ingests the document, and notes it.  If this is a repeat ingestion of the document, this
  * method also REMOVES ALL OLD METADATA.  When complete, the index will contain only the metadata
  * described by the RepositoryDocument object passed to this method.
  * ServiceInterruption is thrown if the document ingestion must be rescheduled.
  *@param pipelineSpecificationWithVersions is the pipeline specification with already-fetched output versioning information.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param componentHash is the hashed component identifier, if any.
  *@param documentVersion is the document version.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the URI of the document, which will be used as the key of the document in the index.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  *@return true if the ingest was ok, false if the ingest is illegal (and should not be repeated).
  *@throws IOException only if data stream throws an IOException.
  */
  public boolean documentIngest(
    IPipelineSpecificationWithVersions pipelineSpecificationWithVersions,
    String identifierClass, String identifierHash, String componentHash,
    String documentVersion,
    String authorityName,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException;

  /** Remove a document component from the search engine index.
  *@param pipelineConnections is the pipeline specification.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@param componentHash is the hashed component identifier, if any.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentRemove(
    IPipelineConnections pipelineConnections,
    String identifierClass, String identifierHash, String componentHash,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Remove multiple document components from the search engine index.
  *@param pipelineConnections is the pipeline specification.
  *@param identifierClasses are the names of the spaces in which the identifier hash should be interpreted.
  *@param identifierHashes are the hashes of the ids of the documents.
  *@param componentHash is the hashed component identifier, if any.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentRemoveMultiple(
    IPipelineConnections pipelineConnections,
    String[] identifierClasses, String[] identifierHashes, String componentHash,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param pipelineSpecificationBasic is a pipeline specification.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes are the set of document identifier hashes.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  public void documentCheckMultiple(
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String[] identifierClasses, String[] identifierHashes,
    long checkTime)
    throws ManifoldCFException;

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param pipelineSpecificationBasic is a basic pipeline specification.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  public void documentCheck(
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String identifierClass, String identifierHash,
    long checkTime)
    throws ManifoldCFException;

  /** Delete multiple documents, and their components, from the search engine index.
  *@param pipelineConnections are the pipeline specifications associated with the documents.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDeleteMultiple(
    IPipelineConnections[] pipelineConnections,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Delete multiple documents, and their components, from the search engine index.
  *@param pipelineConnections is the pipeline specification.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDeleteMultiple(
    IPipelineConnections pipelineConnections,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Delete a document, and all its components, from the search engine index.
  *@param pipelineConnections is the pipeline specification.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  public void documentDelete(
    IPipelineConnections pipelineConnections,
    String identifierClass, String identifierHash,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  /** Look up ingestion data for a set of documents.
  *@param rval is a map of output key to document data, in no particular order, which will be loaded with all matching results.
  *@param pipelineSpecificationBasics are the pipeline specifications corresponding to the identifier classes and hashes.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  */
  public void getPipelineDocumentIngestDataMultiple(
    IngestStatuses rval,
    IPipelineSpecificationBasic[] pipelineSpecificationBasics,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Look up ingestion data for a SET of documents.
  *@param rval is a map of output key to document data, in no particular order, which will be loaded with all matching results.
  *@param pipelineSpecificationBasic is the pipeline specification for all documents.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  */
  public void getPipelineDocumentIngestDataMultiple(
    IngestStatuses rval,
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Look up ingestion data for a document.
  *@param rval is a map of output key to document data, in no particular order, which will be loaded with all matching results.
  *@param pipelineSpecificationBasic is the pipeline specification for the document.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  */
  public void getPipelineDocumentIngestData(
    IngestStatuses rval,
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String identifierClass, String identifierHash)
    throws ManifoldCFException;

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param pipelineSpecificationBasic is the basic pipeline specification.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the hashes of the ids of the documents.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  public long[] getDocumentUpdateIntervalMultiple(
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException;

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param pipelineSpecificationBasic is the basic pipeline specification.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  public long getDocumentUpdateInterval(
    IPipelineSpecificationBasic pipelineSpecificationBasic,
    String identifierClass, String identifierHash)
    throws ManifoldCFException;

  /** Reset all documents belonging to a specific output connection, because we've got information that
  * that system has been reconfigured.  This will force all such documents to be reindexed the next time
  * they are checked.
  *@param outputConnection is the output connection associated with this action.
  */
  public void resetOutputConnection(IOutputConnection outputConnection)
    throws ManifoldCFException;
    
  /** Remove all knowledge of an output index from the system.  This is appropriate
  * when the output index no longer exists and you wish to delete the associated job.
  *@param outputConnection is the output connection associated with this action.
  */
  public void removeOutputConnection(IOutputConnection outputConnection)
    throws ManifoldCFException;

}
