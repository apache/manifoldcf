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
import org.apache.lcf.core.system.LCF;
import org.apache.lcf.core.system.Logging;
import java.util.*;
import java.io.*;

public class DBInterfaceDerby extends Database implements IDBInterface
{
  public static final String _rcsid = "@(#)$Id$";

  protected final static String _url = "jdbc:derby:";
  protected final static String _driver = "org.apache.derby.jdbc.EmbeddedDriver";
  
  public final static String databasePathProperty = "org.apache.lcf.derbydatabasepath";
  
  protected String userName;
  protected String password;
  
  protected String cacheKey;
  // Postgresql serializable transactions are broken in that transactions that occur within them do not in fact work properly.
  // So, once we enter the serializable realm, STOP any additional transactions from doing anything at all.
  protected int serializableDepth = 0;

  protected static String getFullDatabasePath(String databaseName)
    throws LCFException
  {
    String path = LCF.getProperty(databasePathProperty);
    if (path == null)
      throw new LCFException("Derby database requires '"+databasePathProperty+"' property, containing a full path");
    path = path.replace("\\","/");
    if (!path.endsWith("/"))
      path = path + "/";
    return path + databaseName;
  }
  
  public DBInterfaceDerby(IThreadContext tc, String databaseName, String userName, String password)
    throws LCFException
  {
    super(tc,_url+getFullDatabasePath((databaseName==null)?"default":databaseName)+";create=true;user="+userName+";password="+password,_driver,getFullDatabasePath((databaseName==null)?"default":databaseName),userName,password);
    cacheKey = CacheKeyFactory.makeDatabaseKey(this.databaseName);
    this.userName = userName;
    this.password = password;
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
    int constraintNumber = 0;
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

  protected void appendDescription(StringBuffer queryBuffer, String columnName, ColumnDescription cd, boolean forceNull)
    throws LCFException
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
  public void performAlter(String tableName, Map columnMap, Map columnModifyMap, ArrayList columnDeleteList,
    StringSet invalidateKeys)
    throws LCFException
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
          performModification("ALTER TABLE ONLY "+tableName+" DROP "+columnName,null,invalidateKeys);
        }
      }

      // Do the modifies.  This involves renaming each column to a temp column, then creating a new one, then copying
      if (columnModifyMap != null)
      {
        Iterator iter = columnModifyMap.keySet().iterator();
        while (iter.hasNext())
        {
          String columnName = (String)iter.next();
          ColumnDescription cd = (ColumnDescription)columnModifyMap.get(columnName);
          String renameColumn = "__temp__";
          // Create a new column we can copy the data into.
          performModification("RENAME COLUMN "+tableName+"."+columnName+" TO "+renameColumn,null,invalidateKeys);
          // Create new column
          StringBuffer sb = new StringBuffer();
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
    // Does nothing on Derby
  }

  /** Reindex a table.
  *@param tableName is the name of the table to rebuild indexes for.
  */
  public void reindexTable(String tableName)
    throws LCFException
  {
    // Does nothing on Derby
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

  /** Create user and database.
  *@param userName is the user name.
  *@param password is the user's desired password.
  *@param databaseName is the database name.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void createUserAndDatabase(String userName, String password, String databaseName,
    StringSet invalidateKeys)
    throws LCFException
  {
    if (lookupUser(userName,null,null) == false)
    {
      performCreateUser(userName,password,invalidateKeys);
    }

    if (lookupDatabase(databaseName,null,null) == false)
    {
      performCreateDatabase(databaseName,userName,password,invalidateKeys);
    }

  }

  /** Drop user and database.
  *@param userName is the user name.
  *@param databaseName is the database name.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  public void dropUserAndDatabase(String userName, String databaseName, StringSet invalidateKeys)
    throws LCFException
  {
    if (lookupDatabase(databaseName,null,null))
    {
      performDropDatabase(databaseName,invalidateKeys);
    }
  }

  /** Perform user lookup.
  *@param userName is the user name to lookup.
  *@return true if the user exists.
  */
  protected boolean lookupUser(String userName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    Database rootDatabase = new Database(context,_url+databaseName+";create=true",_driver,databaseName,"","");
    IResultSet set = rootDatabase.executeQuery("VALUES SYSCS_UTIL.SYSCS_GET_DATABASE_PROPERTY('derby.user."+userName+"')",null,cacheKeys,null,queryClass,true,-1,null,null);
    if (set.getRowCount() == 0)
      return false;
    return true;
  }

  /** Perform user create.
  *@param userName is the user name.
  *@param password is the user's password.
  */
  protected void performCreateUser(String userName, String password, StringSet invalidateKeys)
    throws LCFException
  {
    Database rootDatabase = new Database(context,_url+databaseName+";create=true",_driver,databaseName,"","");
    rootDatabase.executeQuery("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user."+userName+"', '"+password+"')",null,invalidateKeys,null,null,false,0,null,null);
    rootDatabase.executeQuery("CREATE SCHEMA "+userName+" AUTHORIZATION "+userName,null,invalidateKeys,null,null,false,0,null,null);
  }

  /** Perform user delete.
  *@param userName is the user name.
  */
  public void performDropUser(String userName, StringSet invalidateKeys)
    throws LCFException
  {
    Database rootDatabase = new Database(context,_url+databaseName+";create=true",_driver,databaseName,"","");
    rootDatabase.executeQuery("DROP SCHEMA "+userName+" RESTRICT",null,invalidateKeys,null,null,false,0,null,null);
    rootDatabase.executeQuery("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user."+userName+"', null)",null,invalidateKeys,null,null,false,0,null,null);
  }

  /** Perform database lookup.
  *@param databaseName is the database name.
  *@param cacheKeys are the cache keys, if any.
  *@return true if the database exists.
  */
  protected boolean lookupDatabase(String databaseName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    File f = new File(databaseName);
    return f.exists();
  }

  /** Perform database create.
  *@param databaseName is the database name.
  *@param databaseUser is the user to grant access to the database.
  *@param databasePassword is the password of the user to grant access to the database.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  protected void performCreateDatabase(String databaseName, String databaseUser, String databasePassword,
    StringSet invalidateKeys)
    throws LCFException
  {
    // Do nothing here; we create the database automatically whenever we go try to do stuff.
    //DatabaseFactory.make(context,_url+databaseName+";create=true",_driver,
    //  database.getDatabaseName(),userName,password);
  }

  /** Perform database drop.
  *@param databaseName is the database name.
  *@param invalidateKeys are the cache keys that should be invalidated, if any.
  */
  protected void performDropDatabase(String databaseName, StringSet invalidateKeys)
    throws LCFException
  {
    // rm -rf <databasename>
    File f = new File(databaseName);
    recursiveDelete(f);
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
    f.delete();
  }
  
  /** Reinterpret an exception tossed by the database layer.  We need to disambiguate the various kinds of exception that
  * should be thrown.
  *@param theException is the exception to reinterpret
  *@return the reinterpreted exception to throw.
  */
  protected LCFException reinterpretException(LCFException theException)
  {
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Reinterpreting exception '"+theException.getMessage()+"'.  The exception type is "+Integer.toString(theException.getErrorCode()));
    if (theException.getErrorCode() != LCFException.DATABASE_CONNECTION_ERROR)
      return theException;
    Throwable e = theException.getCause();
    if (!(e instanceof java.sql.SQLException))
      return theException;
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Exception "+theException.getMessage()+" is possibly a transaction abort signal");
    String message = e.getMessage();
    if (message.indexOf("due to a deadlock") != -1)
      return new LCFException(message,e,LCFException.DATABASE_TRANSACTION_ABORT);
    // Note well: We also have to treat 'duplicate key' as a transaction abort, since this is what you get when two threads attempt to
    // insert the same row.  (Everything only works, then, as long as there is a unique constraint corresponding to every bad insert that
    // one could make.)
    if (message.indexOf("duplicate key") != -1)
      return new LCFException(message,e,LCFException.DATABASE_TRANSACTION_ABORT);
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
    throws LCFException
  {
    try
    {
      executeQuery(query,params,null,invalidateKeys,null,false,0,null,null);
    }
    catch (LCFException e)
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
    throws LCFException
  {
    String query = "SELECT CAST(t0.columnname AS VARCHAR(128)) AS columnname,CAST(t0.columndatatype AS VARCHAR(128)) AS columndatatype FROM sys.syscolumns t0, sys.systables t1 WHERE t0.referenceid=t1.tableid AND CAST(t1.tablename AS VARCHAR(128))=? ORDER BY t0.columnnumber ASC";
    ArrayList list = new ArrayList();
    list.add(tableName.toUpperCase());

    IResultSet set = performQuery(query,list,cacheKeys,queryClass);
    if (set.getRowCount() == 0)
      return null;
    // Digest the result
    HashMap rval = new HashMap();
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String fieldName = (String)row.getValue("columnname");
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
  public Map getTableIndexes(String tableName, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    Map rval = new HashMap();

    // This query returns all index names for the table
    String query = "SELECT t0.conglomeratename FROM sys.sysconglomerates t0,sys.systables t1 WHERE t0.tableid=t1.tableid AND t0.isindex IS NOT NULL AND CAST(t1.tablename AS VARCHAR(128))=?";
    ArrayList list = new ArrayList();
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
    throws LCFException
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
  public IResultSet performQuery(String query, ArrayList params, StringSet cacheKeys, String queryClass)
    throws LCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,-1,null,null);
    }
    catch (LCFException e)
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
    throws LCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,null,returnLimit);
    }
    catch (LCFException e)
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
    throws LCFException
  {
    try
    {
      return executeQuery(query,params,cacheKeys,null,queryClass,true,maxResults,resultSpec,returnLimit);
    }
    catch (LCFException e)
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
    // MHL to invoke a stored procedure
    return column + " LIKE " + regularExpression;
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
      sb.append("OFFSET ").append(Integer.toString(offset)).append(" ROWS");
    if (limit != -1)
    {
      if (offset != 0)
        sb.append(" ");
      sb.append("FETCH NEXT ").append(Integer.toString(limit)).append(" ROWS ONLY");
    }
    return sb.toString();
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
    throws LCFException
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
        executeViaThread(connection,"SET ISOLATION READ COMMITTED",null,false,0,null,null);
      }
      catch (Error e)
      {
        super.signalRollback();
        super.endTransaction();
        throw e;
      }
      catch (LCFException e)
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
        executeViaThread(connection,"SET ISOLATION SERIALIZABLE",null,false,0,null,null);
      }
      catch (Error e)
      {
        super.signalRollback();
        super.endTransaction();
        throw e;
      }
      catch (LCFException e)
      {
        super.signalRollback();
        super.endTransaction();
        throw e;
      }
      super.beginTransaction(TRANSACTION_SERIALIZED);
      break;
    default:
      throw new LCFException("Bad transaction type");
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
    throws LCFException
  {
    if (serializableDepth > 0)
    {
      serializableDepth--;
      return;
    }

    super.endTransaction();
  }

  int depthCount = 0;
  boolean inTransaction = false;
  
  /** Abstract method to start a transaction */
  protected void startATransaction()
    throws LCFException
  {
    if (!inTransaction)
    {
      try
      {
        connection.setAutoCommit(false);
      }
      catch (java.sql.SQLException e)
      {
        throw new LCFException(e.getMessage(),e);
      }
      inTransaction = true;
    }
    depthCount++;
  }

  /** Abstract method to commit a transaction */
  protected void commitCurrentTransaction()
    throws LCFException
  {
    if (inTransaction)
    {
      depthCount--;
      if (depthCount == 0)
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
          throw new LCFException(e.getMessage(),e);
        }
        inTransaction = false;
      }
    }
    else
      throw new LCFException("Transaction nesting error!");
  }
  
  /** Abstract method to roll back a transaction */
  protected void rollbackCurrentTransaction()
    throws LCFException
  {
    if (inTransaction)
    {
      depthCount--;
      if (depthCount == 0)
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
          throw new LCFException(e.getMessage(),e);
        }
        inTransaction = false;
      }
    }
    else
      throw new LCFException("Transaction nesting error!");
  }

  /** Abstract method for mapping a column name from resultset */
  protected String mapColumnName(String rawColumnName)
  {
    return rawColumnName.toLowerCase();
  }

}

