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
try
{
  // Check if authorized
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_VIEW_JOBS))
  {
    variableContext.setParameter("target","index.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }
  // Get the job manager handle
  IJobManager manager = JobManagerFactory.make(threadContext);
  IJobDescription[] jobs = manager.getAllJobs();
%>

<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listjobs.ApacheManifoldCFListJobDescriptions")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listjobs.JobList")%>',
      'jobs'
  );

  function Delete(jobID)
  {
    if (confirm("Warning: Deleting this job will remove all\nassociated documents from the index.\nDo you want to proceed?"))
    {
      document.listjobs.op.value="Delete";
      document.listjobs.jobid.value=jobID;
      $.ManifoldCF.submit(document.listjobs);
    }
  }

  //-->
</script>

<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="listjobs" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="job"/>
        <input type="hidden" name="jobid" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th>Action</th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.Name")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.OutputConnection")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.RepositoryConnection")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.ScheduleType")%></th>
            </tr>
<%
  for (int i = 0; i < jobs.length; i++)
  {
    IJobDescription jd = jobs[i];

    StringBuilder sb = new StringBuilder();
    for (int j = 0; j < jd.countPipelineStages(); j++)
    {
      if (jd.getPipelineStageIsOutputConnection(j))
      {
        if (sb.length() > 0)
          sb.append(",");
        sb.append(jd.getPipelineStageConnectionName(j));
      }
    }
    String outputConnectionNames = sb.toString();

    String jobType = "";
    switch (jd.getType())
    {
    case IJobDescription.TYPE_CONTINUOUS:
      jobType = "Continuous crawl";
      break;
    case IJobDescription.TYPE_SPECIFIED:
      jobType = "Specified time";
      break;
    default:
    }

%>
            <tr job-id="<%= jd.getID() %>">
              <td>
                <div class="btn-group">
                  <a href='<%="viewjob.jsp?jobid="+jd.getID()%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listjobs.Viewjob")+" "+jd.getID()%>'
                          class="link btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-eye fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.View")%></a>
                  <a href='<%="editjob.jsp?jobid="+jd.getID()%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listjobs.Editjob")+" "+jd.getID()%>'
                          class="link btn btn-primary btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-pencil-square-o fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.Edit")%></a>
                  <a href='<%="javascript:Delete(\""+jd.getID()+"\")"%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listjobs.DeleteJob")+" "+jd.getID()%>'
                          class="btn btn-danger btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.Delete")%></a>
                  <a href='<%="editjob.jsp?origjobid="+jd.getID()%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listjobs.CopyJob")+" "+jd.getID()%>'
                          class="link btn btn-primary btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-clipboard fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.Copy")%></a>
                </div>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(jd.getDescription())%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(outputConnectionNames)%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(jd.getConnectionName())%></td>
              <td><%=jobType%></td>
            </tr>
<%
  }
%>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href="editjob.jsp" title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listjobs.Addajob")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-plus-circle fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listjobs.AddaNewJob")%></a>
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
        </div>
      </form>
    </div>
  </div>
</div>
