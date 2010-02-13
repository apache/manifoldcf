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
	// This file is included by every place that the configuration information for the file system connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".
	
	// The coder can presume that this jsp is executed within a body section, and within a form.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String server   = parameters.getParameter(com.metacarta.crawler.connectors.sharedrive.SharedDriveParameters.server);
	if (server==null) server = "";
	String domain = parameters.getParameter(com.metacarta.crawler.connectors.sharedrive.SharedDriveParameters.domain);
	if (domain==null) domain = "";
	String username = parameters.getParameter(com.metacarta.crawler.connectors.sharedrive.SharedDriveParameters.username);
	if (username==null) username = "";
	String password = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.sharedrive.SharedDriveParameters.password);
	if (password==null) password = "";

	// "Server" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
        <tr>
                <td class="description"><nobr>Server:</nobr></td><td class="value"><input type="text" size="32" name="server" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(server)%>'/></td>
        </tr>
        <tr>
                <td class="description"><nobr>Authentication domain (optional):</nobr></td><td class="value"><input type="text" size="32" name="domain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(domain)%>'/></td>
        </tr>
        <tr>
                <td class="description"><nobr>User name:</nobr></td><td class="value"><input type="text" size="32" name="username" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(username)%>'/></td>
        </tr>
        <tr>
                <td class="description"><nobr>Password:</nobr></td><td class="value"><input type="password" size="32" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/></td>
        </tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="server" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(server)%>'/>
<input type="hidden" name="domain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(domain)%>'/>
<input type="hidden" name="username" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(username)%>'/>
<input type="hidden" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}
%>


