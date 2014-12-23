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

/** This class manages the "jobnotifications" table, which contains the ordered list of notification
* connections and their specification data.
* 
* <br><br>
* <b>jobnotifications</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownerid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
* <tr><td>ordinal</td><td>BIGINT</td><td></td></tr>
* <tr><td>notificationname</td><td>VARCHAR(32)</td><td></td></tr>
* <tr><td>connectiondesc</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>connectionspec</td><td>LONGTEXT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class NotificationManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Schema
  public final static String ownerIDField = "ownerid";
  public final static String ordinalField = "ordinal";
  public final static String notificationNameField = "notificationname";
  public final static String connectionDescriptionField = "connectiondesc";
  public final static String connectionSpecField = "connectionspec";

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database instance.
  */
  public NotificationManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobnotifications");
  }

  /** Install or upgrade.
  *@param ownerTable is the name of the table that owns this one.
  *@param owningTablePrimaryKey is the primary key of the owning table.
  */
  public void install(String ownerTable, String owningTablePrimaryKey,
    String notificationTableName, String notificationTableNameField)
    throws ManifoldCFException
  {
    // Standard practice: Outer loop to support upgrades
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(ownerIDField,new ColumnDescription("BIGINT",false,false,ownerTable,owningTablePrimaryKey,false));
        map.put(ordinalField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(notificationNameField,new ColumnDescription("VARCHAR(32)",false,true,notificationTableName,notificationTableNameField,false));
        map.put(connectionDescriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(connectionSpecField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, if needed.
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerIDField});
      IndexDescription notificationNameIndex = new IndexDescription(false,new String[]{notificationNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        else if (notificationNameIndex != null && id.equals(notificationNameIndex))
          notificationNameIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);
      if (notificationNameIndex != null)
        performAddIndex(null,notificationNameIndex);

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

  /** Build a query clause matching a set of notification connection names.
  */
  public void buildNotificationQueryClause(StringBuilder query, ArrayList params,
    String parentIDField, List<String> connectionNames)
  {
    query.append("SELECT 'x' FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(params,new ClauseDescription[]{
      new JoinClause(parentIDField,ownerIDField),
      new MultiClause(notificationNameField,connectionNames)}));
  }

  /** Get all the notification connection names for a job.
  *@param ownerID is the job ID.
  *@return the set of connection names.
  */
  public String[] getNotificationConnectionNames(Long ownerID)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    StringBuilder query = new StringBuilder("SELECT ");
    query.append(notificationNameField).append(" FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(ownerIDField,ownerID)}));
    IResultSet set = performQuery(query.toString(),newList,null,null);
    String[] rval = new String[set.getRowCount()];
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(notificationNameField);
    }
    return rval;
  }

  /** Fill in a set of notifications corresponding to a set of owner id's.
  *@param returnValues is a map keyed by ownerID, with value of JobDescription.
  *@param ownerIDList is the list of owner id's.
  *@param ownerIDParams is the corresponding set of owner id parameters.
  */
  public void getRows(Map<Long,JobDescription> returnValues, String ownerIDList, ArrayList ownerIDParams)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+ownerIDField+" IN ("+ownerIDList+") ORDER BY "+ordinalField+" ASC",ownerIDParams,
      null,null);
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      Long ownerID = (Long)row.getValue(ownerIDField);
      String notificationName = (String)row.getValue(notificationNameField);
      String notificationDesc = (String)row.getValue(connectionDescriptionField);
      String notificationSpec = (String)row.getValue(connectionSpecField);
      JobDescription jd = returnValues.get(ownerID);
      jd.addNotification(notificationName,notificationDesc).fromXML(notificationSpec);
    }
  }

  /** Compare rows in job description with what's currently in the database.
  *@param ownerID is the owning identifier.
  *@param job is a job description.
  */
  public boolean compareRows(Long ownerID, IJobDescription job)
    throws ManifoldCFException
  {
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(ownerIDField,ownerID)});
    IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
      query+" ORDER BY "+ordinalField+" ASC",params,null,null);
    if (set.getRowCount() != job.countNotifications())
      return false;
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      String notificationConnectionName = (String)row.getValue(notificationNameField);
      String spec = (String)row.getValue(connectionSpecField);
      if (spec == null)
        spec = "";
      if (!job.getNotificationConnectionName(i).equals(notificationConnectionName))
        return false;
      if (!job.getNotificationSpecification(i).toXML().equals(spec))
        return false;
    }
    return true;
  }
  
  /** Write a pipeline list into the database.
  *@param ownerID is the owning identifier.
  *@param job is the job description that is the source of the pipeline.
  */
  public void writeRows(Long ownerID, IJobDescription job)
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      HashMap map = new HashMap();
      for (int i = 0; i < job.countNotifications(); i++)
      {
        String notificationConnectionName = job.getNotificationConnectionName(i);
        String notificationDescription = job.getNotificationDescription(i);
        Specification os = job.getNotificationSpecification(i);
        map.clear();
        map.put(ownerIDField,ownerID);
        map.put(ordinalField,new Long((long)i));
        map.put(notificationNameField,notificationConnectionName);
        if (notificationDescription != null && notificationDescription.length() > 0)
          map.put(connectionDescriptionField,notificationDescription);
        map.put(connectionSpecField,os.toXML());
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
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(ownerIDField,ownerID)});
      
    performDelete("WHERE "+query,list,null);
  }

}
