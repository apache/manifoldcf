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

/** This interface abstracts from the activities that a transformation connector can do
when transforming a document.
*/
public interface ITransformationActivity extends ITransformationCheckActivity
{
  public static final String _rcsid = "@(#)$Id$";

  /** Send a (transformed) document via the pipeline to the current output connection.
  * This method must be called in order for any output indexing to take place!
  *@param document is the document data to be processed (handed to the output data store).
  *@return the document status (accepted or permanently rejected); return codes are listed in IOutputConnector.
  */
  public int sendDocument(RepositoryDocument document)
    throws ManifoldCFException, ServiceInterruption;
}
