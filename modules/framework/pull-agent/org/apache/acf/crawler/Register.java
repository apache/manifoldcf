/* $Id: Register.java 988101 2010-08-23 12:18:13Z kwright $ */

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
package org.apache.acf.crawler;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.*;

/**
 * Register a repository connector class
 */
public class Register extends TransactionalCrawlerInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: Register.java 988101 2010-08-23 12:18:13Z kwright $";

  private final String className;
  private final String description;

  private Register(String className, String description)
  {
    this.className = className;
    this.description = description;
  }

  protected void doExecute(IThreadContext tc) throws LCFException
  {
    IConnectorManager mgr = ConnectorManagerFactory.make(tc);
    IJobManager jobManager = JobManagerFactory.make(tc);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
    // First, register connector
    mgr.registerConnector(description,className);
    // Then, signal to all jobs that might depend on this connector that they can switch state
    // Find the connection names that come with this class
    String[] connectionNames = connManager.findConnectionsForConnector(className);
    // For each connection name, modify the jobs to note that the connector is now installed
    jobManager.noteConnectorRegistration(connectionNames);

    Logging.root.info("Successfully registered connector '"+className+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      System.err.println("Usage: Register <classname> <description>");
      System.exit(1);
    }

    String className = args[0];
    String description = args[1];

    try
    {
      Register register = new Register(className,description);
      register.execute();
      System.err.println("Successfully registered connector '"+className+"'");
    }
    catch (LCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
