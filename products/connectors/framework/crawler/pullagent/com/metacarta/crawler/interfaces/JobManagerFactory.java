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
package com.metacarta.crawler.interfaces;

import com.metacarta.core.interfaces.*;
import com.metacarta.crawler.system.*;

/** Factory class for IJobManager.
*/
public class JobManagerFactory
{
	public static final String _rcsid = "@(#)$Id$";

	// Name
	protected final static String jobManagerName = "_JobManager_";

	private JobManagerFactory()
	{
	}

	/** Create a job manager handle.
	*@param threadContext is the thread context.
	*@return the handle.
	*/
	public static IJobManager make(IThreadContext threadContext)
		throws MetacartaException
	{
		Object o = threadContext.get(jobManagerName);
		if (o == null || !(o instanceof IJobManager))
		{
			IDBInterface database = DBInterfaceFactory.make(threadContext,
				Metacarta.getMasterDatabaseName(),
				Metacarta.getMasterDatabaseUsername(),
				Metacarta.getMasterDatabasePassword());

			o = new com.metacarta.crawler.jobs.JobManager(threadContext,database);
			threadContext.save(jobManagerName,o);
		}
		return (IJobManager)o;
	}

}
