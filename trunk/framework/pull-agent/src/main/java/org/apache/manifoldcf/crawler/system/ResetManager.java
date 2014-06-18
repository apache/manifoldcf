/* $Id: ResetManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** The reset manager basically keeps track of threads that work together.  If the
* threads get hosed as a result of problems, then certain resets need to be done.
* Each instance of this manager therefore tracks all the threads which depend or
* affect a condition that needs explicit resetting.  When a thread recognizes that
* the database (or whatever resource) is potentially in a state where a reset for
* the particular condition is required, then the corresponding reset manager object
* will cause all dependent threads to block, until they are all accounted for.
* Then, the corrective reset is done, and the threads are released (with a signal
* corresponding to the fact that a reset occurred returned).
*
* This class is meant to be extended in order to implement the exact reset
* functionality required.
*/
public abstract class ResetManager
{
  public static final String _rcsid = "@(#)$Id: ResetManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Process ID */
  protected final String processID;
  
  /** Boolean which describes whether an event requiring reset has occurred. */
  protected volatile boolean resetRequired = false;
  /** This is the count of the threads that care about this resource. */
  protected int involvedThreadCount = 0;
  /** This is the number of threads that are waiting for the reset. */
  protected volatile int waitingThreads = 0;

  /** Constructor.
  */
  public ResetManager(String processID)
  {
    this.processID = processID;
  }

  /** Register a thread with this reset manager.
  */
  public synchronized void registerMe()
  {
    involvedThreadCount++;
  }

  /** Note a resettable event.
  */
  public void noteEvent()
  {
    //System.out.println(this + " Event noted; involvedThreadCount = "+involvedThreadCount);
    synchronized (this)
    {
      resetRequired = true;
    }
    performWakeupLogic();
  }

  /** Enter "wait" state for current thread.
  * This method is the main logic for the reset manager.  A thread
  * calls this method, which may block until all other threads are
  * waiting too.  Then, the reset method is called by exactly ONE
  * of the waiting threads, and they all are released.
  * @return false if no reset took place, or true if one did.
  */
  public synchronized boolean waitForReset(IThreadContext tc)
    throws ManifoldCFException, InterruptedException
  {
    if (resetRequired == false)
      return false;

    waitingThreads++;

    // Check if this is the "Prince Charming" thread, who will wake up
    // all the others.
    if (waitingThreads == involvedThreadCount)
    {
      //System.out.println(this + " Prince Charming thread found!");
      // Kick off reset, and wake everyone up
      // There's a question of what to do if the reset fails.
      // Right now, my notion is that we throw the exception
      // in the current thread, and just make sure everything
      // is tracked.
      try
      {
        performResetLogic(tc, processID);
      }
      finally
      {
        // MUST do all this in the finally block, because if the reset fails we'll wind up with
        // all threads blocked if we don't.  All waiting threads will be restarted, and will fail
        // again, but that's the only way we can retry.
        waitingThreads = 0;
        resetRequired = false;
        notifyAll();
      }
      return true;
    }

    //System.out.println(this + " Waiting threads = "+waitingThreads+"; going to sleep");
    // Just go to sleep until kicked.
    wait();
    // If we were awakened, it's because reset was fired.
    return true;
  }

  /** Do the reset logic.
  */
  protected abstract void performResetLogic(IThreadContext tc, String processID)
    throws ManifoldCFException;

  /** Do the wakeup logic.
  */
  protected abstract void performWakeupLogic();
  
}
