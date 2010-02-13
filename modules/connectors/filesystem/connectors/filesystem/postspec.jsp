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
		ds.clearChildren();
		// Find out how many children were sent
		int pathCount = Integer.parseInt(x);
		// Gather up these
		int i = 0;
		int k = 0;
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
			// Path inserts won't happen until the very end
			String path = variableContext.getParameter("specpath"+pathDescription);
			SpecificationNode node = new SpecificationNode("startpoint");
			node.setAttribute("path",path);
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
					flavor = variableContext.getParameter("specflavor"+instanceDescription);
					type = variableContext.getParameter("spectype"+instanceDescription);
					match = variableContext.getParameter("specmatch"+instanceDescription);
					sn = new SpecificationNode(flavor);
					sn.setAttribute("type",type);
					sn.setAttribute("match",match);
					node.addChild(w++,sn);
				}
				flavor = variableContext.getParameter("specfl"+instanceDescription);
				type = variableContext.getParameter("specty"+instanceDescription);
				match = variableContext.getParameter("specma"+instanceDescription);
				sn = new SpecificationNode(flavor);
				sn.setAttribute("type",type);
				sn.setAttribute("match",match);
				node.addChild(w++,sn);
				j++;
			}
			if (x != null && x.equals("Add"))
			{
				// Process adds to the end of the rules in-line
				String match = variableContext.getParameter("specmatch"+pathDescription);
				String type = variableContext.getParameter("spectype"+pathDescription);
				String flavor = variableContext.getParameter("specflavor"+pathDescription);
				SpecificationNode sn = new SpecificationNode(flavor);
				sn.setAttribute("type",type);
				sn.setAttribute("match",match);
				node.addChild(w,sn);
			}
			ds.addChild(k++,node);
			i++;
		}

		// See if there's a global add operation
		String op = variableContext.getParameter("specop");
		if (op != null && op.equals("Add"))
		{
			String path = variableContext.getParameter("specpath");
			SpecificationNode node = new SpecificationNode("startpoint");
			node.setAttribute("path",path);
			ds.addChild(k,node);
		}
	}
%>
