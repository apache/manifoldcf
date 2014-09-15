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

import java.io.*;
import java.util.*;

import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector;
import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConfig;

/** Run a session-based crawl */
public class SessionTester
{
  protected org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance;
  
  public SessionTester(org.apache.manifoldcf.crawler.tests.ManifoldCFInstance instance)
  {
    this.instance = instance;
  }
  
  public void executeTest()
    throws Exception
  {
    // Hey, we were able to install the web connector etc.
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
    
    // Set session auth settings
    ConfigurationNode accessCredential = new ConfigurationNode(WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
    accessCredential.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_SESSION);
    accessCredential.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/web/");
    
    // Add auth pages to accessCredential node
    
    // Redirection to login page
    ConfigurationNode redirectToLogin = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPAGE);
    redirectToLogin.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/protectedcontent\\.html\\?");
    redirectToLogin.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_REDIRECTION);
    redirectToLogin.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,"/loginpage\\.html\\?");
    accessCredential.addChild(accessCredential.getChildCount(),redirectToLogin);
    
    // Redirection to login page from index
    ConfigurationNode redirectFromIndex = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPAGE);
    redirectFromIndex.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/index\\.html$");
    redirectFromIndex.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_REDIRECTION);
    redirectFromIndex.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,"/loginpage\\.html$");
    accessCredential.addChild(accessCredential.getChildCount(),redirectFromIndex);

    // Login page
    ConfigurationNode loginPage = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPAGE);
    loginPage.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/loginpage\\.html(\\?|$)");
    loginPage.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_FORM);
    loginPage.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,"");
    // Set credentials
    ConfigurationNode userParameter = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPARAMETER);
    userParameter.setAttribute(WebcrawlerConfig.ATTR_NAMEREGEXP,"user");
    userParameter.setAttribute(WebcrawlerConfig.ATTR_VALUE,"foo");
    loginPage.addChild(loginPage.getChildCount(),userParameter);
    ConfigurationNode passwordParameter = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPARAMETER);
    passwordParameter.setAttribute(WebcrawlerConfig.ATTR_NAMEREGEXP,"password");
    passwordParameter.setAttribute(WebcrawlerConfig.ATTR_VALUE,"bar");
    loginPage.addChild(loginPage.getChildCount(),passwordParameter);
    accessCredential.addChild(accessCredential.getChildCount(),loginPage);

    // Redirection from login page to content
    ConfigurationNode redirectFromLogin = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPAGE);
    redirectFromLogin.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/loginpage\\.html\\?");
    redirectFromLogin.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_REDIRECTION);
    redirectFromLogin.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,"/protectedcontent\\.html\\?");
    accessCredential.addChild(accessCredential.getChildCount(),redirectFromLogin);

    // Redirection from login page to index
    ConfigurationNode redirectToIndexFromLogin = new ConfigurationNode(WebcrawlerConfig.NODE_AUTHPAGE);
    redirectToIndexFromLogin.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,"/loginpage\\.html$");
    redirectToIndexFromLogin.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_REDIRECTION);
    redirectToIndexFromLogin.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,"/index\\.html$");
    accessCredential.addChild(accessCredential.getChildCount(),redirectToIndexFromLogin);

    cp.addChild(cp.getChildCount(),accessCredential);
    
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
    
    // Set up the seed
    SpecificationNode sn = new SpecificationNode(WebcrawlerConfig.NODE_SEEDS);
    sn.setValue("http://localhost:8191/web/index.html\n");
    ds.addChild(ds.getChildCount(),sn);
    
    sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDES);
    sn.setValue(".*\n");
    ds.addChild(ds.getChildCount(),sn);
    
    sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDESINDEX);
    sn.setValue(".*\n");
    ds.addChild(ds.getChildCount(),sn);

    // Save the job.
    jobManager.save(job);

    // Now, start the job, and wait until it completes.
    long startTime = System.currentTimeMillis();
    jobManager.manualStart(job.getID());
    instance.waitJobInactiveNative(jobManager,job.getID(),600000L);
    System.err.println("Crawl required "+new Long(System.currentTimeMillis()-startTime).toString()+" milliseconds");

    // Check to be sure we actually processed the right number of documents.
    JobStatus status = jobManager.getStatus(job.getID());
    if (status.getDocumentsProcessed() != 101)
    {
      throw new ManifoldCFException("Wrong number of documents processed - expected 101, saw "+new Long(status.getDocumentsProcessed()).toString());
    }
    
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),600000L);
      
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  
}
