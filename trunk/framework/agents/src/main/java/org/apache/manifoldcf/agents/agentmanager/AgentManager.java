/* $Id: AgentManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.agentmanager;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import java.util.*;

/** This is the implementation of IAgentManager.
 * 
 * <br><br>
 * <b>agents</b>
 * <table border="1" cellpadding="3" cellspacing="0">
 * <tr class="TableHeadingColor">
 * <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
 * <tr><td>classname</td><td>VARCHAR(255)</td><td>Primary Key</td></tr>
 * </table>
 * <br><br>
 * 
 */
public class AgentManager extends org.apache.manifoldcf.core.database.BaseTable implements IAgentManager
{
  public static final String _rcsid = "@(#)$Id: AgentManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // Fields
  protected final static String classNameField = "classname";

  // Thread context
  protected IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  *@param database is the database instance.
  */
  public AgentManager(IThreadContext threadContext, IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"agents");
    this.threadContext = threadContext;
  }

  /** Install or upgrade.
  */
  public void install()
    throws ManifoldCFException
  {
    // We always use an outer loop, in case the upgrade will need it.
    while (true)
    {
      // Check if table is already present
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(classNameField,new ColumnDescription("VARCHAR(255)",true,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Any required upgrade code goes here.
      }

      // Any index creation goes here.

      break;
    }
  }

  /** Uninstall.  Also uninstalls all remaining agents.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    // Since we are uninstalling agents, better do this inside a transaction
    beginTransaction();
    try
    {
      // Uninstall everything remaining
      IResultSet set = performQuery("SELECT * FROM "+getTableName(),null,null,null);
      int i = 0;
      while (i < set.getRowCount())
      {
        IResultRow row = set.getRow(i++);
        String className = row.getValue(classNameField).toString();
        IAgent agent = AgentFactory.make(className);
        agent.deinstall(threadContext);
      }
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

  /** Register an agent.
  *@param className is the class.
  */
  public void registerAgent(String className)
    throws ManifoldCFException
  {
    // Do in a transaction, so the installation is atomic
    beginTransaction();
    try
    {
      // See if already registered, if so just upgrade
      ArrayList params = new ArrayList();
      params.add(className);
      IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+classNameField+"=?",params,null,null);
      if (set.getRowCount() == 0)
      {
        // Try to add to the table first
        HashMap map = new HashMap();
        map.put(classNameField,className);
        performInsert(map,null);
      }
      // In any case, call the install/upgrade method
      IAgent agent = AgentFactory.make(className);
      agent.install(threadContext);
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


  /** Unregister an agent.
  *@param className is the class to unregister.
  */
  public void unregisterAgent(String className)
    throws ManifoldCFException
  {
    // Do in a transaction, so the installation is atomic
    beginTransaction();
    try
    {
      // First, deregister agent
      IAgent agent = AgentFactory.make(className);
      agent.deinstall(threadContext);

      // Remove from table
      removeAgent(className);
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

  /** Remove an agent.
  * Use this when the agent cannot be invoked.  The agent becomes unavailable,
  * but its schema is not cleaned up.
  *@param className is the class to remove.
  */
  public void removeAgent(String className)
    throws ManifoldCFException
  {
    // Remove from table
    ArrayList list = new ArrayList();
    list.add(className);
    performDelete("WHERE "+classNameField+"=?",list,null);
  }

  /** Get a list of all registered agent class names.
  *@return the classnames in an array.
  */
  public String[] getAllAgents()
    throws ManifoldCFException
  {
    IResultSet set = performQuery("SELECT * FROM "+getTableName(),null,null,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i);
      rval[i] = row.getValue(classNameField).toString();
      i++;
    }
    return rval;
  }

}
