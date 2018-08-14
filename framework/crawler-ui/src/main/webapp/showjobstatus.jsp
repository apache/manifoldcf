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
final String clientTimezoneString = variableContext.getParameter("client_timezone");
final TimeZone clientTimezone;
if (clientTimezoneString == null || clientTimezoneString.length() == 0)
{
  clientTimezone = TimeZone.getDefault();
}
else
{
  clientTimezone = TimeZone.getTimeZone(clientTimezoneString);
}

try
{
  // Check if authorized
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_RUN_JOBS))
  {
    variableContext.setParameter("target","index.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }

  if (org.apache.manifoldcf.crawler.system.ManifoldCF.checkMaintenanceUnderway())
  {
%>
    <jsp:forward page="maintenanceunderway.jsp"/>
<%
  }
  
  // Get the max count
  int maxCount = LockManagerFactory.getIntProperty(threadContext,"org.apache.manifoldcf.ui.maxstatuscount",500000);
  // Get the job manager handle
  IJobManager manager = JobManagerFactory.make(threadContext);
  JobStatus[] jobs = manager.getAllStatus(true,maxCount);
%>

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "showjobstatus.ApacheManifoldCFStatusOfAllJobs")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "showjobstatus.StatusOfJobs")%>',
      'jobs'
  );

  function Start(jobID)
  {
    document.liststatuses.op.value="Start";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function StartMinimal(jobID)
  {
    document.liststatuses.op.value="StartMinimal";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function Abort(jobID)
  {
    document.liststatuses.op.value="Abort";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function Restart(jobID)
  {
    document.liststatuses.op.value="Restart";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function RestartMinimal(jobID)
  {
    document.liststatuses.op.value="RestartMinimal";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function Pause(jobID)
  {
    document.liststatuses.op.value="Pause";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  function Resume(jobID)
  {
    document.liststatuses.op.value="Resume";
    document.liststatuses.jobid.value=jobID;
    $.ManifoldCF.submit(document.liststatuses);
  }

  //-->
</script>
<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="liststatuses" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="jobstatus"/>
        <input type="hidden" name="jobid" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th>Action</th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Name")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Status")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.StartTime")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.EndTime")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Documents")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Active")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Processed")%></th>
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
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Notyetrun");
      break;
    case JobStatus.JOBSTATUS_RUNNING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Running");
      break;
    case JobStatus.JOBSTATUS_RUNNING_UNINSTALLED:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Runningnoconnector");
      break;
    case JobStatus.JOBSTATUS_ABORTING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Aborting");
      break;
    case JobStatus.JOBSTATUS_RESTARTING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Restarting");
      break;
    case JobStatus.JOBSTATUS_STOPPING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Stopping");
      break;
    case JobStatus.JOBSTATUS_RESUMING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Resuming");
      break;
    case JobStatus.JOBSTATUS_PAUSED:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Paused");
      break;
    case JobStatus.JOBSTATUS_COMPLETED:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Done");
      break;
    case JobStatus.JOBSTATUS_WINDOWWAIT:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Waiting");
      break;
    case JobStatus.JOBSTATUS_STARTING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Startingup");
      break;
    case JobStatus.JOBSTATUS_DESTRUCTING:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Cleaningup");
      break;
    case JobStatus.JOBSTATUS_JOBENDCLEANUP:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Terminating");
      break;
    case JobStatus.JOBSTATUS_JOBENDNOTIFICATION:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Endnotification");
      break;
    case JobStatus.JOBSTATUS_ERROR:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.ErrorColon")+" "+js.getErrorText();
      break;
    default:
      statusName = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Unknown");
      break;
    }
    String startTime = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Notstarted");
    if (js.getStartTime() != -1L)
      startTime = org.apache.manifoldcf.ui.util.Formatter.formatTime(clientTimezone, pageContext.getRequest().getLocale(), js.getStartTime());
    String endTime = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Aborted");
    if (js.getStartTime() == -1L)
      endTime = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Neverrun");
    else
    {
      if (js.getEndTime() == -1L)
      {
        if (status == JobStatus.JOBSTATUS_COMPLETED)
          endTime = Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Aborted");
        else
          endTime = "";
      }
      else
        endTime = org.apache.manifoldcf.ui.util.Formatter.formatTime(clientTimezone, pageContext.getRequest().getLocale(), js.getEndTime());
    }
%>
            <tr job-id="<%= js.getJobID() %>" job-status="<%= status %>" job-status-name="<%= statusName%>">
              <td>

<%
    if (status == JobStatus.JOBSTATUS_NOTYETRUN ||
      status == JobStatus.JOBSTATUS_COMPLETED ||
      status == JobStatus.JOBSTATUS_ERROR)
    {
%>
                <a href='<%="javascript:Start(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Startjob")+" "+js.getJobID()%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-play fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Start")%></a>
                <a href='<%="javascript:StartMinimal(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Startjob")+" "+js.getJobID()+" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.minimally")%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-compress fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Startminimal")%></a>
<%
    }
    if (status == JobStatus.JOBSTATUS_RUNNING ||
      status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED ||
      status == JobStatus.JOBSTATUS_WINDOWWAIT ||
      status == JobStatus.JOBSTATUS_PAUSED ||
      status == JobStatus.JOBSTATUS_STARTING)
    {
%>
                <a href='<%="javascript:Restart(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Restartjob")+" "+js.getJobID()%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-play fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Restart")%></a>
                <a href='<%="javascript:RestartMinimal(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Restartjob")+" "+js.getJobID()+" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.minimally")%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-compress fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Restartminimal")%></a>
<%
    }
    if (status == JobStatus.JOBSTATUS_RUNNING ||
      status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED ||
      status == JobStatus.JOBSTATUS_WINDOWWAIT)
    {
%>
                <a href='<%="javascript:Pause(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Pausejob")+" "+js.getJobID()%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-pause fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Pause")%></a>
<%
    }
    if (status == JobStatus.JOBSTATUS_RUNNING ||
      status == JobStatus.JOBSTATUS_RUNNING_UNINSTALLED ||
      status == JobStatus.JOBSTATUS_STOPPING ||
      status == JobStatus.JOBSTATUS_RESUMING ||
      status == JobStatus.JOBSTATUS_WINDOWWAIT ||
      status == JobStatus.JOBSTATUS_PAUSED ||
      status == JobStatus.JOBSTATUS_STARTING ||
      status == JobStatus.JOBSTATUS_RESTARTING)
    {
%>
                <a href='<%="javascript:Abort(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Abortjob")+" "+js.getJobID()%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-stop fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Abort")%></a>
<%
    }
    if (status == JobStatus.JOBSTATUS_PAUSED)
    {
%>
                <a href='<%="javascript:Resume(\""+js.getJobID()+"\")"%>'
                        title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.Resumejob")+" "+js.getJobID()%>'
                        class="btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-play fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Resume")%></a>
<%
    }
%>

              </td>
              <td><%="<!--jobid=" + js.getJobID() + "-->"%><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(js.getDescription())%></td>
              <td><%=statusName%></td>
              <td><%=startTime%></td>
              <td><%=endTime%></td>
              <td><%=(js.getQueueCountExact()?"":"&gt; ")%><%=new Long(js.getDocumentsInQueue()).toString()%></td>
              <td><%=(js.getOutstandingCountExact()?"":"&gt; ")%><%=new Long(js.getDocumentsOutstanding()).toString()%></td>
              <td><%=(js.getProcessedCountExact()?"":"&gt; ")%><%=new Long(js.getDocumentsProcessed()).toString()%></td>
            </tr>
<%
  }
%>
          </table>
        </div>
        <div class="box-footer clearfix">
            <a href="showjobstatus.jsp"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"showjobstatus.RefreshStatus")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-refresh fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"showjobstatus.Refresh")%></a>
        </div>
          
<%
}
catch (ManifoldCFException e)
{
  e.printStackTrace();
  variableContext.setParameter("text",e.getMessage());
  variableContext.setParameter("target","index.jsp");
%>
<jsp:forward page="error.jsp"/>
<%
}
%>
      </form>
    </div>
  </div>
</div>
