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

/** Tests that run the "agents daemon" should be derived from this */
public class BaseITHSQLDB extends ConnectorBaseHSQLDB
{
  protected final ManifoldCFInstance mcfInstance;
  
  public BaseITHSQLDB()
  {
    super();
    mcfInstance = new ManifoldCFInstance();
  }
  
  public BaseITHSQLDB(boolean singleWar)
  {
    super();
    mcfInstance = new ManifoldCFInstance(singleWar);
  }
  
  public BaseITHSQLDB(boolean singleWar, boolean webapps)
  {
    super();
    mcfInstance = new ManifoldCFInstance(singleWar, webapps);
  }
  
  // Basic job support
  
  protected void waitJobInactiveNative(IJobManager jobManager, Long jobID, long maxTime)
    throws ManifoldCFException, InterruptedException
  {
    mcfInstance.waitJobInactiveNative(jobManager,jobID,maxTime);
  }
  
  protected void waitJobDeletedNative(IJobManager jobManager, Long jobID, long maxTime)
    throws ManifoldCFException, InterruptedException
  {
    mcfInstance.waitJobDeletedNative(jobManager,jobID,maxTime);
  }

  // API support
  
  // These methods allow communication with the ManifoldCF api webapp, via the locally-instantiated jetty
  
  protected void startJobAPI(String jobIDString)
    throws Exception
  {
    mcfInstance.startJobAPI(jobIDString);
  }
  
  protected void deleteJobAPI(String jobIDString)
    throws Exception
  {
    mcfInstance.deleteJobAPI(jobIDString);
  }

  protected String getJobStatusAPI(String jobIDString)
    throws Exception
  {
    return mcfInstance.getJobStatusAPI(jobIDString);
  }
  
  protected long getJobDocumentsProcessedAPI(String jobIDString)
    throws Exception
  {
    return mcfInstance.getJobDocumentsProcessedAPI(jobIDString);
  }

  protected void waitJobInactiveAPI(String jobIDString, long maxTime)
    throws Exception
  {
    mcfInstance.waitJobInactiveAPI(jobIDString,maxTime);
  }

  protected void waitJobDeletedAPI(String jobIDString, long maxTime)
    throws Exception
  {
    mcfInstance.waitJobDeletedAPI(jobIDString,maxTime);
  }
  
  /** Construct a command url.
  */
  protected String makeAPIURL(String command)
    throws Exception
  {
    return mcfInstance.makeAPIURL(command);
  }
  
  /** Perform an json API GET operation.
  *@param apiURL is the operation.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIGetOperation(String apiURL, int expectedResponse)
    throws Exception
  {
    return mcfInstance.performAPIGetOperation(apiURL,expectedResponse);
  }

  /** Perform an json API DELETE operation.
  *@param apiURL is the operation.
  *@param expectedResponse is the expected response code.
  *@return the json response.
  */
  protected String performAPIDeleteOperation(String apiURL, int expectedResponse)
    throws Exception
  {
    return mcfInstance.performAPIDeleteOperation(apiURL,expectedResponse);
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
    return mcfInstance.performAPIPutOperation(apiURL,expectedResponse,input);
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
    return mcfInstance.performAPIPostOperation(apiURL,expectedResponse,input);
  }

  /** Perform a json GET API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIGetOperationViaNodes(String command, int expectedResponse)
    throws Exception
  {
    return mcfInstance.performAPIGetOperationViaNodes(command,expectedResponse);
  }

  /** Perform a json DELETE API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIDeleteOperationViaNodes(String command, int expectedResponse)
    throws Exception
  {
    return mcfInstance.performAPIDeleteOperationViaNodes(command,expectedResponse);
  }

  /** Perform a json PUT API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIPutOperationViaNodes(String command, int expectedResponse, Configuration argument)
    throws Exception
  {
    return mcfInstance.performAPIPutOperationViaNodes(command,expectedResponse,argument);
  }

  /** Perform a json POST API operation, using Configuration structures to represent the json.  This is for testing convenience,
  * mostly.
  */
  protected Configuration performAPIPostOperationViaNodes(String command, int expectedResponse, Configuration argument)
    throws Exception
  {
    return mcfInstance.performAPIPostOperationViaNodes(command,expectedResponse,argument);
  }

  // Setup/teardown
  
  @Before
  public void setUp()
    throws Exception
  {
    initializeSystem();
    try
    {
      localReset();
    }
    catch (Exception e)
    {
      System.out.println("Warning: Preclean failed: "+e.getMessage());
    }
    try
    {
      localSetUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
    mcfInstance.start();
  }
  
  @After
  public void cleanUp()
    throws Exception
  {
    Exception currentException = null;
    try
    {
      mcfInstance.stop();
    }
    catch (Exception e)
    {
      if (currentException == null)
        currentException = e;
    }
    // Last, shut down the web applications.
    // If this is done too soon it closes the database before the rest of the cleanup happens.
    try
    {
      mcfInstance.unload();
    }
    catch (Exception e)
    {
      if (currentException == null)
        currentException = e;
    }
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
    if (currentException != null)
      throw currentException;
    cleanupSystem();
  }
  
}
