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
package org.apache.manifoldcf.authorities.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;
import java.util.*;
import java.lang.reflect.*;

/** This thread performs actual user mapping operations.
*/
public class MappingThread extends Thread
{
  public static final String _rcsid = "@(#)$Id$";

  // Local data
  protected RequestQueue<MappingRequest> requestQueue;

  /** Constructor.
  */
  public MappingThread(String id, RequestQueue<MappingRequest> requestQueue)
    throws ManifoldCFException
  {
    super();
    this.requestQueue = requestQueue;
    setName("Mapping thread "+id);
    setDaemon(true);
  }

  public void run()
  {
    // Create a thread context object.
    IThreadContext threadContext = ThreadContextFactory.make();
    try
    {
      IMappingConnectorPool mappingConnectorPool = MappingConnectorPoolFactory.make(threadContext);
      // Loop
      while (true)
      {
        // Do another try/catch around everything in the loop
        try
        {
          if (Thread.currentThread().isInterrupted())
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

          // Wait for a request.
          MappingRequest theRequest = requestQueue.getRequest();

          // Try to fill the request before going back to sleep.
          if (Logging.authorityService.isDebugEnabled())
          {
            Logging.authorityService.debug(" Calling mapping connector class '"+theRequest.getMappingConnection().getClassName()+"'");
          }

          String outputUserID = null;
          Throwable exception = null;

          // Only try a mapping if we have a user to map...
          if (theRequest.getUserID() != null)
          {
            try
            {
              IMappingConnector connector = mappingConnectorPool.grab(theRequest.getMappingConnection());
              try
              {
                if (connector == null)
                  exception = new ManifoldCFException("Mapping connector "+theRequest.getMappingConnection().getClassName()+" is not registered.");
                else
                {
                  // Do the mapping
                  try
                  {
                    outputUserID = connector.mapUser(theRequest.getUserID());
                  }
                  catch (ManifoldCFException e)
                  {
                    if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                      throw e;
                    Logging.authorityService.warn("Mapping error: "+e.getMessage(),e);
                  }

                }
              }
              finally
              {
                mappingConnectorPool.release(theRequest.getMappingConnection(),connector);
              }
            }
            catch (ManifoldCFException e)
            {
              if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                throw e;
              Logging.authorityService.warn("Mapping connection exception: "+e.getMessage(),e);
              exception = e;
            }
            catch (Throwable e)
            {
              Logging.authorityService.warn("Mapping connection error: "+e.getMessage(),e);
              exception = e;
            }
          }

          // The request is complete
          theRequest.completeRequest(outputUserID, exception);

          // Repeat, and only go to sleep if there are no more requests.
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            break;

          // Log it, but keep the thread alive
          Logging.authorityService.error("Exception tossed: "+e.getMessage(),e);

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
        catch (Throwable e)
        {
          // A more severe error - but stay alive
          Logging.authorityService.fatal("Error tossed: "+e.getMessage(),e);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      // Severe error on initialization
      System.err.println("Authority service mapping thread could not start - shutting down");
      Logging.authorityService.fatal("MappingThread initialization error tossed: "+e.getMessage(),e);
      System.exit(-300);
    }
  }

}
