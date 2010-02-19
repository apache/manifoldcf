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
package org.apache.lcf.crawler.connectors.livelink;

import org.apache.lcf.crawler.interfaces.*;

/** This class describes live link connection parameters.
*/
public class LiveLinkParameters
{
        public static final String _rcsid = "@(#)$Id$";

        /** Ingestion CGI protocol */
        public final static String ingestProtocol = "Protocol";
        /** Ingestion CGI port **/
        public final static String ingestPort = "Port";
        /** Ingestion CGI path (path to fetch document from for ingestion) */
        public final static String ingestCgiPath = "CGI path";
        /** View CGI protocol */
        public final static String viewProtocol = "View protocol";
        /** View CGI server name */
        public final static String viewServerName = "View server name";
        /** View CGI port **/
        public final static String viewPort = "View port";
        /** View CGI path (path to use for viewing) */
        public final static String viewCgiPath = "View CGI path";
        /** Server name */
        public final static String serverName = "Server name";
        /** Server port */
        public final static String serverPort = "Server port";
        /** Server user */
        public final static String serverUsername = "Server user name";
        /** Server password */
        public final static String serverPassword = "Server password";
        /** NTLM username */
        public final static String ntlmUsername = "NTLM user name";
        /** NTLM password */
        public final static String ntlmPassword = "NTLM password";
        /** NTLM domain (set if NTLM desired) */
        public final static String ntlmDomain = "NTLM domain";
        /** Livelink SSL keystore */
        public final static String livelinkKeystore = "Livelink SSL keystore";
        /** User name mapping description.  This replaces username regexp and username spec below. */
        public final static String userNameMapping = "Livelink user name map";

        // These two are deprecated
        /** Username regexp */
        public final static String userNameRegexp = "User name regexp";
        /** Livelink username spec */
        public final static String livelinkNameSpec = "Livelink user spec";


}
