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
package org.apache.manifoldcf.crawler.connectors.webcrawler;

import org.apache.manifoldcf.crawler.connectors.webcrawler.RobotsManager;
import org.junit.*;
import static org.junit.Assert.*;

public class RobotsTest
{

  @Test
  public void doesPathMatch()
    throws Exception
  {
    // This test assesses the functionality of doesPathMatch()
    assertTrue(RobotsManager.doesPathMatch("/folder/doc1.pdf","/folder/doc1.pdf"));
    assertTrue(RobotsManager.doesPathMatch("/folder/doc1.pdf","/folder/*"));
    assertTrue(RobotsManager.doesPathMatch("/folder/doc1.pdf","/"));
    assertTrue(RobotsManager.doesPathMatch("/folder/doc1.pdf","/folder/"));
    assertFalse(RobotsManager.doesPathMatch("/folder/doc1.pdf","folder/doc1.pdf"));
  }
  
  @Test
  public void testRecord()
    throws Exception
  {
    // Assess whether the Record class is doing the right thing
    RobotsManager.Record record = new RobotsManager.Record();
    record.addAgent("*");
    record.addDisallow("/");
    record.addAllow("folder/doc1.pdf");
    record.addAllow("folder/doc2.pdf");
    record.addAllow("folder/doc3.pdf");
    assertTrue(record.isAgentMatch("*",true));
    assertTrue(record.isDisallowed("/folder/doc1.pdf"));
    assertFalse(record.isAllowed("/folder/doc1.pdf"));
  }
  
}
