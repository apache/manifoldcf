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
	// Get the connection manager handle
	IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(threadContext);
	// Also get the list of available connectors
	IConnectorManager connectorManager = ConnectorManagerFactory.make(threadContext);
	IAuthorityConnectionManager authConnectionManager = AuthorityConnectionManagerFactory.make(threadContext);

	String tabName = variableContext.getParameter("tabname");
	if (tabName == null || tabName.length() == 0)
		tabName = "Name";

	// In case this form posts to itself, we need to pick up everything we can.
	String connectionName = variableContext.getParameter("connname");
	if (connectionName == null)
		connectionName = "";
	String description = "";
	String className = "";
	String authorityName = null;
	int maxConnections = 10;
	// Fetches per minute
	ArrayList throttles = new ArrayList();
	ConfigParams parameters = new ConfigParams();

	IRepositoryConnection connection = null;

	// If the connectionname is not null, load the connection description and prepopulate everything with what comes from it.
	if (connectionName != null && connectionName.length() > 0)
	{
		connection = connMgr.load(connectionName);
		if (connection != null)
		{
			// Set up values
			description = connection.getDescription();
			className = connection.getClassName();
			parameters = connection.getConfigParams();
			authorityName = connection.getACLAuthority();
			maxConnections = connection.getMaxConnections();
			String[] throttlesX = connection.getThrottles();
			int j = 0;
			while (j < throttlesX.length)
			{
				String throttleRegexp = throttlesX[j++];
				Map map = new HashMap();
				map.put("regexp",throttleRegexp);
				map.put("description",connection.getThrottleDescription(throttleRegexp));
				map.put("value",new Long((long)(((double)connection.getThrottleValue(throttleRegexp) * (double)60000.0) + 0.5)));
				throttles.add(map);
			}
		}
	}
	else
		connectionName = "";

	// Passed-in parameters override the current values
	String x;
	x = variableContext.getParameter("description");
	if (x != null)
		description = x;
	x = variableContext.getParameter("classname");
	if (x != null)
		className = x;
	x = variableContext.getParameter("authorityname");
	if (x != null && x.length() > 0)
	{
		if (x.equals("_none_"))
			authorityName = null;
		else
			authorityName = x;
	}
	x = variableContext.getParameter("maxconnections");
	if (x != null && x.length() > 0)
	{
		maxConnections = Integer.parseInt(x);
	}

	// Gather and edit the throttle stuff
	x = variableContext.getParameter("throttlecount");
	if (x != null)
	{
		int throttleCount = Integer.parseInt(x);
		throttles.clear();
		int j = 0;
		while (j < throttleCount)
		{
			Map map = new HashMap();
			String regexp = variableContext.getParameter("throttle_"+Integer.toString(j));
			map.put("regexp",regexp);
			String desc = variableContext.getParameter("throttledesc_"+Integer.toString(j));
			map.put("description",desc);
			String value = variableContext.getParameter("throttlevalue_"+Integer.toString(j));
			map.put("value",new Long(value));
			throttles.add(map);
			j++;
		}
		x = variableContext.getParameter("throttleop");
		if (x != null && x.equals("Delete"))
		{
			// Delete an item from the throttles list
			x = variableContext.getParameter("throttlenumber");
			throttles.remove(Integer.parseInt(x));
		}
		else if (x != null && x.equals("Add"))
		{
			// Add an item to the throttles list
			String regexp = variableContext.getParameter("throttle");
			String desc = variableContext.getParameter("throttledesc");
			Long value = new Long(variableContext.getParameter("throttlevalue"));
			Map newMap = new HashMap();
			newMap.put("regexp",regexp);
			newMap.put("description",desc);
			newMap.put("value",value);
			j = 0;
			while (j < throttles.size())
			{
				Map currentPos = (Map)throttles.get(j);
				String currentRegexp = (String)currentPos.get("regexp");
				int pos = regexp.compareTo(currentRegexp);
				if (pos == 0)
				{
					throttles.remove(j);
					throttles.add(j,newMap);
					break;
				}
				if (pos < 0)
				{
					throttles.add(j,newMap);
					break;
				}
				j++;
			}
			if (j == throttles.size())
				throttles.add(newMap);
		}
	}

	ArrayList tabsArray = new ArrayList();

	// Set up the predefined tabs
	tabsArray.add("Name");
	tabsArray.add("Type");
	if (className.length() > 0)
		tabsArray.add("Throttling");

%>

<%
	if (className.length() > 0)
	{
		String error = RepositoryConnectorFactory.processConfigurationPost(threadContext,className,variableContext,parameters);
		if (error != null)
		{
			variableContext.setParameter("text",error);
			variableContext.setParameter("target","listconnections.jsp");
%>
<jsp:forward page="error.jsp"/>
<%
		}
	}
%>

<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<link rel="StyleSheet" href="style.css" type="text/css" media="screen"/>
	<title>
		Lucene Connectors Framework: Edit Connection
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

	function DeleteThrottle(i)
	{
		document.editconnection.throttleop.value="Delete";
		document.editconnection.throttlenumber.value=i;
		postForm();
	}

	function AddThrottle()
	{
		if (!isInteger(editconnection.throttlevalue.value))
		{
			alert("Throttle rate must be an integer");
			document.editconnection.throttlevalue.focus();
			return;
		}
		if (!isRegularExpression(editconnection.throttle.value))
		{
			alert("The throttle expression must be a valid regular expression");
			editconnection.throttle.focus();
			return;
		}
		document.editconnection.throttleop.value="Add";
		postForm();
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
	RepositoryConnectorFactory.outputConfigurationHeader(threadContext,className,new org.apache.lcf.ui.jsp.JspWrapper(out),parameters,tabsArray);
%>

</head>

<body class="standardbody">

    <table class="page">
      <tr><td colspan="2" class="banner"><jsp:include page="banner.jsp" flush="true"/></td></tr>
      <tr><td class="navigation"><jsp:include page="navigation.jsp" flush="true"/></td>
       <td class="darkwindow">
<%
	// Get connector list; need this to decide what to do
	IResultSet set = connectorManager.getConnectors();
	if (set.getRowCount() == 0)
	{
%>
	<p class="windowtitle">Edit Repository Connection</p>
	<table class="displaytable"><tr><td class="message">No repository connectors registered</td></tr></table>
<%
	}
	else
	{
%>

	<form class="standardform" name="editconnection" action="execute.jsp" method="POST" enctype="multipart/form-data">
	  <input type="hidden" name="op" value="Continue"/>
	  <input type="hidden" name="type" value="connection"/>
	  <input type="hidden" name="tabname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(tabName)%>'/>
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
		      <td class="activetab"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(tab)%></nobr></td>
<%
		}
		else
		{
%>
		      <td class="passivetab"><nobr><a href="javascript:void(0);" alt='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(tab+" tab")%>' onclick='<%="javascript:SelectTab(\""+tab+"\");return false;"%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(tab)%></a></nobr></td>
<%
		}
	  }
%>
		      <td class="remaindertab">
<%
	  if (description.length() > 0)
	  {
%>
			  <nobr>Edit connection '<%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%>'</nobr>
<%
	  }
	  else
	  {
%>
		          <nobr>Edit a Connection</nobr>
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
					<input type="text" size="32" name="connname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
	    }
	    else
	    {
%>
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(connectionName)%>
					<input type="hidden" name="connname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
<%
	    }
%>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Description:</nobr></td><td class="value" colspan="4">
					<input type="text" size="50" name="description" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(description)%>'/>
				</td>
			</tr>
		    </table>
<%
	  }
	  else
	  {
		// Hiddens for the Name tab
%>
		    <input type="hidden" name="connname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(connectionName)%>'/>
		    <input type="hidden" name="description" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(description)%>'/>
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
					<nobr>UNREGISTERED <%=org.apache.lcf.ui.util.Encoder.bodyEscape(className)%></nobr>
<%
		}
		else
		{
%>
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(value)%>
<%
		}
%>
					<input type="hidden" name="classname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(className)%>'/>
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
						<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(thisClassName)%>'
							<%=className.equals(thisClassName)?"selected=\"selected\"":""%>><%=org.apache.lcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
		}
%>
					</select>
<%
	    }
%>
				</td>
			</tr>
			<tr>
				<td class="description"><nobr>Authority:</nobr></td>
				<td class="value" colspan="4">
<%
	    IAuthorityConnection[] set2 = authConnectionManager.getAllConnections();
	    int i = 0;
%>
					<select name="authorityname" size="1">
						<option value="_none_" <%=(authorityName==null)?"selected=\"selected\"":""%>>None (global authority)</option>
<%
	    while (i < set2.length)
	    {
		IAuthorityConnection row = set2[i++];
		String thisAuthorityName = row.getName();
		String thisDescription = row.getDescription();
		if (thisDescription == null || thisDescription.length() == 0)
			thisDescription = thisAuthorityName;
%>
						<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(thisAuthorityName)%>'
							<%=(authorityName!=null&&authorityName.equals(thisAuthorityName))?"selected=\"selected\"":""%>><%=org.apache.lcf.ui.util.Encoder.bodyEscape(thisDescription)%></option>
<%
	    }
%>
					</select>
				</td>
			</tr>
		    </table>
<%
	  }
	  else
	  {
		// Hiddens for the "Type" tab
%>
		    <input type="hidden" name="classname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(className)%>'/>
		    <input type="hidden" name="authorityname" value='<%=(authorityName==null)?"_none_":org.apache.lcf.ui.util.Encoder.attributeEscape(authorityName)%>'/>
<%
	  }


	  // The "Throttling" tab
%>
		    <input type="hidden" name="throttlecount" value='<%=Integer.toString(throttles.size())%>'/>
<%
	  if (tabName.equals("Throttling"))
	  {
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="2"><hr/></td></tr>
			<tr>
				<td class="description"><nobr>Max connections</nobr><br/><nobr>(per JVM):</nobr></td>
				<td class="value"><input type="text" size="6" name="maxconnections" value='<%=Integer.toString(maxConnections)%>'/></td>
			</tr>
			<tr>
				<td class="separator" colspan="2"><hr/></td>
			</tr>
			<tr>
				<td class="description" colspan="1"><nobr>Throttling:</nobr></td>
				<td class="boxcell" colspan="4">
					<input type="hidden" name="throttleop" value="Continue"/>
					<input type="hidden" name="throttlenumber" value=""/>
					<table class="formtable">
					    <tr class="formheaderrow">
						<td class="formcolumnheader"></td>
						<td class="formcolumnheader"><nobr>Bin regular expression</nobr></td>
						<td class="formcolumnheader"><nobr>Description</nobr></td>
						<td class="formcolumnheader"><nobr>Max avg fetches/min</nobr></td>
					    </tr>
<%
		int k = 0;
		while (k < throttles.size())
		{
			Map map = (Map)throttles.get(k);
			String regexp = (String)map.get("regexp");
			String desc = (String)map.get("description");
			if (desc == null)
				desc = "";
			Long value = (Long)map.get("value");
%>
					    <tr class='<%=((k % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><input type="button" value="Delete" alt='<%="Delete throttle "+org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>' onclick='<%="javascript:DeleteThrottle("+Integer.toString(k)+");"%>'/></td>
						<td class="formcolumncell">
						    <input type="hidden" name='<%="throttle_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
						    <input type="hidden" name='<%="throttledesc_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(desc)%>'/>
						    <input type="hidden" name='<%="throttlevalue_"+Integer.toString(k)%>' value='<%=value.toString()%>'/>
						    <nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
						</td>
						<td class="formcolumncell">
<%
			if (desc.length() > 0)
			{
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(desc)%>
<%
			}
%>
						</td>
						<td class="formcolumncell"><%=value.toString()%></td>
					    </tr>
<%
			k++;
		}
		if (k == 0)
		{
%>
					    <tr class="formrow"><td colspan="4" class="formcolumnmessage"><nobr>No throttling specified</nobr></td></tr>
<%
		}
%>
					    <tr class="formrow"><td colspan="4" class="formseparator"><hr/></td></tr>
					    <tr class="formrow">
						<td class="formcolumncell"><input type="button" value="Add" alt="Add throttle" onclick="javascript:AddThrottle();"/></td>
						<td class="formcolumncell"><input type="text" name="throttle" size="30" value=""/></td>
						<td class="formcolumncell"><input type="text" name="throttledesc" size="30" value=""/></td>
						<td class="formcolumncell"><input type="text" name="throttlevalue" size="5" value=""/></td>
					    </tr>
					</table>
				</td>
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
		int k = 0;
		while (k < throttles.size())
		{
			Map map = (Map)throttles.get(k);
			String regexp = (String)map.get("regexp");
			String desc = (String)map.get("description");
			if (desc == null)
				desc = "";
			Long value = (Long)map.get("value");
%>
		    <input type="hidden" name='<%="throttle_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
		    <input type="hidden" name='<%="throttledesc_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(desc)%>'/>
		    <input type="hidden" name='<%="throttlevalue_"+Integer.toString(k)%>' value='<%=value.toString()%>'/>
<%
			k++;
		}
	  }

	  if (className.length() > 0)
		RepositoryConnectorFactory.outputConfigurationBody(threadContext,className,new org.apache.lcf.ui.jsp.JspWrapper(out),parameters,tabName);
%>
		    <table class="displaytable">
			<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr><td class="message" colspan="4"><nobr>
<%
	  if (className.length() > 0)
	  {
%>
			    <input type="button" value="Save" onClick="javascript:Save()" alt="Save this connection"/>
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
			    &nbsp;<input type="button" value="Cancel" onClick="javascript:Cancel()" alt="Cancel connection editing"/></nobr></td>
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
    catch (LCFException e)
    {
	e.printStackTrace();
	variableContext.setParameter("text",e.getMessage());
	variableContext.setParameter("target","listconnections.jsp");
%>
	<jsp:forward page="error.jsp"/>
<%
    }
%>

