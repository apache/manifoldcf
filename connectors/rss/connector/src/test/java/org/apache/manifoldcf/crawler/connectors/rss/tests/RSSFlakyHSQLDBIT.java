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
package org.apache.manifoldcf.crawler.connectors.rss.tests;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a very basic sanity check */
public class RSSFlakyHSQLDBIT extends BaseITHSQLDB
{
  protected RSSSimpleCrawlTester tester;
  protected MockRSSService rssService = null;
  
  public RSSFlakyHSQLDBIT()
  {
    tester = new RSSSimpleCrawlTester(mcfInstance);
  }
  
  // Setup and teardown the mock wiki service
  
  @Before
  public void createRSSService()
    throws Exception
  {
    rssService = new MockRSSService(10);
    rssService.start();
  }
  
  @After
  public void shutdownRSSService()
    throws Exception
  {
    if (rssService != null)
      rssService.stop();
  }

  @Test
  public void simpleCrawl()
    throws Exception
  {
    tester.executeTest(new DBInterruptionNotification());
  }
  
  /** Method to get database implementation class */
  @Override
  protected String getDatabaseImplementationClass()
    throws Exception
  {
    return FlakyHSQLDBInstance.class.getName();
  }

  protected static class DBInterruptionNotification implements RSSSimpleCrawlTester.TestNotification
  {
    public void notifyMe()
      throws Exception
    {
      // Wait 5 seconds, then turn of database access for 10 seconds.  Then, do it again.
      Thread.sleep(5000L);
      FlakyHSQLDBInstance.setConnectionWorking(false);
      System.out.println("Database connectivity is OFF");
      Thread.sleep(10000L);
      FlakyHSQLDBInstance.setConnectionWorking(true);
      System.out.println("Database connectivity restored");
      Thread.sleep(5000L);
      FlakyHSQLDBInstance.setConnectionWorking(false);
      System.out.println("Database connectivity is OFF");
      Thread.sleep(10000L);
      FlakyHSQLDBInstance.setConnectionWorking(true);
      System.out.println("Database connectivity restored");
    }
  }
}
