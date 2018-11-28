/* $Id: IDBInterface.java 999670 2010-09-21 22:18:19Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import java.util.*;

/** The purpose of this interface is to provide abstracted database table modification primitives,
* as well as general access primitives.  It is expected that the generalized database layer will
* provide the underlying services.  This layer should provide services roughly equivalent to
* the former DBInterface bean, but be callable in a pure Java fashion.
* It is furthermore intended that all abstraction database requests go through this layer.  It
* will therefore, over time, provide grander and grander levels of database query abstraction.
*
* Also note that the database parameters will be passed to the factory for this class, not
* to the individual methods.
*
*/
public interface IDBInterface
{
  public static final String _rcsid = "@(#)$Id: IDBInterface.java 999670 2010-09-21 22:18:19Z kwright $";

  public static int TRANSACTION_ENCLOSING = 0;
  public static int TRANSACTION_READCOMMITTED = 1;
  public static int TRANSACTION_SERIALIZED = 2;
  public static int TRANSACTION_REPEATABLEREAD = 3;

  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  public void openDatabase()
    throws ManifoldCFException;
  
  /** Uninitialize.  This method is called during JVM shutdown, in order to close
  * all database communication.
  */
  public void closeDatabase()
    throws ManifoldCFException;
  
  /** Get the database name.
  *@return the database name.
  */
  public String getDatabaseName();

  /** Get the current transaction id.
  *@return the current transaction identifier, or null if no transaction.
  */
  public String getTransactionID();

  /** Get the database general cache key.
  *@return the general cache key for the database.
  */
  public String getDatabaseCacheKey();

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  public void performInsert(String tableName, Map<String,Object> parameterMap, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform an update operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param parameterMap is the map of column name/values to write.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performUpdate(String tableName, Map<String,Object> parameterMap, String whereClause,
    List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a delete operation.
  *@param tableName is the name of the table to delete from.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performDelete(String tableName, String whereClause, List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a table creation operation.
  *@param tableName is the name of the table to create.
  *@param columnMap is the map describing the columns and types.  NOTE that these are abstract
  * types, which will be mapped to the proper types for the actual database inside this
  * layer.  The types are ColumnDefinition objects.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performCreate(String tableName, Map<String,ColumnDescription> columnMap, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a table alter operation.
  *@param tableName is the name of the table to alter.
  *@param columnMap is the map describing the columns and types to add.  These
  * are in the same form as for performCreate.
  *@param columnModifyMap is the map describing the columns to be changed.  The key is the
  * existing column name, and the value is the new type of the column.  Data will be copied from
  * the old column to the new.
  *@param columnDeleteList is the list of column names to delete.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performAlter(String tableName, Map<String,ColumnDescription> columnMap,
    Map<String,ColumnDescription> columnModifyMap, List<String> columnDeleteList,
    StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  public void addTableIndex(String tableName, boolean unique, List<String> columnList)
    throws ManifoldCFException;

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param indexName is the optional name of the table index.  If null, a name will be chosen automatically.
  *@param description is the index description.
  */
  public void performAddIndex(String indexName, String tableName, IndexDescription description)
    throws ManifoldCFException;

  /** Remove an index.
  *@param indexName is the name of the index to remove.
  *@param tableName is the table the index belongs to.
  */
  public void performRemoveIndex(String indexName, String tableName)
    throws ManifoldCFException;

  /** Analyze a table.
  *@param tableName is the name of the table to analyze/calculate statistics for.
  */
  public void analyzeTable(String tableName)
    throws ManifoldCFException;

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws ManifoldCFException;

  /** Perform a table drop operation.
  *@param tableName is the name of the table to drop.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performDrop(String tableName, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Create user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void createUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Drop user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void dropUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException;
    
  /** Get a table's schema.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of column names and ColumnDescription objects, describing the schema.
  */
  public Map<String,ColumnDescription> getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException;

  /** Get a table's indexes.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of index names and IndexDescription objects, describing the indexes.
  */
  public Map<String,IndexDescription> getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException;

  /** Get a database's tables.
  *@param cacheKeys are the cache keys for the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return the set of tables.
  */
  public StringSet getAllTables(StringSet cacheKeys, String queryClass)
    throws ManifoldCFException;

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  public void performModification(String query, List params, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@return a resultset.
  */
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException;

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@param maxResults is the maximum number of results returned (-1 for all).
  *@param returnLimit is a description of how to limit the return result, or null if no limit.
  *@return a resultset.
  */
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
    int maxResults, ILimitChecker returnLimit)
    throws ManifoldCFException;

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@param maxResults is the maximum number of results returned (-1 for all).
  *@param resultSpec is a result specification, or null for the standard treatment.
  *@param returnLimit is a description of how to limit the return result, or null if no limit.
  *@return a resultset.
  */
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
    int maxResults, ResultSpecification resultSpec, ILimitChecker returnLimit)
    throws ManifoldCFException;

  /** Construct index hint clause.
  * On most databases this returns an empty string, but on MySQL this returns
  * a USE INDEX hint.  It requires the name of an index.
  *@param tableName is the table the index is from.
  *@param description is the description of an index, which is expected to exist.
  *@return the query chunk that should go between the table names and the WHERE
  * clause.
  */
  public String constructIndexHintClause(String tableName, IndexDescription description)
    throws ManifoldCFException;
  
  /** Construct ORDER-BY clause meant for reading from an index.
  * Supply the field names belonging to the index, in order.
  * Also supply a corresponding boolean array, where TRUE means "ASC", and FALSE
  * means "DESC".
  *@param fieldNames are the names of the fields in the index that is to be used.
  *@param direction is a boolean describing the sorting order of the first term.
  *@return a query chunk, including "ORDER BY" text, which is appropriate for
  * at least ordering by the FIRST column supplied.
  */
  public String constructIndexOrderByClause(String[] fieldNames, boolean direction);
  
  /** Construct a cast to a double value.
  * On most databases this cast needs to be explicit, but on some it is implicit (and cannot be in fact
  * specified).
  *@param value is the value to be cast.
  *@return the query chunk needed.
  */
  public String constructDoubleCastClause(String value);
  
  /** Construct a count clause.
  * On most databases this will be COUNT(col), but on some the count needs to be cast to a BIGINT, so
  * CAST(COUNT(col) AS BIGINT) will be emitted instead.
  *@param column is the column string to be counted.
  *@return the query chunk needed.
  */
  public String constructCountClause(String column);
  
  /** Construct a regular-expression match clause.
  * This method builds a regular-expression match expression.
  *@param column is the column specifier string.
  *@param regularExpression is the properly-quoted regular expression string, or "?" if a parameterized value is to be used.
  *@param caseInsensitive is true if the regular expression match is to be case insensitive.
  *@return the query chunk needed, not padded with spaces on either side.
  */
  public String constructRegexpClause(String column, String regularExpression, boolean caseInsensitive);
  
  /** Construct a regular-expression substring clause.
  * This method builds an expression that extracts a specified string section from a field, based on
  * a regular expression.
  *@param column is the column specifier string.
  *@param regularExpression is the properly-quoted regular expression string, or "?" if a parameterized value is to be used.
  *@param caseInsensitive is true if the regular expression match is to be case insensitive.
  *@return the expression chunk needed, not padded with spaces on either side.
  */
  public String constructSubstringClause(String column, String regularExpression, boolean caseInsensitive);
  
  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@param afterOrderBy is true if this offset/limit comes after an ORDER BY.
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit, boolean afterOrderBy);
  
  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit);

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
    String[] distinctFields, String[] orderFields, boolean[] orderFieldsAscending, Map<String,String> otherFields);
  
  /* Calculate the number of values a particular clause can have, given the values for all the other clauses.
  * For example, if in the expression x AND y AND z, x has 2 values and z has 1, find out how many values x can legally have
  * when using the buildConjunctionClause() method below.
  */
  public int findConjunctionClauseMax(ClauseDescription[] otherClauseDescriptions);
  
  /* Construct a conjunction clause, e.g. x AND y AND z, where there is expected to be an index (x,y,z,...), and where x, y, or z
  * can have multiple distinct values, The proper implementation of this method differs from database to database, because some databases
  * only permit index operations when there are OR's between clauses, such as x1 AND y1 AND z1 OR x2 AND y2 AND z2 ..., where others
  * only recognize index operations when there are lists specified for each, such as x IN (x1,x2) AND y IN (y1,y2) AND z IN (z1,z2).
  */
  public String buildConjunctionClause(List outputParameters, ClauseDescription[] clauseDescriptions);
  
  /** Obtain the maximum number of individual items that should be
  * present in an IN clause.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of IN clause members.
  */
  public int getMaxInClause();

  /** Obtain the maximum number of individual clauses that should be
  * present in a sequence of OR clauses.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of OR clause members.
  */
  public int getMaxOrClause();

  /** For windowed report queries, e.g. maxActivity or maxBandwidth, obtain the maximum number of rows
  * that can reasonably be expected to complete in an acceptable time.
  *@return the maximum number of rows.
  */
  public int getWindowedReportMaxRows();
  
  /** Begin a database transaction.  This method call MUST be paired with an endTransaction() call,
  * or database handles will be lost.  If the transaction should be rolled back, then signalRollback() should
  * be called before the transaction is ended.
  * It is strongly recommended that the code that uses transactions be structured so that a try block
  * starts immediately after this method call.  The body of the try block will contain all direct or indirect
  * calls to executeQuery().  After this should be a catch for every exception type, including Error, which should call the
  * signalRollback() method, and rethrow the exception.  Then, after that a finally{} block which calls endTransaction().
  * (The kind of transaction started by this method is the current default transaction type, which is "read committed"
  * if not otherwise determined).
  */
  public void beginTransaction()
    throws ManifoldCFException;

  /** Begin a database transaction.  This method call MUST be paired with an endTransaction() call,
  * or database handles will be lost.  If the transaction should be rolled back, then signalRollback() should
  * be called before the transaction is ended.
  * It is strongly recommended that the code that uses transactions be structured so that a try block
  * starts immediately after this method call.  The body of the try block will contain all direct or indirect
  * calls to executeQuery().  After this should be a catch for every exception type, including Error, which should call the
  * signalRollback() method, and rethrow the exception.  Then, after that a finally{} block which calls endTransaction().
  *@param transactionType is the kind of transaction desired.
  */
  public void beginTransaction(int transactionType)
    throws ManifoldCFException;

  /** Perform the transaction commit.
  * Calling this method does not relieve the coder of the responsibility of calling endTransaction(),
  * as listed below.  The purpose of a separate commit operation is to allow handling of situations where the
  * commit generates a TRANSACTION_ABORT signal.
  */
  public void performCommit()
    throws ManifoldCFException;

  /** Signal that a rollback should occur on the next endTransaction().
  */
  public void signalRollback();

  /** End a database transaction, either performing a commit or a rollback or nothing (depending on whether
  * signalRollback() was called within the transaction or performCommit() or none of the above).
  */
  public void endTransaction()
    throws ManifoldCFException;

  /** Note a number of inserts, modifications, or deletions to a specific table.  This is so we can decide when to do appropriate maintenance.
  *@param tableName is the name of the table being modified.
  *@param insertCount is the number of inserts.
  *@param modifyCount is the number of updates.
  *@param deleteCount is the number of deletions.
  */
  public void noteModifications(String tableName, int insertCount, int modifyCount, int deleteCount)
    throws ManifoldCFException;

  /** Get a random time, in milliseconds, for backoff from deadlock.
  *@return the random time.
  */
  public long getSleepAmt();
  
  /** Sleep for a specified time, as part of backoff from deadlock.
  *@param time is the amount to sleep.
  */
  public void sleepFor(long time)
    throws ManifoldCFException;
    
}

