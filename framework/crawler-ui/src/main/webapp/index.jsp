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

<%
  String mcfVersion = org.apache.manifoldcf.core.system.ManifoldCF.getMcfVersion();
%>

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport"
            content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
    <!-- Bootstrap -->
    <link href="bootstrap/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
    <link href="bootstrap-select/css/bootstrap-select.min.css" rel="stylesheet" type="text/css"/>
    <link href="css/font-awesome.min.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet" href="css/style.css" type="text/css" media="screen"/>
    
  <script type="text/javascript">
    var MCFError = {
        ServerDown          :"<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"errorCode.ServerDown")%>",
        InternalServerError :"<%=Messages.getBodyJavascriptString(pageContext.getRequest().getLocale(),"errorCode.InternalServerError")%>"
    }
  </script>
  </head>
  <body class="fixed skin-black sidebar-mini">
    <div class="wrapper">
      <header class="main-header">
        <nav class="navbar navbar-static-top" role="navigation">
          <a class="logo">
            <img src="ManifoldCF-logo.png"/>
          </a>
          <!-- Sidebar toggle button-->
          <a href="/" class="sidebar-toggle" data-toggle="offcanvas" role="button">
            <span class="sr-only">Toggle navigation</span>
          </a>

          <h1 class="hidden-xs"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"banner.DocumentIngestion")%>
          </h1>
          <!-- Navbar Right Menu -->
          <div class="navbar-custom-menu">
            <div id="loader">Loading...</div>
          </div>
        </nav>
      </header>
      <aside class="main-sidebar">
        <jsp:include page="sidebar.jsp" flush="true"/>
      </aside>
      <div class="content-wrapper">
        <section class="content-header">
          <h1 class="visible-print-block"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.ApacheManifoldCF")%></h1>

          <h1 id="heading"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.WelcomeToApacheManifoldFC")%></h1>
        </section>
        <section id="content" class="content">
        </section>
      </div>
      <footer class="main-footer">
        <div class="pull-right hidden-xs"><b>Version</b>&nbsp;<%= mcfVersion %></div>
        <strong><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.Copyright")%>&nbsp;&nbsp;&nbsp;&nbsp;<a target="_blank" href="https://www.apache.org/"><%=Messages.getBodyString(pageContext.getRequest().getLocale(),"index.TheApacheSoftwareFoundation")%></a></strong>
      </footer>
    </div>
    <div class="overlay" style="display: none">
      <div class="spinner">
        <div class="bounce1"></div>
        <div class="bounce2"></div>
        <div class="bounce3"></div>
      </div>
    </div>
    <!-- Error Modal -->
    <div class="modal fade" id="exceptionModal" tabindex="-1" role="dialog" aria-labelledby="exceptionModalLabel">
      <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
            <h4 class="modal-title" id="exceptionModalLabel">Internal Server Exception</h4>
          </div>
          <div class="modal-body" style="max-height:calc(100vh - 212px);overflow: auto;">
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <script src="javascript/jquery.min.js"></script>
    <!-- Bootstrap JS -->
    <script src="bootstrap/js/bootstrap.min.js" type="text/javascript"></script>
    <!-- Bootstrap Select -->
    <script src="bootstrap-select/js/bootstrap-select.min.js" type="text/javascript"></script>
    <script src="javascript/jquery.slimscroll.min.js" type="text/javascript"></script>
    <!-- ManifoldCF -->
    <script src="javascript/mcf.js?v=<%= mcfVersion %>" type="text/javascript"></script>
<%
  String reqPage = request.getParameter("p");
  if (reqPage != null && reqPage.length() > 0)
  {
%>
    <script type="application/javascript">
      $(document).ready(function ()
      {
        $.ManifoldCF.loadContent('<%=URLEncoder.encode(reqPage)%>');
      });
    </script>
<%
  }
%>
  </body>
</html>
