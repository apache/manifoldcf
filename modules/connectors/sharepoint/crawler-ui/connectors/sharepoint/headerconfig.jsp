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

	if (parameters == null)
		out.println("No parameters!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Server");


%>

<script type="text/javascript">
<!--
	function ShpDeleteCertificate(aliasName)
	{
		editconnection.shpkeystorealias.value = aliasName;
		editconnection.configop.value = "Delete";
		postForm();
	}

	function ShpAddCertificate()
	{
		if (editconnection.shpcertificate.value == "")
		{
			alert("Choose a certificate file");
			editconnection.shpcertificate.focus();
		}
		else
		{
			editconnection.configop.value = "Add";
			postForm();
		}
	}

	function checkConfig()
	{
		if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
		{
			alert("Please supply a valid number");
			editconnection.serverPort.focus();
			return false;
		}
		if (editconnection.serverName.value.indexOf("/") >= 0)
		{
			alert("Please specify any server path information in the site path field, not the server name field");
			editconnection.serverName.focus();
			return false;
		}
		var svrloc = editconnection.serverLocation.value;
		if (svrloc != "" && svrloc.charAt(0) != "/")
		{
			alert("Site path must begin with a '/' character");
			editconnection.serverLocation.focus();
			return false;
		}
		if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
		{
			alert("Site path cannot end with a '/' character");
			editconnection.serverLocation.focus();
			return false;
		}
		if (editconnection.userName.value != "" && editconnection.userName.value.indexOf("\\") <= 0)
		{
			alert("A valid SharePoint user name has the form <domain>\\<user>");
			editconnection.userName.focus();
			return false;
		}
		return true;
	}

	function checkConfigForSave()
	{
		if (editconnection.serverName.value == "")
		{
			alert("Please fill in a SharePoint server name");
			SelectTab("Server");
			editconnection.serverName.focus();
			return false;
		}
		if (editconnection.serverName.value.indexOf("/") >= 0)
		{
			alert("Please specify any server path information in the site path field, not the server name field");
			SelectTab("Server");
			editconnection.serverName.focus();
			return false;
		}
		var svrloc = editconnection.serverLocation.value;
		if (svrloc != "" && svrloc.charAt(0) != "/")
		{
			alert("Site path must begin with a '/' character");
			SelectTab("Server");
			editconnection.serverLocation.focus();
			return false;
		}
		if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
		{
			alert("Site path cannot end with a '/' character");
			SelectTab("Server");
			editconnection.serverLocation.focus();
			return false;
		}
		if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
		{
			alert("Please supply a SharePoint port number, or none for default");
			SelectTab("Server");
			editconnection.serverPort.focus();
			return false;
		}
		if (editconnection.userName.value == "" || editconnection.userName.value.indexOf("\\") <= 0)
		{
			alert("The connection requires a valid SharePoint user name of the form <domain>\\<user>");
			SelectTab("Server");
			editconnection.userName.focus();
			return false;
		}
		return true;
	}

//-->
</script>

