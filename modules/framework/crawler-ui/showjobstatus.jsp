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
		MetaCarta Administration: Status of all jobs
	</title>

	<script type="text/javascript">
	<!--

	function Start(jobID)
	{
		document.liststatuses.op.value="Start";
		document.liststatuses.jobid.value=jobID;
		document.liststatuses.submit();
	}

	function Abort(jobID)
	{
		document.liststatuses.op.value="Abort";
		document.liststatuses.jobid.value=jobID;
		document.liststatuses.submit();
	}

	function Restart(jobID)
	{
		document.liststatuses.op.value="Restart";
		document.liststatuses.jobid.value=jobID;
		document.liststatuses.submit();
	}

	function Pause(jobID)
	{
		document.liststatuses.op.value="Pause";
		document.liststatuses.jobid.value=jobID;
		document.liststatuses.submit();
	}

	function Resume(jobID)
	{
		document.liststatuses.op.value="Resume";
		document.liststatuses.jobid.value=jobID;
		document.liststatuses.submit();
	}

	//-->
	</script>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="window">
	<p class="windowtitle">Status of Jobs</p>
<%
if (maintenanceUnderway == false)
{
%>
	<form class="standardform" name="liststatuses" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="job"/>
		<input type="hidden" name="jobid" value=""/>

<%
    try
    {
	// Get the job manager handle
	IJobManager manager = JobManagerFactory.make(threadContext);
	JobStatus[] jobs = manager.getAllStatus();
%>
		<table class="datatable">
			<tr>
				<td class="separator" colspan="8"><hr/></td>
			</tr>
			<tr class="headerrow">
				<td class="columnheader"></td><td class="columnheader">Name</td><td class="columnheader">Status</td><td class="columnheader">Start&nbsp;Time</td><td class="columnheader">End&nbsp;Time</td><td class="columnheader">Documents</td><td class="columnheader">Active</td><td class="columnheader">Processed</td>
			</tr>
<%
	int i = 0;
	while (i < jobs.length)
	{
		JobStatus js = jobs[i++];
		String statusName;
		int status = js.getStatus();
		switch (status)
		{
		case JobStatus.JOBSTATUS_NOTYETRUN:
			statusName = "Not yet run";
			break;
		case JobStatus.JOBSTATUS_RUNNING:
			statusName = "Running";
			break;
		case JobStatus.JOBSTATUS_RUNNING_UNINSTALLED:
			statusName = "Running, no connector";
			break;
		case JobStatus.JOBSTATUS_ABORTING:
			statusName = "Aborting";
			break;
		case JobStatus.JOBSTATUS_RESTARTING:
			statusName = "Restarting";
			break;
		case JobStatus.JOBSTATUS_PAUSED:
			statusName = "Paused";
			break;
		case JobStatus.JOBSTATUS_COMPLETED:
			statusName = "Done";
			break;
		case JobStatus.JOBSTATUS_WINDOWWAIT:
			statusName = "Waiting";
			break;
		case JobStatus.JOBSTATUS_STARTING:
			statusName = "Starting up";
			break;
		case JobStatus.JOBSTATUS_DESTRUCTING:
			statusName = "Cleaning up";
			break;
		case JobStatus.JOBSTATUS_JOBENDCLEANUP:
			statusName = "Terminating";
			break;
		case JobStatus.JOBSTATUS_ERROR:
			statusName = "Error: "+js.getErrorText();
			break;
		default:
			statusName = "Unknown";
			break;
		}
		String startTime = "Not started";
		if (js.getStartTime() != -1L)
			startTime = new Date(js.getStartTime()).toString();
		String endTime = "Aborted";
		if (js.getStartTime() == -1L)
			endTime = "Never run";
		else
		{
			if (js.getEndTime() == -1L)
			{
				if (status == JobStatus.JOBSTATUS_COMPLETED)
					endTime = "Aborted";
				else
					endTime = "";
			}
			else
				endTime = new Date(js.getEndTime()).toString();
		}
%>
		<tr <%="class=\""+((i%2==0)?"evendatarow":"odddatarow")+"\""%>>
			<td class="columncell">
<% if (status == JobStatus.JOBSTATUS_NOTYETRUN || status == JobStatus.JOBSTATUS_COMPLETED || status == JobStatus.JOBSTATUS_ERROR) { %>
	<a href='<%="javascript:Start(\""+js.getJobID()+"\")"%>' alt='<%="Start job "+js.getJobID()%>'>Start</a>&nbsp;
<% } %>
<% if (status == JobStatus.JOBSTATUS_RUNNING || status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED || status == JobStatus.JOBSTATUS_WINDOWWAIT ||
	status == JobStatus.JOBSTATUS_PAUSED || status == JobStatus.JOBSTATUS_STARTING) { %>
	<a href='<%="javascript:Restart(\""+js.getJobID()+"\")"%>' alt='<%="Restart job "+js.getJobID()%>'>Restart</a>&nbsp;
<% } %>
<% if (status == JobStatus.JOBSTATUS_RUNNING || status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED || status == JobStatus.JOBSTATUS_WINDOWWAIT) { %>
	<a href='<%="javascript:Pause(\""+js.getJobID()+"\")"%>' alt='<%="Pause job "+js.getJobID()%>'>Pause</a>&nbsp;
<% } %>
<% if (status == JobStatus.JOBSTATUS_RUNNING || status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED || status == JobStatus.JOBSTATUS_WINDOWWAIT ||
	status == JobStatus.JOBSTATUS_PAUSED || status == JobStatus.JOBSTATUS_STARTING || status == JobStatus.JOBSTATUS_RESTARTING) { %>
	<a href='<%="javascript:Abort(\""+js.getJobID()+"\")"%>' alt='<%="Abort job "+js.getJobID()%>'>Abort</a>&nbsp;
<% } %>
<% if (status == JobStatus.JOBSTATUS_PAUSED) { %>
	<a href='<%="javascript:Resume(\""+js.getJobID()+"\")"%>' alt='<%="Resume job "+js.getJobID()%>'>Resume</a>&nbsp;
<% } %>

			</td>
			<td class="columncell"><%="<!--jobid="+js.getJobID()+"-->"%><%=js.getDescription()%></td><td class="columncell"><%=statusName%></td><td class="columncell"><%=startTime%></td><td class="columncell"><%=endTime%></td>
			<td class="columncell"><%=new Long(js.getDocumentsInQueue()).toString()%></td>
			<td class="columncell"><%=new Long(js.getDocumentsOutstanding()).toString()%></td>
			<td class="columncell"><%=new Long(js.getDocumentsProcessed()).toString()%></td>
		</tr>
<%
	}
%>

			<tr>
				<td class="separator" colspan="8"><hr/></td>
			</tr>
		<tr><td class="message" colspan="8"><a href="showjobstatus.jsp" alt="Refresh status">Refresh</a></td></tr>
		</table>

<%
    }
    catch (LCFException e)
    {
	out.println("Error: "+e.getMessage());
	e.printStackTrace();
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
