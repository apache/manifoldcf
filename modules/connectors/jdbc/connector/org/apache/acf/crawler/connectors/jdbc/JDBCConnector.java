/* $Id: JDBCConnector.java 979199 2010-07-26 08:41:44Z kwright $ */

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
package org.apache.acf.crawler.connectors.jdbc;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import org.apache.acf.core.database.*;

import java.sql.*;
import javax.naming.*;
import javax.sql.*;

import java.io.*;
import java.util.*;

/** This interface describes an instance of a connection between a repository and ACF's
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
public class JDBCConnector extends org.apache.acf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: JDBCConnector.java 979199 2010-07-26 08:41:44Z kwright $";

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
    throws ACFException
  {
    if (connection == null)
    {
      if (jdbcProvider == null || jdbcProvider.length() == 0)
        throw new ACFException("Missing parameter '"+JDBCConstants.providerParameter+"'");
      if (host == null || host.length() == 0)
        throw new ACFException("Missing parameter '"+JDBCConstants.hostParameter+"'");

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
    throws ACFException
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
    throws ACFException
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
    throws ACFException, ServiceInterruption
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
    catch (ACFException e)
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
          throw new ACFException("Bad seed query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
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
    throws ACFException, ServiceInterruption
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
    catch (ACFException e)
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
          throw new ACFException("Bad version query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
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
    throws ACFException, ServiceInterruption
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
    catch (ACFException e)
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
          throw new ACFException("Bad document query; doesn't return $(IDCOLUMN) column.  Try using quotes around $(IDCOLUMN) variable, e.g. \"$(IDCOLUMN)\".");
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
                    throw new ACFException("Socket timeout reading database data: "+e.getMessage(),e);
                  }
                  catch (InterruptedIOException e)
                  {
                    throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    throw new ACFException("Error reading database data: "+e.getMessage(),e);
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
                    throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
                  }
                  catch (IOException e)
                  {
                    throw new ACFException("Error reading database data: "+e.getMessage(),e);
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
    tabsArray.add("Database Type");
    tabsArray.add("Server");
    tabsArray.add("Credentials");

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.databasehost.value == \"\")\n"+
"  {\n"+
"    alert(\"Please fill in a database server name\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.databasehost.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.databasename.value == \"\")\n"+
"  {\n"+
"    alert(\"Please fill in the name of the database\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.databasename.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.username.value == \"\")\n"+
"  {\n"+
"    alert(\"Please supply the database username for this connection\");\n"+
"    SelectTab(\"Credentials\");\n"+
"    editconnection.username.focus();\n"+
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
    String jdbcProvider = parameters.getParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.providerParameter);
    if (jdbcProvider == null)
      jdbcProvider = "oracle:thin:@";
    String host = parameters.getParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.hostParameter);
    if (host == null)
      host = "localhost";
    String databaseName = parameters.getParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databaseNameParameter);
    if (databaseName == null)
      databaseName = "database";
    String databaseUser = parameters.getParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databaseUserName);
    if (databaseUser == null)
      databaseUser = "";
    String databasePassword = parameters.getObfuscatedParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databasePassword);
    if (databasePassword == null)
      databasePassword = "";

    // "Database Type" tab
    if (tabName.equals("Database Type"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Database type:</nobr></td><td class=\"value\">\n"+
"      <select multiple=\"false\" name=\"databasetype\" size=\"2\">\n"+
"        <option value=\"oracle:thin:@\" "+(jdbcProvider.equals("oracle:thin:@")?"selected=\"selected\"":"")+">Oracle</option>\n"+
"        <option value=\"postgresql:\" "+(jdbcProvider.equals("postgresql:")?"selected=\"selected\"":"")+">Postgres SQL</option>\n"+
"        <option value=\"jtds:sqlserver:\" "+(jdbcProvider.equals("jtds:sqlserver:")?"selected=\"selected\"":"")+">MS SQL Server (&gt; V6.5)</option>\n"+
"        <option value=\"jtds:sybase:\" "+(jdbcProvider.equals("jtds:sybase:")?"selected=\"selected\"":"")+">Sybase (&gt;= V10)</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"databasetype\" value=\""+jdbcProvider+"\"/>\n"
      );
    }

    // "Server" tab
    if (tabName.equals("Server"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Database host and port:</nobr></td><td class=\"value\"><input type=\"text\" size=\"64\" name=\"databasehost\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(host)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Database service name or instance/database:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"databasename\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databaseName)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"databasehost\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(host)+"\"/>\n"+
"<input type=\"hidden\" name=\"databasename\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databaseName)+"\"/>\n"
      );
    }

    // "Credentials" tab
    if (tabName.equals("Credentials"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>User name:</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"username\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databaseUser)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Password:</nobr></td><td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databasePassword)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"username\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databaseUser)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(databasePassword)+"\"/>\n"
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
    String type = variableContext.getParameter("databasetype");
    if (type != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.providerParameter,type);

    String host = variableContext.getParameter("databasehost");
    if (host != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.hostParameter,host);

    String databaseName = variableContext.getParameter("databasename");
    if (databaseName != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databaseNameParameter,databaseName);

    String userName = variableContext.getParameter("username");
    if (userName != null)
      parameters.setParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databaseUserName,userName);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.databasePassword,password);
    
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
    tabsArray.add("Queries");
    tabsArray.add("Security");

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
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
"function checkSpecification()\n"+
"{\n"+
"  if (editjob.idquery.value == \"\")\n"+
"  {\n"+
"    alert(\"Enter a seeding query\");\n"+
"    editjob.idquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.idquery.value.indexOf(\"$(IDCOLUMN)\") == -1)\n"+
"  {\n"+
"    alert(\"Must return $(IDCOLUMN) in the result.\\nExample: SELECT idfield AS $(IDCOLUMN) FROM ...\");\n"+
"    editjob.idquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.versionquery.value != \"\")\n"+
"  {\n"+
"    if (editjob.versionquery.value.indexOf(\"$(IDCOLUMN)\") == -1)\n"+
"    {\n"+
"      alert(\"Must return $(IDCOLUMN) in the result.\\nExample: SELECT idfield AS $(IDCOLUMN), ...\");\n"+
"      editjob.versionquery.focus();\n"+
"      return false;\n"+
"    }\n"+
"    if (editjob.versionquery.value.indexOf(\"$(VERSIONCOLUMN)\") == -1)\n"+
"    {\n"+
"      alert(\"Must return $(VERSIONCOLUMN) in the result, containing the document version.\\nExample: SELECT versionfield AS $(VERSIONCOLUMN), ...\");\n"+
"      editjob.versionquery.focus();\n"+
"      return false;\n"+
"    }\n"+
"    if (editjob.versionquery.value.indexOf(\"$(IDLIST)\") == -1)\n"+
"    {\n"+
"      alert(\"Must use $(IDLIST) in WHERE clause.\\nExample: SELECT ... WHERE idfield IN $(IDLIST) ...\");\n"+
"      editjob.versionquery.focus();\n"+
"      return false;\n"+
"    }\n"+
"  }\n"+
"  if (editjob.dataquery.value == \"\")\n"+
"  {\n"+
"    alert(\"Enter a data query\");\n"+
"    editjob.dataquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.dataquery.value.indexOf(\"$(IDCOLUMN)\") == -1)\n"+
"  {\n"+
"    alert(\"Must return $(IDCOLUMN) in the result.\\nExample: SELECT idfield AS $(IDCOLUMN), ...\");\n"+
"    editjob.dataquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.dataquery.value.indexOf(\"$(URLCOLUMN)\") == -1)\n"+
"  {\n"+
"    alert(\"Must return $(URLCOLUMN) in the result, containing the url to use to reach the document.\\nExample: SELECT urlfield AS $(URLCOLUMN), ...\");\n"+
"    editjob.dataquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.dataquery.value.indexOf(\"$(DATACOLUMN)\") == -1)\n"+
"  {\n"+
"    alert(\"Must return $(DATACOLUMN) in the result, containing the document data.\\nExample: SELECT datafield AS $(DATACOLUMN), ...\");\n"+
"    editjob.dataquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editjob.dataquery.value.indexOf(\"$(IDLIST)\") == -1)\n"+
"  {\n"+
"    alert(\"Must use $(IDLIST) in WHERE clause.\\nExample: SELECT ... WHERE idfield IN $(IDLIST) ...\");\n"+
"    editjob.dataquery.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  return true;\n"+
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
    String idQuery = "SELECT idfield AS $(IDCOLUMN) FROM documenttable WHERE modifydatefield > $(STARTTIME) AND modifydatefield <= $(ENDTIME)";
    String versionQuery = "SELECT idfield AS $(IDCOLUMN), versionfield AS $(VERSIONCOLUMN) FROM documenttable WHERE idfield IN $(IDLIST)";
    String dataQuery = "SELECT idfield AS $(IDCOLUMN), urlfield AS $(URLCOLUMN), datafield AS $(DATACOLUMN) FROM documenttable WHERE idfield IN $(IDLIST)";

    int i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
      {
        idQuery = sn.getValue();
        if (idQuery == null)
          idQuery = "";
      }
      else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
      {
        versionQuery = sn.getValue();
        if (versionQuery == null)
          versionQuery = "";
      }
      else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
      {
        dataQuery = sn.getValue();
        if (dataQuery == null)
          dataQuery = "";
      }
    }

    // The Queries tab

    if (tabName.equals("Queries"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Seeding query:</nobr><br/><nobr>(return ids that need to be checked)</nobr></td>\n"+
"    <td class=\"value\"><textarea name=\"idquery\" cols=\"64\" rows=\"6\">"+org.apache.acf.ui.util.Encoder.bodyEscape(idQuery)+"</textarea></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Version check query:</nobr><br/><nobr>(return ids and versions for a set of documents;</nobr><br/><nobr>leave blank if no versioning capability)</nobr></td>\n"+
"    <td class=\"value\"><textarea name=\"versionquery\" cols=\"64\" rows=\"6\">"+org.apache.acf.ui.util.Encoder.bodyEscape(versionQuery)+"</textarea></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Data query:</nobr><br/><nobr>(return ids, urls, and data for a set of documents)</nobr></td>\n"+
"    <td class=\"value\"><textarea name=\"dataquery\" cols=\"64\" rows=\"6\">"+org.apache.acf.ui.util.Encoder.bodyEscape(dataQuery)+"</textarea></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"idquery\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(idQuery)+"\"/>\n"+
"<input type=\"hidden\" name=\"versionquery\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(versionQuery)+"\"/>\n"+
"<input type=\"hidden\" name=\"dataquery\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(dataQuery)+"\"/>\n"
      );
    }
	
    // Security tab
    // There is no native security, so all we care about are the tokens.
    i = 0;

    if (tabName.equals("Security"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Go through forced ACL
      i = 0;
      int k = 0;
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
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")' alt=\"Delete token #\""+Integer.toString(k)+"\"/>\n"+
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
      // Finally, go through forced ACL
      i = 0;
      int k = 0;
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
    String idQuery = variableContext.getParameter("idquery");
    String versionQuery = variableContext.getParameter("versionquery");
    String dataQuery = variableContext.getParameter("dataquery");

    SpecificationNode sn;
    if (idQuery != null)
    {
      int i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
          ds.removeChild(i);
        else
          i++;
      }
      sn = new SpecificationNode(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.idQueryNode);
      sn.setValue(idQuery);
      ds.addChild(ds.getChildCount(),sn);
    }
    if (versionQuery != null)
    {
      int i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
          ds.removeChild(i);
        else
          i++;
      }
      sn = new SpecificationNode(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode);
      sn.setValue(versionQuery);
      ds.addChild(ds.getChildCount(),sn);
    }
    if (dataQuery != null)
    {
      int i = 0;
      while (i < ds.getChildCount())
      {
        if (ds.getChild(i).getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
          ds.removeChild(i);
        else
          i++;
      }
      sn = new SpecificationNode(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode);
      sn.setValue(dataQuery);
      ds.addChild(ds.getChildCount(),sn);
    }
	
    String xc = variableContext.getParameter("tokencount");
    if (xc != null)
    {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount())
      {
        sn = ds.getChild(i);
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
    String idQuery = "";
    String versionQuery = "";
    String dataQuery = "";

    int i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
      {
        idQuery = sn.getValue();
        if (idQuery == null)
          idQuery = "";
      }
      else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
      {
        versionQuery = sn.getValue();
        if (versionQuery == null)
          versionQuery = "";
      }
      else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
      {
        dataQuery = sn.getValue();
        if (dataQuery == null)
          dataQuery = "";
      }
    }

    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Seeding query:</nobr></td>\n"+
"    <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(idQuery)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Version check query:</nobr></td>\n"+
"    <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(versionQuery)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Data query:</nobr></td>\n"+
"    <td class=\"value\">"+org.apache.acf.ui.util.Encoder.bodyEscape(dataQuery)+"</td>\n"+
"  </tr>\n"+
"\n"+
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
"  <tr><td class=\"description\"><nobr>Access tokens:</nobr></td>\n"+
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
"  <tr><td class=\"message\" colspan=\"2\"><nobr>No access tokens specified</nobr></td></tr>\n"
      );
    }

    out.print(
"</table>\n"
    );
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
    throws ACFException
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
          throw new ACFException("Metadata column '"+columnName+"' must be convertible to a string, and cannot be binary");
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
    throws ACFException
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
    throws ACFException
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
        throw new ACFException("No such substitution variable: $("+variableName+")");
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
    throws ACFException
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
        throw new ACFException(e.getMessage(),e);
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
        if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
        {
          idQuery = sn.getValue();
          if (idQuery == null)
            idQuery = "";
        }
        else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
        {
          versionQuery = sn.getValue();
          if (versionQuery == null)
            versionQuery = "";
        }
        else if (sn.getType().equals(org.apache.acf.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
        {
          dataQuery = sn.getValue();
          if (dataQuery == null)
            dataQuery = "";
        }
      }

    }

  }

}


