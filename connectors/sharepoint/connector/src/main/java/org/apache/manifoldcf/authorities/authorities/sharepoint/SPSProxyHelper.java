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

package org.apache.manifoldcf.authorities.authorities.sharepoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.*;

import java.io.InputStream;

import javax.xml.soap.*;

import org.apache.manifoldcf.core.common.XMLDoc;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.authorities.system.Logging;

import com.microsoft.schemas.sharepoint.dsp.*;
import com.microsoft.schemas.sharepoint.soap.*;

import org.apache.http.client.HttpClient;

import org.apache.axis.EngineConfiguration;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
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

/**
*
* @author Michael Cummings
*
*/
public class SPSProxyHelper {


  public static final String HTTPCLIENT_PROPERTY = org.apache.manifoldcf.connectorcommon.common.CommonsHTTPSender.HTTPCLIENT_PROPERTY;

  private final String serverUrl;
  private final String serverLocation;
  private final String decodedServerLocation;
  private final String baseUrl;
  private final String userName;
  private final String password;
  private final boolean isClaimSpace;
  private final EngineConfiguration configuration;
  private final HttpClient httpClient;

  /**
  *
  * @param serverUrl
  * @param userName
  * @param password
  */
  public SPSProxyHelper( String serverUrl, String serverLocation, String decodedServerLocation, String userName, String password,
    Class resourceClass, String configFileName, HttpClient httpClient, boolean isClaimSpace )
  {
    this.serverUrl = serverUrl;
    this.serverLocation = serverLocation;
    this.decodedServerLocation = decodedServerLocation;
    if (serverLocation.equals("/"))
      baseUrl = serverUrl;
    else
      baseUrl = serverUrl + serverLocation;
    this.userName = userName;
    this.password = password;
    this.configuration = new ResourceProvider(resourceClass,configFileName);
    this.httpClient = httpClient;
    this.isClaimSpace = isClaimSpace;
  }

  /**
  * Get the access tokens for a user principal.
  */
  public List<String> getAccessTokens( String site, String userLoginName )
    throws ManifoldCFException
  {
    try
    {
      if ( site.compareTo("/") == 0 )
        site = ""; // root case

      userLoginName = mapToClaimSpace(userLoginName);
      
      UserGroupWS userService = new UserGroupWS( baseUrl + site, userName, password, configuration, httpClient  );
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall = userService.getUserGroupSoapHandler( );

      com.microsoft.schemas.sharepoint.soap.directory.GetUserInfoResponseGetUserInfoResult userResp = userCall.getUserInfo( userLoginName );
      org.apache.axis.message.MessageElement[] usersList = userResp.get_any();

      /* Response looks like this:
          <GetUserInfo xmlns="http://schemas.microsoft.com/sharepoint/soap/directory/">
             <User ID="4" Sid="S-1-5-21-2127521184-1604012920-1887927527-34577" Name="User1_Display_Name" 
                LoginName="DOMAIN\User1_Alias" Email="User1_E-mail" 
                Notes="Notes" IsSiteAdmin="False" IsDomainGroup="False" />
          </GetUserInfo>
        */

      if (usersList.length != 1)
        throw new ManifoldCFException("Bad response - expecting one outer 'GetUserInfo' node, saw "+Integer.toString(usersList.length));
      
      if (Logging.authorityConnectors.isDebugEnabled()){
        Logging.authorityConnectors.debug("SharePoint authority: getUserInfo xml response: '" + usersList[0].toString() + "'");
      }

      MessageElement users = usersList[0];
      if (!users.getElementName().getLocalName().equals("GetUserInfo"))
        throw new ManifoldCFException("Bad response - outer node should have been 'GetUserInfo' node");
          
      String userID = null;
      String userName = null;
      
      Iterator userIter = users.getChildElements();
      while (userIter.hasNext())
      {
        MessageElement child = (MessageElement)userIter.next();
        if (child.getElementName().getLocalName().equals("User"))
        {
          userID = child.getAttribute("ID");
          userName = child.getAttribute("LoginName");
        }
      }

      // If userID is null, no such user
      if (userID == null)
        return null;

      List<String> accessTokens = new ArrayList<String>();
      accessTokens.add("U"+userName);
      
      com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionFromUserResponseGetGroupCollectionFromUserResult userGroupResp =
        userCall.getGroupCollectionFromUser( userLoginName );
      org.apache.axis.message.MessageElement[] groupsList = userGroupResp.get_any();
      
      /* Response looks like this:
          <GetGroupCollectionFromUser xmlns=
             "http://schemas.microsoft.com/sharepoint/soap/directory/">
             <Groups>
                <Group ID="3" Name="Group1" Description="Description" OwnerID="1" 
                   OwnerIsUser="False" />
                <Group ID="15" Name="Group2" Description="Description" 
                   OwnerID="12" OwnerIsUser="True" />
                <Group ID="16" Name="Group3" Description="Description" 
                   OwnerID="7" OwnerIsUser="False" />
             </Groups>
          </GetGroupCollectionFromUser>
        */

      if (groupsList.length != 1)
        throw new ManifoldCFException("Bad response - expecting one outer 'GetGroupCollectionFromUser' node, saw "+Integer.toString(groupsList.length));

      if (Logging.authorityConnectors.isDebugEnabled()){
        Logging.authorityConnectors.debug("SharePoint authority: getGroupCollectionFromUser xml response: '" + groupsList[0].toString() + "'");
      }

      MessageElement groups = groupsList[0];
      if (!groups.getElementName().getLocalName().equals("GetGroupCollectionFromUser"))
        throw new ManifoldCFException("Bad response - outer node should have been 'GetGroupCollectionFromUser' node");
          
      Iterator groupsIter = groups.getChildElements();
      while (groupsIter.hasNext())
      {
        MessageElement child = (MessageElement)groupsIter.next();
        if (child.getElementName().getLocalName().equals("Groups"))
        {
          Iterator groupIter = child.getChildElements();
          while (groupIter.hasNext())
          {
            MessageElement group = (MessageElement)groupIter.next();
            if (group.getElementName().getLocalName().equals("Group"))
            {
              String groupID = group.getAttribute("ID");
              String groupName = group.getAttribute("Name");
              // Add to the access token list
              accessTokens.add("G"+groupName);
            }
          }
        }
      }

      // AxisFault is expected for case where user has no assigned roles
      try
      {
        com.microsoft.schemas.sharepoint.soap.directory.GetRoleCollectionFromUserResponseGetRoleCollectionFromUserResult userRoleResp =
          userCall.getRoleCollectionFromUser( userLoginName );
        org.apache.axis.message.MessageElement[] rolesList = userRoleResp.get_any();

        if (rolesList.length != 1)
          throw new ManifoldCFException("Bad response - expecting one outer 'GetRoleCollectionFromUser' node, saw "+Integer.toString(rolesList.length));
        
        if (Logging.authorityConnectors.isDebugEnabled()){
          Logging.authorityConnectors.debug("SharePoint authority: getRoleCollectionFromUser xml response: '" + rolesList[0].toString() + "'");
        }

        // Not specified in doc and must be determined experimentally
        /*
<ns1:GetRoleCollectionFromUser xmlns:ns1="http://schemas.microsoft.com/sharepoint/soap/directory/">
  <ns1:Roles>
    <ns1:Role ID="1073741825" Name="Limited Access" Description="Can view specific lists, document libraries, list items, folders, or documents when given permissions."
      Order="160" Hidden="True" Type="Guest" BasePermissions="ViewFormPages, Open, BrowseUserInfo, UseClientIntegration, UseRemoteAPIs"/>
  </ns1:Roles>
</ns1:GetRoleCollectionFromUser>'
        */
        
        MessageElement roles = rolesList[0];
        if (!roles.getElementName().getLocalName().equals("GetRoleCollectionFromUser"))
          throw new ManifoldCFException("Bad response - outer node should have been 'GetRoleCollectionFromUser' node");
            
        Iterator rolesIter = roles.getChildElements();
        while (rolesIter.hasNext())
        {
          MessageElement child = (MessageElement)rolesIter.next();
          if (child.getElementName().getLocalName().equals("Roles"))
          {
            Iterator roleIter = child.getChildElements();
            while (roleIter.hasNext())
            {
              MessageElement role = (MessageElement)roleIter.next();
              if (role.getElementName().getLocalName().equals("Role"))
              {
                String roleID = role.getAttribute("ID");
                String roleName = role.getAttribute("Name");
                // Add to the access token list
                accessTokens.add("R"+roleName);
              }
            }
          }
        }
      }
      catch (org.apache.axis.AxisFault e)
      {
        if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
        {
          org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
          if (elem != null)
          {
            elem.normalize();
            String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
            if (!sharepointErrorCode.equals("0x80131600"))
              throw e;
          }
        }
        else
          throw e;
      }
      
      return accessTokens;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new ManifoldCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("SharePoint: Got a service exception getting the acls for site "+site,e);
      throw new ManifoldCFException("Service exception: "+e.getMessage(), e);
    }
    catch (org.apache.axis.AxisFault e)
    {
      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
        if (elem != null)
        {
          elem.normalize();
          String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (httpErrorCode.equals("404"))
          {
            // Page did not exist
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("SharePoint: The page at "+baseUrl+site+" did not exist");
            throw new ManifoldCFException("The page at "+baseUrl+site+" did not exist");
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to get the acls
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("SharePoint: The user did not have access to the usergroups service for "+baseUrl+site);
            throw new ManifoldCFException("The user did not have access to the usergroups service at "+baseUrl+site);
          }
          else if (httpErrorCode.equals("403"))
            throw new ManifoldCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new ManifoldCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x80131600"))
          {
            // No such user
            return null;
          }
          if (Logging.authorityConnectors.isDebugEnabled())
          {
            org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
            String errorString = "";
            if (elem != null)
              errorString = elem2.getFirstChild().getNodeValue().trim();

            Logging.authorityConnectors.debug("SharePoint: Getting usergroups in site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString,e);
          }
          throw new ManifoldCFException("SharePoint server error code: "+sharepointErrorCode);
        }
        if (Logging.authorityConnectors.isDebugEnabled())
          Logging.authorityConnectors.debug("SharePoint: Unknown SharePoint server error getting usergroups for site "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);

        throw new ManifoldCFException("Unknown SharePoint server error: "+e.getMessage());
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }

      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("SharePoint: Got an unknown remote exception getting usergroups for "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      throw new ManifoldCFException("Remote procedure exception: "+e.getMessage(), e);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("SharePoint: Got an unexpected remote exception usergroups for site "+site,e);
      throw new ManifoldCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  *
  * @return true if connection OK
  * @throws java.net.MalformedURLException
  * @throws javax.xml.rpc.ServiceException
  * @throws java.rmi.RemoteException
  */
  public boolean checkConnection( String site )
    throws ManifoldCFException
  {
    try
    {
      if (site.equals("/"))
        site = "";

      UserGroupWS userService = new UserGroupWS( baseUrl + site, userName, password, configuration, httpClient  );
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall = userService.getUserGroupSoapHandler( );

      // Get the info for the admin user
      com.microsoft.schemas.sharepoint.soap.directory.GetUserInfoResponseGetUserInfoResult userResp = userCall.getUserInfo( mapToClaimSpace(userName) );
      org.apache.axis.message.MessageElement[] userList = userResp.get_any();

      return true;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new ManifoldCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.authorityConnectors.isDebugEnabled())
        Logging.authorityConnectors.debug("SharePoint: Got a service exception checking connection",e);
      throw new ManifoldCFException("Service exception: "+e.getMessage(), e);
    }
    catch (org.apache.axis.AxisFault e)
    {
      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HTTP")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://xml.apache.org/axis/","HttpErrorCode"));
        if (elem != null)
        {
          elem.normalize();
          String httpErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (httpErrorCode.equals("404"))
          {
            // Page did not exist
            throw new ManifoldCFException("The site at "+baseUrl+site+" did not exist");
          }
          else if (httpErrorCode.equals("401"))
            throw new ManifoldCFException("User did not authenticate properly, or has insufficient permissions to access "+baseUrl+site+": "+e.getMessage(),e);
          else if (httpErrorCode.equals("403"))
            throw new ManifoldCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new ManifoldCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new ManifoldCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
          String errorString = "";
          if (elem != null)
            errorString = elem2.getFirstChild().getNodeValue().trim();

          throw new ManifoldCFException("Accessing site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString,e);
        }
        throw new ManifoldCFException("Unknown SharePoint server error accessing site "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }

      throw new ManifoldCFException("Got an unknown remote exception accessing site "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      throw new ManifoldCFException("Got an unexpected remote exception accessing site "+site+": "+e.getMessage(),e);
    }
  }
  
  /** Conditionally map SharePoint user login name to claim space.
  */
  protected String mapToClaimSpace(String userLoginName)
  {
    if (isClaimSpace)
    {
      return "i:0#.w|" + userLoginName.toLowerCase(java.util.Locale.ROOT);
    }
    return userLoginName;
  }

  /**
  * SharePoint UserGroup Service Wrapper Class
  */
  protected static class UserGroupWS extends com.microsoft.schemas.sharepoint.soap.directory.UserGroupLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = -2052484076803624502L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private HttpClient httpClient;

    public UserGroupWS ( String siteUrl, String userName, String password, EngineConfiguration configuration, HttpClient httpClient )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/usergroup.asmx");
      this.userName = userName;
      this.password = password;
      this.httpClient = httpClient;
    }

    public com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap getUserGroupSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub(endPoint, this);
      _stub.setPortName(getUserGroupSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      _stub._setProperty( HTTPCLIENT_PROPERTY, httpClient );
      return _stub;
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
