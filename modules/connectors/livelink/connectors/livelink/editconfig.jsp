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

	String ingestProtocol = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestProtocol);
	if (ingestProtocol == null)
		ingestProtocol = "http";
	String ingestPort = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestPort);
	if (ingestPort == null)
		ingestPort = "";
	String ingestCgiPath = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestCgiPath);
	if (ingestCgiPath == null)
		ingestCgiPath = "/livelink/livelink.exe";
	String viewProtocol = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewProtocol);
	if (viewProtocol == null)
		viewProtocol = "";
	String viewServerName = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewServerName);
	if (viewServerName == null)
		viewServerName = "";
	String viewPort = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewPort);
	if (viewPort == null)
		viewPort = "";
	String viewCgiPath = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewCgiPath);
	if (viewCgiPath == null)
		viewCgiPath = "";
	String serverName = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverName);
	if (serverName == null)
		serverName = "localhost";
	String serverPort = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPort);
	if (serverPort == null)
		serverPort = "2099";
	String serverUserName = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverUsername);
	if (serverUserName == null)
		serverUserName = "";
	String serverPassword = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPassword);
	if (serverPassword == null)
		serverPassword = "";
	String ntlmUsername = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmUsername);
	if (ntlmUsername == null)
		ntlmUsername = "";
	String ntlmPassword = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmPassword);
	if (ntlmPassword == null)
		ntlmPassword = "";
	String ntlmDomain = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmDomain);
	if (ntlmDomain == null)
		ntlmDomain = "";
	String livelinkKeystore = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
	IKeystoreManager localKeystore;
	if (livelinkKeystore == null)
		localKeystore = KeystoreManagerFactory.make("");
	else
		localKeystore = KeystoreManagerFactory.make("",livelinkKeystore);
%>
<input name="configop" type="hidden" value="Continue"/>
<%
	// The "Server" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Server name:</nobr></td><td class="value"><input type="text" size="64" name="servername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server port:</nobr></td><td class="value"><input type="text" size="5" name="serverport" value='<%=serverPort%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server user name:</nobr></td><td class="value"><input type="text" size="32" name="serverusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverUserName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Server password:</nobr></td><td class="value"><input type="password" size="32" name="serverpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverPassword)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Server tab
%>
<input type="hidden" name="servername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverName)%>'/>
<input type="hidden" name="serverport" value='<%=serverPort%>'/>
<input type="hidden" name="serverusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverUserName)%>'/>
<input type="hidden" name="serverpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(serverPassword)%>'/>
<%
	}

	// The "Document Access" tab
	// Always pass the whole keystore as a hidden.
	if (livelinkKeystore != null)
	{
%>
<input type="hidden" name="keystoredata" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(livelinkKeystore)%>'/>
<%
	}
	if (tabName.equals("Document Access"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description">Document fetch protocol:</td>
		<td class="value">
		    <select name="ingestprotocol" size="2">
			<option value="http" <%=((ingestProtocol.equals("http"))?"selected=\"selected\"":"")%>>http</option>
			<option value="https" <%=((ingestProtocol.equals("https"))?"selected=\"selected\"":"")%>>https</option>
		    </select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch port:</nobr></td>
		<td class="value"><input type="text" size="5" name="ingestport" value='<%=ingestPort%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch SSL certificate list:</nobr></td>
		<td class="value">
			<input type="hidden" name="llkeystorealias" value=""/>
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
				<td class="value"><input type="button" onclick='<%="Javascript:LLDeleteCertificate(\""+com.metacarta.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")"%>' alt='<%="Delete cert "+com.metacarta.ui.util.Encoder.attributeEscape(alias)%>' value="Delete"/></td>
				<td><%=com.metacarta.ui.util.Encoder.bodyEscape(description)%></td>
			</tr>
<%

				i++;
			}
		}
%>
			</table>
			<input type="button" onclick='<%="Javascript:LLAddCertificate(\""+IDFactory.make()+"\")"%>' alt="Add cert" value="Add"/>&nbsp;
			Certificate:&nbsp;<input name="llcertificate" size="50" type="file"/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch CGI path:</nobr></td>
		<td class="value"><input type="text" size="32" name="ingestcgipath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ingestCgiPath)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch NTLM domain:</nobr><br/><nobr>(set if NTLM auth desired)</nobr></td>
		<td class="value">
			<input type="text" size="32" name="ntlmdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmDomain)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch NTLM user name:</nobr><br/><nobr>(set if different from server user name)</nobr></td>
		<td class="value">
			<input type="text" size="32" name="ntlmusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmUsername)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document fetch NTLM password:</nobr><br/><nobr>(set if different from server password)</nobr></td>
		<td class="value">
			<input type="password" size="32" name="ntlmpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmPassword)%>'/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Document Access tab
%>
<input type="hidden" name="ingestprotocol" value='<%=ingestProtocol%>'/>
<input type="hidden" name="ingestport" value='<%=ingestPort%>'/>
<input type="hidden" name="ingestcgipath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ingestCgiPath)%>'/>
<input type="hidden" name="ntlmusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmUsername)%>'/>
<input type="hidden" name="ntlmpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmPassword)%>'/>
<input type="hidden" name="ntlmdomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ntlmDomain)%>'/>
<%
	}

	// Document View tab
	if (tabName.equals("Document View"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description">Document view protocol:</td>
		<td class="value">
		    <select name="viewprotocol" size="3">
			<option value="" <%=((viewProtocol.equals(""))?"selected=\"selected\"":"")%>>Same as fetch protocol</option>
			<option value="http" <%=((viewProtocol.equals("http"))?"selected=\"selected\"":"")%>>http</option>
			<option value="https" <%=((viewProtocol.equals("https"))?"selected=\"selected\"":"")%>>https</option>
		    </select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document view server name:</nobr><br/><nobr>(blank = same as fetch server)</nobr></td>
		<td class="value"><input type="text" size="64" name="viewservername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(viewServerName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document view port:</nobr><br/><nobr>(blank = same as fetch port)</nobr></td>
		<td class="value"><input type="text" size="5" name="viewport" value='<%=viewPort%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Document view CGI path:</nobr><br/><nobr>(blank = same as fetch path)</nobr></td>
		<td class="value"><input type="text" size="32" name="viewcgipath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(viewCgiPath)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Document View tab
%>
<input type="hidden" name="viewprotocol" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(viewProtocol)%>'/>
<input type="hidden" name="viewservername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(viewServerName)%>'/>
<input type="hidden" name="viewport" value='<%=viewPort%>'/>
<input type="hidden" name="viewcgipath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(viewCgiPath)%>'/>
<%
	}
%>

