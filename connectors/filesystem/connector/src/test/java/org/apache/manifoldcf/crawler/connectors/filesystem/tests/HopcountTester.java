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

/** This is a test which checks to be sure hopcount functionality is working properly. */
public class HopcountTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public HopcountTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
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
    IThreadContext tc = ThreadContextFactory.make();
      
    // Create a basic file system connection, and save it.
    IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(tc);
    IRepositoryConnection conn = mgr.create();
    conn.setName("File Connection");
    conn.setDescription("File Connection");
    conn.setClassName("org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector");
    conn.setMaxConnections(100);
    // Now, save
    mgr.save(conn);
      
    // Create a basic null output connection, and save it.
    IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);
    IOutputConnection outputConn = outputMgr.create();
    outputConn.setName("Null Connection");
    outputConn.setDescription("Null Connection");
    outputConn.setClassName("org.apache.manifoldcf.agents.tests.TestingOutputConnector");
    outputConn.setMaxConnections(100);
    // Now, save
    outputMgr.save(outputConn);

    // Create a job.
    IJobManager jobManager = JobManagerFactory.make(tc);
    IJobDescription job = jobManager.createJob();
    job.setDescription("Test Job");
    job.setConnectionName("File Connection");
    job.addPipelineStage(-1,true,"Null Connection","");
    job.setType(job.TYPE_SPECIFIED);
    job.setStartMethod(job.START_DISABLE);
    job.setHopcountMode(job.HOPCOUNT_ACCURATE);
    job.addHopCountFilter("child",new Long(2));
      
    // Now, set up the document specification.
    Specification ds = job.getSpecification();
    // Crawl everything underneath the 'testdata' area
    File testDataFile = new File("testdata").getCanonicalFile();
    if (!testDataFile.exists())
      throw new ManifoldCFException("Test data area not found!  Looking in "+testDataFile.toString());
    if (!testDataFile.isDirectory())
      throw new ManifoldCFException("Test data area not a directory!  Looking in "+testDataFile.toString());
    SpecificationNode sn = new SpecificationNode("startpoint");
    sn.setAttribute("path",testDataFile.toString());
    SpecificationNode n = new SpecificationNode("include");
    n.setAttribute("type","file");
    n.setAttribute("match","*");
    sn.addChild(sn.getChildCount(),n);
    n = new SpecificationNode("include");
    n.setAttribute("type","directory");
    n.setAttribute("match","*");
    sn.addChild(sn.getChildCount(),n);
    ds.addChild(ds.getChildCount(),sn);
      
    // Save the job.
    jobManager.save(job);

    // Create the test data files.
    FileHelper.createFile(new File("testdata/test1.txt"),"This is a test file");
    FileHelper.createFile(new File("testdata/test2.txt"),"This is another test file");
    FileHelper.createDirectory(new File("testdata/testdir"));
    FileHelper.createFile(new File("testdata/testdir/test3.txt"),"This is yet another test file");
    FileHelper.createDirectory(new File("testdata/testdir/seconddir"));
    FileHelper.createFile(new File("testdata/testdir/seconddir/test4.txt"),"Lowest test file");
      
    // Now, start the job, and wait until it completes.
    jobManager.manualStart(job.getID());
    instance.waitJobInactiveNative(jobManager,job.getID(),120000L);

    // Check to be sure we actually processed the right number of documents.
    JobStatus status = jobManager.getStatus(job.getID());
    // The test data area has 4 documents and 2 directories and we have to count the root directory too.
    // But the max hopcount is 2, so one file will be left behind, so the count should be 6, not 7.
    if (status.getDocumentsProcessed() != 6)
      throw new ManifoldCFException("Wrong number of documents processed - expected 6, saw "+new Long(status.getDocumentsProcessed()).toString());
      
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(), 120000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
