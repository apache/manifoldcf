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
	// This file is included by every place that edited specification information for the rss connector
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

	// Get the map
	String value = variableContext.getParameter("rssmapcount");
	if (value != null)
	{
		int mapsize = Integer.parseInt(value);

		// Clear it first
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("map"))
				ds.removeChild(j);
			else
				j++;
		}

		// Grab the map values
		j = 0;
		while (j < mapsize)
		{
			String prefix = "rssregexp_"+Integer.toString(j)+"_";
			String match = variableContext.getParameter(prefix+"match");
			String map = variableContext.getParameter(prefix+"map");
			if (map == null)
				map = "";
			// Add to the documentum specification
			SpecificationNode node = new SpecificationNode("map");
			node.setAttribute("match",match);
			node.setAttribute("map",map);
			ds.addChild(ds.getChildCount(),node);

			j++;
		}
	}

	// Get the cgiPath
	String rssURLSequence = variableContext.getParameter("rssurls");
	if (rssURLSequence != null)
	{
		// Delete all url specs first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("feed"))
				ds.removeChild(i);
			else
				i++;
		}

		try
		{
			java.io.Reader str = new java.io.StringReader(rssURLSequence);
			try
			{
				java.io.BufferedReader is = new java.io.BufferedReader(str);
				try
				{
					while (true)
					{
						String nextString = is.readLine();
						if (nextString == null)
							break;
						if (nextString.length() == 0)
							continue;
						SpecificationNode node = new SpecificationNode("feed");
						node.setAttribute("url",nextString);
						ds.addChild(ds.getChildCount(),node);
					}
				}
				finally
				{
					is.close();
				}
			}
			finally
			{
				str.close();
			}
		}
		catch (java.io.IOException e)
		{
			throw new MetacartaException("IO error",e);
		}
	}

	// Read the url specs
	String urlRegexpCount = variableContext.getParameter("urlregexpcount");
	if (urlRegexpCount != null && urlRegexpCount.length() > 0)
	{
		int regexpCount = Integer.parseInt(urlRegexpCount);
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("urlspec"))
				ds.removeChild(j);
			else
				j++;
		}
		
		// Grab the operation and the index (if any)
		String operation = variableContext.getParameter("urlregexpop");
		if (operation == null)
			operation = "Continue";
		int opIndex = -1;
		if (operation.equals("Delete"))
			opIndex = Integer.parseInt(variableContext.getParameter("urlregexpnumber"));
		
		// Reconstruct urlspec nodes
		j = 0;
		while (j < regexpCount)
		{
			// For each index, first look for a delete operation
			if (!operation.equals("Delete") || j != opIndex)
			{
				// Add the jth node
				String regexp = variableContext.getParameter("urlregexp_"+Integer.toString(j));
				String regexpDescription = variableContext.getParameter("urlregexpdesc_"+Integer.toString(j));
				String reorder = variableContext.getParameter("urlregexpreorder_"+Integer.toString(j));
				String javaSession = variableContext.getParameter("urlregexpjava_"+Integer.toString(j));
				String aspSession = variableContext.getParameter("urlregexpasp_"+Integer.toString(j));
				String phpSession = variableContext.getParameter("urlregexpphp_"+Integer.toString(j));
				String bvSession = variableContext.getParameter("urlregexpbv_"+Integer.toString(j));
				SpecificationNode newSn = new SpecificationNode("urlspec");
				newSn.setAttribute("regexp",regexp);
				if (regexpDescription != null && regexpDescription.length() > 0)
					newSn.setAttribute("description",regexpDescription);
				if (reorder != null && reorder.length() > 0)
					newSn.setAttribute("reorder",reorder);
				if (javaSession != null && javaSession.length() > 0)
					newSn.setAttribute("javasessionremoval",javaSession);
				if (aspSession != null && aspSession.length() > 0)
					newSn.setAttribute("aspsessionremoval",aspSession);
				if (phpSession != null && phpSession.length() > 0)
					newSn.setAttribute("phpsessionremoval",phpSession);
				if (bvSession != null && bvSession.length() > 0)
					newSn.setAttribute("bvsessionremoval",bvSession);
				ds.addChild(ds.getChildCount(),newSn);
			}
			j++;
		}
		if (operation.equals("Add"))
		{
			String regexp = variableContext.getParameter("urlregexp");
			String regexpDescription = variableContext.getParameter("urlregexpdesc");
			String reorder = variableContext.getParameter("urlregexpreorder");
			String javaSession = variableContext.getParameter("urlregexpjava");
			String aspSession = variableContext.getParameter("urlregexpasp");
			String phpSession = variableContext.getParameter("urlregexpphp");
			String bvSession = variableContext.getParameter("urlregexpbv");

			// Add a new node at the end
			SpecificationNode newSn = new SpecificationNode("urlspec");
			newSn.setAttribute("regexp",regexp);
			if (regexpDescription != null && regexpDescription.length() > 0)
				newSn.setAttribute("description",regexpDescription);
			if (reorder != null && reorder.length() > 0)
				newSn.setAttribute("reorder",reorder);
			if (javaSession != null && javaSession.length() > 0)
				newSn.setAttribute("javasessionremoval",javaSession);
			if (aspSession != null && aspSession.length() > 0)
				newSn.setAttribute("aspsessionremoval",aspSession);
			if (phpSession != null && phpSession.length() > 0)
				newSn.setAttribute("phpsessionremoval",phpSession);
			if (bvSession != null && bvSession.length() > 0)
				newSn.setAttribute("bvsessionremoval",bvSession);
			ds.addChild(ds.getChildCount(),newSn);
		}
	}
	
	// Read the feed timeout, if present
	String feedTimeoutValue = variableContext.getParameter("feedtimeout");
	if (feedTimeoutValue != null && feedTimeoutValue.length() > 0)
	{
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("feedtimeout"))
				ds.removeChild(j);
			else
				j++;
		}
		SpecificationNode node = new SpecificationNode("feedtimeout");
		node.setAttribute("value",feedTimeoutValue);
		ds.addChild(ds.getChildCount(),node);
	}

	// Read the feed refetch interval, if present
	String feedRefetchValue = variableContext.getParameter("feedrefetch");
	if (feedRefetchValue != null && feedRefetchValue.length() > 0)
	{
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("feedrescan"))
				ds.removeChild(j);
			else
				j++;
		}
		SpecificationNode node = new SpecificationNode("feedrescan");
		node.setAttribute("value",feedRefetchValue);
		ds.addChild(ds.getChildCount(),node);
	}

	// Read the minimum feed refetch interval, if present
	String minFeedRefetchValue = variableContext.getParameter("minfeedrefetch");
	if (minFeedRefetchValue != null && minFeedRefetchValue.length() > 0)
	{
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("minfeedrescan"))
				ds.removeChild(j);
			else
				j++;
		}
		SpecificationNode node = new SpecificationNode("minfeedrescan");
		node.setAttribute("value",minFeedRefetchValue);
		ds.addChild(ds.getChildCount(),node);
	}
	
	// Read the bad feed refetch interval (which is allowed to be null)
	String badFeedRefetchValuePresent = variableContext.getParameter("badfeedrefetch_present");
	if (badFeedRefetchValuePresent != null && badFeedRefetchValuePresent.length() > 0)
	{
		String badFeedRefetchValue = variableContext.getParameter("badfeedrefetch");
		int k = 0;
		while (k < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(k);
			if (sn.getType().equals("badfeedrescan"))
				ds.removeChild(k);
			else
				k++;
		}
		if (badFeedRefetchValue != null && badFeedRefetchValue.length() > 0)
		{
			SpecificationNode node = new SpecificationNode("badfeedrescan");
			node.setAttribute("value",badFeedRefetchValue);
			ds.addChild(ds.getChildCount(),node);
		}
	}
	
	// Read the dechromed mode
	String dechromedMode = variableContext.getParameter("dechromedmode");
	if (dechromedMode != null && dechromedMode.length() > 0)
	{
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("dechromedmode"))
				ds.removeChild(j);
			else
				j++;
		}
		SpecificationNode node = new SpecificationNode("dechromedmode");
		node.setAttribute("mode",dechromedMode);
		ds.addChild(ds.getChildCount(),node);
	}
	
	// Read the chromed mode
	String chromedMode = variableContext.getParameter("chromedmode");
	if (chromedMode != null && chromedMode.length() > 0)
	{
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("chromedmode"))
				ds.removeChild(j);
			else
				j++;
		}
		SpecificationNode node = new SpecificationNode("chromedmode");
		node.setAttribute("mode",chromedMode);
		ds.addChild(ds.getChildCount(),node);
	}
	
	// Now, do whatever action we were told to do.
	String rssop = variableContext.getParameter("rssop");
	if (rssop != null && rssop.equals("Add"))
	{
		// Add a match to the end
		String match = variableContext.getParameter("rssmatch");
		String map = variableContext.getParameter("rssmap");
		SpecificationNode node = new SpecificationNode("map");
		node.setAttribute("match",match);
		node.setAttribute("map",map);
		ds.addChild(ds.getChildCount(),node);
	}
	else if (rssop != null && rssop.equals("Delete"))
	{
		int index = Integer.parseInt(variableContext.getParameter("rssindex"));
		int j = 0;
		while (j < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(j);
			if (sn.getType().equals("map"))
			{
				if (index == 0)
				{
					ds.removeChild(j);
					break;
				}
				index--;
			}
			j++;
		}
	}

	String xc = variableContext.getParameter("tokencount");
	if (xc != null)
	{
		// Delete all tokens first
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

	xc = variableContext.getParameter("metadatacount");
	if (xc != null)
	{
		// Delete all tokens first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals("metadata"))
				ds.removeChild(i);
			else
				i++;
		}

		int metadataCount = Integer.parseInt(xc);
		i = 0;
		while (i < metadataCount)
		{
			String metadataDescription = "_"+Integer.toString(i);
			String metadataOpName = "metadataop"+metadataDescription;
			xc = variableContext.getParameter(metadataOpName);
			if (xc != null && xc.equals("Delete"))
			{
				// Next row
				i++;
				continue;
			}
			// Get the stuff we need
			String metaNameSpec = variableContext.getParameter("specmetaname"+metadataDescription);
			String metaValueSpec = variableContext.getParameter("specmetavalue"+metadataDescription);
			SpecificationNode node = new SpecificationNode("metadata");
			node.setAttribute("name",metaNameSpec);
			node.setAttribute("value",metaValueSpec);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		String op = variableContext.getParameter("metadataop");
		if (op != null && op.equals("Add"))
		{
			String metaNameSpec = variableContext.getParameter("specmetaname");
			String metaValueSpec = variableContext.getParameter("specmetavalue");
			
			SpecificationNode node = new SpecificationNode("metadata");
			node.setAttribute("name",metaNameSpec);
			node.setAttribute("value",metaValueSpec);
			
			ds.addChild(ds.getChildCount(),node);
		}
	}

%>

