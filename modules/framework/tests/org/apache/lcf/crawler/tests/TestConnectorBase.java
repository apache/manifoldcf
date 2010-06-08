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
package org.apache.lcf.crawler.tests;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.LCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the agents framework. */
public abstract class TestConnectorBase extends org.apache.lcf.crawler.tests.TestBase
{
  
  protected abstract String getConnectorClass();
  protected abstract String getConnectorName();
  
  @Before
  public void setUp()
    throws Exception
  {
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      System.out.println("Warning: Preclean failed: "+e.getMessage());
    }
    localSetUp();
  }

  protected void localSetUp()
    throws Exception
  {
    String connectorClass = getConnectorClass();
    String connectorName = getConnectorName();
    
    super.localSetUp();
    
    // Register the connector we're testing
    initialize();
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();

    IDBInterface database = DBInterfaceFactory.make(tc,
      LCF.getMasterDatabaseName(),
      LCF.getMasterDatabaseUsername(),
      LCF.getMasterDatabasePassword());
    IConnectorManager mgr = ConnectorManagerFactory.make(tc);
    IJobManager jobManager = JobManagerFactory.make(tc);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
    // Deregistration should be done in a transaction
    database.beginTransaction();
    try
    {
      // First, register connector
      mgr.registerConnector(connectorName,connectorClass);
      // Then, signal to all jobs that might depend on this connector that they can switch state
      // Find the connection names that come with this class
      String[] connectionNames = connManager.findConnectionsForConnector(connectorClass);
      // For each connection name, modify the jobs to note that the connector is now installed
      jobManager.noteConnectorRegistration(connectionNames);
    }
    catch (LCFException e)
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
  
  @After
  public void cleanUp()
    throws Exception
  {
    localCleanUp();
  }

  protected void localCleanUp()
    throws Exception
  {
    String connectorClass = getConnectorClass();
    initialize();
    if (propertiesFile.exists())
    {
      // Test the uninstall
      LCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      
      Exception currentException = null;
      try
      {
        IDBInterface database = DBInterfaceFactory.make(tc,
          LCF.getMasterDatabaseName(),
          LCF.getMasterDatabaseUsername(),
          LCF.getMasterDatabasePassword());
        IConnectorManager mgr = ConnectorManagerFactory.make(tc);
        IJobManager jobManager = JobManagerFactory.make(tc);
        IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
        // Deregistration should be done in a transaction
        database.beginTransaction();
        try
        {
          // Find the connection names that come with this class
          String[] connectionNames = connManager.findConnectionsForConnector(connectorClass);
          // For each connection name, modify the jobs to note that the connector is no longer installed
          jobManager.noteConnectorDeregistration(connectionNames);
          // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
          mgr.unregisterConnector(connectorClass);
        }
        catch (LCFException e)
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
      catch (Exception e)
      {
        if (currentException != null)
          currentException = e;
      }
      try
      {
        super.localCleanUp();
      }
      catch (Exception e)
      {
        if (currentException != null)
          currentException = e;
      }
      if (currentException != null)
        throw currentException;
    }
  }

}
