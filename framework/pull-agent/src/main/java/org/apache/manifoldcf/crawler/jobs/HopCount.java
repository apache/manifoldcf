/* $Id: HopCount.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class manages the table that keeps track of hop count, and algorithmically determines this value
* for a document identifier upon request.
* 
* <br><br>
* <b>hopcount</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>id</td><td>BIGINT</td><td>Primary Key</td></tr>
* <tr><td>jobid</td><td>BIGINT</td><td>Reference:jobs.id</td></tr>
* <tr><td>linktype</td><td>VARCHAR(255)</td><td></td></tr>
* <tr><td>parentidhash</td><td>VARCHAR(40)</td><td></td></tr>
* <tr><td>distance</td><td>BIGINT</td><td></td></tr>
* <tr><td>deathmark</td><td>CHAR(1)</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class HopCount extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: HopCount.java 988245 2010-08-23 18:39:35Z kwright $";

  // Answer constants
  public static final int ANSWER_UNKNOWN = -1;
  public static final int ANSWER_INFINITY = -2;

  // Notes on the schema
  // ===================
  //
  // This schema consists of three interrelated tables.  The table controlled directly by this class
  // is the table where cached distance results are kept.  It has a child table, which keeps track
  // of certain dependencies, so that we have a way of figuring out relatively accurately which cached links
  // need to be re-evaluated when there is a change.  Finally, there is a related table where intrinsic
  // (i.e. direct) link information is kept.
  //
  // When links are recorded, a source document refers to target documents.  The convention here is
  // that the source document is called the "child", and the target document is called the "parent".
  // Also by convention, a child value of null means "the root".  Since all cached distances are to
  // the root, we only store the "parent" in the hopcount table.
  //
  // Each row in the main hopcount table is linked with the child tables by means of an id field.
  //
  // Database table management for hopcount determination
  // ====================================================
  //
  // The critical operation we want to be able to do is to propagate the effects of a change throughout
  // the cached data.  I originally assumed that that meant "blowing the cache" - deleting all minimum
  // hop counts stored in the database which corresponded to the link we have added or deleted.
  // However, after the naive algorithm ran for a while, it became clear that it was not going to perform
  // well, because the sheer quantity of dependency information made management of dependencies far
  // exceed reason.  Caching of hopcount, however, still was clearly essential, because when I removed
  // the caching completely, things just plain wedged.
  //
  // Then I realized that by far the most common activity involves adding links to the graph, and therefore
  // if I could optimize that activity without storing huge quantities of dependency information, the
  // performance goals would be met.  So, this is how the thinking went:
  //
  // - We always start with a graph where the cached hopcount values only exist IF the hopcount values
  //   that were needed to come up with that value also exist.  Any changes to the graph MUST preserve this
  //   situation.
  // - Under these conditions, adding a link between a source and target could encounter either of two conditions:
  //   (a) the target has no cached hopcount, or
  //   (b) the target DOES have a cached hopcount.
  //   In case (a), we must treat the existing non-record as meaning "infinite distance", which is clearly wrong.
  //   We therefore must create a record for that location, which has a value of infinity.  After that, treat this
  //   the exact same way as for (b).
  //   In the case of (b), we need to re-evaluate the hopcount with the new link in place,
  //   and compare it against the existing hopcount.  The new value cannot be larger (unless the table was somehow corrupted),
  //   because adding a link can NEVER increase a hopcount.  If the new hopcount is less than the old, then
  //   we change the value in the table, and examine all the target nodes in the same way.  Most likely, the
  //   propagation will stop quickly, because there are lots of ways of getting to a node and this is just one
  //   of them.
  // - When a link is deleted, we run the risk of leaving around disconnected loops that evaluate forever, if
  //   we use the same propagation algorithm.  So instead, we want to keep track of what nodes will need reevaluation
  //   when a link is destroyed.  This list is relatively small, since only the shortest possible path to a node
  //   is represented in this dependency information.
  //   So, when a link is deleted, the following steps take place.  All the dependent hopcount nodes are queued, but
  //   in such a way as to be reset to having an "infinite" distance.  Then, re-evaluation occurs in the same manner as for
  //   the add case above.
  // - In order to determine the hopcount value of a node at any given time, all you need to do is to look for a cached
  //   hopcount value.  If you find it, that's the right number.  If you don't, you can presume the value is infinity.
  //
  //
  // Activities that should occur when a hopcount changes
  // ====================================================
  //
  // Documents in the job queue may be excluded from consideration based on hopcount.  If the hopcount for a document changes
  // (decreases), this assessment could well change.  Therefore, this hopcount module MUST cause documents to be switched
  // to a "pending" state whenever a hopcount change occurs that makes the document pass its hopcount filtering criteria.
  //
  //

  // Field names
  public static final String idField = "id";
  public static final String jobIDField = "jobid";
  public static final String linkTypeField = "linktype";
  public static final String parentIDHashField = "parentidhash";
  public static final String distanceField = "distance";
  public static final String markForDeathField = "deathmark";

  // Mark for death status
  public static final int MARK_NORMAL = 0;
  public static final int MARK_QUEUED = 1;
  public static final int MARK_DELETING = 2;

  protected static Map markMap;

  static
  {
    markMap = new HashMap();
    markMap.put("N",new Integer(MARK_NORMAL));
    markMap.put("Q",new Integer(MARK_QUEUED));
    markMap.put("D",new Integer(MARK_DELETING));
  }

  /** Intrinsic link table manager. */
  protected IntrinsicLink intrinsicLinkManager;
  /** Hop "delete" dependencies manager */
  protected HopDeleteDeps deleteDepsManager;

  /** Thread context */
  protected IThreadContext threadContext;
  
  /** Constructor.
  *@param database is the database handle.
  */
  public HopCount(IThreadContext tc, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"hopcount");
    this.threadContext = tc;
    intrinsicLinkManager = new IntrinsicLink(database);
    deleteDepsManager = new HopDeleteDeps(database);
  }

  /** Install or upgrade.
  */
  public void install(String jobsTable, String jobsColumn)
    throws ManifoldCFException
  {
    // Per convention, always have outer loop in install() methods
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(idField,new ColumnDescription("BIGINT",true,false,null,null,false));
        map.put(jobIDField,new ColumnDescription("BIGINT",false,false,jobsTable,jobsColumn,false));
        map.put(linkTypeField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(parentIDHashField,new ColumnDescription("VARCHAR(40)",false,false,null,null,false));
        map.put(distanceField,new ColumnDescription("BIGINT",false,true,null,null,false));
        map.put(markForDeathField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
        performCreate(map,null);

      }
      else
      {
        // Upgrade goes here, if needed
      }

      // Do child tables.
      intrinsicLinkManager.install(jobsTable,jobsColumn);
      deleteDepsManager.install(jobsTable,jobsColumn,getTableName(),idField);

      // Do indexes
      IndexDescription jobLinktypeParentIndex = new IndexDescription(true,new String[]{jobIDField,parentIDHashField,linkTypeField});
      IndexDescription jobDeathIndex = new IndexDescription(false,new String[]{jobIDField,markForDeathField,parentIDHashField,linkTypeField});

      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (jobLinktypeParentIndex != null && id.equals(jobLinktypeParentIndex))
          jobLinktypeParentIndex = null;
        else if (jobDeathIndex != null && id.equals(jobDeathIndex))
          jobDeathIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      if (jobLinktypeParentIndex != null)
        performAddIndex(null,jobLinktypeParentIndex);

      if (jobDeathIndex != null)
        performAddIndex(null,jobDeathIndex);

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
      deleteDepsManager.deinstall();
      intrinsicLinkManager.deinstall();
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

  /** Go from string to mark.
  *@param value is the string.
  *@return the status value.
  */
  public static int stringToMark(String value)
    throws ManifoldCFException
  {
    Integer x = (Integer)markMap.get(value);
    if (x == null)
      throw new ManifoldCFException("Bad mark value: '"+value+"'");
    return x.intValue();
  }

  /** Go from mark to string.
  *@param mark is the mark.
  *@return the string.
  */
  public static String markToString(int mark)
    throws ManifoldCFException
  {
    switch (mark)
    {
    case MARK_NORMAL:
      return "N";
    case MARK_QUEUED:
      return "Q";
    case MARK_DELETING:
      return "D";
    default:
      throw new ManifoldCFException("Bad mark value");
    }
  }

  /** Delete an owner (and clean up the corresponding hopcount rows).
  */
  public void deleteOwner(Long jobID)
    throws ManifoldCFException
  {
    // Delete the intrinsic rows belonging to this job.
    intrinsicLinkManager.deleteOwner(jobID);

    // Delete the deletedeps rows
    deleteDepsManager.deleteJob(jobID);

    // Delete our own rows.
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID)});
    performDelete("WHERE "+query,list,null);
    noteModifications(0,0,1);
  }

  /** Reset, at startup time.
  *@param processID is the process ID.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    intrinsicLinkManager.restart(processID);
  }

  /** Clean up after all process IDs.
  */
  public void restart()
    throws ManifoldCFException
  {
    intrinsicLinkManager.restart();
  }
  
  /** Restart entire cluster.
  */
  public void restartCluster()
    throws ManifoldCFException
  {
    intrinsicLinkManager.restartCluster();
  }
  
  /** Record a references from a set of documents to the root.  These will be marked as "new" or "existing", and
  * will have a null linktype.
  */
  public void recordSeedReferences(Long jobID, String[] legalLinkTypes, String[] targetDocumentIDHashes, int hopcountMethod, String processID)
    throws ManifoldCFException
  {
    doRecord(jobID,legalLinkTypes,"",targetDocumentIDHashes,"",hopcountMethod,processID);
  }

  /** Finish seed references.  Seed references are special in that the only source is the root.
  */
  public void finishSeedReferences(Long jobID, String[] legalLinkTypes, int hopcountMethod)
    throws ManifoldCFException
  {
    doFinish(jobID,legalLinkTypes,new String[]{""},hopcountMethod);
  }

  /** Record a reference from source to target.  This reference will be marked as "new" or "existing".
  */
  public boolean recordReference(Long jobID, String[] legalLinkTypes, String sourceDocumentIDHash, String targetDocumentIDHash, String linkType,
    int hopcountMethod, String processID)
    throws ManifoldCFException
  {
    return doRecord(jobID,legalLinkTypes,sourceDocumentIDHash,new String[]{targetDocumentIDHash},linkType,hopcountMethod,processID)[0];
  }

  /** Record a set of references from source to target.  This reference will be marked as "new" or "existing".
  */
  public boolean[] recordReferences(Long jobID, String[] legalLinkTypes, String sourceDocumentIDHash, String[] targetDocumentIDHashes, String linkType,
    int hopcountMethod, String processID)
    throws ManifoldCFException
  {
    return doRecord(jobID,legalLinkTypes,sourceDocumentIDHash,targetDocumentIDHashes,linkType,hopcountMethod,processID);
  }

  /** Complete a recalculation pass for a set of source documents.  All child links that are not marked as "new"
  * or "existing" will be removed.  At the completion of this pass, the links will have their "new" flag cleared.
  */
  public void finishParents(Long jobID, String[] legalLinkTypes, String[] sourceDocumentHashes, int hopcountMethod)
    throws ManifoldCFException
  {
    doFinish(jobID,legalLinkTypes,sourceDocumentHashes,hopcountMethod);
  }

  /** Revert newly-added links, because of a possibly incomplete document processing phase.
  * All child links marked as "new" will be removed, and all links marked as "existing" will be
  * reset to be "base".
  */
  public void revertParents(Long jobID, String[] sourceDocumentHashes)
    throws ManifoldCFException
  {
    intrinsicLinkManager.revertLinks(jobID,sourceDocumentHashes);
  }
  
  /** Do the work of recording source-target references. */
  protected boolean[] doRecord(Long jobID, String[] legalLinkTypes, String sourceDocumentIDHash, String[] targetDocumentIDHashes, String linkType,
    int hopcountMethod, String processID)
    throws ManifoldCFException
  {
    // NOTE: In order for the revertParents() call above to be correct in its current form,
    // this method would need to be revised to not process any additions until the finishParents() call
    // is made.  At the moment, revertParents() is not used by any thread.
    // TBD, MHL
    boolean[] rval = new boolean[targetDocumentIDHashes.length];
    for (int i = 0; i < rval.length; i++)
    {
      rval[i] = false;
    }
    
    String[] newReferences = intrinsicLinkManager.recordReferences(jobID,sourceDocumentIDHash,targetDocumentIDHashes,linkType,processID);
    if (newReferences.length > 0)
    {
      // There are added links.
        

      // The add causes hopcount records to be queued for processing (and created if they don't exist).
      // ALL the hopcount records for the target document ids must be queued, for all the link types
      // there are for this job.  Other times, the queuing requirement is less stringent, such as
      // when a hopcount for one linktype changes.  In those cases we only want to queue up hopcount
      // records corresponding to the changed record.

      // What we need to do is create a queue which contains only the target hopcount table rows, if they
      // exist.  Then we run the update algorithm until the cache is empty.

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Queueing "+Integer.toString(targetDocumentIDHashes.length)+" documents");

      // Since we really want efficiency, we can write the answer in place now, based on the current
      // hopcount rows.  This works even if the current row is out of date, because if we change the
      // current row's value, the target rows will be requeued at that point.

      // When we record new links, we must come up with an initial calculation or requeue ALL legal link
      // types.  If this isn't done, then we cannot guarantee that the target record will exist - and
      // somebody will then interpret the distance as being 'infinity'.
      //
      // It would be possible to change this but we would then also need to change how a missing record
      // would be interpreted.

      //if (!(linkType == null || linkType.length() == 0))
      //      legalLinkTypes = new String[]{linkType};

      // So, let's load what we have for hopcount and dependencies for sourceDocumentID.

      Answer[] estimates = new Answer[legalLinkTypes.length];

      if (sourceDocumentIDHash == null || sourceDocumentIDHash.length() == 0)
      {
        for (int i = 0; i < estimates.length; i++)
        {
          estimates[i] = new Answer(0);
        }
      }
      else
      {
        StringBuilder sb = new StringBuilder("SELECT ");
        ArrayList list = new ArrayList();
          
        sb.append(idField).append(",")
          .append(distanceField).append(",")
          .append(linkTypeField)
          .append(" FROM ").append(getTableName()).append(" WHERE ");
          
        sb.append(buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(jobIDField,jobID),
          new UnitaryClause(parentIDHashField,sourceDocumentIDHash),
          new MultiClause(linkTypeField,legalLinkTypes)}));

        IResultSet set = performQuery(sb.toString(),list,null,null);
        Map<String,Answer> answerMap = new HashMap<String,Answer>();
        for (int i = 0; i < estimates.length; i++)
        {
          estimates[i] = new Answer(ANSWER_INFINITY);
          answerMap.put(legalLinkTypes[i],estimates[i]);
        }

        for (int i = 0; i < set.getRowCount(); i++)
        {
          IResultRow row = set.getRow(i);
          Long id = (Long)row.getValue(idField);
          DeleteDependency[] dds;
          if (hopcountMethod != IJobDescription.HOPCOUNT_NEVERDELETE)
            dds = deleteDepsManager.getDeleteDependencies(id);
          else
            dds = new DeleteDependency[0];
          Long distance = (Long)row.getValue(distanceField);
          String recordedLinkType = (String)row.getValue(linkTypeField);
          Answer a = answerMap.get(recordedLinkType);
          int recordedDistance = (int)distance.longValue();
          if (recordedDistance != -1)
          {
            a.setAnswer(recordedDistance,dds);
          }
        }
      }

      // Now add these documents to the processing queue
      boolean[] hasChanged = addToProcessingQueue(jobID,legalLinkTypes,newReferences,estimates,sourceDocumentIDHash,linkType,hopcountMethod);

      // First, note them in return value
      Map<String,Boolean> changeMap = new HashMap<String,Boolean>();
      for (int i = 0; i < newReferences.length; i++)
      {
        changeMap.put(newReferences[i],new Boolean(hasChanged[i]));
      }
      for (int i = 0; i < rval.length; i++)
      {
        Boolean x = changeMap.get(targetDocumentIDHashes[i]);
        if (x != null && x.booleanValue())
          rval[i] = true;
      }

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Done queueing "+Integer.toString(targetDocumentIDHashes.length)+" documents");
    }
    return rval;
  }

  /** Remove a set of document identifiers specified as a criteria.  This will remove hopcount rows and
  * also intrinsic links that have the specified document identifiers as sources.
  */
  public void deleteMatchingDocuments(Long jobID, String[] legalLinkTypes,
    String joinTableName,
    String joinTableIDColumn, String joinTableJobColumn,
    String joinTableCriteria, ArrayList joinTableParams,
    int hopcountMethod)
    throws ManifoldCFException
  {
    // This should work similarly to deleteDocumentIdentifiers() except that the identifiers
    // come from a subquery rather than a list.
    // This also removes the links themselves...
    if (hopcountMethod == IJobDescription.HOPCOUNT_ACCURATE)
    {
      doDeleteDocuments(jobID,joinTableName,
        joinTableIDColumn,joinTableJobColumn,
        joinTableCriteria,joinTableParams);
    }
  }


  /** Remove a set of document identifier hashes.  This will also remove the intrinsic links that have these document
  * identifier hashes as sources, as well as invalidating cached hop counts that depend on them.
  */
  public void deleteDocumentIdentifiers(Long jobID, String[] legalLinkTypes, String[] documentHashes, int hopcountMethod)
    throws ManifoldCFException
  {
    // What I want to do here is to first perform the invalidation of the cached hopcounts.
    //
    // UPDATE hopcount SET markfordeath='X' WHERE EXISTS(SELECT 'x' FROM hopdeletedeps t0 WHERE t0.ownerid=hopcount.id AND t0.jobid=<jobid>
    //      AND EXISTS(SELECT 'x' FROM intrinsiclinks t1 WHERE t1.linktype=t0.linktype AND t1.parentid=t0.parentid
    //              AND t1.childid=t0.childid AND t1.jobid=<jobid> AND t1.childid IN(<sourcedocs>)))
    //
    // ... and then, re-evaluate all hopcount records and their dependencies that are marked for delete.
    //


    // This also removes the links themselves...
    if (hopcountMethod == IJobDescription.HOPCOUNT_ACCURATE)
      doDeleteDocuments(jobID,documentHashes);

  }

  /** Calculate a bunch of hop-counts.  The values returned are only guaranteed to be an upper bound, unless
  * the queue has recently been processed (via processQueue below).  -1 will be returned to indicate "infinity".
  */
  public int[] findHopCounts(Long jobID, String[] parentIdentifierHashes, String linkType)
    throws ManifoldCFException
  {
    // No transaction, since we can happily interpret whatever comes back.
    ArrayList list = new ArrayList();

    int[] rval = new int[parentIdentifierHashes.length];
    HashMap rvalMap = new HashMap();
    int i = 0;
    while (i < rval.length)
    {
      rval[i] = -1;
      rvalMap.put(parentIdentifierHashes[i],new Integer(i));
      i++;
    }

    int maxClause = maxClauseProcessFind(jobID,linkType);
    i = 0;
    int k = 0;
    while (i < parentIdentifierHashes.length)
    {
      if (k == maxClause)
      {
        processFind(rval,rvalMap,jobID,linkType,list);
        k = 0;
        list.clear();
      }
      list.add(parentIdentifierHashes[i]);
      k++;
      i++;
    }
    if (k > 0)
      processFind(rval,rvalMap,jobID,linkType,list);

    return rval;
  }

  /** Find max clause count.
  */
  protected int maxClauseProcessFind(Long jobID, String linkType)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(linkTypeField,linkType)});
  }
  
  /** Process a portion of a find request for hopcount information.
  */
  protected void processFind(int[] rval, Map rvalMap, Long jobID, String linkType, ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list),
      new UnitaryClause(linkTypeField,linkType)});
      
    IResultSet set = performQuery("SELECT "+distanceField+","+parentIDHashField+" FROM "+getTableName()+" WHERE "+query,newList,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      Long distance = (Long)row.getValue(distanceField);
      rval[((Integer)rvalMap.get(parentIDHash)).intValue()] = (int)distance.longValue();
    }
  }

  /** Process a stage of the propagation queue for a job.
  *@param jobID is the job we need to have the hopcount propagated for.
  *@return true if the queue is empty.
  */
  public boolean processQueue(Long jobID, String[] legalLinkTypes, int hopcountMethod)
    throws ManifoldCFException
  {
    // We can't instantiate the DocumentHash object here, because it will wind up having
    // cached in it the answers from the previous round of calculation.  That round had
    // a different set of marked nodes than the current round.

    ArrayList list = new ArrayList();

    // Pick off up to n queue items at a time.  We don't want to pick off too many (because
    // then we wind up delaying other threads too much), nor do we want to do one at a time
    // (because that is inefficient against the database), so I picked 200 as being 200+x faster
    // than 1...
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(markForDeathField,markToString(MARK_QUEUED))});
      
    IResultSet set = performQuery("SELECT "+linkTypeField+","+parentIDHashField+" FROM "+
      getTableName()+" WHERE "+query+" "+constructOffsetLimitClause(0,200)+" FOR UPDATE",list,null,null,200);

    // No more entries == we are done
    if (set.getRowCount() == 0)
      return true;

    DocumentHash dh = new DocumentHash(jobID,legalLinkTypes,hopcountMethod);

    Question[] questions = new Question[set.getRowCount()];

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      String parentIdentifierHash = (String)row.getValue(parentIDHashField);
      String linkType = (String)row.getValue(linkTypeField);

      // All documents in the set have the same basic assumptions; another set may be queued
      // as a side effect of some of these getting resolved, but treating them in chunks
      // seems like it should not cause problems (because the same underlying assumptions
      // underlie the whole chunk).  The side effects *may* cause other documents that are
      // still in the queue to be evaluated as well, in which case they will disappear from
      // the queue and not be processed further.

      // Create a document hash object.
      questions[i] = new Question(parentIdentifierHash,linkType);
      i++;
    }

    // We don't care what the response is; we just want the documents to leave the queue.
    dh.askQuestions(questions);
    return false;
  }

  /** Calculate max clauses */
  protected int maxClausePerformFindMissingRecords(Long jobID, String[] affectedLinkTypes)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(linkTypeField,affectedLinkTypes)});
  }
  
  /** Limited find for missing records.
  */
  protected void performFindMissingRecords(Long jobID, String[] affectedLinkTypes, ArrayList list, Map<Question,Long> matchMap)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new MultiClause(parentIDHashField,list),
      new MultiClause(linkTypeField,affectedLinkTypes)});
      
    // The naive query is this - but postgres does not find the index this way:
    //IResultSet set = performQuery("SELECT "+parentIDField+","+linkTypeField+" FROM "+getTableName()+" WHERE "+
    //      parentIDField+" IN("+query+") AND "+jobIDField+"=?",list,null,null);
    IResultSet set = performQuery("SELECT "+parentIDHashField+","+linkTypeField+","+distanceField+" FROM "+getTableName()+" WHERE "+query,newList,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String docIDHash = (String)row.getValue(parentIDHashField);
      String linkType = (String)row.getValue(linkTypeField);
      Long distance = (Long)row.getValue(distanceField);
      Question q = new Question(docIDHash,linkType);
      matchMap.put(q,distance);
    }
  }


  /** Add documents to the processing queue.  For the supplied bunch of link types and document ids,
  * the corresponding hopcount records will be marked as being queued.  If, for example, the affected link types
  * are 'link' and 'redirect', and the specified document id's are 'A' and 'B' and 'C', then six hopcount
  * rows will be created and/or queued.
  * The values that this code uses for initial distance or delete dependencies for each of the hopcount
  * rows combinatorially described above are calculated by this method by starting with the passed-in hopcount values
  * and dependencies for each of the affectedLinkTypes for the specified "source" document.  The result estimates are then
  * generated by passing these values and dependencies over the links to the target document identifiers, presuming that
  * the link is of the supplied link type.
  *
  *@param jobID is the job the documents belong to.
  *@param affectedLinkTypes are the set of affected link types.
  *@param documentIDHashes are the documents to add.
  *@param startingAnswers are the hopcounts for the documents as they are currently known.
  *@param sourceDocumentIDHash is the source document identifier for the links from source to target documents.
  *@param linkType is the link type for this queue addition.
  *@param hopcountMethod is the desired method of managing hopcounts.
  *@return a boolean array which is the subset of documentIDHashes whose distances may have changed.
  */
  protected boolean[] addToProcessingQueue(Long jobID, String[] affectedLinkTypes, String[] documentIDHashes,
    Answer[] startingAnswers, String sourceDocumentIDHash, String linkType, int hopcountMethod)
    throws ManifoldCFException
  {
    // If we're given the source hopcount distances, we should write the derived target values into the NEW
    // hopcount records we create, because it will save much database access in the long run, and handles the
    // typical case in an inexpensive way.  These records do not even need to be queued - since we are creating
    // them, we know there are no other paths to them yet (or paths that depend upon them).  So we can write in
    // 'final' values, which will need to be updated only if the source hopcount row's distance is lowered (and
    // then, the targets will all be requeued anyhow).
    //
    // For EXISTING hopcount rows, I've opted to not consider the passed-in distance estimates.  Even if I should
    // detect that the hopcount has improved, there would still be the requirement of requeuing all the target's
    // targets.  This kind of propagation is probably best handled by the normal queue processing code, which does
    // as much in bulk as is possible.  So, for existing target hopcount rows, they simply get queued.

    if (Logging.hopcount.isDebugEnabled())
    {
      Logging.hopcount.debug("Adding "+Integer.toString(documentIDHashes.length)+" documents to processing queue");
      for (int z = 0; z < documentIDHashes.length; z++)
      {
        Logging.hopcount.debug("  Adding '"+documentIDHashes[z]+"' to processing queue");
      }
      Logging.hopcount.debug("The source id is '"+sourceDocumentIDHash+"' and linktype is '"+linkType+"', and there are "+
        Integer.toString(affectedLinkTypes.length)+" affected link types, as below:");
      for (int z = 0; z < affectedLinkTypes.length; z++)
      {
        Logging.hopcount.debug("  Linktype '"+affectedLinkTypes[z]+"', current distance "+Integer.toString(startingAnswers[z].getAnswer())+" with "+
          Integer.toString(startingAnswers[z].countDeleteDependencies())+" delete dependencies.");
      }
    }


    // If hopcount records for the targets for the links don't yet exist, we had better create them,
    // so we can make sure they are added to the queue properly.

    // Make a map of the combinations of link type and document id we want to have present
    Map<Question,Long> matchMap = new HashMap();

    // Make a map from the link type to the corresponding Answer object
    Map<String,Answer> answerMap = new HashMap<String,Answer>();
    for (int u = 0; u < affectedLinkTypes.length; u++)
    {
      answerMap.put(affectedLinkTypes[u],startingAnswers[u]);
    }

    boolean[] rval = new boolean[documentIDHashes.length];
    for (int i = 0; i < rval.length; i++)
    {
      rval[i] = false;
    }

    // I don't think we have to throw a table lock here, because even though we base decisions for insertion on the lack of existence
    // of a record, there can be only one thread in here at a time.

    int maxClause = maxClausePerformFindMissingRecords(jobID,affectedLinkTypes);
    ArrayList list = new ArrayList();
      
    int k = 0;
    for (int i = 0; i < documentIDHashes.length; i++)
    {
      String documentIDHash = documentIDHashes[i];
        
      if (k == maxClause)
      {
        performFindMissingRecords(jobID,affectedLinkTypes,list,matchMap);
        k = 0;
        list.clear();
      }
        
      list.add(documentIDHash);
      k++;
    }
    if (k > 0)
      performFindMissingRecords(jobID,affectedLinkTypes,list,matchMap);

    // Repeat our pass through the documents and legal link types.  For each document/legal link type,
    // see if there was an existing row.  If not, we create a row.  If so, we compare the recorded
    // distance against the distance estimate we would have given it.  If the new distance is LOWER, it gets left around
    // for queuing.

    HashMap map = new HashMap();
    for (int i = 0; i < documentIDHashes.length; i++)
    {
      String documentIDHash = documentIDHashes[i];
      for (int j = 0; j < affectedLinkTypes.length; j++)
      {
        String affectedLinkType = affectedLinkTypes[j];
        Question q = new Question(documentIDHash,affectedLinkType);

        // Calculate what our new answer would be.
        Answer startingAnswer = (Answer)answerMap.get(affectedLinkType);
        int newAnswerValue = startingAnswer.getAnswer();
        if (newAnswerValue >= 0 && affectedLinkType.equals(linkType))
          newAnswerValue++;

        // Now, see if there's a distance already present.
        Long currentDistance = (Long)matchMap.get(q);
        if (currentDistance == null)
        {
          // Prepare to do an insert.
          // The dependencies are the old dependencies, plus the one we are about to add.
          DeleteDependency dd = new DeleteDependency(linkType,documentIDHash,sourceDocumentIDHash);
          // Build a new answer, based on the starting answer and the kind of link this is.
          map.clear();
          Long hopCountID = new Long(IDFactory.make(threadContext));
          map.put(idField,hopCountID);
          map.put(parentIDHashField,q.getDocumentIdentifierHash());
          map.put(linkTypeField,q.getLinkType());
          if (newAnswerValue == ANSWER_INFINITY)
            map.put(distanceField,new Long(-1L));
          else
            map.put(distanceField,new Long((long)newAnswerValue));
          map.put(jobIDField,jobID);
          map.put(markForDeathField,markToString(MARK_NORMAL));
          if (Logging.hopcount.isDebugEnabled())
            Logging.hopcount.debug("Inserting new record for '"+documentIDHash+"' linktype '"+affectedLinkType+"' distance "+Integer.toString(newAnswerValue)+" for job "+jobID);
          performInsert(map,null);
          noteModifications(1,0,0);
          if (hopcountMethod != IJobDescription.HOPCOUNT_NEVERDELETE)
          {
            deleteDepsManager.writeDependency(hopCountID,jobID,dd);
            Iterator iter2 = startingAnswer.getDeleteDependencies();
            while (iter2.hasNext())
            {
              dd = (DeleteDependency)iter2.next();
              deleteDepsManager.writeDependency(hopCountID,jobID,dd);
            }
          }
        }
        else
        {
          // If the new distance >= saved distance, don't queue anything.  That means, remove it from the hash.
          int oldAnswerValue = (int)currentDistance.longValue();
          if (!(newAnswerValue >= 0 && (oldAnswerValue < 0 || newAnswerValue < oldAnswerValue)))
          {
            // New answer is no better than the old answer, so don't queue
            if (Logging.hopcount.isDebugEnabled())
              Logging.hopcount.debug("Existing record for '"+documentIDHash+"' linktype '"+affectedLinkType+"' has better distance "+Integer.toString(oldAnswerValue)+
              " than new distance "+Integer.toString(newAnswerValue)+", so not queuing for job "+jobID);
            matchMap.remove(q);
          }
          else
            rval[i] = true;
        }
      }
    }

    // For all the records still in the matchmap, queue them.

    // The query I want to run is:
    // UPDATE hopcount SET markfordeath='Q' WHERE jobID=? AND parentid IN (...)
    // but postgresql is stupid and won't use the index that way.  So do this instead:
    // UPDATE hopcount SET markfordeath='Q' WHERE (jobID=? AND parentid=?) OR (jobid=? AND parentid=?)...

    maxClause = getMaxOrClause();
    StringBuilder sb = new StringBuilder();
    list = new ArrayList();
    k = 0;
    for (int i = 0; i < documentIDHashes.length; i++)
    {
      String documentIDHash = documentIDHashes[i];
      for (int j = 0; j < affectedLinkTypes.length; j++)
      {
        String affectedLinkType = affectedLinkTypes[j];

        Question q = new Question(documentIDHash,affectedLinkType);
        if (matchMap.get(q) != null)
        {
          if (k == maxClause)
          {
            performMarkAddDeps(sb.toString(),list);
            k = 0;
            sb.setLength(0);
            list.clear();
          }
          if (k > 0)
            sb.append(" OR ");

          // We only want to queue up hopcount records that correspond to the affected link types.
          //
          // Also, to reduce deadlock, do not update any records that are already marked as queued.  These would be infrequent,
          // but they nevertheless seem to cause deadlock very easily.
          //
          if (Logging.hopcount.isDebugEnabled())
            Logging.hopcount.debug("Queuing '"+documentIDHash+"' linktype '"+affectedLinkType+"' for job "+jobID);
            
          sb.append(buildConjunctionClause(list,new ClauseDescription[]{
            new UnitaryClause(jobIDField,jobID),
            new MultiClause(markForDeathField,new Object[]{
              markToString(MARK_NORMAL),
              markToString(MARK_DELETING)}),
            new UnitaryClause(parentIDHashField,documentIDHash),
            new UnitaryClause(linkTypeField,affectedLinkType)}));
              
          k++;
        }
      }
    }
    if (k > 0)
      performMarkAddDeps(sb.toString(),list);

    // Leave the dependency records for the queued rows.  This will save lots of work if we decide not to
    // update the distance.  It's safe to leave the old dep records, because they must only record links that furnish
    // A minimal path, not THE minimal path.

    noteModifications(0,documentIDHashes.length,0);
    return rval;
  }

  /** Do the work of marking add-dep-dependent links in the hopcount table. */
  protected void performMarkAddDeps(String query, ArrayList list)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(markForDeathField,markToString(MARK_QUEUED));
    performUpdate(map,"WHERE "+query,list,null);
  }


  /** Method that does the work of "finishing" a set of child references. */
  protected void doFinish(Long jobID, String[] legalLinkTypes, String[] sourceDocumentHashes, int hopcountMethod)
    throws ManifoldCFException
  {
    if (hopcountMethod == IJobDescription.HOPCOUNT_ACCURATE)
    {
      // First, blow the cache.
      //
      // To do this, I'd the following queries to occur:
      //
      // UPDATE hopcount SET markfordeath='Q' WHERE EXISTS(SELECT 'x' FROM hopdeletedeps t0 WHERE t0.ownerid=hopcount.id AND t0.jobid=<jobid>
      //      AND EXISTS(SELECT 'x' FROM intrinsiclinks t1 WHERE t1.linktype=t0.linktype AND t1.parentid=t0.parentid
      //              AND t1.childid=t0.childid AND t1.jobid=<jobid> AND t1.isnew=<base> AND t1.childid IN(<sourcedocs>)))
      //
      // ... and then, get rid of all hopcount records and their dependencies that are marked for delete.


      // Invalidate all links with the given source documents that match the common expression
      doDeleteInvalidation(jobID,sourceDocumentHashes);
    }
    // Make all new and existing links become just "base" again.
    intrinsicLinkManager.restoreLinks(jobID,sourceDocumentHashes);
  }

  /** Invalidate links that start with a specific set of documents, described by
  * a table join.
  */
  protected void doDeleteDocuments(Long jobID,
    String joinTableName,
    String joinTableIDColumn, String joinTableJobColumn,
    String joinTableCriteria, ArrayList joinTableParams)
    throws ManifoldCFException
  {
    if (Logging.hopcount.isDebugEnabled())
    {
      Logging.hopcount.debug("Marking for delete for job "+jobID+" all hopcount document references"+
        " from table "+joinTableName+" matching "+joinTableCriteria);
    }
    
    // For this query, postgresql seems to not do the right thing unless the subclause is a three-way join:
    //
    // UPDATE hopcount SET x=y WHERE id IN(SELECT t0.ownerid FROM hopdeletedeps t0,jobqueue t99,intrinsiclink t1 WHERE
    //      t0.jobid=? and t99.jobid=? and t1.jobid=? and
    //      t0.childidhash=t99.dochash and t0.childid=t99.docid and t99.status='P' and
    //      t0.parentidhash=t1.parentidhash and t0.childidhash=t1.childidhash and t0.linktype=t1.linktype and
    //      t0.parentid=t1.parentid and t0.childid=t1.childid)
        
    // MHL to figure out the "correct" way to state this for all databases

    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList list = new ArrayList();
        
    sb.append(idField).append(" IN(SELECT t0.").append(deleteDepsManager.ownerIDField).append(" FROM ")
      .append(deleteDepsManager.getTableName()).append(" t0,").append(joinTableName).append(",")
      .append(intrinsicLinkManager.getTableName()).append(" t1 WHERE ");

    sb.append(buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause("t0."+deleteDepsManager.jobIDField,jobID)})).append(" AND ");

    sb.append(buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause("t1."+intrinsicLinkManager.jobIDField,jobID),
      new JoinClause("t1."+intrinsicLinkManager.parentIDHashField,"t0."+deleteDepsManager.parentIDHashField),
      new JoinClause("t1."+intrinsicLinkManager.linkTypeField,"t0."+deleteDepsManager.linkTypeField),
      new JoinClause("t1."+intrinsicLinkManager.childIDHashField,"t0."+deleteDepsManager.childIDHashField)})).append(" AND ");

    sb.append(buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(joinTableJobColumn,jobID),
      new JoinClause(joinTableIDColumn,"t0."+deleteDepsManager.childIDHashField)})).append(" AND ");
          
    sb.append(joinTableCriteria);
    list.addAll(joinTableParams);

    sb.append(")");

    HashMap map = new HashMap();
    // These are whacked back to "infinity" to avoid infinite looping in a cut-off graph.
    map.put(distanceField,new Long(-1L));
    map.put(markForDeathField,markToString(MARK_DELETING));
    performUpdate(map,sb.toString(),list,null);
    noteModifications(0,1,0);
      
    // We do NOT do the parentID because otherwise we have the potential to delete links that we need later.  See CONNECTORS-501.

    if (Logging.hopcount.isDebugEnabled())
      Logging.hopcount.debug("Done setting hopcount rows for job "+jobID+" to initial distances");

    // Remove the intrinsic links that we said we would - BEFORE we evaluate the queue.
    intrinsicLinkManager.removeDocumentLinks(jobID,
      joinTableName,
      joinTableIDColumn,joinTableJobColumn,
      joinTableCriteria,joinTableParams);

    // Remove the delete dependencies of the nodes marked as being queued, with distance infinity.
    ArrayList queryList = new ArrayList();
    String query = buildConjunctionClause(queryList,new ClauseDescription[]{
      new UnitaryClause(jobIDField,jobID),
      new UnitaryClause(markForDeathField,markToString(MARK_DELETING))});
    deleteDepsManager.removeMarkedRows(getTableName(),idField,query,queryList);

    // Set the hopcount rows back to just "queued".
    HashMap newMap = new HashMap();
    newMap.put(markForDeathField,markToString(MARK_QUEUED));
    performUpdate(newMap,"WHERE "+query,queryList,null);

    // At this point, we have a queue that contains all the hopcount entries that our dependencies told us
    // needed to change as a result of the deletions.  Evaluating the queue will clean up hopcount entries
    // and dependencies that are just going away, as well as updating those that are still around but
    // will have new hopcount values.

    if (Logging.hopcount.isDebugEnabled())
      Logging.hopcount.debug("Done queueing for deletion for "+jobID);

  }
  
  /** Invalidate links that start with a specific set of documents.
  */
  protected void doDeleteDocuments(Long jobID,
    String[] documentHashes)
    throws ManifoldCFException
  {
    // Clear up hopcount table
    if (documentHashes.length > 0)
    {
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Marking for delete for job "+jobID+" all hopcount document references"+
          " from:");
        for (int k = 0; k < documentHashes.length; k++)
        {
          Logging.hopcount.debug("  "+documentHashes[k]);
        }
      }

      // The query form I found that seems to work ok with postgresql looks like this:
      //
      // UPDATE hopcount SET x=y WHERE id IN (SELECT ownerid FROM hopdeletedeps t0
      //   WHERE ((t0.jobid=? AND t0.childid=?)
      //       OR (t0.jobid=? AND t0.childid=?)
      //       ...
      //       OR (t0.jobid=? AND t0.childid=?))
      //       AND EXISTS(SELECT 'x' FROM intrinsiclink t1 WHERE t1.linktype=t0.linktype
      //              AND t1.parentid=t0.parentid AND t1.childid=t0.childid AND t1.jobid=t0.jobid AND t1.isnew='B'))
      //
      // Here's a revised form that would take advantage of postgres's better ability to work with joins, if this should
      // turn out to be necessary:
      //
      // UPDATE hopcount SET x=y WHERE id IN (SELECT t0.ownerid FROM hopdeletedeps t0, intrinsiclink t1
      //      WHERE t1.childidhash=t0.childidhash AND t1.jobid=? AND t1.linktype=t0.linktype AND t1.parentid=t0.parentid AND t1.childid=t0.childid AND t1.isnew='B'
      //      AND ((t0.jobid=? AND t0.childidhash=? AND t0.childid=?)
      //       OR (t0.jobid=? AND t0.childidhash=? AND t0.childid=?)
      //       ...
      //       OR (t0.jobid=? AND t0.childidhash=? AND t0.childid=?))

      int maxClause = maxClauseMarkForDocumentDelete(jobID);
      ArrayList list = new ArrayList();
      int i = 0;
      int k = 0;
      while (i < documentHashes.length)
      {
        if (k == maxClause)
        {
          markForDocumentDelete(jobID,list);
          list.clear();
          k = 0;
        }
        list.add(documentHashes[i]);
        i++;
        k++;
      }
      if (k > 0)
        markForDocumentDelete(jobID,list);
      noteModifications(0,documentHashes.length,0);

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Done setting hopcount rows for job "+jobID+" to initial distances");

      // Remove the intrinsic links that we said we would - BEFORE we evaluate the queue.
      intrinsicLinkManager.removeDocumentLinks(jobID,
        documentHashes);

      // Remove the delete dependencies of the nodes marked as being queued, with distance infinity.
      ArrayList queryList = new ArrayList();
      String query = buildConjunctionClause(queryList,new ClauseDescription[]{
        new UnitaryClause(jobIDField,jobID),
        new UnitaryClause(markForDeathField,markToString(MARK_DELETING))});
      deleteDepsManager.removeMarkedRows(getTableName(),idField,query,queryList);

      // Set the hopcount rows back to just "queued".
      HashMap newMap = new HashMap();
      newMap.put(markForDeathField,markToString(MARK_QUEUED));
      performUpdate(newMap,"WHERE "+query,queryList,null);

      // At this point, we have a queue that contains all the hopcount entries that our dependencies told us
      // needed to change as a result of the deletions.  Evaluating the queue will clean up hopcount entries
      // and dependencies that are just going away, as well as updating those that are still around but
      // will have new hopcount values.

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Done queueing for deletion for "+jobID);

    }
  }
  
  protected int maxClauseMarkForDocumentDelete(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause("t0."+deleteDepsManager.jobIDField,jobID)});
  }

  protected void markForDocumentDelete(Long jobID, ArrayList list)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList thisList = new ArrayList();

    sb.append(idField).append(" IN(SELECT ").append(deleteDepsManager.ownerIDField).append(" FROM ").append(deleteDepsManager.getTableName()).append(" t0 WHERE ")
      .append(buildConjunctionClause(thisList,new ClauseDescription[]{
        new UnitaryClause("t0."+deleteDepsManager.jobIDField,jobID),
        new MultiClause("t0."+deleteDepsManager.childIDHashField,list)})).append(" AND ");
        
    sb.append("EXISTS(SELECT 'x' FROM ").append(intrinsicLinkManager.getTableName()).append(" t1 WHERE ")
      .append(buildConjunctionClause(thisList,new ClauseDescription[]{
        new JoinClause("t1."+intrinsicLinkManager.jobIDField,"t0."+deleteDepsManager.jobIDField),
        new JoinClause("t1."+intrinsicLinkManager.linkTypeField,"t0."+deleteDepsManager.linkTypeField),
        new JoinClause("t1."+intrinsicLinkManager.parentIDHashField,"t0."+deleteDepsManager.parentIDHashField),
        new JoinClause("t1."+intrinsicLinkManager.childIDHashField,"t0."+deleteDepsManager.childIDHashField)}));
    
    sb.append("))");

    HashMap map = new HashMap();
    // These are whacked back to "infinity" to avoid infinite looping in a cut-off graph.
    map.put(distanceField,new Long(-1L));
    map.put(markForDeathField,markToString(MARK_DELETING));
    performUpdate(map,sb.toString(),thisList,null);

    // We do NOT do the parentID because we need to leave intrinsic links around that could be used again.
    // See CONNECTORS-501.
  }

  /** Invalidate links meeting a simple criteria which have a given set of source documents.  This also runs a queue
  * which is initialized with all the documents that have sources that exist in the hopcount table.  The purpose
  * of that queue is to re-establish non-infinite values for all nodes that are described in IntrinsicLinks, that are
  * still connected to the root. */
  protected void doDeleteInvalidation(Long jobID,
    String[] sourceDocumentHashes)
    throws ManifoldCFException
  {
    ArrayList commonNewList = new ArrayList();
    commonNewList.add(intrinsicLinkManager.statusToString(intrinsicLinkManager.LINKSTATUS_BASE));
    String commonNewExpression = intrinsicLinkManager.newField+"=?";

    // Clear up hopcount table
    if (sourceDocumentHashes.length > 0)
    {
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Marking for delete for job "+jobID+" all target document references matching '"+commonNewExpression+"'"+
          " from:");
        for (int k = 0; k < sourceDocumentHashes.length; k++)
        {
          Logging.hopcount.debug("  "+sourceDocumentHashes[k]);
        }
      }

      // The query form I found that seems to work ok with postgresql looks like this:
      //
      // UPDATE hopcount SET x=y WHERE id IN (SELECT ownerid FROM hopdeletedeps t0
      //   WHERE ((t0.jobid=? AND t0.childid=?)
      //       OR (t0.jobid=? AND t0.childid=?)
      //       ...
      //       OR (t0.jobid=? AND t0.childid=?))
      //       AND EXISTS(SELECT 'x' FROM intrinsiclink t1 WHERE t1.linktype=t0.linktype
      //              AND t1.parentid=t0.parentid AND t1.childid=t0.childid AND t1.jobid=t0.jobid AND t1.isnew='B'))
      //
      // Here's a revised form that would take advantage of postgres's better ability to work with joins, if this should
      // turn out to be necessary:
      //
      // UPDATE hopcount SET x=y WHERE id IN (SELECT t0.ownerid FROM hopdeletedeps t0, intrinsiclink t1
      //      WHERE t1.childidhash=t0.childidhash AND t1.jobid=? AND t1.linktype=t0.linktype AND t1.parentid=t0.parentid AND t1.childid=t0.childid AND t1.isnew='B'
      //      AND ((t0.jobid=? AND t0.childidhash=? AND t0.childid=?)
      //       OR (t0.jobid=? AND t0.childidhash=? AND t0.childid=?)
      //       ...
      //       OR (t0.jobid=? AND t0.childidhash=? AND t0.childid=?))

      int maxClause = maxClauseMarkForDelete(jobID);
      ArrayList list = new ArrayList();
      int i = 0;
      int k = 0;
      while (i < sourceDocumentHashes.length)
      {
        if (k == maxClause)
        {
          markForDelete(jobID,list,commonNewExpression,commonNewList);
          list.clear();
          k = 0;
        }
        list.add(sourceDocumentHashes[i]);
        i++;
        k++;
      }
      if (k > 0)
        markForDelete(jobID,list,commonNewExpression,commonNewList);
      noteModifications(0,sourceDocumentHashes.length,0);

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Done setting hopcount rows for job "+jobID+" to initial distances");

      // Remove the intrinsic links that we said we would - BEFORE we evaluate the queue.
      intrinsicLinkManager.removeLinks(jobID,
        commonNewExpression,commonNewList,
        sourceDocumentHashes);

      // Remove the delete dependencies of the nodes marked as being queued, with distance infinity.
      ArrayList queryList = new ArrayList();
      String query = buildConjunctionClause(queryList,new ClauseDescription[]{
        new UnitaryClause(jobIDField,jobID),
        new UnitaryClause(markForDeathField,markToString(MARK_DELETING))});
      deleteDepsManager.removeMarkedRows(getTableName(),idField,query,queryList);

      // Set the hopcount rows back to just "queued".
      HashMap newMap = new HashMap();
      newMap.put(markForDeathField,markToString(MARK_QUEUED));
      performUpdate(newMap,"WHERE "+query,queryList,null);

      // At this point, we have a queue that contains all the hopcount entries that our dependencies told us
      // needed to change as a result of the deletions.  Evaluating the queue will clean up hopcount entries
      // and dependencies that are just going away, as well as updating those that are still around but
      // will have new hopcount values.

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Done queueing for deletion for "+jobID);

    }

  }
  
  protected int maxClauseMarkForDelete(Long jobID)
  {
    return findConjunctionClauseMax(new ClauseDescription[]{
      new UnitaryClause("t0."+deleteDepsManager.jobIDField,jobID)});
  }

  protected void markForDelete(Long jobID, ArrayList list, String commonNewExpression, ArrayList commonNewList)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder("WHERE ");
    ArrayList thisList = new ArrayList();

    sb.append(idField).append(" IN(SELECT ").append(deleteDepsManager.ownerIDField).append(" FROM ").append(deleteDepsManager.getTableName()).append(" t0 WHERE ")
      .append(buildConjunctionClause(thisList,new ClauseDescription[]{
        new UnitaryClause("t0."+deleteDepsManager.jobIDField,jobID),
        new MultiClause("t0."+deleteDepsManager.childIDHashField,list)})).append(" AND ");
        
    sb.append("EXISTS(SELECT 'x' FROM ").append(intrinsicLinkManager.getTableName()).append(" t1 WHERE ")
      .append(buildConjunctionClause(thisList,new ClauseDescription[]{
        new JoinClause("t1."+intrinsicLinkManager.jobIDField,"t0."+deleteDepsManager.jobIDField),
        new JoinClause("t1."+intrinsicLinkManager.linkTypeField,"t0."+deleteDepsManager.linkTypeField),
        new JoinClause("t1."+intrinsicLinkManager.parentIDHashField,"t0."+deleteDepsManager.parentIDHashField),
        new JoinClause("t1."+intrinsicLinkManager.childIDHashField,"t0."+deleteDepsManager.childIDHashField)}));
        
    if (commonNewExpression != null)
    {
      sb.append(" AND t1.").append(commonNewExpression);
      thisList.addAll(commonNewList);
    }
    sb.append("))");

    HashMap map = new HashMap();
    // These are whacked back to "infinity" to avoid infinite looping in a cut-off graph.
    map.put(distanceField,new Long(-1L));
    map.put(markForDeathField,markToString(MARK_DELETING));
    performUpdate(map,sb.toString(),thisList,null);
  }

  /** Get document's children.
  *@return rows that contain the children.  Column names are 'linktype','childidentifier'.
  */
  protected IResultSet getDocumentChildren(Long jobID, String documentIDHash)
    throws ManifoldCFException
  {
    return intrinsicLinkManager.getDocumentChildren(jobID,documentIDHash);
  }

  /** Find the cached distance from a set of identifiers to the root.
  * This is tricky, because if there is a queue assessment going on, some values are not valid.
  * In general, one would treat a missing record as meaning "infinity".  But if the missing record
  * is simply invalidated at the moment, we want it to be treated as "missing".  So... we pick up
  * the record despite it potentially being marked, and we then examine the mark to figure out
  * what to do.
  *@return the corresponding list of nodes, taking into account unknown distances.
  */
  protected DocumentNode[] readCachedNodes(Long jobID, Question[] unansweredQuestions)
    throws ManifoldCFException
  {
    // We should not ever get requests that are duplications, or are not germane (e.g.
    // for the root).

    DocumentNode[] rval = new DocumentNode[unansweredQuestions.length];

    // Set the node up as being "infinity" first; we'll change it around later
    Answer a = new Answer(ANSWER_INFINITY);

    Map indexMap = new HashMap();
    int i = 0;
    while (i < unansweredQuestions.length)
    {
      indexMap.put(unansweredQuestions[i],new Integer(i));
      // If we wind up deleting a row in the hopcount table, because it's distance is infinity,
      // we need to treat that here as loading a node with ANSWER_INFINITY as the value.  Right
      // now, we load UNKNOWN in this case, which is wrong.
      //
      // The way in which this deletion occurs is that nodes get marked BEFORE the intrinsic link goes
      // away (supposedly), and then the intrinsic link(s) are removed.  Plus, all possible nodes are not
      // added in this case.  Therefore, we should expect questions pertaining to nodes that don't exist
      // to work.

      DocumentNode dn = new DocumentNode(unansweredQuestions[i]);
      rval[i] = dn;

      // Make the node "complete", since we found a legit value.
      dn.setStartingAnswer(a);
      dn.setTrialAnswer(a);
      // Leave bestPossibleAnswer alone.  It's not used after node is marked complete.
      dn.makeCompleteNoWrite();

      i++;
    }

    // Accumulate the ids of rows where I need deps too.  This is keyed by id and has the right answer object as a value.
    Map depsMap = new HashMap();

    int maxClause = maxClausePerformGetCachedDistances(jobID);
    ArrayList list = new ArrayList();
    ArrayList ltList = new ArrayList();
      
    i = 0;
    int k = 0;
    while (i < unansweredQuestions.length)
    {
      if (k == maxClause)
      {
        performGetCachedDistances(rval,indexMap,depsMap,jobID,ltList,list);
        k = 0;
        list.clear();
        ltList.clear();
      }
      Question q = unansweredQuestions[i++];
      ltList.add(q.getLinkType());
      list.add(q.getDocumentIdentifierHash());
      k++;
    }
    if (k > 0)
      performGetCachedDistances(rval,indexMap,depsMap,jobID,ltList,list);

    // Now, find the required delete dependencies too.
    maxClause = maxClausePerformGetCachedDistanceDeps();
    list.clear();
    k = 0;
    Iterator iter = depsMap.keySet().iterator();
    while (iter.hasNext())
    {
      Long id = (Long)iter.next();
      if (k == maxClause)
      {
        performGetCachedDistanceDeps(depsMap,list);
        k = 0;
        list.clear();
      }
      list.add(id);
      k++;
    }
    if (k > 0)
      performGetCachedDistanceDeps(depsMap,list);

    return rval;
  }

  protected int maxClausePerformGetCachedDistanceDeps()
  {
    return findConjunctionClauseMax(new ClauseDescription[]{});
  }
  
  /** Do a limited fetch of cached distance dependencies */
  protected void performGetCachedDistanceDeps(Map depsMap, ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    String query = buildConjunctionClause(newList,new ClauseDescription[]{
      new MultiClause(deleteDepsManager.ownerIDField,list)});
      
    IResultSet set = performQuery("SELECT "+deleteDepsManager.ownerIDField+","+
      deleteDepsManager.linkTypeField+","+
      deleteDepsManager.parentIDHashField+","+
      deleteDepsManager.childIDHashField+" FROM "+deleteDepsManager.getTableName()+
      " WHERE "+query,newList,null,null);

    // Each dependency needs to be filed by owner id, so let's populate a hash.  The
    // hash will be keyed by owner id and contain an arraylist of deletedependency
    // objects.

    HashMap ownerHash = new HashMap();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      Long ownerID = (Long)row.getValue(deleteDepsManager.ownerIDField);
      String linkType = (String)row.getValue(deleteDepsManager.linkTypeField);
      if (linkType == null)
        linkType = "";
      String parentIDHash = (String)row.getValue(deleteDepsManager.parentIDHashField);
      String childIDHash = (String)row.getValue(deleteDepsManager.childIDHashField);
      if (childIDHash == null)
        childIDHash = "";
      DeleteDependency dd = new DeleteDependency(linkType,parentIDHash,childIDHash);
      ArrayList ddlist = (ArrayList)ownerHash.get(ownerID);
      if (ddlist == null)
      {
        ddlist = new ArrayList();
        ownerHash.put(ownerID,ddlist);
      }
      ddlist.add(dd);
    }

    // Now, for each owner, populate the dependencies in the answer
    Iterator iter = ownerHash.keySet().iterator();
    while (iter.hasNext())
    {
      Long owner = (Long)iter.next();
      ArrayList ddlist = (ArrayList)ownerHash.get(owner);
      if (ddlist != null)
      {
        DocumentNode dn = (DocumentNode)depsMap.get(owner);
        DeleteDependency[] array = new DeleteDependency[ddlist.size()];
        int j = 0;
        while (j < array.length)
        {
          array[j] = (DeleteDependency)ddlist.get(j);
          j++;
        }
        // In the DocumentNode's created earlier, the starting answer and trial answer refer
        // to the same answer object, so fooling
        // with it will set both values, just as we want.
        Answer a = dn.getStartingAnswer();
        dn.setStartingAnswer(new Answer(a.getAnswer(),array));
        a = dn.getTrialAnswer();
        dn.setTrialAnswer(new Answer(a.getAnswer(),array));
      }
    }
  }

  /** Calculate the max clauses.
  */
  protected int maxClausePerformGetCachedDistances(Long jobID)
  {
    // Always OR clauses, so it's maxORClause.
    return getMaxOrClause();
  }
  
  /** Do a limited fetch of cached distances */
  protected void performGetCachedDistances(DocumentNode[] rval, Map indexMap, Map depsMap, Long jobID, ArrayList ltList, ArrayList list)
    throws ManifoldCFException
  {
    ArrayList newList = new ArrayList();
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0 ; i < list.size() ; i++)
    {
      if (i > 0)
        sb.append(" OR ");
      sb.append(buildConjunctionClause(newList,new ClauseDescription[]{
        new UnitaryClause(jobIDField,jobID),
        new UnitaryClause(parentIDHashField,list.get(i)),
        new UnitaryClause(linkTypeField,ltList.get(i))}));
    }
    
    String query = sb.toString();

    IResultSet set = performQuery("SELECT "+idField+","+parentIDHashField+","+linkTypeField+","+distanceField+","+markForDeathField+
      " FROM "+getTableName()+" WHERE "+query,newList,null,null);

    // Go through results and create answers
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String parentIDHash = (String)row.getValue(parentIDHashField);
      String linkType = (String)row.getValue(linkTypeField);
      Question q = new Question(parentIDHash,linkType);

      Long id = (Long)row.getValue(idField);
      Long distance = (Long)row.getValue(distanceField);
      int answerDistance;
      if (distance.longValue() == -1L)
        answerDistance = ANSWER_INFINITY;
      else
        answerDistance = (int)distance.longValue();

      DocumentNode dn = rval[((Integer)indexMap.get(q)).intValue()];

      // If the record is marked, don't use it's value; we'll look at it again on write.
      // Get the mark.
      int foundMark = stringToMark((String)row.getValue(markForDeathField));
      if (foundMark != MARK_NORMAL)
      {
        if (foundMark == MARK_QUEUED)
        {
          // The record has been disabled because it's on the queue.
          // We treat this as 'unknown value'.
          if (Logging.hopcount.isDebugEnabled())
            Logging.hopcount.debug("For '"+parentIDHash+"' linktype '"+linkType+"', the record is marked: returned 'unknown'");
          // Reset the node to be "unknown" and "incomplete"
          dn.reset();
          // Leave the document node as-is (unknown), except set the source information.
          dn.setSource(id,answerDistance);
          continue;
        }
        else
        {
          Logging.hopcount.error("Document '"+parentIDHash+"' linktype '"+linkType+"' is labeled with 'DELETING'!");
          throw new ManifoldCFException("Algorithm transaction error!");
        }
      }


      // Initially the returned answer has no dependencies.  We'll add the dependencies later.
      if (answerDistance != ANSWER_INFINITY)
      {
        // Need the dependencies for anything better than infinity
        depsMap.put(id,dn);
      }

      // Make the node "complete", since we found a legit value.
      dn.setStartingAnswer(new Answer(answerDistance));
      dn.setTrialAnswer(new Answer(answerDistance));
      // Leave bestPossibleAnswer alone.  It's not used after node is marked complete.
      dn.makeCompleteNoWrite();

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("For '"+parentIDHash+"' linktype '"+linkType+"', the value returned is "+Integer.toString(dn.getFinalAnswer()));
    }
  }

  /** Write a distance into the cache.
  */
  protected void writeCachedDistance(Long jobID, String[] legalLinkTypes, DocumentNode dn, int hopcountMethod)
    throws ManifoldCFException
  {
    Question q = dn.getQuestion();
    String linkType = q.getLinkType();
    String parentIDHash = q.getDocumentIdentifierHash();
    Answer answer = dn.getTrialAnswer();

    if (Logging.hopcount.isDebugEnabled())
      Logging.hopcount.debug("Deciding whether to cache answer for document '"+parentIDHash+"' linktype '"+linkType+"' answer="+Integer.toString(answer.getAnswer()));

    int answerValue = answer.getAnswer();
    if (answerValue < 0 && answerValue != ANSWER_INFINITY)
      return;

    // Write cached distance and dependencies, all together.
    // Yeah, this is expected to take place in a larger transaction, but I've bracketed necessary atomicity here
    // also in case later we want to call this in another way.


    HashMap map = new HashMap();
    Iterator iter;

    // Find the existing record
    int existingDistance = dn.getDatabaseValue();
    Long existingID = dn.getDatabaseRow();

    if (existingID != null)
    {
      // If we find a cached distance here, it will be marked with the same value as is passed in.
      // The algorithm makes us compare values in that case.  If the new value is LESS than the current
      // value, we must throw all the target documents of this node onto the queue.

      // If the new answer is "infinity", delete the old record too.
      if (answerValue == ANSWER_INFINITY)
      {
        if (Logging.hopcount.isDebugEnabled())
          Logging.hopcount.debug("Caching infinity for document '"+parentIDHash+"' linktype '"+linkType+"' answer="+Integer.toString(answer.getAnswer()));

        // Delete the old dependencies in any case.
        deleteDepsManager.deleteOwnerRows(new Long[]{existingID});

        ArrayList list = new ArrayList();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(idField,existingID)});

        performDelete("WHERE "+query,list,null);
        noteModifications(0,0,1);
        // Since infinity is not a reduction of any kind, we're done here.
        return;
      }

      // It should not be possible for an existing value to be better than the new value,
      // because the way we get rid of links should clean up all questionable existing values.
      if (existingDistance != ANSWER_INFINITY && existingDistance < answerValue)
      {
        Logging.hopcount.error("Existing distance "+Integer.toString(existingDistance)+" better than new distance "+
          Integer.toString(answerValue)+" for '"+parentIDHash+"' linktype '"+linkType+"'");
        throw new ManifoldCFException("Existing distance is better than new distance! Failure.");
      }

      // If the new distance is exactly the same as the old, we can leave everything as is.
      // If the distance has improved, then push target documents onto the queue.
      // Use the intrinsic link table for this.
      if (existingDistance == ANSWER_INFINITY || existingDistance > answerValue)
      {
        // Update existing row, and write new delete dependencies.

        if (Logging.hopcount.isDebugEnabled())
          Logging.hopcount.debug("Updating answer for document '"+parentIDHash+"' linktype '"+linkType+"' answer="+Integer.toString(answer.getAnswer()));

        // We need to make sure the delete deps agree with what we have in mind.

        // This is currently the most expensive part of propagating lots of changes, because most of the nodes
        // have numerous delete dependencies.  I therefore reorganized this code to be incremental where it makes
        // sense to be.  This could cut back on the number of required operations significantly.

        HashMap existingDepsMap = new HashMap();
        if (hopcountMethod != IJobDescription.HOPCOUNT_NEVERDELETE)
        {
          // If we knew in advance which nodes we'd be writing, we could have read the old
          // delete deps when we read the old distance value, in one largish query per some 25 nodes.
          // But we don't know in advance, so it's not clear whether we'd win or lose by such a strategy.
          //
          // In any case, I do believe that it will be rare for wholesale changes to occur to these dependencies,
          // so I've chosen to optimize by reading the old dependencies and just writing out the deltas.
          DeleteDependency[] existingDeps = deleteDepsManager.getDeleteDependencies(existingID);

          /*  This code demonstrated that once in a while Postgresql forgets to inherit the isolation level properly.  I wound up disabling nested transactions inside
            serializable transactions as a result, in DBInterfacePostgresql.

            IResultSet set = performQuery("SHOW TRANSACTION ISOLATION LEVEL",null, null,null);
            if (set.getRowCount() != 1)
              throw new ManifoldCFException("Unexpected return: no rows");
            IResultRow row = set.getRow(0);
            if (row.getColumnCount() != 1)
              throw new ManifoldCFException("Unexpected return: no columns");
            Iterator itera = row.getColumns();
            String columnName = (String)itera.next();
            if (row.getValue(columnName).toString().indexOf("serializ") == -1)
              throw new ManifoldCFException("Not in a serializable transaction! "+row.getValue(columnName).toString());
            */

          // Drop these into a hash map.
          int k = 0;
          while (k < existingDeps.length)
          {
            DeleteDependency dep = existingDeps[k++];
            existingDepsMap.put(dep,dep);
          }
        }

        map.put(distanceField,new Long((long)answerValue));
        map.put(markForDeathField,markToString(MARK_NORMAL));
        ArrayList list = new ArrayList();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(idField,existingID)});
        performUpdate(map,"WHERE "+query,list,null);
        noteModifications(0,1,0);

        if (hopcountMethod != IJobDescription.HOPCOUNT_NEVERDELETE)
        {
          // Write either dependencies, or dependency deltas
          int incrementalOpCount = 0;

          iter = existingDepsMap.keySet().iterator();
          while (iter.hasNext())
          {
            DeleteDependency dep = (DeleteDependency)iter.next();
            if (answer.hasDependency(dep) == false)
              incrementalOpCount++;
          }
          iter = answer.getDeleteDependencies();
          while (iter.hasNext())
          {
            DeleteDependency dep = (DeleteDependency)iter.next();
            if (existingDepsMap.get(dep) == null)
              incrementalOpCount++;
          }

          if (incrementalOpCount > 1 + answer.countDeleteDependencies())
          {
            deleteDepsManager.deleteOwnerRows(new Long[]{existingID});
            existingDepsMap.clear();
          }

          // Write the individual deletes...
          iter = existingDepsMap.keySet().iterator();
          while (iter.hasNext())
          {
            DeleteDependency dep = (DeleteDependency)iter.next();
            if (answer.hasDependency(dep) == false)
              deleteDepsManager.deleteDependency(existingID,dep);
          }

          // Then, inserts...
          iter = answer.getDeleteDependencies();
          while (iter.hasNext())
          {
            DeleteDependency dep = (DeleteDependency)iter.next();
            if (existingDepsMap.get(dep) == null)
              deleteDepsManager.writeDependency(existingID,jobID,dep);
          }
        }

        String[] targetDocumentIDHashes = intrinsicLinkManager.getDocumentUniqueParents(jobID,parentIDHash);

        // Push the target documents onto the queue!

        // It makes sense to drop in a maximal estimate of the hopcount when we do this queuing,
        // because that estimate may well be low enough so that the true hopcount value doesn't
        // need to be calculated for a time.  So, calculate an estimate and pass it in.
        // The estimate will by definition be larger than the final value.

        addToProcessingQueue(jobID,new String[]{linkType},targetDocumentIDHashes,new Answer[]{answer},parentIDHash,linkType,hopcountMethod);
      }
      else
      {
        // Take the row off the queue.
        map.put(markForDeathField,markToString(MARK_NORMAL));
        ArrayList list = new ArrayList();
        String query = buildConjunctionClause(list,new ClauseDescription[]{
          new UnitaryClause(idField,existingID)});
        performUpdate(map,"WHERE "+query,list,null);
        noteModifications(0,1,0);
      }

      // Done
      return;
    }


    // The logic for dealing with "infinity" is that we need to remove such records from the table,
    // in order to keep the table from growing forever.
    if (answerValue == ANSWER_INFINITY)
    {
      // There is nothing currently recorded, so just exit.

      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Caching infinity for document '"+parentIDHash+"' linktype '"+linkType+"' answer="+Integer.toString(answer.getAnswer()));

      return;
    }

    if (Logging.hopcount.isDebugEnabled())
      Logging.hopcount.debug("Caching answer for document '"+parentIDHash+"' linktype '"+linkType+"' answer="+Integer.toString(answer.getAnswer()));

    // We do NOT expect there to already be a cached entry!  If there is, we've screwed
    // up somehow, and it's a bug.
    Long id = new Long(IDFactory.make(threadContext));

    map.put(idField,id);
    map.put(jobIDField,jobID);
    if (linkType.length() > 0)
      map.put(linkTypeField,linkType);
    map.put(parentIDHashField,parentIDHash);
    map.put(distanceField,new Long(answer.getAnswer()));
    performInsert(map,null);
    noteModifications(1,0,0);

    if (hopcountMethod != IJobDescription.HOPCOUNT_NEVERDELETE)
    {
      iter = answer.getDeleteDependencies();
      while (iter.hasNext())
      {
        DeleteDependency dep = (DeleteDependency)iter.next();
        deleteDepsManager.writeDependency(id,jobID,dep);
      }
    }
  }

  /** A class describing a document identifier and a link type, to be used in looking up the appropriate node in
  * the hash.
  */
  protected static class Question
  {
    /** Document identifier. */
    protected String documentIdentifierHash;
    /** Link type. */
    protected String linkType;

    /** Constructor. */
    public Question(String documentIdentifierHash, String linkType)
    {
      this.documentIdentifierHash = documentIdentifierHash;
      this.linkType = linkType;
    }

    /** Get the document identifier.
    */
    public String getDocumentIdentifierHash()
    {
      return documentIdentifierHash;
    }

    /** Get the link type.
    */
    public String getLinkType()
    {
      return linkType;
    }

    /** The 'question' is uniquely described by linktype, document identifier,
    * and maximum hop count.  However, there is good reason to want to merge answers where possible that have
    * the same linktype and document identifier, so that's what we key on for hashing.
    */
    public boolean equals(Object o)
    {
      if (!(o instanceof Question))
        return false;
      Question dn = (Question)o;
      return dn.documentIdentifierHash.equals(documentIdentifierHash) && dn.linkType.equals(linkType);
    }

    /** Hash must agree with equals, above.
    */
    public int hashCode()
    {
      return documentIdentifierHash.hashCode() + linkType.hashCode();
    }

  }

  /** This class represents an answer - which consists both of an answer value, and also the dependencies
  * of that answer (i.e. the add dependencies and delete dependencies).
  */
  protected static class Answer
  {
    /** The answer value */
    protected int answer = ANSWER_UNKNOWN;
    /** This is the set of delete dependencies.  It is keyed by a DeleteDependency object. */
    protected HashMap deleteDependencies = new HashMap();

    /** Constructor. */
    public Answer()
    {
    }

    public Answer(Answer other)
    {
      answer = other.answer;
      // Shallow copy is fine, because the stuff in these dependencies is immutable.
      deleteDependencies = (HashMap)other.deleteDependencies.clone();
    }

    public Answer(int value)
    {
      answer = value;
    }

    /** Set an answer from initial data. */
    public Answer(int answer, DeleteDependency[] deleteDeps)
    {
      this.answer = answer;
      int i = 0;
      while (i < deleteDeps.length)
      {
        DeleteDependency dep = (DeleteDependency)deleteDeps[i++];
        deleteDependencies.put(dep,dep);
      }
    }

    /** Get the current answer value.
    */
    public int getAnswer()
    {
      return answer;
    }

    /** Get the number of delete dependencies */
    public int countDeleteDependencies()
    {
      return deleteDependencies.size();
    }

    /** Iterate over the delete dependencies. */
    public Iterator getDeleteDependencies()
    {
      return deleteDependencies.keySet().iterator();
    }

    /** Check if a delete dependency is present */
    public boolean hasDependency(DeleteDependency dep)
    {
      return deleteDependencies.get(dep) != null;
    }

    /** Initialize this answer object.  This sets the answer value to ANSWER_INFINITY
    * and clears the maps.
    */
    public void initialize(int value)
    {
      answer = value;
      deleteDependencies.clear();
    }

    /** Copy the answer value from another answer object */
    public void duplicate(Answer other)
    {
      answer = other.answer;
      // Shallow copy is fine, because the stuff in these dependencies is immutable.
      deleteDependencies = (HashMap)other.deleteDependencies.clone();
    }

    /** Update the current answer, using a child link's information and answer.
    * This method basically decides if the child is relevant, and if so merges the answer from the
    * child together with the current value stored here.
    *@param childAnswer is the current answer found for the child.
    *@param isIncrementingLink is true if this link is the kind being counted, and thus increments
    * the hopcount.
    *@param linkType is the type of THIS link (for building appropriate delete dependency).
    *@param parentIDHash is the hash of the parent document id for THIS link.
    *@param childIDHash is the hash of the child document id for THIS link.
    */
    public void merge(Answer childAnswer, boolean isIncrementingLink,
      String linkType, String parentIDHash, String childIDHash)
    {
      // For answers, we obviously pick the best answer we can.
      // For dependencies, this is the process:
      //
      // 1) Delete dependencies
      //    There can be only one delete dependency resulting from any given link.  This
      //    dependency will only be created if the link is "the best" so far.  The child
      //    node's delete dependencies will also be included whenever a new best match is
      //    found.
      //
      //

      // Now, get the child answer value.
      int childAnswerValue = childAnswer.getAnswer();

      // If the link is the same kind as the kind of answer we want, then it adds one
      // to the distance measurement to the child.
      if (answer >= 0)
      {
        // Determined distance against whatever the child says.
        if (childAnswerValue >= 0)
        {
          if (isIncrementingLink)
            childAnswerValue++;
          if (childAnswerValue < answer)
          {
            // Use the child answer value
            setAnswerFromChild(childAnswerValue,childAnswer.deleteDependencies,linkType,parentIDHash,childIDHash);
            return;
          }
        }
        // The current answer is better than either infinity or greater-than-max.
        return;
      }
      // If the current answer is infinity, use the child answer.
      if (answer == ANSWER_INFINITY)
      {
        if (childAnswerValue >= 0)
        {
          if (isIncrementingLink)
            childAnswerValue++;
          // Use the child answer value
          setAnswerFromChild(childAnswerValue,childAnswer.deleteDependencies,linkType,parentIDHash,childIDHash);
          return;
        }
        // Leave the current answer.
        return;
      }
      // For the current answer being "greater than max":
      if (childAnswerValue >= 0)
      {
        if (isIncrementingLink)
          childAnswerValue++;
        // Use the child answer value
        setAnswerFromChild(childAnswerValue,childAnswer.deleteDependencies,linkType,parentIDHash,childIDHash);
        return;
      }
      // All other cases: just keep the current answer.
    }

    /** Set answer from child */
    protected void setAnswerFromChild(int newAnswer, HashMap childDeleteDependencies, String linkType, String parentIDHash, String childIDHash)
    {
      answer = newAnswer;
      deleteDependencies = (HashMap)childDeleteDependencies.clone();
      DeleteDependency x = new DeleteDependency(linkType,parentIDHash,childIDHash);
      deleteDependencies.put(x,x);
    }

    /** Set an answer from initial data. */
    public void setAnswer(int answer, DeleteDependency[] deleteDeps)
    {
      this.answer = answer;
      deleteDependencies.clear();
      int i = 0;
      while (i < deleteDeps.length)
      {
        DeleteDependency dep = (DeleteDependency)deleteDeps[i++];
        deleteDependencies.put(dep,dep);
      }
    }
  }

  /** This class describes a document reference. */
  protected static class DocumentReference
  {
    protected String childIdentifierHash;
    protected String linkType;

    /** Constructor */
    public DocumentReference(String childIdentifierHash, String linkType)
    {
      this.childIdentifierHash = childIdentifierHash;
      this.linkType = linkType;
    }

    /** Get the child identifier */
    public String getChildIdentifierHash()
    {
      return childIdentifierHash;
    }

    /** Get the link type */
    public String getLinkType()
    {
      return linkType;
    }
  }

  /** This class describes a node link reference. */
  protected static class NodeReference
  {

    /** The node being referred to */
    protected DocumentNode theNode;
    /** The kind of link it is */
    protected String linkType;

    /** Constructor */
    public NodeReference(DocumentNode theNode, String linkType)
    {
      this.theNode = theNode;
      this.linkType = linkType;
    }

    /** Get the node */
    public DocumentNode getNode()
    {
      return theNode;
    }

    /** Get the link type */
    public String getLinkType()
    {
      return linkType;
    }

    /** Hash function. */
    public int hashCode()
    {
      return theNode.hashCode() + linkType.hashCode();
    }

    /** Is this equal? */
    public boolean equals(Object o)
    {
      if (!(o instanceof NodeReference))
        return false;
      NodeReference other = (NodeReference)o;
      // DocumentNode objects compare only with themselves.
      return theNode.equals(other.theNode) && this.linkType.equals(other.linkType);
    }
  }

  /** This class keeps track of the data associated with a node in the hash map.
  * This basically includes the following:
  * - the document identifier
  * - the 'question' that was asked, which has the form (link type, maximum distance)
  * - possibly the 'answer' to the question, which is either ">(maximum distance)", or a number.
  * - references to the nodes which care about this answer, if they are still queued.
  * - summary of the information we've gathered from children so far (if answer not known yet)
  * - references to the children of this node that can affect the answer, including link details
  *   (if answer not known yet)
  */
  protected static class DocumentNode
  {
    /** The question. */
    protected Question question;

    /** This is the original answer (if any), which is the current value in the database */
    protected int databaseAnswerValue = ANSWER_UNKNOWN;
    /** The original database row, if any */
    protected Long databaseRow = null;

    /** The answer, as calculated up to the level of all the completed children, which will
    * not include incomplete child references of this node.  This is a starting point for every reassessment
    * of this node's current answer.  It is adjust only when additional children are noted as being complete.
    */
    protected Answer startingAnswer = new Answer(ANSWER_UNKNOWN);

    /** The current best answer.  This takes into account the current status of all the child nodes.  If the
    * node is not complete, then the answer must be viewed as being less than or equal to this value.
    */
    protected Answer trialAnswer = new Answer(ANSWER_UNKNOWN);

    /** The best (lowest) possible answer value for this node.  This value is calculated based on the known
    * child link structure of a node, and can only increase.  The value will start low (at 0) and will climb
    * as more knowledge is gained, as the children's best possible answer value increases upon re-evaluation.
    * When the trial answer (above) reaches a value equal to the best possible value, then the node will be
    * immediately marked as "complete", and further processing will be considered unnecessary.
    * As far as dependencies are concerned, the bestPossibleAnswer includes dependencies that have gone
    * into its assessment.  These dependencies represent what would need to be changed to invalidate
    * the answer as it stands.  (Invalidation means that a smaller best possible answer would be possible, so
    * only add dependencies would need consideration.)
    *
    */
    protected Answer bestPossibleAnswer = new Answer(0);

    /** Answer complete flag.  Will be set to true only if the value of "trialAnswer" is deemed final. */
    protected boolean isComplete = false;
    /** This flag is meaningful only if the complete flag is set. */
    protected boolean writeNeeded = true;
    /** Parent nodes who care (i.e. are still queued).  This map contains DocumentNode objects. */
    protected Map parentsWhoCare = new HashMap();
    /** Child node references.  This is a reference to an actual document node object which has a parent reference
    * back to this one.  If the child node is modified, there is an obligation to cause the parent node to be
    * re-evaluated.  The re-evaluation process examines all child nodes and may adjust the status of the trial
    * answer, and may indeed even remove the reference to the child.
    * This map contains NodeReference objects. */
    protected Map childReferences = new HashMap();

    /** Create a document node.  This will happen only if there is no comparable one already in the hash.
    */
    public DocumentNode(Question question)
    {
      this.question = question;
    }

    /** Get the question. */
    public Question getQuestion()
    {
      return question;
    }

    /** Reset back to an "unknown" state.
    */
    public void reset()
    {
      isComplete = false;
      writeNeeded = true;
      databaseAnswerValue = ANSWER_UNKNOWN;
      databaseRow = null;
      trialAnswer.initialize(ANSWER_UNKNOWN);
      startingAnswer.initialize(ANSWER_UNKNOWN);
      bestPossibleAnswer.initialize(0);
    }

    /** Clear child references. */
    public void clearChildReferences()
    {
      childReferences.clear();
    }

    /** Check if there are children. */
    public boolean hasChildren()
    {
      return childReferences.size() > 0;
    }

    /** Get an answer that's final.
    * Returns "unknown" if the current answer is incomplete.
    */
    public int getFinalAnswer()
    {
      if (isComplete)
      {
        return trialAnswer.getAnswer();
      }
      else
        return ANSWER_UNKNOWN;
    }

    /** Check if the answer is complete.  Returns true if the answer is complete.
    */
    public boolean isAnswerComplete()
    {
      return isComplete;
    }

    /** Check if the node is complete, given the question it represents. */
    public boolean isComplete()
    {
      return isComplete;
    }

    /** Check if a write of the answer is needed to the database */
    public boolean isWriteNeeded()
    {
      return writeNeeded;
    }

    /** Check if answer is still needed.
    */
    public boolean isAnswerNeeded()
    {
      // Check to make sure there are parents that care.
      return parentsWhoCare.size() > 0;
    }

    /** Get best possible answer */
    public Answer getBestPossibleAnswer()
    {
      return bestPossibleAnswer;
    }

    /** Set best possible answer */
    public void setBestPossibleAnswer(Answer answer)
    {
      bestPossibleAnswer.duplicate(answer);
    }

    /** Get the current best answer.
    */
    public Answer getTrialAnswer()
    {
      return trialAnswer;
    }

    /** Set the answer for this node.
    */
    public void setTrialAnswer(Answer answer)
    {
      this.trialAnswer.duplicate(answer);
    }

    /** Get the starting (base) answer. */
    public Answer getStartingAnswer()
    {
      return startingAnswer;
    }

    /** Set the starting (base) answer. */
    public void setStartingAnswer(Answer answer)
    {
      startingAnswer.duplicate(answer);
    }

    /** Mark the node as being "complete", with a write needed. */
    public void makeComplete()
    {
      if (!isComplete)
      {
        isComplete = true;
        writeNeeded = true;
      }
    }

    /** Mark the answer as being "complete", and not needing a write. */
    public void makeCompleteNoWrite()
    {
      isComplete = true;
      writeNeeded = false;
    }

    /** Add a parent who should be notified if this node's answer changes.
    * The parent is responsible for figuring out when this reference should be removed.
    */
    public void addParent(DocumentNode parent)
    {
      parentsWhoCare.put(parent,parent);
    }

    /** Clear the 'write needed' flag, to prevent another write. */
    public void clearWriteNeeded()
    {
      writeNeeded = false;
    }

    /** Add a child reference.
    *@param childRef is the child node reference to add.
    */
    public void addChild(NodeReference childRef)
    {
      childReferences.put(childRef,childRef);
    }

    /** Remove a child reference.
    *@param childRef is the child node reference to remove.
    */
    public void removeChild(NodeReference childRef)
    {
      childReferences.remove(childRef);
    }

    /** Remove a parent.  This method will get called when the parent's answer no longer can be affected by
    * this child's answer (probably because the child's answer has become complete).
    */
    public void removeParent(DocumentNode parent)
    {
      parentsWhoCare.remove(parent);
    }

    /** Iterate through all current parents.  This is an iterator over DocumentNode objects. */
    public Iterator getCurrentParents()
    {
      return parentsWhoCare.keySet().iterator();
    }

    /** Iterate through current children.  This is an iterator over NodeReference objects. */
    public Iterator getCurrentChildren()
    {
      return childReferences.keySet().iterator();
    }

    /** Set the database row and answer value */
    public void setSource(Long rowID, int answerValue)
    {
      this.databaseRow = rowID;
      this.databaseAnswerValue = answerValue;
    }

    /** Get the database row */
    public Long getDatabaseRow()
    {
      return databaseRow;
    }

    /** Get the database answer value */
    public int getDatabaseValue()
    {
      return databaseAnswerValue;
    }

    // Do NOT override hashCode() and equals(), since we want a node to match only itself.
  }

  /** A queue object allows document nodes to be ordered appropriately for the most efficient execution.
  * The queue handles DocumentNode objects exclusively.  Mapping of Question to DocumentNode object
  * involves structures outside of all queues.
  */
  protected static class NodeQueue
  {
    protected HashMap nodeMap = new HashMap();

    /** Constructor.
    */
    public NodeQueue()
    {
    }

    /** Queue a document node.
    */
    public void addToQueue(DocumentNode node)
    {
      if (nodeMap.get(node.getQuestion()) == null)
      {
        if (Logging.hopcount.isDebugEnabled())
          Logging.hopcount.debug("Adding document node "+node.toString()+" to queue "+toString());

        nodeMap.put(node.getQuestion(),node);
      }
    }

    /** Remove a node from the queue.  This might happen if the node no longer needs evaluation.
    */
    public void removeFromQueue(DocumentNode node)
    {
      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Removing document node "+node.toString()+" from queue "+toString());

      nodeMap.remove(node.getQuestion());
    }

    /** Fetch the next object off the queue for processing.  Returns null if there are no more objects.
    */
    public DocumentNode nextNode()
    {
      if (nodeMap.size() == 0)
      {
        if (Logging.hopcount.isDebugEnabled())
          Logging.hopcount.debug("Retrieving node from queue "+toString()+": none found!");

        return null;
      }

      Question q = (Question)nodeMap.keySet().iterator().next();
      DocumentNode dn = (DocumentNode)nodeMap.remove(q);
      if (Logging.hopcount.isDebugEnabled())
        Logging.hopcount.debug("Retrieving node "+dn.toString()+" from queue "+toString());
      return dn;
    }

    /** Fetch ALL of the nodes off the queue in one step.
    */
    public DocumentNode[] nextNodes()
    {
      DocumentNode[] rval = new DocumentNode[nodeMap.size()];
      Iterator iter = nodeMap.keySet().iterator();
      int j = 0;
      while (iter.hasNext())
      {
        Question q = (Question)iter.next();
        rval[j++] = (DocumentNode)nodeMap.get(q);
      }
      nodeMap.clear();
      return rval;
    }


  }

  /** The Document Hash structure contains the document nodes we are interested in, including those we need answers
  * for to proceed.  The main interface involves specifying a set of questions and receiving the answers.  This
  * structure permits multiple requests to be made to each object, and in-memory caching is used to reduce the amount of database
  * activity as much as possible.
  * It is also presumed that these requests take place inside of the appropriate transactions, since both read and write
  * database activity may well occur.
  */
  protected class DocumentHash
  {
    /** The job identifier */
    protected Long jobID;


    /** This is the map of known questions to DocumentNode objects. */
    protected Map questionLookupMap = new HashMap();

    /** This is the queue for nodes that need to be initialized, who need child fetching. */
    protected NodeQueue childFetchQueue = new NodeQueue();

    /** This is the queue for evaluating nodes.  For all of these nodes, the processing
    * has begun: all child nodes have been queued, and at least a partial answer is present.  Evaluating one
    * of these nodes involves potentially updating the node's answer, and when that is done, all listed parents
    * will be requeued on this queue.
    */
    protected NodeQueue evaluationQueue = new NodeQueue();

    /** These are the legal link types for the job */
    protected String[] legalLinkTypes;

    /** The hopcount method */
    protected int hopcountMethod;

    /** Constructor */
    public DocumentHash(Long jobID, String[] legalLinkTypes, int hopcountMethod)
    {
      this.jobID = jobID;
      this.legalLinkTypes = legalLinkTypes;
      this.hopcountMethod = hopcountMethod;
    }

    /** Throw in some questions, and prepare for the answers. */
    public int[] askQuestions(Question[] questions)
      throws ManifoldCFException
    {
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Questions asked as follows:");
        int i = 0;
        while (i < questions.length)
        {
          Logging.hopcount.debug("  Linktype='"+questions[i].getLinkType()+"' DocumentID='"+questions[i].getDocumentIdentifierHash()+"'");
          i++;
        }
        Logging.hopcount.debug("");
      }

      // The algorithm is complex, and works as follows.  There are two queues - a queue for
      // starting off a node's evaluation (called the child fetch queue), and a second queue for
      // re-evaluating nodes (called the evaluation queue).
      //
      // Whenever a node is first examined, and no answer is available, the node is placed on the
      // child fetch queue.  The activity associated with this queue is to fetch a node's children
      // and queue them in turn (if needed).  But in any case, the node is initialized with the
      // best available answer.
      //
      // If the answer is complete, the node is not placed in any queues.
      // Parent nodes do not need to be notified, because
      // they must already be in the evaluation queue, and will be processed in time.
      //
      // If the answer was incomplete, the node will be placed into the evaluation queue.  Nodes in this
      // queue are there because some of their children have changed state in a meaningful way since the
      // last time a tentative answer was calculated.  The processing of nodes from this queue involves
      // updating the answer value, deciding whether it is complete or not, and, if so, writing the answer
      // to the database.  Nodes that are not complete but have not been modified are not placed in a
      // queue; they are simply left unqueued.  When all processing is complete, these nodes will be
      // checked and converted to "completed" states.

      int[] answers = new int[questions.length];
      DocumentNode[] nodes = queueQuestions(questions);

      // Throw these questions into the opennodes structure, unless the answer is already known.
      int i = 0;
      while (i < nodes.length)
      {
        // Flag these questions as having a special parent, so they can't be removed.
        nodes[i++].addParent(null);
      }

      // Now, process until we have all the answers we wanted.
      while (true)
      {
        if (Thread.currentThread().isInterrupted())
          throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);

        // Early decision!
        // For each question, see if there's a completed answer yet
        i = 0;
        while (i < questions.length)
        {
          DocumentNode dn = nodes[i];
          int answer = dn.getFinalAnswer();
          if (answer == ANSWER_UNKNOWN)
            break;
          // Found one!  Record it, just in case we finish.
          answers[i++] = answer;
        }

        if (i != questions.length)
        {

          // Evaluation queue has priority.  If there's anything waiting on it, process it.
          DocumentNode evaluationNode = evaluationQueue.nextNode();
          if (evaluationNode != null)
          {
            // Evaluate!
            evaluateNode(evaluationNode);
            continue;
          }

          Logging.hopcount.debug("Found no nodes to evaluate at the moment");

          // Nothing left to evaluate.  Do the child fetch bit instead.
          DocumentNode[] fetchNodes = childFetchQueue.nextNodes();
          if (fetchNodes.length > 0)
          {
            // Fetch children and initialize the node
            getNodeChildren(fetchNodes);
            continue;
          }

          Logging.hopcount.debug("Found no children to fetch at the moment");

          // Nothing left to do at all.
          // Scan the map and convert all non-complete answers to complete ones.  They'll
          // be left in an incomplete state if there were loops.

          Iterator iter = questionLookupMap.values().iterator();
          while (iter.hasNext())
          {
            DocumentNode dn = (DocumentNode)iter.next();
            if (!dn.isComplete())
            {
              makeNodeComplete(dn);
            }
          }

          Logging.hopcount.debug("Made remaining nodes complete");

          // Copy out the answer.  All nodes are guaranteed to be complete now.
          i = 0;
          while (i < questions.length)
          {
            DocumentNode dn = nodes[i];
            answers[i++] = dn.getFinalAnswer();
          }

          Logging.hopcount.debug("Done (copied out the answers)");
        }
        else
          Logging.hopcount.debug("Done (because answers already available)");

        if (Logging.hopcount.isDebugEnabled())
        {
          Logging.hopcount.debug("Answers returned as follows:");
          i = 0;
          while (i < questions.length)
          {
            Logging.hopcount.debug("  Linktype='"+questions[i].getLinkType()+"' DocumentID='"+questions[i].getDocumentIdentifierHash()+"'"+
              " Answer="+Integer.toString(answers[i]));
            i++;
          }
          Logging.hopcount.debug("");
        }

        return answers;
      }
    }

    /** Evaluate a node from the evaluation queue.
    */
    protected void evaluateNode(DocumentNode node)
      throws ManifoldCFException
    {
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Evaluating node; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
          node.getQuestion().getLinkType()+"'"+
          " BaseAnswer="+Integer.toString(node.getStartingAnswer().getAnswer())+
          " TrialAnswer="+Integer.toString(node.getTrialAnswer().getAnswer()));
      }

      // The base (or starting) answer should already have been set for this node.
      // What we do here is go through all the remaining listed children,
      // and evaluate a new trial answer.  There are some special cases we want to
      // catch:
      //
      // 1) If an answer goes to zero, then the node is automatically marked "complete".
      //    All child references are removed.
      // 2) Child references should only be kept around if there's a chance they would
      //    REDUCE the current answer.  So, we should keep children that are incomplete.
      //    Complete children should be factored into the base answer, and discarded.
      // 3) If the node is still marked incomplete, AND if there are no parents, then
      //    simply delete it.
      // 4) If the node is now complete, it should be marked as such, and the distance
      //    from the node to the root should be written into the database.  Parent
      //    should be requeued also.
      // 5) If the node is incomplete, and the trial answer has changed, then update the
      //    trial answer and requeue all parents.

      boolean signalParentsNeeded = false;

      Answer baseAnswer = new Answer(node.getStartingAnswer());
      // THe baseAnswer already includes the current node in its add deps, so I don't have to add it here.

      // Make a pass through the children, looking for completed nodes.
      // Keep track of the ones we find, so we can remove them from the child list.

      ArrayList childRemovalList = new ArrayList();
      Iterator iter = node.getCurrentChildren();
      while (iter.hasNext())
      {
        NodeReference childRef = (NodeReference)iter.next();
        DocumentNode child = childRef.getNode();
        String linkType = childRef.getLinkType();
        if (child.isComplete())
        {
          childRemovalList.add(childRef);
          baseAnswer.merge(child.getTrialAnswer(),
            linkType.equals(node.getQuestion().getLinkType()),
            linkType,
            node.getQuestion().getDocumentIdentifierHash(),
            child.getQuestion().getDocumentIdentifierHash());
        }
      }

      // Get rid of the marked children.
      int i = 0;
      while (i < childRemovalList.size())
      {
        NodeReference childRef = (NodeReference)childRemovalList.get(i++);
        childRef.getNode().removeParent(node);
        node.removeChild(childRef);
      }

      // Set new starting answer, if it has changed.  This will NOT cause a requeue of parents,
      // all by itself.
      node.setStartingAnswer(baseAnswer);
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Setting baseAnswer; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
          node.getQuestion().getLinkType()+"' baseAnswer="+Integer.toString(baseAnswer.getAnswer()));
      }

      // Now, go through remaining nodes and build a trial answer.
      Answer trialAnswer = new Answer(baseAnswer);
      iter = node.getCurrentChildren();
      while (iter.hasNext())
      {
        NodeReference childRef = (NodeReference)iter.next();
        DocumentNode child = childRef.getNode();
        String linkType = childRef.getLinkType();
        trialAnswer.merge(child.getTrialAnswer(),
          linkType.equals(node.getQuestion().getLinkType()),
          linkType,
          node.getQuestion().getDocumentIdentifierHash(),
          child.getQuestion().getDocumentIdentifierHash());
      }

      // Get the current trial answer, so we can compare
      Answer currentTrialAnswer = node.getTrialAnswer();
      if (trialAnswer.getAnswer() != currentTrialAnswer.getAnswer())
      {
        signalParentsNeeded = true;
      }

      // See if we mark this "complete".
      if (trialAnswer.getAnswer() == node.getBestPossibleAnswer().getAnswer())
      {
        // Early exit.
        if (Logging.hopcount.isDebugEnabled())
        {
          Logging.hopcount.debug("Setting complete [bestpossible]; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
            node.getQuestion().getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
        }
        node.setTrialAnswer(trialAnswer);
        makeNodeComplete(node);
        signalParentsNeeded = true;
      }
      else if (!node.hasChildren())
      {
        if (Logging.hopcount.isDebugEnabled())
        {
          Logging.hopcount.debug("Setting complete [nochildren]; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
            node.getQuestion().getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
        }
        // Simply have no more children that aren't complete, so we are done.
        node.setTrialAnswer(trialAnswer);
        // It's complete!
        makeNodeComplete(node);
        signalParentsNeeded = true;
      }
      else
      {
        // Update the answer.
        if (Logging.hopcount.isDebugEnabled())
        {
          Logging.hopcount.debug("Setting trialAnswer; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
            node.getQuestion().getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
        }

        node.setTrialAnswer(trialAnswer);

        // Still not complete.  If it has no parents, it's not needed anymore, so chuck it.
        if (!node.isAnswerNeeded())
        {
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Discarding [unneeded]; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
              node.getQuestion().getLinkType()+"'");
          }

          // Take this node out of the main map.
          questionLookupMap.remove(node.getQuestion());
          // Remove all the child references
          removeChildLinks(node);
          Logging.hopcount.debug("Done node evaluation");
          return;
        }
      }

      if (signalParentsNeeded)
      {
        Logging.hopcount.debug("Requeueing parent nodes");
        // Requeue the parents.
        queueParents(node);
      }

      Logging.hopcount.debug("Done node evaluation");
    }

    /** Fetch a the children of a bunch of nodes, and initialize all of the nodes appropriately.
    */
    protected void getNodeChildren(DocumentNode[] nodes)
      throws ManifoldCFException
    {
      if (Logging.hopcount.isDebugEnabled())
      {
        Logging.hopcount.debug("Finding children for the following nodes:");
        int z = 0;
        while (z < nodes.length)
        {
          DocumentNode node = nodes[z++];
          Logging.hopcount.debug("  DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
            node.getQuestion().getLinkType()+"'");
        }
      }

      // Need to figure out which nodes need processing, and which don't.
      // All of the current nodes are (by definition) not in any queues.  We need to keep track of
      // which queues these nodes have to go into.
      // - Some will just be deleted
      // - Some will be made complete, and not put into any queue
      //
      // Naively, we might presume that some will be queued (on the evaluation queue) as a result of being the
      // parent of a node that was changed.  But, in fact, being on the "child fetch" queue means that we
      // DON'T have any loaded child references yet.  So - that can't happen, at least not until the child references
      // are loaded and the nodes initialized.
      //
      // The real question therefore is how, exactly, to handle the situation where we load children for a bunch of
      // nodes, and initialize the nodes, and then need to put their parents on the evaluation queue.  When we did
      // only a single node at a time, the parents became queued but no further evaluation took place here.
      // Since one of the nodes being processed may in fact refer to another node being processed, the
      // 'full' initialization cannot easily be handled here; the nodes must be simply initialized to a basic incomplete
      // state, and put on the evaluation queue, for complete evaluation.

      // This is a map where I'll put the nodes that I still need children for, so I can get all children at once.
      HashMap nodesNeedingChildren = new HashMap();

      // From the nodes needing children, come up with a unique set of parent identifiers, so
      // we can get the children as efficiently as possible.
      HashMap parentMap = new HashMap();

      int k = 0;
      while (k < nodes.length)
      {
        DocumentNode node = nodes[k++];

        if (!node.isAnswerNeeded())
        {
          // If there are no parents for this node, then this node is not currently needed, so just ditch it.
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Discard before getting node children[unneeded]; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
              node.getQuestion().getLinkType()+"'");
          }
          questionLookupMap.remove(node.getQuestion());
        }
        else if (node.getQuestion().getDocumentIdentifierHash().length() == 0)
        {
          // If this is the root, set all node values accordingly.
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Found root; DocID='"+node.getQuestion().getDocumentIdentifierHash()+"' Linktype='"+
              node.getQuestion().getLinkType()+"'");
          }

          node.setStartingAnswer(new Answer(0));
          node.setTrialAnswer(new Answer(0));
          node.makeCompleteNoWrite();
          queueParents(node);
        }
        nodesNeedingChildren.put(node.getQuestion(),node);
        parentMap.put(node.getQuestion().getDocumentIdentifierHash(),node.getQuestion().getDocumentIdentifierHash());
      }

      // Now, we want all the children of all the nodes that are left - if any
      if (nodesNeedingChildren.size() == 0)
        return;

      // This map will get built as a map keyed by parent document identifier and containing as
      // a value an arraylist of DocumentReference objects.

      HashMap referenceMap = new HashMap();

      int maxClause = maxClauseFindChildren(jobID);
      ArrayList list = new ArrayList();
      k = 0;
      Iterator iter = parentMap.keySet().iterator();
      while (iter.hasNext())
      {
        String parentIDHash = (String)iter.next();
        referenceMap.put(parentIDHash,new ArrayList());
        if (k == maxClause)
        {
          findChildren(referenceMap,jobID,list);
          k = 0;
          list.clear();
        }
        list.add(parentIDHash);
        k++;
      }

      if (k > 0)
        findChildren(referenceMap,jobID,list);

      // Go through the 'nodes needing children'.  For each node, look up the child references, and create a set
      // of questions for all the node children.  We'll refer directly to this list when putting together the
      // nodes in the last step.

      HashMap childQuestionMap = new HashMap();

      iter = nodesNeedingChildren.keySet().iterator();
      while (iter.hasNext())
      {
        Question q = (Question)iter.next();
        ArrayList childlist = (ArrayList)referenceMap.get(q.getDocumentIdentifierHash());
        k = 0;
        while (k < childlist.size())
        {
          DocumentReference dr = (DocumentReference)childlist.get(k++);
          Question childQuestion = new Question(dr.getChildIdentifierHash(),q.getLinkType());
          childQuestionMap.put(childQuestion,childQuestion);
        }
      }

      // Put together a child question array
      Question[] questionsToAsk = new Question[childQuestionMap.size()];
      k = 0;
      iter = childQuestionMap.keySet().iterator();
      while (iter.hasNext())
      {
        questionsToAsk[k++] = (Question)iter.next();
      }

      // Ask the questions in batch (getting back nodes that we can then refer to)
      DocumentNode[] resultNodes = queueQuestions(questionsToAsk);

      // Put the resulting nodes into the map for ease of lookup.
      k = 0;
      while (k < resultNodes.length)
      {
        childQuestionMap.put(questionsToAsk[k],resultNodes[k]);
        k++;
      }


      // Now, go through all the nodes that need processing one-by-one, and use the childQuestionMap to find
      // the nodes we need, and the referenceMap to find the link details.
      iter = nodesNeedingChildren.keySet().iterator();
      while (iter.hasNext())
      {
        Question q = (Question)iter.next();
        DocumentNode node = (DocumentNode)nodesNeedingChildren.get(q);
        String documentIdentifierHash = q.getDocumentIdentifierHash();

        Answer startingAnswer = new Answer(ANSWER_INFINITY);
        Answer trialAnswer = new Answer(ANSWER_INFINITY);
        int bestPossibleAnswerValue = ANSWER_INFINITY;

        ArrayList childReferences = (ArrayList)referenceMap.get(q.getDocumentIdentifierHash());

        // Each childReference is a DocumentReference object which will allow the lookup of
        // the child node from the childQuestionMap.
        k = 0;
        while (k < childReferences.size())
        {
          DocumentReference dr = (DocumentReference)childReferences.get(k++);
          String childIdentifierHash = dr.getChildIdentifierHash();
          Question lookupQuestion = new Question(childIdentifierHash,q.getLinkType());
          DocumentNode childNode = (DocumentNode)childQuestionMap.get(lookupQuestion);
          String linkType = dr.getLinkType();
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("  Child found for DocID='"+documentIdentifierHash+"' Linktype='"+
              q.getLinkType()+"'; ID='"+childIdentifierHash+"' linktype='"+linkType+"'");
          }

          boolean isIncrementing = linkType.equals(node.getQuestion().getLinkType());
          int bestPossibleCheckValue = 0;
          if (isIncrementing)
          {
            bestPossibleCheckValue = 1;
          }

          if (bestPossibleAnswerValue == ANSWER_INFINITY || bestPossibleAnswerValue > bestPossibleCheckValue)
            bestPossibleAnswerValue = bestPossibleCheckValue;

          // Decide how to tally this - into starting answer (and don't record), or
          // record it and scan it later?

          // If the node is complete, incorporate it into BOTH the starting answer and the
          // trial answer.  If incomplete, leave a parent reference around.
          Answer childAnswer = childNode.getTrialAnswer();
          if (childNode.isComplete())
          {
            startingAnswer.merge(childAnswer,isIncrementing,
              linkType,documentIdentifierHash,childIdentifierHash);
            trialAnswer.merge(childAnswer,isIncrementing,
              linkType,documentIdentifierHash,childIdentifierHash);
          }
          else
          {
            // Add it as a child, and only include these results in the trial answer.
            childNode.addParent(node);
            node.addChild(new NodeReference(childNode,linkType));
            trialAnswer.merge(childAnswer,isIncrementing,
              linkType,documentIdentifierHash,childIdentifierHash);
          }
        }

        node.setStartingAnswer(startingAnswer);
        if (Logging.hopcount.isDebugEnabled())
        {
          Logging.hopcount.debug("Setting baseAnswer; DocID='"+documentIdentifierHash+"' Linktype='"+
            q.getLinkType()+"' baseAnswer="+Integer.toString(startingAnswer.getAnswer()));
        }

        // Set up best possible answer
        Answer bestPossible = new Answer(bestPossibleAnswerValue);
        node.setBestPossibleAnswer(bestPossible);

        // If the node has managed to complete itself, just throw it onto the "completed" stack
        // See if we mark this "complete".
        if (trialAnswer.getAnswer() == bestPossible.getAnswer())
        {
          // It's complete, but we need to update the trial answer's add deps
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Setting complete [bestpossible]; DocID='"+documentIdentifierHash+"' Linktype='"+
              q.getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
          }
          node.setTrialAnswer(trialAnswer);
          makeNodeComplete(node);
        }
        else if (!node.hasChildren())
        {
          // It's complete!
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Setting complete [nochildren]; DocID='"+documentIdentifierHash+"' Linktype='"+
              q.getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
          }
          node.setTrialAnswer(trialAnswer);
          makeNodeComplete(node);
        }
        else
        {
          if (Logging.hopcount.isDebugEnabled())
          {
            Logging.hopcount.debug("Setting trialAnswer; DocID='"+documentIdentifierHash+"' Linktype='"+
              q.getLinkType()+"' trialAnswer="+Integer.toString(trialAnswer.getAnswer()));
          }

          node.setTrialAnswer(trialAnswer);
        }

        // Notify parents.
        queueParents(node);
      }
    }

    /** Get the max clauses.
    */
    protected int maxClauseFindChildren(Long jobID)
    {
      return findConjunctionClauseMax(new ClauseDescription[]{
        new UnitaryClause(intrinsicLinkManager.jobIDField,jobID)});
    }
    
    /** Get the children of a bunch of nodes.
    */
    protected void findChildren(Map referenceMap, Long jobID, ArrayList list)
      throws ManifoldCFException
    {
      ArrayList newList = new ArrayList();

      String query = buildConjunctionClause(newList,new ClauseDescription[]{
        new UnitaryClause(intrinsicLinkManager.jobIDField,jobID),
        new MultiClause(intrinsicLinkManager.parentIDHashField,list)});
        
      // Grab the appropriate rows from the intrinsic link table.
      IResultSet set = performQuery("SELECT "+intrinsicLinkManager.childIDHashField+","+intrinsicLinkManager.linkTypeField+","+
        intrinsicLinkManager.parentIDHashField+" FROM "+intrinsicLinkManager.getTableName()+" WHERE "+query,newList,null,null);

      // What I want to produce from this is a filled-in reference map, where the parentid is the
      // key, and the value is an ArrayList of DocumentReference objects.

      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i);
        String parentIDHash = (String)row.getValue(intrinsicLinkManager.parentIDHashField);
        String childIDHash = (String)row.getValue(intrinsicLinkManager.childIDHashField);
        String linkType = (String)row.getValue(intrinsicLinkManager.linkTypeField);
        if (linkType == null)
          linkType = "";
        if (childIDHash == null)
          childIDHash = "";
        ArrayList children = (ArrayList)referenceMap.get(parentIDHash);
        children.add(new DocumentReference(childIDHash,linkType));
        i++;
      }
    }

    /** Queue the parents on the evaluation queue. */
    protected void queueParents(DocumentNode node)
    {
      Iterator iter = node.getCurrentParents();
      while (iter.hasNext())
      {
        DocumentNode dn = (DocumentNode)iter.next();
        if (dn != null && dn.getTrialAnswer().getAnswer() != ANSWER_UNKNOWN)
        {
          // This is no longer needed, since it's not ordered anymore.
          // evaluationQueue.removeFromQueue(dn);
          evaluationQueue.addToQueue(dn);
        }
      }
    }


    /** Make a node be complete.  This involves writing the node's data to the database,
    * if appropriate.
    */
    protected void makeNodeComplete(DocumentNode node)
      throws ManifoldCFException
    {
      node.makeComplete();
      // Clean up children.
      removeChildLinks(node);
      if (node.isWriteNeeded())
      {
        // The answer did not not change, so notification of parents is unnecessary.
        // But, we need to write this value to the database now.
        writeCachedDistance(jobID,legalLinkTypes,node,hopcountMethod);
        node.clearWriteNeeded();
      }
    }

    /** Queue up a set of questions.  If the question is completed, nothing is done and the node is
    * returned. If the question is queued already, the node may be modified if the question is more specific than what was
    * already there.  In any case, if the answer isn't ready, null is returned.
    *@param questions are the set of questions.
    */
    protected DocumentNode[] queueQuestions(Question[] questions)
      throws ManifoldCFException
    {
      DocumentNode[] rval = new DocumentNode[questions.length];

      // Map for keeping track of questions that need to check database data.
      HashMap requestHash = new HashMap();

      int z = 0;
      while (z < questions.length)
      {
        Question q = questions[z++];

        if (Logging.hopcount.isDebugEnabled())
          Logging.hopcount.debug("Queuing question: DocID='"+q.getDocumentIdentifierHash()+"' Linktype='"+q.getLinkType()+
          "'");

        // The first thing to do is locate any existing nodes that correspond to the question,
        // and find the ones we need to query the database for.
        DocumentNode dn = (DocumentNode)questionLookupMap.get(q);
        if (dn != null)
        {
          if (Logging.hopcount.isDebugEnabled())
            Logging.hopcount.debug("Question exists: DocID='"+q.getDocumentIdentifierHash()+"' Linktype='"+q.getLinkType()+
            "'");

          // Try to figure out what to do based on the node's status.
          // Possible options include:
          // 1) Just use the node's complete answer as it stands
          // 2) Wait on the node to have a complete answer

          if (dn.isAnswerComplete())
          {
            if (Logging.hopcount.isDebugEnabled())
              Logging.hopcount.debug("Answer complete for: DocID='"+q.getDocumentIdentifierHash()+"' Linktype='"+q.getLinkType()+
              "'");
            continue;
          }

          // The answer is incomplete.
          if (Logging.hopcount.isDebugEnabled())
            Logging.hopcount.debug("Returning incomplete answer: DocID='"+q.getDocumentIdentifierHash()+"' Linktype='"+q.getLinkType()+
            "'");

          continue;
        }

        // If it's the root, build a record with zero distance.
        if (q.getDocumentIdentifierHash() == null || q.getDocumentIdentifierHash().length() == 0)
        {
          Logging.hopcount.debug("Creating root document node, with distance 0");
          Answer a = new Answer(0);
          dn = new DocumentNode(q);
          dn.setStartingAnswer(a);
          dn.setTrialAnswer(a);
          // Leave bestPossibleAnswer alone.  It's not used after node is marked complete.
          dn.makeCompleteNoWrite();
          questionLookupMap.put(q,dn);
          continue;
        }

        // There is no existing node.  Put a null value in the slot, and throw the question into a hash
        // so we can ask it later (as part of a bulk request).
        requestHash.put(q,q);
      }

      // Query for any cached entries that correspond to questions in the request hash
      Question[] unansweredQuestions = new Question[requestHash.size()];
      z = 0;
      Iterator iter = requestHash.keySet().iterator();
      while (iter.hasNext())
      {
        Question q = (Question)iter.next();
        unansweredQuestions[z++] = q;
      }

      // Look up the cached distances in bulk
      DocumentNode[] nodes = readCachedNodes(jobID,unansweredQuestions);
      z = 0;
      while (z < nodes.length)
      {
        Question q = unansweredQuestions[z];
        DocumentNode dn = nodes[z];

        // If the node is not complete, need to queue it.
        if (!dn.isComplete())
        {
          // We don't know the distance, so we need to calculate it.
          // Queue the question in the child fetch pool.  That pool reads the children and queues them,
          // and queues the parent for evaluation.
          childFetchQueue.addToQueue(dn);
        }

        questionLookupMap.put(q,dn);

        z++;
      }

      // Go through the original questions again, and look up the nodes to return.
      z = 0;
      while (z < questions.length)
      {
        Question q = questions[z];
        rval[z] = (DocumentNode)questionLookupMap.get(q);
        z++;
      }
      return rval;
    }

    /** Notify parents of a node's change of state. */
    protected void notifyParents(DocumentNode node)
    {
      Iterator iter = node.getCurrentParents();
      while (iter.hasNext())
      {
        DocumentNode dn = (DocumentNode)iter.next();
        if (dn.getTrialAnswer().getAnswer() != ANSWER_UNKNOWN)
        {
          // As long as it's not on the childFetch queue, we put it onto
          // the eval queue
          evaluationQueue.removeFromQueue(dn);
          evaluationQueue.addToQueue(dn);
        }
      }
    }

    /** Remove remaining links to children. */
    protected void removeChildLinks(DocumentNode dn)
    {
      Iterator iter = dn.getCurrentChildren();
      while (iter.hasNext())
      {
        NodeReference nr = (NodeReference)iter.next();
        // Ditch the parent reference
        DocumentNode child = nr.getNode();
        child.removeParent(dn);
      }
      dn.clearChildReferences();
    }

  }

}
