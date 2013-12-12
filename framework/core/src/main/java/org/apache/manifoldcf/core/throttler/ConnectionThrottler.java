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
package org.apache.manifoldcf.core.throttler;

import org.apache.manifoldcf.core.interfaces.*;

/** An implementation of IConnectionThrottler, which establishes a JVM-wide
* pool of throttlers that can be used as a resource by any connector that needs
* it.
*/
public class ConnectionThrottler implements IConnectionThrottler
{
  public static final String _rcsid = "@(#)$Id$";

  /** The thread context */
  protected final IThreadContext threadContext;
  
  // Parameters of the current connection
  
  protected String throttleGroup = null;
  protected IThrottleSpec throttleSpec = null;
  protected String[] binNames = null;
  
  
  /** The actual static pool */
  protected final static Throttler throttler = new Throttler();
  
  /** Constructor */
  public ConnectionThrottler(IThreadContext threadContext)
    throws ManifoldCFException
  {
    this.threadContext = threadContext;
  }
  
  /** Get permission to use a connection, which is described by the passed array of bin names.
  * The connection can be used multiple
  * times until the releaseConnectionPermission() method is called.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  *@param currentTime is the current time, in ms. since epoch.
  *@return the fetch throttler to use when performing fetches from this connection.
  */
  @Override
  public IFetchThrottler obtainConnectionPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    // MHL
    return null;
  }
  
  /** Release permission to use a connection. This presumes that obtainConnectionPermission()
  * was called earlier in the same thread and was successful.
  *@param currentTime is the current time, in ms. since epoch.
  */
  @Override
  public void releaseConnectionPermission(long currentTime)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Poll periodically.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    throttler.poll(threadContext);
  }
  
  /** Free unused resources.
  */
  @Override
  public void freeUnusedResources()
    throws ManifoldCFException
  {
    throttler.freeUnusedResources(threadContext);
  }
  
  /** Shut down throttler permanently.
  */
  @Override
  public void destroy()
    throws ManifoldCFException
  {
    throttler.destroy(threadContext);
  }

}
