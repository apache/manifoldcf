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
package org.apache.lcf.api;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.LCF;
import org.apache.lcf.crawler.system.Logging;

import java.io.*;
import java.util.*;
import java.net.*;

import javax.servlet.*;
import javax.servlet.http.*;

/** This servlet class provides API services for LCF.
*/
public class APIServlet extends HttpServlet
{
  public static final String _rcsid = "@(#)$Id$";

  /** The init method.
  */
  public void init(ServletConfig config)
    throws ServletException
  {
    super.init(config);
    try
    {
      // Set up the environment
      LCF.initializeEnvironment();
      // Nothing more needs to be done at this point.
    }
    catch (LCFException e)
    {
      Logging.misc.error("Error starting API service: "+e.getMessage(),e);
      throw new ServletException("Error starting API service: "+e.getMessage(),e);
    }

  }

  /** The destroy method.
  */
  public void destroy()
  {
    try
    {
      // Set up the environment
      LCF.initializeEnvironment();
      // Nothing more needs to be done.
    }
    catch (LCFException e)
    {
      Logging.misc.error("Error shutting down API service: "+e.getMessage(),e);
    }
    super.destroy();
  }

  /** The get method.
  */
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    try
    {
      // Set up the environment
      LCF.initializeEnvironment();

      // Mint a thread context
      IThreadContext tc = ThreadContextFactory.make();
      
      // Get the path info string.  This will furnish the command.
      String pathInfo = request.getPathInfo();
      
      // The first field of the pathInfo string is the protocol.  Someday this will be a dispatcher using reflection.  Right now, we only support json, so a quick check is fine.
      if (pathInfo == null)
      {
        response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol");
        return;
      }
      
      // Strip off leading "/"
      if (pathInfo.startsWith("/"))
        pathInfo = pathInfo.substring(1);
      
      int index = pathInfo.indexOf("/");
      String protocol;
      String command;
      if (index == -1)
      {
        protocol = pathInfo;
        command = "";
      }
      else
      {
        protocol = pathInfo.substring(0,index);
        command = pathInfo.substring(index+1);
      }
        
      // Handle multipart forms
      IPostParameters parameters = new org.apache.lcf.ui.multipart.MultipartWrapper(request);
        
      // Input
      String argument = parameters.getParameter("object");
      // Output
      String outputText = null;

      if (protocol.equals("json"))
      {
        // Parse the input argument, if it is present
        Configuration input;
        if (argument != null)
        {
          input = new Argument();
          input.fromJSON(argument);
        }
        else
          input = null;
          
        Configuration output = LCF.executeCommand(tc,command,input);
          
        // Format the response
        outputText = output.toJSON();
      }
      else
      {
        response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
        return;
      }
        
      byte[] responseValue = outputText.getBytes("utf-8");

      // Set response mime type
      response.setContentType("text/plain; charset=utf-8");
      response.setIntHeader("Content-Length", (int)responseValue.length);
      ServletOutputStream out = response.getOutputStream();
      try
      {
        out.write(responseValue,0,responseValue.length);
        out.flush();
      }
      finally
      {
        out.close();
      }
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      //Logging.authorityService.error("Unsupported encoding: "+e.getMessage(),e);
      throw new ServletException("Fatal error occurred: "+e.getMessage(),e);
    }
    catch (LCFException e)
    {
      // We should only see this error if there's an API problem, not if there's an actual problem with the method being called.
      //Logging.authorityService.error("API servlet error: "+e.getMessage(),e);
      response.sendError(response.SC_BAD_REQUEST,e.getMessage());
    }
  }

}
