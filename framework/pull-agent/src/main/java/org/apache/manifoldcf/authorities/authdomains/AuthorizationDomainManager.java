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
package org.apache.manifoldcf.authorities.authdomains;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;
import org.apache.manifoldcf.authorities.interfaces.CacheKeyFactory;

/** This is the implementation of that authority connector manager.
 * 
 * <br><br>
 * <b>authdomains</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>domainname</td><td>VARCHAR(255)</td><td>Primary Key</td></tr>
 * </table>
 * <br><br>
 * 
 */
public class AuthorizationDomainManager extends org.apache.manifoldcf.core.database.BaseTable implements IAuthorizationDomainManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Fields
  protected static final String descriptionField = "description";
  protected static final String domainNameField = "domainname";

  // Thread context
  protected final IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database handle.
  */
  public AuthorizationDomainManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"authdomains");
    this.threadContext = threadContext;
  }


  /** Install or upgrade.
  */
  @Override
  public void install()
    throws ManifoldCFException
  {
    // Always use a loop, in case there's upgrade retries needed.
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(domainNameField,new ColumnDescription("VARCHAR(255)",true,false,null,null,false));

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


  /** Uninstall.
  */
  @Override
  public void deinstall()
    throws ManifoldCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    performDrop(invKeys);
  }

  /** Register a new domain.
  *@param description is the description to use in the UI.
  *@param domainName is the internal domain name used by the authority service.
  */
  @Override
  public void registerDomain(String description, String domainName)
    throws ManifoldCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    beginTransaction();
    try
    {
      // See if already there.
      ArrayList params = new ArrayList();
      params.add(domainName);
      IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+domainNameField+"=? FOR UPDATE",params,null,null);
      HashMap map = new HashMap();
      map.put(descriptionField,description);
      if (set.getRowCount() == 0)
      {
        // Insert it into table first.
        map.put(domainNameField,domainName);
        performInsert(map,invKeys);
      }
      else
      {
        performUpdate(map,"WHERE "+domainNameField+"=?",params,invKeys);
      }
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

  /** Unregister a domain.
  * This may fail if any authority connections refer to the domain.
  *@param domainName is the internal domain name to unregister.
  */
  @Override
  public void unregisterDomain(String domainName)
    throws ManifoldCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());
    ArrayList list = new ArrayList();
    list.add(domainName);
    performDelete("WHERE "+domainNameField+"=?",list,invKeys);
  }

  /** Get ordered list of domains.
  *@return a resultset with the columns "description" and "domainname".
  * These will be ordered by description.
  */
  @Override
  public IResultSet getDomains()
    throws ManifoldCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());

    return performQuery("SELECT "+descriptionField+" AS description,"+domainNameField+" AS domainname FROM "+
      getTableName()+" ORDER BY "+descriptionField+" ASC",null,invKeys,null);
  }

  /** Get a description given a domain name.
  *@param domainName is the domain name.
  *@return the description, or null if the domain is not registered.
  */
  @Override
  public String getDescription(String domainName)
    throws ManifoldCFException
  {
    StringSet invKeys = new StringSet(getCacheKey());

    ArrayList list = new ArrayList();
    list.add(domainName);
    IResultSet set = performQuery("SELECT "+descriptionField+" FROM "+
      getTableName()+" WHERE "+domainNameField+"=?",list,invKeys,null);
    if (set.getRowCount() == 0)
      return null;
    IResultRow row = set.getRow(0);
    String x = (String)row.getValue(descriptionField);
    if (x == null)
      return "";
    return x;
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
