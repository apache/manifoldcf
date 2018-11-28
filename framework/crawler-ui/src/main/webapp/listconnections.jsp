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

<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.ApacheManifoldCFListConnections")%>
	</title>

	<script type="text/javascript">
	<!--

	function Delete(connectionName)
	{
		if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"listconnections.DeleteConnection")%> '"+connectionName+"'?"))
		{
			document.listconnections.op.value="Delete";
			document.listconnections.connname.value=connectionName;
			document.listconnections.submit();
		}
	}

	//-->
	</script>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="window">
	<p class="windowtitle"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.ListOfRepositoryConnections")%></p>
	<form class="standardform" name="listconnections" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="connection"/>
		<input type="hidden" name="connname" value=""/>

<%
    try
    {
	// Get the job manager handle
	IRepositoryConnectionManager manager = RepositoryConnectionManagerFactory.make(threadContext);
	IConnectorManager connectorManager = ConnectorManagerFactory.make(threadContext);
	IRepositoryConnection[] connections = manager.getAllConnections();
%>
		<table class="datatable">
			<tr>
				<td class="separator" colspan="6"><hr/></td>
			</tr>
			<tr class="headerrow">
				<td class="columnheader"></td><td class="columnheader"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Name")%></td><td class="columnheader"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Description")%></td><td class="columnheader"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.ConnectionType")%></td><td class="columnheader"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Authority")%></td>
				<td class="columnheader"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Max")%></td>
			</tr>
<%
	int i = 0;
	while (i < connections.length)
	{
		IRepositoryConnection connection = connections[i++];

		String name = connection.getName();
		String description = connection.getDescription();
		if (description == null)
			description = "";
		String className = connection.getClassName();
		String connectorName = connectorManager.getDescription(className);
		if (connectorName == null)
			connectorName = className + "(uninstalled)";
		String authorityName = connection.getACLAuthority();
		int maxCount = connection.getMaxConnections();

%>
		<tr <%="class=\""+((i%2==0)?"evendatarow":"odddatarow")+"\""%>>
			<td class="columncell">
				<a href='<%="viewconnection.jsp?connname="+java.net.URLEncoder.encode(name,"UTF-8")%>' alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listconnections.View")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.View")%></a>&nbsp;<a href='<%="editconnection.jsp?connname="+java.net.URLEncoder.encode(name,"UTF-8")%>' alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listconnections.Edit")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Edit")%></a>&nbsp;<a href="javascript:void()" onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(name)+"\")"%>' alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listconnections.Delete")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)%>'><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.Delete")%></a>
			</td>
			<td class="columncell"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name)%></td>
			<td class="columncell"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
			<td class="columncell"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></td>
			<td class="columncell"><%=((authorityName==null)?Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.GlobalAuthority"):org.apache.manifoldcf.ui.util.Encoder.bodyEscape(authorityName))%></td>
			<td class="columncell"><%=Integer.toString(maxCount)%></td>
		</tr>
<%
	}
%>
			<tr>
				<td class="separator" colspan="6"><hr/></td>
			</tr>
			<tr><td class="message" colspan="6"><a href="editconnection.jsp" alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"listconnections.AddAConnection")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"listconnections.AddNewConnection")%></a></td></tr>
		</table>

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
	    </form>
       </td>
      </tr>
    </table>

</body>

</html>

<%

%>
