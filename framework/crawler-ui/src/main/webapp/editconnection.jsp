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
    variableContext.setParameter("target","listconnections.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }
  // Get the connection manager handle
  IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
  // Also get the list of available connectors
  IConnectorManager connectorManager = ConnectorManagerFactory.make(threadContext);
  IAuthorityGroupManager authGroupManager = AuthorityGroupManagerFactory.make(threadContext);

  IAuthorityGroup[] set2 = authGroupManager.getAllGroups();
  IResultSet set = connectorManager.getConnectors();

  // Figure out what the current tab name is.
  String tabName = variableContext.getParameter("tabname");
  if (tabName == null || tabName.length() == 0)
    tabName = Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Name");

  String connectionName = null;
  IRepositoryConnection connection = (IRepositoryConnection)threadContext.get("ConnectionObject");
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
  String authorityName = null;
  int maxConnections = 10;
  // Fetches per minute
  ArrayList throttles = new ArrayList();
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
    authorityName = connection.getACLAuthority();
    maxConnections = connection.getMaxConnections();
    String[] throttlesX = connection.getThrottles();
    int j = 0;
    while (j < throttlesX.length)
    {
      String throttleRegexp = throttlesX[j++];
      Map map = new HashMap();
      map.put("regexp",throttleRegexp);
      map.put("description",connection.getThrottleDescription(throttleRegexp));
      map.put("value",new Long((long)(((double)connection.getThrottleValue(throttleRegexp) * (double)60000.0) + 0.5)));
      throttles.add(map);
    }
  }
  else
    connectionName = null;

  if (connectionName == null)
    connectionName = "";

  // Initialize tabs array.
  ArrayList tabsArray = new ArrayList();

  // Set up the predefined tabs
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Name"));
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Type"));
  if (className.length() > 0)
    tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Throttling"));

%>

<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editconnection.ApacheManifoldCFEditConnection")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editconnection.EditRepositoryConnection")%>',
      'repositories'
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
        document.editconnection.action=document.editconnection.action + "#" + anchorValue;
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
        alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editconnection.ConnectionMustHaveAName")%>");
        SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editconnection.Name")%>");
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

  function DeleteThrottle(i)
  {
    document.editconnection.throttleop.value="Delete";
    document.editconnection.throttlenumber.value=i;
    postForm();
  }

  function AddThrottle()
  {
    if (!isInteger(editconnection.throttlevalue.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editconnection.ThrottleRateMustBeAnInteger")%>");
      document.editconnection.throttlevalue.focus();
      return;
    }
    if (!isRegularExpression(editconnection.throttle.value))
    {
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editconnection.TheThrottleExpressionMustBeAValidRegularExpression")%>");
      editconnection.throttle.focus();
      return;
    }
    document.editconnection.throttleop.value="Add";
    postForm();
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
      alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editconnection.TheMaximumNumberOfConnectionsMustBeAValidInteger")%>");
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
<%
  RepositoryConnectorFactory.outputConfigurationHeader(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabsArray);
%>
<div class="row">
  <div class="col-md-12">
<%
  // Get connector list; need this to decide what to do
  if (set.getRowCount() == 0)
  {
%>

    <div class="callout callout-warning">
      <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.NoRepositoryConnectorsRegistered")%></p>
    </div>
<%
  }
  else
  {
%>
    <div class="box box-primary">
      <form class="standardform" name="editconnection" action="execute.jsp" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="connection"/>
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
            <li class="active">
              <a href="#tab_<%=tabNum%>"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tab)%></a>
            </li>
<%
      }
      else
      {
%>
            <li>
              <a href="#tab_<%=tabNum%>"
                      alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.tab")%>'
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
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Name")))
    {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.NameColon")%></label>
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
                <label for="description"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.DescriptionColon")%></label>
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
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Type")))
    {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.ConnectionTypeColon")%></label>
<%
      if (className.length() > 0)
      {
        String value = connectorManager.getDescription(className);
        if (value == null)
        {
%>
                <nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.UNREGISTERED")%> <%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(className)%></nobr>
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
                  <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisClassName)%>'<%=className.equals(thisClassName)?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
        }
%>
                </select>
<%
      }
%>
              </div>
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.AuthorityGroupColon")%></label>

<%
      int i = 0;
%>
                <select name="authorityname" class="form-control">
                  <option value="_none_" <%=(authorityName==null)?"selected=\"selected\"":""%>><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.GlobalAuthority")%></option>
<%
      while (i < set2.length)
      {
        IAuthorityGroup row = set2[i++];
        String thisAuthorityName = row.getName();
        String thisDescription = row.getDescription();
        if (thisDescription == null || thisDescription.length() == 0)
          thisDescription = thisAuthorityName;
%>
                  <option value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(thisAuthorityName)%>'<%=(authorityName!=null && authorityName.equals(thisAuthorityName))?"selected=\"selected\"":""%>><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
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
            <input type="hidden" name="authorityname" value='<%=(authorityName==null)?"_none_":org.apache.manifoldcf.ui.util.Encoder.attributeEscape(authorityName)%>'/>
<%
    }


    // The "Throttling" tab
%>
            <input type="hidden" name="throttlecount" value='<%=Integer.toString(throttles.size())%>'/>
<%
    if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Throttling")))
    {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.MaxconnectionsColon")%></label>
                <input type="text" size="6" name="maxconnections" class="form-control" value='<%=Integer.toString(maxConnections)%>'/>
              </div>
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.ThrottlingColon")%></label>

                <input type="hidden" name="throttleop" value="Continue"/>
                <input type="hidden" name="throttlenumber" value=""/>
                <table class="table table-bordered">
                  <tr>
                    <th>Action</th>
                    <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.BinRegularExpression")%></th>
                    <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.Description")%></th>
                    <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.MaxAvgFetchesMin")%></th>
                  </tr>
<%
    int k = 0;
    while (k < throttles.size())
    {
      Map map = (Map)throttles.get(k);
      String regexp = (String)map.get("regexp");
      String desc = (String)map.get("description");
      if (desc == null)
        desc = "";
      Long value = (Long)map.get("value");
%>
                  <tr>
                    <td>
                      <input class="btn btn-danger btn-xs" type="button"
                              value="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.Delete")%>"
                              alt='<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.Deletethrottle")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(regexp)%>'
                              onclick='<%="javascript:DeleteThrottle("+Integer.toString(k)+");"%>'/>
                    </td>
                    <td>
                      <input type="hidden" name='<%="throttle_"+Integer.toString(k)%>' value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
                      <input type="hidden" name='<%="throttledesc_"+Integer.toString(k)%>' value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(desc)%>'/>
                      <input type="hidden" name='<%="throttlevalue_"+Integer.toString(k)%>' value='<%=value.toString()%>'/>
                      <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
                    </td>
                    <td>
<%
      if (desc.length() > 0)
      {
%>
                      <%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(desc)%>
<%
      }
%>
                    </td>
                    <td><%=value.toString()%></td>
                  </tr>
<%
      k++;
    }
    if (k == 0)
    {
%>
                  <div class="callout callout-info">
                    <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editconnection.NoThrottlingSpecified")%>
                  </div>
<%
    }
%>
                  <tr>
                    <td>
                      <input type="button" class="btn btn-success btn-xs"
                              value="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.Add")%>"
                              alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.Addthrottle")%>"
                              onclick="javascript:AddThrottle();"/>
                    </td>
                    <td><input type="text" class="form-control" name="throttle" size="30" value=""/></td>
                    <td><input type="text" class="form-control" name="throttledesc" size="30" value=""/></td>
                    <td><input type="text" class="form-control" name="throttlevalue" size="5" value=""/></td>
                  </tr>
                </table>
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
      int k = 0;
      while (k < throttles.size())
      {
        Map map = (Map)throttles.get(k);
        String regexp = (String)map.get("regexp");
        String desc = (String)map.get("description");
        if (desc == null)
          desc = "";
        Long value = (Long)map.get("value");
%>
            <input type="hidden" name='<%="throttle_"+Integer.toString(k)%>' value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
            <input type="hidden" name='<%="throttledesc_"+Integer.toString(k)%>' value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(desc)%>'/>
            <input type="hidden" name='<%="throttlevalue_"+Integer.toString(k)%>' value='<%=value.toString()%>'/>
<%
        k++;
      }
    }

    if (className.length() > 0)
    {
      RepositoryConnectorFactory.outputConfigurationBody(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters,tabName);
    }
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
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.SaveThisAuthorityConnection")%>" data-toggle="tooltip"><i class="fa fa-save fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.Save")%></a>
<%
    }
    else
    {
      if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editconnection.Type")))
      {
%>
            <a href="#" class="btn btn-primary" onClick="javascript:Continue()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.ContinueToNextPage")%>" data-toggle="tooltip"><i class="fa fa-play fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.Continue")%></a>
<%
      }
    }
%>
            <a href="#" class="btn btn-primary" onClick="javascript:Cancel()" 
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.CancelConnectionEditing")%>" data-toggle="tooltip"><i class="fa fa-times-circle-o fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editconnection.Cancel")%></a>
          </div>
        </div>
      </form>
<%
  }
%>
<%
}
catch (ManifoldCFException e)
{
  e.printStackTrace();
  variableContext.setParameter("text",e.getMessage());
  variableContext.setParameter("target","listconnections.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
    </div>
  </div>
</div>
