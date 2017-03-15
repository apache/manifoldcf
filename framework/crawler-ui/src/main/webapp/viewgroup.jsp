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
  // Get the job manager handle
  IAuthorityGroupManager manager = AuthorityGroupManagerFactory.make(threadContext);
  String groupName = variableContext.getParameter("groupname");
  IAuthorityGroup group = manager.load(groupName);
  if (group == null)
  {
    throw new ManifoldCFException("No such group: "+groupName);
  }
  else
  {
    String description = group.getDescription();
    if (description == null)
      description = "";
%>

<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewgroup.ApacheManifoldCFViewGroup")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewgroup.ViewAuthorityGroup") + " - " + groupName %>',
      'authorities'
  );

  function Delete(groupName)
  {
    document.viewgroup.op.value="Delete";
    document.viewgroup.groupname.value=groupName;
    $.ManifoldCF.submit(document.viewgroup);
  }

  //-->
</script>


<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="viewgroup" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="group"/>
        <input type="hidden" name="groupname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewgroup.NameColon")%></nobr></th>
              <td><%="<!--group=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(groupName) + "-->"%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(groupName)%></nobr>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewgroup.DescriptionColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href='<%="editgroup.jsp?groupname="+org.apache.manifoldcf.core.util.URLEncoder.encode(groupName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewgroup.EditThisAuthorityGroup")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-pencil fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewgroup.Edit")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(groupName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewgroup.DeleteThisAuthorityGroup")%>"
                    class="btn btn-danger" role="button" data-toggle="tooltip"><i class="fa fa-remove fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewgroup.Delete")%></a>
          </div>
        </div>
      </form>
    </div>
  </div>
</div>
<%
  }
}
catch (ManifoldCFException e)
{
  e.printStackTrace();
  variableContext.setParameter("text",e.getMessage());
  variableContext.setParameter("target","listgroups.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
