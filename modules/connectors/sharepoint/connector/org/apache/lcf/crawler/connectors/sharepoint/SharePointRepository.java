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
package org.apache.lcf.crawler.connectors.sharepoint;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.Metacarta;
import org.apache.lcf.core.common.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.net.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.protocol.*;


/** This is the "repository connector" for Microsoft SharePoint.
* Document identifiers for this connector come in three forms:
* (1) An "S" followed by the encoded subsite/library path, which represents the encoded relative path from the root site to a library. [deprecated and no longer supported];
* (2) A "D" followed by a subsite/library/folder/file path, which represents the relative path from the root site to a file. [deprecated and no longer supported]
* (3) Three different kinds of unencoded path, each of which starts with a "/" at the beginning, where the "/" represents the root site of the connection, as follows:
*   /sitepath/ - the relative path to a site.  The path MUST both begin and end with a single "/".
*   /sitepath/libraryname// - the relative path to a library.  The path MUST begin with a single "/" and end with "//".
*   /sitepath/libraryname//folderfilepath - the relative path to a file.  The path MUST begin with a single "/" and MUST include a "//" after the library, and must NOT end with a "/".
*/
public class SharePointRepository extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
	public static final String _rcsid = "@(#)$Id$";

	// Activities we log
	public final static String ACTIVITY_FETCH = "fetch";

	private boolean supportsItemSecurity = false;
	private String serverProtocol = null;
	private String serverUrl = null;
	private String fileBaseUrl = null;
	private String userName = null;
	private String strippedUserName = null;
	private String password = null;
	private String ntlmDomain = null;
	private String serverName = null;
	private String serverLocation = null;
	private String encodedServerLocation = null;
	private int serverPort = -1;

	private SPSProxyHelper proxy = null;

	// SSL support
	private String keystoreData = null;
	private IKeystoreManager keystoreManager = null;
	private SharepointSecureSocketFactory secureSocketFactory = null;
	private ProtocolFactory myFactory = null;
	private MultiThreadedHttpConnectionManager connectionManager = null;

	// Current host name
	private static String currentHost = null;
	static
	{
		// Find the current host name
	        try
		{
       			java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

	            	// Get hostname
       			currentHost = addr.getHostName();
       		}
		catch (UnknownHostException e)
		{
       		}
	}

	/** Deny access token for default authority */
	private final static String defaultAuthorityDenyToken = "McAdAuthority_MC_DEAD_AUTHORITY";

	/** Constructor.
	*/
	public SharePointRepository()
	{
	}

	/** Set up a session */
	protected void getSession()
		throws MetacartaException
	{
		if (proxy == null)
		{
			String serverVersion = params.getParameter( "serverVersion" );
			if (serverVersion == null)
				serverVersion = "2.0";
			supportsItemSecurity = serverVersion.equals("3.0");
			
			serverProtocol = params.getParameter( "serverProtocol" );
			if (serverProtocol == null)
				serverProtocol = "http";
			try
			{
				String serverPort = params.getParameter( "serverPort" );
				if (serverPort == null || serverPort.length() == 0)
				{
					if (serverProtocol.equals("https"))
						this.serverPort = 443;
					else
						this.serverPort = 80;
				}
				else
					this.serverPort = Integer.parseInt(serverPort);
			}
			catch (NumberFormatException e)
			{
				throw new MetacartaException(e.getMessage(),e);
			}
			serverLocation = params.getParameter("serverLocation");
			if (serverLocation == null)
				serverLocation = "";
			if (serverLocation.endsWith("/"))
				serverLocation = serverLocation.substring(0,serverLocation.length()-1);
			if (serverLocation.length() > 0 && !serverLocation.startsWith("/"))
				serverLocation = "/" + serverLocation;
			encodedServerLocation = serverLocation;
			serverLocation = decodePath(serverLocation);
			
			userName = params.getParameter( "userName" );
			password = params.getObfuscatedParameter( "password" );
			int index = userName.indexOf("\\");
			if (index != -1)
			{
				strippedUserName = userName.substring(index+1);
				ntlmDomain = userName.substring(0,index);
			}
			else
				throw new MetacartaException("Invalid user name - need <domain>\\<name>");

			serverUrl = serverProtocol + "://" + serverName;
			if (serverProtocol.equals("https"))
			{
				if (serverPort != 443)
					serverUrl += ":" + Integer.toString(serverPort);
			}
			else
			{
				if (serverPort != 80)
					serverUrl += ":" + Integer.toString(serverPort);
			}
			
			// Set up ssl if indicated
			keystoreData = params.getParameter( "keystore" );
			myFactory = new ProtocolFactory();

			if (keystoreData != null)
			{
				keystoreManager = KeystoreManagerFactory.make("",keystoreData);
				secureSocketFactory = new SharepointSecureSocketFactory(keystoreManager.getSecureSocketFactory());
				Protocol myHttpsProtocol = new Protocol("https", (ProtocolSocketFactory)secureSocketFactory, 443);
				myFactory.registerProtocol("https",myHttpsProtocol);
			}
			
			connectionManager = new MultiThreadedHttpConnectionManager();
			connectionManager.getParams().setMaxTotalConnections(1);
			
			fileBaseUrl = serverUrl + encodedServerLocation;
		
			proxy = new SPSProxyHelper( serverUrl, encodedServerLocation, serverLocation, userName, password, myFactory, "/usr/lib/metacarta/sharepoint-client-config.wsdd",
				connectionManager );
		}
	}
	
	/** Return the list of activities that this connector supports (i.e. writes into the log).
	*@return the list.
	*/
	public String[] getActivitiesList()
	{
		return new String[]{ACTIVITY_FETCH};
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
		return "sharepoint";
	}

	/** Connect.
	*@param configParameters is the set of configuration parameters, which
	* in this case describe the root directory.
	*/
	public void connect(ConfigParams configParameters)
	{
		super.connect(configParameters);
		// This is needed by getBins()
		serverName = configParameters.getParameter( "serverName" );
	}

	/** Close the connection.  Call this before discarding the repository connector.
	*/
	public void disconnect()
		throws MetacartaException
	{
		serverUrl = null;
		fileBaseUrl = null;
		userName = null;
		strippedUserName = null;
		password = null;
		ntlmDomain = null;
		serverName = null;
		serverLocation = null;
		encodedServerLocation = null;
		serverPort = -1;

		keystoreData = null;
		keystoreManager = null;
		secureSocketFactory = null;
		myFactory = null;

		proxy = null;
		if (connectionManager != null)
			connectionManager.shutdown();
		connectionManager = null;
		
		super.disconnect();
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
		return new String[]{serverName};
	}
	
	/** Get the maximum number of documents to amalgamate together into one batch, for this connector.
	*@return the maximum number. 0 indicates "unlimited".
	*/
	public int getMaxDocumentRequest()
	{
		// Since we pick up acls on a per-lib basis, it helps to have this bigger than 1.
		return 10;
	}

	/** Test the connection.  Returns a string describing the connection integrity.
	*@return the connection's status as a displayable string.
	*/
	public String check()
		throws MetacartaException
	{
		getSession();
		try
		{
			URL urlServer = new URL( serverUrl );
		}
		catch ( MalformedURLException e )
		{
			return "Illegal SharePoint url: "+e.getMessage();
		}
		
		try
		{
			proxy.checkConnection( "/", supportsItemSecurity );
		}
		catch ( ServiceInterruption e )
		{
			return "SharePoint temporarily unavailable: "+e.getMessage();
		}
		catch (MetacartaException e)
		{
			return e.getMessage();
		}
	
		return super.check();
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

	/** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
	* are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
	*
	* This method can choose to find repository changes that happen only during the specified time interval.
	* The seeds recorded by this method will be viewed by the framework based on what the
	* getConnectorModel() method returns. 
	*
	* It is not a big problem if the connector chooses to create more seeds than are
	* strictly necessary; it is merely a question of overall work required.
	*
	* The times passed to this method may be interpreted for greatest efficiency.  The time ranges
	* any given job uses with this connector will not overlap, but will proceed starting at 0 and going
	* to the "current time", each time the job is run.  For continuous crawling jobs, this method will
	* be called once, when the job starts, and at various periodic intervals as the job executes.
	* 
	* When a job's specification is changed, the framework automatically resets the seeding start time to 0.  The
	* seeding start time may also be set to 0 on each job run, depending on the connector model returned by
	* getConnectorModel().
	*
	* Note that it is always ok to send MORE documents rather than less to this method.
	*@param activities is the interface this method should use to perform whatever framework actions are desired.
	*@param spec is a document specification (that comes from the job).
	*@param startTime is the beginning of the time range to consider, inclusive.
	*@param endTime is the end of the time range to consider, exclusive.
	*@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
	*/
	public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
		long startTime, long endTime, int jobMode)
		throws MetacartaException, ServiceInterruption
	{
		// Check the session
		getSession();
		// Add just the root.
		activities.addSeedDocument("/");
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
		
		// Before we begin looping, make sure we know what to add to the version string to account for the forced acls.

		// Read the forced acls.  A null return indicates that security is disabled!!!
		// A zero-length return indicates that the native acls should be used.
		// All of this is germane to how we ingest the document, so we need to note it in
		// the version string completely.
		String[] acls = getAcls(spec);
		// Make sure they are in sorted order, since we need the version strings to be comparable
		if (acls != null)
			java.util.Arrays.sort(acls);

		// Look at the metadata attributes.
		// So that the version strings are comparable, we will put them in an array first, and sort them.
		String pathAttributeName = null;
		MatchMap matchMap = new MatchMap();
		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i++);
			if (n.getType().equals("pathnameattribute"))
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

		// This is the cached map of sitelibstring to library identifier
		HashMap libIDMap = new HashMap();
		// This is the cached map of sitelibstring to field list (stored as a Map)
		HashMap fieldListMap = new HashMap();
		
		// Calculate the part of the version string that comes from path name and mapping.
		// This starts with = since ; is used by another optional component (the forced acls)
		StringBuffer pathNameAttributeVersion = new StringBuffer();
		if (pathAttributeName != null)
			pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

		String[] rval = new String[documentIdentifiers.length];
		// Build a cache of the acls for a given site, lib.
		// The key is site + "/" + lib, and the value is a String[]
		HashMap ACLmap = new HashMap();

		i = 0;
		while (i < rval.length)
		{
				// Check if we should abort
				activities.checkJobStillActive();
			
				String documentIdentifier = documentIdentifiers[i];
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug( "SharePoint: Getting version of '" + documentIdentifier + "'");
				if ( documentIdentifier.startsWith("D") || documentIdentifier.startsWith("S") )
				{
					// Old-style document identifier.  We don't recognize these anymore, so signal deletion.
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Removing old-style document identifier '"+documentIdentifier+"'");
					rval[i] = null;
				}
				else if (documentIdentifier.startsWith("/"))
				{
					// New-style document identifier.  A double-slash marks the separation between the library and folder/file levels.
					int dSlashIndex = documentIdentifier.indexOf("//");
					if (dSlashIndex == -1)
					{
						// Site path!
						String sitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);
						if (sitePath.length() == 0)
							sitePath = "/";
						if (checkIncludeSite(sitePath,spec))
							// This is the path for the site: No versioning
							rval[i] = "";
						else
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Site specification no longer includes site '"+documentIdentifier+"' - removing");
							rval[i] = null;
						}
					}
					else if (dSlashIndex == documentIdentifier.length() - 2)
					{
						// Library path!
						if (checkIncludeLibrary(documentIdentifier.substring(0,documentIdentifier.length()-2),spec))
							// This is the path for the library: No versioning
							rval[i] = "";
						else
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Library specification no longer includes library '"+documentIdentifier+"' - removing");
							rval[i] = null;
						}
					}
					else
					{
						// Convert the modified document path to an unmodified one, plus a library path.
						String decodedLibPath = documentIdentifier.substring(0,dSlashIndex);
						String decodedDocumentPath = decodedLibPath + documentIdentifier.substring(dSlashIndex+1);
						if (checkInclude(decodedDocumentPath,spec))
						{
							// This file is included, so calculate a version string.  This will include metadata info, so get that first.
							MetadataInformation metadataInfo = getMetadataSpecification(decodedDocumentPath,spec);

							int lastIndex = decodedLibPath.lastIndexOf("/");
							String sitePath = decodedLibPath.substring(0,lastIndex);
							String lib = decodedLibPath.substring(lastIndex+1);

							String encodedSitePath = encodePath(sitePath);
							
							// Need to get the library id.  Cache it if we need to calculate it.
							String libID = (String)libIDMap.get(decodedLibPath);
							if (libID == null)
							{
								libID = proxy.getDocLibID(encodedSitePath, sitePath, lib);
								if (libID != null)
									libIDMap.put(decodedLibPath,libID);
							}

							if (libID != null)
							{
								HashMap metadataFields = null;
							
								// Figure out the actual metadata fields we will request
								if (metadataInfo.getAllMetadata())
								{
									// Fetch the fields
									Map fieldNames = (Map)fieldListMap.get(decodedLibPath);
									if (fieldNames == null)
									{
										fieldNames = proxy.getFieldList( encodedSitePath, libID );
										if (fieldNames != null)
											fieldListMap.put(decodedLibPath,fieldNames);
									}
								
									if (fieldNames != null)
									{
										metadataFields = new HashMap();
										for (Iterator e = fieldNames.keySet().iterator(); e.hasNext();)
										{
											String key = (String)e.next();
											metadataFields.put( key, key );
										}
									}	
								}
								else
								{
									metadataFields = new HashMap();
									String[] fields = metadataInfo.getMetadataFields();
									int q = 0;
									while (q < fields.length)
									{
										String field = fields[q++];
										metadataFields.put(field,field);
									}
								}
							
								if (metadataFields != null)
								{
									// Convert the hashtable to an array and sort it.
									String[] sortedMetadataFields = new String[metadataFields.size()];
									int z = 0;
									Iterator iter = metadataFields.keySet().iterator();
									while (iter.hasNext())
									{
										sortedMetadataFields[z++] = (String)iter.next();
									}
									java.util.Arrays.sort(sortedMetadataFields);
								
									// Next, get the actual timestamp field for the file.
									ArrayList metadataDescription = new ArrayList();
									metadataDescription.add("Last_x0020_Modified");
									// The document path includes the library, with no leading slash, and is decoded.
									int cutoff = decodedLibPath.lastIndexOf("/");
									String decodedDocumentPathWithoutSite = decodedDocumentPath.substring(cutoff+1);
									Map values = proxy.getFieldValues( metadataDescription, encodedSitePath, libID, decodedDocumentPathWithoutSite);
									String modifyDate = (String)values.get("Last_x0020_Modified");
									if (modifyDate != null)
									{
										// Build version string
										String versionToken = modifyDate;
										// Revamped version string on 11/8/2006 to make parseability better

										StringBuffer sb = new StringBuffer();

										packList(sb,sortedMetadataFields,'+');

										// Do the acls.
										boolean foundAcls = true;
										if (acls != null)
										{
											String[] accessTokens;
											sb.append('+');

											// If there are forced acls, use those in the version string instead.
											if (acls.length == 0)
											{
												if (supportsItemSecurity)
												{
													// For documents, just fetch
													accessTokens = proxy.getDocumentACLs( encodedSitePath, encodePath(decodedDocumentPath) );
													if (accessTokens != null)
													{
														java.util.Arrays.sort(accessTokens);
														if (Logging.connectors.isDebugEnabled())
															Logging.connectors.debug( "SharePoint: Received " + accessTokens.length + " acls for '" +decodedDocumentPath+"'");
													}
													else
													{
														foundAcls = false;
													}
												}
												else
												{
													// The goal here is simply to record what should get ingested with the document, so that
													// we can compare against future values.
													// Grab the acls for this combo, if we haven't already
													accessTokens = (String[])ACLmap.get(decodedLibPath);
													if (accessTokens == null)
													{
														if (Logging.connectors.isDebugEnabled())
															Logging.connectors.debug( "SharePoint: Compiling acl list for '"+decodedLibPath+"'... ");
														accessTokens = proxy.getACLs( encodedSitePath, libID );
														if (accessTokens != null)
														{
															java.util.Arrays.sort(accessTokens);
															// no point in sorting, because we sort at the end anyhow
															if (Logging.connectors.isDebugEnabled())
																Logging.connectors.debug( "SharePoint: Received " + accessTokens.length + " acls for '" +decodedLibPath+"'");
															ACLmap.put(decodedLibPath,accessTokens);
														}
														else
														{
															foundAcls = false;
														}
													}
												}
											}
											else
												accessTokens = acls;
											// Only pack access tokens if they are non-null; we'll be giving up anyhow otherwise
											if (foundAcls)
											{
												packList(sb,accessTokens,'+');
												// Added 4/21/2008 to handle case when AD authority is down
												pack(sb,defaultAuthorityDenyToken,'+');
											}
										}
										else
											sb.append('-');
										if (foundAcls)
										{
											// The rest of this is unparseable
											sb.append(versionToken);
											sb.append(pathNameAttributeVersion);
											// Added 9/7/07
											sb.append("_").append(fileBaseUrl);
											//
											rval[i] = sb.toString();
											if (Logging.connectors.isDebugEnabled())
												Logging.connectors.debug( "SharePoint: Complete version string for '"+documentIdentifier+"': " + rval[i]);
										}
										else
										{
											if (Logging.connectors.isDebugEnabled())
												Logging.connectors.debug("SharePoint: Couldn't get access tokens for library '"+decodedLibPath+"'; removing document '"+documentIdentifier+"'");
											rval[i] = null;
										}
									}
									else
									{
										if (Logging.connectors.isDebugEnabled())
											Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because it has no modify date");
										rval[i] = null;
									}
								}
								else
								{
									if (Logging.connectors.isDebugEnabled())
										Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because library '"+decodedLibPath+"' doesn't respond to metadata requests - removing");
									rval[i] = null;
								}
							}
							else
							{
								if (Logging.connectors.isDebugEnabled())
									Logging.connectors.debug("SharePoint: Can't get version of '"+documentIdentifier+"' because library '"+decodedLibPath+"' does not exist - removing");
								rval[i] = null;
							}
						}
						else
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Document '"+documentIdentifier+"' is no longer included - removing");
							rval[i] = null;
						}
					}
				}
				else
					throw new MetacartaException("Invalid document identifier discovered: '"+documentIdentifier+"'");
				i++;
		}
		return rval;
	}

	protected static class ExecuteMethodThread extends Thread
	{
		protected HttpClient client;
		protected HostConfiguration hostConfiguration;
		protected HttpMethodBase executeMethod;
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
		
		// Decode the system metadata part of the specification
		SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

		HashMap docLibIDMap = new HashMap();
		
		int i = 0;
		while (i < documentIdentifiers.length)
		{
			// Make sure the job is still active
			activities.checkJobStillActive();
			
			String documentIdentifier = documentIdentifiers[i];
			String version = versions[i];

			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug( "SharePoint: Processing: '" + documentIdentifier + "'");
			if ( documentIdentifier.startsWith("/") )
			{
				int dSlashIndex = documentIdentifier.indexOf("//");
				if (dSlashIndex == -1)
				{
					// It's a site.  Strip off the trailing "/" to get the site name.
					String decodedSitePath = documentIdentifier.substring(0,documentIdentifier.length()-1);

					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug( "SharePoint: Document identifier is a site: '" + decodedSitePath + "'" );

					// Look at subsites
					ArrayList subsites = proxy.getSites( encodePath(decodedSitePath) );
					if (subsites != null)
					{
						int j = 0;
						while (j < subsites.size())
						{
							NameValue subSiteName = (NameValue)subsites.get(j++);
							String newPath = decodedSitePath + "/" + subSiteName.getValue();

							String encodedNewPath = encodePath(newPath);
							if ( checkIncludeSite(newPath,spec) )
								activities.addDocumentReference(newPath + "/");
						}
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: No permissions to access subsites of '"+decodedSitePath+"' - skipping");
					}
				
					// Look at libraries
					ArrayList libraries = proxy.getDocumentLibraries( encodePath(decodedSitePath), decodedSitePath );
					if (libraries != null)
					{
						int j = 0;
						while (j < libraries.size())
						{
							NameValue library = (NameValue)libraries.get(j++);
							String newPath = decodedSitePath + "/" + library.getValue();
						
							if (checkIncludeLibrary(newPath,spec))
								activities.addDocumentReference(newPath + "//");
						
						}
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: No permissions to access libraries of '"+decodedSitePath+"' - skipping");
					}

				}
				else if (dSlashIndex == documentIdentifier.length() - 2)
				{
					// It's a library.
					String siteLibPath = documentIdentifier.substring(0,documentIdentifier.length()-2);
					int libCutoff = siteLibPath.lastIndexOf( "/" );
					String site = siteLibPath.substring(0,libCutoff);
					String libName = siteLibPath.substring( libCutoff + 1 );

					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug( "SharePoint: Document identifier is a library: '" + siteLibPath + "'" );

					// Calculate the start of the path part that would contain the folders/file
					int foldersFilePathIndex = site.length() + 1 + libName.length();

					String libID = proxy.getDocLibID( encodePath(site), site, libName );
					if (libID != null)
					{
						XMLDoc docs = proxy.getDocuments( encodePath(site) , libID );
						if (docs != null)
						{
							ArrayList nodeDocs = new ArrayList();
				
							docs.processPath( nodeDocs, "*", null );
							Object parent = nodeDocs.get(0);		// ns1:dsQueryResponse
							nodeDocs.clear();
							docs.processPath(nodeDocs, "*", parent); 
							Object documents = nodeDocs.get(1);
							nodeDocs.clear();
							docs.processPath(nodeDocs, "*", documents); 
				
							StringBuffer sb = new StringBuffer();
							for( int j =0; j < nodeDocs.size(); j++)
							{
								Object node = nodeDocs.get(j);
								Logging.connectors.debug( node.toString() );
								String relPath = docs.getData( docs.getElement( node, "FileRef" ) );
					
								// This relative path is apparently from the domain on down; if there's a location offset we therefore
								// need to get rid of it before checking the document against the site/library tuples.  The recorded
								// document identifier should also not include it.

								if (!relPath.toLowerCase().startsWith(serverLocation.toLowerCase()))
								{
									// Unexpected processing error; the path to the folder or document did not start with the location
									// offset, so throw up.
									throw new MetacartaException("Internal error: Relative path '"+relPath+"' was expected to start with '"+
										serverLocation+"'");
								}
						
								relPath = relPath.substring(serverLocation.length());

								// Since the processing for a file needs to know the library path, we need a way to signal the cutoff between library and folder levels.
								// The way I've chosen to do this is to use a double slash at that point, as a separator.
								String modifiedPath = relPath.substring(0,foldersFilePathIndex) + "/" + relPath.substring(foldersFilePathIndex);
					
								if ( !relPath.endsWith(".aspx") && checkInclude( relPath, spec ) )
									activities.addDocumentReference( modifiedPath );
							}
						}
						else
						{
							// Site/library no longer exists, so delete entry
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: No list found for library '"+siteLibPath+"' - deleting");
							activities.deleteDocument(documentIdentifier);
						}
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: GUID lookup failed for library '"+siteLibPath+"' - deleting");
						activities.deleteDocument(documentIdentifier);
					}
				}
				else
				{
					// File/folder identifier
					if ( !scanOnly[ i ] )
					{
						// New-style document identifier.  A double-slash marks the separation between the library and folder/file levels.
						// Convert the modified document path to an unmodified one, plus a library path.
						String decodedLibPath = documentIdentifier.substring(0,dSlashIndex);
						String decodedDocumentPath = decodedLibPath + documentIdentifier.substring(dSlashIndex+1);
						String encodedDocumentPath = encodePath(decodedDocumentPath);

						int libCutoff = decodedLibPath.lastIndexOf( "/" );
						String site = decodedLibPath.substring(0,libCutoff);
						String libName = decodedLibPath.substring( libCutoff + 1 );

						// Parse what we need out of version string.

						// Placeholder for metadata specification
						ArrayList metadataDescription = new ArrayList();
						int startPosition = unpackList(metadataDescription,version,0,'+');

						// Acls
						ArrayList acls = null;
						String denyAcl = null;
						if (startPosition < version.length() && version.charAt(startPosition++) == '+')
						{
							acls = new ArrayList();
							startPosition = unpackList(acls,version,startPosition,'+');
							if (startPosition < version.length())
							{
								StringBuffer denyAclBuffer = new StringBuffer();
								startPosition = unpack(denyAclBuffer,version,startPosition,'+');
								denyAcl = denyAclBuffer.toString();
							}
						}


						// Generate the URL we are going to use
						String fileUrl = fileBaseUrl + encodedDocumentPath;
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug( "SharePoint: Processing file '"+documentIdentifier+"'; url: '" + fileUrl + "'" );

						// Set stuff up for fetch activity logging
						long startFetchTime = System.currentTimeMillis();
						try
						{
							// Read the document into a local temporary file, so I get a reliable length.
							File tempFile = File.createTempFile("__shp__",".tmp");
							try
							{
								// Open the output stream
								OutputStream os = new FileOutputStream(tempFile);
								try
								{
									// Read the document.
									try
									{
										HttpClient httpClient = new HttpClient(connectionManager);
										HostConfiguration clientConf = new HostConfiguration();
										clientConf.setParams(new HostParams());
										clientConf.setHost(serverName,serverPort,myFactory.getProtocol(serverProtocol));
 
										Credentials credentials =  new NTCredentials(strippedUserName, password, currentHost, ntlmDomain);
						    
										httpClient.getState().setCredentials(new AuthScope(serverName,serverPort,null),
											credentials);  

										HttpMethodBase method = new GetMethod( encodedServerLocation + encodedDocumentPath ); 
										try
										{
											// Set up SSL using our keystore
											method.getParams().setParameter("http.socket.timeout", new Integer(60000));

											int returnCode;
											ExecuteMethodThread t = new ExecuteMethodThread(httpClient,clientConf,method);
											try
											{
												t.start();
												t.join();
												Throwable thr = t.getException();
												if (thr != null)
												{
													if (thr instanceof IOException)
														throw (IOException)thr;
													if (thr instanceof RuntimeException)
														throw (RuntimeException)thr;
													else
														throw (Error)thr;
												}
												returnCode = t.getResponse();
											}
											catch (InterruptedException e)
											{
												t.interrupt();
												// We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
												method = null;
												throw e;
											}
											if (returnCode == HttpStatus.SC_NOT_FOUND || returnCode == HttpStatus.SC_UNAUTHORIZED || returnCode == HttpStatus.SC_BAD_REQUEST)
											{
												// Well, sharepoint thought the document was there, but it really isn't, so delete it.
												if (Logging.connectors.isDebugEnabled())
													Logging.connectors.debug("SharePoint: Document at '"+encodedServerLocation+encodedDocumentPath+"' failed to fetch with code "+Integer.toString(returnCode)+", deleting");
												activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
													null,documentIdentifier,"Not found",Integer.toString(returnCode),null);
												activities.deleteDocument(documentIdentifier);
												i++;
												continue;
											}
											if (returnCode != HttpStatus.SC_OK)
											{
												activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
													null,documentIdentifier,"Error","Http status "+Integer.toString(returnCode),null);
												throw new MetacartaException("Error fetching document '"+fileUrl+"': "+Integer.toString(returnCode));
											}
							
											// int contentSize = (int)method.getResponseContentLength();
											InputStream is = method.getResponseBodyAsStream();
											try
											{
												byte[] transferBuffer = new byte[65536];
												while (true)
												{
													int amt = is.read(transferBuffer);
													if (amt == -1)
														break;
													os.write(transferBuffer,0,amt);
												}
											}
											finally
											{
												try
												{
													is.close();
												}
												catch (java.net.SocketTimeoutException e)
												{
													Logging.connectors.warn("SharePoint: Socket timeout error closing connection to file '"+fileUrl+"': "+e.getMessage(),e);
												}
												catch (org.apache.commons.httpclient.ConnectTimeoutException e)
												{
													Logging.connectors.warn("SharePoint: Connect timeout error closing connection to file '"+fileUrl+"': "+e.getMessage(),e);
												}
												catch (InterruptedIOException e)
												{
													throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
												}
												catch (IOException e)
												{
													Logging.connectors.warn("SharePoint: Error closing connection to file '"+fileUrl+"': "+e.getMessage(),e);
												}
											}
										}
										finally
										{
											if (method != null)
												method.releaseConnection();
										}
				
										// Log the normal fetch activity
										activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Success",null,null);
									}
									catch (InterruptedException e)
									{
										throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
									}
									catch (java.net.SocketTimeoutException e)
									{
										activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
										Logging.connectors.warn("SharePoint: SocketTimeoutException thrown: "+e.getMessage(),e);
										long currentTime = System.currentTimeMillis();
										throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
											currentTime + 12 * 60 * 60000L,-1,true);
									}
									catch (org.apache.commons.httpclient.ConnectTimeoutException e)
									{
										activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
										Logging.connectors.warn("SharePoint: ConnectTimeoutException thrown: "+e.getMessage(),e);
										long currentTime = System.currentTimeMillis();
										throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
											currentTime + 12 * 60 * 60000L,-1,true);
									}
									catch (InterruptedIOException e)
									{
										throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
									}
									catch (IllegalArgumentException e)
									{
										Logging.connectors.error("SharePoint: Illegal argument", e);
											activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
										throw new MetacartaException("SharePoint: Illegal argument: "+e.getMessage(),e);
									}
									catch (HttpException e) 
									{  
										Logging.connectors.warn("SharePoint: HttpException thrown",e);
										activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
										long currentTime = System.currentTimeMillis();
										throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
											currentTime + 12 * 60 * 60000L,-1,true);
									} 
									catch (IOException e) 
									{
										activities.recordActivity(new Long(startFetchTime),ACTIVITY_FETCH,
											new Long(tempFile.length()),documentIdentifier,"Error",e.getMessage(),null);
										Logging.connectors.warn("SharePoint: IOException thrown: "+e.getMessage(),e);
										long currentTime = System.currentTimeMillis();
										throw new ServiceInterruption("SharePoint is down attempting to read '"+fileUrl+"', retrying: "+e.getMessage(),e,currentTime + 300000L,
											currentTime + 12 * 60 * 60000L,-1,true);
									}
								}
								finally
								{
									os.close();
								}
								InputStream is = new FileInputStream(tempFile);
								try
								{
									RepositoryDocument data = new RepositoryDocument();
									data.setBinary( is, tempFile.length() );

									if (acls != null)
									{
										String[] actualAcls = new String[acls.size()];
										int j = 0;
										while (j < actualAcls.length)
										{
											actualAcls[j] = (String)acls.get(j);
											j++;
										}
	
										if (Logging.connectors.isDebugEnabled())
										{
											j = 0;
											StringBuffer sb = new StringBuffer("SharePoint: Acls: [ ");
											while (j < actualAcls.length)
											{
												sb.append(actualAcls[j++]).append(" ");
											}
											sb.append("]");
											Logging.connectors.debug( sb.toString() );
										}

										data.setACL( actualAcls );
									}

									if (denyAcl != null)
									{
										String[] actualDenyAcls = new String[]{denyAcl};
										data.setDenyACL(actualDenyAcls);
									}

									// Add the path metadata item into the mix, if enabled
									String pathAttributeName = sDesc.getPathAttributeName();
									if (pathAttributeName != null && pathAttributeName.length() > 0)
									{
										if (Logging.connectors.isDebugEnabled())
											Logging.connectors.debug("SharePoint: Path attribute name is '"+pathAttributeName+"'");
										String pathString = sDesc.getPathAttributeValue(documentIdentifier);
										if (Logging.connectors.isDebugEnabled())
											Logging.connectors.debug("SharePoint: Path attribute value is '"+pathString+"'");
										data.addField(pathAttributeName,pathString);
									}
									else
										Logging.connectors.debug("SharePoint: Path attribute name is null");

									// Retrieve field values from SharePoint
									if (metadataDescription.size() > 0)
									{
										String documentLibID = (String)docLibIDMap.get(decodedLibPath);
										if (documentLibID == null)
										{
											documentLibID = proxy.getDocLibID( encodePath(site), site, libName);
											if (documentLibID == null)
												documentLibID = "";
											docLibIDMap.put(decodedLibPath,documentLibID);
										}
										
										if (documentLibID.length() == 0)
										{
											if (Logging.connectors.isDebugEnabled())
												Logging.connectors.debug("SharePoint: Library '"+decodedLibPath+"' no longer exists - deleting document '"+documentIdentifier+"'");
											activities.deleteDocument( documentIdentifier );
											i++;
											continue;
										}
										
										int cutoff = decodedLibPath.lastIndexOf("/");
										Map values = proxy.getFieldValues( metadataDescription, encodePath(site), documentLibID, decodedDocumentPath.substring(cutoff+1) );
										if (values != null)
										{
											Iterator iter = values.keySet().iterator();
											while (iter.hasNext())
											{
												String fieldName = (String)iter.next();
												String fieldData = (String)values.get(fieldName);
												data.addField(fieldName,fieldData);
											}
										}
										else
										{
											// Document has vanished
											if (Logging.connectors.isDebugEnabled())
												Logging.connectors.debug("SharePoint: Document metadata fetch failure indicated that document is gone: '"+documentIdentifier+"' - removing");
											activities.deleteDocument( documentIdentifier );
											i++;
											continue;
										}
									}

									activities.ingestDocument( documentIdentifier, version, fileUrl , data );
								}
								finally
								{
									try
									{
										is.close();
									}
									catch (java.net.SocketTimeoutException e)
									{
										// This is not fatal
										Logging.connectors.debug("SharePoint: Timeout before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
									}
									catch (org.apache.commons.httpclient.ConnectTimeoutException e)
									{
										// This is not fatal
										Logging.connectors.debug("SharePoint: Connect timeout before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
									}
									catch (InterruptedIOException e)
									{
										throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
									}
									catch (IOException e)
									{
										// This is not fatal
										Logging.connectors.debug("SharePoint: Server closed connection before read could finish for '"+fileUrl+"': "+e.getMessage(),e);
									}
								}
							}
							finally
							{
								tempFile.delete();
							}
						}
						catch (java.net.SocketTimeoutException e)
						{
							throw new MetacartaException("Socket timeout error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
						}
						catch (org.apache.commons.httpclient.ConnectTimeoutException e)
						{
							throw new MetacartaException("Connect timeout error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
						}
						catch (InterruptedIOException e)
						{
							throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
						}
						catch (IOException e)
						{
							throw new MetacartaException("IO error writing '"+fileUrl+"' to temporary file: "+e.getMessage(),e);
						}
					}
				}
			}
			else
				throw new MetacartaException("Found illegal document identifier in processDocuments: '"+documentIdentifier+"'");
			
			i++;
		}
	}


	/**
	* Gets a list of field names of the given document library.
	* @param parentSite - parent site path
	* @param docLibrary
	* @return list of the fields
	*/
	public Map getFieldList( String parentSite, String docLibrary )
		throws ServiceInterruption, MetacartaException
	{
		getSession();
		return proxy.getFieldList( encodePath(parentSite), proxy.getDocLibID( encodePath(parentSite), parentSite, docLibrary) );
	}

	/**
	* Gets a list of sites/subsites of the given parent site
	* @param parentSite the unencoded parent site path to search for subsites, empty for root.
	* @return list of the sites
	*/
	public ArrayList getSites( String parentSite )
		throws ServiceInterruption, MetacartaException
	{
		getSession();
		return proxy.getSites( encodePath(parentSite) );
	}

	/**
	* Gets a list of document libraries of the given parent site
	* @param parentSite the unencoded parent site to search for libraries, empty for root.
	* @return list of the libraries
	*/
	public ArrayList getDocLibsBySite( String parentSite )
		throws MetacartaException, ServiceInterruption
	{
		getSession();
		return proxy.getDocumentLibraries( encodePath(parentSite), parentSite );
	}


	// Protected static methods

	/** Check if a library should be included, given a document specification.
	*@param libraryPath is the unencoded canonical library name (including site path from root site), without any starting slash.
	*@param documentSpecification is the specification.
	*@return true if it should be included.
	*/
	protected boolean checkIncludeLibrary( String libraryPath, DocumentSpecification documentSpecification )
	{
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug( "SharePoint: Checking whether to include library '" + libraryPath + "'" );

		// Scan the specification, looking for the old-style "startpoint" matches and the new-style "libraryrule" matches.
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i++);
			if ( sn.getType().equals("startpoint") )
			{
				// Old style rule!
				
				String site = sn.getAttributeValue( "site" );
				String lib = sn.getAttributeValue( "lib" );
				// Both site and lib are unencoded.  See if they match the library path
				String pathStart = site + "/" + lib;

				// Old-style matches have a preceding "/" when there's no subsite...
				if (libraryPath.equals(pathStart))
				{
					// Hey, the startpoint rule matches!  It's an implicit inclusion, so we don't need to do anything else except return.
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Library path '"+libraryPath+"' matched old-style startpoint with site '"+site+"' and library '"+lib+"' - including");
					return true;
				}
			}
			else if (sn.getType().equals("pathrule"))
			{
				// New-style rule.
				// Here's the trick: We do what the first matching rule tells us to do.
				String pathMatch = sn.getAttributeValue("match");
				String action = sn.getAttributeValue("action");
				String ruleType = sn.getAttributeValue("type");

				// First, find out if we match EXACTLY.
				if (checkMatch(libraryPath,0,pathMatch))
				{
					// If this is true, the type also has to match if the rule is to apply.
					if (ruleType.equals("library"))
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' exactly matched rule path '"+pathMatch+"'");
						if (action.equals("include"))
						{
							// For include rules, partial match is good enough to proceed.
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Including library '"+libraryPath+"'");
							return true;
						}
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Excluding library '"+libraryPath+"'");
						return false;
					}
				}
				else if (ruleType.equals("file") && checkPartialPathMatch(libraryPath,0,pathMatch,1) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' partially matched file rule path '"+pathMatch+"' - including");
					return true;
				}
				else if (ruleType.equals("folder") && checkPartialPathMatch(libraryPath,0,pathMatch,1) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Library '"+libraryPath+"' partially matched folder rule path '"+pathMatch+"' - including");
					return true;
				}
			}
		}
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug("SharePoint: Not including library '"+libraryPath+"' because no matching rule");
		return false;
	}
	
	/** Check if a site should be included, given a document specification.
	*@param sitePath is the unencoded canonical site path name from the root site level, without any starting slash.
	*@param documentSpecification is the specification.
	*@return true if it should be included.
	*/
	protected boolean checkIncludeSite( String sitePath, DocumentSpecification documentSpecification )
	{
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug( "SharePoint: Checking whether to include site '" + sitePath + "'" );

		// Scan the specification, looking for the old-style "startpoint" matches and the new-style "libraryrule" matches.
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i++);
			if ( sn.getType().equals("startpoint") )
			{
				// Old style rule!
				
				String site = sn.getAttributeValue( "site" );
				// Both site and lib are unencoded.  See if they match part of the site path.
				// Note well: We want a complete subsection match!  That is, what's left in the path after the match must
				// either start with "/" or be empty.
				if (!site.startsWith("/"))
					site = "/" + site;

				// Old-style matches have a preceding "/" when there's no subsite...
				if (site.startsWith(sitePath))
				{
					if (sitePath.length() == 1 || site.length() == sitePath.length() || site.charAt(sitePath.length()) == '/')
					{
						// Hey, the startpoint rule matches!  It's an implicit inclusion, so we don't need to do anything else except return.
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Site path '"+sitePath+"' matched old-style startpoint with site '"+site+"' - including");
						return true;
					}
				}
			}
			else if (sn.getType().equals("pathrule"))
			{
				// New-style rule.
				String pathMatch = sn.getAttributeValue("match");
				String action = sn.getAttributeValue("action");
				String ruleType = sn.getAttributeValue("type");

				// First, find out if we match EXACTLY.
				if (checkMatch(sitePath,0,pathMatch))
				{
					// If this is true, the type also has to match if the rule is to apply.
					if (ruleType.equals("site"))
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Site '"+sitePath+"' exactly matched rule path '"+pathMatch+"'");
						if (action.equals("include"))
						{
							// For include rules, partial match is good enough to proceed.
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Including site '"+sitePath+"'");
							return true;
						}
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Excluding site '"+sitePath+"'");
						return false;
					}
				}
				else if (ruleType.equals("library") && checkPartialPathMatch(sitePath,0,pathMatch,1) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched library rule path '"+pathMatch+"' - including");
					return true;
				}
				else if (ruleType.equals("site") && checkPartialPathMatch(sitePath,0,pathMatch,0) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched site rule path '"+pathMatch+"' - including");
					return true;
				}
				else if (ruleType.equals("file") && checkPartialPathMatch(sitePath,0,pathMatch,2) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched file rule path '"+pathMatch+"' - including");
					return true;
				}
				else if (ruleType.equals("folder") && checkPartialPathMatch(sitePath,0,pathMatch,2) && action.equals("include"))
				{
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: Site '"+sitePath+"' partially matched folder rule path '"+pathMatch+"' - including");
					return true;
				}

			}
		}
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug("SharePoint: Not including site '"+sitePath+"' because no matching rule");
		return false;
	}
	
	/** Get a file's metadata specification, given a path and a document specification.
	*@param filePath is the unencoded path to a file, including sites and library, beneath the root site.
	*@param documentSpecification is the document specification.
	*@return the metadata description appropriate to the file.
	*/
	protected MetadataInformation getMetadataSpecification( String filePath, DocumentSpecification documentSpecification )
	{
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug( "SharePoint: Finding metadata to include for document '" + filePath + "'." );

		MetadataInformation rval = new MetadataInformation();
		
		// Scan the specification, looking for the old-style "startpoint" matches and the new-style "metadatarule" matches.
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i++);
			if ( sn.getType().equals("startpoint") )
			{
				// Old style rule!
				
				String site = sn.getAttributeValue( "site" );
				String lib = sn.getAttributeValue( "lib" );
				// Both site and lib are unencoded.  See if they match the first part of the filepath
				String pathStart = site + "/" + lib + "/";
				// Old-style matches have a preceding "/" when there's no subsite...
				if (filePath.startsWith(pathStart))
				{
					// Hey, the startpoint rule matches!  It's an implicit inclusion, so this is where we get the metadata from (and then return)
					String allmetadata = sn.getAttributeValue("allmetadata");
					if (allmetadata != null && allmetadata.equals("true"))
						rval.setAllMetadata();
					else
					{
						// Scan children looking for metadata nodes
						int j = 0;
						while (j < sn.getChildCount())
						{
							SpecificationNode node = sn.getChild(j++);
							if (node.getType().equals("metafield"))
								rval.addMetadataField(node.getAttributeValue("value"));
						}
					}
					return rval;
				}
			}
			else if (sn.getType().equals("metadatarule"))
			{
				// New-style rule.
				// Here's the trick: We do what the first matching rule tells us to do.
				String pathMatch = sn.getAttributeValue("match");
				// First, find out if we match...
				if (checkMatch(filePath,0,pathMatch))
				{
					// The rule "fired".  Now, do what it tells us to.
					String action = sn.getAttributeValue("action");
					if (action.equals("include"))
					{
						// Include: Process the metadata specification, then return
						String allMetadata = sn.getAttributeValue("allmetadata");
						if (allMetadata != null && allMetadata.equals("true"))
							rval.setAllMetadata();
						else
						{
							// Scan children looking for metadata nodes
							int j = 0;
							while (j < sn.getChildCount())
							{
								SpecificationNode node = sn.getChild(j++);
								if (node.getType().equals("metafield"))
									rval.addMetadataField(node.getAttributeValue("value"));
							}
						}
					}
					return rval;
				}
			}
		}
		
		return rval;
		
	}
	
	/** Check if a file should be included.
	*@param filePath is the path to the file, including sites and library, beneath the root site.
	*@param documentSpecification is the document specification.
	*@return true if file should be included.
	*/
	protected boolean checkInclude( String filePath, DocumentSpecification documentSpecification )
	{
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug( "SharePoint: Checking whether to include document '" + filePath + "'" );

		// Break up the file/folder part of the path
    		int lastSlash = filePath.lastIndexOf("/");
		String pathPart = filePath.substring(0,lastSlash);
		String filePart = filePath.substring(lastSlash+1);

		// Scan the spec rules looking for a library match, and extract the information if found.
		// We need to understand both the old-style rules (startpoints), and the new style (matchrules)
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i++);
			if ( sn.getType().equals("startpoint") )
			{
				// Old style rule!
				
				String site = sn.getAttributeValue( "site" );
				String lib = sn.getAttributeValue( "lib" );
				// Both site and lib are unencoded.  The string we are matching starts with "/" if the site is empty.
				String pathMatch = site + "/" + lib + "/";
				if (filePath.startsWith(pathMatch))
				{
					// Hey, it matched!
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style startpoint with site '"+site+"' and library '"+lib+"'");
					
					int restOfPathIndex = pathMatch.length();
					
					// We need to walk through the subrules and see whether it's in or out.
					int j = 0;
					while (j < sn.getChildCount())
					{
						SpecificationNode node = sn.getChild(j++);
						String flavor = node.getType();
						if (flavor.equals("include") || flavor.equals("exclude"))
						{
							String match = node.getAttributeValue("match");
							String type = node.getAttributeValue("type");
							String sourceMatch;
							int sourceIndex;
							if ( type.equals("file") )
							{
								sourceMatch = filePart;
								sourceIndex = 0;
							}
							else
							{
								sourceMatch = pathPart;
								sourceIndex = restOfPathIndex;
							}
							if ( checkMatch(sourceMatch,sourceIndex,match) )
							{
								// Our file path matched the rule.
								if (flavor.equals("include"))
								{
									if (Logging.connectors.isDebugEnabled())
										Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style inclusion rule '"+match+"' - including");
									return true;
								}
								if (Logging.connectors.isDebugEnabled())
									Logging.connectors.debug("SharePoint: File path '"+filePath+"' matched old-style exclusion rule '"+match+"' - excluding");
								return false;
							}
						}
					}
					
					// Didn't match any of the file rules; therefore exclude.
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("SharePoint: File path '"+filePath+"' did not match any old-style inclusion/exclusion rules - excluding");
					return false;
				}
			}
			else if (sn.getType().equals("pathrule"))
			{
				// New style rule!
				String pathMatch = sn.getAttributeValue("match");
				String action = sn.getAttributeValue("action");
				String ruleType = sn.getAttributeValue("type");

				// Find out if we match EXACTLY.  There are no "partial matches" for files.
				if (checkMatch(filePath,0,pathMatch))
				{
					// If this is true, the type also has to match if the rule is to apply.
					if (ruleType.equals("file"))
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: File '"+filePath+"' exactly matched rule path '"+pathMatch+"'");
						if (action.equals("include"))
						{
							// For include rules, partial match is good enough to proceed.
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("SharePoint: Including file '"+filePath+"'");
							return true;
						}
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("SharePoint: Excluding file '"+filePath+"'");
						return false;
					}
				}
			}
		}
		
		if (Logging.connectors.isDebugEnabled())
			Logging.connectors.debug("SharePoint: File path '"+filePath+"' does not match any rules - excluding");
		
		return false;
	}

	/** Match a sub-path.  The sub-path must match the complete starting part of the full path, in a path
	* sense.  The returned value should point into the file name beyond the end of the matched path, or
	* be -1 if there is no match.
	*@param subPath is the sub path.
	*@param fullPath is the full path.
	*@return the index of the start of the remaining part of the full path, or -1.
	*/
	protected static int matchSubPath( String subPath, String fullPath )
	{
		if ( subPath.length() > fullPath.length() )
			return -1;
		if ( fullPath.startsWith( subPath ) == false )
			return -1;
		int rval = subPath.length();
		if ( fullPath.length() == rval )
			return rval;
		char x = fullPath.charAt( rval );
		if ( x == '/' )
			rval++;
		return rval;
	}

	/** Check for a partial path match between two strings with wildcards.
	* Match allowance also must be made for the minimum path components in the rest of the path.
	*/
	protected static boolean checkPartialPathMatch( String sourceMatch, int sourceIndex, String match, int requiredExtraPathSections )
	{
		// The partial match must be of a complete path, with at least a specified number of trailing path components possible in what remains.
		// Path components can include everything but the "/" character itself.
		//
		// The match string is the one containing the wildcards.  Both the "*" wildcard and the "?" wildcard will match a "/", which is intended but is why this
		// matcher is a little tricky to write.
		//
		// Note also that it is OK to return "true" more than strictly necessary, but it is never OK to return "false" incorrectly.
		
		// This is a partial path match.  That means that we don't have to completely use up the match string, but what's left on the match string after the source
		// string is used up MUST either be capable of being null, or be capable of starting with a "/"integral path sections, and MUST include at least n of these sections.
		// 
		
		boolean caseSensitive = true;
		if (!sourceMatch.endsWith("/"))
			sourceMatch = sourceMatch + "/";
		
		return processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex, match, 0, requiredExtraPathSections );
	}

	/** Recursive worker method for checkPartialPathMatch.  Returns 'true' if there is a path that consumes the source string entirely,
	* and leaves the remainder of the match string able to match the required followup.
	*@param caseSensitive is true if file names are case sensitive.
	*@param sourceMatch is the source string (w/o wildcards)
	*@param sourceIndex is the current point in the source string.
	*@param match is the match string (w/wildcards)
	*@param matchIndex is the current point in the match string.
	*@return true if there is a match.
	*/
	protected static boolean processPartialPathCheck(boolean caseSensitive, String sourceMatch, int sourceIndex, String match, int matchIndex,
		int requiredExtraPathSections)
	{
		// Match up through the next * we encounter
		while ( true )
		{
			// If we've reached the end of the source, verify that it's a match.
			if ( sourceMatch.length() == sourceIndex)
			{
				// The "correct" way to code this is to recursively attempt to generate all different paths that correspond to the required extra sections.  However,
				// that's computationally very nasty.  In practice, we'll simply distinguish between "some" and "none".
				// If we've reached the end of the match string too, then it passes (or fails, if we need extra sections)
				if (match.length() == matchIndex)
					return (requiredExtraPathSections == 0);
				// We can match a path separator, so we win
				return true;
			}
			// If we have reached the end of the match (but not the source), match fails
			if ( match.length() == matchIndex )
				return false;
			char x = sourceMatch.charAt( sourceIndex );
			char y = match.charAt( matchIndex );
			if ( !caseSensitive )
			{
				if ( x >= 'A' && x <= 'Z' )
					x -= 'A'-'a';
				if ( y >= 'A' && y <= 'Z' )
					y -= 'A'-'a';
			}
			if ( y == '*' )
			{
				// Wildcard!
				// We will recurse at this point.
				// Basically, we want to combine the results for leaving the "*" in the match string
				// at this point and advancing the source index, with skipping the "*" and leaving the source
				// string alone.
				return processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex + 1, match, matchIndex, requiredExtraPathSections ) ||
					   processPartialPathCheck( caseSensitive, sourceMatch, sourceIndex, match, matchIndex + 1, requiredExtraPathSections );
			}
			if ( y == '?' || x == y )
			{
				sourceIndex++;
				matchIndex++;
			}
			else
				return false;
		}
	}

	/** Check a match between two strings with wildcards.
	*@param sourceMatch is the expanded string (no wildcards)
	*@param sourceIndex is the starting point in the expanded string.
	*@param match is the wildcard-based string.
	*@return true if there is a match.
	*/
	protected static boolean checkMatch( String sourceMatch, int sourceIndex, String match )
	{
		// Note: The java regex stuff looks pretty heavyweight for this purpose.
		// I've opted to try and do a simple recursive version myself, which is not compiled.
		// Basically, the match proceeds by recursive descent through the string, so that all *'s cause
		// recursion.
		boolean caseSensitive = true;

		return processCheck( caseSensitive, sourceMatch, sourceIndex, match, 0 );
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
	protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex, String match, int matchIndex )
	{
		// Match up through the next * we encounter
		while ( true )
		{
			// If we've reached the end, it's a match.
			if ( sourceMatch.length() == sourceIndex && match.length() == matchIndex )
				return true;
			// If one has reached the end but the other hasn't, no match
			if ( match.length() == matchIndex )
				return false;
			if ( sourceMatch.length() == sourceIndex )
			{
				if ( match.charAt(matchIndex) != '*' )
					return false;
				matchIndex++;
				continue;
			}
			char x = sourceMatch.charAt( sourceIndex );
			char y = match.charAt( matchIndex );
			if ( !caseSensitive )
			{
				if ( x >= 'A' && x <= 'Z' )
					x -= 'A'-'a';
				if ( y >= 'A' && y <= 'Z' )
					y -= 'A'-'a';
			}
			if ( y == '*' )
			{
				// Wildcard!
				// We will recurse at this point.
				// Basically, we want to combine the results for leaving the "*" in the match string
				// at this point and advancing the source index, with skipping the "*" and leaving the source
				// string alone.
				return processCheck( caseSensitive, sourceMatch, sourceIndex + 1, match, matchIndex ) ||
					   processCheck( caseSensitive, sourceMatch, sourceIndex, match, matchIndex + 1 );
			}
			if ( y == '?' || x == y )
			{
				sourceIndex++;
				matchIndex++;
			}
			else
				return false;
		}
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

	/** Decode a path item.
	*/
	public static String pathItemDecode(String pathItem)
	{
		try
		{
			return java.net.URLDecoder.decode(pathItem.replaceAll("\\%20","+"),"utf-8");
		}
		catch (UnsupportedEncodingException e)
		{
			// Bad news, utf-8 not available!
			throw new RuntimeException("No utf-8 encoding available");
		}
	}
	
	/** Encode a path item.
	*/
	public static String pathItemEncode(String pathItem)
	{
		try
		{
			String output = java.net.URLEncoder.encode(pathItem,"utf-8");
			return output.replaceAll("\\+","%20");
		}
		catch (UnsupportedEncodingException e)
		{
			// Bad news, utf-8 not available!
			throw new RuntimeException("No utf-8 encoding available");
		}
	}

	/** Given a path that is /-separated, and otherwise encoded, decode properly to convert to
	* unencoded form.
	*/
	public static String decodePath(String relPath)
	{
		StringBuffer sb = new StringBuffer();
		String[] pathEntries = relPath.split("/");
		int k = 0;

		boolean isFirst = true;
		while (k < pathEntries.length)
		{
			if (isFirst)
				isFirst = false;
			else
				sb.append("/");
			sb.append(pathItemDecode(pathEntries[k++]));
		}
		return sb.toString();
	}
	
	/** Given a path that is /-separated, and otherwise unencoded, encode properly for an actual
	* URI 
	*/
	public static String encodePath(String relPath)
	{
		StringBuffer sb = new StringBuffer();
		String[] pathEntries = relPath.split("/");
		int k = 0;

		boolean isFirst = true;
		while (k < pathEntries.length)
		{
			if (isFirst)
				isFirst = false;
			else
				sb.append("/");
			sb.append(pathItemEncode(pathEntries[k++]));
		}
		return sb.toString();
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

	/** Metadata information gleaned from document paths and specification.
	*/
	protected static class MetadataInformation
	{
		protected boolean allMetadata = false;
		protected HashMap metadataFields = new HashMap();
		
		/** Constructor */
		public MetadataInformation()
		{
		}
		
		/** Set "all metadata" */
		public void setAllMetadata()
		{
			allMetadata = true;
		}
		
		/** Add a metadata field */
		public void addMetadataField(String fieldName)
		{
			metadataFields.put(fieldName,fieldName);
		}
		
		/** Get whether "all metadata" is to be used */
		public boolean getAllMetadata()
		{
			return allMetadata;
		}
		
		/** Get the set of metadata fields to use */
		public String[] getMetadataFields()
		{
			String[] rval = new String[metadataFields.size()];
			Iterator iter = metadataFields.keySet().iterator();
			int i = 0;
			while (iter.hasNext())
			{
				rval[i++] = (String)iter.next();
			}
			return rval;
		}
	}

	/** Class that tracks paths associated with id's, and the name
	* of the metadata attribute to use for the path.
	*/
	protected class SystemMetadataDescription
	{
		// The path attribute name
		protected String pathAttributeName;

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
			throws MetacartaException
		{
			String path = getPathString(documentIdentifier);
			return matchMap.translate(path);
		}

		/** For a given id, get the portion of its path which the mapping and ingestion
		* should go against.  Effectively this should include the whole identifer, so this
		* is easy to calculate.
		*/
		public String getPathString(String documentIdentifier)
			throws MetacartaException
		{
			// There will be a "//" somewhere in the string.  Remove it!
			int dslashIndex = documentIdentifier.indexOf("//");
			if (dslashIndex == -1)
				return documentIdentifier;
			return documentIdentifier.substring(0,dslashIndex) + documentIdentifier.substring(dslashIndex+1);
		}
	}

	/** Socket factory for our https implementation.
	*/
	protected static class MySSLSocketFactory implements org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory
	{
		protected javax.net.ssl.SSLSocketFactory thisSocketFactory = null;
		protected IKeystoreManager keystore;

		/** Constructor.  Pass the keystore.
		*/
		public MySSLSocketFactory(IKeystoreManager keystore)
			throws MetacartaException
		{
			this.keystore = keystore;
			thisSocketFactory = keystore.getSecureSocketFactory();
		}


		public Socket createSocket(String host,
			int port,
			InetAddress clientHost,
			int clientPort)
			throws IOException, UnknownHostException
		{
			return thisSocketFactory.createSocket(host,
				port,
				clientHost,
				clientPort);
		}


		public Socket createSocket(final String host,
			final int port,
			final InetAddress localAddress,
			final int localPort,
			final HttpConnectionParams params)
			throws IOException, UnknownHostException, ConnectTimeoutException
		{
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
			{
				return createSocket(host, port, localAddress, localPort);

/*
				return thisSocketFactory.createSocket(host,
					port,
					localAddress,
					localPort,
					timeout);
*/
			}
		}

		public Socket createSocket(String host, int port)
			throws IOException, UnknownHostException
		{
			return thisSocketFactory.createSocket(host,port);
		}

		public Socket createSocket(Socket socket,
			String host,
			int port,
			boolean autoClose)
			throws IOException, UnknownHostException
		{
			return thisSocketFactory.createSocket(socket,
				host,
				port,
				autoClose);
		}


		/** There's a socket factory per keystore;
		* look at the keystore to do the comparison.
		*/
		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof MySSLSocketFactory))
				return false;
			MySSLSocketFactory other = (MySSLSocketFactory)obj;
			try
			{
				return keystore.getString().equals(other.keystore.getString());
			}
			catch (MetacartaException e)
			{
				return false;
			}
		}

		public int hashCode()
		{
			try
			{
				return keystore.getString().hashCode();
			}
			catch (MetacartaException e)
			{
				return 0;
			}
		}


	}

	/** HTTPClient secure socket factory, which implements SecureProtocolSocketFactory
	*/
	protected static class SharepointSecureSocketFactory implements SecureProtocolSocketFactory
	{
		/** This is the javax.net socket factory.
		*/
		protected javax.net.ssl.SSLSocketFactory socketFactory;

		/** Constructor */
		public SharepointSecureSocketFactory(javax.net.ssl.SSLSocketFactory socketFactory)
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
        		return socketFactory.createSocket(
            			socket,
            			host,
            			port,
            			autoClose
        			);
    		}

		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof SharepointSecureSocketFactory))
				return false;
			// Each object is unique
			return super.equals(obj);
		}

		public int hashCode()
		{
        		return super.hashCode();
    		}    

	}

}
