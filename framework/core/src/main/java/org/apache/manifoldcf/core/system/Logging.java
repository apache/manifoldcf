/* $Id: Logging.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.system;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;
import java.io.*;

import org.apache.log4j.*;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;

/** This class furnishes the logging environment for ManifoldCF.
*/
public class Logging
{
  public static final String _rcsid = "@(#)$Id: Logging.java 988245 2010-08-23 18:39:35Z kwright $";

  // Public logger objects.  Initialized by initializeLoggers() method.
  public static Logger root = null;
  public static Logger misc = null;
  public static Logger db = null;
  public static Logger lock = null;
  public static Logger cache = null;
  public static Logger keystore = null;
  public static Logger perf = null;
  public static Logger diagnostics = null;

  private static HashMap loggerTable = null;
  private static HashMap logLevelMap = null;

  /** Initialize logger setup.
  */
  public static synchronized void initializeLoggingSystem(final File logConfigFile)
  {
    if (logLevelMap != null)
      return;


    // configuration: log map hash
    logLevelMap = new HashMap();
    logLevelMap.put("OFF", Level.OFF);
    logLevelMap.put("FATAL", Level.FATAL);
    logLevelMap.put("WARN", Level.WARN);
    logLevelMap.put("ERROR", Level.ERROR);
    logLevelMap.put("INFO", Level.INFO);
    logLevelMap.put("DEBUG", Level.DEBUG);
    logLevelMap.put("ALL", Level.ALL);

    loggerTable = new HashMap();

    System.setProperty("log4j.configurationFile", logConfigFile.toString());
    //System.err.println("ManifoldCF logger setup complete");
  }

  /** Set up loggers used by core package.
  */
  public static synchronized void initializeLoggers()
  {
    // package loggers
    if (misc != null)
      return;

    root = newLogger("org.apache.manifoldcf.root");
    misc = newLogger("org.apache.manifoldcf.misc");
    db = newLogger("org.apache.manifoldcf.db");
    lock = newLogger("org.apache.manifoldcf.lock");
    cache = newLogger("org.apache.manifoldcf.cache");
    keystore = newLogger("org.apache.manifoldcf.keystore");
    perf = newLogger("org.apache.manifoldcf.perf");
    diagnostics = newLogger("org.apache.manifoldcf.diagnostics");
  }

  /** Reset all loggers
  */
  public static void setLogLevels(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // System.out.println("Setting log levels @ " + new Date().toString());
    Iterator it = loggerTable.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry e = (Map.Entry)it.next();
      Logger logger = (Logger)e.getValue();

      // logger name;
      String loggername = (String)e.getKey();

      // logger level
      String level = LockManagerFactory.getProperty(threadContext, loggername);

      Level loglevel = null;
      if (level != null && level.length() > 0)
      {
        loglevel = (Level)logLevelMap.get(level);
      }

      if (loglevel==null)
      {
        loglevel = Level.WARN;
      }

      try
      {
        logger.setLevel(loglevel);
      }
      catch (Exception ex)
      {
        System.err.println("Unable to set log level " + level +
          " on logger " + loggername);
        ex.printStackTrace();
      }
    }
  }

  /** Get a logger given a logger name.
  *@param loggerName is the logger name.
  *@return the logger.
  */
  public static final Logger getLogger(String loggerName)
  {
    return (Logger)loggerTable.get(loggerName);
  }

  /** Register a new logger.
  *@param s is the logger name.
  *@return the new logger.
  */
  public static final Logger newLogger(String s)
  {
    Logger l = Logger.getLogger(s);
    loggerTable.put(s, l);
    return l;
  }


}
