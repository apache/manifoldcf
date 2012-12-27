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
package org.apache.manifoldcf.agents.output.solrcloud;


/** Parameters and output data for SOLR output connector.
 */
public class SolrCloudConfig
{
  /** Solr type */
  public static final String PARAM_SOLR_TYPE = "Solr type";



  /*
   * SolrCloud Configurations
   */
  /** ZooKeeper Servers */
  public static final String PARAM_ZOOKEEPER_HOST = "ZooKeeper host";

  /** ZooKeeper client timeout */
  public static final String PARAM_ZOOKEEPER_CLIENT_TIMEOUT = "ZooKeeper client timeout";

  /** ZooKeeper connect timeout */
  public static final String PARAM_ZOOKEEPER_CONNECT_TIMEOUT = "ZooKeeper connect timeout";



  /*
   * Solr Configurations
   */
  /** Protocol */
  public static final String PARAM_PROTOCOL = "Protocol";

  /** Server name */
  public static final String PARAM_HOST = "Host";

  /** Port */
  public static final String PARAM_PORT = "Port";

  /** Context */
  public static final String PARAM_CONTEXT = "Context";



  /** Collection */
  public static final String PARAM_COLLECTION = "Collection";

  /** Update path */
  public static final String PARAM_UPDATEPATH = "Server update handler";

  /** Remove path */
  public static final String PARAM_REMOVEPATH = "Server remove handler";

  /** Status path */
  public static final String PARAM_STATUSPATH = "Server status handler";



  /** HTTP client connection timeout */
  public static final String PARAM_HTTP_CLIENT_CONNECTION_TIMEOUT = "HTTP client connection timeout";

  /** HTTP client socket timeout */
  public static final String PARAM_HTTP_CLIENT_SOCKET_TIMEOUT = "HTTP client socket timeout";



  /** Optional basic auth realm */
  public static final String PARAM_REALM = "Realm";

  /** Optional user ID */
  public static final String PARAM_USERID = "User ID";

  /** Optional user password */
  public static final String PARAM_PASSWORD = "Password";

  /** Keystore */
  public static final String PARAM_KEYSTORE = "Keystore";

  /** Id field */
  public static final String PARAM_UNIQUE_KEY_FIELD = "Unique key field";

  /** Maximum document length */
  public static final String PARAM_MAXLENGTH = "Maximum document length";

  /** Included mime types */
  public static final String PARAM_INCLUDEDMIMETYPES = "Included mime types";

  /** Excluded mime types */
  public static final String PARAM_EXCLUDEDMIMETYPES="Excluded mime types";

  /** Node describing an argument */
  public static final String NODE_ARGUMENT = "argument";

  /** Attribute with the argument name */
  public static final String ATTRIBUTE_NAME = "name";

  /** Attribute with the argument value */
  public static final String ATTRIBUTE_VALUE = "value";

  /** Enable commits */
  public static final String PARAM_COMMITS = "Commits";

  /** Commit within time */
  public static final String PARAM_COMMITWITHIN = "Commit within";

  // Output specification

  /** Node describing a fieldmap */
  public static final String NODE_FIELDMAP = "fieldmap";

  /** Attribute describing a source field name */
  public static final String ATTRIBUTE_SOURCE = "source";

  /** Attribute describing a target field name */
  public static final String ATTRIBUTE_TARGET = "target";

}
