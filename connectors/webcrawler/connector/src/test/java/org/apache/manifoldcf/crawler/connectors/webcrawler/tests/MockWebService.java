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
package org.apache.manifoldcf.crawler.connectors.webcrawler.tests;

import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.*;

/** Mock wiki service */
public class MockWebService
{
  Server server;
  WebServlet servlet;
  
  public MockWebService(int docsPerLevel)
  {
    this(docsPerLevel, 10, false);
  }
  
  public MockWebService(int docsPerLevel, int maxLevels, boolean generateBadPages)
  {
    server = new Server(new QueuedThreadPool(100));
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8191);
    server.addConnector(connector);
    servlet = new WebServlet(docsPerLevel, maxLevels, generateBadPages);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/web");
    server.setHandler(context);
    context.addServlet(new ServletHolder(servlet), "/gen.php");
  }
    
  public void start() throws Exception
  {
    server.start();
  }
    
  public void stop() throws Exception
  {
    server.stop();
  }

  
  public static class WebServlet extends HttpServlet
  {
    final int docsPerLevel;
    final int maxLevels;
    final boolean generateBadPages;
    
    public WebServlet(int docsPerLevel, int maxLevels, boolean generateBadPages)
    {
      this.docsPerLevel = docsPerLevel;
      this.maxLevels = maxLevels;
      this.generateBadPages = generateBadPages;
    }
    
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException
    {
      try {
        String resourceName = null;
        
        String site = req.getParameter("site");     // Site ID
        if (site == null)
          throw new IOException("Site ID parameter must be set");

        String level = req.getParameter("level");   // Level #
        if (site == null)
          throw new IOException("Level number parameter must be set");

        String item = req.getParameter("item");    // Item #
        if (item == null)
          throw new IOException("Item number parameter must be set");

        int theLevel;
        try
        {
          theLevel = Integer.parseInt(level);
        }
        catch (NumberFormatException e)
        {
          throw new IOException("Level number must be a number: "+level);
        }
        if (theLevel >= maxLevels)
          throw new IOException("Level number too big.");

        int theItem;
        try
        {
          theItem = Integer.parseInt(item);
        }
        catch (NumberFormatException e)
        {
          throw new IOException("Item number must be a number: "+item);
        }

        // Formulate the response.
        // First, calculate the number of docs on the current level
        int maxDocsThisLevel = 1;
        for (int i = 0 ; i < theLevel ; i++)
        {
          maxDocsThisLevel *= docsPerLevel;
        }
        if (theItem >= maxDocsThisLevel)
          // Not legal
          throw new IOException("Doc number too big: "+theItem+" ; level "+theLevel+" ; docsPerLevel "+docsPerLevel);

        // Generate the page
        if (generateBadPages && (theItem % 2) == 1)
        {
          // Generate a bad page.  This is a page with a non-200 return code, and with some content
          // > 1024 characters
          res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          res.getWriter().printf(Locale.ROOT, "This is the error message for a 401 page.");
          for (int i = 0; i < 1000; i++)
          {
            res.getWriter().printf(Locale.ROOT, " Error message # "+i);
          }
        }
        else
        {
          res.setStatus(HttpServletResponse.SC_OK);
          res.setContentType("text/html; charset=utf-8");
          res.getWriter().printf(Locale.ROOT, "<html>\n");
          res.getWriter().printf(Locale.ROOT, "  <body>\n");

          res.getWriter().printf(Locale.ROOT, "This is doc number "+theItem+" and level number "+theLevel+" in site "+site+"\n");

          // Generate links to all parents
          int parentLevel = theLevel;
          int parentItem = theItem;
          while (parentLevel > 0)
          {
            parentLevel--;
            parentItem /= docsPerLevel;
            generateLink(res,site,parentLevel,parentItem);
          }
          
          if (theLevel < maxLevels-1)
          {
            // Generate links to direct children
            for (int i = 0; i < docsPerLevel; i++)
            {
              int docNumber = i + theItem * docsPerLevel;
              generateLink(res,site,theLevel+1,docNumber);
            }
          }
          
          // Generate some limited cross-links to other items at this level
          for (int i = theItem; i < maxDocsThisLevel && i < theItem + docsPerLevel; i++)
          {
            generateLink(res,site,theLevel,i);
          }
          
          res.getWriter().printf(Locale.ROOT, "  </body>\n");
          res.getWriter().printf(Locale.ROOT, "</html>\n");
        }
        res.getWriter().flush();
      }
      catch (IOException e)
      {
        e.printStackTrace();
        throw e;
      }
    }
    
    protected void generateLink(HttpServletResponse res, String site, int level, int item)
      throws IOException
    {
      res.getWriter().printf(Locale.ROOT, "    <a href=\"http://localhost:8191/web/gen.php?site="+site+"&level="+level+"&item="+item+"\"/>\n");
    }

  }
}
