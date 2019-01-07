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
package org.apache.manifoldcf.livelink;

/** This class describes live link connection parameters.
*/
public class LiveLinkParameters
{
  public static final String _rcsid = "@(#)$Id: LiveLinkParameters.java 988245 2010-08-23 18:39:35Z kwright $";

  // These parameters are for ingestion: picking up a document after we discover it through LAPI
  /** Ingestion CGI protocol */
  public final static String ingestProtocol = "Protocol";
  /** Ingestion CGI port **/
  public final static String ingestPort = "Port";
  /** Ingestion CGI path (path to fetch document from for ingestion) */
  public final static String ingestCgiPath = "CGI path";
  /** NTLM username */
  public final static String ingestNtlmUsername = "NTLM user name";
  /** NTLM password */
  public final static String ingestNtlmPassword = "NTLM password";
  /** NTLM domain (set if NTLM desired) */
  public final static String ingestNtlmDomain = "NTLM domain";
  /** Livelink SSL keystore */
  public final static String ingestKeystore = "Livelink SSL keystore";
  
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
  
  // These parameters are for LAPI
  /** Connection options; choices are "internal", "http", "https" */
  public final static String serverProtocol = "Server protocol";
  /** Server name */
  public final static String serverName = "Server name";
  /** Server port */
  public final static String serverPort = "Server port";
  /** Server user */
  public final static String serverUsername = "Server user name";
  /** Server password */
  public final static String serverPassword = "Server password";
  /** Server CGI path (path to use for viewing) */
  public final static String serverHTTPCgiPath = "Server HTTP CGI path";
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
