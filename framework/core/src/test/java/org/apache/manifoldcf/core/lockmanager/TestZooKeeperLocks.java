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
package org.apache.manifoldcf.core.lockmanager;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TestZooKeeperLocks extends ZooKeeperBase
{
  protected File synchDir = null;
  
  protected final static int readerThreadCount = 10;
  protected final static int writerThreadCount = 5;

  @Test
  public void multiThreadZooKeeperLockTest()
    throws Exception
  {
    // First, set off the threads
    ZooKeeperConnectionPool pool = new ZooKeeperConnectionPool("localhost:8348",2000);
    LockObjectFactory factory = new ZooKeeperLockObjectFactory(pool);

    runTest(factory);
  }
  
  @Before
  public void createSynchDir()
    throws Exception
  {
    synchDir = new File("synchdir");
    synchDir.mkdir();
  }

  @After
  public void removeSynchDir()
    throws Exception
  {
    if (synchDir != null)
      deleteRecursively(synchDir);
    synchDir = null;
  }
  
  @Test
  public void multiThreadFileLockTest()
    throws Exception
  {
    runTest(new FileLockObjectFactory(synchDir));
  }
  
  protected static void runTest(LockObjectFactory factory)
    throws Exception
  {
    String lockKey = "testkey";
    AtomicInteger ai = new AtomicInteger(0);
    
    ReaderThread[] readerThreads = new ReaderThread[readerThreadCount];
    for (int i = 0 ; i < readerThreadCount ; i++)
    {
      readerThreads[i] = new ReaderThread(factory, lockKey, ai);
      readerThreads[i].start();
    }

    WriterThread[] writerThreads = new WriterThread[writerThreadCount];
    for (int i = 0 ; i < writerThreadCount ; i++)
    {
      writerThreads[i] = new WriterThread(factory, lockKey, ai);
      writerThreads[i].start();
    }
    
    for (int i = 0 ; i < readerThreadCount ; i++)
    {
      Throwable e = readerThreads[i].finishUp();
      if (e != null)
      {
        if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        if (e instanceof Error)
          throw (Error)e;
        if (e instanceof Exception)
          throw (Exception)e;
      }
    }
    
    for (int i = 0 ; i < writerThreadCount ; i++)
    {
      Throwable e = writerThreads[i].finishUp();
      if (e != null)
      {
        if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        if (e instanceof Error)
          throw (Error)e;
        if (e instanceof Exception)
          throw (Exception)e;
      }
    }
    
  }
  
  protected static void enterReadLock(Long threadID, LockGate lo)
    throws Exception
  {
    try
    {
      lo.enterReadLock(threadID);
    }
    catch (ExpiredObjectException e)
    {
      throw new ManifoldCFException("Unexpected exception: "+e.getMessage(),e);
    }
  }
  
  protected static void leaveReadLock(LockGate lo)
    throws Exception
  {
    try
    {
      lo.leaveReadLock();
    }
    catch (ExpiredObjectException e)
    {
      throw new ManifoldCFException("Unexpected exception: "+e.getMessage(),e);
    }
  }

  protected static void enterWriteLock(Long threadID, LockGate lo)
    throws Exception
  {
    try
    {
      lo.enterWriteLock(threadID);
    }
    catch (ExpiredObjectException e)
    {
      throw new ManifoldCFException("Unexpected exception: "+e.getMessage(),e);
    }
  }
  
  protected static void leaveWriteLock(LockGate lo)
    throws Exception
  {
    try
    {
      lo.leaveWriteLock();
    }
    catch (ExpiredObjectException e)
    {
      throw new ManifoldCFException("Unexpected exception: "+e.getMessage(),e);
    }
  }
  
  /** Reader thread */
  protected static class ReaderThread extends Thread
  {
    protected final LockObjectFactory factory;
    protected final Object lockKey;
    protected final AtomicInteger ai;
    protected final Long threadID;

    protected Throwable exception = null;
    
    public ReaderThread(LockObjectFactory factory, Object lockKey, AtomicInteger ai)
    {
      setName("reader");
      this.factory = factory;
      this.lockKey = lockKey;
      this.ai = ai;
      this.threadID = Thread.currentThread().getId();
    }
    
    public void run()
    {
      try
      {
        // Create a new lock pool since that is the best way to insure real
        // zookeeper action.
        LockPool lp = new LockPool(factory);
        LockGate lo;
        // First test: count all reader threads inside read lock.
        // This guarantees that read locks are non-exclusive.
        // Enter read lock
        System.out.println("Entering read lock");
        lo = lp.getObject(lockKey);
        enterReadLock(threadID,lo);
        try
        {
          System.out.println(" Read lock entered!");
          // Count this thread
          ai.incrementAndGet();
          // Wait until all readers have been counted.  This test will hang if the readers function
          // exclusively
          while (ai.get() < readerThreadCount)
          {
            Thread.sleep(10L);
          }
        }
        finally
        {
          System.out.println("Leaving read lock");
          leaveReadLock(lo);
          System.out.println(" Left read lock!");
        }
        // Now, all the writers will get involved; we just need to make sure we never see an inconsistent value
        while (ai.get() < readerThreadCount + 2*writerThreadCount)
        {
          System.out.println("Waiting for all write threads to succeed...");
          lo = lp.getObject(lockKey);
          enterReadLock(threadID,lo);
          try
          {
            // The writer thread will increment the counter twice for every thread, both times within the lock.
            // We never want to see the intermediate values.
            if ((ai.get() - readerThreadCount) % 2 == 1)
              throw new Exception("Was able to read when write lock in place");
          }
          finally
          {
            leaveReadLock(lo);
          }
          Thread.sleep(100L);
        }
        System.out.println("Done with reader thread");
      }
      catch (InterruptedException e)
      {
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public Throwable finishUp()
      throws InterruptedException
    {
      join();
      return exception;
    }
    
  }

  /** Writer thread */
  protected static class WriterThread extends Thread
  {
    protected final LockObjectFactory factory;
    protected final Object lockKey;
    protected final AtomicInteger ai;
    protected final Long threadID;
    
    protected Throwable exception = null;
    
    public WriterThread(LockObjectFactory factory, Object lockKey, AtomicInteger ai)
    {
      setName("writer");
      this.factory = factory;
      this.lockKey = lockKey;
      this.ai = ai;
      this.threadID = Thread.currentThread().getId();
    }
    
    public void run()
    {
      try
      {
        // Create a new lock pool since that is the best way to insure real
        // zookeeper action.
        // LockPool is a dummy
        LockPool lp = new LockPool(factory);
        LockGate lo;
        // The reader threads require ALL of the readers to get into the protected area.  If
        // we try to write before that happens, ordering requirements produce a deadlock.  So wait.
        while (ai.get() < readerThreadCount)
        {
          Thread.sleep(100L);
        }
        
        /*
        // Take write locks but free them immediately if read is what's active
        while (true)
        {
          lo = lp.getObject(lockKey);
          enterWriteLock(threadID,lo);
          try
          {
            System.out.println("Made it into read-time write lock");
            // Check if we made it in during read cycle... that would be bad.
            if (ai.get() > 0 && ai.get() < readerThreadCount)
              throw new Exception("Was able to write even when readers were active");
            if (ai.get() >= readerThreadCount)
              break;
          }
          finally
          {
            System.out.println("Leaving read-time write lock");
            leaveWriteLock(lo);
            System.out.println("Left read-time write lock");
          }
          Thread.sleep(1000L);
        }
        */
        
        // Get write lock, increment twice, and leave write lock.  Meanwhile, reader threads will be trying to gain access.
        lo = lp.getObject(lockKey);
        enterWriteLock(threadID,lo);
        try
        {
          if ((ai.get() - readerThreadCount) % 2 == 1)
            throw new Exception("More than one writer thread active at the same time!");
          ai.incrementAndGet();
          // Keep the lock for a while so other threads have to wait
          Thread.sleep(50L);
          // Increment again
          ai.incrementAndGet();
          System.out.println("Updated write count");
        }
        finally
        {
          leaveWriteLock(lo);
        }
        // Completed successfully!
      }
      catch (InterruptedException e)
      {
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public Throwable finishUp()
      throws InterruptedException
    {
      join();
      return exception;
    }
    
  }
  
}
