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

	String[] x;
	String y;
	int i;

	if (variableContext.getParameter("hasdocumentclasses") != null)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			if (ds.getChild(i).getType().equals(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
				ds.removeChild(i);
			else
				i++;
		}
		x = variableContext.getParameterValues("documentclasses");
		if (x != null)
		{
		    i = 0;
		    while (i < x.length)
		    {
			String value = x[i++];
			SpecificationNode node = new SpecificationNode(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS);
			node.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,value);
			// Get the allmetadata value for this document class
			String allmetadata = variableContext.getParameter("allmetadata_"+value);
			if (allmetadata == null)
				allmetadata = "false";
			if (allmetadata.equals("true"))
				node.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_ALLMETADATA,allmetadata);
			else
			{
				String[] fields = variableContext.getParameterValues("metadatafield_"+value);
				if (fields != null)
				{
					int j = 0;
					while (j < fields.length)
					{
						String field = fields[j++];
						SpecificationNode sp2 = new SpecificationNode(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_METADATAFIELD);
						sp2.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,field);
						node.addChild(node.getChildCount(),sp2);
					}
				}
			}
			
			// Now, gather up matches too
			String matchCountString = variableContext.getParameter("matchcount_"+value);
			int matchCount = Integer.parseInt(matchCountString);
			int q = 0;
			while (q < matchCount)
			{
				String matchOp = variableContext.getParameter("matchop_"+value+"_"+Integer.toString(q));
				String matchType = variableContext.getParameter("matchtype_"+value+"_"+Integer.toString(q));
				String matchField = variableContext.getParameter("matchfield_"+value+"_"+Integer.toString(q));
				String matchValue = variableContext.getParameter("matchvalue_"+value+"_"+Integer.toString(q));
				if (matchOp == null || !matchOp.equals("Delete"))
				{
					SpecificationNode matchNode = new SpecificationNode(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MATCH);
					matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_MATCHTYPE,matchType);
					matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_FIELDNAME,matchField);
					if (matchValue == null)
						matchValue = "";
					matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,matchValue);
					node.addChild(node.getChildCount(),matchNode);
				}
				q++;
			}
			ds.addChild(ds.getChildCount(),node);
			
			// Look for the add operation
			String addMatchOp = variableContext.getParameter("matchop_"+value);
			if (addMatchOp != null && addMatchOp.equals("Add"))
			{
				String matchType = variableContext.getParameter("matchtype_"+value);
				String matchField = variableContext.getParameter("matchfield_"+value);
				String matchValue = variableContext.getParameter("matchvalue_"+value);
				SpecificationNode matchNode = new SpecificationNode(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MATCH);
				matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_MATCHTYPE,matchType);
				matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_FIELDNAME,matchField);
				if (matchValue == null)
					matchValue = "";
				matchNode.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,matchValue);
				node.addChild(node.getChildCount(),matchNode);
			}
			
		    }
		}
	}
	
	if (variableContext.getParameter("hasmimetypes") != null)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			if (ds.getChild(i).getType().equals(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
				ds.removeChild(i);
			else
				i++;
		}
		x = variableContext.getParameterValues("mimetypes");
		if (x != null)
		{
		    i = 0;
		    while (i < x.length)
		    {
			String value = x[i++];
			SpecificationNode node = new SpecificationNode(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE);
			node.setAttribute(org.apache.lcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE,value);
			ds.addChild(ds.getChildCount(),node);
		    }
		}
	}

	y = variableContext.getParameter("specsecurity");
	if (y != null)
	{
		// Delete all security entries first
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("security"))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode node = new SpecificationNode("security");
		node.setAttribute("value",y);
		ds.addChild(ds.getChildCount(),node);

	}

	y = variableContext.getParameter("tokencount");
	if (y != null)
	{
		// Delete all file specs first
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("access"))
				ds.removeChild(i);
			else
				i++;
		}

		int accessCount = Integer.parseInt(y);
		i = 0;
		while (i < accessCount)
		{
			String accessDescription = "_"+Integer.toString(i);
			String accessOpName = "accessop"+accessDescription;
			y = variableContext.getParameter(accessOpName);
			if (y != null && y.equals("Delete"))
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
