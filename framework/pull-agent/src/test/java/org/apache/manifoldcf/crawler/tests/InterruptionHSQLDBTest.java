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
import org.apache.manifoldcf.agents.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a test of service interruptions */
public class InterruptionHSQLDBTest extends ConnectorBaseHSQLDB
{
  protected final ManifoldCFInstance mcfInstance;
  protected InterruptionTester tester;

  public InterruptionHSQLDBTest()
  {
    super();
    mcfInstance = new ManifoldCFInstance("A",false,false);
    tester = new InterruptionTester(mcfInstance);
  }
  
  @Override
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.tests.InterruptionRepositoryConnector"};
  }
  
  @Override
  protected String[] getConnectorNames()
  {
    return new String[]{"InterruptionConnector"};
  }

  @Override
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.tests.TestingOutputConnector"};
  }
  
  @Override
  protected String[] getOutputNames()
  {
    return new String[]{"NullOutput"};
  }

  @Test
  public void interruptionTestRun()
    throws Exception
  {
    tester.executeTest();
  }
  
  @Before
  public void setUp()
    throws Exception
  {
    initializeSystem();
    try
    {
      localReset();
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
  
  @After
  public void cleanUp()
    throws Exception
  {
    Exception currentException = null;
    // Last, shut down the web applications.
    // If this is done too soon it closes the database before the rest of the cleanup happens.
    try
    {
      mcfInstance.unload();
    }
    catch (Exception e)
    {
      if (currentException == null)
        currentException = e;
    }
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
    if (currentException != null)
      throw currentException;
    cleanupSystem();
  }
  

}
