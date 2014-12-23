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
package org.apache.manifoldcf.crawler.notificationconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of INotificationConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class NotificationConnectorPool implements INotificationConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  // This implementation is a place-holder for the real one, which will likely fold in the pooling code
  // as we strip it out of RepositoryConnectorFactory.

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public NotificationConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple notification connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param notificationConnections are the connections to use the build the connector instances.
  */
  @Override
  public INotificationConnector[] grabMultiple(String[] orderingKeys, INotificationConnection[] notificationConnections)
    throws ManifoldCFException
  {
    // For now, use the NotificationConnectorFactory method.  This will require us to extract info
    // from each notification connection, however.
    String[] connectionNames = new String[notificationConnections.length];
    String[] classNames = new String[notificationConnections.length];
    ConfigParams[] configInfos = new ConfigParams[notificationConnections.length];
    int[] maxPoolSizes = new int[notificationConnections.length];
    
    for (int i = 0; i < notificationConnections.length; i++)
    {
      connectionNames[i] = notificationConnections[i].getName();
      classNames[i] = notificationConnections[i].getClassName();
      configInfos[i] = notificationConnections[i].getConfigParams();
      maxPoolSizes[i] = notificationConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, connectionNames, classNames, configInfos, maxPoolSizes);
  }

  /** Get a notification connector.
  * The connector is specified by a notification connection object.
  *@param authorityConnection is the notification connection to base the connector instance on.
  */
  @Override
  public INotificationConnector grab(INotificationConnection notificationConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, notificationConnection.getName(), notificationConnection.getClassName(),
      notificationConnection.getConfigParams(), notificationConnection.getMaxConnections());
  }

  /** Release multiple notification connectors.
  *@param connections are the connections describing the instances to release.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(INotificationConnection[] connections, INotificationConnector[] connectors)
    throws ManifoldCFException
  {
    String[] connectionNames = new String[connections.length];
    for (int i = 0; i < connections.length; i++)
    {
      connectionNames[i] = connections[i].getName();
    }
    localPool.releaseMultiple(threadContext, connectionNames, connectors);
  }

  /** Release a notification connector.
  *@param connection is the connection describing the instance to release.
  *@param connector is the connector to release.
  */
  @Override
  public void release(INotificationConnection connection, INotificationConnector connector)
    throws ManifoldCFException
  {
    localPool.release(threadContext, connection.getName(), connector);
  }

  /** Idle notification for inactive repository connector handles.
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

  /** Clean up all open repository connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  */
  @Override
  public void closeAllConnectors()
    throws ManifoldCFException
  {
    localPool.closeAllConnectors(threadContext);
  }

  /** Actual static mapping connector pool */
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<INotificationConnector>
  {
    public LocalPool()
    {
      super("_NOTIFICATIONCONNECTORPOOL_");
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      INotificationConnectorManager connectorManager = NotificationConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }

    @Override
    protected boolean isConnectionNameValid(IThreadContext tc, String connectionName)
      throws ManifoldCFException
    {
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      return connectionManager.load(connectionName) != null;
    }

    public INotificationConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String[] connectionNames, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,INotificationConnector.class,orderingKeys,connectionNames,classNames,configInfos,maxPoolSizes);
    }

  }

}
