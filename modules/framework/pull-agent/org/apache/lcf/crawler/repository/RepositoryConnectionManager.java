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
package org.apache.lcf.crawler.repository;

import java.util.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.authorities.interfaces.*;
import org.apache.lcf.crawler.interfaces.CacheKeyFactory;
import org.apache.lcf.crawler.system.LCF;


/** This class is the manager of the repository connection description.  Inside, multiple database tables are managed,
* with appropriate caching.
* Note well: The database handle is instantiated here using the DBInterfaceFactory.  This is acceptable because the
* actual database that this table is located in is fixed.
*/
public class RepositoryConnectionManager extends org.apache.lcf.core.database.BaseTable implements IRepositoryConnectionManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Special field suffix
  private final static String passwordSuffix = "password";

  // Database fields
  protected final static String nameField = "connectionname";     // Changed this to work around constraint bug in postgresql
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String authorityNameField = "authorityname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";

  // Handle for repository history manager
  protected RepositoryHistoryManager historyManager;
  // Handle for throttle spec storage
  protected ThrottleSpecManager throttleSpecManager;

  // Cache manager
  ICacheManager cacheManager;
  // Thread context
  IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  */
  public RepositoryConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws LCFException
  {
    super(database,"repoconnections");

    historyManager = new RepositoryHistoryManager(threadContext,database);
    throttleSpecManager = new ThrottleSpecManager(database);
    cacheManager = CacheManagerFactory.make(threadContext);
    this.threadContext = threadContext;
  }

  /** Install the manager.
  */
  public void install()
    throws LCFException
  {
    // First, get the authority manager table name and name column
    IAuthorityConnectionManager authMgr = AuthorityConnectionManagerFactory.make(threadContext);

    // Always use a loop, and no transaction, as we may need to retry due to upgrade
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        // Install the "objects" table.
        HashMap map = new HashMap();
        map.put(nameField,new ColumnDescription("VARCHAR(32)",true,false,null,null,false));
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(classNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(authorityNameField,new ColumnDescription("VARCHAR(32)",false,true,
          authMgr.getTableName(),authMgr.getAuthorityNameColumn(),false));
        map.put(maxCountField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(configField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code would go here, if needed.
      }

      // Install dependent tables.
      historyManager.install(getTableName(),nameField);
      throttleSpecManager.install(getTableName(),nameField);

      // Index management goes here.

      break;
    }
  }

  /** Uninstall the manager.
  */
  public void deinstall()
    throws LCFException
  {
    beginTransaction();
    try
    {
      throttleSpecManager.deinstall();
      historyManager.deinstall();
      performDrop(null);
    }
    catch (LCFException e)
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

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, LCFException
  {
    // Write a version indicator
    LCF.writeDword(os,1);
    // Get the authority list
    IRepositoryConnection[] list = getAllConnections();
    // Write the number of authorities
    LCF.writeDword(os,list.length);
    // Loop through the list and write the individual repository connection info
    int i = 0;
    while (i < list.length)
    {
      IRepositoryConnection conn = list[i++];
      LCF.writeString(os,conn.getName());
      LCF.writeString(os,conn.getDescription());
      LCF.writeString(os,conn.getClassName());
      LCF.writeString(os,conn.getConfigParams().toXML());
      LCF.writeString(os,conn.getACLAuthority());
      LCF.writeDword(os,conn.getMaxConnections());
      String[] throttles = conn.getThrottles();
      LCF.writeDword(os,throttles.length);
      int j = 0;
      while (j < throttles.length)
      {
        String throttleName = throttles[j++];
        LCF.writeString(os,throttleName);
        LCF.writeString(os,conn.getThrottleDescription(throttleName));
        LCF.writefloat(os,conn.getThrottleValue(throttleName));
      }
    }
  }

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, LCFException
  {
    int version = LCF.readDword(is);
    if (version != 1)
      throw new java.io.IOException("Unknown repository connection configuration version: "+Integer.toString(version));
    int count = LCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      IRepositoryConnection conn = create();
      conn.setName(LCF.readString(is));
      conn.setDescription(LCF.readString(is));
      conn.setClassName(LCF.readString(is));
      conn.getConfigParams().fromXML(LCF.readString(is));
      conn.setACLAuthority(LCF.readString(is));
      conn.setMaxConnections(LCF.readDword(is));
      int throttleCount = LCF.readDword(is);
      int j = 0;
      while (j < throttleCount)
      {
        String throttleName = LCF.readString(is);
        conn.addThrottleValue(throttleName,LCF.readString(is),LCF.readfloat(is));
        j++;
      }
      // Attempt to save this connection
      save(conn);
      i++;
    }
  }

  /** Obtain a list of the repository connections, ordered by name.
  *@return an array of connection objects.
  */
  public IRepositoryConnection[] getAllConnections()
    throws LCFException
  {
    beginTransaction();
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getRepositoryConnectionsKey());
      StringSet localCacheKeys = new StringSet(ssb);
      IResultSet set = performQuery("SELECT "+nameField+",lower("+nameField+") AS sortfield FROM "+getTableName()+" ORDER BY sortfield ASC",null,
        localCacheKeys,null);
      String[] names = new String[set.getRowCount()];
      int i = 0;
      while (i < names.length)
      {
        IResultRow row = set.getRow(i);
        names[i] = row.getValue(nameField).toString();
        i++;
      }
      return loadMultiple(names);
    }
    catch (LCFException e)
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

  /** Load a repository connection by name.
  *@param name is the name of the repository connection.
  *@return the loaded connection object, or null if not found.
  */
  public IRepositoryConnection load(String name)
    throws LCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  /** Load multiple repository connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  public IRepositoryConnection[] loadMultiple(String[] names)
    throws LCFException
  {
    // Build description objects
    RepositoryConnectionDescription[] objectDescriptions = new RepositoryConnectionDescription[names.length];
    int i = 0;
    StringSetBuffer ssb = new StringSetBuffer();
    while (i < names.length)
    {
      ssb.clear();
      ssb.add(getRepositoryConnectionKey(names[i]));
      objectDescriptions[i] = new RepositoryConnectionDescription(names[i],new StringSet(ssb));
      i++;
    }

    RepositoryConnectionExecutor exec = new RepositoryConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    return exec.getResults();
  }

  /** Create a new repository connection object.
  *@return the new object.
  */
  public IRepositoryConnection create()
    throws LCFException
  {
    RepositoryConnection rval = new RepositoryConnection();
    return rval;
  }

  /** Save a repository connection object.
  *@param object is the object to save.
  */
  public void save(IRepositoryConnection object)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getRepositoryConnectionsKey());
    ssb.add(getRepositoryConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
    try
    {
      beginTransaction();
      try
      {
        performLock();
        // Notify of a change to the configuration
        LCF.noteConfigurationChange();
        // See whether the instance exists
        ArrayList params = new ArrayList();
        params.add(object.getName());
        IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
          nameField+"=? FOR UPDATE",params,null,null);
        HashMap values = new HashMap();
        values.put(descriptionField,object.getDescription());
        values.put(classNameField,object.getClassName());
        values.put(authorityNameField,object.getACLAuthority());
        values.put(maxCountField,new Long((long)object.getMaxConnections()));
        values.put(configField,object.getConfigParams().toXML());

        if (set.getRowCount() > 0)
        {
          // Update
          params.clear();
          params.add(object.getName());
          performUpdate(values," WHERE "+nameField+"=?",params,null);
          throttleSpecManager.deleteRows(object.getName());
        }
        else
        {
          // Insert
          values.put(nameField,object.getName());
          // We only need the general key because this is new.
          performInsert(values,null);
        }

        // Write secondary table stuff
        throttleSpecManager.writeRows(object.getName(),object);

        cacheManager.invalidateKeys(ch);
      }
      catch (LCFException e)
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
      cacheManager.leaveCache(ch);
    }
  }

  /** Delete a repository connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws LCFException
  {
    // Grab a job manager handle.  We will need to check if any jobs refer to this connection.
    IJobManager jobManager = JobManagerFactory.make(threadContext);

    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getRepositoryConnectionsKey());
    ssb.add(getRepositoryConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
    try
    {
      beginTransaction();
      try
      {
        // Check if any jobs refer to this connection name
        if (jobManager.checkIfReference(name))
          throw new LCFException("Can't delete repository connection '"+name+"': existing jobs refer to it");
        LCF.noteConfigurationChange();
        throttleSpecManager.deleteRows(name);
        historyManager.deleteOwner(name,null);
        ArrayList params = new ArrayList();
        params.add(name);
        performDelete("WHERE "+nameField+"=?",params,null);
        cacheManager.invalidateKeys(ch);
      }
      catch (LCFException e)
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
      cacheManager.leaveCache(ch);
    }

  }

  /** Return true if the specified authority name is referenced.
  *@param authorityName is the authority name.
  *@return true if referenced, false otherwise.
  */
  public boolean isReferenced(String authorityName)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getRepositoryConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    params.add(authorityName);
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+authorityNameField+"=?",params,
      localCacheKeys,null);
    return set.getRowCount() > 0;
  }

  /** Get a list of repository connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the repository connections that use that connector.
  */
  public String[] findConnectionsForConnector(String className)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getRepositoryConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    params.add(className);
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+classNameField+"=?",params,
      localCacheKeys,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(nameField);
      i++;
    }
    java.util.Arrays.sort(rval);
    return rval;
  }

  /** Check if underlying connector exists.
  *@param name is the name of the connection to check.
  *@return true if the underlying connector is registered.
  */
  public boolean checkConnectorExists(String name)
    throws LCFException
  {
    beginTransaction();
    try
    {
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getRepositoryConnectionKey(name));
      StringSet localCacheKeys = new StringSet(ssb);
      ArrayList params = new ArrayList();
      params.add(name);
      IResultSet set = performQuery("SELECT "+classNameField+" FROM "+getTableName()+" WHERE "+nameField+"=?",params,
        localCacheKeys,null);
      if (set.getRowCount() == 0)
        throw new LCFException("No such connection: '"+name+"'");
      IResultRow row = set.getRow(0);
      String className = (String)row.getValue(classNameField);
      IConnectorManager cm = ConnectorManagerFactory.make(threadContext);
      return cm.isInstalled(className);
    }
    catch (LCFException e)
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

  // Schema related
  /** Return the name column.
  *@return the name column.
  */
  public String getConnectionNameColumn()
  {
    return nameField;
  }


  // Reporting and analysis related


  /** Record time-stamped information about the activity of the connection.  This information can originate from
  * either the connector or from the framework.  The reason it is here is that it is viewed as 'belonging' to an
  * individual connection, and is segregated accordingly.
  *@param connectionName is the connection to which the record belongs.  If the connection is deleted, the
  * corresponding records will also be deleted.  Cannot be null.
  *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
  *       activity has an associated time; the startTime field records when the activity began.  A null value
  *       indicates that the start time and the finishing time are the same.
  *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
  *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
  *       "fetch document" activity, while the framework might record "ingest document", "job start", "job finish",
  *       "job abort", etc.  Cannot be null.
  *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
  *@param entityIdentifier is a (possibly long) string which identifies the object involved in the history record.
  *       The interpretation of this field will differ from connector to connector.  May be null.
  *@param resultCode contains a terse description of the result of the activity.  The description is limited in
  *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
  *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
  *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
  *@param childIdentifiers is a set of child entity identifiers associated with this activity.  May be null.
  */
  public void recordHistory(String connectionName, Long startTime, String activityType, Long dataSize,
    String entityIdentifier, String resultCode, String resultDescription, String[] childIdentifiers)
    throws LCFException
  {
    long endTimeValue = System.currentTimeMillis();
    long startTimeValue;
    if (startTime == null)
      startTimeValue = endTimeValue - 1L;
    else
    {
      startTimeValue = startTime.longValue();
      if (startTimeValue == endTimeValue)
        startTimeValue -= 1L;   // Zero-time events are not allowed.
    }

    long dataSizeValue;
    if (dataSize == null)
      dataSizeValue = 0L;
    else
      dataSizeValue = dataSize.longValue();

    Long rowID = historyManager.addRow(connectionName,startTimeValue,endTimeValue,dataSizeValue,activityType,
      entityIdentifier,resultCode,resultDescription);
    // child identifiers are not stored, for now.
    // MHL
  }

  /** Count the number of rows specified by a given set of criteria.  This can be used to make decisions
  * as to whether a query based on those rows will complete in an acceptable amount of time.
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@return the number of rows included by the criteria.
  */
  public long countHistoryRows(String connectionName, FilterCriteria criteria)
    throws LCFException
  {
    return historyManager.countHistoryRows(connectionName,criteria);
  }

  /** Generate a report, listing the start time, elapsed time, result code and description, number of bytes, and entity identifier.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The resultset returned should have the following columns: "starttime","elapsedtime","resultcode","resultdesc","bytes","identifier".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistorySimple(String connectionName, FilterCriteria criteria, SortOrder sort, int startRow, int maxRowCount)
    throws LCFException
  {
    return historyManager.simpleReport(connectionName,criteria,sort,startRow,maxRowCount);
  }

  /** Generate a report, listing the start time, activity count, and identifier bucket, given
  * a time slice (interval) size.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The identifier bucket description is specified by the bucket description object.
  * The resultset returned should have the following columns: "starttime","endtime","activitycount","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param interval is the time interval, in milliseconds, to locate.  There will be one row in the resultset
  *       for each distinct idBucket value, and the returned activity count will the maximum found over the
  *       specified interval size.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryActivityCount(String connectionName, FilterCriteria criteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws LCFException
  {
    return historyManager.maxActivityCountReport(connectionName,criteria,sort,idBucket,interval,startRow,maxRowCount);
  }

  /** Generate a report, listing the start time, bytes processed, and identifier bucket, given
  * a time slice (interval) size.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The identifier bucket description is specified by the bucket description object.
  * The resultset returned should have the following columns: "starttime","endtime","bytecount","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param interval is the time interval, in milliseconds, to locate.  There will be one row in the resultset
  *       for each distinct idBucket value, and the returned activity count will the maximum found over the
  *       specified interval size.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryByteCount(String connectionName, FilterCriteria criteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws LCFException
  {
    return historyManager.maxByteCountReport(connectionName,criteria,sort,idBucket,interval,startRow,maxRowCount);
  }


  /** Generate a report, listing the result bucket and identifier bucket.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The result code bucket description is specified by a bucket description object.
  * The identifier bucket description is specified by a bucket description object.
  * The resultset returned should have the following columns: "resultcodebucket","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param resultCodeBucket is the description of the bucket based on processed result codes.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryResultCodes(String connectionName, FilterCriteria criteria, SortOrder sort,
    BucketDescription resultCodeBucket, BucketDescription idBucket, int startRow, int maxRowCount)
    throws LCFException
  {
    return historyManager.resultCodesReport(connectionName,criteria,sort,resultCodeBucket,idBucket,startRow,maxRowCount);
  }

  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // repository connections.

  /** Construct a key which represents the general list of repository connectors.
  *@return the cache key.
  */
  protected static String getRepositoryConnectionsKey()
  {
    return CacheKeyFactory.makeRepositoryConnectionsKey();
  }

  /** Construct a key which represents an individual repository connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getRepositoryConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeRepositoryConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple repository connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding repository connection objects.
  */
  protected RepositoryConnection[] getRepositoryConnectionsMultiple(String[] connectionNames)
    throws LCFException
  {
    RepositoryConnection[] rval = new RepositoryConnection[connectionNames.length];
    HashMap returnIndex = new HashMap();
    int i = 0;
    while (i < connectionNames.length)
    {
      rval[i] = null;
      returnIndex.put(connectionNames[i],new Integer(i));
      i++;
    }
    beginTransaction();
    try
    {
      i = 0;
      StringBuffer sb = new StringBuffer();
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = getMaxInClause();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getRepositoryConnectionsChunk(rval,returnIndex,sb.toString(),params);
          sb.setLength(0);
          params.clear();
          j = 0;
        }
        if (j > 0)
          sb.append(',');
        sb.append('?');
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getRepositoryConnectionsChunk(rval,returnIndex,sb.toString(),params);
      return rval;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (LCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Read a chunk of repository connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param idList is the list of id's.
  *@param params is the set of parameters.
  */
  protected void getRepositoryConnectionsChunk(RepositoryConnection[] rval, Map returnIndex, String idList, ArrayList params)
    throws LCFException
  {
    IResultSet set;
    set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
      nameField+" IN ("+idList+")",params,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String name = row.getValue(nameField).toString();
      int index = ((Integer)returnIndex.get(name)).intValue();
      RepositoryConnection rc = new RepositoryConnection();
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rc.setClassName((String)row.getValue(classNameField));
      rc.setACLAuthority((String)row.getValue(authorityNameField));
      rc.setMaxConnections((int)((Long)row.getValue(maxCountField)).longValue());
      String xml = (String)row.getValue(configField);
      if (xml != null && xml.length() > 0)
        rc.getConfigParams().fromXML(xml);
      rval[index] = rc;
    }

    // Do throttle part
    throttleSpecManager.getRows(rval,returnIndex,idList,params);
  }

  // The cached instance will be a RepositoryConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for a repository connection object.
  */
  protected static class RepositoryConnectionDescription extends org.apache.lcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public RepositoryConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("repositoryconnectioncache");
      this.connectionName = connectionName;
      criticalSectionName = getClass().getName()+"-"+connectionName;
      cacheKeys = invKeys;
    }

    public String getConnectionName()
    {
      return connectionName;
    }

    public int hashCode()
    {
      return connectionName.hashCode();
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof RepositoryConnectionDescription))
        return false;
      RepositoryConnectionDescription d = (RepositoryConnectionDescription)o;
      return d.connectionName.equals(connectionName);
    }

    public String getCriticalSectionName()
    {
      return criticalSectionName;
    }

    /** Get the cache keys for an object (which may or may not exist yet in
    * the cache).  This method is called in order for cache manager to throw the correct locks.
    * @return the object's cache keys, or null if the object should not
    * be cached.
    */
    public StringSet getObjectKeys()
    {
      return cacheKeys;
    }

  }

  /** This is the executor object for locating repository connection objects.
  */
  protected static class RepositoryConnectionExecutor extends org.apache.lcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected RepositoryConnectionManager thisManager;
    protected RepositoryConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the ToolManager.
    *@param objectDescriptions are the object descriptions.
    */
    public RepositoryConnectionExecutor(RepositoryConnectionManager manager, RepositoryConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new RepositoryConnection[objectDescriptions.length];
      int i = 0;
      while (i < objectDescriptions.length)
      {
        returnMap.put(objectDescriptions[i].getConnectionName(),new Integer(i));
        i++;
      }
    }

    /** Get the result.
    *@return the looked-up or read cached instances.
    */
    public RepositoryConnection[] getResults()
    {
      return returnValues;
    }

    /** Create a set of new objects to operate on and cache.  This method is called only
    * if the specified object(s) are NOT available in the cache.  The specified objects
    * should be created and returned; if they are not created, it means that the
    * execution cannot proceed, and the execute() method will not be called.
    * @param objectDescriptions is the set of unique identifier of the object.
    * @return the newly created objects to cache, or null, if any object cannot be created.
    *  The order of the returned objects must correspond to the order of the object descriptinos.
    */
    public Object[] create(ICacheDescription[] objectDescriptions) throws LCFException
    {
      // Turn the object descriptions into the parameters for the ToolInstance requests
      String[] connectionNames = new String[objectDescriptions.length];
      int i = 0;
      while (i < connectionNames.length)
      {
        RepositoryConnectionDescription desc = (RepositoryConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getRepositoryConnectionsMultiple(connectionNames);
    }


    /** Notify the implementing class of the existence of a cached version of the
    * object.  The object is passed to this method so that the execute() method below
    * will have it available to operate on.  This method is also called for all objects
    * that are freshly created as well.
    * @param objectDescription is the unique identifier of the object.
    * @param cachedObject is the cached object.
    */
    public void exists(ICacheDescription objectDescription, Object cachedObject) throws LCFException
    {
      // Cast what came in as what it really is
      RepositoryConnectionDescription objectDesc = (RepositoryConnectionDescription)objectDescription;
      RepositoryConnection ci = (RepositoryConnection)cachedObject;

      // Duplicate it!
      if (ci != null)
        ci = ci.duplicate();

      // In order to make the indexes line up, we need to use the hashtable built by
      // the constructor.
      returnValues[((Integer)returnMap.get(objectDesc.getConnectionName())).intValue()] = ci;
    }

    /** Perform the desired operation.  This method is called after either createGetObject()
    * or exists() is called for every requested object.
    */
    public void execute() throws LCFException
    {
      // Does nothing; we only want to fetch objects in this cacher.
    }


  }

}
