/* $Id: JobResetThread.java 991295 2010-08-31 19:12:14Z kwright $ */

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
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This class represents the thread that notices jobs that have completed their shutdown phase, and puts them in the
* "notify connector" state.
*/
public class JobResetThread extends Thread
{
  public static final String _rcsid = "@(#)$Id: JobResetThread.java 991295 2010-08-31 19:12:14Z kwright $";

  // Local data
  /** Process ID */
  protected final String processID;

  /** Constructor.
  */
  public JobResetThread(String processID)
    throws ManifoldCFException
  {
    super();
    setName("Job reset thread");
    setDaemon(true);
    this.processID = processID;
  }

  public void run()
  {
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      IJobManager jobManager = JobManagerFactory.make(threadContext);
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);

      INotificationConnectionManager notificationManager = NotificationConnectionManagerFactory.make(threadContext);
      INotificationConnectorPool notificationPool = NotificationConnectorPoolFactory.make(threadContext);
      
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // See if there are any completed jobs
          long currentTime = System.currentTimeMillis();
          
          List<IJobDescription> jobStops = new ArrayList<IJobDescription>();
          List<Integer> jobStopNotifications = new ArrayList<Integer>();
          jobManager.finishJobStops(currentTime,jobStops,jobStopNotifications);
          List<IJobDescription> jobResumes = new ArrayList<IJobDescription>();
          jobManager.finishJobResumes(currentTime,jobResumes);
          List<IJobDescription> jobCompletions = new ArrayList<IJobDescription>();
          jobManager.resetJobs(currentTime,jobCompletions);
          
          // If there were any job aborts, we must reprioritize all active documents, since we've done something
          // not predicted by the algorithm that assigned those priorities.  This is, of course, quite expensive,
          // but it cannot be helped (at least, I cannot find a way to avoid it).
          //
          if (jobStops.size() > 0)
          {
            Logging.threads.debug("Job reset thread reprioritizing documents...");

            ManifoldCF.resetAllDocumentPriorities(threadContext,processID);
            
            Logging.threads.debug("Job reset thread done reprioritizing documents.");

          }

          int k = 0;
          while (k < jobStops.size())
          {
            IJobDescription desc = jobStops.get(k);
            Integer notificationType = jobStopNotifications.get(k);
            connectionManager.recordHistory(desc.getConnectionName(),
              null,connectionManager.ACTIVITY_JOBSTOP,null,
              desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
            // As a courtesy, call all the notification connections (if any)
            doStopNotifications(desc,notificationType,notificationManager,notificationPool);
            k++;
          }

          k = 0;
          while (k < jobResumes.size())
          {
            IJobDescription desc = jobResumes.get(k++);
            connectionManager.recordHistory(desc.getConnectionName(),
              null,connectionManager.ACTIVITY_JOBCONTINUE,null,
              desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
          }

          k = 0;
          while (k < jobCompletions.size())
          {
            IJobDescription desc = jobCompletions.get(k++);
            connectionManager.recordHistory(desc.getConnectionName(),
              null,connectionManager.ACTIVITY_JOBEND,null,
              desc.getID().toString()+"("+desc.getDescription()+")",null,null,null);
            // As a courtesy, call all the notification connections (if any)
            doEndNotifications(desc,notificationManager,notificationPool);
          }
          
          ManifoldCF.sleep(10000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.threads.error("Job reset thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
            try
            {
              // Give the database a chance to catch up/wake up
              ManifoldCF.sleep(10000L);
            }
            catch (InterruptedException se)
            {
              break;
            }
            continue;
          }

          // Log it, but keep the thread alive
          Logging.threads.error("Exception tossed: "+e.getMessage(),e);

          if (e.getErrorCode() == ManifoldCFException.SETUP_ERROR)
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

  protected static void doStopNotifications(IJobDescription jobDescription, int notificationType, INotificationConnectionManager notificationManager,
    INotificationConnectorPool notificationPool)
    throws ManifoldCFException
  {
    for (int j = 0; j < jobDescription.countNotifications(); j++)
    {
      String notificationConnectionName = jobDescription.getNotificationConnectionName(j);
      try
      {
        INotificationConnection c = notificationManager.load(notificationConnectionName);
        if (c != null)
        {
          INotificationConnector connector = notificationPool.grab(c);
          if (connector != null)
          {
            try
            {
              switch (notificationType)
              {
              case IJobManager.STOP_ERRORABORT:
                connector.notifyOfJobStopErrorAbort(jobDescription.getNotificationSpecification(j));
                break;
              case IJobManager.STOP_MANUALABORT:
                connector.notifyOfJobStopManualAbort(jobDescription.getNotificationSpecification(j));
                break;
              case IJobManager.STOP_MANUALPAUSE:
                connector.notifyOfJobStopManualPause(jobDescription.getNotificationSpecification(j));
                break;
              case IJobManager.STOP_SCHEDULEPAUSE:
                connector.notifyOfJobStopSchedulePause(jobDescription.getNotificationSpecification(j));
                break;
              case IJobManager.STOP_RESTART:
                connector.notifyOfJobStopRestart(jobDescription.getNotificationSpecification(j));
                break;
              default:
                throw new RuntimeException("Unhandled notification type: "+notificationType);
              }
            }
            finally
            {
              notificationPool.release(c,connector);
            }
          }
        }
      }
      catch (ServiceInterruption e)
      {
        Logging.connectors.warn("Can't notify right now: "+e.getMessage(),e);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          throw e;
        Logging.connectors.warn("Error notifying: "+ e.getMessage(),e);
      }
    }
  }
  
  protected static void doEndNotifications(IJobDescription jobDescription, INotificationConnectionManager notificationManager,
    INotificationConnectorPool notificationPool)
    throws ManifoldCFException
  {
    for (int j = 0; j < jobDescription.countNotifications(); j++)
    {
      String notificationConnectionName = jobDescription.getNotificationConnectionName(j);
      try
      {
        INotificationConnection c = notificationManager.load(notificationConnectionName);
        if (c != null)
        {
          INotificationConnector connector = notificationPool.grab(c);
          if (connector != null)
          {
            try
            {
              connector.notifyOfJobEnd(jobDescription.getNotificationSpecification(j));
            }
            finally
            {
              notificationPool.release(c,connector);
            }
          }
        }
      }
      catch (ServiceInterruption e)
      {
        Logging.connectors.warn("Can't notify right now: "+e.getMessage(),e);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          throw e;
        Logging.connectors.warn("Error notifying: "+ e.getMessage(),e);
      }
    }
  }

}
