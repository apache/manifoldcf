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

package org.apache.manifoldcf.jettyrunner;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

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
 * Run ManifoldCF with jetty.
 * 
 */
public class ManifoldCFJettyRunner
{

  public static final String _rcsid = "@(#)$Id: ManifoldCFJettyRunner.java 989983 2010-08-27 00:10:12Z kwright $";

  public static final String agentShutdownSignal = org.apache.manifoldcf.agents.AgentRun.agentShutdownSignal;

  // Configuration parameters
  public static final String connectorsConfigurationFile = "org.apache.manifoldcf.connectorsconfigurationfile";
  public static final String databaseSuperuserName = "org.apache.manifoldcf.dbsuperusername";
  public static final String databaseSuperuserPassword = "org.apache.manifoldcf.dbsuperuserpassword";
  
  // Connectors configuration file
  public static final String NODE_OUTPUTCONNECTOR = "outputconnector";
  public static final String NODE_AUTHORITYCONNECTOR = "authorityconnector";
  public static final String NODE_REPOSITORYCONNECTOR = "repositoryconnector";
  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_CLASS = "class";
  
  protected Server server;
  
  public ManifoldCFJettyRunner( int port, String crawlerWarPath, String authorityServiceWarPath, String apiWarPath )
  {
    server = new Server( port );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/mcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityServiceWarPath,"/mcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    server.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/mcf-api-service");
    lcfApi.setParentLoaderPriority(true);
    server.addHandler(lcfApi);
  }

  public void start()
    throws ManifoldCFException
  {
    if(!server.isRunning() )
    {
      try
      {
        server.start();
      }
      catch (Exception e)
      {
        throw new ManifoldCFException("Couldn't start: "+e.getMessage(),e);
      }
    }
  }

  public void stop()
    throws ManifoldCFException
  {
    if( server.isRunning() )
    {
      try
      {
        server.stop();
      }
      catch (Exception e)
      {
        throw new ManifoldCFException("Couldn't stop: "+e.getMessage(),e);
      }
      try
      {
        server.join();
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
    }
  }

  /**
   * Returns the Local Port of the first Connector found for the jetty Server.
   * @return the port number.
   */
  public int getLocalPort()
    throws ManifoldCFException
  {
    Connector[] conns = server.getConnectors();
    if (0 == conns.length) {
      throw new ManifoldCFException("Jetty Server has no Connectors");
    }
    return conns[0].getLocalPort();
  }

  /** Clear the agents shutdown signal */
  public static void clearShutdownSignal(IThreadContext tc)
    throws ManifoldCFException
  {
    // Clear the agents shutdown signal.
    ILockManager lockManager = LockManagerFactory.make(tc);
    lockManager.clearGlobalFlag(agentShutdownSignal);
  }

  /** Create the database and the schema */
  public static void createDatabaseAndSchema(IThreadContext tc, String superuserName, String superuserPassword)
    throws ManifoldCFException
  {
    ManifoldCF.createSystemDatabase(tc,superuserName,superuserPassword);
    ManifoldCF.installTables(tc);
  }
  
  /** Register the agents */
  public static void registerAgents(IThreadContext tc)
    throws ManifoldCFException
  {
    // Register 
    IAgentManager agentMgr = AgentManagerFactory.make(tc);
    agentMgr.registerAgent("org.apache.manifoldcf.crawler.system.CrawlerAgent");
  }

  /** Read connectors configuration file.
  */
  public static Connectors readConnectorDeclarations(File connectorConfigFile)
    throws ManifoldCFException
  {
    Connectors c = null;
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
        throw new ManifoldCFException("Couldn't find connector configuration file: "+e.getMessage(),e);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Error reading connector configuration file: "+e.getMessage(),e);
      }
    }
    return c;
  }

  /** Unregister all connectors.
  */
  public static void unregisterAllConnectors(IThreadContext tc)
    throws ManifoldCFException
  {
    // Grab a database handle, so we can use transactions later.
    IDBInterface database = DBInterfaceFactory.make(tc,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());

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
      System.err.println("Successfully unregistered all repository connectors");
    }
  }

  /** Register all connectors as specified by a Connectors structure, usually read from the connectors.xml file.
  */
  public static void registerConnectors(IThreadContext tc, Connectors c)
    throws ManifoldCFException
  {
    if (c != null)
    {
      // Grab a database handle, so we can use transactions later.
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
        
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
          System.err.println("Successfully registered repository connector '"+className+"'");
        }
        else
          throw new ManifoldCFException("Unrecognized connectors node type '"+cn.getType()+"'");
      }
    }
  }

  /** Run the agents process.  This method will not return unless the agents process is shut down.
  */
  public static void runAgents(IThreadContext tc)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(tc);

    while (true)
    {
      // Any shutdown signal yet?
      if (lockManager.checkGlobalFlag(agentShutdownSignal))
        break;
          
      // Start whatever agents need to be started
      ManifoldCF.startAgents(tc);

      try
      {
        ManifoldCF.sleep(5000);
      }
      catch (InterruptedException e)
      {
        break;
      }
    }
  }

  /**
   * A main class that starts jetty+mcf
   */
  public static void main( String[] args )
  {
    if (args.length != 4 && args.length != 1 && args.length != 0)
    {
      System.err.println("Usage: ManifoldCFJettyRunner [<port> [<crawler-war-path> <authority-service-war-path> <api-war-path>]]");
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
    
    String crawlerWarPath = "war/mcf-crawler-ui.war";
    String authorityserviceWarPath = "war/mcf-authority-service.war";
    String apiWarPath = "war/mcf-api-service.war";
    if (args.length == 4)
    {
      crawlerWarPath = args[1];
      authorityserviceWarPath = args[2];
      apiWarPath = args[3];
    }
    
    // Ready to begin in earnest...
    if (System.getProperty(ManifoldCF.lcfConfigFileProperty) == null)
    	System.setProperty(ManifoldCF.lcfConfigFileProperty,"./properties.xml");
    try
    {
      ManifoldCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();

      // Clear the shutdown signal
      clearShutdownSignal(tc);
      
      // Get the specified superuser name and password, in case this isn't Derby we're using
      String superuserName = ManifoldCF.getProperty(databaseSuperuserName);
      if (superuserName == null)
        superuserName = "";
      String superuserPassword = ManifoldCF.getProperty(databaseSuperuserPassword);
      if (superuserPassword == null)
        superuserPassword = "";
      
      // Do the basic initialization of the database and its schema
      createDatabaseAndSchema(tc,superuserName,superuserPassword);
      registerAgents(tc);
      
        // Read connectors configuration file (to figure out what we need to register)
      File connectorConfigFile = ManifoldCF.getFileProperty(connectorsConfigurationFile);
      Connectors c = readConnectorDeclarations(connectorConfigFile);
      
      // Unregister all connectors.
      unregisterAllConnectors(tc);

      // Register connections specified by connectors.xml
      registerConnectors(tc,c);
      
      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      ManifoldCFJettyRunner jetty = new ManifoldCFJettyRunner(jettyPort,crawlerWarPath,authorityserviceWarPath,apiWarPath);
      // This will register a shutdown hook as well.
      jetty.start();

      System.err.println("Jetty started.");

      System.err.println("Starting crawler...");
      runAgents(tc);
      System.err.println("Shutting down crawler...");
    }
    catch (ManifoldCFException e)
    {
      if (Logging.root != null)
        Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


