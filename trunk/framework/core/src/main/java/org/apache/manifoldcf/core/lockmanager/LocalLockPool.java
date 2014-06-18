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
package org.apache.manifoldcf.core.lockmanager;

import java.util.*;
import java.io.*;

/** Pool of local locks, designed to gate access within a single thread.
* Since it is within a single thread, synchronization is not necessary.
*/
public class LocalLockPool
{
  public static final String _rcsid = "@(#)$Id$";

  protected final Map<String,LocalLock> localLocks = new HashMap<String,LocalLock>();
  
  public LocalLockPool()
  {
  }
  
  public LocalLock getLocalLock(String lockKey)
  {
    LocalLock ll = localLocks.get(lockKey);
    if (ll == null)
    {
      ll = new LocalLock();
      localLocks.put(lockKey,ll);
    }
    return ll;
  }

  public void releaseLocalLock(String lockKey)
  {
    localLocks.remove(lockKey);
  }

  public Set<String> keySet()
  {
    return localLocks.keySet();
  }
}
