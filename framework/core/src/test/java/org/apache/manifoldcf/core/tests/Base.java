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
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is the base class of all ManifoldCF testing classes, at least the ones
* that manage configuration files and database setup. */
public class Base
{
  protected File currentPath = null;
  protected File configFile = null;
  protected File loggingFile = null;
  protected File logOutputFile = null;
  protected File connectorFile = null;

  protected void initialize()
    throws Exception
  {
    if (currentPath == null)
    {
      currentPath = new File(".").getCanonicalFile();

      // First, write a properties file and a logging file, in the current directory.
      configFile = new File("properties.xml").getCanonicalFile();
      loggingFile = new File("logging.xml").getCanonicalFile();
      logOutputFile = new File("manifoldcf.log").getCanonicalFile();
      connectorFile = new File("connectors.xml").getCanonicalFile();

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
    initializeSystem();
    try
    {
      localReset();
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

  /** Method to write the logging.ini contents.
  * Override this method if you want different contents, or you want to your own stuff.
  */
  protected void writeLoggingIni(StringBuilder output)
    throws Exception
  {
    output.append(
"<Configuration status=\"warn\" name=\"ManifoldCF\" packages=\"\">\n"+
"  <Appenders>\n"+
"    <File name=\"MyFile\" fileName=\""+logOutputFile.getAbsolutePath().replaceAll("\\\\","/")+"\">\n"+
"      <PatternLayout>\n"+
"        <Pattern>%5p %d{ISO8601} (%t) - %m%n</Pattern>\n"+
"      </PatternLayout>\n"+
"    </File>\n"+
"  </Appenders>\n"+
"  <Loggers>\n"+
"    <Root level=\"error\">\n"+
"      <AppenderRef ref=\"MyFile\"/>\n"+
"    </Root>\n"+
"  </Loggers>\n"+
"</Configuration>\n"
    );
  }
  
  /** Method to write the properties.xml contents.
  * Override this method if you want dto replace everything
  * with your own stuff.
  */
  protected void writePropertiesXML(StringBuilder output)
    throws Exception
  {
    output.append(
      "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<configuration>\n"
    );
    writeProperties(output);
    output.append(
      "</configuration>\n"
    );
  }
  
  /** Method to add properties to properties.xml contents.
  * Override this method to add properties clauses to the property file.
  */
  protected void writeProperties(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.logconfigfile\" value=\""+loggingFile.getAbsolutePath().replaceAll("\\\\","/")+"\"/>\n"+
      "  <property name=\"org.apache.manifoldcf.connectorsconfigurationfile\" value=\""+connectorFile.getAbsolutePath().replaceAll("\\\\","/")+"\"/>\n"+
      "  <property name=\"org.apache.manifoldcf.diagnostics\" value=\"DEBUG\"/>\n"
    );
  }
  
  /** Method to write the connectors.xml contents.
  * Override to replace everything.
  */
  protected void writeConnectorsXML(StringBuilder output)
    throws Exception
  {
    output.append(
      "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<connectors>\n"
    );
    writeConnectors(output);
    output.append(
      "</connectors>\n"
    );
  }
  
  /** Method to add connectors to connectors.xml contents.
  * Override this method to add connector clauses to the connectors file.
  */
  protected void writeConnectors(StringBuilder output)
    throws Exception
  {
  }
  
  /** Method to get database superuser name.
  */
  protected String getDatabaseSuperuserName()
    throws Exception
  {
    return "";
  }
  
  /** Method to get database superuser password.
  */
  protected String getDatabaseSuperuserPassword()
    throws Exception
  {
    return "";
  }

  protected void initializeSystem()
    throws Exception
  {
    initialize();
    
    StringBuilder loggingIniContents = new StringBuilder();
    writeLoggingIni(loggingIniContents);
    writeFile(loggingFile,loggingIniContents.toString());

    StringBuilder propertiesXMLContents = new StringBuilder();
    writePropertiesXML(propertiesXMLContents);
    writeFile(configFile,propertiesXMLContents.toString());

    StringBuilder connectorsXMLContents = new StringBuilder();
    writeConnectorsXML(connectorsXMLContents);
    writeFile(connectorFile,connectorsXMLContents.toString());

    ManifoldCF.initializeEnvironment(ThreadContextFactory.make());
  }
  
  protected void localSetUp()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    // Create the database
    ManifoldCF.createSystemDatabase(tc,getDatabaseSuperuserName(),getDatabaseSuperuserPassword());
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
    cleanupSystem();
  }

  protected void cleanupSystem()
    throws Exception
  {
    initialize();
    if (isInitialized())
    {
      // Get rid of the property and logging files.
      logOutputFile.delete();
      configFile.delete();
      loggingFile.delete();
      connectorFile.delete();
      
      IThreadContext threadContext = ThreadContextFactory.make();
      ManifoldCF.cleanUpEnvironment(threadContext);
      // Just in case we're not synchronized...
      ManifoldCF.resetEnvironment(threadContext);
    }
  }
  
  protected void localReset()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    // Remove the database
    ManifoldCF.dropSystemDatabase(tc,getDatabaseSuperuserName(),getDatabaseSuperuserPassword());
  }
  
  protected void localCleanUp()
    throws Exception
  {
    localReset();
  }

  protected static void writeFile(File f, String fileContents)
    throws IOException
  {
    OutputStream os = new FileOutputStream(f);
    try
    {
      Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
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
