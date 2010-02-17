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
package org.apache.lcf.core.interfaces;

import java.util.*;

/** Main low-leval database interface.  This has semantics of basic database operations, as well as transaction support.  Caching is integrated
* at this level; query results are cached against the specified keys.  Other than that, this layer knows nothing of the higher applications.
* There is a different IDatabase object for each different database instance.
*/
public interface IDatabase
{
	public static final String _rcsid = "@(#)$Id$";

	public static final int TRANSACTION_READCOMMITTED = 1;
	public static final int TRANSACTION_SERIALIZED = 2;
	
	/** Get the database name.  This is often used as a cache key qualifier.
	*@return the database name.
	*/
	public String getDatabaseName();

	/** Get the current transaction id.
	*@return the current transaction identifier, or null if no transaction.
	*/
	public String getTransactionID();

	/** Get the current transaction type.  Returns "READCOMMITTED"
	* outside of a transaction.
	*/
	public int getCurrentTransactionType();
	
	/** Execute arbitrary database query, and optionally cache the result.  Cached results are
	* returned for this operation if they are valid and appropriate.  Note that any cached results
	* returned were only guaranteed to be pertinent at the time the cached result was obtained; the
	* actual data may become invalid due to other threads writing to the database.
	* This is NOT true, however, if a transaction is started.  If a transaction was started for this
	* database within this thread context, then the query
	* will be executed within the transaction, and since the transaction is owned by the current
	* thread, no others will be able to disrupt its processing.
	* @param query is the actual query string.
	* @param params if not null, are prepared statement parameters.
	* @param cacheKeys is the set of cache keys that the query result will be cached against.  If the
	* value for this parameter is null, then the query will not be cached.
	* @param invalidateKeys is the set of cache keys that the query will invalidate when the query occurs.
	* If this is null, then no keys will be invalidated. Note that if this is in a transaction, the
	* cache invalidation will only occur for queries that are part of the transaction, at least until
	* the transaction is committed.
	* @param queryClass describes the class of the query, for the purposes of LRU and expiration time.
	* The queryClass groups queries together, so that they are managed with a common set of timeouts
	* and maximum sizes.  If null, then no expiration or LRU behavior will take place.
	* @param needResult should be set to false for any query that does not generate a result, and true
	* for queries that do.  Otherwise an exception will be thrown.
	* @param maxReturn is the maximum number of rows to return.  Use -1 for infinite.
	* @param spec is the result specification object, or null for standard.
	* @param returnLimits is a description of how to limit return results (in addition to the maxReturn value).
	*  Pass null if no limits are desired.
	* @return the resultset
	*/
	public IResultSet executeQuery(String query, ArrayList params, StringSet cacheKeys, StringSet invalidateKeys,
		String queryClass, boolean needResult, int maxReturn, ResultSpecification spec, ILimitChecker returnLimits)
		throws LCFException;

	/** Begin a database transaction.  This method call MUST be paired with an endTransaction() call,
	* or database handles will be lost.  If the transaction should be rolled back, then signalRollback() should
	* be called before the transaction is ended.
	* It is strongly recommended that the code that uses transactions be structured so that a try block
	* starts immediately after this method call.  The body of the try block will contain all direct or indirect
	* calls to executeQuery().  After this should be a catch for every exception type, including Error, which should call the
	* signalRollback() method, and rethrow the exception.  Then, after that a finally{} block which calls endTransaction().
	*@param transactionType describes the type of the transaction.
	*/
	public void beginTransaction(int transactionType)
		throws LCFException;

	/** Signal that a rollback should occur on the next endTransaction().
	*/
	public void signalRollback();

	/** End a database transaction, either performing a commit or a rollback (depending on whether
	* signalRollback() was called within the transaction).
	*/
	public void endTransaction()
		throws LCFException;

}
