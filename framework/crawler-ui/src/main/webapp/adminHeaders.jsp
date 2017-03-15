<%
response.setHeader("Pragma","No-cache");
response.setDateHeader("Expires",0);
response.setHeader("Cache-Control", "no-cache");
response.setDateHeader("max-age", 0);
response.setContentType("text/html;charset=utf-8");
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

<%@ page language="java" %>
<%@ page import="org.apache.manifoldcf.core.interfaces.*" %>
<%@ page import="org.apache.manifoldcf.core.util.*" %>
<%@ page import="org.apache.manifoldcf.ui.i18n.*" %>
<%@ page import="org.apache.manifoldcf.agents.interfaces.*" %>
<%@ page import="org.apache.manifoldcf.crawler.interfaces.*" %>
<%@ page import="org.apache.manifoldcf.authorities.interfaces.*" %>
<%@ page import="java.util.*" %>

<jsp:useBean id="thread" class="org.apache.manifoldcf.ui.beans.ThreadContext" scope="request"/>
<jsp:useBean id="adminprofile" class="org.apache.manifoldcf.ui.beans.AdminProfile" scope="session"/>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="x" uri="http://java.sun.com/jsp/jstl/xml" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="sql" uri="http://java.sun.com/jsp/jstl/sql" %>


<%
  String queryString = request.getQueryString();
  String requestURL = request.getRequestURI();
  requestURL = requestURL.substring(requestURL.lastIndexOf("/") + 1);
  requestURL += queryString == null ? "" : "?" + queryString;
  if (adminprofile.getLoggedOn() == false)
  {
    if (queryString == null)
    {
      response.sendRedirect("login.jsp");
    } else
    {
      if (!requestURL.startsWith("index.jsp"))
      {
        requestURL = "index.jsp?p=" + requestURL;
      }
      response.sendRedirect("login.jsp?nextUrl=" + URLEncoder.encode(requestURL));
    }
    return;
  }

  response.setHeader("page", requestURL);

  IThreadContext threadContext = thread.getThreadContext();
  org.apache.manifoldcf.ui.multipart.MultipartWrapper variableContext = (org.apache.manifoldcf.ui.multipart.MultipartWrapper)threadContext.get("__WRAPPER__");
  if (variableContext == null)
  {
    variableContext = new org.apache.manifoldcf.ui.multipart.MultipartWrapper(request,adminprofile);
    threadContext.save("__WRAPPER__",variableContext);
  }
%>
