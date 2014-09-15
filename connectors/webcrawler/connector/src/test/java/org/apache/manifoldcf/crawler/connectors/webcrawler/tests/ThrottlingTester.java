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
package org.apache.manifoldcf.crawler.connectors.webcrawler.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector;
import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConfig;

import java.io.*;
import java.util.*;

/** This is a repeated 100-document crawl with throttling */
public class ThrottlingTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public ThrottlingTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
  {
    this.instance = instance;
  }
  
  public void executeTest()
    throws Exception
  {
    // Hey, we were able to install the connector etc.
    // Now, create a local test job and run it.
    IThreadContext tc = ThreadContextFactory.make();
      
    // Create a basic file system connection, and save it.
    IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(tc);
    IRepositoryConnection conn = mgr.create();
    conn.setName("Web Connection");
    conn.setDescription("Web Connection");
    conn.setClassName("org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector");
    conn.setMaxConnections(100);
    ConfigParams cp = conn.getConfigParams();
    
    cp.setParameter(WebcrawlerConfig.PARAMETER_EMAIL,"someone@somewhere.com");
    cp.setParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE,"none");
    
    // Throttling
    ConfigurationNode cn = new ConfigurationNode(WebcrawlerConfig.NODE_BINDESC);
    cn.setAttribute(WebcrawlerConfig.ATTR_BINREGEXP,"");
    
    ConfigurationNode con = new ConfigurationNode(WebcrawlerConfig.NODE_MAXCONNECTIONS);
    con.setAttribute(WebcrawlerConfig.ATTR_VALUE,"10");
    cn.addChild(cn.getChildCount(),con);
    
    ConfigurationNode maxKB = new ConfigurationNode(WebcrawlerConfig.NODE_MAXKBPERSECOND);
    maxKB.setAttribute(WebcrawlerConfig.ATTR_VALUE,"128");
    cn.addChild(cn.getChildCount(),maxKB);
    
    ConfigurationNode maxFetches = new ConfigurationNode(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE);
    maxFetches.setAttribute(WebcrawlerConfig.ATTR_VALUE,"120");
    cn.addChild(cn.getChildCount(),maxFetches);
    
    cp.addChild(cp.getChildCount(),cn);
    
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
    job.setConnectionName("Web Connection");
    job.addPipelineStage(-1,true,"Null Connection","");
    job.setType(job.TYPE_SPECIFIED);
    job.setStartMethod(job.START_DISABLE);
    job.setHopcountMode(job.HOPCOUNT_NEVERDELETE);

    // Now, set up the document specification.
    Specification ds = job.getSpecification();
    
    // Set up 100 seeds
    SpecificationNode sn = new SpecificationNode(WebcrawlerConfig.NODE_SEEDS);
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < 50 ; i++)
    {
      sb.append("http://localhost:8191/web/gen.php?site="+i+"&level=0&item=0\n");
    }
    sn.setValue(sb.toString());
    ds.addChild(ds.getChildCount(),sn);
    
    sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDES);
    sn.setValue(".*\n");
    ds.addChild(ds.getChildCount(),sn);
    
    sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDESINDEX);
    sn.setValue(".*\n");
    ds.addChild(ds.getChildCount(),sn);

    // Save the job.
    jobManager.save(job);

    for (int i = 0; i < 100; i++)
    {
      System.err.println("Iteration # "+i);
      // Now, start the job, and wait until it completes.
      long startTime = System.currentTimeMillis();
      jobManager.manualStart(job.getID());
      try
      {
        instance.waitJobInactiveNative(jobManager,job.getID(),900000L);
      }
      catch (ManifoldCFException e)
      {
        System.err.println("Halting for inspection");
        Thread.sleep(9000000L);
        throw e;
      }
      System.err.println(" Crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");

      // Check to be sure we actually processed the right number of documents.
      JobStatus status = jobManager.getStatus(job.getID());
      System.err.println(" "+new Long(status.getDocumentsProcessed())+" documents processed");
    }
    
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),900000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
