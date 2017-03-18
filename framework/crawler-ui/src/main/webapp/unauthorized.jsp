<%@ include file="adminHeaders.jsp" %>

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

<script type="text/javascript">
  <!--
  $.ManifoldCF.setTitle(
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "error.Unauthorized")%>',
      '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "error.Unauthorized")%>'
  );
  //-->
</script>

<%
  // These have to be fetched from request rather than variableContext since error
  // forwards screw up the multipart wrapper
  String target = variableContext.getParameter("target");
%>

<div class="box box-danger">
  <div class="box-body">
    <div class="alert alert-danger">
      <h3><i class="icon fa fa-ban"></i> Error!</h3>
      <h4><%=Messages.getBodyString(pageContext.getRequest().getLocale(), "error.Unauthorized")%></h4>
    </div>
  </div>
  <div class="box-footer with-border">
    <a class="btn btn-primary" href='<%=org.apache.manifoldcf.core.util.URLEncoder.encode(target)%>' 
            title="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"error.Return")%>" data-toggle="tooltip">
      <i class="fa fa-check fa-fw" aria-hidden="true"></i>OK
    </a>
  </div>
</div>
