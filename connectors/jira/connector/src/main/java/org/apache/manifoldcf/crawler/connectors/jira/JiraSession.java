/* $Id: JiraSession.java 1490586 2013-06-07 11:14:52Z kwright $ */
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
package org.apache.manifoldcf.crawler.connectors.jira;

import org.apache.manifoldcf.core.common.*;

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
  
  private final static String apiPost = "/rest/api/2/";

  /**
   * Constructor. Create a session.
   */
  public JiraSession(String clientId, String clientSecret, String URLbase) {
    this.URLbase = URLbase;
    this.clientId = clientId;
    this.clientSecret = clientSecret;

    PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
    localConnectionManager.setMaxTotal(1);
    connectionManager = localConnectionManager;

    BasicHttpParams params = new BasicHttpParams();
    params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
    params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,false);
    params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,60000);
    params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,900000);
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

  private JSONObject getRest(String rightside) throws IOException {

    final HttpRequestBase method = new HttpGet(URLbase + apiPost + rightside);
    method.addHeader("Accept", "application/json");

    try {
      HttpResponse httpResponse = httpClient.execute(method);
      int resultCode = httpResponse.getStatusLine().getStatusCode();
      if (resultCode != 200)
        throw new IOException("Unexpected result code "+resultCode+": "+convertToString(httpResponse));
      return convertToJSON(httpResponse);
    } finally {
      method.abort();
    }
  }

  /**
   * Obtain repository information.
   */
  public Map<String, String> getRepositoryInfo() throws IOException {
    HashMap<String, String> statistics = new HashMap<String, String>();
    JSONObject jsonobj = getRest("search?maxResults=1&jql=");
    statistics.put("Total Issues", jsonobj.get("total") + "");
    return statistics;
  }

  /**
   * Get the list of matching root documents, e.g. seeds.
   */
  public void getSeeds(XThreadStringBuffer idBuffer, String jiraDriveQuery)
      throws IOException, InterruptedException {
    JSONObject jsonobj;
    int startAt = 0;
    int setSize = 100;
    Long total = 0l;
    do {
      jsonobj = getRest("search?maxResults=" + setSize + "&startAt=" + startAt + "&jql=" + URLEncoder.encode(jiraDriveQuery, "ISO-8859-1"));
      total = (Long) (jsonobj.get("total"));
      JSONArray issues = (JSONArray) jsonobj.get("issues");
      for (Object issuei : issues) {
        JSONObject issue = (JSONObject) issuei;
        idBuffer.add(issue.get("id") + "");
      }
      startAt += setSize;
    } while (startAt < total); //results in a little overlap
  }

  /**
   * Get an individual document.
   */
  public JSONObject getObject(String id) throws IOException {
    return getRest("issue/" + id);
  }


}
