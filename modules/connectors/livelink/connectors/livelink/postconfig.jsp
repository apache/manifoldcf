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
	// This file is included by every place that edited specification information for the livelink connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.

	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String serverName = variableContext.getParameter("servername");
	if (serverName != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverName,serverName);
	String serverPort = variableContext.getParameter("serverport");
	if (serverPort != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPort,serverPort);
	String ingestProtocol = variableContext.getParameter("ingestprotocol");
	if (ingestProtocol != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestProtocol,ingestProtocol);
	String ingestPort = variableContext.getParameter("ingestport");
	if (ingestPort != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestPort,ingestPort);
	String ingestCgiPath = variableContext.getParameter("ingestcgipath");
	if (ingestCgiPath != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ingestCgiPath,ingestCgiPath);
	String viewProtocol = variableContext.getParameter("viewprotocol");
	if (viewProtocol != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewProtocol,viewProtocol);
	String viewServerName = variableContext.getParameter("viewservername");
	if (viewServerName != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewServerName,viewServerName);
	String viewPort = variableContext.getParameter("viewport");
	if (viewPort != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewPort,viewPort);
	String viewCgiPath = variableContext.getParameter("viewcgipath");
	if (viewCgiPath != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.viewCgiPath,viewCgiPath);
	String serverUserName = variableContext.getParameter("serverusername");
	if (serverUserName != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverUsername,serverUserName);
	String serverPassword = variableContext.getParameter("serverpassword");
	if (serverPassword != null)
		parameters.setObfuscatedParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.serverPassword,serverPassword);
	String ntlmDomain = variableContext.getParameter("ntlmdomain");
	if (ntlmDomain != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmDomain,ntlmDomain);
	String ntlmUsername = variableContext.getParameter("ntlmusername");
	if (ntlmUsername != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmUsername,ntlmUsername);
	String ntlmPassword = variableContext.getParameter("ntlmpassword");
	if (ntlmPassword != null)
		parameters.setObfuscatedParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.ntlmPassword,ntlmPassword);
	String keystoreValue = variableContext.getParameter("keystoredata");
	if (keystoreValue != null)
		parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,keystoreValue);

	String configOp = variableContext.getParameter("configop");
	if (configOp != null)
	{
		if (configOp.equals("Delete"))
		{
			String alias = variableContext.getParameter("llkeystorealias");
			keystoreValue = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
			IKeystoreManager mgr;
			if (keystoreValue != null)
				mgr = KeystoreManagerFactory.make("",keystoreValue);
			else
				mgr = KeystoreManagerFactory.make("");
			mgr.remove(alias);
			parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,mgr.getString());
		}
		else if (configOp.equals("Add"))
		{
			String alias = variableContext.getParameter("llkeystorealias");
			byte[] certificateValue = variableContext.getBinaryBytes("llcertificate");
			keystoreValue = parameters.getParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore);
			IKeystoreManager mgr;
			if (keystoreValue != null)
				mgr = KeystoreManagerFactory.make("",keystoreValue);
			else
				mgr = KeystoreManagerFactory.make("");
			java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
			String certError = null;
			try
			{
				mgr.importCertificate(alias,is);
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
			parameters.setParameter(com.metacarta.crawler.connectors.livelink.LiveLinkParameters.livelinkKeystore,mgr.getString());
		}
	}

%>
