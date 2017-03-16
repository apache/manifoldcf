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
  INotificationConnectorManager connectorManager = NotificationConnectorManagerFactory.make(threadContext);
  // Get the connection manager handle
  INotificationConnectionManager connManager = NotificationConnectionManagerFactory.make(threadContext);
  INotificationConnectorPool notificationConnectorPool = NotificationConnectorPoolFactory.make(threadContext);
  String connectionName = variableContext.getParameter("connname");
  INotificationConnection connection = connManager.load(connectionName);
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
      connectorName = className + Messages.getString(pageContext.getRequest().getLocale(),"viewnotification.uninstalled");
    int maxCount = connection.getMaxConnections();
    ConfigParams parameters = connection.getConfigParams();

    // Do stuff so we can call out to display the parameters
    //String JSPFolder = NotificationConnectorFactory.getJSPFolder(threadContext,className);
    //threadContext.save("Parameters",parameters);

    // Now, test the connection.
    String connectionStatus;
    try
    {
      INotificationConnector c = notificationConnectorPool.grab(connection);
      if (c == null)
        connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewnotification.Connectorisnotinstalled");
      else
      {
        try
        {
          connectionStatus = c.check();
        }
        finally
        {
          notificationConnectorPool.release(connection,c);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewnotification.Threwexception")+" '"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
    }
%>

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewnotification.ApacheManifoldCFViewNotificationConnectionStatus")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewnotification.ViewNotificationConnectionStatus")%>',
      'repositories'
  );
  function Delete(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewnotification.Deletenotificationconnection")%> '"+connectionName+"'<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewnotification.qmark")%>"))
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
        <input type="hidden" name="type" value="notification"/>
        <input type="hidden" name="connname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.NameColon")%></nobr></th>
              <td><%="<!--connection=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName) + "-->"%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr>
              </td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.DescriptionColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.ConnectionTypeColon")%></nobr></th>
              <td><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.MaxConnectionsColon")%></nobr></th>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td>
            </tr>
            <tr>
              <td colspan="4">
<%
    NotificationConnectorFactory.viewConfiguration(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters);
%>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.ConnectionStatusColon")%></nobr></th>
              <td colspan="3"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href='<%="viewnotification.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewnotification.Refresh")%>"
                    class="link btn btn-success" role="button" data-toggle="tooltip"><i class="fa fa-refresh fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.Refresh")%></a>
            <a href='<%="editnotification.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewnotification.EditThisNotificationConnection")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-pencil fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.Edit")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewnotification.DeleteThisNotificationConnection")%>"
                    class="btn btn-danger" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewnotification.Delete")%></a>
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
  variableContext.setParameter("target","listnotifications.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
