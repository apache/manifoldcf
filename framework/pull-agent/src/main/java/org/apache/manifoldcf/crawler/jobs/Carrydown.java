/* $Id: Carrydown.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.util.*;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

/** This class manages the table that keeps track of intrinsic relationships between documents.
 * 
 * <br><br>
 * <b>carrydown</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>jobid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
 * <tr><td>parentidhash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>childidhash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>dataname</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>datavaluehash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>datavalue</td><td>LONGTEXT</td><td></td></tr>
 * <tr><td>isnew</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>processid</td><td>VARCHAR(16)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
 */
public class Carrydown extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: Carrydown.java 988245 2010-08-23 18:39:35Z kwright $";

  // Field names
  public static final String jobIDField = "jobid";
  public static final String parentIDHashField = "parentidhash";
  public static final String childIDHashField = "childidhash";
  public static final String dataNameField = "dataname";
  public static final String dataValueHashField = "datavaluehash";
  public static final String dataValueField = "datavalue";
  public static final String newField = "isnew";
  public static final String processIDField = "processid";

  /** The standard value for the "isnew" field.  Means that the link existed prior to this scan, and no new link
  * was found yet. */
  protected static final int ISNEW_BASE = 0;
  /** This value means that the link is brand-new; it did not exist before this pass. */
  protected static final int ISNEW_NEW = 1;
  /** This value means that the link existed before, and has been found during this scan. */
  protected static final int ISNEW_EXISTING = 2;

  // Map from string character to link status
  protected static Map isNewMap;
  static
  {
    isNewMap = new HashMap();
    isNewMap.put("B",new Integer(ISNEW_BASE));
    isNewMap.put("N",new Integer(ISNEW_NEW));
    isNewMap.put("E",new Integer(ISNEW_EXISTING));
  }

  /** Constructor.
  *@param database is the database handle.
  */
  public Carrydown(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"carrydown");
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ManifoldCFException
  {
    // Standard practice: Outer loop, to support upgrade requirements.
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        // I'm going to allow the parent to be null, which basically will be able to represent carry-down from the seeding
        // process to the seed, in case this ever arises.
        //
        // I am also going to allow null data values.
        HashMap map = new HashMap();
        map.put(jobIDField,new ColumnDescription("BIGINT",false,false,jobsTable,jobsColumn,false));
        map.put(parentIDHashField,new ColumnDescription("VARCHAR(40)",false,true,null,null,false));
        map.put(childIDHashField,new ColumnDescription("VARCHAR(40)",false,false,null,null,false));
        map.put(dataNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(dataValueHashField,new ColumnDescription("VARCHAR(40)",false,true,null,null,false));
        map.put(dataValueField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        map.put(newField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(processIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));

        performCreate(map,null);

      }
      else
      {
        // Upgrade code goes here, if needed.
      }

      // Now do index management

      IndexDescription uniqueIndex = new IndexDescription(true,new String[]{jobIDField,parentIDHashField,childIDHashField,dataNameField,dataValueHashField});
      IndexDescription jobChildDataIndex = new IndexDescription(false,new String[]{jobIDField,childIDHashField,dataNameField});
      IndexDescription newIndex = new IndexDescription(false,new String[]{newField,processIDField});

      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (uniqueIndex != null && id.equals(uniqueIndex))
          uniqueIndex = null;
        else if (jobChildDataIndex != null && id.equals(jobChildDataIndex))
          jobChildDataIndex = null;
        else if (newIndex != null && id.equals(newIndex))
          newIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Create the indexes we are missing

      if (jobChildDataIndex != null)
        performAddIndex(null,jobChildDataIndex);

      if (newIndex != null)
        performAddIndex(null,newIndex);

      // This index is the constraint.  Only one row per job,dataname,datavalue,parent,and child.
      if (uniqueIndex != null)
        performAddIndex(null,uniqueIndex);

      // Install/upgrade complete
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
    Logging.perf.debug("Beginning to analyze carrydown table");
    analyzeTable();
    Logging.perf.debug("Done analyzing carrydown table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
  }

  /** Delete an owning job (and clean up the corresponding carrydown rows).
  */
  public void deleteOwner(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
    performDelete("WHERE "+query,list,null);
  }

  // The strategy here is to leave all rows that have a given document as a parent labelled as "BASE" at the start of the
  // processing of that parent.  As data are encountered, the values get written as "NEW" or flipped to "EXISTING".
  // When the document's processing has been completed, another method is called
  // that will remove all rows that belong to the parent which are still labelled "BASE", and will map the other rows that
  // belong to the parent back to the "BASE" state.
  //
  //  If the daemon is aborted and restarted, the "new" rows should be deleted, and the EXISTING rows should be reset to
  // BASE, in order to restore the system to a good base state.
  //

  /** Reset, at startup time.
  *@param processID is the process ID.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    // Delete "new" rows
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(newField,statusToString(ISNEW_NEW)),
      new UnitaryClause(processIDField,processID)});
    performDelete("WHERE "+query,list,null);

    // Convert "existing" rows to base
    map.put(newField,statusToString(ISNEW_BASE));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(newField,statusToString(ISNEW_EXISTING)),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Clean up after all process IDs.
  */
  public void restart()
    throws ManifoldCFException
  {
    // Delete "new" rows
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(newField,statusToString(ISNEW_NEW))});
    performDelete("WHERE "+query,list,null);

    // Convert "existing" rows to base
    map.put(newField,statusToString(ISNEW_BASE));
    list.clear();
    query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(newField,statusToString(ISNEW_EXISTING))});
    performUpdate(map,"WHERE "+query,list,null);
  }
  
  /** Reset, at startup time, entire cluster
  */
  public void restartCluster()
    throws ManifoldCFException
  {
    // Does nothing
  }

  /** Add carrydown data for a given parent/child pair.
  *
  *@return true if new carrydown data was recorded; false otherwise.
  */
  public boolean recordCarrydownData(Long jobID, String parentDocumentIDHash, String childDocumentIDHash,
    String[] documentDataNames, String[][] documentDataValueHashes, Object[][] documentDataValues, String processID)
    throws ManifoldCFException
  {
    return recordCarrydownDataMultiple(jobID,parentDocumentIDHash,new String[]{childDocumentIDHash},
      new String[][]{documentDataNames},new String[][][]{documentDataValueHashes},new Object[][][]{documentDataValues},processID)[0];
  }

  /** Add carrydown data to the table.
  */
  public boolean[] recordCarrydownDataMultiple(Long jobID, String parentDocumentIDHash, String[] childDocumentIDHashes,
    String[][] dataNames, String[][][] dataValueHashes, Object[][][] dataValues, String processID)
    throws ManifoldCFException
  {

    // Need to go into a transaction because we need to distinguish between update and insert.
    HashMap duplicateRemoval = new HashMap();
    HashMap presentMap = new HashMap();

    int maxClause = getMaxOrClause();
    StringBuilder sb = new StringBuilder();
    ArrayList list = new ArrayList();
    int i = 0;
    int k = 0;
    // Keep track of the data items that have been seen vs. those that were unseen.
    while (k < childDocumentIDHashes.length)
    {
      String childDocumentIDHash = childDocumentIDHashes[k];

      // Loop through data names and values for this document
      String[] documentDataNames = dataNames[k];
      String[][] documentDataValueHashes = dataValueHashes[k];
      Object[][] documentDataValues = dataValues[k];
      k++;

      int q = 0;
      while (q < documentDataNames.length)
      {
        String documentDataName = documentDataNames[q];
        String[] documentDataValueHashSet = documentDataValueHashes[q];
        Object[] documentDataValueSet = documentDataValues[q];
        q++;

        if (documentDataValueHashSet != null)
        {
          int p = 0;
          while (p < documentDataValueHashSet.length)
          {
            String documentDataValueHash = documentDataValueHashSet[p];
            Object documentDataValue = documentDataValueSet[p];
            // blank values equivalent to null
            if (documentDataValueHash != null && documentDataValueHash.length() == 0)
              documentDataValueHash = null;
            // Build a hash record
            ValueRecord vr = new ValueRecord(childDocumentIDHash,
              documentDataName,documentDataValueHash,documentDataValue);
            if (duplicateRemoval.get(vr) != null)
              continue;
            duplicateRemoval.put(vr,vr);
            if (i == maxClause)
            {
              // Do the query and record the results
              performExistsCheck(presentMap,sb.toString(),list);
              i = 0;
              sb.setLength(0);
              list.clear();
            }
            if (i > 0)
              sb.append(" OR ");
            
            sb.append(buildConjunctionClause(list,new ClauseDescription[]{
              new UnitaryClause(jobIDField,jobID),
              new UnitaryClause(parentIDHashField,parentDocumentIDHash),
              new UnitaryClause(childIDHashField,childDocumentIDHash),
              new UnitaryClause(dataNameField,documentDataName),
              (documentDataValueHash==null)?
                new NullCheckClause(dataValueHashField,true):
                new UnitaryClause(dataValueHashField,documentDataValueHash)}));

            i++;
            p++;
          }
        }
      }
    }
    if (i > 0)
      performExistsCheck(presentMap,sb.toString(),list);

    // Go through the list again, and based on the results above, decide to do either an insert or
    // an update.  Keep track of this information also, so we can build the return array when done.

    HashMap insertHappened = new HashMap();

    int j = 0;
    Iterator iter = duplicateRemoval.keySet().iterator();
    while (iter.hasNext())
    {
      ValueRecord childDocumentRecord = (ValueRecord)iter.next();

      String childDocumentIDHash = childDocumentRecord.getDocumentIDHash();

      HashMap map = new HashMap();
      String dataName = childDocumentRecord.getDataName();
      String dataValueHash = childDocumentRecord.getDataValueHash();
      Object dataValue = childDocumentRecord.getDataValue();

      if (presentMap.get(childDocumentRecord) == null)
      {
        map.put(jobIDField,jobID);
        map.put(parentIDHashField,parentDocumentIDHash);
        map.put(childIDHashField,childDocumentIDHash);
        map.put(dataNameField,dataName);
        if (dataValueHash != null)
        {
          map.put(dataValueHashField,dataValueHash);
          map.put(dataValueField,dataValue);
        }

        map.put(newField,statusToString(ISNEW_NEW));
        map.put(processIDField,processID);
        performInsert(map,null);
        noteModifications(1,0,0);
        insertHappened.put(childDocumentIDHash,new Boolean(true));
      }
      else
      {
        sb = new StringBuilder("WHERE ");
        ArrayList updateList = new ArrayList();
        sb.append(buildConjunctionClause(updateList,new ClauseDescription[]{
          new UnitaryClause(jobIDField,jobID),
          new UnitaryClause(parentIDHashField,parentDocumentIDHash),
          new UnitaryClause(childIDHashField,childDocumentIDHash),
          new UnitaryClause(dataNameField,dataName),
          (dataValueHash==null)?
            new NullCheckClause(dataValueHashField,true):
            new UnitaryClause(dataValueHashField,dataValueHash)}));

        /*
        sb.append(jobIDField).append("=? AND ")
          .append(parentIDHashField).append("=? AND ")
          .append(childIDHashField).append("=? AND ")
          .append(dataNameField).append("=? AND ");

        updateList.add(jobID);
        updateList.add(parentDocumentIDHash);
        updateList.add(childDocumentIDHash);
        updateList.add(dataName);
        if (dataValueHash != null)
        {
          sb.append(dataValueHashField).append("=?");
          updateList.add(dataValueHash);
        }
        else
        {
          sb.append(dataValueHashField).append(" IS NULL");
        }
        */
            
        map.put(newField,statusToString(ISNEW_EXISTING));
        map.put(processIDField,processID);
        performUpdate(map,sb.toString(),updateList,null);
        noteModifications(0,1,0);
      }
    }

    boolean[] rval = new boolean[childDocumentIDHashes.length];
    i = 0;
    while (i < rval.length)
    {
      String childDocumentIDHash = childDocumentIDHashes[i];
      rval[i++] = (insertHappened.get(childDocumentIDHash) != null);
    }

    return rval;
  }

  /** Do the exists check, in batch. */
  protected void performExistsCheck(Map presentMap, String query, ArrayList list)
    throws ManifoldCFException
  {
    // Note well: presentMap is only checked for the *existence* of a record, so we do not need to populate the datavalue field!
    // This is crucial, because otherwise we'd either be using an undetermined amount of memory, or we'd need to read into a temporary file.
    IResultSet result = performQuery("SELECT "+childIDHashField+","+dataNameField+","+dataValueHashField+" FROM "+getTableName()+" WHERE "+query+" FOR UPDATE",list,null,null);
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String documentIDHash = (String)row.getValue(childIDHashField);
      String dataName = (String)row.getValue(dataNameField);
      String dataValueHash = (String)row.getValue(dataValueHashField);
      //String dataValue = (String)row.getValue(dataValueField);
      ValueRecord vr = new ValueRecord(documentIDHash,dataName,dataValueHash,null);

      presentMap.put(vr,vr);
    }
  }
  
  /** Revert all records belonging to the specified parent documents to their original,
  * pre-modified, state.
  */
  public void revertRecords(Long jobID, String[] parentDocumentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = getMaxInClause();
    StringBuilder sb = new StringBuilder();
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String parentDocumentIDHash : parentDocumentIDHashes)
    {
      if (k == maxClause)
      {
        performRevertRecords(sb.toString(),jobID,list);
        sb.setLength(0);
        list.clear();
        k = 0;
      }
      if (k > 0)
        sb.append(",");
      sb.append("?");
      list.add(parentDocumentIDHash);
      k++;
    }

    if (k > 0)
      performRevertRecords(sb.toString(),jobID,list);
  }
  
  protected void performRevertRecords(String query, Long jobID, List<String> list)
    throws ManifoldCFException
  {
    // Delete new records
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList newList = new ArrayList();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list)})).append(" AND ");
      
    sb.append(newField).append("=?");
    newList.add(statusToString(ISNEW_NEW));
    performDelete(sb.toString(),newList,null);

    // Restore old values
    sb = new StringBuilder("WHERE ");
    newList.clear();

    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list)})).append(" AND ");

    sb.append(newField).append("=?");
    newList.add(statusToString(ISNEW_EXISTING));
    
    HashMap map = new HashMap();
    map.put(newField,statusToString(ISNEW_BASE));
    map.put(processIDField,null);
    performUpdate(map,sb.toString(),newList,null);
    
    noteModifications(0,list.size(),0);
  }

  /** Return all records belonging to the specified parent documents to the base state,
  * and delete the old (eliminated) child records.
  */
  public void restoreRecords(Long jobID, String[] parentDocumentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = getMaxInClause();
    StringBuilder sb = new StringBuilder();
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String parentDocumentIDHash : parentDocumentIDHashes)
    {
      if (k == maxClause)
      {
        performRestoreRecords(sb.toString(),jobID,list);
        sb.setLength(0);
        list.clear();
        k = 0;
      }
      if (k > 0)
        sb.append(",");
      sb.append("?");
      list.add(parentDocumentIDHash);
      k++;
    }

    if (k > 0)
      performRestoreRecords(sb.toString(),jobID,list);
  }

  protected void performRestoreRecords(String query, Long jobID, List<String> list)
    throws ManifoldCFException
  {
    // Delete
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList newList = new ArrayList();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list)})).append(" AND ");
      
    sb.append(newField).append("=?");
    newList.add(statusToString(ISNEW_BASE));
    performDelete(sb.toString(),newList,null);

    // Restore new values
    sb = new StringBuilder("WHERE ");
    newList.clear();

    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list)})).append(" AND ");

    sb.append(newField).append(" IN (?,?)");
    newList.add(statusToString(ISNEW_EXISTING));
    newList.add(statusToString(ISNEW_NEW));
    
    HashMap map = new HashMap();
    map.put(newField,statusToString(ISNEW_BASE));
    map.put(processIDField,null);
    performUpdate(map,sb.toString(),newList,null);
    
    noteModifications(0,list.size(),0);
  }

  /** Delete all records that mention a particular set of document identifiers.
  */
  public void deleteRecords(Long jobID, String[] documentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = maxClausePerformDeleteRecords(jobID);
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String documentIDHash : documentIDHashes)
    {
      if (k == maxClause)
      {
        performDeleteRecords(jobID,list);
        list.clear();
        k = 0;
      }
      list.add(documentIDHash);
      k++;
    }

    if (k > 0)
      performDeleteRecords(jobID,list);
  }

  protected int maxClausePerformDeleteRecords(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
    
  protected void performDeleteRecords(Long jobID, List<String> list)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList newList = new ArrayList();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)}));
    performDelete(sb.toString(),newList,null);
    
    sb = new StringBuilder("WHERE ");
    newList.clear();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list)}));
    performDelete(sb.toString(),newList,null);

    noteModifications(0,0,list.size()*2);
  }

  /** Get unique values given a document identifier, data name, an job identifier */
  public String[] getDataValues(Long jobID, String documentIdentifierHash, String dataName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(childIDHashField,documentIdentifierHash),
      new UnitaryClause(dataNameField,dataName)});

    IResultSet set = getDBInterface().performQuery("SELECT "+dataValueHashField+","+dataValueField+" FROM "+getTableName()+" WHERE "+
      query+" ORDER BY 1 ASC",list,null,null,-1,null,new ResultDuplicateEliminator());

    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(dataValueField);
      if (rval[i] == null)
        rval[i] = "";
      i++;
    }
    return rval;
  }

  /** Get unique values given a document identifier, data name, an job identifier */
  public CharacterInput[] getDataValuesAsFiles(Long jobID, String documentIdentifierHash, String dataName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(childIDHashField,documentIdentifierHash),
      new UnitaryClause(dataNameField,dataName)});

    ResultSpecification rs = new ResultSpecification();
    rs.setForm(dataValueField,ResultSpecification.FORM_STREAM);
    IResultSet set = getDBInterface().performQuery("SELECT "+dataValueHashField+","+dataValueField+" FROM "+getTableName()+" WHERE "+
      query+" ORDER BY 1 ASC",list,null,null,-1,rs,new ResultDuplicateEliminator());

    CharacterInput[] rval = new CharacterInput[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (CharacterInput)row.getValue(dataValueField);
      i++;
    }
    return rval;
  }

  /** Convert string to link status. */
  public static int stringToStatus(String status)
  {
    Integer value = (Integer)isNewMap.get(status);
    return value.intValue();
  }

  /** Convert link status to string */
  public static String statusToString(int status)
  {
    switch (status)
    {
    case ISNEW_BASE:
      return "B";
    case ISNEW_NEW:
      return "N";
    case ISNEW_EXISTING:
      return "E";
    default:
      return null;
    }
  }

  /** Limit checker which removes duplicate rows, based on datavaluehash */
  protected static class ResultDuplicateEliminator implements ILimitChecker
  {
    // The last value of data hash
    protected String currentDataHashValue = null;

    public ResultDuplicateEliminator()
    {
    }

    public boolean doesCompareWork()
    {
      return false;
    }

    public ILimitChecker duplicate()
    {
      return null;
    }

    public int hashCode()
    {
      return 0;
    }

    public boolean equals(Object object)
    {
      return false;
    }

    /** See if a result row should be included in the final result set.
    *@param row is the result row to check.
    *@return true if it should be included, false otherwise.
    */
    public boolean checkInclude(IResultRow row)
      throws ManifoldCFException
    {
      // Check to be sure that this row is different from the last; only then agree to include it.
      String value = (String)row.getValue(dataValueHashField);
      if (value == null)
        value = "";
      if (currentDataHashValue == null || !value.equals(currentDataHashValue))
      {
        currentDataHashValue = value;
        return true;
      }
      return false;
    }

    /** See if we should examine another row.
    *@return true if we need to keep going, or false if we are done.
    */
    public boolean checkContinue()
      throws ManifoldCFException
    {
      return true;
    }
  }

  protected static class ValueRecord
  {
    protected String documentIdentifierHash;
    protected String dataName;
    protected String dataValueHash;
    // This value may be null, if we're simply using this record as a key
    protected Object dataValue;

    public ValueRecord(String documentIdentifierHash, String dataName, String dataValueHash, Object dataValue)
    {
      this.documentIdentifierHash = documentIdentifierHash;
      this.dataName = dataName;
      this.dataValueHash = dataValueHash;
      this.dataValue = dataValue;
    }

    public String getDocumentIDHash()
    {
      return documentIdentifierHash;
    }

    public String getDataName()
    {
      return dataName;
    }

    public String getDataValueHash()
    {
      return dataValueHash;
    }

    public Object getDataValue()
    {
      return dataValue;
    }

    public int hashCode()
    {
      return documentIdentifierHash.hashCode() + dataName.hashCode() + ((dataValueHash == null)?0:dataValueHash.hashCode());
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof ValueRecord))
        return false;
      ValueRecord v = (ValueRecord)o;
      if (!documentIdentifierHash.equals(v.documentIdentifierHash))
        return false;
      if (!dataName.equals(v.dataName))
        return false;
      if (dataValueHash == null && v.dataValueHash != null)
        return false;
      if (dataValueHash != null && v.dataValueHash == null)
        return false;
      if (dataValueHash == null)
        return true;
      return dataValueHash.equals(v.dataValueHash);
    }
  }

  // This class filters an ordered resultset to return only the duplicates
  protected static class DuplicateFinder implements ILimitChecker
  {
    protected Long prevJobID = null;
    protected String prevParentIDHash = null;
    protected String prevChildIDHash = null;
    protected String prevDataName = null;
    protected String prevDataValue = null;

    public DuplicateFinder()
    {
    }

    /** See if this class can be legitimately compared against another of
    * the same type.
    *@return true if comparisons will ever return "true".
    */
    public boolean doesCompareWork()
    {
      return false;
    }

    /** Create a duplicate of this class instance.  All current state should be preserved.
    * NOTE: Since doesCompareWork() returns false, queries using this limit checker cannot
    * be cached, and therefore duplicate() is never called from the query executor.
    *@return the duplicate.
    */
    public ILimitChecker duplicate()
    {
      DuplicateFinder df = new DuplicateFinder();
      df.prevJobID = prevJobID;
      df.prevParentIDHash = prevParentIDHash;
      df.prevChildIDHash = prevChildIDHash;
      df.prevDataName = prevDataName;
      df.prevDataValue = prevDataValue;
      return df;
    }

    /** Find the hashcode for this class.  This will only ever be used if
    * doesCompareWork() returns true.
    *@return the hashcode.
    */
    public int hashCode()
    {
      return 0;
    }

    /** Compare two objects and see if equal.  This will only ever be used
    * if doesCompareWork() returns true.
    *@param object is the object to compare against.
    *@return true if equal.
    */
    public boolean equals(Object object)
    {
      return false;
    }

    /** See if a result row should be included in the final result set.
    *@param row is the result row to check.
    *@return true if it should be included, false otherwise.
    */
    public boolean checkInclude(IResultRow row)
      throws ManifoldCFException
    {
      Long jobID = (Long)row.getValue(jobIDField);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      if (parentIDHash == null)
        parentIDHash = "";
      String childIDHash = (String)row.getValue(childIDHashField);
      if (childIDHash == null)
        childIDHash = "";
      String dataName = (String)row.getValue(dataNameField);
      String dataValue = (String)row.getValue(dataValueField);
      if (dataValue == null)
        dataValue = "";

      // If this is a duplicate, we want to keep it!
      if (prevJobID != null && jobID.equals(prevJobID) && dataName.equals(prevDataName) && dataValue.equals(prevDataValue) && parentIDHash.equals(prevParentIDHash) && childIDHash.equals(prevChildIDHash))
        return true;
      prevJobID = jobID;
      prevDataName = dataName;
      prevParentIDHash = parentIDHash;
      prevChildIDHash = childIDHash;
      prevDataValue = dataValue;
      return false;
    }

    /** See if we should examine another row.
    *@return true if we need to keep going, or false if we are done.
    */
    public boolean checkContinue()
      throws ManifoldCFException
    {
      return true;
    }
  }

}
