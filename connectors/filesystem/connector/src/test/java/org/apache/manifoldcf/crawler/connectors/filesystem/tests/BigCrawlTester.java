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

/** This is a 100000 document crawl */
public class BigCrawlTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public BigCrawlTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
  {
    this.instance = instance;
  }
  
  public void setupTestArea()
    throws Exception
  {
    File f = new File("testdata");
    FileHelper.removeDirectory(f);
    FileHelper.createDirectory(f);
    // Create the test data files.
    String baseFileName = "testdata/";
    int i0 = 0;
    while (i0 < 10)
    {
      String fileName0 = baseFileName + "/dir-" + i0;
      FileHelper.createDirectory(new File(fileName0));
      int i1 = 0;
      while (i1 < 10)
      {
        String fileName1 = fileName0 + "/dir-" + i1;
        FileHelper.createDirectory(new File(fileName1));
        int i2 = 0;
        while (i2 < 10)
        {
          String fileName2 = fileName1 + "/dir-" + i2;
          FileHelper.createDirectory(new File(fileName2));
          int i3 = 0;
          while (i3 < 10)
          {
            String fileName3 = fileName2 + "/dir-" + i3;
            FileHelper.createDirectory(new File(fileName3));
            int i4 = 0;
            while (i4 < 10)
            {
              String fileName4 = fileName3 + "/file-"+i4;
              FileHelper.createFile(new File(fileName4),"Test file "+i0+":"+i1+":"+i2+":"+i3+":"+i4);
              i4++;
            }
            i3++;
          }
          i2++;
        }
        i1++;
      }
      i0++;
    }
    System.err.println("Done generating files");
  }
  
  public void teardownTestArea()
    throws Exception
  {
    System.err.println("Removing generated files");
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
    job.setHopcountMode(job.HOPCOUNT_NEVERDELETE);
      
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

    // Now, start the job, and wait until it completes.
    long startTime = System.currentTimeMillis();
    jobManager.manualStart(job.getID());
    instance.waitJobInactiveNative(jobManager,job.getID(),18000000L);
    System.err.println("Crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");

    // Check to be sure we actually processed the right number of documents.
    JobStatus status = jobManager.getStatus(job.getID());
    // The test data area has 3 documents and one directory, and we have to count the root directory too.
    if (status.getDocumentsProcessed() != 111111)
      throw new ManifoldCFException("Wrong number of documents processed - expected 111111, saw "+new Long(status.getDocumentsProcessed()).toString());

    // Now, start the job AGAIN, and wait until it completes.
    startTime = System.currentTimeMillis();
    jobManager.manualStart(job.getID());
    instance.waitJobInactiveNative(jobManager,job.getID(),18000000L);
    System.err.println("Second crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");

    // Check to be sure we actually processed the right number of documents.
    status = jobManager.getStatus(job.getID());
    // The test data area has 3 documents and one directory, and we have to count the root directory too.
    if (status.getDocumentsProcessed() != 111111)
      throw new ManifoldCFException("Wrong number of documents processed - expected 111111, saw "+new Long(status.getDocumentsProcessed()).toString());

    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),18000000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
