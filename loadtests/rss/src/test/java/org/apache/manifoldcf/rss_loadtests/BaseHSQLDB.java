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
package org.apache.manifoldcf.rss_loadtests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

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
public class BaseHSQLDB extends org.apache.manifoldcf.crawler.tests.ConnectorBaseHSQLDB
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
    return new String[]{"org.apache.manifoldcf.crawler.connectors.rss.RSSConnector"};
  }
  
  protected String[] getOutputNames()
  {
    return new String[]{"Null Output"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.output.nullconnector.NullConnector"};
  }
  
  // API support
  
  // These methods allow communication with the ManifoldCF api webapp, via the locally-instantiated jetty
  
  /** Construct a command url.
  */
  protected String makeAPIURL(String command)
  {
    return "http://localhost:"+Integer.toString(testPort)+"/mcf-api-service/json/"+command;
  }
  
  /** Perform an json API GET operation.
  *@param apiURL is the operation.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIGetOperation(String apiURL, int expectedResponse)
    throws Exception
  {
    HttpClient client = new HttpClient();
    GetMethod method = new GetMethod(apiURL);
    int response = client.executeMethod(method);
    byte[] responseData = method.getResponseBody();
    String responseString = new String(responseData,"utf-8");
    if (response != expectedResponse)
      throw new Exception("API http error; expected "+Integer.toString(expectedResponse)+", saw "+Integer.toString(response)+": "+responseString);
    // We presume that the data is utf-8, since that's what the API uses throughout.
    return responseString;
  }

  /** Perform an json API DELETE operation.
  *@param apiURL is the operation.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIDeleteOperation(String apiURL, int expectedResponse)
    throws Exception
  {
    HttpClient client = new HttpClient();
    DeleteMethod method = new DeleteMethod(apiURL);
    int response = client.executeMethod(method);
    byte[] responseData = method.getResponseBody();
    String responseString = new String(responseData,"utf-8");
    if (response != expectedResponse)
      throw new Exception("API http error; expected "+Integer.toString(expectedResponse)+", saw "+Integer.toString(response)+": "+responseString);
    // We presume that the data is utf-8, since that's what the API uses throughout.
    return responseString;
  }

  /** Perform an json API PUT operation.
  *@param apiURL is the operation.
  *@param input is the input JSON.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIPutOperation(String apiURL, int expectedResponse, String input)
    throws Exception
  {
    HttpClient client = new HttpClient();
    PutMethod method = new PutMethod(apiURL);
    method.setRequestHeader("Content-type", "text/plain; charset=UTF-8");
    method.setRequestBody(input);
    int response = client.executeMethod(method);
    byte[] responseData = method.getResponseBody();
    String responseString = new String(responseData,"utf-8");
    if (response != expectedResponse)
      throw new Exception("API http error; expected "+Integer.toString(expectedResponse)+", saw "+Integer.toString(response)+": "+responseString);
    // We presume that the data is utf-8, since that's what the API uses throughout.
    return responseString;
  }

  /** Perform an json API POST operation.
  *@param apiURL is the operation.
  *@param input is the input JSON.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIPostOperation(String apiURL, int expectedResponse, String input)
    throws Exception
  {
    HttpClient client = new HttpClient();
    PostMethod method = new PostMethod(apiURL);
    method.setRequestHeader("Content-type", "text/plain; charset=UTF-8");
    method.setRequestBody(input);
    int response = client.executeMethod(method);
    byte[] responseData = method.getResponseBody();
    String responseString = new String(responseData,"utf-8");
    if (response != expectedResponse)
      throw new Exception("API http error; expected "+Integer.toString(expectedResponse)+", saw "+Integer.toString(response)+": "+responseString);
    // We presume that the data is utf-8, since that's what the API uses throughout.
    return responseString;
  }

  /** Perform a json GET API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIGetOperationViaNodes(String command, int expectedResponse)
    throws Exception
  {
    String result = performAPIGetOperation(makeAPIURL(command),expectedResponse);
    Configuration cfg = new Configuration();
    cfg.fromJSON(result);
    return cfg;
  }

  /** Perform a json DELETE API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIDeleteOperationViaNodes(String command, int expectedResponse)
    throws Exception
  {
    String result = performAPIDeleteOperation(makeAPIURL(command),expectedResponse);
    Configuration cfg = new Configuration();
    cfg.fromJSON(result);
    return cfg;
  }

  /** Perform a json PUT API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIPutOperationViaNodes(String command, int expectedResponse, Configuration argument)
    throws Exception
  {
    String argumentJson;
    if (argument != null)
      argumentJson = argument.toJSON();
    else
      argumentJson = null;
    
    String result = performAPIPutOperation(makeAPIURL(command),expectedResponse,argumentJson);
    Configuration cfg = new Configuration();
    cfg.fromJSON(result);
    return cfg;
  }

  /** Perform a json POST API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIPostOperationViaNodes(String command, int expectedResponse, Configuration argument)
    throws Exception
  {
    String argumentJson;
    if (argument != null)
      argumentJson = argument.toJSON();
    else
      argumentJson = null;
    
    String result = performAPIPostOperation(makeAPIURL(command),expectedResponse,argumentJson);
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

    
    String crawlerWarPath = "../../framework/build/war/mcf-crawler-ui.war";
    String authorityserviceWarPath = "../../framework/build/war/mcf-authority-service.war";
    String apiWarPath = "../../framework/build/war/mcf-api-service.war";

    if (System.getProperty("crawlerWarPath") != null)
    	crawlerWarPath = System.getProperty("crawlerWarPath");
    if (System.getProperty("authorityserviceWarPath") != null)
    	authorityserviceWarPath = System.getProperty("authorityserviceWarPath");
    if (System.getProperty("apiWarPath") != null)
    	apiWarPath = System.getProperty("apiWarPath");
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/mcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityserviceWarPath,"/mcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    server.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/mcf-api-service");
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
          catch (ManifoldCFException e)
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
            JobStatus status = jobManager.getStatus(desc.getID(),false);
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
                ManifoldCF.sleep(10000);
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
          catch (ManifoldCFException e)
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
            JobStatus status = jobManager.getStatus(desc.getID(),false);
            if (status != null)
            {
              ManifoldCF.sleep(10000);
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
          ManifoldCF.startAgents(tc);

          try
          {
            ManifoldCF.sleep(5000);
          }
          catch (InterruptedException e)
          {
            break;
          }
        }
      }
      catch (ManifoldCFException e)
      {
        daemonException = e;
      }
      finally
      {
        try
        {
          ManifoldCF.stopAgents(tc);
        }
        catch (ManifoldCFException e)
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
