/* $Id: IIngestLogger.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface describes logging support that will be called by the ingestion engine to record its
* activities interacting with the ingestion API.
*/
public interface IIngestLogger
{

  /** Record time-stamped information about an ingestion attempt.
  *@param documentIdentifier is the internal document identifier being described.
  *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
  *       ingestion attempt has an associated time; the startTime field records when the activity began.  A null value
  *       indicates that the start time and the finishing time are the same.
  *@param dataSize is the number of bytes of data ingested, or null if not applicable.
  *@param entityIdentifier is a (possibly long) string which identifies the object involved in the ingestion attempt.
  *@param resultCode contains a terse description of the result of the ingestion.  The description is limited in
  *       size to 255 characters.
  *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
  *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
  */
  public void recordIngestionAttempt(String documentIdentifier, Long startTime, Long dataSize,
    String entityIdentifier, String resultCode, String resultDescription)
    throws ManifoldCFException;

  /** Record time-stamped information about a deletion attempt.
  *@param documentIdentifier is the internal document identifier being described.
  *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
  *       deletion attempt has an associated time; the startTime field records when the activity began.  A null value
  *       indicates that the start time and the finishing time are the same.
  *@param entityIdentifier is a (possibly long) string which identifies the object involved in the deletion attempt.
  *@param resultCode contains a terse description of the result of the ingestion.  The description is limited in
  *       size to 255 characters.
  *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
  *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
  */
  public void recordDeletionAttempt(String documentIdentifier, Long startTime,
    String entityIdentifier, String resultCode, String resultDescription)
    throws ManifoldCFException;

}
