/* $Id: JDBCConnection.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.connectors.jdbc;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.core.database.*;
import org.apache.acf.agents.interfaces.*;

import java.sql.*;
import javax.naming.*;
import javax.sql.*;

import java.io.*;
import java.util.*;

import com.bitmechanic.sql.*;

/** This object describes a connection to a particular JDBC instance.
*/
public class JDBCConnection
{
  public static final String _rcsid = "@(#)$Id: JDBCConnection.java 921329 2010-03-10 12:44:20Z kwright $";

  protected String jdbcProvider = null;
  protected String host = null;
  protected String databaseName = null;
  protected String userName = null;
  protected String password = null;

  /** Constructor.
  */
  public JDBCConnection(String jdbcProvider, String host, String databaseName, String userName, String password)
  {
    this.jdbcProvider = jdbcProvider;
    this.host = host;
    this.databaseName = databaseName;
    this.userName = userName;
    this.password = password;
  }

  protected static IResultRow readNextResultRowViaThread(ResultSet rs, ResultSetMetaData rsmd, String[] resultCols)
    throws ACFException, ServiceInterruption
  {
    NextResultRowThread t = new NextResultRowThread(rs,rsmd,resultCols);
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof java.sql.SQLException)
          throw new ACFException("Error fetching next JDBC result row: "+thr.getMessage(),thr);
        else if (thr instanceof ACFException)
          throw (ACFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else
          throw (Error)thr;
      }
      return t.getResponse();
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
    }
  }

  protected static class NextResultRowThread extends Thread
  {
    protected ResultSet rs;
    protected ResultSetMetaData rsmd;
    protected String[] resultCols;

    protected Throwable exception = null;
    protected IResultRow response = null;

    public NextResultRowThread(ResultSet rs, ResultSetMetaData rsmd, String[] resultCols)
    {
      super();
      setDaemon(true);
      this.rs = rs;
      this.rsmd = rsmd;
      this.resultCols = resultCols;
    }

    public void run()
    {
      try
      {
        response = readNextResultRow(rs,rsmd,resultCols);
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

    public IResultRow getResponse()
    {
      return response;
    }
  }

  protected static IResultRow readNextResultRow(ResultSet rs, ResultSetMetaData rsmd, String[] resultCols)
    throws ACFException, ServiceInterruption
  {
    try
    {
      if (rs.next())
      {
        return readResultRow(rs,rsmd,resultCols);
      }
      return null;
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Result set error: "+e.getMessage(),e);
    }
  }

  protected static void closeResultset(ResultSet rs)
    throws ACFException, ServiceInterruption
  {
    try
    {
      rs.close();
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Exception closing resultset: "+e.getMessage(),e);
    }
  }

  protected static void closeStmt(Statement stmt)
    throws ACFException, ServiceInterruption
  {
    try
    {
      stmt.close();
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Exception closing statement: "+e.getMessage(),e);
    }
  }

  protected static void closePS(PreparedStatement ps)
    throws ACFException, ServiceInterruption
  {
    try
    {
      ps.close();
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Exception closing statement: "+e.getMessage(),e);
    }
  }


  /** Test connection.
  */
  public void testConnection()
    throws ACFException, ServiceInterruption
  {
    TestConnectionThread t = new TestConnectionThread();
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof java.sql.SQLException)
          throw new ACFException("Error doing JDBC connection test: "+thr.getMessage(),thr);
        else if (thr instanceof ACFException)
          throw (ACFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else
          throw (Error)thr;
      }
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
    }
  }

  protected class TestConnectionThread extends Thread
  {
    protected Throwable exception = null;

    public TestConnectionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        Connection tempConnection = JDBCConnectionFactory.getConnection(jdbcProvider,host,databaseName,userName,password);
        JDBCConnectionFactory.releaseConnection(tempConnection);
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
  }

  /** Execute query.
  */
  public IDynamicResultSet executeUncachedQuery(String query, ArrayList params, int maxResults)
    throws ACFException, ServiceInterruption
  {
    if (params == null)
      return new JDBCResultSet(query,maxResults);
    else
      return new JDBCPSResultSet(query,params,maxResults);
  }

  /** Execute operation.
  */
  public void executeOperation(String query, ArrayList params)
    throws ACFException, ServiceInterruption
  {
    ExecuteOperationThread t = new ExecuteOperationThread(query,params);
    try
    {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null)
      {
        if (thr instanceof java.sql.SQLException)
          throw new ACFException("Exception doing connector query '"+query+"': "+thr.getMessage(),thr);
        else if (thr instanceof ACFException)
          throw (ACFException)thr;
        else if (thr instanceof ServiceInterruption)
          throw (ServiceInterruption)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else
          throw (Error)thr;
      }
    }
    catch (InterruptedException e)
    {
      t.interrupt();
      throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
    }
  }

  protected class ExecuteOperationThread extends Thread
  {
    protected String query;
    protected ArrayList params;

    protected Throwable exception = null;

    public ExecuteOperationThread(String query, ArrayList params)
    {
      super();
      setDaemon(true);
      this.query = query;
      this.params = params;
    }

    public void run()
    {
      try
      {
        Connection tempConnection = JDBCConnectionFactory.getConnection(jdbcProvider,host,databaseName,userName,password);
        try
        {
          execute(tempConnection,query,params,false,0);
        }
        finally
        {
          JDBCConnectionFactory.releaseConnection(tempConnection);
        }
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
  }

  /** Run a query.  No caching is involved at all at this level.
  * @param query String the query string
  * @param maxResults is the maximum number of results to load: -1 if all
  * @param params ArrayList if params !=null, use preparedStatement
  */
  protected static IResultSet execute(Connection connection, String query, ArrayList params, boolean bResults, int maxResults)
    throws ACFException, ServiceInterruption
  {

    ResultSet rs;

    try
    {

      if (params==null)
      {
        // lightest statement type
        Statement stmt = connection.createStatement();
        try
        {
          stmt.execute(query);
          rs = stmt.getResultSet();
          try
          {
            // Suck data from resultset
            if (bResults)
              return getData(rs,maxResults);
            return null;
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
              return getData(rs,maxResults);
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
            return null;
          }

        }
        finally
        {
          ps.close();
          cleanupParameters(params);
        }
      }

    }
    catch (ACFException e)
    {
      throw e;
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Exception doing connector query '"+query+"': "+e.getMessage(),e);
    }
  }

  /** Read the current row from the resultset */
  protected static IResultRow readResultRow(ResultSet rs, ResultSetMetaData rsmd, String[] resultCols)
    throws ACFException, ServiceInterruption
  {
    try
    {
      Object value = null;
      RRow m = new RRow();

      // We have 'colcount' cols to look thru
      for (int i = 0; i < resultCols.length; i++)
      {
        String key = resultCols[i];
        // System.out.println("Key = "+key);
        int colnum = findColumn(rs,key);
        if (colnum > -1)
        {
          if (isBinaryData(rsmd,colnum))
          {
            InputStream bis = rs.getBinaryStream(colnum);
            if (bis != null)
              value = new TempFileInput(bis);
          }
          else if (isBLOB(rsmd,colnum))
          {
            // System.out.println("It's a blob!");
            Blob blob = getBLOB(rs,colnum);
            // Create a tempfileinput object!
            // Cleanup should happen by the user of the resultset.
            // System.out.println(" Blob length = "+Long.toString(blob.length()));
            if (blob != null)
              value = new TempFileInput(blob.getBinaryStream(),blob.length());
          }
          else if (isCLOB(rsmd,colnum))
          {
            Clob clob = getCLOB(rs,colnum);
            // Note well: we have not figured out how to handle characters outside of ASCII!
            if (clob != null)
              value = new TempFileInput(clob.getAsciiStream(),clob.length());
          }
          else
          {
            // System.out.println("It's not a blob");
            value = getObject(rs,rsmd,colnum);
          }
        }
        if (value != null)
          m.put(key, value);
      }
      return m;

    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Resultset error: "+e.getMessage(),e);
    }
  }

  protected static String[] readColumnNames(ResultSetMetaData rsmd)
    throws ACFException, ServiceInterruption
  {
    try
    {
      String[] resultCols;
      if (rsmd != null)
      {
        int colcount = rsmd.getColumnCount();
        resultCols = new String[colcount];
        for (int i = 0; i < colcount; i++)
        {
          resultCols[i] = rsmd.getColumnName(i+1);
        }
      }
      else
        resultCols = new String[0];
      return resultCols;
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Sql exception reading column names: "+e.getMessage(),e);
    }
  }

  // Read data from a resultset
  protected static IResultSet getData(ResultSet rs, int maxResults)
    throws ACFException, ServiceInterruption
  {
    try
    {
      RSet results = new RSet();  // might be empty but not an error

      if (rs != null)
      {
        // Optionally we're going to suck the data
        // out of the db and return it in a
        // readonly structure
        ResultSetMetaData rsmd = rs.getMetaData();
        String[] resultCols = readColumnNames(rsmd);
        if (resultCols.length == 0)
        {
          // This is an error situation; if a result with no columns is
          // necessary, bResults must be false!!!
          throw new ACFException("Empty query, no columns returned",ACFException.GENERAL_ERROR);
        }

        while (rs.next() && (maxResults == -1 || maxResults > 0))
        {
          IResultRow m = readResultRow(rs,rsmd,resultCols);
          if (maxResults != -1)
            maxResults--;
          results.addRow(m);
        }
      }
      return results;
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Resultset error: "+e.getMessage(),e);
    }
  }

  // pass params to preparedStatement
  protected static void loadPS(PreparedStatement ps, ArrayList data)
    throws java.sql.SQLException, ACFException
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
          // System.out.println("Blob length on write = "+Long.toString(value.getLength()));
          // The oracle driver does a binary conversion to base 64 when writing data
          // into a clob column using a binary stream operator.  Since at this
          // point there is no way to distinguish the two, and since our tests use CLOB,
          // this code doesn't work for them.
          // So, for now, use the ascii stream method.
          //ps.setBinaryStream(i+1,value.getStream(),(int)value.getLength());
          ps.setAsciiStream(i+1,value.getStream(),(int)value.getLength());
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
          ps.setTimestamp(i+1,new java.sql.Timestamp(((Long)x).longValue()));
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
  protected static void cleanupParameters(ArrayList data)
    throws ACFException
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
          BinaryInput value = (BinaryInput)x;
          value.doneWithStream();
        }
      }
    }
  }

  protected static int findColumn(ResultSet rs, String name)
    throws ACFException, ServiceInterruption
  {
    try
    {
      return rs.findColumn(name);
    }
    catch (java.sql.SQLException e)
    {
      return -1;
    }
  }

  protected static Blob getBLOB(ResultSet rs, int col)
    throws ACFException, ServiceInterruption
  {
    try
    {
      return rs.getBlob(col);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ACFException("Error in getBlob("+col+"): "+sqle.getMessage(),sqle,ACFException.DATABASE_ERROR);
    }
  }

  protected static Clob getCLOB(ResultSet rs, int col)
    throws ACFException, ServiceInterruption
  {
    try
    {
      return rs.getClob(col);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ACFException("Error in getClob("+col+"): "+sqle.getMessage(),sqle,ACFException.DATABASE_ERROR);
    }
  }

  protected static boolean isBLOB(ResultSetMetaData rsmd, int col)
    throws ACFException, ServiceInterruption
  {
    try
    {
      int type = rsmd.getColumnType(col);
      return (type == java.sql.Types.BLOB);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ACFException("Error in isBlob("+col+"): "+sqle.getMessage(),sqle,ACFException.DATABASE_ERROR);
    }
  }

  protected static boolean isBinaryData(ResultSetMetaData rsmd, int col)
    throws ACFException, ServiceInterruption
  {
    try
    {
      int type = rsmd.getColumnType(col);
      return (type == java.sql.Types.VARBINARY ||
        type == java.sql.Types.BINARY || type == java.sql.Types.LONGVARBINARY);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ACFException("Error in isBinaryData("+col+"): "+sqle.getMessage(),sqle,ACFException.DATABASE_ERROR);
    }
  }

  protected static boolean isCLOB(ResultSetMetaData rsmd, int col)
    throws ACFException, ServiceInterruption
  {
    try
    {
      int type = rsmd.getColumnType(col);
      return (type == java.sql.Types.CLOB || type == java.sql.Types.LONGVARCHAR);
    }
    catch (java.sql.SQLException sqle)
    {
      throw new ACFException("Error in isClob("+col+"): "+sqle.getMessage(),sqle,ACFException.DATABASE_ERROR);
    }
  }

  protected static Object getObject(ResultSet rs, ResultSetMetaData rsmd, int col)
    throws ACFException, ServiceInterruption
  {
    Object result = null;

    try
    {
      Timestamp timestamp;
      java.sql.Date date;
      Clob clob;
      String resultString;

      switch (rsmd.getColumnType(col))
      {
      case java.sql.Types.CHAR :
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
        if ((clob = rs.getClob(col)) != null)
        {
          result = clob.getSubString(1, (int) clob.length());
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

      case java.sql.Types.BLOB:
      case java.sql.Types.VARBINARY:
      case java.sql.Types.BINARY:
      case java.sql.Types.LONGVARBINARY:
        throw new ACFException("Binary type is not a string, column = " + col,ACFException.GENERAL_ERROR);
        //break

      default :
        result = rs.getString(col);
        break;
      }
      if (rs.wasNull())
      {
        result = null;
      }
    }
    catch (java.sql.SQLException e)
    {
      throw new ACFException("Exception in getString(): "+e.getMessage(),e,ACFException.DATABASE_ERROR);
    }
    return result;
  }

  protected class JDBCResultSet implements IDynamicResultSet
  {
    protected Connection connection;
    protected Statement stmt;
    protected ResultSet rs;
    protected ResultSetMetaData rsmd;
    protected String[] resultCols;
    protected int maxResults;

    /** Constructor */
    public JDBCResultSet(String query, int maxResults)
      throws ACFException, ServiceInterruption
    {
      this.maxResults = maxResults;
      StatementQueryThread t = new StatementQueryThread(query);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof java.sql.SQLException)
            throw new ACFException("Exception doing connector query '"+query+"': "+thr.getMessage(),thr);
          else if (thr instanceof ACFException)
            throw (ACFException)thr;
          else if (thr instanceof ServiceInterruption)
            throw (ServiceInterruption)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        connection = t.getConnection();
        stmt = t.getStatement();
        rs = t.getResultSet();
        rsmd = t.getResultSetMetaData();
        resultCols = t.getColumnNames();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
    }

    /** Get the next row from the resultset.
    *@return the immutable row description, or null if there is no such row.
    */
    public IResultRow getNextRow()
      throws ACFException, ServiceInterruption
    {
      if (maxResults == -1 || maxResults > 0)
      {
        IResultRow row = readNextResultRowViaThread(rs,rsmd,resultCols);
        if (row != null && maxResults != -1)
          maxResults--;
        return row;
      }
      return null;
    }

    /** Close this resultset.
    */
    public void close()
      throws ACFException, ServiceInterruption
    {
      ACFException rval = null;
      if (rs != null)
      {
        try
        {
          closeResultset(rs);
          rs = null;
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
      }
      if (stmt != null)
      {
        try
        {
          closeStmt(stmt);
          stmt = null;
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
      }
      if (connection != null)
      {
        try
        {
          JDBCConnectionFactory.releaseConnection(connection);
          connection = null;
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
      }
      if (rval != null)
        throw rval;
    }

  }

  protected class StatementQueryThread extends Thread
  {
    protected String query;

    protected Throwable exception = null;
    protected Connection connection = null;
    protected Statement stmt = null;
    protected ResultSet rs = null;
    protected ResultSetMetaData rsmd = null;
    protected String[] resultCols = null;

    public StatementQueryThread(String query)
    {
      super();
      setDaemon(true);
      this.query = query;
    }

    public void run()
    {
      try
      {
        connection = JDBCConnectionFactory.getConnection(jdbcProvider,host,databaseName,userName,password);
        // lightest statement type
        stmt = connection.createStatement();
        stmt.execute(query);
        rs = stmt.getResultSet();
        rsmd = rs.getMetaData();
        resultCols = readColumnNames(rsmd);
      }
      catch (Throwable e)
      {
        this.exception = e;
        if (rs != null)
        {
          try
          {
            closeResultset(rs);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
            // Ignore
          }
          finally
          {
            rs = null;
          }
        }
        if (stmt != null)
        {
          try
          {
            closeStmt(stmt);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
            // Ignore
          }
          finally
          {
            stmt = null;
          }
        }
        if (connection != null)
        {
          try
          {
            JDBCConnectionFactory.releaseConnection(connection);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
            // Otherwise, ignore
          }
          finally
          {
            connection = null;
          }
        }
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public Connection getConnection()
    {
      return connection;
    }

    public Statement getStatement()
    {
      return stmt;
    }

    public ResultSet getResultSet()
    {
      return rs;
    }

    public ResultSetMetaData getResultSetMetaData()
    {
      return rsmd;
    }

    public String[] getColumnNames()
    {
      return resultCols;
    }
  }

  protected class JDBCPSResultSet implements IDynamicResultSet
  {
    protected Connection connection;
    protected PreparedStatement ps;
    protected ResultSet rs;
    protected ResultSetMetaData rsmd;
    protected String[] resultCols;
    protected int maxResults;
    protected ArrayList params;

    /** Constructor */
    public JDBCPSResultSet(String query, ArrayList params, int maxResults)
      throws ACFException, ServiceInterruption
    {
      this.maxResults = maxResults;
      this.params = params;
      PreparedStatementQueryThread t = new PreparedStatementQueryThread(query,params);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          // Cleanup of parameters happens even if exception doing query
          cleanupParameters(params);
          if (thr instanceof java.sql.SQLException)
            throw new ACFException("Exception doing connector query '"+query+"': "+thr.getMessage(),thr);
          else if (thr instanceof ACFException)
            throw (ACFException)thr;
          else if (thr instanceof ServiceInterruption)
            throw (ServiceInterruption)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        connection = t.getConnection();
        ps = t.getPreparedStatement();
        rs = t.getResultSet();
        rsmd = t.getResultSetMetaData();
        resultCols = t.getColumnNames();
      }
      catch (InterruptedException e)
      {
        cleanupParameters(params);
        t.interrupt();
        throw new ACFException("Interrupted: "+e.getMessage(),e,ACFException.INTERRUPTED);
      }
    }

    /** Get the next row from the resultset.
    *@return the immutable row description, or null if there is no such row.
    */
    public IResultRow getNextRow()
      throws ACFException, ServiceInterruption
    {
      if (maxResults == -1 || maxResults > 0)
      {
        IResultRow row = readNextResultRowViaThread(rs,rsmd,resultCols);
        if (row != null && maxResults != -1)
          maxResults--;
        return row;
      }
      return null;
    }

    /** Close this resultset.
    */
    public void close()
      throws ACFException, ServiceInterruption
    {
      ACFException rval = null;
      if (rs != null)
      {
        try
        {
          closeResultset(rs);
        }
        catch (ServiceInterruption e)
        {
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
        finally
        {
          rs = null;
        }
      }
      if (ps != null)
      {
        try
        {
          closePS(ps);
        }
        catch (ServiceInterruption e)
        {
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
        finally
        {
          ps = null;
        }
      }
      if (connection != null)
      {
        try
        {
          JDBCConnectionFactory.releaseConnection(connection);
        }
        catch (ServiceInterruption e)
        {
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
        finally
        {
          connection = null;
        }
      }
      if (params != null)
      {
        try
        {
          cleanupParameters(params);
          params = null;
        }
        catch (ACFException e)
        {
          if (rval == null || e.getErrorCode() == ACFException.INTERRUPTED)
            rval = e;
        }
      }
      if (rval != null)
        throw rval;

    }

  }

  protected class PreparedStatementQueryThread extends Thread
  {
    protected ArrayList params;
    protected String query;

    protected Connection connection = null;
    protected Throwable exception = null;
    protected PreparedStatement ps = null;
    protected ResultSet rs = null;
    protected ResultSetMetaData rsmd = null;
    protected String[] resultCols = null;

    public PreparedStatementQueryThread(String query, ArrayList params)
    {
      super();
      setDaemon(true);
      this.query = query;
      this.params = params;
    }

    public void run()
    {
      try
      {
        connection = JDBCConnectionFactory.getConnection(jdbcProvider,host,databaseName,userName,password);
        ps = connection.prepareStatement(query);
        loadPS(ps, params);
        rs = ps.executeQuery();
        rsmd = rs.getMetaData();
        resultCols = readColumnNames(rsmd);
      }
      catch (Throwable e)
      {
        this.exception = e;
        if (rs != null)
        {
          try
          {
            closeResultset(rs);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
          }
          finally
          {
            rs = null;
          }
        }
        if (ps != null)
        {
          try
          {
            closePS(ps);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
          }
          finally
          {
            ps = null;
          }
        }
        if (connection != null)
        {
          try
          {
            JDBCConnectionFactory.releaseConnection(connection);
          }
          catch (ServiceInterruption e2)
          {
          }
          catch (ACFException e2)
          {
            if (e2.getErrorCode() == ACFException.INTERRUPTED)
              this.exception = e2;
          }
          finally
          {
            connection = null;
          }
        }
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public Connection getConnection()
    {
      return connection;
    }

    public PreparedStatement getPreparedStatement()
    {
      return ps;
    }

    public ResultSet getResultSet()
    {
      return rs;
    }

    public ResultSetMetaData getResultSetMetaData()
    {
      return rsmd;
    }

    public String[] getColumnNames()
    {
      return resultCols;
    }
  }

}
