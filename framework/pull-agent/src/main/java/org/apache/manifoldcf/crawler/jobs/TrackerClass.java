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
import java.io.*;

import org.apache.manifoldcf.crawler.system.Logging;

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
  protected final static List<HistoryRecord> history = new ArrayList<HistoryRecord>();
  
  // Place where we keep track of individual modifications
  private TrackerClass()
  {
  }
  
  /** Add a single record event, as yet uncommitted */
  public static void noteRecordChange(Long recordID, int newStatus, String description)
  {
    if (Logging.diagnostics.isDebugEnabled())
      addChange(new RecordChange(recordID, newStatus, description));
  }
  
  /** Add a global event, as yet uncommitted, which has the potential
  * to affect any record's state in a given job.
  */
  public static void noteJobChange(Long jobID, String description)
  {
    if (Logging.diagnostics.isDebugEnabled())
      addChange(new JobChange(jobID, description));
  }
  
  /** Add a global event, as yet uncommitted, which has the potential
  * to affect the state of any record.
  */
  public static void noteGlobalChange(String description)
  {
    if (Logging.diagnostics.isDebugEnabled())
      addChange(new GlobalChange(description));
  }
  
  protected static void addChange(DataChange dc)
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
    td.addChange(dc);
  }
  
  /** Note that we are about to commit.
  */
  public static void notePrecommit()
  {
    if (!Logging.diagnostics.isDebugEnabled())
      return;

    long currentTime = System.currentTimeMillis();
    String threadName = Thread.currentThread().getName();

    TransactionData td;
    synchronized (transactionData)
    {
      td = transactionData.get(threadName);
    }
    
    if (td == null)
      return;
    
    HistoryRecord hr = new PrecommitEvent(new Exception("Precommit stack trace"),currentTime,threadName,td);
    
    synchronized (history)
    {
      history.add(hr);
    }
  }
  
  /** Note a read status operation.
  */
  public static void noteRead(Long recordID)
  {
    if (!Logging.diagnostics.isDebugEnabled())
      return;
    
    long currentTime = System.currentTimeMillis();
    String threadName = Thread.currentThread().getName();

    HistoryRecord hr = new ReadEvent(new Exception("Read stack trace"),currentTime,threadName,recordID);
    
    synchronized (history)
    {
      history.add(hr);
    }
  }

  /** Note about to read status operation.
  */
  public static void notePreread(Long recordID)
  {
    if (!Logging.diagnostics.isDebugEnabled())
      return;
    
    long currentTime = System.currentTimeMillis();
    String threadName = Thread.currentThread().getName();

    HistoryRecord hr = new PrereadEvent(new Exception("Pre-read stack trace"),currentTime,threadName,recordID);
    
    synchronized (history)
    {
      history.add(hr);
    }
  }
  
  /** Note a commit operation.
  */
  public static void noteCommit()
  {
    if (!Logging.diagnostics.isDebugEnabled())
      return;
    
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

    HistoryRecord hr = new CommitEvent(new Exception("Commit stack trace"),currentTime,threadName,td);

    // Only keep stuff around for an hour
    long removalCutoff = currentTime - HISTORY_LENGTH;
    synchronized (history)
    {
      history.add(hr);
      // Clean out older records
      // MHL - this logic is wrong
      while (history.size() > 0)
      {
        HistoryRecord oldRecord = history.get(0);
        if (oldRecord.isFlushable(removalCutoff))
          history.remove(0);
        else
          break;
      }
    }
    
  }
  
  /** Note a rollback operation.
  */
  public static void noteRollback()
  {
    if (!Logging.diagnostics.isDebugEnabled())
      return;

    String threadName = Thread.currentThread().getName();
    synchronized (transactionData)
    {
      transactionData.remove(threadName);
    }
  }
  
  public static void printForensics(Long recordID, int existingStatus)
  {
    if (Logging.diagnostics.isDebugEnabled())
    {
      synchronized (transactionData)
      {
        synchronized (history)
        {
          Logging.diagnostics.debug("==== Forensics for record "+recordID+", current status: "+existingStatus+" ====");
          Logging.diagnostics.debug("=== Current stack trace ===",new Exception("Forensics stack trace"));
          Logging.diagnostics.debug("=== Active transactions ===");
          for (String threadName : transactionData.keySet())
          {
            for (DataChange dc : transactionData.get(threadName).getChanges())
            {
              if (dc.applies(recordID))
              {
                Logging.diagnostics.debug("Thread '"+threadName+"' was doing things to this record: " + dc.getDescription());
              }
            }
          }
          Logging.diagnostics.debug("=== Pertinent History ===");
          for (HistoryRecord hr : history)
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
    protected final List<DataChange> changes = new ArrayList<DataChange>();
    
    public TransactionData()
    {
    }
    
    public void addChange(DataChange change)
    {
      changes.add(change);
    }
    
    public List<DataChange> getChanges()
    {
      return changes;
    }
    
    public boolean applies(Long recordID)
    {
      for (DataChange dc : changes)
      {
        if (dc.applies(recordID))
          return true;
      }
      return false;
    }
  }
  
  protected abstract static class DataChange
  {
    protected final String description;
    
    public DataChange(String description)
    {
      this.description = description;
    }
    
    public String getDescription()
    {
      return description;
    }
    
    public abstract boolean applies(Long recordID);

  }
  
  protected abstract static class HistoryRecord
  {
    protected final long timestamp;
    protected final Exception trace;
    protected final String threadName;
    
    public HistoryRecord(Exception trace, long timestamp, String threadName)
    {
      this.trace = trace;
      this.timestamp = timestamp;
      this.threadName = threadName;
    }
    
    public void print(String description)
    {
      Logging.diagnostics.debug("== "+description+" by '"+threadName+"' at "+new Long(timestamp)+" ==",trace);
    }
    
    public boolean isFlushable(long timestamp)
    {
      return this.timestamp < timestamp;
    }

    public abstract boolean applies(Long recordID);
    
    public abstract void print();

  }
  
  protected static class CommitEvent extends HistoryRecord
  {
    protected final TransactionData transactionData;
    
    public CommitEvent(Exception trace, long timestamp, String threadName, TransactionData transactionData)
    {
      super(trace,timestamp,threadName);
      this.transactionData = transactionData;
    }

    @Override
    public void print()
    {
      super.print("Commit transaction");
      Logging.diagnostics.debug("    Transaction includes:");
      for (DataChange dc : transactionData.getChanges())
      {
        Logging.diagnostics.debug("      "+dc.getDescription());
      }
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return transactionData.applies(recordID);
    }
    
  }

  protected static class PrecommitEvent extends HistoryRecord
  {
    protected final TransactionData transactionData;
    
    public PrecommitEvent(Exception trace, long timestamp, String threadName, TransactionData transactionData)
    {
      super(trace,timestamp,threadName);
      this.transactionData = transactionData;
    }

    @Override
    public void print()
    {
      super.print("About to commit transaction");
      Logging.diagnostics.debug("    Transaction includes:");
      for (DataChange dc : transactionData.getChanges())
      {
        Logging.diagnostics.debug("      "+dc.getDescription());
      }
    }
        
    @Override
    public boolean applies(Long recordID)
    {
      return transactionData.applies(recordID);
    }
  }
  
  protected static class ReadEvent extends HistoryRecord
  {
    protected final Long recordID;
    
    public ReadEvent(Exception trace, long timestamp, String threadName, Long recordID)
    {
      super(trace,timestamp,threadName);
      this.recordID = recordID;
    }
    
    @Override
    public void print()
    {
      super.print("Read status");
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return recordID.equals(this.recordID);
    }
  }

  protected static class PrereadEvent extends HistoryRecord
  {
    protected final Long recordID;
    
    public PrereadEvent(Exception trace, long timestamp, String threadName, Long recordID)
    {
      super(trace,timestamp,threadName);
      this.recordID = recordID;
    }
    
    @Override
    public void print()
    {
      super.print("About to read status");
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return recordID.equals(this.recordID);
    }
  }
  
  protected static class RecordChange extends DataChange
  {
    protected final Long recordID;
    protected final int newStatus;
    
    public RecordChange(Long recordID, int newStatus, String description)
    {
      super(description);
      this.recordID = recordID;
      this.newStatus = newStatus;
    }
    
    @Override
    public String getDescription()
    {
      return "Record "+recordID+" status modified to "+newStatus+": "+super.getDescription();
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return recordID.equals(this.recordID);
    }
  }
  
  protected static class JobChange extends DataChange
  {
    protected final Long jobID;
    
    public JobChange(Long jobID, String description)
    {
      super(description);
      this.jobID = jobID;
    }
    
    @Override
    public String getDescription()
    {
      return "All job related records modified for job "+jobID+": "+super.getDescription();
    }
    
    @Override
    public boolean applies(Long recordID)
    {
      return true;
    }
  }
  
  protected static class GlobalChange extends DataChange
  {
    public GlobalChange(String description)
    {
      super(description);
    }
    
    @Override
    public String getDescription()
    {
      return "All records modified: "+super.getDescription();
    }

    @Override
    public boolean applies(Long recordID)
    {
      return true;
    }
  }
  
}
