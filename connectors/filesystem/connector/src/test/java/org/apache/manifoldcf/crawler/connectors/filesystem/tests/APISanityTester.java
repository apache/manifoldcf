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
package org.apache.manifoldcf.crawler.connectors.filesystem.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;

/** This is a very basic sanity check */
public class APISanityTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public APISanityTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
  {
    this.instance = instance;
  }
  
  public void setupTestArea()
    throws Exception
  {
    File f = new File("testdata");
    FileHelper.removeDirectory(f);
    FileHelper.createDirectory(f);
  }
  
  public void teardownTestArea()
    throws Exception
  {
    File f = new File("testdata");
    FileHelper.removeDirectory(f);
  }
  
  public void executeTest()
    throws Exception
  {
    // Hey, we were able to install the file system connector etc.
    // Now, create a local test job and run it.
    int i;

    // Create a basic file system connection, and save it.
    ConfigurationNode connectionObject;
    ConfigurationNode child;
    Configuration requestObject;
    Configuration result;

    instance.loginAPI("","");
    
    connectionObject = new ConfigurationNode("repositoryconnection");
    
    child = new ConfigurationNode("name");
    child.setValue("File Connection");
    connectionObject.addChild(connectionObject.getChildCount(),child);
      
    child = new ConfigurationNode("class_name");
    child.setValue("org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector");
    connectionObject.addChild(connectionObject.getChildCount(),child);
      
    child = new ConfigurationNode("description");
    child.setValue("File Connection");
    connectionObject.addChild(connectionObject.getChildCount(),child);

    child = new ConfigurationNode("max_connections");
    child.setValue("100");
    connectionObject.addChild(connectionObject.getChildCount(),child);

    requestObject = new Configuration();
    requestObject.addChild(0,connectionObject);
      
    result = instance.performAPIPutOperationViaNodes("repositoryconnections/File%20Connection",201,requestObject);
      
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
      
    result = instance.performAPIPutOperationViaNodes("outputconnections/Null%20Connection",201,requestObject);
      
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
    // Crawl everything underneath the 'testdata' area
    File testDataFile = new File("testdata").getCanonicalFile();
    if (!testDataFile.exists())
      throw new ManifoldCFException("Test data area not found!  Looking in "+testDataFile.toString());
    if (!testDataFile.isDirectory())
      throw new ManifoldCFException("Test data area not a directory!  Looking in "+testDataFile.toString());
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
      
    result = instance.performAPIPostOperationViaNodes("jobs",201,requestObject);
      
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
    FileHelper.createFile(new File("testdata/test1.txt"),"This is a test file");
    FileHelper.createFile(new File("testdata/test2.txt"),"This is another test file");
    FileHelper.createDirectory(new File("testdata/testdir"));
    FileHelper.createFile(new File("testdata/testdir/test3.txt"),"This is yet another test file");
      
    ConfigurationNode requestNode;
      
    // Now, start the job, and wait until it completes.
    instance.startJobAPI(jobIDString);
    instance.waitJobInactiveAPI(jobIDString, 120000L);

    // Check to be sure we actually processed the right number of documents.
    // The test data area has 3 documents and one directory, and we have to count the root directory too.
    long count;
    count = instance.getJobDocumentsProcessedAPI(jobIDString);
    if (count != 5)
      throw new ManifoldCFException("Wrong number of documents processed - expected 5, saw "+new Long(count).toString());
      
    // Add a file and recrawl
    FileHelper.createFile(new File("testdata/testdir/test4.txt"),"Added file");

    // Now, start the job, and wait until it completes.
    instance.startJobAPI(jobIDString);
    instance.waitJobInactiveAPI(jobIDString, 120000L);

    // The test data area has 4 documents and one directory, and we have to count the root directory too.
    count = instance.getJobDocumentsProcessedAPI(jobIDString);
    if (count != 6)
      throw new ManifoldCFException("Wrong number of documents processed after add - expected 6, saw "+new Long(count).toString());

    // Change a file, and recrawl
    FileHelper.changeFile(new File("testdata/test1.txt"),"Modified contents");
      
    // Now, start the job, and wait until it completes.
    instance.startJobAPI(jobIDString);
    instance.waitJobInactiveAPI(jobIDString, 120000L);

    // The test data area has 4 documents and one directory, and we have to count the root directory too.
    count = instance.getJobDocumentsProcessedAPI(jobIDString);
    if (count != 6)
      throw new ManifoldCFException("Wrong number of documents processed after change - expected 6, saw "+new Long(count).toString());
    // We also need to make sure the new document was indexed.  Have to think about how to do this though.
    // MHL
      
    // Delete a file, and recrawl
    FileHelper.removeFile(new File("testdata/test2.txt"));
      
    // Now, start the job, and wait until it completes.
    instance.startJobAPI(jobIDString);
    instance.waitJobInactiveAPI(jobIDString, 120000L);

    // Check to be sure we actually processed the right number of documents.
    // The test data area has 3 documents and one directory, and we have to count the root directory too.
    count = instance.getJobDocumentsProcessedAPI(jobIDString);
    if (count != 5)
      throw new ManifoldCFException("Wrong number of documents processed after delete - expected 5, saw "+new Long(count).toString());

    // Have a try to get the history records for the connection
    result = instance.performAPIGetOperationViaNodes("repositoryconnectionhistory/File%20Connection?report=simple",200);

    // Now, delete the job.
    instance.deleteJobAPI(jobIDString);

    instance.waitJobDeletedAPI(jobIDString, 120000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
