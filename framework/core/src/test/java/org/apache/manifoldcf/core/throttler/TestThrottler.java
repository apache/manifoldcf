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
package org.apache.manifoldcf.core.throttler;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TestThrottler extends org.apache.manifoldcf.core.tests.BaseDerby
{
  @Test
  public void multiThreadConnectionPoolTest()
    throws Exception
  {
    // First, create the throttle group.
    IThreadContext threadContext = ThreadContextFactory.make();
    IThrottleGroups tg = ThrottleGroupsFactory.make(threadContext);
    tg.createOrUpdateThrottleGroup("test","test",new ThrottleSpec());
    
    // We create a pretend connection pool
    IConnectionThrottler connectionThrottler = tg.obtainConnectionThrottler("test","test",new String[]{"A","B","C"});
    System.out.println("Connection throttler obtained");
    
    // How best to test this?
    // Well, what I'm going to do is to have multiple threads active.  Each one will do perfectly sensible things
    // while generating a log that includes timestamps for everything that happens.  At the end, the log will be
    // analyzed for violations of throttling policy.
    
    PollingThread pt = new PollingThread();
    pt.start();

    EventLog eventLog = new EventLog();
    
    int numThreads = 10;
    
    TesterThread[] threads = new TesterThread[numThreads];
    for (int i = 0; i < numThreads; i++)
    {
      threads[i] = new TesterThread(connectionThrottler, eventLog);
      threads[i].start();
    }
    
    // Now, join all the threads at the end
    for (int i = 0; i < numThreads; i++)
    {
      threads[i].finishUp();
    }
    
    pt.interrupt();
    pt.finishUp();

    // Finally, do the log analysis
    eventLog.analyze();
    
    System.out.println("Done test");
  }
  
  protected static class PollingThread extends Thread
  {
    protected Throwable exception = null;
    
    public PollingThread()
    {
    }
    
    public void run()
    {
      try
      {
        IThreadContext threadContext = ThreadContextFactory.make();
        IThrottleGroups throttleGroups = ThrottleGroupsFactory.make(threadContext);
        
        while (true)
        {
          throttleGroups.poll("test");
          Thread.sleep(1000L);
        }
      }
      catch (InterruptedException e)
      {
      }
      catch (Exception e)
      {
        exception = e;
      }

    }
    
    public void finishUp()
      throws Exception
    {
      join();
      if (exception != null)
      {
        if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        else if (exception instanceof Error)
          throw (Error)exception;
        else if (exception instanceof Exception)
          throw (Exception)exception;
        else
          throw new RuntimeException("Unknown exception: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
      }
    }

  }
  
  protected static class TesterThread extends Thread
  {
    protected final EventLog eventLog;
    protected final IConnectionThrottler connectionThrottler;
    protected Throwable exception = null;
    
    public TesterThread(IConnectionThrottler connectionThrottler, EventLog eventLog)
    {
      this.connectionThrottler = connectionThrottler;
      this.eventLog = eventLog;
    }
    
    public void run()
    {
      try
      {
        int numberConnectionCycles = 3;
        int numberFetchesPerCycle = 3;
        
        for (int k = 0; k < numberConnectionCycles; k++)
        {
          // First grab a connection.
          int rval = connectionThrottler.waitConnectionAvailable();
          if (rval == IConnectionThrottler.CONNECTION_FROM_NOWHERE)
            return;
          IFetchThrottler fetchThrottler;
          if (rval == IConnectionThrottler.CONNECTION_FROM_CREATION)
          {
            // Pretend to create the connection
            eventLog.addLogEntry(new ConnectionCreatedEvent());
          }
          else
          {
            // Pretend to get it from the pool
            eventLog.addLogEntry(new ConnectionFromPoolEvent());
          }
          fetchThrottler = connectionThrottler.getNewConnectionFetchThrottler();

          for (int l = 0; l < numberFetchesPerCycle; l++)
          {
            // Perform a fake fetch
            IStreamThrottler streamThrottler = fetchThrottler.obtainFetchDocumentPermission();
            if (streamThrottler == null)
              return;
            eventLog.addLogEntry(new FetchStartEvent());
            // Do one read
            if (streamThrottler.obtainReadPermission(1000) == false)
              return;
            eventLog.addLogEntry(new ReadStartEvent(1000));
            streamThrottler.releaseReadPermission(1000, 1000);
            eventLog.addLogEntry(new ReadDoneEvent(1000));
            // Do another read
            if (streamThrottler.obtainReadPermission(1000) == false)
              return;
            eventLog.addLogEntry(new ReadStartEvent(1000));
            streamThrottler.releaseReadPermission(1000, 1000);
            eventLog.addLogEntry(new ReadDoneEvent(1000));
            // Do a third read
            if (streamThrottler.obtainReadPermission(1000) == false)
              return;
            eventLog.addLogEntry(new ReadStartEvent(1000));
            streamThrottler.releaseReadPermission(1000, 100);
            eventLog.addLogEntry(new ReadDoneEvent(100));
            // Close the stream
            streamThrottler.closeStream();
            eventLog.addLogEntry(new FetchDoneEvent());
          }
          
          // Pretend to release the connection
          boolean destroyIt = connectionThrottler.noteReturnedConnection();
          if (destroyIt)
          {
            eventLog.addLogEntry(new ConnectionDestroyedEvent());
            connectionThrottler.noteConnectionDestroyed();
          }
          else
          {
            eventLog.addLogEntry(new ConnectionReturnedToPoolEvent());
            connectionThrottler.noteConnectionReturnedToPool();
          }
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
        exception = e;
      }
    }
    
    public void finishUp()
      throws Exception
    {
      join();
      if (exception != null)
      {
        if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        else if (exception instanceof Error)
          throw (Error)exception;
        else if (exception instanceof Exception)
          throw (Exception)exception;
        else
          throw new RuntimeException("Unknown exception: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
      }
    }
    
  }
  
  protected static class ThrottleSpec implements IThrottleSpec
  {
    public ThrottleSpec()
    {
    }
    
    /** Given a bin name, find the max open connections to use for that bin.
    *@return -1 if no limit found.
    */
    @Override
    public int getMaxOpenConnections(String binName)
    {
      if (binName.equals("A"))
        return 3;
      if (binName.equals("B"))
        return 4;
      return Integer.MAX_VALUE;
    }

    /** Look up minimum milliseconds per byte for a bin.
    *@return 0.0 if no limit found.
    */
    @Override
    public double getMinimumMillisecondsPerByte(String binName)
    {
      if (binName.equals("B"))
        return 1.0;
      if (binName.equals("C"))
        return 1.5;
      return 0.0;
    }

    /** Look up minimum milliseconds for a fetch for a bin.
    *@return 0 if no limit found.
    */
    @Override
    public long getMinimumMillisecondsPerFetch(String binName)
    {
      if (binName.equals("A"))
        return 5;
      if (binName.equals("C"))
        return 20;
      return 0;
    }

  }
  
  protected static class EventLog
  {
    protected final List<LogEntry> logList = new ArrayList<LogEntry>();
    
    public EventLog()
    {
    }
    
    public synchronized void addLogEntry(LogEntry x)
    {
      System.out.println(x.toString());
      logList.add(x);
    }
    
    public synchronized void analyze()
      throws Exception
    {
      State s = new State();
      for (LogEntry l : logList)
      {
        l.apply(s);
      }
      // Success!
    }
    
  }
  
  protected static abstract class LogEntry
  {
    protected final long timestamp;
    
    public LogEntry(long timestamp)
    {
      this.timestamp = timestamp;
    }
    
    public abstract void apply(State state)
      throws Exception;
    
    public String toString()
    {
      return "Time: "+timestamp;
    }
    
  }
  
  protected static class ConnectionCreatedEvent extends LogEntry
  {
    public ConnectionCreatedEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Connection created";
    }

  }

  protected static class ConnectionDestroyedEvent extends LogEntry
  {
    public ConnectionDestroyedEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Connection destroyed";
    }

  }

  protected static class ConnectionFromPoolEvent extends LogEntry
  {
    public ConnectionFromPoolEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Connection from pool";
    }

  }

  protected static class ConnectionReturnedToPoolEvent extends LogEntry
  {
    public ConnectionReturnedToPoolEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Connection back to pool";
    }

  }

  protected static class FetchStartEvent extends LogEntry
  {
    public FetchStartEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Fetch start";
    }
  }
  
  protected static class FetchDoneEvent extends LogEntry
  {
    public FetchDoneEvent()
    {
      super(System.currentTimeMillis());
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Fetch done";
    }
  }
  
  protected static class ReadStartEvent extends LogEntry
  {
    final int proposed;
    
    public ReadStartEvent(int proposed)
    {
      super(System.currentTimeMillis());
      this.proposed = proposed;
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Read start("+proposed+")";
    }
  }

  protected static class ReadDoneEvent extends LogEntry
  {
    final int actual;
    
    public ReadDoneEvent(int actual)
    {
      super(System.currentTimeMillis());
      this.actual = actual;
    }
    
    public void apply(State state)
      throws Exception
    {
      // MHL
    }
    
    public String toString()
    {
      return super.toString() + "; Read done("+actual+")";
    }
  }

  protected static class State
  {
    public int outstandingConnections = 0;
    public long lastFetch = 0L;
    public long lastByteRead = 0L;
    public int lastByteAmt = 0;
  }
  
}
