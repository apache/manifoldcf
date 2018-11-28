/* $Id: ServiceInterruption.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This is an exception that means that service was interrupted.  The exception contains
* a description of when the service may be restored.
*/
public class ServiceInterruption extends java.lang.Exception
{
  public static final String _rcsid = "@(#)$Id: ServiceInterruption.java 988245 2010-08-23 18:39:35Z kwright $";

  /** This is the time (in milliseconds since epoch) when to retry the request. */
  protected long retryTime;
  /** This is the time (in milliseconds since epoch) to FAIL if no successful read has yet occurred. */
  protected long failTime;
  /** This is the number of retries to permit before FAIL. -1 means infinite. */
  protected int failRetryCount;
  /** Should we abort the process if failure condition has been reached? */
  protected boolean abortOnFail;
  /** True if job inactive abort. */
  protected boolean jobInactiveAbort;
  
  /** Constructor.
  *@param message is the exact error condition.
  *@param retryTime is the time to retry.
  */
  public ServiceInterruption(String message, long retryTime)
  {
    this(message, retryTime, false);
  }
  
  /** Constructor.
  *@param message is the exact error condition.
  *@param retryTime is the time to retry.
  *@param jobInactiveAbort is true if this exception being thrown because the job is aborting
  */
  public ServiceInterruption(String message, long retryTime, boolean jobInactiveAbort)
  {
    super(message);
    this.retryTime = retryTime;
    this.failTime = -1L;
    this.failRetryCount = -1;
    this.abortOnFail = true;
    this.jobInactiveAbort = jobInactiveAbort;
  }

  /** Constructor.
  *@param message is the exact error condition.
  *@param failureCause is an exception that should be reported if it is decided to abort the process.
  *@param retryTime is the time to retry.
  *@param failTime is the time to fail.
  *@param failRetryCount is the number of times to retry before declaring failure.
  *@param abortOnFail signals what to do if failure.  Setting this to "true" will cause whatever process incurred the
  * service interruption to stop immediately, if failure condition has been reached.
  */
  public ServiceInterruption(String message, Throwable failureCause, long retryTime, long failTime, int failRetryCount,
    boolean abortOnFail)
  {
    super(message,failureCause);
    this.retryTime = retryTime;
    this.failTime = failTime;
    this.failRetryCount = failRetryCount;
    this.abortOnFail = abortOnFail;
  }

  /** Get the retry time.
  *@return the retry time.
  */
  public long getRetryTime()
  {
    return retryTime;
  }

  /** Get the fail time.
  *@return the fail time.  Returns -1L if there is no fail time.
  */
  public long getFailTime()
  {
    return failTime;
  }

  /** Get the number of error iterations needed before failure should be declared.
  *@return the count, -1 if infinite.
  */
  public int getFailRetryCount()
  {
    return failRetryCount;
  }

  /** On failure, should we abort?
  *@return true if abort is requested when failure is declared.
  */
  public boolean isAbortOnFail()
  {
    return abortOnFail;
  }

  /** Check if this service interruption is due to a job aborting.
  *@return true if yes
  */
  public boolean jobInactiveAbort()
  {
    return jobInactiveAbort;
  }
  
}
