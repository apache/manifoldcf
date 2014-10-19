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

package org.apache.manifoldcf.crawler.connectors.alfrescowebscript.tests;

import org.apache.manifoldcf.core.interfaces.Configuration;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class APISanityHSQLDBIT extends BaseITHSQLDB
{
  
  private static final String REPLACER = "?";
  private static final String ALFRESCO_TEST_QUERY = "PATH:\"/app:company_home/cm:testdata\"";
  
  private static final String ALFRESCO_USERNAME = "admin"; 
  private static final String ALFRESCO_PASSWORD = "admin";
  private static final String ALFRESCO_PROTOCOL = "http";
  private static final String ALFRESCO_HOST = "localhost";
  private static final String ALFRESCO_PORT = "9090";
  private static final String ALFRESCO_CONTEXT = "/alfresco";
  private static final int SOCKET_TIMEOUT = 120000;
  private static final String ALFRESCO_ENDPOINT_TEST_SERVER = 
      ALFRESCO_PROTOCOL+"://"+ALFRESCO_HOST+":"+ALFRESCO_PORT+ALFRESCO_CONTEXT;

  @Before
  public void createTestArea()
    throws Exception
  {
    removeTestArea();
    
    try
    {
      //@TODO - Add cmis client logic to push some documents into Alfresco
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
  @After
  public void removeTestArea()
    throws Exception
  {
  }
  
  @Test
  public void sanityCheck()
    throws Exception
  {
    try
    {
      
      int i;

      // Create a basic file system connection, and save it.
      ConfigurationNode connectionObject;
      ConfigurationNode child;
      Configuration requestObject;
      Configuration result;
      
      connectionObject = new ConfigurationNode("repositoryconnection");
      
      child = new ConfigurationNode("name");
      child.setValue("Alfresco Connector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.manifoldcf.crawler.connectors.alfrescowebscript.AlfrescoConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("An Alfresco Repository Connector");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("10");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("configuration");
      
      //Alfresco Repository Connector parameters
      
      //username
      ConfigurationNode alfrescoUsernameNode = new ConfigurationNode("_PARAMETER_");
      alfrescoUsernameNode.setAttribute("name", ALFRESCO_USERNAME);
      alfrescoUsernameNode.setValue(ALFRESCO_USERNAME);
      child.addChild(child.getChildCount(), alfrescoUsernameNode);
      
      //password
      ConfigurationNode alfrescoPasswordNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPasswordNode.setAttribute("name", ALFRESCO_PASSWORD);
      alfrescoPasswordNode.setValue(ALFRESCO_PASSWORD);
      child.addChild(child.getChildCount(), alfrescoPasswordNode);
      
      //protocol
      ConfigurationNode alfrescoProtocolNode = new ConfigurationNode("_PARAMETER_");
      alfrescoProtocolNode.setAttribute("name", ALFRESCO_PROTOCOL);
      alfrescoProtocolNode.setValue(ALFRESCO_PROTOCOL);
      child.addChild(child.getChildCount(), alfrescoProtocolNode);
      
      //server
      ConfigurationNode alfrescoServerNode = new ConfigurationNode("_PARAMETER_");
      alfrescoServerNode.setAttribute("name", ALFRESCO_HOST);
      alfrescoServerNode.setValue(ALFRESCO_HOST);
      child.addChild(child.getChildCount(), alfrescoServerNode);
      
      //port
      ConfigurationNode alfrescoPortNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPortNode.setAttribute("name", ALFRESCO_PORT);
      alfrescoPortNode.setValue(ALFRESCO_PORT);
      child.addChild(child.getChildCount(), alfrescoPortNode);
      
      //path
      ConfigurationNode alfrescoPathNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPathNode.setAttribute("name", ALFRESCO_CONTEXT);
      alfrescoPathNode.setValue(ALFRESCO_CONTEXT);
      child.addChild(child.getChildCount(), alfrescoPathNode);
      
      //socketTimeout
      ConfigurationNode socketTimeoutNode = new ConfigurationNode("_PARAMETER_");
      socketTimeoutNode.setAttribute("name", "60000");
      socketTimeoutNode.setValue(String.valueOf(SOCKET_TIMEOUT));
      child.addChild(child.getChildCount(), socketTimeoutNode);
      
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIPutOperationViaNodes("repositoryconnections/Alfresco%20Connector",201,requestObject);

      i = 0;
      while (i < result.getChildCount())
      {
        ConfigurationNode resultNode = result.findChild(i++);
        if (resultNode.getType().equals("error"))
          throw new Exception(resultNode.getValue());
      }
      
      // Create a basic null output connection, and save it.
      connectionObject = new ConfigurationNode("outputconnection");
      
      child = new ConfigurationNode("name");
      child.setValue("Null Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.manifoldcf.agents.tests.TestingOutputConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("Null Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("100");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIPutOperationViaNodes("outputconnections/Null%20Connection",201,requestObject);
      
      i = 0;
      while (i < result.getChildCount())
      {
        ConfigurationNode resultNode = result.findChild(i++);
        if (resultNode.getType().equals("error"))
          throw new Exception(resultNode.getValue());
      }

      //@TODO - Test Job execution
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
  protected void startJob(String jobIDString)
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
  
  protected void deleteJob(String jobIDString)
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
  
  protected String getJobStatus(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIGetOperationViaNodes("jobstatuses/"+jobIDString,200);
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

  protected long getJobDocumentsProcessed(String jobIDString)
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

  protected void waitJobInactive(String jobIDString, long maxTime)
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatus(jobIDString);
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
  
  protected void waitJobDeleted(String jobIDString, long maxTime)
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatus(jobIDString);
      if (status == null)
        return;
      ManifoldCF.sleep(1000L);
    }
    throw new ManifoldCFException("ManifoldCF did not delete in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
}
