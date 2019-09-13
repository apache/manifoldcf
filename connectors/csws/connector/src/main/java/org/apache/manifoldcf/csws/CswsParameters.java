/* $Id: LiveLinkParameters.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.csws;

/** This class describes live link connection parameters.
*/
public class CswsParameters
{
  public static final String _rcsid = "@(#)$Id: LiveLinkParameters.java 988245 2010-08-23 18:39:35Z kwright $";

  // These parameters are for viewing: constructing a URL the user can use to view the document
  /** View CGI protocol */
  public final static String viewProtocol = "View protocol";
  /** View CGI server name */
  public final static String viewServerName = "View server name";
  /** View CGI port **/
  public final static String viewPort = "View port";
  /** View CGI path (path to use for viewing) */
  public final static String viewCgiPath = "View CGI path";
  /** Document View Action**/
  public final static String viewAction = "View Action";

  // These parameters are for Web Services
  /** Connection options; choices are "http", "https" */
  public final static String serverProtocol = "Server protocol";
  /** Server name */
  public final static String serverName = "Server name";
  /** Server port */
  public final static String serverPort = "Server port";
  /** Server user */
  public final static String serverUsername = "Server user name";
  /** Server password */
  public final static String serverPassword = "Server password";
  /** Authentication service CGI path */
  public final static String authenticationPath = "Server Authentication Service path";
  public final static String authenticationPathDefault = "/cws/Authentication.svc";
  /** ContentService service CGI path */
  public final static String contentServicePath = "Server ContentService Service path";
  public final static String contentServicePathDefault = "/cws/ContentService.svc";
  /** DocumentManagement service CGI path */
  public final static String documentManagementPath = "Server DocumentManagement Service path";
  public final static String documentManagementPathDefault = "/cws/DocumentManagement.svc";
  /** SearchService service CGI path */
  public final static String searchServicePath = "Server SearchService Service path";
  public final static String searchServicePathDefault = "/cws/SearchService.svc";
  /** MemberService service CGI path */
  public final static String memberServicePath = "Server MemberService Service path";
  public final static String memberServicePathDefault = "/cws/MemberService.svc";
  /** Name of the Livelink Collection */
  public final static String dataCollection = "Data Collection";
  public final static String dataCollectionDefault = "'LES Enterprise'";
  /** Server domain, if NTLM */
  public final static String serverHTTPNTLMDomain = "Server HTTP NTLM domain";
  /** Server HTTP user */
  public final static String serverHTTPNTLMUsername = "Server HTTP NTLM user name";
  /** Server password */
  public final static String serverHTTPNTLMPassword = "Server HTTP NTLM password";
  /** Keystore for LAPI */
  public final static String serverHTTPSKeystore = "Server HTTPS truststore";


  // These parameters are for the LiveLink Authority
  /** Cache time in seconds */
  public final static String cacheLifetime = "Cache lifetime minutes";
  /** Max LRU size */
  public final static String cacheLRUSize = "Max cache LRU size";

}
