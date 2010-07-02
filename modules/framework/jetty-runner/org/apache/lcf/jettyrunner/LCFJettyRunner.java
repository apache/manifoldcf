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

package org.apache.lcf.jettyrunner;

import java.io.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.*;

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
 * Run LCF with jetty.
 * 
 */
public class LCFJettyRunner
{

  public static final String _rcsid = "@(#)$Id$";

  public static final String agentShutdownSignal = org.apache.lcf.agents.AgentRun.agentShutdownSignal;

  protected Server server;
  
  public LCFJettyRunner( int port, String crawlerWarPath, String authorityServiceWarPath )
  {
    server = new Server( port );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/lcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(true);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityServiceWarPath,"/lcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(true);
    server.addHandler(lcfAuthorityService);
  }

  public void start()
    throws LCFException
  {
    if(!server.isRunning() )
    {
      try
      {
        server.start();
      }
      catch (Exception e)
      {
        throw new LCFException("Couldn't start: "+e.getMessage(),e);
      }
    }
  }

  public void stop()
    throws LCFException
  {
    if( server.isRunning() )
    {
      try
      {
        server.stop();
      }
      catch (Exception e)
      {
        throw new LCFException("Couldn't stop: "+e.getMessage(),e);
      }
      try
      {
        server.join();
      }
      catch (InterruptedException e)
      {
        throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
      }
    }
  }

  /**
   * Returns the Local Port of the first Connector found for the jetty Server.
   * @return the port number.
   */
  public int getLocalPort()
    throws LCFException
  {
    Connector[] conns = server.getConnectors();
    if (0 == conns.length) {
      throw new LCFException("Jetty Server has no Connectors");
    }
    return conns[0].getLocalPort();
  }

  /**
   * A main class that starts jetty+lcf
   */
  public static void main( String[] args )
  {
    if (args.length != 3)
    {
      System.err.println("Usage: LCFJettyRunner <port> <crawler-war-path> <authority-service-war-path>");
      System.exit(1);
    }

    int jettyPort = 8888;
    try
    {
      jettyPort = Integer.parseInt(args[0]);
    }
    catch (NumberFormatException e)
    {
      e.printStackTrace(System.err);
      System.exit(1);
    }
    
    // Ready to begin in earnest...
    
    
    try
    {
      LCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();

      // Clear the agents shutdown signal.
      ILockManager lockManager = LockManagerFactory.make(tc);
      lockManager.clearGlobalFlag(agentShutdownSignal);

      // Do the basic initialization of the database and its schema
      LCF.createSystemDatabase(tc,"","");
      LCF.installTables(tc);
      IAgentManager mgr = AgentManagerFactory.make(tc);
      mgr.registerAgent("org.apache.lcf.crawler.system.CrawlerAgent");

      // Other code will go here to discover and register various connectors that exist in the classpath
      // MHL

      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      LCFJettyRunner jetty = new LCFJettyRunner(jettyPort,args[1],args[2]);
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
      System.err.println("Shutting down crawler...");
    }
    catch (LCFException e)
    {
      Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


