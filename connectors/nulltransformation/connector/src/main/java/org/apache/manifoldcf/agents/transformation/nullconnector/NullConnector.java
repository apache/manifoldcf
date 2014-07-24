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
package org.apache.manifoldcf.agents.transformation.nullconnector;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This connector works as a transformation connector, but does nothing other than logging.
*
*/
public class NullConnector extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector
{
  public static final String _rcsid = "@(#)$Id$";

  protected static final String ACTIVITY_PROCESS = "process";

  protected static final String[] activitiesList = new String[]{ACTIVITY_PROCESS};
  
  /** Return a list of activities that this connector generates.
  * The connector does NOT need to be connected before this method is called.
  *@return the set of activities.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
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
    long startTime = System.currentTimeMillis();
    String resultCode = "OK";
    String description = null;
    Long length = null;
    try
    {
      long binaryLength = document.getBinaryLength();
      int rval = activities.sendDocument(documentURI,document);
      length =  new Long(binaryLength);
      resultCode = (rval == DOCUMENTSTATUS_ACCEPTED)?"ACCEPTED":"REJECTED";
      return rval;
    }
    catch (ServiceInterruption e)
    {
      resultCode = "SERVICEINTERRUPTION";
      description = e.getMessage();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      resultCode = "EXCEPTION";
      description = e.getMessage();
      throw e;
    }
    catch (IOException e)
    {
      resultCode = "IOEXCEPTION";
      description = e.getMessage();
      throw e;
    }
    finally
    {
      activities.recordActivity(new Long(startTime), ACTIVITY_PROCESS, length, documentURI,
        resultCode, description);
    }

  }

}


