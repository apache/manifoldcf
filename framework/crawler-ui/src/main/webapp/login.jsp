<% response.setHeader("Pragma","No-cache");
response.setDateHeader("Expires",0);
response.setHeader("Cache-Control", "no-cache");
response.setDateHeader("max-age", 0);
response.setContentType("text/html;charset=utf-8");
%><%@ include file="adminDefaults.jsp" %>

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
    <link href="bootstrap/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
    <link rel="StyleSheet" href="css/style.css" type="text/css" media="screen"/>
    <title><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.ApacheManifoldCFLogin")%></title>
    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
    <script src=javascript/html5shiv.min.js"></script>
    <script src="javascript/respond.min.js"></script>
    <![endif]-->
    <script type="text/javascript">
      <!--
      function login()
      {
        document.loginform.submit();
      }

      function loginKeyPress(e)
      {
        e = e || window.event;
        if (e.keyCode == 13)
        {
          document.getElementById('buttonLogin').click();
          return false;
        }
        return true;
      }
      
      document.onkeypress = loginKeyPress;

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
        <p class="login-box-msg">Sign in to start your session</p>

        <form class="standardform" name="loginform" action="setupAdminProfile.jsp" method="POST">
<%
if (request.getParameter("nextUrl") != null)
{
%>
          <input type="hidden" name="nextUrl" value="<%=request.getParameter("nextUrl")%>">
<%
}
%>
<%
String value = variableContext.getParameter("loginfailed");
if (value != null && value.equals("true"))
{
%>
          <div class="callout callout-danger">
            <p><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.LoginFailed")%></p>
          </div>

<%
}
%>
          <div class="form-group has-feedback">
            <input name="userID" type="text" autofocus="autofocus" class="form-control" placeholder="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.UserIDColon")%>"/>
            <span class="glyphicon glyphicon-user form-control-feedback"></span>
          </div>
          <div class="form-group has-feedback">
            <input name="password" type="password" class="form-control" placeholder="<%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.PasswordColon")%>"/>
            <span class="glyphicon glyphicon-lock form-control-feedback"></span>
          </div>
          <div class="row">
            <div class="col-xs-8">
            </div>
            <div class="col-xs-4">
              <input id="buttonLogin" type="button" class="btn btn-primary btn-block" onclick='Javascript:login();'
                      value='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"index.Login")%>'
                      alt='<%=Messages.getAttributeString(pageContext.getRequest().getLocale(),"index.Login")%>'/>
            </div>
          </div>
        </form>
      </div>
    </div>
  </body>
</html>
