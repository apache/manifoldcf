/* $Id: IHistoryActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that manage history information.
*/
public interface IHistoryActivity
{
  public static final String _rcsid = "@(#)$Id: IHistoryActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  // General result codes.  Use these rather than inventing your own, where reasonable.
  public static final String EXCLUDED_URL = IOutputHistoryActivity.EXCLUDED_URL;
  public static final String EXCLUDED_LENGTH = IOutputHistoryActivity.EXCLUDED_LENGTH;
  public static final String EXCLUDED_MIMETYPE = IOutputHistoryActivity.EXCLUDED_MIMETYPE;
  public static final String EXCLUDED_DATE = IOutputHistoryActivity.EXCLUDED_DATE;
  public static final String EXCLUDED_CONTENT = IOutputHistoryActivity.EXCLUDED_CONTENT;

  /**
   * Use this result code when you get URL value from repository and it is not valid.
   */
  public static final String BAD_URL = "BADURL";
  /**
   * Use this result code when you get URL value from repository and it is null.
   */
  public static final String NULL_URL = "NULLURL";
  /** Record time-stamped information about the activity of the connector.
  *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
  *       activity has an associated time; the startTime field records when the activity began.  A null value
  *       indicates that the start time and the finishing time are the same.
  *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
  *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
  *       "fetch document" activity.  Cannot be null.
  *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
  *@param entityIdentifier is a (possibly long) string which identifies the object involved in the history record.
  *       The interpretation of this field will differ from connector to connector.  May be null.
  *@param resultCode contains a terse description of the result of the activity.  The description is limited in
  *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
  *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
  *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
  *@param childIdentifiers is a set of child entity identifiers associated with this activity.  May be null.
  */
  public void recordActivity(Long startTime, String activityType, Long dataSize,
    String entityIdentifier, String resultCode, String resultDescription, String[] childIdentifiers)
    throws ManifoldCFException;

}
