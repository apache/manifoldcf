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
import com.metacarta.agents.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import com.metacarta.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This class represents the startup thread.  This thread's job is to detect when a job is starting, and stuff its queue
* appropriately.  Finally, the job is marked as "started".
*/
public class StartupThread extends Thread
{
	public static final String _rcsid = "@(#)$Id$";

	/** Worker thread pool reset manager */
	protected static StartupResetManager resetManager = new StartupResetManager();

	// Local data
	protected QueueTracker queueTracker;
	
	/** The number of documents that are added to the queue per transaction */
	protected final static int MAX_COUNT = 100;

	/** Constructor.
	*/
	public StartupThread(QueueTracker queueTracker)
		throws MetacartaException
	{
		super();
		setName("Startup thread");
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

					// Accumulate the wait before doing the next check.
					// We start with 10 seconds, which is the maximum.  If there's a service request
					// that's faster than that, we'll adjust the time downward.
					long waitTime = 10000L;

					if (Logging.threads.isDebugEnabled())
						Logging.threads.debug("Checking for starting jobs");
					
					// See if there are any starting jobs.
					// Note: Since this following call changes the job state, we must be careful to reset it on any kind of failure.
					JobStartRecord[] startupJobs = jobManager.getJobsReadyForStartup();
					try
					{

					    if (startupJobs.length == 0)
					    {
						Metacarta.sleep(waitTime);
						continue;
					    }

					    if (Logging.threads.isDebugEnabled())
						    Logging.threads.debug("Found "+Integer.toString(startupJobs.length)+" jobs ready to be started");
					    
					    long currentTime = System.currentTimeMillis();


					    // Loop through jobs
					    int i = 0;
					    while (i < startupJobs.length)
					    {
						JobStartRecord jsr = startupJobs[i++];
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

						    // If the attempt to grab a connector instance failed, don't start the job, of course.
						    if (connector == null)
							continue;
						    
						    // Only now record the fact that we are trying to start the job.
						    connectionMgr.recordHistory(jobDescription.getConnectionName(),
							null,connectionMgr.ACTIVITY_JOBSTART,null,
							jobID.toString()+"("+jobDescription.getDescription()+")",null,null,null);

						    try
						    {
							int model = connector.getConnectorModel();
							// Get the number of link types.
							String[] legalLinkTypes = connector.getRelationshipTypes();

							// The old logic here looked at the model, and if it was incomplete, either
							// performed a complete crawl initialization, or an incremental crawl initialization.
							// The fact is that we can now determine automatically what kind of crawl should be
							// done, based on the model the connector states and on the starting time that we
							// would be feeding it.  (The starting time is reset to 0 when the document specification
							// is changed - that's a crucial consideration.)
							//
							// The new logic does this:
							//
							// (1) If the connector has MODEL_ADD_CHANGE_DELETE, then
							// we let the connector run the show; there's no purge phase, and therefore the
							// documents are left in a COMPLETED state if they don't show up in the list
							// of seeds that require the attention of the connector.
							//
							// (2) If the connector has MODEL_ALL, then it's a full crawl no matter what, so
							// we do a full scan initialization.
							//
							// (3) If the connector has some other model, we look at the start time.  A start
							// time of 0 implies a full scan, while any other start time implies an incremental
							// scan.

							if (model != connector.MODEL_ADD_CHANGE_DELETE)
							{
								if (Logging.threads.isDebugEnabled())
									Logging.threads.debug("Preparing job "+jobID.toString()+" for execution...");
								if (jobType != IJobDescription.TYPE_CONTINUOUS && model != connector.MODEL_PARTIAL && (model == connector.MODEL_ALL || lastJobTime == 0L))
									jobManager.prepareFullScan(jobID,legalLinkTypes,hopcountMethod);
								else
									jobManager.prepareIncrementalScan(jobID,legalLinkTypes,hopcountMethod);
								if (Logging.threads.isDebugEnabled())
									Logging.threads.debug("Prepared job "+jobID.toString()+" for execution.");
							}

							try
							{
							    SeedingActivity activity = new SeedingActivity(connection.getName(),connectionMgr,jobManager,queueTracker,
								connection,connector,jobID,legalLinkTypes,true,hopcountMethod);

							    if (Logging.threads.isDebugEnabled())
								Logging.threads.debug("Adding initial seed documents for job "+jobID.toString()+"...");
							    // Get the initial seed documents, and make sure those are added
							    connector.addSeedDocuments(activity,jobDescription.getSpecification(),lastJobTime,currentTime,jobType);
							    // Flush anything left
							    activity.doneSeeding(model==connector.MODEL_PARTIAL);
							    if (Logging.threads.isDebugEnabled())
								Logging.threads.debug("Done adding initial seed documents for job "+jobID.toString()+".");
							}
							catch (ServiceInterruption e)
							{
								// Note the service interruption
								Logging.threads.warn("Service interruption for job "+jobID,e);
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

						    // Start this job!
						    jobManager.noteJobStarted(jobID,currentTime);
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
								Logging.threads.warn("Startup thread: connection error; continuing: "+e.getMessage(),e);
								continue;
							}
							// Note: The error abort below will put the job in the "ABORTINGSTARTUP" state.  We still need a reset at that point
							// to get all the way back to an "aborting" state.
							if (jobManager.errorAbort(jobID,e.getMessage()))
								Logging.threads.error("Exception tossed: "+e.getMessage(),e);
						}
					    }
					}
					finally
					{
						// Clean up all jobs that did not start
						MetacartaException exception = null;
						int i = 0;
						while (i < startupJobs.length)
						{
							JobStartRecord jsr = startupJobs[i++];
							if (!jsr.wasStarted())
							{
								// Clean up from failed start.
								try
								{
									jobManager.resetStartupJob(jsr.getJobID());
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

						Logging.threads.error("Startup thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
			Logging.threads.fatal("StartupThread initialization error tossed: "+e.getMessage(),e);
			System.exit(-300);
		}
	}

	/** Class which handles reset for seeding thread pool (of which there's
	* typically only one member).  The reset action here
	* is to move the status of jobs back from "seeding" to normal.
	*/
	protected static class StartupResetManager extends ResetManager
	{

		/** Constructor. */
		public StartupResetManager()
		{
			super();
		}

		/** Reset */
		protected void performResetLogic(IThreadContext tc)
			throws MetacartaException
		{
			IJobManager jobManager = JobManagerFactory.make(tc);
			jobManager.resetStartupWorkerStatus();
		}
	}

}
