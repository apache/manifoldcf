/* $Id: LiveLinkParameters.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.csws;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPFaultException;

import com.opentext.ecm.api.OTAuthentication;
import com.opentext.livelink.service.core.Authentication;
import com.opentext.livelink.service.core.Authentication_Service;
import com.opentext.livelink.service.core.ContentService;
import com.opentext.livelink.service.core.ContentService_Service;
import com.opentext.livelink.service.core.FileAtts;
import com.opentext.livelink.service.docman.AttributeGroup;
import com.opentext.livelink.service.docman.CategoryInheritance;
import com.opentext.livelink.service.docman.DocumentManagement;
import com.opentext.livelink.service.docman.DocumentManagement_Service;
import com.opentext.livelink.service.docman.GetNodesInContainerOptions;
import com.opentext.livelink.service.docman.Node;


/** This class describes a livelink csws session.  It manages OAuth authentication
* and provides logged-in access to csws services via methods provided within.
*/
public class CswsSession
{

  // Namespaces for the SOAP headers
  private final static String ECM_API_NAMESPACE = "urn:api.ecm.opentext.com";
  private final static String CORE_NAMESPACE = "urn:Core.service.livelink.opentext.com";

  private final String userName;
  private final String password;
  private final long expirationInterval;
  private final Authentication_Service authService;
  private final ContentService_Service contentServiceService;
  private final DocumentManagement_Service documentManagementService;
  
  // Authentication support
  private final Authentication authClientHandle;
  private final DocumentManagement documentManagementHandle;
  private final ContentService contentServiceHandle;
  
  // Transient data that will need to be periodically rebuilt
  private long currentSessionExpiration = -1L;
  private String currentAuthToken = null;
  
  public CswsSession(final String userName,
    final String password, 
    final long sessionExpirationInterval,
    final Authentication_Service authenticationService,
    final ContentService_Service contentServiceService,
    final DocumentManagement_Service documentManagementService) {
    this.userName = userName;
    this.password = password;
    this.sessionExpirationInterval = sessionExpirationInterval;
    this.authService = authService;
    this.contentServiceService = contentServiceService;
    this.documentManagementService = documentManagementService;

    // Initialize authclient etc.
    this.authClientHandle = authService.getBasicHttpBindingAuthentication();
    this.documentManagementHandle = documentManagementService.getBasicHttpBindingDocumentManagement();
    this.contentServiceHandle = contentServiceService.getBasicHttpBindingContentService();
  }
  
  public ContentService getContentServiceHandle() {
    // MHL
    return null;
  }
  
  public DocumentManagement getDocumentManagementHandle() {
    // Set the outgoing headers; all we need is the auth token
    // Create a SOAP header
    final SOAPHeader header = MessageFactory.newInstance().createMessage().getSOAPPart().getEnvelope().getHeader();
    //((WSBindingProvider)documentManagementHandle).setOutboundHeaders(createAuthHeader(header));
    
    List<String> rootNodeTypes = documentManagementHandle.getRootNodeTypes();

    return documentManagementHandle;
  }
  
  // Private methods
  
  /*
  private Header createAuthHeader(final SOAPHeader header) {
    final String authToken = getAuthToken();
    // Create the OTAuthentication object and set the authentication token
    final OTAuthentication otAuth = new OTAuthentication();
    otAuth.setAuthenticationToken(authToken);

    // We need to manually set the SOAP header to include the authentication token

    // Add the OTAuthentication SOAP header element
    final SOAPHeaderElement otAuthElement = header.addHeaderElement(new QName(ECM_API_NAMESPACE, "OTAuthentication"));

    // Add the AuthenticationToken SOAP element
    final SOAPElement authTokenElement = otAuthElement.addChildElement(new QName(ECM_API_NAMESPACE, "AuthenticationToken"));
    authTokenElement.addTextNode(otAuth.getAuthenticationToken());
    return Headers.create(otAuthElement);
  }
  */
  
  /*
  private static Header getContentIDHeader(final SOAPHeader header, String contentID) {
    final SOAPHeaderElement contentIDElement = header.addHeaderElement(new QName(CORE_NAMESPACE, "contextID"));
    contentIDElement.addTextNode(contentID);
    return Headers.create(contentIDElement);
  }
  */
  
  private String getAuthToken() {
    final long currentTime = System.currentTimeMillis();
    if (currentSessionExpiration == -1L || currentTime > currentSessionExpiraton) {
      // Kill current auth token etc
      currentSessionExpiration = -1L;
      currentAuthToken = null;
      documentManagementHandle = null;
      contentServiceHandle = null;
      // Refetch the auth token (this may fail)
      currentAuthToken = authClient.authenticateUser(userName, password);
      currentSessionExpiration = currentTime + expirationInterval;
    }
    return currentAuthToken;
  }
  
}
