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

	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");

	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Server");
%>

<script type="text/javascript">
<!--
	function checkConfigForSave()
	{
		if (editconnection.server.value == "")
		{
			alert("Need a server name");
			SelectTab("Server");
			editconnection.server.focus();
			return false;
		}

		if (editconnection.server.value.indexOf("/") != -1)
		{
			alert("Server name cannot include path information");
			SelectTab("Server");
			editconnection.server.focus();
			return false;
		}
		
		if (editconnection.username.value == "")
		{
			alert("Need a user name");
			SelectTab("Server");
			editconnection.username.focus();
			return false;
		}

		return true;
	}

//-->
</script>

