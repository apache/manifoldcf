/* $Id: HopFilterManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class manages the "hopfilters" table, which contains the hopcount filters for each job.
* It's separated from the main jobs table because we will need multiple hop filters per job.
* 
* <br><br>
* <b>jobhopfilters</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownerid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
* <tr><td>linktype</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>maxhops</td><td>BIGINT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class HopFilterManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: HopFilterManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Schema
  public final static String ownerIDField = "ownerid";
  public final static String linkTypeField = "linktype";
  public final static String maxHopsField = "maxhops";

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database instance.
  */
  public HopFilterManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobhopfilters");
  }

  /** Install or upgrade.
  *@param ownerTable is the name of the table that owns this one.
  *@param owningTablePrimaryKey is the primary key of the owning table.
  */
  public void install(String ownerTable, String owningTablePrimaryKey)
    throws ManifoldCFException
  {
    // Standard practice: outer loop
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(ownerIDField,new ColumnDescription("BIGINT",false,false,ownerTable,owningTablePrimaryKey,false));
        // Null link types are NOT allowed here.  The restrictions can only be made on a real link type.
        map.put(linkTypeField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(maxHopsField,new ColumnDescription("BIGINT",false,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, as needed
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(true,new String[]{ownerIDField,linkTypeField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);

      break;
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Read rows for a given owner id.
  *@param id is the owner id.
  *@return a map of link type to max hop count (as a Long).
  */
  public Map readRows(Long id)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(id);
    IResultSet set = performQuery("SELECT "+linkTypeField+","+maxHopsField+" FROM "+getTableName()+" WHERE "+ownerIDField+"=?",list,
      null,null);
    Map rval = new HashMap();
    if (set.getRowCount() == 0)
      return rval;
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      String linkType = (String)row.getValue(linkTypeField);
      Long max = (Long)row.getValue(maxHopsField);
      rval.put(linkType,max);
      i++;
    }
    return rval;
  }

  /** Fill in a set of filters corresponding to a set of owner id's.
  *@param returnValues is a map keyed by ownerID, with value of JobDescription.
  *@param ownerIDList is the list of owner id's.
  *@param ownerIDParams is the corresponding set of owner id parameters.
  */
  public void getRows(Map<Long,JobDescription> returnValues, String ownerIDList, ArrayList ownerIDParams)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+ownerIDField+" IN ("+ownerIDList+")",ownerIDParams,
      null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      Long ownerID = (Long)row.getValue(ownerIDField);
      String linkType = (String)row.getValue(linkTypeField);
      Long maxHops = (Long)row.getValue(maxHopsField);
      returnValues.get(ownerID).addHopCountFilter(linkType,maxHops);
      i++;
    }
  }

  /** Compare a filter list against what's in a job description.
  *@param ownerID is the owning identifier.
  *@param list is the job description to write hopcount filters for.
  */
  public boolean compareRows(Long ownerID, IJobDescription list)
    throws ManifoldCFException
  {
    // Compare hopcount filter criteria.
    Map filterRows = readRows(ownerID);
    Map newFilterRows = list.getHopCountFilters();
    if (filterRows.size() != newFilterRows.size())
      return false;
    for (String linkType : (Collection<String>)filterRows.keySet())
    {
      Long oldCount = (Long)filterRows.get(linkType);
      Long newCount = (Long)newFilterRows.get(linkType);
      if (oldCount == null || newCount == null)
        return false;
      if (oldCount.longValue() != newCount.longValue())
        return false;
    }
    return true;
  }
  
  /** Write a filter list into the database.
  *@param ownerID is the owning identifier.
  *@param list is the job description to write hopcount filters for.
  */
  public void writeRows(Long ownerID, IJobDescription list)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      int i = 0;
      HashMap map = new HashMap();
      Map filters = list.getHopCountFilters();
      Iterator iter = filters.keySet().iterator();
      while (iter.hasNext())
      {
        String linkType = (String)iter.next();
        Long maxHops = (Long)filters.get(linkType);
        map.clear();
        map.put(linkTypeField,linkType);
        map.put(maxHopsField,maxHops);
        map.put(ownerIDField,ownerID);
        performInsert(map,null);
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

  /** Delete rows.
  *@param ownerID is the owner whose rows to delete.
  */
  public void deleteRows(Long ownerID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(ownerID);
    performDelete("WHERE "+ownerIDField+"=?",list,null);
  }

}
