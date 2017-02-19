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
import java.util.regex.*;

/** This class calculates a document priority given all the required inputs.
* It is not thread safe, but calls classes that are (e.g. QueueTracker).
*/
public class PriorityCalculator implements IPriorityCalculator
{
  public static final String _rcsid = "@(#)$Id$";

  /** This is a made-up constant, originally based on 100 documents/second, but adjusted downward as a result of experimentation and testing, which is described as "T" below.
  */
  private final static double minMsPerFetch = 50.0;

  protected final IRepositoryConnection connection;
  protected final String[] binNames;
  protected final String documentIdentifier;
  protected final IReprioritizationTracker rt;
  
  protected final double[] binCountScaleFactors;
  protected final double[] weightedMinimumDepths;
  
  protected Double cachedValue = null;
  
  /** Constructor. */
  public PriorityCalculator(IReprioritizationTracker rt, IRepositoryConnection connection, String[] documentBins, String documentIdentifier)
    throws ManifoldCFException
  {
    this(rt,rt.getMinimumDepth(),connection,documentBins,documentIdentifier);
  }
  
  public PriorityCalculator(IReprioritizationTracker rt, double currentMinimumDepth, IRepositoryConnection connection, String[] documentBins, String documentIdentifier)
    throws ManifoldCFException
  {
    this.documentIdentifier = documentIdentifier;
    this.connection = connection;
    this.binNames = documentBins;
    this.rt = rt;
    
    // Now, precompute the weightedMinimumDepths etc; we'll need it whether we preload or not.
    
    // For each bin, we will be calculating the bin count scale factor, which is what we multiply the bincount by to adjust for the
    // throttling on that bin.
    binCountScaleFactors = new double[binNames.length];
    weightedMinimumDepths = new double[binNames.length];

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

    // Before calculating priority, calculate some factors that will allow us to determine the proper starting value for a bin.
    // First thing to do is to reset the bin values based on the current minimum.
    for (int i = 0; i < binNames.length; i++)
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
      weightedMinimumDepths[i] = currentMinimumDepth / binCountScaleFactor;
    }
    
  }
  
  /** Log a preload request for this priority value.
  */
  public void makePreloadRequest()
  {
    for (int i = 0; i < binNames.length; i++)
    {
      String binName = binNames[i];
      rt.addPreloadRequest(connection.getClassName(), binName, weightedMinimumDepths[i]);
    }

  }

  /** Calculate a document priority value.  Priorities are reversed, and in log space, so that
  * zero (0.0) is considered the highest possible priority, and larger priority values are considered lower in actual priority.
  *@param binNames are the global bins to which the document belongs.
  *@param connection is the connection, from which the throttles may be obtained.  More highly throttled connections are given
  *          less favorable priority.
  *@return the priority value, based on recent history.  Also updates statistics atomically.
  */
  @Override
  public double getDocumentPriority()
    throws ManifoldCFException
  {
     if (cachedValue != null)
       return cachedValue.doubleValue();

    double highestAdjustedCount = 0.0;
    // Find the bin with the largest effective count, and use that for the document's priority.
    // (This of course assumes that the slowest throttle is the one that wins.)
    for (int i = 0; i < binNames.length; i++)
    {
      String binName = binNames[i];
      double binCountScaleFactor = binCountScaleFactors[i];
      double weightedMinimumDepth = weightedMinimumDepths[i];

      double thisCount = rt.getIncrementBinValue(connection.getClassName(),binName,weightedMinimumDepth);
      double adjustedCount;
      // Use the scale factor already calculated above to yield a priority that is adjusted for the fetch rate.
      if (binCountScaleFactor == Double.POSITIVE_INFINITY)
        adjustedCount = Double.POSITIVE_INFINITY;
      else
        adjustedCount = thisCount * binCountScaleFactor;
      if (adjustedCount > highestAdjustedCount)
        highestAdjustedCount = adjustedCount;
    }
    
    // Calculate the proper log value
    double returnValue;
    
    if (highestAdjustedCount == Double.POSITIVE_INFINITY)
      returnValue = Double.POSITIVE_INFINITY;
    else
      returnValue = Math.log(1.0 + highestAdjustedCount);

    if (Logging.scheduling.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      int k = 0;
      while (k < binNames.length)
      {
        sb.append(binNames[k++]).append(" ");
      }
      Logging.scheduling.debug("Document '"+documentIdentifier+"' with bins ["+sb.toString()+"] given priority value "+new Double(returnValue).toString());
    }

    cachedValue = new Double(returnValue);

    return returnValue;
  }


  /** Calculate the maximum fetch rate for a given set of bins for a given connection.
  * This is used to adjust the final priority of a document.
  */
  protected static double[] calculateMaxFetchRates(String[] binNames, IRepositoryConnection connection)
  {
    ThrottleLimits tl = new ThrottleLimits(connection);
    return tl.getMaximumRates(binNames);
  }

  /** This class represents the throttle limits out of the connection specification */
  protected static class ThrottleLimits
  {
    protected List<ThrottleLimitSpec> specs = new ArrayList<ThrottleLimitSpec>();

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
      for (int j = 0 ; j < binNames.length ; j++)
      {
        String binName = binNames[j];
        double maxRate = Double.POSITIVE_INFINITY;
        for (ThrottleLimitSpec spec : specs)
        {
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
      }
      return rval;
    }

  }

  /** This is a class which describes an individual throttle limit, in fetches per millisecond. */
  protected static class ThrottleLimitSpec
  {
    /** Regexp */
    protected final Pattern regexp;
    /** The fetch limit for all bins matching that regexp, in fetches per millisecond */
    protected final double maxRate;

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

}

