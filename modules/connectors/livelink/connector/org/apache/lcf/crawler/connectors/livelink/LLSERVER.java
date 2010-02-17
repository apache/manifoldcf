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
package org.apache.lcf.crawler.connectors.livelink;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;

import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * @author Riccardo, modified extensively by Karl Wright
 *
 * This class represents information about a particular
 * Livelink Server. It also maintains a particular server session.
 * NOTE: The original Volant code insisted at a fundamental level that there be only
 * one session per JVM.  Not sure why they did this, and this is a vile restriction
 * if true.  I've therefore reworked this class to be able to work in a multi-session
 * environment, if possible; the instantiator gets to determine how many there will be.
 */
public class LLSERVER
{
	public static final String _rcsid = "@(#)$Id$";

	private String LLServer;
	private int LLPort;
	private String LLUser;
	private String LLPwd;
	private LLValue LLConfig;
	private LLSession session;
	
	
	public LLSERVER()
	{
		
		session = null;
	}
	
	public LLSERVER(String server, int port, String user, String pwd) 
	{
		LLServer = server;
		LLPort = port;
		LLUser = user;
		LLPwd = pwd;	
		
		connect();
	}
	
	private void connect()
	{
		session = new LLSession (this.LLServer, this.LLPort, "", this.LLUser, this.LLPwd, null);
	}
	

	/**
	 * Disconnects
	 *
	 */
	public void disconnect()
	{
		
		session = null;
	}
	

	/**
	 * Returns the server name where the Livelink
	 * Server has been installed on
	 * 
	 * @return the server name
	 */
	public String getHost()
	{
		
		if (session != null)
		{
			return session.getHost();
		}
		
		return null;	
	}
	
	
	/**
	 * Returns the port Livelink is listening on
	 * @return the port number
	 */
	public int getPort ()
	{
		
		if (session != null)
		{
			 return session.getPort();
		}
		
		return -1;
	}
	
	
	/**
	 * Returns the Livelink user currently connected
	 * to the Livelink Server
	 * @return the user name
	 */
	public String getLLUser()
	{
		
		return LLUser;	
	}
	
	
	
	/**
	 * Returns the password of the user currently connected
	 * to the Livelink Server
	 * @return the user password
	 */
	public String getLLPwd()
	{
		
		return LLPwd;
	}
	
	
	/**
	 * Returns the Livelink session
	 * @return Livelink session
	 */
	public LLSession getLLSession()
	{
		
		return session;
	}	

	/**
	 * Get the current session errors as a string.
	*/
	public String getErrors()
	{
		if (session == null)
			return null;
		StringBuffer rval = new StringBuffer();
		if (session.getStatus() != 0)
			rval.append("LAPI status code: ").append(session.getStatus());
		if (session.getApiError().length() > 0)
		{
			if (rval.length() > 0)
				rval.append("; ");
			rval.append("LAPI error detail: ").append(session.getApiError());
		}
		if (session.getErrMsg().length() > 0)
		{
			if (rval.length() > 0)
				rval.append("; ");
			rval.append("LAPI error message: ").append(session.getErrMsg());
		}
		if (session.getStatusMessage().length() > 0)
		{
			if (rval.length() > 0)
				rval.append("; ");
			rval.append("LAPI status message: ").append(session.getStatusMessage());
		}
		if (rval.length() > 0)
			return rval.toString();
		return null;
	}
	
}
