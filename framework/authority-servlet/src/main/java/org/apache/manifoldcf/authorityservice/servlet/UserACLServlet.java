/* $Id: UserACLServlet.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorityservice.servlet;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.ManifoldCF;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.authorities.system.RequestQueue;
import org.apache.manifoldcf.authorities.system.AuthRequest;

import java.io.*;
import java.util.*;
import java.net.*;

import javax.servlet.*;
import javax.servlet.http.*;

/** This servlet class is meant to receive a user name and return a list of access tokens.
* The user name is expected to be sent as an argument on the url (the "username" argument), and the
* response will simply be a list of access tokens separated by newlines.
* This is guaranteed safe because the index system cannot work with access tokens that aren't 7-bit ascii that
* have any control characters in them.
*
* Errors will simply report back with an empty acl.
*
* The content type will always be text/plain.
*/
public class UserACLServlet extends HttpServlet
{
  public static final String _rcsid = "@(#)$Id: UserACLServlet.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final static String AUTHORIZED_VALUE = "AUTHORIZED:";
  protected final static String UNREACHABLE_VALUE = "UNREACHABLEAUTHORITY:";
  protected final static String UNAUTHORIZED_VALUE = "UNAUTHORIZED:";
  protected final static String USERNOTFOUND_VALUE = "USERNOTFOUND:";

  protected final static String ID_PREFIX = "ID:";
  protected final static String TOKEN_PREFIX = "TOKEN:";

  /** The init method.
  */
  public void init(ServletConfig config)
    throws ServletException
  {
    super.init(config);
    try
    {
      // Set up the environment
      //ManifoldCF.initializeEnvironment();
      IThreadContext itc = ThreadContextFactory.make();
      ManifoldCF.startSystem(itc);
    }
    catch (ManifoldCFException e)
    {
      Logging.misc.error("Error starting authority service: "+e.getMessage(),e);
      throw new ServletException("Error starting authority service: "+e.getMessage(),e);
    }

  }

  /** The destroy method.
  */
  public void destroy()
  {
    try
    {
      // Set up the environment
      //ManifoldCF.initializeEnvironment();
      IThreadContext itc = ThreadContextFactory.make();
      ManifoldCF.stopSystem(itc);
    }
    catch (ManifoldCFException e)
    {
      Logging.misc.error("Error shutting down authority service: "+e.getMessage(),e);
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
      //ManifoldCF.initializeEnvironment();

      Logging.authorityService.debug("Received request");

      String userID = request.getParameter("username");
      if (userID == null)
      {
        response.sendError(response.SC_BAD_REQUEST);
        return;
      }

      boolean idneeded = false;
      boolean aclneeded = true;

      String idneededValue = request.getParameter("idneeded");
      if (idneededValue != null)
      {
        if (idneededValue.equals("true"))
          idneeded = true;
        else if (idneededValue.equals("false"))
          idneeded = false;
      }
      String aclneededValue = request.getParameter("aclneeded");
      if (aclneededValue != null)
      {
        if (aclneededValue.equals("true"))
          aclneeded = true;
        else if (aclneededValue.equals("false"))
          aclneeded = false;
      }

      if (Logging.authorityService.isDebugEnabled())
      {
        Logging.authorityService.debug("Received authority request for user '"+userID+"'");
      }

      RequestQueue queue = ManifoldCF.getRequestQueue();
      if (queue == null)
      {
        // System wasn't started; return unauthorized
        throw new ManifoldCFException("System improperly initialized");
      }

      IThreadContext itc = ThreadContextFactory.make();
      IAuthorityConnectionManager authConnManager = AuthorityConnectionManagerFactory.make(itc);

      IAuthorityConnection[] connections = authConnManager.getAllConnections();
      int i = 0;

      AuthRequest[] requests = new AuthRequest[connections.length];

      // Queue up all the requests
      while (i < connections.length)
      {
        IAuthorityConnection ac = connections[i];

        String identifyingString = ac.getDescription();
        if (identifyingString == null || identifyingString.length() == 0)
          identifyingString = ac.getName();

        AuthRequest ar = new AuthRequest(userID,ac.getClassName(),identifyingString,ac.getConfigParams(),ac.getMaxConnections());
        queue.addRequest(ar);

        requests[i++] = ar;
      }

      // Now, work through the returning answers.
      i = 0;

      // Ask all the registered authorities for their ACLs, and merge the final list together.
      StringBuilder sb = new StringBuilder();
      // Set response mime type
      response.setContentType("text/plain; charset=ISO8859-1");
      ServletOutputStream out = response.getOutputStream();
      try
      {
        while (i < connections.length)
        {
          IAuthorityConnection ac = connections[i];
          AuthRequest ar = requests[i++];

          if (Logging.authorityService.isDebugEnabled())
            Logging.authorityService.debug("Waiting for answer from connector class '"+ac.getClassName()+"' for user '"+userID+"'");

          ar.waitForComplete();

          if (Logging.authorityService.isDebugEnabled())
            Logging.authorityService.debug("Received answer from connector class '"+ac.getClassName()+"' for user '"+userID+"'");

          Throwable exception = ar.getAnswerException();
          AuthorizationResponse reply = ar.getAnswerResponse();
          if (exception != null)
          {
            // Exceptions are always bad now
            // The ManifoldCFException here must disable access to the UI without causing a generic badness thing to happen, so use 403.
            if (exception instanceof ManifoldCFException)
              response.sendError(response.SC_FORBIDDEN,"From "+ar.getIdentifyingString()+": "+exception.getMessage());
            else
              response.sendError(response.SC_INTERNAL_SERVER_ERROR,"From "+ar.getIdentifyingString()+": "+exception.getMessage());
            return;
          }

          if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_UNREACHABLE)
          {
            Logging.authorityService.warn("Authority '"+ar.getIdentifyingString()+"' is unreachable for user '"+userID+"'");
            sb.append(UNREACHABLE_VALUE).append(java.net.URLEncoder.encode(ar.getIdentifyingString(),"UTF-8")).append("\n");
          }
          else if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_USERUNAUTHORIZED)
          {
            if (Logging.authorityService.isDebugEnabled())
              Logging.authorityService.debug("Authority '"+ar.getIdentifyingString()+"' does not authorize user '"+userID+"'");
            sb.append(UNAUTHORIZED_VALUE).append(java.net.URLEncoder.encode(ar.getIdentifyingString(),"UTF-8")).append("\n");
          }
          else if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_USERNOTFOUND)
          {
            if (Logging.authorityService.isDebugEnabled())
              Logging.authorityService.debug("User '"+userID+"' unknown to authority '"+ar.getIdentifyingString()+"'");
            sb.append(USERNOTFOUND_VALUE).append(java.net.URLEncoder.encode(ar.getIdentifyingString(),"UTF-8")).append("\n");
          }
          else
            sb.append(AUTHORIZED_VALUE).append(java.net.URLEncoder.encode(ar.getIdentifyingString(),"UTF-8")).append("\n");

          String[] acl = reply.getAccessTokens();
          if (acl != null)
          {
            if (aclneeded)
            {
              int j = 0;
              while (j < acl.length)
              {
                if (Logging.authorityService.isDebugEnabled())
                  Logging.authorityService.debug("  User '"+userID+"' has Acl = '"+acl[j]+"' from authority '"+ar.getIdentifyingString()+"'");
                sb.append(TOKEN_PREFIX).append(java.net.URLEncoder.encode(ac.getName(),"UTF-8")).append(":").append(java.net.URLEncoder.encode(acl[j++],"UTF-8")).append("\n");
              }
            }
          }
        }

        if (idneeded)
          sb.append(ID_PREFIX).append(java.net.URLEncoder.encode(userID,"UTF-8")).append("\n");

        byte[] responseValue = sb.toString().getBytes("ISO8859-1");

        response.setIntHeader("Content-Length", (int)responseValue.length);
        out.write(responseValue,0,responseValue.length);
        out.flush();
      }
      finally
      {
        out.close();
      }

      if (Logging.authorityService.isDebugEnabled())
        Logging.authorityService.debug("Done with request for '"+userID+"'");
    }
    catch (InterruptedException e)
    {
      // Shut down and don't bother to respond
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      Logging.authorityService.error("Unsupported encoding: "+e.getMessage(),e);
      throw new ServletException("Fatal error occurred: "+e.getMessage(),e);
    }
    catch (ManifoldCFException e)
    {
      Logging.authorityService.error("User ACL servlet error: "+e.getMessage(),e);
      response.sendError(response.SC_INTERNAL_SERVER_ERROR,e.getMessage());
    }
  }

}
