/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.crawler.connectors.alfrescowebscript;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.alfresco.consulting.indexer.client.AlfrescoClient;
import org.alfresco.consulting.indexer.client.AlfrescoDownException;
import org.alfresco.consulting.indexer.client.AlfrescoResponse;
import org.alfresco.consulting.indexer.client.WebScriptsAlfrescoClient;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;
import java.text.MessageFormat;

public class AlfrescoConnector extends BaseRepositoryConnector {
  private static final String ACTIVITY_FETCH = "fetch document";
  private static final String[] activitiesList = new String[]{ACTIVITY_FETCH};
  private AlfrescoClient alfrescoClient;
  private Boolean enableDocumentProcessing = Boolean.TRUE;
  
  private static final String CONTENT_URL_PROPERTY = "contentUrlPath";
  private static final String AUTHORITIES_PROPERTY = "readableAuthorities";
  
  // Static Fields
  private static final String FIELD_UUID = "uuid";
  private static final String FIELD_NODEREF = "nodeRef";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_NAME = "name";

  @Override
  public int getConnectorModel() {
    return MODEL_ADD_CHANGE_DELETE; // We return only incremental documents.
  }

  public void setClient(AlfrescoClient client) {
    alfrescoClient = client;
  }

  @Override
  public void connect(ConfigParams config) {
    super.connect(config);

    String protocol = getConfig(config, "protocol", "http");
    String hostname = getConfig(config, "hostname", "localhost");
    String endpoint = getConfig(config, "endpoint", "/alfresco/service");
    String storeProtocol = getConfig(config, "storeprotocol", "workspace");
    String storeId = getConfig(config, "storeid", "SpacesStore");
    String username = getConfig(config, "username", null);
    String password = getConfig(config, "password", null);
    this.enableDocumentProcessing = new Boolean(getConfig(config, "enabledocumentprocessing", "false"));

    alfrescoClient = new WebScriptsAlfrescoClient(protocol, hostname, endpoint,
            storeProtocol, storeId, username, password);
  }

  private static String getConfig(ConfigParams config,
                                  String parameter,
                                  String defaultValue) {
    final String protocol = config.getParameter(parameter);
    if (protocol == null) {
      return defaultValue;
    }
    return protocol;
  }

  @Override
  public String check() throws ManifoldCFException {
    return super.check();
  }

  @Override
  public void disconnect() throws ManifoldCFException {
    super.disconnect();
  }

  @Override
  public String[] getActivitiesList() {
    return activitiesList;
  }

  @Override
  public int getMaxDocumentRequest() {
    return 20;
  }

  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
                                              String lastSeedVersion, long seedTime, int jobMode) throws ManifoldCFException, ServiceInterruption {
    try {
      long lastTransactionId = 0;
      long lastAclChangesetId = 0;
      
      if(lastSeedVersion != null && !lastSeedVersion.isEmpty()){
        StringTokenizer tokenizer = new StringTokenizer(lastSeedVersion,"|");

        if (tokenizer.countTokens() == 2) {
          lastTransactionId = new Long(tokenizer.nextToken());
          lastAclChangesetId = new Long(tokenizer.nextToken());
        }
      }
      
      Logging.connectors.debug(MessageFormat.format("Starting from transaction id: {0} and acl changeset id: {1}", new Object[]{lastTransactionId, lastAclChangesetId}));
      
      long transactionIdsProcessed;
      long aclChangesetsProcessed;
      do {
        final AlfrescoResponse response = alfrescoClient.
        		fetchNodes(lastTransactionId, 
        				lastAclChangesetId,
        				ConfigurationHandler.getFilters(spec));
        int count = 0;
        for (Map<String, Object> doc : response.getDocuments()) {
//          String json = gson.toJson(doc);
//          activities.addSeedDocument(json);
          String uuid = doc.get("uuid").toString();
          activities.addSeedDocument(uuid);
          count++;
        }
        Logging.connectors.debug(MessageFormat.format("Fetched and added {0} seed documents", new Object[]{new Integer(count)}));

        transactionIdsProcessed = response.getLastTransactionId() - lastTransactionId;
        aclChangesetsProcessed = response.getLastAclChangesetId() - lastAclChangesetId;

        lastTransactionId = response.getLastTransactionId();
        lastAclChangesetId = response.getLastAclChangesetId();

        Logging.connectors.debug(MessageFormat.format("transaction_id={0}, acl_changeset_id={1}", new Object[]{lastTransactionId, lastAclChangesetId}));
      } while (transactionIdsProcessed > 0 || aclChangesetsProcessed > 0);

      Logging.connectors.debug(MessageFormat.format("Recording {0} as last transaction id and {1} as last changeset id", new Object[]{lastTransactionId, lastAclChangesetId}));
      return lastTransactionId + "|" + lastAclChangesetId;
    } catch (AlfrescoDownException e) {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
                             IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
        throws ManifoldCFException, ServiceInterruption {

    for (String doc : documentIdentifiers) {
      String nextVersion = statuses.getIndexedVersionString(doc);	
    	
      // Calling again Alfresco API because Document's actions are lost from seeding method
      AlfrescoResponse response = alfrescoClient.fetchNode(doc);
      if(response.getDocumentList().isEmpty()){ // Not found seeded document. Could reflect an error in Alfresco
        Logging.connectors.warn(MessageFormat.format("Invalid Seeded Document from Alfresco with ID {0}", new Object[]{doc}));
        activities.noDocument(doc, nextVersion);
        continue;
      }
      Map<String, Object> map = response.getDocumentList().get(0); // Should be only one
      RepositoryDocument rd = new RepositoryDocument();
      String uuid = map.containsKey(FIELD_UUID) ? map.get(FIELD_UUID).toString() : doc;
      String nodeRef = map.containsKey(FIELD_NODEREF) ? map.get(FIELD_NODEREF).toString() : "";
      rd.addField(FIELD_NODEREF, nodeRef);
      String type = map.containsKey(FIELD_TYPE) ? map.get(FIELD_TYPE).toString() : "";
      rd.addField(FIELD_TYPE, type);
      String name =  map.containsKey(FIELD_NAME) ? map.get(FIELD_NAME).toString() : "";
      rd.setFileName(name);

      if ((Boolean) map.get("deleted")) {
        activities.deleteDocument(uuid);
      } else {
        if (this.enableDocumentProcessing) {
          try{
            processMetaData(rd,uuid);
          }catch(AlfrescoDownException e){
            Logging.connectors.warn(MessageFormat.format("Invalid Document from Alfresco with ID {0}", new Object[]{uuid}), e);
            activities.noDocument(doc, nextVersion);
            continue; // No Metadata, No Content....skip document
          }
        }
        try {
          if(rd.getBinaryStream() == null){
            byte[] empty = new byte[0];
            rd.setBinary(new ByteArrayInputStream(empty), 0L);
          }
          Logging.connectors.debug(MessageFormat.format("Ingesting with id: {0}, URI {1} and rd {2}", new Object[]{uuid, nodeRef, rd.getFileName()}));
          activities.ingestDocumentWithException(uuid, "", uuid, rd);
        } catch (IOException e) {
          throw new ManifoldCFException(
					"Error Ingesting Document with ID " + String.valueOf(uuid), e);
        }
      }
    }
  }
  
  private void processMetaData(RepositoryDocument rd,
		  String uuid) throws ManifoldCFException, AlfrescoDownException {
    Map<String,Object> properties = alfrescoClient.fetchMetadata(uuid);
    for(String property : properties.keySet()) {
      Object propertyValue = properties.get(property);
      rd.addField(property,propertyValue.toString());
    }
    
    // Document Binary Content
    String contentUrlPath = (String) properties.get(CONTENT_URL_PROPERTY);
    if(contentUrlPath != null && !contentUrlPath.isEmpty()){
      InputStream binaryContent = alfrescoClient.fetchContent(contentUrlPath);
      if(binaryContent != null) // Content-based Alfresco Document
        rd.setBinary(binaryContent, 0L);
    }
    
    // Indexing Permissions
    @SuppressWarnings("unchecked")
    List<String> permissions = (List<String>) properties.remove(AUTHORITIES_PROPERTY);
    if(permissions != null){
      rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,
    		permissions.toArray(new String[permissions.size()]));
    }
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
                                        IHTTPOutput out, Locale locale, ConfigParams parameters,
                                        List<String> tabsArray) throws ManifoldCFException, IOException {
    ConfigurationHandler.outputConfigurationHeader(threadContext, out, locale,
            parameters, tabsArray);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
                                      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
          throws ManifoldCFException, IOException {
    ConfigurationHandler.outputConfigurationBody(threadContext, out, locale,
            parameters, tabName);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
                                         IPostParameters variableContext, Locale locale, ConfigParams parameters)
          throws ManifoldCFException {
    return ConfigurationHandler.processConfigurationPost(threadContext,
            variableContext, locale, parameters);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
                                Locale locale, ConfigParams parameters) throws ManifoldCFException,
          IOException {
    ConfigurationHandler.viewConfiguration(threadContext, out, locale,
            parameters);
  }
  
  
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException{
    ConfigurationHandler.outputSpecificationHeader(out, locale, os, connectionSequenceNumber, tabsArray);
  }
   
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName) throws ManifoldCFException, IOException{
    ConfigurationHandler.outputSpecificationBody(out, locale, os, connectionSequenceNumber, actualSequenceNumber, tabName);
  }
   
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
			  int connectionSequenceNumber) throws ManifoldCFException{
    return ConfigurationHandler.processSpecificationPost(variableContext, locale, os, connectionSequenceNumber);
  }
   
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
			  int connectionSequenceNumber) throws ManifoldCFException, IOException{
  ConfigurationHandler.viewSpecification(out, locale, os, connectionSequenceNumber);
  }
  
}
