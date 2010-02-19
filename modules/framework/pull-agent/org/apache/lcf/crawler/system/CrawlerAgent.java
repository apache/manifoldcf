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

/** This is the main agent class for the crawler.
*/
public class CrawlerAgent implements IAgent
{
  public static final String _rcsid = "@(#)$Id$";

  protected IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  */
  public CrawlerAgent(IThreadContext threadContext)
    throws LCFException
  {
    this.threadContext = threadContext;
  }

  /** Install agent.  This usually installs the agent's database tables etc.
  */
  public void install()
    throws LCFException
  {
    // Install the system tables for the crawler.
    LCF.initializeEnvironment();
    LCF.installSystemTables(threadContext);
  }

  /** Uninstall agent.  This must clean up everything the agent is responsible for.
  */
  public void deinstall()
    throws LCFException
  {
    LCF.initializeEnvironment();
    LCF.deinstallSystemTables(threadContext);
  }

  /** Start the agent.  This method should spin up the agent threads, and
  * then return.
  */
  public void startAgent()
    throws LCFException
  {
    LCF.initializeEnvironment();
    LCF.startSystem(threadContext);
  }

  /** Stop the agent.  This should shut down the agent threads.
  */
  public void stopAgent()
    throws LCFException
  {
    LCF.stopSystem(threadContext);
  }

  /** Request permission from agent to delete an output connection.
  *@param connName is the name of the output connection.
  *@return true if the connection is in use, false otherwise.
  */
  public boolean isOutputConnectionInUse(String connName)
    throws LCFException
  {
    return LCF.isOutputConnectionInUse(threadContext,connName);
  }

  /** Note the deregistration of a set of output connections.
  *@param connectionNames are the names of the connections being deregistered.
  */
  public void noteOutputConnectorDeregistration(String[] connectionNames)
    throws LCFException
  {
    LCF.noteOutputConnectorDeregistration(threadContext,connectionNames);
  }

  /** Note the registration of a set of output connections.
  *@param connectionNames are the names of the connections being registered.
  */
  public void noteOutputConnectorRegistration(String[] connectionNames)
    throws LCFException
  {
    LCF.noteOutputConnectorRegistration(threadContext,connectionNames);
  }

}

