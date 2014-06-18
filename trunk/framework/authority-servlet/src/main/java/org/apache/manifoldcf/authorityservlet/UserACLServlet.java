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
package org.apache.manifoldcf.authorityservlet;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.ManifoldCF;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.authorities.system.RequestQueue;
import org.apache.manifoldcf.authorities.system.AuthRequest;
import org.apache.manifoldcf.authorities.system.MappingRequest;
import org.apache.manifoldcf.core.util.URLEncoder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


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

      Map<String,String> domainMap = new HashMap<String,String>();

      // Legacy mode: single user name with optional domain
      String userID = request.getParameter("username");
      if (userID != null)
      {
        String domain = request.getParameter("domain");
        if (domain == null)
          domain = "";
        domainMap.put(domain,userID);
      }
      
      // Now, go through enumerated username/domain pairs
      int q = 0;
      while (true)
      {
        String enumUserName = request.getParameter("username_"+q);
        if (enumUserName == null)
          break;
        String enumDomain = request.getParameter("domain_"+q);
        if (enumDomain == null)
          enumDomain = "";
        domainMap.put(enumDomain,enumUserName);
        q++;
      }
      
      if (domainMap.size() == 0)
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
        StringBuilder sb2 = new StringBuilder("[");
        boolean first = true;
        for (String domain : domainMap.keySet())
        {
          if (first)
            first = false;
          else
            sb2.append(",");
          sb2.append("'").append(domain).append("':'").append(domainMap.get(domain)).append("'");
        }
        sb2.append("]");
        Logging.authorityService.debug("Received authority request for domain:user set "+sb2.toString());
      }

      RequestQueue<MappingRequest> mappingQueue = ManifoldCF.getMappingRequestQueue();
      if (mappingQueue == null)
      {
        // System wasn't started; return unauthorized
        throw new ManifoldCFException("System improperly initialized");
      }

      RequestQueue<AuthRequest> queue = ManifoldCF.getRequestQueue();
      if (queue == null)
      {
        // System wasn't started; return unauthorized
        throw new ManifoldCFException("System improperly initialized");
      }

      
      IThreadContext itc = ThreadContextFactory.make();
      
      IMappingConnectionManager mappingConnManager = MappingConnectionManagerFactory.make(itc);
      IAuthorityConnectionManager authConnManager = AuthorityConnectionManagerFactory.make(itc);

      // Get all mapping connections; we may not need them all but we do need to be able to look them all up
      IMappingConnection[] mappingConnections = mappingConnManager.getAllConnections();
      
      // One thread per connection, which is responsible for starting the mapping process when it is ready.
      List<MappingOrderThread> mappingThreads = new ArrayList<MappingOrderThread>();
      // One thread per authority, which is responsible for starting the auth request when it is ready.
      List<AuthOrderThread> authThreads = new ArrayList<AuthOrderThread>();

      Map<MapperDescription,MappingRequest> mappingRequests = new HashMap<MapperDescription,MappingRequest>();
      Map<String,AuthRequest> authRequests = new HashMap<String,AuthRequest>();

      Map<String,IMappingConnection> mappingConnMap = new HashMap<String,IMappingConnection>();
      
      // Fill in mappingConnMap, since we need to be able to find connections given connection names
      for (IMappingConnection c : mappingConnections)
      {
        mappingConnMap.put(c.getName(),c);
      }

      // Set of connections we need to fire off
      Set<MapperDescription> activeConnections = new HashSet<MapperDescription>();

      // We do the minimal set of mapping requests and authorities.  Since it is the authority tokens we are
      // looking for, we start there, and build authority requests first, then mapping requests that support them,
      // etc.
      // Create auth requests
      for (String authDomain : domainMap.keySet())
      {
        IAuthorityConnection[] connections = authConnManager.getDomainConnections(authDomain);
        for (int i = 0; i < connections.length; i++)
        {
          IAuthorityConnection thisConnection = connections[i];
          String identifyingString = thisConnection.getDescription();
          if (identifyingString == null || identifyingString.length() == 0)
            identifyingString = thisConnection.getName();
          
          // Create a request
          AuthRequest ar = new AuthRequest(thisConnection,identifyingString);
          authRequests.put(thisConnection.getName(), ar);
          
          // We create an auth thread if there are prerequisites to meet.
          // Otherwise, we just fire off the request
          String domainUserID = domainMap.get(authDomain);
          if (thisConnection.getPrerequisiteMapping() == null)
          {
            ar.setUserID(domainUserID);
            queue.addRequest(ar);
          }
          else
          {
            MapperDescription md = new MapperDescription(thisConnection.getPrerequisiteMapping(),authDomain);
            AuthOrderThread thread = new AuthOrderThread(identifyingString,
              ar, md,
              queue, mappingRequests);
            authThreads.add(thread);
            // The same mapper can be used for multiple domains, although this is likely to be uncommon.  Nevertheless,
            // mapper invocations need to be segregated to prevent trouble
            activeConnections.add(md);
          }
        }
      }

      // Create mapping requests
      while (!activeConnections.isEmpty())
      {
        Iterator<MapperDescription> connectionIter = activeConnections.iterator();
        MapperDescription mapperDesc = connectionIter.next();
        String connectionName = mapperDesc.mapperName;
        String authDomain = mapperDesc.authDomain;
        IMappingConnection thisConnection = mappingConnMap.get(connectionName);
        String identifyingString = thisConnection.getDescription();
        if (identifyingString == null || identifyingString.length() == 0)
          identifyingString = connectionName;

        // Create a request
        MappingRequest mr = new MappingRequest(thisConnection,identifyingString);
        mappingRequests.put(mapperDesc, mr);

        // Either start up a thread, or just fire it off immediately.
        if (thisConnection.getPrerequisiteMapping() == null)
        {
          mr.setUserID(domainMap.get(authDomain));
          mappingQueue.addRequest(mr);
        }
        else
        {
          //System.out.println("Mapper: prerequisite found: '"+thisConnection.getPrerequisiteMapping()+"'");
          MapperDescription p = new MapperDescription(thisConnection.getPrerequisiteMapping(),authDomain);
          MappingOrderThread thread = new MappingOrderThread(identifyingString,
            mr, p, mappingQueue, mappingRequests);
          mappingThreads.add(thread);
          if (mappingRequests.get(p) == null)
            activeConnections.add(p);
        }
        activeConnections.remove(mapperDesc);
      }
      
      // Start threads.  We have to wait until all the requests have been
      // at least created before we do this.
      for (MappingOrderThread thread : mappingThreads)
      {
        thread.start();
      }
      for (AuthOrderThread thread : authThreads)
      {
        thread.start();
      }
      
      // Wait for the threads to finish up.  This will guarantee that all entities have run to completion.
      for (MappingOrderThread thread : mappingThreads)
      {
        thread.finishUp();
      }
      for (AuthOrderThread thread : authThreads)
      {
        thread.finishUp();
      }
      
      // This is probably unnecessary, but we do it anyway just to adhere to the contract
      for (MappingRequest mr : mappingRequests.values())
      {
        mr.waitForComplete();
      }
      
      // Handle all exceptions thrown during mapping.  In general this just means logging them, because
      // the downstream authorities will presumably not find what they are looking for and error out that way.
      for (MappingRequest mr : mappingRequests.values())
      {
        Throwable exception = mr.getAnswerException();
        if (exception != null)
        {
          Logging.authorityService.warn("Mapping exception logged from "+mr.getIdentifyingString()+": "+exception.getMessage()+"; mapper aborted", exception);
        }
      }
      
      // Now, work through the returning answers.

      // Ask all the interrogated authorities for their ACLs, and merge the final list together.
      StringBuilder sb = new StringBuilder();
      // Set response mime type
      response.setContentType("text/plain; charset=ISO8859-1");
      ServletOutputStream out = response.getOutputStream();
      try
      {
        for (String connectionName : authRequests.keySet())
        {
          AuthRequest ar = authRequests.get(connectionName);

          if (Logging.authorityService.isDebugEnabled())
            Logging.authorityService.debug("Waiting for answer from authority connection "+ar.getIdentifyingString()+" for user '"+ar.getUserID()+"'");

          ar.waitForComplete();

          if (Logging.authorityService.isDebugEnabled())
            Logging.authorityService.debug("Received answer from authority connection "+ar.getIdentifyingString()+" for user '"+ar.getUserID()+"'");

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

          String authGroup = ar.getAuthorityConnection().getAuthGroup();
          
          // A null reply means the same as USERNOTFOUND; it occurs because a user mapping failed somewhere.
          if (reply == null)
          {
            if (Logging.authorityService.isDebugEnabled())
              Logging.authorityService.debug("User '"+ar.getUserID()+"' mapping failed for authority '"+ar.getIdentifyingString()+"'");
            sb.append(USERNOTFOUND_VALUE).append(URLEncoder.encode(ar.getIdentifyingString())).append("\n");
          }
          else if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_UNREACHABLE)
          {
            Logging.authorityService.warn("Authority '"+ar.getIdentifyingString()+"' is unreachable for user '"+ar.getUserID()+"'");
            sb.append(UNREACHABLE_VALUE).append(URLEncoder.encode(ar.getIdentifyingString())).append("\n");
          }
          else if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_USERUNAUTHORIZED)
          {
            if (Logging.authorityService.isDebugEnabled())
              Logging.authorityService.debug("Authority '"+ar.getIdentifyingString()+"' does not authorize user '"+ar.getUserID()+"'");
            sb.append(UNAUTHORIZED_VALUE).append(URLEncoder.encode(ar.getIdentifyingString())).append("\n");
          }
          else if (reply.getResponseStatus() == AuthorizationResponse.RESPONSE_USERNOTFOUND)
          {
            if (Logging.authorityService.isDebugEnabled())
              Logging.authorityService.debug("User '"+ar.getUserID()+"' unknown to authority '"+ar.getIdentifyingString()+"'");
            sb.append(USERNOTFOUND_VALUE).append(URLEncoder.encode(ar.getIdentifyingString())).append("\n");
          }
          else
            sb.append(AUTHORIZED_VALUE).append(URLEncoder.encode(ar.getIdentifyingString())).append("\n");

          String[] acl = reply.getAccessTokens();
          if (acl != null)
          {
            if (aclneeded)
            {
              int j = 0;
              while (j < acl.length)
              {
                if (Logging.authorityService.isDebugEnabled())
                  Logging.authorityService.debug("  User '"+ar.getUserID()+"' has Acl = '"+acl[j]+"' from authority '"+ar.getIdentifyingString()+"'");
                sb.append(TOKEN_PREFIX).append(URLEncoder.encode(authGroup)).append(":").append(URLEncoder.encode(acl[j++])).append("\n");
              }
            }
          }
        }

        // Maintained for backwards compatibility only; no practical use that I can determine here
        if (idneeded && userID != null)
          sb.append(ID_PREFIX).append(URLEncoder.encode(userID)).append("\n");

        byte[] responseValue = sb.toString().getBytes(StandardCharsets.ISO_8859_1);


        response.setIntHeader("Content-Length", (int)responseValue.length);
        out.write(responseValue,0,responseValue.length);
        out.flush();
      }
      finally
      {
        out.close();
      }

      if (Logging.authorityService.isDebugEnabled())
      {
        StringBuilder sb2 = new StringBuilder("[");
        boolean first = true;
        for (String domain : domainMap.keySet())
        {
          if (first)
            first = false;
          else
            sb2.append(",");
          sb2.append("'").append(domain).append("':'").append(domainMap.get(domain)).append("'");
        }
        sb2.append("]");
        Logging.authorityService.debug("Done with request for domain:user set "+sb2.toString());
      }
    }
    catch (InterruptedException e)
    {
      // Shut down and don't bother to respond
    }
    catch (ManifoldCFException e)
    {
      Logging.authorityService.error("User ACL servlet error: "+e.getMessage(),e);
      response.sendError(response.SC_INTERNAL_SERVER_ERROR,e.getMessage());
    }
  }

  /** This class represents a tuple of (mapper_name, auth_domain).
  */
  protected static class MapperDescription
  {
    public final String mapperName;
    public final String authDomain;
    
    public MapperDescription(String mapperName, String authDomain)
    {
      this.mapperName = mapperName;
      this.authDomain = authDomain;
    }
    
    public int hashCode()
    {
      return mapperName.hashCode() + authDomain.hashCode();
    }
    
    public boolean equals(Object o)
    {
      if (!(o instanceof MapperDescription))
        return false;
      MapperDescription other = (MapperDescription)o;
      return this.mapperName.equals(other.mapperName) &&
        this.authDomain.equals(other.authDomain);
    }
  }
  
  /** This thread is responsible for making sure that the constraints for a given mapping connection
  * are met, and then when they are, firing off a MappingRequest.  One of these threads is spun up
  * for every IMappingConnection being handled.
  * NOTE WELL: The number of threads this might require is worrisome.  It is essentially
  * <number_of_app_server_threads> * <number_of_mappers>.  I will try later to see if I can find
  * a way of limiting this to sane numbers.
  */
  protected static class MappingOrderThread extends Thread
  {
    protected final MappingRequest request;
    protected final MapperDescription prerequisite;
    protected final Map<MapperDescription,MappingRequest> requests;
    protected final RequestQueue<MappingRequest> mappingRequestQueue;

    protected Throwable exception = null;
    
    public MappingOrderThread(
      String identifyingString,
      MappingRequest request,
      MapperDescription prerequisite,
      RequestQueue<MappingRequest> mappingRequestQueue,
      Map<MapperDescription, MappingRequest> requests)
    {
      super();
      this.request = request;
      this.prerequisite = prerequisite;
      this.mappingRequestQueue = mappingRequestQueue;
      this.requests = requests;
      setName("Constraint matcher for mapper '"+identifyingString+"'");
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        MappingRequest mappingRequest = requests.get(prerequisite);
        mappingRequest.waitForComplete();
        // Constraints are met.  Fire off the request.
        request.setUserID(mappingRequest.getAnswerResponse());
        mappingRequestQueue.addRequest(request);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException
    {
      join();
      if (exception != null)
      {
        if (exception instanceof Error)
          throw (Error)exception;
        else if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
      }
    }
    
  }

  /** This thread is responsible for making sure that the constraints for a given authority connection
  * are met, and then when they are, firing off an AuthRequest.  One of these threads is spun up
  * for every IAuthorityConnection being handled.
  * NOTE WELL: The number of threads this might require is worrisome.  It is essentially
  * <number_of_app_server_threads> * <number_of_authorities>.  I will try later to see if I can find
  * a way of limiting this to sane numbers.
  */
  protected static class AuthOrderThread extends Thread
  {
    protected final AuthRequest request;
    protected final MapperDescription prerequisite;
    protected final Map<MapperDescription,MappingRequest> mappingRequests;
    protected final RequestQueue<AuthRequest> authRequestQueue;
    
    protected Throwable exception = null;
    
    public AuthOrderThread(
      String identifyingString,
      AuthRequest request,
      MapperDescription prerequisite,
      RequestQueue<AuthRequest> authRequestQueue,
      Map<MapperDescription, MappingRequest> mappingRequests)
    {
      super();
      this.request = request;
      this.prerequisite = prerequisite;
      this.authRequestQueue = authRequestQueue;
      this.mappingRequests = mappingRequests;
      setName("Constraint matcher for authority '"+identifyingString+"'");
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        MappingRequest mappingRequest = mappingRequests.get(prerequisite);
        mappingRequest.waitForComplete();
        // Constraints are met.  Fire off the request.  User may be null if mapper failed!!
        request.setUserID(mappingRequest.getAnswerResponse());
        authRequestQueue.addRequest(request);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException
    {
      join();
      if (exception != null)
      {
        if (exception instanceof Error)
          throw (Error)exception;
        else if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
      }
    }
    
  }
  
}
