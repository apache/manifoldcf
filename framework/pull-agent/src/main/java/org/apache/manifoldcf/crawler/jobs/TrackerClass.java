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

package org.apache.manifoldcf.crawler.jobs;

import java.util.*;

/** Debugging class to keep track of recent modifications to the jobqueue table,
* along with context as to where it occurred.  If a jobqueue state error occurs,
* we can then print out all of the pertinent history and find the culprit.
*/
public class TrackerClass
{
  // The goal of this class is to keep track of at least some of the history
  // potentially affecting each record.
  protected final static long HISTORY_LENGTH = 60000L * 15;     // 15 minutes

  // Active transaction
  protected final static Map<String,TransactionData> transactionData = new HashMap<String,TransactionData>();
  
  // Modification history
  protected final static List<TransactionData> history = new ArrayList<TransactionData>();
  
  // Place where we keep track of individual modifications
  private TrackerClass()
  {
  }
  
  /** Add a single record event, as yet uncommitted */
  public static void noteRecordEvent(Long recordID, int newStatus, String description)
  {
    addEvent(new RecordEvent(recordID, newStatus, new Exception(description)));
  }
  
  /** Add a global event, as yet uncommitted, which has the potential
  * to affect any record's state in a given job.
  */
  public static void noteJobEvent(Long jobID, String description)
  {
    addEvent(new JobEvent(jobID, new Exception(description)));
  }
  
  /** Add a global event, as yet uncommitted, which has the potential
  * to affect the state of any record.
  */
  public static void noteGlobalEvent(String description)
  {
    addEvent(new GlobalEvent(new Exception(description)));
  }
  
  protected static void addEvent(HistoryRecord hr)
  {
    String threadName = Thread.currentThread().getName();
    TransactionData td;
    synchronized (transactionData)
    {
      td = transactionData.get(threadName);
      if (td == null)
      {
        td = new TransactionData();
        transactionData.put(threadName,td);
      }
    }
    td.addEvent(hr);
  }
  
  /** Note a commit operation.
  */
  public static void noteCommit()
  {
    long currentTime = System.currentTimeMillis();
    String threadName = Thread.currentThread().getName();
    TransactionData td;
    synchronized (transactionData)
    {
      td = transactionData.get(threadName);
      transactionData.remove(threadName);
    }
    if (td == null)
      return;
    // Only keep stuff around for an hour
    long removalCutoff = currentTime - HISTORY_LENGTH;
    synchronized (history)
    {
      history.add(td);
      // Clean out older records
      while (history.size() > 0)
      {
        TransactionData td2 = history.get(0);
        if (td2.isFlushable(removalCutoff))
          history.remove(0);
      }
    }
    
  }
  
  /** Note a rollback operation.
  */
  public static void noteRollback()
  {
    String threadName = Thread.currentThread().getName();
    synchronized (transactionData)
    {
      transactionData.remove(threadName);
    }
  }
  
  public static void printForensics(Long recordID, int existingStatus)
  {
    synchronized (transactionData)
    {
      synchronized (history)
      {
        System.out.println("---- Forensics for record "+recordID+", current status: "+existingStatus+" ----");
        System.out.println("--Current stack trace--");
        new Exception("Unexpected jobqueue status").printStackTrace();
        System.out.println("--Active transactions--");
        for (String threadName : transactionData.keySet())
        {
          for (HistoryRecord hr : transactionData.get(threadName).getEvents())
          {
            if (hr.applies(recordID))
            {
              System.out.println("Thread '"+threadName+"' was active:");
              hr.print();
            }
          }
        }
        System.out.println("--Pertinent History--");
        for (TransactionData td : history)
        {
          for (HistoryRecord hr : td.getEvents())
          {
            if (hr.applies(recordID))
            {
              hr.print();
            }
          }
        }
      }
    }
        
  }
  
  protected static class TransactionData
  {
    protected final List<HistoryRecord> transactionEvents = new ArrayList<HistoryRecord>();
    protected long timestamp;
    
    public TransactionData()
    {
      timestamp = System.currentTimeMillis();
    }
    
    public void addEvent(HistoryRecord event)
    {
      transactionEvents.add(event);
    }
    
    public List<HistoryRecord> getEvents()
    {
      return transactionEvents;
    }
    
    public boolean isFlushable(long cutoffTime)
    {
      return cutoffTime > timestamp;
    }
  }
  
  protected abstract static class HistoryRecord
  {
    protected long timestamp;
    protected Exception trace;
    
    public HistoryRecord(Exception trace)
    {
      this.trace = trace;
      this.timestamp = System.currentTimeMillis();
    }
    
    public void print()
    {
      System.out.println("  at "+new Long(timestamp)+", location: ");
      trace.printStackTrace();
    }
    
    public abstract boolean applies(Long recordID);
    
  }
  
  protected static class RecordEvent extends HistoryRecord
  {
    protected Long recordID;
    protected int newStatus;
    
    public RecordEvent(Long recordID, int newStatus, Exception trace)
    {
      super(trace);
      this.recordID = recordID;
      this.newStatus = newStatus;
    }
    
    @Override
    public void print()
    {
      System.out.println("Record "+recordID+" status modified to "+newStatus);
      super.print();
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return this.recordID.equals(recordID);
    }

  }
  
  protected static class JobEvent extends HistoryRecord
  {
    protected Long jobID;
    
    public JobEvent(Long jobID, Exception trace)
    {
      super(trace);
      this.jobID = jobID;
    }
    
    @Override
    public void print()
    {
      System.out.println("All job related records modified for job "+jobID);
      super.print();
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return true;
    }
  }
  
  protected static class GlobalEvent extends HistoryRecord
  {
    public GlobalEvent(Exception trace)
    {
      super(trace);
    }
    
    @Override
    public void print()
    {
      System.out.println("All records modified");
      super.print();
    }

    @Override
    public boolean applies(Long recordID)
    {
      return true;
    }
  }
  
}
