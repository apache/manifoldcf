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

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a very basic sanity check */
public class SolrCrawlHSQLDBIT extends BaseITHSQLDB
{

  protected SolrTester tester;
  protected MockSolrService solrService = null;
  
  public SolrCrawlHSQLDBIT()
  {
    tester = new SolrTester(mcfInstance);
  }
  
  // Setup and teardown the mock wiki service
  
  @Before
  public void createSolrService()
    throws Exception
  {
    System.out.println("Creating mock service");
    solrService = new MockSolrService();
    solrService.start();
    System.out.println("Mock service created");
  }
  
  @After
  public void shutdownSolrService()
    throws Exception
  {
    if (solrService != null)
      solrService.stop();
  }

  @Test
  public void simpleCrawl()
    throws Exception
  {
    tester.executeTest();
  }
}
