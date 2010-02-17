<%@ include file="adminHeaders.jsp" %>

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
boolean maintenanceUnderway = org.apache.lcf.crawler.system.LCF.checkMaintenanceUnderway();

%>



<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		MetaCarta Administration: Document Status
	</title>

	<script type="text/javascript">
	<!--

	function Go()
	{
		if (!isInteger(report.rowcount.value))
		{
			alert("Enter a legal number for rows per page");
			report.rowcount.focus();
			return;
		}
		if (!isRegularExpression(report.statusidentifiermatch.value))
		{
			alert("Identifier match must be a valid regular expression");
			report.statusidentifiermatch.focus();
			return;
		}
		
		document.report.op.value="Status";
		document.report.action = document.report.action + "#MainButton";
		document.report.submit();
	}

	function Continue()
	{
		if (!isRegularExpression(report.statusidentifiermatch.value))
		{
			alert("Identifier match must be a valid regular expression");
			report.statusidentifiermatch.focus();
			return;
		}
		document.report.op.value="Continue";
		document.report.action = document.report.action + "#MainButton";
		document.report.submit();
	}

	function ColumnClick(colname)
	{
		document.report.clickcolumn.value = colname;
		Go();
	}

	function SetPosition(amt)
	{
		if (amt < 0)
			amt = 0;
		document.report.startrow.value = amt;
		Go();
	}

	function isRegularExpression(value)
	{
		try
		{
			var foo = "teststring";
                        foo.search(value.replace(/\(\?i\)/,""));
			return true;
		}
		catch (e)
		{
			return false;
		}

	}

	function isInteger(value)
	{
		var anum=/(^\d+$)/;
		return anum.test(value);
	}

	//-->
	</script>


</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="window">
	<p class="windowtitle">Document Status</p>
<%
if (maintenanceUnderway == false)
{
	int k;

	// Read the document selection parameters.
	
	// The status report is connection based, so the connection has to be selected before anything else makes sense.
	String statusConnection = variableContext.getParameter("statusconnection");
	if (statusConnection == null)
		statusConnection = "";
	
	// Which jobs we care about also figure in the selection part of the query.  It is the user's responsibility to pick jobs
	// that are in desired states.
	String[] statusJobIdentifiers = variableContext.getParameterValues("statusjobs");
	if (statusJobIdentifiers == null)
		statusJobIdentifiers = new String[0];

	// We can select documents from the queue based on the earliest time they can be acted upon.  This is specified in
	// a delta in minutes offset from "now".  Empty means that we don't want to select on that criteria.
	String activeTimeOffsetMinutes = variableContext.getParameter("statusscheduleoffset");
	if (activeTimeOffsetMinutes == null)
		activeTimeOffsetMinutes = "";
	
	// There is a selection criteria also based on the document state; these are integers defined in IJobManager.
	String[] documentStateTypes = variableContext.getParameterValues("statusdocumentstates");
	if (documentStateTypes == null)
		documentStateTypes = new String[0];
	
	// There is a selection criteria based on the document status; these are also integers defined in IJobManager.
	String[] documentStatusTypes = variableContext.getParameterValues("statusdocumentstatuses");
	if (documentStatusTypes == null)
		documentStatusTypes = new String[0];

	// Match string for the document identifier
	String identifierMatch = variableContext.getParameter("statusidentifiermatch");
	if (identifierMatch == null)
		identifierMatch = "";

	// From the passed-in selection values, calculate the actual selection criteria that we'll use in the queries.
	IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
	IRepositoryConnection[] connList = connMgr.getAllConnections();
	
	IJobManager jobManager = JobManagerFactory.make(threadContext);

	// Repository connection name: This simply needs to be mapped to an eligible list of identifiers.
	IJobDescription[] eligibleList = null;
	HashMap selectedJobs = null;
	if (statusConnection.length() > 0)
	{
		eligibleList = jobManager.findJobsForConnection(statusConnection);
		selectedJobs = new HashMap();
		k = 0;
		while (k < statusJobIdentifiers.length)
		{
			Long identifier = new Long(statusJobIdentifiers[k++]);
			selectedJobs.put(identifier,identifier);
		}
	}
	
	// Time offset: Need to calculate the actual time in ms since epoch to use to query against the "checktime" field.
	// Note that the checktime field is actually nullable and will only have a value when the document is in certain states;
	// therefore, the query itself will only include checktime for those states where it makes sense.  An empty value
	// means "from the beginning of time", or is equivalent to time 0.
	long nowTime = 0L;
	if (activeTimeOffsetMinutes.length() > 0)
	{
		nowTime = System.currentTimeMillis() + (new Long(activeTimeOffsetMinutes).longValue()) * 60000L;
		if (nowTime < 0L)
			nowTime = 0L;
	}
	else
		nowTime = System.currentTimeMillis();

	// Translate the states from a string to a number that will be understood by IJobManager.
	int[] matchingStates = new int[documentStateTypes.length];
	HashMap matchingStatesHash = new HashMap();
	k = 0;
	while (k < matchingStates.length)
	{
		Integer state = new Integer(documentStateTypes[k]);
		matchingStatesHash.put(state,state);
		matchingStates[k] = state.intValue();
		k++;
	}
	
	// Convert the status from a string to a number that will be understood by IJobManager
	int[] matchingStatuses = new int[documentStatusTypes.length];
	HashMap matchingStatusesHash = new HashMap();
	k = 0;
	while (k < matchingStatuses.length)
	{
		Integer status = new Integer(documentStatusTypes[k]);
		matchingStatusesHash.put(status,status);
		matchingStatuses[k] = status.intValue();
		k++;
	}
	
%>
	<form class="standardform" name="report" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="documentstatus"/>
		<table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1">Connection:</td><td class="value" colspan="1">
					<select name="statusconnection" size="3">
						<option <%=(statusConnection.length()==0)?"selected=\"selected\"":""%> value="">-- Not specified --</option>
<%
	int i = 0;
	while (i < connList.length)
	{
		IRepositoryConnection conn = connList[i++];
		String thisConnectionName = conn.getName();
		String thisDescription = conn.getDescription();
		if (thisDescription == null || thisDescription.length() == 0)
			thisDescription = thisConnectionName;
%>
						<option <%=(thisConnectionName.equals(statusConnection))?"selected=\"selected\"":""%> value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(thisConnectionName)%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
	}
%>
					</select>
				</td>
<%
	if (eligibleList != null)
	{
%>
				<td class="description" colspan="1">Jobs:</td><td class="value" colspan="1">
					<select multiple="true" name="statusjobs" size="3">
<%
	    i = 0;
	    while (i < eligibleList.length)
	    {
		IJobDescription job = eligibleList[i++];
		String description = job.getDescription();
		Long identifier = job.getID();
%>
						<option <%=((selectedJobs.get(identifier)==null)?"":"selected=\"selected\"")%> value='<%=identifier.toString()%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></option>
<%
	    }
%>
					</select>
				</td>
<%
	}
	else
	{
%>
				<td class="value" colspan="2"></td>
<%
	}
%>

			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description">Time offset from now (minutes):</td>
				<td class="value" colspan="3">
					<input name="statusscheduleoffset" type="text" size="6" value=""/>
				</td>
			</tr>
			<tr>
				<td class="description">Document state:</td>
				<td class="value" colspan="3">
					<select name="statusdocumentstates" multiple="true" size="3">
						<option <%=((matchingStatesHash.get(new Integer(IJobManager.DOCSTATE_NEVERPROCESSED))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATE_NEVERPROCESSED)%>'>Documents that have never been processed</option>
						<option <%=((matchingStatesHash.get(new Integer(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED)%>'>Documents processed at least once</option>
					</select>
				</td>
			</tr>
			<tr>
				<td class="description">Document status:</td>
				<td class="value" colspan="3">
					<select name="statusdocumentstatuses" multiple="true" size="3">
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_INACTIVE))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_INACTIVE)%>'>Documents that are no longer active</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_PROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_PROCESSING)%>'>Documents currently in progress</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_EXPIRING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_EXPIRING)%>'>Documents currently being expired</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_DELETING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_DELETING)%>'>Documents currently being deleted</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_READYFORPROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_READYFORPROCESSING)%>'>Documents currently available for processing</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_READYFOREXPIRATION))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_READYFOREXPIRATION)%>'>Documents currently available for expiration</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFORPROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFORPROCESSING)%>'>Documents not yet processable</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION)%>'>Documents not yet expirable</option>
						<option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFOREVER))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFOREVER)%>'>Documents waiting forever</option>
					</select>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr>Document identifier match:</nobr></td>
				<td class="value" colspan="3"><input type="text" name="statusidentifiermatch" size="40" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(identifierMatch)%>'/></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="message" colspan="4">
<%
	if (statusConnection.length() > 0 && statusJobIdentifiers.length > 0)
	{
%>
					<a name="MainButton"><input type="button" value="Go" onClick="javascript:Go()" alt="Execute this query"/></a>
<%
	}
	else
	{
%>
					<a name="MainButton"><input type="button" value="Continue" onClick="javascript:Continue()" alt="Continue"/></a>
<%
	}
%>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>

		</table>
<%
	if (statusConnection.length() > 0)
	{
	    if (statusJobIdentifiers.length > 0)
	    {
		// Run the report.

		// First, we need to gather the sort order object.
		String sortOrderString = variableContext.getParameter("sortorder");
		SortOrder sortOrder;
		if (sortOrderString == null || sortOrderString.length() == 0)
			sortOrder = new SortOrder();
		else
			sortOrder = new SortOrder(sortOrderString);

		// Now, gather the column header that was clicked on (if any)
		String clickedColumn = variableContext.getParameter("clickcolumn");
		if (clickedColumn != null && clickedColumn.length() > 0)
			sortOrder.clickColumn(clickedColumn);

		// Gather the start
		String startRowString = variableContext.getParameter("startrow");
		int startRow = 0;
		if (startRowString != null && startRowString.length() > 0)
			startRow = Integer.parseInt(startRowString);

		// Gather the max
		String maxRowCountString = variableContext.getParameter("rowcount");
		int rowCount = 20;
		if (maxRowCountString != null && maxRowCountString.length() > 0)
			rowCount = Integer.parseInt(maxRowCountString);

		Long[] ourJobs = new Long[selectedJobs.size()];
		Iterator iter = selectedJobs.keySet().iterator();
		int zz = 0;
		while (iter.hasNext())
		{
			ourJobs[zz++] = (Long)iter.next();
		}

		RegExpCriteria identifierMatchObject = null;
		if (identifierMatch.length() > 0)
			identifierMatchObject = new RegExpCriteria(identifierMatch,true);
		StatusFilterCriteria criteria = new StatusFilterCriteria(ourJobs,nowTime,identifierMatchObject,matchingStates,matchingStatuses);

		IResultSet set = jobManager.genDocumentStatus(statusConnection,criteria,sortOrder,startRow,rowCount+1);

%>
		<input type="hidden" name="clickcolumn" value=""/>
		<input type="hidden" name="startrow" value='<%=Integer.toString(startRow)%>'/>
		<input type="hidden" name="sortorder" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(sortOrder.toString())%>'/>

		<table class="displaytable">
		    <tr class="headerrow">
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("identifier");'><nobr>Identifier</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("job");'><nobr>Job</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("state");'><nobr>State</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("status");'><nobr>Status</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("scheduled");'><nobr>Scheduled</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("action");'><nobr>Scheduled Action</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("retrycount");'><nobr>Retry Count</nobr></a></td>
			<td class="reportcolumnheader"><a href="javascript:void(0);" onclick='javascript:ColumnClick("retrylimit");'><nobr>Retry Limit</nobr></a></td>
		    </tr>
<%
		zz = 0;
		boolean hasMoreRows = (set.getRowCount() > rowCount);
		int iterCount = hasMoreRows?rowCount:set.getRowCount();
		while (zz < iterCount)
		{
		    IResultRow row = set.getRow(zz);
		    
		    // Translate column values into something that can be reasonably displayed.
		    // Note that the actual hard work of translating things to human-readable strings largely is done by the query itself; this is because
		    // we want to sort on the columns, so it has to be that way.
		    
		    String[] identifierBreakdown = org.apache.lcf.ui.util.Formatter.formatString(row.getValue("identifier").toString(),64,true,true);
		    Long scheduleTime = (Long)row.getValue("scheduled");
		    String scheduleTimeString = "";
		    if (scheduleTime != null)
			scheduleTimeString = org.apache.lcf.ui.util.Formatter.formatTime(scheduleTime.longValue());
		    String scheduledActionString = (String)row.getValue("action");
		    if (scheduledActionString == null)
		        scheduledActionString = "";
		    Long retryCount = (Long)row.getValue("retrycount");
		    String retryCountString = "";
		    if (retryCount != null)
			retryCountString = retryCount.toString();
		    Long retryLimit = (Long)row.getValue("retrylimit");
		    String retryLimitString = "";
		    if (retryLimit != null)
		        retryLimitString = org.apache.lcf.ui.util.Formatter.formatTime(retryLimit.longValue());
			
%>
		    <tr <%="class=\""+((zz%2==0)?"evendatarow":"odddatarow")+"\""%>>
			<td class="reportcolumncell">
<%
		    int q = 0;
		    while (q < identifierBreakdown.length)
		    {
%>
				<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(identifierBreakdown[q++])%></nobr><br/>
<%
		    }
%>
			</td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(row.getValue("job").toString())%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(row.getValue("state").toString())%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(row.getValue("status").toString())%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(scheduleTimeString)%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(scheduledActionString)%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(retryCountString)%></td>
			<td class="reportcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(retryLimitString)%></td>
		    </tr>
<%
			zz++;
		}
%>
		</table>
		<table class="displaytable">
		    <tr>
			<td class="separator" colspan="5"><hr/></td>
		    </tr>
		    <tr>
			<td class="value">
				<nobr>
<%
		if (startRow == 0)
		{
%>
				Previous
<%
		}
		else
		{
%>
				<a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow-rowCount)+");"%>' alt="Previous page">Previous</a>
<%
		}
%>
				&nbsp;
<%
		if (hasMoreRows == false)
		{
%>
				Next
<%
		}
		else
		{
%>
				<a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow+rowCount)+");"%>' alt="Next page">Next</a>
<%
		}
%>
				</nobr>
			</td>
			<td class="description"><nobr>Rows:</nobr></td><td class="value"><%=Integer.toString(startRow)%>-<%=(hasMoreRows?Integer.toString(startRow+rowCount-1):"END")%></td>
			<td class="description"><nobr>Rows per page:</nobr></td>
			<td class="value">
				<input type="text" name="rowcount" size="5" value='<%=Integer.toString(rowCount)%>'/>
			</td>
		    </tr>
		    <tr>
			<td class="separator" colspan="5"><hr/></td>
		    </tr>
		</table>

<%
	    }
	    else
	    {
%>
		<table class="displaytable"><tr><td class="message">Please select at least one job</td></tr></table>
<%
	    }
	}
	else
	{
%>
		<table class="displaytable"><tr><td class="message">Please select a connection</td></tr></table>
<%
	}
%>
	</form>
<%
}
else
{
%>
		<table class="displaytable">
			<tr><td class="separator" colspan="1"><hr/></td></tr>
			<tr><td class="message">This page is unavailable due to maintenance operations.  Please try again later.</td></tr>
		</table>
<%
}
%>
       </td>
      </tr>
    </table>

</body>

</html>
