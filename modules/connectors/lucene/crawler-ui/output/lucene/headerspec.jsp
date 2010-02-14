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
	// This file is included in the head section by every place that the specification information for the GTS connector
	// needs to be edited.  When it is called, the OutputSpecification object is placed in the thread context
	// under the name "OutputSpecification".  The IOutputConnection object is also in the thread context,
	// under the name "OutputConnection".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editspec.jsp for this connector.
	//
	// The function checkOutputSpecificationOnSave() is called prior to the save operation, and serves to allow the
	// connector code to check any form data.  This should return a boolean false value if the form should NOT be submitted.

	OutputSpecification os = (OutputSpecification)threadContext.get("OutputSpecification");
	IOutputConnection outputConnection = (IOutputConnection)threadContext.get("OutputConnection");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");

%>

<%
	if (os == null)
		out.println("Hey!  No output specification came in!!!");
	if (outputConnection == null)
		out.println("No output connection!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

%>

<script type="text/javascript">
<!--

	function checkOutputSpecification()
	{
		return true;
	}

//-->
</script>

