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
package org.apache.manifoldcf.agents.outputconnectorpool;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.util.*;
import java.io.*;

/** An implementation of IOutputConnectorPool.
* Coordination and allocation among cluster members is managed within. 
* These objects are thread-local, so do not share them among threads.
*/
public class OutputConnectorPool implements IOutputConnectorPool
{
  public static final String _rcsid = "@(#)$Id$";

  /** Local connector pool */
  protected final static LocalPool localPool = new LocalPool();

  // How global connector allocation works:
  // (1) There is a lock-manager "service" associated with this connector pool.  This allows us to clean
  // up after local pools that have died without being released.  There's one service instance per local pool,
  // and thus one service instance per JVM.  NOTE WELL: This means that we need distinct process id's
  // for non-agent services as well!!  How do we do that??
  // A: -D always overrides processID, but in addition there's a default describing the kind of process it is,
  // e.g. "" for agents, "AUTH" for authority service, "API" for api service, "UI" for crawler UI, "CL" for
  // command line.
  // Next question; how does the process ID get set for these?
  // A: Good question...
  // Alternative: SINCE the pools are in-memory-only and static, maybe we can just mint a unique ID for
  // each pool on each instantiation.
  // Problem: Guaranteeing uniqueness
  // Solution: Zookeeper sequential nodes will do that for us, but is there a local/file equivalent?  Must be, but lock manager
  // needs extension, clearly.  But given that, it should be possible to have a globally-unique, transient "pool ID".
  // The transient globally-unique ID seems like the best solution.
  // (2) Each local pool knows how many connector instances of each type (keyed by class name and config info) there
  // are.
  // (3) Each local pool/connector instance type has a local authorization count.  This is the amount it's
  // allowed to actually keep.  If the pool has more connectors of a type than the local authorization count permits,
  // then every connector release operation will destroy the released connector until the local authorization count
  // is met.
  // (4) Each local pool/connector instance type needs a global variable describing how many CURRENT instances
  // the local pool has allocated.  This is a transient value which should automatically go to zero if the service becomes inactive.
  // The lock manager will need to be extended in order to provide this functionality - essentially, transient service-associated data.
  // There also needs to be a fast way to sum the data for all active services (but that does not need to be a lock-manager primitive)

  /** Thread context */
  protected final IThreadContext threadContext;
  
  /** Constructor */
  public OutputConnectorPool(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get multiple output connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  *@param orderingKeys are the keys which determine in what order the connectors are obtained.
  *@param outputConnections are the connections to use the build the connector instances.
  */
  @Override
  public IOutputConnector[] grabMultiple(String[] orderingKeys, IOutputConnection[] outputConnections)
    throws ManifoldCFException
  {
    // For now, use the OutputConnectorFactory method.  This will require us to extract info
    // from each output connection, however.
    String[] classNames = new String[outputConnections.length];
    ConfigParams[] configInfos = new ConfigParams[outputConnections.length];
    int[] maxPoolSizes = new int[outputConnections.length];
    
    for (int i = 0; i < outputConnections.length; i++)
    {
      classNames[i] = outputConnections[i].getClassName();
      configInfos[i] = outputConnections[i].getConfigParams();
      maxPoolSizes[i] = outputConnections[i].getMaxConnections();
    }
    return localPool.grabMultiple(threadContext,
      orderingKeys, classNames, configInfos, maxPoolSizes);
  }

  /** Get an output connector.
  * The connector is specified by an output connection object.
  *@param outputConnection is the output connection to base the connector instance on.
  */
  @Override
  public IOutputConnector grab(IOutputConnection outputConnection)
    throws ManifoldCFException
  {
    return localPool.grab(threadContext, outputConnection.getClassName(),
      outputConnection.getConfigParams(), outputConnection.getMaxConnections());
  }

  /** Release multiple output connectors.
  *@param connectors are the connector instances to release.
  */
  @Override
  public void releaseMultiple(IOutputConnector[] connectors)
    throws ManifoldCFException
  {
    localPool.releaseMultiple(connectors);
  }

  /** Release an output connector.
  *@param connector is the connector to release.
  */
  @Override
  public void release(IOutputConnector connector)
    throws ManifoldCFException
  {
    localPool.release(connector);
  }

  /** Idle notification for inactive output connector handles.
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
  protected static class LocalPool extends org.apache.manifoldcf.core.connectorpool.ConnectorPool<IOutputConnector>
  {
    public LocalPool()
    {
    }
    
    @Override
    protected boolean isInstalled(IThreadContext tc, String className)
      throws ManifoldCFException
    {
      IOutputConnectorManager connectorManager = OutputConnectorManagerFactory.make(tc);
      return connectorManager.isInstalled(className);
    }

    public IOutputConnector[] grabMultiple(IThreadContext tc, String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
      throws ManifoldCFException
    {
      return grabMultiple(tc,IOutputConnector.class,orderingKeys,classNames,configInfos,maxPoolSizes);
    }

  }
  
}
