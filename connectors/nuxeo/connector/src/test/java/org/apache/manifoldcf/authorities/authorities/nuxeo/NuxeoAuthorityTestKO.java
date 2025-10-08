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
package org.apache.manifoldcf.authorities.authorities.nuxeo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.interfaces.IAuthorityConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.user.User;
import org.nuxeo.client.objects.user.UserManager;


/**
 * @author David Arroyo Escobar &lt;arroyoescobardavid@gmail.com&gt;
 *
 */
@ExtendWith(MockitoExtension.class)
public class NuxeoAuthorityTestKO {

  @Mock
  private NuxeoClient client;

  private NuxeoAuthorityConnector authorityConnector;

  @BeforeEach
  public void setup() {
    authorityConnector = new NuxeoAuthorityConnector();
    authorityConnector.setNuxeoClient(client);
  }

  @Test
  public void check() throws Exception {

    when(client.userManager()).thenReturn(mock(UserManager.class));
    when(client.userManager().fetchUser(anyString())).thenReturn(mock(User.class));
    
    assertEquals(authorityConnector.check(), "Connection working");
  }

  @Test
  public void checkUserNotFound() throws Exception {
    UserManager userManager = mock(UserManager.class);
    User user = mock(User.class);
    
    when(client.userManager()).thenReturn(userManager);
    when(client.userManager().fetchUser("")).thenReturn(user);
    
    AuthorizationResponse response = authorityConnector.getAuthorizationResponse("NOT_USER_EXIST");
    String[] tokens = response.getAccessTokens();
    
    assertEquals(tokens.length, 1);
    assertEquals(tokens[0], IAuthorityConnector.GLOBAL_DENY_TOKEN);
    assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_USERNOTFOUND);
  }

  @Test
  public void checkUserFound() throws Exception {
    when(client.userManager()).thenReturn(mock(UserManager.class));
    when(client.userManager().fetchUser(anyString())).thenReturn(mock(User.class));
    
    AuthorizationResponse response = authorityConnector.getAuthorizationResponse("Administrator");
    
    String[] tokens = response.getAccessTokens();
    
    assertEquals(tokens.length, 1);
    assertEquals(response.getResponseStatus(), AuthorizationResponse.RESPONSE_OK);
  }
}
