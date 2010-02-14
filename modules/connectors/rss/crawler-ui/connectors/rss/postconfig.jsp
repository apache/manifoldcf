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
	// This file is included by every place that edited specification information for the rss connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String email = variableContext.getParameter("email");
	if (email != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.emailParameter,email);
	String robotsUsage = variableContext.getParameter("robotsusage");
	if (robotsUsage != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.robotsUsageParameter,robotsUsage);
	String bandwidth = variableContext.getParameter("bandwidth");
	if (bandwidth != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.bandwidthParameter,bandwidth);
	String connections = variableContext.getParameter("connections");
	if (connections != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.maxOpenParameter,connections);
	String fetches = variableContext.getParameter("fetches");
	if (fetches != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.maxFetchesParameter,fetches);
	String throttleGroup = variableContext.getParameter("throttlegroup");
	if (throttleGroup != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.throttleGroupParameter,throttleGroup);
	String proxyHost = variableContext.getParameter("proxyhost");
	if (proxyHost != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyHostParameter,proxyHost);
	String proxyPort = variableContext.getParameter("proxyport");
	if (proxyPort != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyPortParameter,proxyPort);
	String proxyAuthDomain = variableContext.getParameter("proxyauthdomain");
	if (proxyAuthDomain != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthDomainParameter,proxyAuthDomain);
	String proxyAuthUsername = variableContext.getParameter("proxyauthusername");
	if (proxyAuthUsername != null)
		parameters.setParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthUsernameParameter,proxyAuthUsername);
	String proxyAuthPassword = variableContext.getParameter("proxyauthpassword");
	if (proxyAuthPassword != null)
		parameters.setObfuscatedParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthPasswordParameter,proxyAuthPassword);


%>
