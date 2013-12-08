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
		<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ApacheManifoldCFViewJob")%>
	</title>

	<script type="text/javascript">
	<!--

	function Delete(jobID)
	{
		if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewjob.DeleteJobConfirmation")%>"))
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
	<p class="windowtitle"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ViewAJob")%></p>

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
	
	IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
	IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
	
	String jobID = variableContext.getParameter("jobid");
	IJobDescription job = manager.load(new Long(jobID));
	if (job == null)
	{
		throw new ManifoldCFException("No such job: "+jobID);
	}
	else
	{
		String naMessage = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Notapplicable");
		String jobType = "";
		String intervalString = naMessage;
		String reseedIntervalString = naMessage;
		String expirationIntervalString = naMessage;

		switch (job.getType())
		{
		case IJobDescription.TYPE_CONTINUOUS:
			String infinityMessage = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Infinity");
			String minutesMessage = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.minutes");
			jobType = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Rescandocumentsdynamically");
			Long recrawlInterval = job.getInterval();
			Long reseedInterval = job.getReseedInterval();
			Long expirationInterval = job.getExpiration();
			intervalString = (recrawlInterval==null)?infinityMessage:(new Long(recrawlInterval.longValue()/60000L).toString()+" "+minutesMessage);
			reseedIntervalString = (reseedInterval==null)?infinityMessage:(new Long(reseedInterval.longValue()/60000L).toString()+" "+minutesMessage);
			expirationIntervalString = (expirationInterval==null)?infinityMessage:(new Long(expirationInterval.longValue()/60000L).toString()+" "+minutesMessage);
			break;
		case IJobDescription.TYPE_SPECIFIED:
			jobType = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Scaneverydocumentonce");
			break;
		default:
		}

		String startMethod = "";
		switch (job.getStartMethod())
		{
		case IJobDescription.START_WINDOWBEGIN:
			startMethod = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Startatbeginningofschedulewindow");
			break;
		case IJobDescription.START_WINDOWINSIDE:
			startMethod = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Startinsideschedulewindow");
			break;
		case IJobDescription.START_DISABLE:
			startMethod = Messages.getString(pageContext.getRequest().getLocale(),"viewjob.Dontautomaticallystart");
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
		Map<String,Set<String>> forcedMetadata = job.getForcedMetadata();
		
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
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.NameColon")%></nobr></td>
				<td class="value" colspan="3" ><%="<!--jobid="+jobID+"-->"%><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getDescription())%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.OutputConnectionColon")%></nobr></td>
				<td class="value"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getOutputConnectionName())%></td>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.RepositoryConnectionColon")%></nobr></td>
				<td class="value"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(job.getConnectionName())%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.PriorityColon")%></nobr></td>
				<td class="value"><%=priority%></td>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.StartMethodColon")%></nobr></td>
				<td class="value"><%=startMethod%></td>
			</tr>
<%
		if (model != -1 && model != IRepositoryConnector.MODEL_ADD_CHANGE_DELETE)
		{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ScheduleTypeColon")%></nobr></td>
				<td class="value"><nobr><%=jobType%></nobr></td>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.MinimumRecrawlIntervalColon")%></nobr></td>
				<td class="value"><nobr><%=intervalString%></nobr></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ExpirationIntervalColon")%></nobr></td>
				<td class="value"><nobr><%=expirationIntervalString%></nobr></td>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ReseedIntervalColon")%></nobr></td>
				<td class="value"><nobr><%=reseedIntervalString%></nobr></td>
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
			<tr><td class="message" colspan="4"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.NoScheduledRunTimes")%></td></tr>
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
				boolean srRequestMinimum = sr.getRequestMinimum();
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
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ScheduledTimeColon")%></nobr></td>
				<td class="value" colspan="3">
<%
					if (srDayOfWeek == null)
						out.println(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Anydayoftheweek"));
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
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Sundays"));
						}
						if (srDayOfWeek.checkValue(1))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Mondays"));
						}
						if (srDayOfWeek.checkValue(2))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Tuesdays"));
						}
						if (srDayOfWeek.checkValue(3))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Wednesdays"));
						}
						if (srDayOfWeek.checkValue(4))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Thursdays"));
						}
						if (srDayOfWeek.checkValue(5))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Fridays"));
						}
						if (srDayOfWeek.checkValue(6))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Saturdays"));
						}
						out.println(sb.toString());
					}
%>
<%
					if (srHourOfDay == null)
					{
						if (srMinutesOfHour != null)
							out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.oneveryhour")+" ");
						else
							out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.atmidnight")+" ");
					}
					else
					{
						out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.at")+" ");
						int k = 0;
						while (k < 24)
						{
							int q = k;
							String ampm;
							if (k < 12)
								ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.am");
							else
							{
								ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.pm");
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
						out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.plus")+" ");
						int k = 0;
						while (k < 60)
						{
							if (srMinutesOfHour.checkValue(k))
								out.println(Integer.toString(k)+" ");
							k++;
						}
						out.println(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.minutes")+" ");
					}
%>
<%
					if (srMonthOfYear == null)
					{
						if (srDayOfMonth == null && srDayOfWeek == null && srHourOfDay == null && srMinutesOfHour == null)
							out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ineverymonthofyear"));
					}
					else
					{
						StringBuffer sb = new StringBuffer(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.in")+" ");
						boolean firstTime = true;
						if (srMonthOfYear.checkValue(0))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.January"));
						}
						if (srMonthOfYear.checkValue(1))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.February"));
						}
						if (srMonthOfYear.checkValue(2))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.March"));
						}
						if (srMonthOfYear.checkValue(3))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.April"));
						}
						if (srMonthOfYear.checkValue(4))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.May"));
						}
						if (srMonthOfYear.checkValue(5))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.June"));
						}
						if (srMonthOfYear.checkValue(6))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.July"));
						}
						if (srMonthOfYear.checkValue(7))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.August"));
						}
						if (srMonthOfYear.checkValue(8))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.September"));
						}
						if (srMonthOfYear.checkValue(9))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.October"));
						}
						if (srMonthOfYear.checkValue(10))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.November"));
						}
						if (srMonthOfYear.checkValue(11))
						{
							if (firstTime)
								firstTime = false;
							else
								sb.append(",");
							sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.December"));
						}
						out.println(sb.toString());
					}
%>
<%
					if (srDayOfMonth == null)
					{
						if (srDayOfWeek == null && srHourOfDay == null && srMinutesOfHour == null)
							out.println(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.onanydayofthemonth"));
					}
					else
					{
						StringBuffer sb = new StringBuffer(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.onthe")+" ");
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
									sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.st"));
								else if (value == 2 && k != 11)
									sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.nd"));
								else if (value == 3 && k != 12)
									sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.rd"));
								else
									sb.append(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.th"));
							}
							k++;
						}
						sb.append(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ofthemonth"));
						out.println(sb.toString());
					}
%>
<%
					if (srYear != null)
					{
						StringBuffer sb = new StringBuffer(" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.inyears")+" ");
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
				<td class="description">
					<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.MaximumRunTimeColon")%>
				</td>
				<td class="value">
<%
					if (srDuration == null)
						out.println(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Nolimit"));
					else
						out.println(new Long(srDuration.longValue()/60000L).toString() + " "+Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.minutes"));
%>
				</td>
				<td class="description">
					<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.JobInvocationColon")%>
				</td>
				<td class="value">
<%
					if (srRequestMinimum)
						out.println(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Minimal"));
					else
						out.println(Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Complete"));
%>
				</td>
			</tr>
<%
				j++;
			}
		}
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
<%
		if (forcedMetadata.size() == 0)
		{
%>
			<tr><td class="message" colspan="4"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.NoForcedMetadata")%></td></tr>
<%
		}
		else
		{
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.ForcedMetadataColon")%></nobr></td>
				<td class="value" colspan="3">
<%
			String[] paramNames = new String[forcedMetadata.size()];
			int q = 0;
			for (String paramName : forcedMetadata.keySet())
			{
				paramNames[q++] = paramName;
			}
			java.util.Arrays.sort(paramNames);
			for (String paramName : paramNames)
			{
				Set<String> values = forcedMetadata.get(paramName);
				String[] paramValues = new String[values.size()];
				q = 0;
				for (String paramValue : values)
				{
					paramValues[q++] = paramValue;
				}
				java.util.Arrays.sort(paramValues);
				for (String paramValue : paramValues)
				{
					out.println(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(paramName) + " = " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(paramValue));
%>
					<br/>
<%
				}
			}
%>
				</td>
			</tr>
<%
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
				<td class="description" colspan="1">
					<nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.MaximumHopCountForLinkType")%> '<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(relationshipType)%>':</nobr>
				</td>
				<td class="value" colspan="3">
					<%=((value==null)?Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Unlimited"):value.toString())%>
				</td>
			</tr>
			
<%
			}
%>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1">
					<nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.HopCountModeColon")%></nobr>
				</td>
				<td class="value" colspan="3">
					<nobr>
						<%=(hopcountMode==IJobDescription.HOPCOUNT_ACCURATE)?Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Deleteunreachabledocuments"):""%>
						<%=(hopcountMode==IJobDescription.HOPCOUNT_NODELETE)?Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Nodeletesfornow"):""%>
						<%=(hopcountMode==IJobDescription.HOPCOUNT_NEVERDELETE)?Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Nodeletesforever"):""%>
					</nobr>
				</td>
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
			IOutputConnector outputConnector = outputConnectorPool.grab(outputConnection);
			if (outputConnector != null)
			{
				try
				{
					outputConnector.viewSpecification(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),job.getOutputSpecification());
				}
				finally
				{
					outputConnectorPool.release(outputConnection,outputConnector);
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
			IRepositoryConnector repositoryConnector = repositoryConnectorPool.grab(connection);
			if (repositoryConnector != null)
			{
				try
				{
					repositoryConnector.viewSpecification(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),job.getSpecification());
				}
				finally
				{
					repositoryConnectorPool.release(connection,repositoryConnector);
				}
			}
		}
%>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
		<tr><td class="message" colspan="4"><a href='<%="editjob.jsp?jobid="+jobID%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewjob.EditThisJob")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Edit")%></a>
		&nbsp;<a href='<%="javascript:Delete(\""+jobID+"\")"%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewjob.DeleteThisJob")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Delete")%></a>&nbsp;<a href='<%="editjob.jsp?origjobid="+jobID%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewjob.CopyThisJob")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewjob.Copy")%></a></td>
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
