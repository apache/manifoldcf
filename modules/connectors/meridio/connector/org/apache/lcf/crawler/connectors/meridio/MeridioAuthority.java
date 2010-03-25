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

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.connectors.meridio.DMDataSet.DMDataSet;
import org.apache.lcf.crawler.connectors.meridio.meridiowrapper.MeridioDataSetException;
import org.apache.lcf.crawler.connectors.meridio.meridiowrapper.MeridioWrapper;
import org.apache.lcf.crawler.connectors.meridio.RMDataSet.RMDataSet;
import org.tempuri.GroupResult;
import org.apache.lcf.authorities.system.Logging;
import org.apache.lcf.authorities.system.LCF;
import org.apache.lcf.authorities.interfaces.AuthorizationResponse;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolFactory;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.net.*;



/** This is the Meridio implementation of the IAuthorityConnector interface.
*
*/
public class MeridioAuthority extends org.apache.lcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Properties we need
  public final static String wsddPathProperty = "org.apache.lcf.meridio.wsddpath";

  private MeridioWrapper meridio_       = null;  // A handle to the Meridio Java API Wrapper

  // URLs initialized by 'connect' code
  private URL DmwsURL = null;
  private URL RmwsURL = null;
  private URL MetaCartawsURL = null;

  private ProtocolFactory myFactory = null;

  private String DMWSProxyHost = null;
  private String DMWSProxyPort = null;
  private String RMWSProxyHost = null;
  private String RMWSProxyPort = null;
  private String MetaCartaWSProxyHost = null;
  private String MetaCartaWSProxyPort = null;
  private String UserName = null;
  private String Password = null;


  final private static int MANAGE_DOCUMENT_PRIVILEGE = 17;

  /** Deny access token for Meridio.  All tokens begin with "U" or with "G", except the blanket "READ_ALL" that I create.
  * However, we currently have code in the field, so I will continue ot use "DEAD_AUTHORITY" for that reason.
  */
  private final static String denyToken = "DEAD_AUTHORITY";

  private final static AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{denyToken},AuthorizationResponse.RESPONSE_UNREACHABLE);
  private final static AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{denyToken},AuthorizationResponse.RESPONSE_USERNOTFOUND);


  /** Constructor.
  */
  public MeridioAuthority() {}

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



  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    /*=================================================================
    * Construct the URL strings from the parameters
    *
    *================================================================*/
    DMWSProxyHost = configParams.getParameter("DMWSProxyHost");
    DMWSProxyPort = configParams.getParameter("DMWSProxyPort");
    RMWSProxyHost = configParams.getParameter("RMWSProxyHost");
    RMWSProxyPort = configParams.getParameter("RMWSProxyPort");
    MetaCartaWSProxyHost = configParams.getParameter("MetaCartaWSProxyHost");
    MetaCartaWSProxyPort = configParams.getParameter("MetaCartaWSProxyPort");
    UserName = configParams.getParameter("UserName");
    Password = configParams.getObfuscatedParameter("Password");

  }

  /** Set up connection before attempting to use it */
  protected void attemptToConnect()
    throws LCFException
  {
    if (meridio_ == null)
    {

      // This stuff used to be in connect(); was moved here so proper exception handling could be done
      /*=================================================================
      * Construct the URL strings from the parameters
      *
      *================================================================*/

      String DMWSProtocol = params.getParameter("DMWSServerProtocol");
      if (DMWSProtocol == null)
        throw new LCFException("Missing required configuration parameter: DMWSServerProtocol");
      String DMWSPort = params.getParameter("DMWSServerPort");
      if (DMWSPort == null || DMWSPort.length() == 0)
        DMWSPort = "";
      else
        DMWSPort = ":" + DMWSPort;

      String DMWSUrlString = DMWSProtocol + "://" +
        params.getParameter("DMWSServerName") +
        DMWSPort +
        params.getParameter("DMWSLocation");

      String RMWSProtocol = params.getParameter("RMWSServerProtocol");
      if (RMWSProtocol == null)
        throw new LCFException("Missing required configuration parameter: RMWSServerProtocol");
      String RMWSPort = params.getParameter("RMWSServerPort");
      if (RMWSPort == null || RMWSPort.length() == 0)
        RMWSPort = "";
      else
        RMWSPort = ":" + RMWSPort;

      String RMWSUrlString = RMWSProtocol + "://" +
        params.getParameter("RMWSServerName") +
        RMWSPort +
        params.getParameter("RMWSLocation");

      String MetaCartaWSProtocol = params.getParameter("MetaCartaWSServerProtocol");
      if (MetaCartaWSProtocol == null)
        throw new LCFException("Missing required configuration parameter: MetaCartaWSServerProtocol");
      String MetaCartaWSPort = params.getParameter("MetaCartaWSServerPort");
      if (MetaCartaWSPort == null || MetaCartaWSPort.length() == 0)
        MetaCartaWSPort = "";
      else
        MetaCartaWSPort = ":" + MetaCartaWSPort;

      String LCFWSUrlString = MetaCartaWSProtocol + "://" +
        params.getParameter("MetaCartaWSServerName") +
        MetaCartaWSPort +
        params.getParameter("MetaCartaWSLocation");


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

      try
      {
        DmwsURL = new URL(DMWSUrlString);
        RmwsURL = new URL(RMWSUrlString);
        MetaCartawsURL = new URL(LCFWSUrlString);

        if (Logging.authorityConnectors.isDebugEnabled())
        {
          Logging.authorityConnectors.debug("Meridio: Document Management Web Service (DMWS) URL is [" + DmwsURL + "]");
          Logging.authorityConnectors.debug("Meridio: Record Management Web Service (RMWS) URL is [" + RmwsURL + "]");
          Logging.authorityConnectors.debug("Meridio: MetaCarta Web Service (MCWS) URL is [" + MetaCartawsURL + "]");
        }

      }
      catch (MalformedURLException malformedURLException)
      {
        throw new LCFException("Meridio: Could not construct the URL for either " +
          "the Meridio DM, Meridio RM, or MetaCarta Meridio Web Service: "+malformedURLException, malformedURLException);
      }

      try
      {
        /*=================================================================
        * Now try and login to Meridio; the wrapper's constructor can be
        * used as it calls the Meridio login method
        *================================================================*/
        String meridioWSDDLocation = LCF.getProperty(wsddPathProperty);
        if (meridioWSDDLocation == null)
          throw new LCFException("Meridio wsdd location path (property "+wsddPathProperty+") must be specified!");

        meridio_ = new MeridioWrapper(Logging.authorityConnectors, DmwsURL, RmwsURL, MetaCartawsURL,
          DMWSProxyHost, DMWSProxyPort, RMWSProxyHost, RMWSProxyPort, MetaCartaWSProxyHost, MetaCartaWSProxyPort,
          UserName, Password,
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
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Meridio: Got an unknown remote exception connecting - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
        throw new LCFException("Remote procedure exception: "+e.getMessage(),e);
      }
      catch (RemoteException remoteException)
      {
        throw new LCFException("Meridio: An unknown remote exception occurred while " +
          "connecting: "+remoteException.getMessage(), remoteException);
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
    Logging.authorityConnectors.debug("Meridio: Entering 'check' method");
    attemptToConnect();
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
        Logging.authorityConnectors.warn("Meridio: DM DataSet returned was null in 'check' method");
        return "Connection Failed - Internal Error Contact Support";
      }
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("Meridio System Name is [" +
        ds.getSYSTEMINFO().getSystemName() + "] and the comment is [" +
        ds.getSYSTEMINFO().getComment() + "]");

      /*=================================================================
      * For completeness, we also call a method in the RM Web
      * Service API
      *================================================================*/
      RMDataSet rmws = meridio_.getConfiguration();
      if (null == rmws)
      {
        Logging.authorityConnectors.warn("Meridio: RM DataSet returned was null in 'check' method");
        return "Connection Failed - RM DataSet Error, contact Support";
      }

      /*=================================================================
      * Finally, try to get the group parents of user ID 2 (which is the admin user always).
      * This tests the MetaCarta web service setup.
      */
      meridio_.getUsersGroups(2);

      Logging.authorityConnectors.debug("Meridio: Exiting 'check' method");

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
        return "Unknown http error occurred while connecting: "+e.getMessage();
      }
      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("Meridio: Got an unknown remote exception checking - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      return "Axis fault: "+e.getMessage();
    }
    catch (RemoteException remoteException)
    {
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("Meridio: Unknown remote exception occurred during 'check' method: "+remoteException.getMessage(),
        remoteException);
      return "Meridio: An unknown remote exception occurred while connecting: "+remoteException.getMessage();
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
      Logging.authorityConnectors.warn("Meridio: DataSet Exception occurred during 'check' method",
        meridioDataSetException);

      return "Connection Failed - DataSet error: "+meridioDataSetException.getMessage();
    }
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws LCFException
  {
    Logging.authorityConnectors.debug("Meridio: Entering 'disconnect' method");
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
          Logging.authorityConnectors.warn("Unexpected http error code "+httpErrorCode+" logging out: "+e.getMessage());
          return;
        }
        Logging.authorityConnectors.warn("Unknown http error occurred while logging out: "+e.getMessage());
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

      Logging.authorityConnectors.warn("Meridio: Got an unknown remote exception logging out - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      return;
    }
    catch (RemoteException remoteException)
    {
      Logging.authorityConnectors.warn("Meridio: A remote exception occurred while " +
        "logging out: "+remoteException.getMessage(), remoteException);
    }
    finally
    {
      super.disconnect();
      meridio_ = null;
      DmwsURL = null;
      RmwsURL = null;
      MetaCartawsURL = null;
      myFactory = null;
      DMWSProxyHost = null;
      DMWSProxyPort = null;
      RMWSProxyHost = null;
      RMWSProxyPort = null;
      MetaCartaWSProxyHost = null;
      MetaCartaWSProxyPort = null;
      UserName = null;
      Password = null;
    }
    Logging.authorityConnectors.debug("Meridio: Exiting 'disconnect' method");
  }


  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws LCFException
  {
    if (Logging.authorityConnectors.isDebugEnabled())
      Logging.authorityConnectors.debug("Meridio: Authentication user name = '" + userName + "'");

    while (true)
    {
      attemptToConnect();

      // Strip off everything after the @-sign
      int index = userName.indexOf("@");
      if (index != -1)
        userName = userName.substring(0,index);

      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("Meridio: Meridio user name = '"+userName+"'");

      ArrayList aclList = new ArrayList();

      try
      {
        /*=================================================================
        * Search for the user in Meridio to find their internal user ID.
        * The method returns 0 if the user is not found
        *
        * We expect the user name passed in to be the user's login name
        * without the "domain" prefix or suffix (i.e. domain\\username
        * or username@domain.com)
        *
        * If the username is passed in a different format, then it should
        * be transformed first
        *================================================================*/
        //TODO: We could also possibly search on the user's directory name to
        //      avoid the transformation
        long userId = meridio_.getUserIdFromName(userName);
        if (0L == userId)
        {
          if (Logging.authorityConnectors.isDebugEnabled())
            Logging.authorityConnectors.debug("Meridio: User '" + userName + "' does not exist");
          return userNotFoundResponse;
        }
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Meridio: Found user - the User Id for '" + userName +
          "' is " + userId);

        aclList.add("U" + userId);

        /** This is the new, MetaCarta-service way of getting the groups, which relies on the custom
        * MetaCarta web service developed by Meridio.  The old way was too inefficient to be workable for
        * installations that had more than a few users and groups.
        */
        GroupResult [] userGroups = null;
        userGroups = meridio_.getUsersGroups(new Long(userId).intValue());
        if (userGroups != null)
        {
          for (int i = 0; i < userGroups.length; i++)
          {
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("Group ID '" + userGroups[i].getGroupID() +
              "' Group Name '" + userGroups[i].getGroupName() + ">'");
            aclList.add("G" + userGroups[i].getGroupID());
          }
        }

        /*=================================================================
        * Protective markings
        *================================================================*/
        //TODO: Check if we must cater for protective markings since there
        //      is the complexity of informative markings and
        //      hierarchical markings to deal with
        //
        // Hierarchical ones are CONFIDENTIAL, RESTRICTED, SECRET, TOP SECRET
        // Non-hierarchical ones might be UK Eyes Only, US Eyes Only, etc.
        //


        /*=================================================================
        * Get the user's privileges
        *
        * The "Manage Documents" privilege will effectively grant the user
        * "manage" access to all documents/records within Meridio, so if
        * the user has been granted that privilege, or one of the groups
        * of which they are a member has that privilege then add it to
        * the token list
        *================================================================*/
        RMDataSet userPrivileges = meridio_.getUserPrivilegeList(new Long(userId).intValue());

        for (int i = 0; i < userPrivileges.getRm2Privilege().length; i++)
        {
          if (Logging.authorityConnectors.isDebugEnabled())
            Logging.authorityConnectors.debug("Meridio: Privilege ID '" + userPrivileges.getRm2Privilege()[i].getId() + "' " +
            "Name '" + userPrivileges.getRm2Privilege()[i].getName() + "'");

          if (userPrivileges.getRm2Privilege()[i].getId() == MANAGE_DOCUMENT_PRIVILEGE)
          {
            Logging.authorityConnectors.debug("Meridio: User has Manage Document privilege so adding READ_ALL to token list");
            aclList.add("READ_ALL");
          }
        }

        String[] rval = new String[aclList.size()];
        for (int i = 0; i < rval.length; i++)
        {
          rval[i] = (String)aclList.get(i);
        }

        Logging.authorityConnectors.debug("Meridio: Exiting method getAccessTokens");
        return new AuthorizationResponse(rval,AuthorizationResponse.RESPONSE_OK);
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

        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("Meridio: Got an unknown remote exception getting user tokens - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
        throw new LCFException("Axis fault: "+e.getMessage(),  e);
      }
      catch (RemoteException remoteException)
      {
        throw new LCFException("Meridio: A remote exception occurred while getting user tokens: " +
          remoteException.getMessage(), remoteException);
      }
      catch (MeridioDataSetException meridioDataSetException)
      {
        Logging.authorityConnectors.error("Meridio: A provlem occurred manipulating the Web Service XML: " +
          meridioDataSetException.getMessage(), meridioDataSetException);
        throw new LCFException("Meridio: A problem occurred manipulating the Web " +
          "Service XML: "+meridioDataSetException.getMessage(), meridioDataSetException);
      }
    }
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    return unreachableResponse;
  }
}
