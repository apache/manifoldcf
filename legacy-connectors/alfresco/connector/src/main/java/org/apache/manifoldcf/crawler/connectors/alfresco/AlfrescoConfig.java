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
package org.apache.manifoldcf.crawler.connectors.alfresco;

/** 
 * Parameters data for the Alfresco repository connector.
*/
public class AlfrescoConfig {

  /** Username parameter */
  public static final String USERNAME_PARAM = "username";
 
  /** Password parameter */
  public static final String PASSWORD_PARAM = "password";
  
  /** Protocol parameter */
  public static final String PROTOCOL_PARAM = "protocol";
  
  /** Server name parameter */
  public static final String SERVER_PARAM = "server";
  
  /** Port parameter */
  public static final String PORT_PARAM = "port";
  
  /** Parameter for the path of the context of the Alfresco Web Services API */
  public static final String PATH_PARAM = "path";
  
  /** The Lucene Query parameter */
  public static final String LUCENE_QUERY_PARAM = "luceneQuery";
  
  /** Tenant domain parameter (optional) */
  public static final String TENANT_DOMAIN_PARAM = "tenantDomain";
  
  /** Separator for the username field dedicated to the tenant domain */
  public static final String TENANT_DOMAIN_SEP = "@";
  
  /** Socket Timeout parameter for the Alfresco Web Service Client */
  public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
  
  //default values
  public static final String USERNAME_DEFAULT_VALUE = "admin";
  public static final String PASSWORD_DEFAULT_VALUE = "admin";
  public static final String PROTOCOL_DEFAULT_VALUE = "http";
  public static final String SERVER_DEFAULT_VALUE = "localhost";
  public static final String PORT_DEFAULT_VALUE = "8080";
  public static final String PATH_DEFAULT_VALUE = "/alfresco/api";
  public static final int SOCKET_TIMEOUT_DEFAULT_VALUE = 120000;
  
}
