/* $Id: IncrementalIngester.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.incrementalingest;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.system.ManifoldCF;
import java.util.*;
import java.io.*;

/** Incremental ingestion API implementation.
* This class is responsible for keeping track of what has been sent where, and also the corresponding version of
* each document so indexed.  The space over which this takes place is defined by the individual output connection - that is, the output connection
* seems to "remember" what documents were handed to it.
*
* A secondary purpose of this module is to provide a mapping between the key by which a document is described internally (by an
* identifier hash, plus the name of an identifier space), and the way the document is identified in the output space (by the name of an
* output connection, plus a URI which is considered local to that output connection space).
*
* <br><br>
* <b>ingeststatus</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>id</td><td>BIGINT</td><td>Primary Key</td></tr>
* <tr><td>connectionname</td><td>VARCHAR(32)</td><td>Reference:outputconnections.connectionname</td></tr>
* <tr><td>dockey</td><td>VARCHAR(73)</td><td></td></tr>
* <tr><td>docuri</td><td>LONGTEXT</td><td></td></tr>
* <tr><td>urihash</td><td>VARCHAR(40)</td><td></td></tr>
* <tr><td>lastversion</td><td>LONGTEXT</td><td></td></tr>
* <tr><td>lastoutputversion</td><td>LONGTEXT</td><td></td></tr>
* <tr><td>forcedparams</td><td>LONGTEXT</td><td></td></tr>
* <tr><td>changecount</td><td>BIGINT</td><td></td></tr>
* <tr><td>firstingest</td><td>BIGINT</td><td></td></tr>
* <tr><td>lastingest</td><td>BIGINT</td><td></td></tr>
* <tr><td>authorityname</td><td>VARCHAR(32)</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class IncrementalIngester extends org.apache.manifoldcf.core.database.BaseTable implements IIncrementalIngester
{
  public static final String _rcsid = "@(#)$Id: IncrementalIngester.java 988245 2010-08-23 18:39:35Z kwright $";

  // Fields
  protected final static String idField = "id";
  protected final static String outputConnNameField = "connectionname";
  protected final static String docKeyField = "dockey";
  protected final static String docURIField = "docuri";
  protected final static String uriHashField = "urihash";
  protected final static String lastVersionField = "lastversion";
  protected final static String lastOutputVersionField = "lastoutputversion";
  protected final static String forcedParamsField = "forcedparams";
  protected final static String changeCountField = "changecount";
  protected final static String firstIngestField = "firstingest";
  protected final static String lastIngestField = "lastingest";
  protected final static String authorityNameField = "authorityname";

  // Thread context.
  protected IThreadContext threadContext;
  // Lock manager.
  protected ILockManager lockManager;
  // Output connection manager
  protected IOutputConnectionManager connectionManager;
  // Output connector pool manager
  protected IOutputConnectorPool outputConnectorPool;
  
  /** Constructor.
  */
  public IncrementalIngester(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"ingeststatus");
    this.threadContext = threadContext;
    lockManager = LockManagerFactory.make(threadContext);
    connectionManager = OutputConnectionManagerFactory.make(threadContext);
    outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
  }

  /** Install the incremental ingestion manager.
  */
  @Override
  public void install()
    throws ManifoldCFException
  {
    String outputConnectionTableName = connectionManager.getTableName();
    String outputConnectionNameField = connectionManager.getConnectionNameColumn();

    // We always include an outer loop, because some upgrade conditions require retries.
    while (true)
    {
      // Postgresql has a limitation on the number of characters that can be indexed in a column.  So we use hashes instead.
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(idField,new ColumnDescription("BIGINT",true,false,null,null,false));
        map.put(outputConnNameField,new ColumnDescription("VARCHAR(32)",false,false,outputConnectionTableName,outputConnectionNameField,false));
        map.put(docKeyField,new ColumnDescription("VARCHAR(73)",false,false,null,null,false));
        // The document URI field, if null, indicates that the document was not actually ingested!
        // This happens when a connector wishes to keep track of a version string, but not actually ingest the doc.
        map.put(docURIField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(uriHashField,new ColumnDescription("VARCHAR(40)",false,true,null,null,false));
        map.put(lastVersionField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(lastOutputVersionField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(forcedParamsField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(changeCountField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(firstIngestField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(lastIngestField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(authorityNameField,new ColumnDescription("VARCHAR(32)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Schema upgrade from 1.1 to 1.2
        ColumnDescription cd = (ColumnDescription)existing.get(forcedParamsField);
        if (cd == null)
        {
          Map<String,ColumnDescription> addMap = new HashMap<String,ColumnDescription>();
          addMap.put(forcedParamsField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
          performAlter(addMap,null,null,null);
        }
      }

      // Now, do indexes
      IndexDescription keyIndex = new IndexDescription(true,new String[]{docKeyField,outputConnNameField});
      IndexDescription uriHashIndex = new IndexDescription(false,new String[]{uriHashField,outputConnNameField});
      IndexDescription outputConnIndex = new IndexDescription(false,new String[]{outputConnNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (keyIndex != null && id.equals(keyIndex))
          keyIndex = null;
        else if (uriHashIndex != null && id.equals(uriHashIndex))
          uriHashIndex = null;
        else if (outputConnIndex != null && id.equals(outputConnIndex))
          outputConnIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (uriHashIndex != null)
        performAddIndex(null,uriHashIndex);

      if (keyIndex != null)
        performAddIndex(null,keyIndex);

      if (outputConnIndex != null)
        performAddIndex(null,outputConnIndex);
      
      // All done; break out of loop
      break;
    }

  }

  /** Uninstall the incremental ingestion manager.
  */
  @Override
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Flush all knowledge of what was ingested before.
  */
  @Override
  public void clearAll()
    throws ManifoldCFException
  {
    performDelete("",null,null);
  }

  /** Check if a mime type is indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param mimeType is the mime type to check.
  *@return true if the mimeType is indexable.
  */
  @Override
  public boolean checkMimeTypeIndexable(String outputConnectionName, String outputDescription, String mimeType)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.checkMimeTypeIndexable(outputDescription,mimeType);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Check if a file is indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param localFile is the local file to check.
  *@return true if the local file is indexable.
  */
  @Override
  public boolean checkDocumentIndexable(String outputConnectionName, String outputDescription, File localFile)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.checkDocumentIndexable(outputDescription,localFile);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are too long to be indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param length is the length of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkLengthIndexable(String outputConnectionName, String outputDescription, long length)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.checkLengthIndexable(outputDescription,length);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that not indexable.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param outputDescription is the output description string.
  *@param url is the url of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkURLIndexable(String outputConnectionName, String outputDescription, String url)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.checkURLIndexable(outputDescription,url);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Get an output version string for a document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param spec is the output specification.
  *@return the description string.
  */
  @Override
  public String getOutputDescription(String outputConnectionName, OutputSpecification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.getOutputDescription(spec);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }

  }

  /** Record a document version, but don't ingest it.
  * The purpose of this method is to keep track of the frequency at which ingestion "attempts" take place.
  * ServiceInterruption is thrown if this action must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param recordTime is the time at which the recording took place, in milliseconds since epoch.
  *@param activities is the object used in case a document needs to be removed from the output index as the result of this operation.
  */
  @Override
  public void documentRecord(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion,
    long recordTime, IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);

    String docKey = makeKey(identifierClass,identifierHash);

    if (Logging.ingest.isDebugEnabled())
    {
      Logging.ingest.debug("Recording document '"+docKey+"' for output connection '"+outputConnectionName+"'");
    }

    performIngestion(connection,docKey,documentVersion,null,null,null,null,recordTime,null,activities);
  }

  /** Ingest a document.
  * This ingests the document, and notes it.  If this is a repeat ingestion of the document, this
  * method also REMOVES ALL OLD METADATA.  When complete, the index will contain only the metadata
  * described by the RepositoryDocument object passed to this method.
  * ServiceInterruption is thrown if the document ingestion must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param outputVersion is the output version string constructed from the output specification by the output connector.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the URI of the document, which will be used as the key of the document in the index.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  *@return true if the ingest was ok, false if the ingest is illegal (and should not be repeated).
  */
  @Override
  public boolean documentIngest(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion,
    String outputVersion,
    String authorityName,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    return documentIngest(outputConnectionName,
      identifierClass,
      identifierHash,
      documentVersion,
      outputVersion,
      null,
      authorityName,
      data,
      ingestTime,
      documentURI,
      activities);
  }
  
  /** Ingest a document.
  * This ingests the document, and notes it.  If this is a repeat ingestion of the document, this
  * method also REMOVES ALL OLD METADATA.  When complete, the index will contain only the metadata
  * described by the RepositoryDocument object passed to this method.
  * ServiceInterruption is thrown if the document ingestion must be rescheduled.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param documentVersion is the document version.
  *@param parameterVersion is the forced parameter version.
  *@param outputVersion is the output version string constructed from the output specification by the output connector.
  *@param authorityName is the name of the authority associated with the document, if any.
  *@param data is the document data.  The data is closed after ingestion is complete.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the URI of the document, which will be used as the key of the document in the index.
  *@param activities is an object providing a set of methods that the implementer can use to perform the operation.
  *@return true if the ingest was ok, false if the ingest is illegal (and should not be repeated).
  */
  @Override
  public boolean documentIngest(String outputConnectionName,
    String identifierClass, String identifierHash,
    String documentVersion,
    String outputVersion,
    String parameterVersion,
    String authorityName,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);

    String docKey = makeKey(identifierClass,identifierHash);

    if (Logging.ingest.isDebugEnabled())
    {
      Logging.ingest.debug("Ingesting document '"+docKey+"' into output connection '"+outputConnectionName+"'");
    }

    return performIngestion(connection,docKey,documentVersion,outputVersion,parameterVersion,authorityName,
      data,ingestTime,documentURI,activities);
  }

  
  /** Do the actual ingestion, or just record it if there's nothing to ingest. */
  protected boolean performIngestion(IOutputConnection connection,
    String docKey, String documentVersion, String outputVersion, String parameterVersion,
    String authorityNameString,
    RepositoryDocument data,
    long ingestTime, String documentURI,
    IOutputActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // No transactions; not safe because post may take too much time

    // First, calculate a document uri hash value
    String documentURIHash = null;
    if (documentURI != null)
      documentURIHash = ManifoldCF.hash(documentURI);

    String oldURI = null;
    String oldURIHash = null;
    String oldOutputVersion = null;

    
    while (true)
    {
      long sleepAmt = 0L;
      try
      {
        // See what uri was used before for this doc, if any
        ArrayList list = new ArrayList();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(docKeyField,docKey),
          new UnitaryClause(outputConnNameField,connection.getName())});
          
        IResultSet set = performQuery("SELECT "+docURIField+","+uriHashField+","+lastOutputVersionField+" FROM "+getTableName()+
          " WHERE "+query,list,null,null);

        if (set.getRowCount() > 0)
        {
          IResultRow row = set.getRow(0);
          oldURI = (String)row.getValue(docURIField);
          oldURIHash = (String)row.getValue(uriHashField);
          oldOutputVersion = (String)row.getValue(lastOutputVersionField);
        }
        
        break;
      }
      catch (ManifoldCFException e)
      {
        // Look for deadlock and retry if so
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted select looking for status: "+e.getMessage());
          sleepAmt = getSleepAmt();
          continue;
        }
        throw e;
      }
      finally
      {
        sleepFor(sleepAmt);
      }
    }

    // If uri hashes collide, then we must be sure to eliminate only the *correct* records from the table, or we will leave
    // dangling documents around.  So, all uri searches and comparisons MUST compare the actual uri as well.

    // But, since we need to insure that any given URI is only worked on by one thread at a time, use critical sections
    // to block the rare case that multiple threads try to work on the same URI.
    int uriCount = 0;
    if (documentURI != null)
      uriCount++;
    if (oldURI != null && (documentURI == null || !documentURI.equals(oldURI)))
      uriCount++;
    String[] lockArray = new String[uriCount];
    uriCount = 0;
    if (documentURI != null)
      lockArray[uriCount++] = connection.getName()+":"+documentURI;
    if (oldURI != null && (documentURI == null || !documentURI.equals(oldURI)))
      lockArray[uriCount++] = connection.getName()+":"+oldURI;

    lockManager.enterCriticalSections(null,null,lockArray);
    try
    {

      ArrayList list = new ArrayList();
      
      if (oldURI != null && (documentURI == null || !oldURI.equals(documentURI)))
      {
        // Delete all records from the database that match the old URI, except for THIS record.
        list.clear();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(uriHashField,"=",oldURIHash),
          new UnitaryClause(outputConnNameField,"=",connection.getName())});
        list.add(docKey);
        performDelete("WHERE "+query+" AND "+docKeyField+"!=?",list,null);
        removeDocument(connection,oldURI,oldOutputVersion,activities);
      }

      if (documentURI != null)
      {
        // Get rid of all records that match the NEW uri, except for this record.
        list.clear();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(uriHashField,"=",documentURIHash),
          new UnitaryClause(outputConnNameField,"=",connection.getName())});
        list.add(docKey);
        performDelete("WHERE "+query+" AND "+ docKeyField+"!=?",list,null);
      }

      // Now, we know we are ready for the ingest.
      if (documentURI != null)
      {
        // Here are the cases:
        // 1) There was a service interruption before the upload started.
        // (In that case, we don't need to log anything, just reschedule).
        // 2) There was a service interruption after the document was transmitted.
        // (In that case, we should presume that the document was ingested, but
        //  reschedule another import anyway.)
        // 3) Everything went OK
        // (need to log the ingestion.)
        // 4) Everything went OK, but we were told we have an illegal document.
        // (We note the ingestion because if we don't we will be forced to repeat ourselves.
        //  In theory, document doesn't need to be deleted, but there is no way to signal
        //  that at the moment.)

        // Note an ingestion before we actually try it.
        // This is a marker that says "something is there"; it has an empty version, which indicates
        // that we don't know anything about it.  That means it will be reingested when the
        // next version comes along, and will be deleted if called for also.
        noteDocumentIngest(connection.getName(),docKey,null,null,null,null,ingestTime,documentURI,documentURIHash);
        int result = addOrReplaceDocument(connection,documentURI,outputVersion,data,authorityNameString,activities);
        noteDocumentIngest(connection.getName(),docKey,documentVersion,outputVersion,parameterVersion,authorityNameString,ingestTime,documentURI,documentURIHash);
        return result == IOutputConnector.DOCUMENTSTATUS_ACCEPTED;
      }

      // If we get here, it means we are noting that the document was examined, but that no change was required.  This is signaled
      // to noteDocumentIngest by having the null documentURI.
      noteDocumentIngest(connection.getName(),docKey,documentVersion,outputVersion,parameterVersion,authorityNameString,ingestTime,null,null);
      return true;
    }
    finally
    {
      lockManager.leaveCriticalSections(null,null,lockArray);
    }
  }

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes are the set of document identifier hashes.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  @Override
  public void documentCheckMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes,
    long checkTime)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      int maxClauses;
      
      HashMap docIDValues = new HashMap();
      int j = 0;
      while (j < identifierHashes.length)
      {
        String docDBString = makeKey(identifierClasses[j],identifierHashes[j]);
        docIDValues.put(docDBString,docDBString);
        j++;
      }

      // Now, perform n queries, each of them no larger the maxInClause in length.
      // Create a list of row id's from this.
      HashMap rowIDSet = new HashMap();
      Iterator iter = docIDValues.keySet().iterator();
      j = 0;
      ArrayList list = new ArrayList();
      maxClauses = maxClausesRowIdsForDocIds(outputConnectionName);
      while (iter.hasNext())
      {
        if (j == maxClauses)
        {
          findRowIdsForDocIds(outputConnectionName,rowIDSet,list);
          list.clear();
          j = 0;
        }
        list.add(iter.next());
        j++;
      }

      if (j > 0)
        findRowIdsForDocIds(outputConnectionName,rowIDSet,list);

      // Now, break row id's into chunks too; submit one chunk at a time
      j = 0;
      list.clear();
      iter = rowIDSet.keySet().iterator();
      maxClauses = maxClausesUpdateRowIds();
      while (iter.hasNext())
      {
        if (j == maxClauses)
        {
          updateRowIds(list,checkTime);
          list.clear();
          j = 0;
        }
        list.add(iter.next());
        j++;
      }

      if (j > 0)
        updateRowIds(list,checkTime);
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Note the fact that we checked a document (and found that it did not need to be ingested, because the
  * versions agreed).
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hashed document identifier.
  *@param checkTime is the time at which the check took place, in milliseconds since epoch.
  */
  @Override
  public void documentCheck(String outputConnectionName,
    String identifierClass, String identifierHash,
    long checkTime)
    throws ManifoldCFException
  {
    documentCheckMultiple(outputConnectionName,new String[]{identifierClass},new String[]{identifierHash},checkTime);
  }

  /** Calculate the number of clauses.
  */
  protected int maxClausesUpdateRowIds()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
  
  /** Update a chunk of row ids.
  */
  protected void updateRowIds(ArrayList list, long checkTime)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(idField,list)});
      
    HashMap map = new HashMap();
    map.put(lastIngestField,new Long(checkTime));
    performUpdate(map,"WHERE "+query,newList,null);
  }

  /** Delete multiple documents from the search engine index.
  *@param outputConnectionNames are the names of the output connections associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  @Override
  public void documentDeleteMultiple(String[] outputConnectionNames,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Segregate request by connection names
    HashMap keyMap = new HashMap();
    int i = 0;
    while (i < outputConnectionNames.length)
    {
      String outputConnectionName = outputConnectionNames[i];
      ArrayList list = (ArrayList)keyMap.get(outputConnectionName);
      if (list == null)
      {
        list = new ArrayList();
        keyMap.put(outputConnectionName,list);
      }
      list.add(new Integer(i));
      i++;
    }

    // Create the return array.
    Iterator iter = keyMap.keySet().iterator();
    while (iter.hasNext())
    {
      String outputConnectionName = (String)iter.next();
      ArrayList list = (ArrayList)keyMap.get(outputConnectionName);
      String[] localIdentifierClasses = new String[list.size()];
      String[] localIdentifierHashes = new String[list.size()];
      i = 0;
      while (i < localIdentifierClasses.length)
      {
        int index = ((Integer)list.get(i)).intValue();
        localIdentifierClasses[i] = identifierClasses[index];
        localIdentifierHashes[i] = identifierHashes[index];
        i++;
      }
      documentDeleteMultiple(outputConnectionName,localIdentifierClasses,localIdentifierHashes,activities);
    }
  }

  /** Delete multiple documents from the search engine index.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is tha array of document identifier hashes if the documents.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  @Override
  public void documentDeleteMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnection connection = connectionManager.load(outputConnectionName);

    if (Logging.ingest.isDebugEnabled())
    {
      int i = 0;
      while (i < identifierHashes.length)
      {
        Logging.ingest.debug("Request to delete document '"+makeKey(identifierClasses[i],identifierHashes[i])+"' from output connection '"+outputConnectionName+"'");
        i++;
      }
    }

    // No transactions.  Time for the operation may exceed transaction timeout.

    // Obtain the current URIs of all of these.
    DeleteInfo[] uris = getDocumentURIMultiple(outputConnectionName,identifierClasses,identifierHashes);

    // Grab critical section locks so that we can't attempt to ingest at the same time we are deleting.
    // (This guarantees that when this operation is complete the database reflects reality.)
    int validURIcount = 0;
    int i = 0;
    while (i < uris.length)
    {
      if (uris[i] != null && uris[i].getURI() != null)
        validURIcount++;
      i++;
    }
    String[] lockArray = new String[validURIcount];
    String[] validURIArray = new String[validURIcount];
    validURIcount = 0;
    i = 0;
    while (i < uris.length)
    {
      if (uris[i] != null && uris[i].getURI() != null)
      {
        validURIArray[validURIcount] = uris[i].getURI();
        lockArray[validURIcount] = outputConnectionName+":"+validURIArray[validURIcount];
        validURIcount++;
      }
      i++;
    }

    lockManager.enterCriticalSections(null,null,lockArray);
    try
    {
      // Fetch the document URIs for the listed documents
      int j = 0;
      while (j < uris.length)
      {
        if (uris[j] != null && uris[j].getURI() != null)
          removeDocument(connection,uris[j].getURI(),uris[j].getOutputVersion(),activities);
        j++;
      }

      // Now, get rid of all rows that match the given uris.
      // Do the queries together, then the deletes
      beginTransaction();
      try
      {
        // The basic process is this:
        // 1) Come up with a set of urihash values
        // 2) Find the matching, corresponding id values
        // 3) Delete the rows corresponding to the id values, in sequence

        // Process (1 & 2) has to be broken down into chunks that contain the maximum
        // number of doc hash values each.  We need to avoid repeating doc hash values,
        // so the first step is to come up with ALL the doc hash values before looping
        // over them.

        int maxClauses;
        
        // Find all the documents that match this set of URIs
        HashMap docURIHashValues = new HashMap();
        HashMap docURIValues = new HashMap();
        j = 0;
        while (j < validURIArray.length)
        {
          String docDBString = validURIArray[j++];
          String docDBHashString = ManifoldCF.hash(docDBString);
          docURIValues.put(docDBString,docDBString);
          docURIHashValues.put(docDBHashString,docDBHashString);
        }

        // Now, perform n queries, each of them no larger the maxInClause in length.
        // Create a list of row id's from this.
        HashMap rowIDSet = new HashMap();
        Iterator iter = docURIHashValues.keySet().iterator();
        j = 0;
        ArrayList hashList = new ArrayList();
        maxClauses = maxClausesRowIdsForURIs(outputConnectionName);
        while (iter.hasNext())
        {
          if (j == maxClauses)
          {
            findRowIdsForURIs(outputConnectionName,rowIDSet,docURIValues,hashList);
            hashList.clear();
            j = 0;
          }
          hashList.add(iter.next());
          j++;
        }

        if (j > 0)
          findRowIdsForURIs(outputConnectionName,rowIDSet,docURIValues,hashList);

        // Next, go through the list of row IDs, and delete them in chunks
        j = 0;
        ArrayList list = new ArrayList();
        iter = rowIDSet.keySet().iterator();
        maxClauses = maxClausesDeleteRowIds();
        while (iter.hasNext())
        {
          if (j == maxClauses)
          {
            deleteRowIds(list);
            list.clear();
            j = 0;
          }
          list.add(iter.next());
          j++;
        }

        if (j > 0)
          deleteRowIds(list);

        // Now, find the set of documents that remain that match the document identifiers.
        HashMap docIdValues = new HashMap();
        j = 0;
        while (j < identifierHashes.length)
        {
          String docDBString = makeKey(identifierClasses[j],identifierHashes[j]);
          docIdValues.put(docDBString,docDBString);
          j++;
        }

        // Now, perform n queries, each of them no larger the maxInClause in length.
        // Create a list of row id's from this.
        rowIDSet.clear();
        iter = docIdValues.keySet().iterator();
        j = 0;
        list.clear();
        maxClauses = maxClausesRowIdsForDocIds(outputConnectionName);
        while (iter.hasNext())
        {
          if (j == maxClauses)
          {
            findRowIdsForDocIds(outputConnectionName,rowIDSet,list);
            list.clear();
            j = 0;
          }
          list.add(iter.next());
          j++;
        }

        if (j > 0)
          findRowIdsForDocIds(outputConnectionName,rowIDSet,list);

        // Next, go through the list of row IDs, and delete them in chunks
        j = 0;
        list.clear();
        iter = rowIDSet.keySet().iterator();
        maxClauses = maxClausesDeleteRowIds();
        while (iter.hasNext())
        {
          if (j == maxClauses)
          {
            deleteRowIds(list);
            list.clear();
            j = 0;
          }
          list.add(iter.next());
          j++;
        }

        if (j > 0)
          deleteRowIds(list);

      }
      catch (ManifoldCFException e)
      {
        signalRollback();
        throw e;
      }
      catch (Error e)
      {
        signalRollback();
        throw e;
      }
      finally
      {
        endTransaction();
      }
    }
    finally
    {
      lockManager.leaveCriticalSections(null,null,lockArray);
    }
  }

  /** Calculate the clauses.
  */
  protected int maxClausesRowIdsForURIs(String outputConnectionName)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
  }
  
  /** Given values and parameters corresponding to a set of hash values, add corresponding
  * table row id's to the output map.
  */
  protected void findRowIdsForURIs(String outputConnectionName, HashMap rowIDSet, HashMap uris, ArrayList hashParamValues)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(uriHashField,hashParamValues),
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    IResultSet set = performQuery("SELECT "+idField+","+docURIField+" FROM "+
      getTableName()+" WHERE "+query,list,null,null);
      
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docURI = (String)row.getValue(docURIField);
      if (docURI != null && docURI.length() > 0)
      {
        if (uris.get(docURI) != null)
        {
          Long rowID = (Long)row.getValue(idField);
          rowIDSet.put(rowID,rowID);
        }
      }
    }
  }

  /** Calculate the maximum number of doc ids we should use.
  */
  protected int maxClausesRowIdsForDocIds(String outputConnectionName)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
  }
  
  /** Given values and parameters corresponding to a set of hash values, add corresponding
  * table row id's to the output map.
  */
  protected void findRowIdsForDocIds(String outputConnectionName, HashMap rowIDSet, ArrayList paramValues)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(docKeyField,paramValues),
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    IResultSet set = performQuery("SELECT "+idField+" FROM "+
      getTableName()+" WHERE "+query,list,null,null);
      
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long rowID = (Long)row.getValue(idField);
      rowIDSet.put(rowID,rowID);
    }
  }

  /** Calculate the maximum number of clauses.
  */
  protected int maxClausesDeleteRowIds()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Delete a chunk of row ids.
  */
  protected void deleteRowIds(ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(idField,list)});
    performDelete("WHERE "+query,newList,null);
  }

  /** Delete a document from the search engine index.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@param activities is the object to use to log the details of the ingestion attempt.  May be null.
  */
  @Override
  public void documentDelete(String outputConnectionName,
    String identifierClass, String identifierHash,
    IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    documentDeleteMultiple(outputConnectionName,new String[]{identifierClass},new String[]{identifierHash},activities);
  }

  /** Find out what URIs a SET of document URIs are currently ingested.
  *@param identifierHashes is the array of document id's to check.
  *@return the array of current document uri's.  Null returned for identifiers
  * that don't exist in the index.
  */
  protected DeleteInfo[] getDocumentURIMultiple(String outputConnectionName, String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException
  {
    DeleteInfo[] rval = new DeleteInfo[identifierHashes.length];
    HashMap map = new HashMap();
    int i = 0;
    while (i < identifierHashes.length)
    {
      map.put(makeKey(identifierClasses[i],identifierHashes[i]),new Integer(i));
      rval[i] = null;
      i++;
    }

    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      int maxCount = maxClauseDocumentURIChunk(outputConnectionName);
      int j = 0;
      Iterator iter = map.keySet().iterator();
      while (iter.hasNext())
      {
        if (j == maxCount)
        {
          getDocumentURIChunk(rval,map,outputConnectionName,list);
          j = 0;
          list.clear();
        }
        list.add(iter.next());
        j++;
      }
      if (j > 0)
        getDocumentURIChunk(rval,map,outputConnectionName,list);
      return rval;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Look up ingestion data for a SET of documents.
  *@param outputConnectionNames are the names of the output connections associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  *@return the array of document data.  Null will come back for any identifier that doesn't
  * exist in the index.
  */
  @Override
  public DocumentIngestStatus[] getDocumentIngestDataMultiple(String[] outputConnectionNames,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException
  {
    // Segregate request by connection names
    HashMap keyMap = new HashMap();
    int i = 0;
    while (i < outputConnectionNames.length)
    {
      String outputConnectionName = outputConnectionNames[i];
      ArrayList list = (ArrayList)keyMap.get(outputConnectionName);
      if (list == null)
      {
        list = new ArrayList();
        keyMap.put(outputConnectionName,list);
      }
      list.add(new Integer(i));
      i++;
    }

    // Create the return array.
    DocumentIngestStatus[] rval = new DocumentIngestStatus[outputConnectionNames.length];
    Iterator iter = keyMap.keySet().iterator();
    while (iter.hasNext())
    {
      String outputConnectionName = (String)iter.next();
      ArrayList list = (ArrayList)keyMap.get(outputConnectionName);
      String[] localIdentifierClasses = new String[list.size()];
      String[] localIdentifierHashes = new String[list.size()];
      i = 0;
      while (i < localIdentifierClasses.length)
      {
        int index = ((Integer)list.get(i)).intValue();
        localIdentifierClasses[i] = identifierClasses[index];
        localIdentifierHashes[i] = identifierHashes[index];
        i++;
      }
      DocumentIngestStatus[] localRval = getDocumentIngestDataMultiple(outputConnectionName,localIdentifierClasses,localIdentifierHashes);
      i = 0;
      while (i < localRval.length)
      {
        int index = ((Integer)list.get(i)).intValue();
        rval[index] = localRval[i];
        i++;
      }
    }
    return rval;
  }

  /** Look up ingestion data for a SET of documents.
  *@param outputConnectionName is the names of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the array of document identifier hashes to look up.
  *@return the array of document data.  Null will come back for any identifier that doesn't
  * exist in the index.
  */
  @Override
  public DocumentIngestStatus[] getDocumentIngestDataMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException
  {
    // Build the return array
    DocumentIngestStatus[] rval = new DocumentIngestStatus[identifierHashes.length];

    // Build a map, so we can convert an identifier into an array index.
    HashMap indexMap = new HashMap();
    int i = 0;
    while (i < identifierHashes.length)
    {
      indexMap.put(makeKey(identifierClasses[i],identifierHashes[i]),new Integer(i));
      rval[i] = null;
      i++;
    }

    beginTransaction();
    try
    {
      ArrayList list = new ArrayList();
      int maxCount = maxClauseDocumentIngestDataChunk(outputConnectionName);
      int j = 0;
      Iterator iter = indexMap.keySet().iterator();
      while (iter.hasNext())
      {
        if (j == maxCount)
        {
          getDocumentIngestDataChunk(rval,indexMap,outputConnectionName,list);
          j = 0;
          list.clear();
        }
        list.add(iter.next());
        j++;
      }
      if (j > 0)
        getDocumentIngestDataChunk(rval,indexMap,outputConnectionName,list);
      return rval;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Look up ingestion data for a documents.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@return the current document's ingestion data, or null if the document is not currently ingested.
  */
  @Override
  public DocumentIngestStatus getDocumentIngestData(String outputConnectionName,
    String identifierClass, String identifierHash)
    throws ManifoldCFException
  {
    return getDocumentIngestDataMultiple(outputConnectionName,new String[]{identifierClass},new String[]{identifierHash})[0];
  }

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClass is the name of the space in which the identifier hash should be interpreted.
  *@param identifierHash is the hash of the id of the document.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  @Override
  public long getDocumentUpdateInterval(String outputConnectionName,
    String identifierClass, String identifierHash)
    throws ManifoldCFException
  {
    return getDocumentUpdateIntervalMultiple(outputConnectionName,new String[]{identifierClass},new String[]{identifierHash})[0];
  }

  /** Calculate the average time interval between changes for a document.
  * This is based on the data gathered for the document.
  *@param outputConnectionName is the name of the output connection associated with this action.
  *@param identifierClasses are the names of the spaces in which the identifier hashes should be interpreted.
  *@param identifierHashes is the hashes of the ids of the documents.
  *@return the number of milliseconds between changes, or 0 if this cannot be calculated.
  */
  @Override
  public long[] getDocumentUpdateIntervalMultiple(String outputConnectionName,
    String[] identifierClasses, String[] identifierHashes)
    throws ManifoldCFException
  {
    // Do these all at once!!
    // First, create a return array
    long[] rval = new long[identifierHashes.length];
    // Also create a map from identifier to return index.
    HashMap returnMap = new HashMap();
    // Finally, need the set of hash codes
    HashMap idCodes = new HashMap();
    int j = 0;
    while (j < identifierHashes.length)
    {
      String key = makeKey(identifierClasses[j],identifierHashes[j]);
      rval[j] = 0L;
      returnMap.put(key,new Integer(j));
      idCodes.put(key,key);
      j++;
    }

    // Get the chunk size
    int maxClause = maxClauseGetIntervals(outputConnectionName);

    // Loop through the hash codes
    Iterator iter = idCodes.keySet().iterator();
    ArrayList list = new ArrayList();
    j = 0;
    while (iter.hasNext())
    {
      if (j == maxClause)
      {
        getIntervals(rval,outputConnectionName,list,returnMap);
        list.clear();
        j = 0;
      }

      list.add(iter.next());
      j++;
    }

    if (j > 0)
      getIntervals(rval,outputConnectionName,list,returnMap);

    return rval;
  }

  /** Calculate the number of clauses.
  */
  protected int maxClauseGetIntervals(String outputConnectionName)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
    }
    
  /** Query for and calculate the interval for a bunch of hashcodes.
  *@param rval is the array to stuff calculated return values into.
  *@param list is the list of parameters.
  *@param queryPart is the part of the query pertaining to the list of hashcodes
  *@param returnMap is a mapping from document id to rval index.
  */
  protected void getIntervals(long[] rval, String outputConnectionName, ArrayList list, HashMap returnMap)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(docKeyField,list),
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    IResultSet set = performQuery("SELECT "+docKeyField+","+changeCountField+","+firstIngestField+","+lastIngestField+
      " FROM "+getTableName()+" WHERE "+query,newList,null,null);

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docHash = (String)row.getValue(docKeyField);
      Integer index = (Integer)returnMap.get(docHash);
      if (index != null)
      {
        // Calculate the return value
        long changeCount = ((Long)row.getValue(changeCountField)).longValue();
        long firstIngest = ((Long)row.getValue(firstIngestField)).longValue();
        long lastIngest = ((Long)row.getValue(lastIngestField)).longValue();
        rval[index.intValue()] = (long)(((double)(lastIngest-firstIngest))/(double)changeCount);
      }
    }
  }

  /** Reset all documents belonging to a specific output connection, because we've got information that
  * that system has been reconfigured.  This will force all such documents to be reindexed the next time
  * they are checked.
  *@param outputConnectionName is the name of the output connection associated with this action.
  */
  @Override
  public void resetOutputConnection(String outputConnectionName)
    throws ManifoldCFException
  {
    // We're not going to blow away the records, but we are going to set their versions to mean, "reindex required"
    HashMap map = new HashMap();
    map.put(lastVersionField,null);
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Remove all knowledge of an output index from the system.  This is appropriate
  * when the output index no longer exists and you wish to delete the associated job.
  *@param outputConnectionName is the name of the output connection associated with this action.
  */
  @Override
  public void removeOutputConnection(String outputConnectionName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    performDelete("WHERE "+query,list,null);
  }
  
  /** Note the ingestion of a document, or the "update" of a document.
  *@param outputConnectionName is the name of the output connection.
  *@param docKey is the key string describing the document.
  *@param documentVersion is a string describing the new version of the document.
  *@param outputVersion is the version string calculated for the output connection.
  *@param authorityNameString is the name of the relevant authority connection.
  *@param packedForcedParameters is the string we use to determine differences in packed parameters.
  *@param ingestTime is the time at which the ingestion took place, in milliseconds since epoch.
  *@param documentURI is the uri the document can be accessed at, or null (which signals that we are to record the version, but no
  * ingestion took place).
  *@param documentURIHash is the hash of the document uri.
  */
  protected void noteDocumentIngest(String outputConnectionName,
    String docKey, String documentVersion,
    String outputVersion, String packedForcedParameters,
    String authorityNameString,
    long ingestTime, String documentURI, String documentURIHash)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    while (true)
    {
      // The table can have at most one row per URI, for non-null URIs.  It can also have at most one row per document identifier.
      // However, for null URI's, multiple rows are allowed.  Null URIs have a special meaning, which is that
      // the document was not actually ingested.

      // To make sure the constraints are enforced, we cannot simply look for the row and insert one if not found.  This is because
      // postgresql does not cause a lock to be created on rows that don't yet exist, so multiple transactions of the kind described
      // can lead to multiple rows with the same key.  Instead, we *could* lock the whole table down, but that would interfere with
      // parallelism.  The lowest-impact approach is to make sure an index constraint is in place, and first attempt to do an INSERT.
      // That attempt will fail if a record already exists.  Then, an update can be attempted.
      //
      // In the situation where the INSERT fails, the current transaction is aborted and a new transaction must be performed.
      // This means that it is impossible to structure things so that the UPDATE is guaranteed to succeed.  So, on the event of an
      // INSERT failure, the UPDATE is tried, but if that fails too, then the INSERT is tried again.  This should also handle the
      // case where a DELETE in another transaction removes the database row before it can be UPDATEd.
      //
      // If the UPDATE does not appear to modify any rows, this is also a signal that the INSERT must be retried.
      //

      // Try the update first.  Typically this succeeds except in the case where a doc is indexed for the first time.
      map.clear();
      map.put(lastVersionField,documentVersion);
      map.put(lastOutputVersionField,outputVersion);
      map.put(forcedParamsField,packedForcedParameters);
      map.put(lastIngestField,new Long(ingestTime));
      if (documentURI != null)
      {
        map.put(docURIField,documentURI);
        map.put(uriHashField,documentURIHash);
      }
      if (authorityNameString != null)
        map.put(authorityNameField,authorityNameString);
      else
        map.put(authorityNameField,"");
      
      // Transaction abort due to deadlock should be retried here.
      while (true)
      {
        long sleepAmt = 0L;

        beginTransaction();
        try
        {
          // Look for existing row.
          ArrayList list = new ArrayList();
          String query = buildConjunctionClause(list,new ClauseDescription[]{
            new UnitaryClause(docKeyField,docKey),
            new UnitaryClause(outputConnNameField,outputConnectionName)});
          IResultSet set = performQuery("SELECT "+idField+","+changeCountField+" FROM "+getTableName()+" WHERE "+
            query+" FOR UPDATE",list,null,null);
          IResultRow row = null;
          if (set.getRowCount() > 0)
            row = set.getRow(0);

          if (row != null)
          {
            // Update the record
            list.clear();
            query = buildConjunctionClause(list,new ClauseDescription[]{
              new UnitaryClause(idField,row.getValue(idField))});
            long changeCount = ((Long)row.getValue(changeCountField)).longValue();
            changeCount++;
            map.put(changeCountField,new Long(changeCount));
            performUpdate(map,"WHERE "+query,list,null);
            // Update successful!
            performCommit();
            return;
          }

          // Update failed to find a matching record, so try the insert
          break;
        }
        catch (ManifoldCFException e)
        {
          signalRollback();
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            if (Logging.perf.isDebugEnabled())
              Logging.perf.debug("Aborted transaction noting ingestion: "+e.getMessage());
            sleepAmt = getSleepAmt();
            continue;
          }

          throw e;
        }
        catch (Error e)
        {
          signalRollback();
          throw e;
        }
        finally
        {
          endTransaction();
          sleepFor(sleepAmt);
        }
      }

      // Set up for insert
      map.clear();
      map.put(lastVersionField,documentVersion);
      map.put(lastOutputVersionField,outputVersion);
      map.put(forcedParamsField,packedForcedParameters);
      map.put(lastIngestField,new Long(ingestTime));
      if (documentURI != null)
      {
        map.put(docURIField,documentURI);
        map.put(uriHashField,documentURIHash);
      }
      if (authorityNameString != null)
        map.put(authorityNameField,authorityNameString);
      else
        map.put(authorityNameField,"");

      Long id = new Long(IDFactory.make(threadContext));
      map.put(idField,id);
      map.put(outputConnNameField,outputConnectionName);
      map.put(docKeyField,docKey);
      map.put(changeCountField,new Long(1));
      map.put(firstIngestField,map.get(lastIngestField));
      beginTransaction();
      try
      {
        performInsert(map,null);
        noteModifications(1,0,0);
        performCommit();
        return;
      }
      catch (ManifoldCFException e)
      {
        signalRollback();
        // If this is simply a constraint violation, we just want to fall through and try the update!
        if (e.getErrorCode() != ManifoldCFException.DATABASE_TRANSACTION_ABORT)
          throw e;
        // Otherwise, exit transaction and fall through to 'update' attempt
      }
      catch (Error e)
      {
        signalRollback();
        throw e;
      }
      finally
      {
        endTransaction();
      }

      // Insert must have failed.  Attempt an update.
    }
  }

  /** Calculate how many clauses at a time
  */
  protected int maxClauseDocumentURIChunk(String outputConnectionName)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
  }
  
  /** Get a chunk of document uris.
  *@param rval is the string array where the uris should be put.
  *@param map is the map from id to index.
  *@param clause is the in clause for the query.
  *@param list is the parameter list for the query.
  */
  protected void getDocumentURIChunk(DeleteInfo[] rval, Map map, String outputConnectionName, ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(docKeyField,list),
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    IResultSet set = performQuery("SELECT "+docKeyField+","+docURIField+","+lastOutputVersionField+" FROM "+getTableName()+" WHERE "+
      query,newList,null,null);

    // Go through list and put into buckets.
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docHash = row.getValue(docKeyField).toString();
      Integer position = (Integer)map.get(docHash);
      if (position != null)
      {
        String lastURI = (String)row.getValue(docURIField);
        if (lastURI != null && lastURI.length() == 0)
          lastURI = null;
        String lastOutputVersion = (String)row.getValue(lastOutputVersionField);
        rval[position.intValue()] = new DeleteInfo(lastURI,lastOutputVersion);
      }
    }
  }

  /** Count the clauses
  */
  protected int maxClauseDocumentIngestDataChunk(String outputConnectionName)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(outputConnNameField,outputConnectionName)});
  }
  
  /** Get a chunk of document ingest data records.
  *@param rval is the document ingest status array where the data should be put.
  *@param map is the map from id to index.
  *@param clause is the in clause for the query.
  *@param list is the parameter list for the query.
  */
  protected void getDocumentIngestDataChunk(DocumentIngestStatus[] rval, Map map, String outputConnectionName, ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(docKeyField,list),
      new UnitaryClause(outputConnNameField,outputConnectionName)});
      
    // Get the primary records associated with this hash value
    IResultSet set = performQuery("SELECT "+idField+","+docKeyField+","+lastVersionField+","+lastOutputVersionField+","+authorityNameField+","+forcedParamsField+
      " FROM "+getTableName()+" WHERE "+query,newList,null,null);

    // Now, go through the original request once more, this time building the result
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docHash = row.getValue(docKeyField).toString();
      Integer position = (Integer)map.get(docHash);
      if (position != null)
      {
        Long id = (Long)row.getValue(idField);
        String lastVersion = (String)row.getValue(lastVersionField);
        String lastOutputVersion = (String)row.getValue(lastOutputVersionField);
        String authorityName = (String)row.getValue(authorityNameField);
        String paramVersion = (String)row.getValue(forcedParamsField);
        rval[position.intValue()] = new DocumentIngestStatus(lastVersion,lastOutputVersion,authorityName,paramVersion);
      }
    }
  }

  // Protected methods

  /** Add or replace document, using the specified output connection, via the standard pool.
  */
  protected int addOrReplaceDocument(IOutputConnection connection, String documentURI, String outputDescription,
    RepositoryDocument document, String authorityNameString,
    IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Set indexing date
    document.setIndexingDate(new Date());
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      return connector.addOrReplaceDocument(documentURI,outputDescription,document,authorityNameString,activities);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Remove document, using the specified output connection, via the standard pool.
  */
  protected void removeDocument(IOutputConnection connection, String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    IOutputConnector connector = outputConnectorPool.grab(connection);
    if (connector == null)
      // The connector is not installed; treat this as a service interruption.
      throw new ServiceInterruption("Output connector not installed",0L);
    try
    {
      connector.removeDocument(documentURI,outputDescription,activities);
    }
    finally
    {
      outputConnectorPool.release(connection,connector);
    }
  }

  /** Make a key from a document class and a hash */
  protected static String makeKey(String documentClass, String documentHash)
  {
    return documentClass + ":" + documentHash;
  }

  /** This class contains the information necessary to delete a document */
  protected static class DeleteInfo
  {
    protected String uriValue;
    protected String outputVersion;

    public DeleteInfo(String uriValue, String outputVersion)
    {
      this.uriValue = uriValue;
      this.outputVersion = outputVersion;
    }

    public String getURI()
    {
      return uriValue;
    }

    public String getOutputVersion()
    {
      return outputVersion;
    }
  }
}
