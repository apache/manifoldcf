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
	// This file is included in the head section by every place that the specification information for the file system connector
	// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editspec.jsp for this connector.
	//
	// The function checkSpecificationOnSave() is called prior to the save operation, and serves to allow the
	// connector code to check any form data.  This should return a boolean false value if the form should NOT be submitted.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");
	String scheduleCount = (String)threadContext.get("ScheduleCount");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");
%>

<%
	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Paths");
	tabsArray.add("Document Types");
	tabsArray.add("Content Types");
	tabsArray.add("Content Length");
	tabsArray.add("Security");
	tabsArray.add("Path Metadata");
%>

<script type="text/javascript">
<!--

	function checkSpecification()
	{
		if (editjob.specmaxdoclength.value != "" && !isInteger(editjob.specmaxdoclength.value))
		{
			alert("Maximum document length must be null or an integer");
			editjob.specmaxdoclength.focus();
			return false;
		}
		return true;
	}

	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
	}

	function SpecAddToPath(anchorvalue)
	{
		if (editjob.pathaddon.value == "")
		{
			alert("Select a folder first");
			editjob.pathaddon.focus();
			return;
		}

		SpecOp("pathop","AddToPath",anchorvalue);
	}

	function SpecAddToken(anchorvalue)
	{
		if (editjob.spectoken.value == "")
		{
			alert("Null tokens not allowed");
			editjob.spectoken.focus();
			return;
		}

		SpecOp("accessop","Add",anchorvalue);
	}

	function SpecAddMapping(anchorvalue)
	{
		if (editjob.specmatch.value == "")
		{
			alert("Enter a specification first");
			editjob.specmatch.focus();
			return;
		}
		if (!isRegularExpression(editjob.specmatch.value))
		{
			alert("Specification must be valid regular expression");
			editjob.specmatch.focus();
			return false;
		}
		SpecOp("specmappingop","Add",anchorvalue);
	}

//-->
</script>

