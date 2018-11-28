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
package org.apache.manifoldcf.crawler.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the agents framework. */
public class ConnectorBaseHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseHSQLDB
{
  
  protected String[] getConnectorClasses()
  {
    return new String[0];
  }
  
  protected String[] getConnectorNames()
  {
    return new String[0];
  }
  
  protected String[] getAuthorityClasses()
  {
    return new String[0];
  }
  
  protected String[] getAuthorityNames()
  {
    return new String[0];
  }
  
  protected String[] getOutputClasses()
  {
    return new String[0];
  }
  
  protected String[] getOutputNames()
  {
    return new String[0];
  }
  
  @Override
  protected void writeConnectors(StringBuilder output)
    throws Exception
  {
    String[] connectorClasses = getConnectorClasses();
    String[] connectorNames = getConnectorNames();
    for (int i = 0; i < connectorNames.length; i++)
    {
      output.append("    <repositoryconnector name=\""+connectorNames[i]+"\" class=\""+connectorClasses[i]+"\"/>\n");
    }
    
    String[] outputClasses = getOutputClasses();
    String[] outputNames = getOutputNames();
    for (int i = 0; i < outputNames.length; i++)
    {
      output.append("    <outputconnector name=\""+outputNames[i]+"\" class=\""+outputClasses[i]+"\"/>\n");
    }

    String[] authorityClasses = getAuthorityClasses();
    String[] authorityNames = getAuthorityNames();
    for (int i = 0; i < authorityNames.length; i++)
    {
      output.append("    <authorityconnector name=\""+authorityNames[i]+"\" class=\""+authorityClasses[i]+"\"/>\n");
    }

  }

  @Before
  public void setUp()
    throws Exception
  {
    initializeSystem();
    try
    {
      localReset();
    }
    catch (Exception e)
    {
      System.out.println("Warning: Preclean failed: "+e.getMessage());
    }
    try
    {
      localSetUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }

  protected void localSetUp()
    throws Exception
  {
    
    super.localSetUp();
    
    IThreadContext tc = ThreadContextFactory.make();

    IDBInterface database = DBInterfaceFactory.make(tc,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
    
    IConnectorManager mgr = ConnectorManagerFactory.make(tc);
    IAuthorityConnectorManager authMgr = AuthorityConnectorManagerFactory.make(tc);
    IJobManager jobManager = JobManagerFactory.make(tc);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
    IOutputConnectorManager outputMgr = OutputConnectorManagerFactory.make(tc);
    IOutputConnectionManager outputConnManager = OutputConnectionManagerFactory.make(tc);

    // Deregistration should be done in a transaction
    database.beginTransaction();
    try
    {
      int i;
      
      String[] connectorClasses = getConnectorClasses();
      String[] connectorNames = getConnectorNames();

      i = 0;
      while (i < connectorClasses.length)
      {
        // First, register connector
        mgr.registerConnector(connectorNames[i],connectorClasses[i]);
        // Then, signal to all jobs that might depend on this connector that they can switch state
        // Find the connection names that come with this class
        String[] connectionNames = connManager.findConnectionsForConnector(connectorClasses[i]);
        // For each connection name, modify the jobs to note that the connector is now installed
        jobManager.noteConnectorRegistration(connectionNames);
        i++;
      }
      
      String[] authorityClasses = getAuthorityClasses();
      String[] authorityNames = getAuthorityNames();
      
      i = 0;
      while (i < authorityClasses.length)
      {
        authMgr.registerConnector(authorityNames[i],authorityClasses[i]);
        i++;
      }
      
      String[] outputClasses = getOutputClasses();
      String[] outputNames = getOutputNames();
      
      i = 0;
      while (i < outputClasses.length)
      {
        // First, register connector
        outputMgr.registerConnector(outputNames[i],outputClasses[i]);
        // Then, signal to all jobs that might depend on this connector that they can switch state
        // Find the connection names that come with this class
        String[] connectionNames = outputConnManager.findConnectionsForConnector(outputClasses[i]);
        // For all connection names, notify all agents of the registration
        AgentManagerFactory.noteOutputConnectorRegistration(tc,connectionNames);
        i++;
      }
      
    }
    catch (ManifoldCFException e)
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
    try
    {
      localCleanUp();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
    cleanupSystem();
  }

  protected void localCleanUp()
    throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
      
    Exception currentException = null;
    // First, tear down all jobs, connections, authority connections, and output connections.
    try
    {
      IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(tc);
      IAuthorityConnectionManager authConnMgr = AuthorityConnectionManagerFactory.make(tc);
      IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);
        
      // Now, get a list of the repository connections
      IRepositoryConnection[] connections = connMgr.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        connMgr.delete(connections[i++].getName());
      }

      // Get a list of authority connections
      IAuthorityConnection[] authorities = authConnMgr.getAllConnections();
      i = 0;
      while (i < authorities.length)
      {
        authConnMgr.delete(authorities[i++].getName());
      }
        
      // Finally, get rid of output connections
      IOutputConnection[] outputs = outputMgr.getAllConnections();
      i = 0;
      while (i < outputs.length)
      {
        outputMgr.delete(outputs[i++].getName());
      }

    }
    catch (Exception e)
    {
      currentException = e;
    }
    try
    {
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
        
      IConnectorManager mgr = ConnectorManagerFactory.make(tc);
      IAuthorityConnectorManager authMgr = AuthorityConnectorManagerFactory.make(tc);
      IOutputConnectorManager outputMgr = OutputConnectorManagerFactory.make(tc);
      IOutputConnectionManager outputConnManager = OutputConnectionManagerFactory.make(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
        
      // Deregistration should be done in a transaction
      database.beginTransaction();
      try
      {
        int i;
          
        String[] connectorClasses = getConnectorClasses();

        i = 0;
        while (i < connectorClasses.length)
        {
          // Find the connection names that come with this class
          String[] connectionNames = connManager.findConnectionsForConnector(connectorClasses[i]);
          // For each connection name, modify the jobs to note that the connector is no longer installed
          jobManager.noteConnectorDeregistration(connectionNames);
          // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
          mgr.unregisterConnector(connectorClasses[i]);
          i++;
        }
          
        String[] authorityClasses = getAuthorityClasses();
          
        i = 0;
        while (i < authorityClasses.length)
        {
          authMgr.unregisterConnector(authorityClasses[i]);
          i++;
        }
          
        String[] outputClasses = getOutputClasses();
          
        i = 0;
        while (i < outputClasses.length)
        {
          // Find the connection names that come with this class
          String[] connectionNames = outputConnManager.findConnectionsForConnector(outputClasses[i]);
          // For all connection names, notify all agents of the deregistration
          AgentManagerFactory.noteOutputConnectorDeregistration(tc,connectionNames);
          // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
          outputMgr.unregisterConnector(outputClasses[i]);
          i++;
        }
          
      }
      catch (ManifoldCFException e)
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
