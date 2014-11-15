/* $Id: MeridioConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.meridio;

import com.meridio.www.MeridioDMWS.DmLogicalOp;
import com.meridio.www.MeridioDMWS.DmPermission;
import com.meridio.www.MeridioDMWS.DmSearchScope;
import com.meridio.www.MeridioDMWS.DmVersionInfo;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.meridio.DMSearchResults;
import org.apache.manifoldcf.meridio.MeridioDataSetException;
import org.apache.manifoldcf.meridio.MeridioWrapper;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import org.apache.manifoldcf.connectorcommon.interfaces.*;

import org.apache.http.conn.ConnectTimeoutException;

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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import javax.xml.soap.SOAPException;

import org.apache.axis.attachments.AttachmentPart;

import org.apache.manifoldcf.crawler.connectors.meridio.DMDataSet.*;
import org.apache.manifoldcf.crawler.connectors.meridio.RMDataSet.*;

/** This is the "repository connector" for a file system.
*/
public class MeridioConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: MeridioConnector.java 996524 2010-09-13 13:38:01Z kwright $";

  // This is the base url to use.
  protected String urlBase = null;
  protected String urlVersionBase = null;

  private final static int maxHitsToReturn      = 100;

  /** Deny access token for Meridio */
  private final static String defaultAuthorityDenyToken = GLOBAL_DENY_TOKEN;

  private static final long interruptionRetryTime = 60000L;

  // These are the variables needed to establish a connection
  protected URL DmwsURL = null;
  protected URL RmwsURL = null;
  protected javax.net.ssl.SSLSocketFactory mySSLFactory = null;
  protected MeridioWrapper meridio_  = null;  // A handle to the Meridio Java API Wrapper

  /** Constructor.
  */
  public MeridioConnector() {}



  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    // Return the simplest model - full everything
    return MODEL_ADD_CHANGE;
  }


  /** Set up the session with Meridio */
  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
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

        if (keystoreData != null)
          mySSLFactory = KeystoreManagerFactory.make("",keystoreData).getSecureSocketFactory();
        else
          mySSLFactory = null;

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
        throw new ManifoldCFException("Meridio: Could not construct the URL for either " +
          "the DM or RM Meridio Web Service", malformedURLException, ManifoldCFException.REPOSITORY_CONNECTION_ERROR);
      }

      // Do the second part (where we actually try to connect to the system)
      try
      {
        /*=================================================================
        * Now try and login to Meridio; the wrapper's constructor can be
        * used as it calls the Meridio login method
        *================================================================*/
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
          mySSLFactory,
          org.apache.manifoldcf.connectorcommon.common.CommonsHTTPSender.class,
          "client-config.wsdd");
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Meridio: bad number: "+e.getMessage(),e);
      }
      catch (UnknownHostException unknownHostException)
      {
        throw new ManifoldCFException("Meridio: A Unknown Host Exception occurred while " +
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
          }
          throw new ManifoldCFException("Unknown http error occurred while connecting: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Meridio: Got an unknown remote exception connecting - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
        throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }
      catch (RemoteException remoteException)
      {
        throw new ManifoldCFException("Meridio: An unknown remote exception occurred while " +
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
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    String dmwshost = params.getParameter("DMWSServerName");
    String rmwshost = params.getParameter("RMWSServerName");
    return new String[]{dmwshost,rmwshost};
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
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
    catch (ManifoldCFException e)
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
          throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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
  @Override
  public void disconnect()
    throws ManifoldCFException
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
          throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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
      mySSLFactory = null;
      Logging.connectors.debug("Meridio: Exiting 'disconnect' method");
    }
  }



  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    return 10;
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  @Override
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException
  {
    if (command.equals("categories"))
    {
      try
      {
        String[] categories = getMeridioCategories();
        int i = 0;
        while (i < categories.length)
        {
          String category = categories[i++];
          ConfigurationNode node = new ConfigurationNode("category");
          node.setValue(category);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.equals("documentproperties"))
    {
      try
      {
        String[] properties = getMeridioDocumentProperties();
        int i = 0;
        while (i < properties.length)
        {
          String property = properties[i++];
          ConfigurationNode node = new ConfigurationNode("document_property");
          node.setValue(property);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("classorfolder/"))
    {
      String classOrFolderIdString = command.substring("classorfolder/".length());
      int classOrFolderId;
      try
      {
        classOrFolderId = Integer.parseInt(classOrFolderIdString);
      }
      catch (NumberFormatException e)
      {
        ManifoldCF.createErrorNode(output,new ManifoldCFException(e.getMessage(),e));
	return false;
      }
      try
      {
        MeridioClassContents[] contents = getClassOrFolderContents(classOrFolderId);
        int i = 0;
        while (i < contents.length)
        {
          MeridioClassContents content = contents[i++];
          ConfigurationNode node = new ConfigurationNode("content");
          ConfigurationNode child;
          child = new ConfigurationNode("id");
          child.setValue(Integer.toString(content.classOrFolderId));
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("name");
          child.setValue(content.classOrFolderName);
          node.addChild(node.getChildCount(),child);
          child = new ConfigurationNode("type");
          String typeString;
          if (content.containerType == MeridioClassContents.CLASS)
            typeString = "class";
          else if (content.containerType == MeridioClassContents.FOLDER)
            typeString = "folder";
          else
            typeString = "unknown";
          child.setValue(typeString);
          node.addChild(node.getChildCount(),child);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else
      return super.requestInfo(output,command);
    return true;
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
  * The end time and seeding version string passed to this method may be interpreted for greatest efficiency.
  * For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding version string to null.  The
  * seeding version string may also be set to null on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  * The connector will be connected before this method can be called.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param seedTime is the end of the time range of documents to consider, exclusive.
  *@param lastSeedVersionString is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("Meridio: Entering 'addSeedDocuments' method");
    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else
    {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }
    // Adjust start time so that we don't miss documents that squeeze in with earlier timestamps after we've already scanned that interval.
    // Chose an interval of 15 minutes, but I've never seen this effect take place over a time interval even 1/10 of that.
    long timeAdjust = 15L * 60000L;
    if (startTime > timeAdjust)
      startTime -= timeAdjust;
    else
      startTime = 0L;

    while (true)
    {
      getSession();

      try
      {
        DMSearchResults searchResults;
        int numResultsReturnedByStream = 0;

        while (true)
        {
          searchResults = documentSpecificationSearch(spec,
            startTime, seedTime, numResultsReturnedByStream + 1, maxHitsToReturn);

          for (int i = 0; i < searchResults.returnedHitsCount; i++)
          {
            long documentId =
              searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS()[i].getDocId();

            String strDocumentId = new Long(documentId).toString();
            activities.addSeedDocument(strDocumentId);
          }
          
          numResultsReturnedByStream += searchResults.returnedHitsCount;
          if (numResultsReturnedByStream == searchResults.totalHitsCount)
            break;
        }
        return new Long(seedTime).toString();
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
          }
          throw new ManifoldCFException("Unknown http error occurred while performing search: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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
        throw new ManifoldCFException("Meridio: A Remote Exception occurred while " +
          "performing a search: "+remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        throw new ManifoldCFException("Meridio: A problem occurred manipulating the Web " +
          "Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
      }
    }

  }


  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param statuses are the currently-stored document versions for each document in the set of document identifiers
  * passed in above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    // Get forced acls/security enable/disable
    String[] acls = getAcls(spec);
    // Sort it, in case it is needed.
    if (acls != null)
      java.util.Arrays.sort(acls);

    // Look at the metadata attributes.
    // So that the version strings are comparable, we will put them in an array first, and sort them.
    Set<String> holder = new HashSet<String>();

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
        holder.add(metadataName);
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
        ReturnMetadata[] categoryPropertyValues;
        String[] categoryPropertyStringValues;
        String[] sortArray;
        if (allMetadata)
        {
          categoryPropertyStringValues = getMeridioDocumentProperties();
        }
        else
        {
          categoryPropertyStringValues = new String[holder.size()];
          i = 0;
          for (String value : holder)
          {
            categoryPropertyStringValues[i++] = value;
          }
        }
        // Sort!
        java.util.Arrays.sort(categoryPropertyStringValues);
        categoryPropertyValues = new ReturnMetadata[categoryPropertyStringValues.length];
        i = 0;
        for (String value : categoryPropertyStringValues)
        {
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
        
        // Prepare the part of the version string that is decodeable
        StringBuilder decodeableString = new StringBuilder();

        // Add the metadata piece first
        packList(decodeableString,categoryPropertyStringValues,'+');
        
        // Now, put in the forced acls.
        // The version string needs only to contain the forced acls, since the version date captures changes
        // made to the acls that are actually associated with the document.
        if (acls == null)
          decodeableString.append('-');
        else
        {
          decodeableString.append('+');
          packList(decodeableString,acls,'+');
          decodeableString.append('+');
          pack(decodeableString,defaultAuthorityDenyToken,'+');
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

        long[] docIds = new long[documentIdentifiers.length];
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
        DMSearchResults searchResults = documentSpecificationSearch(spec,
          0, 0, 1, this.getMaxDocumentRequest(), docIds, null);

        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("Found a total of <" + searchResults.totalHitsCount + "> hit(s) " +
          "and <" + searchResults.returnedHitsCount + "> were returned by the method call");

        // If we are searching based on document identifier, then it is possible that we will not
        // find a document we are looking for, if it was removed from the system between the time
        // it was put in the queue and when it's version is obtained.  Documents where this happens
        // should return a version string of null.

        // Let's go through the search results and build a hash based on the document identifier.
        Map<Long,SEARCHRESULTS_DOCUMENTS> documentMap = new HashMap<Long,SEARCHRESULTS_DOCUMENTS>();
        if (searchResults.dsDM != null)
        {
          SEARCHRESULTS_DOCUMENTS [] srd = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
          for (i = 0; i < srd.length; i++)
          {
            documentMap.put(new Long(srd[i].getDocId()),srd[i]);
          }
        }

        // Now, walk through the individual documents.
        Map<Long,String> versionStrings = new HashMap<Long,String>();
        for (int j = 0; j < docIds.length; j++)
        {
          String documentIdentifier = documentIdentifiers[j];
          long docId = docIds[j];
          Long docKey = new Long(docId);
          // Look up the record.
          SEARCHRESULTS_DOCUMENTS doc = documentMap.get(docKey);
          if (doc != null)
          {
            // Set the version string.  The parseable stuff goes first, so parsing is easy.
            String version = doc.getStr_value();
            StringBuilder composedVersion = new StringBuilder();
            composedVersion.append(decodeableString);
            composedVersion.append(version);
            // Added 9/7/2007
            composedVersion.append("_").append(urlVersionBase);
            //
            String versionString = composedVersion.toString();
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Meridio: Document "+docKey+" has version "+versionString);
            if (activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
              versionStrings.put(docKey,versionString);
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Meridio: Document "+docKey+" is no longer in the search set, or has been deleted - removing.");
            activities.deleteDocument(documentIdentifier);
          }
        }

        // Now submit search requests for all the documents requiring fetch.
        
        Map<Long,Map<String,String>> documentPropertyMap = new HashMap<Long,Map<String,String>>();

        // Only look up metadata if we need some!
        if (versionStrings.size() > 0 && categoryPropertyValues.length > 0)
        {
          long[] fetchIds = new long[versionStrings.size()];
          i = 0;
          for (Long docKey : versionStrings.keySet())
          {
            fetchIds[i++] = docKey;
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
          searchResults = documentSpecificationSearch(spec,
            0, 0, 1, fetchIds.length,
            fetchIds, categoryPropertyValues);

          // If we ask for a document and it is no longer there, we should treat this as a deletion.
          // The activity in that case is to delete the document.  A similar thing should happen if
          // any of the other methods (like getting the document's content) also fail to find the
          // document.

          // Let's build a hash which contains all the document metadata returned.  The form of
          // the hash will be: key = the document identifier, value = another hash, which is keyed
          // by the metadata category/property, and which has a value that is the metadata value.

          Map<Long,MutableInteger> counterMap = new HashMap<Long,MutableInteger>();

          if (searchResults.dsDM != null)
          {
            SEARCHRESULTS_DOCUMENTS [] searchResultsDocuments = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
            for (SEARCHRESULTS_DOCUMENTS searchResultsDocument : searchResultsDocuments)
            {
              long docId = searchResultsDocument.getDocId();
              Long docKey = new Long(docId);
              MutableInteger counterMapItem = counterMap.get(docKey);
              if (counterMapItem == null)
              {
                counterMapItem = new MutableInteger();
                counterMap.put(docKey,counterMapItem);
              }

              String propertyName = categoryPropertyStringValues[counterMapItem.getValue()];
              counterMapItem.increment();
              String propertyValue = searchResultsDocuments[i].getStr_value();
              Map<String,String> propertyMap = documentPropertyMap.get(docKey);
              if (propertyMap == null)
              {
                propertyMap = new HashMap<String,String>();
                documentPropertyMap.put(docKey,propertyMap);
              }
              if (propertyValue != null && propertyValue.length() > 0)
                propertyMap.put(propertyName,propertyValue);
            }
          }
        }

        // Okay, we are ready now to go through the individual documents and do the ingestion or deletion.
        for (String documentIdentifier : documentIdentifiers)
        {
          Long docKey = new Long(documentIdentifier);
          long docId = docKey.longValue();
          String docVersion = versionStrings.get(docKey);
          if (docVersion != null)
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("Processing document identifier '" + documentIdentifier + "' " +
              "with version string '" + docVersion + "'");

            // For each document, be sure the job is still allowed to run.
            activities.checkJobStillActive();

            RepositoryDocument repositoryDocument = new RepositoryDocument();

            // Load the metadata items into the ingestion document object
            Map<String,String> docMetadataMap = documentPropertyMap.get(docKey);
            if (docMetadataMap != null)
            {
              for (String categoryPropertyName : categoryPropertyStringValues)
              {
                String propertyValue = docMetadataMap.get(categoryPropertyName);
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
              activities.noDocument(documentIdentifier,docVersion);
              continue;
            }

            if (null == documentData.getDOCUMENTS() ||
              documentData.getDOCUMENTS().length != 1)
            {
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Meridio: Could not retrieve document owner for document id '" +
                new Long(docId).toString() + "' in processDocuments method. No information or incorrect amount " +
                "of information was returned");
              activities.noDocument(documentIdentifier,docVersion);
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
            if (acls != null && acls.length == 0)
            {
              ACCESSCONTROL [] documentAcls = documentData.getACCESSCONTROL();
              List<String> allowAclsArrayList = new ArrayList<String>();
              List<String> denyAclsArrayList = new ArrayList<String>();

              // Allow a broken authority to disable all Meridio documents, even if the document is 'wide open', because
              // Meridio does not permit viewing of the document if the user does not exist (at least, I don't know of a way).
              denyAclsArrayList.add(defaultAuthorityDenyToken);

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
                allowAcls[j] = allowAclsArrayList.get(j);
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Meridio: Adding '" + allowAcls[j] + "' to allow ACLs");
              }

              denyAcls = new String[denyAclsArrayList.size()];
              for (int j = 0; j < denyAclsArrayList.size(); j++)
              {
                denyAcls[j] = denyAclsArrayList.get(j);
                if (Logging.connectors.isDebugEnabled())
                  Logging.connectors.debug("Meridio: Adding '" + denyAcls[j] + "' to deny ACLs");
              }
            }
            else
            {
              allowAcls = acls;
              if (allowAcls == null)
                denyAcls = null;
              else
                denyAcls = new String[]{defaultAuthorityDenyToken};
            }

            repositoryDocument.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,allowAcls,denyAcls);

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
                activities.noDocument(documentIdentifier,docVersion);
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
                  long fileSize = theTempFile.length();                   // ap.getSize();
                  if (activities.checkLengthIndexable(fileSize))
                  {
                    InputStream is = new FileInputStream(theTempFile);      // ap.getDataHandler().getInputStream();
                    try
                    {
                      repositoryDocument.setBinary(is, fileSize);

                      if (null != activities)
                      {
                        activities.ingestDocumentWithException(documentIdentifier, docVersion,
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
                    activities.noDocument(documentIdentifier, docVersion);
                    continue;
                  }
                }
                else
                {
                  if (Logging.connectors.isDebugEnabled())
                    Logging.connectors.debug("Meridio: Expected temporary file was not present - skipping document '"+new Long(docId).toString() + "'");
                  activities.deleteDocument(documentIdentifier);
                  continue;
                }
              }
              finally
              {
                ap.dispose();
              }

            }
            catch (java.net.SocketTimeoutException ioex)
            {
              throw new ManifoldCFException("Socket timeout exception: "+ioex.getMessage(), ioex);
            }
            catch (ConnectTimeoutException ioex)
            {
              throw new ManifoldCFException("Connect timeout exception: "+ioex.getMessage(), ioex);
            }
            catch (InterruptedIOException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
              throw new ManifoldCFException("SOAP Exception encountered while retrieving document content: "+soapEx.getMessage(),
                soapEx);
            }
            catch (IOException ioex)
            {
              throw new ManifoldCFException("Input stream failure: "+ioex.getMessage(), ioex);
            }
          }
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" accessing Meridio: "+e.getMessage(),e);
          }
          throw new ManifoldCFException("Unknown http error occurred while getting doc versions: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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
        throw new ManifoldCFException("Meridio: A remote exception occurred while getting doc versions: " +
          remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        throw new ManifoldCFException("Meridio: A problem occurred manipulating the Web " +
          "Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
      }
    }
  }


  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"MeridioConnector.DocumentServer"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.RecordsServer"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.Credentials"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.WebClient"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.dmwsServerPort.value != \"\" && !isInteger(editconnection.dmwsServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAValidNumber") + "\");\n"+
"    editconnection.dmwsServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.rmwsServerPort.value != \"\" && !isInteger(editconnection.rmwsServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAValidNumber") + "\");\n"+
"    editconnection.dmwsServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.dmwsProxyPort.value != \"\" && !isInteger(editconnection.dmwsProxyPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAValidNumber") + "\");\n"+
"    editconnection.dmwsProxyPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.rmwsProxyPort.value != \"\" && !isInteger(editconnection.rmwsProxyPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAValidNumber") + "\");\n"+
"    editconnection.dmwsProxyPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  if (editconnection.webClientServerPort.value != \"\" && !isInteger(editconnection.webClientServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAValidNumber") + "\");\n"+
"    editconnection.webClientServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userName.value != \"\" && editconnection.userName.value.indexOf(\"\\\\\") <= 0)\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.AValidMeridioUserNameHasTheForm") + "\");\n"+
"    editconnection.userName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.dmwsServerName.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseFillInAMeridioDocumentManagementServerName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.DocumentServer") + "\");\n"+
"    editconnection.dmwsServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.rmwsServerName.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseFillInAMeridioRecordsManagementServerName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.RecordsServer") + "\");\n"+
"    editconnection.rmwsServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webClientServerName.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseFillInAMeridioWebClientServerName") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.WebClient") + "\");\n"+
"    editconnection.webClientServerName.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.dmwsServerPort.value != \"\" && !isInteger(editconnection.dmwsServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAMeridioDocumentManagementPortNumber") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.DocumentServer") + "\");\n"+
"    editconnection.dmwsServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.rmwsServerPort.value != \"\" && !isInteger(editconnection.rmwsServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAMeridioDocumentManagementPortNumber") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.RecordsServer") + "\");\n"+
"    editconnection.rmwsServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webClientServerPort.value != \"\" && !isInteger(editconnection.webClientServerPort.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.PleaseSupplyAMeridioWebClientPortNumberOrNoneForDefault") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.WebClient") + "\");\n"+
"    editconnection.webClientServerPort.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.userName.value == \"\" || editconnection.userName.value.indexOf(\"\\\\\") <= 0)\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.TheConnectionRequiresAValidMeridioUserNameOfTheForm") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.Credentials") + "\");\n"+
"    editconnection.userName.focus();\n"+
"    return false;\n"+
"  }\n"+
"\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function DeleteCertificate(aliasName)\n"+
"{\n"+
"  editconnection.keystorealias.value = aliasName;\n"+
"  editconnection.configop.value = \"Delete\";\n"+
"  postForm();\n"+
"}\n"+
"\n"+
"function AddCertificate()\n"+
"{\n"+
"  if (editconnection.certificate.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.ChooseACertificateFile") + "\");\n"+
"    editconnection.certificate.focus();\n"+
"  }\n"+
"  else\n"+
"  {\n"+
"    editconnection.configop.value = \"Add\";\n"+
"    postForm();\n"+
"  }\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String dmwsServerProtocol = parameters.getParameter("DMWSServerProtocol");
    if (dmwsServerProtocol == null)
      dmwsServerProtocol = "http";
    String rmwsServerProtocol = parameters.getParameter("RMWSServerProtocol");
    if (rmwsServerProtocol == null)
      rmwsServerProtocol = "http";

    String dmwsServerName = parameters.getParameter("DMWSServerName");
    if (dmwsServerName == null)
      dmwsServerName = "";
    String rmwsServerName = parameters.getParameter("RMWSServerName");
    if (rmwsServerName == null)
      rmwsServerName = "";

    String dmwsServerPort = parameters.getParameter("DMWSServerPort");
    if (dmwsServerPort == null)
      dmwsServerPort = "";
    String rmwsServerPort = parameters.getParameter("RMWSServerPort");
    if (rmwsServerPort == null)
      rmwsServerPort = "";

    String dmwsLocation = parameters.getParameter("DMWSLocation");
    if (dmwsLocation == null)
      dmwsLocation = "/DMWS/MeridioDMWS.asmx";
    String rmwsLocation = parameters.getParameter("RMWSLocation");
    if (rmwsLocation == null)
      rmwsLocation = "/RMWS/MeridioRMWS.asmx";

    String dmwsProxyHost = parameters.getParameter("DMWSProxyHost");
    if (dmwsProxyHost == null)
      dmwsProxyHost = "";
    String rmwsProxyHost = parameters.getParameter("RMWSProxyHost");
    if (rmwsProxyHost == null)
      rmwsProxyHost = "";

    String dmwsProxyPort = parameters.getParameter("DMWSProxyPort");
    if (dmwsProxyPort == null)
      dmwsProxyPort = "";
    String rmwsProxyPort = parameters.getParameter("RMWSProxyPort");
    if (rmwsProxyPort == null)
      rmwsProxyPort = "";

    String userName = parameters.getParameter("UserName");
    if (userName == null)
      userName = "";

    String password = parameters.getObfuscatedParameter("Password");
    if (password == null)
      password = "";
    else
      password = out.mapPasswordToKey(password);

    String webClientProtocol = parameters.getParameter("MeridioWebClientProtocol");
    if (webClientProtocol == null)
      webClientProtocol = "http";
    String webClientServerName = parameters.getParameter("MeridioWebClientServerName");
    if (webClientServerName == null)
      webClientServerName = "";
    String webClientServerPort = parameters.getParameter("MeridioWebClientServerPort");
    if (webClientServerPort == null)
      webClientServerPort = "";
    String webClientDocDownloadLocation = parameters.getParameter("MeridioWebClientDocDownloadLocation");
    if (webClientDocDownloadLocation == null)
      webClientDocDownloadLocation = "/meridio/browse/downloadcontent.aspx";

    String meridioKeystore = parameters.getParameter("MeridioKeystore");
    IKeystoreManager localKeystore;
    if (meridioKeystore == null)
      localKeystore = KeystoreManagerFactory.make("");
    else
      localKeystore = KeystoreManagerFactory.make("",meridioKeystore);
    out.print(
"<input name=\"configop\" type=\"hidden\" value=\"Continue\"/>\n"
    );

    // "Document Server" tab
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.DocumentServer")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceServerProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"dmwsServerProtocol\">\n"+
"        <option value=\"http\" "+((dmwsServerProtocol.equals("http"))?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(dmwsServerProtocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceServerName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"dmwsServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsServerName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceServerPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"dmwsServerPort\" value=\""+dmwsServerPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceLocation") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"dmwsLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsLocation)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"separator\" colspan=\"2\"><hr/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceServerProxyHost") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"dmwsProxyHost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsProxyHost)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DocumentWebserviceServerProxyPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"dmwsProxyPort\" value=\""+dmwsProxyPort+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for the Document Server tab.
      out.print(
"<input type=\"hidden\" name=\"dmwsServerProtocol\" value=\""+dmwsServerProtocol+"\"/>\n"+
"<input type=\"hidden\" name=\"dmwsServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsServerName)+"\"/>\n"+
"<input type=\"hidden\" name=\"dmwsServerPort\" value=\""+dmwsServerPort+"\"/>\n"+
"<input type=\"hidden\" name=\"dmwsLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsLocation)+"\"/>\n"+
"<input type=\"hidden\" name=\"dmwsProxyHost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(dmwsProxyHost)+"\"/>\n"+
"<input type=\"hidden\" name=\"dmwsProxyPort\" value=\""+dmwsProxyPort+"\"/>\n"
      );
    }

    // "Records Server" tab
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.RecordsServer")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceServerProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"rmwsServerProtocol\">\n"+
"        <option value=\"http\" "+((rmwsServerProtocol.equals("http"))?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(rmwsServerProtocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceServerName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"rmwsServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsServerName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceServerPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"rmwsServerPort\" value=\""+rmwsServerPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceLocation") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"rmwsLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsLocation)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"separator\" colspan=\"2\"><hr/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceServerProxyHost") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"rmwsProxyHost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsProxyHost)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.RecordWebserviceServerProxyPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"rmwsProxyPort\" value=\""+rmwsProxyPort+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for the Records Server tab.
      out.print(
"<input type=\"hidden\" name=\"rmwsServerProtocol\" value=\""+rmwsServerProtocol+"\"/>\n"+
"<input type=\"hidden\" name=\"rmwsServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsServerName)+"\"/>\n"+
"<input type=\"hidden\" name=\"rmwsServerPort\" value=\""+rmwsServerPort+"\"/>\n"+
"<input type=\"hidden\" name=\"rmwsLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsLocation)+"\"/>\n"+
"<input type=\"hidden\" name=\"rmwsProxyHost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(rmwsProxyHost)+"\"/>\n"+
"<input type=\"hidden\" name=\"rmwsProxyPort\" value=\""+rmwsProxyPort+"\"/>\n"
      );
    }

    // The "Credentials" tab
    // Always pass the whole keystore as a hidden.
    if (meridioKeystore != null)
    {
      out.print(
"<input type=\"hidden\" name=\"keystoredata\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(meridioKeystore)+"\"/>\n"
      );
    }
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.Credentials")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.UserName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"32\" name=\"userName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Password") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.SSLCertificateList") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\"keystorealias\" value=\"\"/>\n"+
"      <table class=\"displaytable\">\n"
      );
      // List the individual certificates in the store, with a delete button for each
      String[] contents = localKeystore.getContents();
      if (contents.length == 0)
      {
        out.print(
"        <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.NoCertificatesPresent") + "</nobr></td></tr>\n"
        );
      }
      else
      {
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          out.print(
"        <tr>\n"+
"          <td class=\"value\"><input type=\"button\" onclick='Javascript:DeleteCertificate(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")' alt=\""+Messages.getAttributeString(locale,"MeridioConnector.DeleteCert")+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(alias)+"\" value=\"Delete\"/></td>\n"+
"          <td>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)+"</td>\n"+
"        </tr>\n"
          );

          i++;
        }
      }
      out.print(
"      </table>\n"+
"      <input type=\"button\" onclick='Javascript:AddCertificate()' alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.AddCert") + "\" value=\"Add\"/>&nbsp;\n"+
"      Certificate:&nbsp;<input name=\"certificate\" size=\"50\" type=\"file\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for the "Credentials" tab
      out.print(
"<input type=\"hidden\" name=\"userName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userName)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
      );
    }

    // Web Client tab
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.WebClient")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.WebClientProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"webClientProtocol\">\n"+
"        <option value=\"http\" "+((webClientProtocol.equals("http"))?"selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\" "+(webClientProtocol.equals("https")?"selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.WebClientServerName") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"webClientServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webClientServerName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.WebClientServerPort") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"5\" name=\"webClientServerPort\" value=\""+webClientServerPort+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.WebClientServerDocLocation") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"64\" name=\"webClientDocDownloadLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webClientDocDownloadLocation)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for the "Web Client" tab
      out.print(
"<input type=\"hidden\" name=\"webClientProtocol\" value=\""+webClientProtocol+"\"/>\n"+
"<input type=\"hidden\" name=\"webClientServerName\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webClientServerName)+"\"/>\n"+
"<input type=\"hidden\" name=\"webClientServerPort\" value=\""+webClientServerPort+"\"/>\n"+
"<input type=\"hidden\" name=\"webClientDocDownloadLocation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webClientDocDownloadLocation)+"\"/>\n"
      );
    }
  }
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    String dmwsServerProtocol = variableContext.getParameter("dmwsServerProtocol");
    if (dmwsServerProtocol != null)
      parameters.setParameter("DMWSServerProtocol",dmwsServerProtocol);
    String rmwsServerProtocol = variableContext.getParameter("rmwsServerProtocol");
    if (rmwsServerProtocol != null)
      parameters.setParameter("RMWSServerProtocol",rmwsServerProtocol);

    String dmwsServerName = variableContext.getParameter("dmwsServerName");
    if (dmwsServerName != null)
      parameters.setParameter("DMWSServerName",dmwsServerName);
    String rmwsServerName = variableContext.getParameter("rmwsServerName");
    if (rmwsServerName != null)
      parameters.setParameter("RMWSServerName",rmwsServerName);

    String dmwsServerPort = variableContext.getParameter("dmwsServerPort");
    if (dmwsServerPort != null)
    {
      if (dmwsServerPort.length() > 0)
        parameters.setParameter("DMWSServerPort",dmwsServerPort);
      else
        parameters.setParameter("DMWSServerPort",null);
    }
    String rmwsServerPort = variableContext.getParameter("rmwsServerPort");
    if (rmwsServerPort != null)
    {
      if (rmwsServerPort.length() > 0)
        parameters.setParameter("RMWSServerPort",rmwsServerPort);
      else
        parameters.setParameter("RMWSServerPort",null);
    }

    String dmwsLocation = variableContext.getParameter("dmwsLocation");
    if (dmwsLocation != null)
      parameters.setParameter("DMWSLocation",dmwsLocation);
    String rmwsLocation = variableContext.getParameter("rmwsLocation");
    if (rmwsLocation != null)
      parameters.setParameter("RMWSLocation",rmwsLocation);

    String dmwsProxyHost = variableContext.getParameter("dmwsProxyHost");
    if (dmwsProxyHost != null)
      parameters.setParameter("DMWSProxyHost",dmwsProxyHost);
    String rmwsProxyHost = variableContext.getParameter("rmwsProxyHost");
    if (rmwsProxyHost != null)
      parameters.setParameter("RMWSProxyHost",rmwsProxyHost);
    String dmwsProxyPort = variableContext.getParameter("dmwsProxyPort");
    if (dmwsProxyPort != null && dmwsProxyPort.length() > 0)
      parameters.setParameter("DMWSProxyPort",dmwsProxyPort);
    String rmwsProxyPort = variableContext.getParameter("rmwsProxyPort");
    if (rmwsProxyPort != null && rmwsProxyPort.length() > 0)
      parameters.setParameter("RMWSProxyPort",rmwsProxyPort);

    String userName = variableContext.getParameter("userName");
    if (userName != null)
      parameters.setParameter("UserName",userName);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter("Password",variableContext.mapKeyToPassword(password));

    String webClientProtocol = variableContext.getParameter("webClientProtocol");
    if (webClientProtocol != null)
      parameters.setParameter("MeridioWebClientProtocol",webClientProtocol);
    String webClientServerName = variableContext.getParameter("webClientServerName");
    if (webClientServerName != null)
      parameters.setParameter("MeridioWebClientServerName",webClientServerName);
    String webClientServerPort = variableContext.getParameter("webClientServerPort");
    if (webClientServerPort != null)
    {
      if (webClientServerPort.length() > 0)
        parameters.setParameter("MeridioWebClientServerPort",webClientServerPort);
      else
        parameters.setParameter("MeridioWebClientServerPort",null);
    }

    String webClientDocDownloadLocation = variableContext.getParameter("webClientDocDownloadLocation");
    if (webClientDocDownloadLocation != null)
      parameters.setParameter("MeridioWebClientDocDownloadLocation",webClientDocDownloadLocation);

    String configOp = variableContext.getParameter("configop");
    if (configOp != null)
    {
      String keystoreValue;
      if (configOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("keystorealias");
        keystoreValue = parameters.getParameter("MeridioKeystore");
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        parameters.setParameter("MeridioKeystore",mgr.getString());
      }
      else if (configOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("certificate");
        keystoreValue = parameters.getParameter("MeridioKeystore");
        IKeystoreManager mgr;
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try
        {
          mgr.importCertificate(alias,is);
        }
        catch (Throwable e)
        {
          certError = e.getMessage();
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Eat this exception
          }
        }

        if (certError != null)
        {
          // Redirect to error page
          return "Illegal certificate: "+certError;
        }
        parameters.setParameter("MeridioKeystore",mgr.getString());
      }
    }
    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Parameters") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" certificate(s)&gt;</nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }

  // The allowed mime types, which are those that the ingestion API understands
  private static String[] allowedMimeTypes;
  static
  {
    allowedMimeTypes = new String[]
    {
      "application/excel",
      "application/powerpoint",
      "application/ppt",
      "application/rtf",
      "application/xls",
      "text/html",
      "text/rtf",
      "text/pdf",
      "application/x-excel",
      "application/x-msexcel",
      "application/x-mspowerpoint",
      "application/x-msword-doc",
      "application/x-msword",
      "application/x-word",
      "Application/pdf",
      "text/xml",
      "no-type",
      "text/plain",
      "application/pdf",
      "application/x-rtf",
      "application/vnd.ms-excel",
      "application/vnd.ms-pps",
      "application/vnd.ms-powerpoint",
      "application/vnd.ms-word",
      "application/msword",
      "application/msexcel",
      "application/mspowerpoint",
      "application/ms-powerpoint",
      "application/ms-word",
      "application/ms-excel",
      "Adobe",
      "application/Vnd.Ms-Excel",
      "vnd.ms-powerpoint",
      "application/x-pdf",
      "winword",
      "text/richtext",
      "Text",
      "Text/html",
      "application/MSWORD",
      "application/PDF",
      "application/MSEXCEL",
      "application/MSPOWERPOINT"
    };
    java.util.Arrays.sort(allowedMimeTypes);
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"MeridioConnector.SearchPaths"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.ContentTypes"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.Categories"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.DataTypes"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.Security"));
    tabsArray.add(Messages.getString(locale,"MeridioConnector.Metadata"));
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function "+seqPrefix+"SpecDeletePath(n)\n"+
"{\n"+
"  var anchor;\n"+
"  if (n == 0)\n"+
"    anchor = \""+seqPrefix+"SpecPathAdd\";\n"+
"  else\n"+
"    anchor = \""+seqPrefix+"SpecPath_\"+(n-1);\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specpathop_\"+n,\"Delete\",anchor);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddPath()\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specpathop\",\"Add\",\""+seqPrefix+"SpecPathAdd\");\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecDeleteFromPath()\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specpathop\",\"DeleteFromPath\",\""+seqPrefix+"SpecPathAdd\");\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddToPath()\n"+
"{\n"+
"  if (editjob."+seqPrefix+"specpath.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.SelectAFolderOrClassFirst") + "\");\n"+
"    editjob."+seqPrefix+"specpath.focus();\n"+
"  }\n"+
"  else\n"+
"    "+seqPrefix+"SpecOp(\""+seqPrefix+"specpathop\",\"AddToPath\",\""+seqPrefix+"SpecPathAdd\");\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddAccessToken(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.AccessTokenCannotBeNull") + "\");\n"+
"    editjob."+seqPrefix+"spectoken.focus();\n"+
"  }\n"+
"  else\n"+
"    "+seqPrefix+"SpecOp(\""+seqPrefix+"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecDeleteMapping(item, anchorvalue)\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specmappingop_\"+item,\"Delete\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddMapping(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"MeridioConnector.MatchStringCannotBeEmpty") + "\");\n"+
"    editjob."+seqPrefix+"specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specmappingop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.  (actualSequenceNumber, tabName) form a unique tuple within
  *  the job.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int i;
    int k;

    // Search Paths tab
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.SearchPaths")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("SearchPath"))
        {
          // Found a search path.  Not clear from the spec what the attribute is, or whether this is
          // body data, so I'm going to presume it's a path attribute.
          String pathString = sn.getAttributeValue("path");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specpathop_"+Integer.toString(k)+"\" value=\"Continue\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specpath_"+Integer.toString(k)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathString)+"\"/>\n"+
"      <a name=\""+seqPrefix+"SpecPath_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onclick='javascript:"+seqPrefix+"SpecDeletePath("+Integer.toString(k)+");' alt=\""+Messages.getAttributeString(locale,"MeridioConnector.DeletePath")+Integer.toString(k)+"\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathString)+"</nobr></td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.NoPathsSpecified") + "</nobr></td></tr>\n"
        );
      }
      out.print(
"  <tr>\n"+
"    <td class=\"lightseparator\" colspan=\"2\"><input type=\"hidden\" name=\""+seqPrefix+"specpath_total\" value=\""+Integer.toString(k)+"\"/><hr/></td>\n"+
"  </tr>\n"+
"  <tr>\n"
      );
      // The path, and the corresponding IDs
      String pathSoFar = (String)currentContext.get(seqPrefix+"specpath");
      String idsSoFar = (String)currentContext.get(seqPrefix+"specpathids");

      // The type of the object described by the path
      Integer containerType = (Integer)currentContext.get(seqPrefix+"specpathtype");

      if (pathSoFar == null)
        pathSoFar = "/";
      if (idsSoFar == null)
        idsSoFar = "0";
      if (containerType == null)
        containerType = new Integer(org.apache.manifoldcf.crawler.connectors.meridio.MeridioClassContents.CLASS);

      int currentInt = 0;
      if (idsSoFar.length() > 0)
      {
        String[] ids = idsSoFar.split(",");
        currentInt = Integer.parseInt(ids[ids.length-1]);
      }

      // Grab next folder/project list
      try
      {
        org.apache.manifoldcf.crawler.connectors.meridio.MeridioClassContents[] childList;
        if (containerType.intValue() == org.apache.manifoldcf.crawler.connectors.meridio.MeridioClassContents.CLASS)
        {
          childList = getClassOrFolderContents(currentInt);
        }
        else
          childList = new org.apache.manifoldcf.crawler.connectors.meridio.MeridioClassContents[0];
        out.print(
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specpathop\" value=\"Continue\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specpathbase\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specidsbase\" value=\""+idsSoFar+"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"spectype\" value=\""+containerType.toString()+"\"/>\n"+
"      <a name=\""+seqPrefix+"SpecPathAdd\"><input type=\"button\" value=\"Add\" onclick=\"javascript:"+seqPrefix+"SpecAddPath();\" alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.AddPath") + "\"/></a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <nobr>\n"+
"        "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathSoFar)+"\n"
        );
        if (pathSoFar.length() > 1)
        {
          out.print(
"        <input type=\"button\" value=\"-\" onclick=\"javascript:"+seqPrefix+"SpecDeleteFromPath();\" alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.DeleteFromPath") + "\"/>\n"
          );
        }
        if (childList.length > 0)
        {
          out.print(
"        <input type=\"button\" value=\"+\" onclick=\"javascript:"+seqPrefix+"SpecAddToPath();\" alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.AddToPath") + "\"/>\n"+
"        <select name=\""+seqPrefix+"specpath\" size=\"10\">\n"+
"          <option value=\"\" selected=\"\">" + Messages.getBodyString(locale,"MeridioConnector.PickAFolder") + "</option>\n"
          );
          int j = 0;
          while (j < childList.length)
          {
            // The option selected needs to include both the id and the name, since I have no way
            // to get to the name from the id.  So, put the id first, then a semicolon, then the name.
            out.print(
"          <option value=\""+Integer.toString(childList[j].classOrFolderId)+";"+Integer.toString(childList[j].containerType)+";"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childList[j].classOrFolderName)+"\">\n"+
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childList[j].classOrFolderName)+"\n"+
"          </option>\n"
            );
            j++;
          }
          out.print(
"        </select>\n"
          );
        }
        out.print(
"      </nobr>\n"+
"    </td>\n"
        );

      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"MeridioConnector.ServiceInterruption") +org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"2\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }
      out.print(
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // The path tab is hidden; just preserve the contents
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("SearchPath"))
        {
          // Found a search path.  Not clear from the spec what the attribute is, or whether this is
          // body data, so I'm going to presume it's a value attribute.
          String pathString = sn.getAttributeValue("path");
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specpath_"+Integer.toString(k)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathString)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specpath_total\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Content Types tab
    Set<String> mimeTypeMap = new HashSet<String>();
    for (i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("MIMEType"))
      {
        String type = sn.getAttributeValue("type");
        mimeTypeMap.add(type);
      }
    }
    // If there are none selected, then check them all, since no mime types would be nonsensical.
    if (mimeTypeMap.size() == 0)
    {
      for (String allowedMimeType : allowedMimeTypes)
      {
        mimeTypeMap.add(allowedMimeType);
      }
    }

    if (tabName.equals(Messages.getString(locale,"MeridioConnector.ContentTypes")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.MimeTypes") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
      );
      i = 0;
      while (i < allowedMimeTypes.length)
      {
        String mimeType = allowedMimeTypes[i++];
        out.print(
"      <nobr>\n"+
"        <input type=\"checkbox\" name=\""+seqPrefix+"specmimetypes\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeType)+"\" "+(mimeTypeMap.contains(mimeType)?"checked=\"true\"":"")+">\n"+
"          "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mimeType)+"\n"+
"        </input>\n"+
"      </nobr>\n"+
"      <br/>\n"

        );
      }
      out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Tab is not selected.  Submit a separate hidden for each value that was selected before.
      Iterator<String> iter = mimeTypeMap.iterator();
      while (iter.hasNext())
      {
        String mimeType = iter.next();
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmimetypes\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeType)+"\"/>\n"
        );
      }
    }

    // Categories tab

    Set<String> categoriesMap = new HashSet<String>();
    for (i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("SearchCategory"))
      {
        String category = sn.getAttributeValue("category");
        categoriesMap.add(category);
      }
    }

    if (tabName.equals(Messages.getString(locale,"MeridioConnector.Categories")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
      );
      // Grab the list of available categories from Meridio
      try
      {
        String[] categoryList;
        categoryList = getMeridioCategories();
        out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Categories") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
        );
        k = 0;
        while (k < categoryList.length)
        {
          String category = categoryList[k++];
          out.print(
"      <nobr>\n"+
"        <input type=\"checkbox\" name=\""+seqPrefix+"speccategories\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(category)+"\" "+(categoriesMap.contains(category)?"checked=\"true\"":"")+">\n"+
"        "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(category)+"\n"+
"        </input>\n"+
"      </nobr>\n"+
"      <br/>\n"
          );
        }
        out.print(
"    </td>\n"
        );
      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"MeridioConnector.ServiceInterruption") +org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"2\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }

      out.print(
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Tab is not selected.  Submit a separate hidden for each value that was selected before.
      Iterator<String> iter = categoriesMap.iterator();
      while (iter.hasNext())
      {
        String category = iter.next();
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"speccategories\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(category)+"\"/>\n"
        );
      }
    }

    // Data Types tab
    String mode = "DOCUMENTS_AND_RECORDS";
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("SearchOn"))
        mode = sn.getAttributeValue("value");
    }

    if (tabName.equals(Messages.getString(locale,"MeridioConnector.DataTypes")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DataTypesToIngest") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <nobr><input type=\"radio\" name=\""+seqPrefix+"specsearchon\" value=\"DOCUMENTS\" "+(mode.equals("DOCUMENTS")?"checked=\"true\"":"")+"/>" + Messages.getBodyString(locale,"MeridioConnector.Documents") + "</nobr><br/>\n"+
"      <nobr><input type=\"radio\" name=\""+seqPrefix+"specsearchon\" value=\"RECORDS\" "+(mode.equals("RECORDS")?"checked=\"true\"":"")+"/>" + Messages.getBodyString(locale,"MeridioConnector.Records") + "</nobr><br/>\n"+
"      <nobr><input type=\"radio\" name=\""+seqPrefix+"specsearchon\" value=\"DOCUMENTS_AND_RECORDS\" "+(mode.equals("DOCUMENTS_AND_RECORDS")?"checked=\"true\"":"")+"/>" + Messages.getBodyString(locale,"MeridioConnector.DocumentsAndRecords") + "</nobr><br/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specsearchon\" value=\""+mode+"\"/>\n"
      );
    }

    // Security tab

    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }

    if (tabName.equals(Messages.getString(locale,"MeridioConnector.Security")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Security2") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"radio\" name=\""+seqPrefix+"specsecurity\" value=\"on\" "+(securityOn?"checked=\"true\"":"")+" />" + Messages.getBodyString(locale,"MeridioConnector.Enabled") + "&nbsp;\n"+
"      <input type=\"radio\" name=\""+seqPrefix+"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />" + Messages.getBodyString(locale,"MeridioConnector.Disabled") + "\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = seqPrefix+"accessop"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+accessOpName+"\",\"Delete\",\""+seqPrefix+"token_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"MeridioConnector.DeleteToken")+Integer.toString(k)+"\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"MeridioConnector.NoAccessTokensPresent") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"accessop\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" onClick='Javascript:"+seqPrefix+"SpecAddAccessToken(\""+seqPrefix+"token_"+Integer.toString(k+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.AddAccessToken") + "\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specsecurity\" value=\""+(securityOn?"on":"off")+"\"/>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Metadata tab

    // Find the path-value metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.meridio.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.meridio.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }

    boolean allMetadata = false;
    HashMap metadataSelected = new HashMap();
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("ReturnedMetadata"))
      {
        String category = sn.getAttributeValue("category");
        String property = sn.getAttributeValue("property");
        String descriptor;
        if (category == null || category.length() == 0)
          descriptor = property;
        else
          descriptor = category + "." + property;
        metadataSelected.put(descriptor,descriptor);
      }
      else if (sn.getType().equals("AllMetadata"))
      {
        String value = sn.getAttributeValue("value");
        if (value != null && value.equals("true"))
        {
          allMetadata = true;
        }
        else
          allMetadata = false;
      }
    }
    if (tabName.equals(Messages.getString(locale,"MeridioConnector.Metadata")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingop\" value=\"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\">\n"+
"      <nobr>" + Messages.getBodyString(locale,"MeridioConnector.IncludeAllMetadata") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <nobr>\n"+
"        <input type=\"radio\" name=\""+seqPrefix+"allmetadata\" value=\"false\" "+((allMetadata==false)?"checked=\"true\"":"")+">" + Messages.getBodyString(locale,"MeridioConnector.IncludeSpecified") + "</input>\n"+
"        <input type=\"radio\" name=\""+seqPrefix+"allmetadata\" value=\"true\" "+(allMetadata?"checked=\"true\"":"")+">" + Messages.getBodyString(locale,"MeridioConnector.IncludeAll") + "</input>\n"+
"      </nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"
      );
      // get the list of properties from the repository
      try
      {
        String[] propertyList;
        propertyList = getMeridioDocumentProperties();
        out.print(
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Metadata") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"specproperties_edit\" value=\"true\"/>\n"
        );
        k = 0;
        while (k < propertyList.length)
        {
          String descriptor = propertyList[k++];
          out.print(
"      <nobr>\n"+
"        <input type=\"checkbox\" name=\""+seqPrefix+"specproperties\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(descriptor)+"\" "+((metadataSelected.get(descriptor)!=null)?"checked=\"true\"":"")+">\n"+
"          "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(descriptor)+"\n"+
"        </input>\n"+
"      </nobr>\n"+
"      <br/>\n"
          );
        }
        out.print(
"    </td>\n"
        );
      }
      catch (ServiceInterruption e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"4\">" + Messages.getBodyString(locale,"MeridioConnector.ServiceInterruption") + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        out.print(
"    <td class=\"message\" colspan=\"4\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"</td>\n"
        );
      }
      out.print(
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.PathAttributeMetadataName") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\"><nobr>\n"+
"      <input type=\"text\" size=\"16\" name=\""+seqPrefix+"specpathnameattribute\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/></nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"  <tr>\n"+
"    <td class=\"description\"><input type=\"hidden\" name=\""+seqPrefix+"specmappingop_"+Integer.toString(i)+"\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:"+seqPrefix+"SpecDeleteMapping(Integer.toString(i),\""+seqPrefix+"mapping_"+Integer.toString(i)+"\")' alt=\""+Messages.getAttributeString(locale,"MeridioConnector.DeleteMapping")+Integer.toString(i)+"\" value=\"Delete\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\""+seqPrefix+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\""+seqPrefix+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"  </tr>\n"
        );
        i++;
      }
      if (i == 0)
      {
        out.print(
"  <tr><td colspan=\"4\" class=\"message\">" + Messages.getBodyString(locale,"MeridioConnector.NoMappingsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <a name=\""+seqPrefix+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:"+seqPrefix+"SpecAddMapping(\""+seqPrefix+"mapping_"+Integer.toString(i+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"MeridioConnector.AddToMappings") + "\" value=\"Add\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"MeridioConnector.MatchRegexp") + "&nbsp;<input type=\"text\" name=\""+seqPrefix+"specmatch\" size=\"32\" value=\"\"/></td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"MeridioConnector.ReplaceString") + "&nbsp;<input type=\"text\" name=\""+seqPrefix+"specreplace\" size=\"32\" value=\"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specproperties_edit\" value=\"true\"/>\n"
      );
      Iterator iter = metadataSelected.keySet().iterator();
      while (iter.hasNext())
      {
        String descriptor = (String)iter.next();
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specproperties\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(descriptor)+"\"/>\n"
        );
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"allmetadata\" value=\""+(allMetadata?"true":"false")+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specpathnameattribute\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"
        );
        i++;
      }
    }

  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int i;

    // Gather the path names
    String x = variableContext.getParameter(seqPrefix+"specpath_total");
    if (x != null)
    {
      // Get rid of old specpath entries
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("SearchPath"))
          ds.removeChild(i);
        else
          i++;
      }

      // Gather into spec node, paying attention to any delete requests.
      i = 0;
      int count = Integer.parseInt(x);
      while (i < count)
      {
        String path = variableContext.getParameter(seqPrefix+"specpath_"+Integer.toString(i));
        String pathOp = variableContext.getParameter(seqPrefix+"specpathop_"+Integer.toString(i));
        if (pathOp == null || !pathOp.equals("Delete"))
        {
          SpecificationNode sn = new SpecificationNode("SearchPath");
          sn.setAttribute("path",path);
          ds.addChild(ds.getChildCount(),sn);
        }
        i++;
      }


      // Do operation
      x = variableContext.getParameter(seqPrefix+"specpathop");
      if (x != null)
      {
        // Retrieve current state information
        String pathSoFar = variableContext.getParameter(seqPrefix+"specpathbase");
        String idsSoFar = variableContext.getParameter(seqPrefix+"specidsbase");
        Integer containerType = new Integer(variableContext.getParameter(seqPrefix+"spectype"));

        if (x.equals("Add"))
        {
          // Tack the current path onto the specification
          SpecificationNode sn = new SpecificationNode("SearchPath");
          sn.setAttribute("path",pathSoFar);
          ds.addChild(ds.getChildCount(),sn);
          pathSoFar = null;
          idsSoFar = null;
          containerType = null;
        }
        else if (x.equals("AddToPath"))
        {
          String pathField = variableContext.getParameter(seqPrefix+"specpath");
          int index = pathField.indexOf(";");
          int secondIndex = pathField.indexOf(";",index+1);
          pathSoFar = pathSoFar + pathField.substring(secondIndex+1) + "/";
          idsSoFar = idsSoFar + "," + pathField.substring(0,index);
          containerType = new Integer(pathField.substring(index+1,secondIndex));
        }
        else if (x.equals("DeleteFromPath"))
        {
          pathSoFar = pathSoFar.substring(0,pathSoFar.lastIndexOf("/"));
          pathSoFar = pathSoFar.substring(0,pathSoFar.lastIndexOf("/")+1);
          idsSoFar = idsSoFar.substring(0,idsSoFar.lastIndexOf(",")-1);
          containerType = new Integer(org.apache.manifoldcf.crawler.connectors.meridio.MeridioClassContents.CLASS);
        }

        currentContext.save(seqPrefix+"specpath",pathSoFar);
        currentContext.save(seqPrefix+"specpathids",idsSoFar);
        currentContext.save(seqPrefix+"specpathtype",containerType);
      }

    }

    // Searchon parameter
    x = variableContext.getParameter(seqPrefix+"specsearchon");
    if (x != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("SearchOn"))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode newNode = new SpecificationNode("SearchOn");
      newNode.setAttribute("value",x);
      ds.addChild(ds.getChildCount(),newNode);
    }

    // Categories parameter
    String[] y = variableContext.getParameterValues(seqPrefix+"speccategories");
    if (y != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("SearchCategory"))
          ds.removeChild(i);
        else
          i++;
      }

      i = 0;
      while (i < y.length)
      {
        String category = y[i++];
        SpecificationNode newNode = new SpecificationNode("SearchCategory");
        newNode.setAttribute("category",category);
        ds.addChild(ds.getChildCount(),newNode);
      }
    }

    // Properties parameter
    x = variableContext.getParameter(seqPrefix+"specproperties_edit");
    if (x != null && x.length() > 0)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("ReturnedMetadata"))
          ds.removeChild(i);
        else
          i++;
      }

      y = variableContext.getParameterValues(seqPrefix+"specproperties");
      if (y != null)
      {
        i = 0;
        while (i < y.length)
        {
          String descriptor = y[i++];
          SpecificationNode newNode = new SpecificationNode("ReturnedMetadata");
          int index = descriptor.indexOf(".");
          String category;
          String property;
          if (index == -1)
          {
            category = null;
            property = descriptor;
          }
          else
          {
            category = descriptor.substring(0,index);
            property = descriptor.substring(index+1);
          }
          if (category != null)
            newNode.setAttribute("category",category);
          newNode.setAttribute("property",property);
          ds.addChild(ds.getChildCount(),newNode);
        }
      }
    }


    // Mime types parameter
    y = variableContext.getParameterValues(seqPrefix+"specmimetypes");
    if (y != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("MIMEType"))
          ds.removeChild(i);
        else
          i++;
      }

      i = 0;
      while (i < y.length)
      {
        String category = y[i++];
        SpecificationNode newNode = new SpecificationNode("MIMEType");
        newNode.setAttribute("type",category);
        ds.addChild(ds.getChildCount(),newNode);
      }
    }

    x = variableContext.getParameter(seqPrefix+"specsecurity");
    if (x != null)
    {
      // Delete all security entries first
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("security"))
          ds.removeChild(i);
        else
          i++;
      }

      SpecificationNode node = new SpecificationNode("security");
      node.setAttribute("value",x);
      ds.addChild(ds.getChildCount(),node);

    }

    x = variableContext.getParameter(seqPrefix+"tokencount");
    if (x != null)
    {
      // Delete all file specs first
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(x);
      i = 0;
      while (i < accessCount)
      {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = seqPrefix+"accessop"+accessDescription;
        String xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete"))
        {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix+"spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token",accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    x = variableContext.getParameter(seqPrefix+"specpathnameattribute");
    if (x != null && x.length() > 0)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathnameattribute"))
          ds.removeChild(i);
        else
          i++;
      }
      SpecificationNode node = new SpecificationNode("pathnameattribute");
      node.setAttribute("value",x);
      ds.addChild(ds.getChildCount(),node);
    }
    
    x = variableContext.getParameter(seqPrefix+"specmappingcount");
    if (x != null && x.length() > 0)
    {
      // Delete old spec
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("pathmap"))
          ds.removeChild(i);
        else
          i++;
      }

      // Now, go through the data and assemble a new list.
      int mappingCount = Integer.parseInt(x);

      // Gather up these
      i = 0;
      while (i < mappingCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = seqPrefix+"specmappingop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Inserts won't happen until the very end
        String match = variableContext.getParameter(seqPrefix+"specmatch"+pathDescription);
        String replace = variableContext.getParameter(seqPrefix+"specreplace"+pathDescription);
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      // Check for add
      x = variableContext.getParameter(seqPrefix+"specmappingop");
      if (x != null && x.equals("Add"))
      {
        String match = variableContext.getParameter(seqPrefix+"specmatch");
        String replace = variableContext.getParameter(seqPrefix+"specreplace");
        SpecificationNode node = new SpecificationNode("pathmap");
        node.setAttribute("match",match);
        node.setAttribute("replace",replace);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    x = variableContext.getParameter(seqPrefix+"allmetadata");
    if (x != null)
    {
      i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("AllMetadata"))
          ds.removeChild(i);
        else
          i++;
      }
      SpecificationNode node = new SpecificationNode("AllMetadata");
      node.setAttribute("value",x);
      ds.addChild(ds.getChildCount(),node);
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    int i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("SearchPath"))
      {
        if (seenAny == false)
        {
          out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Paths") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"MeridioConnector.NoPathsSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.DataType") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <nobr>\n"
    );
    i = 0;
    String mode = "DOCUMENTS_AND_RECORDS";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("SearchOn"))
        mode = sn.getAttributeValue("value");
    }
    String displayMode;
    if (mode.equals("DOCUMENTS"))
      displayMode = "Documents only";
    else if (mode.equals("RECORDS"))
      displayMode = "Records only";
    else
      displayMode = "Documents and Records";
    out.print(
"        "+displayMode+"\n"+
"      </nobr>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.Categories") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"
    );
    int count = 0;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("SearchCategory"))
        count++;
    }
    String[] sortArray = new String[count];
    count = 0;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("SearchCategory"))
        sortArray[count++] = sn.getAttributeValue("category");
    }
    java.util.Arrays.sort(sortArray);
    i = 0;
    while (i < sortArray.length)
    {
      String category = sortArray[i++];
      out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(category)+"</nobr><br/>\n"
      );
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.MimeTypes") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"
    );
    count = 0;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("MIMEType"))
        count++;
    }
    sortArray = new String[count];
    count = 0;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("MIMEType"))
        sortArray[count++] = sn.getAttributeValue("type");
    }
    java.util.Arrays.sort(sortArray);
    i = 0;
    while (i < sortArray.length)
    {
      String mimeType = sortArray[i++];
      out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mimeType)+"</nobr><br/>\n"
      );
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"
    );
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }
    out.print(
"  <tr>\n"+
"    <td class=\"description\">" + Messages.getBodyString(locale,"MeridioConnector.Security2") + "</td>\n"+
"    <td class=\"value\">"+(securityOn?Messages.getBodyString(locale,"MeridioConnector.Enabled"):Messages.getBodyString(locale,"MeridioConnector.Disabled"))+"</td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr>\n"+
"    <td class=\"description\">" + Messages.getBodyString(locale,"MeridioConnector.AccessTokens") + "</td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"MeridioConnector.NoAccessTokensSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
    );
    count = 0;
    i = 0;
    boolean allMetadata = false;

    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("ReturnedMetadata"))
        count++;
      else if (sn.getType().equals("AllMetadata"))
      {
        String value = sn.getAttributeValue("value");
        if (value != null && value.equals("true"))
        {
          allMetadata = true;
        }
      }
    }

    if (allMetadata)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.MetadataPropertiesToIngest") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\"><nobr><b>" + Messages.getBodyString(locale,"MeridioConnector.AllMetadata") + "</b></nobr></td>\n"
      );
    }
    else if (count > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.MetadataPropertiesToIngest") + "</nobr>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"
      );
      sortArray = new String[count];
      i = 0;
      count = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("ReturnedMetadata"))
        {
          String category = sn.getAttributeValue("category");
          String property = sn.getAttributeValue("property");
          String descriptor;
          if (category == null || category.length() == 0)
            descriptor = property;
          else
            descriptor = category + "." + property;

          sortArray[count++] = descriptor;
        }
      }

      java.util.Arrays.sort(sortArray);  
      i = 0;
      while (i < sortArray.length)
      {
        String descriptor = sortArray[i++];
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(descriptor)+"</nobr><br/>\n"
        );
      }
      out.print(
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.NoMetadataPropertiesToIngest") + "</nobr></td> \n"
      );
    } 
    out.print(
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find the path-name metadata attribute name i = 0;
    String pathNameAttribute = "";
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathnameattribute"))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }
    out.print(
"  <tr>\n"
    );
    if (pathNameAttribute.length() > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.PathNameMetadataAttribute") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathNameAttribute)+"</nobr></td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.NoPathNameMetadataAttributeSpecified") + "</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"\n"+
"  <tr>\n"
    );
    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.meridio.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.meridio.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("pathmap"))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }
    if (matchMap.getMatchCount() > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"MeridioConnector.PathValueMapping") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"        <tr>\n"+
"          <td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"          <td class=\"value\">--></td><td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"        </tr>\n"
        );
        i++;
      }
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\"><nobr>"+Messages.getBodyString(locale,"MeridioConnector.NoMappingsSpecified")+"</nobr></td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"</table>\n"
    );
  }

// Protected methods

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(Specification spec)
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

  private static String [] getMIMETypes
  (
    Specification spec
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
  * @see documentSpecificationSearch Specification docSpec,      long startTime,
  long endTime, int startPositionOfHits, int maxHitsToReturn,
    int restrictDocumentId
  */
  private DMSearchResults documentSpecificationSearch
  (
    Specification docSpec,      // The castor representation of the Document Specification
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
  * @param docSpec                                       the criteria to determine if an object should be returned
  * @param startTime                 the date/time after which the object must have been added (inclusive)
  * @param endTime                   the date/time before which the object must have been added (exclusive)
  * @param startPositionOfHits       the starting position in the hits to begin returning results from
  * @param maxHitsToReturn           the maximum number of hits to return
  * @param restrictDocumentId        if zero, then consider all objects, otherwise if set consider only
  *                                                                      the indicated document identifier - this is used to check if a
  *                                                                      give document id subsequently matches the document specification
  *                                                                      at some point after it was initially returned from the search results
  *
  * @see documentSpecificationSearch Specification docSpec,      long startTime,
  long endTime, int startPositionOfHits, int maxHitsToReturn,
    int [] restrictDocumentId
  */
  private DMSearchResults documentSpecificationSearch
  (
    Specification docSpec,      // The castor representation of the Document Specification
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
  * @param docSpec                                       the criteria to determine if an object should be returned
  * @param startTime                 the date/time after which the object must have been added (inclusive)
  * @param endTime                   the date/time before which the object must have been added (exclusive)
  * @param startPositionOfHits       the starting position in the hits to begin returning results from
  * @param maxHitsToReturn           the maximum number of hits to return
  * @param restrictDocumentId        if the array is empty then return all matching objects, otherwise
  *
  *                                                                      Search results are returned in the SEARCHRESULTS_DOCUMENTS DataTable.
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  protected DMSearchResults documentSpecificationSearch
  (
    Specification docSpec,
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


  /** Returns the categories set up in the Meridio system; these are used by the UI for two
  * purposes
  *
  *              1)      To populate the "SearchCategory"
  *                              Use "getPROP_title()" on the list of CATEGORIES object in
  *                              the return ArrayList
  *              2)  To assist with population of the metadata values to return. The
  *                      available metadata depends on the chosen category
  *
  *@return Sorted array of strings containing the category names
  */
  public String [] getMeridioCategories ()
    throws ManifoldCFException, ServiceInterruption
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" getting categories: "+e.getMessage());
          }
          throw new ManifoldCFException("Unknown http error occurred while getting categories: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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

        throw new ManifoldCFException("Meridio: Got an unknown remote exception getting categories - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      }
      catch (RemoteException remoteException)
      {
        throw new ManifoldCFException("Meridio: A Remote Exception occurred while " +
          "retrieving the Meridio categories: "+remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        throw new ManifoldCFException("Meridio: DataSet Exception occurred retrieving the Meridio categories: "+meridioDataSetException.getMessage(),
          meridioDataSetException);
      }
    }
  }



  public String [] getMeridioDocumentProperties ()
    throws ManifoldCFException, ServiceInterruption
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" getting document properties: "+e.getMessage());
          }
          throw new ManifoldCFException("Unknown http error occurred while getting document properties: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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

        throw new ManifoldCFException("Meridio: Got an unknown remote exception getting document properties - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      }
      catch (RemoteException remoteException)
      {
        throw new ManifoldCFException("Meridio: A Remote Exception occurred while " +
          "retrieving the Meridio document properties: "+remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        throw new ManifoldCFException("Meridio: DataSet Exception occurred retrieving the Meridio document properties: "+meridioDataSetException.getMessage(),
          meridioDataSetException);
      }
    }
  }



  public MeridioClassContents [] getClassOrFolderContents
  (
    int classOrFolderId
  )
    throws ManifoldCFException, ServiceInterruption
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
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" getting class or folder contents: "+e.getMessage());
          }
          throw new ManifoldCFException("Unknown http error occurred while getting class or folder contents: "+e.getMessage(),e);
        }
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
        {
          String exceptionName = e.getFaultString();
          if (exceptionName.equals("java.lang.InterruptedException"))
            throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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

        throw new ManifoldCFException("Meridio: Got an unknown remote exception getting class or folder contents - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      }
      catch (RemoteException remoteException)
      {
        throw new ManifoldCFException("Meridio: A Remote Exception occurred while " +
          "retrieving class or folder contents: "+remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        throw new ManifoldCFException("Meridio: A problem occurred manipulating the Web " +
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



