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
package org.apache.manifoldcf.authorities.mapping;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;
import org.apache.manifoldcf.authorities.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

/** Implementation of the authority connection manager functionality.
 * 
 * <br><br>
 * <b>mapconnections</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>mappingname</td><td>VARCHAR(32)</td><td>Primary Key</td></tr>
 * <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>classname</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>maxcount</td><td>BIGINT</td><td></td></tr>
 * <tr><td>configxml</td><td>LONGTEXT</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class MappingConnectionManager extends org.apache.manifoldcf.core.database.BaseTable implements IMappingConnectionManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Special field suffix
  private final static String passwordSuffix = "password";

  protected final static String nameField = "connname";      // Changed this to work around a bug in postgresql
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";
  protected final static String mappingField = "mappingname";

  // Cache manager
  protected final ICacheManager cacheManager;
  // Thread context
  protected final IThreadContext threadContext;
  // Lock manager
  protected final ILockManager lockManager;
  
  protected final static String mappingsLock = "MAPPINGS_LOCK";
  
  /** Constructor.
  *@param threadContext is the thread context.
  */
  public MappingConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"mapconnections");

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
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here
      }

      // Index management goes here

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
    ManifoldCF.writeDword(os,1);
    // Get the authority list
    IMappingConnection[] list = getAllConnections();
    // Write the number of authorities
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual mapping info
    for (IMappingConnection conn : list)
    {
      ManifoldCF.writeString(os,conn.getName());
      ManifoldCF.writeString(os,conn.getDescription());
      ManifoldCF.writeString(os,conn.getClassName());
      ManifoldCF.writeString(os,conn.getConfigParams().toXML());
      ManifoldCF.writeDword(os,conn.getMaxConnections());
      ManifoldCF.writeString(os,conn.getPrerequisiteMapping());
    }
    
  }

  /** Import configuration */
  @Override
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    int version = ManifoldCF.readDword(is);
    if (version != 1)
      throw new java.io.IOException("Unknown mapping configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    for (int i = 0; i < count; i++)
    {
      IMappingConnection conn = create();
      conn.setName(ManifoldCF.readString(is));
      conn.setDescription(ManifoldCF.readString(is));
      conn.setClassName(ManifoldCF.readString(is));
      conn.getConfigParams().fromXML(ManifoldCF.readString(is));
      conn.setMaxConnections(ManifoldCF.readDword(is));
      conn.setPrerequisiteMapping(ManifoldCF.readString(is));
      // Attempt to save this connection
      save(conn);
    }
  }

  /** Obtain a list of the mapping connections, ordered by name,
  * excluding those that would form a prerequisite loop if chosen.
  *@param startingConnectionName is the name of the connection we would be starting with.
  * Pass null for all connections.
  *@return an array of connection objects.
  */
  public IMappingConnection[] getAllNonLoopingConnections(String startingConnectionName)
    throws ManifoldCFException
  {
    // The point of this method is to prune connections from the list that, if a new prereq was established
    // between the specified starting connection name and the listed mapping connection, a loop would develop.
    IMappingConnection[] connections = getAllConnections();
    // Degenerate case: no (existing) starting point.
    if (startingConnectionName == null)
      return connections;
    
    List<IMappingConnection> finalConnections = new ArrayList<IMappingConnection>();
    
    Map<String,IMappingConnection> connectionMap = new HashMap<String,IMappingConnection>();
    for (IMappingConnection thisConnection : connections)
    {
      connectionMap.put(thisConnection.getName(), thisConnection);
    }
    
    for (IMappingConnection connectionToEvaluate : connections)
    {
      // The algorithm we want is as follows (from Wikipedia):
      //
      // L <- Empty list where we put the sorted elements
      // Q <- Set of all nodes with no incoming edges
      // while Q is non-empty do
      //    remove a node n from Q
      //    insert n into L
      //    for each node m with an edge e from n to m do
      //        remove edge e from the graph
      //        if m has no other incoming edges then
      //            insert m into Q
      // if graph has edges then
      //    output error message (graph has a cycle)
      // else 
      //    output message (proposed topologically sorted order: L)
      //
      // In order to "remove" a link, we have to either keep a list of links we've already processed, or copy the
      // structure to another one where we *can* remove links.  I opt for the former.
      // The second issue is that we need to generate Q up front.  This is easy enough; just keep a hash of connections
      // that have not been referenced (yet), and remove connections from the hash as refs are found.
      // Also interesting: we don't actually need to keep L.
      
      // The set of nodes with NO incoming edges
      Set<String> Q = new HashSet<String>();
      // The set of links in the graph
      Set<String> links = new HashSet<String>();
      // The count of the number of incoming links to a node
      Map<String,Integer> incomingCount = new HashMap<String,Integer>();

      for (int i = 0; i < connections.length; i++)
      {
        Q.add(connections[i].getName());
      }
      for (int i = 0; i < connections.length; i++)
      {
        String connectionName = connections[i].getName();
        String prerequisite;
        if (connectionName.equals(startingConnectionName))
        {
          // We ignore what's saved and instead substitute a hypothetical
          // There is a "proposed" edge from the current connection, ending at connectionToEvaluate,
          // so for the purpose of determining cycles, add that one too into the graph
          prerequisite = connectionToEvaluate.getName();
        }
        else
        {
          // Use what's actually saved
          prerequisite = connections[i].getPrerequisiteMapping();
        }
        if (prerequisite != null)
        {
          Integer x = incomingCount.get(prerequisite);
          if (x == null)
            incomingCount.put(prerequisite,new Integer(1));
          else
            incomingCount.put(prerequisite,new Integer(x.intValue()+1));
          Q.remove(prerequisite);
          links.add(connectionName + ":" + prerequisite);
        }
      }


      // Now, repeat until Q is empty
      while (!Q.isEmpty())
      {
        Iterator<String> iter = Q.iterator();
        String checkConnectionName = iter.next();
        Q.remove(checkConnectionName);
        // Get prereqs for the connection, those that are still in the graph
        String s;
        if (checkConnectionName.equals(startingConnectionName))
        {
          // We have a hypothetical link to evaluate and remove
          s = connectionToEvaluate.getName();
        }
        else
        {
          IMappingConnection sourceConnection = connectionMap.get(checkConnectionName);
          s = sourceConnection.getPrerequisiteMapping();
        }
        
        if (s != null)
        {
          String edgeName = checkConnectionName + ":" + s;
          if (links.contains(edgeName))
          {
            // Remove edgeName from graph
            links.remove(edgeName);
            // If s has no other incoming edges then insert it into Q
            Integer x = incomingCount.get(s);
            if (x.intValue() == 1)
            {
              incomingCount.remove(s);
              Q.add(s);
            }
            else
              incomingCount.put(s,new Integer(x.intValue() - 1));
            
          }
        }
      }
      
      // If there are links, we've failed to remove them and thus they must be part of cycles
      if (links.isEmpty())
      {
        // No cycles.  Add this connection to the final list.
        finalConnections.add(connectionToEvaluate);
      }
    }
    return finalConnections.toArray(new IMappingConnection[0]);
  }

  /** Obtain a list of the repository connections, ordered by name.
  *@return an array of connection objects.
  */
  @Override
  public IMappingConnection[] getAllConnections()
    throws ManifoldCFException
  {
    lockManager.enterReadLock(mappingsLock);
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getMappingConnectionsKey());
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
      lockManager.leaveReadLock(mappingsLock);
    }
  }

  /** Load a mapping connection by name.
  *@param name is the name of the mapping connection.
  *@return the loaded connection object, or null if not found.
  */
  @Override
  public IMappingConnection load(String name)
    throws ManifoldCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  protected final static int FETCH_MAX = 200;

  /** Load multiple mapping connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  @Override
  public IMappingConnection[] loadMultiple(String[] names)
    throws ManifoldCFException
  {
    IMappingConnection[] rval = new IMappingConnection[names.length];
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
  
  protected int loadMultipleInternal(IMappingConnection[] rval, int outputIndex, String[] fetchNames, int inputIndex, int length)
    throws ManifoldCFException
  {
    // Build description objects
    MappingConnectionDescription[] objectDescriptions = new MappingConnectionDescription[length];
    StringSetBuffer ssb = new StringSetBuffer();
    for (int i = 0; i < length; i++)
    {
      ssb.clear();
      String name = fetchNames[inputIndex + i];
      ssb.add(getMappingConnectionKey(name));
      objectDescriptions[i] = new MappingConnectionDescription(name,new StringSet(ssb));
    }

    MappingConnectionExecutor exec = new MappingConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    IMappingConnection[] results = exec.getResults();
    for (IMappingConnection result : results)
    {
      rval[outputIndex++] = result;
    }
    return outputIndex;
  }

  /** Create a new repository connection object.
  *@return the new object.
  */
  @Override
  public IMappingConnection create()
    throws ManifoldCFException
  {
    MappingConnection rval = new MappingConnection();
    return rval;
  }

  /** Save a mapping connection object.
  *@param object is the object to save.
  *@return true if the object is created, false otherwise.
  */
  @Override
  public boolean save(IMappingConnection object)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getMappingConnectionsKey());
    ssb.add(getMappingConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    while (true)
    {
      long sleepAmt = 0L;
      try
      {
        lockManager.enterNonExWriteLock(mappingsLock);
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
                  throw new ManifoldCFException("Mapping connection '"+object.getName()+"' no longer exists");
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
          lockManager.leaveNonExWriteLock(mappingsLock);
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

    // Grab authority connection manager handle, to check on legality of deletion.
    IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getMappingConnectionsKey());
    ssb.add(getMappingConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);

    lockManager.enterNonExWriteLock(mappingsLock);
    try
    {
      ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
      try
      {
        beginTransaction();
        try
        {
          // Check if any other mapping refers to this connection name
          if (isReferenced(name))
            throw new ManifoldCFException("Can't delete mapping connection '"+name+"': existing mapping connections refer to it");
          if (authManager.isMappingReferenced(name))
            throw new ManifoldCFException("Can't delete mapping connection '"+name+"': existing authority connections refer to it");
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
      lockManager.leaveNonExWriteLock(mappingsLock);
    }
  }

  /** Get the mapping connection name column.
  *@return the name column.
  */
  @Override
  public String getMappingNameColumn()
  {
    return nameField;
  }

  /** Return true if the specified mapping name is referenced.
  *@param mappingName is the mapping name.
  *@return true if referenced, false otherwise.
  */
  protected boolean isReferenced(String mappingName)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getMappingConnectionsKey());
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

  /** Construct a key which represents the general list of mapping connectors.
  *@return the cache key.
  */
  protected static String getMappingConnectionsKey()
  {
    return CacheKeyFactory.makeMappingConnectionsKey();
  }

  /** Construct a key which represents an individual mapping connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getMappingConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeMappingConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple mapping connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding mapping connection objects.
  */
  protected MappingConnection[] getMappingConnectionsMultiple(String[] connectionNames)
    throws ManifoldCFException
  {
    MappingConnection[] rval = new MappingConnection[connectionNames.length];
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
      int maxIn = maxClauseGetMappingConnectionsChunk();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getMappingConnectionsChunk(rval,returnIndex,params);
          params.clear();
          j = 0;
        }
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getMappingConnectionsChunk(rval,returnIndex,params);
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

  /** Find the maximum number of clauses for getMappingConnectionsChunk.
  */
  protected int maxClauseGetMappingConnectionsChunk()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Read a chunk of mapping connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param params is the set of parameters.
  */
  protected void getMappingConnectionsChunk(MappingConnection[] rval, Map returnIndex, ArrayList params)
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
      MappingConnection rc = new MappingConnection();
      rc.setIsNew(false);
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rc.setClassName((String)row.getValue(classNameField));
      rc.setMaxConnections((int)((Long)row.getValue(maxCountField)).longValue());
      rc.setPrerequisiteMapping((String)row.getValue(mappingField));
      String xml = (String)row.getValue(configField);
      if (xml != null && xml.length() > 0)
        rc.getConfigParams().fromXML(xml);
      rval[index] = rc;
    }
  }

  // The cached instance will be a MappingConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for a mapping connection object.
  */
  protected static class MappingConnectionDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public MappingConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("mappingconnectioncache");
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
      if (!(o instanceof MappingConnectionDescription))
        return false;
      MappingConnectionDescription d = (MappingConnectionDescription)o;
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

  /** This is the executor object for locating mapping connection objects.
  */
  protected static class MappingConnectionExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected MappingConnectionManager thisManager;
    protected MappingConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the ToolManager.
    *@param objectDescriptions are the object descriptions.
    */
    public MappingConnectionExecutor(MappingConnectionManager manager, MappingConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new MappingConnection[objectDescriptions.length];
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
    public MappingConnection[] getResults()
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
        MappingConnectionDescription desc = (MappingConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getMappingConnectionsMultiple(connectionNames);
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
      MappingConnectionDescription objectDesc = (MappingConnectionDescription)objectDescription;
      MappingConnection ci = (MappingConnection)cachedObject;

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
