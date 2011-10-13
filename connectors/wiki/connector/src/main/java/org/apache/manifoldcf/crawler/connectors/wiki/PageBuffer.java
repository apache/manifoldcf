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
package org.apache.manifoldcf.crawler.connectors.wiki;

import java.util.*;

/** Thread-safe class that functions as a limited-size buffer of pageIDs */
public class PageBuffer
{
  protected static int MAX_SIZE = 1024;
  
  protected List<String> buffer = new ArrayList<String>(MAX_SIZE);
  
  protected boolean complete = false;
  protected boolean abandoned = false;
  
  /** Constructor */
  public PageBuffer()
  {
  }
  
  /** Add a page id to the buffer, and block if the buffer is full */
  public synchronized void add(String pageID)
    throws InterruptedException
  {
    while (buffer.size() == MAX_SIZE && !abandoned)
      wait();
    if (abandoned)
      return;
    buffer.add(pageID);
    // Notify threads that are waiting on there being stuff in the queue
    notifyAll();
  }
  
  /** Signal that the buffer should be abandoned */
  public synchronized void abandon()
  {
    abandoned = true;
    // Notify waiting threads
    notifyAll();
  }
  
  /** Signal that the operation is complete, and that no more pageID's
  * will be added.
  */
  public synchronized void signalDone()
  {
    complete = true;
    // Notify threads that are waiting for stuff to appear, because it won't
    notifyAll();
  }
  
  /** Pull an id off the buffer, and wait if there's more to come.
  * Returns null if the operation is complete.
  */
  public synchronized String fetch()
    throws InterruptedException
  {
    while (buffer.size() == 0 && !complete)
      wait();
    if (buffer.size() == 0)
      return null;
    boolean isBufferFull = (buffer.size() == MAX_SIZE);
    String rval = buffer.remove(buffer.size()-1);
    // Notify those threads waiting on buffer being not completely full to wake
    if (isBufferFull)
      notifyAll();
    return rval;
  }
  
}
