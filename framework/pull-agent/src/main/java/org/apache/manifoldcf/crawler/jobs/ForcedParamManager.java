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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class manages the "jobforcedparams" table, which contains the forced parameters for each job.
* 
* <br><br>
* <b>jobforcedparams</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownerid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
* <tr><td>paramname</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>paramvalue</td><td>VARCHAR(255)</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class ForcedParamManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Schema
  public final static String ownerIDField = "ownerid";
  public final static String paramNameField = "paramname";
  public final static String paramValueField = "paramvalue";

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database instance.
  */
  public ForcedParamManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobforcedparams");
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
        map.put(paramNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(paramValueField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, as needed
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(true,new String[]{ownerIDField,paramNameField});

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
  *@return a map of param name to param set.
  */
  public Map<String,Set<String>> readRows(Long id)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(id);
    IResultSet set = performQuery("SELECT "+paramNameField+","+paramValueField+" FROM "+getTableName()+" WHERE "+ownerIDField+"=?",list,
      null,null);
    Map<String,Set<String>> rval = new HashMap<String,Set<String>>();
    if (set.getRowCount() == 0)
      return rval;
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      String paramName = (String)row.getValue(paramNameField);
      String paramValue = (String)row.getValue(paramValueField);
      if (paramValue == null)
        paramValue = "";
      Set<String> valueSet = rval.get(paramName);
      if (valueSet == null)
      {
        valueSet = new HashSet<String>();
        rval.put(paramName,valueSet);
      }
      valueSet.add(paramValue);
    }
    return rval;
  }

  /** Fill in a set of param maps corresponding to a set of owner id's.
  *@param returnValues is a map keyed by ownerID, with value of JobDescription.
  *@param ownerIDList is the list of owner id's.
  *@param ownerIDParams is the corresponding set of owner id parameters.
  */
  public void getRows(Map<Long,JobDescription> returnValues, String ownerIDList, ArrayList ownerIDParams)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+ownerIDField+" IN ("+ownerIDList+")",ownerIDParams,
      null,null);
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      Long ownerID = (Long)row.getValue(ownerIDField);
      String paramName = (String)row.getValue(paramNameField);
      String paramValue = (String)row.getValue(paramValueField);
      if (paramValue == null)
        paramValue = "";
      returnValues.get(ownerID).addForcedMetadataValue(paramName,paramValue);
    }
  }

  /** Write a filter list into the database.
  *@param ownerID is the owning identifier.
  *@param list is the job description to write hopcount filters for.
  */
  public void writeRows(Long ownerID, IJobDescription list)
    throws ManifoldCFException
  {
    Map map = new HashMap();
    beginTransaction();
    try
    {
      Map<String,Set<String>> forcedMetadata = list.getForcedMetadata();
      for (String paramName : forcedMetadata.keySet())
      {
        Set<String> forcedValue = forcedMetadata.get(paramName);
        for (String paramValue : forcedValue)
        {
          map.clear();
          map.put(paramNameField,paramName);
          map.put(paramValueField,paramValue);
          map.put(ownerIDField,ownerID);
          performInsert(map,null);
        }
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
