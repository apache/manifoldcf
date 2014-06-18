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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class is what's actually queued for cleanup threads.  It represents an array of DocumentDescription objects,
* of an appropriate size to be a decent chunk.  It will be processed by a single cleanup worker thread, in bulk.
*/
public class DocumentCleanupSet
{
  public static final String _rcsid = "@(#)$Id$";

  /** This is the array of documents to delete. */
  protected CleanupQueuedDocument[] documents;
  /** The job description for this set of documents. */
  protected IJobDescription jobDescription;

  /** Constructor.
  *@param documents is the arraylist representing the documents for this chunk.
  *@param jobDescription is the job description for all the documents.
  */
  public DocumentCleanupSet(CleanupQueuedDocument[] documents, IJobDescription jobDescription)
  {
    this.documents = documents;
    this.jobDescription = jobDescription;
  }

  /** Get the job description.
  *@return the job description.
  */
  public IJobDescription getJobDescription()
  {
    return this.jobDescription;
  }

  /** Get the number of documents.
  *@return the number.
  */
  public int getCount()
  {
    return documents.length;
  }

  /** Get the nth document.
  *@param index is the document number.
  *@return the document.
  */
  public CleanupQueuedDocument getDocument(int index)
  {
    return documents[index];
  }

}
