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
	// This file is included by every place that the configuration information for the rss connector
	// needs to be edited.  When it is called, the parameter Map object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section, and within a form.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
	String tabName = (String)threadContext.get("TabName");

	if (parameters == null)
		out.println("No parameter map!!!");
	if (tabName == null)
		out.println("No tab name!");

	String email = parameters.getParameter(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_EMAIL);
	if (email == null)
		email = "";
	String robotsUsage = parameters.getParameter(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_ROBOTSUSAGE);
	if (robotsUsage == null)
		robotsUsage = "all";

	// Email tab
	if (tabName.equals("Email"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Email address to contact:</nobr></td><td class="value"><input type="text" size="32" name="email" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(email)%>'/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="email" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(email)%>'/>
<%
	}

	// Robots tab
	if (tabName.equals("Robots"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Robots.txt usage:</nobr></td>
		<td class="value">
		    <select name="robotsusage" size="3">
			<option value="none" <%=robotsUsage.equals("none")?"selected=\"selected\"":""%>>Don't look at robots.txt</option>
			<option value="data" <%=robotsUsage.equals("data")?"selected=\"selected\"":""%>>Obey robots.txt for data fetches only</option>
			<option value="all" <%=robotsUsage.equals("all")?"selected=\"selected\"":""%>>Obey robots.txt for all fetches</option>
		    </select>
		</td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="robotsusage" value='<%=robotsUsage%>'/>
<%
	}

	// Bandwidth tab
	if (tabName.equals("Bandwidth"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Throttles:</nobr></td>
		<td class="boxcell">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"></td>
					<td class="formcolumnheader"><nobr>Bin regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Case insensitive?</nobr></td>
					<td class="formcolumnheader"><nobr>Max connections</nobr></td>
					<td class="formcolumnheader"><nobr>Max Kbytes/sec</nobr></td>
					<td class="formcolumnheader"><nobr>Max fetches/min</nobr></td>
				</tr>
<%
		int i = 0;
		int binCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC))
			{
				// A bin description node!  Look for all its parameters.
				String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_BINREGEXP);
				String isCaseInsensitive = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_INSENSITIVE);
				String maxConnections = null;
				String maxKBPerSecond = null;
				String maxFetchesPerMinute = null;
				int j = 0;
				while (j < cn.getChildCount())
				{
					ConfigNode childNode = cn.getChild(j++);
					if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXCONNECTIONS))
						maxConnections = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
					else if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXKBPERSECOND))
						maxKBPerSecond = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
					else if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
						maxFetchesPerMinute = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
				}
				if (maxConnections == null)
					maxConnections = "";
				if (maxKBPerSecond == null)
					maxKBPerSecond = "";
				if (maxFetchesPerMinute == null)
					maxFetchesPerMinute = "";
					
				if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
					isCaseInsensitive = "false";

				// It's prefix will be...
				String prefix = "bandwidth_" + Integer.toString(binCounter);
%>
				<tr class='<%=((binCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'><input type="button" value="Delete" alt='<%="Delete bin regular expression #"+Integer.toString(binCounter+1)%>' onclick='<%="javascript:deleteRegexp("+Integer.toString(binCounter)+");"%>'/>
						<input type="hidden" name='<%="op_"+prefix%>' value="Continue"/>
						<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/></a>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="checkbox" name='<%="insensitive_"+prefix%>' value="true" <%=(isCaseInsensitive.equals("true"))?"checked=\"\"":""%> /></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name='<%="connections_"+prefix%>' value='<%=maxConnections%>'/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name='<%="rate_"+prefix%>' value='<%=maxKBPerSecond%>'/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name='<%="fetches_"+prefix%>' value='<%=maxFetchesPerMinute%>'/></nobr>
					</td>
				</tr>
<%
				binCounter++;
			}
		}

		if (binCounter == 0)
		{
%>
				<tr class="formrow"><td class="formmessage" colspan="6">No bandwidth or connection throttling specified</td></tr>
<%
		}
%>
				<tr class="formrow"><td class="formseparator" colspan="6"><hr/></td></tr>
				<tr class="formrow">
					<td class="formcolumncell">
						<a name="bandwidth"><input type="button" value="Add" alt="Add bin regular expression" onclick="javascript:addRegexp();"/></a>
						<input type="hidden" name="bandwidth_count" value='<%=binCounter%>'/>
						<input type="hidden" name="bandwidth_op" value="Continue"/>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="regexp_bandwidth" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="checkbox" name="insensitive_bandwidth" value="true"/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name="connections_bandwidth" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name="rate_bandwidth" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="5" name="fetches_bandwidth" value=""/></nobr>
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
		// Hiddens for bandwidth tab.
		int i = 0;
		int binCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC))
			{
				// A bin description node!  Look for all its parameters.
				String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_BINREGEXP);
				String isCaseInsensitive = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_INSENSITIVE);
				String maxConnections = null;
				String maxKBPerSecond = null;
				String maxFetchesPerMinute = null;
				int j = 0;
				while (j < cn.getChildCount())
				{
					ConfigNode childNode = cn.getChild(j++);
					if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXCONNECTIONS))
						maxConnections = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
					else if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXKBPERSECOND))
						maxKBPerSecond = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
					else if (childNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
						maxFetchesPerMinute = childNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
				}
				if (maxConnections == null)
					maxConnections = "";
				if (maxKBPerSecond == null)
					maxKBPerSecond = "";
				if (maxFetchesPerMinute == null)
					maxFetchesPerMinute = "";
				if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
					isCaseInsensitive = "false";

				// It's prefix will be...
				String prefix = "bandwidth_" + Integer.toString(binCounter);
%>
<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
<input type="hidden" name='<%="insensitive_"+prefix%>' value='<%=isCaseInsensitive%>'/>
<input type="hidden" name='<%="connections_"+prefix%>' value='<%=maxConnections%>'/>
<input type="hidden" name='<%="rate_"+prefix%>' value='<%=maxKBPerSecond%>'/>
<input type="hidden" name='<%="fetches_"+prefix%>' value='<%=maxFetchesPerMinute%>'/>
<%
				binCounter++;
			}
		}
%>
<input type="hidden" name="bandwidth_count" value='<%=binCounter%>'/>
<%
	}

	// Access Credentials tab
	if (tabName.equals("Access Credentials"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Page access credentials:</nobr></td>
		<td class="boxcell">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"></td>
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Authentication type</nobr></td>
					<td class="formcolumnheader"><nobr>Domain</nobr></td>
					<td class="formcolumnheader"><nobr>User name</nobr></td>
					<td class="formcolumnheader"><nobr>Password</nobr></td>
				</tr>
<%
		int i = 0;
		int accessCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
			{
				// A bin description node!  Look for all its parameters.
				String type = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
                                if (!type.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
                                {
                                        String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
                                        String domain = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_DOMAIN);
                                        if (domain == null)
                                                domain = "";
                                        String userName = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_USERNAME);
                                        String password = org.apache.lcf.crawler.system.LCF.deobfuscate(cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD));
                                        
                                        // It's prefix will be...
                                        String prefix = "acredential_" + Integer.toString(accessCounter);
%>
				<tr class='<%=((accessCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'><input type="button" value="Delete" alt='<%="Delete page authentication url regular expression #"+Integer.toString(accessCounter+1)%>' onclick='<%="javascript:deleteARegexp("+Integer.toString(accessCounter)+");"%>'/>
						<input type="hidden" name='<%="op_"+prefix%>' value="Continue"/>
						<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/></a>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="radio" name='<%="type_"+prefix%>' value="basic" <%=(type.equals("basic"))?"checked=\"\"":""%> />&nbsp;Basic authentication</nobr><br/>
						<nobr><input type="radio" name='<%="type_"+prefix%>' value="ntlm" <%=(type.equals("ntlm"))?"checked=\"\"":""%> />&nbsp;NTLM authentication</nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="16" name='<%="domain_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(domain)%>'/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="16" name='<%="username_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="password" size="16" name='<%="password_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/></nobr>
					</td>
				</tr>
<%
                                        accessCounter++;
                                }
                        }
                }

		if (accessCounter == 0)
		{
%>
				<tr class="formrow"><td class="formmessage" colspan="6">No page access credentials specified</td></tr>
<%
		}
%>
				<tr class="formrow"><td class="formseparator" colspan="6"><hr/></td></tr>
				<tr class="formrow">
					<td class="formcolumncell">
						<a name="acredential"><input type="button" value="Add" alt="Add page authentication url regular expression" onclick="javascript:addARegexp();"/></a>
						<input type="hidden" name="acredential_count" value='<%=accessCounter%>'/>
						<input type="hidden" name="acredential_op" value="Continue"/>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="regexp_acredential" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="radio" name="type_acredential" value="basic" checked="" />&nbsp;Basic authentication</nobr><br/>
						<nobr><input type="radio" name="type_acredential" value="ntlm" />&nbsp;NTLM authentication</nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="16" name="domain_acredential" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="16" name="username_acredential" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><input type="password" size="16" name="password_acredential" value=""/></nobr>
					</td>
				</tr>
			</table>
		</td>
	</tr>
        
	<tr><td class="separator" colspan="2"><hr/></td></tr>

	<tr>
		<td class="description"><nobr>Session-based access credentials:</nobr></td>
		<td class="boxcell">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"></td>
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Login pages</nobr></td>
				</tr>
<%
		i = 0;
		accessCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
			{
				// A bin description node!  Look for all its parameters.
				String type = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
                                if (type.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
                                {
                                        String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
                                        
                                        // It's prefix will be...
                                        String prefix = "scredential_" + Integer.toString(accessCounter);
%>
				<tr class='<%=((accessCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'>
							<input type="button" value="Delete" alt='<%="Delete session authentication url regular expression #"+Integer.toString(accessCounter+1)%>' onclick='<%="javascript:deleteSRegexp("+Integer.toString(accessCounter)+");"%>'/>
							<input type="hidden" name='<%=prefix+"_op"%>' value="Continue"/>
							<input type="hidden" name='<%=prefix+"_regexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
						</a>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
					</td>
					<td class="boxcell">
						<table class="formtable">
							<tr class="formheaderrow">
								<td class="formcolumnheader"></td>
								<td class="formcolumnheader"><nobr>Login URL regular expression</nobr></td>
								<td class="formcolumnheader"><nobr>Page type</nobr></td>
								<td class="formcolumnheader"><nobr>Form name/link target regular expression</nobr></td>
								<td class="formcolumnheader"><nobr>Override form parameters</nobr></td>
							</tr>
<%
					int q = 0;
					int authPageCounter = 0;
					while (q < cn.getChildCount())
					{
						ConfigNode authPageNode = cn.getChild(q++);
						if (authPageNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPAGE))
						{
							String pageRegexp = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
							String pageType = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
							String matchRegexp = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_MATCHREGEXP);
							if (matchRegexp == null)
								matchRegexp = "";
							String authpagePrefix = prefix + "_" + authPageCounter;
%>
							<tr class='<%=((authPageCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
								<td class="formcolumncell">
									<a name='<%=authpagePrefix%>'>
										<input type="button" value="Delete" alt='<%="Delete login page #"+(authPageCounter+1)+" for url regular expression #"+Integer.toString(accessCounter+1)%>' onclick='<%="javascript:deleteLoginPage("+Integer.toString(accessCounter)+","+Integer.toString(authPageCounter)+");"%>'/>
										<input type="hidden" name='<%=authpagePrefix+"_op"%>' value="Continue"/>
										<input type="hidden" name='<%=authpagePrefix+"_regexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pageRegexp)%>'/>
										<input type="hidden" name='<%=authpagePrefix+"_type"%>' value='<%=pageType%>'/>
									</a>
								</td>

								<td class="formcolumncell"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(pageRegexp)%></nobr></td>
								<td class="formcolumncell"><nobr><%=pageType%></nobr></td>
								<td class="formcolumncell"><nobr><input type="text" size="30" name='<%=authpagePrefix+"_matchregexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(matchRegexp)%>'/></nobr></td>
<%
							if (pageType.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_FORM))
							{
%>
								<td class="boxcell">
									<table class="formtable">
										<tr class="formheaderrow">
											<td class="formcolumnheader"></td>
											<td class="formcolumnheader"><nobr>Parameter regular expression</nobr></td>
											<td class="formcolumnheader"><nobr>Value</nobr></td>
											<td class="formcolumnheader"><nobr>Password</nobr></td>
										</tr>
<%
								int z = 0;
								int paramCounter = 0;
								while (z < authPageNode.getChildCount())
								{
									ConfigNode paramNode = authPageNode.getChild(z++);
									if (paramNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPARAMETER))
									{
										String param = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_NAMEREGEXP);
										if (param == null)
											param = "";
										String value = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
										if (value == null)
											value = "";
										String password = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD);
										if (password == null)
											password = "";
										String authParamPrefix = authpagePrefix + "_" + paramCounter;
%>
										<tr class='<%=((paramCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
											<td class="formcolumncell">
												<a name='<%=authParamPrefix%>'>
													<input type="button" value="Delete" alt='<%="Delete parameter #"+(paramCounter+1)+" for login page #"+(authPageCounter+1)+" for credential #"+(accessCounter+1)%>' onclick='<%="javascript:deleteLoginPageParameter("+accessCounter+","+authPageCounter+","+paramCounter+");"%>'/>
													<input type="hidden" name='<%=authParamPrefix+"_op"%>' value="Continue"/>
												</a>
											</td>
											<td class="formcolumncell">
												<nobr><input type="text" size="30" name='<%=authParamPrefix+"_param"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(param)%>'/></nobr>
											</td>
											<td class="formcolumncell">
												<nobr><input type="text" size="15" name='<%=authParamPrefix+"_value"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'/></nobr>
											</td>
											<td class="formcolumncell">
												<nobr><input type="password" size="15" name='<%=authParamPrefix+"_password"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(org.apache.lcf.crawler.system.LCF.deobfuscate(password))%>'/></nobr>
											</td>
										</tr>
<%
										paramCounter++;
									}
								}
%>
										<tr class="formrow"><td class="formseparator" colspan="4"><hr/></td></tr>
										<tr class="formrow">
											<td class="formcolumncell">
												<a name='<%=authpagePrefix+"_loginparam"%>'><input type="button" value="Add" alt='<%="Add parameter to login page #"+(authPageCounter+1)+" for credential #"+(accessCounter+1)%>' onclick='<%="javascript:addLoginPageParameter("+accessCounter+","+authPageCounter+");"%>'/></a>
												<input type="hidden" name='<%=authpagePrefix+"_loginparamcount"%>' value='<%=paramCounter%>'/>
												<input type="hidden" name='<%=authpagePrefix+"_loginparamop"%>' value="Continue"/>
											</td>
											<td class="formcolumncell">
												<nobr><input type="text" size="30" name='<%=authpagePrefix+"_loginparamname"%>' value=""/></nobr>
											</td>
											<td class="formcolumncell">
												<nobr><input type="text" size="15" name='<%=authpagePrefix+"_loginparamvalue"%>' value=""/></nobr>
											</td>
											<td class="formcolumncell">
												<nobr><input type="password" size="15" name='<%=authpagePrefix+"_loginparampassword"%>' value=""/></nobr>
											</td>
										</tr>
									</table>
								</td>
<%
							}
							else
							{
%>
								<td class="formcolumncell"></td>
<%
							}
%>
							</tr>
<%
							authPageCounter++;
						}
					}
%>
							<tr class="formrow"><td class="formseparator" colspan="5"><hr/></td></tr>
							<tr class="formrow">
								<td class="formcolumncell">
									<a name='<%=prefix+"_loginpage"%>'><input type="button" value="Add" alt='<%="Add login page to credential #"+(accessCounter+1)%>' onclick='<%="javascript:addLoginPage("+accessCounter+");"%>'/></a>
									<input type="hidden" name='<%=prefix+"_loginpagecount"%>' value='<%=authPageCounter%>'/>
									<input type="hidden" name='<%=prefix+"_loginpageop"%>' value="Continue"/>
								</td>
								<td class="formcolumncell">
									<nobr><input type="text" size="30" name='<%=prefix+"_loginpageregexp"%>' value=""/></nobr>
								</td>
								<td class="formcolumncell">
									<nobr><input type="radio" name='<%=prefix+"_loginpagetype"%>' value='<%=org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_FORM%>' checked=""/>&nbsp;Form name</nobr><br/>
									<nobr><input type="radio" name='<%=prefix+"_loginpagetype"%>' value='<%=org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_LINK%>'/>&nbsp;Link target</nobr>
									<nobr><input type="radio" name='<%=prefix+"_loginpagetype"%>' value='<%=org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_REDIRECTION%>'/>&nbsp;Redirection</nobr>
								</td>
								<td class="formcolumncell">
									<nobr><input type="text" size="30" name='<%=prefix+"_loginpagematchregexp"%>' value=""/></nobr>
								</td>
								<td class="formcolumncell">
								</td>
							</tr>

						</table>
					</td>
                                        </tr>
<%
                                        accessCounter++;
                                }
                        }
                }

		if (accessCounter == 0)
		{
%>
				<tr class="formrow"><td class="formmessage" colspan="3">No session-based access credentials specified</td></tr>
<%
		}
%>
				<tr class="formrow"><td class="formseparator" colspan="3"><hr/></td></tr>
				<tr class="formrow">
					<td class="formcolumncell">
						<a name="scredential"><input type="button" value="Add" alt="Add session authentication url regular expression" onclick="javascript:addSRegexp();"/></a>
						<input type="hidden" name="scredential_count" value='<%=accessCounter%>'/>
						<input type="hidden" name="scredential_op" value="Continue"/>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="scredential_regexp" value=""/></nobr>
					</td>
					<td class="formcolumncell">
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
		// Hiddens for Access Credentials tab.
		
		// Page credentials first.
		int i = 0;
		int accessCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
			{
				// A bin description node!  Look for all its parameters.
				String type = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
				if (!type.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
				{
					String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
					String domain = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_DOMAIN);
					if (domain == null)
						domain = "";
					String userName = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_USERNAME);
					String password = org.apache.lcf.crawler.system.LCF.deobfuscate(cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD));

					// It's prefix will be...
					String prefix = "acredential_" + Integer.toString(accessCounter);
%>
<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
<input type="hidden" name='<%="type_"+prefix%>' value='<%=type%>'/>
<input type="hidden" name='<%="domain_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(domain)%>'/>
<input type="hidden" name='<%="username_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(userName)%>'/>
<input type="hidden" name='<%="password_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(password)%>'/>
<%
					accessCounter++;
				}
			}
		}
%>
<input type="hidden" name="acredential_count" value='<%=accessCounter%>'/>
<%

		// Now, session credentials
		i = 0;
		accessCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
			{
				// A bin description node!  Look for all its parameters.
				String type = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
				if (type.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
				{
					String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
					// It's identifier will be...
					String prefix = "scredential_" + Integer.toString(accessCounter);
%>
<input type="hidden" name='<%=prefix+"_regexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
<%
					// Loop through login pages...
					int q = 0;
					int authPageCounter = 0;
					while (q < cn.getChildCount())
					{
						ConfigNode authPageNode = cn.getChild(q++);
						if (authPageNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPAGE))
						{
							String pageRegexp = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
							String pageType = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
							String matchRegexp = authPageNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_MATCHREGEXP);
							if (matchRegexp == null)
								matchRegexp = "";
							String authpagePrefix = prefix + "_" + authPageCounter;
%>
<input type="hidden" name='<%=authpagePrefix+"_regexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pageRegexp)%>'/>
<input type="hidden" name='<%=authpagePrefix+"_type"%>' value='<%=pageType%>'/>
<input type="hidden" name='<%=authpagePrefix+"_matchregexp"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(matchRegexp)%>'/>
<%
							if (pageType.equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_FORM))
							{
								int z = 0;
								int paramCounter = 0;
								while (z < authPageNode.getChildCount())
								{
									ConfigNode paramNode = authPageNode.getChild(z++);
									if (paramNode.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPARAMETER))
									{
										String param = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_NAMEREGEXP);
										if (param == null)
											param = "";
										String value = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
										if (value == null)
											value = "";
										String password = paramNode.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD);
										if (password == null)
											password = "";
										String authParamPrefix = authpagePrefix + "_" + paramCounter;
%>
<input type="hidden" name='<%=authParamPrefix+"_param"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(param)%>'/>
<input type="hidden" name='<%=authParamPrefix+"_value"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'/>
<input type="hidden" name='<%=authParamPrefix+"_password"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(org.apache.lcf.crawler.system.LCF.deobfuscate(password))%>'/>
<%
										paramCounter++;
									}
								}
%>
<input type="hidden" name='<%=authpagePrefix+"_loginparamcount"%>' value='<%=paramCounter%>'/>
<%
							}
							authPageCounter++;
						}
					}
%>
<input type="hidden" name='<%=prefix+"_loginpagecount"%>' value='<%=authPageCounter%>'/>
<%
					accessCounter++;
				}
			}
		}
%>
<input type="hidden" name="scredential_count" value='<%=accessCounter%>'/>
<%
	}

	// "Certificates" tab
	if (tabName.equals("Certificates"))
	{
%>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Trust certificates:</nobr></td>
		<td class="boxcell">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"></td>
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Certificate</nobr></td>
				</tr>
<%
		int i = 0;
		int trustsCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST))
			{
				// It's prefix will be...
				String prefix = "trust_" + Integer.toString(trustsCounter);
				// A bin description node!  Look for all its parameters.
				String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
				String trustEverything = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
				if (trustEverything != null && trustEverything.equals("true"))
				{
					// We trust everything that matches this regexp
%>
				<tr class='<%=((trustsCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'><input type="button" value="Delete" alt='<%="Delete trust url regular expression #"+Integer.toString(trustsCounter+1)%>' onclick='<%="javascript:deleteTRegexp("+Integer.toString(trustsCounter)+");"%>'/>
						<input type="hidden" name='<%="op_"+prefix%>' value="Continue"/>
						<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
						<input type="hidden" name='<%="trustall_"+prefix%>' value="true"/>
						<input type="hidden" name='<%="truststore_"+prefix%>' value=""/>
						</a>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><i>Trust everything</i></nobr>
					</td>
				</tr>
<%
					trustsCounter++;
				}
				else
				{
					String trustStore = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTSTORE);
					IKeystoreManager localTruststore = KeystoreManagerFactory.make("",trustStore);
					String[] truststoreContents = localTruststore.getContents();
					
					// Each trust store will have only at most one cert in it at this level.  These individual certs are assembled into the proper trust store
					// for each individual url at fetch time.
					
					if (truststoreContents.length == 1)
					{
						String alias = truststoreContents[0];
						String description = localTruststore.getDescription(alias);
						String shortenedDescription = description;
						if (shortenedDescription.length() > 100)
							shortenedDescription = shortenedDescription.substring(0,100) + "...";

%>
				<tr class='<%=((trustsCounter % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell">
						<a name='<%=prefix%>'><input type="button" value="Delete" alt='<%="Delete trust url regular expression #"+Integer.toString(trustsCounter+1)%>' onclick='<%="javascript:deleteTRegexp("+Integer.toString(trustsCounter)+");"%>'/>
						<input type="hidden" name='<%="op_"+prefix%>' value="Continue"/>
						<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
						<input type="hidden" name='<%="trustall_"+prefix%>' value="false"/>
						<input type="hidden" name='<%="truststore_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(trustStore)%>'/>
						</a>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexp)%></nobr>
					</td>
					<td class="formcolumncell">
						<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(shortenedDescription)%></nobr>
					</td>
				</tr>
<%
						trustsCounter++;
					}
				}

			}
		}

		if (trustsCounter == 0)
		{
%>
				<tr class="formrow"><td class="formmessage" colspan="3">No trust certificates specified</td></tr>
<%
		}
%>

				<tr class="formrow"><td class="formseparator" colspan="3"><hr/></td></tr>
				<tr class="formrow">
					<td class="formcolumncell">
						<a name="trust"><input type="button" value="Add" alt="Add url regular expression for truststore" onclick="javascript:addTRegexp();"/></a>
						<input type="hidden" name="trust_count" value='<%=trustsCounter%>'/>
						<input type="hidden" name="trust_op" value="Continue"/>
					</td>
					<td class="formcolumncell">
						<nobr><input type="text" size="30" name="regexp_trust" value=""/></nobr>
					</td>
					<td class="formcolumncell">
						<nobr>Upload certificate: <input name="certificate_trust" size="50" type="file"/>&nbsp;<input name="all_trust" type="checkbox" value="true">Trust everything</input></nobr>
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
		// Hiddens for Certificates tab.
		int i = 0;
		int trustsCounter = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode cn = parameters.getChild(i++);
			if (cn.getType().equals(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST))
			{
				// It's prefix will be...
				String prefix = "trust_" + Integer.toString(trustsCounter);

				// A bin description node!  Look for all its parameters.
				String regexp = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
				String trustEverything = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
				if (trustEverything != null && trustEverything.equals("true"))
				{
					// We trust everything that matches this regexp
%>
<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
<input type="hidden" name='<%="truststore_"+prefix%>' value=""/>
<input type="hidden" name='<%="trustall_"+prefix%>' value="true"/>
<%
					trustsCounter++;
				}
				else
				{
					String trustStore = cn.getAttributeValue(org.apache.lcf.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTSTORE);

%>
<input type="hidden" name='<%="regexp_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexp)%>'/>
<input type="hidden" name='<%="truststore_"+prefix%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(trustStore)%>'/>
<input type="hidden" name='<%="trustall_"+prefix%>' value="false"/>
<%
					trustsCounter++;
				}
			}
		}
%>
<input type="hidden" name="trust_count" value='<%=trustsCounter%>'/>
<%
	}
%>
