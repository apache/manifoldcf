/* $Id: PerformanceStatistics.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;

/** An instance of this class keeps a running average of how long it takes for every connection to process a document.
* This information is used to limit queuing per connection to something reasonable given the characteristics of the connection.
*/
public class PerformanceStatistics
{
  public static final String _rcsid = "@(#)$Id: PerformanceStatistics.java 988245 2010-08-23 18:39:35Z kwright $";

  /** This is the fetch rate that will be returned in the complete absence of any other information.  This represents a 'wild guess' of a sort,
  * used only at the very start of a job, and designed to not hopelessly overload the queue with stuff from one connection only. */
  protected static double DEFAULT_FETCH_RATE = 900.0;
  protected static long DEFAULT_FETCH_TIME = (long)(((double)60000.0)/DEFAULT_FETCH_RATE);

  /** These are the weighting coefficients for the average.  They should all add up to 1.0 */
  protected static double[] weights = new double[]{0.5,0.25,0.125,0.0625,0.0625};

  /** This hash is keyed by the connection name, and has elements of type AveragingQueue */
  protected HashMap connectionHash = new HashMap();

  /** Constructor */
  public PerformanceStatistics()
  {
  }

  /** Note the successful completion of a set of documents using a single connection, and record the statistics for them. **/
  public synchronized void noteDocumentsCompleted(String connectionName, int documentSetSize, long elapsedTime)
  {
    AveragingQueue q = (AveragingQueue)connectionHash.get(connectionName);
    if (q == null)
    {
      q = new AveragingQueue();
      connectionHash.put(connectionName,q);
    }
    q.addRecord(documentSetSize,elapsedTime);
  }

  /** Obtain current average document fetch rate (in documents per minute per connection) */
  public synchronized double calculateConnectionFetchRate(String connectionName)
  {
    AveragingQueue q = (AveragingQueue)connectionHash.get(connectionName);
    if (q == null)
      // If there's no averaging queue, return a value that is consistent with wide-open performance
      return DEFAULT_FETCH_RATE;
    return q.calculateFetchRate();
  }

  /** This class keeps track of some depth of fetch history for an individual connection, and is used to calculate a
  * weighted average fetches-per-minute rate. */
  protected static class AveragingQueue
  {
    /** The internal structure of the averaging queue is a circular buffer, which gets initialized to the default value */
    protected AveragingRecord[] records;

    /** This is the current start pointer */
    protected int startIndex;

    /** Constructor */
    public AveragingQueue()
    {
      records = new AveragingRecord[weights.length];
      int i = 0;
      while (i < weights.length)
      {
        records[i++] = new AveragingRecord(1,DEFAULT_FETCH_TIME);
      }
      startIndex = 0;
    }

    /** Add a record */
    public void addRecord(int setSize, long elapsedTime)
    {
      records[startIndex] = new AveragingRecord(setSize,elapsedTime);
      startIndex++;
      if (startIndex == records.length)
        startIndex -= records.length;
    }

    /** Calculate running-average fetch rate */
    public double calculateFetchRate()
    {
      // The calculation involves calculating the fetch rate for each point in the history we keep, and then multiplying it times the appropriate weight,
      // and summing the whole thing.
      double rval = 0.0;
      int currentIndex = startIndex;
      int i = 0;
      while (i < weights.length)
      {
        double currentWeight = weights[i++];
        if (currentIndex == 0)
          currentIndex = records.length;
        currentIndex--;
        AveragingRecord ar = records[currentIndex];
        rval += currentWeight * ar.calculateRate();
      }
      return rval;
    }
  }

  /** This class contains the data for a single document set against the given connection */
  protected static class AveragingRecord
  {
    protected int documentCount;
    protected long elapsedTime;

    public AveragingRecord(int documentCount, long elapsedTime)
    {
      this.documentCount = documentCount;
      this.elapsedTime = elapsedTime;
    }

    public double calculateRate()
    {
      return 60000.0 * ((double)documentCount)/((double)elapsedTime);
    }
  }

}
