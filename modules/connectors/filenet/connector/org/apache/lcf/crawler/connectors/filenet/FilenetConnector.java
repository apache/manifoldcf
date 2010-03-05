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
package org.apache.lcf.crawler.connectors.filenet;

import org.apache.log4j.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import java.util.*;
import java.io.*;
import org.apache.lcf.crawler.common.filenet.*;
import java.rmi.*;
import java.text.SimpleDateFormat;


public class FilenetConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
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
  private final static String defaultAuthorityDenyToken = "McAdAuthority_MC_DEAD_AUTHORITY";

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
    throws LCFException, ServiceInterruption
  {
    if (session == null)
    {
      // Check for parameter validity
      if (userID == null || userID.length() < 1)
        throw new LCFException("Parameter "+CONFIG_PARAM_USERID+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: UserID = '" + userID + "'");

      if (password == null || password.length() < 1)
        throw new LCFException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

      Logging.connectors.debug("FileNet: Password exists");

      if (objectStore == null || objectStore.length() < 1)
        throw new LCFException("Parameter "+CONFIG_PARAM_OBJECTSTORE+" required but not set");

      if (serverProtocol == null || serverProtocol.length() < 1)
        throw new LCFException("Parameter "+CONFIG_PARAM_SERVERPROTOCOL+" required but not set");

      if (serverHostname == null || serverHostname.length() < 1)
        throw new LCFException("Parameter "+CONFIG_PARAM_SERVERHOSTNAME+" required but not set");

      if (serverPort != null && serverPort.length() < 1)
        serverPort = null;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("FileNet: Server URI is '"+serverWSIURI+"'");

      if (docUrlServerProtocol == null || docUrlServerProtocol.length() == 0)
        throw new LCFException("Parameter "+CONFIG_PARAM_URLPROTOCOL+" required but not set");

      if (docUrlServerName == null || docUrlServerName.length() == 0)
        throw new LCFException("Parameter "+CONFIG_PARAM_URLHOSTNAME+" required but not set");

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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throw new LCFException(e.getMessage(),e);
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
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
        throw new LCFException(e.getMessage(),e);
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
    throws LCFException
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws LCFException
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
          throw new LCFException(e.getMessage(),e);
      }
    }
    catch (ServiceInterruption e)
    {
      return "Connection temporarily failed: "+e.getMessage();
    }
    catch (LCFException e)
    {
      return "Connection failed: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws LCFException
  {
    releaseCheck();
  }

  /** Disconnect from Filenet.
  */
  public void disconnect()
    throws LCFException
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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

  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws LCFException, ServiceInterruption
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
            throw new LCFException(e.getMessage(),e);
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
    throws LCFException, ServiceInterruption
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
            throw new LCFException(e.getMessage(),e);
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
    throws LCFException, ServiceInterruption
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
            throw new LCFException("Bad number in identifier: "+documentIdentifier,e);
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
                  throw new LCFException(e.getMessage(),e);
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
                  throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
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
            throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
          }
          catch (IOException e)
          {
            Logging.connectors.error("FileNet: IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
            throw new LCFException("IO Exception ingesting document '"+documentIdentifier+"': "+e.getMessage(),e);
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
            throw new LCFException(e.getMessage(),e);
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
    throws LCFException
  {
    // Nothing to do
  }

  public int getMaxDocumentRequest()
  {
    // 1 at a time, since this connector does not deal with documents en masse, but one at a time.
    return 1;
  }

  // UI support methods

  /** Get the set of available document classes, with details */
  public DocumentClassDefinition[] getDocumentClassesDetails()
    throws LCFException, ServiceInterruption
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
        throw new LCFException(e.getMessage(),e);
    }
  }

  /** Get the set of available metadata fields per document class */
  public MetadataFieldDefinition[] getDocumentClassMetadataFieldsDetails(String documentClassName)
    throws ServiceInterruption, LCFException
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
        throw new LCFException(e.getMessage(),e);
    }
  }

  /** Get the set of available mime types */
  public String[] getMimeTypes()
    throws LCFException, ServiceInterruption
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
    throws FilenetException, LCFException, ServiceInterruption
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
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
