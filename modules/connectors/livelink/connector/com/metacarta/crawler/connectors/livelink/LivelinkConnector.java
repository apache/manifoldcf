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
package com.metacarta.crawler.connectors.livelink;

import com.metacarta.core.interfaces.*;
import com.metacarta.agents.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import com.metacarta.crawler.system.Logging;
import com.metacarta.crawler.system.Metacarta;

import java.io.*;
import java.util.*;
import java.net.*;

import com.opentext.api.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.protocol.*;


/** This is the Livelink implementation of the IRepositoryConnectr interface.
* The original Volant code forced there to be one livelink session per JVM, with
* lots of buggy synchronization present to try to enforce this.  This implementation
* is multi-session.  However, since it is possible that the Volant restriction was
* indeed needed, I have attempted to structure things to allow me to turn on
* single-session if needed.
*
* For livelink, the document identifiers are the object identifiers.
*
*/
public class LivelinkConnector extends com.metacarta.crawler.connectors.BaseRepositoryConnector
{
	public static final String _rcsid = "@(#)$Id$";

	// Activities we will report on
	private final static String ACTIVITY_SEED = "find documents";
	private final static String ACTIVITY_FETCH = "fetch document";

	/** Deny access token for default authority */
	private final static String defaultAuthorityDenyToken = "MCADAuthority_MC_DEAD_AUTHORITY";

	// Livelink does not have "deny" permissions, and there is no such thing as a document with no tokens, so it is safe to not have a local "deny" token.
	// However, people feel that a suspenders-and-belt approach is called for, so this restriction has been added.
	// Livelink tokens are numbers, "SYSTEM", or "GUEST", so they can't collide with the standard form.
	private static final String denyToken = "MC_DEAD_AUTHORITY";

	// A couple of very important points.
	// First, the canonical document identifier has the following form:
	// <D|F>[<volume_id>:]<object_id>
	// Second, the only LEGAL objects for a document identifier to describe
	// are folders, documents, and volume objects.  Project objects are NOT
	// allowed; they must be mapped to the appropriate volume object before
	// being returned to the crawler.

	// Signal that we have set up a connection properly
	private boolean hasBeenSetup = false;
	
	// Data required for maintaining livelink connection
	private LAPI_DOCUMENTS LLDocs = null;
	private LAPI_ATTRIBUTES LLAttributes = null;
	private LLSERVER llServer = null;
	private int LLENTWK_VOL;
	private int LLENTWK_ID;
	private int LLCATWK_VOL;
	private int LLCATWK_ID;

	// Parameter values we need
	private String serverName = null;
	private int serverPort = -1;
	private String serverUsername = null;
	private String serverPassword = null;

	private String ingestProtocol = null;
	private String ingestPort = null;
	private String ingestCgiPath = null;
	
	private String viewProtocol = null;
	private String viewServerName = null;
	private String viewPort = null;
	private String viewCgiPath = null;
	
	private String ntlmDomain = null;
	private String ntlmUsername = null;
	private String ntlmPassword = null;

	// SSL support
	private String keystoreData = null;
	private IKeystoreManager keystoreManager = null;
	private LivelinkSecureSocketFactory secureSocketFactory = null;
	private ProtocolFactory myFactory = null;

	// Connection management
	private MultiThreadedHttpConnectionManager connectionManager = null;
	
	// Base path for viewing
	private String viewBasePath = null;
	
	// Ingestion port number
	private int ingestPortNumber = -1;
	
	// Activities list
	private static final String[] activitiesList = new String[]{ACTIVITY_SEED,ACTIVITY_FETCH};

	// Retry count.  This is so we can try to install some measure of sanity into situations where LAPI gets confused communicating to the server.
	// So, for some kinds of errors, we just retry for a while hoping it will go away.
	private static final int FAILURE_RETRY_COUNT = 10;

	// Current host name
	private static String currentHost = null;
	private static java.net.InetAddress currentAddr = null;
	static
	{
		// Find the current host name
	        try
		{
       			currentAddr = java.net.InetAddress.getLocalHost();

	            	// Get hostname
       			currentHost = currentAddr.getHostName();
       		}
		catch (UnknownHostException e)
		{
       		}
	}


	/** Constructor.
	*/
	public LivelinkConnector()
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

		// This is required by getBins()
		serverName = params.getParameter(LiveLinkParameters.serverName);
	}

	protected class GetSessionThread extends Thread
	{
		protected Throwable exception = null;

		public GetSessionThread()
		{
			super();
			setDaemon(true);
		}
		
		public void run()
		{
			try
			{
				// Create the session
				llServer = new LLSERVER(serverName,serverPort,serverUsername,serverPassword);

				LLDocs = new LAPI_DOCUMENTS(llServer.getLLSession());
				LLAttributes = new LAPI_ATTRIBUTES(llServer.getLLSession());
				if (Logging.connectors.isDebugEnabled())
				{
					String passwordExists = (serverPassword!=null&&serverPassword.length()>0)?"password exists":"";
					Logging.connectors.debug("Livelink: Livelink Session: Server='"+serverName+"'; port='"+serverPort+"'; user name='"+serverUsername+"'; "+passwordExists);
				}
				LLValue entinfo = new LLValue().setAssoc();

				int status;
				status = LLDocs.AccessEnterpriseWS(entinfo);
				if (status == 0)
				{
					LLENTWK_ID = entinfo.toInteger("ID");
					LLENTWK_VOL = entinfo.toInteger("VolumeID");
				}
				else
					throw new MetacartaException("Error accessing enterprise workspace: "+status);

				entinfo = new LLValue().setAssoc();
				status = LLDocs.AccessCategoryWS(entinfo);
				if (status == 0)
				{
					LLCATWK_ID = entinfo.toInteger("ID");
					LLCATWK_VOL = entinfo.toInteger("VolumeID");
				}
				else
					throw new MetacartaException("Error accessing category workspace: "+status);
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
	}

	/** Get the bin name string for a document identifier.  The bin name describes the queue to which the
	* document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
	* given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
	* multiple queues or bins.
	* For example, if you implement a web crawler, a good choice of bin name would be the server name, since
	* that is likely to correspond to a real resource that will need real throttle protection.
	*@param documentIdentifier is the document identifier.
	*@return the bin name.
	*/
	public String[] getBinNames(String documentIdentifier)
	{
		// This should return server name
		return new String[]{serverName};
	}
	
	protected void getSession()
		throws MetacartaException, ServiceInterruption
	{
		if (hasBeenSetup == false)
		{
			// Do the initial setup part (what used to be part of connect() itself)
			
			// Get the parameters
			ingestProtocol = params.getParameter(LiveLinkParameters.ingestProtocol);
			ingestPort = params.getParameter(LiveLinkParameters.ingestPort);
			ingestCgiPath = params.getParameter(LiveLinkParameters.ingestCgiPath);

			viewProtocol = params.getParameter(LiveLinkParameters.viewProtocol);
			viewServerName = params.getParameter(LiveLinkParameters.viewServerName);
			viewPort = params.getParameter(LiveLinkParameters.viewPort);
			viewCgiPath = params.getParameter(LiveLinkParameters.viewCgiPath);

			ntlmDomain = params.getParameter(LiveLinkParameters.ntlmDomain);
			ntlmUsername = params.getParameter(LiveLinkParameters.ntlmUsername);
			ntlmPassword = params.getObfuscatedParameter(LiveLinkParameters.ntlmPassword);

			String serverPortString = params.getParameter(LiveLinkParameters.serverPort);
			serverUsername = params.getParameter(LiveLinkParameters.serverUsername);
			serverPassword = params.getObfuscatedParameter(LiveLinkParameters.serverPassword);

			if (ingestProtocol == null || ingestProtocol.length() == 0)
				ingestProtocol = "http";
			if (viewProtocol == null || viewProtocol.length() == 0)
				viewProtocol = ingestProtocol;
			
			if (ingestPort == null || ingestPort.length() == 0)
			{
				if (!ingestProtocol.equals("https"))
					ingestPort = "80";
				else
					ingestPort = "443";
			}
			
			if (viewPort == null || viewPort.length() == 0)
			{
				if (!viewProtocol.equals(ingestProtocol))
				{
					if (!viewProtocol.equals("https"))
						viewPort = "80";
					else
						viewPort = "443";
				}
				else
					viewPort = ingestPort;
			}
			
			try
			{
				ingestPortNumber = Integer.parseInt(ingestPort);
			}
			catch (NumberFormatException e)
			{
				throw new MetacartaException("Bad ingest port: "+e.getMessage(),e);
			}
			
			String viewPortString;
			try
			{
				int portNumber = Integer.parseInt(viewPort);
				viewPortString = ":" + Integer.toString(portNumber);
				if (!viewProtocol.equals("https"))
				{
					if (portNumber == 80)
						viewPortString = "";
				}
				else
				{
					if (portNumber == 443)
						viewPortString = "";
				}
			}
			catch (NumberFormatException e)
			{
				throw new MetacartaException("Bad view port: "+e.getMessage(),e);
			}

			if (viewCgiPath == null || viewCgiPath.length() == 0)
				viewCgiPath = ingestCgiPath;

			if (ntlmDomain != null && ntlmDomain.length() == 0)
				ntlmDomain = null;
			if (ntlmDomain == null)
			{
				ntlmUsername = null;
				ntlmPassword = null;
			}
			else
			{
				if (ntlmUsername == null || ntlmUsername.length() == 0)
				{
					ntlmUsername = serverUsername;
					if (ntlmPassword == null || ntlmPassword.length() == 0)
						ntlmPassword = serverPassword;
				}
				else
				{
					if (ntlmPassword == null)
						ntlmPassword = "";
				}
			}

			// First, create server object (llServer)

			if (serverPortString == null)
				serverPort = 2099;
			else
				serverPort = new Integer(serverPortString).intValue();

			// Set up ssl if indicated
			keystoreData = params.getParameter(LiveLinkParameters.livelinkKeystore);
			myFactory = new ProtocolFactory();

			if (keystoreData != null)
			{
				keystoreManager = KeystoreManagerFactory.make("",keystoreData);
				secureSocketFactory = new LivelinkSecureSocketFactory(keystoreManager.getSecureSocketFactory());
				Protocol myHttpsProtocol = new Protocol("https", (ProtocolSocketFactory)secureSocketFactory, 443);
				myFactory.registerProtocol("https",myHttpsProtocol);
				if (Logging.connectors.isDebugEnabled())
				{
					Logging.connectors.debug("Livelink: Created new secure protocol class instance; factory type is '"+myHttpsProtocol.getSocketFactory().getClass().getName()+"'");
				}
			}

			// Set up connection manager
			connectionManager = new MultiThreadedHttpConnectionManager();
			connectionManager.getParams().setMaxTotalConnections(1);

			// System.out.println("Connection server object = "+llServer.toString());
			
			// Establish the actual connection
			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				GetSessionThread t = new GetSessionThread();
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RuntimeException)
							throw (RuntimeException)thr;
						else if (thr instanceof MetacartaException)
							throw (MetacartaException)thr;
						else
							throw (Error)thr;
					}
					if (viewServerName == null || viewServerName.length() == 0)
						viewServerName = llServer.getHost();

					viewBasePath = viewProtocol+"://"+viewServerName+viewPortString+viewCgiPath;
					hasBeenSetup = true;
					return;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RuntimeException e2)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e2,sanityRetryCount,true);
				}
			}
		}
	}

	// All methods below this line will ONLY be called if a connect() call succeeded
	// on this instance!

	protected static class ExecuteMethodThread extends Thread
	{
		protected HttpClient client;
		protected HttpMethodBase executeMethod;
		protected HostConfiguration hostConfiguration;
		protected Throwable exception = null;
		protected int rval = 0;

		public ExecuteMethodThread(HttpClient client, HostConfiguration hostConfiguration, HttpMethodBase executeMethod)
		{
			super();
			setDaemon(true);
			this.client = client;
			this.hostConfiguration = hostConfiguration;
			this.executeMethod = executeMethod;
		}
			
		public void run()
		{
			try
			{
				// Call the execute method appropriately
				rval = client.executeMethod(hostConfiguration,executeMethod,null);
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
			
		public Throwable getException()
		{
			return exception;
		}
		
		public int getResponse()
		{
			return rval;
		}
	}

	protected static int executeMethodViaThread(HttpClient client, HostConfiguration hostConfiguration, HttpMethodBase executeMethod)
		throws InterruptedException, IOException
	{
		ExecuteMethodThread t = new ExecuteMethodThread(client,hostConfiguration,executeMethod);
		try
		{
			t.start();
			t.join();
			Throwable thr = t.getException();
			if (thr != null)
			{
				if (thr instanceof IOException)
					throw (IOException)thr;
				else if (thr instanceof RuntimeException)
					throw (RuntimeException)thr;
				else
					throw (Error)thr;
			}
			return t.getResponse();
		}
		catch (InterruptedException e)
		{
			t.interrupt();
			// We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
			throw e;
		}
	}
	
	/** Check status of connection.
	*/
	public String check()
		throws MetacartaException
	{
		try
		{
			// Destroy saved session setup and repeat it
			hasBeenSetup = false;
			getSession();
		
			// Now, set up trial of ingestion connection
			String contextMsg = "for document access";
			String ingestHttpAddress = ingestCgiPath;
			
			HttpClient client = getInitializedClient(contextMsg);
		
			try
			{
			    // Set up fetch using our special stuff if it's https
			    GetMethod method = new GetMethod(ingestHttpAddress);
			    try
			    {
				method.getParams().setParameter("http.socket.timeout", new Integer(300000));
				method.setFollowRedirects(true);

				int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),method);
				switch (statusCode)
				{
				case 502:
				    return "Fetch test had transient 502 error response";

				case HttpStatus.SC_UNAUTHORIZED:
				    return "Fetch test returned UNAUTHORIZED (401) response; check the security credentials and configuration";

				case HttpStatus.SC_OK:
				    return super.check();

				default:
				    return "Fetch test returned an unexpected response code of "+Integer.toString(statusCode);
				}
			    }
			    catch (InterruptedException e)
			    {
				// Drop the connection on the floor
				method = null;
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			    }
			    catch (java.net.SocketTimeoutException e)
			    {
				return "Fetch test timed out reading from the Livelink HTTP Server: "+e.getMessage();
			    }
			    catch (java.net.SocketException e)
			    {
				return "Fetch test received a socket error reading from Livelink HTTP Server: "+e.getMessage();
			    }
			    catch (javax.net.ssl.SSLHandshakeException e)
			    {
				return "Fetch test was unable to set up a SSL connection to Livelink HTTP Server: "+e.getMessage();
			    }
			    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
			    {
				return "Fetch test connection timed out reading from Livelink HTTP Server: "+e.getMessage();
			    }
			    catch (InterruptedIOException e)
			    {
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			    }
			    catch (IOException e)
			    {
				return "Fetch test had an IO failure: "+e.getMessage();
			    }
			    finally
			    {
				if (method != null)
					method.releaseConnection();
			    }
			}
			catch (IllegalStateException e)
			{
				return "Fetch test had a state exception talking to Livelink HTTP Server: "+e.getMessage();
			}
		}
		catch (ServiceInterruption e)
		{
			return "Transient error: "+e.getMessage();
		}
		catch (MetacartaException e)
		{
			if (e.getErrorCode() == MetacartaException.INTERRUPTED)
				throw e;
			return "Error: "+e.getMessage();
		}
	}

	/** This method is periodically called for all connectors that are connected but not
	* in active use.
	*/
	public void poll()
		throws MetacartaException
	{
		if (connectionManager != null)
			connectionManager.closeIdleConnections(60000L);
	}

	/** Close the connection.  Call this before discarding the repository connector.
	*/
	public void disconnect()
		throws MetacartaException
	{
		llServer.disconnect();
		hasBeenSetup = false;
		llServer = null;
		LLDocs = null;
		LLAttributes = null;
		keystoreData = null;
		keystoreManager = null;
		secureSocketFactory = null;
		myFactory = null;
		ingestPortNumber = -1;
		
		serverName = null;
		serverPort = -1;
		serverUsername = null;
		serverPassword = null;

		ingestPort = null;
		ingestProtocol = null;
		ingestCgiPath = null;
		
		viewPort = null;
		viewServerName = null;
		viewProtocol = null;
		viewCgiPath = null;
		
		viewBasePath = null;
		
		ntlmDomain = null;
		ntlmUsername = null;
		ntlmPassword = null;
		
		if (connectionManager != null)
			connectionManager.shutdown();
		connectionManager = null;
		
		super.disconnect();
	}

	/** List the activities we might report on.
	*/
	public String[] getActivitiesList()
	{
		return activitiesList;
	}

	/** Convert a document identifier to a relative URI to read data from.  This is not the search URI; that's constructed
	* by a different method.
	*@param documentIdentifier is the document identifier.
	*@return the relative document uri.
	*/
	protected String convertToIngestURI(String documentIdentifier)
		throws MetacartaException
	{
		// The document identifier is the string form of the object ID for this connector.
		if (!documentIdentifier.startsWith("D"))
			return null;
		int colonPosition = documentIdentifier.indexOf(":",1);
		if (colonPosition == -1)
			return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(1)+"&objAction=download";
		else
			return ingestCgiPath+"?func=ll&objID="+documentIdentifier.substring(colonPosition+1)+"&objAction=download";
	}

	/** Convert a document identifier to a URI to view.  The URI is the URI that will be the unique key from
	* the search index, and will be presented to the user as part of the search results.  It must therefore
	* be a unique way of describing the document.
	*@param documentIdentifier is the document identifier.
	*@return the document uri.
	*/
	protected String convertToViewURI(String documentIdentifier)
		throws MetacartaException
	{
		// The document identifier is the string form of the object ID for this connector.
		if (!documentIdentifier.startsWith("D"))
			return null;
		int colonPosition = documentIdentifier.indexOf(":",1);
		if (colonPosition == -1)
			return viewBasePath+"?func=ll&objID="+documentIdentifier.substring(1)+"&objAction=download";
		else
			return viewBasePath+"?func=ll&objID="+documentIdentifier.substring(colonPosition+1)+"&objAction=download";
	}

	/** Convert a document identifier to an object ID.  MUST be a simple document, not a folder or project.
	*@param documentIdentifier is the document identifier.
	*@return the object id, or -1 if documentIdentifier does not describe a document.
	*/
	protected static int convertToObjectID(String documentIdentifier)
		throws MetacartaException
	{
		if (!documentIdentifier.startsWith("D"))
			return -1;
		int colonPosition = documentIdentifier.indexOf(":",1);
		try
		{
			if (colonPosition == -1)
				return Integer.parseInt(documentIdentifier.substring(1));
			else
				return Integer.parseInt(documentIdentifier.substring(colonPosition+1));
		}
		catch (NumberFormatException e)
		{
			throw new MetacartaException("Bad document identifier: "+e.getMessage(),e);
		}
	}


	/** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
	* are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
	*@param activities is the interface this method should use to perform whatever framework actions are desired.
	*@param spec is a document specification (that comes from the job).
	*@param startTime is the beginning of the time range to consider, inclusive.
	*@param endTime is the end of the time range to consider, exclusive.
	*/
	public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
		long startTime, long endTime)
		throws MetacartaException, ServiceInterruption
	{
		getSession();

		// First, grab the root LLValue
		LLValue rootValue = getObjectInfo(LLENTWK_VOL,LLENTWK_ID);
		if (rootValue == null)
		{
			// If we get here, it HAS to be a bad network/transient problem.
			Logging.connectors.warn("Livelink: Could not look up root workspace object during seeding!  Retrying -");
			throw new ServiceInterruption("Service interruption during seeding",new MetacartaException("Could not looking root workspace object during seeding"),System.currentTimeMillis()+60000L,
				System.currentTimeMillis()+600000L,-1,true);
		}

		// Walk the specification for the "startpoint" types.  Amalgamate these into a list of strings.
		// Presume that all roots are startpoint nodes
		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i);
			if (n.getType().equals("startpoint"))
			{
				// The id returned is simply the node path, which can't be messed up
				long beginTime = System.currentTimeMillis();
				String path = n.getAttributeValue("path");
				VolumeAndId vaf = getPathId(rootValue,path);
				if (vaf != null)
				{
					activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
						path,"OK",null,null);
	
					String newID = "F" + new Integer(vaf.getVolumeID()).toString()+":"+ new Integer(vaf.getPathId()).toString();
					activities.addSeedDocument(newID);
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Seed = '"+newID+"'");
				}
				else
				{
					activities.recordActivity(new Long(beginTime),ACTIVITY_SEED,null,
						path,"NOT FOUND",null,null);
				}
			}
			i++;
		}

	}


	/** Get document versions given an array of document identifiers.
	* This method is called for EVERY document that is considered. It is
	* therefore important to perform as little work as possible here.
	*@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
	*@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
	*   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
	*   had an empty version string.
	*@param activities is the interface this method should use to perform whatever framework actions are desired.
	*@param spec is the current document specification for the current job.  If there is a dependency on this
	* specification, then the version string should include the pertinent data, so that reingestion will occur
	* when the specification changes.  This is primarily useful for metadata.
	*@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
	*@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
	*@return the corresponding version strings, with null in the places where the document no longer exists.
	* Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
	* will always be processed.
	*/
	public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
		DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
		throws MetacartaException, ServiceInterruption
	{
		getSession();
		
		// First, process the spec to get the string we tack on

		// Read the forced acls.  A null return indicates that security is disabled!!!
		// A zero-length return indicates that the native acls should be used.
		// All of this is germane to how we ingest the document, so we need to note it in
		// the version string completely.
		String[] acls = getAcls(spec);
		// Sort it, in case it is needed.
		if (acls != null)
			java.util.Arrays.sort(acls);

		// Look at the metadata attributes.
		// So that the version strings are comparable, we will put them in an array first, and sort them.
		String pathAttributeName = null;
		HashMap holder = new HashMap();
		MatchMap matchMap = new MatchMap();
		boolean includeAllMetadata = false;
		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i++);
			if (n.getType().equals("allmetadata"))
			{
				String isAll = n.getAttributeValue("all");
				if (isAll != null && isAll.equals("true"))
					includeAllMetadata = true;
			}
			else if (n.getType().equals("metadata"))
			{
				String category = n.getAttributeValue("category");
				String attributeName = n.getAttributeValue("attribute");
				String isAll = n.getAttributeValue("all");
				if (isAll != null && isAll.equals("true"))
				{
					// Locate all metadata items for the specified category path,
					// and enter them into the array
					String[] attrs = getCategoryAttributes(category);
					if (attrs != null)
					{
						int j = 0;
						while (j < attrs.length)
						{
							attributeName = attrs[j++];
							String metadataName = packCategoryAttribute(category,attributeName);
							holder.put(metadataName,metadataName);
						}
					}
				}
				else
				{
					String metadataName = packCategoryAttribute(category,attributeName);
					holder.put(metadataName,metadataName);
				}
			}
			else if (n.getType().equals("pathnameattribute"))
				pathAttributeName = n.getAttributeValue("value");
			else if (n.getType().equals("pathmap"))
			{
				// Path mapping info also needs to be looked at, because it affects what is
				// ingested.
				String pathMatch = n.getAttributeValue("match");
				String pathReplace = n.getAttributeValue("replace");
				matchMap.appendMatchPair(pathMatch,pathReplace);
			}

		}

		// Prepare the specified metadata
		StringBuffer metadataString = null;
		CategoryPathAccumulator catAccum = null;
		if (!includeAllMetadata)
		{
			metadataString = new StringBuffer();
			// Put into an array
			String[] sortArray = new String[holder.size()];
			i = 0;
			Iterator iter = holder.keySet().iterator();
			while (iter.hasNext())
			{
				sortArray[i++] = (String)iter.next();
			}

			// Sort!
			java.util.Arrays.sort(sortArray);
			// Build the metadata string piece now
			packList(metadataString,sortArray,'+');
		}
		else
			catAccum = new CategoryPathAccumulator();

		// Calculate the part of the version string that comes from path name and mapping.
		// This starts with = since ; is used by another optional component (the forced acls)
		StringBuffer pathNameAttributeVersion = new StringBuffer();
		if (pathAttributeName != null)
			pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

		// The version string includes the following:
		// 1) The modify date for the document
		// 2) The rights for the document, ordered (which can change without changing the ModifyDate field)
		// 3) The requested metadata fields (category and attribute, ordered) for the document
		// 
		// The document identifiers are object id's.

		String[] rval = new String[documentIdentifiers.length];
		i = 0;
		while (i < documentIdentifiers.length)
		{
			// Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
			activities.checkJobStillActive();

			// Read the document or folder metadata, which includes the ModifyDate
			String docID = documentIdentifiers[i];
			    
			boolean isFolder = docID.startsWith("F");

			int colonPos = docID.indexOf(":",1);

			int objID;
			int vol;

			if (colonPos == -1)
			{
				objID = new Integer(docID.substring(1)).intValue();
				vol = LLENTWK_VOL;
			}
			else
			{
				objID = new Integer(docID.substring(colonPos+1)).intValue();
				vol = new Integer(docID.substring(1,colonPos)).intValue();
			}

			rval[i] = null;
			LLValue value = getObjectInfo(vol,objID);
			if (value != null)
			{
				// Make sure we have permission to see the object's contents
				int permissions = value.toInteger("Permissions");
				if ((permissions & LAPI_DOCUMENTS.PERM_SEECONTENTS) != 0)
				{
					Date dt = value.toDate("ModifyDate");
					// The rights don't change when the object changes, so we have to include those too.
					int[] rights = getObjectRights(vol,objID);
					if (rights != null)
					{
						// We were able to get rights, so object still exists.

						// I rearranged this on 11/7/2006 so that it would be more parseable, since
						// we want to pull the transient information out of the version string where
						// possible (so there is no mismatch between version checking and ingestion).

						StringBuffer sb = new StringBuffer();

						// On 1/17/2008 I changed the version generation code to NOT include metadata, view info, etc. for folders, since
						// folders make absolutely no use of this info.
						if (!isFolder)
						{
							if (includeAllMetadata)
							{
								// Find all the metadata associated with this object, and then
								// find the set of category pathnames that correspond to it.
								int[] catIDs = getObjectCategoryIDs(vol,objID);
								String[] categoryPaths = catAccum.getCategoryPathsAttributeNames(catIDs);
								// Sort!
								java.util.Arrays.sort(categoryPaths);
								// Build the metadata string piece now
								packList(sb,categoryPaths,'+');
							}
							else
								sb.append(metadataString);
						}

						String[] actualAcls;
						String denyAcl;
						if (acls != null && acls.length == 0)
						{
							// No forced acls.  Read the actual acls from livelink, as a set of rights.
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
							// These objects are returned by the GetObjectRights() call made above, and NOT
							// returned by LLUser.ListObjects().  We have to figure out how to map these to
							// things that are
							// the equivalent of acls.

							actualAcls = lookupTokens(rights, vol, objID);
							java.util.Arrays.sort(actualAcls);
							// If security is on, no deny acl is needed for the local authority, since the repository does not support "deny".  But this was added
							// to be really really really sure.
							denyAcl = denyToken;

						}
						else if (acls != null && acls.length > 0)
						{
							// Forced acls
							actualAcls = acls;
							if (usesDefaultAuthority)
								denyAcl = defaultAuthorityDenyToken;
							else
								denyAcl = denyToken;
						}
						else
						{
							// Security is OFF
							actualAcls = acls;
							denyAcl = null;
						}

						// Now encode the acls.  If null, we write a special value.
						if (actualAcls == null)
							sb.append('-');
						else
						{
							sb.append('+');
							packList(sb,actualAcls,'+');
							// This was added on 4/21/2008 to support forced acls working with the global default authority.
							pack(sb,denyAcl,'+');
						}

						// The date does not need to be parseable
						sb.append(dt.toString());

						if (!isFolder)
						{
							// PathNameAttributeVersion comes completely from the spec, so we don't
							// have to worry about it changing.  No need, therefore, to parse it during
							// processDocuments.
							sb.append("=").append(pathNameAttributeVersion);

							// Tack on ingestCgiPath, to insulate us against changes to the repository connection setup.  Added 9/7/07.
							sb.append("_").append(viewBasePath);
						}
						
						rval[i] = sb.toString();
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Successfully calculated version string for object "+Integer.toString(vol)+":"+Integer.toString(objID)+" : '"+rval[i]+"'");
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Could not get rights for object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
					}
				}
				else
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Crawl user cannot see contents of object "+Integer.toString(vol)+":"+Integer.toString(objID)+" - deleting");
				}
			}
			else
			{
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(objID)+" has no information - deleting");
			}
			i++;

		}
		return rval;
	}

	protected class ListObjectsThread extends Thread
	{
		protected int vol;
		protected int objID;
		protected String filterString;
		protected Throwable exception = null;
		protected LLValue rval = null;

		public ListObjectsThread(int vol, int objID, String filterString)
		{
			super();
			setDaemon(true);
			this.vol = vol;
			this.objID = objID;
			this.filterString = filterString;
		}
		
		public void run()
		{
			try
			{
				LLValue childrenDocs = new LLValue();
				int status = LLDocs.ListObjects(vol, objID, null, filterString, LAPI_DOCUMENTS.PERM_SEECONTENTS, childrenDocs);
				if (status != 0)
				{
					throw new MetacartaException("Error retrieving contents of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : Status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
				}
				rval = childrenDocs;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** Process a set of documents.
	* This is the method that should cause each document to be fetched, processed, and the results either added
	* to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
	* The document specification allows this class to filter what is done based on the job.
	*@param documentIdentifiers is the set of document identifiers to process.
	*@param activities is the interface this method should use to queue up new document references
	* and ingest documents.
	*@param spec is the document specification.
	*@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
	* should only find other references, and should not actually call the ingestion methods.
	*/
	public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
		throws MetacartaException, ServiceInterruption
	{
		getSession();
		
		// First, initialize the table of catid's.
		// Keeping this around will allow us to benefit from batching of documents.
		MetadataDescription desc = new MetadataDescription();

		// Build the node/path cache
		SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

		int i = 0;
		while (i < documentIdentifiers.length)
		{
			// Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
			activities.checkJobStillActive();
			String documentIdentifier = documentIdentifiers[i];
			boolean doScanOnly = scanOnly[i];

			boolean isFolder = documentIdentifier.startsWith("F");
			int colonPosition = documentIdentifier.indexOf(":",1);
			int vol;
			int objID;
			if (colonPosition == -1)
			{
				vol = LLENTWK_VOL;
				objID = new Integer(documentIdentifier.substring(1)).intValue();
			}
			else
			{
				vol = new Integer(documentIdentifier.substring(1,colonPosition)).intValue();
				objID = new Integer(documentIdentifier.substring(colonPosition+1)).intValue();
			}
				
			if (isFolder)
			{
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));
				
				// Since the identifier indicates it is a directory, then queue up all the current children which pass the filter.
				String filterString = buildFilterString(spec);

				int sanityRetryCount = FAILURE_RETRY_COUNT;
				while (true)
				{
				  ListObjectsThread t = new ListObjectsThread(vol,objID,filterString);
				  try
				  {
				    t.start();
				    t.join();
				    Throwable thr = t.getException();
				    if (thr != null)
				    {
					if (thr instanceof RuntimeException)
						throw (RuntimeException)thr;
					else if (thr instanceof MetacartaException)
					{
						sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
						continue;
					}
					else
						throw (Error)thr;
				    }
				    
				    LLValue childrenDocs = t.getResponse();

				    int size = 0;
			
				    if (childrenDocs.isRecord())
					size = 1;
				    if (childrenDocs.isTable())
					size = childrenDocs.size();

				    // System.out.println("Total child count = "+Integer.toString(size));

				    // Do the scan
				    int j = 0;
				    while (j < size)
				    {
					int childID = childrenDocs.toInteger(j, "ID");

					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Found a child of folder "+Integer.toString(vol)+":"+Integer.toString(objID)+" : ID="+Integer.toString(childID));

					int subtype = childrenDocs.toInteger(j, "SubType");
					boolean childIsFolder = (subtype == LAPI_DOCUMENTS.FOLDERSUBTYPE || subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE ||
						subtype == LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE);

					// If it's a folder, we just let it through for now
					if (!childIsFolder && checkInclude(childrenDocs.toString(j,"Name") + "." + childrenDocs.toString(j,"FileType"), spec) == false)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" was excluded by inclusion criteria");
						j++;
						continue;
					}

					if (childIsFolder)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a folder, project, or compound document; adding a reference");
						if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
						{
							// If we pick up a project object, we need to describe the volume object (which
							// will be the root of all documents beneath)
							activities.addDocumentReference("F"+new Integer(childID).toString()+":"+new Integer(-childID).toString());
						}
						else
							activities.addDocumentReference("F"+new Integer(vol).toString()+":"+new Integer(childID).toString());
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Child identifier "+Integer.toString(childID)+" is a simple document; adding a reference");

						activities.addDocumentReference("D"+new Integer(vol).toString()+":"+new Integer(childID).toString());
					}

					j++;
				    }
				    break;
				  }
				  catch (InterruptedException e)
				  {
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				  }
				  catch (RuntimeException e)
				  {
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
					continue;
				  }
				}
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Done processing folder "+Integer.toString(vol)+":"+Integer.toString(objID));
			}
			else
			{
				// It's a known file, and we've already checked whether it's allowed or not (except any
				// checks based on the file data)

				if (doScanOnly == false)
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
					if (checkIngest(objID,spec))
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Decided to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID));

						// Grab the access tokens for this file from the version string, inside ingest method.
						ingestFromLiveLink(documentIdentifiers[i],versions[i],activities,desc,sDesc);
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Livelink: Decided not to ingest document "+Integer.toString(vol)+":"+Integer.toString(objID)+" - Did not match ingestion criteria");

					}
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Done processing document "+Integer.toString(vol)+":"+Integer.toString(objID));
				}
			}
			i++;
		}
	}

	/** Get the maximum number of documents to amalgamate together into one batch, for this connector.
	*@return the maximum number. 0 indicates "unlimited".
	*/
	public int getMaxDocumentRequest()
	{
		// Intrinsically, Livelink doesn't batch well.  Multiple chunks have no advantage over one-at-a-time requests,
		// since apparently the Livelink API does not support multiples.  HOWEVER - when metadata is considered,
		// it becomes worthwhile, because we will be able to do what is needed to look up the correct CATID node
		// only once per n requests!  So it's a tradeoff between the advantage gained by threading, and the
		// savings gained by CATID lookup.
		// Note that at Shell, the fact that the network hiccups a lot makes it better to choose a smaller value.
		return 6;
	}

	// The following public methods are NOT part of the interface.  They are here so that the UI can present information
	// that will allow users to select what they need.

	protected final static String CATEGORY_NAME = "CATEGORY";
	protected final static String ENTWKSPACE_NAME = "ENTERPRISE";

	/** Get the allowed workspace names.
	*@return a list of workspace names.
	*/
	public String[] getWorkspaceNames()
		throws MetacartaException
	{
		return new String[]{CATEGORY_NAME,ENTWKSPACE_NAME};
	}

	/** Given a path string, get a list of folders and projects under that node.
	*@param pathString is the current path (folder names and project names, separated by dots (.)).
	*@return a list of folder and project names, in sorted order, or null if the path was invalid.
	*/
	public String[] getChildFolderNames(String pathString)
		throws MetacartaException
	{
		try
		{
			getSession();
			return getChildFolders(pathString);
		}
		catch (ServiceInterruption e)
		{
			throw new MetacartaException("Livelink server seems to be down: "+e.getMessage());
		}
	}


	/** Given a path string, get a list of categories under that node.
	*@param pathString is the current path (folder names and project names, separated by dots (.)).
	*@return a list of category names, in sorted order, or null if the path was invalid.
	*/
	public String[] getChildCategoryNames(String pathString)
		throws MetacartaException
	{
		try
		{
			return getChildCategories(pathString);
		}
		catch (ServiceInterruption e)
		{
			throw new MetacartaException("Livelink server seems to be down: "+e.getMessage());
		}
	}

	/** Given a category path, get a list of legal attribute names.
	*@param pathString is the current path of a category (with path components separated by dots).
	*@return a list of attribute names, in sorted order, or null of the path was invalid.
	*/
	public String[] getCategoryAttributes(String pathString)
		throws MetacartaException, ServiceInterruption
	{
		getSession();

		// Start at root
		RootValue rv = new RootValue(pathString);

		// Get the object id of the category the path describes
		int catObjectID = getCategoryId(rv);
		if (catObjectID == -1)
			return null;

		String[] rval = getCategoryAttributes(catObjectID);
		if (rval == null)
			return new String[0];
		return rval;
	}

	// Protected methods and classes


	/** Create the login URI.  This must be a relative URI.
	*/
	protected String createLivelinkLoginURI()
		throws MetacartaException
	{
	    try
	    {
		StringBuffer llURI = new StringBuffer();
		
		llURI.append(ingestCgiPath);
		llURI.append("?func=ll.login&CurrentClientTime=D%2F2005%2F3%2F9%3A13%3A16%3A30&NextURL=");
		llURI.append(URLEncoder.encode(ingestCgiPath,"UTF-8"));
		llURI.append("%3FRedirect%3D1&Username=");
		llURI.append(URLEncoder.encode(llServer.getLLUser(),"UTF-8"));
		llURI.append("&Password=");
		llURI.append(URLEncoder.encode(llServer.getLLPwd(),"UTF-8"));
		
		return llURI.toString();
	    }
	    catch (UnsupportedEncodingException e)
	    {
		throw new MetacartaException("Login URI setup error: "+e.getMessage(),e);
	    }
	}
	
	protected static class CloseThread extends Thread
	{
		protected InputStream is;
		protected Throwable exception = null;

		public CloseThread(InputStream is)
		{
			super();
			setDaemon(true);
			this.is = is;
		}
			
		public void run()
		{
			try
			{
				// Call the close method appropriately
				is.close();
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
			
		public Throwable getException()
		{
			return exception;
		}
	}

	protected static void closeViaThread(InputStream is)
		throws InterruptedException, IOException
	{
		CloseThread t = new CloseThread(is);
		try
		{
			t.start();
			t.join();
			Throwable thr = t.getException();
			if (thr != null)
			{
				if (thr instanceof IOException)
					throw (IOException)thr;
				else if (thr instanceof RuntimeException)
					throw (RuntimeException)thr;
				else
					throw (Error)thr;
			}
		}
		catch (InterruptedException e)
		{
			t.interrupt();
			// We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
			throw e;
		}
	}

	/**
	 * Connects to the specified Livelink document using HTTP protocol
	 * @param documentIdentifier is the document identifier (as far as the crawler knows).
	 * @param activities is the process activity structure, so we can ingest
	 */
	protected void ingestFromLiveLink(String documentIdentifier, String version,
		IProcessActivity activities,
		MetadataDescription desc, SystemMetadataDescription sDesc)
		throws MetacartaException, ServiceInterruption
	{

		String contextMsg = "for '"+documentIdentifier+"'";

		String ingestHttpAddress = convertToIngestURI(documentIdentifier);
		if (ingestHttpAddress == null)
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: No fetch URI "+contextMsg+" - not ingesting");
			return;
		}

		String viewHttpAddress = convertToViewURI(documentIdentifier);
		if (viewHttpAddress == null)
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: No view URI "+contextMsg+" - not ingesting");
			return;
		}

		RepositoryDocument rd = new RepositoryDocument();

		// Iterate over the metadata items.  These are organized by category
		// for speed of lookup.
		int objID = convertToObjectID(documentIdentifier);

		// Unpack version string
		int startPos = 0;

		// Metadata items first
		ArrayList metadataItems = new ArrayList();
		startPos = unpackList(metadataItems,version,startPos,'+');
		Iterator catIter = desc.getItems(metadataItems);
		while (catIter.hasNext())
		{
			MetadataItem item = (MetadataItem)catIter.next();
			MetadataPathItem pathItem = item.getPathItem();
			if (pathItem != null)
			{
				int catID = pathItem.getCatID();
				// grab the associated catversion
				LLValue catVersion = getCatVersion(objID,catID);
				if (catVersion != null)
				{
					// Go through attributes now
					Iterator attrIter = item.getAttributeNames();
					while (attrIter.hasNext())
					{
						String attrName = (String)attrIter.next();
						// Create a unique metadata name
						String metadataName = pathItem.getCatName()+":"+attrName;
						// Fetch the metadata and stuff it into the RepositoryData structure
						String[] metadataValue = getAttributeValue(catVersion,attrName);
						if (metadataValue != null)
							rd.addField(metadataName,metadataValue);
						else
							Logging.connectors.warn("Livelink: Metadata attribute '"+metadataName+"' does not seem to exist; please correct the job");
					}
				}
				
			}
		}

		// Unpack acls (conditionally)
		if (startPos < version.length())
		{
			char x = version.charAt(startPos++);
			if (x == '+')
			{
				ArrayList acls = new ArrayList();
				startPos = unpackList(acls,version,startPos,'+');
				// Turn into acls and add into description
				String[] aclArray = new String[acls.size()];
				int j = 0;
				while (j < aclArray.length)
				{
					aclArray[j] = (String)acls.get(j);
					j++;
				}
				rd.setACL(aclArray);
				
				StringBuffer denyBuffer = new StringBuffer();
				startPos = unpack(denyBuffer,version,startPos,'+');
				String denyAcl = denyBuffer.toString();
				String[] denyAclArray = new String[1];
				denyAclArray[0] = denyAcl;
				rd.setDenyACL(denyAclArray);
			}
		}

		// Add the path metadata item into the mix, if enabled
		String pathAttributeName = sDesc.getPathAttributeName();
		if (pathAttributeName != null && pathAttributeName.length() > 0)
		{
			String pathString = sDesc.getPathAttributeValue(documentIdentifier);
			if (pathString != null)
			{
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Path attribute name is '"+pathAttributeName+"'"+contextMsg+", value is '"+pathString+"'");
				rd.addField(pathAttributeName,pathString);
			}
		}

		// Set up connection
		HttpClient client = getInitializedClient(contextMsg);
	
		long currentTime;
		
        	if (Logging.connectors.isInfoEnabled())
			Logging.connectors.info("Livelink: " + ingestHttpAddress);

		try
		{
		    long startTime = System.currentTimeMillis();
		    String resultCode = "OK";
		    String resultDescription = null;
		    Long readSize = null;

		    // Set up fetch using our special stuff if it's https
		    GetMethod method = new GetMethod(ingestHttpAddress);
		    try
		    {
			method.getParams().setParameter("http.socket.timeout", new Integer(300000));
		        method.setFollowRedirects(true);


		        int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),method);
		        switch (statusCode)
		        {
			case 500:
			case 502:
			    Logging.connectors.warn("Livelink: Service interruption during fetch "+contextMsg+" with Livelink HTTP Server, retrying...");
			    throw new ServiceInterruption("Service interruption during fetch",new MetacartaException(Integer.toString(statusCode)+" error while fetching"),System.currentTimeMillis()+60000L,
				System.currentTimeMillis()+600000L,-1,true);

		        case HttpStatus.SC_UNAUTHORIZED:
			    Logging.connectors.warn("Livelink: Document fetch unauthorized for "+ingestHttpAddress+" ("+contextMsg+")");
			    // Since we logged in, we should fail here if the ingestion user doesn't have access to the
			    // the document, but if we do, don't fail hard.
			    resultCode = "UNAUTHORIZED";
			    return;

		        case HttpStatus.SC_OK:
			    if (Logging.connectors.isDebugEnabled())
				    Logging.connectors.debug("Livelink: Created http document connection to Livelink "+contextMsg);
			    long dataSize = method.getResponseContentLength();
			    // The above replaces this, which required another access:
			    // long dataSize = (long)value.toInteger("DataSize");
			    // A non-existent content length will cause a value of -1 to be returned.  This seems to indicate that the session login did not work right.
			    if (dataSize >= 0)
			    {
				    if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Content length from livelink server "+contextMsg+"' = "+new Long(dataSize).toString());

				    try
				    {
					InputStream is = method.getResponseBodyAsStream();
					try
					{
					    rd.setBinary(is,dataSize);

					    activities.ingestDocument(documentIdentifier,version,viewHttpAddress,rd);

					    if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Livelink: Ingesting done "+contextMsg);

					}
					finally
					{
                                            // Close stream via thread, since otherwise this can hang
					    closeViaThread(is);
					}
				    }
				    catch (java.net.SocketTimeoutException e)
				    {
					resultCode = "DATATIMEOUT";
					resultDescription = e.getMessage();
					currentTime = System.currentTimeMillis();
					Logging.connectors.warn("Livelink: Livelink socket timed out ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
					throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
				    }
				    catch (java.net.SocketException e)
				    {
					resultCode = "DATASOCKETERROR";
					resultDescription = e.getMessage();
					currentTime = System.currentTimeMillis();
					Logging.connectors.warn("Livelink: Livelink socket error ingesting from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
					throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
				    }
				    catch (javax.net.ssl.SSLHandshakeException e)
				    {
					resultCode = "DATASSLHANDSHAKEERROR";
					resultDescription = e.getMessage();
					currentTime = System.currentTimeMillis();
					Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
					throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
				    }
				    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
				    {
					resultCode = "CONNECTTIMEOUT";
					resultDescription = e.getMessage();
					currentTime = System.currentTimeMillis();
					Logging.connectors.warn("Livelink: Livelink socket timed out connecting to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
					throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
				    }
				    catch (InterruptedException e)
				    {
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				    }
				    catch (InterruptedIOException e)
				    {
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				    }
				    catch (IOException e)
				    {
					resultCode = "DATAEXCEPTION";
					resultDescription = e.getMessage();
					// Treat unknown error ingesting data as a transient condition
					currentTime = System.currentTimeMillis();
					Logging.connectors.warn("Livelink: IO exception ingesting "+contextMsg+": "+e.getMessage(),e);
					throw new ServiceInterruption("IO exception ingesting "+contextMsg+": "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,false);
				    }
				    readSize = new Long(dataSize);
			    }
			    else
			    {
				    resultCode = "SESSIONLOGINFAILED";
			    }
			    break;
		        case HttpStatus.SC_BAD_REQUEST:
		        case HttpStatus.SC_USE_PROXY:
		        case HttpStatus.SC_GONE:
			    resultCode = "ERROR "+Integer.toString(statusCode);
			    throw new MetacartaException("Unrecoverable request failure; error = "+Integer.toString(statusCode));
		        default:
			    resultCode = "UNKNOWN";
			    Logging.connectors.warn("Livelink: Attempt to retrieve document from '"+ingestHttpAddress+"' received a response of "+Integer.toString(statusCode)+"; retrying in one minute");
			    currentTime = System.currentTimeMillis();
			    throw new ServiceInterruption("Fetch failed; retrying in 1 minute",new MetacartaException("Fetch failed with unknown code "+Integer.toString(statusCode)),
				currentTime+60000L,currentTime+600000L,-1,true);
		        }
		    }
		    catch (InterruptedException e)
		    {
			// Drop the connection on the floor
			method = null;
			throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
		    }
		    catch (java.net.SocketTimeoutException e)
		    {
			Logging.connectors.warn("Livelink: Socket timed out reading from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
			resultCode = "TIMEOUT";
			resultDescription = e.getMessage();
			currentTime = System.currentTimeMillis();
			throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
		    }
		    catch (java.net.SocketException e)
		    {
			Logging.connectors.warn("Livelink: Socket error reading from Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
			resultCode = "SOCKETERROR";
			resultDescription = e.getMessage();
			currentTime = System.currentTimeMillis();
			throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
		    }
		    catch (javax.net.ssl.SSLHandshakeException e)
		    {
			currentTime = System.currentTimeMillis();
			Logging.connectors.warn("Livelink: SSL handshake failed "+contextMsg+": "+e.getMessage(),e);
			resultCode = "SSLHANDSHAKEERROR";
			resultDescription = e.getMessage();
			throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
		    }
		    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
		    {
			Logging.connectors.warn("Livelink: Connect timed out reading from the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
			resultCode = "CONNECTTIMEOUT";
			resultDescription = e.getMessage();
			currentTime = System.currentTimeMillis();
			throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
		    }
		    catch (InterruptedIOException e)
		    {
			throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
		    }
		    catch (IOException e)
		    {
			resultCode = "EXCEPTION";
			resultDescription = e.getMessage();
			throw new MetacartaException("Exception getting response "+contextMsg+": "+e.getMessage(), e);
		    }
		    finally
		    {
			if (method != null)
			{
				method.releaseConnection();
				activities.recordActivity(new Long(startTime),ACTIVITY_FETCH,readSize,Integer.toString(objID),resultCode,resultDescription,null);
			}
		    }
		}
		catch (IllegalStateException e)
		{
			Logging.connectors.error("Livelink: State exception dealing with '"+ingestHttpAddress+"': "+e.getMessage(),e);
			throw new MetacartaException("State exception dealing with '"+ingestHttpAddress+"': "+e.getMessage(),e);
		}
	}

	/** Initialize the host configuration */
	protected HostConfiguration getHostConfiguration(String contextMsg)
	{
		HostConfiguration clientConf = new HostConfiguration();
		// clientConf.setLocalAddress(currentAddr);
		clientConf.setParams(new HostParams());
		clientConf.setHost(llServer.getHost(),ingestPortNumber,myFactory.getProtocol(ingestProtocol));
		return clientConf;
	}
	
	/** Initialize a livelink client connection */
	protected HttpClient getInitializedClient(String contextMsg)
		throws ServiceInterruption, MetacartaException
	{
        	HttpClient client = new HttpClient(connectionManager);
		client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.PROTOCOL_FACTORY,myFactory);
		client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.ALLOW_CIRCULAR_REDIRECTS,new Boolean(true));
		
		long currentTime;
		
		if (ntlmDomain != null)
		{
			// Set the NTLM credentials
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: Setting up NTLM credentials "+contextMsg);
			client.getState().setCredentials(AuthScope.ANY,
            			new NTCredentials(ntlmUsername,ntlmPassword,currentHost,ntlmDomain));
		}      

		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug("Livelink: Session authenticating via http "+contextMsg+"...");
		try
		{
			GetMethod authget = new GetMethod(createLivelinkLoginURI());
        		try
			{
				authget.getParams().setParameter("http.socket.timeout", new Integer(60000));
	       	 		authget.setFollowRedirects(true);
				if (Logging.connectors.isDebugEnabled())
	        			Logging.connectors.debug("Livelink: Created new GetMethod "+contextMsg+"; executing authentication method");
        			int statusCode = executeMethodViaThread(client,getHostConfiguration(contextMsg),authget);
        	
				if (statusCode == 502 || statusCode == 500)
				{
					Logging.connectors.warn("Livelink: Service interruption during authentication "+contextMsg+" with Livelink HTTP Server, retrying...");
					currentTime = System.currentTimeMillis();
					throw new ServiceInterruption("502 error during authentication",new MetacartaException("502 error while authenticating"),
						currentTime+60000L,currentTime+600000L,-1,true);
				}
				if (statusCode != HttpStatus.SC_OK)
				{
					Logging.connectors.error("Livelink: Failed to authenticate "+contextMsg+" against Livelink HTTP Server; Status code: " + statusCode);
					// Ok, so we didn't get in - simply do not ingest
					if (statusCode == HttpStatus.SC_UNAUTHORIZED)
						throw new MetacartaException("Session authorization failed with a 401 code; are credentials correct?");
					else
						throw new MetacartaException("Session authorization failed with code "+Integer.toString(statusCode));
				}
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Retrieving authentication response "+contextMsg+"");
				authget.getResponseBodyAsStream();
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Livelink: Authentication response retrieved "+contextMsg+"");
			
        		}
			catch (InterruptedException e)
			{
				// Drop the connection on the floor
				authget = null;
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (java.net.SocketTimeoutException e)
			{
				currentTime = System.currentTimeMillis();
				Logging.connectors.warn("Livelink: Socket timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
				throw new ServiceInterruption("Socket timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
			}
			catch (java.net.SocketException e)
			{
				currentTime = System.currentTimeMillis();
				Logging.connectors.warn("Livelink: Socket error authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
				throw new ServiceInterruption("Socket error: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
			}
			catch (javax.net.ssl.SSLHandshakeException e)
			{
				currentTime = System.currentTimeMillis();
				Logging.connectors.warn("Livelink: SSL handshake failed authenticating "+contextMsg+": "+e.getMessage(),e);
				throw new ServiceInterruption("SSL handshake error: "+e.getMessage(),e,currentTime+60000L,currentTime+300000L,-1,true);
			}
			catch (org.apache.commons.httpclient.ConnectTimeoutException e)
			{
				currentTime = System.currentTimeMillis();
				Logging.connectors.warn("Livelink: Connect timed out authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
				throw new ServiceInterruption("Connect timed out: "+e.getMessage(),e,currentTime+300000L,currentTime+6*3600000L,-1,true);
			}
			catch (InterruptedIOException e)
			{
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (IOException e)
			{
        			Logging.connectors.error("Livelink: IO exception when authenticating to the Livelink HTTP Server "+contextMsg+": "+e.getMessage(), e);
        			throw new MetacartaException("Unable to communicate with the Livelink HTTP Server: "+e.getMessage(), e);
        	
			}
			finally
			{
				if (authget != null)
					authget.releaseConnection();
			}
		}
		catch (IllegalStateException e)
		{
			Logging.connectors.error("Livelink: State exception dealing with '"+createLivelinkLoginURI()+"'",e);
			throw new MetacartaException("State exception dealing with login URI: "+e.getMessage(),e);
		}

		return client;
	}

	/** Pack category and attribute */
	protected static String packCategoryAttribute(String category, String attribute)
	{
		StringBuffer sb = new StringBuffer();
		pack(sb,category,':');
		pack(sb,attribute,':');
		return sb.toString();
	}

	/** Unpack category and attribute */
	protected static void unpackCategoryAttribute(StringBuffer category, StringBuffer attribute, String value)
	{
		int startPos = 0;
		startPos = unpack(category,value,startPos,':');
		startPos = unpack(attribute,value,startPos,':');
	}

	/** Stuffer for packing a single string with an end delimiter */
	protected static void pack(StringBuffer output, String value, char delimiter)
	{
		int i = 0;
		while (i < value.length())
		{
			char x = value.charAt(i++);
			if (x == '\\' || x == delimiter)
				output.append('\\');
			output.append(x);
		}
		output.append(delimiter);
	}

	/** Unstuffer for the above. */
	protected static int unpack(StringBuffer sb, String value, int startPosition, char delimiter)
	{
		while (startPosition < value.length())
		{
			char x = value.charAt(startPosition++);
			if (x == '\\')
			{
				if (startPosition < value.length())
					x = value.charAt(startPosition++);
			}
			else if (x == delimiter)
				break;
			sb.append(x);
		}
		return startPosition;
	}

	/** Stuffer for packing lists of fixed length */
	protected static void packFixedList(StringBuffer output, String[] values, char delimiter)
	{
		int i = 0;
		while (i < values.length)
		{
			pack(output,values[i++],delimiter);
		}
	}

	/** Unstuffer for unpacking lists of fixed length */
	protected static int unpackFixedList(String[] output, String value, int startPosition, char delimiter)
	{
		StringBuffer sb = new StringBuffer();
		int i = 0;
		while (i < output.length)
		{
			sb.setLength(0);
			startPosition = unpack(sb,value,startPosition,delimiter);
			output[i++] = sb.toString();
		}
		return startPosition;
	}

	/** Stuffer for packing lists of variable length */
	protected static void packList(StringBuffer output, ArrayList values, char delimiter)
	{
		pack(output,Integer.toString(values.size()),delimiter);
		int i = 0;
		while (i < values.size())
		{
			pack(output,values.get(i++).toString(),delimiter);
		}
	}

	/** Another stuffer for packing lists of variable length */
	protected static void packList(StringBuffer output, String[] values, char delimiter)
	{
		pack(output,Integer.toString(values.length),delimiter);
		int i = 0;
		while (i < values.length)
		{
			pack(output,values[i++],delimiter);
		}
	}

	/** Unstuffer for unpacking lists of variable length.
	*@param value is the value to unpack.
	*@param startPosition is the place to start the unpack.
	*@param delimiter is the character to use between values.
	*@param endChar is the character to use to mark the end.
	*@return the next position beyond the end of the list.
	*/
	protected static int unpackList(ArrayList output, String value, int startPosition, char delimiter)
	{
		StringBuffer sb = new StringBuffer();
		startPosition = unpack(sb,value,startPosition,delimiter);
		try
		{
			int count = Integer.parseInt(sb.toString());
			int i = 0;
			while (i < count)
			{
				sb.setLength(0);
				startPosition = unpack(sb,value,startPosition,delimiter);
				output.add(sb.toString());
				i++;
			}
		}
		catch (NumberFormatException e)
		{
		}
		return startPosition;
	}
	
	/** Given a path string, get a list of folders and projects under that node.
	*@param pathString is the current path (folder names and project names, separated by dots (.)).
	*@return a list of folder and project names, in sorted order, or null if the path was invalid.
	*/
	protected String[] getChildFolders(String pathString)
		throws MetacartaException, ServiceInterruption
	{
	  RootValue rv = new RootValue(pathString);

	  // Get the volume, object id of the folder/project the path describes
	  VolumeAndId vid = getPathId(rv);
	  if (vid == null)
		return null;

	  String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE + 
		" or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";

	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
				    
		LLValue children = t.getResponse();

		String[] rval = new String[children.size()];
		int j = 0;
		while (j < children.size())
		{
			rval[j] = children.toString(j,"Name");
			j++;
		}
		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}


	/** Given a path string, get a list of categories under that node.
	*@param pathString is the current path (folder names and project names, separated by dots (.)).
	*@return a list of category names, in sorted order, or null if the path was invalid.
	*/
	protected String[] getChildCategories(String pathString)
		throws MetacartaException, ServiceInterruption
	{
	  // Start at root
	  RootValue rv = new RootValue(pathString);

	  // Get the volume, object id of the folder/project the path describes
	  VolumeAndId vid = getPathId(rv);
	  if (vid == null)
		return null;
	  
	  // We want only folders that are children of the current object and which match the specified subfolder
	  String filterString = "SubType="+ LAPI_DOCUMENTS.CATEGORYSUBTYPE;

	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    ListObjectsThread t = new ListObjectsThread(vid.getVolumeID(), vid.getPathId(), filterString);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
				    
		LLValue children = t.getResponse();

		String[] rval = new String[children.size()];
		int j = 0;
		while (j < children.size())
		{
			rval[j] = children.toString(j,"Name");
			j++;
		}
		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	protected class GetCategoryAttributesThread extends Thread
	{
		protected int catObjectID;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetCategoryAttributesThread(int catObjectID)
		{
			super();
			setDaemon(true);
			this.catObjectID = catObjectID;
		}
		
		public void run()
		{
			try
			{
				LLValue catID = new LLValue();
				catID.setAssoc();
				catID.add("ID", catObjectID);
				catID.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

				LLValue catVersion = new LLValue();
				int status = LLDocs.FetchCategoryVersion(catID,catVersion);
				if (status == 107105 || status == 107106)
					return;
				if (status != 0)
				{
					throw new MetacartaException("Error getting category version: "+Integer.toString(status));
				}

				LLValue children = new LLValue();
				status = LLAttributes.AttrListNames(catVersion,null,children);
				if (status != 0)
				{
					throw new MetacartaException("Error getting attribute names: "+Integer.toString(status));
				}
				rval = children;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}


	/** Given a category path, get a list of legal attribute names.
	*@param catObjectID is the object id of the category.
	*@return a list of attribute names, in sorted order, or null of the path was invalid.
	*/
	protected String[] getCategoryAttributes(int catObjectID)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetCategoryAttributesThread t = new GetCategoryAttributesThread(catObjectID);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
				    
		LLValue children = t.getResponse();
		if (children == null)
			return null;

		String[] rval = new String[children.size()];
		Enumeration en = children.enumerateValues();

		int j = 0;
		while (en.hasMoreElements())
		{
			LLValue v = (LLValue)en.nextElement();
			rval[j] = v.toString();
			j++;
		}
		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	protected class GetCategoryVersionThread extends Thread
	{
		protected int objID;
		protected int catID;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetCategoryVersionThread(int objID, int catID)
		{
			super();
			setDaemon(true);
			this.objID = objID;
			this.catID = catID;
		}
		
		public void run()
		{
			try
			{
				// Set up the right llvalues

				// Object ID
				LLValue objIDValue = new LLValue().setAssoc();
				objIDValue.add("ID", objID);
				// Current version, so don't set the "Version" field

				// CatID
				LLValue catIDValue = new LLValue().setAssoc();
				catIDValue.add("ID", catID);
				catIDValue.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

				LLValue rvalue = new LLValue();

				int status = LLDocs.GetObjectAttributesEx(objIDValue,catIDValue,rvalue);
				// If either the object is wrong, or the object does not have the specified category, return null.
				if (status == 103101 || status == 107205)
					return;

				if (status != 0)
				{
					throw new MetacartaException("Error retrieving category version: "+Integer.toString(status)+": "+llServer.getErrors());
				}

				rval = rvalue;

			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** Get a category version for document.
	*/
	protected LLValue getCatVersion(int objID, int catID)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetCategoryVersionThread t = new GetCategoryVersionThread(objID,catID);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
		return t.getResponse();
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (NullPointerException npe)
	    {
		// LAPI throws a null pointer exception under very rare conditions when the GetObjectAttributesEx is
		// called.  The conditions are not clear at this time - it could even be due to Livelink corruption.
		// However, I'm going to have to treat this as
		// indicating that this category version does not exist for this document.
		Logging.connectors.warn("Livelink: Null pointer exception thrown trying to get cat version for category "+
			Integer.toString(catID)+" for object "+Integer.toString(objID));
		return null;
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	protected class GetAttributeValueThread extends Thread
	{
		protected LLValue categoryVersion;
		protected String attributeName;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetAttributeValueThread(LLValue categoryVersion, String attributeName)
		{
			super();
			setDaemon(true);
			this.categoryVersion = categoryVersion;
			this.attributeName = attributeName;
		}
		
		public void run()
		{
			try
			{
				// Set up the right llvalues
				LLValue children = new LLValue();
				int status = LLAttributes.AttrGetValues(categoryVersion,attributeName,
					0,null,children);
				// "Not found" status - I don't know if it possible to get this here, but if so, behave civilly
				if (status == 103101)
					return;
				// This seems to be the real error LAPI returns if you don't have an attribute of this name
				if (status == 8000604)
					return;

				if (status != 0)
				{
					throw new MetacartaException("Error retrieving attribute value: "+Integer.toString(status)+": "+llServer.getErrors());
				}
				rval = children;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** Get an attribute value from a category version.
	*/
	protected String[] getAttributeValue(LLValue categoryVersion, String attributeName)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetAttributeValueThread t = new GetAttributeValueThread(categoryVersion, attributeName);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
		LLValue children = t.getResponse();
		if (children == null)
			return null;
		String[] rval = new String[children.size()];
		Enumeration en = children.enumerateValues();

		int j = 0;
		while (en.hasMoreElements())
		{
			LLValue v = (LLValue)en.nextElement();
			rval[j] = v.toString();
			j++;
		}
		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	protected class GetObjectRightsThread extends Thread
	{
		protected int vol;
		protected int objID;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetObjectRightsThread(int vol, int objID)
		{
			super();
			setDaemon(true);
			this.vol = vol;
			this.objID = objID;
		}
		
		public void run()
		{
			try
			{
				LLValue childrenObjects = new LLValue();
				int status = LLDocs.GetObjectRights(vol, objID, childrenObjects);
				// If the rights object doesn't exist, behave civilly
				if (status == 103101)
					return;

				if (status != 0)
				{
					throw new MetacartaException("Error retrieving document rights: "+Integer.toString(status)+": "+llServer.getErrors());
				}

				rval = childrenObjects;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** Get an object's rights.  This will be an array of right id's, including the special
	* ones defined by Livelink, or null will be returned (if the object is not found).
	*@param vol is the volume id
	*@param objID is the object id
	*@return the array.
	*/
	protected int[] getObjectRights(int vol, int objID)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetObjectRightsThread t = new GetObjectRightsThread(vol,objID);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}

		LLValue childrenObjects = t.getResponse();
		if (childrenObjects == null)
			return null;
		
		int size;
		if (childrenObjects.isRecord())
			size = 1;
		else if (childrenObjects.isTable())
			size = childrenObjects.size();
		else
			size = 0;

		int minPermission = LAPI_DOCUMENTS.PERM_SEE +
			LAPI_DOCUMENTS.PERM_SEECONTENTS;


		int j = 0;
		int count = 0;
		while (j < size)
		{
			int permission = childrenObjects.toInteger(j, "Permissions");
			// Only if the permission is "see contents" can we consider this
			// access token!
			if ((permission & minPermission) == minPermission)
				count++;
			j++;
		}

		int[] rval = new int[count];
		j = 0;
		count = 0;
		while (j < size)
		{
			int token = childrenObjects.toInteger(j, "RightID");
			int permission = childrenObjects.toInteger(j, "Permissions");
			// Only if the permission is "see contents" can we consider this
			// access token!
			if ((permission & minPermission) == minPermission)
				rval[count++] = token;
			j++;
		}
		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	protected class GetObjectInfoThread extends Thread
	{
		protected int vol;
		protected int id;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetObjectInfoThread(int vol, int id)
		{
			super();
			setDaemon(true);
			this.vol = vol;
			this.id = id;
		}
		
		public void run()
		{
			try
			{
				LLValue objinfo = new LLValue().setAssocNotSet();
				int status = LLDocs.GetObjectInfo(vol,id,objinfo);

				// Need to detect if object was deleted, and return null in this case!!!
				if (Logging.connectors.isDebugEnabled())
				{
					Logging.connectors.debug("Livelink: Status retrieved for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status));
				}

				if (status == 103101)
					return;

				// Status 103102 indicates that we could not retrieve the object contents, even though we could find the object.  I think it is safe to treat this as a
				// ServiceInterruption.
				if (status == 103102)
				{
					Logging.connectors.warn("Livelink: Interpreting LAPI error 103102 while fetching object "+Integer.toString(vol)+":"+Integer.toString(id)+" as a service interruption - retrying.");
					long currentTime = System.currentTimeMillis();
					throw new ServiceInterruption("Service interruption fetching object "+Integer.toString(vol)+":"+Integer.toString(id),
						new MetacartaException("Could not read object "+Integer.toString(vol)+":"+Integer.toString(id)),currentTime+60000L,
						currentTime+600000L,-1,false);
				}
				
				// This error means we don't have permission to get the object's status, apparently
				if (status < 0)
				{
					Logging.connectors.debug("Livelink: Object info inaccessable for object "+Integer.toString(vol)+":"+Integer.toString(id)+
						" ("+llServer.getErrors()+")");
					return;
				}

				if (status != 0)
				{
					throw new MetacartaException("Error retrieving document object "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
				}
				rval = objinfo;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** 
	* Returns an Assoc value object containing information
	* about the specified object.
	* @param vol is the volume id (which comes from the project)
	* @param id the object ID 
	* @return LLValue the LAPI value object, or null if object has been deleted (or doesn't exist)
	*/
	protected LLValue getObjectInfo(int vol, int id)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetObjectInfoThread t = new GetObjectInfoThread(vol,id);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof ServiceInterruption)
				throw (ServiceInterruption)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}
		return t.getResponse();
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	/** Build a set of actual acls given a set of rights */
	protected String[] lookupTokens(int[] rights, int vol, int objID)
		throws MetacartaException, ServiceInterruption
	{
		String[] convertedAcls = new String[rights.length];

		LLValue infoObject = null;
		int j = 0;
		int k = 0;
		while (j < rights.length)
		{
			int token = rights[j++];
			String tokenValue;
			// Consider this token
			switch (token)
			{
			case LAPI_DOCUMENTS.RIGHT_OWNER:
				// Look up user for current document (UserID attribute)
				if (infoObject == null)
				{
					infoObject = getObjectInfo(vol, objID);
					if (infoObject == null)
					{
						tokenValue = null;
						break;
					}
				}
				tokenValue = Integer.toString(infoObject.toInteger("UserID"));
				break;
			case LAPI_DOCUMENTS.RIGHT_GROUP:
				// Look up group for current document (GroupID attribute)
				if (infoObject == null)
				{
					infoObject = getObjectInfo(vol, objID);
					if (infoObject == null)
					{
						tokenValue = null;
						break;
					}
				}
				tokenValue = Integer.toString(infoObject.toInteger("GroupID"));
				break;
			case LAPI_DOCUMENTS.RIGHT_WORLD:
				// Add "Guest" token
				tokenValue = "GUEST";
				break;
			case LAPI_DOCUMENTS.RIGHT_SYSTEM:
				// Add "System" token
				tokenValue = "SYSTEM";
				break;
			default:
				tokenValue = Integer.toString(token);
				break;
			}

			// This might return a null if we could not look up the object corresponding to the right.  If so, it is safe to skip it because
			// that always RESTRICTS view of the object (maybe erroneously), but does not expand visibility.
			if (tokenValue != null)
				convertedAcls[k++] = tokenValue;
		}
		String[] actualAcls = new String[k];
		j = 0;
		while (j < k)
		{
			actualAcls[j] = convertedAcls[j];
			j++;
		}
		return actualAcls;
	}

	/** Get an object's standard path, given its ID.
	*/
	protected String getObjectPath(int id)
		throws MetacartaException, ServiceInterruption
	{
		int objectId = id;
		String path = null;
		while (true)
		{
			// Load the object. I'm told I can use zero for a volume ID safely.
			LLValue x = getObjectInfo(0,objectId);
			if (x == null)
			{
				// The document identifier describes a path that does not exist.
				// This is unexpected, but an exception would terminate the job, and we don't want that.
				Logging.connectors.warn("Livelink: Bad identifier found? "+Integer.toString(id)+" apparently does not exist, but need to look up its path");
				return null;
			}

			if (objectId == LLCATWK_ID)
				return CATEGORY_NAME + ":" + path;
			else if (objectId == LLENTWK_ID)
				return ENTWKSPACE_NAME + ":" + path;

			// Get the name attribute
			String name = x.toString("Name");
			if (path == null)
				path = name;
			else
				path = name + "/" + path;

			// Get the parentID attribute
			int parentID = x.toInteger("ParentID");
			if (parentID == -1)
			{
				// Oops, hit the top of the path without finding the workspace we're in.
				// No idea where it lives; note this condition and exit.
				Logging.connectors.warn("Livelink: Object ID "+Integer.toString(id)+" doesn't seem to live in enterprise or category workspace!  Path I got was '"+path+"'");
				return null;
			}
			objectId = parentID;
		}
	}

	protected class GetObjectCategoryIDsThread extends Thread
	{
		protected int vol;
		protected int id;
		protected Throwable exception = null;
		protected LLValue rval = null;
	
		public GetObjectCategoryIDsThread(int vol, int id)
		{
			super();
			setDaemon(true);
			this.vol = vol;
			this.id = id;
		}
		
		public void run()
		{
			try
			{
				// Object ID
				LLValue objIDValue = new LLValue().setAssocNotSet();
				objIDValue.add("ID", id);

				// Category ID List
				LLValue catIDList = new LLValue().setAssocNotSet();

				int status = LLDocs.ListObjectCategoryIDs(objIDValue,catIDList);

				// Need to detect if object was deleted, and return null in this case!!!
				if (Logging.connectors.isDebugEnabled())
				{
					Logging.connectors.debug("Livelink: Status value for getting object categories for "+Integer.toString(vol)+":"+Integer.toString(id)+" is: "+Integer.toString(status));
				}

				if (status == 103101)
					return;

				if (status != 0)
				{
					throw new MetacartaException("Error retrieving document categories for "+Integer.toString(vol)+":"+Integer.toString(id)+": status="+Integer.toString(status)+" ("+llServer.getErrors()+")");
				}
				rval = catIDList;
			}
			catch (Throwable e)
			{
				this.exception = e;
			}
		}
		
		public Throwable getException()
		{
			return exception;
		}
		
		public LLValue getResponse()
		{
			return rval;
		}
	}

	/** Get category IDs associated with an object.
	* @param vol is the volume ID
	* @param id the object ID 
	* @return an array of integers containing category identifiers, or null if the object is not found.
	*/
	protected int[] getObjectCategoryIDs(int vol, int id)
		throws MetacartaException, ServiceInterruption
	{
	  int sanityRetryCount = FAILURE_RETRY_COUNT;
	  while (true)
	  {
	    GetObjectCategoryIDsThread t = new GetObjectCategoryIDsThread(vol,id);
	    try
	    {
		t.start();
		t.join();
		Throwable thr = t.getException();
		if (thr != null)
		{
			if (thr instanceof RuntimeException)
				throw (RuntimeException)thr;
			else if (thr instanceof MetacartaException)
			{
				sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
				continue;
			}
			else
				throw (Error)thr;
		}

		LLValue catIDList = t.getResponse();
		if (catIDList == null)
			return null;
		
		int size = catIDList.size();

		if (Logging.connectors.isDebugEnabled())
		{
			Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(size)+" attached categories");
		}

		// Count the category ids
		int count = 0;
		int j = 0;
		while (j < size)
		{
			int type = catIDList.toValue(j).toInteger("Type");
			if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
				count++;
			j++;
		}

		int[] rval = new int[count];

		// Do the scan
		j = 0;
		count = 0;
		while (j < size)
		{
			int type = catIDList.toValue(j).toInteger("Type");
			if (type == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY)
			{
				int childID = catIDList.toValue(j).toInteger("ID");
				rval[count++] = childID;
			}
			j++;
		}

		if (Logging.connectors.isDebugEnabled())
		{
			Logging.connectors.debug("Livelink: Object "+Integer.toString(vol)+":"+Integer.toString(id)+" has "+Integer.toString(rval.length)+" attached library categories");
		}

		return rval;
	    }
	    catch (InterruptedException e)
	    {
		t.interrupt();
		throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
	    }
	    catch (RuntimeException e)
	    {
		sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
		continue;
	    }
          }
	}

	/** RootValue version of getPathId.
	*/
	protected VolumeAndId getPathId(RootValue rv)
		throws MetacartaException, ServiceInterruption
	{
		return getPathId(rv.getRootValue(),rv.getRemainderPath());
	}

	/**
	* Returns the object ID specified by the path name.
	* @param objInfo a value object containing information about root folder (or workspace) above the specified object
	* @param startPath is the folder name (a string with dots as separators)
	*/		
	protected VolumeAndId getPathId(LLValue objInfo, String startPath)
		throws MetacartaException, ServiceInterruption
	{
		// Grab the volume ID and starting object
		int obj = objInfo.toInteger("ID");
		int vol = objInfo.toInteger("VolumeID");

		// Pick apart the start path.  This is a string separated by slashes.
		int charindex = 0;
		while (charindex < startPath.length())
		{
			StringBuffer currentTokenBuffer = new StringBuffer();
			// Find the current token
			while (charindex < startPath.length())
			{
				char x = startPath.charAt(charindex++);
				if (x == '/')
					break;
				if (x == '\\')
				{
					// Attempt to escape what follows
					x = startPath.charAt(charindex);
					charindex++;
				}
				currentTokenBuffer.append(x);
			}
			
			String subFolder = currentTokenBuffer.toString();
			// We want only folders that are children of the current object and which match the specified subfolder
			String filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
				" or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ") and Name='" + subFolder + "'";

			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RuntimeException)
							throw (RuntimeException)thr;
						else if (thr instanceof MetacartaException)
						{
							sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
							continue;
						}
						else
							throw (Error)thr;
					}

					LLValue children = t.getResponse();
					if (children == null)
						return null;
					
					// If there is one child, then we are okay.
					if (children.size() == 1)
					{
						// New starting point is the one we found.
						obj = children.toInteger(0, "ID");
						int subtype = children.toInteger(0, "SubType");
						if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
						{
							vol = obj;
							obj = -obj;
						}
					}
					else
					{
						// Couldn't find the path.  Instead of throwing up, return null to indicate
						// illegal node.
						return null;
					}
					break;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RuntimeException e)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
					continue;

				}
			}

		}
		return new VolumeAndId(vol,obj);
	}

	/** Rootvalue version of getCategoryId.
	*/
	protected int getCategoryId(RootValue rv)
		throws MetacartaException, ServiceInterruption
	{
		return getCategoryId(rv.getRootValue(),rv.getRemainderPath());
	}

	/**
	* Returns the category ID specified by the path name.
	* @param objInfo a value object containing information about root folder (or workspace) above the specified object
	* @param startPath is the folder name, ending in a category name (a string with slashes as separators)
	*/		
	protected int getCategoryId(LLValue objInfo, String startPath)
		throws MetacartaException, ServiceInterruption
	{
		// Grab the volume ID and starting object
		int obj = objInfo.toInteger("ID");
		int vol = objInfo.toInteger("VolumeID");

		// Pick apart the start path.  This is a string separated by slashes.
		if (startPath.length() == 0)
			return -1;

		int charindex = 0;
		while (charindex < startPath.length())
		{
			StringBuffer currentTokenBuffer = new StringBuffer();
			// Find the current token
			while (charindex < startPath.length())
			{
				char x = startPath.charAt(charindex++);
				if (x == '/')
					break;
				if (x == '\\')
				{
					// Attempt to escape what follows
					x = startPath.charAt(charindex);
					charindex++;
				}
				currentTokenBuffer.append(x);
			}
			String subFolder = currentTokenBuffer.toString();
			String filterString;

			// We want only folders that are children of the current object and which match the specified subfolder
			if (charindex < startPath.length())
				filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
					" or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";
			else
				filterString = "SubType="+LAPI_DOCUMENTS.CATEGORYSUBTYPE;

			filterString += " and Name='" + subFolder + "'";
			
			int sanityRetryCount = FAILURE_RETRY_COUNT;
			while (true)
			{
				ListObjectsThread t = new ListObjectsThread(vol,obj,filterString);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RuntimeException)
							throw (RuntimeException)thr;
						else if (thr instanceof MetacartaException)
						{
							sanityRetryCount = assessRetry(sanityRetryCount,(MetacartaException)thr);
							continue;
						}
						else
							throw (Error)thr;
					}

					LLValue children = t.getResponse();
					if (children == null)
						return -1;

					// If there is one child, then we are okay.
					if (children.size() == 1)
					{
						// New starting point is the one we found.
						obj = children.toInteger(0, "ID");
						int subtype = children.toInteger(0, "SubType");
						if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
						{
							vol = obj;
							obj = -obj;
						}
					}
					else
					{
						// Couldn't find the path.  Instead of throwing up, return null to indicate
						// illegal node.
						return -1;
					}
					break;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RuntimeException e)
				{
					sanityRetryCount = handleLivelinkRuntimeException(e,sanityRetryCount,true);
					continue;
				}
			}
	 	}
		return obj;
	}

	// Protected static methods

	/** Convert the document specification to a set of query clauses meant to match file names.
	*@param spec is the specification string.
	*@return the correct part of the filter string.  Empty string is returned if there is no filtering.
	*/
	protected static String buildFilterString(DocumentSpecification spec)
	{
		StringBuffer rval = new StringBuffer();


		boolean first = true;
		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode sn = spec.getChild(i++);
			if (sn.getType().equals("include"))
			{
				String includeMatch = sn.getAttributeValue("filespec");
				if (includeMatch != null)
				{
					// Peel off the extension
					int index = includeMatch.lastIndexOf(".");
					if (index != -1)
					{
						String type = includeMatch.substring(index+1).toLowerCase().replace('*','%');
						if (first)
							first = false;
						else
							rval.append(" or ");
						rval.append("lower(FileType) like '").append(type).append("'");
					}
				}
			}
		}
		String filterStringPiece = rval.toString();
		if (filterStringPiece.length() == 0)
			return "0>1";
		StringBuffer sb = new StringBuffer();
		sb.append("SubType=").append(new Integer(LAPI_DOCUMENTS.FOLDERSUBTYPE).toString());
		sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE).toString());
		sb.append(" or SubType=").append(new Integer(LAPI_DOCUMENTS.PROJECTSUBTYPE).toString());
		sb.append(" or (SubType=").append(new Integer(LAPI_DOCUMENTS.DOCUMENTSUBTYPE).toString());
		sb.append(" and (");
		// Walk through the document spec to find the documents that match under the specified root
		// include lower(column)=spec
		sb.append(filterStringPiece);
		sb.append("))");
		return sb.toString();
	}


	/** Grab forced acl out of document specification.
	*@param spec is the document specification.
	*@return the acls.
	*/
	protected static String[] getAcls(DocumentSpecification spec)
	{
		HashMap map = new HashMap();
		int i = 0;
		boolean securityOn = true;
		while (i < spec.getChildCount())
		{
			SpecificationNode sn = spec.getChild(i++);
			if (sn.getType().equals("access"))
			{
				String token = sn.getAttributeValue("token");
				map.put(token,token);
			}
			else if (sn.getType().equals("security"))
			{
				String value = sn.getAttributeValue("value");
				if (value.equals("on"))
					securityOn = true;
				else if (value.equals("off"))
					securityOn = false;
			}
		}
		if (!securityOn)
			return null;

		String[] rval = new String[map.size()];
		Iterator iter = map.keySet().iterator();
		i = 0;
		while (iter.hasNext())
		{
			rval[i++] = (String)iter.next();
		}
		return rval;
	}

	/** Check if a file or directory should be included, given a document specification.
	*@param filename is the name of the "file".
	*@param documentSpecification is the specification.
	*@return true if it should be included.
	*/
	protected static boolean checkInclude(String filename, DocumentSpecification documentSpecification)
		throws MetacartaException
	{
		// Scan includes to insure we match
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i);
			if (sn.getType().equals("include"))
			{
				String filespec = sn.getAttributeValue("filespec");
				// If it matches, we can exit this loop.
				if (checkMatch(filename,0,filespec))
					break;
			}
			i++;
		}
		if (i == documentSpecification.getChildCount())
			return false;

		// We matched an include.  Now, scan excludes to ditch it if needed.
		i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i);
			if (sn.getType().equals("exclude"))
			{
				String filespec = sn.getAttributeValue("filespec");
				// If it matches, we return false.
				if (checkMatch(filename,0,filespec))
					return false;
			}
			i++;
		}

		// System.out.println("Match!");
		return true;
	}

	/** Check if a file should be ingested, given a document specification.  It is presumed that
	* documents that pass checkInclude() will be checked with this method.
	*@param objID is the file ID.
	*@param documentSpecification is the specification.
	*/
	protected boolean checkIngest(int objID, DocumentSpecification documentSpecification)
		throws MetacartaException
	{
		// Since the only exclusions at this point are not based on file contents, this is a no-op.
		return true;
	}

	/** Check a match between two strings with wildcards.
	*@param sourceMatch is the expanded string (no wildcards)
	*@param sourceIndex is the starting point in the expanded string.
	*@param match is the wildcard-based string.
	*@return true if there is a match.
	*/
	protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
	{
		// Note: The java regex stuff looks pretty heavyweight for this purpose.
		// I've opted to try and do a simple recursive version myself, which is not compiled.
		// Basically, the match proceeds by recursive descent through the string, so that all *'s cause
		// recursion.
		boolean caseSensitive = false;

		return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
	}

	/** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
	* strings in their entirety in a matched way.
	*@param caseSensitive is true if file names are case sensitive.
	*@param sourceMatch is the source string (w/o wildcards)
	*@param sourceIndex is the current point in the source string.
	*@param match is the match string (w/wildcards)
	*@param matchIndex is the current point in the match string.
	*@return true if there is a match.
	*/
	protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
		String match, int matchIndex)
	{
		// Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
		// 	" against '"+match+"' position "+Integer.toString(matchIndex));

		// Match up through the next * we encounter
		while (true)
		{
			// If we've reached the end, it's a match.
			if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
				return true;
			// If one has reached the end but the other hasn't, no match
			if (match.length() == matchIndex)
				return false;
			if (sourceMatch.length() == sourceIndex)
			{
				if (match.charAt(matchIndex) != '*')
					return false;
				matchIndex++;
				continue;
			}
			char x = sourceMatch.charAt(sourceIndex);
			char y = match.charAt(matchIndex);
			if (!caseSensitive)
			{
				if (x >= 'A' && x <= 'Z')
					x -= 'A'-'a';
				if (y >= 'A' && y <= 'Z')
					y -= 'A'-'a';
			}
			if (y == '*')
			{
				// Wildcard!
				// We will recurse at this point.
				// Basically, we want to combine the results for leaving the "*" in the match string
				// at this point and advancing the source index, with skipping the "*" and leaving the source
				// string alone.
				return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
					processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
			}
			if (y == '?' || x == y)
			{
				sourceIndex++;
				matchIndex++;
			}
			else
				return false;
		}
	}

	/** Class for returning volume id/folder id combination on path lookup.
	*/
	protected static class VolumeAndId
	{
		protected int volumeID;
		protected int folderID;

		public VolumeAndId(int volumeID, int folderID)
		{
			this.volumeID = volumeID;
			this.folderID = folderID;
		}

		public int getVolumeID()
		{
			return volumeID;
		}

		public int getPathId()
		{
			return folderID;
		}
	}

	/** Class that describes a metadata catid and path.
	*/
	protected static class MetadataPathItem
	{
		protected int catID;
		protected String catName;

		/** Constructor.
		*/
		public MetadataPathItem(int catID, String catName)
		{
			this.catID = catID;
			this.catName = catName;
		}

		/** Get the cat ID.
		*@return the id.
		*/
		public int getCatID()
		{
			return catID;
		}

		/** Get the cat name.
		*@return the category name path.
		*/
		public String getCatName()
		{
			return catName;
		}

	}

	/** Class that describes a metadata catid and attribute set.
	*/
	protected static class MetadataItem
	{
		protected MetadataPathItem pathItem;
		protected Map attributeNames = new HashMap();

		/** Constructor.
		*/
		public MetadataItem(MetadataPathItem pathItem)
		{
			this.pathItem = pathItem;
		}

		/** Add an attribute name.
		*/
		public void addAttribute(String attributeName)
		{
			attributeNames.put(attributeName,attributeName);
		}

		/** Get the path object.
		*@return the object.
		*/
		public MetadataPathItem getPathItem()
		{
			return pathItem;
		}

		/** Get an iterator over the attribute names.
		*@return the iterator.
		*/
		public Iterator getAttributeNames()
		{
			return attributeNames.keySet().iterator();
		}

	}

	/** Class that tracks paths associated with nodes, and also keeps track of the name
	* of the metadata attribute to use for the path.
	*/
	protected class SystemMetadataDescription
	{
		// The path attribute name
		protected String pathAttributeName;

		// The node ID to path name mapping (which acts like a cache)
		protected Map pathMap = new HashMap();

		// The path name map
		protected MatchMap matchMap = new MatchMap();

		/** Constructor */
		public SystemMetadataDescription(DocumentSpecification spec)
			throws MetacartaException
		{
			pathAttributeName = null;
			int i = 0;
			while (i < spec.getChildCount())
			{
				SpecificationNode n = spec.getChild(i++);
				if (n.getType().equals("pathnameattribute"))
					pathAttributeName = n.getAttributeValue("value");
				else if (n.getType().equals("pathmap"))
				{
					String pathMatch = n.getAttributeValue("match");
					String pathReplace = n.getAttributeValue("replace");
					matchMap.appendMatchPair(pathMatch,pathReplace);
				}
			}
		}

		/** Get the path attribute name.
		*@return the path attribute name, or null if none specified.
		*/
		public String getPathAttributeName()
		{
			return pathAttributeName;
		}

		/** Given an identifier, get the translated string that goes into the metadata.
		*/
		public String getPathAttributeValue(String documentIdentifier)
			throws MetacartaException, ServiceInterruption
		{
			String path = getNodePathString(documentIdentifier);
			if (path == null)
				return null;
			return matchMap.translate(path);
		}

		/** For a given node, get its path.
		*/
		public String getNodePathString(String documentIdentifier)
			throws MetacartaException, ServiceInterruption
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Looking up path for '"+documentIdentifier+"'");
			String path = (String)pathMap.get(documentIdentifier);
			if (path == null)
			{
				// Not yet present.  Look it up, recursively
				String identifierPart = documentIdentifier;
				// Get the current node's name first
				// D = Document; anything else = Folder
				if (identifierPart.startsWith("D") || identifierPart.startsWith("F"))
				{
					// Strip off the letter
					identifierPart = identifierPart.substring(1);
				}
				// See if there's a volume label; if not, use the default.
				int colonPosition = identifierPart.indexOf(":");
				int volumeID;
				int objectID;
				try
				{
					if (colonPosition == -1)
					{
						// Default volume ID
						volumeID = LLENTWK_VOL;
						objectID = Integer.parseInt(identifierPart);
					}
					else
					{
						volumeID = Integer.parseInt(identifierPart.substring(0,colonPosition));
						objectID = Integer.parseInt(identifierPart.substring(colonPosition+1));
					}
				}
				catch (NumberFormatException e)
				{
					throw new MetacartaException("Bad document identifier: "+e.getMessage(),e);
				}

				// Load the object
				LLValue x = getObjectInfo(volumeID,objectID);
				if (x == null)
				{
					// The document identifier describes a path that does not exist.
					// This is unexpected, but don't die: just log a warning and allow the higher level to deal with it.
					Logging.connectors.warn("Livelink: Bad document identifier: '"+documentIdentifier+"' apparently does not exist, but need to find its path");
					return null;
				}

				// Get the name attribute
				String name = x.toString("Name");
				// Get the parentID attribute
				int parentID = x.toInteger("ParentID");
				if (parentID == -1)
					path = name;
				else
				{
					String parentIdentifier = "F"+Integer.toString(volumeID)+":"+Integer.toString(parentID);
					String parentPath = getNodePathString(parentIdentifier);
					if (parentPath == null)
						return null;
					path = parentPath + "/"+ name;
				}

				pathMap.put(documentIdentifier,path);
			}

			return path;
		}
	}


	/** Class that manages to find catid's and attribute names that have been specified.
	* This accepts a part of the version string which contains the string-ified metadata
	* spec, rather than pulling it out of the document specification.  That guarantees that
	* the version string actually corresponds to the document that was ingested.
	*/
	protected class MetadataDescription
	{
		// This is a map of category name to category ID and attributes
		protected Map categoryMap = new HashMap();

		/** Constructor.
		*/
		public MetadataDescription()
		{
		}

		/** Iterate over the metadata items represented by the specified chunk of version string.
		*@return an iterator over MetadataItem objects.
		*/
		public Iterator getItems(ArrayList metadataItems)
			throws MetacartaException, ServiceInterruption
		{
			// This is the map that will be iterated over for a return value.
			// It gets built out of (hopefully cached) data from categoryMap.
			HashMap newMap = new HashMap();

			// Start at root
			LLValue rootValue = null;

			// Walk through string and process each metadata element in turn.
			int i = 0;
			while (i < metadataItems.size())
			{
				String metadataSpec = (String)metadataItems.get(i++);
				StringBuffer categoryBuffer = new StringBuffer();
				StringBuffer attributeBuffer = new StringBuffer();
				unpackCategoryAttribute(categoryBuffer,attributeBuffer,metadataSpec);
				String category = categoryBuffer.toString();
				String attributeName = attributeBuffer.toString();

				// If there's already an entry for this category in the return map, use it
				MetadataItem mi = (MetadataItem)newMap.get(category);
				if (mi == null)
				{
					// Now, look up the node information
					// Convert category to cat id.
					MetadataPathItem item = (MetadataPathItem)categoryMap.get(category);
					if (item == null)
					{
						RootValue rv = new RootValue(category);
						if (rootValue == null)
						{
							rootValue = rv.getRootValue();
						}

						// Get the object id of the category the path describes.
						// NOTE: We don't use the RootValue version of getCategoryId because
						// we want to use the cached value of rootValue, if it was around.
						int catObjectID = getCategoryId(rootValue,rv.getRemainderPath());
						if (catObjectID != -1)
						{
							item = new MetadataPathItem(catObjectID,rv.getRemainderPath());
							categoryMap.put(category,item);
						}
					}
					mi = new MetadataItem(item);
					newMap.put(category,mi);
				}
				// Add attribute name to category
				mi.addAttribute(attributeName);
			}

			return newMap.values().iterator();
		}
	
	}


	/** This class caches the category path strings associated with a given category object identifier.
	* The goal is to allow reasonably speedy lookup of the path name, so we can put it into the metadata part of the
	* version string.
	*/
	protected class CategoryPathAccumulator
	{
		// This is the map from category ID to category path name.
		// It's keyed by an Integer formed from the id, and has String values.
		protected HashMap categoryPathMap = new HashMap();

		// This is the map from category ID to attribute names.  Keyed
		// by an Integer formed from the id, and has a String[] value.
		protected HashMap attributeMap = new HashMap();

		/** Constructor */
		public CategoryPathAccumulator()
		{
		}

		/** Get a specified set of packed category paths with attribute names, given the category identifiers */
		public String[] getCategoryPathsAttributeNames(int[] catIDs)
			throws MetacartaException, ServiceInterruption
		{
			HashMap set = new HashMap();
			int i = 0;
			while (i < catIDs.length)
			{
				Integer key = new Integer(catIDs[i++]);
				String pathValue = (String)categoryPathMap.get(key);
				if (pathValue == null)
				{
					// Chase the path back up the chain
					pathValue = findPath(key.intValue());
					if (pathValue == null)
						continue;
					categoryPathMap.put(key,pathValue);
				}
				String[] attributeNames = (String[])attributeMap.get(key);
				if (attributeNames == null)
				{
					// Get the attributes for this category
					attributeNames = findAttributes(key.intValue());
					if (attributeNames == null)
						continue;
					attributeMap.put(key,attributeNames);
				}
				// Now, put the path and the attributes into the hash.
				int j = 0;
				while (j < attributeNames.length)
				{
					String metadataName = packCategoryAttribute(pathValue,attributeNames[j++]);
					set.put(metadataName,metadataName);
				}
			}

			String[] rval = new String[set.size()];
			i = 0;
			Iterator iter = set.keySet().iterator();
			while (iter.hasNext())
			{
				rval[i++] = (String)iter.next();
			}

			return rval;
		}

		/** Find a category path given a category ID */
		protected String findPath(int catID)
			throws MetacartaException, ServiceInterruption
		{
			return getObjectPath(catID);
		}

		/** Find a set of attributes given a category ID */
		protected String[] findAttributes(int catID)
			throws MetacartaException, ServiceInterruption
		{
			return getCategoryAttributes(catID);
		}

	}

	/** Class representing a root value object, plus remainder string.
	* This class peels off the workspace name prefix from a path string or
	* attribute string, and finds the right workspace root node and remainder
	* path.
	*/
	protected class RootValue
	{
		protected String workspaceName;
		protected LLValue rootValue = null;
		protected String remainderPath;

		/** Constructor.
		*@param pathString is the path string.
		*/
		public RootValue(String pathString)
		{
			int colonPos = pathString.indexOf(":");
			if (colonPos == -1)
			{
				remainderPath = pathString;
				workspaceName = ENTWKSPACE_NAME;
			}
			else
			{
				workspaceName = pathString.substring(0,colonPos);
				remainderPath = pathString.substring(colonPos+1);
			}
		}

		/** Get the path string.
		*@return the path string (without the workspace name prefix).
		*/
		public String getRemainderPath()
		{
			return remainderPath;
		}

		/** Get the root node.
		*@return the root node.
		*/
		public LLValue getRootValue()
			throws MetacartaException, ServiceInterruption
		{
			if (rootValue == null)
			{
				if (workspaceName.equals(CATEGORY_NAME))
					rootValue = getObjectInfo(LLCATWK_VOL,LLCATWK_ID);
				else if (workspaceName.equals(ENTWKSPACE_NAME))
					rootValue = getObjectInfo(LLENTWK_VOL,LLENTWK_ID);
				else
					throw new MetacartaException("Bad workspace name: "+workspaceName);
				if (rootValue == null)
				{
					Logging.connectors.warn("Livelink: Could not get workspace/volume ID!  Retrying...");
					// This cannot mean a real failure; it MUST mean that we have had an intermittent communication hiccup.  So, pass it off as a service interruption.
					throw new ServiceInterruption("Service interruption getting root value",new MetacartaException("Could not get workspace/volume id"),System.currentTimeMillis()+60000L,
						System.currentTimeMillis()+600000L,-1,true);
				}
			}

			return rootValue;
		}
	}

	/** HTTPClient secure socket factory, which implements SecureProtocolSocketFactory
	*/
	protected static class LivelinkSecureSocketFactory implements SecureProtocolSocketFactory
	{
		/** This is the javax.net socket factory.
		*/
		protected javax.net.ssl.SSLSocketFactory socketFactory;

		/** Constructor */
		public LivelinkSecureSocketFactory(javax.net.ssl.SSLSocketFactory socketFactory)
		{
			this.socketFactory = socketFactory;
		}

		public Socket createSocket(
        		String host,
        		int port,
        		InetAddress clientHost,
        		int clientPort)
        		throws IOException, UnknownHostException
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
        		return socketFactory.createSocket(
        		    host,
        		    port,
        		    clientHost,
        		    clientPort
        		);
		}

		public Socket createSocket(
		        final String host,
		        final int port,
		        final InetAddress localAddress,
		        final int localPort,
		        final HttpConnectionParams params
		    ) throws IOException, UnknownHostException, ConnectTimeoutException
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));

        		if (params == null)
			{
        		    throw new IllegalArgumentException("Parameters may not be null");
        		}
        		int timeout = params.getConnectionTimeout();
        		if (timeout == 0)
			{
        		    return createSocket(host, port, localAddress, localPort);
        		}
			else
			    throw new IllegalArgumentException("This implementation does not handle non-zero connection timeouts");
    		}

		public Socket createSocket(String host, int port)
        		throws IOException, UnknownHostException
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
        		return socketFactory.createSocket(
            			host,
            			port
        			);
    		}

		public Socket createSocket(
        		Socket socket,
        		String host,
        		int port,
        		boolean autoClose)
        		throws IOException, UnknownHostException
		{
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Livelink: Creating secure livelink connection to '"+host+"' on port "+Integer.toString(port));
        		return socketFactory.createSocket(
            			socket,
            			host,
            			port,
            			autoClose
        			);
    		}

		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof LivelinkSecureSocketFactory))
				return false;
			// Each object is unique
			return super.equals(obj);
		}

		public int hashCode()
		{
        		return super.hashCode();
    		}    

	}

	// Here's an interesting note.  All of the LAPI exceptions are subclassed off of RuntimeException.  This makes life
	// hell because there is no superclass exception to capture, and even tweaky server communication issues wind up throwing
	// uncaught RuntimeException's up the stack.
	//
	// To fix this rather bad design, all places that invoke LAPI need to catch RuntimeException and run it through the following
	// method for interpretation and logging.
	//
	
	/** Interpret runtimeexception to search for livelink API errors.  Throws an appropriately reinterpreted exception, or
	* just returns if the exception indicates that a short-cycle retry attempt should be made.  (In that case, the appropriate
	* wait has been already performed).
	*@param e is the RuntimeException caught
	*@param failIfTimeout is true if, for transient conditions, we want to signal failure if the timeout condition is acheived.
	*/
	protected int handleLivelinkRuntimeException(RuntimeException e, int sanityRetryCount, boolean failIfTimeout)
		throws MetacartaException, ServiceInterruption
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
			throw new MetacartaException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e,MetacartaException.REPOSITORY_CONNECTION_ERROR);
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
			throw new MetacartaException("Livelink API error: "+e.getMessage()+((details==null)?"":"; "+details),e);
		}
		else if (e instanceof com.opentext.api.LLIllegalOperationException)
		{
			// This usually means that LAPI has had a minor communication difficulty but hasn't reported it accurately.
			// We *could* throw a ServiceInterruption, but OpenText recommends to just retry almost immediately.
			String details = llServer.getErrors();
			return assessRetry(sanityRetryCount,new MetacartaException("Livelink API illegal operation error: "+e.getMessage()+((details==null)?"":"; "+details),e));
		}
		else if (e instanceof com.opentext.api.LLIOException)
		{
			// Treat this as a transient error; try again in 5 minutes, and only fail after 12 hours of trying
			
			// LAPI is returning errors that are not terribly explicit, and I don't have control over their wording, so check that server can be resolved by DNS,
			// so that a better error message can be returned.
			try
			{
				InetAddress.getByName(serverName);
			}
			catch (UnknownHostException e2)
			{
				throw new MetacartaException("Server name '"+serverName+"' cannot be resolved",e2);
			}
			
			long currentTime = System.currentTimeMillis();
			throw new ServiceInterruption(e.getMessage(),e,currentTime + 5*60000L,currentTime+12*60*60000L,-1,failIfTimeout);
		}
		else
			throw e;
	}
	
	/** Do a retry, or throw an exception if the retry count has been exhausted
	*/
	protected static int assessRetry(int sanityRetryCount, MetacartaException e)
		throws MetacartaException
	{
		if (sanityRetryCount == 0)
		{
			throw e;
		}
			
		sanityRetryCount--;

		try
		{
			Metacarta.sleep(1000L);
		}
		catch (InterruptedException e2)
		{
			throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
		}
		// Exit the method
		return sanityRetryCount;

	}
	
}


