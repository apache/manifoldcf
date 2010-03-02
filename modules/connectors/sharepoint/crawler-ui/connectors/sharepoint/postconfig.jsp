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

	String serverVersion = variableContext.getParameter("serverVersion");
	if (serverVersion != null)
		parameters.setParameter("serverVersion",serverVersion);

	String serverProtocol = variableContext.getParameter("serverProtocol");
	if (serverProtocol != null)
		parameters.setParameter("serverProtocol",serverProtocol);

	String serverName = variableContext.getParameter("serverName");
	if (serverName != null)
		parameters.setParameter("serverName",serverName);

	String serverPort = variableContext.getParameter("serverPort");
	if (serverPort != null)
		parameters.setParameter("serverPort",serverPort);

	String serverLocation = variableContext.getParameter("serverLocation");
	if (serverLocation != null)
		parameters.setParameter("serverLocation",serverLocation);

	String userName = variableContext.getParameter("userName");
	if (userName != null)
		parameters.setParameter("userName",userName);

	String password = variableContext.getParameter("password");
	if (password != null)
		parameters.setObfuscatedParameter("password",password);

	String keystoreValue = variableContext.getParameter("keystoredata");
	if (keystoreValue != null)
		parameters.setParameter("keystore",keystoreValue);

	String configOp = variableContext.getParameter("configop");
	if (configOp != null)
	{
		if (configOp.equals("Delete"))
		{
			String alias = variableContext.getParameter("shpkeystorealias");
			keystoreValue = parameters.getParameter("keystore");
			IKeystoreManager mgr;
			if (keystoreValue != null)
				mgr = KeystoreManagerFactory.make("",keystoreValue);
			else
				mgr = KeystoreManagerFactory.make("");
			mgr.remove(alias);
			parameters.setParameter("keystore",mgr.getString());
		}
		else if (configOp.equals("Add"))
		{
			String alias = IDFactory.make(threadContext);
			byte[] certificateValue = variableContext.getBinaryBytes("shpcertificate");
			keystoreValue = parameters.getParameter("keystore");
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
			parameters.setParameter("keystore",mgr.getString());
		}
	}


%>
