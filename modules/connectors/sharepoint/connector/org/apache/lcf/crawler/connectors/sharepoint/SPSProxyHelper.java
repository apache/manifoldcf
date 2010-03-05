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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.soap.*;

import org.apache.lcf.core.common.XMLDoc;
import org.apache.lcf.core.interfaces.LCFException;
import org.apache.lcf.agents.interfaces.ServiceInterruption;
import org.apache.lcf.crawler.system.Logging;

import com.microsoft.schemas.sharepoint.dsp.*;
import com.microsoft.schemas.sharepoint.soap.*;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolFactory;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.configuration.FileProvider;

/**
*
* @author Michael Cummings
*
*/
public class SPSProxyHelper {


  public static final String PROTOCOL_FACTORY_PROPERTY = "LCF_Protocol_Factory";
  public static final String CONNECTION_MANAGER_PROPERTY = "LCF_Connection_Manager";

  private String serverUrl;
  private String serverLocation;
  private String decodedServerLocation;
  private String baseUrl;
  private String userName;
  private String password;
  private ProtocolFactory myFactory;
  private EngineConfiguration configuration;
  private HttpConnectionManager connectionManager;

  /**
  *
  * @param serverUrl
  * @param userName
  * @param password
  */
  public SPSProxyHelper( String serverUrl, String serverLocation, String decodedServerLocation, String userName, String password, ProtocolFactory myFactory, String configFileName, HttpConnectionManager connectionManager )
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
    this.myFactory = myFactory;
    this.configuration = new FileProvider(configFileName);
    this.connectionManager = connectionManager;
  }

  /**
  * Get the acls for a document library.
  * @param site
  * @param docLib is the library GUID
  * @return array of sids
  * @throws Exception
  */
  public String[] getACLs(String site, String docLib )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      if ( site.compareTo("/") == 0 ) site = ""; // root case
        UserGroupWS userService = new UserGroupWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager  );
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall = userService.getUserGroupSoapHandler( );

      PermissionsWS aclService = new PermissionsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoap aclCall = aclService.getPermissionsSoapHandler( );

      com.microsoft.schemas.sharepoint.soap.directory.GetPermissionCollectionResponseGetPermissionCollectionResult aclResult = aclCall.getPermissionCollection( docLib, "List" );
      org.apache.axis.message.MessageElement[] aclList = aclResult.get_any();

      XMLDoc doc = new XMLDoc( aclList[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:GetPermissionCollection' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }
      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("ns1:GetPermissionCollection"))
        throw new LCFException("Bad xml - outer node is not 'ns1:GetPermissionCollection'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);

      if ( nodeList.size() != 1 )
      {
        throw new LCFException( " No results found." );
      }
      parent = nodeList.get(0);
      nodeList.clear();
      doc.processPath( nodeList, "*", parent );
      java.util.HashSet sids = new java.util.HashSet();
      int i = 0;
      for (; i< nodeList.size(); i++ )
      {
        Object node = nodeList.get( i );
        String mask = doc.getValue( node, "Mask" );
        long maskValue = new Long(mask).longValue();
        if ((maskValue & 1L) == 1L)
        {
          // Permission to view
          String isUser = doc.getValue( node, "MemberIsUser" );

          if ( isUser.compareToIgnoreCase("True") == 0 )
          {
            // Use AD user or group
            String userLogin = doc.getValue( node, "UserLogin" );
            String userSid = getSidForUser( userCall, userLogin );
            sids.add( userSid );
          }
          else
          {
            // Role
            String[] roleSids;
            String roleName = doc.getValue( node, "RoleName" );
            if ( roleName.length() == 0)
            {
              roleName = doc.getValue(node,"GroupName");
              roleSids = getSidsForGroup(userCall, roleName);
            }
            else
            {
              roleSids = getSidsForRole(userCall, roleName);
            }

            int j = 0;
            for (; j < roleSids.length; j++ )
            {
              sids.add( roleSids[ j ] );
            }
          }
        }
      }

      return (String[]) sids.toArray( new String[0] );
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting the acls for site "+site+" library "+docLib+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (org.apache.axis.AxisFault e)
    {
      currentTime = System.currentTimeMillis();
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
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The page at "+baseUrl+site+" did not exist; assuming library deleted");
            return null;
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to get the acls
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The crawl user did not have access to the permissions service for "+baseUrl+site+"; skipping documents within");
            return null;
          }
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x82000006"))
          {
            // List did not exist
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The list "+docLib+" in site "+site+" did not exist; assuming library deleted");
            return null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
            {
              org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
              String errorString = "";
              if (elem != null)
                errorString = elem2.getFirstChild().getNodeValue().trim();

              Logging.connectors.debug("SharePoint: Getting permissions for the list "+docLib+" in site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString+" - Skipping",e);
            }
            return null;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Unknown SharePoint server error getting the acls for site "+site+" library "+docLib+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);

        throw new ServiceInterruption("Unknown SharePoint server error: "+e.getMessage()+" - retrying", e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unknown remote exception getting the acls for site "+site+" library "+docLib+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unexpected remote exception getting the acls for site "+site+" library "+docLib,e);
      throw new LCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  * Get the acls for a document.
  * NOTE that this function only works for SharePoint 3.0 with the MCPermissions web service installed.
  * @param site is the encoded subsite path
  * @param file is the encoded file url (not including protocol or server or location, but including encoded subsite, library and folder/file path)
  * @return array of document SIDs
  * @throws LCFException
  * @throws ServiceInterruption
  */
  public String[] getDocumentACLs(String site, String file)
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      if ( site.compareTo("/") == 0 ) site = ""; // root case

        // Calculate the full server-relative path of the file
        String encodedRelativePath = serverLocation + file;
      if (encodedRelativePath.startsWith("/"))
        encodedRelativePath = encodedRelativePath.substring(1);

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Getting document acls for site '"+site+"' file '"+file+"': Encoded relative path is '"+encodedRelativePath+"'");
      UserGroupWS userService = new UserGroupWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager  );
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall = userService.getUserGroupSoapHandler( );

      MCPermissionsWS aclService = new MCPermissionsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      com.microsoft.sharepoint.webpartpages.PermissionsSoap aclCall = aclService.getPermissionsSoapHandler( );

      com.microsoft.sharepoint.webpartpages.GetPermissionCollectionResponseGetPermissionCollectionResult aclResult = aclCall.getPermissionCollection( encodedRelativePath, "Item" );
      org.apache.axis.message.MessageElement[] aclList = aclResult.get_any();

      if (Logging.connectors.isDebugEnabled())
      {
        Logging.connectors.debug("SharePoint: document acls xml: '" + aclList[0].toString() + "'");
      }

      XMLDoc doc = new XMLDoc( aclList[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:GetPermissionCollection' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }
      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("GetPermissionCollection"))
        throw new LCFException("Bad xml - outer node is not 'GetPermissionCollection'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);

      if ( nodeList.size() != 1 )
      {
        throw new LCFException( " No results found." );
      }
      parent = nodeList.get(0);
      nodeList.clear();
      doc.processPath( nodeList, "*", parent );
      java.util.HashSet sids = new java.util.HashSet();
      int i = 0;
      for (; i< nodeList.size(); i++ )
      {
        Object node = nodeList.get( i );
        String mask = doc.getValue( node, "Mask" );
        long maskValue = new Long(mask).longValue();
        if ((maskValue & 1L) == 1L)
        {
          // Permission to view
          String isUser = doc.getValue( node, "MemberIsUser" );

          if ( isUser.compareToIgnoreCase("True") == 0 )
          {
            // Use AD user or group
            String userLogin = doc.getValue( node, "UserLogin" );
            String userSid = getSidForUser( userCall, userLogin );
            sids.add( userSid );
          }
          else
          {
            // Role
            String[] roleSids;
            String roleName = doc.getValue( node, "RoleName" );
            if ( roleName.length() == 0)
            {
              roleName = doc.getValue(node,"GroupName");
              roleSids = getSidsForGroup(userCall, roleName);
            }
            else
            {
              roleSids = getSidsForRole(userCall, roleName);
            }

            int j = 0;
            for (; j < roleSids.length; j++ )
            {
              sids.add( roleSids[ j ] );
            }
          }
        }
      }

      return (String[]) sids.toArray( new String[0] );
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting the acls for site "+site+" file "+file+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (org.apache.axis.AxisFault e)
    {
      currentTime = System.currentTimeMillis();
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
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The page at "+baseUrl+site+" did not exist; assuming library deleted");
            return null;
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to get the acls
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The crawl user did not have access to the MCPermissions service for "+baseUrl+site+"; skipping documents within");
            return null;
          }
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x82000006"))
          {
            // List did not exist
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The file "+file+" in site "+site+" did not exist; assuming file deleted");
            return null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
            {
              org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
              String errorString = "";
              if (elem != null)
                errorString = elem2.getFirstChild().getNodeValue().trim();

              Logging.connectors.debug("SharePoint: Getting permissions for the file "+file+" in site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString+" - Skipping",e);
            }
            return null;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Unknown SharePoint server error getting the acls for site "+site+" file "+file+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);

        throw new ServiceInterruption("Unknown SharePoint server error: "+e.getMessage()+" - retrying", e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unknown remote exception getting the acls for site "+site+" file "+file+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unexpected remote exception getting the acls for site "+site+" file "+file,e);
      throw new LCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  *
  * @param site
  * @param docLibrary
  * @return an XML document
  * @throws LCFException
  * @throws ServiceInterruption
  */
  public XMLDoc getDocuments(String site, String docLibrary)
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      if ( site.equals("/") ) site = ""; // root case
        StsAdapterWS listService = new StsAdapterWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      StsAdapterSoapStub stub = (StsAdapterSoapStub)listService.getStsAdapterSoapHandler();

      String[] vArray = new String[1];
      vArray[0] = "1.0";
      VersionsHeader myVersion = new VersionsHeader();
      myVersion.setVersion( vArray );

      stub.setHeader( "http://schemas.microsoft.com/sharepoint/dsp", "versions", myVersion );

      RequestHeader reqHeader = new RequestHeader();
      reqHeader.setDocument( DocumentType.content );
      reqHeader.setMethod(MethodType.query );

      stub.setHeader( "http://schemas.microsoft.com/sharepoint/dsp", "request", reqHeader );

      QueryRequest myRequest = new QueryRequest();

      DSQuery sQuery = new DSQuery();
      sQuery.setSelect( "/list[@id='" + docLibrary + "']" );
      myRequest.setDsQuery( sQuery );

      StsAdapterSoap call = stub;
      ArrayList nodeList = new ArrayList();

      QueryResponse resp = call.query( myRequest );
      org.apache.axis.message.MessageElement[] list = resp.get_any();
      if (Logging.connectors.isInfoEnabled())
      {
        Logging.connectors.info("SharePoint: list xml: '" + list[0].toString() + "'");
      }

      XMLDoc doc = new XMLDoc( list[0].toString() );

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:dsQueryResponse' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }

      Object parent = nodeList.get(0);
      //System.out.println( "Outer NodeName = " + doc.getNodeName(parent) );
      if (!doc.getNodeName(parent).equals("ns1:dsQueryResponse"))
        throw new LCFException("Bad xml - outer node is not 'ns1:dsQueryResponse'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);

      if ( nodeList.size() != 2 )
      {
        throw new LCFException( " No results found." );
      }

      return doc;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting documents for site "+site+" doclibrary "+docLibrary+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (org.apache.axis.AxisFault e)
    {
      currentTime = System.currentTimeMillis();
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
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The page at "+baseUrl+site+" did not exist; assuming library deleted");
            return null;
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to get the acls
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The crawl user did not have access to list documents for "+baseUrl+site+"; skipping documents within");
            return null;
          }
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x82000006"))
          {
            // List did not exist
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The list "+docLibrary+" in site "+site+" did not exist; assuming library deleted");
            return null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
            {
              org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
              String errorString = "";
              if (elem != null)
                errorString = elem2.getFirstChild().getNodeValue().trim();

              Logging.connectors.debug("SharePoint: Getting child documents for the list "+docLibrary+" in site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString+" - Skipping",e);
            }
            return null;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Unknown SharePoint server error getting child documents for site "+site+" library "+docLibrary+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);

        throw new ServiceInterruption("Unknown SharePoint server error: "+e.getMessage()+" - retrying",  e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unknown remote exception getting child documents for site "+site+" library "+docLibrary+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(),  e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unexpected remote exception getting child documents for site "+site+" library "+docLibrary,e);
      throw new LCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  *
  * @param parentSite
  * @param docLibrary
  * @return document library ID
  * @throws LCFException
  * @throws ServiceInterruption
  */
  public String getDocLibID(String parentSite, String parentSiteDecoded, String docLibrary)
    throws ServiceInterruption, LCFException
  {
    long currentTime;
    try
    {
      // The old code here used to call the lists service to find the guid, using the doc library url name as the title.
      // This did not work when the title differed from the url name.
      // On 5/8/2008 I modified the code to use the lists service to locate the correct record by matching the defaultViewUrl field,
      // so that we instead iterate through the children.  It's more expensive but it works.
      String parentSiteRequest = parentSite;

      if ( parentSiteRequest.equals("/"))
      {
        parentSiteRequest = ""; // root case
        parentSiteDecoded = "";
      }

      ListsWS listsService = new ListsWS( baseUrl + parentSiteRequest, userName, password, myFactory, configuration, connectionManager );
      ListsSoap listsCall = listsService.getListsSoapHandler( );

      GetListCollectionResponseGetListCollectionResult listResp = listsCall.getListCollection();
      org.apache.axis.message.MessageElement[] lists = listResp.get_any();

      XMLDoc doc = new XMLDoc( lists[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:Lists' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }
      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("ns1:Lists"))
        throw new LCFException("Bad xml - outer node is not 'ns1:Lists'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);  // <ns1:Lists>

      int chuckIndex = decodedServerLocation.length() + parentSiteDecoded.length();

      int i = 0;
      while (i < nodeList.size())
      {
        Object o = nodeList.get( i++ );

        String baseType = doc.getValue( o, "BaseType");
        if ( baseType.compareTo( "1" ) == 0 )
        {
          // We think it's a library

          // This is how we display it, so this has the right path extension
          String urlPath = doc.getValue( o, "DefaultViewUrl" );

          // It's a library.  If it has no view url, we don't have any idea what to do with it
          if (urlPath != null && urlPath.length() > 0)
          {
            if (urlPath.length() < chuckIndex)
              throw new LCFException("View url is not in the expected form: '"+urlPath+"'");
            urlPath = urlPath.substring(chuckIndex);
            if (!urlPath.startsWith("/"))
              throw new LCFException("View url without site is not in the expected form: '"+urlPath+"'");
            // We're at the library name.  Figure out where the end of it is.
            int index = urlPath.indexOf("/",1);
            if (index == -1)
              throw new LCFException("Bad view url without site: '"+urlPath+"'");
            String pathpart = urlPath.substring(1,index);

            if ( pathpart.equals(docLibrary) )
            {
              // We found it!
              // Return its ID
              return doc.getValue( o, "ID" );
            }
          }
        }
      }

      // Not found - return null
      return null;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting the library ID for site "+parentSite+" library "+docLibrary+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (org.apache.axis.AxisFault e)
    {
      currentTime = System.currentTimeMillis();
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
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The page at "+baseUrl+parentSite+" did not exist; assuming library deleted");
            return null;
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to list libraries
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The crawl user did not have access to list libraries for "+baseUrl+parentSite+"; skipping");
            return null;
          }
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+parentSite+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+parentSite+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x82000006"))
          {
            // List did not exist
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The list "+docLibrary+" in site "+parentSite+" did not exist; assuming library deleted");
            return null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
            {
              org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
              String errorString = "";
              if (elem != null)
                errorString = elem2.getFirstChild().getNodeValue().trim();

              Logging.connectors.debug("SharePoint: Getting library ID for the list "+docLibrary+" in site "+parentSite+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString+" - Skipping",e);
            }
            return null;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Unknown SharePoint server error getting library ID for site "+parentSite+" library "+docLibrary+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);

        throw new ServiceInterruption("Unknown SharePoint server error: "+e.getMessage()+" - retrying", e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unknown remote exception getting library ID for site "+parentSite+" library "+docLibrary+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unexpected remote exception getting library ID for site "+parentSite+" library "+docLibrary,e);
      throw new LCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  *
  * @param site
  * @param docPath
  * @return an XML document
  * @throws LCFException
  * @throws ServiceInterruption
  */
  public XMLDoc getVersions( String site, String docPath)
    throws ServiceInterruption, LCFException
  {
    long currentTime;
    try
    {
      if ( site.compareTo("/") == 0 ) site = ""; // root case
        VersionsWS versionsService = new VersionsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      VersionsSoap versionsCall = versionsService.getVersionsSoapHandler( );

      GetVersionsResponseGetVersionsResult versionsResp = versionsCall.getVersions( docPath );
      org.apache.axis.message.MessageElement[] lists = versionsResp.get_any();

      //System.out.println( lists[0].toString() );
      XMLDoc doc = new XMLDoc( lists[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);

      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'results' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }

      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("results"))
        throw new LCFException("Bad xml - outer node is not 'results'");

      return doc;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting versions for site "+site+" docpath "+docPath+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    }
    catch (org.apache.axis.AxisFault e)
    {
      currentTime = System.currentTimeMillis();
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
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The page at "+baseUrl+site+" did not exist; assuming library deleted");
            return null;
          }
          else if (httpErrorCode.equals("401"))
          {
            // User did not have permissions for this library to get the acls
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The crawl user did not have access to get versions for "+baseUrl+site+"; skipping");
            return null;
          }
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      else if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server")))
      {
        org.w3c.dom.Element elem = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorcode"));
        if (elem != null)
        {
          elem.normalize();
          String sharepointErrorCode = elem.getFirstChild().getNodeValue().trim();
          if (sharepointErrorCode.equals("0x82000006"))
          {
            // List did not exist
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: The docpath "+docPath+" in site "+site+" did not exist; assuming library deleted");
            return null;
          }
          else
          {
            if (Logging.connectors.isDebugEnabled())
            {
              org.w3c.dom.Element elem2 = e.lookupFaultDetail(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/soap/","errorstring"));
              String errorString = "";
              if (elem != null)
                errorString = elem2.getFirstChild().getNodeValue().trim();

              Logging.connectors.debug("SharePoint: Getting versions for the docpath "+docPath+" in site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString+" - Skipping",e);
            }
            return null;
          }
        }
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Unknown SharePoint server error getting versions for site "+site+" docpath "+docPath+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);

        throw new ServiceInterruption("Unknown SharePoint server error: "+e.getMessage()+" - retrying", e, currentTime + 300000L,
          currentTime + 3 * 60 * 60000L,-1,false);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unknown remote exception getting versions for site "+site+" docpath "+docPath+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString()+" - retrying",e);
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got an unexpected remote exception getting versions for site "+site+" docpath "+docPath,e);
      throw new LCFException("Unexpected remote procedure exception: "+e.getMessage(), e);
    }
  }

  /**
  *
  * @param userCall
  * @param userLogin
  * @return
  * @throws Exception
  */
  private String getSidForUser(com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall, String userLogin )
  throws LCFException, java.net.MalformedURLException, javax.xml.rpc.ServiceException,
    java.rmi.RemoteException
  {
    com.microsoft.schemas.sharepoint.soap.directory.GetUserInfoResponseGetUserInfoResult userResp = userCall.getUserInfo( userLogin );
    org.apache.axis.message.MessageElement[] userList = userResp.get_any();

    XMLDoc doc = new XMLDoc( userList[0].toString() );
    ArrayList nodeList = new ArrayList();

    doc.processPath(nodeList, "*", null);
    if (nodeList.size() != 1)
    {
      throw new LCFException("Bad xml - missing outer 'ns1:GetUserInfo' node - there are "+Integer.toString(nodeList.size())+" nodes");
    }
    Object parent = nodeList.get(0);
    if (!doc.getNodeName(parent).equals("ns1:GetUserInfo"))
      throw new LCFException("Bad xml - outer node is not 'ns1:GetUserInfo'");

    nodeList.clear();
    doc.processPath(nodeList, "*", parent);  // ns1:User

    if ( nodeList.size() != 1 )
    {
      throw new LCFException( " No User found." );
    }
    parent = nodeList.get(0);
    nodeList.clear();
    String sid = doc.getValue( parent, "Sid" );
    return sid;
  }

  /**
  *
  * @param userCall
  * @param roleName
  * @return
  * @throws Exception
  */
  private String[] getSidsForGroup(com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall, String groupName)
    throws LCFException, java.net.MalformedURLException, javax.xml.rpc.ServiceException, java.rmi.RemoteException
  {
    com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromGroupResponseGetUserCollectionFromGroupResult roleResp = userCall.getUserCollectionFromGroup(groupName);
    org.apache.axis.message.MessageElement[] roleList = roleResp.get_any();

    XMLDoc doc = new XMLDoc(roleList[0].toString());
    ArrayList nodeList = new ArrayList();

    doc.processPath(nodeList, "*", null);
    if (nodeList.size() != 1)
    {
      throw new LCFException("Bad xml - missing outer 'ns1:GetUserCollectionFromGroup' node - there are "
      + Integer.toString(nodeList.size()) + " nodes");
    }
    Object parent = nodeList.get(0);
    if (!doc.getNodeName(parent).equals("ns1:GetUserCollectionFromGroup"))
      throw new LCFException("Bad xml - outer node is not 'ns1:GetUserCollectionFromGroup'");

    nodeList.clear();
    doc.processPath(nodeList, "*", parent); // <ns1:Users>

    if (nodeList.size() != 1)
    {
      throw new LCFException(" No Users collection found.");
    }
    parent = nodeList.get(0);
    nodeList.clear();
    doc.processPath(nodeList, "*", parent); // <ns1:User>

    ArrayList sidsList = new ArrayList();
    String[] sids = new String[0];
    int i = 0;
    while (i < nodeList.size())
    {
      Object o = nodeList.get(i++);
      sidsList.add(doc.getValue(o, "Sid"));
    }
    sids = (String[]) sidsList.toArray((Object[]) sids);
    return sids;
  }

  /**
  *
  * @param userCall
  * @param roleName
  * @return
  * @throws Exception
  */
  private String[] getSidsForRole( com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap userCall, String roleName )
  throws LCFException, java.net.MalformedURLException, javax.xml.rpc.ServiceException,
    java.rmi.RemoteException
  {

    com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromRoleResponseGetUserCollectionFromRoleResult roleResp = userCall.getUserCollectionFromRole( roleName );
    org.apache.axis.message.MessageElement[] roleList = roleResp.get_any();

    XMLDoc doc = new XMLDoc( roleList[0].toString() );
    ArrayList nodeList = new ArrayList();

    doc.processPath(nodeList, "*", null);
    if (nodeList.size() != 1)
    {
      throw new LCFException("Bad xml - missing outer 'ns1:GetUserCollectionFromRole' node - there are "+Integer.toString(nodeList.size())+" nodes");
    }
    Object parent = nodeList.get(0);
    if (!doc.getNodeName(parent).equals("ns1:GetUserCollectionFromRole"))
      throw new LCFException("Bad xml - outer node is not 'ns1:GetUserCollectionFromRole'");

    nodeList.clear();
    doc.processPath(nodeList, "*", parent);  // <ns1:Users>

    if ( nodeList.size() != 1 )
    {
      throw new LCFException( " No Users collection found." );
    }
    parent = nodeList.get(0);
    nodeList.clear();
    doc.processPath( nodeList, "*", parent ); // <ns1:User>

    ArrayList sidsList = new ArrayList();
    String[] sids = new String[0];
    int i = 0;
    while (i < nodeList.size())
    {
      Object o = nodeList.get( i++ );
      sidsList.add( doc.getValue( o, "Sid" ) );
    }
    sids = (String[])sidsList.toArray( (Object[])sids );
    return sids;
  }

  /**
  *
  * @return true if connection OK
  * @throws java.net.MalformedURLException
  * @throws javax.xml.rpc.ServiceException
  * @throws java.rmi.RemoteException
  */
  public boolean checkConnection( String site, boolean sps30 )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      if (site.equals("/"))
        site = "";

      // Attempt a listservice call
      ListsWS listService = new ListsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      ListsSoap listCall = listService.getListsSoapHandler();
      listCall.getListCollection();

      // If this is 3.0, we should also attempt to reach our custom webservice
      if (sps30)
      {
        // The web service allows us to get acls for a site, so that's what we will attempt

        // This fails:
        MCPermissionsWS aclService = new MCPermissionsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
        com.microsoft.sharepoint.webpartpages.PermissionsSoap aclCall = aclService.getPermissionsSoapHandler( );

        // This works:
        //PermissionsWS aclService = new PermissionsWS( baseUrl + site, userName, password, myFactory, configuration );
        //com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoap aclCall = aclService.getPermissionsSoapHandler( );

        aclCall.getPermissionCollection( "/", "Web" );
      }

      return true;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception checking connection - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
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
            throw new LCFException("The site at "+baseUrl+site+" did not exist");
          }
          else if (httpErrorCode.equals("401"))
            throw new LCFException("Crawl user did not authenticate properly, or has insufficient permissions to access "+baseUrl+site+": "+e.getMessage(),e);
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Http error "+httpErrorCode+" while reading from "+baseUrl+site+" - check IIS and SharePoint security settings! "+e.getMessage(),e);
          else
            throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
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

          throw new LCFException("Accessing site "+site+" failed with unexpected SharePoint error code "+sharepointErrorCode+": "+errorString,e);
        }
        throw new LCFException("Unknown SharePoint server error accessing site "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      throw new LCFException("Got an unknown remote exception accessing site "+site+" - axis fault = "+e.getFaultCode().getLocalPart()+", detail = "+e.getFaultString(),e);
    }
    catch (java.rmi.RemoteException e)
    {
      // We expect the axis exception to be thrown, not this generic one!
      // So, fail hard if we see it.
      throw new LCFException("Got an unexpected remote exception accessing site "+site+": "+e.getMessage(),e);
    }
  }

  /**
  * Gets a list of field names of the given document library
  * @param site
  * @param docLibrary
  * @return list of the fields
  */
  public Map getFieldList( String site, String docLibrary )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      HashMap result = new HashMap();

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: In getFieldList; site='"+site+"', docLibrary='"+docLibrary+"'");

      // The docLibrary must be a GUID, because we don't have  title.

      if ( site.compareTo( "/") == 0 ) site = "";
        ListsWS listService = new ListsWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      ListsSoap listCall = listService.getListsSoapHandler();

      GetListResponseGetListResult listResponse = listCall.getList( docLibrary );
      org.apache.axis.message.MessageElement[] List = listResponse.get_any();

      XMLDoc doc = new XMLDoc( List[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer node - there are "+Integer.toString(nodeList.size())+" nodes");
      }

      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("ns1:List"))
        throw new LCFException("Bad xml - outer node is '" + doc.getNodeName(parent) + "' not 'ns1:List'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);  // <ns1:Fields>

      Object fields = nodeList.get(0);
      if ( !doc.getNodeName(fields).equals("ns1:Fields") )
        throw new LCFException( "Bad xml - child node 0 '" + doc.getNodeName(fields) + "' is not 'ns1:Fields'");

      nodeList.clear();
      doc.processPath(nodeList, "*", fields);

      int i = 0;
      while (i < nodeList.size())
      {
        Object o = nodeList.get( i++ );
        // Logging.connectors.debug( i + ": " + o );
        String name = doc.getValue( o, "DisplayName" );
        String fieldName = doc.getValue( o, "Name" );
        String hidden = doc.getValue( o, "Hidden" );
        // System.out.println( "Hidden :" + hidden );
        if ( name.length() != 0 && fieldName.length() != 0 && ( !hidden.equalsIgnoreCase( "true") ) )
        {
          // make sure we don't include the same field more than once.
          // This may happen if the Library has more than one view.
          if ( result.containsKey( fieldName ) == false)
            result.put(fieldName, name);
        }
      }
      // System.out.println(result.size());
      return result;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting field list for site "+site+" library "+docLibrary+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
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
            return null;
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Remote procedure exception: "+e.getMessage(),e);
          else if (httpErrorCode.equals("401"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Crawl user does not have sufficient privileges to get field list for site "+site+" library "+docLibrary+" - skipping",e);
            return null;
          }
          throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      // I don't know if this is what you get when the library is missing, but here's hoping.
      if (e.getMessage().indexOf("List does not exist") != -1)
        return null;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a remote exception getting field list for site "+site+" library "+docLibrary+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      throw new LCFException("Unexpected remote exception occurred: "+e.getMessage(),e);
    }
  }

  /**
  * Gets a list of field values of the given document
  * @param fieldNames
  * @param site
  * @param docId
  * @return set of the field values
  */
  public Map getFieldValues( ArrayList fieldNames, String site, String docLibrary, String docId )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      HashMap result = new HashMap();

      if ( site.compareTo("/") == 0 ) site = ""; // root case
        StsAdapterWS listService = new StsAdapterWS( baseUrl + site, userName, password, myFactory, configuration, connectionManager );
      StsAdapterSoapStub stub = (StsAdapterSoapStub)listService.getStsAdapterSoapHandler();

      String[] vArray = new String[1];
      vArray[0] = "1.0";
      VersionsHeader myVersion = new VersionsHeader();
      myVersion.setVersion( vArray );

      stub.setHeader( "http://schemas.microsoft.com/sharepoint/dsp", "versions", myVersion );

      RequestHeader reqHeader = new RequestHeader();
      reqHeader.setDocument( DocumentType.content );
      reqHeader.setMethod(MethodType.query );

      stub.setHeader( "http://schemas.microsoft.com/sharepoint/dsp", "request", reqHeader );

      QueryRequest myRequest = new QueryRequest();

      DSQuery sQuery = new DSQuery();
      sQuery.setSelect( "/list[@id='" + docLibrary + "']" );
      sQuery.setResultContent(ResultContentType.dataOnly);
      myRequest.setDsQuery( sQuery );

      DspQuery spQuery = new DspQuery();
      spQuery.setRowLimit( 1 );
      // For the Requested Fields
      if ( fieldNames.size() > 0 )
      {
        Fields spFields = new Fields();
        Field[] fieldArray = new Field[0];
        ArrayList fields = new ArrayList();

        Field spField = new Field();
        //                      spField.setName( "ID" );
        //                      spField.setAlias( "ID" );
        //                      fields.add( spField );

        for ( int k = 0; k < fieldNames.size(); k++ )
        {
          spField = new Field();
          spField.setName( (String)fieldNames.get(k) );
          spField.setAlias( (String)fieldNames.get(k) );
          fields.add( spField );
        }
        spFields.setField( (Field[]) fields.toArray( fieldArray ));
        spQuery.setFields( spFields );
      }
      // Of this document
      DspQueryWhere spWhere = new DspQueryWhere();

      org.apache.axis.message.MessageElement criterion = new org.apache.axis.message.MessageElement( (String)null, "Contains" );
      SOAPElement seFieldRef = criterion.addChildElement( "FieldRef" );
      seFieldRef.addAttribute( SOAPFactory.newInstance().createName("Name") , "FileRef" );
      SOAPElement seValue = criterion.addChildElement( "Value" );
      seValue.addAttribute( SOAPFactory.newInstance().createName("Type") , "String" );
      seValue.setValue( docId );

      org.apache.axis.message.MessageElement[] criteria = { criterion };
      spWhere.set_any( criteria );
      spQuery.setWhere( (DspQueryWhere)spWhere );

      // Set Criteria
      myRequest.getDsQuery().setQuery(spQuery);

      StsAdapterSoap call = stub;

      // Make Request
      QueryResponse resp = call.query( myRequest );
      org.apache.axis.message.MessageElement[] list = resp.get_any();

      if (Logging.connectors.isDebugEnabled())
      {
        Logging.connectors.debug("SharePoint: list xml: '" + list[0].toString() + "'");
      }

      XMLDoc doc = new XMLDoc( list[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:dsQueryResponse' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }

      Object parent = nodeList.get(0);
      //System.out.println( "Outer NodeName = " + doc.getNodeName(parent) );
      if (!doc.getNodeName(parent).equals("ns1:dsQueryResponse"))
        throw new LCFException("Bad xml - outer node is not 'ns1:dsQueryResponse'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);

      parent = nodeList.get( 0 ); // <Shared_X0020_Documents />

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);

      // Process each result (Should only be one )
      // Get each childs Value and add to return array
      for ( int i= 0; i < nodeList.size(); i++ )
      {
        Object documentNode = nodeList.get( i );
        ArrayList fieldList = new ArrayList();

        doc.processPath( fieldList, "*", documentNode );
        for ( int j =0; j < fieldList.size(); j++)
        {
          Object field = fieldList.get( j );
          String fieldData = doc.getData(field);
          String fieldName = doc.getNodeName(field);
          // Right now this really only works right for single-valued fields.  For multi-valued
          // fields, we'd need to know in advance that they were multivalued
          // so that we could interpret commas as value separators.
          result.put(fieldName,fieldData);
        }
      }

      return result;
    }
    catch (javax.xml.soap.SOAPException e)
    {
      throw new LCFException("Soap exception: "+e.getMessage(),e);
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting field values for site "+site+" library "+docLibrary+" document '"+docId+"' - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
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
            return null;
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Remote procedure exception: "+e.getMessage(),e);
          else if (httpErrorCode.equals("401"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Crawl user does not have sufficient privileges to get field values for site "+site+" library "+docLibrary+" - skipping",e);
            return null;
          }
          throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+site+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      // I don't know if this is what you get when the library is missing, but here's hoping.
      if (e.getMessage().indexOf("List does not exist") != -1)
        return null;

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a remote exception getting field values for site "+site+" library "+docLibrary+" document ["+docId+"] - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      throw new LCFException("Unexpected remote exception occurred: "+e.getMessage(),e);
    }
  }

  /**
  * Gets a list of sites given a parent site
  * @param parentSite the site to search for subsites, empty string for root
  * @return lists of sites as an arraylist of NameValue objects
  */
  public ArrayList getSites( String parentSite )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      ArrayList result = new ArrayList();

      if ( parentSite.equals( "/") ) parentSite = "";
        WebsWS webService = new WebsWS( baseUrl + parentSite, userName, password, myFactory, configuration, connectionManager );
      WebsSoap webCall = webService.getWebsSoapHandler();

      GetWebCollectionResponseGetWebCollectionResult webResp = webCall.getWebCollection();
      org.apache.axis.message.MessageElement[] webList = webResp.get_any();

      XMLDoc doc = new XMLDoc( webList[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:Webs' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }
      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("ns1:Webs"))
        throw new LCFException("Bad xml - outer node is not 'ns1:Webs'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);  // <ns1:Webs>

      int i = 0;
      while (i < nodeList.size())
      {
        Object o = nodeList.get( i++ );
        //Logging.connectors.debug( i + ": " + o );
        //System.out.println( i + ": " + o );
        String url = doc.getValue( o, "Url" );
        String title = doc.getValue( o, "Title" );

        // Leave here for now
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("SharePoint: Subsite list: '"+url+"', '"+title+"'");

        // A full path to the site is tacked on the front of each one of these.  However, due to nslookup differences, we cannot guarantee that
        // the server name part of the path will actually match what got passed in.  Therefore, we want to look only at the last path segment, whatever that is.
        if (url != null && url.length() > 0)
        {
          int lastSlash = url.lastIndexOf("/");
          if (lastSlash != -1)
          {
            String pathValue = url.substring(lastSlash + 1);
            if (pathValue.length() > 0)
            {
              if (title == null || title.length() == 0)
                title = pathValue;
              result.add(new NameValue(pathValue,title));
            }
          }
        }
      }

      return result;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting subsites for site "+parentSite+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
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
            return null;
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Remote procedure exception: "+e.getMessage(),e);
          else if (httpErrorCode.equals("401"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Crawl user does not have sufficient privileges to get subsites of site "+parentSite+" - skipping",e);
            return null;
          }
          throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+parentSite+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }

      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a remote exception getting subsites for site "+parentSite+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      throw new LCFException("Unexpected remote exception occurred: "+e.getMessage(),e);
    }

  }

  /**
  * Gets a list of document libraries given a parent site
  * @param parentSite the site to search for document libraries, empty string for root
  * @return lists of NameValue objects, representing document libraries
  */
  public ArrayList getDocumentLibraries( String parentSite, String parentSiteDecoded )
    throws LCFException, ServiceInterruption
  {
    long currentTime;
    try
    {
      ArrayList result = new ArrayList();

      String parentSiteRequest = parentSite;

      if ( parentSiteRequest.equals("/"))
      {
        parentSiteRequest = ""; // root case
        parentSiteDecoded = "";
      }

      ListsWS listsService = new ListsWS( baseUrl + parentSiteRequest, userName, password, myFactory, configuration, connectionManager );
      ListsSoap listsCall = listsService.getListsSoapHandler( );

      GetListCollectionResponseGetListCollectionResult listResp = listsCall.getListCollection();
      org.apache.axis.message.MessageElement[] lists = listResp.get_any();

      //if ( parentSite.compareTo("/Sample2") == 0) System.out.println( lists[0].toString() );

      XMLDoc doc = new XMLDoc( lists[0].toString() );
      ArrayList nodeList = new ArrayList();

      doc.processPath(nodeList, "*", null);
      if (nodeList.size() != 1)
      {
        throw new LCFException("Bad xml - missing outer 'ns1:Lists' node - there are "+Integer.toString(nodeList.size())+" nodes");
      }
      Object parent = nodeList.get(0);
      if (!doc.getNodeName(parent).equals("ns1:Lists"))
        throw new LCFException("Bad xml - outer node is not 'ns1:Lists'");

      nodeList.clear();
      doc.processPath(nodeList, "*", parent);  // <ns1:Lists>

      int chuckIndex = decodedServerLocation.length() + parentSiteDecoded.length();

      int i = 0;
      while (i < nodeList.size())
      {
        Object o = nodeList.get( i++ );

        String baseType = doc.getValue( o, "BaseType");
        if ( baseType.compareTo( "1" ) == 0 )
        {
          // We think it's a library

          // This is how we display it, so this has the right path extension
          String urlPath = doc.getValue( o, "DefaultViewUrl" );
          // This is the pretty name
          String title = doc.getValue( o, "Title" );

          // Leave this in for the moment
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("SharePoint: Library list: '"+urlPath+"', '"+title+"'");

          // It's a library.  If it has no view url, we don't have any idea what to do with it
          if (urlPath != null && urlPath.length() > 0)
          {
            if (urlPath.length() < chuckIndex)
              throw new LCFException("View url is not in the expected form: '"+urlPath+"'");
            urlPath = urlPath.substring(chuckIndex);
            if (!urlPath.startsWith("/"))
              throw new LCFException("View url without site is not in the expected form: '"+urlPath+"'");
            // We're at the library name.  Figure out where the end of it is.
            int index = urlPath.indexOf("/",1);
            if (index == -1)
              throw new LCFException("Bad view url without site: '"+urlPath+"'");
            String pathpart = urlPath.substring(1,index);

            if ( pathpart.length() != 0 && !pathpart.equals("_catalogs"))
            {
              if (title == null || title.length() == 0)
                title = pathpart;
              result.add( new NameValue(pathpart, title) );
            }
          }
        }
      }

      return result;
    }
    catch (java.net.MalformedURLException e)
    {
      throw new LCFException("Bad SharePoint url: "+e.getMessage(),e);
    }
    catch (javax.xml.rpc.ServiceException e)
    {
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a service exception getting document libraries for site "+parentSite+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Service exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
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
            return null;
          else if (httpErrorCode.equals("403"))
            throw new LCFException("Remote procedure exception: "+e.getMessage(),e);
          else if (httpErrorCode.equals("401"))
          {
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("SharePoint: Crawl user does not have sufficient privileges to read document libraries for site "+parentSite+" - skipping",e);
            return null;
          }
          throw new LCFException("Unexpected http error code "+httpErrorCode+" accessing SharePoint at "+baseUrl+parentSite+": "+e.getMessage(),e);
        }
        throw new LCFException("Unknown http error occurred: "+e.getMessage(),e);
      }
      if (e.getFaultCode().equals(new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/","Server.userException")))
      {
        String exceptionName = e.getFaultString();
        if (exceptionName.equals("java.lang.InterruptedException"))
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("SharePoint: Got a remote exception reading document libraries for site "+parentSite+" - retrying",e);
      currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Remote procedure exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
    }
    catch (java.rmi.RemoteException e)
    {
      throw new LCFException("Unexpected remote exception occurred: "+e.getMessage(),e);
    }
  }

  /**
  * SharePoint Permissions Service Wrapper Class
  */
  protected static class PermissionsWS extends com.microsoft.schemas.sharepoint.soap.directory.PermissionsLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = -2542430113046450050L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public PermissionsWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/Permissions.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoap getPermissionsSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoapStub(endPoint, this);
      _stub.setPortName(getPermissionsSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

  /**
  * MC Permissions Service Wrapper Class
  */
  protected static class MCPermissionsWS extends com.microsoft.sharepoint.webpartpages.PermissionsLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = -2542430113046450051L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public MCPermissionsWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/MCPermissions.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.sharepoint.webpartpages.PermissionsSoap getPermissionsSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.sharepoint.webpartpages.PermissionsSoapStub _stub = new com.microsoft.sharepoint.webpartpages.PermissionsSoapStub(endPoint, this);
      _stub.setPortName(getPermissionsSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
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
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public UserGroupWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/usergroup.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap getUserGroupSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub(endPoint, this);
      _stub.setPortName(getUserGroupSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

  /**
  * SharePoint StsAdapter (List Data Services) Service Wrapper Class
  */
  protected static class StsAdapterWS extends StsAdapterLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = -731937337802481409L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public StsAdapterWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/dspsts.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.dsp.StsAdapterSoap getStsAdapterSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.dsp.StsAdapterSoapStub _stub = new com.microsoft.schemas.sharepoint.dsp.StsAdapterSoapStub(endPoint, this);
      _stub.setPortName(getStsAdapterSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

  /**
  * SharePoint Lists Service Wrapper Class
  */
  protected static class ListsWS extends ListsLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = 5506842429029882999L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public ListsWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/lists.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.soap.ListsSoap getListsSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.ListsSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.ListsSoapStub(endPoint, this);
      _stub.setPortName(getListsSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

  /**
  * SharePoint Versions Service Wrapper Class
  */
  protected static class VersionsWS extends VersionsLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = 4903552161088337964L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public VersionsWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/versions.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.soap.VersionsSoap getVersionsSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.VersionsSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.VersionsSoapStub(endPoint, this);
      _stub.setPortName(getVersionsSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

  /**
  * SharePoint Webs Service Wrapper Class
  */
  protected static class WebsWS extends WebsLocator
  {
    /**
    *
    */
    private static final long serialVersionUID = 6879757392680147691L;
    private java.net.URL endPoint;
    private String userName;
    private String password;
    private ProtocolFactory myFactory;
    private HttpConnectionManager connectionManager;

    public WebsWS ( String siteUrl, String userName, String password, ProtocolFactory myFactory, EngineConfiguration configuration, HttpConnectionManager connectionManager )
      throws java.net.MalformedURLException
    {
      super(configuration);
      endPoint = new java.net.URL(siteUrl + "/_vti_bin/webs.asmx");
      this.userName = userName;
      this.password = password;
      this.myFactory = myFactory;
      this.connectionManager = connectionManager;
    }

    public com.microsoft.schemas.sharepoint.soap.WebsSoap getWebsSoapHandler( )
      throws javax.xml.rpc.ServiceException, org.apache.axis.AxisFault
    {
      com.microsoft.schemas.sharepoint.soap.WebsSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.WebsSoapStub(endPoint, this);
      _stub.setPortName(getWebsSoapWSDDServiceName());
      _stub.setUsername( userName );
      _stub.setPassword( password );
      if (myFactory != null)
        _stub._setProperty( PROTOCOL_FACTORY_PROPERTY, myFactory );
      if (connectionManager != null)
        _stub._setProperty( CONNECTION_MANAGER_PROPERTY, connectionManager );
      return _stub;
    }
  }

}
