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
		
	// Display path rules
%>
	<table class="displaytable">
		<tr>

<%
	int i = 0;
	int l = 0;
	boolean seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("startpoint"))
		{
			String site = sn.getAttributeValue("site");
			String lib = sn.getAttributeValue("lib");
			String siteLib = site + "/" + lib + "/";

			// Old-style path.
			// There will be an inclusion or exclusion rule for every entry in the path rules for this startpoint, so loop through them.
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode node = sn.getChild(j++);
				if (node.getType().equals("include") || node.getType().equals("exclude"))
				{
					String matchPart = node.getAttributeValue("match");
					String ruleType = node.getAttributeValue("type");
					// Whatever happens, we're gonna display a rule here, so go ahead and set that up.
					if (seenAny == false)
					{
						seenAny = true;
%>
			<td class="description"><nobr>Path rules:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Rule type</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
					</tr>
<%
					}
					String action = node.getType();
					// Display the path rule corresponding to this match rule
					// The first part comes from the site/library
					String completePath;
					// The match applies to only the file portion.  Therefore, there are TWO rules needed to emulate: sitelib/<match>, and sitelib/*/<match>
					completePath = siteLib + matchPart;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(completePath)%></nobr></td>
						<td class="formcolumncell"><nobr>file</nobr></td>
						<td class="formcolumncell"><nobr><%=action%></nobr></td>
					</tr>
<%
					l++;
					if (ruleType.equals("file") && !matchPart.startsWith("*"))
					{
						completePath = siteLib + "*/" + matchPart;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(completePath)%></nobr></td>
						<td class="formcolumncell"><nobr>file</nobr></td>
						<td class="formcolumncell"><nobr><%=action%></nobr></td>
					</tr>
<%
						l++;
					}
				}
			}
		}
		else if (sn.getType().equals("pathrule"))
		{
			String path = sn.getAttributeValue("match");
			String action = sn.getAttributeValue("action");
			String ruleType = sn.getAttributeValue("type");
			if (seenAny == false)
			{
				seenAny = true;
%>
			<td class="description"><nobr>Path rules:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Rule type</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
					</tr>
<%
			}
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(path)%></nobr></td>
						<td class="formcolumncell"><nobr><%=ruleType%></nobr></td>
						<td class="formcolumncell"><nobr><%=action%></nobr></td>
					</tr>
<%
			l++;
		}
	}
	if (seenAny)
	{
%>
				</table>
			</td>
<%
	}
	else
	{
%>
			<td colspan="2" class="message"><nobr>No documents will be included</nobr></td>
<%
	}
%>
		</tr>
<%
	
	// Finally, display metadata rules
%>
		<tr>

<%
	i = 0;
	l = 0;
	seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("startpoint"))
		{
			// Old-style
			String site = sn.getAttributeValue("site");
			String lib = sn.getAttributeValue("lib");
			String path = site + "/" + lib + "/*";
			
			String allmetadata = sn.getAttributeValue("allmetadata");
			StringBuffer metadataFieldList = new StringBuffer();
			if (allmetadata == null || !allmetadata.equals("true"))
			{
				int j = 0;
				while (j < sn.getChildCount())
				{
					SpecificationNode node = sn.getChild(j++);
					if (node.getType().equals("metafield"))
					{
						String value = node.getAttributeValue("value");
						if (metadataFieldList.length() > 0)
							metadataFieldList.append(", ");
						metadataFieldList.append(value);
					}
				}
				allmetadata = "false";
			}
			if (allmetadata.equals("true") || metadataFieldList.length() > 0)
			{
				if (seenAny == false)
				{
					seenAny = true;
%>
			<td class="description"><nobr>Metadata:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
						<td class="formcolumnheader"><nobr>All metadata?</nobr></td>
						<td class="formcolumnheader"><nobr>Fields</nobr></td>
					</tr>
<%
				}
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(path)%></nobr></td>
						<td class="formcolumncell"><nobr>include</nobr></td>
						<td class="formcolumncell"><nobr><%=allmetadata%></nobr></td>
						<td class="formcolumncell"><%=com.metacarta.ui.util.Encoder.bodyEscape(metadataFieldList.toString())%></td>
					</tr>
<%
				l++;
			}
		}
		else if (sn.getType().equals("metadatarule"))
		{
			String path = sn.getAttributeValue("match");
			String action = sn.getAttributeValue("action");
			String allmetadata = sn.getAttributeValue("allmetadata");
			StringBuffer metadataFieldList = new StringBuffer();
			if (action.equals("include"))
			{
				if (allmetadata == null || !allmetadata.equals("true"))
				{
					int j = 0;
					while (j < sn.getChildCount())
					{
						SpecificationNode node = sn.getChild(j++);
						if (node.getType().equals("metafield"))
						{
							String fieldName = node.getAttributeValue("value");
							if (metadataFieldList.length() > 0)
								metadataFieldList.append(", ");
							metadataFieldList.append(fieldName);
						}
					}
					allmetadata = "false";
				}
			}
			else
				allmetadata = "";
			if (seenAny == false)
			{
				seenAny = true;
%>
			<td class="description"><nobr>Metadata:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
						<td class="formcolumnheader"><nobr>All metadata?</nobr></td>
						<td class="formcolumnheader"><nobr>Fields</nobr></td>
					</tr>
<%
			}
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(path)%></nobr></td>
						<td class="formcolumncell"><nobr><%=action%></nobr></td>
						<td class="formcolumncell"><nobr><%=allmetadata%></nobr></td>
						<td class="formcolumncell"><%=com.metacarta.ui.util.Encoder.bodyEscape(metadataFieldList.toString())%></td>
					</tr>
<%
			l++;
		}
	}
	if (seenAny)
	{
%>
				</table>
			</td>
<%
	}
	else
	{
%>
			<td colspan="2" class="message"><nobr>No metadata will be included</nobr></td>
<%
	}
%>
		</tr>
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
			<td class="description"><nobr>Security:</nobr></td>
			<td class="value"><nobr><%=(securityOn)?"Enabled":"Disabled"%></nobr></td>
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
				<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(token)%></nobr><br/>
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
			<td class="description"><nobr>Path metadata attribute name:</nobr></td>
			<td class="value"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(pathNameAttribute)%></nobr></td>
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
	com.metacarta.crawler.connectors.sharepoint.MatchMap matchMap = new com.metacarta.crawler.connectors.sharepoint.MatchMap();
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
			<td class="description"><nobr>Path-value mapping:</nobr></td>
			<td class="value">
				<table class="displaytable">
<%
	    i = 0;
	    while (i < matchMap.getMatchCount())
	    {
		String matchString = matchMap.getMatchString(i);
		String replaceString = matchMap.getReplaceString(i);
%>
				<tr><td class="value"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></nobr></td><td class="value">==></td><td class="value"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></nobr></td></tr>
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

 