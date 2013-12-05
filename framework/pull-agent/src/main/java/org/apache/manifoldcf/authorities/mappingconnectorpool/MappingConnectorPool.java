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
package org.apache.manifoldcf.authorities.mappingconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of IMappingConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class MappingConnectorPool implements IMappingConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  // This implementation is a place-holder for the real one, which will likely fold in the pooling code
  // as we strip it out of MappingConnectorFactory.

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public MappingConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple mapping connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param mappingConnections are the connections to use the build the connector instances.
  */
  @Override
  public IMappingConnector[] grabMultiple(String[] orderingKeys, IMappingConnection[] mappingConnections)
    throws ManifoldCFException
  {
    // For now, use the MappingConnectorFactory method.  This will require us to extract info
    // from each mapping connection, however.
    String[] classNames = new String[mappingConnections.length];
    ConfigParams[] configInfos = new ConfigParams[mappingConnections.length];
    int[] maxPoolSizes = new int[mappingConnections.length];
    
    for (int i = 0; i < mappingConnections.length; i++)
    {
      classNames[i] = mappingConnections[i].getClassName();
      configInfos[i] = mappingConnections[i].getConfigParams();
      maxPoolSizes[i] = mappingConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, classNames, configInfos, maxPoolSizes);
  }

  /** Get a mapping connector.
  * The connector is specified by an mapping connection object.
  *@param mappingConnection is the mapping connection to base the connector instance on.
  */
  @Override
  public IMappingConnector grab(IMappingConnection mappingConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, mappingConnection.getClassName(),
      mappingConnection.getConfigParams(), mappingConnection.getMaxConnections());
  }

  /** Release multiple mapping connectors.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(IMappingConnector[] connectors)
    throws ManifoldCFException
  {
    localPool.releaseMultiple(connectors);
  }

  /** Release a mapping connector.
  *@param connector is the connector to release.
  */
  @Override
  public void release(IMappingConnector connector)
    throws ManifoldCFException
  {
    localPool.release(connector);
  }

  /** Idle notification for inactive mapping connector handles.
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

  /** Clean up all open mapping connector handles.
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
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<IMappingConnector>
  {
    public LocalPool()
    {
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      IMappingConnectorManager connectorManager = MappingConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }

    public IMappingConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,IMappingConnector.class,orderingKeys,classNames,configInfos,maxPoolSizes);
    }

  }

}
