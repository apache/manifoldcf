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
package org.apache.lcf.tests;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.LCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** Tests that run the "agents daemon" should be derived from this */
public class TestBase extends org.apache.lcf.crawler.tests.TestConnectorBase
{
  public static final String agentShutdownSignal = "agent-process";
  
  protected DaemonThread daemonThread = null;
  
  protected String[] getConnectorNames()
  {
    return new String[]{"File Connector"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.lcf.crawler.connectors.filesystem.FileConnector"};
  }
  
  protected String[] getOutputNames()
  {
    return new String[]{"Null Output"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.lcf.agents.output.nullconnector.NullConnector"};
  }
  
  @Before
  public void setUp()
    throws Exception
  {
    super.setUp();
    // If all worked, then we can start the daemon.
    // Clear the agents shutdown signal.
    IThreadContext tc = ThreadContextFactory.make();
    ILockManager lockManager = LockManagerFactory.make(tc);
    lockManager.clearGlobalFlag(agentShutdownSignal);

    daemonThread = new DaemonThread();
    daemonThread.start();
  }
  
  @After
  public void cleanUp()
    throws Exception
  {
    // Shut down daemon
    Exception currentException = null;
    
    if (daemonThread != null)
    {
      IThreadContext tc = ThreadContextFactory.make();
      ILockManager lockManager = LockManagerFactory.make(tc);
      lockManager.setGlobalFlag(agentShutdownSignal);
    
      // Wait for daemon thread to exit.
      while (true)
      {
        if (daemonThread.isAlive())
        {
          Thread.sleep(1000L);
          continue;
        }
        break;
      }

      Exception e = daemonThread.getDaemonException();
      if (e != null)
        currentException = e;
    }
    // Clean up everything else
    try
    {
      super.cleanUp();
    }
    catch (Exception e)
    {
      if (currentException == null)
        currentException = e;
    }
    if (currentException != null)
      throw currentException;
  }
  
  protected static class DaemonThread extends Thread
  {
    protected Exception daemonException = null;
    
    public DaemonThread()
    {
      setName("Daemon thread");
    }
    
    public void run()
    {
      IThreadContext tc = ThreadContextFactory.make();
      // Now, start the server, and then wait for the shutdown signal.  On shutdown, we have to actually do the cleanup,
      // because the JVM isn't going away.
      try
      {
        ILockManager lockManager = LockManagerFactory.make(tc);
        while (true)
        {
          // Any shutdown signal yet?
          if (lockManager.checkGlobalFlag(agentShutdownSignal))
            break;
            
          // Start whatever agents need to be started
          LCF.startAgents(tc);

          try
          {
            LCF.sleep(5000);
          }
          catch (InterruptedException e)
          {
            break;
          }
        }
      }
      catch (LCFException e)
      {
        daemonException = e;
      }
      finally
      {
        try
        {
          LCF.stopAgents(tc);
        }
        catch (LCFException e)
        {
          daemonException = e;
        }
      }
    }
    
    public Exception getDaemonException()
    {
      return daemonException;
    }
    
  }
  
}
