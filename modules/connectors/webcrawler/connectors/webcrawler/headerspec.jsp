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
	// This file is included in the head section by every place that the specification information for the rss connector
	// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editspec.jsp for this connector.
	//
	// The function checkSpecificationOnSave() is called prior to the save operation, and serves to allow the
	// connector code to check any form data.  This should return a boolean false value if the form should NOT be submitted.
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Seeds");
	tabsArray.add("Canonicalization");
	tabsArray.add("Inclusions");
	tabsArray.add("Exclusions");
	tabsArray.add("Security");
	tabsArray.add("Metadata");
%>

<script type="text/javascript">
<!--

	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
	}

	function URLRegexpDelete(index, anchorvalue)
	{
		editjob.urlregexpnumber.value = index;
		SpecOp("urlregexpop","Delete",anchorvalue);
	}

	function URLRegexpAdd(anchorvalue)
	{
		SpecOp("urlregexpop","Add",anchorvalue);
	}

	function checkSpecification()
	{
		if (check_expressions("inclusions",editjob.inclusions.value) == false)
		{
			editjob.inclusions.focus();
			return false;
		}	
		if (check_expressions("exclusions",editjob.exclusions.value) == false)
		{
			editjob.exclusions.focus();
			return false;
		}
		return true;
	}

	function check_expressions(thecontext,theexpressionlist)
	{
		var rval = true;
		var theArray = theexpressionlist.split("\n");
		var i = 0;
		while (i < theArray.length)
		{
			// For legality check, we must cut out anything useful that is java-only
			var theexp = theArray[i];
			var trimmed = theexp.replace(/^\s+/,"");
			i = i + 1;
			if (trimmed.length == 0 || (trimmed.length >= 1 && trimmed.substring(0,1) == "#"))
				continue;
			try
			{
				var foo = "teststring";
				foo.search(theexp.replace(/\(\?i\)/,""));
			}
			catch (e)
			{
				alert("Found an illegal regular expression in "+thecontext+": '"+theexp+"'.  Error was: "+e);
				rval = false;
			}
		}
		return rval;
	}

	function SpecAddToken(anchorvalue)
	{
		if (editjob.spectoken.value == "")
		{
			alert("Type in an access token");
			editjob.spectoken.focus();
			return;
		}
		SpecOp("accessop","Add",anchorvalue);
	}

	function SpecAddMetadata(anchorvalue)
	{
		if (editjob.specmetaname.value == "")
		{
			alert("Type in metadata name");
			editjob.specmetaname.focus();
			return;
		}
		if (editjob.specmetavalue.value == "")
		{
			alert("Type in metadata value");
			editjob.specmetavalue.focus();
			return;
		}
		SpecOp("metadataop","Add",anchorvalue);
	}


//-->
</script>

