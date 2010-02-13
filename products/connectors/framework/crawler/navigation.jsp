<%@ include file="adminDefaults.jsp" %>

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
	// This module is meant to be called within a table cell, and will drop the
	// metacarta navigation into the cell

%>

<p class="menumain"><nobr>Outputs</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listoutputs.jsp" alt="List authorities">List Output Connections</a></nobr>
	</li>
</ul>
<p class="menumain"><nobr>Authorities</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listauthorities.jsp" alt="List authorities">List Authority Connections</a></nobr>
	</li>
</ul>
<p class="menumain"><nobr>Repositories</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listconnections.jsp" alt="List repository connections">List Repository Connections</a></nobr>
	</li>
</ul>
<p class="menumain"><nobr>Jobs</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listjobs.jsp" alt="List jobs">List all Jobs</a></nobr>						
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="showjobstatus.jsp" alt="Manage jobs">Status and Job Management</a></nobr>						
	</li>
</ul>
<p class="menumain"><nobr>Status Reports</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="documentstatus.jsp" alt="Document status">Document Status</a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="queuestatus.jsp" alt="Queue status">Queue Status</a></nobr>
	</li>
</ul>
<p class="menumain"><nobr>History Reports</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="simplereport.jsp" alt="Simple history">Simple History</a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="maxactivityreport.jsp" alt="Maximum activity">Maximum Activity</a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="maxbandwidthreport.jsp" alt="Maximum bandwidth">Maximum Bandwidth</a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="resultreport.jsp" alt="Result histogram">Result Histogram</a></nobr>
	</li>
</ul>
<p class="menumain"><nobr>Miscellaneous</nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="/documentation/ConnectorGuide.pdf" alt="Help">Help</a></nobr>
	</li>
</ul>