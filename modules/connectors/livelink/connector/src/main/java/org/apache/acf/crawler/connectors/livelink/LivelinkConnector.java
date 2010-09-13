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
package org.apache.acf.crawler.connectors.livelink;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import org.apache.acf.crawler.system.ACF;

import java.io.*;
import java.util.*;
import java.net.*;

import com.opentext.api.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.protocol.*;


/** This is the Livelink implementation of the IRepositoryConnectr interface.
* The original Volant code forced there to be one livelink session per JVM, with
* lots of buggy synchronization present to try to enforce this.  This implementation
* is multi-session.  However, since it is possible that the Volant restriction was
* indeed needed, I have attempted to structure things to allow me to turn on
* single-session if needed.
*
* For livelink, the document identifiers are the object identifiers.
*
*/
public class LivelinkConnector extends org.apache.acf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Activities we will report on
  private final static String ACTIVITY_SEED = "find documents";
  private final static String ACTIVITY_FETCH = "fetch document";

  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  // Livelink does not have "deny" permissions, and there is no such thing as a document with no tokens, so it is safe to not have a local "deny" token.
  // However, people feel that a suspenders-and-belt approach is called for, so this restriction has been added.
  // Livelink tokens are numbers, "SYSTEM", or "GUEST", so they can't collide with the standard form.
  private static final String denyToken = "DEAD_AUTHORITY";

  // A couple of very important points.
  // First, the canonical document identifier has the following form:
  // <D|F>[<volume_id>:]<object_id>
  // Second, the only LEGAL objects for a document identifier to describe
  // are folders, documents, and volume objects.  Project objects are NOT
  // allowed; they must be mapped to the appropriate volume object before
  // being returned to the crawler.

  // Signal that we have set up a connection properly
  private boolean hasBeenSetup = false;

  // Data required for maintaining livelink connection
  private LAPI_DOCUMENTS LLDocs = null;
  private LAPI_ATTRIBUTES LLAttributes = null;
  private LLSERVER llServer = null;
  private int LLENTWK_VOL;
  private int LLENTWK_ID;
  private int LLCATWK_VOL;
  private int LLCATWK_ID;

  // Parameter values we need
  private String serverName = null;
  private int serverPort = -1;
  private String serverUsername = null;
  private String serverPassword = null;

  private String ingestProtocol = null;
  private String ingestPort = null;
  private String ingestCgiPath = null;

  private String viewProtocol = null;
  private String viewServerName = null;
  private String viewPort = null;
  private String viewCgiPath = null;

  private String ntlmDomain = null;
  private String ntlmUsername = null;
  private String ntlmPassword = null;

  // SSL support
  private String keystoreData = null;
  private IKeystoreManager keystoreManager = null;
  private LivelinkSecureSocketFactory secureSocketFactory = null;
  private ProtocolFactory myFactory = null;

  // Connection management
  private MultiThreadedHttpConnectionManager connectionManager = null;

  // Base path for viewing
  private String viewBasePath = null;

  // Ingestion port number
  private int ingestPortNumber = -1;

  // Activities list
  private static final String[] activitiesList = new String[]{ACTIVITY_SEED,ACTIVITY_FETCH};

  // Retry count.  This is so we can try to install some measure of sanity into situations where LAPI gets confused communicating to the server.
  // So, for some kinds of errors, we just retry for a while hoping it will go away.
  private static final int FAILURE_RETRY_COUNT = 10;

  // Current host name
  private static String currentHost = null;
  private static java.net.InetAddress currentAddr = null;
  static
  {
    // Find the current host name
    try
    {
      currentAddr = java.net.InetAddress.getLocalHost();

      // Get hostname
      currentHost = currentAddr.getHostName();
    }
    catch (UnknownHostException e)
    {
    }
  }


  /** Constructor.
  */
  public LivelinkConnector()
  {
  }


  /** Return the path for the UI interface JSP elements.
  * These JSP's must be provided to allow the connector to be configured, and to
  * permit it to present document filtering specification information in the UI.
  * This method should return the name of the folder, under the <webapp>/connectors/
  * area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
  *@return the folder part
  */
  public String getJSPFolder()
  {
    return "livelink";
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // This is required by getBins()
    serverName = params.getParameter(LiveLinkParameters.serverName);
  }

  protected class GetSessionThread extends Thread
  {
    protected Throwable exception = null;

    public GetSessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Create the session
        llServer = new LLSERVER(serverName,serverPort,serverUsername,serverPassword);

        LLDocs = new LAPI_DOCUMENTS(llServer.getLLSession());
        LLAttributes = new LAPI_ATTRIBUTES(llServer.getLLSession());
        if (Logging.connectors.isDebugEnabled())
        {
          String passwordExists = (serverPassword!=null&&serverPassword.length()>0)?"password exists":"";
          Logging.connectors.debug("Livelink: Livelink Session: Server='"+serverName+"'; port='"+serverPort+"'; user name='"+serverUsername+"'; "+passwordExists);
        }
        LLValue entinfo = new LLValue().setAssoc();

        int status;
        status = LLDocs.AccessEnterpriseWS(entinfo);
        if (status == 0)
        {
          LLENTWK_ID = entinfo.toInteger("ID");
          LLENTWK_VOL = entinfo.toInteger("VolumeID");
        }
        else
          throw new ACFException("Error accessing enterprise workspace: "+status);

        entinfo = new LLValue().setAssoc();
        status = LLDocs.AccessCategoryWS(entinfo);
        if (status == 0)
        {
          LLCATWK_ID = entinfo.toInteger("ID");
          LLCATWK_VOL = entinfo.toInteger("VolumeID");
        }
        else
          throw new ACFException("Error accessing category workspace: "+status);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }
  }

  /** Get the bin name string for a document identifier.  The bin name describes the queue to which the
  * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  *@param documentIdentifier is the document identifier.
  *@return the bin name.
  */
  public String[] getBinNames(String documentIdentifier)
  {
    // This should return server name
    return new String[]{serverName};
  }

  protected void getSession()
    throws ACFException, ServiceInterruption
  {
    if (hasBeenSetup == false)
    {
      // Do the initial setup part (what used to be part of connect() itself)

      // Get the parameters
      ingestProtocol = params.getParameter(LiveLinkParameters.ingestProtocol);
      ingestPort = params.getParameter(LiveLinkParameters.ingestPort);
      ingestCgiPath = params.getParameter(LiveLinkParameters.ingestCgiPath);

      viewProtocol = params.getParameter(LiveLinkParameters.viewProtocol);
      viewServerName = params.getParameter(LiveLinkParameters.viewServerName);
      viewPort = params.getParameter(LiveLinkParameters.viewPort);
      viewCgiPath = params.getParameter(LiveLinkParameters.viewCgiPath);

      ntlmDomain = params.getParameter(LiveLinkParameters.ntlmDomain);
      ntlmUsername = params.getParameter(LiveLinkParameters.ntlmUsername);
      ntlmPassword = params.getObfuscatedParameter(LiveLinkParameters.ntlmPassword);

      String serverPortString = params.getParameter(LiveLinkParameters.serverPort);
      serverUsername = params.getParameter(LiveLinkParameters.serverUsername);
      serverPassword = params.getObfuscatedParameter(LiveLinkParameters.serverPassword);

      if (ingestProtocol == null || ingestProtocol.length() == 0)
        ingestProtocol = "http";
      if (viewProtocol == null || viewProtocol.length() == 0)
        viewProtocol = ingestProtocol;

      if (ingestPort == null || ingestPort.length() == 0)
      {
        if (!ingestProtocol.equals("https"))
          ingestPort = "80";
        else
          ingestPort = "443";
      }

      if (viewPort == null || viewPort.length() == 0)
      {
        if (!viewProtocol.equals(ingestProtocol))
        {
          if (!viewProtocol.equals("https"))
            viewPort = "80";
          else
            viewPort = "443";
        }
        else
          viewPort = ingestPort;
      }

      try
      {
        ingestPortNumber = Integer.parseInt(ingestPort);
      }
      catch (NumberFormatException e)
      {
        throw new ACFException("Bad ingest port: "+e.getMessage(),e);
      }

      String viewPortString;
      try
      {
        int portNumber = Integer.parseInt(viewPort);
        viewPortString = ":" + Integer.toString(portNumber);
        if (!viewProtocol.equals("https"))
        {
          if (portNumber == 80)
            viewPortString = "";
        }
        else
        {
          if (portNumber == 443)
            viewPortString = "";
        }
      }
      catch (NumberFormatException e)
      {
        throw new ACFException("Bad view port: "+e.getMessage(),e);
      }

      if (viewCgiPath == null || viewCgiPath.length() == 0)
        viewCgiPath = ingestCgiPath;

      if (ntlmDomain != null && ntlmDomain.length() == 0)
        ntlmDomain = null;
      if (ntlmDomain == null)
      {
        ntlmUsername = null;
        ntlmPassword = null;
      }
      else
      {
        if (ntlmUsername == null || ntlmUsername.length() == 0)
        {
          ntlmUsername = serverUsername;
          if (ntlmPassword == null || ntlmPassword.length() == 0)
            ntlmPassword = serverPassword;
        }
        else
        {
          if (ntlmPassword == null)
            ntlmPassword = "";
        }
      }

      // First, create server object (llServer)

      if (serverPortString == null)
        serverPort = 2099;
      else
        serverPort = new Integer(serverPortString).intValue();

      // Set up ssl if indicated
      keystoreData = params.getParameter(LiveLinkParameters.livelinkKeystore);
      myFactory = new ProtocolFactory();

      if (keystoreData != null)
      {
        keystoreManager = KeystoreManagerFactory.make("",keystoreData);
        secureSocketFactory = new LivelinkSecureSocketFactory(keystoreManager.getSecureSocketFactory());
        Protocol myHttpsProtocol = new Protocol("https", (ProtocolSocketFactory)secureSocketFactory, 443);
        myFactory.registerProtocol("https",myHttpsProtocol);
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Created new secure protocol class instance; factory type is '"+myHttpsProtocol.getSocketFactory().getClass().getName()+"'");
        }
      }

      // Set up connection manager
      connectionManager = new MultiThreadedHttpConnectionManager();
      connectionManager.getParams().setMaxTotalConnections(1);

      // System.out.println("Connection server object = "+llServer.toString());

      // Establish the actual connection
      int sanityRetryCount = FAILURE_RETRY_COUNT;
      while (true)
      {
        GetSessionThread t = new GetSessionThread();
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else if (thr instanceof ACFException)
              throw (ACFException)thr;
            else
              throw (Error)thr;
          }
          if (viewServerName == null || viewServerName.length() == 0)
            viewServerName = llServer.getHost();

          viewBasePath = viewProtocol+"://"+viewServerName+viewPortString+viewCgiPath;
          hasBeenSetup = true;
          return;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
        }
        catch (RuntimeException e2)
        {
          sanityRetryCount = handleLivelinkRuntimeException(e2,sanityRetryCount,true);
        }
      }
    }
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  protected static class ExecuteMethodThread extends Thread
  {
    protected HttpClient client;
    protected HttpMethodBase executeMethod;
    protected HostConfiguration hostConfiguration;
    protected Throwable exception = null;
    protected int rval = 0;

    public ExecuteMethodThread(HttpClient client, HostConfiguration hostConfiguration, HttpMethodBase executeMethod)
    {
      super();
      setDaemon(true);
      this.client = client;
      this.hostConfiguration = hostConfiguration;
      this.executeMethod = executeMethod;
    }

    public void run()
    {
      try
      {
        // Call the execute method appropriately
        rval = client.executeMethod(hostConfiguration,executeMethod,null);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public int getResponse()
    {
      return rval;
    }
  }

  protected static int executeMethodViaThread(HttpClient client, HostConfiguration hostConfiguration, HttpMethodBase executeMethod)
    throws InterruptedException, IOException
  {
    ExecuteMethodThread t = new ExecuteMethodThread(client,hostConfiguration,executeMethod);
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof IOException)
          throw (IOException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else
          throw (Error)thr;
      }
      return t.getResponse();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
      throw e;
    }
  }

  /** Check status of connection.
  */
  public String check()
    throws ACFException
  {
    try
    {
      // Destroy saved session setup and repeat it
      hasBeenSetup = false;
      getSession();

      // Now, set up trial of ingestion connection
      String contextMsg = "for document access";
      String ingestHttpAddress = ingestCgiPath;

      HttpClient client = getInitializedClient(contextMsg);

      try
      {
        // Set up fetch using our special stuff if it's https
        GetMethod method = new GetMethod(ingestHttpAddress);
        try
        {
          method.getParams().setParameter("http.socket.timeout", new Integer(300000));
          method.setFollowRedirects(true);

          int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),method);
          switch (statusCode)
          {
          case 502:
            return "Fetch test had transient 502 error response";

          case HttpStatus.SC_UNAUTHORIZED:
            return "Fetch test returned UNAUTHORIZED (401) response; check the security credentials and configuration";

          case HttpStatus.SC_OK:
            return super.check();

          default:
            return "Fetch test returned an unexpected response code of "+Integer.toString(statusCode);
          }
        }
        catch (InterruptedException e)
        {
          // Drop the connection on the floor
          method = null;
          throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
        }
        catch (java.net.SocketTimeoutException e)
        {
          return "Fetch test timed out reading from the Livelink HTTP Server: "+e.getMessage();
        }
        catch (java.net.SocketException e)
        {
          return "Fetch test received a socket error reading from Livelink HTTP Server: "+e.getMessage();
        }
        catch (javax.net.ssl.SSLHandshakeException e)
        {
          return "Fetch test was unable to set up a SSL connection to Livelink HTTP Server: "+e.getMessage();
        }
        catch (org.apache.commons.httpclient.ConnectTimeoutException e)
        {
          return "Fetch test connection timed out reading from Livelink HTTP Server: "+e.getMessage();
        }
        catch (InterruptedIOException e)
        {
          throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
        }
        catch (IOException e)
        {
          return "Fetch test had an IO failure: "+e.getMessage();
        }
        finally
        {
          if (method != null)
            method.releaseConnection();
        }
      }
      catch (IllegalStateException e)
      {
        return "Fetch test had a state exception talking to Livelink HTTP Server: "+e.getMessage();
      }
    }
    catch (ServiceInterruption e)
    {
      return "Transient error: "+e.getMessage();
    }
    catch (ACFException e)
    {
      if (e.getErrorCode() == ACFException.INTERRUPTED)
        throw e;
      return "Error: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws ACFException
  {
    if (connectionManager != null)
      connectionManager.closeIdleConnections(60000L);
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws ACFException
  {
    llServer.disconnect();
    hasBeenSetup = false;
    llServer = null;
    LLDocs = null;
    LLAttributes = null;
    keystoreData = null;
    keystoreManager = null;
    secureSocketFactory = null;
    myFactory = null;
    ingestPortNumber = -1;

    serverName = null;
    serverPort = -1;
    serverUsername = null;
    serverPassword = null;

    ingestPort = null;
    ingestProtocol = null;
    ingestCgiPath = null;

    viewPort = null;
    viewServerName = null;
    viewProtocol = null;
    viewCgiPath = null;

    viewBasePath = null;

    ntlmDomain = null;
    ntlmUsername = null;
    ntlmPassword = null;

    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;

    super.disconnect();
  }

  /** List the activities we might report on.
  */
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Convert a document identifier to a relative URI to read data from.  This is not the search URI; that's constructed
  * by a different method.
  *@param documentIdentifier is the document identifier.
  *@return the relative document uri.
  */
  protected String convertToIngestURI(String documentIdentifier)
    throws ACFException
  {
    // The document identifier is the string form of the object ID for this connector.
    if (!documentIdentifier.startsWith("D"))
      return null;
    int colonPosition = documentIdentifier.indexOf(":",1);
    if (colonPosition == -1)
      return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(1)+"&objAction=download";
    else
      return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(colonPosition+1)+"&objAction=download";
  }

  /** Convert a document identifier to a URI to view.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.  It must therefore
  * be a unique way of describing the document.
  *@param documentIdentifier is the document identifier.
  *@return the document uri.
  */
  protected String convertToViewURI(String documentIdentifier)
    throws ACFException
  {
    // The document identifier is the string form of the object ID for this connector.
    if (!documentIdentifier.startsWith("D"))
      return null;
    int colonPosition = documentIdentifier.indexOf(":",1);
    if (colonPosition == -1)
      return viewBasePath+"?func=ll&objID="+documentIdentifier.substring(1)+"&objAction=download";
    else
      return viewBasePath+"?func=ll&objID="+documentIdentifier.substring(colonPosition+1)+"&objAction=download";
  }

  /** Convert a document identifier to an object ID.  MUST be a simple document, not a folder or project.
  *@param documentIdentifier is the document identifier.
  *@return the object id, or -1 if documentIdentifier does not describe a document.
  */
  protected static int convertToObjectID(String documentIdentifier)
    throws ACFException
  {
    if (!documentIdentifier.startsWith("D"))
      return -1;
    int colonPosition = documentIdentifier.indexOf(":",1);
    try
    {
      if (colonPosition == -1)
        return Integer.parseInt(documentIdentifier.substring(1));
      else
        return Integer.parseInt(documentIdentifier.substring(colonPosition+1));
    }
    catch (NumberFormatException e)
    {
      throw new ACFException("Bad document identifier: "+e.getMessage(),e);
    }
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  public boolean requestInfo(Configuration output, String command)
    throws ACFException
  {
    if (command.equals("workspaces"))
    {
      try
      {
        String[] workspaces = getWorkspaceNames();
        int i = 0;
        while (i < workspaces.length)
        {
          String workspace = workspaces[i++];
          ConfigurationNode node = new ConfigurationNode("workspace");
          node.setValue(workspace);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("folders/"))
    {
      String path = command.substring("folders/".length());
      
      try
      {
        String[] folders = getChildFolderNames(path);
        int i = 0;
        while (i < folders.length)
        {
          String folder = folders[i++];
          ConfigurationNode node = new ConfigurationNode("folder");
          node.setValue(folder);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("categories/"))
    {
      String path = command.substring("categories/".length());

      try
      {
        String[] categories = getChildCategoryNames(path);
        int i = 0;
        while (i < categories.length)
        {
          String category = categories[i++];
          ConfigurationNode node = new ConfigurationNode("category");
          node.setValue(category);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
      }

    }
    else if (command.startsWith("categoryattributes/"))
    {
      String path = command.substring("categoryattributes/".length());

      try
      {
        String[] attributes = getCategoryAttributes(path);
        int i = 0;
        while (i < attributes.length)
        {
          String attribute = attributes[i++];
          ConfigurationNode node = new ConfigurationNode("attribute");
          node.setValue(attribute);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
      }
    }
    else
      return super.requestInfo(output,command);
    return true;
  }
  
  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws ACFException, ServiceInterruption
  {
    getSession();

    // First, grab the root LLValue
    LLValue rootValue = getObjectInfo(LLENTWK_VOL,LLENTWK_ID);
    if (rootValue == null)
    {
      // If we get here, it HAS to be a bad network/transient problem.
      Logging.connectors.warn("Livelink: Could not look up root workspace object during seeding!  Retrying -");
      throw new ServiceInterruption("Service interruption during seeding",new ACFException("Could not looking root workspace object during seeding"),System.currentTimeMillis()+60000L,
        System.currentTimeMillis()+600000L,-1,true);
    }

    // Walk the specification for the "startpoint" types.  Amalgamate these into a list of strings.
    // Presume that all roots are startpoint nodes
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);
      if (n.getType().equals("startpoint"))
      {
        // The id returned is simply the node path, which can't be messed up
        long beginTime = System.currentTimeMillis();
        String path = n.getAttributeValue("path");
        VolumeAndId vaf = getPathId(rootValue,path);
        if (vaf != null)
        {
          activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
            path,"OK",null,null);

          String newID = "F" + new Integer(vaf.getVolumeID()).toString()+":"+ new Integer(vaf.getPathId()).toString();
          activities.addSeedDocument(newID);
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Seed = '"+newID+"'");
        }
        else
        {
          activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
            path,"NOT FOUND",null,null);
        }
      }
      i++;
    }

  }


  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ACFException, ServiceInterruption
  {
    getSession();

    // First, process the spec to get the string we tack on

    // Read the forced acls.  A null return indicates that security is disabled!!!
    // A zero-length return indicates that the native acls should be used.
    // All of this is germane to how we ingest the document, so we need to note it in
    // the version string completely.
    String[] acls = getAcls(spec);
    // Sort it, in case it is needed.
    if (acls != null)
      java.util.Arrays.sort(acls);

    // Look at the metadata attributes.
    // So that the version strings are comparable, we will put them in an array first, and sort them.
    String pathAttributeName = null;
    HashMap holder = new HashMap();
    MatchMap matchMap = new MatchMap();
    boolean includeAllMetadata = false;
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals("allmetadata"))
      {
        String isAll = n.getAttributeValue("all");
        if (isAll != null && isAll.equals("true"))
          includeAllMetadata = true;
      }
      else if (n.getType().equals("metadata"))
      {
        String category = n.getAttributeValue("category");
        String attributeName = n.getAttributeValue("attribute");
        String isAll = n.getAttributeValue("all");
        if (isAll != null && isAll.equals("true"))
        {
          // Locate all metadata items for the specified category path,
          // and enter them into the array
          String[] attrs = getCategoryAttributes(category);
          if (attrs != null)
          {
            int j = 0;
            while (j < attrs.length)
            {
              attributeName = attrs[j++];
              String metadataName = packCategoryAttribute(category,attributeName);
              holder.put(metadataName,metadataName);
            }
          }
        }
        else
        {
          String metadataName = packCategoryAttribute(category,attributeName);
          holder.put(metadataName,metadataName);
        }
      }
      else if (n.getType().equals("pathnameattribute"))
        pathAttributeName = n.getAttributeValue("value");
      else if (n.getType().equals("pathmap"))
      {
        // Path mapping info also needs to be looked at, because it affects what is
        // ingested.
        String pathMatch = n.getAttributeValue("match");
        String pathReplace = n.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }

    }

    // Prepare the specified metadata
    StringBuffer metadataString = null;
    CategoryPathAccumulator catAccum = null;
    if (!includeAllMetadata)
    {
      metadataString = new StringBuffer();
      // Put into an array
      String[] sortArray = new String[holder.size()];
      i = 0;
      Iterator iter = holder.keySet().iterator();
      while (iter.hasNext())
      {
        sortArray[i++] = (String)iter.next();
      }

      // Sort!
      java.util.Arrays.sort(sortArray);
      // Build the metadata string piece now
      packList(metadataString,sortArray,'+');
    }
    else
      catAccum = new CategoryPathAccumulator();

    // Calculate the part of the version string that comes from path name and mapping.
    // This starts with = since ; is used by another optional component (the forced acls)
    StringBuffer pathNameAttributeVersion = new StringBuffer();
    if (pathAttributeName != null)
      pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

    // The version string includes the following:
    // 1) The modify date for the document
    // 2) The rights for the document, ordered (which can change without changing the ModifyDate field)
    // 3) The requested metadata fields (category and attribute, ordered) for the document
    //
    // The document identifiers are object id's.

    String[] rval = new String[documentIdentifiers.length];
    i = 0;
    while (i < documentIdentifiers.length)
    {
      // Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
      activities.checkJobStillActive();

      // Read the document or folder metadata, which includes the ModifyDate
      String docID = documentIdentifiers[i];

      boolean isFolder = docID.startsWith("F");

      int colonPos = docID.indexOf(":",1);

      int objID;
      int vol;

      if (colonPos == -1)
      {
        objID = new Integer(docID.substring(1)).intValue();
        vol = LLENTWK_VOL;
      }
      else
      {
        objID = new Integer(docID.substring(colonPos+1)).intValue();
        vol = new Integer(docID.substring(1,colonPos)).intValue();
      }

      rval[i] = null;
      LLValue value = getObjectInfo(vol,objID);
      if (value != null)
      {
        // Make sure we have permission to see the object's contents
        int permissions = value.toInteger("Permissions");
        if ((permissions & LAPI_DOCUMENTS.PERM_SEECONTENTS) != 0)
        {
          Date dt = value.toDate("ModifyDate");
          // The rights don't change when the object changes, so we have to include those too.
          int[] rights = getObjectRights(vol,objID);
          if (rights != null)
          {
            // We were able to get rights, so object still exists.

            // I rearranged this on 11/7/2006 so that it would be more parseable, since
            // we want to pull the transient information out of the version string where
            // possible (so there is no mismatch between version checking and ingestion).

            StringBuffer sb = new StringBuffer();

            // On 1/17/2008 I changed the version generation code to NOT include metadata, view info, etc. for folders, since
            // folders make absolutely no use of this info.
            if (!isFolder)
            {
              if (includeAllMetadata)
              {
                // Find all the metadata associated with this object, and then
                // find the set of category pathnames that correspond to it.
                int[] catIDs = getObjectCategoryIDs(vol,objID);
                String[] categoryPaths = catAccum.getCategoryPathsAttributeNames(catIDs);
                // Sort!
                java.util.Arrays.sort(categoryPaths);
                // Build the metadata string piece now
                packList(sb,categoryPaths,'+');
              }
              else
                sb.append(metadataString);
            }

            String[] actualAcls;
            String denyAcl;
            if (acls != null && acls.length == 0)
            {
              // No forced acls.  Read the actual acls from livelink, as a set of rights.
              // We need also to add in support for the special rights objects.  These are:
              // -1: RIGHT_WORLD
              // -2: RIGHT_SYSTEM
              // -3: RIGHT_OWNER
              // -4: RIGHT_GROUP
              //
              // RIGHT_WORLD means guest access.
              // RIGHT_SYSTEM is "Public Access".
              // RIGHT_OWNER is access by the owner of the object.
              // RIGHT_GROUP is access by a member of the base group containing the owner
              //
              // These objects are returned by the GetObjectRights() call made above, and NOT
              // returned by LLUser.ListObjects().  We have to figure out how to map these to
              // things that are
              // the equivalent of acls.

              actualAcls = lookupTokens(rights, vol, objID);
              java.util.Arrays.sort(actualAcls);
              // If security is on, no deny acl is needed for the local authority, since the repository does not support "deny".  But this was added
              // to be really really really sure.
              denyAcl = denyToken;

            }
            else if (acls != null && acls.length > 0)
            {
              // Forced acls
              actualAcls = acls;
              denyAcl = defaultAuthorityDenyToken;
            }
            else
            {
              // Security is OFF
              actualAcls = acls;
              denyAcl = null;
            }

            // Now encode the acls.  If null, we write a special value.
            if (actualAcls == null)
              sb.append('-');
            else
            {
              sb.append('+');
              packList(sb,actualAcls,'+');
              // This was added on 4/21/2008 to support forced acls working with the global default authority.
              pack(sb,denyAcl,'+');
            }

            // The date does not need to be parseable
            sb.append(dt.toString());

            if (!isFolder)
            {
              // PathNameAttributeVersion comes completely from the spec, so we don't
              // have to worry about it changing.  No need, therefore, to parse it during
              // processDocuments.
              sb.append("=").append(pathNameAttributeVersion);

              // Tack on ingestCgiPath, to insulate us against changes to the repository connection setup.  Added 9/7/07.
              sb.append("_").append(viewBasePath);
            }

            rval[i] = sb.toString();
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Successfully calculated version string for object "+Integer.toString(vol)+":"+Integer.toString(objID)+" : '"+rval[i]+"'");
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Could not get rights for object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
          }
        }
        else
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Crawl user cannot see contents of object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
        }
      }
      else
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(objID)+" has no information - deleting");
      }
      i++;

    }
    return rval;
  }

  protected class ListObjectsThread extends Thread
  {
    protected int vol;
    protected int objID;
    protected String filterString;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public ListObjectsThread(int vol, int objID, String filterString)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.objID = objID;
      this.filterString = filterString;
    }

    public void run()
    {
      try
      {
        LLValue childrenDocs = new LLValue();
        int status = LLDocs.ListObjects(vol, objID, null, filterString, LAPI_DOCUMENTS.PERM_SEECONTENTS, childrenDocs);
        if (status != 0)
        {
          throw new ACFException("Error retrieving contents of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : Status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = childrenDocs;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  */
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ACFException, ServiceInterruption
  {
    getSession();

    // First, initialize the table of catid's.
    // Keeping this around will allow us to benefit from batching of documents.
    MetadataDescription desc = new MetadataDescription();

    // Build the node/path cache
    SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

    int i = 0;
    while (i < documentIdentifiers.length)
    {
      // Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
      activities.checkJobStillActive();
      String documentIdentifier = documentIdentifiers[i];
      boolean doScanOnly = scanOnly[i];

      boolean isFolder = documentIdentifier.startsWith("F");
      int colonPosition = documentIdentifier.indexOf(":",1);
      int vol;
      int objID;
      if (colonPosition == -1)
      {
        vol = LLENTWK_VOL;
        objID = new Integer(documentIdentifier.substring(1)).intValue();
      }
      else
      {
        vol = new Integer(documentIdentifier.substring(1,colonPosition)).intValue();
        objID = new Integer(documentIdentifier.substring(colonPosition+1)).intValue();
      }

      if (isFolder)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));

        // Since the identifier indicates it is a directory, then queue up all the current children which pass the filter.
        String filterString = buildFilterString(spec);

        int sanityRetryCount = FAILURE_RETRY_COUNT;
        while (true)
        {
          ListObjectsThread t = new ListObjectsThread(vol,objID,filterString);
          try
          {
            t.start();
            t.join();
            Throwable thr = t.getException();
            if (thr != null)
            {
              if (thr instanceof RuntimeException)
                throw (RuntimeException)thr;
              else if (thr instanceof ACFException)
              {
                sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
                continue;
              }
              else
                throw (Error)thr;
            }

            LLValue childrenDocs = t.getResponse();

            int size = 0;

            if (childrenDocs.isRecord())
              size = 1;
            if (childrenDocs.isTable())
              size = childrenDocs.size();

            // System.out.println("Total child count = "+Integer.toString(size));

            // Do the scan
            int j = 0;
            while (j < size)
            {
              int childID = childrenDocs.toInteger(j, "ID");

              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Livelink: Found a child of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : ID="+Integer.toString(childID));

              int subtype = childrenDocs.toInteger(j, "SubType");
              boolean childIsFolder = (subtype == LAPI_DOCUMENTS.FOLDERSUBTYPE || subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE ||
                subtype == LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE);

              // If it's a folder, we just let it through for now
              if (!childIsFolder && checkInclude(childrenDocs.toString(j,"Name") + "." + childrenDocs.toString(j,"FileType"), spec) == false)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" was excluded by inclusion criteria");
                j++;
                continue;
              }

              if (childIsFolder)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a folder, project, or compound document; adding a reference");
                if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
                {
                  // If we pick up a project object, we need to describe the volume object (which
                  // will be the root of all documents beneath)
                  activities.addDocumentReference("F"+new Integer(childID).toString()+":"+new Integer(-childID).toString());
                }
                else
                  activities.addDocumentReference("F"+new Integer(vol).toString()+":"+new Integer(childID).toString());
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a simple document; adding a reference");

                activities.addDocumentReference("D"+new Integer(vol).toString()+":"+new Integer(childID).toString());
              }

              j++;
            }
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
          }
          catch (RuntimeException e)
          {
            sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
            continue;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Done processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));
      }
      else
      {
        // It's a known file, and we've already checked whether it's allowed or not (except any
        // checks based on the file data)

        if (doScanOnly == false)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
          if (checkIngest(objID,spec))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Decided to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID));

            // Grab the access tokens for this file from the version string, inside ingest method.
            ingestFromLiveLink(documentIdentifiers[i],versions[i],activities,desc,sDesc);
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Decided not to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID)+" - Did not match ingestion criteria");

          }
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Done processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
        }
      }
      i++;
    }
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  public int getMaxDocumentRequest()
  {
    // Intrinsically, Livelink doesn't batch well.  Multiple chunks have no advantage over one-at-a-time requests,
    // since apparently the Livelink API does not support multiples.  HOWEVER - when metadata is considered,
    // it becomes worthwhile, because we will be able to do what is needed to look up the correct CATID node
    // only once per n requests!  So it's a tradeoff between the advantage gained by threading, and the
    // savings gained by CATID lookup.
    // Note that at Shell, the fact that the network hiccups a lot makes it better to choose a smaller value.
    return 6;
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, ArrayList tabsArray)
    throws ACFException, IOException
  {
    tabsArray.add("Server");
    tabsArray.add("Document Access");
    tabsArray.add("Document View");
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function LLDeleteCertificate(aliasName)\n"+
"{\n"+
"  editconnection.llkeystorealias.value = aliasName;\n"+
"  editconnection.configop.value = \"Delete\";\n"+
"  postForm();\n"+
"}\n"+
"\n"+
"function LLAddCertificate()\n"+
"{\n"+
"  if (editconnection.llcertificate.value == \"\")\n"+
"  {\n"+
"    alert(\"Choose a certificate file\");\n"+
"    editconnection.llcertificate.focus();\n"+
"  }\n"+
"  else\n"+
"  {\n"+
"    editconnection.configop.value = \"Add\";\n"+
"    postForm();\n"+
"  }\n"+
"}\n"+
"\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"A valid number is required\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ingestport.value != \"\" && !isInteger(editconnection.ingestport.value))\n"+
"  {\n"+
"    alert(\"A valid number, or blank, is required\");\n"+
"    editconnection.ingestport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.viewport.value != \"\" && !isInteger(editconnection.viewport.value))\n"+
"  {\n"+
"    alert(\"A valid number, or blank, is required\");\n"+
"    editconnection.viewport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\"Enter a livelink server name\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value == \"\")\n"+
"  {\n"+
"    alert(\"A server port number is required\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ingestcgipath.value == \"\")\n"+
"  {\n"+
"    alert(\"Enter the crawl cgi path to livelink\");\n"+
"    SelectTab(\"Document Access\");\n"+
"    editconnection.ingestcgipath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.ingestcgipath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"The ingest cgi path must begin with a / character\");\n"+
"    SelectTab(\"Document Access\");\n"+
"    editconnection.ingestcgipath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.viewcgipath.value != \"\" && editconnection.viewcgipath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"The view cgi path must be blank, or begin with a / character\");\n"+
"    SelectTab(\"Document View\");\n"+
"    editconnection.viewcgipath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ACFException, IOException
  {
    String ingestProtocol = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestProtocol);
    if (ingestProtocol == null)
      ingestProtocol = "http";
    String ingestPort = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestPort);
    if (ingestPort == null)
      ingestPort = "";
    String ingestCgiPath = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestCgiPath);
    if (ingestCgiPath == null)
      ingestCgiPath = "/livelink/livelink.exe";
    String viewProtocol = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewProtocol);
    if (viewProtocol == null)
      viewProtocol = "";
    String viewServerName = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewServerName);
    if (viewServerName == null)
      viewServerName = "";
    String viewPort = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewPort);
    if (viewPort == null)
      viewPort = "";
    String viewCgiPath = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewCgiPath);
    if (viewCgiPath == null)
      viewCgiPath = "";
    String serverName = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverName);
    if (serverName == null)
      serverName = "localhost";
    String serverPort = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverPort);
    if (serverPort == null)
      serverPort = "2099";
    String serverUserName = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverUsername);
    if (serverUserName == null)
      serverUserName = "";
    String serverPassword = parameters.getObfuscatedParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverPassword);
    if (serverPassword == null)
      serverPassword = "";
    String ntlmUsername = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmUsername);
    if (ntlmUsername == null)
      ntlmUsername = "";
    String ntlmPassword = parameters.getObfuscatedParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmPassword);
    if (ntlmPassword == null)
      ntlmPassword = "";
    String ntlmDomain = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmDomain);
    if (ntlmDomain == null)
      ntlmDomain = "";
    String livelinkKeystore = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
    IKeystoreManager localKeystore;
    if (livelinkKeystore == null)
      localKeystore = KeystoreManagerFactory.make("");
    else
      localKeystore = KeystoreManagerFactory.make("",livelinkKeystore);
    out.print(
"<input name=\"configop\" type=\"hidden\" value=\"Continue\"/>\n"
    );
    // The "Server" tab
    if (tabName.equals("Server"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server name:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"servername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server port:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"serverport\" value=\""+serverPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server user name:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"serverusername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverUserName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server password:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"32\" name=\"serverpassword\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverPassword)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Server tab
      out.print(
"<input type=\"hidden\" name=\"servername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverName)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+serverPort+"\"/>\n"+
"<input type=\"hidden\" name=\"serverusername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverUserName)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverpassword\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverPassword)+"\"/>\n"
      );
    }

    // The "Document Access" tab
    // Always pass the whole keystore as a hidden.
    if (livelinkKeystore != null)
    {
      out.print(
"<input type=\"hidden\" name=\"keystoredata\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(livelinkKeystore)+"\"/>\n"
      );
    }
    if (tabName.equals("Document Access"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">Document fetch protocol:</td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"ingestprotocol\" size=\"2\">\n"+
"        <option value=\"http\" "+((ingestProtocol.equals("http"))?"selected=\"selected\"":"")+">http</option>\n"+
"        <option value=\"https\" "+((ingestProtocol.equals("https"))?"selected=\"selected\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch port:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"ingestport\" value=\""+ingestPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch SSL certificate list:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\"llkeystorealias\" value=\"\"/>\n"+
"      <table class=\"displaytable\">\n"
      );
      // List the individual certificates in the store, with a delete button for each
      String[] contents = localKeystore.getContents();
      if (contents.length == 0)
      {
        out.print(
"        <tr><td class=\"message\" colspan=\"2\"><nobr>No certificates present</nobr></td></tr>\n"
        );
      }
      else
      {
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          out.print(
"        <tr>\n"+
"          <td class=\"value\"><input type=\"button\" onclick='Javascript:LLDeleteCertificate(\""+org.apache.acf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")' alt=\""+"Delete cert "+org.apache.acf.ui.util.Encoder.attributeEscape(alias)+"\" value=\"Delete\"/></td>\n"+
"          <td>"+org.apache.acf.ui.util.Encoder.bodyEscape(description)+"</td>\n"+
"        </tr>\n"
          );
          i++;
        }
      }
      out.print(
"      </table>\n"+
"      <input type=\"button\" onclick='Javascript:LLAddCertificate()' alt=\"Add cert\" value=\"Add\"/>&nbsp;\n"+
"      Certificate:&nbsp;<input name=\"llcertificate\" size=\"50\" type=\"file\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch CGI path:</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"ingestcgipath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ingestCgiPath)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch NTLM domain:</nobr><br/><nobr>(set if NTLM auth desired)</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"32\" name=\"ntlmdomain\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmDomain)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch NTLM user name:</nobr><br/><nobr>(set if different from server user name)</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"32\" name=\"ntlmusername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmUsername)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document fetch NTLM password:</nobr><br/><nobr>(set if different from server password)</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"password\" size=\"32\" name=\"ntlmpassword\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmPassword)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Document Access tab
      out.print(
"<input type=\"hidden\" name=\"ingestprotocol\" value=\""+ingestProtocol+"\"/>\n"+
"<input type=\"hidden\" name=\"ingestport\" value=\""+ingestPort+"\"/>\n"+
"<input type=\"hidden\" name=\"ingestcgipath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ingestCgiPath)+"\"/>\n"+
"<input type=\"hidden\" name=\"ntlmusername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmUsername)+"\"/>\n"+
"<input type=\"hidden\" name=\"ntlmpassword\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmPassword)+"\"/>\n"+
"<input type=\"hidden\" name=\"ntlmdomain\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(ntlmDomain)+"\"/>\n"
      );
  }

    // Document View tab
    if (tabName.equals("Document View"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">Document view protocol:</td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"viewprotocol\" size=\"3\">\n"+
"        <option value=\"\" "+((viewProtocol.equals(""))?"selected=\"selected\"":"")+">Same as fetch protocol</option>\n"+
"        <option value=\"http\" "+((viewProtocol.equals("http"))?"selected=\"selected\"":"")+">http</option>\n"+
"        <option value=\"https\" "+((viewProtocol.equals("https"))?"selected=\"selected\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document view server name:</nobr><br/><nobr>(blank = same as fetch server)</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"viewservername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(viewServerName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document view port:</nobr><br/><nobr>(blank = same as fetch port)</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"viewport\" value=\""+viewPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document view CGI path:</nobr><br/><nobr>(blank = same as fetch path)</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"viewcgipath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(viewCgiPath)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Document View tab
      out.print(
"<input type=\"hidden\" name=\"viewprotocol\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(viewProtocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"viewservername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(viewServerName)+"\"/>\n"+
"<input type=\"hidden\" name=\"viewport\" value=\""+viewPort+"\"/>\n"+
"<input type=\"hidden\" name=\"viewcgipath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(viewCgiPath)+"\"/>\n"
      );
    }
  }
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ACFException
  {
    String serverName = variableContext.getParameter("servername");
    if (serverName != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverName,serverName);
    String serverPort = variableContext.getParameter("serverport");
    if (serverPort != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverPort,serverPort);
    String ingestProtocol = variableContext.getParameter("ingestprotocol");
    if (ingestProtocol != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestProtocol,ingestProtocol);
    String ingestPort = variableContext.getParameter("ingestport");
    if (ingestPort != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestPort,ingestPort);
    String ingestCgiPath = variableContext.getParameter("ingestcgipath");
    if (ingestCgiPath != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ingestCgiPath,ingestCgiPath);
    String viewProtocol = variableContext.getParameter("viewprotocol");
    if (viewProtocol != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewProtocol,viewProtocol);
    String viewServerName = variableContext.getParameter("viewservername");
    if (viewServerName != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewServerName,viewServerName);
    String viewPort = variableContext.getParameter("viewport");
    if (viewPort != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewPort,viewPort);
    String viewCgiPath = variableContext.getParameter("viewcgipath");
    if (viewCgiPath != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.viewCgiPath,viewCgiPath);
    String serverUserName = variableContext.getParameter("serverusername");
    if (serverUserName != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverUsername,serverUserName);
    String serverPassword = variableContext.getParameter("serverpassword");
    if (serverPassword != null)
      parameters.setObfuscatedParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.serverPassword,serverPassword);
    String ntlmDomain = variableContext.getParameter("ntlmdomain");
    if (ntlmDomain != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmDomain,ntlmDomain);
    String ntlmUsername = variableContext.getParameter("ntlmusername");
    if (ntlmUsername != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmUsername,ntlmUsername);
    String ntlmPassword = variableContext.getParameter("ntlmpassword");
    if (ntlmPassword != null)
      parameters.setObfuscatedParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.ntlmPassword,ntlmPassword);
    String keystoreValue = variableContext.getParameter("keystoredata");
    if (keystoreValue != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,keystoreValue);

    String configOp = variableContext.getParameter("configop");
    if (configOp != null)
    {
      if (configOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("llkeystorealias");
        keystoreValue = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,mgr.getString());
      }
      else if (configOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("llcertificate");
        keystoreValue = parameters.getParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try
        {
          mgr.importCertificate(alias,is);
        }
        catch (Throwable e)
        {
          certError = e.getMessage();
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Eat this exception
          }
        }

        if (certError != null)
        {
          return "Illegal certificate: "+certError;
        }
        parameters.setParameter(org.apache.acf.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,mgr.getString());
      }
    }

    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters)
    throws ACFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Parameters:</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"=<"+Integer.toString(kmanager.getContents().length)+" certificate(s)></nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.acf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, DocumentSpecification ds, ArrayList tabsArray)
    throws ACFException, IOException
  {
    tabsArray.add("Paths");
    tabsArray.add("Filters");
    tabsArray.add("Security");
    tabsArray.add("Metadata");
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkSpecification()\n"+
"{\n"+
"  // Does nothing right now.\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToPath(anchorvalue)\n"+
"{\n"+
"  if (editjob.pathaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"Select a folder first\");\n"+
"    editjob.pathaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"pathop\",\"AddToPath\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddFilespec(anchorvalue)\n"+
"{\n"+
"  if (editjob.specfile.value == \"\")\n"+
"  {\n"+
"    alert(\"Type in a file specification\");\n"+
"    editjob.specfile.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"fileop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"Type in an access token\");\n"+
"    editjob.spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToMetadata(anchorvalue)\n"+
"{\n"+
"  if (editjob.metadataaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"Select a folder first\");\n"+
"    editjob.metadataaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"metadataop\",\"AddToPath\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecSetWorkspace(anchorvalue)\n"+
"{\n"+
"  if (editjob.metadataaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"Select a workspace first\");\n"+
"    editjob.metadataaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"metadataop\",\"SetWorkspace\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddCategory(anchorvalue)\n"+
"{\n"+
"  if (editjob.categoryaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"Select a category first\");\n"+
"    editjob.categoryaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"metadataop\",\"AddCategory\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddMetadata(anchorvalue)\n"+
"{\n"+
"  if (editjob.attributeselect.value == \"\" && editjob.attributeall.value == \"\")\n"+
"  {\n"+
"    alert(\"Select at least one attribute first, and do not select the pulldown title\");\n"+
"    editjob.attributeselect.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"metadataop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddMapping(anchorvalue)\n"+
"{\n"+
"  if (editjob.specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\"Match string cannot be empty\");\n"+
"    editjob.specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editjob.specmatch.value))\n"+
"  {\n"+
"    alert(\"Match string must be valid regular expression\");\n"+
"    editjob.specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"specmappingop\",\"Add\",anchorvalue);\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, DocumentSpecification ds, String tabName)
    throws ACFException, IOException
  {
    int i;
    int k;

    // Paths tab
    if (tabName.equals("Paths"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Now, loop through paths
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "pathop"+pathDescription;
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"+
"      <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"      <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+"Delete path #"+Integer.toString(k)+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+((sn.getAttributeValue("path").length() == 0)?"(root)":org.apache.acf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path")))+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">No starting points defined</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
  
      String pathSoFar = (String)currentContext.get("specpath");
      if (pathSoFar == null)
      pathSoFar = "";

      // Grab next folder/project list
      try
      {
        String[] childList;
        childList = getChildFolderNames(pathSoFar);
        if (childList == null)
        {
          // Illegal path - set it back
          pathSoFar = "";
          childList = getChildFolderNames("");
          if (childList == null)
            throw new ACFException("Can't find any children for root folder");
        }
        out.print(
"      <input type=\"hidden\" name=\"specpath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"      <input type=\"hidden\" name=\"pathop\" value=\"\"/>\n"+
"      <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" onClick='Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")' alt=\"Add path\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+((pathSoFar.length()==0)?"(root)":org.apache.acf.ui.util.Encoder.bodyEscape(pathSoFar))+"\n"
        );
        if (pathSoFar.length() > 0)
        {
          out.print(
"      <input type=\"button\" value=\"-\" onClick='Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")' alt=\"Back up path\"/>\n"
          );
        }
        if (childList.length > 0)
        {
          out.print(
"      <input type=\"button\" value=\"+\" onClick='Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")' alt=\"Add to path\"/>&nbsp;\n"+
"      <select multiple=\"false\" name=\"pathaddon\" size=\"2\">\n"+
"        <option value=\"\" selected=\"selected\">-- Pick a folder --</option>\n"
          );
          int j = 0;
          while (j < childList.length)
          {
            out.print(
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(childList[j])+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(childList[j])+"</option>\n"
            );
            j++;
          }
          out.print(
"      </select>\n"
          );
        }
      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.println(org.apache.acf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      catch (ACFException e)
      {
        e.printStackTrace();
        out.println(org.apache.acf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Now, loop through paths
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);
          out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Filter tab
    if (tabName.equals("Filters"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Next, go through include/exclude filespecs
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("include") || sn.getType().equals("exclude"))
        {
          String fileSpecDescription = "_"+Integer.toString(k);
          String fileOpName = "fileop"+fileSpecDescription;
          String filespec = sn.getAttributeValue("filespec");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+"specfiletype"+fileSpecDescription+"\" value=\""+sn.getType()+"\"/>\n"+
"      <input type=\"hidden\" name=\""+fileOpName+"\" value=\"\"/>\n"+
"      <a name=\""+"filespec_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+fileOpName+"\",\"Delete\",\"filespec_"+Integer.toString(k)+"\")' alt=\""+"Delete filespec #"+Integer.toString(k)+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+(sn.getType().equals("include")?"Include:":"")+"\n"+
"      "+(sn.getType().equals("exclude")?"Exclude:":"")+"\n"+
"      &nbsp;<input type=\"hidden\" name=\""+"specfile"+fileSpecDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(filespec)+"\"/>\n"+
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(filespec)+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">No include/exclude files defined</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"filecount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\"fileop\" value=\"\"/>\n"+
"      <a name=\""+"filespec_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" onClick='Javascript:SpecAddFilespec(\"filespec_"+Integer.toString(k+1)+"\")' alt=\"Add file specification\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"specfiletype\" size=\"1\">\n"+
"        <option value=\"include\" selected=\"selected\">Include</option>\n"+
"        <option value=\"exclude\">Exclude</option>\n"+
"      </select>&nbsp;\n"+
"      <input type=\"text\" size=\"30\" name=\"specfile\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Next, go through include/exclude filespecs
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("include") || sn.getType().equals("exclude"))
        {
          String fileSpecDescription = "_"+Integer.toString(k);
          String filespec = sn.getAttributeValue("filespec");
          out.print(
"<input type=\"hidden\" name=\""+"specfiletype"+fileSpecDescription+"\" value=\""+sn.getType()+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specfile"+fileSpecDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(filespec)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"filecount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }


    // Security tab
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }

    if (tabName.equals("Security"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Security:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"on\" "+(securityOn?"checked=\"true\"":"")+" />Enabled&nbsp;\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />Disabled\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = "accessop"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")' alt=\""+"Delete token #"+Integer.toString(k)+"\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(token)+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">No access tokens present</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\"accessop\" value=\"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" onClick='Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")' alt=\"Add access token\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specsecurity\" value=\""+(securityOn?"on":"off")+"\"/>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }


    // Metadata tab

    // Find the path-value metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    // Find the path-value mapping data
    i = 0;
    org.apache.acf.crawler.connectors.livelink.MatchMap matchMap = new org.apache.acf.crawler.connectors.livelink.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }


    i = 0;
    String ingestAllMetadata = "false";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("allmetadata"))
      {
        ingestAllMetadata = sn.getAttributeValue("all");
        if (ingestAllMetadata == null)
          ingestAllMetadata = "false";
      }
    }

    if (tabName.equals("Metadata"))
    {
      out.print(
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\"specmappingop\" value=\"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Ingest ALL metadata?</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <nobr><input type=\"radio\" name=\"specallmetadata\" value=\"true\" "+(ingestAllMetadata.equals("true")?"checked=\"true\"":"")+"/>Yes</nobr>&nbsp;\n"+
"      <nobr><input type=\"radio\" name=\"specallmetadata\" value=\"false\" "+(ingestAllMetadata.equals("false")?"checked=\"true\"":"")+"/>No</nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      // Go through the selected metadata attributes
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("metadata"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = "metadataop"+accessDescription;
          String categoryPath = sn.getAttributeValue("category");
          String isAll = sn.getAttributeValue("all");
          if (isAll == null)
            isAll = "false";
          String attributeName = sn.getAttributeValue("attribute");
          if (attributeName == null)
            attributeName = "";
          out.print(
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+"speccategory"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(categoryPath)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+"specattributeall"+accessDescription+"\" value=\""+isAll+"\"/>\n"+
"      <input type=\"hidden\" name=\""+"specattribute"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(attributeName)+"\"/>\n"+
"      <a name=\""+"metadata_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"metadata_"+Integer.toString(k)+"\")' alt=\""+"Delete metadata #"+Integer.toString(k)+"\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(categoryPath)+":"+((isAll!=null&&isAll.equals("true"))?"(All metadata attributes)":org.apache.acf.ui.util.Encoder.bodyEscape(attributeName))+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"4\">No metadata specified</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\">\n"+
"      <a name=\""+"metadata_"+Integer.toString(k)+"\"></a>\n"+
"      <input type=\"hidden\" name=\"metadatacount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
      String categorySoFar = (String)currentContext.get("speccategory");
      if (categorySoFar == null)
      categorySoFar = "";
      // Grab next folder/project list, and the appropriate category list
      try
      {
        String[] childList = null;
        String[] workspaceList = null;
        String[] categoryList = null;
        String[] attributeList = null;
        if (categorySoFar.length() == 0)
        {
          workspaceList = getWorkspaceNames();
        }
        else
        {
          attributeList = getCategoryAttributes(categorySoFar);
          if (attributeList == null)
          {
            childList = getChildFolderNames(categorySoFar);
            if (childList == null)
            {
              // Illegal path - set it back
              categorySoFar = "";
              childList = getChildFolderNames("");
              if (childList == null)
                throw new ACFException("Can't find any children for root folder");
            }
            categoryList = getChildCategoryNames(categorySoFar);
            if (categoryList == null)
              throw new ACFException("Can't find any categories for root folder folder");
          }
        }
        out.print(
"      <input type=\"hidden\" name=\"speccategory\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(categorySoFar)+"\"/>\n"+
"      <input type=\"hidden\" name=\"metadataop\" value=\"\"/>\n"
        );
        if (attributeList != null)
        {
          // We have a valid category!
          out.print(
"      <input type=\"button\" value=\"Add\" onClick='Javascript:SpecAddMetadata(\"metadata_"+Integer.toString(k+1)+"\")' alt=\"Add metadata item\"/>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(categorySoFar)+":<input type=\"button\" value=\"-\" onClick='Javascript:SpecOp(\"metadataop\",\"Up\",\"metadata_"+Integer.toString(k)+"\")' alt=\"Back up metadata path\"/>&nbsp;\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"value\">\n"+
"            <input type=\"checkbox\" name=\"attributeall\" value=\"true\"/>&nbsp;All attributes in this category<br/>\n"+
"            <select multiple=\"true\" name=\"attributeselect\" size=\"2\">\n"+
"              <option value=\"\" selected=\"selected\">-- Pick attributes --</option>\n"
          );
          int l = 0;
          while (l < attributeList.length)
          {
            String attributeName = attributeList[l++];
            out.print(
"              <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(attributeName)+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(attributeName)+"</option>\n"
            );
          }
          out.print(
"            </select>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"
          );
        }
        else if (workspaceList != null)
        {
          out.print(
"    </td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"button\" value=\"+\" onClick='Javascript:SpecSetWorkspace(\"metadata_"+Integer.toString(k)+"\")' alt=\"Add to metadata path\"/>&nbsp;\n"+
"      <select multiple=\"false\" name=\"metadataaddon\" size=\"2\">\n"+
"        <option value=\"\" selected=\"selected\">-- Pick workspace --</option>\n"
          );
          int j = 0;
          while (j < workspaceList.length)
          {
            out.print(
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(workspaceList[j])+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(workspaceList[j])+"</option>\n"
            );
            j++;
          }
          out.print(
"      </select>\n"
          );
        }
        else
        {
          out.print(
"    </td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      "+((categorySoFar.length()==0)?"(root)":org.apache.acf.ui.util.Encoder.bodyEscape(categorySoFar))+"&nbsp;\n"
          );
          if (categorySoFar.length() > 0)
          {
            out.print(
"      <input type=\"button\" value=\"-\" onClick='Javascript:SpecOp(\"metadataop\",\"Up\",\"metadata_"+Integer.toString(k)+"\")' alt=\"Back up metadata path\"/>&nbsp;\n"
            );
          }
          if (childList.length > 0)
          {
            out.print(
"      <input type=\"button\" value=\"+\" onClick='Javascript:SpecAddToMetadata(\"metadata_"+Integer.toString(k)+"\")' alt=\"Add to metadata path\"/>&nbsp;\n"+
"      <select multiple=\"false\" name=\"metadataaddon\" size=\"2\">\n"+
"        <option value=\"\" selected=\"selected\">-- Pick a folder --</option>\n"
            );
            int j = 0;
            while (j < childList.length)
            {
              out.print(
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(childList[j])+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(childList[j])+"</option>\n"
              );
              j++;
            }
            out.print(
"      </select>\n"
            );
          }
          if (categoryList.length > 0)
          {
            out.print(
"      <input type=\"button\" value=\"+\" onClick='Javascript:SpecAddCategory(\"metadata_"+Integer.toString(k)+"\")' alt=\"Add category\"/>&nbsp;\n"+
"      <select multiple=\"false\" name=\"categoryaddon\" size=\"2\">\n"+
"        <option value=\"\" selected=\"selected\">-- Pick a category --</option>\n"
            );
            int j = 0;
            while (j < categoryList.length)
            {
              out.print(
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(categoryList[j])+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(categoryList[j])+"</option>\n"
              );
              j++;
            }
            out.print(
"      </select>\n"
            );
          }
        }
      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.println(org.apache.acf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      catch (ACFException e)
      {
        e.printStackTrace();
        out.println(org.apache.acf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Path attribute name:</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"text\" name=\"specpathnameattribute\" size=\"20\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+"specmappingop_"+Integer.toString(i)+"\" value=\"\"/>\n"+
"      <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"Delete\",\"mapping_"+Integer.toString(i)+"\")' alt=\""+"Delete mapping #"+Integer.toString(i)+"\" value=\"Delete\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\""+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchString)+"\"/>"+org.apache.acf.ui.util.Encoder.bodyEscape(matchString)+"\n"+
"    </td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\""+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>"+org.apache.acf.ui.util.Encoder.bodyEscape(replaceString)+"\n"+
"    </td>\n"+
"  </tr>\n"
        );
        i++;
      }
      if (i == 0)
      {
        out.print(
"  <tr><td colspan=\"4\" class=\"message\">No mappings specified</td></tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")' alt=\"Add to mappings\" value=\"Add\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">Match regexp:&nbsp;<input type=\"text\" name=\"specmatch\" size=\"32\" value=\"\"/></td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\">Replace string:&nbsp;<input type=\"text\" name=\"specreplace\" size=\"32\" value=\"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specallmetadata\" value=\""+ingestAllMetadata+"\"/>\n"
      );
      // Go through the selected metadata attributes
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("metadata"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String categoryPath = sn.getAttributeValue("category");
          String isAll = sn.getAttributeValue("all");
          if (isAll == null)
            isAll = "false";
          String attributeName = sn.getAttributeValue("attribute");
          if (attributeName == null)
            attributeName = "";
          out.print(
"<input type=\"hidden\" name=\""+"speccategory"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(categoryPath)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specattributeall"+accessDescription+"\" value=\""+isAll+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specattribute"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(attributeName)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"metadatacount\" value=\""+Integer.toString(k)+"\"/>\n"+
"<input type=\"hidden\" name=\"specpathnameattribute\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"<input type=\"hidden\" name=\""+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"
        );
        i++;
      }
    }
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, DocumentSpecification ds)
    throws ACFException
  {
    String xc = variableContext.getParameter("pathcount");
    if (xc != null)
    {
      // Delete all path specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("startpoint"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(xc);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "pathop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter("specpath"+pathDescription);
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("pathop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter("specpath");
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        ds.addChild(ds.getChildCount(),node);
      }
      else if (op != null && op.equals("Up"))
      {
        // Strip off end
        String path = variableContext.getParameter("specpath");
        int lastSlash = -1;
        int k = 0;
        while (k < path.length())
        {
          char x = path.charAt(k++);
          if (x == '/')
          {
            lastSlash = k-1;
            continue;
          }
          if (x == '\\')
            k++;
        }
        if (lastSlash == -1)
          path = "";
        else
          path = path.substring(0,lastSlash);
        currentContext.save("specpath",path);
      }
      else if (op != null && op.equals("AddToPath"))
      {
        String path = variableContext.getParameter("specpath");
        String addon = variableContext.getParameter("pathaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuffer sb = new StringBuffer();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (path.length() == 0)
            path = sb.toString();
          else
            path += "/" + sb.toString();
        }
        currentContext.save("specpath",path);
      }
    }

    xc = variableContext.getParameter("filecount");
    if (xc != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("include") || sn.getType().equals("exclude"))
          ds.removeChild(i);
        else
          i++;
      }

      int fileCount = Integer.parseInt(xc);
      i = 0;
      while (i < fileCount)
      {
        String fileSpecDescription = "_"+Integer.toString(i);
        String fileOpName = "fileop"+fileSpecDescription;
        xc = variableContext.getParameter(fileOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String filespecType = variableContext.getParameter("specfiletype"+fileSpecDescription);
        String filespec = variableContext.getParameter("specfile"+fileSpecDescription);
        SpecificationNode node = new SpecificationNode(filespecType);
        node.setAttribute("filespec",filespec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter("fileop");
      if (op != null && op.equals("Add"))
      {
        String filespec = variableContext.getParameter("specfile");
        String filespectype = variableContext.getParameter("specfiletype");
        SpecificationNode node = new SpecificationNode(filespectype);
        node.setAttribute("filespec",filespec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter("specsecurity");
    if (xc != null)
    {
      // Delete all security entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("security"))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode node = new SpecificationNode("security");
      node.setAttribute("value",xc);
      ds.addChild(ds.getChildCount(),node);

    }

    xc = variableContext.getParameter("tokencount");
    if (xc != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount)
      {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = "accessop"+accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter("spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter("spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter("specallmetadata");
    if (xc != null)
    {
      // Look for the 'all metadata' checkbox
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("allmetadata"))
          ds.removeChild(i);
        else
          i++;
      }

      if (xc.equals("true"))
      {
        SpecificationNode newNode = new SpecificationNode("allmetadata");
        newNode.setAttribute("all",xc);
        ds.addChild(ds.getChildCount(),newNode);
      }
    }

    xc = variableContext.getParameter("metadatacount");
    if (xc != null)
    {
      // Delete all metadata specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("metadata"))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int metadataCount = Integer.parseInt(xc);
      // Gather up these
      i = 0;
      while (i < metadataCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "metadataop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Metadata inserts won't happen until the very end
        String category = variableContext.getParameter("speccategory"+pathDescription);
        String attributeName = variableContext.getParameter("specattribute"+pathDescription);
        String isAll = variableContext.getParameter("specattributeall"+pathDescription);
        SpecificationNode node = new SpecificationNode("metadata");
        node.setAttribute("category",category);
        if (isAll != null && isAll.equals("true"))
          node.setAttribute("all","true");
        else
          node.setAttribute("attribute",attributeName);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("metadataop");
      if (op != null && op.equals("Add"))
      {
        String category = variableContext.getParameter("speccategory");
        String isAll = variableContext.getParameter("attributeall");
        if (isAll != null && isAll.equals("true"))
        {
          SpecificationNode node = new SpecificationNode("metadata");
          node.setAttribute("category",category);
          node.setAttribute("all","true");
          ds.addChild(ds.getChildCount(),node);
        }
        else
        {
          String[] attributes = variableContext.getParameterValues("attributeselect");
          if (attributes != null && attributes.length > 0)
          {
            int k = 0;
            while (k < attributes.length)
            {
              String attribute = attributes[k++];
              SpecificationNode node = new SpecificationNode("metadata");
              node.setAttribute("category",category);
              node.setAttribute("attribute",attribute);
              ds.addChild(ds.getChildCount(),node);
            }
          }
        }
      }
      else if (op != null && op.equals("Up"))
      {
        // Strip off end
        String category = variableContext.getParameter("speccategory");
        int lastSlash = -1;
        int firstColon = -1;      
        int k = 0;
        while (k < category.length())
        {
          char x = category.charAt(k++);
          if (x == '/')
          {
            lastSlash = k-1;
            continue;
          }
          if (x == ':')
          {
            firstColon = k;
            continue;
          }
          if (x == '\\')
            k++;
        }

        if (lastSlash == -1)
        {
          if (firstColon == -1 || firstColon == category.length())
            category = "";
          else
            category = category.substring(0,firstColon);
        }
        else
          category = category.substring(0,lastSlash);
        currentContext.save("speccategory",category);
      }
      else if (op != null && op.equals("AddToPath"))
      {
        String category = variableContext.getParameter("speccategory");
        String addon = variableContext.getParameter("metadataaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuffer sb = new StringBuffer();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (category.length() == 0 || category.endsWith(":"))
            category += sb.toString();
          else
            category += "/" + sb.toString();
        }
        currentContext.save("speccategory",category);
      }
      else if (op != null && op.equals("SetWorkspace"))
      {
        String addon = variableContext.getParameter("metadataaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuffer sb = new StringBuffer();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }

          String category = sb.toString() + ":";
          currentContext.save("speccategory",category);
        }
      }
      else if (op != null && op.equals("AddCategory"))
      {
        String category = variableContext.getParameter("speccategory");
        String addon = variableContext.getParameter("categoryaddon");
        if (addon != null && addon.length() > 0)
        {
          StringBuffer sb = new StringBuffer();
          int k = 0;
          while (k < addon.length())
          {
            char x = addon.charAt(k++);
            if (x == '/' || x == '\\' || x == ':')
              sb.append('\\');
            sb.append(x);
          }
          if (category.length() == 0 || category.endsWith(":"))
            category += sb.toString();
          else
            category += "/" + sb.toString();
        }
        currentContext.save("speccategory",category);
      }
    }

    xc = variableContext.getParameter("specpathnameattribute");
    if (xc != null)
    {
      // Delete old one
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathnameattribute"))
          ds.removeChild(i);
        else
          i++;
      }
      if (xc.length() > 0)
      {
        SpecificationNode node = new SpecificationNode("pathnameattribute");
        node.setAttribute("value",xc);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter("specmappingcount");
    if (xc != null)
    {
      // Delete old spec
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathmap"))
          ds.removeChild(i);
        else
          i++;
      }

      // Now, go through the data and assemble a new list.
      int mappingCount = Integer.parseInt(xc);

      // Gather up these
      i = 0;
      while (i < mappingCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "specmappingop"+pathDescription;
        xc = variableContext.getParameter(pathOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Inserts won't happen until the very end
        String match = variableContext.getParameter("specmatch"+pathDescription);
        String replace = variableContext.getParameter("specreplace"+pathDescription);
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // Check for add
      xc = variableContext.getParameter("specmappingop");
      if (xc != null && xc.equals("Add"))
      {
        String match = variableContext.getParameter("specmatch");
        String replace = variableContext.getParameter("specreplace");
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, DocumentSpecification ds)
    throws ACFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    int i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("startpoint"))
      {
        if (seenAny == false)
        {
          out.print(
"    <td class=\"description\">Roots:</td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        out.print(
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">No start points specified</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );

    seenAny = false;
    // Go through looking for include or exclude file specs
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("include") || sn.getType().equals("exclude"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr><td class=\"description\">File specs:</td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String filespec = sn.getAttributeValue("filespec");
        out.print(
"      "+(sn.getType().equals("include")?"Include file:":"")+"\n"+
"      "+(sn.getType().equals("exclude")?"Exclude file:":"")+"\n"+
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(filespec)+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">No file specs specified</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }
    out.print(
"  <tr>\n"+
"    <td class=\"description\">Security:</td>\n"+
"    <td class=\"value\">"+(securityOn?"Enabled":"Disabled")+"</td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr><td class=\"description\">Access tokens:</td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(token)+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">No access tokens specified</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    i = 0;
    String allMetadata = "Only specified metadata will be ingested";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("allmetadata"))
      {
        String value = sn.getAttributeValue("all");
        if (value != null && value.equals("true"))
        {
          allMetadata="All document metadata will be ingested";
        }
      }
    }
    out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>Metadata specification:</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+allMetadata+"</nobr></td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for metadata spec
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("metadata"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr><td class=\"description\"><nobr>Specific metadata:</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String category = sn.getAttributeValue("category");
        String attribute = sn.getAttributeValue("attribute");
        String isAll = sn.getAttributeValue("all");
        out.print(
"      "+org.apache.acf.ui.util.Encoder.bodyEscape(category)+":"+((isAll!=null&&isAll.equals("true"))?"(All metadata attributes)":org.apache.acf.ui.util.Encoder.bodyEscape(attribute))+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">No metadata specified</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find the path-name metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }
    out.print(
"  <tr>\n"
    );
    if (pathNameAttribute.length() > 0)
    {
      out.print(
"    <td class=\"description\">Path-name metadata attribute:</td>\n"+
"    <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(pathNameAttribute)+"</td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">No path-name metadata attribute specified</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"+
"  <tr>\n"
    );
    // Find the path-value mapping data
    i = 0;
    org.apache.acf.crawler.connectors.livelink.MatchMap matchMap = new org.apache.acf.crawler.connectors.livelink.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }
    if (matchMap.getMatchCount() > 0)
    {
      out.print(
"    <td class=\"description\">Path-value mapping:</td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"        <tr>\n"+
"          <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"          <td class=\"value\">--></td>\n"+
"          <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"        </tr>\n"
        );
        i++;
      }
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">No mappings specified</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"</table>\n"
    );
  }

  // The following public methods are NOT part of the interface.  They are here so that the UI can present information
  // that will allow users to select what they need.

  protected final static String CATEGORY_NAME = "CATEGORY";
  protected final static String ENTWKSPACE_NAME = "ENTERPRISE";

  /** Get the allowed workspace names.
  *@return a list of workspace names.
  */
  public String[] getWorkspaceNames()
    throws ACFException, ServiceInterruption
  {
    return new String[]{CATEGORY_NAME,ENTWKSPACE_NAME};
  }

  /** Given a path string, get a list of folders and projects under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of folder and project names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildFolderNames(String pathString)
    throws ACFException, ServiceInterruption
  {
    getSession();
    return getChildFolders(pathString);
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  public String[] getChildCategoryNames(String pathString)
    throws ACFException, ServiceInterruption
  {
    getSession();
    return getChildCategories(pathString);
  }

  /** Given a category path, get a list of legal attribute names.
  *@param pathString is the current path of a category (with path components separated by dots).
  *@return a list of attribute names, in sorted order, or null of the path was invalid.
  */
  public String[] getCategoryAttributes(String pathString)
    throws ACFException, ServiceInterruption
  {
    getSession();

    // Start at root
    RootValue rv = new RootValue(pathString);

    // Get the object id of the category the path describes
    int catObjectID = getCategoryId(rv);
    if (catObjectID == -1)
      return null;

    String[] rval = getCategoryAttributes(catObjectID);
    if (rval == null)
      return new String[0];
    return rval;
  }

  // Protected methods and classes


  /** Create the login URI.  This must be a relative URI.
  */
  protected String createLivelinkLoginURI()
    throws ACFException
  {
    try
    {
      StringBuffer llURI = new StringBuffer();

      llURI.append(ingestCgiPath);
      llURI.append("?func=ll.login&CurrentClientTime=D%2F2005%2F3%2F9%3A13%3A16%3A30&NextURL=");
      llURI.append(URLEncoder.encode(ingestCgiPath,"UTF-8"));
      llURI.append("%3FRedirect%3D1&Username=");
      llURI.append(URLEncoder.encode(llServer.getLLUser(),"UTF-8"));
      llURI.append("&Password=");
      llURI.append(URLEncoder.encode(llServer.getLLPwd(),"UTF-8"));

      return llURI.toString();
    }
    catch (UnsupportedEncodingException e)
    {
      throw new ACFException("Login URI setup error: "+e.getMessage(),e);
    }
  }

  protected static class CloseThread extends Thread
  {
    protected InputStream is;
    protected Throwable exception = null;

    public CloseThread(InputStream is)
    {
      super();
      setDaemon(true);
      this.is = is;
    }

    public void run()
    {
      try
      {
        // Call the close method appropriately
        is.close();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }
  }

  protected static void closeViaThread(InputStream is)
    throws InterruptedException, IOException
  {
    CloseThread t = new CloseThread(is);
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof IOException)
          throw (IOException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else
          throw (Error)thr;
      }
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
      throw e;
    }
  }

  /**
  * Connects to the specified Livelink document using HTTP protocol
  * @param documentIdentifier is the document identifier (as far as the crawler knows).
  * @param activities is the process activity structure, so we can ingest
  */
  protected void ingestFromLiveLink(String documentIdentifier, String version,
    IProcessActivity activities,
    MetadataDescription desc, SystemMetadataDescription sDesc)
    throws ACFException, ServiceInterruption
  {

    String contextMsg = "for '"+documentIdentifier+"'";

    String ingestHttpAddress = convertToIngestURI(documentIdentifier);
    if (ingestHttpAddress == null)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: No fetch URI "+contextMsg+" - not ingesting");
      return;
    }

    String viewHttpAddress = convertToViewURI(documentIdentifier);
    if (viewHttpAddress == null)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: No view URI "+contextMsg+" - not ingesting");
      return;
    }

    RepositoryDocument rd = new RepositoryDocument();

    // Iterate over the metadata items.  These are organized by category
    // for speed of lookup.
    int objID = convertToObjectID(documentIdentifier);

    // Unpack version string
    int startPos = 0;

    // Metadata items first
    ArrayList metadataItems = new ArrayList();
    startPos = unpackList(metadataItems,version,startPos,'+');
    Iterator catIter = desc.getItems(metadataItems);
    while (catIter.hasNext())
    {
      MetadataItem item = (MetadataItem)catIter.next();
      MetadataPathItem pathItem = item.getPathItem();
      if (pathItem != null)
      {
        int catID = pathItem.getCatID();
        // grab the associated catversion
        LLValue catVersion = getCatVersion(objID,catID);
        if (catVersion != null)
        {
          // Go through attributes now
          Iterator attrIter = item.getAttributeNames();
          while (attrIter.hasNext())
          {
            String attrName = (String)attrIter.next();
            // Create a unique metadata name
            String metadataName = pathItem.getCatName()+":"+attrName;
            // Fetch the metadata and stuff it into the RepositoryData structure
            String[] metadataValue = getAttributeValue(catVersion,attrName);
            if (metadataValue != null)
              rd.addField(metadataName,metadataValue);
            else
              Logging.connectors.warn("Livelink: Metadata attribute '"+metadataName+"' does not seem to exist; please correct the job");
          }
        }

      }
    }

    // Unpack acls (conditionally)
    if (startPos < version.length())
    {
      char x = version.charAt(startPos++);
      if (x == '+')
      {
        ArrayList acls = new ArrayList();
        startPos = unpackList(acls,version,startPos,'+');
        // Turn into acls and add into description
        String[] aclArray = new String[acls.size()];
        int j = 0;
        while (j < aclArray.length)
        {
          aclArray[j] = (String)acls.get(j);
          j++;
        }
        rd.setACL(aclArray);

        StringBuffer denyBuffer = new StringBuffer();
        startPos = unpack(denyBuffer,version,startPos,'+');
        String denyAcl = denyBuffer.toString();
        String[] denyAclArray = new String[1];
        denyAclArray[0] = denyAcl;
        rd.setDenyACL(denyAclArray);
      }
    }

    // Add the path metadata item into the mix, if enabled
    String pathAttributeName = sDesc.getPathAttributeName();
    if (pathAttributeName != null && pathAttributeName.length() > 0)
    {
      String pathString = sDesc.getPathAttributeValue(documentIdentifier);
      if (pathString != null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Path attribute name is '"+pathAttributeName+"'"+contextMsg+", value is '"+pathString+"'");
        rd.addField(pathAttributeName,pathString);
      }
    }

    // Set up connection
    HttpClient client = getInitializedClient(contextMsg);

    long currentTime;

    if (Logging.connectors.isInfoEnabled())
      Logging.connectors.info("Livelink: " + ingestHttpAddress);

    try
    {
      long startTime = System.currentTimeMillis();
      String resultCode = "OK";
      String resultDescription = null;
      Long readSize = null;

      // Set up fetch using our special stuff if it's https
      GetMethod method = new GetMethod(ingestHttpAddress);
      try
      {
        method.getParams().setParameter("http.socket.timeout", new Integer(300000));
        method.setFollowRedirects(true);


        int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),method);
        switch (statusCode)
        {
        case 500:
        case 502:
          Logging.connectors.warn("Livelink: Service interruption during fetch "+contextMsg+" with Livelink HTTP Server, retrying...");
          throw new ServiceInterruption("Service interruption during fetch",new ACFException(Integer.toString(statusCode)+" error while fetching"),System.currentTimeMillis()+60000L,
            System.currentTimeMillis()+600000L,-1,true);

        case HttpStatus.SC_UNAUTHORIZED:
          Logging.connectors.warn("Livelink: Document fetch unauthorized for "+ingestHttpAddress+" ("+contextMsg+")");
          // Since we logged in, we should fail here if the ingestion user doesn't have access to the
          // the document, but if we do, don't fail hard.
          resultCode = "UNAUTHORIZED";
          return;

        case HttpStatus.SC_OK:
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Livelink: Created http document connection to Livelink "+contextMsg);
          long dataSize = method.getResponseContentLength();
          // The above replaces this, which required another access:
          // long dataSize = (long)value.toInteger("DataSize");
          // A non-existent content length will cause a value of -1 to be returned.  This seems to indicate that the session login did not work right.
          if (dataSize >= 0)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Livelink: Content length from livelink server "+contextMsg+"' = "+new Long(dataSize).toString());

            try
            {
              InputStream is = method.getResponseBodyAsStream();
              try
              {
                rd.setBinary(is,dataSize);

                activities.ingestDocument(documentIdentifier,version,viewHttpAddress,rd);

                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Livelink: Ingesting done "+contextMsg);

              }
              finally
              {
                // Close stream via thread, since otherwise this can hang
                closeViaThread(is);
              }
            }
            catch (java.net.SocketTimeoutException e)
            {
              resultCode = "DATATIMEOUT";
              resultDescription = e.getMessage();
              currentTime = System.currentTimeMillis();
              Logging.connectors.warn("Livelink: Livelink socket timed out ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
              throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
            }
            catch (java.net.SocketException e)
            {
              resultCode = "DATASOCKETERROR";
              resultDescription = e.getMessage();
              currentTime = System.currentTimeMillis();
              Logging.connectors.warn("Livelink: Livelink socket error ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
              throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
            }
            catch (javax.net.ssl.SSLHandshakeException e)
            {
              resultCode = "DATASSLHANDSHAKEERROR";
              resultDescription = e.getMessage();
              currentTime = System.currentTimeMillis();
              Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
              throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
            }
            catch (org.apache.commons.httpclient.ConnectTimeoutException e)
            {
              resultCode = "CONNECTTIMEOUT";
              resultDescription = e.getMessage();
              currentTime = System.currentTimeMillis();
              Logging.connectors.warn("Livelink: Livelink socket timed out connecting to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
              throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
            }
            catch (InterruptedException e)
            {
              throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
            }
            catch (InterruptedIOException e)
            {
              throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
            }
            catch (IOException e)
            {
              resultCode = "DATAEXCEPTION";
              resultDescription = e.getMessage();
              // Treat unknown error ingesting data as a transient condition
              currentTime = System.currentTimeMillis();
              Logging.connectors.warn("Livelink: IO exception ingesting "+contextMsg+": "+e.getMessage(),e);
              throw new ServiceInterruption("IO exception ingesting "+contextMsg+": "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
            }
            readSize = new Long(dataSize);
          }
          else
          {
            resultCode = "SESSIONLOGINFAILED";
          }
          break;
        case HttpStatus.SC_BAD_REQUEST:
        case HttpStatus.SC_USE_PROXY:
        case HttpStatus.SC_GONE:
          resultCode = "ERROR "+Integer.toString(statusCode);
          throw new ACFException("Unrecoverable request failure; error = "+Integer.toString(statusCode));
        default:
          resultCode = "UNKNOWN";
          Logging.connectors.warn("Livelink: Attempt to retrieve document from '"+ingestHttpAddress+"' received a response of "+Integer.toString(statusCode)+"; retrying in one minute");
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Fetch failed; retrying in 1 minute",new ACFException("Fetch failed with unknown code "+Integer.toString(statusCode)),
            currentTime+60000L,currentTime+600000L,-1,true);
        }
      }
      catch (InterruptedException e)
      {
        // Drop the connection on the floor
        method = null;
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (java.net.SocketTimeoutException e)
      {
        Logging.connectors.warn("Livelink: Socket timed out reading from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        resultCode = "TIMEOUT";
        resultDescription = e.getMessage();
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (java.net.SocketException e)
      {
        Logging.connectors.warn("Livelink: Socket error reading from Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        resultCode = "SOCKETERROR";
        resultDescription = e.getMessage();
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (javax.net.ssl.SSLHandshakeException e)
      {
        currentTime = System.currentTimeMillis();
        Logging.connectors.warn("Livelink: SSL handshake failed "+contextMsg+": "+e.getMessage(),e);
        resultCode = "SSLHANDSHAKEERROR";
        resultDescription = e.getMessage();
        throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
      }
      catch (org.apache.commons.httpclient.ConnectTimeoutException e)
      {
        Logging.connectors.warn("Livelink: Connect timed out reading from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        resultCode = "CONNECTTIMEOUT";
        resultDescription = e.getMessage();
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (InterruptedIOException e)
      {
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        resultCode = "EXCEPTION";
        resultDescription = e.getMessage();
        throw new ACFException("Exception getting response "+contextMsg+": "+e.getMessage(), e);
      }
      finally
      {
        if (method != null)
        {
          method.releaseConnection();
          activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,readSize,Integer.toString(objID),resultCode,resultDescription,null);
        }
      }
    }
    catch (IllegalStateException e)
    {
      Logging.connectors.error("Livelink: State exception dealing with '"+ingestHttpAddress+"': "+e.getMessage(),e);
      throw new ACFException("State exception dealing with '"+ingestHttpAddress+"': "+e.getMessage(),e);
    }
  }

  /** Initialize the host configuration */
  protected HostConfiguration getHostConfiguration(String contextMsg)
  {
    HostConfiguration clientConf = new HostConfiguration();
    // clientConf.setLocalAddress(currentAddr);
    clientConf.setParams(new HostParams());
    clientConf.setHost(llServer.getHost(),ingestPortNumber,myFactory.getProtocol(ingestProtocol));
    return clientConf;
  }

  /** Initialize a livelink client connection */
  protected HttpClient getInitializedClient(String contextMsg)
    throws ServiceInterruption, ACFException
  {
    HttpClient client = new HttpClient(connectionManager);
    client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.PROTOCOL_FACTORY,myFactory);
    client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.ALLOW_CIRCULAR_REDIRECTS,new Boolean(true));

    long currentTime;

    if (ntlmDomain != null)
    {
      // Set the NTLM credentials
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Setting up NTLM credentials "+contextMsg);
      client.getState().setCredentials(AuthScope.ANY,
        new NTCredentials(ntlmUsername,ntlmPassword,currentHost,ntlmDomain));
    }

    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("Livelink: Session authenticating via http "+contextMsg+"...");
    try
    {
      GetMethod authget = new GetMethod(createLivelinkLoginURI());
      try
      {
        authget.getParams().setParameter("http.socket.timeout", new Integer(60000));
        authget.setFollowRedirects(true);
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Created new GetMethod "+contextMsg+"; executing authentication method");
        int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),authget);

        if (statusCode == 502 || statusCode == 500)
        {
          Logging.connectors.warn("Livelink: Service interruption during authentication "+contextMsg+" with Livelink HTTP Server, retrying...");
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("502 error during authentication",new ACFException("502 error while authenticating"),
            currentTime+60000L,currentTime+600000L,-1,true);
        }
        if (statusCode != HttpStatus.SC_OK)
        {
          Logging.connectors.error("Livelink: Failed to authenticate "+contextMsg+" against Livelink HTTP Server; Status code: " + statusCode);
          // Ok, so we didn't get in - simply do not ingest
          if (statusCode == HttpStatus.SC_UNAUTHORIZED)
            throw new ACFException("Session authorization failed with a 401 code; are credentials correct?");
          else
            throw new ACFException("Session authorization failed with code "+Integer.toString(statusCode));
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Retrieving authentication response "+contextMsg+"");
        authget.getResponseBodyAsStream();
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Livelink: Authentication response retrieved "+contextMsg+"");

      }
      catch (InterruptedException e)
      {
        // Drop the connection on the floor
        authget = null;
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (java.net.SocketTimeoutException e)
      {
        currentTime = System.currentTimeMillis();
        Logging.connectors.warn("Livelink: Socket timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (java.net.SocketException e)
      {
        currentTime = System.currentTimeMillis();
        Logging.connectors.warn("Livelink: Socket error authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (javax.net.ssl.SSLHandshakeException e)
      {
        currentTime = System.currentTimeMillis();
        Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
        throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
      }
      catch (org.apache.commons.httpclient.ConnectTimeoutException e)
      {
        currentTime = System.currentTimeMillis();
        Logging.connectors.warn("Livelink: Connect timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
      }
      catch (InterruptedIOException e)
      {
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        Logging.connectors.error("Livelink: IO exception when authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        throw new ACFException("Unable to communicate with the Livelink HTTP Server: "+e.getMessage(), e);

      }
      finally
      {
        if (authget != null)
          authget.releaseConnection();
      }
    }
    catch (IllegalStateException e)
    {
      Logging.connectors.error("Livelink: State exception dealing with '"+createLivelinkLoginURI()+"'",e);
      throw new ACFException("State exception dealing with login URI: "+e.getMessage(),e);
    }

    return client;
  }

  /** Pack category and attribute */
  protected static String packCategoryAttribute(String category, String attribute)
  {
    StringBuffer sb = new StringBuffer();
    pack(sb,category,':');
    pack(sb,attribute,':');
    return sb.toString();
  }

  /** Unpack category and attribute */
  protected static void unpackCategoryAttribute(StringBuffer category, StringBuffer attribute, String value)
  {
    int startPos = 0;
    startPos = unpack(category,value,startPos,':');
    startPos = unpack(attribute,value,startPos,':');
  }

  /** Stuffer for packing a single string with an end delimiter */
  protected static void pack(StringBuffer output, String value, char delimiter)
  {
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\\' || x == delimiter)
        output.append('\\');
      output.append(x);
    }
    output.append(delimiter);
  }

  /** Unstuffer for the above. */
  protected static int unpack(StringBuffer sb, String value, int startPosition, char delimiter)
  {
    while (startPosition < value.length())
    {
      char x = value.charAt(startPosition++);
      if (x == '\\')
      {
        if (startPosition < value.length())
          x = value.charAt(startPosition++);
      }
      else if (x == delimiter)
        break;
      sb.append(x);
    }
    return startPosition;
  }

  /** Stuffer for packing lists of fixed length */
  protected static void packFixedList(StringBuffer output, String[] values, char delimiter)
  {
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of fixed length */
  protected static int unpackFixedList(String[] output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < output.length)
    {
      sb.setLength(0);
      startPosition = unpack(sb,value,startPosition,delimiter);
      output[i++] = sb.toString();
    }
    return startPosition;
  }

  /** Stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, ArrayList values, char delimiter)
  {
    pack(output,Integer.toString(values.size()),delimiter);
    int i = 0;
    while (i < values.size())
    {
      pack(output,values.get(i++).toString(),delimiter);
    }
  }

  /** Another stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, String[] values, char delimiter)
  {
    pack(output,Integer.toString(values.length),delimiter);
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of variable length.
  *@param output is the array to write the unpacked result into.
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@return the next position beyond the end of the list.
  */
  protected static int unpackList(ArrayList output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    startPosition = unpack(sb,value,startPosition,delimiter);
    try
    {
      int count = Integer.parseInt(sb.toString());
      int i = 0;
      while (i < count)
      {
        sb.setLength(0);
        startPosition = unpack(sb,value,startPosition,delimiter);
        output.add(sb.toString());
        i++;
      }
    }
    catch (NumberFormatException e)
    {
    }
    return startPosition;
  }

  /** Given a path string, get a list of folders and projects under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of folder and project names, in sorted order, or null if the path was invalid.
  */
  protected String[] getChildFolders(String pathString)
    throws ACFException, ServiceInterruption
  {
    RootValue rv = new RootValue(pathString);

    // Get the volume, object id of the folder/project the path describes
    VolumeAndId vid = getPathId(rv);
    if (vid == null)
      return null;

    String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
      " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";

    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }

        LLValue children = t.getResponse();

        String[] rval = new String[children.size()];
        int j = 0;
        while (j < children.size())
        {
          rval[j] = children.toString(j,"Name");
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }


  /** Given a path string, get a list of categories under that node.
  *@param pathString is the current path (folder names and project names, separated by dots (.)).
  *@return a list of category names, in sorted order, or null if the path was invalid.
  */
  protected String[] getChildCategories(String pathString)
    throws ACFException, ServiceInterruption
  {
    // Start at root
    RootValue rv = new RootValue(pathString);

    // Get the volume, object id of the folder/project the path describes
    VolumeAndId vid = getPathId(rv);
    if (vid == null)
      return null;

    // We want only folders that are children of the current object and which match the specified subfolder
    String filterString = "SubType="+ LAPI_DOCUMENTS.CATEGORYSUBTYPE;

    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }

        LLValue children = t.getResponse();

        String[] rval = new String[children.size()];
        int j = 0;
        while (j < children.size())
        {
          rval[j] = children.toString(j,"Name");
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetCategoryAttributesThread extends Thread
  {
    protected int catObjectID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetCategoryAttributesThread(int catObjectID)
    {
      super();
      setDaemon(true);
      this.catObjectID = catObjectID;
    }

    public void run()
    {
      try
      {
        LLValue catID = new LLValue();
        catID.setAssoc();
        catID.add("ID", catObjectID);
        catID.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

        LLValue catVersion = new LLValue();
        int status = LLDocs.FetchCategoryVersion(catID,catVersion);
        if (status == 107105 || status == 107106)
          return;
        if (status != 0)
        {
          throw new ACFException("Error getting category version: "+Integer.toString(status));
        }

        LLValue children = new LLValue();
        status = LLAttributes.AttrListNames(catVersion,null,children);
        if (status != 0)
        {
          throw new ACFException("Error getting attribute names: "+Integer.toString(status));
        }
        rval = children;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }


  /** Given a category path, get a list of legal attribute names.
  *@param catObjectID is the object id of the category.
  *@return a list of attribute names, in sorted order, or null of the path was invalid.
  */
  protected String[] getCategoryAttributes(int catObjectID)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetCategoryAttributesThread t = new GetCategoryAttributesThread(catObjectID);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }

        LLValue children = t.getResponse();
        if (children == null)
          return null;

        String[] rval = new String[children.size()];
        Enumeration en = children.enumerateValues();

        int j = 0;
        while (en.hasMoreElements())
        {
          LLValue v = (LLValue)en.nextElement();
          rval[j] = v.toString();
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetCategoryVersionThread extends Thread
  {
    protected int objID;
    protected int catID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetCategoryVersionThread(int objID, int catID)
    {
      super();
      setDaemon(true);
      this.objID = objID;
      this.catID = catID;
    }

    public void run()
    {
      try
      {
        // Set up the right llvalues

        // Object ID
        LLValue objIDValue = new LLValue().setAssoc();
        objIDValue.add("ID", objID);
        // Current version, so don't set the "Version" field

        // CatID
        LLValue catIDValue = new LLValue().setAssoc();
        catIDValue.add("ID", catID);
        catIDValue.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

        LLValue rvalue = new LLValue();

        int status = LLDocs.GetObjectAttributesEx(objIDValue,catIDValue,rvalue);
        // If either the object is wrong, or the object does not have the specified category, return null.
        if (status == 103101 || status == 107205)
          return;

        if (status != 0)
        {
          throw new ACFException("Error retrieving category version: "+Integer.toString(status)+": "+llServer.getErrors());
        }

        rval = rvalue;

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /** Get a category version for document.
  */
  protected LLValue getCatVersion(int objID, int catID)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetCategoryVersionThread t = new GetCategoryVersionThread(objID,catID);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }
        return t.getResponse();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (NullPointerException npe)
      {
        // LAPI throws a null pointer exception under very rare conditions when the GetObjectAttributesEx is
        // called.  The conditions are not clear at this time - it could even be due to Livelink corruption.
        // However, I'm going to have to treat this as
        // indicating that this category version does not exist for this document.
        Logging.connectors.warn("Livelink: Null pointer exception thrown trying to get cat version for category "+
          Integer.toString(catID)+" for object "+Integer.toString(objID));
        return null;
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetAttributeValueThread extends Thread
  {
    protected LLValue categoryVersion;
    protected String attributeName;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetAttributeValueThread(LLValue categoryVersion, String attributeName)
    {
      super();
      setDaemon(true);
      this.categoryVersion = categoryVersion;
      this.attributeName = attributeName;
    }

    public void run()
    {
      try
      {
        // Set up the right llvalues
        LLValue children = new LLValue();
        int status = LLAttributes.AttrGetValues(categoryVersion,attributeName,
          0,null,children);
        // "Not found" status - I don't know if it possible to get this here, but if so, behave civilly
        if (status == 103101)
          return;
        // This seems to be the real error LAPI returns if you don't have an attribute of this name
        if (status == 8000604)
          return;

        if (status != 0)
        {
          throw new ACFException("Error retrieving attribute value: "+Integer.toString(status)+": "+llServer.getErrors());
        }
        rval = children;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /** Get an attribute value from a category version.
  */
  protected String[] getAttributeValue(LLValue categoryVersion, String attributeName)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetAttributeValueThread t = new GetAttributeValueThread(categoryVersion, attributeName);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }
        LLValue children = t.getResponse();
        if (children == null)
          return null;
        String[] rval = new String[children.size()];
        Enumeration en = children.enumerateValues();

        int j = 0;
        while (en.hasMoreElements())
        {
          LLValue v = (LLValue)en.nextElement();
          rval[j] = v.toString();
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetObjectRightsThread extends Thread
  {
    protected int vol;
    protected int objID;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectRightsThread(int vol, int objID)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.objID = objID;
    }

    public void run()
    {
      try
      {
        LLValue childrenObjects = new LLValue();
        int status = LLDocs.GetObjectRights(vol, objID, childrenObjects);
        // If the rights object doesn't exist, behave civilly
        if (status == 103101)
          return;

        if (status != 0)
        {
          throw new ACFException("Error retrieving document rights: "+Integer.toString(status)+": "+llServer.getErrors());
        }

        rval = childrenObjects;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /** Get an object's rights.  This will be an array of right id's, including the special
  * ones defined by Livelink, or null will be returned (if the object is not found).
  *@param vol is the volume id
  *@param objID is the object id
  *@return the array.
  */
  protected int[] getObjectRights(int vol, int objID)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetObjectRightsThread t = new GetObjectRightsThread(vol,objID);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }

        LLValue childrenObjects = t.getResponse();
        if (childrenObjects == null)
          return null;

        int size;
        if (childrenObjects.isRecord())
          size = 1;
        else if (childrenObjects.isTable())
          size = childrenObjects.size();
        else
          size = 0;

        int minPermission = LAPI_DOCUMENTS.PERM_SEE +
          LAPI_DOCUMENTS.PERM_SEECONTENTS;


        int j = 0;
        int count = 0;
        while (j < size)
        {
          int permission = childrenObjects.toInteger(j, "Permissions");
          // Only if the permission is "see contents" can we consider this
          // access token!
          if ((permission & minPermission) == minPermission)
            count++;
          j++;
        }

        int[] rval = new int[count];
        j = 0;
        count = 0;
        while (j < size)
        {
          int token = childrenObjects.toInteger(j, "RightID");
          int permission = childrenObjects.toInteger(j, "Permissions");
          // Only if the permission is "see contents" can we consider this
          // access token!
          if ((permission & minPermission) == minPermission)
            rval[count++] = token;
          j++;
        }
        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  protected class GetObjectInfoThread extends Thread
  {
    protected int vol;
    protected int id;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectInfoThread(int vol, int id)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.id = id;
    }

    public void run()
    {
      try
      {
        LLValue objinfo = new LLValue().setAssocNotSet();
        int status = LLDocs.GetObjectInfo(vol,id,objinfo);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Status retrieved for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status));
        }

        // Treat both 103101 and 103102 as 'object not found'.
        if (status == 103101 || status == 103102)
          return;

        // This error means we don't have permission to get the object's status, apparently
        if (status < 0)
        {
          Logging.connectors.debug("Livelink: Object info inaccessable for object "+Integer.toString(vol)+":"+Integer.toString(id)+
            " ("+llServer.getErrors()+")");
          return;
        }

        if (status != 0)
        {
          throw new ACFException("Error retrieving document object "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = objinfo;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /**
  * Returns an Assoc value object containing information
  * about the specified object.
  * @param vol is the volume id (which comes from the project)
  * @param id the object ID
  * @return LLValue the LAPI value object, or null if object has been deleted (or doesn't exist)
  */
  protected LLValue getObjectInfo(int vol, int id)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetObjectInfoThread t = new GetObjectInfoThread(vol,id);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ServiceInterruption)
            throw (ServiceInterruption)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }
        return t.getResponse();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  /** Build a set of actual acls given a set of rights */
  protected String[] lookupTokens(int[] rights, int vol, int objID)
    throws ACFException, ServiceInterruption
  {
    String[] convertedAcls = new String[rights.length];

    LLValue infoObject = null;
    int j = 0;
    int k = 0;
    while (j < rights.length)
    {
      int token = rights[j++];
      String tokenValue;
      // Consider this token
      switch (token)
      {
      case LAPI_DOCUMENTS.RIGHT_OWNER:
        // Look up user for current document (UserID attribute)
        if (infoObject == null)
        {
          infoObject = getObjectInfo(vol, objID);
          if (infoObject == null)
          {
            tokenValue = null;
            break;
          }
        }
        tokenValue = Integer.toString(infoObject.toInteger("UserID"));
        break;
      case LAPI_DOCUMENTS.RIGHT_GROUP:
        // Look up group for current document (GroupID attribute)
        if (infoObject == null)
        {
          infoObject = getObjectInfo(vol, objID);
          if (infoObject == null)
          {
            tokenValue = null;
            break;
          }
        }
        tokenValue = Integer.toString(infoObject.toInteger("GroupID"));
        break;
      case LAPI_DOCUMENTS.RIGHT_WORLD:
        // Add "Guest" token
        tokenValue = "GUEST";
        break;
      case LAPI_DOCUMENTS.RIGHT_SYSTEM:
        // Add "System" token
        tokenValue = "SYSTEM";
        break;
      default:
        tokenValue = Integer.toString(token);
        break;
      }

      // This might return a null if we could not look up the object corresponding to the right.  If so, it is safe to skip it because
      // that always RESTRICTS view of the object (maybe erroneously), but does not expand visibility.
      if (tokenValue != null)
        convertedAcls[k++] = tokenValue;
    }
    String[] actualAcls = new String[k];
    j = 0;
    while (j < k)
    {
      actualAcls[j] = convertedAcls[j];
      j++;
    }
    return actualAcls;
  }

  /** Get an object's standard path, given its ID.
  */
  protected String getObjectPath(int id)
    throws ACFException, ServiceInterruption
  {
    int objectId = id;
    String path = null;
    while (true)
    {
      // Load the object. I'm told I can use zero for a volume ID safely.
      LLValue x = getObjectInfo(0,objectId);
      if (x == null)
      {
        // The document identifier describes a path that does not exist.
        // This is unexpected, but an exception would terminate the job, and we don't want that.
        Logging.connectors.warn("Livelink: Bad identifier found? "+Integer.toString(id)+" apparently does not exist, but need to look up its path");
        return null;
      }

      if (objectId == LLCATWK_ID)
        return CATEGORY_NAME + ":" + path;
      else if (objectId == LLENTWK_ID)
        return ENTWKSPACE_NAME + ":" + path;

      // Get the name attribute
      String name = x.toString("Name");
      if (path == null)
        path = name;
      else
        path = name + "/" + path;

      // Get the parentID attribute
      int parentID = x.toInteger("ParentID");
      if (parentID == -1)
      {
        // Oops, hit the top of the path without finding the workspace we're in.
        // No idea where it lives; note this condition and exit.
        Logging.connectors.warn("Livelink: Object ID "+Integer.toString(id)+" doesn't seem to live in enterprise or category workspace!  Path I got was '"+path+"'");
        return null;
      }
      objectId = parentID;
    }
  }

  protected class GetObjectCategoryIDsThread extends Thread
  {
    protected int vol;
    protected int id;
    protected Throwable exception = null;
    protected LLValue rval = null;

    public GetObjectCategoryIDsThread(int vol, int id)
    {
      super();
      setDaemon(true);
      this.vol = vol;
      this.id = id;
    }

    public void run()
    {
      try
      {
        // Object ID
        LLValue objIDValue = new LLValue().setAssocNotSet();
        objIDValue.add("ID", id);

        // Category ID List
        LLValue catIDList = new LLValue().setAssocNotSet();

        int status = LLDocs.ListObjectCategoryIDs(objIDValue,catIDList);

        // Need to detect if object was deleted, and return null in this case!!!
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Status value for getting object categories for "+Integer.toString(vol)+":"+Integer.toString(id)+" is: "+Integer.toString(status));
        }

        if (status == 103101)
          return;

        if (status != 0)
        {
          throw new ACFException("Error retrieving document categories for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
        }
        rval = catIDList;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public LLValue getResponse()
    {
      return rval;
    }
  }

  /** Get category IDs associated with an object.
  * @param vol is the volume ID
  * @param id the object ID
  * @return an array of integers containing category identifiers, or null if the object is not found.
  */
  protected int[] getObjectCategoryIDs(int vol, int id)
    throws ACFException, ServiceInterruption
  {
    int sanityRetryCount = FAILURE_RETRY_COUNT;
    while (true)
    {
      GetObjectCategoryIDsThread t = new GetObjectCategoryIDsThread(vol,id);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof ACFException)
          {
            sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
            continue;
          }
          else
            throw (Error)thr;
        }

        LLValue catIDList = t.getResponse();
        if (catIDList == null)
          return null;

        int size = catIDList.size();

        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(size)+" attached categories");
        }

        // Count the category ids
        int count = 0;
        int j = 0;
        while (j < size)
        {
          int type = catIDList.toValue(j).toInteger("Type");
          if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
            count++;
          j++;
        }

        int[] rval = new int[count];

        // Do the scan
        j = 0;
        count = 0;
        while (j < size)
        {
          int type = catIDList.toValue(j).toInteger("Type");
          if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
          {
            int childID = catIDList.toValue(j).toInteger("ID");
            rval[count++] = childID;
          }
          j++;
        }

        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(rval.length)+" attached library categories");
        }

        return rval;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RuntimeException e)
      {
        sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
        continue;
      }
    }
  }

  /** RootValue version of getPathId.
  */
  protected VolumeAndId getPathId(RootValue rv)
    throws ACFException, ServiceInterruption
  {
    return getPathId(rv.getRootValue(),rv.getRemainderPath());
  }

  /**
  * Returns the object ID specified by the path name.
  * @param objInfo a value object containing information about root folder (or workspace) above the specified object
  * @param startPath is the folder name (a string with dots as separators)
  */
  protected VolumeAndId getPathId(LLValue objInfo, String startPath)
    throws ACFException, ServiceInterruption
  {
    // Grab the volume ID and starting object
    int obj = objInfo.toInteger("ID");
    int vol = objInfo.toInteger("VolumeID");

    // Pick apart the start path.  This is a string separated by slashes.
    int charindex = 0;
    while (charindex < startPath.length())
    {
      StringBuffer currentTokenBuffer = new StringBuffer();
      // Find the current token
      while (charindex < startPath.length())
      {
        char x = startPath.charAt(charindex++);
        if (x == '/')
          break;
        if (x == '\\')
        {
          // Attempt to escape what follows
          x = startPath.charAt(charindex);
          charindex++;
        }
        currentTokenBuffer.append(x);
      }

      String subFolder = currentTokenBuffer.toString();
      // We want only folders that are children of the current object and which match the specified subfolder
      String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
        " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ") and Name='" + subFolder + "'";

      int sanityRetryCount = FAILURE_RETRY_COUNT;
      while (true)
      {
        ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else if (thr instanceof ACFException)
            {
              sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
              continue;
            }
            else
              throw (Error)thr;
          }

          LLValue children = t.getResponse();
          if (children == null)
            return null;

          // If there is one child, then we are okay.
          if (children.size() == 1)
          {
            // New starting point is the one we found.
            obj = children.toInteger(0, "ID");
            int subtype = children.toInteger(0, "SubType");
            if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
            {
              vol = obj;
              obj = -obj;
            }
          }
          else
          {
            // Couldn't find the path.  Instead of throwing up, return null to indicate
            // illegal node.
            return null;
          }
          break;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
        }
        catch (RuntimeException e)
        {
          sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
          continue;

        }
      }

    }
    return new VolumeAndId(vol,obj);
  }

  /** Rootvalue version of getCategoryId.
  */
  protected int getCategoryId(RootValue rv)
    throws ACFException, ServiceInterruption
  {
    return getCategoryId(rv.getRootValue(),rv.getRemainderPath());
  }

  /**
  * Returns the category ID specified by the path name.
  * @param objInfo a value object containing information about root folder (or workspace) above the specified object
  * @param startPath is the folder name, ending in a category name (a string with slashes as separators)
  */
  protected int getCategoryId(LLValue objInfo, String startPath)
    throws ACFException, ServiceInterruption
  {
    // Grab the volume ID and starting object
    int obj = objInfo.toInteger("ID");
    int vol = objInfo.toInteger("VolumeID");

    // Pick apart the start path.  This is a string separated by slashes.
    if (startPath.length() == 0)
      return -1;

    int charindex = 0;
    while (charindex < startPath.length())
    {
      StringBuffer currentTokenBuffer = new StringBuffer();
      // Find the current token
      while (charindex < startPath.length())
      {
        char x = startPath.charAt(charindex++);
        if (x == '/')
          break;
        if (x == '\\')
        {
          // Attempt to escape what follows
          x = startPath.charAt(charindex);
          charindex++;
        }
        currentTokenBuffer.append(x);
      }
      String subFolder = currentTokenBuffer.toString();
      String filterString;

      // We want only folders that are children of the current object and which match the specified subfolder
      if (charindex < startPath.length())
        filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
        " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";
      else
        filterString = "SubType="+LAPI_DOCUMENTS.CATEGORYSUBTYPE;

      filterString += " and Name='" + subFolder + "'";

      int sanityRetryCount = FAILURE_RETRY_COUNT;
      while (true)
      {
        ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else if (thr instanceof ACFException)
            {
              sanityRetryCount = assessRetry(sanityRetryCount,(ACFException)thr);
              continue;
            }
            else
              throw (Error)thr;
          }

          LLValue children = t.getResponse();
          if (children == null)
            return -1;

          // If there is one child, then we are okay.
          if (children.size() == 1)
          {
            // New starting point is the one we found.
            obj = children.toInteger(0, "ID");
            int subtype = children.toInteger(0, "SubType");
            if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
            {
              vol = obj;
              obj = -obj;
            }
          }
          else
          {
            // Couldn't find the path.  Instead of throwing up, return null to indicate
            // illegal node.
            return -1;
          }
          break;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
        }
        catch (RuntimeException e)
        {
          sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
          continue;
        }
      }
    }
    return obj;
  }

  // Protected static methods

  /** Convert the document specification to a set of query clauses meant to match file names.
  *@param spec is the specification string.
  *@return the correct part of the filter string.  Empty string is returned if there is no filtering.
  */
  protected static String buildFilterString(DocumentSpecification spec)
  {
    StringBuffer rval = new StringBuffer();


    boolean first = true;
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("include"))
      {
        String includeMatch = sn.getAttributeValue("filespec");
        if (includeMatch != null)
        {
          // Peel off the extension
          int index = includeMatch.lastIndexOf(".");
          if (index != -1)
          {
            String type = includeMatch.substring(index+1).toLowerCase().replace('*','%');
            if (first)
              first = false;
            else
              rval.append(" or ");
            rval.append("lower(FileType) like '").append(type).append("'");
          }
        }
      }
    }
    String filterStringPiece = rval.toString();
    if (filterStringPiece.length() == 0)
      return "0>1";
    StringBuffer sb = new StringBuffer();
    sb.append("SubType=").append(new Integer(LAPI_DOCUMENTS.FOLDERSUBTYPE).toString());
    sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE).toString());
    sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.PROJECTSUBTYPE).toString());
    sb.append(" or (SubType=").append(new Integer(LAPI_DOCUMENTS.DOCUMENTSUBTYPE).toString());
    sb.append(" and (");
    // Walk through the document spec to find the documents that match under the specified root
    // include lower(column)=spec
    sb.append(filterStringPiece);
    sb.append("))");
    return sb.toString();
  }


  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(DocumentSpecification spec)
  {
    HashMap map = new HashMap();
    int i = 0;
    boolean securityOn = true;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("access"))
      {
        String token = sn.getAttributeValue("token");
        map.put(token,token);
      }
      else if (sn.getType().equals("security"))
      {
        String value = sn.getAttributeValue("value");
        if (value.equals("on"))
          securityOn = true;
        else if (value.equals("off"))
          securityOn = false;
      }
    }
    if (!securityOn)
      return null;

    String[] rval = new String[map.size()];
    Iterator iter = map.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    return rval;
  }

  /** Check if a file or directory should be included, given a document specification.
  *@param filename is the name of the "file".
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected static boolean checkInclude(String filename, DocumentSpecification documentSpecification)
    throws ACFException
  {
    // Scan includes to insure we match
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i);
      if (sn.getType().equals("include"))
      {
        String filespec = sn.getAttributeValue("filespec");
        // If it matches, we can exit this loop.
        if (checkMatch(filename,0,filespec))
          break;
      }
      i++;
    }
    if (i == documentSpecification.getChildCount())
      return false;

    // We matched an include.  Now, scan excludes to ditch it if needed.
    i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i);
      if (sn.getType().equals("exclude"))
      {
        String filespec = sn.getAttributeValue("filespec");
        // If it matches, we return false.
        if (checkMatch(filename,0,filespec))
          return false;
      }
      i++;
    }

    // System.out.println("Match!");
    return true;
  }

  /** Check if a file should be ingested, given a document specification.  It is presumed that
  * documents that pass checkInclude() will be checked with this method.
  *@param objID is the file ID.
  *@param documentSpecification is the specification.
  */
  protected boolean checkIngest(int objID, DocumentSpecification documentSpecification)
    throws ACFException
  {
    // Since the only exclusions at this point are not based on file contents, this is a no-op.
    return true;
  }

  /** Check a match between two strings with wildcards.
  *@param sourceMatch is the expanded string (no wildcards)
  *@param sourceIndex is the starting point in the expanded string.
  *@param match is the wildcard-based string.
  *@return true if there is a match.
  */
  protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
  {
    // Note: The java regex stuff looks pretty heavyweight for this purpose.
    // I've opted to try and do a simple recursive version myself, which is not compiled.
    // Basically, the match proceeds by recursive descent through the string, so that all *'s cause
    // recursion.
    boolean caseSensitive = false;

    return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
  }

  /** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
  * strings in their entirety in a matched way.
  *@param caseSensitive is true if file names are case sensitive.
  *@param sourceMatch is the source string (w/o wildcards)
  *@param sourceIndex is the current point in the source string.
  *@param match is the match string (w/wildcards)
  *@param matchIndex is the current point in the match string.
  *@return true if there is a match.
  */
  protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
    String match, int matchIndex)
  {
    // Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
    //      " against '"+match+"' position "+Integer.toString(matchIndex));

    // Match up through the next * we encounter
    while (true)
    {
      // If we've reached the end, it's a match.
      if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
        return true;
      // If one has reached the end but the other hasn't, no match
      if (match.length() == matchIndex)
        return false;
      if (sourceMatch.length() == sourceIndex)
      {
        if (match.charAt(matchIndex) != '*')
          return false;
        matchIndex++;
        continue;
      }
      char x = sourceMatch.charAt(sourceIndex);
      char y = match.charAt(matchIndex);
      if (!caseSensitive)
      {
        if (x >= 'A' && x <= 'Z')
          x -= 'A'-'a';
        if (y >= 'A' && y <= 'Z')
          y -= 'A'-'a';
      }
      if (y == '*')
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
          processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
      }
      if (y == '?' || x == y)
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /** Class for returning volume id/folder id combination on path lookup.
  */
  protected static class VolumeAndId
  {
    protected int volumeID;
    protected int folderID;

    public VolumeAndId(int volumeID, int folderID)
    {
      this.volumeID = volumeID;
      this.folderID = folderID;
    }

    public int getVolumeID()
    {
      return volumeID;
    }

    public int getPathId()
    {
      return folderID;
    }
  }

  /** Class that describes a metadata catid and path.
  */
  protected static class MetadataPathItem
  {
    protected int catID;
    protected String catName;

    /** Constructor.
    */
    public MetadataPathItem(int catID, String catName)
    {
      this.catID = catID;
      this.catName = catName;
    }

    /** Get the cat ID.
    *@return the id.
    */
    public int getCatID()
    {
      return catID;
    }

    /** Get the cat name.
    *@return the category name path.
    */
    public String getCatName()
    {
      return catName;
    }

  }

  /** Class that describes a metadata catid and attribute set.
  */
  protected static class MetadataItem
  {
    protected MetadataPathItem pathItem;
    protected Map attributeNames = new HashMap();

    /** Constructor.
    */
    public MetadataItem(MetadataPathItem pathItem)
    {
      this.pathItem = pathItem;
    }

    /** Add an attribute name.
    */
    public void addAttribute(String attributeName)
    {
      attributeNames.put(attributeName,attributeName);
    }

    /** Get the path object.
    *@return the object.
    */
    public MetadataPathItem getPathItem()
    {
      return pathItem;
    }

    /** Get an iterator over the attribute names.
    *@return the iterator.
    */
    public Iterator getAttributeNames()
    {
      return attributeNames.keySet().iterator();
    }

  }

  /** Class that tracks paths associated with nodes, and also keeps track of the name
  * of the metadata attribute to use for the path.
  */
  protected class SystemMetadataDescription
  {
    // The path attribute name
    protected String pathAttributeName;

    // The node ID to path name mapping (which acts like a cache)
    protected Map pathMap = new HashMap();

    // The path name map
    protected MatchMap matchMap = new MatchMap();

    /** Constructor */
    public SystemMetadataDescription(DocumentSpecification spec)
      throws ACFException
    {
      pathAttributeName = null;
      int i = 0;
      while (i < spec.getChildCount())
      {
        SpecificationNode n = spec.getChild(i++);
        if (n.getType().equals("pathnameattribute"))
          pathAttributeName = n.getAttributeValue("value");
        else if (n.getType().equals("pathmap"))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
      }
    }

    /** Get the path attribute name.
    *@return the path attribute name, or null if none specified.
    */
    public String getPathAttributeName()
    {
      return pathAttributeName;
    }

    /** Given an identifier, get the translated string that goes into the metadata.
    */
    public String getPathAttributeValue(String documentIdentifier)
      throws ACFException, ServiceInterruption
    {
      String path = getNodePathString(documentIdentifier);
      if (path == null)
        return null;
      return matchMap.translate(path);
    }

    /** For a given node, get its path.
    */
    public String getNodePathString(String documentIdentifier)
      throws ACFException, ServiceInterruption
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Looking up path for '"+documentIdentifier+"'");
      String path = (String)pathMap.get(documentIdentifier);
      if (path == null)
      {
        // Not yet present.  Look it up, recursively
        String identifierPart = documentIdentifier;
        // Get the current node's name first
        // D = Document; anything else = Folder
        if (identifierPart.startsWith("D") || identifierPart.startsWith("F"))
        {
          // Strip off the letter
          identifierPart = identifierPart.substring(1);
        }
        // See if there's a volume label; if not, use the default.
        int colonPosition = identifierPart.indexOf(":");
        int volumeID;
        int objectID;
        try
        {
          if (colonPosition == -1)
          {
            // Default volume ID
            volumeID = LLENTWK_VOL;
            objectID = Integer.parseInt(identifierPart);
          }
          else
          {
            volumeID = Integer.parseInt(identifierPart.substring(0,colonPosition));
            objectID = Integer.parseInt(identifierPart.substring(colonPosition+1));
          }
        }
        catch (NumberFormatException e)
        {
          throw new ACFException("Bad document identifier: "+e.getMessage(),e);
        }

        // Load the object
        LLValue x = getObjectInfo(volumeID,objectID);
        if (x == null)
        {
          // The document identifier describes a path that does not exist.
          // This is unexpected, but don't die: just log a warning and allow the higher level to deal with it.
          Logging.connectors.warn("Livelink: Bad document identifier: '"+documentIdentifier+"' apparently does not exist, but need to find its path");
          return null;
        }

        // Get the name attribute
        String name = x.toString("Name");
        // Get the parentID attribute
        int parentID = x.toInteger("ParentID");
        if (parentID == -1)
          path = name;
        else
        {
          String parentIdentifier = "F"+Integer.toString(volumeID)+":"+Integer.toString(parentID);
          String parentPath = getNodePathString(parentIdentifier);
          if (parentPath == null)
            return null;
          path = parentPath + "/"+ name;
        }

        pathMap.put(documentIdentifier,path);
      }

      return path;
    }
  }


  /** Class that manages to find catid's and attribute names that have been specified.
  * This accepts a part of the version string which contains the string-ified metadata
  * spec, rather than pulling it out of the document specification.  That guarantees that
  * the version string actually corresponds to the document that was ingested.
  */
  protected class MetadataDescription
  {
    // This is a map of category name to category ID and attributes
    protected Map categoryMap = new HashMap();

    /** Constructor.
    */
    public MetadataDescription()
    {
    }

    /** Iterate over the metadata items represented by the specified chunk of version string.
    *@return an iterator over MetadataItem objects.
    */
    public Iterator getItems(ArrayList metadataItems)
      throws ACFException, ServiceInterruption
    {
      // This is the map that will be iterated over for a return value.
      // It gets built out of (hopefully cached) data from categoryMap.
      HashMap newMap = new HashMap();

      // Start at root
      LLValue rootValue = null;

      // Walk through string and process each metadata element in turn.
      int i = 0;
      while (i < metadataItems.size())
      {
        String metadataSpec = (String)metadataItems.get(i++);
        StringBuffer categoryBuffer = new StringBuffer();
        StringBuffer attributeBuffer = new StringBuffer();
        unpackCategoryAttribute(categoryBuffer,attributeBuffer,metadataSpec);
        String category = categoryBuffer.toString();
        String attributeName = attributeBuffer.toString();

        // If there's already an entry for this category in the return map, use it
        MetadataItem mi = (MetadataItem)newMap.get(category);
        if (mi == null)
        {
          // Now, look up the node information
          // Convert category to cat id.
          MetadataPathItem item = (MetadataPathItem)categoryMap.get(category);
          if (item == null)
          {
            RootValue rv = new RootValue(category);
            if (rootValue == null)
            {
              rootValue = rv.getRootValue();
            }

            // Get the object id of the category the path describes.
            // NOTE: We don't use the RootValue version of getCategoryId because
            // we want to use the cached value of rootValue, if it was around.
            int catObjectID = getCategoryId(rootValue,rv.getRemainderPath());
            if (catObjectID != -1)
            {
              item = new MetadataPathItem(catObjectID,rv.getRemainderPath());
              categoryMap.put(category,item);
            }
          }
          mi = new MetadataItem(item);
          newMap.put(category,mi);
        }
        // Add attribute name to category
        mi.addAttribute(attributeName);
      }

      return newMap.values().iterator();
    }

  }


  /** This class caches the category path strings associated with a given category object identifier.
  * The goal is to allow reasonably speedy lookup of the path name, so we can put it into the metadata part of the
  * version string.
  */
  protected class CategoryPathAccumulator
  {
    // This is the map from category ID to category path name.
    // It's keyed by an Integer formed from the id, and has String values.
    protected HashMap categoryPathMap = new HashMap();

    // This is the map from category ID to attribute names.  Keyed
    // by an Integer formed from the id, and has a String[] value.
    protected HashMap attributeMap = new HashMap();

    /** Constructor */
    public CategoryPathAccumulator()
    {
    }

    /** Get a specified set of packed category paths with attribute names, given the category identifiers */
    public String[] getCategoryPathsAttributeNames(int[] catIDs)
      throws ACFException, ServiceInterruption
    {
      HashMap set = new HashMap();
      int i = 0;
      while (i < catIDs.length)
      {
        Integer key = new Integer(catIDs[i++]);
        String pathValue = (String)categoryPathMap.get(key);
        if (pathValue == null)
        {
          // Chase the path back up the chain
          pathValue = findPath(key.intValue());
          if (pathValue == null)
            continue;
          categoryPathMap.put(key,pathValue);
        }
        String[] attributeNames = (String[])attributeMap.get(key);
        if (attributeNames == null)
        {
          // Get the attributes for this category
          attributeNames = findAttributes(key.intValue());
          if (attributeNames == null)
            continue;
          attributeMap.put(key,attributeNames);
        }
        // Now, put the path and the attributes into the hash.
        int j = 0;
        while (j < attributeNames.length)
        {
          String metadataName = packCategoryAttribute(pathValue,attributeNames[j++]);
          set.put(metadataName,metadataName);
        }
      }

      String[] rval = new String[set.size()];
      i = 0;
      Iterator iter = set.keySet().iterator();
      while (iter.hasNext())
      {
        rval[i++] = (String)iter.next();
      }

      return rval;
    }

    /** Find a category path given a category ID */
    protected String findPath(int catID)
      throws ACFException, ServiceInterruption
    {
      return getObjectPath(catID);
    }

    /** Find a set of attributes given a category ID */
    protected String[] findAttributes(int catID)
      throws ACFException, ServiceInterruption
    {
      return getCategoryAttributes(catID);
    }

  }

  /** Class representing a root value object, plus remainder string.
  * This class peels off the workspace name prefix from a path string or
  * attribute string, and finds the right workspace root node and remainder
  * path.
  */
  protected class RootValue
  {
    protected String workspaceName;
    protected LLValue rootValue = null;
    protected String remainderPath;

    /** Constructor.
    *@param pathString is the path string.
    */
    public RootValue(String pathString)
    {
      int colonPos = pathString.indexOf(":");
      if (colonPos == -1)
      {
        remainderPath = pathString;
        workspaceName = ENTWKSPACE_NAME;
      }
      else
      {
        workspaceName = pathString.substring(0,colonPos);
        remainderPath = pathString.substring(colonPos+1);
      }
    }

    /** Get the path string.
    *@return the path string (without the workspace name prefix).
    */
    public String getRemainderPath()
    {
      return remainderPath;
    }

    /** Get the root node.
    *@return the root node.
    */
    public LLValue getRootValue()
      throws ACFException, ServiceInterruption
    {
      if (rootValue == null)
      {
        if (workspaceName.equals(CATEGORY_NAME))
          rootValue = getObjectInfo(LLCATWK_VOL,LLCATWK_ID);
        else if (workspaceName.equals(ENTWKSPACE_NAME))
          rootValue = getObjectInfo(LLENTWK_VOL,LLENTWK_ID);
        else
          throw new ACFException("Bad workspace name: "+workspaceName);
        if (rootValue == null)
        {
          Logging.connectors.warn("Livelink: Could not get workspace/volume ID!  Retrying...");
          // This cannot mean a real failure; it MUST mean that we have had an intermittent communication hiccup.  So, pass it off as a service interruption.
          throw new ServiceInterruption("Service interruption getting root value",new ACFException("Could not get workspace/volume id"),System.currentTimeMillis()+60000L,
            System.currentTimeMillis()+600000L,-1,true);
        }
      }

      return rootValue;
    }
  }

  /** HTTPClient secure socket factory, which implements SecureProtocolSocketFactory
  */
  protected static class LivelinkSecureSocketFactory implements SecureProtocolSocketFactory
  {
    /** This is the javax.net socket factory.
    */
    protected javax.net.ssl.SSLSocketFactory socketFactory;

    /** Constructor */
    public LivelinkSecureSocketFactory(javax.net.ssl.SSLSocketFactory socketFactory)
    {
      this.socketFactory = socketFactory;
    }

    public Socket createSocket(
      String host,
      int port,
      InetAddress clientHost,
      int clientPort)
      throws IOException, UnknownHostException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
      return socketFactory.createSocket(
        host,
        port,
        clientHost,
        clientPort
      );
    }

    public Socket createSocket(
      final String host,
      final int port,
      final InetAddress localAddress,
      final int localPort,
      final HttpConnectionParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));

      if (params == null)
      {
        throw new IllegalArgumentException("Parameters may not be null");
      }
      int timeout = params.getConnectionTimeout();
      if (timeout == 0)
      {
        return createSocket(host, port, localAddress, localPort);
      }
      else
        throw new IllegalArgumentException("This implementation does not handle non-zero connection timeouts");
    }

    public Socket createSocket(String host, int port)
      throws IOException, UnknownHostException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
      return socketFactory.createSocket(
        host,
        port
      );
    }

    public Socket createSocket(
      Socket socket,
      String host,
      int port,
      boolean autoClose)
      throws IOException, UnknownHostException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
      return socketFactory.createSocket(
        socket,
        host,
        port,
        autoClose
      );
    }

    public boolean equals(Object obj)
    {
      if (obj == null || !(obj instanceof LivelinkSecureSocketFactory))
        return false;
      // Each object is unique
      return super.equals(obj);
    }

    public int hashCode()
    {
      return super.hashCode();
    }

  }

  // Here's an interesting note.  All of the LAPI exceptions are subclassed off of RuntimeException.  This makes life
  // hell because there is no superclass exception to capture, and even tweaky server communication issues wind up throwing
  // uncaught RuntimeException's up the stack.
  //
  // To fix this rather bad design, all places that invoke LAPI need to catch RuntimeException and run it through the following
  // method for interpretation and logging.
  //

  /** Interpret runtimeexception to search for livelink API errors.  Throws an appropriately reinterpreted exception, or
  * just returns if the exception indicates that a short-cycle retry attempt should be made.  (In that case, the appropriate
  * wait has been already performed).
  *@param e is the RuntimeException caught
  *@param failIfTimeout is true if, for transient conditions, we want to signal failure if the timeout condition is acheived.
  */
  protected int handleLivelinkRuntimeException(RuntimeException e, int sanityRetryCount, boolean failIfTimeout)
    throws ACFException, ServiceInterruption
  {
    if (
      e instanceof com.opentext.api.LLHTTPAccessDeniedException ||
      e instanceof com.opentext.api.LLHTTPClientException ||
      e instanceof com.opentext.api.LLHTTPServerException ||
      e instanceof com.opentext.api.LLIndexOutOfBoundsException ||
      e instanceof com.opentext.api.LLNoFieldSpecifiedException ||
      e instanceof com.opentext.api.LLNoValueSpecifiedException ||
      e instanceof com.opentext.api.LLSecurityProviderException ||
      e instanceof com.opentext.api.LLUnknownFieldException
    )
    {
      String details = llServer.getErrors();
      throw new ACFException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e,ACFException.REPOSITORY_CONNECTION_ERROR);
    }
    else if (
      e instanceof com.opentext.api.LLBadServerCertificateException ||
      e instanceof com.opentext.api.LLHTTPCGINotFoundException ||
      e instanceof com.opentext.api.LLCouldNotConnectHTTPException ||
      e instanceof com.opentext.api.LLHTTPForbiddenException ||
      e instanceof com.opentext.api.LLHTTPProxyAuthRequiredException ||
      e instanceof com.opentext.api.LLHTTPRedirectionException ||
      e instanceof com.opentext.api.LLUnsupportedAuthMethodException ||
      e instanceof com.opentext.api.LLWebAuthInitException
    )
    {

      String details = llServer.getErrors();
      throw new ACFException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e);
    }
    else if (e instanceof com.opentext.api.LLIllegalOperationException)
    {
      // This usually means that LAPI has had a minor communication difficulty but hasn't reported it accurately.
      // We *could* throw a ServiceInterruption, but OpenText recommends to just retry almost immediately.
      String details = llServer.getErrors();
      return assessRetry(sanityRetryCount,new ACFException("Livelink API illegal operation error: "+e.getMessage()+((details==null)?"":"; "+details),e));
    }
    else if (e instanceof com.opentext.api.LLIOException)
    {
      // Treat this as a transient error; try again in 5 minutes, and only fail after 12 hours of trying

      // LAPI is returning errors that are not terribly explicit, and I don't have control over their wording, so check that server can be resolved by DNS,
      // so that a better error message can be returned.
      try
      {
        InetAddress.getByName(serverName);
      }
      catch (UnknownHostException e2)
      {
        throw new ACFException("Server name '"+serverName+"' cannot be resolved",e2);
      }

      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption(e.getMessage(),e,currentTime + 5*60000L,currentTime+12*60*60000L,-1,failIfTimeout);
    }
    else
      throw e;
  }

  /** Do a retry, or throw an exception if the retry count has been exhausted
  */
  protected static int assessRetry(int sanityRetryCount, ACFException e)
    throws ACFException
  {
    if (sanityRetryCount == 0)
    {
      throw e;
    }

    sanityRetryCount--;

    try
    {
      ACF.sleep(1000L);
    }
    catch (InterruptedException e2)
    {
      throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
    }
    // Exit the method
    return sanityRetryCount;

  }

}


