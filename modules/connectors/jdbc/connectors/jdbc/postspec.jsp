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
	// This file is included by every place that edited specification information for the file system connector
	// is posted upon submit.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".  The DocumentSpecification object should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");

	if (ds == null)
		System.out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		System.out.println("No repository connection!!!");

	String idQuery = variableContext.getParameter("idquery");
	String versionQuery = variableContext.getParameter("versionquery");
	String dataQuery = variableContext.getParameter("dataquery");

	SpecificationNode sn;
	if (idQuery != null)
	{
		int i = 0;
		while (i < ds.getChildCount())
		{
			if (ds.getChild(i).getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
				ds.removeChild(i);
			else
				i++;
		}
		sn = new SpecificationNode(com.metacarta.crawler.connectors.jdbc.JDBCConstants.idQueryNode);
		sn.setValue(idQuery);
		ds.addChild(ds.getChildCount(),sn);
	}
	if (versionQuery != null)
	{
		int i = 0;
		while (i < ds.getChildCount())
		{
			if (ds.getChild(i).getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
				ds.removeChild(i);
			else
				i++;
		}
		sn = new SpecificationNode(com.metacarta.crawler.connectors.jdbc.JDBCConstants.versionQueryNode);
		sn.setValue(versionQuery);
		ds.addChild(ds.getChildCount(),sn);
	}
	if (dataQuery != null)
	{
		int i = 0;
		while (i < ds.getChildCount())
		{
			if (ds.getChild(i).getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
				ds.removeChild(i);
			else
				i++;
		}
		sn = new SpecificationNode(com.metacarta.crawler.connectors.jdbc.JDBCConstants.dataQueryNode);
		sn.setValue(dataQuery);
		ds.addChild(ds.getChildCount(),sn);
	}
	
	String xc = variableContext.getParameter("tokencount");
	if (xc != null)
	{
		// Delete all tokens first
		int i = 0;
		while (i < ds.getChildCount())
		{
			sn = ds.getChild(i);
			if (sn.getType().equals("access"))
				ds.removeChild(i);
			else
				i++;
		}

		int accessCount = Integer.parseInt(xc);
		i = 0;
		while (i < accessCount)
		{
			String accessDescription = "_"+Integer.toString(i);
			String accessOpName = "accessop"+accessDescription;
			xc = variableContext.getParameter(accessOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Next row
				i++;
				continue;
			}
			// Get the stuff we need
			String accessSpec = variableContext.getParameter("spectoken"+accessDescription);
			SpecificationNode node = new SpecificationNode("access");
			node.setAttribute("token",accessSpec);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		String op = variableContext.getParameter("accessop");
		if (op != null && op.equals("Add"))
		{
			String accessspec = variableContext.getParameter("spectoken");
			SpecificationNode node = new SpecificationNode("access");
			node.setAttribute("token",accessspec);
			ds.addChild(ds.getChildCount(),node);
		}
	}

%>
