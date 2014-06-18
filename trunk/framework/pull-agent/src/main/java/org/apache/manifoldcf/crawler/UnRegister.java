/* $Id: UnRegister.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;

/**
 * Un-register a repository connector class
 */
public class UnRegister extends TransactionalCrawlerInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: UnRegister.java 988245 2010-08-23 18:39:35Z kwright $";

  private final String className;

  public UnRegister(String className)
  {
    this.className = className;
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    IConnectorManager mgr = ConnectorManagerFactory.make(tc);
    IJobManager jobManager = JobManagerFactory.make(tc);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
    // Find the connection names that come with this class
    String[] connectionNames = connManager.findConnectionsForConnector(className);
    // For each connection name, modify the jobs to note that the connector is no longer installed
    jobManager.noteConnectorDeregistration(connectionNames);
    // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
    mgr.unregisterConnector(className);
    Logging.root.info("Successfully unregistered connector '"+className+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: UnRegister <classname>");
      System.exit(1);
    }

    String className = args[0];
    try
    {
      UnRegister unRegister = new UnRegister(className);
      unRegister.execute();
      System.err.println("Successfully unregistered connector '"+className+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
