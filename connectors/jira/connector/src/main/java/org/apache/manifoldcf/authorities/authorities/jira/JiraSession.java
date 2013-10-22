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
import java.util.List;
import java.util.ArrayList;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.conn.params.ConnRoutePNames;
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
    
    // If authentication needed, set that
    if (clientId != null)
    {
      localHttpClient.getCredentialsProvider().setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(clientId,clientSecret));
    }
    
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

        localHttpClient.getCredentialsProvider().setCredentials(
          new AuthScope(proxyHost, proxyPortInt),
          new NTCredentials(proxyUsername, proxyPassword, currentHost, proxyDomain));
      }

      HttpHost proxy = new HttpHost(proxyHost, proxyPortInt);

      localHttpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
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

  private static Object convertToJSON(HttpResponse httpResponse)
    throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null) {
      InputStream is = entity.getContent();
      try {
        String charSet = EntityUtils.getContentCharSet(entity);
        if (charSet == null)
          charSet = "utf-8";
        Reader r = new InputStreamReader(is,charSet);
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
  public boolean checkUserExists(String userName) throws IOException, ResponseException {
    JiraUserQueryResults qr = new JiraUserQueryResults();
    getRest("user/search?username="+URLEncoder.encode(userName,"utf-8")+"&maxResults=1&startAt=0", qr);
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
