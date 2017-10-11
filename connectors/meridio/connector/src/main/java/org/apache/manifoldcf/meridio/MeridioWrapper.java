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

package org.apache.manifoldcf.meridio;

import java.net.*;
import java.rmi.RemoteException;
import java.io.*;
import java.util.*;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.parsers.*;
import javax.xml.rpc.Service;

import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.xml.sax.*;
import org.apache.xerces.dom.*;
import org.apache.axis.types.*;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.client.Call;
import org.apache.axis.holders.*;
import org.apache.axis.message.*;
import org.apache.log4j.Logger;

import javax.xml.namespace.QName;

import org.apache.axis.EngineConfiguration;
import org.apache.axis.AxisEngine;
import org.apache.axis.ConfigurationException;
import org.apache.axis.Handler;
import org.apache.axis.WSDDEngineConfiguration;
import org.apache.axis.components.logger.LogFactory;
import org.apache.axis.deployment.wsdd.WSDDDeployment;
import org.apache.axis.deployment.wsdd.WSDDDocument;
import org.apache.axis.deployment.wsdd.WSDDGlobalConfiguration;
import org.apache.axis.encoding.TypeMappingRegistry;
import org.apache.axis.handlers.soap.SOAPService;
import org.apache.axis.utils.Admin;
import org.apache.axis.utils.Messages;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;

import org.apache.manifoldcf.crawler.connectors.meridio.DMDataSet.*;
import com.meridio.www.MeridioDMWS.*;
//import com.meridio.www.MeridioDMWS.ApplyChangesWTResponseApplyChangesWTResult;
import com.meridio.www.MeridioDMWS.holders.*;

import org.apache.manifoldcf.crawler.connectors.meridio.RMDataSet.*;
import com.meridio.www.MeridioRMWS.*;
import com.meridio.www.MeridioRMWS.ApplyChangesWTResponseApplyChangesWTResult;

import org.tempuri.*;
import org.tempuri.holders.*;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;


public class MeridioWrapper
{

  public static final String HTTPCLIENT_PROPERTY = "ManifoldCF_HttpClient";

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

  // This is the local cache
  protected long meridioCategoriesTime                    = -1L;
  protected DMDataSet meridioCategories_                    = null;
  protected final static long meridioCategoriesExpire     = 300000L;

  protected long meridioStaticDataTime                    = -1L;
  protected DMDataSet meridioStaticData_                    = null;
  protected final static long meridioStaticDataExpire     = 300000L;

  // These are the properties that are set in the constructor.  They are kept around so
  // that we can relogin when necessary.
  protected EngineConfiguration engineConfiguration               = null;
  protected Logger oLog                                   = null;
  protected String clientWorkstation                      = null;

  protected HttpClientConnectionManager dmwsConnectionManager = null;
  protected HttpClientConnectionManager rmwsConnectionManager = null;
  protected HttpClientConnectionManager mcwsConnectionManager = null;
  protected HttpClient dmwsHttpClient = null;
  protected HttpClient rmwsHttpClient = null;
  protected HttpClient mcwsHttpClient = null;

  // These are set up during construction of this class.
  protected MeridioDMSoapStub meridioDMWebService_        = null;
  protected MeridioRMSoapStub meridioRMWebService_        = null;
  protected MetaCartaSoapStub meridioMCWS_                = null;

  // This is set up as a result of login.  This MUST NOT be a static because
  // multiple different connections may be using this same class!!!
  protected String            loginToken_                 = null;

  public MeridioDMSoapStub getDMWebService ()
  {
    return meridioDMWebService_;
  }

  public MeridioRMSoapStub getRMWebService ()
  {
    return meridioRMWebService_;
  }

  public MetaCartaSoapStub getMCWSWebService ()
  {
    return meridioMCWS_;
  }

  public String getLoginToken ()
  {
    return loginToken_;
  }

  public void clearCache ()
  {
    meridioCategories_ = null;
    meridioStaticData_ = null;
  }



  /** The Meridio Wrapper constructor that calls the Meridio login method
  *
  *@param log                                     a handle to a Log4j logger
  *@param meridioDmwsUrl          the URL to the Meridio Document Management Web Service
  *@param meridioRmwsUrl          the URL to the Meridio Records Management Web Service
  *@param dmwsProxyHost           the proxy for DMWS, or null if none
  *@param dmwsProxyPort           the proxy port for DMWS, or -1 if default
  *@param rmwsProxyHost           the proxy for RMWS, or null if none
  *@param rmwsProxyPort           the proxy port for RMWS, or -1 if default
  *@param userName                        the username of the user to log in as, must include the Windows, e.g. domain\\user
  *@param password                        the password of the user who is logging in
  *@param clientWorkstation       an identifier for the client workstation, could be the IP address, for auditing purposes
  *@param protocolFactory         the protocol factory object to use for https communication
  *@param engineConfigurationFile the engine configuration object to use to communicate with the web services
  *
  *@throws RemoteException        if an error is encountered logging into Meridio
  */
  public MeridioWrapper
  (
    Logger log,
    URL    meridioDmwsUrl,
    URL    meridioRmwsUrl,
    URL    meridioManifoldCFWSUrl,
    String dmwsProxyHost,
    String dmwsProxyPort,
    String rmwsProxyHost,
    String rmwsProxyPort,
    String mcwsProxyHost,
    String mcwsProxyPort,
    String userName,
    String password,
    String clientWorkstation,
    javax.net.ssl.SSLSocketFactory mySSLFactory,
    Class resourceClass,
    String engineConfigurationFile
  )
    throws RemoteException, NumberFormatException
  {
    // Initialize local instance variables
    oLog = log;
    this.engineConfiguration = new ResourceProvider(resourceClass,engineConfigurationFile);
    this.clientWorkstation = clientWorkstation;

    SSLConnectionSocketFactory myFactory = null;
    if (mySSLFactory != null)
    {
      myFactory = new SSLConnectionSocketFactory(mySSLFactory, new NoopHostnameVerifier());
    }
    else
    {
      myFactory = SSLConnectionSocketFactory.getSocketFactory();
    }

    // Set up the pool.
    // We have a choice: We can either have one httpclient instance, which gets reinitialized for every service
    // it connects with (because each one has a potentially different proxy setup), OR we can have a different
    // httpclient for each service.  The latter approach is obviously the more efficient, so I've chosen to do it
    // that way.

    // Parse the user and password values
    int index = userName.indexOf("\\");
    String domainUser;
    String domain;
    if (index != -1)
    {
      domainUser = userName.substring(index+1);
      domain = userName.substring(0,index);
      if (oLog != null && oLog.isDebugEnabled())
        oLog.debug("Meridio: User is '"+domainUser+"', domain is '"+domain+"'");
    }
    else
    {
      domain = null;
      domainUser = userName;
      if (oLog != null && oLog.isDebugEnabled())
        oLog.debug("Meridio: User is '"+domainUser+"'; there is no domain specified");
    }

    if (oLog != null && oLog.isDebugEnabled())
    {
      if (password != null && password.length() > 0)
        oLog.debug("Meridio: Password exists");
      else
        oLog.debug("Meridio: Password is null");
    }

    int socketTimeout = 900000;
    int connectionTimeout = 300000;

    PoolingHttpClientConnectionManager poolingDmwsConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", myFactory)
        .build());
    poolingDmwsConnectionManager.setDefaultMaxPerRoute(1);
    poolingDmwsConnectionManager.setValidateAfterInactivity(2000);
    poolingDmwsConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
      .setTcpNoDelay(true)
      .setSoTimeout(socketTimeout)
      .build());
    dmwsConnectionManager = poolingDmwsConnectionManager;
    
    PoolingHttpClientConnectionManager poolingRmwsConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", myFactory)
        .build());
    poolingRmwsConnectionManager.setDefaultMaxPerRoute(1);
    poolingRmwsConnectionManager.setValidateAfterInactivity(2000);
    poolingRmwsConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
      .setTcpNoDelay(true)
      .setSoTimeout(socketTimeout)
      .build());
    rmwsConnectionManager = poolingRmwsConnectionManager;
    
    PoolingHttpClientConnectionManager poolingMcwsConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", myFactory)
        .build());
    poolingMcwsConnectionManager.setDefaultMaxPerRoute(1);
    poolingMcwsConnectionManager.setValidateAfterInactivity(2000);
    poolingMcwsConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
      .setTcpNoDelay(true)
      .setSoTimeout(socketTimeout)
      .build());
    mcwsConnectionManager = poolingMcwsConnectionManager;

    // Initialize the three httpclient objects

    CredentialsProvider dmwsCredentialsProvider = new BasicCredentialsProvider();
    CredentialsProvider rmwsCredentialsProvider = new BasicCredentialsProvider();

    RequestConfig.Builder dmwsRequestBuilder = RequestConfig.custom()
      .setCircularRedirectsAllowed(true)
      .setSocketTimeout(socketTimeout)
      .setExpectContinueEnabled(false)
      .setConnectTimeout(connectionTimeout)
      .setConnectionRequestTimeout(socketTimeout);
    RequestConfig.Builder rmwsRequestBuilder = RequestConfig.custom()
      .setCircularRedirectsAllowed(true)
      .setSocketTimeout(socketTimeout)
      .setExpectContinueEnabled(true)
      .setConnectTimeout(connectionTimeout)
      .setConnectionRequestTimeout(socketTimeout);

    // Set up credentials
    if (domainUser != null)
    {
      dmwsCredentialsProvider.setCredentials(
        new AuthScope(meridioDmwsUrl.getHost(),meridioDmwsUrl.getPort()),
        new NTCredentials(domainUser, password, currentHost, domain));
      rmwsCredentialsProvider.setCredentials(
        new AuthScope(meridioRmwsUrl.getHost(),meridioRmwsUrl.getPort()),
        new NTCredentials(domainUser, password, currentHost, domain));
    }
    
    // Initialize DMWS proxy
    if (dmwsProxyHost != null && dmwsProxyHost.length() > 0)
    {
      int port = (dmwsProxyPort == null || dmwsProxyPort.length() == 0)?8080:Integer.parseInt(dmwsProxyPort);
      // Configure proxy authentication
      if (domainUser != null && domainUser.length() > 0)
      {
        dmwsCredentialsProvider.setCredentials(
          new AuthScope(dmwsProxyHost, port),
          new NTCredentials(domainUser, password, currentHost, domain));
      }

      HttpHost proxy = new HttpHost(dmwsProxyHost, port);
      dmwsRequestBuilder.setProxy(proxy);
    }

    // Initialize RMWS proxy
    if (rmwsProxyHost != null && rmwsProxyHost.length() > 0)
    {
      int port = (rmwsProxyPort == null || rmwsProxyPort.length() == 0)?8080:Integer.parseInt(rmwsProxyPort);
      // Configure proxy authentication
      if (domainUser != null && domainUser.length() > 0)
      {
        rmwsCredentialsProvider.setCredentials(
          new AuthScope(rmwsProxyHost, port),
          new NTCredentials(domainUser, password, currentHost, domain));
      }

      HttpHost proxy = new HttpHost(rmwsProxyHost, port);
      rmwsRequestBuilder.setProxy(proxy);
    }

    dmwsHttpClient = HttpClients.custom()
      .setConnectionManager(dmwsConnectionManager)
      .disableAutomaticRetries()
      .setDefaultRequestConfig(dmwsRequestBuilder.build())
      .setDefaultCredentialsProvider(dmwsCredentialsProvider)
      .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
      .setRedirectStrategy(new LaxRedirectStrategy())
      .build();
    
    rmwsHttpClient = HttpClients.custom()
      .setConnectionManager(rmwsConnectionManager)
      .disableAutomaticRetries()
      .setDefaultRequestConfig(rmwsRequestBuilder.build())
      .setDefaultCredentialsProvider(rmwsCredentialsProvider)
      .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
      .setRedirectStrategy(new LaxRedirectStrategy())
      .build();

    if (meridioManifoldCFWSUrl != null)
    {
      CredentialsProvider mcwsCredentialsProvider = new BasicCredentialsProvider();

      RequestConfig.Builder mcwsRequestBuilder = RequestConfig.custom()
        .setCircularRedirectsAllowed(true)
        .setSocketTimeout(socketTimeout)
        .setExpectContinueEnabled(true)
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(socketTimeout);

      if (domainUser != null)
      {
        mcwsCredentialsProvider.setCredentials(
          new AuthScope(meridioManifoldCFWSUrl.getHost(),meridioManifoldCFWSUrl.getPort()),
          new NTCredentials(domainUser, password, currentHost, domain));
      }

      // Initialize MCWS proxy
      if (mcwsProxyHost != null && mcwsProxyHost.length() > 0)
      {
        int port = (mcwsProxyPort == null || mcwsProxyPort.length() == 0)?8080:Integer.parseInt(mcwsProxyPort);
        // Configure proxy authentication
        if (domainUser != null && domainUser.length() > 0)
        {
          mcwsCredentialsProvider.setCredentials(
            new AuthScope(mcwsProxyHost, port),
            new NTCredentials(domainUser, password, currentHost, domain));
        }

        HttpHost proxy = new HttpHost(mcwsProxyHost, port);
        mcwsRequestBuilder.setProxy(proxy);
      }
      
      mcwsHttpClient = HttpClients.custom()
        .setConnectionManager(mcwsConnectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(mcwsRequestBuilder.build())
        .setDefaultCredentialsProvider(mcwsCredentialsProvider)
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .setRedirectStrategy(new LaxRedirectStrategy())
        .build();
    }

    // Set up the stub handles
    /*=================================================================
    * Get a handle to the DMWS
    *================================================================*/
    MeridioDMLocator meridioDMLocator = new MeridioDMLocator(engineConfiguration);
    MeridioDMSoapStub meridioDMWebService = new MeridioDMSoapStub(meridioDmwsUrl, meridioDMLocator);

    meridioDMWebService.setPortName(meridioDMLocator.getMeridioDMSoapWSDDServiceName());
    meridioDMWebService.setUsername(userName);
    meridioDMWebService.setPassword(password);
    meridioDMWebService._setProperty( HTTPCLIENT_PROPERTY, dmwsHttpClient);

    meridioDMWebService_ = meridioDMWebService;

    /*=================================================================
    * Get a handle to the RMWS
    *================================================================*/
    MeridioRMLocator meridioRMLocator = new MeridioRMLocator(engineConfiguration);
    MeridioRMSoapStub meridioRMWebService = new MeridioRMSoapStub(meridioRmwsUrl, meridioRMLocator);

    meridioRMWebService.setPortName(meridioRMLocator.getMeridioRMSoapWSDDServiceName());
    meridioRMWebService.setUsername(userName);
    meridioRMWebService.setPassword(password);
    meridioRMWebService._setProperty( HTTPCLIENT_PROPERTY, rmwsHttpClient);

    meridioRMWebService_ = meridioRMWebService;

    /*=================================================================
    * Get a handle to the MeridioMetaCarta Web Service
    *================================================================*/
    if (meridioManifoldCFWSUrl != null)
    {
      MetaCartaLocator meridioMCWS = new MetaCartaLocator(engineConfiguration);
      Service McWsService = null;
      MetaCartaSoapStub meridioMetaCartaWebService = new MetaCartaSoapStub(meridioManifoldCFWSUrl, McWsService);

      meridioMetaCartaWebService.setPortName(meridioMCWS.getMetaCartaSoapWSDDServiceName());
      meridioMetaCartaWebService.setUsername(userName);
      meridioMetaCartaWebService.setPassword(password);
      meridioMetaCartaWebService._setProperty( HTTPCLIENT_PROPERTY, mcwsHttpClient );
      
      meridioMCWS_ = meridioMetaCartaWebService;
    }


    this.loginUnified();
  }


  /** Checks if login needed, and if so performs a login to Meridio, using integrated Windows NT authentication (NTLM)
  *
  *
  *@throws RemoteException        if an error is encountered locating the web services, or logging in
  */
  public void loginUnified
  (
    )
    throws RemoteException
  {
    if (loginToken_ == null)
    {
      if (oLog != null) oLog.debug("Meridio: Calling Meridio Login Web Service");
        String loginToken = meridioDMWebService_.loginUnified(clientWorkstation);
      // String loginToken = meridioDMWebService_.login("Admin","Admin",clientWorkstation);
      if (oLog != null && oLog.isDebugEnabled())
        oLog.debug("Meridio: Successfully logged in - Login Token is '" + loginToken + "'");

      loginToken_          = loginToken;
      if (oLog != null) oLog.debug("Meridio: Exiting login method");
    }
  }



  /** Logs out from Meridio
  *
  *@throws RemoteException        if an error is encountered calling the Meridio Web Service logout method
  */
  public void logout      ()
    throws RemoteException
  {
    if (loginToken_ != null)
    {
      try
      {
        if (oLog != null) oLog.debug("Meridio: Logging out of Meridio");
          meridioDMWebService_.logout(loginToken_);
        if (oLog != null) oLog.debug("Meridio: Successfully logged out of Meridio");
      }
      finally
      {
        loginToken_ = null;
        dmwsConnectionManager.shutdown();
        dmwsConnectionManager = null;
        rmwsConnectionManager.shutdown();
        rmwsConnectionManager = null;
        mcwsConnectionManager.shutdown();
        mcwsConnectionManager = null;
      }
    }
  }

  /** Given a user id, find all the groups that it belongs to (including parents of parents)
  */
  public GroupResult [] getUsersGroups
  (
    int userId
  )
    throws RemoteException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getUsersGroups method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {

        ArrayOfGroupResultHolder groupArray = new  ArrayOfGroupResultHolder();
        javax.xml.rpc.holders.BooleanHolder boolHolder = new javax.xml.rpc.holders.BooleanHolder();

        meridioMCWS_.getUsersGroups(loginToken_, userId, boolHolder, groupArray);

        if (oLog != null) oLog.debug("Success? <" + boolHolder.value + ">");

        if (oLog != null) oLog.debug("Meridio: Exiting getUsersGroups method");

        if (boolHolder.value)
        {
          return groupArray.value;
        }

        return null;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getUsersGroups method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }


  /** This method is a wrapper around the Meridio "GetStaticData" Web Service Method
  *
  * The following is the text from the Meridio Web Services Programming Guide
  *
  * Returns data that may be considered as static in a typical Meridio system, and that is not
  * subject to access control. Such data is therefore amenable to caching at Application
  * scope.
  *
  * The method can be used to retrieve all property definitions (fixed and custom) in the
  * Meridio system, for all Meridio object types supported via the Meridio SOAP API,
  * independently of the permissions of the session user on the categories holding the
  * property definitions. Lookups, policies, rendition definitions and system information
  * (e.g. the name of the target Meridio system) can also be retrieved, depending on the
  * value of the relevant Boolean input argument.
  *
  * The DataTables returned (in the DataSet) are as follows:
  *
  * PROPERTYDEFS
  * LOOKUPS
  * LOOKUP_VALUES
  * POLICIES
  * RENDITIONDEFS
  * SYSTEMINFO
  *
  *@return                   the DM DataSet with the appropriate elements (see above) populated
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMDataSet getStaticData ()
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getStaticData method.");

      long currentTime = System.currentTimeMillis();
    if (meridioStaticData_ != null && meridioStaticDataTime < currentTime)
      meridioStaticData_ = null;

    if (meridioStaticData_ == null)
    {
      boolean loginTried = false;
      while (true)
      {
        loginUnified();
        try
        {
          GetStaticDataWTResponseGetStaticDataWTResult staticDataResult = null;
          staticDataResult =
            meridioDMWebService_.getStaticData(loginToken_, true,
            true, true, true, true);

          meridioStaticData_ = getDMDataSet(staticDataResult.get_any());
          meridioStaticDataTime = currentTime + meridioStaticDataExpire;
          break;
        }
        catch (RemoteException e)
        {
          if (loginTried == false)
          {
            if (oLog != null) oLog.debug("Meridio: Retrying getStaticData method with login.");
              loginToken_ = null;
            loginTried = true;
            continue;
          }
          throw e;
        }
      }
    }
    if (oLog != null) oLog.debug("Meridio: Exiting getStaticData method.");
      return meridioStaticData_;

  }



  /** This method is a wrapper around the Meridio "GetConfiguration" RM Web Service Method
  *
  * The following is the text from the Meridio Web Services Programming Guide
  *
  * Returns a DataSet containing information about all system configuration settings.
  *
  * The DataTables returned are as follows:
  *
  * Table                Comment
  * rm2Config    Contains a row for each system configuration setting.
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public RMDataSet getConfiguration       ()
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getConfiguration method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetConfigurationResponseGetConfigurationResult configurationResult = null;
        configurationResult = meridioRMWebService_.getConfiguration();

        RMDataSet ds = getRMDataSet(configurationResult.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getConfiguration method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getConfiguration method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /** This method is a wrapper around the Meridio "GetPropertyDefs" RM Web Service Method
  *
  * The following is the text from the Meridio Web Services Programming Guide
  *
  * Returns a DataSet containing information about all fixed property definitions for objects
  * in the RM Web Service.
  *
  * The DataTables returned are as follows:
  *
  * Table                        Comment
  * rm2PropertyDef       Contains a row for each fixed property definition for each object in the
  *                                      RM Web Service.
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public RMDataSet getPropertyDefs ()
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getPropertyDefs method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetPropertyDefsResponseGetPropertyDefsResult configurationResult = null;
        configurationResult = meridioRMWebService_.getPropertyDefs();

        RMDataSet ds = getRMDataSet(configurationResult.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getPropertyDefs method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getPropertyDefs method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }


  /** This method is a wrapper around the Meridio "GetCategories" Web Service Method
  *
  * The following is the text from the Meridio Web Services Programming Guide
  *
  * Retrieves all categories accessible by the user.
  *
  * The DataTables returned are as described in the following table.
  *
  * DataTable            Comments
  * CATEGORIES           contains a row for each category in the system, subject to access control.
  * CATEGORIES_EXTRA contains a row for each category in the system, subject to access control.
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMDataSet getCategories ()
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getCategories method.");

      long currentTime = System.currentTimeMillis();
    if (meridioCategories_ != null && meridioCategoriesTime < currentTime)
      meridioCategories_ = null;

    if (meridioCategories_ == null)
    {
      boolean loginTried = false;
      while (true)
      {
        loginUnified();
        try
        {
          GetCategoriesWTResponseGetCategoriesWTResult staticDataResult = null;
          staticDataResult =
            meridioDMWebService_.getCategories(loginToken_);

          meridioCategories_ = getDMDataSet(staticDataResult.get_any());
          meridioCategoriesTime = currentTime + meridioCategoriesExpire;
          break;
        }
        catch (RemoteException e)
        {
          if (loginTried == false)
          {
            if (oLog != null) oLog.debug("Meridio: Retrying getCategories method with login.");
              loginToken_ = null;
            loginTried = true;
            continue;
          }
          throw e;
        }
      }

    }
    if (oLog != null) oLog.debug("Meridio: Exiting getCategories method.");
      return meridioCategories_;
  }



  /** Returns a dataset containing information about the classes or folders within the
  * class specified by the "classOrFolderId" parameter
  *
  * @param classOrFolderId                       the Identifier of the class or folder for which the contents
  *                                                                      are required. 0 indicated the root of the FilePlan
  * @param getPropDefs
  * @param getActivities
  * @param getCustomProperties
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public RMDataSet getClassContents
  (
    int      classOrFolderId,
    boolean  getPropDefs,
    boolean  getActivities,
    boolean  getCustomProperties
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getClassContents method. Class/folder ID <" +
      classOrFolderId + ">");

    boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetClassContentsWTResponseGetClassContentsWTResult classContents = null;
        classContents = meridioRMWebService_.getClassContents(loginToken_,
          classOrFolderId, getPropDefs, getActivities, getCustomProperties);

        RMDataSet ds = getRMDataSet(classContents.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getClassContents method");

          return ds;

      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getClassContents method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /** Given a string representing the path to a class or folder within the FilePlan, this
  * method returns the identifier of the matching class (if any). This method matches
  * a path from the root of the FilePlan
  *
  * @param classOrFolder         is the path to the class or folder to be matched, for example
  *                          /Finances/Audit, Finances/Audit/, or //Finances///Audit//
  *                          will all match the class or folder "Audit" that exists in the
  *                          "Finances" class which is located directly under the root of
  *                          the FilePlan. The forward-slash "/" must be used.
  * @return                              an integer representing the class or folder id, or zero if not found
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public int findClassOrFolder
  (
    String classOrFolder
  )
    throws RemoteException, MeridioDataSetException
  {
    return this.findClassOrFolder(classOrFolder, 0);
  }


  /** Given a string representing the path to a class or folder within the FilePlan, this
  * method returns the identifier of the matching class (if any). This method matches
  * a path from the root of the FilePlan
  *
  * @param classOrFolder                 is the path to the class or folder to be matched, for example
  *                              /Finances/Audit, Finances/Audit/, or //Finances///Audit//
  * @param startClassOrFolder    the id of the class of folder to start searching at (zero for root)
  * @return                                      an integer representing the class or folder id, or zero if not found
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public int findClassOrFolder
  (
    String classOrFolder,
    int    startClassOrFolder
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered findClassOrFolder method. Path = <" + classOrFolder + "> " +
      "Starting class/folder ID <" + startClassOrFolder + ">");

    if (classOrFolder == null)
    {
      if (oLog != null) oLog.debug("Meridio: Class/folder path provided is null, so returning top level FilePlan");
        return 0;
    }

    String [] classTokens = classOrFolder.split("/");

    if (classTokens.length == 0)
    {
      if (oLog != null) oLog.debug("Meridio: No classes in path, so returning top level FilePlan");
      if (oLog != null) oLog.debug("Meridio: Exiting findClassOrFolder method");
        return 0;
    }

    int currentClassOrFolder = startClassOrFolder;
    boolean isMatchFound     = false;
    for (int i=0; i<classTokens.length; i++)
    {
      if (classTokens[i].length() == 0)
      {
        continue;
      }

      RMDataSet ds = this.getClassContents(currentClassOrFolder,
        false, false, false);

      Rm2vClass [] classes = ds.getRm2vClass();
      isMatchFound = false;
      for (int j=0; j<classes.length; j++)
      {
        if (oLog != null && oLog.isDebugEnabled())
          oLog.debug("Meridio: Comparing [" + classTokens[i] +"] with [" + classes[j].getName() + "]");
        if (classTokens[i].compareTo(classes[j].getName()) == 0)
        {
          currentClassOrFolder = classes[j].getId();
          isMatchFound = true;
          break;
        }
      }
      if (!isMatchFound)
      {
        /*=============================================================
        * If we didn't find a match and we are at the last node then
        * check to see if it is a folder
        *============================================================*/
        if (i == classTokens.length - 1)
        {
          Rm2vFolder [] folders = ds.getRm2vFolder();
          for (int j=0; j<folders.length; j++)
          {
            if (classTokens[i].compareTo(folders[j].getName()) == 0)
            {
              currentClassOrFolder = folders[j].getId();
              isMatchFound = true;
              break;
            }
          }
        }
        else
        {
          break;
        }
      }
    }

    if (isMatchFound)
    {
      if (oLog != null && oLog.isDebugEnabled())
        oLog.debug("Meridio: Found match: " + currentClassOrFolder);
      if (oLog != null) oLog.debug("Meridio: Exiting findClassOrFolder method");
        return currentClassOrFolder;
    }
    else
    {
      if (oLog != null) oLog.debug("Meridio: No match found");
      if (oLog != null) oLog.debug("Meridio: Exiting findClassOrFolder method");
        return -1;
    }
  }


  /**
  * This method is a wrapper around the Meridio "SearchDocuments" Web Service Method
  *
  * The following is the text is paraphrased from the Meridio Web Services Programming Guide
  *
  *@param searchInfo                                     contains the search definition and may also specify
  *                                                                      rendition types, containers, keywords and result definitions
  *                                                              Tables that may be included in searchInfo are:
  *                                                                              CONTENT_OPS
  *                                                                              CONTENT_TERMS
  *                                                                              PROPERTY_OPS
  *                                                                              PROPERTY_TERMS
  *                                                                              RESULTDEFS
  *                                                                              SEARCH_CONTAINERS
  *                                                                              SEARCH_KEYWORDS
  *                                                                              SEARCH_RENDITIONDEFS
  *@param maxHitsToReturn                        controls the maximum number of hits to return on this call
  *@param startPositionOfHits            controls what position to start from, 1 is the first result
  *@param permissionFilter                       documents for which the current user's permission is
  *                                                              lower than permissionFilter will be excluded from the search results
  *@param searchAll                                      indicates whether the search should return hits on all versions of
  *                                                              documents or on the latest versions only. If searchAll is true then
  *                                                              hits on all versions will be returned; if false then hits on the latest
  *                                                              versions only will be returned
  *@param scope                                          enditions of the specified type(s) will be included in the search, as long
  *                                                                      as renditions are included in the search scope
  *@param useThesaurus                           controls use of word synonyms in content searches
  *@param searchChildren                         specifies whether subcontainers are searched within any containers
  *                                                              specified in searchInfo. This argument is ignored if the search is not
  *                                                              restricted to specific containers.
  *@param searchKeywordHierarchy         Only documents associated with the specified keywords will be returned,
  *                                                              depending on the operator used to logically combine them (keywordOperator)
  *                                                              and whether all keywords under the specified keywords are to be considered
  *                                                              (searchKeywordHierarchy). However, if no keywords are specified in
  *                                                              searchInfo then searching will ignore keyword associations.
  *@param keywordOperator                        see searchKeywordHierarchy
  *@return                                                       object containing the eturns the total number of hits found in Meridio
  *                                                                      for the search, the actual number of hits returned by the current
  *                                                                      search call, and the results of the search.
  *                                                                      Search results are returned in the SEARCHRESULTS_DOCUMENTS DataTable.
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMSearchResults searchDocuments
  (
    DMDataSet searchInfo,
    int maxHitsToReturn,
    int startPositionOfHits,
    DmPermission permissionFilter,
    boolean searchAll,
    DmSearchScope scope,
    boolean useThesaurus,
    boolean searchChildren,
    boolean searchKeywordHierarchy,
    DmLogicalOp keywordOperator
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null && oLog.isDebugEnabled())
      oLog.debug("Meridio: Entering searchDocuments method\n" +
      "\tMax Hits to Return <" + maxHitsToReturn + "> " +
      "\tStart Position <" + startPositionOfHits + "> " +
      "\tPermission Folder <" + permissionFilter + "> " +
      "\tSearch All <" + searchAll + "> " +
      "\tSearch Scope <" + scope + "> " +
      "\tUse Thesaurus <" + useThesaurus + "> " +
      "\tSearch Children <" + searchChildren + "> " +
      "\tSearch Keyword Hierarchy <" + searchKeywordHierarchy + "> " +
      "\tKeyword Operator <" + keywordOperator + ">");

    boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {

        UnsignedIntHolder totalHitsCount    = new UnsignedIntHolder();
        UnsignedIntHolder returnedHitsCount = new UnsignedIntHolder();
        SearchDocumentsWTSearchInfo searchDocumentsInfo = new SearchDocumentsWTSearchInfo();
        SearchDocumentsWTResponseSearchDocumentsWTResultHolder searchDocumentsWTResult =
          new SearchDocumentsWTResponseSearchDocumentsWTResultHolder();

        searchDocumentsInfo.set_any(getSOAPMessage(searchInfo));

        meridioDMWebService_.searchDocuments(loginToken_, searchDocumentsInfo,
          new UnsignedInt(maxHitsToReturn), new UnsignedInt(startPositionOfHits),
          permissionFilter, searchAll, scope, useThesaurus, searchChildren,
          searchKeywordHierarchy, keywordOperator, searchDocumentsWTResult,
          totalHitsCount, returnedHitsCount);

        DMSearchResults dsDMSearchResults = new DMSearchResults();
        dsDMSearchResults.dsDM = getDMDataSet(searchDocumentsWTResult.value.get_any());
        dsDMSearchResults.returnedHitsCount = returnedHitsCount.value.intValue();
        dsDMSearchResults.totalHitsCount = totalHitsCount.value.intValue();

        if (oLog != null && oLog.isDebugEnabled())
          oLog.debug("Meridio: Returned Hits <" + dsDMSearchResults.returnedHitsCount + "> " +
          "Total Hits <" + dsDMSearchResults.totalHitsCount +">");

        if (oLog != null) oLog.debug("Meridio: Exiting searchDocuments method.");

          return dsDMSearchResults;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying searchDocuments method with login.");

            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }


  /**
  * This method is a wrapper around the Meridio "SearchRecords" Web Service Method
  *
  * @see SearchDocuments DMDataSet searchInfo, int maxHitsToReturn, int startPositionOfHits,     DmPermission permissionFilter,
  boolean searchAll, DmSearchScope scope, boolean useThesaurus, boolean searchChildren,
    boolean searchKeywordHierarchy, DmLogicalOp keywordOperator
  */
  public DMSearchResults searchRecords
  (
    DMDataSet searchInfo,
    int maxHitsToReturn,
    int startPositionOfHits,
    DmPermission permissionFilter,
    boolean searchAll,
    DmSearchScope scope,
    boolean useThesaurus,
    boolean searchChildren,
    boolean searchKeywordHierarchy,
    DmLogicalOp keywordOperator
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null && oLog.isDebugEnabled())
      oLog.debug("Meridio: Entering searchRecords method\n" +
      "\n\tMax Hits to Return <" + maxHitsToReturn + "> " +
      "\n\tStart Position <" + startPositionOfHits + "> " +
      "\n\tPermission Folder <" + permissionFilter + "> " +
      "\n\tSearch All <" + searchAll + "> " +
      "\n\tSearch Scope <" + scope + "> " +
      "\n\tUse Thesaurus <" + useThesaurus + "> " +
      "\n\tSearch Children <" + searchChildren + "> " +
      "\n\tSearch Keyword Hierarchy <" + searchKeywordHierarchy + "> " +
      "\n\tKeyword Operator <" + keywordOperator + ">");

    boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        UnsignedIntHolder totalHitsCount    = new UnsignedIntHolder();
        UnsignedIntHolder returnedHitsCount = new UnsignedIntHolder();
        SearchRecordsWTSearchInfo searchDocumentsInfo = new SearchRecordsWTSearchInfo();
        SearchRecordsWTResponseSearchRecordsWTResultHolder searchRecordsWTResult =
          new SearchRecordsWTResponseSearchRecordsWTResultHolder();

        searchDocumentsInfo.set_any(getSOAPMessage(searchInfo));

        meridioDMWebService_.searchRecords(loginToken_, searchDocumentsInfo,
          new UnsignedInt(maxHitsToReturn), new UnsignedInt(startPositionOfHits),
          permissionFilter, searchAll, scope, useThesaurus, searchChildren,
          searchKeywordHierarchy, keywordOperator, searchRecordsWTResult,
          totalHitsCount, returnedHitsCount);

        DMSearchResults dsDMSearchResults = new DMSearchResults();
        dsDMSearchResults.dsDM = getDMDataSet(searchRecordsWTResult.value.get_any());
        dsDMSearchResults.returnedHitsCount = returnedHitsCount.value.intValue();
        dsDMSearchResults.totalHitsCount = totalHitsCount.value.intValue();

        if (oLog != null) oLog.debug("Meridio: Exiting searchRecords method.");

          return dsDMSearchResults;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying searchRecords method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /**
  * This method is a wrapper around the Meridio "SearchUsers" Web Service Method
  *
  *@param  searchInfo  contains the search definition and may also specify groups
  *                    and result definitions. See "Search Expressions" on page 23 for an overview of how a
  *                    search expression can be recorded in searchInfo.
  *
  *                    Only groups that belong to the groups specified in searchInfo will be included in
  *                    the search results. However, if no groups are specified in searchInfo then
  *                    searching will not be restricted to any specific groups, and will instead take place
  *                    across all groups in the Meridio system.
  *
  *                    The result definitions specified by searchInfo will be applied when returning
  *                    property search results.
  *
  *@param maxHitsToReturn      maxHitsToReturn may be NULL in which case the default limit is 100 hits.
  *@param startPositionOfHits  may be NULL in which case the default start position is 1.
  *@param searchChildren       specifies whether subgroups are searched within any groups specified in searchInfo.
  *                            This argument is ignored if the search is not restricted to specific groups.
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMSearchResults searchUsers
  (
    DMDataSet searchInfo,
    int maxHitsToReturn,
    int startPositionOfHits,
    boolean searchChildren
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null && oLog.isDebugEnabled())
      oLog.debug("Meridio: Entering searchUsers method\n" +
      "\n\tMax Hits to Return <" + maxHitsToReturn + "> " +
      "\n\tStart Position <" + startPositionOfHits + "> " +
      "\n\tSearch Children <" + searchChildren + ">");

    boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        UnsignedIntHolder totalHitsCount    = new UnsignedIntHolder();
        UnsignedIntHolder returnedHitsCount = new UnsignedIntHolder();
        SearchUsersWTSearchInfo searchUsersInfo = new SearchUsersWTSearchInfo();
        SearchUsersWTResponseSearchUsersWTResultHolder searchUsersWTResult =
          new SearchUsersWTResponseSearchUsersWTResultHolder();

        searchUsersInfo.set_any(getSOAPMessage(searchInfo));

        meridioDMWebService_.searchUsers(loginToken_, searchUsersInfo,
          new UnsignedInt(maxHitsToReturn), new UnsignedInt(startPositionOfHits),
          searchChildren, searchUsersWTResult, totalHitsCount, returnedHitsCount);

        DMSearchResults dsDMSearchResults = new DMSearchResults();
        dsDMSearchResults.dsDM = getDMDataSet(searchUsersWTResult.value.get_any());
        dsDMSearchResults.returnedHitsCount = returnedHitsCount.value.intValue();
        dsDMSearchResults.totalHitsCount = totalHitsCount.value.intValue();

        if (oLog != null) oLog.debug("Meridio: Exiting searchUsers method.");

          return dsDMSearchResults;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying searchUsers method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  public long getUserIdFromName
  (
    String userName
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null && oLog.isDebugEnabled())
      oLog.debug("Meridio: Entering getUserIdFromName method\n" +
      "User name is <" + userName + ">");

    //=============================================================
    // Create the search criteria to determine the user id
    //
    // Create a dataset and a property terms row (search for
    // user properties
    //=============================================================
    DMDataSet dsSearchCriteria = new DMDataSet();
    PROPERTY_TERMS drUserSearch = new PROPERTY_TERMS();

    //=============================================================
    // Populate the property row with the search criteria
    //
    // Find the user that matches based on their name
    //=============================================================
    drUserSearch.setId(1);
    drUserSearch.setTermType(new Short("0").shortValue());          // STRING
    drUserSearch.setPropertyName("PROP_loginName");
    drUserSearch.setStr_relation(new Short("0").shortValue());      // IS
    drUserSearch.setStr_value(userName);
    drUserSearch.setParentId(1);
    dsSearchCriteria.addPROPERTY_TERMS(drUserSearch);

    //=============================================================
    // Only return the user account if it is enabled
    //=============================================================
    PROPERTY_TERMS drUserEnabled = new PROPERTY_TERMS();
    drUserEnabled.setId(2);
    drUserEnabled.setTermType(new Short("1").shortValue());         // NUMBER
    drUserEnabled.setPropertyName("PROP_enabled");
    drUserEnabled.setNum_relation(new Short("0").shortValue());     // EQUAL
    drUserEnabled.setNum_value(1);
    drUserEnabled.setParentId(1);
    dsSearchCriteria.addPROPERTY_TERMS(drUserEnabled);

    //=============================================================
    // 'AND' the two terms together
    //=============================================================
    PROPERTY_OPS drPropertyOps = new PROPERTY_OPS();
    drPropertyOps.setId(1);
    drPropertyOps.setOperator(new Short("0").shortValue());   //AND
    dsSearchCriteria.addPROPERTY_OPS(drPropertyOps);

    //=============================================================
    // Create the result definitions criteria: just the user ID is
    // required
    //=============================================================
    RESULTDEFS drResultDefs = new RESULTDEFS();
    drResultDefs.setPropertyName("PROP_id");
    drResultDefs.setIsVersionProperty(false);
    dsSearchCriteria.addRESULTDEFS(drResultDefs);

    DMSearchResults dsSearchResults = null;
    dsSearchResults = this.searchUsers(dsSearchCriteria, 1, 1, false);

    long rval;

    if (dsSearchResults.totalHitsCount != 1 ||
      null == dsSearchResults.dsDM)
    rval = 0L;
    else
      rval = dsSearchResults.dsDM.getSEARCHRESULTS_USERS()[0].getUserId();

    if (oLog != null) oLog.debug("Meridio: Exiting searchUsers method.");

      return rval;
  }



  public RMDataSet getRolesAndMembership ()
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getRolesAndMembership method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetRolesAndMembershipWTResponseGetRolesAndMembershipWTResult rolesAndMembershipResult = null;
        rolesAndMembershipResult = meridioRMWebService_.getRolesAndMembership(loginToken_);

        RMDataSet ds = getRMDataSet(rolesAndMembershipResult.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getRolesAndMembership method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getRolesAndMembership method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }

  }



  public RMDataSet getProtectiveMarkingList
  (
    int id,
    int objectType
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getProtectiveMarkingList method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetProtectiveMarkingListWTResponseGetProtectiveMarkingListWTResult getProtectiveMarkingList = null;
        getProtectiveMarkingList = meridioRMWebService_.getProtectiveMarkingList(loginToken_,
          id, objectType);

        RMDataSet ds = getRMDataSet(getProtectiveMarkingList.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getProtectiveMarkingList method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getProtectiveMarkingList method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  public RMDataSet getUserPrivilegeList
  (
    int userId
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getUserPrivilegeList method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetUserPrivilegeListWTResponseGetUserPrivilegeListWTResult getUserPrivilegeList = null;
        getUserPrivilegeList = meridioRMWebService_.getUserPrivilegeList(loginToken_, userId);

        RMDataSet ds = getRMDataSet(getUserPrivilegeList.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getUserPrivilegeList method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getUserPrivilegeList method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  public RMDataSet getRecord
  (
    int recordId,
    boolean getPropDefs,
    boolean getActivities,
    boolean getCustomProperties
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getRecord method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetRecordWTResponseGetRecordWTResult getRecord = null;
        getRecord = meridioRMWebService_.getRecord(loginToken_, recordId,
          getPropDefs, getActivities, getCustomProperties);

        RMDataSet ds = getRMDataSet(getRecord.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getRecord method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getRecord method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }


  public RMDataSet getFolder
  (
    int folderId,
    boolean getPropDefs,
    boolean getActivities,
    boolean getContents,
    boolean getCustomProperties
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getFolder method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetFolderWTResponseGetFolderWTResult getFolder = null;
        getFolder = meridioRMWebService_.getFolder(loginToken_, folderId,
          getPropDefs, getActivities, getContents, getCustomProperties);

        RMDataSet ds = getRMDataSet(getFolder.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getFolder method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getFolder method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /**
  * This method is a wrapper around the Meridio "GetUsersData" Web Service Method
  *
  * The following table shows which results are populated in the return result
  * if they are set to "true"
  *
  * ACCESSCONTROL        getCanDo    (also controls retrieval of activity information for
  *                                                              requested groups, containers, stored searches and
  *                                                              locked documents)
  * CONTAINERS                   getSharedContainers
  * DOCUMENT_CUSTOMPROPS getLockedDocuments
  * GROUP_CUSTOMPROPS    getGroupList
  * USER_CUSTOMPROPS     getPropList
  * DOCUMENTS                    getLockedDocuments
  * GROUPS                               getGroupList
  * STOREDSEARCHES               getStoredSearches
  * USERS                                getPropList
  *
  *@param userId         Retrieves data relating to the user specified by the userId argument.
  *                                      The data retrieved can be selected by supplying a true value for those
  *                                      Boolean arguments in which the caller is interested
  *@param getPropList
  *@param getCanDo
  *@param getGroups  if true then the returned groups are only those of which the specified user
  *                                      is a direct member, i.e. they do not include groups of which the user is
  *                                      indirectly a member by virtue of the user's membership of an intermediate subgroup
  *@param getSharedContainers returns the user's shared containers (if any)
  *@param getStoredSearches      getLockedDocuments and getStoredSearches arguments control retrieval
  *                                                      of the specified user's (userId) locked documents and stored searches.
  *                                                      Only documents or stored searches to which the current session user has
  *                                                      access are returned when the getLockedDocuments or getStoredSearches
  *                                                      arguments are true.
  *@param getLockedDocuments see getStoredSearches
  *
  *
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMDataSet getUserData
  (
    long userId,
    boolean getPropList,
    boolean getCanDo,
    boolean getGroups,
    boolean getSharedContainers,
    boolean getStoredSearches,
    boolean getLockedDocuments
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null && oLog.isDebugEnabled())
      oLog.debug("Meridio: Entering getUserData method\n" +
      "\n\tUser Identifier <" + userId + "> " +
      "\n\tGet Property List <" + getPropList + "> " +
      "\n\tGet Can Do <" + getCanDo + "> " +
      "\n\tGet Groups <" + getGroups + "> " +
      "\n\tGet Shared Containers <" + getSharedContainers + ">" +
      "\n\tSet Stored Searches <" + getStoredSearches + "> " +
      "\n\tGet Locked Documents <" + getLockedDocuments + ">");

    boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetUserDataWTResponseGetUserDataWTResult userData = null;

        userData = meridioDMWebService_.getUserData(loginToken_, new UnsignedInt(userId),
          getPropList, getCanDo, getGroups, getSharedContainers, getStoredSearches, getLockedDocuments);

        DMDataSet dmDS = getDMDataSet(userData.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getUserData method.");

          return dmDS;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getUserData method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /**
  * This method is a wrapper around the Meridio "GetDocumentData" Web Service Method
  *
  * Retrieves data relating to a specified document. The data retrieved can be selected by
  * supplying a true value for those Boolean arguments in which the caller is interested.
  * Similarly, if getVersions is ALL then all versions of the specified document will be
  *
  * DataTable                            Controlling flag
  * ACCESSCONTROL                        getAcl (ACL of document docId only)
  * ACTIVITIES                           getCanDo (also controls retrieval of activity information for requested policy,
  *                                                                              lock, keywords and referencing containers)
  * CONTAINERS                           getReferencingContainers
  * CONTAINER_DOCUMENTREFS       getReferencingContainers
  * CONTAINER_CUSTOMPROPS        getReferencingContainers
  * DOCUMENT_CUSTOMPROPS         getPropList
  * DOCUMENT_POLICIES            getPolicy
  * DOCUMENT_REFERENCEPATHS      getReferencePaths
  * LOCK_CUSTOMPROPS             getLockInfo
  * VERSION_CUSTOMPROPS          getVersions
  * DOCUMENTS                            getPropList
  * DOCUMENT_KEYWORDS            getKeywords
  * GROUPS                                       getAcl
  * KEYWORDS                             getKeywords
  * LOCKS                                        getLockInfo
  * USERS                                        getAcl
  * VERSIONS                             getVersions
  *
  *@return                                                       Meridio DMDataSet with elements populated as per controlling flags
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMDataSet getDocumentData
  (
    int docId,
    boolean getPropList,
    boolean getAcl,
    boolean getCanDo,
    boolean getPolicy,
    DmVersionInfo getVersions,
    boolean getKeywords,
    boolean getReferencingContainers,
    boolean getLockInfo
  )
    throws RemoteException, MeridioDataSetException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getDocumentData method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetDocumentDataWTResponseGetDocumentDataWTResult getDocumentResponse = null;

        getDocumentResponse = meridioDMWebService_.getDocumentData(loginToken_, new UnsignedInt(docId),
          getPropList, getAcl, getCanDo, getPolicy, getVersions, getKeywords,
          getReferencingContainers, getLockInfo);

        DMDataSet ds = getDMDataSet(getDocumentResponse.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getDocumentData method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getDocumentData method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }



  /**
  * This method is a wrapper around the Meridio "GetContainerData" Web Service Method
  *
  * Retrieves data relating to a specified container. The data retrieved can be selected by supplying
  * a true value for those arguments in which the caller is interested. The DataTables returned are
  * as described below.
  *
  * DataTable                                            Controlling flag
  * ACCESSCONTROL                                        getAcl
  *                                                                      (ACL of container containerId only)
  * ACTIVITIES                                           getCanDo
  *                                                                      (also controls retrieval of activity information for
  *                                                                      requested references, child containers, keywords and
  *                                                                      referencing containers)
  * CONTAINERS                                           getPropList
  *                                                                      getContainerRefs
  *                                                                      getReferencingContainers
  * CONTAINER_CHILDCONTAINERS            getChildContainers
  *                                                                      (always includes specified container's parent)
  * CONTAINER_CONTAINERREFS                      getContainerRefs
  *                                                                      (includes containers referenced by specified container)
  *                                                                      getReferencingContainers
  *                                                                      (includes containers that specified container is referenced by)
  * CONTAINER_DOCUMENTREFS                       getDocumentRefs
  * CONTAINER_STOREDSEARCHREFS           getStoredSearchRefs
  * CONTAINER_CUSTOMPROPS                        getPropList
  *                                                                      getChildContainers
  *                                                                      getContainerRefs
  *                                                                      getReferencingContainers
  * CONTAINER_KEYWORDS                           getKeywords
  * DOCUMENT_CUSTOMPROPS                         getDocumentRefs
  * DOCUMENTS                                            getDocumentRefs
  * GROUPS                                                       getAcl
  * KEYWORDS                                             getKeywords
  * STOREDSEARCHES                                       getStoredSearchRefs
  * USERS                                                        getAcl
  *
  *@return                                                       Meridio DMDataSet with elements populated as per controlling flags
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public DMDataSet getContainerData
  (
    int containerId,
    boolean getPropList,
    boolean getAcl,
    boolean getCanDo,
    boolean getChildContainers,
    boolean getContainerRefs,
    boolean getDocumentRefs,
    boolean getStoredSearchRefs,
    boolean getKeywords,
    boolean getReferencingContainers
  )
    throws RemoteException, MeridioDataSetException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getContainerData method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetContainerDataWTResponseGetContainerDataWTResult getContainerDataResponse = null;

        getContainerDataResponse = meridioDMWebService_.getContainerData(loginToken_, new UnsignedInt(containerId),
          getPropList, getAcl, getCanDo, getChildContainers, getContainerRefs, getDocumentRefs,
          getStoredSearchRefs, getKeywords, getReferencingContainers);

        DMDataSet ds = getDMDataSet(getContainerDataResponse.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getContainerData method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getContainerData method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }




  /**
  * This method is a wrapper around the Meridio "GetLatestVersionFile" Web Service Method
  *
  * These methods retrieve a copy of a the latest version of a file that is stored in Meridio.
  *@param  docId                     the identifier of the document for the version file to return
  *@return                                                       An attachment part object, containing the version file
  *
  *@throws RemoteException                       if an error is encountered call the Meridio web service method(s)
  *@throws MeridioDataSetException       if an error is encountered manipulating the Meridio DataSet
  */
  public AttachmentPart getLatestVersionFile
  (
    int docId
  )
    throws RemoteException, MeridioDataSetException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getLatestVersionFile method.");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        String mimeType = null;

        meridioDMWebService_._setProperty(
          Call.ATTACHMENT_ENCAPSULATION_FORMAT,
          Call.ATTACHMENT_ENCAPSULATION_FORMAT_DIME);

        mimeType = meridioDMWebService_.getLatestVersionFile
        (loginToken_, new UnsignedInt(docId));

        Object [] attachments = meridioDMWebService_.getAttachments();
        if (oLog != null && oLog.isDebugEnabled())
          oLog.debug("Meridio: Mime Type is: <" + mimeType + "> - " + attachments.length + " attachment(s)");

        if (attachments.length != 1)  // There should be exactly 1 attachment
        {
          if (oLog != null) oLog.debug("Meridio: Exiting getLatestVersionFile method with null.");
            return null;
        }

        AttachmentPart ap = (AttachmentPart) attachments[0];
        if (oLog != null && oLog.isDebugEnabled())
          oLog.debug("Meridio: Temp File is <" + ap.getAttachmentFile() + ">");

        if (oLog != null) oLog.debug("Meridio: Exiting getLatestVersionFile method.");
          return ap;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getLatestVersionFile method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }

  public RMDataSet getRecordPartList
  (
    int recordId,
    boolean getPropDefs,
    boolean getActivities
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getRecordPartList method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetRecordPartListWTResponseGetRecordPartListWTResult getRecordPartList = null;
        getRecordPartList = meridioRMWebService_.getRecordPartList(loginToken_, recordId,
          getPropDefs, getActivities);

        RMDataSet ds = getRMDataSet(getRecordPartList.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getRecordPartList method.");
          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getRecordPartList method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }

  public RMDataSet getDocumentPartList
  (
    int documentId
  )
    throws RemoteException, MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entering getDocumentPartList method");

      boolean loginTried = false;
    while (true)
    {
      loginUnified();
      try
      {
        GetDocumentPartListWTResponseGetDocumentPartListWTResult getDocumentPartList = null;
        getDocumentPartList = meridioRMWebService_.getDocumentPartList(loginToken_, documentId);

        RMDataSet ds = getRMDataSet(getDocumentPartList.get_any());

        if (oLog != null) oLog.debug("Meridio: Exiting getDocumentPartList method.");

          return ds;
      }
      catch (RemoteException e)
      {
        if (loginTried == false)
        {
          if (oLog != null) oLog.debug("Meridio: Retrying getDocumentPartList method with login.");
            loginToken_ = null;
          loginTried = true;
          continue;
        }
        throw e;
      }
    }
  }

  /** Given the SOAP response received by AXIS on the successful call to a Meridio
  * Web Service, this helper method returns a castor DMDataSet object which represents
  * the XML
  *
  * This makes it much easier to subsequently manipulate the data that has been
  * returned from the web service, and ensures that the Meridio wrapper implementation
  * is as close to native .NET code as we can get.
  */
  protected DMDataSet getDMDataSet
  (
    MessageElement [] messageElement
  )
    throws MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getDMDataSet method.");

      try
    {
      if (messageElement.length != 2)
      {
        if (oLog != null) oLog.warn("Meridio: SOAP Message not of expected length");
        if (oLog != null) oLog.debug("Meridio: Exiting getDMDataSet method with null.");
          return null;
      }

      /*
      for (int i = 0; i < messageElement.length; i++)
      {
        oLog.debug("Meridio: Message Part: " + i + " " + messageElement[i]);
      }
      */


      Document document         = messageElement[1].getAsDocument();
      NodeList nl               = document.getElementsByTagName("DMDataSet");


      NodeList errors           = document.getElementsByTagName("diffgr:errors");
      if (errors.getLength() != 0)
      {
        String errorXML = "";

        if (oLog != null) oLog.error("Found <" + errors.getLength() + "> errors in returned data set");
        for (int i = 0; i < errors.getLength(); i++)
        {

          Element e                 = (Element) errors.item(i);

          Document resultDocument   = new DocumentImpl();
          Node node                 = resultDocument.importNode(e, true);

          resultDocument.appendChild(node);

          DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();
          DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
          LSSerializer writer = implLS.createLSSerializer();
          errorXML += writer.writeToString(resultDocument) + "\n";

          if (oLog != null) oLog.warn("..." + errorXML);
        }
        throw new MeridioDataSetException(errorXML);
      }


      if (nl.getLength() != 1)
      {
        if (oLog != null) oLog.warn("Meridio: Returning null - could not find DMDataSet in SOAP Message");
        if (oLog != null) oLog.debug("Meridio: Exiting getDMDataSet method with null.");
          return null;
      }

      Element e                 = (Element) nl.item(0);
      Document resultDocument   = new DocumentImpl();
      Node node                 = resultDocument.importNode(e, true);

      resultDocument.appendChild(node);

      DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();
      DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
      LSSerializer writer = implLS.createLSSerializer();
      String documentXML = writer.writeToString(resultDocument);

      //oLog.debug("Meridio: Result: " + documentXML);

      StringReader sr = new StringReader(documentXML);
      DMDataSet dsDM  = new DMDataSet();
      dsDM            = DMDataSet.unmarshal(sr);

      if (oLog != null) oLog.debug("Meridio: Exiting getDMDataSet method.");

        return dsDM;
    }
    catch (ClassNotFoundException classNotFoundException)
    {
      throw new MeridioDataSetException(
        "Could not find the DOM Parser class when unmarshalling the Meridio Dataset",
        classNotFoundException);
    }
    catch (InstantiationException instantiationException)
    {
      throw new MeridioDataSetException(
        "Error instantiating the DOM Parser when unmarshalling the Meridio Dataset",
        instantiationException);
    }
    catch (IllegalAccessException illegalAccessException)
    {
      throw new MeridioDataSetException(
        "DOM Parser illegal access when unmarshalling the Meridio Dataset",
        illegalAccessException);
    }
    catch (MarshalException marshalException)
    {
      throw new MeridioDataSetException(
        "Castor error in marshalling the XML from the Meridio Dataset",
        marshalException);
    }
    catch (ValidationException validationException)
    {
      throw new MeridioDataSetException(
        "Castor error in validating the XML from the Meridio Dataset",
        validationException);
    }
    catch (Exception ex) // from messageElement[1].getAsDocument();
    {
      throw new MeridioDataSetException(
        "Error retrieving the XML Document from the Web Service response",
        ex);
    }
  }



  /** Given the SOAP response received by AXIS on the successful call to a Meridio
  * Web Service, this helper method returns a castor RMDataSet object which represents
  * the XML
  *
  * This makes it much easier to subsequently manipulate the data that has been
  * returned from the web service, and ensures that the Meridio wrapper implementation
  * is as close to native .NET code as we can get.
  */
  protected RMDataSet getRMDataSet
  (
    MessageElement [] messageElement
  )
    throws MeridioDataSetException
  {
    if (oLog != null) oLog.debug("Meridio: Entered getRMDataSet method.");

      try
    {
      if (messageElement.length != 2)
      {
        if (oLog != null) oLog.warn("Meridio: SOAP Message not of expected length");
        if (oLog != null) oLog.debug("Meridio: Exiting getRMDataSet method with null.");
          return null;
      }

      /*
      for (int i = 0; i < messageElement.length; i++)
      {
        oLog.debug("Meridio: Message Part: " + i + " " + messageElement[i]);
      }
      */

      Document document         = messageElement[1].getAsDocument();
      NodeList nl               = document.getElementsByTagName("RMDataSet");


      NodeList errors           = document.getElementsByTagName("diffgr:errors");
      if (errors.getLength() != 0)
      {
        String errorXML = "";

        if (oLog != null) oLog.error("Found <" + errors.getLength() + "> errors in returned data set");
        for (int i = 0; i < errors.getLength(); i++)
        {

          Element e                 = (Element) errors.item(i);

          Document resultDocument   = new DocumentImpl();
          Node node                 = resultDocument.importNode(e, true);

          resultDocument.appendChild(node);

          DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();
          DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
          LSSerializer writer = implLS.createLSSerializer();
          errorXML += writer.writeToString(resultDocument) + "\n";

          if (oLog != null) oLog.warn("..." + errorXML);
        }
        throw new MeridioDataSetException(errorXML);
      }


      if (nl.getLength() != 1)
      {
        if (oLog != null) oLog.warn("Meridio: Returning null - could not find RMDataSet in SOAP Message");
        if (oLog != null) oLog.debug("Meridio: Exiting getRMDataSet method with null.");
          return null;
      }

      Element e                 = (Element) nl.item(0);
      Document resultDocument   = new DocumentImpl();
      Node node                 = resultDocument.importNode(e, true);

      resultDocument.appendChild(node);

      DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();
      DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
      LSSerializer writer = implLS.createLSSerializer();
      String documentXML = writer.writeToString(resultDocument);

      StringReader sr = new StringReader(documentXML);
      RMDataSet dsRM  = new RMDataSet();
      dsRM            = RMDataSet.unmarshal(sr);
      if (oLog != null) oLog.debug("Meridio: Exiting getRMDataSet method.");
        return dsRM;
    }
    catch (ClassNotFoundException classNotFoundException)
    {
      throw new MeridioDataSetException(
        "Could not find the DOM Parser class when unmarshalling the Meridio Dataset",
        classNotFoundException);
    }
    catch (InstantiationException instantiationException)
    {
      throw new MeridioDataSetException(
        "Error instantiating the DOM Parser when unmarshalling the Meridio Dataset",
        instantiationException);
    }
    catch (IllegalAccessException illegalAccessException)
    {
      throw new MeridioDataSetException(
        "DOM Parser illegal access when unmarshalling the Meridio Dataset",
        illegalAccessException);
    }
    catch (MarshalException marshalException)
    {
      throw new MeridioDataSetException(
        "Castor error in marshalling the XML from the Meridio Dataset",
        marshalException);
    }
    catch (ValidationException validationException)
    {
      throw new MeridioDataSetException(
        "Castor error in validating the XML from the Meridio Dataset",
        validationException);
    }
    catch (Exception ex) // from messageElement[1].getAsDocument();
    {
      throw new MeridioDataSetException(
        "Error retrieving the XML Document from the Web Service response",
        ex);
    }
  }



  /** Given the castor object representing the Meridio DMDataSet XSD, this method generates
  * the XML that must be passed over the wire to invoke the Meridio DM Web Service
  */
  protected MessageElement [] getSOAPMessage
  (
    DMDataSet dsDM
  )
    throws MeridioDataSetException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getSOAPMessage method.");

      try
    {
      Writer writer = new StringWriter();
      dsDM.marshal(writer);
      writer.close();
      //oLog.debug("Meridio: Marshalled XML: " + writer.toString());

      StringReader stringReader = new StringReader(writer.toString());
      InputSource inputSource   = new InputSource(stringReader);

      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setValidating(false);
      Document document = documentBuilderFactory.newDocumentBuilder().parse(inputSource);
      Element element = document.getDocumentElement();
      MessageElement messageElement = new MessageElement(element);

      MessageElement [] messageElementArray = {messageElement};
      if (oLog != null) oLog.debug("Meridio: Exiting getSOAPMessage method.");
        return messageElementArray;

    }
    catch (MarshalException marshalException)
    {
      throw new MeridioDataSetException(
        "Castor error in marshalling the XML from the Meridio Dataset",
        marshalException);
    }
    catch (ValidationException validationException)
    {
      throw new MeridioDataSetException(
        "Castor error in validating the XML from the Meridio Dataset",
        validationException);
    }
    catch (IOException IoException)
    {
      throw new MeridioDataSetException(
        "IO Error in marshalling the Meridio Dataset",
        IoException);
    }
    catch (SAXException SaxException)
    {
      throw new MeridioDataSetException(
        "XML Error in marshalling the Meridio Dataset",
        SaxException);
    }
    catch (ParserConfigurationException parserConfigurationException)
    {
      throw new MeridioDataSetException(
        "XML Error in parsing the Meridio Dataset",
        parserConfigurationException);
    }
  }



  /** Given the castor object representing the Meridio DMDataSet XSD, this method generates
  * the XML that must be passed over the wire to invoke the Meridio DM Web Service
  */

  protected MessageElement [] getSOAPMessage
  (
    RMDataSet dsRM
  )
    throws MeridioDataSetException
  {

    if (oLog != null) oLog.debug("Meridio: Entered getSOAPMessage method.");

      try
    {
      Writer writer = new StringWriter();
      dsRM.marshal(writer);
      writer.close();
      //oLog.debug("Meridio: Marshalled XML: " + writer.toString());

      StringReader stringReader = new StringReader(writer.toString());
      InputSource inputSource   = new InputSource(stringReader);

      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setValidating(false);
      Document document = documentBuilderFactory.newDocumentBuilder().parse(inputSource);
      Element element = document.getDocumentElement();
      MessageElement messageElement = new MessageElement(element);

      MessageElement [] messageElementArray = {messageElement};
      if (oLog != null) oLog.debug("Meridio: Exiting getSOAPMessage method.");
        return messageElementArray;

    }
    catch (MarshalException marshalException)
    {
      throw new MeridioDataSetException(
        "Castor error in marshalling the XML from the Meridio Dataset",
        marshalException);
    }
    catch (ValidationException validationException)
    {
      throw new MeridioDataSetException(
        "Castor error in validating the XML from the Meridio Dataset",
        validationException);
    }
    catch (IOException IoException)
    {
      throw new MeridioDataSetException(
        "IO Error in marshalling the Meridio Dataset",
        IoException);
    }
    catch (SAXException SaxException)
    {
      throw new MeridioDataSetException(
        "XML Error in marshalling the Meridio Dataset",
        SaxException);
    }
    catch (ParserConfigurationException parserConfigurationException)
    {
      throw new MeridioDataSetException(
        "XML Error in parsing the Meridio Dataset",
        parserConfigurationException);
    }
  }

  /** Implementation of EngineConfiguration that we'll use to get the wsdd file from a
  * local resource.
  */
  protected static class ResourceProvider implements WSDDEngineConfiguration
  {
    private WSDDDeployment deployment = null;

    private Class resourceClass;
    private String resourceName;

    /**
     * Constructor setting the resource name.
     */
    public ResourceProvider(Class resourceClass, String resourceName) 
    {
      this.resourceClass = resourceClass;
      this.resourceName = resourceName;
    }

    public WSDDDeployment getDeployment() {
        return deployment;
    }

    public void configureEngine(AxisEngine engine)
      throws ConfigurationException
    {
      try
      {
        InputStream resourceStream = resourceClass.getResourceAsStream(resourceName);
        if (resourceStream == null)
          throw new ConfigurationException("Resource not found: '"+resourceName+"'");
        try
        {
          WSDDDocument doc = new WSDDDocument(XMLUtils.newDocument(resourceStream));
          deployment = doc.getDeployment();

          deployment.configureEngine(engine);
          engine.refreshGlobalOptions();
        }
        finally
        {
          resourceStream.close();
        }
      }
      catch (ConfigurationException e)
      {
        throw e;
      }
      catch (Exception e)
      {
        throw new ConfigurationException(e);
      }
    }

    public void writeEngineConfig(AxisEngine engine)
      throws ConfigurationException
    {
      // Do nothing
    }

    /**
     * retrieve an instance of the named handler
     * @param qname XXX
     * @return XXX
     * @throws ConfigurationException XXX
     */
    public Handler getHandler(QName qname) throws ConfigurationException
    {
      return deployment.getHandler(qname);
    }

    /**
     * retrieve an instance of the named service
     * @param qname XXX
     * @return XXX
     * @throws ConfigurationException XXX
     */
    public SOAPService getService(QName qname) throws ConfigurationException
    {
      SOAPService service = deployment.getService(qname);
      if (service == null)
      {
        throw new ConfigurationException(Messages.getMessage("noService10",
          qname.toString()));
      }
      return service;
    }

    /**
     * Get a service which has been mapped to a particular namespace
     * 
     * @param namespace a namespace URI
     * @return an instance of the appropriate Service, or null
     */
    public SOAPService getServiceByNamespaceURI(String namespace)
      throws ConfigurationException
    {
      return deployment.getServiceByNamespaceURI(namespace);
    }

    /**
     * retrieve an instance of the named transport
     * @param qname XXX
     * @return XXX
     * @throws ConfigurationException XXX
     */
    public Handler getTransport(QName qname) throws ConfigurationException
    {
      return deployment.getTransport(qname);
    }

    public TypeMappingRegistry getTypeMappingRegistry()
        throws ConfigurationException
    {
      return deployment.getTypeMappingRegistry();
    }

    /**
     * Returns a global request handler.
     */
    public Handler getGlobalRequest() throws ConfigurationException
    {
      return deployment.getGlobalRequest();
    }

    /**
     * Returns a global response handler.
     */
    public Handler getGlobalResponse() throws ConfigurationException
    {
      return deployment.getGlobalResponse();
    }

    /**
     * Returns the global configuration options.
     */
    public Hashtable getGlobalOptions() throws ConfigurationException
    {
      WSDDGlobalConfiguration globalConfig = deployment.getGlobalConfiguration();
            
      if (globalConfig != null)
        return globalConfig.getParametersTable();

      return null;
    }

    /**
     * Get an enumeration of the services deployed to this engine
     */
    public Iterator getDeployedServices() throws ConfigurationException
    {
      return deployment.getDeployedServices();
    }

    /**
     * Get a list of roles that this engine plays globally.  Services
     * within the engine configuration may also add additional roles.
     *
     * @return a <code>List</code> of the roles for this engine
     */
    public List getRoles()
    {
      return deployment.getRoles();
    }
  }

}
