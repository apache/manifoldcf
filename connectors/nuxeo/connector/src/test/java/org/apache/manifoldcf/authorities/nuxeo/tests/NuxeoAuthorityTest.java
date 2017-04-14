/**
 * 
 */
package org.apache.manifoldcf.authorities.nuxeo.tests;

import org.apache.manifoldcf.authorities.authorities.nuxeo.NuxeoAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.interfaces.IAuthorityConnector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import org.nuxeo.client.api.NuxeoClient;
import org.nuxeo.client.api.objects.user.User;
import org.nuxeo.client.api.objects.user.UserManager;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
						
		when(client.getUserManager()).thenReturn(mock(UserManager.class));
		when(client.getUserManager().fetchUser(anyString())).thenReturn(mock(User.class));
		
		assertEquals(authorityConnector.check(), "Connection working");
	}

	@Test
	public void checkUserNotFound() throws Exception {
		UserManager userManager = mock(UserManager.class);
		User user = mock(User.class);
		
		when(client.getUserManager()).thenReturn(userManager);
		when(client.getUserManager().fetchUser("")).thenReturn(user);
		
		AuthorizationResponse response = authorityConnector.getAuthorizationResponse("NOT_USER_EXIST");
		String[] tokens = response.getAccessTokens();
		
		assertEquals(tokens.length, 1);
		assertEquals(tokens[0], IAuthorityConnector.GLOBAL_DENY_TOKEN);
		assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_USERNOTFOUND);
	}

	@Test
	public void checkUserFound() throws Exception {
		when(client.getUserManager()).thenReturn(mock(UserManager.class));
		when(client.getUserManager().fetchUser(anyString())).thenReturn(mock(User.class));
		
		AuthorizationResponse response = authorityConnector.getAuthorizationResponse("Administrator");
		
		String[] tokens = response.getAccessTokens();
		
		assertEquals(tokens.length, 1);
		assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_OK);
	}
}
