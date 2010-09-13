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
package org.apache.acf.agents.output;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;

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
public abstract class BaseOutputConnector extends org.apache.acf.core.connector.BaseConnector implements IOutputConnector
{
  public static final String _rcsid = "@(#)$Id$";


  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
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
  public boolean requestInfo(Configuration output, String command)
    throws ACFException
  {
    return false;
  }

  /** Notify the connector of a completed job.
  * This is meant to allow the connector to flush any internal data structures it has been keeping around, or to tell the output repository that this
  * is a good time to synchronize things.  It is called whenever a job is either completed or aborted.
  */
  public void noteJobComplete()
    throws ACFException, ServiceInterruption
  {
    // The base implementation does nothing here.
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  public boolean checkMimeTypeIndexable(String mimeType)
    throws ACFException, ServiceInterruption
  {
    return true;
  }

  /** Pre-determine whether a document (passed here as a File object) is indexable by this connector.  This method is used by participating
  * repository connectors to help reduce the number of unmanageable documents that are passed to this output connector in advance of an
  * actual transfer.  This hook is provided mainly to support search engines that only handle a small set of accepted file types.
  *@param localFile is the local file to check.
  *@return true if the file is indexable.
  */
  public boolean checkDocumentIndexable(File localFile)
    throws ACFException, ServiceInterruption
  {
    return true;
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing output specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
 
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected an output connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, OutputSpecification os, ArrayList tabsArray)
    throws ACFException, IOException
  {
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, OutputSpecification os, String tabName)
    throws ACFException, IOException
  {
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the output specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param os is the current output specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, OutputSpecification os)
    throws ACFException
  {
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, OutputSpecification os)
    throws ACFException, IOException
  {
  }

}


