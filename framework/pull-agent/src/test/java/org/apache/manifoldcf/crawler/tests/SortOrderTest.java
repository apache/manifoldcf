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

import java.util.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.jobs.JobManager;
import org.apache.manifoldcf.crawler.repository.RepositoryHistoryManager;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.junit.*;
import static org.junit.Assert.*;

public class SortOrderTest
{
  @Test
  public void testSortOrderValid()
    throws Exception
  {
    SortOrder so = new SortOrder();
    so.addCriteria("identifier", SortOrder.SORT_ASCENDING);
    so.addCriteria("job", SortOrder.SORT_DESCENDING);
    assertEquals(2, so.getCount());
    assertEquals("job", so.getColumn(0));
    assertEquals(SortOrder.SORT_DESCENDING, so.getDirection(0));
    assertEquals("identifier", so.getColumn(1));
    assertEquals(SortOrder.SORT_ASCENDING, so.getDirection(1));

    String rep = so.toString();
    SortOrder deserialized = new SortOrder(rep);
    assertEquals(2, deserialized.getCount());
    assertEquals("job", deserialized.getColumn(0));
    assertEquals(SortOrder.SORT_DESCENDING, deserialized.getDirection(0));
    assertEquals("identifier", deserialized.getColumn(1));
    assertEquals(SortOrder.SORT_ASCENDING, deserialized.getDirection(1));
  }

  @Test
  public void testSortOrderClickColumn()
  {
    SortOrder so = new SortOrder();
    so.clickColumn("starttime");
    assertEquals(1, so.getCount());
    assertEquals("starttime", so.getColumn(0));
    assertEquals(SortOrder.SORT_ASCENDING, so.getDirection(0));

    // Clicking again should toggle direction
    so.clickColumn("starttime");
    assertEquals(1, so.getCount());
    assertEquals("starttime", so.getColumn(0));
    assertEquals(SortOrder.SORT_DESCENDING, so.getDirection(0));

    // Clicking an invalid column should be ignored
    so.clickColumn("invalid; DROP TABLE repojobs;");
    assertEquals(1, so.getCount());
    assertEquals("starttime", so.getColumn(0));
  }

  @Test
  public void testSortOrderInjectionRejection()
  {
    SortOrder so = new SortOrder();
    try
    {
      so.addCriteria("job; DROP TABLE repojobs;", SortOrder.SORT_ASCENDING);
      fail("Expected IllegalArgumentException for SQL injection in column name");
    }
    catch (IllegalArgumentException e)
    {
      // Expected
    }

    try
    {
      so.addCriteria("(CASE WHEN 1=1 THEN id ELSE starttime END)", SortOrder.SORT_ASCENDING);
      fail("Expected IllegalArgumentException for expression in column name");
    }
    catch (IllegalArgumentException e)
    {
      // Expected
    }

    try
    {
      so.addCriteria("starttime DESC--", SortOrder.SORT_ASCENDING);
      fail("Expected IllegalArgumentException for comment syntax in column name");
    }
    catch (IllegalArgumentException e)
    {
      // Expected
    }

    try
    {
      new SortOrder("1:+job; DROP TABLE repojobs..");
      fail("Expected ManifoldCFException for SQL injection in serialized SortOrder");
    }
    catch (ManifoldCFException e)
    {
      // Expected
    }
  }

  @Test
  public void testApiQueueSortColumnValidation()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    IAuthorizer authorizer = new IAuthorizer() {
      @Override
      public boolean checkAllowed(IThreadContext tc, int capability) {
        return true;
      }
    };

    // Valid queue document sort column
    Map<String,List<String>> params = new HashMap<String,List<String>>();
    params.put("report", Collections.singletonList("document"));
    params.put("sortcolumn", Arrays.asList("job", "status"));
    params.put("sortcolumn_direction", Arrays.asList("ascending", "descending"));

    // Invalid queue document sort column
    Map<String,List<String>> badParams = new HashMap<String,List<String>>();
    badParams.put("report", Collections.singletonList("document"));
    badParams.put("sortcolumn", Collections.singletonList("nonexistent_column"));
    badParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionqueue/TestConn", badParams, authorizer);
      fail("Expected ManifoldCFException for invalid sort column in queue document report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn 'nonexistent_column'"));
    }

    // SQL injection payload in queue document sort column
    Map<String,List<String>> sqlInjParams = new HashMap<String,List<String>>();
    sqlInjParams.put("report", Collections.singletonList("document"));
    sqlInjParams.put("sortcolumn", Collections.singletonList("(CASE WHEN (1=1) THEN identifier ELSE scheduled END)"));
    sqlInjParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionqueue/TestConn", sqlInjParams, authorizer);
      fail("Expected ManifoldCFException for SQL injection payload in queue document report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn"));
    }

    // Invalid queue status sort column
    Map<String,List<String>> badStatusParams = new HashMap<String,List<String>>();
    badStatusParams.put("report", Collections.singletonList("status"));
    badStatusParams.put("sortcolumn", Collections.singletonList("job"));
    badStatusParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionqueue/TestConn", badStatusParams, authorizer);
      fail("Expected ManifoldCFException for 'job' sort column in queue status report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn 'job' for report 'status'"));
    }
  }

  @Test
  public void testApiHistorySortColumnValidation()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    IAuthorizer authorizer = new IAuthorizer() {
      @Override
      public boolean checkAllowed(IThreadContext tc, int capability) {
        return true;
      }
    };

    // Invalid history simple sort column
    Map<String,List<String>> badSimpleParams = new HashMap<String,List<String>>();
    badSimpleParams.put("report", Collections.singletonList("simple"));
    badSimpleParams.put("sortcolumn", Collections.singletonList("bad_column"));
    badSimpleParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionhistory/TestConn", badSimpleParams, authorizer);
      fail("Expected ManifoldCFException for invalid sort column in history simple report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn 'bad_column' for report 'simple'"));
    }

    // SQL injection payload in history simple sort column
    Map<String,List<String>> sqlInjParams = new HashMap<String,List<String>>();
    sqlInjParams.put("report", Collections.singletonList("simple"));
    sqlInjParams.put("sortcolumn", Collections.singletonList("starttime; pg_sleep(5);--"));
    sqlInjParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionhistory/TestConn", sqlInjParams, authorizer);
      fail("Expected ManifoldCFException for SQL injection payload in history simple report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn"));
    }

    // Invalid maxactivity sort column
    Map<String,List<String>> badMaxActParams = new HashMap<String,List<String>>();
    badMaxActParams.put("report", Collections.singletonList("maxactivity"));
    badMaxActParams.put("sortcolumn", Collections.singletonList("bytes"));
    badMaxActParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionhistory/TestConn", badMaxActParams, authorizer);
      fail("Expected ManifoldCFException for 'bytes' sort column in maxactivity report");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown sortcolumn 'bytes' for report 'maxactivity'"));
    }

    // Invalid report type
    Map<String,List<String>> badReportParams = new HashMap<String,List<String>>();
    badReportParams.put("report", Collections.singletonList("nonexistent_report"));
    badReportParams.put("sortcolumn", Collections.singletonList("starttime"));
    badReportParams.put("sortcolumn_direction", Collections.singletonList("ascending"));

    try
    {
      ManifoldCF.executeReadCommand(tc, new Configuration(), "repositoryconnectionhistory/TestConn", badReportParams, authorizer);
      fail("Expected ManifoldCFException for unknown report type");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown report type 'nonexistent_report'"));
    }
  }

}
