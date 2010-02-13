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

	// Remove old-style rules, but only if the information would not be lost
	if (variableContext.getParameter("specpathcount") != null && variableContext.getParameter("metapathcount") != null)
	{
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("startpoint"))
				ds.removeChild(i);
			else
				i++;
		}
	}
	
	String x = variableContext.getParameter("specpathcount");
	if (x != null)
	{
		// Delete all path rule entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("pathrule"))
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
			String pathOpName = "specop"+pathDescription;
			x = variableContext.getParameter(pathOpName);
			if (x != null && x.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			
			// Get the stored information for this rule.
			String path = variableContext.getParameter("specpath"+pathDescription);
			String type = variableContext.getParameter("spectype"+pathDescription);
			String action = variableContext.getParameter("specflav"+pathDescription);
			
			SpecificationNode node = new SpecificationNode("pathrule");
			node.setAttribute("match",path);
			node.setAttribute("action",action);
			node.setAttribute("type",type);
			
			// If there was an insert operation, do it now
			if (x != null && x.equals("Insert Here"))
			{
				// The global parameters are what are used to create the rule
				path = variableContext.getParameter("specpath");
				type = variableContext.getParameter("spectype");
				action = variableContext.getParameter("specflavor");
				
				SpecificationNode sn = new SpecificationNode("pathrule");
				sn.setAttribute("match",path);
				sn.setAttribute("action",action);
				sn.setAttribute("type",type);
				ds.addChild(ds.getChildCount(),sn);
			}
			
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// See if there's a global path rule operation
		String op = variableContext.getParameter("specop");
		if (op != null)
		{
			if (op.equals("Add"))
			{
				String match = variableContext.getParameter("specpath");
				String action = variableContext.getParameter("specflavor");
				String type = variableContext.getParameter("spectype");
				SpecificationNode node = new SpecificationNode("pathrule");
				node.setAttribute("match",match);
				node.setAttribute("action",action);
				node.setAttribute("type",type);
				ds.addChild(ds.getChildCount(),node);
			}
		}

		// See if there's a global pathbuilder operation
		String pathop = variableContext.getParameter("specpathop");
		if (pathop != null)
		{
			if (pathop.equals("Reset"))
			{
				threadContext.save("specpath","/");
				threadContext.save("specpathstate","site");
				threadContext.save("specpathlibrary",null);
			}
			else if (pathop.equals("AppendSite"))
			{
				String path = variableContext.getParameter("specpath");
				String addon = variableContext.getParameter("specsite");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
				}
				threadContext.save("specpath",path);
				threadContext.save("specpathstate","site");
				threadContext.save("specpathlibrary",null);
			}
			else if (pathop.equals("AppendLibrary"))
			{
				String path = variableContext.getParameter("specpath");
				String addon = variableContext.getParameter("speclibrary");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
					threadContext.save("specpathstate","library");
					threadContext.save("specpathlibrary",path);
				}
				threadContext.save("specpath",path);
			}
			else if (pathop.equals("AppendText"))
			{
				String path = variableContext.getParameter("specpath");
				String library = variableContext.getParameter("specpathlibrary");
				String addon = variableContext.getParameter("specmatch");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
					threadContext.save("specpathstate","unknown");
				}
				threadContext.save("specpath",path);
				threadContext.save("specpathlibrary",library);
			}
			else if (pathop.equals("Remove"))
			{
				// Strip off end
				String path = variableContext.getParameter("specpath");
				int index = path.lastIndexOf("/");
				path = path.substring(0,index);
				if (path.length() == 0)
					path = "/";
				threadContext.save("specpath",path);
				// Now, adjust state.
				String pathState = variableContext.getParameter("specpathstate");
				if (pathState.equals("library"))
					pathState = "site";
				threadContext.save("specpathstate",pathState);
			}
		}

	}
	
	x = variableContext.getParameter("metapathcount");
	if (x != null)
	{
		// Delete all metadata rule entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("metadatarule"))
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
			String pathOpName = "metaop"+pathDescription;
			x = variableContext.getParameter(pathOpName);
			if (x != null && x.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}

			// Get the stored information for this rule.
			String path = variableContext.getParameter("metapath"+pathDescription);
			String action = variableContext.getParameter("metaflav"+pathDescription);
			String allmetadata =  variableContext.getParameter("metaall"+pathDescription);
			String[] metadataFields = variableContext.getParameterValues("metafields"+pathDescription);
			
			SpecificationNode node = new SpecificationNode("metadatarule");
			node.setAttribute("match",path);
			node.setAttribute("action",action);
			if (action.equals("include"))
			{
				if (allmetadata != null)
					node.setAttribute("allmetadata",allmetadata);
				if (metadataFields != null)
				{
					int j = 0;
					while (j < metadataFields.length)
					{
						SpecificationNode sn = new SpecificationNode("metafield");
						sn.setAttribute("value",metadataFields[j]);
						node.addChild(j++,sn);
					}
				}
			}
			
			if (x != null && x.equals("Insert Here"))
			{
				// Insert the new global rule information now
				path = variableContext.getParameter("metapath");
				action = variableContext.getParameter("metaflavor");
				allmetadata =  variableContext.getParameter("metaall");
				metadataFields = variableContext.getParameterValues("metafields");
			
				SpecificationNode sn = new SpecificationNode("metadatarule");
				sn.setAttribute("match",path);
				sn.setAttribute("action",action);
				if (action.equals("include"))
				{
					if (allmetadata != null)
						node.setAttribute("allmetadata",allmetadata);
					if (metadataFields != null)
					{
						int j = 0;
						while (j < metadataFields.length)
						{
							SpecificationNode node2 = new SpecificationNode("metafield");
							node2.setAttribute("value",metadataFields[j]);
							sn.addChild(j++,node2);
						}
					}
				}

				ds.addChild(ds.getChildCount(),sn);
			}
			
			ds.addChild(ds.getChildCount(),node);
			i++;
		}
		
		// See if there's a global path rule operation
		String op = variableContext.getParameter("metaop");
		if (op != null)
		{
			if (op.equals("Add"))
			{
				String match = variableContext.getParameter("metapath");
				String action = variableContext.getParameter("metaflavor");
				SpecificationNode node = new SpecificationNode("metadatarule");
				node.setAttribute("match",match);
				node.setAttribute("action",action);
				if (action.equals("include"))
				{
					String allmetadata = variableContext.getParameter("metaall");
					String[] metadataFields = variableContext.getParameterValues("metafields");
					if (allmetadata != null)
						node.setAttribute("allmetadata",allmetadata);
					if (metadataFields != null)
					{
						int j = 0;
						while (j < metadataFields.length)
						{
							SpecificationNode sn = new SpecificationNode("metafield");
							sn.setAttribute("value",metadataFields[j]);
							node.addChild(j++,sn);
						}
					}

				}
				ds.addChild(ds.getChildCount(),node);
			}
		}

		// See if there's a global pathbuilder operation
		String pathop = variableContext.getParameter("metapathop");
		if (pathop != null)
		{
			if (pathop.equals("Reset"))
			{
				threadContext.save("metapath","/");
				threadContext.save("metapathstate","site");
				threadContext.save("metapathlibrary",null);
			}
			else if (pathop.equals("AppendSite"))
			{
				String path = variableContext.getParameter("metapath");
				String addon = variableContext.getParameter("metasite");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
				}
				threadContext.save("metapath",path);
				threadContext.save("metapathstate","site");
				threadContext.save("metapathlibrary",null);
			}
			else if (pathop.equals("AppendLibrary"))
			{
				String path = variableContext.getParameter("metapath");
				String addon = variableContext.getParameter("metalibrary");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
					threadContext.save("metapathstate","library");
					threadContext.save("metapathlibrary",path);
				}
				threadContext.save("metapath",path);
			}
			else if (pathop.equals("AppendText"))
			{
				String path = variableContext.getParameter("metapath");
				String library = variableContext.getParameter("metapathlibrary");
				String addon = variableContext.getParameter("metamatch");
				if (addon != null && addon.length() > 0)
				{
					if (path.equals("/"))
						path = path + addon;
					else
						path = path + "/" + addon;
					threadContext.save("metapathstate","unknown");
				}
				threadContext.save("metapath",path);
				threadContext.save("metapathlibrary",library);
			}
			else if (pathop.equals("Remove"))
			{
				// Strip off end
				String path = variableContext.getParameter("metapath");
				int index = path.lastIndexOf("/");
				path = path.substring(0,index);
				if (path.length() == 0)
					path = "/";
				threadContext.save("metapath",path);
				// Now, adjust state.
				String pathState = variableContext.getParameter("metapathstate");
				if (pathState.equals("library"))
				{
					pathState = "site";
				}
				threadContext.save("metapathlibrary",null);
				threadContext.save("metapathstate",pathState);
			}
		}

		
	}

	String xc = variableContext.getParameter("specsecurity");
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
