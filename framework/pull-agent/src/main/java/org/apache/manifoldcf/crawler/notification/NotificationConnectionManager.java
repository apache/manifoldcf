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
package org.apache.manifoldcf.crawler.notification;

import java.util.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;


/** This class is the manager of the notification connection description.  Inside, multiple database tables are managed,
* with appropriate caching.
* Note well: The database handle is instantiated here using the DBInterfaceFactory.  This is acceptable because the
* actual database that this table is located in is fixed.
* 
* <br><br>
* <b>notificationconnections</b>
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
public class NotificationConnectionManager extends org.apache.manifoldcf.core.database.BaseTable implements INotificationConnectionManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Special field suffix
  private final static String passwordSuffix = "password";

  // Database fields
  protected final static String nameField = "connectionname";     // Changed this to work around constraint bug in postgresql
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";

  protected static Random random = new Random();

  // Cache manager
  protected final ICacheManager cacheManager;
  // Thread context
  protected final IThreadContext threadContext;
  // Lock manager
  protected final ILockManager lockManager;
  
  protected final String notificationsLock = "NOTIFICATIONS_LOCK";
  
  /** Constructor.
  *@param threadContext is the thread context.
  */
  public NotificationConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"notificationconnections");

    cacheManager = CacheManagerFactory.make(threadContext);
    lockManager = LockManagerFactory.make(threadContext);
    this.threadContext = threadContext;
  }

  /** Install the manager.
  */
  @Override
  public void install()
    throws ManifoldCFException
  {

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
        map.put(maxCountField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(configField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code
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
  @Override
  public void deinstall()
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      performDrop(null);
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

  /** Export configuration */
  @Override
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException
  {
    // Write a version indicator
    ManifoldCF.writeDword(os,1);
    // Get the authority list
    INotificationConnection[] list = getAllConnections();
    // Write the number of authorities
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual repository connection info
    int i = 0;
    while (i < list.length)
    {
      INotificationConnection conn = list[i++];
      ManifoldCF.writeString(os,conn.getName());
      ManifoldCF.writeString(os,conn.getDescription());
      ManifoldCF.writeString(os,conn.getClassName());
      ManifoldCF.writeString(os,conn.getConfigParams().toXML());
      ManifoldCF.writeDword(os,conn.getMaxConnections());
    }
  }

  /** Import configuration */
  @Override
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    int version = ManifoldCF.readDword(is);
    if (version != 1)
      throw new java.io.IOException("Unknown notification connection configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      INotificationConnection conn = create();
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

  /** Obtain a list of the notification connections, ordered by name.
  *@return an array of connection objects.
  */
  @Override
  public INotificationConnection[] getAllConnections()
    throws ManifoldCFException
  {
    lockManager.enterReadLock(notificationsLock);
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getNotificationConnectionsKey());
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
      lockManager.leaveReadLock(notificationsLock);
    }
  }

  /** Load a notification connection by name.
  *@param name is the name of the notification connection.
  *@return the loaded connection object, or null if not found.
  */
  @Override
  public INotificationConnection load(String name)
    throws ManifoldCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  protected final static int FETCH_MAX = 200;
  
  /** Load multiple notification connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  @Override
  public INotificationConnection[] loadMultiple(String[] names)
    throws ManifoldCFException
  {
    INotificationConnection[] rval = new INotificationConnection[names.length];
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
  
  protected int loadMultipleInternal(INotificationConnection[] rval, int outputIndex, String[] fetchNames, int inputIndex, int length)
    throws ManifoldCFException
  {
    // Build description objects
    NotificationConnectionDescription[] objectDescriptions = new NotificationConnectionDescription[length];
    StringSetBuffer ssb = new StringSetBuffer();
    for (int i = 0; i < length; i++)
    {
      ssb.clear();
      String name = fetchNames[inputIndex + i];
      ssb.add(getNotificationConnectionKey(name));
      objectDescriptions[i] = new NotificationConnectionDescription(name,new StringSet(ssb));
    }

    NotificationConnectionExecutor exec = new NotificationConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    INotificationConnection[] results = exec.getResults();
    for (INotificationConnection result : results)
    {
      rval[outputIndex++] = result;
    }
    return outputIndex;
  }

  /** Create a new notification connection object.
  *@return the new object.
  */
  @Override
  public INotificationConnection create()
    throws ManifoldCFException
  {
    NotificationConnection rval = new NotificationConnection();
    return rval;
  }

  /** Save a notification connection object.
  *@param object is the object to save.
  *@return true if the object was created, false otherwise.
  */
  @Override
  public boolean save(INotificationConnection object)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getNotificationConnectionsKey());
    ssb.add(getNotificationConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    while (true)
    {
      // Catch deadlock condition
      long sleepAmt = 0L;
      try
      {
        lockManager.enterNonExWriteLock(notificationsLock);
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
                  throw new ManifoldCFException("Notification connection '"+object.getName()+"' already exists");
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
                  throw new ManifoldCFException("Notification connection '"+object.getName()+"' no longer exists");
                isCreated = true;
                // Insert
                values.put(nameField,object.getName());
                // We only need the general key because this is new.
                performInsert(values,null);
              }

              // If notification required, do it.
              if (notificationNeeded)
              {
                IJobManager jobManager = JobManagerFactory.make(threadContext);
                jobManager.noteNotificationConnectionChange(object.getName());
              }

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
          lockManager.leaveNonExWriteLock(notificationsLock);
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

  /** Delete a notification connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  @Override
  public void delete(String name)
    throws ManifoldCFException
  {
    // Grab a job manager handle.  We will need to check if any jobs refer to this connection.
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getNotificationConnectionsKey());
    ssb.add(getNotificationConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    lockManager.enterNonExWriteLock(notificationsLock);
    try
    {
      ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
      try
      {
        beginTransaction();
        try
        {
          // Check if any jobs refer to this connection name
          if (jobManager.checkIfNotificationReference(name))
            throw new ManifoldCFException("Can't delete notification connection '"+name+"': existing jobs refer to it");
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
      lockManager.leaveNonExWriteLock(notificationsLock);
    }
  }

  /** Get a list of notification connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the notification connections that use that connector.
  */
  @Override
  public String[] findConnectionsForConnector(String className)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getNotificationConnectionsKey());
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
  @Override
  public boolean checkConnectorExists(String name)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getNotificationConnectionKey(name));
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
      INotificationConnectorManager cm = NotificationConnectorManagerFactory.make(threadContext);
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
  @Override
  public String getConnectionNameColumn()
  {
    return nameField;
  }


  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // repository connections.

  /** Construct a key which represents the general list of notification connectors.
  *@return the cache key.
  */
  protected static String getNotificationConnectionsKey()
  {
    return CacheKeyFactory.makeNotificationConnectionsKey();
  }

  /** Construct a key which represents an individual notification connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getNotificationConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeNotificationConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple notification connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding repository connection objects.
  */
  protected NotificationConnection[] getNotificationConnectionsMultiple(String[] connectionNames)
    throws ManifoldCFException
  {
    NotificationConnection[] rval = new NotificationConnection[connectionNames.length];
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
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = maxClauseGetNotificationConnectionsChunk();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getNotificationConnectionsChunk(rval,returnIndex,params);
          params.clear();
          j = 0;
        }
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getNotificationConnectionsChunk(rval,returnIndex,params);
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

  /** Calculate how many notification connections to get at once.
  */
  protected int maxClauseGetNotificationConnectionsChunk()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
  
  /** Read a chunk of notification connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param params is the set of parameters.
  */
  protected void getNotificationConnectionsChunk(NotificationConnection[] rval, Map returnIndex, ArrayList params)
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
      int index = ((Integer)returnIndex.get(name)).intValue();
      NotificationConnection rc = new NotificationConnection();
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

  // The cached instance will be a NotificationConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for a repository connection object.
  */
  protected static class NotificationConnectionDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public NotificationConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("notificationconnectioncache");
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
      if (!(o instanceof NotificationConnectionDescription))
        return false;
      NotificationConnectionDescription d = (NotificationConnectionDescription)o;
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

  /** This is the executor object for locating notification connection objects.
  */
  protected static class NotificationConnectionExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected NotificationConnectionManager thisManager;
    protected NotificationConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the NotificationManager.
    *@param objectDescriptions are the object descriptions.
    */
    public NotificationConnectionExecutor(NotificationConnectionManager manager, NotificationConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new NotificationConnection[objectDescriptions.length];
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
    public NotificationConnection[] getResults()
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
        NotificationConnectionDescription desc = (NotificationConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getNotificationConnectionsMultiple(connectionNames);
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
      NotificationConnectionDescription objectDesc = (NotificationConnectionDescription)objectDescription;
      NotificationConnection ci = (NotificationConnection)cachedObject;

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
