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

import java.util.*;

import java.net.URL;
import java.net.MalformedURLException;

import java.io.OutputStream;
import java.io.IOException;

import com.opentext.livelink.service.memberservice.*;
import org.apache.cxf.transport.http.HttpConduitFeature;
import org.apache.cxf.transport.http.HttpConduitConfig;
import org.apache.cxf.configuration.jsse.TLSClientParameters;

import javax.activation.DataHandler;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import com.opentext.ecm.api.OTAuthentication;
import com.opentext.livelink.service.core.PageHandle;
import com.opentext.livelink.service.core.Authentication;
import com.opentext.livelink.service.core.Authentication_Service;
import com.opentext.livelink.service.core.ContentService;
import com.opentext.livelink.service.core.ContentService_Service;
import com.opentext.livelink.service.docman.DocumentManagement;
import com.opentext.livelink.service.docman.DocumentManagement_Service;
import com.opentext.livelink.service.searchservices.SearchService;
import com.opentext.livelink.service.searchservices.SearchService_Service;

import com.opentext.livelink.service.docman.CategoryInheritance;
import com.opentext.livelink.service.docman.GetNodesInContainerOptions;
import com.opentext.livelink.service.docman.Node;
import com.opentext.livelink.service.docman.Version;
import com.opentext.livelink.service.docman.NodeRights;
import com.opentext.livelink.service.docman.AttributeGroupDefinition;
import com.opentext.livelink.service.searchservices.SResultPage;
import com.opentext.livelink.service.searchservices.SGraph;
import com.opentext.livelink.service.searchservices.SingleSearchRequest;
import com.opentext.livelink.service.searchservices.SingleSearchResponse;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

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
    final IKeystoreManager keystore,
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
    if (keystore != null) {
      tlsConfig.setTrustManagers(keystore.getTrustManagers());
    }
    // Build configuration for conduit
    final HttpConduitConfig config = new HttpConduitConfig();
    config.setTlsClientParameters(tlsConfig);
    final HttpConduitFeature conduitFeature = new HttpConduitFeature();
    conduitFeature.setConduitConfig(config);

    // Construct service references from the URLs
    // EVERYTHING depends on the right classloader being used to help us locate appropriate resources etc, so swap to the classloader for THIS
    // class.
    final ClassLoader savedCl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      this.authService = (authenticationServiceURL != null)?new Authentication_Service(new URL(authenticationServiceURL + "?wsdl"), conduitFeature):null;
      this.documentManagementService = (documentManagementServiceURL != null)?new DocumentManagement_Service(new URL(documentManagementServiceURL + "?wsdl"), conduitFeature):null;
      this.contentServiceService = (contentServiceServiceURL != null)?new ContentService_Service(new URL(contentServiceServiceURL + "?wsdl"), conduitFeature):null;
      this.memberServiceService = (memberServiceServiceURL != null)?new MemberService_Service(new URL(memberServiceServiceURL + "?wsdl"), conduitFeature):null;
      this.searchServiceService = (searchServiceServiceURL != null)?new SearchService_Service(new URL(searchServiceServiceURL + "?wsdl"), conduitFeature):null;
    } catch (javax.xml.ws.WebServiceException e) {
      throw new ManifoldCFException("Error initializing web services: "+e.getMessage(), e);
    } catch (MalformedURLException e) {
      throw new ManifoldCFException("Malformed URL: "+e.getMessage(), e);
    } finally {
      Thread.currentThread().setContextClassLoader(savedCl);
    }
    // Initialize authclient etc.
    if (authService != null) {
      this.authClientHandle = authService.getBasicHttpBindingAuthentication();
      ((BindingProvider)authClientHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, authenticationServiceURL);
    } else {
      this.authClientHandle = null;
    }
    if (documentManagementService != null) {
      this.documentManagementHandle = documentManagementService.getBasicHttpBindingDocumentManagement();
      ((BindingProvider)documentManagementHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, documentManagementServiceURL);
    } else {
      this.documentManagementHandle = null;
    }
    if (contentServiceService != null) {
      this.contentServiceHandle = contentServiceService.getBasicHttpBindingContentService();
      ((BindingProvider)contentServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, contentServiceServiceURL);
    } else {
      this.contentServiceHandle = null;
    }
    if (memberServiceService != null) {
      this.memberServiceHandle = memberServiceService.getBasicHttpBindingMemberService();
      ((BindingProvider)memberServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, memberServiceServiceURL);
    } else {
      this.memberServiceHandle = null;
    }
    if (searchServiceService != null) {
      this.searchServiceHandle = searchServiceService.getBasicHttpBindingSearchService();
      ((BindingProvider)searchServiceHandle).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, searchServiceServiceURL);
    } else {
      this.searchServiceHandle = null;
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
        processSOAPFault("Failed to get root node types", e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException("Failed to get root node types", e);
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
        processSOAPFault("Failed to get root node of type " + nodeType, e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException("Failed to get root node of type " + nodeType, e);
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
      processSOAPFault("Failed to list nodes under id " + nodeId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to list nodes under id " + nodeId, e);
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
        Logging.connectors.warn("Ignoring children of node " + nodeId + " due to DocMan.ErrorGettingParentNode", e);
        return null;
      }
      processSOAPFault("Failed to get children of node " + nodeId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get children of node " + nodeId, e);
      return null;
    }
  }

  public List<? extends CategoryInheritance> getCategoryInheritance(final long parentId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryInheritance(parentId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get category inheritance for id " + parentId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get category inheritance for id " + parentId, e);
      return null;
    }
  }

  public List<? extends AttributeGroupDefinition> getCategoryDefinitions(final List<Long> categoryIDs)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryDefinitions(categoryIDs, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get definitions inheritance for ids " + Arrays.toString(categoryIDs.toArray()), e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get definitions inheritance for ids " + Arrays.toString(categoryIDs.toArray()), e);
      return null;
    }
  }

  public Node getNode(final long nodeId)
    throws ManifoldCFException, ServiceInterruption {
    // Need to detect if object was deleted, and return null in this case!!!
    try {
      return getDocumentManagementHandle().getNode(nodeId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get node " + nodeId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get node " + nodeId, e);
      return null;
    }
  }

  public Node getNodeByPath(final long rootNode, final List<String> colonSeparatedPath)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getNodeByPath(rootNode, colonSeparatedPath, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get node by path " + rootNode + " with path " + Arrays.toString(colonSeparatedPath.toArray()), e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get node by path " + rootNode + " with path " + Arrays.toString(colonSeparatedPath.toArray()), e);
      return null;
    }
  }

  public NodeRights getNodeRights(final long nodeId)
    throws ManifoldCFException, ServiceInterruption {
    // Need to detect if object was deleted, and return null in this case!!!
    try {
      return getDocumentManagementHandle().getNodeRights(nodeId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      if (e.getFault().getFaultCode().equals("ns0:DocMan.ErrorGettingNodeRights")) {
        Logging.connectors.warn("Ignoring node " + nodeId + " due to DocMan.ErrorGettingNodeRights", e);
        return null;
      }
      processSOAPFault("Failed to get node rights for " + nodeId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get node rights for " + nodeId, e);
      return null;
    }
  }

  public Version getVersion(final long nodeId, final long version)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getVersion(nodeId, version, getOTAuthentication());
    } catch (SOAPFaultException e) {
      if (e.getFault().getFaultCode().equals("ns0:DocMan.VersionRetrievalError")) {
        Logging.connectors.warn("Ignoring node " + nodeId + " due to DocMan.VersionRetrievalError", e);
        return null;
      }
      processSOAPFault("Failed to get version " + version + " of node " + nodeId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get version " + version + " of node " + nodeId, e);
      return null;
    }
  }

  public AttributeGroupDefinition getCategoryDefinition(final long catId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getDocumentManagementHandle().getCategoryDefinition(catId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get category definition " + catId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get category definition " + catId, e);
      return null;
    }
  }

  public User getUserByLoginName(final String userName)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getUserByLoginName(userName, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get user by userName " + userName, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get user by userName " + userName, e);
      return null;
    }
  }

  public Member getMemberByLoginName(final String memberName)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getMemberByLoginName(memberName, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get member by memberName " + memberName, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get member by memberName " + memberName, e);
      return null;
    }
  }

  public List<? extends MemberRight> listUserMemberOf(final long memberId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().listRightsByID(memberId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to list user member of " + memberId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to list user member of " + memberId, e);
      return null;
    }
  }

  public Member getMember(final long memberId)
    throws ManifoldCFException, ServiceInterruption {
    try {
      return getMemberServiceHandle().getMemberById(memberId, getOTAuthentication());
    } catch (SOAPFaultException e) {
      processSOAPFault("Failed to get member " + memberId, e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get member " + memberId, e);
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
      processSOAPFault("Failed to get version contents of node " + nodeId + " in version " + version, e);
    } catch (IOException e) {
      processIOException("Failed to get version contents of node " + nodeId + " in version " + version, e);
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get version contents of node " + nodeId + " in version " + version, e);
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
      processSOAPFault("Failed to search for members", e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to search for members", e);
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
      processSOAPFault("Failed to get search results", e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to get search results", e);
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
  * @param dataCollection the data collection (i.e. the slice) to be queried
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
    final String[] returnColumns, final String dataCollection, final String searchSpec, final String orderingColumn, final int start, final int count)
    throws ManifoldCFException, ServiceInterruption {

    final SingleSearchRequest singleSrchReq = new SingleSearchRequest();
    singleSrchReq.setDataCollectionSpec(dataCollection);//Livelink Enterprise Server
    singleSrchReq.setQueryLanguage("Livelink Search API V1"); //Search Query Language API
    singleSrchReq.setFirstResultToRetrieve(start + 1);
    singleSrchReq.setNumResultsToRetrieve(count);
    if (orderingColumn != null) {
      singleSrchReq.setResultOrderSpec("sortByRegion="+orderingColumn+"&sortDirection=ascending");
    }
    singleSrchReq.setResultSetSpec("where1=(\"OTParentID\":"+parentID+" AND ("+searchSpec+"))&lookfor1=complexquery");
    for (final String returnColumn : returnColumns) {
      singleSrchReq.getResultTransformationSpec().add(returnColumn);
    }

    try {

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
      processSOAPFault("Failed to search for " + singleSrchReq.getResultSetSpec(), e);
      return null;
    } catch (javax.xml.ws.WebServiceException e) {
      processWSException("Failed to search for " + singleSrchReq.getResultSetSpec(), e);
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
        processSOAPFault("Failed to get auth token", e);
      } catch (javax.xml.ws.WebServiceException e) {
        processWSException("Failed to get auth token", e);
      }
      currentSessionExpiration = currentTime + sessionExpirationInterval;
    }
    return currentAuthToken;
  }

  private void processIOException(String message, IOException e)
    throws ManifoldCFException {
    throw new ManifoldCFException("IO exception: " + message, e);
    // MHL
  }

  private void processSOAPFault(String message, SOAPFaultException e)
    throws ManifoldCFException {
    throw new ManifoldCFException("SOAP exception: " + message + " (Fault code: " + e.getFault().getFaultCode() +")", e);
    // MHL
  }

  private void processWSException(String message, javax.xml.ws.WebServiceException e)
    throws ManifoldCFException {
    throw new ManifoldCFException("Web service communication issue: " + message, e);
  }
}
