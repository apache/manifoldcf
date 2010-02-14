<%@ include file="adminDefaults.jsp" %>

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
	// This module is meant to be called within a table cell, and will drop the
	// metacarta banner into the cell
	String dateString = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date());
%>

<table class="bannertable">
    <tr><td class="headerimage"><img src="LCF-logo.png"/></td>
          <td>
	    <table class="headertable">
		<tr><td class="headerdate"><%=com.metacarta.ui.util.Encoder.bodyEscape(dateString)%></td></tr>
	          <tr><td class="header">Document Ingestion</td></tr>
	    </table>
	</td>
    </tr>
</table>



