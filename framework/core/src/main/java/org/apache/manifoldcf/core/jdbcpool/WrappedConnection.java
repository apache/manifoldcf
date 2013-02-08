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
import org.apache.manifoldcf.core.system.Logging;

/** The class that represents a connection from a pool.
*/
public class WrappedConnection
{
  public static final String _rcsid = "@(#)$Id$";

  protected Connection connection;
  protected ConnectionPool owner;
  /** Exception, to keep track of where the connection was allocated */
  protected Exception instantiationException;
  
  /** Constructor */
  public WrappedConnection(ConnectionPool owner, Connection connection)
  {
    this(owner,connection,null);
  }
  
  /** Constructor */
  public WrappedConnection(ConnectionPool owner, Connection connection, Exception instantiationException)
  {
    this.owner = owner;
    this.connection = connection;
    this.instantiationException = instantiationException;
  }
  
  /** Get the JDBC connection object.
  */
  public Connection getConnection()
  {
    return connection;
  }
  
  /** Release the object into its pool.
  */
  public void release()
  {
    owner.releaseConnection(this);
    this.connection = null;
  }
  
  /** Get instantiation exception.
  */
  public Exception getInstantiationException()
  {
    return instantiationException;
  }
  
}


