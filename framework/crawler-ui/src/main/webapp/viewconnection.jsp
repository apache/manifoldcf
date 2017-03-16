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
  IConnectorManager connectorManager = ConnectorManagerFactory.make(threadContext);
  // Get the connection manager handle
  IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
  IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
  String connectionName = variableContext.getParameter("connname");
  IRepositoryConnection connection = connManager.load(connectionName);
  if (connection == null)
  {
    throw new ManifoldCFException("No such connection: '"+connectionName+"'");
  }
  else
  {
    String description = connection.getDescription();
    if (description == null)
      description = "";
    String className = connection.getClassName();
    String connectorName = connectorManager.getDescription(className);
    if (connectorName == null)
      connectorName = className + Messages.getString(pageContext.getRequest().getLocale(),"viewconnection.uninstalled");
    String authorityName = connection.getACLAuthority();
    if (authorityName == null)
      authorityName = Messages.getString(pageContext.getRequest().getLocale(),"viewconnection.NoneGlobalAuthority");
    int maxCount = connection.getMaxConnections();
    String[] throttles = connection.getThrottles();
    ConfigParams parameters = connection.getConfigParams();

    // Do stuff so we can call out to display the parameters
    //String JSPFolder = RepositoryConnectorFactory.getJSPFolder(threadContext,className);
    //threadContext.save("Parameters",parameters);

    // Now, test the connection.
    String connectionStatus;
    try
    {
      IRepositoryConnector c = repositoryConnectorPool.grab(connection);
      if (c == null)
        connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewconnection.Connectorisnotinstalled");
      else
      {
        try
        {
          connectionStatus = c.check();
        }
        finally
        {
          repositoryConnectorPool.release(connection,c);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewconnection.Threwexception")+" '"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
    }
%>
<script type="text/javascript">
  <!--

  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewconnection.ApacheManifoldCFViewRepositoryConnectionStatus")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewconnection.ViewRepositoryConnectionStatus")%>',
      'repositories'
  );

  function Delete(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewconnection.DeleteConnection")%> '"+connectionName+"'<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewconnection.qmark")%>"))
    {
      document.viewconnection.op.value="Delete";
      document.viewconnection.connname.value=connectionName;
      $.ManifoldCF.submit(document.viewconnection);
    }
  }

  function ClearHistory(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewconnection.Thiscommandwillclearallhistoryrelatedto")%> '"+connectionName+"' <%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewconnection.period")%>"))
    {
      document.viewconnection.op.value="ClearHistory";
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
        <input type="hidden" name="type" value="connection"/>
        <input type="hidden" name="connname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.NameColon")%></nobr></th>
              <td><%="<!--connection=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName) + "-->"%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr>
              </td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.DescriptionColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.ConnectionTypeColon")%></nobr></th>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.MaxConnectionsColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td></tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.AuthorityGroupColon")%></nobr></th>
              <td colspan="3"><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(authorityName)%></nobr></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.ThrottlingColon")%></nobr></th>
              <td colspan="3">
                <table class="table table-bordered">
                  <tr>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.Binregularexpression")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.Description")%></nobr></th>
                    <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.Maxavgfetches")%></nobr></th>
                  </tr>
<%
    int j = 0;
    while (j < throttles.length)
    {
%>
                  <tr>
                    <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(throttles[j])%></nobr></td>
                    <td>
<%
      String tdescription = connection.getThrottleDescription(throttles[j]);
      if (tdescription != null && tdescription.length() > 0)
      {
%>
                      <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(tdescription)%></nobr>
<%
      }
%>
                    </td>
                    <td><%=new Long((long)((double)connection.getThrottleValue(throttles[j])*(double)60000.0+0.5)).toString()%></td>
                  </tr>
<%
      j++;
    }
    if (j == 0)
    {
%>
                  <tr><td class="text-yellow" colspan="3"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.NoThrottles")%></nobr></td></tr>
<%
    }
%>
                </table>
              </td>
            </tr>
            <tr>
              <td colspan="4">
<%
    RepositoryConnectorFactory.viewConfiguration(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters);
%>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewconnection.ConnectionStatusColon")%></nobr></th>
              <td colspan="3"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href='<%="viewconnection.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.Refresh")%>"
                    class="link btn btn-success" role="button" data-toggle="tooltip"><i class="fa fa-refresh fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.Refresh")%></a>
            <a href='<%="editconnection.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.EditThisConnection")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-pencil fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.Edit")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.Deletethisconnection")%>"
                    class="btn btn-danger" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.Deletethisconnection")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:ClearHistory(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.ClearHistoryAssociatedWithThisConnection")%>"
                    class="btn btn-warning" role="button" data-toggle="tooltip"><i class="fa fa-remove fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewconnection.ClearAllRelatedHistory")%></a>
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
  variableContext.setParameter("target","listconnections.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
