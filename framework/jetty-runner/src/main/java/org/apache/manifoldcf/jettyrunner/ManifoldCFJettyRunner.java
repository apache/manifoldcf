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

      // Clear the agents shutdown signal.
      ILockManager lockManager = LockManagerFactory.make(tc);
      lockManager.clearGlobalFlag(agentShutdownSignal);
      
      // Do the basic initialization of the database and its schema
      ManifoldCF.createSystemDatabase(tc);
      
      ManifoldCF.installTables(tc);
      
      org.apache.manifoldcf.crawler.system.ManifoldCF.registerThisAgent(tc);
      
      ManifoldCF.reregisterAllConnectors(tc);
      
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


