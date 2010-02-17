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
package org.apache.lcf.agents;

import java.io.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.*;
import java.lang.reflect.*;
import java.util.*;

public class SetupAgentConnection
{
	public static final String _rcsid = "@(#)$Id$";

	private SetupAgentConnection()
	{
	}


	public static void main(String[] args)
	{
		if (args.length < 3 || args.length > 4)
		{
			System.err.println("Usage: SetupAgentConnection <conf-file> <ingest_url> <username> [<password>]");
			System.err.println("If <password> is not specified on command line, it is read from stdin");
			System.exit(1);
		}

		try
		{
			String confFile = args[0];

			File f = new File(confFile);	// for re-read
			Properties p = new Properties();

			InputStream is = new FileInputStream(f);
			try
			{
				p.load(is);
			}
			finally
			{
				is.close();
			}

			p.setProperty(Metacarta.ingestURIProperty,args[1]);
			p.setProperty(Metacarta.ingestUserProperty,args[2]);
			String password;
			if (args.length > 3)
				password = args[3];
			else
			{
				// Read the password from standard in, using default encoding
				java.io.Reader str = new java.io.InputStreamReader(System.in);
				try
				{
					java.io.BufferedReader is2 = new java.io.BufferedReader(str);
					try
					{
						String thisString = is2.readLine();
						if (thisString == null)
							password = "";
						else
							password = thisString;
					}
					finally
					{
						is2.close();
					}
				}
				finally
				{
					str.close();
				}
			}
			
			p.setProperty(Metacarta.ingestPasswordProperty,Metacarta.obfuscate(password));

			// Write it out.
			OutputStream os = new FileOutputStream(f);
			try
			{
				p.store(os,"Agent Properties");
			}
			finally
			{
				os.close();
			}
			System.err.println("Done updating file "+confFile);

		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

}
