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
package org.apache.lcf.authorities.authconnmgr;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.authorities.interfaces.*;
import java.util.*;
import org.apache.lcf.authorities.interfaces.CacheKeyFactory;

/** This is the implementation of that authority connector manager.
*/
public class AuthorityConnectorManager extends org.apache.lcf.core.database.BaseTable implements IAuthorityConnectorManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Fields
  protected static final String descriptionField = "description";
  protected static final String classNameField = "classname";

  // Thread context
  protected IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database handle.
  */
  public AuthorityConnectorManager(IThreadContext threadContext, IDBInterface database)
    throws LCFException
  {
    super(database,"authconnectors");
    this.threadContext = threadContext;
  }


  /** Install or upgrade.
  */
  public void install()
    throws LCFException
  {
    // Always use a loop, in case there's upgrade retries needed.
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(classNameField,new ColumnDescription("VARCHAR(255)",true,false,null,null,false));

        performCreate(map,null);
      }
      else
      {
        // Schema upgrade code goes here, if needed.
      }

      // Index management
      IndexDescription descriptionIndex = new IndexDescription(true,new String[]{descriptionField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (descriptionIndex != null && id.equals(descriptionIndex))
          descriptionIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (descriptionIndex != null)
        performAddIndex(null,descriptionIndex);

      break;
    }
  }


  /** Uninstall.  This also unregisters all connectors.
  */
  public void deinstall()
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());

    // First, go through all registered connectors.  This is all inside a transaction.
    beginTransaction();
    try
    {
      IResultSet set = performQuery("SELECT "+classNameField+" FROM "+getTableName(),null,null,null);
      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i++);
        String className = row.getValue(classNameField).toString();
        // Call the deinstall method
        AuthorityConnectorFactory.deinstall(threadContext,className);
      }
      performDrop(invKeys);
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

  /** Register a new connector.
  * The connector's install method will also be called.
  *@param description is the description to use in the UI.
  *@param className is the class name.
  */
  public void registerConnector(String description, String className)
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    beginTransaction();
    try
    {
      performLock();
      // See if already there.
      ArrayList params = new ArrayList();
      params.add(className);
      IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+classNameField+"=? FOR UPDATE",params,null,null);
      HashMap map = new HashMap();
      map.put(descriptionField,description);
      if (set.getRowCount() == 0)
      {
        // Insert it into table first.
        map.put(classNameField,className);
        performInsert(map,invKeys);
      }
      else
      {
        performUpdate(map,"WHERE "+classNameField+"=?",params,invKeys);
      }

      // Either way, we must do the install/upgrade itself.
      AuthorityConnectorFactory.install(threadContext,className);
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

  /** Unregister a connector.
  * The connector's deinstall method will also be called.
  *@param className is the class name of the connector to unregister.
  */
  public void unregisterConnector(String className)
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    beginTransaction();
    try
    {
      // Uninstall first
      AuthorityConnectorFactory.deinstall(threadContext,className);

      removeConnector(className);
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

  /** Remove a connector.
  * Call this when the connector cannot be instantiated.
  *@param className is the connector class to remove.
  */
  public void removeConnector(String className)
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    ArrayList list = new ArrayList();
    list.add(className);
    performDelete("WHERE "+classNameField+"=?",list,invKeys);
  }

  /** Get ordered list of connectors.
  *@return a resultset with the columns "description" and "classname".
  * These will be ordered by description.
  */
  public IResultSet getConnectors()
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());

    return performQuery("SELECT "+descriptionField+" AS description,"+classNameField+" AS classname FROM "+
      getTableName()+" ORDER BY "+descriptionField+" ASC",null,invKeys,null);
  }

  /** Get a description given a class name.
  *@param className is the class name.
  *@return the description, or null if the class is not registered.
  */
  public String getDescription(String className)
    throws LCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());

    ArrayList list = new ArrayList();
    list.add(className);
    IResultSet set = performQuery("SELECT "+descriptionField+" FROM "+
      getTableName()+" WHERE "+classNameField+"=?",list,invKeys,null);
    if (set.getRowCount() == 0)
      return null;
    IResultRow row = set.getRow(0);
    return row.getValue(descriptionField).toString();
  }

  /** Check if a particular connector is installed or not.
  *@param className is the class name of the connector.
  *@return true if installed, false otherwise.
  */
  public boolean isInstalled(String className)
    throws LCFException
  {
    // Use the global table key; that's good enough because we don't expect stuff to change out from under very often.
    StringSet invKeys = new StringSet(getCacheKey());

    ArrayList list = new ArrayList();
    list.add(className);
    IResultSet set = performQuery("SELECT * FROM "+
      getTableName()+" WHERE "+classNameField+"=?",list,invKeys,null);
    return set.getRowCount() > 0;
  }

  // Protected methods

  /** Get the cache key for the connector manager table.
  *@return the cache key
  */
  protected String getCacheKey()
  {
    return CacheKeyFactory.makeTableKey(null,getTableName(),getDBInterface().getDatabaseName());
  }

}
