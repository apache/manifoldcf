/* $Id: IntrinsicLink.java 988245 2010-08-23 18:39:35Z kwright $ */

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
 * <b>intrinsiclink</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>jobid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
 * <tr><td>linktype</td><td>VARCHAR(255)</td><td></td></tr>
 * <tr><td>parentidhash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>childidhash</td><td>VARCHAR(40)</td><td></td></tr>
 * <tr><td>isnew</td><td>CHAR(1)</td><td></td></tr>
 * <tr><td>processid</td><td>VARCHAR(16)</td><td></td></tr>
 * </table>
 * <br><br>
 * 
*/
public class IntrinsicLink extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: IntrinsicLink.java 988245 2010-08-23 18:39:35Z kwright $";

  // "new" field values

  /** The standard value for this field.  Means that the link existed prior to this scan, and no new link
  * was found yet. */
  protected static final int LINKSTATUS_BASE = 0;
  /** This value means that the link is brand-new; it did not exist before this pass. */
  protected static final int LINKSTATUS_NEW = 1;
  /** This value means that the link existed before, and has been found during this scan. */
  protected static final int LINKSTATUS_EXISTING = 2;

  // Field names
  public static final String jobIDField = "jobid";
  public static final String linkTypeField = "linktype";
  public static final String parentIDHashField = "parentidhash";
  public static final String childIDHashField = "childidhash";
  public static final String newField = "isnew";
  public static final String processIDField = "processid";

  // Map from string character to link status
  protected static Map linkstatusMap;
  static
  {
    linkstatusMap = new HashMap();
    linkstatusMap.put("B",new Integer(LINKSTATUS_BASE));
    linkstatusMap.put("N",new Integer(LINKSTATUS_NEW));
    linkstatusMap.put("E",new Integer(LINKSTATUS_EXISTING));
  }

  /** Constructor.
  *@param database is the database handle.
  */
  public IntrinsicLink(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"intrinsiclink");
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ManifoldCFException
  {
    // Creating a unique index as part of upgrading could well fail, so we must have the ability to fix things up and retry if that happens.
    while (true)
    {
      // Schema
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(jobIDField,new ColumnDescription("BIGINT",false,false,jobsTable,jobsColumn,false));
        map.put(linkTypeField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(parentIDHashField,new ColumnDescription("VARCHAR(40)",false,false,null,null,false));
        map.put(childIDHashField,new ColumnDescription("VARCHAR(40)",false,true,null,null,false));
        map.put(newField,new ColumnDescription("CHAR(1)",false,true,null,null,false));
        map.put(processIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Perform upgrade, if needed.
      }

      // Indexes
      IndexDescription uniqueIndex = new IndexDescription(true,new String[]{jobIDField,parentIDHashField,linkTypeField,childIDHashField});
      IndexDescription jobChildNewIndex = new IndexDescription(false,new String[]{jobIDField,childIDHashField,newField});
      IndexDescription newIndex = new IndexDescription(false,new String[]{newField,processIDField});

      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (uniqueIndex != null && id.equals(uniqueIndex))
          uniqueIndex = null;
        else if (jobChildNewIndex != null && id.equals(jobChildNewIndex))
          jobChildNewIndex = null;
        else if (newIndex != null && id.equals(newIndex))
          newIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Create the indexes we are still missing
      if (jobChildNewIndex != null)
        performAddIndex(null,jobChildNewIndex);

      if (newIndex != null)
        performAddIndex(null,newIndex);

      if (uniqueIndex != null)
        performAddIndex(null,uniqueIndex);
      
      // All done
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
    Logging.perf.debug("Beginning to analyze intrinsiclink table");
    analyzeTable();
    Logging.perf.debug("Done analyzing intrinsiclink table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
  }

  /** Delete an owner (and clean up the corresponding hopcount rows).
  */
  public void deleteOwner(Long jobID)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
    performDelete("WHERE "+query,list,null);
    noteModifications(0,0,1);
  }

  /** Reset, at startup time.  Since links can only be added in a transactionally safe way by processing
  * of documents, and cached records of hopcount are updated only when requested, it is safest to simply
  * move any "new" or "new existing" links back to base state on startup.  Then, the next time that page
  * is processed, the links will be updated properly.
  *@param processID is the process to restart.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(newField,new Object[]{
        statusToString(LINKSTATUS_NEW),
        statusToString(LINKSTATUS_EXISTING)}),
      new UnitaryClause(processIDField,processID)});
    performUpdate(map,"WHERE "+query,list,null);
  }

  /** Clean up after all process IDs
  */
  public void restart()
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new MultiClause(newField,new Object[]{
        statusToString(LINKSTATUS_NEW),
        statusToString(LINKSTATUS_EXISTING)})});
    performUpdate(map,"WHERE "+query,list,null);
  }
  
  public void restartCluster()
    throws ManifoldCFException
  {
    // Does nothing
  }
  
  /** Record a references from source to targets.  These references will be marked as either "new" or "existing".
  *@return the target document ID's that are considered "new".
  */
  public String[] recordReferences(Long jobID, String sourceDocumentIDHash,
    String[] targetDocumentIDHashes, String linkType, String processID)
    throws ManifoldCFException
  {
    Set<String> duplicateRemoval = new HashSet<String>();
    int maxClause = maxClausePerformExistsCheck(jobID,linkType,sourceDocumentIDHash);
    List<String> list = new ArrayList<String>();
    int i = 0;
    // Keep track of the document identifiers that have been seen vs. those that were unseen.
    Set<String> presentMap = new HashSet<String>();
    for (String targetDocumentIDHash : targetDocumentIDHashes)
    {
      if (duplicateRemoval.contains(targetDocumentIDHash))
        continue;
      duplicateRemoval.add(targetDocumentIDHash);
      if (i == maxClause)
      {
        // Do the query and record the results
        performExistsCheck(presentMap,jobID,linkType,sourceDocumentIDHash,list);
        i = 0;
        list.clear();
      }
      list.add(targetDocumentIDHash);
      i++;
    }
    if (i > 0)
      performExistsCheck(presentMap,jobID,linkType,sourceDocumentIDHash,list);

    // Go through the list again, and based on the results above, decide to do either an insert or
    // an update.
    // We have to count these by hand, in case there are duplicates in the array.
    int count = 0;
    Iterator<String> iter = duplicateRemoval.iterator();
    while (iter.hasNext())
    {
      String targetDocumentIDHash = iter.next();
      if (!presentMap.contains(targetDocumentIDHash))
        count++;
    }
    String[] newReferences = new String[count];
    int j = 0;
    // Note: May be able to make this more efficient if we update things in batches...
    iter = duplicateRemoval.iterator();
    while (iter.hasNext())
    {
      String targetDocumentIDHash = iter.next();

      if (!presentMap.contains(targetDocumentIDHash))
      {
        newReferences[j++] = targetDocumentIDHash;
        HashMap map = new HashMap();
        map.put(jobIDField,jobID);
        map.put(parentIDHashField,targetDocumentIDHash);
        map.put(childIDHashField,sourceDocumentIDHash);
        map.put(linkTypeField,linkType);
        map.put(newField,statusToString(LINKSTATUS_NEW));
        map.put(processIDField,processID);
        performInsert(map,null);
        noteModifications(1,0,0);
      }
      else
      {
        HashMap map = new HashMap();
        map.put(newField,statusToString(LINKSTATUS_EXISTING));
        map.put(processIDField,processID);
        ArrayList updateList = new ArrayList();
        String query = buildConjunctionClause(updateList,new ClauseDescription[]{
          new UnitaryClause(jobIDField,jobID),
          new UnitaryClause(parentIDHashField,targetDocumentIDHash),
          new UnitaryClause(linkTypeField,linkType),
          new UnitaryClause(childIDHashField,sourceDocumentIDHash)});
        performUpdate(map,"WHERE "+query,updateList,null);
        noteModifications(0,1,0);
      }
    }
    return newReferences;
  }

  /** Calculate the max clauses for the exists check
  */
  protected int maxClausePerformExistsCheck(Long jobID, String linkType, String childIDHash)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(linkTypeField,linkType),
      new UnitaryClause(childIDHashField,childIDHash)});
  }
    
  /** Do the exists check, in batch. */
  protected void performExistsCheck(Set<String> presentMap, Long jobID, String linkType, String childIDHash, List<String> list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list),
      new UnitaryClause(linkTypeField,linkType),
      new UnitaryClause(childIDHashField,childIDHash)});

    IResultSet result = performQuery("SELECT "+parentIDHashField+" FROM "+getTableName()+" WHERE "+query+" FOR UPDATE",newList,null,null);
    for (int i = 0; i < result.getRowCount(); i++)
    {
      IResultRow row = result.getRow(i);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      presentMap.add(parentIDHash);
    }
  }

  /** Remove all links that mention a specific set of documents, as described by a join.
  */
  public void removeDocumentLinks(Long jobID,
    String joinTableName,
    String joinTableIDColumn, String joinTableJobColumn,
    String joinTableCriteria, ArrayList joinTableParams)
    throws ManifoldCFException
  {
    // Delete matches for childIDHashField
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList list = new ArrayList();
          
    sb.append("EXISTS(SELECT 'x' FROM ").append(joinTableName).append(" WHERE ")
      .append(buildConjunctionClause(list,new ClauseDescription[]{
        new UnitaryClause(joinTableJobColumn,jobID),
        new JoinClause(joinTableIDColumn,getTableName()+"."+childIDHashField)})).append(" AND ");

    sb.append(joinTableCriteria);
    list.addAll(joinTableParams);
              
    sb.append(")");
              
    performDelete(sb.toString(),list,null);
    noteModifications(0,0,1);
      
    // DON'T delete ParentID matches; we need to leave those around for bookkeeping to
    // be correct.  See CONNECTORS-501.
  }

  /** Remove all links that mention a specific set of documents.
  */
  public void removeDocumentLinks(Long jobID,
    String[] documentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = maxClausePerformRemoveDocumentLinks(jobID);
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String documentIDHash : documentIDHashes)
    {
      if (k == maxClause)
      {
        performRemoveDocumentLinks(list,jobID);
        list.clear();
        k = 0;
      }
      list.add(documentIDHash);
      k++;
    }

    if (k > 0)
      performRemoveDocumentLinks(list,jobID);
    noteModifications(0,0,documentIDHashes.length);
  }

  protected int maxClausePerformRemoveDocumentLinks(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
    
  protected void performRemoveDocumentLinks(List<String> list, Long jobID)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList thisList = new ArrayList();

    sb.append(buildConjunctionClause(thisList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)}));
    performDelete(sb.toString(),thisList,null);
    
    // DON'T do parentID matches; we need to leave those around.  See CONNECTORS-501.
  }

  /** Remove all target links of the specified source documents that are not marked as "new" or "existing", and
  * return the others to their base state.
  */
  public void removeLinks(Long jobID,
    String commonNewExpression, ArrayList commonNewParams,
    String[] sourceDocumentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = maxClausePerformRemoveLinks(jobID);
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String sourceDocumentIDHash : sourceDocumentIDHashes)
    {
      if (k == maxClause)
      {
        performRemoveLinks(list,jobID,commonNewExpression,commonNewParams);
        list.clear();
        k = 0;
      }
      list.add(sourceDocumentIDHash);
      k++;
    }

    if (k > 0)
      performRemoveLinks(list,jobID,commonNewExpression,commonNewParams);
    noteModifications(0,0,sourceDocumentIDHashes.length);
  }
  
  protected int maxClausePerformRemoveLinks(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
    
  protected void performRemoveLinks(List<String> list, Long jobID, String commonNewExpression,
    ArrayList commonNewParams)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList thisList = new ArrayList();

    sb.append(buildConjunctionClause(thisList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)}));
    if (commonNewExpression != null)
    {
      sb.append(" AND ").append(commonNewExpression);
      thisList.addAll(commonNewParams);
    }
    performDelete(sb.toString(),thisList,null);
  }

  /** Return all target links of the specified source documents to their base state.
  */
  public void restoreLinks(Long jobID, String[] sourceDocumentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = maxClausesPerformRestoreLinks(jobID);
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String sourceDocumentIDHash : sourceDocumentIDHashes)
    {
      if (k == maxClause)
      {
        performRestoreLinks(jobID,list);
        list.clear();
        k = 0;
      }
      list.add(sourceDocumentIDHash);
      k++;
    }

    if (k > 0)
      performRestoreLinks(jobID,list);
    noteModifications(0,sourceDocumentIDHashes.length,0);
  }

  protected int maxClausesPerformRestoreLinks(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
  
  protected void performRestoreLinks(Long jobID, List<String> list)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    map.put(processIDField,null);
    
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList newList = new ArrayList();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)})).append(" AND ")
      .append(newField).append(" IN (?,?)");
    newList.add(statusToString(LINKSTATUS_EXISTING));
    newList.add(statusToString(LINKSTATUS_NEW));
    performUpdate(map,sb.toString(),newList,null);
  }

  /** Throw away links added during (aborted) processing.
  */
  public void revertLinks(Long jobID, String[] sourceDocumentIDHashes)
    throws ManifoldCFException
  {
    int maxClause = maxClausesPerformRevertLinks(jobID);
    List<String> list = new ArrayList<String>();
    int k = 0;
    for (String sourceDocumentIDHash : sourceDocumentIDHashes)
    {
      if (k == maxClause)
      {
        performRevertLinks(jobID,list);
        list.clear();
        k = 0;
      }
      list.add(sourceDocumentIDHash);
      k++;
    }

    if (k > 0)
      performRevertLinks(jobID,list);
    noteModifications(0,sourceDocumentIDHashes.length,0);
  }

  protected int maxClausesPerformRevertLinks(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
  }
  
  protected void performRevertLinks(Long jobID, List<String> list)
    throws ManifoldCFException
  {
    // First, delete everything marked as "new"
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList newList = new ArrayList();

    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)})).append(" AND ")
      .append(newField).append("=?");
    newList.add(statusToString(LINKSTATUS_NEW));
    performDelete(sb.toString(),newList,null);

    // Now map everything marked as "EXISTING" back to "BASE".
    HashMap map = new HashMap();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    map.put(processIDField,null);
    
    sb = new StringBuilder();
    newList.clear();
    
    sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(childIDHashField,list)})).append(" AND ")
      .append(newField).append("=?");
    newList.add(statusToString(LINKSTATUS_EXISTING));
    performUpdate(map,sb.toString(),newList,null);
  }

  /** Get document's children.
  *@return rows that contain the children.  Column names are 'linktype','childidentifier'.
  */
  public IResultSet getDocumentChildren(Long jobID, String parentIDHash)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(parentIDHashField,parentIDHash)});
    return performQuery("SELECT "+linkTypeField+" AS linktype,"+childIDHashField+" AS childidentifier FROM "+
      getTableName()+" WHERE "+query,list,null,null);
  }

  /** Get document's parents.
  *@return a set of document identifier hashes that constitute parents of the specified identifier.
  */
  public String[] getDocumentUniqueParents(Long jobID, String childIDHash)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(childIDHashField,childIDHash)});
      
    IResultSet set = performQuery("SELECT DISTINCT "+parentIDHashField+" FROM "+
      getTableName()+" WHERE "+query,list,null,null);
    String[] rval = new String[set.getRowCount()];
    for (int i = 0; i < rval.length; i++)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(parentIDHashField);
    }
    return rval;
  }

  /** Convert string to link status. */
  public static int stringToStatus(String status)
  {
    Integer value = (Integer)linkstatusMap.get(status);
    return value.intValue();
  }

  /** Convert link status to string */
  public static String statusToString(int status)
  {
    switch (status)
    {
    case LINKSTATUS_BASE:
      return "B";
    case LINKSTATUS_NEW:
      return "N";
    case LINKSTATUS_EXISTING:
      return "E";
    default:
      return null;
    }
  }

  // This class filters an ordered resultset to return only the duplicates
  protected static class DuplicateFinder implements ILimitChecker
  {
    protected Long prevJobID = null;
    protected String prevLinkType = null;
    protected String prevParentIDHash = null;
    protected String prevChildIDHash = null;

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
      df.prevLinkType = prevLinkType;
      df.prevParentIDHash = prevParentIDHash;
      df.prevChildIDHash = prevChildIDHash;
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
      String linkType = (String)row.getValue(linkTypeField);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      if (parentIDHash == null)
        parentIDHash = "";
      String childIDHash = (String)row.getValue(childIDHashField);
      if (childIDHash == null)
        childIDHash = "";

      // If this is a duplicate, we want to keep it!
      if (prevJobID != null && jobID.equals(prevJobID) && linkType.equals(prevLinkType) && parentIDHash.equals(prevParentIDHash) && childIDHash.equals(prevChildIDHash))
        return true;
      prevJobID = jobID;
      prevLinkType = linkType;
      prevParentIDHash = parentIDHash;
      prevChildIDHash = childIDHash;
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
