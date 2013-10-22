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
public class JobRecord
{
  public static final String _rcsid = "@(#)$Id$";

  /** The job id. */
  protected final Long jobID;
  /** Whether this job was started or not */
  protected boolean wasStarted = false;

  /** Constructor.
  */
  public JobRecord(Long jobID)
  {
    this.jobID = jobID;
  }

  /** Get the job ID.
  *@return the id.
  */
  public Long getJobID()
  {
    return jobID;
  }

  /** Note that the job was started.
  */
  public void noteStarted()
  {
    wasStarted = true;
  }

  /** Check whether job was started.
  *@return true if started.
  */
  public boolean wasStarted()
  {
    return wasStarted;
  }

}
