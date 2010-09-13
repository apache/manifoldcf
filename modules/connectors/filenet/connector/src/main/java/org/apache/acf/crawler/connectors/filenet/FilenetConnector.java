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
package org.apache.acf.crawler.connectors.filenet;

import org.apache.log4j.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import org.apache.acf.crawler.system.ACF;
import java.util.*;
import java.io.*;
import org.apache.acf.crawler.common.filenet.*;
import java.rmi.*;
import java.text.SimpleDateFormat;


public class FilenetConnector extends org.apache.acf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

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
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

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
    throws ACFException, ServiceInterruption
  {
    if (session == null)
    {
      // Check for parameter validity
      if (userID == null || userID.length() < 1)
        throw new ACFException("Parameter "+CONFIG_PARAM_USERID+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: UserID = '" + userID + "'");

      if (password == null || password.length() < 1)
        throw new ACFException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

      Logging.connectors.debug("FileNet: Password exists");

      if (objectStore == null || objectStore.length() < 1)
        throw new ACFException("Parameter "+CONFIG_PARAM_OBJECTSTORE+" required but not set");

      if (serverProtocol == null || serverProtocol.length() < 1)
        throw new ACFException("Parameter "+CONFIG_PARAM_SERVERPROTOCOL+" required but not set");

      if (serverHostname == null || serverHostname.length() < 1)
        throw new ACFException("Parameter "+CONFIG_PARAM_SERVERHOSTNAME+" required but not set");

      if (serverPort != null && serverPort.length() < 1)
        serverPort = null;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: Server URI is '"+serverWSIURI+"'");

      if (docUrlServerProtocol == null || docUrlServerProtocol.length() == 0)
        throw new ACFException("Parameter "+CONFIG_PARAM_URLPROTOCOL+" required but not set");

      if (docUrlServerName == null || docUrlServerName.length() == 0)
        throw new ACFException("Parameter "+CONFIG_PARAM_URLHOSTNAME+" required but not set");

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
          else
            throw (Error)thr;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throw new ACFException(e.getMessage(),e);
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
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
        throw new ACFException(e.getMessage(),e);
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
    throws ACFException
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
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
  public String[] getBinNames(String documentIdentifier)
  {
    return new String[]{serverHostname};
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH};
  }

  /** Get the folder for the jsps.
  */
  public String getJSPFolder()
  {
    return "filenet";
  }

  /** Connect to filenet.
  */
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
  public String check()
    throws ACFException
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
          throw new ACFException(e.getMessage(),e);
      }
    }
    catch (ServiceInterruption e)
    {
      return "Connection temporarily failed: "+e.getMessage();
    }
    catch (ACFException e)
    {
      return "Connection failed: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws ACFException
  {
    releaseCheck();
  }

  /** Disconnect from Filenet.
  */
  public void disconnect()
    throws ACFException
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
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
  public boolean requestInfo(Configuration output, String command)
    throws ACFException
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
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
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
        ACF.createServiceInterruptionNode(output,e);
      }
      catch (ACFException e)
      {
        ACF.createErrorNode(output,e);
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
    Logging.connectors.debug("FileNet: Inside addSeedDocuments");

    // Go through all the document classes and do a query for each one
    //get all mimetypes and build a SQL "where condition"

    StringBuffer mimeTypesClause = new StringBuffer();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);
      if (n.getType().equals("mimetype"))
      {
        String mimeType = n.getAttributeValue("value");
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

    StringBuffer sqlBuffer = new StringBuffer(" WHERE ([IsCurrentVersion] = TRUE AND (");
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



    String whereClause = sqlBuffer.toString();

    i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);

      if (n.getType().equals("documentclass"))
      {
        String dc = n.getAttributeValue("value");
        DocClassSpec dcs = new DocClassSpec(n);
        int matchCount = dcs.getMatchCount();
        StringBuffer moreWhereClause = new StringBuffer(whereClause);
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
            throw new ACFException(e.getMessage(),e);
        }
      }

      i++;
    }
  }

  protected static String quoteSQLString(String value)
  {
    StringBuffer sb = new StringBuffer();
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
    StringBuffer rval = new StringBuffer();
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

  protected static void print_int(StringBuffer sb, int value, int digits)
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

  protected static int print_digit(StringBuffer sb, int value, int divisor)
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
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activity,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ACFException, ServiceInterruption
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
            StringBuffer versionBuffer = new StringBuffer();

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
            throw new ACFException(e.getMessage(),e);
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
    throws ACFException, ServiceInterruption
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
            throw new ACFException("Bad number in identifier: "+documentIdentifier,e);
          }

          // Unpack the information in the document version
          ArrayList metadataNames = new ArrayList();
          ArrayList metadataValues = new ArrayList();
          ArrayList aclValues = null;
          ArrayList denyAclValues = null;
          StringBuffer documentClass = new StringBuffer();
          StringBuffer urlBase = new StringBuffer();
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
                  activities.deleteDocument(documentIdentifier);
                  i++;
                  continue;
                }
                else
                {
                  activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                    null,documentIdentifier,"Miscellaneous error",e.getMessage(),null);
                  throw new ACFException(e.getMessage(),e);
                }
              }

              // Document fetch completed
              long fileLength = objFileTemp.length();

              activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,
                new Long(fileLength),documentIdentifier,"Success",null,null);

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
                  throw new ACFException(e.getMessage(),e,ACFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                  Logging.connectors.warn("FileNet: IOException closing file input stream: "+e.getMessage(),e);
                }
              }
            }
            finally
            {
              // Delete temp file
              objFileTemp.delete();
            }
          }
          catch (InterruptedIOException e)
          {
            throw new ACFException(e.getMessage(),e,ACFException.INTERRUPTED);
          }
          catch (IOException e)
          {
            Logging.connectors.error("FileNet: IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
            throw new ACFException("IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
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

            activities.deleteDocument(documentIdentifier);
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
            activities.deleteDocument(documentIdentifier);
            i++;
            continue;
          }
          else
          {
            throw new ACFException(e.getMessage(),e);
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
  public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions)
    throws ACFException
  {
    // Nothing to do
  }

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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, ArrayList tabsArray)
    throws ACFException, IOException
  {
    tabsArray.add("Server");
    tabsArray.add("Object Store");
    tabsArray.add("Document URL");
    tabsArray.add("Credentials");
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"The server port must be an integer\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlport.value != \"\" && !isInteger(editconnection.urlport.value))\n"+
"  {\n"+
"    alert(\"The Document URL port must be an integer\");\n"+
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
"    alert(\"The connection requires a FileNet host name\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.serverhostname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"The server port must be an integer\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlhostname.value == \"\")\n"+
"  {\n"+
"    alert(\"The Document URL requires a host name\");\n"+
"    SelectTab(\"Document URL\");\n"+
"    editconnection.urlhostname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.urlport.value != \"\" && !isInteger(editconnection.urlport.value))\n"+
"  {\n"+
"    alert(\"The Document URL port must be an integer\");\n"+
"    SelectTab(\"Document URL\");\n"+
"    editconnection.urlport.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  if (editconnection.filenetdomain.value == \"\")\n"+
"  {\n"+
"    alert(\"The file net domain name cannot be null\");\n"+
"    SelectTab(\"Object Store\");\n"+
"    editconnection.filenetdomain.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.objectstore.value == \"\")\n"+
"  {\n"+
"    alert(\"The object store name cannot be null\");\n"+
"    SelectTab(\"Object Store\");\n"+
"    editconnection.objectstore.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userid.value == \"\")\n"+
"  {\n"+
"    alert(\"The connection requires a valid FileNet user ID\");\n"+
"    SelectTab(\"Credentials\");\n"+
"    editconnection.userid.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.password.value == \"\")\n"+
"  {\n"+
"    alert(\"The connection requires the FileNet user's password\");\n"+
"    SelectTab(\"Credentials\");\n"+
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ACFException, IOException
  {
    String userID = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_USERID);
    if (userID == null)
      userID = "";
    String password = parameters.getObfuscatedParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_PASSWORD);
    if (password == null)
      password = "";
    String filenetdomain = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN);
    if (filenetdomain == null)
    {
      filenetdomain = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN_OLD);
      if (filenetdomain == null)
        filenetdomain = "";
    }
    String objectstore = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_OBJECTSTORE);
    if (objectstore == null)
      objectstore = "";
    String serverprotocol = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPROTOCOL);
    if (serverprotocol == null)
      serverprotocol = "http";
    String serverhostname = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERHOSTNAME);
    if (serverhostname == null)
      serverhostname = "";
    String serverport = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPORT);
    if (serverport == null)
      serverport = "";
    String serverwsilocation = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERWSILOCATION);
    if (serverwsilocation == null)
      serverwsilocation = "wsi/FNCEWS40DIME";
    String urlprotocol = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPROTOCOL);
    if (urlprotocol == null)
      urlprotocol = "http";
    String urlhostname = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLHOSTNAME);
    if (urlhostname == null)
      urlhostname = "";
    String urlport = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPORT);
    if (urlport == null)
      urlport = "";
    String urllocation = parameters.getParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLLOCATION);
    if (urllocation == null)
      urllocation = "Workplace/Browse.jsp";

    // "Server" tab
    if (tabName.equals("Server"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server protocol:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverprotocol\" size=\"2\">\n"+
"        <option value=\"http\" "+(serverprotocol.equals("http")?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(serverprotocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server host name:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"serverhostname\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverhostname)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server port:</nobr></td><td class=\"value\"><input type=\"text\" size=\"5\" name=\"serverport\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverport)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server web service location:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"serverwsilocation\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverwsilocation)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Server tab
      out.print(
"<input type=\"hidden\" name=\"serverprotocol\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverprotocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverhostname\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverhostname)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverport)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverwsilocation\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(serverwsilocation)+"\"/>\n"
      );
    }

    // "Document URL" tab
    if (tabName.equals("Document URL"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document URL protocol:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"urlprotocol\" size=\"2\">\n"+
"        <option value=\"http\" "+(serverprotocol.equals("http")?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(serverprotocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document URL host name:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"urlhostname\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urlhostname)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document URL port:</nobr></td><td class=\"value\"><input type=\"text\" size=\"5\" name=\"urlport\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urlport)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Document URL location:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"urllocation\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urllocation)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Document URL tab
      out.print(
"<input type=\"hidden\" name=\"urlprotocol\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urlprotocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"urlhostname\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urlhostname)+"\"/>\n"+
"<input type=\"hidden\" name=\"urlport\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urlport)+"\"/>\n"+
"<input type=\"hidden\" name=\"urllocation\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(urllocation)+"\"/>\n"
      );
    }

    // "Object Store" tab
    if (tabName.equals("Object Store"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>FileNet domain name:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"filenetdomain\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(filenetdomain)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Object store name:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"objectstore\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(objectstore)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Object Store tab
      out.print(
"<input type=\"hidden\" name=\"filenetdomain\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(filenetdomain)+"\"/>\n"+
"<input type=\"hidden\" name=\"objectstore\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(objectstore)+"\"/>\n"
      );
    }


    // "Credentials" tab
    if (tabName.equals("Credentials"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>User ID:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"userid\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(userID)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Password:</nobr></td><td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(password)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Credentials tab
      out.print(
"<input type=\"hidden\" name=\"userid\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
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
    String serverprotocol = variableContext.getParameter("serverprotocol");
    if (serverprotocol != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPROTOCOL,serverprotocol);

    String serverhostname = variableContext.getParameter("serverhostname");
    if (serverhostname != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERHOSTNAME,serverhostname);
    
    String serverport = variableContext.getParameter("serverport");
    if (serverport != null && serverport.length() > 0)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPORT,serverport);

    String serverwsilocation = variableContext.getParameter("serverwsilocation");
    if (serverwsilocation != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERWSILOCATION,serverwsilocation);

    String urlprotocol = variableContext.getParameter("urlprotocol");
    if (urlprotocol != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPROTOCOL,urlprotocol);

    String urlhostname = variableContext.getParameter("urlhostname");
    if (urlhostname != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLHOSTNAME,urlhostname);

    String urlport = variableContext.getParameter("urlport");
    if (urlport != null && urlport.length() > 0)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPORT,urlport);

    String urllocation = variableContext.getParameter("urllocation");
    if (urllocation != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLLOCATION,urllocation);

    String userID = variableContext.getParameter("userid");
    if (userID != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_USERID,userID);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_PASSWORD,password);

    String filenetdomain = variableContext.getParameter("filenetdomain");
    if (filenetdomain != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN,filenetdomain);

    String objectstore = variableContext.getParameter("objectstore");
    if (objectstore != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_OBJECTSTORE,objectstore);
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
    tabsArray.add("Document Classes");
    tabsArray.add("Mime Types");
    tabsArray.add("Security");
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
"function SpecAddMatch(docclass, anchorvalue)\n"+
"{\n"+
"  if (eval(\"editjob.matchfield_\"+docclass+\".value\") == \"\")\n"+
"  {\n"+
"    alert(\"Select a field first\");\n"+
"    eval(\"editjob.matchfield_\"+docclass+\".focus()\");\n"+
"    return;\n"+
"  }\n"+
"  if (eval(\"editjob.matchtype_\"+docclass+\".value\") == \"\")\n"+
"  {\n"+
"    alert(\"Select a match type\");\n"+
"    eval(\"editjob.matchtype_\"+docclass+\".focus()\");\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"matchop_\"+docclass,\"Add\",anchorvalue);\n"+
"}\n"+
"	\n"+
"function SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"Null tokens not allowed\");\n"+
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
  public void outputSpecificationBody(IHTTPOutput out, DocumentSpecification ds, String tabName)
    throws ACFException, IOException
  {
    int i;
    Iterator iter;

    // "Document Classes" tab
    // Look for document classes
    HashMap documentClasses = new HashMap();
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
      {
        String value = sn.getAttributeValue(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
        // Now, scan for metadata etc.
        org.apache.acf.crawler.connectors.filenet.DocClassSpec spec = new org.apache.acf.crawler.connectors.filenet.DocClassSpec(sn);
        documentClasses.put(value,spec);
      }
    }

    if (tabName.equals("Document Classes"))
    {
      out.print(
"<input type=\"hidden\" name=\"hasdocumentclasses\" value=\"true\"/>\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Fetch the list of valid document classes from the connector
      org.apache.acf.crawler.common.filenet.DocumentClassDefinition[] documentClassArray = null;
      HashMap documentClassFields = new HashMap();
      String message = null;
      try
      {
        documentClassArray = getDocumentClassesDetails();
        int j = 0;
        while (j < documentClassArray.length)
        {
          String documentClass = documentClassArray[j++].getSymbolicName();
          org.apache.acf.crawler.common.filenet.MetadataFieldDefinition[] metaFields = getDocumentClassMetadataFieldsDetails(documentClass);
          documentClassFields.put(documentClass,metaFields);
        }
      }
      catch (ACFException e)
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
"  <tr><td class=\"message\" colspan=\"2\">"+org.apache.acf.ui.util.Encoder.bodyEscape(message)+"</td></tr>\n"
        );
      }
      else
      {
        i = 0;
        while (i < documentClassArray.length)
        {
          org.apache.acf.crawler.common.filenet.DocumentClassDefinition def = documentClassArray[i++];
          String documentClass = def.getSymbolicName();
          String displayName = def.getDisplayName();
          org.apache.acf.crawler.connectors.filenet.DocClassSpec spec = (org.apache.acf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(documentClass+" ("+displayName+")")+":</nobr>\n"+
"    </td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"displaytable\">\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>Include?</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr><input type=\"checkbox\" name=\"documentclasses\" "+((spec != null)?"checked=\"true\"":"")+" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\"></input></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>Document criteria:</nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"displaytable\">\n"
          );
          org.apache.acf.crawler.common.filenet.MetadataFieldDefinition[] fields = (org.apache.acf.crawler.common.filenet.MetadataFieldDefinition[])documentClassFields.get(documentClass);
          String[] fieldArray = new String[fields.length];
          HashMap fieldMap = new HashMap();
          int j = 0;
          while (j < fieldArray.length)
          {
            org.apache.acf.crawler.common.filenet.MetadataFieldDefinition field = fields[j];
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
            String opName = "matchop_" + org.apache.acf.ui.util.Encoder.attributeEscape(documentClass) + "_" +Integer.toString(q);
            String labelName = "match_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
            out.print(
"              <tr>\n"+
"                <td class=\"description\">\n"+
"                  <input type=\"hidden\" name=\""+opName+"\" value=\"\"/>\n"+
"                  <a name=\""+labelName+"\">\n"+
"                    <input type=\"button\" value=\"Delete\" alt=\""+"Delete "+documentClass+" match # "+Integer.toString(q)+"\" onClick='Javascript:SpecOp(\""+opName+"\",\"Delete\",\""+labelName+"\")'/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\""+"matchfield_" + org.apache.acf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchField)+"\"/>\n"+
"                  <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(matchField)+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\""+"matchtype_" + org.apache.acf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+matchType+"\"/>\n"+
"                  <nobr>"+matchType+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input type=\"hidden\" name=\""+"matchvalue_" + org.apache.acf.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchValue)+"\"/>\n"+
"                  <nobr>\""+org.apache.acf.ui.util.Encoder.bodyEscape(matchValue)+"\"</nobr>\n"+
"                </td>\n"+
"              </tr>\n"
            );
            q++;
          }
          if (q == 0)
          {
            out.print(
"              <tr><td class=\"message\" colspan=\"4\"><nobr>(No criteria specified - all documents will be taken)</nobr></td></tr>\n"
            );
          }
          String addLabelName = "match_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
          String addOpName = "matchop_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass);
          out.print(
"              <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"              <tr>\n"+
"                <td class=\"description\">\n"+
"                  <input type=\"hidden\" name=\""+"matchcount_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+Integer.toString(matchCount)+"\"/>\n"+
"                  <input type=\"hidden\" name=\""+addOpName+"\" value=\"\"/>\n"+
"                  <a name=\""+addLabelName+"\">\n"+
"                    <input type=\"button\" value=\"Add\" alt=\""+"Add match for "+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" onClick='Javascript:SpecAddMatch(\""+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\",\"match_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q+1)+"\")'/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <select name=\""+"matchfield_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" size=\"5\">\n"
          );
          q = 0;
          while (q < fieldArray.length)
          {
            String field = fieldArray[q++];
            String dName = (String)fieldMap.get(field);
            out.print(
"                    <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(field)+"\">"+org.apache.acf.ui.util.Encoder.bodyEscape(field+" ("+dName+")")+"</option>\n"
            );
          }
          out.print(
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <select name=\""+"matchtype_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\">\n"+
"                    <option value=\"=\">Equals</option>\n"+
"                    <option value="!=">Not equals</option>\n"+
"                    <option value=\"LIKE\">'Like' (with % wildcards)</option>\n"+
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"value\">\n"+
"                  <input name=\""+"matchvalue_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" type=\"text\" size=\"32\" value=\"\"/>\n"+
"                </td>\n"+
"              </tr>\n"+
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>Ingest all metadata fields?</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr><input type=\"checkbox\" name=\""+"allmetadata_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\"true\" "+((spec != null && spec.getAllMetadata())?"checked=\"\"":"")+"></input></nobr><br/>\n"+
"          </td>\n"+
"        </tr>\n"+
"        <tr>\n"+
"          <td class=\"description\">\n"+
"            <nobr>Metadata fields:</nobr>\n"+
"          </td>\n"+
"          <td class=\"value\">\n"+
"            <nobr>\n"+
"              <select name=\""+"metadatafield_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" multiple=\"true\" size=\"5\">\n"
          );
          j = 0;
          while (j < fieldArray.length)
          {
            String field = fieldArray[j++];
            String dName = (String)fieldMap.get(field);
            out.print(
"                <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(field)+"\" "+((spec!=null && spec.getAllMetadata() == false && spec.checkMetadataIncluded(field))?"selected=\"true\"":"")+">"+org.apache.acf.ui.util.Encoder.bodyEscape(field+" ("+dName+")")+"</option>\n"
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
        org.apache.acf.crawler.connectors.filenet.DocClassSpec spec = (org.apache.acf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
        if (spec.getAllMetadata())
        {
          out.print(
"<input type=\"hidden\" name=\""+"allmetadata_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\"true\"/>\n"
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
"<input type=\"hidden\" name=\""+"metadatafield_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(field)+"\"/>\n"
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
"<input type=\"hidden\" name=\""+"matchfield_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchField)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"matchtype_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+matchType+"\"/>\n"+
"<input type=\"hidden\" name=\""+"matchvalue_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(matchValue)+"\"/>\n"
          );
          q++;
        }
        out.print(
"<input type=\"hidden\" name=\""+"matchcount_"+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\" value=\""+Integer.toString(matchCount)+"\"/>\n"+
"<input type=\"hidden\" name=\"documentclasses\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(documentClass)+"\"/>\n"
        );
      }
    }

    // "Mime Types" tab
    HashMap mimeTypes = null;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
      {
        String value = sn.getAttributeValue(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
        if (mimeTypes == null)
          mimeTypes = new HashMap();
        mimeTypes.put(value,value);
      }
    }

    if (tabName.equals("Mime Types"))
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
      catch (ACFException e)
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
"    <td class=\"message\" colspan=\"2\">"+org.apache.acf.ui.util.Encoder.bodyEscape(message)+"</td>\n"
        );
      }
      else
      {
        out.print(
"    <td class=\"description\"><nobr>Mime types to include:</nobr></td>\n"+
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
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(mimeType)+"\" selected=\"true\">\n"+
"          "+org.apache.acf.ui.util.Encoder.bodyEscape(mimeType)+"\n"+
"        </option>\n"
            );
          }
          else
          {
            out.print(
"        <option value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(mimeType)+"\">\n"+
"          "+org.apache.acf.ui.util.Encoder.bodyEscape(mimeType)+"\n"+
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
"<input type=\"hidden\" name=\"mimetypes\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(mimeType)+"\"/>\n"
          );
        }
      }
    }

    // Security tab
    int k;
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
"    <td class=\"value\" colspan=\"1\">\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"on\" "+((securityOn)?"checked=\"true\"":"")+" />Enabled&nbsp;\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />Disabled\n"+
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
"      <input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" alt=\""+"Delete access token #"+Integer.toString(k)+"\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")'/>\n"+
"      </a>\n"+
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
"        <input type=\"button\" value=\"Add\" alt=\"Add access token\" onClick='Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")'/>\n"+
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
"<input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
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
  public String processSpecificationPost(IPostParameters variableContext, DocumentSpecification ds)
    throws ACFException
  {
    String[] x;
    String y;
    int i;

    if (variableContext.getParameter("hasdocumentclasses") != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
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
          SpecificationNode node = new SpecificationNode(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS);
          node.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,value);
          // Get the allmetadata value for this document class
          String allmetadata = variableContext.getParameter("allmetadata_"+value);
          if (allmetadata == null)
            allmetadata = "false";
          if (allmetadata.equals("true"))
            node.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_ALLMETADATA,allmetadata);
          else
          {
            String[] fields = variableContext.getParameterValues("metadatafield_"+value);
            if (fields != null)
            {
              int j = 0;
              while (j < fields.length)
              {
                String field = fields[j++];
                SpecificationNode sp2 = new SpecificationNode(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_METADATAFIELD);
                sp2.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,field);
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
              SpecificationNode matchNode = new SpecificationNode(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MATCH);
              matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_MATCHTYPE,matchType);
              matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_FIELDNAME,matchField);
              if (matchValue == null)
                matchValue = "";
              matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,matchValue);
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
            SpecificationNode matchNode = new SpecificationNode(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MATCH);
            matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_MATCHTYPE,matchType);
            matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_FIELDNAME,matchField);
            if (matchValue == null)
              matchValue = "";
            matchNode.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,matchValue);
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
        if (ds.getChild(i).getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
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
          SpecificationNode node = new SpecificationNode(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE);
          node.setAttribute(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,value);
          ds.addChild(ds.getChildCount(),node);
        }
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
  public void viewSpecification(IHTTPOutput out, DocumentSpecification ds)
    throws ACFException, IOException
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
      if (sn.getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
      {
        String value = sn.getAttributeValue(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
        org.apache.acf.crawler.connectors.filenet.DocClassSpec spec = new org.apache.acf.crawler.connectors.filenet.DocClassSpec(sn);
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
"    <td class=\"message\" colspan=\"2\"><nobr>No included document classes</nobr></td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"description\"><nobr>Included document classes:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < sortedDocumentClasses.length)
      {
        String docclass = sortedDocumentClasses[i++];
        out.print(
"        <tr>\n"+
"          <td class=\"description\"><nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(docclass)+"</nobr></td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"displaytable\">\n"+
"              <tr>\n"+
"                <td class=\"description\"><nobr>Metadata:</nobr></td>\n"+
"                <td class=\"value\">\n"
        );
        org.apache.acf.crawler.connectors.filenet.DocClassSpec fieldValues = (org.apache.acf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(docclass);
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
"                  <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
            );
          }
        }
        out.print(
"                </td>\n"+
"              </tr>\n"+
"              <tr>\n"+
"                <td class=\"description\"><nobr>Documents matching:</nobr></td>\n"+
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
"                  <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(matchField)+" "+matchType+" \""+org.apache.acf.ui.util.Encoder.bodyEscape(matchValue)+"\"</nobr><br/>\n"
          );
        }

        if (q == 0)
        {
          out.print(
"                  <nobr>(All documents in class \""+org.apache.acf.ui.util.Encoder.bodyEscape(docclass)+"\")</nobr>\n"
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
      if (sn.getType().equals(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
      {
        String value = sn.getAttributeValue(org.apache.acf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
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
"    <td class=\"description\"><nobr>Included mime types:</nobr></td>\n"+
"    <td class=\"value\">\n"
      );
      i = 0;
      while (i < sortedMimeTypes.length)
      {
        String value = sortedMimeTypes[i++];
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
      out.print(
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>No included mime types - ALL will be ingested</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"+
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
"    <td class=\"description\"><nobr>Security:</nobr></td>\n"+
"    <td class=\"value\">"+(securityOn?"Enabled":"Disabled")+"</td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    boolean seenAny = false;
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
"    <td class=\"description\"><nobr>Access tokens:</nobr></td>\n"+
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
"</table>\n"
    );
  }

  // UI support methods

  /** Get the set of available document classes, with details */
  public DocumentClassDefinition[] getDocumentClassesDetails()
    throws ACFException, ServiceInterruption
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
        throw new ACFException(e.getMessage(),e);
    }
  }

  /** Get the set of available metadata fields per document class */
  public MetadataFieldDefinition[] getDocumentClassMetadataFieldsDetails(String documentClassName)
    throws ServiceInterruption, ACFException
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
        throw new ACFException(e.getMessage(),e);
    }
  }

  /** Get the set of available mime types */
  public String[] getMimeTypes()
    throws ACFException, ServiceInterruption
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
    throws FilenetException, ACFException, ServiceInterruption
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
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
    throws FilenetException, ACFException, ServiceInterruption
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
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ACFException(e2.getMessage(),e2,ACFException.INTERRUPTED);
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
  *@param output is the place to write the unpacked array into.
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


}
