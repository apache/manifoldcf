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
package org.apache.lcf.crawler.connectors.meridio;

import com.meridio.www.MeridioDMWS.DmLogicalOp;
import com.meridio.www.MeridioDMWS.DmPermission;
import com.meridio.www.MeridioDMWS.DmSearchScope;
import com.meridio.www.MeridioDMWS.DmVersionInfo;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.connectors.meridio.meridiowrapper.DMSearchResults;
import org.apache.lcf.crawler.connectors.meridio.meridiowrapper.MeridioDataSetException;
import org.apache.lcf.crawler.connectors.meridio.meridiowrapper.MeridioWrapper;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolFactory;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import java.io.File;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;

import javax.xml.soap.SOAPException;

import org.apache.axis.attachments.AttachmentPart;

import org.apache.lcf.crawler.connectors.meridio.DMDataSet.*;
import org.apache.lcf.crawler.connectors.meridio.RMDataSet.*;

/** This is the "repository connector" for a file system. 
*/
public class MeridioConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
	public static final String _rcsid = "@(#)$Id$";

	// This is the base url to use.
	protected String urlBase = null;
	protected String urlVersionBase = null;
	
	/** Deny access token for Meridio */
	private final static String denyToken = "DEAD_AUTHORITY";

	/** Deny access token for default authority */
	private final static String defaultAuthorityDenyToken = "McAdAuthority_MC_DEAD_AUTHORITY";

	private static final long interruptionRetryTime = 60000L;

	// These are the variables needed to establish a connection
	protected URL DmwsURL = null;
	protected URL RmwsURL = null;
	protected ProtocolFactory myFactory = null;
	protected MeridioWrapper meridio_  = null;  // A handle to the Meridio Java API Wrapper

	/** Constructor.
	*/
	public MeridioConnector() {}

	
	
	/** Tell the world what model this connector uses for getDocumentIdentifiers().
	* This must return a model value as specified above.
	*@return the model type value.
	*/
	public int getConnectorModel()
	{
		// Return the simplest model - full everything
		return MODEL_ADD_CHANGE;
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
		final String jspFolder = "meridio";
		return jspFolder;
	}

	/** Set up the session with Meridio */
	protected void getSession()
		throws LCFException, ServiceInterruption
	{
		if (meridio_ == null)
		{
			// Do the first part (which used to be in connect() itself)
			try
			{								
				/*=================================================================
				* Construct the URL strings from the parameters
				*================================================================*/
				String DMWSProtocol = params.getParameter("DMWSServerProtocol");
				String DMWSPort = params.getParameter("DMWSServerPort");
				if (DMWSPort == null || DMWSPort.length() == 0)
					DMWSPort = "";
				else
					DMWSPort = ":" + DMWSPort;
					
				String Url = DMWSProtocol + "://" +
							 params.getParameter("DMWSServerName") +
							 DMWSPort +
							 params.getParameter("DMWSLocation");							
				
				Logging.connectors.debug("Meridio: Document Management Web Service (DMWS) URL is [" + Url + "]");				
				DmwsURL = new URL(Url);
							
				String RMWSProtocol = params.getParameter("RMWSServerProtocol");
				String RMWSPort = params.getParameter("RMWSServerPort");
				if (RMWSPort == null || RMWSPort.length() == 0)
					RMWSPort = "";
				else
					RMWSPort = ":" + RMWSPort;

				Url = RMWSProtocol + "://" +
					  params.getParameter("RMWSServerName") +
				      RMWSPort +
				      params.getParameter("RMWSLocation");
				
				Logging.connectors.debug("Meridio: Record Management Web Service (RMWS) URL is [" + Url + "]");
				RmwsURL = new URL(Url);			       		 

				// Set up ssl if indicated
				String keystoreData = params.getParameter( "MeridioKeystore" );
				myFactory = new ProtocolFactory();

				if (keystoreData != null)
				{
					IKeystoreManager keystoreManager = KeystoreManagerFactory.make("",keystoreData);
					MeridioSecureSocketFactory secureSocketFactory = new MeridioSecureSocketFactory(keystoreManager.getSecureSocketFactory());
					Protocol myHttpsProtocol = new Protocol("https", (ProtocolSocketFactory)secureSocketFactory, 443);
					myFactory.registerProtocol("https",myHttpsProtocol);
				}

				// Put together the url base
				String clientProtocol = params.getParameter("MeridioWebClientProtocol");
				String clientPort = params.getParameter("MeridioWebClientServerPort");
				if (clientPort == null || clientPort.length() == 0)
					clientPort = "";
				else
					clientPort = ":"+clientPort;
				urlVersionBase = clientProtocol + "://" + params.getParameter("MeridioWebClientServerName") + clientPort +
					params.getParameter("MeridioWebClientDocDownloadLocation");
				urlBase = urlVersionBase + "?launchMode=1&launchAs=0&documentId=";

			}		
			catch (MalformedURLException malformedURLException)
			{			
				throw new LCFException("Meridio: Could not construct the URL for either " +
								 "the DM or RM Meridio Web Service", malformedURLException, LCFException.REPOSITORY_CONNECTION_ERROR);
			}

			// Do the second part (where we actually try to connect to the system)
			try
			{
				/*=================================================================
				* Now try and login to Meridio; the wrapper's constructor can be
				* used as it calls the Meridio login method
				*================================================================*/
                                String meridioWSDDLocation = System.getProperty("org.apache.lcf.meridio.wsddpath");
                                if (meridioWSDDLocation == null)
                                        throw new LCFException("Meridio wsdd location path (property org.apache.lcf.meridio.wsddpath) must be specified!");

				meridio_ = new MeridioWrapper(Logging.connectors, DmwsURL, RmwsURL, null,
							      params.getParameter("DMWSProxyHost"),
							      params.getParameter("DMWSProxyPort"),
							      params.getParameter("RMWSProxyHost"),
							      params.getParameter("RMWSProxyPort"),
							      null,
							      null,
					                      params.getParameter("UserName"), 
					                      params.getObfuscatedParameter("Password"),					                      
					                      InetAddress.getLocalHost().getHostName(),
							      myFactory,
							      meridioWSDDLocation);
			}
			catch (UnknownHostException unknownHostException)
			{
				throw new LCFException("Meridio: A Unknown Host Exception occurred while " +
					"connecting - is a network software and hardware configuration: "+unknownHostException.getMessage(), unknownHostException);
			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
					}
					throw new LCFException("Unknown http error occurred while connecting: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Meridio: Got an unknown remote exception connecting - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
				throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
					currentTime + 3 * 60 * 60000L,-1,false);
			}
			catch (RemoteException remoteException)
			{			
				throw new LCFException("Meridio: An unknown remote exception occurred while " +
					"connecting: "+remoteException.getMessage(), remoteException);
			}

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
		String dmwshost = params.getParameter("DMWSServerName");
		String rmwshost = params.getParameter("RMWSServerName");
		return new String[]{dmwshost,rmwshost};
	}
	
	/** Test the connection.  Returns a string describing the connection integrity.
	*@return the connection's status as a displayable string.
	*/
	public String check()
		throws LCFException
	{
		Logging.connectors.debug("Meridio: Entering 'check' method");

		try
		{
			// Force a relogin
			meridio_ = null;
			getSession();
		}
		catch (ServiceInterruption e)
		{
			return "Meridio temporarily unavailable: "+e.getMessage();
		}
		catch (LCFException e)
		{
			return e.getMessage();
		}

		try
		{
			
			/*=================================================================
			* Call a method in the Web Services API to get the Meridio system
			* name back - just something simple to test the connection
			* end-to-end			
			*================================================================*/
			DMDataSet ds = meridio_.getStaticData();
			if (null == ds)
			{
				Logging.connectors.debug("Meridio: DM DataSet returned was null in 'check' method");				
				return "Connection failed - null DM DataSet";				
			}

			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Meridio System Name is [" + 
					ds.getSYSTEMINFO().getSystemName() + "] and the comment is [" +
					ds.getSYSTEMINFO().getComment() + "]");
			
			/*=================================================================
			* For completeness, we also call a method in the RM Web
			* Service API
			*================================================================*/		
			RMDataSet rmws = meridio_.getConfiguration();
			if (null == rmws)
			{
				Logging.connectors.warn("Meridio: RM DataSet returned was null in 'check' method");				
				return "Connection failed - null RM DataSet returned";				
			}
						
			return super.check();
		}
		catch (org.apache.axis.AxisFault e)
		{
			long currentTime = System.currentTimeMillis();
			if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
			{
				org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
				if (elem != null)
				{
					elem.normalize();
					String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
					return "Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage();
				}
				return "Unknown http error occurred while checking: "+e.getMessage();
			}
			if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
			{
				String exceptionName = e.getFaultString();
				if (exceptionName.equals("java.lang.InterruptedException"))
					throw new LCFException("Interrupted",LCFException.INTERRUPTED);
			}
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Meridio: Got an unknown remote exception checking - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
			return "Axis fault: "+e.getMessage();
		}
		catch (RemoteException remoteException)
		{
			/*=================================================================
			* Log the exception because we will then discard it
			* 
			* Potentially attempting to re-login may resolve this error but
			* if it is being called soon after a successful login, then that
			* is unlikely. 
			* 
			* A RemoteException could be a transient network error
			*================================================================*/
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Meridio: Unknown remote exception occurred during 'check' method: "+remoteException.getMessage(), 
					remoteException);
			
			return "Connection failed - Remote exception: "+remoteException.getMessage();
		}
		catch (MeridioDataSetException meridioDataSetException)
		{
			/*=================================================================
			* Log the exception because we will then discard it
			* 
			* If it is a DataSet exception it means that we could not marshal
			* or unmarshall the XML returned from the Web Service call. This
			* means there is either a problem with the code, or perhaps the 
			* connector is pointing at an incorrect/unsupported version of
			* Meridio 
			*================================================================*/
			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("Meridio: DataSet exception occurred during 'check' method: "+meridioDataSetException.getMessage(), 
					meridioDataSetException);
			
			return "Connection failed - DataSet exception: "+meridioDataSetException.getMessage();
		}
		finally
		{
			Logging.connectors.debug("Meridio: Exiting 'check' method");
		}				
	}
	
	
	
	/** Close the connection.  Call this before discarding the repository connector.
	*/
	public void disconnect()
		throws LCFException
	{
		Logging.connectors.debug("Meridio: Entering 'disconnect' method");
		
		try
		{					
			if (meridio_ != null)
			{
				meridio_.logout();	
			}
		}
		catch (org.apache.axis.AxisFault e)
		{
			long currentTime = System.currentTimeMillis();
			if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
			{
				org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
				if (elem != null)
				{
					elem.normalize();
					String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
					Logging.connectors.warn("Unexpected http error code "+httpErrorCode+" logging out: "+e.getMessage());
					return;
				}
				Logging.connectors.warn("Unknown http error occurred while logging out: "+e.getMessage());
				return;
			}
			if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
			{
				String exceptionName = e.getFaultString();
				if (exceptionName.equals("java.lang.InterruptedException"))
					throw new LCFException("Interrupted",LCFException.INTERRUPTED);
			}
			if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
			{
				if (e.getFaultString().indexOf(" 23031#") != -1)
				{
					// This means that the session has expired, so reset it and retry
					meridio_ = null;
					return;
				}
			}

			Logging.connectors.warn("Meridio: Got an unknown remote exception logging out - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
			return;
		}
		catch (RemoteException remoteException)
		{
			Logging.connectors.warn("Meridio: A remote exception occurred while " +
				"logging out: "+remoteException.getMessage(), remoteException);
		}
		finally
		{
			super.disconnect();			
			meridio_ = null;
			urlBase = null;
			urlVersionBase = null;
			DmwsURL = null;
			RmwsURL = null;
			myFactory = null;
			Logging.connectors.debug("Meridio: Exiting 'disconnect' method");
		}
	}
	
	
	
	/** Get the maximum number of documents to amalgamate together into one batch, for this connector.
	*@return the maximum number. 0 indicates "unlimited".
	*/	
	public int getMaxDocumentRequest()
	{		
		return 10;
	}

	
	
	/** Given a document specification, get either a list of starting document identifiers (seeds),
	* or a list of changes (deltas), depending on whether this is a "crawled" connector or not.
	* These document identifiers will be loaded into the job's queue at the beginning of the
	* job's execution.
	* This method can return changes only (because it is provided a time range).  For full
	* recrawls, the start time is always zero.
	* Note that it is always ok to return MORE documents rather than less with this method.
	*@param spec is a document specification (that comes from the job).
	*@param startTime is the beginning of the time range to consider, inclusive.
	*@param endTime is the end of the time range to consider, exclusive.
	*@return the stream of local document identifiers that should be added to the queue.
	*/
	public IDocumentIdentifierStream getDocumentIdentifiers(DocumentSpecification spec, long startTime, long endTime)
		throws LCFException, ServiceInterruption
	{				
		Logging.connectors.debug("Meridio: Entering 'getDocumentIdentifiers' method");
		
		try
		{
			// Adjust start time so that we don't miss documents that squeeze in with earlier timestamps after we've already scanned that interval.
			// Chose an interval of 15 minutes, but I've never seen this effect take place over a time interval even 1/10 of that.
			long timeAdjust = 15L * 60000L;
			if (startTime > timeAdjust)
				startTime -= timeAdjust;
			else
				startTime = 0L;
			return new IdentifierStream(spec, startTime, endTime);
		}		
		finally
		{
			Logging.connectors.debug("Meridio: Exiting 'getDocumentIdentifiers' method");
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
		throws LCFException, ServiceInterruption
	{			
		Logging.connectors.debug("Meridio: Entering 'getDocumentVersions' method");

		// Get forced acls/security enable/disable
		String[] acls = getAcls(spec);
		// Sort it, in case it is needed.
		if (acls != null)
			java.util.Arrays.sort(acls);

		// Look at the metadata attributes.
		// So that the version strings are comparable, we will put them in an array first, and sort them.
		HashMap holder = new HashMap();

		String pathAttributeName = null;
		MatchMap matchMap = new MatchMap();
		boolean allMetadata = false;

		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i++);
			if (n.getType().equals("ReturnedMetadata"))
			{
				String category = n.getAttributeValue("category");
				String attributeName = n.getAttributeValue("property");
				String metadataName;
				if (category == null || category.length() == 0)
					metadataName = attributeName;
				else
					metadataName = category + "." + attributeName;
				holder.put(metadataName,metadataName);
			}
			else if (n.getType().equals("AllMetadata"))
			{
				String value = n.getAttributeValue("value");
				if (value != null && value.equals("true"))
				{
					allMetadata = true;
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

		while (true)
		{

			getSession();
			
			// The version string returned must include everything that could affect what is ingested.  In meridio's
			// case, this includes the date stamp, but it also includes the part of the specification that describes
			// the metadata desired.

			// The code here relies heavily on the search method to do it's thing.  The search method originally
			// used the document specification to determine what metadata to return, which was problematic because that
			// meant this method had to modify the specification (not good practice), and was also wrong from the point
			// of view that we need to get the metadata specification appended to the version string in some way, and
			// use THAT data in processDocuments().  So I've broken all that up.
					
			try
			{
				// Put into an array
				String[] sortArray;
				if (allMetadata)
				{
				    sortArray = getMeridioDocumentProperties();
				}
				else
				{
				    sortArray = new String[holder.size()];
				    i = 0;
				    Iterator iter = holder.keySet().iterator();
				    while (iter.hasNext())
				    {
					    sortArray[i++] = (String)iter.next();
				    }
				}
				// Sort!
				java.util.Arrays.sort(sortArray);

				// Prepare the part of the version string that is decodeable
				StringBuffer decodeableString = new StringBuffer();

				// Add the metadata piece first
				packList(decodeableString,sortArray,'+');
				// Now, put in the forced acls.
				// The version string needs only to contain the forced acls, since the version date captures changes
				// made to the acls that are actually associated with the document.
				if (acls == null)
					decodeableString.append('-');
				else
				{
					decodeableString.append('+');
					packList(decodeableString,acls,'+');
					// KDW: Added this 4/21/2008 to handle the relationship with the global authority
					if (usesDefaultAuthority)
					{
						decodeableString.append('+');
						pack(decodeableString,defaultAuthorityDenyToken,'+');
					}
					else
						decodeableString.append('-');
				}

				// Calculate the part of the version string that comes from path name and mapping.
				if (pathAttributeName != null)
				{
					decodeableString.append("+");
					pack(decodeableString,pathAttributeName,'+');
					pack(decodeableString,matchMap.toString(),'+');
				}
				else
					decodeableString.append("-");

				String[] rval 					= new String[documentIdentifiers.length];
				long[] docIds 					= new long[documentIdentifiers.length];
				DMSearchResults searchResults 	= null;
				
				/*=================================================================
				* Convert the string array of document identifiers to an array of
				* integers
				*================================================================*/
				for (i = 0; i < documentIdentifiers.length; i++)
				{
					docIds[i] = new Long(documentIdentifiers[i]).longValue();
				}
				
				/*=================================================================
				* Call the search, with the document specification and the list of
				* document ids - the search will never return more than exactly
				* one match per document id
				* 
				* We are assuming that the maximum number of hits to return
				* should never be more than the maximum batch size set up for this
				* class
				* 
				* We are just making one web service call (to the search API) 
				* rather than iteratively calling a web service method for each
				* document passed in as part of the document array
				* 
				* Additionally, re-using the same search method as for the 
				* "getDocumentIdentifiers" method ensures that we are not
				* duplicating any logic which ensures that the document/records
				* in question match the search criteria or not.
				*================================================================*/
				searchResults = documentSpecificationSearch(spec,
						0, 0, 1, this.getMaxDocumentRequest(), docIds, null);	
				
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Found a total of <" + searchResults.totalHitsCount + "> hit(s) " +
						"and <" + searchResults.returnedHitsCount + "> were returned by the method call");

				// If we are searching based on document identifier, then it is possible that we will not
				// find a document we are looking for, if it was removed from the system between the time
				// it was put in the queue and when it's version is obtained.  Documents where this happens
				// should return a version string of null.

				// Let's go through the search results and build a hash based on the document identifier.
				HashMap documentMap = new HashMap();
				if (searchResults.dsDM != null)
				{
					SEARCHRESULTS_DOCUMENTS [] srd = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
					for (i = 0; i < srd.length; i++)
					{
						documentMap.put(new Long(srd[i].getDocId()),srd[i]);
					}
				}

				// Now, walk through the individual documents.
				int j = 0;
				while (j < docIds.length)
				{	
					long docId = docIds[j];
					Long docKey = new Long(docId);
					// Look up the record.
					SEARCHRESULTS_DOCUMENTS doc = (SEARCHRESULTS_DOCUMENTS)documentMap.get(docKey);
					if (doc != null)
					{
						// Set the version string.  The parseable stuff goes first, so parsing is easy.
						String version = doc.getStr_value();
						StringBuffer composedVersion = new StringBuffer();
						composedVersion.append(decodeableString);
						composedVersion.append(version);
						// Added 9/7/2007
						composedVersion.append("_").append(urlVersionBase);
						//
						rval[j] = composedVersion.toString();
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Document "+docKey+" has version "+rval[j]);
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Document "+docKey+" is no longer in the search set, or has been deleted - removing.");
						rval[j] = null;
					}
					j++;
				}

				Logging.connectors.debug("Meridio: Exiting 'getDocumentVersions' method");

				return rval;

			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
					}
					throw new LCFException("Unknown http error occurred while getting doc versions: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
				{
					if (e.getFaultString().indexOf(" 23031#") != -1)
					{
						// This means that the session has expired, so reset it and retry
						meridio_ = null;
						continue;
					}
				}

				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Meridio: Got an unknown remote exception getting doc versions - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
				throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
					currentTime + 3 * 60 * 60000L,-1,false);
			}
			catch (RemoteException remoteException)
			{
				throw new LCFException("Meridio: A remote exception occurred while getting doc versions: " +
					remoteException.getMessage(), remoteException);
			}
			catch (MeridioDataSetException meridioDataSetException)
			{
				throw new LCFException("Meridio: A problem occurred manipulating the Web " +
					"Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
			}
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
		throws LCFException, ServiceInterruption
	{
		Logging.connectors.debug("Meridio: Entering 'processDocuments' method");

		// First step: Come up with the superset of all the desired metadata.  This will be put together from
		// the version strings that were passed in.
		// I'm also going to use this loop to produce the actual set of document identifiers that we'll want to
		// query on.

		ArrayList neededQueryIDs = new ArrayList();
		HashMap metadataSet = new HashMap();
		ArrayList metadataItems = new ArrayList();

		int i = 0;
		while (i < versions.length)
		{
			boolean scanOnlyValue = scanOnly[i];
			if (!scanOnlyValue)
			{
				neededQueryIDs.add(documentIdentifiers[i]);
				String versionString = versions[i];
				metadataItems.clear();
				// The metadata spec is the first element of the version string.
				unpackList(metadataItems,versionString,0,'+');
				int j = 0;
				while (j < metadataItems.size())
				{
					String categoryProperty = (String)metadataItems.get(j++);
					metadataSet.put(categoryProperty,categoryProperty);
				}
			}
			i++;
		}
		
		// Convert to a string array of category/property values
		ReturnMetadata[] categoryPropertyValues = new ReturnMetadata[metadataSet.size()];
		String[] categoryPropertyStringValues = new String[metadataSet.size()];
		i = 0;
		Iterator iter = metadataSet.keySet().iterator();
		while (iter.hasNext())
		{
			String value = (String)iter.next();
			categoryPropertyStringValues[i] = value;

			int dotIndex = value.indexOf(".");
			String categoryName = null;
			String propertyName;
			if (dotIndex == -1)
				propertyName = value;
			else
			{
				categoryName = value.substring(0,dotIndex);
				propertyName = value.substring(dotIndex+1);
			}

			categoryPropertyValues[i++] = new ReturnMetadata(categoryName,propertyName);
		}

		while (true)
		{
			getSession();
			
			// This method uses the search call to locate the document metadata, and associated methods to locate the document
			// contents and acls.
			//
			// It looks like Meridio returns a distinct record from its search API for every specified piece of metadata.
			// We can only specify one set of metadata for all records.  That means the basic algorithm here is going to have to be:
			// 1) Find the superset of all the metadata records we want
			// 2) Call the search API to get the metadata information
			// 3) Look up the metadata information just prior to ingestion time, making SURE that each piece of metadata is
			//    in fact specified for that document.
			try
			{

				HashMap documentMap = new HashMap();

				// Only look up metadata if we need some!
				if (neededQueryIDs.size() > 0 && categoryPropertyValues.length > 0)
				{
					long[] docIds = new long[neededQueryIDs.size()];
				
					/*=================================================================
					* Convert the string array of document identifiers to an array of
					* integers
					*================================================================*/
					for (i = 0; i < neededQueryIDs.size(); i++)
					{
						docIds[i] = new Long((String)neededQueryIDs.get(i)).longValue();
					}
				
					/*=================================================================
					* Call the search, with the document specification and the list of
					* document ids - the search will never return more than exactly
					* one match per document id
					*
					* This call will return all the metadata that was specified in the
					* document specification for all the documents and
					* records in one call.
					*================================================================*/
					DMSearchResults searchResults = documentSpecificationSearch(spec,
						0, 0, 1, docIds.length,
						docIds, categoryPropertyValues);	

					// If we ask for a document and it is no longer there, we should treat this as a deletion.
					// The activity in that case is to delete the document.  A similar thing should happen if
					// any of the other methods (like getting the document's content) also fail to find the
					// document.

					// Let's build a hash which contains all the document metadata returned.  The form of
					// the hash will be: key = the document identifier, value = another hash, which is keyed
					// by the metadata category/property, and which has a value that is the metadata value.

					HashMap counterMap = new HashMap();

					if (searchResults.dsDM != null)
					{
						SEARCHRESULTS_DOCUMENTS [] searchResultsDocuments = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
						i = 0;
						while (i < searchResultsDocuments.length)
						{
							SEARCHRESULTS_DOCUMENTS searchResultsDocument = searchResultsDocuments[i];
							long docId = searchResultsDocument.getDocId();
							Long docKey = new Long(docId);
							MutableInteger counterMapItem = (MutableInteger)counterMap.get(docKey);
							if (counterMapItem == null)
							{
								counterMapItem = new MutableInteger();
								counterMap.put(docKey,counterMapItem);
							}

							String propertyName = categoryPropertyStringValues[counterMapItem.getValue()];
							counterMapItem.increment();
							String propertyValue = searchResultsDocuments[i].getStr_value();
							HashMap propertyMap = (HashMap)documentMap.get(docKey);
							if (propertyMap == null)
							{
								propertyMap = new HashMap();
								documentMap.put(docKey,propertyMap);
							}
							if (propertyValue != null && propertyValue.length() > 0)
								propertyMap.put(propertyName,propertyValue);
							i++;
						}
					}
				}

				// Okay, we are ready now to go through the individual documents and do the ingestion or deletion.
				i = 0;
				while (i < documentIdentifiers.length)
				{
					String documentIdentifier = documentIdentifiers[i];
					Long docKey = new Long(documentIdentifier);
					long docId = docKey.longValue();
					String docVersion = versions[i];

					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Processing document identifier '" + documentIdentifier + "' " +
									 "with version string '" + docVersion + "'");
					
					if (scanOnly[i])
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Scan only for document '" + documentIdentifier + "'");
						i++;
						continue;
					}
					
					// For each document, be sure the job is still allowed to run.
					activities.checkJobStillActive();
					
					RepositoryDocument repositoryDocument = new RepositoryDocument();

					// Prepare to parse the version string for this individual document.  We'll want
					// to produce a list of the actual properties we want.
					int startPos = 0;

					// Metadata items
					ArrayList metadataItems0 = new ArrayList();
					startPos = unpackList(metadataItems0,docVersion,startPos,'+');
					// Forced acls
					String[] forcedAcls;
					String denyAcl = null;
					if (startPos < docVersion.length())
					{
						char x = docVersion.charAt(startPos++);
						if (x == '+')
						{
							ArrayList acls = new ArrayList();
							startPos = unpackList(acls,docVersion,startPos,'+');
							// Turn into acls and add into description
							forcedAcls = new String[acls.size()];
							int j = 0;
							while (j < forcedAcls.length)
							{
								forcedAcls[j] = (String)acls.get(j);
								j++;
							}
							if (startPos < docVersion.length())
							{
								x = docVersion.charAt(startPos++);
								if (x == '+')
								{
									StringBuffer denyAclBuffer = new StringBuffer();
									unpack(denyAclBuffer,docVersion,startPos,'+');
									denyAcl = denyAclBuffer.toString();
								}
							}
						}
						else
						{
							forcedAcls = null;
						}
					}
					else
					{
						forcedAcls = new String[0];
					}
					
					// Path attribute name and mapping
					String pathAttributeName = null;
					MatchMap matchMap = null;
					if (startPos < docVersion.length())
					{
						char x = docVersion.charAt(startPos++);
						if (x == '+')
						{
							StringBuffer sb = new StringBuffer();
							startPos = unpack(sb,docVersion,startPos,'+');
							pathAttributeName = sb.toString();
							sb.setLength(0);
							startPos = unpack(sb,docVersion,startPos,'+');
							// Initialize matchmap.
							matchMap = new MatchMap(sb.toString());
						}
					}

					// Load the metadata items into the ingestion document object
					HashMap docMetadataMap = (HashMap)documentMap.get(docKey);
					if (docMetadataMap != null)
					{
						int j = 0;
						while (j < metadataItems0.size())
						{
							String categoryPropertyName = (String)metadataItems0.get(j++);
							String propertyValue = (String)docMetadataMap.get(categoryPropertyName);
							if (propertyValue != null && propertyValue.length() > 0)
								repositoryDocument.addField(categoryPropertyName,propertyValue);
						}
					}

					/*=================================================================
					* Construct the URL to the object
					* 
					* HTTP://HOST:PORT/meridio/browse/downloadcontent.aspx?documentId=<docId>&launchMode=1&launchAs=0
					* 
					* I expect we need to add additional parameters to the configuration
					* specification
					*================================================================*/
					String fileURL = urlBase + new Long(docId).toString();
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("URL for document '" + new Long(docId).toString() + "' is '" + fileURL + "'");
													
					/*=================================================================
					* Get the object's ACLs and owner information
					*================================================================*/
					DMDataSet documentData = null;
					documentData = meridio_.getDocumentData((int)docId, true, true, false, false, 
							DmVersionInfo.LATEST, false, false, false);
					
					if (null == documentData)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Could not retrieve document data for document id '" + 
								new Long(docId).toString() + "' in processDocuments method - deleting document.");
						activities.deleteDocument(documentIdentifier);
						i++;
						continue;												
					}

					if (null == documentData.getDOCUMENTS() ||
						documentData.getDOCUMENTS().length != 1)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Could not retrieve document owner for document id '" + 
								new Long(docId).toString() + "' in processDocuments method. No information or incorrect amount " +
								"of information was returned");	
						activities.deleteDocument(documentIdentifier);
						i++;
						continue;
					}

					// Do path metadata
					if (pathAttributeName != null && pathAttributeName.length() > 0)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Path attribute name is "+pathAttributeName);
						RMDataSet partList;
						int recordType = documentData.getDOCUMENTS()[0].getPROP_recordType();
						if (recordType == 0 || recordType == 4 || recordType == 19)
							partList = meridio_.getRecordPartList((int)docId, false, false);
						else
							partList = meridio_.getDocumentPartList((int)docId);
						if (partList != null)
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: Document '"+new Long(docId).toString()+"' has a part list with "+Integer.toString(partList.getRm2vPart().length)+" values");

							for (int k = 0; k < partList.getRm2vPart().length; k++)
							{
								 repositoryDocument.addField(pathAttributeName,matchMap.translate(partList.getRm2vPart()[k].getParentTitlePath()));
							}
						}
						else
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: Document '"+new Long(docId).toString()+"' has no part list, so no path attribute");
						}
					}

					// Process acls.  If there are forced acls, use those, otherwise get them from Meridio.
					String [] allowAcls;
					String [] denyAcls;

					// forcedAcls will be null if security is off, or nonzero length if security is on but hard-wired
					if (forcedAcls != null && forcedAcls.length == 0)
					{
						ACCESSCONTROL [] documentAcls = documentData.getACCESSCONTROL();
						ArrayList allowAclsArrayList = new ArrayList();
						ArrayList denyAclsArrayList = new ArrayList();
						
						// Allow a broken authority to disable all Meridio documents, even if the document is 'wide open', because
						// Meridio does not permit viewing of the document if the user does not exist (at least, I don't know of a way).
						denyAclsArrayList.add(denyToken);

						if (documentAcls != null)
						{
							for (int j = 0; j < documentAcls.length; j++)
							{
								if (Logging.connectors.isDebugEnabled())
									Logging.connectors.debug(
										"Object Id '" + documentAcls[j].getObjectId() + "' " +
										"Object Type '" + documentAcls[j].getObjectType() + "' " +
										"Permission '" + documentAcls[j].getPermission() + "' " +
										"User Id '" + documentAcls[j].getUserId() + "' " +
										"Group Id '" + documentAcls[j].getGroupId() + "'");

								if (documentAcls[j].getPermission() == 0)  // prohibit permission
								{
									if (documentAcls[j].getGroupId() > 0)
								    {
									denyAclsArrayList.add("G" + documentAcls[j].getGroupId());
								    } else if (documentAcls[j].getUserId() > 0)
								    {
									denyAclsArrayList.add("U" + documentAcls[j].getUserId());
								    }						    
								}
								else                                       // read, amend or manage
								{
									if (documentAcls[j].getGroupId() > 0)
								    {
									allowAclsArrayList.add("G" + documentAcls[j].getGroupId());
								    } else if (documentAcls[j].getUserId() > 0)
								    {
									allowAclsArrayList.add("U" + documentAcls[j].getUserId());
								    }						    						
								}
							}
						}
					
						DOCUMENTS document = documentData.getDOCUMENTS()[0];

						if (Logging.connectors.isDebugEnabled())												
							Logging.connectors.debug("Document id '" + new Long(docId).toString() + "' is owned by owner id '" +
								document.getPROP_ownerId() + "' having the owner name '" + 	
								document.getPROP_ownerName() + "' Record Type is '" +
								document.getPROP_recordType() + "'");
					
						if (document.getPROP_recordType() == 4 ||
							document.getPROP_recordType() == 19)
						{
							RMDataSet rmds = meridio_.getRecord((int)docId, false, false, false);
							Rm2vRecord record = rmds.getRm2vRecord()[0];
				
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Record User Id Owner is '" + record.getOwnerID() +
									"' Record Group Owner Id is '" + record.getGroupOwnerID() + "'");
						
							/*=================================================================
							* Either a group or a user owns a record, cannot be both and the
							* group takes priority if it is set
							*================================================================*/
							if (record.getGroupOwnerID() > 0)
							{
								allowAclsArrayList.add("G" + record.getGroupOwnerID());
							} else if (record.getOwnerID() > 0)
							{
								allowAclsArrayList.add("U" + record.getOwnerID());
							}					
						}
						else
						{
							allowAclsArrayList.add("U" + document.getPROP_ownerId());	
						}

						/*=================================================================
						* Set up the string arrays and then set the ACLs in the
						* repository document
						*================================================================*/
						allowAcls = new String[allowAclsArrayList.size()];
						for (int j = 0; j < allowAclsArrayList.size(); j++)
						{					
							allowAcls[j] = (String) allowAclsArrayList.get(j);
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: Adding '" + allowAcls[j] + "' to allow ACLs");
						}
				
						denyAcls = new String[denyAclsArrayList.size()];
						for (int j = 0; j < denyAclsArrayList.size(); j++)
						{
							denyAcls[j] = (String) denyAclsArrayList.get(j);
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: Adding '" + denyAcls[j] + "' to deny ACLs");
						}
					}
					else
					{
						allowAcls = forcedAcls;
						if (denyAcl != null)
							denyAcls = new String[]{denyAcl};
						else
							denyAcls = null;
					}

					if (allowAcls != null)
						repositoryDocument.setACL(allowAcls);

					if (denyAcls != null)
						repositoryDocument.setDenyACL(denyAcls);							
									
					/*=================================================================
					* Get the object's content, and ingest the document
					*================================================================*/
					try
					{
						AttachmentPart ap = meridio_.getLatestVersionFile((int)docId);
						if (null == ap)
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: Failed to get content for document '" + new Long(docId).toString() + "'");
							// No document.  Delete what's there
							activities.deleteDocument(documentIdentifier);
							i++;
							continue;					            
						}
						try
						{
							// Get the file name.
							String fileName = ap.getDataHandler().getName();
							// Log what we are about to do.
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: File data is supposedly in "+fileName);
							File theTempFile = new File(fileName);
							if (theTempFile.isFile())
							{
								long fileSize = theTempFile.length();			// ap.getSize();
								InputStream is = new FileInputStream(theTempFile);	// ap.getDataHandler().getInputStream();
								try
								{
									repositoryDocument.setBinary(is, fileSize);					
						
									if (null != activities)
									{
										activities.ingestDocument(documentIdentifier, docVersion, 
											fileURL, repositoryDocument);
									}
								}
								finally
								{
									is.close();
								}
							}
							else
							{
								if (Logging.connectors.isDebugEnabled())
									Logging.connectors.debug("Meridio: Expected temporary file was not present - skipping document '"+new Long(docId).toString() + "'");
							}
						}
						finally
						{
							ap.dispose();
							// String fileName = ap.getAttachmentFile();
							// if (fileName != null)
							// {
							//	File tempFile = new File(fileName);
							//	if (tempFile != null)
							//		tempFile.delete();
							// }		
						}
						
					}
					catch (java.net.SocketTimeoutException ioex)
					{
						throw new LCFException("Socket timeout exception: "+ioex.getMessage(), ioex);
					}
					catch (org.apache.commons.httpclient.ConnectTimeoutException ioex)
					{
						throw new LCFException("Connect timeout exception: "+ioex.getMessage(), ioex);
					}
					catch (InterruptedIOException e)
					{
						throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
					}
					catch (org.apache.axis.AxisFault e)
					{
						throw e;
					}
					catch (RemoteException e)
					{
						throw e;
					}
					catch (SOAPException soapEx)
					{
						throw new LCFException("SOAP Exception encountered while retrieving document content: "+soapEx.getMessage(), 
								soapEx);
					}
					catch (IOException ioex)
					{
						throw new LCFException("Input stream failure: "+ioex.getMessage(), ioex);
					}
					i++;								
				}

				Logging.connectors.debug("Meridio: Exiting 'processDocuments' method");
				return;
			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
					}
					throw new LCFException("Unknown http error occurred while processing docs: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
				{
					if (e.getFaultString().indexOf(" 23031#") != -1)
					{
						// This means that the session has expired, so reset it and retry
						meridio_ = null;
						continue;
					}
				}

				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("Meridio: Got an unknown remote exception processing docs - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
				throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
					currentTime + 3 * 60 * 60000L,-1,false);
			}
			catch (RemoteException remoteException)
			{			
				throw new LCFException("Meridio: A remote exception occurred while " +
					"processing a Meridio document: "+remoteException.getMessage(), remoteException);
			}
			catch (MeridioDataSetException meridioDataSetException)
			{
				throw new LCFException("Meridio: A DataSet exception occurred while  " +
					"processing a Meridio document", meridioDataSetException);
			}
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


	private static String [] getMIMETypes
	(
			DocumentSpecification spec
	)
	{
		ArrayList al = new ArrayList ();
		
		for (int i = 0; i < spec.getChildCount(); i++)
		{
			SpecificationNode sn = spec.getChild(i);
			
			if (sn.getType().equals("MIMEType"))
			{												
				al.add(sn.getAttributeValue("type"));
			}
		}
		
		String [] mimeTypes = new String[al.size()];
		Iterator it = al.iterator();
		for (int i = 0; it.hasNext(); i++)
		{
			mimeTypes[i] = (String) it.next();
		}
		
		return mimeTypes;		
	}
	
	
	
	/** Returns all objects from the Meridio repository matching the document specification,
	 * and constrained by the start/end object addition times, and the subset of the total
	 * results to return (startPositionOfHits and maxHitsToReturn) 
	 *  
	 * @see documentSpecificationSearch Specification docSpec,	long startTime,
    		long endTime, int startPositionOfHits, int maxHitsToReturn,
    		int restrictDocumentId
	 */
	private DMSearchResults documentSpecificationSearch
    (
    		DocumentSpecification docSpec,      // The castor representation of the Document Specification
    		long startTime,
    		long endTime,
    		int startPositionOfHits,
    		int maxHitsToReturn
    )
	throws RemoteException, MeridioDataSetException
	{
		return documentSpecificationSearch(docSpec, startTime, endTime,
				startPositionOfHits, maxHitsToReturn, null, null);
	}
	
	
	
	/** Returns objects from the Meridio repository matching the document specification,
	 * and constrained by the start/end object addition times, and the subset of the total
	 * results to return (startPositionOfHits and maxHitsToReturn)
	 * 
	 * @param docSpec					the criteria to determine if an object should be returned
	 * @param startTime                 the date/time after which the object must have been added (inclusive)
	 * @param endTime                   the date/time before which the object must have been added (exclusive)
	 * @param startPositionOfHits       the starting position in the hits to begin returning results from
	 * @param maxHitsToReturn           the maximum number of hits to return
	 * @param restrictDocumentId        if zero, then consider all objects, otherwise if set consider only
	 * 									the indicated document identifier - this is used to check if a
	 * 									give document id subsequently matches the document specification
	 * 									at some point after it was initially returned from the search results
	 *  
	 * @see documentSpecificationSearch Specification docSpec,	long startTime,
    		long endTime, int startPositionOfHits, int maxHitsToReturn,
    		int [] restrictDocumentId
	 */
	private DMSearchResults documentSpecificationSearch
    (
    		DocumentSpecification docSpec,      // The castor representation of the Document Specification
    		long startTime,
    		long endTime,
    		int startPositionOfHits,
    		int maxHitsToReturn,
    		long restrictDocumentId
    )
	throws RemoteException, MeridioDataSetException
	{
		if (restrictDocumentId > 0)
		{
			return documentSpecificationSearch(docSpec, startTime, endTime,
				startPositionOfHits, maxHitsToReturn, new long [] {restrictDocumentId}, null);
		}
		else
		{
			return documentSpecificationSearch(docSpec, startTime, endTime,
					startPositionOfHits, maxHitsToReturn, null, null);
		}
	}
	
	
	
	/** Returns objects from the Meridio repository matching the document specification,
	 * and constrained by the start/end object addition times, and the subset of the total
	 * results to return (startPositionOfHits and maxHitsToReturn)
	 * 
	 *  The search method can return the results in "batches" results, based on the start position
	 *  and maximum hits to return.
	 * 
	 * @param docSpec					the criteria to determine if an object should be returned
	 * @param startTime                 the date/time after which the object must have been added (inclusive)
	 * @param endTime                   the date/time before which the object must have been added (exclusive)
	 * @param startPositionOfHits       the starting position in the hits to begin returning results from
	 * @param maxHitsToReturn           the maximum number of hits to return
	 * @param restrictDocumentId        if the array is empty then return all matching objects, otherwise
	 * 
	 *									Search results are returned in the SEARCHRESULTS_DOCUMENTS DataTable.
	 * 
	 *@throws RemoteException			if an error is encountered call the Meridio web service method(s)
	 *@throws MeridioDataSetException 	if an error is encountered manipulating the Meridio DataSet
	 */
	protected DMSearchResults documentSpecificationSearch
    (
    		DocumentSpecification docSpec,
    		long startTime,
    		long endTime,
    		int startPositionOfHits,
    		int maxHitsToReturn,
    		long [] restrictDocumentId,
		ReturnMetadata[] returnMetadata
	)
	throws RemoteException, MeridioDataSetException
	{
		try
		{
			Logging.connectors.debug("Entering documentSpecificationSearch");						
			
			int currentSearchTerm = 1;
			DMDataSet dsSearchCriteria = new DMDataSet();
													
			/*====================================================================
			* Exclude things marked for delete
			*===================================================================*/
			PROPERTY_TERMS drDeleteSearch = new PROPERTY_TERMS();
			drDeleteSearch.setId(currentSearchTerm++);				
			drDeleteSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
			drDeleteSearch.setPropertyName("PROP_markedForDelete");
			drDeleteSearch.setCategoryId(4);                               //Global Standard/Fixed Property
			drDeleteSearch.setNum_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
			drDeleteSearch.setNum_value(0);
			drDeleteSearch.setParentId(1);
			drDeleteSearch.setIsVersionProperty(false);
			dsSearchCriteria.addPROPERTY_TERMS(drDeleteSearch);
			
			/*====================================================================
			* Restrict based on start & end date/time, if necessssary
			*===================================================================*/
			if (startTime > 0L)
			{				
				Logging.connectors.debug("Start Date/time is <" + new Date(startTime) + "> in ms <" + startTime + ">" +
				      " End Date/time is <" + new Date(endTime) + "> in ms <" + endTime + ">");				
				
				PROPERTY_TERMS drDateStart = new PROPERTY_TERMS();
				drDateStart.setId(currentSearchTerm++);				
				drDateStart.setTermType(new Short("2").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDateStart.setPropertyName("PROP_lastModifiedDate");
				drDateStart.setCategoryId(4);                                 //Global Standard/Fixed Property
				drDateStart.setDate_relation(new Short("11").shortValue());   //dtONORAFTER
				drDateStart.setDate_value(new Date(startTime));
				drDateStart.setParentId(1);
				drDateStart.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDateStart);
				
				PROPERTY_TERMS drDateEnd = new PROPERTY_TERMS();
				drDateEnd.setId(currentSearchTerm++);				
				drDateEnd.setTermType(new Short("2").shortValue());        //0=STRING, 1=NUMBER, 2=DATE
				drDateEnd.setPropertyName("PROP_lastModifiedDate");
				drDateEnd.setCategoryId(4);                                //Global Standard/Fixed Property
				drDateEnd.setDate_relation(new Short("8").shortValue());  //dtBEFORE
				drDateEnd.setDate_value(new Date(endTime));
				drDateEnd.setParentId(1);
				drDateEnd.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDateEnd);			
			}
						
			/*====================================================================
			* Just add a dummy term to make the conditional logic easier; i.e.
			* always add an "AND" - the dummy term is required in case there are
			* no other search criteria - i.e. we could be searching the whole
			* Meridio repository
			* 
			* Search for document id's which are > 0 - this will always be true
			*===================================================================*/
			PROPERTY_TERMS drDocIdSearch = new PROPERTY_TERMS();
			drDocIdSearch.setId(currentSearchTerm++);				
			drDocIdSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
			drDocIdSearch.setPropertyName("PROP_documentId");
			drDocIdSearch.setCategoryId(4);                               //Global Standard/Fixed Property
			drDocIdSearch.setNum_relation(new Short("3").shortValue());   //dmNumRelation.GREATER
			drDocIdSearch.setNum_value(0);
			drDocIdSearch.setParentId(1);
			drDocIdSearch.setIsVersionProperty(false);
			dsSearchCriteria.addPROPERTY_TERMS(drDocIdSearch);
				
			if (restrictDocumentId != null && restrictDocumentId.length == 1)
			{
				/*====================================================================
				* Restrict the search query to just the 1 document ID passed in
				*===================================================================*/
				PROPERTY_TERMS drDocIdSearchRestricted = new PROPERTY_TERMS();
				drDocIdSearchRestricted.setId(currentSearchTerm++);				
				drDocIdSearchRestricted.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocIdSearchRestricted.setPropertyName("PROP_documentId");
				drDocIdSearchRestricted.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocIdSearchRestricted.setNum_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
				drDocIdSearchRestricted.setNum_value(restrictDocumentId[0]);            //Search for the specific doc ID
				drDocIdSearchRestricted.setParentId(1);
				drDocIdSearchRestricted.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocIdSearchRestricted);				
			}
			else if (restrictDocumentId != null && restrictDocumentId.length > 1)
			{								
				/*====================================================================
				* Multiple document id's have been passed in, so we need to "or"
				* them together
				*===================================================================*/
				for (int i = 0; i < restrictDocumentId.length; i++)
				{
					PROPERTY_TERMS drDocIdSearchRestricted = new PROPERTY_TERMS();
					drDocIdSearchRestricted.setId(currentSearchTerm++);				
					drDocIdSearchRestricted.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
					drDocIdSearchRestricted.setPropertyName("PROP_documentId");
					drDocIdSearchRestricted.setCategoryId(4);                               //Global Standard/Fixed Property
					drDocIdSearchRestricted.setNum_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
					drDocIdSearchRestricted.setNum_value(restrictDocumentId[i]);            //Search for the specific doc ID
					drDocIdSearchRestricted.setParentId(4);
					drDocIdSearchRestricted.setIsVersionProperty(false);
					dsSearchCriteria.addPROPERTY_TERMS(drDocIdSearchRestricted);
				}
											
				PROPERTY_OPS drMIMETypeOps = new PROPERTY_OPS();
				drMIMETypeOps.setId(4);	
				drMIMETypeOps.setParentId(1);	
				drMIMETypeOps.setOperator(new Short("1").shortValue());    // OR
				dsSearchCriteria.addPROPERTY_OPS(drMIMETypeOps);														
			}
																
			PROPERTY_OPS drPropertyOps = new PROPERTY_OPS();
			drPropertyOps.setId(1);				
			drPropertyOps.setOperator(new Short("0").shortValue());   //AND
			dsSearchCriteria.addPROPERTY_OPS(drPropertyOps);
						
			/*====================================================================
			* Filter on documents, records, or documents and records
			* 
			* The "SearchDocuments" method returns both documents and records; to
			* return just documents, get things where the recordType is not
			* 0, 4 or 19 (refer to Meridio Documentation)
			*===================================================================*/
			String searchOn = null;
			for (int i = 0; i < docSpec.getChildCount(); i++)
			{
				SpecificationNode sn = docSpec.getChild(i);
				
				if (sn.getType().equals("SearchOn"))
				{
					searchOn = sn.getAttributeValue("value"); 
				}
			}
			
			if (searchOn != null && searchOn.equals("DOCUMENTS_ONLY"))
			{
				PROPERTY_TERMS drDocsOrRecsSearch = new PROPERTY_TERMS();
				drDocsOrRecsSearch.setId(currentSearchTerm++);									
				drDocsOrRecsSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocsOrRecsSearch.setPropertyName("PROP_recordType");
				drDocsOrRecsSearch.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocsOrRecsSearch.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
				drDocsOrRecsSearch.setNum_value(0);
				drDocsOrRecsSearch.setParentId(1);
				drDocsOrRecsSearch.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch);
				
				PROPERTY_TERMS drDocsOrRecsSearch2 = new PROPERTY_TERMS();
				drDocsOrRecsSearch2.setId(currentSearchTerm++);									
				drDocsOrRecsSearch2.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocsOrRecsSearch2.setPropertyName("PROP_recordType");
				drDocsOrRecsSearch2.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocsOrRecsSearch2.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
				drDocsOrRecsSearch2.setNum_value(4);
				drDocsOrRecsSearch2.setParentId(1);
				drDocsOrRecsSearch2.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch2);
				
				PROPERTY_TERMS drDocsOrRecsSearch3 = new PROPERTY_TERMS();
				drDocsOrRecsSearch3.setId(currentSearchTerm++);									
				drDocsOrRecsSearch3.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocsOrRecsSearch3.setPropertyName("PROP_recordType");
				drDocsOrRecsSearch3.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocsOrRecsSearch3.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
				drDocsOrRecsSearch3.setNum_value(19);
				drDocsOrRecsSearch3.setParentId(1);
				drDocsOrRecsSearch3.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch3);
			}
			
			/*====================================================================
			* Filter on documents, records, or documents and records
			* 
			* The "SearchDocuments" method returns both documents and records; to
			* return just records, get things where the recordType is 4 or greater
			*===================================================================*/	
			if (searchOn != null && searchOn.equals("RECORDS_ONLY"))
			{
				PROPERTY_TERMS drDocsOrRecsSearch = new PROPERTY_TERMS();
				drDocsOrRecsSearch.setId(currentSearchTerm++);									
				drDocsOrRecsSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocsOrRecsSearch.setPropertyName("PROP_recordType");
				drDocsOrRecsSearch.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocsOrRecsSearch.setNum_relation(new Short("5").shortValue());   //dmNumberRelation.GREATEROREQUAL=5
				drDocsOrRecsSearch.setNum_value(4);
				drDocsOrRecsSearch.setParentId(1);
				drDocsOrRecsSearch.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch);
			}							
			
			/*====================================================================
			* Filter on class or folder (if any)
			*===================================================================*/			
			for (int i = 0; i < docSpec.getChildCount(); i++)
			{										
				SpecificationNode sn = docSpec.getChild(i);
								
				if (sn.getType().equals("SearchPath"))
				{													
					String searchPath   = sn.getAttributeValue("path");					
					int searchContainer = meridio_.findClassOrFolder(searchPath);
					
					if (searchContainer > 0)
					{				
						SEARCH_CONTAINERS drSearchContainers = new SEARCH_CONTAINERS();
						drSearchContainers.setContainerId(searchContainer);					
						dsSearchCriteria.addSEARCH_CONTAINERS(drSearchContainers);
						
						Logging.connectors.debug("Found path [" +  searchPath + "] id: [" + 
								searchContainer + "]");
					}
					else if (searchContainer == 0)
					{
						Logging.connectors.debug("Meridio: Found FilePlan root, so not including in search criteria!");
					}
					else
					{
						/*====================================================================
						* We can't find the path, so ignore it.
						* 
						* This is potentially opening up the search scope, i.e. if there was
						* one path which was being searched and then the Meridio FilePlan is 
						* re-organised and the path no longer exists (but the original content
						* has just been moved in the tree) then this could cause all the
						* Meridio content to be returned
						*===================================================================*/
						Logging.connectors.warn("Meridio: Did not find FilePlan path [" +  searchPath + "]. " + 
								  "The path is therefore *not* being used to restrict the search scope");
					}
				}
			}
			
			/*====================================================================
			* Filter on category (if any)
			*===================================================================*/			
			CATEGORIES [] meridioCategories = meridio_.getCategories().getCATEGORIES();
			// Create a map from title to category ID
			HashMap categoryMap = new HashMap();
			int i = 0;
			while (i < meridioCategories.length)
			{
				String title = meridioCategories[i].getPROP_title();
				long categoryID = meridioCategories[i].getPROP_categoryId();
				categoryMap.put(title,new Long(categoryID));
				i++;
			}
			
			ArrayList categoriesToAdd = new ArrayList ();

			for (i = 0; i < docSpec.getChildCount(); i++)
			{				
				SpecificationNode sn = docSpec.getChild(i);
				
				if (sn.getType().equals("SearchCategory"))
				{													
					String searchCategory   = sn.getAttributeValue("category");	
					Long categoryIDObject = (Long)categoryMap.get(searchCategory);
					if (categoryIDObject != null)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Category [" + searchCategory + "] match, ID=[" + categoryIDObject + "]");						
						categoriesToAdd.add(categoryIDObject);
					}
					else
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: No match found for Category [" + searchCategory + "]"); 
					}
				}
			}
			
			for (i = 0; i < categoriesToAdd.size(); i++)
			{
				PROPERTY_TERMS drDocsOrRecsSearch = new PROPERTY_TERMS();
				drDocsOrRecsSearch.setId(currentSearchTerm++);									
				drDocsOrRecsSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drDocsOrRecsSearch.setPropertyName("PROP_categoryId");
				drDocsOrRecsSearch.setCategoryId(4);                               //Global Standard/Fixed Property
				drDocsOrRecsSearch.setNum_relation(new Short("0").shortValue());   //dmNumberRelation.GREATEROREQUAL=5
				drDocsOrRecsSearch.setNum_value(((Long) categoriesToAdd.get(i)).longValue());
				if (categoriesToAdd.size() == 1)  // If there is one term, we can use the AND clause
				{
					drDocsOrRecsSearch.setParentId(1);
				}
				else                      // Otherwise, need to have an OR subclause
				{
					drDocsOrRecsSearch.setParentId(2);
				}
				drDocsOrRecsSearch.setIsVersionProperty(false);
				dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch);				
			}						
			
			/*====================================================================
			* Filter on MIME Type (if any are in the Document Specification)
			*===================================================================*/
			String [] mimeTypes = getMIMETypes(docSpec);			
			for (i = 0; i < mimeTypes.length; i++)
			{					
				PROPERTY_TERMS drMIMETypesSearch = new PROPERTY_TERMS();
				drMIMETypesSearch.setId(currentSearchTerm++);									
				drMIMETypesSearch.setTermType(new Short("0").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
				drMIMETypesSearch.setPropertyName("PROP_W_mimeType");
				drMIMETypesSearch.setCategoryId(4);                               //Global Standard/Fixed Property
				drMIMETypesSearch.setStr_relation(new Short("0").shortValue());   //dmNumberRelation.GREATEROREQUAL=5
				drMIMETypesSearch.setStr_value(mimeTypes[i]);
				if (mimeTypes.length == 1)  // If there is one term, we can use the AND clause
				{
					drMIMETypesSearch.setParentId(1);
				}
				else                      // Otherwise, need to have an OR subclause
				{
					drMIMETypesSearch.setParentId(3);
				}
				drMIMETypesSearch.setIsVersionProperty(true);
				dsSearchCriteria.addPROPERTY_TERMS(drMIMETypesSearch);				
			}
						
			if (categoriesToAdd.size() > 1)
			{
				PROPERTY_OPS drCategoryOps = new PROPERTY_OPS();
				drCategoryOps.setId(2);	
				drCategoryOps.setParentId(1);	
				drCategoryOps.setOperator(new Short("1").shortValue());    // OR
				dsSearchCriteria.addPROPERTY_OPS(drCategoryOps);							
			}		
			if (mimeTypes.length > 1)
			{
				PROPERTY_OPS drMIMETypeOps = new PROPERTY_OPS();
				drMIMETypeOps.setId(3);	
				drMIMETypeOps.setParentId(1);	
				drMIMETypeOps.setOperator(new Short("1").shortValue());    // OR
				dsSearchCriteria.addPROPERTY_OPS(drMIMETypeOps);							
			}
								
			/*====================================================================
			* Define what is being returned: include the properties that are
			* present within the document specification
			*===================================================================*/		
			int returnResultsAdded = 0;
			if (returnMetadata != null && returnMetadata.length > 0)			
			{																			
				PROPERTYDEFS [] propertyDefs = meridio_.getStaticData().getPROPERTYDEFS();

				// Build a hash table containing standard and custom properties
				HashMap propertyMap = new HashMap();
				HashMap customMap = new HashMap();
				i = 0;
				while (i < propertyDefs.length)
				{
					PROPERTYDEFS def = propertyDefs[i++];
					if (def.getTableName().equals("DOCUMENTS"))
					{
						propertyMap.put(def.getDisplayName(),def.getColumnName());
					}
					else if (def.getTableName().equals("DOCUMENT_CUSTOMPROPS"))
					{
						Long categoryID = new Long(def.getCategoryId());
						HashMap dataMap = (HashMap)customMap.get(categoryID);
						if (dataMap == null)
						{
							dataMap = new HashMap();
							customMap.put(categoryID,dataMap);
						}
						dataMap.put(def.getDisplayName(),def.getColumnName());
					}
				}

				for (i = 0; i < returnMetadata.length; i++)
				{
					long categoryMatch = 0;
					boolean isCategoryMatch    = false;

					RESULTDEFS drResultDefs = new RESULTDEFS();
					drResultDefs.setIsVersionProperty(false);
					
					if (returnMetadata[i].getCategoryName() == null ||
						returnMetadata[i].getCategoryName().length() == 0)
					{
						isCategoryMatch = true;
						categoryMatch   = 4;						
					}
					else
					{
						Long categoryIDObject = (Long)categoryMap.get(returnMetadata[i].getCategoryName());
						if (categoryIDObject != null)
						{
							isCategoryMatch = true;
							categoryMatch = categoryIDObject.longValue();
						}
					}
					
					if (!isCategoryMatch)
					{
						if (Logging.connectors.isDebugEnabled())
							Logging.connectors.debug("Meridio: Category '" + returnMetadata[i].getCategoryName() + "' no match found for search results criteria!");
						continue;
					}
					else
					{						
						
						/*====================================================================
						* Find the matching property name for the display name (as it is the
						* property column name that is required by the search)
						*===================================================================*/

						String columnName = (String)propertyMap.get(returnMetadata[i].getPropertyName());
						if (columnName == null)
						{
							HashMap categoryMatchMap = (HashMap)customMap.get(new Long(categoryMatch));
							if (categoryMatchMap != null)
							{
								columnName = (String)categoryMatchMap.get(returnMetadata[i].getPropertyName());
							}
						}

						if (columnName != null)
							drResultDefs.setPropertyName(columnName);
						else
						{
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("Meridio: No property match found for '" + returnMetadata[i].getPropertyName() + "'");
							continue;
						}

						drResultDefs.setCategoryId(categoryMatch);
						dsSearchCriteria.addRESULTDEFS(drResultDefs);
						returnResultsAdded++;						
					}	
				}
			}
						
			/*====================================================================
			* We always need to return something in the search results, so add
			* the last modified date if nothing else was provided
			*===================================================================*/	
			if (returnResultsAdded == 0)
			{
				RESULTDEFS drResultDefs = new RESULTDEFS();
				drResultDefs.setPropertyName("PROP_lastModifiedDate");
				drResultDefs.setIsVersionProperty(false);
				drResultDefs.setCategoryId(4);			
				dsSearchCriteria.addRESULTDEFS(drResultDefs);
			}
						
			/*====================================================================
			* Call the search method			
			*===================================================================*/			 		
			DMSearchResults searchResults = meridio_.searchDocuments(dsSearchCriteria, 
				maxHitsToReturn, startPositionOfHits, DmPermission.READ, false,
				DmSearchScope.BOTH, false, true, false, DmLogicalOp.AND);					
									
			return searchResults;					
		}
		finally
		{
			Logging.connectors.debug("Exiting documentSpecificationSearch method.");
		}
	}


	
	private static class ReturnMetadata
	{
		protected String categoryName_;
		protected String propertyName_;
		
		public ReturnMetadata 
		(
				String categoryName,
				String propertyName
		)
		{
			categoryName_ = categoryName;
			propertyName_ = propertyName;
		}
		
		public String getCategoryName ()
		{
			return categoryName_;
		}
		
		public String getPropertyName ()
		{
			return propertyName_;
		}

	}
	
	
	private final static int maxHitsToReturn      = 100;

	/** Document identifier stream.
	*/
	protected class IdentifierStream implements IDocumentIdentifierStream
	{	
		protected DMSearchResults searchResults  = null;		
		protected int currentResult              = 0;
		protected int numResultsReturnedByStream = 0;
				
		DocumentSpecification spec_              = null;
		long startTime_                          = 0L;
		long endTime_                            = 0L;
		
		
		public IdentifierStream
		(
				DocumentSpecification spec,
				long startTime,
				long endTime
		)
		throws LCFException,ServiceInterruption
		{			
			Logging.connectors.debug("Meridio: Entering 'IdentifierStream' constructor");
			while (true)
			{
				getSession();
		
				try
				{
					spec_             = spec;
					startTime_        = startTime;
					endTime_          = endTime;
					
					searchResults = documentSpecificationSearch(spec, 
							startTime, endTime,	1, maxHitsToReturn);
					
					Logging.connectors.debug("Meridio: Exiting 'IdentifierStream' constructor");

					return;
				}
				catch (org.apache.axis.AxisFault e)
				{
					long currentTime = System.currentTimeMillis();
					if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
					{
						org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
						if (elem != null)
						{
							elem.normalize();
							String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
							throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
						}
						throw new LCFException("Unknown http error occurred while performing search: "+e.getMessage(),e);
					}
					if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
					{
						String exceptionName = e.getFaultString();
						if (exceptionName.equals("java.lang.InterruptedException"))
							throw new LCFException("Interrupted",LCFException.INTERRUPTED);
					}
					if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
					{
						if (e.getFaultString().indexOf(" 23031#") != -1)
						{
							// This means that the session has expired, so reset it and retry
							meridio_ = null;
							continue;
						}
					}
					if (Logging.connectors.isDebugEnabled())
						Logging.connectors.debug("Meridio: Got an unknown remote exception while performing search - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
					throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
						currentTime + 3 * 60 * 60000L,-1,false);
				}
				catch (RemoteException remoteException)
				{			
					throw new LCFException("Meridio: A Remote Exception occurred while " +
						"performing a search: "+remoteException.getMessage(), remoteException);
				}
				catch (MeridioDataSetException meridioDataSetException)
				{						
					throw new LCFException("Meridio: A problem occurred manipulating the Web " +
						"Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
				}
			}
		}

		
		
		/** Get the next identifier.
		*@return the next document identifier, or null if there are no more.
		*/
		public String getNextIdentifier()
			throws LCFException, ServiceInterruption
		{		
			Logging.connectors.debug("Meridio: Entering 'getNextIdentifier' method");
			
			try
			{
				if (null                       == searchResults ||
					numResultsReturnedByStream == searchResults.totalHitsCount)
				{
					return null;
				}			
				
				if (currentResult == searchResults.returnedHitsCount)
				{
					while (true)
					{
						getSession();
						try
						{						
							searchResults = documentSpecificationSearch(spec_, 
								startTime_, endTime_, numResultsReturnedByStream + 1, 
								maxHitsToReturn);
							
							currentResult = 0;
							break;
						}
						catch (org.apache.axis.AxisFault e)
						{
							long currentTime = System.currentTimeMillis();
							if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
							{
								org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
								if (elem != null)
								{
									elem.normalize();
									String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
									throw new LCFException("Unexpected http error code "+httpErrorCode+" performing search: "+e.getMessage());
								}
								throw new LCFException("Unknown http error occurred while performing search: "+e.getMessage(),e);
							}
							if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
							{
								String exceptionName = e.getFaultString();
								if (exceptionName.equals("java.lang.InterruptedException"))
									throw new LCFException("Interrupted",LCFException.INTERRUPTED);
							}
							if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
							{
								if (e.getFaultString().indexOf(" 23031#") != -1)
								{
									// This means that the session has expired, so reset it and retry
									meridio_ = null;
									continue;
								}
							}

							throw new LCFException("Meridio: Got an unknown remote exception performing search - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
						}
						catch (RemoteException remoteException)
						{						
							throw new ServiceInterruption("Meridio: A Remote Exception occurred while " +
											     "performing a Meridio search: "+remoteException.getMessage(), remoteException,
											     System.currentTimeMillis() + interruptionRetryTime,
											     -1L, -1, true);					
						}
						catch (MeridioDataSetException meridioDataSetException)
						{						
							throw new LCFException("Meridio: A problem occurred manipulating the Web " +
						     "Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
						}
					}
				}
	
				long documentId = 
					searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS()[currentResult].getDocId();
											
				String strDocumentId = new Long(documentId).toString();
				
				currentResult++;
				numResultsReturnedByStream++;
				
				return strDocumentId;		
			}
			finally
			{
				Logging.connectors.debug("Meridio: Exiting 'getNextIdentifier' method");
			}
		}

		
		
		/** Close the stream.
		*/
		public void close()
			throws LCFException
		{
			Logging.connectors.debug("Meridio: Entering 'IdentifierStream.close' method");
			
			searchResults              = null;
			currentResult              = 0;
			numResultsReturnedByStream = 0;
			
			Logging.connectors.debug("Meridio: Exiting 'IdentifierStream.close' method");
		}
	}
	
	
	
	/** Returns the categories set up in the Meridio system; these are used by the UI for two
	 * purposes
	 * 
	 * 		1)	To populate the "SearchCategory"
	 * 				Use "getPROP_title()" on the list of CATEGORIES object in
	 * 				the return ArrayList
	 * 		2)  To assist with population of the metadata values to return. The
	 * 			available metadata depends on the chosen category
	 * 
	*@return Sorted array of strings containing the category names
	*/
	public String [] getMeridioCategories ()
		throws LCFException, ServiceInterruption
	{
		Logging.connectors.debug("Entering 'getMeridioCategories' method");
		
		while (true)
		{
			getSession();
			ArrayList returnCategories = new ArrayList();
					
			try
			{				
				CATEGORIES [] categories = meridio_.getCategories().getCATEGORIES();
				for (int i = 0; i < categories.length; i++)
				{
					if (categories[i].getPROP_categoryId() == 4 ||   // Global Document Category
						categories[i].getPROP_categoryId() == 5 ||   // Mail Message
						categories[i].getPROP_categoryId() > 100)    // Custom Document Category
					{
						if (!categories[i].getPROP_title().equals("<None>"))
						{
							Logging.connectors.debug("Adding category <" + 
										categories[i].getPROP_title() + ">");
							returnCategories.add(categories[i].getPROP_title());
						}					
					}					
				}
				
				String [] returnStringArray = new String[returnCategories.size()];
				Iterator it = returnCategories.iterator();
				for (int i = 0; it.hasNext(); i++)
				{
					returnStringArray[i] = (String) it.next();
				}
				
				java.util.Arrays.sort(returnStringArray);
				
				Logging.connectors.debug("Exiting 'getMeridioCategories' method");

				return returnStringArray;
			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" getting categories: "+e.getMessage());
					}
					throw new LCFException("Unknown http error occurred while getting categories: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
				{
					if (e.getFaultString().indexOf(" 23031#") != -1)
					{
						// This means that the session has expired, so reset it and retry
						meridio_ = null;
						continue;
					}
				}

				throw new LCFException("Meridio: Got an unknown remote exception getting categories - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
			}
			catch (RemoteException remoteException)
			{			
				throw new LCFException("Meridio: A Remote Exception occurred while " +
								     "retrieving the Meridio categories: "+remoteException.getMessage(), remoteException);
			}
			catch (MeridioDataSetException meridioDataSetException)
			{			
				throw new LCFException("Meridio: DataSet Exception occurred retrieving the Meridio categories: "+meridioDataSetException.getMessage(), 
						meridioDataSetException);			
			}
		}
	}
	
	
	
	public String [] getMeridioDocumentProperties ()
		throws LCFException, ServiceInterruption
	{
		Logging.connectors.debug("Entering 'getMeridioDocumentProperties' method");
		
		while (true)
		{
			getSession();
			ArrayList meridioDocumentProperties = new ArrayList();
					
			try
			{										
				CATEGORIES [] categories = meridio_.getCategories().getCATEGORIES();						
				PROPERTYDEFS [] propertyDefs = meridio_.getStaticData().getPROPERTYDEFS();
				
				for (int i = 0; i < propertyDefs.length; i++)
				{
					if (propertyDefs[i].getTableName() == null)
					{
						continue;
					}								
					
					if (propertyDefs[i].getTableName().compareTo("DOCUMENTS") == 0)
					{
						meridioDocumentProperties.add(propertyDefs[i].getDisplayName());
					}								
												
					if (   (propertyDefs[i].getCategoryId() == 4 ||   // Global Document Category
							propertyDefs[i].getCategoryId()  == 5 ||   // Mail Message
							propertyDefs[i].getCategoryId() > 100) &&  // Custom Category
							propertyDefs[i].getTableName().compareTo("DOCUMENT_CUSTOMPROPS") == 0)
					{										
						for (int j = 0; j < categories.length; j++)
						{
							if (categories[j].getPROP_categoryId() == propertyDefs[i].getCategoryId())
							{							
								meridioDocumentProperties.add(categories[j].getPROP_title() + "." +
												 propertyDefs[i].getDisplayName());
								
								Logging.connectors.debug("Prop: <" + 
									 categories[j].getPROP_title() + "." +
										 propertyDefs[i].getDisplayName() + "> Column <" +
										 propertyDefs[i].getColumnName() + ">");	
								
								break;
							} 							
						}							
					}							
				}
				
				String [] returnStringArray = new String[meridioDocumentProperties.size()];
				Iterator it = meridioDocumentProperties.iterator();
				for (int i = 0; it.hasNext(); i++)
				{
					returnStringArray[i] = (String) it.next();
				}
				
				java.util.Arrays.sort(returnStringArray);
				Logging.connectors.debug("Exiting 'getMeridioDocumentProperties' method");

				return returnStringArray;
			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" getting document properties: "+e.getMessage());
					}
					throw new LCFException("Unknown http error occurred while getting document properties: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
				{
					if (e.getFaultString().indexOf(" 23031#") != -1)
					{
						// This means that the session has expired, so reset it and retry
						meridio_ = null;
						continue;
					}
				}

				throw new LCFException("Meridio: Got an unknown remote exception getting document properties - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
			}
			catch (RemoteException remoteException)
			{			
				throw new LCFException("Meridio: A Remote Exception occurred while " +
								     "retrieving the Meridio document properties: "+remoteException.getMessage(), remoteException);
			}
			catch (MeridioDataSetException meridioDataSetException)
			{			
				throw new LCFException("Meridio: DataSet Exception occurred retrieving the Meridio document properties: "+meridioDataSetException.getMessage(), 
						meridioDataSetException);			
			}
		}
	}
	
	
	
	public MeridioClassContents [] getClassOrFolderContents
	(
		int classOrFolderId
	)
	throws LCFException, ServiceInterruption
	{
		Logging.connectors.debug("Entering 'getClassOrFolderContents' method");
		
		while (true)
		{
			getSession();
			ArrayList meridioContainers = new ArrayList();
					
			try
			{				
				RMDataSet ds = meridio_.getClassContents(classOrFolderId, false, false, false);
				if (ds == null)
				{
					Logging.connectors.debug("No classes or folders in returned DataSet");
					return new MeridioClassContents [] {};
				}
				
				Rm2vClass [] classes  = ds.getRm2vClass();
				Rm2vFolder [] folders = ds.getRm2vFolder();
				
				for (int i = 0; i < classes.length; i++)
				{
					if (classes[i].getHomePage() == null ||
						classes[i].getHomePage().length() == 0) // Not a federated link
					{
						MeridioClassContents classContents = new MeridioClassContents();
						
						classContents.containerType = MeridioClassContents.CLASS;
						classContents.classOrFolderId = classes[i].getId();
						classContents.classOrFolderName = classes[i].getName();
						
						meridioContainers.add(classContents);
					}				
				}
				
				for (int i = 0; i < folders.length; i++)
				{
					MeridioClassContents classContents = new MeridioClassContents();
					
					classContents.containerType = MeridioClassContents.FOLDER;
					classContents.classOrFolderId = folders[i].getId();
					classContents.classOrFolderName = folders[i].getName();
					
					meridioContainers.add(classContents);				
				}
				
				MeridioClassContents [] classArray = new MeridioClassContents[meridioContainers.size()];
				Iterator it = meridioContainers.iterator();
				for (int i = 0; it.hasNext(); i++)
				{
					classArray[i] = (MeridioClassContents) it.next();
				}
				Logging.connectors.debug("Exiting 'getClassOrFolderContents' method");

				return classArray;
			}
			catch (org.apache.axis.AxisFault e)
			{
				long currentTime = System.currentTimeMillis();
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
				{
					org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
					if (elem != null)
					{
						elem.normalize();
						String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
						throw new LCFException("Unexpected http error code "+httpErrorCode+" getting class or folder contents: "+e.getMessage());
					}
					throw new LCFException("Unknown http error occurred while getting class or folder contents: "+e.getMessage(),e);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
				{
					String exceptionName = e.getFaultString();
					if (exceptionName.equals("java.lang.InterruptedException"))
						throw new LCFException("Interrupted",LCFException.INTERRUPTED);
				}
				if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
				{
					if (e.getFaultString().indexOf(" 23031#") != -1)
					{
						// This means that the session has expired, so reset it and retry
						meridio_ = null;
						continue;
					}
				}

				throw new LCFException("Meridio: Got an unknown remote exception getting class or folder contents - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
			}
			catch (RemoteException remoteException)
			{			
				throw new LCFException("Meridio: A Remote Exception occurred while " +
								     "retrieving class or folder contents: "+remoteException.getMessage(), remoteException);
			}
			catch (MeridioDataSetException meridioDataSetException)
			{						
				throw new LCFException("Meridio: A problem occurred manipulating the Web " +
			     "Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
			}
		}
	}
	
	
	/** Helper class for keeping track of metadata index for each document */
	protected static class MutableInteger
	{
		int value = 0;

		public MutableInteger()
		{
		}

		public int getValue()
		{
			return value;
		}

		public void increment()
		{
			value++;
		}
	}
	
}
	
	
	
