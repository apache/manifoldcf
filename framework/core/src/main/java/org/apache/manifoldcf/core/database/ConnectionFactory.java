/* $Id: ConnectionFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.core.jdbcpool.*;
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.util.*;
import java.sql.*;
import javax.naming.*;
import javax.sql.*;

/** This class creates a connection, and may at our discretion manage
* a connection pool someday.
*/
public class ConnectionFactory
{
  public static final String _rcsid = "@(#)$Id: ConnectionFactory.java 988245 2010-08-23 18:39:35Z kwright $";


  private static HashMap checkedOutConnections = new HashMap();

  private static PoolManager poolManager = new PoolManager();

  private ConnectionFactory()
  {
  }

  public static WrappedConnection getConnection(String jdbcUrl, String jdbcDriver, String database, String userName, String password,
    int maxDBConnections, boolean debug)
    throws ManifoldCFException
  {
    // Make sure database driver is registered
    try
    {
      Class.forName(jdbcDriver);
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Unable to load database driver: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
    
    ConnectionPoolManager cpm = poolManager.createPoolManager(debug);
    
    try
    {
      // Hope for a connection now
      WrappedConnection rval;
      ConnectionPool cp = cpm.getPool(database);
      if (cp == null)
      {
        cpm.addAlias(database, jdbcDriver, jdbcUrl,
          userName, password,
          maxDBConnections, 300000L);
        cp = cpm.getPool(database);
      }
      return getConnectionWithRetries(cp);
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (SQLException e)
    {
      throw new ManifoldCFException("Error getting connection: "+e.getMessage(),e,ManifoldCFException.DATABASE_CONNECTION_ERROR);
    }
    catch (ClassNotFoundException e)
    {
      throw new ManifoldCFException("Fatal error getting connection: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
    catch (InstantiationException e)
    {
      throw new ManifoldCFException("Fatal error getting connection: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
    catch (IllegalAccessException e)
    {
      throw new ManifoldCFException("Fatal error getting connection: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
  }

  public static void releaseConnection(WrappedConnection c)
    throws ManifoldCFException
  {
    c.release();
  }

  public static void flush()
  {
    if (poolManager != null)
      poolManager.flush();
  }
  
  public static void releaseAll()
  {
    if (poolManager != null)
      poolManager.releaseAll();
  }

  protected static WrappedConnection getConnectionWithRetries(ConnectionPool cp)
    throws SQLException, InterruptedException
  {
    // If we have a problem, we will wait a grand total of 30 seconds
    int retryCount = 3;
    while (true)
    {
      try
      {
        return cp.getConnection();
      }
      catch (SQLException e)
      {
        if (retryCount == 0)
          throw e;
        // Eat the exception and try again
        retryCount--;
      }
      // Ten seconds is a long time
      ManifoldCF.sleep(10000L);
    }

  }

  protected static void checkConnections(long currentTime)
  {
    synchronized (checkedOutConnections)
    {
      Iterator iter = checkedOutConnections.keySet().iterator();
      while (iter.hasNext())
      {
        Object key = iter.next();
        ConnectionTracker ct = (ConnectionTracker)checkedOutConnections.get(key);
        if (ct.hasExpired(currentTime))
          ct.printDetails();
      }
    }
  }

  /** This class abstracts from a connection pool, such that a static reference
  * to an instance of this class will describe the entire body of connections.
  * The finalizer for this class attempts to free all connections that are outstanding,
  * so that class unloading, as it is practiced under tomcat 5.5, will not leave dangling
  * connections around.
  */
  protected static class PoolManager
  {
    private Integer poolExistenceLock = new Integer(0);
    private ConnectionPoolManager _pool = null;
    
    private PoolManager()
    {
    }

    public ConnectionPoolManager createPoolManager(boolean debug)
      throws ManifoldCFException
    {
      synchronized (poolExistenceLock)
      {
        if (_pool != null)
          return _pool;
        _pool = new ConnectionPoolManager(100, debug);
        return _pool;
      }
    }

    public void releaseAll()
    {
      ConnectionPoolManager thisPool;
      synchronized (poolExistenceLock)
      {
        if (_pool == null)
          return;
        thisPool = _pool;
        _pool = null;
      }

      // Cleanup strategy: Some connections are still in use because they are being
      // used by non-worker threads that have been interrupted but haven't yet died.
      // Cleaning these up is a challenge.  For now I won't address this.
      
      thisPool.shutdown();
    }
    
    public void flush()
    {
      synchronized (poolExistenceLock)
      {
        if (_pool != null)
        {
          _pool.flush();
        }
      }
    }
    
      /*
      // Cleanup strategy is to close everything that can easily be closed, but leave around connections that are so busy that they will not close within a certain amount of
      // time.  To do that, we spin up a thread for each connection, which attempts to close that connection, and then wait until either 15 seconds passes, or all the threads
      // are finished.
      //
      // Under conditions of high load, or (more likely) when long-running exclusive operations like REINDEX are running, the 15 seconds may well be insufficient to acheive
      // thread shutdown.  In that case a message "LOG:  unexpected EOF on client connection" will appear for each dangling connection in the postgresql log.
      // This is not ideal, but is a compromise designed to permit speedy and relatively clean shutdown even under
      // difficult conditions.

      
      Enumeration enumeration = _pool.getPools();
      ArrayList connectionShutdownThreads = new ArrayList();
      while (enumeration.hasMoreElements())
      {
        ConnectionPool pool = (ConnectionPool)enumeration.nextElement();
        try
        {
          // The removeAllConnections() method did not work, probably because the cleanup was
          // delayed by their design.  So instead, we have to do everything the hard way.
          // If the calling logic is poorly behaved, there is a chance that an open connection will be
          // left hanging around after this call happens.  If so, postgresql log gets written
          // with: LOG:  unexpected EOF on client connection

          // System.err.println("There are currently "+Integer.toString(pool.size())+" connections in this pool");
          int count = pool.size();
          int i = 0;
          while (i < count)
          {
            com.bitmechanic.sql.PooledConnection p = (com.bitmechanic.sql.PooledConnection)pool.getConnection();
            ConnectionCloseThread t = new ConnectionCloseThread(p);
            t.start();
            connectionShutdownThreads.add(t);
            i++;
          }
          // System.err.println("Done closing connections.");
        }
        catch (Exception e)
        {
        }
      }

      int k = 0;
      while (k < 15)
      {
        int j = 0;
        while (j < connectionShutdownThreads.size())
        {
          ConnectionCloseThread t = (ConnectionCloseThread)connectionShutdownThreads.get(j);
          if (t.isAlive())
            break;
          j++;
        }
        if (j < connectionShutdownThreads.size())
        {
          try
          {
            ManifoldCF.sleep(1000L);
            k++;
            continue;
          }
          catch (InterruptedException e)
          {
            break;
          }
        }
        break;
      }

      // Some threads may still be running - but that can't be helped.
    }
    */

    // Protected methods and classes

    /** Finalizer method should attempt to close open connections.
    * This should get called when tomcat 5.5 unloads a web application.
    * A shutdown thread will also be registered, which will attempt to do the same, but
    * will be blocked from proceeding under Tomcat 5.5.  Between the two, however,
    * there's hope that the right things will take place.
    */
    /*
    protected void finalize()
      throws Throwable
    {
      try
      {
        // Release all the connections we can within 15 seconds
        releaseAll();
      }
      finally
      {
        super.finalize();
      }
    }
    */
  }

  /*
  protected static class ConnectionCloseThread extends Thread
  {
    protected com.bitmechanic.sql.PooledConnection connection;
    protected Throwable exception = null;

    public ConnectionCloseThread(com.bitmechanic.sql.PooledConnection connection)
    {
      super();
      setDaemon(true);
      this.connection = connection;
    }

    public void run()
    {
      try
      {
        // Call the shutdown method
        connection.run();
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
  */
  
  protected static class ConnectionTracker
  {
    protected Connection theConnection;
    protected long checkoutTime;
    protected Exception theTrace;

    public ConnectionTracker(Connection theConnection)
    {
      this.theConnection = theConnection;
      this.checkoutTime = System.currentTimeMillis();
      this.theTrace = new Exception("Stack trace");
    }

    public boolean hasExpired(long currentTime)
    {
      return (checkoutTime + 300000L < currentTime);
    }

    public void printDetails()
    {
      Logging.db.error("Connection handle may have been abandoned: "+theConnection.toString(),theTrace);
    }
  }
}

