/* $Id: TestBase.java 959660 2010-07-01 13:46:10Z kwright $ */

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
package org.apache.acf.crawler.tests;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.ACF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the agents framework. */
public class TestBase extends org.apache.acf.agents.tests.TestBase
{
  
  @Before
  public void setUp()
    throws Exception
  {
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      System.out.println("Warning: Preclean failed: "+e.getMessage());
    }
    try
    {
      localSetUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }

  protected void localSetUp()
    throws Exception
  {
    super.localSetUp();
    
    // Install the agents tables
    initialize();
    ACF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IAgentManager mgr = AgentManagerFactory.make(tc);
    mgr.registerAgent("org.apache.acf.crawler.system.CrawlerAgent");
  }
  
  @After
  public void cleanUp()
    throws Exception
  {
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }

  protected void localCleanUp()
    throws Exception
  {
    initialize();
    if (isInitialized())
    {
      // Test the uninstall
      ACF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      
      Exception currentException = null;
      try
      {
        IAgentManager mgr = AgentManagerFactory.make(tc);
        mgr.unregisterAgent("org.apache.acf.crawler.system.CrawlerAgent");
      }
      catch (Exception e)
      {
        if (currentException != null)
          currentException = e;
      }
      try
      {
        super.localCleanUp();
      }
      catch (Exception e)
      {
        if (currentException != null)
          currentException = e;
      }
      if (currentException != null)
        throw currentException;
    }
  }

}
