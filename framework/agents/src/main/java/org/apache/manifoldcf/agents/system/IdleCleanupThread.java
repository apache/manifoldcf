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
package org.apache.manifoldcf.agents.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import java.util.*;

/** This thread periodically calls the cleanup method in all connected output connectors.  The ostensible purpose
* is to allow the connectors to shutdown idle connections etc.
*/
public class IdleCleanupThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  // Local data
  /** Process ID */
  protected final String processID;

  /** Constructor.
  */
  public IdleCleanupThread(String processID)
    throws ManifoldCFException
  {
    super();
    this.processID = processID;
    setName("Agents idle cleanup thread");
    setDaemon(true);
  }

  public void run()
  {
    Logging.agents.debug("Start up agents idle cleanup thread");
    try
    {
      // Create a thread context object.
      IThreadContext threadContext = ThreadContextFactory.make();
      // Get the output connector pool handle
      IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
      // Get the transformation connector pool handle
      ITransformationConnectorPool transformationConnectorPool = TransformationConnectorPoolFactory.make(threadContext);
      
      /* For HSQLDB debugging...
      IDBInterface database = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
      */
      
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          /*
          System.out.println("+++++++++");
          IResultSet results = database.performQuery("SELECT * FROM information_schema.system_sessions",null,null,null);
          for (int i = 0; i < results.getRowCount(); i++)
          {
            IResultRow row = results.getRow(i);
            Iterator<String> iter = row.getColumns();
            while (iter.hasNext())
            {
              String columnName = iter.next();
              System.out.println(columnName+": "+row.getValue(columnName).toString());
            }
            System.out.println("--------");
          }
          System.out.println("++++++++++");
          */
          
          // Do the cleanup
          outputConnectorPool.pollAllConnectors();
          transformationConnectorPool.pollAllConnectors();
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
            Logging.agents.error("Idle cleanup thread aborting and restarting due to database connection reset: "+e.getMessage(),e);
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
          Logging.agents.error("Exception tossed: "+e.getMessage(),e);

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
          Logging.agents.fatal("Error tossed: "+e.getMessage(),e);
        }
      }
    }
    catch (Throwable e)
    {
      // Severe error on initialization
      System.err.println("agents process could not start - shutting down");
      Logging.agents.fatal("IdleCleanupThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }

  }

}
