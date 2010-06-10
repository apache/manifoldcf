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
package org.apache.lcf.core.tests;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.core.system.LCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the core Derby database. */
public class TestBase
{
  protected File currentPath = null;
  protected File propertiesFile = null;
  protected File loggingFile = null;
  protected File logOutputFile = null;

  protected void initialize()
    throws Exception
  {
    if (currentPath == null)
    {
      currentPath = new File(".").getCanonicalFile();

      // First, write a properties file and a logging file, in the current directory.
      propertiesFile = new File("properties.ini").getCanonicalFile();
      loggingFile = new File("logging.ini").getCanonicalFile();
      logOutputFile = new File("lcf.log").getCanonicalFile();

      // Set a system property that will point us to the proper place to find the properties file
      System.setProperty("org.apache.lcf.configfile",propertiesFile.getCanonicalFile().getAbsolutePath());
    }
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

    writeFile(propertiesFile,
      "org.apache.lcf.databaseimplementationclass=org.apache.lcf.core.database.DBInterfaceDerby\n" +
      "org.apache.lcf.derbydatabasepath="+currentPathString.replaceAll("\\\\","/")+"\n" +
      "org.apache.lcf.logconfigfile="+loggingFile.getAbsolutePath().replaceAll("\\\\","/")+"\n");

    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    
    // Create the database
    LCF.createSystemDatabase(tc,"","");

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
    if (propertiesFile.exists())
    {
      LCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      
      // Remove the database
      LCF.dropSystemDatabase(tc,"","");
      
      // Get rid of the property and logging files.
      logOutputFile.delete();
      propertiesFile.delete();
      loggingFile.delete();
    }
  }

  protected static void writeFile(File f, String fileContents)
    throws IOException
  {
    OutputStream os = new FileOutputStream(f);
    try
    {
      Writer w = new OutputStreamWriter(os);
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
