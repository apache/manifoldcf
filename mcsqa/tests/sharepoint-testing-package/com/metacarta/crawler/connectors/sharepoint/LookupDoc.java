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
package com.metacarta.crawler.connectors.sharepoint;

import com.metacarta.core.interfaces.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.protocol.*;

public class LookupDoc
{
	public static final String _rcsid = "@(#)$Id$";

	private LookupDoc()
	{
	}

	public static void main(String[] args)
	{
		if (args.length != 8)
		{
			System.err.println("Usage: LookupDoc <protocol> <servername> <port> <location> <username> <password> <domain> <sharepoint_path>");
			System.exit(1);
		}

		try
		{
			// Find the current host name
       			java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

	            	// Get hostname
       			String currentHost = addr.getHostName();

			// Set up httpclient access to document
			MultiThreadedHttpConnectionManager connManager = new MultiThreadedHttpConnectionManager();
			try
			{
				HttpClient httpClient = new HttpClient(connManager);
				HostConfiguration clientConf = new HostConfiguration();
				clientConf.setParams(new HostParams());
				clientConf.setHost(args[1],Integer.parseInt(args[2]),Protocol.getProtocol(args[0]));
				Credentials credentials =  new NTCredentials(args[4], args[5], currentHost, args[6]);
							    
				httpClient.getState().setCredentials(new AuthScope(args[1],Integer.parseInt(args[2]),null),credentials);  

				HttpMethodBase method = new GetMethod( args[3] + "/" + args[7] ); 
				try
				{
					int returnCode = httpClient.executeMethod(clientConf,method);
					if (returnCode == HttpStatus.SC_OK)
						UTF8Stdout.print(args[7]);
				}
				finally
				{
					method.releaseConnection();
				}
			}
			finally
			{
				connManager.shutdown();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}

}
