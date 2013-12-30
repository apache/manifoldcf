/* $Id: TestBase.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the agents framework. */
public class BasePostgresql extends org.apache.manifoldcf.core.tests.BasePostgresql
{
  
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

  protected void localSetUp()
    throws Exception
  {
    super.localSetUp();
    
    IThreadContext tc = ThreadContextFactory.make();
    ManifoldCF.installTables(tc);
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
    cleanupSystem();
  }

  protected void localCleanUp()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
      
    Exception currentException = null;
    try
    {
      ManifoldCF.deinstallTables(tc);
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
  
  protected void initializeSystem()
    throws Exception
  {
    super.initializeSystem();
    org.apache.manifoldcf.agents.system.ManifoldCF.localInitialize(ThreadContextFactory.make());
  }
  
  protected void cleanupSystem()
    throws Exception
  {
    org.apache.manifoldcf.agents.system.ManifoldCF.localCleanup(ThreadContextFactory.make());
    super.cleanupSystem();
  }

}
