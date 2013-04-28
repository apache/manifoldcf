/* $Id: PrereqEventManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import java.util.*;

/** This class manages the prerequisite event table.
* This table lists the prerequisite event rows that must NOT exist in order for a jobqueue entry to be queued for processing.  If a prerequisite event row does, in fact,
* exist, then queuing and processing do not take place until that row is cleared.
* 
* <br><br>
* <b>prereqevents</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>owner</td><td>BIGINT</td><td>Reference:jobqueue.id</td></tr>
* <tr><td>eventname</td><td>VARCHAR(255)</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class PrereqEventManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: PrereqEventManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Field names
  public final static String ownerField = "owner";
  public final static String eventNameField = "eventname";

  /** Constructor.
  *@param database is the database handle.
  */
  public PrereqEventManager(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"prereqevents");
  }

  /** Install or upgrade this table.
  */
  public void install(String ownerTableName, String ownerColumn)
    throws ManifoldCFException
  {
    // Standard practice: Outer loop for upgrade support.
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(ownerField,new ColumnDescription("BIGINT",false,false,ownerTableName,ownerColumn,false));
        map.put(eventNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Schema upgrade goes here, when needed.
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerField});
      // eventNameIndex was proposed by postgresql team, but it did not help, so I am removing it.
      //IndexDescription eventNameIndex = new IndexDescription(false,new String[]{eventNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        //else if (eventNameIndex != null && id.equals(eventNameIndex))
        //  eventNameIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);
      //if (eventNameIndex != null)
      //  performAddIndex(null,eventNameIndex);

      break;
    }
  }

  /** Uninstall.
  */
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

  /** Delete specified rows, based on jobqueue criteria. */
  public void deleteRows(String parentTableName, String joinField, String parentCriteria, ArrayList list)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder();
    ArrayList newList = new ArrayList();
    
    sb.append("WHERE EXISTS(SELECT 'x' FROM ").append(parentTableName).append(" WHERE ")
      .append(buildConjunctionClause(newList,new ClauseDescription[]{
        new JoinClause(joinField,getTableName() + "." + ownerField)}));

    if (parentCriteria != null)
    {
      sb.append(" AND ").append(parentCriteria);
      if (list != null)
        newList.addAll(list);
    }
    
    sb.append(")");
    
    performDelete(sb.toString(),newList,null);
    noteModifications(0,0,1);
  }

  /** Delete specified rows, as directly specified without a join. */
  public void deleteRows(ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(ownerField,list)});
    performDelete("WHERE "+query,newList,null);
    noteModifications(0,0,1);
  }

  /** Delete rows pertaining to a single entry */
  public void deleteRows(Long recordID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(ownerField,recordID)});
    performDelete(" WHERE "+query,list,null);
    noteModifications(0,0,1);
  }

  /** Add rows pertaining to a single entry */
  public void addRows(Long recordID, String[] eventNames)
    throws ManifoldCFException
  {
    if (eventNames != null)
    {
      int i = 0;
      while (i < eventNames.length)
      {
        HashMap map = new HashMap();
        map.put(ownerField,recordID);
        map.put(eventNameField,eventNames[i++]);
        performInsert(map,null);
      }
      noteModifications(i,0,0);
    }
  }


}
