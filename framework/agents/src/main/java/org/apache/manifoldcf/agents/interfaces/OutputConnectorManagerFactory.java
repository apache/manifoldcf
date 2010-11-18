/* $Id: OutputConnectorManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

/** Factory for output connector manager.
*/
public class OutputConnectorManagerFactory
{
  public static final String _rcsid = "@(#)$Id: OutputConnectorManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  protected static final String connMgr = "_OutputConnectorManager_";

  private OutputConnectorManagerFactory()
  {
  }

  /** Construct an output connector manager.
  *@param tc is the thread context.
  *@return the output connector manager handle.
  */
  public static IOutputConnectorManager make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(connMgr);
    if (o == null || !(o instanceof IOutputConnectorManager))
    {

      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.agents.outputconnmgr.OutputConnectorManager(tc,database);
      tc.save(connMgr,o);
    }
    return (IOutputConnectorManager)o;
  }

}
