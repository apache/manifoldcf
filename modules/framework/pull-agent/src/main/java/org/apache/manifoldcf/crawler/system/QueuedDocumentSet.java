/* $Id: QueuedDocumentSet.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class is what's actually queued.  It is immutable and it represents an array or set of QueuedDocument objects, all of which
* will be processed by a single worker thread in bulk.
*/
public class QueuedDocumentSet
{
  public static final String _rcsid = "@(#)$Id: QueuedDocumentSet.java 988245 2010-08-23 18:39:35Z kwright $";

  /** This is the array of QueuedDocument objects. */
  protected QueuedDocument[] documents;
  /** The job description that applies to this document set.  There is no guarantee that
  * this won't change before we get around to processing the document; therefore any
  * job-based metadata changes will also need to go through the queue mechanism. */
  protected IJobDescription jobDescription;
  /** The connection description that applies to this document set. */
  protected IRepositoryConnection connection;

  /** Constructor.
  *@param documents is the arraylist representing the documents accumulated for a single connection.
  */
  public QueuedDocumentSet(ArrayList documents, IJobDescription jobDescription, IRepositoryConnection connection)
  {
    this.documents = new QueuedDocument[documents.size()];
    int i = 0;
    while (i < this.documents.length)
    {
      this.documents[i] = (QueuedDocument)documents.get(i);
      i++;
    }
    this.jobDescription = jobDescription;
    this.connection = connection;
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
  public QueuedDocument getDocument(int index)
  {
    return documents[index];
  }

  /** Log that we are beginning the processing of a set of documents */
  public void beginProcessing(QueueTracker queueTracker)
  {
    int l = 0;
    while (l < documents.length)
    {
      QueuedDocument d = documents[l++];
      queueTracker.beginProcessing(d.getBinNames());
    }
  }

  /** Log that we are done processing a set of documents */
  public void endProcessing(QueueTracker queueTracker)
  {
    int l = 0;
    while (l < documents.length)
    {
      QueuedDocument d = documents[l++];
      queueTracker.endProcessing(d.getBinNames());
    }

  }

  /** Calculate a rating for this set.
  *@param overlapCalculator is the calculator object.
  *@return the rating.
  */
  public double calculateAssignmentRating(QueueTracker overlapCalculator)
  {
    // This rating is the average across all documents in the set.
    double ratingAccumulator = 0.0;
    int i = 0;
    while (i < documents.length)
    {
      String[] binNames = documents[i++].getBinNames();
      ratingAccumulator += overlapCalculator.calculateAssignmentRating(binNames,connection);
    }

    return ratingAccumulator / (double)documents.length;
  }

  /** Get the job description.
  *@return the job description.
  */
  public IJobDescription getJobDescription()
  {
    return jobDescription;
  }

  /** Get the connection.
  *@return the connection.
  */
  public IRepositoryConnection getConnection()
  {
    return connection;
  }

}
