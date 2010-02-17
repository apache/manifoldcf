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

	String serverName = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.serverName);
	if (serverName == null)
		serverName = "localhost";
	String serverPort = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.serverPort);
	if (serverPort == null)
		serverPort = "2099";
	String serverUserName = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.serverUsername);
	if (serverUserName == null)
		serverUserName = "";
	String serverPassword = parameters.getObfuscatedParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.serverPassword);
	if (serverPassword == null)
		serverPassword = "";
	org.apache.lcf.crawler.connectors.livelink.MatchMap matchMap = null;
	String usernameRegexp = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.userNameRegexp);
	String livelinkUserExpr = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.livelinkNameSpec);
	if (usernameRegexp != null && usernameRegexp.length() > 0 && livelinkUserExpr != null)
	{
		// Old-style configuration.  Convert to the new.
		matchMap = new org.apache.lcf.crawler.connectors.livelink.MatchMap();
		matchMap.appendOldstyleMatchPair(usernameRegexp,livelinkUserExpr);
	}
	else
	{
		// New style configuration.
		String userNameMapping = parameters.getParameter(org.apache.lcf.crawler.connectors.livelink.LiveLinkParameters.userNameMapping);
		if (userNameMapping == null)
			userNameMapping = "^(.*)\\\\@([A-Z|a-z|0-9|_|-]*)\\\\.(.*)$=$(2)\\\\$(1l)";
		matchMap = new org.apache.lcf.crawler.connectors.livelink.MatchMap(userNameMapping);
	}

	usernameRegexp = matchMap.getMatchString(0);
	livelinkUserExpr = matchMap.getReplaceString(0);

	// The "Server" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Server name:</nobr></td>
		<td class="value"><input type="text" size="64" name="servername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server port:</nobr></td>
		<td class="value"><input type="text" size="5" name="serverport" value='<%=serverPort%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server user name:</nobr></td>
		<td class="value"><input type="text" size="32" name="serverusername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverUserName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server password:</nobr></td>
		<td class="value"><input type="password" size="32" name="serverpassword" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverPassword)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Server tab
%>
	<input type="hidden" name="servername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverName)%>'/>
	<input type="hidden" name="serverport" value='<%=serverPort%>'/>
	<input type="hidden" name="serverusername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverUserName)%>'/>
	<input type="hidden" name="serverpassword" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverPassword)%>'/>
<%
	}

	// The "User Mapping" tab
	if (tabName.equals("User Mapping"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>User name regular expression:</nobr></td>
		<td class="value"><input type="text" size="40" name="usernameregexp" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(usernameRegexp)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Livelink user expression:</nobr></td>
		<td class="value"><input type="text" size="40" name="livelinkuserexpr" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(livelinkUserExpr)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for "User Mapping" tab
%>
	<input type="hidden" name="usernameregexp" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(usernameRegexp)%>'/>
	<input type="hidden" name="livelinkuserexpr" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(livelinkUserExpr)%>'/>
<%
	}
%>
