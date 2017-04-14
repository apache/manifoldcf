/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.tests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.NuxeoRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.DocumentManifold;
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
import org.nuxeo.client.api.NuxeoClient;
import org.nuxeo.client.api.objects.Document;
import org.nuxeo.client.api.objects.Documents;
import org.nuxeo.client.api.objects.Repository;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NuxeoConnectorTest {

	@Mock
	public NuxeoClient client;

	public NuxeoRepositoryConnector repositoryConnector;

	@Before
	public void setup() throws Exception {

		repositoryConnector = new NuxeoRepositoryConnector();
		repositoryConnector.setNuxeoClient(client);
	};

	@Test
	public void checkMockInjection() throws Exception {
		Document doc = mock(Document.class);
		Repository repository = mock(Repository.class);
		
		when(client.repository()).thenReturn(repository);
		when(client.repository().getDocumentRoot()).thenReturn(doc);
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
		when(docs.getIsNextPageAvailable()).thenReturn(false);
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
		when(docs.getIsNextPageAvailable()).thenReturn(false);
		when(docs.getDocuments()).thenReturn(documents);
		
		repositoryConnector.addSeedDocuments(activities, spec, "", seedTime,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS);

		verify(activities, times(0)).addSeedDocument(anyString());
	}

	@Test
	public void mockDeleteDocument() throws Exception {
		DocumentManifold docMa = mock(DocumentManifold.class);
		Repository repository = mock(Repository.class);
		Document doc = mock(Document.class);
		Specification spec = new Specification();
		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);

		when(client.repository()).thenReturn(repository);
		when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);
		when(docMa.getDocument()).thenReturn(doc);
		when(doc.getState()).thenReturn("deleted");

		repositoryConnector.processDocuments(new String[] { anyString() }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);

		verify(activities, times(1)).deleteDocument(anyString());
	}

	@Test
	public void mockSimpleIngestion() throws Exception {

		Document doc = mock(Document.class);
		DocumentManifold docMa = mock(DocumentManifold.class);
		Date date = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		String version = df.format(date);
		Long size = 0L;
		String id;
		String uri = "http://localhost:8080/nuxeo/site/api/v1/id/7995ff6d-1eda-41db-b9de-3ea4037fdb81";
		Map<String, Object> metadata = new HashMap<String, Object>();
		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);
		Specification spec = new Specification();
		Repository repository = mock(Repository.class);


		metadata.put("key", "value");
		id = df.format(date);

		when(doc.get("lenght")).thenReturn(size);
		when(doc.getLastModified()).thenReturn(version);
		when(docMa.getMetadata()).thenReturn(metadata);
		when(doc.getUid()).thenReturn(uri);
		when(client.repository()).thenReturn(repository);
		when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);

		when(activities.checkDocumentNeedsReindexing(anyString(), anyString())).thenReturn(true);
		when(statuses.getIndexedVersionString(id)).thenReturn(null);


		repositoryConnector.processDocuments(new String[] { id }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
		ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

		verify(activities, times(1)).ingestDocumentWithException(eq(id), eq(df.format(date)), eq(uri), ac.capture());
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
		Date date = new Date();
		Repository repository = mock(Repository.class);
		DateFormat df = DateFormat.getDateTimeInstance();
		String version = df.format(date);
		String uid = "297529bf-191a-4c87-8259-28b692394229";

		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);
		Specification spec = new Specification();

		when(doc.getLastModified()).thenReturn(version);
		when(statuses.getIndexedVersionString(uid)).thenReturn(version);
		when(client.repository()).thenReturn(repository);
		when(client.repository().fetchDocumentById(anyString())).thenReturn(doc);

		repositoryConnector.processDocuments(new String[] { uid }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
		ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

		verify(activities, times(1)).checkDocumentNeedsReindexing(uid, version);
		verify(activities, times(0)).ingestDocumentWithException(anyString(), anyString(), anyString(), ac.capture());

	}

}
