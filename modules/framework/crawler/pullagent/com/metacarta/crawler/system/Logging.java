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
package com.metacarta.crawler.system;

import com.metacarta.core.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import java.util.*;

import org.apache.log4j.Logger;

/** This class furnishes the logging environment for the crawler application.
*/
public class Logging extends com.metacarta.agents.system.Logging
{
	public static final String _rcsid = "@(#)$Id$";


	// Public logger objects
	public static Logger threads = null;
	public static Logger jobs = null;
	public static Logger connectors = null;
	public static Logger hopcount = null;
	public static Logger scheduling = null;

	/** Initialize logger setup.
	*/
	public static synchronized void initializeLoggers()
	{
		com.metacarta.agents.system.Logging.initializeLoggers();

		if (jobs != null)
			return;

		// package loggers
		threads = newLogger("com.metacarta.crawlerthreads");
		jobs = newLogger("com.metacarta.jobs");
		connectors = newLogger("com.metacarta.connectors");
		hopcount = newLogger("com.metacarta.hopcount");
		scheduling = newLogger("com.metacarta.scheduling");
	}


}
