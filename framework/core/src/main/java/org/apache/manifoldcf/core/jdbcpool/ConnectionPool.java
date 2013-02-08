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
package org.apache.manifoldcf.core.jdbcpool;

import java.sql.*;
import javax.naming.*;
import javax.sql.*;
import java.util.*;
import org.apache.manifoldcf.core.system.Logging;

/** The class that defines a connection pool.
*/
public class ConnectionPool
{
  public static final String _rcsid = "@(#)$Id$";

  protected final String dbURL;
  protected final String userName;
  protected final String password;
  protected volatile int freePointer;
  protected volatile int activeConnections;
  protected volatile boolean closed;
  protected final Connection[] freeConnections;
  protected final long[] connectionCleanupTimeouts;
  protected final long expiration;
  
  protected final boolean debug;
  
  protected final Set<WrappedConnection> outstandingConnections = new HashSet<WrappedConnection>();
  
  /** Constructor */
  public ConnectionPool(String dbURL, String userName, String password, int maxConnections, long expiration, boolean debug)
  {
    this.dbURL = dbURL;
    this.userName = userName;
    this.password = password;
    this.freeConnections = new Connection[maxConnections];
    this.connectionCleanupTimeouts = new long[maxConnections];
    this.freePointer = 0;
    this.activeConnections = 0;
    this.closed = false;
    this.expiration = expiration;
    this.debug = debug;
  }
  
  /** Obtain a connection from the pool.
  * This will wait until a connection is free, if the pool is already completely tapped.
  * The connection is returned by the "close" operation, executed on the connection.
  * (This requires us to wrap the actual connection object).
  */
  public WrappedConnection getConnection()
    throws SQLException, InterruptedException
  {
    Exception instantiationException;
    if (debug)
      instantiationException = new Exception("Possibly leaked db connection");
    else
      instantiationException = null;
    Connection rval = null;
    while (true)
    {
      synchronized (this)
      {
        if (freePointer > 0)
        {
          if (closed)
            throw new InterruptedException("Pool already closed");
          rval = freeConnections[--freePointer];
          freeConnections[freePointer] = null;
          break;
        }
        if (activeConnections == freeConnections.length)
        {
          // If properly configured, we really shouldn't be getting here.
          if (debug)
          {
            synchronized (outstandingConnections)
            {
              Logging.db.warn("Out of db connections, list of outstanding ones follows.");
              for (WrappedConnection c : outstandingConnections)
              {
                Logging.db.warn("Found a possibly leaked db connection",c.getInstantiationException());
              }
            }
          }
          // Wait until kicked; we hope something will free up...
          this.wait();
          continue;
        }
        // Increment active connection counter, because we're about to mint a new connection, and break out of our loop
        activeConnections++;
        break;
      }
    }

    boolean returnedValue = true;
    try
    {
      if (rval == null)
      {
        if (userName != null)
          rval = DriverManager.getConnection(dbURL, userName, password);
        else
          rval = DriverManager.getConnection(dbURL);
      }

      WrappedConnection wc = new WrappedConnection(this,rval,instantiationException);
      if (debug)
      {
        synchronized (outstandingConnections)
        {
          outstandingConnections.add(wc);
        }
      }
      return wc;
    }
    catch (Error e)
    {
      returnedValue = false;
      throw e;
    }
    catch (RuntimeException e)
    {
      returnedValue = false;
      throw e;
    }
    catch (SQLException e)
    {
      returnedValue = false;
      throw e;
    }
    finally
    {
      if (!returnedValue)
      {
        // We didn't finish.  Restore the pool to the correct form.
        // Note: We should always be able to just return any current connection to the pool.  This is
        // safe because we reserved a slot when we decided to create the connection (if that's what
        // we did), or we just used a connection that was already allocated.  Either way, we can put
        // it into the pool.
        if (rval != null)
        {
          release(rval);
        }
      }
    }
  }
  
  /** Close down the pool.
  */
  public synchronized void closePool()
  {
    for (int i = 0 ; i < freePointer ; i++)
    {
      try
      {
        freeConnections[i].close();
      }
      catch (SQLException e)
      {
        Logging.db.warn("Error closing pooled connection: "+e.getMessage(),e);
      }
      freeConnections[i] = null;
    }
    freePointer = 0;
    closed = true;
    notifyAll();
  }
  
  /** Clean up expired connections.
  */
  public synchronized void cleanupExpiredConnections(long currentTime)
  {
    int i = 0;
    while (i < freePointer)
    {
      if (connectionCleanupTimeouts[i] <= currentTime)
      {
        Connection c = freeConnections[i];
        freeConnections[i] = null;
        freePointer--;
        activeConnections--;
        if (freePointer == i)
        {
          freeConnections[i] = null;
        }
        else
        {
          freeConnections[i] = freeConnections[freePointer];
          connectionCleanupTimeouts[i] = connectionCleanupTimeouts[freePointer];
          freeConnections[freePointer] = null;
        }
        try
        {
         c.close();
        }
        catch (SQLException e)
        {
          Logging.db.warn("Error closing pooled connection: "+e.getMessage(),e);
        }
      }
      else
        i++;
    }
  }
  
  public void releaseConnection(WrappedConnection connection)
  {

    if (debug)
    {
      synchronized (outstandingConnections)
      {
        outstandingConnections.remove(connection);
      }
    }

    release(connection.getConnection());
  }
  
  protected void release(Connection c)
  {
    synchronized (this)
    {
      freeConnections[freePointer] = c;
      connectionCleanupTimeouts[freePointer] = System.currentTimeMillis() + expiration;
      freePointer++;
      notifyAll();
    }
    
  }
  
}


