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

	String jdbcProvider = parameters.getParameter(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.providerParameter);
	if (jdbcProvider == null)
		jdbcProvider = "oracle:thin:@";
	String host = parameters.getParameter(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.hostParameter);
	if (host == null)
		host = "localhost";
	String databaseName = parameters.getParameter(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.databaseNameParameter);
	if (databaseName == null)
		databaseName = "database";
	String databaseUser = parameters.getParameter(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.databaseUserName);
	if (databaseUser == null)
		databaseUser = "";
	String databasePassword = parameters.getObfuscatedParameter(org.apache.lcf.crawler.connectors.jdbc.JDBCConstants.databasePassword);
	if (databasePassword == null)
		databasePassword = "";

	// "Database Type" tab
	if (tabName.equals("Database Type"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Database type:</nobr></td><td class="value">
		    <select multiple="false" name="databasetype" size="2">
			<option value="oracle:thin:@" <%=(jdbcProvider.equals("oracle:thin:@"))?"selected=\"selected\"":""%>>Oracle</option>
			<option value="postgresql:" <%=(jdbcProvider.equals("postgresql:"))?"selected=\"selected\"":""%>>Postgres SQL</option>
			<option value="jtds:sqlserver:" <%=(jdbcProvider.equals("jtds:sqlserver:"))?"selected=\"selected\"":""%>>MS SQL Server (&gt; V6.5)</option>
			<option value="jtds:sybase:" <%=(jdbcProvider.equals("jtds:sybase:"))?"selected=\"selected\"":""%>>Sybase (&gt;= V10)</option>
		    </select></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="databasetype" value='<%=jdbcProvider%>'/>
<%
	}

	// "Server" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Database host and port:</nobr></td><td class="value"><input type="text" size="64" name="databasehost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(host)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Database service name or instance/database:</nobr></td><td class="value"><input type="text" size="32" name="databasename" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databaseName)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="databasehost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(host)%>'/>
<input type="hidden" name="databasename" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databaseName)%>'/>
<%
	}

	// "Credentials" tab
	if (tabName.equals("Credentials"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
        <tr>
		<td class="description"><nobr>User name:</nobr></td><td class="value"><input type="text" size="32" name="username" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databaseUser)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Password:</nobr></td><td class="value"><input type="password" size="32" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databasePassword)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="username" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databaseUser)%>'/>
<input type="hidden" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(databasePassword)%>'/>
<%
	}
%>


