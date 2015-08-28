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
package org.apache.manifoldcf.authorities.authorities.amazons3;

/**
 * @author Kuhajeyan
 *
 */
public class AmazonS3Config {
  public static final String AWS_ACCESS_KEY = "aws_access_key";

  public static final String AWS_SECRET_KEY = "aws_secret_key";

  public static final String AMAZONS3_HOST = "amazons3_host";

  public static final String AMAZONS3_PORT = "amazons3_port";

  public static final String AMAZONS3_PROTOCOL = "amazons3_protocol";

  public static final String AMAZONS3_PROXY_HOST = "amazons3_proxy_host";

  public static final String AMAZONS3_PROXY_PORT = "amazons3_proxy_port";

  public static final String AMAZONS3_PROXY_DOMAIN = "amazons3_proxy_domain";

  public static final String AMAZONS3_PROXY_USERNAME = "amazons3_proxy_username";

  public static final String AMAZONS3_PROXY_PASSWORD = "amazons3_proxy_password";

  public static final String AMAZONS3_HOST_DEFAULT = "";

  public static final String AMAZONS3_PORT_DEFAULT = "";

  public static final String AMAZONS3_PROTOCOL_DEFAULT = "http";

  public static final String AMAZONS3_AWS_ACCESS_KEY_DEFAULT = "";

  public static final String AMAZONS3_AWS_SECRET_KEY_DEFAULT = "";

  public static final String AMAZONS3_PROXY_HOST_DEFAULT = "";

  public static final String AMAZONS3_PROXY_PORT_DEFAULT = "";

  public static final String AMAZONS3_PROXY_DOMAIN_DEFAULT = "";

  public static final String AMAZONS3_PROXY_USERNAME_DEFAULT = "";

  public static final String AMAZONS3_PROXY_PASSWORD_DEFAULT = "";

  public static final String AMAZONS3_BUCKETS_DEFAULT = "";

  // Configuration tabs
  public static final String AMAZONS3_SERVER_TAB_PROPERTY = "Amazons3AuthorityConnector.Server";

  public static final String AMAZONS3_PROXY_TAB_PROPERTY = "Amazons3AuthorityConnector.Proxy";

  // Specification tabs
  public static final String AMAZONS3_BUCKETS_TAB_PROPERTY = "Amazons3AuthorityConnector.Amazons3Buckets";

  public static final String AMAZONS3_SECURITY_TAB_PROPERTY = "Amazons3AuthorityConnector.Amazons3Security";

  // Template names for configuration
  /**
   * Forward to the javascript to check the configuration parameters
   */
  public static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration_amazons3.js";

  public static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_amazons3_server.html";

  public static final String EDIT_CONFIG_FORWARD_PROXY = "editConfiguration_amazons3_proxy.html";

  public static final String VIEW_CONFIG_FORWARD = "viewConfiguration_amazons3.html";

  
  
  
  
  
  //////
  public static final int CHARACTER_LIMIT = 1000000;

  public static final String DOCUMENT_URI_FORMAT = "%s.s3.amazonaws.com/%s";

  

  public static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";

  public static final String JOB_BUCKETS_ATTRIBUTE = "s3buckets";
}
