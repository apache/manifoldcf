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
	// This file is included by every place that the configuration information for the rss connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String email = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.emailParameter);
	if (email == null)
		email = "";
	String robotsUsage = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.robotsUsageParameter);
	if (robotsUsage == null)
		robotsUsage = "all";
	String bandwidth = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.bandwidthParameter);
	if (bandwidth == null)
		bandwidth = "";
	String connections = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.maxOpenParameter);
	if (connections == null)
		connections = "10";
	String fetches = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.maxFetchesParameter);
	if (fetches == null)
		fetches = "";
	String throttleGroup = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.throttleGroupParameter);
	if (throttleGroup == null)
		throttleGroup = "";
	String proxyHost = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyHostParameter);
	if (proxyHost == null)
		proxyHost = "";
	String proxyPort = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyPortParameter);
	if (proxyPort == null)
		proxyPort = "";
	String proxyAuthDomain = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthDomainParameter);
	if (proxyAuthDomain == null)
		proxyAuthDomain = "";
	String proxyAuthUsername = parameters.getParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthUsernameParameter);
	if (proxyAuthUsername == null)
		proxyAuthUsername = "";
	String proxyAuthPassword = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.rss.RSSConnector.proxyAuthPasswordParameter);
	if (proxyAuthPassword == null)
		proxyAuthPassword = "";
		
	// Email tab
	if (tabName.equals("Email"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Email address to contact:</nobr></td><td class="value"><input type="text" size="32" name="email" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(email)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="email" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(email)%>'/>
<%
	}

	// Robots tab
	if (tabName.equals("Robots"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Robots.txt usage:</nobr></td>
		<td class="value">
		    <select name="robotsusage" size="3">
			<option value="none" <%=robotsUsage.equals("none")?"selected=\"selected\"":""%>>Don't look at robots.txt</option>
			<option value="data" <%=robotsUsage.equals("data")?"selected=\"selected\"":""%>>Obey robots.txt for data fetches only</option>
			<option value="all" <%=robotsUsage.equals("all")?"selected=\"selected\"":""%>>Obey robots.txt for all fetches</option>
		    </select>
		</td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="robotsusage" value='<%=robotsUsage%>'/>
<%
	}

	// Bandwidth tab
	if (tabName.equals("Bandwidth"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Max KBytes per second per server:</nobr></td><td class="value"><input type="text" size="6" name="bandwidth" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(bandwidth)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Max connections per server:</nobr></td><td class="value"><input type="text" size="4" name="connections" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(connections)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Max fetches per minute per server:</nobr></td><td class="value"><input type="text" size="4" name="fetches" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(fetches)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Throttle group name:</nobr></td><td class="value"><input type="text" size="32" name="throttlegroup" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(throttleGroup)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="bandwidth" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(bandwidth)%>'/>
<input type="hidden" name="connections" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(connections)%>'/>
<input type="hidden" name="fetches" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(fetches)%>'/>
<input type="hidden" name="throttlegroup" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(throttleGroup)%>'/>
<%
	}
	
	// Proxy tab
	if (tabName.equals("Proxy"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Proxy host:</nobr></td><td class="value"><input type="text" size="40" name="proxyhost" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyHost)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Proxy port:</nobr></td><td class="value"><input type="text" size="5" name="proxyport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyPort)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Proxy authentication domain:</nobr></td><td class="value"><input type="text" size="32" name="proxyauthdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthDomain)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Proxy authentication user name:</nobr></td><td class="value"><input type="text" size="32" name="proxyauthusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthUsername)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Proxy authentication password:</nobr></td><td class="value"><input type="password" size="16" name="proxyauthpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthPassword)%>'/></td>
	</tr>
</table>
<%

	}
	else
	{
%>
<input type="hidden" name="proxyhost" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyHost)%>'/>
<input type="hidden" name="proxyport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyPort)%>'/>
<input type="hidden" name="proxyauthusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthUsername)%>'/>
<input type="hidden" name="proxyauthdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthDomain)%>'/>
<input type="hidden" name="proxyauthpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(proxyAuthPassword)%>'/>
<%
	}
%>
