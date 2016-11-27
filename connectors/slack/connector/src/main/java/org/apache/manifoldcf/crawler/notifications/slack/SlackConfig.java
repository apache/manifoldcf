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


/**
* Parameters data for the Slack notification connector.
*/
public class SlackConfig {

  /**
  * Slack WebHook URL
  */
  public static final String WEBHOOK_URL_PARAM = "webHookUrl";

  /**
   * Proxy Host
   */
  public static final String PROXY_HOST_PARAM = "proxyHost";

  /**
   * Proxy Port
   */

  public static final String PROXY_PORT_PARAM = "proxyPort";

  /**
   * Proxy Username
   */
  public static final String PROXY_USERNAME_PARAM = "proxyUsername";

  /**
   * Proxy Password
   */
  public static final String PROXY_PASSWORD_PARAM = "proxyPassword";

  /**
   * Proxy Domain
   */
  public static final String PROXY_DOMAIN_PARAM = "proxyDomain";

  /**
  * URL template
  */
  public static final String URL_PARAM = "url";

  // Specification nodes
  public static final String NODE_FINISHED = "finished";
  public static final String NODE_ERRORABORTED = "erroraborted";
  public static final String NODE_MANUALABORTED = "manualaborted";
  public static final String NODE_MANUALPAUSED = "manualpaused";
  public static final String NODE_SCHEDULEPAUSED = "schedulepaused";
  public static final String NODE_RESTARTED = "restarted";

  public static final String NODE_CHANNEL = "channel";
  public static final String NODE_MESSAGE = "message";

  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_VALUE = "value";


}