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

/** Mapping connector pool manager factory.
*/
public class MappingConnectorPoolFactory
{
  public static final String _rcsid = "@(#)$Id$";

  // name to use in thread context pool of objects
  private final static String objectName = "_MappingConnectorPoolMgr_";

  private MappingConnectorPoolFactory()
  {
  }

  /** Make a mapping connector pool handle.
  *@param tc is the thread context.
  *@return the handle.
  */
  public static IMappingConnectorPool make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(objectName);
    if (o == null || !(o instanceof IMappingConnectorPool))
    {
      o = new org.apache.manifoldcf.authorities.mappingconnectorpool.MappingConnectorPool(tc);
      tc.save(objectName,o);
    }
    return (IMappingConnectorPool)o;
  }

}
