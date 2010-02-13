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
	// This file is included in the head section by every place that the configuration information for the rss connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editconfig.jsp for this connector.
	//
	// The method checkConfigOnSave() is called prior to the form being submitted for save.  It should return false if the
	// form should not be submitted.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");

	if (parameters == null)
		out.println("No parameters!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Email");
	tabsArray.add("Robots");
	tabsArray.add("Bandwidth");
	tabsArray.add("Access Credentials");
	tabsArray.add("Certificates");

%>

<script type="text/javascript">
<!--
	function checkConfig()
	{
		if (editconnection.email.value != "" && editconnection.email.value.indexOf("@") == -1)
		{
			alert("Need a valid email address");
			editconnection.email.focus();
			return false;
		}

		// If the Bandwidth tab is up, check to be sure we have valid numbers and regexps everywhere.
		var i = 0;
		var count = editconnection.bandwidth_count.value;
		while (i < count)
		{
			var connections = eval("editconnection.connections_bandwidth_"+i+".value");
			if (connections != "" && !isInteger(connections))
			{
				alert("Maximum connections must be an integer");
				eval("editconnection.connections_bandwidth_"+i+".focus()");
				return false;
			}
			var rate = eval("editconnection.rate_bandwidth_"+i+".value");
			if (rate != "" && !isInteger(rate))
			{
				alert("Maximum Kbytes per second must be an integer");
				eval("editconnection.rate_bandwidth_"+i+".focus()");
				return false;
			}
			var fetches = eval("editconnection.fetches_bandwidth_"+i+".value");
			if (fetches != "" && !isInteger(fetches))
			{
				alert("Maximum fetches per minute must be an integer");
				eval("editconnection.fetches_bandwidth_"+i+".focus()");
				return false;
			}

			i = i + 1;
		}
		
		// Make sure access credentials are all legal
		i = 0;
		count = editconnection.acredential_count.value;
		while (i < count)
		{
			var username = eval("editconnection.username_acredential_"+i+".value");
			if (username == "")
			{
				alert("Credential must have non-null user name");
				eval("editconnection.username_acredential_"+i+".focus()");
				return false;
			}
			i = i + 1;
		}

		// Make sure session credentials are all legal
		i = 0;
		count = editconnection.scredential_count.value;
		while (i < count)
		{
			var loginpagecount = eval("editconnection.scredential_"+i+"_loginpagecount.value");
			var j = 0;
			while (j < loginpagecount)
			{
				var matchregexp = eval("editconnection.scredential_"+i+"_"+j+"_matchregexp.value");
				if (!isRegularExpression(matchregexp))
				{
                                        alert("Match expression must be a valid regular expression");
                                        eval("editconnection.scredential_"+i+"_"+j+"_matchregexp.focus()");
                                        return false;
				}
				if (eval("editconnection.scredential_"+i+"_"+j+"_type.value") == "form")
				{
					var paramcount = eval("editconnection.scredential_"+i+"_"+j+"_loginparamcount.value");
					var k = 0;
					while (k < paramcount)
					{
						var paramname = eval("editconnection.scredential_"+i+"_"+j+"_"+k+"_param.value");
						if (paramname == "")
						{
							alert("Parameter must have non-empty name");
							eval("editconnection.scredential_"+i+"_"+j+"_"+k+"_param.focus()");
							return false;
						}
						var paramvalue = eval("editconnection.scredential_"+i+"_"+j+"_"+k+"_value.value");
						var parampassword = eval("editconnection.scredential_"+i+"_"+j+"_"+k+"_password.value");
						if (paramvalue != "" && parampassword != "")
						{
							alert("Parameter can either be hidden or not, but can't be both");
							eval("editconnection.scredential_"+i+"_"+j+"_"+k+"_value.focus()");
							return false;
						}
						k = k + 1;
					}
				}
				j = j + 1;
			}
			i = i + 1;
		}
		return true;
	}

	function checkConfigForSave()
	{
		if (editconnection.email.value == "")
		{
			alert("Email address required, to be included in all request headers");
			SelectTab("Email");
			editconnection.email.focus();
			return false;
		}
		return true;
	}

	function deleteRegexp(i)
	{
		// Set the operation
		eval("editconnection.op_bandwidth_"+i+".value=\"Delete\"");
		// Submit
		if (editconnection.bandwidth_count.value==i)
			postFormSetAnchor("bandwidth");
		else
			postFormSetAnchor("bandwidth_"+i)
		// Undo, so we won't get two deletes next time
		eval("editconnection.op_bandwidth_"+i+".value=\"Continue\"");
	}

	function addRegexp()
	{
		if (editconnection.connections_bandwidth.value != "" && !isInteger(editconnection.connections_bandwidth.value))
		{
			alert("Maximum connections must be an integer");
			editconnection.connections_bandwidth.focus();
			return;
		}
		if (editconnection.rate_bandwidth.value != "" && !isInteger(editconnection.rate_bandwidth.value))
		{
			alert("Maximum Kbytes per second must be an integer");
			editconnection.rate_bandwidth.focus();
			return;
		}
		if (editconnection.fetches_bandwidth.value != "" && !isInteger(editconnection.fetches_bandwidth.value))
		{
			alert("Maximum fetches per minute must be an integer");
			editconnection.fetches_bandwidth.focus();
			return;
		}
		if (!isRegularExpression(editconnection.regexp_bandwidth.value))
		{
			alert("A valid regular expression is required");
			editconnection.regexp_bandwidth.focus();
			return;
		}
		editconnection.bandwidth_op.value="Add";
		postFormSetAnchor("bandwidth");
	}

	function deleteARegexp(i)
	{
		// Set the operation
		eval("editconnection.op_acredential_"+i+".value=\"Delete\"");
		// Submit
		if (editconnection.acredential_count.value==i)
			postFormSetAnchor("acredential");
		else
			postFormSetAnchor("acredential_"+i)
		// Undo, so we won't get two deletes next time
		eval("editconnection.op_acredential_"+i+".value=\"Continue\"");
	}

	function addARegexp()
	{
		if (editconnection.username_acredential.value == "")
		{
			alert("Credential must include a non-null user name");
			editconnection.username_acredential.focus();
			return;
		}
		if (!isRegularExpression(editconnection.regexp_acredential.value))
		{
			alert("A valid regular expression is required");
			editconnection.regexp_acredential.focus();
			return;
		}
		editconnection.acredential_op.value="Add";
		postFormSetAnchor("acredential");
	}

	function deleteSRegexp(i)
	{
		// Set the operation
		eval("editconnection.scredential_"+i+"_op.value=\"Delete\"");
		// Submit
		if (editconnection.scredential_count.value==i)
			postFormSetAnchor("scredential");
		else
			postFormSetAnchor("scredential_"+i)
		// Undo, so we won't get two deletes next time
		eval("editconnection.scredential_"+i+"_op.value=\"Continue\"");
	}

	function addSRegexp()
	{
		if (!isRegularExpression(editconnection.scredential_regexp.value))
		{
			alert("A valid regular expression is required");
			editconnection.scredential_regexp.focus();
			return;
		}
		editconnection.scredential_op.value="Add";
		postFormSetAnchor("scredential");
	}

	function deleteLoginPage(credential,loginpage)
	{
		// Set the operation
		eval("editconnection.scredential_"+credential+"_"+loginpage+"_op.value=\"Delete\"");
		// Submit
		if (eval("editconnection.scredential_"+credential+"_loginpagecount.value")==credential)
			postFormSetAnchor("scredential_loginpage");
		else
			postFormSetAnchor("scredential_"+credential+"_"+loginpage)
		// Undo, so we won't get two deletes next time
		eval("editconnection.scredential_"+credential+"_"+loginpage+"_op.value=\"Continue\"");

	}
	
	function addLoginPage(credential)
	{
		if (!isRegularExpression(eval("editconnection.scredential_"+credential+"_loginpageregexp.value")))
		{
			alert("A valid regular expression is required");
			eval("editconnection.scredential_"+credential+"_loginpageregexp.focus()");
			return;
		}
                if (!isRegularExpression(eval("editconnection.scredential_"+credential+"_loginpagematchregexp.value")))
                {
                        alert("A valid regular expression is required");
			eval("editconnection.scredential_"+credential+"_loginpagematchregexp.focus()");
			return;
                }
		eval("editconnection.scredential_"+credential+"_loginpageop.value=\"Add\"");
		postFormSetAnchor("scredential_"+credential);
	}
	
	function deleteLoginPageParameter(credential,loginpage,parameter)
	{
		// Set the operation
		eval("editconnection.scredential_"+credential+"_"+loginpage+"_"+parameter+"_op.value=\"Delete\"");
		// Submit
		if (eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamcount.value")==credential)
			postFormSetAnchor("scredential_"+credential+"_loginparam");
		else
			postFormSetAnchor("scredential_"+credential+"_"+loginpage+"_"+parameter)
		// Undo, so we won't get two deletes next time
		eval("editconnection.scredential_"+credential+"_"+loginpage+"_"+parameter+"_op.value=\"Continue\"");
	}
	
	function addLoginPageParameter(credential,loginpage)
	{
		if (!isRegularExpression(eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamname.value")))
		{
			alert("Parameter name must be a regular expression");
			eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamname.focus()");
			return;
		}
		if (eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamvalue.value") != "" && eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparampassword.value") != "")
		{
			alert("Parameter can either be hidden or not but can't be both");
			eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamvalue.focus()");
			return;
		}
		eval("editconnection.scredential_"+credential+"_"+loginpage+"_loginparamop.value=\"Add\"");
		postFormSetAnchor("scredential_"+credential+"_"+loginpage);
	}
	
	function deleteTRegexp(i)
	{
		// Set the operation
		eval("editconnection.op_trust_"+i+".value=\"Delete\"");
		// Submit
		if (editconnection.trust_count.value==i)
			postFormSetAnchor("trust");
		else
			postFormSetAnchor("trust_"+i)
		// Undo, so we won't get two deletes next time
		eval("editconnection.op_trust_"+i+".value=\"Continue\"");
	}

	function addTRegexp()
	{
		if (editconnection.certificate_trust.value == "" && editconnection.all_trust.checked == false)
		{
			alert("Specify a trust certificate file to upload first, or check 'Trust everything'");
			editconnection.certificate_trust.focus();
			return;
		}
		if (!isRegularExpression(editconnection.regexp_trust.value))
		{
			alert("A valid regular expression is required");
			editconnection.regexp_trust.focus();
			return;
		}
		editconnection.trust_op.value="Add";
		postFormSetAnchor("trust");
	}
	
//-->
</script>

