/* $Id: APIServlet.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.apiservlet;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.core.util.URLDecoder;

import org.apache.manifoldcf.ui.beans.APIProfile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


import javax.servlet.*;
import javax.servlet.http.*;

/** This servlet class provides API services for ManifoldCF.
*/
public class APIServlet extends HttpServlet
{
  public static final String _rcsid = "@(#)$Id: APIServlet.java 996524 2010-09-13 13:38:01Z kwright $";

  /** The init method.
  */
  public void init(ServletConfig config)
    throws ServletException
  {
    super.init(config);
  }

  /** The destroy method.
  */
  public void destroy()
  {
    super.destroy();
  }

  protected APIProfile getAPISession(IThreadContext tc, HttpServletRequest request)
  {
    Object x = request.getSession().getAttribute("apiprofile");
    if (x == null || !(x instanceof APIProfile))
    {
      // Basic login
      APIProfile ap = new APIProfile();
      request.getSession().setAttribute("apiprofile",ap);
      ap.login(tc,"","");
      return ap;
    }
    return (APIProfile)x;
  }
  
  /** The get method.
  */
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    try
    {
      // Mint a thread context
      IThreadContext tc = ThreadContextFactory.make();
      
      // Get the path info string.  This will furnish the command.
      String pathInfo = request.getPathInfo();
      // Get query string.  This is used by some GET operations.
      String queryString = request.getQueryString();
      
      if (pathInfo == null)
      {
        response.sendError(response.SC_BAD_REQUEST,"No path info found");
        return;
      }

      // Verify session
      APIProfile ap = getAPISession(tc,request);
      // Perform the get
      executeRead(tc,response,pathInfo,queryString,ap);
    }
    catch (ManifoldCFException e)
    {
      // We should only see this error if there's an API problem, not if there's an actual problem with the method being called.
      Logging.api.debug("API error doing GET: "+e.getMessage(),e);
      response.sendError(response.SC_BAD_REQUEST,e.getMessage());
    }
  }

  /** The PUT method.
  */
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    try
    {
      // Mint a thread context
      IThreadContext tc = ThreadContextFactory.make();
      
      // Get the path info string.  This will furnish the command.
      String pathInfo = request.getPathInfo();
      
      if (pathInfo == null)
      {
        response.sendError(response.SC_BAD_REQUEST,"No path info found");
        return;
      }

      // Verify session
      APIProfile ap = getAPISession(tc,request);
      // Get the content being 'put'
      InputStream content = request.getInputStream();
      try
      {
	// Do the put.
	executeWrite(tc,response,pathInfo,content,ap);
      }
      finally
      {
	content.close();
      }
    }
    catch (ManifoldCFException e)
    {
      // We should only see this error if there's an API problem, not if there's an actual problem with the method being called.
      Logging.api.debug("API error doing PUT: "+e.getMessage(),e);
      response.sendError(response.SC_BAD_REQUEST,e.getMessage());
    }
  }

 
  /** The POST method.
  */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    try
    {
      // Mint a thread context
      IThreadContext tc = ThreadContextFactory.make();
      
      // Get the path info string.  This will furnish the command.
      String pathInfo = request.getPathInfo();
      
      if (pathInfo == null)
      {
        response.sendError(response.SC_BAD_REQUEST,"No path info found");
        return;
      }

      // Verify session
      APIProfile ap = getAPISession(tc,request);
      // Get the content being posted
      InputStream content = request.getInputStream();
      try
      {
	// Do the put.
	executePost(tc,response,pathInfo,content,ap);
      }
      finally
      {
	content.close();
      }
      
    }
    catch (ManifoldCFException e)
    {
      // We should only see this error if there's an API problem, not if there's an actual problem with the method being called.
      Logging.api.debug("API error doing POST: "+e.getMessage(),e);
      response.sendError(response.SC_BAD_REQUEST,e.getMessage());
    }
  }

  /** The DELETE method.
  */
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    try
    {
      // Mint a thread context
      IThreadContext tc = ThreadContextFactory.make();
      
      // Get the path info string.  This will furnish the command.
      String pathInfo = request.getPathInfo();
      
      if (pathInfo == null)
      {
        response.sendError(response.SC_BAD_REQUEST,"No path info found");
        return;
      }

      // Verify session
      APIProfile ap = getAPISession(tc,request);
      // Perform the deletion
      executeDelete(tc,response,pathInfo,ap);
      
    }
    catch (ManifoldCFException e)
    {
      // We should only see this error if there's an API problem, not if there's an actual problem with the method being called.
      Logging.api.debug("API error doing DELETE: "+e.getMessage(),e);
      response.sendError(response.SC_BAD_REQUEST,e.getMessage());
    }
  }

  // Protected methods

  protected static void sendUnauthorizedResponse(HttpServletResponse response)
    throws IOException
  {
    response.setStatus(response.SC_UNAUTHORIZED);
    sendNullJSON(response);
  }
  
  protected static void sendNullJSON(HttpServletResponse response)
    throws IOException
  {
    String loutputText = "{}";
    byte[] lresponseValue = loutputText.getBytes(StandardCharsets.UTF_8);

    // Set response mime type
    response.setContentType("text/plain; charset=utf-8");
    response.setIntHeader("Content-Length", (int)lresponseValue.length);
    ServletOutputStream out = response.getOutputStream();
    try
    {
      out.write(lresponseValue,0,lresponseValue.length);
      out.flush();
    }
    finally
    {
      out.close();
    }
  }
  
  /** Perform a general "read" operation.
  */
  protected static void executeRead(IThreadContext tc, HttpServletResponse response, String pathInfo, String queryString, APIProfile ap)
    throws ManifoldCFException, IOException
  {
    if (!ap.getLoggedOn())
    {
      // Login failed
      sendUnauthorizedResponse(response);
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

    // If query string exists, parse it
    Map<String,List<String>> queryParameters = parseQueryString(queryString);
    
    // Execute the request.
    // Since there are no input arguments, we can do this before we look at the protocol.
    
    // There the only response distinction we have here is between exception and no exception.
    Configuration output = new Configuration();
    int readResult = ManifoldCF.executeReadCommand(tc,output,command,queryParameters,ap);

    // Output
    
    String outputText = null;

    if (protocol.equals("json"))
    {
      // Format the response
      try
      {
	outputText = output.toJSON();
      }
      catch (ManifoldCFException e)
      {
	// Log it
	Logging.api.error("Error forming JSON response: "+e.getMessage(),e);
	// Internal server error
	response.sendError(response.SC_INTERNAL_SERVER_ERROR);
	return;
      }
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }

    if (readResult == ManifoldCF.READRESULT_NOTFOUND)
      response.setStatus(response.SC_NOT_FOUND);
    else if (readResult == ManifoldCF.READRESULT_NOTALLOWED)
      response.setStatus(response.SC_UNAUTHORIZED);

    byte[] responseValue = outputText.getBytes(StandardCharsets.UTF_8);

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
  
  /** Perform a general "write" operation.
  */
  protected static void executeWrite(IThreadContext tc, HttpServletResponse response, String pathInfo, InputStream data, APIProfile ap)
    throws ManifoldCFException, IOException
  {
    if (!ap.getLoggedOn())
    {
      // Login failed
      sendUnauthorizedResponse(response);
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

    // We presume the data is utf-8
    StringBuilder sb = new StringBuilder();
    char[] buffer = new char[65536];
    Reader r = new InputStreamReader(data,StandardCharsets.UTF_8);
    while (true)
    {
      int amt = r.read(buffer);
      if (amt == -1)
	break;
      sb.append(buffer,0,amt);
    }
    String argument = sb.toString();
    
    // Parse the input
    Configuration input;
    
    if (protocol.equals("json"))
    {
      if (argument.length() != 0)
      {
	input = new Configuration();
	input.fromJSON(argument);
      }
      else
	input = null;
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }
    
    // Execute the request.
    
    // We need the following distinctions:
    // Exception vs. no exception
    // OK vs CREATE (both with json response packets)
    Configuration output = new Configuration();
    int writeResult = ManifoldCF.executeWriteCommand(tc,output,command,input,ap);
    
    // Output
    
    
    String outputText = null;

    if (protocol.equals("json"))
    {
      // Format the response
      try
      {
	outputText = output.toJSON();
      }
      catch (ManifoldCFException e)
      {
	// Log it
	Logging.api.error("Error forming JSON response: "+e.getMessage(),e);
	// Internal server error
	response.sendError(response.SC_INTERNAL_SERVER_ERROR);
	return;
      }
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }

    // This should return either 200 or SC_CREATED
    if (writeResult == ManifoldCF.WRITERESULT_CREATED)
      response.setStatus(response.SC_CREATED);
    else if (writeResult == ManifoldCF.WRITERESULT_NOTFOUND)
      response.setStatus(response.SC_NOT_FOUND);
    else if (writeResult == ManifoldCF.WRITERESULT_NOTALLOWED)
      response.setStatus(response.SC_UNAUTHORIZED);
    
    byte[] responseValue = outputText.getBytes(StandardCharsets.UTF_8);

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

  /** Perform a general "post" operation.
  */
  protected static void executePost(IThreadContext tc, HttpServletResponse response, String pathInfo, InputStream data, APIProfile ap)
    throws ManifoldCFException, IOException
  {
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

    // Security check.  If the protocol is JSON and the command is LOGIN, we do the login now.  But to
    // prevent denial of service attacks, we don't accept more than a limited amount of login JSON.
    if (protocol.equals("json") && command.equals("LOGIN")) {
      // Do the login!
      // Parse the json login packet
      char[] lbuffer = new char[65536];
      StringBuilder lsb = new StringBuilder();
      Reader lr = new InputStreamReader(data,StandardCharsets.UTF_8);
      while (true)
      {
        int amt = lr.read(lbuffer);
        if (amt == -1)
          break;
        if (lsb.length() + amt > 65536)
          break;
        lsb.append(lbuffer,0,amt);
      }
      
      Configuration loginInput = new Configuration();
      loginInput.fromJSON(lsb.toString());

      String userID = "";
      String password = "";
      for (int i = 0; i < loginInput.getChildCount(); i++)
      {
        ConfigurationNode cn = loginInput.findChild(i);
        if (cn.getType().equals("userID"))
          userID = cn.getValue();
        else if (cn.getType().equals("password"))
          password = cn.getValue();
      }
      ap.login(tc,userID,password);
      if (!ap.getLoggedOn())
      {
        sendUnauthorizedResponse(response);
        return;
      }
      else
      {
        sendNullJSON(response);
        return;
      }
    }

    if (!ap.getLoggedOn())
    {
      // Login failed
      sendUnauthorizedResponse(response);
      return;
    }

    // We presume the data is utf-8
    StringBuilder sb = new StringBuilder();
    char[] buffer = new char[65536];
    Reader r = new InputStreamReader(data,StandardCharsets.UTF_8);
    while (true)
    {
      int amt = r.read(buffer);
      if (amt == -1)
	break;
      sb.append(buffer,0,amt);
    }
    String argument = sb.toString();
    
    // Parse the input
    Configuration input;
    
    if (protocol.equals("json"))
    {
      if (argument.length() != 0)
      {
	input = new Configuration();
	input.fromJSON(argument);
      }
      else
	input = null;
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }
    
    // Execute the request.

    
    Configuration output = new Configuration();
    int writeResult = ManifoldCF.executePostCommand(tc,output,command,input,ap);
    
    // Output
    
    
    String outputText = null;

    if (protocol.equals("json"))
    {
      // Format the response
      try
      {
	outputText = output.toJSON();
      }
      catch (ManifoldCFException e)
      {
	// Log it
	Logging.api.error("Error forming JSON response: "+e.getMessage(),e);
	// Internal server error
	response.sendError(response.SC_INTERNAL_SERVER_ERROR);
	return;
      }
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }

    // This should return either 200 or SC_CREATED
    if (writeResult == ManifoldCF.POSTRESULT_CREATED)
      response.setStatus(response.SC_CREATED);
    else if (writeResult == ManifoldCF.POSTRESULT_NOTFOUND)
      response.setStatus(response.SC_NOT_FOUND);
    else if (writeResult == ManifoldCF.POSTRESULT_NOTALLOWED)
      response.setStatus(response.SC_UNAUTHORIZED);

    byte[] responseValue = outputText.getBytes(StandardCharsets.UTF_8);

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
  
  /** Perform a general "delete" operation.
  */
  protected static void executeDelete(IThreadContext tc, HttpServletResponse response, String pathInfo, APIProfile ap)
    throws ManifoldCFException, IOException
  {
    if (!ap.getLoggedOn())
    {
      // Login failed
      sendUnauthorizedResponse(response);
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

    // Execute the request.
    // Since there are no input arguments, we can do this before we look at the protocol.
    
    // There the only response distinction we have here is between exception and no exception.
    Configuration output = new Configuration();
    int result = ManifoldCF.executeDeleteCommand(tc,output,command,ap);
    
    // Output
    String outputText = null;

    if (protocol.equals("json"))
    {
      // Format the response
      try
      {
	outputText = output.toJSON();
      }
      catch (ManifoldCFException e)
      {
	// Log it
	Logging.api.error("Error forming JSON response: "+e.getMessage(),e);
	// Internal server error
	response.sendError(response.SC_INTERNAL_SERVER_ERROR);
	return;
      }
    }
    else
    {
      response.sendError(response.SC_BAD_REQUEST,"Unknown API protocol: "+protocol);
      return;
    }
    
    if (result == ManifoldCF.DELETERESULT_NOTFOUND)
      response.setStatus(response.SC_NOT_FOUND);
    else if (result == ManifoldCF.DELETERESULT_NOTALLOWED)
      response.setStatus(response.SC_UNAUTHORIZED);

    
    byte[] responseValue = outputText.getBytes(StandardCharsets.UTF_8);

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

    // return code 200 assumed!
  }
  
  protected static Map<String,List<String>> parseQueryString(String queryString)
  {
    if (queryString == null)
      return null;
    Map<String,List<String>> rval = new HashMap<String,List<String>>();
    String[] terms = queryString.split("&");
    for (String term : terms)
    {
      int index = term.indexOf("=");
      if (index == -1)
        addValue(rval,URLDecoder.decode(term),"");
      else
        addValue(rval,URLDecoder.decode(term.substring(0,index)),URLDecoder.decode(term.substring(index+1)));
    }
    return rval;
  }
  
  protected static void addValue(Map<String,List<String>> rval, String name, String value)
  {
    List<String> valueList = rval.get(name);
    if (valueList == null)
    {
      valueList = new ArrayList<String>(1);
      rval.put(name,valueList);
    }
    valueList.add(value);
  }
  
}
