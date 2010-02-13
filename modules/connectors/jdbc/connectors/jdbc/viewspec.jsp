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
	// This file is included by every place that the specification information for the file system connector
	// needs to be viewed.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a body section.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");

	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");

	String idQuery = null;
	String versionQuery = null;
	String dataQuery = null;

	int i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
			idQuery = sn.getValue();
		else if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
			versionQuery = sn.getValue();
		else if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
			dataQuery = sn.getValue();
	}

	if (idQuery == null)
		idQuery = "";
	if (versionQuery == null)
		versionQuery = "";
	if (dataQuery == null)
		dataQuery = "";
%>

	<table class="displaytable">
		<tr>
			<td class="description"><nobr>Seeding query:</nobr></td>
			<td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(idQuery)%></td>
		</tr>
		<tr>
			<td class="description"><nobr>Version check query:</nobr></td>
			<td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(versionQuery)%></td>
		</tr>
		<tr>
			<td class="description"><nobr>Data query:</nobr></td>
			<td class="value"><%=com.metacarta.ui.util.Encoder.bodyEscape(dataQuery)%></td>
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
		<tr><td class="description"><nobr>Access tokens:</nobr></td>
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
		<tr><td class="message" colspan="2"><nobr>No access tokens specified</nobr></td></tr>
<%
	}
%>

	</table>

