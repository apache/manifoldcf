/* $Id: EventManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;

/** This class manages the events table.
* A row in this table indicates that a specific event sequence is in progress.  For example, a login sequence for a specific web domain
* may be underway.  During the time that the event is taking place, no documents that depend on that event will be queued for processing.
* 
* <br><br>
* <b>events</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>name</td><td>VARCHAR(255)</td><td>Primary Key</td></tr>
* <tr><td>processid</td><td>VARCHAR(16)</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class EventManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: EventManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Field names
  public final static String eventNameField = "name";
  public final static String processIDField = "processid";
  
  /** Constructor.
  *@param database is the database handle.
  */
  public EventManager(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"events");
  }

  /** Install or upgrade this table.
  */
  public void install()
    throws ManifoldCFException
  {
    // Standard practice: outer loop for installs
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(eventNameField,new ColumnDescription("VARCHAR(255)",true,false,null,null,false));
        map.put(processIDField,new ColumnDescription("VARCHAR(16)",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade goes here if needed
      }

      // Index management goes here
      IndexDescription processIDIndex = new IndexDescription(false,new String[]{processIDField});
      // Get rid of unused indexes
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (processIDIndex != null && id.equals(processIDIndex))
          processIDIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Build missing indexes

      if (processIDIndex != null)
        performAddIndex(null,processIDIndex);

      break;
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Prepare for restart.
  *@param processID is the processID to restart.
  */
  public void restart(String processID)
    throws ManifoldCFException
  {
    // Delete all rows in this table matching the processID
    ArrayList list = new ArrayList();
    String query = buildConjunctionClause(list,new ClauseDescription[]{
      new UnitaryClause(processIDField,processID)});
    performDelete("WHERE "+query,list,null);
  }

  /** Clean up after all processIDs.
  */
  public void restart()
    throws ManifoldCFException
  {
    performDelete("",null,null);
  }
  
  /** Restart cluster.
  */
  public void restartCluster()
    throws ManifoldCFException
  {
    // Does nothing
  }
  
  /** Atomically create an event - and return false if the event already exists */
  public void createEvent(String eventName, String processID)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(eventNameField,eventName);
    map.put(processIDField,processID);
    performInsert(map,null);
  }

  /** Destroy an event */
  public void destroyEvent(String eventName)
    throws ManifoldCFException
  {
    ArrayList list = new ArrayList();
    list.add(eventName);
    performDelete("WHERE "+eventNameField+"=?",list,null);
  }
}
