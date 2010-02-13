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
	// This file is included by every place that the configuration information for the livelink connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String userID = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_USERID);
	if (userID == null)
		userID = "";
	String password = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_PASSWORD);
	if (password == null)
		password = "";
	String filenetdomain = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN);
	if (filenetdomain == null)
	{
		filenetdomain = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_FILENETDOMAIN_OLD);
		if (filenetdomain == null)
			filenetdomain = "";
	}
	String objectstore = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_OBJECTSTORE);
	if (objectstore == null)
		objectstore = "";
	String serverprotocol = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPROTOCOL);
	if (serverprotocol == null)
		serverprotocol = "http";
	String serverhostname = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERHOSTNAME);
	if (serverhostname == null)
		serverhostname = "";
	String serverport = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERPORT);
	if (serverport == null)
		serverport = "";
	String serverwsilocation = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_SERVERWSILOCATION);
	if (serverwsilocation == null)
		serverwsilocation = "wsi/FNCEWS40DIME";
	String urlprotocol = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPROTOCOL);
	if (urlprotocol == null)
		urlprotocol = "http";
	String urlhostname = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLHOSTNAME);
	if (urlhostname == null)
		urlhostname = "";
	String urlport = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLPORT);
	if (urlport == null)
		urlport = "";
	String urllocation = parameters.getParameter(com.metacarta.crawler.connectors.filenet.FilenetConnector.CONFIG_PARAM_URLLOCATION);
	if (urllocation == null)
		urllocation = "Workplace/Browse.jsp";

	// "Server" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Server protocol:</nobr></td>
		<td class="value">
			<select name="serverprotocol" size="2">
				<option value="http" <%=(serverprotocol.equals("http")?"selected=\"true\"":"")%>>http</option>
				<option value="https" <%=(serverprotocol.equals("https")?"selected=\"true\"":"")%>>https</option>
			</select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Server host name:</nobr></td><td class="value"><input type="text" size="32" name="serverhostname" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverhostname)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server port:</nobr></td><td class="value"><input type="text" size="5" name="serverport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverport)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server web service location:</nobr></td><td class="value"><input type="text" size="32" name="serverwsilocation" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverwsilocation)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Server tab
%>
<input type="hidden" name="serverprotocol" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverprotocol)%>'/>
<input type="hidden" name="serverhostname" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverhostname)%>'/>
<input type="hidden" name="serverport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverport)%>'/>
<input type="hidden" name="serverwsilocation" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverwsilocation)%>'/>
<%
	}

	// "Document URL" tab
	if (tabName.equals("Document URL"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Document URL protocol:</nobr></td>
		<td class="value">
			<select name="urlprotocol" size="2">
				<option value="http" <%=(serverprotocol.equals("http")?"selected=\"true\"":"")%>>http</option>
				<option value="https" <%=(serverprotocol.equals("https")?"selected=\"true\"":"")%>>https</option>
			</select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document URL host name:</nobr></td><td class="value"><input type="text" size="32" name="urlhostname" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urlhostname)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document URL port:</nobr></td><td class="value"><input type="text" size="5" name="urlport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urlport)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document URL location:</nobr></td><td class="value"><input type="text" size="32" name="urllocation" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urllocation)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Document URL tab
%>
<input type="hidden" name="urlprotocol" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urlprotocol)%>'/>
<input type="hidden" name="urlhostname" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urlhostname)%>'/>
<input type="hidden" name="urlport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urlport)%>'/>
<input type="hidden" name="urllocation" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(urllocation)%>'/>
<%
	}

	// "Object Store" tab
	if (tabName.equals("Object Store"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>FileNet domain name:</nobr></td><td class="value"><input type="text" size="32" name="filenetdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(filenetdomain)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Object store name:</nobr></td><td class="value"><input type="text" size="32" name="objectstore" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(objectstore)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Object Store tab
%>
<input type="hidden" name="filenetdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(filenetdomain)%>'/>
<input type="hidden" name="objectstore" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(objectstore)%>'/>
<%
	}


	// "Credentials" tab
	if (tabName.equals("Credentials"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>User ID:</nobr></td><td class="value"><input type="text" size="32" name="userid" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(userID)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Password:</nobr></td><td class="value"><input type="password" size="32" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Credentials tab
%>
<input type="hidden" name="userid" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(userID)%>'/>
<input type="hidden" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}

%>