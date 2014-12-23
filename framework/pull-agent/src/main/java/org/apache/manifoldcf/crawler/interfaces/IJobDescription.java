/* $Id: IJobDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This is a paper object describing a job.
* Each job in lcf has:
* - an identifier;
* - a description;
* - a repository connection;
* - one of a number of scheduling options: starting every n hours/days/weeks/months, on specific dates, or "continuous" (which basically
*   establishes a priority queue based on modification frequency);
* - "seeds" (or starting points), which are the places that scanning begins.
* Also remember that since incremental deletion must occur on a job-by-job basis, the scanning data also records the job that
* performed the scan, so that each job can rescan previous ingested data, and delete documents that have been removed.
*/
public interface IJobDescription
{
  public static final String _rcsid = "@(#)$Id: IJobDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  // Kinds of document scheduling that are allowed
  public final static int TYPE_CONTINUOUS = 0;    // Never stops, just reschedules individual documents indefinitely
  public final static int TYPE_SPECIFIED = 1;     // Stops when out of documents to process; never reschedules documents

  // Kinds of job starting that are allowed
  public final static int START_WINDOWBEGIN = 0;  // Only start at the beginning of a window
  public final static int START_WINDOWINSIDE = 1; // Restart even inside a window.
  public final static int START_DISABLE = 2;      // Disable this job from starting.

  // Hopcount models
  public final static int HOPCOUNT_ACCURATE = 0;  // Calculate an accurate hopcount, taking into account deletions
  public final static int HOPCOUNT_NODELETE = 1;  // Hopcount does not reflect any deletions which may have taken place
  public final static int HOPCOUNT_NEVERDELETE =2;        // This job will never become HOPCOUNT_ACCURATE.

  /** Get isnew.
  *@return true if the object is new.
  */
  public boolean getIsNew();

  /** Get the id.
  *@return the id.
  */
  public Long getID();

  /** Set the description.
  *@param description is the description.
  */
  public void setDescription(String description);

  /** Get the description.
  *@return the description
  */
  public String getDescription();

  /** Set the connection name.
  *@param connectionName is the connection name.
  */
  public void setConnectionName(String connectionName);

  /** Get the connection name.
  *@return the connection name.
  */
  public String getConnectionName();

  /** Clear pipeline connections.
  */
  public void clearPipeline();
  
  /** Add a pipeline connection.
  *@param prerequisiteStage is the prerequisite stage number for this connection, or -1 if there is none.
  *@param isOutput is true if the pipeline stage is an output connection.
  *@param pipelineStageConnectionName is the name of the pipeline connection to add.
  *@param pipelineStageDescription is a description of the pipeline stage being added.
  *@return the empty output specification for this pipeline stage.
  */
  public Specification addPipelineStage(int prerequisiteStage, boolean isOutput, String pipelineStageConnectionName, String pipelineStageDescription);
  
  /** Get a count of pipeline connections.
  *@return the current number of pipeline connections.
  */
  public int countPipelineStages();
  
  /** Get the prerequisite stage number for a pipeline stage.
  *@param index is the index of the pipeline stage to get.
  *@return the preceding stage number for that stage, or -1 if there is none.
  */
  public int getPipelineStagePrerequisite(int index);
  
  /** Check if a pipeline stage is an output connection.
  *@param index is the index of the pipeline stage to check.
  *@return true if it is an output connection.
  */
  public boolean getPipelineStageIsOutputConnection(int index);
  
  /** Get a specific pipeline connection name.
  *@param index is the index of the pipeline stage whose connection name to get.
  *@return the name of the connection.
  */
  public String getPipelineStageConnectionName(int index);

  /** Get a specific pipeline stage description.
  *@param index is the index of the pipeline stage whose description to get.
  *@return the name of the connection.
  */
  public String getPipelineStageDescription(int index);

  /** Get a specific pipeline stage specification.
  *@param index is the index of the pipeline stage whose specification is needed.
  *@return the specification for the connection.
  */
  public Specification getPipelineStageSpecification(int index);

  /** Delete a pipeline stage.
  *@param index is the index of the pipeline stage to delete.
  */
  public void deletePipelineStage(int index);
  
  /** Insert a new pipeline stage.
  *@param index is the index to insert pipeline stage before
  *@param pipelineStageConnectionName is the connection name.
  *@param pipelineStageDescription is the description.
  *@return the newly-created output specification.
  */
  public Specification insertPipelineStage(int index, boolean isOutput, String pipelineStageConnectionName, String pipelineStageDescription);

  /** Clear notification connections.
  */
  public void clearNotifications();
  
  /** Add a notification.
  *@param notificationConnectionName is the name of the notification connection to add.
  *@param notificationDescription is a description of the notification being added.
  *@return the empty specification for this notification.
  */
  public Specification addNotification(String notificationConnectionName, String notificationDescription);
  
  /** Get a count of pipeline connections.
  *@return the current number of pipeline connections.
  */
  public int countNotifications();
  
  /** Get a specific notification connection name.
  *@param index is the index of the notification whose connection name to get.
  *@return the name of the connection.
  */
  public String getNotificationConnectionName(int index);

  /** Get a specific notification description.
  *@param index is the index of the notification whose description to get.
  *@return the name of the connection.
  */
  public String getNotificationDescription(int index);

  /** Get a specific notification specification.
  *@param index is the index of the notification whose specification is needed.
  *@return the specification for the connection.
  */
  public Specification getNotificationSpecification(int index);

  /** Delete a notification.
  *@param index is the index of the notification to delete.
  */
  public void deleteNotification(int index);
  
  /** Insert a new notification.
  *@param index is the index to insert pipeline stage before
  *@param notificationConnectionName is the connection name.
  *@param notificationDescription is the description.
  *@return the newly-created output specification.
  */
  public Specification insertNotification(int index, String notificationConnectionName, String notificationDescription);
  
  /** Set the job type.
  *@param type is the type (as an integer).
  */
  public void setType(int type);

  /** Get the job type.
  *@return the type (as an integer).
  */
  public int getType();

  /** Set the job's start method.
  *@param startMethod is the start description.
  */
  public void setStartMethod(int startMethod);

  /** Get the job's start method.
  *@return the start method.
  */
  public int getStartMethod();


  // For time-specified (scheduled) jobs.  These occur at a given time that matches the specifications.
  // The specifications set certain criteria (specific hours, days of the week, etc.)

  /** Clear all the scheduling records.
  */
  public void clearScheduleRecords();

  /** Add a record.
  *@param record is the record to add.
  */
  public void addScheduleRecord(ScheduleRecord record);

  /** Get the number of schedule records.
  *@return the count.
  */
  public int getScheduleRecordCount();

  /** Get a specified schedule record.
  *@param index is the record number.
  *@return the record.
  */
  public ScheduleRecord getScheduleRecord(int index);

  /** Delete a specified schedule record.
  *@param index is the record number.
  */
  public void deleteScheduleRecord(int index);


  // For continuous jobs
  // This is the rescheduling interval to use when no calculated interval is known

  /** Set the rescheduling interval, in milliseconds, or null if forever.
  *@param interval is the default interval.
  */
  public void setInterval(Long interval);

  /** Get the rescheduling interval, in milliseconds.
  *@return the default interval, or null if forever.
  */
  public Long getInterval();

  /** Set the maximum rescheduling interval, in milliseconds, or null if forever.
  *@param interval is the maximum interval.
  */
  public void setMaxInterval(Long interval);

  /** Get the maximum rescheduling interval, in milliseconds.
  *@return the max interval, or null if forever.
  */
  public Long getMaxInterval();

  /** Set the expiration time, in milliseconds.
  *@param time is the maximum expiration time of a document, in milliseconds, or null if none.
  */
  public void setExpiration(Long time);

  /** Get the expiration time, in milliseconds.
  *@return the maximum expiration time of a document, or null if none.
  */
  public Long getExpiration();

  /** Set the reseeding interval, in milliseconds.
  *@param interval is the interval, or null for infinite.
  */
  public void setReseedInterval(Long interval);

  /** Get the reseeding interval, in milliseconds.
  *@return the interval, or null if infinite.
  */
  public Long getReseedInterval();

  // Document specification

  /** Get the document specification (which can be modified).
  *@return the specification.
  */
  public Specification getSpecification();


  // Priority

  /** Set the job priority.  This is a simple integer between 1 and 10, where
  * 1 is the highest priority.
  *@param priority is the priority.
  */
  public void setPriority(int priority);

  /** Get the job priority.
  *@return the priority (a number between 1 and 10).
  */
  public int getPriority();

  // Hopcount filters

  /** Get the set of hopcount filters the job has defined.
  *@return the set as a map, keyed by Strings and containing Longs.
  */
  public Map getHopCountFilters();

  /** Clear the set of hopcount filters for the job.
  */
  public void clearHopCountFilters();

  /** Add a hopcount filter to the job.
  *@param linkType is the type of link the filter applies to.
  *@param maxHops is the maximum hop count.  Use null to remove a filter.
  */
  public void addHopCountFilter(String linkType, Long maxHops);

  /** Get the hopcount mode. */
  public int getHopcountMode();

  /** Set the hopcount mode. */
  public void setHopcountMode(int mode);

}
