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

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
import javax.xml.ws.BindingProvider;

import com.opentext.ecm.api.OTAuthentication;
import com.opentext.livelink.service.core.Authentication;
import com.opentext.livelink.service.core.Authentication_Service;
import com.opentext.livelink.service.core.ContentService;
import com.opentext.livelink.service.core.ContentService_Service;
import com.opentext.livelink.service.memberservice.MemberService;
import com.opentext.livelink.service.memberservice.MemberService_Service;
import com.opentext.livelink.service.docman.DocumentManagement;
import com.opentext.livelink.service.docman.DocumentManagement_Service;

import com.opentext.livelink.service.docman.AttributeGroup;
import com.opentext.livelink.service.docman.CategoryInheritance;
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
  private final MemberService_Service memberServiceService;
  
  // Authentication support
  private final Authentication authClientHandle;
  private final DocumentManagement documentManagementHandle;
  private final ContentService contentServiceHandle;
  private final MemberService memberServiceHandle;
  
  // Transient data
  
  // Cached root node types
  private List<? extends String> rootNodeTypes = null;
  
  // Cached workspace root nodes
  private Map<String, Node> workspaceTypeNodes = new HashMap<>();
  
  // Transient data that will need to be periodically rebuilt
  private long currentSessionExpiration = -1L;
  private String currentAuthToken = null;
  
  public CswsSession(final String userName,
    final String password, 
    final long sessionExpirationInterval,
    final String authenticationServiceURL,
    final String documentManagementServiceURL,
    final String contentServiceServiceURL,
    final String memberServiceServiceURL) {
      
    // Save username/password
    this.userName = userName;
    this.password = password;
    // Save expiration interval
    this.sessionExpirationInterval = sessionExpirationInterval;
    // Construct service references from the URLs
    this.authService = new Authentication_Service();
    this.documentManagementService = new DocumentManagement_Service();
    this.contentServiceService = new ContentService_Service();
    this.memberServiceService = new MemberService_Service();
    // Initialize authclient etc.
    this.authClientHandle = authService.getBasicHttpBindingAuthentication();
    this.documentManagementHandle = documentManagementService.getBasicHttpBindingDocumentManagement();
    this.contentServiceHandle = contentServiceService.getBasicHttpBindingContentService();
    this.memberServiceHandle = memberServiceService.getBasicHttpBindingMemberService();
    // Set up endpoints
    ((BindingProvider)authClientHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, authenticationServiceURL);
    ((BindingProvider)documentManagementHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, documentManagementServiceURL);
    ((BindingProvider)contentServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, contentServiceServiceURL);
    ((BindingProvider)memberServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, memberServiceServiceURL);
  }

  /**
   * Fetch initialized DocumentManagement handle.
   */
  public DocumentManagement getDocumentManagementHandle() {
    // MHL to set up transport etc.
    return documentManagementHandle;
  }

  /**
   * Fetch initialized ContentService handle.
   */
  public ContentService getContentServiceHandle() {
    // MHL to set up transport etc.
    return contentServiceHandle;
  }
  
  /** 
   * Fetch initialized MemberService handle.
   */
  public MemberService getMemberServiceHandle() {
    // MHL
    return memberServiceHandle;
  }
  
  // Accessors for information that only needs to be accessed once per session, which we will cache
  
  /**
   * Fetch root node types.   These will be cached so we only need to do it once.
   */
  public List<? extends String> getRootNodeTypes() {
    if (rootNodeTypes == null) {
      // Fetch them
      this.rootNodeTypes = getDocumentManagementHandle().getRootNodeTypes(getOTAuthentication());
    }
    return this.rootNodeTypes;
  }
  
  /**
   * Fetch root node given type.
   */
  public Node getRootNode(final String nodeType) {
    Node thisWorkspaceNode = workspaceTypeNodes.get(nodeType);
    if (thisWorkspaceNode == null) {
      thisWorkspaceNode = getDocumentManagementHandle().getRootNode(nodeType, getOTAuthentication());
      workspaceTypeNodes.put(nodeType, thisWorkspaceNode);
    }
    return thisWorkspaceNode;
  }
  
  // Helper methods -- helpful simplifications of API
  
  public List<? extends Node> getChildren(final String nodeId) {
    final GetNodesInContainerOptions gnico = new GetNodesInContainerOptions();
    // Depth 0 - default listing and Depth 1 - One level down
    gnico.setMaxDepth(0);
    // We're listing folder by folder so hopefully this is nowhere near what we'll ever get back
    gnico.setMaxResults(1000000);
    return getDocumentManagementHandle().getNodesInContainer(nodeId, gnico);
  }
  
  public List<? extends CategoryInheritance> getCategoryInheritance(final String parentId) {
    return getDocumentManagementHandle().getCategoryInheritance(parentId, getOTAuthentication());
  }

  public Node getNode(final String parentId) {
    return getDocumentManagementHandle().getNode(parentId, getOTAuthentication());
  }
  
  // Construct authentication token argument, which must be passed as last argument for every method
  
  /**
   * Construct OTAuthentication structure (to be passed as an argument)
   */
  public OTAuthentication getOTAuthentication() {
    final String authToken = getAuthToken();
    // Create the OTAuthentication object and set the authentication token
    final OTAuthentication otAuth = new OTAuthentication();
    otAuth.setAuthenticationToken(authToken);
    return otAuth;
  }

  // Private methods
  
  private String getAuthToken() {
    final long currentTime = System.currentTimeMillis();
    if (currentSessionExpiration == -1L || currentTime > currentSessionExpiraton) {
      // Kill current auth token etc
      currentSessionExpiration = -1L;
      currentAuthToken = null;
      // Refetch the auth token (this may fail)
      currentAuthToken = authClient.authenticateUser(userName, password);
      currentSessionExpiration = currentTime + expirationInterval;
    }
    return currentAuthToken;
  }
  
}
