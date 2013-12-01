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

/** This is a test of the scheduler.  If the test succeeds, it is because
* the scheduler has properly distributed requests for all bins evenly. */
public class SchedulerHSQLDBTest extends BaseITHSQLDB
{
  
  protected SchedulerTester tester;

  public SchedulerHSQLDBTest()
  {
    super(false,false);
    tester = new SchedulerTester(mcfInstance);
  }
  
  @Override
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.tests.SchedulingRepositoryConnector"};
  }
  
  @Override
  protected String[] getConnectorNames()
  {
    return new String[]{"SchedulingConnector"};
  }

  @Override
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.tests.NullOutputConnector"};
  }
  
  @Override
  protected String[] getOutputNames()
  {
    return new String[]{"NullOutput"};
  }

  @Test
  public void schedulingTestRun()
    throws Exception
  {
    tester.executeTest();
  }
  

}
