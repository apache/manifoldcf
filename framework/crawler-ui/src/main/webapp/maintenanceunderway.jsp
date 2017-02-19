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

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<meta http-equiv="X-UA-Compatible" content="IE=edge"/>

<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <meta content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no' name='viewport'>
    <!-- Bootstrap 3.3.2 -->
    <link href="bootstrap/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
    <link rel="StyleSheet" href="css/style.css" type="text/css" media="screen"/>
    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
    <script src=javascript/html5shiv.min.js"></script>
    <script src="javascript/respond.min.js"></script>
    <![endif]-->

    <script type="text/javascript">
      <!--
      $.ManifoldCF.setTitle(
          '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "error.ApacheManifoldCFMaintenanceUnderway")%>',
          '<%=Messages.getBodyString(pageContext.getRequest().getLocale(), "error.ApacheManifoldCFMaintenanceUnderway")%>'
      );
      //-->
    </script>

  </head>
  <body class="login-page">
    <div class="login-box">
      <div class="login-logo">
        <a href="/"><img src="ManifoldCF-logo.png"/></a>
      </div>
      <!-- /.login-logo -->
      <div class="login-box-body">

        <div class="alert alert-danger">
          <h4><i class="icon fa fa-ban"></i> Error!</h4>
          <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"error.MaintenanceUnderway")%><br/>
          <%=Messages.getBodyString(pageContext.getRequest().getLocale(),"error.PleaseTryAgainLater")%>
        </div>

        <a class="btn btn-primary btn-sm" href='index.jsp' alt="<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"error.Return")%>">OK</a>

      </div>
    </div>
    </div>
  </body>
</html>
