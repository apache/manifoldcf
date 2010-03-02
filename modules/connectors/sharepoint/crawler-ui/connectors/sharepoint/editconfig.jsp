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

	String serverVersion = parameters.getParameter("serverVersion");
	if (serverVersion == null)
		serverVersion = "2.0";

	String serverProtocol = parameters.getParameter("serverProtocol");
	if (serverProtocol == null)
		serverProtocol = "http";

	String serverName = parameters.getParameter("serverName");
	if (serverName == null)
		serverName = "localhost";

	String serverPort = parameters.getParameter("serverPort");
	if (serverPort == null)
		serverPort = "";

	String serverLocation = parameters.getParameter("serverLocation");
	if (serverLocation == null)
		serverLocation = "";
		
	String userName = parameters.getParameter("userName");
	if (userName == null)
		userName = "";

	String password = parameters.getObfuscatedParameter("password");
	if (password == null)
		password = "";

	String keystore = parameters.getParameter("keystore");
	IKeystoreManager localKeystore;
	if (keystore == null)
		localKeystore = KeystoreManagerFactory.make("");
	else
		localKeystore = KeystoreManagerFactory.make("",keystore);

	// "Server" tab
	// Always send along the keystore.
	if (keystore != null)
	{
%>
<input type="hidden" name="keystoredata" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(keystore)%>'/>
<%
	}

	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr>
		<td class="description"><nobr>Server SharePoint version:</nobr></td><td class="value">
		     <select name="serverVersion">
			<option value="2.0" <%=((serverVersion.equals("2.0"))?"selected=\"true\"":"")%>>SharePoint Services 2.0</option>
			<option value="3.0" <%=(serverVersion.equals("3.0")?"selected=\"true\"":"")%>>SharePoint Services 3.0</option>
		    </select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Server protocol:</nobr></td><td class="value"><select name="serverProtocol"><option value="http" <%=((serverProtocol.equals("http"))?"selected=\"true\"":"")%>>http</option><option value="https" <%=(serverProtocol.equals("https")?"selected=\"true\"":"")%>>https</option></select></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server name:</nobr></td><td class="value"><input type="text" size="64" name="serverName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Server port:</nobr></td><td class="value"><input type="text" size="5" name="serverPort" value='<%=serverPort%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Site path:</nobr></td><td class="value"><input type="text" size="64" name="serverLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverLocation)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>User name:</nobr></td><td class="value"><input type="text" size="32" name="userName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Password:</nobr></td><td class="value"><input type="password" size="32" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>SSL certificate list:</nobr></td>
		<td class="value">
			<input type="hidden" name="configop" value="Continue"/>
			<input type="hidden" name="shpkeystorealias" value=""/>
			<table class="displaytable">
<%
		// List the individual certificates in the store, with a delete button for each
		String[] contents = localKeystore.getContents();
		if (contents.length == 0)
		{
%>
			<tr><td class="message" colspan="2">No certificates present</td></tr>
<%
		}
		else
		{
%>
<%
			int i = 0;
			while (i < contents.length)
			{
				String alias = contents[i];
				String description = localKeystore.getDescription(alias);
				if (description.length() > 128)
					description = description.substring(0,125) + "...";
%>
			<tr>
				<td class="value"><input type="button" onclick='<%="Javascript:ShpDeleteCertificate(\""+org.apache.lcf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")"%>' alt='<%="Delete cert "+org.apache.lcf.ui.util.Encoder.attributeEscape(alias)%>' value="Delete"/></td>
				<td><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></td>
			</tr>
<%

				i++;
			}
		}
%>
			</table>
			<input type="button" onclick='<%="Javascript:ShpAddCertificate()"%>' alt="Add cert" value="Add"/>&nbsp;
			Certificate:&nbsp;<input name="shpcertificate" size="50" type="file"/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Server tab hiddens
%>
<input type="hidden" name="serverProtocol" value='<%=serverProtocol%>'/>
<input type="hidden" name="serverName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverName)%>'/>
<input type="hidden" name="serverPort" value='<%=serverPort%>'/>
<input type="hidden" name="serverLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(serverLocation)%>'/>
<input type="hidden" name="userName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/>
<input type="hidden" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}
%>
