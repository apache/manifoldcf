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
package org.apache.manifoldcf.crawler.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.webapp.WebAppContext;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

/** Tests that run the "agents daemon" should be derived from this */
public class ManifoldCFInstance
{
  public static final String agentShutdownSignal = "agent-process";
  
  protected DaemonThread daemonThread = null;
  protected Server server = null;

  protected int testPort = 8346;
  
  public ManifoldCFInstance()
  {
  }
  
  public ManifoldCFInstance(int testPort)
  {
    this.testPort = testPort;
  }

  // Basic job support
  
  public void waitJobInactiveNative(IJobManager jobManager, Long jobID, long maxTime)
    throws ManifoldCFException, InterruptedException
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      JobStatus status = jobManager.getStatus(jobID);
      if (status == null)
        throw new ManifoldCFException("No such job: '"+jobID+"'");
      int statusValue = status.getStatus();
      switch (statusValue)
      {
        case JobStatus.JOBSTATUS_NOTYETRUN:
          throw new ManifoldCFException("Job was never started.");
        case JobStatus.JOBSTATUS_COMPLETED:
          break;
        case JobStatus.JOBSTATUS_ERROR:
          throw new ManifoldCFException("Job reports error status: "+status.getErrorText());
        default:
          ManifoldCF.sleep(1000L);
          continue;
      }
      return;
    }
    throw new ManifoldCFException("ManifoldCF did not terminate in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
  
  public void waitJobDeletedNative(IJobManager jobManager, Long jobID, long maxTime)
    throws ManifoldCFException, InterruptedException
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      JobStatus status = jobManager.getStatus(jobID);
      if (status == null)
        return;
      ManifoldCF.sleep(1000L);
    }
    throw new ManifoldCFException("ManifoldCF did not delete in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
    
  public void waitJobRunningNative(IJobManager jobManager, Long jobID, long maxTime)
    throws ManifoldCFException, InterruptedException
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      JobStatus status = jobManager.getStatus(jobID);
      if (status == null)
        throw new ManifoldCFException("No such job: '"+jobID+"'");
      int statusValue = status.getStatus();
      switch (statusValue)
      {
        case JobStatus.JOBSTATUS_NOTYETRUN:
          throw new ManifoldCFException("Job was never started.");
        case JobStatus.JOBSTATUS_COMPLETED:
          throw new ManifoldCFException("Job ended on its own!");
        case JobStatus.JOBSTATUS_ERROR:
          throw new ManifoldCFException("Job reports error status: "+status.getErrorText());
        case JobStatus.JOBSTATUS_RUNNING:
          break;
        default:
          ManifoldCF.sleep(1000L);
          continue;
      }
      return;
    }
    throw new ManifoldCFException("ManifoldCF did not start in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }

  // API support
  
  // These methods allow communication with the ManifoldCF api webapp, via the locally-instantiated jetty
  
  public void startJobAPI(String jobIDString)
    throws Exception
  {
    Configuration requestObject = new Configuration();
    
    Configuration result = performAPIPutOperationViaNodes("start/"+jobIDString,201,requestObject);
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
    }
  }
  
  public void deleteJobAPI(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIDeleteOperationViaNodes("jobs/"+jobIDString,200);
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
    }

  }
  
  public String getJobStatusAPI(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIGetOperationViaNodes("jobstatusesnocounts/"+jobIDString,200);
    String status = null;
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
      else if (resultNode.getType().equals("jobstatus"))
      {
        int j = 0;
        while (j < resultNode.getChildCount())
        {
          ConfigurationNode childNode = resultNode.findChild(j++);
          if (childNode.getType().equals("status"))
            status = childNode.getValue();
        }
      }
    }
    return status;
  }

  public long getJobDocumentsProcessedAPI(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIGetOperationViaNodes("jobstatuses/"+jobIDString,200);
    String documentsProcessed = null;
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
      else if (resultNode.getType().equals("jobstatus"))
      {
        int j = 0;
        while (j < resultNode.getChildCount())
        {
          ConfigurationNode childNode = resultNode.findChild(j++);
          if (childNode.getType().equals("documents_processed"))
            documentsProcessed = childNode.getValue();
        }
      }
    }
    if (documentsProcessed == null)
      throw new Exception("Expected a documents_processed field, didn't find it");
    return new Long(documentsProcessed).longValue();
  }

  public void waitJobInactiveAPI(String jobIDString, long maxTime)
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatusAPI(jobIDString);
      if (status == null)
        throw new Exception("No such job: '"+jobIDString+"'");
      if (status.equals("not yet run"))
        throw new Exception("Job was never started.");
      if (status.equals("done"))
        return;
      if (status.equals("error"))
        throw new Exception("Job reports error.");
      ManifoldCF.sleep(1000L);
      continue;
    }
    throw new ManifoldCFException("ManifoldCF did not terminate in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
  
  public void waitJobDeletedAPI(String jobIDString, long maxTime)
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatusAPI(jobIDString);
      if (status == null)
        return;
      ManifoldCF.sleep(1000L);
    }
    throw new ManifoldCFException("ManifoldCF did not delete in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
    
  /** Construct a command url.
  */
  public String makeAPIURL(String command)
  {
    return "http://localhost:"+Integer.toString(testPort)+"/mcf-api-service/json/"+command;
  }
  
  /** Perform an json API GET operation.
  *@param apiURL is the operation.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  public String performAPIGetOperation(String apiURL, int expectedResponse)
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
  public String performAPIDeleteOperation(String apiURL, int expectedResponse)
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
  public String performAPIPutOperation(String apiURL, int expectedResponse, String input)
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
  public String performAPIPostOperation(String apiURL, int expectedResponse, String input)
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
  public Configuration performAPIGetOperationViaNodes(String command, int expectedResponse)
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
  public Configuration performAPIDeleteOperationViaNodes(String command, int expectedResponse)
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
  public Configuration performAPIPutOperationViaNodes(String command, int expectedResponse, Configuration argument)
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
  public Configuration performAPIPostOperationViaNodes(String command, int expectedResponse, Configuration argument)
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
  
  public void start()
    throws Exception
  {
    // Start jetty
    server = new Server( testPort );    
    server.setStopAtShutdown( true );
    
    String crawlerWarPath = "../../framework/build/war-proprietary/mcf-crawler-ui.war";
    String authorityserviceWarPath = "../../framework/build/war-proprietary/mcf-authority-service.war";
    String apiWarPath = "../../framework/build/war-proprietary/mcf-api-service.war";

    if (System.getProperty("crawlerWarPath") != null)
    	crawlerWarPath = System.getProperty("crawlerWarPath");
    if (System.getProperty("authorityserviceWarPath") != null)
    	authorityserviceWarPath = System.getProperty("authorityserviceWarPath");
    if (System.getProperty("apiWarPath") != null)
    	apiWarPath = System.getProperty("apiWarPath");

    
    // Initialize the servlets
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    server.setHandler(contexts);
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/mcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    contexts.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityserviceWarPath,"/mcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    contexts.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/mcf-api-service");
    lcfApi.setParentLoaderPriority(true);
    contexts.addHandler(lcfApi);
    server.start();

    // If all worked, then we can start the daemon.
    // Clear the agents shutdown signal.
    IThreadContext tc = ThreadContextFactory.make();
    ILockManager lockManager = LockManagerFactory.make(tc);
    lockManager.clearGlobalFlag(agentShutdownSignal);

    daemonThread = new DaemonThread();
    daemonThread.start();
  }
  
  public void stop()
    throws Exception
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
      
    if (currentException != null)
      throw currentException;
  }
  
  public void unload()
    throws Exception
  {
    if (server != null)
    {
      // Unfortunately, this causes the shutdown hooks to be called, which causes
      // no end of trouble unless it is done last.
      server.stop();
      server.join();
      server = null;
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
