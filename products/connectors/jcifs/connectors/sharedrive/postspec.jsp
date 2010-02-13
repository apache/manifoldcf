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
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT))
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
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH,path);

			// Now, get the number of children
			String y = variableContext.getParameter("specchildcount"+pathDescription);
			int childCount = Integer.parseInt(y);
			int j = 0;
			int w = 0;
			while (j < childCount)
			{
				String instanceDescription = "_"+Integer.toString(i)+"_"+Integer.toString(j);
				// Look for an insert or a delete at this point
				String instanceOp = "specop"+instanceDescription;
				String z = variableContext.getParameter(instanceOp);
				String flavor;
				String type;
				String indexable;
				String match;
				SpecificationNode sn;
				if (z != null && z.equals("Delete"))
				{
					// Process the deletion as we gather
					j++;
					continue;
				}
				if (z != null && z.equals("Insert Here"))
				{
					// Process the insertion as we gather.
					flavor = variableContext.getParameter("specfl_i"+instanceDescription);
					indexable = "";
					type = "";
					String xxx = variableContext.getParameter("spectin_i"+instanceDescription);
					if (xxx.equals("file") || xxx.equals("directory"))
						type = xxx;
					else if (xxx.equals("indexable-file"))
					{
						indexable = "yes";
						type = "file";
					}
					else if (xxx.equals("unindexable-file"))
					{
						indexable = "no";
						type = "file";
					}

					match = variableContext.getParameter("specfile_i"+instanceDescription);
					sn = new SpecificationNode(flavor);
					if (type != null && type.length() > 0)
						sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE,type);
					if (indexable != null && indexable.length() > 0)
						sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE,indexable);
					sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC,match);
					node.addChild(w++,sn);
				}
				flavor = variableContext.getParameter("specfl"+instanceDescription);
				type = variableContext.getParameter("specty"+instanceDescription);
				match = variableContext.getParameter("specfile"+instanceDescription);
				indexable = variableContext.getParameter("specin"+instanceDescription);
				sn = new SpecificationNode(flavor);
				if (type != null && type.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE,type);
				if (indexable != null && indexable.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE,indexable);
				sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC,match);
				node.addChild(w++,sn);
				j++;
			}
			if (x != null && x.equals("Add"))
			{
				// Process adds to the end of the rules in-line
				String match = variableContext.getParameter("specfile"+pathDescription);
				String indexable = "";
				String type = "";
				String xxx = variableContext.getParameter("spectin"+pathDescription);
				if (xxx.equals("file") || xxx.equals("directory"))
					type = xxx;
				else if (xxx.equals("indexable-file"))
				{
					indexable = "yes";
					type = "file";
				}
				else if (xxx.equals("unindexable-file"))
				{
					indexable = "no";
					type = "file";
				}

				String flavor = variableContext.getParameter("specfl"+pathDescription);
				SpecificationNode sn = new SpecificationNode(flavor);
				if (type != null && type.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE,type);
				if (indexable != null && indexable.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE,indexable);
				sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC,match);
				node.addChild(w,sn);
			}

			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// See if there's a global add operation
		String op = variableContext.getParameter("pathop");
		if (op != null && op.equals("Add"))
		{
			String path = variableContext.getParameter("specpath");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH,path);
			ds.addChild(ds.getChildCount(),node);

			// Now add in the defaults; these will be "include all directories" and "include all indexable files".
			SpecificationNode sn = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_INCLUDE);
			sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE,"file");
			sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE,"yes");
			sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC,"*");
			node.addChild(node.getChildCount(),sn);
			sn = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_INCLUDE);
			sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE,"directory");
			sn.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC,"*");
			node.addChild(node.getChildCount(),sn);
		}
		else if (op != null && op.equals("Up"))
		{
			// Strip off end
			String path = variableContext.getParameter("specpath");
			int k = path.lastIndexOf("/");
			if (k == -1)
				path = "";
			else
				path = path.substring(0,k);
			threadContext.save("specpath",path);
		}
		else if (op != null && op.equals("AddToPath"))
		{
			String path = variableContext.getParameter("specpath");
			String addon = variableContext.getParameter("pathaddon");
			String typein = variableContext.getParameter("pathtypein");
			if (addon != null && addon.length() > 0)
			{
				if (path.length() == 0)
					path = addon;
				else
					path += "/" + addon;
			}
			else if (typein != null && typein.length() > 0)
			{
				String trialPath = path;
				if (trialPath.length() == 0)
					trialPath = typein;
				else
					trialPath += "/" + typein;
				// Validate trial path
				try
				{
					IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
						repositoryConnection.getClassName(),
						repositoryConnection.getConfigParams(),
						repositoryConnection.getMaxConnections());
					try
					{
						com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector c = (com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector)connector;
						trialPath = c.validateFolderName(trialPath);
						if (trialPath != null)
							path = trialPath;
					}
					finally
					{
						RepositoryConnectorFactory.release(connector);
					}
				}
				catch (MetacartaException e)
				{
					// Effectively, this just means we can't add a typein to the path right now.
				}
			}
			threadContext.save("specpath",path);
		}
	}

	x = variableContext.getParameter("specmaxlength");
	if (x != null)
	{
		// Delete max length entry
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_MAXLENGTH))
				ds.removeChild(i);
			else
				i++;
		}
		if (x.length() > 0)
		{
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_MAXLENGTH);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE,x);
			ds.addChild(ds.getChildCount(),node);
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
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SECURITY))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SECURITY);
		node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE,x);
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
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS))
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
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TOKEN,accessSpec);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		String op = variableContext.getParameter("accessop");
		if (op != null && op.equals("Add"))
		{
			String accessspec = variableContext.getParameter("spectoken");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TOKEN,accessspec);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	x = variableContext.getParameter("specsharesecurity");
	if (x != null)
	{
		// Delete all security entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SHARESECURITY))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SHARESECURITY);
		node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE,x);
		ds.addChild(ds.getChildCount(),node);

	}

	String xc = variableContext.getParameter("specpathnameattribute");
	if (xc != null)
	{
		// Delete old one
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHNAMEATTRIBUTE))
				ds.removeChild(i);
			else
				i++;
		}
		if (xc.length() > 0)
		{
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHNAMEATTRIBUTE);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE,xc);
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
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHMAP))
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
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// Check for add
		xc = variableContext.getParameter("specmappingop");
		if (xc != null && xc.equals("Add"))
		{
			String match = variableContext.getParameter("specmatch");
			String replace = variableContext.getParameter("specreplace");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}
	
	xc = variableContext.getParameter("specfmapcount");
	if (xc != null)
	{
		// Delete old spec
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_FILEMAP))
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
			String pathOpName = "specfmapop"+pathDescription;
			xc = variableContext.getParameter(pathOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Inserts won't happen until the very end
			String match = variableContext.getParameter("specfmapmatch"+pathDescription);
			String replace = variableContext.getParameter("specfmapreplace"+pathDescription);
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_FILEMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// Check for add
		xc = variableContext.getParameter("specfmapop");
		if (xc != null && xc.equals("Add"))
		{
			String match = variableContext.getParameter("specfmapmatch");
			String replace = variableContext.getParameter("specfmapreplace");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_FILEMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}

	xc = variableContext.getParameter("specumapcount");
	if (xc != null)
	{
		// Delete old spec
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_URIMAP))
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
			String pathOpName = "specumapop"+pathDescription;
			xc = variableContext.getParameter(pathOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Skip to the next
				i++;
				continue;
			}
			// Inserts won't happen until the very end
			String match = variableContext.getParameter("specumapmatch"+pathDescription);
			String replace = variableContext.getParameter("specumapreplace"+pathDescription);
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_URIMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		// Check for add
		xc = variableContext.getParameter("specumapop");
		if (xc != null && xc.equals("Add"))
		{
			String match = variableContext.getParameter("specumapmatch");
			String replace = variableContext.getParameter("specumapreplace");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_URIMAP);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH,match);
			node.setAttribute(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE,replace);
			ds.addChild(ds.getChildCount(),node);
		}
	}

%>