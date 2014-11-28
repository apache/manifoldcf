/* $Id: AuthorityConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.authorities.authority;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;
import org.apache.manifoldcf.authorities.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

import org.apache.manifoldcf.crawler.interfaces.IRepositoryConnectionManager;
import org.apache.manifoldcf.crawler.interfaces.RepositoryConnectionManagerFactory;

/** Implementation of the authority connection manager functionality.
 * 
 * <br><br>
 * <b>authconnections</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>authorityname</td><td>VARCHAR(32)</td><td>Primary Key</td></tr>
 * <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>classname</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>maxcount</td><td>BIGINT</td><td></td></tr>
 * <tr><td>configxml</td><td>LONGTEXT</td><td></td></tr>
 * <tr><td>mappingname</td><td>VARCHAR(32)</td><td></td></tr>
 * <tr><td>authdomainname</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>groupname</td><td>VARCHAR(32)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class AuthorityConnectionManager extends org.apache.manifoldcf.core.database.BaseTable implements IAuthorityConnectionManager
{
  public static final String _rcsid = "@(#)$Id: AuthorityConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $";

  // Special field suffix
  private final static String passwordSuffix = "password";

  protected final static String nameField = "authorityname";      // Changed this to work around a bug in postgresql
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";
  protected final static String mappingField = "mappingname";
  protected final static String authDomainField = "authdomainname";
  protected final static String groupNameField = "groupname";
  
  // Cache manager
  protected final ICacheManager cacheManager;
  // Thread context
  protected final IThreadContext threadContext;
  // Lock manager
  protected final ILockManager lockManager;
  
  protected final static String authoritiesLock = "AUTHORITIES_LOCK";
  
  /** Constructor.
  *@param threadContext is the thread context.
  */
  public AuthorityConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"authconnections");

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
    // First, get the authority manager table name and name column
    IAuthorityGroupManager authMgr = AuthorityGroupManagerFactory.make(threadContext);

    // Always do a loop, in case upgrade needs it.
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
        map.put(mappingField,new ColumnDescription("VARCHAR(32)",false,true,null,null,false));
        map.put(authDomainField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(groupNameField,new ColumnDescription("VARCHAR(32)",false,false,
          authMgr.getTableName(),authMgr.getGroupNameColumn(),false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade goes here if any
      }

      // Index management goes here
      IndexDescription authDomainIndex = new IndexDescription(false,new String[]{authDomainField});
      IndexDescription authorityIndex = new IndexDescription(false,new String[]{groupNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (authDomainIndex != null && id.equals(authDomainIndex))
          authDomainIndex = null;
        if (authorityIndex != null && id.equals(authorityIndex))
          authorityIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (authDomainIndex != null)
        performAddIndex(null,authDomainIndex);
      if (authorityIndex != null)
        performAddIndex(null,authorityIndex);
      break;
    }
  }

  /** Uninstall the manager.
  */
  @Override
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Export configuration */
  @Override
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException
  {
    // Write a version indicator
    ManifoldCF.writeDword(os,3);
    // Get the authority list
    IAuthorityConnection[] list = getAllConnections();
    // Write the number of authorities
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual authority info
    int i = 0;
    while (i < list.length)
    {
      IAuthorityConnection conn = list[i++];
      ManifoldCF.writeString(os,conn.getName());
      ManifoldCF.writeString(os,conn.getDescription());
      ManifoldCF.writeString(os,conn.getClassName());
      ManifoldCF.writeString(os,conn.getConfigParams().toXML());
      ManifoldCF.writeDword(os,conn.getMaxConnections());
      ManifoldCF.writeString(os,conn.getPrerequisiteMapping());
      ManifoldCF.writeString(os,conn.getAuthDomain());
      ManifoldCF.writeString(os,conn.getAuthGroup());
    }
  }

  /** Import configuration */
  @Override
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    IAuthorityGroupManager authMgr = AuthorityGroupManagerFactory.make(threadContext);
    int version = ManifoldCF.readDword(is);
    if (version < 3 || version > 3)
      throw new java.io.IOException("Unknown authority configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      IAuthorityConnection conn = create();
      String name = ManifoldCF.readString(is);
      String description = ManifoldCF.readString(is);
      conn.setName(name);
      conn.setDescription(description);
      conn.setClassName(ManifoldCF.readString(is));
      conn.getConfigParams().fromXML(ManifoldCF.readString(is));
      conn.setMaxConnections(ManifoldCF.readDword(is));
      conn.setPrerequisiteMapping(ManifoldCF.readString(is));
      conn.setAuthDomain(ManifoldCF.readString(is));
      conn.setAuthGroup(ManifoldCF.readString(is));
      
      // Attempt to save this connection
      save(conn);
      i++;
    }
  }

  /** Return true if the specified authority group name is referenced.
  *@param groupName is the authority group name.
  *@return true if referenced, false otherwise.
  */
  @Override
  public boolean isGroupReferenced(String groupName)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(groupNameField,groupName)});
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+query,params,
      localCacheKeys,null);
    return set.getRowCount() > 0;
  }

  /** Obtain a list of the authority connections which correspond to an auth domain.
  *@param authDomain is the domain to get connections for.
  *@return an array of connection objects.
  */
  @Override
  public IAuthorityConnection[] getDomainConnections(String authDomain)
    throws ManifoldCFException
  {
    lockManager.enterReadLock(authoritiesLock);
    try
    {
      // Read the connections for the domain
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getAuthorityConnectionsKey());
      StringSet localCacheKeys = new StringSet(ssb);
      StringBuilder sb = new StringBuilder("SELECT ");
      ArrayList list = new ArrayList();
      sb.append(nameField).append(" FROM ").append(getTableName()).append(" WHERE ");
      sb.append(buildConjunctionClause(list,new ClauseDescription[]{new UnitaryClause(authDomainField,authDomain)}));
      IResultSet set = performQuery(sb.toString(),list,localCacheKeys,null);
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
      lockManager.leaveReadLock(authoritiesLock);
    }
  }
  
  /** Obtain a list of the authority connections, ordered by name.
  *@return an array of connection objects.
  */
  @Override
  public IAuthorityConnection[] getAllConnections()
    throws ManifoldCFException
  {
    lockManager.enterReadLock(authoritiesLock);
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getAuthorityConnectionsKey());
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
      lockManager.leaveReadLock(authoritiesLock);
    }
  }

  /** Load a repository connection by name.
  *@param name is the name of the repository connection.
  *@return the loaded connection object, or null if not found.
  */
  @Override
  public IAuthorityConnection load(String name)
    throws ManifoldCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  protected final static int FETCH_MAX = 200;

  /** Load multiple repository connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  @Override
  public IAuthorityConnection[] loadMultiple(String[] names)
    throws ManifoldCFException
  {
    IAuthorityConnection[] rval = new IAuthorityConnection[names.length];
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
  
  protected int loadMultipleInternal(IAuthorityConnection[] rval, int outputIndex, String[] fetchNames, int inputIndex, int length)
    throws ManifoldCFException
  {
    // Build description objects
    AuthorityConnectionDescription[] objectDescriptions = new AuthorityConnectionDescription[length];
    StringSetBuffer ssb = new StringSetBuffer();
    for (int i = 0; i < length; i++)
    {
      ssb.clear();
      String name = fetchNames[inputIndex + i];
      ssb.add(getAuthorityConnectionKey(name));
      objectDescriptions[i] = new AuthorityConnectionDescription(name,new StringSet(ssb));
    }

    AuthorityConnectionExecutor exec = new AuthorityConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    IAuthorityConnection[] results = exec.getResults();
    for (IAuthorityConnection result : results)
    {
      rval[outputIndex++] = result;
    }
    return outputIndex;
  }

  /** Create a new repository connection object.
  *@return the new object.
  */
  @Override
  public IAuthorityConnection create()
    throws ManifoldCFException
  {
    AuthorityConnection rval = new AuthorityConnection();
    return rval;
  }

  /** Save a repository connection object.
  *@param object is the object to save.
  *@return true if the object is created, false otherwise.
  */
  @Override
  public boolean save(IAuthorityConnection object)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityConnectionsKey());
    ssb.add(getAuthorityConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    while (true)
    {
      long sleepAmt = 0L;
      try
      {
        lockManager.enterNonExWriteLock(authoritiesLock);
        try
        {
          ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
          try
          {
            beginTransaction();
            try
            {
              //performLock();
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
              values.put(configField,object.getConfigParams().toXML());
              values.put(mappingField,object.getPrerequisiteMapping());
              values.put(authDomainField,object.getAuthDomain());
              values.put(groupNameField,object.getAuthGroup());

              boolean isCreated;
              
              if (set.getRowCount() > 0)
              {
                // If the object is supposedly new, it is bad that we found one that already exists.
                if (isNew)
                  throw new ManifoldCFException("Authority connection '"+object.getName()+"' already exists");
                isCreated = false;
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
                  throw new ManifoldCFException("Authority connection '"+object.getName()+"' no longer exists");
                isCreated = true;
                // Insert
                values.put(nameField,object.getName());
                // We only need the general key because this is new.
                performInsert(values,null);
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
          lockManager.leaveNonExWriteLock(authoritiesLock);
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

  /** Delete an authority connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  @Override
  public void delete(String name)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityConnectionsKey());
    ssb.add(getAuthorityConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    lockManager.enterNonExWriteLock(authoritiesLock);
    try
    {
      ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
      try
      {
        beginTransaction();
        try
        {
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
      lockManager.leaveNonExWriteLock(authoritiesLock);
    }
  }

  /** Get the authority connection name column.
  *@return the name column.
  */
  @Override
  public String getAuthorityNameColumn()
  {
    return nameField;
  }

  /** Return true if the specified mapping name is referenced.
  *@param mappingName is the mapping name.
  *@return true if referenced, false otherwise.
  */
  @Override
  public boolean isMappingReferenced(String mappingName)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(mappingField,mappingName)});
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+query,params,
      localCacheKeys,null);
    return set.getRowCount() > 0;
  }

  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // repository connections.

  /** Construct a key which represents the general list of repository connectors.
  *@return the cache key.
  */
  protected static String getAuthorityConnectionsKey()
  {
    return CacheKeyFactory.makeAuthorityConnectionsKey();
  }

  /** Construct a key which represents an individual repository connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getAuthorityConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeAuthorityConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple repository connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding repository connection objects.
  */
  protected AuthorityConnection[] getAuthorityConnectionsMultiple(String[] connectionNames)
    throws ManifoldCFException
  {
    AuthorityConnection[] rval = new AuthorityConnection[connectionNames.length];
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
      int maxIn = maxClauseGetAuthorityConnectionsChunk();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getAuthorityConnectionsChunk(rval,returnIndex,params);
          params.clear();
          j = 0;
        }
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getAuthorityConnectionsChunk(rval,returnIndex,params);
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

  /** Find the maximum number of clauses for getAuthorityConnectionsChunk.
  */
  protected int maxClauseGetAuthorityConnectionsChunk()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Read a chunk of authority connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param params is the set of parameters.
  */
  protected void getAuthorityConnectionsChunk(AuthorityConnection[] rval, Map returnIndex, ArrayList params)
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
      AuthorityConnection rc = new AuthorityConnection();
      rc.setIsNew(false);
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rc.setClassName((String)row.getValue(classNameField));
      rc.setMaxConnections((int)((Long)row.getValue(maxCountField)).longValue());
      rc.setPrerequisiteMapping((String)row.getValue(mappingField));
      rc.setAuthDomain((String)row.getValue(authDomainField));
      rc.setAuthGroup((String)row.getValue(groupNameField));
      String xml = (String)row.getValue(configField);
      if (xml != null && xml.length() > 0)
        rc.getConfigParams().fromXML(xml);
      rval[index] = rc;
    }
  }

  // The cached instance will be a AuthorityConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for a repository connection object.
  */
  protected static class AuthorityConnectionDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public AuthorityConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("authorityconnectioncache");
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
      if (!(o instanceof AuthorityConnectionDescription))
        return false;
      AuthorityConnectionDescription d = (AuthorityConnectionDescription)o;
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
  protected static class AuthorityConnectionExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected AuthorityConnectionManager thisManager;
    protected AuthorityConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the ToolManager.
    *@param objectDescriptions are the object descriptions.
    */
    public AuthorityConnectionExecutor(AuthorityConnectionManager manager, AuthorityConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new AuthorityConnection[objectDescriptions.length];
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
    public AuthorityConnection[] getResults()
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
        AuthorityConnectionDescription desc = (AuthorityConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getAuthorityConnectionsMultiple(connectionNames);
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
      AuthorityConnectionDescription objectDesc = (AuthorityConnectionDescription)objectDescription;
      AuthorityConnection ci = (AuthorityConnection)cachedObject;

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
