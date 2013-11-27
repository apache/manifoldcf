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

import org.apache.manifoldcf.crawler.system.Logging;
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

  /** These are the bin counts for a prioritization pass.
  * This hash table is keyed by bin, and contains DoubleBinCount objects as values */
  protected final Map<String,DoubleBinCount> binCounts = new HashMap<String,DoubleBinCount>();

  /** These are the bin counts for tracking the documents that are on
  * the active queue, but are not being processed yet */
  protected final Map<String,BinCount> queuedBinCounts = new HashMap<String,BinCount>();

  /** These are the bin counts for active threads */
  protected final Map<String,BinCount> activeBinCounts = new HashMap<String,BinCount>();

  /** The "minimum depth" - which is the smallest bin count of the last document queued.  This helps guarantee that documents that are
  * newly discovered don't wind up with high priority, but instead wind up about the same as the currently active document priority. */
  protected double currentMinimumDepth = 0.0;

  /** This flag, when set, indicates that a reset is in progress, so queuetracker bincount updates are ignored. */
  protected boolean resetInProgress = false;


  /** Constructor */
  public QueueTracker()
  {
  }

  /** Reset the queue tracker.
  * This occurs ONLY when we are about to reprioritize all active documents.  It does not affect the portion of the queue tracker that
  * tracks the active queue.
  */
  public void beginReset()
  {
    synchronized (binCounts)
    {
      binCounts.clear();
      currentMinimumDepth = 0.0;
      resetInProgress = true;
    }

  }

  /** Finish the reset operation */
  public void endReset()
  {
    synchronized (binCounts)
    {
      resetInProgress = false;
    }
  }

  /** Add an access record to the queue tracker.  This happens when a document
  * is added to the in-memory queue, and allows us to keep track of that particular event so
  * we can schedule in a way that meets our distribution goals.
  *@param binNames are the set of bins, as returned from the connector in question, for
  * the document that is being queued.  These bins are considered global in nature.
  */
  public void addRecord(String[] binNames)
  {
    if (Logging.scheduling.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      int j = 0;
      while (j < binNames.length)
      {
        sb.append(binNames[j++]).append(" ");
      }
      Logging.scheduling.debug("Putting document with bins ["+sb.toString()+"] onto active queue");
    }
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
    if (Logging.scheduling.isDebugEnabled())
      Logging.scheduling.debug("Worker thread for connection "+connectionName+" took "+new Long(elapsedTime).toString()+"ms to handle "+Integer.toString(docCount)+" documents");

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
    if (Logging.scheduling.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      int j = 0;
      while (j < binNames.length)
      {
        sb.append(binNames[j++]).append(" ");
      }
      Logging.scheduling.debug("Handing document with bins ["+sb.toString()+"] to worker thread");
    }

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

  /** Assess the current minimum depth.
  * This method is called to provide to the QueueTracker information about the priorities of the documents being currently
  * queued.  It is the case that it is unoptimal to assign document priorities that are fundamentally higher than this value,
  * because then the new documents will be preferentially queued, and the goal of distributing documents across bins will not be
  * adequately met.
  *@param binNamesSet is the current set of priorities we see on the queuing operation.
  */
  public void assessMinimumDepth(Double[] binNamesSet)
  {
    synchronized (binCounts)
    {
      // Ignore all numbers until reset is complete
      if (!resetInProgress)
      {
        //Logging.scheduling.debug("In assessMinimumDepth");
        int j = 0;
        double newMinPriority = Double.MAX_VALUE;
        while (j < binNamesSet.length)
        {
          Double binValue = binNamesSet[j++];
          if (binValue.doubleValue() < newMinPriority)
            newMinPriority = binValue.doubleValue();
        }

        if (newMinPriority != Double.MAX_VALUE)
        {
          // Convert minPriority to minDepth.
          // Note that this calculation does not take into account anything having to do with connection rates, throttling,
          // or other adjustment factors.  It allows us only to obtain the "raw" minimum depth: the depth without any
          // adjustments.
          double newMinDepth = Math.exp(newMinPriority)-1.0;

          if (newMinDepth > currentMinimumDepth)
          {
            currentMinimumDepth = newMinDepth;
            if (Logging.scheduling.isDebugEnabled())
              Logging.scheduling.debug("Setting new minimum depth value to "+new Double(currentMinimumDepth).toString());
          }
          else
          {
            if (newMinDepth < currentMinimumDepth && Logging.scheduling.isDebugEnabled())
              Logging.scheduling.debug("Minimum depth value seems to have been set too high too early! currentMin = "+new Double(currentMinimumDepth).toString()+"; queue value = "+new Double(newMinDepth).toString());
          }
        }
      }
    }

  }


  /** Note that we have completed processing of a document with a given set of bins.
  * This method gets called when a Worker Thread has finished with a document.
  */
  public void endProcessing(String[] binNames)
  {
    if (Logging.scheduling.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      int j = 0;
      while (j < binNames.length)
      {
        sb.append(binNames[j++]).append(" ");
      }
      Logging.scheduling.debug("Worker thread done document with bins ["+sb.toString()+"]");
    }

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
    double rval = ratingLog/(double)i;

    if (false && Logging.scheduling.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      int j = 0;
      while (j < binNames.length)
      {
        sb.append(binNames[j++]).append(" ");
      }
      Logging.scheduling.debug("Document with bins ["+sb.toString()+"] given assignment rating "+new Double(rval).toString());
    }

    return rval;
  }

  /** This is a made-up constant, originally based on 100 documents/second, but adjusted downward as a result of experimentation and testing, which is described as "T" below.
  */
  private final static double minMsPerFetch = 50.0;

  /** Calculate a document priority value.  Priorities are reversed, and in log space, so that
  * zero (0.0) is considered the highest possible priority, and larger priority values are considered lower in actual priority.
  *@param binNames are the global bins to which the document belongs.
  *@param connection is the connection, from which the throttles may be obtained.  More highly throttled connections are given
  *          less favorable priority.
  *@return the priority value, based on recent history.  Also updates statistics atomically.
  */
  public double calculatePriority(String[] binNames, IRepositoryConnection connection)
  {
    synchronized (binCounts)
    {

      // NOTE: We must be sure to adjust the return value by the factor calculated due to performance; a slower throttle rate
      // should yield a lower priority.  In theory it should be possible to calculate an adjusted priority pretty exactly,
      // on the basis that the fetch rates of two distinct bins should grant priorities such that:
      //
      //  (n documents) / (the rate of fetch (docs/millisecond) of the first bin) = milliseconds for the first bin
      //
      //  should equal:
      //
      //  (m documents) / (the rate of fetch of the second bin) = milliseconds for the second bin
      //
      // ... and then assigning priorities so that after a given number of document priorities are assigned from the first bin, the
      // corresponding (*m/n) number of document priorities would get assigned for the second bin.
      //
      // Suppose the maximum fetch rate for the document is F fetches per millisecond.  If the document priority assigned for the Bth
      // bin member is -log(1/(1+B)) for a document fetched with no throttling whatsoever,
      // then we want the priority to be -log(1/(1+k)) for a throttled bin, where k is chosen so that:
      // k = B * ((T + 1/F)/T) = B * (1 + 1/TF)
      // ... where T is the time taken to fetch a single document that has no throttling at all.
      // For the purposes of this exercise, a value of 100 doc/sec, or T=10ms.
      //
      // Basically, for F = 0, k should be infinity, and for F = infinity, k should be B.


      // First, calculate the document's max fetch rate, in fetches per millisecond.  This will be used to adjust the priority, and
      // also when resetting the bin counts.
      double[] maxFetchRates = calculateMaxFetchRates(binNames,connection);

      // For each bin, we will be calculating the bin count scale factor, which is what we multiply the bincount by to adjust for the
      // throttling on that bin.
      double[] binCountScaleFactors = new double[binNames.length];


      // Before calculating priority, reset any bins to a higher value, if it seems like it is appropriate.  This is how we avoid assigning priorities
      // higher than the current level at which queuing is currently taking place.

      // First thing to do is to reset the bin values based on the current minimum.
      int i = 0;
      while (i < binNames.length)
      {
        String binName = binNames[i];
        // Remember, maxFetchRate is in fetches per ms.
        double maxFetchRate = maxFetchRates[i];

        // Calculate (and save for later) the scale factor for this bin.
        double binCountScaleFactor;
        if (maxFetchRate == 0.0)
          binCountScaleFactor = Double.POSITIVE_INFINITY;
        else
          binCountScaleFactor = 1.0 + 1.0 / (minMsPerFetch * maxFetchRate);
        binCountScaleFactors[i] = binCountScaleFactor;

        double thisCount = 0.0;
        DoubleBinCount bc = binCounts.get(binName);
        if (bc != null)
        {
          thisCount = bc.getValue();
        }
        // Adjust the count, if needed, so that we are not assigning priorities greater than the current level we are
        // grabbing documents at
        if (thisCount * binCountScaleFactor < currentMinimumDepth)
        {
          double weightedMinimumDepth = currentMinimumDepth / binCountScaleFactor;

          if (Logging.scheduling.isDebugEnabled())
            Logging.scheduling.debug("Resetting value of bin '"+binName+"' to "+new Double(weightedMinimumDepth).toString()+"(scale factor is "+new Double(binCountScaleFactor)+")");

          // Set a new bin value
          if (bc == null)
          {
            bc = new DoubleBinCount();
            binCounts.put(binName,bc);
          }
          bc.setValue(weightedMinimumDepth);
        }

        i++;
      }

      double returnValue;

      // There was no previously-calculated value available, so we need to calculate a new value.

      // Find the bin with the largest effective count, and use that for the document's priority.
      // (This of course assumes that the slowest throttle is the one that wins.)
      double highestAdjustedCount = 0.0;
      i = 0;
      while (i < binNames.length)
      {
        String binName = binNames[i];
        double binCountScaleFactor = binCountScaleFactors[i];

        double thisCount = 0.0;
        DoubleBinCount bc = binCounts.get(binName);
        if (bc != null)
          thisCount = bc.getValue();

        double adjustedCount;
        // Use the scale factor already calculated above to yield a priority that is adjusted for the fetch rate.
        if (binCountScaleFactor == Double.POSITIVE_INFINITY)
          adjustedCount = Double.POSITIVE_INFINITY;
        else
          adjustedCount = thisCount * binCountScaleFactor;
        if (adjustedCount > highestAdjustedCount)
          highestAdjustedCount = adjustedCount;
        i++;
      }

      // Calculate the proper log value
      if (highestAdjustedCount == Double.POSITIVE_INFINITY)
        returnValue = Double.POSITIVE_INFINITY;
      else
        returnValue = Math.log(1.0 + highestAdjustedCount);

      // Update bins to indicate we used another priority.  If more than one bin is associated with the document,
      // counts for all bins are nevertheless updated, because we don't wish to arrange scheduling collisions with hypothetical
      // documents that share any of these bins.
      int j = 0;
      while (j < binNames.length)
      {
        String binName = binNames[j];
        DoubleBinCount bc = binCounts.get(binName);
        if (bc == null)
        {
          bc = new DoubleBinCount();
          binCounts.put(binName,bc);
        }
        bc.increment();

        j++;
      }

      if (Logging.scheduling.isDebugEnabled())
      {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        while (k < binNames.length)
        {
          sb.append(binNames[k++]).append(" ");
        }
        Logging.scheduling.debug("Document with bins ["+sb.toString()+"] given priority value "+new Double(returnValue).toString());
      }


      return returnValue;
    }
  }

  /** Calculate the maximum fetch rate for a given set of bins for a given connection.
  * This is used to adjust the final priority of a document.
  */
  protected double[] calculateMaxFetchRates(String[] binNames, IRepositoryConnection connection)
  {
    ThrottleLimits tl = new ThrottleLimits(connection);
    return tl.getMaximumRates(binNames);
  }

  /** This class represents the throttle limits out of the connection specification */
  protected static class ThrottleLimits
  {
    protected ArrayList specs = new ArrayList();

    public ThrottleLimits(IRepositoryConnection connection)
    {
      String[] throttles = connection.getThrottles();
      int i = 0;
      while (i < throttles.length)
      {
        try
        {
          specs.add(new ThrottleLimitSpec(throttles[i],(double)connection.getThrottleValue(throttles[i])));
        }
        catch (PatternSyntaxException e)
        {
        }
        i++;
      }
    }

    public double[] getMaximumRates(String[] binNames)
    {
      double[] rval = new double[binNames.length];
      int j = 0;
      while (j < binNames.length)
      {
        String binName = binNames[j];
        double maxRate = Double.POSITIVE_INFINITY;
        int i = 0;
        while (i < specs.size())
        {
          ThrottleLimitSpec spec = (ThrottleLimitSpec)specs.get(i++);
          Pattern p = spec.getRegexp();
          Matcher m = p.matcher(binName);
          if (m.find())
          {
            double rate = spec.getMaxRate();
            // The direction of this inequality reflects the fact that the throttling is conservative when more rules are present.
            if (rate < maxRate)
              maxRate = rate;
          }
        }
        rval[j] = maxRate;
        j++;
      }
      return rval;
    }

  }

  /** This is a class which describes an individual throttle limit, in fetches per millisecond. */
  protected static class ThrottleLimitSpec
  {
    /** Regexp */
    protected Pattern regexp;
    /** The fetch limit for all bins matching that regexp, in fetches per millisecond */
    protected double maxRate;

    /** Constructor */
    public ThrottleLimitSpec(String regexp, double maxRate)
      throws PatternSyntaxException
    {
      this.regexp = Pattern.compile(regexp);
      this.maxRate = maxRate;
    }

    /** Get the regexp. */
    public Pattern getRegexp()
    {
      return regexp;
    }

    /** Get the max count */
    public double getMaxRate()
    {
      return maxRate;
    }
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

  /** This is the class which allows a mutable integer count value to be saved in the bincount table.
  */
  protected static class DoubleBinCount
  {
    /** The count */
    protected double count = 0.0;

    /** Create */
    public DoubleBinCount()
    {
    }

    public DoubleBinCount duplicate()
    {
      DoubleBinCount rval = new DoubleBinCount();
      rval.count = this.count;
      return rval;
    }

    /** Increment the counter */
    public void increment()
    {
      count += 1.0;
    }

    /** Set the value */
    public void setValue(double count)
    {
      this.count = count;
    }

    /** Get the value */
    public double getValue()
    {
      return count;
    }
  }

}
