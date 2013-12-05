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
package org.apache.manifoldcf.crawler.repositoryconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of IRepositoryConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class RepositoryConnectorPool implements IRepositoryConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  // This implementation is a place-holder for the real one, which will likely fold in the pooling code
  // as we strip it out of RepositoryConnectorFactory.

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public RepositoryConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple repository connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param repositoryConnections are the connections to use the build the connector instances.
  */
  @Override
  public IRepositoryConnector[] grabMultiple(String[] orderingKeys, IRepositoryConnection[] repositoryConnections)
    throws ManifoldCFException
  {
    // For now, use the RepositoryConnectorFactory method.  This will require us to extract info
    // from each repository connection, however.
    String[] classNames = new String[repositoryConnections.length];
    ConfigParams[] configInfos = new ConfigParams[repositoryConnections.length];
    int[] maxPoolSizes = new int[repositoryConnections.length];
    
    for (int i = 0; i < repositoryConnections.length; i++)
    {
      classNames[i] = repositoryConnections[i].getClassName();
      configInfos[i] = repositoryConnections[i].getConfigParams();
      maxPoolSizes[i] = repositoryConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, classNames, configInfos, maxPoolSizes);
  }

  /** Get a repository connector.
  * The connector is specified by a repository connection object.
  *@param authorityConnection is the repository connection to base the connector instance on.
  */
  @Override
  public IRepositoryConnector grab(IRepositoryConnection repositoryConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, repositoryConnection.getClassName(),
      repositoryConnection.getConfigParams(), repositoryConnection.getMaxConnections());
  }

  /** Release multiple repository connectors.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(IRepositoryConnector[] connectors)
    throws ManifoldCFException
  {
    localPool.releaseMultiple(connectors);
  }

  /** Release a repository connector.
  *@param connector is the connector to release.
  */
  @Override
  public void release(IRepositoryConnector connector)
    throws ManifoldCFException
  {
    localPool.release(connector);
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
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<IRepositoryConnector>
  {
    public LocalPool()
    {
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      IConnectorManager connectorManager = ConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }

    public IRepositoryConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,IRepositoryConnector.class,orderingKeys,classNames,configInfos,maxPoolSizes);
    }

  }

}
