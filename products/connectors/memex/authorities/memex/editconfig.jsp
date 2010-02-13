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
	// This file is included by every place that the configuration information for the memex connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String memexServerName = parameters.getParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_MEMEXSERVERNAME);
	if (memexServerName == null)
		memexServerName = "";
	String memexServerPort = parameters.getParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_MEMEXSERVERPORT);
	if (memexServerPort == null)
		memexServerPort = "";
	String crawlUser = parameters.getParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_USERID);
	if (crawlUser == null)
		crawlUser = "";
	String crawlUserPassword = parameters.getObfuscatedParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_PASSWORD);
	if (crawlUserPassword == null)
		crawlUserPassword = "";
	String userNameMapping = parameters.getParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_USERNAMEMAPPING);
	if (userNameMapping == null)
		userNameMapping = "^([^\\\\@]*).*$=$(1)";
	String characterEncoding = parameters.getParameter(com.metacarta.crawler.connectors.memex.MemexAuthority.CONFIG_PARAM_CHARACTERENCODING);
	if (characterEncoding == null)
		characterEncoding = "windows-1252";

	com.metacarta.crawler.connectors.memex.MatchMap matchMap = new com.metacarta.crawler.connectors.memex.MatchMap(userNameMapping);

	String usernameRegexp = matchMap.getMatchString(0);
	String memexUserExpr = matchMap.getReplaceString(0);

	// "Memex Server" tab
	if (tabName.equals("Memex Server"))
	{
		// Legal character sets for java 1.5
		String[] legalCharsets = new String[]{
		    "ISO-8859-1",
		    "ISO-8859-2",
		    "ISO-8859-4",
		    "ISO-8859-5",
		    "ISO-8859-7",
		    "ISO-8859-9",
		    "ISO-8859-13",
		    "ISO-8859-15",
		    "KOI8-R",
		    "US-ASCII",
		    "UTF-8",
		    "UTF-16",
		    "UTF-16BE",
		    "UTF-16LE",
		    "windows-1250",
		    "windows-1251",
		    "windows-1252",
		    "windows-1253",
		    "windows-1254",
		    "windows-1257",
		    "Big5",
		    "Big5-HKSCS",
		    "EUC-JP",
		    "EUC-KR",
		    "GB18030",
		    "GB2312",
		    "GBK",
		    "IBM-Thai",
		    "IBM00858",
		    "IBM01140",
		    "IBM01141",
		    "IBM01142",
		    "IBM01143",
		    "IBM01144",
		    "IBM01145",
		    "IBM01146",
		    "IBM01147",
		    "IBM01148",
		    "IBM01149",
		    "IBM037",
		    "IBM1026",
		    "IBM1047",
		    "IBM273",
		    "IBM277",
		    "IBM278",
		    "IBM280",
		    "IBM284",
		    "IBM285",
		    "IBM297",
		    "IBM420",
		    "IBM424",
		    "IBM437",
		    "IBM500",
		    "IBM775",
		    "IBM850",
		    "IBM852",
		    "IBM855",
		    "IBM857",
		    "IBM860",
		    "IBM861",
		    "IBM862",
		    "IBM863",
		    "IBM864",
		    "IBM865",
		    "IBM866",
		    "IBM868",
		    "IBM869",
		    "IBM870",
		    "IBM871",
		    "IBM918",
		    "ISO-2022-CN",
		    "ISO-2022-JP",
		    "ISO-2022-KR",
		    "ISO-8859-3",
		    "ISO-8859-6",
		    "ISO-8859-8",
		    "Shift_JIS",
		    "TIS-620",
		    "windows-1255",
		    "windows-1256",
		    "windows-1258",
		    "windows-31j",
		    "x-Big5_Solaris",
		    "x-euc-jp-linux",
		    "x-EUC-TW",
		    "x-eucJP-Open",
		    "x-IBM1006",
		    "x-IBM1025",
		    "x-IBM1046",
		    "x-IBM1097",
		    "x-IBM1098",
		    "x-IBM1112",
		    "x-IBM1122",
		    "x-IBM1123",
		    "x-IBM1124",
		    "x-IBM1381",
		    "x-IBM1383",
		    "x-IBM33722",
		    "x-IBM737",
		    "x-IBM856",
		    "x-IBM874",
		    "x-IBM875",
		    "x-IBM921",
		    "x-IBM922",
		    "x-IBM930",
		    "x-IBM933",
		    "x-IBM935",
		    "x-IBM937",
		    "x-IBM939",
		    "x-IBM942",
		    "x-IBM942C",
		    "x-IBM943",
		    "x-IBM943C",
		    "x-IBM948",
		    "x-IBM949",
		    "x-IBM949C",
		    "x-IBM950",
		    "x-IBM964",
		    "x-IBM970",
		    "x-ISCII91",
		    "x-ISO2022-CN-CNS",
		    "x-ISO2022-CN-GB",
		    "x-iso-8859-11",
		    "x-Johab",
		    "x-MacArabic",
		    "x-MacCentralEurope",
		    "x-MacCroatian",
		    "x-MacCyrillic",
		    "x-MacDingbat",
		    "x-MacGreek",
		    "x-MacHebrew",
		    "x-MacIceland",
		    "x-MacRoman",
		    "x-MacRomania",
		    "x-MacSymbol",
		    "x-MacThai",
		    "x-MacTurkish",
		    "x-MacUkraine",
		    "x-MS950-HKSCS",
		    "x-mswin-936",
		    "x-PCK",
		    "x-windows-874",
		    "x-windows-949",
		    "x-windows-950"
		    };

		java.util.Arrays.sort(legalCharsets);

%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Memex server name:</nobr></td><td class="value"><input type="text" size="64" name="memexservername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexServerName)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Memex server port:</nobr></td><td class="value"><input type="text" size="5" name="memexserverport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexServerPort)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Authorization user name:</nobr></td><td class="value"><input type="text" size="32" name="crawluser" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(crawlUser)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Authorization user password:</nobr></td><td class="value"><input type="password" size="32" name="crawluserpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(crawlUserPassword)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Character encoding:</nobr></td>
		<td class="value">
			<select name="characterencoding" size="10">
<%
		int k = 0;
		while (k < legalCharsets.length)
		{
			String charSet = legalCharsets[k++];
%>
				<option value="<%=com.metacarta.ui.util.Encoder.attributeEscape(charSet)%>" <%=(charSet.equals(characterEncoding))?" selected=\"selected\"":""%>><%=com.metacarta.ui.util.Encoder.bodyEscape(charSet)%></option>
<%
		}
%>
			</select>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for Memex Server tab
%>
<input type="hidden" name="memexservername" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexServerName)%>'/>
<input type="hidden" name="memexserverport" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexServerPort)%>'/>
<input type="hidden" name="crawluser" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(crawlUser)%>'/>
<input type="hidden" name="crawluserpassword" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(crawlUserPassword)%>'/>
<input type="hidden" name="characterencoding" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(characterEncoding)%>'/>
<%
	}

	// The "User Mapping" tab
	if (tabName.equals("User Mapping"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>User name regular expression:</nobr></td>
		<td class="value"><input type="text" size="40" name="usernameregexp" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(usernameRegexp)%>'/></td>
	</tr>
	<tr>
		<td class="description"><nobr>Memex user expression:</nobr></td>
		<td class="value"><input type="text" size="40" name="memexuserexpr" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexUserExpr)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
		// Hiddens for "User Mapping" tab
%>
	<input type="hidden" name="usernameregexp" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(usernameRegexp)%>'/>
	<input type="hidden" name="memexuserexpr" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(memexUserExpr)%>'/>
<%
	}
%>


