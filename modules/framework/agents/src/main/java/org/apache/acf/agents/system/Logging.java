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
package org.apache.acf.agents.system;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import java.util.*;

import org.apache.log4j.Logger;

/** This class furnishes the logging environment for the crawler application.
*/
public class Logging extends org.apache.acf.core.system.Logging
{
  public static final String _rcsid = "@(#)$Id$";

  // Public logger objects
  public static Logger agents = null;
  public static Logger ingest = null;
  public static Logger api = null;

  /** Initialize logger setup.
  */
  public static synchronized void initializeLoggers()
  {
    org.apache.acf.core.system.Logging.initializeLoggers();

    if (agents != null)
      return;

    // package loggers
    agents = newLogger("org.apache.acf.agents");
    ingest = newLogger("org.apache.acf.ingest");
    api = newLogger("org.apache.acf.api");

  }


}
