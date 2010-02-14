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
	// This file is included by every place that the output specification information for the GTS connector
	// needs to be viewed.  When it is called, the OutputSpecification object is placed in the thread context
	// under the name "OutputSpecification".  The IOutputConnection object is also in the thread context,
	// under the name "OutputConnection".

	// The coder can presume that this jsp is executed within a body section.

	OutputSpecification os = (OutputSpecification)threadContext.get("OutputSpecification");
	IOutputConnection outputConnection = (IOutputConnection)threadContext.get("OutputConnection");
%>

<%
	if (os == null)
		out.println("Hey!  No output specification came in!!!");
	if (outputConnection == null)
		out.println("No output connection!!!");

	int i = 0;
	String collectionName = null;
	String documentTemplate = null;
	while (i < os.getChildCount())
	{
		SpecificationNode sn = os.getChild(i++);
		if (sn.getType().equals(com.metacarta.agents.output.gts.GTSConfig.NODE_COLLECTION))
		{
			collectionName = sn.getAttributeValue(com.metacarta.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
		}
		else if (sn.getType().equals(com.metacarta.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE))
		{
			documentTemplate = sn.getAttributeValue(com.metacarta.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
		}
	}
	if (collectionName == null)
		collectionName = "";
	if (documentTemplate == null)
		documentTemplate = "";

	// Display collections
%>
<table class="displaytable">
	<tr>
		<td class="description"><nobr>Collection:</nobr></td>
		<td class="value">
			<%=com.metacarta.ui.util.Encoder.bodyEscape(collectionName)%>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Document template:</nobr></td>
		<td class="value">
<%
	if (documentTemplate == null || documentTemplate.length() == 0)
		out.println("None specified");
	else
	{
%>
			<textarea name="documenttemplate" cols="96" rows="5" readonly="true"><%=com.metacarta.ui.util.Encoder.bodyEscape(documentTemplate)%></textarea>
<%
	}
%>
		</td>
	</tr>
</table>

 