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
  // Check if authorized
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_EDIT_JOBS))
  {
    variableContext.setParameter("target","listjobs.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }

  // Get the job manager handle
  IJobManager manager = JobManagerFactory.make(threadContext);
  IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
  IRepositoryConnection[] connList = connMgr.getAllConnections();
  INotificationConnectionManager notificationMgr = NotificationConnectionManagerFactory.make(threadContext);
  INotificationConnection[] notificationList = notificationMgr.getAllConnections();
  IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(threadContext);
  IOutputConnection[] outputList = outputMgr.getAllConnections();
  ITransformationConnectionManager transformationMgr = TransformationConnectionManagerFactory.make(threadContext);
  ITransformationConnection[] transformationList = transformationMgr.getAllConnections();

  IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
  IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
  INotificationConnectorPool notificationConnectorPool = NotificationConnectorPoolFactory.make(threadContext);
  ITransformationConnectorPool transformationConnectorPool = TransformationConnectorPoolFactory.make(threadContext);

  // Figure out tab name and sequence number
  String tabName = variableContext.getParameter("tabname");
  String tabSequenceNumber = variableContext.getParameter("sequencenumber");
  int tabSequenceInt;
  if (tabName == null || tabName.length() == 0)
  {
    tabName = Messages.getString(pageContext.getRequest().getLocale(),"editjob.Name");
    tabSequenceInt = -1;
  }
  else
  {
    if (tabSequenceNumber == null || tabSequenceNumber.length() == 0)
      tabSequenceInt = -1;
    else
      tabSequenceInt = Integer.parseInt(tabSequenceNumber);
  }

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
  String description = "";
  int type = IJobDescription.TYPE_SPECIFIED;
  Specification documentSpecification = new Specification();

  // Pipeline data
  String[] pipelineConnectionNames = new String[0];
  String[] pipelineDescriptions = new String[0];
  boolean[] pipelineIsOutputs = new boolean[0];
  int[] pipelinePrerequisites = new int[0];
  Specification[] pipelineSpecifications = new Specification[0];

  String[] notificationConnectionNames = new String[0];
  String[] notificationDescriptions = new String[0];
  Specification[] notificationSpecifications = new Specification[0];

  ArrayList scheduleRecords = new ArrayList();

  EnumeratedValues dayOfWeek = null;
  EnumeratedValues dayOfMonth = null;
  EnumeratedValues monthOfYear = null;
  EnumeratedValues year = null;
  EnumeratedValues hourOfDay = null;
  EnumeratedValues minutesOfHour = null;
  // Duration in minutes
  Long duration = null;
  // RequestMinimum flag
  boolean requestMinimum = false;

  // Priority
  int priority = 5;
  // Minimum recrawl interval (Default: 1 day)
  Long recrawlInterval = new Long(60L * 24L);
  // Maximum recrawl interval (Default: none)
  Long maxRecrawlInterval = null;
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
    connectionName = job.getConnectionName();

    pipelineConnectionNames = new String[job.countPipelineStages()];
    pipelineDescriptions = new String[job.countPipelineStages()];
    pipelineIsOutputs = new boolean[job.countPipelineStages()];
    pipelinePrerequisites = new int[job.countPipelineStages()];
    pipelineSpecifications = new Specification[job.countPipelineStages()];
    for (int j = 0; j < job.countPipelineStages(); j++)
    {
      pipelineConnectionNames[j] = job.getPipelineStageConnectionName(j);
      pipelineDescriptions[j] = job.getPipelineStageDescription(j);
      pipelineIsOutputs[j] = job.getPipelineStageIsOutputConnection(j);
      pipelinePrerequisites[j] = job.getPipelineStagePrerequisite(j);
      pipelineSpecifications[j] = job.getPipelineStageSpecification(j);
    }
    notificationConnectionNames = new String[job.countNotifications()];
    notificationDescriptions = new String[job.countNotifications()];
    notificationSpecifications = new Specification[job.countNotifications()];
    for (int j = 0; j < job.countNotifications(); j++)
    {
      notificationConnectionNames[j] = job.getNotificationConnectionName(j);
      notificationDescriptions[j] = job.getNotificationDescription(j);
      notificationSpecifications[j] = job.getNotificationSpecification(j);
    }

    type = job.getType();
    startMethod = job.getStartMethod();
    hopcountMode = job.getHopcountMode();
    documentSpecification = job.getSpecification();
    // Fill in schedule records from job
    for (int j = 0; j < job.getScheduleRecordCount(); j++)
    {
      scheduleRecords.add(job.getScheduleRecord(j));
    }

    priority = job.getPriority();
    Long value = job.getInterval();
    recrawlInterval = (value==null)?null:new Long(value.longValue()/60000L);
    value = job.getMaxInterval();
    maxRecrawlInterval = (value==null)?null:new Long(value.longValue()/60000L);
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
  List<String> tabsArray = new ArrayList<String>();
  List<Integer> sequenceArray = new ArrayList<Integer>();

  IRepositoryConnection connection = null;
  if (connectionName.length() > 0)
  {
    connection = connMgr.load(connectionName);
    model = RepositoryConnectorFactory.getConnectorModel(threadContext,connection.getClassName());
    relationshipTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());
  }

  // Set up the predefined tabs
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Name"));
  sequenceArray.add(null);
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Connection"));
  sequenceArray.add(null);
  if (connectionName.length() > 0)
  {
    tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Scheduling"));
    sequenceArray.add(null);
    if (relationshipTypes != null && relationshipTypes.length > 0)
    {
      tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editjob.HopFilters"));
      sequenceArray.add(null);
    }
  }

  // Get the names of the various Javascript methods we'll need to call
  String checkMethod = "checkSpecification";
  String saveCheckMethod = "checkSpecificationForSave";
  String[] pipelineCheckMethods = new String[pipelineConnectionNames.length];
  String[] pipelineCheckForSaveMethods = new String[pipelineConnectionNames.length];
  String[] notificationCheckMethods = new String[notificationConnectionNames.length];
  String[] notificationCheckForSaveMethods = new String[notificationConnectionNames.length];

  for (int j = 0; j < pipelineConnectionNames.length; j++)
  {
    pipelineCheckMethods[j] = "unknown";
    pipelineCheckForSaveMethods[j] = "unknown";
  }
  for (int j = 0; j < notificationConnectionNames.length; j++)
  {
    notificationCheckMethods[j] = "unknown";
    notificationCheckForSaveMethods[j] = "unknown";
  }

  if (connection != null)
  {
    IRepositoryConnector connector = RepositoryConnectorFactory.getConnectorNoCheck(connection.getClassName());
    if (connector != null)
    {
      checkMethod = connector.getFormCheckJavascriptMethodName(0);
      saveCheckMethod = connector.getFormPresaveCheckJavascriptMethodName(0);
    }
  }

  for (int j = 0; j < pipelineConnectionNames.length; j++)
  {
    if (pipelineIsOutputs[j])
    {
      IOutputConnection outputConnection = outputMgr.load(pipelineConnectionNames[j]);
      if (outputConnection != null)
      {
        IOutputConnector outputConnector = OutputConnectorFactory.getConnectorNoCheck(outputConnection.getClassName());
        if (outputConnector != null)
        {
          pipelineCheckMethods[j] = outputConnector.getFormCheckJavascriptMethodName(1+j);
          pipelineCheckForSaveMethods[j] = outputConnector.getFormPresaveCheckJavascriptMethodName(1+j);
        }
      }
    }
    else
    {
      ITransformationConnection transformationConnection = transformationMgr.load(pipelineConnectionNames[j]);
      if (transformationConnection != null)
      {
        ITransformationConnector transformationConnector = TransformationConnectorFactory.getConnectorNoCheck(transformationConnection.getClassName());
        if (transformationConnector != null)
        {
          pipelineCheckMethods[j] = transformationConnector.getFormCheckJavascriptMethodName(1+j);
          pipelineCheckForSaveMethods[j] = transformationConnector.getFormPresaveCheckJavascriptMethodName(1+j);
        }
      }
    }
  }

  for (int j = 0; j < notificationConnectionNames.length; j++)
  {
    INotificationConnection notificationConnection = notificationMgr.load(notificationConnectionNames[j]);
    if (notificationConnection != null)
    {
      INotificationConnector notificationConnector = NotificationConnectorFactory.getConnectorNoCheck(notificationConnection.getClassName());
      if (notificationConnector != null)
      {
        notificationCheckMethods[j] = notificationConnector.getFormCheckJavascriptMethodName(1+pipelineConnectionNames.length+j);
        notificationCheckForSaveMethods[j] = notificationConnector.getFormPresaveCheckJavascriptMethodName(1+pipelineConnectionNames.length+j);
      }

    }
  }
%>

<script type="text/javascript">
  <!--
<%
  String title = null;
  if (description.length() > 0)
  {
    title = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.EditJob") + " - " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description);
  }
  else
  {
    title = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.EditaJob");
  }
%>
    $.ManifoldCF.setTitle(
        '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.ApacheManifoldCFEditJob")%>',
        '<%=title%>',
        'jobs'
    );

// Use this method to repost the form and pick a new tab
function SelectTab(newtab)
{
  if (checkForm())
  {
    document.editjob.tabname.value = newtab;
    document.editjob.sequencenumber.value = "";
    $.ManifoldCF.submit(document.editjob);
  }
}

// Use this method to repost the form and pick a new tab
function SelectSequencedTab(newtab, sequencenumber)
{
  if (checkForm())
  {
    document.editjob.tabname.value = newtab;
    document.editjob.sequencenumber.value = sequencenumber;
    $.ManifoldCF.submit(document.editjob);
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
    $.ManifoldCF.submit(document.editjob);
  }
}

// Use this method to repost the form
function postFormNew()
{
  if (checkForm())
  {
    $.ManifoldCF.submit(document.editjob);
  }
}

// Deprecated
function postForm(schedCount)
{
  if (checkForm())
  {
    $.ManifoldCF.submit(document.editjob);
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
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.JobMustHaveAName")%>");
      SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.Name")%>");
      document.editjob.description.focus();
      return;
    }
<%
  for (int j = 0; j < pipelineCheckForSaveMethods.length; j++)
  {
%>
    if (window.<%=pipelineCheckForSaveMethods[j]%>)
    {
      if (<%=pipelineCheckForSaveMethods[j]%>() == false)
        return;
    }
<%
  }
  for (int j = 0; j < notificationCheckForSaveMethods.length; j++)
  {
%>
    if (window.<%=notificationCheckForSaveMethods[j]%>)
    {
      if (<%=notificationCheckForSaveMethods[j]%>() == false)
        return;
    }
<%
  }
%>
    if (window.<%=saveCheckMethod%>)
    {
      if (<%=saveCheckMethod%>() == false)
        return;
    }
    document.editjob.op.value="Save";
    $.ManifoldCF.submit(document.editjob);
  }
}

function Cancel()
{
  document.editjob.op.value="Cancel";
  $.ManifoldCF.submit(document.editjob);
}

function Continue()
{
  document.editjob.op.value="Continue";
  postFormNew();
}

function InsertPipelineStageTransformation(n)
{
  if (editjob.transformation_connectionname.value == "")
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.SelectATransformationStageConnectionName")%>");
    editjob.transformation_connectionname.focus();
    return;
  }
  eval("document.editjob.pipeline_"+n+"_op.value = 'InsertTransformation'");
  postFormSetAnchor("pipeline_"+(n+1)+"_tag");
}

function InsertPipelineStageOutput(n)
{
  if (editjob.output_connectionname.value == "")
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.SelectAnOutputStageConnectionName")%>");
    editjob.output_connectionname.focus();
    return;
  }
  eval("document.editjob.pipeline_"+n+"_op.value = 'InsertOutput'");
  postFormSetAnchor("pipeline_"+(n+1)+"_tag");
}

function AppendPipelineStageOutput()
{
  if (editjob.output_connectionname.value == "")
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.SelectAnOutputStageConnectionName")%>");
    editjob.output_connectionname.focus();
    return;
  }
  document.editjob.output_op.value="Add";
  postFormSetAnchor("output_tag");
}

function DeletePipelineStage(n)
{
  eval("document.editjob.pipeline_"+n+"_op.value = 'Delete'");
  if (n == 0)
    postFormSetAnchor("pipeline_tag");
  else
    postFormSetAnchor("pipeline_"+(n-1)+"_tag");
}

function AppendNotification()
{
  if (editjob.notification_connectionname.value == "")
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.SelectANotificationConnectionName")%>");
    editjob.notification_connectionname.focus();
    return;
  }
  document.editjob.notification_op.value="Add";
  postFormSetAnchor("notification_tag");
}

function DeleteNotification(n)
{
  eval("document.editjob.notification_"+n+"_op.value = 'Delete'");
  if (n == 0)
    postFormSetAnchor("notification_tag");
  else
    postFormSetAnchor("notification_"+(n-1)+"_tag");
}

function AddScheduledTime()
{
  if (editjob.duration.value != "" && !isInteger(editjob.duration.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.DurationMustBeAValidInteger")%>");
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
  if (!checkMaxRecrawl())
    return false;
  if (!checkRecrawlConsistent())
    return false;
  if (!checkReseed())
    return false;
  if (!checkExpiration())
    return false;
  if (!checkSchedule())
    return false;
<%
  for (int j = 0; j < pipelineCheckMethods.length; j++)
  {
%>
  if (window.<%=pipelineCheckMethods[j]%>)
  {
    if (<%=pipelineCheckMethods[j]%>() == false)
      return false;
  }
<%
  }
  for (int j = 0; j < notificationCheckMethods.length; j++)
  {
%>
  if (window.<%=notificationCheckMethods[j]%>)
  {
    if (<%=notificationCheckMethods[j]%>() == false)
      return false;
  }
<%
  }
%>
  // Check the connector part
  if (window.<%=checkMethod%>)
  {
    if (<%=checkMethod%>() == false)
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
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.DurationMustBeAValidInteger")%>");
      eval("editjob."+propertyname+".focus()");
      return false;
    }
    i = i+1;
  }
  return true;
}

function checkRecrawl()
{
  if (editjob.recrawlinterval.value != "" && !isInteger(editjob.recrawlinterval.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.RecrawlIntervalMustBeAValidIntegerOrNull")%>");
    editjob.recrawlinterval.focus();
    return false;
  }
  return true;
}

function checkMaxRecrawl()
{
  if (editjob.maxrecrawlinterval.value != "" && !isInteger(editjob.maxrecrawlinterval.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.MaxRecrawlIntervalMustBeAValidIntegerOrNull")%>");
    editjob.maxrecrawlinterval.focus();
    return false;
  }
  return true;
}

function checkRecrawlConsistent()
{
  if (editjob.maxrecrawlinterval.value != "" && editjob.recrawlinterval.value != "" && parseInt(editjob.maxrecrawlinterval.value) < parseInt(editjob.recrawlinterval.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.MaxRecrawlIntervalMustBeLargerThanRecrawlInterval")%>");
    editjob.maxrecrawlinterval.focus();
    return false;
  }
  return true;
}

function checkReseed()
{
  if (editjob.reseedinterval.value != "" && !isInteger(editjob.reseedinterval.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.ReseedIntervalMustBeAValidIntegerOrNull")%>");
    editjob.reseedinterval.focus();
    return false;
  }
  return true;
}

function checkExpiration()
{
  if (editjob.expirationinterval.value != "" && !isInteger(editjob.expirationinterval.value))
  {
    alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editjob.ExpirationIntervalMustBeAValidIntegerOrNull")%>");
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
  if (connection != null)
  {
    IRepositoryConnector repositoryConnector = repositoryConnectorPool.grab(connection);
    if (repositoryConnector != null)
    {
      try
      {
        repositoryConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),documentSpecification,0,tabsArray);
      }
      finally
      {
        repositoryConnectorPool.release(connection,repositoryConnector);
      }
    }
    Integer repositoryConnectionSequenceNumber = new Integer(0);
    while (sequenceArray.size() < tabsArray.size())
    {
      sequenceArray.add(repositoryConnectionSequenceNumber);
    }
  }
%>

<%
  for (int j = 0; j < pipelineConnectionNames.length; j++)
  {
    if (pipelineIsOutputs[j])
    {
      IOutputConnection outputConnection = outputMgr.load(pipelineConnectionNames[j]);
      if (outputConnection != null)
      {
        IOutputConnector outputConnector = outputConnectorPool.grab(outputConnection);
        if (outputConnector != null)
        {
          try
          {
            outputConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),pipelineSpecifications[j],1+j,tabsArray);
          }
          finally
          {
            outputConnectorPool.release(outputConnection,outputConnector);
          }
        }
      }
    }
    else
    {
      ITransformationConnection transformationConnection = transformationMgr.load(pipelineConnectionNames[j]);
      if (transformationConnection != null)
      {
        ITransformationConnector transformationConnector = transformationConnectorPool.grab(transformationConnection);
        if (transformationConnector != null)
        {
          try
          {
            transformationConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),pipelineSpecifications[j],1+j,tabsArray);
          }
          finally
          {
            transformationConnectorPool.release(transformationConnection,transformationConnector);
          }
        }
      }
    }
    Integer connectionSequenceNumber = new Integer(1+j);
    while (sequenceArray.size() < tabsArray.size())
    {
      sequenceArray.add(connectionSequenceNumber);
    }
  }

%>

<%
  for (int j = 0; j < notificationConnectionNames.length; j++)
  {
    INotificationConnection notificationConnection = notificationMgr.load(notificationConnectionNames[j]);
    if (notificationConnection != null)
    {
      INotificationConnector notificationConnector = notificationConnectorPool.grab(notificationConnection);
      if (notificationConnector != null)
      {
        try
        {
          notificationConnector.outputSpecificationHeader(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),notificationSpecifications[j],1+pipelineConnectionNames.length+j,tabsArray);
        }
        finally
        {
          notificationConnectorPool.release(notificationConnection,notificationConnector);
        }
      }
    }
    Integer connectionSequenceNumber = new Integer(1+pipelineConnectionNames.length+j);
    while (sequenceArray.size() < tabsArray.size())
    {
      sequenceArray.add(connectionSequenceNumber);
    }
  }
%>

<div class="row">
  <div class="col-md-12">
<%
  if (connList.length == 0)
  {
%>
    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.NoRepositoryConnectionsDefinedCreateOneFirst")%></p>
    </div>
<%
  }
  else if (outputList.length == 0)
  {
%>
    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.NoOutputConnectionsDefinedCreateOneFirst")%></p>
    </div>
<%
  }
  else
  {
%>
    <div class="box box-primary">
      <form class="standardform" name="editjob" action="execute.jsp" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="job"/>
        <input type="hidden" name="index" value=""/>
        <input type="hidden" name="tabname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tabName)%>'/>
        <input type="hidden" name="sequencenumber" value='<%=((tabSequenceInt==-1)?"":Integer.toString(tabSequenceInt))%>'/>
<%
  if (jobID != null)
  {
%>
        <input type="hidden" name="jobid" value='<%=jobID%>'/>
<%
  }
%>
        <div class="box-header">
          <div class="tab-group">
<%
  int activeTab = 0;
  int lastTabSeq = -1;
  for (int tabNum = 0; tabNum < tabsArray.size(); tabNum++)
  {
    String tab = tabsArray.get(tabNum);
    Integer sequenceNumber = sequenceArray.get(tabNum);
    int sequenceNumberInt = (sequenceNumber == null)?-1:sequenceNumber.intValue();
    String activeClass = "";
    if(tab.equals(tabName))
    {
      activeClass = "active";
    }
    else
    {
      activeClass = "";
    }

    if(sequenceNumber == null)
    {
%>
          <div class="btn-group" sequenceNumber="<%= (sequenceNumberInt + 1) %>">
            <a class="btn btn-md <%= activeClass %>" href="#tab_<%=tabNum%>" 
                    alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.tab")%>'
<%
      if(activeClass.length() == 0)
      {
%>
                    onclick='<%="javascript:SelectSequencedTab(\""+tab+"\",\""+((sequenceNumber==null)?"":sequenceNumber.toString())+"\");return false;"%>'
<%
      }
%>                    
            ><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a>
          </div>
<%
    }
    else
    {
      int nextSeqNum = -1;
      if(tabNum < tabsArray.size()-1)
      {
        nextSeqNum = sequenceArray.get(tabNum + 1);                    
      }
      else
      {
        nextSeqNum = -1;
      }
    
      if(lastTabSeq != sequenceNumberInt)
      {
%>
          <div class="btn-group" sequenceNumber="<%= (sequenceNumberInt + 1) %>">                        
<% 
      }
%>                    
            <a class="btn btn-md <%= activeClass %>" href="#tab_<%=tabNum%>" 
                    alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.tab")%>'
<%
      if(activeClass.length() == 0)
      {
%>
                    onclick='<%="javascript:SelectSequencedTab(\""+tab+"\",\""+((sequenceNumber==null)?"":sequenceNumber.toString())+"\");return false;"%>'
<%
      }
%>
            ><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a>
<%
      if(nextSeqNum != sequenceNumberInt)
      {
%>
          </div>
<%
      }
      lastTabSeq = sequenceNumberInt;
    }     
  }
  // Missing remainder tab ON PURPOSE -- comes from rowspan=2 tab above
%>
          </div>
        </div>
        <div class="box-body">
          <div class="tab-content">

            <input type="hidden" name="schedulerecords" value='<%=Integer.toString(scheduleRecords.size())%>'/>
<%
  // The NAME tab
  if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Name")) && tabSequenceInt == -1)
  {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.NameColon")%></label>
                <input type="text" size="50" class="form-control" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
              </div>
            </div>
<%
  }
  else
  {
%>
            <input type="hidden" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
<%
  }

  // Hop Filters tab
  if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editjob.HopFilters")) && tabSequenceInt == -1)
  {
    if (relationshipTypes != null)
    {
%>
            <table class="displaytable table table-bordered">
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
                <td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MaximumHopCountForType")%> '<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(relationshipType)%>'<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.colon")%></nobr></td>
                <td class="value" colspan="3">
                  <input name='<%="hopmax_"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(relationshipType)%>' type="text" size="5" value='<%=mapField%>'/>
                </td>
              </tr>
<%
      }
%>

              <tr>
                <td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.HopCountModeColon")%></nobr></td>
                <td class="value" colspan="3">
                  <nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_ACCURATE)%>' <%=((hopcountMode == IJobDescription.HOPCOUNT_ACCURATE) ? "checked=\"true\"" : "")%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.DeleteUnreachableDocuments")%></input></nobr><br/>
                  <nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_NODELETE)%>' <%=((hopcountMode == IJobDescription.HOPCOUNT_NODELETE) ? "checked=\"true\"" : "")%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.KeepUnreachableDocumentsForNow")%></input></nobr><br/>
                  <nobr><input type="radio" name="hopcountmode" value='<%=Integer.toString(IJobDescription.HOPCOUNT_NEVERDELETE)%>' <%=((hopcountMode == IJobDescription.HOPCOUNT_NEVERDELETE) ? "checked=\"true\"" : "")%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.KeepUnreachableDocumentsForever")%></input></nobr><br/>
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
  if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Connection")) && tabSequenceInt == -1)
  {

%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.PipelineColon")%>
                </label>
                <table class="table table-bordered">
                  <tr>
                    <th><input name="pipeline_count" type="hidden" value="<%=pipelineConnectionNames.length%>"/></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StageNumber")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StageType")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StagePrecedent")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StageDescription")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StageConnectionName")%></nobr></th>
                  </tr>
                  <tr>
                    <td></td>
                    <td>1.</td>
                    <td><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Repository")%></td>
                    <td></td>
                    <td></td>
                    <td>
<%
    if (connectionName.length() == 0)
    {
%>
                      <select name="connectionname" class="form-control">
                        <option <%="".equals(connectionName)?"selected=\"selected\"":""%> value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NoneSelected")%> --</option>
<%
      for (IRepositoryConnection conn : connList)
      {
%>
                        <option <%=conn.getName().equals(connectionName)?"selected=\"selected\"":""%>value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
<%
      }
%>
                      </select>
<%
    }
    else
    {
%>
                      <input type="hidden" name="connectionname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%>
<%
    }
%>
                    </td>
                  </tr>
<%
    // A map of stage number to reference count
    Map<Integer,Integer> referenceCounts = new HashMap<Integer,Integer>();
    // A list of precedents to pick from, displayed at the end
    List<Integer> precedents = new ArrayList<Integer>();
    // Repository connection is always allowed
    precedents.add(new Integer(-1));
    Set<String> alreadyPresent = new HashSet<String>();
    for (int j = 0; j < pipelineConnectionNames.length; j++)
    {
      if (pipelineIsOutputs[j])
        alreadyPresent.add(pipelineConnectionNames[j]);
      else
        precedents.add(new Integer(j));
      if (pipelinePrerequisites[j] != -1)
      {
        Integer thisOne = new Integer(pipelinePrerequisites[j]);
        Integer x = referenceCounts.get(thisOne);
        if (x == null)
          referenceCounts.put(thisOne,new Integer(1));
        else
          referenceCounts.put(thisOne,new Integer(x.intValue() + 1));
      }
    }
    boolean anyTransformationButtons = false;
    for (int j = 0; j < pipelineConnectionNames.length; j++)
    {
      String pipelineConnectionName = pipelineConnectionNames[j];
      String pipelineDescription = pipelineDescriptions[j];
      if (pipelineDescription == null)
        pipelineDescription = "";
      String pipelineType = pipelineIsOutputs[j]?Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Output"):Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Transformation");
%>
                  <tr>
                    <td>
                      <input name="pipeline_<%=j%>_op" type="hidden" value="Continue"/>
                      <a name="pipeline_<%=j%>_tag"/>

                      <div class="btn-group-vertical">
<%
      // We don't want to leave orphans around.  If the pipeline stage is an output, we can delete it ONLY if:
      // -- the precedent is -1, OR
      // -- the precedent is not -1 BUT more than one stage refers to the precedent
      if (!pipelineIsOutputs[j] || pipelinePrerequisites[j] == -1 || referenceCounts.get(new Integer(pipelinePrerequisites[j])).intValue() > 1)
      {
%>
                        <input type="button" class="btn btn-sm btn-danger" value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Delete")%>" alt='<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Deletepipelinestage")%>' onclick="javascript:DeletePipelineStage(<%=j%>);"/>
<%
      }
      if (transformationList.length > 0)
      {
        anyTransformationButtons = true;
%>
                        <input type="button" class="btn btn-sm btn-primary" value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.InsertTransformationBefore")%>" alt='<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Insertnewtransformationhere")%>' onclick="javascript:InsertPipelineStageTransformation(<%=j%>);"/>
<%
      }
      if (outputList.length != alreadyPresent.size())
      {
%>
                        <input type="button" class="btn btn-sm btn-primary"
                               value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.InsertOutputBefore")%>"
                               alt='<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Insertnewoutputhere")%>'
                               onclick="javascript:InsertPipelineStageOutput(<%=j%>);"/>
<%
      }
%>
                      </div>
                    </td>
                    <td><%=(j+2)%>.</td>
                    <td><%=pipelineType%>
                      <input name="pipeline_<%=j%>_isoutput" type="hidden" value='<%=pipelineIsOutputs[j]?"true":"false"%>'/>
                    </td>
                    <td><%=(pipelinePrerequisites[j] + 2)%>.
                      <input name="pipeline_<%=j%>_precedent" type="hidden" value="<%=pipelinePrerequisites[j]%>"/>
                    </td>
                    <td>
                      <input name="pipeline_<%=j%>_description" type="text" size="30" class="from-control" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pipelineDescription)%>"/>
                    </td>
                    <td>
                      <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pipelineConnectionName)%></nobr>
                      <input name="pipeline_<%=j%>_connectionname" type="hidden" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pipelineConnectionName)%>"/>
                    </td>
                  </tr>
<%
    }
    if (anyTransformationButtons)
    {
%>
                  <tr class="formrow">
                    <td><a name="transformation_tag"/></td>
                    <td></td>
                    <td><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Transformation")%></td>
                    <td></td>
                    <td><input name="transformation_description" type="text" class="form-control" size="30" value=""/></td>
                    <td>
                      <select name="transformation_connectionname" class="form-control">
                        <option selected="selected" value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NoneSelected")%> --</option>
<%
      for (ITransformationConnection conn : transformationList)
      {
%>
                        <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
<%
      }
%>
                      </select>
                    </td>
                  </tr>
<%
    }
    if (outputList.length != alreadyPresent.size())
    {
%>
                  <tr class="formrow">
                    <td>
                      <a name="output_tag"/>
                      <input type="button" class="btn btn-primary btn-sm"
                             value='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddOutput")%>'
                             alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddAnOutput")%>'
                             onclick="javascript:AppendPipelineStageOutput();"/>
                      <input name="output_op" type="hidden" value="Continue"/>
                    </td>
                    <td></td>
                    <td><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Output")%></td>
                    <td>
                      <select name="output_precedent" class="form-control">
<%
      for (Integer pre : precedents)
      {
%>
                        <option value="<%=pre%>"><%=(pre.intValue()+2)%></option>
<%
      }
%>
                      </select>
                    </td>
                    <td><input name="output_description" type="text" class="form-control" size="30" value=""/></td>
                    <td>
                      <select name="output_connectionname" class="form-control">
                        <option selected="selected" value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NoneSelected")%> --</option>
<%
      for (IOutputConnection conn : outputList)
      {
        if (!alreadyPresent.contains(conn.getName()))
        {
%>
                        <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
<%
        }
      }
%>
                      </select>
                    </td>
                  </tr>
<%
    }
%>
                </table>
              </div>

<%
    alreadyPresent = new HashSet<String>();
    for (int j = 0; j < notificationConnectionNames.length; j++)
    {
      alreadyPresent.add(notificationConnectionNames[j]);
    }
    if (notificationList.length > 0)
    {
%>
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NotificationsColon")%></label>
                <table class="table table-bordered">
                  <tr>
                    <th><input name="notification_count" type="hidden" value="<%=notificationConnectionNames.length%>"/></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.StageNumber")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NotificationDescription")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NotificationConnectionName")%></nobr></th>
                  </tr>
<%
      for (int j = 0; j < notificationConnectionNames.length; j++)
      {
        String notificationConnectionName = notificationConnectionNames[j];
        String notificationDescription = notificationDescriptions[j];
        if (notificationDescription == null)
          notificationDescription = "";
%>
                  <tr>
                    <td>
                      <input name="notification_<%=j%>_op" type="hidden" value="Continue"/>
                      <a name="notification_<%=j%>_tag"/>
                      <input type="button" class="btn btn-danger btn-sm"
                             value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Delete")%>"
                             alt='<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Deletenotification")%>'
                             onclick="javascript:DeleteNotification(<%=j%>);"/>
                    </td>
                    <td><%=(j+pipelineConnectionNames.length+2)%>.</td>
                    <td>
                      <input name="notification_<%=j%>_description" type="text" size="30"
                             class="form-control"
                             value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(notificationDescription)%>"/>
                    </td>
                    <td>
                      <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(notificationConnectionName)%></nobr>
                      <input name="notification_<%=j%>_connectionname" type="hidden" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(notificationConnectionName)%>"/>
                    </td>
                  </tr>
<%
      }
      if (notificationList.length != alreadyPresent.size())
      {
%>
                  <tr class="formrow">
                    <td>
                      <a name="notification_tag"/>
                      <input type="button" class="btn btn-primary btn-sm"
                             value='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddNotification")%>'
                             alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddANotification")%>'
                             onclick="javascript:AppendNotification();"/>
                      <input name="notification_op" type="hidden" value="Continue"/>
                    </td>
                    <td></td>
                    <td><input name="notification_description" type="text" size="30" value="" class="form-control"/></td>
                    <td>
                      <select name="notification_connectionname" class="form-control">
                        <option selected="selected" value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.NoneSelected")%> --</option>
<%
        for (INotificationConnection conn : notificationList)
        {
          if (!alreadyPresent.contains(conn.getName()))
          {
%>
                        <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(conn.getName())%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(conn.getName())%></option>
<%
          }
        }
%>
                      </select>
                    </td>
                  </tr>
<%
      }
%>
                </table>
              </div>
<%
    }
%>
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.PriorityColon")%></label>
                <select name="priority" class="form-control">
                  <option value="1" <%=(priority==1)?"selected=\"selected\"":""%>>1 <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Highest")%></option>
                  <option value="2" <%=(priority==2)?"selected=\"selected\"":""%>>2</option>
                  <option value="3" <%=(priority==3)?"selected=\"selected\"":""%>>3</option>
                  <option value="4" <%=(priority==4)?"selected=\"selected\"":""%>>4</option>
                  <option value="5" <%=(priority==5)?"selected=\"selected\"":""%>>5</option>
                  <option value="6" <%=(priority==6)?"selected=\"selected\"":""%>>6</option>
                  <option value="7" <%=(priority==7)?"selected=\"selected\"":""%>>7</option>
                  <option value="8" <%=(priority==8)?"selected=\"selected\"":""%>>8</option>
                  <option value="9" <%=(priority==9)?"selected=\"selected\"":""%>>9</option>
                  <option value="10" <%=(priority==10)?"selected=\"selected\"":""%>>10 <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Lowest")%></option>
                </select>
              </div>
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StartMethodColon")%></label>
                <select name="startmethod" class="form-control">
                  <option value='<%=IJobDescription.START_WINDOWBEGIN%>' <%=(startMethod==IJobDescription.START_WINDOWBEGIN)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StartWhenScheduleWindowStarts")%></option>
                  <option value='<%=IJobDescription.START_WINDOWINSIDE%>' <%=(startMethod==IJobDescription.START_WINDOWINSIDE)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.StartEvenInsideAScheduleWindow")%></option>
                  <option value='<%=IJobDescription.START_DISABLE%>' <%=(startMethod==IJobDescription.START_DISABLE)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.DontAutomaticallyStartThisJob")%></option>
                </select>
              </div>
            </div>
<%
  }
  else
  {
%>
            <input type="hidden" name="connectionname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
            <input type="hidden" name="pipeline_count" value="<%=pipelineConnectionNames.length%>"/>
            <input type="hidden" name="notification_count" value="<%=notificationConnectionNames.length%>"/>
<%
    for (int j = 0; j < pipelineConnectionNames.length; j++)
    {
      String pipelineConnectionName = pipelineConnectionNames[j];
      String pipelineDescription = pipelineDescriptions[j];
      if (pipelineDescription == null)
        pipelineDescription = "";
%>
            <input name="pipeline_<%=j%>_isoutput" type="hidden" value='<%=pipelineIsOutputs[j]?"true":"false"%>'/>
            <input name="pipeline_<%=j%>_precedent" type="hidden" value="<%=pipelinePrerequisites[j]%>"/>
            <input type="hidden" name="pipeline_<%=j%>_connectionname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pipelineConnectionName)%>"/>
            <input type="hidden" name="pipeline_<%=j%>_description" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pipelineDescription)%>"/>
<%
    }
    for (int j = 0; j < notificationConnectionNames.length; j++)
    {
      String notificationConnectionName = notificationConnectionNames[j];
      String notificationDescription = notificationDescriptions[j];
      if (notificationDescription == null)
        notificationDescription = "";
%>
            <input type="hidden" name="notification_<%=j%>_connectionname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(notificationConnectionName)%>"/>
            <input type="hidden" name="notification_<%=j%>_description" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(notificationDescription)%>"/>
<%
    }

%>
            <input type="hidden" name="priority" value='<%=priority%>'/>
            <input type="hidden" name="startmethod" value='<%=startMethod%>'/>
<%
  }

  // Scheduling tab
  if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Scheduling")) && tabSequenceInt == -1)
  {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
<%
    if (model != -1 && model != IRepositoryConnector.MODEL_ADD_CHANGE_DELETE && model != IRepositoryConnector.MODEL_CHAINED_ADD_CHANGE_DELETE)
    {
%>
              <table class="table table-bordered">
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ScheduleTypeColon")%></nobr></th>
                  <td colspan="3">
                    <select name="scheduletype" class="form-control">
                      <option value='<%=IJobDescription.TYPE_CONTINUOUS%>' <%=(type==IJobDescription.TYPE_CONTINUOUS)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.RescanDocumentsDynamically")%></option>
                      <option value='<%=IJobDescription.TYPE_SPECIFIED%>' <%=(type==IJobDescription.TYPE_SPECIFIED)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ScanEveryDocumentOnce")%></option>
                    </select>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.RecrawlIntervalIfContinuousColon")%></nobr></th>
                  <td colspan="3">
                    <nobr><input type="text" size="5" name="recrawlinterval" class="form-control" value='<%=((recrawlInterval==null)?"":recrawlInterval.toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutesBlankInfinity")%></nobr>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MaxRecrawlIntervalIfContinuousColon")%></nobr></th>
                  <td colspan="3">
                    <nobr><input type="text" size="5" name="maxrecrawlinterval" class="form-control" value='<%=((maxRecrawlInterval==null)?"":maxRecrawlInterval.toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutesBlankInfinity")%></nobr>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ExpirationIntervalIfContinuousColon")%></nobr></th>
                  <td colspan="3">
                    <nobr><input type="text" size="5" name="expirationinterval" class="form-control" value='<%=((expirationInterval==null)?"":expirationInterval.toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutesBlankInfinity")%></nobr>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ReseedIntervalIfContinuousColon")%></nobr></th>
                  <td colspan="3">
                    <nobr><input type="text" size="5" name="reseedinterval" class="form-control" value='<%=((reseedInterval==null)?"":reseedInterval.toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutesBlankInfinity")%></nobr>
                  </td>
                </tr>
<%
    }
    else
    {
%>
                <input type="hidden" name="scheduletype" value='<%=type%>'/>
                <input type="hidden" name="recrawlinterval" value='<%=((recrawlInterval==null)?"":recrawlInterval.toString())%>'/>
                <input type="hidden" name="maxrecrawlinterval" value='<%=((maxRecrawlInterval==null)?"":maxRecrawlInterval.toString())%>'/>
                <input type="hidden" name="reseedinterval" value='<%=((reseedInterval==null)?"":reseedInterval.toString())%>'/>
                <input type="hidden" name="expirationinterval" value='<%=((expirationInterval==null)?"":expirationInterval.toString())%>'/>
<%
    }
    
    String[] availableIDs = java.util.TimeZone.getAvailableIDs();
    String localTimezone = java.util.TimeZone.getDefault().getID();

    if (scheduleRecords.size() == 0)
    {
%>
                <div class="callout callout-info">
                  <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.NoScheduleSpecified")%>
                </div>
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
        boolean srRequestMinimum = sr.getRequestMinimum();
        String srTimezone = sr.getTimezone();
        if (srTimezone == null)
        {
          srTimezone = java.util.TimeZone.getDefault().getID();
        }
        
        String postFix = Integer.toString(l);
        int k;

%>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ScheduledTimeColon")%></nobr></th>
                  <td colspan="3" class="value">
                    <div class="input-group">
                      <select class="selectpicker schedulepulldown" data-size="10" data-live-search="true" data-live-search-normalize="true" data-live-search-style="contains" name='<%="timezone"+postFix%>'>
<%
        k = 0;
        while (k < availableIDs.length)
        {
          String id = availableIDs[k];
          if (id.equals(srTimezone))
          {
%>
                        <option value='<%=id%>' selected=\"selected\"><%=id%></option>
<%
          }
          else
          {
%>
                        <option value='<%=id%>'><%=id%></option>
<%
          }
          k++;
        }
%>
                      </select>
                      <span class="label">:</span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2"  multiple="true" name='<%="dayofweek"+postFix%>'>
                        <option value="none" <%=(srDayOfWeek==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.AnyDayOfWeek")%></option>
                        <option value="0" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(0))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Sundays")%></option>
                        <option value="1" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(1))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Mondays")%></option>
                        <option value="2" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(2))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Tuesdays")%></option>
                        <option value="3" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(3))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Wednesdays")%></option>
                        <option value="4" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(4))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Thursdays")%></option>
                        <option value="5" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(5))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Fridays")%></option>
                        <option value="6" <%=(srDayOfWeek!=null&&srDayOfWeek.checkValue(6))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Saturdays")%></option>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.at")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name='<%="hourofday"+postFix%>'>
                        <option value="none" <%=(srHourOfDay==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MidnightAnyHourOfDay")%></option>
<%
        k = 0;
        while (k < 24)
        {
          int q = k;
          String ampm;
          if (k < 12)
            ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.am");
          else
          {
            ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.pm");
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
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.plus")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name='<%="minutesofhour"+postFix%>'>
                        <option value="none" <%=(srMinutesOfHour==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Nothing")%></option>
<%
        k = 0;
        while (k < 60)
        {
%>
                        <option value='<%=k%>' <%=(srMinutesOfHour!=null&&srMinutesOfHour.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k)%> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutes")%></option>
<%
          k++;
        }
%>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.in")%></span>
                      <select class="selectpicker" multiple="true" data-size="10" data-selected-text-format="count > 2" name='<%="monthofyear"+postFix%>'>
                        <option value="none" <%=(srMonthOfYear==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.EveryMonthOfYear")%></option>
                        <option value="0" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(0))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.January")%></option>
                        <option value="1" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(1))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.February")%></option>
                        <option value="2" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(2))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.March")%></option>
                        <option value="3" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(3))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.April")%></option>
                        <option value="4" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(4))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.May")%></option>
                        <option value="5" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(5))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.June")%></option>
                        <option value="6" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(6))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.July")%></option>
                        <option value="7" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(7))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.August")%></option>
                        <option value="8" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(8))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.September")%></option>
                        <option value="9" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(9))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.October")%></option>
                        <option value="10" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(10))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.November")%></option>
                        <option value="11" <%=(srMonthOfYear!=null&&srMonthOfYear.checkValue(11))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.December")%></option>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.on")%></span>
                      <select class="selectpicker schedulepulldown" data-size="10" data-selected-text-format="count > 2" multiple="true" name='<%="dayofmonth"+postFix%>'>
                        <option value="none" <%=(srDayOfMonth==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.AnyDayOfMonth")%></option>
<%
        k = 0;
        while (k < 31)
        {
          int value = (k+1) % 10;
          String suffix;
          if (value == 1 && k != 10)
            suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.st");
          else if (value == 2 && k != 11)
            suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.nd");
          else if (value == 3 && k != 12)
            suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.rd");
          else
          suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.th");
%>
                        <option value='<%=Integer.toString(k)%>' <%=(srDayOfMonth!=null&&srDayOfMonth.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix+" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.dayofmonth")%></option>
<%
          k++;
        }
%>
                      </select>
                    </div>
                    <input type="hidden" name='<%="year"+postFix%>' value="none"/>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MaximumRunTimeColon")%></nobr></th>
                  <td class="value">
                    <input type="text" size="5" name='<%="duration"+postFix%>' class="form-control" value='<%=((srDuration==null)?"":new Long(srDuration.longValue()/60000L).toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutes")%>
                  </td>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.JobInvocationColon")%></nobr></th>
                  <td class="value">
                    <select class="form-control schedulepulldown" name='<%="invocation"+postFix%>'>
                      <option value="complete" <%=(srRequestMinimum==false)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Complete")%></option>
                      <option value="minimal" <%=srRequestMinimum?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Minimal")%></option>
                    </select>
                  </td>
                </tr>
                <tr>
                  <td class="message" colspan="4">
                    <a name='<%="remove_schedule_"+Integer.toString(l)%>'>
                      <input type="button" class="btn btn-primary btn-sm"
                             value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.RemoveSchedule")%>"
                             onClick='<%="Javascript:RemoveSchedule("+Integer.toString(l)+")"%>'
                             alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.RemoveScheduleRecord")+Integer.toString(l)%>'/></a>
                    <input type="hidden" name='<%="recordop"+postFix%>' value=""/>
                  </td>
                </tr>
<%
        l++;
      }
    }
%>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.ScheduledTimeColon")%></nobr></th>
                  <td colspan="3" class="value">
                    <div class="input-group">
                      <select class="selectpicker schedulepulldown" data-size="10" data-live-search="true" data-live-search-normalize="true" data-live-search-style="contains" name="timezone">
<%
    int k = 0;
    while (k < availableIDs.length)
    {
      String id = availableIDs[k];
      if (id.equals(localTimezone))
      {
%>
                        <option value='<%=id%>' selected=\"selected\"><%=id%></option>
<%
      }
      else
      {
%>
                        <option value='<%=id%>'><%=id%></option>
<%
      }
      k++;
    }
%>
                      </select>
                      <span class="label">:</span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name="dayofweek">
                        <option value="none" <%=(dayOfWeek==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.AnyDayOfWeek")%></option>
                        <option value="0" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(0))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Sundays")%></option>
                        <option value="1" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(1))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Mondays")%></option>
                        <option value="2" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(2))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Tuesdays")%></option>
                        <option value="3" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(3))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Wednesdays")%></option>
                        <option value="4" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(4))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Thursdays")%></option>
                        <option value="5" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(5))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Fridays")%></option>
                        <option value="6" <%=(dayOfWeek!=null&&dayOfWeek.checkValue(6))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Saturdays")%></option>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.at")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name="hourofday">
                        <option value="none" <%=(hourOfDay==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MidnightAnyHourOfDay")%></option>
<%
    k = 0;
    while (k < 24)
    {
      int q = k;
      String ampm;
      if (k < 12)
        ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.am");
      else
      {
        ampm = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.pm");
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
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.plus")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name="minutesofhour">
                        <option value="none" <%=(minutesOfHour==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Nothing")%></option>
<%
    k = 0;
    while (k < 60)
    {
%>
                        <option value='<%=k%>' <%=(minutesOfHour!=null&&minutesOfHour.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k)%> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutes")%></option>
<%
      k++;
    }
%>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.in")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name="monthofyear">
                        <option value="none" <%=(monthOfYear==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.EveryMonthOfYear")%></option>
                        <option value="0" <%=(monthOfYear!=null&&monthOfYear.checkValue(0))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.January")%></option>
                        <option value="1" <%=(monthOfYear!=null&&monthOfYear.checkValue(1))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.February")%></option>
                        <option value="2" <%=(monthOfYear!=null&&monthOfYear.checkValue(2))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.March")%></option>
                        <option value="3" <%=(monthOfYear!=null&&monthOfYear.checkValue(3))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.April")%></option>
                        <option value="4" <%=(monthOfYear!=null&&monthOfYear.checkValue(4))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.May")%></option>
                        <option value="5" <%=(monthOfYear!=null&&monthOfYear.checkValue(5))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.June")%></option>
                        <option value="6" <%=(monthOfYear!=null&&monthOfYear.checkValue(6))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.July")%></option>
                        <option value="7" <%=(monthOfYear!=null&&monthOfYear.checkValue(7))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.August")%></option>
                        <option value="8" <%=(monthOfYear!=null&&monthOfYear.checkValue(8))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.September")%></option>
                        <option value="9" <%=(monthOfYear!=null&&monthOfYear.checkValue(9))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.October")%></option>
                        <option value="10" <%=(monthOfYear!=null&&monthOfYear.checkValue(10))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.November")%></option>
                        <option value="11" <%=(monthOfYear!=null&&monthOfYear.checkValue(11))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.December")%></option>
                      </select>
                      <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editjob.on")%></span>
                      <select class="selectpicker" data-size="10" data-selected-text-format="count > 2" multiple="true" name="dayofmonth">
                        <option value="none" <%=(dayOfMonth==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.AnyDayOfMonth")%></option>
<%
    k = 0;
    while (k < 31)
    {
      int value = (k+1) % 10;
      String suffix;
      if (value == 1 && k != 10)
        suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.st");
      else if (value == 2 && k != 11)
        suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.nd");
      else if (value == 3 && k != 12)
        suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.rd");
      else
        suffix = Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.th");
%>
                        <option value='<%=Integer.toString(k)%>' <%=(dayOfMonth!=null&&dayOfMonth.checkValue(k))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix+" "+Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.dayofmonth")%></option>
<%
      k++;
    }
%>
                      </select>
                    </div>
                    <input type="hidden" name="year" value="none"/>
                  </td>
                </tr>
                <tr>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.MaximumRunTimeColon")%></nobr></th>
                  <td class="value">
                    <input type="text" size="5" name="duration" class="form-control" value='<%=((duration==null)?"":duration.toString())%>'/> <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.minutes")%>
                  </td>
                  <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.JobInvocationColon")%></nobr></th>
                  <td class="value">
                    <select class="selectpicker" data-size="10" name="invocation" class="form-control">
                      <option value="complete" <%=(requestMinimum==false)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Complete")%></option>
                      <option value="minimal" <%=requestMinimum ?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editjob.Minimal")%></option>
                    </select>
                  </td>
                </tr>
                <tr>
                  <td class="message" colspan="4">
                    <input type="hidden" name="recordop" value=""/>
                    <a name="add_schedule">
                      <input type="button" class="btn btn-primary btn-sm"
                              value="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddScheduledTime")%>"
                              onClick="javascript:AddScheduledTime()"
                              alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.AddNewScheduleRecord")%>"/></a>
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
              <input type="hidden" name="maxrecrawlinterval" value='<%=((maxRecrawlInterval==null)?"":maxRecrawlInterval.toString())%>'/>
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
      boolean srRequestMinimum = sr.getRequestMinimum();
      String srTimezone = sr.getTimezone();
      String postFix = Integer.toString(l);

%>
              <input type="hidden" name='<%="timezone"+postFix%>' value='<%=((srTimezone==null)?"":srTimezone)%>'/>
<%
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
              <input type="hidden" name='<%="invocation"+postFix%>' value='<%=srRequestMinimum?"minimal":"complete"%>'/>
              <input type="hidden" name='<%="year"+postFix%>' value="none"/>
<%
      l++;
    }
  }

  if (connection != null)
  {
    IRepositoryConnector repositoryConnector = repositoryConnectorPool.grab(connection);
    if (repositoryConnector != null)
    {
      try
      {
        repositoryConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),documentSpecification,0,tabSequenceInt,tabName);
      }
      finally
      {
        repositoryConnectorPool.release(connection,repositoryConnector);
      }
%>
              <input type="hidden" name="connectionpresent" value="true"/>
<%
    }
  }

  boolean outputPresent = false;
  for (int j = 0; j < pipelineConnectionNames.length; j++)
  {
    if (pipelineIsOutputs[j])
    {
      outputPresent = true;
      IOutputConnection outputConnection = outputMgr.load(pipelineConnectionNames[j]);
      if (outputConnection != null)
      {
        IOutputConnector outputConnector = outputConnectorPool.grab(outputConnection);
        if (outputConnector != null)
        {
          try
          {
            outputConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),pipelineSpecifications[j],1+j,tabSequenceInt,tabName);
          }
          finally
          {
            outputConnectorPool.release(outputConnection,outputConnector);
          }
        }
      }
    }
    else
    {
      ITransformationConnection transformationConnection = transformationMgr.load(pipelineConnectionNames[j]);
      if (transformationConnection != null)
      {
        ITransformationConnector transformationConnector = transformationConnectorPool.grab(transformationConnection);
        if (transformationConnector != null)
        {
          try
          {
            transformationConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),pipelineSpecifications[j],1+j,tabSequenceInt,tabName);
          }
          finally
          {
            transformationConnectorPool.release(transformationConnection,transformationConnector);
          }
        }
      }
    }
  }

  for (int j = 0; j < notificationConnectionNames.length; j++)
  {
    INotificationConnection notificationConnection = notificationMgr.load(notificationConnectionNames[j]);
    if (notificationConnection != null)
    {
      INotificationConnector notificationConnector = notificationConnectorPool.grab(notificationConnection);
      if (notificationConnector != null)
      {
        try
        {
          notificationConnector.outputSpecificationBody(new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),notificationSpecifications[j],1+pipelineConnectionNames.length+j,tabSequenceInt,tabName);
        }
        finally
        {
          notificationConnectorPool.release(notificationConnection,notificationConnector);
        }
      }
    }
  }

%>
            </div>
          </div>
          <div class="box-footer clearfix">
            <div class="btn-group">
<%
  if (connectionName.length() > 0 && outputPresent)
  {
%>
              <a href="#" class="btn btn-primary" onClick="javascript:Save()"
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.SaveThisJob")%>" data-toggle="tooltip"><i class="fa fa-save fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.Save")%></a>
<%
  }
  else
  {
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editjob.Connection")) && tabSequenceInt == -1)
    {
%>
              <a href="#" class="btn btn-primary" onClick="javascript:Continue()" 
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.ContinueToNextScreen")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.Continue")%></a>
<%
    }
  }
%>
              <a href="#" class="btn btn-primary" onClick="javascript:Cancel()" 
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.CancelJobEditing")%>" data-toggle="tooltip"><i class="fa fa-times-circle-o fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editjob.cancel")%></a>

            </div>
          </div>
      </form>
<%
  }
%>
    </div>
  </div>
</div>

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
