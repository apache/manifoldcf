/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.tests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Document;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoResponse;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.system.SeedingActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NuxeoConnectorTest extends AbstractTest {

	@Override
	public void setup() throws Exception {
		super.setup();

		when(client.getDocuments(anyListOf(String.class), anyListOf(String.class), anyString(), anyInt(), anyInt(),
				anyObject())).thenReturn(new NuxeoResponse<Document>(Collections.<Document> emptyList(), 0, 0, true));
	};

	@Test
	public void checkMockInjection() throws Exception {
		when(client.check()).thenReturn(true);
		assertEquals(repositoryConnector.check(), "Connection working");
	}

	@Test
	public void mockSeeding() throws Exception {
		SeedingActivity activities = mock(SeedingActivity.class);
		Specification spec = new Specification();
		List<Document> documents = new ArrayList<Document>();
		Document document = mock(Document.class);
		long seedTime = 0;

		documents.add(document);
		documents.add(document);

		when(client.getDocuments(anyListOf(String.class), anyListOf(String.class), anyString(), anyInt(), anyInt(),
				anyObject())).thenReturn(new NuxeoResponse<Document>(documents, 0, 0, true))
						.thenReturn(new NuxeoResponse<Document>(Collections.<Document> emptyList(), 0, 0, true));

		repositoryConnector.addSeedDocuments(activities, spec, "", seedTime,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS);

		verify(activities, times(2)).addSeedDocument(anyString());
		verify(client, times(1)).getDocuments(anyListOf(String.class), anyListOf(String.class), anyString(), anyInt(),
				anyInt(), anyObject());

	}

	@Test
	public void mockEmptySeeding() throws Exception {
		SeedingActivity activities = mock(SeedingActivity.class);
		Specification spec = new Specification();
		long seedTime = 0;

		when(client.getDocuments(anyListOf(String.class), anyListOf(String.class), anyString(), anyInt(), anyInt(),
				anyObject())).thenReturn(new NuxeoResponse<Document>(Collections.<Document> emptyList(), 0, 0, true));

		repositoryConnector.addSeedDocuments(activities, spec, "", seedTime,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS);

		verify(activities, times(0)).addSeedDocument(anyString());
		verify(client, times(1)).getDocuments(anyListOf(String.class), anyListOf(String.class), anyString(), anyInt(),
				anyInt(), anyObject());

	}

	@Test
	public void mockDeleteDocument() throws Exception {
		Document doc = mock(Document.class);
		String uid = "297529bf-191a-4c87-8259-28b692394229";
		Specification spec = new Specification();
		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);

		when(doc.getState()).thenReturn("deleted");
		when(client.getDocument(uid)).thenReturn(doc);

		repositoryConnector.processDocuments(new String[] { uid }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);

		verify(client, times(1)).getDocument(anyString());
		verify(activities, times(1)).deleteDocument(uid);
	}

	@Test
	public void mockSimpleIngestion() throws Exception {

		Document doc = mock(Document.class);
		Date date = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		Long size = 0L;
		String id;
		String uri = "http://localhost:8080/nuxeo/site/api/v1/id/7995ff6d-1eda-41db-b9de-3ea4037fdb81";
		Map<String, Object> metadata = new HashMap<String, Object>();
		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);
		Specification spec = new Specification();
		String mediaType = "text/html; charset=utf-8";

		metadata.put("key", "value");
		id = df.format(date);

		when(doc.getLenght()).thenReturn(size);
		when(doc.getLastModified()).thenReturn(date);
		when(doc.getMediatype()).thenReturn(mediaType);
		when(doc.getMetadataAsMap()).thenReturn(metadata);
		when(doc.getUid()).thenReturn(uri);

		when(activities.checkDocumentNeedsReindexing(anyString(), anyString())).thenReturn(true);

		when(statuses.getIndexedVersionString(id)).thenReturn(null);

		when(client.getDocument(anyString())).thenReturn(doc);

		when(client.getPathDocument(anyString())).thenReturn(uri);

		repositoryConnector.processDocuments(new String[] { id }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
		ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

		verify(client, times(1)).getDocument(id);
		verify(activities, times(1)).ingestDocumentWithException(eq(id), eq(df.format(date)), eq(uri), ac.capture());
		verify(activities, times(1)).recordActivity(anyLong(), eq("read document"), eq(size), eq(id), eq("OK"),
				anyString(), Mockito.isNull(String[].class));

		RepositoryDocument rd = ac.getValue();
		Long rdSize = rd.getBinaryLength();

		String[] keyValue = rd.getFieldAsStrings("key");
		assertEquals(size, rdSize);

		assertEquals(keyValue.length, 1);
		assertEquals(keyValue[0], "value");
	}

	@Test
	public void mockNotNeedReindexing() throws Exception {
		Document doc = mock(Document.class);
		Date date = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		String version = df.format(date);
		String uid = "297529bf-191a-4c87-8259-28b692394229";

		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);
		Specification spec = new Specification();

		when(doc.getLastModified()).thenReturn(df.parse(version));
		when(statuses.getIndexedVersionString(uid)).thenReturn(version);
		when(client.getDocument(anyString())).thenReturn(doc);

		repositoryConnector.processDocuments(new String[] { uid }, statuses, spec, activities,
				BaseRepositoryConnector.JOBMODE_CONTINUOUS, true);
		ArgumentCaptor<RepositoryDocument> ac = ArgumentCaptor.forClass(RepositoryDocument.class);

		verify(client, times(1)).getDocument(uid);
		verify(activities, times(1)).checkDocumentNeedsReindexing(uid, version);
		verify(activities, times(0)).ingestDocumentWithException(anyString(), anyString(), anyString(), ac.capture());

	}

}
