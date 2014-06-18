/* $Id: BaseTable.java 999670 2010-09-21 22:18:19Z kwright $ */

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
package org.apache.manifoldcf.core.database;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This class is a base class that provides a common foundation for table managers
* for various different tables in the system.
*/
public class BaseTable
{
  public static final String _rcsid = "@(#)$Id: BaseTable.java 999670 2010-09-21 22:18:19Z kwright $";

  protected IDBInterface dbInterface;
  protected String tableName;

  public BaseTable(IDBInterface dbInterface, String tableName)
  {
    this.dbInterface = dbInterface;
    this.tableName = tableName;
  }

  protected IDBInterface getDBInterface()
  {
    return dbInterface;
  }

  public String getTableName()
  {
    return tableName;
  }

  public String getDatabaseCacheKey()
  {
    return dbInterface.getDatabaseCacheKey();
  }

  public String getTransactionID()
  {
    return dbInterface.getTransactionID();
  }

  /** Perform an insert operation.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  protected void performInsert(Map parameterMap, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performInsert(tableName,parameterMap,invalidateKeys);
  }

  /** Perform an update operation.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param parameterMap is the map of column name/values to write.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  protected void performUpdate(Map parameterMap, String whereClause, List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performUpdate(tableName,parameterMap,whereClause,whereParameters,invalidateKeys);
  }

  /** Perform a delete operation.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  protected void performDelete(String whereClause, List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performDelete(tableName,whereClause,whereParameters,invalidateKeys);
  }

  /** Perform a table creation operation.
  *@param columnMap is the map describing the columns and types.  NOTE that these are abstract
  * types, which will be mapped to the proper types for the actual database inside this
  * layer.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  protected void performCreate(Map columnMap, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performCreate(tableName,columnMap,invalidateKeys);
  }

  /** Perform a table alter operation.
  *@param columnMap is the map describing the columns and types to add.  These
  * are in the same form as for performCreate.
  *@param columnModifyMap is the map describing the columns to modify.  These
  * are in the same form as for performCreate.
  *@param columnDeleteList is the list of column names to delete.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performAlter(Map columnMap, Map columnModifyMap, List<String> columnDeleteList,
    StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performAlter(tableName,columnMap,columnModifyMap,columnDeleteList,invalidateKeys);
  }

  /** Add an index to a table.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  protected void addTableIndex(boolean unique, List<String> columnList)
    throws ManifoldCFException
  {
    dbInterface.addTableIndex(tableName,unique,columnList);
  }

  /** Add an index to a table.
  *@param indexName is the optional name of the table index.  If null, a name will be chosen automatically.
  *@param description is the index description.
  */
  protected void performAddIndex(String indexName, IndexDescription description)
    throws ManifoldCFException
  {
    dbInterface.performAddIndex(indexName,tableName,description);
  }

  /** Remove an index.
  *@param indexName is the name of the index to remove.
  */
  public void performRemoveIndex(String indexName)
    throws ManifoldCFException
  {
    dbInterface.performRemoveIndex(indexName,tableName);
  }

  /** Analyze this table.
  */
  protected void analyzeTable()
    throws ManifoldCFException
  {
    dbInterface.analyzeTable(tableName);
  }

  /** Reindex this table.
  */
  protected void reindexTable()
    throws ManifoldCFException
  {
    dbInterface.reindexTable(tableName);
  }

  /** Perform a table drop operation.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  protected void performDrop(StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performDrop(tableName,invalidateKeys);
  }

  /** Get the current table schema.
  *@param invalidateKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@return a map of column names & ColumnDescription's, or null.
  */
  protected Map getTableSchema(StringSet invalidateKeys, String queryClass)
    throws ManifoldCFException
  {
    return dbInterface.getTableSchema(tableName,invalidateKeys,queryClass);
  }

  /** Get a table's indexes.
  *@param invalidateKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of index names and IndexDescription objects, describing the indexes.
  */
  protected Map getTableIndexes(StringSet invalidateKeys, String queryClass)
    throws ManifoldCFException
  {
    return dbInterface.getTableIndexes(tableName,invalidateKeys,queryClass);
  }

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  protected void performModification(String query, List params, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    dbInterface.performModification(query,params,invalidateKeys);
  }

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@return a resultset.
  */
  protected IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    return dbInterface.performQuery(query,params,cacheKeys,queryClass);
  }

  /** Perform a general "data fetch" query, with the ability to provide a limit.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@param resultLimit is the maximum number of results desired.
  *@return a resultset.
  */
  protected IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass, int resultLimit)
    throws ManifoldCFException
  {
    return dbInterface.performQuery(query,params,cacheKeys,queryClass,resultLimit,null);
  }

  /** Begin a database transaction.  This method call MUST be paired with an endTransaction() call,
  * or database handles will be lost.  If the transaction should be rolled back, then signalRollback() should
  * be called before the transaction is ended.
  * It is strongly recommended that the code that uses transactions be structured so that a try block
  * starts immediately after this method call.  The body of the try block will contain all direct or indirect
  * calls to executeQuery().  After this should be a catch for every exception type, including Error, which should call the
  * signalRollback() method, and rethrow the exception.  Then, after that a finally{} block which calls endTransaction().
  */
  protected void beginTransaction()
    throws ManifoldCFException
  {
    dbInterface.beginTransaction();
  }

  /** Perform the transaction commit.
  * Calling this method does not relieve the coder of the responsibility of calling endTransaction(),
  * as listed below.  The purpose of a separate commit operation is to allow handling of situations where the
  * commit generates a TRANSACTION_ABORT signal.
  */
  public void performCommit()
    throws ManifoldCFException
  {
    dbInterface.performCommit();
  }

  /** Signal that a rollback should occur on the next endTransaction().
  */
  protected void signalRollback()
  {
    dbInterface.signalRollback();
  }

  /** End a database transaction, either performing a commit or a rollback (depending on whether
  * signalRollback() was called within the transaction).
  */
  protected void endTransaction()
    throws ManifoldCFException
  {
    dbInterface.endTransaction();
  }

  /** Get a random amount to sleep for (to resolve a deadlock) */
  protected long getSleepAmt()
  {
    return dbInterface.getSleepAmt();
  }
  
  /** Sleep for a specified amount, to resolve a deadlock */
  protected void sleepFor(long amt)
    throws ManifoldCFException
  {
    dbInterface.sleepFor(amt);
  }
  
  /** Note a number of inserts, modifications, or deletions to a specific table.  This is so we can decide when to do appropriate maintenance.
  *@param tableName is the name of the table being modified.
  *@param insertCount is the number of inserts.
  *@param modifyCount is the number of updates.
  *@param deleteCount is the number of deletions.
  */
  public void noteModifications(int insertCount, int modifyCount, int deleteCount)
    throws ManifoldCFException
  {
    dbInterface.noteModifications(tableName,insertCount,modifyCount,deleteCount);
  }

  /** Construct a key that is database specific, and applies to queries
  * made against a specific table name.
  */
  public String makeTableKey()
  {
    return CacheKeyFactory.makeTableKey(null,tableName,dbInterface.getDatabaseName());
  }

  /** Construct a cast to a double value.
  * On most databases this cast needs to be explicit, but on some it is implicit (and cannot be in fact
  * specified).
  *@param value is the value to be cast.
  *@return the query chunk needed.
  */
  public String constructDoubleCastClause(String value)
  {
    return dbInterface.constructDoubleCastClause(value);
  }

  /** Construct a count clause.
  * On most databases this will be COUNT(col), but on some the count needs to be cast to a BIGINT, so
  * CAST(COUNT(col) AS BIGINT) will be emitted instead.
  *@param column is the column string to be counted.
  *@return the query chunk needed.
  */
  public String constructCountClause(String column)
  {
    return dbInterface.constructCountClause(column);
  }

  /** Construct a regular-expression match clause.
  * This method builds both the text part of a regular-expression match.
  *@param column is the column specifier string.
  *@param regularExpression is the properly-quoted regular expression string, or "?" if a parameterized value is to be used.
  *@param caseInsensitive is true of the regular expression match is to be case insensitive.
  *@return the query chunk needed, not padded with spaces on either side.
  */
  public String constructRegexpClause(String column, String regularExpression, boolean caseInsensitive)
  {
    return dbInterface.constructRegexpClause(column,regularExpression,caseInsensitive);
  }

  /** Construct a regular-expression substring clause.
  * This method builds an expression that extracts a specified string section from a field, based on
  * a regular expression.
  *@param column is the column specifier string.
  *@param regularExpression is the properly-quoted regular expression string, or "?" if a parameterized value is to be used.
  *@param caseInsensitive is true if the regular expression match is to be case insensitive.
  *@return the expression chunk needed, not padded with spaces on either side.
  */
  public String constructSubstringClause(String column, String regularExpression, boolean caseInsensitive)
  {
    return dbInterface.constructSubstringClause(column,regularExpression,caseInsensitive);
  }

  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit)
  {
    return dbInterface.constructOffsetLimitClause(offset,limit);
  }

  /** Construct a 'distinct on (x)' filter.
  * This filter wraps a query and returns a new query whose results are similar to POSTGRESQL's DISTINCT-ON feature.
  * Specifically, for each combination of the specified distinct fields in the result, only the first such row is included in the final
  * result.
  *@param outputParameters is a blank list into which to put parameters.  Null may be used if the baseParameters parameter is null.
  *@param baseQuery is the base query, which is another SELECT statement, without parens,
  * e.g. "SELECT ..."
  *@param baseParameters are the parameters corresponding to the baseQuery.
  *@param distinctFields are the fields to consider to be distinct.  These should all be keys in otherFields below.
  *@param orderFields are the otherfield keys that determine the ordering.
  *@param orderFieldsAscending are true for orderFields that are ordered as ASC, false for DESC.  
  *@param otherFields are the rest of the fields to return, keyed by the AS name, value being the base query column value, e.g. "value AS key"
  *@return a revised query that performs the necessary DISTINCT ON operation.  The list outputParameters will also be appropriately filled in.
  */
  public String constructDistinctOnClause(List outputParameters, String baseQuery, List baseParameters,
    String[] distinctFields, String[] orderFields, boolean[] orderFieldsAscending, Map<String,String> otherFields)
  {
    return dbInterface.constructDistinctOnClause(outputParameters,baseQuery,baseParameters,distinctFields,
      orderFields,orderFieldsAscending,otherFields);
  }

  /* Calculate the number of values a particular clause can have, given the values for all the other clauses.
  * For example, if in the expression x AND y AND z, x has 2 values and z has 1, find out how many values x can legally have
  * when using the buildConjunctionClause() method below.
  */
  public int findConjunctionClauseMax(ClauseDescription[] otherClauseDescriptions)
  {
    return dbInterface.findConjunctionClauseMax(otherClauseDescriptions);
  }
  
  /* Construct a conjunction clause, e.g. x AND y AND z, where there is expected to be an index (x,y,z,...), and where x, y, or z
  * can have multiple distinct values, The proper implementation of this method differs from database to database, because some databases
  * only permit index operations when there are OR's between clauses, such as x1 AND y1 AND z1 OR x2 AND y2 AND z2 ..., where others
  * only recognize index operations when there are lists specified for each, such as x IN (x1,x2) AND y IN (y1,y2) AND z IN (z1,z2).
  */
  public String buildConjunctionClause(List outputParameters, ClauseDescription[] clauseDescriptions)
  {
    return dbInterface.buildConjunctionClause(outputParameters,clauseDescriptions);
  }

  /** Obtain the maximum number of individual items that should be
  * present in an IN clause.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of IN clause members.
  */
  protected int getMaxInClause()
  {
    return dbInterface.getMaxInClause();
  }

  /** Obtain the maximum number of individual clauses that should be
  * present in a sequence of OR clauses.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of OR clause members.
  */
  protected int getMaxOrClause()
  {
    return dbInterface.getMaxOrClause();
  }

  /** Calculate history cutoff for windowed report queries.
  * @return the cutoff history row count.
  */
  public int getWindowedReportMaxRows()
  {
    return dbInterface.getWindowedReportMaxRows();
  }

  /** Set up a base object from a database row.
  *@param object is the object to read into.
  *@param resultRow is the row to use to initialize the object.
  */
  public static void readRow(BaseObject object, IResultRow resultRow)
  {
    Iterator iter = resultRow.getColumns();
    while (iter.hasNext())
    {
      String columnName = (String)iter.next();
      Object columnValue = resultRow.getValue(columnName);
      object.setValue(columnName,columnValue);
    }
  }

  /** Read the specified fields from the specified object, and
  * build a Map, which can be used to write the data to the database.
  *@param fieldSet is the set of fields.
  *@param object is the BaseObject to get the data from.
  *@return the map.
  */
  public static Map prepareRowForSave(BaseObject object, StringSet fieldSet)
  {
    HashMap rval = new HashMap();
    Iterator keys = fieldSet.getKeys();
    while (keys.hasNext())
    {
      String keyName = (String)keys.next();
      Object x = object.getValue(keyName);
      if (x != null)
        rval.put(keyName,x);
    }
    return rval;
  }

  // More may follow, as table services expand

}
