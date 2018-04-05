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
package org.apache.manifoldcf.crawler.connectors.nuxeo;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoDocumentHelper;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.system.SeedingActivity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.Repository;
import org.nuxeo.client.objects.blob.FileBlob;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NuxeoConnectorTest {

    @Mock
    private NuxeoClient client;

    private NuxeoRepositoryConnector repositoryConnector;

    @Before
    public void setup(){

        repositoryConnector = new NuxeoRepositoryConnector();
        repositoryConnector.setNuxeoClient(client);
        repositoryConnector.protocol = "http";
        repositoryConnector.host = "localhost";
        repositoryConnector.port = "8080";
        repositoryConnector.path = "/nuxeo";
    }

    @Test
    public void checkMockInjection() throws Exception {
        Document doc = mock(Document.class);
        Repository repository = mock(Repository.class);

        when(client.repository()).thenReturn(repository);
        when(client.repository().fetchDocumentRoot()).thenReturn(doc);
        assertEquals(repositoryConnector.check(), "Connection working");
    }

    @Test
    public void mockSeeding() throws Exception {
        SeedingActivity activities = mock(SeedingActivity.class);
        Specification spec = new Specification();
        Repository repository = mock(Repository.class);
        Document document = mock(Document.class);
        List<Document> documents = new ArrayList<>();
        documents.add(document);
        documents.add(document);
        Documents docs = mock(Documents.class);
        long seedTime = 0;


        when(client.repository()).thenReturn(repository);
        when(client.repository().query(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(docs);
        when(docs.isNextPageAvailable()).thenReturn(false);
        when(docs.getDocuments()).thenReturn(documents);

        repositoryConnector.addSeedDocuments(activities, spec, "", seedTime,
                BaseRepositoryConnector.JOBMODE_CONTINUOUS);

        verify(activities, times(2)).addSeedDocument(anyString());

    }

    @Test
    public void mockEmptySeeding() throws Exception {
        SeedingActivity activities = mock(SeedingActivity.class);
        Specification spec = new Specification();
        Repository repository = mock(Repository.class);
        List<Document> documents = new ArrayList<>();
        Documents docs = mock(Documents.class);
        long seedTime = 0;


        when(client.repository()).thenReturn(repository);
        when(client.repository().query(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(docs);
        when(docs.isNextPageAvailable()).thenReturn(false);
        when(docs.getDocuments()).thenReturn(documents);

        repositoryConnector.addSeedDocuments(activities, spec, "", seedTime,
                BaseRepositoryConnector.JOBMODE_CONTINUOUS);

        verify(activities, times(0)).addSeedDocument(anyString());
    }

    @Test
    public void mockDeleteDocument() throws Exception {
        NuxeoDocumentHelper docMa = mock(NuxeoDocumentHelper.class);
        Repository repository = mock(Repository.class);
        Document doc = mock(Document.class);
        Specification spec = new Specification();
        IProcessActivity activities = mock(IProcessActivity.class);
        IExistingVersions statuses = mock(IExistingVersions.class);

        when(client.repository()).thenReturn(repository);
        when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);
        when(docMa.getDocument()).thenReturn(doc);
        when(doc.getType()).thenReturn("Document");
        when(doc.getState()).thenReturn("deleted");

        repositoryConnector.processDocuments(new String[] { anyString() }, statuses, spec, activities,
                BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);

        verify(activities, times(1)).deleteDocument(anyString());
    }

    @Test
    public void mockSimpleIngestion() throws Exception {

        Document doc = mock(Document.class);
        NuxeoDocumentHelper docMa = mock(NuxeoDocumentHelper.class);
        FileBlob blob = mock(FileBlob.class);
        Date date = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        final String lastModified = df.format(date);
        final String id = "7995ff6d-1eda-41db-b9de-3ea4037fdb81";
        final String path = "/workspaces/folder/001001-0001-000000010690";
        final String repository_name = "default-domain";

        Map<String, Object> metadata = new HashMap<>();
        IProcessActivity activities = mock(IProcessActivity.class);
        IExistingVersions statuses = mock(IExistingVersions.class);
        Specification spec = new Specification();
        Repository repository = mock(Repository.class);
        final Long size = 1000000L;

        when(docMa.getMetadata()).thenReturn(metadata);

        metadata.put("key", "value");

        when(blob.getLength()).thenReturn((int)size.intValue());
        when(doc.fetchBlob()).thenReturn(blob);

        when(doc.getLastModified()).thenReturn(lastModified);
        when(doc.getUid()).thenReturn(id);
        when(doc.getPath()).thenReturn(path);
        when(doc.getType()).thenReturn("Document");
        when(doc.getRepositoryName()).thenReturn(repository_name);
        when(client.repository()).thenReturn(repository);
        when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);

        when(activities.checkDocumentNeedsReindexing(anyString(), anyString())).thenReturn(true);
        when(activities.checkLengthIndexable(anyLong())).thenReturn(true);
        when(activities.checkDateIndexable(anyObject())).thenReturn(true);
        when(activities.checkURLIndexable(anyString())).thenReturn(true);
        when(activities.checkMimeTypeIndexable(anyString())).thenReturn(true);
        when(statuses.getIndexedVersionString(id)).thenReturn(null);


        repositoryConnector.processDocuments(new String[] { id }, statuses, spec, activities,
                BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
        ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

        String expectedUri = repositoryConnector.getUrl() + "/nxpath/" +
                doc.getRepositoryName() + doc.getPath() + "@view_documents";

        verify(activities, times(1)).ingestDocumentWithException(eq(id), eq(id+"_0.0"), eq(expectedUri), ac.capture());
        verify(activities, times(1)).recordActivity(anyLong(), eq("read document"), eq(size), eq(id), eq("OK"),
                anyString(), Mockito.isNull(String[].class));

        RepositoryDocument rd = ac.getValue();
        Long rdSize = rd.getBinaryLength();

        String[] keyValue = rd.getFieldAsStrings("uid");

        assertEquals(size, rdSize);
        assertEquals(keyValue.length, 1);
    }

    @Test
    public void mockNotNeedReindexing() throws Exception {
        Document doc = mock(Document.class);
        Repository repository = mock(Repository.class);
        Date date = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        final String lastModified = df.format(date);
        String uid = "297529bf-191a-4c87-8259-28b692394229";
        final String path = "/workspaces/folder/001001-0001-000000010690";
        final String repository_name = "default-domain";
        final String version = uid + "_" + "0.0";

        IProcessActivity activities = mock(IProcessActivity.class);
        IExistingVersions statuses = mock(IExistingVersions.class);
        Specification spec = new Specification();

        when(doc.getLastModified()).thenReturn(lastModified);
        when(doc.getUid()).thenReturn(uid);
        when(doc.getPath()).thenReturn(path);
        when(doc.getType()).thenReturn("Document");
        when(doc.getRepositoryName()).thenReturn(repository_name);
        when(statuses.getIndexedVersionString(uid)).thenReturn(version);
        when(client.repository()).thenReturn(repository);
        when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);
        when(doc.getType()).thenReturn("Document");

        repositoryConnector.processDocuments(new String[] { uid }, statuses, spec, activities,
                BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
        ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

        verify(activities, times(1)).checkDocumentNeedsReindexing(uid, lastModified);
        verify(activities, times(0)).ingestDocumentWithException(anyString(), anyString(), anyString(), ac.capture());

    }

}
