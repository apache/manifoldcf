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

  /** Perform a table lock operation.
  *@param tableName is the name of the table.
  */
  public void performLock(String tableName)
    throws ManifoldCFException;

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  public void performInsert(String tableName, Map parameterMap, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform an update operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param parameterMap is the map of column name/values to write.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performUpdate(String tableName, Map parameterMap, String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a delete operation.
  *@param tableName is the name of the table to delete from.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performDelete(String tableName, String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a table creation operation.
  *@param tableName is the name of the table to create.
  *@param columnMap is the map describing the columns and types.  NOTE that these are abstract
  * types, which will be mapped to the proper types for the actual database inside this
  * layer.  The types are ColumnDefinition objects.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performCreate(String tableName, Map columnMap, StringSet invalidateKeys)
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
  public void performAlter(String tableName, Map columnMap, Map columnModifyMap, ArrayList columnDeleteList,
    StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  public void addTableIndex(String tableName, boolean unique, ArrayList columnList)
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
  */
  public void performRemoveIndex(String indexName)
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
  public Map getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException;

  /** Get a table's indexes.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of index names and IndexDescription objects, describing the indexes.
  */
  public Map getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
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
  public void performModification(String query, ArrayList params, StringSet invalidateKeys)
    throws ManifoldCFException;

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@return a resultset.
  */
  public IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass)
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
  public IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass,
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
  public IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass,
    int maxResults, ResultSpecification resultSpec, ILimitChecker returnLimit)
    throws ManifoldCFException;

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
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit);
  
  /** Construct a 'distinct on (x)' filter.
  * This filter wraps a query and returns a new query whose results are similar to POSTGRESQL's DISTINCT-ON feature.
  * Specifically, for each combination of the specified distinct fields in the result, only the first such row is included in the final
  * result.
  *@param outputParameters is a blank arraylist into which to put parameters.  Null may be used if the baseParameters parameter is null.
  *@param baseQuery is the base query, which is another SELECT statement, without parens,
  * e.g. "SELECT ..."
  *@param baseParameters are the parameters corresponding to the baseQuery.
  *@param distinctFields are the fields to consider to be distinct.  These should all be keys in otherFields below.
  *@param otherFields are the rest of the fields to return, keyed by the AS name, value being the base query column value, e.g. "value AS key"
  *@return a revised query that performs the necessary DISTINCT ON operation.  The arraylist outputParameters will also be appropriately filled in.
  */
  public String constructDistinctOnClause(ArrayList outputParameters, String baseQuery, ArrayList baseParameters, String[] distinctFields, Map otherFields);
  
  /** Quote a sql string.
  * This method quotes a sql string in the proper manner for the database in question.
  *@param string is the input string.
  *@return the properly quoted (and escaped) output string.
  */
  public String quoteSQLString(String string);

  /** Prepare a sql date for use in a query.
  * This method prepares a query constant using the sql date string passed in.
  * The date passed in is presumed to be in "standard form", or something that might have
  * come back from a resultset of a query.
  *@param date is the date in standard form.
  *@return the sql date expression to use for date comparisons.
  */
  public String prepareSQLDate(String date);

  /** Obtain the maximum number of individual items that should be
  * present in an IN clause.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of IN clause members.
  */
  public int getMaxInClause();

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

  /** Signal that a rollback should occur on the next endTransaction().
  */
  public void signalRollback();

  /** End a database transaction, either performing a commit or a rollback (depending on whether
  * signalRollback() was called within the transaction).
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

}

