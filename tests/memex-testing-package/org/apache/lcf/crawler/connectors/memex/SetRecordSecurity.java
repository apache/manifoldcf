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

public class SetRecordSecurity
{
	public static final String _rcsid = "@(#)$Id$";

	private SetRecordSecurity()
	{
	}


	public static void main(String[] args)
	{
		if (args.length < 8)
		{
			System.err.println("Usage: SetRecordSecurity <servername> <port> <username> <password> <id> <covert_token_count> <protected_token_count> <protected_message>");
			System.err.println("              <covert_user/group_1> ... <covert_user/group_N>");
			System.err.println("              <protected_user/group_1> ... <protected_user/group_N>");
			System.exit(1);
		}

		try
		{
			MemexSupport handle = new MemexSupport(args[2],args[3],args[0],args[1]);
			String id = args[4];
			int clockCount = Integer.parseInt(args[5]);
			int plockCount = Integer.parseInt(args[6]);
			String pMessage = args[7];
			try
			{
				String[] clockSecurityValues = setupSecurityValues(args,8,clockCount);
				String[] plockSecurityValues = setupSecurityValues(args,8+clockCount,plockCount);
				if (clockSecurityValues != null)
					handle.setRecordSecurity(id,clockSecurityValues);
				if (plockSecurityValues != null)
					handle.setRecordSecurity(id,plockSecurityValues,pMessage);
			}
			finally
			{
				handle.close();
			}
			System.err.println("Successfully set security");
		}
		catch (MetacartaException e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}

	protected static String[] setupSecurityValues(String[] args, int startingIndex, int count)
	{
		String[] rval = new String[count];
		int i = 0;
		while (i < count)
		{
			rval[i++] = args[startingIndex++];
		}
		return rval;
	}
}
