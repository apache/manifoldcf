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
package org.apache.manifoldcf.crawler.connectors.filesystem.tests;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a test which checks to be sure hopcount functionality is working properly. */
public class HopcountPostgresqlIT extends BaseITPostgresql
{
  protected HopcountTester tester;
  
  public HopcountPostgresqlIT()
  {
    tester = new HopcountTester(mcfInstance);
  }
  
  @Before
  public void setupTester()
    throws Exception
  {
    tester.setupTestArea();
  }
  
  @After
  public void teardownTester()
    throws Exception
  {
    tester.teardownTestArea();
  }
  
  @Test
  public void hopcountCheck()
    throws Exception
  {
    tester.executeTest();
  }
}
