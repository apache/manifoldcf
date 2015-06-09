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


<script type="text/javascript">

  <!--
  $.ManifoldCF.setTitle('<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.ApacheManifoldCFListTransformationConnections")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.ListOfTransformationConnections")%>',
      'outputs'
  );

  function Delete(connectionName) {
    if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"listtransformations.DeleteTransformationConnection")%> '" + connectionName + "'?")) {
      document.listconnections.op.value = "Delete";
      document.listconnections.connname.value = connectionName;
      $.ManifoldCF.submit(document.listconnections);
    }
  }

  //-->
</script>

<div class="row">
  <div class="col-md-12">
    <div class="box box-primary">
      <form class="standardform" name="listconnections" action="execute.jsp" method="POST">
        <input type="hidden" name="op" value="Continue"/>
        <input type="hidden" name="type" value="transformation"/>
        <input type="hidden" name="connname" value=""/>

        <div class="box-body">
          <%
            try {
              // Get the transformation connection manager handle
              ITransformationConnectionManager manager = TransformationConnectionManagerFactory.make(threadContext);
              ITransformationConnectorManager connectorManager = TransformationConnectorManagerFactory.make(threadContext);
              ITransformationConnection[] connections = manager.getAllConnections();
          %>
          <table class="table table-bordered">
            <tr>
              <th>Action</th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.Name")%>
              </th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.Description")%>
              </th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.ConnectionType")%>
              </th>
              <th><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.Max")%>
              </th>
            </tr>
            <%
              int i = 0;
              while (i < connections.length) {
                ITransformationConnection connection = connections[i++];

                String name = connection.getName();
                String description = connection.getDescription();
                if (description == null)
                  description = "";
                String className = connection.getClassName();
                String connectorName = connectorManager.getDescription(className);
                if (connectorName == null)
                  connectorName = className + Messages.getString(pageContext.getRequest().getLocale(), "listtransformations.uninstalled");
                int maxCount = connection.getMaxConnections();

            %>
            <tr>
              <td>
                <div class="btn-group">
                  <a href='<%="viewtransformation.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(name)%>'
                     title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listtransformations.View")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                     class="link btn btn-success btn-xs" role="button" data-toggle="tooltip">
                    <span class="fa fa-eye" aria-hidden="true"></span>
                  </a>
                  <a href='<%="edittransformation.jsp?connname="+org.apache.manifoldcf.core.util.URLEncoder.encode(name)%>'
                     title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listtransformations.Edit")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                     class="link btn btn-primary btn-xs" role="button" data-toggle="tooltip">
                    <span class="fa fa-pencil-square-o" aria-hidden="true"></span>
                  </a>
                  <a href="javascript:void()"
                     onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(name)+"\")"%>'
                     title='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listtransformations.Delete")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'
                     class="btn btn-danger btn-xs" role="button" data-toggle="tooltip">
                    <span class="fa fa-trash" aria-hidden="true"></span>
                  </a>
                </div>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name)%>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%>
              </td>
              <td><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%>
              </td>
              <td><%=Integer.toString(maxCount)%>
              </td>
            </tr>
            <%
              }
            %>
          </table>
        </div>
        <div class="box-footer clearfix">
          <div class="btn-group">
            <a href="edittransformation.jsp"
               alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listtransformations.AddATransformationConnection")%>"
               class="link btn btn-primary" role="button">
              <span class="fa fa-plus-circle" aria-hidden="true"></span>
              <%=Messages.getBodyString(pageContext.getRequest().getLocale(), "listtransformations.AddaNewTransformationConnection")%>
            </a>
          </div>
          <%
          } catch (ManifoldCFException e) {
            e.printStackTrace();
            variableContext.setParameter("text", e.getMessage());
            variableContext.setParameter("target", "index.jsp");
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