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
	// This file is included by every place that edited specification information for the sharepoint connector
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

	int i;

	// Gather the path names
	String x = variableContext.getParameter("specpath_total");
	if (x != null)
	{
		// Get rid of old specpath entries
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("SearchPath"))
				ds.removeChild(i);
			else
				i++;
		}

		// Gather into spec node, paying attention to any delete requests.
		i = 0;
		int count = Integer.parseInt(x);
		while (i < count)
		{
			String path = variableContext.getParameter("specpath_"+Integer.toString(i));
			String pathOp = variableContext.getParameter("specpathop_"+Integer.toString(i));
			if (pathOp == null || !pathOp.equals("Delete"))
			{
				SpecificationNode sn = new SpecificationNode("SearchPath");
				sn.setAttribute("path",path);
				ds.addChild(ds.getChildCount(),sn);
			}
			i++;
		}


		// Do operation
		x = variableContext.getParameter("specpathop");
		if (x != null)
		{
			// Retrieve current state information
			String pathSoFar = variableContext.getParameter("specpathbase");
			String idsSoFar = variableContext.getParameter("specidsbase");
			Integer containerType = new Integer(variableContext.getParameter("spectype"));

			if (x.equals("Add"))
			{
				// Tack the current path onto the specification
				SpecificationNode sn = new SpecificationNode("SearchPath");
				sn.setAttribute("path",pathSoFar);
				ds.addChild(ds.getChildCount(),sn);
				pathSoFar = null;
				idsSoFar = null;
				containerType = null;
			}
			else if (x.equals("AddToPath"))
			{
				String pathField = variableContext.getParameter("specpath");
				int index = pathField.indexOf(";");
				int secondIndex = pathField.indexOf(";",index+1);
				pathSoFar = pathSoFar + pathField.substring(secondIndex+1) + "/";
				idsSoFar = idsSoFar + "," + pathField.substring(0,index);
				containerType = new Integer(pathField.substring(index+1,secondIndex));
			}
			else if (x.equals("DeleteFromPath"))
			{
				pathSoFar = pathSoFar.substring(0,pathSoFar.lastIndexOf("/"));
				pathSoFar = pathSoFar.substring(0,pathSoFar.lastIndexOf("/")+1);
				idsSoFar = idsSoFar.substring(0,idsSoFar.lastIndexOf(",")-1);
				containerType = new Integer(org.apache.lcf.crawler.connectors.meridio.MeridioClassContents.CLASS);
			}

			threadContext.save("specpath",pathSoFar);
			threadContext.save("specpathids",idsSoFar);
			threadContext.save("specpathtype",containerType);
		}

	}

	// Searchon parameter
	x = variableContext.getParameter("specsearchon");
	if (x != null)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("SearchOn"))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode newNode = new SpecificationNode("SearchOn");
		newNode.setAttribute("value",x);
		ds.addChild(ds.getChildCount(),newNode);
	}

	// Categories parameter
	String[] y = variableContext.getParameterValues("speccategories");
	if (y != null)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("SearchCategory"))
				ds.removeChild(i);
			else
				i++;
		}

		i = 0;
		while (i < y.length)
		{
			String category = y[i++];
			SpecificationNode newNode = new SpecificationNode("SearchCategory");
			newNode.setAttribute("category",category);
			ds.addChild(ds.getChildCount(),newNode);
		}
	}

	// Properties parameter
	x = variableContext.getParameter("specproperties_edit");
	if (x != null && x.length() > 0)
	{
	    i = 0;
	    while (i < ds.getChildCount())
	    {
		SpecificationNode sn = ds.getChild(i);
		if (sn.getType().equals("ReturnedMetadata"))
			ds.removeChild(i);
		else
			i++;
	    }

	    y = variableContext.getParameterValues("specproperties");
	    if (y != null)
	    {
		i = 0;
		while (i < y.length)
		{
			String descriptor = y[i++];
			SpecificationNode newNode = new SpecificationNode("ReturnedMetadata");
			int index = descriptor.indexOf(".");
			String category;
			String property;
			if (index == -1)
			{
				category = null;
				property = descriptor;
			}
			else
			{
				category = descriptor.substring(0,index);
				property = descriptor.substring(index+1);
			}
			if (category != null)
				newNode.setAttribute("category",category);
			newNode.setAttribute("property",property);
			ds.addChild(ds.getChildCount(),newNode);
		}
	    }
	}


	// Mime types parameter
	y = variableContext.getParameterValues("specmimetypes");
	if (y != null)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("MIMEType"))
				ds.removeChild(i);
			else
				i++;
		}

		i = 0;
		while (i < y.length)
		{
			String category = y[i++];
			SpecificationNode newNode = new SpecificationNode("MIMEType");
			newNode.setAttribute("type",category);
			ds.addChild(ds.getChildCount(),newNode);
		}
	}

	x = variableContext.getParameter("specsecurity");
	if (x != null)
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
		node.setAttribute("value",x);
		ds.addChild(ds.getChildCount(),node);

	}

	x = variableContext.getParameter("tokencount");
	if (x != null)
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

		int accessCount = Integer.parseInt(x);
		i = 0;
		while (i < accessCount)
		{
			String accessDescription = "_"+Integer.toString(i);
			String accessOpName = "accessop"+accessDescription;
			String xc = variableContext.getParameter(accessOpName);
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

	x = variableContext.getParameter("specpathnameattribute");
	if (x != null && x.length() > 0)
	{
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("pathnameattribute"))
				ds.removeChild(i);
			else
				i++;
		}
		SpecificationNode node = new SpecificationNode("pathnameattribute");
		node.setAttribute("value",x);
		ds.addChild(ds.getChildCount(),node);
	}
	
	x = variableContext.getParameter("specmappingcount");
	if (x != null && x.length() > 0)
	{
		// Delete old spec
		i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("pathmap"))
				ds.removeChild(i);
			else
				i++;
		}

		// Now, go through the data and assemble a new list.
		int mappingCount = Integer.parseInt(x);

		// Gather up these
		i = 0;
		while (i < mappingCount)
		{
			String pathDescription = "_"+Integer.toString(i);
			String pathOpName = "specmappingop"+pathDescription;
			x = variableContext.getParameter(pathOpName);
			if (x != null && x.equals("Delete"))
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
		x = variableContext.getParameter("specmappingop");
		if (x != null && x.equals("Add"))
		{
			String match = variableContext.getParameter("specmatch");
			String replace = variableContext.getParameter("specreplace");
			SpecificationNode node = new SpecificationNode("pathmap");
			node.setAttribute("match",match);
			node.setAttribute("replace",replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}

    x = variableContext.getParameter("allmetadata");
    if (x != null)
    {
        i = 0;
        while (i < ds.getChildCount())
        {
            SpecificationNode sn = ds.getChild(i);
            if (sn.getType().equals("AllMetadata"))
                ds.removeChild(i);
            else
                i++;
        }
        SpecificationNode node = new SpecificationNode("AllMetadata");
        node.setAttribute("value",x);
        ds.addChild(ds.getChildCount(),node);
    }
%>
