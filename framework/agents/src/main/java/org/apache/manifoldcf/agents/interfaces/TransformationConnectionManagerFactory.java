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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.system.ManifoldCF;

import java.util.*;

/** Transformation connection manager factory.
*/
public class TransformationConnectionManagerFactory
{
  public static final String _rcsid = "@(#)$Id$";

  // name to use in thread context pool of objects
  private final static String objectName = "_TransformationConnectionMgr_";

  private TransformationConnectionManagerFactory()
  {
  }

  /** Make a transformation connection manager handle.
  *@param tc is the thread context.
  *@return the handle.
  */
  public static ITransformationConnectionManager make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(objectName);
    if (o == null || !(o instanceof ITransformationConnectionManager))
    {
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.agents.transformationconnection.TransformationConnectionManager(tc,database);
      tc.save(objectName,o);
    }
    return (ITransformationConnectionManager)o;
  }

  /** Compile a list of all pertinent activities, across all existing transformation connections.
  *@param tc is the thread context.
  *@return the sorted list of transformation connection activities.
  */
  public static String[] getAllTransformationActivities(IThreadContext tc)
    throws ManifoldCFException
  {
    ITransformationConnectionManager manager = make(tc);
    ITransformationConnection[] connections = manager.getAllConnections();
    Set<String> map = new HashSet<String>();
    for (ITransformationConnection connection : connections)
    {
      String connectionName = connection.getName();
      String[] activities = TransformationConnectorFactory.getActivitiesList(tc,connection.getClassName());
      if (activities != null)
      {
        for (String baseActivity : activities)
        {
          String activity = ManifoldCF.qualifyTransformationActivityName(baseActivity, connectionName);
          map.add(activity);
        }
      }
    }
    String[] rval = new String[map.size()];
    int i = 0;
    Iterator<String> iter = map.iterator();
    while (iter.hasNext())
    {
      rval[i++] = iter.next();
    }
    java.util.Arrays.sort(rval);
    return rval;
  }
}
