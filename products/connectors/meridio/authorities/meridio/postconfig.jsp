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
	// This file is included by every place that edited specification information for the file system connector
	// is posted upon submit.  When it is called, the Map parameters object is placed in the thread context
	// under the name "Parameters".  This map should be edited by this code.

	// The coder cannot presume that this jsp is executed within a body section.  Errors should thus be
	// forwarded to "error.jsp" using <jsp:forward>.
	// Arguments from the original request object for the post page will remain available for access.


	ConfigParams parameters = (ConfigParams)threadContext.get("Parameters");

	if (parameters == null)
		System.out.println("No parameter map!!!");

	String dmwsServerProtocol = variableContext.getParameter("dmwsServerProtocol");
	if (dmwsServerProtocol != null)
		parameters.setParameter("DMWSServerProtocol",dmwsServerProtocol);
	String rmwsServerProtocol = variableContext.getParameter("rmwsServerProtocol");
	if (rmwsServerProtocol != null)
		parameters.setParameter("RMWSServerProtocol",rmwsServerProtocol);
	String metacartawsServerProtocol = variableContext.getParameter("metacartawsServerProtocol");
	if (metacartawsServerProtocol != null)
		parameters.setParameter("MetaCartaWSServerProtocol",metacartawsServerProtocol);

	String dmwsServerName = variableContext.getParameter("dmwsServerName");
	if (dmwsServerName != null)
		parameters.setParameter("DMWSServerName",dmwsServerName);
	String rmwsServerName = variableContext.getParameter("rmwsServerName");
	if (rmwsServerName != null)
		parameters.setParameter("RMWSServerName",rmwsServerName);
	String metacartawsServerName = variableContext.getParameter("metacartawsServerName");
	if (metacartawsServerName != null)
		parameters.setParameter("MetaCartaWSServerName",metacartawsServerName);

	String dmwsServerPort = variableContext.getParameter("dmwsServerPort");
	if (dmwsServerPort != null)
	{
		if (dmwsServerPort.length() > 0)
			parameters.setParameter("DMWSServerPort",dmwsServerPort);
		else
			parameters.setParameter("DMWSServerPort",null);
	}
	String rmwsServerPort = variableContext.getParameter("rmwsServerPort");
	if (rmwsServerPort != null)
	{
		if (rmwsServerPort.length() > 0)
			parameters.setParameter("RMWSServerPort",rmwsServerPort);
		else
			parameters.setParameter("RMWSServerPort",null);
	}
	String metacartawsServerPort = variableContext.getParameter("metacartawsServerPort");
	if (metacartawsServerPort != null)
	{
		if (metacartawsServerPort.length() > 0)
			parameters.setParameter("MetaCartaWSServerPort",metacartawsServerPort);
		else
			parameters.setParameter("MetaCartaWSServerPort",null);
	}

	String dmwsLocation = variableContext.getParameter("dmwsLocation");
	if (dmwsLocation != null)
		parameters.setParameter("DMWSLocation",dmwsLocation);
	String rmwsLocation = variableContext.getParameter("rmwsLocation");
	if (rmwsLocation != null)
		parameters.setParameter("RMWSLocation",rmwsLocation);
	String metacartawsLocation = variableContext.getParameter("metacartawsLocation");
	if (metacartawsLocation != null)
		parameters.setParameter("MetaCartaWSLocation",metacartawsLocation);

	String dmwsProxyHost = variableContext.getParameter("dmwsProxyHost");
	if (dmwsProxyHost != null)
		parameters.setParameter("DMWSProxyHost",dmwsProxyHost);
	String rmwsProxyHost = variableContext.getParameter("rmwsProxyHost");
	if (rmwsProxyHost != null)
		parameters.setParameter("RMWSProxyHost",rmwsProxyHost);
	String metacartawsProxyHost = variableContext.getParameter("metacartawsProxyHost");
	if (metacartawsProxyHost != null)
		parameters.setParameter("MetaCartaWSProxyHost",metacartawsProxyHost);
		
	String dmwsProxyPort = variableContext.getParameter("dmwsProxyPort");
	if (dmwsProxyPort != null && dmwsProxyPort.length() > 0)
		parameters.setParameter("DMWSProxyPort",dmwsProxyPort);
	String rmwsProxyPort = variableContext.getParameter("rmwsProxyPort");
	if (rmwsProxyPort != null && rmwsProxyPort.length() > 0)
		parameters.setParameter("RMWSProxyPort",rmwsProxyPort);
	String metacartawsProxyPort = variableContext.getParameter("metacartawsProxyPort");
	if (metacartawsProxyPort != null && metacartawsProxyPort.length() > 0)
		parameters.setParameter("MetaCartaWSProxyPort",metacartawsProxyPort);

	String userName = variableContext.getParameter("userName");
	if (userName != null)
		parameters.setParameter("UserName",userName);

	String password = variableContext.getParameter("password");
	if (password != null)
		parameters.setObfuscatedParameter("Password",password);

	String configOp = variableContext.getParameter("configop");
	if (configOp != null)
	{
		String keystoreValue;
		if (configOp.equals("Delete"))
		{
			String alias = variableContext.getParameter("keystorealias");
			keystoreValue = parameters.getParameter("MeridioKeystore");
			IKeystoreManager mgr;
			if (keystoreValue != null)
				mgr = KeystoreManagerFactory.make("",keystoreValue);
			else
				mgr = KeystoreManagerFactory.make("");
			mgr.remove(alias);
			parameters.setParameter("MeridioKeystore",mgr.getString());
		}
		else if (configOp.equals("Add"))
		{
			String alias = variableContext.getParameter("keystorealias");
			byte[] certificateValue = variableContext.getBinaryBytes("certificate");
			keystoreValue = parameters.getParameter("MeridioKeystore");
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
				variableContext.setParameter("target","listauthorities.jsp");
%>
	<jsp:forward page="../../error.jsp"/>
<%
			}
			parameters.setParameter("MeridioKeystore",mgr.getString());
		}
	}

%>
