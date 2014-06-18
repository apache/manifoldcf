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


/** This class is a paper object which contains a job ID.
*/
public class JobNotifyRecord extends JobRecord
{
  public static final String _rcsid = "@(#)$Id$";

  /** Fail time; -1L if none currently set */
  protected final long failTime;
  /** Fail retry count; -1 if none currently set */
  protected final int failRetryCount;
  
  /** Constructor.
  */
  public JobNotifyRecord(Long jobID, long failTime, int failRetryCount)
  {
    super(jobID);
    this.failTime = failTime;
    this.failRetryCount = failRetryCount;
  }

  /** Get the hard fail time.
  *@return the fail time in ms since epoch, or -1L if none.
  */
  public long getFailTime()
  {
    return failTime;
  }

  /** Get the hard fail retry count.
  *@return the fail retry count, or -1 if none.
  */
  public int getFailRetryCount()
  {
    return failRetryCount;
  }

}
