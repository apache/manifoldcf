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

  /** Username */
  public static final String USERNAME_PARAM = "username";
 
  /** Password */
  public static final String PASSWORD_PARAM = "password";
  
  /** Protocol */
  public static final String PROTOCOL_PARAM = "protocol";
  
  /** Server name */
  public static final String SERVER_PARAM = "server";
  
  /** Port */
  public static final String PORT_PARAM = "port";
  
  /** Path of the context of the Alfresco Web Services API */
  public static final String PATH_PARAM = "path";
  
  /** The Lucene Query parameter */
  public static final String LUCENE_QUERY_PARAM = "luceneQuery";
  
  //default values
  public static final String USERNAME_DEFAULT_VALUE = "admin";
  public static final String PASSWORD_DEFAULT_VALUE = "admin";
  public static final String PROTOCOL_DEFAULT_VALUE = "http";
  public static final String SERVER_DEFAULT_VALUE = "localhost";
  public static final String PORT_DEFAULT_VALUE = "8080";
  public static final String PATH_DEFAULT_VALUE = "/alfresco/api";
  
}
