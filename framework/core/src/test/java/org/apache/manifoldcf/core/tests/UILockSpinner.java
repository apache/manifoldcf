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

package org.apache.manifoldcf.core.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

public class UILockSpinner
{

  protected final static String lockName = "TESTLOCK";

  public static void main(String[] argv)
    throws Exception
  {
    IThreadContext threadContext = ThreadContextFactory.make();
    ManifoldCF.initializeEnvironment(threadContext);

    // Create a thread context object.
    ILockManager lockManager = LockManagerFactory.make(threadContext);

    System.out.println("Starting test");

    int i = 0;
    while (i < 100000)
    {
      if ((i % 100) == 0)
        System.out.println("UI iteration "+Integer.toString(i));

      // This thread is a writer.
      lockManager.enterWriteLock(lockName);
      try
      {
        Thread.sleep(10);
      }
      finally
      {
        lockManager.leaveWriteLock(lockName);
      }

      Thread.sleep(1000);
      i++;
    }

    System.out.println("Done test - no hang");

  }

}
