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
  */
  @Override
  public void obtainConnectionPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    throttler.obtainConnectionPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime);
  }
  
  /** Release permission to use a connection. This presumes that obtainConnectionPermission()
  * was called earlier in the same thread and was successful.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param currentTime is the current time, in ms. since epoch.
  */
  @Override
  public void releaseConnectionPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    throttler.releaseConnectionPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime);
  }
  
  /** Get permission to fetch a document.  This grants permission to start
  * fetching a single document.  When done (or aborting), call
  * releaseFetchDocumentPermission() to note the completion of the document
  * fetch activity.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names describing this documemnt.
  *@param currentTime is the current time, in ms. since epoch.
  */
  @Override
  public void obtainFetchDocumentPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    throttler.obtainFetchDocumentPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime);
  }
  
  /** Release permission to fetch a document.  Call this only when you
  * called obtainFetchDocumentPermission() successfully earlier in the same
  * thread.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names describing this documemnt.
  *@param currentTime is the current time, in ms. since epoch.
  */
  @Override
  public void releaseFetchDocumentPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    throttler.releaseFetchDocumentPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime);
  }
  
  /** Obtain permission to read a block of bytes.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names describing this documemnt.
  *@param currentTime is the current time, in ms. since epoch.
  *@param byteCount is the number of bytes to get permissions to read.
  */
  @Override
  public void obtainReadPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime, int byteCount)
    throws ManifoldCFException
  {
    throttler.obtainReadPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime, byteCount);
  }
    
  /** Note the completion of the read of a block of bytes.  Call this after
  * obtainReadPermission() was successfully called in the same thread.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names describing this documemnt.
  *@param currentTime is the current time, in ms. since epoch.
  *@param origByteCount is the originally requested number of bytes to get permissions to read.
  *@param actualByteCount is the number of bytes actually read.
  */
  @Override
  public void releaseReadPermission(String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime, int origByteCount, int actualByteCount)
    throws ManifoldCFException
  {
    throttler.releaseReadPermission(threadContext, throttleGroup,
      throttleSpec, binNames, currentTime, origByteCount, actualByteCount);
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
