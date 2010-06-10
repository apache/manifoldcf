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
package org.apache.lcf.crawler.connectors.jdbc;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.core.database.*;

import java.sql.*;
import javax.naming.*;
import javax.sql.*;

import java.io.*;
import java.util.*;

/** This interface describes an instance of a connection between a repository and LCF's
* standard "pull" ingestion agent.
*
* Each instance of this interface is used in only one thread at a time.  Connection Pooling
* on these kinds of objects is performed by the factory which instantiates repository connectors
* from symbolic names and config parameters, and is pooled by these parameters.  That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
*
* Implementers of this interface should provide a default constructor which has this signature:
*
* xxx();
*
* Connectors are either configured or not.  If configured, they will persist in a pool, and be
* reused multiple times.  Certain methods of a connector may be called before the connector is
* configured.  This includes basically all methods that permit inspection of the connector's
* capabilities.  The complete list is:
*
*
* The purpose of the repository connector is to allow documents to be fetched from the repository.
*
* Each repository connector describes a set of documents that are known only to that connector.
* It therefore establishes a space of document identifiers.  Each connector will only ever be
* asked to deal with identifiers that have in some way originated from the connector.
*
* Documents are fetched in three stages.  First, the getDocuments() method is called in the connector
* implementation.  This returns a set of document identifiers.  The document identifiers are used to
* obtain the current document version strings in the second stage, using the getDocumentVersions() method.
* The last stage is processDocuments(), which queues up any additional documents needed, and also ingests.
* This method will not be called if the document version seems to indicate that no document change took
* place.
*/
public class JDBCConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Activities that we know about
  protected final static String ACTIVITY_EXTERNAL_QUERY = "external query";

  // Activities list
  protected static final String[] activitiesList = new String[]{ACTIVITY_EXTERNAL_QUERY};

  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  protected JDBCConnection connection = null;
  protected String jdbcProvider = null;
  protected String host = null;
  protected String databaseName = null;
  protected String userName = null;
  protected String password = null;

  /** Constructor.
  */
  public JDBCConnector()
  {
  }

  /** Set up a session */
  protected void getSession()
    throws LCFException
  {
    if (connection == null)
    {
      if (jdbcProvider == null || jdbcProvider.length() == 0)
        throw new LCFException("Missing parameter '"+JDBCConstants.providerParameter+"'");
      if (host == null || host.length() == 0)
        throw new LCFException("Missing parameter '"+JDBCConstants.hostParameter+"'");

      connection = new JDBCConnection(jdbcProvider,host,databaseName,userName,password);
    }
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return activitiesList;
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
    return "jdbc";
  }

  /** Model.  Depending on what people enter for the seeding query, this could be either ALL or
  * could be less than that.  So, I've decided it will be at least the adds and changes, and
  * won't include the deletes.
  */
  public int getConnectorModel()
  {
    return MODEL_ADD_CHANGE;
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    jdbcProvider = configParams.getParameter(JDBCConstants.providerParameter);
    host = configParams.getParameter(JDBCConstants.hostParameter);
    databaseName = configParams.getParameter(JDBCConstants.databaseNameParameter);
    userName= configParams.getParameter(JDBCConstants.databaseUserName);
    password = configParams.getObfuscatedParameter(JDBCConstants.databasePassword);
  }

  /** Check status of connection.
  */
  public String check()
    throws LCFException
  {
    try
    {
      getSession();
      // Attempt to fetch a connection; if this succeeds we pass
      connection.testConnection();
      return super.check();
    }
    catch (ServiceInterruption e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Service interruption in check(): "+e.getMessage(),e);
      return "Transient error: "+e.getMessage();
    }
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws LCFException
  {
    connection = null;
    host = null;
    jdbcProvider = null;
    databaseName = null;
    userName = null;
    password = null;

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
    return new String[]{host};
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
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime, int jobMode)
    throws LCFException, ServiceInterruption
  {
    getSession();

    // Set up the query
    TableSpec ts = new TableSpec(spec);

    VariableMap vm = new VariableMap();
    addConstant(vm,JDBCConstants.idReturnVariable,JDBCConstants.idReturnColumnName);
    addVariable(vm,JDBCConstants.startTimeVariable,startTime);
    addVariable(vm,JDBCConstants.endTimeVariable,endTime);

    // Do the substitution
    ArrayList paramList = new ArrayList();
    StringBuffer sb = new StringBuffer();
    substituteQuery(ts.idQuery,vm,sb,paramList);

    IDynamicResultSet idSet;

    String queryText = sb.toString();
    long startQueryTime = System.currentTimeMillis();
    try
    {
      idSet = connection.executeUncachedQuery(queryText,paramList,-1);
    }
    catch (ServiceInterruption e)
    {
      // If failure, record the failure.
      activities.recordActivity(new Long(startQueryTime), ACTIVITY_EXTERNAL_QUERY, null,
        createQueryString(queryText,paramList), "ERROR", e.getMessage(), null);
      throw e;
    }
    catch (LCFException e)
    {
      // If failure, record the failure.
      activities.recordActivity(new Long(startQueryTime), ACTIVITY_EXTERNAL_QUERY, null,
        createQueryString(queryText,paramList), "ERROR", e.getMessage(), null);
      throw e;
    }

    try
    {
      // If success, record that too.
      activities.recordActivity(new Long(startQueryTime), ACTIVITY_EXTERNAL_QUERY, null,
        createQueryString(queryText,paramList), "OK", null, null);

      while (true)
      {
        IResultRow row = idSet.getNextRow();
        if (row == null)
          break;
        Object o = row.getValue(JDBCConstants.idReturnColumnName);
        if (o == null)
          throw new LCFException("Bad seed query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
        String idValue = o.toString();
        activities.addSeedDocument(idValue);
      }
    }
    finally
    {
      idSet.close();
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
    throws LCFException, ServiceInterruption
  {
    getSession();
    TableSpec ts = new TableSpec(spec);
    String[] acls = getAcls(spec);
    // Sort these,
    java.util.Arrays.sort(acls);

    String[] versionsReturned = new String[documentIdentifiers.length];

    // If there is no version query, then always return empty string for all documents.
    // This will mean that processDocuments will be called
    // for all.  ProcessDocuments will then be responsible for doing document deletes itself,
    // based on the query results.

    if (ts.versionQuery == null || ts.versionQuery.length() == 0)
    {
      int i = 0;
      while (i < versionsReturned.length)
      {
        versionsReturned[i++] = "";
      }

      return versionsReturned;
    }

    // If there IS a versions query, do it.  First set up the variables, then do the substitution.
    VariableMap vm = new VariableMap();
    addConstant(vm,JDBCConstants.idReturnVariable,JDBCConstants.idReturnColumnName);
    addConstant(vm,JDBCConstants.versionReturnVariable,JDBCConstants.versionReturnColumnName);
    if (!addIDList(vm,JDBCConstants.idListVariable,documentIdentifiers,null))
      return new String[0];

    // Do the substitution
    ArrayList paramList = new ArrayList();
    StringBuffer sb = new StringBuffer();
    substituteQuery(ts.versionQuery,vm,sb,paramList);

    // Now, build a result return, and a hash table so we can correlate the returned values with the place to put them.
    // We presume that if the row is missing, the document is gone.
    Map map = new HashMap();
    int j = 0;
    while (j < documentIdentifiers.length)
    {
      map.put(documentIdentifiers[j],new Integer(j));
      versionsReturned[j] = "";
      j++;
    }

    // Fire off the query!
    IDynamicResultSet result;
    String queryText = sb.toString();
    long startTime = System.currentTimeMillis();
    try
    {
      result = connection.executeUncachedQuery(queryText,paramList,-1);
    }
    catch (LCFException e)
    {
      // If failure, record the failure.
      activities.recordActivity(new Long(startTime), ACTIVITY_EXTERNAL_QUERY, null,
        createQueryString(queryText,paramList), "ERROR", e.getMessage(), null);
      throw e;
    }
    // If success, record that too.
    activities.recordActivity(new Long(startTime), ACTIVITY_EXTERNAL_QUERY, null,
      createQueryString(queryText,paramList), "OK", null, null);
    try
    {
      // Now, go through resultset
      while (true)
      {
        IResultRow row = result.getNextRow();
        if (row == null)
          break;
        Object o = row.getValue(JDBCConstants.idReturnColumnName);
        if (o == null)
          throw new LCFException("Bad version query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
        String idValue = o.toString();
        o = row.getValue(JDBCConstants.versionReturnColumnName);
        String versionValue;
        // Null version is OK; make it a ""
        if (o == null)
          versionValue = "";
        else
        {
          // A real version string!  Any acls must be added to the front, if they are present...
          sb = new StringBuffer();
          packList(sb,acls,'+');
          if (acls.length > 0)
          {
            sb.append('+');
            pack(sb,defaultAuthorityDenyToken,'+');
          }
          else
            sb.append('-');

          sb.append(o.toString()).append("=").append(ts.dataQuery);
          versionValue = sb.toString();
        }
        // Versions that are "", when processed, will have their acls fetched at that time...
        versionsReturned[((Integer)map.get(idValue)).intValue()] = versionValue;
      }
    }
    finally
    {
      result.close();
    }

    return versionsReturned;
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
  *       The implementation may choose to ignore this parameter and always process the current version.
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
    TableSpec ts = new TableSpec(spec);

    // For all the documents not marked "scan only", form a query and pick up the contents.
    // If the contents is not found, then explicitly call the delete action method.
    VariableMap vm = new VariableMap();
    addConstant(vm,JDBCConstants.idReturnVariable,JDBCConstants.idReturnColumnName);
    addConstant(vm,JDBCConstants.urlReturnVariable,JDBCConstants.urlReturnColumnName);
    addConstant(vm,JDBCConstants.dataReturnVariable,JDBCConstants.dataReturnColumnName);
    if (!addIDList(vm,JDBCConstants.idListVariable,documentIdentifiers,scanOnly))
      return;

    // Do the substitution
    ArrayList paramList = new ArrayList();
    StringBuffer sb = new StringBuffer();
    substituteQuery(ts.dataQuery,vm,sb,paramList);

    int i;

    // Build a map of versions we are allowed to ingest
    Map map = new HashMap();
    i = 0;
    while (i < documentIdentifiers.length)
    {
      if (!scanOnly[i])
      {
        // Version strings at this point should never be null; the CF interprets nulls as
        // meaning that delete must occur.  Empty strings are possible though.
        map.put(documentIdentifiers[i],versions[i]);
      }
      i++;
    }

    // Execute the query
    IDynamicResultSet result;
    String queryText = sb.toString();
    long startTime = System.currentTimeMillis();
    try
    {
      result = connection.executeUncachedQuery(queryText,paramList,-1);
    }
    catch (LCFException e)
    {
      // If failure, record the failure.
      activities.recordActivity(new Long(startTime), ACTIVITY_EXTERNAL_QUERY, null,
        createQueryString(queryText,paramList), "ERROR", e.getMessage(), null);
      throw e;
    }
    // If success, record that too.
    activities.recordActivity(new Long(startTime), ACTIVITY_EXTERNAL_QUERY, null,
      createQueryString(queryText,paramList), "OK", null, null);

    try
    {
      while (true)
      {
        IResultRow row = result.getNextRow();
        if (row == null)
          break;
        Object o = row.getValue(JDBCConstants.idReturnColumnName);
        if (o == null)
          throw new LCFException("Bad document query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
        String id = readAsString(o);
        String version = (String)map.get(id);
        if (version != null)
        {
          // This document was marked as "not scan only", so we expect to find it.
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("JDBC: Document data result found for '"+id+"'");
          o = row.getValue(JDBCConstants.urlReturnColumnName);
          if (o != null)
          {
            // This is not right - url can apparently be a BinaryInput
            String url = readAsString(o);
            boolean validURL;
            try
            {
              // Check to be sure url is valid
              new java.net.URI(url);
              validURL = true;
            }
            catch (java.net.URISyntaxException e)
            {
              validURL = false;
            }

            if (validURL)
            {
              // Process the document itself
              Object contents = row.getValue(JDBCConstants.dataReturnColumnName);
              // Null data is allowed; we just ignore these
              if (contents != null)
              {
                // We will ingest something, so remove this id from the map in order that we know what we still
                // need to delete when all done.
                map.remove(id);
                if (contents instanceof BinaryInput)
                {
                  // An ingestion will take place for this document.
                  RepositoryDocument rd = new RepositoryDocument();

                  applyAccessTokens(rd,version,spec);
                  applyMetadata(rd,row);

                  BinaryInput bi = (BinaryInput)contents;
                  try
                  {
                    // Read the stream
                    InputStream is = bi.getStream();
                    try
                    {
                      rd.setBinary(is,bi.getLength());
                      activities.ingestDocument(id, version, url, rd);
                    }
                    finally
                    {
                      is.close();
                    }
                  }
                  catch (java.net.SocketTimeoutException e)
                  {
                    throw new LCFException("Socket timeout reading database data: "+e.getMessage(),e);
                  }
                  catch (InterruptedIOException e)
                  {
                    throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    throw new LCFException("Error reading database data: "+e.getMessage(),e);
                  }
                  finally
                  {
                    bi.discard();
                  }
                }
                else
                {
                  // Turn it into a string, and then into a stream
                  String value = contents.toString();
                  try
                  {
                    byte[] bytes = value.getBytes("utf-8");
                    RepositoryDocument rd = new RepositoryDocument();

                    applyAccessTokens(rd,version,spec);
                    applyMetadata(rd,row);

                    InputStream is = new ByteArrayInputStream(bytes);
                    try
                    {
                      rd.setBinary(is,bytes.length);
                      activities.ingestDocument(id, version, url, rd);
                    }
                    finally
                    {
                      is.close();
                    }
                  }
                  catch (InterruptedIOException e)
                  {
                    throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    throw new LCFException("Error reading database data: "+e.getMessage(),e);
                  }
                }
              }
              else
                Logging.connectors.warn("JDBC: Document '"+id+"' seems to have null data - skipping");
            }
            else
              Logging.connectors.warn("JDBC: Document '"+id+"' has an illegal url: '"+url+"' - skipping");
          }
          else
            Logging.connectors.warn("JDBC: Document '"+id+"' has a null url - skipping");
        }
      }
      // Now, go through the original id's, and see which ones are still in the map.  These
      // did not appear in the result and are presumed to be gone from the database, and thus must be deleted.
      i = 0;
      while (i < documentIdentifiers.length)
      {
        if (!scanOnly[i])
        {
          String documentIdentifier = documentIdentifiers[i];
          if (map.get(documentIdentifier) != null)
          {
            // This means we did not see it (or data for it) in the result set.  Delete it!
            activities.deleteDocument(documentIdentifier);
          }
        }
        i++;
      }

    }
    finally
    {
      result.close();
    }
  }

  /** Special column names, as far as document queries are concerned */
  protected static HashMap documentKnownColumns;
  static
  {
    documentKnownColumns = new HashMap();
    documentKnownColumns.put(JDBCConstants.idReturnColumnName,"");
    documentKnownColumns.put(JDBCConstants.urlReturnColumnName,"");
    documentKnownColumns.put(JDBCConstants.dataReturnColumnName,"");
  }
  
  /** Apply metadata to a repository document.
  *@param rd is the repository document to apply the metadata to.
  *@param row is the resultset row to use to get the metadata.  All non-special columns from this row will be considered to be metadata.
  */
  protected void applyMetadata(RepositoryDocument rd, IResultRow row)
    throws LCFException
  {
    // Cycle through the row's columns
    Iterator iter = row.getColumns();
    while (iter.hasNext())
    {
      String columnName = (String)iter.next();
      if (documentKnownColumns.get(columnName) == null)
      {
        // Consider this column to contain metadata.
        // We can only accept non-binary metadata at this time.
        Object metadata = row.getValue(columnName);
        if (metadata instanceof BinaryInput)
          throw new LCFException("Metadata column '"+columnName+"' must be convertible to a string, and cannot be binary");
        rd.addField(columnName,metadata.toString());
      }
    }
  }
  
  /** Apply access tokens to a repository document.
  *@param rd is the repository document to apply the access tokens to.
  *@param version is the version string.
  *@param spec is the document specification.
  */
  protected void applyAccessTokens(RepositoryDocument rd, String version, DocumentSpecification spec)
    throws LCFException
  {
    // Set up any acls
    String[] accessAcls = null;
    String[] denyAcls = null;

    if (version.length() == 0)
    {
      // Version is empty string, therefore acl information must be gathered from spec
      String[] specAcls = getAcls(spec);
      accessAcls = specAcls;
      if (specAcls.length != 0)
        denyAcls = new String[]{defaultAuthorityDenyToken};
      else
        denyAcls = new String[0];
    }
    else
    {
      // Unpack access tokens and the deny token too
      ArrayList acls = new ArrayList();
      StringBuffer denyAclBuffer = new StringBuffer();
      int startPos = unpackList(acls,version,0,'+');
      if (startPos < version.length() && version.charAt(startPos++) == '+')
      {
        startPos = unpack(denyAclBuffer,version,startPos,'+');
      }
      // Turn into acls and add into description
      accessAcls = new String[acls.size()];
      int j = 0;
      while (j < accessAcls.length)
      {
        accessAcls[j] = (String)acls.get(j);
        j++;
      }
      // Deny acl too
      if (denyAclBuffer.length() > 0)
      {
        denyAcls = new String[]{denyAclBuffer.toString()};
      }
    }

    if (accessAcls != null)
      rd.setACL(accessAcls);
    if (denyAcls != null)
      rd.setDenyACL(denyAcls);

  }
  
  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  public int getMaxDocumentRequest()
  {
    // This is a number that is comfortably processed by the query processor as part of an IN clause.
    return 100;
  }


  // These are protected helper methods

  /** Add starttime and endtime query variables
  */
  protected static void addVariable(VariableMap map, String varName, long variable)
  {
    ArrayList params = new ArrayList();
    params.add(new Long(variable));
    map.addVariable(varName,"?",params);
  }

  /** Add string query variables
  */
  protected static void addVariable(VariableMap map, String varName, String variable)
  {
    ArrayList params = new ArrayList();
    params.add(variable);
    map.addVariable(varName,"?",params);
  }

  /** Add string query constants
  */
  protected static void addConstant(VariableMap map, String varName, String value)
  {
    map.addVariable(varName,value,null);
  }

  /** Build an idlist variable, and add it to the specified variable map.
  */
  protected static boolean addIDList(VariableMap map, String varName, String[] documentIdentifiers, boolean[] scanOnly)
  {
    ArrayList params = new ArrayList();
    StringBuffer sb = new StringBuffer(" (");
    int i = 0;
    int k = 0;
    while (i < documentIdentifiers.length)
    {
      if (scanOnly == null || !scanOnly[i])
      {
        if (k > 0)
          sb.append(",");
        String documentIdentifier = documentIdentifiers[i];
        sb.append("?");
        params.add(documentIdentifier);
        k++;
      }
      i++;
    }
    sb.append(") ");
    map.addVariable(varName,sb.toString(),params);
    return (k > 0);
  }

  /** Given a query, and a parameter map, substitute it.
  * Each variable substitutes the string, and it also substitutes zero or more query parameters.
  */
  protected static void substituteQuery(String inputString, VariableMap inputMap, StringBuffer outputQuery, ArrayList outputParams)
    throws LCFException
  {
    // We are looking for strings that look like this: $(something)
    // Right at the moment we don't care even about quotes, so we just want to look for $(.
    int startIndex = 0;
    while (true)
    {
      int nextIndex = inputString.indexOf("$(",startIndex);
      if (nextIndex == -1)
      {
        outputQuery.append(inputString.substring(startIndex));
        break;
      }
      int endIndex = inputString.indexOf(")",nextIndex);
      if (endIndex == -1)
      {
        outputQuery.append(inputString.substring(startIndex));
        break;
      }
      String variableName = inputString.substring(nextIndex+2,endIndex);
      VariableMapItem item = inputMap.getVariable(variableName);
      if (item == null)
        throw new LCFException("No such substitution variable: $("+variableName+")");
      outputQuery.append(inputString.substring(startIndex,nextIndex));
      outputQuery.append(item.getValue());
      ArrayList inputParams = item.getParameters();
      if (inputParams != null)
      {
        int i = 0;
        while (i < inputParams.size())
        {
          Object x = inputParams.get(i++);
          outputParams.add(x);
        }
      }
      startIndex = endIndex+1;
    }
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

  /** Create an entity identifier from a querystring and a parameter list.
  */
  protected static String createQueryString(String queryText, ArrayList paramList)
  {
    StringBuffer sb = new StringBuffer(queryText);
    sb.append("; arguments = (");
    int i = 0;
    while (i < paramList.size())
    {
      if (i > 0)
        sb.append(",");
      Object parameter = paramList.get(i++);
      if (parameter instanceof String)
        sb.append(quoteSQLString((String)parameter));
      else
        sb.append(parameter.toString());
    }
    sb.append(")");
    return sb.toString();
  }

  /** Quote a sql string.
  */
  protected static String quoteSQLString(String input)
  {
    StringBuffer sb = new StringBuffer("\'");
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
      if (x == '\'')
        sb.append('\'').append(x);
      else if (x >= 0 && x < ' ')
        sb.append(' ');
      else
        sb.append(x);
    }
    sb.append("\'");
    return sb.toString();
  }

  /** Make sure we read this field as a string */
  protected static String readAsString(Object o)
    throws LCFException
  {
    if (o instanceof BinaryInput)
    {
      // Convert this input to a string, since mssql can mess us up with the wrong column types here.
      BinaryInput bi = (BinaryInput)o;
      try
      {
        InputStream is = bi.getStream();
        try
        {
          InputStreamReader reader = new InputStreamReader(is,"utf-8");
          StringBuffer sb = new StringBuffer();
          while (true)
          {
            int x = reader.read();
            if (x == -1)
              break;
            sb.append((char)x);
          }
          return sb.toString();
        }
        finally
        {
          is.close();
        }
      }
      catch (IOException e)
      {
        throw new LCFException(e.getMessage(),e);
      }
      finally
      {
        bi.doneWithStream();
      }
    }
    else
    {
      return o.toString();
    }
  }

  /** Variable map entry.
  */
  protected static class VariableMapItem
  {
    protected String value;
    protected ArrayList params;

    /** Constructor.
    */
    public VariableMapItem(String value, ArrayList params)
    {
      this.value = value;
      this.params = params;
    }

    /** Get value.
    */
    public String getValue()
    {
      return value;
    }

    /** Get parameters.
    */
    public ArrayList getParameters()
    {
      return params;
    }
  }

  /** Variable map.
  */
  protected static class VariableMap
  {
    protected Map variableMap = new HashMap();

    /** Constructor
    */
    public VariableMap()
    {
    }

    /** Add a variable map entry */
    public void addVariable(String variableName, String value, ArrayList parameters)
    {
      VariableMapItem e = new VariableMapItem(value,parameters);
      variableMap.put(variableName,e);
    }

    /** Get a variable map entry */
    public VariableMapItem getVariable(String variableName)
    {
      return (VariableMapItem)variableMap.get(variableName);
    }
  }

  /** This class represents data gleaned from a document specification, in a more usable form.
  */
  protected static class TableSpec
  {
    public String idQuery;
    public String versionQuery;
    public String dataQuery;

    public TableSpec(DocumentSpecification ds)
    {
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
          idQuery = sn.getValue();
        else if (sn.getType().equals(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
          versionQuery = sn.getValue();
        else if (sn.getType().equals(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
          dataQuery = sn.getValue();
      }

    }

  }

}


