/* $Id: DocumentDeleteQueue.java 988245 2010-08-23 18:39:35Z kwright $ */

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
public class DocumentDeleteQueue
{
  public static final String _rcsid = "@(#)$Id: DocumentDeleteQueue.java 988245 2010-08-23 18:39:35Z kwright $";

  // Since the queue has a maximum size, an ArrayList is a fine way to keep it
  protected ArrayList queue = new ArrayList();

  /** Constructor.
  */
  public DocumentDeleteQueue()
  {
  }

  /** Wake up all threads waiting on this queue.  This happens at the beginning of a reset.
  */
  public void reset()
  {
    synchronized (queue)
    {
      queue.notifyAll();
    }
  }

  /** Clear.  This is only used on reset.
  */
  public void clear()
  {
    synchronized (queue)
    {
      queue.clear();
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

  /** Add a document set to the queue.  This will be a set of n documents (where n is some chunk size
  * set by experiment).
  *@param dd is the document set.
  */
  public void addDocuments(DocumentDeleteSet dd)
  {
    synchronized (queue)
    {
      queue.add(dd);
      queue.notify();
    }
  }

  /** Pull a document set off the queue, and wait if there is
  * nothing there.
  *@return the document.
  */
  public DocumentDeleteSet getDocuments()
    throws InterruptedException
  {
    synchronized (queue)
    {
      // If queue is empty, go to sleep
      while (queue.size() == 0)
        queue.wait();
      // If we've been awakened, there's either an entry to grab, or we've been
      // awakened because it's time to reset.
      if (queue.size() == 0)
        return null;
      // If we've been awakened, there's an entry to grab
      DocumentDeleteSet dd = (DocumentDeleteSet)queue.remove(queue.size()-1);
      return dd;
    }
  }


}
