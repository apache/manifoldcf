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

<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		<%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ApacheManifoldCFViewJob")%>
	</title>

	<script type="text/javascript">
	<!--

	function Delete(jobID)
	{
		if (confirm("Warning: Deleting this job will remove all\nassociated documents from the index.\nDo you want to proceed?"))
		{
			document.viewjob.op.value="Delete";
			document.viewjob.jobid.value=jobID;
			document.viewjob.submit();
		}
	}

	//-->
	</script>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="window">
	<p class="windowtitle"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ViewAJob")%></p>

	<form class="standardform" name="viewjob" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="job"/>
		<input type="hidden" name="jobid" value=""/>

<%
    try
    {
	// Get the job manager handle
	IJobManager manager = JobManagerFactory.make(threadContext);
        IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
	IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
	String jobID = variableContext.getParameter("jobid");
	IJobDescription job = manager.load(new Long(jobID));
	if (job == null)
	{
		throw new ManifoldCFException("No such job: "+jobID);
	}
	else
	{
		String jobType = "";
		String intervalString = "Not applicable";
		String reseedIntervalString = "Not applicable";
		String expirationIntervalString = "Not applicable";

		switch (job.getType())
		{
		case IJobDescription.TYPE_CONTINUOUS:
			jobType = "Rescan documents dynamically";
			Long recrawlInterval = job.getInterval();
			Long reseedInterval = job.getReseedInterval();
			Long expirationInterval = job.getExpiration();
			intervalString = (recrawlInterval==null)?"Infinity":(new Long(recrawlInterval.longValue()/60000L).toString()+" minutes");
			reseedIntervalString = (reseedInterval==null)?"Infinity":(new Long(reseedInterval.longValue()/60000L).toString()+" minutes");
			expirationIntervalString = (expirationInterval==null)?"Infinity":(new Long(expirationInterval.longValue()/60000L).toString()+" minutes");
			break;
		case IJobDescription.TYPE_SPECIFIED:
			jobType = "Scan every document once";
			break;
		default:
		}

		String startMethod = "";
		switch (job.getStartMethod())
		{
		case IJobDescription.START_WINDOWBEGIN:
			startMethod = "Start at beginning of schedule window";
			break;
		case IJobDescription.START_WINDOWINSIDE:
			startMethod = "Start inside schedule window";
			break;
		case IJobDescription.START_DISABLE:
			startMethod = "Don't automatically start";
			break;
		default:
			break;
		}

		int priority = job.getPriority();

		IRepositoryConnection connection = connManager.load(job.getConnectionName());
		IOutputConnection outputConnection = outputManager.load(job.getOutputConnectionName());
		int model = RepositoryConnectorFactory.getConnectorModel(threadContext,connection.getClassName());
		String[] relationshipTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
		Map hopCountFilters = job.getHopCountFilters();
		int hopcountMode = job.getHopcountMode();

		//threadContext.save("OutputSpecification",job.getOutputSpecification());
		//threadContext.save("OutputConnection",outputConnection);
		//threadContext.save("DocumentSpecification",job.getSpecification());
		//threadContext.save("RepositoryConnection",connection);
%>
		<table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Name")%></nobr></td><td class="value" colspan="3" ><%="<!--jobid="+jobID+"-->"%><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getDescription())%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.OutputConnection")%></nobr></td>
				<td class="value"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getOutputConnectionName())%></td>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.RepositoryConnection")%></nobr></td>
				<td class="value"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getConnectionName())%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Priority")%></nobr></td><td class="value"><%=priority%></td>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.StartMethod")%></nobr></td><td class="value"><%=startMethod%></td>
			</tr>
<%
		if (model != -1 && model != IRepositoryConnector.MODEL_ADD_CHANGE_DELETE)
		{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ScheduleType")%></nobr></td><td class="value"><nobr><%=jobType%></nobr></td>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.MinimumRecrawlInterval")%></nobr></td><td class="value"><nobr><%=intervalString%></nobr>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ExpirationInterval")%></nobr></td><td class="value"><nobr><%=expirationIntervalString%></nobr></td>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ReseedInterval")%></nobr></td><td class="value"><nobr><%=reseedIntervalString%></nobr></td>
			</tr>
<%
		}
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>

<%
		if (job.getScheduleRecordCount() == 0)
		{
%>
			<tr><td class="message" colspan="4"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.NoScheduledRunTimes")%></td></tr>
<%
		}
		else
		{
			// Loop through the schedule records
			int j = 0;
			while (j < job.getScheduleRecordCount())
			{
				ScheduleRecord sr = job.getScheduleRecord(j);
				Long srDuration = sr.getDuration();
				EnumeratedValues srDayOfWeek = sr.getDayOfWeek();
				EnumeratedValues srMonthOfYear = sr.getMonthOfYear();
				EnumeratedValues srDayOfMonth = sr.getDayOfMonth();
				EnumeratedValues srYear = sr.getYear();
				EnumeratedValues srHourOfDay = sr.getHourOfDay();
				EnumeratedValues srMinutesOfHour = sr.getMinutesOfHour();

				if (j > 0)
				{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
<%
				}
%>
			<tr>
				<td class="description"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.ScheduledTime")%></nobr></td>
				<td class="value" colspan="3">
<%
					if (srDayOfWeek == null)
						out.println("Any day of week");
					else
					{
						StringBuffer sb = new StringBuffer();
						boolean firstTime = true;
						if (srDayOfWeek.checkValue(0))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Sundays");
						}
						if (srDayOfWeek.checkValue(1))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Mondays");
						}
						if (srDayOfWeek.checkValue(2))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Tuesdays");
						}
						if (srDayOfWeek.checkValue(3))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Wednesdays");
						}
						if (srDayOfWeek.checkValue(4))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Thursdays");
						}
						if (srDayOfWeek.checkValue(5))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Fridays");
						}
						if (srDayOfWeek.checkValue(6))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("Saturdays");
						}
						out.println(sb.toString());
					}
%>
<%
					if (srHourOfDay == null)
					{
						if (srMinutesOfHour != null)
							out.println(" on every hour ");
						else
							out.println(" at midnight ");
					}
					else
					{
						out.println(" at ");
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
							if (srHourOfDay.checkValue(k))
								out.println(Integer.toString(q)+" "+ampm+" ");
							k++;
						}
					}
%>
<%
					if (srMinutesOfHour != null)
					{
						out.println(" plus ");
						int k = 0;
						while (k < 60)
						{
							if (srMinutesOfHour.checkValue(k))
								out.println(Integer.toString(k)+" ");
							k++;
						}
						out.println(" minutes");
					}
%>
<%
					if (srMonthOfYear == null)
					{
						if (srDayOfMonth == null && srDayOfWeek == null && srHourOfDay == null && srMinutesOfHour == null)
							out.println(" in January");
					}
					else
					{
						StringBuffer sb = new StringBuffer(" in ");
						boolean firstTime = true;
						if (srMonthOfYear.checkValue(0))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("January");
						}
						if (srMonthOfYear.checkValue(1))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("February");
						}
						if (srMonthOfYear.checkValue(2))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("March");
						}
						if (srMonthOfYear.checkValue(3))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("April");
						}
						if (srMonthOfYear.checkValue(4))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("May");
						}
						if (srMonthOfYear.checkValue(5))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("June");
						}
						if (srMonthOfYear.checkValue(6))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("July");
						}
						if (srMonthOfYear.checkValue(7))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("August");
						}
						if (srMonthOfYear.checkValue(8))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("September");
						}
						if (srMonthOfYear.checkValue(9))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("October");
						}
						if (srMonthOfYear.checkValue(10))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("November");
						}
						if (srMonthOfYear.checkValue(11))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append("December");
						}
						out.println(sb.toString());
					}
%>
<%
					if (srDayOfMonth == null)
					{
						if (srDayOfWeek == null && srHourOfDay == null && srMinutesOfHour == null)
							out.println(" on the 1st of the month");
					}
					else
					{
						StringBuffer sb = new StringBuffer(" on the ");
						int k = 0;
						boolean firstTime = true;
						while (k < 31)
						{
							if (srDayOfMonth.checkValue(k))
							{
								if (firstTime)
									firstTime = false;
								else
									sb.append(",");
								sb.append(Integer.toString(k+1));
								int value = (k+1) % 10;
								if (value == 1 && k != 10)
									sb.append("st");
								else if (value == 2 && k != 11)
									sb.append("nd");
								else if (value == 3 && k != 12)
									sb.append("rd");
								else
									sb.append("th");
							}
							k++;
						}
						sb.append(" of the month");
						out.println(sb.toString());
					}
%>
<%
					if (srYear != null)
					{
						StringBuffer sb = new StringBuffer(" in year(s) ");
						Iterator iter = srYear.getValues();
						boolean firstTime = true;
						while (iter.hasNext())
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							Integer value = (Integer)iter.next();
							sb.append(value.toString());
						}
						out.println(sb.toString());
					}
%>


				</td>
			</tr>
			<tr>
				<td class="description"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.MaximumRunTime")%></td><td class="value" colspan="3">
<%
					if (srDuration == null)
						out.println("No limit");
					else
						out.println(new Long(srDuration.longValue()/60000L).toString() + " minutes");
%>
				</td>
			</tr>

<%
				j++;
			}
		}

		if (relationshipTypes != null && relationshipTypes.length > 0)
		{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
<%
			int k = 0;
			while (k < relationshipTypes.length)
			{
				String relationshipType = relationshipTypes[k++];
				Long value = (Long)hopCountFilters.get(relationshipType);
%>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.MaximumHopCountForLinkType")%> '<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(relationshipType)%>':</nobr></td>
				<td class="value" colspan="3"><%=((value==null)?"Unlimited":value.toString())%></td>
			</tr>
			
<%
			}
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.HopCountMode")%></nobr></td>
				<td class="value" colspan="3"><nobr><%=(hopcountMode==IJobDescription.HOPCOUNT_ACCURATE)?"Delete unreachable documents":""%><%=(hopcountMode==IJobDescription.HOPCOUNT_NODELETE)?"No deletes, for now":""%><%=(hopcountMode==IJobDescription.HOPCOUNT_NEVERDELETE)?"No deletes, forever":""%></nobr></td>
			</tr>
<%

		}
%>

			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td colspan="4">
<%
		if (outputConnection != null)
		{
			IOutputConnector outputConnector = OutputConnectorFactory.grab(threadContext,outputConnection.getClassName(),outputConnection.getConfigParams(),
				outputConnection.getMaxConnections());
			if (outputConnector != null)
			{
				try
				{
					outputConnector.viewSpecification(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),pageContext.getRequest().getLocale(),job.getOutputSpecification());
				}
				finally
				{
					OutputConnectorFactory.release(outputConnector);
				}
			}
		}
%>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td colspan="4">
<%
		if (connection != null)
		{
			IRepositoryConnector repositoryConnector = RepositoryConnectorFactory.grab(threadContext,connection.getClassName(),connection.getConfigParams(),

				connection.getMaxConnections());
			if (repositoryConnector != null)
			{
				try
				{
					repositoryConnector.viewSpecification(new org.apache.manifoldcf.ui.jsp.JspWrapper(out),pageContext.getRequest().getLocale(),job.getSpecification());
				}
				finally
				{
					RepositoryConnectorFactory.release(repositoryConnector);
				}
			}
		}
%>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
		<tr><td class="message" colspan="4"><a href='<%="editjob.jsp?jobid="+jobID%>' alt="<%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.EditThisJob")%>"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Edit")%></a>
		&nbsp;<a href='<%="javascript:Delete(\""+jobID+"\")"%>' alt="<%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.DeleteThisJob")%>"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Delete")%></a>&nbsp;<a href='<%="editjob.jsp?origjobid="+jobID%>' alt="<%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.CopyThisJob")%>"><%=Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Copy")%></a></td>
		</tr>
		</table>

<%
	}
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
	    </form>
       </td>
      </tr>
    </table>

</body>

</html>
