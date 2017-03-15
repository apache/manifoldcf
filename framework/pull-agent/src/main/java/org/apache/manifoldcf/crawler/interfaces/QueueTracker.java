/* $Id: QueueTracker.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** This class attempts to provide document priorities in order to acheive as much balance as possible between documents having different bins.
* A document's priority assignment takes place at the time the document is added to the queue, and will be recalculated when a job is aborted, or
* when the crawler daemon is started.  The document priorities are strictly obeyed when documents are chosen from the queue and handed to
* worker threads; higher-priority documents always have precedence, except due to deliberate priority adjustment specified by the job priority.
*
* The priority values themselves are logarithmic: 0.0 is the highest, and the larger the number, the lower the priority.
*
* The basis for the calculation for each document priority handed out by this module are:
*
* - number of documents having a given bin (tracked)
* - performance of a connection (gathered through statistics)
* - throttling that applies to the each document bin
*
*
* The queuing prioritization model hooks into the document lifecycle in the following places:
* (1) When a document is added to the queue (and thus when its priority is handed out)
* (2) When documents that were *supposed* to be added to the queue turned out to already be there and already have an established priority,
*     (in which case the priority that was handed out before is returned to the pool for reuse)
* (3) When a document is pulled from the database queue (which sets the current highest priority level that should not be exceeded in step (1))
*
* The assignment prioritization model is largely independent of the queuing prioritization model, and is used to select among documents that have
* been marked "active" as they are handed to worker threads.  These events cause information to be logged:
* (1) When a document is handed to a worker thread
* (2) When the worker thread completes the document
*
*/
public class QueueTracker
{
  public static final String _rcsid = "@(#)$Id: QueueTracker.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Factor by which bins are reduced */
  protected final static double binReductionFactor = 1.0;

  /** These are the accumulated performance averages for all connections etc. */
  protected final PerformanceStatistics performanceStatistics = new PerformanceStatistics();

  /** These are the bin counts for tracking the documents that are on
  * the active queue, but are not being processed yet */
  protected final Map<String,BinCount> queuedBinCounts = new HashMap<String,BinCount>();

  /** These are the bin counts for active threads */
  protected final Map<String,BinCount> activeBinCounts = new HashMap<String,BinCount>();

  /** Constructor */
  public QueueTracker()
  {
  }

  /** Add an access record to the queue tracker.  This happens when a document
  * is added to the in-memory queue, and allows us to keep track of that particular event so
  * we can schedule in a way that meets our distribution goals.
  *@param binNames are the set of bins, as returned from the connector in question, for
  * the document that is being queued.  These bins are considered global in nature.
  */
  public void addRecord(String[] binNames)
  {
    int i = 0;
    while (i < binNames.length)
    {
      String binName = binNames[i++];
      synchronized (queuedBinCounts)
      {
        BinCount value = queuedBinCounts.get(binName);
        if (value == null)
        {
          value = new BinCount();
          queuedBinCounts.put(binName,value);
        }
        value.increment();
      }
    }

  }

  /** Note the time required to successfully complete a set of documents.  This allows this module to keep track of
  * the performance characteristics of each individual connection, so distribution across connections can be balanced
  * properly.
  */
  public void noteConnectionPerformance(int docCount, String connectionName, long elapsedTime)
  {
    performanceStatistics.noteDocumentsCompleted(connectionName,docCount,elapsedTime);
  }

  /** Obtain the current performance statistics object */
  public PerformanceStatistics getCurrentStatistics()
  {
    return performanceStatistics;
  }

  /** Note that we are beginning processing for a document with a particular set of bins.
  * This method is called when a worker thread starts work on a set of documents.
  */
  public void beginProcessing(String[] binNames)
  {
    // Effectively, we are moving the document from one status to another, so we adjust the bin counts of the source and
    // the target both.

    int i = 0;
    while (i < binNames.length)
    {
      String binName = binNames[i++];

      // Increment queued bin count for this bin.
      synchronized (queuedBinCounts)
      {
        BinCount value = queuedBinCounts.get(binName);
        if (value != null)
        {
          if (value.decrement())
            queuedBinCounts.remove(binName);
        }
      }

      // Decrement active bin count for this bin.
      synchronized (activeBinCounts)
      {
        BinCount value = activeBinCounts.get(binName);
        if (value == null)
        {
          value = new BinCount();
          activeBinCounts.put(binName,value);
        }
        value.increment();
      }
    }
  }


  /** Note that we have completed processing of a document with a given set of bins.
  * This method gets called when a Worker Thread has finished with a document.
  */
  public void endProcessing(String[] binNames)
  {
    // Remove the document from the active queue, by decrementing the corresponding active bin counts.

    int i = 0;
    while (i < binNames.length)
    {
      String binName = binNames[i++];
      synchronized (activeBinCounts)
      {
        BinCount value = activeBinCounts.get(binName);
        if (value != null)
        {
          if (value.decrement())
            activeBinCounts.remove(binName);
        }
      }
    }
  }

  /** Calculate an assignment rating for a set of bins based on what's currently in use.
  * This rating is used to help determine which documents returned from a queueing query actually get made "active",
  * and which ones are skipped for the moment.
  *
  * The rating returned
  * for each bin will be 1 divided by one plus the active thread count for that bin.  The higher the
  * rating, the better.  The ratings are combined by multiplying the rating for each bin by that for
  * every other bin, and then taking the nth root (where n is the number of bins) to normalize for
  * the number of bins.
  * The repository connection is used to reduce the priority of assignment, based on the fetch rate that will
  * result from this set of bins.
  */
  public double calculateAssignmentRating(String[] binNames, IRepositoryConnection connection)
  {
    // Work in log space
    double ratingLog = 0.0;
    int i = 0;
    while (i < binNames.length)
    {
      String binName = binNames[i++];
      int count = 0;
      synchronized (activeBinCounts)
      {
        BinCount value = activeBinCounts.get(binName);
        if (value != null)
          count = value.getValue();
      }
      // rating *= (1.0 / (1.0 + (double)count))
      ratingLog -= Math.log(1.0 + (double)count);
    }

    // Take the ith root of the bin rating, and leave it in log form
    return ratingLog/(double)i;
  }


  /** This is the class which allows a mutable integer count value to be saved in the bincount table.
  */
  protected static class BinCount
  {
    /** The count */
    protected int count = 0;

    /** Create */
    public BinCount()
    {
    }

    public BinCount duplicate()
    {
      BinCount rval = new BinCount();
      rval.count = this.count;
      return rval;
    }

    /** Increment the counter */
    public void increment()
    {
      count++;
    }

    /** Decrement the counter, returning true if empty */
    public boolean decrement()
    {
      count--;
      return count == 0;
    }

    /** Set the counter value */
    public void setValue(int count)
    {
      this.count = count;
    }

    /** Get the counter value */
    public int getValue()
    {
      return count;
    }
  }


}
