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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This interface describes an instance of a connector which can live in a chained processing pipeline.
* Both transformation connectors and output connectors are expected to extend this interface.
*
* Pipeline connectors have two basic functions:
* (1) Processing documents, and optionally passing them to the next pipeline stage;
* (2) Determining if a document is acceptable, optionally by querying the next pipeline stage.
*
*/
public interface IPipelineConnector extends IConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Document statuses

  /** Document accepted */
  public final static int DOCUMENTSTATUS_ACCEPTED = 0;
  /** Document permanently rejected */
  public final static int DOCUMENTSTATUS_REJECTED = 1;

  /** Get a pipeline version object, given a pipeline specification object.  The version string is used to
  * uniquely describe the pertinent details of the specification and the configuration, to allow the Connector 
  * Framework to determine whether a document will need to be processed again.
  * Note that the contents of any document cannot be considered by this method; only configuration and specification information
  * can be considered.
  *
  * This method presumes that the underlying connector object has been configured.
  *@param spec is the current pipeline specification object for this connection for the job that is doing the crawling.
  *@return a version object, including a string of unlimited length, which uniquely describes configuration and specification in such a way that
  * if two such strings are equal, nothing that affects how or whether the document is indexed will be different.
  */
  public VersionContext getPipelineDescription(Specification spec)
    throws ManifoldCFException, ServiceInterruption;

  /** Detect if a document date is acceptable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param date is the date of the document.
  *@param checkActivity is an object including the activities that can be performed by this method.
  *@return true if the document with that date can be accepted by this connector.
  */
  public boolean checkDateIndexable(VersionContext pipelineDescription, Date date, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Detect if a mime type is acceptable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param mimeType is the mime type of the document.
  *@param checkActivity is an object including the activities that can be performed by this method.
  *@return true if the mime type can be accepted by this connector.
  */
  public boolean checkMimeTypeIndexable(VersionContext pipelineDescription, String mimeType, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document (passed here as a File object) is acceptable or not.  This method is
  * used to determine whether a document needs to be actually transferred.  This hook is provided mainly to support
  * search engines that only handle a small set of accepted file types.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param localFile is the local file to check.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  public boolean checkDocumentIndexable(VersionContext pipelineDescription, File localFile, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's length is acceptable.  This method is used
  * to determine whether to fetch a document in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param length is the length of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  public boolean checkLengthIndexable(VersionContext pipelineDescription, long length, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is acceptable.  This method is used
  * to help filter out documents that cannot be indexed in advance.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param url is the URL of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  public boolean checkURLIndexable(VersionContext pipelineDescription, String url, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param pipelineDescription includes the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity,
  * or sending a modified document to the next stage in the pipeline.
  *@return the document status (accepted or permanently rejected).
  *@throws IOException only if there's a stream error reading the document data.
  */
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException;

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch (inherited from IConnector) is involved in setting up connection configuration information.
  // The second bunch
  // is involved in presenting and editing pipeline specification information for a connection within a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).

  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber);

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber);

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a pipeline connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this connection.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException;
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a pipeline connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException;
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException;
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the pipeline specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current pipeline specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException;
  
}


