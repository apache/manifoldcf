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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class APISanityHSQLDBIT extends BaseITHSQLDB
{
  private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHss", Locale.ROOT);

  //@TODO - Should be moved into AlfrescoConnector.java
  public static final String ALFRESCO_PROTOCOL_PARAM = "protocol";
  public static final String ALFRESCO_HOSTNAME_PARAM = "hostname";
  public static final String ALFRESCO_PORT_PARAM = "port";
  public static final String ALFRESCO_ENDPOINT_PARAM = "/alfresco/service";
  public static final String ALFRESCO_STOREPROTOCOL_PARAM = "storeprotocol";
  public static final String ALFRESCO_STOREID_PARAM = "storeid";
  public static final String ALFRESCO_USERNAME_PARAM = "username";
  public static final String ALFRESCO_PASSWORD_PARAM = "password";

  public static final String ALFRESCO_PROTOCOL = "http";
  public static final String ALFRESCO_HOSTNAME = "localhost";
  public static final String ALFRESCO_PORT = "9090";
  public static final String ALFRESCO_ENDPOINT = "/alfresco/service";
  public static final String ALFRESCO_STOREPROTOCOL = "workspace";
  public static final String ALFRESCO_STOREID = "SpacesStore";
  public static final String ALFRESCO_USERNAME = "admin";
  public static final String ALFRESCO_PASSWORD = "admin";

  @Before
  public void createTestArea()
    throws Exception
  {
    removeTestArea();
    
    try
    {
      //Adding a document in Alfresco via CMIS
      CMISUtils cdc = new CMISUtils();
      cdc.setServiceUrl(ALFRESCO_PROTOCOL+"://"+ALFRESCO_HOSTNAME+":"+ALFRESCO_PORT + "/alfresco/api/-default-/public/cmis/versions/1.1/atom");
      cdc.setUser("admin");
      cdc.setPassword("admin");
      cdc.createDocument("test" + "." + sdf.format(new Date()), "cmis:document");

      //@TODO - Add more logic to push documents into Alfresco
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
      child.setValue("Alfresco Repository Connector");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("10");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("configuration");
      
      //Alfresco Repository Connector parameters

      //protocol
      ConfigurationNode alfrescoProtocolNode = new ConfigurationNode("_PARAMETER_");
      alfrescoProtocolNode.setAttribute("name", ALFRESCO_PROTOCOL_PARAM);
      alfrescoProtocolNode.setValue(ALFRESCO_PROTOCOL);
      child.addChild(child.getChildCount(), alfrescoProtocolNode);
      
      //server
      ConfigurationNode alfrescoServerNode = new ConfigurationNode("_PARAMETER_");
      alfrescoServerNode.setAttribute("name", ALFRESCO_HOSTNAME_PARAM);
      alfrescoServerNode.setValue(ALFRESCO_HOSTNAME);
      child.addChild(child.getChildCount(), alfrescoServerNode);
      
      //port
      ConfigurationNode alfrescoPortNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPortNode.setAttribute("name", ALFRESCO_PORT_PARAM);
      alfrescoPortNode.setValue(ALFRESCO_PORT);
      child.addChild(child.getChildCount(), alfrescoPortNode);
      
      //endpoint
      ConfigurationNode alfrescoEndpointNode = new ConfigurationNode("_PARAMETER_");
      alfrescoEndpointNode.setAttribute("name", ALFRESCO_ENDPOINT_PARAM);
      alfrescoEndpointNode.setValue(ALFRESCO_ENDPOINT);
      child.addChild(child.getChildCount(), alfrescoEndpointNode);

      //storeProtocol
      ConfigurationNode alfrescoStoreProtocol = new ConfigurationNode("_PARAMETER_");
      alfrescoStoreProtocol.setAttribute("name", ALFRESCO_STOREPROTOCOL_PARAM);
      alfrescoStoreProtocol.setValue(ALFRESCO_STOREPROTOCOL);
      child.addChild(child.getChildCount(), alfrescoStoreProtocol);

      //storeId
      ConfigurationNode alfrescoStoreId = new ConfigurationNode("_PARAMETER_");
      alfrescoStoreId.setAttribute("name", ALFRESCO_STOREID_PARAM);
      alfrescoStoreId.setValue(ALFRESCO_STOREID);
      child.addChild(child.getChildCount(), alfrescoStoreId);

      //username
      ConfigurationNode alfrescoUsernameNode = new ConfigurationNode("_PARAMETER_");
      alfrescoUsernameNode.setAttribute("name", ALFRESCO_USERNAME_PARAM);
      alfrescoUsernameNode.setValue(ALFRESCO_USERNAME);
      child.addChild(child.getChildCount(), alfrescoUsernameNode);

      //password
      ConfigurationNode alfrescoPasswordNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPasswordNode.setAttribute("name", ALFRESCO_PASSWORD_PARAM);
      alfrescoPasswordNode.setValue(ManifoldCF.obfuscate(ALFRESCO_PASSWORD));
      child.addChild(child.getChildCount(), alfrescoPasswordNode);

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


      // Create a job.
      ConfigurationNode jobObject = new ConfigurationNode("job");

      child = new ConfigurationNode("description");
      child.setValue("Test Job");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("repository_connection");
      child.setValue("Alfresco Connector");
      jobObject.addChild(jobObject.getChildCount(),child);

      // Revamped way of adding output connection
      child = new ConfigurationNode("pipelinestage");
      ConfigurationNode pipelineChild = new ConfigurationNode("stage_id");
      pipelineChild.setValue("0");
      child.addChild(child.getChildCount(),pipelineChild);
      pipelineChild = new ConfigurationNode("stage_isoutput");
      pipelineChild.setValue("true");
      child.addChild(child.getChildCount(),pipelineChild);
      pipelineChild = new ConfigurationNode("stage_connectionname");
      pipelineChild.setValue("Null Connection");
      child.addChild(child.getChildCount(),pipelineChild);
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("run_mode");
      child.setValue("scan once");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("start_mode");
      child.setValue("manual");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("hopcount_mode");
      child.setValue("accurate");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("document_specification");
      jobObject.addChild(jobObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,jobObject);

      result = performAPIPostOperationViaNodes("jobs",201,requestObject);

      String jobIDString = null;
      i = 0;
      while (i < result.getChildCount())
      {
        ConfigurationNode resultNode = result.findChild(i++);

        if (resultNode.getType().equals("error"))
          throw new Exception(resultNode.getValue());
        else if (resultNode.getType().equals("job_id"))
          jobIDString = resultNode.getValue();
      }
      if (jobIDString == null)
        throw new Exception("Missing job_id from return!");

      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 120000L);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      long count;
      count = getJobDocumentsProcessed(jobIDString);

      if (count == 0)
        throw new ManifoldCFException("No documents processed");
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
    while (i < result.getChildCount()) {
      ConfigurationNode resultNode = result.findChild(i++);

      if (resultNode.getType().equals("error")) {
        throw new Exception(resultNode.getValue());
      } else if (resultNode.getType().equals("jobstatus"))
      {
        int j = 0;
        while (j < resultNode.getChildCount())
        {
          ConfigurationNode childNode = resultNode.findChild(j++);
          if (childNode.getType().equals("status")) {
            status = childNode.getValue();
            System.out.println("Type: "+resultNode.getType());
            System.out.println("Status: "+status);
          }

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
