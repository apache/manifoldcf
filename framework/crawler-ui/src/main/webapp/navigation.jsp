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
	// lcf navigation into the cell

%>

<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Outputs")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listoutputs.jsp" alt="List authorities"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.ListOutputConnections")%></a></nobr>
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Authorities")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listauthorities.jsp" alt="List authorities"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.ListAuthorityConnections")%></a></nobr>
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Repositories")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listconnections.jsp" alt="List repository connections"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.ListRepositoryConnections")%></a></nobr>
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Jobs")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="listjobs.jsp" alt="List jobs"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.ListAllJobs")%></a></nobr>						
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="showjobstatus.jsp" alt="Manage jobs"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.StatusAndJobManagement")%></a></nobr>						
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.StatusReports")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="documentstatus.jsp" alt="Document status"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.DocumentStatus")%></a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="queuestatus.jsp" alt="Queue status"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.QueueStatus")%></a></nobr>
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.HistoryReports")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="simplereport.jsp" alt="Simple history"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.SimpleHistory")%></a></nobr>
	</li>
<!-- hozawa
	<li class="menuitem">
		<nobr><a class="menulink" href="maxactivityreport.jsp" alt="Maximum activity"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.MaximumActivity")%></a></nobr>
//-->
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="maxbandwidthreport.jsp" alt="Maximum bandwidth"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.MaximumBandwidth")%></a></nobr>
	</li>
	<li class="menuitem">
		<nobr><a class="menulink" href="resultreport.jsp" alt="Result histogram"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.ResultHistogram")%></a></nobr>
	</li>
</ul>
<p class="menumain"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Miscellaneous")%></nobr></p>
<ul class="menusecond">
	<li class="menuitem">
		<nobr><a class="menulink" href="http://incubator.apache.org/connectors/end-user-documentation.html" alt="Help"><%=Messages.getString(pageContext.getRequest().getLocale(),"navigation.Help")%></a></nobr>
	</li>
</ul>
