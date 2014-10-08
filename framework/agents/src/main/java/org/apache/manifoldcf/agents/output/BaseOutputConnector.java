/* $Id: BaseOutputConnector.java 998081 2010-09-17 11:33:15Z kwright $ */

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
package org.apache.manifoldcf.agents.output;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This base class describes an instance of a connection between an output pipeline and the Connector Framework.
*
* Each instance of this interface is used in only one thread at a time.  Connection Pooling
* on these kinds of objects is performed by the factory which instantiates repository connectors
* from symbolic names and config parameters, and is pooled by these parameters.  That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
*
* Implementers of this interface should provide a default constructor which has this signature:
*
* xxx();
*
* Connectors are either configured or not.  If configured, they will persist in a pool, and be
* reused multiple times.  Certain methods of a connector may be called before the connector is
* configured.  This includes basically all methods that permit inspection of the connector's
* capabilities.
*
*/
public abstract class BaseOutputConnector extends org.apache.manifoldcf.core.connector.BaseConnector implements IOutputConnector
{
  public static final String _rcsid = "@(#)$Id: BaseOutputConnector.java 998081 2010-09-17 11:33:15Z kwright $";


  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[0];
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  @Override
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException
  {
    return false;
  }

  /** Notify the connector of a completed job.
  * This is meant to allow the connector to flush any internal data structures it has been keeping around, or to tell the output repository that this
  * is a good time to synchronize things.  It is called whenever a job is either completed or aborted.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // The base implementation does nothing here.
  }

  /** Detect if a document date is acceptable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param date is the date of the document.
  *@param checkActivity is an object including the activities that can be performed by this method.
  *@return true if the document with that date can be accepted by this connector.
  */
  @Override
  public boolean checkDateIndexable(VersionContext pipelineDescription, Date date, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    return true;
  }

  /** Detect if a mime type is acceptable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param mimeType is the mime type of the document.
  *@param checkActivity is an object including the activities that can be performed by this method.
  *@return true if the mime type can be accepted by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext pipelineDescription, String mimeType, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    return true;
  }

  /** Pre-determine whether a document (passed here as a File object) is acceptable or not.  This method is
  * used to determine whether a document needs to be actually transferred.  This hook is provided mainly to support
  * search engines that only handle a small set of accepted file types.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param localFile is the local file to check.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  @Override
  public boolean checkDocumentIndexable(VersionContext pipelineDescription, File localFile, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    return true;
  }

  /** Pre-determine whether a document's length is acceptable.  This method is used
  * to determine whether to fetch a document in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param length is the length of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  @Override
  public boolean checkLengthIndexable(VersionContext pipelineDescription, long length, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    return true;
  }

  /** Pre-determine whether a document's URL is acceptable.  This method is used
  * to help filter out documents that cannot be indexed in advance.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param url is the URL of the document.
  *@param checkActivity is an object including the activities that can be done by this method.
  *@return true if the file is acceptable, false if not.
  */
  @Override
  public boolean checkURLIndexable(VersionContext pipelineDescription, String url, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    return true;
  }

  /** Get a pipeline version string, given a pipeline specification object.  The version string is used to
  * uniquely describe the pertinent details of the specification and the configuration, to allow the Connector 
  * Framework to determine whether a document will need to be processed again.
  * Note that the contents of any document cannot be considered by this method; only configuration and specification information
  * can be considered.
  *
  * This method presumes that the underlying connector object has been configured.
  *@param spec is the current pipeline specification object for this connection for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes configuration and specification in such a way that
  * if two such strings are equal, nothing that affects how or whether the document is indexed will be different.
  */
  @Override
  public VersionContext getPipelineDescription(Specification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    return new VersionContext("",params,spec);
  }
  
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
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    return DOCUMENTSTATUS_REJECTED;
  }

  /** Remove a document using the connector.
  * Note that the last outputDescription is included, since it may be necessary for the connector to use such information to know how to properly remove the document.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the last description string that was constructed for this document by the getOutputDescription() method above.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  @Override
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Does nothing in the base class
  }
  
  /** Notify the connector that all records associated with this connection have been removed.
  * This method allows the connector to remove any internal data storage that is associated with records sent to the index on
  * behalf of a connection. It should not attempt to communicate with the output index.
  */
  @Override
  public void noteAllRecordsRemoved()
    throws ManifoldCFException
  {
    // Does nothing in the base class
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing output specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).

  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  @Override
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecification";
  }

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecificationForSave";
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected an output connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the output specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current output specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException
  {
    return null;
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current output specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
  }
  
}


