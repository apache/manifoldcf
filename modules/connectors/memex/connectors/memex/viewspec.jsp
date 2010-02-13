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
	int l = 0;
	boolean seenAny = false;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE))
		{
			String virtualServer = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VIRTUALSERVER);
			if (virtualServer == null)
				virtualServer = "";
			String entityPrefix = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_ENTITY);
			if (entityPrefix == null)
				entityPrefix = "";
			String entityDescription = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION);
			if (entityDescription == null)
				entityDescription = "";
			String fieldName = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDNAME);
			if (fieldName == null)
				fieldName = "";
			String operation = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_OPERATION);
			if (operation == null)
				operation = "";
			String fieldValue = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDVALUE);
			if (fieldValue == null)
				fieldValue = "";
                        
                        
			if (seenAny == false)
			{
				seenAny = true;
%>
			<td class="description"><nobr>Inclusion rules:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Virtual server</nobr></td>
						<td class="formcolumnheader"><nobr>Entity</nobr></td>
						<td class="formcolumnheader"><nobr>Criteria</nobr></td>
					</tr>

<%
			}
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr><%=(virtualServer.length()==0)?"(all)":com.metacarta.ui.util.Encoder.bodyEscape(virtualServer)%></nobr></td>
						<td class="formcolumncell"><nobr><%=(entityPrefix.length()==0)?"(all)":com.metacarta.ui.util.Encoder.bodyEscape(entityDescription)%></nobr></td>
						<td class="formcolumncell"><nobr><%=(fieldName.length()==0)?"(all)":com.metacarta.ui.util.Encoder.bodyEscape(fieldName+" "+operation+" "+fieldValue)%></nobr></td>
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
			<td colspan="2" class="message"><nobr>No rules specified (no records will be scanned)</nobr></td>
<%
	}
%>
		</tr>

		<tr><td class="separator" colspan="2"><hr/></td></tr>

		<tr>
<%
	seenAny = false;
	i = 0;
	int z = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ENTITY))
		{
			if (seenAny == false)
			{
%>
			<td class="description"><nobr>Entity tagged fields and metadata:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"><nobr>Entity name</nobr></td>
						<td class="formcolumnheader"><nobr>Tagged fields</nobr></td>
						<td class="formcolumnheader"><nobr>Metadata fields</nobr></td>
					</tr>
<%
				seenAny = true;
			}
			String strEntityName = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
			String strEntityDescription = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION);
			
%>
					<tr class='<%=((z % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell">
							<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(strEntityDescription)%></nobr>
						</td>
						<td class="formcolumncell">
<%
			int k = 0;
			while (k < sn.getChildCount())
			{
				SpecificationNode dsn = sn.getChild(k++);
				if (dsn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_PRIMARYFIELD))
				{
					String attrName = dsn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
%>
							<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(attrName)%></nobr><br/>
<%
				}
			}
%>
						</td>
						<td class="formcolumncell">
<%
			l = 0;
			k = 0;
			while (k < sn.getChildCount())
			{
				SpecificationNode dsn = sn.getChild(k++);
				if (dsn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_METAFIELD))
				{
					String attrName = dsn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
%>
							<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(attrName)%></nobr><br/>
<%

					l++;
				}
			}
			if (l == 0)
			{
%>
							<nobr>(No metadata attributes chosen)</nobr>
<%
			}
%>
						</td>
					</tr>
<%
			z++;
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
			<td colspan="2" class="message">No entities specified; nothing will be crawled</td>
<%
	}
%>
		</tr>
<%
	// Find whether security is on or off
	i = 0;
	boolean securityOn = true;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SECURITY))
		{
			String securityValue = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VALUE);
			if (securityValue.equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_VALUE_OFF))
				securityOn = false;
			else if (securityValue.equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_VALUE_ON))
				securityOn = true;
		}
	}
%>
		<tr><td class="separator" colspan="2"><hr/></td></tr>

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
		if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS))
		{
			if (seenAny == false)
			{
%>
		<tr><td class="description"><nobr>Access tokens:</nobr></td>
			<td class="value">

<%
				seenAny = true;
			}
			String token = sn.getAttributeValue(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_TOKEN);
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
	</table>

