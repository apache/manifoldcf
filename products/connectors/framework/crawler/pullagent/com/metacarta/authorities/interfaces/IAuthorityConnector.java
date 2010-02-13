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
package com.metacarta.authorities.interfaces;

import com.metacarta.core.interfaces.*;

/** An authority connector supplies an ACL of some kind for a given user.  This is necessary so that the search UI
* can find the documents that can be legally seen.
*
* An instance of this interface provides this functionality.  Authority connector instances are pooled, so that session
* setup does not need to be done repeatedly.  The pool is segregated by specific sets of configuration parameters.
*/
public interface IAuthorityConnector
{

	/** Install the connector.
	* This method is called to initialize persistent storage for the connector, such as database tables etc.
	* It is called when the connector is registered.
	*@param threadContext is the current thread context.
	*/
	public void install(IThreadContext threadContext)
		throws MetacartaException;

	/** Uninstall the connector.
	* This method is called to remove persistent storage for the connector, such as database tables etc.
	* It is called when the connector is deregistered.
	*@param threadContext is the current thread context.
	*/
	public void deinstall(IThreadContext threadContext)
		throws MetacartaException;

	/** Return the path for the UI interface JSP elements.
	* These JSP's must be provided to allow the connector to be configured.
	* This method should return the name of the folder, under the <webapp>/connectors/
	* area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
	*@return the folder part
	*/
	public String getJSPFolder();

	/** Connect.  The configuration parameters are included.
	*@param configParams are the configuration parameters for this connection.
	*/
	public void connect(ConfigParams configParams);

	// All methods below this line will ONLY be called if a connect() call succeeded
	// on this instance!

	/** Test the connection.  Returns a string describing the connection integrity.
	*@return the connection's status as a displayable string.
	*/
	public String check()
		throws MetacartaException;

	/** This method is periodically called for all connectors that are connected but not
	* in active use.
	*/
	public void poll()
		throws MetacartaException;

	/** Close the connection.  Call this before discarding the repository connector.
	*/
	public void disconnect()
		throws MetacartaException;

	/** Get configuration information.
	*@return the configuration information for this class.
	*/
	public ConfigParams getConfiguration();

	/** Clear out any state information specific to a given thread.
	* This method is called when this object is returned to the connection pool.
	*/
	public void clearThreadContext();

	/** Attach to a new thread.
	*@param threadContext is the new thread context.
	*/
	public void setThreadContext(IThreadContext threadContext);

	/** Obtain the access tokens for a given user name.
	*@param userName is the user name or identifier.
	*@return the response tokens (according to the current authority).
	* (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
	*/
	public AuthorizationResponse getAuthorizationResponse(String userName)
		throws MetacartaException;
	
	/** Obtain the default access tokens for a given user name.
	*@param userName is the user name or identifier.
	*@return the default response tokens, presuming that the connect method fails.
	*/
	public AuthorizationResponse getDefaultAuthorizationResponse(String userName);
	
}
