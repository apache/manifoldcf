/* $Id: RequestQueue.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;

/** This class describes a authorization request queue, which has a "stuffer" servlet and many "reader" threads.
* The queue manages thread synchronization so that (a) the "stuffer" servlet blindly appends authority requests, and
* then waits for these requests to be completed, and
* (b) the "reader" threads block if queue is empty.
* The objects being queued are all AuthRequest objects.
*/
public class RequestQueue<T>
{
  public static final String _rcsid = "@(#)$Id: RequestQueue.java 988245 2010-08-23 18:39:35Z kwright $";

  // Since the queue has a maximum size, an ArrayList is a fine way to keep it
  protected List<T> queue = new ArrayList<T>();

  /** Constructor.
  */
  public RequestQueue()
  {
  }

  /** Add a request to the queue.
  *@param dd is the request.
  */
  public void addRequest(T dd)
  {
    synchronized (queue)
    {
      queue.add(dd);
      queue.notify();
    }
  }

  /** Pull the next request off the queue, but wait if there is
  * nothing there.
  *@return the request to be processed.
  */
  public T getRequest()
    throws InterruptedException
  {
    synchronized (queue)
    {
      // If queue is empty, go to sleep
      while (queue.size() == 0)
        queue.wait();

      return queue.remove(queue.size()-1);
    }
  }


}
