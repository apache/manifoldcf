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
* <tr><td>prerequisite</td><td>BIGINT</td><td></td></tr>
* <tr><td>outputname</td><td>VARCHAR(32)</td><td></td></tr>
* <tr><td>transformationname</td><td>VARCHAR(32)</td><td></td></tr>
* <tr><td>connectiondesc</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>connectionspec</td><td>LONGTEXT</td><td></td></tr>
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
  public final static String prerequisiteField = "prerequisite";
  public final static String outputNameField = "outputname";
  public final static String transformationNameField = "transformationname";
  public final static String connectionDescriptionField = "connectiondesc";
  public final static String connectionSpecField = "connectionspec";

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
  public void install(String ownerTable, String owningTablePrimaryKey,
    String outputTableName, String outputTableNameField,
    String transformationTableName, String transformationTableNameField)
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
        map.put(prerequisiteField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(outputNameField,new ColumnDescription("VARCHAR(32)",false,true,outputTableName,outputTableNameField,false));
        map.put(transformationNameField,new ColumnDescription("VARCHAR(32)",false,true,transformationTableName,transformationTableNameField,false));
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
      IndexDescription transformationNameIndex = new IndexDescription(false,new String[]{transformationNameField});
      IndexDescription outputNameIndex = new IndexDescription(false,new String[]{outputNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (ownerIndex != null && id.equals(ownerIndex))
          ownerIndex = null;
        else if (transformationNameIndex != null && id.equals(transformationNameIndex))
          transformationNameIndex = null;
        else if (outputNameIndex != null && id.equals(outputNameIndex))
          outputNameIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (ownerIndex != null)
        performAddIndex(null,ownerIndex);
      if (transformationNameIndex != null)
        performAddIndex(null,transformationNameIndex);
      if (outputNameIndex != null)
        performAddIndex(null,outputNameIndex);

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

  /** Build a query clause matching a set of transformation connection names.
  */
  public void buildTransformationQueryClause(StringBuilder query, ArrayList params,
    String parentIDField, List<String> connectionNames)
  {
    query.append("SELECT 'x' FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(params,new ClauseDescription[]{
      new JoinClause(parentIDField,ownerIDField),
      new MultiClause(transformationNameField,connectionNames)}));
  }

  /** Build a query clause matching a set of output connection names.
  */
  public void buildOutputQueryClause(StringBuilder query, ArrayList params,
    String parentIDField, List<String> connectionNames)
  {
    query.append("SELECT 'x' FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(params,new ClauseDescription[]{
      new JoinClause(parentIDField,ownerIDField),
      new MultiClause(outputNameField,connectionNames)}));
  }
  
  /** Get all the transformation connection names for a job.
  *@param ownerID is the job ID.
  *@return the set of connection names.
  */
  public String[] getTransformationConnectionNames(Long ownerID)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    StringBuilder query = new StringBuilder("SELECT ");
    query.append(transformationNameField).append(" FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(ownerIDField,ownerID),
      new NullCheckClause(transformationNameField,false)}));
    IResultSet set = performQuery(query.toString(),newList,null,null);
    String[] rval = new String[set.getRowCount()];
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(transformationNameField);
    }
    return rval;
  }

  /** Get all the output connection names for a job.
  *@param ownerID is the job ID.
  *@return the set of connection names.
  */
  public String[] getOutputConnectionNames(Long ownerID)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    StringBuilder query = new StringBuilder("SELECT ");
    query.append(outputNameField).append(" FROM ").append(getTableName()).append(" WHERE ");
    query.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(ownerIDField,ownerID),
      new NullCheckClause(outputNameField,false)}));
    IResultSet set = performQuery(query.toString(),newList,null,null);
    String[] rval = new String[set.getRowCount()];
    for (int i = 0; i < set.getRowCount(); i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(outputNameField);
    }
    return rval;
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
      Long prerequisite = (Long)row.getValue(prerequisiteField);
      String outputName = (String)row.getValue(outputNameField);
      String transformationName = (String)row.getValue(transformationNameField);
      String transformationDesc = (String)row.getValue(connectionDescriptionField);
      String transformationSpec = (String)row.getValue(connectionSpecField);
      boolean isOutput = outputName != null && outputName.length() > 0;
      int prerequisiteValue = (prerequisite == null)?-1:(int)prerequisite.longValue();
      JobDescription jd = returnValues.get(ownerID);
      jd.addPipelineStage(prerequisiteValue,isOutput,isOutput?outputName:transformationName,transformationDesc).fromXML(transformationSpec);
    }
  }

  /** Compare rows in job description with what's currently in the database.
  *@param ownerID is the owning identifier.
  *@param job is a job description.
  */
  public boolean compareRows(Long ownerID, IJobDescription job)
    throws ManifoldCFException
  {
    // Compute a set of the outputs
    Set<String> outputSet = new HashSet<String>();
    for (int i = 0; i < job.countPipelineStages(); i++)
    {
      if (job.getPipelineStageIsOutputConnection(i))
      {
        String outputName = job.getPipelineStageConnectionName(i);
        if (outputSet.contains(outputName))
          throw new ManifoldCFException("Output name '"+outputName+"' is duplicated within job; not allowed");
        outputSet.add(outputName);
      }
    }
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
      String outputConnectionName = (String)row.getValue(outputNameField);
      boolean isOutputConnection = outputConnectionName != null && outputConnectionName.length() > 0;
      if (isOutputConnection)
      {
        if (!outputSet.contains(outputConnectionName))
          throw new ManifoldCFException("Output name '"+outputConnectionName+"' removed from job; not allowed");
      }
      String transformationConnectionName = (String)row.getValue(transformationNameField);
      Long prerequisite = (Long)row.getValue(prerequisiteField);
      String spec = (String)row.getValue(connectionSpecField);
      if (spec == null)
        spec = "";
      int prerequisiteValue = (prerequisite==null)?-1:(int)prerequisite.longValue();
      if (job.getPipelineStagePrerequisite(i) != prerequisiteValue)
        return false;
      if (job.getPipelineStageIsOutputConnection(i) != isOutputConnection)
        return false;
      if (!job.getPipelineStageConnectionName(i).equals(isOutputConnection?outputConnectionName:transformationConnectionName))
        return false;
      if (!job.getPipelineStageSpecification(i).toXML().equals(spec))
        return false;
    }
    return true;
  }
  
  /** Write an output stage (part of the upgrade code).
  */
  public void writeOutputStage(Long ownerID, String outputConnectionName, String outputSpecification)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(ownerIDField,ownerID);
    map.put(ordinalField,new Long(0));
    map.put(outputNameField,outputConnectionName);
    map.put(connectionSpecField,outputSpecification);
    performInsert(map,null);
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
        boolean isOutput = job.getPipelineStageIsOutputConnection(i);
        int prerequisite = job.getPipelineStagePrerequisite(i);
        String pipelineConnectionName = job.getPipelineStageConnectionName(i);
        String pipelineStageDescription = job.getPipelineStageDescription(i);
        Specification os = job.getPipelineStageSpecification(i);
        map.clear();
        map.put(ownerIDField,ownerID);
        map.put(ordinalField,new Long((long)i));
        if (prerequisite != -1)
          map.put(prerequisiteField,new Long(prerequisite));
        if (isOutput)
          map.put(outputNameField,pipelineConnectionName);
        else
          map.put(transformationNameField,pipelineConnectionName);
        if (pipelineStageDescription != null && pipelineStageDescription.length() > 0)
          map.put(connectionDescriptionField,pipelineStageDescription);
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
