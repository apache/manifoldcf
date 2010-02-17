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
	// This file is included by every place that the specification information for the rss connector
	// needs to be viewed.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");

	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
%>
	<table class="displaytable">
<%
	int i = 0;
	boolean seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("feed"))
		{
			if (seenAny == false)
			{
%>
	    <tr>
		<td class="description"><nobr>RSS urls:</nobr></td>
		<td class="value">
<%
				seenAny = true;
			}
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("url"))%></nobr><br/>
<%
		}
	}

	if (seenAny)
	{
%>
		</td>
	    </tr>
<%
	}
	else
	{
%>
	    <tr><td class="message" colspan="2"><nobr>No RSS urls specified</nobr></td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	i = 0;
	int l = 0;
	seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("urlspec"))
		{
			if (l == 0)
			{
%>
	    <tr>
		<td class="description"><nobr>URL canonicalization:</nobr></td>
		<td class="value">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"><nobr>URL regexp</nobr></td>
					<td class="formcolumnheader"><nobr>Description</nobr></td>
					<td class="formcolumnheader"><nobr>Reorder?</nobr></td>
					<td class="formcolumnheader"><nobr>Remove JSP sessions?</nobr></td>
					<td class="formcolumnheader"><nobr>Remove ASP sessions?</nobr></td>
					<td class="formcolumnheader"><nobr>Remove PHP sessions?</nobr></td>
					<td class="formcolumnheader"><nobr>Remove BV sessions?</nobr></td>
				</tr>
<%
			}
			String regexpString = sn.getAttributeValue("regexp");
			String description = sn.getAttributeValue("description");
			if (description == null)
				description = "";
			String allowReorder = sn.getAttributeValue("reorder");
			if (allowReorder == null || allowReorder.length() == 0)
				allowReorder = "no";
			String allowJavaSessionRemoval = sn.getAttributeValue("javasessionremoval");
			if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
				allowJavaSessionRemoval = "no";
			String allowASPSessionRemoval = sn.getAttributeValue("aspsessionremoval");
			if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
				allowASPSessionRemoval = "no";
			String allowPHPSessionRemoval = sn.getAttributeValue("phpsessionremoval");
			if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
				allowPHPSessionRemoval = "no";
			String allowBVSessionRemoval = sn.getAttributeValue("bvsessionremoval");
			if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
				allowBVSessionRemoval = "no";
%>
				<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexpString)%></nobr></td>
					<td class="formcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></td>
					<td class="formcolumncell"><nobr><%=allowReorder%></nobr></td>
					<td class="formcolumncell"><nobr><%=allowJavaSessionRemoval%></nobr></td>
					<td class="formcolumncell"><nobr><%=allowASPSessionRemoval%></nobr></td>
					<td class="formcolumncell"><nobr><%=allowPHPSessionRemoval%></nobr></td>
					<td class="formcolumncell"><nobr><%=allowBVSessionRemoval%></nobr></td>
				</tr>
<%
			l++;
		}
	}
	if (l > 0)
	{
%>
			</table>
		</td>
	    </tr>
<%
	}
	else
	{
%>
	    <tr><td class="message" colspan="2"><nobr>No url canonicalization specified; will reorder all urls and remove all sessions</nobr></td></tr>
<%
	}

%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	i = 0;
	seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("map"))
		{
			if (seenAny == false)
			{
%>
	    <tr>
		<td class="description"><nobr>URL mappings:</nobr></td>
		<td class="value">
<%
				seenAny = true;
			}
			String match = sn.getAttributeValue("match");
			String map = sn.getAttributeValue("map");
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(match)%></nobr>
<%
			if (map != null && map.length() > 0)
			{
%>
				&nbsp;--&gt;&nbsp;<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(map)%></nobr>
<%
			}
%>
			<br/>
<%
		}
	}

	if (seenAny)
	{
%>
		</td>
	    </tr>
<%
	}
	else
	{
%>
	    <tr><td class="message" colspan="2"><nobr>No mappings specified; will accept all urls</nobr></td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	String feedTimeoutValue = "60";
	String feedRefetchValue = "60";
	String minFeedRefetchValue = "15";
	String badFeedRefetchValue = null;
	String dechromedMode = "none";
	String chromedMode = "use";
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("feedtimeout"))
		{
			feedTimeoutValue = sn.getAttributeValue("value");
		}
		else if (sn.getType().equals("feedrescan"))
		{
			feedRefetchValue = sn.getAttributeValue("value");
		}
		else if (sn.getType().equals("minfeedrescan"))
		{
			minFeedRefetchValue = sn.getAttributeValue("value");
		}
		else if (sn.getType().equals("badfeedrescan"))
		{
			badFeedRefetchValue = sn.getAttributeValue("value");
		}
		else if (sn.getType().equals("dechromedmode"))
		{
			dechromedMode = sn.getAttributeValue("mode");
		}
		else if (sn.getType().equals("chromedmode"))
		{
			chromedMode = sn.getAttributeValue("mode");
		}
	}
%>
	    <tr>
		<td class="description"><nobr>Feed connection timeout (seconds):</nobr></td>
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(feedTimeoutValue)%></td>
	    </tr>
	    <tr>
		<td class="description"><nobr>Default feed rescan interval (minutes):</nobr></td>
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(feedRefetchValue)%></td>
	    </tr>
	    <tr>
		<td class="description"><nobr>Minimum feed rescan interval (minutes):</nobr></td>
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(minFeedRefetchValue)%></td>
	    </tr>
	    <tr>
		<td class="description"><nobr>Bad feed rescan interval (minutes):</nobr></td>
		<td class="value"><%=((badFeedRefetchValue==null)?"(Default feed rescan value)":org.apache.lcf.ui.util.Encoder.bodyEscape(badFeedRefetchValue))%></td>
	    </tr>
	    
	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>
		<td class="description"><nobr>Dechromed content source:</nobr></td>
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(dechromedMode)%></td>
	    </tr>
	    <tr>
		<td class="description"><nobr>Chromed content:</nobr></td>
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(chromedMode)%></td>
	    </tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Go through looking for access tokens
	seenAny = false;
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("access"))
		{
			if (seenAny == false)
			{
%>
	    <tr><td class="description"><nobr>Access tokens:</nobr></td>
		<td class="value">

<%
				seenAny = true;
			}
			String token = sn.getAttributeValue("token");
%>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(token)%><br/>
<%		}
	}

	if (seenAny)
	{
%>
		</td>
	    </tr>
<%
	}
	else
	{
%>
	    <tr><td class="message" colspan="2"><nobr>No access tokens specified</nobr></td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Go through looking for metadata
	seenAny = false;
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("metadata"))
		{
			if (seenAny == false)
			{
%>

	    <tr><td class="description"><nobr>Metadata:</nobr></td>
		<td class="value">

<%
				seenAny = true;
			}
			String name = sn.getAttributeValue("name");
			String value = sn.getAttributeValue("value");
%>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(name)%>&nbsp;=&nbsp;<%=org.apache.lcf.ui.util.Encoder.bodyEscape(value)%><br/>
<%		}
	}

	if (seenAny)
	{
%>
		</td>
	    </tr>
<%
	}
	else
	{
%>
	    <tr><td class="message" colspan="2"><nobr>No metadata specified</nobr></td></tr>
<%
	}
%>
        </table>
