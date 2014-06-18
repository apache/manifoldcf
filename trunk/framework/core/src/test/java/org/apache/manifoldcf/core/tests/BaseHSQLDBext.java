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
package org.apache.manifoldcf.core.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;
import java.lang.reflect.*;

/** This is a testing base class that is responsible for setting up/tearing down the core HSQLDB remote database. */
public class BaseHSQLDBext extends BaseHSQLDB
{
  protected DatabaseThread databaseThread = null;
  
  /** Method to set database properties */
  @Override
  protected void writeDatabaseControlProperties(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.hsqldbdatabaseprotocol\" value=\"hsql\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.hsqldbdatabaseserver\" value=\"localhost\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.hsqldbdatabaseinstance\" value=\"xdb\"/>\n"
    );
  }

  /** Method to get database superuser name.
  */
  @Override
  protected String getDatabaseSuperuserName()
    throws Exception
  {
    return "sa";
  }
  
  /** Method to get database superuser password.
  */
  @Override
  protected String getDatabaseSuperuserPassword()
    throws Exception
  {
    return "";
  }

  @Before
  public void startHSQLDBInstance()
    throws Exception
  {
    startDatabase();
  }
  
  @After
  public void stopHSQLDBInstance()
    throws Exception
  {
    stopDatabase();
  }
  
  protected void startDatabase()
    throws Exception
  {
    databaseThread = new DatabaseThread();
    databaseThread.start();
  }
  
  protected void stopDatabase()
    throws Exception
  {
    while (true)
    {
      if (!databaseThread.isAlive())
        break;
      databaseThread.interrupt();
      Thread.yield();
    }
    databaseThread.join();
  }
  
  protected static class DatabaseThread extends Thread
  {
    public DatabaseThread()
    {
      setName("Database runner thread");
    }
    
    public void run()
    {
      // We need to do the equivalent of:
      // java -cp ../lib/hsqldb.jar org.hsqldb.Server -database.0 file:mydb -dbname.0 xdb
      try
      {
        Class x = Class.forName("org.hsqldb.Server");
        String[] args = new String[]{"-database.0","file:extdb;hsqldb.tx=mvcc;hsqldb.cache_file_scale=512","-dbname.0","xdb"};
        Method m = x.getMethod("main",String[].class);
        m.invoke(null,(Object)args);
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
    
  }
  
}
