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

package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.Operation;
import org.nuxeo.client.objects.acl.ACE;
import org.nuxeo.client.objects.acl.ACL;
import org.nuxeo.client.objects.blob.Blob;

import com.google.common.collect.Maps;

public class DocumentManifold {

  private static final String URI_TAGGING = "SELECT * FROM Tagging";

  private static final String DEFAULT_MIMETYPE = "text/html; charset=utf-8";
  private static final String[] avoid_properties = { "file:filename", "file:content", "files:files" };

  private Document document;
  private InputStream content;
  private String mimetype;

  public static final String DELETED = "deleted";

  private static final String DOC_UID = "uid";
  private static final String DOC_ENTITY_TYPE = "entity-type";
  private static final String DOC_LAST_MODIFIED = "last-modified";
  private static final String DOC_STATE = "state";

  // Constructor
  public DocumentManifold(Document document) {
    this.document = document;
    processDocument();
  }


  /**
   * 
   * 
   * @return Map<String, Object>
   */
  public Map<String, Object> getMetadata() {
    Map<String, Object> docMetadata = Maps.newHashMap();

    for (Entry<String, Object> property : this.document.getProperties().entrySet()) {
      if (!Arrays.asList(avoid_properties).contains(property.getKey())) {
        addIfNotEmpty(docMetadata, property.getKey(), property.getValue());
      }
    }
    addIfNotEmpty(docMetadata, DOC_UID, this.document.getUid());
    addIfNotEmpty(docMetadata, DOC_ENTITY_TYPE, this.document.getEntityType());
    addIfNotEmpty(docMetadata, DOC_LAST_MODIFIED, this.document.getLastModified());
    addIfNotEmpty(docMetadata, DOC_STATE, this.document.getState());

    return docMetadata;
  }

  private void addIfNotEmpty(Map<String, Object> docMetadata, String key, Object obj) {
    if (obj != null && ((obj instanceof String && !((String) obj).isEmpty()) || !(obj instanceof String))) {
      docMetadata.put(key, obj);
    }
  }

  private void processDocument() {
    try {
      this.content = document.fetchBlob().getStream();
      this.mimetype = (String) ((Map) this.document.getPropertyValue("file:content")).get("mime-type");
    } catch (Exception ex) {
      this.content = new ByteArrayInputStream("".getBytes());
      this.mimetype = DEFAULT_MIMETYPE;
    }
  }

  // GETTERS AND SETERS
  public Document getDocument() {
    return this.document;
  }

  public String getMimeType() {
    return this.mimetype;
  }

  public int getLength() {
    int size;
    try {
      size = this.getContent().available();
    } catch (IOException ex) {
      size = 0;
    }
    return size;
  }

  public InputStream getContent() {
    return this.content;
  }

  public String[] getPermissions() {

    List<String> permissions = new ArrayList<>();
    try {
      for (ACL acl : this.getDocument().fetchPermissions().getAcls()) {
        for (ACE ace : acl.getAces()) {
          if (ace.getStatus().equalsIgnoreCase("effective") && ace.getGranted().equalsIgnoreCase("true")) {
            permissions.add(ace.getUsername());
          }
        }
      }

      return permissions.toArray(new String[permissions.size()]);
    } catch (Exception e) {
      return new String[] {};
    }
  }

  public List<Attachment> getAttachments(NuxeoClient nuxeoClient) {
    List<Attachment> attachments = new ArrayList<>();
    List<?> arrayList = this.document.getPropertyValue(Attachment.ATT_KEY_FILES);

    for (Object object : arrayList) {
      Attachment attach = new Attachment();
      LinkedHashMap<?, ?> file = (LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) object)
          .get(Attachment.ATT_KEY_FILE);

      attach.name = (String) file.get("name");
      attach.encoding = (String) file.get("encoding");
      attach.mime_type = (String) file.get("mime-type");
      attach.digestAlgorithm = (String) file.get("digestAlgorithm");
      attach.digest = (String) file.get("digest");
      attach.length = Long.valueOf((String) file.get("length"));
      attach.url = (String) file.get("data");

      try {
        Blob blob = nuxeoClient.repository().fetchBlobById(this.document.getUid(), 
            getAttachPath(attach.url));
        
        attach.data = blob.getStream();

      } catch (Exception ex) {
        attach.data = new ByteArrayInputStream("".getBytes());
      }

      attachments.add(attach);

    }

    return attachments;
  }

  private String getAttachPath(String absolutePath) {
    String[] splitPath = absolutePath.split("/");
    int size = splitPath.length;
    return String.join("/", splitPath[size - 4], splitPath[size - 3], splitPath[size - 2]);
  }

  public String[] getTags(NuxeoClient nuxeoClient) {
    try {
      Operation op = nuxeoClient.operation("Repository.Query").param("query",
          URI_TAGGING + " where relation:source='" + this.document.getUid() + "'");
      Documents tags = op.execute();
      List<String> ls = new ArrayList<>();

      for (Document tag : tags.getDocuments()) {
        ls.add(tag.getTitle());
      }
      return ls.toArray(new String[tags.size()]);
    } catch (Exception e) {
      return new String[] {};
    }
  }

}
