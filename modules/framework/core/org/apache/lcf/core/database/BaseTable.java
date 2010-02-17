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
package org.apache.lcf.core.database;

import org.apache.lcf.core.interfaces.*;
import java.util.*;

/** This class is a base class that provides a common foundation for table managers
* for various different tables in the system.
*/
public class BaseTable
{
	public static final String _rcsid = "@(#)$Id$";

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

	/** Perform a table lock operation.
	*/
	protected void performLock()
		throws MetacartaException
	{
		dbInterface.performLock(tableName);
	}

	/** Perform an insert operation.
	*@param invalidateKeys are the cache keys that should be
	* invalidated.
	*@param parameterMap is the map of column name/values to write.
	*/
	protected void performInsert(Map parameterMap, StringSet invalidateKeys)
		throws MetacartaException
	{
		dbInterface.performInsert(tableName,parameterMap,invalidateKeys);
	}

	/** Perform an update operation.
	*@param invalidateKeys are the cache keys that should be invalidated.
	*@param parameterMap is the map of column name/values to write.
	*@param whereClause is the where clause describing the match (including the WHERE), or null if none.
	*@param whereParameters are the parameters that come with the where clause, if any.
	*/
	protected void performUpdate(Map parameterMap, String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
		throws MetacartaException
	{
		dbInterface.performUpdate(tableName,parameterMap,whereClause,whereParameters,invalidateKeys);
	}

	/** Perform a delete operation.
	*@param invalidateKeys are the cache keys that should be invalidated.
	*@param whereClause is the where clause describing the match (including the WHERE), or null if none.
	*@param whereParameters are the parameters that come with the where clause, if any.
	*/
	protected void performDelete(String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
		throws MetacartaException
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
		throws MetacartaException
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
	public void performAlter(Map columnMap, Map columnModifyMap, ArrayList columnDeleteList,
		StringSet invalidateKeys)
		throws MetacartaException
	{
		dbInterface.performAlter(tableName,columnMap,columnModifyMap,columnDeleteList,invalidateKeys);
	}

	/** Add an index to a table.
	*@param unique is a boolean that if true describes a unique index.
	*@param columnList is the list of columns that need to be included
	* in the index, in order.
	*/
	protected void addTableIndex(boolean unique, ArrayList columnList)
		throws MetacartaException
	{
		dbInterface.addTableIndex(tableName,unique,columnList);
	}

	/** Add an index to a table.
	*@param indexName is the optional name of the table index.  If null, a name will be chosen automatically.
	*@param description is the index description.
	*/
	protected void performAddIndex(String indexName, IndexDescription description)
		throws MetacartaException
	{
		dbInterface.performAddIndex(indexName,tableName,description);
	}

	/** Remove an index.
	*@param indexName is the name of the index to remove.
	*/
	public void performRemoveIndex(String indexName)
		throws MetacartaException
	{
		dbInterface.performRemoveIndex(indexName);
	}

	/** Analyze this table.
	*/
	protected void analyzeTable()
		throws MetacartaException
	{
		dbInterface.analyzeTable(tableName);
	}

	/** Reindex this table.
	*/
	protected void reindexTable()
		throws MetacartaException
	{
		dbInterface.reindexTable(tableName);
	}

	/** Perform a table drop operation.
	*@param invalidateKeys are the cache keys that should be invalidated, if any.
	*/
	protected void performDrop(StringSet invalidateKeys)
		throws MetacartaException
	{
		dbInterface.performDrop(tableName,invalidateKeys);
	}

	/** Get the current table schema.
	*@param cacheKeys are the cache keys, if needed (null if no cache desired).
	*@param queryClass is the LRU class name against which this query would be cached,
	* or null if no LRU behavior desired.
	*@return a map of column names & ColumnDescription's, or null.
	*/
	protected Map getTableSchema(StringSet invalidateKeys, String queryClass)
		throws MetacartaException
	{
		return dbInterface.getTableSchema(tableName,invalidateKeys,queryClass);
	}

	/** Get a table's indexes.
	*@param cacheKeys are the keys against which to cache the query, or null.
	*@param queryClass is the name of the query class, or null.
	*@return a map of index names and IndexDescription objects, describing the indexes.
	*/
	protected Map getTableIndexes(StringSet invalidateKeys, String queryClass)
		throws MetacartaException
	{
		return dbInterface.getTableIndexes(tableName,invalidateKeys,queryClass);
	}

	/** Perform a general database modification query.
	*@param query is the query string.
	*@param params are the parameterized values, if needed.
	*@param invalidateKeys are the cache keys to invalidate.
	*/
	protected void performModification(String query, ArrayList params, StringSet invalidateKeys)
		throws MetacartaException
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
	protected IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass)
		throws MetacartaException
	{
		return dbInterface.performQuery(query,params,cacheKeys,queryClass);
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
		throws MetacartaException
	{
		dbInterface.beginTransaction();
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
		throws MetacartaException
	{
		dbInterface.endTransaction();
	}


	/** Construct a key that is database specific, and applies to queries
	* made against a specific table name.
	*/
	public String makeTableKey()
	{
		return CacheKeyFactory.makeTableKey(null,tableName,dbInterface.getDatabaseName());
	}


	/** Quote a sql string.
	* This method quotes a sql string in the proper manner for the database in question.
	*@param string is the input string.
	*@return the properly quoted (and escaped) output string.
	*/
	protected String quoteSQLString(String string)
	{
		return dbInterface.quoteSQLString(string);
	}

	/** Prepare a sql date for use in a query.
	* This method prepares a query constant using the sql date string passed in.
	* The date passed in is presumed to be in "standard form", or something that might have
	* come back from a resultset of a query.
	*@param date is the date in standard form.
	*@return the sql date expression to use for date comparisons.
	*/
	protected String prepareSQLDate(String date)
	{
		return dbInterface.prepareSQLDate(date);
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
