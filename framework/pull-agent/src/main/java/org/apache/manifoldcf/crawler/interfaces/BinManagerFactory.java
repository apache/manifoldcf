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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;

/** Factory class for IBinManager.
*/
public class BinManagerFactory
{
  public static final String _rcsid = "@(#)$Id$";

  // Name
  protected final static String binManagerName = "_BinManager_";

  private BinManagerFactory()
  {
  }

  /** Create a bin manager handle.
  *@param threadContext is the thread context.
  *@return the handle.
  */
  public static IBinManager make(IThreadContext threadContext)
    throws ManifoldCFException
  {
    Object o = threadContext.get(binManagerName);
    if (o == null || !(o instanceof IBinManager))
    {
      IDBInterface database = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.crawler.bins.BinManager(database);
      threadContext.save(binManagerName,o);
    }
    return (IBinManager)o;
  }

}
