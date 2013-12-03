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

  // This implementation is a place-holder for the real one, which will likely fold in the pooling code
  // as we strip it out of OutputConnectorFactory.

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
    return OutputConnectorFactory.grabMultiple(threadContext,
      orderingKeys, classNames, configInfos, maxPoolSizes);
  }

  /** Get an output connector.
  * The connector is specified by an output connection object.
  *@param outputConnection is the output connection to base the connector instance on.
  */
  public IOutputConnector grab(IOutputConnection outputConnection)
    throws ManifoldCFException
  {
    return OutputConnectorFactory.grab(threadContext, outputConnection.getClassName(),
      outputConnection.getConfigParams(), outputConnection.getMaxConnections());
  }

  /** Release multiple output connectors.
  *@param connectors are the connector instances to release.
  */
  public void releaseMultiple(IOutputConnector[] connectors)
    throws ManifoldCFException
  {
    OutputConnectorFactory.releaseMultiple(connectors);
  }

  /** Release an output connector.
  *@param connector is the connector to release.
  */
  public void release(IOutputConnector connector)
    throws ManifoldCFException
  {
    OutputConnectorFactory.release(connector);
  }

  /** Idle notification for inactive output connector handles.
  * This method polls all inactive handles.
  */
  public void pollAllConnectors()
    throws ManifoldCFException
  {
    OutputConnectorFactory.pollAllConnectors(threadContext);
  }

  /** Clean up all open output connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  */
  public void closeAllConnectors()
    throws ManifoldCFException
  {
    OutputConnectorFactory.closeAllConnectors(threadContext);
  }

}
