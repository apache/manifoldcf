/* $Id: DBInterfacePostgreSQL.java 999670 2010-09-21 22:18:19Z kwright $ */

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

public class DBInterfacePostgreSQL extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id: DBInterfacePostgreSQL.java 999670 2010-09-21 22:18:19Z kwright $";

  /** PostgreSQL host name property */
  public static final String postgresqlHostnameProperty = "org.apache.manifoldcf.postgresql.hostname";
  /** PostgreSQL port property */
  public static final String postgresqlPortProperty = "org.apache.manifoldcf.postgresql.port";
  /** PostgreSQL ssl property */
  public static final String postgresqlSslProperty = "org.apache.manifoldcf.postgresql.ssl";

  private static final String _defaultUrl = "jdbc:postgresql://localhost/";
  private static final String _driver = "org.postgresql.Driver";

  /** A lock manager handle. */
  protected final ILockManager lockManager;
  
  // Database cache key
  protected final String cacheKey;
	
  // Postgresql serializable transactions are broken in that transactions that occur within them do not in fact work properly.
  // So, once we enter the serializable realm, STOP any additional transactions from doing anything at all.
  protected int serializableDepth = 0;

  // This is where we keep track of tables that we need to analyze on transaction exit
  protected List<String> tablesToAnalyze = new ArrayList<String>();

  // Keep track of tables to reindex on transaction exit
  protected List<String> tablesToReindex = new ArrayList<String>();

  // This is where we keep temporary table statistics, which accumulate until they reach a threshold, and then are added into shared memory.
  
  /** Accumulated reindex statistics.  This map is keyed by the table name, and contains TableStatistics values. */
  protected static Map<String,TableStatistics> currentReindexStatistics = new HashMap<String,TableStatistics>();
  /** Table reindex thresholds, as read from configuration information.  Keyed by table name, contains Integer values. */
  protected static Map<String,Integer> reindexThresholds = new HashMap<String,Integer>();
  
  /** Accumulated analyze statistics.  This map is keyed by the table name, and contains TableStatistics values. */
  protected static Map<String,TableStatistics> currentAnalyzeStatistics = new HashMap<String,TableStatistics>();
  /** Table analyze thresholds, as read from configuration information.  Keyed by table name, contains Integer values. */
  protected static Map<String,Integer> analyzeThresholds = new HashMap<String,Integer>();
  
  /** The number of inserts, deletes, etc. before we update the shared area. */
  protected static final int commitThreshold = 100;

  // Lock and shared datum name prefixes (to be combined with table names)
  protected static final String statslockReindexPrefix = "statslock-reindex-";
  protected static final String statsReindexPrefix = "stats-reindex-";
  protected static final String statslockAnalyzePrefix = "statslock-analyze-";
  protected static final String statsAnalyzePrefix = "stats-analyze-";
  

  public DBInterfacePostgreSQL(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,getJdbcUrl(tc,databaseName),_driver,databaseName,userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    lockManager = LockManagerFactory.make(tc);
  }
  
  private static String getJdbcUrl(final IThreadContext tc, final String databaseName)
    throws ManifoldCFException
  {
    String jdbcUrl = _defaultUrl + databaseName;
    final String hostname = LockManagerFactory.getProperty(tc,postgresqlHostnameProperty);
    final String ssl = LockManagerFactory.getProperty(tc,postgresqlSslProperty);
    final String port = LockManagerFactory.getProperty(tc,postgresqlPortProperty);
    if (hostname != null && hostname.length() > 0)
    {
      jdbcUrl = "jdbc:postgresql://" + hostname;
      if (port != null && port.length() > 0)
      {
        jdbcUrl += ":" + port;
      }
      jdbcUrl += "/" + databaseName;
      if (ssl != null && ssl.equals("true"))
      {
        jdbcUrl += "?ssl=true";
      }
    }
    return jdbcUrl;
  }

  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  @Override
  public void openDatabase()
    throws ManifoldCFException
  {
    // Nothing to do
  }
  
  /** Uninitialize.  This method is called during JVM shutdown, in order to close
  * all database communication.
  */
  @Override
  public void closeDatabase()
    throws ManifoldCFException
  {
    // Nothing to do
  }

  /** Get the database general cache key.
  *@return the general cache key for the database.
  */
  @Override
  public String getDatabaseCacheKey()
  {
    return cacheKey;
  }

  /** Perform an insert operation.
  *@param tableName is the name of the table.
  *@param invalidateKeys are the cache keys that should be
  * invalidated.
  *@param parameterMap is the map of column name/values to write.
  */
  @Override
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
  @Override
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
  @Override
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
  @Override
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
  @Override
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
          performModification("ALTER TABLE ONLY "+tableName+" DROP "+columnName,null,invalidateKeys);
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
          String renameColumn = "__temp__";
          // Rename current column
          performModification("ALTER TABLE ONLY "+tableName+" RENAME "+columnName+" TO "+renameColumn,null,invalidateKeys);
          // Create new column
          StringBuilder sb = new StringBuilder();
          appendDescription(sb,columnName,cd,true);
          performModification("ALTER TABLE ONLY "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
          // Copy old data to new
          performModification("UPDATE "+tableName+" SET "+columnName+"="+renameColumn,null,invalidateKeys);
          // Make the column null, if it needs it
          if (cd.getIsNull() == false)
            performModification("ALTER TABLE ONLY "+tableName+" ALTER "+columnName+" SET NOT NULL",null,invalidateKeys);
          // Drop old column
          performModification("ALTER TABLE ONLY "+tableName+" DROP "+renameColumn,null,invalidateKeys);
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
          performModification("ALTER TABLE ONLY "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
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
      return "text";
    if (inputType.equalsIgnoreCase("blob"))
      return "bytea";
    return inputType;
  }

  /** Add an index to a table.
  *@param tableName is the name of the table to add the index for.
  *@param unique is a boolean that if true describes a unique index.
  *@param columnList is the list of columns that need to be included
  * in the index, in order.
  */
  @Override
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
  @Override
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
  @Override
  public void performRemoveIndex(String indexName, String tableName)
    throws ManifoldCFException
  {
    performModification("DROP INDEX "+indexName,null,null);
  }


  /** Perform a table drop operation.
  *@param tableName is the name of the table to drop.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  @Override
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
  @Override
  public void createUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    // Create a connection to the master database, using the credentials supplied
    Database masterDatabase = new DBInterfacePostgreSQL(context,"template1",adminUserName,adminPassword);
    try
    {
      // Create user
      List params = new ArrayList();
      params.add(userName);
      IResultSet set = masterDatabase.executeQuery("SELECT * FROM pg_user WHERE usename=?",params,
        null,null,null,true,-1,null,null);
      if (set.getRowCount() == 0)
      {
        // We have to quote the password.  Due to a postgresql bug, parameters don't work for this field.
        StringBuilder sb = new StringBuilder();
        sb.append("'");
        int i = 0;
        while (i < password.length())
        {
          char x = password.charAt(i);
          if (x == '\'')
            sb.append("'");
          sb.append(x);
          i++;
        }
        sb.append("'");
        String quotedPassword = sb.toString();
	masterDatabase.executeQuery("CREATE USER "+userName+" PASSWORD "+quotedPassword,null,
          null,invalidateKeys,null,false,0,null,null);
      }
      
      // Create database
      params.clear();
      params.add(databaseName);
      set = masterDatabase.executeQuery("SELECT * FROM pg_database WHERE datname=?",params,
        null,null,null,true,-1,null,null);
      if (set.getRowCount() == 0)
      {
        // Special for Postgresql
        masterDatabase.prepareForDatabaseCreate();
	masterDatabase.executeQuery("CREATE DATABASE "+databaseName+" OWNER "+
	  userName+" ENCODING 'utf8'",null,null,invalidateKeys,null,false,0,null,null);
      }
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
  }

  /** Drop user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  @Override
  public void dropUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    // Create a connection to the master database, using the credentials supplied
    Database masterDatabase = new DBInterfacePostgreSQL(context,"template1",adminUserName,adminPassword);
    try
    {
      // Drop database
      masterDatabase.executeQuery("DROP DATABASE "+databaseName,null,null,invalidateKeys,null,false,0,null,null);
      // Drop user
      masterDatabase.executeQuery("DROP USER "+userName,null,null,invalidateKeys,null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
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
    {
      //e.printStackTrace();
      return theException;
    }
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Exception "+theException.getMessage()+" is possibly a transaction abort signal");
    java.sql.SQLException sqlException = (java.sql.SQLException)e;
    String message = sqlException.getMessage();
    String sqlState = sqlException.getSQLState();
    // If connection is closed, presume we are shutting down
    if (sqlState != null && sqlState.equals("08003"))
      return new ManifoldCFException(message,e,ManifoldCFException.INTERRUPTED);
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
    // New Postgresql behavior (9.3): sometimes we don't get an exception thrown, but the transaction is dead nonetheless.
    if (sqlState != null && sqlState.equals("25P02"))
      return new ManifoldCFException(message,e,ManifoldCFException.DATABASE_TRANSACTION_ABORT);
      
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Exception "+theException.getMessage()+" is NOT a transaction abort signal");
    //e.printStackTrace();
    //System.err.println("sqlstate = "+sqlState);
    return theException;
  }

  /** Perform a general database modification query.
  *@param query is the query string.
  *@param params are the parameterized values, if needed.
  *@param invalidateKeys are the cache keys to invalidate.
  */
  @Override
  public void performModification(String query, List params, StringSet invalidateKeys)
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
  @Override
  public Map<String,ColumnDescription> getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    List list = new ArrayList();
    query.append("SELECT pg_attribute.attname AS \"Field\",");
    query.append("CASE pg_type.typname WHEN 'int2' THEN 'smallint' WHEN 'int4' THEN 'int'");
    query.append(" WHEN 'int8' THEN 'bigint' WHEN 'varchar' THEN 'varchar(' || pg_attribute.atttypmod-4 || ')'");
    query.append(" WHEN 'text' THEN 'longtext'");
    query.append(" WHEN 'bpchar' THEN 'char(' || pg_attribute.atttypmod-4 || ')'");
    query.append(" ELSE pg_type.typname END AS \"Type\",");
    query.append("CASE WHEN pg_attribute.attnotnull THEN '' ELSE 'YES' END AS \"Null\",");
    query.append("CASE pg_type.typname WHEN 'varchar' THEN substring(pg_attrdef.adsrc from '^(.*).*$') ELSE pg_attrdef.adsrc END AS Default ");
    query.append("FROM pg_class INNER JOIN pg_attribute ON (pg_class.oid=pg_attribute.attrelid) INNER JOIN pg_type ON (pg_attribute.atttypid=pg_type.oid) ");
    query.append("LEFT JOIN pg_attrdef ON (pg_class.oid=pg_attrdef.adrelid AND pg_attribute.attnum=pg_attrdef.adnum) ");
    query.append("WHERE pg_class.relname=? AND pg_attribute.attnum>=1 AND NOT pg_attribute.attisdropped ");
    query.append("ORDER BY pg_attribute.attnum");
    list.add(tableName);

    IResultSet set = performQuery(query.toString(),list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;
    // Digest the result
    Map<String,ColumnDescription> rval = new HashMap<String,ColumnDescription>();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = row.getValue("Field").toString();
      String type = row.getValue("Type").toString();
      boolean isNull = row.getValue("Null").toString().equals("YES");
      boolean isPrimaryKey = false; // row.getValue("Key").toString().equals("PRI");
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
  @Override
  public Map<String,IndexDescription> getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    Map<String,IndexDescription> rval = new HashMap<String,IndexDescription>();

    String query = "SELECT pg_catalog.pg_get_indexdef(i.indexrelid, 0, true) AS indexdef "+
      "FROM pg_catalog.pg_class c, pg_catalog.pg_class c2, pg_catalog.pg_index i "+
      "WHERE c.relname = ? AND c.oid = i.indrelid AND i.indexrelid = c2.oid";
    List list = new ArrayList();
    list.add(tableName);
    
    IResultSet result = performQuery(query,list,cacheKeys,queryClass);
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String indexdef = (String)row.getValue("indexdef");

      // Parse the command!
      boolean isUnique;
      int parsePosition = 0;
      int beforeMatch = indexdef.indexOf("CREATE UNIQUE INDEX ",parsePosition);
      if (beforeMatch == -1)
      {
        beforeMatch = indexdef.indexOf("CREATE INDEX ",parsePosition);
        if (beforeMatch == -1)
          throw new ManifoldCFException("Cannot parse index description: '"+indexdef+"'");
        isUnique = false;
        parsePosition += "CREATE INDEX ".length();
      }
      else
      {
        isUnique = true;
        parsePosition += "CREATE UNIQUE INDEX ".length();
      }

      int afterMatch = indexdef.indexOf(" ON",parsePosition);
      if (afterMatch == -1)
        throw new ManifoldCFException("Cannot parse index description: '"+indexdef+"'");
      String indexName = indexdef.substring(parsePosition,afterMatch);
      parsePosition = afterMatch + " ON".length();
      int parenPosition = indexdef.indexOf("(",parsePosition);
      if (parenPosition == -1)
        throw new ManifoldCFException("Cannot parse index description: '"+indexdef+"'");
      parsePosition = parenPosition + 1;
      List<String> columns = new ArrayList<String>();
      while (true)
      {
        int nextIndex = indexdef.indexOf(",",parsePosition);
        int nextParenIndex = indexdef.indexOf(")",parsePosition);
        if (nextIndex == -1)
          nextIndex = nextParenIndex;
        if (nextIndex == -1)
          throw new ManifoldCFException("Cannot parse index description: '"+indexdef+"'");
        if (nextParenIndex != -1 && nextParenIndex < nextIndex)
          nextIndex = nextParenIndex;

        String columnName = indexdef.substring(parsePosition,nextIndex).trim();
        columns.add(columnName);

        if (nextIndex == nextParenIndex)
          break;
        parsePosition = nextIndex + 1;
      }

      String[] columnNames = new String[columns.size()];
      int j = 0;
      while (j < columnNames.length)
      {
        columnNames[j] = columns.get(j);
        j++;
      }
      rval.put(indexName,new IndexDescription(isUnique,columnNames));
    }

    return rval;
  }

  /** Get a database's tables.
  *@param cacheKeys are the cache keys for the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return the set of tables.
  */
  @Override
  public StringSet getAllTables(StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT relname FROM pg_class",null,cacheKeys,queryClass);
    StringSetBuffer ssb = new StringSetBuffer();
    String columnName = "relname";

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
  @Override
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass)
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
  @Override
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
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
  @Override
  public IResultSet performQuery(String query, List params, StringSet cacheKeys, String queryClass,
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

  /** Construct a cast to a double value.
  * On most databases this cast needs to be explicit, but on some it is implicit (and cannot be in fact
  * specified).
  *@param value is the value to be cast.
  *@return the query chunk needed.
  */
  @Override
  public String constructDoubleCastClause(String value)
  {
    return "CAST("+value+" AS DOUBLE PRECISION)";
  }

  /** Construct a count clause.
  * On most databases this will be COUNT(col), but on some the count needs to be cast to a BIGINT, so
  * CAST(COUNT(col) AS BIGINT) will be emitted instead.
  *@param column is the column string to be counted.
  *@return the query chunk needed.
  */
  @Override
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
  @Override
  public String constructRegexpClause(String column, String regularExpression, boolean caseInsensitive)
  {
    return column + "~" + (caseInsensitive?"*":"") + regularExpression;
  }

  /** Construct a regular-expression substring clause.
  * This method builds an expression that extracts a specified string section from a field, based on
  * a regular expression.
  *@param column is the column specifier string.
  *@param regularExpression is the properly-quoted regular expression string, or "?" if a parameterized value is to be used.
  *@param caseInsensitive is true if the regular expression match is to be case insensitive.
  *@return the expression chunk needed, not padded with spaces on either side.
  */
  @Override
  public String constructSubstringClause(String column, String regularExpression, boolean caseInsensitive)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("SUBSTRING(");
    if (caseInsensitive)
      sb.append("LOWER(").append(column).append(")");
    else
      sb.append(column);
    sb.append(" FROM ");
    if (caseInsensitive)
      sb.append("LOWER(").append(regularExpression).append(")");
    else
      sb.append(regularExpression);
    sb.append(")");
    return sb.toString();
  }

  /** Construct an offset/limit clause.
  * This method constructs an offset/limit clause in the proper manner for the database in question.
  *@param offset is the starting offset number.
  *@param limit is the limit of result rows to return.
  *@param afterOrderBy is true if this offset/limit comes after an ORDER BY.
  *@return the proper clause, with no padding spaces on either side.
  */
  @Override
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
  @Override
  public String constructDistinctOnClause(List outputParameters, String baseQuery, List baseParameters,
    String[] distinctFields, String[] orderFields, boolean[] orderFieldsAscending, Map<String,String> otherFields)
  {
    // Copy arguments
    if (baseParameters != null)
      outputParameters.addAll(baseParameters);

    StringBuilder sb = new StringBuilder("SELECT DISTINCT ON(");
    int i = 0;
    while (i < distinctFields.length)
    {
      if (i > 0)
        sb.append(",");
      sb.append(distinctFields[i++]);
    }
    sb.append(") ");
    Iterator<String> iter = otherFields.keySet().iterator();
    boolean needComma = false;
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
      i = 0;
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
  @Override
  public int getMaxInClause()
  {
    return 100;
  }

  /** Obtain the maximum number of individual clauses that should be
  * present in a sequence of OR clauses.  Exceeding this amount will potentially cause the query performance
  * to drop.
  *@return the maximum number of OR clause members.
  */
  @Override
  public int getMaxOrClause()
  {
    return 25;
  }

  /* Calculate the number of values a particular clause can have, given the values for all the other clauses.
  * For example, if in the expression x AND y AND z, x has 2 values and z has 1, find out how many values x can legally have
  * when using the buildConjunctionClause() method below.
  */
  @Override
  public int findConjunctionClauseMax(ClauseDescription[] otherClauseDescriptions)
  {
    // This implementation uses "OR"
    return getMaxOrClause();
  }

  /* Construct a conjunction clause, e.g. x AND y AND z, where there is expected to be an index (x,y,z,...), and where x, y, or z
  * can have multiple distinct values, The proper implementation of this method differs from database to database, because some databases
  * only permit index operations when there are OR's between clauses, such as x1 AND y1 AND z1 OR x2 AND y2 AND z2 ..., where others
  * only recognize index operations when there are lists specified for each, such as x IN (x1,x2) AND y IN (y1,y2) AND z IN (z1,z2).
  */
  @Override
  public String buildConjunctionClause(List outputParameters, ClauseDescription[] clauseDescriptions)
  {
    // This implementation uses "OR" instead of "IN ()" for multiple values, since this generates better plans in Postgresql 9.x.
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < clauseDescriptions.length ; i++)
    {
      ClauseDescription cd = clauseDescriptions[i];
      if (i > 0)
        sb.append(" AND ");
      String columnName = cd.getColumnName();
      List values = cd.getValues();
      String operation = cd.getOperation();
      String joinColumn = cd.getJoinColumnName();
      if (values != null)
      {
        if (values.size() > 1)
        {
          sb.append(" (");
          for (int j = 0 ; j < values.size() ; j++)
          {
            if (j > 0)
              sb.append(" OR ");
            sb.append(columnName).append(operation).append("?");
            outputParameters.add(values.get(j));
          }
          sb.append(")");
        }
        else
        {
          sb.append(columnName).append(operation).append("?");
          outputParameters.add(values.get(0));
        }
      }
      else if (joinColumn != null)
      {
        sb.append(columnName).append(operation).append(joinColumn);
      }
      else
        sb.append(columnName).append(operation);
    }
    return sb.toString();
  }

  /** For windowed report queries, e.g. maxActivity or maxBandwidth, obtain the maximum number of rows
  * that can reasonably be expected to complete in an acceptable time.
  *@return the maximum number of rows.
  */
  @Override
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
  @Override
  public void beginTransaction()
    throws ManifoldCFException
  {
    beginTransaction(TRANSACTION_ENCLOSING);
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
  @Override
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
      super.beginTransaction(TRANSACTION_READCOMMITTED);
      break;
    case TRANSACTION_SERIALIZED:
      super.beginTransaction(TRANSACTION_SERIALIZED);
      try
      {
        performModification("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE",null,null);
      }
      catch (Error e)
      {
        super.signalRollback();
        super.endTransaction();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        super.signalRollback();
        super.endTransaction();
        throw e;
      }
      break;
    default:
      throw new ManifoldCFException("Bad transaction type: "+Integer.toString(transactionType));
    }
  }

  /** Signal that a rollback should occur on the next endTransaction().
  */
  @Override
  public void signalRollback()
  {
    if (serializableDepth == 0)
      super.signalRollback();
  }

  /** End a database transaction, either performing a commit or a rollback (depending on whether
  * signalRollback() was called within the transaction).
  */
  @Override
  public void endTransaction()
    throws ManifoldCFException
  {
    if (serializableDepth > 0)
    {
      serializableDepth--;
      return;
    }

    super.endTransaction();
    if (getTransactionID() == null)
    {
      int i = 0;
      while (i < tablesToAnalyze.size())
      {
        analyzeTableInternal(tablesToAnalyze.get(i++));
      }
      tablesToAnalyze.clear();
      i = 0;
      while (i < tablesToReindex.size())
      {
        reindexTableInternal(tablesToReindex.get(i++));
      }
      tablesToReindex.clear();
    }
  }

  /** Abstract method to start a transaction */
  @Override
  protected void startATransaction()
    throws ManifoldCFException
  {
    try
    {
      executeViaThread((connection==null)?null:connection.getConnection(),"START TRANSACTION",null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
  }

  /** Abstract method to commit a transaction */
  @Override
  protected void commitCurrentTransaction()
    throws ManifoldCFException
  {
    try
    {
      executeViaThread((connection==null)?null:connection.getConnection(),"COMMIT",null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }

  }
  
  /** Abstract method to roll back a transaction */
  @Override
  protected void rollbackCurrentTransaction()
    throws ManifoldCFException
  {
    try
    {
      executeViaThread((connection==null)?null:connection.getConnection(),"ROLLBACK",null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      throw reinterpretException(e);
    }
  }
  
  /** Abstract method for explaining a query */
  @Override
  protected void explainQuery(String query, List params)
    throws ManifoldCFException
  {
    // We really can't retry at this level; it's not clear what the transaction nesting is etc.
    // So if the EXPLAIN fails due to deadlock, we just give up.
    IResultSet x;
    String queryType = "EXPLAIN ";
    if ("SELECT".equalsIgnoreCase(query.substring(0,6)))
      queryType += "ANALYZE ";
    x = executeUncachedQuery(queryType+query,params,true,
      -1,null,null);
    for (int k = 0; k < x.getRowCount(); k++)
    {
      IResultRow row = x.getRow(k);
      Iterator<String> iter = row.getColumns();
      String colName = (String)iter.next();
      Logging.db.warn(" Plan: "+row.getValue(colName).toString());
    }
    Logging.db.warn("");

    if (query.indexOf("jobqueue") != -1)
    {
      // Dump jobqueue stats
      x = executeUncachedQuery("select n_distinct, most_common_vals, most_common_freqs from pg_stats where tablename='jobqueue' and attname='status'",null,true,-1,null,null);
      for (int k = 0; k < x.getRowCount(); k++)
      {
        IResultRow row = x.getRow(k);
        Logging.db.warn(" Stats: n_distinct="+row.getValue("n_distinct").toString()+" most_common_vals="+row.getValue("most_common_vals").toString()+" most_common_freqs="+row.getValue("most_common_freqs").toString());
      }
      Logging.db.warn("");
    }
  }

  
  /** Read a datum, presuming zero if the datum does not exist.
  */
  protected int readDatum(String datumName)
    throws ManifoldCFException
  {
    byte[] bytes = lockManager.readData(datumName);
    if (bytes == null)
      return 0;
    return (((int)bytes[0]) & 0xff) + ((((int)bytes[1]) & 0xff) << 8) + ((((int)bytes[2]) & 0xff) << 16) + ((((int)bytes[3]) & 0xff) << 24);
  }

  /** Write a datum, presuming zero if the datum does not exist.
  */
  protected void writeDatum(String datumName, int value)
    throws ManifoldCFException
  {
    byte[] bytes = new byte[4];
    bytes[0] = (byte)(value & 0xff);
    bytes[1] = (byte)((value >> 8) & 0xff);
    bytes[2] = (byte)((value >> 16) & 0xff);
    bytes[3] = (byte)((value >> 24) & 0xff);
    
    lockManager.writeData(datumName,bytes);
  }

  /** Analyze a table.
  *@param tableName is the name of the table to analyze/calculate statistics for.
  */
  public void analyzeTable(String tableName)
    throws ManifoldCFException
  {
    String tableStatisticsLock = statslockAnalyzePrefix+tableName;
    lockManager.enterWriteCriticalSection(tableStatisticsLock);
    try
    {
      TableStatistics ts = currentAnalyzeStatistics.get(tableName);
      // Lock this table's statistics files
      lockManager.enterWriteLock(tableStatisticsLock);
      try
      {
        String eventDatum = statsAnalyzePrefix+tableName;
        // Time to reindex this table!
        analyzeTableInternal(tableName);
        // Now, clear out the data
        writeDatum(eventDatum,0);
        if (ts != null)
          ts.reset();
      }
      finally
      {
        lockManager.leaveWriteLock(tableStatisticsLock);
      }
    }
    finally
    {
      lockManager.leaveWriteCriticalSection(tableStatisticsLock);
    }
  }

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws ManifoldCFException
  {
    String tableStatisticsLock;
    
    // Reindexing.
    tableStatisticsLock = statslockReindexPrefix+tableName;
    lockManager.enterWriteCriticalSection(tableStatisticsLock);
    try
    {
      TableStatistics ts = currentReindexStatistics.get(tableName);
      // Lock this table's statistics files
      lockManager.enterWriteLock(tableStatisticsLock);
      try
      {
        String eventDatum = statsReindexPrefix+tableName;
        // Time to reindex this table!
        reindexTableInternal(tableName);
        // Now, clear out the data
        writeDatum(eventDatum,0);
        if (ts != null)
          ts.reset();
      }
      finally
      {
        lockManager.leaveWriteLock(tableStatisticsLock);
      }
    }
    finally
    {
      lockManager.leaveWriteCriticalSection(tableStatisticsLock);
    }
  }

  protected void analyzeTableInternal(String tableName)
    throws ManifoldCFException
  {
    if (getTransactionID() == null)
      performModification("ANALYZE "+tableName,null,null);
    else
      tablesToAnalyze.add(tableName);
  }

  protected void reindexTableInternal(String tableName)
    throws ManifoldCFException
  {
    if (getTransactionID() == null)
    {
      long sleepAmt = 0L;
      while (true)
      {
        try
        {
          performModification("REINDEX TABLE "+tableName,null,null);
          break;
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
          {
            sleepAmt = getSleepAmt();
            continue;
          }
          throw e;
        }
        finally
        {
          sleepFor(sleepAmt);
        }
      }
    }
    else
      tablesToReindex.add(tableName);
  }

  /** Note a number of inserts, modifications, or deletions to a specific table.  This is so we can decide when to do appropriate maintenance.
  *@param tableName is the name of the table being modified.
  *@param insertCount is the number of inserts.
  *@param modifyCount is the number of updates.
  *@param deleteCount is the number of deletions.
  */
  @Override
  protected void noteModificationsNoTransactions(String tableName, int insertCount, int modifyCount, int deleteCount)
    throws ManifoldCFException
  {
    String tableStatisticsLock;
    int eventCount;
    
    // Reindexing.
    // Here we count tuple deletion.  So we want to know the deletecount + modifycount.
    eventCount = modifyCount + deleteCount;
    tableStatisticsLock = statslockReindexPrefix+tableName;
    lockManager.enterWriteCriticalSection(tableStatisticsLock);
    try
    {
      Integer threshold = reindexThresholds.get(tableName);
      int reindexThreshold;
      if (threshold == null)
      {
        // Look for this parameter; if we don't find it, use a default value.
        reindexThreshold = lockManager.getSharedConfiguration().getIntProperty("org.apache.manifoldcf.db.postgres.reindex."+tableName,250000);
        reindexThresholds.put(tableName,new Integer(reindexThreshold));
      }
      else
        reindexThreshold = threshold.intValue();
      
      TableStatistics ts = currentReindexStatistics.get(tableName);
      if (ts == null)
      {
        ts = new TableStatistics();
        currentReindexStatistics.put(tableName,ts);
      }
      ts.add(eventCount);
      // Check if we have passed threshold yet for this table, for committing the data to the shared area
      if (ts.getEventCount() >= commitThreshold)
      {
        // Lock this table's statistics files
        lockManager.enterWriteLock(tableStatisticsLock);
        try
        {
          String eventDatum = statsReindexPrefix+tableName;
          int oldEventCount = readDatum(eventDatum);
          oldEventCount += ts.getEventCount();
          if (oldEventCount >= reindexThreshold)
          {
            // Time to reindex this table!
            reindexTableInternal(tableName);
            // Now, clear out the data
            writeDatum(eventDatum,0);
          }
          else
            writeDatum(eventDatum,oldEventCount);
          ts.reset();
        }
        finally
        {
          lockManager.leaveWriteLock(tableStatisticsLock);
        }
      }
    }
    finally
    {
      lockManager.leaveWriteCriticalSection(tableStatisticsLock);
    }
    
    // Analysis.
    // Here we count tuple addition.
    eventCount = modifyCount + insertCount;
    tableStatisticsLock = statslockAnalyzePrefix+tableName;
    lockManager.enterWriteCriticalSection(tableStatisticsLock);
    try
    {
      Integer threshold = analyzeThresholds.get(tableName);
      int analyzeThreshold;
      if (threshold == null)
      {
        // Look for this parameter; if we don't find it, use a default value.
        analyzeThreshold = lockManager.getSharedConfiguration().getIntProperty("org.apache.manifoldcf.db.postgres.analyze."+tableName,2000);
        analyzeThresholds.put(tableName,new Integer(analyzeThreshold));
      }
      else
        analyzeThreshold = threshold.intValue();
      
      TableStatistics ts = currentAnalyzeStatistics.get(tableName);
      if (ts == null)
      {
        ts = new TableStatistics();
        currentAnalyzeStatistics.put(tableName,ts);
      }
      ts.add(eventCount);
      // Check if we have passed threshold yet for this table, for committing the data to the shared area
      if (ts.getEventCount() >= commitThreshold)
      {
        // Lock this table's statistics files
        lockManager.enterWriteLock(tableStatisticsLock);
        try
        {
          String eventDatum = statsAnalyzePrefix+tableName;
          int oldEventCount = readDatum(eventDatum);
          oldEventCount += ts.getEventCount();
          if (oldEventCount >= analyzeThreshold)
          {
            // Time to reindex this table!
            analyzeTableInternal(tableName);
            // Now, clear out the data
            writeDatum(eventDatum,0);
          }
          else
            writeDatum(eventDatum,oldEventCount);
          ts.reset();
        }
        finally
        {
          lockManager.leaveWriteLock(tableStatisticsLock);
        }
      }
    }
    finally
    {
      lockManager.leaveWriteCriticalSection(tableStatisticsLock);
    }

  }
  

  /** Table accumulation records.
  */
  protected static class TableStatistics
  {
    protected int eventCount = 0;
    
    public TableStatistics()
    {
    }
    
    public void reset()
    {
      eventCount = 0;
    }
    
    public void add(int eventCount)
    {
      this.eventCount += eventCount;
    }
    
    public int getEventCount()
    {
      return eventCount;
    }
  }
  
}

