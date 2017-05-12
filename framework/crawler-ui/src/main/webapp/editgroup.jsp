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
// the connection object being edited will be placed in the thread context under the name "GroupObject".
try
{
  // Check if authorized
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
  {
    variableContext.setParameter("target","listgroups.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }
  // Get the group manager
  IAuthorityGroupManager authGroupManager = AuthorityGroupManagerFactory.make(threadContext);

  // Figure out what the current tab name is.
  String tabName = variableContext.getParameter("tabname");
  if (tabName == null || tabName.length() == 0)
    tabName = Messages.getString(pageContext.getRequest().getLocale(),"editgroup.Name");

  String groupName = null;
  IAuthorityGroup group = (IAuthorityGroup)threadContext.get("GroupObject");
  if (group == null)
  {
    // We did not go through execute.jsp
    // We might have received an argument specifying the connection name.
    groupName = variableContext.getParameter("groupname");
    // If the groupname is not null, load the connection description and prepopulate everything with what comes from it.
    if (groupName != null && groupName.length() > 0)
    {
      group = authGroupManager.load(groupName);
    }
  }

  // Setup default fields
  boolean isNew = true;
  String description = "";

  if (group != null)
  {
    // Set up values
    isNew = group.getIsNew();
    groupName = group.getName();
    description = group.getDescription();
  }
  else
    groupName = null;

  if (groupName == null)
    groupName = "";

  // Initialize tabs array
  ArrayList tabsArray = new ArrayList();

  // Set up the predefined tabs
  tabsArray.add(Messages.getString(pageContext.getRequest().getLocale(),"editgroup.Name"));
%>

<script type="text/javascript">
  <!--
<%
  String heading = null;
  if (description.length() > 0)
  {
    heading = Messages.getBodyString(pageContext.getRequest().getLocale(),"editgroup.EditGroup") + " - " + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description);
  }
  else
  {
    heading = Messages.getBodyString(pageContext.getRequest().getLocale(),"editgroup.EditAGroup");
  }
%>
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "editgroup.ApacheManifoldCFEditAuthorityGroup")%>',
      '<%=heading%>',
      'authorities'
  );
  // Use this method to repost the form and pick a new tab
  function SelectTab(newtab)
  {
    if (checkForm())
    {
      document.editgroup.tabname.value=newtab;
      $.ManifoldCF.submit(document.editgroup);
    }
  }

  // Use this method to repost the form,
  // and set the anchor request.
  function postFormSetAnchor(anchorValue)
  {
    if (checkForm())
    {
      if (anchorValue != "")
        document.group.action=document.editgroup.action + "#" + anchorValue;
      $.ManifoldCF.submit(document.editgroup);
    }
  }

  // Use this method to repost the form
  function postForm()
  {
    if (checkForm())
    {
      $.ManifoldCF.submit(document.editgroup);
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
      if (editgroup.groupname.value == "")
      {
        alert("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editgroup.AuthorityGroupMustHaveAName")%>");
        SelectTab("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"editgroup.Name")%>");
        document.editgroup.groupname.focus();
        return;
      }
      if (window.checkConfigForSave)
      {
        if (!checkConfigForSave())
          return;
      }
      document.editgroup.op.value="Save";
      $.ManifoldCF.submit(document.editgroup);
    }
  }

  function Continue()
  {
    document.editgroup.op.value="Continue";
    postForm();
  }

  function Cancel()
  {
    document.editgroup.op.value="Cancel";
    $.ManifoldCF.submit(document.editgroup);
  }

  function checkForm()
  {
    return true;
  }

  //-->
</script>

<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">

      <form class="standardform" name="editgroup" action="execute.jsp" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="group"/>
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
                      alt='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(tab)+" "+Messages.getAttributeString(pageContext.getRequest().getLocale(),"editgroup.tab")%>'
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
  if (tabName.equals(Messages.getString(pageContext.getRequest().getLocale(),"editgroup.Name")))
  {
%>
            <div class="tab-pane active" id="tab_<%=activeTab%>">
              <div class="form-group">
                <label><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editgroup.NameColon")%></label>
<%
    // If the group doesn't exist yet, we are allowed to change the name.
    if (isNew)
    {
%>
                <input type="text" size="32" name="groupname" class="form-control" placeholder="Name..." value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(groupName)%>'/>
<%
    }
    else
    {
%>
                <input type="text" size="32" class="form-control" disabled value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(groupName)%>'/>
                <input type="hidden" name="groupname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(groupName)%>'/>
<%
    }
%>
              </div>
              <div class="form-group"> 
                <label for="description"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"editgroup.DescriptionColon")%></label>
                <input type="text" size="50" class="form-control" name="description" id="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
              </div>
            </div>
<%
  }
  else
  {
    // Hiddens for the Name tab
%>
            <input type="hidden" name="groupname" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(groupName)%>'/>
            <input type="hidden" name="description" value='<%=org.apache.manifoldcf.ui.util.Encoder.attributeEscape(description)%>'/>
<%
  }
%>
          </div>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href="javascript:void(0);" onClick="javascript:Save()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editgroup.SaveThisAuthorityGroup")%>"
                    class="btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-save fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editgroup.Save")%></a>
            <a href="javascript:void(0);" onClick="javascript:Cancel()"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editgroup.CancelAuthorityGroupEditing")%>"
                    class="btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-times-circle-o fa-fw"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"editgroup.Cancel")%></a>
          </div>
        </div>
      </form>
    </div>
  </div>
</div>

<%
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
