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
package org.apache.manifoldcf.rss_tests;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.thread.QueuedThreadPool;

import java.io.*;
import java.util.*;

/** Mock wiki service */
public class MockRSSService
{
  Server server;
  RSSServlet servlet;
    
  public MockRSSService(int docsPerFeed)
  {
    server = new Server(8189);
    server.setThreadPool(new QueuedThreadPool(35));
    servlet = new RSSServlet(docsPerFeed);
    Context asContext = new Context(server,"/rss",Context.SESSIONS);
    asContext.addServlet(new ServletHolder(servlet), "/gen.php");
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
        res.setContentType("text/xml; charset=utf-8");
        // Write out an rss 2.0 response, with docsperfeed docs
        res.getWriter().printf("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        res.getWriter().printf("<rss>\n");
        res.getWriter().printf("  <channel>\n");
        for (int i = 0 ; i < docsPerFeed ; i++)
        {
          res.getWriter().printf("    <item>\n");
          res.getWriter().printf("      <link>http://localhost:8189/rss/gen.php?type=doc&#38;feed="+theFeed+"&#38;doc="+i+"</link>\n");
          res.getWriter().printf("      <title>Feed "+theFeed+" Document "+i+"</title>\n");
          res.getWriter().printf("    </item>\n");
        }
        res.getWriter().printf("  </channel>\n");
        res.getWriter().printf("</rss>\n");
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
        res.getWriter().printf("This is feed number "+theFeed+" and document number "+theDoc+"\n");
        res.getWriter().flush();
      }
      else
        throw new IOException("Illegal type parameter: "+type);
      
    }
  }
}
