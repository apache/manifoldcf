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
package org.apache.lcf.core.interfaces;

/** This is the factory class for an IDBInterface.
*/
public class DBInterfaceFactory
{
        public static final String _rcsid = "@(#)$Id$";

        private final static String dbinterfaceInstancePrefix = "_DBInterface:";

        private DBInterfaceFactory()
        {
        }

        public static IDBInterface make(IThreadContext context, String databaseName, String userName, String password)
                throws LCFException
        {
                String dbName = dbinterfaceInstancePrefix + databaseName;
                Object x = context.get(dbName);
                if (x == null || !(x instanceof IDBInterface))
                {
                        // Create new database handle
                        // x = new org.apache.lcf.core.database.DBInterfaceMySQL(context,databaseName,userName,password);
                        x = new org.apache.lcf.core.database.DBInterfacePostgreSQL(context,databaseName,userName,password);
                        context.save(dbName,x);
                }
                return (IDBInterface)x;

        }

}
