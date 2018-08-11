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
package org.apache.manifoldcf.crawler.notifications.slack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.connectorcommon.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.Logging;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/** This class represents a slack web hook session, without any protection
* from threads waiting on sockets, etc.
*/
public class SlackSession
{

  private static String currentHost = null;

  private CloseableHttpClient httpClient;
  private ObjectMapper objectMapper;
  private final String webHookUrl;

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
   * Create a session.
   * @param webHookUrl - the webHookUrl to use for slack messages.
   * @param proxySettingsOrNull - the proxy settings or null if not necessary.
   * @throws ManifoldCFException
   */
  public SlackSession(final String webHookUrl, final ProxySettings proxySettingsOrNull) throws ManifoldCFException
  {
    this.webHookUrl = webHookUrl;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.setSerializationInclusion(Include.NON_NULL);

    int connectionTimeout = 60000;
    int socketTimeout = 900000;

    final RequestConfig.Builder requestBuilder = RequestConfig.custom()
        .setSocketTimeout(socketTimeout)
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(socketTimeout);

    if(proxySettingsOrNull != null) {
      addProxySettings(requestBuilder, proxySettingsOrNull);
    }

    // Create a ssl socket factory trusting everything.
    // Reason: manifoldcf wishes connectors to encapsulate certificate handling
    //         per connection and not rely on the global keystore.
    //         A configurable keystore seems overkill for the slack notification use case
    //         so we trust everything.
    SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
    SSLConnectionSocketFactory myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeout),
        NoopHostnameVerifier.INSTANCE);

    httpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(requestBuilder.build())
        .setSSLSocketFactory(myFactory)
        .build();
  }

  private void addProxySettings(RequestConfig.Builder requestBuilder, ProxySettings proxySettingsOrNull)
  {
    if (proxySettingsOrNull.hasUsername()) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          new AuthScope(proxySettingsOrNull.getHost(), proxySettingsOrNull.getPort()),
          new NTCredentials(proxySettingsOrNull.getUsername(),
              (proxySettingsOrNull.getPassword() == null) ? "" : proxySettingsOrNull.getPassword(),
                  currentHost,
                  (proxySettingsOrNull.getDomain() == null) ? "" : proxySettingsOrNull.getDomain()));
    }

    HttpHost proxy = new HttpHost(proxySettingsOrNull.getHost(), proxySettingsOrNull.getPort());
    requestBuilder.setProxy(proxy);
  }

  public void checkConnection() throws IOException
  {
    HttpPost postRequest = new HttpPost(webHookUrl);
    int statusCode;
    String responseBody = null;

    try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
      responseBody = EntityUtils.toString(response.getEntity());
      statusCode = response.getStatusLine().getStatusCode();
    }

    // the API responds with error 400 and payload "invalid_payload"
    // when called without proper payload. We use this
    // as a connection check, since there is no specific method to
    // check if there is a working webhook endpoint.
    boolean isExpectedStatus = statusCode == HttpStatus.SC_BAD_REQUEST;
    boolean isExpectedPayload = "invalid_payload".equals(responseBody);
    boolean isConnectionOk = isExpectedStatus && isExpectedPayload;
    if (!isConnectionOk) {
      String statusInfo = isExpectedStatus ? "ok" : statusCode + " is unexpected";
      String payloadInfo = isExpectedPayload ? "ok" : "is unexpected";
      String message = "connection failed: status "+statusInfo+", payload "+payloadInfo;
      throw new ClientProtocolException(message);
    }
  }

  public void send(String channel, String message) throws IOException
  {
    HttpPost messagePost = new HttpPost(webHookUrl);

    SlackMessage slackMessage = new SlackMessage();
    if (StringUtils.isNotBlank(channel)) {
      slackMessage.setChannel(channel);
    }
    slackMessage.setText(message);

    String json = objectMapper.writeValueAsString(slackMessage);

    HttpEntity entity = EntityBuilder.create()
        .setContentType(ContentType.APPLICATION_JSON)
        .setContentEncoding(StandardCharsets.UTF_8.name())
        .setText(json)
        .build();

    messagePost.setEntity(entity);
    try (CloseableHttpResponse response = httpClient.execute(messagePost)) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
        EntityUtils.consume(response.getEntity());
      } else {
        Logging.connectors.error("Sending slack message failed with statusline " + response.getStatusLine());
        Logging.connectors.debug("  Response was: " + EntityUtils.toString(response.getEntity()));
      }
    }
  }

  public void close() throws IOException
  {
    httpClient.close();
    httpClient = null;
    objectMapper = null;
  }

  protected static final class ProxySettings {
    private String host;
    private int port = -1;
    private String username;
    private String password;
    private String domain;

    public ProxySettings(String host, String portString, String username, String password, String domain) {
      this.host = host;
      if(StringUtils.isNotEmpty(portString)) {
        try {
          this.port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
          Logging.connectors.warn("Proxy port must be an number. Found " + portString);
        }
      }
      this.username = username;
      this.password = password;
      this.domain = domain;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
    }

    public String getUsername() {
      return username;
    }

    public boolean hasUsername() {
      return StringUtils.isNotEmpty(this.username);
    }

    public String getPassword() {
      return password;
    }

    public String getDomain() {
      return domain;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("ProxySettings{");
      sb.append("host='").append(host).append('\'');
      sb.append(", port=").append(port);
      sb.append(", username='").append(username).append('\'');
      sb.append(", password='").append(password).append('\'');
      sb.append(", domain='").append(domain).append('\'');
      sb.append('}');
      return sb.toString();
    }
  }
}