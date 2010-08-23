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
package org.apache.acf.filesystem_tests;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.ACF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a very basic sanity check */
public class APISanity extends TestBase
{
  
  @Before
  public void createTestArea()
    throws Exception
  {
    try
    {
      File f = new File("testdata");
      removeDirectory(f);
      createDirectory(f);
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
    try
    {
      File f = new File("testdata");
      removeDirectory(f);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
  @Test
  public void sanityCheck()
    throws Exception
  {
    try
    {
      // Hey, we were able to install the file system connector etc.
      // Now, create a local test job and run it.
      IThreadContext tc = ThreadContextFactory.make();
      int i;
      IJobManager jobManager = JobManagerFactory.make(tc);

      // Create a basic file system connection, and save it.
      ConfigurationNode connectionObject;
      ConfigurationNode child;
      Configuration requestObject;
      Configuration result;
      
      connectionObject = new ConfigurationNode("repositoryconnection");
      
      child = new ConfigurationNode("name");
      child.setValue("File Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.acf.crawler.connectors.filesystem.FileConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("File Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("100");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIOperationViaNodes("repositoryconnection/save",requestObject);
      
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
      child.setValue("org.apache.acf.agents.output.nullconnector.NullConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("Null Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("100");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIOperationViaNodes("outputconnection/save",requestObject);
      
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
      child.setValue("File Connection");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("output_connection");
      child.setValue("Null Connection");
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
      // Crawl everything underneath the 'testdata' area
      File testDataFile = new File("testdata").getCanonicalFile();
      if (!testDataFile.exists())
        throw new ACFException("Test data area not found!  Looking in "+testDataFile.toString());
      if (!testDataFile.isDirectory())
        throw new ACFException("Test data area not a directory!  Looking in "+testDataFile.toString());
      ConfigurationNode sn = new ConfigurationNode("startpoint");
      sn.setAttribute("path",testDataFile.toString());
      ConfigurationNode n = new ConfigurationNode("include");
      n.setAttribute("type","file");
      n.setAttribute("match","*");
      sn.addChild(sn.getChildCount(),n);
      n = new ConfigurationNode("include");
      n.setAttribute("type","directory");
      n.setAttribute("match","*");
      sn.addChild(sn.getChildCount(),n);
      child.addChild(child.getChildCount(),sn);
      jobObject.addChild(jobObject.getChildCount(),child);
      
      requestObject = new Configuration();
      requestObject.addChild(0,jobObject);
      
      result = performAPIOperationViaNodes("job/save",requestObject);
      
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
      
      Long jobID = new Long(jobIDString);
      
      // Create the test data files.
      createFile(new File("testdata/test1.txt"),"This is a test file");
      createFile(new File("testdata/test2.txt"),"This is another test file");
      createDirectory(new File("testdata/testdir"));
      createFile(new File("testdata/testdir/test3.txt"),"This is yet another test file");
      
      ConfigurationNode requestNode;
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      long count;
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ACFException("Wrong number of documents processed - expected 5, saw "+new Long(count).toString());
      
      // Add a file and recrawl
      createFile(new File("testdata/testdir/test4.txt"),"Added file");

      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 6)
        throw new ACFException("Wrong number of documents processed after add - expected 6, saw "+new Long(count).toString());

      // Change a file, and recrawl
      changeFile(new File("testdata/test1.txt"),"Modified contents");
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 6)
        throw new ACFException("Wrong number of documents processed after change - expected 6, saw "+new Long(count).toString());
      // We also need to make sure the new document was indexed.  Have to think about how to do this though.
      // MHL
      
      // Delete a file, and recrawl
      removeFile(new File("testdata/test2.txt"));
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ACFException("Wrong number of documents processed after delete - expected 5, saw "+new Long(count).toString());

      // Now, delete the job.
      deleteJob(jobIDString);

      waitJobDeleted(jobIDString);
      
      // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
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
    ConfigurationNode requestNode = new ConfigurationNode("job_id");
    requestNode.setValue(jobIDString);
    Configuration requestObject = new Configuration();
    requestObject.addChild(0,requestNode);
    
    Configuration result = performAPIOperationViaNodes("jobstatus/start",requestObject);
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
    ConfigurationNode requestNode = new ConfigurationNode("job_id");
    requestNode.setValue(jobIDString);
    Configuration requestObject = new Configuration();
    requestObject.addChild(0,requestNode);
      
    Configuration result = performAPIOperationViaNodes("job/delete",requestObject);
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
    ConfigurationNode requestNode = new ConfigurationNode("job_id");
    requestNode.setValue(jobIDString);
    Configuration requestObject = new Configuration();
    requestObject.addChild(0,requestNode);
    
    Configuration result = performAPIOperationViaNodes("jobstatus/get",requestObject);
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
    ConfigurationNode requestNode = new ConfigurationNode("job_id");
    requestNode.setValue(jobIDString);
    Configuration requestObject = new Configuration();
    requestObject.addChild(0,requestNode);
    
    Configuration result = performAPIOperationViaNodes("jobstatus/get",requestObject);
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

  protected void waitJobInactive(String jobIDString)
    throws Exception
  {
    while (true)
    {
      String status = getJobStatus(jobIDString);
      if (status == null)
        throw new Exception("No such job: '"+jobIDString+"'");
      if (status.equals("not yet run"))
        throw new Exception("Job was never started.");
      if (status.equals("done"))
        break;
      if (status.equals("error"))
        throw new Exception("Job reports error.");
      ACF.sleep(10000L);
      continue;
    }
  }
  
  protected void waitJobDeleted(String jobIDString)
    throws Exception
  {
    while (true)
    {
      String status = getJobStatus(jobIDString);
      if (status == null)
        break;
      ACF.sleep(10000L);
    }
  }
    

}
