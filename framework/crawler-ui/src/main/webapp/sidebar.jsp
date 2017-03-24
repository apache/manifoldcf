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

<section class="sidebar">
  <ul class="sidebar-menu">
    <li class="header">MAIN NAVIGATION</li>
    <li class="outputs treeview">
      <a href="#">
        <i class="fa fa-outdent" aria-hidden="true"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Outputs")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="listtransformations.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listtransformationconnections")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListTransformationConnections")%>
          </a>
        </li>
        <li>
          <a class="link" href="listoutputs.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listoutputconnections")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListOutputConnections")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="authorities treeview">
      <a href="#">
        <i class="fa fa-user" aria-hidden="true"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Authorities")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="listgroups.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listauthoritygroups")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListAuthorityGroups")%>
          </a>
        </li>
        <li>
          <a class="link" href="listmappers.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listusermappings")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListUserMappings")%>
          </a>
        </li>
        <li>
          <a class="link" href="listauthorities.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listauthorities")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListAuthorityConnections")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="repositories treeview">
      <a href="#">
        <i class="fa fa-download" aria-hidden="true"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Repositories")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="listconnections.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listrepositoryconnections")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListRepositoryConnections")%>
          </a>
        </li>
        <li>
          <a class="link" href="listnotifications.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listnotificationconnections")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListNotificationConnections")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="jobs treeview">
      <a href="#">
        <i class="fa fa-laptop"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Jobs")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="listjobs.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Listjobs")%>">
            <i class="fa fa-list" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ListAllJobs")%>
          </a>
        </li>
        <li>
          <a class="link" href="showjobstatus.jsp"
                  alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Managejobs")%>">
            <i class="fa fa-list" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.StatusAndJobManagement")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="statusreports treeview">
      <a href="#">
        <i class="fa fa-table"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.StatusReports")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="documentstatus.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Documentstatus")%>">
            <i class="fa fa-file-text"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.DocumentStatus")%>
          </a>
        </li>
        <li>
          <a class="link" href="queuestatus.jsp"
                  alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Queuestatus")%>">
            <i class="fa fa-list" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.QueueStatus")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="historyreports treeview">
      <a href="#">
        <i class="fa fa-history"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.HistoryReports")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a class="link" href="simplereport.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Simplehistory")%>">
            <i class="fa fa-history"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.SimpleHistory")%>
          </a>
        </li>
        <li>
          <a class="link" href="maxactivityreport.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Maximumactivity")%>">
            <i class="fa fa-circle-o"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.MaximumActivity")%>
          </a>
        </li>
        <li>
          <a class="link" href="maxbandwidthreport.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Maximumbandwidth")%>">
            <i class="fa fa-bar-chart"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.MaximumBandwidth")%>
          </a>
        </li>
        <li>
          <a class="link" href="resultreport.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Resulthistogram")%>">
            <i class="fa fa-area-chart"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.ResultHistogram")%>
          </a>
        </li>
      </ul>
    </li>
    <li class="miscellaneous treeview">
      <a href="#">
        <i class="fa fa-info" aria-hidden="true"></i>
        <span><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Miscellaneous")%></span>
        <i class="fa fa-angle-left pull-right"></i>
      </a>
      <ul class="treeview-menu">
        <li>
          <a href="<%="http://manifoldcf.apache.org/"+Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Locale")+"/release-documentation.html"%>" target="_blank" 
                  alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.Help")%>"><i class="fa fa-book"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.Help")%></a>
        </li>
        <li>
          <a href="logout.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"navigation.LogOut")%>">
            <i class="fa fa-sign-out" aria-hidden="true"></i>
            <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"navigation.LogOut")%>
          </a>
        </li>
      </ul>
    </li>
  </ul>
</section>
