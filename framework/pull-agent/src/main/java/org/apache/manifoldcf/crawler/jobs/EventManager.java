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
import java.util.*;

/** This class manages the events table.
* A row in this table indicates that a specific event sequence is in progress.  For example, a login sequence for a specific web domain
* may be underway.  During the time that the event is taking place, no documents that depend on that event will be queued for processing.
*/
public class EventManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id: EventManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Field names
  public final static String eventNameField = "name";

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
        performCreate(map,null);
      }
      else
      {
        // Upgrade goes here if needed
      }

      // Index management goes here

      break;
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    beginTransaction();
    try
    {
      performDrop(null);
    }
    catch (ManifoldCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Prepare for restart.
  */
  public void restart()
    throws ManifoldCFException
  {
    // Delete all rows in this table.
    performDelete("",null,null);
  }

  /** Atomically create an event - and return false if the event already exists */
  public void createEvent(String eventName)
    throws ManifoldCFException
  {
    HashMap map = new HashMap();
    map.put(eventNameField,eventName);
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
