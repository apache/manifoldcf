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
package com.metacarta.agents.outputconnmgr;

import com.metacarta.core.interfaces.*;
import com.metacarta.agents.interfaces.*;
import com.metacarta.agents.interfaces.CacheKeyFactory;
import java.util.*;

/** Implementation of IOutputConnectorManager.
*/
public class OutputConnectorManager extends com.metacarta.core.database.BaseTable implements IOutputConnectorManager
{
        public static final String _rcsid = "@(#)$Id$";

        // Fields
        protected static final String descriptionField = "description";
        protected static final String classNameField = "classname";

        // Thread context
        protected IThreadContext threadContext;

        /** Constructor.
        *@param threadContext is the thread context.
        *@param database is the database handle.
        */
        public OutputConnectorManager(IThreadContext threadContext, IDBInterface database)
                throws MetacartaException
        {
                super(database,"outputconnectors");
                this.threadContext = threadContext;
        }


        /** Install or upgrade.
        */
        public void install()
                throws MetacartaException
        {
                beginTransaction();
                try
                {
                        Map existing = getTableSchema(null,null);
                        if (existing == null)
                        {
                                HashMap map = new HashMap();
                                map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
                                map.put(classNameField,new ColumnDescription("VARCHAR(255)",true,false,null,null,false));

                                performCreate(map,null);

                                // This index is here to enforce uniqueness
                                ArrayList list = new ArrayList();
                                list.add(descriptionField);
                                addTableIndex(true,list);
                        }
                }
                catch (MetacartaException e)
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


        /** Uninstall.  This also unregisters all connectors.
        */
        public void deinstall()
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());

                // First, go through all registered connectors.  This is all inside a transaction.
                beginTransaction();
                try
                {
                        IResultSet set = performQuery("SELECT "+classNameField+" FROM "+getTableName(),null,null,null);
                        int i = 0;
                        while (i < set.getRowCount())
                        {
                                IResultRow row = set.getRow(i++);
                                String className = row.getValue(classNameField).toString();
                                // Call the deinstall method
                                OutputConnectorFactory.deinstall(threadContext,className);
                        }
                        performDrop(invKeys);
                }
                catch (MetacartaException e)
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

        /** Register a new connector.
        * The connector's install method will also be called.
        *@param description is the description to use in the UI.
        *@param className is the class name.
        */
        public void registerConnector(String description, String className)
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());
                beginTransaction();
                try
                {
                        performLock();
                        // See if already there.
                        ArrayList params = new ArrayList();
                        params.add(className);
                        IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+classNameField+"=? FOR UPDATE",params,null,null);
                        HashMap map = new HashMap();
                        map.put(descriptionField,description);
                        if (set.getRowCount() == 0)
                        {
                                // Insert it into table first.
                                map.put(classNameField,className);
                                performInsert(map,invKeys);
                        }
                        else
                        {
                                performUpdate(map,"WHERE "+classNameField+"=?",params,invKeys);
                        }				

                        // Either way, we must do the install/upgrade itself.
                        OutputConnectorFactory.install(threadContext,className);
                }
                catch (MetacartaException e)
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

        /** Unregister a connector.
        * The connector's deinstall method will also be called.
        *@param description is the description to unregister.
        */
        public void unregisterConnector(String className)
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());
                beginTransaction();
                try
                {
                        // Uninstall first
                        OutputConnectorFactory.deinstall(threadContext,className);

                        removeConnector(className);
                }
                catch (MetacartaException e)
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

        /** Remove a connector.
        * Use this method when the connector doesn't seem to be in the
        * classpath, so deregistration cannot occur.
        *@param className is the connector class to remove.
        */
        public void removeConnector(String className)
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());
                ArrayList list = new ArrayList();
                list.add(className);
                performDelete("WHERE "+classNameField+"=?",list,invKeys);
        }

        /** Get ordered list of connectors.
        *@return a resultset with the columns "description" and "classname".
        * These will be ordered by description.
        */
        public IResultSet getConnectors()
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());

                return performQuery("SELECT "+descriptionField+" AS description,"+classNameField+" AS classname FROM "+
                        getTableName()+" ORDER BY "+descriptionField+" ASC",null,invKeys,null);
        }

        /** Get a description given a class name.
        *@param className is the class name.
        *@return the description, or null if the class is not registered.
        */
        public String getDescription(String className)
                throws MetacartaException
        {
                StringSet invKeys = new StringSet(getCacheKey());

                ArrayList list = new ArrayList();
                list.add(className);
                IResultSet set = performQuery("SELECT "+descriptionField+" FROM "+
                        getTableName()+" WHERE "+classNameField+"=?",list,invKeys,null);
                if (set.getRowCount() == 0)
                        return null;
                IResultRow row = set.getRow(0);
                return row.getValue(descriptionField).toString();
        }

        /** Check if a particular connector is installed or not.
        *@param className is the class name of the connector.
        *@return true if installed, false otherwise.
        */
        public boolean isInstalled(String className)
                throws MetacartaException
        {
                // Use the global table key; that's good enough because we don't expect stuff to change out from under very often.
                StringSet invKeys = new StringSet(getCacheKey());
                
                ArrayList list = new ArrayList();
                list.add(className);
                IResultSet set = performQuery("SELECT * FROM "+
                        getTableName()+" WHERE "+classNameField+"=?",list,invKeys,null);
                return set.getRowCount() > 0;
        }
                


        // Protected methods

        /** Get the cache key for the connector manager table.
        *@return the cache key
        */
        protected String getCacheKey()
        {
                return CacheKeyFactory.makeTableKey(null,getTableName(),getDBInterface().getDatabaseName());
        }

}
