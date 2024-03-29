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
package org.apache.manifoldcf.core.lockmanager;

import java.io.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class ZooKeeperBase
{
  static protected File tempDir;
  static protected ZooKeeperInstance instance;
  
  @BeforeClass
  public static void startZooKeeper()
    throws Exception
  {
    tempDir = new File("zookeeper");
    tempDir.mkdir();
    instance = new ZooKeeperInstance(8348,tempDir);
    instance.start();
  }
  
  @AfterClass
  public static void stopZookeeper()
    throws Exception
  {
    instance.stop();
    deleteRecursively(tempDir);
  }
  
  protected static void deleteRecursively(File tempDir)
    throws Exception
  {
    if (tempDir.isDirectory())
    {
      File[] files = tempDir.listFiles();
      for (File f : files)
      {
        deleteRecursively(f);
      }
    }
    tempDir.delete();
  }
  

}
