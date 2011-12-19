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

package org.apache.manifoldcf.multiprocessjettyrunner;

import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

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
public class MCFMultiprocessJettyRunner
{

  public static final String _rcsid = "@(#)$Id$";

  protected Server server;
  
  public MCFMultiprocessJettyRunner( int port, String crawlerWarPath, String authorityServiceWarPath, String apiWarPath )
  {
    server = new Server( port );    
    server.setStopAtShutdown( true );
    
    // Initialize the servlets
    WebAppContext lcfCrawlerUI = new WebAppContext(crawlerWarPath,"/mcf-crawler-ui");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfCrawlerUI.setParentLoaderPriority(false);
    server.addHandler(lcfCrawlerUI);
    WebAppContext lcfAuthorityService = new WebAppContext(authorityServiceWarPath,"/mcf-authority-service");
    // This will cause jetty to ignore all of the framework and jdbc jars in the war, which is what we want.
    lcfAuthorityService.setParentLoaderPriority(false);
    server.addHandler(lcfAuthorityService);
    WebAppContext lcfApi = new WebAppContext(apiWarPath,"/mcf-api-service");
    lcfApi.setParentLoaderPriority(false);
    server.addHandler(lcfApi);
  }

  public void start()
    throws Exception
  {
    if(!server.isRunning() )
    {
      server.start();
    }
  }

  public void stop()
    throws Exception
  {
    if( server.isRunning() )
    {
      server.stop();
      server.join();
    }
  }

  /**
   * A main class that starts jetty with mcf wars
   */
  public static void main( String[] args )
  {
    if (args.length != 4 && args.length != 1 && args.length != 0)
    {
      System.err.println("Usage: MCFMultiprocessJettyRunner [<port> [<crawler-war-path> <authority-service-war-path> <api-war-path>]]");
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
    
    String crawlerWarPath = "web/war/mcf-crawler-ui.war";
    String authorityserviceWarPath = "web/war/mcf-authority-service.war";
    String apiWarPath = "web/war/mcf-api-service.war";
    if (args.length == 4)
    {
      crawlerWarPath = args[1];
      authorityserviceWarPath = args[2];
      apiWarPath = args[3];
    }
    
    // Ready to begin in earnest...  Set the system-wide properties to point to the current properties file.
    if (System.getProperty("org.apache.manifoldcf.configfile") == null)
    	System.setProperty("org.apache.manifoldcf.configfile","./properties.xml");
    try
    {
      System.err.println("Starting jetty...");
      
      // Create a jetty instance
      MCFMultiprocessJettyRunner jetty = new MCFMultiprocessJettyRunner(jettyPort,crawlerWarPath,authorityserviceWarPath,apiWarPath);
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
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


