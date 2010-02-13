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
	// This file is included by every place that the configuration information for the connector
	// needs to be viewed.  When it is called, the Parameters object is placed in the thread context
	// under the name "Parameters".

	// The coder can presume that this jsp is executed within a body section.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");
%>

<%
	if (parameters == null)
		out.println("Hey!  No parameters came in!!!");

	String email = parameters.getParameter(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_EMAIL);
	String robots = parameters.getParameter(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_ROBOTSUSAGE);
	if (robots.equals("none"))
		robots = "Ignore robots.txt";
	else if (robots.equals("data"))
		robots = "Obey robots.txt for data fetches only";
	else if (robots.equals("all"))
		robots = "Obey robots.txt for all fetches";

%>
<table class="displaytable">
	<tr>
		<td class="description" colspan="1"><nobr>Email address:</nobr></td>
		<td class="value" colspan="1"><%=com.metacarta.ui.util.Encoder.bodyEscape(email)%></td>
		<td class="description" colspan="1"><nobr>Robots usage:</nobr></td>
		<td class="value" colspan="1"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(robots)%></nobr></td>
	</tr>
	<tr>
		<td class="description" colspan="1"><nobr>Bandwidth throttling:</nobr></td>
		<td class="boxcell" colspan="3">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"><nobr>Bin regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Case insensitive?</nobr></td>
					<td class="formcolumnheader"><nobr>Max connections</nobr></td>
					<td class="formcolumnheader"><nobr>Max kbytes/sec</nobr></td>
					<td class="formcolumnheader"><nobr>Max fetches/min</nobr></td>
				</tr>
<%
	int i = 0;
	int instanceNumber = 0;
	while (i < parameters.getChildCount())
	{
		ConfigNode cn = parameters.getChild(i++);
		if (cn.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC))
		{
			// A bin description node!  Look for all its parameters.
			String regexp = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_BINREGEXP);
			String isCaseInsensitive = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_INSENSITIVE);
			String maxConnections = null;
			String maxKBPerSecond = null;
			String maxFetchesPerMinute = null;
			int j = 0;
			while (j < cn.getChildCount())
			{
				ConfigNode childNode = cn.getChild(j++);
				if (childNode.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXCONNECTIONS))
					maxConnections = childNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
				else if (childNode.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXKBPERSECOND))
					maxKBPerSecond = childNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
				else if (childNode.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE))
					maxFetchesPerMinute = childNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
			}
			if (maxConnections == null)
				maxConnections = "Not limited";
			if (maxKBPerSecond == null)
				maxKBPerSecond = "Not limited";
			if (maxFetchesPerMinute == null)
				maxFetchesPerMinute = "Not limited";
			if (isCaseInsensitive == null || isCaseInsensitive.length() == 0)
				isCaseInsensitive = "false";
%>
				<tr class='<%=((instanceNumber % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(regexp)%></nobr></td>
					<td class="formcolumncell"><%=isCaseInsensitive%></td>
					<td class="formcolumncell"><nobr><%=maxConnections%></nobr></td>
					<td class="formcolumncell"><nobr><%=maxKBPerSecond%></nobr></td>
					<td class="formcolumncell"><nobr><%=maxFetchesPerMinute%></nobr></td>
				</tr>
<%
			instanceNumber++;
		}
	}
	if (instanceNumber == 0)
	{
%>
				<tr class="formrow"><td class="formmessage" colspan="5">No bandwidth throttling</td></tr>
<%
	}
%>
			</table>
		</td>
	</tr>
	
	<tr>
		<td class="description" colspan="1"><nobr>Page access credentials:</nobr></td>
		<td class="boxcell" colspan="3">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Credential type</nobr></td>
					<td class="formcolumnheader"><nobr>Credential domain</nobr></td>
					<td class="formcolumnheader"><nobr>User name</nobr></td>
				</tr>
<%
	i = 0;
	instanceNumber = 0;
	while (i < parameters.getChildCount())
	{
		ConfigNode cn = parameters.getChild(i++);
		if (cn.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
		{
			// A bin description node!  Look for all its parameters.
			String type = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
			if (!type.equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
			{
				String regexp = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
				// Page-based auth
				String domain = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_DOMAIN);
				if (domain == null)
					domain = "";
				String userName = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_USERNAME);
%>
				<tr>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(regexp)%></nobr></td>
					<td class="formcolumncell"><nobr><%=type%></nobr></td>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(domain)%></nobr></td>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(userName)%></nobr></td>
				</tr>
<%
				instanceNumber++;
			}
		}
	}
	if (instanceNumber == 0)
	{
%>
				<tr class="formrow"><td class="formmessage" colspan="4"><nobr>No page access credentials</nobr></td></tr>
<%
	}
%>
			</table>
		</td>
	</tr>

	<tr>
		<td class="description" colspan="1"><nobr>Session-based access credentials:</nobr></td>
		<td class="boxcell" colspan="3">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Login pages</nobr></td>
				</tr>
<%
	i = 0;
	instanceNumber = 0;
	while (i < parameters.getChildCount())
	{
		ConfigNode cn = parameters.getChild(i++);
		if (cn.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL))
		{
			// A bin description node!  Look for all its parameters.
			String type = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
			if (type.equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
			{
				String regexp = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
				// Session-based auth.  Display this as a nested table.
%>
				<tr class='<%=((instanceNumber % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(regexp)%></nobr></td>
					<td class="boxcell">
<%
				int q = 0;
				int authPageInstanceNumber = 0;
				while (q < cn.getChildCount())
				{
					ConfigNode authPageNode = cn.getChild(q++);
					if (authPageNode.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPAGE))
					{
						String authURLRegexp = authPageNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
						String pageType = authPageNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE);
						String authMatchRegexp = authPageNode.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_MATCHREGEXP);
						if (authMatchRegexp == null)
							authMatchRegexp = "";
						if (authPageInstanceNumber == 0)
						{
%>
						<table class="formtable">
							<tr class="formheaderrow">
								<td class="formcolumnheader"><nobr>Login URL regular expression</nobr></td>
								<td class="formcolumnheader"><nobr>Page type</nobr></td>
								<td class="formcolumnheader"><nobr>Form name/link target regular expression</nobr></td>
								<td class="formcolumnheader"><nobr>Override form parameters</nobr></td>
							</tr>
<%
						}
%>
							<tr class='<%=((authPageInstanceNumber % 2)==0)?"evenformrow":"oddformrow"%>'>
								<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(authURLRegexp)%></nobr></td>
								<td class="formcolumncell"><nobr><%=pageType%></nobr></td>
								<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(authMatchRegexp)%></nobr></td>
								<td class="formcolumncell">
<%
						if (pageType.equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_FORM))
						{
							int z = 0;
							while (z < authPageNode.getChildCount())
							{
								ConfigNode authParameter = authPageNode.getChild(z++);
								if (authParameter.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPARAMETER))
								{
									String paramName = authParameter.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_NAMEREGEXP);
									if (paramName == null)
										paramName = "";
									String paramValue = authParameter.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE);
									if (paramValue == null)
										paramValue = "";
									String password = authParameter.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD);
									if (password != null && password.length() > 0)
										paramValue = "*****";
%>
									<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(paramName+": "+paramValue)%></nobr><br/>
<%
								}
							}
						}
%>
								</td>
							</tr>
<%
						authPageInstanceNumber++;
					}
				}
				if (authPageInstanceNumber == 0)
				{
%>
						<nobr>No login pages specified</nobr>
<%
				}
				else
				{
%>
						</table>
<%
				}
%>
					</td>
				</tr>
<%
				instanceNumber++;
			}
		}
	}
	if (instanceNumber == 0)
	{
%>
				<tr class="formrow"><td class="formmessage" colspan="2"><nobr>No session-based access credentials</nobr></td></tr>
<%
	}
%>
			</table>
		</td>
	</tr>
	
	<tr>
		<td class="description" colspan="1"><nobr>Trust certificates:</nobr></td>
		<td class="boxcell" colspan="3">
			<table class="formtable">
				<tr class="formheaderrow">
					<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
					<td class="formcolumnheader"><nobr>Certificate</nobr></td>
				</tr>
<%
	i = 0;
	instanceNumber = 0;
	while (i < parameters.getChildCount())
	{
		ConfigNode cn = parameters.getChild(i++);
		if (cn.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST))
		{
			// A bin description node!  Look for all its parameters.
			String regexp = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP);
			String trustEverything = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTEVERYTHING);
			if (trustEverything != null && trustEverything.equals("true"))
			{
				// We trust everything that matches this regexp
%>
				<tr class='<%=((instanceNumber % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(regexp)%></nobr></td>
					<td class="formcolumncell"><i>Trust everything</i></td>
				</tr>
<%
				instanceNumber++;
			}
			else
			{
				String trustStore = cn.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTSTORE);
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
				<tr class='<%=((instanceNumber % 2)==0)?"evenformrow":"oddformrow"%>'>
					<td class="formcolumncell"><nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(regexp)%></nobr></td>
					<td class="formcolumncell"><%=com.metacarta.ui.util.Encoder.bodyEscape(shortenedDescription)%></td>
				</tr>
<%
					instanceNumber++;
				}
			}
		}
	}
	if (instanceNumber == 0)
	{
%>
				<tr class="formrow"><td class="formmessage" colspan="2">No trust certificates</td></tr>
<%
	}
%>
			</table>
		</td>
	</tr>

</table>
