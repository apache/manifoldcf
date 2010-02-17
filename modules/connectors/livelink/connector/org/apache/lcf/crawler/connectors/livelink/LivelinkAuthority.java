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
import org.apache.lcf.authorities.interfaces.*;
import org.apache.lcf.authorities.system.Logging;
import org.apache.lcf.authorities.system.LCF;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.regex.*;

import com.opentext.api.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;


/** This is the Livelink implementation of the IAuthorityConnector interface.
* This is not based on Volant code, but has been developed by me at the behest of
* James Maupin for use at Shell.
*
* Access tokens for livelink are simply user and usergroup node identifiers.  Therefore,
* this class retrieves those using the standard livelink call, being sure to map anything
* that looks like an active directory user name to something that looks like a Livelink
* domain/username form.
*
*/
public class LivelinkAuthority extends org.apache.lcf.authorities.authorities.BaseAuthorityConnector
{
	public static final String _rcsid = "@(#)$Id$";

	// Data from the parameters
	private String serverName = null;
	private String serverPortString = null;
	private int serverPort = -1;
	private String serverUsername = null;
	private String serverPassword = null;
	
	// Data required for maintaining livelink connection
	private LAPI_USERS LLUsers = null;
	private LLSERVER llServer = null;

	// Match map for username
	private MatchMap matchMap = null;

	// Retry count.  This is so we can try to install some measure of sanity into situations where LAPI gets confused communicating to the server.
	// So, for some kinds of errors, we just retry for a while hoping it will go away.
	private static final int FAILURE_RETRY_COUNT = 5;

	// Livelink does not have "deny" permissions, and there is no such thing as a document with no tokens, so it is safe to not have a local "deny" token.
	// However, people feel that a suspenders-and-belt approach is called for, so this restriction has been added.
	// Livelink tokens are numbers, "SYSTEM", or "GUEST", so they can't collide with the standard form.
	private static final String denyToken = "MC_DEAD_AUTHORITY";
	private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{denyToken},
			AuthorizationResponse.RESPONSE_UNREACHABLE);
	private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{denyToken},
			AuthorizationResponse.RESPONSE_USERNOTFOUND);

	/** Constructor.
	*/
	public LivelinkAuthority()
	{
	}

	/** Return the path for the UI interface JSP elements.
	* These JSP's must be provided to allow the connector to be configured, and to
	* permit it to present document filtering specification information in the UI.
	* This method should return the name of the folder, under the <webapp>/connectors/
	* area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
	*@return the folder part
	*/
	public String getJSPFolder()
	{
		return "livelink";
	}

	/** Connect.  The configuration parameters are included.
	*@param configParams are the configuration parameters for this connection.
	*/
	public void connect(ConfigParams configParams)
	{
		super.connect(configParams);

		// First, create server object (llServer)
		serverName = configParams.getParameter(LiveLinkParameters.serverName);
		serverPortString = configParams.getParameter(LiveLinkParameters.serverPort);
		serverUsername = configParams.getParameter(LiveLinkParameters.serverUsername);
		serverPassword = configParams.getObfuscatedParameter(LiveLinkParameters.serverPassword);

		// These have been deprecated
		String userNamePattern = configParams.getParameter(LiveLinkParameters.userNameRegexp);
		String userEvalExpression = configParams.getParameter(LiveLinkParameters.livelinkNameSpec);
		String userNameMapping = configParams.getParameter(LiveLinkParameters.userNameMapping);
		if ((userNameMapping == null || userNameMapping.length() == 0) && userNamePattern != null && userEvalExpression != null)
		{
			// Create a matchmap using the old system
			matchMap = new MatchMap();
			matchMap.appendOldstyleMatchPair(userNamePattern,userEvalExpression);
		}
		else
		{
			if (userNameMapping == null)
				userNameMapping = "(.*)\\\\@([A-Z|a-z|0-9|_|-]*)\\\\.(.*)=$(2)\\$(1l)";
			matchMap = new MatchMap(userNameMapping);
		}

		if (serverPortString == null)
			serverPort = 80;
		else
			serverPort = new Integer(serverPortString).intValue();
		


	}
	
	protected void attemptToConnect()
		throws LCFException, ServiceInterruption
	{
		if (LLUsers == null)
		{

			if (Logging.authorityConnectors.isDebugEnabled())
			{
				String passwordExists = (serverPassword!=null && serverPassword.length() > 0)?"password exists":"";
				Logging.authorityConnectors.debug("Livelink: Livelink connection parameters: Server='"+serverName+"'; port='"+serverPortString+"'; user name='"+serverUsername+"'; "+passwordExists);
			}

			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				try
				{
					llServer = new LLSERVER(serverName,serverPort,serverUsername,serverPassword);

					LLUsers = new LAPI_USERS(llServer.getLLSession());
					if (Logging.authorityConnectors.isDebugEnabled())
					{
						Logging.authorityConnectors.debug("Livelink: Livelink session created.");
					}
					return;
				}
				catch (RuntimeException e)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount);
				}
			}
		}
	}

	// All methods below this line will ONLY be called if a connect() call succeeded
	// on this instance!

	/** Check connection for sanity.
	*/
	public String check()
		throws LCFException
	{
		try
		{
			// Reestablish the session
			LLUsers = null;
			attemptToConnect();
			// Get user info for the crawl user, to make sure it works
			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				try
				{
					LLValue userObject = new LLValue();
					int status = LLUsers.GetUserInfo(serverUsername, userObject);
					if (status != 0)
						return "Connection failed: User authentication failed";
					return super.check();
				}
				catch (RuntimeException e)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount);
				}
			}
		}
		catch (ServiceInterruption e)
		{
			return "Temporary service interruption: "+e.getMessage();
		}
		catch (LCFException e)
		{
			return "Connection failed: "+e.getMessage();
		}
	}

	/** Close the connection.  Call this before discarding the repository connector.
	*/
	public void disconnect()
		throws LCFException
	{
		if (llServer != null)
		{
			llServer.disconnect();
			llServer = null;
		}
		LLUsers = null;
		matchMap = null;
		serverName = null;
		serverPortString = null;
		serverPort = -1;
		serverUsername = null;
		serverPassword = null;
		super.disconnect();
	}

	/** Obtain the access tokens for a given user name.
	*@param userName is the user name or identifier.
	*@return the response tokens (according to the current authority).
	* (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
	*/
	public AuthorizationResponse getAuthorizationResponse(String userName)
		throws LCFException
	{
		// First, do what's necessary to map the user name that comes in to a reasonable
		// Livelink domain\\user combination.

		if (Logging.authorityConnectors.isDebugEnabled())
		{
			Logging.authorityConnectors.debug("Authentication user name = '"+userName+"'");
		}

		// Use the matchMap object to do the translation
		String domainAndUser = matchMap.translate(userName);

		if (Logging.authorityConnectors.isDebugEnabled())
		{
			Logging.authorityConnectors.debug("Livelink: Livelink user name = '"+domainAndUser+"'");
		}

		try
		{
			attemptToConnect();
		
			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				try
				{
					ArrayList list = new ArrayList();

					// Find out if the specified user is a member of the Guest group, or is a member
					// of the System group.
					// Get information about the current user.  This is how we will determine if the
					// user exists, and also what permissions s/he has.
					LLValue userObject = new LLValue();
					int status = LLUsers.GetUserInfo(domainAndUser, userObject);
					if (status == 103101 || status == 401203)
					{
						if (Logging.authorityConnectors.isDebugEnabled())
						    Logging.authorityConnectors.debug("Livelink: Livelink user '"+domainAndUser+"' does not exist");
						return userNotFoundResponse;
					}

					if (status != 0)
					{
						Logging.authorityConnectors.warn("Livelink: User '"+domainAndUser+"' GetUserInfo error # "+Integer.toString(status)+" "+llServer.getErrors());
						// The server is probably down.
						return unreachableResponse;
					}

					int deleted = userObject.toInteger("Deleted");
					if (deleted == 1)
					{
						if (Logging.authorityConnectors.isDebugEnabled())
						    Logging.authorityConnectors.debug("Livelink: Livelink user '"+domainAndUser+"' has been deleted");
						// Since the user cannot become undeleted, then this should be treated as 'user does not exist'.
						return userNotFoundResponse;
					}
					int privs = userObject.toInteger("UserPrivileges");
					if ((privs & LAPI_USERS.PRIV_PERM_WORLD) == LAPI_USERS.PRIV_PERM_WORLD)
						list.add("GUEST");
					if ((privs & LAPI_USERS.PRIV_PERM_BYPASS) == LAPI_USERS.PRIV_PERM_BYPASS)
						list.add("SYSTEM");

					LLValue childrenObjects = new LLValue();
					status = LLUsers.ListRights(LAPI_USERS.USER, domainAndUser, childrenObjects);
					if (status == 103101 || status == 401203)
					{
						if (Logging.authorityConnectors.isDebugEnabled())
						    Logging.authorityConnectors.debug("Livelink: Livelink error looking up user rights for '"+domainAndUser+"' - user does not exist");
						return userNotFoundResponse;
					}

					if (status != 0)
					{
						// If the user doesn't exist, return null.  Right now, not sure how to figure out the
						// right error code, so just stuff it in the log.
						Logging.authorityConnectors.warn("Livelink: For user '"+domainAndUser+"', ListRights error # "+Integer.toString(status)+" "+llServer.getErrors());
						// An error code at this level has to indicate a suddenly unreachable authority
						return unreachableResponse;
					}

					// Go through the individual objects, and get their IDs.  These id's will be the access tokens
					int size;

					if (childrenObjects.isRecord())
						size = 1;
					else if (childrenObjects.isTable())
						size = childrenObjects.size();
					else
						size = 0;

					// We need also to add in support for the special rights objects.  These are:
					// -1: RIGHT_WORLD
					// -2: RIGHT_SYSTEM
					// -3: RIGHT_OWNER
					// -4: RIGHT_GROUP
					//
					// RIGHT_WORLD means guest access.
					// RIGHT_SYSTEM is "Public Access".
					// RIGHT_OWNER is access by the owner of the object.
					// RIGHT_GROUP is access by a member of the base group containing the owner
					//
					// These objects are returned by the corresponding GetObjectRights() call made during
					// the ingestion process.  We have to figure out how to map these to things that are
					// the equivalent of acls.

					// Idea:
					// 1) RIGHT_WORLD is based on some property of the user.
					// 2) RIGHT_SYSTEM is based on some property of the user.
					// 3) RIGHT_OWNER and RIGHT_GROUP are managed solely in the ingestion side of the world.

					// NOTE:  It turns out that -1 and -2 are in fact returned as part of the list of
					// rights requested above.  They get mapped to special keywords already in the above
					// code, so it *may* be reasonable to filter them from here.  It's not a real problem because
					// it's effectively just a duplicate of what we are doing.

					int j = 0;
					while (j < size)
					{
						int token = childrenObjects.toInteger(j, "ID");
						list.add(Integer.toString(token));
						j++;
					}
					String[] rval = new String[list.size()];
					j = 0;
					while (j < rval.length)
					{
						rval[j] = (String)list.get(j);
						j++;
					}
					
					return new AuthorizationResponse(rval,AuthorizationResponse.RESPONSE_OK);
				}
				catch (RuntimeException e)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount);
				}
			}
		}
		catch (ServiceInterruption e)
		{
			Logging.authorityConnectors.warn("Livelink: Server seems to be down: "+e.getMessage(),e);
			return unreachableResponse;
		}
	}

	/** Obtain the default access tokens for a given user name.
	*@param userName is the user name or identifier.
	*@return the default response tokens, presuming that the connect method fails.
	*/
	public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
	{
		// The default response if the getConnection method fails
		return unreachableResponse;
	}

	/** Interpret runtimeexception to search for livelink API errors.  Throws an appropriately reinterpreted exception, or
	* just returns if the exception indicates that a short-cycle retry attempt should be made.  (In that case, the appropriate
	* wait has been already performed).
	*@param e is the RuntimeException caught
	*/
	protected int handleLivelinkRuntimeException(RuntimeException e, int sanityRetryCount)
		throws LCFException, ServiceInterruption
	{
		if (
			e instanceof com.opentext.api.LLHTTPAccessDeniedException ||
			e instanceof com.opentext.api.LLHTTPClientException ||
			e instanceof com.opentext.api.LLHTTPServerException ||
			e instanceof com.opentext.api.LLIndexOutOfBoundsException ||
			e instanceof com.opentext.api.LLNoFieldSpecifiedException ||
			e instanceof com.opentext.api.LLNoValueSpecifiedException ||
			e instanceof com.opentext.api.LLSecurityProviderException ||
			e instanceof com.opentext.api.LLUnknownFieldException
		   )
		{
			String details = llServer.getErrors();
			throw new LCFException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e,LCFException.REPOSITORY_CONNECTION_ERROR);
		}
		else if (
			e instanceof com.opentext.api.LLBadServerCertificateException ||
			e instanceof com.opentext.api.LLHTTPCGINotFoundException ||
			e instanceof com.opentext.api.LLCouldNotConnectHTTPException ||
			e instanceof com.opentext.api.LLHTTPForbiddenException ||
			e instanceof com.opentext.api.LLHTTPProxyAuthRequiredException ||
			e instanceof com.opentext.api.LLHTTPRedirectionException ||
			e instanceof com.opentext.api.LLUnsupportedAuthMethodException ||
			e instanceof com.opentext.api.LLWebAuthInitException
			  )
		{
			String details = llServer.getErrors();
			throw new LCFException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e);
		}
		else if (e instanceof com.opentext.api.LLIllegalOperationException)
		{
			// This usually means that LAPI has had a minor communication difficulty but hasn't reported it accurately.
			// We *could* throw a ServiceInterruption, but OpenText recommends to just retry almost immediately.
			String details = llServer.getErrors();
			return assessRetry(sanityRetryCount,new LCFException("Livelink API illegal operation error: "+e.getMessage()+((details==null)?"":"; "+details),e));
		}
		else if (e instanceof com.opentext.api.LLIOException)
		{
			// LAPI is returning errors that are not terribly explicit, and I don't have control over their wording, so check that server can be resolved by DNS,
			// so that a better error message can be returned.
			try
			{
				InetAddress.getByName(serverName);
			}
			catch (UnknownHostException e2)
			{
				throw new LCFException("Server name '"+serverName+"' cannot be resolved",e2);
			}

			throw new ServiceInterruption("Transient error: "+e.getMessage(),e,System.currentTimeMillis()+5*60000L,System.currentTimeMillis()+12*60*60000L,-1,true);
		}
		else
			throw e;
	}
	
	/** Do a retry, or throw an exception if the retry count has been exhausted
	*/
	protected static int assessRetry(int sanityRetryCount, LCFException e)
		throws LCFException
	{
		if (sanityRetryCount == 0)
		{
			throw e;
		}
			
		sanityRetryCount--;

		try
		{
			LCF.sleep(1000L);
		}
		catch (InterruptedException e2)
		{
			throw new LCFException(e2.getMessage(),e2,LCFException.INTERRUPTED);
		}
		// Exit the method
		return sanityRetryCount;

	}

}


