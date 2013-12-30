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

  private static final String _localUrl = "jdbc:hsqldb:file:";
  private static final String _remoteUrl = "jdbc:hsqldb:";
  private static final String _driver = "org.hsqldb.jdbcDriver";

  private static Map<String,String> legalProtocolValues;
  static
  {
    legalProtocolValues = new HashMap<String,String>();
    legalProtocolValues.put("hsql","hsql");
    legalProtocolValues.put("http","http");
    legalProtocolValues.put("https","https");
  }
  
  public final static String databasePathProperty = "org.apache.manifoldcf.hsqldbdatabasepath";
  public final static String databaseProtocolProperty = "org.apache.manifoldcf.hsqldbdatabaseprotocol";
  public final static String databaseServerProperty = "org.apache.manifoldcf.hsqldbdatabaseserver";
  public final static String databasePortProperty = "org.apache.manifoldcf.hsqldbdatabaseport";
  public final static String databaseInstanceProperty = "org.apache.manifoldcf.hsqldbdatabaseinstance";
  
  protected String cacheKey;
  protected int serializableDepth = 0;
  protected boolean isRemote;
  protected String schemaNameForQueries;
  
  public DBInterfaceHSQLDB(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,getJDBCString(tc,databaseName),_driver,getDatabaseString(tc,databaseName),userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    this.isRemote = LockManagerFactory.getProperty(tc,databaseProtocolProperty) != null;
    this.userName = userName;
    this.password = password;
    if (this.isRemote)
      schemaNameForQueries = databaseName;
    else
      schemaNameForQueries = "PUBLIC";
  }

  protected static String getJDBCString(IThreadContext tc, String databaseName)
    throws ManifoldCFException
  {
    // For local, we use the database name as the name of the database files.
    // For remote, we connect to an instance specified by a different property, and use the database name as the schema name.
    String protocol = LockManagerFactory.getProperty(tc,databaseProtocolProperty);
    if (protocol == null)
      return _localUrl+getFullDatabasePath(databaseName);
    
    // Remote instance.  Build the URL.
    if (legalProtocolValues.get(protocol) == null)
      throw new ManifoldCFException("The value of the '"+databaseProtocolProperty+"' property was illegal; try hsql, http, or https");
    String server = LockManagerFactory.getProperty(tc,databaseServerProperty);
    if (server == null)
      throw new ManifoldCFException("HSQLDB remote mode requires '"+databaseServerProperty+"' property, containing a server name or IP address");
    String port = LockManagerFactory.getProperty(tc,databasePortProperty);
    if (port != null && port.length() > 0)
      server += ":"+port;
    String instanceName = LockManagerFactory.getProperty(tc,databaseInstanceProperty);
    if (instanceName != null && instanceName.length() > 0)
      server += "/" + instanceName;
    return _remoteUrl + protocol + "://" + server;
  }
  
  protected static String getDatabaseString(IThreadContext tc, String databaseName)
    throws ManifoldCFException
  {
    String protocol = LockManagerFactory.getProperty(tc,databaseProtocolProperty);
    if (protocol == null)
      return getFullDatabasePath(databaseName);
    return databaseName;
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

  /** Initialize the connection (for HSQLDB).
  * HSQLDB has a great deal of session state, and no way to pool individual connections based on it.
  * So, every time we pull a connection off the pool we have to execute a number of statements on it
  * before it can work reliably for us.  This is the abstraction that permits that to happen.
  *@param connection is the JDBC connection.
  */
  protected void initializeConnection(Connection connection)
    throws ManifoldCFException
  {
    super.initializeConnection(connection);
    // Set the schema
    executeViaThread(connection,"SET SCHEMA "+schemaNameForQueries.toUpperCase(Locale.ROOT),null,false,-1,null,null);
  }

  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  public void openDatabase()
    throws ManifoldCFException
  {
  }
  
  /** Uninitialize.  This method is called during JVM shutdown, in order to close
  * all database communication.
  */
  public void closeDatabase()
    throws ManifoldCFException
  {
    //System.out.println("Close database called");
    if (!isRemote)
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
        Connection c = DriverManager.getConnection(_localUrl+databaseName,userName,password);
        Statement s = c.createStatement();
        s.execute("SHUTDOWN");
        c.close();
      }
      catch (Exception e)
      {
        // Never any exception!
        e.printStackTrace();
      }
    }
  }

  /** Get the database general cache key.
  *@return the general cache key for the database.
  */
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
    StringBuilder queryBuffer = new StringBuilder("CREATE CACHED TABLE ");
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
          StringBuilder sb;
          String columnName = iter.next();
          ColumnDescription cd = columnModifyMap.get(columnName);
          sb = new StringBuilder();
          appendDescription(sb,columnName,cd,false);
          // Rename current column.  This too involves a copy.
          performModification("ALTER TABLE "+tableName+" ALTER COLUMN "+sb.toString(),null,invalidateKeys);
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


  /** Map a standard type into a postgresql type.
  *@param inputType is the input type.
  *@return the output type.
  */
  protected static String mapType(String inputType)
  {
    if (inputType.equalsIgnoreCase("longtext"))
      return "longvarchar";
    return inputType;
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
    if (isRemote)
    {
      // Create a connection using the admin credentials
      Database masterDatabase = new DBInterfaceHSQLDB(context,"PUBLIC",adminUserName,adminPassword);
      ArrayList params = new ArrayList();
      // First, look for user
      params.add(userName);
      IResultSet userResult = masterDatabase.executeQuery("SELECT * FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=?",params,
        null,null,null,true,-1,null,null);
      if (userResult.getRowCount() == 0)
      {
        // Create the user
	masterDatabase.executeQuery("CREATE USER "+quoteString(userName)+" PASSWORD "+quoteString(password),null,
          null,invalidateKeys,null,false,0,null,null);
      }
      
      // Now, look for schema
      params.clear();
      params.add(databaseName.toUpperCase(Locale.ROOT));
      IResultSet schemaResult = masterDatabase.executeQuery("SELECT * FROM INFORMATION_SCHEMA.SYSTEM_SCHEMAS WHERE TABLE_SCHEM=?",params,
        null,null,null,true,-1,null,null);
      if (schemaResult.getRowCount() == 0)
      {
        // Create the schema
	masterDatabase.executeQuery("CREATE SCHEMA "+databaseName.toUpperCase(Locale.ROOT)+" AUTHORIZATION "+quoteString(userName),null,
          null,invalidateKeys,null,false,0,null,null);
      }
    }
    else
    {
      try
      {
        // Force a load of the appropriate JDBC driver
        Class.forName(_driver).newInstance();
        DriverManager.getConnection(_localUrl+databaseName,userName,password).close();
      }
      catch (Exception e)
      {
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      performModification("SET DATABASE TRANSACTION CONTROL MVCC",null,null);
      performModification("SET FILES SCALE 512",null,null);
    }
  }

  private static String quoteString(String password)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("\"");
    int i = 0;
    while (i < password.length())
    {
      char x = password.charAt(i);
      if (x == '\"')
        sb.append("\"");
      sb.append(x);
      i++;
    }
    sb.append("\"");
    return sb.toString();
  }

  /** Drop user and database.
  *@param adminUserName is the admin user name.
  *@param adminPassword is the admin password.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void dropUserAndDatabase(String adminUserName, String adminPassword, StringSet invalidateKeys)
    throws ManifoldCFException
  {
    if (isRemote)
    {
      // Drop the schema, then the user
      Database masterDatabase = new DBInterfaceHSQLDB(context,"PUBLIC",adminUserName,adminPassword);
      try
      {
        // Drop schema
        masterDatabase.executeQuery("DROP SCHEMA "+databaseName,null,null,invalidateKeys,null,false,0,null,null);
        // Drop user
        masterDatabase.executeQuery("DROP USER "+quoteString(userName),null,null,invalidateKeys,null,false,0,null,null);
      }
      catch (ManifoldCFException e)
      {
        throw reinterpretException(e);
      }
    }
    else
    {
      File f = new File(databaseName + ".properties");
      if (f.exists())
      {
        // Try to guarantee that all connections are discarded before we shut the database down.  Otherwise we get pool warnings from bitstream.
        ConnectionFactory.releaseAll();
        // Make sure database is shut down.
        closeDatabase();
        // Now, it's OK to delete
        singleDelete(f);
        singleDelete(new File(databaseName + ".data"));
        singleDelete(new File(databaseName + ".lck"));
        singleDelete(new File(databaseName + ".log"));
        singleDelete(new File(databaseName + ".script"));
        recursiveDelete(new File(databaseName + ".tmp"));
      }
    }
  }
  
  protected static void recursiveDelete(File f)
  {
    if (f.exists())
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
            singleDelete(newf);
        }
      }
      if (!f.delete())
        System.out.println("Failed to delete directory "+f.toString());
    }
  }

  protected static void singleDelete(File f)
  {
    if (f.exists() && !f.delete())
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
  public Map<String,ColumnDescription> getTableSchema(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    StringBuilder query = new StringBuilder();
    List list = new ArrayList();
    list.add(schemaNameForQueries.toUpperCase(Locale.ROOT));
    list.add(tableName.toUpperCase(Locale.ROOT));
    query.append("SELECT column_name, is_nullable, data_type, character_maximum_length ")
      .append("FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=? AND table_name=?");
    IResultSet set = performQuery(query.toString(),list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;

    query = new StringBuilder();
    query.append("SELECT column_name ")
      .append("FROM INFORMATION_SCHEMA.SYSTEM_PRIMARYKEYS WHERE table_schem=? AND table_name=?");
    IResultSet primarySet = performQuery(query.toString(),list,cacheKeys,queryClass);
    String primaryKey = null;
    if (primarySet.getRowCount() != 0)
      primaryKey = ((String)primarySet.getRow(0).getValue("column_name")).toLowerCase(Locale.ROOT);
    if (primaryKey == null)
      primaryKey = "";
    
    // Digest the result
    Map<String,ColumnDescription> rval = new HashMap<String,ColumnDescription>();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = ((String)row.getValue("column_name")).toLowerCase(Locale.ROOT);
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
  public Map<String,IndexDescription> getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    Map<String,IndexDescription> rval = new HashMap<String,IndexDescription>();

    String query = "SELECT index_name,column_name,non_unique,ordinal_position FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO "+
      "WHERE table_schem=? AND TABLE_NAME=? ORDER BY index_name,ordinal_position ASC";
    List list = new ArrayList();
    list.add(schemaNameForQueries.toUpperCase(Locale.ROOT));
    list.add(tableName.toUpperCase(Locale.ROOT));
    IResultSet result = performQuery(query,list,cacheKeys,queryClass);
    String lastIndexName = null;
    List<String> indexColumns = null;
    boolean isUnique = false;
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String indexName = ((String)row.getValue("index_name")).toLowerCase(Locale.ROOT);
      String columnName = ((String)row.getValue("column_name")).toLowerCase(Locale.ROOT);
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
      isUnique = nonUnique.equals("false");
    }
    
    if (lastIndexName != null)
      addIndex(rval,lastIndexName,isUnique,indexColumns);
    
    return rval;
  }

  protected void addIndex(Map rval, String indexName, boolean isUnique, List<String> indexColumns)
  {
    if (indexName.indexOf("sys_idx") != -1)
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
    ArrayList list = new ArrayList();
    list.add(schemaNameForQueries.toUpperCase(Locale.ROOT));
    IResultSet set = performQuery("SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_schema=?",list,cacheKeys,queryClass);
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

  /** Construct ORDER-BY clause meant for reading from an index.
  * Supply the field names belonging to the index, in order.
  * Also supply a corresponding boolean array, where TRUE means "ASC", and FALSE
  * means "DESC".
  *@param fieldNames are the names of the fields in the index that is to be used.
  *@param direction is a boolean describing the sorting order of the first term.
  *@return a query chunk, including "ORDER BY" text, which is appropriate for
  * at least ordering by the FIRST column supplied.
  */
  public String constructIndexOrderByClause(String[] fieldNames, boolean direction)
  {
    if (fieldNames.length == 0)
      return "";
    StringBuilder sb = new StringBuilder("ORDER BY ");
    for (int i = 0; i < fieldNames.length; i++)
    {
      if (i > 0)
        sb.append(", ");
      sb.append(fieldNames[i]);
      if (direction)
        sb.append(" ASC");
      else
        sb.append(" DESC");
    }
    return sb.toString();
  }
  
  /** Construct a cast to a double value.
  * On most databases this cast needs to be explicit, but on some it is implicit (and cannot be in fact
  * specified).
  *@param value is the value to be cast.
  *@return the query chunk needed.
  */
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
  public String constructCountClause(String column)
  {
    return "CAST(COUNT("+column+") AS bigint)";
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
      if (afterOrderBy)
        // Hint to HSQLDB to use the order-by index
        sb.append(" USING INDEX");
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
    // For HSQLDB, we want to generate the following:
    // WITH ct01 ( ... otherfields ... ) AS ( ... baseQuery ... )
    //   SELECT * FROM (SELECT DISTINCT ... distinctFields ... FROM ct01) AS ct02,
    //   LATERAL ( SELECT ... otherfields ... FROM ct01 WHERE ... distinctFields = ct02.distinctField ... ORDER BY ... order by ... LIMIT 1) AS ct03
    //
    
    // Copy arguments
    if (baseParameters != null)
      outputParameters.addAll(baseParameters);

    StringBuilder sb = new StringBuilder("WITH txxx1 (");
    boolean needComma = false;
    Iterator<String> iter = otherFields.keySet().iterator();
    while (iter.hasNext())
    {
      String fieldName = iter.next();
      if (needComma)
        sb.append(",");
      sb.append(fieldName);
      needComma = true;
    }
    sb.append(") AS (SELECT ");
    needComma = false;
    iter = otherFields.keySet().iterator();
    while (iter.hasNext())
    {
      String fieldName = iter.next();
      String columnValue = otherFields.get(fieldName);
      if (needComma)
        sb.append(",");
      needComma = true;
      sb.append("txxx2.").append(columnValue).append(" AS ").append(fieldName);
    }
    sb.append(" FROM (").append(baseQuery).append(") txxx2)");
    sb.append(" SELECT * FROM (SELECT DISTINCT ");
    Map<String,String> distinctMap = new HashMap<String,String>();
    int i = 0;
    while (i < distinctFields.length)
    {
      String distinctField = distinctFields[i];
      if (i > 0)
        sb.append(",");
      sb.append(distinctField);
      distinctMap.put(distinctField,distinctField);
      i++;
    }
    sb.append(" FROM txxx1) AS txxx3, LATERAL (SELECT ");
    iter = otherFields.keySet().iterator();
    needComma = false;
    while (iter.hasNext())
    {
      String fieldName = iter.next();
      if (distinctMap.get(fieldName) == null)
      {
        if (needComma)
          sb.append(",");
        needComma = true;
        sb.append(fieldName);
      }
    }
    sb.append(" FROM txxx1 WHERE ");
    i = 0;
    while (i < distinctFields.length)
    {
      String distinctField = distinctFields[i];
      if (i > 0)
        sb.append(" AND ");
      sb.append(distinctField).append("=txxx3.").append(distinctField);
      i++;
    }
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
    sb.append(" LIMIT 1) AS txxx4");
    return sb.toString();
  }

  /* Calculate the number of values a particular clause can have, given the values for all the other clauses.
  * For example, if in the expression x AND y AND z, x has 2 values and z has 1, find out how many values x can legally have
  * when using the buildConjunctionClause() method below.
  */
  @Override
  public int findConjunctionClauseMax(ClauseDescription[] otherClauseDescriptions)
  {
    // Special handling when there's only 1
    if (otherClauseDescriptions.length == 0)
      return super.findConjunctionClauseMax(otherClauseDescriptions);

    // Since it's an OR clause we have to generate, figure out how many clauses are generated by the others,
    // and work back from there.
    int number = 1;
    for (int i = 0 ; i < otherClauseDescriptions.length ; i++)
    {
      ClauseDescription otherClause = otherClauseDescriptions[i];
      List values = otherClause.getValues();
      if (values != null)
        number *= values.size();
    }
    int rval = getMaxOrClause() / number;
    if (rval == 0)
      rval = 1;
    return rval;
  }
  
  /* Construct a conjunction clause, e.g. x AND y AND z, where there is expected to be an index (x,y,z,...), and where x, y, or z
  * can have multiple distinct values, The proper implementation of this method differs from database to database, because some databases
  * only permit index operations when there are OR's between clauses, such as x1 AND y1 AND z1 OR x2 AND y2 AND z2 ..., where others
  * only recognize index operations when there are lists specified for each, such as x IN (x1,x2) AND y IN (y1,y2) AND z IN (z1,z2).
  */
  @Override
  public String buildConjunctionClause(List outputParameters, ClauseDescription[] clauseDescriptions)
  {
    // Special handling when there's only 1
    if (clauseDescriptions.length == 1)
      return super.buildConjunctionClause(outputParameters,clauseDescriptions);
    
    StringBuilder sb = new StringBuilder("(");
    int[] counters = new int[clauseDescriptions.length];
    for (int i = 0 ; i < counters.length ; i++)
    {
      counters[i] = 0;
    }
    
    boolean isFirst = true;
    while (true)
    {
      // Add this clause in
      if (isFirst)
        isFirst = false;
      else
        sb.append(" OR ");
      for (int i = 0 ; i < counters.length ; i++)
      {
        ClauseDescription cd = clauseDescriptions[i];
        if (i > 0)
          sb.append(" AND ");
        List values = cd.getValues();
        String joinColumn = cd.getJoinColumnName();
        sb.append(cd.getColumnName()).append(cd.getOperation());
        if (values != null)
        {
          sb.append("?");
          outputParameters.add(values.get(counters[i]));
        }
        else if (joinColumn != null)
          sb.append(joinColumn);
      }
    
      // Now, increment the counters
      int j = 0;
      while (true)
      {
        if (j == counters.length)
        {
          sb.append(")");
          return sb.toString();
        }
        counters[j]++;
        ClauseDescription cd = clauseDescriptions[j];
        List values = cd.getValues();
        int size = 1;
        if (values != null)
          size = values.size();
        if (counters[j] < size)
          break;
        j++;
        for (int k = 0 ; k < j ; k++)
        {
          counters[k] = 0;
        }
        // Loop around to carry a one to the j'th counter
      }
    }
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
    return 1000;
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
        connection.getConnection().setAutoCommit(false);
        connection.getConnection().setTransactionIsolation(desiredTransactionType);
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
            connection.getConnection().commit();
            connection.getConnection().setAutoCommit(true);
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
            connection.getConnection().rollback();
            connection.getConnection().setAutoCommit(true);
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

  /** Abstract method for explaining a query */
  protected void explainQuery(String query, List params)
    throws ManifoldCFException
  {
    IResultSet x = executeUncachedQuery("EXPLAIN PLAN FOR "+query,null,true,
      -1,null,null);
    int k = 0;
    while (k < x.getRowCount())
    {
      IResultRow row = x.getRow(k++);
      Iterator<String> iter = row.getColumns();
      String colName = (String)iter.next();
      Logging.db.warn(" Plan: "+row.getValue(colName).toString());
    }
    Logging.db.warn("");
  }

  /** Abstract method for mapping a column name from resultset */
  @Override
  protected String mapLabelName(String rawLabelName)
  {
    return rawLabelName.toLowerCase(Locale.ROOT);
  }

}

