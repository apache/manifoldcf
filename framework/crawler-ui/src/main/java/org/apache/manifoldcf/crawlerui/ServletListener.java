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
package org.apache.manifoldcf.crawlerui;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import javax.servlet.*;

/** This class furnishes a servlet shutdown hook for ManifoldCF.  It should be referenced in the
* web.xml file for the application in order to do the right thing, however.
*/
public class ServletListener implements ServletContextListener
{
  public static final String _rcsid = "@(#)$Id$";

  protected IdleCleanupThread idleCleanupThread = null;

  public void contextInitialized(ServletContextEvent sce)
  {
    try
    {
      IThreadContext threadContext = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(threadContext);
      idleCleanupThread = new IdleCleanupThread();
      idleCleanupThread.start();
    }
    catch (ManifoldCFException e)
    {
      throw new RuntimeException("Could not initialize servlet; "+e.getMessage(),e);
    }
  }
  
  public void contextDestroyed(ServletContextEvent sce)
  {
    try
    {
      while (true)
      {
        if (idleCleanupThread == null)
          break;
        idleCleanupThread.interrupt();
        if (!idleCleanupThread.isAlive())
          idleCleanupThread = null;
      }
    }
    finally
    {
      ManifoldCF.cleanUpEnvironment(ThreadContextFactory.make());
    }
  }

}
