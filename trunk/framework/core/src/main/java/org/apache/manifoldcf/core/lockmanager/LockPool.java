/* $Id: LockPool.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Lock pool class. This is a pool of LockGate objects.
*/
public class LockPool
{
  public static final String _rcsid = "@(#)$Id: LockPool.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final Map<Object,LockGate> myLocks = new HashMap<Object,LockGate>();

  protected final LockObjectFactory factory;
  
  public LockPool(LockObjectFactory factory)
  {
    this.factory = factory;
  }
  
  public synchronized LockGate getObject(Object lockKey)
  {
    LockGate lg = myLocks.get(lockKey);
    if (lg == null)
    {
      LockObject lo = factory.newLockObject(this,lockKey);
      lg = new LockGate(lockKey,lo,this);
      myLocks.put(lockKey,lg);
    }
    return lg;
  }

  public synchronized void releaseObject(Object lockKey, LockGate lockGate)
  {
    lockGate.makeInvalid();
    myLocks.remove(lockKey);
  }
}
