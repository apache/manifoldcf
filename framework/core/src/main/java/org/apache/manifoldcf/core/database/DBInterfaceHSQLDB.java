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
package org.apache.manifoldcf.core.database;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;
import java.util.*;
import java.io.*;
import java.sql.*;

/** This is the HSQLDB implementation of the IDBInterface class.
*/
public class DBInterfaceHSQLDB extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id$";

  private static final String _url = "jdbc:hsqldb:file:";
  private static final String _driver = "org.hsqldb.jdbcDriver";

  public final static String databasePathProperty = "org.apache.manifoldcf.hsqldbdatabasepath";

  protected String cacheKey;
  // Postgresql serializable transactions are broken in that transactions that occur within them do not in fact work properly.
  // So, once we enter the serializable realm, STOP any additional transactions from doing anything at all.
  protected int serializableDepth = 0;

  public DBInterfaceHSQLDB(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,_url+getFullDatabasePath(databaseName),_driver,getFullDatabasePath(databaseName),userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    this.userName = userName;
    this.password = password;
  }

  protected static String getFullDatabasePath(String databaseName)
    throws ManifoldCFException
  {
    File path = ManifoldCF.getFileProperty(databasePathProperty);
    if (path == null)
      throw new ManifoldCFException("HSQLDB database requires '"+databasePathProperty+"' property, containing a relative path");
    String pathString = path.toString().replace("\\\\","/");
    if (!pathString.endsWith("/"))
      pathString = pathString + "/";
    return pathString + databaseName;
  }

  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  public void openDatabase()
    throws ManifoldCFException
  {
    try
    {
      // Force a load of the appropriate JDBC driver
      Class.forName(_driver).newInstance();
      DriverManager.getConnection(_url+databaseName,userName,password).close();
    }
    catch (Exception e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
  }
  
  /** Uninitialize.  This method is called during JVM shutdown, in order to close
  * all database communication.
  */
  public void closeDatabase()
    throws ManifoldCFException
  {
    try
    {
      // Force a load of the appropriate JDBC driver
      Class.forName(_driver).newInstance();
    }
    catch (Exception e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }

    // For the shutdown itself, eat the exception
    try
    {
      DriverManager.getConnection(_url+databaseName+";shutdown=true",userName,password).close();
    }
    catch (Exception e)
    {
      // Never any exception!
    }
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
    throws ManifoldCFException
  {
    performModification("LOCK TABLE "+tableName+" WRITE",null,null);
  }

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  public void performInsert(String tableName, Map parameterMap, StringSet invalidateKeys)
    throws ManifoldCFException
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
    throws ManifoldCFException
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
    throws ManifoldCFException
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
    throws ManifoldCFException
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
      appendDescription(queryBuffer,columnName,cd,false);
    }
    queryBuffer.append(')');

    performModification(queryBuffer.toString(),null,invalidateKeys);

  }

  protected static void appendDescription(StringBuffer queryBuffer, String columnName, ColumnDescription cd, boolean forceNull)
  {
    queryBuffer.append(columnName);
    queryBuffer.append(' ');
    queryBuffer.append(mapType(cd.getTypeString()));
    if (forceNull || cd.getIsNull())
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
    throws ManifoldCFException
  {
    beginTransaction(TRANSACTION_ENCLOSING);
    try
    {
      if (columnDeleteList != null)
      {
        int i = 0;
        while (i < columnDeleteList.size())
        {
          String columnName = (String)columnDeleteList.get(i++);
          performModification("ALTER TABLE "+tableName+" DROP "+columnName+" RESTRICT",null,invalidateKeys);
        }
      }

      // Do the modifies.  This involves renaming each column to a temp column, then creating a new one, then copying
      if (columnModifyMap != null)
      {
        Iterator iter = columnModifyMap.keySet().iterator();
        while (iter.hasNext())
        {
          StringBuffer sb;
          String columnName = (String)iter.next();
          ColumnDescription cd = (ColumnDescription)columnModifyMap.get(columnName);
          String renameColumn = "__temp__";
          sb = new StringBuffer();
          appendDescription(sb,renameColumn,cd,true);
          // Rename current column.  This too involves a copy.
          performModification("ALTER TABLE "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
          performModification("UPDATE "+tableName+" SET "+renameColumn+"="+columnName,null,invalidateKeys);
          performModification("ALTER TABLE "+tableName+" DROP "+columnName+" RESTRICT",null,invalidateKeys);
          // Create new column
          sb = new StringBuffer();
          appendDescription(sb,columnName,cd,true);
          performModification("ALTER TABLE "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
          // Copy old data to new
          performModification("UPDATE "+tableName+" SET "+columnName+"="+renameColumn,null,invalidateKeys);
          // Make the column null, if it needs it
          if (cd.getIsNull() == false)
            performModification("ALTER TABLE "+tableName+" ALTER "+columnName+" SET NOT NULL",null,invalidateKeys);
          // Drop old column
          performModification("ALTER TABLE "+tableName+" DROP "+renameColumn+" RESTRICT",null,invalidateKeys);
        }
      }

      // Now, do the adds
      if (columnMap != null)
      {
        Iterator iter = columnMap.keySet().iterator();
        while (iter.hasNext())
        {
          String columnName = (String)iter.next();
          ColumnDescription cd = (ColumnDescription)columnMap.get(columnName);
          StringBuffer sb = new StringBuffer();
          appendDescription(sb,columnName,cd,false);
          performModification("ALTER TABLE "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
        }
      }
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


  /** Map a standard type into a postgresql type.
  *@param inputType is the input type.
  *@return the output type.
  */
  protected static String mapType(String inputType)
  {
    if (inputType.equalsIgnoreCase("longtext"))
      return "clob";
    return inputType;
  }

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  public void addTableIndex(String tableName, boolean unique, ArrayList columnList)
    throws ManifoldCFException
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
    throws ManifoldCFException
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
    throws ManifoldCFException
  {
    performModification("DROP INDEX "+indexName,null,null);
  }

  /** Analyze a table.
  *@param tableName is the name of the table to analyze/calculate statistics for.
  */
  public void analyzeTable(String tableName)
    throws ManifoldCFException
  {
    // Nothing to do.
  }

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws ManifoldCFException
  {
    // Nothing to do.
  }

  /** Perform a table drop operation.
  *@param tableName is the name of the table to drop.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void performDrop(String tableName, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    performModification("DROP TABLE "+tableName,null,invalidateKeys);
  }

  /** Create user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void createUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
  }

  /** Drop user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void dropUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    File f = new File(databaseName);
    if (f.exists())
    {
      // Try to guarantee that all connections are discarded before we shut the database down.  Otherwise we get pool warnings from bitstream.
      ConnectionFactory.releaseAll();
      // Make sure database is shut down.
      closeDatabase();
      // Now, it's OK to delete
      recursiveDelete(f);
    }
  }
  
  protected static void recursiveDelete(File f)
  {
    File[] files = f.listFiles();
    if (files != null)
    {
      int i = 0;
      while (i < files.length)
      {
        File newf = files[i++];
        if (newf.isDirectory())
          recursiveDelete(newf);
        else
          newf.delete();
      }
    }
    if (!f.delete())
      System.out.println("Failed to delete file "+f.toString());
  }

  /** Reinterpret an exception tossed by the database layer.  We need to disambiguate the various kinds of exception that
  * should be thrown.
  *@param theException is the exception to reinterpret
  *@return the reinterpreted exception to throw.
  */
  protected ManifoldCFException reinterpretException(ManifoldCFException theException)
  {
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Reinterpreting exception '"+theException.getMessage()+"'.  The exception type is "+Integer.toString(theException.getErrorCode()));
    if (theException.getErrorCode() != ManifoldCFException.DATABASE_CONNECTION_ERROR)
      return theException;
    Throwable e = theException.getCause();
    if (!(e instanceof java.sql.SQLException))
      return theException;
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Exception "+theException.getMessage()+" is possibly a transaction abort signal");
    java.sql.SQLException sqlException = (java.sql.SQLException)e;
    String message = sqlException.getMessage();
    String sqlState = sqlException.getSQLState();
    // Could not serialize
    if (sqlState != null && sqlState.equals("40001"))
      return new ManifoldCFException(message,e,ManifoldCFException.DATABASE_TRANSACTION_ABORT);
    // Deadlock detected
    if (sqlState != null && sqlState.equals("40P01"))
      return new ManifoldCFException(message,e,ManifoldCFException.DATABASE_TRANSACTION_ABORT);
    // Note well: We also have to treat 'duplicate key' as a transaction abort, since this is what you get when two threads attempt to
    // insert the same row.  (Everything only works, then, as long as there is a unique constraint corresponding to every bad insert that
    // one could make.)
    if (sqlState != null && sqlState.equals("23505"))
      return new ManifoldCFException(message,e,ManifoldCFException.DATABASE_TRANSACTION_ABORT);
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Exception "+theException.getMessage()+" is NOT a transaction abort signal");
    return theException;
  }

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  public void performModification(String query, ArrayList params, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    try
    {
      executeQuery(query,params,null,invalidateKeys,null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
  }

  /** Get a table's schema.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of column names and ColumnDescription objects, describing the schema, or null if the
  * table doesn't exist.
  */
  public Map getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    StringBuffer query = new StringBuffer();
    ArrayList list = new ArrayList();
    list.add(tableName.toUpperCase());
    query.append("SELECT column_name, is_nullable, data_type, character_maximum_length ")
      .append("FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema='PUBLIC' AND table_name=?");
    IResultSet set = performQuery(query.toString(),list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;

    query = new StringBuffer();
    query.append("SELECT column_name ")
      .append("FROM INFORMATION_SCHEMA.SYSTEM_PRIMARYKEYS WHERE table_schem='PUBLIC' AND table_name=?");
    IResultSet primarySet = performQuery(query.toString(),list,cacheKeys,queryClass);
    String primaryKey = null;
    if (primarySet.getRowCount() != 0)
      primaryKey = ((String)primarySet.getRow(0).getValue("column_name")).toLowerCase();
    if (primaryKey == null)
      primaryKey = "";
    
    // Digest the result
    HashMap rval = new HashMap();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = ((String)row.getValue("column_name")).toLowerCase();
      String type = (String)row.getValue("data_type");
      Long width = (Long)row.getValue("character_maximum_length");
      String isNullable = (String)row.getValue("is_nullable");
      boolean isPrimaryKey = primaryKey.equals(fieldName);
      boolean isNull = isNullable.equals("YES");
      String dataType;
      if (type.equals("CHARACTER VARYING"))
        dataType = "VARCHAR("+width.toString()+")";
      else if (type.equals("CLOB"))
        dataType = "LONGVARCHAR";
      else
        dataType = type;
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
    throws ManifoldCFException
  {
    Map rval = new HashMap();

    String query = "SELECT index_name,column_name,non_unique,ordinal_position FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO "+
      "WHERE table_schem='PUBLIC' AND TABLE_NAME=? ORDER BY index_name,ordinal_position ASC";
    ArrayList list = new ArrayList();
    list.add(tableName.toUpperCase());
    IResultSet result = performQuery(query,list,cacheKeys,queryClass);
    String lastIndexName = null;
    ArrayList indexColumns = null;
    boolean isUnique = false;
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String indexName = ((String)row.getValue("index_name")).toLowerCase();
      String columnName = ((String)row.getValue("column_name")).toLowerCase();
      String nonUnique = row.getValue("non_unique").toString();
      
      if (lastIndexName != null && !lastIndexName.equals(indexName))
      {
        addIndex(rval,lastIndexName,isUnique,indexColumns);
        lastIndexName = null;
        indexColumns = null;
        isUnique = false;
      }
      
      if (lastIndexName == null)
      {
        lastIndexName = indexName;
        indexColumns = new ArrayList();
        isUnique = false;
      }
      indexColumns.add(columnName);
      isUnique = nonUnique.equals("false");
    }
    
    if (lastIndexName != null)
      addIndex(rval,lastIndexName,isUnique,indexColumns);
    
    return rval;
  }

  protected void addIndex(Map rval, String indexName, boolean isUnique, ArrayList indexColumns)
  {
    if (indexName.indexOf("sys_idx") != -1)
      return;
    String[] columnNames = new String[indexColumns.size()];
    int i = 0;
    while (i < columnNames.length)
    {
      columnNames[i] = (String)indexColumns.get(i);
      i++;
    }
    rval.put(indexName,new IndexDescription(isUnique,columnNames));
  }
  
  /** Get a database's tables.
  *@param cacheKeys are the cache keys for the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return the set of tables.
  */
  public StringSet getAllTables(StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_schema='PUBLIC'",null,cacheKeys,queryClass);
    StringSetBuffer ssb = new StringSetBuffer();
    String columnName = "table_name";

    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
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
    throws ManifoldCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,-1,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
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
    throws ManifoldCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,null,returnLimit);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
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
    throws ManifoldCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,resultSpec,returnLimit);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
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
    return "REGEXP_MATCHES(CAST("+column+" AS VARCHAR(4096)),"+regularExpression+")";
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
    return "REGEXP_SUBSTRING(CAST("+column+" AS VARCHAR(4096)),"+regularExpression+")";
  }

  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit)
  {
    StringBuffer sb = new StringBuffer();
    if (offset != 0)
      sb.append("OFFSET ").append(Integer.toString(offset));
    if (limit != -1)
    {
      if (offset != 0)
        sb.append(" ");
      sb.append("LIMIT ").append(Integer.toString(limit));
    }
    return sb.toString();
  }

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
  public String constructDistinctOnClause(ArrayList outputParameters, String baseQuery, ArrayList baseParameters, String[] distinctFields, Map otherFields)
  {
    // HSQLDB does not really support this functionality.
    // We could hack a workaround, along the following lines:
    //
    // SELECT
    //   t1.bucket, t1.bytecount, t1.windowstart, t1.windowend
    // FROM
    //   (xxx) t1
    // WHERE
    //   t1.bytecount=( SELECT t2.bytecount FROM (xxx) t2 WHERE
    //     t2.bucket = t1.bucket LIMIT 1 ) AND
    //   t1.windowstart=( SELECT t2.windowstart FROM (xxx) t2 WHERE
    //     t2.bucket = t1.bucket LIMIT 1 ) AND
    //   t1.windowend=( SELECT t2.windowend FROM (xxx) t2 WHERE
    //     t2.bucket = t1.bucket LIMIT 1 )
    //
    // However, the cost of doing 3 identical and very costly queries is likely to be too high for this to be viable.

    // Copy arguments
    if (baseParameters != null)
      outputParameters.addAll(baseParameters);

    StringBuffer sb = new StringBuffer("SELECT ");
    boolean needComma = false;
    Iterator iter = otherFields.keySet().iterator();
    while (iter.hasNext())
    {
      String fieldName = (String)iter.next();
      String columnValue = (String)otherFields.get(fieldName);
      if (needComma)
        sb.append(",");
      needComma = true;
      sb.append("txxx1.").append(columnValue).append(" AS ").append(fieldName);
    }
    sb.append(" FROM (").append(baseQuery).append(") txxx1");
    return sb.toString();
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

  /** Obtain the maximum number of individual clauses that should be
  * present in a sequence of OR clauses.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of OR clause members.
  */
  public int getMaxOrClause()
  {
    return 25;
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
    throws ManifoldCFException
  {
    beginTransaction(TRANSACTION_ENCLOSING);
  }

  protected int depthCount = 0;
  protected boolean inTransaction = false;
  protected int desiredTransactionType = Connection.TRANSACTION_READ_COMMITTED;

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
    throws ManifoldCFException
  {
    if (getCurrentTransactionType() == TRANSACTION_SERIALIZED)
    {
      serializableDepth++;
      return;
    }

    if (transactionType == TRANSACTION_ENCLOSING)
    {
      transactionType = getCurrentTransactionType();
    }

    switch (transactionType)
    {
    case TRANSACTION_READCOMMITTED:
      desiredTransactionType = Connection.TRANSACTION_READ_COMMITTED;
      super.beginTransaction(TRANSACTION_READCOMMITTED);
      break;
    case TRANSACTION_SERIALIZED:
      desiredTransactionType = Connection.TRANSACTION_SERIALIZABLE;
      super.beginTransaction(TRANSACTION_SERIALIZED);
      break;
    default:
      throw new ManifoldCFException("Bad transaction type: "+Integer.toString(transactionType));
    }
  }

  /** Signal that a rollback should occur on the next endTransaction().
  */
  public void signalRollback()
  {
    if (serializableDepth == 0)
      super.signalRollback();
  }

  /** End a database transaction, either performing a commit or a rollback (depending on whether
  * signalRollback() was called within the transaction).
  */
  public void endTransaction()
    throws ManifoldCFException
  {
    if (serializableDepth > 0)
    {
      serializableDepth--;
      return;
    }

    super.endTransaction();
  }


  /** Abstract method to start a transaction */
  protected void startATransaction()
    throws ManifoldCFException
  {
    if (!inTransaction)
    {
      try
      {
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(desiredTransactionType);
      }
      catch (java.sql.SQLException e)
      {
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
      }
      inTransaction = true;
    }
    depthCount++;
  }

  /** Abstract method to commit a transaction */
  protected void commitCurrentTransaction()
    throws ManifoldCFException
  {
    if (inTransaction)
    {
      if (depthCount == 1)
      {
        try
        {
          if (connection != null)
          {
            connection.commit();
            connection.setAutoCommit(true);
          }
        }
        catch (java.sql.SQLException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
        }
        inTransaction = false;
      }
      depthCount--;
    }
    else
      throw new ManifoldCFException("Transaction nesting error!");
  }
  
  /** Abstract method to roll back a transaction */
  protected void rollbackCurrentTransaction()
    throws ManifoldCFException
  {
    if (inTransaction)
    {
      if (depthCount == 1)
      {
        try
        {
          if (connection != null)
          {
            connection.rollback();
            connection.setAutoCommit(true);
          }
        }
        catch (java.sql.SQLException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
        }
        inTransaction = false;
      }
      depthCount--;
    }
    else
      throw new ManifoldCFException("Transaction nesting error!");
  }
  
  /** Abstract method for mapping a column name from resultset */
  protected String mapColumnName(String rawColumnName)
  {
    return rawColumnName.toLowerCase();
  }

}

