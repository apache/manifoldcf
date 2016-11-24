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

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/** This class represents a slack web hook session, without any protection
* from threads waiting on sockets, etc.
*/
public class SlackSession
{
  private ObjectMapper objectMapper;
  private final String webHookUrl;

  /** Create a session */
  public SlackSession(final String webHookUrl)
  {
    this.webHookUrl = webHookUrl;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.setSerializationInclusion(Include.NON_NULL);
  }

  public void checkConnection()
  {
    // Need something here...
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

    int connectionTimeout = 60000;
    int socketTimeout = 900000;

    RequestConfig requestConfig = RequestConfig.custom()
        .setSocketTimeout(socketTimeout)
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(socketTimeout)
        .build();

    messagePost.setEntity(entity);
    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(requestConfig)
        .build()) {
      httpClient.execute(messagePost);
    }
  }

  public void close()
  {
  }
}