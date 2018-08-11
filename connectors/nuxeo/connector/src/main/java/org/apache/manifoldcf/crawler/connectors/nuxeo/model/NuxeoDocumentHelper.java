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
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.Operation;
import org.nuxeo.client.objects.acl.ACE;
import org.nuxeo.client.objects.acl.ACL;
import org.nuxeo.client.objects.blob.Blob;

import com.google.common.collect.Maps;

public class NuxeoDocumentHelper {

    private static final String URI_TAGGING = "SELECT * FROM Tagging";

    private static final String DEFAULT_MIMETYPE = "text/html; charset=utf-8";
    private static final Set<String> AVOID_PROPERTIES = ImmutableSet.of("file:filename",
            "thumb:thumbnail", "file:content", "files:files" );

    private Document document;
    private InputStream content;
    private int size;
    private String mimetype;
    private String filename;
    private Map<String, Object> properties = null;

    public static final String DELETED = "deleted";

    private static final String DOC_UID = "uid";
    private static final String DOC_ENTITY_TYPE = "entity-type";
    private static final String DOC_LAST_MODIFIED = "last-modified";
    private static final String DOC_STATE = "state";
    private static final String NUXEO_MAJOR_VERSION_PROPERTY = "uid:major_version";
    private static final String NUXEO_MINOR_VERSION_PROPERTY = "uid:minor_version";
    private static final Set<String> NUXEO_FOLDER_TYPE = ImmutableSet.of("Folderish", "Collection");
    private static final String NUXEO_TAGS_PROPERTY = "nxtag:tags";

    private final static byte[] EMPTY_BYTES = new byte[0];
    
    // Constructor
    public NuxeoDocumentHelper(Document document) {
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

        for (Entry<String, Object> property : this.getProperties().entrySet()) {
            if (!AVOID_PROPERTIES.contains(property.getKey())) {
                try {
                    if (property.getKey().equalsIgnoreCase(NUXEO_TAGS_PROPERTY)){
                        List<Map> tags = (List) property.getValue();
                        List<String> tagsLabels = Lists.newArrayList();
                        for (Map tag: tags){
                            if (tag.containsKey("label")) {
                                tagsLabels.add((String) tag.get("label"));
                                break;
                            }
                        }
                        if (!tagsLabels.isEmpty())
                            docMetadata.put("tags", tagsLabels);
                    }else {
                        addIfNotEmpty(docMetadata, property.getKey(), property.getValue());
                    }
                } catch (Exception e){
                    continue;
                }
            }
        }
        addIfNotEmpty(docMetadata, DOC_UID, this.document.getUid());
        addIfNotEmpty(docMetadata, DOC_ENTITY_TYPE, this.document.getEntityType());
        addIfNotEmpty(docMetadata, DOC_LAST_MODIFIED, this.document.getLastModified());
        addIfNotEmpty(docMetadata, DOC_STATE, this.document.getState());

        return docMetadata;
    }

    public boolean isFolder(){
        return !Collections.disjoint(document.getFacets(), NUXEO_FOLDER_TYPE);
    }

    private void addIfNotEmpty(Map<String, Object> docMetadata, String key, Object obj) {
        if (obj != null && ((obj instanceof String && !((String) obj).isEmpty()) || !(obj instanceof String))) {
            docMetadata.put(key, obj);
        }
    }

    private void processDocument() {
        try {
            Blob blob = document.fetchBlob();
            this.content = blob.getStream();
            this.mimetype = blob.getMimeType();
            this.size = blob.getLength();
            this.filename = blob.getFilename();
        } catch (Exception ex) {
            this.content = new ByteArrayInputStream(EMPTY_BYTES);
            this.mimetype = DEFAULT_MIMETYPE;
            this.size = 0;
            this.filename = document.getTitle();
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
        return this.size;
    }

    public InputStream getContent() {
        return this.content;
    }

    public String getFilename(){
        return filename;
    }

    private Map<String, Object> getProperties(){
        if (this.properties == null)
            this.properties = document.getProperties();

        return this.properties;
    }

    public String getVersion(){
        StringBuilder builder = new StringBuilder();
        builder.append(this.document.getUid());
        builder.append('_');
        if (this.getProperties().containsKey(NUXEO_MAJOR_VERSION_PROPERTY)
                && this.getProperties().containsKey(NUXEO_MINOR_VERSION_PROPERTY)){
            builder.append(this.getProperties().get(NUXEO_MAJOR_VERSION_PROPERTY));
            builder.append('.');
            builder.append(this.getProperties().get(NUXEO_MINOR_VERSION_PROPERTY));
        }else{
            builder.append("0.0");
        }

        return builder.toString();
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

    public List<NuxeoAttachment> getAttachments(NuxeoClient nuxeoClient) {
        List<NuxeoAttachment> attachments = new ArrayList<>();
        List<?> arrayList = this.document.getPropertyValue(NuxeoAttachment.ATT_KEY_FILES);

        for (Object object : arrayList) {
            NuxeoAttachment attach = new NuxeoAttachment();
            LinkedHashMap<?, ?> file = (LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) object)
                    .get(NuxeoAttachment.ATT_KEY_FILE);

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
                attach.data = new ByteArrayInputStream(EMPTY_BYTES);
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
