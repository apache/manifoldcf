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
	// This file is included by every place that edited specification information for the GTS output connector
	// is posted upon submit.  When it is called, the OutputSpecification object is placed in the thread context
	// under the name "OutputSpecification".  The IOutputConnection object is also in the thread context,
	// under the name "OutputConnection".  The OutputSpecification object should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	OutputSpecification os = (OutputSpecification)threadContext.get("OutputSpecification");
	IOutputConnection outputConnection = (IOutputConnection)threadContext.get("OutputConnection");

	if (os == null)
		System.out.println("Hey!  No output specification came in!!!");
	if (outputConnection == null)
		System.out.println("No output connection!!!");

	// Collection name
	String collectionName = variableContext.getParameter("gts_collectionname");
	if (collectionName != null)
	{
		int i = 0;
		while (i < os.getChildCount())
		{
			SpecificationNode sn = os.getChild(i);
			if (sn.getType().equals(com.metacarta.agents.output.gts.GTSConfig.NODE_COLLECTION))
				os.removeChild(i);
			else
				i++;
		}
		if (collectionName.length() > 0)
		{
			SpecificationNode newspec = new SpecificationNode(com.metacarta.agents.output.gts.GTSConfig.NODE_COLLECTION);
			newspec.setAttribute(com.metacarta.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE,collectionName);
			os.addChild(os.getChildCount(),newspec);
		}
	}

	// Document template
	String documentTemplate = variableContext.getParameter("gts_documenttemplate");
	if (documentTemplate != null)
	{
		int i = 0;
		while (i < os.getChildCount())
		{
			SpecificationNode sn = os.getChild(i);
			if (sn.getType().equals(com.metacarta.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE))
				os.removeChild(i);
			else
				i++;
		}
		SpecificationNode newspec = new SpecificationNode(com.metacarta.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE);
		newspec.setAttribute(com.metacarta.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE,documentTemplate);
		os.addChild(os.getChildCount(),newspec);
	}
%>
