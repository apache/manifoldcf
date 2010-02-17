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
package org.apache.lcf.crawler.interfaces;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.system.LCF;

/** Repository connection manager factory.
*/
public class RepositoryConnectionManagerFactory
{
	public static final String _rcsid = "@(#)$Id$";

	// name to use in thread context pool of objects
	private final static String objectName = "_RepoConnectionMgr_";

	private RepositoryConnectionManagerFactory()
	{
	}

	/** Make a repository connection manager handle.
	*@param tc is the thread context.
	*@return the handle.
	*/
	public static IRepositoryConnectionManager make(IThreadContext tc)
		throws LCFException
	{
		Object o = tc.get(objectName);
		if (o == null || !(o instanceof IRepositoryConnectionManager))
		{
			IDBInterface database = DBInterfaceFactory.make(tc,
				LCF.getMasterDatabaseName(),
				LCF.getMasterDatabaseUsername(),
				LCF.getMasterDatabasePassword());

			o = new org.apache.lcf.crawler.repository.RepositoryConnectionManager(tc,database);
			tc.save(objectName,o);
		}
		return (IRepositoryConnectionManager)o;
	}

}
