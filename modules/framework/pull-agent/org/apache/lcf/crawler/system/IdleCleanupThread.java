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
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This thread periodically calls the cleanup method in all connected repository connectors.  The ostensible purpose
* is to allow the connectors to shutdown idle connections etc.
*/
public class IdleCleanupThread extends Thread
{
	public static final String _rcsid = "@(#)$Id$";

	// Local data


	/** Constructor.
	*/
	public IdleCleanupThread()
		throws LCFException
	{
		super();
		setName("Idle cleanup thread");
		setDaemon(true);
	}

	public void run()
	{
		Logging.threads.debug("Start up idle cleanup thread");
		try
		{
			// Create a thread context object.
			IThreadContext threadContext = ThreadContextFactory.make();

			// Loop
			while (true)
			{
				// Do another try/catch around everything in the loop
				try
				{
					// Do the cleanup
					RepositoryConnectorFactory.pollAllConnectors(threadContext);
					OutputConnectorFactory.pollAllConnectors(threadContext);
                                    
					// Sleep for the retry interval.
					LCF.sleep(15000L);
				}
				catch (LCFException e)
				{
					if (e.getErrorCode() == LCFException.INTERRUPTED)
						break;

					if (e.getErrorCode() == LCFException.DATABASE_CONNECTION_ERROR)
					{
						Logging.threads.error("Idle cleanup thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
						try
						{
							// Give the database a chance to catch up/wake up
							LCF.sleep(10000L);
						}
						catch (InterruptedException se)
						{
							break;
						}
						continue;
					}

					// Log it, but keep the thread alive
					Logging.threads.error("Exception tossed: "+e.getMessage(),e);

					if (e.getErrorCode() == LCFException.SETUP_ERROR)
					{
						// Shut the whole system down!
						System.exit(1);
					}

				}
				catch (InterruptedException e)
				{
					// We're supposed to quit
					break;
				}
				catch (OutOfMemoryError e)
				{
					System.err.println("metacarta-agents ran out of memory - please contact MetaCarta Customer Support");
					e.printStackTrace(System.err);
					System.exit(-200);
				}
				catch (Throwable e)
				{
					// A more severe error - but stay alive
					Logging.threads.fatal("Error tossed: "+e.getMessage(),e);
				}
			}
		}
		catch (Throwable e)
		{
			// Severe error on initialization
			System.err.println("metacarta-agents could not start - please contact MetaCarta Customer Support");
			Logging.threads.fatal("IdleCleanupThread initialization error tossed: "+e.getMessage(),e);
			System.exit(-300);
		}

	}

}
