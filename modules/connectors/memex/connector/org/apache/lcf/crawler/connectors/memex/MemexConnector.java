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
package org.apache.lcf.crawler.connectors.memex;

import org.apache.log4j.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.LCF;
import java.util.*;
import java.io.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;
import com.memex.mie.*;
import com.memex.mie.pool.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MemexConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Parameters
  public static final String CONFIG_PARAM_MEMEXSERVERNAME = "Memex server name";
  public static final String CONFIG_PARAM_MEMEXSERVERPORT = "Memex server port";
  public static final String CONFIG_PARAM_USERID = "User ID";
  public static final String CONFIG_PARAM_PASSWORD = "Password";
  public static final String CONFIG_PARAM_WEBSERVERPROTOCOL = "Web server protocol";
  public static final String CONFIG_PARAM_WEBSERVERNAME = "Web server name";
  public static final String CONFIG_PARAM_WEBSERVERPORT = "Web server port";
  public static final String CONFIG_PARAM_SERVERTIMEZONE = "Server time zone";
  public static final String CONFIG_PARAM_CHARACTERENCODING = "Character encoding";

  // Specification nodes
  public static final String SPEC_NODE_SPECIFICATIONRULE = "specificationrule";
  public static final String SPEC_NODE_ENTITY = "entity";
  public static final String SPEC_NODE_PRIMARYFIELD = "primaryfield";
  public static final String SPEC_NODE_METAFIELD = "metafield";
  public static final String SPEC_NODE_SECURITY = "security";
  public static final String SPEC_NODE_ACCESS = "access";

  // Specification attributes
  public static final String SPEC_ATTRIBUTE_NAME = "name";
  public static final String SPEC_ATTRIBUTE_DESCRIPTION = "description";
  public static final String SPEC_ATTRIBUTE_VALUE= "value";
  public static final String SPEC_ATTRIBUTE_TOKEN = "token";
  public static final String SPEC_ATTRIBUTE_VIRTUALSERVER = "virtualserver";
  public static final String SPEC_ATTRIBUTE_ENTITY = "entity";
  public static final String SPEC_ATTRIBUTE_FIELDNAME = "fieldname";
  public static final String SPEC_ATTRIBUTE_OPERATION = "operation";
  public static final String SPEC_ATTRIBUTE_FIELDVALUE = "fieldvalue";

  // Specification attribute values
  public static final String SPEC_VALUE_OFF = "off";
  public static final String SPEC_VALUE_ON = "on";

  /** This is the maximum number of decodes to be done at one time */
  protected final static int MAX_DECODE = 1000;

  /** How long the connection may lie idle before it is freed.  I've chosen 15 minutes. */
  private final static long CONNECTION_IDLE_INTERVAL = 900000L;

  /** Deny access token for default authority */
  public final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  // Activities that we know about
  protected final static String ACTIVITY_SEARCH_AND_DECODE = "search and decode record";

  // Activities list
  protected static final String[] activitiesList = new String[]{ACTIVITY_SEARCH_AND_DECODE};

  // Local variables
  protected String userName = null;
  protected String userPassword = null;
  protected String hostName = null;
  protected int hostPort = -1;
  protected String characterEncoding = null;
  protected String serverTimezone = null;
  protected String webServerProtocol = null;
  protected String webServer = null;
  protected String webServerPort = null;

  //mieConnection is the connection to the main Configuration Server.
  //There will be further LCFMemexConnection objects for each
  //physical server accessed through the physicalServers collection.
  private LCFMemexConnection mieConnection = null;
  private MemexConnectionPool miePool = new MemexConnectionPool();

  //Collection describing the logical servers making up this system
  private Hashtable<String, LogicalServer> logicalServers = null;
  private Hashtable<String, LogicalServer> logicalServersByPrefix = null;

  //Collection describing the physical servers making up this system
  private Hashtable<String, LCFMemexConnection> physicalServers = null;

  //Two collections describing the entities in the set-up - one keyed by the entities' name, the other
  //by their label - generally speaking, we should use labels for anything being presented to the users
  //as this is what they are used to seeing within Patriarch.
  private Hashtable<String, MemexEntity> entitiesByName = null;
  private Hashtable<String, MemexEntity> entitiesByLabel = null;
  private Hashtable<String, MemexEntity> entitiesByPrefix = null;

  // Connection expiration time
  private long connectionExpirationTime = -1L;

  //Retreived records stores records we've identified during the version check stage
  //as having changed - this saves us retrieving them again during ingestion.
  // NOTE: This is a structure that is shared across all instances, and is thus a "session cache".  The key is therefore
  // not just the URN, but the server and port as well.
  private static Hashtable<String, Hashtable> retreivedRecords = new Hashtable<String,Hashtable>();

  /** Constructor.
  */
  public MemexConnector()
  {
    super();
  }

  /** Construct a cache key from a urn.  Can only be called after a "connect" operation.
  */
  protected String makeCacheKey(String urn)
  {
    return hostName + ":" + hostPort + ":" + urn;
  }

  /** Look for a URN in the static hash table.
  *@param urn is the urn to locate.
  *@return the record, or null if not found.
  */
  protected Hashtable lookupCachedRecord(String urn)
  {
    String key = makeCacheKey(urn);
    // No explicit synchronizer is needed because Hashtable itself is synchronized
    return retreivedRecords.get(key);
  }

  /** Write a record into the static hash table.
  */
  protected void writeCachedRecord(String urn, Hashtable record)
  {
    String key = makeCacheKey(urn);
    retreivedRecords.put(key,record);
  }

  /** Remove a record from the cache.
  */
  protected void removeCachedRecord(String urn)
  {
    retreivedRecords.remove(makeCacheKey(urn));
  }

  /** Let the crawler know the completeness of the information we are giving it.
  */
  public int getConnectorModel()
  {
    return MODEL_ADD_CHANGE;
  }

  /** Get the bin name string for a document identifier.  The bin name describes the queue to which the
  * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  *
  * For the Memex implementation, bins are physical servers. For a given document we'll look at the the
  * two letter logical server prefix and work out the physical server hosting it. Only one server will ever be
  * returned for a given document. Physical server names are in the format server:port e.g.
  * patriarch.memex-west.com:9001
  *
  *@param documentIdentifier is the document identifier.
  *@return the bin name.
  */
  public String[] getBinNames(String documentIdentifier)
  {
    try{
      this.setupConnection();
      if((documentIdentifier != null)&&(documentIdentifier.length() == 12)){
        String prefix = documentIdentifier.substring(0,2);
        return new String[] {logicalServersByPrefix.get(prefix).getServerName()};
      }
      return new String[]{""};
    }
    catch(LCFException e){
      Logging.connectors.warn("Memex connection error: "+e.getMessage(),e);
      return new String[]{""};
    }
    catch(ServiceInterruption e){
      Logging.connectors.warn("Memex connection transient error: "+e.getMessage(),e);
      return new String[]{""};
    }
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Get the folder for the jsps.
  */
  public String getJSPFolder()
  {
    return "memex";
  }


  /** Connect to Memex.
  */
  public void connect(ConfigParams configParams)
  {

    // Grab some values for convenience
    super.connect(configParams);
    userName = configParams.getParameter(CONFIG_PARAM_USERID);
    userPassword = configParams.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    hostName = configParams.getParameter(CONFIG_PARAM_MEMEXSERVERNAME);
    hostPort = Integer.parseInt(configParams.getParameter(CONFIG_PARAM_MEMEXSERVERPORT));
    characterEncoding = configParams.getParameter(CONFIG_PARAM_CHARACTERENCODING);
    serverTimezone = configParams.getParameter(CONFIG_PARAM_SERVERTIMEZONE);
    webServerProtocol = configParams.getParameter(CONFIG_PARAM_WEBSERVERPROTOCOL);
    webServer = configParams.getParameter(CONFIG_PARAM_WEBSERVERNAME);
    if (webServer == null || webServer.length() == 0)
      webServer = configParams.getParameter(CONFIG_PARAM_MEMEXSERVERNAME);
    webServerPort = configParams.getParameter(CONFIG_PARAM_WEBSERVERPORT);
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  public String check()
    throws LCFException
  {
    try{
      this.setupConnection();
      return super.check();
    }
    catch(ServiceInterruption mxe){
      return "Memex transient connection error: " + mxe.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws LCFException
  {
    // Is the connection still valid?
    if (this.physicalServers != null && !this.physicalServers.isEmpty())
    {
      // Yes: see if it is time to shut it down yet
      if (connectionExpirationTime < System.currentTimeMillis())
      {
        // Expire all the connection information
        this.cleanUpConnections();

      }
    }

  }

  /** Disconnect from Memex.
  */
  public void disconnect()
    throws LCFException
  {
    this.cleanUpConnections();
    userName = null;
    userPassword = null;
    hostName = null;
    hostPort = -1;
    characterEncoding = null;
    serverTimezone = null;
    webServer = null;
    webServerPort = null;
    super.disconnect();
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
  *
  * The Memex implementation will look at all identified audit databases for the following transactions
  * falling within the specified time frames:
  *
  * APPEND, EDIT, RESTORE
  *
  * If the URN of the record reffered to in each audit record indicates its in a database we're
  * interested in (defined by the DocumentSpecification object, its URN will be added to the job queues.
  *
  * NOTE: ARCHIVE and DELETE are *not* currently taken, because this connector operates in the "ADD_CHANGE" model.  Since
  * deletes are technically available, we could potentially run in the ADD_CHANGE_DELETE model, but the ramifications
  * of doing so involve figuring out whether we see sufficient notification for all changes that might appear in the record's version
  * string or not.  Until that analysis is done, we operate in the more conservative model.
  *
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws LCFException, ServiceInterruption
  {
    //start by making sure we have a connection
    this.setupConnection();

    // Among other things, this creates a map from logical server to entity database names we want to crawl, so that
    // we can look up the list of things we're interested in per logical server.  This is keyed by server prefix.
    CrawlDescription crawlDescription = new CrawlDescription(spec);

    //Convert the passed in epoch milliseconds to Memex format dates.
    String mxStartDate = this.getMemexDate(startTime);
    String mxEndDate = this.getMemexDate(endTime);

    // The query will have the form:
    // ((...~...)MXDATE)$sysdatecreated | ((...~...)MXDATE)$sysdateupdated
    StringBuffer mxQueryBuffer = new StringBuffer();
    mxQueryBuffer.append("(((").append(mxStartDate).append("~").append(mxEndDate).append(")MXDATE)$sysdatecreated | ((")
      .append(mxStartDate).append("~").append(mxEndDate).append(")MXDATE)$sysdateupdated) & (!y)$sysarchived");
    String mxQuery = mxQueryBuffer.toString();

    if (Logging.connectors.isDebugEnabled())
      Logging.connectors.debug("Memex: Basic query is '"+mxQuery+"'");

    try
    {
      // We loop through logical servers first, and see which ones have entities that need to be crawled
      int hist = 1;
      Map<String,SearchStatus[]> allsearches = new HashMap<String,SearchStatus[]>();
      Map<String,List<List<CrawlMatchDescription>>> allcriteria = new HashMap<String,List<List<CrawlMatchDescription>>>();
      Map<String,List<MemexEntity>> allentities = new HashMap<String,List<MemexEntity>>();

      for(Enumeration e = logicalServers.keys(); e.hasMoreElements();)
      {
        String serverKey = (String)e.nextElement();
        // Look it up in the crawl description
        if (crawlDescription.shouldCrawlVirtualServer(serverKey))
        {
          // We found some entities for this virtual server, so prepare to do some queries
          LogicalServer ls = (LogicalServer)logicalServers.get(serverKey);
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Memex: Starting searches for logical server '"+ls.getPrefix()+"'");
          // Accumulate the searches and criteria for this virtual server here
          List<Search> searches = new ArrayList<Search>();
          List<List<CrawlMatchDescription>> criteria = new ArrayList<List<CrawlMatchDescription>>();
          List<MemexEntity> entities = new ArrayList<MemexEntity>();
          // We iterate over all entities listed for this virtual server, and exclude the ones for which we have no matches
          int j = 0;
          while (j < ls.getDatabaseCount())
          {
            String entityname = ls.getDatabase(j).getName();
            if (entityname != null && entityname.length() > 0)
            {
              entityname = entityname.substring(0,entityname.indexOf("."));
              // We need to get the prefix, so get the entity object to do that
              MemexEntity ent = entitiesByName.get(entityname);
              if (ent != null)
              {
                // Look up the crawl criteria based on the server and prefix
                List<CrawlMatchDescription> matchDescription = crawlDescription.getCrawlMatches(serverKey,ent.getPrefix());
                if (matchDescription != null)
                {
                  // Hey, we really ARE supposed to search this virtual server!
                  // The constraints used, though, must be applied on the connector side, since we really don't have the operations we need on the server
                  // for decent ability to chunk up the data.
                  Search entitySearch = new Search(ls.getDatabase(j).getPath(), mxQuery, 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, 0, hist);
                  searches.add(entitySearch);
                  criteria.add(matchDescription);
                  entities.add(ent);
                  hist++;
                }
              }
            }
            j++;
          }

          // Found any searches?  If so, start them, and keep track of what we start
          if(!searches.isEmpty())
          {
            SearchStatus[] status = ls.getMIE().mie.mxie_search(searches);
            allsearches.put(serverKey, status);
            allcriteria.put(serverKey, criteria);
            allentities.put(serverKey, entities);
          }
        }
      }

      // Searches have been started.  Now all we need to do is get the results.
      boolean running = true;
      while(running)
      {
        // Check if we're supposed to abort
        try
        {
          activities.checkJobStillActive();
        }
        catch (ServiceInterruption e)
        {
          // Do whatever cleanup is needed for the searches!
          for(Iterator<String> en = allsearches.keySet().iterator(); en.hasNext();){
            String serverKey = en.next();
            LogicalServer ls = (LogicalServer)logicalServers.get(serverKey);
            SearchStatus[] ss = allsearches.get(serverKey);
            ls.getMIE().mie.mxie_search_progress(ss);
            for(int i = 0; i < ss.length; i++){
              ls.getMIE().mie.mxie_search_stop(ss[i].getHistory());
            }
          }

          throw e;
        }

        running = false;
        for(Iterator<String> e = allsearches.keySet().iterator(); e.hasNext();)
        {
          String serverKey = e.next();
          LogicalServer ls = (LogicalServer)logicalServers.get(serverKey);
          SearchStatus[] ss = allsearches.get(serverKey);
          List<List<CrawlMatchDescription>> matchCriteria = allcriteria.get(serverKey);
          List<MemexEntity> entityList = allentities.get(serverKey);

          ls.getMIE().mie.mxie_search_progress(ss);

          for(int i = 0; i < ss.length; i++)
          {
            if(ss[i].getStatus() == 1)
            {
              // This is where the request-for-decode objects are accumulated
              ArrayList fieldListX = new ArrayList();
              // This is where we accumulate the entire set of DecodedField objects, keyed by index
              List<Map<String,DecodedField>> fieldsPerRecord = new ArrayList<Map<String,DecodedField>>();

              //This search is complete
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Memex: The search against server '"+ls.getPrefix()+"' number "+Integer.toString(i)+" is complete.");

              List<CrawlMatchDescription> specificMatchCriteria = matchCriteria.get(i);
              MemexEntity entity = entityList.get(i);

              Map<String,Integer> neededFields = findUniqueFields(ls,entity,specificMatchCriteria);

              for(int x = 1; x <= ss[i].getNumberOfHits();x++)
              {
                int currhist = ss[i].getHistory();
                Map<String,DecodedField> theMap = new HashMap<String,DecodedField>();
                //sysurn!
                DecodedField URNfield = new DecodedField(currhist, x, 1, 100);
                fieldListX.add(URNfield);
                theMap.put("sysurn",URNfield);
                // Now, we have to create DecodedField objects for every field in the criteria.  We want no duplicates.
                for (Iterator<String> w = neededFields.keySet().iterator(); w.hasNext();)
                {
                  String fieldName = w.next();
                  int fieldIndex = neededFields.get(fieldName).intValue();
                  DecodedField searchField = new DecodedField(currhist, x, fieldIndex, 1000);
                  fieldListX.add(searchField);
                  theMap.put(fieldName,searchField);
                }

                fieldsPerRecord.add(theMap);

                if (fieldsPerRecord.size() == MAX_DECODE)
                {
                  // Fire off the decode, and document queuing.
                  doDecode(fieldListX,fieldsPerRecord,specificMatchCriteria,ls,activities);
                  fieldListX.clear();
                  fieldsPerRecord.clear();
                }
              }
              // Done.  Make sure we do the final decoding.
              if (fieldsPerRecord.size() > 0)
                doDecode(fieldListX,fieldsPerRecord,specificMatchCriteria,ls,activities);

              //set the number of hits for this search to 0
              // so we don't process it again
              ss[i].setNumberOfHits(0);
            }else{
              //We've found a search that is still running
              running = true;
            }
          }
        }
        // No point in looping hard; let the search proceed a little while instead
        if (running)
        {
          try
          {
            LCF.sleep(100L);
          }
          catch (InterruptedException e)
          {
            throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
          }
        }
      }
    }
    catch(MemexException m)
    {
      Logging.connectors.warn("Memex: Problem seeding documents: "+m.getMessage()+" - retrying",m);
      //Memex connection error; treat as transient
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Problem seeding documents from Memex: " + m.getMessage(),m,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }

  }

  protected static boolean checkCriteria(Map<String,DecodedField> fields, List<CrawlMatchDescription> specificMatchCriteria)
    throws LCFException
  {
    // An empty array means EVERYTHING.
    if (specificMatchCriteria.size() == 0)
      return true;
    int i = 0;
    while (i < specificMatchCriteria.size())
    {
      CrawlMatchDescription cmd = specificMatchCriteria.get(i++);
      // Does this record match any ONE of those in the list?
      DecodedField fieldValue = fields.get(cmd.getFieldName());
      if (fieldValue != null)
      {
        // Found a value!  Get as text
        String compareValue1 = fieldValue.getText().toLowerCase();
        String compareValue2 = cmd.getFieldValue().toLowerCase();
        String operator = cmd.getOperation();
        if (operator.equals("="))
        {
          if (compareValue1.equals(compareValue2))
            return true;
        }
        else if (operator.equals("<"))
        {
          if (compareValue1.compareTo(compareValue2) < 0)
            return true;
        }
        else if (operator.equals(">"))
        {
          if (compareValue1.compareTo(compareValue2) > 0)
            return true;
        }
        else if (operator.equals("<="))
        {
          if (compareValue1.compareTo(compareValue2) <= 0)
            return true;
        }
        else if (operator.equals(">="))
        {
          if (compareValue1.compareTo(compareValue2) >= 0)
            return true;
        }
        else if (operator.equals("!="))
        {
          if (!compareValue1.equals(compareValue2))
            return true;
        }
        else
          throw new LCFException("Bad operator value: "+operator);
      }
    }
    return false;
  }

  protected void doDecode(ArrayList fieldListX, List<Map<String,DecodedField>> fieldsPerRecord, List<CrawlMatchDescription> specificMatchCriteria,
    LogicalServer ls, ISeedingActivity activities)
    throws MemexException, LCFException
  {
    // First, do the decode
    ls.getMIE().mie.mxie_decode_fields(fieldListX);
    // Now, walk through the entire fieldlist and queue the urns from it
    int i = 0;
    while (i < fieldsPerRecord.size())
    {
      Map<String,DecodedField> fields = fieldsPerRecord.get(i++);

      DecodedField URNfield = fields.get("sysurn");


      String urn = URNfield.getText();
      if (urn != null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Memex: Located candidate seed record "+urn);

        // For each record, we need to verify that it actually matches the criteria we put forth.  We have the search description, so evaluate it in the context of the current field set.
        if (checkCriteria(fields,specificMatchCriteria))
        {

          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Memex: Adding seed record "+urn);
          activities.addSeedDocument(urn);
        }
        else
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Memex: Skipped record "+urn+" because it didn't match search criteria");
        }
      }
      else
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Memex: Seed record located by search, but had no URN!");
      }
    }
  }

  protected static Map<String,Integer> findUniqueFields(LogicalServer ls, MemexEntity entity, List<CrawlMatchDescription> specificMatchCriteria)
    throws MemexException
  {
    // Walk through the match criteria and accumulate the set of fields that we need.  Also look up the field's index, since that's
    // what we actually will need for the request.
    Map<String,Integer> neededFields = new HashMap<String,Integer>();
    int i = 0;
    while (i < specificMatchCriteria.size())
    {
      CrawlMatchDescription cmd = specificMatchCriteria.get(i);
      String fieldName = cmd.getFieldName();
      if (neededFields.get(fieldName) == null)
      {
        // Look up the field name and find its index
        Map<String,DatabaseField> fieldMap = ls.getFieldsByDatabaseName(entity.getName());
        if (fieldMap != null)
        {
          DatabaseField df = fieldMap.get(fieldName);
          if (df != null)
          {
            // Enter it into the map.
            neededFields.put(fieldName,new Integer(df.getFieldNumber()));
          }
        }
      }
      i++;
    }
    return neededFields;
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *
  * For Memex Records, we derive the document version from three elements - the
  * record's generation number, the scurity locks placed on it and the role groups
  * with search access to the database containing the record.  Also included must be data that
  * from the connection or the job that affects how each individual ingestion takes place: the fields that get ingested,
  * the metadata fields specified, and the web server URL prefix.
  *
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
    String[] newVersions = new String[documentIdentifiers.length];
    // Build a hash of the indices for each document identifier
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      indexMap.put(documentIdentifiers[i], new Integer(i));
      i++;
    }

    CrawlDescription crawlDescription = new CrawlDescription(spec);

    // Set up the connection, if it hasn't happened already.
    this.setupConnection();

    // Also, calculate the url prefix (the settable part) so that we know to reingest when that changes
    String URI = webServerProtocol + "://" + webServer;
    if((webServerPort != null)&&(!(webServerPort.equals("")))){
      URI = URI + ":" + webServerPort;
    }


    try{
      //Split the Document Identifiers (Memex URNs) into seperate lists
      //based on their server and entity prefix (i.e. split them up by Memex database).
      Hashtable<String, Hashtable> sortedIDs = new Hashtable<String, Hashtable>();
      for(i = 0; i < documentIdentifiers.length; i++){
        String recordURN = documentIdentifiers[i];
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Memex: Finding version of record "+recordURN);
        EntityDescription fieldlist = this.checkIngest(recordURN, crawlDescription);
        if (fieldlist != null)
        {
          String lserver = documentIdentifiers[i].substring(0, 2);
          String entity = documentIdentifiers[i].substring(2,4);
          Hashtable<String, String> serverEntities = null;
          String dbQuery = null;
          //Look to see if we have already added a hashtable for this server
          if(sortedIDs.containsKey(lserver)){
            serverEntities = sortedIDs.get(lserver);
          }else{
            serverEntities = new Hashtable<String, String>();
          }
          //Now look to see if we have a previous entry for this entity type
          //on this server
          if(serverEntities.containsKey(entity)){
            dbQuery = serverEntities.get(entity);
            dbQuery = dbQuery + " | ";
          }else{
            dbQuery = new String();
          }
          dbQuery = dbQuery + documentIdentifiers[i];
          serverEntities.put(entity, dbQuery);
          sortedIDs.put(lserver, serverEntities);
        }
      }
      //OK - we should now have all the pertinent document identifiers (URNS) organised by database
      //Kick off a search against each database
      int hist = 1;
      Hashtable allsearches = new Hashtable();
      for(Enumeration e = sortedIDs.keys(); e.hasMoreElements();){
        List<Search> searches = new ArrayList<Search>();
        String serverKey = (String)e.nextElement();
        LogicalServer ls = logicalServersByPrefix.get(serverKey);
        Hashtable serverEntities = sortedIDs.get(serverKey);
        for(Enumeration ent = serverEntities.keys(); ent.hasMoreElements();){
          String entityKey = (String)ent.nextElement();
          String mxQuery = "(" + (String)serverEntities.get(entityKey) + ") & (!y)$sysarchived";
          //Find the database's path
          String dbpath = null;
          String dbname = entitiesByPrefix.get(entityKey).getName();
          for(i = 0; i < ls.getDatabaseCount(); i++){
            RegistryEntry db = ls.getDatabase(i);
            if((db.getName().startsWith(dbname))&&(db.getTag().startsWith(serverKey))){
              dbpath = db.getPath();
              break;
            }
          }
          if(dbpath != null){
            Search dbSearch = new Search(dbpath, mxQuery, 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, 0, hist);
            searches.add(dbSearch);
            hist++;
          }
        }
        if(!searches.isEmpty()){
          SearchStatus[] status = ls.getMIE().mie.mxie_search(searches);
          allsearches.put(serverKey, status);
        }
      }
      //At this point we should have all of our searches up and running
      //Monitor for them completing and process the results.
      boolean running = true;
      while(running){
        running = false;
        for(Enumeration e = allsearches.keys(); e.hasMoreElements();){
          String serverKey = (String)e.nextElement();
          LogicalServer ls = (LogicalServer)logicalServersByPrefix.get(serverKey);
          SearchStatus[] ss = (SearchStatus[])allsearches.get(serverKey);
          ls.getMIE().mie.mxie_search_progress(ss);
          for(i = 0; i < ss.length; i++){
            if(ss[i].getStatus() == 1){
              //This search is complete
              if(ss[i].getNumberOfHits()>0){
                for(int x = 1; x <= ss[i].getNumberOfHits();x++){
                  int currhist = ss[i].getHistory();

                  Hashtable currRec = null;
                  //Decode the record and get the urn and any protected locks
                  currRec = this.getmxRecordObj(ls, currhist, x);
                  if(currRec != null){

                    String urn = (String)currRec.get("sysurn");
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("Memex: Fetched record "+urn+"; history number "+Integer.toString(x)+" record number "+Integer.toString(currhist));
                    String sysArchived = (String)currRec.get("sysarchived");
                    if (sysArchived == null || !sysArchived.equalsIgnoreCase("y"))
                    {
                      EntityDescription fieldlist = this.checkIngest(urn, crawlDescription);
                      if (fieldlist != null)
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("Memex: Accepted record "+urn+" as valid for processing");

                        // The version string consists of two parts.  The first part is decoded in processDocuments, and the
                        // second part isn't, but causes appropriate differences to appear when something changes.
                        StringBuffer sb = new StringBuffer();
                        // First decodeable section: the primary field names, in order
                        packList(sb,fieldlist.getPrimaryFields(),'+');
                        // Second decodeable section: the metadata field names, in sorted order
                        packList(sb,fieldlist.getMetadataFields(),'+');
                        // Acl section depends on whether security is on or off
                        if (crawlDescription.isSecurityOn())
                        {
                          if (Logging.connectors.isDebugEnabled())
                            Logging.connectors.debug("Memex: For record "+urn+", security is on");

                          // Signal that we have acls
                          sb.append('+');

                          // Declarations for acl array and deny array
                          String[] aclarray;
                          String[] acldenyarray;

                          // Do we have forced acls?
                          String[] forcedAcls = crawlDescription.getForcedAcls();
                          if (forcedAcls.length == 0)
                          {
                            if (Logging.connectors.isDebugEnabled())
                              Logging.connectors.debug("Memex: For record "+urn+", using native security");

                            // Calculate the acls, since we will pack them into the version string too.
                            // Create maps of the acls we want.  These will contain acl values WITHOUT any prefix; the prefix will be added
                            // on the processDocuments() side, as will the deny token.  This saves version string space and logic both.
                            //
                            // Rules:
                            // If a record is both protected and covert, acl list should be
                            //   set to all permutations of one lock from each category.

                            HashMap<String,String> acllist = new HashMap<String,String>();
                            HashMap<String,String> acldenylist = new HashMap<String,String>();

                            String[] plocks = null;
                            String[] clocks = null;

                            String temp = (String)currRec.get("meta_plocks");
                            if((temp != null)&&(!(temp.equals("")))){
                              //Strip off the parnethesis
                              temp = temp.substring(1, temp.length() - 1);
                              plocks = temp.split(", ");
                            }

                            temp = (String)currRec.get("meta_clocks");
                            if((temp != null)&&(!(temp.equals("")))){
                              temp = temp.substring(1, temp.length() - 1);
                              clocks = temp.split(", ");
                            }


                            if((plocks != null)&&(clocks != null)){
                              if (Logging.connectors.isDebugEnabled())
                                Logging.connectors.debug("Memex: Record "+urn+" has both c-locks and p-locks");

                              //Record is both protected and covert. A user should only get access
                              //if they are in at least one group from both lists
                              boolean covertDenyOnly = true;
                              int z;
                              int y;
                              // Loop through the covert deny locks
                              for(z = 0; z < clocks.length; z++){
                                if(clocks[z].startsWith("!")){
                                  //This is a deny lock
                                  String accessTokenName = clocks[z].substring(1).toUpperCase();
                                  if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("Memex: For record "+urn+", deny token "+accessTokenName+" found");
                                  acldenylist.put(accessTokenName,accessTokenName);
                                }
                              }
                              // Loop through all the protected deny locks
                              for(y = 0; y < plocks.length; y++){
                                if(plocks[y].startsWith("!")){
                                  //This is a deny lock
                                  String accessTokenName = plocks[y].substring(1).toUpperCase();
                                  if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("Memex: For record "+urn+", deny token "+accessTokenName+" found");
                                  acldenylist.put(accessTokenName,accessTokenName);
                                }
                              }

                              // Loop through the covert access locks
                              for(z = 0; z < clocks.length; z++){
                                if(!clocks[z].startsWith("!")){
                                  // This is not a deny lock
                                  String covertLockName = clocks[z].toUpperCase();
                                  covertDenyOnly = false;
                                  boolean protectedDenyOnly = true;
                                  // For each covert lock, loop through all the protected locks
                                  for(y = 0; y < plocks.length; y++){
                                    if(!plocks[y].startsWith("!")){
                                      // This is NOT a deny lock
                                      protectedDenyOnly = false;
                                      String lock;
                                      String upperPlock = plocks[y].toUpperCase();
                                      if((upperPlock.equals(covertLockName))){
                                        //The covert and protected locks are
                                        //the same
                                        lock = upperPlock;
                                      }else{
                                        // They are different, so create a qualified entry for every plock
                                        lock = covertLockName + "-" + upperPlock;
                                      }
                                      if (acldenylist.get(lock) == null)
                                      {
                                        if (Logging.connectors.isDebugEnabled())
                                          Logging.connectors.debug("Memex: For record "+urn+", access token "+lock+" found");
                                        acllist.put(lock,lock);
                                      }
                                    }
                                  }
                                  if(protectedDenyOnly){
                                    //The only protected locks we found were deny locks so add this
                                    //covert lock on its own
                                    String accessTokenName = covertLockName;
                                    if (acldenylist.get(accessTokenName) == null)
                                    {
                                      if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("Memex: For record "+urn+", access token "+accessTokenName+" found");
                                      acllist.put(accessTokenName,accessTokenName);
                                    }
                                  }
                                }
                              }
                              if(covertDenyOnly){
                                //We only have deny covert locks so add all protected locks on their own
                                for(y = 0; y < plocks.length; y++){
                                  if(!plocks[y].startsWith("!")){
                                    String lock = plocks[y].toUpperCase();
                                    if (acldenylist.get(lock) == null)
                                    {
                                      if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("Memex: For record "+urn+", access token "+lock+" found");
                                      acllist.put(lock,lock);
                                    }
                                  }
                                }
                              }
                            }else if(plocks != null){
                              if (Logging.connectors.isDebugEnabled())
                                Logging.connectors.debug("Memex: Record "+urn+" has p-locks");
                              int y;
                              for(y = 0; y < plocks.length; y++){
                                if(plocks[y].startsWith("!")){
                                  //This is a deny lock
                                  String accessTokenName = plocks[y].substring(1).toUpperCase();
                                  if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("Memex: For record "+urn+", deny token "+accessTokenName+" found");
                                  acldenylist.put(accessTokenName,accessTokenName);
                                }
                              }
                              for(y = 0; y < plocks.length; y++){
                                if(!plocks[y].startsWith("!")){
                                  // Not a deny lock
                                  String accessTokenName = plocks[y].toUpperCase();
                                  if(acldenylist.get(accessTokenName) == null){
                                    if (Logging.connectors.isDebugEnabled())
                                      Logging.connectors.debug("Memex: For record "+urn+", access token "+accessTokenName+" found");
                                    acllist.put(accessTokenName,accessTokenName);
                                  }
                                }
                              }
                            }else if(clocks != null){
                              if (Logging.connectors.isDebugEnabled())
                                Logging.connectors.debug("Memex: Record "+urn+" has c-locks");
                              int y;
                              for(y = 0; y < clocks.length; y++){
                                if(clocks[y].startsWith("!")){
                                  //This is a deny lock
                                  String accessTokenName = clocks[y].substring(1).toUpperCase();
                                  if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("Memex: For record "+urn+", deny token "+accessTokenName+" found");
                                  acldenylist.put(accessTokenName,accessTokenName);
                                }
                              }
                              for(y = 0; y < clocks.length; y++){
                                if(!clocks[y].startsWith("!")){
                                  // This is not a deny acl
                                  String accessTokenName = clocks[y].toUpperCase();
                                  if (acldenylist.get(accessTokenName) == null)
                                  {
                                    if (Logging.connectors.isDebugEnabled())
                                      Logging.connectors.debug("Memex: For record "+urn+", access token "+accessTokenName+" found");
                                    acllist.put(accessTokenName,accessTokenName);
                                  }
                                }
                              }
                            }

                            // Convert hashes to sorted arrays.  They must be sorted or the version string compare will fail when it shouldn't.
                            String aclprefix = urn.substring(0, 4).toUpperCase() + "-";
                            int ii;
                            Iterator<String> iter;

                            if (acllist.size() != 0)
                            {
                              aclarray = new String[acllist.size()];
                              ii = 0;
                              iter = acllist.keySet().iterator();
                              while (iter.hasNext())
                              {
                                aclarray[ii] = aclprefix + iter.next();
                                ii++;
                              }
                              java.util.Arrays.sort(aclarray);
                            }
                            else
                              aclarray = new String[]{"DEFAULT-GRANT"};

                            acldenyarray = new String[acldenylist.size()+1];
                            ii = 0;
                            iter = acldenylist.keySet().iterator();
                            while (iter.hasNext())
                            {
                              acldenyarray[ii] = aclprefix + iter.next();
                              ii++;
                            }
                            acldenyarray[ii] = "DEFAULT-DENY";

                            java.util.Arrays.sort(acldenyarray);
                          }
                          else
                          {
                            if (Logging.connectors.isDebugEnabled())
                              Logging.connectors.debug("Memex: Record "+urn+" has forced acls");

                            aclarray = forcedAcls;
                            acldenyarray = new String[]{defaultAuthorityDenyToken};
                          }

                          // Third decodeable section: acls
                          packList(sb,aclarray,'+');
                          // Fourth decodeable section: deny acls
                          packList(sb,acldenyarray,'+');
                        }
                        else
                        {
                          // Signal that security is off
                          sb.append('-');
                        }
                        // Fifth decodeable section: url prefix
                        pack(sb,URI,'=');
                        // Undecodeable section.
                        String gennum = (String)currRec.get("meta_gennum");
                        String recdate = (String)currRec.get("meta_datestamp");
                        sb.append("(").append(gennum).append(",").append(recdate).append(")");

                        Integer indexValue = (Integer)indexMap.get(urn);
                        if (indexValue != null)
                        {
                          newVersions[indexValue.intValue()] = sb.toString();
                          writeCachedRecord(urn, currRec);
                        }
                      }
                      else
                      {
                        if (Logging.connectors.isDebugEnabled())
                          Logging.connectors.debug("Memex: Skipping record "+urn+" as per criteria");
                      }
                    }
                    else
                    {
                      if (Logging.connectors.isDebugEnabled())
                        Logging.connectors.debug("Memex: Skipping record "+urn+" because it was deleted");
                    }
                  }
                  else
                  {
                    if (Logging.connectors.isDebugEnabled())
                      Logging.connectors.debug("Memex: Failed to fetch history number "+Integer.toString(x)+" record number "+Integer.toString(currhist));
                  }
                }
                //set the number of hits for this search to 0
                // so we don't process it again
                ss[i].setNumberOfHits(0);
              }
            }else{
              //We've found a search that is still running
              running = true;
            }
          }
        }
      }
    }
    catch(MemexException e){
      // Treat as transient for now?
      // What can this come from?
      Logging.connectors.warn("Memex: Couldn't get version information: "+e.getMessage()+" - retrying",e);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Problem getting version info from Memex: " + e.getMessage(),e,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    return newVersions;
  }

  /** Process documents whose versions indicate they need processing.
  */
  public void processDocuments(String[] documentIdentifiers, String[] documentVersions,
    IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws LCFException, ServiceInterruption
  {

    // First, create the CrawlDescription object
    CrawlDescription crawlDescription = new CrawlDescription(spec);

    this.setupConnection();

    /*****************************************************
    *
    * We should already have retreived each record
    * required during the getVersions method
    *
    */
    long startTime = System.currentTimeMillis();
    String errorCode = "OK";
    String errorDesc = null;
    Long fileLength = null;
    try{
      //first, start by ensuring we have a connection

      for(int i = 0; i < documentIdentifiers.length; i++){
        if(!(scanOnly[i])){
          //Double check this record is in a database we're meant to be ingesting
          String recordURN = documentIdentifiers[i];
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Memex: Processing record "+recordURN);
          String recordVersion = documentVersions[i];
          //Check we have the record
          Hashtable currentRec = lookupCachedRecord(recordURN);
          if (currentRec == null)
            // This should never happen!!
            throw new LCFException("Process request for a record whose version was never requested!!");

          // Now, unpack the version string to obtain what fields we should ingest etc.
          ArrayList<String> primaryList = new ArrayList<String>();
          ArrayList<String> metadataList = new ArrayList<String>();
          ArrayList<String> acllist = null;
          ArrayList<String> acldenylist = null;
          StringBuffer URIBuffer = new StringBuffer();

          int ii = 0;
          ii = unpackList(primaryList,recordVersion,ii,'+');
          ii = unpackList(metadataList,recordVersion,ii,'+');
          if (ii < recordVersion.length())
          {
            char x = recordVersion.charAt(ii++);
            if (x == '+')
            {
              acllist = new ArrayList<String>();
              acldenylist = new ArrayList<String>();
              ii = unpackList(acllist,recordVersion,ii,'+');
              ii = unpackList(acldenylist,recordVersion,ii,'+');
            }
          }
          unpack(URIBuffer,recordVersion,ii,'=');

          String URI = URIBuffer.toString() + "/search.jsp?urn=" + recordURN;
          RepositoryDocument data = new RepositoryDocument();

          int primaryFieldCount = primaryList.size();
          int k = 0;
          StringBuffer sb = new StringBuffer();
          boolean hasDataYet = false;
          // This is the subrecord accumulator.  Whenever we see a non-subrecord field, we dump the subrecords we've seen up until then,
          // and clear this accumulator before proceeding.
          ArrayList<String> subrecArray = new ArrayList<String>();
          // This is the subname currently in effect.  When this changes, we dump the subrecord fields we've accumulated.
          String subnameInEffect = null;
          while (k < primaryFieldCount)
          {
            String primaryFieldName = primaryList.get(k);
            if(!(primaryFieldName.contains("."))){
              // This field is not part of a subrecord.
              if (subnameInEffect != null)
              {
                // Dump any accumulated subrecords into the buffer.
                hasDataYet = addSubRecords(currentRec,subrecArray,sb,hasDataYet,subnameInEffect);
                subrecArray.clear();
                subnameInEffect = null;
              }
              String primaryFieldData = (String)currentRec.get(primaryList.get(k));
              if (primaryFieldData != null){
                if(hasDataYet){
                  sb.append("\n#####\n");
                }
                else{
                  hasDataYet = true;
                }
                sb.append(primaryFieldData);
              }
            }else{
              //This field is part of a subrecord
              String subname = primaryFieldName.substring(0, primaryFieldName.indexOf("."));
              String fieldname = primaryFieldName.substring(primaryFieldName.indexOf(".")+1);
              if (subnameInEffect != null && !subnameInEffect.equals(subname))
              {
                // Dump any accumulated subrecords into the buffer.
                hasDataYet = addSubRecords(currentRec,subrecArray,sb,hasDataYet,subnameInEffect);
                subrecArray.clear();
              }
              subnameInEffect = subname;

              subrecArray.add(fieldname);
            }
            k++;
          }

          // Add any dangling subrecords to the buffer
          if (subnameInEffect != null)
          {
            hasDataYet = addSubRecords(currentRec,subrecArray,sb,hasDataYet,subnameInEffect);
            subrecArray.clear();
            subnameInEffect = null;
          }

          String primaryData = sb.toString();
          // This encoding is deliberately utf-8, since that can represent anything; the ingestion system needs to figure it out for itself.
          byte[] primaryBytes = primaryData.getBytes("utf-8");
          ByteArrayInputStream is = new ByteArrayInputStream(primaryBytes);
          data.setBinary(is,primaryBytes.length);
          //Add the meta fields
          for(k = 0; k < metadataList.size(); k++)
          {
            String metadataFieldName = metadataList.get(k);
            if(!(metadataFieldName.contains("."))){
              //Field is not part of a subrecord
              String metadataValue = (String)currentRec.get(metadataFieldName);
              if (metadataValue != null){
                data.addField(metadataFieldName, metadataValue);
              }
            }else{
              if(currentRec.containsKey("subrecords")){
                //Field is part of a subrecord.
                String subname = metadataFieldName.substring(0, metadataFieldName.indexOf("."));
                String fieldname = metadataFieldName.substring(metadataFieldName.indexOf(".")+1);

                ArrayList<String> multivaluedMetadataValue = new ArrayList<String>();
                ArrayList<Hashtable> sub = (ArrayList<Hashtable>)((Hashtable<String, ArrayList>)currentRec.get("subrecords")).get(subname);
                for(int subsindex = 0; subsindex < sub.size(); subsindex++){
                  String instancemd = (String)sub.get(subsindex).get(fieldname);
                  if(instancemd != null){
                    multivaluedMetadataValue.add(instancemd);
                  }
                }
                String[] dataValue = new String[multivaluedMetadataValue.size()];
                int zz = 0;
                while (zz < dataValue.length)
                {
                  dataValue[zz] = multivaluedMetadataValue.get(zz);
                  zz++;
                }
                data.addField(metadataFieldName,dataValue);
              }
            }
          }

          //Work out the acl list

          // We must ingest a blank array for documents that can't be seen by anyone
          if (acllist != null)
          {
            String[] aclarray = new String[acllist.size()];
            ii = 0;
            while (ii < aclarray.length)
            {
              aclarray[ii] = acllist.get(ii);
              ii++;
            }
            data.setACL(aclarray);
          }

          if (acldenylist != null)
          {
            String[] aclarray = new String[acldenylist.size()];
            ii = 0;
            while (ii < aclarray.length)
            {
              aclarray[ii] = acldenylist.get(ii);
              ii++;
            }
            data.setDenyACL(aclarray);
          }

          //OK - ingest doc.
          activities.ingestDocument(recordURN, documentVersions[i], URI, data);
          activities.recordActivity(new Long(startTime),ACTIVITY_SEARCH_AND_DECODE,fileLength,recordURN,errorCode,errorDesc,null);
        }
      }
    }catch(java.io.UnsupportedEncodingException e){
      throw new LCFException("Unsupported encoding: "+e.getMessage(),e);
    }

  }

  /** Helper method for processDocuments above.
  *@param currentRec is the current record.
  *@param subrecfields is the specification of the subrecord fields we want written.
  *@param sb is the buffer to append the text to.
  *@param hasDataYet is true only if the buffer has already had fields written to it.
  *@param subname is the subrecord name to dump.
  *@return true if the buffer has had fields written to it.
  */
  private boolean addSubRecords(Hashtable currentRec, ArrayList<String> subrecArray, StringBuffer sb, boolean hasDataYet, String subname)
  {
    if (subrecArray.size() == 0)
      return hasDataYet;

    //Process the subrecords
    if(currentRec.containsKey("subrecords")){
      ArrayList<Hashtable> sub = (ArrayList<Hashtable>)((Hashtable<String, ArrayList>)currentRec.get("subrecords")).get(subname);
      if (sub != null)
      {
        for(int subsindex = 0; subsindex < sub.size(); subsindex++){
          Hashtable<String,String> subinstance = (Hashtable<String, String>)sub.get(subsindex);
          //Look for each primary field identified as being in this subrecord.
          for(int j = 0; j < subrecArray.size(); j++){
            String instancepd = (String)subinstance.get(subrecArray.get(j));
            if(instancepd != null){
              if(hasDataYet){
                sb.append("\n#####\n");
              }
              else{
                hasDataYet = true;
              }
              sb.append(instancepd);
            }
          }
        }
      }
    }

    return hasDataYet;
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
    // Clean up our part of the cache.
    for (int i = 0; i < documentIdentifiers.length; i++)
    {
      if (versions[i] != null)
        removeCachedRecord(documentIdentifiers[i]);
    }
  }

  public int getMaxDocumentRequest()
  {
    // 1 at a time, since this connector does not deal with documents en masse, but one at a time.
    return 1;
  }

  // UI support methods

  /** Return a list of databases (instances of an entity type) on a given virtual server*/
  public NameDescription[] listDatabasesForVirtualServer(String virtualServerName)
    throws LCFException, ServiceInterruption
  {
    //Start by making sure we're connected
    this.setupConnection();

    //Now get the logical server
    if(!(logicalServers.containsKey(virtualServerName))){
      //If we can't find the virtual server, its unlikely we can
      //recover
      throw new LCFException("Memex error: Virtual server "+virtualServerName+" not found");
    }
    LogicalServer ls = logicalServers.get(virtualServerName);
    if (ls == null)
      throw new LCFException("Memex error: Virtual server "+virtualServerName+" not found");

    ArrayList<String> dblist = new ArrayList<String>();
    for(int i = 0; i < ls.getDatabaseCount(); i++){
      RegistryEntry re = ls.getDatabase(i);
      String dbname = re.getName();
      if (dbname != null)
      {
        dbname = dbname.substring(0,dbname.indexOf("."));
        //get the entity's label
        MemexEntity ent = entitiesByName.get(dbname);
        if (ent != null && !ent.isMetaDB())
        {
          dblist.add(ent.getDisplayName());
        }
      }
    }

    // Prepare the return list
    NameDescription[] rval = new NameDescription[dblist.size()];
    for (int i = 0; i < rval.length; i++)
    {
      rval[i] = new NameDescription(dblist.get(i),entitiesByLabel.get(dblist.get(i)).getPrefix());
    }
    return rval;
  }

  /** Return a list of virtual servers for the connection, in sorted alphabetic order */
  public String[] listVirtualServers()
    throws LCFException, ServiceInterruption
  {
    //Start by making sure we're connected
    this.setupConnection();

    ArrayList allservers = new ArrayList();
    for(Enumeration e = logicalServers.keys(); e.hasMoreElements();){
      allservers.add(e.nextElement());
    }

    //Strip out any inactive servers.
    //Active servers are those containing databases i.e. those that would
    //appear in Patriarch's search manger
    ArrayList activeservers = new ArrayList();
    for(int i = 0; i < allservers.size(); i++){
      LogicalServer currentServer = (LogicalServer)logicalServers.get(allservers.get(i));
      if(currentServer.isActive()){
        activeservers.add(currentServer.getServerName());
      }
    }

    //finally, convert the active server array list
    //to a string array
    String returnArray[] = new String[activeservers.size()];
    for(int i = 0; i < activeservers.size(); i++){
      returnArray[i] = (String)activeservers.get(i);
    }
    Arrays.sort(returnArray, String.CASE_INSENSITIVE_ORDER);
    return returnArray;
  }

  /** Return a list of the entity types there are for the connection, in sorted alphabetic order */
  public NameDescription[] listEntityTypes()
    throws LCFException, ServiceInterruption
  {
    //Start by making sure we're connected
    this.setupConnection();

    // We want this ordered by display name, so we use that as the key here.
    ArrayList<String> allentityLabels= new ArrayList<String>();
    // Internally, we handle entities by prefix, so that's how everything is set up.
    for(Enumeration e = entitiesByLabel.keys(); e.hasMoreElements();){
      String entityLabel = (String)e.nextElement();
      MemexEntity ent = entitiesByLabel.get(entityLabel);
      if (!ent.isMetaDB())
      {
        allentityLabels.add(entityLabel);
      }
    }

    // Go through the
    //Convert the entity array list
    //to a string array
    String returnArray[] = new String[allentityLabels.size()];
    for(int i = 0; i < allentityLabels.size(); i++){
      returnArray[i] = (String)allentityLabels.get(i);
    }
    Arrays.sort(returnArray, String.CASE_INSENSITIVE_ORDER);

    // Next, find the internal name for each one and build the return array
    NameDescription[] rval = new NameDescription[returnArray.length];
    for (int i = 0; i < returnArray.length; i++)
    {
      rval[i] = new NameDescription(returnArray[i],entitiesByLabel.get(returnArray[i]).getPrefix());
    }
    return rval;
  }

  /** Return a list of the field names for the entity prefix in the implied connection, in sorted alphabetic order */
  public String[] listFieldNames(String entityPrefix)
    throws LCFException, ServiceInterruption
  {
    //Start by making sure we're connected
    this.setupConnection();

    if(entitiesByPrefix.containsKey(entityPrefix)){
      MemexEntity entity = entitiesByPrefix.get(entityPrefix);
      if (entity != null)
        return entity.getFields();
    }
    throw new LCFException("Entity type '"+entityPrefix+"' does not exist");
  }

  /** Return a list of the field names that mie can directly fetch from a record (for document specification) */
  public String[] listMatchableFieldNames(String entityPrefix)
    throws LCFException, ServiceInterruption
  {
    String[] candidates = listFieldNames(entityPrefix);
    if (candidates == null)
      return null;
    // Strip out the ones that have a '.' in them
    int count = 0;
    int i = 0;
    while (i < candidates.length)
    {
      if (candidates[i].indexOf(".") == -1)
        count++;
      i++;
    }
    String[] rval = new String[count];
    count = 0;
    i = 0;
    while (i < candidates.length)
    {
      if (candidates[i].indexOf(".") == -1)
      {
        rval[count++] = candidates[i];
      }
      i++;
    }
    return rval;
  }

  // Utility methods

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
  protected static void packList(StringBuffer output, ArrayList<String> values, char delimiter)
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
  *@param output is the array to unpack the list into.
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@return the next position beyond the end of the list.
  */
  protected static int unpackList(ArrayList<String> output, String value, int startPosition, char delimiter)
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

  //Memex Connector specific helper methods

  //////////////////////////////////////////////////////////////////////
  //
  //Method that looks to see if a connection has been established and
  //if so, is it still valid. If not, creates / recreates the connection
  //
  ///////////////////////////////////////////////////////////////////////
  private void setupConnection()
    throws LCFException, ServiceInterruption
  {

    boolean connected = false;
    if((this.physicalServers != null) && !(this.physicalServers.isEmpty())){
      //If we have entries in the physical server collection, check they are all connected
      connected = true;
      for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
        String serverkey = (String)serverkeys.nextElement();
        LCFMemexConnection pserver = physicalServers.get(serverkey);
        if(!(pserver.isConnected())){
          connected = false;
        }
      }
      if (!connected)
        //Clear any existing connections
        this.cleanUpConnections();
    }

    if(!connected){

      try{
        miePool.setUsername(userName);
        miePool.setPassword(userPassword);
        miePool.setHostname(hostName);
        miePool.setPort(hostPort);
        miePool.setCharset(characterEncoding);

        //Initialise data structures
        mieConnection = new LCFMemexConnection();
        logicalServers = new Hashtable<String, LogicalServer>();
        logicalServersByPrefix = new Hashtable<String, LogicalServer>();
        physicalServers = new Hashtable<String, LCFMemexConnection>();
        entitiesByName = new Hashtable<String, MemexEntity>();
        entitiesByLabel = new Hashtable<String, MemexEntity>();
        entitiesByPrefix = new Hashtable<String, MemexEntity>();

        //Start out creating a connection to the Configuration Server.
        mieConnection.mie = miePool.getSystemConnection();
        Registry reg = mieConnection.mie.mxie_dbreg_init();
        mieConnection.localRegistry = mieConnection.mie.mxie_dbreg_list(reg);
        mieConnection.mie.mxie_dbreg_close(reg);
        reg = null;

        //Add the configuration server as the first entry in the physical servers collection.
        //There may be more physical servers - we'll discover this later with a call to getServers
        String key = miePool.getHostname() + ":" + Integer.toString(miePool.getPort());
        mieConnection.name = key;
        physicalServers.put(key, mieConnection);
        mieConnection.ConnectionMessage = "Connection to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " created";

        //Create a collection of data structures describing the entities in this set-up
        this.getEntities();

        //Create a collection of data structures describing each physical server in this set up. The
        //configuration server has laready been added.
        this.getServers();

      }
      catch(PoolAuthenticationException e){
        throw new LCFException("Authentication failure connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort())+": "+e.getMessage(),e);
      }
      catch(PoolException e){
        Logging.connectors.warn("Memex: Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",e);
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",
          e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      catch(MemexException e){
        Logging.connectors.warn("Memex: Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",e);
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",
          e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
    }

    connectionExpirationTime = System.currentTimeMillis() + CONNECTION_IDLE_INTERVAL;
  }

  /////////////////////////////////////////////////////
  //
  //Method disconnects any existing MIE connections
  //and sets datastructures to null
  //
  /////////////////////////////////////////////////////
  private void cleanUpConnections(){

    //Clear any existing connections
    if(physicalServers != null){
      // Don't want to remove things while enumerating, so build a list first
      String[] serverKeyArray = new String[physicalServers.size()];
      int i = 0;
      for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
        String serverkey = (String)serverkeys.nextElement();
        serverKeyArray[i++] = serverkey;
      }

      while (i < serverKeyArray.length)
      {
        String serverkey = serverKeyArray[i++];
        LCFMemexConnection currentMIE = physicalServers.get(serverkey);
        try{
          // Remove history directories belonging to this session
          physicalServers.remove(serverkey);
          String histdir = currentMIE.mie.mxie_history_current();
          currentMIE.mie.mxie_history_close();
          currentMIE.mie.mxie_svrfile_rmdir(histdir, true);
          currentMIE.mie.mxie_connection_logout();
          currentMIE.mie.mxie_connection_shutdown();
        }
        catch(MemexException e){
          Logging.connectors.warn("Memex exception logging out virtual server "+serverkey+": "+e.getMessage(),e);
        }
      }
      mieConnection = null;
      miePool.close();
      logicalServers = null;
      logicalServersByPrefix = null;
      physicalServers = null;
      entitiesByName = null;
      entitiesByLabel = null;
      entitiesByPrefix = null;
      connectionExpirationTime = -1L;

    }
  }

  /**Creates an alphabetically ordered list of entity objects.
  */
  private void getEntities()
    throws MemexException, LCFException, ServiceInterruption
  {
    String mxEntityPath = null;
    String[] entityReturn = new String[1];

    //Start by locating the mxEntity database on the Config Server
    if(mieConnection.localRegistry != null){
      Map<String,RegistryEntry> registryMap = new HashMap<String,RegistryEntry>();
      int i;
      int dbcount = mieConnection.localRegistry.length;
      for(i=0;i<dbcount;i++){
        RegistryEntry re = mieConnection.localRegistry[i];
        String name = re.getName();
        if (name != null)
        {
          // The registry name consists of a name part + "." + the URN
          registryMap.put(name.substring(name.indexOf(".")+1),re);
          name = name.substring(0,name.indexOf("."));
          if(name.equals("mxEntity")){
            mxEntityPath = re.getPath();
          }
        }
      }
      if(mxEntityPath != null && !mxEntityPath.equals("")){
        String configServerPath = mxEntityPath.substring(0, mxEntityPath.indexOf("mxEntity"));
        //get all entries from the mxEntity db and create an mxEntity
        //object for each

        int hist = 0;
        int numHits = 0;
        SearchStatus entitySearch = mieConnection.mie.mxie_search(mxEntityPath, "e|!e", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
        if (entitySearch.getNumberOfHits() < 0) {
          throw new MemexException("Memex Error retrieving entity information: " + mieConnection.mie.mxie_error());
        }else{
          hist = entitySearch.getHistory();
          for(int x = 1; x <= entitySearch.getNumberOfHits(); x++){
            //Field 2 is the server name in the mxServer database
            ArrayList entityFields = new ArrayList();
            DecodedField entityURN = new DecodedField(hist, x, 1, 100);
            DecodedField entityfields = new DecodedField(hist, x, 2, 100000);
            DecodedField entityprefix = new DecodedField(hist, x, 8, 100);
            DecodedField entityname = new DecodedField(hist, x, 10, 100);
            DecodedField entitylabels = new DecodedField(hist, x, 33, 100000);
            DecodedField entitydisplayname = new DecodedField(hist, x, 40, 100);
            entityFields.add(entityURN);
            entityFields.add(entityfields);
            entityFields.add(entityprefix);
            entityFields.add(entityname);
            entityFields.add(entitylabels);
            entityFields.add(entitydisplayname);
            //mieConnection.mie.mxie_goto_record(hist, x);
            mieConnection.mie.mxie_decode_fields(entityFields);

            //Get the form file for the entity
            String entityNameString = entityname.getText();
            String entityURNString = entityURN.getText();

            if (entityNameString != null && entityNameString.length() > 0)
            {
              Document entityForm = null;
              try{
                InputStream formStream = mieConnection.mie.mxie_svrfile_read(configServerPath + "files/forms/" + entityNameString + ".form.xml");
                try
                {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  //Using factory get an instance of document builder
                  DocumentBuilder db = dbf.newDocumentBuilder();
                  // Parse it!
                  entityForm = db.parse(formStream);
                }catch(ParserConfigurationException e){
                  throw new LCFException("Can't find a valid parser: "+e.getMessage(),e);
                }catch(SAXException e){
                  throw new LCFException("XML had parse errors: "+e.getMessage(),e);
                }catch(InterruptedIOException e){
                  throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
                }catch(IOException e){
                  // I/O problem; treat as  a service interruption
                  long currentTime = System.currentTimeMillis();
                  throw new ServiceInterruption("Problem initializing connection: " + e.getMessage(),e,currentTime + 300000L,
                    currentTime + 12 * 60 * 60000L,-1,true);
                }
                finally
                {
                  try
                  {
                    formStream.close();
                  }
                  catch (InterruptedIOException e)
                  {
                    throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    throw new LCFException("Error reading memex form data: "+e.getMessage(),e);
                  }
                }
              }catch(MemexException e){
                // This means file doesn't exist, which might be OK for some kinds of entities we may encounter
              }

              // Get the entity's config, so we can map field names to indexes for it.  This needs the database path.
              RegistryEntry regEntry = registryMap.get(entityURNString);
              // We ONLY care about entities that have had their databases created.  All others we should ignore at this point.
              if (regEntry != null)
              {
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Memex: Found registry entry for '"+entityURNString+"'");
                MemexEntity ent = new MemexEntity(entityNameString, entityURNString, entityprefix.getText(), entitydisplayname.getText(), entityfields.getText(), entitylabels.getText(), entityForm);
                entitiesByName.put(ent.getName(), ent);
                entitiesByPrefix.put(ent.getPrefix(), ent);
                entitiesByLabel.put(ent.getDisplayName(), ent);
              }
              else
              {
                Logging.connectors.warn("Memex: Could not find registry entry for '"+entityURNString+"' ("+entityNameString+") - skipping");
              }

            }
          }
        }
      }
    }
  }

  /**Creates a list of logical server objects.
  *Configuration Server is always the first entry in the list, all other
  *server are listed alphabetically thereafter
  */
  private void getServers()
    throws MemexException
  {
    //Start by locating the mxServer database on the Config Server
    String mxServerPath = null;

    //Start by locating the mxServer database on the Config Server
    if (mieConnection.localRegistry != null)
    {
      int i = 0;
      while (i < mieConnection.localRegistry.length)
      {
        RegistryEntry regEntry = mieConnection.localRegistry[i++];
        String entityName = regEntry.getName();
        if (entityName != null && entityName.length() > 0)
        {
          entityName = entityName.substring(0,entityName.indexOf("."));
          if (entityName.equals("mxServer"))
          {
            mxServerPath = regEntry.getPath();
            break;
          }
        }
      }
      if(mxServerPath != null && !mxServerPath.equals("")){
        //get all entiries from the mxServer db and create a connection
        //to any remote physical servers.

        int hist = 0;
        int numHits = 0;
        SearchStatus serverSearch = mieConnection.mie.mxie_search(mxServerPath, "e|!e", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
        if (serverSearch.getNumberOfHits() < 0) {
          throw new MemexException("Memex Error retrieving Server information : " + mieConnection.mie.mxie_error());
        }else{
          for(int x = 1; x <= serverSearch.getNumberOfHits(); x++){
            //Field 2 is the server name in the mxServer database
            ArrayList serverFields = new ArrayList();
            DecodedField servername = new DecodedField(hist, x, 2, 100);
            DecodedField serverprefix = new DecodedField(hist, x, 3, 100);
            DecodedField serversource = new DecodedField(hist, x, 5, 100);
            serverFields.add(servername);
            serverFields.add(serverprefix);
            serverFields.add(serversource);
            //mieConnection.mie.mxie_goto_record(hist, x);
            mieConnection.mie.mxie_decode_fields(serverFields);
            LCFMemexConnection mie;
            if(serversource.getText().equals("configuration-server")){
              mie = mieConnection;
            }else{
              //this logical server lives on a remote physical server
              //extract the port and server strings from the source
              String[] source = serversource.getText().split("\n");
              String remoteserver = source[2].substring(4);
              String remoteport = source[3].substring(5);
              mie = getPhysicalServer(remoteserver, Integer.parseInt(remoteport));
            }
            //Now create a list of databases on this server
            LogicalServer ls = new LogicalServer(servername.getText(),serverprefix.getText(),mie,entitiesByName);
            logicalServers.put(ls.getServerName(), ls);
            logicalServersByPrefix.put(ls.getPrefix(), ls);
          }
        }
      }
    }
  }

  private LCFMemexConnection getPhysicalServer(String server, int port){

    String key = server + ":" + Integer.toString(port);

    if(physicalServers.containsKey(key)){
      return (LCFMemexConnection)physicalServers.get(key);
    }else{
      LCFMemexConnection newServer = new LCFMemexConnection();
      try{
        MemexConnection newMIE = miePool.getConnection(server, port);
        newServer.mie = newMIE;
        Registry reg = newServer.mie.mxie_dbreg_init();
        newServer.localRegistry = newServer.mie.mxie_dbreg_list(reg);
        newServer.mie.mxie_dbreg_close(reg);
        reg = null;
        newServer.ConnectionMessage = "Connection to Memex Server " + server + ":" + Integer.toString(port) + " created";
        newServer.name = key;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(PoolAuthenticationException e){
        newServer.ConnectionMessage = "Authentication failure connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort());
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(PoolException e){
        newServer.ConnectionMessage = "Error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage();
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(MemexException e){
        newServer.ConnectionMessage = "Error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage();
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
    }
  }


  private String getMemexDate(long epochmilis){

    TimeZone tz = TimeZone.getTimeZone(serverTimezone);
    Calendar calendar = new GregorianCalendar(tz);
    Date theDate = new Date(epochmilis);
    calendar.setTime(theDate);

    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    int year = calendar.get(Calendar.YEAR);

    return "date" + String.format("%04d",year) + String.format("%02d",month) + String.format("%02d",day) + " month" + String.format("%02d",month) + String.format("%04d",year);
  }

  /*
  * Method checks a given URN against the current document specifcation
  * and ensures the record comes from a database we're meant to be ingesting.
  * If it does come from a valid DB, return an array list with the list of fields
  * required. The first field in the list should be the primary field, evrything else
  * is a meta field.
  * @param recordURN is the URN of the record being considered
  * @param spec is the current digested document specification
  * @return the EntityDescription corresponding to this URN, or null if the URN should not be ingested.
  *
  */
  private EntityDescription checkIngest(String recordURN, CrawlDescription spec){

    String serverName = logicalServersByPrefix.get(recordURN.substring(0,2)).getServerName();
    String entityPrefix = recordURN.substring(2,4);

    return spec.getEntityDescription(serverName,entityPrefix);
  }

  /*
  * Method to do a regex check for Memex format dates, returning the string with
  * any dates swapped for normal format dates.
  *
  */
  private String regexDateSwap(String test){

    try{

      Pattern ptrn = Pattern.compile("(date((\\d{4})(\\d{2})(\\d{2})) month\\d{6})");
      Matcher matcher = ptrn.matcher(test);
      while (matcher.find()) {
        int start = matcher.start(1);
        int end = matcher.end(1);
        String year = test.substring(matcher.start(3), matcher.end(3));
        String month = test.substring(matcher.start(4), matcher.end(4));
        int mm = Integer.parseInt(month);
        switch (mm) {
        case 1:  month = "JAN"; break;
        case 2:  month = "FEB"; break;
        case 3:  month = "MAR"; break;
        case 4:  month = "APR"; break;
        case 5:  month = "MAY"; break;
        case 6:  month = "JUN"; break;
        case 7:  month = "JUL"; break;
        case 8:  month = "AUG"; break;
        case 9:  month = "SEP"; break;
        case 10:  month = "OCT"; break;
        case 11:  month = "NOV"; break;
        case 12:  month = "DEC"; break;
        default: return test;
        }

        String day = test.substring(matcher.start(5), matcher.end(5));
        test = test.substring(0, start) + day + "-" + month + "-" + year + test.substring(end);
        matcher = ptrn.matcher(test);
      }
      return test;
    }
    catch(Exception e){
      return "";
    }
  }

  /*
  * Return an HashTable represtation of a Memex Patriarch Record
  * Normal fields are stored as strings. Subrecords are stored as
  * an array list of hashtables;
  * @param urn - the urn of the record being sought
  * @return - the hash table representation of the record. Null if its not found
  */
  private Hashtable getmxRecordObj(LogicalServer ls, int histno, int recnum)
    throws LCFException, ServiceInterruption
  {
    Hashtable mxRecord = null;
    try{
      mxRecord = new Hashtable();
      //Get the record header and look for any covert locks
      String sepinfo = ls.getMIE().mie.mxie_decode_sepinfo(histno, recnum);
      if(sepinfo == null){
        //Do something if the record's disappeared between the search completing
        //and us decoding the record.
        return null;
      }
      //generation number
      int start = sepinfo.indexOf(",");
      int end = sepinfo.indexOf(",", start+1);
      mxRecord.put("meta_gennum", sepinfo.substring(start+2, end));

      //datestamp
      start = sepinfo.indexOf(",", end+1);
      end = sepinfo.indexOf("]");
      mxRecord.put("meta_datestamp", sepinfo.substring(start+2, end));

      //Covert locks
      if(sepinfo.contains("(")){
        sepinfo = sepinfo.substring(sepinfo.indexOf("("), sepinfo.length());
        mxRecord.put("meta_clocks", sepinfo);
      }

      //Decode the record
      try {
        ls.getMIE().mie.mxie_goto_record(histno, recnum);
      }
      catch (MemexException e) {
        // Record is not there; return null to signal this situation upstream
        return null;
      }
      InputStream isDecode = ls.getMIE().mie.mxie_decode_record("", "", 3, MemexConnection.MXIE_FORWARD);
      if(isDecode == null){
        //Do something if the record's disappeared between the search completing
        //and us decoding the record.
        return null;
      }
      // I need a client encoding specification in repository connection!!
      BufferedReader reader = new BufferedReader(new InputStreamReader(isDecode,characterEncoding));
      String line = null;
      try {
        String fieldname = null;
        String value = "";
        String subname = null;
        Hashtable subvalues = null;
        while((line = reader.readLine()) != null){
          //Loop through the record - each field should be added as an entry in the hash table
          if(line.startsWith("xx")){
            //we've found a field definition
            //Add the previous field to the hash table
            if(fieldname != null){
              if(subname != null){
                //We're currently processing a subrecord
                subvalues.put(fieldname, value);
              }else{
                mxRecord.put(fieldname, value);
              }
              fieldname = null;
              value = "";
            }
            fieldname = line.substring(2);
          }
          else if(line.startsWith("ssssssss")){
            //we've found the level seperator
            //Add any previous field to the hash table
            if(fieldname != null){
              if(subname != null){
                //We're currently processing a subrecord
                subvalues.put(fieldname, value);
              }else{
                mxRecord.put(fieldname, value);
              }
              fieldname = null;
              value = "";
            }
            //Now look to see if there are any protected keys
            if(line.contains("(")){
              line = line.substring(line.indexOf("("), line.length());
              mxRecord.put("meta_plocks", line);
            }
          }
          else if(line.startsWith("cccccccc")){
            //we've found the sub-record seperator
            //Add any previous field to the hash table
            if(fieldname != null){
              if(subname != null){
                //We're currently processing a subrecord
                subvalues.put(fieldname, value);
              }else{
                mxRecord.put(fieldname, value);
              }
              fieldname = null;
              value = "";
            }
            //This is the start of a new subrecord, so write
            //any subrecord we were previosuly processing to
            //the main hash
            if(subname != null){
              ArrayList subarray = null;
              if(!(mxRecord.containsKey("subrecords"))){
                mxRecord.put("subrecords", new Hashtable<String, ArrayList>());
              }
              if(!(((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).containsKey(subname))){
                subarray = new ArrayList();
              }else{
                subarray = ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).get(subname);
                ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).remove(subname);
              }
              subarray.add(subvalues);
              ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).put(subname, subarray);
              subname = null;
              subvalues = null;
            }
            //Now read in the name of the new subrecord
            line = reader.readLine(); //Should be xxsyscategory
            line = reader.readLine();
            subname = line;
            subvalues = new Hashtable();
          }
          else if(!(line.startsWith("rrrrrrrr"))){
            //This is just a normal field value line
            if(value.equals("")){
              value = regexDateSwap(line);
            }else{
              value = value + "\n" + regexDateSwap(line);
            }
          }
        }
        //We've reached the end of the stream - check to see if there are any final
        //fields or subrecords to add
        if(fieldname != null){
          if(subname != null){
            //We're currently processing a subrecord
            subvalues.put(fieldname, value);
          }else{
            mxRecord.put(fieldname, value);
          }
          fieldname = null;
          value = "";
        }

        //Was this a subrecord?
        if(subname != null){
          ArrayList subarray = null;
          if(!(mxRecord.containsKey("subrecords"))){
            mxRecord.put("subrecords", new Hashtable<String, ArrayList>());
          }
          if(!(((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).containsKey(subname))){
            subarray = new ArrayList();
          }else{
            subarray = ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).get(subname);
            ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).remove(subname);
          }
          subarray.add(subvalues);
          ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).put(subname, subarray);
          subname = null;
          subvalues = null;
        }

        return mxRecord;

      }
      catch (InterruptedIOException eek)
      {
        throw new LCFException(eek.getMessage(),eek,LCFException.INTERRUPTED);
      }
      catch (IOException eek) {
        // Treat this as a service interruption
        Logging.connectors.warn("Memex: Couldn't read record from "+ls.getServerName()+"; record id="+Integer.toString(recnum)+": "+eek.getMessage()+" - retrying",eek);
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Couldn't read record from "+ls.getServerName()+"; record id="+Integer.toString(recnum)+": "+eek.getMessage(),eek,currentTime + 300000L,
          currentTime + 12 * 60 * 60000L,-1,true);
      }
      finally
      {
        // Close the reader
        reader.close();
      }
    }catch(MemexException mex){
      // Throw a transient error??  It would be good to know if this could happen (a) as a result of record deletion, or
      // (b) as a result of security issues, and treat that separately
      Logging.connectors.warn("Memex: Couldn't read record?  Record id ? Message: "+mex.getMessage()+" - retrying",mex);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Problem reading record from Memex, retrying: " + mex.getMessage(),mex,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (UnsupportedEncodingException e){
      Logging.connectors.error("Memex: "+e.getMessage(),e);
      throw new LCFException(e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException(e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException(e.getMessage(),e);
    }
  }

  /** This class contains data describing a single entity, in digested form */
  protected static class EntityDescription
  {
    /** This is an arraylist of primary fields, in order */
    protected ArrayList<String> primaryFields = new ArrayList<String>();
    /** This is the set of metadata fields, in no particular order */
    protected ArrayList<String> metadataFields = new ArrayList<String>();
    /** These are the sorted metadata fields, initialized when needed */
    protected ArrayList<String> sortedMetadataFields = null;

    public EntityDescription()
    {
    }

    public void appendPrimaryField(String primaryField)
    {
      primaryFields.add(primaryField);
    }

    public void addMetadataField(String metadataField)
    {
      metadataFields.add(metadataField);
      sortedMetadataFields = null;
    }

    public int getPrimaryFieldCount()
    {
      return primaryFields.size();
    }

    public String getPrimaryField(int index)
    {
      return primaryFields.get(index);
    }

    public ArrayList<String> getPrimaryFields()
    {
      return primaryFields;
    }

    public int getMetadataFieldCount()
    {
      return metadataFields.size();
    }

    public String getMetadataField(int index)
    {
      sortFields();
      return sortedMetadataFields.get(index);
    }

    public ArrayList<String> getMetadataFields()
    {
      sortFields();
      return sortedMetadataFields;
    }

    protected void sortFields()
    {
      if (sortedMetadataFields == null)
      {
        String[] sortArray = new String[metadataFields.size()];
        int i = 0;
        while (i < sortArray.length)
        {
          sortArray[i] = metadataFields.get(i);
          i++;
        }
        java.util.Arrays.sort(sortArray);
        i = 0;
        sortedMetadataFields = new ArrayList<String>();
        while (i < sortArray.length)
        {
          sortedMetadataFields.add(sortArray[i++]);
        }
      }
    }

  }

  /** This class describes an individual crawl rule, which is unmodifiable */
  protected static class CrawlRule
  {
    /** The virtual server, or null if none specified */
    protected String virtualServer;
    /** The entity prefix, or null if none specified */
    protected String entityPrefix;
    /** The field name to match, or null if none specified */
    protected String fieldName;
    /** The match operation, or null if no field match specified */
    protected String operation;
    /** The match value, or null if no field match specified */
    protected String fieldValue;

    public CrawlRule(String virtualServer, String entityPrefix, String fieldName, String operation, String fieldValue)
    {
      this.virtualServer = virtualServer;
      this.entityPrefix = entityPrefix;
      this.fieldName = fieldName;
      this.operation = operation;
      this.fieldValue = fieldValue;
    }

    public String getVirtualServer()
    {
      return virtualServer;
    }

    public String getEntityPrefix()
    {
      return entityPrefix;
    }

    public String getFieldName()
    {
      return fieldName;
    }

    public String getOperation()
    {
      return operation;
    }

    public String getFieldValue()
    {
      return fieldValue;
    }
  }

  /** This class describes an individual match description, which is unmodifiable */
  protected static class CrawlMatchDescription
  {
    /** The field name */
    protected String fieldName;
    /** The operation */
    protected String operation;
    /** The field value */
    protected String fieldValue;

    public CrawlMatchDescription(String fieldName, String operation, String fieldValue)
    {
      this.fieldName = fieldName;
      this.operation = operation;
      this.fieldValue = fieldValue;
    }

    public String getFieldName()
    {
      return fieldName;
    }

    public String getOperation()
    {
      return operation;
    }

    public String getFieldValue()
    {
      return fieldValue;
    }
  }

  /** This class contains data in a digested form from a DocumentSpecification object */
  protected static class CrawlDescription
  {
    /** This is the list of rule tuples culled from the specification.  It's used to decide what records we will crawl */
    protected ArrayList<CrawlRule> crawlRules = new ArrayList<CrawlRule>();
    /** This is a hashmap keyed by entity name, and containing an EntityDescription object */
    protected HashMap<String,EntityDescription> entities = new HashMap<String,EntityDescription>();
    /** Security switch  */
    protected boolean securityOn = true;
    /** This array contains the forced acls, if any, in sorted order */
    protected String[] orderedTokens;

    public CrawlDescription(DocumentSpecification spec)
    {
      // Temporary place to accumulate stuff
      HashMap<String,String> forcedAcls = new HashMap<String,String>();

      // Do one pass over the nodes to build our object
      for(int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode sn = spec.getChild(i);
        if (sn.getType().equals(SPEC_NODE_SPECIFICATIONRULE))
        {
          String virtualServerName = sn.getAttributeValue(SPEC_ATTRIBUTE_VIRTUALSERVER);
          String entityPrefix = sn.getAttributeValue(SPEC_ATTRIBUTE_ENTITY);
          String fieldName = sn.getAttributeValue(SPEC_ATTRIBUTE_FIELDNAME);
          String operation = sn.getAttributeValue(SPEC_ATTRIBUTE_OPERATION);
          String fieldValue = sn.getAttributeValue(SPEC_ATTRIBUTE_FIELDVALUE);
          // Create a rule record
          crawlRules.add(new CrawlRule(virtualServerName,entityPrefix,fieldName,operation,fieldValue));
        }
        else if (sn.getType().equals(SPEC_NODE_ENTITY))
        {
          String entityName = sn.getAttributeValue(SPEC_ATTRIBUTE_NAME);
          // Look through children
          EntityDescription ed = new EntityDescription();
          for(int y = 0; y < sn.getChildCount(); y++)
          {
            SpecificationNode snEntitychild = sn.getChild(y);
            if(snEntitychild.getType().equals(SPEC_NODE_PRIMARYFIELD))
            {
              ed.appendPrimaryField(snEntitychild.getAttributeValue(SPEC_ATTRIBUTE_NAME));
            }
            else if(snEntitychild.getType().equals(SPEC_NODE_METAFIELD))
            {
              ed.addMetadataField(snEntitychild.getAttributeValue(SPEC_ATTRIBUTE_NAME));
            }
          }
          entities.put(entityName,ed);
        }
        else if (sn.getType().equals(SPEC_NODE_ACCESS))
        {
          String token = sn.getAttributeValue(SPEC_ATTRIBUTE_TOKEN);
          forcedAcls.put(token,token);
        }
        else if (sn.getType().equals(SPEC_NODE_SECURITY))
        {
          String value = sn.getAttributeValue(SPEC_ATTRIBUTE_VALUE);
          if (value.equals(SPEC_VALUE_ON))
            securityOn = true;
          else if (value.equals(SPEC_VALUE_OFF))
            securityOn = false;
        }

      }

      orderedTokens = new String[forcedAcls.size()];
      Iterator iter = forcedAcls.keySet().iterator();
      int i = 0;
      while (iter.hasNext())
      {
        orderedTokens[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(orderedTokens);
    }

    /** Check if a given server and entity should be crawled, based only on the servername and entity prefix */
    public boolean shouldCrawlServerEntity(String serverName, String entityPrefix)
    {
      // Run through the rule list, looking for matches
      int i = 0;
      while (i < crawlRules.size())
      {
        CrawlRule cr = crawlRules.get(i++);
        // Rules are always inclusive, so if we find any partial match, we must crawl the entity
        String ruleVs = cr.getVirtualServer();
        if (ruleVs == null)
          // All virtual servers, all entities
          return true;
        if (!ruleVs.equals(serverName))
          continue;
        String ruleEntity = cr.getEntityPrefix();
        if (ruleEntity == null)
          // All entities for this virtual server
          return true;
        if (!ruleEntity.equals(entityPrefix))
          continue;
        // A match was found!
        return true;
      }
      // No match at all
      return false;
    }

    /** Get the entity description for an entity, or null if it should not be ingested based on the server name and prefix */
    public EntityDescription getEntityDescription(String serverName, String entityPrefix)
    {
      if (!shouldCrawlServerEntity(serverName,entityPrefix))
        return null;
      return entities.get(entityPrefix);
    }

    /** Return true if we should crawl a given virtual server. */
    public boolean shouldCrawlVirtualServer(String serverName)
    {
      // Go through the rules and see if there is a match
      int i = 0;
      while (i < crawlRules.size())
      {
        CrawlRule cr = crawlRules.get(i++);
        // Rules are always inclusive, so if we find any partial match, we must crawl the entity
        String ruleVs = cr.getVirtualServer();
        if (ruleVs == null)
          // All virtual servers, all entities
          return true;
        if (!ruleVs.equals(serverName))
          continue;
        // A match was found!
        return true;
      }
      // No match at all
      return false;
    }

    /** Get the crawl rules in effect, if any, for a given virtual server/entity combination.  Returns null if there should be no crawling, or an empty list if there are no limitations.
    */
    public List<CrawlMatchDescription> getCrawlMatches(String serverName, String entityPrefix)
    {
      // Before we do anything, the entity in question better have an ingestion description
      if (entities.get(entityPrefix) == null)
        return null;

      // Create the return array
      List<CrawlMatchDescription> returnArray = new ArrayList<CrawlMatchDescription>();
      // Go through the rules, and whenever we find a match, add the criteria to the list
      int i = 0;
      boolean sawMatch = false;
      boolean noRestrictions = false;
      while (i < crawlRules.size())
      {
        CrawlRule cr = crawlRules.get(i++);
        // Rules are always inclusive, so if we find any partial match, we must crawl the entity
        String ruleVs = cr.getVirtualServer();
        if (ruleVs == null)
        {
          // All virtual servers, all entities
          sawMatch = true;
          noRestrictions = true;
          continue;
        }
        if (!ruleVs.equals(serverName))
        {
          // This rule does not apply
          continue;
        }
        String ruleEntity = cr.getEntityPrefix();
        if (ruleEntity == null)
        {
          // All entities for the (matching) virtual server
          sawMatch = true;
          noRestrictions = true;
          continue;
        }
        if (!ruleEntity.equals(entityPrefix))
        {
          // This rule does not apply
          continue;
        }

        // A match was found!
        sawMatch = true;
        if (cr.getFieldName() == null)
        {
          // Open-ended match
          noRestrictions = true;
        }
        else
        {
          // There's a restriction, so add it to the list.
          returnArray.add(new CrawlMatchDescription(cr.getFieldName(),cr.getOperation(),cr.getFieldValue()));
        }
      }
      if (sawMatch)
      {
        // If no restrictions, that overrides everything, and we return an empty list to signal that condition.
        if (noRestrictions)
        {
          returnArray.clear();
          return returnArray;
        }
        // There are restrictions, and they are enumerated within the accumulated list
        return returnArray;
      }
      return null;
    }

    /** Check if security is on */
    public boolean isSecurityOn()
    {
      return securityOn;
    }

    /** Get the forced acls */
    public String[] getForcedAcls()
    {
      return orderedTokens;
    }

  }
}




