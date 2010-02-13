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
	// This file is included by every place that edited specification information for the memex connector
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

	String x = variableContext.getParameter("entitytypecount");
	if (x != null && x.length() > 0)
	{
		// Delete all entity records first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ENTITY))
				ds.removeChild(i);
			else
				i++;
		}

		// Loop through specs
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String entityType = variableContext.getParameter("entitytype_"+Integer.toString(i));
			String displayName = variableContext.getParameter("entitydesc_"+Integer.toString(i));
			String[] primaryFields = variableContext.getParameterValues("primaryfields_"+Integer.toString(i));
			if (primaryFields != null && primaryFields.length > 0)
			{
				// At least one primary field was selected for this entity type!
				SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ENTITY);
				node.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME,entityType);
				node.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION,displayName);
				
				int k = 0;
				while (k < primaryFields.length)
				{
					SpecificationNode primaryNode = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_PRIMARYFIELD);
					primaryNode.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME,primaryFields[k++]);
					node.addChild(node.getChildCount(),primaryNode);
				}
				
				String[] z = variableContext.getParameterValues("metadatafields_"+Integer.toString(i));
				if (z != null)
				{
					k = 0;
					while (k < z.length)
					{
						SpecificationNode attrNode = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_METAFIELD);
						attrNode.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME,z[k++]);
						node.addChild(node.getChildCount(),attrNode);
					}
				}
				ds.addChild(ds.getChildCount(),node);
			}
			i++;
		}
	}

	// Next, process the record criteria
	x = variableContext.getParameter("specrulecount");
	if (x != null && x.length() > 0)
	{
		// Delete all rules first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE))
				ds.removeChild(i);
			else
				i++;
		}

		// Loop through specs
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String ruleSuffix = "_"+Integer.toString(i);
			// Grab the rule data
			String virtualServer = variableContext.getParameter("virtualserver"+ruleSuffix);
			if (virtualServer == null)
				virtualServer = "";
			String entityPrefix = variableContext.getParameter("entityprefix"+ruleSuffix);
			if (entityPrefix == null)
				entityPrefix = "";
			String entityDescription = variableContext.getParameter("entitydescription"+ruleSuffix);
			if (entityDescription == null)
				entityDescription = "";
			String fieldName = variableContext.getParameter("fieldname"+ruleSuffix);
			if (fieldName == null)
				fieldName = "";
			String operation = variableContext.getParameter("operation"+ruleSuffix);
			if (operation == null)
				operation = "";
			String fieldValue = variableContext.getParameter("fieldvalue"+ruleSuffix);
			if (fieldValue == null)
				fieldValue = "";
			String opcode = variableContext.getParameter("specop"+ruleSuffix);
			if (opcode != null && opcode.equals("Delete"))
			{
				// Do not include this row in the new set
			}
			else
			{
				SpecificationNode sn = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE);
				if (virtualServer.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VIRTUALSERVER,virtualServer);
				if (entityPrefix.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_ENTITY,entityPrefix);
				if (entityDescription.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION,entityDescription);
				if (fieldName.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDNAME,fieldName);
				if (operation.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_OPERATION,operation);
				if (fieldValue.length() > 0)
					sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDVALUE,fieldValue);
				ds.addChild(ds.getChildCount(),sn);
			}
			i++;
		}
		
		// Pull out rule fields and put them into the thread context
		String ruleVirtualServer = variableContext.getParameter("rulevirtualserver");
		if (ruleVirtualServer == null)
			ruleVirtualServer = "";
		String ruleEntityPrefix = variableContext.getParameter("ruleentityprefix");
		if (ruleEntityPrefix == null)
			ruleEntityPrefix = "";
		String ruleEntityDescription = variableContext.getParameter("ruleentitydescription");
		if (ruleEntityDescription == null)
			ruleEntityDescription = "";
		String ruleFieldName = variableContext.getParameter("rulefieldname");
		if (ruleFieldName == null)
			ruleFieldName = "";
		String ruleOperation = variableContext.getParameter("ruleoperation");
		if (ruleOperation == null)
			ruleOperation = "";
		String ruleFieldValue = variableContext.getParameter("rulefieldvalue");
		if (ruleFieldValue == null)
			ruleFieldValue = "";
			
		// Now, look at global opcode.
		String globalOpcode = variableContext.getParameter("specop");
		if (globalOpcode != null && globalOpcode.equals("Add"))
		{
			// Add the specified rule to the end of the list, and clear out all the rule parameters.
			SpecificationNode sn = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE);
			if (ruleVirtualServer.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VIRTUALSERVER,ruleVirtualServer);
			if (ruleEntityPrefix.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_ENTITY,ruleEntityPrefix);
			if (ruleEntityDescription.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION,ruleEntityDescription);
			if (ruleFieldName.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDNAME,ruleFieldName);
			if (ruleOperation.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_OPERATION,ruleOperation);
			if (ruleFieldValue.length() > 0)
				sn.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDVALUE,ruleFieldValue);
			ds.addChild(ds.getChildCount(),sn);
			ruleVirtualServer = "";
			ruleEntityPrefix = "";
			ruleEntityDescription = "";
			ruleFieldName = "";
			ruleOperation = "";
			ruleFieldValue = "";
		}
			
		threadContext.save("rulevirtualserver",ruleVirtualServer);
		threadContext.save("ruleentityprefix",ruleEntityPrefix);
		threadContext.save("ruleentitydescription",ruleEntityDescription);
		threadContext.save("rulefieldname",ruleFieldName);
		threadContext.save("ruleoperation",ruleOperation);
		threadContext.save("rulefieldvalue",ruleFieldValue);
	}

	// Look whether security is on or off
	String xc = variableContext.getParameter("specsecurity");
	if (xc != null)
	{
		// Delete all security entries first
		int i = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i);
			if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SECURITY))
				ds.removeChild(i);
			else
				i++;
		}

		SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_SECURITY);
		node.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VALUE,xc);
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
			if (sn.getType().equals(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS))
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
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS);
			node.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_TOKEN,accessSpec);
			ds.addChild(ds.getChildCount(),node);
			i++;
		}

		String op = variableContext.getParameter("accessop");
		if (op != null && op.equals("Add"))
		{
			String accessspec = variableContext.getParameter("spectoken");
			SpecificationNode node = new SpecificationNode(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS);
			node.setAttribute(com.metacarta.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_TOKEN,accessspec);
			ds.addChild(ds.getChildCount(),node);
		}
	}

%>
