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

import com.metacarta.core.interfaces.*;
import com.metacarta.crawler.connectors.meridio.meridiowrapper.MeridioTestWrapper;

import com.metacarta.crawler.connectors.meridio.DMDataSet.*;
import com.meridio.www.MeridioDMWS.*;
import com.meridio.www.MeridioDMWS.holders.*;

import com.metacarta.crawler.connectors.meridio.RMDataSet.*;
import com.meridio.www.MeridioRMWS.*;

public class AddDoc
{
	public static final String _rcsid = "@(#)$Id$";

	private AddDoc()
	{
	}


	public static void main(String[] args)
	{
		if (args.length < 8 || args.length > 9)
		{
			System.err.println("Usage: AddDoc <docurl> <recurl> <username> <password> <folder> <filepath> <filename> <filetitle> [<category>]");
			System.exit(1);
		}

		try
		{
    			MeridioTestWrapper handle = new MeridioTestWrapper(args[0],args[1],args[2],args[3]);
			try
			{
				if (args[8] != null && args[8].length() > 0)
				{
					int categoryID = handle.findCategory(args[8]);
					if (categoryID == 0)
						throw new Exception("Unknown category '"+args[8]+"'");
					
					DOCUMENTS d = new DOCUMENTS();
					d.setNewDocCategoryId(categoryID); 
		
					long id = handle.addDocumentToFolder(args[4],args[5],args[6],args[7],d,new DOCUMENT_CUSTOMPROPS[0]);
					System.out.print(new Long(id).toString());
				}
				else
				{
					long id = handle.addDocumentToFolder(args[4],args[5],args[6],args[7]);
					System.out.print(new Long(id).toString());
				}
			}
			finally
			{
				handle.logout();
			}
			System.err.println("Successfully added");
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}

}
