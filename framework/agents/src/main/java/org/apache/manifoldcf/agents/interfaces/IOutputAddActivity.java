/* $Id: IOutputAddActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.*;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that an output connector can do
when adding or replacing documents.
*/
public interface IOutputAddActivity extends IOutputQualifyActivity,IOutputHistoryActivity,IOutputCheckActivity
{
  public static final String _rcsid = "@(#)$Id: IOutputAddActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Send a document via the pipeline to the next output connection.
  *@param documentURI is the document's URI.
  *@param document is the document data to be processed (handed to the output data store).
  *@return the document status (accepted or permanently rejected); return codes are listed in IPipelineConnector.
  *@throws IOException only if there's an IO error reading the data from the document.
  */
  public int sendDocument(String documentURI, RepositoryDocument document)
    throws ManifoldCFException, ServiceInterruption, IOException;

  /** Send NO document via the pipeline to the next output connection.  This is equivalent
  * to sending an empty document placeholder.
  */
  public void noDocument()
    throws ManifoldCFException, ServiceInterruption;

}
