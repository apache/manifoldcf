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
package org.apache.lcf.authorities.interfaces;

import org.apache.lcf.core.interfaces.*;
import java.util.*;
import org.apache.lcf.authorities.system.LCF;

/** This is the factory class for authority connection manager objects.
*/
public class AuthorityConnectionManagerFactory
{
	// name to use in thread context pool of objects
	private final static String objectName = "_AuthConnectionMgr_";

	private AuthorityConnectionManagerFactory()
	{
	}

	/** Make an authority connection manager handle.
	*@param tc is the thread context.
	*@return the handle.
	*/
	public static IAuthorityConnectionManager make(IThreadContext tc)
		throws LCFException
	{
		Object o = tc.get(objectName);
		if (o == null || !(o instanceof IAuthorityConnectionManager))
		{
			IDBInterface database = DBInterfaceFactory.make(tc,
				LCF.getMasterDatabaseName(),
				LCF.getMasterDatabaseUsername(),
				LCF.getMasterDatabasePassword());

			o = new org.apache.lcf.authorities.authority.AuthorityConnectionManager(tc,database);
			tc.save(objectName,o);
		}
		return (IAuthorityConnectionManager)o;
	}

}
