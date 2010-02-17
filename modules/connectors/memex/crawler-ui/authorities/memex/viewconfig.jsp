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
	// This file is included by every place that the configuration information for the connector
	// needs to be viewed.  When it is called, the Parameters object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
%>

<%
	if (parameters == null)
		out.println("Hey!  No parameters came in!!!");
%>
<table class="displaytable">
	<tr>
		<td class="description" colspan="1"><nobr>Parameters:</nobr></td>
		<td class="value" colspan="3">
<%
		Iterator iter = parameters.listParameters();
		while (iter.hasNext())
		{
			String param = (String)iter.next();
			String value = parameters.getParameter(param);
			if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
			{
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(param)+"=********"%></nobr><br/>
<%
			}
			else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
			{
				IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(param)+"=<"+Integer.toString(kmanager.getContents().length)+" certificate(s)>"%></nobr><br/>
<%
			}
			else
			{
%>
			<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.lcf.ui.util.Encoder.bodyEscape(value)%></nobr><br/>
<%
			}
		}
%>

		</td>
	</tr>
</table>
