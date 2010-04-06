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

	String domainControllerName = parameters.getParameter(org.apache.lcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_DOMAINCONTROLLER);
	if (domainControllerName == null)
		domainControllerName = "";
	String userName = parameters.getParameter(org.apache.lcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_USERNAME);
	if (userName == null)
		userName = "";
	String password = parameters.getObfuscatedParameter(org.apache.lcf.authorities.authorities.activedirectory.ActiveDirectoryConfig.PARAM_PASSWORD);
	if (password == null)
		password = "";

	// The "Domain Controller" tab
	if (tabName.equals("Domain Controller"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Domain controller name:</nobr></td>
		<td class="value"><input type="text" size="64" name="domaincontrollername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(domainControllerName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Administrative user name:</nobr></td>
		<td class="value"><input type="text" size="32" name="username" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Administrative password:</nobr></td>
		<td class="value"><input type="password" size="32" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Domain Controller tab
%>
<input type="hidden" name="domaincontrollername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(domainControllerName)%>'/>
<input type="hidden" name="username" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/>
<input type="hidden" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}
%>
