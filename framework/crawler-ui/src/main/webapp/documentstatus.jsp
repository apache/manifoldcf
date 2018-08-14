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
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_VIEW_REPORTS))
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
  String[] documentStateTypes;
  if (variableContext.getParameter("statusdocumentstates_posted") != null)
  {
    documentStateTypes = variableContext.getParameterValues("statusdocumentstates");
    if (documentStateTypes == null)
      documentStateTypes = new String[0];
  }
  else
    documentStateTypes = null;

  // There is a selection criteria based on the document status; these are also integers defined in IJobManager.
  String[] documentStatusTypes;
  if (variableContext.getParameter("statusdocumentstatuses_posted") != null)
  {
    documentStatusTypes = variableContext.getParameterValues("statusdocumentstatuses");
    if (documentStatusTypes == null)
      documentStatusTypes = new String[0];
  }
  else
    documentStatusTypes = null;

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
  int[] matchingStates;
  if (documentStateTypes == null)
  {
    matchingStates = new int[]{IJobManager.DOCSTATE_NEVERPROCESSED,IJobManager.DOCSTATE_PREVIOUSLYPROCESSED,
      IJobManager.DOCSTATE_OUTOFSCOPE};
  }
  else
  {
    matchingStates = new int[documentStateTypes.length];
    k = 0;
    while (k < matchingStates.length)
    {
      matchingStates[k] = new Integer(documentStateTypes[k]).intValue();
      k++;
    }
  }
  HashMap matchingStatesHash = new HashMap();
  k = 0;
  while (k < matchingStates.length)
  {
    Integer state = new Integer(matchingStates[k++]);
    matchingStatesHash.put(state,state);
  }

  // Convert the status from a string to a number that will be understood by IJobManager
  int[] matchingStatuses;
  if (documentStatusTypes == null)
  {
    matchingStatuses = new int[]{IJobManager.DOCSTATUS_INACTIVE,IJobManager.DOCSTATUS_PROCESSING,IJobManager.DOCSTATUS_EXPIRING,
      IJobManager.DOCSTATUS_DELETING,IJobManager.DOCSTATUS_READYFORPROCESSING,IJobManager.DOCSTATUS_READYFOREXPIRATION,
      IJobManager.DOCSTATUS_WAITINGFORPROCESSING,IJobManager.DOCSTATUS_WAITINGFOREXPIRATION,IJobManager.DOCSTATUS_WAITINGFOREVER,
      IJobManager.DOCSTATUS_HOPCOUNTEXCEEDED};
  }
  else
  {
    matchingStatuses = new int[documentStatusTypes.length];
    k = 0;
    while (k < matchingStatuses.length)
    {
      matchingStatuses[k] = new Integer(documentStatusTypes[k]).intValue();
      k++;
    }
  }
  HashMap matchingStatusesHash = new HashMap();
  k = 0;
  while (k < matchingStatuses.length)
  {
    Integer status = new Integer(matchingStatuses[k++]);
    matchingStatusesHash.put(status,status);
  }

%>

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "documentstatus.ApacheManifoldCFDocumentStatus")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentStatus")%>',
      'statusReports'
  );

  function Go()
  {
    if (!isInteger(report.rowcount.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"documentstatus.EnterALegalNumberForRowsPerPage")%>");
      report.rowcount.focus();
      return;
    }
    if (!isRegularExpression(report.statusidentifiermatch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"documentstatus.IdentifierMatchMustBeAValidRegularExpression")%>");
      report.statusidentifiermatch.focus();
      return;
    }

    document.report.op.value="Status";
    document.report.action = document.report.action + "#MainButton";
    $.ManifoldCF.submit(document.report);
  }

  function Continue()
  {
    if (!isRegularExpression(report.statusidentifiermatch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"documentstatus.IdentifierMatchMustBeAValidRegularExpression")%>");
      report.statusidentifiermatch.focus();
      return;
    }
    document.report.op.value = "Continue";
    document.report.action = document.report.action + "#MainButton";
    $.ManifoldCF.submit(document.report);
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

  $(function ()
  {
    $('.selectpicker').selectpicker();
  });

  //-->
</script>

<div class="row">
  <div class="col-md-12">
    <form class="standardform" name="report" action="execute.jsp" method="POST">
      <input type="hidden" name="op" value="Continue" />
      <input type="hidden" name="type" value="documentstatus" />

      <div class="box box-primary">
        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Connection")%></th>
              <td>
                <select class="selectpicker" name="statusconnection">
                  <option <%=(statusConnection.length()==0)?"selected=\"selected\"":""%> value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.NotSpecified")%> --</option>
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
                  <option <%=(thisConnectionName.equals(statusConnection))?"selected=\"selected\"":""%> value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisConnectionName)%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
  }
%>
                </select>
              </td>
<%
  if (eligibleList != null)
  {
%>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Jobs")%></th>
              <td>
                <select class="selectpicker" multiple="true" name="statusjobs">
<%
    i = 0;
    while (i < eligibleList.length)
    {
      IJobDescription job = eligibleList[i++];
      String description = job.getDescription();
      Long identifier = job.getID();
%>
                  <option <%=((selectedJobs.get(identifier)==null)?"":"selected=\"selected\"")%> value='<%=identifier.toString()%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></option>
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
              <td colspan="2"></td>
<%
  }
%>

            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.TimeOffsetFromNowMinutes")%></th>
              <td colspan="3">
                <input name="statusscheduleoffset" type="text" size="6" value="" class="form-control" />
              </td>
            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentState")%></th>
              <td colspan="3">
                <input name="statusdocumentstates_posted" type="hidden" value="true"/>
                <select class="selectpicker" name="statusdocumentstates" multiple="true">
                  <option <%=((matchingStatesHash.get(new Integer(IJobManager.DOCSTATE_NEVERPROCESSED))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATE_NEVERPROCESSED)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsThatHaveNeverBeenProcessed")%></option>
                  <option <%=((matchingStatesHash.get(new Integer(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsProcessedAtLeastOnce")%></option>
                  <option <%=((matchingStatesHash.get(new Integer(IJobManager.DOCSTATE_OUTOFSCOPE))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATE_OUTOFSCOPE)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsOutOfScope")%></option>
                </select>
              </td>
            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentState")%></th>
              <td colspan="3">
                <input name="statusdocumentstatuses_posted" type="hidden" value="true"/>
                <select class="selectpicker" name="statusdocumentstatuses" multiple="true">
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_INACTIVE))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_INACTIVE)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsThatAreNoLongerActive")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_PROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_PROCESSING)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsCurrentlyInProgress")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_EXPIRING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_EXPIRING)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsCurrentlyBeingExpired")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_DELETING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_DELETING)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsCurrentlyBeingDeleted")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_READYFORPROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_READYFORPROCESSING)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsCurrentlyAvailableForProcessing")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_READYFOREXPIRATION))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_READYFOREXPIRATION)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsCurrentlyAvailableForExpiration")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFORPROCESSING))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFORPROCESSING)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsNotYetProcessable")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsNotYetExpirable")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_WAITINGFOREVER))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_WAITINGFOREVER)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsWaitingForever")%></option>
                  <option <%=((matchingStatusesHash.get(new Integer(IJobManager.DOCSTATUS_HOPCOUNTEXCEEDED))==null)?"":"selected=\"selected\"")%> value='<%=Integer.toString(IJobManager.DOCSTATUS_HOPCOUNTEXCEEDED)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentsHopcountExceeded")%></option>
                </select>
              </td>
            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.DocumentIdentifierMatch")%></th>
              <td colspan="3">
                <input type="text" name="statusidentifiermatch" size="40" class="form-control" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(identifierMatch)%>'/>
              </td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
<%
  if (statusConnection.length() > 0 && statusJobIdentifiers.length > 0)
  {
%>
            <a href="#" class="btn btn-primary" role="button" onClick="javascript:Go()" 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"documentstatus.ExecuteThisQuery")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"documentstatus.ExecuteThisQuery")%></a>
<%
  }
  else
  {
%>
            <a href="#" class="btn btn-primary" role="button" onClick="javascript:Continue()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"documentstatus.Continue")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"documentstatus.Continue")%></a>
<%
  }
%>
          </div>
        </div>
      </div>

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
      <input type="hidden" name="sortorder" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sortOrder.toString())%>'/>

      <div class="box box-primary">
        <div class="box-body table-responsive">
          <table class="table table-bordered">
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Identifier")%></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("job");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Job")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("state");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.State")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("status");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Status")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("scheduled");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Scheduled")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("action");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.ScheduledAction")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("retrycount");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.RetryCount")%></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("retrylimit");'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.RetryLimit")%></a></th>
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

        String[] identifierBreakdown = org.apache.manifoldcf.ui.util.Formatter.formatString(row.getValue("identifier").toString(),64,true,true);
        Long scheduleTime = (Long)row.getValue("scheduled");
        String scheduleTimeString = "";
        if (scheduleTime != null)
          scheduleTimeString = org.apache.manifoldcf.ui.util.Formatter.formatTime(clientTimezone, pageContext.getRequest().getLocale(), scheduleTime.longValue());
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
          retryLimitString = org.apache.manifoldcf.ui.util.Formatter.formatTime(clientTimezone, pageContext.getRequest().getLocale(), retryLimit.longValue());

%>
            <tr>
              <td>
<%
        int q = 0;
        while (q < identifierBreakdown.length)
        {
%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(identifierBreakdown[q++])%></nobr><br />
<%
        }
%>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(row.getValue("job").toString())%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(row.getValue("state").toString())%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(row.getValue("status").toString())%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(scheduleTimeString)%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(scheduledActionString)%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(retryCountString)%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(retryLimitString)%></td>
            </tr>
<%
        zz++;
      }
%>
          </table>
        </div>
        <div class="box-footer">
          <ul class="pagination pagination-sm no-margin pull-left">
<%
      if (startRow == 0)
      {
%>
            <li><a href="#"><i class="fa fa-arrow-circle-o-left fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Previous")%></a></li>
<%
      }
      else
      {
%>
            <li><a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow-rowCount)+");"%>' title="Previous page" data-toggle="tooltip"><i class="fa fa-arrow-circle-o-left fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Previous")%></a></li>
<%
      }
%>

<%
      if (hasMoreRows == false)
      {
%>
            <li><a href="#"><i class="fa fa-arrow-circle-o-right fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Next")%></a></li>
<%
      }
      else
      {
%>
            <li><a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow+rowCount)+");"%>' title="Next page"><i class="fa fa-arrow-circle-o-right fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Next")%></a></li>
<%
      }
%>
          </ul>
          <ul class="pagination pagination-sm no-margin pull-right">
            <li class="pad">
              <span class="label label-primary">
                <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.Rows")%>
                <%=Integer.toString(startRow)%>-<%=(hasMoreRows?Integer.toString(startRow+rowCount-1):"END")%>
              </span>
            </li>
            <li class="form-inline">
              <div class="input-group input-group-sm">
                <span class="input-group-addon"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.RowsPerPage")%></span>
                <input type="text" class="form-control" name="rowcount" size="5" class="form-control" value='<%=Integer.toString(rowCount)%>'/>
              </div>
            </li>
          </ul>
        </div>

<%
    }
    else
    {
%>
        <div class="callout callout-info">
          <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.PleaseSelectAtLeastOneJob")%></p>
        </div>
<%
    }
  }
  else
  {
%>
        <div class="callout callout-info">
          <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"documentstatus.PleaseSelectaConnection")%></p>
        </div>
<%
  }
%>
      </div>
    </form>
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
</div>
