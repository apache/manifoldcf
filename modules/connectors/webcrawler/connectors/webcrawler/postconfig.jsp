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
	// This file is included by every place that edited specification information for the rss connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String email = variableContext.getParameter("email");
	if (email != null)
		parameters.setParameter(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_EMAIL,email);
	String robotsUsage = variableContext.getParameter("robotsusage");
	if (robotsUsage != null)
		parameters.setParameter(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.PARAMETER_ROBOTSUSAGE,robotsUsage);

	String x = variableContext.getParameter("bandwidth_count");
	if (x != null && x.length() > 0)
	{
		// About to gather the bandwidth nodes, so get rid of the old ones.
		int i = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode node = parameters.getChild(i);
			if (node.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC))
				parameters.removeChild(i);
			else
				i++;
		}
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String prefix = "bandwidth_"+Integer.toString(i);
			String op = variableContext.getParameter("op_"+prefix);
			if (op == null || !op.equals("Delete"))
			{
				// Gather the regexp etc.
				String regexp = variableContext.getParameter("regexp_"+prefix);
				String isCaseInsensitive = variableContext.getParameter("insensitive_"+prefix);
				String maxConnections = variableContext.getParameter("connections_"+prefix);
				String rate = variableContext.getParameter("rate_"+prefix);
				String fetches = variableContext.getParameter("fetches_"+prefix);
				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_BINREGEXP,regexp);
				if (isCaseInsensitive != null && isCaseInsensitive.length() > 0)
					node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_INSENSITIVE,isCaseInsensitive);
				if (maxConnections != null && maxConnections.length() > 0)
				{
					ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXCONNECTIONS);
					child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,maxConnections);
					node.addChild(node.getChildCount(),child);
				}
				if (rate != null && rate.length() > 0)
				{
					ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXKBPERSECOND);
					child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,rate);
					node.addChild(node.getChildCount(),child);
				}
				if (fetches != null && fetches.length() > 0)
				{
					ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE);
					child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,fetches);
					node.addChild(node.getChildCount(),child);
				}
				parameters.addChild(parameters.getChildCount(),node);
			}
			i++;
		}
		String addop = variableContext.getParameter("bandwidth_op");
		if (addop != null && addop.equals("Add"))
		{
			String regexp = variableContext.getParameter("regexp_bandwidth");
			String isCaseInsensitive = variableContext.getParameter("insensitive_bandwidth");
			String maxConnections = variableContext.getParameter("connections_bandwidth");
			String rate = variableContext.getParameter("rate_bandwidth");
			String fetches = variableContext.getParameter("fetches_bandwidth");
			ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_BINDESC);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_BINREGEXP,regexp);
			if (isCaseInsensitive != null && isCaseInsensitive.length() > 0)
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_INSENSITIVE,isCaseInsensitive);
			if (maxConnections != null && maxConnections.length() > 0)
			{
				ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXCONNECTIONS);
				child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,maxConnections);
				node.addChild(node.getChildCount(),child);
			}
			if (rate != null && rate.length() > 0)
			{
				ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXKBPERSECOND);
				child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,rate);
				node.addChild(node.getChildCount(),child);
			}
			if (fetches != null && fetches.length() > 0)
			{
				ConfigNode child = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_MAXFETCHESPERMINUTE);
				child.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,fetches);
				node.addChild(node.getChildCount(),child);
			}
			parameters.addChild(parameters.getChildCount(),node);
		}
	}
	
	x = variableContext.getParameter("acredential_count");
	if (x != null && x.length() > 0)
	{
		// About to gather the access credential nodes, so get rid of the old ones.
		int i = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode node = parameters.getChild(i);
			if (node.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL)
                                && !node.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE).equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
				parameters.removeChild(i);
			else
				i++;
		}
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String prefix = "acredential_"+Integer.toString(i);
			String op = variableContext.getParameter("op_"+prefix);
			if (op == null || !op.equals("Delete"))
			{
				// Gather the regexp etc.
				String regexp = variableContext.getParameter("regexp_"+prefix);
				String type = variableContext.getParameter("type_"+prefix);
				String domain = variableContext.getParameter("domain_"+prefix);
				if (domain == null)
					domain = "";
				String userName = variableContext.getParameter("username_"+prefix);
				String password = variableContext.getParameter("password_"+prefix);
				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,type);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_DOMAIN,domain);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_USERNAME,userName);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD,
					com.metacarta.crawler.system.Metacarta.obfuscate(password));
				parameters.addChild(parameters.getChildCount(),node);
			}
			i++;
		}
		String addop = variableContext.getParameter("acredential_op");
		if (addop != null && addop.equals("Add"))
		{
			String regexp = variableContext.getParameter("regexp_acredential");
			String type = variableContext.getParameter("type_acredential");
			String domain = variableContext.getParameter("domain_acredential");
			String userName = variableContext.getParameter("username_acredential");
			String password = variableContext.getParameter("password_acredential");
			ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,type);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_DOMAIN,domain);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_USERNAME,userName);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD,
				com.metacarta.crawler.system.Metacarta.obfuscate(password));
			parameters.addChild(parameters.getChildCount(),node);
		}
	}

	x = variableContext.getParameter("scredential_count");
	if (x != null && x.length() > 0)
	{
		// About to gather the access credential nodes, so get rid of the old ones.
		int i = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode node = parameters.getChild(i);
			if (node.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL)
                                && node.getAttributeValue(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE).equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION))
				parameters.removeChild(i);
			else
				i++;
		}
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String prefix = "scredential_"+Integer.toString(i);
			String op = variableContext.getParameter(prefix+"_op");
			if (op == null || !op.equals("Delete"))
			{
				// Gather the regexp etc.
				String regexp = variableContext.getParameter(prefix+"_regexp");
				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION);
                                // How many login pages are there?
                                int loginPageCount = Integer.parseInt(variableContext.getParameter(prefix+"_loginpagecount"));
                                int q = 0;
                                while (q < loginPageCount)
                                {
                                        String authpagePrefix = prefix + "_" + Integer.toString(q);
                                        String authpageOp = variableContext.getParameter(authpagePrefix+"_op");
                                        if (authpageOp == null || !authpageOp.equals("Delete"))
                                        {
                                                String pageRegexp = variableContext.getParameter(authpagePrefix+"_regexp");
						String pageType = variableContext.getParameter(authpagePrefix+"_type");
						String matchRegexp = variableContext.getParameter(authpagePrefix+"_matchregexp");
						if (matchRegexp == null)
							matchRegexp = "";
                                                ConfigNode authPageNode = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPAGE);
                                                authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,pageRegexp);
						authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,pageType);
						authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_MATCHREGEXP,matchRegexp);
						if (pageType.equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_FORM))
						{
							// How many parameters are there?
							int paramCount = Integer.parseInt(variableContext.getParameter(authpagePrefix+"_loginparamcount"));
							int z = 0;
							while (z < paramCount)
							{
								String paramPrefix = authpagePrefix+"_"+Integer.toString(z);
								String paramOp = variableContext.getParameter(paramPrefix+"_op");
								if (paramOp == null || !paramOp.equals("Delete"))
								{
									String name = variableContext.getParameter(paramPrefix+"_param");
									String value = variableContext.getParameter(paramPrefix+"_value");
									String password = variableContext.getParameter(paramPrefix+"_password");
									ConfigNode paramNode = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPARAMETER);
									paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_NAMEREGEXP,name);
									if (value != null && value.length() > 0)
										paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,value);
									if (password != null && password.length() > 0)
										paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD,com.metacarta.crawler.system.Metacarta.obfuscate(password));
									authPageNode.addChild(authPageNode.getChildCount(),paramNode);
								}
								z++;
							}
                                                
							// Look for add op
							String paramAddOp = variableContext.getParameter(authpagePrefix+"_loginparamop");
							if (paramAddOp != null && paramAddOp.equals("Add"))
							{
								String name = variableContext.getParameter(authpagePrefix+"_loginparamname");
								String value = variableContext.getParameter(authpagePrefix+"_loginparamvalue");
								String password = variableContext.getParameter(authpagePrefix+"_loginparampassword");
								ConfigNode paramNode = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPARAMETER);
								paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_NAMEREGEXP,name);
								if (value != null && value.length() > 0)
									paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_VALUE,value);
								if (password != null && password.length() > 0)
									paramNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_PASSWORD,com.metacarta.crawler.system.Metacarta.obfuscate(password));
								authPageNode.addChild(authPageNode.getChildCount(),paramNode);
							}
						}
						
						node.addChild(node.getChildCount(),authPageNode);
                                        }
                                        q++;
                                }
                                // Look for add op
                                String authpageAddop = variableContext.getParameter(prefix+"_loginpageop");
                                if (authpageAddop != null && authpageAddop.equals("Add"))
                                {
                                        String pageRegexp = variableContext.getParameter(prefix+"_loginpageregexp");
					String pageType = variableContext.getParameter(prefix+"_loginpagetype");
					String matchRegexp = variableContext.getParameter(prefix+"_loginpagematchregexp");
					if (matchRegexp == null)
						matchRegexp = "";
                                        ConfigNode authPageNode = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_AUTHPAGE);
                                        authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,pageRegexp);
					authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,pageType);
					authPageNode.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_MATCHREGEXP,matchRegexp);
                                        node.addChild(node.getChildCount(),authPageNode);
                                }

				parameters.addChild(parameters.getChildCount(),node);
			}
			i++;
		}
		String addop = variableContext.getParameter("scredential_op");
		if (addop != null && addop.equals("Add"))
		{
			String regexp = variableContext.getParameter("scredential_regexp");
			ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_ACCESSCREDENTIAL);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
			node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TYPE,com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTRVALUE_SESSION);
			parameters.addChild(parameters.getChildCount(),node);
		}
	}

	x = variableContext.getParameter("trust_count");
	if (x != null && x.length() > 0)
	{
		// About to gather the trust nodes, so get rid of the old ones.
		int i = 0;
		while (i < parameters.getChildCount())
		{
			ConfigNode node = parameters.getChild(i);
			if (node.getType().equals(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST))
				parameters.removeChild(i);
			else
				i++;
		}
		int count = Integer.parseInt(x);
		i = 0;
		while (i < count)
		{
			String prefix = "trust_"+Integer.toString(i);
			String op = variableContext.getParameter("op_"+prefix);
			if (op == null || !op.equals("Delete"))
			{
				// Gather the regexp etc.
				String regexp = variableContext.getParameter("regexp_"+prefix);
				String trustall = variableContext.getParameter("trustall_"+prefix);
				String truststore = variableContext.getParameter("truststore_"+prefix);
				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
				if (trustall != null && trustall.equals("true"))
					node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTEVERYTHING,"true");
				else
					node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTSTORE,truststore);
				parameters.addChild(parameters.getChildCount(),node);
			}
			i++;
		}
		String addop = variableContext.getParameter("trust_op");
		if (addop != null && addop.equals("Add"))
		{
			String regexp = variableContext.getParameter("regexp_trust");
			String trustall = variableContext.getParameter("all_trust");
			if (trustall != null && trustall.equals("true"))
			{
				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTEVERYTHING,"true");
				parameters.addChild(parameters.getChildCount(),node);
			}
			else
			{
				byte[] certificateValue = variableContext.getBinaryBytes("certificate_trust");
				IKeystoreManager mgr = KeystoreManagerFactory.make("");
				java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
				String certError = null;
				try
				{
					mgr.importCertificate("Certificate",is);
				}
				catch (Throwable e)
				{
					certError = e.getMessage();
				}
				finally
				{
					is.close();
				}

				if (certError != null)
				{
					// Redirect to error page
					variableContext.setParameter("text","Illegal certificate: "+certError);
					variableContext.setParameter("target","listconnections.jsp");
%>
	<jsp:forward page="../../error.jsp"/>
<%
				}

				ConfigNode node = new ConfigNode(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.NODE_TRUST);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_URLREGEXP,regexp);
				node.setAttribute(com.metacarta.crawler.connectors.webcrawler.WebcrawlerConfig.ATTR_TRUSTSTORE,mgr.getString());
				parameters.addChild(parameters.getChildCount(),node);
			}
		}
	}

%>
