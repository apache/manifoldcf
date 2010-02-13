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
package com.metacarta.authorities;

import java.io.*;
import com.metacarta.core.interfaces.*;
import com.metacarta.authorities.interfaces.*;
import com.metacarta.authorities.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class DeleteAuthorityConnection
{
	public static final String _rcsid = "@(#)$Id$";

	private DeleteAuthorityConnection()
	{
	}


	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.err.println("Usage: DeleteAuthorityConnection <connection_name>");
			System.exit(1);
		}

		String connectionName = args[0];
		try
		{
		        Metacarta.initializeEnvironment();
			IThreadContext tc = ThreadContextFactory.make();
			IAuthorityConnectionManager mgr = AuthorityConnectionManagerFactory.make(tc);
			mgr.delete(connectionName);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}



		
}
