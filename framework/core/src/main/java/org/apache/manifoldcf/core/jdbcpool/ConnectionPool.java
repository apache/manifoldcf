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

  protected String dbURL;
  protected String userName;
  protected String password;
  protected volatile int freePointer;
  protected volatile int activeConnections;
  protected volatile boolean closed;
  protected Connection[] freeConnections;
  protected long[] connectionCleanupTimeouts;
  protected long expiration;
  
  protected final static boolean debug = true;
  
  protected List<WrappedConnection> outstandingConnections = new ArrayList<WrappedConnection>();
  
  /** Constructor */
  public ConnectionPool(String dbURL, String userName, String password, int maxConnections, long expiration)
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
    while (true)
    {
      synchronized (this)
      {
        if (freePointer > 0)
        {
          if (closed)
            throw new InterruptedException("Pool already closed");
          Connection rval = freeConnections[--freePointer];
          freeConnections[freePointer] = null;
          WrappedConnection rval3 = new WrappedConnection(this,rval,instantiationException);
          if (debug)
            outstandingConnections.add(rval3);
          return rval3;
        }
        if (activeConnections == freeConnections.length)
        {
          // If properly configured, we really shouldn't be getting here.
          if (debug)
          {
            Logging.db.warn("Out of db connections, list of outstanding ones follows.");
            for (int i = 0; i < outstandingConnections.size(); i++)
            {
              Logging.db.warn("Found a possibly leaked db connection",outstandingConnections.get(i).getInstantiationException());
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
    
    // Create a new connection.  If we fail at this we need to restore the number of active connections, so catch any failures
    Connection rval2 = null;
    try
    {
      if (userName != null)
        rval2 = DriverManager.getConnection(dbURL, userName, password);
      else
        rval2 = DriverManager.getConnection(dbURL);
    }
    finally
    {
      if (rval2 == null)
        activeConnections--;
    }
    WrappedConnection rval4 = new WrappedConnection(this,rval2,instantiationException);
    if (debug)
    {
      synchronized (this)
      {
        outstandingConnections.add(rval4);
      }
    }
    return rval4;
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
        try
        {
          freeConnections[i].close();
        }
        catch (SQLException e)
        {
          Logging.db.warn("Error closing pooled connection: "+e.getMessage(),e);
        }
        freePointer--;
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
      }
      else
        i++;
    }
  }
  
  public synchronized void releaseConnection(WrappedConnection connection)
  {
    freeConnections[freePointer] = connection.getConnection();
    connectionCleanupTimeouts[freePointer] = System.currentTimeMillis() + expiration;
    freePointer++;
    if (debug)
      outstandingConnections.remove(connection);
    notifyAll();
  }
  
}


