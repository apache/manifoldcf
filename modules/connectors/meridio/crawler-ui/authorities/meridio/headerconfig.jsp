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

	tabsArray.add("Document Server");
	tabsArray.add("Records Server");
	tabsArray.add("MetaCarta Service Server");
	tabsArray.add("Credentials");

%>

<script type="text/javascript">
<!--

	function checkConfig()
	{
		if (editconnection.dmwsServerPort.value != "" && !isInteger(editconnection.dmwsServerPort.value))
		{
			alert("Please supply a valid number");
			editconnection.dmwsServerPort.focus();
			return false;
		}
		if (editconnection.rmwsServerPort.value != "" && !isInteger(editconnection.rmwsServerPort.value))
		{
			alert("Please supply a valid number");
			editconnection.rmwsServerPort.focus();
			return false;
		}
		if (editconnection.dmwsProxyPort.value != "" && !isInteger(editconnection.dmwsProxyPort.value))
		{
			alert("Please supply a valid number");
			editconnection.dmwsProxyPort.focus();
			return false;
		}
		if (editconnection.rmwsProxyPort.value != "" && !isInteger(editconnection.rmwsProxyPort.value))
		{
			alert("Please supply a valid number");
			editconnection.rmwsProxyPort.focus();
			return false;
		}
		if (editconnection.metacartawsServerPort.value != "" && !isInteger(editconnection.metacartawsServerPort.value))
		{
			alert("Please supply a valid number");
			editconnection.metacartawsServerPort.focus();
			return false;
		}
		if (editconnection.metacartawsProxyPort.value != "" && !isInteger(editconnection.metacartawsProxyPort.value))
		{
			alert("Please supply a valid number");
			editconnection.metacartawsProxyPort.focus();
			return false;
		}
		if (editconnection.userName.value != "" && editconnection.userName.value.indexOf("\\") <= 0)
		{
			alert("A valid Meridio user name has the form <domain>\\<user>");
			editconnection.userName.focus();
			return false;
		}
		return true;
	}

	function checkConfigForSave()
	{
		if (editconnection.dmwsServerName.value == "")
		{
			alert("Please fill in a Meridio document management server name");
			SelectTab("Document Server");
			editconnection.dmwsServerName.focus();
			return false;
		}
		if (editconnection.rmwsServerName.value == "")
		{
			alert("Please fill in a Meridio records management server name");
			SelectTab("Records Server");
			editconnection.rmwsServerName.focus();
			return false;
		}
		if (editconnection.metacartawsServerName.value == "")
		{
			alert("Please fill in a Meridio MetaCarta Service server name");
			SelectTab("MetaCarta Service Server");
			editconnection.metacartawsServerName.focus();
			return false;
		}

		if (editconnection.dmwsServerPort.value != "" && !isInteger(editconnection.dmwsServerPort.value))
		{
			alert("Please supply a Meridio document management port number, or none for default");
			SelectTab("Document Server");
			editconnection.dmwsServerPort.focus();
			return false;
		}
		if (editconnection.rmwsServerPort.value != "" && !isInteger(editconnection.rmwsServerPort.value))
		{
			alert("Please supply a Meridio document management port number, or none for default");
			SelectTab("Records Server");
			editconnection.rmwsServerPort.focus();
			return false;
		}
		if (editconnection.metacartawsServerPort.value != "" && !isInteger(editconnection.metacartawsServerPort.value))
		{
			alert("Please supply a Meridio MetaCarta Service port number, or none for default");
			SelectTab("MetaCarta Service Server");
			editconnection.metacartawsServerPort.focus();
			return false;
		}

		if (editconnection.userName.value == "" || editconnection.userName.value.indexOf("\\") <= 0)
		{
			alert("The connection requires a valid Meridio user name of the form <domain>\\<user>");
			SelectTab("Credentials");
			editconnection.userName.focus();
			return false;
		}

		return true;
	}

	function DeleteCertificate(aliasName)
	{
		editconnection.keystorealias.value = aliasName;
		editconnection.configop.value = "Delete";
		postForm();
	}

	function AddCertificate()
	{
		if (editconnection.certificate.value == "")
		{
			alert("Choose a certificate file");
			editconnection.certificate.focus();
		}
		else
		{
			editconnection.configop.value = "Add";
			postForm();
		}
	}

//-->
</script>

