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
		<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.ApacheManifoldCFViewAuthorityConnectionStatus")%>
	</title>

	<script type="text/javascript">
	<!--

	function Delete(connectionName)
	{
		if (confirm("<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewauthority.DeleteConnection")%> '"+connectionName+"'<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"viewauthority.qmark")%>"))
		{
			document.viewconnection.op.value="Delete";
			document.viewconnection.connname.value=connectionName;
			document.viewconnection.submit();
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
	<p class="windowtitle"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.ViewAuthorityConnectionStatus")%></p>
	<form class="standardform" name="viewconnection" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="authority"/>
		<input type="hidden" name="connname" value=""/>

<%
    try
    {
	IAuthorityConnectionManager manager = AuthorityConnectionManagerFactory.make(threadContext);
	IAuthorityConnectorManager connectorManager = AuthorityConnectorManagerFactory.make(threadContext);
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
		ConfigParams parameters = connection.getConfigParams();

		// Do stuff so we can call out to display the parameters
		//String JSPFolder = AuthorityConnectorFactory.getJSPFolder(threadContext,className);
		//threadContext.save("Parameters",parameters);

		// Now, test the connection.
		String connectionStatus;
		try
		{
			IAuthorityConnector c = AuthorityConnectorFactory.grab(threadContext,className,parameters,maxCount);
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
					AuthorityConnectorFactory.release(c);
				}
			}
		}
		catch (ManifoldCFException e)
		{
			connectionStatus = Messages.getString(pageContext.getRequest().getLocale(),"viewauthority.Threwexception")+" '"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
		}
%>
		<table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.NameColon")%></nobr></td><td class="value" colspan="1"><%="<!--connection="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)+"-->"%><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr></td>
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.DescriptionColon")%></nobr></td><td class="value" colspan="1"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.AuthorityTypeColon")%></nobr></td><td class="value" colspan="1"><nobr><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.MaxConnectionsColon")%></nobr></td><td class="value" colspan="1"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td colspan="4">
<%
		AuthorityConnectorFactory.viewConfiguration(threadContext,className,new org.apache.manifoldcf.ui.jsp.JspWrapper(out),pageContext.getRequest().getLocale(),parameters);
%>

				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.ConnectionStatusColon")%></nobr></td><td class="value" colspan="3"><%=org.apache.manifoldcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
		<tr><td class="message" colspan="4"><a href='<%="viewauthority.jsp?connname="+java.net.URLEncoder.encode(connectionName,"UTF-8")%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.Refresh")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.Refresh")%></a>&nbsp;<a href='<%="editauthority.jsp?connname="+java.net.URLEncoder.encode(connectionName,"UTF-8")%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.EditThisAuthorityConnection")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.Edit")%></a>&nbsp;<a href="javascript:void()" onclick='<%="javascript:Delete(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"viewauthority.DeleteThisAuthorityConnection")%>"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"viewauthority.Delete")%></a>
		</td></tr>
		</table>

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
	    </form>
       </td>
      </tr>
    </table>

</body>

</html>
