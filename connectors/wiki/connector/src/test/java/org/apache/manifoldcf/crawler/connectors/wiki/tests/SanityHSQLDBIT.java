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
package org.apache.manifoldcf.crawler.connectors.wiki.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import org.apache.manifoldcf.crawler.connectors.wiki.WikiConfig;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a very basic sanity check */
public class SanityHSQLDBIT extends BaseITHSQLDB
{
  protected static Map<String,String> initialCheckResources;
  protected static Map<String,String> initialListResources;
  protected static Map<String,String> initialTimestampQueryResources;
  protected static Map<String,String> initialURLQueryResources;
  protected static Map<String,String> initialDocInfoQueryResources;
  protected static final String namespaceResource = "get_namespaces.xml";
  static
  {
    initialCheckResources = new HashMap<String,String>();
    initialCheckResources.put("","list_one.xml");
    
    initialListResources = new HashMap<String,String>();
    initialListResources.put("","list_full.xml");
    initialListResources.put("Kre Mbaye","list_full_last.xml");
    
    initialTimestampQueryResources = new HashMap<String,String>();
    addCombinations(initialTimestampQueryResources,new String[]{"14773725","19219017","19319577","19839654","30955295"},"get_timestamps.xml");
    // Use some individual overrides too
    // MHL
    
    initialURLQueryResources = new HashMap<String,String>();
    addCombinations(initialURLQueryResources,new String[]{"14773725","19219017","19319577","19839654","30955295"},"get_urls.xml");
    // Use some individual overrides too
    // MHL
    
    initialDocInfoQueryResources = new HashMap<String,String>();
    initialDocInfoQueryResources.put("14773725","14773725.xml");
    initialDocInfoQueryResources.put("19219017","19219017.xml");
    initialDocInfoQueryResources.put("19319577","19319577.xml");
    initialDocInfoQueryResources.put("19839654","19839654.xml");
    initialDocInfoQueryResources.put("30955295","30955295.xml");
    
  }

  protected static void addCombinations(Map<String,String> target, String[] values, String resource)
  {
    boolean[] vector = new boolean[values.length];
    for (int i = 0 ; i < vector.length ; i++)
    {
      vector[i] = false;
    }
    
    // Iterate through all the combinations.  Only take the ones that can exist.
    while (true)
    {
      int result = 0;
      for (int i = 0 ; i < vector.length ; i++)
      {
        result += vector[i]?1:0;
      }
      if (result != 0 && result <= 20)
      {
        // found one
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (int i = 0 ; i < vector.length ; i++)
        {
          if (vector[i])
          {
            if (!isFirst)
              sb.append("|");
            else
              isFirst = false;
            sb.append(values[i]);
          }
        }
        target.put(sb.toString(),resource);
      }
      // Now we increment.
      int incLevel = 0;
      while (incLevel < vector.length)
      {
        if (!vector[incLevel])
        {
          vector[incLevel] = true;
          break;
        }
        // Had a carry
        incLevel++;
        for (int i = 0 ; i < incLevel ; i++)
        {
          vector[i] = false;
        }
      }
      if (incLevel == vector.length)
        break;
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
      
      // Create a basic file system connection, and save it.
      IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(tc);
      IRepositoryConnection conn = mgr.create();
      conn.setName("Wiki Connection");
      conn.setDescription("Wiki Connection");
      conn.setClassName("org.apache.manifoldcf.crawler.connectors.wiki.WikiConnector");
      conn.setMaxConnections(10);
      ConfigParams cp = conn.getConfigParams();
      cp.setParameter(WikiConfig.PARAM_PROTOCOL,"http");
      cp.setParameter(WikiConfig.PARAM_SERVER,"localhost");
      cp.setParameter(WikiConfig.PARAM_PORT,"8089");
      cp.setParameter(WikiConfig.PARAM_PATH,"/w");
      
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
      job.setConnectionName("Wiki Connection");
      job.addPipelineStage(-1,true,"Null Connection","");
      job.setType(job.TYPE_SPECIFIED);
      job.setStartMethod(job.START_DISABLE);
      job.setHopcountMode(job.HOPCOUNT_ACCURATE);
      
      // Now, set up the document specification.
      // Right now we don't need any...
      Specification ds = job.getSpecification();
      
      // Save the job.
      jobManager.save(job);

      // Initialize the mock service
      wikiService.setResources(initialCheckResources,
        initialListResources,
        initialTimestampQueryResources,
        initialURLQueryResources,
        initialDocInfoQueryResources,
	namespaceResource);
        
      // Now, start the job, and wait until it completes.
      jobManager.manualStart(job.getID());
      waitJobInactiveNative(jobManager,job.getID(),120000L);

      // Check to be sure we actually processed the right number of documents.
      JobStatus status = jobManager.getStatus(job.getID());
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      if (status.getDocumentsProcessed() != 5)
        throw new ManifoldCFException("Wrong number of documents processed - expected 5, saw "+new Long(status.getDocumentsProcessed()).toString());
      
      /*
      // Add a file and recrawl
      createFile(new File("testdata/testdir/test4.txt"),"Added file");

      // Now, start the job, and wait until it completes.
      jobManager.manualStart(job.getID());
      waitJobInactive(jobManager,job.getID(),120000L);

      status = jobManager.getStatus(job.getID());
      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      if (status.getDocumentsProcessed() != 6)
        throw new ManifoldCFException("Wrong number of documents processed after add - expected 6, saw "+new Long(status.getDocumentsProcessed()).toString());

      // Change a file, and recrawl
      changeFile(new File("testdata/test1.txt"),"Modified contents");
      
      // Now, start the job, and wait until it completes.
      jobManager.manualStart(job.getID());
      waitJobInactive(jobManager,job.getID(),120000L);

      status = jobManager.getStatus(job.getID());
      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      if (status.getDocumentsProcessed() != 6)
        throw new ManifoldCFException("Wrong number of documents processed after change - expected 6, saw "+new Long(status.getDocumentsProcessed()).toString());
      // We also need to make sure the new document was indexed.  Have to think about how to do this though.
      // MHL
      
      // Delete a file, and recrawl
      removeFile(new File("testdata/test2.txt"));
      
      // Now, start the job, and wait until it completes.
      jobManager.manualStart(job.getID());
      waitJobInactive(jobManager,job.getID(),120000L);

      // Check to be sure we actually processed the right number of documents.
      status = jobManager.getStatus(job.getID());
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      if (status.getDocumentsProcessed() != 5)
        throw new ManifoldCFException("Wrong number of documents processed after delete - expected 5, saw "+new Long(status.getDocumentsProcessed()).toString());
      */
      
      // Now, delete the job.
      jobManager.deleteJob(job.getID());
      waitJobDeletedNative(jobManager,job.getID(),120000L);
      
      // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
}
