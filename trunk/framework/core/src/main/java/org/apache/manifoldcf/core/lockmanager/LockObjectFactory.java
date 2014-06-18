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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;
import java.io.*;

/** Base factory for lock objects.  This will be extended to
* support different kinds of lock objects.
*/
public class LockObjectFactory
{
  public static final String _rcsid = "@(#)$Id$";

  public LockObjectFactory()
  {
  }
  
  public LockObject newLockObject(LockPool lockPool, Object lockKey)
  {
    return new LockObject(lockPool, lockKey);
  }
}

