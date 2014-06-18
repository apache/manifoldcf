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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

/** This is the factory class for authority group manager objects.
*/
public class AuthorityGroupManagerFactory
{
  // name to use in thread context pool of objects
  private final static String objectName = "_AuthGroupMgr_";

  private AuthorityGroupManagerFactory()
  {
  }

  /** Make an authority connection manager handle.
  *@param tc is the thread context.
  *@return the handle.
  */
  public static IAuthorityGroupManager make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(objectName);
    if (o == null || !(o instanceof IAuthorityGroupManager))
    {
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.authorities.authgroups.AuthorityGroupManager(tc,database);
      tc.save(objectName,o);
    }
    return (IAuthorityGroupManager)o;
  }

}
