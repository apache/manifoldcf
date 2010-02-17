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

/** This class represents the background seeding thread.  Its job is to add
* seeded documents from the connector periodically, during adaptive crawls
* (which continue until stopped).  The actual use case is for creating a
* connector that handles RSS feeds, including keeping them current and
* handling deletions.
*/
public class SeedingThread extends Thread
{
	public static final String _rcsid = "@(#)$Id$";

	/** Worker thread pool reset manager */
	protected static SeedingResetManager resetManager = new SeedingResetManager();

	// Local data
	protected QueueTracker queueTracker;

	/** The number of documents that are added to the queue per transaction */
	protected final static int MAX_COUNT = 100;

	/** Constructor.
	*/
	public SeedingThread(QueueTracker queueTracker)
		throws MetacartaException
	{
		super();
		setName("Seeding thread");
		setDaemon(true);
		this.queueTracker = queueTracker;
	}

	public void run()
	{
		resetManager.registerMe();

		try
		{
			// Create a thread context object.
			IThreadContext threadContext = ThreadContextFactory.make();
			IJobManager jobManager = JobManagerFactory.make(threadContext);
			IRepositoryConnectionManager connectionMgr = RepositoryConnectionManagerFactory.make(threadContext);

			IDBInterface database = DBInterfaceFactory.make(threadContext,
				Metacarta.getMasterDatabaseName(),
				Metacarta.getMasterDatabaseUsername(),
				Metacarta.getMasterDatabasePassword());

			String[] identifiers = new String[MAX_COUNT];
			// Loop
			while (true)
			{
				// Do another try/catch around everything in the loop
				try
				{
					// Before we begin, conditionally reset
					resetManager.waitForReset(threadContext);

					long currentTime = System.currentTimeMillis();

					// Accumulate the wait before doing the next check.
					// We start with 10 seconds, which is the maximum.  If there's a service request
					// that's faster than that, we'll adjust the time downward.
					long waitTime = 60000L;

					Logging.threads.debug("Seeding thread woke up");

					// Grab active, adaptive jobs (and set their state to xxxSEEDING as a side effect)
					JobStartRecord[] seedJobs = jobManager.getJobsReadyForSeeding(currentTime);

					// Process these jobs, and do the seeding.  The seeding is based on what came back
					// in the job start record for sync time.  If there's an interruption, we just go on
					// to the next job, since the whole thing will retry anyhow.
					try
					{

					    if (seedJobs.length == 0)
					    {
						Logging.threads.debug("Seeding thread found nothing to do");
						Metacarta.sleep(waitTime);
						continue;
					    }

					    if (Logging.threads.isDebugEnabled())
						Logging.threads.debug("Seeding thread: Found "+Integer.toString(seedJobs.length)+" jobs to seed");

					    // Loop through jobs
					    int i = 0;
					    while (i < seedJobs.length)
					    {
						JobStartRecord jsr = seedJobs[i++];
						Long jobID = jsr.getJobID();
						try
						{
						    long lastJobTime = jsr.getSynchTime();
						    IJobDescription jobDescription = jobManager.load(jobID,true);
						    int jobType = jobDescription.getType();

						    int hopcountMethod = jobDescription.getHopcountMode();

						    IRepositoryConnection connection = connectionMgr.load(jobDescription.getConnectionName());
					    	    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
								connection.getClassName(),
								connection.getConfigParams(),
								connection.getMaxConnections());
						    // Null will come back if the connector instance could not be obtained, so just skip in that case.
						    if (connector == null)
							continue;
						    try
						    {


							// Get the number of link types.
							String[] legalLinkTypes = connector.getRelationshipTypes();
							
							int model = connector.getConnectorModel();

							try
							{

							    SeedingActivity activity = new SeedingActivity(connection.getName(),connectionMgr,jobManager,queueTracker,
								connection,connector,jobID,legalLinkTypes,false,hopcountMethod);

							    if (Logging.threads.isDebugEnabled())
								Logging.threads.debug("Seeding thread: Getting seeds for job "+jobID.toString());

							    connector.addSeedDocuments(activity,jobDescription.getSpecification(),lastJobTime,currentTime,jobType);

							    activity.doneSeeding(model==connector.MODEL_PARTIAL);

							    if (Logging.threads.isDebugEnabled())
								Logging.threads.debug("Seeding thread: Done processing seeds from job "+jobID.toString());


							}
							catch (ServiceInterruption e)
							{
								// Note the service interruption
								Logging.threads.error("Service interruption for job "+jobID,e);
								long retryInterval = e.getRetryTime() - currentTime;
								if (retryInterval >= 0L && retryInterval < waitTime)
									waitTime = retryInterval;
								// Go on to the next job
								continue;
							}
						    }
						    finally
						    {
							RepositoryConnectorFactory.release(connector);
						    }


						    if (Logging.threads.isDebugEnabled())
							Logging.threads.debug("Seeding thread: Successfully reseeded job "+jobID.toString());

						    // Note that this job has been seeded!
						    jobManager.noteJobSeeded(jobID,currentTime);
						    jsr.noteStarted();

						}
						catch (MetacartaException e)
						{
							if (e.getErrorCode() == MetacartaException.INTERRUPTED)
								throw new InterruptedException();
							if (e.getErrorCode() == MetacartaException.DATABASE_CONNECTION_ERROR)
								throw e;
							if (e.getErrorCode() == MetacartaException.REPOSITORY_CONNECTION_ERROR)
							{
								Logging.threads.warn("Seeding thread: Ignoring connection error: "+e.getMessage(),e);
								continue;
							}
							if (jobManager.errorAbort(jobID,e.getMessage()))
								Logging.threads.error("Exception tossed: "+e.getMessage(),e);
							// We DO have to clean up, because there is otherwise no
							// way the job will be reset from the seeding state.
						}
					    }
					}
					finally
					{
						// Clean up all jobs that did not seed
						MetacartaException exception = null;
						int i = 0;
						while (i < seedJobs.length)
						{
							JobStartRecord jsr = seedJobs[i++];
							if (!jsr.wasStarted())
							{
						    		if (Logging.threads.isDebugEnabled())
									Logging.threads.debug("Seeding thread: aborting reseed for "+jsr.getJobID().toString());

								// Clean up from failed seed.
								try
								{
									jobManager.resetSeedJob(jsr.getJobID());
								}
								catch (MetacartaException e)
								{
									exception = e;
								}
							}
						}
						if (exception != null)
							throw exception;
					}

					// Sleep for the retry interval.
					Metacarta.sleep(waitTime);
				}
				catch (MetacartaException e)
				{
					if (e.getErrorCode() == MetacartaException.INTERRUPTED)
						break;

					if (e.getErrorCode() == MetacartaException.DATABASE_CONNECTION_ERROR)
					{
						resetManager.noteEvent();

						Logging.threads.error("Seeding thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
						try
						{
							// Give the database a chance to catch up/wake up
							Metacarta.sleep(10000L);
						}
						catch (InterruptedException se)
						{
							break;
						}
						continue;
					}

					// Log it, but keep the thread alive
					Logging.threads.error("Exception tossed: "+e.getMessage(),e);

					if (e.getErrorCode() == MetacartaException.SETUP_ERROR)
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
			Logging.threads.fatal("SeedingThread initialization error tossed: "+e.getMessage(),e);
			System.exit(-300);
		}
	}

	/** Class which handles reset for seeding thread pool (of which there's
	* typically only one member).  The reset action here
	* is to move the status of jobs back from "seeding" to normal.
	*/
	protected static class SeedingResetManager extends ResetManager
	{

		/** Constructor. */
		public SeedingResetManager()
		{
			super();
		}

		/** Reset */
		protected void performResetLogic(IThreadContext tc)
			throws MetacartaException
		{
			IJobManager jobManager = JobManagerFactory.make(tc);
			jobManager.resetSeedingWorkerStatus();
		}
	}

}
