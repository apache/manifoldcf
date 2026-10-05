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
import org.apache.manifoldcf.crawler.interfaces.*;
import static org.junit.Assert.*;

/** This is a very basic sanity check */
public class SanityHSQLDBTest extends BaseHSQLDB
{
  
  @Test
  public void sanityCheck()
    throws Exception
  {
    // If we get this far, it must mean that the setup was successful, which is all that I'm shooting for in this test.
  }

  @Test
  public void testJobManagerDocumentStatusSortValidation()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    IJobManager jobManager = JobManagerFactory.make(tc);
    StatusFilterCriteria filterCriteria = new StatusFilterCriteria(new Long[0], System.currentTimeMillis(), null, new int[0], new int[0]);

    // Valid sort columns
    SortOrder validSort = new SortOrder();
    validSort.addCriteria("job", SortOrder.SORT_ASCENDING);
    validSort.addCriteria("status", SortOrder.SORT_DESCENDING);
    IResultSet set = jobManager.genDocumentStatus("testconn", filterCriteria, validSort, 0, 20);
    assertNotNull(set);

    // Invalid sort column (valid identifier syntax, but not allowed for document status)
    SortOrder invalidSort = new SortOrder();
    invalidSort.addCriteria("nonexistent_column", SortOrder.SORT_ASCENDING);
    try
    {
      jobManager.genDocumentStatus("testconn", filterCriteria, invalidSort, 0, 20);
      fail("Expected ManifoldCFException for invalid sort column in genDocumentStatus");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown or invalid sort column: 'nonexistent_column'"));
    }
  }

  @Test
  public void testHistoryManagerSortValidation()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
    FilterCriteria criteria = new FilterCriteria(new String[0], null, null, null, null);

    // Valid sort columns (including entityid alias)
    SortOrder validSort = new SortOrder();
    validSort.addCriteria("activity", SortOrder.SORT_ASCENDING);
    validSort.addCriteria("entityid", SortOrder.SORT_DESCENDING);
    IResultSet set = connManager.genHistorySimple("testconn", criteria, validSort, 0, 20);
    assertNotNull(set);

    // Invalid sort column
    SortOrder invalidSort = new SortOrder();
    invalidSort.addCriteria("unknown_field", SortOrder.SORT_ASCENDING);
    try
    {
      connManager.genHistorySimple("testconn", criteria, invalidSort, 0, 20);
      fail("Expected ManifoldCFException for invalid sort column in genHistorySimple");
    }
    catch (ManifoldCFException e)
    {
      assertTrue(e.getMessage().contains("Unknown or invalid sort column: 'unknown_field'"));
    }
  }

}
