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
import org.apache.manifoldcf.crawler.system.*;
import org.apache.manifoldcf.crawler.*;
import org.apache.manifoldcf.agents.system.AgentsDaemon;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.server.Handler;

/**
 * Run ManifoldCF with jetty.
 * 
 */
public class ManifoldCFJettyRunner
{

  public static final String _rcsid = "@(#)$Id: ManifoldCFJettyRunner.java 989983 2010-08-27 00:10:12Z kwright $";

  public static final String crawlerUIWarPathProperty = "org.apache.manifoldcf.crawleruiwarpath";
  public static final String authorityServiceWarPathProperty = "org.apache.manifoldcf.authorityservicewarpath";
  public static final String apiServiceWarPathProperty = "org.apache.manifoldcf.apiservicewarpath";
  public static final String useJettyParentClassLoaderProperty = "org.apache.manifoldcf.usejettyparentclassloader";
  public static final String jettyConfigFileProperty = "org.apache.manifoldcf.jettyconfigfile";
  
  protected Server server;
  
  public ManifoldCFJettyRunner( File configFile, String crawlerWarPath, String authorityServiceWarPath, String apiWarPath, boolean useParentLoader )
    throws Exception
  {
    Resource fileserverXml = Resource.newResource(configFile.getCanonicalFile());
    XmlConfiguration configuration = new XmlConfiguration(fileserverXml.getInputStream());
    server = (Server)configuration.configure();
    initializeServer(crawlerWarPath, authorityServiceWarPath, apiWarPath, useParentLoader);
  }
  
  public ManifoldCFJettyRunner( int port, String crawlerWarPath, String authorityServiceWarPath, String apiWarPath, boolean useParentLoader )
  {
    Server server = new Server( port );
    initializeServer(crawlerWarPath, authorityServiceWarPath, apiWarPath, useParentLoader);
  }
  
  protected void initializeServer( String crawlerWarPath, String authorityServiceWarPath, String apiWarPath, boolean useParentLoader )
  {
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/mcf-crawler-ui");
    // This can cause jetty to ignore all of the framework and jdbc jars in the war, which is what we
    // want in the single-process case.
    lcfCrawlerUI.setParentLoaderPriority(useParentLoader);
    contexts.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityServiceWarPath,"/mcf-authority-service");
    // This can cause jetty to ignore all of the framework and jdbc jars in the war, which is what we
    // want in the single-process case.
    lcfAuthorityService.setParentLoaderPriority(useParentLoader);
    contexts.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/mcf-api-service");
    // This can cause jetty to ignore all of the framework and jdbc jars in the war, which is what we
    // want in the single-process case.
    lcfApi.setParentLoaderPriority(useParentLoader);
    contexts.addHandler(lcfApi);
    
    HandlerList handlers = new HandlerList();
    handlers.addHandler(contexts);
    
    // Pick up shutdown token
    String shutdownToken = System.getProperty("org.apache.manifoldcf.jettyshutdowntoken");
    if (shutdownToken != null)
    {
      ShutdownHandler shutdown = new ShutdownHandler(shutdownToken);
      shutdown.setExitJvm(true);

      handlers.addHandler(shutdown);
    }
    server.setHandler(handlers);

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
    ServerConnector[] conns = (ServerConnector[]) server.getConnectors();
    if (0 == conns.length) {
      throw new ManifoldCFException("Jetty Server has no Connectors");
    }
    return conns[0].getLocalPort();
  }

  /** Run the agents process.  This method will not return unless the agents process is shut down.
  */
  public static void runAgents(IThreadContext tc)
    throws ManifoldCFException
  {
    String processID = ManifoldCF.getProcessID();
    // Do this so we don't have to call stopAgents() ourselves.
    AgentsDaemon ad = new AgentsDaemon(processID);
    ad.registerAgentsShutdownHook(tc);
    ad.runAgents(tc);
  }

  /**
   * A main class that starts jetty+mcf
   */
  public static void main( String[] args )
  {
    if (args.length != 4 && args.length != 1 && args.length != 0)
    {
      System.err.println("Usage: ManifoldCFJettyRunner [<jetty-config-file> [<crawler-war-path> <authority-service-war-path> <api-war-path>]]");
      System.exit(1);
    }

    
    // Ready to begin in earnest...
    if (System.getProperty(ManifoldCF.lcfConfigFileProperty) == null)
    	System.setProperty(ManifoldCF.lcfConfigFileProperty,"./properties.xml");
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);

      // Grab the parameters which locate the wars and describe how we work with Jetty.  These are not shared.
      File crawlerWarPath = ManifoldCF.getFileProperty(crawlerUIWarPathProperty);
      File authorityserviceWarPath = ManifoldCF.getFileProperty(authorityServiceWarPathProperty);
      File apiWarPath = ManifoldCF.getFileProperty(apiServiceWarPathProperty);
      boolean useParentClassLoader = ManifoldCF.getBooleanProperty(useJettyParentClassLoaderProperty,true);
      File jettyConfigFile = ManifoldCF.getFileProperty(jettyConfigFileProperty);

      if (jettyConfigFile == null)
        jettyConfigFile = new File("./jetty.xml");
      if (args.length == 4)
      {
        crawlerWarPath = new File(args[1]);
        authorityserviceWarPath = new File(args[2]);
        apiWarPath = new File(args[3]);
      }
      else
      {
        if (crawlerWarPath == null)
          throw new ManifoldCFException("The property '"+crawlerUIWarPathProperty+"' must be set");
        if (authorityserviceWarPath == null)
          throw new ManifoldCFException("The property '"+authorityServiceWarPathProperty+"' must be set");
        if (apiWarPath == null)
          throw new ManifoldCFException("The property '"+apiServiceWarPathProperty+"' must be set");
      }
      
      if (useParentClassLoader)
      {
        // Clear the agents shutdown signal.
        AgentsDaemon.clearAgentsShutdownSignal(tc);
        
        // Do the basic initialization of the database and its schema
        ManifoldCF.createSystemDatabase(tc);
        
        ManifoldCF.installTables(tc);
        
        org.apache.manifoldcf.crawler.system.ManifoldCF.registerThisAgent(tc);
        
        ManifoldCF.reregisterAllConnectors(tc);
      }
      
      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      ManifoldCFJettyRunner jetty = new ManifoldCFJettyRunner(jettyConfigFile,crawlerWarPath.toString(),authorityserviceWarPath.toString(),apiWarPath.toString(),useParentClassLoader);
      // This will register a shutdown hook as well.
      jetty.start();

      System.err.println("Jetty started.");

      if (useParentClassLoader)
      {
        System.err.println("Starting crawler...");
        runAgents(tc);
        System.err.println("Shutting down crawler...");
      }
      else
      {
        // Go to sleep until interrupted.
        while (true)
        {
          try
          {
            Thread.sleep(5000);
            continue;
          }
          catch (InterruptedException e)
          {
            break;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (Logging.root != null)
        Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


