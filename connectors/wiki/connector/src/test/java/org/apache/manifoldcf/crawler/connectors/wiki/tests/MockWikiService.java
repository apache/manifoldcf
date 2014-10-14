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
package org.apache.manifoldcf.crawler.connectors.wiki.tests;

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
public class MockWikiService
{
  Server server;
  WikiAPIServlet servlet;
    
  public MockWikiService(Class theResourceClass)
  {
    server = new Server(new QueuedThreadPool(35));
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8089);
    server.addConnector(connector);
    servlet = new WikiAPIServlet(theResourceClass);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/w");
    server.setHandler(context);
    context.addServlet(new ServletHolder(servlet), "/api.php");
  }
    
  public void start() throws Exception
  {
    server.start();
  }
    
  public void stop() throws Exception
  {
    server.stop();
  }

  public void setResources(Map<String,String> checkResources,
    Map<String,String> listResources,
    Map<String,String> timestampQueryResources,
    Map<String,String> urlQueryResources,
    Map<String,String> docInfoQueryResources,
    String namespaceResource)
  {
    servlet.setResources(checkResources,listResources,timestampQueryResources,urlQueryResources,docInfoQueryResources,namespaceResource);
  }
  
  protected static String sortStuff(String input)
  {
    String[] ids = input.split("\\|");
    java.util.Arrays.sort(ids);
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < ids.length ; i++)
    {
      if (i > 0)
        sb.append("|");
      sb.append(ids[i]);
    }
    return sb.toString();
  }
  
  public static class WikiAPIServlet extends HttpServlet
  {
    protected Class theResourceClass;
    
    protected Map<String,String> checkResources = null;
    protected Map<String,String> listResources = null;
    protected Map<String,String> timestampQueryResources = null;
    protected Map<String,String> urlQueryResources = null;
    protected Map<String,String> docInfoQueryResources = null;
    protected String namespaceResource = null;
    
    public WikiAPIServlet(Class theResourceClass)
    {
      this.theResourceClass = theResourceClass;
    }
    
    public void setResources(Map<String,String> checkResources,
      Map<String,String> listResources,
      Map<String,String> timestampQueryResources,
      Map<String,String> urlQueryResources,
      Map<String,String> docInfoQueryResources,
      String namespaceResource)
    {
      this.checkResources = checkResources;
      this.listResources = listResources;
      this.timestampQueryResources = timestampQueryResources;
      this.urlQueryResources = urlQueryResources;
      this.docInfoQueryResources = docInfoQueryResources;
      this.namespaceResource = namespaceResource;
    }
    
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException
    {
      String resourceName = null;
      
      String format = req.getParameter("format");
      if (!format.equals("xml"))
        throw new IOException("Format parameter incorrect: "+format);
      String action = req.getParameter("action");
      if (action == null || !action.equals("query"))
        throw new IOException("Action parameter incorrect: "+action);
      String list = req.getParameter("list");
      String prop = req.getParameter("prop");
      String siprop = req.getParameter("siprop");
      if (prop != null)
      {
	if (siprop != null || list != null)
	  throw new IOException("Cannot have both prop and list or siprop");
        String pageIds = req.getParameter("pageids");
        if (prop.equals("revisions"))
        {
          String rvprop = req.getParameter("rvprop");
          if (rvprop != null && rvprop.equals("timestamp"))
          {
            // Version query
            if (pageIds == null)
              throw new IOException("missing pageids parameter, required for version query");
            // Sort the pageIds
            pageIds = sortStuff(pageIds);
            resourceName = timestampQueryResources.get(pageIds);
            if (resourceName == null)
              throw new IOException("Could not find a matching resource for the timestamp parameters; pageids = '"+pageIds+"'");
          }
          else if (rvprop != null && rvprop.equals("user|comment|content|timestamp"))
          {
            // Doc info query
            if (pageIds == null)
              throw new IOException("missing pageids parameter, required for docinfo query");
            if (pageIds.indexOf("|") != -1)
              throw new IOException("cannot do more than one docinfo request at once");
            resourceName = docInfoQueryResources.get(pageIds);
            if (resourceName == null)
              throw new IOException("Could not find a matching resource for the user|comment|content|timestamp parameters; pageids = '"+pageIds+"'");
          }
          else
            throw new IOException("rvprop parameter missing or incorrect: "+rvprop);
        }
        else if (prop.equals("info"))
        {
          String inprop = req.getParameter("inprop");
          if (inprop == null || !inprop.equals("url"))
            throw new IOException("inprop parameter missing or incorrect: "+inprop);
          // url query
          if (pageIds == null)
            throw new IOException("missing pageids parameter, required for url query");
          // Sort the pageIds
          pageIds = sortStuff(pageIds);
          resourceName = urlQueryResources.get(pageIds);
          if (resourceName == null)
            throw new IOException("Could not find a matching resource for the info url parameters; pageids = '"+pageIds+"'");
        }
        else
          throw new IOException("Invalid value for prop parameter: "+prop);
      }
      else if (list != null)
      {
	if (prop != null || siprop != null)
	  throw new IOException("Cannot have both list and prop or siprop");
        if (!list.equals("allpages"))
          throw new IOException("List parameter incorrect: "+list);
        String apfrom = req.getParameter("apfrom");
        if (apfrom == null)
          apfrom = "";
        String aplimit = req.getParameter("aplimit");
        // Only two legal values for aplimit here: 1 and 500.
        if (aplimit.equals("1"))
          resourceName = checkResources.get(apfrom);
        else if (aplimit.equals("500"))
          resourceName = listResources.get(apfrom);
        else
          throw new IOException("aplimit parameter incorrect: "+aplimit);
        if (resourceName == null)
          throw new IOException("Could not find a matching resource for the list parameters; apfrom = '"+apfrom+"'");

      }
      else if (siprop != null)
      {
	if (prop != null || list != null)
	  throw new IOException("Cannot have both siprop and list or prop");
	String meta = req.getParameter("meta");
	if (meta == null || !meta.equals("siteinfo"))
	  throw new IOException("meta parameter missing or incorrect");
	resourceName = namespaceResource;
      }

      // Select the resource
      if (resourceName == null)
        throw new IOException("Could not find a matching resource for the parameters");
      
      res.setStatus(HttpServletResponse.SC_OK);
      
      OutputStream os = res.getOutputStream();
      try
      {
        InputStream is = theResourceClass.getResourceAsStream(resourceName);
        if (is == null)
          throw new IOException("Can't locate resource '"+resourceName+"' in class '"+theResourceClass.getName()+"'");
        try
        {
          byte[] bytes = new byte[65536];
          while (true)
          {
            int amt = is.read(bytes,0,bytes.length);
            if (amt == -1)
              break;
            os.write(bytes,0,amt);
          }
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        os.close();
      }
    }
  }
}
