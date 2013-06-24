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

/** This class manages the "mappingprereq" table, which contains the mapping connection prerequisites for each connection.
* 
* <br><br>
* <b>mappingprereq</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownername</td><td>VARCHAR(32)</td><td>Reference:repoconnections.connectionname</td></tr>
* <tr><td>prereq</td><td>VARCHAR(32)</td><td>Reference:repoconnections.connectionname</td></tr>
* </table>
* <br><br>
* 
*/
public class MappingPrereqManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Schema
  public final static String ownerNameField = "ownername";
  public final static String prereqField = "prereq";

  /** Constructor.
  *@param database is the database instance.
  */
  public MappingPrereqManager(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"mappingprereq");
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
        map.put(prereqField,new ColumnDescription("VARCHAR(32)",false,false,ownerTable,owningTablePrimaryKey,false));
        performCreate(map,null);
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
  *@return a list, with columns: "prereq"
  */
  public IResultSet readRows(String name)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(ownerNameField,name)});
    return performQuery("SELECT "+prereqField+" AS prereq FROM "+
      getTableName()+" WHERE "+query,list,null,null);
  }

  /** Calculate the maximum number of clauses we can use with getRows.
  */
  public int maxClauseGetRows()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
    
  /** Fill in a set of prereqs corresponding to a set of connection names.
  *@param connections is the set of connections to fill in.
  *@param indexMap maps the connection name to the index in the connections array.
  *@param ownerNameParams is the corresponding set of connection name parameters.
  */
  public void getRows(IMappingConnection[] connections, Map indexMap, ArrayList ownerNameParams)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(ownerNameField,ownerNameParams)});
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+query,list,
      null,null);
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      String ownerName = (String)row.getValue(ownerNameField);
      int index = ((Integer)indexMap.get(ownerName)).intValue();
      String prereq = (String)row.getValue(prereqField);
      connections[index].getPrerequisites().add(prereq);
    }
  }

  /** Write a set of prereqs into the database.
  *@param owner is the owning connection name.
  *@param connection is the connection to write prereqs for.
  */
  public void writeRows(String owner, IMappingConnection connection)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      HashMap map = new HashMap();
      Set<String> prereqs = connection.getPrerequisites();
      for (String prereq : prereqs)
      {
        map.clear();
        map.put(prereqField,prereq);
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
