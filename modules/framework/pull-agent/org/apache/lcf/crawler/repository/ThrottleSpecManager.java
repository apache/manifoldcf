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

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import java.util.*;

/** This class manages the "throttlespec" table, which contains the throttle specifications for each connection.
* These are basically tuples consisting of a regexp and a fetch rate.  There's a description of each tuple,
* so that a person can attach a description of what they are attempting to do with each limit.
*/
public class ThrottleSpecManager extends org.apache.lcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Schema
  public final static String ownerNameField = "ownername";
  public final static String descriptionField = "description";
  public final static String matchField = "matchstring";
  public final static String throttleField = "throttle";

  /** Constructor.
  *@param database is the database instance.
  */
  public ThrottleSpecManager(IDBInterface database)
    throws LCFException
  {
    super(database,"throttlespec");
  }

  /** Install or upgrade.
  *@param ownerTable is the name of the table that owns this one.
  *@param owningTablePrimaryKey is the primary key of the owning table.
  */
  public void install(String ownerTable, String owningTablePrimaryKey)
    throws LCFException
  {
    // Always use a loop, in case upgrade needs it.
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(ownerNameField,new ColumnDescription("VARCHAR(32)",false,false,ownerTable,owningTablePrimaryKey,false));
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(matchField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(throttleField,new ColumnDescription("FLOAT",false,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, if needed.
        if (existing.get(matchField) == null)
        {
          // Need to rename the "match" column as the "matchstring" column.
          HashMap map = new HashMap();
          map.put(matchField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
          performAlter(map,null,null,null);
          performModification("UPDATE "+getTableName()+" SET "+matchField+"=match",null,null);
          ArrayList list = new ArrayList();
          list.add("match");
          performAlter(null,null,list,null);
        }
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerNameField});

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
    throws LCFException
  {
    performDrop(null);
  }

  /** Read rows for a given owner name.
  *@param name is the owner name.
  *@return a list, with columns: "description", "match", and "value".
  */
  public IResultSet readRows(String name)
    throws LCFException
  {
    ArrayList list = new ArrayList();
    list.add(name);
    return performQuery("SELECT "+descriptionField+" AS description,"+matchField+" AS match,"+throttleField+" AS value FROM "+
      getTableName()+" WHERE "+ownerNameField+"=?",list,null,null);
  }

  /** Fill in a set of throttles corresponding to a set of connection names.
  *@param connections is the set of connections to fill in.
  *@param indexMap maps the connection name to the index in the connections array.
  *@param ownerNameList is the list of connection names.
  *@param ownerNameParams is the corresponding set of connection name parameters.
  */
  public void getRows(IRepositoryConnection[] connections, Map indexMap, String ownerNameList, ArrayList ownerNameParams)
    throws LCFException
  {
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+ownerNameField+" IN ("+ownerNameList+")",ownerNameParams,
      null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      String ownerName = (String)row.getValue(ownerNameField);
      int index = ((Integer)indexMap.get(ownerName)).intValue();
      String description = (String)row.getValue(descriptionField);
      String match = (String)row.getValue(matchField);
      float throttle = new Float(row.getValue(throttleField).toString()).floatValue();
      connections[index].addThrottleValue(match,description,throttle);
      i++;
    }
  }

  /** Write a throttle spec into the database.
  *@param owner is the owning connection name.
  *@param connection is the connection to write throttle specs for.
  */
  public void writeRows(String owner, IRepositoryConnection connection)
    throws LCFException
  {
    beginTransaction();
    try
    {
      int i = 0;
      HashMap map = new HashMap();
      String[] matches = connection.getThrottles();
      while (i < matches.length)
      {
        String match = matches[i++];
        String description = connection.getThrottleDescription(match);
        float value = connection.getThrottleValue(match);
        map.clear();
        map.put(matchField,match);
        if (description != null && description.length() > 0)
          map.put(descriptionField,description);
        map.put(throttleField,new Float(value));
        map.put(ownerNameField,owner);
        performInsert(map,null);
      }
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

  /** Delete rows.
  *@param owner is the owner whose rows to delete.
  */
  public void deleteRows(String owner)
    throws LCFException
  {
    ArrayList list = new ArrayList();
    list.add(owner);
    performDelete("WHERE "+ownerNameField+"=?",list,null);
  }

}
