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
  IOutputConnectorManager connectorManager = OutputConnectorManagerFactory.make(threadContext);
  // Get the connection manager handle
  IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(threadContext);
  IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(threadContext);
  String connectionName = variableContext.getParameter("connname");
  IOutputConnection connection = connManager.load(connectionName);
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
      connectorName = className + Messages.getString(pageContext.getRequest().getLocale(),"viewoutput.uninstalled");
    int maxCount = connection.getMaxConnections();
    ConfigParams parameters = connection.getConfigParams();

    // Do stuff so we can call out to display the parameters
    //String JSPFolder = OutputConnectorFactory.getJSPFolder(threadContext,className);
    //threadContext.save("Parameters",parameters);

    // Now, test the connection.
    String connectionStatus;
    try
    {
      IOutputConnector c = outputConnectorPool.grab(connection);
      if (c == null)
        connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewoutput.Connectorisnotinstalled");
      else
      {
        try
        {
          connectionStatus = c.check();
        }
        finally
        {
          outputConnectorPool.release(connection,c);
        }
      }
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewoutput.Threwexception")+" '"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
    }
%>

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "viewoutput.ApacheManifoldCFViewOutputConnectionStatus")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.ViewOutputConnectionStatus") + " - " + connectionName %>',
      'outputs'
  );

  function Delete(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.Deleteoutputconnection")%> '"+connectionName+"'<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.qmark")%>"))
    {
      document.viewconnection.op.value="Delete";
      document.viewconnection.connname.value=connectionName;
      $.ManifoldCF.submit(document.viewconnection);
    }
  }

  function ReingestAll(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.Thiscommandwillforce")%> '"+connectionName+"' <%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.toberecrawled")%>"))
    {
      document.viewconnection.op.value="ReingestAll";
      document.viewconnection.connname.value=connectionName;
      $.ManifoldCF.submit(document.viewconnection);
    }
  }

  function RemoveAll(connectionName)
  {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.Thiscommandwillcause")%> '"+connectionName+"' <%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewoutput.tobeforgotten")%>"))
    {
      document.viewconnection.op.value="RemoveAll";
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
        <input type="hidden" name="type" value="output"/>
        <input type="hidden" name="connname" value=""/>

        <div class="box-body">
          <table class="table table-bordered">
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.NameColon")%></th>
              <td class="value" colspan="1"><%="<!--connection=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName) + "-->"%>
                <nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr>
              </td>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.DescriptionColon")%></th>
              <td class="value" colspan="1"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
            </tr>
            <tr>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.ConnectionTypeColon")%></th>
              <td class="value" colspan="1"><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.MaxConnectionsColon")%></th>
              <td class="value" colspan="1"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td>
            </tr>
            <tr>
              <td colspan="4">
<%
    OutputConnectorFactory.viewConfiguration(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out,adminprofile),pageContext.getRequest().getLocale(),parameters);
%>
              </td>
            </tr>
            <tr>
              <th><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewoutput.ConnectionStatusColon")%></nobr></th>
              <td colspan="3"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
            </tr>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href='<%="viewoutput.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.Refresh")%>"
                    class="link btn btn-success" role="button" data-toggle="tooltip"><i class="fa fa-refresh fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.Refresh")%></a>
            <a href='<%="editoutput.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(connectionName)%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.EditThisOutputConnection")%>"
                    class="link btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-pencil fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.Edit")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.DeleteThisOutputConnection")%>"
                    class="btn btn-danger" role="button" data-toggle="tooltip"><i class="fa fa-trash fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.Delete")%></a>
            <a href="javascript:void(0);"
                    onclick='<%="javascript:ReingestAll(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.ReIngestAllDocumentsAssociatedWithThisOutputConnection")%>"
                    class="btn btn-primary" role="button" data-toggle="tooltip"><i class="fa fa-repeat fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.ReIngestAllAssociatedDocuments")%></a>
            <a href="javascript:void(0)"
                    onclick='<%="javascript:RemoveAll(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>'
                    title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.RemoveAllDocumentsAssociatedWithThisOutputConnection")%>"
                    class="btn btn-warning" role="button" data-toggle="tooltip"><i class="fa fa-remove fa-fw" aria-hidden="true"></i><%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewoutput.RemoveAllAssociatedDocuments")%></a>
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
  variableContext.setParameter("target","listoutputs.jsp");
%>
  <jsp:forward page="error.jsp"/>
<%
}
%>
