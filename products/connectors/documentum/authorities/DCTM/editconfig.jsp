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

	String docbaseName = parameters.getParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_DOCBASE);
	if (docbaseName == null)
		docbaseName = "";

	String docbaseUserName = parameters.getParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_USERNAME);
	if (docbaseUserName == null)
		docbaseUserName = "";

	String docbasePassword = parameters.getObfuscatedParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_PASSWORD);
	if (docbasePassword == null)
		docbasePassword = "";

	String docbaseDomain = parameters.getParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_DOMAIN);
	if (docbaseDomain == null)
		docbaseDomain = "";

        String caseInsensitiveUser = parameters.getParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_CASEINSENSITIVE);
        if (caseInsensitiveUser == null)
                caseInsensitiveUser = "false";

	String useSystemAcls = parameters.getParameter(com.metacarta.crawler.authorities.DCTM.AuthorityConnector.CONFIG_PARAM_USESYSTEMACLS);
	if (useSystemAcls == null)
		useSystemAcls = "true";

	// "Docbase" tab
	if (tabName.equals("Docbase"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Docbase name:</nobr></td>
		<td class="value"><input type="text" size="32" name="docbasename" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Docbase user name:</nobr></td>
		<td class="value"><input type="text" size="32" name="docbaseusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseUserName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Docbase password:</nobr></td>
		<td class="value"><input type="password" size="32" name="docbasepassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbasePassword)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Docbase domain:</nobr></td>
		<td class="value"><input type="text" size="32" name="docbasedomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseDomain)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for "Docbase" tab
%>
<input type="hidden" name="docbasename" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseName)%>'/>
<input type="hidden" name="docbaseusername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseUserName)%>'/>
<input type="hidden" name="docbasepassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbasePassword)%>'/>
<input type="hidden" name="docbasedomain" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(docbaseDomain)%>'/>
<%
	}

	// "User Mapping" tab
	if (tabName.equals("User Mapping"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
        <tr>
                <td class="description"><nobr>Authentication username matching:</nobr></td>
                <td class="value">
		    <table class="displaytable">
                    <tr>
			<td class="description"><input name="usernamecaseinsensitive" type="radio" value="true" <%=(caseInsensitiveUser.equals("true"))?"checked=\"true\"":""%> /></td>
			<td class="value"><nobr>Case insensitive</nobr></td>
                    </tr>
                    <tr>
			<td class="description"><input name="usernamecaseinsensitive" type="radio" value="false" <%=(!caseInsensitiveUser.equals("true"))?"checked=\"true\"":""%> /></td>
			<td class="value"><nobr>Case sensitive</nobr></td>
                    </tr>
                    </table>
                </td>
        </tr>
</table>
<%
	}
	else
	{
		// Hiddens for "User Mapping" tab
%>
<input type="hidden" name="usernamecaseinsensitive" value='<%=caseInsensitiveUser%>'/>
<%
	}

	// "System ACLs" tab
	if (tabName.equals("System ACLs"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
        <tr>
                <td class="description"><nobr>Use system acls:</nobr></td>
                <td class="value">
		    <table class="displaytable">
                    <tr>
			<td class="description"><input name="usesystemacls" type="radio" value="true" <%=(useSystemAcls.equals("true"))?"checked=\"true\"":""%> /></td>
			<td class="value"><nobr>Use system acls</nobr></td>
                    </tr>
                    <tr>
			<td class="description"><input name="usesystemacls" type="radio" value="false" <%=(!useSystemAcls.equals("true"))?"checked=\"true\"":""%> /></td>
			<td class="value"><nobr>Don't use system acls</nobr></td>
                    </tr>
                    </table>
                </td>
        </tr>
</table>
<%
	}
	else
	{
		// Hiddens for "System ACLs" tab
%>
<input type="hidden" name="usesystemacls" value='<%=useSystemAcls%>'/>
<%
	}
%>

