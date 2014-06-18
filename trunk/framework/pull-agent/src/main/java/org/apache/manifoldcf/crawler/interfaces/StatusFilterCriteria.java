/* $Id: StatusFilterCriteria.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.interfaces.*;

/** Class which describes specification of history records to include in a report.
*/
public class StatusFilterCriteria
{
  public static final String _rcsid = "@(#)$Id: StatusFilterCriteria.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The set of jobs to match. */
  protected Long[] ourJobs;
  /** The lowest time of interest. */
  protected long nowTime;
  /** The regular expression string to match the doucment identifier. */
  protected RegExpCriteria identifierMatchObject;
  /** Matching states */
  protected int[] matchingStates;
  /** Matching statuses */
  protected int[] matchingStatuses;

  /** Constructor.
  */
  public StatusFilterCriteria(Long[] ourJobs, long nowTime, RegExpCriteria identifierMatchObject,
    int[] matchingStates, int[] matchingStatuses)
  {
    this.ourJobs = ourJobs;
    this.nowTime = nowTime;
    this.identifierMatchObject = identifierMatchObject;
    this.matchingStates = matchingStates;
    this.matchingStatuses = matchingStatuses;
  }

  /** Get the desired activities criteria.
  */
  public Long[] getJobs()
  {
    return ourJobs;
  }

  /** Get the "now" time
  */
  public long getNowTime()
  {
    return nowTime;
  }

  /** Get the regular expression to match the entity identifier.
  */
  public RegExpCriteria getIdentifierMatch()
  {
    return identifierMatchObject;
  }

  /** Get the match states
  */
  public int[] getMatchingStates()
  {
    return matchingStates;
  }

  /** Get the match statuses
  */
  public int[] getMatchingStatuses()
  {
    return matchingStatuses;
  }

}
