/* $Id$ */

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
package org.apache.manifoldcf.crawler.connectors.gridfs.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.crawler.connectors.gridfs.GridFSRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/**
 * Integration test for the GridFS repository connector against a real MongoDB 9.0.2
 * instance started with Testcontainers (Docker is required).
 */
public class GridFSRepositoryConnectorHSQLDBIT extends BaseHSQLDB {

    private static final String DATABASE = "gridfsTestDatabase";
    private static final String BUCKET = "fs";
    private static final String USERNAME = "gridfsuser";
    private static final String PASSWORD = "gridfssecret";

    private static MongoDBContainer mongoDBContainer;

    private MongoClient testClient;
    private MongoDatabase testDatabase;
    private GridFSRepositoryConnector connector;

    private ObjectId documentWithMetadataId;
    private ObjectId legacyContentTypeId;
    private ObjectId documentWithoutMetadataId;

    @BeforeClass
    public static void startMongoDB() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:9.0.2"));
        mongoDBContainer.start();
    }

    @AfterClass
    public static void stopMongoDB() {
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
            mongoDBContainer = null;
        }
    }

    @Before
    public void setUpGridFS() throws Exception {
        testClient = MongoClients.create(MongoClientSettings.builder()
                .applyToClusterSettings(b -> b.hosts(Collections.singletonList(
                        new ServerAddress(mongoDBContainer.getHost(), mongoDBContainer.getFirstMappedPort()))))
                .build());
        testDatabase = testClient.getDatabase(DATABASE);
        GridFSBucket bucket = GridFSBuckets.create(testDatabase, BUCKET);

        // Modern layout: content type stored in the metadata document
        documentWithMetadataId = bucket.uploadFromStream("document.txt", stream("Hello GridFS"),
                new GridFSUploadOptions().metadata(new Document("url", "http://www.example.com/document.txt")
                        .append("contentType", "text/plain")
                        .append("acl", Arrays.asList("user1", "group1"))
                        .append("denyAcl", Collections.singletonList("user2"))));

        // Legacy layout: content type stored as a top-level field of the files document
        legacyContentTypeId = bucket.uploadFromStream("legacy.html", stream("<html></html>"),
                new GridFSUploadOptions().metadata(new Document("url", "http://www.example.com/legacy.html")));
        testDatabase.getCollection(BUCKET + ".files")
                .updateOne(Filters.eq("_id", legacyContentTypeId), Updates.set("contentType", "text/html"));

        // No metadata at all: must be skipped
        documentWithoutMetadataId = bucket.uploadFromStream("nometadata.bin", stream("binary"));

        testDatabase.runCommand(new Document("createUser", USERNAME)
                .append("pwd", PASSWORD)
                .append("roles", Collections.singletonList(new Document("role", "read").append("db", DATABASE))));

        connector = new GridFSRepositoryConnector();
        connector.connect(configParams(null, null));
    }

    @After
    public void cleanUpGridFS() throws Exception {
        if (connector != null) {
            connector.disconnect();
            connector = null;
        }
        if (testDatabase != null) {
            try {
                testDatabase.runCommand(new Document("dropUser", USERNAME));
            } catch (Exception e) {
                // ignore
            }
            testDatabase.drop();
            testDatabase = null;
        }
        if (testClient != null) {
            testClient.close();
            testClient = null;
        }
    }

    @Test
    public void checkConnection() throws Exception {
        assertEquals("Connection working", connector.check());
    }

    @Test
    public void checkConnectionWithCredentials() throws Exception {
        GridFSRepositoryConnector authenticated = new GridFSRepositoryConnector();
        authenticated.connect(configParams(USERNAME, PASSWORD));
        try {
            assertEquals("Connection working", authenticated.check());
        } finally {
            authenticated.disconnect();
        }

        GridFSRepositoryConnector wrongPassword = new GridFSRepositoryConnector();
        wrongPassword.connect(configParams(USERNAME, "wrong-password"));
        try {
            assertNotEquals("Connection working", wrongPassword.check());
        } finally {
            wrongPassword.disconnect();
        }
    }

    @Test
    public void seedsAllFiles() throws Exception {
        ISeedingActivity seedingActivity = mock(ISeedingActivity.class);

        connector.addSeedDocuments(seedingActivity, null, null, System.currentTimeMillis(), 0);

        verify(seedingActivity).addSeedDocument(documentWithMetadataId.toHexString());
        verify(seedingActivity).addSeedDocument(legacyContentTypeId.toHexString());
        verify(seedingActivity).addSeedDocument(documentWithoutMetadataId.toHexString());
        verify(seedingActivity, times(3)).addSeedDocument(anyString());
    }

    @Test
    public void processesDocuments() throws Exception {
        IProcessActivity activities = indexEverythingActivity();
        List<RepositoryDocument> ingested = new ArrayList<RepositoryDocument>();
        List<String> ingestedUris = new ArrayList<String>();
        List<byte[]> ingestedContents = new ArrayList<byte[]>();
        doAnswer(invocation -> {
            RepositoryDocument rd = invocation.getArgument(3);
            ingested.add(rd);
            ingestedUris.add(invocation.getArgument(2));
            ingestedContents.add(readAll(rd.getBinaryStream()));
            return null;
        }).when(activities).ingestDocumentWithException(anyString(), anyString(), anyString(), any(RepositoryDocument.class));

        String missingId = new ObjectId().toHexString();
        connector.processDocuments(new String[]{
                documentWithMetadataId.toHexString(),
                legacyContentTypeId.toHexString(),
                documentWithoutMetadataId.toHexString(),
                missingId
        }, null, null, activities, 0, true);

        // Two documents ingested, in order
        assertEquals(2, ingested.size());

        RepositoryDocument modern = ingested.get(0);
        assertEquals("http://www.example.com/document.txt", ingestedUris.get(0));
        assertEquals("document.txt", modern.getFileName());
        assertEquals("text/plain", modern.getMimeType());
        assertEquals("Hello GridFS", new String(ingestedContents.get(0), StandardCharsets.UTF_8));
        assertArrayEquals(new String[]{"user1", "group1"},
                modern.getSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT));
        String[] denyAcls = modern.getSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT);
        assertNotNull(denyAcls);
        assertEquals(2, denyAcls.length);
        assertEquals("user2", denyAcls[0]);

        RepositoryDocument legacy = ingested.get(1);
        assertEquals("http://www.example.com/legacy.html", ingestedUris.get(1));
        assertEquals("text/html", legacy.getMimeType());
        assertEquals("<html></html>", new String(ingestedContents.get(1), StandardCharsets.UTF_8));

        // No metadata: skipped; missing file: deleted
        verify(activities).noDocument(eq(documentWithoutMetadataId.toHexString()), anyString());
        verify(activities).deleteDocument(missingId);
        verify(activities, never()).deleteDocument(documentWithMetadataId.toHexString());
    }

    @Test
    public void versionStringChangesWhenMetadataChanges() throws Exception {
        String id = documentWithMetadataId.toHexString();
        List<String> versions = new ArrayList<String>();
        IProcessActivity activities = mock(IProcessActivity.class);
        when(activities.checkDocumentNeedsReindexing(anyString(), anyString())).thenAnswer(invocation -> {
            versions.add(invocation.getArgument(1));
            return false;
        });

        connector.processDocuments(new String[]{id}, null, null, activities, 0, true);
        connector.processDocuments(new String[]{id}, null, null, activities, 0, true);
        testDatabase.getCollection(BUCKET + ".files")
                .updateOne(Filters.eq("_id", documentWithMetadataId), Updates.set("metadata.url", "http://www.example.com/moved.txt"));
        connector.processDocuments(new String[]{id}, null, null, activities, 0, true);

        assertEquals(3, versions.size());
        assertFalse(versions.get(0).isEmpty());
        assertEquals("Version must be stable", versions.get(0), versions.get(1));
        assertNotEquals("Version must change with metadata", versions.get(1), versions.get(2));
        verify(activities, never()).ingestDocumentWithException(anyString(), anyString(), anyString(), any(RepositoryDocument.class));
    }

    @Test
    public void sessionLifecycle() throws Exception {
        assertFalse(connector.isConnected());
        connector.addSeedDocuments(mock(ISeedingActivity.class), null, null, System.currentTimeMillis(), 0);
        assertTrue(connector.isConnected());
        connector.disconnect();
        assertFalse(connector.isConnected());
        connector = null;
    }

    private IProcessActivity indexEverythingActivity() throws Exception {
        IProcessActivity activities = mock(IProcessActivity.class);
        when(activities.checkDocumentNeedsReindexing(anyString(), anyString())).thenReturn(true);
        when(activities.checkURLIndexable(any())).thenReturn(true);
        when(activities.checkLengthIndexable(anyLong())).thenReturn(true);
        when(activities.checkMimeTypeIndexable(any())).thenReturn(true);
        when(activities.checkDateIndexable(any())).thenReturn(true);
        return activities;
    }

    private ConfigParams configParams(String username, String password) {
        ConfigParams params = new ConfigParams();
        params.setParameter("host", mongoDBContainer.getHost());
        params.setParameter("port", String.valueOf(mongoDBContainer.getFirstMappedPort()));
        params.setParameter("db", DATABASE);
        params.setParameter("bucket", BUCKET);
        params.setParameter("url", "url");
        params.setParameter("acl", "acl");
        params.setParameter("denyAcl", "denyAcl");
        if (username != null) {
            params.setParameter("username", username);
            params.setObfuscatedParameter("password", password);
        }
        return params;
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
