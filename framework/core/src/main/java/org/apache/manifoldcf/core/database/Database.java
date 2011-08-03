/* $Id: Database.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.util.*;
import java.sql.*;
import javax.naming.*;
import javax.sql.*;

/** This class implements jskw.interfaces.IDatabase, and provides basic cached database services.
* The actual cache keys are determined by layers above this.
* It is expected that there is ONE of these objects per thread per database!  If there are more, then
* the transaction management will get screwed up (i.e. nobody will know what happened to the connection
* handles...)
*/
public class Database
{
  public static final String _rcsid = "@(#)$Id: Database.java 988245 2010-08-23 18:39:35Z kwright $";

  protected ICacheManager cacheManager;
  protected IThreadContext context;
  protected String jdbcUrl;
  protected String jdbcDriverClass;
  protected String databaseName;
  protected String userName;
  protected String password;
  protected TransactionHandle th = null;
  protected Connection connection = null;
  protected boolean doRollback = false;
  protected int delayedTransactionDepth = 0;

  protected final static String _TRANSACTION_ = "_TRANSACTION_";

  public Database(IThreadContext context, String jdbcUrl, String jdbcDriverClass, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    this.context = context;
    this.jdbcUrl = jdbcUrl;
    this.jdbcDriverClass = jdbcDriverClass;
    this.databaseName = databaseName;
    this.userName = userName;
    this.password = password;
    this.cacheManager = CacheManagerFactory.make(context);
  }

  /** Get the database name.  This is often used as a cache key qualifier.
  *@return the database name.
  */
  public String getDatabaseName()
  {
    return databaseName;
  }

  /** Get the current transaction id.
  *@return the current transaction identifier, or null if no transaction.
  */
  public String getTransactionID()
  {
    if (th == null)
      return null;
    return th.getTransactionID();
  }

  /** Abstract method to start a transaction */
  protected void startATransaction()
    throws ManifoldCFException
  {
  }

  /** Abstract method to commit a transaction */
  protected void commitCurrentTransaction()
    throws ManifoldCFException
  {
  }
  
  /** Abstract method to roll back a transaction */
  protected void rollbackCurrentTransaction()
    throws ManifoldCFException
  {
  }
  
  /** Abstract method for explaining a query */
  protected void explainQuery(String query, List params)
    throws ManifoldCFException
  {
  }
  
  /** Abstract method for mapping a column name from resultset */
  protected String mapColumnName(String rawColumnName)
  {
    return rawColumnName;
  }
  
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
  * @param needResult is true if the result is needed.
  * @param maxReturn is the maximum number of rows to return.  Use -1 for infinite.
  * @param spec is the result specification object, or null for standard.
  * @param returnLimits is a description of how to limit return results (in addition to the maxReturn value).
  *  Pass null if no limits are desired.
  * @return the resultset
  */
  public IResultSet executeQuery(String query, List params, StringSet cacheKeys, StringSet invalidateKeys,
    String queryClass, boolean needResult, int maxReturn, ResultSpecification spec, ILimitChecker returnLimits)
    throws ManifoldCFException
  {
    // System.out.println("Query: "+query);
    if (Logging.db.isDebugEnabled())
    {
      Logging.db.debug("Requested query: [" + query + "]");
    }


    // Make sure we can't cache a query that invalidates stuff
    if (!needResult)
      cacheKeys = null;

    // We do NOT automatically qualify the cache and invalidation keys with the database name.
    // This is a job that the caller will need to do, where required.

    // Create object description
    QueryDescription[] queryDescriptions = new QueryDescription[1];
    QueryCacheExecutor executor;

    // Note: The caching effects of transactions are now handled by the cache manager.
    // All we do is tell it what we are doing.  This is encapsulated by the transaction ID passed
    // to the cache methods.

    queryDescriptions[0] = new QueryDescription(databaseName,query,params,queryClass,cacheKeys,maxReturn,spec,returnLimits);
    executor = new QueryCacheExecutor(this,needResult);
    cacheManager.findObjectsAndExecute(queryDescriptions,invalidateKeys,executor,getTransactionID());

    return executor.getResult();
  }

  /** Get the current transaction type.  Returns "READCOMMITTED"
  * outside of a transaction.
  */
  public int getCurrentTransactionType()
  {
    if (th == null)
      return IDBInterface.TRANSACTION_READCOMMITTED;
    return th.getTransactionType();
  }

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
    throws ManifoldCFException
  {
    if (Logging.db.isDebugEnabled())
      Logging.db.debug("Beginning transaction of type "+Integer.toString(transactionType));
    // Currently the cache management does absolutely nothing different for transactions of different types.
    // In practice this is not currently a problem, although a more rigorous treatment would involve taking greater
    // care to mirror the different types.
    // The "begin transaction" command itself is fired off in this module.  Anything additional will be fired off after that
    // at the database implementation layer, which will incidentally cause any delayed transactions to actually be starte.d
    String enclosingID = (th==null)?null:th.getTransactionID();
    delayedTransactionDepth++;
    th = new TransactionHandle(context,th,transactionType);
    cacheManager.startTransaction(th.getTransactionID(),enclosingID);
    doRollback = false;
  }

  /** Synchronize internal transactions.
  */
  protected void synchronizeTransactions()
    throws ManifoldCFException
  {
    while (delayedTransactionDepth > 0)
    {
      // Try starting the transaction
      // If failure, make CERTAIN that the little number does not get decremented!
      internalTransactionBegin();
      delayedTransactionDepth--;
    }
  }

  /** Perform actual transaction begin.
  */
  protected void internalTransactionBegin()
    throws ManifoldCFException
  {
    // Get a semipermanent connection
    if (connection == null)
    {
      connection = ConnectionFactory.getConnection(jdbcUrl,jdbcDriverClass,databaseName,userName,password);
      try
      {
        // Start a transaction
        startATransaction();
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        {
          connection = null;
          throw e;
        }
        ConnectionFactory.releaseConnection(connection);
        connection = null;
        throw e;
      }
      catch (Error e)
      {
        ConnectionFactory.releaseConnection(connection);
        connection = null;
        throw e;
      }
    }
    else
    {
      try
      {
        startATransaction();
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
        {
          // Don't do anything else other than drop the connection on the floor
          connection = null;
        }
        throw e;
      }
    }
  }

  /** Signal that a rollback should occur on the next endTransaction().
  */
  public void signalRollback()
  {
    Logging.db.debug("Rolling transaction back!");
    doRollback = true;
  }

  /** End a database transaction, either performing a commit or a rollback (depending on whether
  * signalRollback() was called within the transaction).
  */
  public void endTransaction()
    throws ManifoldCFException
  {
    Logging.db.debug("Ending transaction");
    if (th == null)
      throw new ManifoldCFException("End transaction without begin!",ManifoldCFException.GENERAL_ERROR);

    TransactionHandle parentTransaction = th.getParent();
    // If the database throws up on the commit or the rollback, above us there
    // will be no attempt to retry the transaction commit or rollback, so do NOT leave things
    // in an inconsistent state!  As far as we are concerned, the transaction is over, end of
    // story.
    try
    {
      if (delayedTransactionDepth > 0)
        delayedTransactionDepth--;
      else
      {
        try
        {
          if (doRollback)
          {
            // Do a rollback in the database, and blow away cached queries (cached against the
            // database transaction key).
            rollbackCurrentTransaction();
          }
          else
          {
            // Do a commit into the database, and blow away cached queries (cached against the
            // database transaction key).
            commitCurrentTransaction();
          }
        }
        catch (ManifoldCFException e)
        {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          {
            // Drop the connection on the floor, so it cannot be reused.
            connection = null;
          }
          throw e;
        }
        finally
        {
          if (parentTransaction == null && connection != null)
          {
            ConnectionFactory.releaseConnection(connection);
            connection = null;
          }
        }
      }
    }
    finally
    {
      if (doRollback)
        cacheManager.rollbackTransaction(th.getTransactionID());
      else
        cacheManager.commitTransaction(th.getTransactionID());
      th = parentTransaction;
    }

  }

  /** Note a number of inserts, modifications, or deletions to a specific table.  This is so we can decide when to do appropriate maintenance.
  *@param tableName is the name of the table being modified.
  *@param insertCount is the number of inserts.
  *@param modifyCount is the number of updates.
  *@param deleteCount is the number of deletions.
  */
  public void noteModifications(String tableName, int insertCount, int modifyCount, int deleteCount)
    throws ManifoldCFException
  {
  }

  protected static Random random = new Random();

  /** Sleep a random amount of time after a transaction abort.
  */
  public long getSleepAmt()
  {
    // Amount should be between .5 and 1 minute, approx, to give things time to unwind
    return (long)(random.nextDouble() * 60000.0 + 500.0);
  }

  /** Sleep, as part of recovery from deadlock.
  */
  public void sleepFor(long amt)
    throws ManifoldCFException
  {
    if (amt == 0L)
      return;

    try
    {
      ManifoldCF.sleep(amt);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted",e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Thread used to execute queries.  An instance of this thread is spun up every time a query is executed.  This is necessary because JDBC does not
  * guarantee interruptability, and the Postgresql JDBC driver unfortunately eats all thread interrupts.  So, we fire up a thread to do each interaction with
  * the database server, thus insuring that the owning thread remains interruptable and will therefore not block shutdown.
  */
  protected class ExecuteQueryThread extends Thread
  {
    protected Connection connection;
    protected String query;
    protected List params;
    protected boolean bResults;
    protected int maxResults;
    protected ResultSpecification spec;
    protected ILimitChecker returnLimit;
    protected Throwable exception = null;
    protected IResultSet rval = null;

    public ExecuteQueryThread(Connection connection, String query, List params, boolean bResults, int maxResults,
      ResultSpecification spec, ILimitChecker returnLimit)
    {
      super();
      setDaemon(true);
      this.connection = connection;
      this.query = query;
      this.params = params;
      this.bResults = bResults;
      this.maxResults = maxResults;
      this.spec = spec;
      this.returnLimit = returnLimit;
    }

    public void run()
    {
      try
      {
        // execute using the passed connection handle
        rval = execute(connection,query,params,bResults,maxResults,spec,returnLimit);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public IResultSet getResponse()
    {
      return rval;
    }
  }

  /** Do query execution via a subthread, so the primary thread can be interrupted */
  protected IResultSet executeViaThread(Connection connection, String query, List params, boolean bResults, int maxResults,
    ResultSpecification spec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {
    if (connection == null)
      // This probably means that the thread was interrupted and the connection was abandoned.  Just return null.
      return null;

    ExecuteQueryThread t = new ExecuteQueryThread(connection,query,params,bResults,maxResults,spec,returnLimit);
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof ManifoldCFException)
        {
          // Nest the exceptions so there is a hope we actually see the context, while preserving the kind of error it is
          ManifoldCFException me = (ManifoldCFException)thr;
          throw new ManifoldCFException("Database exception: "+me.getMessage(),me.getCause(),me.getErrorCode());
        }
        else
          throw (Error)thr;
      }
      return t.getResponse();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }

  }

  /** This method does NOT appear in any interface; it is here to
  * service the cache object.
  */
  protected IResultSet executeUncachedQuery(String query, List params, boolean bResults, int maxResults,
    ResultSpecification spec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {

    if (connection != null)
    {
      try
      {
        return executeViaThread(connection,query,params,bResults,maxResults,spec,returnLimit);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          // drop the connection object on the floor, so it cannot possibly be reused
          connection = null;
        throw e;
      }
    }
    else
    {
      // Grab a connection
      Connection tempConnection = ConnectionFactory.getConnection(jdbcUrl,jdbcDriverClass,databaseName,userName,password);
      try
      {
        return executeViaThread(tempConnection,query,params,bResults,maxResults,spec,returnLimit);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          // drop the connection object on the floor, so it cannot possibly be reused
          tempConnection = null;
        throw e;
      }
      finally
      {
        if (tempConnection != null)
          ConnectionFactory.releaseConnection(tempConnection);
      }
    }
  }


  // These are protected helper methods

  /** Run a query.  No caching is involved at all at this level.
  * @param query String the query string
  * @param bResults boolean whether to load the resultset or not
  * @param maxResults is the maximum number of results to load: -1 if all
  * @param params List if params !=null, use preparedStatement
  */
  protected IResultSet execute(Connection connection, String query, List params, boolean bResults, int maxResults,
    ResultSpecification spec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {
    IResultSet rval = null;
    try
    {
      try
      {
        ResultSet rs;
        long queryStartTime = 0L;

        if (Logging.db.isDebugEnabled())
        {
          queryStartTime = System.currentTimeMillis();
          Logging.db.debug("Actual query: [" + query + "]");
          if (params != null)
          {
            int i = 0;
            while (i <  params.size())
            {
              Logging.db.debug("  Parameter " + i + ": '" + params.get(i).toString() + "'");
              i++;
            }
          }
        }

        if (params==null)
        {
          //stmt = _connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
          //                                                                      ResultSet.CONCUR_READ_ONLY);
          // lightest statement type
          Statement stmt = connection.createStatement();
          try
          {
            stmt.execute(query);
            rs = stmt.getResultSet();
            try
            {
              // Suck data from resultset
              rval = getData(rs,bResults,maxResults,spec,returnLimit);
            }
            finally
            {
              if (rs != null)
                rs.close();
            }
          }
          finally
          {
            stmt.close();
          }
        }
        else
        {
          PreparedStatement ps = connection.prepareStatement(query);
          try
          {
            loadPS(ps, params);
            if (bResults)
            {
              rs = ps.executeQuery();
              try
              {
                // Suck data from resultset
                rval = getData(rs,true,maxResults,spec,returnLimit);
              }
              finally
              {
                if (rs != null)
                  rs.close();
              }
            }
            else
            {
              ps.executeUpdate();
              rval = getData(null,false,0,spec,null);
            }
          }
          finally
          {
            ps.close();
          }
        }
        if (Logging.db.isDebugEnabled())
          Logging.db.debug("Done actual query ("+new Long(System.currentTimeMillis()-queryStartTime).toString()+"ms): ["+query+"]");
      }
      catch (java.sql.SQLException e)
      {
        // There are a lot of different sorts of error that can be embedded here.  Unfortunately, it's database dependent how
        // to interpret the error.  So toss a generic error, and let the caller figure out if it needs to treat it differently.
        throw new ManifoldCFException("Exception doing query: "+e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
      }
    }
    finally
    {
      // Cleanup of input parameters ALWAYS occurs, because whether we succeed or fail, we are done with any input streams.
      if (params != null)
        cleanupParameters(params);
    }
    return rval;
  }

  // Read data from a resultset
  protected IResultSet getData(ResultSet rs, boolean bResults, int maxResults, ResultSpecification spec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {
    RSet results = new RSet();  // might be empty but not an error
    try
    {
      try
      {
        if (rs != null)
        {
          int colcount = 0;
          String[] resultCols = null;
          String[] resultLabels = null;

          // Optionally we're going to suck the data
          // out of the db and return it in a
          // readonly structure
          ResultSetMetaData rsmd = rs.getMetaData();
          if (rsmd != null)
          {
            colcount = rsmd.getColumnCount();

            //LogBean.db.debug(colcount + " columns returned.");

            resultCols = new String[colcount];
            resultLabels = new String[colcount];
            for (int i = 0; i < colcount; i++)
            {
              resultCols[i] = rsmd.getColumnName(i+1);
              resultLabels[i] = mapColumnName(rsmd.getColumnLabel(i+1));
            }
          }

          if (bResults)
          {
            if (colcount == 0)
            {
              // This is an error situation; if a result with no columns is
              // necessary, bResults must be false!!!
              throw new ManifoldCFException("Empty query, no columns returned",ManifoldCFException.GENERAL_ERROR);
            }

            while (rs.next() && (maxResults == -1 || maxResults > 0) && (returnLimit == null || returnLimit.checkContinue()))
            {
              Object value;
              RRow m = new RRow();

              // We have 'colcount' cols to look thru
              for (int i = 0; i < colcount; i++)
              {
                String key = resultCols[i];
                // System.out.println("Key = "+key);
                int colnum = findColumn(rs,key);
                value = null;
                if (colnum > -1)
                {
                  value = getObject(rs,rsmd,colnum,(spec == null)?ResultSpecification.FORM_DEFAULT:spec.getForm(key.toLowerCase()));
                }
                //System.out.println(" Key = '"+resultLabels[i]+"', value = "+((value==null)?"NULL":value.toString()));
                m.put(resultLabels[i], value);
              }

              // See if we should include this row
              boolean include = true;
              if (returnLimit != null)
              {
                include = returnLimit.checkInclude(m);
              }

              if (include)
              {
                if (maxResults != -1)
                  maxResults--;
                results.addRow(m);
              }
              else
              {
                // As a courtesy, clean up any BinaryInput objects in the row we are skipping
                Iterator iter = m.getColumns();
                while (iter.hasNext())
                {
                  String columnName = (String)iter.next();
                  Object colValue = m.getValue(columnName);
                  if (colValue instanceof BinaryInput)
                    ((BinaryInput)colValue).discard();
                  else if (colValue instanceof CharacterInput)
                    ((CharacterInput)colValue).discard();
                }
              }
            }
          }
        }
      }
      catch (java.sql.SQLException e)
      {
        throw new ManifoldCFException("Resultset error: "+e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
      }
    }
    catch (Throwable e)
    {
      // Clean up resultset before continuing
      int i = 0;
      while (i < results.getRowCount())
      {
        IResultRow row = results.getRow(i++);
        int j = 0;
        Iterator iter = row.getColumns();
        while (iter.hasNext())
        {
          String colName = (String)iter.next();
          Object o = row.getValue(colName);
          if (o instanceof BinaryInput)
            ((BinaryInput)o).discard();
          else if (o instanceof CharacterInput)
            ((CharacterInput)o).discard();
        }
      }
      if (e instanceof ManifoldCFException)
        throw (ManifoldCFException)e;
      if (e instanceof RuntimeException)
        throw (RuntimeException)e;
      if (e instanceof Error)
        throw (Error)e;
      throw new Error("Unexpected exception caught: "+e.getMessage(),e);
    }
    return results;
  }

  // pass params to preparedStatement
  protected static void loadPS(PreparedStatement ps, List data)
    throws java.sql.SQLException, ManifoldCFException
  {
    if (data!=null)
    {
      for (int i = 0; i < data.size(); i++)
      {
        // If the input type is a string, then set it as such.
        // Otherwise, if it's an input stream, we make a blob out of it.
        Object x = data.get(i);
        if (x instanceof String)
        {
          String value = (String)x;
          // letting database do lame conversion!
          ps.setString(i+1, value);
        }
        if (x instanceof BinaryInput)
        {
          BinaryInput value = (BinaryInput)x;
          long length = value.getLength();
          // System.out.println("Blob length on write = "+Long.toString(value.getLength()));
          ps.setBinaryStream(i+1,value.getStream(),(length == -1L)?Integer.MAX_VALUE:(int)length);
        }
        if (x instanceof CharacterInput)
        {
          CharacterInput value = (CharacterInput)x;
          long length = value.getCharacterLength();
          ps.setCharacterStream(i+1,value.getStream(),(length == -1L)?Integer.MAX_VALUE:(int)length);
        }
        if (x instanceof java.util.Date)
        {
          ps.setDate(i+1,new java.sql.Date(((java.util.Date)x).getTime()));
        }
        if (x instanceof Long)
        {
          ps.setLong(i+1,((Long)x).longValue());
        }
        if (x instanceof TimeMarker)
        {
          ps.setTimestamp(i+1,new java.sql.Timestamp(((TimeMarker)x).longValue()));
        }
        if (x instanceof Double)
        {
          ps.setDouble(i+1,((Double)x).doubleValue());
        }
        if (x instanceof Integer)
        {
          ps.setInt(i+1,((Integer)x).intValue());
        }
        if (x instanceof Float)
        {
          ps.setFloat(i+1,((Float)x).floatValue());
        }
      }
    }
  }

  /** Clean up parameters after query has been triggered.
  */
  protected static void cleanupParameters(List data)
    throws ManifoldCFException
  {
    if (data != null)
    {
      for (int i = 0; i < data.size(); i++)
      {
        // If the input type is a string, then set it as such.
        // Otherwise, if it's an input stream, we make a blob out of it.
        Object x = data.get(i);
        if (x instanceof BinaryInput)
        {
          ((BinaryInput)x).doneWithStream();
        }
        else if (x instanceof CharacterInput)
        {
          ((CharacterInput)x).doneWithStream();
        }
      }
    }
  }

  protected int findColumn(ResultSet rs, String name)
    throws ManifoldCFException
  {
    try
    {
      return rs.findColumn(name);
    }
    catch (SQLException e)
    {
      return -1;
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Error finding " + name + " in resultset: "+e.getMessage(),e,ManifoldCFException.DATABASE_ERROR);
    }
  }

  protected Blob getBLOB(ResultSet rs, int col)
    throws ManifoldCFException
  {
    try
    {
      return rs.getBlob(col);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ManifoldCFException("Error in getBlob",sqle,ManifoldCFException.DATABASE_CONNECTION_ERROR);
    }
    catch (Exception sqle)
    {
      throw new ManifoldCFException("Error in getBlob",sqle,ManifoldCFException.DATABASE_ERROR);
    }
  }

  protected boolean isBLOB(ResultSetMetaData rsmd, int col)
    throws ManifoldCFException
  {
    try
    {
      int type = rsmd.getColumnType(col);
      return (type == java.sql.Types.BLOB);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ManifoldCFException("Error in isBlob("+col+"): "+sqle.getMessage(),sqle,ManifoldCFException.DATABASE_CONNECTION_ERROR);
    }
    catch (Exception sqle)
    {
      throw new ManifoldCFException("Error in isBlob("+col+"): "+sqle.getMessage(),sqle,ManifoldCFException.DATABASE_ERROR);
    }
  }

  protected boolean isBinary(ResultSetMetaData rsmd, int col)
    throws ManifoldCFException
  {
    try
    {
      int type = rsmd.getColumnType(col);
      return (type == java.sql.Types.VARBINARY ||
        type == java.sql.Types.BINARY || type == java.sql.Types.LONGVARBINARY);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ManifoldCFException("Error in isBinary("+col+"): "+sqle.getMessage(),sqle,ManifoldCFException.DATABASE_CONNECTION_ERROR);
    }
    catch (Exception sqle)
    {
      throw new ManifoldCFException("Error in isBinary("+col+"): "+sqle.getMessage(),sqle,ManifoldCFException.DATABASE_ERROR);
    }
  }

  protected Object getObject(ResultSet rs, ResultSetMetaData rsmd, int col, int desiredForm)
    throws ManifoldCFException
  {
    Object result = null;

    try
    {
      try
      {
        if (isBLOB(rsmd,col))
        {
          // System.out.println("It's a blob!");
          Blob blob = getBLOB(rs,col);
          if (blob != null)
          {
            // Create a tempfileinput object!
            // Cleanup should happen by the user of the resultset.
            // System.out.println(" Blob length = "+Long.toString(blob.length()));
            result = new TempFileInput(blob.getBinaryStream(),blob.length());
          }
        }
        else if (isBinary(rsmd,col))
        {
          java.io.InputStream is = rs.getBinaryStream(col);
          if (is != null)
          {
            // Create a tempfileinput object!
            // Cleanup should happen by the user of the resultset.
            result = new TempFileInput(is);
          }
        }
        else
        {
          Timestamp timestamp;
          java.sql.Date date;
          Clob clob;
          String resultString;

          switch (rsmd.getColumnType(col))
          {
          case java.sql.Types.CHAR :
          case java.sql.Types.VARCHAR :
            if ((resultString = rs.getString(col)) != null)
            {
              if (rsmd.getColumnDisplaySize(col) < resultString.length())
              {
                result = resultString.substring(0,rsmd.getColumnDisplaySize(col));
              }
              else
                result = resultString;
            }
            break;
          case java.sql.Types.CLOB :
            switch (desiredForm)
            {
            case ResultSpecification.FORM_DEFAULT:
            case ResultSpecification.FORM_STRING:
              if ((clob = rs.getClob(col)) != null)
              {
                result = clob.getSubString(1, (int) clob.length());
              }
              break;
            case ResultSpecification.FORM_STREAM:
              result = new TempFileCharacterInput(rs.getCharacterStream(col));
              break;
            default:
              throw new ManifoldCFException("Illegal form requested for column "+Integer.toString(col)+": "+Integer.toString(desiredForm));
            }
            break;
          case java.sql.Types.BIGINT :
            long l = rs.getLong(col);
            if (!rs.wasNull())
              result = new Long(l);
            break;

          case java.sql.Types.INTEGER :
            int i = rs.getInt(col);
            if (!rs.wasNull())
              result = new Integer(i);
            break;

          case java.sql.Types.SMALLINT:
            short s = rs.getShort(col);
            if (!rs.wasNull())
              result = new Short(s);
            break;

          case java.sql.Types.REAL :
          case java.sql.Types.FLOAT :
            float f = rs.getFloat(col);
            if (!rs.wasNull())
              result = new Float(f);
            break;

          case java.sql.Types.DOUBLE :
            double d = rs.getDouble(col);
            if (!rs.wasNull())
              result = new Double(d);
            break;

          case java.sql.Types.DATE :
            if ((date = rs.getDate(col)) != null)
            {
              result = new java.util.Date(date.getTime());
            }
            break;

          case java.sql.Types.TIMESTAMP :
            if ((timestamp = rs.getTimestamp(col)) != null)
            {
              result = new TimeMarker(timestamp.getTime());
            }
            break;

          case java.sql.Types.BOOLEAN :
            boolean b = rs.getBoolean(col);
            if (!rs.wasNull())
              result = new Boolean(b);
            break;

          case java.sql.Types.BLOB:
            throw new ManifoldCFException("BLOB is not a string, column = " + col,ManifoldCFException.GENERAL_ERROR);

          default :
            switch (desiredForm)
            {
            case ResultSpecification.FORM_DEFAULT:
            case ResultSpecification.FORM_STRING:
              result = rs.getString(col);
              break;
            case ResultSpecification.FORM_STREAM:
              result = new TempFileCharacterInput(rs.getCharacterStream(col));
              break;
            default:
              throw new ManifoldCFException("Illegal form requested for column "+Integer.toString(col)+": "+Integer.toString(desiredForm));
            }
            break;
          }
          if (rs.wasNull())
          {
            if (result instanceof CharacterInput)
              ((CharacterInput)result).discard();
            result = null;
          }
        }
      }
      catch (java.sql.SQLException e)
      {
        throw new ManifoldCFException("Exception in getObject(): "+e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
      }
    }
    catch (Throwable e)
    {
      if (result instanceof CharacterInput)
        ((CharacterInput)result).discard();
      else if (result instanceof BinaryInput)
        ((BinaryInput)result).discard();
      if (e instanceof ManifoldCFException)
        throw (ManifoldCFException)e;
      if (e instanceof RuntimeException)
        throw (RuntimeException)e;
      if (e instanceof Error)
        throw (Error)e;
      throw new Error("Unexpected exception caught: "+e.getMessage(),e);
    }
    return result;
  }


  /** This object is meant to execute within a cache manager call.  It contains all knowledge needed to
  * perform any query, including a parameterized one.  It may (or may not) be also passed a transaction
  * handle, depending on whether or not a transaction is currently underway.
  * Nevertheless, all database access, save transaction setup and teardown, takes place inside this class.
  * Even uncached queries will be done here; the cache manager will simply not keep the result around
  * afterwards.
  */
  public static class QueryCacheExecutor extends org.apache.manifoldcf.core.cachemanager.ExecutorBase
  {
    // We store only those things that will not come in from the object description.
    protected Database database;
    protected boolean needResult;
    protected IResultSet resultset = null;

    public QueryCacheExecutor(Database database, boolean needResult)
    {
      super();
      this.database = database;
      this.needResult = needResult;
    }

    /** Fetch the result.  No errors are possible at this time; they would have already
    * occurred...
    */
    public IResultSet getResult()
    {
      return resultset;
    }

    /** Create a new object to operate on and cache.  This method is called only
    * if the specified object is NOT available in the cache.  The specified object
    * should be created and returned; if it is not created, it means that the
    * execution cannot proceed, and the execute() method will not be called.
    * @param objectDescriptions are the unique identifiers of the objects.
    * @return the newly created objects to cache, or null, if any object cannot be created.
    */
    public Object[] create(ICacheDescription[] objectDescriptions) throws ManifoldCFException
    {
      // Perform the requested query, within the appropriate transaction object.
      // Call the database object to do this
      Object[] rval = new Object[objectDescriptions.length];
      int i = 0;
      while (i < objectDescriptions.length)
      {
        database.synchronizeTransactions();
        QueryDescription description = (QueryDescription)objectDescriptions[i];
        ILimitChecker limit = description.getReturnLimit();
        ResultSpecification spec = description.getResultSpecification();
        // I've prevented us from ever caching things that have limit objects
        // at a higher level...
        // if (limit != null)
        //      limit = limit.duplicate();
        // ResultSpecification objects are considered "read only" once passed to the cache, so duplication is unneeded.
        // if (spec != null)
        //      spec = spec.duplicate();
        long startTime = System.currentTimeMillis();
        rval[i] = database.executeUncachedQuery(description.getQuery(),description.getParameters(),needResult,
          description.getMaxReturn(),spec,limit);

        long endTime = System.currentTimeMillis();
        if (endTime-startTime > 60000L && description.getQuery().length() >= 6 &&
          ("SELECT".equalsIgnoreCase(description.getQuery().substring(0,6)) || "UPDATE".equalsIgnoreCase(description.getQuery().substring(0,6))))
        {
          Logging.db.warn("Found a query that took more than a minute ("+new Long(endTime-startTime).toString()+" ms): ["+description.getQuery()+"]");
          if (description.getParameters() != null)
          {
            int j = 0;
            while (j <  description.getParameters().size())
            {
              Logging.db.warn("  Parameter " + j + ": '" + description.getParameters().get(j).toString() + "'");
              j++;
            }
          }
          try
          {
            database.explainQuery(description.getQuery(),description.getParameters());
          }
          catch (ManifoldCFException e)
          {
            Logging.db.error("Explain failed with error "+e.getMessage(),e);
          }

        }
        i++;
      }
      return rval;
    }


    /** Notify the implementing class of the existence of a cached version of the
    * object.  The object is passed to this method so that the execute() method below
    * will have it available to operate on.  This method is also called for all objects
    * that are freshly created as well.
    * @param objectDescription is the unique identifier of the object.
    * @param cachedObject is the cached object.
    */
    public void exists(ICacheDescription objectDescription, Object cachedObject) throws ManifoldCFException
    {
      // System.out.println("Object created or found: "+objectDescription.getCriticalSectionName());
      // Save the resultset for return
      resultset = (IResultSet)cachedObject;
    }

    /** Perform the desired operation.  This method is called after either createGetObject()
    * or exists() is called for every requested object.
    */
    public void execute() throws ManifoldCFException
    {
      // Does nothing at all; the query would already have been done
    }


  }

}
