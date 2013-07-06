/* $Id$ */
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
* http://www.apache.org/licenses/LICENSE-2.0
 * 
* Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.authorities.authorities.jira;

import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.core.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

import java.io.Reader;
import java.io.Writer;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;

import java.util.Map;
import java.util.HashMap;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.params.CoreProtocolPNames;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;

/**
 *
 * @author andrew
 */
public class JiraSession {

  private final String URLbase;
  private final String clientId;
  private final String clientSecret;
  
  private ClientConnectionManager connectionManager;
  private HttpClient httpClient;
  
  /**
   * Constructor. Create a session.
   */
  public JiraSession(String clientId, String clientSecret, String URLbase)
    throws ManifoldCFException {
    this.URLbase = URLbase;
    this.clientId = clientId;
    this.clientSecret = clientSecret;

    int socketTimeout = 900000;
    int connectionTimeout = 60000;

    javax.net.ssl.SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
    SSLSocketFactory myFactory = new SSLSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeout),
      new AllowAllHostnameVerifier());
    Scheme myHttpsProtocol = new Scheme("https", 443, myFactory);

    PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
    localConnectionManager.setMaxTotal(1);
    connectionManager = localConnectionManager;
    // Set up protocol registry
    connectionManager.getSchemeRegistry().register(myHttpsProtocol);

    BasicHttpParams params = new BasicHttpParams();
    params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,true);
    params.setIntParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE,socketTimeout);
    params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
    params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,false);
    params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeout);
    params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,socketTimeout);
    params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS,true);
    DefaultHttpClient localHttpClient = new DefaultHttpClient(connectionManager,params);
    // No retries
    localHttpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler()
      {
        public boolean retryRequest(
          IOException exception,
          int executionCount,
          HttpContext context)
        {
          return false;
        }
       
      });
    localHttpClient.setRedirectStrategy(new DefaultRedirectStrategy());
    if (clientId != null)
    {
      localHttpClient.getCredentialsProvider().setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(clientId,clientSecret));
    }

    httpClient = localHttpClient;
  }

  /**
   * Close session.
   */
  public void close() {
    httpClient = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;
  }

  private static JSONObject convertToJSON(HttpResponse httpResponse)
    throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null) {
      InputStream is = entity.getContent();
      try {
        String charSet = EntityUtils.getContentCharSet(entity);
        if (charSet == null)
          charSet = "utf-8";
        Reader r = new InputStreamReader(is,charSet);
        return (JSONObject)JSONValue.parse(r);
      } finally {
        is.close();
      }
    }
    return null;
  }

  private static String convertToString(HttpResponse httpResponse)
    throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null) {
      InputStream is = entity.getContent();
      try {
        String charSet = EntityUtils.getContentCharSet(entity);
        if (charSet == null)
          charSet = "utf-8";
        char[] buffer = new char[65536];
        Reader r = new InputStreamReader(is,charSet);
        Writer w = new StringWriter();
        try {
          while (true) {
            int amt = r.read(buffer);
            if (amt == -1)
              break;
            w.write(buffer,0,amt);
          }
        } finally {
          w.flush();
        }
        return w.toString();
      } finally {
        is.close();
      }
    }
    return "";
  }

  private void getRest(String rightside, JiraJSONResponse response) throws IOException {

    final HttpRequestBase method = new HttpGet(URLbase + rightside);
    method.addHeader("Accept", "application/json");

    try {
      HttpResponse httpResponse = httpClient.execute(method);
      int resultCode = httpResponse.getStatusLine().getStatusCode();
      if (resultCode != 200)
        throw new IOException("Unexpected result code "+resultCode+": "+convertToString(httpResponse));
      JSONObject jo = convertToJSON(httpResponse);
      response.acceptJSONObject(jo);
    } finally {
      method.abort();
    }
  }

  /**
   * Obtain repository information.
   */
  public Map<String, String> getRepositoryInfo() throws IOException {
    HashMap<String, String> statistics = new HashMap<String, String>();
    /*
    JiraQueryResults qr = new JiraQueryResults();
    getRest("search?maxResults=1&jql=", qr);
    statistics.put("Total Issues", qr.getTotal().toString());
    */
    return statistics;
  }

  /** Check if user exists.
  */
  public boolean checkUserExists(String userName) throws IOException {
    // MHL
    return false;
  }
}
