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
%>

<%
    // The contract of this edit page is as follows.  It is either called directly, in which case it is expected to be creating
    // a job or beginning the process of editing an existing job, or it is called via redirection from execute.jsp, in which case
    // the job object being edited will be placed in the thread context under the name "JobObject".
    // It may also be called directly with a parameter of "origjobid", which implies that a copy operation should be started.
    try
    {
	// Get the job manager handle
	IJobManager manager = JobManagerFactory.make(threadContext);
	IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
	IRepositoryConnection[] connList = connMgr.getAllConnections();
	IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(threadContext);
	IOutputConnection[] outputList = outputMgr.getAllConnections();

	// Figure out tab name
	String tabName = variableContext.getParameter("tabname");
	if (tabName == null || tabName.length() == 0)
		tabName = "Name";

	// Get a loaded job object, somehow.
	String jobID = null;
	IJobDescription job = (IJobDescription)threadContext.get("JobObject");
	if (job == null)
	{
		// We did not go through execute.jsp
		// We might have received an argument specifying the connection name.
		jobID = variableContext.getParameter("jobid");
		String origJobID = variableContext.getParameter("origjobid");
		if (origJobID == null || origJobID.length() == 0)
			origJobID = jobID;
		if (origJobID != null)
			job = manager.load(new Long(origJobID));
	}
	else
		jobID = job.getID().toString();

	// Setup default fields
	String connectionName = "";
	String outputName = "";
	String description = "";
	int type = IJobDescription.TYPE_SPECIFIED;
	OutputSpecification outputSpecification = new OutputSpecification();
	DocumentSpecification documentSpecification = new DocumentSpecification();
	ArrayList scheduleRecords = new ArrayList();

	EnumeratedValues dayOfWeek = null;
	EnumeratedValues dayOfMonth = null;
	EnumeratedValues monthOfYear = null;
	EnumeratedValues year = null;
	EnumeratedValues hourOfDay = null;
	EnumeratedValues minutesOfHour = null;
	// Duration in minutes
	Long duration = null;

	// Priority
	int priority = 5;
	// Minimum recrawl interval (Default: 1 day)
	Long recrawlInterval = new Long(60L * 24L);
	// Reseed interval (Default: 60 minutes)
	Long reseedInterval = new Long(60L);
	// Expiration interval (Default: never)
	Long expirationInterval = null;
	// Start method
	int startMethod = IJobDescription.START_DISABLE;
	// Hopcount mode
	int hopcountMode = IJobDescription.HOPCOUNT_ACCURATE;
	// Hop filters
	Map hopFilterMap = new HashMap();

	// If the job is not null, prepopulate everything with what comes from it.
	if (job != null)
	{
		// Set up values
		description = job.getDescription();
		outputName = job.getOutputConnectionName();
		connectionName = job.getConnectionName();
		type = job.getType();
		startMethod = job.getStartMethod();
		hopcountMode = job.getHopcountMode();
		outputSpecification = job.getOutputSpecification();
		documentSpecification = job.getSpecification();
		// Fill in schedule records from job
		int j = 0;
		while (j < job.getScheduleRecordCount())
		{
			scheduleRecords.add(job.getScheduleRecord(j++));
		}

		priority = job.getPriority();
		Long value = job.getInterval();
		recrawlInterval = (value==null)?null:new Long(value.longValue()/60000L);
		value = job.getReseedInterval();
		reseedInterval = (value==null)?null:new Long(value.longValue()/60000L);
		value = job.getExpiration();
		expirationInterval = (value==null)?null:new Long(value.longValue()/60000L);
		hopFilterMap = job.getHopCountFilters();
	}


	// This form reposts to itself.  It basically only allows the connection to be picked once; once done, the repost occurs
	// and cannot be undone.
	// Therefore, there are three possible entry conditions:
	// 1) no jobid w/no connection name, which indicates a brand-new job without a chosen connection
	// 2) no jobid w/a connection name, which indicates that the connection at least has been chosen
	// 3) a jobid and a connection name, which indicates that we are editing an existing connection.
	// There are similar combinations for output connections.

	int model = IRepositoryConnector.MODEL_ADD_CHANGE_DELETE;
	String[] relationshipTypes = null;
	ArrayList tabsArray = new ArrayList();
	
	IRepositoryConnection connection = null;
	IOutputConnection outputConnection = null;
	if (connectionName.length() > 0)
	{
		connection = connMgr.load(connectionName);
		model = RepositoryConnectorFactory.getConnectorModel(threadContext,connection.getClassName());
		relationshipTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
	}
	if (outputName.length() > 0)
	{
		outputConnection = outputMgr.load(outputName);
	}

	// Set up the predefined tabs
	tabsArray.add("Name");
	tabsArray.add("Connection");
	if (connectionName.length() > 0)
	{
		tabsArray.add("Scheduling");
		if (relationshipTypes != null && relationshipTypes.length > 0)
			tabsArray.add("Hop Filters");
	}


%>

<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		Apache ManifoldCF: Edit Job
	</title>

	<script type="text/javascript">
	<!--

	// Use this method to repost the form and pick a new tab
	function SelectTab(newtab)
	{
		if (checkForm())
		{
			document.editjob.tabname.value = newtab;
			document.editjob.submit();
		}
	}

	// Use this method to repost the form,
	// and set the anchor request.
	function postFormSetAnchor(anchorValue)
	{
		if (checkForm())
		{
			if (anchorValue != "")
				document.editjob.action = document.editjob.action + "#" + anchorValue;
			document.editjob.submit();
		}
	}

	// Use this method to repost the form
	function postFormNew()
	{
		if (checkForm())
		{
			document.editjob.submit();
		}
	}

	// Deprecated
	function postForm(schedCount)
	{
		if (checkForm())
		{
			document.editjob.submit();
		}
	}

	function Save()
	{
		if (checkForm())
		{
			// Can't submit until all required fields have been set.
			// Some of these don't live on the current tab, so don't set
			// focus.

			// Check our part of the form, for save
			if (editjob.description.value == "")
			{
				alert("Job must have a name");
				SelectTab("Name");
				document.editjob.description.focus();
				return;
			}
			if (window.checkOutputSpecificationForSave)
			{
				if (checkOutputSpecificationForSave() == false)
					return;
			}
			if (window.checkSpecificationForSave)
			{
				if (checkSpecificationForSave() == false)
					return;
			}
			document.editjob.op.value="Save";
			document.editjob.submit();
		}
	}

	function Cancel()
	{
		document.editjob.op.value="Cancel";
		document.editjob.submit();
	}

	function Continue()
	{
		document.editjob.op.value="Continue";
		postFormNew();
	}

	function AddScheduledTime()
	{
		if (editjob.duration.value != "" && !isInteger(editjob.duration.value))
		{
			alert("Duration must be a valid integer");
			editjob.duration.focus();
			return;
		}
		document.editjob.recordop.value="Add Scheduled Time";
		postFormSetAnchor("add_schedule");
	}

	function RemoveSchedule(n)
	{
		eval("document.editjob.recordop"+n+".value = 'Remove Schedule'");
		if (n == 0)
			postFormSetAnchor("add_schedule");
		else
			postFormSetAnchor("remove_schedule_"+(n-1));
	}

	function checkForm()
	{
		if (!checkRecrawl())
			return false;
		if (!checkReseed())
			return false;
		if (!checkExpiration())
			return false;
		if (!checkSchedule())
			return false;
		// Check the output connector part
		if (window.checkOutputSpecification)
		{
			if (checkOutputSpecification() == false)
				return false;
		}
		// Check the connector part
		if (window.checkSpecification)
		{
			if (checkSpecification() == false)
				return false;
		}
		return true;
	}

	function checkSchedule()
	{
		var i = 0;
		var schedCount = <%=Integer.toString(scheduleRecords.size())%>;
		while (i < schedCount)
		{
			var propertyname = "duration" + i;
			if (eval("editjob."+propertyname+".value") != "" && !isInteger(eval("editjob."+propertyname+".value")))
			{
				alert("Duration must be a valid integer");
				eval("editjob."+propertyname+".focus()");
				return false;
			}
			i++;
		}
		return true;
	}

	function checkRecrawl()
	{
		if (editjob.recrawlinterval.value != "" && !isInteger(editjob.recrawlinterval.value))
		{
			alert("Recrawl interval must be a valid integer or null");
			editjob.recrawlinterval.focus();
			return false;
		}
		return true;
	}

	function checkReseed()
	{
		if (editjob.reseedinterval.value != "" && !isInteger(editjob.reseedinterval.value))
		{
			alert("Reseed interval must be a valid integer or null");
			editjob.reseedinterval.focus();
			return false;
		}
		return true;
	}

	function checkExpiration()
	{
		if (editjob.expirationinterval.value != "" && !isInteger(editjob.expirationinterval.value))
		{
			alert("Expiration interval must be a valid integer or null");
			editjob.expirationinterval.focus();
			return false;
		}
		return true;
	}

	function isInteger(value)
	{
		var anum=/(^\d+$)/;
		return anum.test(value);
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

	//-->
	</script>
<%
	if (outputConnection != null)
	{
		IOutputConnector outputConnector = OutputConnectorFactory.grab(threadContext,outputConnection.getClassName(),outputConnection.getConfigParams(),
			outputConnection.getMaxConnections());
		if (outputConnector != null)
		{
			try
			{
				outputConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),outputSpecification,tabsArray);
			}
			finally
			{
				OutputConnectorFactory.release(outputConnector);
			}
		}
	}
%>

<%
	if (connection != null)
	{
		IRepositoryConnector repositoryConnector = RepositoryConnectorFactory.grab(threadContext,connection.getClassName(),connection.getConfigParams(),
			connection.getMaxConnections());
		if (repositoryConnector != null)
		{
			try
			{
				repositoryConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),documentSpecification,tabsArray);
			}
			finally
			{
				RepositoryConnectorFactory.release(repositoryConnector);
			}
		}
	}
%>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="darkwindow">
<%
	if (connList.length == 0)
	{
%>
	<p class="windowtitle">Edit a Job</p>
	<table class="displaytable"><tr><td class="message">No repository connections defined - create one first</td></tr></table>
<%
	}
	else if (outputList.length == 0)
	{
%>
	<p class="windowtitle">Edit a Job</p>
	<table class="displaytable"><tr><td class="message">No output connections defined - create one first</td></tr></table>
<%
	}
	else
	{
%>
	<form class="standardform" name="editjob" action="execute.jsp" method="POST" enctype="multipart/form-data">
	  <input type="hidden" name="op" value="Continue"/>
	  <input type="hidden" name="type" value="job"/>
	  <input type="hidden" name="index" value=""/>
	  <input type="hidden" name="tabname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tabName)%>'/>
<%
	if (jobID != null)
	{
%>
	  <input type="hidden" name="jobid" value='<%=jobID%>'/>
<%
	}
%>
	    <table class="tabtable">
	      <tr class="tabrow">
<%
	int tabNum = 0;
	while (tabNum < tabsArray.size())
	{
		String tab = (String)tabsArray.get(tabNum++);
		if (tab.equals(tabName))
		{
%>
		      <td class="activetab"><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></nobr></td>
<%
		}
		else
		{
%>
		      <td class="passivetab"><nobr><a href="javascript:void(0);" alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab+" tab")%>' onclick='<%="javascript:SelectTab(\""+tab+"\");return false;"%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a></nobr></td>
<%
		}
	}
%>
		      <td class="remaindertab">
<%
	if (description.length() > 0)
	{
%>
			  <nobr>Edit job '<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%>'</nobr>
<%
	}
	else
	{
%>
		          <nobr>Edit a Job</nobr>
<%
	}
%>
		      </td>
	      </tr>
	      <tr class="tabbodyrow">
		<td class="tabbody" colspan='<%=Integer.toString(tabsArray.size()+1)%>'>

		  <input type="hidden" name="schedulerecords" value='<%=Integer.toString(scheduleRecords.size())%>'/>
<%
	// The NAME tab
	if (tabName.equals("Name"))
	{
%>
		  <table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr>Name:</nobr></td><td class="value" colspan="3">
					<input type="text" size="50" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
				</td>
			</tr>
		  </table>
<%
	}
	else
	{
%>
		  <input type="hidden" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
<%
	}

	// Hop Filters tab
	if (tabName.equals("Hop Filters"))
	{
	    if (relationshipTypes != null)
	    {
%>
		  <table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><input type="hidden" name="hopfilters" value="true"/><hr/></td>
			</tr>
<%
		int i = 0;
		while (i < relationshipTypes.length)
		{
			String relationshipType = relationshipTypes[i++];
			String mapField = "";
			Long mapValue = (Long)hopFilterMap.get(relationshipType);
			if (mapValue != null)
				mapField = mapValue.toString();
%>
			<tr>
				<td class="description" colspan="1"><nobr>Maximum hop count for type '<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(relationshipType)%>':</nobr></td>
				<td class="value" colspan="3" >
					<input name='<%="hopmax_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(relationshipType)%>' type="text" size="5" value='<%=mapField%>'/>
				</td>
			</tr>
<%
		}
%>
			<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr>
				<td class="description" colspan="1"><nobr>Hop count mode:</nobr></td>
				<td class="value" colspan="3">
					<nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_ACCURATE)%>' <%=((hopcountMode==IJobDescription.HOPCOUNT_ACCURATE)?"checked=\"true\"":"")%>>Delete unreachable documents</input></nobr><br/>
					<nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_NODELETE)%>' <%=((hopcountMode==IJobDescription.HOPCOUNT_NODELETE)?"checked=\"true\"":"")%>>Keep unreachable documents, for now</input></nobr><br/>
					<nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_NEVERDELETE)%>' <%=((hopcountMode==IJobDescription.HOPCOUNT_NEVERDELETE)?"checked=\"true\"":"")%>>Keep unreachable documents, forever</input></nobr><br/>
				</td>
			</tr>
		  </table>
<%
	    }
	}
	else
	{
	    if (relationshipTypes != null)
	    {
%>
	<input type="hidden" name="hopfilters" value="true"/>
<%
		int i = 0;
		while (i < relationshipTypes.length)
		{
			String relationshipType = relationshipTypes[i++];
			String mapField = "";
			Long mapValue = (Long)hopFilterMap.get(relationshipType);
			if (mapValue != null)
				mapField = mapValue.toString();
%>
	<input name='<%="hopmax_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(relationshipType)%>' type="hidden" value='<%=mapField%>'/>
	<input name="hopcountmode" type="hidden" value='<%=Integer.toString(hopcountMode)%>'/>
<%
		}
	    }
	}

	// Connection tab
	if (tabName.equals("Connection"))
	{
%>
		  <table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
<%
	    if (outputName.length() == 0)
	    {
%>
				<td class="description"><nobr>Output connection:</nobr></td>
				<td class="value">
					<select name="outputname" size="1">
						<option <%="".equals(outputName)?"selected=\"selected\"":""%> value="">-- None selected --</option>
<%
		int j = 0;
		while (j < outputList.length)
		{
			IOutputConnection conn = outputList[j++];
%>
						<option <%=conn.getName().equals(outputName)?"selected=\"selected\"":""%> value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
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
				<td class="description"><nobr>Output connection:</nobr></td>
				<td class="value"><input type="hidden" name="outputname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(outputName)%>'/><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(outputName)%></td>
<%
	    }
%>

<%
	    if (connectionName.length() == 0)
	    {
%>
				<td class="description"><nobr>Repository connection:</nobr></td>
				<td class="value">
					<select name="connectionname" size="1">
						<option <%="".equals(connectionName)?"selected=\"selected\"":""%> value="">-- None selected --</option>
<%
		int j = 0;
		while (j < connList.length)
		{
			IRepositoryConnection conn = connList[j++];
%>
						<option <%=conn.getName().equals(connectionName)?"selected=\"selected\"":""%> value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
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
				<td class="description"><nobr>Repository connection:</nobr></td>
				<td class="value"><input type="hidden" name="connectionname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></td>
<%
	    }
%>
			</tr>
			<tr>
				<td class="description"><nobr>Priority:</nobr></td>
				<td class="value">
					<select name="priority" size="1">
						<option value="1" <%=(priority==1)?"selected=\"selected\"":""%>>1 (Highest)</option>
						<option value="2" <%=(priority==2)?"selected=\"selected\"":""%>>2</option>
						<option value="3" <%=(priority==3)?"selected=\"selected\"":""%>>3</option>
						<option value="4" <%=(priority==4)?"selected=\"selected\"":""%>>4</option>
						<option value="5" <%=(priority==5)?"selected=\"selected\"":""%>>5</option>
						<option value="6" <%=(priority==6)?"selected=\"selected\"":""%>>6</option>
						<option value="7" <%=(priority==7)?"selected=\"selected\"":""%>>7</option>
						<option value="8" <%=(priority==8)?"selected=\"selected\"":""%>>8</option>
						<option value="9" <%=(priority==9)?"selected=\"selected\"":""%>>9</option>
						<option value="10" <%=(priority==10)?"selected=\"selected\"":""%>>10 (Lowest)</option>
					</select>
				</td>
				<td class="description"><nobr>Start method:</nobr></td>
				<td class="value">
					<select name="startmethod" size="1">
						<option value='<%=IJobDescription.START_WINDOWBEGIN%>' <%=(startMethod==IJobDescription.START_WINDOWBEGIN)?"selected=\"selected\"":""%>>Start when schedule window starts</option>
						<option value='<%=IJobDescription.START_WINDOWINSIDE%>' <%=(startMethod==IJobDescription.START_WINDOWINSIDE)?"selected=\"selected\"":""%>>Start even inside a schedule window</option>
						<option value='<%=IJobDescription.START_DISABLE%>' <%=(startMethod==IJobDescription.START_DISABLE)?"selected=\"selected\"":""%>>Don't automatically start this job</option>
					</select>
				</td>
			</tr>
		  </table>
<%
	}
	else
	{
%>
		  <input type="hidden" name="outputname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(outputName)%>'/>
		  <input type="hidden" name="connectionname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
		  <input type="hidden" name="startmethod" value='<%=startMethod%>'/>
<%
	}

	// Scheduling tab
	if (tabName.equals("Scheduling"))
	{
%>
		  <table class="displaytable">
<%
	    if (model != -1 && model != IRepositoryConnector.MODEL_ADD_CHANGE_DELETE)
	    {
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr>Schedule type:</nobr></td><td class="value"><select name="scheduletype" size="1">
					<option value='<%=IJobDescription.TYPE_CONTINUOUS%>' <%=(type==IJobDescription.TYPE_CONTINUOUS)?"selected=\"selected\"":""%>>Rescan documents dynamically</option>
					<option value='<%=IJobDescription.TYPE_SPECIFIED%>' <%=(type==IJobDescription.TYPE_SPECIFIED)?"selected=\"selected\"":""%>>Scan every document once</option>
				</select></td><td class="description"><nobr>Recrawl interval (if continuous):</nobr></td><td class="value">
					<nobr><input type="text" size="5" name="recrawlinterval" value='<%=((recrawlInterval==null)?"":recrawlInterval.toString())%>'/> minutes (blank=infinity)</nobr>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Expiration interval (if continuous):</nobr></td>
				<td class="value">
					<nobr><input type="text" size="5" name="expirationinterval" value='<%=((expirationInterval==null)?"":expirationInterval.toString())%>'/> minutes (blank=infinity)</nobr>
				</td>
				<td class="description"><nobr>Reseed interval (if continuous):</nobr></td>
				<td class="value">
					<nobr><input type="text" size="5" name="reseedinterval" value='<%=((reseedInterval==null)?"":reseedInterval.toString())%>'/> minutes (blank=infinity)</nobr>
				</td>
			</tr>
<%
	    }
	    else
	    {
%>
			<input type="hidden" name="scheduletype" value='<%=type%>'/>
			<input type="hidden" name="recrawlinterval" value='<%=((recrawlInterval==null)?"":recrawlInterval.toString())%>'/>
			<input type="hidden" name="reseedinterval" value='<%=((reseedInterval==null)?"":reseedInterval.toString())%>'/>
			<input type="hidden" name="expirationinterval" value='<%=((expirationInterval==null)?"":expirationInterval.toString())%>'/>
<%
	    }
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
<%
	    if (scheduleRecords.size() == 0)
	    {
%>
			<tr><td class="message" colspan="4">No schedule specified</td></tr>
<%
	    }
	    else
	    {
	      int l = 0;
	      while (l < scheduleRecords.size())
	      {
		ScheduleRecord sr = (ScheduleRecord)scheduleRecords.get(l);
		Long srDuration = sr.getDuration();
		EnumeratedValues srDayOfWeek = sr.getDayOfWeek();
		EnumeratedValues srMonthOfYear = sr.getMonthOfYear();
		EnumeratedValues srDayOfMonth = sr.getDayOfMonth();
		EnumeratedValues srYear = sr.getYear();
		EnumeratedValues srHourOfDay = sr.getHourOfDay();
		EnumeratedValues srMinutesOfHour = sr.getMinutesOfHour();
		String postFix = Integer.toString(l);
		int k;

		if (l > 0)
		{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
<%
		}
%>
			<tr>
				<td class="description"><nobr>Scheduled time:</nobr></td>
				<td colspan="3" class="value">
				    <select class="schedulepulldown" multiple="true" name='<%="dayofweek"+postFix%>' size="3">
					<option value="none" <%=(srDayOfWeek==null)?"selected=\"selected\"":""%>>Any day of week</option>
					<option value="0" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(0))?"selected=\"selected\"":""%>>Sundays</option>
					<option value="1" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(1))?"selected=\"selected\"":""%>>Mondays</option>
					<option value="2" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(2))?"selected=\"selected\"":""%>>Tuesdays</option>
					<option value="3" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(3))?"selected=\"selected\"":""%>>Wednesdays</option>
					<option value="4" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(4))?"selected=\"selected\"":""%>>Thursdays</option>
					<option value="5" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(5))?"selected=\"selected\"":""%>>Fridays</option>
					<option value="6" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(6))?"selected=\"selected\"":""%>>Saturdays</option>
				    </select> at 
				    <select class="schedulepulldown" multiple="true" name='<%="hourofday"+postFix%>' size="3">
					<option value="none" <%=(srHourOfDay==null)?"selected=\"selected\"":""%>>Midnight/Any hour of day</option>
<%
					k = 0;
					while (k < 24)
					{
						int q = k;
						String ampm;
						if (k < 12)
							ampm = "am";
						else
						{
							ampm = "pm";
							q -= 12;
						}
						String hour;
						if (q == 0)
							q = 12;
%>
						<option value='<%=k%>' <%=(srHourOfDay!=null&&srHourOfDay.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(q)+" "+ampm%></option>
<%						
						k++;
					}
%>
				    </select> plus 
				    <select class="schedulepulldown" multiple="true" name='<%="minutesofhour"+postFix%>' size="3">
					<option value="none" <%=(srMinutesOfHour==null)?"selected=\"selected\"":""%>>Nothing</option>
<%
					k = 0;
					while (k < 60)
					{
%>
						<option value='<%=k%>' <%=(srMinutesOfHour!=null&&srMinutesOfHour.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k)%> minutes</option>
<%
						k++;
					}
%>
				    </select> in 
				    <select class="schedulepulldown" multiple="true" name='<%="monthofyear"+postFix%>' size="3">
					<option value="none" <%=(srMonthOfYear==null)?"selected=\"selected\"":""%>>Every month of year</option>
					<option value="0" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(0))?"selected=\"selected\"":""%>>January</option>
					<option value="1" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(1))?"selected=\"selected\"":""%>>February</option>
					<option value="2" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(2))?"selected=\"selected\"":""%>>March</option>
					<option value="3" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(3))?"selected=\"selected\"":""%>>April</option>
					<option value="4" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(4))?"selected=\"selected\"":""%>>May</option>
					<option value="5" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(5))?"selected=\"selected\"":""%>>June</option>
					<option value="6" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(6))?"selected=\"selected\"":""%>>July</option>
					<option value="7" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(7))?"selected=\"selected\"":""%>>August</option>
					<option value="8" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(8))?"selected=\"selected\"":""%>>September</option>
					<option value="9" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(9))?"selected=\"selected\"":""%>>October</option>
					<option value="10" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(10))?"selected=\"selected\"":""%>>November</option>
					<option value="11" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(11))?"selected=\"selected\"":""%>>December</option>
				    </select> on
				    <select class="schedulepulldown" multiple="true" name='<%="dayofmonth"+postFix%>' size="3">
					<option value="none" <%=(srDayOfMonth==null)?"selected=\"selected\"":""%>>Any day of month</option>
<%
					k = 0;
					while (k < 31)
					{
						int value = (k+1) % 10;
						String suffix;
						if (value == 1 && k != 10)
							suffix = "st";
						else if (value == 2 && k != 11)
							suffix = "nd";
						else if (value == 3 && k != 12)
							suffix = "rd";
						else
							suffix = "th";
%>
						<option value='<%=Integer.toString(k)%>' <%=(srDayOfMonth!=null&&srDayOfMonth.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix+" day of month"%></option>
<%
						k++;
					}
%>
				    </select><input type="hidden" name='<%="year"+postFix%>' value="none"/>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Maximum run time:</nobr></td><td colspan="3" class="value">
					<input type="text" size="5" name='<%="duration"+postFix%>' value='<%=((srDuration==null)?"":new Long(srDuration.longValue()/60000L).toString())%>'/> minutes
				</td>
			</tr>
			<tr>
				<td class="message" colspan="4">
					<a name='<%="remove_schedule_"+Integer.toString(l)%>'><input type="button" value="Remove Schedule" onClick='<%="Javascript:RemoveSchedule("+Integer.toString(l)+")"%>' alt='<%="Remove schedule record #"+Integer.toString(l)%>'/></a>
					<input type="hidden" name='<%="recordop"+postFix%>' value=""/>
				</td>
			</tr>
<%
		l++;
	      }
	    }
%>

			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr>Scheduled time:</nobr></td>
				<td colspan="3" class="value">
				    <select class="schedulepulldown" multiple="true" name="dayofweek" size="3">
					<option value="none" <%=(dayOfWeek==null)?"selected=\"selected\"":""%>>Any day of week</option>
					<option value="0" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(0))?"selected=\"selected\"":""%>>Sundays</option>
					<option value="1" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(1))?"selected=\"selected\"":""%>>Mondays</option>
					<option value="2" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(2))?"selected=\"selected\"":""%>>Tuesdays</option>
					<option value="3" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(3))?"selected=\"selected\"":""%>>Wednesdays</option>
					<option value="4" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(4))?"selected=\"selected\"":""%>>Thursdays</option>
					<option value="5" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(5))?"selected=\"selected\"":""%>>Fridays</option>
					<option value="6" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(6))?"selected=\"selected\"":""%>>Saturdays</option>
				    </select> at 
				    <select class="schedulepulldown" multiple="true" name="hourofday" size="3">
					<option value="none" <%=(hourOfDay==null)?"selected=\"selected\"":""%>>Midnight/Any hour of day</option>
<%
					int k = 0;
					while (k < 24)
					{
						int q = k;
						String ampm;
						if (k < 12)
							ampm = "am";
						else
						{
							ampm = "pm";
							q -= 12;
						}
						String hour;
						if (q == 0)
							q = 12;
%>
						<option value='<%=k%>' <%=(hourOfDay!=null&&hourOfDay.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(q)+" "+ampm%></option>
<%						
						k++;
					}
%>
				    </select> plus 
				    <select class="schedulepulldown" multiple="true" name="minutesofhour" size="3">
					<option value="none" <%=(minutesOfHour==null)?"selected=\"selected\"":""%>>Nothing</option>
<%
					k = 0;
					while (k < 60)
					{
%>
						<option value='<%=k%>' <%=(minutesOfHour!=null&&minutesOfHour.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k)%> minutes</option>
<%
						k++;
					}
%>
				    </select> in 
				    <select class="schedulepulldown" multiple="true" name="monthofyear" size="3">
					<option value="none" <%=(monthOfYear==null)?"selected=\"selected\"":""%>>Every month of year</option>
					<option value="0" <%=(monthOfYear!=null&&monthOfYear.checkValue(0))?"selected=\"selected\"":""%>>January</option>
					<option value="1" <%=(monthOfYear!=null&&monthOfYear.checkValue(1))?"selected=\"selected\"":""%>>February</option>
					<option value="2" <%=(monthOfYear!=null&&monthOfYear.checkValue(2))?"selected=\"selected\"":""%>>March</option>
					<option value="3" <%=(monthOfYear!=null&&monthOfYear.checkValue(3))?"selected=\"selected\"":""%>>April</option>
					<option value="4" <%=(monthOfYear!=null&&monthOfYear.checkValue(4))?"selected=\"selected\"":""%>>May</option>
					<option value="5" <%=(monthOfYear!=null&&monthOfYear.checkValue(5))?"selected=\"selected\"":""%>>June</option>
					<option value="6" <%=(monthOfYear!=null&&monthOfYear.checkValue(6))?"selected=\"selected\"":""%>>July</option>
					<option value="7" <%=(monthOfYear!=null&&monthOfYear.checkValue(7))?"selected=\"selected\"":""%>>August</option>
					<option value="8" <%=(monthOfYear!=null&&monthOfYear.checkValue(8))?"selected=\"selected\"":""%>>September</option>
					<option value="9" <%=(monthOfYear!=null&&monthOfYear.checkValue(9))?"selected=\"selected\"":""%>>October</option>
					<option value="10" <%=(monthOfYear!=null&&monthOfYear.checkValue(10))?"selected=\"selected\"":""%>>November</option>
					<option value="11" <%=(monthOfYear!=null&&monthOfYear.checkValue(11))?"selected=\"selected\"":""%>>December</option>
				    </select> on 
				    <select class="schedulepulldown" multiple="true" name="dayofmonth" size="3">
					<option value="none" <%=(dayOfMonth==null)?"selected=\"selected\"":""%>>Any day of month</option>
<%
					k = 0;
					while (k < 31)
					{
						int value = (k+1) % 10;
						String suffix;
						if (value == 1 && k != 10)
							suffix = "st";
						else if (value == 2 && k != 11)
							suffix = "nd";
						else if (value == 3 && k != 12)
							suffix = "rd";
						else
							suffix = "th";
%>
						<option value='<%=Integer.toString(k)%>' <%=(dayOfMonth!=null&&dayOfMonth.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix+" day of month"%></option>
<%
						k++;
					}
%>
				    </select><input type="hidden" name="year" value="none"/>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Maximum run time:</nobr></td><td colspan="3" class="value">
					<input type="text" size="5" name="duration" value='<%=((duration==null)?"":duration.toString())%>'/> minutes
				</td>
			</tr>
			<tr>
				<td class="message" colspan="4">
					<input type="hidden" name="recordop" value=""/>
					<a name="add_schedule"><input type="button" value="Add Scheduled Time" onClick="javascript:AddScheduledTime()" alt="Add new schedule record"/></a>
				</td>
			</tr>
		  </table>
<%
	}
	else
	{
%>
		  <input type="hidden" name="scheduletype" value='<%=type%>'/>
		  <input type="hidden" name="recrawlinterval" value='<%=((recrawlInterval==null)?"":recrawlInterval.toString())%>'/>
		  <input type="hidden" name="reseedinterval" value='<%=((reseedInterval==null)?"":reseedInterval.toString())%>'/>
		  <input type="hidden" name="expirationinterval" value='<%=((expirationInterval==null)?"":expirationInterval.toString())%>'/>
<%

	      int l = 0;
	      while (l < scheduleRecords.size())
	      {
		ScheduleRecord sr = (ScheduleRecord)scheduleRecords.get(l);
		Long srDuration = sr.getDuration();
		EnumeratedValues srDayOfWeek = sr.getDayOfWeek();
		EnumeratedValues srMonthOfYear = sr.getMonthOfYear();
		EnumeratedValues srDayOfMonth = sr.getDayOfMonth();
		EnumeratedValues srYear = sr.getYear();
		EnumeratedValues srHourOfDay = sr.getHourOfDay();
		EnumeratedValues srMinutesOfHour = sr.getMinutesOfHour();
		String postFix = Integer.toString(l);

		if (srDayOfWeek == null)
		{
%>
		  <input type="hidden" name='<%="dayofweek"+postFix%>' value="none"/>
<%
		}
		else
		{
			Iterator iter = srDayOfWeek.getValues();
			while (iter.hasNext())
			{
				Integer value = (Integer)iter.next();
%>
		  <input type="hidden" name='<%="dayofweek"+postFix%>' value='<%=value%>'/>
<%
			}
		}

		if (srHourOfDay == null)
		{
%>
		  <input type="hidden" name='<%="hourofday"+postFix%>' value="none"/>
<%
		}
		else
		{
			Iterator iter = srHourOfDay.getValues();
			while (iter.hasNext())
			{
				Integer value = (Integer)iter.next();
%>
		  <input type="hidden" name='<%="hourofday"+postFix%>' value='<%=value%>'/>
<%
			}
		}

		if (srMinutesOfHour == null)
		{
%>
		  <input type="hidden" name='<%="minutesofhour"+postFix%>' value="none"/>
<%
		}
		else
		{
			Iterator iter = srMinutesOfHour.getValues();
			while (iter.hasNext())
			{
				Integer value = (Integer)iter.next();
%>
		  <input type="hidden" name='<%="minutesofhour"+postFix%>' value='<%=value%>'/>
<%
			}
		}

		if (srDayOfMonth == null)
		{
%>
		  <input type="hidden" name='<%="dayofmonth"+postFix%>' value="none"/>
<%
		}
		else
		{
			Iterator iter = srDayOfMonth.getValues();
			while (iter.hasNext())
			{
				Integer value = (Integer)iter.next();
%>
		  <input type="hidden" name='<%="dayofmonth"+postFix%>' value='<%=value%>'/>
<%
			}
		}

		if (srMonthOfYear == null)
		{
%>
		  <input type="hidden" name='<%="monthofyear"+postFix%>' value="none"/>
<%
		}
		else
		{
			Iterator iter = srMonthOfYear.getValues();
			while (iter.hasNext())
			{
				Integer value = (Integer)iter.next();
%>
		  <input type="hidden" name='<%="monthofyear"+postFix%>' value='<%=value%>'/>
<%
			}
		}
%>
		  <input type="hidden" name='<%="duration"+postFix%>' value='<%=((srDuration==null)?"":new Long(srDuration.longValue()/60000L).toString())%>'/>
		  <input type="hidden" name='<%="year"+postFix%>' value="none"/>
<%
		l++;
	      }
	}

	if (outputConnection != null)
	{
		IOutputConnector outputConnector = OutputConnectorFactory.grab(threadContext,outputConnection.getClassName(),outputConnection.getConfigParams(),
			outputConnection.getMaxConnections());
		if (outputConnector != null)
		{
			try
			{
				outputConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),outputSpecification,tabName);
			}
			finally
			{
				OutputConnectorFactory.release(outputConnector);
			}
		}
	}

	if (connection != null)
	{
		IRepositoryConnector repositoryConnector = RepositoryConnectorFactory.grab(threadContext,connection.getClassName(),connection.getConfigParams(),
			connection.getMaxConnections());
		if (repositoryConnector != null)
		{
			try
			{
				repositoryConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),documentSpecification,tabName);
			}
			finally
			{
				RepositoryConnectorFactory.release(repositoryConnector);
			}
		}
	}
%>
		  <table class="displaytable">
			<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr><td class="message" colspan="4"><nobr>
<%
	if (connectionName.length() > 0 && outputName.length() > 0)
	{
%>
			<input type="button" value="Save" onClick="javascript:Save()" alt="Save this job"/>
<%
	}
	else
	{
		if (tabName.equals("Connection"))
		{
%>
			<input type="button" value="Continue" onClick="javascript:Continue()" alt="Continue to next screen"/>
<%
		}
	}
%>
			&nbsp;<input type="button" value="Cancel" onClick="javascript:Cancel()" alt="Cancel job editing"/>
			</nobr></td>
			</tr>
		  </table>
		</td>
	      </tr>
	    </table>
	</form>
<%
	}
%>
       </td>
      </tr>
    </table>
</body>

</html>

<%
    }
    catch (ManifoldCFException e)
    {
	e.printStackTrace();
	variableContext.setParameter("text",e.getMessage());
	variableContext.setParameter("target","listjobs.jsp");
%>
	<jsp:forward page="error.jsp"/>
<%
    }
%>

