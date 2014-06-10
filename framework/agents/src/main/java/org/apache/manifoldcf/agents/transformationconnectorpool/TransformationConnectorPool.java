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
package org.apache.manifoldcf.agents.transformationconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of ITransformationConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class TransformationConnectorPool implements ITransformationConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public TransformationConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple transformation connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param transformationConnections are the connections to use the build the connector instances.
  */
  @Override
  public ITransformationConnector[] grabMultiple(String[] orderingKeys, ITransformationConnection[] transformationConnections)
    throws ManifoldCFException
  {
    // For now, use the TransformationConnectorFactory method.  This will require us to extract info
    // from each output connection, however.
    String[] connectionNames = new String[transformationConnections.length];
    String[] classNames = new String[transformationConnections.length];
    ConfigParams[] configInfos = new ConfigParams[transformationConnections.length];
    int[] maxPoolSizes = new int[transformationConnections.length];
    
    for (int i = 0; i < transformationConnections.length; i++)
    {
      connectionNames[i] = transformationConnections[i].getName();
      classNames[i] = transformationConnections[i].getClassName();
      configInfos[i] = transformationConnections[i].getConfigParams();
      maxPoolSizes[i] = transformationConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, connectionNames, classNames, configInfos, maxPoolSizes);
  }

  /** Get a transformation connector.
  * The connector is specified by a transformation connection object.
  *@param transformationConnection is the transformation connection to base the connector instance on.
  */
  @Override
  public ITransformationConnector grab(ITransformationConnection transformationConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, transformationConnection.getName(), transformationConnection.getClassName(),
      transformationConnection.getConfigParams(), transformationConnection.getMaxConnections());
  }

  /** Release multiple transformation connectors.
  *@param connections are the connections describing the instances to release.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(ITransformationConnection[] connections, ITransformationConnector[] connectors)
    throws ManifoldCFException
  {
    String[] connectionNames = new String[connections.length];
    for (int i = 0; i < connections.length; i++)
    {
      connectionNames[i] = connections[i].getName();
    }
    localPool.releaseMultiple(threadContext, connectionNames, connectors);
  }

  /** Release a transformation connector.
  *@param connection is the connection describing the instance to release.
  *@param connector is the connector to release.
  */
  @Override
  public void release(ITransformationConnection connection, ITransformationConnector connector)
    throws ManifoldCFException
  {
    localPool.release(threadContext,connection.getName(),connector);
  }

  /** Idle notification for inactive transformation connector handles.
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

  /** Clean up all open output connector handles.
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
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<ITransformationConnector>
  {
    public LocalPool()
    {
      super("_TRANSFORMATIONCONNECTORPOOL_");
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      ITransformationConnectorManager connectorManager = TransformationConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }

    @Override
    protected boolean isConnectionNameValid(IThreadContext tc, String connectionName)
      throws ManifoldCFException
    {
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      return connectionManager.load(connectionName) != null;
    }

    public ITransformationConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String[] connectionNames, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,ITransformationConnector.class,orderingKeys,connectionNames,classNames,configInfos,maxPoolSizes);
    }

  }
  
}
