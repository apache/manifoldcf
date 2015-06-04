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

package org.apache.manifoldcf.crawler.notifications.email;


/**
* Parameters data for the Email notification connector.
*/
public class EmailConfig {

  /**
  * Username
  */
  public static final String USERNAME_PARAM = "username";

  /**
  * Password
  */
  public static final String PASSWORD_PARAM = "password";

  /**
  * Protocol
  */
  public static final String PROTOCOL_PARAM = "protocol";

  /**
  * Server name
  */
  public static final String SERVER_PARAM = "server";

  /**
  * Port
  */
  public static final String PORT_PARAM = "port";

  /**
  * URL template
  */
  public static final String URL_PARAM = "url";
  
  // Protocol options
  
  public static final String PROTOCOL_IMAP = "IMAP";
  public static final String PROTOCOL_IMAPS = "IMAP-SSL";
  public static final String PROTOCOL_POP3 = "POP3";
  public static final String PROTOCOL_POP3S = "POP3-SSL";
  
  // Protocol providers
  
  public static final String PROTOCOL_IMAP_PROVIDER = "imap";
  public static final String PROTOCOL_IMAPS_PROVIDER = "imaps";
  public static final String PROTOCOL_POP3_PROVIDER = "pop3";
  public static final String PROTOCOL_POP3S_PROVIDER = "pop3s";
  
  // Default values and various other constants
  
  public static final String PROTOCOL_DEFAULT_VALUE = "IMAP";
  public static final String PORT_DEFAULT_VALUE = "";

  // Specification nodes
  
  public static final String NODE_FINISHED = "finished";
  public static final String NODE_ERRORABORTED = "erroraborted";
  public static final String NODE_MANUALABORTED = "manualaborted";
  public static final String NODE_MANUALPAUSED = "manualpaused";
  public static final String NODE_SCHEDULEPAUSED = "schedulepaused";
  public static final String NODE_RESTARTED = "restarted";
  
  public static final String NODE_TO = "to";
  public static final String NODE_FROM = "from";
  public static final String NODE_SUBJECT = "subject";
  public static final String NODE_BODY = "body";
  public static final String NODE_PROPERTIES = "properties";
  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_VALUE = "value";


}