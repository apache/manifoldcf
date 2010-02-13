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
	// This file is included by every place that the specification information for the file system connector
	// needs to be viewed.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a body section.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");
%>

<%
	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
%>
	<table class="displaytable">
	    <tr>
<%
	int i = 0;
	boolean seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("startpoint"))
		{
			if (seenAny == false)
			{
%>
		<td class="description">Roots:</td>
		<td class="value">
<%
				seenAny = true;
			}
%>
			<%=com.metacarta.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))%><br/>
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
	    <tr><td class="message" colspan="2">No start points specified</td></tr>
<%
	}

%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%

	seenAny = false;
	// Go through looking for include or exclude file specs
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("include") || sn.getType().equals("exclude"))
		{
			if (seenAny == false)
			{
%>
		<tr><td class="description">File specs:</td>
			<td class="value">
<%
				seenAny = true;
			}
			String filespec = sn.getAttributeValue("filespec");
%>
				<%=sn.getType().equals("include")?"Include file:":""%>
				<%=sn.getType().equals("exclude")?"Exclude file:":""%>
				<%=com.metacarta.ui.util.Encoder.bodyEscape(filespec)%><br/>
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
	    <tr><td class="message" colspan="2">No file specs specified</td></tr>

<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>

<%
	// Find whether security is on or off
	i = 0;
	boolean securityOn = true;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("security"))
		{
			String securityValue = sn.getAttributeValue("value");
			if (securityValue.equals("off"))
				securityOn = false;
			else if (securityValue.equals("on"))
				securityOn = true;
		}
	}
%>

	    <tr>
		<td class="description">Security:</td>
		<td class="value"><%=(securityOn)?"Enabled":"Disabled"%></td>
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
		<tr><td class="description">Access tokens:</td>
			<td class="value">

<%
				seenAny = true;
			}
			String token = sn.getAttributeValue("token");
%>
				<%=com.metacarta.ui.util.Encoder.bodyEscape(token)%><br/>
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
	    <tr><td class="message" colspan="2">No access tokens specified</td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	i = 0;
	String allMetadata = "Only specified metadata will be ingested";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("allmetadata"))
		{
			String value = sn.getAttributeValue("all");
			if (value != null && value.equals("true"))
			{
				allMetadata="All document metadata will be ingested";
			}
		}
	}
%>
		<tr>
			<td class="description"><nobr>Metadata specification:</nobr></td>
			<td class="value"><nobr><%=allMetadata%></nobr></td>
		</tr>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Go through looking for metadata spec
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
		<tr><td class="description"><nobr>Specific metadata:</nobr></td>
			<td class="value">

<%
				seenAny = true;
			}
			String category = sn.getAttributeValue("category");
			String attribute = sn.getAttributeValue("attribute");
			String isAll = sn.getAttributeValue("all");
%>
				<%=com.metacarta.ui.util.Encoder.bodyEscape(category)%>:<%=(isAll!=null&&isAll.equals("true"))?"(All metadata attributes)":com.metacarta.ui.util.Encoder.bodyEscape(attribute)%><br/>
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
	    <tr><td class="message" colspan="2">No metadata specified</td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Find the path-name metadata attribute name
	i = 0;
	String pathNameAttribute = "";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("pathnameattribute"))
		{
			pathNameAttribute = sn.getAttributeValue("value");
		}
	}
%>
	    <tr>
<%
	if (pathNameAttribute.length() > 0)
	{
%>
		<td class="description">Path-name metadata attribute:</td>
		<td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(pathNameAttribute)%></td>
<%
	}
	else
	{
%>
		<td class="message" colspan="2">No path-name metadata attribute specified</td>
<%
	}
%>
	    </tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>

<%
	// Find the path-value mapping data
	i = 0;
	com.metacarta.crawler.connectors.livelink.MatchMap matchMap = new com.metacarta.crawler.connectors.livelink.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("pathmap"))
		{
			String pathMatch = sn.getAttributeValue("match");
			String pathReplace = sn.getAttributeValue("replace");
			matchMap.appendMatchPair(pathMatch,pathReplace);
		}
	}
	if (matchMap.getMatchCount() > 0)
	{
%>
		<td class="description">Path-value mapping:</td>
		<td class="value">
		    <table class="displaytable">
<%
	    i = 0;
	    while (i < matchMap.getMatchCount())
	    {
		String matchString = matchMap.getMatchString(i);
		String replaceString = matchMap.getReplaceString(i);
%>
		    <tr><td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></td><td class="value">--></td><td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></td></tr>
<%
		i++;
	    }
%>
		    </table>
		</td>
<%
	}
	else
	{
%>
		    <td class="message" colspan="2">No mappings specified</td>
<%
	}
%>
	    </tr>

	</table>

