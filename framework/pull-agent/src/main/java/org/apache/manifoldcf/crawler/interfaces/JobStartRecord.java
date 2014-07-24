/* $Id: JobStartRecord.java 988245 2010-08-23 18:39:35Z kwright $ */

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


/** This class is a paper object which contains a job ID and a last job start time.
*/
public class JobStartRecord extends JobRecord
{
  public static final String _rcsid = "@(#)$Id: JobStartRecord.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The last seeding version */
  protected final String seedingVersionString;
  /** The requestMinimum flag */
  protected final boolean requestMinimum;
  /** The fail time, or -1L if none */
  protected final long failTime;
  /** The fail count, or -1 if none */
  protected final int failRetryCount;

  /** Constructor.
  */
  public JobStartRecord(Long jobID, String seedingVersionString, boolean requestMinimum, long failTime, int failRetryCount)
  {
    super(jobID);
    this.seedingVersionString = seedingVersionString;
    this.requestMinimum = requestMinimum;
    this.failTime = failTime;
    this.failRetryCount = failRetryCount;
  }

  /** Get the seeding version string.
  *@return the string.
  */
  public String getSeedingVersionString()
  {
    return seedingVersionString;
  }

  /** Get the requestMinimum flag.
  *@return the flag.
  */
  public boolean getRequestMinimum()
  {
    return requestMinimum;
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
