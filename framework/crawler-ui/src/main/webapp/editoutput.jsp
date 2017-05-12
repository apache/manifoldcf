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
    variableContext.setParameter("target","listoutputs.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }

  // Get the connection manager handle
  IOutputConnectionManager connMgr = OutputConnectionManagerFactory.make(threadContext);
  // Also get the list of available connectors
  IOutputConnectorManager connectorManager = OutputConnectorManagerFactory.make(threadContext);

  IResultSet set = connectorManager.getConnectors();

  // Figure out what the current tab name is.
  String tabName = variableContext.getParameter("tabname");
  if (tabName == null || tabName.length() == 0)
    tabName = Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Name");
  String connectionName = null;
  IOutputConnection connection = (IOutputConnection)threadContext.get("ConnectionObject");
  if (connection == null)
  {
    // We did not go through execute.jsp
    // We might have received an argument specifying the connection name.
    connectionName = variableContext.getParameter("connname");
    // If the connectionname is not null, load the connection description and prepopulate everything with what comes from it.
    if (connectionName != null && connectionName.length() > 0)
    {
      connection = connMgr.load(connectionName);
    }
  }

  // Set up default fields.
  boolean isNew = true;
  String description = "";
  String className = "";
  int maxConnections = 10;
  ConfigParams parameters = new ConfigParams();

  // If there's a connection object, set up all our parameters from it.
  if (connection != null)
  {
    // Set up values
    isNew = connection.getIsNew();
    connectionName = connection.getName();
    description = connection.getDescription();
    className = connection.getClassName();
    parameters = connection.getConfigParams();
    maxConnections = connection.getMaxConnections();
  }
  else
    connectionName = null;

  if (connectionName == null)
    connectionName = "";

  // Initialize tabs array.
  ArrayList tabsArray = new ArrayList();

  // Set up the predefined tabs
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Name"));
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Type"));
  if (className.length() > 0)
    tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Throttling"));

%>

<%
  String heading = null;
  if (description.length() > 0)
  {
    heading = Messages.getBodyString(pageContext.getRequest()
            .getLocale(),"editoutput.EditOutputConnection") + " - " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description);
  }
  else
  {
    heading = Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.EditAnOutputConnection");
  }
%>

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editoutput.ApacheManifoldCFEditOutputConnection")%>',
      '<%=heading%>',
      'outputs'
  );

  // Use this method to repost the form and pick a new tab
  function SelectTab(newtab)
  {
    if (checkForm())
    {
      document.editconnection.tabname.value=newtab;
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
      {
        document.editconnection.action=document.editconnection.action + "#" + anchorValue;
      }
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
        alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editoutput.ConnectionMustHaveAName")%>");
        SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editoutput.Name")%>");
        document.editconnection.connname.focus();
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
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editoutput.TheMaximumNumberOfConnectionsMustBeAValidInteger")%>");
      editconnection.maxconnections.focus();
      return false;
    }
    return true;
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

<section id="script">
  <%
    OutputConnectorFactory.outputConfigurationHeader(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabsArray);
  %>
</section>


<div class="row">
  <div class="col-md-12">
<%
  // Get connector list; need this to decide what to do
  if (set.getRowCount() == 0)
  {
%>
    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.NoOutputConnectorsRegistered")%></p>
    </div>
<%
  }
  else
  {
%>
    <div class="box box-primary">
      <form class="standardform" name="editconnection" action="execute.jsp" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="output"/>
        <input type="hidden" name="tabname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tabName)%>"/>
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
                      alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.tab")%>'
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
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Name")))
    {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.NameColon")%></label>
<%
      // If the connection doesn't exist yet, we are allowed to change the name.
      if (connection == null || connectionName.length() < 1)
      {
%>
                <input type="text" size="32" name="connname" class="form-control" placeholder="Name..." value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>"/>
<%
      }
      else
      {
%>
                <input type="text" class="form-control" disabled value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>"/>
                <input type="hidden" name="connname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>"/>
<%
      }
%>
              </div>
              <div class="form-group">
                <label for="description"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.DescriptionColon")%></label>
                <input type="text" size="50" class="form-control" name="description" id="description" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>"/>
              </div>
            </div>
<%
    }
    else
    {
      // Hiddens for the Name tab
%>
            <input type="hidden" name="connname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionName)%>"/>
            <input type="hidden" name="description" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>"/>
<%
    }


    // "Type" tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Type")))
    {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.ConnectionTypeColon")%></label>
<%
      if (className.length() > 0)
      {
        String value = connectorManager.getDescription(className);
        if (value == null)
        {
%>
                <nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.UNREGISTERED")%> <%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(className)%></nobr>
<%
        }
        else
        {
%>
                <input type="text" class="form-control" disabled value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value)%>"/>
<%
        }
%>
                <input type="hidden" name="classname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(className)%>"/>
<%
      }
      else
      {
        int i = 0;
%>
                <select name="classname" clas="form-control">
<%
        while (i < set.getRowCount())
        {
          IResultRow row = set.getRow(i++);
          String thisClassName = row.getValue("classname").toString();
          String thisDescription = row.getValue("description").toString();
%>
                  <option value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisClassName)%>" <%=className.equals(thisClassName)?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
        }
%>
                </select>
<%
      }
%>
              </div>
            </div>
<%
    }
    else
    {
      // Hiddens for the "Type" tab
%>
              <input type="hidden" name="classname" value="<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(className)%>"/>
<%
    }


    // The "Throttling" tab
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Throttling")))
    {
%>
              <div class="tab-pane active" id="tab_<%=activeTab%>">
                <div class="form-group">
                  <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editoutput.MaxConnectionsColon")%></label>
                  <input type="text" size="6" name="maxconnections" class="form-control" value="<%=Integer.toString(maxConnections)%>"/>
                </div>
              </div>
<%
    }
    else
    {
      // Hiddens for "Throttling" tab
%>
              <input type="hidden" name="maxconnections" value="<%=Integer.toString(maxConnections)%>"/>
<%
    }

    if (className.length() > 0)
      OutputConnectorFactory.outputConfigurationBody(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabName);

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
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.SaveThisOutputConnection")%>" data-toggle="tooltip"><i class="fa fa-save fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.Save")%></a>
<%
    }
    else
    {
      if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editoutput.Type")))
      {
%>
              <a href="#" class="btn btn-primary" onClick="javascript:Continue()"
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.ContinueToNextPage")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.Continue")%></a>
<%
      }
    }
%>
              <a href="#" class="btn btn-primary" onClick="javascript:Cancel()"
                      title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.CancelOutputConnectionEditing")%>" data-toggle="tooltip"><i class="fa fa-times-circle-o fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editoutput.Cancel")%></a>
            </div>
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
  variableContext.setParameter("target","listoutputs.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
