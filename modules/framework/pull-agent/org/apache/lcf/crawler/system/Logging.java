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
package org.apache.lcf.crawler.system;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import java.util.*;

import org.apache.log4j.Logger;

/** This class furnishes the logging environment for the crawler application.
*/
public class Logging extends org.apache.lcf.agents.system.Logging
{
  public static final String _rcsid = "@(#)$Id$";


  // Public logger objects
  public static Logger threads = null;
  public static Logger jobs = null;
  public static Logger connectors = null;
  public static Logger hopcount = null;
  public static Logger scheduling = null;
  public static Logger api = null;

  /** Initialize logger setup.
  */
  public static synchronized void initializeLoggers()
  {
    org.apache.lcf.agents.system.Logging.initializeLoggers();

    if (jobs != null)
      return;

    // package loggers
    threads = newLogger("org.apache.lcf.crawlerthreads");
    jobs = newLogger("org.apache.lcf.jobs");
    connectors = newLogger("org.apache.lcf.connectors");
    hopcount = newLogger("org.apache.lcf.hopcount");
    scheduling = newLogger("org.apache.lcf.scheduling");
    api = newLogger("org.apache.lcf.api");
  }


}
