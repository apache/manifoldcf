/* $Id: FilterCriteria.java 988245 2010-08-23 18:39:35Z kwright $ */

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
public class FilterCriteria
{
  public static final String _rcsid = "@(#)$Id: FilterCriteria.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The set of activities to match. */
  protected String[] activityTypes;
  /** The lowest time of interest. */
  protected Long startTime;
  /** The highest time of interest, plus 1. */
  protected Long endTime;
  /** The regular expression string to match the entity identifier. */
  protected RegExpCriteria entityMatch;
  /** The regular expression string to match the resultcode. */
  protected RegExpCriteria resultCodeMatch;

  /** Constructor.
  */
  public FilterCriteria(String[] activityTypes, Long startTime, Long endTime, RegExpCriteria entityMatch,
    RegExpCriteria resultCodeMatch)
  {
    this.activityTypes = activityTypes;
    this.startTime = startTime;
    this.endTime = endTime;
    this.entityMatch = entityMatch;
    this.resultCodeMatch = resultCodeMatch;
  }

  /** Get the desired activities criteria.
  */
  public String[] getActivities()
  {
    return activityTypes;
  }

  /** Get desired start time criteria.
  */
  public Long getStartTime()
  {
    return startTime;
  }


  /** Get desired end time criteria.
  */
  public Long getEndTime()
  {
    return endTime;
  }

  /** Get the regular expression to match the entity identifier.
  */
  public RegExpCriteria getEntityMatch()
  {
    return entityMatch;
  }

  /** Get the regular expression to match the result code.
  */
  public RegExpCriteria getResultCodeMatch()
  {
    return resultCodeMatch;
  }



}
