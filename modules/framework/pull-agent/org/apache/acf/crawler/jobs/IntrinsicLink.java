/* $Id: IntrinsicLink.java 950850 2010-06-03 01:20:45Z kwright $ */

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
package org.apache.acf.crawler.jobs;

import java.util.*;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;
import org.apache.acf.crawler.system.ACF;

/** This class manages the table that keeps track of intrinsic relationships between documents.
*/
public class IntrinsicLink extends org.apache.acf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: IntrinsicLink.java 950850 2010-06-03 01:20:45Z kwright $";

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

  // Map from string character to link status
  protected static Map linkstatusMap;
  static
  {
    linkstatusMap = new HashMap();
    linkstatusMap.put("B",new Integer(LINKSTATUS_BASE));
    linkstatusMap.put("N",new Integer(LINKSTATUS_NEW));
    linkstatusMap.put("E",new Integer(LINKSTATUS_EXISTING));
  }

  /** Counter for kicking off analyze */
  protected static AnalyzeTracker tracker = new AnalyzeTracker();
  /** Counter for kicking off reindex */
  protected static AnalyzeTracker reindexTracker = new AnalyzeTracker();

  // Count of events to call reindex for
  protected static final long REINDEX_COUNT = 250000L;

  /** Constructor.
  *@param database is the database handle.
  */
  public IntrinsicLink(IDBInterface database)
    throws ACFException
  {
    super(database,"intrinsiclink");
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ACFException
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
        performCreate(map,null);
      }
      else
      {
        // Perform upgrade, if needed.
      }

      // Indexes
      IndexDescription uniqueIndex = new IndexDescription(true,new String[]{jobIDField,linkTypeField,parentIDHashField,childIDHashField});
      IndexDescription jobParentIndex = new IndexDescription(false,new String[]{jobIDField,parentIDHashField});
      IndexDescription jobChildNewIndex = new IndexDescription(false,new String[]{jobIDField,childIDHashField,newField});

      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (uniqueIndex != null && id.equals(uniqueIndex))
          uniqueIndex = null;
        else if (jobParentIndex != null && id.equals(jobParentIndex))
          jobParentIndex = null;
        else if (jobChildNewIndex != null && id.equals(jobChildNewIndex))
          jobChildNewIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      if (jobParentIndex != null)
        performAddIndex(null,jobParentIndex);

      if (jobChildNewIndex != null)
        performAddIndex(null,jobChildNewIndex);

      // Create the indexes we are still missing
      if (uniqueIndex != null)
        performAddIndex(null,uniqueIndex);
      
      // All done
      break;
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ACFException
  {
    performDrop(null);
  }

  /** Analyze job tables that need analysis.
  */
  public void analyzeTables()
    throws ACFException
  {
    long startTime = System.currentTimeMillis();
    Logging.perf.debug("Beginning to analyze intrinsiclink table");
    analyzeTable();
    Logging.perf.debug("Done analyzing intrinsiclink table in "+new Long(System.currentTimeMillis()-startTime)+" ms");
  }

  /** Delete an owner (and clean up the corresponding hopcount rows).
  */
  public void deleteOwner(Long jobID)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    performDelete("WHERE "+jobIDField+"=?",list,null);
    reindexTracker.noteInsert();
  }

  /** Reset, at startup time.  Since links can only be added in a transactionally safe way by processing
  * of documents, and cached records of hopcount are updated only when requested, it is safest to simply
  * move any "new" or "new existing" links back to base state on startup.  Then, the next time that page
  * is processed, the links will be updated properly.
  */
  public void reset()
    throws ACFException
  {
    HashMap map = new HashMap();
    ArrayList list = new ArrayList();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    list.add(statusToString(LINKSTATUS_NEW));
    list.add(statusToString(LINKSTATUS_EXISTING));
    performUpdate(map,"WHERE "+newField+"=? OR "+newField+"=?",list,null);
  }

  /** Record a references from source to targets.  These references will be marked as either "new" or "existing".
  *@return the target document ID's that are considered "new".
  */
  public String[] recordReferences(Long jobID, String sourceDocumentIDHash, String[] targetDocumentIDHashes, String linkType)
    throws ACFException
  {
    // Need to go into a transaction because we need to distinguish between update and insert.
    beginTransaction();
    try
    {
      // It is no longer necessary to perform a lock here, because the parent document can only be accessed by one thread at a time, so only
      // one thread can make it into here at any given time for any given parent.
      //performLock();
      HashMap duplicateRemoval = new HashMap();
      int maxClause = 25;
      StringBuffer sb = new StringBuffer();
      ArrayList list = new ArrayList();
      int i = 0;
      int k = 0;
      // Keep track of the document identifiers that have been seen vs. those that were unseen.
      HashMap presentMap = new HashMap();
      while (k < targetDocumentIDHashes.length)
      {
        String targetDocumentIDHash = targetDocumentIDHashes[k++];
        if (duplicateRemoval.get(targetDocumentIDHash) != null)
          continue;
        duplicateRemoval.put(targetDocumentIDHash,targetDocumentIDHash);
        if (i == maxClause)
        {
          // Do the query and record the results
          performExistsCheck(presentMap,sb.toString(),list);
          i = 0;
          sb.setLength(0);
          list.clear();
        }
        if (i > 0)
          sb.append(" OR");
        sb.append("(").append(jobIDField).append("=? AND ")
          .append(linkTypeField).append("=? AND ")
          .append(parentIDHashField).append("=? AND ").append(childIDHashField).append("=?)");
        list.add(jobID);
        list.add(linkType);
        list.add(targetDocumentIDHash);
        list.add(sourceDocumentIDHash);
        i++;
      }
      if (i > 0)
        performExistsCheck(presentMap,sb.toString(),list);

      // Go through the list again, and based on the results above, decide to do either an insert or
      // an update.
      // We have to count these by hand, in case there are duplicates in the array.
      int count = 0;
      Iterator iter = duplicateRemoval.keySet().iterator();
      while (iter.hasNext())
      {
        String targetDocumentIDHash = (String)iter.next();
        if (presentMap.get(targetDocumentIDHash) == null)
          count++;
      }
      String[] newReferences = new String[count];
      int j = 0;
      // Note: May be able to make this more efficient if we update things in batches...
      iter = duplicateRemoval.keySet().iterator();
      while (iter.hasNext())
      {
        String targetDocumentIDHash = (String)iter.next();

        if (presentMap.get(targetDocumentIDHash) == null)
        {
          newReferences[j++] = targetDocumentIDHash;
          HashMap map = new HashMap();
          map.put(jobIDField,jobID);
          map.put(parentIDHashField,targetDocumentIDHash);
          map.put(childIDHashField,sourceDocumentIDHash);
          map.put(linkTypeField,linkType);
          map.put(newField,statusToString(LINKSTATUS_NEW));
          performInsert(map,null);
          tracker.noteInsert();
        }
        else
        {
          ArrayList updateList = new ArrayList();
          updateList.add(jobID);
          updateList.add(linkType);
          updateList.add(targetDocumentIDHash);
          updateList.add(sourceDocumentIDHash);
          HashMap map = new HashMap();
          map.put(newField,statusToString(LINKSTATUS_EXISTING));
          performUpdate(map,"WHERE "+jobIDField+"=? AND "+linkTypeField+"=? AND "+
            parentIDHashField+"=? AND "+childIDHashField+"=?",updateList,null);
          reindexTracker.noteInsert();
        }
      }
      return newReferences;
    }
    catch (ACFException e)
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

  /** Do the exists check, in batch. */
  protected void performExistsCheck(Map presentMap, String query, ArrayList list)
    throws ACFException
  {
    IResultSet result = performQuery("SELECT "+parentIDHashField+" FROM "+getTableName()+" WHERE "+query+" FOR UPDATE",list,null,null);
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      presentMap.put(parentIDHash,parentIDHash);
    }
  }

  /** Remove all target links of the specified source documents that are not marked as "new" or "existing", and
  * return the others to their base state.
  */
  public void removeLinks(Long jobID, String commonNewExpression, String[] sourceDocumentIDHashes,
    String sourceTableName,
    String sourceTableIDColumn, String sourceTableJobColumn, String sourceTableCriteria)
    throws ACFException
  {
    beginTransaction();
    try
    {
      if (sourceDocumentIDHashes != null)
      {
        int maxClause = 25;
        StringBuffer sb = new StringBuffer();
        ArrayList list = new ArrayList();
        int i = 0;
        int k = 0;
        while (i < sourceDocumentIDHashes.length)
        {
          if (k == maxClause)
          {
            performRemoveLinks(sb.toString(),list,commonNewExpression);
            sb.setLength(0);
            list.clear();
            k = 0;
          }
          if (k > 0)
            sb.append(" OR");
          sb.append("(").append(jobIDField).append("=? AND ")
            .append(childIDHashField).append("=?)");
          String sourceDocumentIDHash = sourceDocumentIDHashes[i++];
          list.add(jobID);
          list.add(sourceDocumentIDHash);
          k++;
        }

        if (k > 0)
          performRemoveLinks(sb.toString(),list,commonNewExpression);
        reindexTracker.noteInsert(sourceDocumentIDHashes.length);
      }
      else
      {
        ArrayList list = new ArrayList();
        list.add(jobID);
        StringBuffer sb = new StringBuffer("WHERE EXISTS(SELECT 'x' FROM ");
        sb.append(sourceTableName).append(" WHERE ").append(sourceTableJobColumn).append("=? AND ")
          .append(sourceTableIDColumn).append("=").append(getTableName()).append(".").append(childIDHashField)
          .append(" AND ").append(sourceTableCriteria).append(")");
        performDelete(sb.toString(),list,null);
        reindexTracker.noteInsert();
      }
    }
    catch (ACFException e)
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

  protected void performRemoveLinks(String query, ArrayList list, String commonNewExpression)
    throws ACFException
  {
    StringBuffer sb = new StringBuffer("WHERE (");
    sb.append(query).append(")");
    if (commonNewExpression != null)
      sb.append(" AND ").append(commonNewExpression);
    performDelete(sb.toString(),list,null);
  }

  /** Return all target links of the specified source documents to their base state.
  */
  public void restoreLinks(Long jobID, String[] sourceDocumentIDHashes)
    throws ACFException
  {
    beginTransaction();
    try
    {
      int maxClause = 25;
      StringBuffer sb = new StringBuffer();
      ArrayList list = new ArrayList();
      int i = 0;
      int k = 0;
      while (i < sourceDocumentIDHashes.length)
      {
        if (k == maxClause)
        {
          performRestoreLinks(sb.toString(),list);
          sb.setLength(0);
          list.clear();
          k = 0;
        }
        if (k > 0)
          sb.append(" OR");
        sb.append("(").append(jobIDField).append("=? AND ")
          .append(childIDHashField).append("=?)");
        String sourceDocumentIDHash = sourceDocumentIDHashes[i++];
        list.add(jobID);
        list.add(sourceDocumentIDHash);
        k++;
      }

      if (k > 0)
        performRestoreLinks(sb.toString(),list);
    }
    catch (ACFException e)
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
    reindexTracker.noteInsert(sourceDocumentIDHashes.length);
  }

  protected void performRestoreLinks(String query, ArrayList list)
    throws ACFException
  {
    StringBuffer sb = new StringBuffer("WHERE (");
    sb.append(query).append(") AND (").append(newField).append("=? OR ").append(newField).append("=?)");
    list.add(statusToString(LINKSTATUS_EXISTING));
    list.add(statusToString(LINKSTATUS_NEW));
    HashMap map = new HashMap();
    map.put(newField,statusToString(LINKSTATUS_BASE));
    performUpdate(map,sb.toString(),list,null);
  }

  /** Get document's children.
  *@return rows that contain the children.  Column names are 'linktype','childidentifier'.
  */
  public IResultSet getDocumentChildren(Long jobID, String parentIDHash)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    list.add(parentIDHash);
    return performQuery("SELECT "+linkTypeField+" AS linktype,"+childIDHashField+" AS childidentifier FROM "+
      getTableName()+" WHERE "+jobIDField+"=? AND "+parentIDHashField+"=?",list,null,null);
  }

  /** Get document's parents.
  *@return a set of document identifier hashes that constitute parents of the specified identifier.
  */
  public String[] getDocumentUniqueParents(Long jobID, String childIDHash)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(jobID);
    list.add(childIDHash);
    IResultSet set = performQuery("SELECT DISTINCT "+parentIDHashField+" FROM "+
      getTableName()+" WHERE "+jobIDField+"=? AND "+childIDHashField+"=?",list,null,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i++] = (String)row.getValue(parentIDHashField);
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

  /** Conditionally do analyze operation.
  */
  public void conditionallyAnalyzeTables()
    throws ACFException
  {
    if (tracker.checkAnalyze())
    {
      try
      {
        // Do the analyze
        analyzeTable();
      }
      finally
      {
        // Get the size of the table
        // For this table, we base the wait time on the number of rows in it.
        // Simply reanalyze every n inserts
        tracker.doAnalyze(30000L);
      }
    }
    if (reindexTracker.checkAnalyze())
    {
      try
      {
        // Do the reindex
        reindexTable();
      }
      finally
      {
        // Get the size of the table
        // For this table, we base the wait time on the number of rows in it.
        // Simply reanalyze every n inserts
        reindexTracker.doAnalyze(REINDEX_COUNT);
      }
    }

  }


  /** Analyze tracker class.
  */
  protected static class AnalyzeTracker
  {
    // Number of records to insert before we need to analyze again.
    // After start, we wait 1000 before analyzing the first time.
    protected long recordCount = 1000L;
    protected boolean busy = false;

    /** Constructor.
    */
    public AnalyzeTracker()
    {

    }

    /** Note an analyze.
    */
    public synchronized void doAnalyze(long repeatCount)
    {
      recordCount = repeatCount;
      busy = false;
    }

    public synchronized void noteInsert(int count)
    {
      if (recordCount >= (long)count)
        recordCount -= (long)count;
      else
        recordCount = 0L;
    }

    /** Note an insert */
    public synchronized void noteInsert()
    {
      if (recordCount > 0L)
        recordCount--;
    }

    /** Prepare to insert/delete a record, and see if analyze is required.
    */
    public synchronized boolean checkAnalyze()
    {
      if (busy)
        return false;
      busy = (recordCount == 0L);
      return busy;
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
      throws ACFException
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
      throws ACFException
    {
      return true;
    }
  }

}
