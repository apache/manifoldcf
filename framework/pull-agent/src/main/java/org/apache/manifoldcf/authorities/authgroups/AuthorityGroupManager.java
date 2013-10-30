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
package org.apache.manifoldcf.authorities.authgroups;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;
import org.apache.manifoldcf.authorities.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

import org.apache.manifoldcf.crawler.interfaces.IRepositoryConnectionManager;
import org.apache.manifoldcf.crawler.interfaces.RepositoryConnectionManagerFactory;

/** Implementation of the authority group manager functionality.
 * 
 * <br><br>
 * <b>authgroups</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>groupname</td><td>VARCHAR(32)</td><td>Primary Key</td></tr>
 * <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class AuthorityGroupManager extends org.apache.manifoldcf.core.database.BaseTable implements IAuthorityGroupManager
{
  public static final String _rcsid = "@(#)$Id$";

  protected final static String nameField = "groupname";
  protected final static String descriptionField = "description";

  // Cache manager
  ICacheManager cacheManager;
  // Thread context
  IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  */
  public AuthorityGroupManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"authgroups");

    cacheManager = CacheManagerFactory.make(threadContext);
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
        performCreate(map,null);
      }
      else
      {
        // Upgrade, when needed
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
    IAuthorityGroup[] list = getAllGroups();
    // Write the number of groups
    ManifoldCF.writeDword(os,list.length);
    // Loop through the list and write the individual group info
    int i = 0;
    while (i < list.length)
    {
      IAuthorityGroup conn = list[i++];
      ManifoldCF.writeString(os,conn.getName());
      ManifoldCF.writeString(os,conn.getDescription());
    }
  }

  /** Import configuration */
  @Override
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException
  {
    int version = ManifoldCF.readDword(is);
    if (version < 1 || version > 1)
      throw new java.io.IOException("Unknown authority group configuration version: "+Integer.toString(version));
    int count = ManifoldCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      IAuthorityGroup conn = create();
      conn.setName(ManifoldCF.readString(is));
      conn.setDescription(ManifoldCF.readString(is));
      // Attempt to save this connection
      save(conn);
      i++;
    }
  }

  /** Obtain a list of the authority grouops, ordered by name.
  *@return an array of connection objects.
  */
  @Override
  public IAuthorityGroup[] getAllGroups()
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getAuthorityGroupsKey());
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

  /** Load an authority group by name.
  *@param name is the name of the authority group.
  *@return the loaded group object, or null if not found.
  */
  @Override
  public IAuthorityGroup load(String name)
    throws ManifoldCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  /** Load multiple authority groups by name.
  *@param names are the names to load.
  *@return the loaded group objects.
  */
  @Override
  public IAuthorityGroup[] loadMultiple(String[] names)
    throws ManifoldCFException
  {
    // Build description objects
    AuthorityGroupDescription[] objectDescriptions = new AuthorityGroupDescription[names.length];
    int i = 0;
    StringSetBuffer ssb = new StringSetBuffer();
    while (i < names.length)
    {
      ssb.clear();
      ssb.add(getAuthorityGroupKey(names[i]));
      objectDescriptions[i] = new AuthorityGroupDescription(names[i],new StringSet(ssb));
      i++;
    }

    AuthorityGroupExecutor exec = new AuthorityGroupExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    return exec.getResults();
  }

  /** Create a new authority group object.
  *@return the new object.
  */
  @Override
  public IAuthorityGroup create()
    throws ManifoldCFException
  {
    AuthorityGroup rval = new AuthorityGroup();
    return rval;
  }

  /** Save an authority group object.
  *@param object is the object to save.
  *@return true if the object is created, false otherwise.
  */
  @Override
  public boolean save(IAuthorityGroup object)
    throws ManifoldCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityGroupsKey());
    ssb.add(getAuthorityGroupKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    while (true)
    {
      long sleepAmt = 0L;
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

            boolean isCreated;
            
            if (set.getRowCount() > 0)
            {
              // If the object is supposedly new, it is bad that we found one that already exists.
              if (isNew)
                throw new ManifoldCFException("Authority group '"+object.getName()+"' already exists");
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
                throw new ManifoldCFException("Authority group '"+object.getName()+"' no longer exists");
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

  /** Delete an authority group.
  *@param name is the name of the group to delete.  If the
  * name does not exist, no error is returned.
  */
  @Override
  public void delete(String name)
    throws ManifoldCFException
  {
    // Grab repository connection manager handle, to check on legality of deletion.
    IRepositoryConnectionManager repoManager = RepositoryConnectionManagerFactory.make(threadContext);
    IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
    
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getAuthorityGroupsKey());
    ssb.add(getAuthorityGroupKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
    try
    {
      beginTransaction();
      try
      {
        // Check if anything refers to this group name
        if (repoManager.isGroupReferenced(name))
          throw new ManifoldCFException("Can't delete authority group '"+name+"': existing repository connections refer to it");
        if (authManager.isGroupReferenced(name))
          throw new ManifoldCFException("Can't delete authority group '"+name+"': existing authority connections refer to it");
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

  /** Get the authority group name column.
  *@return the name column.
  */
  @Override
  public String getGroupNameColumn()
  {
    return nameField;
  }

  /** Get the authority connection description column.
  *@return the description column.
  */
  @Override
  public String getGroupDescriptionColumn()
  {
    return descriptionField;
  }

  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // repository connections.

  /** Construct a key which represents the general list of repository connectors.
  *@return the cache key.
  */
  protected static String getAuthorityGroupsKey()
  {
    return CacheKeyFactory.makeAuthorityGroupsKey();
  }

  /** Construct a key which represents an individual authority group.
  *@param groupName is the name of the group.
  *@return the cache key.
  */
  protected static String getAuthorityGroupKey(String groupName)
  {
    return CacheKeyFactory.makeAuthorityGroupKey(groupName);
  }

  // Other utility methods.

  /** Fetch multiple authority groups at a single time.
  *@param groupNames are a list of group names.
  *@return the corresponding authority group objects.
  */
  protected AuthorityGroup[] getAuthorityGroupsMultiple(String[] groupNames)
    throws ManifoldCFException
  {
    AuthorityGroup[] rval = new AuthorityGroup[groupNames.length];
    HashMap returnIndex = new HashMap();
    int i = 0;
    while (i < groupNames.length)
    {
      rval[i] = null;
      returnIndex.put(groupNames[i],new Integer(i));
      i++;
    }
    beginTransaction();
    try
    {
      i = 0;
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = maxClauseGetAuthorityGroupsChunk();
      while (i < groupNames.length)
      {
        if (j == maxIn)
        {
          getAuthorityGroupsChunk(rval,returnIndex,params);
          params.clear();
          j = 0;
        }
        params.add(groupNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getAuthorityGroupsChunk(rval,returnIndex,params);
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
  protected int maxClauseGetAuthorityGroupsChunk()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Read a chunk of authority groups.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param params is the set of parameters.
  */
  protected void getAuthorityGroupsChunk(AuthorityGroup[] rval, Map returnIndex, ArrayList params)
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
      AuthorityGroup rc = new AuthorityGroup();
      rc.setIsNew(false);
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rval[index] = rc;
    }
  }

  // The cached instance will be a AuthorityGroup.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for an authority group object.
  */
  protected static class AuthorityGroupDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription
  {
    protected String groupName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public AuthorityGroupDescription(String groupName, StringSet invKeys)
    {
      super("authoritygroupcache");
      this.groupName = groupName;
      criticalSectionName = getClass().getName()+"-"+groupName;
      cacheKeys = invKeys;
    }

    public String getGroupName()
    {
      return groupName;
    }

    public int hashCode()
    {
      return groupName.hashCode();
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof AuthorityGroupDescription))
        return false;
      AuthorityGroupDescription d = (AuthorityGroupDescription)o;
      return d.groupName.equals(groupName);
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

  /** This is the executor object for locating authority group objects.
  */
  protected static class AuthorityGroupExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected AuthorityGroupManager thisManager;
    protected AuthorityGroup[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the AuthorityGroupManager.
    *@param objectDescriptions are the object descriptions.
    */
    public AuthorityGroupExecutor(AuthorityGroupManager manager, AuthorityGroupDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new AuthorityGroup[objectDescriptions.length];
      int i = 0;
      while (i < objectDescriptions.length)
      {
        returnMap.put(objectDescriptions[i].getGroupName(),new Integer(i));
        i++;
      }
    }

    /** Get the result.
    *@return the looked-up or read cached instances.
    */
    public AuthorityGroup[] getResults()
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
      // Turn the object descriptions into the parameters for the AuthorityGroup requests
      String[] groupNames = new String[objectDescriptions.length];
      int i = 0;
      while (i < groupNames.length)
      {
        AuthorityGroupDescription desc = (AuthorityGroupDescription)objectDescriptions[i];
        groupNames[i] = desc.getGroupName();
        i++;
      }

      return thisManager.getAuthorityGroupsMultiple(groupNames);
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
      AuthorityGroupDescription objectDesc = (AuthorityGroupDescription)objectDescription;
      AuthorityGroup ci = (AuthorityGroup)cachedObject;

      // Duplicate it!
      if (ci != null)
        ci = ci.duplicate();

      // In order to make the indexes line up, we need to use the hashtable built by
      // the constructor.
      returnValues[((Integer)returnMap.get(objectDesc.getGroupName())).intValue()] = ci;
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
