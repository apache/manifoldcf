/* $Id: DocumentQueue.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class describes a document queue, which has a "stuffer" thread and many "reader" threads.
* The queue manages thread synchronization so that (a) the "stuffer" thread blocks until queue is empty, and
* (b) the "reader" threads block if queue is empty.
* The objects being queued are all QueuedDocumentSet objects.
*/
public class DocumentQueue
{
  public static final String _rcsid = "@(#)$Id: DocumentQueue.java 988245 2010-08-23 18:39:35Z kwright $";

  // Since the queue has a maximum size, an ArrayList is a fine way to keep it
  protected final List<QueuedDocumentSet> queue = new ArrayList<QueuedDocumentSet>();
  // This flag gets set to 'true' if the queue is being cleared due to a reset
  protected boolean resetFlag = false;

  /** Constructor.
  */
  public DocumentQueue()
  {
  }

  /** Wake up all threads waiting on this queue.  This happens at the beginning of a reset.
  */
  public void reset()
  {
    synchronized (queue)
    {
      resetFlag = true;
      queue.notifyAll();
    }
  }

  /** Clear the queue.  This happens during a reset.
  */
  public void clear()
  {
    synchronized (queue)
    {
      queue.clear();
      resetFlag = false;
    }
  }

  /** Check if "empty".
  *@param n is the low-water mark; if the number falls below this, then this method will return true.
  */
  public boolean checkIfEmpty(int n)
  {
    synchronized (queue)
    {
      if (queue.size() <= n)
        return true;
    }
    return false;
  }


  /** Add a document to the queue.
  *@param dd is the document description.
  */
  public void addDocument(QueuedDocumentSet dd)
  {
    synchronized (queue)
    {
      queue.add(dd);
      queue.notify();
    }
  }

  /** Pull the best-rated document set off the queue, but wait if there is
  * nothing there.
  *@param overlapCalculator performs analysis of the document sets on the queue so that we can
  * pick the best one.
  *@return the document set.
  */
  public QueuedDocumentSet getDocument(QueueTracker overlapCalculator)
    throws InterruptedException
  {
    synchronized (queue)
    {
      // If we are being reset, return null
      if (resetFlag)
        return null;

      // If queue is empty, go to sleep
      while (queue.size() == 0 && resetFlag == false)
        queue.wait();

      // If we've been awakened, there's either an entry to grab, or we've been
      // awakened because it's time to reset.
      if (resetFlag)
        return null;

      // Go through all the documents and pick the one with the best rating
      int i = 0;
      int bestIndex = -1;
      double bestRating = Double.NEGATIVE_INFINITY;
      while (i < queue.size())
      {
        QueuedDocumentSet dd = queue.get(i);
        // Evaluate each document's bins.  These will be saved in the QueuedDocumentSet.
        double rating = dd.calculateAssignmentRating(overlapCalculator);
        if (bestIndex == -1 || rating > bestRating)
        {
          bestIndex = i;
          bestRating = rating;
        }
        i++;
      }
      // Pull off the best one.  DON'T REORDER!!
      QueuedDocumentSet rval = queue.remove(bestIndex);
      return rval;
    }
  }


}
