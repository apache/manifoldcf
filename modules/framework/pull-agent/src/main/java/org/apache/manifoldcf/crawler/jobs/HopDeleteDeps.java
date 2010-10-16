/* $Id: HopDeleteDeps.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;

/** This class manages the table that keeps track of link deletion dependencies for cached
* hopcounts.
*/
public class HopDeleteDeps extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: HopDeleteDeps.java 988245 2010-08-23 18:39:35Z kwright $";

  // Field names
  public static final String jobIDField = "jobid";
  public static final String ownerIDField = "ownerid";
  public static final String linkTypeField = "linktype";
  public static final String parentIDHashField = "parentidhash";
  public static final String childIDHashField = "childidhash";

  /** Constructor.
  *@param database is the database handle.
  */
  public HopDeleteDeps(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"hopdeletedeps");
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn, String hopCountTable, String idColumn)
    throws ManifoldCFException
  {
    // Standard practice: outer retry loop
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(jobIDField,new ColumnDescription("BIGINT",false,false,jobsTable,jobsColumn,false));
        map.put(ownerIDField,new ColumnDescription("BIGINT",false,false,hopCountTable,idColumn,false));
        map.put(linkTypeField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(parentIDHashField,new ColumnDescription("VARCHAR(40)",false,false,null,null,false));
        map.put(childIDHashField,new ColumnDescription("VARCHAR(40)",false,true,null,null,false));

        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, if needed.
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerIDField});
      IndexDescription jobIndex = new IndexDescription(false,new String[]{jobIDField});
      IndexDescription completeIndex = new IndexDescription(true,new String[]{ownerIDField,linkTypeField,parentIDHashField,childIDHashField});
      IndexDescription jobChildIndex = new IndexDescription(false,new String[]{jobIDField,childIDHashField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        else if (jobIndex != null && id.equals(jobIndex))
          jobIndex = null;
        else if (completeIndex != null && id.equals(completeIndex))
          completeIndex = null;
        else if (jobChildIndex != null && id.equals(jobChildIndex))
          jobChildIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);
      if (jobIndex != null)
        performAddIndex(null,jobIndex);
      if (completeIndex != null)
        performAddIndex(null,completeIndex);
      if (jobChildIndex != null)
        performAddIndex(null,jobChildIndex);

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

  /** Analyze job tables that need analysis.
  */
  public void analyzeTables()
    throws ManifoldCFException
  {
    long startTime = System.currentTimeMillis();
    Logging.perf.debug("Beginning to analyze hopdeletedeps table");
    analyzeTable();
    Logging.perf.debug("Done analyzing hopdeletedeps table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
  }

  /** Delete a job. */
  public void deleteJob(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    performDelete("WHERE "+jobIDField+"=?",list,null);
    // Log one event - it may not be enough, but it's the best we can do without overhead
    noteModifications(0,0,1);
  }

  /** Remove rows that correspond to specific hopcount records.
  */
  public void removeMarkedRows(String parentTable, String parentIDHashField, String query, ArrayList queryList)
    throws ManifoldCFException
  {
    // This didn't perform very well.
    //performDelete("WHERE EXISTS(SELECT 'x' FROM "+parentTable+" t0 WHERE t0."+parentIDField+"="+ownerIDField+
    //      " AND t0."+markField+"=?)",list,null);
    performDelete("WHERE "+ownerIDField+" IN(SELECT "+parentIDHashField+" FROM "+parentTable+" WHERE "+query+")",
      queryList,null);
    // Log one event - it may not be enough, but it's the best we can do without overhead
    noteModifications(0,0,1);
  }

  /** Delete rows related to specified owners.  The list of
  * specified owners does not exceed the maximum database in-clause
  * size.
  */
  public void deleteOwnerRows(Long[] ownerIDs)
    throws ManifoldCFException
  {
    StringBuffer sb = new StringBuffer("WHERE ");
    sb.append(ownerIDField).append(" IN(");
    ArrayList list = new ArrayList();
    int i = 0;
    while (i < ownerIDs.length)
    {
      if (i > 0)
        sb.append(",");
      sb.append("?");
      list.add(ownerIDs[i++]);
    }
    sb.append(")");
    performDelete(sb.toString(),list,null);
    noteModifications(0,0,ownerIDs.length);
  }

  /** Get the delete dependencies for an owner.
  *@return the links
  */
  public DeleteDependency[] getDeleteDependencies(Long ownerID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(ownerID);
    IResultSet set = performQuery("SELECT "+linkTypeField+", "+parentIDHashField+", "+
      childIDHashField+" FROM "+getTableName()+" WHERE "+ownerIDField+"=?",list,null,null);
    DeleteDependency[] rval = new DeleteDependency[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i] = new DeleteDependency((String)row.getValue(linkTypeField),
        (String)row.getValue(parentIDHashField),
        (String)row.getValue(childIDHashField));
      i++;
    }
    return rval;
  }

  /** Delete a dependency */
  public void deleteDependency(Long ownerID, DeleteDependency dd)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    StringBuffer sb = new StringBuffer("WHERE ");
    sb.append(ownerIDField).append("=? AND ");
    list.add(ownerID);
    if (dd.getLinkType().length() > 0)
    {
      sb.append(linkTypeField).append("=? AND ");
      list.add(dd.getLinkType());
    }
    else
      sb.append(linkTypeField).append(" IS NULL AND ");
    sb.append(parentIDHashField).append("=? AND ");
    list.add(dd.getParentIDHash());
    if (dd.getChildIDHash().length() > 0)
    {
      sb.append(childIDHashField).append("=?");
      list.add(dd.getChildIDHash());
    }
    else
      sb.append(childIDHashField).append(" IS NULL");
    performDelete(sb.toString(),list,null);
    noteModifications(0,0,1);
  }

  /** Write a delete dependency.
  */
  public void writeDependency(Long ownerID, Long jobID, DeleteDependency dd)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(jobIDField,jobID);
    map.put(ownerIDField,ownerID);
    if (dd.getLinkType().length() > 0)
      map.put(linkTypeField,dd.getLinkType());
    map.put(parentIDHashField,dd.getParentIDHash());
    if (dd.getChildIDHash().length() > 0)
    {
      map.put(childIDHashField,dd.getChildIDHash());
    }
    performInsert(map,null);
    noteModifications(1,0,0);
  }

}
