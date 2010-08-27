/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.acf.jettyrunner;

import java.io.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.authorities.interfaces.*;
import org.apache.acf.agents.system.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.log.Logger;

/**
 * Run ACF with jetty.
 * 
 */
public class ACFJettyRunner
{

  public static final String _rcsid = "@(#)$Id$";

  public static final String agentShutdownSignal = org.apache.acf.agents.AgentRun.agentShutdownSignal;

  // Configuration parameters
  public static final String connectorsConfigurationFile = "org.apache.acf.connectorsconfigurationfile";
  
  // Connectors configuration file
  public static final String NODE_OUTPUTCONNECTOR = "outputconnector";
  public static final String NODE_AUTHORITYCONNECTOR = "authorityconnector";
  public static final String NODE_REPOSITORYCONNECTOR = "repositoryconnector";
  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_CLASS = "class";
  
  protected Server server;
  
  public ACFJettyRunner( int port, String crawlerWarPath, String authorityServiceWarPath, String apiWarPath )
  {
    server = new Server( port );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/acf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityServiceWarPath,"/acf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    server.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/acf-api-service");
    lcfApi.setParentLoaderPriority(true);
    server.addHandler(lcfApi);
  }

  public void start()
    throws ACFException
  {
    if(!server.isRunning() )
    {
      try
      {
        server.start();
      }
      catch (Exception e)
      {
        throw new ACFException("Couldn't start: "+e.getMessage(),e);
      }
    }
  }

  public void stop()
    throws ACFException
  {
    if( server.isRunning() )
    {
      try
      {
        server.stop();
      }
      catch (Exception e)
      {
        throw new ACFException("Couldn't stop: "+e.getMessage(),e);
      }
      try
      {
        server.join();
      }
      catch (InterruptedException e)
      {
        throw new ACFException(e.getMessage(),e,ACFException.INTERRUPTED);
      }
    }
  }

  /**
   * Returns the Local Port of the first Connector found for the jetty Server.
   * @return the port number.
   */
  public int getLocalPort()
    throws ACFException
  {
    Connector[] conns = server.getConnectors();
    if (0 == conns.length) {
      throw new ACFException("Jetty Server has no Connectors");
    }
    return conns[0].getLocalPort();
  }

  /**
   * A main class that starts jetty+acf
   */
  public static void main( String[] args )
  {
    if (args.length != 4 && args.length != 1 && args.length != 0)
    {
      System.err.println("Usage: ACFJettyRunner [<port> [<crawler-war-path> <authority-service-war-path> <api-war-path>]]");
      System.exit(1);
    }

    int jettyPort = 8345;
    if (args.length > 0)
    {
      try
      {
        jettyPort = Integer.parseInt(args[0]);
      }
      catch (NumberFormatException e)
      {
        e.printStackTrace(System.err);
        System.exit(1);
      }
    }
    
    String crawlerWarPath = "war/acf-crawler-ui.war";
    String authorityserviceWarPath = "war/acf-authority-service.war";
    String apiWarPath = "war/acf-api-service.war";
    if (args.length == 4)
    {
      crawlerWarPath = args[1];
      authorityserviceWarPath = args[2];
      apiWarPath = args[3];
    }
    
    // Ready to begin in earnest...
    System.setProperty(ACF.lcfConfigFileProperty,"./properties.xml");
    try
    {
      ACF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();

      // Clear the agents shutdown signal.
      ILockManager lockManager = LockManagerFactory.make(tc);
      lockManager.clearGlobalFlag(agentShutdownSignal);

      // Grab a database handle, so we can use transactions later.
      IDBInterface database = DBInterfaceFactory.make(tc,
        ACF.getMasterDatabaseName(),
        ACF.getMasterDatabaseUsername(),
        ACF.getMasterDatabasePassword());

      // Do the basic initialization of the database and its schema
      ACF.createSystemDatabase(tc,"","");
      ACF.installTables(tc);
      IAgentManager agentMgr = AgentManagerFactory.make(tc);
      agentMgr.registerAgent("org.apache.acf.crawler.system.CrawlerAgent");

      // Read connectors configuration file (to figure out what we need to register)
      Connectors c = null;
      File connectorConfigFile = ACF.getFileProperty(connectorsConfigurationFile);
      if (connectorConfigFile != null)
      {
        try
        {
          // Open the file, read it, and attempt to do the connector registrations
          InputStream is = new FileInputStream(connectorConfigFile);
          try
          {
            c = new Connectors(is);
          }
          finally
          {
            is.close();
          }
        }
        catch (FileNotFoundException e)
        {
          throw new ACFException("Couldn't find connector configuration file: "+e.getMessage(),e);
        }
        catch (IOException e)
        {
          throw new ACFException("Error reading connector configuration file: "+e.getMessage(),e);
        }
      }
      
      // Unregister all connectors.
      
      // Output connectors...
      {
        IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
        IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
        IResultSet classNames = mgr.getConnectors();
        int i = 0;
        while (i < classNames.getRowCount())
        {
          IResultRow row = classNames.getRow(i++);
          String className = (String)row.getValue("classname");
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For all connection names, notify all agents of the deregistration
            AgentManagerFactory.noteOutputConnectorDeregistration(tc,connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ACFException e)
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
        System.err.println("Successfully unregistered all output connectors");
      }
      
      // Authority connectors...
      {
        IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);
        IResultSet classNames = mgr.getConnectors();
        int i = 0;
        while (i < classNames.getRowCount())
        {
          IResultRow row = classNames.getRow(i++);
          mgr.unregisterConnector((String)row.getValue("classname"));
        }
        System.err.println("Successfully unregistered all authority connectors");
      }
      
      // Repository connectors...
      {
        IConnectorManager mgr = ConnectorManagerFactory.make(tc);
        IJobManager jobManager = JobManagerFactory.make(tc);
        IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
        IResultSet classNames = mgr.getConnectors();
        int i = 0;
        while (i < classNames.getRowCount())
        {
          IResultRow row = classNames.getRow(i++);
          String className = (String)row.getValue("classname");
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For each connection name, modify the jobs to note that the connector is no longer installed
            jobManager.noteConnectorDeregistration(connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ACFException e)
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
        System.err.println("Successfully unregistered all repository connectors");
      }
      
      if (c != null)
      {

        // Other code will go here to discover and register various connectors that exist in the classpath
        int i = 0;
        while (i < c.getChildCount())
        {
          ConfigurationNode cn = c.findChild(i++);
          if (cn.getType().equals(NODE_OUTPUTCONNECTOR))
          {
            String name = cn.getAttributeValue(ATTRIBUTE_NAME);
            String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
            IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
            IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
            // Registration should be done in a transaction
            database.beginTransaction();
            try
            {
              // First, register connector
              mgr.registerConnector(name,className);
              // Then, signal to all jobs that might depend on this connector that they can switch state
              // Find the connection names that come with this class
              String[] connectionNames = connManager.findConnectionsForConnector(className);
              // For all connection names, notify all agents of the registration
              AgentManagerFactory.noteOutputConnectorRegistration(tc,connectionNames);
            }
            catch (ACFException e)
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
            System.err.println("Successfully registered output connector '"+className+"'");
          }
          else if (cn.getType().equals(NODE_AUTHORITYCONNECTOR))
          {
            String name = cn.getAttributeValue(ATTRIBUTE_NAME);
            String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
            IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);
            mgr.registerConnector(name,className);
            System.err.println("Successfully registered authority connector '"+className+"'");
          }
          else if (cn.getType().equals(NODE_REPOSITORYCONNECTOR))
          {
            String name = cn.getAttributeValue(ATTRIBUTE_NAME);
            String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
            IConnectorManager mgr = ConnectorManagerFactory.make(tc);
            IJobManager jobManager = JobManagerFactory.make(tc);
            IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
            // Deregistration should be done in a transaction
            database.beginTransaction();
            try
            {
              // First, register connector
              mgr.registerConnector(name,className);
              // Then, signal to all jobs that might depend on this connector that they can switch state
              // Find the connection names that come with this class
              String[] connectionNames = connManager.findConnectionsForConnector(className);
              // For each connection name, modify the jobs to note that the connector is now installed
              jobManager.noteConnectorRegistration(connectionNames);
            }
            catch (ACFException e)
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
            System.err.println("Successfully registered repository connector '"+className+"'");
          }
          else
            throw new ACFException("Unrecognized connectors node type '"+cn.getType()+"'");
        }
      }
      
      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      ACFJettyRunner jetty = new ACFJettyRunner(jettyPort,crawlerWarPath,authorityserviceWarPath,apiWarPath);
      // This will register a shutdown hook as well.
      jetty.start();

      System.err.println("Jetty started.");

      System.err.println("Starting crawler...");
      while (true)
      {
        // Any shutdown signal yet?
        if (lockManager.checkGlobalFlag(agentShutdownSignal))
          break;
          
        // Start whatever agents need to be started
        ACF.startAgents(tc);

        try
        {
          ACF.sleep(5000);
        }
        catch (InterruptedException e)
        {
          break;
        }
      }
      System.err.println("Shutting down crawler...");
    }
    catch (ACFException e)
    {
      if (Logging.root != null)
        Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


