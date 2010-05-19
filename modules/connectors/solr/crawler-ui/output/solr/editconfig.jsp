<%@ include file="../../adminDefaults.jsp" %>

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
	// This file is included by every place that the configuration information for the GTS output connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.


	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String protocol = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);
	if (protocol == null)
		protocol = "http";
		
	String server = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_SERVER);
	if (server == null)
		server = "localhost";

	String port = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PORT);
	if (port == null)
		port = "8983";

	String webapp = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_WEBAPPNAME);
	if (webapp == null)
		webapp = "solr";

	String updatePath = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_UPDATEPATH);
	if (updatePath == null)
		updatePath = "/update/extract";

	String removePath = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_REMOVEPATH);
	if (removePath == null)
		removePath = "/update";

	String statusPath = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_STATUSPATH);
	if (statusPath == null)
		statusPath = "/admin/ping";

	String realm = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_REALM);
	if (realm == null)
		realm = "";

	String userID = parameters.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_USERID);
	if (userID == null)
		userID = "";
		
	String password = parameters.getObfuscatedParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PASSWORD);
	if (password == null)
		password = "";
		
	// "Appliance" tab
	if (tabName.equals("Server"))
	{
%>
<table class="displaytable">
	<tr>
		<td class="description"><nobr>Protocol:</nobr></td>
		<td class="value">
			<select name="serverprotocol">
				<option value="http"<%=(protocol.equals("http")?" selected=\"true\"":"")%>>http</option>
				<option value="https"<%=(protocol.equals("https")?" selected=\"true\"":"")%>>https</option>
			</select>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Server name:</nobr></td>
		<td class="value">
			<input name="servername" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(server)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Port:</nobr></td>
		<td class="value">
			<input name="serverport" type="text" size="5" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(port)%>'/>
		</td>
	</tr>
	<tr><td colspan="2" class="separator"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Web application  name:</nobr></td>
		<td class="value">
			<input name="webappname" type="text" size="16" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(webapp)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Update handler:</nobr></td>
		<td class="value">
			<input name="updatepath" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(updatePath)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Remove handler:</nobr></td>
		<td class="value">
			<input name="removepath" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(removePath)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Status handler:</nobr></td>
		<td class="value">
			<input name="statuspath" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(statusPath)%>'/>
		</td>
	</tr>
	<tr><td colspan="2" class="separator"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Realm:</nobr></td>
		<td class="value">
			<input name="realm" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(realm)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>User ID:</nobr></td>
		<td class="value">
			<input name="userid" type="text" size="32" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userID)%>'/>
		</td>
	</tr>
	<tr>
		<td class="description"><nobr>Password:</nobr></td>
		<td class="value">
			<input type="password" size="32" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Server tab hiddens
%>
<input type="hidden" name="serverprotocol" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(protocol)%>'/>
<input type="hidden" name="servername" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(server)%>'/>
<input type="hidden" name="serverport" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(port)%>'/>
<input type="hidden" name="webappname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(webapp)%>'/>
<input type="hidden" name="updatepath" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(updatePath)%>'/>
<input type="hidden" name="removepath" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(removePath)%>'/>
<input type="hidden" name="statuspath" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(statusPath)%>'/>
<input type="hidden" name="realm" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(realm)%>'/>
<input type="hidden" name="userid" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userID)%>'/>
<input type="hidden" name="password" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
<%
	}

	// Prepare for the argument tab
	Map argumentMap = new HashMap();
	int i = 0;
	while (i < parameters.getChildCount())
	{
		ConfigNode sn = parameters.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.agents.output.solr.SolrConfig.NODE_ARGUMENT))
		{
			String name = sn.getAttributeValue(org.apache.lcf.agents.output.solr.SolrConfig.ATTRIBUTE_NAME);
			String value = sn.getAttributeValue(org.apache.lcf.agents.output.solr.SolrConfig.ATTRIBUTE_VALUE);
			ArrayList values = (ArrayList)argumentMap.get(name);
			if (values == null)
			{
				values = new ArrayList();
				argumentMap.put(name,values);
			}
			values.add(value);
		}
	}
	// "Arguments" tab
	if (tabName.equals("Arguments"))
	{
		// For the display, sort the arguments into alphabetic order
		String[] sortArray = new String[argumentMap.size()];
		i = 0;
		Iterator iter = argumentMap.keySet().iterator();
		while (iter.hasNext())
		{
			sortArray[i++] = (String)iter.next();
		}
		java.util.Arrays.sort(sortArray);
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Arguments:</nobr></td>
		<td class="boxcell">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"></td>
					<td class="formcolumnheader"><nobr>Name</nobr></td>
					<td class="formcolumnheader"><nobr>Value</nobr></td>
				</tr>
<%
		i = 0;
		int k = 0;
		while (k < sortArray.length)
		{
			String name = sortArray[k++];
			ArrayList values = (ArrayList)argumentMap.get(name);
			int j = 0;
			while (j < values.size())
			{
				String value = (String)values.get(j++);
				// Its prefix will be...
				String prefix = "argument_" + Integer.toString(i);
%>
				<tr class='<%=((i % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'><input type="button" value="Delete" alt='<%="Delete argument #"+Integer.toString(i+1)%>' onclick='<%="javascript:deleteArgument("+Integer.toString(i)+");"%>'/>
						<input type="hidden" name='<%=prefix+"_op"%>' value="Continue"/>
						<input type="hidden" name='<%=prefix+"_name"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(name)%>'/>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(name)%></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name='<%=prefix+"_value"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'</nobr>
					</td>
				</tr>
<%
				i++;
			}
		}
		if (i == 0)
		{
%>
				<tr class="formrow"><td class="formmessage" colspan="3">No arguments specified</td></tr>
<%
		}
%>
				<tr class="formrow"><td class="formseparator" colspan="3"><hr/></td></tr>
				<tr class="formrow">
					<td class="formcolumncell">
						<a name="argument"><input type="button" value="Add" alt="Add argument" onclick="javascript:addArgument();"/></a>
						<input type="hidden" name="argument_count" value='<%=i%>'/>
						<input type="hidden" name="argument_op" value="Continue"/>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="argument_name" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="argument_value" value=""/></nobr>
					</td>
				</tr>
			</table>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Emit hiddens for argument tab
		i = 0;
		Iterator iter = argumentMap.keySet().iterator();
		while (iter.hasNext())
		{
			String name = (String)iter.next();
			ArrayList values = (ArrayList)argumentMap.get(name);
			int j = 0;
			while (j < values.size())
			{
				String value = (String)values.get(j++);
				// It's prefix will be...
				String prefix = "argument_" + Integer.toString(i++);
%>
<input type="hidden" name='<%=prefix+"_name"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(name)%>'/>
<input type="hidden" name='<%=prefix+"_value"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'/>
<%

			}
		}
%>
<input type="hidden" name="argument_count" value='<%=i%>'/>
<%
	}
%>
