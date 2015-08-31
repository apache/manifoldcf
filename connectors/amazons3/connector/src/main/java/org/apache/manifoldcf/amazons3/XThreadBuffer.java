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
package org.apache.manifoldcf.amazons3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.manifoldcf.crawler.system.Logging;

/**
 * Generic XThread class 
 * @author Kuhajeyan
 *
 * @param <T>
 */
public class XThreadBuffer<T> {
  protected static int MAX_SIZE = 1024;

  protected List<T> buffer = Collections.synchronizedList(new ArrayList<T>(
      MAX_SIZE));

  protected boolean complete = false;

  protected boolean abandoned = false;

  /** Constructor */
  public XThreadBuffer() {
  }

  public synchronized void add(T t) throws InterruptedException {
    while (buffer.size() == MAX_SIZE && !abandoned)
      wait();
    if (abandoned)
      return;
    buffer.add(t);
    // Notify threads that are waiting on there being stuff in the queue
    notifyAll();
  }

  public synchronized void abandon() {
    abandoned = true;
    // Notify waiting threads
    notifyAll();
  }

  public synchronized T fetch() throws InterruptedException {

    while (buffer.size() == 0 && !complete) 
    {
      if (Logging.connectors != null) {
        Logging.connectors.info("thread will be put to wait");
      }
      wait();
    }

    if (buffer.size() == 0)
      return null;
    boolean isBufferFull = (buffer.size() == MAX_SIZE);
    T rval = buffer.remove(buffer.size() - 1);
    // Notify those threads waiting on buffer being not completely full to
    // wake
    if (isBufferFull)
      notifyAll();
    return rval;
  }

  public synchronized void signalDone() {
    complete = true;
    // Notify threads that are waiting for stuff to appear, because it won't
    notifyAll();
  }

}
