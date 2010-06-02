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

public class DBInterfaceMySQL extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id$";

  private static final String _url = "jdbc:mysql://localhost/";
  private static final String _driver = "org.gjt.mm.mysql.Driver";

  protected IThreadContext context;
  protected String cacheKey;

  public DBInterfaceMySQL(IThreadContext tc, String databaseName, String userName, String password)
    throws LCFException
  {
    super(tc,_url+databaseName,_driver,(databaseName==null)?"mysql":databaseName,userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
  }

  /** Get the database general cache key.
  *@return the general cache key for the database.
  */
  public String getDatabaseCacheKey()
  {
    return cacheKey;
  }

  /** Perform a table lock operation.
  *@param tableName is the name of the table.
  */
  public void performLock(String tableName)
    throws LCFException
  {
    performModification("LOCK TABLE "+tableName+" IN EXCLUSIVE MODE",null,null);
  }

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  public void performInsert(String tableName, Map parameterMap, StringSet invalidateKeys)
    throws LCFException
  {
    ArrayList paramArray = new ArrayList();

    StringBuffer bf = new StringBuffer();
    bf.append("INSERT INTO ");
    bf.append(tableName);
    bf.append(" (") ;

    StringBuffer values = new StringBuffer(" VALUES (");

    // loop for cols
    Iterator it = parameterMap.entrySet().iterator();
    boolean first = true;
    while (it.hasNext())
    {
      Map.Entry e = (Map.Entry)it.next();
      String key = (String)e.getKey();

      Object o = e.getValue();
      if (o != null)
      {
        paramArray.add(o);

        if (!first)
        {
          bf.append(',');
          values.append(',');
        }
        bf.append(key);
        values.append('?');

        first = false;
      }
    }

    bf.append(')');
    values.append(')');
    bf.append(values);

    // Do the modification
    performModification(bf.toString(),paramArray,invalidateKeys);
  }


  /** Perform an update operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param parameterMap is the map of column name/values to write.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performUpdate(String tableName, Map parameterMap, String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
    throws LCFException
  {
    ArrayList paramArray = new ArrayList();

    StringBuffer bf = new StringBuffer();
    bf.append("UPDATE ");
    bf.append(tableName);
    bf.append(" SET ") ;

    // loop for parameters
    Iterator it = parameterMap.entrySet().iterator();
    boolean first = true;
    while (it.hasNext())
    {
      Map.Entry e = (Map.Entry)it.next();
      String key = (String)e.getKey();

      Object o = e.getValue();

      if (!first)
      {
        bf.append(',');
      }
      bf.append(key);
      bf.append('=');
      if (o == null)
      {
        bf.append("NULL");
      }
      else
      {
        bf.append('?');
        paramArray.add(o);
      }

      first = false;
    }

    if (whereClause != null)
    {
      bf.append(' ');
      bf.append(whereClause);
      if (whereParameters != null)
      {
        for (int i = 0; i < whereParameters.size(); i++)
        {
          Object value = whereParameters.get(i);
          paramArray.add(value);
        }
      }
    }

    // Do the modification
    performModification(bf.toString(),paramArray,invalidateKeys);

  }


  /** Perform a delete operation.
  *@param tableName is the name of the table to delete from.
  *@param invalidateKeys are the cache keys that should be invalidated.
  *@param whereClause is the where clause describing the match (including the WHERE), or null if none.
  *@param whereParameters are the parameters that come with the where clause, if any.
  */
  public void performDelete(String tableName, String whereClause, ArrayList whereParameters, StringSet invalidateKeys)
    throws LCFException
  {
    StringBuffer bf = new StringBuffer();
    bf.append("DELETE FROM ");
    bf.append(tableName);
    if (whereClause != null)
    {
      bf.append(' ');
      bf.append(whereClause);
    }
    else
      whereParameters = null;

    // Do the modification
    performModification(bf.toString(),whereParameters,invalidateKeys);

  }

  /** Perform a table creation operation.
  *@param tableName is the name of the table to create.
  *@param columnMap is the map describing the columns and types.  NOTE that these are abstract
  * types, which will be mapped to the proper types for the actual database inside this
  * layer.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performCreate(String tableName, Map columnMap, StringSet invalidateKeys)
    throws LCFException
  {
    StringBuffer queryBuffer = new StringBuffer("CREATE TABLE ");
    queryBuffer.append(tableName);
    queryBuffer.append('(');
    Iterator iter = columnMap.keySet().iterator();
    boolean first = true;
    while (iter.hasNext())
    {
      String columnName = (String)iter.next();
      ColumnDescription cd = (ColumnDescription)columnMap.get(columnName);
      if (!first)
        queryBuffer.append(',');
      else
        first = false;
      queryBuffer.append(columnName);
      queryBuffer.append(' ');
      queryBuffer.append(cd.getTypeString());
      if (cd.getIsNull())
        queryBuffer.append(" NULL");
      else
        queryBuffer.append(" NOT NULL");
      if (cd.getIsPrimaryKey())
        queryBuffer.append(" PRIMARY KEY");
      if (cd.getReferenceTable() != null)
      {
        queryBuffer.append(" REFERENCES ");
        queryBuffer.append(cd.getReferenceTable());
        queryBuffer.append('(');
        queryBuffer.append(cd.getReferenceColumn());
        queryBuffer.append(") ON DELETE");
        if (cd.getReferenceCascade())
          queryBuffer.append(" CASCADE");
        else
          queryBuffer.append(" RESTRICT");
      }
    }
    queryBuffer.append(')');

    performModification(queryBuffer.toString(),null,invalidateKeys);

  }

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
    throws LCFException
  {
    // MHL
  }

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  public void addTableIndex(String tableName, boolean unique, ArrayList columnList)
    throws LCFException
  {
    String[] columns = new String[columnList.size()];
    int i = 0;
    while (i < columns.length)
    {
      columns[i] = (String)columnList.get(i);
      i++;
    }
    performAddIndex(null,tableName,new IndexDescription(unique,columns));
  }

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param indexName is the optional name of the table index.  If null, a name will be chosen automatically.
  *@param description is the index description.
  */
  public void performAddIndex(String indexName, String tableName, IndexDescription description)
    throws LCFException
  {
    String[] columnNames = description.getColumnNames();
    if (columnNames.length == 0)
      return;

    if (indexName == null)
      // Build an index name
      indexName = "I"+IDFactory.make(context);
    StringBuffer queryBuffer = new StringBuffer("CREATE ");
    if (description.getIsUnique())
      queryBuffer.append("UNIQUE ");
    queryBuffer.append("INDEX ");
    queryBuffer.append(indexName);
    queryBuffer.append(" ON ");
    queryBuffer.append(tableName);
    queryBuffer.append(" (");
    int i = 0;
    while (i < columnNames.length)
    {
      String colName = columnNames[i];
      if (i > 0)
        queryBuffer.append(',');
      queryBuffer.append(colName);
      i++;
    }
    queryBuffer.append(')');

    performModification(queryBuffer.toString(),null,null);
  }

  /** Remove an index.
  *@param indexName is the name of the index to remove.
  */
  public void performRemoveIndex(String indexName)
    throws LCFException
  {
    performModification("DROP INDEX "+indexName,null,null);
  }

  /** Analyze a table.
  *@param tableName is the name of the table to analyze/calculate statistics for.
  */
  public void analyzeTable(String tableName)
    throws LCFException
  {
    // Does nothing
  }

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws LCFException
  {
    // Does nothing
  }

  /** Perform a table drop operation.
  *@param tableName is the name of the table to drop.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performDrop(String tableName, StringSet invalidateKeys)
    throws LCFException
  {
    performModification("DROP TABLE "+tableName,null,invalidateKeys);
  }

  /** Perform user lookup.
  *@param userName is the user name to lookup.
  *@return true if the user exists.
  */
  public boolean lookupUser(String userName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    throw new LCFException("Not Supported");
  }

  /** Perform user create.
  *@param userName is the user name.
  *@param password is the user's password.
  */
  public void performCreateUser(String userName, String password)
    throws LCFException
  {
    // Does nothing for MySQL
  }

  /** Perform user delete.
  *@param userName is the user name.
  */
  public void performDropUser(String userName)
    throws LCFException
  {
    // Does nothing
  }

  /** Perform database lookup.
  *@param databaseName is the database name.
  *@param cacheKeys are the cache keys, if any.
  *@return true if the database exists.
  */
  public boolean lookupDatabase(String databaseName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    throw new LCFException("Not Supported");
  }


  /** Perform database create.
  *@param databaseName is the database name.
  *@param databaseUser is the user to grant access to the database.
  *@param databasePassword is the password of the user to grant access to the database.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performCreateDatabase(String databaseName, String databaseUser, String databasePassword,
    StringSet invalidateKeys)
    throws LCFException
  {
    beginTransaction();
    try
    {
      performModification("CREATE DATABASE "+databaseName+" CHARACTER SET "+
        quoteSQLString("utf8"),null,invalidateKeys);
      if (databaseUser != null)
      {
        performModification("GRANT ALL ON "+databaseName+".* TO "+
          quoteSQLString(databaseUser)+"@"+
          quoteSQLString("localhost")+" IDENTIFIED BY "+
          quoteSQLString(databasePassword),null,invalidateKeys);
      }
    }
    catch (LCFException e)
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

  /** Perform database drop.
  *@param databaseName is the database name.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performDropDatabase(String databaseName, StringSet invalidateKeys)
    throws LCFException
  {
    performModification("DROP DATABASE "+databaseName,null,invalidateKeys);
  }

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  public void performModification(String query, ArrayList params, StringSet invalidateKeys)
    throws LCFException
  {
    executeQuery(query,params,null,invalidateKeys,null,false,0,null,null);
  }

  /** Get a table's schema.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of column names and ColumnDescription objects, describing the schema.
  */
  public Map getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    IResultSet set = performQuery("DESCRIBE "+tableName,null,cacheKeys,queryClass);
    // Digest the result
    HashMap rval = new HashMap();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = row.getValue("Field").toString();
      String type = row.getValue("Type").toString();
      boolean isNull = row.getValue("Null").toString().equals("YES");
      boolean isPrimaryKey = row.getValue("Key").toString().equals("PRI");
      rval.put(fieldName,new ColumnDescription(type,isPrimaryKey,isNull,null,null,false));
    }

    return rval;
  }

  /** Get a table's indexes.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of index names and IndexDescription objects, describing the indexes.
  */
  public Map getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    // MHL
    return null;
  }

  /** Get a database's tables.
  *@param cacheKeys are the cache keys for the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return the set of tables.
  */
  public StringSet getAllTables(StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    IResultSet set = performQuery("SHOW TABLES",null,cacheKeys,queryClass);
    StringSetBuffer ssb = new StringSetBuffer();
    String columnName = "Tables_in_"+databaseName.toLowerCase();
    // System.out.println(columnName);

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      // Iterator iter2 = row.getColumns();
      // if (iter2.hasNext())
      //      System.out.println("column = '"+(String)iter2.next()+"'");
      String value = row.getValue(columnName).toString();
      ssb.add(value);
    }
    return new StringSet(ssb);
  }

  /** Perform a general "data fetch" query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param cacheKeys are the cache keys, if needed (null if no cache desired).
  *@param queryClass is the LRU class name against which this query would be cached,
  * or null if no LRU behavior desired.
  *@return a resultset.
  */
  public IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    return executeQuery(query,params,cacheKeys,null,queryClass,true,-1,null,null);
  }

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
    throws LCFException
  {
    return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,null,returnLimit);
  }

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
    throws LCFException
  {
    return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,resultSpec,returnLimit);
  }

  /** Quote a sql string.
  * This method quotes a sql string in the proper manner for the database in question.
  *@param string is the input string.
  *@return the properly quoted (and escaped) output string.
  */
  public String quoteSQLString(String string)
  {
    StringBuffer rval = new StringBuffer();
    char quoteChar = '\'';
    rval.append(quoteChar);
    int i = 0;
    while (i < string.length())
    {
      char x = string.charAt(i++);
      if (x == quoteChar)
        rval.append(quoteChar);
      rval.append(x);
    }
    rval.append(quoteChar);
    return rval.toString();
  }

  /** Prepare a sql date for use in a query.
  * This method prepares a query constant using the sql date string passed in.
  * The date passed in is presumed to be in "standard form", or something that might have
  * come back from a resultset of a query.
  *@param date is the date in standard form.
  *@return the sql date expression to use for date comparisons.
  */
  public String prepareSQLDate(String date)
  {
    // MHL
    return null;
  }

  /** Obtain the maximum number of individual items that should be
  * present in an IN clause.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of IN clause members.
  */
  public int getMaxInClause()
  {
    return 100;
  }

  /** Begin a database transaction.  This method call MUST be paired with an endTransaction() call,
  * or database handles will be lost.  If the transaction should be rolled back, then signalRollback() should
  * be called before the transaction is ended.
  * It is strongly recommended that the code that uses transactions be structured so that a try block
  * starts immediately after this method call.  The body of the try block will contain all direct or indirect
  * calls to executeQuery().  After this should be a catch for every exception type, including Error, which should call the
  * signalRollback() method, and rethrow the exception.  Then, after that a finally{} block which calls endTransaction().
  */
  public void beginTransaction()
    throws LCFException
  {
    super.beginTransaction(TRANSACTION_READCOMMITTED);
  }

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
    throws LCFException
  {
    super.beginTransaction(TRANSACTION_READCOMMITTED);
  }

  /** Abstract method to start a transaction */
  protected void startATransaction()
    throws LCFException
  {
    executeViaThread(connection,"START TRANSACTION",null,false,0,null,null);
  }

  /** Abstract method to commit a transaction */
  protected void commitCurrentTransaction()
    throws LCFException
  {
    executeViaThread(connection,"COMMIT",null,false,0,null,null);
  }
  
  /** Abstract method to roll back a transaction */
  protected void rollbackCurrentTransaction()
    throws LCFException
  {
    executeViaThread(connection,"ROLLBACK",null,false,0,null,null);
  }

}

