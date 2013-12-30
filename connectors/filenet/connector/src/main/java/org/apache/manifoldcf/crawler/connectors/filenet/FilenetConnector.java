/* $Id: FilenetConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.filenet;

import org.apache.log4j.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;
import org.apache.manifoldcf.crawler.common.filenet.*;
import java.rmi.*;
import java.text.SimpleDateFormat;


public class FilenetConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: FilenetConnector.java 996524 2010-09-13 13:38:01Z kwright $";

  // Parameters
  public static final String CONFIG_PARAM_USERID = "User ID";
  public static final String CONFIG_PARAM_PASSWORD = "Password";
  public static final String CONFIG_PARAM_FILENETDOMAIN_OLD = "Filenet domain";
  public static final String CONFIG_PARAM_FILENETDOMAIN = "FileNet domain";
  public static final String CONFIG_PARAM_OBJECTSTORE = "Object store";
  public static final String CONFIG_PARAM_SERVERPROTOCOL = "Server protocol";
  public static final String CONFIG_PARAM_SERVERHOSTNAME = "Server hostname";
  public static final String CONFIG_PARAM_SERVERPORT = "Server port";
  public static final String CONFIG_PARAM_SERVERWSILOCATION = "Server WebServices location";
  public static final String CONFIG_PARAM_URLPROTOCOL = "Document URL protocol";
  public static final String CONFIG_PARAM_URLHOSTNAME = "Document URL hostname";
  public static final String CONFIG_PARAM_URLPORT = "Document URL port";
  public static final String CONFIG_PARAM_URLLOCATION = "Document URL location";

  // Specification nodes
  public static final String SPEC_NODE_FOLDER = "folder";
  public static final String SPEC_NODE_MIMETYPE = "mimetype";
  public static final String SPEC_NODE_DOCUMENTCLASS = "documentclass";
  // This specification node is only ever a child of SPEC_NODE_DOCUMENTCLASS
  public static final String SPEC_NODE_METADATAFIELD = "metafield";
  // This specification node is only ever a child of SPEC_NODE_DOCUMENTCLASS
  public static final String SPEC_NODE_MATCH = "match";

  // Specification attributes
  public static final String SPEC_ATTRIBUTE_VALUE = "value";
  public static final String SPEC_ATTRIBUTE_ALLMETADATA = "allmetadata";
  public static final String SPEC_ATTRIBUTE_MATCHTYPE = "matchtype";
  public static final String SPEC_ATTRIBUTE_FIELDNAME = "fieldname";

  // Activities
  public static final String ACTIVITY_FETCH = "fetch";

  protected static final long timeToRelease = 300000L;

  /** Filenet session handle. */
  protected IFilenet session = null;
  /** Time last session was created */
  protected long lastSessionFetch = -1L;
  /** Username */
  protected String userID = null;
  /** Password */
  protected String password = null;
  /** Filenet domain */
  protected String filenetDomain = null;
  /** Object store */
  protected String objectStore = null;
  /** Server protocol */
  protected String serverProtocol = null;
  /** Server host name */
  protected String serverHostname = null;
  /** Server port */
  protected String serverPort = null;
  /** Server location */
  protected String serverLocation = null;
  /** URI to get us to the webservices integration */
  protected String serverWSIURI = null;
  /** Document URI server protocol */
  protected String docUrlServerProtocol = null;
  /** Document URI server name */
  protected String docUrlServerName = null;
  /** Document URI port */
  protected String docUrlPort = null;
  /** Document URI location */
  protected String docUrlLocation = null;
  /** Document URI protocol, server, port, and location */
  protected String docURIPrefix = null;

  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = GLOBAL_DENY_TOKEN;

  protected class GetSessionThread extends Thread
  {
    protected IFilenet rval = null;
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
        // Create a session
        IFilenetFactory df = (IFilenetFactory)Naming.lookup("rmi://127.0.0.1:8305/filenet_factory");
        IFilenet newSession = df.make();
        newSession.createSession(userID,password,filenetDomain,objectStore,serverWSIURI);
        session = newSession;
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

  /** Get a DFC session.  This will be done every time it is needed.
  */
  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
  {
    if (session == null)
    {
      // Check for parameter validity
      if (userID == null || userID.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_USERID+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: UserID = '" + userID + "'");

      if (password == null || password.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

      Logging.connectors.debug("FileNet: Password exists");

      if (objectStore == null || objectStore.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_OBJECTSTORE+" required but not set");

      if (serverProtocol == null || serverProtocol.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_SERVERPROTOCOL+" required but not set");

      if (serverHostname == null || serverHostname.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_SERVERHOSTNAME+" required but not set");

      if (serverPort != null && serverPort.length() < 1)
        serverPort = null;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: Server URI is '"+serverWSIURI+"'");

      if (docUrlServerProtocol == null || docUrlServerProtocol.length() == 0)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_URLPROTOCOL+" required but not set");

      if (docUrlServerName == null || docUrlServerName.length() == 0)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_URLHOSTNAME+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: Document base URI is '"+docURIPrefix+"'");

      long currentTime;
      GetSessionThread t = new GetSessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof java.net.MalformedURLException)
            throw (java.net.MalformedURLException)thr;
          else if (thr instanceof NotBoundException)
            throw (NotBoundException)thr;
          else if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
      catch (NotBoundException e)
      {
        // Transient problem: Server not available at the moment.
        Logging.connectors.warn("FileNet: RMI server not up at the moment: "+e.getMessage(),e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        // Treat this as a transient problem
        Logging.connectors.warn("FileNet: Transient remote exception creating session: "+e.getMessage(),e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
      }
      catch (FilenetException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("FileNet: Remote service interruption creating session: "+e.getMessage(),e);
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
        }
        throw new ManifoldCFException(e.getMessage(),e);
      }
    }

    // Note that we need the session at this time; this will determine when
    // the session expires.
    lastSessionFetch = System.currentTimeMillis();

  }

  protected class DestroySessionThread extends Thread
  {
    protected Throwable exception = null;

    public DestroySessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.destroySession();
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

  /** Release the session, if it's time.
  */
  protected void releaseCheck()
    throws ManifoldCFException
  {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("FileNet: Transient remote exception closing session: "+e.getMessage(),e);
      }
      catch (FilenetException e)
      {
        session = null;
        lastSessionFetch = -1L;
        // Base our treatment on the kind of error it is.
        if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("FileNet: Remote service interruption closing session: "+e.getMessage(),e);
        }
        else
          Logging.connectors.warn("FileNet: Error closing session: "+e.getMessage(),e);
      }
    }
  }

  /** Constructor.
  */
  public FilenetConnector()
  {
    super();
  }

  /** Let the crawler know the completeness of the information we are giving it.
  */
  @Override
  public int getConnectorModel()
  {
    return MODEL_ADD;
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
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    return new String[]{serverHostname};
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH};
  }

  /** Connect to filenet.
  */
  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // Grab some values for convenience
    userID = configParams.getParameter(CONFIG_PARAM_USERID);
    password = configParams.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    filenetDomain = configParams.getParameter(CONFIG_PARAM_FILENETDOMAIN);
    if (filenetDomain == null)
    {
      filenetDomain = configParams.getParameter(CONFIG_PARAM_FILENETDOMAIN_OLD);
      if (filenetDomain == null)
        filenetDomain = "";
    }

    objectStore = configParams.getParameter(CONFIG_PARAM_OBJECTSTORE);
    serverProtocol = configParams.getParameter(CONFIG_PARAM_SERVERPROTOCOL);
    serverHostname = configParams.getParameter(CONFIG_PARAM_SERVERHOSTNAME);
    serverPort = configParams.getParameter(CONFIG_PARAM_SERVERPORT);
    if (serverPort != null && serverPort.length() < 1)
      serverPort = null;
    serverLocation = configParams.getParameter(CONFIG_PARAM_SERVERWSILOCATION);
    if (serverLocation != null && serverLocation.length() < 1)
      serverLocation = null;

    serverWSIURI = ((serverProtocol==null)?"":serverProtocol) + "://" + ((serverHostname==null)?"":serverHostname);
    if (serverPort != null)
      serverWSIURI += ":" + serverPort;
    if (serverLocation != null)
      serverWSIURI += "/" + serverLocation;

    docUrlServerProtocol = configParams.getParameter(CONFIG_PARAM_URLPROTOCOL);
    docUrlServerName = configParams.getParameter(CONFIG_PARAM_URLHOSTNAME);
    docUrlPort = configParams.getParameter(CONFIG_PARAM_URLPORT);
    if (docUrlPort != null && docUrlPort.length() < 1)
      docUrlPort = null;
    docUrlLocation = configParams.getParameter(CONFIG_PARAM_URLLOCATION);
    if (docUrlLocation != null && docUrlLocation.length() < 1)
      docUrlLocation = null;

    docURIPrefix = ((docUrlServerProtocol==null)?"":docUrlServerProtocol) + "://" + ((docUrlServerName==null)?"":docUrlServerName);
    if (docUrlPort != null)
      docURIPrefix = docURIPrefix + ":" + docUrlPort;
    if (docUrlLocation != null)
      docURIPrefix = docURIPrefix + "/" + docUrlLocation;
    docURIPrefix += "/getContent?objectStoreName=" + ((objectStore==null)?"":objectStore);
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      try
      {
        checkConnection();
        return super.check();
      }
      catch (FilenetException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
          throw new ServiceInterruption(e.getMessage(),0L);
        else
          throw new ManifoldCFException(e.getMessage(),e);
      }
    }
    catch (ServiceInterruption e)
    {
      return "Connection temporarily failed: "+e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      return "Connection failed: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    releaseCheck();
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return session != null;
  }

  /** Disconnect from Filenet.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    if (session != null)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("FileNet: Transient remote exception closing session: "+e.getMessage(),e);
      }
      catch (FilenetException e)
      {
        session = null;
        lastSessionFetch = -1L;
        // Base our treatment on the kind of error it is.
        if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("FileNet: Remote service interruption closing session: "+e.getMessage(),e);
        }
        else
          Logging.connectors.warn("FileNet: Error closing session: "+e.getMessage(),e);
      }

    }

    userID = null;
    password = null;
    objectStore = null;
    serverWSIURI = null;
    serverHostname = null;
    docURIPrefix = null;
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  @Override
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException
  {
    if (command.startsWith("metadatafields/"))
    {
      String documentClass = command.substring("metadatafields/".length());
      try
      {
        MetadataFieldDefinition[] metaFields = getDocumentClassMetadataFieldsDetails(documentClass);
        int i = 0;
        while (i < metaFields.length)
        {
          MetadataFieldDefinition def = metaFields[i++];
          ConfigurationNode node = new ConfigurationNode("metadata_field");
          ConfigurationNode child;
          child = new ConfigurationNode("display_name");
          child.setValue(def.getDisplayName());
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("symbolic_name");
          child.setValue(def.getSymbolicName());
          node.addChild(node.getChildCount(),child);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.equals("documentclasses"))
    {
      try
      {
        DocumentClassDefinition[] definitions = getDocumentClassesDetails();
        int i = 0;
        while (i < definitions.length)
        {
          DocumentClassDefinition def = definitions[i++];
          ConfigurationNode node = new ConfigurationNode("document_class");
          ConfigurationNode child;
          child = new ConfigurationNode("display_name");
          child.setValue(def.getDisplayName());
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("symbolic_name");
          child.setValue(def.getSymbolicName());
          node.addChild(node.getChildCount(),child);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.equals("mimetypes"))
    {
      try
      {
        String[] mimeTypesArray = getMimeTypes();
        int i = 0;
        while (i < mimeTypesArray.length)
        {
          String mimeType = mimeTypesArray[i++];
          ConfigurationNode node = new ConfigurationNode("mime_type");
          node.setValue(mimeType);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else
      return super.requestInfo(output,command);
    return true;
  }
  
  /** Get child folder names, given a starting folder name.
  *@param folderName is the starting folder name.
  *@return the child folder names.
  */
  public String[] getChildFolders(String folderName)
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      if (folderName.startsWith("/"))
        folderName = folderName.substring(1);
      
      String[] folderPath;
      if (folderName.length() == 0)
        folderPath = new String[0];
      else
        folderPath = folderName.split("/");
      
      String[] rval = doGetChildFolders(folderPath);
      if (rval == null)
        return null;
      java.util.Arrays.sort(rval);
      return rval;
    }
    catch (FilenetException e)
    {
      // Base our treatment on the kind of error it is.
      if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        throw new ServiceInterruption(e.getMessage(),0L);
      else
        throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  @Override
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("FileNet: Inside addSeedDocuments");

    // Go through all the document classes and do a query for each one
    //get all mimetypes and build a SQL "where condition"

    StringBuilder mimeTypesClause = new StringBuilder();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);
      if (n.getType().equals(SPEC_NODE_MIMETYPE))
      {
        String mimeType = n.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        if (mimeType != null)
        {
          if (mimeTypesClause.length() != 0)
            mimeTypesClause.append(" or ");
          mimeTypesClause.append("[MimeType] = '").append(mimeType).append("'");
        }
      }
      i++;
    }
    if (mimeTypesClause.length() == 0)
    {
      // Build the standard default filter list
      String[] mimeTypes = getMimeTypes();
      i = 0;
      while (i < mimeTypes.length)
      {
        String mimeType = mimeTypes[i++];
        if (mimeTypesClause.length() != 0)
          mimeTypesClause.append(" or ");
        mimeTypesClause.append("[MimeType] = '").append(mimeType).append("'");
      }
    }

    StringBuilder sqlBuffer = new StringBuilder(" WHERE ([IsCurrentVersion] = TRUE AND (");
    sqlBuffer.append(mimeTypesClause);
    sqlBuffer.append(")");
    Calendar c = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    // FileNet can apparently take a while to make documents available for searching, so throw in a bias of 15 minutes
    long biasTime = 15L * 60000L;
    if (startTime < biasTime)
      startTime = 0L;
    else
      startTime -= biasTime;

    if (startTime > 0L)
    {
      sqlBuffer.append(" AND [DateLastModified] >= ").append(buildTime(c,startTime));
    }

    sqlBuffer.append(" AND  [DateLastModified] <= ").append(buildTime(c,endTime));

    // Folders are also based just on objectstore, so we add those here.
    boolean seenAny = false;
    i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals(SPEC_NODE_FOLDER))
      {
        if (!seenAny)
        {
          sqlBuffer.append(" AND (");
          seenAny = true;
        }
        else
        {
          sqlBuffer.append(" or ");
        }
        String folderValue = n.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        sqlBuffer.append("This INSUBFOLDER "+quoteSQLString(folderValue));
      }
    }
    if (seenAny)
    {
      sqlBuffer.append(")");
    }
    
    String whereClause = sqlBuffer.toString();

    i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);

      if (n.getType().equals(SPEC_NODE_DOCUMENTCLASS))
      {
        String dc = n.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        DocClassSpec dcs = new DocClassSpec(n);
        int matchCount = dcs.getMatchCount();
        StringBuilder moreWhereClause = new StringBuilder(whereClause);
        int q = 0;
        while (q < matchCount)
        {
          String matchType = dcs.getMatchType(q);
          String matchField = dcs.getMatchField(q);
          String matchValue = dcs.getMatchValue(q);
          q++;
          moreWhereClause.append(" AND [").append(matchField).append("] ").append(matchType).append(" ")
            .append(quoteSQLString(matchValue));
        }
        moreWhereClause.append(")");
        String fullSQL = "SELECT Id, [VersionSeries] FROM "+dc+" WITH EXCLUDESUBCLASSES "+moreWhereClause.toString()+" OPTIONS(TIMELIMIT 180)";
        long currentTime;
        try
        {
          String[] objectIds = doGetMatchingObjectIds(fullSQL);
          int j = 0;
          while (j < objectIds.length)
          {
            // Add an identifier WITHOUT a comma for each document picked up in seeding.
            // These identifiers will be processed later to yield individual content identifiers.
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("FileNet: Seeding with identifier '"+objectIds[j]+"'");
            activities.addSeedDocument(objectIds[j++]);
          }
        }
        catch (FilenetException e)
        {
          // Base our treatment on the kind of error it is.
          currentTime = System.currentTimeMillis();
          if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
            throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
          else
            throw new ManifoldCFException(e.getMessage(),e);
        }
      }

      i++;
    }
  }

  protected static String quoteSQLString(String value)
  {
    StringBuilder sb = new StringBuilder();
    sb.append('\'');
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\'')
        sb.append('\'');
      sb.append(x);
    }
    sb.append('\'');
    return sb.toString();
  }

  protected static String buildTime(Calendar c, long timeValue)
  {
    c.setTimeInMillis(timeValue);
    //c.computeFields();
    StringBuilder rval = new StringBuilder();
    print_int(rval,c.get(Calendar.YEAR),4);
    print_int(rval,c.get(Calendar.MONTH)+1,2);
    print_int(rval,c.get(Calendar.DAY_OF_MONTH),2);
    rval.append("T");
    print_int(rval,c.get(Calendar.HOUR_OF_DAY),2);
    print_int(rval,c.get(Calendar.MINUTE),2);
    print_int(rval,c.get(Calendar.SECOND),2);
    rval.append("Z");
    return rval.toString();
  }

  protected static void print_int(StringBuilder sb, int value, int digits)
  {
    if (digits == 4)
    {
      value = print_digit(sb,value,1000);
      digits--;
    }
    if (digits == 3)
    {
      value = print_digit(sb,value,100);
      digits--;
    }
    if (digits == 2)
    {
      value = print_digit(sb,value,10);
      digits--;
    }
    if (digits == 1)
    {
      print_digit(sb,value,1);
    }
  }

  protected static int print_digit(StringBuilder sb, int value, int divisor)
  {
    int digit = value / divisor;
    int x = '0' + digit;
    sb.append((char)x);
    return value - digit * divisor;
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activity is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activity,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("FileNet: Inside getDocumentVersions");

    String[] acls = getAcls(spec);

    String[] rval = new String[documentIdentifiers.length];

    // Put together a set of the metadata fields, from the document specification
    int i = 0;
    HashMap docClassSpecs = new HashMap();
    // Also calculate the fields we will need to retrieve from each document, on a document class basis
    HashMap metadataFields = new HashMap();
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(SPEC_NODE_DOCUMENTCLASS))
      {
        String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        DocClassSpec classSpec = new DocClassSpec(sn);
        docClassSpecs.put(value,classSpec);
        if (classSpec.getAllMetadata())
          metadataFields.put(value,new Boolean(true));
        else
        {
          HashMap sumMap = new HashMap();
          int j = 0;
          String[] fields = classSpec.getMetadataFields();
          while (j < fields.length)
          {
            String field = fields[j++];
            sumMap.put(field,field);
          }
          j = 0;
          while (j < classSpec.getMatchCount())
          {
            String field = classSpec.getMatchField(j++);
            sumMap.put(field,field);
          }
          // Convert to an array
          String[] fieldArray = new String[sumMap.size()];
          Iterator iter = sumMap.keySet().iterator();
          j = 0;
          while (iter.hasNext())
          {
            fieldArray[j++] = (String)iter.next();
          }
          metadataFields.put(value,fieldArray);
        }
      }
    }


    for (i=0; i<documentIdentifiers.length; i++)
    {
      // For each document, be sure to confirm job still active
      activity.checkJobStillActive();

      String documentIdentifier = documentIdentifiers[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Filenet: Getting version for identifier '"+documentIdentifier+"'");

      // Calculate the version id and the element number
      int cIndex = documentIdentifier.indexOf(",");
      if (cIndex != -1)
      {
        String vId = documentIdentifier.substring(0,cIndex);

        long currentTime;
        try
        {
          FileInfo fileInfo = doGetDocumentInformation(vId, metadataFields);
          if (fileInfo == null)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("FileNet: Skipping document '"+documentIdentifier+"' because not a current document");
            rval[i] = null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Filenet: Document '"+documentIdentifier+"' is a current document");

            // Form a version string based on the info in fileInfo
            // Version string will consist of:
            // (a) metadata info
            // (b) acl info
            // (c) the url prefix to use
            StringBuilder versionBuffer = new StringBuilder();

            String docClass = fileInfo.getDocClass();
            DocClassSpec docclassspec = (DocClassSpec)docClassSpecs.get(docClass);

            // First, verify that this document matches the match criteria
            boolean docMatches = true;
            int q = 0;
            while (q < docclassspec.getMatchCount())
            {
              String matchType = docclassspec.getMatchType(q);
              String matchField = docclassspec.getMatchField(q);
              String matchValue = docclassspec.getMatchValue(q);
              q++;
              // Grab the appropriate field value from the fileinfo.  We know it is there because we explicitly
              // folded the match fields into the server request.
              String matchDocValue = fileInfo.getMetadataValue(matchField);
              docMatches = performMatch(matchType,matchDocValue,matchValue);
              if (docMatches == false)
                break;
            }

            if (docMatches)
            {
              // Metadata info
              int metadataCount = 0;
              Iterator iter = fileInfo.getMetadataIterator();
              while (iter.hasNext())
              {
                String field = (String)iter.next();
                if (docclassspec.checkMetadataIncluded(field))
                  metadataCount++;
              }
              String[] metadataFieldNames = new String[metadataCount];
              int j = 0;
              iter = fileInfo.getMetadataIterator();
              while (iter.hasNext())
              {
                String field = (String)iter.next();
                if (docclassspec.checkMetadataIncluded(field))
                  metadataFieldNames[j++] = field;
              }
              java.util.Arrays.sort(metadataFieldNames);
              // Pack field names and values
              // For sanity, pack the names first and then the values!
              packList(versionBuffer,metadataFieldNames,'+');
              String[] metadataFieldValues = new String[metadataFieldNames.length];
              j = 0;
              while (j < metadataFieldValues.length)
              {
                metadataFieldValues[j] = fileInfo.getMetadataValue(metadataFieldNames[j]);
                if (metadataFieldValues[j] == null)
                  metadataFieldValues[j] = "";
                j++;
              }
              packList(versionBuffer,metadataFieldValues,'+');

              // Acl info
              // Future work will add "forced acls", so use a single character as a signal as to whether security is on or off.
              String[] aclValues = null;
              String[] denyAclValues = null;
              if (acls != null && acls.length == 0)
              {
                // Security is on, so use the acls that came back from filenet
                aclValues = new String[fileInfo.getAclCount()];
                j = 0;
                iter = fileInfo.getAclIterator();
                while (iter.hasNext())
                {
                  aclValues[j++] = (String)iter.next();
                }
                denyAclValues = new String[fileInfo.getDenyAclCount()];
                j = 0;
                iter = fileInfo.getDenyAclIterator();
                while (iter.hasNext())
                {
                  denyAclValues[j++] = (String)iter.next();
                }
              }
              else if (acls != null && acls.length > 0)
              {
                // Forced acls
                aclValues = acls;
                denyAclValues = new String[]{defaultAuthorityDenyToken};
              }

              if (aclValues != null)
              {
                versionBuffer.append('+');
                java.util.Arrays.sort(aclValues);
                packList(versionBuffer,aclValues,'+');
                if (denyAclValues == null)
                  denyAclValues = new String[0];
                java.util.Arrays.sort(denyAclValues);
                packList(versionBuffer,denyAclValues,'+');
              }
              else
                versionBuffer.append('-');

              // Document class
              pack(versionBuffer,docClass,'+');
              // Document URI
              pack(versionBuffer,docURIPrefix,'+');

              rval[i] = versionBuffer.toString();
            }
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("FileNet: Skipping document '"+documentIdentifier+"' because doesn't match field criteria");
              rval[i] = null;
            }
          }
        }
        catch (FilenetException e)
        {
          // Base our treatment on the kind of error it is.
          currentTime = System.currentTimeMillis();
          if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
            throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
          else if (e.getType() == FilenetException.TYPE_NOTALLOWED)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("FileNet: Skipping file '"+documentIdentifier+"' because: "+e.getMessage(),e);
            rval[i] = null;

          }
          else
            throw new ManifoldCFException(e.getMessage(),e);
        }
      }
      else
      {
        // This is a naked version identifier.
        // On every crawl, we need to convert this identifier to the individual identifiers for each bit of content.
        // There is no versioning available for this process.
        rval[i] = "";
      }
    }
    return rval;
  }

  /** Emulate the query matching for filenet sql expressions. */
  protected static boolean performMatch(String matchType, String matchDocValue, String matchValue)
  {
    if (matchType.equals("="))
      return matchDocValue.equalsIgnoreCase(matchValue);
    else if (matchType.equals("!="))
      return !matchDocValue.equalsIgnoreCase(matchValue);

    // Do a LIKE comparison
    return likeMatch(matchDocValue,0,matchValue,0);
  }

  /** Match a portion of a string with SQL wildcards (%) */
  protected static boolean likeMatch(String matchDocValue, int matchDocPos, String matchValue, int matchPos)
  {
    if (matchPos == matchValue.length())
    {
      return matchDocPos == matchDocValue.length();
    }
    if (matchDocPos == matchDocValue.length())
    {
      return matchValue.charAt(matchPos) == '%' && likeMatch(matchDocValue,matchDocPos,matchValue,matchPos+1);
    }
    char x = matchDocValue.charAt(matchDocPos);
    char y = matchValue.charAt(matchPos);
    if (y != '%')
      return Character.toLowerCase(x) == Character.toLowerCase(y) && likeMatch(matchDocValue,matchDocPos+1,matchValue,matchPos+1);

    return likeMatch(matchDocValue,matchDocPos+1,matchValue,matchPos) ||
      likeMatch(matchDocValue,matchDocPos,matchValue,matchPos+1);
  }

  /** Process documents whose versions indicate they need processing.
  */
  public void processDocuments(String[] documentIdentifiers, String[] documentVersions,
    IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("FileNet: Inside processDocuments");

    int i = 0;
    while (i < documentIdentifiers.length)
    {
      // For each document, be sure to confirm job still active
      activities.checkJobStillActive();

      String documentIdentifier = documentIdentifiers[i];
      String documentVersion = documentVersions[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: Processing document identifier '"+documentIdentifier+"'");

      // Calculate the version id and the element number
      int cIndex = documentIdentifier.indexOf(",");
      if (cIndex != -1)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("FileNet: Document identifier '"+documentIdentifier+"' is a document attachment");

        if (!scanOnly[i])
        {
          String vId = documentIdentifier.substring(0,cIndex);
          int elementNumber;
          try
          {
            elementNumber = Integer.parseInt(documentIdentifier.substring(cIndex+1));
          }
          catch (NumberFormatException e)
          {
            throw new ManifoldCFException("Bad number in identifier: "+documentIdentifier,e);
          }

          // Unpack the information in the document version
          ArrayList metadataNames = new ArrayList();
          ArrayList metadataValues = new ArrayList();
          ArrayList aclValues = null;
          ArrayList denyAclValues = null;
          StringBuilder documentClass = new StringBuilder();
          StringBuilder urlBase = new StringBuilder();
          int position = 0;
          position = unpackList(metadataNames, documentVersion, position, '+');
          position = unpackList(metadataValues, documentVersion, position, '+');
          //Logging.connectors.debug("Names length = "+Integer.toString(metadataNames.size()));
          //Logging.connectors.debug("Values length = "+Integer.toString(metadataValues.size()));
          if (documentVersion.length() > position && documentVersion.charAt(position++) == '+')
          {
            //Logging.connectors.debug("Acls found at position "+Integer.toString(position));
            aclValues = new ArrayList();
            position = unpackList(aclValues, documentVersion, position, '+');
            denyAclValues = new ArrayList();
            position = unpackList(denyAclValues, documentVersion, position, '+');
            //Logging.connectors.debug("ACLs length = "+Integer.toString(aclValues.size()));
          }
          position = unpack(documentClass, documentVersion, position, '+');
          position = unpack(urlBase, documentVersion, position, '+');

          //Logging.connectors.debug("Url base from version string = "+urlBase.toString());
          try
          {
            // Try to ingest the document.
            // We need to fetch the contents first
            File objFileTemp = File.createTempFile("_mc_fln_", null);
            try
            {
              long startTime = System.currentTimeMillis();
              try
              {
                doGetDocumentContents(vId,elementNumber,objFileTemp.getCanonicalPath());
              }
              catch (FilenetException e)
              {
                // Base our treatment on the kind of error it is.
                long currentTime = System.currentTimeMillis();
                if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
                {
                  activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Transient error",e.getMessage(),null);
                  throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
                }
                else if (e.getType() == FilenetException.TYPE_NOTALLOWED)
                {
                  activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Authorization error",e.getMessage(),null);
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("FileNet: Removing file '"+documentIdentifier+"' because: "+e.getMessage(),e);
                  activities.deleteDocument(documentIdentifier,documentVersion);
                  i++;
                  continue;
                }
                else
                {
                  activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Miscellaneous error",e.getMessage(),null);
                  throw new ManifoldCFException(e.getMessage(),e);
                }
              }

              // Document fetch completed
              long fileLength = objFileTemp.length();

              activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                new Long(fileLength),documentIdentifier,"Success",null,null);

              if (activities.checkLengthIndexable(fileLength))
              {

                InputStream is = new FileInputStream(objFileTemp);
                try
                {
                  RepositoryDocument rd = new RepositoryDocument();
                  rd.setBinary(is, fileLength);

                  // Apply metadata
                  int j = 0;
                  while (j < metadataNames.size())
                  {
                    String metadataName = (String)metadataNames.get(j);
                    String metadataValue = (String)metadataValues.get(j);
                    rd.addField(metadataName,metadataValue);
                    j++;
                  }

                  // Apply acls
                  if (aclValues != null)
                  {
                    String[] acls = new String[aclValues.size()];
                    j = 0;
                    while (j < aclValues.size())
                    {
                      acls[j] = (String)aclValues.get(j);
                      j++;
                    }
                    rd.setACL(acls);
                  }
                  if (denyAclValues != null)
                  {
                    String[] denyAcls = new String[denyAclValues.size()];
                    j = 0;
                    while (j < denyAclValues.size())
                    {
                      denyAcls[j] = (String)denyAclValues.get(j);
                      j++;
                    }
                    rd.setDenyACL(denyAcls);
                  }

                  // Ingest
                  activities.ingestDocument(documentIdentifier,documentVersion,
                    convertToURI(urlBase.toString(),vId,elementNumber,documentClass.toString()),rd);

                }
                finally
                {
                  try
                  {
                    is.close();
                  }
                  catch (InterruptedIOException e)
                  {
                    throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    Logging.connectors.warn("FileNet: IOException closing file input stream: "+e.getMessage(),e);
                  }
                }
              }
              else
                activities.deleteDocument(documentIdentifier,documentVersion);
            }
            finally
            {
              // Delete temp file
              objFileTemp.delete();
            }
          }
          catch (InterruptedIOException e)
          {
            throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (IOException e)
          {
            Logging.connectors.error("FileNet: IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
            throw new ManifoldCFException("IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
          }
        }
      }
      else
      {
        // Need to map naked version id to the individual content element identifiers
        // This is done even if we get a "scan only" request, because in fact this is part of the document discovery
        // process, not the actual ingestion.
        try
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("FileNet: Looking up content count for version identifier '"+documentIdentifier+"'");
          Integer count = doGetDocumentContentCount(documentIdentifier);
          if (count == null)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("FileNet: Removing version '"+documentIdentifier+"' because it seems to no longer exist");

            activities.deleteDocument(documentIdentifier,documentVersion);
            i++;
            continue;
          }

          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("FileNet: There are "+count.toString()+" content values for '"+documentIdentifier+"'");

          // Loop through all document content identifiers and add a child identifier for each
          int j = 0;
          while (j < count.intValue())
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Filenet: Adding document identifier '"+documentIdentifier+","+Integer.toString(j)+"'");

            activities.addDocumentReference(documentIdentifier + "," + Integer.toString(j++));
          }
        }
        catch (FilenetException e)
        {
          // Base our treatment on the kind of error it is.
          long currentTime = System.currentTimeMillis();
          if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
          {
            throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
          }
          else if (e.getType() == FilenetException.TYPE_NOTALLOWED)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("FileNet: Removing version '"+documentIdentifier+"' because: "+e.getMessage(),e);
            activities.deleteDocument(documentIdentifier,documentVersion);
            i++;
            continue;
          }
          else
          {
            throw new ManifoldCFException(e.getMessage(),e);
          }
        }
      }
      i++;
    }

  }

  /** Free a set of documents.  This method is called for all documents whose versions have been fetched using
  * the getDocumentVersions() method, including those that returned null versions.  It may be used to free resources
  * committed during the getDocumentVersions() method.  It is guaranteed to be called AFTER any calls to
  * processDocuments() for the documents in question.
  *@param documentIdentifiers is the set of document identifiers.
  *@param versions is the corresponding set of version identifiers (individual identifiers may be null).
  */
  @Override
  public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions)
    throws ManifoldCFException
  {
    // Nothing to do
  }

  @Override
  public int getMaxDocumentRequest()
  {
    // 1 at a time, since this connector does not deal with documents en masse, but one at a time.
    return 1;
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
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"FilenetConnector.Server"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.ObjectStore"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.DocumentURL"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.Credentials"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheServerPortMustBeAnInteger") + "\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlport.value != \"\" && !isInteger(editconnection.urlport.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheDocumentURLPortMustBeAnInteger") + "\");\n"+
"    editconnection.urlport.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  return true;\n"+
"}\n"+
"	\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.serverhostname.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheConnectionRequiresAFileNetHostName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.Server") + "\");\n"+
"    editconnection.serverhostname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheServerPortMustBeAnInteger") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.Server") + "\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlhostname.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheDocumentURLRequiresAHostName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.DocumentURL") + "\");\n"+
"    editconnection.urlhostname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlport.value != \"\" && !isInteger(editconnection.urlport.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheDocumentURLPortMustBeAnInteger") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.DocumentURL") + "\");\n"+
"    editconnection.urlport.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  if (editconnection.filenetdomain.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheFileNetDomainNameCannotBeNull") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.ObjectStore") + "\");\n"+
"    editconnection.filenetdomain.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.objectstore.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheObjectStoreNameCannotBeNull") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.ObjectStore") + "\");\n"+
"    editconnection.objectstore.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userid.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheConnectionRequiresAValidFileNetUserID") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.Credentials") + "\");\n"+
"    editconnection.userid.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.password.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.TheConnectionRequiresTheFileNetUsersPassword") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.Credentials") + "\");\n"+
"    editconnection.password.focus();\n"+
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
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String userID = parameters.getParameter(CONFIG_PARAM_USERID);
    if (userID == null)
      userID = "";
    String password = parameters.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    if (password == null)
      password = "";
    else
      password = out.mapPasswordToKey(password);
    String filenetdomain = parameters.getParameter(CONFIG_PARAM_FILENETDOMAIN);
    if (filenetdomain == null)
    {
      filenetdomain = parameters.getParameter(CONFIG_PARAM_FILENETDOMAIN_OLD);
      if (filenetdomain == null)
        filenetdomain = "";
    }
    String objectstore = parameters.getParameter(CONFIG_PARAM_OBJECTSTORE);
    if (objectstore == null)
      objectstore = "";
    String serverprotocol = parameters.getParameter(CONFIG_PARAM_SERVERPROTOCOL);
    if (serverprotocol == null)
      serverprotocol = "http";
    String serverhostname = parameters.getParameter(CONFIG_PARAM_SERVERHOSTNAME);
    if (serverhostname == null)
      serverhostname = "";
    String serverport = parameters.getParameter(CONFIG_PARAM_SERVERPORT);
    if (serverport == null)
      serverport = "";
    String serverwsilocation = parameters.getParameter(CONFIG_PARAM_SERVERWSILOCATION);
    if (serverwsilocation == null)
      serverwsilocation = "wsi/FNCEWS40DIME";
    String urlprotocol = parameters.getParameter(CONFIG_PARAM_URLPROTOCOL);
    if (urlprotocol == null)
      urlprotocol = "http";
    String urlhostname = parameters.getParameter(CONFIG_PARAM_URLHOSTNAME);
    if (urlhostname == null)
      urlhostname = "";
    String urlport = parameters.getParameter(CONFIG_PARAM_URLPORT);
    if (urlport == null)
      urlport = "";
    String urllocation = parameters.getParameter(CONFIG_PARAM_URLLOCATION);
    if (urllocation == null)
      urllocation = "Workplace/Browse.jsp";

    // "Server" tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.Server")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.ServerProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverprotocol\" size=\"2\">\n"+
"        <option value=\"http\" "+(serverprotocol.equals("http")?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(serverprotocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.ServerHostName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"serverhostname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverhostname)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.ServerPort") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"5\" name=\"serverport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverport)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.ServerWebServiceLocation") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"serverwsilocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverwsilocation)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Server tab
      out.print(
"<input type=\"hidden\" name=\"serverprotocol\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverprotocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverhostname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverhostname)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverport)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverwsilocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(serverwsilocation)+"\"/>\n"
      );
    }

    // "Document URL" tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.DocumentURL")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentURLProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"urlprotocol\" size=\"2\">\n"+
"        <option value=\"http\" "+(serverprotocol.equals("http")?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(serverprotocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentURLHostName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"urlhostname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urlhostname)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentURLPort") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"5\" name=\"urlport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urlport)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentURLLocation") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"urllocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urllocation)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Document URL tab
      out.print(
"<input type=\"hidden\" name=\"urlprotocol\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urlprotocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"urlhostname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urlhostname)+"\"/>\n"+
"<input type=\"hidden\" name=\"urlport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urlport)+"\"/>\n"+
"<input type=\"hidden\" name=\"urllocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(urllocation)+"\"/>\n"
      );
    }

    // "Object Store" tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.ObjectStore")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.FileNetDomainName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"filenetdomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filenetdomain)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.ObjectStoreName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"objectstore\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(objectstore)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Object Store tab
      out.print(
"<input type=\"hidden\" name=\"filenetdomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filenetdomain)+"\"/>\n"+
"<input type=\"hidden\" name=\"objectstore\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(objectstore)+"\"/>\n"
      );
    }


    // "Credentials" tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.Credentials")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.UserID") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"userid\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Password") + "</nobr></td><td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Credentials tab
      out.print(
"<input type=\"hidden\" name=\"userid\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
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
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    String serverprotocol = variableContext.getParameter("serverprotocol");
    if (serverprotocol != null)
      parameters.setParameter(CONFIG_PARAM_SERVERPROTOCOL,serverprotocol);

    String serverhostname = variableContext.getParameter("serverhostname");
    if (serverhostname != null)
      parameters.setParameter(CONFIG_PARAM_SERVERHOSTNAME,serverhostname);
    
    String serverport = variableContext.getParameter("serverport");
    if (serverport != null && serverport.length() > 0)
      parameters.setParameter(CONFIG_PARAM_SERVERPORT,serverport);

    String serverwsilocation = variableContext.getParameter("serverwsilocation");
    if (serverwsilocation != null)
      parameters.setParameter(CONFIG_PARAM_SERVERWSILOCATION,serverwsilocation);

    String urlprotocol = variableContext.getParameter("urlprotocol");
    if (urlprotocol != null)
      parameters.setParameter(CONFIG_PARAM_URLPROTOCOL,urlprotocol);

    String urlhostname = variableContext.getParameter("urlhostname");
    if (urlhostname != null)
      parameters.setParameter(CONFIG_PARAM_URLHOSTNAME,urlhostname);

    String urlport = variableContext.getParameter("urlport");
    if (urlport != null && urlport.length() > 0)
      parameters.setParameter(CONFIG_PARAM_URLPORT,urlport);

    String urllocation = variableContext.getParameter("urllocation");
    if (urllocation != null)
      parameters.setParameter(CONFIG_PARAM_URLLOCATION,urllocation);

    String userID = variableContext.getParameter("userid");
    if (userID != null)
      parameters.setParameter(CONFIG_PARAM_USERID,userID);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(CONFIG_PARAM_PASSWORD,variableContext.mapKeyToPassword(password));

    String filenetdomain = variableContext.getParameter("filenetdomain");
    if (filenetdomain != null)
      parameters.setParameter(CONFIG_PARAM_FILENETDOMAIN,filenetdomain);

    String objectstore = variableContext.getParameter("objectstore");
    if (objectstore != null)
      parameters.setParameter(CONFIG_PARAM_OBJECTSTORE,objectstore);
    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Parameters") + "</nobr></td>\n"+
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
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" certificate(s)&gt;</nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
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
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"FilenetConnector.DocumentClasses"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.MimeTypes"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.Folders"));
    tabsArray.add(Messages.getString(locale,"FilenetConnector.Security"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkSpecification()\n"+
"{\n"+
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
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.SelectAFolderFirst") + "\");\n"+
"    editjob.pathaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"  SpecOp(\"pathop\",\"AddToPath\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddMatch(docclass, anchorvalue)\n"+
"{\n"+
"  if (eval(\"editjob.matchfield_\"+docclass+\".value\") == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.SelectAFieldFirst") + "\");\n"+
"    eval(\"editjob.matchfield_\"+docclass+\".focus()\");\n"+
"    return;\n"+
"  }\n"+
"  if (eval(\"editjob.matchtype_\"+docclass+\".value\") == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.SelectAMatchType") + "\");\n"+
"    eval(\"editjob.matchtype_\"+docclass+\".focus()\");\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"matchop_\"+docclass,\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"FilenetConnector.NullTokensNotAllowed") + "\");\n"+
"    editjob.spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
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
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    int i;
    int k;
    Iterator iter;

    // "Document Classes" tab
    // Look for document classes
    HashMap documentClasses = new HashMap();
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(SPEC_NODE_DOCUMENTCLASS))
      {
        String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        // Now, scan for metadata etc.
        org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec spec = new org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec(sn);
        documentClasses.put(value,spec);
      }
    }

    // Folders tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.Folders")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Now, loop through paths.  There will be a row in the current table for each one.
      // The row will contain a delete button on the left.  On the right will be the startpoint itself at the top,
      // and underneath it the table where the filter criteria are edited.
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(SPEC_NODE_FOLDER))
        {
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "pathop"+pathDescription;
          String startPath = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
          out.print(
"  <tr>\n"+
"    <td class=\"value\">\n"+
"      <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"FilenetConnector.DeletePath")+Integer.toString(k)+"\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")'/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(startPath)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"      <nobr>"+((startPath.length() == 0)?"(root)":org.apache.manifoldcf.ui.util.Encoder.bodyEscape(startPath))+"</nobr>\n"+
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
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"FilenetConnector.NoFoldersChosen") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"value\" colspan=\"2\">\n"+
"      <nobr>\n"+
"        <input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"        <a name=\""+"path_"+Integer.toString(k)+"\">\n"
      );
	
      String pathSoFar = (String)currentContext.get("specpath");
      if (pathSoFar == null)
        pathSoFar = "/";

      // Grab next folder/project list
      try
      {
        String[] childList;
        childList = getChildFolders(pathSoFar);
        if (childList == null)
        {
          // Illegal path - set it back
          pathSoFar = "/";
          childList = getChildFolders("/");
          if (childList == null)
            throw new ManifoldCFException("Can't find any children for root folder");
        }
        out.print(
"          <input type=\"hidden\" name=\"specpath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"          <input type=\"hidden\" name=\"pathop\" value=\"\"/>\n"+
"          <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.AddPath") + "\" onClick='Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")'/>\n"+
"          &nbsp;"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathSoFar)+"\n"
        );
        if (pathSoFar.length() > 0 && !pathSoFar.equals("/"))
        {
          out.print(
"          <input type=\"button\" value=\"-\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.RemoveFromPath") + "\" onClick='Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")'/>\n"
          );
        }
        if (childList.length > 0)
        {
          out.print(
"          <nobr>\n"+
"            <input type=\"button\" value=\"+\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.AddToPath") + "\" onClick='Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")'/>&nbsp;\n"+
"            <select multiple=\"false\" name=\"pathaddon\" size=\"4\">\n"+
"              <option value=\"\" selected=\"selected\">-- " + Messages.getBodyString(locale,"FilenetConnector.PickAFolder") + " --</option>\n"
          );
          int j = 0;
          while (j < childList.length)
          {
            String attrFolder = org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childList[j]);
            String bodyFolder = org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childList[j]);
            out.print(
"              <option value=\""+attrFolder+"\">"+bodyFolder+"</option>\n"
            );
            j++;
          }
          out.print(
"            </select>\n"+
"          </nobr>\n"
          );
        }
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        out.println(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.println(org.apache.manifoldcf.ui.util.Encoder.bodyEscape("Transient error - "+e.getMessage()));
      }
      out.print(
"        </a>\n"+
"      </nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Generate hiddens for the folders tab
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(SPEC_NODE_FOLDER))
        {
          String pathDescription = "_"+Integer.toString(k);
          String startPath = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
          out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(startPath)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }
    
    // Document classes tab
    if (tabName.equals(Messages.getString(locale,"FilenetConnector.DocumentClasses")))
    {
      out.print(
"<input type=\"hidden\" name=\"hasdocumentclasses\" value=\"true\"/>\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Fetch the list of valid document classes from the connector
      org.apache.manifoldcf.crawler.common.filenet.DocumentClassDefinition[] documentClassArray = null;
      HashMap documentClassFields = new HashMap();
      String message = null;
      try
      {
        documentClassArray = getDocumentClassesDetails();
        int j = 0;
        while (j < documentClassArray.length)
        {
          String documentClass = documentClassArray[j++].getSymbolicName();
          org.apache.manifoldcf.crawler.common.filenet.MetadataFieldDefinition[] metaFields = getDocumentClassMetadataFieldsDetails(documentClass);
          documentClassFields.put(documentClass,metaFields);
        }
      }
      catch (ManifoldCFException e)
      {
        message = e.getMessage();
      }
      catch (ServiceInterruption e)
      {
        message = "FileNet server temporarily unavailable: "+e.getMessage();
      }

      if (message != null)
      {
        out.print(
"  <tr><td class=\"message\" colspan=\"2\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(message)+"</td></tr>\n"
        );
      }
      else
      {
        i = 0;
        while (i < documentClassArray.length)
        {
          org.apache.manifoldcf.crawler.common.filenet.DocumentClassDefinition def = documentClassArray[i++];
          String documentClass = def.getSymbolicName();
          String displayName = def.getDisplayName();
          org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec spec = (org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(documentClass+" ("+displayName+")")+":</nobr>\n"+
"    </td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>" + Messages.getBodyString(locale,"FilenetConnector.Include") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr><input type=\"checkbox\" name=\"documentclasses\" "+((spec != null)?"checked=\"true\"":"")+" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\"></input></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentCriteria") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"displaytable\">\n"
          );
          org.apache.manifoldcf.crawler.common.filenet.MetadataFieldDefinition[] fields = (org.apache.manifoldcf.crawler.common.filenet.MetadataFieldDefinition[])documentClassFields.get(documentClass);
          String[] fieldArray = new String[fields.length];
          HashMap fieldMap = new HashMap();
          int j = 0;
          while (j < fieldArray.length)
          {
            org.apache.manifoldcf.crawler.common.filenet.MetadataFieldDefinition field = fields[j];
            fieldArray[j++] = field.getSymbolicName();
            fieldMap.put(field.getSymbolicName(),field.getDisplayName());
          }
          java.util.Arrays.sort(fieldArray);

          int q = 0;
          int matchCount = ((spec==null)?0:spec.getMatchCount());
          while (q < matchCount)
          {
            String matchType = spec.getMatchType(q);
            String matchField = spec.getMatchField(q);
            String matchValue = spec.getMatchValue(q);
            String opName = "matchop_" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass) + "_" +Integer.toString(q);
            String labelName = "match_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
            out.print(
"              <tr>\n"+
"                <td class=\"description\">\n"+
"                  <input type=\"hidden\" name=\""+opName+"\" value=\"\"/>\n"+
"                  <a name=\""+labelName+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Delete") + "\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.Delete") + documentClass+" match # "+Integer.toString(q)+"\" onClick='Javascript:SpecOp(\""+opName+"\",\"Delete\",\""+labelName+"\")'/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\"matchfield_" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchField)+"\"/>\n"+
"                  <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchField)+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\"matchtype_" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+matchType+"\"/>\n"+
"                  <nobr>"+matchType+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\"matchvalue_" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchValue)+"\"/>\n"+
"                  <nobr>\""+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchValue)+"\"</nobr>\n"+
"                </td>\n"+
"              </tr>\n"
            );
            q++;
          }
          if (q == 0)
          {
            out.print(
"              <tr><td class=\"message\" colspan=\"4\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.NoCriteriaSpecified") + "</nobr></td></tr>\n"
            );
          }
          String addLabelName = "match_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
          String addOpName = "matchop_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass);
          out.print(
"              <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"              <tr>\n"+
"                <td class=\"description\">\n"+
"                  <input type=\"hidden\" name=\""+"matchcount_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+Integer.toString(matchCount)+"\"/>\n"+
"                  <input type=\"hidden\" name=\""+addOpName+"\" value=\"\"/>\n"+
"                  <a name=\""+addLabelName+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.AddMatchFor") +org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" onClick='Javascript:SpecAddMatch(\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\",\"match_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q+1)+"\")'/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <select name=\"matchfield_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" size=\"5\">\n"
          );
          q = 0;
          while (q < fieldArray.length)
          {
            String field = fieldArray[q++];
            String dName = (String)fieldMap.get(field);
            out.print(
"                    <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(field+" ("+dName+")")+"</option>\n"
            );
          }
          out.print(
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <select name=\"matchtype_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\">\n"+
"                    <option value=\"=\">" + Messages.getBodyString(locale,"FilenetConnector.Equals") + "</option>\n"+
"                    <option value=\"!=\">" + Messages.getBodyString(locale,"FilenetConnector.NotEquals") + "</option>\n"+
"                    <option value=\"LIKE\">" + Messages.getBodyString(locale,"FilenetConnector.Like") + "</option>\n"+
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input name=\"matchvalue_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" type=\"text\" size=\"32\" value=\"\"/>\n"+
"                </td>\n"+
"              </tr>\n"+
          
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>" + Messages.getBodyString(locale,"FilenetConnector.IngestAllMetadataFields") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr><input type=\"checkbox\" name=\"allmetadata_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\"true\" "+((spec != null && spec.getAllMetadata())?"checked=\"\"":"")+"></input></nobr><br/>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>" + Messages.getBodyString(locale,"FilenetConnector.MetadataFields") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr>\n"+
"              <select name=\""+"metadatafield_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" multiple=\"true\" size=\"5\">\n"
          );
          j = 0;
          while (j < fieldArray.length)
          {
            String field = fieldArray[j++];
            String dName = (String)fieldMap.get(field);
            out.print(
"                <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\" "+((spec!=null && spec.getAllMetadata() == false && spec.checkMetadataIncluded(field))?"selected=\"true\"":"")+">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(field+" ("+dName+")")+"</option>\n"
            );
          }
          out.print(
"              </select>\n"+
"            </nobr>\n"+
"\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
          );
        }
      }
      out.print(
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"hasdocumentclasses\" value=\"true\"/>\n"
      );
      iter = documentClasses.keySet().iterator();
      while (iter.hasNext())
      {
        String documentClass = (String)iter.next();
        org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec spec = (org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
        if (spec.getAllMetadata())
        {
          out.print(
"<input type=\"hidden\" name=\""+"allmetadata_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\"true\"/>\n"
          );
        }
        else
        {
          String[] metadataFields = spec.getMetadataFields();
          int q = 0;
          while (q < metadataFields.length)
          {
            String field = metadataFields[q++];
            out.print(
"<input type=\"hidden\" name=\""+"metadatafield_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(field)+"\"/>\n"
            );
          }
        }
                    
        // Do matches
        int matchCount = spec.getMatchCount();
        int q = 0;
        while (q < matchCount)
        {
          String matchType = spec.getMatchType(q);
          String matchField = spec.getMatchField(q);
          String matchValue = spec.getMatchValue(q);
          out.print(
"<input type=\"hidden\" name=\""+"matchfield_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchField)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"matchtype_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+matchType+"\"/>\n"+
"<input type=\"hidden\" name=\""+"matchvalue_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchValue)+"\"/>\n"
          );
          q++;
        }
        out.print(
"<input type=\"hidden\" name=\""+"matchcount_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+Integer.toString(matchCount)+"\"/>\n"+
"<input type=\"hidden\" name=\"documentclasses\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentClass)+"\"/>\n"
        );
      }
    }

    // "Mime Types" tab
    HashMap mimeTypes = null;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(SPEC_NODE_MIMETYPE))
      {
        String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        if (mimeTypes == null)
          mimeTypes = new HashMap();
        mimeTypes.put(value,value);
      }
    }

    if (tabName.equals(Messages.getString(locale,"FilenetConnector.MimeTypes")))
    {
      out.print(
"<input type=\"hidden\" name=\"hasmimetypes\" value=\"true\"/>\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Fetch the list of valid document classes from the connector
      String[] mimeTypesArray = null;
      String message = null;
      try
      {
        mimeTypesArray = getMimeTypes();
      }
      catch (ManifoldCFException e)
      {
        message = e.getMessage();
      }
      catch (ServiceInterruption e)
      {
        message = "FileNet server temporarily unavailable: "+e.getMessage();
      }
      out.print(
"  <tr>\n"
      );
      if (message != null)
      {
        out.print(
"    <td class=\"message\" colspan=\"2\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(message)+"</td>\n"
        );
      }
      else
      {
        out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.MimeTypesToInclude") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"mimetypes\" size=\"10\" multiple=\"true\">\n"
        );
        i = 0;
        while (i < mimeTypesArray.length)
        {
          String mimeType = mimeTypesArray[i++];
          if (mimeTypes == null || mimeTypes.get(mimeType) != null)
          {
            out.print(
"        <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeType)+"\" selected=\"true\">\n"+
"          "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mimeType)+"\n"+
"        </option>\n"
            );
          }
          else
          {
            out.print(
"        <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeType)+"\">\n"+
"          "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mimeType)+"\n"+
"        </option>\n"
            );
          }
        }
        out.print(
"      </select>\n"+
"    </td>\n"
        );
      }
      out.print(
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"hasmimetypes\" value=\"true\"/>\n"
      );
      if (mimeTypes != null)
      {
        iter = mimeTypes.keySet().iterator();
        while (iter.hasNext())
        {
          String mimeType = (String)iter.next();
          out.print(
"<input type=\"hidden\" name=\"mimetypes\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeType)+"\"/>\n"
          );
        }
      }
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

    if (tabName.equals(Messages.getString(locale,"FilenetConnector.Security")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Security2") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"on\" "+((securityOn)?"checked=\"true\"":"")+" />" + Messages.getBodyString(locale,"FilenetConnector.Enabled") +
"      <input type=\"radio\" name=\"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />" + Messages.getBodyString(locale,"FilenetConnector.Disabled") + 
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
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
          String accessOpName = "accessop"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Delete") + "\" alt=\""+ Messages.getAttributeString(locale,"FilenetConnector.DeleteAccessToken")+Integer.toString(k)+"\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")'/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"\n"+
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
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"FilenetConnector.NoAccessTokensPresent") + "</td>\n"+
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
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FilenetConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"FilenetConnector.AddAccessToken") + "\" onClick='Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")'/>\n"+
"      </a>\n"+
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
"<input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
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
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    String[] x;
    String y;
    int i;

    if (variableContext.getParameter("hasdocumentclasses") != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(SPEC_NODE_DOCUMENTCLASS))
          ds.removeChild(i);
        else
          i++;
      }
      x = variableContext.getParameterValues("documentclasses");
      if (x != null)
      {
        i = 0;
        while (i < x.length)
        {
          String value = x[i++];
          SpecificationNode node = new SpecificationNode(SPEC_NODE_DOCUMENTCLASS);
          node.setAttribute(SPEC_ATTRIBUTE_VALUE,value);
          // Get the allmetadata value for this document class
          String allmetadata = variableContext.getParameter("allmetadata_"+value);
          if (allmetadata == null)
            allmetadata = "false";
          if (allmetadata.equals("true"))
            node.setAttribute(SPEC_ATTRIBUTE_ALLMETADATA,allmetadata);
          else
          {
            String[] fields = variableContext.getParameterValues("metadatafield_"+value);
            if (fields != null)
            {
              int j = 0;
              while (j < fields.length)
              {
                String field = fields[j++];
                SpecificationNode sp2 = new SpecificationNode(SPEC_NODE_METADATAFIELD);
                sp2.setAttribute(SPEC_ATTRIBUTE_VALUE,field);
                node.addChild(node.getChildCount(),sp2);
              }
            }
          }
			
          // Now, gather up matches too
          String matchCountString = variableContext.getParameter("matchcount_"+value);
          int matchCount = Integer.parseInt(matchCountString);
          int q = 0;
          while (q < matchCount)
          {
            String matchOp = variableContext.getParameter("matchop_"+value+"_"+Integer.toString(q));
            String matchType = variableContext.getParameter("matchtype_"+value+"_"+Integer.toString(q));
            String matchField = variableContext.getParameter("matchfield_"+value+"_"+Integer.toString(q));
            String matchValue = variableContext.getParameter("matchvalue_"+value+"_"+Integer.toString(q));
            if (matchOp == null || !matchOp.equals("Delete"))
            {
              SpecificationNode matchNode = new SpecificationNode(SPEC_NODE_MATCH);
              matchNode.setAttribute(SPEC_ATTRIBUTE_MATCHTYPE,matchType);
              matchNode.setAttribute(SPEC_ATTRIBUTE_FIELDNAME,matchField);
              if (matchValue == null)
                matchValue = "";
              matchNode.setAttribute(SPEC_ATTRIBUTE_VALUE,matchValue);
              node.addChild(node.getChildCount(),matchNode);
            }
            q++;
          }
          ds.addChild(ds.getChildCount(),node);
			
          // Look for the add operation
          String addMatchOp = variableContext.getParameter("matchop_"+value);
          if (addMatchOp != null && addMatchOp.equals("Add"))
          {
            String matchType = variableContext.getParameter("matchtype_"+value);
            String matchField = variableContext.getParameter("matchfield_"+value);
            String matchValue = variableContext.getParameter("matchvalue_"+value);
            SpecificationNode matchNode = new SpecificationNode(SPEC_NODE_MATCH);
            matchNode.setAttribute(SPEC_ATTRIBUTE_MATCHTYPE,matchType);
            matchNode.setAttribute(SPEC_ATTRIBUTE_FIELDNAME,matchField);
            if (matchValue == null)
              matchValue = "";
            matchNode.setAttribute(SPEC_ATTRIBUTE_VALUE,matchValue);
            node.addChild(node.getChildCount(),matchNode);
          }
			
        }
      }
    }
	
    if (variableContext.getParameter("hasmimetypes") != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(SPEC_NODE_MIMETYPE))
          ds.removeChild(i);
        else
          i++;
      }
      x = variableContext.getParameterValues("mimetypes");
      if (x != null)
      {
        i = 0;
        while (i < x.length)
        {
          String value = x[i++];
          SpecificationNode node = new SpecificationNode(SPEC_NODE_MIMETYPE);
          node.setAttribute(SPEC_ATTRIBUTE_VALUE,value);
          ds.addChild(ds.getChildCount(),node);
        }
      }
    }

    y = variableContext.getParameter("pathcount");
    if (y != null)
    {
      // Delete all path specs first
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(SPEC_NODE_FOLDER))
          ds.removeChild(i);
        else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(y);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "pathop"+pathDescription;
        y = variableContext.getParameter(pathOpName);
        if (y != null && y.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter("specpath"+pathDescription);
        SpecificationNode node = new SpecificationNode(SPEC_NODE_FOLDER);
        node.setAttribute(SPEC_ATTRIBUTE_VALUE,path);

        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("pathop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter("specpath");
        SpecificationNode node = new SpecificationNode(SPEC_NODE_FOLDER);
        node.setAttribute(SPEC_ATTRIBUTE_VALUE,path);
        ds.addChild(ds.getChildCount(),node);
      }
      else if (op != null && op.equals("Up"))
      {
        // Strip off end
        String path = variableContext.getParameter("specpath");
        int k = path.lastIndexOf("/");
        if (k <= 0)
          path = "/";
        else
          path = path.substring(0,k);
        currentContext.save("specpath",path);
      }
      else if (op != null && op.equals("AddToPath"))
      {
        String path = variableContext.getParameter("specpath");
        String addon = variableContext.getParameter("pathaddon");
        if (addon != null && addon.length() > 0)
        {
          if (path.length() <= 1)
            path = "/" + addon;
          else
            path += "/" + addon;
        }
        currentContext.save("specpath",path);
      }
    }

    y = variableContext.getParameter("specsecurity");
    if (y != null)
    {
      // Delete all security entries first
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("security"))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode node = new SpecificationNode("security");
      node.setAttribute("value",y);
      ds.addChild(ds.getChildCount(),node);

    }

    y = variableContext.getParameter("tokencount");
    if (y != null)
    {
      // Delete all file specs first
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(y);
      i = 0;
      while (i < accessCount)
      {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = "accessop"+accessDescription;
        y = variableContext.getParameter(accessOpName);
        if (y != null && y.equals("Delete"))
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

    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    int i;
    Iterator iter;
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    // Look for document classes
    HashMap documentClasses = new HashMap();
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(SPEC_NODE_DOCUMENTCLASS))
      {
        String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec spec = new org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec(sn);
        documentClasses.put(value,spec);
      }
    }
    String[] sortedDocumentClasses = new String[documentClasses.size()];
    i = 0;
    iter = documentClasses.keySet().iterator();
    while (iter.hasNext())
    {
      sortedDocumentClasses[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sortedDocumentClasses);

    if (sortedDocumentClasses.length == 0)
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.NoIncludedDocumentClasses") + "</nobr></td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.IncludedDocumentClasses") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < sortedDocumentClasses.length)
      {
        String docclass = sortedDocumentClasses[i++];
        out.print(
"        <tr>\n"+
"          <td class=\"description\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(docclass)+"</nobr></td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"displaytable\">\n"+
"              <tr>\n"+
"                <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Metadata") + "</nobr></td>\n"+
"                <td class=\"value\">\n"
        );
        org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec fieldValues = (org.apache.manifoldcf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(docclass);
        if (fieldValues.getAllMetadata())
        {
          out.print(
"                  <nobr>(all metadata values)</nobr>\n"
          );
        }
        else
        {
          String[] valuesList = fieldValues.getMetadataFields();
          java.util.Arrays.sort(valuesList);
          int j = 0;
          while (j < valuesList.length)
          {
            String value = valuesList[j++];
            out.print(
"                  <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
            );
          }
        }
        out.print(
"                </td>\n"+
"              </tr>\n"+
"              <tr>\n"+
"                <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.DocumentsMatching") + "</nobr></td>\n"+
"                <td class=\"value\">\n"
        );
        int matchCount = fieldValues.getMatchCount();
        int q = 0;
        while (q < matchCount)
        {
          String matchType = fieldValues.getMatchType(q);
          String matchField = fieldValues.getMatchField(q);
          String matchValue = fieldValues.getMatchValue(q);
          q++;
          out.print(
"                  <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchField)+" "+matchType+" \""+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchValue)+"\"</nobr><br/>\n"
          );
        }

        if (q == 0)
        {
          out.print(
"                  <nobr>(" + Messages.getBodyString(locale,"FilenetConnector.AllDocumentsInClass") + "\""+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(docclass)+"\")</nobr>\n"
          );
        }
        out.print(
"                </td>\n"+
"              </tr>\n"+
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"
        );
      }
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
    );
    // Look for mime types
    i = 0;
    HashMap mimeTypes = null;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(SPEC_NODE_MIMETYPE))
      {
        String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
        if (mimeTypes == null)
          mimeTypes = new HashMap();
        mimeTypes.put(value,value);
      }
    }

    if (mimeTypes != null)
    {
      String[] sortedMimeTypes = new String[mimeTypes.size()];
      i = 0;
      iter = mimeTypes.keySet().iterator();
      while (iter.hasNext())
      {
        sortedMimeTypes[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sortedMimeTypes);
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.IncludedMimeTypes") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
      );
      i = 0;
      while (i < sortedMimeTypes.length)
      {
        String value = sortedMimeTypes[i++];
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
      out.print(
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.NoIncludedMimeTypes") + "</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    
    // Handle Folders
    i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode spn = ds.getChild(i++);
      if (spn.getType().equals(SPEC_NODE_FOLDER))
      {
        if (seenAny == false)
        {
          seenAny = true;
        }
        out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Folders2") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(spn.getAttributeValue(SPEC_ATTRIBUTE_VALUE))+"</nobr>\n"+
"    </td>\n"+
"  </tr>\n"
        );
      }
    }
    if (seenAny == false)
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"FilenetConnector.AllFoldersSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"
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
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.Security2") + "</nobr></td>\n"+
"    <td class=\"value\">"+(securityOn?"Enabled":"Disabled")+"</td>\n"+
"  </tr>\n"+
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
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FilenetConnector.AccessTokens") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"<br/>\n"
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
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"FilenetConnector.NoAccessTokensSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"</table>\n"
    );
  }

  // UI support methods

  /** Get the set of available document classes, with details */
  public DocumentClassDefinition[] getDocumentClassesDetails()
    throws ManifoldCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      return getDocumentClassesInfo();
    }
    catch (FilenetException e)
    {
      // Base our treatment on the kind of error it is.
      currentTime = System.currentTimeMillis();
      if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
      else
        throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Get the set of available metadata fields per document class */
  public MetadataFieldDefinition[] getDocumentClassMetadataFieldsDetails(String documentClassName)
    throws ServiceInterruption, ManifoldCFException
  {
    long currentTime;
    try
    {
      return getDocumentClassMetadataFieldsInfo(documentClassName);
    }
    catch (FilenetException e)
    {
      // Base our treatment on the kind of error it is.
      currentTime = System.currentTimeMillis();
      if (e.getType() == FilenetException.TYPE_SERVICEINTERRUPTION)
        throw new ServiceInterruption(e.getMessage(),e,currentTime+300000L,currentTime+12*60*60000L,-1,true);
      else
        throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Get the set of available mime types */
  public String[] getMimeTypes()
    throws ManifoldCFException, ServiceInterruption
  {
    // For now, return the list of mime types we know about
    return new String[]
    {
      "application/excel",
        "application/powerpoint",
        "application/ppt",
        "application/rtf",
        "application/xls",
        "text/html",
        "text/rtf",
        "text/pdf",
        "application/x-excel",
        "application/x-msexcel",
        "application/x-mspowerpoint",
        "application/x-msword-doc",
        "application/x-msword",
        "application/x-word",
        "Application/pdf",
        "text/xml",
        "no-type",
        "text/plain",
        "application/pdf",
        "application/x-rtf",
        "application/vnd.ms-excel",
        "application/vnd.ms-pps",
        "application/vnd.ms-powerpoint",
        "application/vnd.ms-word",
        "application/msword",
        "application/msexcel",
        "application/mspowerpoint",
        "application/ms-powerpoint",
        "application/ms-word",
        "application/ms-excel",
        "Adobe",
        "application/Vnd.Ms-Excel",
        "vnd.ms-powerpoint",
        "application/x-pdf",
        "winword",
        "text/richtext",
        "Text",
        "Text/html",
        "application/MSWORD",
        "application/PDF",
        "application/MSEXCEL",
        "application/MSPOWERPOINT"
    };
  }

  // Protected methods

  /** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param documentIdentifier is the document identifier.
  *@return the document uri.
  */
  protected static String convertToURI(String urlBase, String documentIdentifier, int elementNumber, String documentClass)
  {
    // Will this work for sub-types of documents too?  ask - MHL
    return  urlBase  +  "&id=" + documentIdentifier + "&element="+Integer.toString(elementNumber)+"&objectType="+documentClass;
  }

  protected class CheckConnectionThread extends Thread
  {
    protected Throwable exception = null;

    public CheckConnectionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.checkConnection();
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

  /** Check connection, with appropriate retries */
  protected void checkConnection()
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      CheckConnectionThread t = new CheckConnectionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetDocumentClassesInfoThread extends Thread
  {
    protected DocumentClassDefinition[] rval = null;
    protected Throwable exception = null;

    public GetDocumentClassesInfoThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        rval = session.getDocumentClassesDetails();
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

    public DocumentClassDefinition[] getResponse()
    {
      return rval;
    }
  }

  /** Get document class details, with appropriate retries */
  protected DocumentClassDefinition[] getDocumentClassesInfo()
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetDocumentClassesInfoThread t = new GetDocumentClassesInfoThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetDocumentClassesMetadataFieldsInfoThread extends Thread
  {
    protected String documentClassName;
    protected MetadataFieldDefinition[] rval = null;
    protected Throwable exception = null;

    public GetDocumentClassesMetadataFieldsInfoThread(String documentClassName)
    {
      super();
      setDaemon(true);
      this.documentClassName = documentClassName;
    }

    public void run()
    {
      try
      {
        rval = session.getDocumentClassMetadataFieldsDetails(documentClassName);
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

    public MetadataFieldDefinition[] getResponse()
    {
      return rval;
    }
  }

  /** Get document class metadata fields details, with appropriate retries */
  protected MetadataFieldDefinition[] getDocumentClassMetadataFieldsInfo(String documentClassName)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetDocumentClassesMetadataFieldsInfoThread t = new GetDocumentClassesMetadataFieldsInfoThread(documentClassName);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetChildFoldersThread extends Thread
  {
    protected String[] folderPath;
    protected String[] rval = null;
    protected Throwable exception = null;

    public GetChildFoldersThread(String[] folderPath)
    {
      super();
      setDaemon(true);
      this.folderPath = folderPath;
    }

    public void run()
    {
      try
      {
        rval = session.getChildFolders(folderPath);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public String[] getResponse()
    {
      return rval;
    }

    public Throwable getException()
    {
      return exception;
    }

  }


  /** Get child folder names */
  protected String[] doGetChildFolders(String[] folderPath)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetChildFoldersThread t = new GetChildFoldersThread(folderPath);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetMatchingObjectIdsThread extends Thread
  {
    protected String sql;
    protected String[] rval = null;
    protected Throwable exception = null;

    public GetMatchingObjectIdsThread(String sql)
    {
      super();
      setDaemon(true);
      this.sql = sql;
    }

    public void run()
    {
      try
      {
        rval = session.getMatchingObjectIds(sql);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public String[] getResponse()
    {
      return rval;
    }

    public Throwable getException()
    {
      return exception;
    }

  }
  
  /** Get matching object id's for a given query */
  protected String[] doGetMatchingObjectIds(String sql)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetMatchingObjectIdsThread t = new GetMatchingObjectIdsThread(sql);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetDocumentContentCountThread extends Thread
  {
    protected String identifier;
    protected Integer rval = null;
    protected Throwable exception = null;

    public GetDocumentContentCountThread(String identifier)
    {
      super();
      setDaemon(true);
      this.identifier = identifier;
    }

    public void run()
    {
      try
      {
        rval = session.getDocumentContentCount(identifier);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Integer getResponse()
    {
      return rval;
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  protected Integer doGetDocumentContentCount(String documentIdentifier)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetDocumentContentCountThread t = new GetDocumentContentCountThread(documentIdentifier);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }

  }

  protected class GetDocumentInformationThread extends Thread
  {
    protected String docId;
    protected HashMap metadataFields;
    protected FileInfo rval = null;
    protected Throwable exception = null;

    public GetDocumentInformationThread(String docId, HashMap metadataFields)
    {
      super();
      setDaemon(true);
      this.docId = docId;
      this.metadataFields = metadataFields;
    }

    public void run()
    {
      try
      {
        rval = session.getDocumentInformation(docId,metadataFields);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public FileInfo getResponse()
    {
      return rval;
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  /** Get document info */
  protected FileInfo doGetDocumentInformation(String docId, HashMap metadataFields)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetDocumentInformationThread t = new GetDocumentInformationThread(docId,metadataFields);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
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
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class GetDocumentContentsThread extends Thread
  {
    protected String docId;
    protected int elementNumber;
    protected String tempFileName;
    protected Throwable exception = null;

    public GetDocumentContentsThread(String docId, int elementNumber, String tempFileName)
    {
      super();
      setDaemon(true);
      this.docId = docId;
      this.elementNumber = elementNumber;
      this.tempFileName = tempFileName;
    }

    public void run()
    {
      try
      {
        session.getDocumentContents(docId,elementNumber,tempFileName);
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

  /** Get document contents */
  protected void doGetDocumentContents(String docId, int elementNumber, String tempFileName)
    throws FilenetException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      long currentTime;
      GetDocumentContentsThread t = new GetDocumentContentsThread(docId,elementNumber,tempFileName);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof FilenetException)
            throw (FilenetException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: "+e.getMessage(),currentTime+60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }

  }


  // Utility methods

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

}
