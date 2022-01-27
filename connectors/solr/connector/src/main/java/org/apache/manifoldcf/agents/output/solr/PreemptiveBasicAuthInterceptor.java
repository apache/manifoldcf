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
package org.apache.manifoldcf.agents.output.solr;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class PreemptiveBasicAuthInterceptor implements HttpRequestInterceptor {

  private final AuthScope scope;

  public PreemptiveBasicAuthInterceptor(final AuthScope scope) {
    this.scope = scope;
  }

  @Override
  public void process(final HttpRequest request,
                      final HttpContext context) throws HttpException, IOException {
    if (!alreadyAppliesAuthScheme(context)) {
      final CredentialsProvider provider = getCredentialsProvider(context);
      final Credentials credentials = provider.getCredentials(scope);
      if (credentials == null) {
        throw new HttpException("Missing credentials for preemptive basic authentication.");
      }
      final AuthState state = getAuthState(context);
      state.update(new BasicScheme(), credentials);
    }
  }

  private boolean alreadyAppliesAuthScheme(final HttpContext context) {
    final AuthState authState = getAuthState(context);
    return authState.getAuthScheme() != null;
  }

  private AuthState getAuthState(final HttpContext context) {
    return (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
  }

  private CredentialsProvider getCredentialsProvider(final HttpContext context) {
    return (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
  }
}
