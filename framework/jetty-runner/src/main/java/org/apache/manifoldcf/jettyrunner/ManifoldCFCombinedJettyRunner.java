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
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.server.Connector;

/**
 * Run ManifoldCF with jetty.
 * 
 */
public class ManifoldCFCombinedJettyRunner
{

  public static final String _rcsid = "@(#)$Id$";

  public static final String combinedWarPathProperty = "org.apache.manifoldcf.combinedwarpath";
  public static final String jettyPortProperty = "org.apache.manifoldcf.jettyport";
  
  protected Server server;
  
  public ManifoldCFCombinedJettyRunner( int port, String combinedWarPath )
  {
    server = new Server( port );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    server.setHandler(contexts);
    WebAppContext mcfCombined = new WebAppContext(combinedWarPath,"/mcf");
    mcfCombined.setParentLoaderPriority(false);
    contexts.addHandler(mcfCombined);
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
      int jettyPort = ManifoldCF.getIntProperty(jettyPortProperty,8347);
      if (args.length > 0)
      {
        try
        {
          jettyPort = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Illegal value for jetty port argument: "+e.getMessage(),e);
        }
      }
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
      ManifoldCFCombinedJettyRunner jetty = new ManifoldCFCombinedJettyRunner(jettyPort,combinedWarPath.toString());
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
    catch (ManifoldCFException e)
    {
      if (Logging.root != null)
        Logging.root.error("Exception: "+e.getMessage(),e);
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


