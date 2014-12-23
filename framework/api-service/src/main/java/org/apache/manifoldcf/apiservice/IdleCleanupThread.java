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
package org.apache.manifoldcf.apiservice;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.util.*;
import java.lang.reflect.*;

/** This thread periodically calls the cleanup method in all connected repository connectors.  The ostensible purpose
* is to allow the connectors to shutdown idle connections etc.
*/
public class IdleCleanupThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  /** Constructor.
  */
  public IdleCleanupThread()
    throws ManifoldCFException
  {
    super();
    setName("Idle cleanup thread");
    setDaemon(true);
  }

  public void run()
  {
    Logging.root.debug("Start up idle cleanup thread");
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      
      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
      INotificationConnectorPool notificationConnectorPool = NotificationConnectorPoolFactory.make(threadContext);
      IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
      ITransformationConnectorPool transformationConnectorPool = TransformationConnectorPoolFactory.make(threadContext);
      IAuthorityConnectorPool authorityConnectorPool = AuthorityConnectorPoolFactory.make(threadContext);
      IMappingConnectorPool mappingConnectorPool = MappingConnectorPoolFactory.make(threadContext);

      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          // Do the cleanup
          repositoryConnectorPool.pollAllConnectors();
          notificationConnectorPool.pollAllConnectors();
          outputConnectorPool.pollAllConnectors();
          transformationConnectorPool.pollAllConnectors();
          authorityConnectorPool.pollAllConnectors();
          mappingConnectorPool.pollAllConnectors();
          // Poll all basic services
          ManifoldCF.pollAll(threadContext);
          
          // Sleep for the retry interval.
          ManifoldCF.sleep(5000L);
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          if (e.getErrorCode() == ManifoldCFException.DATABASE_CONNECTION_ERROR)
          {
            Logging.root.error("Idle cleanup thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
          Logging.root.error("Exception tossed: "+e.getMessage(),e);

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
          System.err.println("API service ran out of memory - shutting down");
          e.printStackTrace(System.err);
          System.exit(-200);
        }
        catch (Throwable e)
        {
          // A more severe error - but stay alive
          Logging.root.fatal("Error tossed: "+e.getMessage(),e);
        }
      }
    }
    catch (Throwable e)
    {
      // Severe error on initialization
      System.err.println("API service could not start - shutting down");
      Logging.root.fatal("IdleCleanupThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }

  }

}
