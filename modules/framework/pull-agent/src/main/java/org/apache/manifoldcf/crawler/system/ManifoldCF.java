/* $Id: ManifoldCF.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.io.*;
import java.util.*;

public class ManifoldCF extends org.apache.manifoldcf.agents.system.ManifoldCF
{
  public static final String _rcsid = "@(#)$Id: ManifoldCF.java 996524 2010-09-13 13:38:01Z kwright $";

  // Initialization flag.
  protected static boolean crawlerInitialized = false;
  
  // Thread objects.
  // These get filled in as threads are created.
  protected static InitializationThread initializationThread = null;
  protected static JobStartThread jobStartThread = null;
  protected static StufferThread stufferThread = null;
  protected static FinisherThread finisherThread = null;
  protected static JobNotificationThread notificationThread = null;
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

  protected static final String workerThreadCountProperty = "org.apache.manifoldcf.crawler.threads";
  protected static final String deleteThreadCountProperty = "org.apache.manifoldcf.crawler.deletethreads";
  protected static final String expireThreadCountProperty = "org.apache.manifoldcf.crawler.expirethreads";
  protected static final String lowWaterFactorProperty = "org.apache.manifoldcf.crawler.lowwaterfactor";
  protected static final String stuffAmtFactorProperty = "org.apache.manifoldcf.crawler.stuffamountfactor";

  /** This object is used to make sure the initialization sequence is atomic.  Shutdown cannot occur until the system is in a known state. */
  protected static Integer startupLock = new Integer(0);
  
  /** Initialize environment.
  */
  public static void initializeEnvironment()
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.authorities.system.ManifoldCF.initializeEnvironment();
      
      if (crawlerInitialized)
        return;
      
      org.apache.manifoldcf.agents.system.ManifoldCF.initializeEnvironment();
      Logging.initializeLoggers();
      Logging.setLogLevels();
      crawlerInitialized = true;
    }
  }


  /** Install all the crawler system tables.
  *@param threadcontext is the thread context.
  */
  public static void installSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
    IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
    IJobManager jobManager = JobManagerFactory.make(threadcontext);
    org.apache.manifoldcf.authorities.system.ManifoldCF.installSystemTables(threadcontext);
    repConnMgr.install();
    repCon.install();
    jobManager.install();
  }

  /** Uninstall all the crawler system tables.
  *@param threadcontext is the thread context.
  */
  public static void deinstallSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    ManifoldCFException se = null;

    IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
    IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
    IJobManager jobManager = JobManagerFactory.make(threadcontext);
    jobManager.deinstall();
    repCon.deinstall();
    repConnMgr.deinstall();
    org.apache.manifoldcf.authorities.system.ManifoldCF.deinstallSystemTables(threadcontext);
    if (se != null)
      throw se;
  }


  /** Start everything.
  */
  public static void startSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Logging.root.info("Starting up pull-agent...");
    synchronized (startupLock)
    {
      // Now, start all the threads
      String maxThreads = getProperty(workerThreadCountProperty);
      if (maxThreads == null)
        maxThreads = "100";
      numWorkerThreads = new Integer(maxThreads).intValue();
      if (numWorkerThreads < 1 || numWorkerThreads > 300)
        throw new ManifoldCFException("Illegal value for the number of worker threads");
      String maxDeleteThreads = getProperty(deleteThreadCountProperty);
      if (maxDeleteThreads == null)
        maxDeleteThreads = "10";
      String maxExpireThreads = getProperty(expireThreadCountProperty);
      if (maxExpireThreads == null)
        maxExpireThreads = "10";
      numDeleteThreads = new Integer(maxDeleteThreads).intValue();
      if (numDeleteThreads < 1 || numDeleteThreads > 300)
        throw new ManifoldCFException("Illegal value for the number of delete threads");
      numExpireThreads = new Integer(maxExpireThreads).intValue();
      if (numExpireThreads < 1 || numExpireThreads > 300)
        throw new ManifoldCFException("Illegal value for the number of expire threads");
      String lowWaterFactorString = getProperty(lowWaterFactorProperty);
      if (lowWaterFactorString == null)
        lowWaterFactorString = "5";
      lowWaterFactor = new Float(lowWaterFactorString).floatValue();
      if (lowWaterFactor < 1.0 || lowWaterFactor > 1000.0)
        throw new ManifoldCFException("Illegal value for the low water factor");
      String stuffAmtFactorString = getProperty(stuffAmtFactorProperty);
      if (stuffAmtFactorString == null)
        stuffAmtFactorString = "2";
      stuffAmtFactor = new Float(stuffAmtFactorString).floatValue();
      if (stuffAmtFactor < 0.1 || stuffAmtFactor > 1000.0)
        throw new ManifoldCFException("Illegal value for the stuffing amount factor");


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
      notificationThread = new JobNotificationThread();
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
    Logging.root.info("Pull-agent started");
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
        notificationThread.start();
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
        if (e instanceof ManifoldCFException)
        {
          // Deal with interrupted exception gracefully, because it means somebody is trying to shut us down before we got started.
          if (((ManifoldCFException)e).getErrorCode() == ManifoldCFException.INTERRUPTED)
            return;
        }
        System.err.println("agents process could not start - shutting down");
        Logging.threads.fatal("Startup initialization error tossed: "+e.getMessage(),e);
        System.exit(-300);
      }
    }
  }

  /** Stop the system.
  */
  public static void stopSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Logging.root.info("Shutting down pull-agent...");
    synchronized (startupLock)
    {
      while (initializationThread != null || jobDeleteThread != null || startupThread != null || jobStartThread != null || stufferThread != null ||
        finisherThread != null || notificationThread != null || workerThreads != null || expireStufferThread != null | expireThreads != null ||
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
        if (notificationThread != null)
        {
          notificationThread.interrupt();
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
          ManifoldCF.sleep(1000L);
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
        if (notificationThread != null)
        {
          if (!notificationThread.isAlive())
            notificationThread = null;
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
    Logging.root.info("Pull-agent successfully shut down");
  }

  /** Atomically export the crawler configuration */
  public static void exportConfiguration(IThreadContext threadContext, String exportFilename)
    throws ManifoldCFException
  {
    // The basic idea here is that we open a zip stream, into which we dump all the pertinent information in a transactionally-consistent manner.
    // First, we need a database handle...
    IDBInterface database = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
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
          catch (ManifoldCFException e)
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
      // Convert I/O error into lcf exception
      throw new ManifoldCFException("Error creating configuration file: "+e.getMessage(),e);
    }
  }

  /** Atomically import a crawler configuration */
  public static void importConfiguration(IThreadContext threadContext, String importFilename)
    throws ManifoldCFException
  {
    // First, we need a database handle...
    IDBInterface database = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
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
                throw new ManifoldCFException("Configuration file has an entry named '"+name+"' that I do not recognize");
              zis.closeEntry();

            }
            // All done!!
          }
          catch (ManifoldCFException e)
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
      // Convert I/O error into lcf exception
      throw new ManifoldCFException("Error reading configuration file: "+e.getMessage(),e);
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
    throws ManifoldCFException
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
    throws ManifoldCFException
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
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.REPOSITORY_CONNECTION_ERROR)
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
    throws ManifoldCFException
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
    throws ManifoldCFException
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
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteOutputConnectorRegistration(connectionNames);
  }

  /** Note the change in configuration of an output connection.
  *@param threadContext is the thread context.
  *@param connectionName is the output connection name.
  */
  public static void noteOutputConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteOutputConnectionChange(connectionName);
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
  
  // API support
  
  protected static final String API_JOBNODE = "job";
  protected static final String API_JOBSTATUSNODE = "jobstatus";
  protected static final String API_REPOSITORYCONNECTORNODE = "repositoryconnector";
  protected static final String API_OUTPUTCONNECTORNODE = "outputconnector";
  protected static final String API_AUTHORITYCONNECTORNODE = "authorityconnector";
  protected static final String API_REPOSITORYCONNECTIONNODE = "repositoryconnection";
  protected static final String API_OUTPUTCONNECTIONNODE = "outputconnection";
  protected static final String API_AUTHORITYCONNECTIONNODE = "authorityconnection";
  protected static final String API_CHECKRESULTNODE = "check_result";
  protected static final String API_JOBIDNODE = "job_id";
  protected static final String API_CONNECTIONNAMENODE = "connection_name";
  
  // Connector nodes
  protected static final String CONNECTORNODE_DESCRIPTION = "description";
  protected static final String CONNECTORNODE_CLASSNAME = "class_name";
  
  /** Decode path element.
  * Path elements in the API world cannot have "/" characters, or they become impossible to parse.  This method undoes
  * escaping that prevents "/" from appearing.
  */
  public static String decodeAPIPathElement(String startingPathElement)
    throws ManifoldCFException
  {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < startingPathElement.length())
    {
      char x = startingPathElement.charAt(i++);
      if (x == '.')
      {
        if (i == startingPathElement.length())
          throw new ManifoldCFException("Element decoding failed; illegal '.' character in '"+startingPathElement+"'");
        
        x = startingPathElement.charAt(i++);
        if (x == '.')
          sb.append(x);
        else if (x == '+')
          sb.append('/');
        else
          throw new ManifoldCFException("Element decoding failed; illegal post-'.' character in '"+startingPathElement+"'");
      }
      else
        sb.append(x);
    }
    return sb.toString();
  }
  
  /** Execute specified read command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@return true if the resource exists, false otherwise.
  */
  public static boolean executeReadCommand(IThreadContext tc, Configuration output, String path)
    throws ManifoldCFException
  {
    if (path.equals("jobs"))
    {
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        IJobDescription[] jobs = jobManager.getAllJobs();
        int i = 0;
        while (i < jobs.length)
        {
          ConfigurationNode jobNode = new ConfigurationNode(API_JOBNODE);
          formatJobDescription(jobNode,jobs[i++]);
          output.addChild(output.getChildCount(),jobNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        IJobDescription job = jobManager.load(jobID);
        if (job != null)
        {
          // Fill the return object with job information
          ConfigurationNode jobNode = new ConfigurationNode(API_JOBNODE);
          formatJobDescription(jobNode,job);
          output.addChild(output.getChildCount(),jobNode);
        }
        else
          return false;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("status/"))
    {
      int firstSeparator = "status/".length();
      int secondSeparator = path.indexOf("/",firstSeparator);
      if (secondSeparator == -1)
      {
        ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
        error.setValue("Need connection name.");
        output.addChild(output.getChildCount(),error);
        return false;
      }
      
      String connectionType = path.substring(firstSeparator,secondSeparator);
      String connectionName = decodeAPIPathElement(path.substring(secondSeparator+1));
      
      if (connectionType.equals("outputconnections"))
      {
        try
        {
          IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
          IOutputConnection connection = connectionManager.load(connectionName);
          if (connection == null)
          {
            ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
            error.setValue("Connection '"+connectionName+"' does not exist");
            output.addChild(output.getChildCount(),error);
            return false;
          }
          
          String results;
          // Grab a connection handle, and call the test method
          IOutputConnector connector = OutputConnectorFactory.grab(tc,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
          try
          {
            results = connector.check();
          }
          catch (ManifoldCFException e)
          {
            results = e.getMessage();
          }
          finally
          {
            OutputConnectorFactory.release(connector);
          }
          
          ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
          response.setValue(results);
          output.addChild(output.getChildCount(),response);
        }
        catch (ManifoldCFException e)
        {
          createErrorNode(output,e);
        }
      }
      else if (connectionType.equals("authorityconnections"))
      {
        try
        {
          IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
          IAuthorityConnection connection = connectionManager.load(connectionName);
          if (connection == null)
          {
            ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
            error.setValue("Connection '"+connectionName+"' does not exist");
            output.addChild(output.getChildCount(),error);
            return false;
          }
          
          String results;
          // Grab a connection handle, and call the test method
          IAuthorityConnector connector = AuthorityConnectorFactory.grab(tc,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
          try
          {
            results = connector.check();
          }
          catch (ManifoldCFException e)
          {
            results = e.getMessage();
          }
          finally
          {
            AuthorityConnectorFactory.release(connector);
          }
          
          ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
          response.setValue(results);
          output.addChild(output.getChildCount(),response);
        }
        catch (ManifoldCFException e)
        {
          createErrorNode(output,e);
        }
      }
      else if (connectionType.equals("repositoryconnections"))
      {
        try
        {
          IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
          IRepositoryConnection connection = connectionManager.load(connectionName);
          if (connection == null)
          {
            ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
            error.setValue("Connection '"+connectionName+"' does not exist");
            output.addChild(output.getChildCount(),error);
            return false;
          }
          
          String results;
          // Grab a connection handle, and call the test method
          IRepositoryConnector connector = RepositoryConnectorFactory.grab(tc,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
          try
          {
            results = connector.check();
          }
          catch (ManifoldCFException e)
          {
            results = e.getMessage();
          }
          finally
          {
            RepositoryConnectorFactory.release(connector);
          }
          
          ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
          response.setValue(results);
          output.addChild(output.getChildCount(),response);
        }
        catch (ManifoldCFException e)
        {
          createErrorNode(output,e);
        }
      }
      else
      {
        ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
        error.setValue("Unknown connection type '"+connectionType+"'.");
        output.addChild(output.getChildCount(),error);
        return false;
      }
    }
    else if (path.startsWith("info/"))
    {
      int firstSeparator = "info/".length();
      int secondSeparator = path.indexOf("/",firstSeparator);
      if (secondSeparator == -1)
      {
        ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
        error.setValue("Need connection type and connection name.");
        output.addChild(output.getChildCount(),error);
        return false;
      }

      int thirdSeparator = path.indexOf("/",secondSeparator+1);
      if (thirdSeparator == -1)
      {
        ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
        error.setValue("Need connection name.");
        output.addChild(output.getChildCount(),error);
        return false;
      }

      String connectionType = path.substring(firstSeparator,secondSeparator);
      String connectionName = decodeAPIPathElement(path.substring(secondSeparator+1,thirdSeparator));
      String command = path.substring(thirdSeparator+1);
      
      if (connectionType.equals("outputconnections"))
      {
        try
        {
          IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
          IOutputConnection connection = connectionManager.load(connectionName);
          if (connection == null)
          {
            ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
            error.setValue("Connection '"+connectionName+"' does not exist");
            output.addChild(output.getChildCount(),error);
            return false;
          }

          // Grab a connection handle, and call the test method
          IOutputConnector connector = OutputConnectorFactory.grab(tc,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
          try
          {
            return connector.requestInfo(output,command);
          }
          finally
          {
            OutputConnectorFactory.release(connector);
          }
        }
        catch (ManifoldCFException e)
        {
          createErrorNode(output,e);
        }
      }
      else if (connectionType.equals("repositoryconnections"))
      {
        try
        {
          IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
          IRepositoryConnection connection = connectionManager.load(connectionName);
          if (connection == null)
          {
            ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
            error.setValue("Connection '"+connectionName+"' does not exist");
            output.addChild(output.getChildCount(),error);
            return false;
          }

          // Grab a connection handle, and call the test method
          IRepositoryConnector connector = RepositoryConnectorFactory.grab(tc,connection.getClassName(),connection.getConfigParams(),connection.getMaxConnections());
          try
          {
            return connector.requestInfo(output,command);
          }
          finally
          {
            RepositoryConnectorFactory.release(connector);
          }
        }
        catch (ManifoldCFException e)
        {
          createErrorNode(output,e);
        }
      }
      else
      {
        ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
        error.setValue("Unknown connection type '"+connectionType+"'.");
        output.addChild(output.getChildCount(),error);
        return false;
      }
    }
    else if (path.equals("jobstatuses"))
    {
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        JobStatus[] jobStatuses = jobManager.getAllStatus();
        int i = 0;
        while (i < jobStatuses.length)
        {
          ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
          formatJobStatus(jobStatusNode,jobStatuses[i++]);
          output.addChild(output.getChildCount(),jobStatusNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("jobstatuses/"))
    {
      String jobID = path.substring("jobstatuses/".length());

      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        JobStatus status = jobManager.getStatus(new Long(jobID));
	if (status != null)
        {
          ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
          formatJobStatus(jobStatusNode,status);
          output.addChild(output.getChildCount(),jobStatusNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("outputconnections"))
    {
      try
      {
        IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
        IOutputConnection[] connections = connManager.getAllConnections();
        int i = 0;
        while (i < connections.length)
        {
          ConfigurationNode connectionNode = new ConfigurationNode(API_OUTPUTCONNECTIONNODE);
          formatOutputConnection(connectionNode,connections[i++]);
          output.addChild(output.getChildCount(),connectionNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      try
      {
        IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
        IOutputConnection connection = connectionManager.load(connectionName);
        if (connection != null)
        {
          // Fill the return object with job information
          ConfigurationNode connectionNode = new ConfigurationNode(API_OUTPUTCONNECTIONNODE);
          formatOutputConnection(connectionNode,connection);
          output.addChild(output.getChildCount(),connectionNode);
        }
        else
          return false;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("authorityconnections"))
    {
      try
      {
        IAuthorityConnectionManager connManager = AuthorityConnectionManagerFactory.make(tc);
        IAuthorityConnection[] connections = connManager.getAllConnections();
        int i = 0;
        while (i < connections.length)
        {
          ConfigurationNode connectionNode = new ConfigurationNode(API_AUTHORITYCONNECTIONNODE);
          formatAuthorityConnection(connectionNode,connections[i++]);
          output.addChild(output.getChildCount(),connectionNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));
      try
      {
        IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
        IAuthorityConnection connection = connectionManager.load(connectionName);
        if (connection != null)
        {
          // Fill the return object with job information
          ConfigurationNode connectionNode = new ConfigurationNode(API_AUTHORITYCONNECTIONNODE);
          formatAuthorityConnection(connectionNode,connection);
          output.addChild(output.getChildCount(),connectionNode);
        }
        else
          return false;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("repositoryconnections"))
    {
      try
      {
        IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
        IRepositoryConnection[] connections = connManager.getAllConnections();
        int i = 0;
        while (i < connections.length)
        {
          ConfigurationNode connectionNode = new ConfigurationNode(API_REPOSITORYCONNECTIONNODE);
          formatRepositoryConnection(connectionNode,connections[i++]);
          output.addChild(output.getChildCount(),connectionNode);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));
      try
      {
        IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
        IRepositoryConnection connection = connectionManager.load(connectionName);
        if (connection != null)
        {
          // Fill the return object with job information
          ConfigurationNode connectionNode = new ConfigurationNode(API_REPOSITORYCONNECTIONNODE);
          formatRepositoryConnection(connectionNode,connection);
          output.addChild(output.getChildCount(),connectionNode);
        }
        else
          return false;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("outputconnectors"))
    {
      // List registered output connectors
      try
      {
        IOutputConnectorManager manager = OutputConnectorManagerFactory.make(tc);
        IResultSet resultSet = manager.getConnectors();
        int j = 0;
        while (j < resultSet.getRowCount())
        {
          IResultRow row = resultSet.getRow(j++);
          ConfigurationNode child = new ConfigurationNode(API_OUTPUTCONNECTORNODE);
          String description = (String)row.getValue("description");
          String className = (String)row.getValue("classname");
          ConfigurationNode node;
          if (description != null)
          {
            node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
            node.setValue(description);
            child.addChild(child.getChildCount(),node);
          }
          node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
          node.setValue(className);
          child.addChild(child.getChildCount(),node);

          output.addChild(output.getChildCount(),child);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("authorityconnectors"))
    {
      // List registered authority connectors
      try
      {
        IAuthorityConnectorManager manager = AuthorityConnectorManagerFactory.make(tc);
        IResultSet resultSet = manager.getConnectors();
        int j = 0;
        while (j < resultSet.getRowCount())
        {
          IResultRow row = resultSet.getRow(j++);
          ConfigurationNode child = new ConfigurationNode(API_AUTHORITYCONNECTORNODE);
          String description = (String)row.getValue("description");
          String className = (String)row.getValue("classname");
          ConfigurationNode node;
          if (description != null)
          {
            node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
            node.setValue(description);
            child.addChild(child.getChildCount(),node);
          }
          node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
          node.setValue(className);
          child.addChild(child.getChildCount(),node);

          output.addChild(output.getChildCount(),child);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.equals("repositoryconnectors"))
    {
      // List registered repository connectors
      try
      {
        IConnectorManager manager = ConnectorManagerFactory.make(tc);
        IResultSet resultSet = manager.getConnectors();
        int j = 0;
        while (j < resultSet.getRowCount())
        {
          IResultRow row = resultSet.getRow(j++);
          ConfigurationNode child = new ConfigurationNode(API_REPOSITORYCONNECTORNODE);
          String description = (String)row.getValue("description");
          String className = (String)row.getValue("classname");
          ConfigurationNode node;
          if (description != null)
          {
            node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
            node.setValue(description);
            child.addChild(child.getChildCount(),node);
          }
          node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
          node.setValue(className);
          child.addChild(child.getChildCount(),node);

          output.addChild(output.getChildCount(),child);
        }
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }   
    else
    {
      ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
      error.setValue("Unrecognized resource.");
      output.addChild(output.getChildCount(),error);
      return false;
    }
    
    return true;
  }
  
  // Write result codes
  public static final int WRITERESULT_NOTFOUND = 0;
  public static final int WRITERESULT_FOUND = 1;
  public static final int WRITERESULT_CREATED = 2;
  
  /** Execute specified post command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@param input is the input object.
  *@return write result - either "not found", "found", or "created".
  */
  public static int executePostCommand(IThreadContext tc, Configuration output, String path, Configuration input)
    throws ManifoldCFException
  {
    if (path.equals("jobs"))
    {
      ConfigurationNode jobNode = findConfigurationNode(input,API_JOBNODE);
      if (jobNode == null)
        throw new ManifoldCFException("Input must have '"+API_JOBNODE+"' field");

      // Turn the configuration node into a JobDescription
      org.apache.manifoldcf.crawler.jobs.JobDescription job = new org.apache.manifoldcf.crawler.jobs.JobDescription();
      processJobDescription(job,jobNode);
      
      if (job.getID() != null)
        throw new ManifoldCFException("Input job cannot supply an ID field for create");
      
      try
      {
        Long jobID = new Long(IDFactory.make(tc));
        job.setID(jobID);
        job.setIsNew(true);
        
        // Save the job.
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.save(job);

        ConfigurationNode idNode = new ConfigurationNode(API_JOBIDNODE);
        idNode.setValue(jobID.toString());
        output.addChild(output.getChildCount(),idNode);
        
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else
    {
      ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
      error.setValue("Unrecognized resource.");
      output.addChild(output.getChildCount(),error);
      return WRITERESULT_NOTFOUND;
    }
    return WRITERESULT_FOUND;
  }

  /** Execute specified write command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@param input is the input object.
  *@return write result - either "not found", "found", or "created".
  */
  public static int executeWriteCommand(IThreadContext tc, Configuration output, String path, Configuration input)
    throws ManifoldCFException
  {
    if (path.startsWith("start/"))
    {
      Long jobID = new Long(path.substring("start/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.manualStart(jobID);
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("abort/"))
    {
      Long jobID = new Long(path.substring("abort/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.manualAbort(jobID);
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("restart/"))
    {
      Long jobID = new Long(path.substring("restart/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.manualAbortRestart(jobID);
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("pause/"))
    {
      Long jobID = new Long(path.substring("pause/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.pauseJob(jobID);
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("resume/"))
    {
      Long jobID = new Long(path.substring("resume/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.restartJob(jobID);
        return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      
      ConfigurationNode jobNode = findConfigurationNode(input,API_JOBNODE);
      if (jobNode == null)
        throw new ManifoldCFException("Input must have '"+API_JOBNODE+"' field");

      // Turn the configuration node into a JobDescription
      org.apache.manifoldcf.crawler.jobs.JobDescription job = new org.apache.manifoldcf.crawler.jobs.JobDescription();
      processJobDescription(job,jobNode);
      
      try
      {
        if (job.getID() == null)
        {
          job.setID(jobID);
        }
        else
        {
          if (!job.getID().equals(jobID))
            throw new ManifoldCFException("Job identifier must agree within object and within path");
        }
        
        job.setIsNew(false);
        
        // Save the job.
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.save(job);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      
      ConfigurationNode connectionNode = findConfigurationNode(input,API_OUTPUTCONNECTIONNODE);
      if (connectionNode == null)
        throw new ManifoldCFException("Input argument must have '"+API_OUTPUTCONNECTIONNODE+"' field");
      
      // Turn the configuration node into an OutputConnection
      org.apache.manifoldcf.agents.outputconnection.OutputConnection outputConnection = new org.apache.manifoldcf.agents.outputconnection.OutputConnection();
      processOutputConnection(outputConnection,connectionNode);
      
      if (outputConnection.getName() == null)
        outputConnection.setName(connectionName);
      else
      {
        if (!outputConnection.getName().equals(connectionName))
          throw new ManifoldCFException("Connection name in path and in object must agree");
      }
      
      try
      {
        // Save the connection.
        IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
        if (connectionManager.save(outputConnection))
          return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));

      ConfigurationNode connectionNode = findConfigurationNode(input,API_AUTHORITYCONNECTIONNODE);
      if (connectionNode == null)
        throw new ManifoldCFException("Input argument must have '"+API_AUTHORITYCONNECTIONNODE+"' field");
      
      // Turn the configuration node into an OutputConnection
      org.apache.manifoldcf.authorities.authority.AuthorityConnection authorityConnection = new org.apache.manifoldcf.authorities.authority.AuthorityConnection();
      processAuthorityConnection(authorityConnection,connectionNode);
      
      if (authorityConnection.getName() == null)
        authorityConnection.setName(connectionName);
      else
      {
        if (!authorityConnection.getName().equals(connectionName))
          throw new ManifoldCFException("Connection name in path and in object must agree");
      }
      
      try
      {
        // Save the connection.
        IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
        if (connectionManager.save(authorityConnection))
          return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));

      ConfigurationNode connectionNode = findConfigurationNode(input,API_REPOSITORYCONNECTIONNODE);
      if (connectionNode == null)
        throw new ManifoldCFException("Input argument must have '"+API_REPOSITORYCONNECTIONNODE+"' field");
      
      // Turn the configuration node into an OutputConnection
      org.apache.manifoldcf.crawler.repository.RepositoryConnection repositoryConnection = new org.apache.manifoldcf.crawler.repository.RepositoryConnection();
      processRepositoryConnection(repositoryConnection,connectionNode);
      
      if (repositoryConnection.getName() == null)
        repositoryConnection.setName(connectionName);
      else
      {
        if (!repositoryConnection.getName().equals(connectionName))
          throw new ManifoldCFException("Connection name in path and in object must agree");
      }

      try
      {
        // Save the connection.
        IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
        if (connectionManager.save(repositoryConnection))
          return WRITERESULT_CREATED;
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else
    {
      ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
      error.setValue("Unrecognized resource.");
      output.addChild(output.getChildCount(),error);
      return WRITERESULT_NOTFOUND;
    }
    return WRITERESULT_FOUND;
  }
  
  /** Execute specified delete command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@return true if the object exists, false otherwise.
  */
  public static boolean executeDeleteCommand(IThreadContext tc, Configuration output, String path)
    throws ManifoldCFException
  {
    if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      try
      {
        IJobManager jobManager = JobManagerFactory.make(tc);
        jobManager.deleteJob(jobID);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      try
      {
        IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
        connectionManager.delete(connectionName);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));
      try
      {
        IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
        connectionManager.delete(connectionName);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));
      try
      {
        IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
        connectionManager.delete(connectionName);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
      }
    }
    else
    {
      ConfigurationNode error = new ConfigurationNode(API_ERRORNODE);
      error.setValue("Unrecognized resource.");
      output.addChild(output.getChildCount(),error);
      return false;
    }
    return true;
  }
  
  // The following chunk of code is responsible for formatting a job description into a set of nodes, and for reading back a formatted job description.
  // This is needed to support the job-related API methods, above.
  
  // Job node types
  protected static final String JOBNODE_ID = "id";
  protected static final String JOBNODE_DESCRIPTION = "description";
  protected static final String JOBNODE_CONNECTIONNAME = "repository_connection";
  protected static final String JOBNODE_OUTPUTNAME = "output_connection";
  protected static final String JOBNODE_DOCUMENTSPECIFICATION = "document_specification";
  protected static final String JOBNODE_OUTPUTSPECIFICATION = "output_specification";
  protected static final String JOBNODE_STARTMODE = "start_mode";
  protected static final String JOBNODE_RUNMODE = "run_mode";
  protected static final String JOBNODE_HOPCOUNTMODE = "hopcount_mode";
  protected static final String JOBNODE_PRIORITY = "priority";
  protected static final String JOBNODE_RECRAWLINTERVAL = "recrawl_interval";
  protected static final String JOBNODE_EXPIRATIONINTERVAL = "expiration_interval";
  protected static final String JOBNODE_RESEEDINTERVAL = "reseed_interval";
  protected static final String JOBNODE_HOPCOUNT = "hopcount";
  protected static final String JOBNODE_SCHEDULE = "schedule";
  protected static final String JOBNODE_LINKTYPE = "link_type";
  protected static final String JOBNODE_COUNT = "count";
  protected static final String JOBNODE_TIMEZONE = "timezone";
  protected static final String JOBNODE_DURATION = "duration";
  protected static final String JOBNODE_DAYOFWEEK = "dayofweek";
  protected static final String JOBNODE_MONTHOFYEAR = "monthofyear";
  protected static final String JOBNODE_DAYOFMONTH = "dayofmonth";
  protected static final String JOBNODE_YEAR = "year";
  protected static final String JOBNODE_HOUROFDAY = "hourofday";
  protected static final String JOBNODE_MINUTESOFHOUR = "minutesofhour";
  protected static final String JOBNODE_ENUMVALUE = "value";
  
  /** Convert a node into a job description.
  *@param jobDescription is the job to be filled in.
  *@param jobNode is the configuration node corresponding to the whole job itself.
  */
  protected static void processJobDescription(org.apache.manifoldcf.crawler.jobs.JobDescription jobDescription, ConfigurationNode jobNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < jobNode.getChildCount())
    {
      ConfigurationNode child = jobNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(JOBNODE_ID))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Job id node requires a value");
        jobDescription.setID(new Long(child.getValue()));
      }
      else if (childType.equals(JOBNODE_DESCRIPTION))
      {
        jobDescription.setDescription(child.getValue());
      }
      else if (childType.equals(JOBNODE_CONNECTIONNAME))
      {
        jobDescription.setConnectionName(child.getValue());
      }
      else if (childType.equals(JOBNODE_OUTPUTNAME))
      {
        jobDescription.setOutputConnectionName(child.getValue());
      }
      else if (childType.equals(JOBNODE_DOCUMENTSPECIFICATION))
      {
        // Get the job's document specification, clear out the children, and copy new ones from the child.
        DocumentSpecification ds = jobDescription.getSpecification();
        ds.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          ds.addChild(ds.getChildCount(),new SpecificationNode(cn));
        }
      }
      else if (childType.equals(JOBNODE_OUTPUTSPECIFICATION))
      {
        // Get the job's output specification, clear out the children, and copy new ones from the child.
        OutputSpecification os = jobDescription.getOutputSpecification();
        os.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          os.addChild(os.getChildCount(),new SpecificationNode(cn));
        }
      }
      else if (childType.equals(JOBNODE_STARTMODE))
      {
        jobDescription.setStartMethod(mapToStartMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_RUNMODE))
      {
        jobDescription.setType(mapToRunMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_HOPCOUNTMODE))
      {
        jobDescription.setHopcountMode(mapToHopcountMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_PRIORITY))
      {
        try
        {
          jobDescription.setPriority(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage(),e);
        }
      }
      else if (childType.equals(JOBNODE_RECRAWLINTERVAL))
      {
        jobDescription.setInterval(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_EXPIRATIONINTERVAL))
      {
        jobDescription.setExpiration(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_RESEEDINTERVAL))
      {
        jobDescription.setReseedInterval(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_HOPCOUNT))
      {
        // Read the hopcount values
        String linkType = null;
        String hopCount = null;
        
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(q++);
          if (cn.getType().equals(JOBNODE_LINKTYPE))
            linkType = cn.getValue();
          else if (cn.getType().equals(JOBNODE_COUNT))
            hopCount = cn.getValue();
          else
            throw new ManifoldCFException("Found an unexpected node type: '"+cn.getType()+"'");
        }
        if (linkType == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_LINKTYPE+"'");
        if (hopCount == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_COUNT+"'");
        jobDescription.addHopCountFilter(linkType,new Long(hopCount));
      }
      else if (childType.equals(JOBNODE_SCHEDULE))
      {
        // Create a schedule record.
        String timezone = null;
        Long duration = null;
        EnumeratedValues dayOfWeek = null;
        EnumeratedValues monthOfYear = null;
        EnumeratedValues dayOfMonth = null;
        EnumeratedValues year = null;
        EnumeratedValues hourOfDay = null;
        EnumeratedValues minutesOfHour = null;
            
        // Now, walk through children of the schedule node.
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode scheduleField = child.findChild(q++);
          String fieldType = scheduleField.getType();
          if (fieldType.equals(JOBNODE_TIMEZONE))
          {
            timezone = scheduleField.getValue();
          }
          else if (fieldType.equals(JOBNODE_DURATION))
          {
            duration = new Long(scheduleField.getValue());
          }
          else if (fieldType.equals(JOBNODE_DAYOFWEEK))
          {
            dayOfWeek = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_MONTHOFYEAR))
          {
            monthOfYear = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_YEAR))
          {
            year = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_DAYOFMONTH))
          {
            dayOfMonth = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_HOUROFDAY))
          {
            hourOfDay = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_MINUTESOFHOUR))
          {
            minutesOfHour = processEnumeratedValues(scheduleField);
          }
          else
            throw new ManifoldCFException("Unrecognized field in schedule record: '"+fieldType+"'");
        }
        ScheduleRecord sr = new ScheduleRecord(dayOfWeek,monthOfYear,dayOfMonth,year,hourOfDay,minutesOfHour,timezone,duration);
        // Add the schedule record to the job.
        jobDescription.addScheduleRecord(sr);
      }
      else
        throw new ManifoldCFException("Unrecognized job field: '"+childType+"'");
    }
  }

  /** Convert a job description into a ConfigurationNode.
  *@param jobNode is the node to be filled in.
  *@param job is the job description.
  */
  protected static void formatJobDescription(ConfigurationNode jobNode, IJobDescription job)
  {
    // For each field of the job, add an appropriate child node, with value.
    ConfigurationNode child;
    int j;
    
    // id
    if (job.getID() != null)
    {
      child = new ConfigurationNode(JOBNODE_ID);
      child.setValue(job.getID().toString());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // description
    if (job.getDescription() != null)
    {
      child = new ConfigurationNode(JOBNODE_DESCRIPTION);
      child.setValue(job.getDescription());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // connection
    if (job.getConnectionName() != null)
    {
      child = new ConfigurationNode(JOBNODE_CONNECTIONNAME);
      child.setValue(job.getConnectionName());
      jobNode.addChild(jobNode.getChildCount(),child);
    }

    // output connection
    if (job.getOutputConnectionName() != null)
    {
      child = new ConfigurationNode(JOBNODE_OUTPUTNAME);
      child.setValue(job.getOutputConnectionName());
      jobNode.addChild(jobNode.getChildCount(),child);
    }

    // Document specification
    DocumentSpecification ds = job.getSpecification();
    child = new ConfigurationNode(JOBNODE_DOCUMENTSPECIFICATION);
    j = 0;
    while (j < ds.getChildCount())
    {
      ConfigurationNode cn = ds.getChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    jobNode.addChild(jobNode.getChildCount(),child);

    // Output specification
    OutputSpecification os = job.getOutputSpecification();
    child = new ConfigurationNode(JOBNODE_OUTPUTSPECIFICATION);
    j = 0;
    while (j < os.getChildCount())
    {
      ConfigurationNode cn = os.getChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    jobNode.addChild(jobNode.getChildCount(),child);

    // Start mode
    child = new ConfigurationNode(JOBNODE_STARTMODE);
    child.setValue(startModeMap(job.getStartMethod()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Run mode
    child = new ConfigurationNode(JOBNODE_RUNMODE);
    child.setValue(runModeMap(job.getType()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Hopcount mode
    child = new ConfigurationNode(JOBNODE_HOPCOUNTMODE);
    child.setValue(hopcountModeMap(job.getHopcountMode()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Priority
    child = new ConfigurationNode(JOBNODE_PRIORITY);
    child.setValue(Integer.toString(job.getPriority()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Recrawl interval
    if (job.getInterval() != null)
    {
      child = new ConfigurationNode(JOBNODE_RECRAWLINTERVAL);
      child.setValue(job.getInterval().toString());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // Expiration interval
    if (job.getExpiration() != null)
    {
      child = new ConfigurationNode(JOBNODE_EXPIRATIONINTERVAL);
      child.setValue(job.getExpiration().toString());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // Reseed interval
    if (job.getReseedInterval() != null)
    {
      child = new ConfigurationNode(JOBNODE_RESEEDINTERVAL);
      child.setValue(job.getReseedInterval().toString());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // Hopcount records
    Map filters = job.getHopCountFilters();
    Iterator iter = filters.keySet().iterator();
    while (iter.hasNext())
    {
      String linkType = (String)iter.next();
      Long hopCount = (Long)filters.get(linkType);
      child = new ConfigurationNode(JOBNODE_HOPCOUNT);
      ConfigurationNode cn;
      cn = new ConfigurationNode(JOBNODE_LINKTYPE);
      cn.setValue(linkType);
      child.addChild(child.getChildCount(),cn);
      cn = new ConfigurationNode(JOBNODE_COUNT);
      cn.setValue(hopCount.toString());
      child.addChild(child.getChildCount(),cn);
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // Schedule records
    child = new ConfigurationNode(JOBNODE_SCHEDULE);
    j = 0;
    while (j < job.getScheduleRecordCount())
    {
      ScheduleRecord sr = job.getScheduleRecord(j++);
      ConfigurationNode recordChild;
      
      // timezone
      if (sr.getTimezone() != null)
      {
        recordChild = new ConfigurationNode(JOBNODE_TIMEZONE);
        recordChild.setValue(sr.getTimezone());
        child.addChild(child.getChildCount(),recordChild);
      }

      // duration
      if (sr.getDuration() != null)
      {
        recordChild = new ConfigurationNode(JOBNODE_DURATION);
        recordChild.setValue(sr.getDuration().toString());
        child.addChild(child.getChildCount(),recordChild);
      }
      
      // Schedule specification values
      
      // day of week
      if (sr.getDayOfWeek() != null)
        formatEnumeratedValues(child,JOBNODE_DAYOFWEEK,sr.getDayOfWeek());
      if (sr.getMonthOfYear() != null)
        formatEnumeratedValues(child,JOBNODE_MONTHOFYEAR,sr.getMonthOfYear());
      if (sr.getDayOfMonth() != null)
        formatEnumeratedValues(child,JOBNODE_DAYOFMONTH,sr.getDayOfMonth());
      if (sr.getYear() != null)
        formatEnumeratedValues(child,JOBNODE_YEAR,sr.getYear());
      if (sr.getHourOfDay() != null)
        formatEnumeratedValues(child,JOBNODE_HOUROFDAY,sr.getHourOfDay());
      if (sr.getMinutesOfHour() != null)
        formatEnumeratedValues(child,JOBNODE_MINUTESOFHOUR,sr.getMinutesOfHour());
    }
    jobNode.addChild(jobNode.getChildCount(),child);
  }

  protected static void formatEnumeratedValues(ConfigurationNode recordNode, String childType, EnumeratedValues value)
  {
    ConfigurationNode child = new ConfigurationNode(childType);
    Iterator iter = value.getValues();
    while (iter.hasNext())
    {
      Integer theValue = (Integer)iter.next();
      ConfigurationNode valueNode = new ConfigurationNode(JOBNODE_ENUMVALUE);
      valueNode.setValue(theValue.toString());
      child.addChild(child.getChildCount(),valueNode);
    }
    recordNode.addChild(recordNode.getChildCount(),child);
  }
  
  protected static EnumeratedValues processEnumeratedValues(ConfigurationNode fieldNode)
    throws ManifoldCFException
  {
    ArrayList values = new ArrayList();
    int i = 0;
    while (i < fieldNode.getChildCount())
    {
      ConfigurationNode cn = fieldNode.findChild(i++);
      if (cn.getType().equals(JOBNODE_ENUMVALUE))
      {
        try
        {
          values.add(new Integer(cn.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error processing enumerated value node: "+e.getMessage(),e);
        }
      }
      else
        throw new ManifoldCFException("Error processing enumerated value nodes: Unrecognized node type '"+cn.getType()+"'");
    }
    return new EnumeratedValues(values);
  }
  
  protected static String presentInterval(Long interval)
  {
    if (interval == null)
      return "infinite";
    return interval.toString();
  }

  protected static Long interpretInterval(String interval)
    throws ManifoldCFException
  {
    if (interval == null || interval.equals("infinite"))
      return null;
    else
      return new Long(interval);
  }
  
  protected static String startModeMap(int startMethod)
  {
    switch (startMethod)
    {
    case IJobDescription.START_WINDOWBEGIN:
      return "schedule window start";
    case IJobDescription.START_WINDOWINSIDE:
      return "schedule window anytime";
    case IJobDescription.START_DISABLE:
      return "manual";
    default:
      return "unknown";
    }
  }

  protected static int mapToStartMode(String startMethod)
    throws ManifoldCFException
  {
    if (startMethod.equals("schedule window start"))
      return IJobDescription.START_WINDOWBEGIN;
    else if (startMethod.equals("schedule window anytime"))
      return IJobDescription.START_WINDOWINSIDE;
    else if (startMethod.equals("manual"))
      return IJobDescription.START_DISABLE;
    else
      throw new ManifoldCFException("Unrecognized start method: '"+startMethod+"'");
  }
  
  protected static String runModeMap(int type)
  {
    switch (type)
    {
    case IJobDescription.TYPE_CONTINUOUS:
      return "continuous";
    case IJobDescription.TYPE_SPECIFIED:
      return "scan once";
    default:
      return "unknown";
    }
  }

  protected static int mapToRunMode(String mode)
    throws ManifoldCFException
  {
    if (mode.equals("continuous"))
      return IJobDescription.TYPE_CONTINUOUS;
    else if (mode.equals("scan once"))
      return IJobDescription.TYPE_SPECIFIED;
    else
      throw new ManifoldCFException("Unrecognized run method: '"+mode+"'");
  }
  
  protected static String hopcountModeMap(int mode)
  {
    switch (mode)
    {
    case IJobDescription.HOPCOUNT_ACCURATE:
      return "accurate";
    case IJobDescription.HOPCOUNT_NODELETE:
      return "no delete";
    case IJobDescription.HOPCOUNT_NEVERDELETE:
      return "never delete";
    default:
      return "unknown";
    }
  }

  protected static int mapToHopcountMode(String mode)
    throws ManifoldCFException
  {
    if (mode.equals("accurate"))
      return IJobDescription.HOPCOUNT_ACCURATE;
    else if (mode.equals("no delete"))
      return IJobDescription.HOPCOUNT_NODELETE;
    else if (mode.equals("never delete"))
      return IJobDescription.HOPCOUNT_NEVERDELETE;
    else
      throw new ManifoldCFException("Unrecognized hopcount method: '"+mode+"'");
  }
  
  // End of job API support code.
  
  // The following chunk of code supports job statuses in the API.  Only a formatting method is required, since we never "save" a status.

  // Node types used to handle job statuses.
  protected static final String JOBSTATUSNODE_JOBID = "job_id";
  protected static final String JOBSTATUSNODE_STATUS = "status";
  protected static final String JOBSTATUSNODE_ERRORTEXT = "errortext";
  protected static final String JOBSTATUSNODE_STARTTIME = "start_time";
  protected static final String JOBSTATUSNODE_ENDTIME = "end_time";
  protected static final String JOBSTATUSNODE_DOCUMENTSINQUEUE = "documents_in_queue";
  protected static final String JOBSTATUSNODE_DOCUMENTSOUTSTANDING = "documents_outstanding";
  protected static final String JOBSTATUSNODE_DOCUMENTSPROCESSED = "documents_processed";
  
  /** Format a job status.
  */
  protected static void formatJobStatus(ConfigurationNode jobStatusNode, JobStatus jobStatus)
  {
    // For each field of the job, add an appropriate child node, with value.
    ConfigurationNode child;
    int j;
    
    // id
    child = new ConfigurationNode(JOBSTATUSNODE_JOBID);
    child.setValue(jobStatus.getJobID().toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // status
    child = new ConfigurationNode(JOBSTATUSNODE_STATUS);
    child.setValue(statusMap(jobStatus.getStatus()));
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // error text
    if (jobStatus.getErrorText() != null)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_ERRORTEXT);
      child.setValue(jobStatus.getErrorText());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }
    
    // start time
    if (jobStatus.getStartTime() != -1L)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_STARTTIME);
      child.setValue(new Long(jobStatus.getStartTime()).toString());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }
    
    // end time
    if (jobStatus.getEndTime() != -1L)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_ENDTIME);
      child.setValue(new Long(jobStatus.getEndTime()).toString());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }

    // documents in queue
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSINQUEUE);
    child.setValue(new Long(jobStatus.getDocumentsInQueue()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // documents outstanding
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSOUTSTANDING);
    child.setValue(new Long(jobStatus.getDocumentsOutstanding()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // documents processed
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSPROCESSED);
    child.setValue(new Long(jobStatus.getDocumentsProcessed()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

  }

  protected static String statusMap(int status)
  {
    switch (status)
    {
    case JobStatus.JOBSTATUS_NOTYETRUN:
      return "not yet run";
    case JobStatus.JOBSTATUS_RUNNING:
      return "running";
    case JobStatus.JOBSTATUS_PAUSED:
      return "paused";
    case JobStatus.JOBSTATUS_COMPLETED:
      return "done";
    case JobStatus.JOBSTATUS_WINDOWWAIT:
      return "waiting";
    case JobStatus.JOBSTATUS_STARTING:
      return "starting up";
    case JobStatus.JOBSTATUS_DESTRUCTING:
      return "cleaning up";
    case JobStatus.JOBSTATUS_ERROR:
      return "error";
    case JobStatus.JOBSTATUS_ABORTING:
      return "aborting";
    case JobStatus.JOBSTATUS_RESTARTING:
      return "restarting";
    case JobStatus.JOBSTATUS_RUNNING_UNINSTALLED:
      return "running no connector";
    case JobStatus.JOBSTATUS_JOBENDCLEANUP:
      return "terminating";
    case JobStatus.JOBSTATUS_JOBENDNOTIFICATION:
      return "notifying";
    default:
      return "unknown";
    }
  }

  // End of jobstatus API support.
  
  // Connection API
  
  protected static final String CONNECTIONNODE_NAME = "name";
  protected static final String CONNECTIONNODE_CLASSNAME = "class_name";
  protected static final String CONNECTIONNODE_MAXCONNECTIONS = "max_connections";
  protected static final String CONNECTIONNODE_DESCRIPTION = "description";
  protected static final String CONNECTIONNODE_CONFIGURATION = "configuration";
  protected static final String CONNECTIONNODE_ACLAUTHORITY = "acl_authority";
  protected static final String CONNECTIONNODE_THROTTLE = "throttle";
  protected static final String CONNECTIONNODE_MATCH = "match";
  protected static final String CONNECTIONNODE_MATCHDESCRIPTION = "match_description";
  protected static final String CONNECTIONNODE_RATE = "rate";
  
  // Output connection API support.
  
  /** Convert input hierarchy into an OutputConnection object.
  */
  protected static void processOutputConnection(org.apache.manifoldcf.agents.outputconnection.OutputConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized output connection field: '"+childType+"'");
    }
    if (connection.getName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_NAME+"'");
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }
  
  /** Format an output connection.
  */
  protected static void formatOutputConnection(ConfigurationNode connectionNode, IOutputConnection connection)
  {
    ConfigurationNode child;
    int j;
    
    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Authority connection API support
  
  /** Convert input hierarchy into an AuthorityConnection object.
  */
  protected static void processAuthorityConnection(org.apache.manifoldcf.authorities.authority.AuthorityConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized authority connection field: '"+childType+"'");
    }
    if (connection.getName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_NAME+"'");
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }
  
  /** Format an authority connection.
  */
  protected static void formatAuthorityConnection(ConfigurationNode connectionNode, IAuthorityConnection connection)
  {
    ConfigurationNode child;
    int j;
    
    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Repository connection API support methods
  
  /** Convert input hierarchy into a RepositoryConnection object.
  */
  protected static void processRepositoryConnection(org.apache.manifoldcf.crawler.repository.RepositoryConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else if (childType.equals(CONNECTIONNODE_ACLAUTHORITY))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection aclauthority node requires a value");
        connection.setACLAuthority(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_THROTTLE))
      {
        String match = null;
        String description = null;
        Float rate = null;
            
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode throttleField = child.findChild(q++);
          String fieldType = throttleField.getType();
          if (fieldType.equals(CONNECTIONNODE_MATCH))
          {
            match = throttleField.getValue();
          }
          else if (fieldType.equals(CONNECTIONNODE_MATCHDESCRIPTION))
          {
            description = throttleField.getValue();
          }
          else if (fieldType.equals(CONNECTIONNODE_RATE))
          {
            rate = new Float(throttleField.getValue());
          }
          else
            throw new ManifoldCFException("Unrecognized throttle field: '"+fieldType+"'");
        }
        if (match == null)
          throw new ManifoldCFException("Missing throttle field: '"+CONNECTIONNODE_MATCH+"'");
        if (rate == null)
          throw new ManifoldCFException("Missing throttle field: '"+CONNECTIONNODE_RATE+"'");
        connection.addThrottleValue(match,description,rate.floatValue());
      }
      else
        throw new ManifoldCFException("Unrecognized repository connection field: '"+childType+"'");
    }
    if (connection.getName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_NAME+"'");
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }
  
  /** Format a repository connection.
  */
  protected static void formatRepositoryConnection(ConfigurationNode connectionNode, IRepositoryConnection connection)
  {
    ConfigurationNode child;
    int j;
    
    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getACLAuthority() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_ACLAUTHORITY);
      child.setValue(connection.getACLAuthority());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    String[] throttles = connection.getThrottles();
    j = 0;
    while (j < throttles.length)
    {
      String match = throttles[j++];
      String description = connection.getThrottleDescription(match);
      float rate = connection.getThrottleValue(match);
      child = new ConfigurationNode(CONNECTIONNODE_THROTTLE);
      ConfigurationNode throttleChildNode;
      
      throttleChildNode = new ConfigurationNode(CONNECTIONNODE_MATCH);
      throttleChildNode.setValue(match);
      child.addChild(child.getChildCount(),throttleChildNode);
      
      if (description != null)
      {
        throttleChildNode = new ConfigurationNode(CONNECTIONNODE_MATCHDESCRIPTION);
        throttleChildNode.setValue(description);
        child.addChild(child.getChildCount(),throttleChildNode);
      }

      throttleChildNode = new ConfigurationNode(CONNECTIONNODE_RATE);
      throttleChildNode.setValue(new Float(rate).toString());
      child.addChild(child.getChildCount(),throttleChildNode);

      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
  }

  // End of connection API code
  
  
}

