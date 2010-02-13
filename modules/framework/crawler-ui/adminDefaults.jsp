<%@ page language="java" %>
<%@ page import="com.metacarta.core.interfaces.*" %>
<%@ page import="com.metacarta.agents.interfaces.*" %>
<%@ page import="com.metacarta.authorities.interfaces.*" %>
<%@ page import="com.metacarta.crawler.interfaces.*" %>
<%@ page import="java.util.*" %>

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
	com.metacarta.crawler.system.Metacarta.initializeEnvironment();
%>

<jsp:useBean id="thread" class="com.metacarta.ui.beans.ThreadContext" scope="request"/>
<jsp:useBean id="adminprofile" class="com.metacarta.ui.beans.AdminProfile" scope="session"/>

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core" %>
<%@ taglib prefix="x" uri="http://java.sun.com/jstl/xml" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>
<%@ taglib prefix="sql" uri="http://java.sun.com/jstl/sql" %>


<%
	IThreadContext threadContext = thread.getThreadContext();
	com.metacarta.ui.multipart.MultipartWrapper variableContext = (com.metacarta.ui.multipart.MultipartWrapper)threadContext.get("__WRAPPER__");
	if (variableContext == null)
	{
		variableContext = new com.metacarta.ui.multipart.MultipartWrapper(request);
		threadContext.save("__WRAPPER__",variableContext);
	}
%>

