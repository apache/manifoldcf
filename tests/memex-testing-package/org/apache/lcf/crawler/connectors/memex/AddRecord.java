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
package org.apache.lcf.crawler.connectors.memex;

import org.apache.lcf.core.interfaces.*;
import java.util.*;

public class AddRecord
{
	public static final String _rcsid = "@(#)$Id$";

	private AddRecord()
	{
	}


	public static void main(String[] args)
	{
		if (args.length < 6 && (args.length & 1) != 0)
		{
			System.err.println("Usage: AddRecord <servername> <port> <username> <password> <virtualserver> <database> <name_1> <value_1> ... <name_N> <value_N>");
			System.exit(1);
		}

		try
		{
			MemexSupport handle = new MemexSupport(args[2],args[3],args[0],args[1]);
			try
			{
				Hashtable fields = setupFields(args,6);
				String id = handle.addRecord(fields,args[4],args[5]);
				UTF8Stdout.print(id);
			}
			finally
			{
				handle.close();
			}
			System.err.println("Successfully added");
		}
		catch (MetacartaException e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}

	protected static Hashtable setupFields(String[] args, int startingIndex)
	{
		Hashtable rval = new Hashtable();
		while (startingIndex < args.length)
		{
			String name = args[startingIndex];
			String value = args[startingIndex+1];
			startingIndex += 2;
			rval.put(name,value);
		}
		return rval;
	}
}
