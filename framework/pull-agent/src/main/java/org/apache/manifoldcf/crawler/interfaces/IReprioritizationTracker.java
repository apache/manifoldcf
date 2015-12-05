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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This interface represents functionality that tracks cluster-wide
* reprioritization operations.
* These operations are driven forward by whatever thread needs them,
* and are completed if those processes die by the threads that clean up
* after the original process.
*/
public interface IReprioritizationTracker
{
  public static final String _rcsid = "@(#)$Id$";

  /** Start a reprioritization activity.
  *@param processID is the process ID of the process performing/waiting for the prioritization
  * to complete.
  *@param reproID is the reprocessing thread ID
  */
  public void startReprioritization(String processID, String reproID)
    throws ManifoldCFException;
  
  /** Complete a reprioritization activity.  Prioritization will be marked as complete
  * only if the processID matches the one that started the current reprioritization.
  *@param processID is the process ID of the process completing the prioritization.
  */
  public void doneReprioritization(String reproID)
    throws ManifoldCFException;
  
  /** Check if the specified processID is the one performing reprioritization.
  *@param processID is the process ID to check.
  *@return the repro ID if the processID is confirmed to be the one.
  */
  public String isSpecifiedProcessReprioritizing(String processID)
    throws ManifoldCFException;
  
  /** Assess the current minimum depth.
  * This method is called to provide information about the priorities of the documents being currently
  * queued.  It is the case that it is unoptimal to assign document priorities that are fundamentally higher than this value,
  * because then the new documents will be preferentially queued, and the goal of distributing documents across bins will not be
  * adequately met.
  *@param binNamesSet is the current set of priorities we see on the queuing operation.
  */
  public void assessMinimumDepth(Double[] binNamesSet)
    throws ManifoldCFException;

  /** Retrieve current minimum depth.
  *@return the current minimum depth to use.
  */
  public double getMinimumDepth()
    throws ManifoldCFException;
  
  /** Note preload amounts.
  */
  public void addPreloadRequest(String connectorClass, String binName, double weightedMinimumDepth);
  
  /** Preload bin values.  Call this OUTSIDE of a transaction.
  */
  public void preloadBinValues()
    throws ManifoldCFException;
  
  /** Clear any preload requests.
  */
  public void clearPreloadRequests();
  
  /** Clear remaining preloaded values.
  */
  public void clearPreloadedValues();

  /** Get a bin value.  Must be called INSIDE a transaction.
  *@param connectorClass is the connector class name.
  *@param binName is the bin name.
  *@param weightedMinimumDepth is the minimum depth to use.
  *@return the bin value.
  */
  public double getIncrementBinValue(String connectorClass, String binName, double weightedMinimumDepth)
    throws ManifoldCFException;
  

}

