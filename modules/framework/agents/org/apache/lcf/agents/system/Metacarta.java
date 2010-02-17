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
package org.apache.lcf.agents.system;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import java.io.*;
import java.util.*;

public class Metacarta extends org.apache.lcf.core.system.Metacarta
{
	public static final String _rcsid = "@(#)$Id$";

	protected static boolean initialized = false;

	/** Ingestion buffer size property. */
	public static String ingestBufferSizeProperty = "org.apache.lcf.ingest.buffersize";
	public static String ingestCredentialsRealm = "org.apache.lcf.ingest.credentialrealm";
	public static String ingestResponseRetryCount = "org.apache.lcf.ingest.responseretrycount";
	public static String ingestResponseRetryInterval = "org.apache.lcf.ingest.retryinterval";
	public static String ingestRescheduleInterval = "org.apache.lcf.ingest.rescheduleinterval";
	public static String ingestURIProperty = "org.apache.lcf.ingest.uri";
	public static String ingestUserProperty = "org.apache.lcf.ingest.user";
	public static String ingestPasswordProperty = "org.apache.lcf.ingest.password";
	public static String ingestMaxConnectionsProperty = "org.apache.lcf.ingest.maxconnections";

	/** This is the place we keep track of the agents we've started. */
	protected static HashMap runningHash = new HashMap();

	/** Initialize environment.
	*/
	public static synchronized void initializeEnvironment()
	{
		// System.out.println(" In agents initializeEnvironment");
		if (initialized)
			return;

		// System.out.println("Initializing");
		org.apache.lcf.core.system.Metacarta.initializeEnvironment();
		Logging.initializeLoggers();
		Logging.setLogLevels();
		initialized = true;
	}


	/** Install the agent tables.  This is also responsible for upgrading the existing
	* tables!!!
	*@param threadcontext is the thread context.
	*/
	public static void installTables(IThreadContext threadcontext)
		throws MetacartaException
	{
		IAgentManager mgr = AgentManagerFactory.make(threadcontext);
		IIncrementalIngester igstmgr = IncrementalIngesterFactory.make(threadcontext);
		IOutputConnectorManager outputConnMgr = OutputConnectorManagerFactory.make(threadcontext);
		IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadcontext);
                mgr.install();
		outputConnMgr.install();
		outputConnectionManager.install();
                igstmgr.install();
	}

	/** Uninstall all the crawler system tables.
	*@param threadcontext is the thread context.
	*/
	public static void deinstallTables(IThreadContext threadcontext)
		throws MetacartaException
	{
		IAgentManager mgr = AgentManagerFactory.make(threadcontext);
		IIncrementalIngester igstmgr = IncrementalIngesterFactory.make(threadcontext);
		IOutputConnectorManager outputConnMgr = OutputConnectorManagerFactory.make(threadcontext);
		IOutputConnectionManager outputConnectionManager = OutputConnectionManagerFactory.make(threadcontext);
                igstmgr.deinstall();
		outputConnectionManager.deinstall();
		outputConnMgr.deinstall();
                mgr.deinstall();
	}


	/** Start agents.
	*@param threadContext is the thread context.
	*/
	public static void startAgents(IThreadContext threadContext)
		throws MetacartaException
	{
		// Get agent manager
		IAgentManager manager = AgentManagerFactory.make(threadContext);
		String[] classes = manager.getAllAgents();
		int i = 0;
		while (i < classes.length)
		{
			String className = classes[i++];
			synchronized (runningHash)
			{
				if (runningHash.get(className) == null)
				{
					// Start this agent
					IAgent agent = AgentFactory.make(threadContext,className);
					// Start it
					agent.startAgent();
					// Successful
					runningHash.put(className,agent);
				}
			}
		}
		// Done.
	}

	/** Stop agents.
	*@param threadContext is the thread context
	*/
	public static void stopAgents(IThreadContext threadContext)
		throws MetacartaException
	{
		synchronized (runningHash)
		{
			HashMap iterHash = (HashMap)runningHash.clone();
			Iterator iter = iterHash.keySet().iterator();
			while (iter.hasNext())
			{
				String className = (String)iter.next();
				IAgent agent = (IAgent)runningHash.get(className);
				// Stop it
				agent.stopAgent();
				runningHash.remove(className);
			}
		}
		// Done.
	}
}

