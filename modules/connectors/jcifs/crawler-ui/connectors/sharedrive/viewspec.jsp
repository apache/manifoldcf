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
<%
	int i = 0;
	boolean seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode spn = ds.getChild(i++);
		if (spn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT))
		{
			if (seenAny == false)
			{
				seenAny = true;
%>
				<!-- tr><td>Include root</td><td>Rules</td></tr -->
<%
			}
%>
			<tr><td class="description"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(spn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH))+":"%></nobr></td>
			<td class="value">
<%
			int j = 0;
			while (j < spn.getChildCount())
			{
				SpecificationNode sn = spn.getChild(j++);
				// This is "include" or "exclude"
				String nodeFlavor = sn.getType();
				// This is the file/directory name match
				String filespec = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC);
				// This has a value of null, "", "file", or "directory".
				String nodeType = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE);
				if (nodeType == null)
					nodeType = "";
				// This has a value of null, "", "yes", or "no".
				String ingestableFlag = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE);
				if (ingestableFlag == null)
					ingestableFlag = "";

%>
				<nobr><%=Integer.toString(j)%>.
				<%=(nodeFlavor.equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_INCLUDE))?"Include":""%>
				<%=(nodeFlavor.equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_EXCLUDE))?"Exclude":""%>
				<%=(ingestableFlag.equals("yes"))?"&nbsp;indexable":""%>
				<%=(ingestableFlag.equals("no"))?"&nbsp;un-indexable":""%>
				<%=(nodeType.equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.VALUE_FILE))?"&nbsp;file(s)":""%>
				<%=(nodeType.equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.VALUE_DIRECTORY))?"&nbsp;directory(s)":""%>
				<%=(nodeType.equals(""))?"&nbsp;file(s)&nbsp;or&nbsp;directory(s)":""%>&nbsp;matching&nbsp;
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(filespec)%></nobr><br/>
<%
			}
%>
			</td></tr>
<%
		}
	}
	if (seenAny == false)
	{
%>
		<tr><td class="message" colspan="2">No documents specified</td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>

<%
	// Find whether security is on or off
	i = 0;
	boolean securityOn = true;
	boolean shareSecurityOn = true;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SECURITY))
		{
			String securityValue = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
			if (securityValue.equals("off"))
				securityOn = false;
			else if (securityValue.equals("on"))
				securityOn = true;
		}
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SHARESECURITY))
		{
			String securityValue = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
			if (securityValue.equals("off"))
				shareSecurityOn = false;
			else if (securityValue.equals("on"))
				shareSecurityOn = true;
		}
	}
%>

	    <tr>
		<td class="description"><nobr>File security:</nobr></td>
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
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS))
		{
			if (seenAny == false)
			{
%>
		<tr><td class="description"><nobr>File access tokens:</nobr></td>
			<td class="value">

<%
				seenAny = true;
			}
			String token = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TOKEN);
%>
				<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(token)%></nobr><br/>
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
	    <tr><td class="message" colspan="2">No file access tokens specified</td></tr>
<%
	}
%>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>
		<td class="description"><nobr>Share security:</nobr></td>
		<td class="value"><nobr><%=(shareSecurityOn)?"Enabled":"Disabled"%></nobr></td>
	    </tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Find the path-name metadata attribute name
	i = 0;
	String pathNameAttribute = "";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHNAMEATTRIBUTE))
		{
			pathNameAttribute = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
		}
	}
%>
	    <tr>
<%
	if (pathNameAttribute.length() > 0)
	{
%>
		<td class="description"><nobr>Path-name metadata attribute:</nobr></td>
		<td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(pathNameAttribute)%></nobr></td>
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
	org.apache.lcf.crawler.connectors.sharedrive.MatchMap matchMap = new org.apache.lcf.crawler.connectors.sharedrive.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHMAP))
		{
			String pathMatch = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
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
		    <tr><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchString)%></nobr></td><td class="value">==></td><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(replaceString)%></nobr></td></tr>
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

	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>

<%
	// Find the file name mapping data
	i = 0;
	org.apache.lcf.crawler.connectors.sharedrive.MatchMap fileMap = new org.apache.lcf.crawler.connectors.sharedrive.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_FILEMAP))
		{
			String pathMatch = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
			fileMap.appendMatchPair(pathMatch,pathReplace);
		}
	}
	if (fileMap.getMatchCount() > 0)
	{
%>
		<td class="description"><nobr>File name mapping:</nobr></td>
		<td class="value">
		    <table class="displaytable">
<%
	    i = 0;
	    while (i < fileMap.getMatchCount())
	    {
		String matchString = fileMap.getMatchString(i);
		String replaceString = fileMap.getReplaceString(i);
%>
		    <tr><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchString)%></nobr></td><td class="value">==></td><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(replaceString)%></nobr></td></tr>
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
		    <td class="message" colspan="2">No file name mappings specified</td>
<%
	}
%>
	    </tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>

<%
	// Find the url mapping data
	i = 0;
	org.apache.lcf.crawler.connectors.sharedrive.MatchMap uriMap = new org.apache.lcf.crawler.connectors.sharedrive.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_URIMAP))
		{
			String pathMatch = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
			uriMap.appendMatchPair(pathMatch,pathReplace);
		}
	}
	if (uriMap.getMatchCount() > 0)
	{
%>
		<td class="description"><nobr>URL mapping:</nobr></td>
		<td class="value">
		    <table class="displaytable">
<%
	    i = 0;
	    while (i < uriMap.getMatchCount())
	    {
		String matchString = uriMap.getMatchString(i);
		String replaceString = uriMap.getReplaceString(i);
%>
		    <tr><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchString)%></nobr></td><td class="value">==></td><td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(replaceString)%></nobr></td></tr>
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
		    <td class="message" colspan="2">No URL mappings specified; will produce a file IRI</td>
<%
	}
%>
	    </tr>

	    <tr><td class="separator" colspan="2"><hr/></td></tr>

	    <tr>
		<td class="description"><nobr>Maximum document length:</nobr></td>
		<td class="value"><nobr>
<%
	// Find the path-value mapping data
	i = 0;
	String maxLength = null;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.NODE_MAXLENGTH))
		{
			maxLength = sn.getAttributeValue(org.apache.lcf.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
		}
	}
	if (maxLength == null || maxLength.length() == 0)
		maxLength = "Unlimited";
%>
			<%=maxLength%>
		</nobr></td>
	    </tr>
	</table>

