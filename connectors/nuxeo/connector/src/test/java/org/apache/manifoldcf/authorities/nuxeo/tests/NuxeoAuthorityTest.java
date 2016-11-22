/**
 * 
 */
package org.apache.manifoldcf.authorities.nuxeo.tests;

import org.apache.manifoldcf.authorities.authorities.nuxeo.NuxeoAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.interfaces.IAuthorityConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.client.NuxeoClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

import org.mockito.Mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyString;

import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NuxeoAuthorityTest {

	@Mock
	private NuxeoClient client;

	private NuxeoAuthorityConnector authorityConnector;

	@Before
	public void setup() throws Exception {
		authorityConnector = new NuxeoAuthorityConnector();
		authorityConnector.setNuxeoClient(client);
	}

	@Test
	public void check() throws Exception {
		when(client.checkAuth()).thenReturn(true);
		assertEquals(authorityConnector.check(), "Connection working");
	}

	@Test
	public void checkUserNotFound() throws Exception {
		when(client.getUserAuthorities(anyString())).thenReturn(new ArrayList<String>());
		AuthorizationResponse response = authorityConnector.getAuthorizationResponse(anyString());
		String[] tokens = response.getAccessTokens();
		
		assertEquals(tokens.length, 1);
		assertEquals(tokens[0], IAuthorityConnector.GLOBAL_DENY_TOKEN);
		assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_USERNOTFOUND);
	}

	@Test
	public void checkuserFound() throws Exception {
		List<String> tokensList = new ArrayList<String>();
		tokensList.add("token");
		when(client.getUserAuthorities(anyString())).thenReturn(tokensList);
		AuthorizationResponse response = authorityConnector.getAuthorizationResponse(anyString());
		
		String[] tokens = response.getAccessTokens();
		assertEquals(tokens.length, 1);
		assertEquals(tokens[0], tokensList.get(0));
		assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_OK);
	}
}
