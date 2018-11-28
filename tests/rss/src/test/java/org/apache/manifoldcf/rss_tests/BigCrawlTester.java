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
package org.apache.manifoldcf.rss_tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import org.apache.manifoldcf.crawler.connectors.rss.RSSConfig;

import java.io.*;
import java.util.*;

/** This is a 100000-document crawl */
public class BigCrawlTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public BigCrawlTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
  {
    this.instance = instance;
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
    conn.setName("RSS Connection");
    conn.setDescription("RSS Connection");
    conn.setClassName("org.apache.manifoldcf.crawler.connectors.rss.RSSConnector");
    conn.setMaxConnections(100);
    ConfigParams cp = conn.getConfigParams();
    cp.setParameter(RSSConfig.PARAMETER_EMAIL,"somebody@somewhere.com");
    cp.setParameter(RSSConfig.PARAMETER_MAXOPEN,"100");
    cp.setParameter(RSSConfig.PARAMETER_MAXFETCHES,"1000000");
    cp.setParameter(RSSConfig.PARAMETER_BANDWIDTH,"1000000");
    cp.setParameter(RSSConfig.PARAMETER_ROBOTSUSAGE,"none");
    // Now, save
    mgr.save(conn);
      
    // Create a basic null output connection, and save it.
    IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);
    IOutputConnection outputConn = outputMgr.create();
    outputConn.setName("Null Connection");
    outputConn.setDescription("Null Connection");
    outputConn.setClassName("org.apache.manifoldcf.agents.output.nullconnector.NullConnector");
    outputConn.setMaxConnections(100);
    // Now, save
    outputMgr.save(outputConn);

    // Create a job.
    IJobManager jobManager = JobManagerFactory.make(tc);
    IJobDescription job = jobManager.createJob();
    job.setDescription("Test Job");
    job.setConnectionName("RSS Connection");
    job.setOutputConnectionName("Null Connection");
    job.setType(job.TYPE_SPECIFIED);
    job.setStartMethod(job.START_DISABLE);
    job.setHopcountMode(job.HOPCOUNT_NEVERDELETE);
      
    // Now, set up the document specification.
    DocumentSpecification ds = job.getSpecification();
    // For 100000 documents, set up 10000 seeds
    for (int i = 0 ; i < 10000 ; i++)
    {
      SpecificationNode sn = new SpecificationNode("feed");
      sn.setAttribute("url","http://localhost:8189/rss/gen.php?type=feed&feed="+i);
      ds.addChild(ds.getChildCount(),sn);
    }
      
    // Set up the output specification.
    OutputSpecification os = job.getOutputSpecification();
    // Null output connections have no output specification, so this is a no-op.
    
    // Save the job.
    jobManager.save(job);

    // Now, start the job, and wait until it completes.
    long startTime = System.currentTimeMillis();
    jobManager.manualStart(job.getID());
    instance.waitJobInactiveNative(jobManager,job.getID(),22000000L);
    System.err.println("Crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");

    // Check to be sure we actually processed the right number of documents.
    JobStatus status = jobManager.getStatus(job.getID());
    // The test data area has 3 documents and one directory, and we have to count the root directory too.
    if (status.getDocumentsProcessed() != 110000)
      throw new ManifoldCFException("Wrong number of documents processed - expected 110000, saw "+new Long(status.getDocumentsProcessed()).toString());
      
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),18000000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
