/* $Id: DBInterfaceDerby.java 1001023 2010-09-24 18:41:28Z kwright $ */

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
import java.util.regex.*;
import java.io.*;
import java.sql.*;

public class DBInterfaceDerby extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id: DBInterfaceDerby.java 1001023 2010-09-24 18:41:28Z kwright $";

  protected final static String _url = "jdbc:derby:";
  protected final static String _driver = "org.apache.derby.jdbc.EmbeddedDriver";
  
  public final static String databasePathProperty = "org.apache.manifoldcf.derbydatabasepath";
  
  /** A lock manager handle. */
  protected ILockManager lockManager;

  // Credentials
  protected String userName;
  protected String password;
  
  // Database cache key
  protected String cacheKey;
  
  // Once we enter the serializable realm, STOP any additional transactions from doing anything at all.
  protected int serializableDepth = 0;

  // Internal transaction depth, and flag whether we're in a transaction or not
  int depthCount = 0;
  boolean inTransaction = false;
  
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
  

  // Override the Derby default lock timeout, and make it wait indefinitely instead.
  static
  {
    System.setProperty("derby.locks.waitTimeout","-1");
    // Detect deadlocks immediately
    System.setProperty("derby.locks.deadlockTimeout","0");
  }
  
  protected static String getFullDatabasePath(String databaseName)
    throws ManifoldCFException
  {
    // Derby is local file based so it cannot currently be used in zookeeper mode
    File path = ManifoldCF.getFileProperty(databasePathProperty);
    if (path == null)
      throw new ManifoldCFException("Derby database requires '"+databasePathProperty+"' property, containing a relative path");
    String pathString = path.toString().replace("\\\\","/");
    if (!pathString.endsWith("/"))
      pathString = pathString + "/";
    return pathString + databaseName;
  }
  
  public DBInterfaceDerby(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,_url+getFullDatabasePath(databaseName)+";user="+userName+";password="+password,_driver,getFullDatabasePath(databaseName),userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    lockManager = LockManagerFactory.make(tc);
    this.userName = userName;
    this.password = password;
  }

  public DBInterfaceDerby(IThreadContext tc, String databaseName)
    throws ManifoldCFException
  {
    super(tc,_url+databaseName,_driver,databaseName,"","");
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    lockManager = LockManagerFactory.make(tc);
    this.userName = "";
    this.password = "";
  }
  
  /** Initialize.  This method is called once per JVM instance, in order to set up
  * database communication.
  */
  public void openDatabase()
    throws ManifoldCFException
  {
    // Does nothing
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
      DriverManager.getConnection(_url+databaseName+";shutdown=true;user="+userName+";password="+password,userName,password).close();
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
    int constraintNumber = 0;
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

  protected void appendDescription(StringBuilder queryBuffer, String columnName, ColumnDescription cd, boolean forceNull)
    throws ManifoldCFException
  {
    queryBuffer.append(columnName);
    queryBuffer.append(' ');
    queryBuffer.append(mapType(cd.getTypeString()));
    if (forceNull || cd.getIsNull())
    {
      //queryBuffer.append(" NULL");
    }
    else
      queryBuffer.append(" NOT NULL");
    if (cd.getIsPrimaryKey())
      queryBuffer.append(" CONSTRAINT c" + IDFactory.make(context) + " PRIMARY KEY");
    if (cd.getReferenceTable() != null)
    {
      queryBuffer.append(" CONSTRAINT c" + IDFactory.make(context) + " REFERENCES ");
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
          // Create a new column we can copy the data into.
          performModification("RENAME COLUMN "+tableName+"."+columnName+" TO "+renameColumn,null,invalidateKeys);
          // Create new column
          StringBuilder sb = new StringBuilder();
          appendDescription(sb,columnName,cd,true);
          performModification("ALTER TABLE "+tableName+" ADD "+sb.toString(),null,invalidateKeys);
          // Copy old data to new
          performModification("UPDATE "+tableName+" SET "+columnName+"="+renameColumn,null,invalidateKeys);
          // Make the column null, if it needs it
          if (cd.getIsNull() == false)
            performModification("ALTER TABLE "+tableName+" ALTER "+columnName+" SET NOT NULL",null,invalidateKeys);
          // Drop old column
          performModification("ALTER TABLE "+tableName+" DROP "+renameColumn,null,invalidateKeys);
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


  /** Map a standard type into a derby type.
  *@param inputType is the input type.
  *@return the output type.
  */
  protected static String mapType(String inputType)
  {
    if (inputType.equalsIgnoreCase("longtext"))
      return "CLOB";
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
    {
      List list = new ArrayList();
      list.add("APP");
      list.add(tableName.toUpperCase(Locale.ROOT));
      performModification("CALL SYSCS_UTIL.SYSCS_UPDATE_STATISTICS(?,?,null)",list,null);
    }
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
          // To reindex, we (a) get all the table's indexes, (b) drop them, (c) recreate them
          Map<String,IndexDescription> x = getTableIndexes(tableName,null,null);
          Iterator<String> iter = x.keySet().iterator();
          while (iter.hasNext())
          {
            String indexName = iter.next();
            IndexDescription id = x.get(indexName);
            performRemoveIndex(indexName,tableName);
            performAddIndex(indexName,tableName,id);
          }
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
    try
    {
      // Force a load of the appropriate JDBC driver
      Class.forName(_driver).newInstance();
      DriverManager.getConnection(_url+databaseName+";create=true;user="+userName+";password="+password,userName,password).close();
    }
    catch (Exception e)
    {
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }

    Database rootDatabase = new DBInterfaceDerby(context,databaseName);
    IResultSet set = rootDatabase.executeQuery("VALUES SYSCS_UTIL.SYSCS_GET_DATABASE_PROPERTY('derby.user."+userName+"')",null,null,null,null,true,-1,null,null);
    if (set.getRowCount() == 0)
    {
      rootDatabase.executeQuery("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user."+userName+"', '"+password+"')",null,invalidateKeys,null,null,false,0,null,null);
      rootDatabase.executeQuery("CREATE SCHEMA "+userName+" AUTHORIZATION "+userName,null,invalidateKeys,null,null,false,0,null,null);
    }
    try
    {
      rootDatabase.executeQuery("DROP FUNCTION CASEINSENSITIVEREGULAREXPRESSIONCOMPARE",null,invalidateKeys,null,null,false,0,null,null);
      rootDatabase.executeQuery("DROP FUNCTION CASESENSITIVEREGULAREXPRESSIONCOMPARE",null,invalidateKeys,null,null,false,0,null,null);
      rootDatabase.executeQuery("DROP FUNCTION CASEINSENSITIVESUBSTRING",null,invalidateKeys,null,null,false,0,null,null);
      rootDatabase.executeQuery("DROP FUNCTION CASESENSITIVESUBSTRING",null,invalidateKeys,null,null,false,0,null,null);
    }
    catch (ManifoldCFException e)
    {
      if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        throw e;
      // Otherwise, eat it.
    }
    // Create user-defined functions
    rootDatabase.executeQuery("CREATE FUNCTION CASEINSENSITIVEREGULAREXPRESSIONCOMPARE (value varchar(255), regexp varchar(255)) returns varchar(255) "+
      "language java parameter style java no sql external name 'org.apache.manifoldcf.core.database.DBInterfaceDerby.caseInsensitiveRegularExpressionCompare'",null,invalidateKeys,null,null,false,0,null,null);
    rootDatabase.executeQuery("CREATE FUNCTION CASESENSITIVEREGULAREXPRESSIONCOMPARE (value varchar(255), regexp varchar(255)) returns varchar(255) "+
      "language java parameter style java no sql external name 'org.apache.manifoldcf.core.database.DBInterfaceDerby.caseSensitiveRegularExpressionCompare'",null,invalidateKeys,null,null,false,0,null,null);
    rootDatabase.executeQuery("CREATE FUNCTION CASEINSENSITIVESUBSTRING (value varchar(255), regexp varchar(255)) returns varchar(255) "+
      "language java parameter style java no sql external name 'org.apache.manifoldcf.core.database.DBInterfaceDerby.caseInsensitiveSubstring'",null,invalidateKeys,null,null,false,0,null,null);
    rootDatabase.executeQuery("CREATE FUNCTION CASESENSITIVESUBSTRING (value varchar(255), regexp varchar(255)) returns varchar(255) "+
      "language java parameter style java no sql external name 'org.apache.manifoldcf.core.database.DBInterfaceDerby.caseSensitiveSubstring'",null,invalidateKeys,null,null,false,0,null,null);
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
          singleDelete(newf);
      }
    }
    if (!f.delete())
      System.out.println("Failed to delete directory "+f.toString());
  }
  
  protected static void singleDelete(File f)
  {
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
    // Note well: We also have to treat 'duplicate key' as a transaction abort, since this is what you get when two threads attempt to
    // insert the same row.  (Everything only works, then, as long as there is a unique constraint corresponding to every bad insert that
    // one could make.)
    if (sqlState != null && sqlState.equals("23505"))
      return new ManifoldCFException(message,e,ManifoldCFException.DATABASE_TRANSACTION_ABORT);
    // Deadlock also aborts.
    if (sqlState != null && sqlState.equals("40001"))
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
    while (true)
    {
      try
      {
        try
        {
          executeQuery(query,params,null,invalidateKeys,null,false,0,null,null);
          return;
        }
        catch (ManifoldCFException e)
        {
          throw reinterpretException(e);
        }
      }
      catch (ManifoldCFException e)
      {
        // If this is a transaction abort, and we are NOT in a transaction, then repeat the request after
        // a delay.  This is because Derby is perfectly capable of deadlocking itself even without any involved transactions, but
        // ManifoldCF doesn't expect deadlocks to arise in any way other than through a transaction.  See CONNECTORS-111.
        if (e.getErrorCode() != ManifoldCFException.DATABASE_TRANSACTION_ABORT)
          throw e;
        // Check if we are in a transaction
        if (inTransaction)
          throw e;
        // Unique key constraints generate transaction abort signals only when they are in a transaction, so we are safe in
        // assuming the exception is the result of internal deadlock alone.
        // Wait a short time.
        try
        {
          ManifoldCF.sleep(1000L);
        }
        catch (InterruptedException e2)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
      }
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
    String query = "SELECT CAST(t0.columnname AS VARCHAR(128)) AS columnname,CAST(t0.columndatatype AS VARCHAR(128)) AS columndatatype FROM sys.syscolumns t0, sys.systables t1 WHERE t0.referenceid=t1.tableid AND CAST(t1.tablename AS VARCHAR(128))=? ORDER BY t0.columnnumber ASC";
    List list = new ArrayList();
    list.add(tableName.toUpperCase(Locale.ROOT));

    IResultSet set = performQuery(query,list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;
    // Digest the result
    Map<String,ColumnDescription> rval = new HashMap<String,ColumnDescription>();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = ((String)row.getValue("columnname")).toLowerCase(Locale.ROOT);
      String type = (String)row.getValue("columndatatype");
      boolean isNull = false;
      boolean isPrimaryKey = false;
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

    // This query returns all index names for the table
    String query = "SELECT t0.conglomeratename FROM sys.sysconglomerates t0,sys.systables t1 WHERE t0.tableid=t1.tableid AND t0.isindex IS NOT NULL AND CAST(t1.tablename AS VARCHAR(128))=?";
    List list = new ArrayList();
    list.add(tableName);
    
    // It doesn't look like there's a way to find exactly what is in the index, and what the columns are.  Since
    // the goal of Derby is to build tests, and this method is used primarily on installation, we can probably accept
    // the poor performance implied in tearing an index down and recreating it unnecessarily, so I'm going to do a fake-out.
    
    IResultSet result = performQuery(query,list,cacheKeys,queryClass);
    int i = 0;
    while (i < result.getRowCount())
    {
      IResultRow row = result.getRow(i++);
      String indexName = (String)row.getValue("conglomeratename");

      rval.put(indexName,new IndexDescription(false,new String[0]));
    }

    return rval;
  }

  /** Get a database's tables.
  *@param cacheKeys are the cache keys for the query, or null.
  *@param queryClass is the name of the query class, or null.
  *@return the set of tables.
  */
  public StringSet getAllTables(StringSet cacheKeys, String queryClass)
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT CAST(tablename AS VARCHAR(128)) FROM sys.systables WHERE table_type='T'",null,cacheKeys,queryClass);
    StringSetBuffer ssb = new StringSetBuffer();
    String columnName = "tablename";

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
    //return column + " LIKE " + regularExpression;
    // Waiting for DERBY-4066 to be resolved in a release for the following:
    //if (caseInsensitive)
    //  return "caseInsensitiveRegularExpressionCompare("+column+","+regularExpression+")='true'";
    //else
    //  return "caseSensitiveRegularExpressionCompare("+column+","+regularExpression+")='true'";
    if (caseInsensitive)
      return "CASEINSENSITIVEREGULAREXPRESSIONCOMPARE(CAST("+column+" AS VARCHAR(255)),"+regularExpression+")='true'";
    else
      return "CASESENSITIVEREGULAREXPRESSIONCOMPARE(CAST("+column+" AS VARCHAR(255)),"+regularExpression+")='true'";

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
    //return "''";
    // Waiting for DERBY-4066 to be resolved in a release for the following:
    //if (caseInsensitive)
    //  return "caseInsensitiveSubstring("+column+","+regularExpression+")";
    //else
    //  return "caseSensitiveSubstring("+column+","+regularExpression+")";
    if (caseInsensitive)
      return "CASEINSENSITIVESUBSTRING(CAST("+column+" AS VARCHAR(255)),"+regularExpression+")";
    else
      return "CASESENSITIVESUBSTRING(CAST("+column+" AS VARCHAR(255)),"+regularExpression+")";
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
      sb.append("OFFSET ").append(Integer.toString(offset)).append(" ROWS");
    if (limit != -1)
    {
      if (offset != 0)
        sb.append(" ");
      sb.append("FETCH NEXT ").append(Integer.toString(limit)).append(" ROWS ONLY");
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
    // Derby does not really support this functionality.
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
    return 1;
  }

  /** For windowed report queries, e.g. maxActivity or maxBandwidth, obtain the maximum number of rows
  * that can reasonably be expected to complete in an acceptable time.
  *@return the maximum number of rows.
  */
  public int getWindowedReportMaxRows()
  {
    return 5000;
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
        reindexThreshold = lockManager.getSharedConfiguration().getIntProperty("org.apache.manifoldcf.db.derby.reindex."+tableName,250000);
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
        analyzeThreshold = lockManager.getSharedConfiguration().getIntProperty("org.apache.manifoldcf.db.derby.analyze."+tableName,5000);
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
      transactionType = super.getCurrentTransactionType();
    }

    switch (transactionType)
    {
    case TRANSACTION_READCOMMITTED:
      try
      {
        executeViaThread((connection==null)?null:connection.getConnection(),"SET ISOLATION READ COMMITTED",null,false,0,null,null);
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
      super.beginTransaction(TRANSACTION_READCOMMITTED);
      break;
    case TRANSACTION_SERIALIZED:
      try
      {
        executeViaThread((connection==null)?null:connection.getConnection(),"SET ISOLATION SERIALIZABLE",null,false,0,null,null);
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
      super.beginTransaction(TRANSACTION_SERIALIZED);
      break;
    default:
      throw new ManifoldCFException("Bad transaction type");
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
  protected void startATransaction()
    throws ManifoldCFException
  {
    if (!inTransaction)
    {
      try
      {
        connection.getConnection().setAutoCommit(false);
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

  /** Abstract method for mapping a column name from resultset */
  @Override
  protected String mapLabelName(String rawLabelName)
  {
    return rawLabelName.toLowerCase(Locale.ROOT);
  }

  // Functions that correspond to user-defined functions in Derby
  
  /** Method to compare a value using a case-insensitive regular expression.
  */
  public static String caseInsensitiveRegularExpressionCompare(String value, String regularExpression)
    throws SQLException
  {
    try
    {
      Pattern p = Pattern.compile(regularExpression,Pattern.CASE_INSENSITIVE);
      Matcher m = p.matcher((value==null)?"":value);
      if (m.find())
        return "true";
      else
        return "false";
    }
    catch (PatternSyntaxException e)
    {
      throw new SQLException("Pattern syntax exception: "+e.getMessage());
    }
  }
  
  /** Method to compare a value using a case-sensitive regular expression.
  */
  public static String caseSensitiveRegularExpressionCompare(String value, String regularExpression)
    throws SQLException
  {
    try
    {
      Pattern p = Pattern.compile(regularExpression,0);
      Matcher m = p.matcher((value==null)?"":value);
      if (m.find())
        return "true";
      else
        return "false";
    }
    catch (PatternSyntaxException e)
    {
      throw new SQLException("Pattern syntax exception: "+e.getMessage());
    }
  }

  /** Method to get a substring out of a case-insensitive regular expression group.
  */
  public static String caseInsensitiveSubstring(String value, String regularExpression)
    throws SQLException
  {
    try
    {
      Pattern p = Pattern.compile(regularExpression,Pattern.CASE_INSENSITIVE);
      Matcher m = p.matcher((value==null)?"":value);
      if (m.find())
        return m.group(1);
      return "";
    }
    catch (IndexOutOfBoundsException e)
    {
      return value;
    }
    catch (PatternSyntaxException e)
    {
      throw new SQLException("Pattern syntax exception: "+e.getMessage());
    }
  }
  
  /** Method to get a substring out of a case-sensitive regular expression group.
  */
  public static String caseSensitiveSubstring(String value, String regularExpression)
    throws SQLException
  {
    try
    {
      Pattern p = Pattern.compile(regularExpression,0);
      Matcher m = p.matcher((value==null)?"":value);
      if (m.find())
        return m.group(1);
      return "";
    }
    catch (IndexOutOfBoundsException e)
    {
      return value;
    }
    catch (PatternSyntaxException e)
    {
      throw new SQLException("Pattern syntax exception: "+e.getMessage());
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

