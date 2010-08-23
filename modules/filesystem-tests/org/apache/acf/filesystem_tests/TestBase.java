/* $Id: TestBase.java 964422 2010-07-15 13:38:10Z kwright $ */

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
package org.apache.acf.filesystem_tests;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.ACF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.log.Logger;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

/** Tests that run the "agents daemon" should be derived from this */
public class TestBase extends org.apache.acf.crawler.tests.TestConnectorBase
{
  public static final String agentShutdownSignal = "agent-process";
  public static final int testPort = 8346;
  
  protected DaemonThread daemonThread = null;
  protected Server server = null;

  protected String[] getConnectorNames()
  {
    return new String[]{"File Connector"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.acf.crawler.connectors.filesystem.FileConnector"};
  }
  
  protected String[] getOutputNames()
  {
    return new String[]{"Null Output"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.acf.agents.output.nullconnector.NullConnector"};
  }
  
  protected void createDirectory(File f)
    throws Exception
  {
    if (f.mkdirs() == false)
      throw new Exception("Failed to create directory "+f.toString());
  }
  
  protected void removeDirectory(File f)
    throws Exception
  {
    File[] files = f.listFiles();
    if (files != null)
    {
      int i = 0;
      while (i < files.length)
      {
        File subfile = files[i++];
        if (subfile.isDirectory())
          removeDirectory(subfile);
        else
          subfile.delete();
      }
    }
    f.delete();
  }
  
  protected void createFile(File f, String contents)
    throws Exception
  {
    OutputStream os = new FileOutputStream(f);
    try
    {
      Writer w = new OutputStreamWriter(os,"utf-8");
      try
      {
        w.write(contents);
      }
      finally
      {
        w.flush();
      }
    }
    finally
    {
      os.close();
    }
  }
  
  protected void removeFile(File f)
    throws Exception
  {
    if (f.delete() == false)
      throw new Exception("Failed to delete file "+f.toString());
  }
  
  protected void changeFile(File f, String newContents)
    throws Exception
  {
    removeFile(f);
    createFile(f,newContents);
  }
  
  // API support
  
  // These methods allow communication with the ACF api webapp, via the locally-instantiated jetty
  
  /** Perform an json API operation.
  *@param command is the operation.
  *@param argument is the json argument, or null if none.
  *@return the json response.
  */
  protected String performAPIOperation(String command, String argument)
    throws Exception
  {
    HttpClient client = new HttpClient();
    HttpMethod method = new GetMethod("http://localhost:"+Integer.toString(testPort)+"/lcf-api/json/"+command+((argument==null)?"":"?object="+
      java.net.URLEncoder.encode(argument,"utf-8")));
    int response = client.executeMethod(method);
    byte[] responseData = method.getResponseBody();
    String responseString = new String(responseData,"utf-8");
    if (response != 200)
      throw new Exception("API http error "+Integer.toString(response)+": "+responseString);
    // We presume that the data is utf-8, since that's what the API uses throughout.
    return responseString;
  }
  
  /** Perform a json API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIOperationViaNodes(String command, Configuration argument)
    throws Exception
  {
    String argumentJson;
    if (argument != null)
      argumentJson = argument.toJSON();
    else
      argumentJson = null;
    
    String result = performAPIOperation(command,argumentJson);
    Configuration cfg = new Configuration();
    cfg.fromJSON(result);
    return cfg;
  }
  
  // Setup/teardown
  
  @Before
  public void setUp()
    throws Exception
  {
    super.setUp();
    // Start jetty
    server = new Server( testPort );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext("../../framework/dist/web/war/lcf-crawler-ui.war","/lcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext("../../framework/dist/web/war/lcf-authority-service.war","/lcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    server.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext("../../framework/dist/web/war/lcf-api.war","/lcf-api");
    lcfApi.setParentLoaderPriority(true);
    server.addHandler(lcfApi);
    server.start();

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
    initialize();
    if (isInitialized())
    {
      Exception currentException = null;
      IThreadContext tc = ThreadContextFactory.make();

      // Delete all jobs (and wait for them to go away)
      if (daemonThread != null)
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        
        // Get a list of the current active jobs
        IJobDescription[] jobs = jobManager.getAllJobs();
        int i = 0;
        while (i < jobs.length)
        {
          IJobDescription desc = jobs[i++];
          // Abort this job, if it is running
          try
          {
            jobManager.manualAbort(desc.getID());
          }
          catch (ACFException e)
          {
            // This generally means that the job was not running
          }
        }
        i = 0;
        while (i < jobs.length)
        {
          IJobDescription desc = jobs[i++];
          // Wait for this job to stop
          while (true)
          {
            JobStatus status = jobManager.getStatus(desc.getID());
            if (status != null)
            {
              int statusValue = status.getStatus();
              switch (statusValue)
              {
              case JobStatus.JOBSTATUS_NOTYETRUN:
              case JobStatus.JOBSTATUS_COMPLETED:
              case JobStatus.JOBSTATUS_ERROR:
                break;
              default:
                ACF.sleep(10000);
                continue;
              }
            }
            break;
          }
        }

        // Now, delete them all
        i = 0;
        while (i < jobs.length)
        {
          IJobDescription desc = jobs[i++];
          try
          {
            jobManager.deleteJob(desc.getID());
          }
          catch (ACFException e)
          {
            // This usually means that the job is already being deleted
          }
        }

        i = 0;
        while (i < jobs.length)
        {
          IJobDescription desc = jobs[i++];
          // Wait for this job to disappear
          while (true)
          {
            JobStatus status = jobManager.getStatus(desc.getID());
            if (status != null)
            {
              ACF.sleep(10000);
              continue;
            }
            break;
          }
        }

        // Shut down daemon
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
      
      if (server != null)
      {
        server.stop();
        server.join();
        server = null;
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
          ACF.startAgents(tc);

          try
          {
            ACF.sleep(5000);
          }
          catch (InterruptedException e)
          {
            break;
          }
        }
      }
      catch (ACFException e)
      {
        daemonException = e;
      }
      finally
      {
        try
        {
          ACF.stopAgents(tc);
        }
        catch (ACFException e)
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
