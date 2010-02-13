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
package com.metacarta.core.interfaces;

public class LockManagerFactory
{
	public static final String _rcsid = "@(#)$Id$";

	private final static String lockManager = "_LockManager_";

	private LockManagerFactory()
	{
	}

	/** Instantiate a lock manager.
	* This should be thread specific (so that locks can nest properly in the same
	* thread).
	*/
	public static ILockManager make(IThreadContext context)
		throws MetacartaException
	{
		Object x = context.get(lockManager);
		if (x == null || !(x instanceof ILockManager))
		{
			x = new com.metacarta.core.lockmanager.LockManager();
			context.save(lockManager,x);
		}
		return (ILockManager)x;
	}

}

