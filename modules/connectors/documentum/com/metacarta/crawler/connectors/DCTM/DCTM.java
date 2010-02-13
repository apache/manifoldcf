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
package com.metacarta.crawler.connectors.DCTM;

import org.apache.log4j.*;
import com.metacarta.core.interfaces.*;
import com.metacarta.agents.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import com.metacarta.crawler.system.Logging;
import java.util.*;
import java.io.*;
import com.metacarta.license.LicenseFile;
import com.metacarta.crawler.common.DCTM.*;
import java.rmi.*;

public class DCTM extends com.metacarta.crawler.connectors.BaseRepositoryConnector
{
	public static final String _rcsid = "@(#)$Id$";

	public static String CONFIG_PARAM_DOCBASE = "docbasename";
	public static String CONFIG_PARAM_USERNAME = "docbaseusername";
	public static String CONFIG_PARAM_PASSWORD = "docbasepassword";
	public static String CONFIG_PARAM_WEBTOPBASEURL = "webtopbaseurl";
	public static String CONFIG_PARAM_DOMAIN = "domain";

	public static String CONFIG_PARAM_LOCATION = "docbaselocation";
	public static String CONFIG_PARAM_OBJECTTYPE = "objecttype";
	public static String CONFIG_PARAM_ATTRIBUTENAME = "attrname";
	public static String CONFIG_PARAM_MAXLENGTH = "maxdoclength";
	public static String CONFIG_PARAM_FORMAT = "mimetype";
	public static String CONFIG_PARAM_PATHNAMEATTRIBUTE = "pathnameattribute";
	public static String CONFIG_PARAM_PATHMAP = "pathmap";

	// Activities we log
	public final static String ACTIVITY_FETCH = "fetch";

	protected String docbaseName = null;
	protected String userName = null;
	protected String password = null;
	protected String domain = null;
	protected String webtopBaseURL = null;

	protected IDocumentum session = null;
	protected long lastSessionFetch = -1L;

	protected static final long timeToRelease = 300000L;

	/** Deny access token for default authority */
	private final static String defaultAuthorityDenyToken = "McAdAuthority_MC_DEAD_AUTHORITY";

	// Documentum has no "deny" tokens, and its document acls cannot be empty, so no local authority deny token is required.
	// However, it is felt that we need to be suspenders-and-belt, so here is the deny token.
	// The documentum tokens are of the form xxx:yyy, so they cannot collide with the standard deny token.
	private static final String denyToken = "MC_DEAD_AUTHORITY";

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
				// Create a session
				IDocumentumFactory df = (IDocumentumFactory)Naming.lookup("rmi://127.0.0.1:8300/documentum_factory");
				IDocumentum newSession = df.make();
				newSession.createSession(docbaseName,userName,password,domain);
				session = newSession;
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

	/** Get a DFC session.  This will be done every time it is needed.
	*/
	protected void getSession()
		throws MetacartaException, ServiceInterruption
	{
		if (session == null)
		{
			// License check at the point we actually establish connection to the server
			DCTMLicense license = DCTMLicense.getInstance();
			LicenseFile.Error license_error = license.verify();
			if (! license.verify().equals(LicenseFile.Error.E_NOERROR)) {
			    throw new MetacartaException("License error.  Contact MetaCarta customer service. (" + license_error.toString() + ")");
			}

			// Perform basic parameter checking, and debug output.
			if (docbaseName == null || docbaseName.length() < 1)
				throw new MetacartaException("Parameter "+CONFIG_PARAM_DOCBASE+" required but not set");

			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("DCTM: Docbase = '" + docbaseName + "'");

			if (userName == null || userName.length() < 1)
				throw new MetacartaException("Parameter "+CONFIG_PARAM_USERNAME+" required but not set");

			if (Logging.connectors.isDebugEnabled())
				Logging.connectors.debug("DCTM: Username = '" + userName + "'");

			if (password == null || password.length() < 1)
				throw new MetacartaException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

			Logging.connectors.debug("DCTM: Password exists");

			if (webtopBaseURL == null || webtopBaseURL.length() < 1)
				throw new MetacartaException("Required parameter "+CONFIG_PARAM_WEBTOPBASEURL+" missing");

			if (domain == null)
				// Empty domain is allowed
				Logging.connectors.debug("DCTM: No domain");
			else
				Logging.connectors.debug("DCTM: Domain = '" + domain + "'");

			long currentTime;
			GetSessionThread t = new GetSessionThread();
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof java.net.MalformedURLException)
						throw (java.net.MalformedURLException)thr;
					else if (thr instanceof NotBoundException)
						throw (NotBoundException)thr;
					else if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
					else
						throw (Error)thr;
				}
			}
			catch (InterruptedException e)
			{
				t.interrupt();
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (java.net.MalformedURLException e)
			{
				throw new MetacartaException(e.getMessage(),e);
			}
			catch (NotBoundException e)
			{
				// Transient problem: Server not available at the moment.
				Logging.connectors.warn("DCTM: RMI server not up at the moment: "+e.getMessage(),e);
				currentTime = System.currentTimeMillis();
				throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
			}
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				// Treat this as a transient problem
				Logging.connectors.warn("DCTM: Transient remote exception creating session: "+e.getMessage(),e);
				currentTime = System.currentTimeMillis();
				throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
			}
			catch (DocumentumException e)
			{
				// Base our treatment on the kind of error it is.
				if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
				{
					Logging.connectors.warn("DCTM: Remote service interruption creating session: "+e.getMessage(),e);
					currentTime = System.currentTimeMillis();
					throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12*60*60000L,
						-1,true);
				}
				throw new MetacartaException(e.getMessage(),e);
			}
		}
		
		// Note that we need the session at this time; this will determine when
		// the session expires.
		lastSessionFetch = System.currentTimeMillis();

	}

	protected class GetListOfValuesThread extends Thread
	{
		protected String query;
		protected String fieldName;
		protected ArrayList list;
		protected Throwable exception = null;

		public GetListOfValuesThread(String query, String fieldName, ArrayList list)
		{
			super();
			setDaemon(true);
			this.query = query;
			this.fieldName = fieldName;
			this.list = list;
		}
		
		public void run()
		{
			try
			{
				IDocumentumResult result = session.performDQLQuery(query);
				try
				{
					while (result.isValidRow())
					{
						list.add(result.getStringValue(fieldName));
						result.nextRow();
					}
					return;
				}
				finally
				{
					result.close();
				}

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

	protected void getAttributesForType(ArrayList list, String typeName)
		throws DocumentumException, MetacartaException, ServiceInterruption
	{
		String strDQL = "select attr_name FROM dmi_dd_attr_info where type_name = '" + typeName + "'";

		while (true)
		{
			boolean noSession = (session==null);
			getSession();
			GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name",list);
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
					else
						throw (Error)thr;
				}
				return;
			}
			catch (InterruptedException e)
			{
				t.interrupt();
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				if (noSession)
				{
					long currentTime = System.currentTimeMillis();
					throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
				}
				session = null;
				lastSessionFetch = -1L;
				continue;
			}
		}
	}

	protected class CheckConnectionThread extends Thread
	{
		protected Throwable exception = null;

		public CheckConnectionThread()
		{
			super();
			setDaemon(true);
		}
		
		public void run()
		{
			try
			{
				session.checkConnection();
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

	/** Check connection, with appropriate retries */
	protected void checkConnection()
		throws DocumentumException, MetacartaException, ServiceInterruption
	{
		while (true)
		{
			boolean noSession = (session==null);
			getSession();
			CheckConnectionThread t = new CheckConnectionThread();
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
					else
						throw (Error)thr;
				}
				return;
			}
			catch (InterruptedException e)
			{
				t.interrupt();
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				if (noSession)
				{
					long currentTime = System.currentTimeMillis();
					throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
				}
				session = null;
				lastSessionFetch = -1L;
				continue;
			}
		}
	}

	protected class BuildDateStringThread extends Thread
	{
		protected long timevalue;
		protected Throwable exception = null;
		protected String rval = null;

		public BuildDateStringThread(long timevalue)
		{
			super();
			setDaemon(true);
			this.timevalue = timevalue;
		}
		
		public void run()
		{
			try
			{
				rval = session.buildDateString(timevalue);
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
		
		public String getResponse()
		{
			return rval;
		}
	}

	/** Build date string with appropriate reset */
	protected String buildDateString(long timevalue)
		throws DocumentumException, MetacartaException, ServiceInterruption
	{
		while (true)
		{
			boolean noSession = (session==null);
			getSession();
			BuildDateStringThread t = new BuildDateStringThread(timevalue);
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
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
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				if (noSession)
				{
					long currentTime = System.currentTimeMillis();
					throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
				}
				session = null;
				lastSessionFetch = -1L;
				continue;
			}
		}
	}

	protected class DestroySessionThread extends Thread
	{
		protected Throwable exception = null;

		public DestroySessionThread()
		{
			super();
			setDaemon(true);
		}
		
		public void run()
		{
			try
			{
				session.destroySession();
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

	/** Release the session, if it's time.
	*/
	protected void releaseCheck()
		throws MetacartaException
	{
		if (lastSessionFetch == -1L)
			return;

		long currentTime = System.currentTimeMillis();
		if (currentTime >= lastSessionFetch + timeToRelease)
		{
			DestroySessionThread t = new DestroySessionThread();
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
					else
						throw (Error)thr;
				}
				session = null;
				lastSessionFetch = -1L;
			}
			catch (InterruptedException e)
			{
				t.interrupt();
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				session = null;
				lastSessionFetch = -1L;
				// Treat this as a transient problem
				Logging.connectors.warn("Transient remote exception closing session: "+e.getMessage(),e);
			}
			catch (DocumentumException e)
			{
				// Base our treatment on the kind of error it is.
				if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
				{
					Logging.connectors.warn("Remote service interruption closing session: "+e.getMessage(),e);
				}
				else
					Logging.connectors.warn("Error closing session: "+e.getMessage(),e);
			}
		}
	}

	/** Constructor.
	*/
	public DCTM()
	{
		super();
	}

	/** Let the crawler know the completeness of the information we are giving it.
	*/
	public int getConnectorModel()
	{
		// For documentum, originally we thought it would return the deleted objects when we
		// reseeded.  Later research has shown that documentum simply deletes the whole thing now
		// and doesn't leave a gravemarker around at all.  So we have no choice but to treat this
		// like other stupid repositories and check for deletes by scanning!  UGH.  It also does
		// not accurately provide changes, because the ACL changes are not caught by the query.
		return MODEL_ADD;
	}

	/** Return the list of activities that this connector supports (i.e. writes into the log).
	*@return the list.
	*/
	public String[] getActivitiesList()
	{
		return new String[]{ACTIVITY_FETCH};
	}

	/** Get the folder for the jsps.
	*/
	public String getJSPFolder()
	{
		return "DCTM";
	}

	/** Test the connection.  Returns a string describing the connection integrity.
	*@return the connection's status as a displayable string.
	*/
	public String check()
			throws MetacartaException
	{
		try
		{
			try
			{
			    checkConnection();
			    return super.check();
			}
			catch (DocumentumException e)
			{
				// Base our treatment on the kind of error it is.
				if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
					throw new ServiceInterruption(e.getMessage(),0L);
				else
					throw new MetacartaException(e.getMessage(),e);
			}
		}
		catch (ServiceInterruption e)
		{
			return "Connection temporarily failed: "+e.getMessage();
		}
		catch (MetacartaException e)
		{
			return "Connection failed: "+e.getMessage();
		}
	}

	/** Connect.  The configuration parameters are included.
	*@param configParams are the configuration parameters for this connection.
	* Note well: There are no exceptions allowed from this call, since it is expected to mainly establish connection parameters.
	*/
	public void connect(ConfigParams configParams)
	{
		super.connect(configParams);
	
		// Set local parameters, for convenience
		docbaseName = params.getParameter(CONFIG_PARAM_DOCBASE);
		userName = params.getParameter(CONFIG_PARAM_USERNAME);
		password = params.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
		webtopBaseURL = params.getParameter(CONFIG_PARAM_WEBTOPBASEURL);
		domain = params.getParameter(CONFIG_PARAM_DOMAIN);
		if (domain == null || domain.length() < 1)
			domain = null;
	}

	/** This method is periodically called for all connectors that are connected but not
	* in active use.
	*/
	public void poll()
			throws MetacartaException
	{
		releaseCheck();
	}

	/** Disconnect from Documentum.
	*/
	public void disconnect()
		throws MetacartaException
	{
		if (session != null)
		{
			DestroySessionThread t = new DestroySessionThread();
			try
			{
				t.start();
				t.join();
				Throwable thr = t.getException();
				if (thr != null)
				{
					if (thr instanceof RemoteException)
						throw (RemoteException)thr;
					else if (thr instanceof DocumentumException)
						throw (DocumentumException)thr;
					else
						throw (Error)thr;
				}
				session = null;
				lastSessionFetch = -1L;
			}
			catch (InterruptedException e)
			{
				t.interrupt();
				throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
			}
			catch (RemoteException e)
			{
				Throwable e2 = e.getCause();
				if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
					throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
				session = null;
				lastSessionFetch = -1L;
				// Treat this as a transient problem
				Logging.connectors.warn("DCTM: Transient remote exception closing session: "+e.getMessage(),e);
			}
			catch (DocumentumException e)
			{
				// Base our treatment on the kind of error it is.
				if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
				{
					Logging.connectors.warn("DCTM: Remote service interruption closing session: "+e.getMessage(),e);
				}
				else
					Logging.connectors.warn("DCTM: Error closing session: "+e.getMessage(),e);
			}
			
		}

		docbaseName = null;
		userName = null;
		password = null;
		domain = null;
		webtopBaseURL = null;

	}

	/** Protected method for calculating the URI
	*/
	protected String convertToURI(String strObjectId, String objectType)
		throws MetacartaException
	{
		    String strWebtopBaseUrl = webtopBaseURL;

		    if (!strWebtopBaseUrl.endsWith("/"))
		    {
			strWebtopBaseUrl = strWebtopBaseUrl + "/";
		    }

		    return strWebtopBaseUrl +
			"component/drl?versionLabel=CURRENT&objectId=" + strObjectId;
	}

	/** Get the bin (so throttling makes sense).  We will bin by docbase.
	*/
	public String[] getBinNames(String documentIdentifier)
	{
		// Previously, this actually established a session and went back-and-forth with
		// documentum.  But we already have the docbase name, so that seems stupid.
		return new String[]{docbaseName};
	}

	protected static class StringQueue
	{
		protected String value = null;
		protected boolean present = false;
		protected boolean abort = false;
		
		public StringQueue()
		{
		}
		
		public synchronized String getNext()
			throws InterruptedException
		{
			while (abort == false && present == false)
				wait();
			if (abort)
				return null;
			present = false;
			String rval = value;
			notifyAll();
			return rval;
		}
		
		public synchronized void add(String value)
			throws InterruptedException
		{
			while (abort == false && present == true)
				wait();
			if (abort)
				return;
			present = true;
			this.value = value;
			notifyAll();
		}
		
		public synchronized void abort()
		{
			abort = true;
			notifyAll();
		}
	}
	
	protected class GetDocumentsFromQueryThread extends Thread
	{
		protected String dql;
		protected StringQueue queue;
		protected Throwable exception = null;
		protected boolean abortSignaled = false;

		public GetDocumentsFromQueryThread(String dql, StringQueue queue)
		{
			super();
			setDaemon(true);
			this.dql = dql;
			this.queue = queue;
		}
		
		public void abort()
		{
			abortSignaled = true;
			queue.abort();
		}
		
		public void run()
		{
			try
			{
				try
				{
					// This is a bit dicey, because any call to a ISeedingActivities method may well cause locks to be thrown.  The owning thread,
					// however, will try to shut this thread down only once, then it will exit itself.  Since the activities method itself is properly
					// interruptible, cleanup will correctly occur should there be enough time to do it before the process exits - but that is not
					// guaranteed, unfortunately.
					//
					// So, the only way this can work is to build an in-memory queue, where the owning thread does the actual call to the appropriate
					// ISeedingActivities method.  It's yet another complication on an already extremely complex model.
					if (!abortSignaled)
					{
						IDocumentumResult result = session.performDQLQuery(dql);
						try
						{
							while (result.isValidRow())
							{
								if (abortSignaled)
									break;
								String strObjectId = result.getStringValue("i_chronicle_id");
								result.nextRow();
								queue.add(strObjectId);
							}
						}
						finally
						{
							result.close();
						}
					}
				}
				catch (InterruptedException e)
				{
					// Abort the thread
					throw e;
				}
				catch (Throwable e)
				{
					this.exception = e;
				}
				finally
				{
					// Always signal the end!!  This guarantees that the calling thread will be able to wake up and notice we have finished.
					queue.add(null);
				}
			}
			catch (InterruptedException e)
			{
				// Just end
			}
		}
		
		public Throwable getException()
		{
			return exception;
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
		// First, build the query
		
		int i = 0;
		StringBuffer strLocationsClause = new StringBuffer();
		ArrayList tokenList = new ArrayList();
		ArrayList contentList = null;
		String maxSize = null;

		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i);
			if (n.getType().equals(CONFIG_PARAM_LOCATION))
			{
			    String strLocation = n.getAttributeValue("path");
			    if (strLocation != null && strLocation.length() > 0)
			   {
			       if (strLocationsClause != null && strLocationsClause.length() > 0)
			       {
				 strLocationsClause.append(" OR Folder('").append(strLocation).append("', DESCEND)");
			       }
			       else
			       {
				 strLocationsClause.append("Folder('").append(strLocation).append("', DESCEND)");
			       }
			   }
			}
			else if (n.getType().equals(CONFIG_PARAM_OBJECTTYPE))
			{
				String objType = n.getAttributeValue("token");
				tokenList.add(objType);
			}
			else if (n.getType().equals(CONFIG_PARAM_FORMAT))
			{
				String docType = n.getAttributeValue("value");
				if (contentList == null)
					contentList = new ArrayList();
				contentList.add(docType);
			}
			else if (n.getType().equals(CONFIG_PARAM_MAXLENGTH))
			{
				maxSize = n.getAttributeValue("value");
			}

			i++;
		}

		if (tokenList.size() == 0)
		{
			Logging.connectors.debug("DCTM: No ObjectType found in Document Spec. Setting it to dm_document");
			tokenList.add("dm_document");
		}

		if (strLocationsClause.length() < 1)
		{
			Logging.connectors.debug("DCTM: No location found in document specification. Search will be across entire docbase");
		}

		try
		{
			String strDQLstart = "select for READ distinct i_chronicle_id from ";
			// There seems to be some unexplained slop in the latest DCTM version.  It misses documents depending on how close to the r_modify_date you happen to be.
			// So, I've decreased the start time by a full five minutes, to insure overlap.
			if (startTime > 300000L)
				startTime = startTime - 300000L;
			else
				startTime = 0L;
			StringBuffer strDQLend = new StringBuffer(" where r_modify_date >= " + buildDateString(startTime) +
			    " and r_modify_date<=" + buildDateString(endTime) + 
			    " AND (i_is_deleted=TRUE Or (i_is_deleted=FALSE AND a_full_text=TRUE AND r_content_size>0");

			// append maxsize if set
			if (maxSize != null && maxSize.length() > 0)
			{
				strDQLend.append(" AND r_content_size<=").append(maxSize);
			}

			String[] dctmTypes = convertToDCTMTypes(contentList);
			if (dctmTypes != null)
			{
				if (dctmTypes.length == 0)
					strDQLend.append(" AND 1<0");
				else
				{
					i = 0;
					strDQLend.append(" AND a_content_type IN (");
					while (i < dctmTypes.length)
					{
						if (i > 0)
							strDQLend.append(",");
						String cType = dctmTypes[i++];
						strDQLend.append("'").append(cType).append("'");
					}
					strDQLend.append(")");
				}
			}

			// End the clause for non-deleted documents
			strDQLend.append("))");

			// append location on if it is provided.  This will apply to both deleted and non-deleted documents.
			if (strLocationsClause.length() > 0)
			{
				strDQLend.append(" AND ( " + strLocationsClause.toString() + " )");
			}

			// Now, loop through the documents and queue them up. 
			int tokenIndex = 0;
			while (tokenIndex < tokenList.size())
			{
				activities.checkJobStillActive();
				String tokenValue = (String)tokenList.get(tokenIndex);
				String strDQL = strDQLstart + tokenValue + strDQLend;
				if (Logging.connectors.isDebugEnabled())
					Logging.connectors.debug("DCTM: About to execute query= (" + strDQL + ")");
				while (true)
				{
					boolean noSession = (session==null);
					getSession();
					StringQueue stringQueue = new StringQueue();
					GetDocumentsFromQueryThread t = new GetDocumentsFromQueryThread(strDQL,stringQueue);
					try
					{
						t.start();
						try
						{
							int checkIndex = 0;
							// Loop through return values and add them until done is signalled
							while (true)
							{
								if (checkIndex == 10)
								{
									activities.checkJobStillActive();
									checkIndex = 0;
								}
								checkIndex++;
								String next = stringQueue.getNext();
								if (next == null)
									break;
								activities.addSeedDocument(next);
							}
							t.join();
							Throwable thr = t.getException();
							if (thr != null)
							{
								if (thr instanceof RemoteException)
									throw (RemoteException)thr;
								else if (thr instanceof DocumentumException)
									throw (DocumentumException)thr;
								else if (thr instanceof InterruptedException)
									throw (InterruptedException)thr;
								else
									throw (Error)thr;
							}
							tokenIndex++;
							// Go on to next document type and repeat
							break;
						}
						catch (InterruptedException e)
						{
							t.abort();
							// This is just a courtesy; the thread will be killed regardless on process exit
							t.interrupt();
							// It's ok to leave the thread still active; we'll be shutting down anyway.
							throw e;
						}
						catch (MetacartaException e)
						{
							t.abort();
							// We need the join, because we really don't want this documentum session to be
							// still busy when we leave.
							t.join();
							throw e;
						}
						catch (ServiceInterruption e)
						{
							t.abort();
							// We need the join, because we really don't want this documentum session to be
							// still busy when we leave.
							t.join();
							throw e;
						}
						catch (RemoteException e)
						{
							Throwable e2 = e.getCause();
							if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
								throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
							if (noSession)
							{
								long currentTime = System.currentTimeMillis();
								throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
							}
							session = null;
							lastSessionFetch = -1L;
							// Go back around again
						}
					}
					catch (InterruptedException e)
					{
						throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
					}
				}
			}
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				long currentTime = System.currentTimeMillis();
				Logging.connectors.warn("DCTM: Remote service interruption getting versions: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}

	}

	/** Do a query and read back the name column */
	protected static String[] convertToDCTMTypes(ArrayList contentList)
		throws MetacartaException, ServiceInterruption
	{
		if (contentList != null && contentList.size() > 0)
		{
			// The contentList has type names.
			String[] rval = new String[contentList.size()];
			int i = 0;
			while (i < rval.length)
			{
				rval[i] = (String)contentList.get(i);
				i++;
			}
			return rval;
		}
		return null;

	}

	protected class GetDocumentVersionThread extends Thread
	{
		protected String documentIdentifier;
		protected HashMap typeMap;
		protected String forcedAclString;
		protected String pathNameAttributeVersion;
		protected Throwable exception = null;
		protected String rval = null;

		public GetDocumentVersionThread(String documentIdentifier, HashMap typeMap, String forcedAclString, String pathNameAttributeVersion)
		{
			super();
			setDaemon(true);
			this.documentIdentifier = documentIdentifier;
			this.typeMap = typeMap;
			this.forcedAclString = forcedAclString;
			this.pathNameAttributeVersion = pathNameAttributeVersion;
		}
		
		public void run()
		{
			try
			{
					
				IDocumentumObject object = session.getObjectByQualification("dm_document where i_chronicle_id='" + documentIdentifier +
					"' and any r_version_label='CURRENT'");
				try
				{
					if (object.exists() && !object.isDeleted() && !object.isHidden() && object.getPermit() > 1 &&
						object.getContentSize() > 0 && object.getPageCount() > 0)
					{
						// According to Ryck, the version label is not helping us much, so if it's null it's ok
						String versionLabel = object.getVersionLabel();

						// The version string format was reorganized on 11/6/2006.

						StringBuffer strVersionLabel = new StringBuffer();

						// Get the type name; this is what we use to figure out the desired attributes
						String typeName = object.getTypeName();
						// Look for the string to append to the version
						String metadataVersionAddendum = (String)typeMap.get(typeName);
						// If there's no typemap entry, it can only mean that the document type was not selected for in the UI.
						// In that case, we presume no metadata.

						if (metadataVersionAddendum != null)
							strVersionLabel.append(metadataVersionAddendum);
						else
							packList(strVersionLabel,new String[0],'+');

						// Now do the forced acls.  Since this is a reorganization of the version string,
						// I decided to make these parseable, and pass them through to processDocument() in that
						// way, because most connectors seem to be heading in that direction.
						strVersionLabel.append(forcedAclString);

						// The version label passed back will be a concatenation of the implicit version label and the v_stamp
						// This way we can catch any changes to the content
						strVersionLabel.append(versionLabel);
						strVersionLabel.append("_").append(object.getVStamp());

			/* This was removed on 9/5/2006 because Rick indicated that i_vstamp is incremented on every change to a document,
			   including change of dynamic acl name.  This is in contrast to r_modifydate, which is NOT changed under such conditions.

						if (acls != null && acls.length == 0)
						{
							// Get the acl for the document, and tack it on to the version if it's dynamic.  This compensates
							// for the fact that changing a dynamic acl on an object doesn't mark it as modified!
							String aclName = object.getACLName();
							if (aclName != null && aclName.startsWith("dm_"))
								strVersionLabel.append("=").append(aclName);
						}
			*/

						// Append the path name attribute version
						strVersionLabel.append(pathNameAttributeVersion);

						// Append the Webtop base url.  This was added on 9/7/2007.
						strVersionLabel.append("_").append(webtopBaseURL);
						
						rval = strVersionLabel.toString();
					}
					else
						rval = null;
				}
				finally
				{
					object.release();
				}

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
		
		public String getResponse()
		{
			return rval;
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
	public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activity,
		DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
		throws MetacartaException, ServiceInterruption
	{
		Logging.connectors.debug("DCTM: Inside getDocumentVersions");

		String[] strArrayRetVal = new String[documentIdentifiers.length];

		// Get the forced acls (and whether security is on as well)
		String[] acls = getAcls(spec);
		// Build a "forced acl" version string, of the form ";<acl>+<acl>+..."
		StringBuffer forcedAclString = new StringBuffer();
		if (acls != null)
		{
			forcedAclString.append('+');
			java.util.Arrays.sort(acls);
			packList(forcedAclString,acls,'+');
			// KDW: Added 4/21/2008 to allow AD authority failure to disable documents with forced acls
			if (usesDefaultAuthority)
				pack(forcedAclString,defaultAuthorityDenyToken,'+');
			else
				pack(forcedAclString,denyToken,'+');
		}
		else
			forcedAclString.append('-');

		// Build a map of type name and metadata version string to append
		HashMap typeMap = new HashMap();
		String pathAttributeName = null;
		MatchMap matchMap = new MatchMap();

		int i = 0;
		while (i < spec.getChildCount())
		{
			SpecificationNode n = spec.getChild(i++);
			if (n.getType().equals(CONFIG_PARAM_OBJECTTYPE))
			{
				String typeName = n.getAttributeValue("token");
				String isAll = n.getAttributeValue("all");
				ArrayList list = new ArrayList();
				if (isAll != null && isAll.equals("true"))
				{
					// "All" attributes are specified
					// The current complete list of attributes must be fetched for this document type
					try
					{
						getAttributesForType(list,typeName);
					}
					catch (DocumentumException e)
					{
						// Base our treatment on the kind of error it is.
						long currentTime = System.currentTimeMillis();
						if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
						{
							Logging.connectors.warn("DCTM: Remote service interruption listing attributes: "+e.getMessage(),e);
							throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
						}
						throw new MetacartaException(e.getMessage(),e);
					}

				}
				else
				{
					int l = 0;
					while (l < n.getChildCount())
					{
						SpecificationNode sn = n.getChild(l++);
						if (sn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
						{
							String attrName = sn.getAttributeValue("attrname");
							list.add(attrName);
						}
					}
				}
				// Sort the attribute names, because we need them to be comparable.
				String[] sortArray = new String[list.size()];
				int j = 0;
				while (j < sortArray.length)
				{
					sortArray[j] = (String)list.get(j);
					j++;
				}
				java.util.Arrays.sort(sortArray);
				StringBuffer sb = new StringBuffer();
				packList(sb,sortArray,'+');
				typeMap.put(typeName,sb.toString());
			}
			else if (n.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
				pathAttributeName = n.getAttributeValue("value");
			else if (n.getType().equals(CONFIG_PARAM_PATHMAP))
			{
				// Path mapping info also needs to be looked at, because it affects what is
				// ingested.
				String pathMatch = n.getAttributeValue("match");
				String pathReplace = n.getAttributeValue("replace");
				matchMap.appendMatchPair(pathMatch,pathReplace);
			}
		}


		// Calculate the part of the version string that comes from path name and mapping.
		// This starts with = since ; is used by another optional component (the forced acls)
		StringBuffer pathNameAttributeVersion = new StringBuffer();
		if (pathAttributeName != null)
			pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

		int intObjectIdCount = documentIdentifiers.length;

		long currentTime;
		
		try
		{
		    for (int intInc = 0; intInc < intObjectIdCount; intInc++)
		    {
			// Since each documentum access is time-consuming, be sure that we abort if the job has gone inactive
			activity.checkJobStillActive();

			String documentIdentifier = documentIdentifiers[intInc];
			while (true)
			{
				boolean noSession = (session==null);
				getSession();
				GetDocumentVersionThread t = new GetDocumentVersionThread(documentIdentifier, typeMap, forcedAclString.toString(), pathNameAttributeVersion.toString());
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RemoteException)
							throw (RemoteException)thr;
						else if (thr instanceof DocumentumException)
							throw (DocumentumException)thr;
						else
							throw (Error)thr;
					}
					String versionString = t.getResponse();
					strArrayRetVal[intInc] = versionString;
					
					if (Logging.connectors.isDebugEnabled())
					{
						if (versionString != null)
						{
							Logging.connectors.debug("DCTM: Document " + documentIdentifier+" has version label: " + versionString);
						}
						else
						{
							Logging.connectors.debug("DCTM: Document " + documentIdentifier+" has been removed or is hidden");
						}
					}
					// Leave the retry loop; go on to the next document
					break;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RemoteException e)
				{
					Throwable e2 = e.getCause();
					if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
						throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
					if (noSession)
					{
						currentTime = System.currentTimeMillis();
						throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
					}
					session = null;
					lastSessionFetch = -1L;
					// Go back around again
				}
			}
		    }
		    return strArrayRetVal;
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption getting versions: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}
	}

	protected class ProcessDocumentThread extends Thread
	{
		protected String documentIdentifier;
		protected String versionString;
		protected File objFileTemp;
		protected SystemMetadataDescription sDesc;
		protected Throwable exception = null;
		protected RepositoryDocument rval = null;
		protected Long activityStartTime = null;
		protected Long activityFileLength = null;
		protected String activityStatus = null;
		protected String activityMessage = null;
		protected String uri = null;
		

		public ProcessDocumentThread(String documentIdentifier, String versionString, File objFileTemp, SystemMetadataDescription sDesc)
		{
			super();
			setDaemon(true);
			this.documentIdentifier = documentIdentifier;
			this.versionString = versionString;
			this.objFileTemp = objFileTemp;
			this.sDesc = sDesc;
		}
		
		public void run()
		{
			try
			{
				IDocumentumObject object = session.getObjectByQualification("dm_document where i_chronicle_id='" + documentIdentifier +
					"' and any r_version_label='CURRENT'");
				try
				{
					if (object.exists() && !object.isDeleted() && !object.isHidden() && object.getPermit() > 1 &&
						object.getContentSize() > 0 && object.getPageCount() > 0)
					{
						String objName = object.getObjectName();

						// This particular way of getting content failed, because DFC loaded the
						// whole object into memory (very very bad DFC!)
						// InputStream is = objIDfSysObject.getContent();
						//
						// Instead, read the file to a disk temporary file, and then stream from there.
						activityStartTime = new Long(System.currentTimeMillis());

						String strFilePath = null;
						try
						{
							strFilePath = object.getFile(objFileTemp.getCanonicalPath());
						}
						catch (DocumentumException dfe)
						{
							// Fetch failed, so log it
							activityStatus = "Did not exist";
							activityMessage = dfe.getMessage();
							if (dfe.getType() != DocumentumException.TYPE_NOTALLOWED)
								throw dfe;
							return;
						}
						long fileLength = objFileTemp.length();
						activityFileLength = new Long(fileLength);

						if (strFilePath == null)
						{
							activityStatus = "Failed";
							activityMessage = "Unknown";
							// We don't know why it won't fetch, but skip it and keep going.
							return;
						}
						
						activityStatus = "Success";

						rval = new RepositoryDocument();

						// Handle the metadata.
						// The start of the version string contains the names of the metadata.  We parse it out of the
						// version string, because we don't want the chance of somebody changing something after we got
						// the version together and before we actually ingested the metadata.  Plus, it's faster.
						ArrayList attributeDescriptions = new ArrayList();
						int startPosition = unpackList(attributeDescriptions,versionString,0,'+');
						// Unpack forced acls.
						ArrayList acls = null;
						String denyAcl = null;
						if (startPosition < versionString.length() && versionString.charAt(startPosition++) == '+')
						{
							acls = new ArrayList();
							startPosition = unpackList(acls,versionString,startPosition,'+');
							StringBuffer denyAclBuffer = new StringBuffer();
							startPosition = unpack(denyAclBuffer,versionString,startPosition,'+');
							denyAcl = denyAclBuffer.toString();
						}

						int z = 0;
						while (z < attributeDescriptions.size())
						{
							String attrName = (String)attributeDescriptions.get(z++);
							// Fetch the attributes from the object
							String[] values = object.getAttributeValues(attrName);
							// Add the attribute to the rd
							rval.addField(attrName,values);
						}

						// Add the path metadata item into the mix, if enabled
						String pathAttributeName = sDesc.getPathAttributeName();
						if (pathAttributeName != null && pathAttributeName.length() > 0)
						{
							String[] pathString = sDesc.getPathAttributeValue(object);
							rval.addField(pathAttributeName,pathString);
						}

						// Handle the forced acls
						if (acls != null && acls.size() == 0)
						{
							String[] strarrACL = new String[1];
							// This used to go back-and-forth to documentum to get the docbase name, but that seemed stupid, so i just
							// use the one I have already now.
							strarrACL[0] = docbaseName + ":" + object.getACLDomain() + "." + object.getACLName();
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("DCTM: Processing document (" + objName + ") with ACL=" + strarrACL[0] + " and size=" + object.getContentSize() + " bytes.");
							rval.setACL(strarrACL);
						}
						else if (acls != null)
						{
							String[] forcedAcls = new String[acls.size()];
							z = 0;
							while (z < forcedAcls.length)
							{
								forcedAcls[z] = (String)acls.get(z);
								z++;
							}
							rval.setACL(forcedAcls);
									
								
							if (Logging.connectors.isDebugEnabled())
								Logging.connectors.debug("DCTM: Processing document (" + objName + ") with size=" + object.getContentSize() + " bytes.");
						}

						if (denyAcl != null)
						{
							String[] denyAcls = new String[]{denyAcl};
							rval.setDenyACL(denyAcls);
						}
						
						uri = convertToURI(object.getObjectId(),object.getContentType());
					}
				}
				finally
				{
					object.release();
				}
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
		
		public RepositoryDocument getResponse()
		{
			return rval;
		}
		
		public Long getActivityStartTime()
		{
			return activityStartTime;
		}
		
		public Long getActivityFileLength()
		{
			return activityFileLength;
		}
		
		public String getActivityStatus()
		{
			return activityStatus;
		}
		
		public String getActivityMessage()
		{
			return activityMessage;
		}
		
		public String getURI()
		{
			return uri;
		}
	}
	
	/** Process documents whose versions indicate they need processing.
	*/
	public void processDocuments(String[] documentIdentifiers, String[] documentVersions,
		IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
		throws MetacartaException, ServiceInterruption
	{
		Logging.connectors.debug("DCTM: Inside processDocuments");

		// Build the node/path cache
		SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

		int intObjectIdCount = documentIdentifiers.length;

		long currentTime;
		
		try
		{
		    for (int intInc = 0; intInc < intObjectIdCount; intInc++)
		    {
			// Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
			activities.checkJobStillActive();

			String documentIdentifier = documentIdentifiers[intInc];
			String versionString =  documentVersions[intInc];

			if (!scanOnly[intInc])
			{
				while (true)
				{
					boolean noSession = (session==null);
					getSession();

					// Create a temporary file for every attempt, because we don't know yet whether we'll need it or not -
					// but probably we will.
					File objFileTemp = File.createTempFile("_mc_dctm_", null);
					try
					{
						ProcessDocumentThread t = new ProcessDocumentThread(documentIdentifier,versionString,objFileTemp,
							sDesc);
						try
						{
							t.start();
							t.join();
							Throwable thr = t.getException();
							if (thr != null)
							{
								if (thr instanceof RemoteException)
									throw (RemoteException)thr;
								else if (thr instanceof DocumentumException)
									throw (DocumentumException)thr;
                                                                else if (thr instanceof MetacartaException)
                                                                        throw (MetacartaException)thr;
								else
									throw (Error)thr;
							}

							// Log the fetch activity
							if (t.getActivityStatus() != null)
								activities.recordActivity(t.getActivityStartTime(),ACTIVITY_FETCH,
									t.getActivityFileLength(),documentIdentifier,t.getActivityStatus(),t.getActivityMessage(),
									null);

							RepositoryDocument rd = t.getResponse();
							if (rd != null)
							{
								// Stream the data to the ingestion system
								InputStream is = new FileInputStream(objFileTemp);
								try
								{
									rd.setBinary(is, t.getActivityFileLength().longValue());
									// Do the ingestion
									activities.ingestDocument(documentIdentifier,versionString,
										t.getURI(), rd);
								}
								finally
								{
									is.close();
								}
							}
							
							// Abort the retry loop and go on to the next document
							break;
							
						}
						catch (InterruptedException e)
						{
							t.interrupt();
							throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
						}
						catch (RemoteException e)
						{
							Throwable e2 = e.getCause();
							if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
								throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
							if (noSession)
							{
								currentTime = System.currentTimeMillis();
								throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
							}
							session = null;
							lastSessionFetch = -1L;
							// Go back around
						}
					}
					finally
					{
						objFileTemp.delete();
					}
				}
					
			}
		    }
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption reading files: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}
		catch (java.io.InterruptedIOException e)
		{
			throw new MetacartaException("Interrupted IO: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
		}
		catch (java.io.IOException e)
		{
			throw new MetacartaException("IO exception: "+e.getMessage(),e);
		}
	}

	/** Free a set of documents.  This method is called for all documents whose versions have been fetched using
	* the getDocumentVersions() method, including those that returned null versions.  It may be used to free resources
	* committed during the getDocumentVersions() method.  It is guaranteed to be called AFTER any calls to
	* processDocuments() for the documents in question.
	*@param documentIdentifiers is the set of document identifiers.
	*@param versions is the corresponding set of version identifiers (individual identifiers may be null).
	*/
	public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions)
		throws MetacartaException
	{
		// Nothing to do
	}

	/** Documentum-specific method, for UI support.
	* This one returns the supported content types, which will be presented in the UI for selection.
	*/
	public String[] getContentTypes()
		throws MetacartaException, ServiceInterruption
	{
		try
		{
			String dql = "select name from dm_format where can_index=true order by name asc";
			while (true)
			{
				boolean noSession = (session==null);
				getSession();
				ArrayList contentTypes = new ArrayList();
				GetListOfValuesThread t = new GetListOfValuesThread(dql,"name",contentTypes);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RemoteException)
							throw (RemoteException)thr;
						else if (thr instanceof DocumentumException)
							throw (DocumentumException)thr;
						else
							throw (Error)thr;
					}
					String[] rval = new String[contentTypes.size()];
					int i = 0;
					while (i < rval.length)
					{
						rval[i] = (String)contentTypes.get(i);
						i++;
					}
					return rval;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RemoteException e)
				{
					Throwable e2 = e.getCause();
					if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
						throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
					if (noSession)
					{
						long currentTime = System.currentTimeMillis();
						throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
					}
					session = null;
					lastSessionFetch = -1L;
					continue;
				}
			}
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			long currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption reading content types: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}
	}

	/** Documentum-specific method, for UI support.
	*/
	public String[] getObjectTypes()
		throws MetacartaException, ServiceInterruption
	{
		try
		{
			String strDQL = "select distinct A.r_type_name from dmi_type_info A, dmi_dd_type_info B  " +
				   " where ((not A.r_type_name like 'dm_%' and any A.r_supertype='dm_document' and B.life_cycle <> 3) " +
				   "or (A.r_type_name = 'dm_document' and B.life_cycle <> 3)) " +
				   " AND A.r_type_name = B.type_name order by A.r_type_name";
			while (true)
			{
				boolean noSession = (session==null);
				getSession();
				ArrayList objectTypes = new ArrayList();
				GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"r_type_name",objectTypes);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RemoteException)
							throw (RemoteException)thr;
						else if (thr instanceof DocumentumException)
							throw (DocumentumException)thr;
						else
							throw (Error)thr;
					}
					String[] rval = new String[objectTypes.size()];
					int i = 0;
					while (i < rval.length)
					{
						rval[i] = (String)objectTypes.get(i);
						i++;
					}
					return rval;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RemoteException e)
				{
					Throwable e2 = e.getCause();
					if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
						throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
					if (noSession)
					{
						long currentTime = System.currentTimeMillis();
						throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
					}
					session = null;
					lastSessionFetch = -1L;
					continue;
				}
			}
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			long currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption reading object types: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}
	}

	public int getMaxDocumentRequest()
	{
		// 1 at a time, since this connector does not deal with documents en masse, but one at a time.
		return 1;
	}

	protected class GetChildFolderNamesThread extends Thread
	{
		protected String strTheParentFolderPath;
		protected Throwable exception = null;
		protected String[] rval = null;

		public GetChildFolderNamesThread(String strTheParentFolderPath)
		{
			super();
			setDaemon(true);
			this.strTheParentFolderPath = strTheParentFolderPath;
		}
		
		public void run()
		{
			try
			{
				IDocumentumResult result;
				ArrayList objFolderNames = new ArrayList();
				if (strTheParentFolderPath.equalsIgnoreCase("/"))
				{
					String strDQLForCabinets = "select object_name, r_object_type, r_object_id from dm_cabinet order by 1";
					result = session.performDQLQuery(strDQLForCabinets);
				}
				else
				{
					result = session.getFolderContents(strTheParentFolderPath);
				}

				try
				{
					Map matchTypes = new HashMap();
					while (result.isValidRow())
					{
						String strObjectName = result.getStringValue("object_name");
						String strObjectType = result.getStringValue("r_object_type").trim();
						Boolean x = (Boolean)matchTypes.get(strObjectType);
						if (x == null)
						{
							// Look up whether this is a type of folder or cabinet
							boolean isMatch = session.isOneOf(strObjectType,new String[]{"dm_folder","dm_cabinet"});
							x = new Boolean(isMatch);
							matchTypes.put(strObjectType,x);
						}

						if (x.booleanValue())
						{
							objFolderNames.add(strObjectName);
						}
						result.nextRow();
					}
				}
				finally
				{
					result.close();
				}
				
				rval = new String[objFolderNames.size()];
				int i = 0;
				while (i < rval.length)
				{
					rval[i] = (String)objFolderNames.get(i);
					i++;
				}
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
		
		public String[] getResponse()
		{
			return rval;
		}
	}

	/** This method returns an ordered set of the "next things" given a folder path, for the UI to
	* use in constructing the starting folder for a job's document specification.
	*/
	public String[] getChildFolderNames(String strTheParentFolderPath)
		throws MetacartaException, ServiceInterruption
	{
		try
		{
			while (true)
			{
				boolean noSession = (session==null);
				getSession();
				GetChildFolderNamesThread t = new GetChildFolderNamesThread(strTheParentFolderPath);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RemoteException)
							throw (RemoteException)thr;
						else if (thr instanceof DocumentumException)
							throw (DocumentumException)thr;
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
				catch (RemoteException e)
				{
					Throwable e2 = e.getCause();
					if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
						throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
					if (noSession)
					{
						long currentTime = System.currentTimeMillis();
						throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
					}
					session = null;
					lastSessionFetch = -1L;
					continue;
				}
			}
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			long currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption reading child folders: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}

	}

	/** Get the list of attributes for a given data type.  This returns an inclusive list of both pre-defined and extended
	* attributes, to be presented to the user as a select list per data type.  From this list, the metadata attributes will
	* be selected.
	*@param docType is the document type (e.g. "dm_document") for which the attributes are requested.
	*@return the array of data attributes, in alphabetic order.
	*/
	public String[] getIngestableAttributes(String docType)
		throws MetacartaException, ServiceInterruption
	{
		try
		{
			String strDQL = "select attr_name FROM dmi_dd_attr_info where type_name = '" + docType + "' order by attr_name asc";
			while (true)
			{
				boolean noSession = (session==null);
				getSession();
				ArrayList attributes = new ArrayList();
				GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name",attributes);
				try
				{
					t.start();
					t.join();
					Throwable thr = t.getException();
					if (thr != null)
					{
						if (thr instanceof RemoteException)
							throw (RemoteException)thr;
						else if (thr instanceof DocumentumException)
							throw (DocumentumException)thr;
						else
							throw (Error)thr;
					}
					String[] rval = new String[attributes.size()];
					int i = 0;
					while (i < rval.length)
					{
						rval[i] = (String)attributes.get(i);
						i++;
					}
					return rval;
				}
				catch (InterruptedException e)
				{
					t.interrupt();
					throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
				}
				catch (RemoteException e)
				{
					Throwable e2 = e.getCause();
					if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
						throw new MetacartaException(e2.getMessage(),e2,MetacartaException.INTERRUPTED);
					if (noSession)
					{
						long currentTime = System.currentTimeMillis();
						throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
					}
					session = null;
					lastSessionFetch = -1L;
					continue;
				}
			}
		}
		catch (DocumentumException e)
		{
			// Base our treatment on the kind of error it is.
			long currentTime = System.currentTimeMillis();
			if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
			{
				Logging.connectors.warn("DCTM: Remote service interruption reading child folders: "+e.getMessage(),e);
				throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
			}
			throw new MetacartaException(e.getMessage(),e);
		}
	}


	// Private and protected methods

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

	/** Class that tracks paths associated with folder IDs, and also keeps track of the name
	* of the metadata attribute to use for the path.
	*/
	protected class SystemMetadataDescription
	{
		// The path attribute name
		protected String pathAttributeName;

		// The folder ID to path name mapping (which acts like a cache).
		// The key is the folder ID, and the value is an array of Strings.
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
				if (n.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
					pathAttributeName = n.getAttributeValue("value");
				else if (n.getType().equals(CONFIG_PARAM_PATHMAP))
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

		/** Given an identifier, get the array of translated strings that goes into the metadata.
		*/
		public String[] getPathAttributeValue(IDocumentumObject object)
			throws DocumentumException, RemoteException, MetacartaException
		{
			String[] paths = object.getFolderPaths(pathMap);
			String[] rval = new String[paths.length];
			int i = 0;
			while (i < paths.length)
			{
				rval[i] = matchMap.translate(paths[i]);
				i++;
			}
			return rval;
		}

	}
}
