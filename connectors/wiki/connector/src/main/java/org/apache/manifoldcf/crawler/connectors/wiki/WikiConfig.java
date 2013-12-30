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
package org.apache.manifoldcf.crawler.connectors.wiki;


/** Parameters and output data for Wiki repository connector.
*/
public class WikiConfig
{
  public static final String _rcsid = "@(#)$Id$";

  // Configuration parameters

  /** Protocol */
  public static final String PARAM_PROTOCOL = "Server protocol";
  /** Server name */
  public static final String PARAM_SERVER = "Server name";
  /** Port */
  public static final String PARAM_PORT = "Server port";
  /** Path */
  public static final String PARAM_PATH = "Server path";
  
  /** Email */
  public static final String PARAM_EMAIL = "Email";
  
  // Login info
  public static final String PARAM_LOGIN = "serverlogin";
  public static final String PARAM_PASSWORD = "serverpass";
  public static final String PARAM_DOMAIN = "serverdomain";

  // Access credentials
  public static final String PARAM_ACCESSREALM = "accessrealm";
  public static final String PARAM_ACCESSUSER = "accessuser";
  public static final String PARAM_ACCESSPASSWORD = "accesspassword";
  
  // Proxy info
  public static final String PARAM_PROXYHOST = "Proxy host";
  public static final String PARAM_PROXYPORT = "Proxy port";
  public static final String PARAM_PROXYDOMAIN = "Proxy domain";
  public static final String PARAM_PROXYUSERNAME = "Proxy username";
  public static final String PARAM_PROXYPASSWORD = "Proxy password";

  // Document specification

  /** Namespace and title prefix */
  public static final String NODE_NAMESPACE_TITLE_PREFIX = "namespaceandprefix";
  /** Namespace attribute */
  public static final String ATTR_NAMESPACE = "namespace";
  /** Title prefix attribute */
  public static final String ATTR_TITLEPREFIX = "titleprefix";
}
