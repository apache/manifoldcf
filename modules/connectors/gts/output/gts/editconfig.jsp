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

	String ingestURI = parameters.getParameter(com.metacarta.agents.output.gts.GTSConfig.PARAM_INGESTURI);
	if (ingestURI == null)
		ingestURI = "http://localhost:7031/HTTPIngest";

	String realm = parameters.getParameter(com.metacarta.agents.output.gts.GTSConfig.PARAM_REALM);
	if (realm == null)
		realm = "";

	String userID = parameters.getParameter(com.metacarta.agents.output.gts.GTSConfig.PARAM_USERID);
	if (userID == null)
		userID = "";
		
	String password = parameters.getObfuscatedParameter(com.metacarta.agents.output.gts.GTSConfig.PARAM_PASSWORD);
	if (password == null)
		password = "";
		
	// "Appliance" tab
	if (tabName.equals("Appliance"))
	{
%>
<table class="displaytable">
	<tr>
		<td class="description"><nobr>Ingest URI:</nobr></td>
		<td class="value">
			<input name="ingesturi" type="text" size="32" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ingestURI)%>'/>
		</td>
	</tr>
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
		// Appliance tab hiddens
%>
<input type="hidden" name="ingesturi" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(ingestURI)%>'/>
<input type="hidden" name="userid" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(userID)%>'/>
<input type="hidden" name="password" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}
%>
