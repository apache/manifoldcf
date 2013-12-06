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
package org.apache.manifoldcf.authorities.authorityconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of IAuthorityConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class AuthorityConnectorPool implements IAuthorityConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  // This implementation is a place-holder for the real one, which will likely fold in the pooling code
  // as we strip it out of AuthorityConnectorFactory.

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public AuthorityConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple authority connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param authorityConnections are the connections to use the build the connector instances.
  */
  @Override
  public IAuthorityConnector[] grabMultiple(String[] orderingKeys, IAuthorityConnection[] authorityConnections)
    throws ManifoldCFException
  {
    // For now, use the AuthorityConnectorFactory method.  This will require us to extract info
    // from each authority connection, however.
    String[] connectionNames = new String[authorityConnections.length];
    String[] classNames = new String[authorityConnections.length];
    ConfigParams[] configInfos = new ConfigParams[authorityConnections.length];
    int[] maxPoolSizes = new int[authorityConnections.length];
    
    for (int i = 0; i < authorityConnections.length; i++)
    {
      connectionNames[i] = authorityConnections[i].getName();
      classNames[i] = authorityConnections[i].getClassName();
      configInfos[i] = authorityConnections[i].getConfigParams();
      maxPoolSizes[i] = authorityConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, connectionNames, classNames, configInfos, maxPoolSizes);
  }

  /** Get an authority connector.
  * The connector is specified by an authority connection object.
  *@param authorityConnection is the authority connection to base the connector instance on.
  */
  @Override
  public IAuthorityConnector grab(IAuthorityConnection authorityConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, authorityConnection.getName(), authorityConnection.getClassName(),
      authorityConnection.getConfigParams(), authorityConnection.getMaxConnections());
  }

  /** Release multiple authority connectors.
  *@param connections are the connections describing the instances to release.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(IAuthorityConnection[] connections, IAuthorityConnector[] connectors)
    throws ManifoldCFException
  {
    String[] connectionNames = new String[connections.length];
    for (int i = 0; i < connections.length; i++)
    {
      connectionNames[i] = connections[i].getName();
    }
    localPool.releaseMultiple(threadContext, connectionNames, connectors);
  }

  /** Release an output connector.
  *@param connection is the connection describing the instance to release.
  *@param connector is the connector to release.
  */
  @Override
  public void release(IAuthorityConnection connection, IAuthorityConnector connector)
    throws ManifoldCFException
  {
    localPool.release(threadContext, connection.getName(), connector);
  }

  /** Idle notification for inactive authority connector handles.
  * This method polls all inactive handles.
  */
  @Override
  public void pollAllConnectors()
    throws ManifoldCFException
  {
    localPool.pollAllConnectors(threadContext);
  }

  /** Flush only those connector handles that are currently unused.
  */
  @Override
  public void flushUnusedConnectors()
    throws ManifoldCFException
  {
    localPool.flushUnusedConnectors(threadContext);
  }

  /** Clean up all open authority connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  */
  @Override
  public void closeAllConnectors()
    throws ManifoldCFException
  {
    localPool.closeAllConnectors(threadContext);
  }

  /** Actual static output connector pool */
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<IAuthorityConnector>
  {
    public LocalPool()
    {
      super("_AUTHORITYCONNECTORPOOL_");
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      IAuthorityConnectorManager connectorManager = AuthorityConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }
    
    @Override
    protected boolean isConnectionNameValid(IThreadContext tc, String connectionName)
      throws ManifoldCFException
    {
      IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
      return connectionManager.load(connectionName) != null;
    }

    public IAuthorityConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String connectionNames[], String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,IAuthorityConnector.class,orderingKeys,connectionNames,classNames,configInfos,maxPoolSizes);
    }

  }

}
