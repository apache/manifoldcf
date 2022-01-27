/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.solr.tests;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.manifoldcf.agents.output.solr.PreemptiveBasicAuthInterceptor;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PreemptiveBasicAuthInterceptorTest {

  @Test
  public void shouldAddBasicAuthenticationToRequestIfNotAlreadySet() throws Exception {
    final HttpRequestInterceptor interceptor = new PreemptiveBasicAuthInterceptor(AuthScope.ANY);
    final HttpContext context = contextWithoutBasicAuth(new UsernamePasswordCredentials("user", "secret"));
    interceptor.process(get(), context);
    final AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
    assertTrue(authState.getAuthScheme() instanceof BasicScheme);
    assertEquals("user", authState.getCredentials().getUserPrincipal().getName());
    assertEquals("secret", authState.getCredentials().getPassword());
  }

  @Test
  public void shouldThrowHttpExceptionIfNoCredentialsWereProvided() {
    final HttpRequestInterceptor interceptor = new PreemptiveBasicAuthInterceptor(AuthScope.ANY);
    final HttpContext context = contextWithoutBasicAuth(null);
    try {
      interceptor.process(get(), context);
      fail("Expected an HttpException, but none was raised.");
    } catch (HttpException e) {
      assertEquals("Missing credentials for preemptive basic authentication.", e.getMessage());
    } catch (IOException e) {
      fail("Expected an HttpException, but an IOException was raised instead.");
    }
  }

  private HttpRequest get() {
    return new BasicHttpRequest("GET", "https://manifoldcf.apache.org/");
  }

  private HttpContext contextWithoutBasicAuth(final Credentials credentials) {
    final CredentialsProvider credentialsProvider = new FakeCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, credentials);
    final AuthState authState = new AuthState();
    final HttpContext context = new FakeHttpContext();
    context.setAttribute(HttpClientContext.CREDS_PROVIDER, credentialsProvider);
    context.setAttribute(HttpClientContext.TARGET_AUTH_STATE, authState);
    return context;
  }

  static class FakeHttpContext implements HttpContext {

    private final Map<String, Object> context = new HashMap<>();

    @Override
    public Object getAttribute(final String id) {
      return context.get(id);
    }

    @Override
    public void setAttribute(final String id, final Object obj) {
      context.put(id, obj);
    }

    @Override
    public Object removeAttribute(final String id) {
      return context.remove(id);
    }
  }

  static class FakeCredentialsProvider implements CredentialsProvider {

    private final Map<AuthScope, Credentials> credentialsByAuthScope = new HashMap<>();

    @Override
    public void setCredentials(final AuthScope authScope, final Credentials credentials) {
      credentialsByAuthScope.put(authScope, credentials);
    }

    @Override
    public Credentials getCredentials(final AuthScope authScope) {
      return credentialsByAuthScope.get(authScope);
    }

    @Override
    public void clear() {
      credentialsByAuthScope.clear();
    }
  }
}
