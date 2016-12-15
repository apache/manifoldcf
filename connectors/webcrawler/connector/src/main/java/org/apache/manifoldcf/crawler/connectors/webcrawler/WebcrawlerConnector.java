/* $Id: WebcrawlerConnector.java 995042 2010-09-08 13:10:06Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.webcrawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.ui.util.Encoder;

import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.connectorcommon.fuzzyml.*;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.*;
import java.util.regex.*;

/** This is the Web Crawler implementation of the IRepositoryConnector interface.
* This connector may be superceded by one that calls out to python, or by a entirely
* python Connector Framework, depending on how the winds blow.
*
*/
public class WebcrawlerConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: WebcrawlerConnector.java 995042 2010-09-08 13:10:06Z kwright $";

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

  /** This represents a list of the mime types that this connector knows how to extract links from.
  * Documents that are indexable are described by the output connector. */
  protected static final String[] interestingMimeTypeArray = new String[]
  {
    "application/rtf",
    "application/xls",
    "text/html",
    "text/rtf",
    "application/x-excel",
    "application/x-msexcel",
    "application/x-mspowerpoint",
    "application/x-msword-doc",
    "application/x-msword",
    "application/x-word",
    "text/xml",
    "no-type",
    "text/plain",
    "application/x-rtf",
    "application/x-pdf",
    "text/richtext",
    "Text",
    "Text/html"
  };

  protected static final Set<String> interestingMimeTypeMap;
  static
  {
    interestingMimeTypeMap = new HashSet<String>();
    int i = 0;
    while (i < interestingMimeTypeArray.length)
    {
      String type = interestingMimeTypeArray[i++];
      interestingMimeTypeMap.add(type);
    }
  }

  protected static final Set<String> understoodProtocols;
  static
  {
    understoodProtocols = new HashSet<String>();
    understoodProtocols.add("http");
    understoodProtocols.add("https");
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
  public final static String ACTIVITY_PROCESS = "process";
  public final static String ACTIVITY_ROBOTSPARSE = "robots parse";
  public final static String ACTIVITY_LOGON_START = "begin logon";
  public final static String ACTIVITY_LOGON_END = "end logon";

  // Fetch types
  protected final static String FETCH_ROBOTS = "ROBOTS";
  protected final static String FETCH_STANDARD = "URL";
  protected final static String FETCH_LOGIN = "LOGIN";

  // Reserved headers
  protected final static Set<String> reservedHeaders;
  static
  {
    reservedHeaders = new HashSet<String>();
    reservedHeaders.add("age");
    reservedHeaders.add("www-authenticate");
    reservedHeaders.add("proxy-authenticate");
    reservedHeaders.add("date");
    reservedHeaders.add("set-cookie");
    reservedHeaders.add("via");
  }
  
  // Potentially excluded headers
  protected final static List<String> potentiallyExcludedHeaders;
  static
  {
    potentiallyExcludedHeaders = new ArrayList<String>();
    potentiallyExcludedHeaders.add("last-modified");
  }
  
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
  /** Throttle group name */
  protected String throttleGroupName = null;

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

  /** Proxy host */
  protected String proxyHost = null;
  
  /** Proxy port */
  protected int proxyPort = -1;
  
  /** Proxy auth domain */
  protected String proxyAuthDomain = null;
  
  /** Proxy auth user name */
  protected String proxyAuthUsername = null;
  
  /** Proxy auth password */
  protected String proxyAuthPassword = null;
  
  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  /** Constructor.
  */
  public WebcrawlerConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    // We return all seeds every time.
    return MODEL_ALL;
  }

  /** Install the connector.
  * This method is called to initialize persistent storage for the connector, such as database tables etc.
  * It is called when the connector is registered.
  *@param threadContext is the current thread context.
  */
  @Override
  public void install(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Install
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());

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
    catch (ManifoldCFException e)
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
  @Override
  public void deinstall(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Uninstall
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());

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
    catch (ManifoldCFException e)
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
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH, ACTIVITY_PROCESS, ACTIVITY_ROBOTSPARSE, ACTIVITY_LOGON_START, ACTIVITY_LOGON_END};
  }


  /** Return the list of relationship types that this connector recognizes.
  *@return the list.
  */
  @Override
  public String[] getRelationshipTypes()
  {
    return new String[]{REL_LINK,REL_REDIRECT};
  }

  /** Clear out any state information specific to a given thread.
  * This method is called when this object is returned to the connection pool.
  */
  @Override
  public void clearThreadContext()
  {
    super.clearThreadContext();
    robotsManager = null;
    dnsManager = null;
    cookieManager = null;
  }

  /** Start a session */
  protected void getSession()
    throws ManifoldCFException
  {
    // Handle the stuff that requires a thread context
    if (robotsManager == null || dnsManager == null || cookieManager == null)
    {
      IDBInterface databaseHandle = DBInterfaceFactory.make(currentContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      robotsManager = new RobotsManager(currentContext,databaseHandle);
      dnsManager = new DNSManager(currentContext,databaseHandle);
      cookieManager = new CookieManager(currentContext,databaseHandle);
    }

    // Handle everything else
    if (!isInitialized)
    {
      String x;

      // Either set this from the connection name, or just have one.  Right now, we have one.
      String throttleGroupName = "";
      
      String emailAddress = params.getParameter(WebcrawlerConfig.PARAMETER_EMAIL);
      if (emailAddress == null)
        throw new ManifoldCFException("Missing email address");
      userAgent = "Mozilla/5.0 (ApacheManifoldCFWebCrawler; "+emailAddress+")";
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

      proxyHost = params.getParameter(WebcrawlerConfig.PARAMETER_PROXYHOST);
      String proxyPortString = params.getParameter(WebcrawlerConfig.PARAMETER_PROXYPORT);
      proxyAuthDomain = params.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHDOMAIN);
      proxyAuthUsername = params.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHUSERNAME);
      proxyAuthPassword = params.getObfuscatedParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHPASSWORD);

      proxyPort = -1;
      if (proxyPortString != null && proxyPortString.length() > 0)
      {
        try
        {
          proxyPort = Integer.parseInt(proxyPortString);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage(),e);
        }
      }

      isInitialized = true;
    }
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    ThrottledFetcher.flushIdleConnections(currentContext);
  }

  /** Check status of connection.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    getSession();
    return super.check();
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    throttleGroupName = null;
    throttleDescription = null;
    credentialsDescription = null;
    trustsDescription = null;
    userAgent = null;
    from = null;
    proxyHost = null;
    proxyPort = -1;
    proxyAuthDomain = null;
    proxyAuthUsername = null;
    proxyAuthPassword = null;

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
  @Override
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
  * The end time and seeding version string passed to this method may be interpreted for greatest efficiency.
  * For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding version string to null.  The
  * seeding version string may also be set to null on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  * The connector will be connected before this method can be called.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param seedTime is the end of the time range of documents to consider, exclusive.
  *@param lastSeedVersion is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
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
    List<String> list = stringToArray(seeds);
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
    return "";
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


  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param statuses are the currently-stored document versions for each document in the set of document identifiers
  * passed in above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();

    // Forced acls
    String[] acls = getAcls(spec);
    // Sort it,
    java.util.Arrays.sort(acls);

    // Get the excluded headers
    Set<String> excludedHeaders = findExcludedHeaders(spec);
    
    // Since document specifications can change, we need to look at each url and filter it as part of the
    // process of getting version strings.  To do that, we need to compile the DocumentSpecification into
    // an object that knows how to do this.
    DocumentURLFilter filter = new DocumentURLFilter(spec);

    String filterVersion = filter.getVersionString();
    
    // There are two ways to handle any document that's not available.  The first is to remove it.  The second is to keep it, but mark it with an empty version string.
    // With the web crawler, the major concern with simply removing the document is that it might be referred to from multiple places - and in addition
    // it will get requeued every time the parent document is processed.  This is not optimal because it represents churn.
    // On the other hand, keeping the document in the queue causes the queue to bloat, which is also not optimal, and it makes the crawler basically
    // incapable of deleting documents.
    // Since the primary use of the crawler is expected to be repeated intranet crawls,  I've thus chosen to optimize the crawler for accuracy rather than performance
    // - if the document is gone, I just remove it, and expect churn when recrawling activities occur.
    for (String documentIdentifier : documentIdentifiers)
    {
      // Verify that the url is legal
      if (!filter.isDocumentAndHostLegal(documentIdentifier))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Removing url '"+documentIdentifier+"' because it's not in the set of allowed ones");
        // Use null because we should have already filtered when we queued.
        activities.deleteDocument(documentIdentifier);
        continue;
      }
      
      try
      {
        // The first thing we need to know is whether this url is part of a session-protected area.  We'll use that information
        // later to detect redirection to login.
        SequenceCredentials sessionCredential = getSequenceCredential(documentIdentifier);

        if (Logging.connectors.isDebugEnabled())
        {
          if (sessionCredential != null)
            Logging.connectors.debug("Web: For document identifier '"+documentIdentifier+"' found session credential key '"+sessionCredential.getSequenceKey()+"'");
        }
          
        // Set up the initial state and state variables.
        // Fetch status
        FetchStatus fetchStatus = new FetchStatus();

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

          loginAndFetch(fetchStatus,activities,documentIdentifier,sessionCredential,globalSequenceEvent);
        
        
          switch (fetchStatus.resultSignal)
          {
          case RESULT_NO_DOCUMENT:
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Removing url '"+documentIdentifier+"'"+((fetchStatus.contextMessage!=null)?" because "+fetchStatus.contextMessage:""),fetchStatus.contextException);
            activities.deleteDocument(documentIdentifier);
            break;
          case RESULT_NO_VERSION:
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Ignoring url '"+documentIdentifier+"'"+((fetchStatus.contextMessage!=null)?" because "+fetchStatus.contextMessage:""),fetchStatus.contextException);
            
            // We get here when a document didn't fetch.
            // No version 
            activities.noDocument(documentIdentifier,"");
            break;
          case RESULT_VERSION_NEEDED:
            // Calculate version from document data, which is presumed to be present.
            StringBuilder sb = new StringBuilder();

            // Acls
            packList(sb,acls,'+');
            if (acls.length > 0)
            {
              sb.append('+');
              pack(sb,defaultAuthorityDenyToken,'+');
            }
            else
              sb.append('-');

            // Now, do the metadata. 
            Map<String,Set<String>> metaHash = new HashMap<String,Set<String>>();
            
            String[] fixedListStrings = new String[2];
            // They're all folded into the same part of the version string.
            int headerCount = 0;
            Iterator<String> headerIterator = fetchStatus.headerData.keySet().iterator();
            while (headerIterator.hasNext())
            {
              String headerName = headerIterator.next();
              String lowerHeaderName = headerName.toLowerCase(Locale.ROOT);
              if (!reservedHeaders.contains(lowerHeaderName) && !excludedHeaders.contains(lowerHeaderName))
                headerCount += fetchStatus.headerData.get(headerName).size();
            }
            String[] fullMetadata = new String[headerCount];
            headerCount = 0;
            headerIterator = fetchStatus.headerData.keySet().iterator();
            while (headerIterator.hasNext())
            {
              String headerName = headerIterator.next();
              String lowerHeaderName = headerName.toLowerCase(Locale.ROOT);
              if (!reservedHeaders.contains(lowerHeaderName) && !excludedHeaders.contains(lowerHeaderName))
              {
                Set<String> valueSet = metaHash.get(headerName);
                if (valueSet == null)
                {
                  valueSet = new HashSet<String>();
                  metaHash.put(headerName,valueSet);
                }
                List<String> headerValues = fetchStatus.headerData.get(headerName);
                for (String headerValue : headerValues)
                {
                  valueSet.add(headerValue);
                  fixedListStrings[0] = "header-"+headerName;
                  fixedListStrings[1] = headerValue;
                  StringBuilder newsb = new StringBuilder();
                  packFixedList(newsb,fixedListStrings,'=');
                  fullMetadata[headerCount++] = newsb.toString();
                }
              }
            }
            java.util.Arrays.sort(fullMetadata);
              
            packList(sb,fullMetadata,'+');
            // Done with the parseable part!  Add the checksum.
            sb.append(fetchStatus.checkSum);
            // Add the filter version
            sb.append("+");
            sb.append(filterVersion);
              
            String versionString = sb.toString();

            // Now, extract links.
            // We'll call the "link extractor" series, so we can plug more stuff in over time.
            boolean indexDocument = extractLinks(documentIdentifier,activities,filter);

            // If scanOnly is set, we never ingest.  But all else is the same.
            if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
              continue;
            
            processDocument(activities,documentIdentifier,versionString,indexDocument,metaHash,acls,filter);
            break;
          case RESULT_RETRY_DOCUMENT:
            // Document could not be processed right now.
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("WEB: Retrying url '"+documentIdentifier+"' later"+((fetchStatus.contextMessage!=null)?" because "+fetchStatus.contextMessage:""),fetchStatus.contextException);
            activities.retryDocumentProcessing(documentIdentifier);
            break;
          default:
            throw new IllegalStateException("Unexpected value for result signal: "+Integer.toString(fetchStatus.resultSignal));
          }
        }
        finally
        {
          // Clean up event, if there is one.
          if (fetchStatus.sessionState == SESSIONSTATE_LOGIN && globalSequenceEvent != null)
          {
            // Terminate the event
            activities.completeEventSequence(globalSequenceEvent);
          }
        }
      }
      finally
      {
        cache.deleteData(documentIdentifier);
      }
    }
  }

  protected void loginAndFetch(FetchStatus fetchStatus, IProcessActivity activities, String documentIdentifier, SequenceCredentials sessionCredential, String globalSequenceEvent)
    throws ManifoldCFException, ServiceInterruption
  {
    long currentTime = System.currentTimeMillis();
    // Here's the maximum number of connections we are going to allow.
    int connectionLimit = 200;

    String currentURI = documentIdentifier;

    // Login pages are special in that I *don't* require them to do a robots check.  The reason why is because it is conceivable that a
    // site may inadvertantly exclude them via robots, and yet allow content pages to be scanned.  This would effectively exclude session login
    // for that site if we adhered to the strict policy.  Since login pages have to be exclusively identified as being special, explicit
    // permission is effectively granted by the user in any case.

    // The result code to be activity logging, or null if no activity logging desired.
    String activityResultCode = null;
    // Form data
    FormData formData = null;
    
    while (true)
    {
      URL url;
      try
      {
        // Do the mapping from the current host name to the IP address
        url = new URL(currentURI);
      }
      catch (MalformedURLException e)
      {
        // currentURI is malformed.
        // If the document was the primary, we should remove it from the queue.  But if it's part of a login sequence, we'd better just retry later.
        fetchStatus.contextMessage = "was not a valid URL: "+e.getMessage();
        fetchStatus.contextException = e;
        activityResultCode = "-12";
        fetchStatus.resultSignal = RESULT_NO_DOCUMENT;
        break;
      }

      String hostName = url.getHost();
      StringBuilder ipAddressBuffer = new StringBuilder();
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
          url.getFile(),activities,connectionLimit,proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword)) == RESULTSTATUS_TRUE)
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
          IThrottledConnection connection = ThrottledFetcher.getConnection(currentContext,
            throttleGroupName,
            protocol,ipAddress,port,
            credential,trustStore,throttleDescription,binNames,connectionLimit,
            proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword,
            socketTimeoutMilliseconds,connectionTimeoutMilliseconds,
            activities);
          try
          {
            connection.beginFetch((fetchStatus.sessionState == SESSIONSTATE_LOGIN)?FETCH_LOGIN:FETCH_STANDARD);
            try
            {
              // Execute the fetch!
              connection.executeFetch(url.getFile(),userAgent,from,
                false,hostName,formData,lc);
              int response = connection.getResponseCode();

              if (response == 200 || response == 302 || response == 301)
              {
                // If this was part of the login sequence, update the cookies regardless of what else happens
                if (fetchStatus.sessionState == SESSIONSTATE_LOGIN)
                {
                  // Update the cookies
                  LoginCookies lastFetchCookies = connection.getLastFetchCookies();
                  cookieManager.updateCookies(sessionCredential.getSequenceKey(),lastFetchCookies);
                }

                // Decide whether to exclude this document based on what we see here.
                // Basically, we want to get rid of everything that we (a) don't know what
                // to do with in the ingestion system, and (b) we can't get useful links from.

                String contentType = extractContentType(connection.getResponseHeader("Content-Type"));

                if (isContentInteresting(activities,currentURI,response,contentType))
                {
                  // Treat it as real, and cache it.
                  fetchStatus.checkSum = cache.addData(activities,currentURI,connection);
                  fetchStatus.headerData = connection.getResponseHeaders();
                  fetchStatus.resultSignal = RESULT_VERSION_NEEDED;
                  activityResultCode = null;
                }
                else
                {
                  fetchStatus.contextMessage = "it had the wrong content type ('"+contentType+"')";
                  fetchStatus.resultSignal = RESULT_NO_DOCUMENT;
                  activityResultCode = null;
                }
              }
              else
              {
                // We got some kind of http error code.
                // We don't want to remove it from the queue entirely, because that would cause us to lose track of the item, and therefore lose
                // control of all scheduling around it.  Instead, we leave it on the queue and give it an empty version string; that will lead it to be
                // reprocessed without fail on the next scheduled check.
                // Decode response body to the extent we can
                String contentType = extractContentType(connection.getResponseHeader("Content-Type"));
                String encoding = extractEncoding(contentType);
                if (encoding == null)
                  encoding = StandardCharsets.UTF_8.name();
                String decodedResponse = "undecodable";
                try
                {
                  decodedResponse = "'"+connection.getLimitedResponseBody(1024,encoding)+"'";
                }
                catch (ManifoldCFException e)
                {
                  // Eat this exception unless it is an interrupt
                  if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                    throw e;
                  connection.noteInterrupted(e);
                }
                catch (ServiceInterruption e)
                {
                  // Eat this exception too
                  connection.noteInterrupted(e);
                }
                fetchStatus.contextMessage = "it failed to fetch (status="+Integer.toString(response)+", message="+decodedResponse+")";
                fetchStatus.resultSignal = RESULT_NO_VERSION;
                activityResultCode = null;
              }
            }
            catch (ManifoldCFException e)
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
          String contentLink = null;
          if (fetchStatus.resultSignal == RESULT_VERSION_NEEDED)
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
              boolean seenContentError = false;
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
                    // A form overrides links, redirection, or content
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
                      // Link overrides redirection and content
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
                    else
                    {
                      // Look for the content in the page.  The link returned may be an empty string, if matching content
                      // is discovered but there is no override.  It will be null of the content is not found.
                      String newContentLink = findSpecifiedContent(currentURI,lp);
                      if (newContentLink != null)
                      {
                        if (contentLink != null)
                        {
                          seenContentError = true;
                          contentLink = null;
                        }
                        else if (!seenContentError && !seenRedirectionError && !seenLinkError && !seenFormError && formData == null && preferredLink == null && preferredRedirection == null)
                        {
                          contentLink = newContentLink;
                        }
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
              else if (contentLink != null)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("WEB: Document '"+currentURI+"' matches content, so determined to be login page for sequence '"+sessionCredential.getSequenceKey()+"'");
                isLoginPage = true;
              }
              else
              {
                if (seenAnything && Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("WEB: Document '"+currentURI+"' did not match expected form, link, redirection, or content for sequence '"+sessionCredential.getSequenceKey()+"'");
              }
            }

            // Should we do a state transition into the "logging in" state?
            if (fetchStatus.sessionState == SESSIONSTATE_NORMAL && isLoginPage)
            {
              // Entering the login sequence.  Make sure we actually can do this...
              if (activities.beginEventSequence(globalSequenceEvent))
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("WEB: For document '"+documentIdentifier+"', beginning login sequence '"+sessionCredential.getSequenceKey()+"'");

                activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_START,
                  null,sessionCredential.getSequenceKey(),"OK",null,null);

                // Transition to the right state, etc.
                fetchStatus.sessionState = SESSIONSTATE_LOGIN;
              }
              else
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("WEB: For document '"+documentIdentifier+"', login sequence '"+sessionCredential.getSequenceKey()+"' was already in progress.");

                // Didn't make it in.  Retry the main URI when the proper conditions are met.
                // We don't want the cached data anymore.
                cache.deleteData(currentURI);
                fetchStatus.contextMessage = "login sequence already in progress";
                fetchStatus.resultSignal = RESULT_RETRY_DOCUMENT;
                activityResultCode = null;
              }
            }
            else if (fetchStatus.sessionState == SESSIONSTATE_LOGIN && isLoginPage == false)
            {
              //== Exit login mode ==
              activities.completeEventSequence(globalSequenceEvent);
              activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                null,sessionCredential.getSequenceKey(),"OK",null,null);
              fetchStatus.sessionState = SESSIONSTATE_NORMAL;
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
            if (fetchStatus.resultSignal == RESULT_VERSION_NEEDED && fetchStatus.sessionState == SESSIONSTATE_LOGIN)
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
              else if (preferredRedirection != null)
                targetURI = preferredRedirection;
              else /* if (contentLink != null) */
                targetURI = contentLink;

              // Definitely we don't want the cached data anymore
              cache.deleteData(currentURI);

              // If the target URI is null, it means we could not find a suitable link.  If target URI is "",
              // it means that we found a designated logon page but the description did not include a link we
              // could chase.  Either way, treat this exactly the same
              // way as if the link found exited login mode.
              if (targetURI == null || targetURI.length() == 0)
              {
                //== Exiting login mode ==
                activities.completeEventSequence(globalSequenceEvent);
                activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                  null,sessionCredential.getSequenceKey(),"NEXTLINKNOTFOUND","Could not find a usable link to the next page: "+fetchStatus.contextMessage,null);
                fetchStatus.sessionState = SESSIONSTATE_NORMAL;
                // Make sure we go back and try the original document again, no matter where we got directed to
                currentURI = documentIdentifier;
              }
              else
              {
                currentURI = targetURI;
              }
              continue;
            }
            else if (fetchStatus.resultSignal != RESULT_VERSION_NEEDED && fetchStatus.sessionState == SESSIONSTATE_LOGIN)
            {
              // The next URL we fetched in the logon sequence turned out to be unsuitable.
              // That means that the logon sequence is fundamentally wrong.  The session thus ends,
              // and of course it will retry, but that's neither here nor there.
              //== Exiting login mode ==
              activities.completeEventSequence(globalSequenceEvent);
              activities.recordActivity(null,WebcrawlerConnector.ACTIVITY_LOGON_END,
                null,sessionCredential.getSequenceKey(),"LINKTARGETUNSUITABLE","Page was unsuitable for a login sequence because: "+fetchStatus.contextMessage,null);
              fetchStatus.sessionState = SESSIONSTATE_NORMAL;
              // Fall through, leaving everything else alone.
            }
          }

        }
        else if (robotsStatus == RESULTSTATUS_FALSE)
        {
          activityResultCode = "-11";
          fetchStatus.contextMessage = "robots.txt says so";
          fetchStatus.resultSignal = RESULT_NO_DOCUMENT;
        }
        else
        {
          // Robots prerequisite in progress
          activityResultCode = null;
          fetchStatus.resultSignal = RESULT_RETRY_DOCUMENT;
          fetchStatus.contextMessage = "robots prerequisite already in progress";
        }
      }
      else if (ipAddressStatus == RESULTSTATUS_FALSE)
      {
        activityResultCode = "-10";
        fetchStatus.contextMessage = "ip address not found";
        fetchStatus.resultSignal = RESULT_NO_DOCUMENT;
      }
      else
      {
        // DNS prerequisite in progress
        activityResultCode = null;
        fetchStatus.contextMessage = "dns prerequisite already in progress";
        fetchStatus.resultSignal = RESULT_RETRY_DOCUMENT;
      }
      
      // If we fail on a document that's not the primary, the result should be to retry the primary later.
      if (!currentURI.equals(documentIdentifier))
      {
        activityResultCode = null;
        if (fetchStatus.contextMessage != null)
          fetchStatus.contextMessage = "for login sequence url '"+currentURI+"': "+fetchStatus.contextMessage;
        if (fetchStatus.resultSignal != RESULT_VERSION_NEEDED)
          fetchStatus.resultSignal = RESULT_RETRY_DOCUMENT;
      }

      break;
    }

    // Now, look at the result signal, and set up the version appropriately.
    if (activityResultCode != null)
      activities.recordActivity(null,ACTIVITY_FETCH,null,documentIdentifier,activityResultCode,fetchStatus.contextMessage,null);
    
  }

  protected void processDocument(IProcessActivity activities, String documentIdentifier, String versionString,
    boolean indexDocument, Map<String,Set<String>> metaHash, String[] acls, DocumentURLFilter filter)
    throws ManifoldCFException, ServiceInterruption
  {
    // Consider this document for ingestion.
    String errorCode = null;
    String errorDesc = null;
    Long fileLengthLong = null;
    long startTime = System.currentTimeMillis();
    
    try
    {
      // We can exclude it if it does not seem to be a kind of document that the ingestion system knows
      // about.
      
      if (!indexDocument)
      {
        errorCode = "CONTENTNOTINDEXABLE";
        errorDesc = "Content not indexable";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }
      
      int responseCode = cache.getResponseCode(documentIdentifier);
      if (responseCode != 200)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because response code not indexable: "+responseCode);
        errorCode = "RESPONSECODENOTINDEXABLE";
        errorDesc = "HTTP response code not indexable ("+responseCode+")";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }

      long dataLength = cache.getDataLength(documentIdentifier);
      if (!activities.checkLengthIndexable(dataLength))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because pipeline thinks length "+dataLength+" is not acceptable");
        errorCode = activities.EXCLUDED_LENGTH;
        errorDesc = "Rejected due to length ("+dataLength+")";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }

      if (activities.checkURLIndexable(documentIdentifier) == false)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because output connector does not want URL");
        errorCode = activities.EXCLUDED_URL;
        errorDesc = "Rejected due to URL ('"+documentIdentifier+"')";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }

      String ingestURL = filter.isDocumentIndexable(documentIdentifier);
      if (ingestURL == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because document does not match web job constraints");
        errorCode = "JOBRESTRICTION";
        errorDesc = "Rejected because job excludes this URL ('"+documentIdentifier+"')";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }
      
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

      if (contentType != null)
      {
        int pos = contentType.indexOf(";");
        if (pos != -1)
          contentType = contentType.substring(0,pos);
        contentType = contentType.trim();
      }

      if (!activities.checkMimeTypeIndexable(contentType))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because output connector does not want mime type '"+contentType+"'");
        errorCode = activities.EXCLUDED_MIMETYPE;
        errorDesc = "Rejected because of mime type ("+contentType+")";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }

      if(!filter.isDocumentContentIndexable(documentIdentifier)){
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not indexing because document content matched document content exclusion rule");
        errorCode = activities.EXCLUDED_CONTENT;
        errorDesc = "Rejected due to content exclusion rule";
        activities.noDocument(documentIdentifier,versionString);
        return;
      }
      // Ingest the document
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("WEB: Decided to ingest '"+documentIdentifier+"'");

      RepositoryDocument rd = new RepositoryDocument();

      // Set the file name
      String fileName = "";
      try {
        fileName = documentIdentifiertoFileName(documentIdentifier);
      } catch (URISyntaxException e1) {
        fileName = "";
      }
      if (fileName.length() > 0){
        rd.setFileName(fileName);
      }
          
      // Set the content type
      String mimeType = cache.getContentType(documentIdentifier);
      if (mimeType != null)
        rd.setMimeType(mimeType);
          
      // Turn into acls and add into description
      String[] denyAcls;
      if (acls == null)
        denyAcls = null;
      else
      {
        if (acls.length > 0)
          denyAcls = new String[]{defaultAuthorityDenyToken};
        else
          denyAcls = new String[0];
      }
      
      if (acls != null && denyAcls != null)
        rd.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,acls,denyAcls);

      // Grab metadata
      for (String key : metaHash.keySet())
      {
        Set<String> metaList = metaHash.get(key);
        String[] values = new String[metaList.size()];
        int k = 0;
        for (String value : metaList)
        {
          values[k++] = value;
        }
        rd.addField(key,values);
      }

      InputStream is = cache.getData(documentIdentifier);

      if (is != null)
      {
        try
        {
          rd.setBinary(is,dataLength);
          try
          {
            activities.ingestDocumentWithException(documentIdentifier,versionString,ingestURL,rd);
            errorCode = "OK";
            fileLengthLong = new Long(dataLength);
          }
          catch (IOException e)
          {
            errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
            errorDesc = e.getMessage();
            handleIOException(e,"reading data");
          }
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
            errorDesc = e.getMessage();
            handleIOException(e,"closing stream");
          }
        }
      }
      else
        Logging.connectors.error("WEB: Expected a cached document for '"+documentIdentifier+"', but none present!");
      
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        errorCode = null;
      throw e;
    }
    finally
    {
      if (errorCode != null)
        activities.recordActivity(new Long(startTime),ACTIVITY_PROCESS,
          fileLengthLong,documentIdentifier,errorCode,errorDesc,null);
    }


  }
  
  protected static String extractContentType(String contentType)
  {
    // Some sites have multiple content types.  We just look at the LAST one in that case.
    if (contentType != null)
    {
      String[] contentTypes = contentType.split(",");
      if (contentTypes.length > 0)
        contentType = contentTypes[contentTypes.length-1].trim();
      else
        contentType = null;
    }
    return contentType;
  }

  protected static String extractEncoding(String contentType)
  {
    if (contentType == null)
      return null;
    int semiIndex = contentType.indexOf(";");
    if (semiIndex == -1)
      return null;
    String suffix = contentType.substring(semiIndex+1);
    suffix = suffix.trim();
    if (suffix.startsWith("charset="))
      return suffix.substring("charset=".length());
    return null;
  }
  
  protected static String extractMimeType(String contentType)
  {
    if (contentType == null)
      return null;
    int semiIndex = contentType.indexOf(";");
    if (semiIndex != -1)
      contentType = contentType.substring(0,semiIndex);
    contentType = contentType.trim();
    return contentType;
  }
  
  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if (e instanceof java.net.SocketException)
      throw new ManifoldCFException("Socket timeout error "+context+": "+e.getMessage(),e);
    else if (e instanceof ConnectTimeoutException)
      throw new ManifoldCFException("Socket connect timeout error "+context+": "+e.getMessage(),e);
    else if (e instanceof InterruptedIOException)
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    else
      throw new ManifoldCFException("IO error "+context+": "+e.getMessage(),e);
  }
  
  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    // The web in general does not batch well.  Multiple chunks have no advantage over one-at-a-time requests.
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
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Email"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Robots"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Bandwidth"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.AccessCredentials"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Certificates"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Proxy"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.email.value != \"\" && editconnection.email.value.indexOf(\"@\") == -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.NeedAValidEmailAddress")+"\");\n"+
"    editconnection.email.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  // If the Bandwidth tab is up, check to be sure we have valid numbers and regexps everywhere.\n"+
"  var i = 0;\n"+
"  var count = editconnection.bandwidth_count.value;\n"+
"  while (i < count)\n"+
"  {\n"+
"    var connections = eval(\"editconnection.connections_bandwidth_\"+i+\".value\");\n"+
"    if (connections != \"\" && !isInteger(connections))\n"+
"    {\n"+
"      alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumConnectionsMustBeAnInteger")+"\");\n"+
"      eval(\"editconnection.connections_bandwidth_\"+i+\".focus()\");\n"+
"      return false;\n"+
"    }\n"+
"    var rate = eval(\"editconnection.rate_bandwidth_\"+i+\".value\");\n"+
"    if (rate != \"\" && !isInteger(rate))\n"+
"    {\n"+
"      alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumKbytesPerSecondMustBeAnInteger")+"\");\n"+
"      eval(\"editconnection.rate_bandwidth_\"+i+\".focus()\");\n"+
"      return false;\n"+
"    }\n"+
"    var fetches = eval(\"editconnection.fetches_bandwidth_\"+i+\".value\");\n"+
"    if (fetches != \"\" && !isInteger(fetches))\n"+
"    {\n"+
"      alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumFetchesPerMinuteMustBeAnInteger")+"\");\n"+
"      eval(\"editconnection.fetches_bandwidth_\"+i+\".focus()\");\n"+
"      return false;\n"+
"    }\n"+
"\n"+
"    i = i + 1;\n"+
"  }\n"+
"    \n"+
"  // Make sure access credentials are all legal\n"+
"  i = 0;\n"+
"  count = editconnection.acredential_count.value;\n"+
"  while (i < count)\n"+
"  {\n"+
"    var username = eval(\"editconnection.username_acredential_\"+i+\".value\");\n"+
"    if (username == \"\")\n"+
"    {\n"+
"      alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.CredentialMustHaveNonNullUserName")+"\");\n"+
"      eval(\"editconnection.username_acredential_\"+i+\".focus()\");\n"+
"      return false;\n"+
"    }\n"+
"    i = i + 1;\n"+
"  }\n"+
"\n"+
"  // Make sure session credentials are all legal\n"+
"  i = 0;\n"+
"  count = editconnection.scredential_count.value;\n"+
"  while (i < count)\n"+
"  {\n"+
"    var loginpagecount = eval(\"editconnection.scredential_\"+i+\"_loginpagecount.value\");\n"+
"    var j = 0;\n"+
"    while (j < loginpagecount)\n"+
"    {\n"+
"      var matchregexp = eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_matchregexp.value\");\n"+
"      if (!isRegularExpression(matchregexp))\n"+
"      {\n"+
"        alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MatchExpressionMustBeAValidRegularExpression")+"\");\n"+
"        eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_matchregexp.focus()\");\n"+
"        return false;\n"+
"      }\n"+
"      if (eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_type.value\") == \"form\")\n"+
"      {\n"+
"        var paramcount = eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_loginparamcount.value\");\n"+
"        var k = 0;\n"+
"        while (k < paramcount)\n"+
"        {\n"+
"          var paramname = eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_\"+k+\"_param.value\");\n"+
"          if (paramname == \"\")\n"+
"          {\n"+
"            alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.ParameterMustHaveNonEmptyName")+"\");\n"+
"            eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_\"+k+\"_param.focus()\");\n"+
"            return false;\n"+
"          }\n"+
"          var paramvalue = eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_\"+k+\"_value.value\");\n"+
"          var parampassword = eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_\"+k+\"_password.value\");\n"+
"          if (paramvalue != \"\" && parampassword != \"\")\n"+
"          {\n"+
"            alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.ParameterCanEitherBeHidden")+"\");\n"+
"            eval(\"editconnection.scredential_\"+i+\"_\"+j+\"_\"+k+\"_value.focus()\");\n"+
"            return false;\n"+
"          }\n"+
"          k = k + 1;\n"+
"        }\n"+
"      }\n"+
"      j = j + 1;\n"+
"    }\n"+
"    i = i + 1;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.email.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.EmailAaddressRequired") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.Email") + "\");\n"+
"    editconnection.email.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function deleteRegexp(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.op_bandwidth_\"+i+\".value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.bandwidth_count.value==i)\n"+
"    postFormSetAnchor(\"bandwidth\");\n"+
"  else\n"+
"    postFormSetAnchor(\"bandwidth_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.op_bandwidth_\"+i+\".value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addRegexp()\n"+
"{\n"+
"  if (editconnection.connections_bandwidth.value != \"\" && !isInteger(editconnection.connections_bandwidth.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumConnectionsMustBeAnInteger")+"\");\n"+
"    editconnection.connections_bandwidth.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (editconnection.rate_bandwidth.value != \"\" && !isInteger(editconnection.rate_bandwidth.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumKbytesPerSecondMustBeAnInteger")+"\");\n"+
"    editconnection.rate_bandwidth.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (editconnection.fetches_bandwidth.value != \"\" && !isInteger(editconnection.fetches_bandwidth.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MaximumFetchesPerMinuteMustBeAnInteger")+"\");\n"+
"    editconnection.fetches_bandwidth.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editconnection.regexp_bandwidth.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    editconnection.regexp_bandwidth.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.bandwidth_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"bandwidth\");\n"+
"}\n"+
"\n"+
"function deleteARegexp(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.op_acredential_\"+i+\".value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.acredential_count.value==i)\n"+
"    postFormSetAnchor(\"acredential\");\n"+
"  else\n"+
"    postFormSetAnchor(\"acredential_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.op_acredential_\"+i+\".value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addARegexp()\n"+
"{\n"+
"  if (editconnection.username_acredential.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.CredentialMustIncludeANonNullUserName")+"\");\n"+
"    editconnection.username_acredential.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editconnection.regexp_acredential.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    editconnection.regexp_acredential.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.acredential_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"acredential\");\n"+
"}\n"+
"\n"+
"function deleteSRegexp(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.scredential_\"+i+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.scredential_count.value==i)\n"+
"    postFormSetAnchor(\"scredential\");\n"+
"  else\n"+
"    postFormSetAnchor(\"scredential_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.scredential_\"+i+\"_op.value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addSRegexp()\n"+
"{\n"+
"  if (!isRegularExpression(editconnection.scredential_regexp.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    editconnection.scredential_regexp.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.scredential_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"scredential\");\n"+
"}\n"+
"\n"+
"function deleteLoginPage(credential,loginpage)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (eval(\"editconnection.scredential_\"+credential+\"_loginpagecount.value\")==credential)\n"+
"    postFormSetAnchor(\"scredential_loginpage\");\n"+
"  else\n"+
"    postFormSetAnchor(\"scredential_\"+credential+\"_\"+loginpage)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_op.value=\\\"Continue\\\"\");\n"+
"\n"+
"}\n"+
"  \n"+
"function addLoginPage(credential)\n"+
"{\n"+
"  if (!isRegularExpression(eval(\"editconnection.scredential_\"+credential+\"_loginpageregexp.value\")))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    eval(\"editconnection.scredential_\"+credential+\"_loginpageregexp.focus()\");\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(eval(\"editconnection.scredential_\"+credential+\"_loginpagematchregexp.value\")))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    eval(\"editconnection.scredential_\"+credential+\"_loginpagematchregexp.focus()\");\n"+
"    return;\n"+
"  }\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_loginpageop.value=\\\"Add\\\"\");\n"+
"  postFormSetAnchor(\"scredential_\"+credential);\n"+
"}\n"+
"  \n"+
"function deleteLoginPageParameter(credential,loginpage,parameter)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_\"+parameter+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamcount.value\")==credential)\n"+
"    postFormSetAnchor(\"scredential_\"+credential+\"_loginparam\");\n"+
"  else\n"+
"    postFormSetAnchor(\"scredential_\"+credential+\"_\"+loginpage+\"_\"+parameter)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_\"+parameter+\"_op.value=\\\"Continue\\\"\");\n"+
"}\n"+
"  \n"+
"function addLoginPageParameter(credential,loginpage)\n"+
"{\n"+
"  if (!isRegularExpression(eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamname.value\")))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.ParameterNameMustBeARegularExpression")+"\");\n"+
"    eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamname.focus()\");\n"+
"    return;\n"+
"  }\n"+
"  if (eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamvalue.value\") != \"\" && eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparampassword.value\") != \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.ParameterCanEitherBeHidden")+"\");\n"+
"    eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamvalue.focus()\");\n"+
"    return;\n"+
"  }\n"+
"  eval(\"editconnection.scredential_\"+credential+\"_\"+loginpage+\"_loginparamop.value=\\\"Add\\\"\");\n"+
"  postFormSetAnchor(\"scredential_\"+credential+\"_\"+loginpage);\n"+
"}\n"+
"  \n"+
"function deleteTRegexp(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.op_trust_\"+i+\".value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.trust_count.value==i)\n"+
"    postFormSetAnchor(\"trust\");\n"+
"  else\n"+
"    postFormSetAnchor(\"trust_\"+i);\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.op_trust_\"+i+\".value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addTRegexp()\n"+
"{\n"+
"  if (editconnection.certificate_trust.value == \"\" && editconnection.all_trust.checked == false)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.SpecifyATrustCertificateFileToUploadFirst")+"\");\n"+
"    editconnection.certificate_trust.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editconnection.regexp_trust.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.AValidRegularExpressionIsRequired")+"\");\n"+
"    editconnection.regexp_trust.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.trust_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"trust\");\n"+
"}\n"+
"  \n"+
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
    
    String email = parameters.getParameter(WebcrawlerConfig.PARAMETER_EMAIL);
    if (email == null)
      email = "";
    String robotsUsage = parameters.getParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE);
    if (robotsUsage == null)
      robotsUsage = "all";
    String proxyHost = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYHOST);
    if (proxyHost == null)
      proxyHost = "";
    String proxyPort = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYPORT);
    if (proxyPort == null)
      proxyPort = "";
    String proxyAuthDomain = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHDOMAIN);
    if (proxyAuthDomain == null)
      proxyAuthDomain = "";
    String proxyAuthUsername = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHUSERNAME);
    if (proxyAuthUsername == null)
      proxyAuthUsername = "";
    String proxyAuthPassword = parameters.getObfuscatedParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHPASSWORD);
    if (proxyAuthPassword == null)
      proxyAuthPassword = "";
    else
      proxyAuthPassword = out.mapPasswordToKey(proxyAuthPassword);

    // Proxy tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Proxy")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyHostColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"40\" name=\"proxyhost\" value=\""+Encoder.attributeEscape(proxyHost)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyPortColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"proxyport\" value=\""+Encoder.attributeEscape(proxyPort)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyAuthenticationDomainColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"proxyauthdomain\" value=\""+Encoder.attributeEscape(proxyAuthDomain)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyAuthenticationUserNameColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"proxyauthusername\" value=\""+Encoder.attributeEscape(proxyAuthUsername)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyAuthenticationPasswordColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"16\" name=\"proxyauthpassword\" value=\""+Encoder.attributeEscape(proxyAuthPassword)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"proxyhost\" value=\""+Encoder.attributeEscape(proxyHost)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyport\" value=\""+Encoder.attributeEscape(proxyPort)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyauthusername\" value=\""+Encoder.attributeEscape(proxyAuthUsername)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyauthdomain\" value=\""+Encoder.attributeEscape(proxyAuthDomain)+"\"/>\n"+
"<input type=\"hidden\" name=\"proxyauthpassword\" value=\""+Encoder.attributeEscape(proxyAuthPassword)+"\"/>\n"
      );
    }

    // Email tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Email")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.EmailAddressToContact") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"32\" name=\"email\" value=\""+Encoder.attributeEscape(email)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"email\" value=\""+Encoder.attributeEscape(email)+"\"/>\n"
      );
    }

    // Robots tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Robots")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RobotsTxtUsage") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"robotsusage\" size=\"3\">\n"+
"        <option value=\"none\" "+(robotsUsage.equals("none")?"selected=\"selected\"":"")+">" + Messages.getBodyString(locale,"WebcrawlerConnector.DontLookAtRobotsTxt") + "</option>\n"+
"        <option value=\"data\" "+(robotsUsage.equals("data")?"selected=\"selected\"":"")+">" + Messages.getBodyString(locale,"WebcrawlerConnector.ObeyRobotsTxtForDataFetchesOnly") + "</option>\n"+
"        <option value=\"all\" "+(robotsUsage.equals("all")?"selected=\"selected\"":"")+">" + Messages.getBodyString(locale,"WebcrawlerConnector.ObeyRobotsTxtForAllFetches") + "</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"robotsusage\" value=\""+robotsUsage+"\"/>\n"
      );
    }

    // Bandwidth tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Bandwidth")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Throttles") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.BinRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.CaseInsensitive") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.MaxConnections") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.MaxKbytesSec") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.MaxFetchesMin") + "</nobr></td>\n"+
"        </tr>\n"
      );
      int i = 0;
      int binCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_BINDESC))
        {
          // A bin description node!  Look for all its parameters.
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_BINREGEXP);
          String isCaseInsensitive = cn.getAttributeValue(WebcrawlerConfig.ATTR_INSENSITIVE);
          String maxConnections = null;
          String maxKBPerSecond = null;
          String maxFetchesPerMinute = null;
          int j = 0;
          while (j < cn.getChildCount())
          {
            ConfigNode childNode = cn.getChild(j++);
            if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXCONNECTIONS))
              maxConnections = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
            else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXKBPERSECOND))
              maxKBPerSecond = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
            else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
              maxFetchesPerMinute = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
          }
          if (maxConnections == null)
            maxConnections = "";
          if (maxKBPerSecond == null)
            maxKBPerSecond = "";
          if (maxFetchesPerMinute == null)
            maxFetchesPerMinute = "";
            
          if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
            isCaseInsensitive = "false";

          // It's prefix will be...
          String prefix = "bandwidth_" + Integer.toString(binCounter);
          out.print(
"        <tr class=\""+(((binCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteBinRegularExpression")+Integer.toString(binCounter+1)+"\" onclick='javascript:deleteRegexp("+Integer.toString(binCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+"op_"+prefix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(regexp)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"checkbox\" name=\"insensitive_"+prefix+"\" value=\"true\" "+(isCaseInsensitive.equals("true")?"checked=\"\"":"")+" /></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"connections_"+prefix+"\" value=\""+maxConnections+"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"rate_"+prefix+"\" value=\""+maxKBPerSecond+"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"fetches_"+prefix+"\" value=\""+maxFetchesPerMinute+"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          binCounter++;
        }
      }

      // If it looks like this is a brand-new configuration, add in a default throttle.
      // This only works because other nodes must get created on the first post, and cannot then be deleted.
      if (parameters.getChildCount() == 0)
      {
        // It's prefix will be...
        String prefix = "bandwidth_" + Integer.toString(binCounter);
        out.print(
"        <tr class=\""+(((binCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" alt=\""+ Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteBinRegularExpression") +Integer.toString(binCounter+1)+"\" onclick='javascript:deleteRegexp("+Integer.toString(binCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+"op_"+prefix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"checkbox\" name=\"insensitive_"+prefix+"\" value=\"false\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"connections_"+prefix+"\" value=\"2\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"rate_"+prefix+"\" value=\"64\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"fetches_"+prefix+"\" value=\"12\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        binCounter++;
      }

      if (binCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"6\">"+Messages.getBodyString(locale,"WebcrawlerConnector.NoBandwidthOrConnectionThrottlingSpecified")+"</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"6\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"bandwidth\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.AddBinRegularExpression") + "\" onclick=\"javascript:addRegexp();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"bandwidth_count\" value=\""+binCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"bandwidth_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"regexp_bandwidth\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"checkbox\" name=\"insensitive_bandwidth\" value=\"true\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"connections_bandwidth\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"rate_bandwidth\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"fetches_bandwidth\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for bandwidth tab.
      int i = 0;
      int binCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_BINDESC))
        {
          // A bin description node!  Look for all its parameters.
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_BINREGEXP);
          String isCaseInsensitive = cn.getAttributeValue(WebcrawlerConfig.ATTR_INSENSITIVE);
          String maxConnections = null;
          String maxKBPerSecond = null;
          String maxFetchesPerMinute = null;
          int j = 0;
          while (j < cn.getChildCount())
          {
            ConfigNode childNode = cn.getChild(j++);
            if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXCONNECTIONS))
              maxConnections = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
            else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXKBPERSECOND))
              maxKBPerSecond = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
            else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
              maxFetchesPerMinute = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
          }
          if (maxConnections == null)
            maxConnections = "";
          if (maxKBPerSecond == null)
            maxKBPerSecond = "";
          if (maxFetchesPerMinute == null)
            maxFetchesPerMinute = "";
          if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
            isCaseInsensitive = "false";

          // It's prefix will be...
          String prefix = "bandwidth_" + Integer.toString(binCounter);
          out.print(
"<input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"insensitive_"+prefix+"\" value=\""+isCaseInsensitive+"\"/>\n"+
"<input type=\"hidden\" name=\""+"connections_"+prefix+"\" value=\""+maxConnections+"\"/>\n"+
"<input type=\"hidden\" name=\""+"rate_"+prefix+"\" value=\""+maxKBPerSecond+"\"/>\n"+
"<input type=\"hidden\" name=\""+"fetches_"+prefix+"\" value=\""+maxFetchesPerMinute+"\"/>\n"
          );
          binCounter++;
        }
      }

      // If it looks like this is a brand-new configuration, add in a default throttle.
      // This only works because other nodes must get created on the first post, and cannot then be deleted.
      if (parameters.getChildCount() == 0)
      {
        // It's prefix will be...
        String prefix = "bandwidth_" + Integer.toString(binCounter);
        out.print(
"<input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\"\"/>\n"+
"<input type=\"hidden\" name=\""+"insensitive_"+prefix+"\" value=\"false\"/>\n"+
"<input type=\"hidden\" name=\""+"connections_"+prefix+"\" value=\"2\"/>\n"+
"<input type=\"hidden\" name=\""+"rate_"+prefix+"\" value=\"64\"/>\n"+
"<input type=\"hidden\" name=\""+"fetches_"+prefix+"\" value=\"12\"/>\n"
        );
        binCounter++;
      }

      out.print(
"<input type=\"hidden\" name=\"bandwidth_count\" value=\""+binCounter+"\"/>\n"
      );
    }

    // Access Credentials tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.AccessCredentials")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.PageAccessCredentials") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.AuthenticationType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Domain") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.UserName") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Password") + "</nobr></td>\n"+
"        </tr>\n"
      );
      int i = 0;
      int accessCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
        {
          // A bin description node!  Look for all its parameters.
          String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
          if (!type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          {
            String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
            String domain = cn.getAttributeValue(WebcrawlerConfig.ATTR_DOMAIN);
            if (domain == null)
              domain = "";
            String userName = cn.getAttributeValue(WebcrawlerConfig.ATTR_USERNAME);
            String password = out.mapPasswordToKey(ManifoldCF.deobfuscate(cn.getAttributeValue(WebcrawlerConfig.ATTR_PASSWORD)));
                                        
            // It's prefix will be...
            String prefix = "acredential_" + Integer.toString(accessCounter);
            out.print(
"        <tr class=\""+(((accessCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeletePageAuthenticationUrlRegularExpression")+Integer.toString(accessCounter+1)+"\" onclick='javascript:deleteARegexp("+Integer.toString(accessCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\"op_"+prefix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(regexp)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"radio\" name=\"type_"+prefix+"\" value=\"basic\" "+(type.equals("basic")?"checked=\"\"":"")+" />&nbsp;" + Messages.getBodyString(locale,"WebcrawlerConnector.BasicAuthentication") + "</nobr><br/>\n"+
"            <nobr><input type=\"radio\" name=\"type_"+prefix+"\" value=\"ntlm\" "+(type.equals("ntlm")?"checked=\"\"":"")+" />&nbsp;" + Messages.getBodyString(locale,"WebcrawlerConnector.NTLMAuthentication") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"16\" name=\""+"domain_"+prefix+"\" value=\""+Encoder.attributeEscape(domain)+"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"16\" name=\""+"username_"+prefix+"\" value=\""+Encoder.attributeEscape(userName)+"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"password\" size=\"16\" name=\""+"password_"+prefix+"\" value=\""+Encoder.attributeEscape(password)+"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"
            );
            accessCounter++;
          }
        }
      }

      if (accessCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"6\">" + Messages.getBodyString(locale,"WebcrawlerConnector.NoPageAccessCredentialsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"6\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"acredential\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.AddPageAuthenticationUrlRegularExpression") + "\" onclick=\"javascript:addARegexp();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"acredential_count\" value=\""+accessCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"acredential_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"regexp_acredential\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"radio\" name=\"type_acredential\" value=\"basic\" checked=\"\" />&nbsp;" + Messages.getBodyString(locale,"WebcrawlerConnector.BasicAuthentication") + "</nobr><br/>\n"+
"            <nobr><input type=\"radio\" name=\"type_acredential\" value=\"ntlm\" />&nbsp;" + Messages.getBodyString(locale,"WebcrawlerConnector.NTLMAuthentication") + "</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"16\" name=\"domain_acredential\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"16\" name=\"username_acredential\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"password\" size=\"16\" name=\"password_acredential\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"        \n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.SessionBasedAccessCredentials") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.LoginPages") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      accessCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
        {
          // A bin description node!  Look for all its parameters.
          String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
          if (type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          {
            String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
                                        
            // It's prefix will be...
            String prefix = "scredential_" + Integer.toString(accessCounter);
            out.print(
"        <tr class=\""+(((accessCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteSessionAuthenticationUrlRegularExpression")+Integer.toString(accessCounter+1)+"\" onclick='javascript:deleteSRegexp("+Integer.toString(accessCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op"+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_regexp"+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(regexp)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.LoginURLRegularExpression") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.PageType") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.FormNamelinkTargetRegularExpression") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.OverrideTargetURL") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.OverrideFormParameters") + "</nobr></td>\n"+
"              </tr>\n"
            );
            int q = 0;
            int authPageCounter = 0;
            while (q < cn.getChildCount())
            {
              ConfigNode authPageNode = cn.getChild(q++);
              if (authPageNode.getType().equals(WebcrawlerConfig.NODE_AUTHPAGE))
              {
                String pageRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
                String pageType = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
                String matchRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_MATCHREGEXP);
                if (matchRegexp == null)
                  matchRegexp = "";
                String overrideTargetURL = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_OVERRIDETARGETURL);
                if (overrideTargetURL == null)
                  overrideTargetURL = "";
                String authpagePrefix = prefix + "_" + authPageCounter;
                out.print(
"              <tr class=\""+(((authPageCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+authpagePrefix+"\">\n"+
"                    <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteLoginPage")+(authPageCounter+1)+" for url regular expression #"+Integer.toString(accessCounter+1)+"\" onclick='javascript:deleteLoginPage("+Integer.toString(accessCounter)+","+Integer.toString(authPageCounter)+");'/>\n"+
"                    <input type=\"hidden\" name=\""+authpagePrefix+"_op"+"\" value=\"Continue\"/>\n"+
"                    <input type=\"hidden\" name=\""+authpagePrefix+"_regexp"+"\" value=\""+Encoder.attributeEscape(pageRegexp)+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+authpagePrefix+"_type"+"\" value=\""+pageType+"\"/>\n"+
"                  </a>\n"+
"                </td>\n"+
"\n"+
"                <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(pageRegexp)+"</nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr>"+pageType+"</nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr><input type=\"text\" size=\"30\" name=\""+authpagePrefix+"_matchregexp"+"\" value=\""+Encoder.attributeEscape(matchRegexp)+"\"/></nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr><input type=\"text\" size=\"30\" name=\""+authpagePrefix+"_overridetargeturl"+"\" value=\""+Encoder.attributeEscape(overrideTargetURL)+"\"/></nobr></td>\n"
                );
                if (pageType.equals(WebcrawlerConfig.ATTRVALUE_FORM))
                {
                  out.print(
"                <td class=\"boxcell\">\n"+
"                  <table class=\"formtable\">\n"+
"                    <tr class=\"formheaderrow\">\n"+
"                      <td class=\"formcolumnheader\"></td>\n"+
"                      <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.ParameterRegularExpression")+"</nobr></td>\n"+
"                      <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.Value")+"</nobr></td>\n"+
"                      <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.Password")+"</nobr></td>\n"+
"                    </tr>\n"
                  );
                  int z = 0;
                  int paramCounter = 0;
                  while (z < authPageNode.getChildCount())
                  {
                    ConfigNode paramNode = authPageNode.getChild(z++);
                    if (paramNode.getType().equals(WebcrawlerConfig.NODE_AUTHPARAMETER))
                    {
                      String param = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_NAMEREGEXP);
                      if (param == null)
                        param = "";
                      String value = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
                      if (value == null)
                        value = "";
                      String password = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_PASSWORD);
                      if (password == null)
                        password = "";
                      else
                        password = out.mapPasswordToKey(ManifoldCF.deobfuscate(password));
                      String authParamPrefix = authpagePrefix + "_" + paramCounter;
                      out.print(
"                    <tr class=\""+(((paramCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <a name=\""+authParamPrefix+"\">\n"+
"                          <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteParameter")+(paramCounter+1)+Messages.getAttributeString(locale,"WebcrawlerConnector.ForLoginPage")+(authPageCounter+1)+Messages.getAttributeString(locale,"WebcrawlerConnector.ForCredential")+(accessCounter+1)+"\" onclick='javascript:deleteLoginPageParameter("+accessCounter+","+authPageCounter+","+paramCounter+");'/>\n"+
"                          <input type=\"hidden\" name=\""+authParamPrefix+"_op"+"\" value=\"Continue\"/>\n"+
"                        </a>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"text\" size=\"30\" name=\""+authParamPrefix+"_param"+"\" value=\""+Encoder.attributeEscape(param)+"\"/></nobr>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"text\" size=\"15\" name=\""+authParamPrefix+"_value"+"\" value=\""+Encoder.attributeEscape(value)+"\"/></nobr>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"password\" size=\"15\" name=\""+authParamPrefix+"_password"+"\" value=\""+Encoder.attributeEscape(password)+"\"/></nobr>\n"+
"                      </td>\n"+
"                    </tr>\n"
                      );
                      paramCounter++;
                    }
                  }
                  out.print(
"                    <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"                    <tr class=\"formrow\">\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <a name=\""+authpagePrefix+"_loginparam"+"\">\n"+
"                          <input type=\"button\" value=\"Add\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.AddParameterToLoginPage")+(authPageCounter+1)+Messages.getAttributeString(locale,"WebcrawlerConnector.ForCredential")+(accessCounter+1)+"\" onclick='javascript:addLoginPageParameter("+accessCounter+","+authPageCounter+");'/>\n"+
"                        </a>\n"+
"                        <input type=\"hidden\" name=\""+authpagePrefix+"_loginparamcount"+"\" value=\""+paramCounter+"\"/>\n"+
"                        <input type=\"hidden\" name=\""+authpagePrefix+"_loginparamop"+"\" value=\"Continue\"/>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"text\" size=\"30\" name=\""+authpagePrefix+"_loginparamname"+"\" value=\"\"/></nobr>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"text\" size=\"15\" name=\""+authpagePrefix+"_loginparamvalue"+"\" value=\"\"/></nobr>\n"+
"                      </td>\n"+
"                      <td class=\"formcolumncell\">\n"+
"                        <nobr><input type=\"password\" size=\"15\" name=\""+authpagePrefix+"_loginparampassword"+"\" value=\"\"/></nobr>\n"+
"                      </td>\n"+
"                    </tr>\n"+
"                  </table>\n"+
"                </td>\n"
                  );
                }
                else
                {
                  out.print(
"                <td class=\"formcolumncell\"></td>\n"
                  );
                }
                out.print(
"              </tr>\n"
                );
                authPageCounter++;
              }
            }
            out.print(
"              <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"6\"><hr/></td></tr>\n"+
"              <tr class=\"formrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+prefix+"_loginpage"+"\">\n"+
"                    <input type=\"button\" value=\"Add\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.AddLoginPageToCredential")+(accessCounter+1)+"\" onclick='javascript:addLoginPage("+accessCounter+");'/>\n"+
"                  </a>\n"+
"                  <input type=\"hidden\" name=\""+prefix+"_loginpagecount"+"\" value=\""+authPageCounter+"\"/>\n"+
"                  <input type=\"hidden\" name=\""+prefix+"_loginpageop"+"\" value=\"Continue\"/>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr><input type=\"text\" size=\"30\" name=\""+prefix+"_loginpageregexp"+"\" value=\"\"/></nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr><input type=\"radio\" name=\""+prefix+"_loginpagetype"+"\" value=\""+WebcrawlerConfig.ATTRVALUE_FORM+"\" checked=\"\"/>"+Messages.getBodyString(locale,"WebcrawlerConnector.FormName")+"</nobr><br/>\n"+
"                  <nobr><input type=\"radio\" name=\""+prefix+"_loginpagetype"+"\" value=\""+WebcrawlerConfig.ATTRVALUE_LINK+"\"/>"+Messages.getBodyString(locale,"WebcrawlerConnector.LinkTarget")+"</nobr>\n"+
"                  <nobr><input type=\"radio\" name=\""+prefix+"_loginpagetype"+"\" value=\""+WebcrawlerConfig.ATTRVALUE_REDIRECTION+"\"/>"+Messages.getBodyString(locale,"WebcrawlerConnector.RedirectionTo")+"</nobr>\n"+
"                  <nobr><input type=\"radio\" name=\""+prefix+"_loginpagetype"+"\" value=\""+WebcrawlerConfig.ATTRVALUE_CONTENT+"\"/>"+Messages.getBodyString(locale,"WebcrawlerConnector.PageContent")+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr><input type=\"text\" size=\"30\" name=\""+prefix+"_loginpagematchregexp"+"\" value=\"\"/></nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr><input type=\"text\" size=\"30\" name=\""+prefix+"_loginpageoverridetargeturl"+"\" value=\"\"/></nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                </td>\n"+
"              </tr>\n"+
"\n"+
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"
            );
            accessCounter++;
          }
        }
      }

      if (accessCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"WebcrawlerConnector.NoSessionBasedAccessCredentialsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"scredential\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.AddSessionAuthenticationUrlRegularExpression")+"\" onclick=\"javascript:addSRegexp();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"scredential_count\" value=\""+accessCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"scredential_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"scredential_regexp\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Access Credentials tab.
      
      // Page credentials first.
      int i = 0;
      int accessCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
        {
          // A bin description node!  Look for all its parameters.
          String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
          if (!type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          {
            String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
            String domain = cn.getAttributeValue(WebcrawlerConfig.ATTR_DOMAIN);
            if (domain == null)
              domain = "";
            String userName = cn.getAttributeValue(WebcrawlerConfig.ATTR_USERNAME);
            String password = out.mapPasswordToKey(ManifoldCF.deobfuscate(cn.getAttributeValue(WebcrawlerConfig.ATTR_PASSWORD)));

            // It's prefix will be...
            String prefix = "acredential_" + Integer.toString(accessCounter);
            out.print(
"<input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"type_"+prefix+"\" value=\""+type+"\"/>\n"+
"<input type=\"hidden\" name=\""+"domain_"+prefix+"\" value=\""+Encoder.attributeEscape(domain)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"username_"+prefix+"\" value=\""+Encoder.attributeEscape(userName)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"password_"+prefix+"\" value=\""+Encoder.attributeEscape(password)+"\"/>\n"
            );
            accessCounter++;
          }
        }
      }
      out.print(
"<input type=\"hidden\" name=\"acredential_count\" value=\""+accessCounter+"\"/>\n"
      );

      // Now, session credentials
      i = 0;
      accessCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
        {
          // A bin description node!  Look for all its parameters.
          String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
          if (type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          {
            String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
            // It's identifier will be...
            String prefix = "scredential_" + Integer.toString(accessCounter);
            out.print(
"<input type=\"hidden\" name=\""+prefix+"_regexp"+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"
            );
            // Loop through login pages...
            int q = 0;
            int authPageCounter = 0;
            while (q < cn.getChildCount())
            {
              ConfigNode authPageNode = cn.getChild(q++);
              if (authPageNode.getType().equals(WebcrawlerConfig.NODE_AUTHPAGE))
              {
                String pageRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
                String pageType = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
                String matchRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_MATCHREGEXP);
                if (matchRegexp == null)
                  matchRegexp = "";
                String overrideTargetURL = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_OVERRIDETARGETURL);
                if (overrideTargetURL == null)
                  overrideTargetURL = "";
                String authpagePrefix = prefix + "_" + authPageCounter;
                out.print(
"<input type=\"hidden\" name=\""+authpagePrefix+"_regexp"+"\" value=\""+Encoder.attributeEscape(pageRegexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+authpagePrefix+"_type"+"\" value=\""+pageType+"\"/>\n"+
"<input type=\"hidden\" name=\""+authpagePrefix+"_matchregexp"+"\" value=\""+Encoder.attributeEscape(matchRegexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+authpagePrefix+"_overridetargeturl"+"\" value=\""+Encoder.attributeEscape(overrideTargetURL)+"\"/>\n"
                );
                if (pageType.equals(WebcrawlerConfig.ATTRVALUE_FORM))
                {
                  int z = 0;
                  int paramCounter = 0;
                  while (z < authPageNode.getChildCount())
                  {
                    ConfigNode paramNode = authPageNode.getChild(z++);
                    if (paramNode.getType().equals(WebcrawlerConfig.NODE_AUTHPARAMETER))
                    {
                      String param = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_NAMEREGEXP);
                      if (param == null)
                        param = "";
                      String value = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
                      if (value == null)
                        value = "";
                      String password = paramNode.getAttributeValue(WebcrawlerConfig.ATTR_PASSWORD);
                      if (password == null)
                        password = "";
                      else
                        password = out.mapPasswordToKey(ManifoldCF.deobfuscate(password));
                      String authParamPrefix = authpagePrefix + "_" + paramCounter;
                      out.print(
"<input type=\"hidden\" name=\""+authParamPrefix+"_param"+"\" value=\""+Encoder.attributeEscape(param)+"\"/>\n"+
"<input type=\"hidden\" name=\""+authParamPrefix+"_value"+"\" value=\""+Encoder.attributeEscape(value)+"\"/>\n"+
"<input type=\"hidden\" name=\""+authParamPrefix+"_password"+"\" value=\""+Encoder.attributeEscape(password)+"\"/>\n"
                      );
                      paramCounter++;
                    }
                  }
                  out.print(
"<input type=\"hidden\" name=\""+authpagePrefix+"_loginparamcount"+"\" value=\""+paramCounter+"\"/>\n"
                  );
                }
                authPageCounter++;
              }
            }
            out.print(
"<input type=\"hidden\" name=\""+prefix+"_loginpagecount"+"\" value=\""+authPageCounter+"\"/>\n"
            );
            accessCounter++;
          }
        }
      }
      out.print(
"<input type=\"hidden\" name=\"scredential_count\" value=\""+accessCounter+"\"/>\n"
      );
    }

    // "Certificates" tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Certificates")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.TrustCertificates") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Certificate") + "</nobr></td>\n"+
"        </tr>\n"
      );
      int i = 0;
      int trustsCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_TRUST))
        {
          // It's prefix will be...
          String prefix = "trust_" + Integer.toString(trustsCounter);
          // A bin description node!  Look for all its parameters.
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
          String trustEverything = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
          if (trustEverything != null && trustEverything.equals("true"))
          {
            // We trust everything that matches this regexp
            out.print(
"        <tr class=\""+(((trustsCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\"><input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteTrustUrlRegularExpression")+Integer.toString(trustsCounter+1)+"\" onclick='javascript:deleteTRegexp("+Integer.toString(trustsCounter)+");'/>\n"+
"            <input type=\"hidden\" name=\""+"op_"+prefix+"\" value=\"Continue\"/>\n"+
"            <input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"            <input type=\"hidden\" name=\""+"trustall_"+prefix+"\" value=\"true\"/>\n"+
"            <input type=\"hidden\" name=\""+"truststore_"+prefix+"\" value=\"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(regexp)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><i>"+Messages.getBodyString(locale,"WebcrawlerConnector.TrustEverything")+"</i></nobr>\n"+
"          </td>\n"+
"        </tr>\n"
            );
            trustsCounter++;
          }
          else
          {
            String trustStore = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTSTORE);
            IKeystoreManager localTruststore = KeystoreManagerFactory.make("",trustStore);
            String[] truststoreContents = localTruststore.getContents();
            
            // Each trust store will have only at most one cert in it at this level.  These individual certs are assembled into the proper trust store
            // for each individual url at fetch time.
            
            if (truststoreContents.length == 1)
            {
              String alias = truststoreContents[0];
              String description = localTruststore.getDescription(alias);
              String shortenedDescription = description;
              if (shortenedDescription.length() > 100)
                shortenedDescription = shortenedDescription.substring(0,100) + "...";
              out.print(
"        <tr class=\""+(((trustsCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteTrustUrlRegularExpression")+Integer.toString(trustsCounter+1)+"\" onclick='javascript:deleteTRegexp("+Integer.toString(trustsCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+"op_"+prefix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+"trustall_"+prefix+"\" value=\"false\"/>\n"+
"              <input type=\"hidden\" name=\""+"truststore_"+prefix+"\" value=\""+Encoder.attributeEscape(trustStore)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(regexp)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+Encoder.bodyEscape(shortenedDescription)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
              );
              trustsCounter++;
            }
          }

        }
      }

      if (trustsCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"WebcrawlerConnector.NoTrustCertificatesSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"trust\"><input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.AddUrlRegularExpressionForTruststore") + "\" onclick=\"javascript:addTRegexp();\"/></a>\n"+
"            <input type=\"hidden\" name=\"trust_count\" value=\""+trustsCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"trust_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"regexp_trust\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.UploadCertificate") + " <input name=\"certificate_trust\" size=\"50\" type=\"file\"/>&nbsp;<input name=\"all_trust\" type=\"checkbox\" value=\"true\">" + Messages.getBodyString(locale,"WebcrawlerConnector.TrustEverything") + "</input></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Certificates tab.
      int i = 0;
      int trustsCounter = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode cn = parameters.getChild(i++);
        if (cn.getType().equals(WebcrawlerConfig.NODE_TRUST))
        {
          // It's prefix will be...
          String prefix = "trust_" + Integer.toString(trustsCounter);

          // A bin description node!  Look for all its parameters.
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
          String trustEverything = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
          if (trustEverything != null && trustEverything.equals("true"))
          {
            // We trust everything that matches this regexp
            out.print(
"<input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"truststore_"+prefix+"\" value=\"\"/>\n"+
"<input type=\"hidden\" name=\""+"trustall_"+prefix+"\" value=\"true\"/>\n"
            );
            trustsCounter++;
          }
          else
          {
            String trustStore = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTSTORE);
            out.print(
"<input type=\"hidden\" name=\""+"regexp_"+prefix+"\" value=\""+Encoder.attributeEscape(regexp)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"truststore_"+prefix+"\" value=\""+Encoder.attributeEscape(trustStore)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"trustall_"+prefix+"\" value=\"false\"/>\n"
            );
            trustsCounter++;
          }
        }
      }
      out.print(
"<input type=\"hidden\" name=\"trust_count\" value=\""+trustsCounter+"\"/>\n"
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
    String email = variableContext.getParameter("email");
    if (email != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_EMAIL,email);
    String robotsUsage = variableContext.getParameter("robotsusage");
    if (robotsUsage != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE,robotsUsage);
    String proxyHost = variableContext.getParameter("proxyhost");
    if (proxyHost != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_PROXYHOST,proxyHost);
    String proxyPort = variableContext.getParameter("proxyport");
    if (proxyPort != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_PROXYPORT,proxyPort);
    String proxyAuthDomain = variableContext.getParameter("proxyauthdomain");
    if (proxyAuthDomain != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHDOMAIN,proxyAuthDomain);
    String proxyAuthUsername = variableContext.getParameter("proxyauthusername");
    if (proxyAuthUsername != null)
      parameters.setParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHUSERNAME,proxyAuthUsername);
    String proxyAuthPassword = variableContext.getParameter("proxyauthpassword");
    if (proxyAuthPassword != null)
      parameters.setObfuscatedParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHPASSWORD,variableContext.mapKeyToPassword(proxyAuthPassword));

    String x = variableContext.getParameter("bandwidth_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the bandwidth nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(WebcrawlerConfig.NODE_BINDESC))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "bandwidth_"+Integer.toString(i);
        String op = variableContext.getParameter("op_"+prefix);
        if (op == null || !op.equals("Delete"))
        {
          // Gather the regexp etc.
          String regexp = variableContext.getParameter("regexp_"+prefix);
          String isCaseInsensitive = variableContext.getParameter("insensitive_"+prefix);
          String maxConnections = variableContext.getParameter("connections_"+prefix);
          String rate = variableContext.getParameter("rate_"+prefix);
          String fetches = variableContext.getParameter("fetches_"+prefix);
          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_BINDESC);
          node.setAttribute(WebcrawlerConfig.ATTR_BINREGEXP,regexp);
          if (isCaseInsensitive != null && isCaseInsensitive.length() > 0)
            node.setAttribute(WebcrawlerConfig.ATTR_INSENSITIVE,isCaseInsensitive);
          if (maxConnections != null && maxConnections.length() > 0)
          {
            ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXCONNECTIONS);
            child.setAttribute(WebcrawlerConfig.ATTR_VALUE,maxConnections);
            node.addChild(node.getChildCount(),child);
          }
          if (rate != null && rate.length() > 0)
          {
            ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXKBPERSECOND);
            child.setAttribute(WebcrawlerConfig.ATTR_VALUE,rate);
            node.addChild(node.getChildCount(),child);
          }
          if (fetches != null && fetches.length() > 0)
          {
            ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE);
            child.setAttribute(WebcrawlerConfig.ATTR_VALUE,fetches);
            node.addChild(node.getChildCount(),child);
          }
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("bandwidth_op");
      if (addop != null && addop.equals("Add"))
      {
        String regexp = variableContext.getParameter("regexp_bandwidth");
        String isCaseInsensitive = variableContext.getParameter("insensitive_bandwidth");
        String maxConnections = variableContext.getParameter("connections_bandwidth");
        String rate = variableContext.getParameter("rate_bandwidth");
        String fetches = variableContext.getParameter("fetches_bandwidth");
        ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_BINDESC);
        node.setAttribute(WebcrawlerConfig.ATTR_BINREGEXP,regexp);
        if (isCaseInsensitive != null && isCaseInsensitive.length() > 0)
          node.setAttribute(WebcrawlerConfig.ATTR_INSENSITIVE,isCaseInsensitive);
        if (maxConnections != null && maxConnections.length() > 0)
        {
          ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXCONNECTIONS);
          child.setAttribute(WebcrawlerConfig.ATTR_VALUE,maxConnections);
          node.addChild(node.getChildCount(),child);
        }
        if (rate != null && rate.length() > 0)
        {
          ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXKBPERSECOND);
          child.setAttribute(WebcrawlerConfig.ATTR_VALUE,rate);
          node.addChild(node.getChildCount(),child);
        }
        if (fetches != null && fetches.length() > 0)
        {
          ConfigNode child = new ConfigNode(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE);
          child.setAttribute(WebcrawlerConfig.ATTR_VALUE,fetches);
          node.addChild(node.getChildCount(),child);
        }
        parameters.addChild(parameters.getChildCount(),node);
      }
    }
    
    x = variableContext.getParameter("acredential_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the access credential nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL)
                                  && !node.getAttributeValue(WebcrawlerConfig.ATTR_TYPE).equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "acredential_"+Integer.toString(i);
        String op = variableContext.getParameter("op_"+prefix);
        if (op == null || !op.equals("Delete"))
        {
          // Gather the regexp etc.
          String regexp = variableContext.getParameter("regexp_"+prefix);
          String type = variableContext.getParameter("type_"+prefix);
          String domain = variableContext.getParameter("domain_"+prefix);
          if (domain == null)
            domain = "";
          String userName = variableContext.getParameter("username_"+prefix);
          String password = variableContext.getParameter("password_"+prefix);
          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
          node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
          node.setAttribute(WebcrawlerConfig.ATTR_TYPE,type);
          node.setAttribute(WebcrawlerConfig.ATTR_DOMAIN,domain);
          node.setAttribute(WebcrawlerConfig.ATTR_USERNAME,userName);
          node.setAttribute(WebcrawlerConfig.ATTR_PASSWORD,
            ManifoldCF.obfuscate(variableContext.mapKeyToPassword(password)));
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("acredential_op");
      if (addop != null && addop.equals("Add"))
      {
        String regexp = variableContext.getParameter("regexp_acredential");
        String type = variableContext.getParameter("type_acredential");
        String domain = variableContext.getParameter("domain_acredential");
        String userName = variableContext.getParameter("username_acredential");
        String password = variableContext.getParameter("password_acredential");
        ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
        node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
        node.setAttribute(WebcrawlerConfig.ATTR_TYPE,type);
        node.setAttribute(WebcrawlerConfig.ATTR_DOMAIN,domain);
        node.setAttribute(WebcrawlerConfig.ATTR_USERNAME,userName);
        node.setAttribute(WebcrawlerConfig.ATTR_PASSWORD,ManifoldCF.obfuscate(password));
        parameters.addChild(parameters.getChildCount(),node);
      }
    }

    x = variableContext.getParameter("scredential_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the access credential nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL)
                                  && node.getAttributeValue(WebcrawlerConfig.ATTR_TYPE).equals(WebcrawlerConfig.ATTRVALUE_SESSION))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "scredential_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the regexp etc.
          String regexp = variableContext.getParameter(prefix+"_regexp");
          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
          node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
          node.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_SESSION);
          // How many login pages are there?
          int loginPageCount = Integer.parseInt(variableContext.getParameter(prefix+"_loginpagecount"));
          int q = 0;
          while (q < loginPageCount)
          {
            String authpagePrefix = prefix + "_" + Integer.toString(q);
            String authpageOp = variableContext.getParameter(authpagePrefix+"_op");
            if (authpageOp == null || !authpageOp.equals("Delete"))
            {
              String pageRegexp = variableContext.getParameter(authpagePrefix+"_regexp");
              String pageType = variableContext.getParameter(authpagePrefix+"_type");
              String matchRegexp = variableContext.getParameter(authpagePrefix+"_matchregexp");
              if (matchRegexp == null)
                matchRegexp = "";
              String overrideTargetURL = variableContext.getParameter(authpagePrefix+"_overridetargeturl");
              ConfigNode authPageNode = new ConfigNode(WebcrawlerConfig.NODE_AUTHPAGE);
              authPageNode.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,pageRegexp);
              authPageNode.setAttribute(WebcrawlerConfig.ATTR_TYPE,pageType);
              authPageNode.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,matchRegexp);
              if (overrideTargetURL != null && overrideTargetURL.length() > 0)
                authPageNode.setAttribute(WebcrawlerConfig.ATTR_OVERRIDETARGETURL,overrideTargetURL);
              if (pageType.equals(WebcrawlerConfig.ATTRVALUE_FORM))
              {
                // How many parameters are there?
                int paramCount = Integer.parseInt(variableContext.getParameter(authpagePrefix+"_loginparamcount"));
                int z = 0;
                while (z < paramCount)
                {
                  String paramPrefix = authpagePrefix+"_"+Integer.toString(z);
                  String paramOp = variableContext.getParameter(paramPrefix+"_op");
                  if (paramOp == null || !paramOp.equals("Delete"))
                  {
                    String name = variableContext.getParameter(paramPrefix+"_param");
                    String value = variableContext.getParameter(paramPrefix+"_value");
                    String password = variableContext.getParameter(paramPrefix+"_password");
                    ConfigNode paramNode = new ConfigNode(WebcrawlerConfig.NODE_AUTHPARAMETER);
                    paramNode.setAttribute(WebcrawlerConfig.ATTR_NAMEREGEXP,name);
                    if (value != null && value.length() > 0)
                      paramNode.setAttribute(WebcrawlerConfig.ATTR_VALUE,value);
                    if (password != null && password.length() > 0)
                      paramNode.setAttribute(WebcrawlerConfig.ATTR_PASSWORD,ManifoldCF.obfuscate(variableContext.mapKeyToPassword(password)));
                    authPageNode.addChild(authPageNode.getChildCount(),paramNode);
                  }
                  z++;
                }
                                                  
                // Look for add op
                String paramAddOp = variableContext.getParameter(authpagePrefix+"_loginparamop");
                if (paramAddOp != null && paramAddOp.equals("Add"))
                {
                  String name = variableContext.getParameter(authpagePrefix+"_loginparamname");
                  String value = variableContext.getParameter(authpagePrefix+"_loginparamvalue");
                  String password = variableContext.getParameter(authpagePrefix+"_loginparampassword");
                  ConfigNode paramNode = new ConfigNode(WebcrawlerConfig.NODE_AUTHPARAMETER);
                  paramNode.setAttribute(WebcrawlerConfig.ATTR_NAMEREGEXP,name);
                  if (value != null && value.length() > 0)
                    paramNode.setAttribute(WebcrawlerConfig.ATTR_VALUE,value);
                  if (password != null && password.length() > 0)
                    paramNode.setAttribute(WebcrawlerConfig.ATTR_PASSWORD,ManifoldCF.obfuscate(password));
                  authPageNode.addChild(authPageNode.getChildCount(),paramNode);
                }
              }
              
              node.addChild(node.getChildCount(),authPageNode);
            }
            q++;
          }
          // Look for add op
          String authpageAddop = variableContext.getParameter(prefix+"_loginpageop");
          if (authpageAddop != null && authpageAddop.equals("Add"))
          {
            String pageRegexp = variableContext.getParameter(prefix+"_loginpageregexp");
            String pageType = variableContext.getParameter(prefix+"_loginpagetype");
            String matchRegexp = variableContext.getParameter(prefix+"_loginpagematchregexp");
            if (matchRegexp == null)
              matchRegexp = "";
            String overrideTargetURL = variableContext.getParameter(prefix+"_loginpageoverridetargeturl");
            ConfigNode authPageNode = new ConfigNode(WebcrawlerConfig.NODE_AUTHPAGE);
            authPageNode.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,pageRegexp);
            authPageNode.setAttribute(WebcrawlerConfig.ATTR_TYPE,pageType);
            authPageNode.setAttribute(WebcrawlerConfig.ATTR_MATCHREGEXP,matchRegexp);
            if (overrideTargetURL != null && overrideTargetURL.length() > 0)
              authPageNode.setAttribute(WebcrawlerConfig.ATTR_OVERRIDETARGETURL,overrideTargetURL);
            node.addChild(node.getChildCount(),authPageNode);
          }

          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("scredential_op");
      if (addop != null && addop.equals("Add"))
      {
        String regexp = variableContext.getParameter("scredential_regexp");
        ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
        node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
        node.setAttribute(WebcrawlerConfig.ATTR_TYPE,WebcrawlerConfig.ATTRVALUE_SESSION);
        parameters.addChild(parameters.getChildCount(),node);
      }
    }

    x = variableContext.getParameter("trust_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the trust nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(WebcrawlerConfig.NODE_TRUST))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "trust_"+Integer.toString(i);
        String op = variableContext.getParameter("op_"+prefix);
        if (op == null || !op.equals("Delete"))
        {
          // Gather the regexp etc.
          String regexp = variableContext.getParameter("regexp_"+prefix);
          String trustall = variableContext.getParameter("trustall_"+prefix);
          String truststore = variableContext.getParameter("truststore_"+prefix);
          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_TRUST);
          node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
          if (trustall != null && trustall.equals("true"))
            node.setAttribute(WebcrawlerConfig.ATTR_TRUSTEVERYTHING,"true");
          else
            node.setAttribute(WebcrawlerConfig.ATTR_TRUSTSTORE,truststore);
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("trust_op");
      if (addop != null && addop.equals("Add"))
      {
        String regexp = variableContext.getParameter("regexp_trust");
        String trustall = variableContext.getParameter("all_trust");
        if (trustall != null && trustall.equals("true"))
        {
          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_TRUST);
          node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
          node.setAttribute(WebcrawlerConfig.ATTR_TRUSTEVERYTHING,"true");
          parameters.addChild(parameters.getChildCount(),node);
        }
        else
        {
          byte[] certificateValue = variableContext.getBinaryBytes("certificate_trust");
          IKeystoreManager mgr = KeystoreManagerFactory.make("");
          java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
          String certError = null;
          try
          {
            mgr.importCertificate("Certificate",is);
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
              // Ignore this
            }
          }

          if (certError != null)
          {
            // Redirect to error page
            return "Illegal certificate: "+certError;
          }

          ConfigNode node = new ConfigNode(WebcrawlerConfig.NODE_TRUST);
          node.setAttribute(WebcrawlerConfig.ATTR_URLREGEXP,regexp);
          node.setAttribute(WebcrawlerConfig.ATTR_TRUSTSTORE,mgr.getString());
          parameters.addChild(parameters.getChildCount(),node);
        }
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
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    String email = parameters.getParameter(WebcrawlerConfig.PARAMETER_EMAIL);
    String robots = parameters.getParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE);
    if (robots.equals("none"))
      robots = "Ignore robots.txt";
    else if (robots.equals("data"))
      robots = "Obey robots.txt for data fetches only";
    else if (robots.equals("all"))
      robots = "Obey robots.txt for all fetches";
    String proxyHost = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYHOST);
    if (proxyHost == null)
      proxyHost = "";
    String proxyPort = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYPORT);
    if (proxyPort == null)
      proxyPort = "";
    String proxyAuthDomain = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHDOMAIN);
    if (proxyAuthDomain == null)
      proxyAuthDomain = "";
    String proxyAuthUsername = parameters.getParameter(WebcrawlerConfig.PARAMETER_PROXYAUTHUSERNAME);
    if (proxyAuthUsername == null)
      proxyAuthUsername = "";

    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.EmailAddress")+"</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">"+Encoder.bodyEscape(email)+"</td>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.RobotsUsage")+"</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\"><nobr>"+Encoder.bodyEscape(robots)+"</nobr></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyHostColon") + "</nobr></td>\n"+
"    <td class=\"value\">"+Encoder.bodyEscape(proxyHost)+"</td>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyPortColon") + "</nobr></td>\n"+
"    <td class=\"value\">"+Encoder.bodyEscape(proxyPort)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyAuthenticationDomainColon") + "</nobr></td>\n"+
"    <td class=\"value\">"+Encoder.bodyEscape(proxyAuthDomain)+"</td>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ProxyAuthenticationUserNameColon") + "</nobr></td>\n"+
"    <td class=\"value\">"+Encoder.bodyEscape(proxyAuthUsername)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.BandwidthThrottling")+"</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.BinRegularExpression")+"</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.CaseInsensitive")+"</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.MaxConnections")+"</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.MaxKbytesSec")+"</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.MaxFetchesMin")+"</nobr></td>\n"+
"        </tr>\n"
    );
    int i = 0;
    int instanceNumber = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(WebcrawlerConfig.NODE_BINDESC))
      {
        // A bin description node!  Look for all its parameters.
        String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_BINREGEXP);
        String isCaseInsensitive = cn.getAttributeValue(WebcrawlerConfig.ATTR_INSENSITIVE);
        String maxConnections = null;
        String maxKBPerSecond = null;
        String maxFetchesPerMinute = null;
        int j = 0;
        while (j < cn.getChildCount())
        {
          ConfigNode childNode = cn.getChild(j++);
          if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXCONNECTIONS))
            maxConnections = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
          else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXKBPERSECOND))
            maxKBPerSecond = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
          else if (childNode.getType().equals(WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
            maxFetchesPerMinute = childNode.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        }
        if (maxConnections == null)
          maxConnections = "Not limited";
        if (maxKBPerSecond == null)
          maxKBPerSecond = "Not limited";
        if (maxFetchesPerMinute == null)
          maxFetchesPerMinute = "Not limited";
        if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
          isCaseInsensitive = "false";
        out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexp)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\">"+isCaseInsensitive+"</td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+maxConnections+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+maxKBPerSecond+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+maxFetchesPerMinute+"</nobr></td>\n"+
"        </tr>\n"
        );
        instanceNumber++;
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">"+Messages.getBodyString(locale,"WebcrawlerConnector.NoBandwidthThrottling")+"</td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  \n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.PageAccessCredentials") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.CredentialType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.CredentialDomain") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.UserName") + "</nobr></td>\n"+
"        </tr>\n"
    );
    i = 0;
    instanceNumber = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
      {
        // A bin description node!  Look for all its parameters.
        String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
        if (!type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
        {
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
          // Page-based auth
          String domain = cn.getAttributeValue(WebcrawlerConfig.ATTR_DOMAIN);
          if (domain == null)
            domain = "";
          String userName = cn.getAttributeValue(WebcrawlerConfig.ATTR_USERNAME);
          out.print(
"        <tr>\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexp)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+type+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(domain)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(userName)+"</nobr></td>\n"+
"        </tr>\n"
          );
          instanceNumber++;
        }
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"4\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoPageAccessCredentials") + "</nobr></td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.SessionBasedAccessCredentials") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.LoginPages") + "</nobr></td>\n"+
"        </tr>\n"
    );
    i = 0;
    instanceNumber = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
      {
        // A bin description node!  Look for all its parameters.
        String type = cn.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
        if (type.equals(WebcrawlerConfig.ATTRVALUE_SESSION))
        {
          String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
          // Session-based auth.  Display this as a nested table.
          out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexp)+"</nobr></td>\n"+
"          <td class=\"boxcell\">\n"
          );
          int q = 0;
          int authPageInstanceNumber = 0;
          while (q < cn.getChildCount())
          {
            ConfigNode authPageNode = cn.getChild(q++);
            if (authPageNode.getType().equals(WebcrawlerConfig.NODE_AUTHPAGE))
            {
              String authURLRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
              String pageType = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_TYPE);
              String authMatchRegexp = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_MATCHREGEXP);
              if (authMatchRegexp == null)
                authMatchRegexp = "";
              String authOverrideTargetURL = authPageNode.getAttributeValue(WebcrawlerConfig.ATTR_OVERRIDETARGETURL);
              if (authOverrideTargetURL == null)
                authOverrideTargetURL = "";
              if (authPageInstanceNumber == 0)
              {
                out.print(
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.LoginURLRegularExpression") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.PageType") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.FormNamelinkTargetRegularExpression") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.OverrideTargetURL") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.OverrideFormParameters") + "</nobr></td>\n"+
"              </tr>\n"
                );
              }
              out.print(
"              <tr class=\""+(((authPageInstanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(authURLRegexp)+"</nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr>"+pageType+"</nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(authMatchRegexp)+"</nobr></td>\n"+
"                <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(authOverrideTargetURL)+"</nobr></td>\n"+
"                <td class=\"formcolumncell\">\n"
              );
              if (pageType.equals(WebcrawlerConfig.ATTRVALUE_FORM))
              {
                int z = 0;
                while (z < authPageNode.getChildCount())
                {
                  ConfigNode authParameter = authPageNode.getChild(z++);
                  if (authParameter.getType().equals(WebcrawlerConfig.NODE_AUTHPARAMETER))
                  {
                    String paramName = authParameter.getAttributeValue(WebcrawlerConfig.ATTR_NAMEREGEXP);
                    if (paramName == null)
                      paramName = "";
                    String paramValue = authParameter.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
                    if (paramValue == null)
                      paramValue = "";
                    String password = authParameter.getAttributeValue(WebcrawlerConfig.ATTR_PASSWORD);
                    if (password != null && password.length() > 0)
                      paramValue = "*****";
                    out.print(
"                  <nobr>"+Encoder.bodyEscape(paramName+": "+paramValue)+"</nobr><br/>\n"
                    );
                  }
                }
              }
              out.print(
"                </td>\n"+
"              </tr>\n"
              );
              authPageInstanceNumber++;
            }
          }
          if (authPageInstanceNumber == 0)
          {
            out.print(
"            <nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoLoginPagesSpecified") + "</nobr>\n"
            );
          }
          else
          {
            out.print(
"            </table>\n"
            );
          }
          out.print(
"          </td>\n"+
"        </tr>\n"
          );
          instanceNumber++;
        }
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoSessionBasedAccessCredentials") + "</nobr></td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  \n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.TrustCertificates") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Certificate") + "</nobr></td>\n"+
"        </tr>\n"
    );
    i = 0;
    instanceNumber = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(WebcrawlerConfig.NODE_TRUST))
      {
        // A bin description node!  Look for all its parameters.
        String regexp = cn.getAttributeValue(WebcrawlerConfig.ATTR_URLREGEXP);
        String trustEverything = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
        if (trustEverything != null && trustEverything.equals("true"))
        {
          // We trust everything that matches this regexp
          out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexp)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><i>" + Messages.getBodyString(locale,"WebcrawlerConnector.TrustEverything") + "</i></td>\n"+
"        </tr>\n"
          );
          instanceNumber++;
        }
        else
        {
          String trustStore = cn.getAttributeValue(WebcrawlerConfig.ATTR_TRUSTSTORE);
          IKeystoreManager localTruststore = KeystoreManagerFactory.make("",trustStore);
          String[] truststoreContents = localTruststore.getContents();
            
          // Each trust store will have only at most one cert in it at this level.  These individual certs are assembled into the proper trust store
          // for each individual url at fetch time.
            
          if (truststoreContents.length == 1)
          {
            String alias = truststoreContents[0];
            String description = localTruststore.getDescription(alias);
            String shortenedDescription = description;
            if (shortenedDescription.length() > 100)
              shortenedDescription = shortenedDescription.substring(0,100) + "...";
            out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexp)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\">"+Encoder.bodyEscape(shortenedDescription)+"</td>\n"+
"        </tr>\n"
            );
            instanceNumber++;
          }
        }
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"2\">" + Messages.getBodyString(locale,"WebcrawlerConnector.NoTrustCertificates") + "</td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Seeds"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Canonicalization"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.URLMappings"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Inclusions"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Exclusions"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Security"));
    tabsArray.add(Messages.getString(locale,"WebcrawlerConnector.Metadata"));
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function "+seqPrefix+"SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"AddRegexp(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"rssmatch.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.MatchMustHaveARegexpValue")+"\");\n"+
"    editjob."+seqPrefix+"rssmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"rssop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"RemoveRegexp(index, anchorvalue)\n"+
"{\n"+
"  editjob."+seqPrefix+"rssindex.value = index;\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"rssop\",\"Delete\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"URLRegexpDelete(index, anchorvalue)\n"+
"{\n"+
"  editjob."+seqPrefix+"urlregexpnumber.value = index;\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"urlregexpop\",\"Delete\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"URLRegexpAdd(anchorvalue)\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"urlregexpop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"checkSpecification()\n"+
"{\n"+
"  if ("+seqPrefix+"check_expressions(\"inclusions\",editjob."+seqPrefix+"inclusions.value) == false)\n"+
"  {\n"+
"    editjob."+seqPrefix+"inclusions.focus();\n"+
"    return false;\n"+
"  }  \n"+
"  if ("+seqPrefix+"check_expressions(\"exclusions\",editjob."+seqPrefix+"exclusions.value) == false)\n"+
"  {\n"+
"    editjob."+seqPrefix+"exclusions.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if ("+seqPrefix+"check_seedsList() == false)\n"+
"  {\n"+
"    editjob."+seqPrefix+"seeds.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"check_expressions(thecontext,theexpressionlist)\n"+
"{\n"+
"  var rval = true;\n"+
"  var theArray = theexpressionlist.split(\"\\n\");\n"+
"  var i = 0;\n"+
"  while (i < theArray.length)\n"+
"  {\n"+
"    // For legality check, we must cut out anything useful that is java-only\n"+
"    var theexp = theArray[i];\n"+
"    var trimmed = theexp.replace(/^\\s+/,\"\");\n"+
"    i = i + 1;\n"+
"    if (trimmed.length == 0 || (trimmed.length >= 1 && trimmed.substring(0,1) == \"#\"))\n"+
"      continue;\n"+
"    try\n"+
"    {\n"+
"      var foo = \"teststring\";\n"+
"      foo.search(theexp.replace(/\\(\\?i\\)/,\"\"));\n"+
"    }\n"+
"    catch (e)\n"+
"    {\n"+
"      alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.FoundAnIllegalRegularExpressionIn")+"\"+thecontext+\": '\"+theexp+\"'"+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.ErrorWas")+"\"+e);\n"+
"      rval = false;\n"+
"    }\n"+
"  }\n"+
"  return rval;\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"check_seedsList()\n"+
"{\n"+
"  var regexp = /http(s)?:\\/\\/([a-z0-9+!*(),;?&=\\$_.-]+(\\:[a-z0-9+!*(),;?&=\\$_.-]+)?@)?[a-z0-9+\\$_-]+(\\.[a-z0-9+\\$_-]+)*(\\:[0-9]{2,5})?(\\/([a-z0-9+\\$_-]\\.?)+)*\\/?(\\?[a-z+&\\$_.-][a-z0-9;:@\\/&%=+\\$_.-]*)?(#[a-z_.-][a-z0-9+\\$_.-]*)?/;\n"+
"  var lines = editjob."+seqPrefix+"seeds.value.split(\"\\n\");\n"+
"  var trimmedUrlList = \"\";\n"+
"  var invalidUrlList = \"\";\n"+
"  var i = 0;\n"+
"  while (i < lines.length)\n"+
"  {\n"+
"    var line = lines[i].replace(/^\\s*/, \"\").replace(/\\s*$/, \"\");\n"+
"    if (line.length > 0)\n"+
"    {\n"+
"      if (!regexp.test(line))\n"+
"        invalidUrlList = invalidUrlList + line + \"\\n\";\n"+
"      trimmedUrlList = trimmedUrlList + line + \"\\n\";\n"+      
"    }\n"+
"    i = i + 1;\n"+
"  }\n"+
"  editjob."+seqPrefix+"seeds.value = trimmedUrlList;\n"+ 
"  if (invalidUrlList.length > 0)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.InvalidUrlsInSeedsList")+"\\n\" + invalidUrlList);\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"WebcrawlerConnector.TypeInAnAccessToken")+"\");\n"+
"    editjob."+seqPrefix+"spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.  (actualSequenceNumber, tabName) form a unique tuple within
  *  the job.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int i;
    int k;

    // Find the various strings
    List<String> regexp = new ArrayList<String>();
    List<String> matchStrings = new ArrayList<String>();

    String seeds = "";
    String inclusions = ".*\n";
    String exclusions = "";
    String inclusionsIndex = ".*\n";
    String exclusionsIndex = "";
    String exclusionsContentIndex = "";
    
    boolean includeMatching = true;
    Set<String> excludedHeaders = new HashSet<String>();
    
    // Now, loop through description
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_MAP))
      {
        String match = sn.getAttributeValue(WebcrawlerConfig.ATTR_MATCH);
        String map = sn.getAttributeValue(WebcrawlerConfig.ATTR_MAP);
        if (match != null)
        {
          regexp.add(match);
          if (map == null)
            map = "";
          matchStrings.add(map);
        }
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_SEEDS))
      {
        seeds = sn.getValue();
        if (seeds == null)
          seeds = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDES))
      {
        inclusions = sn.getValue();
        if (inclusions == null)
          inclusions = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDES))
      {
        exclusions = sn.getValue();
        if (exclusions == null)
          exclusions = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDESINDEX))
      {
        inclusionsIndex = sn.getValue();
        if (inclusionsIndex == null)
          inclusionsIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESINDEX))
      {
        exclusionsIndex = sn.getValue();
        if (exclusionsIndex == null)
          exclusionsIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX))
      {
        exclusionsContentIndex = sn.getValue();
        if (exclusionsContentIndex == null)
        	exclusionsContentIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_LIMITTOSEEDS))
      {
        String value = sn.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        if (value == null || value.equals("false"))
          includeMatching = false;
        else
          includeMatching = true;
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDEHEADER))
      {
        String value = sn.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        excludedHeaders.add(value);
      }

    }

    // Seeds tab

    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Seeds")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"value\" colspan=\"2\">\n"+
"      <textarea rows=\"25\" cols=\"80\" name=\""+seqPrefix+"seeds\">"+Encoder.bodyEscape(seeds)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"seeds\" value=\""+Encoder.attributeEscape(seeds)+"\"/>\n"
      );
    }

    // Canonicalization tab

    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Canonicalization")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"boxcell\" colspan=\"2\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"urlregexpop\" value=\"Continue\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"urlregexpnumber\" value=\"\"/>\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegularExpression") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Description") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Reorder") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveJSPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveASPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemovePHPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveBVSessions") + "</nobr></td>\n"+
"        </tr>\n"
      );
      int q = 0;
      int l = 0;
      while (q < ds.getChildCount())
      {
        SpecificationNode specNode = ds.getChild(q++);
        if (specNode.getType().equals(WebcrawlerConfig.NODE_URLSPEC))
        {
          // Ok, this node matters to us
          String regexpString = specNode.getAttributeValue(WebcrawlerConfig.ATTR_REGEXP);
          String description = specNode.getAttributeValue(WebcrawlerConfig.ATTR_DESCRIPTION);
          if (description == null)
            description = "";
          String allowReorder = specNode.getAttributeValue(WebcrawlerConfig.ATTR_REORDER);
          String allowReorderOutput;
          if (allowReorder == null || allowReorder.length() == 0)
          {
            allowReorder = WebcrawlerConfig.ATTRVALUE_NO;
            allowReorderOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
          }
          else
            allowReorderOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          String allowJavaSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL);
          String allowJavaSessionRemovalOutput;
          if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
          {
            allowJavaSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
            allowJavaSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
          }
          else
            allowJavaSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          String allowASPSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL);
          String allowASPSessionRemovalOutput;
          if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
          {
            allowASPSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
            allowASPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
          }
          else
            allowASPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          String allowPHPSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL);
          String allowPHPSessionRemovalOutput;
          if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
          {
            allowPHPSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
            allowPHPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
          }
          else
            allowPHPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          String allowBVSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL);
          String allowBVSessionRemovalOutput;
          if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
          {
            allowBVSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
            allowBVSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
          }
          else
            allowBVSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+seqPrefix+"urlregexp_"+Integer.toString(l)+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteUrlRegexp")+Encoder.attributeEscape(regexpString)+"\" onclick='javascript:"+seqPrefix+"URLRegexpDelete("+Integer.toString(l)+",\""+seqPrefix+"urlregexp_"+Integer.toString(l)+"\");'/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexp_"+Integer.toString(l)+"\" value=\""+Encoder.attributeEscape(regexpString)+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpdesc_"+Integer.toString(l)+"\" value=\""+Encoder.attributeEscape(description)+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpreorder_"+Integer.toString(l)+"\" value=\""+allowReorder+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpjava_"+Integer.toString(l)+"\" value=\""+allowJavaSessionRemoval+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpasp_"+Integer.toString(l)+"\" value=\""+allowASPSessionRemoval+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpphp_"+Integer.toString(l)+"\" value=\""+allowPHPSessionRemoval+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"urlregexpbv_"+Integer.toString(l)+"\" value=\""+allowBVSessionRemoval+"\"/>\n"+
"            <nobr>"+Encoder.bodyEscape(regexpString)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">"+Encoder.bodyEscape(description)+"</td>\n"+
"          <td class=\"formcolumncell\">"+allowReorderOutput+"</td>\n"+
"          <td class=\"formcolumncell\">"+allowJavaSessionRemovalOutput+"</td>\n"+
"          <td class=\"formcolumncell\">"+allowASPSessionRemovalOutput+"</td>\n"+
"          <td class=\"formcolumncell\">"+allowPHPSessionRemovalOutput+"</td>\n"+
"          <td class=\"formcolumncell\">"+allowBVSessionRemovalOutput+"</td>\n"+
"        </tr>\n"
          );

          l++;
        }
      }
      if (l == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td colspan=\"8\" class=\"formcolumnmessage\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoCanonicalizationSpecified") + "</nobr></td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td colspan=\"8\" class=\"formseparator\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+seqPrefix+"urlregexp_"+Integer.toString(l)+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.AddUrlRegexp") + "\" onclick='javascript:"+seqPrefix+"URLRegexpAdd(\""+seqPrefix+"urlregexp_"+Integer.toString(l+1)+"\");'/>\n"+
"              <input type=\"hidden\" name=\""+seqPrefix+"urlregexpcount\" value=\""+Integer.toString(l)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\"><input type=\"text\" name=\""+seqPrefix+"urlregexp\" size=\"30\" value=\"\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"text\" name=\""+seqPrefix+"urlregexpdesc\" size=\"30\" value=\"\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"checkbox\" name=\""+seqPrefix+"urlregexpreorder\" value=\""+WebcrawlerConfig.ATTRVALUE_YES+"\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"checkbox\" name=\""+seqPrefix+"urlregexpjava\" value=\""+WebcrawlerConfig.ATTRVALUE_YES+"\" checked=\"true\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"checkbox\" name=\""+seqPrefix+"urlregexpasp\" value=\""+WebcrawlerConfig.ATTRVALUE_YES+"\" checked=\"true\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"checkbox\" name=\""+seqPrefix+"urlregexpphp\" value=\""+WebcrawlerConfig.ATTRVALUE_YES+"\" checked=\"true\"/></td>\n"+
"          <td class=\"formcolumncell\"><input type=\"checkbox\" name=\""+seqPrefix+"urlregexpbv\" value=\""+WebcrawlerConfig.ATTRVALUE_YES+"\" checked=\"true\"/></td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Post the canonicalization specification
      int q = 0;
      int l = 0;
      while (q < ds.getChildCount())
      {
        SpecificationNode specNode = ds.getChild(q++);
        if (specNode.getType().equals(WebcrawlerConfig.NODE_URLSPEC))
        {
          // Ok, this node matters to us
          String regexpString = specNode.getAttributeValue(WebcrawlerConfig.ATTR_REGEXP);
          String description = specNode.getAttributeValue(WebcrawlerConfig.ATTR_DESCRIPTION);
          if (description == null)
            description = "";
          String allowReorder = specNode.getAttributeValue(WebcrawlerConfig.ATTR_REORDER);
          if (allowReorder == null || allowReorder.length() == 0)
            allowReorder = WebcrawlerConfig.ATTRVALUE_NO;
          String allowJavaSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL);
          if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
            allowJavaSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
          String allowASPSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL);
          if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
            allowASPSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
          String allowPHPSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL);
          if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
            allowPHPSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
          String allowBVSessionRemoval = specNode.getAttributeValue(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL);
          if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
            allowBVSessionRemoval = WebcrawlerConfig.ATTRVALUE_NO;
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexp_"+Integer.toString(l)+"\" value=\""+Encoder.attributeEscape(regexpString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpdesc_"+Integer.toString(l)+"\" value=\""+Encoder.attributeEscape(description)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpreorder_"+Integer.toString(l)+"\" value=\""+allowReorder+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpjava_"+Integer.toString(l)+"\" value=\""+allowJavaSessionRemoval+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpasp_"+Integer.toString(l)+"\" value=\""+allowASPSessionRemoval+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpphp_"+Integer.toString(l)+"\" value=\""+allowPHPSessionRemoval+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpbv_"+Integer.toString(l)+"\" value=\""+allowBVSessionRemoval+"\"/>\n"
          );
          l++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"urlregexpcount\" value=\""+Integer.toString(l)+"\"/>\n"
      );
    }

    // Mappings tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.URLMappings")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"rssop\" value=\"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"rssindex\" value=\"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"rssmapcount\" value=\""+Integer.toString(regexp.size())+"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );

      i = 0;
      while (i < regexp.size())
      {
        String prefix = seqPrefix+"rssregexp_"+Integer.toString(i)+"_";
        out.print(
"  <tr>\n"+
"    <td class=\"value\">\n"+
"      <a name=\""+seqPrefix+"regexp_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" value=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.Remove")+"\" onclick='javascript:"+seqPrefix+"RemoveRegexp("+Integer.toString(i)+",\""+seqPrefix+"regexp_"+Integer.toString(i)+"\")' alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.RemoveRegexp")+Integer.toString(i)+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\""+prefix+"match"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape((String)regexp.get(i))+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape((String)regexp.get(i))+"</td>\n"+
"    <td class=\"value\">--&gt;</td>\n"+
"    <td class=\"value\">\n"
        );
        String match = (String)matchStrings.get(i);
        out.print(
"      <input type=\"hidden\" name=\""+prefix+"map"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(match)+"\"/>\n"
        );
        if (match.length() == 0)
        {
          out.print(
"      &lt;as is&gt;\n"
          );
        }
        else
        {
          out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(match)+"\n"
          );
        }
        out.print(
"    </td>\n"+
"  </tr>\n"
        );
        i++;
      }
      out.print(
"  <tr>\n"+
"    <td class=\"value\"><a name=\""+seqPrefix+"regexp_"+Integer.toString(i)+"\"><input type=\"button\" value=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.Add")+"\" onclick='javascript:"+seqPrefix+"AddRegexp(\""+seqPrefix+"regexp_"+Integer.toString(i+1)+"\")' alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.AddRegexp")+"\"/></a></td>\n"+
"    <td class=\"value\"><input type=\"text\" name=\""+seqPrefix+"rssmatch\" size=\"16\" value=\"\"/></td>\n"+
"    <td class=\"value\">--&gt;</td>\n"+
"    <td class=\"value\"><input type=\"text\" name=\""+seqPrefix+"rssmap\" size=\"16\" value=\"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"rssmapcount\" value=\""+Integer.toString(regexp.size())+"\"/>\n"
      );
      i = 0;
      while (i < regexp.size())
      {
        String prefix = seqPrefix+"rssregexp_"+Integer.toString(i)+"_";
        String match = (String)matchStrings.get(i);
        out.print(
"<input type=\"hidden\" name=\""+prefix+"match"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape((String)regexp.get(i))+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"map"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(match)+"\"/>\n"
        );
        i++;
      }
    }

    // Inclusions tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Inclusions")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeInCrawl") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <textarea rows=\"25\" cols=\"60\" name=\""+seqPrefix+"inclusions\">"+Encoder.bodyEscape(inclusions)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeInIndex") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <textarea rows=\"10\" cols=\"60\" name=\""+seqPrefix+"inclusionsindex\">"+Encoder.bodyEscape(inclusionsIndex)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeOnlyHostsMatchingSeeds") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"checkbox\" name=\""+seqPrefix+"matchinghosts\" value=\"true\""+(includeMatching?" checked=\"yes\"":"")+"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"matchinghosts_present\" value=\"true\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"inclusions\" value=\""+Encoder.attributeEscape(inclusions)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"inclusionsindex\" value=\""+Encoder.attributeEscape(inclusionsIndex)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"matchinghosts\" value=\""+(includeMatching?"true":"false")+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"matchinghosts_present\" value=\"true\"/>\n"
      );
    }

    // Exclusions tab

    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Exclusions")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeFromCrawl") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <textarea rows=\"25\" cols=\"60\" name=\""+seqPrefix+"exclusions\">"+Encoder.bodyEscape(exclusions)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeFromIndex") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <textarea rows=\"10\" cols=\"60\" name=\""+seqPrefix+"exclusionsindex\">"+Encoder.bodyEscape(exclusionsIndex)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeContentFromIndex") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <textarea rows=\"10\" cols=\"60\" name=\""+seqPrefix+"exclusionscontentindex\">"+Encoder.bodyEscape(exclusionsContentIndex)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
              "<input type=\"hidden\" name=\"" + seqPrefix + "exclusions\" value=\"" + Encoder.attributeEscape(exclusions) + "\"/>\n" +
                      "<input type=\"hidden\" name=\"" + seqPrefix + "exclusionsindex\" value=\"" + Encoder.attributeEscape(exclusionsIndex) + "\"/>\n" +
                      "<input type=\"hidden\" name=\"" + seqPrefix + "exclusionscontentindex\" value=\"" + Encoder.attributeEscape(exclusionsContentIndex) + "\"/>\n"
      );
    }
  
    // Security tab
    // There is no native security, so all we care about are the tokens.
    i = 0;

    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Security")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(WebcrawlerConfig.NODE_ACCESS))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = seqPrefix+"accessop"+accessDescription;
          String token = sn.getAttributeValue(WebcrawlerConfig.ATTR_TOKEN);
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"spectoken"+accessDescription+"\" value=\""+Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Delete") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+accessOpName+"\",\"Delete\",\""+seqPrefix+"token_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"WebcrawlerConnector.DeleteToken")+Integer.toString(k)+"\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+Encoder.bodyEscape(token)+"\n"+
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
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"WebcrawlerConnector.NoAccessTokensPresent") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"accessop\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecAddToken(\""+seqPrefix+"token_"+Integer.toString(k+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"WebcrawlerConnector.AddAccessToken") + "\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(WebcrawlerConfig.NODE_ACCESS))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue(WebcrawlerConfig.ATTR_TOKEN);
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"spectoken"+accessDescription+"\" value=\""+Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // "Metadata" tab
    if (tabName.equals(Messages.getString(locale,"WebcrawlerConnector.Metadata")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>"+Messages.getBodyString(locale, "WebcrawlerConnector.ExcludedHeadersColon")+"</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"excludedheaders_present\" value=\"true\"/>\n"
      );
      
      for (String potentiallyExcludedHeader : potentiallyExcludedHeaders)
      {
        out.print(
"      <input type=\"checkbox\" name=\""+seqPrefix+"excludedheaders\" value=\""+Encoder.attributeEscape(potentiallyExcludedHeader)+"\""+(excludedHeaders.contains(potentiallyExcludedHeader)?" checked=\"true\"":"")+">"+Encoder.bodyEscape(potentiallyExcludedHeader)+"</input><br/>\n"
        );
      }
      out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"excludedheaders_present\" value=\"true\"/>\n"
      );
      for (String excludedHeader : excludedHeaders)
      {
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"excludedheaders\" value=\""+Encoder.attributeEscape(excludedHeader)+"\"/>\n"
        );
      }
    }
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // Get the map
    String value = variableContext.getParameter(seqPrefix+"rssmapcount");
    if (value != null)
    {
      int mapsize = Integer.parseInt(value);

      // Clear it first
      int j = 0;
      while (j < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(j);
        if (sn.getType().equals(WebcrawlerConfig.NODE_MAP))
          ds.removeChild(j);
        else
          j++;
      }

      // Grab the map values
      j = 0;
      while (j < mapsize)
      {
        String prefix = seqPrefix+"rssregexp_"+Integer.toString(j)+"_";
        String match = variableContext.getParameter(prefix+"match");
        String map = variableContext.getParameter(prefix+"map");
        if (map == null)
          map = "";
        // Add to the specification
        SpecificationNode node = new SpecificationNode(WebcrawlerConfig.NODE_MAP);
        node.setAttribute(WebcrawlerConfig.ATTR_MATCH,match);
        node.setAttribute(WebcrawlerConfig.ATTR_MAP,map);
        ds.addChild(ds.getChildCount(),node);

        j++;
      }
    }
    // Now, do whatever action we were told to do.
    String rssop = variableContext.getParameter(seqPrefix+"rssop");
    if (rssop != null && rssop.equals("Add"))
    {
      // Add a match to the end
      String match = variableContext.getParameter(seqPrefix+"rssmatch");
      String map = variableContext.getParameter(seqPrefix+"rssmap");
      SpecificationNode node = new SpecificationNode(WebcrawlerConfig.NODE_MAP);
      node.setAttribute(WebcrawlerConfig.ATTR_MATCH,match);
      node.setAttribute(WebcrawlerConfig.ATTR_MAP,map);
      ds.addChild(ds.getChildCount(),node);
    }
    else if (rssop != null && rssop.equals("Delete"))
    {
      int index = Integer.parseInt(variableContext.getParameter(seqPrefix+"rssindex"));
      int j = 0;
      while (j < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(j);
        if (sn.getType().equals(WebcrawlerConfig.NODE_MAP))
        {
          if (index == 0)
          {
            ds.removeChild(j);
            break;
          }
          index--;
        }
        j++;
      }
    }

    // Get excluded headers
    String excludedHeadersPresent = variableContext.getParameter(seqPrefix+"excludedheaders_present");
    if (excludedHeadersPresent != null)
    {
      // Delete existing excludedheader record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDEHEADER))
          ds.removeChild(i);
        else
          i++;
      }
      String[] excludedHeaders = variableContext.getParameterValues(seqPrefix+"excludedheaders");
      if (excludedHeaders != null)
      {
        for (String excludedHeader : excludedHeaders)
        {
          SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_EXCLUDEHEADER);
          cn.setAttribute(WebcrawlerConfig.ATTR_VALUE, excludedHeader);
          ds.addChild(ds.getChildCount(),cn);
        }
      }
    }
    
    // Get the seeds
    String seeds = variableContext.getParameter(seqPrefix+"seeds");
    if (seeds != null)
    {
      // Delete existing seeds record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_SEEDS))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_SEEDS);
      cn.setValue(seeds);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Get the inclusions
    String inclusions = variableContext.getParameter(seqPrefix+"inclusions");
    if (inclusions != null)
    {
      // Delete existing inclusions record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDES))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDES);
      cn.setValue(inclusions);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Get the index inclusions
    String inclusionsIndex = variableContext.getParameter(seqPrefix+"inclusionsindex");
    if (inclusionsIndex != null)
    {
      // Delete existing index inclusions record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDESINDEX))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDESINDEX);
      cn.setValue(inclusionsIndex);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Handle the seeds-only switch
    String matchingHostsPresent = variableContext.getParameter(seqPrefix+"matchinghosts_present");
    if (matchingHostsPresent != null)
    {
      // Delete existing switch record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_LIMITTOSEEDS))
          ds.removeChild(i);
        else
          i++;
      }

      String matchingHosts = variableContext.getParameter(seqPrefix+"matchinghosts");
      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_LIMITTOSEEDS);
      cn.setAttribute(WebcrawlerConfig.ATTR_VALUE,(matchingHosts==null||matchingHosts.equals("false"))?"false":"true");
      ds.addChild(ds.getChildCount(),cn);
    }
    
    // Get the exclusions
    String exclusions = variableContext.getParameter(seqPrefix+"exclusions");
    if (exclusions != null)
    {
      // Delete existing exclusions record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDES))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_EXCLUDES);
      cn.setValue(exclusions);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Get the index exclusions
    String exclusionsIndex = variableContext.getParameter(seqPrefix+"exclusionsindex");
    if (exclusionsIndex != null)
    {
      // Delete existing exclusions record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESINDEX))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_EXCLUDESINDEX);
      cn.setValue(exclusionsIndex);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Get the content index exclusions
    String exclusionsContentIndex = variableContext.getParameter(seqPrefix+"exclusionscontentindex");
    if (exclusionsContentIndex != null)
    {
      // Delete existing content exclusions record first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode cn = new SpecificationNode(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX);
      cn.setValue(exclusionsContentIndex);
      ds.addChild(ds.getChildCount(),cn);
    }

    // Read the url specs
    String urlRegexpCount = variableContext.getParameter(seqPrefix+"urlregexpcount");
    if (urlRegexpCount != null && urlRegexpCount.length() > 0)
    {
      int regexpCount = Integer.parseInt(urlRegexpCount);
      int j = 0;
      while (j < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(j);
        if (sn.getType().equals(WebcrawlerConfig.NODE_URLSPEC))
          ds.removeChild(j);
        else
          j++;
      }
      
      // Grab the operation and the index (if any)
      String operation = variableContext.getParameter(seqPrefix+"urlregexpop");
      if (operation == null)
        operation = "Continue";
      int opIndex = -1;
      if (operation.equals("Delete"))
        opIndex = Integer.parseInt(variableContext.getParameter(seqPrefix+"urlregexpnumber"));
      
      // Reconstruct urlspec nodes
      j = 0;
      while (j < regexpCount)
      {
        // For each index, first look for a delete operation
        if (!operation.equals("Delete") || j != opIndex)
        {
          // Add the jth node
          String regexp = variableContext.getParameter(seqPrefix+"urlregexp_"+Integer.toString(j));
          String regexpDescription = variableContext.getParameter(seqPrefix+"urlregexpdesc_"+Integer.toString(j));
          String reorder = variableContext.getParameter(seqPrefix+"urlregexpreorder_"+Integer.toString(j));
          String javaSession = variableContext.getParameter(seqPrefix+"urlregexpjava_"+Integer.toString(j));
          String aspSession = variableContext.getParameter(seqPrefix+"urlregexpasp_"+Integer.toString(j));
          String phpSession = variableContext.getParameter(seqPrefix+"urlregexpphp_"+Integer.toString(j));
          String bvSession = variableContext.getParameter(seqPrefix+"urlregexpbv_"+Integer.toString(j));
          SpecificationNode newSn = new SpecificationNode(WebcrawlerConfig.NODE_URLSPEC);
          newSn.setAttribute(WebcrawlerConfig.ATTR_REGEXP,regexp);
          if (regexpDescription != null && regexpDescription.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_DESCRIPTION,regexpDescription);
          if (reorder != null && reorder.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_REORDER,reorder);
          if (javaSession != null && javaSession.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL,javaSession);
          if (aspSession != null && aspSession.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL,aspSession);
          if (phpSession != null && phpSession.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL,phpSession);
          if (bvSession != null && bvSession.length() > 0)
            newSn.setAttribute(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL,bvSession);
          ds.addChild(ds.getChildCount(),newSn);
        }
        j++;
      }
      if (operation.equals("Add"))
      {
        String regexp = variableContext.getParameter(seqPrefix+"urlregexp");
        String regexpDescription = variableContext.getParameter(seqPrefix+"urlregexpdesc");
        String reorder = variableContext.getParameter(seqPrefix+"urlregexpreorder");
        String javaSession = variableContext.getParameter(seqPrefix+"urlregexpjava");
        String aspSession = variableContext.getParameter(seqPrefix+"urlregexpasp");
        String phpSession = variableContext.getParameter(seqPrefix+"urlregexpphp");
        String bvSession = variableContext.getParameter(seqPrefix+"urlregexpbv");

        // Add a new node at the end
        SpecificationNode newSn = new SpecificationNode(WebcrawlerConfig.NODE_URLSPEC);
        newSn.setAttribute(WebcrawlerConfig.ATTR_REGEXP,regexp);
        if (regexpDescription != null && regexpDescription.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_DESCRIPTION,regexpDescription);
        if (reorder != null && reorder.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_REORDER,reorder);
        if (javaSession != null && javaSession.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL,javaSession);
        if (aspSession != null && aspSession.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL,aspSession);
        if (phpSession != null && phpSession.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL,phpSession);
        if (bvSession != null && bvSession.length() > 0)
          newSn.setAttribute(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL,bvSession);
        ds.addChild(ds.getChildCount(),newSn);
      }
    }

    String xc = variableContext.getParameter(seqPrefix+"tokencount");
    if (xc != null)
    {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(WebcrawlerConfig.NODE_ACCESS))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount)
      {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = seqPrefix+"accessop"+accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix+"spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode(WebcrawlerConfig.NODE_ACCESS);
        node.setAttribute(WebcrawlerConfig.ATTR_TOKEN,accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode(WebcrawlerConfig.NODE_ACCESS);
        node.setAttribute(WebcrawlerConfig.ATTR_TOKEN,accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    int j;
    boolean seenAny;

    String seeds = "";
    String inclusions = ".*\n";
    String exclusions = "";
    String inclusionsIndex = ".*\n";
    String exclusionsIndex = "";
    String exclusionsContentIndex = "";

    boolean includeMatching = false;
    Set<String> excludedHeaders = new HashSet<String>();
    
    int i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_SEEDS))
      {
        seeds = sn.getValue();
        if (seeds == null)
          seeds = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDES))
      {
        inclusions = sn.getValue();
        if (inclusions == null)
          inclusions = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDES))
      {
        exclusions = sn.getValue();
        if (exclusions == null)
          exclusions = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDESINDEX))
      {
        inclusionsIndex = sn.getValue();
        if (inclusionsIndex == null)
          inclusionsIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESINDEX))
      {
        exclusionsIndex = sn.getValue();
        if (exclusionsIndex == null)
          exclusionsIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX))
      {
        exclusionsContentIndex = sn.getValue();
        if (exclusionsContentIndex == null)
        	exclusionsContentIndex = "";
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_LIMITTOSEEDS))
      {
        String value = sn.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        if (value == null || value.equals("false"))
          includeMatching = false;
        else
          includeMatching = true;
      }
      else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDEHEADER))
      {
        String value = sn.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        excludedHeaders.add(value);
      }
    }
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.SeedsColon") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(seeds);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    i = 0;
    int l = 0;
    seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_URLSPEC))
      {
        if (l == 0)
        {
          out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLCanonicalization") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.URLRegexp") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Description") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.Reorder") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveJSPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveASPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemovePHPSessions") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.RemoveBVSessions") + "</nobr></td>\n"+
"        </tr>\n"
          );
        }
        String regexpString = sn.getAttributeValue(WebcrawlerConfig.ATTR_REGEXP);
        String description = sn.getAttributeValue(WebcrawlerConfig.ATTR_DESCRIPTION);
        if (description == null)
          description = "";
        String allowReorder = sn.getAttributeValue(WebcrawlerConfig.ATTR_REORDER);
        String allowReorderOutput;
        if (allowReorder == null || allowReorder.length() == 0)
          allowReorderOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
        else
          allowReorderOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
        String allowJavaSessionRemoval = sn.getAttributeValue(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL);
        String allowJavaSessionRemovalOutput;
        if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
          allowJavaSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
        else
          allowJavaSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
        String allowASPSessionRemoval = sn.getAttributeValue(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL);
        String allowASPSessionRemovalOutput;
        if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
          allowASPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
        else
          allowASPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
        String allowPHPSessionRemoval = sn.getAttributeValue(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL);
        String allowPHPSessionRemovalOutput;
        if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
          allowPHPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
        else
          allowPHPSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
        String allowBVSessionRemoval = sn.getAttributeValue(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL);
        String allowBVSessionRemovalOutput;
        if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
          allowBVSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.no");
        else
          allowBVSessionRemovalOutput = Messages.getBodyString(locale, "WebcrawlerConnector.yes");
          
        out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+Encoder.bodyEscape(regexpString)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\">"+Encoder.bodyEscape(description)+"</td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allowReorderOutput+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allowJavaSessionRemovalOutput+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allowASPSessionRemovalOutput+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allowPHPSessionRemovalOutput+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+allowBVSessionRemovalOutput+"</nobr></td>\n"+
"        </tr>\n"
        );
        l++;
      }
    }
    if (l > 0)
    {
      out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoCanonicalizationSpecified") + "</nobr></td></tr>\n"
      );
    }

    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    i = 0;
    seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_MAP))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.URLMappingsColon")+"</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String match = sn.getAttributeValue(WebcrawlerConfig.ATTR_MATCH);
        String map = sn.getAttributeValue(WebcrawlerConfig.ATTR_MAP);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(match)+"</nobr>\n"
        );
        if (map != null && map.length() > 0)
        {
          out.print(
"      &nbsp;--&gt;&nbsp;<nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(map)+"</nobr>\n"
          );
        }
        out.print(
"      <br/>\n"
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
"  <tr><td class=\"message\" colspan=\"2\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.NoMappingsSpecifiedWillAcceptAllUrls")+"</nobr></td></tr>\n"
      );
    }

    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeOnlyHostsMatchingSeeds") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"    "+(includeMatching?Messages.getBodyString(locale,"WebcrawlerConnector.yes"):Messages.getBodyString(locale,"WebcrawlerConnector.no"))+"\n"+
"    </td>\n"+
"  </tr>\n"
    );

    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeInCrawl") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(inclusions);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.IncludeInIndex") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(inclusionsIndex);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeFromCrawl") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(exclusions);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeFromIndex") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(exclusionsIndex);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.ExcludeContentFromIndex") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    try
    {
      java.io.Reader str = new java.io.StringReader(exclusionsContentIndex);
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
            out.print(
"      <nobr>"+Encoder.bodyEscape(nextString)+"</nobr><br/>\n"
            );
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
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"    \n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_ACCESS))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.AccessTokens") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue(WebcrawlerConfig.ATTR_TOKEN);
        out.print(
"      "+Encoder.bodyEscape(token)+"<br/>\n"
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
"  <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"WebcrawlerConnector.NoAccessTokensSpecified") + "</nobr></td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"WebcrawlerConnector.ExcludedHeadersColon")+"</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    for (String excludedHeader : excludedHeaders)
    {
      out.print(
"      "+Encoder.bodyEscape(excludedHeader)+"<br/>\n"
      );
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }

  // Protected methods and classes

  /** Calculate the event name for session login.
  */
  protected String makeSessionLoginEventName(INamingActivity activities, String sequenceKey)
  {
    return activities.createGlobalString("webcrawler:session:"+sequenceKey);
  }

  /** Calculate the event name for DNS access.
  */
  protected String makeDNSEventName(INamingActivity activities, String hostNameKey)
  {
    // Use the connector unique id, and use a convention that guarantees uniqueness.
    return activities.createGlobalString("webcrawler:dns:"+hostNameKey);
  }

  /** Look up an ipaddress given a non-canonical host name.
  *@return appropriate status.
  */
  protected int lookupIPAddress(String documentIdentifier, IProcessActivity activities, String hostName, long currentTime, StringBuilder ipAddressBuffer)
    throws ManifoldCFException, ServiceInterruption
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
      rval = protocol.toLowerCase(Locale.ROOT)+":"+rval;
    return rval;
  }

  /** Construct a name for the global web-connector robots event.
  */
  protected String makeRobotsEventName(INamingActivity versionActivities, String robotsKey)
  {
    // Use the connector unique id, and use a convention that guarantees uniqueness.
    return versionActivities.createGlobalString("webcrawler:robots:"+robotsKey);
  }

  /** Check robots to see if fetch is allowed.
  *@return appropriate resultstatus code.
  */
  protected int checkFetchAllowed(String documentIdentifier, String protocol, String hostIPAddress, int port, PageCredentials credential,
    IKeystoreManager trustStore, String hostName, String[] binNames, long currentTime, String pathString, IProcessActivity versionActivities, int connectionLimit,
    String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
    throws ManifoldCFException, ServiceInterruption
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
        IThrottledConnection connection = ThrottledFetcher.getConnection(currentContext,throttleGroupName,
          protocol,hostIPAddress,port,credential,
          trustStore,throttleDescription,binNames,connectionLimit,
          proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword,
          socketTimeoutMilliseconds,connectionTimeoutMilliseconds,
          versionActivities);
        try
        {
          connection.beginFetch(FETCH_ROBOTS);
          try
          {
            connection.executeFetch("/robots.txt",userAgent,from,true,hostName,null,null);
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
            catch (ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new ManifoldCFException("Interrupted: "+e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
          }
          catch (ConnectTimeoutException e)
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
            catch (ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new ManifoldCFException("Interrupted: "+e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
            }
            catch (IOException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
          }
          catch (InterruptedIOException e)
          {
            //Logging.connectors.warn("IO interruption seen",e);
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
            catch (ConnectTimeoutException e2)
            {
              Logging.connectors.warn("Web: Couldn't clear robots cache: "+e2.getMessage(),e2);
            }
            catch (InterruptedIOException e2)
            {
              //Logging.connectors.warn("IO interruption seen",e2);
              throw new ManifoldCFException("Interrupted: "+e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
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
  *@param parentIdentifier the identifier of the document in which the raw url was found, or null if none.
  *@param rawURL the starting, un-normalized, un-canonicalized URL.
  *@param filter the filter object, used to remove unmatching URLs.
  *@return the canonical URL (the document identifier), or null if the url was illegal.
  */
  protected String makeDocumentIdentifier(String parentIdentifier, String rawURL, DocumentURLFilter filter)
    throws ManifoldCFException
  {
    try
    {
      WebURL url;
      if (parentIdentifier != null)
      {
        WebURL parentURL = new WebURL(parentIdentifier);
        url = parentURL.resolve(rawURL);
      }
      else
        url = new WebURL(rawURL);

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
      if (!understoodProtocols.contains(protocol))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because it has an unsupported protocol '"+protocol+"'");
        return null;
      }
      if (!filter.isHostLegal(host))
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Can't use url '"+rawURL+"' because its host is not found in the seeds ('"+host+"')");
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
  protected String doCanonicalization(DocumentURLFilter filter, WebURL url)
    throws ManifoldCFException, java.net.URISyntaxException
  {
    // First, we have to figure out what the canonicalization policy is.
    // To do that, we need to do a regexp match against the COMPLETE raw url.
    // Put it back into the URL without the ref, and with the modified query and path parts.
    String pathString = url.getPath();
    String queryString = url.getRawQuery();

    WebURL rawURI = new WebURL(url.getScheme(),url.getHost(),url.getPort(),pathString,queryString);
    String completeRawURL = rawURI.toASCIIString();

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

        StringBuilder newString = new StringBuilder();
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
        StringBuilder newString = new StringBuilder();
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
    url = new WebURL(url.getScheme(),url.getHost(),url.getPort(),pathString,queryString);
    String rval = url.toASCIIString();
    return rval;
  }

  /** Code to check if data is interesting, based on response code and content type.
  */
  protected boolean isContentInteresting(IFingerprintActivity activities, String documentIdentifier, int response, String contentType)
    throws ServiceInterruption, ManifoldCFException
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

    String strippedContentType = contentType;
    int pos = strippedContentType.indexOf(";");
    if (pos != -1)
      strippedContentType = strippedContentType.substring(0,pos);
    strippedContentType = strippedContentType.trim();

    // There are presumably mime types we can extract links from that we can't index?
    if (interestingMimeTypeMap.contains(strippedContentType))
      return true;
    
    boolean rval = activities.checkMimeTypeIndexable(contentType);
    if (rval == false && Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("Web: For document '"+documentIdentifier+"', not fetching because output connector does not want mimetype '"+contentType+"'");
    return rval;
  }
  
  /** Convert a document identifier to filename.
   * @param documentIdentifier
   * @return
   * @throws URISyntaxException
   */
  protected String documentIdentifiertoFileName(String documentIdentifier) 
    throws URISyntaxException
  {
    StringBuffer path = new StringBuffer();
    URI uri = null;

    uri = new URI(documentIdentifier);

    if (uri.getRawPath() != null) {
      if (uri.getRawPath().equals("")) {
        path.append("");
      } else if (uri.getRawPath().equals("/")) {
        path.append("index.html");
      } else if (uri.getRawPath().length() != 0) {
        if (uri.getRawPath().endsWith("/")) {
          path.append("index.html");
        } else {
          String[] names = uri.getRawPath().split("/"); 
          path.append(names[names.length - 1]);
        } 
      }
    }

    if (path.length() > 0) {
      if (uri.getRawQuery() != null) {
        path.append("?");
        path.append(uri.getRawQuery());
      }
    }

    return path.toString();
  }

  /** Find a redirection URI, if it exists */
  protected String findRedirectionURI(String currentURI)
    throws ManifoldCFException
  {
    FindRedirectionHandler handler = new FindRedirectionHandler(currentURI);
    handleRedirects(currentURI,handler);
    return handler.getTargetURI();
  }

  /** Find matching HTML form data, if present.  Return null if not. */
  protected FormData findHTMLForm(String currentURI, LoginParameters lp)
    throws ManifoldCFException
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
    throws ManifoldCFException
  {
    if (lp == null || lp.getPreferredRedirectionPattern() == null)
      return null;

    FindPreferredRedirectionHandler handler = new FindPreferredRedirectionHandler(currentURI,lp.getPreferredRedirectionPattern());
    handleRedirects(currentURI,handler);
    handler.applyOverrides(lp);
    return handler.getTargetURI();
  }

  /** Find existence of specific content on the page (never finds a URL) */
  protected String findSpecifiedContent(String currentURI, LoginParameters lp)
    throws ManifoldCFException
  {
    if (lp == null || lp.getContentPattern() == null)
      return null;
    
    FindContentHandler handler = new FindContentHandler(currentURI,lp.getContentPattern());
    handleHTML(currentURI,handler);
    handler.applyOverrides(lp);
    return handler.getTargetURI();
  }

  /** Find HTML link URI, if present, making sure specified preference is matched. */
  protected String findHTMLLinkURI(String currentURI, LoginParameters lp)
    throws ManifoldCFException
  {
    if (lp == null || lp.getPreferredLinkPattern() == null)
      return null;

    FindHTMLHrefHandler handler = new FindHTMLHrefHandler(currentURI,lp.getPreferredLinkPattern());
    handleHTML(currentURI,handler);
    handler.applyOverrides(lp);
    return handler.getTargetURI();
  }

  /** Code to extract links from an already-fetched document. */
  protected boolean extractLinks(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    throws ManifoldCFException, ServiceInterruption
  {
    ProcessActivityRedirectionHandler redirectHandler = new ProcessActivityRedirectionHandler(documentIdentifier,activities,filter);
    handleRedirects(documentIdentifier,redirectHandler);
    if (Logging.connectors.isDebugEnabled() && redirectHandler.shouldIndex() == false)
      Logging.connectors.debug("Web: Not indexing document '"+documentIdentifier+"' because of redirection");
    // For html, we don't want any actions, because we don't do form submission.
    ProcessActivityHTMLHandler htmlHandler = new ProcessActivityHTMLHandler(documentIdentifier,activities,filter);
    handleHTML(documentIdentifier,htmlHandler);
    if (Logging.connectors.isDebugEnabled() && htmlHandler.shouldIndex() == false)
      Logging.connectors.debug("Web: Not indexing document '"+documentIdentifier+"' because of HTML robots or content tags prohibiting indexing");
    ProcessActivityXMLHandler xmlHandler = new ProcessActivityXMLHandler(documentIdentifier,activities,filter);
    handleXML(documentIdentifier,xmlHandler);
    if (Logging.connectors.isDebugEnabled() && xmlHandler.shouldIndex() == false)
      Logging.connectors.debug("Web: Not indexing document '"+documentIdentifier+"' because of XML robots or content tags prohibiting indexing");
    // May add more later for other extraction tasks.
    return htmlHandler.shouldIndex() && redirectHandler.shouldIndex() && xmlHandler.shouldIndex();
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
    @Override
    public void noteDiscoveredLink(String rawURL)
      throws ManifoldCFException
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
    
    public boolean shouldIndex()
    {
      return true;
    }

  }

  /** Class that describes HTML handling */
  protected class ProcessActivityHTMLHandler extends ProcessActivityLinkHandler implements IHTMLHandler
  {
    boolean allowIndex = true;
    boolean allowFollow = true;
    
    /** Constructor. */
    public ProcessActivityHTMLHandler(String documentIdentifier, IProcessActivity activities, DocumentURLFilter filter)
    {
      super(documentIdentifier,activities,filter,"html",REL_LINK);
    }

    /** Decide whether we should index. */
    public boolean shouldIndex()
    {
      return allowIndex;
    }
    
    /** Note a character of text.
    * Structured this way to keep overhead low for handlers that don't use text.
    */
    @Override
    public void noteTextCharacter(char textCharacter)
      throws ManifoldCFException
    {
    }

    /** Note a meta tag */
    @Override
    public void noteMetaTag(Map metaAttributes)
      throws ManifoldCFException
    {
      String name = (String)metaAttributes.get("name");
      if (name != null && name.toLowerCase(Locale.ROOT).equals("robots"))
      {
        String contentValue = (String)metaAttributes.get("content");
        if (contentValue != null)
        {
          contentValue = contentValue.toLowerCase(Locale.ROOT);
          // Parse content value
          try
          {
            String[] contentValues = contentValue.split(",");
            int i = 0;
            while (i < contentValues.length)
            {
              String cv = contentValues[i++].trim();
              if (cv.equals("index"))
                allowIndex = true;
              else if (cv.equals("noindex"))
                allowIndex = false;
              else if (cv.equals("none"))
              {
                allowFollow = false;
                allowIndex = false;
              }
              else if (cv.equals("follow"))
                allowFollow = true;
              else if (cv.equals("nofollow"))
                allowFollow = false;
            }
          }
          catch (PatternSyntaxException e)
          {
            throw new ManifoldCFException(e.getMessage(),e);
          }
        }
      }
    }

    /** Note the start of a form */
    @Override
    public void noteFormStart(Map formAttributes)
      throws ManifoldCFException
    {
    }

    /** Note an input tag */
    @Override
    public void noteFormInput(Map inputAttributes)
      throws ManifoldCFException
    {
    }

    /** Note the end of a form */
    @Override
    public void noteFormEnd()
      throws ManifoldCFException
    {
    }

    /** Note discovered href */
    @Override
    public void noteAHREF(String rawURL)
      throws ManifoldCFException
    {
      if (allowFollow)
        noteDiscoveredLink(rawURL);
    }

    /** Note discovered href */
    @Override
    public void noteLINKHREF(String rawURL)
      throws ManifoldCFException
    {
      if (allowFollow)
        noteDiscoveredLink(rawURL);
    }

    /** Note discovered IMG SRC */
    @Override
    public void noteIMGSRC(String rawURL)
      throws ManifoldCFException
    {
      if (allowFollow)
        noteDiscoveredLink(rawURL);
    }

    /** Note discovered FRAME SRC */
    @Override
    public void noteFRAMESRC(String rawURL)
      throws ManifoldCFException
    {
      if (allowFollow)
        noteDiscoveredLink(rawURL);
    }

    @Override
    public void finishUp()
      throws ManifoldCFException
    {
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

    public boolean shouldIndex()
    {
      return true;
    }
    
    /** Inform the world of a discovered ttl value.
    *@param rawTtlValue is the raw discovered ttl value.  Null indicates we should set the default.
    */
    public void noteDiscoveredTtlValue(String rawTtlValue)
      throws ManifoldCFException
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
    throws ManifoldCFException
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
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      int responseCode = cache.getResponseCode(documentURI);
      if (responseCode != 200)
        return;

      // We ONLY look for XML if the content type *says* it is XML.
      String contentType = extractContentType(cache.getContentType(documentURI));
      String mimeType = extractMimeType(contentType);
      boolean isXML =
        mimeType.equals("text/xml") ||
        mimeType.equals("application/rss+xml") ||
        mimeType.equals("application/xml") ||
        mimeType.equals("application/atom+xml") ||
        mimeType.equals("application/xhtml+xml") ||
        mimeType.equals("text/XML") ||
        mimeType.equals("application/rdf+xml") ||
        mimeType.equals("text/application") ||
        mimeType.equals("XML");

      if (!isXML)
        return;

      // OK, it's XML.  Now what?  Well, we get the encoding, and we verify that it is text, then we try to get links
      // from it presuming it is an RSS feed.

      String encoding = extractEncoding(contentType);

      InputStream is = cache.getData(documentURI);
      if (is == null)
      {
        Logging.connectors.error("WEB: Document '"+documentURI+"' should be in cache but isn't");
        return;
      }
      try
      {
        // Parse the document.  This will cause various things to occur, within the instantiated XMLParsingContext class.
        Parser p = new Parser();
        XMLFuzzyHierarchicalParseState x = new XMLFuzzyHierarchicalParseState();
        OuterContextClass c = new OuterContextClass(x,documentURI,handler);
        x.setContext(c);
        try
        {
          p.parseWithCharsetDetection(encoding,is,x);
          c.checkIfValidFeed();
        }
        finally
        {
          x.cleanup();
        }
      }
      finally
      {
        is.close();
      }
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (ConnectTimeoutException e)
    {
      throw new ManifoldCFException("Socket connect timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      //Logging.connectors.warn("IO interruption seen",e);

      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
  }

  /** This class handles the outermost XML context for the feed document. */
  protected class OuterContextClass extends XMLParsingContext
  {
    /** Keep track of the number of valid feed signals we saw */
    protected int outerTagCount = 0;
    /** The document uri */
    protected String documentURI;
    /** The link handler */
    protected IXMLHandler handler;

    public OuterContextClass(XMLFuzzyHierarchicalParseState theStream, String documentURI, IXMLHandler handler)
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
    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      if (localName.equals("rss"))
      {
        // RSS feed detected
        outerTagCount++;
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Parsed bottom-level XML for RSS document '"+documentURI+"'");
        return new RSSContextClass(theStream,namespace,localName,qName,atts,documentURI,handler);
      }
      else if (localName.equals("RDF"))
      {
        // RDF/Atom feed detected
        outerTagCount++;
        return new RDFContextClass(theStream,namespace,localName,qName,atts,documentURI,handler);
      }
      else if (localName.equals("feed"))
      {
        // Basic feed detected
        outerTagCount++;
        return new FeedContextClass(theStream,namespace,localName,qName,atts,documentURI,handler);
      }
      else if (localName.equals("urlset") || localName.equals("sitemapindex"))
      {
        // Sitemap detected
        outerTagCount++;
        return new UrlsetContextClass(theStream,namespace,localName,qName,atts,documentURI,handler);
      }

      // The default action is to establish a new default context.
      return super.beginTag(namespace,localName,qName,atts);
    }

    /** Handle the tag ending */
    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext context = theStream.getContext();
      String tagName = context.getLocalname();
      if (tagName.equals("RDF"))
      {
        ((RDFContextClass)context).process();
      }
      else if (tagName.equals("feed"))
      {
        ((FeedContextClass)context).process();
      }
      else if (tagName.equals("urlset") || tagName.equals("sitemapindex"))
      {
        ((UrlsetContextClass)context).process();
      }
      else
        super.endTag();
    }

  }

  protected class RSSContextClass extends XMLParsingContext
  {
    /** The document identifier */
    protected String documentURI;
    /** Link notification interface */
    protected IXMLHandler handler;

    public RSSContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespace,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // Handle each channel
      if (localName.equals("channel"))
      {
        // Channel detected
        return new RSSChannelContextClass(theStream,namespace,localName,qName,atts,documentURI,handler);
      }

      // Skip everything else.
      return super.beginTag(namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      // If it's our channel tag, process global channel information
      XMLParsingContext context = theStream.getContext();
      String tagName = context.getLocalname();
      if (tagName.equals("channel"))
      {
        ((RSSChannelContextClass)context).process();
      }
      else
        super.endTag();
    }
  }

  protected class RSSChannelContextClass extends XMLParsingContext
  {
    /** The document identifier */
    protected String documentURI;
    /** Link handler */
    protected IXMLHandler handler;

    /** TTL value is set on a per-channel basis */
    protected String ttlValue = null;

    public RSSChannelContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespace,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else if (localName.equals("item"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new RSSItemContextClass(theStream,namespace,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringParsingContext)theContext).getValue();
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
      throws ManifoldCFException
    {
      // Deal with the ttlvalue, if it was found
      // Use the ttl value as a signal for when we ought to look at this feed again.  If not present, use the default.
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class RSSItemContextClass extends XMLParsingContext
  {
    protected String guidField = null;
    protected String linkField = null;

    public RSSItemContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts)
    {
      super(theStream,namespace,localName,qName,atts);
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("link"))
      {
        // "link" tag
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else if (localName.equals("guid"))
      {
        // "guid" tag
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespace,localName,qName,atts);
      }
    }

    /** Convert the individual sub-fields of the item context into their final forms */
    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("link"))
      {
        linkField = ((XMLStringParsingContext)theContext).getValue();
      }
      else if (theTag.equals("guid"))
      {
        guidField = ((XMLStringParsingContext)theContext).getValue();
      }
      else
      {
        super.endTag();
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws ManifoldCFException
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

  protected class RDFContextClass extends XMLParsingContext
  {
    /** The document identifier */
    protected String documentURI;
    /** XML handler */
    protected IXMLHandler handler;

    /** ttl value */
    protected String ttlValue = null;

    public RDFContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespace,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else if (localName.equals("item"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new RDFItemContextClass(theStream,namespace,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringParsingContext)theContext).getValue();
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
      throws ManifoldCFException
    {
      // Deal with the ttlvalue, if it was found
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class RDFItemContextClass extends XMLParsingContext
  {
    protected String linkField = null;

    public RDFItemContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts)
    {
      super(theStream,namespace,localName,qName,atts);
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("link"))
      {
        // "link" tag
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespace,localName,qName,atts);
      }
    }

    /** Convert the individual sub-fields of the item context into their final forms */
    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("link"))
      {
        linkField = ((XMLStringParsingContext)theContext).getValue();
      }
      else
      {
        super.endTag();
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws ManifoldCFException
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

  protected class FeedContextClass extends XMLParsingContext
  {
    /** The document identifier */
    protected String documentURI;
    /** XML handler */
    protected IXMLHandler handler;

    /** ttl value */
    protected String ttlValue = null;

    public FeedContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespace,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("ttl"))
      {
        // TTL value seen.  Prepare to record it, as a string.
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else if (localName.equals("entry"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new FeedItemContextClass(theStream,namespace,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("ttl"))
        // If the current context must be the TTL one, record its data value.
        ttlValue = ((XMLStringParsingContext)theContext).getValue();
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
      throws ManifoldCFException
    {
      // Deal with the ttlvalue, if it was found
      // Use the ttl value as a signal for when we ought to look at this feed again.  If not present, use the default.
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class FeedItemContextClass extends XMLParsingContext
  {
    protected List<String> linkField = new ArrayList<String>();

    public FeedItemContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts)
    {
      super(theStream,namespace,localName,qName,atts);
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "ttl" and "item", nothing else.
      if (localName.equals("link"))
      {
        // "link" tag
        String ref = atts.get("href");
        if (ref != null && ref.length() > 0)
          linkField.add(ref);
        return super.beginTag(namespace,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespace,localName,qName,atts);
      }
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws ManifoldCFException
    {
      if (linkField.size() > 0)
      {
        for (String linkValue : linkField)
        {
          String[] links = linkValue.split(", ");
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
  }

  protected class UrlsetContextClass extends XMLParsingContext
  {
    /** The document identifier */
    protected String documentURI;
    /** XML handler */
    protected IXMLHandler handler;

    /** ttl value */
    protected String ttlValue = null;

    public UrlsetContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts, String documentURI, IXMLHandler handler)
    {
      super(theStream,namespace,localName,qName,atts);
      this.documentURI = documentURI;
      this.handler = handler;
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "url", nothing else.
      if (localName.equals("url") || localName.equals("sitemap"))
      {
        // Item seen.  We don't need any of the attributes etc., but we need to start a new context.
        return new UrlsetItemContextClass(theStream,namespace,localName,qName,atts);
      }
      // Skip everything else.
      return super.beginTag(namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("url") || theTag.equals("sitemap"))
      {
        // It's an item.
        UrlsetItemContextClass itemContext = (UrlsetItemContextClass)theContext;
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
      throws ManifoldCFException
    {
      // Deal with the ttlvalue, if it was found
      // Use the ttl value as a signal for when we ought to look at this feed again.  If not present, use the default.
      handler.noteDiscoveredTtlValue(ttlValue);
    }
  }

  protected class UrlsetItemContextClass extends XMLParsingContext
  {
    protected String linkField = null;

    public UrlsetItemContextClass(XMLFuzzyHierarchicalParseState theStream, String namespace, String localName, String qName, Map<String,String> atts)
    {
      super(theStream,namespace,localName,qName,atts);
    }

    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      // The tags we care about are "loc", nothing else.
      if (localName.equals("loc"))
      {
        // "loc" tag
        return new XMLStringParsingContext(theStream,namespace,localName,qName,atts);
      }
      else
      {
        // Skip everything else.
        return super.beginTag(namespace,localName,qName,atts);
      }
    }

    /** Convert the individual sub-fields of the item context into their final forms */
    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      XMLParsingContext theContext = theStream.getContext();
      String theTag = theContext.getLocalname();
      if (theTag.equals("loc"))
      {
        linkField = ((XMLStringParsingContext)theContext).getValue();
      }
      else
      {
        super.endTag();
      }
    }

    protected void tagCleanup()
      throws ManifoldCFException
    {
    }

    /** Process the data accumulated for this item */
    public void process(IXMLHandler handler)
      throws ManifoldCFException
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
    throws ManifoldCFException
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
      String contentType = extractContentType(cache.getContentType(documentURI));
      String encoding = extractEncoding(contentType);
      if (encoding == null)
        encoding = StandardCharsets.UTF_8.name();
      
      // Search for A HREF tags in the document stream.  This is brain-dead link location
      InputStream is = cache.getData(documentURI);
      if (is == null)
      {
        Logging.connectors.error("WEB: Document '"+documentURI+"' should be in cache but isn't");
        return;
      }

      try
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Document '"+documentURI+"' is text, with encoding '"+encoding+"'; link extraction starting");

        // Instantiate the parser, and call the right method
        Parser p = new Parser();
        p.parseWithoutCharsetDetection(encoding,is,new FormParseState(handler));
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
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (ConnectTimeoutException e)
    {
      throw new ManifoldCFException("Socket connect timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      //Logging.connectors.warn("IO interruption seen",e);

      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
    }
  }

  /** Is the document text, as far as we can tell? */
  protected boolean isDocumentText(String documentURI)
    throws ManifoldCFException
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
      throw new ManifoldCFException("Socket timeout exception accessing cached document: "+e.getMessage(),e);
    }
    catch (ConnectTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception accessing cached document: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception accessing cached document: "+e.getMessage(),e);
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

  /** Check if character is not typical ASCII or utf-8. */
  protected static boolean isStrange(byte x)
  {
    return (x >= 0 && x < 32) && (!isWhiteSpace(x));
  }

  /** Check if a byte is a whitespace character. */
  protected static boolean isWhiteSpace(byte x)
  {
    return (x == 0x09 || x == 0x0a || x == 0x0d || x == 0x20);
  }

  /** Read a string as a sequence of individual expressions, urls, etc.
  */
  protected static List<String> stringToArray(String input)
  {
    List<String> list = new ArrayList<String>();
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
            nextString = nextString.trim();
            if (nextString.length() == 0)
              continue;
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
  protected static void compileList(List<Pattern> output, List<String> input)
    throws ManifoldCFException
  {
    int i = 0;
    while (i < input.size())
    {
      String inputString = input.get(i++);
      try
      {
        output.add(Pattern.compile(inputString));
      }
      catch (PatternSyntaxException e)
      {
        throw new ManifoldCFException("Mapping regular expression '"+inputString+"' is illegal: "+e.getMessage(),e);
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
    throws ManifoldCFException
  {
    return trustsDescription.getTrustStore(documentIdentifier);
  }

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(Specification spec)
  {
    Set<String> map = new HashSet<String>();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(WebcrawlerConfig.NODE_ACCESS))
      {
        String token = sn.getAttributeValue(WebcrawlerConfig.ATTR_TOKEN);
        map.add(token);
      }
    }

    String[] rval = new String[map.size()];
    Iterator<String> iter = map.iterator();
    i = 0;
    while (iter.hasNext())
    {
      rval[i++] = iter.next();
    }
    return rval;
  }

  /** Read a document specification to get a set of excluded headers */
  protected static Set<String> findExcludedHeaders(Specification spec)
    throws ManifoldCFException
  {
    Set<String> rval = new HashSet<String>();
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals(WebcrawlerConfig.NODE_EXCLUDEHEADER))
      {
        String value = n.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
        rval.add(value);
      }
    }
    return rval;
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
    protected final String name;
    protected final String value;

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

  /** Evaluator token.
  */
  protected static class EvaluatorToken
  {
    public final static int TYPE_GROUP = 0;
    public final static int TYPE_TEXT = 1;
    public final static int TYPE_COMMA = 2;

    public final static int GROUPSTYLE_NONE = 0;
    public final static int GROUPSTYLE_LOWER = 1;
    public final static int GROUPSTYLE_UPPER = 2;
    public final static int GROUPSTYLE_MIXED = 3;

    protected int type;
    protected int groupNumber = -1;
    protected int groupStyle = GROUPSTYLE_NONE;
    protected String textValue = null;

    public EvaluatorToken()
    {
      type = TYPE_COMMA;
    }

    public EvaluatorToken(int groupNumber, int groupStyle)
    {
      type = TYPE_GROUP;
      this.groupNumber = groupNumber;
      this.groupStyle = groupStyle;
    }

    public EvaluatorToken(String text)
    {
      type = TYPE_TEXT;
      this.textValue = text;
    }

    public int getType()
    {
      return type;
    }

    public int getGroupNumber()
    {
      return groupNumber;
    }

    public int getGroupStyle()
    {
      return groupStyle;
    }

    public String getTextValue()
    {
      return textValue;
    }

  }


  /** Token stream.
  */
  protected static class EvaluatorTokenStream
  {
    protected String text;
    protected int pos;
    protected EvaluatorToken token = null;

    /** Constructor.
    */
    public EvaluatorTokenStream(String text)
    {
      this.text = text;
      this.pos = 0;
    }

    /** Get current token.
    */
    public EvaluatorToken peek()
      throws ManifoldCFException
    {
      if (token == null)
      {
        token = nextToken();
      }
      return token;
    }

    /** Go on to next token.
    */
    public void advance()
    {
      token = null;
    }

    protected EvaluatorToken nextToken()
      throws ManifoldCFException
    {
      char x;
      // Fetch the next token
      while (true)
      {
        if (pos == text.length())
          return null;
        x = text.charAt(pos);
        if (x > ' ')
          break;
        pos++;
      }

      StringBuilder sb;

      if (x == '"')
      {
        // Parse text
        pos++;
        sb = new StringBuilder();
        while (true)
        {
          if (pos == text.length())
            break;
          x = text.charAt(pos);
          pos++;
          if (x == '"')
          {
            break;
          }
          if (x == '\\')
          {
            if (pos == text.length())
              break;
            x = text.charAt(pos++);
          }
          sb.append(x);
        }

        return new EvaluatorToken(sb.toString());
      }

      if (x == ',')
      {
        pos++;
        return new EvaluatorToken();
      }

      // Eat number at beginning
      sb = new StringBuilder();
      while (true)
      {
        if (pos == text.length())
          break;
        x = text.charAt(pos);
        if (x >= '0' && x <= '9')
        {
          sb.append(x);
          pos++;
          continue;
        }
        break;
      }
      String numberValue = sb.toString();
      int groupNumber = 0;
      if (numberValue.length() > 0)
        groupNumber = new Integer(numberValue).intValue();
      // Save the next char position
      int modifierPos = pos;
      // Go to the end of the word
      while (true)
      {
        if (pos == text.length())
          break;
        x = text.charAt(pos);
        if (x == ',' || x >= '0' && x <= '9' || x <= ' ' && x >= 0)
          break;
        pos++;
      }

      int style = EvaluatorToken.GROUPSTYLE_NONE;
      if (modifierPos != pos)
      {
        String modifier = text.substring(modifierPos,pos);
        if (modifier.startsWith("u"))
          style = EvaluatorToken.GROUPSTYLE_UPPER;
        else if (modifier.startsWith("l"))
          style = EvaluatorToken.GROUPSTYLE_LOWER;
        else if (modifier.startsWith("m"))
          style = EvaluatorToken.GROUPSTYLE_MIXED;
        else
          throw new ManifoldCFException("Unknown style: "+modifier);
      }
      return new EvaluatorToken(groupNumber,style);
    }
  }

  /** Class representing a URL regular expression match, for the purposes of determining canonicalization policy */
  protected static class CanonicalizationPolicy
  {
    protected final Pattern matchPattern;
    protected final boolean reorder;
    protected final boolean removeJavaSession;
    protected final boolean removeAspSession;
    protected final boolean removePhpSession;
    protected final boolean removeBVSession;

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
    protected final List<CanonicalizationPolicy> rules = new ArrayList<CanonicalizationPolicy>();

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
        CanonicalizationPolicy rule = rules.get(i++);
        if (rule.checkMatch(url))
          return rule;
      }
      return null;
    }
  }

  /** Class representing a mapping rule */
  protected static class MappingRule
  {
    protected final Pattern matchPattern;
    protected final String evalExpression;

    public MappingRule(Pattern matchPattern, String evalExpression)
    {
      this.matchPattern = matchPattern;
      this.evalExpression = evalExpression;
    }

    public boolean checkMatch(String url)
    {
      Matcher matcher = matchPattern.matcher(url);
      return matcher.matches();
    }

    public String map(String url)
      throws ManifoldCFException
    {
      // Create a matcher, and attempt to do a match
      Matcher matcher = matchPattern.matcher(url);
      if (!matcher.matches())
      {
        return null;
      }

      // A match!  Now, interpret the output expression
      if (evalExpression == null || evalExpression.length() == 0)
        return url;

      StringBuilder sb = new StringBuilder();
      EvaluatorTokenStream et = new EvaluatorTokenStream(evalExpression);

      while (true)
      {
        EvaluatorToken t = et.peek();
        if (t == null)
          break;
        switch (t.getType())
        {
        case EvaluatorToken.TYPE_COMMA:
          et.advance();
          break;
        case EvaluatorToken.TYPE_GROUP:
          et.advance();
          String groupValue = matcher.group(t.getGroupNumber());
          switch (t.getGroupStyle())
          {
          case EvaluatorToken.GROUPSTYLE_NONE:
            sb.append(groupValue);
            break;
          case EvaluatorToken.GROUPSTYLE_LOWER:
            sb.append(groupValue.toLowerCase(Locale.ROOT));
            break;
          case EvaluatorToken.GROUPSTYLE_UPPER:
            sb.append(groupValue.toUpperCase(Locale.ROOT));
            break;
          case EvaluatorToken.GROUPSTYLE_MIXED:
            if (groupValue.length() > 0)
            {
              sb.append(groupValue.substring(0,1).toUpperCase(Locale.ROOT));
              sb.append(groupValue.substring(1).toLowerCase(Locale.ROOT));
            }
            break;
          default:
            throw new ManifoldCFException("Illegal group style");
          }
          break;
        case EvaluatorToken.TYPE_TEXT:
          et.advance();
          sb.append(t.getTextValue());
          break;
        default:
          throw new ManifoldCFException("Illegal token type");
        }
      }
      return sb.toString();
    }

  }

  /** Class that represents all mappings */
  protected static class MappingRules
  {
    protected final List<MappingRule> mappings = new ArrayList<MappingRule>();

    public MappingRules()
    {
    }

    public void add(MappingRule rule)
    {
      mappings.add(rule);
    }

    public boolean isMatch(String url)
    {
      if (mappings.size() == 0)
        return true;
      for (MappingRule p : mappings)
      {
        if (p.checkMatch(url))
          return true;
      }
      return false;
    }

    public String map(String url)
      throws ManifoldCFException
    {
      if (mappings.size() == 0)
        return url;
      for (MappingRule p : mappings)
      {
        String rval = p.map(url);
        if (rval != null)
          return rval;
      }
      return null;
    }
  }

  /** This class describes the url filtering information (for crawling and indexing) obtained from a digested DocumentSpecification.
  */
  protected class DocumentURLFilter
  {
    /** The version string */
    protected String versionString;
    /** Mapping rules */
    protected final MappingRules mappings = new MappingRules();
    /** The arraylist of include patterns */
    protected final List<Pattern> includePatterns = new ArrayList<Pattern>();
    /** The arraylist of exclude patterns */
    protected final List<Pattern> excludePatterns = new ArrayList<Pattern>();
    /** The arraylist of index include patterns */
    protected final List<Pattern> includeIndexPatterns = new ArrayList<Pattern>();
    /** The arraylist of index exclude patterns */
    protected final List<Pattern> excludeIndexPatterns = new ArrayList<Pattern>();
    /** The hash map of seed hosts, to limit urls by, if non-null */
    protected Set<String> seedHosts = null;

    /**List of content exclusion pattern*/
    protected final List<Pattern> excludeContentIndexPatterns = new ArrayList<Pattern>();

    /** Canonicalization policies */
    protected final CanonicalizationPolicies canonicalizationPolicies = new CanonicalizationPolicies();

    /** Process a document specification to produce a filter.
    * Note that we EXPECT the regexp's in the document specification to be properly formed.
    * This should be checked at save time to prevent errors.  Any syntax errors found here
    * will thus cause the include or exclude regexp to be skipped.
    */
    public DocumentURLFilter(Specification spec)
      throws ManifoldCFException
    {
      String includes = ".*";
      String excludes = "";
      String includesIndex = ".*";
      String excludesIndex = "";
      String excludesContentIndex = "";
      String seeds = "";
      List<String> packList = new ArrayList<String>();
      String[] packStuff = new String[2];
      boolean limitToSeeds = false;
      int i = 0;
      while (i < spec.getChildCount())
      {
        SpecificationNode sn = spec.getChild(i++);
        if (sn.getType().equals(WebcrawlerConfig.NODE_MAP))
        {
          String match = sn.getAttributeValue(WebcrawlerConfig.ATTR_MATCH);
          String map = sn.getAttributeValue(WebcrawlerConfig.ATTR_MAP);
          if (match != null && match.length() > 0)
          {
            packStuff[0] = match;
            packStuff[1] = map;
            StringBuilder sb = new StringBuilder();
            packList(sb,packStuff,'=');
            packList.add(sb.toString());
            Pattern p;
            try
            {
              p = Pattern.compile(match);
            }
            catch (java.util.regex.PatternSyntaxException e)
            {
              throw new ManifoldCFException("Regular expression '"+match+"' is illegal: "+e.getMessage(),e);
            }
            if (map == null)
              map = "";
            mappings.add(new MappingRule(p,map));
          }
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_SEEDS))
        {
          // Save the seeds aside; we'll parse them only if we need to.
          seeds = sn.getValue();
          if (seeds == null)
            seeds = "";
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDES))
        {
          includes = sn.getValue();
          if (includes == null)
            includes = "";
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDES))
        {
          excludes = sn.getValue();
          if (excludes == null)
            excludes = "";
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_INCLUDESINDEX))
        {
          includesIndex = sn.getValue();
          if (includesIndex == null)
            includesIndex = "";
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESINDEX))
        {
          excludesIndex = sn.getValue();
          if (excludesIndex == null)
            excludesIndex = "";
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_LIMITTOSEEDS))
        {
          String value = sn.getAttributeValue(WebcrawlerConfig.ATTR_VALUE);
          if (value == null || value.equals(WebcrawlerConfig.ATTRVALUE_FALSE))
            limitToSeeds = false;
          else
            limitToSeeds = true;
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_URLSPEC))
        {
          String urlRegexp = sn.getAttributeValue(WebcrawlerConfig.ATTR_REGEXP);
          if (urlRegexp == null)
            urlRegexp = "";
          String reorder = sn.getAttributeValue(WebcrawlerConfig.ATTR_REORDER);
          boolean reorderValue;
          if (reorder == null)
            reorderValue = false;
          else
          {
            reorderValue = reorder.equals(WebcrawlerConfig.ATTRVALUE_YES);
          }

          String javaSession = sn.getAttributeValue(WebcrawlerConfig.ATTR_JAVASESSIONREMOVAL);
          boolean javaSessionValue;
          if (javaSession == null)
            javaSessionValue = false;
          else
          {
            javaSessionValue = javaSession.equals(WebcrawlerConfig.ATTRVALUE_YES);
          }

          String aspSession = sn.getAttributeValue(WebcrawlerConfig.ATTR_ASPSESSIONREMOVAL);
          boolean aspSessionValue;
          if (aspSession == null)
            aspSessionValue = false;
          else
          {
            aspSessionValue = aspSession.equals(WebcrawlerConfig.ATTRVALUE_YES);
          }

          String phpSession = sn.getAttributeValue(WebcrawlerConfig.ATTR_PHPSESSIONREMOVAL);
          boolean phpSessionValue;
          if (phpSession == null)
            phpSessionValue = false;
          else
          {
            phpSessionValue = phpSession.equals(WebcrawlerConfig.ATTRVALUE_YES);
          }

          String bvSession = sn.getAttributeValue(WebcrawlerConfig.ATTR_BVSESSIONREMOVAL);
          boolean bvSessionValue;
          if (bvSession == null)
            bvSessionValue = false;
          else
          {
            bvSessionValue = bvSession.equals(WebcrawlerConfig.ATTRVALUE_YES);
          }
          try
          {
            canonicalizationPolicies.addRule(new CanonicalizationPolicy(Pattern.compile(urlRegexp),reorderValue,javaSessionValue,aspSessionValue,
              phpSessionValue, bvSessionValue));
          }
          catch (java.util.regex.PatternSyntaxException e)
          {
            throw new ManifoldCFException("Canonicalization regular expression '"+urlRegexp+"' is illegal: "+e.getMessage(),e);
          }
        }
        else if (sn.getType().equals(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX))
        {
          excludesContentIndex = sn.getValue();
          if (excludesContentIndex == null)
            excludesContentIndex = "";
        }
      }

      // Note: format change since MCF 1.7 release
      StringBuilder versionBuffer = new StringBuilder();
      pack(versionBuffer,includesIndex,'+');
      pack(versionBuffer,excludesIndex,'+');
      pack(versionBuffer,excludesContentIndex,'+');
      packList(versionBuffer,packList,'+');
      versionString = versionBuffer.toString();
      
      List<String> list;
      list = stringToArray(includes);
      compileList(includePatterns,list);
      list = stringToArray(excludes);
      compileList(excludePatterns,list);
      list = stringToArray(includesIndex);
      compileList(includeIndexPatterns,list);
      list = stringToArray(excludesIndex);
      compileList(excludeIndexPatterns,list);
      list = stringToArray(excludesContentIndex);
      compileList(excludeContentIndexPatterns,list);

      if (limitToSeeds)
      {
        seedHosts = new HashSet<String>();
        // Parse all URLs, and put their hosts into the hash table.
        // Break up the seeds string and iterate over the results.
        list = stringToArray(seeds);
        // We must only return valid urls here!!!
        int index = 0;
        while (index < list.size())
        {
          String urlCandidate = list.get(index++);
          try
          {
            java.net.URI url = new java.net.URI(urlCandidate);

            String host = url.getHost();

            if (host != null)
              seedHosts.add(host);
          }
          catch (java.net.URISyntaxException e)
          {
            // Skip the entry
          }
          catch (java.lang.IllegalArgumentException e)
          {
            // Skip the entry
          }

        }
      }
    }

    /** Get whatever contribution to the version string should come from this data.
    */
    public String getVersionString()
    {
      // In practice, this is NOT what controls the set that is spidered, but rather the set that is indexed
      return versionString;
    }
    
    /** Check if both a document and host are legal.
    */
    public boolean isDocumentAndHostLegal(String url)
    {
      if (!isDocumentLegal(url))
        return false;
      if (seedHosts == null)
        return true;
      try
      {
        java.net.URI uri = new java.net.URI(url);
        String host = uri.getHost();
        if (host == null)
          return false;
        return isHostLegal(host);
      }
      catch (java.net.URISyntaxException e)
      {
        return false;
      }
      catch (java.lang.IllegalArgumentException e)
      {
        return false;
      }

    }
    
    /** Check if a host is legal.
    */
    public boolean isHostLegal(String host)
    {
      if (seedHosts == null)
        return true;
      return seedHosts.contains(host);
    }
    
    /** Check if the document identifier is legal.
    */
    public boolean isDocumentLegal(String url)
    {
      // First, verify that the url matches one of the patterns in the include list.
      int i = 0;
      while (i < includePatterns.size())
      {
        Pattern p = includePatterns.get(i);
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
        Pattern p = excludePatterns.get(i);
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

    /** Check if the document identifier is indexable, and return the indexing URL if found.
    * @return null if the url doesn't match or should not be ingested, or the new string if it does.
    */
    public String isDocumentIndexable(String url)
      throws ManifoldCFException
    {
      // First, verify that the url matches one of the patterns in the include list.
      int i = 0;
      while (i < includeIndexPatterns.size())
      {
        Pattern p = includeIndexPatterns.get(i);
        Matcher m = p.matcher(url);
        if (m.find())
          break;
        i++;
      }
      if (i == includeIndexPatterns.size())
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Url '"+url+"' is not indexable because no include patterns match it");
        return null;
      }

      // Now make sure it's not in the exclude list.
      i = 0;
      while (i < excludeIndexPatterns.size())
      {
        Pattern p = excludeIndexPatterns.get(i);
        Matcher m = p.matcher(url);
        if (m.find())
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Url '"+url+"' is not indexable because exclude pattern '"+p.toString()+"' matched it");
          return null;
        }
        i++;
      }

      String rval = mappings.map(url);
      if (rval == null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Url '"+url+"' is not indexable because it did not match a mapping rule");
      }

      return rval;
    }

    /** Get canonicalization policies */
    public CanonicalizationPolicies getCanonicalizationPolicies()
    {
      return canonicalizationPolicies;
    }

    public boolean isDocumentContentIndexable(String documentIdentifier) throws ManifoldCFException {
        String content = findSpecifiedContent(documentIdentifier, excludeContentIndexPatterns);
        if (content != null) {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Url '" + documentIdentifier + "' is not indexable because content exclusion pattern was matched");

          return false;
      }
      return true;
    }

    protected String findSpecifiedContent(String currentURI, List<Pattern> patterns) throws ManifoldCFException
    {
      if (excludeContentIndexPatterns.isEmpty()) {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: no content exclusion rule supplied... returning");
        return null;
      }

      FindContentHandler handler = new FindContentHandler(currentURI, patterns);
      handleHTML(currentURI, handler);
      return handler.getTargetURI();
    }

  }

  protected static class FetchStatus
  {
    public int sessionState = SESSIONSTATE_NORMAL;
    public int resultSignal = RESULT_NO_DOCUMENT;
    // The result context message, which will be used for logging and activity logging if enabled.
    public String contextMessage = null;
    // The result context exception, which will be used for logging if needed.
    public Throwable contextException = null;
    // The checksum, which will be needed if resultSignal is RESULT_VERSION_NEEDED.
    public String checkSum = null;
    // The headers, which will be needed if resultSignal is RESULT_VERSION_NEEDED.
    public Map<String,List<String>> headerData = null;

  }
  
}


