/* $Id: JobStatus.java 991295 2010-08-31 19:12:14Z kwright $ */

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

/** This class describes the complete status of a job.
* It is immutable.
*/
public class JobStatus
{
  public static final String _rcsid = "@(#)$Id: JobStatus.java 991295 2010-08-31 19:12:14Z kwright $";

  // Job status values
  public final static int JOBSTATUS_NOTYETRUN = 0;
  public final static int JOBSTATUS_RUNNING = 1;
  public final static int JOBSTATUS_STOPPING = 2;
  public final static int JOBSTATUS_PAUSED = 3;
  public final static int JOBSTATUS_RESUMING = 4;
  public final static int JOBSTATUS_COMPLETED = 5;
  public final static int JOBSTATUS_WINDOWWAIT = 6;
  public final static int JOBSTATUS_STARTING = 7;
  public final static int JOBSTATUS_DESTRUCTING = 8;
  public final static int JOBSTATUS_ERROR = 9;
  public final static int JOBSTATUS_ABORTING = 10;
  public final static int JOBSTATUS_RESTARTING = 11;
  public final static int JOBSTATUS_RUNNING_UNINSTALLED = 12;
  public final static int JOBSTATUS_JOBENDCLEANUP = 13;
  public final static int JOBSTATUS_JOBENDNOTIFICATION = 14;


  // Member variables.
  protected final String jobID;
  protected final String description;
  protected final int status;
  protected final long documentsInQueue;
  protected final long documentsOutstanding;
  protected final long documentsProcessed;
  protected final boolean queueCountExact;
  protected final boolean outstandingCountExact;
  protected final boolean processedCountExact;
  protected final long startTime;       // -1 if job never started
  protected final long endTime;         // -1 if job has not ended yet
  protected final String errorText;     // null if no error on previous action

  /** Constructor.
  *@param jobID is the job identifier.
  *@param description is the job description.
  *@param status is the job status.
  *@param documentsInQueue is the total number of documents currently in the document queue for the job.
  *@param documentsOutstanding is the total number of documents currently marked for processing.
  *@param documentsProcessed is the total number of documents that have been processed at least once.
  *@param startTime is time the job started (use -1 for never)
  *@param endTime is the time the job ended (use -1 for not yet)
  */
  public JobStatus(String jobID,
    String description,
    int status,
    long documentsInQueue,
    long documentsOutstanding,
    long documentsProcessed,
    boolean queueCountExact,
    boolean outstandingCountExact,
    boolean processedCountExact,
    long startTime,
    long endTime,
    String errorText)
  {
    this.jobID = jobID;
    this.description = description;
    this.status = status;
    this.documentsInQueue = documentsInQueue;
    this.documentsOutstanding = documentsOutstanding;
    this.documentsProcessed = documentsProcessed;
    this.queueCountExact = queueCountExact;
    this.outstandingCountExact = outstandingCountExact;
    this.processedCountExact = processedCountExact;
    this.startTime = startTime;
    this.endTime = endTime;
    this.errorText = errorText;
  }

  /** Get the job id.
  *@return the id.
  */
  public String getJobID()
  {
    return jobID;
  }

  /** Get the job description.
  *@return the description.
  */
  public String getDescription()
  {
    return description;
  }

  /** Get the job status.
  *@return the status.
  */
  public int getStatus()
  {
    return status;
  }

  /** Get the number of documents in the queue.
  *@return the number of documents in the queue.
  */
  public long getDocumentsInQueue()
  {
    return documentsInQueue;
  }

  /** Get whether the queue count is accurate, or an estimate.
  *@return true if accurate.
  */
  public boolean getQueueCountExact()
  {
    return queueCountExact;
  }
  
  /** Get the number of documents outstanding.
  *@return the documents that are waiting for processing.
  */
  public long getDocumentsOutstanding()
  {
    return documentsOutstanding;
  }

  /** Get whether the outstanding count is accurate, or an estimate.
  *@return true if accurate.
  */
  public boolean getOutstandingCountExact()
  {
    return outstandingCountExact;
  }

  /** Get the number of documents that have been processed at least once.
  *@return the document count.
  */
  public long getDocumentsProcessed()
  {
    return documentsProcessed;
  }

  /** Get whether the processed count is accurate, or an estimate.
  *@return true if accurate.
  */
  public boolean getProcessedCountExact()
  {
    return processedCountExact;
  }

  /** Get the start time.
  *@return the start time, or -1
  */
  public long getStartTime()
  {
    return startTime;
  }

  /** Get the end time.
  *@return the end time, or -1
  */
  public long getEndTime()
  {
    return endTime;
  }

  /** Get the error text.
  *@return the text, or null.
  */
  public String getErrorText()
  {
    return errorText;
  }
}
