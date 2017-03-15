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
package org.apache.manifoldcf.crawler.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;

/** This is a test whether we can handle service interruptions */
public class InterruptionTester
{
  protected final ManifoldCFInstance instance;
  
  public InterruptionTester(ManifoldCFInstance instance)
  {
    this.instance = instance;
  }
  
  public void executeTest()
    throws Exception
  {
    instance.start();
    
    // Hey, we were able to install the file system connector etc.
    // Now, create a local test job and run it.
    IThreadContext tc = ThreadContextFactory.make();
      
    // Create a basic file system connection, and save it.
    IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(tc);
    IRepositoryConnection conn = mgr.create();
    conn.setName("InterruptionTest Connection");
    conn.setDescription("InterruptionTest Connection");
    conn.setClassName("org.apache.manifoldcf.crawler.tests.InterruptionRepositoryConnector");
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
    job.setConnectionName("InterruptionTest Connection");
    job.addPipelineStage(-1,true,"Null Connection","");
    //job.setOutputConnectionName("Null Connection");
    job.setType(job.TYPE_SPECIFIED);
    job.setStartMethod(job.START_DISABLE);
    job.setHopcountMode(job.HOPCOUNT_ACCURATE);
      
    // Save the job.
    jobManager.save(job);

    // Now, start the job, and wait until it is running.
    jobManager.manualStart(job.getID());
    instance.waitJobRunningNative(jobManager,job.getID(),30000L);
    
    // Wait for the job to become inactive.  The time should not exceed 10 seconds for the actual crawl.
    instance.waitJobInactiveNative(jobManager,job.getID(),60000L);
    // The document will be skipped in the end.
    if (jobManager.getStatus(job.getID()).getDocumentsProcessed() != 9)
      throw new Exception("Expected 9 documents, saw "+jobManager.getStatus(job.getID()).getDocumentsProcessed());
    
    // Now, delete the job.
    jobManager.deleteJob(job.getID());
    instance.waitJobDeletedNative(jobManager,job.getID(),60000L);

    // Shut down instance2
    instance.stop();
  }
}
