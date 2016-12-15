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
package org.apache.manifoldcf.agents.output.solr.tests;

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
public class MockSolrService
{
  Server server;
  SolrServlet servlet;
    
  public MockSolrService()
  {
    server = new Server(new QueuedThreadPool(35));
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8188);
    server.addConnector(connector);
    servlet = new SolrServlet();
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setInitParameter("org.eclipse.jetty.servlet.SessionIdPathParameterName","none");
    context.setContextPath("/solr");
    server.setHandler(context);
    context.addServlet(new ServletHolder(servlet), "/*");
  }
    
  public void start() throws Exception
  {
    server.start();
  }
    
  public void stop() throws Exception
  {
    server.stop();
  }

  
  public static class SolrServlet extends HttpServlet
  {
    public SolrServlet()
    {
    }
    
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException
    {
      try
      {
        // Get path part of request URL
        String pathPart = req.getPathInfo();
        if (pathPart == null)
          generateMissingPageResponse(res);
        else if (pathPart.equals("/admin/ping"))
        {
          generatePingResponse(res);
        }
        else if (pathPart.equals("/update/extract"))
        {
          generateUpdateResponse(res);
        }
        else if (pathPart.equals("/update"))
        {
          generateDeleteResponse(res);
        }
        else
        {
          generateMissingPageResponse(res);
        }
      }
      catch (IOException e)
      {
        e.printStackTrace();
        throw e;
      }

    }

    protected static void generatePingResponse(HttpServletResponse res)
      throws IOException
    {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/xml; charset=utf-8");
      res.getWriter().printf(Locale.ROOT, "<solr>\n");
      res.getWriter().printf(Locale.ROOT, "</solr>\n");
      res.getWriter().flush();
    }
    
    protected static void generateUpdateResponse(HttpServletResponse res)
      throws IOException
    {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/xml; charset=utf-8");
      res.getWriter().printf(Locale.ROOT, "<result>\n");
      res.getWriter().printf(Locale.ROOT, "  <doc name=\"something\"/>\n");
      res.getWriter().printf(Locale.ROOT, "</result>\n");
      res.getWriter().flush();
    }
    
    protected static void generateDeleteResponse(HttpServletResponse res)
      throws IOException
    {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/xml; charset=utf-8");
      res.getWriter().printf(Locale.ROOT, "<result>\n");
      res.getWriter().printf(Locale.ROOT, "  <doc name=\"something\"/>\n");
      res.getWriter().printf(Locale.ROOT, "</result>\n");
      res.getWriter().flush();
    }
    
    protected static void generateMissingPageResponse(HttpServletResponse res)
      throws IOException
    {
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
    
    protected static void generateBadArgumentResponse(HttpServletResponse res)
      throws IOException
    {
      res.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

  }

}
