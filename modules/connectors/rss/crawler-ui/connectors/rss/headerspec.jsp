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
	String scheduleCount = (String)threadContext.get("ScheduleCount");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");

%>

<%
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("URLs");
	tabsArray.add("Canonicalization");
	tabsArray.add("Mappings");
	tabsArray.add("Time Values");
	tabsArray.add("Security");
	tabsArray.add("Metadata");
	tabsArray.add("Dechromed Content");
%>

<script type="text/javascript">
<!--
	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
	}

	function AddRegexp(anchorvalue)
	{
		if (editjob.rssmatch.value == "")
		{
			alert("Match must have a regexp value");
			editjob.rssmatch.focus();
			return;
		}

		SpecOp("rssop","Add",anchorvalue);
	}

	function RemoveRegexp(index, anchorvalue)
	{
		editjob.rssindex.value = index;
		SpecOp("rssop","Delete",anchorvalue);
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
		if (editjob.feedtimeout.value == "" || !isInteger(editjob.feedtimeout.value))
		{
			alert("A timeout value, in seconds, is required");
			editjob.feedtimeout.focus();
			return false;
		}
		if (editjob.feedrefetch.value == "" || !isInteger(editjob.feedrefetch.value))
		{
			alert("A refetch interval, in minutes, is required");
			editjob.feedrefetch.focus();
			return false;
		}
		if (editjob.minfeedrefetch.value == "" || !isInteger(editjob.minfeedrefetch.value))
		{
			alert("A minimum refetch interval, in minutes, is required");
			editjob.minfeedrefetch.focus();
			return false;
		}
		if (editjob.badfeedrefetch.value != "" && !isInteger(editjob.badfeedrefetch.value))
		{
			alert("A bad feed refetch interval, in minutes, is required");
			editjob.badfeedrefetch.focus();
			return false;
		}

		return true;
	}

//-->
</script>

