/* $Id: Logging.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.authorities.system;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.authorities.interfaces.*;
import java.util.*;

import org.apache.log4j.Logger;

/** This class furnishes the logging environment for the authorities application.
*/
public class Logging extends org.apache.acf.core.system.Logging
{

  // Public logger objects
  public static Logger authorityService = null;
  public static Logger authorityConnectors = null;

  /** Initialize logger setup.
  */
  public static synchronized void initializeLoggers()
  {
    org.apache.acf.core.system.Logging.initializeLoggers();

    if (authorityService != null)
      return;

    // package loggers
    authorityService = newLogger("org.apache.acf.authorityservice");
    authorityConnectors = newLogger("org.apache.acf.authorityconnectors");
  }


}
