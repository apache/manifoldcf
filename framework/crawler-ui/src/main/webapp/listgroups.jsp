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
  if (!adminprofile.checkAllowed(threadContext,IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
  {
    variableContext.setParameter("target","index.jsp");
%>
    <jsp:forward page="unauthorized.jsp"/>
<%
  }
  // Get the authority group manager handle
  IAuthorityGroupManager manager = AuthorityGroupManagerFactory.make(threadContext);
  IAuthorityGroup[] groups = manager.getAllGroups();
%>

<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listgroups.ApacheManifoldCFListAuthorityGroups")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listgroups.ListOfAuthorityGroups")%>',
      'authorities'
  );

  function Delete(groupName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"listgroups.DeleteAuthorityGroup")%> '"+groupName+"'?"))
    {
      document.listgroups.op.value="Delete";
      document.listgroups.groupname.value=groupName;
      $.ManifoldCF.submit(document.listgroups);
    }
  }
  //-->
</script>

<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="listgroups" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="group"/>
        <input type="hidden" name="groupname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th>Action</th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.Name")%></th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.Description")%></th>
            </tr>
<%
  int i = 0;
  while (i < groups.length)
  {
    IAuthorityGroup group = groups[i++];

    String name = group.getName();
    String description = group.getDescription();
    if (description == null)
      description = "";

%>
            <tr>
              <td>
                <div class="btn-group">
                  <a href='<%="viewgroup.jsp?groupname="+org.apache.manifoldcf.core.util.URLEncoder.encode(name)%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listgroups.View")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                          class="link btn btn-success btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-eye fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.View")%></a>
                  <a href='<%="editgroup.jsp?groupname="+org.apache.manifoldcf.core.util.URLEncoder.encode(name)%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listgroups.Edit")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                          class="link btn btn-primary btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-pencil-square-o fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.Edit")%></a>
                  <a href="javascript:void(0);"
                          onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(name)+"\")"%>'
                          title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listgroups.Delete")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                          class="btn btn-danger btn-xs" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.Delete")%></a>
                </div>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name)%></td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
<%
  }
%>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href="editgroup.jsp"
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listgroups.AddNewGroup")%>"
                    class="link btn btn-primary" role="button"><i class="fa fa-plus-circle fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listgroups.AddaNewGroup")%></a>
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
