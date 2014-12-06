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
package org.apache.manifoldcf.connectorcommon.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

/** Thread-local IThrottleGroups factory.
*/
public class ThrottleGroupsFactory
{
  public static final String _rcsid = "@(#)$Id$";

  // name to use in thread context pool of objects
  private final static String objectName = "_ThrottleGroups_";

  private ThrottleGroupsFactory()
  {
  }

  /** Make a connection throttle handle.
  *@param tc is the thread context.
  *@return the handle.
  */
  public static IThrottleGroups make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(objectName);
    if (o == null || !(o instanceof IThrottleGroups))
    {
      o = new org.apache.manifoldcf.connectorcommon.throttler.ThrottleGroups(tc);
      tc.save(objectName,o);
    }
    return (IThrottleGroups)o;
  }

  /** Class that polls throttler */
  protected static class ThrottlerPoll implements IPollingHook
  {
    public ThrottlerPoll()
    {
    }
    
    @Override
    public void doPoll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      IThrottleGroups connectionThrottler = ThrottleGroupsFactory.make(threadContext);
      connectionThrottler.poll();
    }
  }
  
  /** Register the throttle groups ManifoldCF service.
  */
  public static void register()
  {
    ManifoldCF.addShutdownHook(new ThrottlerShutdown());
    ManifoldCF.addPollingHook(new ThrottlerPoll());
  }

  /** Class that cleans up throttler on exit */
  protected static class ThrottlerShutdown implements IShutdownHook
  {
    public ThrottlerShutdown()
    {
    }
    
    @Override
    public void doCleanup(IThreadContext threadContext)
      throws ManifoldCFException
    {
      IThrottleGroups connectionThrottler = ThrottleGroupsFactory.make(threadContext);
      connectionThrottler.destroy();
    }
    
    /** Finalizer, which is designed to catch class unloading that tomcat 5.5 does.
    */
    protected void finalize()
      throws Throwable
    {
      try
      {
        doCleanup(ThreadContextFactory.make());
      }
      finally
      {
        super.finalize();
      }
    }

  }

}
