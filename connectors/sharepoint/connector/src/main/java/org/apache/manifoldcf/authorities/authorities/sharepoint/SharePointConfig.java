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
package org.apache.manifoldcf.authorities.authorities.sharepoint;


/** Parameters and output data for SharePoint authority.
*/
public class SharePointConfig
{
  public static final String _rcsid = "@(#)$Id$";

  // Configuration parameters

  /** Cache lifetime */
  public static final String PARAM_CACHELIFETIME = "Cache lifetime";
  /** Cache LRU size */
  public static final String PARAM_CACHELRUSIZE = "Cache LRU size";

  /** SharePoint server version */
  public static final String PARAM_SERVERVERSION = "serverVersion";
  /** Claim space enabled? */
  public static final String PARAM_SERVERCLAIMSPACE = "serverClaimSpace";
  /** SharePoint server protocol */
  public static final String PARAM_SERVERPROTOCOL = "serverProtocol";
  /** SharePoint server name */
  public static final String PARAM_SERVERNAME = "serverName";
  /** SharePoint server port */
  public static final String PARAM_SERVERPORT = "serverPort";
  /** SharePoint server location */
  public static final String PARAM_SERVERLOCATION = "serverLocation";
  /** SharePoint server user name */
  public static final String PARAM_SERVERUSERNAME = "userName";
  /** SharePoint server password */
  public static final String PARAM_SERVERPASSWORD = "password";
  /** SharePoint server certificate store */
  public static final String PARAM_SERVERKEYSTORE = "keystore";
  /** Proxy host */
  public static final String PARAM_PROXYHOST = "proxyHost";
  /** Proxy port */
  public static final String PARAM_PROXYPORT = "proxyPort";
  /** Proxy user name */
  public static final String PARAM_PROXYUSER = "proxyUser";
  /** Proxy password */
  public static final String PARAM_PROXYPASSWORD = "proxyPassword";
  /** Proxy authentication domain */
  public static final String PARAM_PROXYDOMAIN = "proxyDomain";

  // Nodes
  
  /** Domain controller node */
  public static final String NODE_DOMAINCONTROLLER = "domaincontroller";
  
  // Attributes
  
  /** Domain suffix */
  public static final String ATTR_SUFFIX = "suffix";
  /** DC server name */
  public static final String ATTR_DOMAINCONTROLLER = "domaincontroller";
  /** DC user name */
  public static final String ATTR_USERNAME = "username";
  /** DC password */
  public static final String ATTR_PASSWORD = "password";
  /** DC authentication method */
  public static final String ATTR_AUTHENTICATION = "authentication";
  /** DC user acls username attribute name */
  public static final String ATTR_USERACLsUSERNAME = "useraclsusername";

}
