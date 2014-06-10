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
import org.apache.manifoldcf.core.util.URLEncoder;

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
import java.nio.charset.Charset;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;

import org.apache.http.ParseException;

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
  
  private HttpClientConnectionManager connectionManager;
  private HttpClient httpClient;
  
  // Current host name
  private static String currentHost = null;
  static
  {
    // Find the current host name
    try
    {
      java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

      // Get hostname
      currentHost = addr.getHostName();
    }
    catch (java.net.UnknownHostException e)
    {
    }
  }

  /**
   * Constructor. Create a session.
   */
  public JiraSession(String clientId, String clientSecret, String URLbase,
    String proxyHost, String proxyPort, String proxyDomain, String proxyUsername, String proxyPassword)
    throws ManifoldCFException {
    this.URLbase = URLbase;
    this.clientId = clientId;
    this.clientSecret = clientSecret;

    int socketTimeout = 900000;
    int connectionTimeout = 60000;

    javax.net.ssl.SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
    SSLConnectionSocketFactory myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeout),
      SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

    connectionManager = new PoolingHttpClientConnectionManager();

    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

    // If authentication needed, set that
    if (clientId != null)
    {
      credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(clientId,clientSecret));
    }

    RequestConfig.Builder requestBuilder = RequestConfig.custom()
      .setCircularRedirectsAllowed(true)
      .setSocketTimeout(socketTimeout)
      .setStaleConnectionCheckEnabled(true)
      .setExpectContinueEnabled(true)
      .setConnectTimeout(connectionTimeout)
      .setConnectionRequestTimeout(socketTimeout);

    // If there's a proxy, set that too.
    if (proxyHost != null && proxyHost.length() > 0)
    {

      int proxyPortInt;
      if (proxyPort != null && proxyPort.length() > 0)
      {
        try
        {
          proxyPortInt = Integer.parseInt(proxyPort);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Bad number: "+e.getMessage(),e);
        }
      }
      else
        proxyPortInt = 8080;

      // Configure proxy authentication
      if (proxyUsername != null && proxyUsername.length() > 0)
      {
        if (proxyPassword == null)
          proxyPassword = "";
        if (proxyDomain == null)
          proxyDomain = "";

        credentialsProvider.setCredentials(
          new AuthScope(proxyHost, proxyPortInt),
          new NTCredentials(proxyUsername, proxyPassword, currentHost, proxyDomain));
      }

      HttpHost proxy = new HttpHost(proxyHost, proxyPortInt);
      requestBuilder.setProxy(proxy);
    }

    httpClient = HttpClients.custom()
      .setConnectionManager(connectionManager)
      .setMaxConnTotal(1)
      .disableAutomaticRetries()
      .setDefaultRequestConfig(requestBuilder.build())
      .setDefaultSocketConfig(SocketConfig.custom()
        .setTcpNoDelay(true)
        .setSoTimeout(socketTimeout)
        .build())
      .setDefaultCredentialsProvider(credentialsProvider)
      .setSSLSocketFactory(myFactory)
      .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
      .setRedirectStrategy(new DefaultRedirectStrategy())
      .build();

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

  private static Object convertToJSON(HttpResponse httpResponse)
    throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null) {
      InputStream is = entity.getContent();
      try {
        Reader r = new InputStreamReader(is,getCharSet(entity));
        return JSONValue.parse(r);
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
        char[] buffer = new char[65536];
        Reader r = new InputStreamReader(is,getCharSet(entity));
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

  private static Charset getCharSet(HttpEntity entity)
  {
    Charset charSet;
    try
    {
      ContentType ct = ContentType.get(entity);
      if (ct == null)
        charSet = StandardCharsets.UTF_8;
      else
        charSet = ct.getCharset();
    }
    catch (ParseException e)
    {
      charSet = StandardCharsets.UTF_8;
    }
    return charSet;
  }

  private void getRest(String rightside, JiraJSONResponse response)
    throws IOException, ResponseException {

    final HttpRequestBase method = new HttpGet(URLbase + rightside);
    method.addHeader("Accept", "application/json");

    try {
      HttpResponse httpResponse = httpClient.execute(method);
      int resultCode = httpResponse.getStatusLine().getStatusCode();
      if (resultCode != 200)
        throw new ResponseException("Unexpected result code "+resultCode+": "+convertToString(httpResponse));
      Object jo = convertToJSON(httpResponse);
      response.acceptJSONObject(jo);
    } finally {
      method.abort();
    }
  }

  /**
   * Obtain repository information.
   */
  public Map<String, String> getRepositoryInfo() throws IOException, ResponseException {
    HashMap<String, String> statistics = new HashMap<String, String>();
    JiraUserQueryResults qr = new JiraUserQueryResults();
    getRest("user/search?username=&maxResults=1&startAt=0", qr);
    return statistics;
  }

  /** Check if user exists.
  */
  public boolean checkUserExists(String userName) throws IOException, ResponseException, ManifoldCFException {
    JiraUserQueryResults qr = new JiraUserQueryResults();
    getRest("user/search?username="+URLEncoder.encode(userName)+"&maxResults=1&startAt=0", qr);
    List<String> values = new ArrayList<String>();
    qr.getNames(values);
    if (values.size() == 0)
      return false;
    for (String value : values) {
      if (userName.equals(value))
        return true;
    }
    return false;
  }
}
