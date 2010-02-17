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

/** This class represents the thread that notices jobs that have completed their shutdown phase, and resets them back to
* inactive.
*/
public class JobResetThread extends Thread
{
	public static final String _rcsid = "@(#)$Id$";

	// Local data
	protected QueueTracker queueTracker;
	
	/** Constructor.
	*/
	public JobResetThread(QueueTracker queueTracker)
		throws LCFException
	{
		super();
		setName("Job reset thread");
		setDaemon(true);
		this.queueTracker = queueTracker;
	}

	public void run()
	{
		try
		{
			// Create a thread context object.
			IThreadContext threadContext = ThreadContextFactory.make();
			IJobManager jobManager = JobManagerFactory.make(threadContext);
			IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);
			
			// Loop
			while (true)
			{
				// Do another try/catch around everything in the loop
				try
				{
					// See if there are any completed jobs
					long currentTime = System.currentTimeMillis();
					ArrayList jobAborts = new ArrayList();
					jobManager.finishJobAborts(currentTime,jobAborts);
					int k = 0;
					while (k < jobAborts.size())
					{
						IJobDescription desc = (IJobDescription)jobAborts.get(k++);
						connectionManager.recordHistory(desc.getConnectionName(),
							null,connectionManager.ACTIVITY_JOBABORT,null,
							desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
					}
					jobManager.resetJobs(currentTime);
					
					// If there were any job aborts, we must reprioritize all active documents, since we've done something
					// not predicted by the algorithm that assigned those priorities.  This is, of course, quite expensive,
					// but it cannot be helped (at least, I cannot find a way to avoid it).
					//
					if (jobAborts.size() > 0)
					{
						Logging.threads.debug("Job reset thread reprioritizing documents...");

						// Reset the queue tracker
						queueTracker.beginReset();
						// Perform the reprioritization, for all active documents in active jobs.  During this time,
						// it is safe to have other threads assign new priorities to documents, but it is NOT safe
						// for other threads to attempt to change the minimum priority level.  The queuetracker object
						// will therefore block that from occurring, until the reset is complete.
						try
						{
							// Reprioritize all documents in the jobqueue, 1000 at a time
							
							HashMap connectionMap = new HashMap();
							HashMap jobDescriptionMap = new HashMap();

							// Do the 'not yet processed' documents only.  Documents that are queued for reprocessing will be assigned
							// new priorities.  Already processed documents won't.  This guarantees that our bins are appropriate for current thread
							// activity.
							// In order for this to be the correct functionality, ALL reseeding and requeuing operations MUST reset the associated document
							// priorities.
							while (true)
							{
								long startTime = System.currentTimeMillis();
								
								DocumentDescription[] docs = jobManager.getNextNotYetProcessedReprioritizationDocuments(currentTime, 10000);
								if (docs.length == 0)
									break;
								
								// Calculate new priorities for all these documents
								LCF.writeDocumentPriorities(threadContext,connectionManager,jobManager,docs,connectionMap,jobDescriptionMap,queueTracker,currentTime);
								
								Logging.threads.debug("Reprioritized "+Integer.toString(docs.length)+" not-yet-processed documents in "+new Long(System.currentTimeMillis()-startTime)+" ms");
							}
						}
						finally
						{
							queueTracker.endReset();
						}
						
						Logging.threads.debug("Job reset thread done reprioritizing documents.");

					}

					LCF.sleep(10000L);
				}
				catch (LCFException e)
				{
					if (e.getErrorCode() == LCFException.INTERRUPTED)
						break;

					if (e.getErrorCode() == LCFException.DATABASE_CONNECTION_ERROR)
					{
						Logging.threads.error("Job reset thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
					System.err.println("agents process ran out of memory - shutting down");
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
			System.err.println("agents process could not start - shutting down");
			Logging.threads.fatal("JobResetThread initialization error tossed: "+e.getMessage(),e);
			System.exit(-300);
		}
	}

}
