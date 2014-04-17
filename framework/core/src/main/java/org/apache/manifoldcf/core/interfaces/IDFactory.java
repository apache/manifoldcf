/* $Id: IDFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The purpose of this class is to create global unique identifiers.  For performance, every JVM has a local pool of identifiers as well
* as there being a global system of identifier creation, which is also resilient against entire system restarts.
*/
public class IDFactory
{
  public static final String _rcsid = "@(#)$Id: IDFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  private static ArrayList idPool = new ArrayList();

  // The id algorithm depends on the clock.  We don't want to fetch too many; we'll
  // run the risk of a restart in the non-synchronized case beating the clock.
  private final static int poolSize = 100;

  /** This is the critical section name */
  private final static String criticalSectionName = "_IDFACTORY_";
  /** This is the global lock name */
  private final static String globalLockName = "_IDFACTORY_";
  /** This is the name of the global ID data object */
  private final static String globalIDDataName = "_IDFACTORY_";

  private IDFactory()
  {
  }

  public static String make(IThreadContext tc)
    throws ManifoldCFException
  {
    ILockManager lockManager = LockManagerFactory.make(tc);
    // Enter critical section before we look at the pool
    lockManager.enterWriteCriticalSection(criticalSectionName);
    try
    {
      // check the pool
      if (idPool.size() == 0)
      {
        // Pool was empty.  We need to grab more id's from the global resource.
        lockManager.enterWriteLock(globalLockName);
        try
        {
          // Read shared data
          byte[] idData = lockManager.readData(globalIDDataName);
          long _id;
          if (idData == null)
            _id = 0L;
          else
            _id = new Long(new String(idData, StandardCharsets.UTF_8)).longValue();
          
          int i = 0;
          while (i < poolSize)
          {
            long newid = System.currentTimeMillis();
            if (newid <= _id)
            {
              newid = _id + 1;
            }
            _id = newid;
            idPool.add(Long.toString(newid));
            i++;
          }

          lockManager.writeData(globalIDDataName,Long.toString(_id).getBytes(StandardCharsets.UTF_8));
        }
        finally
        {
          lockManager.leaveWriteLock(globalLockName);
        }
      }
      return (String)idPool.remove(idPool.size()-1);
    }
    finally
    {
      lockManager.leaveWriteCriticalSection(criticalSectionName);
    }
    
  }

}
