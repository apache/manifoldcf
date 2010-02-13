<%@ include file="../../adminDefaults.jsp" %>

<%

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
%>

<%
	// This file is included by every place that edited specification information for the livelink connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String serverName = variableContext.getParameter("servername");
	if (serverName != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverName,serverName);
	String serverPort = variableContext.getParameter("serverport");
	if (serverPort != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPort,serverPort);
	String serverUserName = variableContext.getParameter("serverusername");
	if (serverUserName != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverUsername,serverUserName);
	String serverPassword = variableContext.getParameter("serverpassword");
	if (serverPassword != null)
		parameters.setObfuscatedParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPassword,serverPassword);
	String usernameRegexp = variableContext.getParameter("usernameregexp");
	String livelinkUserExpr = variableContext.getParameter("livelinkuserexpr");
	if (usernameRegexp != null && livelinkUserExpr != null)
	{
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.userNameRegexp,null);
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkNameSpec,null);

		com.metacarta.crawler.connectors.livelink.MatchMap matchMap = new com.metacarta.crawler.connectors.livelink.MatchMap();
		matchMap.appendMatchPair(usernameRegexp,livelinkUserExpr);
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.userNameMapping,matchMap.toString());
	}
%>
