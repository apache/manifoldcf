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
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.apache.manifoldcf.core.tests.HTMLTester;

/** Tests that run the "agents daemon" should be derived from this */
public class ConnectorBaseUIDerby extends BaseITDerby
{
  protected HTMLTester testerInstance = null;

  public ConnectorBaseUIDerby()
  {
    super();
  }
  
  public ConnectorBaseUIDerby(boolean singleWar)
  {
    super(singleWar);
  }

  @Before
  public void setupHTMLTester()
    throws Exception
  {
    testerInstance = new HTMLTester();
    testerInstance.setup();
  }
  
  @After
  public void teardownHTMLTester()
    throws Exception
  {
    if (testerInstance != null)
    {
      testerInstance.teardown();
      testerInstance = null;
    }
  }
}
