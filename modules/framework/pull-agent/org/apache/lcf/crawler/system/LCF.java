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
import org.apache.lcf.authorities.interfaces.*;
import java.io.*;
import java.util.*;

public class LCF extends org.apache.lcf.agents.system.LCF
{
	public static final String _rcsid = "@(#)$Id$";

	// Track whether initialized or not
	protected static Integer isInitialized = null;

	// Thread objects.
	// These get filled in as threads are created.
	protected static InitializationThread initializationThread = null;
	protected static JobStartThread jobStartThread = null;
	protected static StufferThread stufferThread = null;
	protected static FinisherThread finisherThread = null;
	protected static StartupThread startupThread = null;
	protected static JobDeleteThread jobDeleteThread = null;
	protected static WorkerThread[] workerThreads = null;
	protected static ExpireStufferThread expireStufferThread = null;
	protected static ExpireThread[] expireThreads = null;
	protected static DocumentDeleteStufferThread deleteStufferThread = null;
	protected static DocumentDeleteThread[] deleteThreads = null;
	protected static JobResetThread jobResetThread = null;
	protected static SeedingThread seedingThread = null;
	protected static IdleCleanupThread idleCleanupThread = null;
	protected static SetPriorityThread setPriorityThread = null;

	// Reset managers
	/** Worker thread pool reset manager */
	protected static WorkerResetManager workerResetManager = null;
	/** Delete thread pool reset manager */
	protected static DocDeleteResetManager docDeleteResetManager = null;

	// Number of worker threads
	protected static int numWorkerThreads = 0;
	// Number of delete threads
	protected static int numDeleteThreads = 0;
	// Number of expiration threads
	protected static int numExpireThreads = 0;
	// Factor for low water level in queueing
	protected static float lowWaterFactor = 5.0f;
	// Factor in amount to stuff
	protected static float stuffAmtFactor = 0.5f;

	protected static final String workerThreadCountProperty = "org.apache.lcf.crawler.threads";
	protected static final String deleteThreadCountProperty = "org.apache.lcf.crawler.deletethreads";
	protected static final String expireThreadCountProperty = "org.apache.lcf.crawler.expirethreads";
	protected static final String lowWaterFactorProperty = "org.apache.lcf.crawler.lowwaterfactor";
	protected static final String stuffAmtFactorProperty = "org.apache.lcf.crawler.stuffamountfactor";

	/** Initialize environment.
	*/
	public static synchronized void initializeEnvironment()
	{
		org.apache.lcf.authorities.system.LCF.initializeEnvironment();

		if (isInitialized != null)
			return;

		isInitialized = new Integer(0);
		org.apache.lcf.agents.system.LCF.initializeEnvironment();
		Logging.initializeLoggers();
		Logging.setLogLevels();

	}


	/** Install all the crawler system tables.
	*@param threadcontext is the thread context.
	*/
	public static void installSystemTables(IThreadContext threadcontext)
		throws LCFException
	{
		IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
		IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
		IJobManager jobManager = JobManagerFactory.make(threadcontext);
                org.apache.lcf.authorities.system.LCF.installSystemTables(threadcontext);
                repConnMgr.install();
                repCon.install();
                jobManager.install();
	}

	/** Uninstall all the crawler system tables.
	*@param threadcontext is the thread context.
	*/
	public static void deinstallSystemTables(IThreadContext threadcontext)
		throws LCFException
	{
		LCFException se = null;

		IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
		IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
		IJobManager jobManager = JobManagerFactory.make(threadcontext);
                jobManager.deinstall();
                repCon.deinstall();
                repConnMgr.deinstall();
                org.apache.lcf.authorities.system.LCF.deinstallSystemTables(threadcontext);
		if (se != null)
			throw se;
	}


	/** Start everything.
	*/
	public static void startSystem(IThreadContext threadContext)
		throws LCFException
	{

		// Now, start all the threads
		String maxThreads = getProperty(workerThreadCountProperty);
		if (maxThreads == null)
			maxThreads = "100";
		numWorkerThreads = new Integer(maxThreads).intValue();
		if (numWorkerThreads < 1 || numWorkerThreads > 300)
			throw new LCFException("Illegal value for the number of worker threads");
		String maxDeleteThreads = getProperty(deleteThreadCountProperty);
		if (maxDeleteThreads == null)
			maxDeleteThreads = "10";
		String maxExpireThreads = getProperty(expireThreadCountProperty);
		if (maxExpireThreads == null)
			maxExpireThreads = "10";
		numDeleteThreads = new Integer(maxDeleteThreads).intValue();
		if (numDeleteThreads < 1 || numDeleteThreads > 300)
			throw new LCFException("Illegal value for the number of delete threads");
		numExpireThreads = new Integer(maxExpireThreads).intValue();
		if (numExpireThreads < 1 || numExpireThreads > 300)
			throw new LCFException("Illegal value for the number of expire threads");
		String lowWaterFactorString = getProperty(lowWaterFactorProperty);
		if (lowWaterFactorString == null)
			lowWaterFactorString = "5";
		lowWaterFactor = new Float(lowWaterFactorString).floatValue();
		if (lowWaterFactor < 1.0 || lowWaterFactor > 1000.0)
			throw new LCFException("Illegal value for the low water factor");
		String stuffAmtFactorString = getProperty(stuffAmtFactorProperty);
		if (stuffAmtFactorString == null)
			stuffAmtFactorString = "2";
		stuffAmtFactor = new Float(stuffAmtFactorString).floatValue();
		if (stuffAmtFactor < 0.1 || stuffAmtFactor > 1000.0)
			throw new LCFException("Illegal value for the stuffing amount factor");

		
		// Create the threads and objects.  This MUST be completed before there is any chance of "shutdownSystem" getting called.
		
		QueueTracker queueTracker = new QueueTracker();


		DocumentQueue documentQueue = new DocumentQueue();
		DocumentDeleteQueue documentDeleteQueue = new DocumentDeleteQueue();
		DocumentDeleteQueue expireQueue = new DocumentDeleteQueue();
		
		BlockingDocuments blockingDocuments = new BlockingDocuments();

		workerResetManager = new WorkerResetManager(documentQueue);
		docDeleteResetManager = new DocDeleteResetManager(documentDeleteQueue);

		jobStartThread = new JobStartThread();
		startupThread = new StartupThread(queueTracker);
		finisherThread = new FinisherThread();
		jobDeleteThread = new JobDeleteThread();
		stufferThread = new StufferThread(documentQueue,numWorkerThreads,workerResetManager,queueTracker,blockingDocuments,lowWaterFactor,stuffAmtFactor);
		expireStufferThread = new ExpireStufferThread(expireQueue,numExpireThreads,workerResetManager);
		setPriorityThread = new SetPriorityThread(queueTracker,numWorkerThreads,blockingDocuments);

		workerThreads = new WorkerThread[numWorkerThreads];
		int i = 0;
		while (i < numWorkerThreads)
		{
			workerThreads[i] = new WorkerThread(Integer.toString(i),documentQueue,workerResetManager,queueTracker);
			i++;
		}
		
		expireThreads = new ExpireThread[numExpireThreads];
		i = 0;
		while (i < numExpireThreads)
		{
			expireThreads[i] = new ExpireThread(Integer.toString(i),expireQueue,queueTracker,workerResetManager);
			i++;
		}
		
		deleteStufferThread = new DocumentDeleteStufferThread(documentDeleteQueue,numDeleteThreads,docDeleteResetManager);
		deleteThreads = new DocumentDeleteThread[numDeleteThreads];
		i = 0;
		while (i < numDeleteThreads)
		{
			deleteThreads[i] = new DocumentDeleteThread(Integer.toString(i),documentDeleteQueue,docDeleteResetManager);
			i++;
		}
		jobResetThread = new JobResetThread(queueTracker);
		seedingThread = new SeedingThread(queueTracker);
		idleCleanupThread = new IdleCleanupThread();
		
		initializationThread = new InitializationThread(queueTracker);
		// Start the initialization thread.  This does the initialization work and starts all the other threads when that's done.  It then exits.
		initializationThread.start();
	}
	
	protected static class InitializationThread extends Thread
	{
		
		protected QueueTracker queueTracker;

		public InitializationThread(QueueTracker queueTracker)
		{
			this.queueTracker = queueTracker;
		}
		
		public void run()
		{
			int i;
			
			// Initialize the database
			try
			{
				IThreadContext threadContext = ThreadContextFactory.make();

				// First, get a job manager
				IJobManager jobManager = JobManagerFactory.make(threadContext);
				IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(threadContext);

				Logging.threads.debug("Agents process starting initialization...");
				
				// Call the database to get it ready
				jobManager.prepareForStart();
				
				Logging.threads.debug("Agents process reprioritizing documents...");
				
				HashMap connectionMap = new HashMap();
				HashMap jobDescriptionMap = new HashMap();
				// Reprioritize all documents in the jobqueue, 1000 at a time
				long currentTime = System.currentTimeMillis();

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
					writeDocumentPriorities(threadContext,mgr,jobManager,docs,connectionMap,jobDescriptionMap,queueTracker,currentTime);
					
					Logging.threads.debug("Reprioritized "+Integer.toString(docs.length)+" not-yet-processed documents in "+new Long(System.currentTimeMillis()-startTime)+" ms");
				}

				Logging.threads.debug("Agents process initialization complete!");

				// Start all the threads
				jobStartThread.start();
				startupThread.start();
				finisherThread.start();
				jobDeleteThread.start();
				stufferThread.start();
				expireStufferThread.start();
				setPriorityThread.start();

				i = 0;
				while (i < numWorkerThreads)
				{
					workerThreads[i].start();
					i++;
				}

				i = 0;
				while (i < numExpireThreads)
				{
					expireThreads[i].start();
					i++;
				}

				deleteStufferThread.start();
				i = 0;
				while (i < numDeleteThreads)
				{
					deleteThreads[i].start();
					i++;
				}

				jobResetThread.start();
				seedingThread.start();
				idleCleanupThread.start();
				// exit!
			}
			catch (Throwable e)
			{
				// Severe error on initialization
				if (e instanceof LCFException)
				{
					// Deal with interrupted exception gracefully, because it means somebody is trying to shut us down before we got started.
					if (((LCFException)e).getErrorCode() == LCFException.INTERRUPTED)
						return;
				}
				System.err.println("metacarta-agents could not start - please contact MetaCarta Customer Support");
				Logging.threads.fatal("Startup initialization error tossed: "+e.getMessage(),e);
				System.exit(-300);
			}
		}
	}
	
	/** Stop the system.
	*/
	public static void stopSystem(IThreadContext threadContext)
		throws LCFException
	{

		while (initializationThread != null || jobDeleteThread != null || startupThread != null || jobStartThread != null || stufferThread != null ||
			 finisherThread != null || workerThreads != null || expireStufferThread != null | expireThreads != null ||
			 deleteStufferThread != null || deleteThreads != null ||
			 jobResetThread != null || seedingThread != null || idleCleanupThread != null || setPriorityThread != null)
		{
			// Send an interrupt to all threads that are still there.
			// In theory, this only needs to be done once.  In practice, I have seen cases where the thread loses track of the fact that it has been
			// interrupted (which may be a JVM bug - who knows?), but in any case there's no harm in doing it again.
			if (initializationThread != null)
			{
				initializationThread.interrupt();
			}
			if (setPriorityThread != null)
			{
				setPriorityThread.interrupt();
			}
			if (jobStartThread != null)
			{
				jobStartThread.interrupt();
			}
			if (jobDeleteThread != null)
			{
				jobDeleteThread.interrupt();
			}
			if (startupThread != null)
			{
				startupThread.interrupt();
			}
			if (stufferThread != null)
			{
				stufferThread.interrupt();
			}
			if (expireStufferThread != null)
			{
				expireStufferThread.interrupt();
			}
			if (finisherThread != null)
			{
				finisherThread.interrupt();
			}
			if (workerThreads != null)
			{
				int i = 0;
				while (i < workerThreads.length)
				{
					Thread workerThread = workerThreads[i++];
					if (workerThread != null)
						workerThread.interrupt();
				}
			}
			if (expireThreads != null)
			{
				int i = 0;
				while (i < expireThreads.length)
				{
					Thread expireThread = expireThreads[i++];
					if (expireThread != null)
						expireThread.interrupt();
				}
			}
			if (deleteStufferThread != null)
			{
				deleteStufferThread.interrupt();
			}
			if (deleteThreads != null)
			{
				int i = 0;
				while (i < deleteThreads.length)
				{
					Thread deleteThread = deleteThreads[i++];
					if (deleteThread != null)
						deleteThread.interrupt();
				}
			}
			if (jobResetThread != null)
			{
				jobResetThread.interrupt();
			}
			if (seedingThread != null)
			{
				seedingThread.interrupt();
			}
			if (idleCleanupThread != null)
			{
				idleCleanupThread.interrupt();
			}

			// Now, wait for all threads to die.
			try
			{
				LCF.sleep(1000L);
			}
			catch (InterruptedException e)
			{
			}
			
			// Check to see which died.
			if (initializationThread != null)
			{
				if (!initializationThread.isAlive())
					initializationThread = null;
			}
			if (setPriorityThread != null)
			{
				if (!setPriorityThread.isAlive())
					setPriorityThread = null;
			}
			if (jobDeleteThread != null)
			{
				if (!jobDeleteThread.isAlive())
					jobDeleteThread = null;
			}
			if (startupThread != null)
			{
				if (!startupThread.isAlive())
					startupThread = null;
			}
			if (jobStartThread != null)
			{
				if (!jobStartThread.isAlive())
					jobStartThread = null;
			}
			if (stufferThread != null)
			{
				if (!stufferThread.isAlive())
					stufferThread = null;
			}
			if (expireStufferThread != null)
			{
				if (!expireStufferThread.isAlive())
					expireStufferThread = null;
			}
			if (finisherThread != null)
			{
				if (!finisherThread.isAlive())
					finisherThread = null;
			}
			if (workerThreads != null)
			{
				int i = 0;
				boolean isAlive = false;
				while (i < workerThreads.length)
				{
					Thread workerThread = workerThreads[i];
					if (workerThread != null)
					{
						if (!workerThread.isAlive())
							workerThreads[i] = null;
						else
							isAlive = true;
					}
					i++;
				}
				if (!isAlive)
					workerThreads = null;
			}
			
			if (expireThreads != null)
			{
				int i = 0;
				boolean isAlive = false;
				while (i < expireThreads.length)
				{
					Thread expireThread = expireThreads[i];
					if (expireThread != null)
					{
						if (!expireThread.isAlive())
							expireThreads[i] = null;
						else
							isAlive = true;
					}
					i++;
				}
				if (!isAlive)
					expireThreads = null;
			}

			if (deleteStufferThread != null)
			{
				if (!deleteStufferThread.isAlive())
					deleteStufferThread = null;
			}
			if (deleteThreads != null)
			{
				int i = 0;
				boolean isAlive = false;
				while (i < deleteThreads.length)
				{
					Thread deleteThread = deleteThreads[i];
					if (deleteThread != null)
					{
						if (!deleteThread.isAlive())
							deleteThreads[i] = null;
						else
							isAlive = true;
					}
					i++;
				}
				if (!isAlive)
					deleteThreads = null;
			}
			if (jobResetThread != null)
			{
				if (!jobResetThread.isAlive())
					jobResetThread = null;
			}
			if (seedingThread != null)
			{
				if (!seedingThread.isAlive())
					seedingThread = null;
			}
			if (idleCleanupThread != null)
			{
				if (!idleCleanupThread.isAlive())
					idleCleanupThread = null;
			}
		}	

		// Threads are down; release connectors
		RepositoryConnectorFactory.closeAllConnectors(threadContext);
		numWorkerThreads = 0;
		numDeleteThreads = 0;
		numExpireThreads = 0;
	}

	/** Atomically export the crawler configuration */
	public static void exportConfiguration(IThreadContext threadContext, String exportFilename)
		throws LCFException
	{
		// The basic idea here is that we open a zip stream, into which we dump all the pertinent information in a transactionally-consistent manner.
		// First, we need a database handle...
		IDBInterface database = DBInterfaceFactory.make(threadContext,
			LCF.getMasterDatabaseName(),
			LCF.getMasterDatabaseUsername(),
			LCF.getMasterDatabasePassword());
		// Also create the following managers, which will handle the actual details of writing configuration data
		IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
		IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
		IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
		IJobManager jobManager = JobManagerFactory.make(threadContext);
		
		File outputFile = new File(exportFilename);
		
		// Create a zip output stream, which is what we will use as a mechanism for handling the output data
		try
		{
			OutputStream os = new FileOutputStream(outputFile);
			try
			{
				java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os);
				try
				{
					// Now, work within a transaction.
					database.beginTransaction();
					try
					{
						// At the outermost level, I've decided that the best structure is to have a zipentry for each
						// manager.  Each manager must manage its own data as a binary blob, including any format versioning information,
						// This guarantees flexibility for the future.
						
						// The zipentries must be written in an order that permits their proper restoration.  The "lowest level" is thus
						// written first, which yields the order: authority connections, repository connections, jobs
						java.util.zip.ZipEntry outputEntry = new java.util.zip.ZipEntry("outputs");
						zos.putNextEntry(outputEntry);
						outputManager.exportConfiguration(zos);
						zos.closeEntry();

						java.util.zip.ZipEntry authEntry = new java.util.zip.ZipEntry("authorities");
						zos.putNextEntry(authEntry);
						authManager.exportConfiguration(zos);
						zos.closeEntry();
						
						java.util.zip.ZipEntry connEntry = new java.util.zip.ZipEntry("connections");
						zos.putNextEntry(connEntry);
						connManager.exportConfiguration(zos);
						zos.closeEntry();

						java.util.zip.ZipEntry jobsEntry = new java.util.zip.ZipEntry("jobs");
						zos.putNextEntry(jobsEntry);
						jobManager.exportConfiguration(zos);
						zos.closeEntry();

						// All done
					}
					catch (LCFException e)
					{
						database.signalRollback();
						throw e;
					}
					catch (Error e)
					{
						database.signalRollback();
						throw e;
					}
					finally
					{
						database.endTransaction();
					}
				}
				finally
				{
					zos.close();
				}
			}
			finally
			{
				os.close();
			}
		}
		catch (java.io.IOException e)
		{
			// On error, delete any file we created
			outputFile.delete();
			// Convert I/O error into metacarta exception
			throw new LCFException("Error creating configuration file: "+e.getMessage(),e);
		}
	}
	
	/** Atomically import a crawler configuration */
	public static void importConfiguration(IThreadContext threadContext, String importFilename)
		throws LCFException
	{
		// First, we need a database handle...
		IDBInterface database = DBInterfaceFactory.make(threadContext,
			LCF.getMasterDatabaseName(),
			LCF.getMasterDatabaseUsername(),
			LCF.getMasterDatabasePassword());
		// Also create the following managers, which will handle the actual details of reading configuration data
		IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
		IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
		IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
		IJobManager jobManager = JobManagerFactory.make(threadContext);
		
		File inputFile = new File(importFilename);
		
		// Create a zip input stream, which is what we will use as a mechanism for handling the input data
		try
		{
			InputStream is = new FileInputStream(inputFile);
			try
			{
				java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
				try
				{
					// Now, work within a transaction.
					database.beginTransaction();
					try
					{
						// Process the entries in the order in which they were recorded.
						while (true)
						{
							java.util.zip.ZipEntry z = zis.getNextEntry();
							// Stop if there are no more entries
							if (z == null)
								break;
							// Get the name of the entry
							String name = z.getName();
							if (name.equals("outputs"))
								outputManager.importConfiguration(zis);
							else if (name.equals("authorities"))
								authManager.importConfiguration(zis);
							else if (name.equals("connections"))
								connManager.importConfiguration(zis);
							else if (name.equals("jobs"))
								jobManager.importConfiguration(zis);
							else
								throw new LCFException("Configuration file has an entry named '"+name+"' that I do not recognize");
							zis.closeEntry();
							
						}
						// All done!!
					}
					catch (LCFException e)
					{
						database.signalRollback();
						throw e;
					}
					catch (Error e)
					{
						database.signalRollback();
						throw e;
					}
					finally
					{
						database.endTransaction();
					}
				}
				finally
				{
					zis.close();
				}
			}
			finally
			{
				is.close();
			}
		}
		catch (java.io.IOException e)
		{
			// Convert I/O error into metacarta exception
			throw new LCFException("Error reading configuration file: "+e.getMessage(),e);
		}
	}
	

	/** Get the maximum number of worker threads.
	*/
	public static int getMaxWorkerThreads()
	{
		return numWorkerThreads;
	}

	/** Get the maximum number of delete threads.
	*/
	public static int getMaxDeleteThreads()
	{
		return numDeleteThreads;
	}

	/** Get the maximum number of expire threads.
	*/
	public static int getMaxExpireThreads()
	{
		return numExpireThreads;
	}
	
	/** Requeue documents due to carrydown.
	*/
	public static void requeueDocumentsDueToCarrydown(IJobManager jobManager, DocumentDescription[] requeueCandidates,
		IRepositoryConnector connector, IRepositoryConnection connection, QueueTracker queueTracker, long currentTime)
		throws LCFException
	{
		// A list of document descriptions from finishDocuments() above represents those documents that may need to be requeued, for the
		// reason that carrydown information for those documents has changed.  In order to requeue, we need to calculate document priorities, however.
		double[] docPriorities = new double[requeueCandidates.length];
		String[][] binNames = new String[requeueCandidates.length][];
		int q = 0;
		while (q < requeueCandidates.length)
		{
			DocumentDescription dd = requeueCandidates[q];
			String[] bins = calculateBins(connector,dd.getDocumentIdentifier());
			binNames[q] = bins;
			docPriorities[q] = queueTracker.calculatePriority(bins,connection);
			if (Logging.scheduling.isDebugEnabled())
			    Logging.scheduling.debug("Document '"+dd.getDocumentIdentifier()+" given priority "+new Double(docPriorities[q]).toString());
			q++;
		}
								
		// Now, requeue the documents with the new priorities
		boolean[] trackerNote = jobManager.carrydownChangeDocumentMultiple(requeueCandidates,currentTime,docPriorities);
								
		// Free the unused priorities.
		// Inform queuetracker about what we used and what we didn't
		q = 0;
		while (q < trackerNote.length)
		{
			if (trackerNote[q] == false)
			{
				String[] bins = binNames[q];
				queueTracker.notePriorityNotUsed(bins,connection,docPriorities[q]);
			}
			q++;
		}
	}

        /** Stuff colons so we can't have conflicts. */
        public static String colonStuff(String input)
        {
                StringBuffer sb = new StringBuffer();
                int i = 0;
                while (i < input.length())
                {
                        char x  = input.charAt(i++);
                        if (x == ':' || x == '\\')
                                sb.append('\\');
                        sb.append(x);
                }
                return sb.toString();
        }

        /** Create a global string */
        public static String createGlobalString(String simpleString)
        {
                return ":" + simpleString;
        }
        
        /** Create a connection-specific string */
        public static String createConnectionSpecificString(String connectionName, String simpleString)
        {
                return "C "+colonStuff(connectionName) + ":" + simpleString;
        }
        
        /** Create a job-specific string */
        public static String createJobSpecificString(Long jobID, String simpleString)
        {
                return "J "+jobID.toString() + ":" + simpleString;
        }
        
	/** Given a connector object and a document identifier, calculate its bins.
	*/
	public static String[] calculateBins(IRepositoryConnector connector, String documentIdentifier)
	{
		// Get the bins for the document identifier
		return connector.getBinNames(documentIdentifier);
	}

	public static void writeDocumentPriorities(IThreadContext threadContext, IRepositoryConnectionManager mgr, IJobManager jobManager, DocumentDescription[] descs, HashMap connectionMap, HashMap jobDescriptionMap, QueueTracker queueTracker, long currentTime)
		throws LCFException
	{
		if (Logging.scheduling.isDebugEnabled())
			Logging.scheduling.debug("Reprioritizing "+Integer.toString(descs.length)+" documents");


		double[] priorities = new double[descs.length];

		// Go through the documents and calculate the priorities
		int i = 0;
		while (i < descs.length)
		{
			DocumentDescription dd = descs[i];
			IJobDescription job = (IJobDescription)jobDescriptionMap.get(dd.getJobID());
			if (job == null)
			{
				job = jobManager.load(dd.getJobID(),true);
				jobDescriptionMap.put(dd.getJobID(),job);
			}
			String connectionName = job.getConnectionName();
			IRepositoryConnection connection = (IRepositoryConnection)connectionMap.get(connectionName);
			if (connection == null)
			{
				connection = mgr.load(connectionName);
				connectionMap.put(connectionName,connection);
			}

			String[] binNames;
			try
			{
				// Grab a connector handle
				IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
					connection.getClassName(),
					connection.getConfigParams(),
					connection.getMaxConnections());
				try
				{
					if (connector == null)
						binNames = new String[]{""};
					else
						// Get the bins for the document identifier
						binNames = connector.getBinNames(descs[i].getDocumentIdentifier());
				}
				finally
				{
					RepositoryConnectorFactory.release(connector);
				}
			}
			catch (LCFException e)
			{
				if (e.getErrorCode() == LCFException.REPOSITORY_CONNECTION_ERROR)
				{
					binNames = new String[]{""};
				}
				else
					throw e;
			}

			priorities[i] = queueTracker.calculatePriority(binNames,connection);
			if (Logging.scheduling.isDebugEnabled())
			    Logging.scheduling.debug("Document '"+dd.getDocumentIdentifier()+"' given priority "+new Double(priorities[i]).toString());

			i++;
		}

		// Now, write all the priorities we can.
		jobManager.writeDocumentPriorities(currentTime,descs,priorities);


	}

	/** Request permission from agent to delete an output connection.
	*@param threadContext is the thread context.
	*@param connName is the name of the output connection.
	*@return true if the connection is in use, false otherwise.
	*/
	public static boolean isOutputConnectionInUse(IThreadContext threadContext, String connName)
		throws LCFException
	{
		// Check with job manager.
		IJobManager jobManager = JobManagerFactory.make(threadContext);
		return jobManager.checkIfOutputReference(connName);
	}

	/** Note the deregistration of a set of output connections.
	*@param threadContext is the thread context.
	*@param connectionNames are the names of the connections being deregistered.
	*/
	public static void noteOutputConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
		throws LCFException
	{
		// Notify job manager
		IJobManager jobManager = JobManagerFactory.make(threadContext);
		jobManager.noteOutputConnectorDeregistration(connectionNames);
	}
		
	/** Note the registration of a set of output connections.
	*@param threadContext is the thread context.
	*@param connectionNames are the names of the connections being registered.
	*/
	public static void noteOutputConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
		throws LCFException
	{
		// Notify job manager
		IJobManager jobManager = JobManagerFactory.make(threadContext);
		jobManager.noteOutputConnectorRegistration(connectionNames);
	}

	/** Qualify output activity name.
	*@param outputActivityName is the name of the output activity.
	*@param outputConnectionName is the corresponding name of the output connection.
	*@return the qualified (global) activity name.
	*/
	public static String qualifyOutputActivityName(String outputActivityName, String outputConnectionName)
	{
		return outputActivityName+" ("+outputConnectionName+")";
	}
}

