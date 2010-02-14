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

	String xc = variableContext.getParameter("pathcount");
	if (xc != null)
	{
		// Delete all path specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("startpoint"))
				ds.removeChild(i);
			else
				i++;
		}

		// Find out how many children were sent
		int pathCount = Integer.parseInt(xc);
		// Gather up these
		i = 0;
		while (i < pathCount)
		{
			String pathDescription = "_"+Integer.toString(i);
			String pathOpName = "pathop"+pathDescription;
			xc = variableContext.getParameter(pathOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Path inserts won't happen until the very end
			String path = variableContext.getParameter("specpath"+pathDescription);
			SpecificationNode node = new SpecificationNode("startpoint");
			node.setAttribute("path",path);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// See if there's a global add operation
		String op = variableContext.getParameter("pathop");
		if (op != null && op.equals("Add"))
		{
			String path = variableContext.getParameter("specpath");
			SpecificationNode node = new SpecificationNode("startpoint");
			node.setAttribute("path",path);
			ds.addChild(ds.getChildCount(),node);
		}
		else if (op != null && op.equals("Up"))
		{
			// Strip off end
			String path = variableContext.getParameter("specpath");
			int lastSlash = -1;
			int k = 0;
			while (k < path.length())
			{
				char x = path.charAt(k++);
				if (x == '/')
				{
					lastSlash = k-1;
					continue;
				}
				if (x == '\\')
					k++;
			}
			if (lastSlash == -1)
				path = "";
			else
				path = path.substring(0,lastSlash);
			threadContext.save("specpath",path);
		}
		else if (op != null && op.equals("AddToPath"))
		{
			String path = variableContext.getParameter("specpath");
			String addon = variableContext.getParameter("pathaddon");
			if (addon != null && addon.length() > 0)
			{
				StringBuffer sb = new StringBuffer();
				int k = 0;
				while (k < addon.length())
				{
					char x = addon.charAt(k++);
					if (x == '/' || x == '\\' || x == ':')
						sb.append('\\');
					sb.append(x);
				}
				if (path.length() == 0)
					path = sb.toString();
				else
					path += "/" + sb.toString();
			}
			threadContext.save("specpath",path);
		}
	}

	xc = variableContext.getParameter("filecount");
	if (xc != null)
	{
		// Delete all file specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("include") || sn.getType().equals("exclude"))
				ds.removeChild(i);
			else
				i++;
		}

		int fileCount = Integer.parseInt(xc);
		i = 0;
		while (i < fileCount)
		{
			String fileSpecDescription = "_"+Integer.toString(i);
			String fileOpName = "fileop"+fileSpecDescription;
			xc = variableContext.getParameter(fileOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Next row
				i++;
				continue;
			}
			// Get the stuff we need
			String filespecType = variableContext.getParameter("specfiletype"+fileSpecDescription);
			String filespec = variableContext.getParameter("specfile"+fileSpecDescription);
			SpecificationNode node = new SpecificationNode(filespecType);
			node.setAttribute("filespec",filespec);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		String op = variableContext.getParameter("fileop");
		if (op != null && op.equals("Add"))
		{
			String filespec = variableContext.getParameter("specfile");
			String filespectype = variableContext.getParameter("specfiletype");
			SpecificationNode node = new SpecificationNode(filespectype);
			node.setAttribute("filespec",filespec);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	xc = variableContext.getParameter("specsecurity");
	if (xc != null)
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
		node.setAttribute("value",xc);
		ds.addChild(ds.getChildCount(),node);

	}

	xc = variableContext.getParameter("tokencount");
	if (xc != null)
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

	xc = variableContext.getParameter("specallmetadata");
	if (xc != null)
	{
		// Look for the 'all metadata' checkbox
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("allmetadata"))
				ds.removeChild(i);
			else
				i++;
		}

		if (xc.equals("true"))
		{
			SpecificationNode newNode = new SpecificationNode("allmetadata");
			newNode.setAttribute("all",xc);
			ds.addChild(ds.getChildCount(),newNode);
		}
	}

	xc = variableContext.getParameter("metadatacount");
	if (xc != null)
	{

		// Delete all metadata specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("metadata"))
				ds.removeChild(i);
			else
				i++;
		}

		// Find out how many children were sent
		int metadataCount = Integer.parseInt(xc);
		// Gather up these
		i = 0;
		while (i < metadataCount)
		{
			String pathDescription = "_"+Integer.toString(i);
			String pathOpName = "metadataop"+pathDescription;
			xc = variableContext.getParameter(pathOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Metadata inserts won't happen until the very end
			String category = variableContext.getParameter("speccategory"+pathDescription);
			String attributeName = variableContext.getParameter("specattribute"+pathDescription);
			String isAll = variableContext.getParameter("specattributeall"+pathDescription);
			SpecificationNode node = new SpecificationNode("metadata");
			node.setAttribute("category",category);
			if (isAll != null && isAll.equals("true"))
				node.setAttribute("all","true");
			else
				node.setAttribute("attribute",attributeName);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// See if there's a global add operation
		String op = variableContext.getParameter("metadataop");
		if (op != null && op.equals("Add"))
		{
			String category = variableContext.getParameter("speccategory");
			String isAll = variableContext.getParameter("attributeall");
			if (isAll != null && isAll.equals("true"))
			{
				SpecificationNode node = new SpecificationNode("metadata");
				node.setAttribute("category",category);
				node.setAttribute("all","true");
				ds.addChild(ds.getChildCount(),node);
			}
			else
			{
				String[] attributes = variableContext.getParameterValues("attributeselect");
				if (attributes != null && attributes.length > 0)
				{
					int k = 0;
					while (k < attributes.length)
					{
						String attribute = attributes[k++];
						SpecificationNode node = new SpecificationNode("metadata");
						node.setAttribute("category",category);
						node.setAttribute("attribute",attribute);
						ds.addChild(ds.getChildCount(),node);
					}
				}
			}
		}
		else if (op != null && op.equals("Up"))
		{
			// Strip off end
			String category = variableContext.getParameter("speccategory");
			int lastSlash = -1;
			int firstColon = -1;			
			int k = 0;
			while (k < category.length())
			{
				char x = category.charAt(k++);
				if (x == '/')
				{
					lastSlash = k-1;
					continue;
				}
				if (x == ':')
				{
					firstColon = k;
					continue;
				}
				if (x == '\\')
					k++;
			}

			if (lastSlash == -1)
			{
				if (firstColon == -1 || firstColon == category.length())
					category = "";
				else
					category = category.substring(0,firstColon);
			}
			else
				category = category.substring(0,lastSlash);
			threadContext.save("speccategory",category);
		}
		else if (op != null && op.equals("AddToPath"))
		{
			String category = variableContext.getParameter("speccategory");
			String addon = variableContext.getParameter("metadataaddon");
			if (addon != null && addon.length() > 0)
			{
				StringBuffer sb = new StringBuffer();
				int k = 0;
				while (k < addon.length())
				{
					char x = addon.charAt(k++);
					if (x == '/' || x == '\\' || x == ':')
						sb.append('\\');
					sb.append(x);
				}
				if (category.length() == 0 || category.endsWith(":"))
					category += sb.toString();
				else
					category += "/" + sb.toString();
			}
			threadContext.save("speccategory",category);
		}
		else if (op != null && op.equals("SetWorkspace"))
		{
			String addon = variableContext.getParameter("metadataaddon");
			if (addon != null && addon.length() > 0)
			{
				StringBuffer sb = new StringBuffer();
				int k = 0;
				while (k < addon.length())
				{
					char x = addon.charAt(k++);
					if (x == '/' || x == '\\' || x == ':')
						sb.append('\\');
					sb.append(x);
				}

				String category = sb.toString() + ":";
				threadContext.save("speccategory",category);
			}
		}
		else if (op != null && op.equals("AddCategory"))
		{
			String category = variableContext.getParameter("speccategory");
			String addon = variableContext.getParameter("categoryaddon");
			if (addon != null && addon.length() > 0)
			{
				StringBuffer sb = new StringBuffer();
				int k = 0;
				while (k < addon.length())
				{
					char x = addon.charAt(k++);
					if (x == '/' || x == '\\' || x == ':')
						sb.append('\\');
					sb.append(x);
				}
				if (category.length() == 0 || category.endsWith(":"))
					category += sb.toString();
				else
					category += "/" + sb.toString();
			}
			threadContext.save("speccategory",category);
		}
	}

	xc = variableContext.getParameter("specpathnameattribute");
	if (xc != null)
	{
		// Delete old one
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("pathnameattribute"))
				ds.removeChild(i);
			else
				i++;
		}
		if (xc.length() > 0)
		{
			SpecificationNode node = new SpecificationNode("pathnameattribute");
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
			if (sn.getType().equals("pathmap"))
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
			SpecificationNode node = new SpecificationNode("pathmap");
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
			SpecificationNode node = new SpecificationNode("pathmap");
			node.setAttribute("match",match);
			node.setAttribute("replace",replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}
%>
