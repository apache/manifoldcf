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

/** This class describes a local lock, which can have various nested levels
* of depth.
*/
public class LocalLock
{
  private int readCount = 0;
  private int writeCount = 0;
  private int nonExWriteCount = 0;

  public LocalLock()
  {
  }

  public boolean hasWriteLock()
  {
    return (writeCount > 0);
  }

  public boolean hasReadLock()
  {
    return (readCount > 0);
  }

  public boolean hasNonExWriteLock()
  {
    return (nonExWriteCount > 0);
  }

  public void incrementReadLocks()
  {
    readCount++;
  }

  public void incrementNonExWriteLocks()
  {
    nonExWriteCount++;
  }
  
  public void incrementWriteLocks()
  {
    writeCount++;
  }

  public void decrementReadLocks()
  {
    readCount--;
  }
  
  public void decrementNonExWriteLocks()
  {
    nonExWriteCount--;
  }

  public void decrementWriteLocks()
  {
    writeCount--;
  }
}
