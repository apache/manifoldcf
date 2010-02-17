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
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION))
		{
			if (seenAny == false)
			{
				seenAny = true;
%>
			<td class="description">Cabinet/folder paths:</td>
			<td class="value">
<%
			}
%>
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))%><br/>
<%
		}
	}

	if (seenAny)
	{
%>
			</td>
<%
	}
	else
	{
%>
			<td colspan="2" class="message">No cabinet/folder paths specified (everything in docbase will be scanned)</td>
<%
	}
%>
		</tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
<%
	seenAny = false;
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_OBJECTTYPE))
		{
			if (seenAny == false)
			{
%>
			<td class="description"><nobr>Object types:</nobr></td>
			<td class="value">
			  <table class="displaytable">
<%
				seenAny = true;
			}
			String strObjectType = sn.getAttributeValue("token");
			String isAll = sn.getAttributeValue("all");
%>
			  <tr>
			    <td class="value">
			      <%=org.apache.lcf.ui.util.Encoder.bodyEscape(strObjectType)%>
			    </td>
			    <td class="value">
<%
			if (isAll != null && isAll.equals("true"))
				out.println("<nobr>(all metadata attributes)</nobr>");
			else
			{
			    int k = 0;
			    while (k < sn.getChildCount())
			    {
				SpecificationNode dsn = sn.getChild(k++);
				if (dsn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_ATTRIBUTENAME))
				{
					String attrName = dsn.getAttributeValue("attrname");
%>
			      <%=org.apache.lcf.ui.util.Encoder.bodyEscape(attrName)%><br/>
<%
				}
			    }
			}
%>
			    </td>
			  </tr>
<%
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
			<td colspan="2" class="message">No document types specified</td>
<%
	}
%>
		</tr>


	    <tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
<%
	seenAny = false;
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_FORMAT))
		{
			if (seenAny == false)
			{
%>
			<td class="description"><nobr>Content types:</nobr></td>
			<td class="value">
<%
				seenAny = true;
			}
			String strContentType = sn.getAttributeValue("value");
%>
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(strContentType)%><br/>
<%
		}
	}
	if (seenAny)
	{
%>
			</td>
<%
	}
	else
	{
%>
			<td colspan="2" class="message">No mime types specified - ALL will be ingested</td>
<%
	}
%>
		</tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Find max document length
	i = 0;
	String maxDocumentLength = "unlimited";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_MAXLENGTH))
		{
			maxDocumentLength = sn.getAttributeValue("value");
		}
	}
%>

	    <tr>
		<td class="description"><nobr>Maximum document length:</nobr></td>
		<td class="value"><%=maxDocumentLength%></td>
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
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHNAMEATTRIBUTE))
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
		<td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(pathNameAttribute)%></td>
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
	org.apache.lcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.lcf.crawler.connectors.DCTM.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHMAP))
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
		    <tr><td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchString)%></td><td class="value">--></td><td class="value"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(replaceString)%></td></tr>
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

