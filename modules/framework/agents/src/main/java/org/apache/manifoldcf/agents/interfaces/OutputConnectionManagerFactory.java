/* $Id: OutputConnectionManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Repository connection manager factory.
*/
public class OutputConnectionManagerFactory
{
  public static final String _rcsid = "@(#)$Id: OutputConnectionManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  // name to use in thread context pool of objects
  private final static String objectName = "_OutputConnectionMgr_";

  private OutputConnectionManagerFactory()
  {
  }

  /** Make an output connection manager handle.
  *@param tc is the thread context.
  *@return the handle.
  */
  public static IOutputConnectionManager make(IThreadContext tc)
    throws ManifoldCFException
  {
    Object o = tc.get(objectName);
    if (o == null || !(o instanceof IOutputConnectionManager))
    {
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());

      o = new org.apache.manifoldcf.agents.outputconnection.OutputConnectionManager(tc,database);
      tc.save(objectName,o);
    }
    return (IOutputConnectionManager)o;
  }

  /** Compile a list of all pertinent activities, across all existing output connections.
  *@param tc is the thread context.
  *@return the sorted list of output connection activities.
  */
  public static String[] getAllOutputActivities(IThreadContext tc)
    throws ManifoldCFException
  {
    IOutputConnectionManager manager = make(tc);
    IOutputConnection[] connections = manager.getAllConnections();
    HashMap map = new HashMap();
    int i = 0;
    while (i < connections.length)
    {
      IOutputConnection connection = connections[i++];
      String connectionName = connection.getName();
      String[] activities = OutputConnectorFactory.getActivitiesList(tc,connection.getClassName());
      int j = 0;
      while (j < activities.length)
      {
        String activity = activities[j++] + " ("+connectionName+")";
        map.put(activity,activity);
      }
    }
    String[] rval = new String[map.size()];
    i = 0;
    Iterator iter = map.keySet().iterator();
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(rval);
    return rval;
  }
}
