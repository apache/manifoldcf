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

	String dmwsServerProtocol = parameters.getParameter("DMWSServerProtocol");
	if (dmwsServerProtocol == null)
		dmwsServerProtocol = "http";
	String rmwsServerProtocol = parameters.getParameter("RMWSServerProtocol");
	if (rmwsServerProtocol == null)
		rmwsServerProtocol = "http";
	String metacartawsServerProtocol = parameters.getParameter("MetaCartaWSServerProtocol");
	if (metacartawsServerProtocol == null)
		metacartawsServerProtocol = "http";

	String dmwsServerName = parameters.getParameter("DMWSServerName");
	if (dmwsServerName == null)
		dmwsServerName = "";
	String rmwsServerName = parameters.getParameter("RMWSServerName");
	if (rmwsServerName == null)
		rmwsServerName = "";
	String metacartawsServerName = parameters.getParameter("MetaCartaWSServerName");
	if (metacartawsServerName == null)
		metacartawsServerName = "";

	String dmwsServerPort = parameters.getParameter("DMWSServerPort");
	if (dmwsServerPort == null)
		dmwsServerPort = "";
	String rmwsServerPort = parameters.getParameter("RMWSServerPort");
	if (rmwsServerPort == null)
		rmwsServerPort = "";
	String metacartawsServerPort = parameters.getParameter("MetaCartaWSServerPort");
	if (metacartawsServerPort == null)
		metacartawsServerPort = "";

	String dmwsLocation = parameters.getParameter("DMWSLocation");
	if (dmwsLocation == null)
		dmwsLocation = "/DMWS/MeridioDMWS.asmx";
	String rmwsLocation = parameters.getParameter("RMWSLocation");
	if (rmwsLocation == null)
		rmwsLocation = "/RMWS/MeridioRMWS.asmx";
	String metacartawsLocation = parameters.getParameter("MetaCartaWSLocation");
	if (metacartawsLocation == null)
		metacartawsLocation = "/MetaCartaWebService/MetaCarta.asmx";

	String dmwsProxyHost = parameters.getParameter("DMWSProxyHost");
	if (dmwsProxyHost == null)
		dmwsProxyHost = "";
	String rmwsProxyHost = parameters.getParameter("RMWSProxyHost");
	if (rmwsProxyHost == null)
		rmwsProxyHost = "";
	String metacartawsProxyHost = parameters.getParameter("MetaCartaWSProxyHost");
	if (metacartawsProxyHost == null)
		metacartawsProxyHost = "";

	String dmwsProxyPort = parameters.getParameter("DMWSProxyPort");
	if (dmwsProxyPort == null)
		dmwsProxyPort = "";
	String rmwsProxyPort = parameters.getParameter("RMWSProxyPort");
	if (rmwsProxyPort == null)
		rmwsProxyPort = "";
	String metacartawsProxyPort = parameters.getParameter("MetaCartaWSProxyPort");
	if (metacartawsProxyPort == null)
		metacartawsProxyPort = "";

	String userName = parameters.getParameter("UserName");
	if (userName == null)
		userName = "";

	String password = parameters.getObfuscatedParameter("Password");
	if (password == null)
		password = "";

	String meridioKeystore = parameters.getParameter("MeridioKeystore");
	IKeystoreManager localKeystore;
	if (meridioKeystore == null)
		localKeystore = KeystoreManagerFactory.make("");
	else
		localKeystore = KeystoreManagerFactory.make("",meridioKeystore);

%>
<input name="configop" type="hidden" value="Continue"/>
<%

	// "Document Server" tab
	if (tabName.equals("Document Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Document webservice server protocol:</nobr></td><td class="value"><select name="dmwsServerProtocol"><option value="http" <%=((dmwsServerProtocol.equals("http"))?"selected=\"true\"":"")%>>http</option><option value="https" <%=(dmwsServerProtocol.equals("https")?"selected=\"true\"":"")%>>https</option></select></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document webservice server name:</nobr></td><td class="value"><input type="text" size="64" name="dmwsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsServerName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Document webservice server port:</nobr></td><td class="value"><input type="text" size="5" name="dmwsServerPort" value='<%=dmwsServerPort%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Document webservice location:</nobr></td><td class="value"><input type="text" size="64" name="dmwsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsLocation)%>'/></td>
	</tr>
	<tr>
		<td class="separator" colspan="2"><hr/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document webservice server proxy host:</nobr></td><td class="value"><input type="text" size="64" name="dmwsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsProxyHost)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document webservice server proxy port:</nobr></td><td class="value"><input type="text" size="5" name="dmwsProxyPort" value='<%=dmwsProxyPort%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for the Document Server tab.
%>
<input type="hidden" name="dmwsServerProtocol" value='<%=dmwsServerProtocol%>'/>
<input type="hidden" name="dmwsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsServerName)%>'/>
<input type="hidden" name="dmwsServerPort" value='<%=dmwsServerPort%>'/>
<input type="hidden" name="dmwsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsLocation)%>'/>
<input type="hidden" name="dmwsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dmwsProxyHost)%>'/>
<input type="hidden" name="dmwsProxyPort" value='<%=dmwsProxyPort%>'/>
<%
	}

	// "Records Server" tab
	if (tabName.equals("Records Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Record webservice server protocol:</nobr></td><td class="value"><select name="rmwsServerProtocol"><option value="http" <%=((rmwsServerProtocol.equals("http"))?"selected=\"true\"":"")%>>http</option><option value="https" <%=(rmwsServerProtocol.equals("https")?"selected=\"true\"":"")%>>https</option></select></td>
	</tr>
	<tr>
		<td class="description"><nobr>Record webservice server name:</nobr></td><td class="value"><input type="text" size="64" name="rmwsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsServerName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Record webservice server port:</nobr></td><td class="value"><input type="text" size="5" name="rmwsServerPort" value='<%=rmwsServerPort%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Record webservice location:</nobr></td><td class="value"><input type="text" size="64" name="rmwsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsLocation)%>'/></td>
	</tr>
	<tr>
		<td class="separator" colspan="2"><hr/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Record webservice server proxy host:</nobr></td><td class="value"><input type="text" size="64" name="rmwsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsProxyHost)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Record webservice server proxy port:</nobr></td><td class="value"><input type="text" size="5" name="rmwsProxyPort" value='<%=rmwsProxyPort%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for the Records Server tab.
%>
<input type="hidden" name="rmwsServerProtocol" value='<%=rmwsServerProtocol%>'/>
<input type="hidden" name="rmwsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsServerName)%>'/>
<input type="hidden" name="rmwsServerPort" value='<%=rmwsServerPort%>'/>
<input type="hidden" name="rmwsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsLocation)%>'/>
<input type="hidden" name="rmwsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(rmwsProxyHost)%>'/>
<input type="hidden" name="rmwsProxyPort" value='<%=rmwsProxyPort%>'/>
<%
	}

	// The "User Service Server" tab
	if (tabName.equals("User Service Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>User webservice server protocol:</nobr></td><td class="value"><select name="metacartawsServerProtocol"><option value="http" <%=((metacartawsServerProtocol.equals("http"))?"selected=\"true\"":"")%>>http</option><option value="https" <%=(metacartawsServerProtocol.equals("https")?"selected=\"true\"":"")%>>https</option></select></td>
	</tr>
	<tr>
		<td class="description"><nobr>User webservice server name:</nobr></td><td class="value"><input type="text" size="64" name="metacartawsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsServerName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>User webservice server port:</nobr></td><td class="value"><input type="text" size="5" name="metacartawsServerPort" value='<%=metacartawsServerPort%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>User webservice location:</nobr></td><td class="value"><input type="text" size="64" name="metacartawsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsLocation)%>'/></td>
	</tr>
	<tr>
		<td class="separator" colspan="2"><hr/></td>
	</tr>
	<tr>
		<td class="description"><nobr>User webservice server proxy host:</nobr></td><td class="value"><input type="text" size="64" name="metacartawsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsProxyHost)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>User webservice server proxy port:</nobr></td><td class="value"><input type="text" size="5" name="metacartawsProxyPort" value='<%=metacartawsProxyPort%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for the User Service Server tab.
%>
<input type="hidden" name="metacartawsServerProtocol" value='<%=metacartawsServerProtocol%>'/>
<input type="hidden" name="metacartawsServerName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsServerName)%>'/>
<input type="hidden" name="metacartawsServerPort" value='<%=metacartawsServerPort%>'/>
<input type="hidden" name="metacartawsLocation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsLocation)%>'/>
<input type="hidden" name="metacartawsProxyHost" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(metacartawsProxyHost)%>'/>
<input type="hidden" name="metacartawsProxyPort" value='<%=metacartawsProxyPort%>'/>
<%
	}

	// The "Credentials" tab
	// Always pass the whole keystore as a hidden.
	if (meridioKeystore != null)
	{
%>
<input type="hidden" name="keystoredata" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(meridioKeystore)%>'/>
<%
	}

	if (tabName.equals("Credentials"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
        <tr>
		<td class="description"><nobr>User name:</nobr></td><td class="value"><input type="text" size="32" name="userName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/></td>
	</tr>
        <tr>
		<td class="description"><nobr>Password:</nobr></td><td class="value"><input type="password" size="32" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>SSL certificate list:</nobr></td>
		<td class="value">
			<input type="hidden" name="keystorealias" value=""/>
			<table class="displaytable">
<%
		// List the individual certificates in the store, with a delete button for each
		String[] contents = localKeystore.getContents();
		if (contents.length == 0)
		{
%>
			<tr><td class="message" colspan="2"><nobr>No certificates present</nobr></td></tr>
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
				<td class="value"><input type="button" onclick='<%="Javascript:DeleteCertificate(\""+org.apache.lcf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")"%>' alt='<%="Delete cert "+org.apache.lcf.ui.util.Encoder.attributeEscape(alias)%>' value="Delete"/></td>
				<td><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></td>
			</tr>
<%

				i++;
			}
		}
%>
			</table>
			<input type="button" onclick='<%="Javascript:AddCertificate()"%>' alt="Add cert" value="Add"/>&nbsp;
			Certificate:&nbsp;<input name="certificate" size="50" type="file"/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for the "Credentials" tab
%>
<input type="hidden" name="userName" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/>
<input type="hidden" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}

%>
