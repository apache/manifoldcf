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

	String x = variableContext.getParameter("pathcount");
	if (x != null)
	{
		// Delete all path specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION))
				ds.removeChild(i);
			else
				i++;
		}

		// Find out how many children were sent
		int pathCount = Integer.parseInt(x);
		// Gather up these
		i = 0;
		while (i < pathCount)
		{
			String pathDescription = "_"+Integer.toString(i);
			String pathOpName = "pathop"+pathDescription;
			x = variableContext.getParameter(pathOpName);
			if (x != null && x.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Path inserts won't happen until the very end
			String path = variableContext.getParameter("specpath"+pathDescription);
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION);
			node.setAttribute("path",path);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// See if there's a global add operation
		String op = variableContext.getParameter("pathop");
		if (op != null && op.equals("Add"))
		{
			String path = variableContext.getParameter("specpath");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION);
			node.setAttribute("path",path);
			ds.addChild(ds.getChildCount(),node);
		}
		else if (op != null && op.equals("Up"))
		{
			// Strip off end
			String path = variableContext.getParameter("specpath");
			int k = path.lastIndexOf("/");
			if (k != -1)
				path = path.substring(0,k);
			if (path.length() == 0)
				path = "/";
			threadContext.save("specpath",path);
		}
		else if (op != null && op.equals("AddToPath"))
		{
			String path = variableContext.getParameter("specpath");
			String addon = variableContext.getParameter("pathaddon");
			if (addon != null && addon.length() > 0)
			{
				if (path.length() == 1)
					path = "/" + addon;
				else
					path += "/" + addon;
			}
			threadContext.save("specpath",path);
		}
	}

	x = variableContext.getParameter("specsecurity");
	if (x != null)
	{
		// Delete all security entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("security"))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode node = new SpecificationNode("security");
		node.setAttribute("value",x);
		ds.addChild(ds.getChildCount(),node);

	}

	x = variableContext.getParameter("tokencount");
	if (x != null)
	{
		// Delete all file specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("access"))
				ds.removeChild(i);
			else
				i++;
		}

		int accessCount = Integer.parseInt(x);
		i = 0;
		while (i < accessCount)
		{
			String accessDescription = "_"+Integer.toString(i);
			String accessOpName = "accessop"+accessDescription;
			x = variableContext.getParameter(accessOpName);
			if (x != null && x.equals("Delete"))
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

	String[] y = variableContext.getParameterValues("specfiletype");
	if (y != null)
	{
		// Delete all file specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_OBJECTTYPE))
				ds.removeChild(i);
			else
				i++;
		}

		// Loop through specs
		i = 0;
		while (i < y.length)
		{
			String fileType = y[i++];
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_OBJECTTYPE);
			node.setAttribute("token",fileType);
			String isAll = variableContext.getParameter("specfileallattrs_"+fileType);
			if (isAll != null)
				node.setAttribute("all",isAll);
			String[] z = variableContext.getParameterValues("specfileattrs_"+fileType);
			if (z != null)
			{
				int k = 0;
				while (k < z.length)
				{
					SpecificationNode attrNode = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_ATTRIBUTENAME);
					attrNode.setAttribute("attrname",z[k++]);
					node.addChild(node.getChildCount(),attrNode);
				}
			}
			ds.addChild(ds.getChildCount(),node);
		}
	}

	y = variableContext.getParameterValues("specmimetype");
	if (y != null)
	{
		// Delete all file specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_FORMAT))
				ds.removeChild(i);
			else
				i++;
		}

		// Loop through specs
		i = 0;
		while (i < y.length)
		{
			String fileType = y[i++];
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_FORMAT);
			node.setAttribute("value",fileType);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	x = variableContext.getParameter("specmaxdoclength");
	if (x != null)
	{
		// Delete all security entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_MAXLENGTH))
				ds.removeChild(i);
			else
				i++;
		}

		if (x.length() > 0)
		{
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_MAXLENGTH);
			node.setAttribute("value",x);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	String xc = variableContext.getParameter("specpathnameattribute");
	if (xc != null)
	{
		// Delete old one
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHNAMEATTRIBUTE))
				ds.removeChild(i);
			else
				i++;
		}
		if (xc.length() > 0)
		{
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHNAMEATTRIBUTE);
			node.setAttribute("value",xc);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	xc = variableContext.getParameter("specmappingcount");
	if (xc != null)
	{
		// Delete old spec
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHMAP))
				ds.removeChild(i);
			else
				i++;
		}

		// Now, go through the data and assemble a new list.
		int mappingCount = Integer.parseInt(xc);

		// Gather up these
		i = 0;
		while (i < mappingCount)
		{
			String pathDescription = "_"+Integer.toString(i);
			String pathOpName = "specmappingop"+pathDescription;
			xc = variableContext.getParameter(pathOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Inserts won't happen until the very end
			String match = variableContext.getParameter("specmatch"+pathDescription);
			String replace = variableContext.getParameter("specreplace"+pathDescription);
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHMAP);
			node.setAttribute("match",match);
			node.setAttribute("replace",replace);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// Check for add
		xc = variableContext.getParameter("specmappingop");
		if (xc != null && xc.equals("Add"))
		{
			String match = variableContext.getParameter("specmatch");
			String replace = variableContext.getParameter("specreplace");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHMAP);
			node.setAttribute("match",match);
			node.setAttribute("replace",replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}

%>
