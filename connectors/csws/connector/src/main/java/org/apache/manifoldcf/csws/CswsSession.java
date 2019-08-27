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

import java.net.URL;
import java.net.MalformedURLException;

import java.io.OutputStream;
import java.io.IOException;

import org.apache.cxf.transport.http.HttpConduitFeature;
import org.apache.cxf.transport.http.HttpConduitConfig;
import org.apache.cxf.configuration.jsse.TLSClientParameters;

import javax.activation.DataHandler;
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
import javax.xml.ws.Holder;

import com.opentext.ecm.api.OTAuthentication;
import com.opentext.livelink.service.core.PageHandle;
import com.opentext.livelink.service.core.Authentication;
import com.opentext.livelink.service.core.Authentication_Service;
import com.opentext.livelink.service.core.ContentService;
import com.opentext.livelink.service.core.ContentService_Service;
import com.opentext.livelink.service.memberservice.MemberService;
import com.opentext.livelink.service.memberservice.MemberService_Service;
import com.opentext.livelink.service.docman.DocumentManagement;
import com.opentext.livelink.service.docman.DocumentManagement_Service;
import com.opentext.livelink.service.searchservices.SearchService;
import com.opentext.livelink.service.searchservices.SearchService_Service;
import com.opentext.livelink.service.memberservice.SearchMatching;
import com.opentext.livelink.service.memberservice.SearchColumn;

import com.opentext.livelink.service.memberservice.MemberSearchOptions;
import com.opentext.livelink.service.memberservice.MemberSearchResults;
import com.opentext.livelink.service.docman.AttributeGroup;
import com.opentext.livelink.service.docman.CategoryInheritance;
import com.opentext.livelink.service.docman.GetNodesInContainerOptions;
import com.opentext.livelink.service.docman.Node;
import com.opentext.livelink.service.docman.NodePermissions;
import com.opentext.livelink.service.docman.Version;
import com.opentext.livelink.service.docman.NodeRights;
import com.opentext.livelink.service.docman.AttributeGroupDefinition;
import com.opentext.livelink.service.docman.Attribute;
import com.opentext.livelink.service.memberservice.User;
import com.opentext.livelink.service.memberservice.Member;
import com.opentext.livelink.service.memberservice.Group;
import com.opentext.livelink.service.searchservices.SResultPage;
import com.opentext.livelink.service.searchservices.SNode;
import com.opentext.livelink.service.searchservices.SGraph;
import com.opentext.livelink.service.searchservices.SingleSearchRequest;
import com.opentext.livelink.service.searchservices.SingleSearchResponse;
import com.opentext.livelink.service.memberservice.SearchScope;
import com.opentext.livelink.service.memberservice.SearchFilter;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.connectorcommon.interfaces.*;

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
  private final long sessionExpirationInterval;
  private final Authentication_Service authService;
  private final ContentService_Service contentServiceService;
  private final DocumentManagement_Service documentManagementService;
  private final MemberService_Service memberServiceService;
  private final SearchService_Service searchServiceService;

  // Authentication support
  private final Authentication authClientHandle;
  private final DocumentManagement documentManagementHandle;
  private final ContentService contentServiceHandle;
  private final MemberService memberServiceHandle;
  private final SearchService searchServiceHandle;
  
  // Transient data
  
  // Cached root node types
  private List<? extends String> rootNodeTypes = null;
  
  // Cached workspace root nodes
  private Map<String, Node> workspaceTypeNodes = new HashMap<>();
  
  private final static String sslSocketFactoryProperty = "com.sun.xml.internal.ws.transport.https.client.SSLSocketFactory";
  
  // Transient data that will need to be periodically rebuilt
  private long currentSessionExpiration = -1L;
  private String currentAuthToken = null;
  
  public CswsSession(final String userName,
    final String password,
    final javax.net.ssl.SSLSocketFactory sslSocketFactory,
    final long sessionExpirationInterval,
    final String authenticationServiceURL,
    final String documentManagementServiceURL,
    final String contentServiceServiceURL,
    final String memberServiceServiceURL,
    final String searchServiceServiceURL) throws ManifoldCFException {
      
    // Save username/password
    this.userName = userName;
    this.password = password;
    // Save expiration interval
    this.sessionExpirationInterval = sessionExpirationInterval;
    // Build TLSClientParameters
    final TLSClientParameters tlsConfig = new TLSClientParameters();
    // Build configuration for conduit
    final HttpConduitConfig config = new HttpConduitConfig();
    config.setTlsClientParameters(tlsConfig);
    
    // Construct service references from the URLs
    try {
      this.authService = new Authentication_Service(new URL(authenticationServiceURL));
      this.documentManagementService = new DocumentManagement_Service(new URL(documentManagementServiceURL));
      this.contentServiceService = new ContentService_Service(new URL(contentServiceServiceURL));
      this.memberServiceService = new MemberService_Service(new URL(memberServiceServiceURL));
      this.searchServiceService = new SearchService_Service(new URL(searchServiceServiceURL));
    } catch (javax.xml.ws.WebServiceException e) {
      throw new ManifoldCFException("Error initializing web services: "+e.getMessage(), e);
    } catch (MalformedURLException e) {
      throw new ManifoldCFException("Malformed URL: "+e.getMessage(), e);
    }
    // Initialize authclient etc.
    this.authClientHandle = authService.getBasicHttpBindingAuthentication();
    this.documentManagementHandle = documentManagementService.getBasicHttpBindingDocumentManagement();
    this.contentServiceHandle = contentServiceService.getBasicHttpBindingContentService();
    this.memberServiceHandle = memberServiceService.getBasicHttpBindingMemberService();
    this.searchServiceHandle = searchServiceService.getBasicHttpBindingSearchService();

    // Set up endpoints
    ((BindingProvider)authClientHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, authenticationServiceURL);
    ((BindingProvider)documentManagementHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, documentManagementServiceURL);
    ((BindingProvider)contentServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, contentServiceServiceURL);
    ((BindingProvider)memberServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, memberServiceServiceURL);
    ((BindingProvider)searchServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, searchServiceServiceURL);
    
    // Set SSLSocketFactory's
    if (sslSocketFactory != null) {
      ((BindingProvider)authClientHandle).getRequestContext().put(sslSocketFactoryProperty, sslSocketFactory);
      ((BindingProvider)documentManagementHandle).getRequestContext().put(sslSocketFactoryProperty, sslSocketFactory);
      ((BindingProvider)contentServiceHandle).getRequestContext().put(sslSocketFactoryProperty, sslSocketFactory);
      ((BindingProvider)memberServiceHandle).getRequestContext().put(sslSocketFactoryProperty, sslSocketFactory);
      ((BindingProvider)searchServiceHandle).getRequestContext().put(sslSocketFactoryProperty, sslSocketFactory);
    }
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

  /** 
   * Fetch initialized SearchService handle.
   */
  public SearchService getSearchServiceHandle() {
    // MHL
    return searchServiceHandle;
  }
  
  // Accessors for information that only needs to be accessed once per session, which we will cache
  
  /**
   * Fetch root node types.   These will be cached so we only need to do it once.
   */
  public List<? extends String> getRootNodeTypes()
    throws ManifoldCFException, ServiceInterruption {
    if (rootNodeTypes == null) {
      // Fetch them
      try {
        this.rootNodeTypes = getDocumentManagementHandle().getRootNodeTypes(getOTAuthentication());
      } catch (SOAPFaultException e) {
        processSOAPFault(e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException(e);
      }
    }
    return this.rootNodeTypes;
  }
  
  /**
   * Fetch root node given type.
   */
  public Node getRootNode(final String nodeType) 
  throws ManifoldCFException, ServiceInterruption {
    Node thisWorkspaceNode = workspaceTypeNodes.get(nodeType);
    if (thisWorkspaceNode == null) {
      try {
        thisWorkspaceNode = getDocumentManagementHandle().getRootNode(nodeType, getOTAuthentication());
      } catch (SOAPFaultException e) {
        processSOAPFault(e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException(e);
      }
      workspaceTypeNodes.put(nodeType, thisWorkspaceNode);
    }
    return thisWorkspaceNode;
  }
  
  public List<? extends Node> listNodes(final long nodeId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().listNodes(nodeId, false, getOTAuthentication());
    } catch (SOAPFaultException e) {
      if (e.getFault().getFaultCode().equals("ns0:DocMan.ErrorGettingParentNode")) {
        return null;
      }
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public List<? extends Node> getChildren(final long nodeId)
    throws ManifoldCFException, ServiceInterruption {
    final GetNodesInContainerOptions gnico = new GetNodesInContainerOptions();
    // Depth 0 - default listing and Depth 1 - One level down
    gnico.setMaxDepth(0);
    // We're listing folder by folder so hopefully this is nowhere near what we'll ever get back
    gnico.setMaxResults(1000000);
    try {
      return getDocumentManagementHandle().getNodesInContainer(nodeId, gnico, getOTAuthentication());
    } catch (SOAPFaultException e) {
      if (e.getFault().getFaultCode().equals("ns0:DocMan.ErrorGettingParentNode")) {
        return null;
      }
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
  
  public List<? extends CategoryInheritance> getCategoryInheritance(final long parentId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryInheritance(parentId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public List<? extends AttributeGroupDefinition> getCategoryDefinitions(final List<Long> categoryIDs)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryDefinitions(categoryIDs, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
  
  public Node getNode(final long nodeId) 
    throws ManifoldCFException, ServiceInterruption {
    // Need to detect if object was deleted, and return null in this case!!!
    try {
      return getDocumentManagementHandle().getNode(nodeId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public Node getNodeByPath(final long rootNode, final List<String> colonSeparatedPath)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getNodeByPath(rootNode, colonSeparatedPath, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public NodeRights getNodeRights(final long nodeId) 
    throws ManifoldCFException, ServiceInterruption {
    // Need to detect if object was deleted, and return null in this case!!!
    try {
      return getDocumentManagementHandle().getNodeRights(nodeId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public Version getVersion(final long nodeId, final long version) 
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getVersion(nodeId, version, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public AttributeGroupDefinition getCategoryDefinition(final long catId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryDefinition(catId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }      
  }

  public User getUserByLoginName(final String userName)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getUserByLoginName(userName, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public Member getMemberByLoginName(final String memberName)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getMemberByLoginName(memberName, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public List<? extends Group> listUserMemberOf(final long memberId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().listMemberOf(memberId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
  
  public Member getMember(final long memberId) 
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getMemberById(memberId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
  
  public void getVersionContents(final long nodeId, final long version, final OutputStream os)
    throws ManifoldCFException, ServiceInterruption {
    try {
      final Holder<OTAuthentication> auth = getOTAuthentication();
      final String contextID = getDocumentManagementHandle().getVersionContentsContext(nodeId, version, auth);
      final DataHandler dataHandler = getContentServiceHandle().downloadContent(contextID, auth);
      dataHandler.writeTo(os);
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
    } catch (IOException e) {
      processIOException(e);
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
    }
  }
  
  public PageHandle getAllUsers() 
    throws ManifoldCFException, ServiceInterruption {
    final MemberSearchOptions srchMemOptions  = new MemberSearchOptions();
    srchMemOptions.setFilter(SearchFilter.USER);
    srchMemOptions.setScope(SearchScope.SYSTEM);
    srchMemOptions.setColumn(SearchColumn.NAME);
    srchMemOptions.setMatching(SearchMatching.STARTSWITH);
    srchMemOptions.setSearch("");
    try {
      return getMemberServiceHandle().searchForMembers(srchMemOptions, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }

  public List<? extends Member> getNextUserSearchResults(PageHandle pgHandle)
    throws ManifoldCFException, ServiceInterruption {
    try {
      final MemberSearchResults msr = getMemberServiceHandle().getSearchResults(pgHandle, getOTAuthentication());
      if (msr == null) {
        return null;
      }
      final List<? extends Member> rval = msr.getMembers();
      if (rval == null || rval.size() == 0) {
        return null;
      }
      return rval;
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
  
  // Node searching
  
  /**
  * Return a set of IDs matching the specification.
  * @param parentID is the parent ID.
  * @param returnColumns is an array of return column names.
  * For reference:
  * OTDataID
  * OTSubTypeName
  * OTName
  * @param searchSpec is the search specification, e.g. "\"OTSubType\":0 OR \"OTSubType\":1 OR \"OTSubType\":144) AND \"OTModifyDate\":&lt;20190312"
  *  For reference:
  * OTSubType Details 
  * 0 - Folder 
  * 1 - Alias [Shortcut] 
  * 131 - Category 
  * 136 - Compound Document 
  * 140 - URL 
  * 144 - Document 
  * 202 - Project 
  * 204 - Task List
  * 207 - Channel 
  * 215 - Discussion 
  * 299 - LiveReport
  * @param orderingColumn is the column name to order the result by
  * @param start is the ID of the result to return (0-based)
  * @param count is the maximum number of IDs to return
  * @return an array of IDs corresponding to documents or categories requested
  */
  public List<? extends SGraph> searchFor(final long parentID,
    final String[] returnColumns, final String searchSpec, final String orderingColumn, final int start, final int count)
    throws ManifoldCFException, ServiceInterruption {
    try {
      final SingleSearchRequest singleSrchReq = new SingleSearchRequest();
      singleSrchReq.setDataCollectionSpec("'LES Enterprise'");//Livelink Enterprise Server
      singleSrchReq.setQueryLanguage("Livelink Search API V1"); //Search Query Language API
      singleSrchReq.setFirstResultToRetrieve(start + 1);
      singleSrchReq.setNumResultsToRetrieve(count);
      if (orderingColumn != null) {
        singleSrchReq.setResultOrderSpec("sortByRegion="+orderingColumn+"&sortDirection=ascending");
      }
      singleSrchReq.setResultSetSpec("where1=(\"OTParentID\":"+parentID+" AND ("+searchSpec+")");
      for (final String returnColumn : returnColumns) {
        singleSrchReq.getResultTransformationSpec().add(returnColumn);
      }
      // Fire off the query
      final SingleSearchResponse results = getSearchServiceHandle().search(singleSrchReq, "", getOTAuthentication());
      if (results == null) {
        return null;
      }
      // Get the result page from the results
      final SResultPage srp = results.getResults();
      if (srp == null) {
        return null;
      }
      // Get the list of actual result rows (?)
      return srp.getItem();
    } catch (SOAPFaultException e) {
      processSOAPFault(e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException(e);
      return null;
    }
  }
                                                
  // Construct authentication token argument, which must be passed as last argument for every method
  
  /**
   * Construct OTAuthentication structure (to be passed as an argument)
   */
  public Holder<OTAuthentication> getOTAuthentication() 
    throws ManifoldCFException, ServiceInterruption {
    final String authToken = getAuthToken();
    // Create the OTAuthentication object and set the authentication token
    final Holder<OTAuthentication> holder = new Holder<>();
    final OTAuthentication otAuth = new OTAuthentication();
    otAuth.setAuthenticationToken(authToken);
    holder.value = otAuth;
    return holder;
  }

  // Private methods
  
  private String getAuthToken()
    throws ManifoldCFException, ServiceInterruption {
    final long currentTime = System.currentTimeMillis();
    if (currentSessionExpiration == -1L || currentTime > currentSessionExpiration) {
      // Kill current auth token etc
      currentSessionExpiration = -1L;
      currentAuthToken = null;
      // Refetch the auth token (this may fail)
      try {
        currentAuthToken = authClientHandle.authenticateUser(userName, password);
      } catch (SOAPFaultException e) {
        processSOAPFault(e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException(e);
      }
      currentSessionExpiration = currentTime + sessionExpirationInterval;
    }
    return currentAuthToken;
  }
  
  private void processIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption {
    throw new ManifoldCFException("IO exception: "+e.getMessage(), e);
    // MHL
  }
  
  private void processSOAPFault(SOAPFaultException e)
    throws ManifoldCFException, ServiceInterruption {
    throw new ManifoldCFException("SOAP exception: "+e.getMessage(), e);
    // MHL
  }
  
  private void processWSException(javax.xml.ws.WebServiceException e)
    throws ManifoldCFException, ServiceInterruption {
    throw new ManifoldCFException("Web service communication issue: "+e.getMessage(), e);
  }
}
