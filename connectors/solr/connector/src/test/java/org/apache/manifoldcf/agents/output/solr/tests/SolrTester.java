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
package org.apache.manifoldcf.agents.output.solr.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;

import org.apache.manifoldcf.agents.output.solr.SolrConfig;

/** This is a 100 document crawl */
public class SolrTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public SolrTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
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
    conn.setName("Test Connection");
    conn.setDescription("Test Connection");
    conn.setClassName("org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector");
    conn.setMaxConnections(100);
    // Now, save
    mgr.save(conn);
      
    // Create a basic null output connection, and save it.
    IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);
    IOutputConnection outputConn = outputMgr.create();
    outputConn.setName("Solr Connection");
    outputConn.setDescription("Solr Connection");
    outputConn.setClassName("org.apache.manifoldcf.agents.output.solr.SolrConnector");
    outputConn.setMaxConnections(10);
    // Set the connection parameters
    ConfigParams configParams = outputConn.getConfigParams();
    configParams.setParameter(SolrConfig.PARAM_PROTOCOL,SolrConfig.PROTOCOL_TYPE_HTTP);
    configParams.setParameter(SolrConfig.PARAM_SERVER,"localhost");
    configParams.setParameter(SolrConfig.PARAM_PORT,"8188");
    configParams.setParameter(SolrConfig.PARAM_WEBAPPNAME,"solr");
    configParams.setParameter(SolrConfig.PARAM_UPDATEPATH,"/update/extract");
    configParams.setParameter(SolrConfig.PARAM_REMOVEPATH,"/update");
    configParams.setParameter(SolrConfig.PARAM_STATUSPATH,"/admin/ping");
    configParams.setParameter(SolrConfig.PARAM_IDFIELD,"id");
    // Now, save
    outputMgr.save(outputConn);

    // Create a job.
    IJobManager jobManager = JobManagerFactory.make(tc);
    IJobDescription job = jobManager.createJob();
    job.setDescription("Test Job");
    job.setConnectionName("Test Connection");
    job.addPipelineStage(-1,true,"Solr Connection","");
    job.setType(job.TYPE_SPECIFIED);
    job.setStartMethod(job.START_DISABLE);
    job.setHopcountMode(job.HOPCOUNT_NEVERDELETE);
      
    // Now, set up the document specification.
    Specification ds = job.getSpecification();
    SpecificationNode sn = new SpecificationNode("documentcount");
    sn.setAttribute("count","111");
    ds.addChild(ds.getChildCount(),sn);
      
    // Save the job.
    jobManager.save(job);

    ManifoldCFException exception = null;
    
    if (exception == null)
    {
      try
      {
        // Now, start the job, and wait until it completes.
        long startTime = System.currentTimeMillis();
        jobManager.manualStart(job.getID());
        instance.waitJobInactiveNative(jobManager,job.getID(),300000L);
        System.err.println("Crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");
        
        // Force reindexing, and immediately retry.  This is the case
        // that always produces IOExceptions.
        org.apache.manifoldcf.agents.system.ManifoldCF.signalOutputConnectionRedo(tc,"Solr Connection");
        startTime = System.currentTimeMillis();
        jobManager.manualStart(job.getID());
        instance.waitJobInactiveNative(jobManager,job.getID(),300000L);
        System.err.println("Second crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");
      }
      catch (ManifoldCFException e)
      {
        exception = e;
      }
    }

    if (exception == null)
    {
      // Check to be sure we actually processed the right number of documents.
      JobStatus status = jobManager.getStatus(job.getID());
      if (status.getDocumentsProcessed() != 111)
        exception = new ManifoldCFException("Wrong number of documents processed - expected 111, saw "+new Long(status.getDocumentsProcessed()).toString());
    }
    
    if (exception == null)
    {
      // Look in the connection history for anything other than an OK
      FilterCriteria fc = new FilterCriteria(new String[]{"document ingest (Solr Connection)"},null,null,null,null);
      SortOrder sc = new SortOrder();
      IResultSet result = mgr.genHistorySimple("File Connection",fc,sc,0,10000);
      for (int i = 0; i < result.getRowCount(); i++)
      {
        IResultRow row = result.getRow(i);
        String activity = (String)row.getValue("activity");
        String resultCode = (String)row.getValue("resultcode");
        String resultDetails = (String)row.getValue("resultdesc");
        if (activity.startsWith("document ingest") && !resultCode.equals("OK"))
          exception = new ManifoldCFException("An indexing operation ("+activity+") failed with result code "+resultCode+" details "+((resultDetails==null)?"none":resultDetails));
        if (exception != null)
          break;
      }
    }
    
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),300000L);
    
    if (exception != null)
      throw exception;
    
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
