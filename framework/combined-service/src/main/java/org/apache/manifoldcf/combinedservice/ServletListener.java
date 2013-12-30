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
package org.apache.manifoldcf.combinedservice;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.agents.system.AgentsDaemon;
import javax.servlet.*;

/** This class furnishes a servlet shutdown hook for ManifoldCF.  It should be referenced in the
* web.xml file for the application in order to do the right thing, however.
*/
public class ServletListener implements ServletContextListener
{
  public static final String _rcsid = "@(#)$Id$";

  protected static AgentsThread agentsThread = null;
  protected IdleCleanupThread idleCleanupThread = null;

  public void contextInitialized(ServletContextEvent sce)
  {
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);

      ManifoldCF.createSystemDatabase(tc);
      ManifoldCF.installTables(tc);
      ManifoldCF.registerThisAgent(tc);
      ManifoldCF.reregisterAllConnectors(tc);

      // This is for the UI and API components
      idleCleanupThread = new IdleCleanupThread();
      idleCleanupThread.start();

      // This is for the agents process
      agentsThread = new AgentsThread(ManifoldCF.getProcessID());
      agentsThread.start();

    }
    catch (ManifoldCFException e)
    {
      throw new RuntimeException("Could not initialize servlet; "+e.getMessage(),e);
    }
  }
  
  public void contextDestroyed(ServletContextEvent sce)
  {
    IThreadContext tc = ThreadContextFactory.make();
    try
    {
      if (agentsThread != null)
      {
        AgentsDaemon.assertAgentsShutdownSignal(tc);
        agentsThread.finishUp();
        agentsThread = null;
        AgentsDaemon.clearAgentsShutdownSignal(tc);
      }
      
      while (true)
      {
        if (idleCleanupThread == null)
          break;
        idleCleanupThread.interrupt();
        if (!idleCleanupThread.isAlive())
          idleCleanupThread = null;
      }
    }
    catch (InterruptedException e)
    {
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() != ManifoldCFException.INTERRUPTED)
        throw new RuntimeException("Cannot shutdown servlet cleanly; "+e.getMessage(),e);
    }
    ManifoldCF.cleanUpEnvironment(tc);
  }

  protected static class AgentsThread extends Thread
  {
    
    protected final String processID;
    
    protected Throwable exception = null;

    public AgentsThread(String processID)
    {
      setName("Agents");
      this.processID = processID;
    }
    
    public void run()
    {
      IThreadContext tc = ThreadContextFactory.make();
      try
      {
        AgentsDaemon.clearAgentsShutdownSignal(tc);
        AgentsDaemon ad = new AgentsDaemon(processID);
        try
        {
          ad.runAgents(tc);
        }
        finally
        {
          ad.stopAgents(tc);
        }
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public void finishUp()
      throws ManifoldCFException, InterruptedException
    {
      join();
      if (exception != null)
      {
        if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        if (exception instanceof Error)
          throw (Error)exception;
        if (exception instanceof ManifoldCFException)
          throw (ManifoldCFException)exception;
        throw new RuntimeException("Unknown exception type thrown: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
      }
    }
  }
  
}
