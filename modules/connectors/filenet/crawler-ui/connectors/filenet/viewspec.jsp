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
		
	int i;
	Iterator iter;
	
	
%>
	<table class="displaytable">
	    <tr>
<%
	// Look for document classes
	HashMap documentClasses = new HashMap();
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
		{
			String value = sn.getAttributeValue(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
			org.apache.lcf.crawler.connectors.filenet.DocClassSpec spec = new org.apache.lcf.crawler.connectors.filenet.DocClassSpec(sn);
			documentClasses.put(value,spec);
		}
	}
	String[] sortedDocumentClasses = new String[documentClasses.size()];
	i = 0;
	iter = documentClasses.keySet().iterator();
	while (iter.hasNext())
	{
		sortedDocumentClasses[i++] = (String)iter.next();
	}
	java.util.Arrays.sort(sortedDocumentClasses);

	if (sortedDocumentClasses.length == 0)
	{
%>
		<td class="message" colspan="2"><nobr>No included document classes</nobr></td>
<%
	}
	else
	{
%>
		<td class="description"><nobr>Included document classes:</nobr></td>
		<td class="value">
			<table class="displaytable">
<%
		i = 0;
		while (i < sortedDocumentClasses.length)
		{
			String docclass = sortedDocumentClasses[i++];
%>
				<tr>
					<td class="description"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(docclass)%></nobr></td>
					<td class="boxcell">
						<table class="displaytable">
							<tr>
								<td class="description"><nobr>Metadata:</nobr></td>
								<td class="value">
<%
			org.apache.lcf.crawler.connectors.filenet.DocClassSpec fieldValues = (org.apache.lcf.crawler.connectors.filenet.DocClassSpec)documentClasses.get(docclass);
			if (fieldValues.getAllMetadata())
			{
%>
									<nobr>(all metadata values)</nobr>
<%
			}
			else
			{
				String[] valuesList = fieldValues.getMetadataFields();
				java.util.Arrays.sort(valuesList);
				int j = 0;
				while (j < valuesList.length)
				{
					String value = valuesList[j++];
%>
									<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(value)%></nobr><br/>
<%
				}
			}
%>
								</td>
							</tr>
							<tr>
								<td class="description"><nobr>Documents matching:</nobr></td>
								<td class="value">
<%
			int matchCount = fieldValues.getMatchCount();
			int q = 0;
			while (q < matchCount)
			{
				String matchType = fieldValues.getMatchType(q);
				String matchField = fieldValues.getMatchField(q);
				String matchValue = fieldValues.getMatchValue(q);
				q++;
%>
									<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchField)%> <%=matchType%> '<%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchValue)%>'</nobr><br/>
<%
			}
			
			if (q == 0)
			{
%>
									<nobr>(All documents in class '<%=org.apache.lcf.ui.util.Encoder.bodyEscape(docclass)%>')</nobr>
<%
			}
%>
								</td>
							</tr>
						</table>
					</td>
				</tr>
<%
		}
%>
			</table>
		</td>
<%
	}
%>
	    </tr>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
	    <tr>
<%
	// Look for mime types
	i = 0;
	HashMap mimeTypes = null;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
		{
			String value = sn.getAttributeValue(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
			if (mimeTypes == null)
				mimeTypes = new HashMap();
			mimeTypes.put(value,value);
		}
	}

	if (mimeTypes != null)
	{
		String[] sortedMimeTypes = new String[mimeTypes.size()];
		i = 0;
		iter = mimeTypes.keySet().iterator();
		while (iter.hasNext())
		{
			sortedMimeTypes[i++] = (String)iter.next();
		}
		java.util.Arrays.sort(sortedMimeTypes);

%>
		<td class="description"><nobr>Included mime types:</nobr></td>
		<td class="value">
<%
		i = 0;
		while (i < sortedMimeTypes.length)
		{
			String value = sortedMimeTypes[i++];
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(value)%></nobr><br/>
<%
		}
%>
		</td>
<%
	}
	else
        {
%>
                <td class="message" colspan="2"><nobr>No included mime types - ALL will be ingested</nobr></td>
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
		<td class="value"><%=(securityOn)?"Enabled":"Disabled"%></td>
	    </tr>
	    <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Go through looking for access tokens
	boolean seenAny = false;
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("access"))
		{
			if (seenAny == false)
			{
%>
	    <tr>
		<td class="description"><nobr>Access tokens:</nobr></td>
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


	</table>

