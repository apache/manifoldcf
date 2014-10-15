/* $Id: ThrottleSpecManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.repository;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class manages the "throttlespec" table, which contains the throttle specifications for each connection.
* These are basically tuples consisting of a regexp and a fetch rate.  There's a description of each tuple,
* so that a person can attach a description of what they are attempting to do with each limit.
* 
* <br><br>
* <b>throttlespec</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownername</td><td>VARCHAR(32)</td><td>Reference:repoconnections.connectionname</td></tr>
* <tr><td>description</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>matchstring</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>throttle</td><td>FLOAT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class ThrottleSpecManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: ThrottleSpecManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Schema
  public final static String ownerNameField = "ownername";
  public final static String descriptionField = "description";
  public final static String matchField = "matchstring";
  public final static String throttleField = "throttle";

  /** Constructor.
  *@param database is the database instance.
  */
  public ThrottleSpecManager(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"throttlespec");
  }

  /** Install or upgrade.
  *@param ownerTable is the name of the table that owns this one.
  *@param owningTablePrimaryKey is the primary key of the owning table.
  */
  public void install(String ownerTable, String owningTablePrimaryKey)
    throws ManifoldCFException
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
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Read rows for a given owner name.
  *@param name is the owner name.
  *@return a list, with columns: "description", "match", and "value".
  */
  public IResultSet readRows(String name)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(ownerNameField,name)});
    return performQuery("SELECT "+descriptionField+" AS description,"+matchField+" AS match,"+throttleField+" AS value FROM "+
      getTableName()+" WHERE "+query,list,null,null);
  }

  /** Calculate the maximum number of clauses we can use with getRows.
  */
  public int maxClauseGetRows()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Fill in a set of throttles corresponding to a set of connection names.
  *@param connections is the set of connections to fill in.
  *@param indexMap maps the connection name to the index in the connections array.
  *@param ownerNameParams is the corresponding set of connection name parameters.
  */
  public void getRows(IRepositoryConnection[] connections, Map indexMap, ArrayList ownerNameParams)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(ownerNameField,ownerNameParams)});
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+query,list,
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
    throws ManifoldCFException
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
  *@param owner is the owner whose rows to delete.
  */
  public void deleteRows(String owner)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(ownerNameField,owner)});
    performDelete("WHERE "+query,list,null);
  }

}
