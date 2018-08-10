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

  // Read the parameters.
  String reportConnection = variableContext.getParameter("reportconnection");
  if (reportConnection == null)
    reportConnection = "";
  String[] reportActivities;
  if (variableContext.getParameter("reportactivities_posted") != null)
  {
    reportActivities = variableContext.getParameterValues("reportactivities");
    if (reportActivities == null)
      reportActivities = new String[0];
  }
  else
    reportActivities = null;

  // Get the current time, so we can fill in default values where possible.
  long currentTime = System.currentTimeMillis();

  Long startTime = null;
  Long endTime = null;

  // Get start time, if selected
  String startYear = variableContext.getParameter("reportstartyear");
  String startMonth = variableContext.getParameter("reportstartmonth");
  String startDay = variableContext.getParameter("reportstartday");
  String startHour = variableContext.getParameter("reportstarthour");
  String startMinute = variableContext.getParameter("reportstartminute");

  // Get end time, if selected.
  String endYear = variableContext.getParameter("reportendyear");
  String endMonth = variableContext.getParameter("reportendmonth");
  String endDay = variableContext.getParameter("reportendday");
  String endHour = variableContext.getParameter("reportendhour");
  String endMinute = variableContext.getParameter("reportendminute");

  if (startYear == null && startMonth == null && startDay == null && startHour == null && startMinute == null &&
      endYear == null && endMonth == null && endDay == null && endHour == null && endMinute == null)
  {
    // Nobody has selected a time range yet.  Pick the last hour.
    endTime = null;
    startTime = new Long(currentTime - 1000L * 60L * 60L);
  }
  else
  {
    // Get start time, if selected
    if (startYear == null)
      startYear = "";
    if (startMonth == null)
      startMonth = "";
    if (startDay == null)
      startDay = "";
    if (startHour == null)
      startHour = "";
    if (startMinute == null)
      startMinute = "";

    // Get end time, if selected.
    if (endYear == null)
      endYear = "";
    if (endMonth == null)
      endMonth = "";
    if (endDay == null)
      endDay = "";
    if (endHour == null)
      endHour = "";
    if (endMinute == null)
      endMinute = "";

    if (startYear.length() == 0 || startMonth.length() == 0 || startDay.length() == 0 || startHour.length() == 0 || startMinute.length() == 0)
    {
      // Undetermined start
      startTime = null;
    }
    else
    {
      // Convert the specified times to a long.
      Calendar c = new GregorianCalendar(clientTimezone, pageContext.getRequest().getLocale());
      c.set(Calendar.YEAR,Integer.parseInt(startYear));
      c.set(Calendar.MONTH,Integer.parseInt(startMonth));
      c.set(Calendar.DAY_OF_MONTH,Integer.parseInt(startDay) + 1);
      c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(startHour));
      c.set(Calendar.MINUTE,Integer.parseInt(startMinute));
      startTime = new Long(c.getTimeInMillis());
    }
    if (endYear.length() == 0 || endMonth.length() == 0 || endDay.length() == 0 || endHour.length() == 0 || endMinute.length() == 0)
    {
      // Undetermined end
      endTime = null;
    }
    else
    {
      // Convert the specified times to a long.
      Calendar c = new GregorianCalendar(clientTimezone, pageContext.getRequest().getLocale());
      c.set(Calendar.YEAR,Integer.parseInt(endYear));
      c.set(Calendar.MONTH,Integer.parseInt(endMonth));
      c.set(Calendar.DAY_OF_MONTH,Integer.parseInt(endDay) + 1);
      c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(endHour));
      c.set(Calendar.MINUTE,Integer.parseInt(endMinute));
      endTime = new Long(c.getTimeInMillis());
    }
  }

  // Now, turn the startTime and endTime back into fielded values.  The values will be blank where there is no limit.
  if (startTime == null)
  {
    startYear = "";
    startMonth = "";
    startDay = "";
    startHour = "";
    startMinute = "";
  }
  else
  {
    // Do the conversion
    Calendar c = new GregorianCalendar(clientTimezone, pageContext.getRequest().getLocale());
    c.setTimeInMillis(startTime.longValue());
    startYear = Integer.toString(c.get(Calendar.YEAR));
    startMonth = Integer.toString(c.get(Calendar.MONTH));
    startDay = Integer.toString(c.get(Calendar.DAY_OF_MONTH)-1);
    startHour = Integer.toString(c.get(Calendar.HOUR_OF_DAY));
    startMinute = Integer.toString(c.get(Calendar.MINUTE));
  }

  if (endTime == null)
  {
    endYear = "";
    endMonth = "";
    endDay = "";
    endHour = "";
    endMinute = "";
  }
  else
  {
    // Do the conversion
    Calendar c = new GregorianCalendar(clientTimezone, pageContext.getRequest().getLocale());
    c.setTimeInMillis(endTime.longValue());
    endYear = Integer.toString(c.get(Calendar.YEAR));
    endMonth = Integer.toString(c.get(Calendar.MONTH));
    endDay = Integer.toString(c.get(Calendar.DAY_OF_MONTH)-1);
    endHour = Integer.toString(c.get(Calendar.HOUR_OF_DAY));
    endMinute = Integer.toString(c.get(Calendar.MINUTE));
  }

  // Get the entity match string.
  String entityMatch = variableContext.getParameter("reportentitymatch");
  if (entityMatch == null)
    entityMatch = "";

  // Get the resultcode match string.
  String resultCodeMatch = variableContext.getParameter("reportresultcodematch");
  if (resultCodeMatch == null)
    resultCodeMatch = "";

  String reportBucketDesc = variableContext.getParameter("reportbucketdesc");
  if (reportBucketDesc == null)
    reportBucketDesc = "(.*)";

  String reportResultDesc = variableContext.getParameter("reportresultdesc");
  if (reportResultDesc == null)
    reportResultDesc = "(.*)";

  // Read the other data we need.
  IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
  IRepositoryConnection[] connList = connMgr.getAllConnections();

  // Query the legal list of activities.  This will depend on the connection has been chosen, if any.
  Map selectedActivities = null;
  String[] activityList = null;
  if (reportConnection.length() > 0)
  {
    activityList = org.apache.manifoldcf.crawler.system.ManifoldCF.getActivitiesList(threadContext,reportConnection);
    if (activityList == null)
      reportConnection = "";
    else
    {
      selectedActivities = new HashMap();
      String[] activitiesToNote;
      int j = 0;
      if (reportActivities == null)
        activitiesToNote = activityList;
      else
        activitiesToNote = reportActivities;

      while (j < activitiesToNote.length)
      {
        String activity = activitiesToNote[j++];
        selectedActivities.put(activity,activity);
      }
    }
  }


%>

<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "resultreport.ApacheManifoldCFActivityResultReport")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "resultreport.ActivityResultReport")%>',
      'historyreports'
  );

  function Go()
  {
    if (!isInteger(report.rowcount.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.EnterALegalNumberForRowsPerPage")%>");
      report.rowcount.focus();
      return;
    }

    if (report.reportbucketdesc.value == "")
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescriptionCannotBeEmpty")%>");
      report.reportbucketdesc.focus();
      return;
    }

    if (report.reportresultdesc.value == "")
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultClassDescriptionCannotBeEmpty")%>");
      report.reportresultdesc.focus();
      return;
    }

    if (!isRegularExpression(report.reportbucketdesc.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescriptionMustBeAValidRegularExpression")%>");
      report.reportbucketdesc.focus();
      return;
    }

    if (!isRegularExpression(report.reportresultdesc.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultClassDescriptionMustBeAValidRegularExpression")%>");
      report.reportresultdesc.focus();
      return;
    }
    if (report.reportbucketdesc.value.indexOf("(") == -1 || report.reportbucketdesc.value.indexOf(")") == -1)
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescriptionMustDelimitAClassWithParentheses")%>");
      report.reportbucketdesc.focus();
      return;
    }
    if (report.reportresultdesc.value.indexOf("(") == -1 || report.reportresultdesc.value.indexOf(")") == -1)
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultClassDescriptionMustDelimitAClassWithParentheses")%>");
      report.reportresultdesc.focus();
      return;
    }

    if (!isRegularExpression(report.reportentitymatch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.EntityMatchMustBeAValidRegularExpression")%>");
      report.reportentitymatch.focus();
      return;
    }
    if (!isRegularExpression(report.reportresultcodematch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultCodeMatchMustBeAValidRegularExpression")%>");
      report.reportresultcodematch.focus();
      return;
    }

    document.report.op.value="Report";
    document.report.action=document.report.action + "#MainButton";
    $.ManifoldCF.submit(document.report);
  }

  function Continue()
  {
    if (!isRegularExpression(report.reportentitymatch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.EntityMatchMustBeAValidRegularExpression")%>");
      report.reportentitymatch.focus();
      return;
    }
    if (!isRegularExpression(report.reportresultcodematch.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultCodeMatchMustBeAValidRegularExpression")%>");
      report.reportresultcodematch.focus();
      return;
    }
    if (!isRegularExpression(report.reportbucketdesc.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescriptionMustBeAValidRegularExpression")%>");
      report.reportbucketdesc.focus();
      return;
    }
    if (!isRegularExpression(report.reportresultdesc.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultClassDescriptionMustBeAValidRegularExpression")%>");
      report.reportresultdesc.focus();
      return;
    }
    if (report.reportbucketdesc.value.indexOf("(") == -1 || report.reportbucketdesc.value.indexOf(")") == -1)
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescriptionMustDelimitAClassWithParentheses")%>");
      report.reportbucketdesc.focus();
      return;
    }
    if (report.reportresultdesc.value.indexOf("(") == -1 || report.reportresultdesc.value.indexOf(")") == -1)
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"resultreport.ResultClassDescriptionMustDelimitAClassWithParentheses")%>");
      report.reportresultdesc.focus();
      return;
    }

    document.report.op.value="Continue";
    document.report.action=document.report.action + "#MainButton";
    $.ManifoldCF.submit(document.report);
  }

  function ColumnClick(colname)
  {
    document.report.clickcolumn.value=colname;
    Go();
  }

  function SetPosition(amt)
  {
    if (amt < 0)
      amt=0;
    document.report.startrow.value=amt;
    Go();
  }

  function isRegularExpression(value)
  {
    try
    {
      var foo="teststring";
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


<div class="row">
  <div class="col-md-12">
    <form class="standardform" name="report" action="execute.jsp" method="POST">
      <input type="hidden" name="op" value="Continue"/>
      <input type="hidden" name="type" value="resultreport"/>

      <div class="box box-primary">
        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th colspan="1"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Connection")%></th>
              <td colspan="1">
                <select name="reportconnection" class="form-control">
                  <option <%=(reportConnection.length()==0)?"selected=\"selected\"":""%> value="">-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
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
                  <option <%=(thisConnectionName.equals(reportConnection))?"selected=\"selected\"":""%> value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisConnectionName)%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
  }
%>
                </select>
              </td>
<%
  if (reportConnection.length() > 0)
  {
%>
              <th colspan="1"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Activities")%></th>
              <td colspan="1">
                <input type="hidden" name="reportactivities_posted" value="true"/>
                <select multiple="true" class="selectpicker" name="reportactivities">
<%
    i = 0;
    while (i < activityList.length)
    {
      String activity = activityList[i++];
%>
                  <option <%=((selectedActivities.get(activity) == null)?"":"selected=\"selected\"")%> value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(activity)%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(activity)%></option>
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
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.StartTime")%></th>
              <td colspan="3">
                <div class="input-group">
                  <select class="schedulepulldown" name='reportstarthour'>
                    <option value="" <%=(startHour.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
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
                    <option value='<%=k%>' <%=(startHour.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(q)+" "+ampm%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label">:</span>
                  <select class="schedulepulldown" name='reportstartminute'>
                    <option value="" <%=(startMinute.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
<%
  k = 0;
  while (k < 60)
  {
%>
                    <option value='<%=k%>' <%=(startMinute.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(k)%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.on")%></span>
                  <select class="schedulepulldown" name='reportstartmonth'>
                    <option value="" <%=(startMonth.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
                    <option value="0" <%=(startMonth.equals("0"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.January")%></option>
                    <option value="1" <%=(startMonth.equals("1"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.February")%></option>
                    <option value="2" <%=(startMonth.equals("2"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.March")%></option>
                    <option value="3" <%=(startMonth.equals("3"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.April")%></option>
                    <option value="4" <%=(startMonth.equals("4"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.May")%></option>
                    <option value="5" <%=(startMonth.equals("5"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.June")%></option>
                    <option value="6" <%=(startMonth.equals("6"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.July")%></option>
                    <option value="7" <%=(startMonth.equals("7"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.August")%></option>
                    <option value="8" <%=(startMonth.equals("8"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.September")%></option>
                    <option value="9" <%=(startMonth.equals("9"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.October")%></option>
                    <option value="10" <%=(startMonth.equals("10"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.November")%></option>
                    <option value="11" <%=(startMonth.equals("11"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.December")%></option>
                  </select>
                  <span class="label">-</span>
                  <select class="schedulepulldown" name='reportstartday'>
                    <option value="" <%=(startDay.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
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
                    <option value='<%=Integer.toString(k)%>' <%=(startDay.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label">,</span>
                  <select class="schedulepulldown" name='reportstartyear'>
                    <option value="" <%=(startYear.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
<% 
  for(int year=2005; year <= java.util.Calendar.getInstance(clientTimezone, pageContext.getRequest().getLocale()).get(java.util.Calendar.YEAR); year++)
  {
    String selected = (startYear.equals(""+year))?"selected=\"selected\"":""; 
%>
                    <option value="<%= year %>" <%= selected %>><%= year %></option>
<% 
  }
%>
                  </select>
                </div>
              </td>
            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.EndTime")%></th>
              <td colspan="3">
                <div class="input-group">
                  <select class="schedulepulldown" name='reportendhour'>
                    <option value="" <%=(endHour.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
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
                    <option value='<%=k%>' <%=(endHour.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(q)+" "+ampm%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label">:</span>
                  <select class="schedulepulldown" name='reportendminute'>
                    <option value="" <%=(endMinute.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
<%
  k = 0;
  while (k < 60)
  {
%>
                    <option value='<%=k%>' <%=(endMinute.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(k)%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.on")%></span>
                  <select class="schedulepulldown" name='reportendmonth'>
                    <option value="" <%=(endMonth.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
                    <option value="0" <%=(endMonth.equals("0"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.January")%></option>
                    <option value="1" <%=(endMonth.equals("1"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.February")%></option>
                    <option value="2" <%=(endMonth.equals("2"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.March")%></option>
                    <option value="3" <%=(endMonth.equals("3"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.April")%></option>
                    <option value="4" <%=(endMonth.equals("4"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.May")%></option>
                    <option value="5" <%=(endMonth.equals("5"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.June")%></option>
                    <option value="6" <%=(endMonth.equals("6"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.July")%></option>
                    <option value="7" <%=(endMonth.equals("7"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.August")%></option>
                    <option value="8" <%=(endMonth.equals("8"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.September")%></option>
                    <option value="9" <%=(endMonth.equals("9"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.October")%></option>
                    <option value="10" <%=(endMonth.equals("10"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.November")%></option>
                    <option value="11" <%=(endMonth.equals("11"))?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.December")%></option>
                  </select>
                  <span class="label">-</span>
                  <select class="schedulepulldown" name='reportendday'>
                    <option value="" <%=(endDay.length()==0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
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
                    <option value='<%=Integer.toString(k)%>' <%=(endDay.equals(Integer.toString(k)))?"selected=\"selected\"":""%>><%=Integer.toString(k+1)+suffix%></option>
<%
    k++;
  }
%>
                  </select>
                  <span class="label">,</span>
                  <select class="schedulepulldown" name='reportendyear'>
                    <option value="" <%=(endYear.length() == 0)?"selected=\"selected\"":""%>>-- <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.NotSpecified")%>--</option>
<%
  for(int year=2005; year <= java.util.Calendar.getInstance(clientTimezone, pageContext.getRequest().getLocale()).get(java.util.Calendar.YEAR); year++)
  {
    String selected = (startYear.equals("" + year))?"selected=\"selected\"":"";
%>
                    <option value="<%= year %>" <%= selected %>><%= year %></option>
<%
  }
%>
                  </select>
                </div>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.EntityMatch")%></nobr></td>
              <td><input type="text" class="form-control" name="reportentitymatch" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(entityMatch)%>'/></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.ResultCodeMatch")%></nobr></td>
              <td><input type="text" class="form-control" name="reportresultcodematch" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(resultCodeMatch)%>'/></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClassDescription")%></nobr></td>
              <td><input type="text" class="form-control" name="reportbucketdesc" size="20" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(reportBucketDesc)%>'/></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.ResultCodeClassDescription")%></nobr></td>
              <td><input type="text" class="form-control" name="reportresultdesc" size="20" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(reportResultDesc)%>'/></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
<%
  if (reportConnection.length() > 0)
  {
%>
            <a href="#" name="MainButton" class="btn btn-primary" role="button" onClick="javascript:Go()" 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.ExecuteThisQuery")%>"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.Go")%></a>
<%
  }
  else
  {
%>
            <a href="#" name="MainButton" class="btn btn-primary" role="button" onClick="javascript:Continue()" 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.Continue")%>"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.Continue")%></a>
            <%
              }
            %>
          </div>
        </div>
      </div>
<%
  if (reportConnection.length() > 0)
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

    String[] ourActivities = new String[selectedActivities.size()];
    Iterator iter = selectedActivities.keySet().iterator();
    int zz = 0;
    while (iter.hasNext())
    {
      ourActivities[zz++] = (String)iter.next();
    }

    RegExpCriteria entityMatchObject = null;
    if (entityMatch.length() > 0)
      entityMatchObject = new RegExpCriteria(entityMatch,true);
    RegExpCriteria resultCodeMatchObject = null;
    if (resultCodeMatch.length() > 0)
      resultCodeMatchObject = new RegExpCriteria(resultCodeMatch,true);
    FilterCriteria criteria = new FilterCriteria(ourActivities,startTime,endTime,entityMatchObject,resultCodeMatchObject);

    BucketDescription idBucket = new BucketDescription(reportBucketDesc,false);
    BucketDescription resultBucket = new BucketDescription(reportResultDesc,false);

    IResultSet set = connMgr.genHistoryResultCodes(reportConnection,criteria,sortOrder,resultBucket,idBucket,startRow,rowCount+1);

%>
      <input type="hidden" name="clickcolumn" value=""/>
      <input type="hidden" name="startrow" value='<%=Integer.toString(startRow)%>'/>
      <input type="hidden" name="sortorder" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sortOrder.toString())%>'/>

      <div class="box box-primary">
        <div class="box-body table-responsive">
          <table class="table table-bordered">
            <tr>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("idbucket");'><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.IdentifierClass")%></nobr></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("resultcodebucket");'><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.ResultClass")%></nobr></a></th>
              <th><a href="javascript:void(0);" onclick='javascript:ColumnClick("eventcount");'><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.EventCount")%></nobr></a></th>
            </tr>
<%
    zz = 0;
    boolean hasMoreRows = (set.getRowCount() > rowCount);
    int iterCount = hasMoreRows?rowCount:set.getRowCount();
    while (zz < iterCount)
    {
      IResultRow row = set.getRow(zz);
      Object idBucketObject = row.getValue("idbucket");
      String idBucketString;
      if (idBucketObject == null)
        idBucketString = "";
      else
        idBucketString = idBucketObject.toString();
      Object resultCodeBucketObject = row.getValue("resultcodebucket");
      String resultCodeBucketString;
      if (resultCodeBucketObject == null)
        resultCodeBucketString = "";
      else
        resultCodeBucketString = resultCodeBucketObject.toString();
%>
            <tr <%="class=\""+((zz%2==0)?"evendatarow":"odddatarow")+"\""%>>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(idBucketString)%></nobr></td>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(resultCodeBucketString)%></nobr></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(row.getValue("eventcount").toString())%></td>
            </tr>
<%
      zz++;
    }
%>
          </table>
        </div>
        <div class="box-footer clearfix">
          <ul class="pagination pagination-sm no-margin pull-left">
<%
    if (startRow == 0)
    {
%>
            <li><a href="#"><i class="fa fa-arrow-circle-o-left fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Previous")%></a></li>
<%
    }
    else
    {
%>
            <li><a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow-rowCount)+");"%>' 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.PreviousPage")%>" data-toggle="tooltip"><i class="fa fa-arrow-circle-o-left fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Previous")%></a></li>
<%
    }
    if (hasMoreRows == false)
    {
%>
            <li><a href="#"><i class="fa fa-arrow-circle-o-right fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Next")%></a></li>
<%
    }
    else
    {
%>
            <li><a href="javascript:void(0);" onclick='<%="javascript:SetPosition("+Integer.toString(startRow+rowCount)+");"%>' 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"resultreport.NextPage")%>" data-toggle="tooltip"><i class="fa fa-arrow-circle-o-right fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Next")%></a></li>
<%
    }
%>
          </ul>
          <ul class="pagination pagination-sm no-margin pull-right">
            <li class="pad">
              <span class="label label-primary"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.Rows")%><%=Integer.toString(startRow)%>-<%=(hasMoreRows?Integer.toString(startRow+rowCount-1):"END")%></span>
            </li>
            <li class="form-inline">
              <div class="input-group input-group-sm">
                <span class="input-group-addon"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.RowsPerPage")%></span><input type="text" class="form-control" name="rowcount" size="5" value='<%=Integer.toString(rowCount)%>'/>
              </div>
            </li>
          </ul>
        </div>
      </div>
<%
  }
  else
  {
%>
      <div class="callout callout-info"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"resultreport.PleaseSelectAConnection")%></div>
<%
  }
%>
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
