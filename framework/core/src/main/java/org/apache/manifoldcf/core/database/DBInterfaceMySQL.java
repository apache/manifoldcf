/* $Id: DBInterfaceMySQL.java 999670 2010-09-21 22:18:19Z kwright $ */

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
import java.util.*;

public class DBInterfaceMySQL extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id: DBInterfaceMySQL.java 999670 2010-09-21 22:18:19Z kwright $";

  /** MySQL server property */
  public static final String mysqlServerProperty = "org.apache.manifoldcf.mysql.server";

  private static final String _driver = "com.mysql.jdbc.Driver";
  
  protected String cacheKey;

  public DBInterfaceMySQL(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,getJdbcUrl(databaseName),_driver,databaseName,userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
  }

  private static String getJdbcUrl(String theDatabaseName)
  {
    String server =  ManifoldCF.getProperty(mysqlServerProperty);
    if (server == null || server.length() == 0)
      server = "localhost";
    return "jdbc:mysql://"+server+"/"+theDatabaseName+"?useUnicode=true&characterEncoding=utf8";
  }

  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  public void openDatabase()
    throws ManifoldCFException
  {
    // Nothing to do.
  }
  
  /** Uninitialize.  This method is called during JVM shutdown, in order to close
  * all database communication.
  */
  public void closeDatabase()
    throws ManifoldCFException
  {
    // Nothing to do.
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
    //performModification("LOCK TABLE "+tableName+" IN EXCLUSIVE MODE",null,null);
  }

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  public void performInsert(String tableName, Map<String,Object> parameterMap, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    List paramArray = new ArrayList();

    StringBuilder bf = new StringBuilder();
    bf.append("INSERT INTO ");
    bf.append(tableName);
    bf.append(" (") ;

    StringBuilder values = new StringBuilder(" VALUES (");

    // loop for cols
    Iterator<Map.Entry<String,Object>> it = parameterMap.entrySet().iterator();
    boolean first = true;
    while (it.hasNext())
    {
      Map.Entry<String,Object> e = it.next();
      String key = e.getKey();

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
  public void performUpdate(String tableName, Map<String,Object> parameterMap, String whereClause,
    List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    List paramArray = new ArrayList();

    StringBuilder bf = new StringBuilder();
    bf.append("UPDATE ");
    bf.append(tableName);
    bf.append(" SET ") ;

    // loop for parameters
    Iterator<Map.Entry<String,Object>> it = parameterMap.entrySet().iterator();
    boolean first = true;
    while (it.hasNext())
    {
      Map.Entry<String,Object> e = it.next();
      String key = e.getKey();

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
  public void performDelete(String tableName, String whereClause, List whereParameters, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    StringBuilder bf = new StringBuilder();
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
  public void performCreate(String tableName, Map<String,ColumnDescription> columnMap, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    StringBuilder queryBuffer = new StringBuilder("CREATE TABLE ");
    queryBuffer.append(tableName);
    queryBuffer.append('(');
    Iterator<String> iter = columnMap.keySet().iterator();
    boolean first = true;
    while (iter.hasNext())
    {
      String columnName = iter.next();
      ColumnDescription cd = columnMap.get(columnName);
      if (!first)
        queryBuffer.append(',');
      else
        first = false;
      appendDescription(queryBuffer,columnName,cd,false);
    }
    queryBuffer.append(')');
    performModification(queryBuffer.toString(),null,invalidateKeys);
  }

  protected static void appendDescription(StringBuilder queryBuffer, String columnName, ColumnDescription cd, boolean forceNull)
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

  /** Map a standard type into a derby type.
  *@param inputType is the input type.
  *@return the output type.
  */
  protected static String mapType(String inputType)
  {
    if (inputType.equalsIgnoreCase("float"))
      return "DOUBLE";
    if (inputType.equalsIgnoreCase("blob"))
      return "LONGBLOB";
    return inputType;
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
  public void performAlter(String tableName, Map<String,ColumnDescription> columnMap,
    Map<String,ColumnDescription> columnModifyMap, List<String> columnDeleteList,
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
          String columnName = columnDeleteList.get(i++);
          performModification("ALTER TABLE "+tableName+" DROP "+columnName,null,invalidateKeys);
        }
      }

      // Do the modifies.  This involves renaming each column to a temp column, then creating a new one, then copying
      if (columnModifyMap != null)
      {
        Iterator<String> iter = columnModifyMap.keySet().iterator();
        while (iter.hasNext())
        {
          String columnName = iter.next();
          ColumnDescription cd = columnModifyMap.get(columnName);
          StringBuilder sb = new StringBuilder();
          appendDescription(sb,columnName,cd,false);
          performModification("ALTER TABLE "+tableName+" MODIFY "+sb.toString(),null,invalidateKeys);
        }
      }

      // Now, do the adds
      if (columnMap != null)
      {
        Iterator<String> iter = columnMap.keySet().iterator();
        while (iter.hasNext())
        {
          String columnName = iter.next();
          ColumnDescription cd = columnMap.get(columnName);
          StringBuilder sb = new StringBuilder();
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

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  public void addTableIndex(String tableName, boolean unique, List<String> columnList)
    throws ManifoldCFException
  {
    String[] columns = new String[columnList.size()];
    int i = 0;
    while (i < columns.length)
    {
      columns[i] = columnList.get(i);
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
    StringBuilder queryBuffer = new StringBuilder("CREATE ");
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
  *@param tableName is the table the index belongs to.
  */
  public void performRemoveIndex(String indexName, String tableName)
    throws ManifoldCFException
  {
    performModification("DROP INDEX "+indexName+" ON "+tableName,null,null);
  }

  /** Analyze a table.
  *@param tableName is the name of the table to analyze/calculate statistics for.
  */
  public void analyzeTable(String tableName)
    throws ManifoldCFException
  {
    // Does nothing
  }

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws ManifoldCFException
  {
    // Does nothing
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
    // Connect to super database

    Database masterDatabase = new DBInterfaceMySQL(context,"mysql",adminUserName,adminPassword);

    List list = new ArrayList();
    try
    {
      list.add("utf8");
      list.add("utf8_bin");
      masterDatabase.executeQuery("CREATE DATABASE "+databaseName+" CHARACTER SET ? COLLATE ?",list,
        null,invalidateKeys,null,false,0,null,null);
    } catch (ManifoldCFException e){
      if (e.getErrorCode() != 4)
	throw new ManifoldCFException(e.getMessage());
    }
    if (userName != null)
    {
      try {
        list.clear();
        list.add(userName);
        list.add("localhost");
        list.add(password);
        masterDatabase.executeQuery("GRANT ALL ON "+databaseName+".* TO ?@? IDENTIFIED BY ?",list,
          null,invalidateKeys,null,false,0,null,null);
      } catch (ManifoldCFException e){
        if (e.getErrorCode() != 4)
          throw new ManifoldCFException(e.getMessage());
      }
      //masterDatabase.executeQuery("USE " + databaseName,null,
      //  null,invalidateKeys,null,false,0,null,null);
    }
  }

  /** Drop user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void dropUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    // Connect to super database
    Database masterDatabase = new DBInterfaceMySQL(context,"mysql",adminUserName,adminPassword);
    masterDatabase.executeQuery("DROP DATABASE "+databaseName,null,null,invalidateKeys,null,false,0,null,null);
    //masterDatabase.executeQuery("USE mysql",null,
    //  null,invalidateKeys,null,false,0,null,null);
  }

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  public void performModification(String query, List params, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    executeQuery(query,params,null,invalidateKeys,null,false,0,null,null);
  }

  /** Get a table's schema.
  *@param tableName is the name of the table.
  *@param cacheKeys are the keys against which to cache the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return a map of column names and ColumnDescription objects, describing the schema.
  */
  public Map<String,ColumnDescription> getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    List list = new ArrayList();
    list.add(databaseName.toUpperCase());
    list.add(tableName.toUpperCase());
    query.append("SELECT column_name, is_nullable, data_type, character_maximum_length ")
      .append("FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?");
    IResultSet set = performQuery(query.toString(),list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;

    query = new StringBuilder();
    query.append("SELECT t1.column_name ")
      .append("FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE t1, INFORMATION_SCHEMA.TABLE_CONSTRAINTS t2 ")
      .append("WHERE t1.CONSTRAINT_NAME=t2.CONSTRAINT_NAME AND t1.TABLE_NAME=t2.TABLE_NAME AND ")
      .append("t1.TABLE_SCHEMA=t2.TABLE_SCHEMA AND ")
      .append("t1.TABLE_SCHEMA=? AND t1.TABLE_NAME=? AND t2.CONSTRAINT_TYPE='PRIMARY KEY'");
    IResultSet primarySet = performQuery(query.toString(),list,cacheKeys,queryClass);
    String primaryKey = null;
    if (primarySet.getRowCount() != 0)
      primaryKey = ((String)primarySet.getRow(0).getValue("column_name")).toLowerCase();
    if (primaryKey == null)
      primaryKey = "";
    
    // Digest the result
    Map<String,ColumnDescription> rval = new HashMap<String,ColumnDescription>();
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
      if (type.equals("VARCHAR"))
        dataType = "VARCHAR("+width.toString()+")";
      else if (type.equals("CHAR"))
        dataType = "CHAR("+width.toString()+")";
      else if (type.equals("LONGBLOB"))
        dataType = "BLOB";
      else if (type.equals("DOUBLE PRECISION"))
        dataType = "FLOAT";
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
  public Map<String,IndexDescription> getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    Map<String,IndexDescription> rval = new HashMap<String,IndexDescription>();

    String query = "SELECT index_name,column_name,non_unique,seq_in_index FROM INFORMATION_SCHEMA.STATISTICS "+
      "WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY index_name,seq_in_index ASC";
    List list = new ArrayList();
    list.add(databaseName.toUpperCase());
    list.add(tableName.toUpperCase());
    IResultSet result = performQuery(query,list,cacheKeys,queryClass);
    String lastIndexName = null;
    List<String> indexColumns = null;
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
        indexColumns = new ArrayList<String>();
        isUnique = false;
      }
      indexColumns.add(columnName);
      isUnique = nonUnique.equals("0");
    }
    
    if (lastIndexName != null)
      addIndex(rval,lastIndexName,isUnique,indexColumns);
    
    return rval;
  }

  protected void addIndex(Map rval, String indexName, boolean isUnique, List<String> indexColumns)
  {
    if (indexName.equals("primary"))
      return;
    String[] columnNames = new String[indexColumns.size()];
    int i = 0;
    while (i < columnNames.length)
    {
      columnNames[i] = indexColumns.get(i);
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
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
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
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
    int maxResults, ILimitChecker returnLimit)
    throws ManifoldCFException
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
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
    int maxResults, ResultSpecification resultSpec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {
    return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,resultSpec,returnLimit);
  }

  /** Construct a count clause.
  * On most databases this will be COUNT(col), but on some the count needs to be cast to a BIGINT, so
  * CAST(COUNT(col) AS BIGINT) will be emitted instead.
  *@param column is the column string to be counted.
  *@return the query chunk needed.
  */
  public String constructCountClause(String column)
  {
    return "COUNT("+column+")";
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
    // MHL - do what MySQL requires, whatever that is...
    return column + " LIKE " + regularExpression;
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
    // MHL for mysql
    return regularExpression;
  }

  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@param afterOrderBy is true if this offset/limit comes after an ORDER BY.
  *@return the proper clause, with no padding spaces on either side.
  */
  public String constructOffsetLimitClause(int offset, int limit, boolean afterOrderBy)
  {
    StringBuilder sb = new StringBuilder();
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
    // I don't know whether MySql supports this functionality or not.
    // MHL
    // Copy arguments
    if (baseParameters != null)
      outputParameters.addAll(baseParameters);

    StringBuilder sb = new StringBuilder("SELECT ");
    boolean needComma = false;
    Iterator<String> iter = otherFields.keySet().iterator();
    while (iter.hasNext())
    {
      String fieldName = iter.next();
      String columnValue = otherFields.get(fieldName);
      if (needComma)
        sb.append(",");
      needComma = true;
      sb.append("txxx1.").append(columnValue).append(" AS ").append(fieldName);
    }
    sb.append(" FROM (").append(baseQuery).append(") txxx1");
    if (distinctFields.length > 0 || orderFields.length > 0)
    {
      sb.append(" ORDER BY ");
      int k = 0;
      int i = 0;
      while (i < distinctFields.length)
      {
        if (k > 0)
          sb.append(",");
        sb.append(distinctFields[i]).append(" ASC");
        k++;
        i++;
      }
      i = 0;
      while (i < orderFields.length)
      {
        if (k > 0)
          sb.append(",");
        sb.append(orderFields[i]).append(" ");
        if (orderFieldsAscending[i])
          sb.append("ASC");
        else
          sb.append("DESC");
        i++;
        k++;
      }
    }
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

  /** For windowed report queries, e.g. maxActivity or maxBandwidth, obtain the maximum number of rows
  * that can reasonably be expected to complete in an acceptable time.
  *@return the maximum number of rows.
  */
  public int getWindowedReportMaxRows()
  {
    return 5000;
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
    throws ManifoldCFException
  {
    super.beginTransaction(TRANSACTION_READCOMMITTED);
  }

  /** Abstract method to start a transaction */
  protected void startATransaction()
    throws ManifoldCFException
  {
    executeViaThread(connection,"START TRANSACTION",null,false,0,null,null);
  }

  /** Abstract method to commit a transaction */
  protected void commitCurrentTransaction()
    throws ManifoldCFException
  {
    executeViaThread(connection,"COMMIT",null,false,0,null,null);
  }
  
  /** Abstract method to roll back a transaction */
  protected void rollbackCurrentTransaction()
    throws ManifoldCFException
  {
    executeViaThread(connection,"ROLLBACK",null,false,0,null,null);
  }

}

