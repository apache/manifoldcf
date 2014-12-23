/* $Id: CrawlerAgent.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This is the main agent class for the crawler.
*/
public class CrawlerAgent implements IAgent
{
  public static final String _rcsid = "@(#)$Id: CrawlerAgent.java 988245 2010-08-23 18:39:35Z kwright $";

  // Thread objects.
  // These get filled in as threads are created.
  protected JobStartThread jobStartThread = null;
  protected StufferThread stufferThread = null;
  protected FinisherThread finisherThread = null;
  protected JobNotificationThread notificationThread = null;
  protected StartupThread startupThread = null;
  protected StartDeleteThread startDeleteThread = null;
  protected JobDeleteThread jobDeleteThread = null;
  protected WorkerThread[] workerThreads = null;
  protected ExpireStufferThread expireStufferThread = null;
  protected ExpireThread[] expireThreads = null;
  protected DocumentDeleteStufferThread deleteStufferThread = null;
  protected DocumentDeleteThread[] deleteThreads = null;
  protected DocumentCleanupStufferThread cleanupStufferThread = null;
  protected DocumentCleanupThread[] cleanupThreads = null;
  protected JobResetThread jobResetThread = null;
  protected SeedingThread seedingThread = null;
  protected IdleCleanupThread idleCleanupThread = null;
  protected SetPriorityThread setPriorityThread = null;
  protected HistoryCleanupThread historyCleanupThread = null;
  protected AssessmentThread assessmentThread = null;
  
  // Reset managers
  /** Worker thread pool reset manager */
  protected WorkerResetManager workerResetManager = null;
  /** Delete thread pool reset manager */
  protected DocDeleteResetManager docDeleteResetManager = null;
  /** Cleanup thread pool reset manager */
  protected DocCleanupResetManager docCleanupResetManager = null;

  // Number of worker threads
  protected int numWorkerThreads = 0;
  // Number of delete threads
  protected int numDeleteThreads = 0;
  // Number of cleanup threads
  protected int numCleanupThreads = 0;
  // Number of expiration threads
  protected int numExpireThreads = 0;
  // Factor for low water level in queueing
  protected float lowWaterFactor = 5.0f;
  // Factor in amount to stuff
  protected float stuffAmtFactor = 0.5f;

  /** Process identifier for this agent */
  protected String processID = null;

  /** Constructor.
  *@param threadContext is the thread context.
  */
  public CrawlerAgent()
    throws ManifoldCFException
  {
  }

  /** Initialize agent environment.
  * This is called before any of the other operations are called, and is meant to insure that
  * the environment is properly initialized.
  */
  public void initialize(IThreadContext threadContext)
    throws ManifoldCFException
  {
    org.apache.manifoldcf.authorities.system.ManifoldCF.localInitialize(threadContext);
    org.apache.manifoldcf.crawler.system.ManifoldCF.localInitialize(threadContext);
  }
  
  /** Tear down agent environment.
  * This is called after all the other operations are completed, and is meant to allow
  * environment resources to be freed.
  */
  public void cleanUp(IThreadContext threadContext)
    throws ManifoldCFException
  {
    org.apache.manifoldcf.crawler.system.ManifoldCF.localCleanup(threadContext);
    org.apache.manifoldcf.authorities.system.ManifoldCF.localCleanup(threadContext);
  }

  /** Install agent.  This usually installs the agent's database tables etc.
  */
  @Override
  public void install(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Install the system tables for the crawler.
    ManifoldCF.installSystemTables(threadContext);
  }

  /** Uninstall agent.  This must clean up everything the agent is responsible for.
  */
  @Override
  public void deinstall(IThreadContext threadContext)
    throws ManifoldCFException
  {
    ManifoldCF.deinstallSystemTables(threadContext);
  }

  /** Called ONLY when no other active services of this kind are running.  Meant to be
  * used after the cluster has been down for an indeterminate period of time.
  */
  @Override
  public void clusterInit(IThreadContext threadContext)
    throws ManifoldCFException
  {
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.prepareForClusterStart();
  }

  /** Cleanup after ALL agents processes.
  * Call this method to clean up dangling persistent state when a cluster is just starting
  * to come up.  This method CANNOT be called when there are any active agents
  * processes at all.
  *@param processID is the current process ID.
  */
  @Override
  public void cleanUpAllAgentData(IThreadContext threadContext, String currentProcessID)
    throws ManifoldCFException
  {
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.cleanupProcessData();
    // What kind of reprioritization should be done here?
    // Answer: since we basically keep everything in the database now, the only kind of reprioritization we need
    // to take care of are dangling ones that won't get done because the process that was doing them went
    // away.  BUT: somebody may have blown away lock info, in which case we won't know anything at all.
    // So we do everything in that case.
    
    ManifoldCF.resetAllDocumentPriorities(threadContext,currentProcessID);

  }
  
  /** Cleanup after agents process.
  * Call this method to clean up dangling persistent state after agent has been stopped.
  * This method CANNOT be called when the agent is active, but it can
  * be called at any time and by any process in order to guarantee that a terminated
  * agent does not block other agents from completing their tasks.
  *@param currentProcessID is the current process ID.
  *@param cleanupProcessID is the process ID of the agent to clean up after.
  */
  @Override
  public void cleanUpAgentData(IThreadContext threadContext, String currentProcessID, String cleanupProcessID)
    throws ManifoldCFException
  {
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.cleanupProcessData(cleanupProcessID);
    
    // If one agents process was starting a reprioritization, it could have started the reprioritization sequence, but
    // failed to complete it.  If so, we may need to reset/complete the reprioritization sequence, which is defined as:
    // - Resetting prioritization parameters
    // - Removing all existing document priorities
    // These must go together in order for the reset to be correct.
    
    IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);
    String reproID = rt.isSpecifiedProcessReprioritizing(cleanupProcessID);
    if (reproID != null)
    {
      // We have to take over the prioritization for the process, which apparently died
      // in the middle.
      
      jobManager.clearAllDocumentPriorities();
      
      /*
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);

      // Reprioritize all documents in the jobqueue, 1000 at a time

      Map<String,IRepositoryConnection> connectionMap = new HashMap<String,IRepositoryConnection>();
      Map<Long,IJobDescription> jobDescriptionMap = new HashMap<Long,IJobDescription>();
      
      // Do the 'not yet processed' documents only.  Documents that are queued for reprocessing will be assigned
      // new priorities.  Already processed documents won't.  This guarantees that our bins are appropriate for current thread
      // activity.
      // In order for this to be the correct functionality, ALL reseeding and requeuing operations MUST reset the associated document
      // priorities.
      // ??? -- start the process of reprioritization ONLY; don't do the whole thing.
      while (true)
      {
        long startTime = System.currentTimeMillis();

        Long currentTimeValue = rt.checkReprioritizationInProgress();
        if (currentTimeValue == null)
        {
          // Some other process or thread superceded us.
          return;
        }
        long updateTime = currentTimeValue.longValue();
        
        DocumentDescription[] docs = jobManager.getNextNotYetProcessedReprioritizationDocuments(10000);
        if (docs.length == 0)
          break;

        // Calculate new priorities for all these documents
        ManifoldCF.writeDocumentPriorities(threadContext,docs,connectionMap,jobDescriptionMap);

        Logging.threads.debug("Reprioritized "+Integer.toString(docs.length)+" not-yet-processed documents in "+new Long(System.currentTimeMillis()-startTime)+" ms");
      }
      */
      
      rt.doneReprioritization(reproID);
    }
  }

  /** Start the agent.  This method should spin up the agent threads, and
  * then return.
  */
  @Override
  public void startAgent(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
    this.processID = processID;
    startSystem(threadContext);
  }

  /** Stop the agent.  This should shut down the agent threads etc.
  */
  @Override
  public void stopAgent(IThreadContext threadContext)
    throws ManifoldCFException
  {
    stopSystem(threadContext);
  }

  /** Request permission from agent to delete an output connection.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  @Override
  public boolean isOutputConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException
  {
    // Check with job manager.
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    return jobManager.checkIfOutputReference(connName);
  }

  /** Note the deregistration of a set of output connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  @Override
  public void noteOutputConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteOutputConnectorDeregistration(connectionNames);
  }

  /** Note the registration of a set of output connections.
  *@param connectionNames are the names of the connections being registered.
  */
  @Override
  public void noteOutputConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteOutputConnectorRegistration(connectionNames);
  }

  /** Note a change in configuration for an output connection.
  *@param connectionName is the name of the connections being changed.
  */
  @Override
  public void noteOutputConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteOutputConnectionChange(connectionName);
  }

  /** Request permission from agent to delete a transformation connection.
  *@param connName is the name of the transformation connection.
  *@return true if the connection is in use, false otherwise.
  */
  @Override
  public boolean isTransformationConnectionInUse(IThreadContext threadContext, String connName)
    throws ManifoldCFException
  {
    // Check with job manager.
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    return jobManager.checkIfTransformationReference(connName);
  }

  /** Note the deregistration of a set of transformation connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  @Override
  public void noteTransformationConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteTransformationConnectorDeregistration(connectionNames);
  }

  /** Note the registration of a set of transformation connections.
  *@param connectionNames are the names of the connections being registered.
  */
  @Override
  public void noteTransformationConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteTransformationConnectorRegistration(connectionNames);
  }

  /** Note a change in configuration for a transformation connection.
  *@param connectionName is the name of the connection being changed.
  */
  @Override
  public void noteTransformationConnectionChange(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    // Notify job manager
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    jobManager.noteTransformationConnectionChange(connectionName);
  }

  /** Start everything.
  */
  public void startSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Logging.root.info("Starting up pull-agent...");
    // Now, start all the threads
    numWorkerThreads = ManifoldCF.getMaxWorkerThreads(threadContext);
    if (numWorkerThreads < 1 || numWorkerThreads > 300)
      throw new ManifoldCFException("Illegal value for the number of worker threads", ManifoldCFException.SETUP_ERROR);
    numDeleteThreads = ManifoldCF.getMaxDeleteThreads(threadContext);
    numCleanupThreads = ManifoldCF.getMaxCleanupThreads(threadContext);
    numExpireThreads = ManifoldCF.getMaxExpireThreads(threadContext);
    if (numDeleteThreads < 1 || numDeleteThreads > 300)
      throw new ManifoldCFException("Illegal value for the number of delete threads", ManifoldCFException.SETUP_ERROR);
    if (numCleanupThreads < 1 || numCleanupThreads > 300)
      throw new ManifoldCFException("Illegal value for the number of cleanup threads", ManifoldCFException.SETUP_ERROR);
    if (numExpireThreads < 1 || numExpireThreads > 300)
      throw new ManifoldCFException("Illegal value for the number of expire threads", ManifoldCFException.SETUP_ERROR);
    lowWaterFactor = (float)LockManagerFactory.getDoubleProperty(threadContext,ManifoldCF.lowWaterFactorProperty,5.0);
    if (lowWaterFactor < 1.0 || lowWaterFactor > 1000.0)
      throw new ManifoldCFException("Illegal value for the low water factor", ManifoldCFException.SETUP_ERROR);
    stuffAmtFactor = (float)LockManagerFactory.getDoubleProperty(threadContext,ManifoldCF.stuffAmtFactorProperty,2.0);
    if (stuffAmtFactor < 0.1 || stuffAmtFactor > 1000.0)
      throw new ManifoldCFException("Illegal value for the stuffing amount factor", ManifoldCFException.SETUP_ERROR);


    // Create the threads and objects.  This MUST be completed before there is any chance of "shutdownSystem" getting called.

    QueueTracker queueTracker = new QueueTracker();


    DocumentQueue documentQueue = new DocumentQueue();
    DocumentDeleteQueue documentDeleteQueue = new DocumentDeleteQueue();
    DocumentCleanupQueue documentCleanupQueue = new DocumentCleanupQueue();
    DocumentCleanupQueue expireQueue = new DocumentCleanupQueue();

    BlockingDocuments blockingDocuments = new BlockingDocuments();

    workerResetManager = new WorkerResetManager(documentQueue,expireQueue,processID);
    docDeleteResetManager = new DocDeleteResetManager(documentDeleteQueue,processID);
    docCleanupResetManager = new DocCleanupResetManager(documentCleanupQueue,processID);

    jobStartThread = new JobStartThread(processID);
    startupThread = new StartupThread(new StartupResetManager(processID),processID);
    startDeleteThread = new StartDeleteThread(new DeleteStartupResetManager(processID),processID);
    finisherThread = new FinisherThread(processID);
    notificationThread = new JobNotificationThread(new NotificationResetManager(processID),processID);
    jobDeleteThread = new JobDeleteThread(processID);
    stufferThread = new StufferThread(documentQueue,numWorkerThreads,workerResetManager,queueTracker,blockingDocuments,lowWaterFactor,stuffAmtFactor,processID);
    expireStufferThread = new ExpireStufferThread(expireQueue,numExpireThreads,workerResetManager,processID);
    setPriorityThread = new SetPriorityThread(numWorkerThreads,blockingDocuments,processID);
    historyCleanupThread = new HistoryCleanupThread(processID);

    workerThreads = new WorkerThread[numWorkerThreads];
    int i = 0;
    while (i < numWorkerThreads)
    {
      workerThreads[i] = new WorkerThread(Integer.toString(i),documentQueue,workerResetManager,queueTracker,processID);
      i++;
    }

    expireThreads = new ExpireThread[numExpireThreads];
    i = 0;
    while (i < numExpireThreads)
    {
      expireThreads[i] = new ExpireThread(Integer.toString(i),expireQueue,workerResetManager,processID);
      i++;
    }

    deleteStufferThread = new DocumentDeleteStufferThread(documentDeleteQueue,numDeleteThreads,docDeleteResetManager,processID);
    deleteThreads = new DocumentDeleteThread[numDeleteThreads];
    i = 0;
    while (i < numDeleteThreads)
    {
      deleteThreads[i] = new DocumentDeleteThread(Integer.toString(i),documentDeleteQueue,docDeleteResetManager,processID);
      i++;
    }
      
    cleanupStufferThread = new DocumentCleanupStufferThread(documentCleanupQueue,numCleanupThreads,docCleanupResetManager,processID);
    cleanupThreads = new DocumentCleanupThread[numCleanupThreads];
    i = 0;
    while (i < numCleanupThreads)
    {
      cleanupThreads[i] = new DocumentCleanupThread(Integer.toString(i),documentCleanupQueue,docCleanupResetManager,processID);
      i++;
    }

    jobResetThread = new JobResetThread(processID);
    seedingThread = new SeedingThread(new SeedingResetManager(processID),processID);
    idleCleanupThread = new IdleCleanupThread(processID);
    assessmentThread = new AssessmentThread(processID);

    // Start all the threads
    jobStartThread.start();
    startupThread.start();
    startDeleteThread.start();
    finisherThread.start();
    notificationThread.start();
    jobDeleteThread.start();
    stufferThread.start();
    expireStufferThread.start();
    setPriorityThread.start();
    historyCleanupThread.start();

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

    cleanupStufferThread.start();
    i = 0;
    while (i < numCleanupThreads)
    {
      cleanupThreads[i].start();
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
    assessmentThread.start();

    Logging.root.info("Pull-agent started");
  }

  /** Stop the system.
  */
  public void stopSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Logging.root.info("Shutting down pull-agent...");
    while (jobDeleteThread != null || startupThread != null || startDeleteThread != null ||
      jobStartThread != null || stufferThread != null ||
      finisherThread != null || notificationThread != null || workerThreads != null || expireStufferThread != null || expireThreads != null ||
      deleteStufferThread != null || deleteThreads != null ||
      cleanupStufferThread != null || cleanupThreads != null ||
      jobResetThread != null || seedingThread != null || idleCleanupThread != null || assessmentThread != null || setPriorityThread != null || historyCleanupThread != null)
    {
      // Send an interrupt to all threads that are still there.
      // In theory, this only needs to be done once.  In practice, I have seen cases where the thread loses track of the fact that it has been
      // interrupted (which may be a JVM bug - who knows?), but in any case there's no harm in doing it again.
      if (historyCleanupThread != null)
      {
        historyCleanupThread.interrupt();
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
      if (startDeleteThread != null)
      {
        startDeleteThread.interrupt();
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
      if (cleanupStufferThread != null)
      {
        cleanupStufferThread.interrupt();
      }
      if (cleanupThreads != null)
      {
        int i = 0;
        while (i < cleanupThreads.length)
        {
          Thread cleanupThread = cleanupThreads[i++];
          if (cleanupThread != null)
            cleanupThread.interrupt();
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
      if (assessmentThread != null)
      {
        assessmentThread.interrupt();
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
      if (historyCleanupThread != null)
      {
        if (!historyCleanupThread.isAlive())
          historyCleanupThread = null;
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
      if (startDeleteThread != null)
      {
        if (!startDeleteThread.isAlive())
          startDeleteThread = null;
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
      
      if (cleanupStufferThread != null)
      {
        if (!cleanupStufferThread.isAlive())
          cleanupStufferThread = null;
      }
      if (cleanupThreads != null)
      {
        int i = 0;
        boolean isAlive = false;
        while (i < cleanupThreads.length)
        {
          Thread cleanupThread = cleanupThreads[i];
          if (cleanupThread != null)
          {
            if (!cleanupThread.isAlive())
              cleanupThreads[i] = null;
            else
              isAlive = true;
          }
          i++;
        }
        if (!isAlive)
          cleanupThreads = null;
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
      if (assessmentThread != null)
      {
        if (!assessmentThread.isAlive())
          assessmentThread = null;
      }
    }

    // Threads are down; release connectors
    RepositoryConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
    NotificationConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
    numWorkerThreads = 0;
    numDeleteThreads = 0;
    numExpireThreads = 0;
    Logging.root.info("Pull-agent successfully shut down");
  }
  

}

