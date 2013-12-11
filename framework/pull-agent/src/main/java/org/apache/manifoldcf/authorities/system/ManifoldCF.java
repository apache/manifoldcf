/* $Id: ManifoldCF.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.io.*;
import java.util.*;

public class ManifoldCF extends org.apache.manifoldcf.core.system.ManifoldCF
{
  // Initialization needed flag
  protected static boolean authoritiesInitialized = false;
  
  // Threads
  protected static IdleCleanupThread idleCleanupThread = null;
  protected static AuthCheckThread[] authCheckThreads = null;
  protected static MappingThread[] mappingThreads = null;

  // Number of auth check threads
  protected static int numAuthCheckThreads = 0;
  // Number of mapping threads
  protected static int numMappingThreads = 0;
  
  protected static final String authCheckThreadCountProperty = "org.apache.manifoldcf.authorityservice.threads";
  protected static final String mappingThreadCountProperty = "org.apache.manifoldcf.authorityservice.mappingthreads";

  // Request queue
  protected static RequestQueue<AuthRequest> requestQueue = null;
  // Mapping request queue
  protected static RequestQueue<MappingRequest> mappingRequestQueue = null;
  
  /** Initialize environment.
  */
  public static void initializeEnvironment(IThreadContext tc)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.core.system.ManifoldCF.initializeEnvironment(tc);
      org.apache.manifoldcf.authorities.system.ManifoldCF.localInitialize(tc);
    }
  }

  public static void cleanUpEnvironment(IThreadContext tc)
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.authorities.system.ManifoldCF.localCleanup(tc);
      org.apache.manifoldcf.core.system.ManifoldCF.cleanUpEnvironment(tc);
    }
  }

  public static void localInitialize(IThreadContext tc)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      if (authoritiesInitialized)
        return;

      Logging.initializeLoggers();
      Logging.setLogLevels(tc);
      authoritiesInitialized = true;
    }
  }
  
  public static void localCleanup(IThreadContext tc)
  {
    // Since pools are a shared resource, we clean them up only
    // when we are certain nothing else is using them in the JVM.
    try
    {
      AuthorityConnectorPoolFactory.make(tc).closeAllConnectors();
    }
    catch (ManifoldCFException e)
    {
      if (Logging.authorityService != null)
        Logging.authorityService.warn("Exception closing authority connection pool: "+e.getMessage(),e);
    }
    try
    {
      MappingConnectorPoolFactory.make(tc).closeAllConnectors();
    }
    catch (ManifoldCFException e)
    {
      if (Logging.authorityService != null)
        Logging.authorityService.warn("Exception closing mapping connection pool: "+e.getMessage(),e);
    }
  }
  
  /** Install all the authority manager system tables.
  *@param threadcontext is the thread context.
  */
  public static void installSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    IAuthorizationDomainManager domainMgr = AuthorizationDomainManagerFactory.make(threadcontext);
    IAuthorityGroupManager groupMgr = AuthorityGroupManagerFactory.make(threadcontext);
    IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadcontext);
    IAuthorityConnectionManager authConnMgr = AuthorityConnectionManagerFactory.make(threadcontext);
    IMappingConnectorManager mappingConnectorMgr = MappingConnectorManagerFactory.make(threadcontext);
    IMappingConnectionManager mappingConnectionMgr = MappingConnectionManagerFactory.make(threadcontext);

    domainMgr.install();
    connMgr.install();
    mappingConnectorMgr.install();
    groupMgr.install();
    authConnMgr.install();
    mappingConnectionMgr.install();
  }

  /** Uninstall all the authority manager system tables.
  *@param threadcontext is the thread context.
  */
  public static void deinstallSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    IAuthorizationDomainManager domainMgr = AuthorizationDomainManagerFactory.make(threadcontext);
    IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadcontext);
    IAuthorityGroupManager groupMgr = AuthorityGroupManagerFactory.make(threadcontext);
    IAuthorityConnectionManager authConnMgr = AuthorityConnectionManagerFactory.make(threadcontext);
    IMappingConnectorManager mappingConnectorMgr = MappingConnectorManagerFactory.make(threadcontext);
    IMappingConnectionManager mappingConnectionMgr = MappingConnectionManagerFactory.make(threadcontext);

    mappingConnectionMgr.deinstall();
    authConnMgr.deinstall();
    groupMgr.deinstall();
    mappingConnectorMgr.deinstall();
    connMgr.deinstall();
    domainMgr.deinstall();
  }

  /** Start the authority system.
  */
  public static void startSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Read any parameters
    numAuthCheckThreads = LockManagerFactory.getIntProperty(threadContext, authCheckThreadCountProperty, 10);
    if (numAuthCheckThreads < 1 || numAuthCheckThreads > 100)
      throw new ManifoldCFException("Illegal value for the number of auth check threads");

    numMappingThreads = LockManagerFactory.getIntProperty(threadContext, mappingThreadCountProperty, 10);
    if (numMappingThreads < 1 || numMappingThreads > 100)
      throw new ManifoldCFException("Illegal value for the number of mapping threads");

    // Start up threads
    idleCleanupThread = new IdleCleanupThread();
    idleCleanupThread.start();

    requestQueue = new RequestQueue<AuthRequest>();
    mappingRequestQueue = new RequestQueue<MappingRequest>();

    authCheckThreads = new AuthCheckThread[numAuthCheckThreads];
    for (int i = 0; i < numAuthCheckThreads; i++)
    {
      authCheckThreads[i] = new AuthCheckThread(Integer.toString(i),requestQueue);
      authCheckThreads[i].start();
    }
    
    mappingThreads = new MappingThread[numMappingThreads];
    for (int i = 0; i < numMappingThreads; i++)
    {
      mappingThreads[i] = new MappingThread(Integer.toString(i),mappingRequestQueue);
      mappingThreads[i].start();
    }

  }

  /** Shut down the authority system.
  */
  public static void stopSystem(IThreadContext threadContext)
    throws ManifoldCFException
  {

    while (idleCleanupThread != null || authCheckThreads != null || mappingThreads != null)
    {
      if (idleCleanupThread != null)
      {
        idleCleanupThread.interrupt();
      }
      if (authCheckThreads != null)
      {
        for (int i = 0; i < authCheckThreads.length; i++)
        {
          Thread authCheckThread = authCheckThreads[i];
          if (authCheckThread != null)
            authCheckThread.interrupt();
        }
      }
      if (mappingThreads != null)
      {
        for (int i = 0; i < mappingThreads.length; i++)
        {
          Thread mappingThread = mappingThreads[i];
          if (mappingThread != null)
            mappingThread.interrupt();
        }
      }
      
      if (idleCleanupThread != null)
      {
        if (!idleCleanupThread.isAlive())
          idleCleanupThread = null;
      }
      if (authCheckThreads != null)
      {
        boolean isAlive = false;
        for (int i = 0; i < authCheckThreads.length; i++)
        {
          Thread authCheckThread = authCheckThreads[i];
          if (authCheckThread != null)
          {
            if (!authCheckThread.isAlive())
              authCheckThreads[i] = null;
            else
              isAlive = true;
          }
          i++;
        }
        if (!isAlive)
          authCheckThreads = null;
      }

      if (mappingThreads != null)
      {
        boolean isAlive = false;
        for (int i = 0; i < mappingThreads.length; i++)
        {
          Thread mappingThread = mappingThreads[i];
          if (mappingThread != null)
          {
            if (!mappingThread.isAlive())
              mappingThreads[i] = null;
            else
              isAlive = true;
          }
          i++;
        }
        if (!isAlive)
          mappingThreads = null;
      }

      try
      {
        ManifoldCF.sleep(1000);
      }
      catch (InterruptedException e)
      {
      }
    }

    // Release all authority connectors
    AuthorityConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
    numAuthCheckThreads = 0;
    requestQueue = null;
    MappingConnectorPoolFactory.make(threadContext).flushUnusedConnectors();
    numMappingThreads = 0;
    mappingRequestQueue = null;
  }

  /** Get the current request queue */
  public static RequestQueue<AuthRequest> getRequestQueue()
  {
    return requestQueue;
  }

  /** Get the current mapping request queue */
  public static RequestQueue<MappingRequest> getMappingRequestQueue()
  {
    return mappingRequestQueue;
  }
  
}

