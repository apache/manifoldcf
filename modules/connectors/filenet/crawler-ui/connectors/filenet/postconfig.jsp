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

	String serverprotocol = variableContext.getParameter("serverprotocol");
	if (serverprotocol != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPROTOCOL,serverprotocol);

	String serverhostname = variableContext.getParameter("serverhostname");
	if (serverhostname != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERHOSTNAME,serverhostname);

	String serverport = variableContext.getParameter("serverport");
	if (serverport != null && serverport.length() > 0)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPORT,serverport);

	String serverwsilocation = variableContext.getParameter("serverwsilocation");
	if (serverwsilocation != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERWSILOCATION,serverwsilocation);

	String urlprotocol = variableContext.getParameter("urlprotocol");
	if (urlprotocol != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPROTOCOL,urlprotocol);

	String urlhostname = variableContext.getParameter("urlhostname");
	if (urlhostname != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLHOSTNAME,urlhostname);

	String urlport = variableContext.getParameter("urlport");
	if (urlport != null && urlport.length() > 0)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPORT,urlport);

	String urllocation = variableContext.getParameter("urllocation");
	if (urllocation != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLLOCATION,urllocation);

	String userID = variableContext.getParameter("userid");
	if (userID != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_USERID,userID);

	String password = variableContext.getParameter("password");
	if (password != null)
		parameters.setObfuscatedParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_PASSWORD,password);

	String filenetdomain = variableContext.getParameter("filenetdomain");
	if (filenetdomain != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN,filenetdomain);

	String objectstore = variableContext.getParameter("objectstore");
	if (objectstore != null)
		parameters.setParameter(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_OBJECTSTORE,objectstore);

%>
