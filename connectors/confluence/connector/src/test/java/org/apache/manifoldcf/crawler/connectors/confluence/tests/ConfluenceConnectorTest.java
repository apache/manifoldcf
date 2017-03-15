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
package org.apache.manifoldcf.crawler.connectors.confluence.tests;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.confluence.ConfluenceRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.confluence.client.ConfluenceClient;
import org.apache.manifoldcf.crawler.connectors.confluence.model.ConfluenceResponse;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Page;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.system.SeedingActivity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Optional;

@RunWith(MockitoJUnitRunner.class)
public class ConfluenceConnectorTest {

	@Mock
	private ConfluenceClient client;
	
	private ConfluenceRepositoryConnector connector;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception{
		connector = new ConfluenceRepositoryConnector();
		connector.setConfluenceClient(client);
		when(client.getPages(anyInt(), anyInt(), Mockito.any(Optional.class))).
			thenReturn(new ConfluenceResponse<Page>(Collections.<Page>emptyList(), 0, 0, true));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void mockEmptySeeding() throws Exception {
		
		SeedingActivity activities = mock(SeedingActivity.class);
		Specification spec = new Specification();
		long seedTime = 0;

		connector.addSeedDocuments(activities, spec, "", seedTime, BaseRepositoryConnector.JOBMODE_ONCEONLY);
		// Verify it starts always at 0. Pagination configurable so anyInt(). Only one call because isLast must be false
		verify(client, times(1)).getPages(eq(0), anyInt(), Mockito.any(Optional.class));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void mockSeeding() throws Exception {
		
		SeedingActivity activities = mock(SeedingActivity.class);
		Specification spec = new Specification();
		long seedTime = 0;
	
		List<Page> pages = new ArrayList<Page>();
		Page page = mock(Page.class);
		pages.add(page);
		when(client.getPages(anyInt(), anyInt(), Mockito.any(Optional.class))).
			thenReturn(new ConfluenceResponse<Page>(pages, 0, 0, false)).
			thenReturn(new ConfluenceResponse<Page>(Collections.<Page>emptyList(), 0, 0, true));
		connector.addSeedDocuments(activities, spec, "", seedTime, BaseRepositoryConnector.JOBMODE_ONCEONLY);
		verify(activities, times(1)).addSeedDocument(Mockito.anyString());
		verify(client, times(1)).getPages(eq(0), anyInt(), Mockito.any(Optional.class));
		verify(client, times(1)).getPages(eq(1), anyInt(), Mockito.any(Optional.class));
	}
	
	@Test
	public void mockSimpleIngestion() throws Exception{
		
		Page fakePage = mock(Page.class);
		
		Date date = new Date();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.ROOT);
		String content = "A";
		String uri = "http://test";
		byte[] documentBytes = content
				.getBytes(StandardCharsets.UTF_8);
		long size = (long) documentBytes.length;
		
		when(fakePage.hasContent()).thenReturn(true);
		when(fakePage.getContent()).thenReturn(content);
		when(fakePage.getLength()).thenReturn(size);
		when(fakePage.getLastModifiedDate()).thenReturn(date);
		when(fakePage.getMediaType()).thenReturn("text/plain");
		when(fakePage.getCreatedDate()).thenReturn(date);
		when(fakePage.getWebUrl()).thenReturn(uri);
		Map<String, Object> metadata = new HashMap<String, Object>();
		metadata.put("x", "y");	
		when(fakePage.getMetadataAsMap()).thenReturn(metadata);
						
		IProcessActivity activities = mock(IProcessActivity.class);
	    when(activities.checkLengthIndexable(anyLong()))
	      .thenReturn(true);
	    when(activities.checkMimeTypeIndexable(anyString()))
	      .thenReturn(true);
	    when(activities.checkDateIndexable((Date)anyObject()))
	      .thenReturn(true);
	    when(activities.checkURLIndexable(anyString()))
	      .thenReturn(true);
	    when(activities.checkDocumentNeedsReindexing(anyString(), anyString()))
	      .thenReturn(true);
	    IExistingVersions statuses = mock(IExistingVersions.class);
	    
	    String ID = df.format(date);
	    when(statuses.getIndexedVersionString(ID)).
	    	thenReturn(null);
	    
	    when(client.getPage(Mockito.anyString())).
	    	thenReturn(fakePage);
	    
	    connector.processDocuments(new String[]{ID}, statuses, new Specification(), activities, 0, true);
	    ArgumentCaptor<RepositoryDocument> rd = ArgumentCaptor.forClass(RepositoryDocument.class);
	    
	    verify(client, times(1)).getPage(ID);
	    verify(activities, times(1)).ingestDocumentWithException(eq(ID),
				eq(df.format(date)), eq(uri), rd.capture());
	    verify(activities, times(1)).recordActivity(anyLong(),
	    		eq("read document"), eq(size), eq(ID), eq("OK"),
				anyString(), Mockito.isNull(String[].class));
	    
	    RepositoryDocument doc = rd.getValue();
	    Assert.assertEquals(size, doc.getBinaryLength());
	    String[] values = doc.getFieldAsStrings("x");
	    Assert.assertEquals(values.length, 1);
	    Assert.assertEquals(values[0], "y");
	    
	}
	
	@Test
	public void mockNeedsReindexing() throws Exception{
		Page fakePage = mock(Page.class);
		when(fakePage.hasContent()).thenReturn(true);
		Date date = new Date();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.ROOT);
		String version = df.format(date);
		when(fakePage.getLastModifiedDate()).thenReturn(df.parse(version));
		
		String id = "1";
		IProcessActivity activities = mock(IProcessActivity.class);
		IExistingVersions statuses = mock(IExistingVersions.class);
		when(statuses.getIndexedVersionString(id)).
			thenReturn(version);
		
		when(client.getPage(Mockito.anyString())).
    		thenReturn(fakePage);
		
		connector.processDocuments(new String[]{id}, statuses, new Specification(), activities, 0, true);
		verify(client, times(1)).getPage(id);
		verify(activities, times(1)).checkDocumentNeedsReindexing(id, version);
	}
	
	@Test
	public void mockDeleteDocument() throws Exception{
		Page fakePage = mock(Page.class);
		when(fakePage.hasContent()).thenReturn(false);
		String id = "A";
		when(fakePage.hasContent()).thenReturn(false);
		when(client.getPage(Mockito.anyString())).
    	thenReturn(fakePage);
		
		IExistingVersions statuses = mock(IExistingVersions.class);
		IProcessActivity activities = mock(IProcessActivity.class);
		connector.processDocuments(new String[]{id}, statuses, new Specification(), activities, 0, true);
		verify(client, times(1)).getPage(id);
		verify(activities, times(1)).deleteDocument(id);
				
	}
		
}
