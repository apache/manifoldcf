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

import java.io.*;
import java.util.*;
import org.junit.*;

/** Web connector session login test */
public class SessionLoginHSQLDBIT extends BaseITHSQLDB
{

  protected SessionTester tester;
  protected MockSessionWebService webService = null;
  
  public SessionLoginHSQLDBIT()
  {
    tester = new SessionTester(mcfInstance);
  }
  
  // Setup and teardown the mock wiki service
  
  @Before
  public void createWebService()
    throws Exception
  {
    webService = new MockSessionWebService(100,"foo","bar");
    webService.start();
  }
  
  @After
  public void shutdownWebService()
    throws Exception
  {
    if (webService != null)
      webService.stop();
  }

  @Test
  public void sessionCrawl()
    throws Exception
  {
    tester.executeTest();
  }
}
