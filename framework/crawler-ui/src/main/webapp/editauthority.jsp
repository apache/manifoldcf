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
// a connection or beginning the process of editing an existing connection, or it is called via redirection from execute.jsp, in which case
// the connection object being edited will be placed in the thread context under the name "ConnectionObject".
try
{
  // Check if authorized
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
  {
    variableContext.setParameter("target","listauthorities.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }

  // Get the domain manager handle
  IAuthorizationDomainManager domainMgr = AuthorizationDomainManagerFactory.make(threadContext);
  // Also get the list of available connectors
  IAuthorityConnectorManager connectorManager = AuthorityConnectorManagerFactory.make(threadContext);
  // Connections
  IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(threadContext);
  // Get the mapping connection manager
  IMappingConnectionManager mappingConnMgr = MappingConnectionManagerFactory.make(threadContext);
  // Get the group manager
  IAuthorityGroupManager authGroupManager = AuthorityGroupManagerFactory.make(threadContext);

  // Get connectors, since this will be needed to determine what to display.
  IResultSet set = connectorManager.getConnectors();
  // Same for authority groups
  IAuthorityGroup[] set2 = authGroupManager.getAllGroups();
  // Get all mapping connections
  IMappingConnection[] mappingConnections = mappingConnMgr.getAllConnections();
  // Get set of domains
  IResultSet domainSet = domainMgr.getDomains();

  // Figure out what the current tab name is.
  String tabName = variableContext.getParameter("tabname");
  if (tabName == null || tabName.length() == 0)
    tabName = Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Name");

  String connectionName = null;
  IAuthorityConnection connection = (IAuthorityConnection)threadContext.get("ConnectionObject");
  if (connection == null)
  {
    // We did not go through execute.jsp
    // We might have received an argument specifying the connection name.
    connectionName = variableContext.getParameter("connname");
    // If the connectionname is not null, load the connection description and prepopulate everything with what comes from it.
    if (connectionName != null && connectionName.length() > 0)
    {
      connection = connectionManager.load(connectionName);
    }
  }

  // Setup default fields
  boolean isNew = true;
  String description = "";
  String className = "";
  int maxConnections = 10;
  ConfigParams parameters = new ConfigParams();
  String prereq = null;
  String authDomain = "";
  String groupName = "";

  if (connection != null)
  {
    // Set up values
    isNew = connection.getIsNew();
    connectionName = connection.getName();
    description = connection.getDescription();
    className = connection.getClassName();
    parameters = connection.getConfigParams();
    maxConnections = connection.getMaxConnections();
    prereq = connection.getPrerequisiteMapping();
    authDomain = connection.getAuthDomain();
    if (authDomain == null)
      authDomain = "";
    groupName = connection.getAuthGroup();
    if (groupName == null)
      groupName = "";
  }
  else
    connectionName = null;

  if (connectionName == null)
    connectionName = "";

  // Initialize tabs array
  ArrayList tabsArray = new ArrayList();

  // Set up the predefined tabs
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Name"));
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Type"));
  if (className.length() > 0)
  {
    tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Prerequisites"));
    tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Throttling"));
  }

%>

<script type="text/javascript">
    <!--

<%
  String title = null;
  if (description.length() > 0)
  {
    title = Messages.getBodyString(pageContext.getRequest().getLocale(), "editauthority.EditAuthority") + " - " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description);
  }
  else
  {
    title = Messages.getBodyString(pageContext.getRequest().getLocale(), "editauthority.EditAnAuthority");
  }
%>

    $.ManifoldCF.setTitle('<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.ApacheManifoldCFEditAuthority")%>','<%=title%>','authorities');

    // Use this method to repost the form and pick a new tab
    function SelectTab(newtab)
    {
        if (checkForm())
        {
            document.editconnection.tabname.value = newtab;
            $.ManifoldCF.submit(document.editconnection);
        }
    }

    // Use this method to repost the form,
    // and set the anchor request.
    function postFormSetAnchor(anchorValue)
    {
        if (checkForm())
        {
            if (anchorValue != "")
                document.editconnection.action = document.editconnection.action + "#" + anchorValue;
            $.ManifoldCF.submit(document.editconnection);
        }
    }

    // Use this method to repost the form
    function postForm()
    {
        if (checkForm())
        {
            $.ManifoldCF.submit(document.editconnection);
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
            if (editconnection.connname.value == "")
            {
                alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editauthority.ConnectionMustHaveAName")%>");
                SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editauthority.Name")%>");
                document.editconnection.connname.focus();
                return;
            }
            if (editconnection.authoritygroup.value == "")
            {
                alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editauthority.ConnectionMustHaveAGroup")%>");
                SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editauthority.Type")%>");
                document.editconnection.authoritygroup.focus();
                return;
            }
            if (window.checkConfigForSave)
            {
                if (!checkConfigForSave())
                    return;
            }
            document.editconnection.op.value="Save";
            $.ManifoldCF.submit(document.editconnection);
        }
    }

    function Continue()
    {
        document.editconnection.op.value="Continue";
        postForm();
    }

    function Cancel()
    {
        document.editconnection.op.value="Cancel";
        $.ManifoldCF.submit(document.editconnection);
    }

    function checkForm()
    {
        if (!checkConnectionCount())
            return false;
        if (window.checkConfig)
            return checkConfig();
        return true;
    }

    function checkConnectionCount()
    {
        if (!isInteger(editconnection.maxconnections.value))
        {
            alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editauthority.TheMaximumNumberOfConnectionsMustBeAValidInteger")%>");
            editconnection.maxconnections.focus();
            return false;
        }
        return true;
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

    //-->
</script>
<%
  AuthorityConnectorFactory.outputConfigurationHeader(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabsArray);
%>

<div class="row">
  <div class="col-md-12">
<%
  if (set2.length == 0)
  {
%>
    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.NoAuthorityGroupsDefinedCreateOneFirst")%></p>
    </div>
<%
  }
  else if (set.getRowCount() == 0)
  {
%>
    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.NoAuthorityConnectorsRegistered")%></p>
    </div>
<%
  }
  else
  {
%>
    <div class="box box-primary">
      <form class="standardform" name="editconnection" action="execute.jsp" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="authority"/>
        <input type="hidden" name="tabname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tabName)%>'/>
        <input type="hidden" name="isnewconnection" value='<%=(isNew?"true":"false")%>'/>

        <div class="box-header">
          <ul class="nav nav-tabs" role="tablist">
<%
    int tabNum = 0;
    int activeTab = 0;
    while (tabNum < tabsArray.size())
    {
      String tab = (String)tabsArray.get(tabNum++);
      if (tab.equals(tabName))
      {
%>
            <li class="active"><a href="#tab_<%=tabNum%>"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a></li>
<%
      }
      else
      {
%>
            <li>
              <a href="#tab_<%=tabNum%>"
                 alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.tab")%>'
                 onclick='<%="javascript:SelectTab(\""+tab+"\");return false;"%>'><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a>
            </li>
<%
      }
    }
%>
          </ul>
        </div>
        <div class="box-body">
          <div class="tab-content">
<%

    // Name tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Name")))
    {
%>
          <div class="tab-pane active" id="tab_<%=activeTab%>">
            <div class="form-group">
              <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.NameColon")%></label>
<%
      // If the connection doesn't exist yet, we are allowed to change the name.
      if (isNew)
      {
%>
              <input type="text" size="32" name="connname" class="form-control" placeholder="Name..." value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
      }
      else
      {
%>
              <input type="text" size="32" class="form-control" disabled value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
              <input type="hidden" name="connname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
      }
%>
            </div>
            <div class="form-group">
              <label for="description"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.DescriptionColon")%> </label>
              <input type="text" size="50" class="form-control" name="description" id="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
            </div>
          </div>
<%
    }
    else
    {
    // Hiddens for the Name tab
%>
          <input type="hidden" name="connname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
          <input type="hidden" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
<%
    }


    // "Type" tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Type")))
    {
%>
          <div class="tab-pane active" id="tab_<%=activeTab%>">
            <div class="form-group">
              <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.ConnectionTypeColon")%></label>

<%
      if (className.length() > 0)
      {
        String value = connectorManager.getDescription(className);
        if (value == null)
        {
%>
              <nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editauthority.UNREGISTERED")%> <%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(className)%></nobr>
<%
        }
        else
        {
%>
              <input type="text" class="form-control" disabled value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value)%>'/>
<%
        }
%>
              <input type="hidden" name="classname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(className)%>'/>
<%
      }
      else
      {
        int i = 0;
%>
              <select name="classname" class="form-control">
<%
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          String thisClassName = row.getValue("classname").toString();
          String thisDescription = row.getValue("description").toString();
%>
                <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisClassName)%>' <%=className.equals(thisClassName)?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
        }
%>
              </select>
<%
      }
%>
            </div>
            <div class="form-group">
              <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.AuthorityGroupColon")%></label>
              <select name="authoritygroup" class="form-control">
                <option value=""><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.SelectAGroup")%></option>
<%
      for (int i = 0; i < set2.length; i++)
      {
        IAuthorityGroup row = set2[i];
        String thisAuthorityName = row.getName();
        String thisDescription = row.getDescription();
        if (thisDescription == null || thisDescription.length() == 0)
          thisDescription = thisAuthorityName;
%>
                <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisAuthorityName)%>'<%=(groupName.equals(thisAuthorityName))?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
      }
%>
              </select>
            </div>
            <div class="form-group">
              <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.AuthorizationDomainColon")%></label>
              <select name="authdomain" class="form-control">
                <option value="" <%=(authDomain == null || authDomain.length() == 0)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.DefaultDomainNone")%></option>
<%
      for (int i = 0; i < domainSet.getRowCount(); i++)
      {
        IResultRow row = domainSet.getRow(i);
        String domainName = (String)row.getValue("domainname");
        String thisDescription = (String)row.getValue("description");
        if (thisDescription == null || thisDescription.length() == 0)
          thisDescription = domainName;
%>
                <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(domainName)%>'<%=(authDomain!=null && domainName.equals(authDomain))?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
      }
%>
              </select>
            </div>
          </div>
<%
    }
    else
    {
    // Hiddens for the "Type" tab
%>
          <input type="hidden" name="classname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(className)%>'/>
          <input type="hidden" name="authdomain" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(authDomain)%>'/>
          <input type="hidden" name="authoritygroup" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(groupName)%>'/>
<%
    }

    // The "Prerequisites" tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Prerequisites")))
    {
%>
          <div class="tab-pane active" id="tab_<%=activeTab%>">
            <div class="form-group">
              <strong><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.PrerequisiteUserMappingColon")%></strong>

              <div class="radio">
                <label>
                  <input type="hidden" name="prerequisites_present" value="true"/>
<%
      if (prereq == null)
      {
%>
                  <input type="radio" name="prerequisites" value="" checked="true"/>&nbsp;<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.NoPrerequisites")%><br/>
<%
      }
      else
      {
%>
                  <input type="radio" name="prerequisites" value=""/>&nbsp;<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.NoPrerequisites")%><br/>
<%
      }

      for (IMappingConnection mappingConnection : mappingConnections)
      {
        String mappingName = mappingConnection.getName();
        String mappingDescription = mappingName;
        if (mappingConnection.getDescription() != null && mappingConnection.getDescription().length() > 0)
          mappingDescription += " (" + mappingConnection.getDescription()+")";
        if (prereq != null && prereq.equals(mappingName))
        {
%>
                  <input type="radio" name="prerequisites" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mappingName)%>' checked="true"/>&nbsp;<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mappingDescription)%><br/>
<%
        }
        else
        {
%>
                  <input type="radio" name="prerequisites" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mappingName)%>'/>&nbsp;<%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(mappingDescription)%><br/>
<%
        }
      }
%>
                </label>
              </div>
            </div>
          </div>
<%
    }
    else
    {
    // Hiddens for Prerequisites tab
%>
          <input type="hidden" name="prerequisites_present" value="true"/>
<%
      if (prereq != null)
      {
%>
          <input type="hidden" name="prerequisites" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(prereq)%>'/>
<%
      }
    }

    // The "Throttling" tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Throttling")))
    {
%>
          <div class="tab-pane active" id="tab_<%=activeTab%>">
            <div class="form-group">
              <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editauthority.MaxConnectionsColon")%></label>
              <input type="text" size="6" name="maxconnections" class="form-control" value='<%=Integer.toString(maxConnections)%>'/>
            </div>
          </div>
<%
    }
    else
    {
    // Hiddens for "Throttling" tab
%>
          <input type="hidden" name="maxconnections" value='<%=Integer.toString(maxConnections)%>'/>
<%
    }

    if (className.length() > 0)
      AuthorityConnectorFactory.outputConfigurationBody(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabName);
%>
          </div>
        </div>

        <div class="box-footer clearfix">
          <div class="btn-group">
<%
    if (className.length() > 0)
    {
%>
            <a href="#" class="btn btn-primary" onClick="javascript:Save()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.SaveThisAuthorityConnection")%>" data-toggle="tooltip"><i class="fa fa-save fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.Save")%></a>
<%
    }
    else
    {
      if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editauthority.Type")))
      {
%>
            <a href="#" class="btn btn-primary" onClick="javascript:Continue()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.ContinueToNextPage")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.Continue")%></a>
<%
      }
    }
%>
            <a href="#" class="btn btn-primary" onClick="javascript:Cancel()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.CancelAuthorityEditing")%>" data-toggle="tooltip"><i class="fa fa-times-circle-o fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editauthority.Cancel")%></a>


          </div>
        </div>
      </form>
<%
  }
}
catch (ManifoldCFException e)
{
  e.printStackTrace();
  variableContext.setParameter("text",e.getMessage());
  variableContext.setParameter("target","listauthorities.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
    </div>
  </div>
</div>
