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

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core" %>
<%@ taglib prefix="sql" uri="http://java.sun.com/jstl/sql" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>

<jsp:useBean id="adminprofile" class="org.apache.manifoldcf.ui.beans.AdminProfile" scope="session"/>

<c:catch var="error">
	<c:if test="${param.valid=='true'}">
		<c:set value="${param.login}" target="${adminprofile}" property="userID"/>
		<c:set value="${param.password}" target="${adminprofile}" property="password"/>
	</c:if>
	
	<c:if test="${param.valid=='false'}">
		<c:set value="null" target="${adminprofile}" property="userID"/>
	</c:if>
</c:catch>

<c:if test="${error!=null}">
	<c:set target="${logger}" property="msg" value="Profile error!!!! ${error}"/>
	<c:set value="null" target="${adminprofile}" property="userID"/>
</c:if>




