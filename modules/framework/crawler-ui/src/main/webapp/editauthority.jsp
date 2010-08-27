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
    // The contract of this edit page is as follows.  It is either called directly, in which case it is expected to be creating
    // a connection or beginning the process of editing an existing connection, or it is called via redirection from execute.jsp, in which case
    // the connection object being edited will be placed in the thread context under the name "ConnectionObject".
    try
    {
	// Get the connection manager handle
	IAuthorityConnectionManager connMgr = AuthorityConnectionManagerFactory.make(threadContext);
	// Also get the list of available connectors
	IAuthorityConnectorManager connectorManager = AuthorityConnectorManagerFactory.make(threadContext);

	// Figure out what the current tab name is.
	String tabName = variableContext.getParameter("tabname");
	if (tabName == null || tabName.length() == 0)
		tabName = "Name";

	String connectionName = null;
	IAuthorityConnection connection = (IAuthorityConnection)threadContext.get("ConnectionObject");
	if (connection == null)
	{
		// We did not go through execute.jsp
		// We might have received an argument specifying the connection name.
		connectionName = variableContext.getParameter("connname");
		// If the connectionname is not null, load the connection description and prepopulate everything with what comes from it.
		if (connectionName != null && connectionName.length() > 0)
		{
			connection = connMgr.load(connectionName);
		}
	}

	// Setup default fields
	String description = "";
	String className = "";
	int maxConnections = 10;
	ConfigParams parameters = new ConfigParams();

	if (connection != null)
	{
		// Set up values
		connectionName = connection.getName();
		description = connection.getDescription();
		className = connection.getClassName();
		parameters = connection.getConfigParams();
		maxConnections = connection.getMaxConnections();
	}
	else
		connectionName = null;

	if (connectionName == null)
		connectionName = "";

	// Initialize tabs array
	ArrayList tabsArray = new ArrayList();

	// Set up the predefined tabs
	tabsArray.add("Name");
	tabsArray.add("Type");
	if (className.length() > 0)
		tabsArray.add("Throttling");

%>

<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		Apache Connectors Framework: Edit Authority
	</title>

	<script type="text/javascript">
	<!--
	// Use this method to repost the form and pick a new tab
	function SelectTab(newtab)
	{
		if (checkForm())
		{
			document.editconnection.tabname.value = newtab;
			document.editconnection.submit();
		}
	}

	// Use this method to repost the form,
	// and set the anchor request.
	function postFormSetAnchor(anchorValue)
	{
		if (checkForm())
		{
			if (anchorValue != "")
				document.editconnection.action = document.editconnection.action + "#" + anchorValue;
			document.editconnection.submit();
		}
	}

	// Use this method to repost the form
	function postForm()
	{
		if (checkForm())
		{
			document.editconnection.submit();
		}
	}

	function Save()
	{
		if (checkForm())
		{
			// Can't submit until all required fields have been set.
			// Some of these don't live on the current tab, so don't set
			// focus.

			// Check our part of the form, for save
			if (editconnection.connname.value == "")
			{
				alert("Connection must have a name");
				SelectTab("Name");
				document.editconnection.connname.focus();
				return;
			}
			if (window.checkConfigForSave)
			{
				if (!checkConfigForSave())
					return;
			}
			document.editconnection.op.value="Save";
			document.editconnection.submit();
		}
	}

	function Continue()
	{
		document.editconnection.op.value="Continue";
		postForm();
	}

	function Cancel()
	{
		document.editconnection.op.value="Cancel";
		document.editconnection.submit();
	}

	function checkForm()
	{
		if (!checkConnectionCount())
			return false;
		if (window.checkConfig)
			return checkConfig();
		return true;
	}

	function checkConnectionCount()
	{
		if (!isInteger(editconnection.maxconnections.value))
		{
			alert("The maximum number of connections must be a valid integer");
			editconnection.maxconnections.focus();
			return false;
		}
		return true;
	}

	function isRegularExpression(value)
	{
		try
		{
			var foo = "teststring";
                        foo.search(value.replace(/\(\?i\)/,""));
			return true;
		}
		catch (e)
		{
			return false;
		}

	}

	function isInteger(value)
	{
		var anum=/(^\d+$)/;
		return anum.test(value);
	}

	//-->
	</script>
<%
	AuthorityConnectorFactory.outputConfigurationHeader(threadContext,className,new org.apache.acf.ui.jsp.JspWrapper(out),parameters,tabsArray);

	// Get connectors, since this will be needed to determine what to display.
	IResultSet set = connectorManager.getConnectors();

%>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="darkwindow">


<%
	if (set.getRowCount() == 0)
	{
%>
	<p class="windowtitle">Edit Authority Connection</p>
	<table class="displaytable"><tr><td class="message">No authority connectors registered</td></tr></table>
<%
	}
	else
	{
%>
	<form class="standardform" name="editconnection" action="execute.jsp" method="POST" enctype="multipart/form-data">
	  <input type="hidden" name="op" value="Continue"/>
	  <input type="hidden" name="type" value="authority"/>
	  <input type="hidden" name="tabname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(tabName)%>'/>
	    <table class="tabtable">
	      <tr class="tabrow">
<%
	  int tabNum = 0;
	  while (tabNum < tabsArray.size())
	  {
		String tab = (String)tabsArray.get(tabNum++);
		if (tab.equals(tabName))
		{
%>
		      <td class="activetab"><nobr><%=org.apache.acf.ui.util.Encoder.bodyEscape(tab)%></nobr></td>
<%
		}
		else
		{
%>
		      <td class="passivetab"><nobr><a href="javascript:void(0);" alt='<%=org.apache.acf.ui.util.Encoder.attributeEscape(tab+" tab")%>' onclick='<%="javascript:SelectTab(\""+tab+"\");return false;"%>'><%=org.apache.acf.ui.util.Encoder.bodyEscape(tab)%></a></nobr></td>
<%
		}
	  }
%>
		      <td class="remaindertab">
<%
	  if (description.length() > 0)
	  {
%>
			  <nobr>Edit authority '<%=org.apache.acf.ui.util.Encoder.bodyEscape(description)%>'</nobr>
<%
	  }
	  else
	  {
%>
		          <nobr>Edit an Authority</nobr>
<%
	  }
%>
		      </td>
	      </tr>
	      <tr class="tabbodyrow">
		<td class="tabbody" colspan='<%=Integer.toString(tabsArray.size()+1)%>'>

<%

	  // Name tab
	  if (tabName.equals("Name"))
	  {
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="5"><hr/></td></tr>
			<tr>
				<td class="description"><nobr>Name:</nobr></td><td class="value" colspan="4">
<%
	    // If the connection doesn't exist yet, we are allowed to change the name.
	    if (connection == null)
	    {
%>
					<input type="text" size="32" name="connname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
	    }
	    else
	    {
%>
					<%=org.apache.acf.ui.util.Encoder.bodyEscape(connectionName)%>
					<input type="hidden" name="connname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
	    }
%>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Description:</nobr></td><td class="value" colspan="4">
					<input type="text" size="50" name="description" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(description)%>'/>
				</td>
			</tr>
		    </table>
<%
	  }
	  else
	  {
		// Hiddens for the Name tab
%>
		    <input type="hidden" name="connname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
		    <input type="hidden" name="description" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(description)%>'/>
<%
	  }


	  // "Type" tab
	  if (tabName.equals("Type"))
	  {
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="5"><hr/></td></tr>
			<tr>
				<td class="description"><nobr>Connection type:</nobr></td><td class="value" colspan="4">
<%
	    if (className.length() > 0)
	    {
		String value = connectorManager.getDescription(className);
		if (value == null)
		{
%>
					<nobr>UNREGISTERED <%=org.apache.acf.ui.util.Encoder.bodyEscape(className)%></nobr>
<%
		}
		else
		{
%>
					<%=org.apache.acf.ui.util.Encoder.bodyEscape(value)%>
<%
		}
%>
					<input type="hidden" name="classname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(className)%>'/>
<%
	    }
	    else
	    {
		int i = 0;
%>
					<select name="classname" size="1">
<%
		while (i < set.getRowCount())
		{
			IResultRow row = set.getRow(i++);
			String thisClassName = row.getValue("classname").toString();
			String thisDescription = row.getValue("description").toString();
%>
						<option value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(thisClassName)%>'
							<%=className.equals(thisClassName)?"selected=\"selected\"":""%>><%=org.apache.acf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
		}
%>
					</select>
<%
	    }
%>
				</td>
			</tr>
		    </table>
<%
	  }
	  else
	  {
		// Hiddens for the "Type" tab
%>
		    <input type="hidden" name="classname" value='<%=org.apache.acf.ui.util.Encoder.attributeEscape(className)%>'/>
<%
	  }


	  // The "Throttling" tab
	  if (tabName.equals("Throttling"))
	  {
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="5"><hr/></td></tr>
			<tr>
				<td class="description"><nobr>Max connections</nobr><br/><nobr>(per JVM):</nobr></td>
				<td class="value" colspan="4"><input type="text" size="6" name="maxconnections" value='<%=Integer.toString(maxConnections)%>'/></td>
			</tr>
		    </table>
<%
	  }
	  else
	  {
		// Hiddens for "Throttling" tab
%>
		    <input type="hidden" name="maxconnections" value='<%=Integer.toString(maxConnections)%>'/>
<%
	  }

	  if (className.length() > 0)
		AuthorityConnectorFactory.outputConfigurationBody(threadContext,className,new org.apache.acf.ui.jsp.JspWrapper(out),parameters,tabName);
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr><td class="message" colspan="4"><nobr>
<%
	  if (className.length() > 0)
	  {
%>
			    <input type="button" value="Save" onClick="javascript:Save()" alt="Save this authority connection"/>
<%
	  }
	  else
	  {
		if (tabName.equals("Type"))
		{
%>
			    <input type="button" value="Continue" onClick="javascript:Continue()" alt="Continue to next page"/>
<%
		}
	  }
%>
			    &nbsp;<input type="button" value="Cancel" onClick="javascript:Cancel()" alt="Cancel authority editing"/></nobr></td>
			</tr>
		    </table>
		</td>
	      </tr>
	    </table>
	</form>
<%
	}
%>
       </td>
      </tr>
    </table>

</body>

</html>

<%
    }
    catch (ACFException e)
    {
	e.printStackTrace();
	variableContext.setParameter("text",e.getMessage());
	variableContext.setParameter("target","listauthorities.jsp");
%>
	<jsp:forward page="error.jsp"/>
<%
    }
%>

