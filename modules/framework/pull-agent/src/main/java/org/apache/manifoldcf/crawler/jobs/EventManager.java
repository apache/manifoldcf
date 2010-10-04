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

/** This class manages the prerequisite event table.
* An existing row in this table indicates that an event has not yet taken place that *must* take place before certain jobqueue entries
* may be queued.  Non-existence of a row conversely indicates that nothing should prevent any dependent jobqueue entries from being
* queued.
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
    throws ACFException
  {
    super(database,"events");
  }

  /** Install or upgrade this table.
  */
  public void install()
    throws ACFException
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
    throws ACFException
  {
    beginTransaction();
    try
    {
      performDrop(null);
    }
    catch (ACFException e)
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
    throws ACFException
  {
    // Delete all rows in this table.
    performDelete("",null,null);
  }

  /** Atomically create an event - and return false if the event already exists */
  public void createEvent(String eventName)
    throws ACFException
  {
    HashMap map = new HashMap();
    map.put(eventNameField,eventName);
    performInsert(map,null);
  }

  /** Destroy an event */
  public void destroyEvent(String eventName)
    throws ACFException
  {
    ArrayList list = new ArrayList();
    list.add(eventName);
    performDelete("WHERE "+eventNameField+"=?",list,null);
  }
}
