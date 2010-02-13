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
	// This file is included by every place that the configuration information for the GTS output connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.


	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String protocol = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PROTOCOL);
	if (protocol == null)
		protocol = "http";
		
	String server = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_SERVER);
	if (server == null)
		server = "localhost";

	String port = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PORT);
	if (port == null)
		port = "8983";

	String webapp = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_WEBAPPNAME);
	if (webapp == null)
		webapp = "solr";

	String updatePath = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_UPDATEPATH);
	if (updatePath == null)
		updatePath = "/update/extract";

	String removePath = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_REMOVEPATH);
	if (removePath == null)
		removePath = "/update";

	String statusPath = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_STATUSPATH);
	if (statusPath == null)
		statusPath = "/admin/ping";

	String realm = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_REALM);
	if (realm == null)
		realm = "";

	String userID = parameters.getParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_USERID);
	if (userID == null)
		userID = "";
		
	String password = parameters.getObfuscatedParameter(com.metacarta.agents.output.lucene.LuceneConfig.PARAM_PASSWORD);
	if (password == null)
		password = "";
		
	// "Appliance" tab
	if (tabName.equals("Lucene"))
	{
%>
<table class="displaytable">
	<tr>
		<td class="description"><nobr>Protocol:</nobr></td>
		<td class="value">
			<select name="serverprotocol">
				<option value="http"<%=(protocol.equals("http")?" selected=\"true\"":"")%>>http</option>
				<option value="https"<%=(protocol.equals("https")?" selected=\"true\"":"")%>>https</option>
			</select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Server name:</nobr></td>
		<td class="value">
			<input name="servername" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(server)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Port:</nobr></td>
		<td class="value">
			<input name="serverport" type="text" size="5" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(port)%>'/>
		</td>
	</tr>
	<tr><td colspan="2" class="separator"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Web application  name:</nobr></td>
		<td class="value">
			<input name="webappname" type="text" size="16" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(webapp)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Update handler:</nobr></td>
		<td class="value">
			<input name="updatepath" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(updatePath)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Remove handler:</nobr></td>
		<td class="value">
			<input name="removepath" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(removePath)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Status handler:</nobr></td>
		<td class="value">
			<input name="statuspath" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(statusPath)%>'/>
		</td>
	</tr>
	<tr><td colspan="2" class="separator"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Realm:</nobr></td>
		<td class="value">
			<input name="realm" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(realm)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>User ID:</nobr></td>
		<td class="value">
			<input name="userid" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(userID)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Password:</nobr></td>
		<td class="value">
			<input type="password" size="32" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Lucene tab hiddens
%>
<input type="hidden" name="serverprotocol" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(protocol)%>'/>
<input type="hidden" name="servername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(server)%>'/>
<input type="hidden" name="serverport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(port)%>'/>
<input type="hidden" name="webappname" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(webapp)%>'/>
<input type="hidden" name="updatepath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(updatePath)%>'/>
<input type="hidden" name="removepath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(removePath)%>'/>
<input type="hidden" name="statuspath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(statusPath)%>'/>
<input type="hidden" name="realm" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(realm)%>'/>
<input type="hidden" name="userid" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(userID)%>'/>
<input type="hidden" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}
%>
