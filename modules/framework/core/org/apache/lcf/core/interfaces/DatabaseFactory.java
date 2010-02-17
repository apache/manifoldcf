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

/** This factory returns a database interface appropriate for
* a specified client connection.  The client or company name is
* passed into the factory, as well as a thread context.
*/
public class DatabaseFactory
{
	public static final String _rcsid = "@(#)$Id$";

	private final static String databaseInstancePrefix = "_Database:";

	private DatabaseFactory()
	{
	}

	/** Grab or create the correct instance of a database manager.
	*/
	public static IDatabase make(IThreadContext context, String databaseName, String userName, String password)
		throws MetacartaException
	{
		String dbName = databaseInstancePrefix + databaseName;
		Object x = context.get(dbName);
		if (x == null || !(x instanceof IDatabase))
		{
			// Create new database handle
			x = new org.apache.lcf.core.database.Database(context,databaseName,userName,password);
			context.save(dbName,x);
		}
		return (IDatabase)x;
	}
}
