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
	tabsArray.add("Security");
	tabsArray.add("Metadata");
%>

<script type="text/javascript">
<!--

	function checkSpecification()
	{
		// Does nothing right now.
		return true;
	}

	function SpecRuleAddPath(anchorvalue)
	{
		if (editjob.spectype.value=="")
		{
			alert("Please select a type first.");
			editjob.spectype.focus();
		}
		else if (editjob.specflavor.value=="")
		{
			alert("Please select an action first.");
			editjob.specflavor.focus();
		}
		else
			SpecOp("specop","Add",anchorvalue);
	}
	
	function SpecPathReset(anchorvalue)
	{
		SpecOp("specpathop","Reset",anchorvalue);
	}
	
	function SpecPathAppendSite(anchorvalue)
	{
		if (editjob.specsite.value == "")
		{
			alert("Please select a site first");
			editjob.specsite.focus();
		}
		else
			SpecOp("specpathop","AppendSite",anchorvalue);
	}

	function SpecPathAppendLibrary(anchorvalue)
	{
		if (editjob.speclibrary.value == "")
		{
			alert("Please select a library first");
			editjob.speclibrary.focus();
		}
		else
			SpecOp("specpathop","AppendLibrary",anchorvalue);
	}

	function SpecPathAppendText(anchorvalue)
	{
		if (editjob.specmatch.value == "")
		{
			alert("Please provide match text first");
			editjob.specmatch.focus();
		}
		else
			SpecOp("specpathop","AppendText",anchorvalue);
	}

	function SpecPathRemove(anchorvalue)
	{
		SpecOp("specpathop","Remove",anchorvalue);
	}

	function MetaRuleAddPath(anchorvalue)
	{
		if (editjob.metaflavor.value=="")
		{
			alert("Please select an action first.");
			editjob.metaflavor.focus();
		}
		else
			SpecOp("metaop","Add",anchorvalue);
	}

	function MetaPathReset(anchorvalue)
	{
		SpecOp("metapathop","Reset",anchorvalue);
	}
	
	function MetaPathAppendSite(anchorvalue)
	{
		if (editjob.metasite.value == "")
		{
			alert("Please select a site first");
			editjob.metasite.focus();
		}
		else
			SpecOp("metapathop","AppendSite",anchorvalue);
	}

	function MetaPathAppendLibrary(anchorvalue)
	{
		if (editjob.metalibrary.value == "")
		{
			alert("Please select a library first");
			editjob.metalibrary.focus();
		}
		else
			SpecOp("metapathop","AppendLibrary",anchorvalue);
	}

	function MetaPathAppendText(anchorvalue)
	{
		if (editjob.metamatch.value == "")
		{
			alert("Please provide match text first");
			editjob.metamatch.focus();
		}
		else
			SpecOp("metapathop","AppendText",anchorvalue);
	}

	function MetaPathRemove(anchorvalue)
	{
		SpecOp("metapathop","Remove",anchorvalue);
	}

	function SpecAddAccessToken(anchorvalue)
	{
		if (editjob.spectoken.value == "")
		{
			alert("Access token cannot be null");
			editjob.spectoken.focus();
		}
		else
			SpecOp("accessop","Add",anchorvalue);
	}

	function SpecAddMapping(anchorvalue)
	{
		if (editjob.specmatch.value == "")
		{
			alert("Match string cannot be empty");
			editjob.specmatch.focus();
			return;
		}
		if (!isRegularExpression(editjob.specmatch.value))
		{
			alert("Match string must be valid regular expression");
			editjob.specmatch.focus();
			return;
		}
		SpecOp("specmappingop","Add",anchorvalue);
	}

	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
	}

//-->
</script>

