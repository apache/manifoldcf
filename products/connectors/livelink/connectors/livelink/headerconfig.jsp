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
	// This file is included in the head section by every place that the configuration information for the file system connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editconfig.jsp for this connector.
	//
	// The method checkConfigOnSave() is called prior to the form being submitted for save.  It should return false if the
	// form should not be submitted.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");
	String tabName = (String)threadContext.get("TabName");
%>

<%
	if (parameters == null)
		out.println("No parameters!!!");
	if (tabsArray == null)
		out.println("No tabs array!");
	if (tabName == null)
		out.println("No tab name!");

	tabsArray.add("Server");
	tabsArray.add("Document Access");
	tabsArray.add("Document View");
%>

<script type="text/javascript">
<!--
	function LLDeleteCertificate(aliasName)
	{
		editconnection.llkeystorealias.value = aliasName;
		editconnection.configop.value = "Delete";
		postForm();
	}

	function LLAddCertificate(aliasName)
	{
		if (editconnection.llcertificate.value == "")
		{
			alert("Choose a certificate file");
			editconnection.llcertificate.focus();
		}
		else
		{
			editconnection.llkeystorealias.value = aliasName;
			editconnection.configop.value = "Add";
			postForm();
		}
	}

	function checkConfig()
	{
		if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
		{
			alert("A valid number is required");
			editconnection.serverport.focus();
			return false;
		}
		if (editconnection.ingestport.value != "" && !isInteger(editconnection.ingestport.value))
		{
			alert("A valid number, or blank, is required");
			editconnection.ingestport.focus();
			return false;
		}
		if (editconnection.viewport.value != "" && !isInteger(editconnection.viewport.value))
		{
			alert("A valid number, or blank, is required");
			editconnection.viewport.focus();
			return false;
		}
		return true;
	}

	function checkConfigForSave()
	{
		if (editconnection.servername.value == "")
		{
			alert("Enter a livelink server name");
			SelectTab("Server");
			editconnection.servername.focus();
			return false;
		}
		if (editconnection.serverport.value == "")
		{
			alert("A server port number is required");
			SelectTab("Server");
			editconnection.serverport.focus();
			return false;
		}
		if (editconnection.ingestcgipath.value == "")
		{
			alert("Enter the crawl cgi path to livelink");
			SelectTab("Document Access");
			editconnection.ingestcgipath.focus();
			return false;
		}
		if (editconnection.ingestcgipath.value.substring(0,1) != "/")
		{
			alert("The ingest cgi path must begin with a / character");
			SelectTab("Document Access");
			editconnection.ingestcgipath.focus();
			return false;
		}
		if (editconnection.viewcgipath.value != "" && editconnection.viewcgipath.value.substring(0,1) != "/")
		{
			alert("The view cgi path must be blank, or begin with a / character");
			SelectTab("Document View");
			editconnection.viewcgipath.focus();
			return false;
		}
		return true;
	}

//-->
</script>

