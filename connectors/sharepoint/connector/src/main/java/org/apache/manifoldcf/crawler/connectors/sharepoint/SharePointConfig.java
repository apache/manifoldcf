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
package org.apache.manifoldcf.crawler.connectors.sharepoint;


/** Parameters and output data for SharePoint repository.
*/
public class SharePointConfig
{
  public static final String _rcsid = "@(#)$Id$";

  // Configuration parameters

  /** SharePoint server version */
  public static final String PARAM_SERVERVERSION = "serverVersion";
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
  
  /** Authority type */
  public static final String PARAM_AUTHORITYTYPE = "authorityType";
}
