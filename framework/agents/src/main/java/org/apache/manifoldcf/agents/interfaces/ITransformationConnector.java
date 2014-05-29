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

/** This interface describes an instance of a connection to a translation engine.
*
* Each instance of this interface is used in only one thread at a time.  Connection Pooling
* on these kinds of objects is performed by the factory which instantiates connector objects
* from symbolic names and config parameters, and is pooled by these parameters.  That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
*
* Implementers of this interface should provide a default constructor which has this signature:
*
* xxx();
*
* Connector instances are either configured or not.  If configured, they will persist in a pool, and be
* reused multiple times.  Certain methods of a connector may be called before the connector is
* configured.  This includes basically all methods that permit inspection of the connector's
* capabilities. 
*
*
* The purpose of the transformation connector is to allow documents to be transformed prior to them
* being sent to an output connection.
*
*/
public interface ITransformationConnector extends IConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Document statuses

  /** Document accepted */
  public final static int DOCUMENTSTATUS_ACCEPTED = IOutputConnector.DOCUMENTSTATUS_ACCEPTED;
  /** Document permanently rejected */
  public final static int DOCUMENTSTATUS_REJECTED = IOutputConnector.DOCUMENTSTATUS_REJECTED;

  /** Return a list of activities that this connector generates.
  * The connector does NOT need to be connected before this method is called.
  *@return the set of activities.
  */
  public String[] getActivitiesList();

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException;
    
    
  /** Get a transformation version string, given a transformation specification.  The transformation version string is used to
  * uniquely describe the pertinent details of the transformation specification and the configuration, to allow the Connector 
  * Framework to determine whether a document will need to be processed again.
  * Note that the contents of the document cannot be considered by this method; only configuration and specification information
  * can be considered.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the transformation
  * engine should that be necessary.
  *@param spec is the current transformation specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes translation configuration and specification in such a way that
  * if two such strings are equal, a document will not need to be translated again .
  */
  public String getTranslationDescription(TransformationSpecification spec)
    throws ManifoldCFException, ServiceInterruption;

  /** Detect if a mime type is transformable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param transformationDescription is the document's translation version.
  *@param mimeType is the mime type of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the mime type can be accepted by this connector.
  */
  public boolean checkMimeTypeIndexable(String transformationDescription, String mimeType, ITransformationCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document (passed here as a File object) is transformable by this connector.  This method is
  * used to determine whether a document needs to be actually transferred.  This hook is provided mainly to support
  * search engines that only handle a small set of accepted file types.
  *@param transformationDescription is the document's transformation version.
  *@param localFile is the local file to check.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is indexable.
  */
  public boolean checkDocumentIndexable(String transformationDescription, File localFile, ITransformationCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's length is transformable by this connector.  This method is used
  * to determine whether to fetch a document in the first place.
  *@param transformationDescription is the document's transformation version.
  *@param length is the length of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is indexable.
  */
  public boolean checkLengthIndexable(String transformationDescription, long length, ITransformationCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is transformable by this connector.  This method is used
  * to help filter out documents that cannot be indexed in advance.
  *@param transformationDescription is the document's transformation version.
  *@param url is the URL of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is indexable.
  */
  public boolean checkURLIndexable(String transformationDescription, String url, ITransformationCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption;

  /** Transform a document using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the
  * transformation engine should that be necessary.
  * The TransformationSpecification is *not* provided to this method, because the goal is consistency, and if output is done it
  * must be consistent with the transformation description, since that was what was partly used to determine if
  * transformation should be taking place.  So it may be necessary for this method to decode
  * an transformation description string in order to determine what should be done.
  *@param transformationDescription is the description string that was constructed for this document by the getTransformationDescription() method.
  *@param documentURI is the URI of the document.  This is for transformation purposes only.
  *@param document is the document data to be transformed (handed to the transformation engine).
  *@param activities is the handle to an object that the implementer of a transformation connector may use to perform operations, such as
  * sending the transformed document downstream for further processing, or logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  public int transformDocument(String transformationDescription, String documentURI, RepositoryDocument document, ITransformationActivity activities)
    throws ManifoldCFException, ServiceInterruption;

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch (inherited from IConnector) is involved in setting up connection configuration information.
  // The second bunch
  // is involved in presenting and editing transformation specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a transformation connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, TransformationSpecification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException;
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a transformation connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, TransformationSpecification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException;
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, TransformationSpecification os,
    int connectionSequenceNumber)
    throws ManifoldCFException;
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the transformation specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current output specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, Locale locale, TransformationSpecification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException;
  
}


