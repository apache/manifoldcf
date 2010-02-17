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
		MetaCarta Administration: View Output Connection Status
	</title>

	<script type="text/javascript">
	<!--

	function Delete(connectionName)
	{
		if (confirm("Delete output connection '"+connectionName+"'?"))
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
	<p class="windowtitle">View Output Connection Status</p>
	<form class="standardform" name="viewconnection" action="execute.jsp" method="POST">
		<input type="hidden" name="op" value="Continue"/>
		<input type="hidden" name="type" value="output"/>
		<input type="hidden" name="connname" value=""/>

<%
    try
    {
	IOutputConnectorManager connectorManager = OutputConnectorManagerFactory.make(threadContext);
	// Get the connection manager handle
	IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(threadContext);
	String connectionName = variableContext.getParameter("connname");
	IOutputConnection connection = connManager.load(connectionName);
	if (connection == null)
	{
		throw new MetacartaException("No such connection: '"+connectionName+"'");
	}
	else
	{
		String description = connection.getDescription();
		if (description == null)
			description = "";
		String className = connection.getClassName();
		String connectorName = connectorManager.getDescription(className);
		if (connectorName == null)
			connectorName = className + "(uninstalled)";
		int maxCount = connection.getMaxConnections();
		ConfigParams parameters = connection.getConfigParams();

		// Do stuff so we can call out to display the parameters
		String JSPFolder = OutputConnectorFactory.getJSPFolder(threadContext,className);
		threadContext.save("Parameters",parameters);

		// Now, test the connection.
		String connectionStatus;
		try
		{
			IOutputConnector c = OutputConnectorFactory.grab(threadContext,className,parameters,maxCount);
			if (c == null)
				connectionStatus = "Connector is not installed.";
			else
			{
				try
				{
					connectionStatus = c.check();
				}
				finally
				{
					OutputConnectorFactory.release(c);
				}
			}
		}
		catch (MetacartaException e)
		{
			connectionStatus = "Threw exception: '"+org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())+"'";
		}
%>
		<table class="displaytable">
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr>Name:</nobr></td><td class="value" colspan="1"><%="<!--connection="+org.apache.lcf.ui.util.Encoder.bodyEscape(connectionName)+"-->"%><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(connectionName)%></nobr></td>
				<td class="description" colspan="1"><nobr>Description:</nobr></td><td class="value" colspan="1"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr>Connection type:</nobr></td><td class="value" colspan="1"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(connectorName)%></nobr></td>
				<td class="description" colspan="1"><nobr>Max connections:</nobr></td><td class="value" colspan="1"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(Integer.toString(maxCount))%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td colspan="4">
<%
		if (JSPFolder != null)
		{
%>
				<jsp:include page='<%="/output/"+JSPFolder+"/viewconfig.jsp"%>' flush="true"/>
<%
		}
%>
				</td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr>Connection status:</nobr></td><td class="value" colspan="3"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(connectionStatus)%></td>
			</tr>
			<tr>
				<td class="separator" colspan="4"><hr/></td>
			</tr>
		<tr><td class="message" colspan="4"><a href='<%="viewoutput.jsp?connname="+java.net.URLEncoder.encode(connectionName,"UTF-8")%>' alt="Refresh">Refresh</a>&nbsp;<a href='<%="editoutput.jsp?connname="+java.net.URLEncoder.encode(connectionName,"UTF-8")%>' alt="Edit this output connection">Edit</a>&nbsp;<a href="javascript:void()" onclick='<%="javascript:Delete(\""+org.apache.lcf.ui.util.Encoder.attributeJavascriptEscape(connectionName)+"\")"%>' alt="Delete this output connection">Delete</a>
		</td></tr>
		</table>

<%
	}
    }
    catch (MetacartaException e)
    {
	e.printStackTrace();
	variableContext.setParameter("text",e.getMessage());
	variableContext.setParameter("target","listoutputs.jsp");
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
