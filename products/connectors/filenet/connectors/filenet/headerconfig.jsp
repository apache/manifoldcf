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
	tabsArray.add("Object Store");
	tabsArray.add("Document URL");
	tabsArray.add("Credentials");
%>

<script type="text/javascript">
<!--
	function checkConfig()
	{
		if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
		{
			alert("The server port must be an integer");
			editconnection.serverport.focus();
			return false;
		}
		if (editconnection.urlport.value != "" && !isInteger(editconnection.urlport.value))
		{
			alert("The Document URL port must be an integer");
			editconnection.urlport.focus();
			return false;
		}

		return true;
	}
	
	function checkConfigForSave()
	{
		if (editconnection.serverhostname.value == "")
		{
			alert("The connection requires a FileNet host name");
			SelectTab("Server");
			editconnection.serverhostname.focus();
			return false;
		}
		if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
		{
			alert("The server port must be an integer");
			SelectTab("Server");
			editconnection.serverport.focus();
			return false;
		}
		if (editconnection.urlhostname.value == "")
		{
			alert("The Document URL requires a host name");
			SelectTab("Document URL");
			editconnection.urlhostname.focus();
			return false;
		}
		if (editconnection.urlport.value != "" && !isInteger(editconnection.urlport.value))
		{
			alert("The Document URL port must be an integer");
			SelectTab("Document URL");
			editconnection.urlport.focus();
			return false;
		}

		if (editconnection.filenetdomain.value == "")
		{
			alert("The file net domain name cannot be null");
			SelectTab("Object Store");
			editconnection.filenetdomain.focus();
			return false;
		}
		if (editconnection.objectstore.value == "")
		{
			alert("The object store name cannot be null");
			SelectTab("Object Store");
			editconnection.objectstore.focus();
			return false;
		}
		if (editconnection.userid.value == "")
		{
			alert("The connection requires a valid FileNet user ID");
			SelectTab("Credentials");
			editconnection.userid.focus();
			return false;
		}
		if (editconnection.password.value == "")
		{
			alert("The connection requires the FileNet user's password");
			SelectTab("Credentials");
			editconnection.password.focus();
			return false;
		}
		return true;
	}

//-->
</script>

