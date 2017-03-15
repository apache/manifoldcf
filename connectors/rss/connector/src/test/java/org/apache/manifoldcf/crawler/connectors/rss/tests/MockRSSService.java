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
package org.apache.manifoldcf.crawler.connectors.rss.tests;

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
public class MockRSSService
{
  Server server;
  RSSServlet servlet;
    
  public MockRSSService(int docsPerFeed)
  {
    server = new Server(new QueuedThreadPool(35));
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8189);
    server.addConnector(connector);
    servlet = new RSSServlet(docsPerFeed);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/rss");
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

  
  public static class RSSServlet extends HttpServlet
  {
    int docsPerFeed;
    
    public RSSServlet(int docsPerFeed)
    {
      this.docsPerFeed = docsPerFeed;
    }
    
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException
    {
      String resourceName = null;
      
      String type = req.getParameter("type");
      String feednum = req.getParameter("feed");
      if (feednum == null)
        throw new IOException("Feed number parameter must be set");
      int theFeed;
      try
      {
        theFeed = Integer.parseInt(feednum);
      }
      catch (NumberFormatException e)
      {
        throw new IOException("Feed number must be a number: "+feednum);
      }
      // Now that we parsed it, we don't actually need it (yet)
      
      if (type != null && type.equals("feed"))
      {
        // Generate feed response
        res.setStatus(HttpServletResponse.SC_OK);
        // Randomly choose a different encoding, to make life interesting for the parser
        if ((theFeed % 3) == 0)
        {
          res.setContentType("text/xml; charset=utf-8");
          res.getWriter().printf(Locale.ROOT, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        else if ((theFeed % 3) ==1)
        {
          res.setContentType("text/xml");
          res.setCharacterEncoding("UTF-16BE");
          // Write BOM + preamble
          res.getWriter().printf(Locale.ROOT, "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16BE\"?>\n");
        }
        else
        {
          res.setContentType("text/xml");
          res.setCharacterEncoding("UTF-16LE");
          // Write BOM + preamble
          res.getWriter().printf(Locale.ROOT, "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16LE\"?>\n");
        }
        // Write out an rss 2.0 response, with docsperfeed docs
        res.getWriter().printf(Locale.ROOT, "<rss>\n");
        res.getWriter().printf(Locale.ROOT, "  <channel>\n");
        for (int i = 0 ; i < docsPerFeed ; i++)
        {
          res.getWriter().printf(Locale.ROOT, "    <item>\n");
          // Test CDATA feeds
          if ((i % 2) == 0)
            res.getWriter().printf(Locale.ROOT, "      <link>http://localhost:8189/rss/gen.php?type=doc&#38;feed="+theFeed+"&#38;doc="+i+"</link>\n");
          else
            res.getWriter().printf(Locale.ROOT, "      <link><![CDATA[http://localhost:8189/rss/gen.php?type=doc&feed="+theFeed+"&doc="+i+"]]></link>\n");
          res.getWriter().printf(Locale.ROOT, "      <title>Feed "+theFeed+" Document "+i+"</title>\n");
          res.getWriter().printf(Locale.ROOT, "    </item>\n");
        }
        res.getWriter().printf(Locale.ROOT, "  </channel>\n");
        res.getWriter().printf(Locale.ROOT, "</rss>\n");
        res.getWriter().flush();
      }
      else if (type != null && type.equals("doc"))
      {
        String docnum = req.getParameter("doc");
        if (docnum == null)
          throw new IOException("Doc number parameter must be set");
        int theDoc;
        try
        {
          theDoc = Integer.parseInt(docnum);
        }
        catch (NumberFormatException e)
        {
          throw new IOException("Doc number must be a number: "+docnum);
        }
        
        // Generate doc response
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("text/plain; charset=utf-8");
        res.getWriter().printf(Locale.ROOT, "This is feed number "+theFeed+" and document number "+theDoc+"\n");
        res.getWriter().flush();
      }
      else
        throw new IOException("Illegal type parameter: "+type);
      
    }
  }
}
