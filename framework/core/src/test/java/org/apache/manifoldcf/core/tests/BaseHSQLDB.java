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

/** This is a testing base class that is responsible for setting up/tearing down the core Derby database. */
public class BaseHSQLDB
{
  protected File currentPath = null;
  protected File configFile = null;
  protected File loggingFile = null;
  protected File logOutputFile = null;

  protected void initialize()
    throws Exception
  {
    if (currentPath == null)
    {
      currentPath = new File(".").getCanonicalFile();

      // First, write a properties file and a logging file, in the current directory.
      configFile = new File("properties.xml").getCanonicalFile();
      loggingFile = new File("logging.ini").getCanonicalFile();
      logOutputFile = new File("manifoldcf.log").getCanonicalFile();

      // Set a system property that will point us to the proper place to find the properties file
      System.setProperty("org.apache.manifoldcf.configfile",configFile.getCanonicalFile().getAbsolutePath());
    }
  }
  
  protected boolean isInitialized()
  {
    return configFile.exists();
  }
  
  @Before
  public void setUp()
    throws Exception
  {
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      System.out.println("Warning: Preclean error: "+e.getMessage());
    }
    try
    {
      localSetUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }


  protected void localSetUp()
    throws Exception
  {
    initialize();
    String currentPathString = currentPath.getAbsolutePath();
    writeFile(loggingFile,
      "log4j.appender.MAIN.File="+logOutputFile.getAbsolutePath().replaceAll("\\\\","/")+"\n" +
      "log4j.rootLogger=WARN, MAIN\n" +
      "log4j.appender.MAIN=org.apache.log4j.RollingFileAppender\n" +
      "log4j.appender.MAIN.layout=org.apache.log4j.PatternLayout\n");

    writeFile(configFile,
      "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<configuration>\n"+
      "  <property name=\"org.apache.manifoldcf.databaseimplementationclass\" value=\"org.apache.manifoldcf.core.database.DBInterfaceHSQLDB\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.hsqldbdatabasepath\" value=\""+currentPathString.replaceAll("\\\\","/")+"\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.logconfigfile\" value=\""+loggingFile.getAbsolutePath().replaceAll("\\\\","/")+"\"/>\n" +
      "</configuration>\n");

    ManifoldCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    
    // Create the database
    ManifoldCF.createSystemDatabase(tc,"","");

  }
  
  @After
  public void cleanUp()
    throws Exception
  {
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }

  protected void localCleanUp()
    throws Exception
  {
    initialize();
    if (isInitialized())
    {
      ManifoldCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      
      // Remove the database
      ManifoldCF.dropSystemDatabase(tc,"","");
      
      // Get rid of the property and logging files.
      logOutputFile.delete();
      configFile.delete();
      loggingFile.delete();
    }
  }

  protected static void writeFile(File f, String fileContents)
    throws IOException
  {
    OutputStream os = new FileOutputStream(f);
    try
    {
      Writer w = new OutputStreamWriter(os,"utf-8");
      try
      {
        w.write(fileContents);
      }
      finally
      {
        w.close();
      }
    }
    finally
    {
      os.close();
    }
  }
  
}
