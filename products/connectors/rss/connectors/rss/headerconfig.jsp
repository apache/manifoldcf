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
	// This file is included in the head section by every place that the configuration information for the rss connector
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

	tabsArray.add("Email");
	tabsArray.add("Robots");
	tabsArray.add("Bandwidth");
	tabsArray.add("Proxy");
%>

<script type="text/javascript">
<!--
	function checkConfig()
	{
		if (editconnection.email.value != "" && editconnection.email.value.indexOf("@") == -1)
		{
			alert("Need a valid email address");
			editconnection.email.focus();
			return false;
		}
		if (editconnection.bandwidth.value != "" && !isInteger(editconnection.bandwidth.value))
		{
			alert("Enter a valid number, or blank for no limit");
			editconnection.bandwidth.focus();
			return false;
		}
		if (editconnection.connections.value == "" || !isInteger(editconnection.connections.value))
		{
			alert("Enter a valid number for the max number of open connections per server");
			editconnection.connections.focus();
			return false;
		}
		if (editconnection.fetches.value != "" && !isInteger(editconnection.fetches.value))
		{
			alert("Enter a valid number, or blank for no limit");
			editconnection.fetches.focus();
			return false;
		}
		return true;
	}

	function checkConfigForSave()
	{
		if (editconnection.email.value == "")
		{
			alert("Email address required, to be included in all request headers");
			SelectTab("Email");
			editconnection.email.focus();
			return false;
		}
		return true;
	}

//-->
</script>

