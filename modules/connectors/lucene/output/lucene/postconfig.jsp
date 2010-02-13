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
	// This file is included by every place that edited specification information for the file system connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.


	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String protocol = variableContext.getParameter("serverprotocol");
	if (protocol != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PROTOCOL,protocol);
		
	String server = variableContext.getParameter("servername");
	if (server != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_SERVER,server);

	String port = variableContext.getParameter("serverport");
	if (port != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PORT,port);

	String webapp = variableContext.getParameter("webappname");
	if (webapp != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_WEBAPPNAME,webapp);

	String updatePath = variableContext.getParameter("updatepath");
	if (updatePath != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_UPDATEPATH,updatePath);

	String removePath = variableContext.getParameter("removepath");
	if (removePath != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_REMOVEPATH,removePath);

	String statusPath = variableContext.getParameter("statuspath");
	if (statusPath != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_STATUSPATH,statusPath);

	String realm = variableContext.getParameter("realm");
	if (realm != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_REALM,realm);

	String userID = variableContext.getParameter("userid");
	if (userID != null)
		parameters.setParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_USERID,userID);
		
	String password = variableContext.getParameter("password");
	if (password != null)
		parameters.setObfuscatedParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PASSWORD,password);
	
%>
