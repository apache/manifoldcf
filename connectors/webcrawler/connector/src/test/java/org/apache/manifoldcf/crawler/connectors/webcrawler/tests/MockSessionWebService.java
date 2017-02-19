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
import javax.servlet.http.HttpSession;

import java.io.*;
import java.util.*;

/** Mock web service that requires session authentication */
public class MockSessionWebService
{
  Server server;
  SessionWebServlet servlet;
    
  public MockSessionWebService(int numContentDocs, String userName, String password)
  {
    server = new Server(new QueuedThreadPool(100));
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8191);
    server.addConnector(connector);
    servlet = new SessionWebServlet(numContentDocs,userName,password);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setInitParameter("org.eclipse.jetty.servlet.SessionIdPathParameterName","none");
    context.setContextPath("/web");
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

  
  public static class SessionWebServlet extends HttpServlet
  {
    protected final int contentPageCount;
    protected final String loginUser;
    protected final String loginPassword;
    
    public SessionWebServlet(int contentPageCount, String loginUser, String loginPassword)
    {
      this.contentPageCount = contentPageCount;
      this.loginUser = loginUser;
      this.loginPassword = loginPassword;
    }
    
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException
    {
      try {
        // This mock web service resolves the following urls:
        // /index.html (an index of all N content pages)
        // /protectedcontent.html?id=N  (N content pages)
        // /loginpage.html (the login page, rendered either as a form,
        //    or as a redirection back to the content page, or as a redirection to the index page)
        
        // Get path part of request URL
        String pathPart = req.getPathInfo();
        if (pathPart == null)
        {
          // 404
          generateMissingPageResponse(res);
        }
        else
        {
          if (pathPart.equals("/loginpage.html"))
          {
            // Login page logic
            String id = req.getParameter("id");
            Integer idNumber;
            if (id == null)
              idNumber = null;
            else
              idNumber = new Integer(id);
            
            HttpSession session = req.getSession();
            Object loginInfo = session.getAttribute("logininfo");
            if (loginInfo != null && (loginInfo instanceof Boolean) && (((Boolean)loginInfo).booleanValue()))
            {
              // Already logged in: redirect back to content or index
              generateLoginRedirectPage(res,idNumber);
            }
            else
            {
              String userName = req.getParameter("user");
              String password = req.getParameter("password");
            
              if (userName == null || password == null || !loginUser.equals(userName) || !loginPassword.equals(password))
              {
                generateLoginFormPage(res,idNumber);
              }
              else
              {
                // Login succeeded, so set the session properly
                session.setAttribute("logininfo",new Boolean(true));
                generateLoginRedirectPage(res,idNumber);
              }
            }
          }
          else if (pathPart.equals("/protectedcontent.html"))
          {
            // Content page logic
            String id = req.getParameter("id");
            if (id == null)
            {
              generateBadArgumentResponse(res);
            }
            else
            {
              Integer idNumber = new Integer(id);
              if (idNumber.intValue() >= contentPageCount)
              {
                generateMissingPageResponse(res);
              }
              else
              {
                HttpSession session = req.getSession();
                Object loginInfo = session.getAttribute("logininfo");
                if (loginInfo != null && (loginInfo instanceof Boolean) && (((Boolean)loginInfo).booleanValue()))
                {
                  // Return content
                  generateContentDisplayPage(res,idNumber.intValue());
                }
                else
                {
                  // Redirect to login page
                  generateContentRedirectPage(res,idNumber.intValue());
                }
              }
            }
          }
          else if (pathPart.equals("/index.html"))
          {
            // Index logic
            HttpSession session = req.getSession();
            Object loginInfo = session.getAttribute("logininfo");
            if (loginInfo != null && (loginInfo instanceof Boolean) && (((Boolean)loginInfo).booleanValue()))
            {
              // Return content
              generateIndexDisplayPage(res,contentPageCount);
            }
            else
            {
              generateIndexRedirectPage(res);
            }
          }
          else
          {
            generateMissingPageResponse(res);
          }
        }
      }
      catch (IOException e)
      {
        e.printStackTrace();
        throw e;
      }
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
    
    protected static void generateLoginRedirectPage(HttpServletResponse res, Integer returnID)
      throws IOException
    {
      String redirectTarget;
      if (returnID == null)
        redirectTarget = "/web/index.html";
      else
        redirectTarget = "/web/protectedcontent.html?id="+returnID;
      res.sendRedirect(redirectTarget);
    }
    
    protected static void generateLoginFormPage(HttpServletResponse res, Integer returnID)
      throws IOException
    {
      String actionURI = "/web/loginpage.html";
      if (returnID != null)
        actionURI += "?id="+returnID;
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("text/html; charset=utf-8");

      res.getWriter().printf(Locale.ROOT, "<html>\n");
      res.getWriter().printf(Locale.ROOT, "  <body>\n");
      res.getWriter().printf(Locale.ROOT, "    <form name=\"login\" action=\""+actionURI+"\">\n");
      res.getWriter().printf(Locale.ROOT, "      User name: <input type=\"text\" name=\"user\" value=\"\" size=\"20\"/>\n");
      res.getWriter().printf(Locale.ROOT, "      Password: <input type=\"password\" name=\"password\" value=\"\" size=\"20\"/>\n");
      res.getWriter().printf(Locale.ROOT, "      <input type=\"submit\"/>\n");
      res.getWriter().printf(Locale.ROOT, "    </form>\n");
      res.getWriter().printf(Locale.ROOT, "  </body>\n");
      res.getWriter().printf(Locale.ROOT, "</html>\n");
      
      res.getWriter().flush();

    }
    
    protected static void generateContentRedirectPage(HttpServletResponse res, int itemNumber)
      throws IOException
    {
      String redirectTarget = "/web/loginpage.html?id="+itemNumber;
      res.sendRedirect(redirectTarget);
    }

    protected static void generateContentDisplayPage(HttpServletResponse res, int itemNumber)
      throws IOException
    {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("text/html; charset=utf-8");

      res.getWriter().printf(Locale.ROOT, "<html>\n");
      res.getWriter().printf(Locale.ROOT, "  <body>This is the document content for item "+itemNumber+"</body>");
      res.getWriter().printf(Locale.ROOT, "</html>\n");
      
      res.getWriter().flush();
    }
    
    protected static void generateIndexRedirectPage(HttpServletResponse res)
      throws IOException
    {
      String redirectTarget = "/web/loginpage.html";
      res.sendRedirect(redirectTarget);
    }
    
    protected static void generateIndexDisplayPage(HttpServletResponse res, int countItems)
      throws IOException
    {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("text/html; charset=utf-8");

      res.getWriter().printf(Locale.ROOT, "<html>\n");
      res.getWriter().printf(Locale.ROOT, "  <body>\n");

      for (int i = 0; i < countItems; i++)
      {
        generateContentLink(res,i);
      }
      
      res.getWriter().printf(Locale.ROOT, "  </body>\n");
      res.getWriter().printf(Locale.ROOT, "</html>\n");
      res.getWriter().flush();

    }
    
    protected static void generateContentLink(HttpServletResponse res, int itemNumber)
      throws IOException
    {
      res.getWriter().printf(Locale.ROOT, "    <a href=\"/web/protectedcontent.html?id="+itemNumber+"\">Item "+itemNumber+"</a>\n");
    }

  }
}
