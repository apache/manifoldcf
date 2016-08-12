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

import com.github.maoo.indexer.client.AlfrescoClient;
import com.github.maoo.indexer.client.AlfrescoDownException;
import com.github.maoo.indexer.client.AlfrescoResponse;
import com.github.maoo.indexer.client.WebScriptsAlfrescoClient;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.DateParser;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


public class AlfrescoConnector extends BaseRepositoryConnector {

  private static final String ACTIVITY_FETCH = "fetch document";
  private static final String[] activitiesList = new String[]{ACTIVITY_FETCH};

  private AlfrescoClient alfrescoClient;
  private String binName;
  
  private static final String CONTENT_URL_PROPERTY = "contentUrlPath";
  private static final String AUTHORITIES_PROPERTY = "readableAuthorities";
  private static final String MIMETYPE_PROPERTY = "mimetype";
  private static final String SIZE_PROPERTY = "size";
  private static final String MODIFIED_DATE_PROPERTY = "cm:modified";
  private static final String CREATED_DATE_PROPERTY = "cm:created";

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
  public String[] getBinNames(String documentIdentifier) {
    return new String[] { binName };
  }

  @Override
  public void connect(ConfigParams config) {
    super.connect(config);

    String protocol = getConfig(config, "protocol", "http");
    String hostname = getConfig(config, "hostname", "localhost");
    String port = getConfig(config, "port", "8080");
    String endpoint = getConfig(config, "endpoint", "/alfresco/service");
    String storeProtocol = getConfig(config, "storeprotocol", "workspace");
    String storeId = getConfig(config, "storeid", "SpacesStore");
    String username = getConfig(config, "username", null);
    String password = getObfuscatedConfig(config, "password", null);

    /*
    System.out.println("============");
    System.out.println(protocol);
    System.out.println(hostname);
    System.out.println(port);
    System.out.println(endpoint);
    System.out.println(storeProtocol);
    System.out.println(storeId);
    System.out.println(username);
    System.out.println(password);
    System.out.println("============");
    */
    
    alfrescoClient = new WebScriptsAlfrescoClient(protocol, hostname + ":" + port, endpoint,
        storeProtocol, storeId, username, password);
    binName = hostname;
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

  private static String getObfuscatedConfig(ConfigParams config,
                                  String parameter,
                                  String defaultValue) {
    final String protocol = config.getObfuscatedParameter(parameter);
    if (protocol == null) {
      return defaultValue;
    }
    return protocol;
  }

  @Override
  public String check() throws ManifoldCFException {
    try {
      // We really want to do something more like fetching a document here...
      alfrescoClient.fetchUserAuthorities("admin");
      return super.check();
    } catch (AlfrescoDownException e) {
      if (Logging.connectors != null) {
        Logging.connectors.warn(e.getMessage(), e);
      }
      return "Alfresco connection check failed: " + e.getMessage();
    } catch (Exception e) {
      if (Logging.connectors != null) {
        Logging.connectors.error(e.getMessage(), e);
      }
      throw new ManifoldCFException("Alfresco connection check failed",e);
    }
  }

  @Override
  public void disconnect() throws ManifoldCFException {
    alfrescoClient = null;
    binName = null;
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

      if(lastSeedVersion != null && !lastSeedVersion.isEmpty()) {
        StringTokenizer tokenizer = new StringTokenizer(lastSeedVersion,"|");

        if (tokenizer.countTokens() == 2) {
          lastTransactionId = new Long(tokenizer.nextToken());
          lastAclChangesetId = new Long(tokenizer.nextToken());
        }
      }

      if (Logging.connectors != null && Logging.connectors.isDebugEnabled())
        Logging.connectors.debug(new MessageFormat("Starting from transaction id: {0} and acl changeset id: {1}", Locale.ROOT)
            .format(new Object[]{lastTransactionId, lastAclChangesetId}));

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
        if (Logging.connectors != null && Logging.connectors.isDebugEnabled())
          Logging.connectors.debug(new MessageFormat("Fetched and added {0} seed documents", Locale.ROOT)
              .format(new Object[]{new Integer(count)}));

        transactionIdsProcessed = response.getLastTransactionId() - lastTransactionId;
        aclChangesetsProcessed = response.getLastAclChangesetId() - lastAclChangesetId;

        lastTransactionId = response.getLastTransactionId();
        lastAclChangesetId = response.getLastAclChangesetId();

        if (Logging.connectors != null && Logging.connectors.isDebugEnabled())
          Logging.connectors.debug(new MessageFormat("transaction_id={0}, acl_changeset_id={1}", Locale.ROOT)
              .format(new Object[]{lastTransactionId, lastAclChangesetId}));
      } while (transactionIdsProcessed > 0 || aclChangesetsProcessed > 0);

      if (Logging.connectors != null && Logging.connectors.isDebugEnabled())
        Logging.connectors.debug(new MessageFormat("Recording {0} as last transaction id and {1} as last changeset id", Locale.ROOT)
            .format(new Object[]{lastTransactionId, lastAclChangesetId}));
      return lastTransactionId + "|" + lastAclChangesetId;
    } catch (AlfrescoDownException e) {
      handleAlfrescoDownException(e,"seeding");
      return null;
    }
  }

  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
                               IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption {

    boolean enableDocumentProcessing = ConfigurationHandler.getEnableDocumentProcessing(spec);
    for (String doc : documentIdentifiers) {

      String errorCode = null;
      String errorDesc = null;
      Long fileLengthLong = null;
      long startTime = System.currentTimeMillis();

      try {

        String nextVersion = statuses.getIndexedVersionString(doc);

        // Calling again Alfresco API because Document's actions are lost from seeding method
        AlfrescoResponse response = alfrescoClient.fetchNode(doc);
        if(response.getDocumentList().isEmpty()){ // Not found seeded document. Could reflect an error in Alfresco
          if (Logging.connectors != null)
            Logging.connectors.warn(new MessageFormat("Invalid Seeded Document from Alfresco with ID {0}", Locale.ROOT)
                .format(new Object[]{doc}));
          activities.deleteDocument(doc);
          continue;
        }
        Map<String, Object> map = response.getDocumentList().get(0); // Should be only one
        if ((Boolean) map.get("deleted")) {
          activities.deleteDocument(doc);
          continue;
        }

        // From the map, get the things we know about
        String uuid = doc;
        String nodeRef = map.containsKey(FIELD_NODEREF) ? map.get(FIELD_NODEREF).toString() : "";
        String type = map.containsKey(FIELD_TYPE) ? map.get(FIELD_TYPE).toString() : "";
        String name =  map.containsKey(FIELD_NAME) ? map.get(FIELD_NAME).toString() : "";

        // Fetch document metadata
        Map<String,Object> properties = alfrescoClient.fetchMetadata(uuid);

        // Process various special fields
        Object mdObject;

        // Size
        Long lSize = null;
        mdObject = properties.get(SIZE_PROPERTY);
        if (mdObject != null) {
          String size = mdObject.toString();
          lSize = new Long(size);
        }

        // Modified Date
        Date modifiedDate = null;
        mdObject = properties.get(MODIFIED_DATE_PROPERTY);
        if (mdObject != null) {
          modifiedDate = DateParser.parseISO8601Date(mdObject.toString());
        }

        // Created Date
        Date createdDate = null;
        mdObject = properties.get(CREATED_DATE_PROPERTY);
        if (mdObject != null) {
          createdDate = DateParser.parseISO8601Date(mdObject.toString());
        }


        // Establish the document version.
        if (modifiedDate == null) {
          activities.deleteDocument(doc);
          continue;
        }

        StringBuilder sb = new StringBuilder();
          
        sb.append((enableDocumentProcessing?"+":"-"));
        sb.append(new Long(modifiedDate.getTime()).toString());
          
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) properties.remove(AUTHORITIES_PROPERTY);
        if(permissions != null){
            for (String permission : permissions) {
                sb.append(permission);
            }
        }
          
        String documentVersion = sb.toString();

        if(!activities.checkDocumentNeedsReindexing(doc, documentVersion))
          continue;

        String mimeType = null;
        Object mimetypeObject = properties.get(MIMETYPE_PROPERTY);
        if (mimetypeObject != null) {
          mimeType = mimetypeObject.toString();
        }

        if (lSize != null && !activities.checkLengthIndexable(lSize.longValue())) {
          activities.noDocument(doc, documentVersion);
          errorCode = activities.EXCLUDED_LENGTH;
          errorDesc = "Excluding document because of length ("+lSize+")";
          continue;
        }

        if (!activities.checkMimeTypeIndexable(mimeType)) {
          activities.noDocument(doc, documentVersion);
          errorCode = activities.EXCLUDED_MIMETYPE;
          errorDesc = "Excluding document because of mime type ("+mimeType+")";
          continue;
        }

        if (!activities.checkDateIndexable(modifiedDate)) {
          activities.noDocument(doc, documentVersion);
          errorCode = activities.EXCLUDED_DATE;
          errorDesc = "Excluding document because of date ("+modifiedDate+")";
          continue;
        }

        String contentUrlPath = (String) properties.get(CONTENT_URL_PROPERTY);
        if (contentUrlPath == null || contentUrlPath.isEmpty()) {
          activities.noDocument(doc, documentVersion);
          errorCode = "NOURL";
          errorDesc = "Excluding document because no URL found";
          continue;
        }

        if (!activities.checkURLIndexable(contentUrlPath)) {
          activities.noDocument(doc, documentVersion);
          errorCode = activities.EXCLUDED_URL;
          errorDesc = "Excluding document because of URL ('"+contentUrlPath+"')";
          continue;
        }

        RepositoryDocument rd = new RepositoryDocument();
        rd.addField(FIELD_NODEREF, nodeRef);
        rd.addField(FIELD_TYPE, type);
        rd.setFileName(name);

        if (modifiedDate != null)
          rd.setModifiedDate(modifiedDate);

        if (createdDate != null)
          rd.setCreatedDate(createdDate);

        for(String property : properties.keySet()) {
          Object propertyValue = properties.get(property);
          rd.addField(property,propertyValue.toString());
        }

        if (mimeType != null && !mimeType.isEmpty())
          rd.setMimeType(mimeType);

        // Indexing Permissions
        if(permissions != null){
          rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,
                            permissions.toArray(new String[permissions.size()]));
        }

        // Document Binary Content
        InputStream stream;
        long length;
        byte[] empty = new byte[0];

        if (enableDocumentProcessing) {
          if (lSize != null) {
            stream = alfrescoClient.fetchContent(contentUrlPath);
            if (stream == null) {
              activities.noDocument(doc, documentVersion);
              errorCode = "NOSTREAM";
              errorDesc = "Excluding document because no content stream found";
              continue;
            }
            length = lSize.longValue();
          } else {
            stream = new ByteArrayInputStream(empty);
            length = 0L;
          }
        } else {
          stream = new ByteArrayInputStream(empty);
          length = 0L;
        }

        try {
          rd.setBinary(stream, length);
          if (Logging.connectors != null && Logging.connectors.isDebugEnabled())
            Logging.connectors.debug(new MessageFormat("Ingesting with id: {0}, URI {1} and rd {2}", Locale.ROOT)
                .format(new Object[]{uuid, nodeRef, rd.getFileName()}));
          activities.ingestDocumentWithException(doc, documentVersion, contentUrlPath, rd);
          errorCode = "OK";
          fileLengthLong = new Long(length);
        } catch (IOException e) {
          handleIOException(e,"reading stream");
        } finally {
          try {
            stream.close();
          } catch (IOException e) {
            handleIOException(e,"closing stream");
          }
        }

      } catch (AlfrescoDownException e) {
        handleAlfrescoDownException(e,"processing");
      } catch (ManifoldCFException e) {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          errorCode = null;
        throw e;
      } finally {
        if (errorCode != null)
          activities.recordActivity(new Long(startTime), ACTIVITY_FETCH,
            fileLengthLong, doc, errorCode, errorDesc, null);
      }
    }
  }

  protected final static long interruptionRetryTime = 5L*60L*1000L;

  protected static void handleAlfrescoDownException(AlfrescoDownException e, String context)
    throws ManifoldCFException, ServiceInterruption {
    long currentTime = System.currentTimeMillis();

    // Server doesn't appear to by up.  Try for a brief time then give up.
    String message = "Server appears down during "+context+": "+e.getMessage();
    Logging.connectors.warn(message,e);
    throw new ServiceInterruption(message,
      e,
      currentTime + interruptionRetryTime,
      -1L,
      3,
      true);
  }

  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if ((e instanceof InterruptedIOException) && (!(e instanceof java.net.SocketTimeoutException)))
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);

    long currentTime = System.currentTimeMillis();

    if (e instanceof java.net.ConnectException)
    {
      // Server isn't up at all.  Try for a brief time then give up.
      String message = "Server could not be contacted during "+context+": "+e.getMessage();
      Logging.connectors.warn(message,e);
      throw new ServiceInterruption(message,
        e,
        currentTime + interruptionRetryTime,
        -1L,
        3,
        true);
    }

    if (e instanceof java.net.SocketTimeoutException)
    {
      String message2 = "Socket timeout exception during "+context+": "+e.getMessage();
      Logging.connectors.warn(message2,e);
      throw new ServiceInterruption(message2,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 20L * 60000L,
        -1,
        false);
    }

    if (e.getClass().getName().equals("java.net.SocketException"))
    {
      // In the past we would have treated this as a straight document rejection, and
      // treated it in the same manner as a 400.  The reasoning is that the server can
      // perfectly legally send out a 400 and drop the connection immediately thereafter,
      // this a race condition.
      // However, Solr 4.0 (or the Jetty version that the example runs on) seems
      // to have a bug where it drops the connection when two simultaneous documents come in
      // at the same time.  This is the final version of Solr 4.0 so we need to deal with
      // this.
      if (e.getMessage().toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("connection reset") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("target server failed to respond") != -1)
      {
        // Treat it as a service interruption, but with a limited number of retries.
        // In that way we won't burden the user with a huge retry interval; it should
        // give up fairly quickly, and yet NOT give up if the error was merely transient
        String message = "Server dropped connection during "+context+": "+e.getMessage();
        Logging.connectors.warn(message,e);
        throw new ServiceInterruption(message,
          e,
          currentTime + interruptionRetryTime,
          -1L,
          3,
          false);
      }

      // Other socket exceptions are service interruptions - but if we keep getting them, it means
      // that a socket timeout is probably set too low to accept this particular document.  So
      // we retry for a while, then skip the document.
      String message2 = "Socket exception during "+context+": "+e.getMessage();
      Logging.connectors.warn(message2,e);
      throw new ServiceInterruption(message2,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 20L * 60000L,
        -1,
        false);
    }

    // Otherwise, no idea what the trouble is, so presume that retries might fix it.
    String message3 = "IO exception during "+context+": "+e.getMessage();
    Logging.connectors.warn(message3,e);
    throw new ServiceInterruption(message3,
      e,
      currentTime + interruptionRetryTime,
      currentTime + 2L * 60L * 60000L,
      -1,
      true);
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
      throws ManifoldCFException, IOException {
    ConfigurationHandler.outputSpecificationHeader(out, locale, os, connectionSequenceNumber, tabsArray);
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
                                      int connectionSequenceNumber, int actualSequenceNumber, String tabName) throws ManifoldCFException, IOException {
    ConfigurationHandler.outputSpecificationBody(out, locale, os, connectionSequenceNumber, actualSequenceNumber, tabName);
  }

  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
                                         int connectionSequenceNumber) throws ManifoldCFException {
    return ConfigurationHandler.processSpecificationPost(variableContext, locale, os, connectionSequenceNumber);
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
                                int connectionSequenceNumber) throws ManifoldCFException, IOException {
    ConfigurationHandler.viewSpecification(out, locale, os, connectionSequenceNumber);
  }

}
