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
  protected String outputConnectionName = null;
  protected String connectionName = null;
  protected int type = TYPE_CONTINUOUS;
  protected int startMethod = START_WINDOWBEGIN;
  protected int priority = 5;

  // Absolute job-triggering times
  protected ScheduleList scheduleList = new ScheduleList();

  // Throttle
  protected Float rate = null;

  // Default interval for continuous crawling
  protected Long interval = new Long(1000L*3600L*24L);            // 1 day is the default

  // Document expiration time for this job, in milliseconds
  protected Long expiration = null;                       // Never is the default

  // Default reseed interval for continuous crawling
  protected Long reseedInterval = new Long(60L * 60L * 1000L);    // 1 hour is the default

  // Output specification
  protected OutputSpecification outputSpecification = new OutputSpecification();

  // Document specification
  protected DocumentSpecification documentSpecification = new DocumentSpecification();

  // Hop count filters.
  protected HashMap hopCountFilters = new HashMap();

  // Hopcount mode
  protected int hopcountMode = HOPCOUNT_ACCURATE;

  // Forced metadata
  protected Map<String,Set<String>> forcedMetadata = new HashMap<String,Set<String>>();
  
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
    rval.outputConnectionName = outputConnectionName;
    rval.connectionName = connectionName;
    rval.description = description;
    rval.type = type;
    // No direct modification of this object is possible
    rval.scheduleList = scheduleList.duplicate();
    rval.interval = interval;
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
    for (String forcedParamName : forcedMetadata.keySet())
    {
      Set<String> values = forcedMetadata.get(forcedParamName);
      for (String value : values)
      {
        rval.addForcedMetadataValue(forcedParamName,value);
      }
    }
    // Direct modification of this object is possible - so it also has to know if it is read-only!!
    rval.outputSpecification = outputSpecification.duplicate(readOnly);
    // Direct modification of this object is possible - so it also has to know if it is read-only!!
    rval.documentSpecification = documentSpecification.duplicate(readOnly);
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
    outputSpecification.makeReadOnly();
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
  public Long getID()
  {
    return id;
  }

  /** Set the description.
  *@param description is the description.
  */
  public void setDescription(String description)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.description = description;
  }

  /** Get the description.
  *@return the description
  */
  public String getDescription()
  {
    return description;
  }

  /** Set the output connection name.
  *@param connectionName is the connection name.
  */
  public void setOutputConnectionName(String connectionName)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.outputConnectionName = connectionName;
  }

  /** Get the output connection name.
  *@return the output connection name.
  */
  public String getOutputConnectionName()
  {
    return outputConnectionName;
  }

  /** Set the connection name.
  *@param connectionName is the connection name.
  */
  public void setConnectionName(String connectionName)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.connectionName = connectionName;
  }

  /** Get the connection name.
  *@return the connection name.
  */
  public String getConnectionName()
  {
    return connectionName;
  }

  /** Set the job type.
  *@param type is the type (as an integer).
  */
  public void setType(int type)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.type = type;
  }

  /** Get the job type.
  *@return the type (as an integer).
  */
  public int getType()
  {
    return type;
  }

  /** Set the job's start method.
  *@param startMethod is the start description.
  */
  public void setStartMethod(int startMethod)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.startMethod = startMethod;
  }

  /** Get the job's start method.
  *@return the start method.
  */
  public int getStartMethod()
  {
    return startMethod;
  }


  // For day-specific jobs.  These occur at a given time that matches the specifications.
  // The specifications set certain criteria (specific hours, days of the week, etc.)

  /** Clear all the scheduling records.
  */
  public void clearScheduleRecords()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    scheduleList.clear();
  }

  /** Add a record.
  *@param record is the record to add.
  */
  public void addScheduleRecord(ScheduleRecord record)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    scheduleList.addRecord(record);
  }

  /** Get the number of schedule records.
  *@return the count.
  */
  public int getScheduleRecordCount()
  {
    return scheduleList.getRecordCount();
  }

  /** Get a specified schedule record.
  *@param index is the record number.
  *@return the record.
  */
  public ScheduleRecord getScheduleRecord(int index)
  {
    return scheduleList.getRecord(index);
  }

  /** Delete a specified schedule record.
  *@param index is the record number.
  */
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
  public void setInterval(Long interval)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.interval = interval;
  }

  /** Get the rescheduling interval, in milliseconds.
  *@return the default interval, or null for infinite.
  */
  public Long getInterval()
  {
    return interval;
  }

  /** Set the expiration time, in milliseconds.
  *@param time is the maximum expiration time of a document, in milliseconds, or null if none.
  */
  public void setExpiration(Long time)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    expiration = time;
  }

  /** Get the expiration time, in milliseconds.
  *@return the maximum expiration time of a document, or null if none.
  */
  public Long getExpiration()
  {
    return expiration;
  }

  /** Set the reseeding interval, in milliseconds.
  *@param interval is the interval, or null for infinite.
  */
  public void setReseedInterval(Long interval)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.reseedInterval = interval;
  }

  /** Get the reseeding interval, in milliseconds.
  *@return the interval, or null if infinite.
  */
  public Long getReseedInterval()
  {
    return reseedInterval;
  }

  /** Get the output specification.
  *@return the output specification object.
  */
  public OutputSpecification getOutputSpecification()
  {
    return outputSpecification;
  }

  /** Get the document specification.
  *@return the document specification object.
  */
  public DocumentSpecification getSpecification()
  {
    return documentSpecification;
  }


  /** Set the job priority.  This is a simple integer between 1 and 10, where
  * 1 is the highest priority.
  *@param priority is the priority.
  */
  public void setPriority(int priority)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.priority = priority;
  }

  /** Get the job priority.
  *@return the priority (a number between 1 and 10).
  */
  public int getPriority()
  {
    return priority;
  }

  // Hopcount filters

  /** Get the set of hopcount filters the job has defined.
  *@return the set as a map, keyed by Strings and containing Longs.
  */
  public Map getHopCountFilters()
  {
    return (Map)hopCountFilters.clone();
  }

  /** Clear the set of hopcount filters for the job.
  */
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
  public void addHopCountFilter(String linkType, Long maxHops)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    hopCountFilters.put(linkType,maxHops);
  }

  /** Get the hopcount mode. */
  public int getHopcountMode()
  {
    return hopcountMode;
  }

  /** Set the hopcount mode. */
  public void setHopcountMode(int mode)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    hopcountMode = mode;
  }

  // Forced metadata
  
  /** Get the forced metadata.
  *@return the set as a map, keyed by metadata name, with value a set of strings.
  */
  public Map<String,Set<String>> getForcedMetadata()
  {
    return forcedMetadata;
  }
  
  /** Clear forced metadata.
  */
  public void clearForcedMetadata()
  {
    forcedMetadata.clear();
  }
  
  /** Add a forced metadata name/value pair.
  */
  public void addForcedMetadataValue(String name, String value)
  {
    Set<String> rval = forcedMetadata.get(name);
    if (rval == null)
    {
      rval = new HashSet<String>();
      forcedMetadata.put(name,rval);
    }
    rval.add(value);
  }

}
