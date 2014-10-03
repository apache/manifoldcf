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

 /* Run ManifoldCF with jetty.
 * 
 */
public class ManifoldCFCombinedJettyRunner
{

  public static final String _rcsid = "@(#)$Id$";

  public static final String combinedWarPathProperty = "org.apache.manifoldcf.combinedwarpath";
  public static final String jettyConfigFileProperty = "org.apache.manifoldcf.jettyconfigfile";
  
  protected Server server;
  

  public ManifoldCFCombinedJettyRunner( File configFile, String combinedWarPath )
    throws Exception
  {
    Resource fileserverXml = Resource.newResource(configFile.getCanonicalFile());
    XmlConfiguration configuration = new XmlConfiguration(fileserverXml.getInputStream());
    server = (Server)configuration.configure();
    initializeServer( combinedWarPath );
  }

  public ManifoldCFCombinedJettyRunner( int port, String combinedWarPath )
  {
    server = new Server( port );
    initializeServer( combinedWarPath );
  }

  protected void initializeServer( String combinedWarPath )
  {
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    WebAppContext mcfCombined = new WebAppContext(combinedWarPath,"/mcf");
    mcfCombined.setParentLoaderPriority(false);
    contexts.addHandler(mcfCombined);
    
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

  /**
   * A main class that starts jetty+mcf
   */
  public static void main( String[] args )
  {
    if (args.length != 2 && args.length != 1 && args.length != 0)
    {
      System.err.println("Usage: ManifoldCFCombinedJettyRunner [<port> [<combined-war-path>]]");
      System.exit(1);
    }

    
    // Ready to begin in earnest...
    if (System.getProperty(ManifoldCF.lcfConfigFileProperty) == null)
    	System.setProperty(ManifoldCF.lcfConfigFileProperty,"./properties.xml");
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);

      // Grab the parameters which locate the wars and describe how we work with Jetty
      File combinedWarPath = ManifoldCF.getFileProperty(combinedWarPathProperty);
      File jettyConfigFile = ManifoldCF.getFileProperty(jettyConfigFileProperty);
      if (jettyConfigFile == null)
        jettyConfigFile = new File("./jetty.xml");
      if (args.length == 2)
      {
        combinedWarPath = new File(args[1]);
      }
      else
      {
        if (combinedWarPath == null)
          throw new ManifoldCFException("The property '"+combinedWarPathProperty+"' must be set");
      }
      
      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      ManifoldCFCombinedJettyRunner jetty = new ManifoldCFCombinedJettyRunner(jettyConfigFile,combinedWarPath.toString());
      // This will register a shutdown hook as well.
      jetty.start();

      System.err.println("Jetty started.");

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
    catch (Exception e)
    {
      if (Logging.root != null)
        Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


