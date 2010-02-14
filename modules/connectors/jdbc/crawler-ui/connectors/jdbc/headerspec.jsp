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
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");
%>

<%
	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Queries");
	tabsArray.add("Security");
%>

<script type="text/javascript">
<!--

	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
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

	function checkSpecification()
	{
		if (editjob.idquery.value == "")
		{
			alert("Enter a seeding query");
			editjob.idquery.focus();
			return false;
		}
		if (editjob.idquery.value.indexOf("$(IDCOLUMN)") == -1)
		{
			alert("Must return $(IDCOLUMN) in the result.\nExample: SELECT idfield AS $(IDCOLUMN) FROM ...");
			editjob.idquery.focus();
			return false;
		}
		if (editjob.versionquery.value != "")
		{
			if (editjob.versionquery.value.indexOf("$(IDCOLUMN)") == -1)
			{
				alert("Must return $(IDCOLUMN) in the result.\nExample: SELECT idfield AS $(IDCOLUMN), ...");
				editjob.versionquery.focus();
				return false;
			}
			if (editjob.versionquery.value.indexOf("$(VERSIONCOLUMN)") == -1)
			{
				alert("Must return $(VERSIONCOLUMN) in the result, containing the document version.\nExample: SELECT versionfield AS $(VERSIONCOLUMN), ...");
				editjob.versionquery.focus();
				return false;
			}
			if (editjob.versionquery.value.indexOf("$(IDLIST)") == -1)
			{
				alert("Must use $(IDLIST) in WHERE clause.\nExample: SELECT ... WHERE idfield IN $(IDLIST) ...");
				editjob.versionquery.focus();
				return false;
			}
		}
		if (editjob.dataquery.value == "")
		{
			alert("Enter a data query");
			editjob.dataquery.focus();
			return false;
		}
		if (editjob.dataquery.value.indexOf("$(IDCOLUMN)") == -1)
		{
			alert("Must return $(IDCOLUMN) in the result.\nExample: SELECT idfield AS $(IDCOLUMN), ...");
			editjob.dataquery.focus();
			return false;
		}
		if (editjob.dataquery.value.indexOf("$(URLCOLUMN)") == -1)
		{
			alert("Must return $(URLCOLUMN) in the result, containing the url to use to reach the document.\nExample: SELECT urlfield AS $(URLCOLUMN), ...");
			editjob.dataquery.focus();
			return false;
		}
		if (editjob.dataquery.value.indexOf("$(DATACOLUMN)") == -1)
		{
			alert("Must return $(DATACOLUMN) in the result, containing the document data.\nExample: SELECT datafield AS $(DATACOLUMN), ...");
			editjob.dataquery.focus();
			return false;
		}
		if (editjob.dataquery.value.indexOf("$(IDLIST)") == -1)
		{
			alert("Must use $(IDLIST) in WHERE clause.\nExample: SELECT ... WHERE idfield IN $(IDLIST) ...");
			editjob.dataquery.focus();
			return false;
		}

		return true;
	}

//-->
</script>

