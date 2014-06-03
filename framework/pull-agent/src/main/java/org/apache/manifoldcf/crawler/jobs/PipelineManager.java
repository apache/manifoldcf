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

/** This class manages the "pipelines" table, which contains the ordered transformation
* connections and their specification data.
* 
* <br><br>
* <b>jobpipelines</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>ownerid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
* <tr><td>ordinal</td><td>BIGINT</td><td></td></tr>
* <tr><td>transformationname</td><td>VARCHAR(32)</td><td></td></tr>
* <tr><td>transformationdesc</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>transformationspec</td><td>LONGTEXT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class PipelineManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Schema
  public final static String ownerIDField = "ownerid";
  public final static String ordinalField = "ordinal";
  public final static String transformationNameField = "transformationname";
  public final static String transformationDescriptionField = "transformationdesc";
  public final static String transformationSpecField = "transformationspec";

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database instance.
  */
  public PipelineManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"jobpipelines");
  }

  /** Install or upgrade.
  *@param ownerTable is the name of the table that owns this one.
  *@param owningTablePrimaryKey is the primary key of the owning table.
  */
  public void install(String ownerTable, String owningTablePrimaryKey, String transformationTableName, String transformationTableNameField)
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
        map.put(transformationNameField,new ColumnDescription("VARCHAR(32)",false,false,transformationTableName,transformationTableNameField,false));
        map.put(transformationDescriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(transformationSpecField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code goes here, if needed.
      }

      // Index management
      IndexDescription ownerIndex = new IndexDescription(false,new String[]{ownerIDField});

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

  /** Fill in a set of pipelines corresponding to a set of owner id's.
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
      String transformationName = (String)row.getValue(transformationNameField);
      String transformationDesc = (String)row.getValue(transformationDescriptionField);
      String transformationSpec = (String)row.getValue(transformationSpecField);
      JobDescription jd = returnValues.get(ownerID);
      jd.addPipelineStage(transformationName,transformationDesc).fromXML(transformationSpec);
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
    if (set.getRowCount() != job.countPipelineStages())
      return false;
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      String connectionName = (String)row.getValue(transformationNameField);
      String spec = (String)row.getValue(transformationSpecField);
      if (spec == null)
        spec = "";
      if (!job.getPipelineStageConnectionName(i).equals(connectionName))
        return false;
      if (!job.getPipelineStageSpecification(i).toXML().equals(spec))
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
      for (int i = 0; i < job.countPipelineStages(); i++)
      {
        String pipelineConnectionName = job.getPipelineStageConnectionName(i);
        String pipelineStageDescription = job.getPipelineStageDescription(i);
        OutputSpecification os = job.getPipelineStageSpecification(i);
        map.clear();
        map.put(ownerIDField,ownerID);
        map.put(ordinalField,new Long((long)i));
        map.put(transformationNameField,pipelineConnectionName);
        if (pipelineStageDescription != null && pipelineStageDescription.length() > 0)
          map.put(transformationDescriptionField,pipelineStageDescription);
        map.put(transformationSpecField,os.toXML());
        performInsert(map,null);
        i++;
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
