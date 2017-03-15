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
  IAuthorityConnectionManager manager = AuthorityConnectionManagerFactory.make(threadContext);
  IAuthorityConnectorManager connectorManager = AuthorityConnectorManagerFactory.make(threadContext);
  IAuthorityConnectorPool authorityConnectorPool = AuthorityConnectorPoolFactory.make(threadContext);
  String connectionName = variableContext.getParameter("connname");
  IAuthorityConnection connection = manager.load(connectionName);
  if (connection == null)
  {
    throw new ManifoldCFException("No such authority: '"+connectionName+"'");
  }
  else
  {
    String description = connection.getDescription();
    if (description == null)
      description = "";
    String className = connection.getClassName();
    String connectorName = connectorManager.getDescription(className);
    if (connectorName == null)
      connectorName = className + Messages.getString(pageContext.getRequest().getLocale(),"viewauthority.uninstalled");
    int maxCount = connection.getMaxConnections();
    String prereq = connection.getPrerequisiteMapping();
    String authDomain = connection.getAuthDomain();
    if (authDomain == null)
      authDomain = "";
    String groupName = connection.getAuthGroup();
    if (groupName == null)
      groupName = "";

    ConfigParams parameters = connection.getConfigParams();

    // Do stuff so we can call out to display the parameters
    //String JSPFolder = AuthorityConnectorFactory.getJSPFolder(threadContext,className);
    //threadContext.save("Parameters",parameters);

    // Now, test the connection.
    String connectionStatus;
    try
    {
      IAuthorityConnector c = authorityConnectorPool.grab(connection);
      if (c == null)
      {
        connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewauthority.Connectorisnotinstalled");
      }
      else
      {
        try
        {
          connectionStatus = c.check();
        }
        finally
        {
          authorityConnectorPool.release(connection,c);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewauthority.Threwexception")+" '"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
    }
%>
<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewauthority.ApacheManifoldCFViewAuthorityConnectionStatus")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.ViewAuthorityConnectionStatus") + " - " + connectionName %>',
      'authorities'
  );
  function Delete(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewauthority.DeleteConnection")%> '"+connectionName+"'<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewauthority.qmark")%>"))
    {
      document.viewconnection.op.value="Delete";
      document.viewconnection.connname.value=connectionName;
      $.ManifoldCF.submit(document.viewconnection);
    }
  }

  //-->
</script>


<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="viewconnection" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="authority"/>
        <input type="hidden" name="connname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.NameColon")%></nobr></th>
              <td><%="<!--connection=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName) + "-->"%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr>
              </td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.DescriptionColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.AuthorityTypeColon")%></nobr></th>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.MaxConnectionsColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.AuthorityGroupColon")%></nobr></th>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(groupName)%></nobr></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.AuthorizationDomainColon")%></nobr></th>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(authDomain)%></nobr>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.PrerequisiteUserMappingColon")%></nobr></th>
              <td colspan="3">
<%
  if (prereq != null)
  {
%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(prereq)%></nobr>
<%
  }
  else
  {
%>
                <nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.NoPrerequisites")%></nobr>
<%
  }
%>
              </td>
            </tr>
            <tr>
              <td colspan="4">
<%
    AuthorityConnectorFactory.viewConfiguration(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters);
%>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.ConnectionStatusColon")%></nobr></th>
              <td colspan="3"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href='<%="viewauthority.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.Refresh")%>"
                    class="link btn btn-success" role="button" data-toggle="tooltip"><i class="fa fa-refresh fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.Refresh")%></a>
            <a href='<%="editauthority.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.EditThisAuthorityConnection")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-pencil fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.Edit")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.DeleteThisAuthorityConnection")%>"
                    class="btn btn-danger" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.Delete")%></a>
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
  variableContext.setParameter("target","listauthorities.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
