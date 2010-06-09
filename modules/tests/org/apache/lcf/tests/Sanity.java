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
package org.apache.lcf.tests;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.LCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a very basic sanity check */
public class Sanity extends TestBase
{
  
  
  @Test
  public void sanityCheck()
    throws Exception
  {
    // Hey, we were able to install the file system connector etc.
    // Now, create a local test job and run it.
    IThreadContext tc = ThreadContextFactory.make();
    
    // Create a basic file system connection, and save it.
    IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(tc);
    IRepositoryConnection conn = mgr.create();
    conn.setName("File Connection");
    conn.setDescription("File Connection");
    conn.setClassName("org.apache.lcf.crawler.connectors.filesystem.FileConnector");
    conn.setMaxConnections(100);
    // Now, save
    mgr.save(conn);
    
    // Create a basic null output connection, and save it.
    IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);
    IOutputConnection outputConn = outputMgr.create();
    outputConn.setName("Null Connection");
    outputConn.setDescription("Null Connection");
    outputConn.setClassName("org.apache.lcf.agents.output.nullconnector.NullConnector");
    outputConn.setMaxConnections(100);
    // Now, save
    outputMgr.save(outputConn);

    // MHL
    
    // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
  }
  

}
