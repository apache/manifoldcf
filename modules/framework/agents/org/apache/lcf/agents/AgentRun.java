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
package org.apache.lcf.agents;

import java.io.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.*;
import java.lang.reflect.*;

public class AgentRun
{
  public static final String _rcsid = "@(#)$Id$";

  public static final String agentShutdownSignal = "_AGENTRUN_";
  
  private AgentRun()
  {
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: AgentRun");
      System.exit(1);
    }

    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();

    try
    {
      ILockManager lockManager = LockManagerFactory.make(tc);
      // Clear the agents shutdown signal.
      lockManager.clearGlobalFlag(agentShutdownSignal);
      System.err.println("Running...");
      while (true)
      {
        // Any shutdown signal yet?
        if (lockManager.checkGlobalFlag(agentShutdownSignal))
          break;
          
        // Start whatever agents need to be started
        LCF.startAgents(tc);

        try
        {
          LCF.sleep(5000);
        }
        catch (InterruptedException e)
        {
          break;
        }
      }
      System.err.println("Shutting down...");
    }
    catch (LCFException e)
    {
      Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace();
      System.exit(1);
    }
  }




}
