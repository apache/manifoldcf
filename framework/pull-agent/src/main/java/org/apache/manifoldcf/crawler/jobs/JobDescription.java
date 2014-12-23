/* $Id: JobDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This is a paper object describing a job.
* Each job in the lcf framework has:
* - an identifier;
* - a description;
* - a repository connection;
* - one of a number of scheduling options: starting every n hours/days/weeks/months, on specific dates, or "continuous" (which basically
*   establishes a priority queue based on modification frequency);
* - "seeds" (or starting points), which are the places that scanning begins.
* Also remember that since incremental deletion must occur on a job-by-job basis, the scanning data also records the job that
* performed the scan, so that each job can rescan previous ingested data, and delete documents that have been removed.
*/
public class JobDescription implements IJobDescription
{
  public static final String _rcsid = "@(#)$Id: JobDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  // Data
  protected boolean isNew = true;
  protected Long id = null;
  protected String description = null;
  protected String connectionName = null;
  protected final List<PipelineStage> pipelineStages = new ArrayList<PipelineStage>();
  protected final List<Notification> notifications = new ArrayList<Notification>();
  protected int type = TYPE_CONTINUOUS;
  protected int startMethod = START_WINDOWBEGIN;
  protected int priority = 5;

  // Absolute job-triggering times
  protected ScheduleList scheduleList = new ScheduleList();

  // Throttle
  protected Float rate = null;

  // Default interval for continuous crawling
  protected Long interval = new Long(1000L*3600L*24L);            // 1 day is the default

  // Maximum interval for continuous crawling
  protected Long maxInterval = null;
  
  // Document expiration time for this job, in milliseconds
  protected Long expiration = null;                       // Never is the default

  // Default reseed interval for continuous crawling
  protected Long reseedInterval = new Long(60L * 60L * 1000L);    // 1 hour is the default

  // Document specification
  protected Specification documentSpecification = new Specification();

  // Hop count filters.
  protected HashMap hopCountFilters = new HashMap();

  // Hopcount mode
  protected int hopcountMode = HOPCOUNT_ACCURATE;

  // Read-only mode
  protected boolean readOnly = false;


  /** Duplicate method, with optional "readonly" flag.
  */
  public JobDescription duplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    // Make a new copy; we'll label it as readonly or not based on the input flag
    JobDescription rval = new JobDescription();
    rval.id = id;
    rval.isNew = isNew;
    rval.connectionName = connectionName;
    // Direct modification of this object is possible - so it also has to know if it is read-only!!
    rval.documentSpecification = documentSpecification.duplicate(readOnly);
    for (PipelineStage pipelineStage : pipelineStages)
    {
      rval.pipelineStages.add(new PipelineStage(pipelineStage.getPrerequisiteStage(),
        pipelineStage.getIsOutput(),
        pipelineStage.getConnectionName(),
        pipelineStage.getDescription(),
        pipelineStage.getSpecification().duplicate(readOnly)));
    }
    for (Notification notification : notifications)
    {
      rval.notifications.add(new Notification(notification.getConnectionName(),
        notification.getDescription(),
        notification.getSpecification().duplicate(readOnly)));
    }
    rval.description = description;
    rval.type = type;
    // No direct modification of this object is possible
    rval.scheduleList = scheduleList.duplicate();
    rval.interval = interval;
    rval.maxInterval = maxInterval;
    rval.expiration = expiration;
    rval.reseedInterval = reseedInterval;
    rval.rate = rate;
    rval.priority = priority;
    rval.startMethod = startMethod;
    rval.hopcountMode = hopcountMode;
    Iterator iter = hopCountFilters.keySet().iterator();
    while (iter.hasNext())
    {
      String linkType = (String)iter.next();
      Long maxHops = (Long)hopCountFilters.get(linkType);
      rval.hopCountFilters.put(linkType,maxHops);
    }
    rval.readOnly = readOnly;
    return rval;
  }

  /** Make the description "read only".  This must be done after the object has been complete specified.
  * Once a document is read-only, it cannot be made writable without duplication.
  */
  public void makeReadOnly()
  {
    if (readOnly)
      return;
    readOnly = true;
    for (PipelineStage pipelineStage : pipelineStages)
    {
      pipelineStage.getSpecification().makeReadOnly();
    }
    for (Notification notification : notifications)
    {
      notification.getSpecification().makeReadOnly();
    }
    documentSpecification.makeReadOnly();
  }

  /** Set isnew.
  *@param isNew is true if the object is new.
  */
  public void setIsNew(boolean isNew)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.isNew = isNew;
  }

  /** Get isnew.
  *@return true if the object is new.
  */
  @Override
  public boolean getIsNew()
  {
    return isNew;
  }

  /** Set the id.
  *@param id is the id.
  */
  public void setID(Long id)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.id = id;
  }

  /** Get the id.
  *@return the id.
  */
  @Override
  public Long getID()
  {
    return id;
  }

  /** Set the description.
  *@param description is the description.
  */
  @Override
  public void setDescription(String description)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.description = description;
  }

  /** Get the description.
  *@return the description
  */
  @Override
  public String getDescription()
  {
    return description;
  }

  /** Set the connection name.
  *@param connectionName is the connection name.
  */
  @Override
  public void setConnectionName(String connectionName)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.connectionName = connectionName;
  }

  /** Get the connection name.
  *@return the connection name.
  */
  @Override
  public String getConnectionName()
  {
    return connectionName;
  }

  /** Clear pipeline connections */
  @Override
  public void clearPipeline()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    pipelineStages.clear();
  }
  
  /** Add a pipeline connection.
  *@param prerequisiteStage is the prerequisite stage number for this connection, or -1 if there is none.
  *@param isOutput is true if the pipeline stage is an output connection.
  *@param pipelineStageConnectionName is the name of the pipeline connection to add.
  *@param pipelineStageDescription is a description of the pipeline stage being added.
  *@return the empty output specification for this pipeline stage.
  */
  @Override
  public Specification addPipelineStage(int prerequisiteStage, boolean isOutput, String pipelineStageConnectionName, String pipelineStageDescription)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    PipelineStage ps = new PipelineStage(prerequisiteStage,isOutput,pipelineStageConnectionName,pipelineStageDescription);
    pipelineStages.add(ps);
    return ps.getSpecification();
  }
  
  /** Get a count of pipeline stages */
  @Override
  public int countPipelineStages()
  {
    return pipelineStages.size();
  }
  
  /** Insert a new pipeline stage.
  *@param index is the index to insert pipeline stage before
  *@param pipelineStageConnectionName is the connection name.
  *@param pipelineStageDescription is the description.
  *@return the newly-created output specification.
  */
  @Override
  public Specification insertPipelineStage(int index, boolean isOutput, String pipelineStageConnectionName, String pipelineStageDescription)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    // What we do here depends on the kind of stage we're inserting.
    // Both kinds take the current stage's prerequisite as their own.  But what happens to the current stage will
    // differ as to whether its reference changes or not.
    PipelineStage currentStage = pipelineStages.get(index);
    PipelineStage ps = new PipelineStage(currentStage.getPrerequisiteStage(),isOutput,pipelineStageConnectionName,pipelineStageDescription);
    pipelineStages.add(index,ps);
    currentStage.adjustReplacedStage(index,isOutput);
    // Adjust stage back-references
    int stage = index + 2;
    while (stage < pipelineStages.size())
    {
      pipelineStages.get(stage).adjustForInsert(index);
      stage++;
    }
    return ps.getSpecification();
  }
  
  /** Get the prerequisite stage number for a pipeline stage.
  *@param index is the index of the pipeline stage to get.
  *@return the preceding stage number for that stage, or -1 if there is none.
  */
  @Override
  public int getPipelineStagePrerequisite(int index)
  {
    return pipelineStages.get(index).getPrerequisiteStage();
  }
  
  /** Check if a pipeline stage is an output connection.
  *@param index is the index of the pipeline stage to check.
  *@return true if it is an output connection.
  */
  @Override
  public boolean getPipelineStageIsOutputConnection(int index)
  {
    return pipelineStages.get(index).getIsOutput();
  }

  /** Get a specific pipeline connection name.
  *@param index is the index of the pipeline stage whose connection name to get.
  *@return the name of the connection.
  */
  @Override
  public String getPipelineStageConnectionName(int index)
  {
    return pipelineStages.get(index).getConnectionName();
  }
  
  /** Get a specific pipeline stage description.
  *@param index is the index of the pipeline stage whose description to get.
  *@return the name of the connection.
  */
  @Override
  public String getPipelineStageDescription(int index)
  {
    return pipelineStages.get(index).getDescription();
  }

  /** Get a specific pipeline stage specification.
  *@param index is the index of the pipeline stage whose specification is needed.
  *@return the specification for the connection.
  */
  @Override
  public Specification getPipelineStageSpecification(int index)
  {
    return pipelineStages.get(index).getSpecification();
  }

  /** Delete a pipeline stage.
  *@param index is the index of the pipeline stage to delete.
  */
  @Override
  public void deletePipelineStage(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    PipelineStage ps = pipelineStages.remove(index);
    int stage = index;
    while (stage < pipelineStages.size())
    {
      pipelineStages.get(stage).adjustForDelete(index,ps.getPrerequisiteStage());
      stage++;
    }
  }

  /** Clear notification connections.
  */
  @Override
  public void clearNotifications()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    notifications.clear();
  }
  
  /** Add a notification.
  *@param notificationConnectionName is the name of the notification connection to add.
  *@param notificationDescription is a description of the notification being added.
  *@return the empty specification for this notification.
  */
  @Override
  public Specification addNotification(String notificationConnectionName, String notificationDescription)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    Notification ps = new Notification(notificationConnectionName,notificationDescription);
    notifications.add(ps);
    return ps.getSpecification();
  }
  
  /** Get a count of pipeline connections.
  *@return the current number of pipeline connections.
  */
  @Override
  public int countNotifications()
  {
    return notifications.size();
  }
  
  /** Get a specific notification connection name.
  *@param index is the index of the notification whose connection name to get.
  *@return the name of the connection.
  */
  @Override
  public String getNotificationConnectionName(int index)
  {
    return notifications.get(index).getConnectionName();
  }

  /** Get a specific notification description.
  *@param index is the index of the notification whose description to get.
  *@return the name of the connection.
  */
  @Override
  public String getNotificationDescription(int index)
  {
    return notifications.get(index).getDescription();
  }

  /** Get a specific notification specification.
  *@param index is the index of the notification whose specification is needed.
  *@return the specification for the connection.
  */
  @Override
  public Specification getNotificationSpecification(int index)
  {
    return notifications.get(index).getSpecification();
  }

  /** Delete a notification.
  *@param index is the index of the notification to delete.
  */
  @Override
  public void deleteNotification(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    notifications.remove(index);
  }
  
  /** Insert a new notification.
  *@param index is the index to insert pipeline stage before
  *@param notificationConnectionName is the connection name.
  *@param notificationDescription is the description.
  *@return the newly-created output specification.
  */
  @Override
  public Specification insertNotification(int index, String notificationConnectionName, String notificationDescription)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    // What we do here depends on the kind of stage we're inserting.
    // Both kinds take the current stage's prerequisite as their own.  But what happens to the current stage will
    // differ as to whether its reference changes or not.
    Notification ps = new Notification(notificationConnectionName,notificationDescription);
    notifications.add(index,ps);
    return ps.getSpecification();
  }
  
  /** Set the job type.
  *@param type is the type (as an integer).
  */
  @Override
  public void setType(int type)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.type = type;
  }

  /** Get the job type.
  *@return the type (as an integer).
  */
  @Override
  public int getType()
  {
    return type;
  }

  /** Set the job's start method.
  *@param startMethod is the start description.
  */
  @Override
  public void setStartMethod(int startMethod)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.startMethod = startMethod;
  }

  /** Get the job's start method.
  *@return the start method.
  */
  @Override
  public int getStartMethod()
  {
    return startMethod;
  }


  // For day-specific jobs.  These occur at a given time that matches the specifications.
  // The specifications set certain criteria (specific hours, days of the week, etc.)

  /** Clear all the scheduling records.
  */
  @Override
  public void clearScheduleRecords()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    scheduleList.clear();
  }

  /** Add a record.
  *@param record is the record to add.
  */
  @Override
  public void addScheduleRecord(ScheduleRecord record)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    scheduleList.addRecord(record);
  }

  /** Get the number of schedule records.
  *@return the count.
  */
  @Override
  public int getScheduleRecordCount()
  {
    return scheduleList.getRecordCount();
  }

  /** Get a specified schedule record.
  *@param index is the record number.
  *@return the record.
  */
  @Override
  public ScheduleRecord getScheduleRecord(int index)
  {
    return scheduleList.getRecord(index);
  }

  /** Delete a specified schedule record.
  *@param index is the record number.
  */
  @Override
  public void deleteScheduleRecord(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    scheduleList.deleteRecord(index);
  }


  // For continuous jobs
  // This is the rescheduling interval to use when no calculated interval is known

  /** Set the rescheduling interval, in milliseconds.
  *@param interval is the default interval, or null for infinite.
  */
  @Override
  public void setInterval(Long interval)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.interval = interval;
  }

  /** Get the rescheduling interval, in milliseconds.
  *@return the default interval, or null for infinite.
  */
  @Override
  public Long getInterval()
  {
    return interval;
  }

  /** Set the maximum rescheduling interval, in milliseconds, or null if forever.
  *@param interval is the maximum interval.
  */
  @Override
  public void setMaxInterval(Long interval)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.maxInterval = interval;
  }

  /** Get the maximum rescheduling interval, in milliseconds.
  *@return the max interval, or null if forever.
  */
  @Override
  public Long getMaxInterval()
  {
    return maxInterval;
  }

  /** Set the expiration time, in milliseconds.
  *@param time is the maximum expiration time of a document, in milliseconds, or null if none.
  */
  @Override
  public void setExpiration(Long time)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    expiration = time;
  }

  /** Get the expiration time, in milliseconds.
  *@return the maximum expiration time of a document, or null if none.
  */
  @Override
  public Long getExpiration()
  {
    return expiration;
  }

  /** Set the reseeding interval, in milliseconds.
  *@param interval is the interval, or null for infinite.
  */
  @Override
  public void setReseedInterval(Long interval)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.reseedInterval = interval;
  }

  /** Get the reseeding interval, in milliseconds.
  *@return the interval, or null if infinite.
  */
  @Override
  public Long getReseedInterval()
  {
    return reseedInterval;
  }

  /** Get the document specification.
  *@return the document specification object.
  */
  @Override
  public Specification getSpecification()
  {
    return documentSpecification;
  }


  /** Set the job priority.  This is a simple integer between 1 and 10, where
  * 1 is the highest priority.
  *@param priority is the priority.
  */
  @Override
  public void setPriority(int priority)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.priority = priority;
  }

  /** Get the job priority.
  *@return the priority (a number between 1 and 10).
  */
  @Override
  public int getPriority()
  {
    return priority;
  }

  // Hopcount filters

  /** Get the set of hopcount filters the job has defined.
  *@return the set as a map, keyed by Strings and containing Longs.
  */
  @Override
  public Map getHopCountFilters()
  {
    return (Map)hopCountFilters.clone();
  }

  /** Clear the set of hopcount filters for the job.
  */
  @Override
  public void clearHopCountFilters()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    hopCountFilters.clear();
  }


  /** Add a hopcount filter to the job.
  *@param linkType is the type of link the filter applies to.
  *@param maxHops is the maximum hop count.  Use null to remove a filter.
  */
  @Override
  public void addHopCountFilter(String linkType, Long maxHops)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    hopCountFilters.put(linkType,maxHops);
  }

  /** Get the hopcount mode. */
  @Override
  public int getHopcountMode()
  {
    return hopcountMode;
  }

  /** Set the hopcount mode. */
  @Override
  public void setHopcountMode(int mode)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    hopcountMode = mode;
  }

  protected static class Notification
  {
    protected final String connectionName;
    protected final String description;
    protected final Specification specification;
    
    public Notification(String connectionName, String description)
    {
      this.connectionName = connectionName;
      this.description = description;
      this.specification = new Specification();
    }

    public Notification(String connectionName, String description, Specification spec)
    {
      this.connectionName = connectionName;
      this.description = description;
      this.specification = spec;
    }

    public Specification getSpecification()
    {
      return specification;
    }
    
    public String getConnectionName()
    {
      return connectionName;
    }
    
    public String getDescription()
    {
      return description;
    }

  }
  
  protected static class PipelineStage
  {
    protected int prerequisiteStage;
    protected final boolean isOutput;
    protected final String connectionName;
    protected final String description;
    protected final Specification specification;
    
    public PipelineStage(int prerequisiteStage, boolean isOutput, String connectionName, String description)
    {
      this.prerequisiteStage = prerequisiteStage;
      this.isOutput = isOutput;
      this.connectionName = connectionName;
      this.description = description;
      this.specification = new Specification();
    }

    public PipelineStage(int prerequisiteStage, boolean isOutput, String connectionName, String description, Specification spec)
    {
      this.prerequisiteStage = prerequisiteStage;
      this.isOutput = isOutput;
      this.connectionName = connectionName;
      this.description = description;
      this.specification = spec;
    }
    
    public void adjustReplacedStage(int index, boolean isOutput)
    {
      if (!isOutput)
	prerequisiteStage = index;
      else
	adjustForInsert(index);
    }
    
    public void adjustForInsert(int index)
    {
      if (prerequisiteStage >= index)
      {
        prerequisiteStage++;
      }
    }
    
    public void adjustForDelete(int index, int prerequisite)
    {
      if (prerequisiteStage > index)
        prerequisiteStage--;
      else if (prerequisiteStage == index)
        prerequisiteStage = prerequisite;
    }
    
    public Specification getSpecification()
    {
      return specification;
    }
    
    public int getPrerequisiteStage()
    {
      return prerequisiteStage;
    }
    
    public boolean getIsOutput()
    {
      return isOutput;
    }
    
    public String getConnectionName()
    {
      return connectionName;
    }
    
    public String getDescription()
    {
      return description;
    }
  }
  
}
