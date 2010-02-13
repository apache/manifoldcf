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
package com.metacarta.crawler.connectors.meridio;

import java.io.*;
import java.util.HashMap;

import com.metacarta.core.interfaces.*;
import com.metacarta.crawler.connectors.meridio.meridiowrapper.MeridioTestWrapper;
import com.metacarta.crawler.connectors.meridio.DMDataSet.ACCESSCONTROL;

public class AddDocAcl
{
	public static final String _rcsid = "@(#)$Id$";

	protected static HashMap map;
	static
	{
	    map = new HashMap();
	    map.put("PROHIBIT",new Integer(0));
	    map.put("READ",new Integer(1));
	    map.put("AMEND",new Integer(2));
	    map.put("MANAGE",new Integer(3));
	}

	private AddDocAcl()
	{
	}


	public static void main(String[] args)
	{
		if (args.length != 7)
		{
			System.err.println("Usage: AddDocAcl <docurl> <recurl> <username> <password> <docid> <username> <permission>");
			System.err.println("where <permission> is 'PROHIBIT','READ','AMEND', or 'MANAGE'");
			System.exit(1);
		}

		try
		{
    			MeridioTestWrapper handle = new MeridioTestWrapper(args[0],args[1],args[2],args[3]);
			try
			{
				int docId = Integer.parseInt(args[4]);
				Integer perm = (Integer)map.get(args[6]);
				if (perm == null)
				    throw new Exception("Unknown permission type: "+args[6]);
				int permission = perm.intValue();

    				// Lookup the user
				long userId = handle.getUserIdFromName(args[5]);

				ACCESSCONTROL acl = new ACCESSCONTROL ();
		
				acl.setObjectId(docId);
				acl.setPermission(new Integer(permission).shortValue());        
				acl.setObjectType(new Integer(1).shortValue());		         // DOCUMENT = 1
				acl.setUserId(userId);

				handle.addAclToDocumentOrRecord(acl);
			}
			finally
			{
				handle.logout();
			}
			System.err.println("Successfully added document acl");
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}

}
