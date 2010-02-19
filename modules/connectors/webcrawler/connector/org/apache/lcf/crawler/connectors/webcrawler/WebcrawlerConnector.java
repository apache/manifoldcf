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
package org.apache.lcf.crawler.connectors.webcrawler;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.LCF;

import org.xml.sax.Attributes;

import org.apache.lcf.core.common.XMLDoc;
import org.apache.lcf.agents.common.XMLStream;
import org.apache.lcf.agents.common.XMLContext;
import org.apache.lcf.agents.common.XMLStringContext;
import org.apache.lcf.agents.common.XMLFileContext;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.regex.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;

/** This is the Web Crawler implementation of the IRepositoryConnector interface.
* This connector may be superceded by one that calls out to python, or by a entirely
* python Connector Framework, depending on how the winds blow.
*
*/
public class WebcrawlerConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // First, a couple of very important points.
  // The canonical document identifier is simply a legal URL.  It may have been processed in some way to
  // map it to a "base" form, but that's all.
  //
  // A document's version is mostly calculated using a checksum technique.  It is conceivable that we
  // will eventually want the version to be more cleverly calculated, perhaps by recognizing the document
  // type and extracting more definitive information, so it will be constructed that way, but right now
  // there's only going to be the basic checksum done.
  //
  // This very basic crawler will extract links only from text, html, or xml - where links can readily be
  // found.  More robust extraction technology will be plugged in eventually, so the architecture will be
  // designed for additional plugins.
  //
  // The documents that actually get ingested will have to pass the "fingerprint" test, which will be lifted
  // wholesale from the jcifs connector.  This is to avoid overwhelming the ingestion system with crap.

  // Next, a note about "bins".
  // This crawler places each URL into a set of bins, based on the host that the URL goes to.
  // For example, the URL "http://foo.metacarta.com/stuff.htm" would have the following bins
  // associated with it: "foo.metacarta.com", ".metacarta.com", ".com", and "".  The idea for this
  // is to make it straightforward to group together different URLs into sets, for throttling and
  // secure access purposes.

  // These are the signals used by some methods in this class to communicate results
  protected static final int RESULTSTATUS_FALSE = 0;
  protected static final int RESULTSTATUS_TRUE = 1;
  protected static final int RESULTSTATUS_NOTYETDETERMINED = 2;

  protected static final String[] ingestableMimeTypeArray = new String[]
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


  protected static final String[] interestingMimeTypeArray = new String[]
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

  protected static final Map ingestableMimeTypeMap = new HashMap();
  protected static final Map interestingMimeTypeMap = new HashMap();
  static
  {
    int i = 0;
    while (i < ingestableMimeTypeArray.length)
    {
      String type = ingestableMimeTypeArray[i++];
      ingestableMimeTypeMap.put(type,type);
    }
    i = 0;
    while (i < interestingMimeTypeArray.length)
    {
      String type = interestingMimeTypeArray[i++];
      interestingMimeTypeMap.put(type,type);
    }
  }

  protected static final Map understoodProtocols = new HashMap();
  static
  {
    understoodProtocols.put("http","http");
    understoodProtocols.put("https","https");
  }

  protected static final Map mapLookup = new HashMap();
  static
  {
    mapLookup.put("amp","&");
    mapLookup.put("lt","<");
    mapLookup.put("gt",">");
    mapLookup.put("quot","\"");
  }

  // Usage flag values
  protected static final int ROBOTS_NONE = 0;
  protected static final int ROBOTS_DATA = 1;
  protected static final int ROBOTS_ALL = 2;

  // Relationship types
  public final static String REL_LINK = "link";
  public final static String REL_REDIRECT = "redirect";

  // Activity types
  public final static String ACTIVITY_FETCH = "fetch";
  public final static String ACTIVITY_ROBOTSPARSE = "robots parse";
  public final static String ACTIVITY_LOGON_START = "begin logon";
  public final static String ACTIVITY_LOGON_END = "end logon";

  // Fetch types
  protected final static String FETCH_ROBOTS = "ROBOTS";
  protected final static String FETCH_STANDARD = "URL";
  protected final static String FETCH_LOGIN = "LOGIN";

  /** Robots usage flag */
  protected int robotsUsage = ROBOTS_ALL;
  /** The user-agent for this connector instance */
  protected String userAgent = null;
  /** The email address for this connector instance */
  protected String from = null;
  /** Connection timeout, milliseconds. */
  protected int connectionTimeoutMilliseconds = 60000;
  /** Socket timeout, milliseconds */
  protected int socketTimeoutMilliseconds = 300000;

  // Canonicalization enabling/disabling.  Eventually this will probably need to be by regular expression.

  /** The throttle description */
  protected ThrottleDescription throttleDescription = null;
  /** The credentials description */
  protected CredentialsDescription credentialsDescription = null;
  /** The trusts description */
  protected TrustsDescription trustsDescription = null;

  /** The robots manager currently used by this instance */
  protected RobotsManager robotsManager = null;
  /** The DNS manager currently used by this instance */
  protected DNSManager dnsManager = null;
  /** The cookie manager used by this instance */
  protected CookieManager cookieManager = null;

  /** This flag is set when the instance has been initialized */
  protected boolean isInitialized = false;

  /** This is where we keep data around between the getVersions() phase and the processDocuments() phase. */
  protected static DataCache cache = new DataCache();

  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = "McAdAuthority_MC_DEAD_AUTHORITY";

  /** Constructor.
  */
  public WebcrawlerConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  public int getConnectorModel()
  {
    // We return all seeds every time.
    return MODEL_ALL;
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
    return "webcrawler";
  }

  /** Install the connector.
  * This method is called to initialize persistent storage for the connector, such as database tables etc.
  * It is called when the connector is registered.
  *@param threadContext is the current thread context.
  */
  public void install(IThreadContext threadContext)
    throws LCFException
  {
    // Install
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
      LCF.getMasterDatabaseName(),
      LCF.getMasterDatabaseUsername(),
      LCF.getMasterDatabasePassword());

    RobotsManager rm = new RobotsManager(threadContext,mainDatabase);
    DNSManager dns = new DNSManager(threadContext,mainDatabase);
    CookieManager cm = new CookieManager(threadContext,mainDatabase);
    mainDatabase.beginTransaction();
    try
    {
      rm.install();
      dns.install();
      cm.install();
    }
    catch (LCFException e)
    {
      mainDatabase.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      mainDatabase.signalRollback();
      throw e;
    }
    finally
    {
      mainDatabase.endTransaction();
    }
  }


  /** Uninstall the connector.
  * This method is called to remove persistent storage for the connector, such as database tables etc.
  * It is called when the connector is deregistered.
  *@param threadContext is the current thread context.
  */
  public void deinstall(IThreadContext threadContext)
    throws LCFException
  {
    // Uninstall
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
      LCF.getMasterDatabaseName(),
      LCF.getMasterDatabaseUsername(),
      LCF.getMasterDatabasePassword());

    RobotsManager rm = new RobotsManager(threadContext,mainDatabase);
    DNSManager dns = new DNSManager(threadContext,mainDatabase);
    CookieManager cm = new CookieManager(threadContext,mainDatabase);
    mainDatabase.beginTransaction();
    try
    {
      cm.deinstall();
      rm.deinstall();
      dns.deinstall();
    }
    catch (LCFException e)
    {
      mainDatabase.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      mainDatabase.signalRollback();
      throw e;
    }
    finally
    {
      mainDatabase.endTransaction();
    }
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH, ACTIVITY_ROBOTSPARSE, ACTIVITY_LOGON_START, ACTIVITY_LOGON_END};
  }


  /** Return the list of relationship types that this connector recognizes.
  *@return the list.
  */
  public String[] getRelationshipTypes()
  {
    return new String[]{REL_LINK,REL_REDIRECT};
  }

  /** Clear out any state information specific to a given thread.
  * This method is called when this object is returned to the connection pool.
  */
  public void clearThreadContext()
  {
    super.clearThreadContext();
    robotsManager = null;
    dnsManager = null;
    cookieManager = null;
  }

  /** Start a session */
  protected void getSession()
    throws LCFException
  {
    // Handle the stuff that requires a thread context
    if (robotsManager == null || dnsManager == null || cookieManager == null)
    {
      IDBInterface databaseHandle = DBInterfaceFactory.make(currentContext,
        LCF.getMasterDatabaseName(),
        LCF.getMasterDatabaseUsername(),
        LCF.getMasterDatabasePassword());

      robotsManager = new RobotsManager(currentContext,databaseHandle);
      dnsManager = new DNSManager(currentContext,databaseHandle);
      cookieManager = new CookieManager(currentContext,databaseHandle);
    }

    // Handle everything else
    if (!isInitialized)
    {
      String x;

      String emailAddress = params.getParameter(WebcrawlerConfig.PARAMETER_EMAIL);
      if (emailAddress == null)
        throw new LCFException("Missing email address");
      userAgent = "ApacheLCFWebCrawler; "+emailAddress+")";
      from = emailAddress;

      x = params.getParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE);
      robotsUsage = ROBOTS_ALL;
      if (x == null || x.length() == 0 || x.equals("all"))
        robotsUsage = ROBOTS_ALL;
      else if (x.equals("none"))
        robotsUsage = ROBOTS_NONE;
      else if (x.equals("data"))
        robotsUsage = ROBOTS_DATA;

      throttleDescription = new ThrottleDescription(params);
      credentialsDescription = new CredentialsDescription(params);
      trustsDescription = new TrustsDescription(params);

      isInitialized = true;
    }
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws LCFException
  {
    ThrottledFetcher.flushIdleConnections();
  }

  /** Check status of connection.
  */
  public String check()
    throws LCFException
  {
    getSession();
    return super.check();
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws LCFException
  {
    throttleDescription = null;
    credentialsDescription = null;
    trustsDescription = null;
    userAgent = null;
    from = null;
    isInitialized = false;

    super.disconnect();
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
    try
    {
      java.net.URI uri = new java.net.URI(documentIdentifier);
      return new String[]{uri.getHost()};
    }
    catch (URISyntaxException e)
    {
      return new String[]{""};
    }

  }

  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *
  * This method can choose to find repository changes that happen only during the specified time interval.
  * The seeds recorded by this method will be viewed by the framework based on what the
  * getConnectorModel() method returns.
  *
  * It is not a big problem if the connector chooses to create more seeds than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * The times passed to this method may be interpreted for greatest efficiency.  The time ranges
  * any given job uses with this connector will not overlap, but will proceed starting at 0 and going
  * to the "current time", each time the job is run.  For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding start time to 0.  The
  * seeding start time may also be set to 0 on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws LCFException, ServiceInterruption
  {
    getSession();

    DocumentURLFilter filter = new DocumentURLFilter(spec);

    // This is the call that's used to seed everything.
    // We just find the current seeds, and create the appropriate iterator.
    String seeds = "";
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_SEEDS))
        seeds = sn.getValue();
    }

    // Break up the seeds string and iterate over the results.
    ArrayList list = stringToArray(seeds);
    // We must only return valid urls here!!!
    int index = 0;
    while (index < list.size())
    {
      String urlCandidate = (String)list.get(index++);
      String documentIdentifier = makeDocumentIdentifier(null,urlCandidate,filter);
      if (documentIdentifier == null)
      {
        // Bad seed.  Log it, and continue!
        Logging.connectors.warn("WEB: Illegal seed URL '"+urlCandidate+"'");
        continue;
      }
      activities.addSeedDocument(documentIdentifier,calculateDocumentEvents(activities,documentIdentifier));
    }
  }

  // Session login states (so we can use the same fetch logic multiple times)

  /** Normal fetch of content document.  (For all we know, we're logged in already). */
  protected static final int SESSIONSTATE_NORMAL = 0;
  /** We're in 'login mode' */
  protected static final int SESSIONSTATE_LOGIN = 1;

  // Result signals
  protected static final int RESULT_NO_DOCUMENT = 0;
  protected static final int RESULT_NO_VERSION = 1;
  protected static final int RESULT_VERSION_NEEDED = 2;
  protected static final int RESULT_RETRY_DOCUMENT = 3;


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
    throws LCFException, ServiceInterruption
  {
    getSession();

    // Here's the maximum number of connections we are going to allow.
    int connectionLimit = 200;

    // Forced acls
    String[] acls = getAcls(spec);
    // Sort it,
    java.util.Arrays.sort(acls);

    // Build a map of the metadata names and values from the spec
    ArrayList namesAndValues = findMetadata(spec);
    // Create an array of name/value fixedlists
    String[] metadata = new String[namesAndValues.size()];
    int k = 0;
    String[] fixedListStrings = new String[2];
    while (k < metadata.length)
    {
      NameValue nv = (NameValue)namesAndValues.get(k);
      String name = nv.getName();
      String value = nv.getValue();
      fixedListStrings[0] = name;
      fixedListStrings[1] = value;
      StringBuffer newsb = new StringBuffer();
      packFixedList(newsb,fixedListStrings,'=');
      metadata[k++] = newsb.toString();
    }
    java.util.Arrays.sort(metadata);

    // Since document specifications can change, we need to look at each url and filter it as part of the
    // process of getting version strings.  To do that, we need to compile the DocumentSpecification into
    // an object that knows how to do this.
    DocumentURLFilter filter = new DocumentURLFilter(spec);

    String[] rval = new String[documentIdentifiers.length];

    long currentTime = System.currentTimeMillis();

    // There are two ways to handle any document that's not available.  The first is to remove it.  The second is to keep it, but mark it with an empty version string.
    // With the web crawler, the major concern with simply removing the document is that it might be referred to from multiple places - and in addition
    // it will get requeued every time the parent document is processed.  This is not optimal because it represents churn.
    // On the other hand, keeping the document in the queue causes the queue to bloat, which is also not optimal, and it makes the crawler basically
    // incapable of deleting documents.
    // Since the primary use of the crawler is expected to be repeated intranet crawls,  I've thus chosen to optimize the crawler for accuracy rather than performance
    // - if the document is gone, I just remove it, and expect churn when recrawling activities occur.
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      String documentIdentifier = documentIdentifiers[i];
      // Verify that the url is legal
      if (filter.isDocumentLegal(documentIdentifier))
      {
        // The first thing we need to know is whether this url is part of a session-protected area.  We'll use that information
        // later to detect redirection to login.
        SequenceCredentials sessionCredential = getSequenceCredential(documentIdentifier);

        // Set up the initial state and state variables.
        int sessionState = SESSIONSTATE_NORMAL;
        String currentURI = documentIdentifier;
        FormData formData = null;
        // Calculate an event name; we'll need this to control sequencing.
        String globalSequenceEvent;
        if (sessionCredential != null)
        {
          String sequenceKey = sessionCredential.getSequenceKey();
          globalSequenceEvent = makeSessionLoginEventName(activities,sequenceKey);
        }
        else
          globalSequenceEvent = null;

        // This is the main 'state loop'.  The code is structured to use the finally clause from the following try to clean up any
        // events that were created within the loop.  The loop itself has two parts: document fetch, and logic to figure out what state to transition
        // to (e.g. how to process the fetched document).  A signal variable is used to signal the desired outcome.
        // We need to be sure we clean up the sequence event in case there's an error, so put a try/finally around everything.
        try
        {
          // Login pages are special in that I *don't* require them to do a robots check.  The reason why is because it is conceivable that a
          // site may inadvertantly exclude them via robots, and yet allow content pages to be scanned.  This would effectively exclude session login
          // for that site if we adhered to the strict policy.  Since login pages have to be exclusively identified as being special, explicit
          // permission is effectively granted by the user in any case.

          int resultSignal = RESULT_NO_DOCUMENT;
          // The result code to be activity logging, or null if no activity logging desired.
          String activityResultCode = null;
          // The result context message, which will be used for logging and activity logging if enabled.
          String contextMessage = null;
          // The result context exception, which will be used for logging if needed.
          Throwable contextException = null;
          // The checksum, which will be needed if resultSignal is RESULT_VERSION_NEEDED.
          String checkSum = null;

          while (true)
          {
            try
            {
              // Do the mapping from the current host name to the IP address
              URL url = new URL(currentURI);
              String hostName = url.getHost();
              StringBuffer ipAddressBuffer = new StringBuffer();
              int ipAddressStatus = lookupIPAddress(currentURI,activities,hostName,currentTime,ipAddressBuffer);
              if (ipAddressStatus == RESULTSTATUS_TRUE)
              {
                String ipAddress = ipAddressBuffer.toString();
                String protocol = url.getProtocol();
                int port = url.getPort();
                if (port == -1)
                  port = url.getDefaultPort();

                // Try to fetch the document.  We'll need its bin names first.
                String[] binNames = getBinNames(currentURI);

                // Get the credentials for this document (if any)
                PageCredentials credential = getPageCredential(currentURI);
                IKeystoreManager trustStore;
                // Save effort - only bother to get a trust store if this is https
                if (protocol.equalsIgnoreCase("https"))
                  // null return is possible here; indicates "trust everything"
                  trustStore = getTrustStore(currentURI);
                else
                  trustStore = KeystoreManagerFactory.make("");
                // Check robots, if enabled, and if we're fetching the primary document identifier.  See comment above.
                int robotsStatus = RESULTSTATUS_TRUE;
                if (!documentIdentifier.equals(currentURI) || robotsUsage < ROBOTS_DATA || (robotsStatus = checkFetchAllowed(documentIdentifier,protocol,ipAddress,port,credential,trustStore,hostName,binNames,currentTime,
                  url.getFile(),activities,connectionLimit)) == RESULTSTATUS_TRUE)
                {
                  // Passed the robots check!

                  // Find whatever login parameters apply.  This will be null if currentURI is not a login page, and will contain
                  // interesting information if it is.
                  LoginCookies lc = null;
                  if (sessionCredential != null)
                  {
                    lc = cookieManager.readCookies(sessionCredential.getSequenceKey());
                  }

                  // Prepare to perform the fetch, and decide what to do with the document.
                  //
                  IThrottledConnection connection = ThrottledFetcher.getConnection(protocol,ipAddress,port,
                    credential,trustStore,throttleDescription,binNames,connectionLimit);
                  try
                  {
                    connection.beginFetch((sessionState == SESSIONSTATE_LOGIN)?FETCH_LOGIN:FETCH_STANDARD);
                    try
                    {

                      // Execute the fetch!
                      connection.executeFetch(url.getFile(),userAgent,from,connectionTimeoutMilliseconds,
                        socketTimeoutMilliseconds,false,hostName,formData,lc);
                      int response = connection.getResponseCode();

                      if (response == 200 || response == 302 || response == 301)
                      {
                        // If this was part of the login sequence, update the cookies regardless of what else happens
                        if (sessionState == SESSIONSTATE_LOGIN)
                        {
                          // Update the cookies
                          LoginCookies lastFetchCookies = connection.getLastFetchCookies();
                          cookieManager.updateCookies(sessionCredential.getSequenceKey(),lastFetchCookies);
                        }

                        // Decide whether to exclude this document based on what we see here.
                        // Basically, we want to get rid of everything that we (a) don't know what
                        // to do with in the ingestion system, and (b) we can't get useful links from.

                        String contentType = connection.getResponseHeader("Content-Type");
                        // Some sites have multiple content types.  We just look at the LAST one in that case.
                        if (contentType != null)
                        {
                          String[] contentTypes = contentType.split(",");
                          if (contentTypes.length > 0)
                            contentType = contentTypes[contentTypes.length-1].trim();
                          else
                            contentType = null;
                        }

                        if (isContentInteresting(currentURI,response,contentType))
                        {
                          // Treat it as real, and cache it.
                          checkSum = cache.addData(activities,currentURI,connection);
                          resultSignal = RESULT_VERSION_NEEDED;
                          activityResultCode = null;
                        }
                        else
                        {
                          contextMessage = "it had the wrong content type";
                          resultSignal = RESULT_NO_DOCUMENT;
                          activityResultCode = null;
                        }
                      }
                      else
                      {
                        // We got some kind of http error code.
                        // We don't want to remove it from the queue entirely, because that would cause us to lose track of the item, and therefore lose
                        // control of all scheduling around it.  Instead, we leave it on the queue and give it an empty version string; that will lead it to be
                        // reprocessed without fail on the next scheduled check.
                        contextMessage = "it failed to fetch (status="+Integer.toString(response)+")";
                        resultSignal = RESULT_NO_VERSION;
                        activityResultCode = null;
                      }
                    }
                    catch (LCFException e)
                    {
                      connection.noteInterrupted(e);
                      throw e;
                    }
                    catch (ServiceInterruption e)
                    {
                      connection.noteInterrupted(e);
                      throw e;
                    }
                    finally
                    {
                      connection.doneFetch(activities);
                    }
                  }
                  finally
                  {
                    connection.close();
                  }

                  // State transition logic.  If the result indicates a successful fetch so far, we need to decide where to go next.
                  // This happens AFTER we've released all the connections, because it's conceivable that processing here might be
                  // significant, and we don't want to tie things up unnecessarily.
                  String preferredLink = null;
                  String preferredRedirection = null;
                  formData = null;
                  if (resultSignal == RESULT_VERSION_NEEDED)
                  {
                    // If we get here, we know:
                    // (a) There's a cached version of the page on disk we can read as many times as necessary;
                    // (b) The saved cookies have not been updated yet, so we'll need to do that where appropriate.

                    // The way we determine if we're in the login sequence for a site is by TWO criteria:
                    // (1) The URI must match the specified regular expression, and
                    // (2) The data from that URI must contain the specified form or link information.
                    // We use the same criteria to look for the exit from a sequence.  So, in essence, we're *always* going to need to know whether we're
                    // officially in the sequence, or not, so we evaluate it always.
                    boolean isLoginPage = false;
                    if (sessionCredential != null)
                    {
                      Iterator iterMatches = sessionCredential.findLoginParameters(currentURI);
                      boolean seenAnything = false;
                      boolean seenFormError = false;
                      boolean seenLinkError = false;
                      boolean seenRedirectionError = false;
                      while (iterMatches.hasNext())
                      {
                        seenAnything = true;
                        LoginParameters lp = (LoginParameters)iterMatches.next();
                        // Note that more than one of the rules may match.
                        // In that case, a clear order of precedence applies between form-style rules and link-style: form has priority.
                        // If more than one of the same kind of rule is seen, then all bets are off, a warning is displayed, and nothing is
                        // matched.

                        // Parse the page; it had better match up!  Otherwise we get null back.
                        FormData newFormData = findHTMLForm(currentURI,lp);
                        if (newFormData != null)
                        {
                          if (formData != null)
                          {
                            // Oops, more than one matching form rule.  Complain.
                            seenFormError = true;
                            formData = null;
                          }
                          else if (!seenFormError)
                          {
                            formData = newFormData;
                            preferredLink = null;
                            preferredRedirection = null;
                          }
                        }
                        else
                        {
                          // Look for the preferred link instead.
                          String newPreferredLink = findHTMLLinkURI(currentURI,lp);
                          if (newPreferredLink != null)
                          {
                            if (preferredLink != null)
                            {
                              // Oops
                              seenLinkError = true;
                              preferredLink = null;
                            }
                            else if (!seenLinkError && !seenFormError && formData == null)
                            {
                              preferredLink = newPreferredLink;
                              preferredRedirection = null;
                            }
                          }
                          else
                          {
                            // Look for the preferred redirection.
                            String newPreferredRedirection = findPreferredRedirectionURI(currentURI,lp);
                            if (newPreferredRedirection != null)
                            {
                              if (preferredRedirection != null)
                              {
                                seenRedirectionError = true;
                                preferredRedirection = null;
                              }
                              else if (!seenRedirectionError && !seenLinkError && !seenFormError && formData == null && preferredLink == null)
                              {
                                preferredRedirection = newPreferredRedirection;
                              }
                            }
                          }
                        }
                      }

                      // Now, evaluate all the data and pick the right rule
                      if (formData != null)
                      {
                        // We found the right form!  And, we filled it in.  So now we enter the "login sequence".
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: Document '"+currentURI+"' matches form, so determined to be login page for sequence '"+sessionCredential.getSequenceKey()+"'");
                        isLoginPage = true;
                      }
                      else if (preferredLink != null)
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: Document '"+currentURI+"' matches preferred link, so determined to be login page for sequence '"+sessionCredential.getSequenceKey()+"'");
                        isLoginPage = true;
                      }
                      else if (preferredRedirection != null)
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: Document '"+currentURI+"' matches preferred redirection, so determined to be login page for sequence '"+sessionCredential.getSequenceKey()+"'");
                        isLoginPage = true;
                      }
                      else
                      {
                        if (seenAnything && Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: Document '"+currentURI+"' did not match expected form, link, or redirection content for sequence '"+sessionCredential.getSequenceKey()+"'");
                      }
                    }

                    // Should we do a state transition into the "logging in" state?
                    if (sessionState == SESSIONSTATE_NORMAL && isLoginPage)
                    {
                      // Entering the login sequence.  Make sure we actually can do this...
                      if (activities.beginEventSequence(globalSequenceEvent))
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: For document '"+documentIdentifier+"', beginning login sequence '"+sessionCredential.getSequenceKey()+"'");

                        activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_START,
                          null,sessionCredential.getSequenceKey(),"OK",null,null);

                        // Transition to the right state, etc.
                        sessionState = SESSIONSTATE_LOGIN;
                      }
                      else
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("WEB: For document '"+documentIdentifier+"', login sequence '"+sessionCredential.getSequenceKey()+"' was already in progress.");

                        // Didn't make it in.  Retry the main URI when the proper conditions are met.
                        // We don't want the cached data anymore.
                        cache.deleteData(currentURI);
                        contextMessage = "login sequence already in progress";
                        resultSignal = RESULT_RETRY_DOCUMENT;
                        activityResultCode = null;
                      }
                    }
                    else if (sessionState == SESSIONSTATE_LOGIN && isLoginPage == false)
                    {
                      //== Exit login mode ==
                      activities.completeEventSequence(globalSequenceEvent);
                      activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                        null,sessionCredential.getSequenceKey(),"OK",null,null);
                      sessionState = SESSIONSTATE_NORMAL;
                      // Make sure we go back and try the original document again, if we happened to have been directed somewhere else
                      if (!currentURI.equals(documentIdentifier))
                      {
                        cache.deleteData(currentURI);
                        currentURI = documentIdentifier;
                        continue;
                      }
                      // Otherwise, the last fetch stands on its own.  Fall through, and allow processing and link extraction
                    }

                    // Now, based on the session state and the document contents, decide how to proceed
                    if (resultSignal == RESULT_VERSION_NEEDED && sessionState == SESSIONSTATE_LOGIN)
                    {
                      // We are dealing with a login page!

                      // We need to (a) figure out what the next URI should be, and (b) record form information that it might need.
                      // This is a bit dicey because there's really
                      // no good way to *guarantee* that we pick the right one, if there's more than one available.
                      // What we do is the following:
                      //
                      // (a) We look for matching forms.  If we found one, we submit it.
                      // (b) Look for redirections.
                      // (c) If there are links that vector within the login sequence, we pick one of those preferentially.
                      // (d) If there are no links that vector within the login sequence, we pick one of the other links.
                      //
                      // Note well that it's probably going to be pretty easy to get this code stuck in an infinite login sequence.
                      // While that won't be a problem performance-wise (because everything is appropriately throttled), it
                      // is obviously not ideal, and furthermore, it will not be possible to crawl a site for which this occurs.
                      //
                      // Longer time (and with higher complexity) we can solve this problem by allowing the user to *specify*
                      // which link they want us to pick for a page.  Hopefully this would not be necessary.

                      // Locate the next target URI.
                      String targetURI;
                      if (formData != null)
                        targetURI = formData.getActionURI();
                      else if (preferredLink != null)
                        targetURI = preferredLink;
                      else
                        targetURI = preferredRedirection;

                      // Definitely we don't want the cached data anymore
                      cache.deleteData(currentURI);

                      // If the target URI is null, it means we could not find a suitable link.  Treat this exactly the same
                      // way as if the link found exited login mode.
                      if (targetURI == null)
                      {
                        //== Exiting login mode ==
                        activities.completeEventSequence(globalSequenceEvent);
                        activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                          null,sessionCredential.getSequenceKey(),"NEXT LINK NOT FOUND",null,null);
                        sessionState = SESSIONSTATE_NORMAL;
                        // Make sure we go back and try the original document again, no matter where we got directed to
                        currentURI = documentIdentifier;
                      }
                      else
                      {
                        currentURI = targetURI;
                      }
                      continue;
                    }
                    else if (resultSignal != RESULT_VERSION_NEEDED && sessionState == SESSIONSTATE_LOGIN)
                    {
                      // The next URL we fetched in the logon sequence turned out to be unsuitable.
                      // That means that the logon sequence is fundamentally wrong.  The session thus ends,
                      // and of course it will retry, but that's neither here nor there.
                      //== Exiting login mode ==
                      activities.completeEventSequence(globalSequenceEvent);
                      activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                        null,sessionCredential.getSequenceKey(),"LINK TARGET UNSUITABLE",null,null);
                      sessionState = SESSIONSTATE_NORMAL;
                      // Fall through, leaving everything else alone.
                    }
                  }

                }
                else
                {
                  if (robotsStatus == RESULTSTATUS_FALSE)
                  {
                    activityResultCode = "-11";
                    contextMessage = "robots.txt says so";
                    resultSignal = RESULT_NO_DOCUMENT;
                  }
                  else
                  {
                    // Robots prerequisite in progress
                    activityResultCode = null;
                    resultSignal = RESULT_RETRY_DOCUMENT;
                    contextMessage = "robots prerequisite already in progress";
                  }
                }
              }
              else
              {
                if (ipAddressStatus == RESULTSTATUS_FALSE)
                {
                  activityResultCode = "-10";
                  contextMessage = "ip address not found";
                  resultSignal = RESULT_NO_DOCUMENT;
                }
                else
                {
                  // DNS prerequisite in progress
                  activityResultCode = null;
                  contextMessage = "dns prerequisite already in progress";
                  resultSignal = RESULT_RETRY_DOCUMENT;
                }
              }
            }
            catch (MalformedURLException e)
            {
              // currentURI is malformed.
              // If the document was the primary, we should remove it from the queue.  But if it's part of a login sequence, we'd better just retry later.
              contextMessage = "was not a valid URL: "+e.getMessage();
              contextException = e;
              activityResultCode = "-12";
              resultSignal = RESULT_NO_DOCUMENT;
            }

            // If we fail on a document that's not the primary, the result should be to retry the primary later.
            if (!currentURI.equals(documentIdentifier))
            {
              activityResultCode = null;
              if (contextMessage != null)
                contextMessage = "for login sequence url '"+currentURI+"': "+contextMessage;
              if (resultSignal != RESULT_VERSION_NEEDED)
                resultSignal = RESULT_RETRY_DOCUMENT;
            }

            break;
          }

          // Now, look at the result signal, and set up the version appropriately.
          if (activityResultCode != null)
            activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,activityResultCode,((contextMessage!=null)?contextMessage:""),null);

          switch (resultSignal)
          {
          case RESULT_NO_DOCUMENT:
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Removing url '"+documentIdentifier+"'"+((contextMessage!=null)?" because "+contextMessage:""),contextException);
            rval[i] = null;
            break;
          case RESULT_NO_VERSION:
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Ignoring url '"+documentIdentifier+"'"+((contextMessage!=null)?" because "+contextMessage:""),contextException);
            rval[i] = "";
            break;
          case RESULT_VERSION_NEEDED:
            // Calculate version from document data, which is presumed to be present.
            StringBuffer sb = new StringBuffer();

            // Acls
            packList(sb,acls,'+');
            if (acls.length > 0 && usesDefaultAuthority)
            {
              sb.append('+');
              pack(sb,defaultAuthorityDenyToken,'+');
            }
            else
              sb.append('-');

            // Now, do the metadata
            packList(sb,metadata,'+');
            // Done with the parseable part!  Add the checksum.
            sb.append(checkSum);
            rval[i] = sb.toString();
            break;
          case RESULT_RETRY_DOCUMENT:
            // Document could not be processed right now.
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Retrying url '"+documentIdentifier+"' later"+((contextMessage!=null)?" because "+contextMessage:""),contextException);
            activities.retryDocumentProcessing(documentIdentifier);
            rval[i] = null;
            break;
          default:
            throw new LCFException("Unexpected value for result signal: "+Integer.toString(resultSignal));
          }
        }
        finally
        {
          // Clean up event, if there is one.
          if (sessionState == SESSIONSTATE_LOGIN && globalSequenceEvent != null)
          {
            // Terminate the event
            activities.completeEventSequence(globalSequenceEvent);
          }
        }
      }
      else
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Removing url '"+documentIdentifier+"' because it's not in the set of allowed ones");
        // Use null because we should have already filtered when we queued.
        rval[i] = null;
      }
      i++;
    }
    return rval;
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
    throws LCFException, ServiceInterruption
  {
    getSession();

    DocumentURLFilter filter = new DocumentURLFilter(spec);

    String[] fixedList = new String[2];

    // We need to extract and ingest here.
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      String documentIdentifier = documentIdentifiers[i];
      String version = versions[i];
      boolean doScanOnly = scanOnly[i];

      if (version.length() == 0)
      {
        i++;
        // Leave document in jobqueue, but do NOT get rid of it, or we will wind up seeing it queued again by
        // somebody else.  We *do* have to signal the document to be removed from the index, however, or it will
        // stick around until the job is deleted.
        activities.deleteDocument(documentIdentifier);
        continue;
      }

      // If scanOnly is set, we never ingest.  But all else is the same.
      if (!doScanOnly)
      {
        // Consider this document for ingestion.
        // We can exclude it if it does not seem to be a kind of document that the ingestion system knows
        // about.

        if (isDataIngestable(documentIdentifier))
        {
          // Ingest the document
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Decided to ingest '"+documentIdentifier+"'");

          // Unpack the version string
          ArrayList acls = new ArrayList();
          StringBuffer denyAclBuffer = new StringBuffer();
          ArrayList metadata = new ArrayList();
          int index = unpackList(acls,version,0,'+');
          if (index < version.length() && version.charAt(index++) == '+')
          {
            index = unpack(denyAclBuffer,version,index,'+');
          }
          index = unpackList(metadata,version,index,'+');

          RepositoryDocument rd = new RepositoryDocument();

          // Turn into acls and add into description
          String[] aclArray = new String[acls.size()];
          int j = 0;
          while (j < aclArray.length)
          {
            aclArray[j] = (String)acls.get(j);
            j++;
          }
          rd.setACL(aclArray);
          if (denyAclBuffer.length() > 0)
          {
            String[] denyAclArray = new String[]{denyAclBuffer.toString()};
            rd.setDenyACL(denyAclArray);
          }

          // Grab metadata
          HashMap metaHash = new HashMap();
          int k = 0;
          while (k < metadata.size())
          {
            String metadataItem = (String)metadata.get(k++);
            unpackFixedList(fixedList,metadataItem,0,'=');
            HashMap hashValue = (HashMap)metaHash.get(fixedList[0]);
            if (hashValue == null)
            {
              hashValue = new HashMap();
              metaHash.put(fixedList[0],hashValue);
            }
            hashValue.put(fixedList[1],fixedList[1]);
          }
          Iterator metaIter = metaHash.keySet().iterator();
          while (metaIter.hasNext())
          {
            String key = (String)metaIter.next();
            HashMap metaList = (HashMap)metaHash.get(key);
            String[] values = new String[metaList.size()];
            Iterator iter = metaList.keySet().iterator();
            k = 0;
            while (iter.hasNext())
            {
              values[k] = (String)iter.next();
              k++;
            }
            rd.addField(key,values);
          }

          long length = cache.getDataLength(documentIdentifier);
          InputStream is = cache.getData(documentIdentifier);

          if (is != null)
          {
            try
            {
              rd.setBinary(is,length);
              activities.ingestDocument(documentIdentifier,version,documentIdentifier,rd);
            }
            finally
            {
              try
              {
                is.close();
              }
              catch (java.net.SocketException e)
              {
                throw new LCFException("Socket timeout error closing stream: "+e.getMessage(),e);
              }
              catch (org.apache.commons.httpclient.ConnectTimeoutException e)
              {
                throw new LCFException("Socket connect timeout error closing stream: "+e.getMessage(),e);
              }
              catch (InterruptedIOException e)
              {
                //Logging.connectors.warn("IO interruption seen",e);
                throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
              }
              catch (IOException e)
              {
                throw new LCFException("IO error closing stream: "+e.getMessage(),e);
              }
            }
          }
          else
            Logging.connectors.error("WEB: Expected a cached document for '"+documentIdentifier+"', but none present!");
        }
        else
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Decided not to ingest '"+documentIdentifier+"' because it did not match ingestability criteria");
        }
      }

      // Now, extract links.
      // We'll call the "link extractor" series, so we can plug more stuff in over time.
      extractLinks(documentIdentifier,activities,filter);

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
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      String version = versions[i];
      if (version != null)
      {
        String urlValue = documentIdentifiers[i];
        cache.deleteData(urlValue);
      }
      i++;
    }
  }


  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  public int getMaxDocumentRequest()
  {
    // The web in general does not batch well.  Multiple chunks have no advantage over one-at-a-time requests.
    return 1;
  }

  // Protected methods and classes

  /** Calculate the event name for session login.
  */
  protected String makeSessionLoginEventName(INamingActivity activities, String sequenceKey)
  {
    return activities.createGlobalString(getJSPFolder()+":session:"+sequenceKey);
  }

  /** Calculate the event name for DNS access.
  */
  protected String makeDNSEventName(INamingActivity activities, String hostNameKey)
  {
    // Use the connector unique id, and use a convention that guarantees uniqueness.
    return activities.createGlobalString(getJSPFolder()+":dns:"+hostNameKey);
  }

  /** Look up an ipaddress given a non-canonical host name.
  *@return appropriate status.
  */
  protected int lookupIPAddress(String documentIdentifier, IVersionActivity activities, String hostName, long currentTime, StringBuffer ipAddressBuffer)
    throws LCFException, ServiceInterruption
  {
    String eventName = makeDNSEventName(activities,hostName);
    DNSManager.DNSInfo info = dnsManager.lookup(hostName,currentTime);
    if (info != null)
    {
      String ipAddress = info.getIPAddress();
      if (ipAddress == null)
        return RESULTSTATUS_FALSE;
      ipAddressBuffer.append(ipAddress);
      return RESULTSTATUS_TRUE;
    }
    if (activities.beginEventSequence(eventName))
    {
      //  We uniquely can do the lookup.
      try
      {
        // Fetch it using InetAddress
        InetAddress ip = null;
        try
        {
          ip = InetAddress.getByName(hostName);
        }
        catch (UnknownHostException e)
        {
          // Host is unknown, so leave ipAddress as null.
        }
        String fqdn = null;
        String ipAddress = null;
        if (ip != null)
        {
          fqdn = ip.getCanonicalHostName();
          ipAddress = ip.getHostAddress();
        }
        // Write this to the cache - expiration time 6 hours
        dnsManager.writeDNSData(hostName,fqdn,ipAddress,currentTime + 1000*60*60*6);
        if (ipAddress == null)
          return RESULTSTATUS_FALSE;
        ipAddressBuffer.append(ipAddress);
        return RESULTSTATUS_TRUE;
      }
      finally
      {
        activities.completeEventSequence(eventName);
      }
    }
    else
    {
      // Abort this fetch, since it's blocked.
      return RESULTSTATUS_NOTYETDETERMINED;
    }
  }

  /** Construct the robots key for a host.
  * This is used to look up robots info in the database, and to form the corresponding event name.
  */
  protected static String makeRobotsKey(String protocol, String hostName, int port)
  {
    String rval = hostName + ":" + port;
    // For backwards compatibility, only tack on the protocol if the protocol is not http
    if (!protocol.equalsIgnoreCase("http"))
      rval = protocol.toLowerCase()+":"+rval;
    return rval;
  }

  /** Construct a name for the global web-connector robots event.
  */
  protected String makeRobotsEventName(INamingActivity versionActivities, String robotsKey)
  {
    // Use the connector unique id, and use a convention that guarantees uniqueness.
    return versionActivities.createGlobalString(getJSPFolder()+":robots:"+robotsKey);
  }

  /** Check robots to see if fetch is allowed.
  *@return appropriate resultstatus code.
  */
  protected int checkFetchAllowed(String documentIdentifier, String protocol, String hostIPAddress, int port, PageCredentials credential,
    IKeystoreManager trustStore, String hostName, String[] binNames, long currentTime, String pathString, IVersionActivity versionActivities, int connectionLimit)
    throws LCFException, ServiceInterruption
  {
    // hostNameAndPort is the key for looking up the robots file in the database
    String hostNameAndPort = makeRobotsKey(protocol,hostName,port);
    String hostIPAddressAndPort = hostIPAddress + ":" + port;

    Boolean info = robotsManager.checkFetchAllowed(userAgent,hostNameAndPort,currentTime,pathString,versionActivities);
    if (info != null)
    {
      if (info.booleanValue())
        return RESULTSTATUS_TRUE;
      else
        return RESULTSTATUS_FALSE;
    }

    // We need to fetch robots.txt.
    // Since this is a prerequisite for many documents, prevent queuing and processing of those documents until the robots document is fetched.

    // Assemble the name of the global web connector robots event.
    String robotsEventName = makeRobotsEventName(versionActivities,hostNameAndPort);
    // Begin the event processing
    if (versionActivities.beginEventSequence(robotsEventName))
    {
      // We've successfully obtained a lock on reading robots for this server!  Now, guarantee that we'll free it, by instantiating a try/finally
      try
      {
        IThrottledConnection connection = ThrottledFetcher.getConnection(protocol,hostIPAddress,port,credential,
          trustStore,throttleDescription,binNames,connectionLimit);
        try
        {
          connection.beginFetch(FETCH_ROBOTS);
          try
          {
            connection.executeFetch("/robots.txt",userAgent,from,connectionTimeoutMilliseconds,socketTimeoutMilliseconds,true,hostName,null,null);
            long expirationTime = currentTime+1000*60*60*24;
            int code = connection.getResponseCode();
            if (code == 200)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Web: Fetch of robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"') succeeded!");

              InputStream is = connection.getResponseBodyStream();
              try
              {
                // Write this to the cache - expiration time 24 hours
                robotsManager.writeRobotsData(hostNameAndPort,expirationTime,is);
              }
              finally
              {
                is.close();
              }
            }
            else
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Web: Fetch of robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"') failed with error "+Integer.toString(code));
              // Write this to the cache - expiration time 24 hours
              robotsManager.writeRobotsData(hostNameAndPort,expirationTime,null);
            }
          }
          catch (ServiceInterruption e)
          {
            // A service interruption on a robots fetch should save the fact that no robots.txt is available, and leave it be for 6 hours that way.
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Web: Timeout fetching robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"'); assuming robots missing for now: "+e.getMessage(),e);
            long expirationTime = currentTime + 1000*60*60*6;
            try
            {
              robotsManager.writeRobotsData(hostNameAndPort,expirationTime,null);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache");
            }
          }
          catch (java.net.SocketTimeoutException e)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Web: Fetch of robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"') generated Socket Timeout Exception: "+e.getMessage(),e);
            // This COULD be a transient error, so we are more aggressive about retrying a fetch of robots.txt in that case
            long expirationTime = currentTime + 1000*60*60*6;
            try
            {
              robotsManager.writeRobotsData(hostNameAndPort,expirationTime,null);
            }
            catch (java.net.SocketTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (org.apache.commons.httpclient.ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new LCFException("Interrupted: "+e2.getMessage(),e2,LCFException.INTERRUPTED);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
          }
          catch (org.apache.commons.httpclient.ConnectTimeoutException e)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Web: Fetch of robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"') generated Socket Connect Timeout Exception: "+e.getMessage(),e);
            // This COULD be a transient error, so we are more aggressive about retrying a fetch of robots.txt in that case
            long expirationTime = currentTime + 1000*60*60*6;
            try
            {
              robotsManager.writeRobotsData(hostNameAndPort,expirationTime,null);
            }
            catch (java.net.SocketTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (org.apache.commons.httpclient.ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new LCFException("Interrupted: "+e2.getMessage(),e2,LCFException.INTERRUPTED);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
          }
          catch (InterruptedIOException e)
          {
            //Logging.connectors.warn("IO interruption seen",e);
            throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
          }
          catch (IOException e)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Web: Fetch of robots.txt from "+protocol+"://"+hostIPAddressAndPort+"(host='"+hostName+"') generated IO Exception: "+e.getMessage(),e);
            // This COULD be a transient error, so we are more aggressive about retrying a fetch of robots.txt in that case
            long expirationTime = currentTime + 1000*60*60*6;
            try
            {
              robotsManager.writeRobotsData(hostNameAndPort,expirationTime,null);
            }
            catch (java.net.SocketTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (org.apache.commons.httpclient.ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new LCFException("Interrupted: "+e2.getMessage(),e2,LCFException.INTERRUPTED);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
          }
          finally
          {
            connection.doneFetch(versionActivities);
          }
        }
        finally
        {
          connection.close();
        }

        // Right now we have no choice but to read it again.
        info = robotsManager.checkFetchAllowed(userAgent,hostNameAndPort,currentTime,pathString,versionActivities);
        if (info.booleanValue())
          return RESULTSTATUS_TRUE;
        else
          return RESULTSTATUS_FALSE;
      }
      finally
      {
        versionActivities.completeEventSequence(robotsEventName);
      }
    }
    else
    {
      // Some other thread is reading robots.txt right now, so abort processing of the current document.
      return RESULTSTATUS_NOTYETDETERMINED;
    }
  }

  /** Convert an absolute or relative URL to a document identifier.  This may involve several steps at some point,
  * but right now it does NOT involve converting the host name to a canonical host name.
  * (Doing so would destroy the ability of virtually hosted sites to do the right thing,
  * since the original host name would be lost.)  Thus, we do the conversion to IP address
  * right before we actually fetch the document.
  *@param the identifier of the document in which the raw url was found, or null if none.
  *@return the canonical URL (the document identifier), or null if the url was illegal.
  */
  protected String makeDocumentIdentifier(String parentIdentifier, String rawURL, DocumentURLFilter filter)
    throws LCFException
  {
    try
    {
      java.net.URI url;
      if (parentIdentifier != null)
      {
        java.net.URI parentURL = new java.net.URI(parentIdentifier);
        url = parentURL.resolve(rawURL);
      }
      else
        url = new java.net.URI(rawURL);

      String protocol = url.getScheme();
      String host = url.getHost();

      // The new URL better darn well have a host and a protocol, and we only know how to deal with
      // http and https.
      if (protocol == null || host == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because it has no protocol or host");
        return null;
      }
      if (understoodProtocols.get(protocol) == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because it has an unsupported protocol '"+protocol+"'");
        return null;
      }

      // Canonicalization procedure.
      // The query part of the URL may contain bad parameters (session id's, for instance), or may be ordered in such a
      // way as to prevent an effectively identical URL from being matched.  The anchor part of the URL should also be stripped.
      // This code performs both of these activities in a simple way; rewrites of various pieces may get more involved if we add
      // the ability to perform mappings using criteria specified in the UI.  Right now we don't.
      String id = doCanonicalization(filter,url);
      if (id == null)
        return null;

      // As a last basic legality check, go through looking for illegal characters.
      int i = 0;
      while (i < id.length())
      {
        char x = id.charAt(i++);
        // Only 7-bit ascii is allowed in URLs - and that has limits too (no control characters)
        if (x < ' ' || x > 127)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because it has illegal characters in it");
          return null;
        }
      }

      // Check to be sure the canonicalized URL is in fact one of the ones we want to include
      if (!filter.isDocumentLegal(id))
        return null;

      return id;
    }
    catch (java.net.URISyntaxException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because it is badly formed: "+e.getMessage());
      return null;
    }
    catch (java.lang.IllegalArgumentException e)
    {
      return null;
    }
    catch (java.lang.NullPointerException e)
    {
      // This gets tossed by url.toAsciiString() for reasons I don't understand, but which have to do with a malformed URL.
      return null;
    }
  }

  /** Code to canonicalize a URL.  If URL cannot be canonicalized (and is illegal) return null.
  */
  protected String doCanonicalization(DocumentURLFilter filter, java.net.URI url)
    throws LCFException, java.net.URISyntaxException
  {
    // First, we have to figure out what the canonicalization policy is.
    // To do that, we need to do a regexp match against the COMPLETE raw url.
    // Put it back into the URL without the ref, and with the modified query and path parts.
    String pathString = url.getPath();
    String queryString = url.getRawQuery();

    java.net.URI rawURI = new java.net.URI(url.getScheme(),null,url.getHost(),url.getPort(),pathString,null,null);
    String completeRawURL = rawURI.toASCIIString();

    if (completeRawURL != null && queryString != null && queryString.length() > 0)
    {
      completeRawURL += "?" + queryString;
    }
    CanonicalizationPolicy p;
    if (completeRawURL != null)
      p = filter.getCanonicalizationPolicies().findMatch(completeRawURL);
    else
      p = null;

    // Note well: The java.net.URI class mistreats the query part of the URI, near as I can tell, in the following ways:
    // (1) It decodes the whole thing without regards to the argument interpretation, so the escaped ampersands etc in the arguments are converted
    //     to non-escaped ones (ugh).  This is why I changed the code below to parse the RAW query string and decode it myself.
    // (2) On reassembly of the query string, the class does not properly escape ":", "/", or a bunch of other characters the class description *says*
    //     it will escape.  This means it creates URI's that are illegal according to RFC 2396 - although it is true that RFC 2396 also contains
    //     apparent errors.
    //
    // I've therefore opted to deal with this problem by doing much of the query string processing myself - including its final reassembly into the
    // URI at the end of the processing.
    //

    // To make the url be canonical, we need to strip off everything after the #.  We also need to order the arguments in a canonical
    // way, and remove session identifiers of the types we know about.
    if (queryString != null)
    {
      // Rewrite the query string.  To do this, we first parse it (by looking for ampersands and equal signs), and then
      // we ditch any keys that we really don't want (session identifiers particularly).  Finally, we go through the
      // keys in sorted order and reassemble the query, making sure that any arguments that have the same name
      // appear in the same order.

      // I don't use the 'split' operation because I think it's a lot more oomph (and performance loss) than is needed
      // for this simple parsing task.

      // When reordering a url, the following is done:
      // (1) The individual order of all arguments with the same name is preserved
      // (2) The arguments themselves appear in sorted order, minus any arguments that should be removed because they
      //      are interpreted to be session arguments.
      //
      // When a url is NOT reordered, the following is done:
      // (1) Each argument is examined IN TURN.
      // (2) If the argument is a session argument and should be excluded, it is simply skipped.

      // Canonicalization note: Broadvision
      //
      // The format of Broadvision's urls is as follows:
      // http://blah/path/path?arg|arg|arg|BVSession@@@@=xxxx&more stuff
      // The session identifier is the BVSession@@@@.  In theory I could strip this away, but I've found that
      // most Broadvision sites require session even for basic navigation!  So the lesser of two evils is to leave it
      // in there and suffer the eventual url duplication.

      if (p == null || p.canReorder())
      {
        // Reorder arguments
        HashMap argumentMap = new HashMap();
        int index = 0;
        while (index < queryString.length())
        {
          int newIndex = queryString.indexOf("&",index);
          if (newIndex == -1)
            newIndex = queryString.length();
          String argument = queryString.substring(index,newIndex);
          int valueIndex = argument.indexOf("=");
          String key;
          if (valueIndex == -1)
            key = argument;
          else
            key = argument.substring(0,valueIndex);

          // If this is a disallowed argument, simply don't include it in the final map.
          boolean includeArgument = true;
          if ((p == null || p.canRemovePhpSession()) && key.equals("PHPSESSID"))
            includeArgument = false;
          // For web connector only, null policy means NO broadvision cookie removal!!
          if (p != null && p.canRemoveBvSession() && key.indexOf("BVSession@@@@") != -1)
            includeArgument = false;

          if (includeArgument)
          {
            ArrayList list = (ArrayList)argumentMap.get(key);
            if (list == null)
            {
              list = new ArrayList();
              argumentMap.put(key,list);
            }
            list.add(argument);
          }

          if (newIndex < queryString.length())
            index = newIndex + 1;
          else
            index = newIndex;
        }

        // Reassemble query string in sorted order
        String[] sortArray = new String[argumentMap.size()];
        int i = 0;
        Iterator iter = argumentMap.keySet().iterator();
        while (iter.hasNext())
        {
          sortArray[i++] = (String)iter.next();
        }
        java.util.Arrays.sort(sortArray);

        StringBuffer newString = new StringBuffer();
        boolean isFirst = true;
        i = 0;
        while (i < sortArray.length)
        {
          String key = sortArray[i++];
          ArrayList list = (ArrayList)argumentMap.get(key);
          int j = 0;
          while (j < list.size())
          {
            if (isFirst == false)
            {
              newString.append("&");
            }
            else
              isFirst = false;
            newString.append((String)list.get(j++));
          }
        }
        queryString = newString.toString();
      }
      else
      {
        // Do not reorder!
        StringBuffer newString = new StringBuffer();
        int index = 0;
        boolean isFirst = true;
        while (index < queryString.length())
        {
          int newIndex = queryString.indexOf("&",index);
          if (newIndex == -1)
            newIndex = queryString.length();
          String argument = queryString.substring(index,newIndex);
          int valueIndex = argument.indexOf("=");
          String key;
          if (valueIndex == -1)
            key = argument;
          else
            key = argument.substring(0,valueIndex);

          // If this is a disallowed argument, simply don't include it in the final query.
          boolean includeArgument = true;
          if ((p == null || p.canRemovePhpSession()) && key.equals("PHPSESSID"))
            includeArgument = false;
          // For web connector only, null policy means NO broadvision cookie removal!!
          if (p != null && p.canRemoveBvSession() && key.indexOf("BVSession@@@@") != -1)
            includeArgument = false;

          if (includeArgument)
          {
            if (!isFirst)
              newString.append("&");
            else
              isFirst = false;
            newString.append(argument);
          }

          if (newIndex < queryString.length())
            index = newIndex + 1;
          else
            index = newIndex;
        }
        queryString = newString.toString();
      }
    }

    // Now, rewrite path to get rid of jsessionid etc.
    if (pathString != null)
    {
      int index = pathString.indexOf(";jsessionid=");
      if ((p == null || p.canRemoveJavaSession()) && index != -1)
      {
        // There's a ";jsessionid="
        // Strip the java session id
        pathString = pathString.substring(0,index);
      }
      if ((p == null || p.canRemoveAspSession()) && pathString.startsWith("/s("))
      {
        // It's asp.net
        index = pathString.indexOf(")");
        if (index != -1)
          pathString = pathString.substring(index+1);
      }

    }

    // Put it back into the URL without the ref, and with the modified query and path parts.
    url = new java.net.URI(url.getScheme(),null,url.getHost(),url.getPort(),pathString,null,null);
    String rval = url.toASCIIString();
    // If there's a non-empty query string, append it to the url using our own logic; this is necessary because java.net.URI is broken as far as query escaping
    // goes.
    if (rval != null && queryString != null && queryString.length() > 0)
    {
      rval += "?" + queryString;
    }
    return rval;
  }

  /** Code to check if data is interesting, based on response code and content type.
  */
  protected boolean isContentInteresting(String documentIdentifier, int response, String contentType)
    throws LCFException
  {
    // Additional filtering only done if it's a 200 response
    if (response != 200)
      return true;

    // Look at the content type and decide if it's a kind we want.  This is defined
    // as something we think we can either ingest, or extract links from.

    // For now, we're only going to attempt to extract links from html.  This will change eventually.
    // But the check here is just what the content type is.

    if (contentType == null)
      return false;

    int pos = contentType.indexOf(";");
    if (pos != -1)
      contentType = contentType.substring(0,pos);
    contentType = contentType.trim();

    return interestingMimeTypeMap.get(contentType) != null;
  }

  /** Code to check if an already-fetched document should be ingested.
  */
  protected boolean isDataIngestable(String documentIdentifier)
    throws LCFException
  {
    if (cache.getResponseCode(documentIdentifier) != 200)
      return false;

    // Check if it's a recognized content type
    String contentType = cache.getContentType(documentIdentifier);

    // Some sites have multiple content types.  We just look at the LAST one in that case.
    if (contentType != null)
    {
      String[] contentTypes = contentType.split(",");
      if (contentTypes.length > 0)
        contentType = contentTypes[contentTypes.length-1].trim();
      else
        contentType = null;
    }

    if (contentType == null)
      return false;

    int pos = contentType.indexOf(";");
    if (pos != -1)
      contentType = contentType.substring(0,pos);
    contentType = contentType.trim();

    if (ingestableMimeTypeMap.get(contentType) == null)
      return false;

    // Now, it looks good, but let's be certain by doing fingerprinting.
    // MHL

    return true;
  }

  /** Find a redirection URI, if it exists */
  protected String findRedirectionURI(String currentURI)
    throws LCFException
  {
    FindRedirectionHandler handler = new FindRedirectionHandler(currentURI);
    handleRedirects(currentURI,handler);
    return handler.getTargetURI();
  }

  /** Find matching HTML form data, if present.  Return null if not. */
  protected FormData findHTMLForm(String currentURI, LoginParameters lp)
    throws LCFException
  {
    if (lp == null || lp.getFormNamePattern() == null)
      return null;

    // Use the specified loginParameters to (a) find an appropriate form, if present, and (b) override what the form's default
    // form parameters would be.  This means that the override parameters are associated with the page on which the *form*
    // is found, not the page to which we are submitting the form.  This is unlike (say) Heritrix, which attaches the parameters
    // to the page that's the target of the submission.
    FindHTMLFormHandler handler = new FindHTMLFormHandler(currentURI,lp.getFormNamePattern());
    handleHTML(currentURI,handler);
    // Apply any overrides
    handler.applyFormOverrides(lp);
    return handler.getFormData();
  }

  /** Find a preferred redirection URI, if it exists */
  protected String findPreferredRedirectionURI(String currentURI, LoginParameters lp)
    throws LCFException
  {
    if (lp == null || lp.getPreferredRedirectionPattern() == null)
      return null;

    FindPreferredRedirectionHandler handler = new FindPreferredRedirectionHandler(currentURI,lp.getPreferredRedirectionPattern());
    handleRedirects(currentURI,handler);
    return handler.getTargetURI();
  }

  /** Find HTML link URI, if present, making sure specified preference is matched. */
  protected String findHTMLLinkURI(String currentURI, LoginParameters lp)
    throws LCFException
  {
    if (lp == null || lp.getPreferredLinkPattern() == null)
      return null;

    FindHTMLHrefHandler handler = new FindHTMLHrefHandler(currentURI,lp.getPreferredLinkPattern());
    handleHTML(currentURI,handler);
    return handler.getTargetURI();
  }

  /** This class is the handler for redirection parsing during state transitions */
  protected class FindRedirectionHandler extends FindHandler implements IRedirectionHandler
  {
    public FindRedirectionHandler(String parentURI)
    {
      super(parentURI);
    }

  }

  /** This class is the handler for redirection handling during state transitions */
  protected class FindPreferredRedirectionHandler extends FindHandler implements IRedirectionHandler
  {
    protected Pattern redirectionURIPattern;

    public FindPreferredRedirectionHandler(String parentURI, Pattern redirectionURIPattern)
    {
      super(parentURI);
      this.redirectionURIPattern = redirectionURIPattern;
    }

    /** Override noteDiscoveredLink */
    public void noteDiscoveredLink(String rawURL)
      throws LCFException
    {
      if (targetURI == null)
      {
        Logging.connectors.debug("WEB: Tried to match raw url '"+rawURL+"'");
        super.noteDiscoveredLink(rawURL);
        if (targetURI != null)
        {
          Logging.connectors.debug("WEB: Tried to match cooked url '"+targetURI+"'");
          // Is this a form element we can use?
          boolean canUse;
          if (redirectionURIPattern != null)
          {
            Matcher m = redirectionURIPattern.matcher(targetURI);
            canUse = m.find();
            Logging.connectors.debug("WEB: Redirection link lookup "+((canUse)?"matched":"didn't match")+" '"+targetURI+"'");
          }
          else
          {
            Logging.connectors.debug("WEB: Redirection link lookup for '"+targetURI+"' had no pattern to match");
            canUse = true;
          }
          if (!canUse)
            targetURI = null;
        }
      }
    }
  }

  /** This class is the handler for HTML form parsing during state transitions */
  protected class FindHTMLFormHandler extends FindHandler implements IHTMLHandler
  {
    protected Pattern formNamePattern;
    protected FormDataAccumulator discoveredFormData = null;
    protected FormDataAccumulator currentFormData = null;

    public FindHTMLFormHandler(String parentURI, Pattern formNamePattern)
    {
      super(parentURI);
      this.formNamePattern = formNamePattern;
    }

    public void applyFormOverrides(LoginParameters lp)
    {
      if (discoveredFormData != null && lp != null)
        discoveredFormData.applyOverrides(lp);
    }

    public FormData getFormData()
    {
      return discoveredFormData;
    }

    /** Note the start of a form */
    public void noteFormStart(Map formAttributes)
      throws LCFException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: Saw form with name "+((formAttributes.get("name")==null)?"null":"'"+formAttributes.get("name")+"'"));

      // Is this a form element we can use?
      boolean canUse;
      if (formNamePattern != null)
      {
        String formName = (String)formAttributes.get("name");
        if (formName == null)
          formName = "";

        Matcher m = formNamePattern.matcher(formName);
        canUse = m.find();
      }
      else
        canUse = true;

      if (canUse)
      {
        String actionURI = (String)formAttributes.get("action");
        if (actionURI == null)
          // Action URI is THIS uri!
          actionURI = parentURI;
        else if (actionURI.length() == 0)
          actionURI = "";
        noteDiscoveredLink(actionURI);
        actionURI = getTargetURI();
        if (actionURI != null)
        {
          String method = (String)formAttributes.get("method");
          if (method == null || method.length() == 0)
            method = "get";
          else
            method = method.toLowerCase();

          // Start a new form
          currentFormData = new FormDataAccumulator(actionURI,method.equals("post")?FormData.SUBMITMETHOD_POST:FormData.SUBMITMETHOD_GET);

        }
      }
    }

    /** Note an input tag */
    public void noteFormInput(Map inputAttributes)
      throws LCFException
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: Saw form element of type '"+inputAttributes.get("type")+"' name '"+inputAttributes.get("name")+"'");
      if (currentFormData != null)
        currentFormData.addElement(inputAttributes);
    }

    /** Note the end of a form */
    public void noteFormEnd()
      throws LCFException
    {
      if (currentFormData != null)
      {
        discoveredFormData = currentFormData;
        currentFormData = null;
      }
    }

    /** Note discovered href */
    public void noteAHREF(String rawURL)
      throws LCFException
    {
    }

    /** Note discovered href */
    public void noteLINKHREF(String rawURL)
      throws LCFException
    {
    }

    /** Note discovered IMG SRC */
    public void noteIMGSRC(String rawURL)
      throws LCFException
    {
    }

    /** Note discovered FRAME SRC */
    public void noteFRAMESRC(String rawURL)
      throws LCFException
    {
    }

  }

  /** This class accumulates form data and allows overrides */
  protected static class FormDataAccumulator implements FormData
  {
    // Note well: We don't handle multipart posts at this time!!

    /** The form's action URI */
    protected String actionURI;
    /** The form's submit method */
    protected int submitMethod;

    /** The set of elements */
    protected ArrayList elementList = new ArrayList();

    public FormDataAccumulator(String actionURI, int submitMethod)
    {
      this.actionURI = actionURI;
      this.submitMethod = submitMethod;
    }

    public void addElement(Map attributes)
    {
      // Interpret the input tag, and make a list of the potential elements we'll want to submit
      String type = (String)attributes.get("type");
      if (type != null)
      {
        String name = (String)attributes.get("name");
        if (name != null)
        {
          String lowerType = type.toLowerCase();
          if (lowerType.equals("submit"))
          {
            String value = (String)attributes.get("value");
            if (value == null)
              value = "Submit Form";
            elementList.add(new FormItem(name,value,ELEMENTCATEGORY_FREEFORM,true));
          }
          else if (lowerType.equals("hidden") || lowerType.equals("text") || lowerType.equals("password"))
          {
            String value = (String)attributes.get("value");
            if (value == null)
              value = "";
            elementList.add(new FormItem(name,value,ELEMENTCATEGORY_FREEFORM,true));
          }
          else if (lowerType.equals("select"))
          {
            String value = (String)attributes.get("value");
            if (value == null)
              value = "";
            String selected = (String)attributes.get("selected");
            boolean isSelected = false;
            if (selected != null)
              isSelected = true;
            String multiple = (String)attributes.get("multiple");
            boolean isMultiple = false;
            if (multiple != null)
              isMultiple = true;
            elementList.add(new FormItem(name,value,isMultiple?ELEMENTCATEGORY_FIXEDINCLUSIVE:ELEMENTCATEGORY_FIXEDEXCLUSIVE,isSelected));
          }
          else if (lowerType.equals("radio"))
          {
            String value = (String)attributes.get("value");
            if (value == null)
              value = "";
            String selected = (String)attributes.get("checked");
            boolean isSelected = false;
            if (selected != null)
              isSelected = true;
            elementList.add(new FormItem(name,value,ELEMENTCATEGORY_FIXEDEXCLUSIVE,isSelected));
          }
          else if (lowerType.equals("checkbox"))
          {
            String value = (String)attributes.get("value");
            if (value == null)
              value = "";
            String selected = (String)attributes.get("checked");
            boolean isSelected = false;
            if (selected != null)
              isSelected = true;
            elementList.add(new FormItem(name,value,ELEMENTCATEGORY_FIXEDINCLUSIVE,isSelected));
          }
          else if (lowerType.equals("textarea"))
          {
            elementList.add(new FormItem(name,"",ELEMENTCATEGORY_FREEFORM,true));
          }
        }
      }
    }

    public void applyOverrides(LoginParameters lp)
    {
      // This map contains the control names we have ALREADY wiped clean.
      Map overrideMap = new HashMap();

      // Override the specified elements with the specified values
      int i = 0;
      while (i < lp.getParameterCount())
      {
        Pattern namePattern = lp.getParameterNamePattern(i);
        String value = lp.getParameterValue(i);
        i++;

        // For each parameter specified, go through the element list and do the right thing.  This will require us to keep some state around about
        // what exactly we've done to the element list so far, so that each parameter rule in turn applies properly.
        //
        // Each rule regular expression will be deemed to apply to all matching controls.  If the rule matches the control name, then the precise behavior
        // will depend on the type of the control.
        //
        // Controls can be categorized in the following way:
        // - free-form value
        // - specified exclusive value (e.g. radio button)
        // - specified inclusive value (e.g. checkbox)
        //
        // For free-form values, the value given will simply override the value of the element.
        // For exclusive controls, all values in the family will be disabled, and the value matching the one specified will be enabled.
        // For inclusive controls, all values in the family will be cleared ONCE, and then subsequently the value matching the one specified will be enabled.
        //
        int j = 0;
        while (j < elementList.size())
        {
          FormItem fi = (FormItem)elementList.get(j++);
          Matcher m = namePattern.matcher(fi.getElementName());
          if (m.find())
          {
            // Hey, it seems to apply!
            switch (fi.getType())
            {
            case ELEMENTCATEGORY_FREEFORM:
              // Override immediately
              fi.setValue(value);
              break;
            case ELEMENTCATEGORY_FIXEDEXCLUSIVE:
              // If it doesn't match the value, disable.
              fi.setEnabled(fi.getElementValue().equals(value));
              break;
            case ELEMENTCATEGORY_FIXEDINCLUSIVE:
              // Make sure we clear the entire control ONCE (and only once).
              if (overrideMap.get(fi.getElementName()) == null)
              {
                // Zip through the entire list
                int k = 0;
                while (k < elementList.size())
                {
                  FormItem fi2 = (FormItem)elementList.get(k++);
                  if (fi2.getElementName().equals(fi.getElementName()))
                    fi.setEnabled(false);
                }
                overrideMap.put(fi.getElementName(),fi.getElementName());
              }
              if (fi.getElementValue().equals(value))
                fi.setEnabled(true);
            default:
              break;
            }
          }
        }
      }
    }

    /** Get the full action URI for this form. */
    public String getActionURI()
    {
      return actionURI;
    }

    /** Get the submit method for this form. */
    public int getSubmitMethod()
    {
      return submitMethod;
    }

    /** Iterate over the active form data elements.  The returned iterator returns FormDataElement objects. */
    public Iterator getElementIterator()
    {
      return new FormItemIterator(elementList);
    }

  }

  /** Iterator over FormItems */
  protected static class FormItemIterator implements Iterator
  {
    protected ArrayList elementList;
    protected int currentIndex = 0;

    public FormItemIterator(ArrayList elementList)
    {
      this.elementList = elementList;
    }

    public boolean hasNext()
    {
      while (true)
      {
        if (currentIndex == elementList.size())
          return false;
        if (((FormItem)elementList.get(currentIndex)).getEnabled() == false)
          currentIndex++;
        else
          break;
      }
      return true;
    }

    public Object next()
    {
      while (true)
      {
        if (currentIndex == elementList.size())
          throw new NoSuchElementException("No such element");
        if (((FormItem)elementList.get(currentIndex)).getEnabled() == false)
          currentIndex++;
        else
          break;
      }
      return elementList.get(currentIndex++);
    }

    public void remove()
    {
      throw new UnsupportedOperationException("Unsupported operation");
    }
  }

  // Element categorization
  protected final static int ELEMENTCATEGORY_FREEFORM = 0;
  protected final static int ELEMENTCATEGORY_FIXEDEXCLUSIVE = 1;
  protected final static int ELEMENTCATEGORY_FIXEDINCLUSIVE = 2;

  /** This class provides an individual data item */
  protected static class FormItem implements FormDataElement
  {
    protected String name;
    protected String value;
    protected boolean isEnabled;
    protected int type;

    public FormItem(String name, String value, int type, boolean isEnabled)
    {
      this.name = name;
      this.value = value;
      this.isEnabled = isEnabled;
      this.type = type;
    }

    public void setEnabled(boolean enabled)
    {
      isEnabled = enabled;
    }

    public boolean getEnabled()
    {
      return isEnabled;
    }

    public void setValue(String value)
    {
      this.value = value;
    }

    public int getType()
    {
      return type;
    }

    /** Get the element name */
    public String getElementName()
    {
      return name;
    }

    /** Get the element value */
    public String getElementValue()
    {
      return value;
    }

  }

  /** This class is the handler for HTML parsing during state transitions */
  protected class FindHTMLHrefHandler extends FindHandler implements IHTMLHandler
  {
    protected Pattern preferredLinkPattern;

    public FindHTMLHrefHandler(String parentURI, Pattern preferredLinkPattern)
    {
      super(parentURI);
      this.preferredLinkPattern = preferredLinkPattern;
    }

    /** Note the start of a form */
    public void noteFormStart(Map formAttributes)
      throws LCFException
    {
    }

    /** Note an input tag */
    public void noteFormInput(Map inputAttributes)
      throws LCFException
    {
    }

    /** Note the end of a form */
    public void noteFormEnd()
      throws LCFException
    {
    }

    /** Override noteDiscoveredLink */
    public void noteDiscoveredLink(String rawURL)
      throws LCFException
    {
      if (targetURI == null)
      {
        Logging.connectors.debug("WEB: Tried to match raw url '"+rawURL+"'");
        super.noteDiscoveredLink(rawURL);
        if (targetURI != null)
        {
          Logging.connectors.debug("WEB: Tried to match cooked url '"+targetURI+"'");
          // Is this a form element we can use?
          boolean canUse;
          if (preferredLinkPattern != null)
          {
            Matcher m = preferredLinkPattern.matcher(targetURI);
            canUse = m.find();
            Logging.connectors.debug("WEB: Preferred link lookup "+((canUse)?"matched":"didn't match")+" '"+targetURI+"'");
          }
          else
          {
            Logging.connectors.debug("WEB: Preferred link lookup for '"+targetURI+"' had no pattern to match");
            canUse = true;
          }
          if (!canUse)
            targetURI = null;
        }
      }
    }

    /** Note discovered href */
    public void noteAHREF(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

    /** Note discovered href */
    public void noteLINKHREF(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

    /** Note discovered IMG SRC */
    public void noteIMGSRC(String rawURL)
      throws LCFException
    {
    }

    /** Note discovered FRAME SRC */
    public void noteFRAMESRC(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

  }

  /** This class is used to discover links in a session login context */
  protected class FindHandler implements IDiscoveredLinkHandler
  {
    protected String parentURI;
    protected String targetURI = null;

    public FindHandler(String parentURI)
    {
      this.parentURI = parentURI;
    }

    /** Inform the world of a discovered link.
    *@param rawURL is the raw discovered url.  This may be relative, malformed, or otherwise unsuitable for use until final form is acheived.
    */
    public void noteDiscoveredLink(String rawURL)
      throws LCFException
    {
      // Build a complete url, but don't filter or anything
      try
      {
        java.net.URI url;
        if (parentURI != null)
        {
          java.net.URI parentURL = new java.net.URI(parentURI);
          url = parentURL.resolve(rawURL);
        }
        else
          url = new java.net.URI(rawURL);

        String protocol = url.getScheme();
        String host = url.getHost();

        // The new URL better darn well have a host and a protocol, and we only know how to deal with
        // http and https.
        if (protocol == null || host == null)
        {
          return;
        }
        if (understoodProtocols.get(protocol) == null)
        {
          return;
        }

        String id = url.toASCIIString();
        if (id == null)
          return;

        // As a last basic legality check, go through looking for illegal characters.
        int i = 0;
        while (i < id.length())
        {
          char x = id.charAt(i++);
          // Only 7-bit ascii is allowed in URLs - and that has limits too (no control characters)
          if (x < ' ' || x > 127)
          {
            return;
          }
        }

        // Set the target.
        targetURI = id;
      }
      catch (java.net.URISyntaxException e)
      {
        return;
      }
      catch (java.lang.IllegalArgumentException e)
      {
        return;
      }
      catch (java.lang.NullPointerException e)
      {
        // This gets tossed by url.toAsciiString() for reasons I don't understand, but which have to do with a malformed URL.
        return;
      }
    }

    public String getTargetURI()
    {
      return targetURI;
    }
  }


  /** Code to extract links from an already-fetched document. */
  protected void extractLinks(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    throws LCFException, ServiceInterruption
  {
    handleRedirects(documentIdentifier,new ProcessActivityRedirectionHandler(documentIdentifier,activities,filter));
    // For html, we don't want any actions, because we don't do form submission.
    handleHTML(documentIdentifier,new ProcessActivityHTMLHandler(documentIdentifier,activities,filter));
    handleXML(documentIdentifier,new ProcessActivityXMLHandler(documentIdentifier,activities,filter));
    // May add more later for other extraction tasks.
  }

  /** This class is the handler for links that get added into a IProcessActivity object.
  */
  protected class ProcessActivityLinkHandler implements IDiscoveredLinkHandler
  {
    protected String documentIdentifier;
    protected IProcessActivity activities;
    protected DocumentURLFilter filter;
    protected String contextDescription;
    protected String linkType;

    /** Constructor. */
    public ProcessActivityLinkHandler(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter, String contextDescription, String linkType)
    {
      this.documentIdentifier = documentIdentifier;
      this.activities = activities;
      this.filter = filter;
      this.contextDescription = contextDescription;
      this.linkType = linkType;
    }

    /** Inform the world of a discovered link.
    *@param rawURL is the raw discovered url.  This may be relative, malformed, or otherwise unsuitable for use until final form is acheived.
    */
    public void noteDiscoveredLink(String rawURL)
      throws LCFException
    {
      String newIdentifier = makeDocumentIdentifier(documentIdentifier,rawURL,filter);
      if (newIdentifier != null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: In "+contextDescription+" document '"+documentIdentifier+"', found link to '"+newIdentifier+"'");
        activities.addDocumentReference(newIdentifier,documentIdentifier,linkType,null,null,null,calculateDocumentEvents(activities,newIdentifier));
      }
      else
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: In "+contextDescription+" document '"+documentIdentifier+"', found an unincluded URL '"+rawURL+"'");
      }
    }

  }

  /** Class that describes redirection handling */
  protected class ProcessActivityRedirectionHandler extends ProcessActivityLinkHandler implements IRedirectionHandler
  {
    /** Constructor. */
    public ProcessActivityRedirectionHandler(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    {
      super(documentIdentifier,activities,filter,"redirection",REL_REDIRECT);
    }
  }

  /** Class that describes HTML handling */
  protected class ProcessActivityHTMLHandler extends ProcessActivityLinkHandler implements IHTMLHandler
  {
    /** Constructor. */
    public ProcessActivityHTMLHandler(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    {
      super(documentIdentifier,activities,filter,"html",REL_LINK);
    }

    /** Note the start of a form */
    public void noteFormStart(Map formAttributes)
      throws LCFException
    {
    }

    /** Note an input tag */
    public void noteFormInput(Map inputAttributes)
      throws LCFException
    {
    }

    /** Note the end of a form */
    public void noteFormEnd()
      throws LCFException
    {
    }

    /** Note discovered href */
    public void noteAHREF(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

    /** Note discovered href */
    public void noteLINKHREF(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

    /** Note discovered IMG SRC */
    public void noteIMGSRC(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

    /** Note discovered FRAME SRC */
    public void noteFRAMESRC(String rawURL)
      throws LCFException
    {
      noteDiscoveredLink(rawURL);
    }

  }

  /** Class that describes XML handling */
  protected class ProcessActivityXMLHandler extends ProcessActivityLinkHandler implements IXMLHandler
  {
    /** Constructor. */
    public ProcessActivityXMLHandler(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    {
      super(documentIdentifier,activities,filter,"xml",REL_LINK);
    }

    /** Inform the world of a discovered ttl value.
    *@param rawTtlValue is the raw discovered ttl value.  Null indicates we should set the default.
    */
    public void noteDiscoveredTtlValue(String rawTtlValue)
      throws LCFException
    {
      long currentTime = System.currentTimeMillis();
      Long rescanTime = null;
      if (rawTtlValue != null)
      {
        try
        {
          int minutes = Integer.parseInt(rawTtlValue);
          long nextTime = currentTime + minutes * 60000L;
          rescanTime = new Long(nextTime);
          // Set the upper bound time; we want to scan the feeds aggressively.
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: In "+contextDescription+" document '"+documentIdentifier+"', found a ttl value of "+rawTtlValue+"; setting refetch time accordingly");
        }
        catch (NumberFormatException e)
        {
          Logging.connectors.warn("WEB: In "+contextDescription+" document '"+documentIdentifier+"' found illegal ttl value '"+rawTtlValue+"': "+e.getMessage(),e);
        }
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: In "+contextDescription+" document '"+documentIdentifier+"' setting rescan time to "+((rescanTime==null)?"null":rescanTime.toString()));

      activities.setDocumentScheduleBounds(documentIdentifier,rescanTime,rescanTime,null,null);
    }

  }

  /** Handle extracting the redirect link from a redirect response. */
  protected void handleRedirects(String documentURI, IRedirectionHandler handler)
    throws LCFException
  {
    int responseCode = cache.getResponseCode(documentURI);
    if (responseCode == 302 || responseCode == 301)
    {
      // Figure out the redirect
      String referralURI = cache.getReferralURI(documentURI);
      if (referralURI == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: The document '"+documentURI+"' is a redirection, but there was no URI header!");
        return;
      }
      int index = referralURI.indexOf(" ");
      if (index != -1)
        referralURI = referralURI.substring(0,index);
      handler.noteDiscoveredLink(referralURI);
    }
  }

  /** Handle document references from XML.  Right now we only understand RSS. */
  protected void handleXML(String documentURI, IXMLHandler handler)
    throws LCFException, ServiceInterruption
  {
    try
    {
      int responseCode = cache.getResponseCode(documentURI);
      if (responseCode != 200)
        return;

      // We ONLY look for XML if the content type *says* it is XML.
      String contentType = cache.getContentType(documentURI);
      // Some sites have multiple content types.  We just look at the LAST one in that case.
      if (contentType != null)
      {
        String[] contentTypes = contentType.split(",");
        if (contentTypes.length > 0)
          contentType = contentTypes[contentTypes.length-1].trim();
        else
          contentType = null;
      }
      if (contentType == null)
        return;

      int semiIndex = contentType.indexOf(";");
      String suffix = null;
      if (semiIndex != -1)
      {
        suffix = contentType.substring(semiIndex+1);
        contentType = contentType.substring(0,semiIndex);
      }
      contentType = contentType.trim();
      boolean isXML =
        contentType.equals("text/xml") ||
        contentType.equals("application/rss+xml") ||
        contentType.equals("application/xml") ||
        contentType.equals("application/atom+xml") ||
        contentType.equals("application/xhtml+xml") ||
        contentType.equals("text/XML") ||
        contentType.equals("application/rdf+xml") ||
        contentType.equals("text/application") ||
        contentType.equals("XML");

      if (!isXML)
        return;

      // OK, it's XML.  Now what?  Well, we get the encoding, and we verify that it is text, then we try to get links
      // from it presuming it is an RSS feed.

      String encoding = "utf-8";
      if (suffix != null)
      {
        suffix = suffix.trim();
        if (suffix.startsWith("charset="))
          encoding = suffix.substring("charset=".length());
      }

      InputStream is = cache.getData(documentURI);
      if (is == null)
      {
        Logging.connectors.error("WEB: Document '"+documentURI+"' should be in cache but isn't");
        return;
      }
      try
      {
        // Parse the document.  This will cause various things to occur, within the instantiated XMLContext class.
        XMLStream x = new XMLStream();
        OuterContextClass c = new OuterContextClass(x,documentURI,handler);
        x.setContext(c);
        try
        {
          x.parse(is);
          c.checkIfValidFeed();
        }
        finally
        {
          x.cleanup();
        }
      }
      catch (LCFException e)
      {
        // Ignore XML parsing errors.  These should probably have their own error code, but that requires a core change.
        if (e.getMessage().indexOf("pars") >= 0)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: XML document '"+documentURI+"' was unparseable ("+e.getMessage()+"), skipping");
          return;
        }
        throw e;
      }
      finally
      {
        is.close();
      }
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new LCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
    {
      throw new LCFException("Socket connect timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      //Logging.connectors.warn("IO interruption seen",e);

      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException("IO error: "+e.getMessage(),e);
    }
  }

  /** This class handles the outermost XML context for the feed document. */
  protected class OuterContextClass extends XMLContext
  {
    /** Keep track of the number of valid feed signals we saw */
    protected int outerTagCount = 0;
    /** The document uri */
    protected String documentURI;
    /** The link handler */
    protected IXMLHandler handler;

    public OuterContextClass(XMLStream theStream, String documentURI, IXMLHandler handler)
    {
      super(theStream);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    /** Check if feed was valid */
    public void checkIfValidFeed()
    {
      if (outerTagCount == 0)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: XML document '"+documentURI+"' does not have rss, feed, or rdf:RDF tag - not valid feed");
      }
    }

    /** Handle the tag beginning to set the correct second-level parsing context */
    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      if (qName.equals("rss"))
      {
        // RSS feed detected
        outerTagCount++;
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Parsed bottom-level XML for RSS document '"+documentURI+"'");
        return new RSSContextClass(theStream,namespaceURI,localName,qName,atts,documentURI,handler);
      }
      else if (qName.equals("rdf:RDF"))
      {
        // RDF/Atom feed detected
        outerTagCount++;
        return new RDFContextClass(theStream,namespaceURI,localName,qName,atts,documentURI,handler);
      }
      else if (qName.equals("feed"))
      {
        // Basic feed detected
        outerTagCount++;
        return new FeedContextClass(theStream,namespaceURI,localName,qName,atts,documentURI,handler);
      }

      // The default action is to establish a new default context.
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    /** Handle the tag ending */
    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext context = theStream.getContext();
      String tagName = context.getQname();
      if (tagName.equals("rdf:RDF"))
      {
        ((RDFContextClass)context).process();
      }
      else if (tagName.equals("feed"))
      {
        ((FeedContextClass)context).process();
      }
      else
        super.endTag();
    }

  }

  protected class RSSContextClass extends XMLContext
  {
    /** The document identifier */
    protected String documentURI;
    /** Link notification interface */
    protected IXMLHandler handler;

    public RSSContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // Handle each channel
      if (qName.equals("channel"))
      {
        // Channel detected
        return new RSSChannelContextClass(theStream,namespaceURI,localName,qName,atts,documentURI,handler);
      }

      // Skip everything else.
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      // If it's our channel tag, process global channel information
      XMLContext context = theStream.getContext();
      String tagName = context.getQname();
      if (tagName.equals("channel"))
      {
        ((RSSChannelContextClass)context).process();
      }
      else
        super.endTag();
    }
  }

  protected class RSSChannelContextClass extends XMLContext
  {
    /** The document identifier */
    protected String documentURI;
    /** Link handler */
    protected IXMLHandler handler;

    /** TTL value is set on a per-channel basis */
    protected String ttlValue = null;

    public RSSChannelContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else if (qName.equals("item"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new RSSItemContextClass(theStream,namespaceURI,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringContext)theContext).getValue();
      else if (theTag.equals("item"))
      {
        // It's an item.
        RSSItemContextClass itemContext = (RSSItemContextClass)theContext;
        // Presumably, since we are done parsing, we've recorded all the information we need in the context, object including:
        // (1) File name (if any), containing dechromed content
        // (2) Link name(s)
        // (3) Pubdate
        // (4) Title
        // The job now is to pull this info out and call the activities interface appropriately.

        // NOTE: After this endTag() method is called, tagCleanup() will be called for the item context.  This should clean up
        // all dangling files etc. that need to be removed.
        // If an exception or error is thrown during the parse, this endTag() method will NOT be called, but the tagCleanup()
        // method will be called regardless.
        itemContext.process(handler);
      }
      else
        super.endTag();
    }

    /** Process this data */
    protected void process()
      throws LCFException
    {
      // Deal with the ttlvalue, if it was found
      // Use the ttl value as a signal for when we ought to look at this feed again.  If not present, use the default.
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class RSSItemContextClass extends XMLContext
  {
    protected String guidField = null;
    protected String linkField = null;

    public RSSItemContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("link"))
      {
        // "link" tag
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else if (qName.equals("guid"))
      {
        // "guid" tag
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespaceURI,localName,qName,atts);
      }
    }

    /** Convert the individual sub-fields of the item context into their final forms */
    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("link"))
      {
        linkField = ((XMLStringContext)theContext).getValue();
      }
      else if (theTag.equals("guid"))
      {
        guidField = ((XMLStringContext)theContext).getValue();
      }
      else
      {
        super.endTag();
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws LCFException
    {
      if (linkField == null || linkField.length() == 0)
        linkField = guidField;

      if (linkField != null && linkField.length() > 0)
      {
        String[] links = linkField.split(", ");
        int l = 0;
        while (l < links.length)
        {
          String rawURL = links[l++].trim();
          // Process the link
          handler.noteDiscoveredLink(rawURL);
        }
      }
    }
  }

  protected class RDFContextClass extends XMLContext
  {
    /** The document identifier */
    protected String documentURI;
    /** XML handler */
    protected IXMLHandler handler;

    /** ttl value */
    protected String ttlValue = null;

    public RDFContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else if (qName.equals("item"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new RDFItemContextClass(theStream,namespaceURI,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringContext)theContext).getValue();
      else if (theTag.equals("item"))
      {
        // It's an item.
        RDFItemContextClass itemContext = (RDFItemContextClass)theContext;
        // Presumably, since we are done parsing, we've recorded all the information we need in the context, object including:
        // (1) Link name(s)
        // The job now is to pull this info out and call the activities interface appropriately.

        // NOTE: After this endTag() method is called, tagCleanup() will be called for the item context.  This should clean up
        // all dangling files etc. that need to be removed.
        // If an exception or error is thrown during the parse, this endTag() method will NOT be called, but the tagCleanup()
        // method will be called regardless.
        itemContext.process(handler);
      }
      else
        super.endTag();
    }

    /** Process this data */
    protected void process()
      throws LCFException
    {
      // Deal with the ttlvalue, if it was found
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class RDFItemContextClass extends XMLContext
  {
    protected String linkField = null;

    public RDFItemContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("link"))
      {
        // "link" tag
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespaceURI,localName,qName,atts);
      }
    }

    /** Convert the individual sub-fields of the item context into their final forms */
    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("link"))
      {
        linkField = ((XMLStringContext)theContext).getValue();
      }
      else
      {
        super.endTag();
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws LCFException
    {
      if (linkField != null && linkField.length() > 0)
      {
        String[] links = linkField.split(", ");
        int l = 0;
        while (l < links.length)
        {
          String rawURL = links[l++].trim();
          // Process the link
          handler.noteDiscoveredLink(rawURL);
        }
      }
    }
  }

  protected class FeedContextClass extends XMLContext
  {
    /** The document identifier */
    protected String documentURI;
    /** XML handler */
    protected IXMLHandler handler;

    /** ttl value */
    protected String ttlValue = null;

    public FeedContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespaceURI,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringContext(theStream,namespaceURI,localName,qName,atts);
      }
      else if (qName.equals("entry"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new FeedItemContextClass(theStream,namespaceURI,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespaceURI,localName,qName,atts);
    }

    protected void endTag()
      throws LCFException, ServiceInterruption
    {
      XMLContext theContext = theStream.getContext();
      String theTag = theContext.getQname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringContext)theContext).getValue();
      else if (theTag.equals("entry"))
      {
        // It's an item.
        FeedItemContextClass itemContext = (FeedItemContextClass)theContext;
        // Presumably, since we are done parsing, we've recorded all the information we need in the context, object including:
        // (1) Link name(s)
        // The job now is to pull this info out and call the activities interface appropriately.

        // NOTE: After this endTag() method is called, tagCleanup() will be called for the item context.  This should clean up
        // all dangling files etc. that need to be removed.
        // If an exception or error is thrown during the parse, this endTag() method will NOT be called, but the tagCleanup()
        // method will be called regardless.
        itemContext.process(handler);
      }
      else
        super.endTag();
    }

    /** Process this data */
    protected void process()
      throws LCFException
    {
      // Deal with the ttlvalue, if it was found
      // Use the ttl value as a signal for when we ought to look at this feed again.  If not present, use the default.
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class FeedItemContextClass extends XMLContext
  {
    protected String linkField = null;

    public FeedItemContextClass(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
    {
      super(theStream,namespaceURI,localName,qName,atts);
    }

    protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
      throws LCFException, ServiceInterruption
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (qName.equals("link"))
      {
        // "link" tag
        linkField = atts.getValue("href");
        return super.beginTag(namespaceURI,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespaceURI,localName,qName,atts);
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws LCFException
    {
      if (linkField != null && linkField.length() > 0)
      {
        String[] links = linkField.split(", ");
        int l = 0;
        while (l < links.length)
        {
          String rawURL = links[l++].trim();
          // Process the link
          handler.noteDiscoveredLink(rawURL);
        }
      }
    }
  }

  /** Handle document references from HTML */
  protected void handleHTML(String documentURI, IHTMLHandler handler)
    throws LCFException
  {
    int responseCode = cache.getResponseCode(documentURI);
    if (responseCode != 200)
      return;
    try
    {
      // We'll check first to see if this is textual - and then we'll try to find html links in it.
      if (!isDocumentText(documentURI))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Document '"+documentURI+"' is not text; can't extract links");
        return;
      }
      // Grab the content-type so we know how to decode.
      String encoding = "utf-8";
      String contentType = cache.getContentType(documentURI);
      // Some sites have multiple content types.  We just look at the LAST one in that case.
      if (contentType != null)
      {
        String[] contentTypes = contentType.split(",");
        if (contentTypes.length > 0)
          contentType = contentTypes[contentTypes.length-1].trim();
        else
          contentType = null;
      }

      if (contentType != null)
      {
        int pos = contentType.indexOf(";");
        if (pos != -1)
        {
          contentType = contentType.substring(pos+1).trim();
          if (contentType.startsWith("charset="))
          {
            encoding = contentType.substring("charset=".length());
          }
        }
      }

      // Search for A HREF tags in the document stream.  This is brain-dead link location
      InputStream is = cache.getData(documentURI);
      if (is == null)
      {
        Logging.connectors.error("WEB: Document '"+documentURI+"' should be in cache but isn't");
        return;
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: Document '"+documentURI+"' is text, with encoding '"+encoding+"'; link extraction starting");

      try
      {
        // Create a reader for the described encoding, if that's possible
        Reader r = new InputStreamReader(is,encoding);
        try
        {
          // We read characters at a time, understanding the basic form of html.
          // This code represents a basic bottom-up parser, which is the best thing since we really don't want to code up all the context we'd need
          // to do a top-down parse.  So, there is a parse state, and the code walks through the document recognizing symbols and modifying the state.

          FormParseState currentParseState = new FormParseState(handler);
          while (true)
          {
            int x = r.read();
            if (x == -1)
              break;
            currentParseState.dealWithCharacter((char)x);
          }
          currentParseState.finishUp();
        }
        finally
        {
          r.close();
        }
      }
      catch (UnsupportedEncodingException e)
      {
        // The encoding specified was crap, so don't handle this document.
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Document '"+documentURI+"' had an unrecognized encoding '"+encoding+"'");
        return;
      }
      finally
      {
        is.close();
      }
    }
    catch (SocketTimeoutException e)
    {
      throw new LCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
    {
      throw new LCFException("Socket connect timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      //Logging.connectors.warn("IO interruption seen",e);

      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException("IO error: "+e.getMessage(),e);
    }
  }

  /** Is the document text, as far as we can tell? */
  protected boolean isDocumentText(String documentURI)
    throws LCFException
  {
    try
    {
      // Look at the first 4K
      byte[] byteBuffer = new byte[4096];
      int amt;

      // Open file for reading.
      InputStream is = cache.getData(documentURI);
      if (is == null)
        return false;
      try
      {
        amt = 0;
        while (amt < byteBuffer.length)
        {
          int incr = is.read(byteBuffer,amt,byteBuffer.length-amt);
          if (incr == -1)
            break;
          amt += incr;
        }
      }
      finally
      {
        is.close();
      }

      if (amt == 0)
        return false;

      return isText(byteBuffer,amt);
    }
    catch (SocketTimeoutException e)
    {
      throw new LCFException("Socket timeout exception accessing cached document: "+e.getMessage(),e);
    }
    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
    {
      throw new LCFException("Socket timeout exception accessing cached document: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException("IO exception accessing cached document: "+e.getMessage(),e);
    }
  }

  /** Test to see if a document is text or not.  The first n bytes are passed
  * in, and this code returns "true" if it thinks they represent text.  The code
  * has been lifted algorithmically from products/Sharecrawler/Fingerprinter.pas,
  * which was based on "perldoc -f -T".
  */
  protected static boolean isText(byte[] beginChunk, int chunkLength)
  {
    if (chunkLength == 0)
      return true;
    int i = 0;
    int count = 0;
    while (i < chunkLength)
    {
      byte x = beginChunk[i++];
      if (x == 0)
        return false;
      if (isStrange(x))
        count++;
    }
    return ((double)count)/((double)chunkLength) < 0.30;
  }

  /** Check if character is not typical ASCII. */
  protected static boolean isStrange(byte x)
  {
    return (x > 127 || x < 32) && (!isWhiteSpace(x));
  }

  /** Check if a byte is a whitespace character. */
  protected static boolean isWhiteSpace(byte x)
  {
    return (x == 0x09 || x == 0x0a || x == 0x0d || x == 0x20);
  }

  /** Read a string as a sequence of individual expressions, urls, etc.
  */
  protected static ArrayList stringToArray(String input)
  {
    ArrayList list = new ArrayList();
    try
    {
      java.io.Reader str = new java.io.StringReader(input);
      try
      {
        java.io.BufferedReader is = new java.io.BufferedReader(str);
        try
        {
          while (true)
          {
            String nextString = is.readLine();
            if (nextString == null)
              break;
            if (nextString.length() == 0)
              continue;
            nextString.trim();
            if (nextString.startsWith("#"))
              continue;
            list.add(nextString);
          }
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        str.close();
      }
    }
    catch (java.io.IOException e)
    {
      // Eat the exception and exit.
    }
    return list;
  }

  /** Compile all regexp entries in the passed in list, and add them to the output
  * list.
  */
  protected static void compileList(ArrayList output, ArrayList input)
    throws LCFException
  {
    int i = 0;
    while (i < input.size())
    {
      String inputString = (String)input.get(i++);
      try
      {
        output.add(Pattern.compile(inputString));
      }
      catch (PatternSyntaxException e)
      {
        throw new LCFException("Mapping regular expression '"+inputString+"' is illegal: "+e.getMessage(),e);
      }
    }
  }

  /** Get the page credentials for a given document identifier (URL) */
  protected PageCredentials getPageCredential(String documentIdentifier)
  {
    return credentialsDescription.getPageCredential(documentIdentifier);
  }

  /** Get the sequence credentials for a given document identifier (URL) */
  protected SequenceCredentials getSequenceCredential(String documentIdentifier)
  {
    return credentialsDescription.getSequenceCredential(documentIdentifier);
  }

  /** Get the trust store for a given document identifier (URL) */
  protected IKeystoreManager getTrustStore(String documentIdentifier)
    throws LCFException
  {
    return trustsDescription.getTrustStore(documentIdentifier);
  }

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(DocumentSpecification spec)
  {
    HashMap map = new HashMap();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("access"))
      {
        String token = sn.getAttributeValue("token");
        map.put(token,token);
      }
    }

    String[] rval = new String[map.size()];
    Iterator iter = map.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    return rval;
  }

  /** Read a document specification to yield a map of name/value pairs for metadata */
  protected static ArrayList findMetadata(DocumentSpecification spec)
    throws LCFException
  {
    ArrayList rval = new ArrayList();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals("metadata"))
      {
        String name = n.getAttributeValue("name");
        String value = n.getAttributeValue("value");
        if (name != null && name.length() > 0 && value != null && value.length() > 0)
          rval.add(new NameValue(name,value));
      }
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
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@param endChar is the character to use to mark the end.
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

  /** Calculate events that should be associated with a document. */
  protected String[] calculateDocumentEvents(INamingActivity activities, String documentIdentifier)
  {
    // All we have for events for right now are the robots and dns events.  Each document has one of each.
    try
    {
      // Get all the appropriate parts from the document identifier
      URL url = new URL(documentIdentifier);
      String hostName = url.getHost();
      String protocol = url.getProtocol();
      int port = url.getPort();
      if (port == -1)
        port = url.getDefaultPort();
      // Form the robots key
      String robotsKey = makeRobotsKey(protocol,hostName,port);
      // Build the corresponding event name
      String robotsEventName = makeRobotsEventName(activities,robotsKey);
      String dnsEventName = makeDNSEventName(activities,hostName);
      // See if we're in a session-protected area
      SequenceCredentials sequenceCredential = getSequenceCredential(documentIdentifier);
      if (sequenceCredential != null)
      {
        String sessionKey = sequenceCredential.getSequenceKey();
        String sessionEventName = makeSessionLoginEventName(activities,sessionKey);
        return new String[]{robotsEventName,hostName,sessionEventName};
      }
      return new String[]{robotsEventName,hostName};
    }
    catch (MalformedURLException e)
    {
      Logging.connectors.warn("WEB: Could not form event names for identifier '"+documentIdentifier+"' because it was malformed: "+e.getMessage(),e);
      return null;
    }
  }

  /** Name/value class */
  protected static class NameValue
  {
    protected String name;
    protected String value;

    public NameValue(String name, String value)
    {
      this.name = name;
      this.value = value;
    }

    public String getName()
    {
      return name;
    }

    public String getValue()
    {
      return value;
    }
  }

  /** Class representing a URL regular expression match, for the purposes of determining canonicalization policy */
  protected static class CanonicalizationPolicy
  {
    protected Pattern matchPattern;
    protected boolean reorder;
    protected boolean removeJavaSession;
    protected boolean removeAspSession;
    protected boolean removePhpSession;
    protected boolean removeBVSession;

    public CanonicalizationPolicy(Pattern matchPattern, boolean reorder, boolean removeJavaSession, boolean removeAspSession,
      boolean removePhpSession, boolean removeBVSession)
    {
      this.matchPattern = matchPattern;
      this.reorder = reorder;
      this.removeJavaSession = removeJavaSession;
      this.removeAspSession = removeAspSession;
      this.removePhpSession = removePhpSession;
      this.removeBVSession = removeBVSession;
    }

    public boolean checkMatch(String url)
    {
      Matcher matcher = matchPattern.matcher(url);
      return matcher.find();
    }

    public boolean canReorder()
    {
      return reorder;
    }

    public boolean canRemoveJavaSession()
    {
      return removeJavaSession;
    }

    public boolean canRemoveAspSession()
    {
      return removeAspSession;
    }

    public boolean canRemovePhpSession()
    {
      return removePhpSession;
    }

    public boolean canRemoveBvSession()
    {
      return removeBVSession;
    }

  }

  /** Class representing a list of canonicalization rules */
  protected static class CanonicalizationPolicies
  {
    protected ArrayList rules = new ArrayList();

    public CanonicalizationPolicies()
    {
    }

    public void addRule(CanonicalizationPolicy rule)
    {
      rules.add(rule);
    }

    public CanonicalizationPolicy findMatch(String url)
    {
      int i = 0;
      while (i < rules.size())
      {
        CanonicalizationPolicy rule = (CanonicalizationPolicy)rules.get(i++);
        if (rule.checkMatch(url))
          return rule;
      }
      return null;
    }
  }

  /** This class describes the url filtering information obtained from a digested DocumentSpecification.
  */
  protected static class DocumentURLFilter
  {
    /** The arraylist of include patterns */
    protected ArrayList includePatterns = new ArrayList();
    /** The arraylist of exclude patterns */
    protected ArrayList excludePatterns = new ArrayList();
    /** Canonicalization policies */
    protected CanonicalizationPolicies canonicalizationPolicies = new CanonicalizationPolicies();

    /** Process a document specification to produce a filter.
    * Note that we EXPECT the regexp's in the document specification to be properly formed.
    * This should be checked at save time to prevent errors.  Any syntax errors found here
    * will thus cause the include or exclude regexp to be skipped.
    */
    public DocumentURLFilter(DocumentSpecification spec)
      throws LCFException
    {
      String includes = "";
      String excludes = "";
      int i = 0;
      while (i < spec.getChildCount())
      {
        SpecificationNode sn = spec.getChild(i++);
        if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDES))
          includes = sn.getValue();
        else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDES))
          excludes = sn.getValue();
        else if (sn.getType().equals("urlspec"))
        {
          String urlRegexp = sn.getAttributeValue("regexp");
          if (urlRegexp == null)
            urlRegexp = "";
          String reorder = sn.getAttributeValue("reorder");
          boolean reorderValue;
          if (reorder == null)
            reorderValue = false;
          else
          {
            if (reorder.equals("yes"))
              reorderValue = true;
            else
              reorderValue = false;
          }

          String javaSession = sn.getAttributeValue("javasessionremoval");
          boolean javaSessionValue;
          if (javaSession == null)
            javaSessionValue = false;
          else
          {
            if (javaSession.equals("yes"))
              javaSessionValue = true;
            else
              javaSessionValue = false;
          }

          String aspSession = sn.getAttributeValue("aspsessionremoval");
          boolean aspSessionValue;
          if (aspSession == null)
            aspSessionValue = false;
          else
          {
            if (aspSession.equals("yes"))
              aspSessionValue = true;
            else
              aspSessionValue = false;
          }

          String phpSession = sn.getAttributeValue("phpsessionremoval");
          boolean phpSessionValue;
          if (phpSession == null)
            phpSessionValue = false;
          else
          {
            if (phpSession.equals("yes"))
              phpSessionValue = true;
            else
              phpSessionValue = false;
          }

          String bvSession = sn.getAttributeValue("bvsessionremoval");
          boolean bvSessionValue;
          if (bvSession == null)
            bvSessionValue = false;
          else
          {
            if (bvSession.equals("yes"))
              bvSessionValue = true;
            else
              bvSessionValue = false;
          }
          try
          {
            canonicalizationPolicies.addRule(new CanonicalizationPolicy(Pattern.compile(urlRegexp),reorderValue,javaSessionValue,aspSessionValue,
              phpSessionValue, bvSessionValue));
          }
          catch (java.util.regex.PatternSyntaxException e)
          {
            throw new LCFException("Canonicalization regular expression '"+urlRegexp+"' is illegal: "+e.getMessage(),e);
          }
        }
      }

      ArrayList list = stringToArray(includes);
      compileList(includePatterns,list);
      list = stringToArray(excludes);
      compileList(excludePatterns,list);
    }

    /** Check if the document identifier is legal.
    */
    public boolean isDocumentLegal(String url)
    {
      // First, verify that the url matches one of the patterns in the include list.
      int i = 0;
      while (i < includePatterns.size())
      {
        Pattern p = (Pattern)includePatterns.get(i);
        Matcher m = p.matcher(url);
        if (m.find())
          break;
        i++;
      }
      if (i == includePatterns.size())
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Url '"+url+"' is illegal because no include patterns match it");
        return false;
      }

      // Now make sure it's not in the exclude list.
      i = 0;
      while (i < excludePatterns.size())
      {
        Pattern p = (Pattern)excludePatterns.get(i);
        Matcher m = p.matcher(url);
        if (m.find())
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Url '"+url+"' is illegal because exclude pattern '"+p.toString()+"' matched it");
          return false;
        }
        i++;
      }

      return true;
    }

    /** Get canonicalization policies */
    public CanonicalizationPolicies getCanonicalizationPolicies()
    {
      return canonicalizationPolicies;
    }

  }

  /** This interface describes the functionality needed by a link extractor to note a discovered link.
  */
  protected static interface IDiscoveredLinkHandler
  {
    /** Inform the world of a discovered link.
    *@param rawURL is the raw discovered url.  This may be relative, malformed, or otherwise unsuitable for use until final form is acheived.
    */
    public void noteDiscoveredLink(String rawURL)
      throws LCFException;
  }

  /** This interface describes the functionality needed by an redirection processor in order to handle a redirection.
  */
  protected static interface IRedirectionHandler extends IDiscoveredLinkHandler
  {
  }

  /** This interface describes the functionality needed by an XML processor in order to handle an XML document.
  */
  protected static interface IXMLHandler extends IDiscoveredLinkHandler
  {
    /** Inform the world of a discovered ttl value.
    *@param rawTtlValue is the raw discovered ttl value.
    */
    public void noteDiscoveredTtlValue(String rawTtlValue)
      throws LCFException;

  }

  /** This interface describes the functionality needed by an HTML processor in order to handle an HTML document.
  */
  protected static interface IHTMLHandler extends IDiscoveredLinkHandler
  {
    /** Note the start of a form */
    public void noteFormStart(Map formAttributes)
      throws LCFException;

    /** Note an input tag */
    public void noteFormInput(Map inputAttributes)
      throws LCFException;

    /** Note the end of a form */
    public void noteFormEnd()
      throws LCFException;

    /** Note discovered href */
    public void noteAHREF(String rawURL)
      throws LCFException;

    /** Note discovered href */
    public void noteLINKHREF(String rawURL)
      throws LCFException;

    /** Note discovered IMG SRC */
    public void noteIMGSRC(String rawURL)
      throws LCFException;

    /** Note discovered FRAME SRC */
    public void noteFRAMESRC(String rawURL)
      throws LCFException;
  }

  // HTML parsing classes and constants

  /** Is a character HTML whitespace? */
  protected static boolean isHTMLWhitespace(char x)
  {
    return x <= ' ';
  }

  /** Decode an html attribute */
  protected static String htmlAttributeDecode(String input)
  {
    StringBuffer output = new StringBuffer();
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
      if (x == '&')
      {
        int index = input.indexOf(";",i);
        if (index != -1)
        {
          String chunk = input.substring(i,index);
          String replacement = mapChunk(chunk);
          if (replacement != null)
          {
            output.append(replacement);
            i = index + 1;
            continue;
          }
        }
      }
      output.append(x);
    }
    return output.toString();
  }

  /** Map an entity reference back to a character */
  protected static String mapChunk(String input)
  {
    if (input.startsWith("#"))
    {
      // Treat as a decimal value
      try
      {
        int value = Integer.parseInt(input.substring(1));
        StringBuffer sb = new StringBuffer();
        sb.append((char)value);
        return sb.toString();
      }
      catch (NumberFormatException e)
      {
        return null;
      }
    }
    else
      return (String)mapLookup.get(input);
  }

  // Basic parse states (lexical analysis)

  protected static final int BASICPARSESTATE_NORMAL = 0;
  protected static final int BASICPARSESTATE_SAWLEFTBRACKET = 1;
  protected static final int BASICPARSESTATE_SAWEXCLAMATION = 2;
  protected static final int BASICPARSESTATE_SAWDASH = 3;
  protected static final int BASICPARSESTATE_IN_COMMENT = 4;
  protected static final int BASICPARSESTATE_SAWCOMMENTDASH = 5;
  protected static final int BASICPARSESTATE_SAWSECONDCOMMENTDASH = 6;
  protected static final int BASICPARSESTATE_IN_TAG_NAME = 7;
  protected static final int BASICPARSESTATE_IN_ATTR_NAME = 8;
  protected static final int BASICPARSESTATE_IN_ATTR_VALUE = 9;
  protected static final int BASICPARSESTATE_IN_TAG_SAW_SLASH = 10;
  protected static final int BASICPARSESTATE_IN_END_TAG_NAME = 11;
  protected static final int BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE = 12;
  protected static final int BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE = 13;
  protected static final int BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE = 14;
  protected static final int BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE = 15;


  /** This class represents the basic, outermost parse state. */
  protected static class BasicParseState
  {
    protected int currentState = BASICPARSESTATE_NORMAL;

    protected StringBuffer currentTagNameBuffer = null;
    protected StringBuffer currentAttrNameBuffer = null;
    protected StringBuffer currentValueBuffer = null;

    protected String currentTagName = null;
    protected String currentAttrName = null;
    protected Map currentAttrMap = null;

    public BasicParseState()
    {
    }

    /** Deal with a character.  No exceptions are allowed, since those would represent syntax errors, and we don't want those to cause difficulty. */
    public void dealWithCharacter(char thisChar)
      throws LCFException
    {
      // At this level we want basic lexical analysis - that is, we deal with identifying tags and comments, that's it.
      char thisCharLower = Character.toLowerCase(thisChar);
      switch (currentState)
      {
      case BASICPARSESTATE_NORMAL:
        if (thisChar == '<')
          currentState = BASICPARSESTATE_SAWLEFTBRACKET;
        break;
      case BASICPARSESTATE_SAWLEFTBRACKET:
        if (thisChar == '!')
          currentState = BASICPARSESTATE_SAWEXCLAMATION;
        else if (thisChar == '/')
        {
          currentState = BASICPARSESTATE_IN_END_TAG_NAME;
          currentTagNameBuffer = new StringBuffer();
        }
        else
        {
          currentState = BASICPARSESTATE_IN_TAG_NAME;
          currentTagNameBuffer = new StringBuffer();
          if (!isHTMLWhitespace(thisChar))
            currentTagNameBuffer.append(thisCharLower);
        }
        break;
      case BASICPARSESTATE_SAWEXCLAMATION:
        if (thisChar == '-')
          currentState = BASICPARSESTATE_SAWDASH;
        else
          currentState = BASICPARSESTATE_NORMAL;
        break;
      case BASICPARSESTATE_SAWDASH:
        if (thisChar == '-')
          currentState = BASICPARSESTATE_IN_COMMENT;
        else
          currentState = BASICPARSESTATE_NORMAL;
        break;
      case BASICPARSESTATE_IN_COMMENT:
        // We're in a comment.  All we should look for is the end of the comment.
        if (thisChar == '-')
          currentState = BASICPARSESTATE_SAWCOMMENTDASH;
        break;
      case BASICPARSESTATE_SAWCOMMENTDASH:
        if (thisChar == '-')
          currentState = BASICPARSESTATE_SAWSECONDCOMMENTDASH;
        else
          currentState = BASICPARSESTATE_IN_COMMENT;
        break;
      case BASICPARSESTATE_SAWSECONDCOMMENTDASH:
        if (thisChar == '>')
          currentState = BASICPARSESTATE_NORMAL;
        else if (thisChar != '-')
          currentState = BASICPARSESTATE_IN_COMMENT;
        break;
      case BASICPARSESTATE_IN_TAG_NAME:
        if (isHTMLWhitespace(thisChar))
        {
          if (currentTagNameBuffer.length() > 0)
          {
            // Done with the tag name!
            currentTagName = currentTagNameBuffer.toString();
            currentTagNameBuffer = null;
            currentAttrMap = new HashMap();
            currentState = BASICPARSESTATE_IN_ATTR_NAME;
            currentAttrNameBuffer = new StringBuffer();
          }
        }
        else if (thisChar == '/')
        {
          if (currentTagNameBuffer.length() > 0)
          {
            currentTagName = currentTagNameBuffer.toString();
            currentTagNameBuffer = null;
            currentAttrMap = new HashMap();
            currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
            noteTag(currentTagName,currentAttrMap);
          }
          else
          {
            currentState = BASICPARSESTATE_NORMAL;
            currentTagNameBuffer = null;
          }
        }
        else if (thisChar == '>')
        {
          if (currentTagNameBuffer.length() > 0)
          {
            currentTagName = currentTagNameBuffer.toString();
            currentTagNameBuffer = null;
            currentAttrMap = new HashMap();
          }
          if (currentTagName != null)
          {
            noteTag(currentTagName,currentAttrMap);
          }
          currentState = BASICPARSESTATE_NORMAL;
          currentTagName = null;
          currentAttrMap = null;
        }
        else
          currentTagNameBuffer.append(thisCharLower);
        break;
      case BASICPARSESTATE_IN_ATTR_NAME:
        if (isHTMLWhitespace(thisChar))
        {
          if (currentAttrNameBuffer.length() > 0)
          {
            // Done with attr name!
            currentAttrName = currentAttrNameBuffer.toString();
            currentAttrNameBuffer = null;
            currentState = BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE;
          }
        }
        else if (thisChar == '=')
        {
          if (currentAttrNameBuffer.length() > 0)
          {
            currentAttrName = currentAttrNameBuffer.toString();
            currentAttrNameBuffer = null;
            currentState = BASICPARSESTATE_IN_ATTR_VALUE;
            currentValueBuffer = new StringBuffer();
          }
        }
        else if (thisChar == '/')
        {
          if (currentAttrNameBuffer.length() > 0)
          {
            currentAttrName = currentAttrNameBuffer.toString();
            currentAttrNameBuffer = null;
          }
          if (currentAttrName != null)
          {
            currentAttrMap.put(currentAttrName,"");
            currentAttrName = null;
          }
          noteTag(currentTagName,currentAttrMap);
          currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
        }
        else if (thisChar == '>')
        {
          if (currentAttrNameBuffer.length() > 0)
          {
            currentAttrName = currentAttrNameBuffer.toString();
            currentAttrNameBuffer = null;
          }
          if (currentAttrName != null)
          {
            currentAttrMap.put(currentAttrName,"");
            currentAttrName = null;
          }
          currentState = BASICPARSESTATE_NORMAL;
          noteTag(currentTagName,currentAttrMap);
          currentTagName = null;
          currentAttrMap = null;
        }
        else
          currentAttrNameBuffer.append(thisCharLower);
        break;
      case BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE:
        if (thisChar == '=')
        {
          currentState = BASICPARSESTATE_IN_ATTR_VALUE;
          currentValueBuffer = new StringBuffer();
        }
        else if (thisChar == '>')
        {
          currentState = BASICPARSESTATE_NORMAL;
          noteTag(currentTagName,currentAttrMap);
          currentTagName = null;
          currentAttrMap = null;
        }
        else if (thisChar == '/')
        {
          currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
          currentAttrMap.put(currentAttrName,"");
          currentAttrName = null;
          noteTag(currentTagName,currentAttrMap);
        }
        else if (!isHTMLWhitespace(thisChar))
        {
          currentAttrMap.put(currentAttrName,"");
          currentState = BASICPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuffer();
          currentAttrNameBuffer.append(thisCharLower);
          currentAttrName = null;
        }
        break;
      case BASICPARSESTATE_IN_ATTR_VALUE:
        if (thisChar == '\'')
          currentState = BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE;
        else if (thisChar == '"')
          currentState = BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE;
        else if (!isHTMLWhitespace(thisChar))
        {
          currentState = BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE;
          currentValueBuffer.append(thisChar);
        }
        break;
      case BASICPARSESTATE_IN_TAG_SAW_SLASH:
        if (thisChar == '>')
        {
          noteEndTag(currentTagName);
          currentState = BASICPARSESTATE_NORMAL;
          currentTagName = null;
          currentAttrMap = null;
        }
        break;
      case BASICPARSESTATE_IN_END_TAG_NAME:
        if (isHTMLWhitespace(thisChar))
        {
          if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
          {
            // Done with the tag name!
            currentTagName = currentTagNameBuffer.toString();
            currentTagNameBuffer = null;
          }
        }
        else if (thisChar == '>')
        {
          if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
          {
            currentTagName = currentTagNameBuffer.toString();
            currentTagNameBuffer = null;
          }
          if (currentTagName != null)
          {
            noteEndTag(currentTagName);
          }
          currentTagName = null;
          currentState = BASICPARSESTATE_NORMAL;
        }
        else if (currentTagNameBuffer != null)
          currentTagNameBuffer.append(thisCharLower);
        break;
      case BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE:
        if (thisChar == '\'' || thisChar == '\n' || thisChar == '\r')
        {
          currentAttrMap.put(currentAttrName,htmlAttributeDecode(currentValueBuffer.toString()));
          currentAttrName = null;
          currentValueBuffer = null;
          currentState = BASICPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuffer();
        }
        else
          currentValueBuffer.append(thisChar);
        break;
      case BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE:
        if (thisChar == '"' || thisChar == '\n' || thisChar == '\r')
        {
          currentAttrMap.put(currentAttrName,htmlAttributeDecode(currentValueBuffer.toString()));
          currentAttrName = null;
          currentValueBuffer = null;
          currentState = BASICPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuffer();
        }
        else
          currentValueBuffer.append(thisChar);
        break;
      case BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE:
        if (isHTMLWhitespace(thisChar))
        {
          currentAttrMap.put(currentAttrName,htmlAttributeDecode(currentValueBuffer.toString()));
          currentAttrName = null;
          currentValueBuffer = null;
          currentState = BASICPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuffer();
        }
        else if (thisChar == '/')
        {
          currentAttrMap.put(currentAttrName,htmlAttributeDecode(currentValueBuffer.toString()));
          noteTag(currentTagName,currentAttrMap);
          currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
        }
        else if (thisChar == '>')
        {
          currentAttrMap.put(currentAttrName,htmlAttributeDecode(currentValueBuffer.toString()));
          currentAttrName = null;
          currentValueBuffer = null;
          currentState = BASICPARSESTATE_NORMAL;
          noteTag(currentTagName,currentAttrMap);
          currentTagName = null;
          currentAttrMap = null;
        }
        else
          currentValueBuffer.append(thisChar);
        break;
      default:
        throw new LCFException("Invalid state: "+Integer.toString(currentState));
      }
    }

    protected void noteTag(String tagName, Map attributes)
      throws LCFException
    {
      Logging.connectors.debug(" Saw tag '"+tagName+"'");
    }

    protected void noteEndTag(String tagName)
      throws LCFException
    {
      Logging.connectors.debug(" Saw end tag '"+tagName+"'");
    }

    public void finishUp()
      throws LCFException
    {
      // Does nothing
    }

  }

  // Script tag parsing states

  protected static final int SCRIPTPARSESTATE_NORMAL = 0;
  protected static final int SCRIPTPARSESTATE_INSCRIPT = 1;

  /** This class interprets the tag stream generated by the BasicParseState class, and causes script sections to be skipped */
  protected static class ScriptParseState extends BasicParseState
  {
    protected int scriptParseState = SCRIPTPARSESTATE_NORMAL;

    public ScriptParseState()
    {
      super();
    }

    // Override methods having to do with notification of tag discovery

    protected void noteTag(String tagName, Map attributes)
      throws LCFException
    {
      super.noteTag(tagName,attributes);
      switch (scriptParseState)
      {
      case SCRIPTPARSESTATE_NORMAL:
        if (tagName.equals("script"))
          scriptParseState = SCRIPTPARSESTATE_INSCRIPT;
        else
          noteNonscriptTag(tagName,attributes);
        break;
      case SCRIPTPARSESTATE_INSCRIPT:
        // Skip all tags until we see the end script one.
        break;
      default:
        throw new LCFException("Unknown script parse state: "+Integer.toString(scriptParseState));
      }
    }

    protected void noteEndTag(String tagName)
      throws LCFException
    {
      super.noteEndTag(tagName);
      switch (scriptParseState)
      {
      case SCRIPTPARSESTATE_NORMAL:
        noteNonscriptEndTag(tagName);
        break;
      case SCRIPTPARSESTATE_INSCRIPT:
        // Skip all tags until we see the end script one.
        if (tagName.equals("script"))
          scriptParseState = SCRIPTPARSESTATE_NORMAL;
        break;
      default:
        break;
      }
    }

    protected void noteNonscriptTag(String tagName, Map attributes)
      throws LCFException
    {
    }

    protected void noteNonscriptEndTag(String tagName)
      throws LCFException
    {
    }

  }

  /** This class recognizes and interprets all links */
  protected static class LinkParseState extends ScriptParseState
  {

    protected IHTMLHandler handler;

    public LinkParseState(IHTMLHandler handler)
    {
      super();
      this.handler = handler;
    }

    protected void noteNonscriptTag(String tagName, Map attributes)
      throws LCFException
    {
      super.noteNonscriptTag(tagName,attributes);
      String lowerTagName = tagName.toLowerCase();
      if (tagName.equals("a"))
      {
        String hrefValue = (String)attributes.get("href");
        if (hrefValue != null && hrefValue.length() > 0)
          handler.noteAHREF(hrefValue);
      }
      else if (tagName.equals("link"))
      {
        String hrefValue = (String)attributes.get("href");
        if (hrefValue != null && hrefValue.length() > 0)
          handler.noteLINKHREF(hrefValue);
      }
      else if (tagName.equals("img"))
      {
        String srcValue = (String)attributes.get("src");
        if (srcValue != null && srcValue.length() > 0)
          handler.noteIMGSRC(srcValue);
      }
      else if (tagName.equals("frame"))
      {
        String srcValue = (String)attributes.get("src");
        if (srcValue != null && srcValue.length() > 0)
          handler.noteFRAMESRC(srcValue);
      }
    }

  }

  // States for form handling.

  protected final static int FORMPARSESTATE_NORMAL = 0;
  protected final static int FORMPARSESTATE_IN_FORM = 1;
  protected final static int FORMPARSESTATE_IN_SELECT = 2;
  protected final static int FORMPARSESTATE_IN_TEXTAREA = 3;

  /** This class interprets the tag stream generated by the BasicParseState class, and keeps track of the form tags. */
  protected static class FormParseState extends LinkParseState
  {
    protected int formParseState = FORMPARSESTATE_NORMAL;
    protected String selectName = null;
    protected String selectMultiple = null;

    public FormParseState(IHTMLHandler handler)
    {
      super(handler);
    }

    // Override methods having to do with notification of tag discovery

    protected void noteNonscriptTag(String tagName, Map attributes)
      throws LCFException
    {
      super.noteNonscriptTag(tagName,attributes);
      switch (formParseState)
      {
      case FORMPARSESTATE_NORMAL:
        if (tagName.equals("form"))
        {
          formParseState = FORMPARSESTATE_IN_FORM;
          handler.noteFormStart(attributes);
        }
        break;
      case FORMPARSESTATE_IN_FORM:
        if (tagName.equals("input"))
        {
          String type = (String)attributes.get("type");
          // We're only interested in form elements that can actually transmit data
          if (type != null && !type.toLowerCase().equals("button") && !type.toLowerCase().equals("reset") && !type.toLowerCase().equals("image"))
            handler.noteFormInput(attributes);
        }
        else if (tagName.equals("select"))
        {
          selectName = (String)attributes.get("name");
          selectMultiple = (String)attributes.get("multiple");
          formParseState = FORMPARSESTATE_IN_SELECT;
        }
        else if (tagName.equals("textarea"))
        {
          formParseState = FORMPARSESTATE_IN_TEXTAREA;
          Map textareaMap = new HashMap();
          textareaMap.put("type","textarea");
          // Default value is too tough to meaningfully compute because of the embedded tags etc.  Known limitation.
          textareaMap.put("value","");
          handler.noteFormInput(textareaMap);
        }
        else if (tagName.equals("button"))
        {
          String type = (String)attributes.get("type");
          if (type == null || type.toLowerCase().equals("submit"))
          {
            // Same as input type="submit"
            handler.noteFormInput(attributes);
          }
        }
        else if (tagName.equals("isindex"))
        {
          Map indexMap = new HashMap();
          indexMap.put("type","text");
        }
        break;
      case FORMPARSESTATE_IN_SELECT:
        if (tagName.equals("option"))
        {
          String optionValue = (String)attributes.get("value");
          String optionSelected = (String)attributes.get("selected");
          Map optionMap = new HashMap();
          optionMap.put("type","select");
          optionMap.put("name",selectName);
          optionMap.put("multiple",selectMultiple);
          optionMap.put("value",optionValue);
          optionMap.put("selected",optionSelected);
          handler.noteFormInput(optionMap);
        }
        break;
      case FORMPARSESTATE_IN_TEXTAREA:
        break;
      default:
        throw new LCFException("Unknown form parse state: "+Integer.toString(formParseState));
      }
    }

    protected void noteNonscriptEndTag(String tagName)
      throws LCFException
    {
      super.noteNonscriptEndTag(tagName);
      switch (formParseState)
      {
      case FORMPARSESTATE_NORMAL:
        break;
      case FORMPARSESTATE_IN_FORM:
        if (tagName.equals("form"))
        {
          handler.noteFormEnd();
          formParseState = FORMPARSESTATE_NORMAL;
        }
        break;
      case FORMPARSESTATE_IN_SELECT:
        formParseState = FORMPARSESTATE_IN_FORM;
        selectName = null;
        selectMultiple = null;
        break;
      case FORMPARSESTATE_IN_TEXTAREA:
        formParseState = FORMPARSESTATE_IN_FORM;
        break;
      default:
        throw new LCFException("Unknown form parse state: "+Integer.toString(formParseState));
      }
    }

  }

}


