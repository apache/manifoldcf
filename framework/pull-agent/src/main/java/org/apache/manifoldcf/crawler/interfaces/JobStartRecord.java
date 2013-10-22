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

  /** The last synch time */
  protected final long synchTime;
  /** The requestMinimum flag */
  protected final boolean requestMinimum;

  /** Constructor.
  */
  public JobStartRecord(Long jobID, long synchTime, boolean requestMinimum)
  {
    super(jobID);
    this.synchTime = synchTime;
    this.requestMinimum = requestMinimum;
  }

  /** Get the synch time.
  *@return the time.
  */
  public long getSynchTime()
  {
    return synchTime;
  }

  /** Get the requestMinimum flag.
  *@return the flag.
  */
  public boolean getRequestMinimum()
  {
    return requestMinimum;
  }
  
}
