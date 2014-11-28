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
package org.apache.manifoldcf.agents.transformationconnection;

import java.util.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.agents.system.ManifoldCF;


/** This class is the manager of the transformation connection description.  Inside, a database table is managed,
* with appropriate caching.
* Note well: The database handle is instantiated here using the DBInterfaceFactory.  This is acceptable because the
* actual database that this table is located in is fixed.
* 
* <br><br>
* <b>transformationconnections</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>connectionname</td><td>VARCHAR(32)</td><td>Primary Key</td></tr>
* <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>classname</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>maxcount</td><td>BIGINT</td><td></td></tr>
* <tr><td>configxml</td><td>LONGTEXT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class TransformationConnectionManager extends org.apache.manifoldcf.core.database.BaseTable implements ITransformationConnectionManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Special field suffix
  private final static String passwordSuffix = "password";

  // Database fields
  protected final static String nameField = "connectionname";
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";

  // Cache manager
  protected final ICacheManager cacheManager;
  // Thread context
  protected final IThreadContext threadContext;
  // Lock manager
  protected final ILockManager lockManager;
  
  protected final static String transformationsLock = "TRANSFORMATIONS_LOCK";
  
  /** Constructor.
  *@param threadContext is the thread context.
  */
  public TransformationConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"transformationconnections");

    cacheManager = CacheManagerFactory.make(threadContext);
    lockManager = LockManagerFactory.make(threadContext);
    this.threadContext = threadContext;
  }

  /** Install the manager.
  */
  public void install()
    throws ManifoldCFException
  {
    // Always have an outer loop, in case retries required
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        // Install the table.
        HashMap map = new HashMap();
        map.put(nameField,new ColumnDescription("VARCHAR(32)",true,false,null,null,false));
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(classNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(maxCountField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(configField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code, if needed, goes here.
      }

      // Index management
      IndexDescription classIndex = new IndexDescription(false,new String[]{classNameField});
      
      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (classIndex != null && id.equals(classIndex))
          classIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (classIndex != null)
        performAddIndex(null,classIndex);

      break;
    }
  }

  /** Uninstall the manager.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException
  {
    // Write a version indicator
    ManifoldCF.writeDword(os,1);
    // Get the authority list
    ITransformationConnection[] list = getAllConnections();
    // Write the number of authorities
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual repository connection info
    int i = 0;
    while (i < list.length)
    {
      ITransformationConnection conn = list[i++];
      ManifoldCF.writeString(os,conn.getName());
      ManifoldCF.writeString(os,conn.getDescription());
      ManifoldCF.writeString(os,conn.getClassName());
      ManifoldCF.writeString(os,conn.getConfigParams().toXML());
      ManifoldCF.writeDword(os,conn.getMaxConnections());
    }
  }

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    int version = ManifoldCF.readDword(is);
    if (version != 1)
      throw new java.io.IOException("Unknown transformation connection configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      ITransformationConnection conn = create();
      conn.setName(ManifoldCF.readString(is));
      conn.setDescription(ManifoldCF.readString(is));
      conn.setClassName(ManifoldCF.readString(is));
      conn.getConfigParams().fromXML(ManifoldCF.readString(is));
      conn.setMaxConnections(ManifoldCF.readDword(is));
      // Attempt to save this connection
      save(conn);
      i++;
    }
  }

  /** Obtain a list of the transformation connections, ordered by name.
  *@return an array of connection objects.
  */
  public ITransformationConnection[] getAllConnections()
    throws ManifoldCFException
  {
    lockManager.enterReadLock(transformationsLock);
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getTransformationConnectionsKey());
      StringSet localCacheKeys = new StringSet(ssb);
      IResultSet set = performQuery("SELECT "+nameField+",lower("+nameField+") AS sortfield FROM "+getTableName()+" ORDER BY sortfield ASC",null,
        localCacheKeys,null);
      String[] names = new String[set.getRowCount()];
      for (int i = 0; i < names.length; i++)
      {
        IResultRow row = set.getRow(i);
        names[i] = row.getValue(nameField).toString();
      }
      return loadMultiple(names);
    }
    finally
    {
      lockManager.leaveReadLock(transformationsLock);
    }
  }

  /** Load a transformation connection by name.
  *@param name is the name of the transformation connection.
  *@return the loaded connection object, or null if not found.
  */
  public ITransformationConnection load(String name)
    throws ManifoldCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  protected final static int FETCH_MAX = 200;

  /** Load multiple transformation connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  public ITransformationConnection[] loadMultiple(String[] names)
    throws ManifoldCFException
  {
    ITransformationConnection[] rval = new ITransformationConnection[names.length];
    if (names.length == 0)
      return rval;
    int inputIndex = 0;
    int outputIndex = 0;
    while (names.length - inputIndex > FETCH_MAX)
    {
      outputIndex = loadMultipleInternal(rval,outputIndex,names,inputIndex,FETCH_MAX);
      inputIndex += FETCH_MAX;
    }
    loadMultipleInternal(rval,outputIndex,names,inputIndex,names.length-inputIndex);
    return rval;
  }
  
  protected int loadMultipleInternal(ITransformationConnection[] rval, int outputIndex, String[] fetchNames, int inputIndex, int length)
    throws ManifoldCFException
  {
    // Build description objects
    TransformationConnectionDescription[] objectDescriptions = new TransformationConnectionDescription[length];
    StringSetBuffer ssb = new StringSetBuffer();
    for (int i = 0; i < length; i++)
    {
      ssb.clear();
      String name = fetchNames[inputIndex + i];
      ssb.add(getTransformationConnectionKey(name));
      objectDescriptions[i] = new TransformationConnectionDescription(name,new StringSet(ssb));
    }

    TransformationConnectionExecutor exec = new TransformationConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    ITransformationConnection[] results = exec.getResults();
    for (ITransformationConnection result : results)
    {
      rval[outputIndex++] = result;
    }
    return outputIndex;
  }

  /** Create a new transformation connection object.
  *@return the new object.
  */
  public ITransformationConnection create()
    throws ManifoldCFException
  {
    TransformationConnection rval = new TransformationConnection();
    return rval;
  }

  /** Save a transformation connection object.
  *@param object is the object to save.
  *@return true if the object is being created, false otherwise.
  */
  public boolean save(ITransformationConnection object)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getTransformationConnectionsKey());
    ssb.add(getTransformationConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    while (true)
    {
      // Catch deadlock condition
      long sleepAmt = 0L;
      try
      {
        lockManager.enterNonExWriteLock(transformationsLock);
        try
        {
          ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
          try
          {
            beginTransaction();
            try
            {
              //performLock();
              // Notify of a change to the configuration
              ManifoldCF.noteConfigurationChange();
              boolean isNew = object.getIsNew();
              // See whether the instance exists
              ArrayList params = new ArrayList();
              String query = buildConjunctionClause(params,new ClauseDescription[]{
                new UnitaryClause(nameField,object.getName())});
              IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
                query+" FOR UPDATE",params,null,null);
              HashMap values = new HashMap();
              values.put(descriptionField,object.getDescription());
              values.put(classNameField,object.getClassName());
              values.put(maxCountField,new Long((long)object.getMaxConnections()));
              String configXML = object.getConfigParams().toXML();
              values.put(configField,configXML);
              boolean notificationNeeded = false;
              boolean isCreated;
              
              if (set.getRowCount() > 0)
              {
                // If the object is supposedly new, it is bad that we found one that already exists.
                if (isNew)
                  throw new ManifoldCFException("Transformation connection '"+object.getName()+"' already exists");
                isCreated = false;
                IResultRow row = set.getRow(0);
                String oldXML = (String)row.getValue(configField);
                if (oldXML == null || !oldXML.equals(configXML))
                  notificationNeeded = true;

                // Update
                params.clear();
                query = buildConjunctionClause(params,new ClauseDescription[]{
                  new UnitaryClause(nameField,object.getName())});
                performUpdate(values," WHERE "+query,params,null);
              }
              else
              {
                // If the object is not supposed to be new, it is bad that we did not find one.
                if (!isNew)
                  throw new ManifoldCFException("Transformation connection '"+object.getName()+"' no longer exists");
                isCreated = true;
                // Insert
                values.put(nameField,object.getName());
                // We only need the general key because this is new.
                performInsert(values,null);
              }
              
              // If notification required, do it.
              if (notificationNeeded)
                AgentManagerFactory.noteTransformationConnectionChange(threadContext,object.getName());

              cacheManager.invalidateKeys(ch);
              return isCreated;
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
            cacheManager.leaveCache(ch);
          }
        }
        finally
        {
          lockManager.leaveNonExWriteLock(transformationsLock);
        }
      }
      catch (ManifoldCFException e)
      {
        // Is this a deadlock exception?  If so, we want to try again.
        if (e.getErrorCode() != ManifoldCFException.DATABASE_TRANSACTION_ABORT)
          throw e;
        sleepAmt = getSleepAmt();
      }
      finally
      {
        sleepFor(sleepAmt);
      }
    }
  }

  /** Delete an output connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getTransformationConnectionsKey());
    ssb.add(getTransformationConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    lockManager.enterNonExWriteLock(transformationsLock);
    try
    {
      ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
      try
      {
        beginTransaction();
        try
        {
          // Check if anything refers to this connection name
          if (AgentManagerFactory.isTransformationConnectionInUse(threadContext,name))
            throw new ManifoldCFException("Can't delete transformation connection '"+name+"': existing entities refer to it");
          ManifoldCF.noteConfigurationChange();
          ArrayList params = new ArrayList();
          String query = buildConjunctionClause(params,new ClauseDescription[]{
            new UnitaryClause(nameField,name)});
          performDelete("WHERE "+query,params,null);
          cacheManager.invalidateKeys(ch);
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
        cacheManager.leaveCache(ch);
      }
    }
    finally
    {
      lockManager.leaveNonExWriteLock(transformationsLock);
    }
  }

  /** Get a list of output connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the repository connections that use that connector.
  */
  public String[] findConnectionsForConnector(String className)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getTransformationConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(classNameField,className)});
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+query,params,
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
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getTransformationConnectionKey(name));
      StringSet localCacheKeys = new StringSet(ssb);
      ArrayList params = new ArrayList();
      String query = buildConjunctionClause(params,new ClauseDescription[]{
        new UnitaryClause(nameField,name)});
      IResultSet set = performQuery("SELECT "+classNameField+" FROM "+getTableName()+" WHERE "+query,params,
        localCacheKeys,null);
      if (set.getRowCount() == 0)
        throw new ManifoldCFException("No such connection: '"+name+"'");
      IResultRow row = set.getRow(0);
      String className = (String)row.getValue(classNameField);
      ITransformationConnectorManager cm = TransformationConnectorManagerFactory.make(threadContext);
      return cm.isInstalled(className);
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

  // Schema related
  /** Return the name column.
  *@return the name column.
  */
  public String getConnectionNameColumn()
  {
    return nameField;
  }

  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // output connections.

  /** Construct a key which represents the general list of transformation connectors.
  *@return the cache key.
  */
  protected static String getTransformationConnectionsKey()
  {
    return CacheKeyFactory.makeTransformationConnectionsKey();
  }

  /** Construct a key which represents an individual transformation connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getTransformationConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeTransformationConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple output connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding output connection objects.
  */
  protected TransformationConnection[] getTransformationConnectionsMultiple(String[] connectionNames)
    throws ManifoldCFException
  {
    TransformationConnection[] rval = new TransformationConnection[connectionNames.length];
    Map<String,Integer> returnIndex = new HashMap<String,Integer>();
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
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = maxClauseGetTransformationConnectionsChunk();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getTransformationConnectionsChunk(rval,returnIndex,params);
          params.clear();
          j = 0;
        }
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getTransformationConnectionsChunk(rval,returnIndex,params);
      return rval;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Calculate max number of clauses to send to getTransformationConnectionsChunk.
  */
  protected int maxClauseGetTransformationConnectionsChunk()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Read a chunk of transformation connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param params is the set of parameters.
  */
  protected void getTransformationConnectionsChunk(TransformationConnection[] rval, Map<String,Integer> returnIndex, ArrayList params)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(nameField,params)});
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
      query,list,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String name = row.getValue(nameField).toString();
      int index = returnIndex.get(name).intValue();
      TransformationConnection rc = new TransformationConnection();
      rc.setIsNew(false);
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rc.setClassName((String)row.getValue(classNameField));
      rc.setMaxConnections((int)((Long)row.getValue(maxCountField)).longValue());
      String xml = (String)row.getValue(configField);
      if (xml != null && xml.length() > 0)
        rc.getConfigParams().fromXML(xml);
      rval[index] = rc;
    }

  }

  // The cached instance will be a TransformationConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for a transformation connection object.
  */
  protected static class TransformationConnectionDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public TransformationConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("transformationconnectioncache");
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
      if (!(o instanceof TransformationConnectionDescription))
        return false;
      TransformationConnectionDescription d = (TransformationConnectionDescription)o;
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

  /** This is the executor object for locating transformation connection objects.
  */
  protected static class TransformationConnectionExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected TransformationConnectionManager thisManager;
    protected TransformationConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the TransformationConnectionManager.
    *@param objectDescriptions are the object descriptions.
    */
    public TransformationConnectionExecutor(TransformationConnectionManager manager, TransformationConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new TransformationConnection[objectDescriptions.length];
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
    public TransformationConnection[] getResults()
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
    public Object[] create(ICacheDescription[] objectDescriptions) throws ManifoldCFException
    {
      // Turn the object descriptions into the parameters for the ToolInstance requests
      String[] connectionNames = new String[objectDescriptions.length];
      int i = 0;
      while (i < connectionNames.length)
      {
        TransformationConnectionDescription desc = (TransformationConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getTransformationConnectionsMultiple(connectionNames);
    }


    /** Notify the implementing class of the existence of a cached version of the
    * object.  The object is passed to this method so that the execute() method below
    * will have it available to operate on.  This method is also called for all objects
    * that are freshly created as well.
    * @param objectDescription is the unique identifier of the object.
    * @param cachedObject is the cached object.
    */
    public void exists(ICacheDescription objectDescription, Object cachedObject) throws ManifoldCFException
    {
      // Cast what came in as what it really is
      TransformationConnectionDescription objectDesc = (TransformationConnectionDescription)objectDescription;
      TransformationConnection ci = (TransformationConnection)cachedObject;

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
    public void execute() throws ManifoldCFException
    {
      // Does nothing; we only want to fetch objects in this cacher.
    }


  }

}
