/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.manifoldcf.jettyrunner;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.ParseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.HttpStatus;
import org.apache.http.HttpException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;

 /* Shutdown jetty by posting the shutdown token
 * 
 */
public class ManifoldCFJettyShutdown
{

  public static final String _rcsid = "@(#)$Id$";

  protected final String jettyBaseURL;
  
  public ManifoldCFJettyShutdown(String jettyBaseURL)
  {
    this.jettyBaseURL = jettyBaseURL;
  }

  public void shutdownJetty()
    throws Exception
  {
    // Pick up shutdown token
    String shutdownToken = System.getProperty("org.apache.manifoldcf.jettyshutdowntoken");
    if (shutdownToken != null)
    {
      int socketTimeout = 900000;
      int connectionTimeout = 300000;

      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(60000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
        .setTcpNoDelay(true)
        .setSoTimeout(socketTimeout)
        .build());
      HttpClientConnectionManager connectionManager = poolingConnectionManager;
        
      RequestConfig.Builder requestBuilder = RequestConfig.custom()
        .setCircularRedirectsAllowed(true)
        .setSocketTimeout(socketTimeout)
        .setExpectContinueEnabled(true)
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(socketTimeout);

      HttpClient httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .setRedirectStrategy(new DefaultRedirectStrategy())
        .build();

      HttpPost method = new HttpPost(jettyBaseURL+"/shutdown?token="+URLEncoder.encode(shutdownToken,"UTF-8"));
      method.setEntity(new StringEntity("",ContentType.create("text/plain", StandardCharsets.UTF_8)));
      try
      {
        HttpResponse httpResponse = httpClient.execute(method);
        int resultCode = httpResponse.getStatusLine().getStatusCode();
        if (resultCode != 200)
          throw new Exception("Received result code "+resultCode+" from POST");
      }
      catch (org.apache.http.NoHttpResponseException e)
      {
        // This is ok and expected
      }
    }
    else
    {
      throw new Exception("No jetty shutdown token specified");
    }
  }
  
  /**
   * A main class that sends a shutdown token to Jetty
   */
  public static void main( String[] args )
  {
    if (args.length != 0 && args.length != 1)
    {
      System.err.println("Usage: ManifoldCFJettyShutdown [<jetty_base_url>]");
      System.exit(1);
    }
    
    String jettyURL;
    if (args.length > 0)
      jettyURL = args[0];
    else
      jettyURL = "http://localhost:8345";
    
    try
    {
      ManifoldCFJettyShutdown js = new ManifoldCFJettyShutdown(jettyURL);
      js.shutdownJetty();
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
}


